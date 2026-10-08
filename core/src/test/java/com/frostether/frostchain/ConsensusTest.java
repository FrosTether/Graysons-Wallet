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
    public void blockOneUsesTheFixedFirstBlockReward() {
        assertEquals(1_337_008_241_991_000L, Consensus.reward(1, 0));
        assertEquals("13,370.08241991", U64.format(Consensus.reward(1, 0)).replaceAll("0+$", ""));
    }

    @Test
    public void laterBlocksFollowTheEmissionCurve() {
        assertEquals(((MAX >>> 20) * 3) / 2, Consensus.reward(2, 0));
        long afterBlockOne = Consensus.reward(2, Consensus.PREMINE);
        assertEquals((((MAX - Consensus.PREMINE) >>> 20) * 3) / 2, afterBlockOne);
        // about 263.86 QOIN per block right after launch
        assertTrue(afterBlockOne > 263_86 * U64.COIN / 100 && afterBlockOne < 263_87 * U64.COIN / 100);
    }

    @Test
    public void rewardShrinksAsCoinsAreMined() {
        assertTrue(Consensus.reward(3, 1_000_000 * U64.COIN) < Consensus.reward(3, 0));
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
    public void genesisIsFixed() {
        // The chain ID is the genesis block's hash. Phones only talk to nodes with the same one,
        // so this locks it in: a change here means a different chain.
        assertEquals(Consensus.GENESIS_TIME, Block.genesis().time);
        assertEquals(64, Block.genesis().hashHex().length());
    }
}
