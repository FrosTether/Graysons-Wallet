package com.frostether.frostchain;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;

public final class Mnemonic {
    private static final String[] WORDS = MnemonicWords.EN;
    private static final int NW = WORDS.length;
    private static final Map<String, Integer> PREFIX = new HashMap();

    private Mnemonic() {
    }

    static {
        for (int i = 0; i < NW; i++) {
            PREFIX.put(WORDS[i].substring(0, 3), Integer.valueOf(i));
        }
    }

    public static String encode(byte[] bArr) {
        if (bArr.length != 32) {
            throw new IllegalArgumentException("seed must be 32 bytes");
        }
        String[] strArr = new String[25];
        for (int i = 0; i < 8; i++) {
            // Unsigned little-endian 32-bit chunk, built in long arithmetic as in the 0.3.0 bytecode.
            long j = (bArr[i * 4] & 255L) | ((bArr[(i * 4) + 1] & 255L) << 8) | ((bArr[(i * 4) + 2] & 255L) << 16) | ((bArr[(i * 4) + 3] & 255L) << 24);
            long j2 = j % NW;
            long j3 = ((j / NW) + j2) % NW;
            strArr[i * 3] = WORDS[(int) j2];
            strArr[(i * 3) + 1] = WORDS[(int) j3];
            strArr[(i * 3) + 2] = WORDS[(int) ((((j / NW) / NW) + j3) % NW)];
        }
        strArr[24] = strArr[checksumIndex(strArr, 24)];
        StringBuilder sb = new StringBuilder();
        for (int i2 = 0; i2 < 25; i2++) {
            if (i2 > 0) {
                sb.append(' ');
            }
            sb.append(strArr[i2]);
        }
        return sb.toString();
    }

    public static byte[] decode(String str) {
        int i;
        String[] split = str.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (split.length != 24 && split.length != 25) {
            throw new IllegalArgumentException("a seed has 25 words (found " + split.length + ")");
        }
        int[] iArr = new int[24];
        String[] strArr = new String[24];
        int i2 = 0;
        while (true) {
            i = i2;
            if (i < 24) {
                Integer num = split[i].length() >= 3 ? PREFIX.get(split[i].substring(0, 3)) : null;
                if (num == null || !(WORDS[num.intValue()].startsWith(split[i]) || split[i].startsWith(WORDS[num.intValue()].substring(0, 3)))) {
                    break;
                }
                iArr[i] = num.intValue();
                strArr[i] = WORDS[num.intValue()];
                i2 = i + 1;
            } else {
                if (split.length == 25) {
                    String substring = strArr[checksumIndex(strArr, 24)].substring(0, 3);
                    if (split[24].length() < 3 || !split[24].substring(0, 3).equals(substring)) {
                        throw new IllegalArgumentException("checksum word doesn't match: check the words for typos");
                    }
                }
                byte[] bArr = new byte[32];
                for (int i3 = 0; i3 < 8; i3++) {
                    long j = iArr[i3 * 3];
                    long j2 = iArr[(i3 * 3) + 1];
                    long j3 = ((((NW - j2) + iArr[(i3 * 3) + 2]) % NW) * NW * NW) + (NW * (((NW - j) + j2) % NW)) + j;
                    if (j3 >= 4294967296L || j3 % NW != j) {
                        throw new IllegalArgumentException("words " + ((i3 * 3) + 1) + "-" + ((i3 * 3) + 3) + " don't form a valid group");
                    }
                    bArr[i3 * 4] = (byte) j3;
                    bArr[(i3 * 4) + 1] = (byte) (j3 >>> 8);
                    bArr[(i3 * 4) + 2] = (byte) (j3 >>> 16);
                    bArr[(i3 * 4) + 3] = (byte) (j3 >>> 24);
                }
                return bArr;
            }
        }
        throw new IllegalArgumentException("word " + (i + 1) + " \"" + split[i] + "\" is not in the word list");
    }

    private static int checksumIndex(String[] strArr, int i) {
        StringBuilder sb = new StringBuilder();
        for (int i2 = 0; i2 < i; i2++) {
            sb.append((CharSequence) strArr[i2], 0, 3);
        }
        CRC32 crc32 = new CRC32();
        crc32.update(Bytes.utf8(sb.toString()));
        return (int) (crc32.getValue() % i);
    }
}
