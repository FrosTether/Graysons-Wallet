package com.frostether.frostchain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Temporal: the burn address, fingerprints and memos the web page shares, seals on the chain, and travel. */
public class TemporalTest {

    /** The page at get.finux.tech/temporal derives the same address and fingerprint (site/temporal/script.js). */
    @Test
    public void burnAddressAndFingerprintMatchThePage() {
        assertEquals("fcn22knwovgbfytc6qyjczvb4kkxwjhqjmoaru", Temporal.BURN_ADDRESS);
        assertEquals("25ced2cfdb8b5a8518b94a2b3d3174ac73055f83",
                Temporal.fingerprint(1823017020L, "00112233445566778899aabbccddeeff", "Hello, 2027.\nÜnïcödé ✓"));
        String memo = Temporal.memo(1823017020L, "25ced2cfdb8b5a8518b94a2b3d3174ac73055f83");
        assertEquals("T1 1823017020 25ced2cfdb8b5a8518b94a2b3d3174ac73055f83", memo);
        assertTrue(Bytes.utf8(memo).length <= Consensus.MEMO_MAX);
        assertEquals(Long.valueOf(1823017020L), Temporal.parse(memo)[0]);
        assertNull(Temporal.parse("T1 soon 25ced2cf"));
        assertNull(Temporal.parse("lunch"));
        assertEquals(32, Temporal.newSalt().length());
    }

    @Test
    public void capsuleCodesRoundTripAndCatchTampering() throws Exception {
        Map<String, Object> c = TemporalStore.capsule(1823017020L, "00112233445566778899aabbccddeeff", "Hello, 2027.\nÜnïcödé ✓");
        assertEquals("25ced2cfdb8b5a8518b94a2b3d3174ac73055f83", c.get("fingerprint"));
        String code = TemporalStore.code(c);
        assertTrue(code.startsWith("temporal1:"));
        assertEquals(c.get("fingerprint"), TemporalStore.decode(code).get("fingerprint"));
        // A capsule file from the web page carries its fingerprint; editing the words breaks the match.
        String file = "{\"temporal\":1,\"opens\":1823017020,\"salt\":\"00112233445566778899aabbccddeeff\",\"text\":\"Hello, 2028.\","
                + "\"fingerprint\":\"25ced2cfdb8b5a8518b94a2b3d3174ac73055f83\"}";
        assertEquals("this capsule was changed after it was sealed: its fingerprint doesn't match", error(() -> TemporalStore.decode(file)));
        assertEquals("that isn't a Temporal capsule code", error(() -> TemporalStore.decode("hello")));

        File dir = Files.createTempDirectory("capsules").toFile();
        TemporalStore store = new TemporalStore(dir);
        store.put(c);
        assertEquals(1, new TemporalStore(dir).all().size());
        assertTrue(store.remove((String) c.get("fingerprint")));
        assertEquals(0, new TemporalStore(dir).all().size());
    }

