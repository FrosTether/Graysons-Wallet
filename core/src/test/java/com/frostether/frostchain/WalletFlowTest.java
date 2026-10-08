package com.frostether.frostchain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.Map;
import org.junit.Test;

/** What a person actually does: mine into a wallet, send QOIN by name, claim a name. */
public class WalletFlowTest {

    @Test
    public void mineSendByNameAndClaim() throws Exception {
        TestKit.FakeClock clock = new TestKit.FakeClock(Consensus.GENESIS_TIME + 300);
        Node node = new Node(Files.createTempDirectory("flow").toFile(), clock);
        Wallets w = node.wallets;

        // A friend's wallet, then the miner's wallet (left open).
        w.create("Friend", "friend123");
        String friendFile = (String) w.info().get("file");
        byte[] friend = w.openAccount();
        w.create("Miner", "miner123");
        String minerFile = (String) w.info().get("file");
        byte[] miner = w.openAccount();

        for (int h = 1; h <= 13; h++) {
            TestKit.mine(node, clock, miner);
            clock.now += 1800;
        }
        assertEquals("jacobfrost", node.chain.nameOf(miner));

        // Preview, then send 250 QOIN to the friend by their raw address.
        Map<String, Object> preview = w.send(Address.raw(friend), "250", "first send", true);
        assertEquals("0.0001", ((String) preview.get("feeText")).replaceAll("0+$", ""));
        Map<String, Object> sent = w.send(Address.raw(friend), "250", "first send", false);
        assertTrue(((String) sent.get("txid")).length() == 64);
        assertEquals(1, node.mempool.size());

        TestKit.mine(node, clock, miner);
        clock.now += 1800;
        assertEquals(0, node.mempool.size());
        assertEquals(250 * TestKit.QOIN, node.chain.account(friend).balance);

        // The friend opens their wallet, claims a name, and gets paid by name.
        w.open(friendFile, "friend123");
        w.register("oofmaster");
        TestKit.mine(node, clock, miner);
        clock.now += 1800;
        assertEquals("oofmaster", node.chain.nameOf(friend));

        w.open(minerFile, "miner123");
        w.send("oofmaster.frostchain", "1.5", "", false);
        TestKit.mine(node, clock, miner);
        // Claiming a name is free in 0.3.0 (the wallet sets a zero fee), so the friend has 250 + 1.5.
        assertEquals(250 * TestKit.QOIN + 150 * TestKit.QOIN / 100, node.chain.account(friend).balance);

        // Mistakes come back as plain messages.
        try {
            w.send("oofmaster", "999999", "", false);
            throw new AssertionError("overspend accepted");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("not enough unlocked QOIN"));
        }
    }
}
