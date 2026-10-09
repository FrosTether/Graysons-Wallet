package com.frostether.frostchain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * 0.5.5: a node that's ahead pushes its blocks to a peer that's behind, for a public node that can't dial a
 * phone behind NAT. The pushing node here is never started, so it has no port the other node could dial: the
 * only way blocks reach the peer is the push.
 */
public class NodePushTest {

    private static final byte[] MINER_A = TestKit.id(TestKit.key(51));
    private static final byte[] MINER_B = TestKit.id(TestKit.key(53));

    private static Node node(String name, Chain.Clock clock) throws IOException {
        return new Node(Files.createTempDirectory(name).toFile(), clock);
    }

    @Test
    public void pushesMissingBlocksToAPeerThatIsBehind() throws Exception {
        TestKit.FakeClock clock = new TestKit.FakeClock(Consensus.GENESIS_TIME + 300);
        Node phone = node("push-phone", clock);
        Node server = node("push-server", clock);
        try {
            server.start(0, false);
            TestKit.mine(phone.chain, clock, MINER_A, Collections.<Tx>emptyList());
            clock.now += 600;
            TestKit.mine(phone.chain, clock, MINER_A, Collections.<Tx>emptyList());
            clock.now += 600;
            // The server already has block 1 (same chain), so it only misses block 2.
            assertTrue(server.chain.addBlock(phone.chain.at(1)).ok);

            assertTrue(phone.syncWith("127.0.0.1:" + server.port()));
            assertEquals(2, server.chain.height());
            assertEquals(phone.chain.tip().hashHex(), server.chain.tip().hashHex());
        } finally {
            phone.stop();
            server.stop();
        }
    }

    @Test
    public void pushedHeavierForkReorgsThePeer() throws Exception {
        TestKit.FakeClock clock = new TestKit.FakeClock(Consensus.GENESIS_TIME + 300);
        Node phone = node("fork-phone", clock);
        Node server = node("fork-server", clock);
        try {
            server.start(0, false);
            TestKit.mine(server.chain, clock, MINER_B, Collections.<Tx>emptyList());
            TestKit.mine(phone.chain, clock, MINER_A, Collections.<Tx>emptyList());
            clock.now += 600;
            TestKit.mine(phone.chain, clock, MINER_A, Collections.<Tx>emptyList());
            clock.now += 600;
            TestKit.mine(phone.chain, clock, MINER_A, Collections.<Tx>emptyList());
            clock.now += 600;
            assertEquals(1, server.chain.height());
            assertTrue(!server.chain.tip().hashHex().equals(phone.chain.at(1).hashHex()));

            assertTrue(phone.syncWith("127.0.0.1:" + server.port()));
            assertEquals(3, server.chain.height());
            assertEquals(phone.chain.tip().hashHex(), server.chain.tip().hashHex());
            assertTrue(server.chain.account(MINER_B) == null || server.chain.account(MINER_B).immature == 0);
        } finally {
            phone.stop();
            server.stop();
        }
    }

    @Test
    public void lighterOrInvalidForksAreRefused() throws Exception {
        TestKit.FakeClock clock = new TestKit.FakeClock(Consensus.GENESIS_TIME + 300);
        Node server = node("refuse-server", clock);
        Node light = node("refuse-light", clock);
        // A fork mined an hour ahead of the server's clock: valid work, but its blocks are from the future.
        TestKit.FakeClock ahead = new TestKit.FakeClock(Consensus.GENESIS_TIME + 3900);
        Node future = node("refuse-future", ahead);
        try {
            server.start(0, false);
            String peer = "127.0.0.1:" + server.port();
            TestKit.mine(server.chain, clock, MINER_B, Collections.<Tx>emptyList());
            TestKit.mine(light.chain, clock, MINER_A, Collections.<Tx>emptyList());
            clock.now += 600;
            TestKit.mine(server.chain, clock, MINER_B, Collections.<Tx>emptyList());
            String tip = server.chain.tip().hashHex();

            // Lighter: one block against the server's two.
            expectRefused(light, peer, 0, light.chain.range(1, 1), "no more work");

            // Too many blocks in one request, before anything is checked.
            List<Block> many = new ArrayList<>();
            for (int i = 0; i < Node.MAX_PUSH_BLOCKS + 1; i++) {
                many.add(light.chain.at(1));
            }
            expectRefused(light, peer, 0, many, "send 1 to");

            // A block whose proof of work doesn't meet its difficulty.
            Block forged = Block.fromJson(light.chain.at(1).toJson());
            forged.nonce ^= 1;
            forged.invalidateHash();
            if (!Consensus.meetsTarget(forged.hash(), Consensus.target(forged.difficulty))) {
                expectRefused(light, peer, 0, Collections.singletonList(forged), "proof of work");
            }

            // Heavier but invalid: three blocks timestamped an hour after the server's clock.
            for (int i = 0; i < 3; i++) {
                TestKit.mine(future.chain, ahead, MINER_A, Collections.<Tx>emptyList());
                ahead.now += 600;
            }
            Thread.sleep(Node.FORK_MIN_INTERVAL_MS + 200);
            expectRefused(future, peer, 0, future.chain.range(1, 3), "future");

            // Nothing changed on the server.
            assertEquals(2, server.chain.height());
            assertEquals(tip, server.chain.tip().hashHex());
        } finally {
            server.stop();
            light.stop();
            future.stop();
        }
    }

    private static void expectRefused(Node from, String peer, long fork, List<Block> blocks, String why) {
        List<Object> raw = new ArrayList<>();
        for (Block b : blocks) {
            raw.add(b.toJson());
        }
        try {
            from.post(peer, "/p2p/fork", Json.write(Json.o("fork", Long.valueOf(fork), "blocks", raw)));
            fail("the fork should have been refused (" + why + ")");
        } catch (IOException e) {
            assertTrue("expected \"" + why + "\" in: " + e.getMessage(), String.valueOf(e.getMessage()).contains(why));
        }
    }
}
