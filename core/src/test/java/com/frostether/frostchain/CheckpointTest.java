package com.frostether.frostchain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

/** Checkpoints and the reorg-depth limit, on two real forks from the same genesis. */
public class CheckpointTest {

    private static TestKit.FakeClock clock;
    private static Chain forkA; // 3 blocks
    private static Chain forkB; // 4 blocks, so it has more work

    @BeforeClass
    public static void mineTwoForks() throws Exception {
        clock = new TestKit.FakeClock(Consensus.GENESIS_TIME + 300);
        forkA = new Chain(null, clock);
        forkB = new Chain(null, clock);
        byte[] minerA = TestKit.id(TestKit.key(51));
        byte[] minerB = TestKit.id(TestKit.key(53));
        for (int i = 0; i < 4; i++) {
            if (i < 3) {
                TestKit.mine(forkA, clock, minerA, Collections.<Tx>emptyList());
            }
            TestKit.mine(forkB, clock, minerB, Collections.<Tx>emptyList());
            clock.now += 600;
        }
        assertEquals(3, forkA.height());
        assertEquals(4, forkB.height());
    }

    private static Block copy(Block b) {
        return Block.fromJson(b.toJson());
    }

    private static List<Block> blocksOf(Chain c, long from) {
        List<Block> out = new ArrayList<>();
        for (Block b : c.range(from, 100)) {
            out.add(copy(b));
        }
        return out;
    }

    @Test
    public void aCheckpointRefusesAForkedBlockAndAcceptsTheRightOne() throws Exception {
        Chain c = new Chain(null, clock);
        Map<Long, String> cp = new LinkedHashMap<>();
        cp.put(1L, forkA.at(1).hashHex());
        c.setCheckpoints(cp);
        Chain.Result wrong = c.addBlock(copy(forkB.at(1)));
        assertFalse(wrong.ok);
        assertTrue(wrong.error, wrong.error.contains("checkpoint"));
        assertEquals(0, c.height());
        assertTrue(c.addBlock(copy(forkA.at(1))).ok);
        assertEquals(1, c.height());
    }

    @Test
    public void aReorgPastACheckpointIsRefused() throws Exception {
        Chain c = new Chain(null, clock);
        for (Block b : blocksOf(forkA, 1)) {
            assertTrue(c.addBlock(b).ok);
        }
        Map<Long, String> cp = new LinkedHashMap<>();
        cp.put(2L, forkA.at(2).hashHex());
        c.setCheckpoints(cp);
        StringBuilder why = new StringBuilder();
        assertNull(c.tryReorg(0, blocksOf(forkB, 1), why));
        assertTrue(why.toString(), why.toString().contains("checkpoint"));
        assertEquals(forkA.tip().hashHex(), c.tip().hashHex());
    }

    @Test
    public void aReorgDeeperThanTheLimitIsRefusedAndAShallowOneIsNot() throws Exception {
        Chain c = new Chain(null, clock);
        for (Block b : blocksOf(forkA, 1)) {
            assertTrue(c.addBlock(b).ok);
        }
        assertEquals(Consensus.MAX_REORG_DEPTH, c.maxReorgDepth());

        c.setMaxReorgDepth(2); // forkB forks at genesis: that undoes all 3 of our blocks
        assertTrue(c.tooDeep(0));
        assertFalse(c.tooDeep(1));
        StringBuilder why = new StringBuilder();
        assertNull(c.tryReorg(0, blocksOf(forkB, 1), why));
        assertTrue(why.toString(), why.toString().contains("limit of 2"));
        assertEquals(forkA.tip().hashHex(), c.tip().hashHex());

        c.setMaxReorgDepth(3); // exactly the depth: allowed
        assertFalse(c.tooDeep(0));
        assertNotNull(c.tryReorg(0, blocksOf(forkB, 1), new StringBuilder()));
        assertEquals(forkB.tip().hashHex(), c.tip().hashHex());
    }

    @Test
    public void zeroMeansNoLimit() throws Exception {
        Chain c = new Chain(null, clock);
        for (Block b : blocksOf(forkA, 1)) {
            assertTrue(c.addBlock(b).ok);
        }
        c.setMaxReorgDepth(0);
        assertFalse(c.tooDeep(0));
        assertNotNull(c.tryReorg(0, blocksOf(forkB, 1), new StringBuilder()));
        assertEquals(4, c.height());
    }

    @Test
    public void theShippedCheckpointListIsEmptyUntilTheNetworkAgrees() {
        assertTrue(Consensus.CHECKPOINTS.isEmpty());
    }
}
