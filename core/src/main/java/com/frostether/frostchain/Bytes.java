package com.frostether.frostchain;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.security.SecureRandom;

public final class Bytes {
    public static final Charset UTF8 = Charset.forName("UTF-8");
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    private Bytes() {
    }

    public static String hex(byte[] bArr) {
        if (bArr == null) {
            return "";
        }
        char[] cArr = new char[bArr.length * 2];
        for (int i = 0; i < bArr.length; i++) {
            cArr[i * 2] = HEX[(bArr[i] >> 4) & 15];
            cArr[(i * 2) + 1] = HEX[bArr[i] & 15];
        }
        return new String(cArr);
    }

    public static byte[] unhex(String str) {
        if (str == null) {
            throw new IllegalArgumentException("missing hex");
        }
        int length = str.length();
        if ((length & 1) != 0) {
            throw new IllegalArgumentException("odd-length hex");
        }
        byte[] bArr = new byte[length / 2];
        for (int i = 0; i < bArr.length; i++) {
            int digit = Character.digit(str.charAt(i * 2), 16);
            int digit2 = Character.digit(str.charAt((i * 2) + 1), 16);
            if (digit < 0 || digit2 < 0) {
                throw new IllegalArgumentException("bad hex");
            }
            bArr[i] = (byte) ((digit << 4) | digit2);
        }
        return bArr;
    }

    public static byte[] unhex(String str, int i) {
        byte[] unhex = unhex(str);
        if (unhex.length != i) {
            throw new IllegalArgumentException("expected " + i + " bytes of hex");
        }
        return unhex;
    }

    public static void u32(byte[] bArr, int i, long j) {
        bArr[i] = (byte) (j >>> 24);
        bArr[i + 1] = (byte) (j >>> 16);
        bArr[i + 2] = (byte) (j >>> 8);
        bArr[i + 3] = (byte) j;
    }

    public static byte[] u32(long j) {
        byte[] bArr = new byte[4];
        u32(bArr, 0, j);
        return bArr;
    }

    public static byte[] u16(int i) {
        return new byte[]{(byte) (i >>> 8), (byte) i};
    }

    public static void u64(byte[] bArr, int i, long j) {
        for (int i2 = 7; i2 >= 0; i2--) {
            bArr[i + i2] = (byte) j;
            j >>>= 8;
        }
    }

    public static byte[] u64(long j) {
        byte[] bArr = new byte[8];
        u64(bArr, 0, j);
        return bArr;
    }

    public static long readU32(byte[] bArr, int i) {
        // Unsigned, in long arithmetic as in the 0.3.0 bytecode.
        return ((bArr[i] & 255L) << 24) | ((bArr[i + 1] & 255L) << 16) | ((bArr[i + 2] & 255L) << 8) | (bArr[i + 3] & 255L);
    }

    public static long readU64(byte[] bArr, int i) {
        long j = 0;
        for (int i2 = 0; i2 < 8; i2++) {
            j = (j << 8) | (bArr[i + i2] & 255);
        }
        return j;
    }

    public static byte[] concat(byte[]... bArr) {
        int i = 0;
        for (byte[] bArr2 : bArr) {
            i += bArr2.length;
        }
        byte[] bArr3 = new byte[i];
        int i2 = 0;
        for (byte[] bArr4 : bArr) {
            System.arraycopy(bArr4, 0, bArr3, i2, bArr4.length);
            i2 += bArr4.length;
        }
        return bArr3;
    }

    public static byte[] slice(byte[] bArr, int i, int i2) {
        if (i < 0 || i2 < 0 || i + i2 > bArr.length) {
            throw new IllegalArgumentException("slice out of range");
        }
        byte[] bArr2 = new byte[i2];
        System.arraycopy(bArr, i, bArr2, 0, i2);
        return bArr2;
    }

    public static boolean equal(byte[] bArr, byte[] bArr2) {
        if (bArr == null || bArr2 == null || bArr.length != bArr2.length) {
            return false;
        }
        int i = 0;
        for (int i2 = 0; i2 < bArr.length; i2++) {
            i |= bArr[i2] ^ bArr2[i2];
        }
        return i == 0;
    }

    public static byte[] random(int i) {
        byte[] bArr = new byte[i];
        RNG.nextBytes(bArr);
        return bArr;
    }

    public static byte[] utf8(String str) {
        return str.getBytes(UTF8);
    }

    public static boolean isZero(byte[] bArr) {
        int i = 0;
        for (byte b : bArr) {
            i |= b;
        }
        return i == 0;
    }

    public static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream(256);

        public Writer raw(byte[] bArr) {
            this.out.write(bArr, 0, bArr.length);
            return this;
        }

        public Writer u8(int i) {
            this.out.write(i & 255);
            return this;
        }

        public Writer u32(long j) {
            return raw(Bytes.u32(j));
        }

        public Writer u64(long j) {
            return raw(Bytes.u64(j));
        }

        public Writer str8(byte[] bArr) {
            if (bArr.length > 255) {
                throw new IllegalArgumentException("field too long");
            }
            u8(bArr.length);
            return raw(bArr);
        }

        public byte[] bytes() {
            return this.out.toByteArray();
        }
    }
}
