package com.frostether.frostchain;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import org.junit.Test;

/** End to end: mine real blocks, send signed transactions, reload from disk. Takes a minute or two. */
public class ChainTest {

    private static final Lms.PrivateKey MINER = TestKit.key(21);
    private static final Lms.PrivateKey FRIEND = TestKit.key(23);
    private static final long QOIN = TestKit.QOIN;

    @Test
    public void mineSendNameAndReload() throws Exception {
        File dir = Files.createTempDirectory("frostchain-test").toFile();
        TestKit.FakeClock clock = new TestKit.FakeClock(Consensus.GENESIS_TIME + 300);
        Chain chain = new Chain(dir, clock);
        byte[] miner = TestKit.id(MINER);
        byte[] friend = TestKit.id(FRIEND);

        // Blocks 1 to 13, spaced 30 minutes apart so the difficulty falls quickly.
        for (int h = 1; h <= 13; h++) {
            TestKit.mine(chain, clock, miner, Collections.<Tx>emptyList());
            clock.now += 1800;
        }
        assertEquals(13, chain.height());
        assertArrayEquals(miner, chain.lookupName("jacobfrost"));

        // Block 1's reward unlocked at height 13. Blocks 2 to 13 are still maturing.
        ChainState.Account a = chain.account(miner);
        assertEquals(Consensus.PREMINE, a.balance);
        assertTrue(a.immature > 0);

        // A signed send is checked like a phone would check it, then mined into block 14.
        Tx pay = TestKit.send(MINER, 0, friend, 100 * QOIN, chain.chainId);
        assertEquals(null, chain.checkTx(pay));
        TestKit.mine(chain, clock, miner, Collections.singletonList(pay));
        clock.now += 1800;
        assertEquals(100 * QOIN, chain.account(friend).balance);

        // The friend claims a name with part of what they received.
        Tx claim = TestKit.claimName(FRIEND, 0, "oofmaster", chain.chainId);
        assertEquals(null, chain.checkTx(claim));
        TestKit.mine(chain, clock, miner, Collections.singletonList(claim));
        assertArrayEquals(friend, chain.lookupName("oofmaster"));
        assertEquals(100 * QOIN - Consensus.MIN_FEE, chain.account(friend).balance);

        // A forged signature is refused.
        Tx forged = TestKit.send(FRIEND, 1, miner, QOIN, chain.chainId);
        forged.amount = 50 * QOIN;
        assertNotNull(chain.checkTx(forged));

        // Everything survives a restart.
        Chain reloaded = new Chain(dir, clock);
        assertEquals(chain.height(), reloaded.height());
        assertEquals(chain.tip().hashHex(), reloaded.tip().hashHex());
        assertEquals(chain.account(miner).balance, reloaded.account(miner).balance);
        assertEquals(chain.account(friend).balance, reloaded.account(friend).balance);
        assertArrayEquals(friend, reloaded.lookupName("oofmaster"));
        assertEquals(chain.generated(), reloaded.generated());
    }
}
