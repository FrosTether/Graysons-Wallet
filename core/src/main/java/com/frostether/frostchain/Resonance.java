package com.frostether.frostchain;

import com.frostether.frostchain.Bytes;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class Resonance {
    // v0.4: the analysis grid runs 2.0-14.8 Hz in 0.05 Hz steps (257 points), so the 33-bin proof
    // spectrum lands on whole 0.4 Hz steps. 0.3.x used 4.0-12.0 Hz.
    public static final double GRID_MIN = 2.0d;
    public static final double GRID_MAX = 14.8d;
    public static final double GRID_STEP = 0.05d;
    public static final int GRID = ((int) Math.round((GRID_MAX - GRID_MIN) / GRID_STEP)) + 1;
    public static final int SPECTRUM_BINS = 33;
    public static final double SPECTRUM_STEP_HZ = ((GRID - 1) / (SPECTRUM_BINS - 1)) * GRID_STEP;
    public static final double LOCK_MIN_SNR = 12.0d;
    public static final double LOCK_TOLERANCE_HZ = 0.25d;
    public static final double MAG_MIN_AMPLITUDE_UT = 0.02d;
    public static final long MAX_AGE_MS = 900000;
    public static final long MAX_AHEAD_MS = 120000;
    public static final int MAX_DUR_MS = 60000;
    public static final double MIC_MIN_DEPTH = 0.05d;
    public static final int MIN_DUR_MS = 4000;
    public static final int MIN_RATE_CENTI_HZ = 1600;
    public static final int MIN_SAMPLES = 200;
    public static final int MIN_SNR_X100 = 1000;
    public static final double TARGET_HZ = 7.83d;

    /**
     * v0.4 mining bands. The band a phone locks on sets how many threads it mines with (Miner.threadsForBand);
     * every band earns the same reward. A proof's frequency must sit inside one of these ranges.
     */
    public static final double[] BAND_HZ = {4.0d, 7.83d, 11.11d};
    public static final String[] BAND_NAMES = {"delta", "theta", "alpha"};
    public static final int[] BAND_MIN_MHZ = {3750, 7500, 10800};
    public static final int[] BAND_MAX_MHZ = {4250, 8200, 11400};

    private Resonance() {
    }

    /** The band whose consensus range contains this frequency, or -1. */
    public static int bandOfMilli(int hzMilli) {
        for (int b = 0; b < BAND_HZ.length; b++) {
            if (hzMilli >= BAND_MIN_MHZ[b] && hzMilli <= BAND_MAX_MHZ[b]) {
                return b;
            }
        }
        return -1;
    }

    /** The band a reading locks on: within LOCK_TOLERANCE_HZ of its tone, or -1. */
    public static int lockBand(double hz) {
        for (int b = 0; b < BAND_HZ.length; b++) {
            if (Math.abs(hz - BAND_HZ[b]) <= LOCK_TOLERANCE_HZ) {
                return b;
            }
        }
        return -1;
    }

    public static final class Reading {
        public boolean aliasSuspect;
        public double amplitude;
        public double depth;
        public byte[] digest;
        public long durMs;
        public boolean locked;
        /** Index into BAND_HZ when locked, otherwise -1. */
        public int band = -1;
        public double peakHz;
        public double rateHz;
        public int samples;
        public String sensor;
        public double snr;
        public long startMs;
        public double[] power = new double[Resonance.GRID];
        public String why = "";

        public Proof proof() {
            Proof proof = new Proof();
            proof.sensor = this.sensor;
            proof.hzMilli = (int) Math.round(this.peakHz * 1000.0d);
            proof.snrX100 = (int) Math.min(2147483647L, Math.round(this.snr * 100.0d));
            proof.ampMilli = (int) Math.min(2147483647L, Math.round(this.amplitude * 1000.0d));
            proof.samples = this.samples;
            proof.rateCenti = (int) Math.round(this.rateHz * 100.0d);
            proof.startMs = this.startMs;
            proof.durMs = this.durMs;
            proof.digest = this.digest;
            proof.spectrum = Resonance.spectrumBytes(this.power);
            return proof;
        }

        public Map<String, Object> toJson() {
            LinkedHashMap linkedHashMap = new LinkedHashMap();
            linkedHashMap.put("sensor", this.sensor);
            linkedHashMap.put("hz", Double.valueOf(Resonance.round(this.peakHz, 3)));
            linkedHashMap.put("snr", Double.valueOf(Resonance.round(this.snr, 1)));
            linkedHashMap.put("amplitude", Double.valueOf(Resonance.round(this.amplitude, 4)));
            linkedHashMap.put("depth", Double.valueOf(Resonance.round(this.depth, 3)));
            linkedHashMap.put("rate", Double.valueOf(Resonance.round(this.rateHz, 1)));
            linkedHashMap.put("samples", Integer.valueOf(this.samples));
            linkedHashMap.put("locked", Boolean.valueOf(this.locked));
            linkedHashMap.put("alias", Boolean.valueOf(this.aliasSuspect));
            linkedHashMap.put("band", this.band >= 0 ? BAND_NAMES[this.band] : "");
            linkedHashMap.put("bandHz", Double.valueOf(this.band >= 0 ? BAND_HZ[this.band] : 0.0d));
            linkedHashMap.put("why", this.why);
            int[] iArr = new int[33];
            byte[] spectrumBytes = Resonance.spectrumBytes(this.power);
            for (int i = 0; i < iArr.length; i++) {
                iArr[i] = spectrumBytes[i] & 255;
            }
            linkedHashMap.put("spectrum", iArr);
            return linkedHashMap;
        }
    }

    public static Reading analyze(String str, long[] jArr, float[][] fArr, int i, long j) {
        double d;
        Reading reading = new Reading();
        reading.sensor = str;
        reading.samples = i;
        reading.startMs = j;
        if (i < 16) {
            reading.why = "not enough samples yet";
            return reading;
        }
        double d2 = jArr[0];
        double d3 = (jArr[i - 1] - d2) / 1.0E9d;
        reading.durMs = Math.round(1000.0d * d3);
        reading.rateHz = d3 > 0.0d ? (i - 1) / d3 : 0.0d;
        reading.digest = digest(str, jArr, fArr, i);
        if (d3 < 2.0d) {
            reading.why = "window too short";
            return reading;
        }
        double[] dArr = new double[i];
        for (int i2 = 0; i2 < i; i2++) {
            dArr[i2] = (jArr[i2] - d2) / 1.0E9d;
        }
        double[] dArr2 = new double[i];
        double d4 = 0.0d;
        int i3 = 0;
        while (true) {
            d = d4;
            if (i3 >= i) {
                break;
            }
            dArr2[i3] = 0.5d - (0.5d * Math.cos((6.283185307179586d * dArr[i3]) / d3));
            d4 = dArr2[i3] + d;
            i3++;
        }
        double[][] dArr3 = new double[fArr.length][];
        double d5 = 0.0d;
        for (int i4 = 0; i4 < fArr.length; i4++) {
            dArr3[i4] = detrend(dArr, fArr[i4], i);
            if (i4 == 0) {
                for (int i5 = 0; i5 < i; i5++) {
                    d5 += Math.abs(fArr[i4][i5]);
                }
                d5 /= i;
            }
        }
        double[][] dArr4 = (double[][]) Array.newInstance((Class<?>) Double.TYPE, fArr.length, GRID);
        double[][] dArr5 = (double[][]) Array.newInstance((Class<?>) Double.TYPE, fArr.length, GRID);
        for (int i6 = 0; i6 < GRID; i6++) {
            double d6 = 6.283185307179586d * (GRID_MIN + (i6 * GRID_STEP));
            double d7 = 0.0d;
            for (int i7 = 0; i7 < fArr.length; i7++) {
                double d8 = 0.0d;
                double d9 = 0.0d;
                double[] dArr6 = dArr3[i7];
                for (int i8 = 0; i8 < i; i8++) {
                    double d10 = dArr6[i8] * dArr2[i8];
                    double d11 = dArr[i8] * d6;
                    d8 += Math.cos(d11) * d10;
                    d9 += d10 * Math.sin(d11);
                }
                dArr4[i7][i6] = d8;
                dArr5[i7][i6] = d9;
                d7 += (d9 * d9) + (d8 * d8);
            }
            reading.power[i6] = d7;
        }
        int i9 = 0;
        for (int i10 = 1; i10 < GRID; i10++) {
            if (reading.power[i10] > reading.power[i9]) {
                i9 = i10;
            }
        }
        double d12 = 0.0d;
        if (i9 > 0 && i9 < GRID - 1) {
            double d13 = reading.power[i9 - 1];
            double d14 = reading.power[i9];
            double d15 = reading.power[i9 + 1];
            double d16 = (d13 - (d14 * 2.0d)) + d15;
            if (d16 != 0.0d) {
                d12 = Math.max(-0.5d, Math.min(0.5d, ((d13 - d15) * 0.5d) / d16));
            }
        }
        reading.peakHz = ((d12 + i9) * GRID_STEP) + GRID_MIN;
        double[] dArr7 = new double[GRID];
        int i11 = 0;
        for (int i12 = 0; i12 < GRID; i12++) {
            if (Math.abs((GRID_MIN + (i12 * GRID_STEP)) - reading.peakHz) > 0.5d) {
                dArr7[i11] = reading.power[i12];
                i11++;
            }
        }
        double median = i11 > 0 ? median(dArr7, i11) : 0.0d;
        reading.snr = median > 0.0d ? reading.power[i9] / median : reading.power[i9] > 0.0d ? 1.0E9d : 0.0d;
        double d17 = 0.0d;
        for (int i13 = 0; i13 < fArr.length; i13++) {
            double sqrt = (2.0d * Math.sqrt((dArr4[i13][i9] * dArr4[i13][i9]) + (dArr5[i13][i9] * dArr5[i13][i9]))) / d;
            d17 += sqrt * sqrt;
        }
        reading.amplitude = Math.sqrt(d17);
        reading.depth = d5 > 0.0d ? reading.amplitude / d5 : 0.0d;
        if (str.equals("mag")) {
            reading.aliasSuspect = mainsAlias(reading.rateHz, reading.peakHz);
        }
        reading.locked = decide(reading);
        return reading;
    }

    private static boolean decide(Reading reading) {
        if (reading.durMs < 4000 || reading.samples < 200) {
            reading.why = "collecting samples";
            return false;
        }
        if (reading.rateHz * 100.0d < 1600.0d) {
            reading.why = "sensor too slow";
            return false;
        }
        if (reading.snr < 12.0d) {
            reading.why = "no clear peak";
            return false;
        }
        int band = lockBand(reading.peakHz);
        if (band < 0) {
            reading.why = String.format(Locale.ROOT, "peak at %.2f Hz, not 4.0, 7.83 or 11.11", Double.valueOf(reading.peakHz));
            return false;
        }
        if (reading.rateHz <= 2.0d * reading.peakHz) {
            reading.why = "sensor too slow for this tone";
            return false;
        }
        if (reading.aliasSuspect) {
            reading.why = "peak may be mains hum aliasing";
            return false;
        }
        if (reading.sensor.equals("mag") && reading.amplitude < 0.02d) {
            reading.why = "field too weak";
            return false;
        }
        if (!reading.sensor.equals("mic") || reading.depth >= 0.05d) {
            reading.band = band;
            reading.why = String.format(Locale.ROOT, "locked on %s Hz (%s)", BAND_HZ[band] == 4.0d ? "4.0" : String.valueOf(BAND_HZ[band]), BAND_NAMES[band]);
            return true;
        }
        reading.why = "pulse too faint";
        return false;
    }

    static boolean mainsAlias(double d, double d2) {
        if (d <= 0.0d) {
            return false;
        }
        for (double d3 : new double[]{50.0d, 60.0d, 100.0d, 120.0d, 150.0d, 180.0d}) {
            if (d3 >= d / 2.0d && Math.abs(Math.abs(d3 - (Math.round(d3 / d) * d)) - d2) < 0.4d) {
                return true;
            }
        }
        return false;
    }

    private static double[] detrend(double[] dArr, float[] fArr, int i) {
        double d = 0.0d;
        double d2 = 0.0d;
        double d3 = 0.0d;
        double d4 = 0.0d;
        for (int i2 = 0; i2 < i; i2++) {
            d += dArr[i2];
            d2 += fArr[i2];
            d3 += dArr[i2] * dArr[i2];
            d4 += dArr[i2] * fArr[i2];
        }
        double d5 = (i * d3) - (d * d);
        double d6 = d5 != 0.0d ? ((d4 * i) - (d * d2)) / d5 : 0.0d;
        double d7 = (d2 - (d6 * d)) / i;
        double[] dArr2 = new double[i];
        for (int i3 = 0; i3 < i; i3++) {
            dArr2[i3] = fArr[i3] - ((dArr[i3] * d6) + d7);
        }
        return dArr2;
    }

    private static double median(double[] dArr, int i) {
        double[] copyOf = Arrays.copyOf(dArr, i);
        Arrays.sort(copyOf);
        if ((i & 1) == 1) {
            return copyOf[i / 2];
        }
        return (copyOf[i / 2] + copyOf[(i / 2) - 1]) * 0.5d;
    }

    private static byte[] digest(String str, long[] jArr, float[][] fArr, int i) {
        Sha256 sha256 = new Sha256();
        sha256.update(Bytes.utf8("frostchain/resonance/" + str));
        byte[] bArr = new byte[(fArr.length * 4) + 8];
        for (int i2 = 0; i2 < i; i2++) {
            Bytes.u64(bArr, 0, jArr[i2]);
            for (int i3 = 0; i3 < fArr.length; i3++) {
                Bytes.u32(bArr, (i3 * 4) + 8, Float.floatToIntBits(fArr[i3][i2]) & 4294967295L);
            }
            sha256.update(bArr);
        }
        return sha256.digest();
    }

    static byte[] spectrumBytes(double[] dArr) {
        byte[] bArr = new byte[33];
        int length = dArr.length;
        int i = 0;
        double d = 0.0d;
        while (i < length) {
            double max = Math.max(d, dArr[i]);
            i++;
            d = max;
        }
        int i2 = (GRID - 1) / 32;
        for (int i3 = 0; i3 < 33; i3++) {
            double d2 = 0.0d;
            for (int max2 = Math.max(0, (i3 * i2) - (i2 / 2)); max2 <= Math.min(GRID - 1, (i3 * i2) + (i2 / 2)); max2++) {
                d2 = Math.max(d2, dArr[max2]);
            }
            bArr[i3] = (byte) (d > 0.0d ? Math.round((255.0d * d2) / d) : 0L);
        }
        return bArr;
    }

    public static double round(double d, int i) {
        double scale = Math.pow(10.0d, i);
        return Math.round(d * scale) / scale;
    }

    public static final class Proof {
        public int ampMilli;
        public byte[] digest;
        public long durMs;
        public int hzMilli;
        public int rateCenti;
        public int samples;
        public String sensor;
        public int snrX100;
        public byte[] spectrum;
        public long startMs;

        public byte[] encode() {
            return new Bytes.Writer().str8(Bytes.utf8(this.sensor)).u32(this.hzMilli).u32(this.snrX100).u32(this.ampMilli).u32(this.samples).u32(this.rateCenti).u64(this.startMs).u64(this.durMs).raw(this.digest).raw(this.spectrum).bytes();
        }

        public byte[] hash() {
            return Sha256.hash(Bytes.utf8("frostchain/resonance-proof"), encode());
        }

        public Map<String, Object> toJson() {
            return Json.o("sensor", this.sensor, "hz", Long.valueOf(this.hzMilli), "snr", Long.valueOf(this.snrX100), "amp", Long.valueOf(this.ampMilli), "n", Long.valueOf(this.samples), "rate", Long.valueOf(this.rateCenti), "start", Long.valueOf(this.startMs), "dur", Long.valueOf(this.durMs), "digest", Bytes.hex(this.digest), "spectrum", Bytes.hex(this.spectrum));
        }

        public static Proof fromJson(Map<String, Object> map) {
            Proof proof = new Proof();
            proof.sensor = Json.str(map, "sensor");
            proof.hzMilli = (int) Json.num(map, "hz");
            proof.snrX100 = (int) Json.num(map, "snr");
            proof.ampMilli = (int) Json.num(map, "amp");
            proof.samples = (int) Json.num(map, "n");
            proof.rateCenti = (int) Json.num(map, "rate");
            proof.startMs = Json.num(map, "start");
            proof.durMs = Json.num(map, "dur");
            proof.digest = Bytes.unhex(Json.str(map, "digest"), 32);
            proof.spectrum = Bytes.unhex(Json.str(map, "spectrum"), 33);
            return proof;
        }

        public String check(long j) {
            if (!"mag".equals(this.sensor) && !"mic".equals(this.sensor)) {
                return "unknown resonance sensor";
            }
            if (bandOfMilli(this.hzMilli) < 0) {
                return "resonance not at a mining tone";
            }
            if (this.snrX100 < 1000) {
                return "resonance peak too weak";
            }
            if (this.samples < 200) {
                return "too few resonance samples";
            }
            if (this.durMs < 4000 || this.durMs > 60000) {
                return "resonance window length out of range";
            }
            if (this.rateCenti < 1600) {
                return "resonance sample rate too low";
            }
            if (this.rateCenti * 5L <= this.hzMilli) {
                return "resonance sample rate too low for its frequency";
            }
            if (this.ampMilli <= 0) {
                return "no resonance amplitude";
            }
            if (this.digest == null || this.digest.length != 32 || Bytes.isZero(this.digest)) {
                return "missing resonance digest";
            }
            if (this.spectrum == null || this.spectrum.length != 33) {
                return "bad resonance spectrum";
            }
            int i = 0;
            for (int i2 = 1; i2 < 33; i2++) {
                if ((this.spectrum[i2] & 255) > (this.spectrum[i] & 255)) {
                    i = i2;
                }
            }
            if ((this.spectrum[i] & 255) != 255) {
                return "resonance spectrum not normalised";
            }
            if (Math.abs(((i * SPECTRUM_STEP_HZ) + GRID_MIN) - (this.hzMilli / 1000.0d)) > 0.38d) {
                return "resonance spectrum peak doesn't match its frequency";
            }
            long j2 = 1000 * j;
            if (this.startMs + this.durMs > Resonance.MAX_AHEAD_MS + j2) {
                return "resonance window is after the block";
            }
            if (this.startMs < j2 - Resonance.MAX_AGE_MS) {
                return "resonance window too old for this block";
            }
            return null;
        }
    }

    public static byte[] genesisMarker() {
        StringBuilder names = new StringBuilder(Consensus.FOUNDER_NAME + Address.SUFFIX);
        for (String alias : Consensus.FOUNDER_ALIASES) {
            names.append(" and ").append(alias).append(Address.SUFFIX);
        }
        return Sha256.hash(Bytes.utf8("Frostchain genesis: Schumann resonance 7.83 Hz, 8 Oct 2026 13:37 ET. Blocks every 5 minutes, never within 270 s. 13.37 QNR a block, 4.25% less each year. "
                + names + (Consensus.FOUNDER_ALIASES.isEmpty() ? " is " : " are ") + Consensus.FOUNDER_ADDRESS));
    }
}
