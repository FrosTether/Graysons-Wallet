package com.frostether.desktop;

import com.frostether.frostchain.Api;
import com.frostether.frostchain.Json;
import com.frostether.frostchain.Log;
import com.frostether.frostchain.Resonance;
import java.io.File;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * Frostoise's sensors on a Linux laptop. The microphone works like the phone's: 44.1 kHz audio, averaged over
 * 441 samples into a 100 Hz loudness envelope, which Resonance analyses for the mining tone. Laptops have no
 * magnetometer. Heat comes from the kernel's thermal zones.
 */
final class DesktopPlatform implements Api.Platform {
    private static final int CAP = 4096;
    private static final long WINDOW_NS = 8_000_000_000L;
    private static final float RATE = 44100f;

    private final long[] t = new long[CAP];
    private final float[] v = new float[CAP];
    private final Object lock = new Object();
    private int head;
    private int count;

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread th = new Thread(r, "frostoise-analyse");
        th.setDaemon(true);
        return th;
    });
    private ScheduledFuture<?> analysis;
    private volatile TargetDataLine line;
    private Thread micThread;
    private volatile String running = "";
    private volatile String error = "";
    private volatile Resonance.Reading latest;
    private volatile boolean mining;
    private volatile int heat;

    @Override
    public Map<String, Object> sensors() {
        return Json.o("mag", false, "mic", hasMic(), "running", running, "error", error, "magName", "");
    }

    private static boolean hasMic() {
        try {
            return AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, format()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static AudioFormat format() {
        return new AudioFormat(RATE, 16, 1, true, false);
    }

    @Override
    public synchronized void startSensor(String sensor) throws Exception {
        if (sensor.equals(running)) {
            return;
        }
        if (sensor.equals("mag")) {
            throw new IllegalArgumentException("this computer has no magnetometer: use the microphone");
        }
        if (!sensor.equals("mic")) {
            throw new IllegalArgumentException("unknown sensor " + sensor);
        }
        stopSensor();
        error = "";
        synchronized (lock) {
            head = 0;
            count = 0;
        }
        TargetDataLine mic;
        try {
            mic = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, format()));
            mic.open(format(), 44100);
        } catch (Exception e) {
            throw new IllegalArgumentException("couldn't open the microphone: " + e.getMessage());
        }
        mic.start();
        line = mic;
        micThread = new Thread(() -> readMic(mic), "frostoise-mic");
        micThread.setDaemon(true);
        micThread.setPriority(Thread.MAX_PRIORITY);
        micThread.start();
        analysis = timer.scheduleWithFixedDelay(this::analyse, 2, 2, TimeUnit.SECONDS);
        running = sensor;
    }

    private void readMic(TargetDataLine mic) {
        byte[] buf = new byte[441 * 2];
        while (line == mic && !Thread.currentThread().isInterrupted()) {
            int got = 0;
            while (got < buf.length) {
                int n = mic.read(buf, got, buf.length - got);
                if (n <= 0) {
                    if (line == mic) {
                        error = "microphone stopped";
                    }
                    return;
                }
                got += n;
            }
            long sum = 0;
            for (int i = 0; i < buf.length; i += 2) {
                sum += Math.abs((short) ((buf[i] & 0xff) | (buf[i + 1] << 8)));
            }
            add(System.nanoTime(), sum / 441f);
        }
    }

    private void add(long nanos, float value) {
        synchronized (lock) {
            t[head] = nanos;
            v[head] = value;
            head = (head + 1) % CAP;
            if (count < CAP) {
                count++;
            }
        }
    }

    private void analyse() {
        try {
            String sensor = running;
            if (sensor.isEmpty()) {
                return;
            }
            long[] times;
            float[][] values;
            int n;
            synchronized (lock) {
                if (count == 0) {
                    return;
                }
                long last = t[(head - 1 + CAP) % CAP];
                int start = (head - count + CAP) % CAP;
                int skip = 0;
                while (skip < count && last - t[(start + skip) % CAP] > WINDOW_NS) {
                    skip++;
                }
                n = count - skip;
                times = new long[n];
                values = new float[1][n];
                for (int i = 0; i < n; i++) {
                    int at = (start + skip + i) % CAP;
                    times[i] = t[at];
                    values[0][i] = v[at];
                }
            }
            if (n >= 16) {
                long startMs = System.currentTimeMillis() - (System.nanoTime() - times[0]) / 1_000_000;
                latest = Resonance.analyze(sensor, times, values, n, startMs);
            }
        } catch (Throwable e) {
            error = "analysis: " + e.getMessage();
        }
    }

    @Override
    public synchronized void stopSensor() {
        TargetDataLine mic = line;
        line = null;
        if (mic != null) {
            mic.stop();
            mic.close();
        }
        if (micThread != null) {
            micThread.interrupt();
            micThread = null;
        }
        if (analysis != null) {
            analysis.cancel(false);
            analysis = null;
        }
        running = "";
        latest = null;
    }

    @Override
    public Resonance.Reading latestReading() {
        return running.isEmpty() ? null : latest;
    }

    @Override
    public void miningChanged(boolean on) {
        mining = on;
    }

    boolean mining() {
        return mining;
    }

    @Override
    public String name() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "desktop";
        }
        return "Linux " + System.getProperty("os.version", "") + " · " + host;
    }

    /** CPU temperatures (°C) where heat levels 1, 2 and 3 start. A level holds until 3 °C below its start. */
    private static final double[] HEAT_START = {80.0, 88.0, 95.0};

    /** The hottest thermal zone, for Frostoise to back off like it does on a hot phone. */
    @Override
    public int thermalLevel() {
        double hottest = Double.NaN;
        File[] zones = new File("/sys/class/thermal").listFiles((dir, name) -> name.startsWith("thermal_zone"));
        if (zones != null) {
            for (File zone : zones) {
                try {
                    String raw = new String(Files.readAllBytes(new File(zone, "temp").toPath()), StandardCharsets.US_ASCII).trim();
                    double c = Long.parseLong(raw) / 1000.0;
                    if (c > 0 && c < 150 && !(c <= hottest)) {
                        hottest = c;
                    }
                } catch (Exception e) {
                    // Some zones can't be read without root; skip them.
                }
            }
        }
        int level = 0;
        if (!Double.isNaN(hottest)) {
            for (int i = 0; i < HEAT_START.length; i++) {
                double start = i < heat ? HEAT_START[i] - 3.0 : HEAT_START[i];
                if (hottest >= start) {
                    level = i + 1;
                }
            }
        }
        if (level != heat) {
            Log.i("frostoise", "CPU " + Math.round(hottest) + " °C, heat level " + level);
        }
        heat = level;
        return level;
    }
}
