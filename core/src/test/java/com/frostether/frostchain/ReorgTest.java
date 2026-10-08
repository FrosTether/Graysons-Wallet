package com.frostether.frostchain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.Collections;
import org.junit.Test;

/** Two nodes mine separate forks; when they connect, the one with less work switches to the other. */
public class ReorgTest {

    @Test
    public void shorterForkSwitchesToTheChainWithMoreWork() throws Exception {
        TestKit.FakeClock clock = new TestKit.FakeClock(Consensus.GENESIS_TIME + 300);
        Node a = new Node(Files.createTempDirectory("fork-a").toFile(), clock);
        Node b = new Node(Files.createTempDirectory("fork-b").toFile(), clock);
        try {
            a.start(0, false);
            b.start(0, false);
            byte[] minerA = TestKit.id(TestKit.key(41));
            byte[] minerB = TestKit.id(TestKit.key(43));

            TestKit.mine(a.chain, clock, minerA, Collections.<Tx>emptyList());
            TestKit.mine(b.chain, clock, minerB, Collections.<Tx>emptyList());
            clock.now += 600;
            TestKit.mine(b.chain, clock, minerB, Collections.<Tx>emptyList());
            assertEquals(1, a.chain.height());
            assertEquals(2, b.chain.height());

            a.addPeer("127.0.0.1:" + b.port());
            long end = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < end && a.chain.height() != 2) {
                Thread.sleep(100);
            }
            assertEquals(2, a.chain.height());
            assertEquals(b.chain.tip().hashHex(), a.chain.tip().hashHex());
            // Block 1 now belongs to B's miner, including the founder name.
            assertEquals(Bytes.hex(minerB), Bytes.hex(a.chain.lookupName("jacobfrost")));
            assertTrue(a.chain.account(minerA) == null || a.chain.account(minerA).immature == 0);
        } finally {
            a.stop();
            b.stop();
        }
    }
}
