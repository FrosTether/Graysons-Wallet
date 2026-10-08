package com.frostether.frostchain;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Consensus {
    public static final long BLOCK_TIME = 300;
    public static final String CHAIN_NAME = "Frostchain v0.4";
    public static final String COIN = "QOIN";
    public static final int COINBASE_MATURITY = 12;
    public static final int EMISSION_SPEED_FACTOR = 20;
    public static final String FOUNDER_NAME = "jacobfrost";
    /** More names for the founder's wallet. They point to it like FOUNDER_NAME, which stays the name it shows. */
    public static final List<String> FOUNDER_ALIASES = Collections.unmodifiableList(Arrays.asList("agorajay"));
    /**
     * jacobfrost.frostchain and the aliases belong to this address from genesis. 0.3.x gave jacobfrost to whoever
     * mined block 1, which anyone running the public app could do. The genesis marker names them all, so they're
     * part of the chain ID.
     */
    public static final String FOUNDER_ADDRESS = "fc3dlrkt7demghofiz7wzjz2st4vzvjiskxumv";
    private static final byte[] FOUNDER_ACCOUNT = Address.decodeRaw(FOUNDER_ADDRESS);

    public static byte[] founderAccount() {
        return FOUNDER_ACCOUNT.clone();
    }
    /** How far ahead of a node's clock a block's time may be. Short, so the block spacing below holds in real time. */
    public static final long FUTURE_TIME_LIMIT = 30;
    /**
     * A block's time must be at least this long after the previous block's (v0.4.1). The difficulty still aims
     * for BLOCK_TIME, so the last 30 seconds of each 5 minutes is the proof-of-work race: blocks come about every
     * 5 minutes and never in bursts. A floor equal to BLOCK_TIME would let the difficulty sink to nothing.
     */
    public static final long MIN_BLOCK_SPACING = 270;
    /**
     * v0.4 relaunch: Thursday 8 October 2026, 13:37 Eastern. Mining opens at this moment, and changing it
     * (with the marker in Resonance.genesisMarker) makes a different chain. 0.3.x started on 3 October.
     */
    public static final long GENESIS_TIME = 1791481020;
    public static final long INITIAL_DIFFICULTY = 20000000;
    public static final int LWMA_N = 60;
    public static final int MAX_BLOCK_TXS = 200;
    public static final int MAX_NAMES_PER_BLOCK = 20;
    public static final int MEDIAN_TIME_WINDOW = 11;
    public static final int MEMO_MAX = 64;
    public static final long MIN_FEE = 10000000;
    public static final long MONEY_SUPPLY = -1;
    public static final int P2P_PORT = 7830;
    private static final BigInteger TWO256 = BigInteger.ONE.shiftLeft(256);
    public static final int VERSION = 1;

    private Consensus() {
    }

    /**
     * Block reward for height j after j2 coins have been mined: (2^64 - mined) / 2^20 x 1.5.
     * Since v0.4 block 1 follows the same curve as every other block; 0.3.x paid it a fixed 13,370.08241991.
     */
    public static long reward(long j, long j2) {
        if (j <= 0) {
            return 0L;
        }
        return ((((-1) - j2) >>> 20) * 3) / 2;
    }

    public static byte[] target(long j) {
        byte[] byteArray = TWO256.subtract(BigInteger.ONE).divide(new BigInteger(Long.toUnsignedString(j))).toByteArray();
        byte[] bArr = new byte[32];
        int min = Math.min(32, byteArray.length);
        System.arraycopy(byteArray, byteArray.length - min, bArr, 32 - min, min);
        return bArr;
    }

    public static boolean meetsTarget(byte[] bArr, byte[] bArr2) {
        for (int i = 0; i < 32; i++) {
            int i2 = bArr[i] & 255;
            int i3 = bArr2[i] & 255;
            if (i2 != i3) {
                return i2 < i3;
            }
        }
        return true;
    }

    public static long nextDifficulty(List<Long> list, List<Long> list2) {
        int size = list.size();
        if (size < 3) {
            return INITIAL_DIFFICULTY;
        }
        int min = Math.min(60, size - 1);
        int i = (size - min) - 1;
        long j = 0;
        BigInteger bigInteger = BigInteger.ZERO;
        int i2 = 1;
        long longValue = list.get(i).longValue();
        BigInteger bigInteger2 = bigInteger;
        while (i2 <= min) {
            long longValue2 = list.get(i + i2).longValue();
            if (longValue2 <= longValue) {
                longValue2 = 1 + longValue;
            }
            long j2 = longValue2;
            j += Math.min(6 * 300, j2 - longValue) * i2;
            BigInteger add = bigInteger2.add(new BigInteger(Long.toUnsignedString(list2.get(i + i2).longValue())));
            i2++;
            longValue = j2;
            bigInteger2 = add;
        }
        long j3 = (min * (min + 1)) / 2;
        BigInteger divide = bigInteger2.multiply(BigInteger.valueOf(300L)).multiply(BigInteger.valueOf(min + 1)).multiply(BigInteger.valueOf(99L)).divide(BigInteger.valueOf((j < (j3 * 300) / 20 ? (j3 * 300) / 20 : j) * 200));
        if (divide.signum() <= 0) {
            return 1L;
        }
        if (divide.bitLength() > 63) {
            return Long.MAX_VALUE;
        }
        return Math.max(1000L, divide.longValue());
    }
}