    @Test
    public void sealsOnTheChainAndTravel() throws Exception {
        TestKit.FakeClock clock = new TestKit.FakeClock(Consensus.GENESIS_TIME + 300);
        Node node = new Node(Files.createTempDirectory("temporal").toFile(), clock);
        Api api = new Api(node, new QuietPlatform());
        node.wallets.create("Miner", "miner123");
        byte[] miner = node.wallets.openAccount();
        long firstBlockTime = 0;
        for (int h = 1; h <= 13; h++) {
            TestKit.mine(node, clock, miner);
            if (h == 1) {
                firstBlockTime = node.chain.tip().time;
            }
            clock.now += 1800;
        }

        // Too soon to open, then a real seal through the app's API: 1 QNR to the burn address with the memo.
        assertTrue(call(api, "temporal.seal", "{\"text\":\"hi\",\"opens\":" + (clock.now + 60) + ",\"dry\":true}").containsKey("error"));
        long opens = clock.now + 86400;
        Map<String, Object> preview = result(call(api, "temporal.seal", "{\"text\":\"See you tomorrow.\",\"opens\":" + opens + ",\"dry\":true}"));
        assertEquals("1", preview.get("amountText"));
        String salt = (String) preview.get("salt");
        Map<String, Object> sealed = result(call(api, "temporal.seal",
                "{\"text\":\"See you tomorrow.\",\"opens\":" + opens + ",\"salt\":\"" + salt + "\"}"));
        String fp = (String) sealed.get("fingerprint");
        assertEquals(preview.get("fingerprint"), fp);
        assertEquals(Temporal.fingerprint(opens, salt, "See you tomorrow."), fp);

        // A payment under the gas, with a seal-shaped memo, doesn't count.
        node.wallets.send(Temporal.BURN_ADDRESS, "0.5", Temporal.memo(opens, "00000000000000000000000000000000000000aa"), false);
        TestKit.mine(node, clock, miner);
        long sealTime = node.chain.tip().time;
        clock.now += 1800;

        List<Temporal.Seal> seals = Temporal.seals(node.chain);
        assertEquals(1, seals.size());
        Temporal.Seal seal = Temporal.find(node.chain, fp);
        assertNotNull(seal);
        assertEquals(sealTime, seal.time);
        assertTrue(seal.onTime());
        assertEquals(Address.raw(miner), seal.toJson().get("from"));
        assertEquals(U64.COIN + U64.COIN / 2, Temporal.burned(node.chain));

        // The capsule stays shut: the API holds its words back until it opens.
        List<Object> capsules = list(call(api, "temporal.capsules", "{}"));
        @SuppressWarnings("unchecked")
        Map<String, Object> view = (Map<String, Object>) capsules.get(0);
        assertFalse((Boolean) view.get("open"));
        assertFalse(view.containsKey("text"));
        assertNotNull(view.get("seal"));
        clock.now = opens + 1;
        @SuppressWarnings("unchecked")
        Map<String, Object> opened = (Map<String, Object>) list(call(api, "temporal.capsules", "{}")).get(0);
        assertEquals("See you tomorrow.", opened.get("text"));

        // Travel: before the chain, between blocks, and now.
        Temporal.At start = Temporal.at(node.chain, Consensus.GENESIS_TIME - 5);
        assertEquals(0, start.block.height);
        assertEquals(0L, start.mined);
        Temporal.At first = Temporal.at(node.chain, firstBlockTime + 10);
        assertEquals(1, first.block.height);
        assertEquals(Consensus.reward(1, 0), first.mined);
        Temporal.At now = Temporal.at(node.chain, clock.now);
        assertEquals(node.chain.height(), now.block.height);
        assertEquals(node.chain.generated(), now.mined);
        assertEquals(1, now.seals);
        Map<String, Object> at = result(call(api, "temporal.at", "{\"time\":" + (firstBlockTime + 10) + "}"));
        assertEquals(U64.format(Consensus.reward(1, 0)), at.get("minedText"));
        assertEquals(U64.format(node.chain.at(1).paid), at.get("balanceText"));
    }

    // ---- helpers ----

    private static Map<String, Object> call(Api api, String method, String params) {
        return Json.obj(api.call(method, params));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(Map<String, Object> reply) {
        assertFalse(String.valueOf(reply.get("error")), reply.containsKey("error"));
        return (Map<String, Object>) reply.get("result");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> reply) {
        assertFalse(String.valueOf(reply.get("error")), reply.containsKey("error"));
        return (List<Object>) reply.get("result");
    }

    private interface Thrower {
        void run() throws Exception;
    }

    private static String error(Thrower t) {
        try {
            t.run();
        } catch (Exception e) {
            return e.getMessage();
        }
        return null;
    }

    /** A phone with no sensors: enough for the API's wallet and Temporal calls. */
    static final class QuietPlatform implements Api.Platform {
        @Override
        public Resonance.Reading latestReading() {
            return null;
        }

        @Override
        public void miningChanged(boolean z) {
        }

        @Override
        public String name() {
            return "test";
        }

        @Override
        public Map<String, Object> sensors() {
            return Collections.emptyMap();
        }

        @Override
        public void startSensor(String str) {
        }

        @Override
        public void stopSensor() {
        }
    }
}
