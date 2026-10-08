package com.frostether.frostchain;

import com.frostether.frostchain.Chain;
import com.frostether.frostchain.Resonance;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;

public final class Miner {
    public static final long MAX_READING_AGE_MS = 20000;
    /** When genesis happens, in Eastern time, for the Frostoise screen. */
    static final String LAUNCH = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.ENGLISH)
            .format(java.time.Instant.ofEpochSecond(Consensus.GENESIS_TIME).atZone(java.time.ZoneId.of("America/New_York"))) + " Eastern";
    private volatile int accepted;
    private volatile Template current;
    private volatile int found;
    private volatile double hashrate;
    private volatile Listener listener;
    private final Node node;
    private volatile byte[] payout;
    private Thread refresher;
    private volatile boolean running;
    private volatile ResonanceSource source;
    private volatile ThermalSource thermal;
    /** The last heat level read: 0 fine to 3 too hot. */
    private volatile int heat;
    /** Seconds until the chain accepts the next block (Consensus.MIN_BLOCK_SPACING), 0 when it's open. */
    private volatile long nextIn;
    private volatile int threads = 1;
    /** How many of the threads hash right now: set by the band the phone is locked on (v0.4). */
    private volatile int active = 1;
    private volatile int band = -1;
    private Thread[] workers = new Thread[0];
    private final long[] hashCounts = new long[64];
    private volatile String lastBlock = "";
    private volatile String gateWhy = "not started";
    private final Object wake = new Object();
    private final Object submitLock = new Object();
    private final SecureRandom rng = new SecureRandom();

    public interface Listener {
        void found(Block block, boolean z, String str);
    }

    public interface ResonanceSource {
        Resonance.Reading latest();
    }

    /**
     * How hot the phone is, so mining on high doesn't cook it: 0 fine, 1 warm (halve the threads),
     * 2 hot (one thread), 3 too hot (pause until it cools).
     */
    public interface ThermalSource {
        int level();
    }

    static final class Template {
        final Block block;
        final int[] mid = new int[8];
        final int[] tail = new int[4];
        final int[] target = new int[8];

        Template(Block block) {
            this.block = block;
            byte[] header = block.header();
            System.arraycopy(Sha256.IV, 0, this.mid, 0, 8);
            int[] iArr = new int[64];
            Sha256.compress(this.mid, header, 0, iArr);
            Sha256.compress(this.mid, header, 64, iArr);
            for (int i = 0; i < 4; i++) {
                this.tail[i] = Miner.word(header, (i * 4) + 128);
            }
            byte[] target = Consensus.target(block.difficulty);
            for (int i2 = 0; i2 < 8; i2++) {
                this.target[i2] = Miner.word(target, i2 * 4);
            }
        }
    }

    public Miner(Node node) {
        this.node = node;
    }

    public void setSource(ResonanceSource resonanceSource) {
        this.source = resonanceSource;
    }

    public void setThermal(ThermalSource thermalSource) {
        this.thermal = thermalSource;
    }

    private int readHeat() {
        ThermalSource t = this.thermal;
        if (t == null) {
            return 0;
        }
        try {
            return Math.max(0, Math.min(3, t.level()));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Fewer threads as the phone heats up: warm halves them, hot leaves one. Level 3 pauses mining. */
    public static int coolCap(int heat, int active) {
        switch (heat) {
            case 0:
                return active;
            case 1:
                return Math.max(1, (active + 1) / 2);
            default:
                return Math.min(1, active);
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public byte[] payout() {
        return this.payout;
    }

    public boolean running() {
        return this.running;
    }

    public int threads() {
        return this.threads;
    }

    // Rebuilt from the 0.3.0 bytecode: the decompiled version threw "needs a payout address" even after starting.
    public synchronized void start(byte[] bArr, int i) {
        if (bArr == null || bArr.length != 20) {
            throw new IllegalArgumentException("Frostoise needs a payout address");
        }
        stop();
        this.payout = (byte[]) bArr.clone();
        this.threads = Math.max(1, Math.min(Math.min(32, this.hashCounts.length), i));
        this.active = this.threads;
        this.band = -1;
        this.running = true;
        this.found = 0;
        this.accepted = 0;
        this.refresher = new Thread(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                Miner.this.refreshLoop();
            }
        }, "frostoise-template");
        this.refresher.setDaemon(true);
        this.refresher.start();
        this.workers = new Thread[this.threads];
        for (int t = 0; t < this.threads; t++) {
            final int i2 = t;
            this.workers[i2] = new Thread(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    Miner.this.hashLoop(i2);
                }
            }, "frostoise-" + i2);
            this.workers[i2].setDaemon(true);
            this.workers[i2].setPriority(1);
            this.workers[i2].start();
        }
        Log.i("frostoise", "mining with " + this.threads + " thread(s) to " + Address.raw(this.payout));
    }

    public synchronized void stop() {
        synchronized (this) {
            this.running = false;
            synchronized (this.wake) {
                this.wake.notifyAll();
            }
            for (Thread thread : this.workers) {
                try {
                    thread.join(2000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (this.refresher != null) {
                try {
                    this.refresher.join(2000L);
                } catch (InterruptedException e2) {
                    Thread.currentThread().interrupt();
                }
            }
            this.workers = new Thread[0];
            this.refresher = null;
            this.current = null;
            this.hashrate = 0.0d;
        }
    }

    public void refresh() {
        synchronized (this.wake) {
            this.wake.notifyAll();
        }
    }

    public Map<String, Object> status() {
        Template template = this.current;
        Object[] objArr = new Object[16];
        objArr[0] = "running";
        objArr[1] = Boolean.valueOf(this.running);
        objArr[2] = "threads";
        objArr[3] = Long.valueOf(this.threads);
        objArr[4] = "hashrate";
        objArr[5] = Long.valueOf(Math.round(this.hashrate));
        objArr[6] = "mining";
        objArr[7] = Boolean.valueOf(template != null);
        objArr[8] = "gate";
        objArr[9] = this.gateWhy;
        objArr[10] = "found";
        objArr[11] = Long.valueOf(this.found);
        objArr[12] = "accepted";
        objArr[13] = Long.valueOf(this.accepted);
        objArr[14] = "lastBlock";
        objArr[15] = this.lastBlock;
        Map<String, Object> o = Json.o(objArr);
        o.put("active", Long.valueOf(this.active));
        o.put("heat", Long.valueOf(this.heat));
        o.put("nextIn", Long.valueOf(this.nextIn));
        o.put("band", this.band >= 0 ? Resonance.BAND_NAMES[this.band] : "");
        o.put("level", this.band == 0 ? "low" : this.band == 1 ? "medium" : this.band == 2 ? "high" : "");
        if (this.payout != null) {
            o.put("payout", Address.raw(this.payout));
        }
        long nextDifficulty = this.node.chain.nextDifficulty();
        o.put("difficulty", U64.str(nextDifficulty));
        o.put("expectedSeconds", Long.valueOf(this.hashrate > 0.0d ? Math.round(nextDifficulty / this.hashrate) : -1L));
        return o;
    }

    public void refreshLoop() {
        long[] jArr = new long[this.hashCounts.length];
        long nanoTime = System.nanoTime();
        while (this.running) {
            try {
                this.current = build();
            } catch (RuntimeException e) {
                this.current = null;
                this.gateWhy = "error: " + e.getMessage();
                Log.w("frostoise", "template: " + e);
            }
            synchronized (this.wake) {
                try {
                    this.wake.wait(3000L);
                } catch (InterruptedException e2) {
                    return;
                }
            }
            long nanoTime2 = System.nanoTime();
            long j = 0;
            for (int i = 0; i < this.hashCounts.length; i++) {
                j += this.hashCounts[i] - jArr[i];
                jArr[i] = this.hashCounts[i];
            }
            double d = (nanoTime2 - nanoTime) / 1.0E9d;
            if (d > 0.0d) {
                this.hashrate = ((j / d) * 0.4d) + (0.6d * this.hashrate);
            }
            nanoTime = nanoTime2;
        }
    }

    private Template build() {
        ResonanceSource resonanceSource = this.source;
        Resonance.Reading latest = resonanceSource == null ? null : resonanceSource.latest();
        long now = this.node.chain.now() * 1000;
        if (this.node.chain.height() == 0 && this.node.chain.now() < Consensus.GENESIS_TIME) {
            // A block can't be older than genesis, so mining opens at the launch moment.
            this.gateWhy = "mining opens at launch: " + LAUNCH;
            return null;
        }
        long opens = this.node.chain.tip().time + Consensus.MIN_BLOCK_SPACING;
        long nowSec = this.node.chain.now();
        if (nowSec < opens) {
            long wait = opens - nowSec;
            this.nextIn = wait;
            this.gateWhy = "next block in " + (wait / 60) + ":" + String.format(Locale.ROOT, "%02d", Long.valueOf(wait % 60));
            return null;
        }
        this.nextIn = 0;
        if (latest == null) {
            this.gateWhy = "resonance sensor is off";
            return null;
        }
        if (!latest.locked) {
            this.gateWhy = "waiting for a mining tone: " + latest.why;
            return null;
        }
        if (now - (latest.startMs + latest.durMs) > MAX_READING_AGE_MS) {
            this.gateWhy = "resonance reading is stale";
            return null;
        }
        int heatNow = readHeat();
        this.heat = heatNow;
        if (heatNow >= 3) {
            this.gateWhy = "cooling down: the phone is too hot to mine";
            return null;
        }
        Block tip = this.node.chain.tip();
        Block block = new Block();
        block.height = tip.height + 1;
        block.prev = tip.hash();
        block.time = Math.max(this.node.chain.now(), this.node.chain.minNextTime());
        block.difficulty = this.node.chain.nextDifficulty();
        block.txs.addAll(this.node.mempool.select(200, this.payout));
        block.root = Block.txRoot(block.txs, this.node.chain.chainId);
        block.miner = this.payout;
        block.reso = latest.proof();
        String check = block.reso.check(block.time);
        if (check != null) {
            this.gateWhy = "resonance proof: " + check;
            return null;
        }
        block.resoHash = block.reso.hash();
        int locked = latest.band >= 0 ? latest.band : Resonance.lockBand(latest.peakHz);
        this.band = locked;
        this.active = coolCap(heatNow, threadsForBand(locked, this.threads));
        this.gateWhy = "mining: locked on " + String.format(Locale.ROOT, "%.2f", Double.valueOf(latest.peakHz)) + " Hz"
                + (heatNow > 0 ? ", fewer threads while the phone cools" : "");
        return new Template(block);
    }

    public void hashLoop(int i) {
        int[] iArr = new int[64];
        int[] iArr2 = new int[64];
        int[] iArr3 = new int[8];
        while (this.running) {
            Template template = this.current;
            if (template == null || i >= this.active) {
                synchronized (this.wake) {
                    try {
                        this.wake.wait(500L);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            } else {
                System.arraycopy(template.tail, 0, iArr, 0, 4);
                iArr[6] = Integer.MIN_VALUE;
                for (int i2 = 7; i2 < 15; i2++) {
                    iArr[i2] = 0;
                }
                iArr[15] = 1216;
                for (int i3 = 9; i3 < 15; i3++) {
                    iArr2[i3] = 0;
                }
                iArr2[8] = Integer.MIN_VALUE;
                iArr2[15] = 256;
                long nextLong = this.rng.nextLong();
                // Hash in batches of 16,384, then recheck that the job is still current.
                // Rebuilt from the 0.3.0 bytecode; the decompiled batch loop never ended.
                while (this.running && this.current == template && i < this.active) {
                    for (int i4 = 0; i4 < 16384; i4++) {
                        System.arraycopy(template.mid, 0, iArr3, 0, 8);
                        iArr[4] = (int) (nextLong >>> 32);
                        iArr[5] = (int) nextLong;
                        Sha256.compressW(iArr3, iArr);
                        System.arraycopy(iArr3, 0, iArr2, 0, 8);
                        System.arraycopy(Sha256.IV, 0, iArr3, 0, 8);
                        Sha256.compressW(iArr3, iArr2);
                        if (Integer.compareUnsigned(iArr3[0], template.target[0]) <= 0 && meets(iArr3, template.target)) {
                            submit(template, nextLong);
                            break;
                        }
                        nextLong++;
                    }
                    long[] jArr = this.hashCounts;
                    jArr[i] = jArr[i] + 16384;
                }
            }
        }
    }

    /**
     * v0.4 tone presets. The thread slider sets the most a phone may use; the band picks how many of those hash:
     * delta 4.0 Hz is low (up to 2), theta 7.83 Hz is medium (about half), alpha 11.11 Hz is high (all of them).
     */
    public static int threadsForBand(int band, int max) {
        int m = Math.max(1, max);
        switch (band) {
            case 0:
                return Math.min(2, m);
            case 1:
                return Math.max(1, (m + 1) / 2);
            default:
                return m;
        }
    }

    private static boolean meets(int[] iArr, int[] iArr2) {
        for (int i = 0; i < 8; i++) {
            int compareUnsigned = Integer.compareUnsigned(iArr[i], iArr2[i]);
            if (compareUnsigned != 0) {
                return compareUnsigned < 0;
            }
        }
        return true;
    }

    private void submit(Template template, long j) {
        synchronized (this.submitLock) {
            if (this.current == template) {
                this.current = null;
                Block fromJson = Block.fromJson(template.block.toJson());
                fromJson.nonce = j;
                fromJson.invalidateHash();
                this.found++;
                Chain.Result submitBlock = this.node.submitBlock(fromJson, null);
                if (submitBlock.ok) {
                    this.accepted++;
                    this.lastBlock = fromJson.height + " " + fromJson.hashHex();
                }
                Log.i("frostoise", "found block " + fromJson.height + (submitBlock.ok ? " (accepted)" : " (rejected: " + submitBlock.error + ")"));
                Listener listener = this.listener;
                if (listener != null) {
                    listener.found(fromJson, submitBlock.ok, submitBlock.error);
                }
                refresh();
            }
        }
    }

    public static int word(byte[] bArr, int i) {
        return (bArr[i] << 24) | ((bArr[i + 1] & 255) << 16) | ((bArr[i + 2] & 255) << 8) | (bArr[i + 3] & 255);
    }
}
