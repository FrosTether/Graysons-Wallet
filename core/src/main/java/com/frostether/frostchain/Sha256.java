package com.frostether.frostchain;

public final class Sha256 {
    private int bufLen;
    private long total;
    private static final int[] K = {1116352408, 1899447441, -1245643825, -373957723, 961987163, 1508970993, -1841331548, -1424204075, -670586216, 310598401, 607225278, 1426881987, 1925078388, -2132889090, -1680079193, -1046744716, -459576895, -272742522, 264347078, 604807628, 770255983, 1249150122, 1555081692, 1996064986, -1740746414, -1473132947, -1341970488, -1084653625, -958395405, -710438585, 113926993, 338241895, 666307205, 773529912, 1294757372, 1396182291, 1695183700, 1986661051, -2117940946, -1838011259, -1564481375, -1474664885, -1035236496, -949202525, -778901479, -694614492, -200395387, 275423344, 430227734, 506948616, 659060556, 883997877, 958139571, 1322822218, 1537002063, 1747873779, 1955562222, 2024104815, -2067236844, -1933114872, -1866530822, -1538233109, -1090935817, -965641998};
    static final int[] IV = {1779033703, -1150833019, 1013904242, -1521486534, 1359893119, -1694144372, 528734635, 1541459225};
    private final int[] h = new int[8];
    private final byte[] buf = new byte[64];
    private final int[] w = new int[64];

    public Sha256() {
        reset();
    }

    public Sha256 reset() {
        System.arraycopy(IV, 0, this.h, 0, 8);
        this.bufLen = 0;
        this.total = 0L;
        return this;
    }

    public Sha256 copyFrom(Sha256 sha256) {
        System.arraycopy(sha256.h, 0, this.h, 0, 8);
        System.arraycopy(sha256.buf, 0, this.buf, 0, 64);
        this.bufLen = sha256.bufLen;
        this.total = sha256.total;
        return this;
    }

    public Sha256 update(byte[] bArr) {
        return update(bArr, 0, bArr.length);
    }

    public Sha256 update(byte[] bArr, int i, int i2) {
        this.total += i2;
        if (this.bufLen > 0) {
            int min = Math.min(64 - this.bufLen, i2);
            System.arraycopy(bArr, i, this.buf, this.bufLen, min);
            this.bufLen += min;
            i += min;
            i2 -= min;
            if (this.bufLen == 64) {
                compress(this.h, this.buf, 0, this.w);
                this.bufLen = 0;
            }
        }
        while (i2 >= 64) {
            compress(this.h, bArr, i, this.w);
            i += 64;
            i2 -= 64;
        }
        if (i2 > 0) {
            System.arraycopy(bArr, i, this.buf, 0, i2);
            this.bufLen = i2;
        }
        return this;
    }

    public Sha256 update(int i) {
        byte[] bArr = this.buf;
        int i2 = this.bufLen;
        this.bufLen = i2 + 1;
        bArr[i2] = (byte) i;
        this.total++;
        if (this.bufLen == 64) {
            compress(this.h, this.buf, 0, this.w);
            this.bufLen = 0;
        }
        return this;
    }

    public void digest(byte[] bArr, int i) {
        long j = this.total << 3;
        byte[] bArr2 = this.buf;
        int i2 = this.bufLen;
        this.bufLen = i2 + 1;
        bArr2[i2] = Byte.MIN_VALUE;
        if (this.bufLen > 56) {
            while (this.bufLen < 64) {
                byte[] bArr3 = this.buf;
                int i3 = this.bufLen;
                this.bufLen = i3 + 1;
                bArr3[i3] = 0;
            }
            compress(this.h, this.buf, 0, this.w);
            this.bufLen = 0;
        }
        while (this.bufLen < 56) {
            byte[] bArr4 = this.buf;
            int i4 = this.bufLen;
            this.bufLen = i4 + 1;
            bArr4[i4] = 0;
        }
        for (int i5 = 7; i5 >= 0; i5--) {
            this.buf[i5 + 56] = (byte) j;
            j >>>= 8;
        }
        compress(this.h, this.buf, 0, this.w);
        for (int i6 = 0; i6 < 8; i6++) {
            bArr[(i6 * 4) + i] = (byte) (this.h[i6] >>> 24);
            bArr[(i6 * 4) + i + 1] = (byte) (this.h[i6] >>> 16);
            bArr[(i6 * 4) + i + 2] = (byte) (this.h[i6] >>> 8);
            bArr[(i6 * 4) + i + 3] = (byte) this.h[i6];
        }
        reset();
    }

    public byte[] digest() {
        byte[] bArr = new byte[32];
        digest(bArr, 0);
        return bArr;
    }

    public static byte[] hash(byte[] bArr) {
        return new Sha256().update(bArr).digest();
    }

    public static byte[] hash(byte[]... bArr) {
        Sha256 sha256 = new Sha256();
        for (byte[] bArr2 : bArr) {
            sha256.update(bArr2);
        }
        return sha256.digest();
    }

    public static byte[] hash2(byte[] bArr) {
        return hash(hash(bArr));
    }

    static void compress(int[] iArr, byte[] bArr, int i, int[] iArr2) {
        for (int i2 = 0; i2 < 16; i2++) {
            int i3 = (i2 * 4) + i;
            iArr2[i2] = (bArr[i3 + 3] & 255) | (bArr[i3] << 24) | ((bArr[i3 + 1] & 255) << 16) | ((bArr[i3 + 2] & 255) << 8);
        }
        compressW(iArr, iArr2);
    }

    static void compressW(int[] iArr, int[] iArr2) {
        for (int i = 16; i < 64; i++) {
            int i2 = iArr2[i - 15];
            int i3 = iArr2[i - 2];
            iArr2[i] = ((i2 >>> 3) ^ (Integer.rotateRight(i2, 7) ^ Integer.rotateRight(i2, 18))) + iArr2[i - 16] + iArr2[i - 7] + ((i3 >>> 10) ^ (Integer.rotateRight(i3, 17) ^ Integer.rotateRight(i3, 19)));
        }
        int i4 = iArr[0];
        int i5 = iArr[1];
        int i6 = iArr[2];
        int i7 = iArr[3];
        int i8 = iArr[4];
        int i9 = iArr[5];
        int i10 = iArr[6];
        int i11 = iArr[7];
        int i12 = 0;
        int i13 = i7;
        while (i12 < 64) {
            int rotateRight = i11 + ((Integer.rotateRight(i8, 6) ^ Integer.rotateRight(i8, 11)) ^ Integer.rotateRight(i8, 25)) + ((i8 & i9) ^ ((i8 ^ (-1)) & i10)) + K[i12] + iArr2[i12];
            int i14 = i13 + rotateRight;
            int rotateRight2 = (((i4 & i5) ^ (i4 & i6)) ^ (i5 & i6)) + ((Integer.rotateRight(i4, 2) ^ Integer.rotateRight(i4, 13)) ^ Integer.rotateRight(i4, 22)) + rotateRight;
            i12++;
            i11 = i10;
            i13 = i6;
            i10 = i9;
            i6 = i5;
            i9 = i8;
            i5 = i4;
            i8 = i14;
            i4 = rotateRight2;
        }
        iArr[0] = iArr[0] + i4;
        iArr[1] = iArr[1] + i5;
        iArr[2] = iArr[2] + i6;
        iArr[3] = iArr[3] + i13;
        iArr[4] = i8 + iArr[4];
        iArr[5] = i9 + iArr[5];
        iArr[6] = i10 + iArr[6];
        iArr[7] = i11 + iArr[7];
    }
}
