package com.frostether.frostchain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Address {
    private static final String B32 = "abcdefghijklmnopqrstuvwxyz234567";
    private static final Pattern NAME = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,22})[a-z0-9]$");
    private static final Pattern RAW = Pattern.compile("^fc[a-z2-7]{36}$");
    public static final String SUFFIX = ".frostchain";

    private Address() {
    }

    public static byte[] accountId(byte[] bArr) {
        return Bytes.slice(Sha256.hash(Bytes.utf8("frostchain/account"), bArr), 0, 20);
    }

    public static String raw(byte[] bArr) {
        if (bArr.length != 20) {
            throw new IllegalArgumentException("account id must be 20 bytes");
        }
        return "fc" + base32(bArr) + checksum(bArr);
    }

    public static boolean isRaw(String str) {
        return RAW.matcher(strip(str)).matches();
    }

    public static byte[] decodeRaw(String str) {
        String strip = strip(str);
        if (!RAW.matcher(strip).matches()) {
            throw new IllegalArgumentException("not a raw Frostchain address");
        }
        byte[] unbase32 = unbase32(strip.substring(2, 34));
        if (checksum(unbase32).equals(strip.substring(34))) {
            return unbase32;
        }
        throw new IllegalArgumentException("address checksum doesn't match: check for a typo");
    }

    public static boolean validName(String str) {
        return (str == null || !NAME.matcher(str).matches() || str.contains("--") || RAW.matcher(str).matches()) ? false : true;
    }

    public static String strip(String str) {
        String lowerCase = str == null ? "" : str.trim().toLowerCase(Locale.ROOT);
        if (lowerCase.startsWith("@")) {
            lowerCase = lowerCase.substring(1);
        }
        return lowerCase.endsWith(SUFFIX) ? lowerCase.substring(0, lowerCase.length() - SUFFIX.length()) : lowerCase;
    }

    public static String display(String str) {
        return str + SUFFIX;
    }

    private static String checksum(byte[] bArr) {
        return base32(Bytes.slice(Sha256.hash(Bytes.utf8("frostchain/address-checksum"), bArr), 0, 5)).substring(0, 4);
    }

    static String base32(byte[] bArr) {
        StringBuilder sb = new StringBuilder();
        int length = bArr.length;
        int i = 0;
        int i2 = 0;
        int i3 = 0;
        while (i < length) {
            i3 = (i3 << 8) | (bArr[i] & 255);
            int i4 = i2 + 8;
            while (i4 >= 5) {
                sb.append(B32.charAt((i3 >>> (i4 - 5)) & 31));
                i4 -= 5;
            }
            i++;
            i2 = i4;
        }
        if (i2 > 0) {
            sb.append(B32.charAt((i3 << (5 - i2)) & 31));
        }
        return sb.toString();
    }

    static byte[] unbase32(String str) {
        int length = (str.length() * 5) / 8;
        byte[] bArr = new byte[length];
        int i = 0;
        int i2 = 0;
        int i3 = 0;
        int i4 = 0;
        while (i < str.length()) {
            int indexOf = B32.indexOf(str.charAt(i));
            if (indexOf < 0) {
                throw new IllegalArgumentException("bad base32");
            }
            i4 = (i4 << 5) | indexOf;
            i3 += 5;
            if (i3 >= 8) {
                if (i2 < length) {
                    bArr[i2] = (byte) (i4 >>> (i3 - 8));
                    i2++;
                }
                i3 -= 8;
            }
            i++;
            i2 = i2;
        }
        return bArr;
    }
}
