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
    public void firstYearPaysLeet() {
        assertEquals("13.37", U64.format(Consensus.reward(1, 0)));
        assertEquals(Consensus.FIRST_REWARD, Consensus.reward(105_120, 123));
        assertEquals(0, Consensus.reward(0, 0));
    }

    @Test
    public void eachYearPaysFourPointTwoFivePercentLess() {
        assertEquals("12.801775", U64.format(Consensus.reward(105_121, 0)));
        assertEquals("12.2576995625", U64.format(Consensus.reward(2 * 105_120 + 1, 0)));
        assertTrue(Consensus.reward(Long.MAX_VALUE, 0) == 0);
    }

    @Test
    public void supplyTopsOutNear33Million() {
        assertEquals("33,069,515.2869234816", U64.format(Consensus.maxSupply()));
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
    public void genesisIsTheV05Relaunch() {
        // The chain ID is the genesis block's hash. Phones only talk to nodes with the same one,
        // so this locks it in: a change here means a different chain.
        assertEquals(1_791_481_020L, Consensus.GENESIS_TIME); // Thu 8 Oct 2026, 13:37 Eastern
        assertEquals(Consensus.GENESIS_TIME, Block.genesis().time);
        assertEquals(CHAIN_ID, Block.genesis().hashHex());
    }

    /** v0.5 chain ID, the 13.37 relaunch. v0.4 phones are on aa18513e5ee8db9f… and 0.3.x on 98ede167f7885dae…; neither connects. */
    static final String CHAIN_ID = "b8b1ec3fefb968349fcf3fdf0cf0735c6912428c477a8d32b0f37277d154cefc";
}
