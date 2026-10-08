package com.frostether.frostchain;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;

public final class Lms {
    private static final int D_INTR = 33667;
    private static final int D_LEAF = 33410;
    private static final int D_MESG = 33153;
    private static final int D_PBLC = 32896;
    public static final int H = 10;
    public static final int LEAVES = 1024;
    public static final int N = 32;
    public static final int PUB_LEN = 56;
    public static final int TYPE_LMS = 6;
    public static final int TYPE_OTS = 3;
    private static final SecureRandom RNG = new SecureRandom();
    static final Params DEFAULT = new Params(6, 3);
    public static final int SIG_LEN = DEFAULT.sigLen();

    public interface Progress {
        void at(int i, int i2);
    }

    private Lms() {
    }

    static final class Params {
        final int h;
        final int lmsType;
        final int ls;
        final int otsType;
        final int p;
        final int w;

        Params(int i, int i2) {
            this.lmsType = i;
            this.otsType = i2;
            switch (i) {
                case 5:
                    this.h = 5;
                    break;
                case Lms.TYPE_LMS /* 6 */:
                    this.h = 10;
                    break;
                case 7:
                    this.h = 15;
                    break;
                case 8:
                    this.h = 20;
                    break;
                case 9:
                    this.h = 25;
                    break;
                default:
                    throw new IllegalArgumentException("unsupported LMS type " + i);
            }
            switch (i2) {
                case 1:
                    this.w = 1;
                    this.p = 265;
                    this.ls = 7;
                    return;
                case Tx.NAME /* 2 */:
                    this.w = 2;
                    this.p = 133;
                    this.ls = 6;
                    return;
                case 3:
                    this.w = 4;
                    this.p = 67;
                    this.ls = 4;
                    return;
                case 4:
                    this.w = 8;
                    this.p = 34;
                    this.ls = 0;
                    return;
                default:
                    throw new IllegalArgumentException("unsupported LM-OTS type " + i2);
            }
        }

        int otsSigLen() {
            return (this.p * 32) + 36;
        }

        int sigLen() {
            return otsSigLen() + 4 + 4 + (this.h * 32);
        }
    }

    public static final class PrivateKey {
        public final byte[] I;
        final Params params;
        public final byte[] pub;
        public final byte[] seed;
        final byte[][] tree;

        PrivateKey(Params params, byte[] bArr, byte[] bArr2, byte[][] bArr3) {
            this.params = params;
            this.I = bArr;
            this.seed = bArr2;
            this.tree = bArr3;
            this.pub = Bytes.concat(Bytes.u32(params.lmsType), Bytes.u32(params.otsType), bArr, bArr3[1]);
        }

        public int leaves() {
            return 1 << this.params.h;
        }
    }

    public static PrivateKey generate(byte[] bArr, byte[] bArr2, Progress progress) {
        return generate(DEFAULT, bArr, bArr2, progress);
    }

