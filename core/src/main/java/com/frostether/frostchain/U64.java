package com.frostether.frostchain;

import java.math.BigInteger;

public final class U64 {
    public static final long COIN = 100000000000L;
    public static final int DECIMALS = 11;
    private static final BigInteger TWO64 = BigInteger.ONE.shiftLeft(64);

    private U64() {
    }

    public static int cmp(long j, long j2) {
        return Long.compareUnsigned(j, j2);
    }

    public static long add(long j, long j2) {
        long j3 = j + j2;
        if (Long.compareUnsigned(j3, j) < 0) {
            throw new ArithmeticException("amount overflow");
        }
        return j3;
    }

    public static long sub(long j, long j2) {
        if (Long.compareUnsigned(j, j2) < 0) {
            throw new ArithmeticException("amount underflow");
        }
        return j - j2;
    }

    public static String str(long j) {
        return Long.toUnsignedString(j);
    }

    public static long parse(String str) {
        if (str == null || !str.matches("[0-9]{1,20}")) {
            throw new IllegalArgumentException("bad amount");
        }
        BigInteger bigInteger = new BigInteger(str);
        if (bigInteger.compareTo(TWO64) >= 0) {
            throw new IllegalArgumentException("amount too large");
        }
        return bigInteger.longValue();
    }

    public static String format(long j) {
        BigInteger[] divideAndRemainder = new BigInteger(Long.toUnsignedString(j)).divideAndRemainder(BigInteger.valueOf(COIN));
        String bigInteger = divideAndRemainder[0].toString();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bigInteger.length(); i++) {
            if (i > 0 && (bigInteger.length() - i) % 3 == 0) {
                sb.append(',');
            }
            sb.append(bigInteger.charAt(i));
        }
        String bigInteger2 = divideAndRemainder[1].toString();
        while (bigInteger2.length() < 11) {
            bigInteger2 = "0" + bigInteger2;
        }
        int length = bigInteger2.length();
        while (length > 0 && bigInteger2.charAt(length - 1) == '0') {
            length--;
        }
        return length == 0 ? sb.toString() : ((Object) sb) + "." + bigInteger2.substring(0, length);
    }

    public static long parseCoins(String str) {
        String str2;
        if (str == null) {
            throw new IllegalArgumentException("missing amount");
        }
        String replace = str.trim().replace(",", "").replace("_", "");
        if (!replace.matches("[0-9]*(\\.[0-9]*)?") || replace.isEmpty() || replace.equals(".")) {
            throw new IllegalArgumentException("not a number: " + str);
        }
        String str3 = "";
        int indexOf = replace.indexOf(46);
        if (indexOf >= 0) {
            str2 = replace.substring(0, indexOf);
            str3 = replace.substring(indexOf + 1);
        } else {
            str2 = replace;
        }
        if (str3.length() > 11) {
            throw new IllegalArgumentException("at most 11 decimal places");
        }
        while (str3.length() < 11) {
            str3 = str3 + "0";
        }
        if (str2.isEmpty()) {
            str2 = "0";
        }
        BigInteger add = new BigInteger(str2).multiply(BigInteger.valueOf(COIN)).add(new BigInteger(str3));
        if (add.compareTo(TWO64) >= 0) {
            throw new IllegalArgumentException("amount too large");
        }
        return add.longValue();
    }
}
