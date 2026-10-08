package com.frostether.frostchain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import org.junit.Test;

/** Two real nodes talking over HTTP on localhost: block sync, transaction relay and mining on top. */
public class NodeSyncTest {

    private static final Lms.PrivateKey MINER = TestKit.key(31);
    private static final Lms.PrivateKey FRIEND = TestKit.key(33);

    private static boolean waitFor(java.util.function.BooleanSupplier ok, long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            if (ok.getAsBoolean()) {
                return true;
            }
            Thread.sleep(100);
        }
        return ok.getAsBoolean();
    }

    @Test
    public void newNodeSyncsAndFollowsTheTip() throws Exception {
        TestKit.FakeClock clock = new TestKit.FakeClock(Consensus.GENESIS_TIME + 300);
        File dirA = Files.createTempDirectory("node-a").toFile();
        File dirB = Files.createTempDirectory("node-b").toFile();
        Node a = new Node(dirA, clock);
        Node b = new Node(dirB, clock);
        try {
            a.start(0, false);
            b.start(0, false);

            // Node A mines two blocks while B is offline from it.
            TestKit.mine(a.chain, clock, TestKit.id(MINER), Collections.<Tx>emptyList());
            clock.now += 1800;
            TestKit.mine(a.chain, clock, TestKit.id(MINER), Collections.<Tx>emptyList());
            clock.now += 1800;
            assertEquals(2, a.chain.height());
            assertEquals(0, b.chain.height());

            // B is told about A, the way a phone adds a node under Node → Add.
            b.addPeer("127.0.0.1:" + a.port());
            assertTrue("B should sync A's blocks", waitFor(() -> b.chain.height() == 2, 30_000));
            assertEquals(a.chain.tip().hashHex(), b.chain.tip().hashHex());
            assertTrue(b.syncStatus().contains("peers reachable"));

            // B checks transactions against the state it synced: an empty account can't pay a fee.
            Tx tx = TestKit.claimName(FRIEND, 0, "oofmaster", b.chain.chainId);
            assertEquals("insufficient funds", b.submitTx(tx, null));

            // A new block mined on A reaches B through B's regular polling.
            TestKit.mine(a.chain, clock, TestKit.id(MINER), Collections.<Tx>emptyList());
            b.wakeSync();
            assertTrue("B should follow A's new tip", waitFor(() -> b.chain.height() == 3, 30_000));
            assertEquals(a.chain.tip().hashHex(), b.chain.tip().hashHex());
            assertNull(b.chain.account(TestKit.id(FRIEND)));
        } finally {
            a.stop();
            b.stop();
        }
    }
}