    static PrivateKey generate(final Params params, final byte[] bArr, final byte[] bArr2, final Progress progress) {
        if (bArr.length != 16 || bArr2.length != 32) {
            throw new IllegalArgumentException("I must be 16 bytes, SEED 32");
        }
        final int i = 1 << params.h;
        final byte[][] bArr3 = new byte[i * 2][];
        int max = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        final AtomicInteger atomicInteger = new AtomicInteger(0);
        final AtomicInteger atomicInteger2 = new AtomicInteger(0);
        Thread[] threadArr = new Thread[max];
        final Throwable[] thArr = new Throwable[1];
        for (int i2 = 0; i2 < max; i2++) {
            threadArr[i2] = new Thread(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    while (true) {
                        try {
                            int andIncrement = atomicInteger.getAndIncrement();
                            if (andIncrement < i) {
                                bArr3[i + andIncrement] = Lms.leafHash(bArr, andIncrement + i, Lms.otsPublic(params, bArr, bArr2, andIncrement));
                                int incrementAndGet = atomicInteger2.incrementAndGet();
                                if (progress != null && (incrementAndGet & 31) == 0) {
                                    progress.at(incrementAndGet, i);
                                }
                            } else {
                                return;
                            }
                        } finally {
                        }
                    }
                }
            }, "lms-keygen-" + i2);
            threadArr[i2].start();
        }
        for (Thread thread : threadArr) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        if (thArr[0] != null) {
            throw new RuntimeException("LMS key generation failed", thArr[0]);
        }
        for (int i3 = i - 1; i3 >= 1; i3--) {
            bArr3[i3] = interiorHash(bArr, i3, bArr3[i3 * 2], bArr3[(i3 * 2) + 1]);
        }
        return new PrivateKey(params, (byte[]) bArr.clone(), (byte[]) bArr2.clone(), bArr3);
    }

    public static byte[] sign(PrivateKey privateKey, int i, byte[] bArr) {
        byte[] bArr2 = new byte[32];
        RNG.nextBytes(bArr2);
        return sign(privateKey, i, bArr, bArr2);
    }

    public static byte[] sign(PrivateKey privateKey, int i, byte[] bArr, byte[] bArr2) {
        Params params = privateKey.params;
        if (i < 0 || i >= privateKey.leaves()) {
            throw new IllegalArgumentException("LMS key exhausted (leaf " + i + ")");
        }
        byte[] messageDigestWithChecksum = messageDigestWithChecksum(params, privateKey.I, i, bArr2, bArr);
        int[] words = words(privateKey.I, 0, 4);
        int[] words2 = words(privateKey.seed, 0, 8);
        byte[] bArr3 = new byte[params.sigLen()];
        Bytes.u32(bArr3, 0, i);
        Bytes.u32(bArr3, 4, params.otsType);
        System.arraycopy(bArr2, 0, bArr3, 8, 32);
        int[] iArr = new int[8];
        int[] iArr2 = new int[64];
        for (int i2 = 0; i2 < params.p; i2++) {
            System.arraycopy(words2, 0, iArr, 0, 8);
            chain(words, i, i2, 255, 256, iArr, iArr2);
            chain(words, i, i2, 0, coef(messageDigestWithChecksum, i2, params.w), iArr, iArr2);
            toBytes(iArr, bArr3, (i2 * 32) + 40);
        }
        int otsSigLen = params.otsSigLen() + 4;
        Bytes.u32(bArr3, otsSigLen, params.lmsType);
        int i3 = otsSigLen + 4;
        int leaves = privateKey.leaves() + i;
        for (int i4 = 0; i4 < params.h; i4++) {
            System.arraycopy(privateKey.tree[leaves ^ 1], 0, bArr3, i3, 32);
            i3 += 32;
            leaves >>>= 1;
        }
        return bArr3;
    }

    public static int sigLeaf(byte[] bArr) {
        if (bArr == null || bArr.length != SIG_LEN) {
            return -1;
        }
        long readU32 = Bytes.readU32(bArr, 0);
        if (readU32 < 1024) {
            return (int) readU32;
        }
        return -1;
    }

    public static boolean validPublicKey(byte[] bArr) {
        return bArr != null && bArr.length == 56 && Bytes.readU32(bArr, 0) == 6 && Bytes.readU32(bArr, 4) == 3;
    }

    public static boolean verify(byte[] bArr, byte[] bArr2, byte[] bArr3) {
        return validPublicKey(bArr) && bArr3 != null && bArr3.length == SIG_LEN && verifyAny(bArr, bArr2, bArr3);
    }

    public static boolean verifyAny(byte[] bArr, byte[] bArr2, byte[] bArr3) {
        if (bArr != null) {
            try {
                if (bArr.length == 56 && bArr3 != null && bArr3.length >= 8) {
                    long readU32 = Bytes.readU32(bArr, 0);
                    long readU322 = Bytes.readU32(bArr, 4);
                    Params params = new Params((int) readU32, (int) readU322);
                    if (bArr3.length != params.sigLen()) {
                        return false;
                    }
                    long readU323 = Bytes.readU32(bArr3, 0);
                    if (readU323 < (1 << params.h) && Bytes.readU32(bArr3, 4) == readU322 && Bytes.readU32(bArr3, params.otsSigLen() + 4) == readU32) {
                        byte[] slice = Bytes.slice(bArr, 8, 16);
                        byte[] slice2 = Bytes.slice(bArr, 24, 32);
                        byte[] messageDigestWithChecksum = messageDigestWithChecksum(params, slice, (int) readU323, Bytes.slice(bArr3, 8, 32), bArr2);
                        int[] words = words(slice, 0, 4);
                        int[] iArr = new int[8];
                        int[] iArr2 = new int[64];
                        Sha256 sha256 = new Sha256();
                        sha256.update(slice).update(Bytes.u32(readU323)).update(Bytes.u16(D_PBLC));
                        byte[] bArr4 = new byte[32];
                        for (int i = 0; i < params.p; i++) {
                            System.arraycopy(words(bArr3, (i * 32) + 40, 8), 0, iArr, 0, 8);
                            chain(words, (int) readU323, i, coef(messageDigestWithChecksum, i, params.w), (1 << params.w) - 1, iArr, iArr2);
                            toBytes(iArr, bArr4, 0);
                            sha256.update(bArr4);
                        }
                        byte[] digest = sha256.digest();
                        long j = (1 << params.h) + readU323;
                        byte[] leafHash = leafHash(slice, (int) j, digest);
                        int otsSigLen = params.otsSigLen() + 4 + 4;
                        int i2 = 0;
                        while (i2 < params.h) {
                            byte[] slice3 = Bytes.slice(bArr3, (i2 * 32) + otsSigLen, 32);
                            byte[] interiorHash = (1 & j) == 1 ? interiorHash(slice, (int) (j >>> 1), slice3, leafHash) : interiorHash(slice, (int) (j >>> 1), leafHash, slice3);
                            j >>>= 1;
                            i2++;
                            leafHash = interiorHash;
                        }
                        return Bytes.equal(leafHash, slice2);
                    }
                    return false;
                }
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return false;
    }

    public static byte[] otsPublic(Params params, byte[] bArr, byte[] bArr2, int i) {
        int[] words = words(bArr, 0, 4);
        int[] words2 = words(bArr2, 0, 8);
        int[] iArr = new int[8];
        int[] iArr2 = new int[64];
        Sha256 sha256 = new Sha256();
        sha256.update(bArr).update(Bytes.u32(i)).update(Bytes.u16(D_PBLC));
        byte[] bArr3 = new byte[32];
        for (int i2 = 0; i2 < params.p; i2++) {
            System.arraycopy(words2, 0, iArr, 0, 8);
            chain(words, i, i2, 255, 256, iArr, iArr2);
            chain(words, i, i2, 0, (1 << params.w) - 1, iArr, iArr2);
            toBytes(iArr, bArr3, 0);
            sha256.update(bArr3);
        }
        return sha256.digest();
    }

    public static byte[] leafHash(byte[] bArr, int i, byte[] bArr2) {
        return new Sha256().update(bArr).update(Bytes.u32(i)).update(Bytes.u16(D_LEAF)).update(bArr2).digest();
    }

    private static byte[] interiorHash(byte[] bArr, int i, byte[] bArr2, byte[] bArr3) {
        return new Sha256().update(bArr).update(Bytes.u32(i)).update(Bytes.u16(D_INTR)).update(bArr2).update(bArr3).digest();
    }

    private static byte[] messageDigestWithChecksum(Params params, byte[] bArr, int i, byte[] bArr2, byte[] bArr3) {
        byte[] digest = new Sha256().update(bArr).update(Bytes.u32(i)).update(Bytes.u16(D_MESG)).update(bArr2).update(bArr3).digest();
        int i2 = (1 << params.w) - 1;
        int i3 = 0;
        for (int i4 = 0; i4 < 256 / params.w; i4++) {
            i3 += i2 - coef(digest, i4, params.w);
        }
        int i5 = i3 << params.ls;
        byte[] bArr4 = new byte[34];
        System.arraycopy(digest, 0, bArr4, 0, 32);
        bArr4[32] = (byte) (i5 >>> 8);
        bArr4[33] = (byte) i5;
        return bArr4;
    }

    static int coef(byte[] bArr, int i, int i2) {
        return ((bArr[(i * i2) >>> 3] & 255) >>> (8 - (((i % (8 / i2)) * i2) + i2))) & ((1 << i2) - 1);
    }

    private static void chain(int[] iArr, int i, int i2, int i3, int i4, int[] iArr2, int[] iArr3) {
        while (i3 < i4) {
            iArr3[0] = iArr[0];
            iArr3[1] = iArr[1];
            iArr3[2] = iArr[2];
            iArr3[3] = iArr[3];
            iArr3[4] = i;
            iArr3[5] = (i2 << 16) | ((i3 & 255) << 8) | (iArr2[0] >>> 24);
            for (int i5 = 0; i5 < 7; i5++) {
                iArr3[i5 + 6] = (iArr2[i5] << 8) | (iArr2[i5 + 1] >>> 24);
            }
            iArr3[13] = (iArr2[7] << 8) | 128;
            iArr3[14] = 0;
            iArr3[15] = 440;
            System.arraycopy(Sha256.IV, 0, iArr2, 0, 8);
            Sha256.compressW(iArr2, iArr3);
            i3++;
        }
    }

    private static int[] words(byte[] bArr, int i, int i2) {
        int[] iArr = new int[i2];
        for (int i3 = 0; i3 < i2; i3++) {
            int i4 = (i3 * 4) + i;
            iArr[i3] = (bArr[i4 + 3] & 255) | (bArr[i4] << 24) | ((bArr[i4 + 1] & 255) << 16) | ((bArr[i4 + 2] & 255) << 8);
        }
        return iArr;
    }

    private static void toBytes(int[] iArr, byte[] bArr, int i) {
        for (int i2 = 0; i2 < 8; i2++) {
            bArr[(i2 * 4) + i] = (byte) (iArr[i2] >>> 24);
            bArr[(i2 * 4) + i + 1] = (byte) (iArr[i2] >>> 16);
            bArr[(i2 * 4) + i + 2] = (byte) (iArr[i2] >>> 8);
            bArr[(i2 * 4) + i + 3] = (byte) iArr[i2];
        }
    }
}
