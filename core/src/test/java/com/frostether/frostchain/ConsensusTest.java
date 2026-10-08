package com.frostether.frostchain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ConsensusTest {

    private static final long MAX = -1L; // 2^64 - 1 as an unsigned value

    @Test
    public void unitsHaveElevenDecimals() {
        assertEquals(11, U64.DECIMALS);
        assertEquals(100_000_000_000L, U64.COIN);
    }

    @Test
    public void blockOnePaysTheNormalReward() {
        // v0.4: no fixed first-block reward. Block 1 follows the same curve as every block.
        assertEquals(((MAX >>> 20) * 3) / 2, Consensus.reward(1, 0));
        assertEquals("263.88279066622", U64.format(Consensus.reward(1, 0)));
    }

    @Test
    public void laterBlocksFollowTheEmissionCurve() {
        long afterBlockOne = Consensus.reward(2, Consensus.reward(1, 0));
        assertEquals((((MAX - Consensus.reward(1, 0)) >>> 20) * 3) / 2, afterBlockOne);
        assertTrue(afterBlockOne < Consensus.reward(1, 0));
    }

    @Test
    public void rewardShrinksAsCoinsAreMined() {
        assertTrue(Consensus.reward(3, 1_000_000 * U64.COIN) < Consensus.reward(3, 0));
        assertEquals(0, Consensus.reward(0, 0));
    }

    @Test
    public void difficultyStartsAtTheInitialValue() {
        List<Long> times = new ArrayList<>();
        List<Long> diffs = new ArrayList<>();
        assertEquals(Consensus.INITIAL_DIFFICULTY, Consensus.nextDifficulty(times, diffs));
        times.add(1L);
        diffs.add(5L);
        times.add(2L);
        diffs.add(5L);
        assertEquals(Consensus.INITIAL_DIFFICULTY, Consensus.nextDifficulty(times, diffs));
    }

    @Test
    public void slowBlocksLowerTheDifficulty() {
        List<Long> times = new ArrayList<>();
        List<Long> diffs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            times.add(i * 1800L); // six times slower than the 5-minute target
            diffs.add(Consensus.INITIAL_DIFFICULTY);
        }
        assertTrue(Consensus.nextDifficulty(times, diffs) < Consensus.INITIAL_DIFFICULTY / 4);
    }

    @Test
    public void onTimeBlocksKeepTheDifficulty() {
        List<Long> times = new ArrayList<>();
        List<Long> diffs = new ArrayList<>();
        for (int i = 0; i < 61; i++) {
            times.add(i * Consensus.BLOCK_TIME);
            diffs.add(Consensus.INITIAL_DIFFICULTY);
        }
        long next = Consensus.nextDifficulty(times, diffs);
        assertTrue(Math.abs(next - Consensus.INITIAL_DIFFICULTY) < Consensus.INITIAL_DIFFICULTY / 50);
    }

    @Test
    public void genesisIsTheV04Relaunch() {
        // The chain ID is the genesis block's hash. Phones only talk to nodes with the same one,
        // so this locks it in: a change here means a different chain.
        assertEquals(1_791_567_420L, Consensus.GENESIS_TIME); // Fri 9 Oct 2026, 13:37 Eastern
        assertEquals(Consensus.GENESIS_TIME, Block.genesis().time);
        assertEquals(CHAIN_ID, Block.genesis().hashHex());
    }

    /** v0.4 chain ID. 0.3.x phones are on 98ede167f7885dae… and won't connect to it. */
    static final String CHAIN_ID = "303c4abcc5626d0b35b982921c9f98909f45be35c853244998f2ada954d101e7";
}
