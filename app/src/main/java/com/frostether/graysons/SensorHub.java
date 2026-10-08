package com.frostether.graysons;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import com.frostether.frostchain.Api;
import com.frostether.frostchain.Json;
import com.frostether.frostchain.Resonance;
import java.lang.reflect.Array;
import java.util.Map;

final class SensorHub implements Api.Platform {
    static final int CAP = 4096;
    static final double WINDOW_S = 8.0d;
    private int count;
    private final Context ctx;
    private Handler handler;
    private int head;
    private volatile Resonance.Reading latest;
    private final Sensor mag;
    private SensorEventListener magListener;
    private Thread micThread;
    private volatile boolean mining;
    private volatile AudioRecord rec;
    private final SensorManager sm;
    private HandlerThread thread;
    private volatile String running = "";
    private volatile String error = "";
    private final long[] t = new long[CAP];
    private final float[][] v = (float[][]) Array.newInstance((Class<?>) Float.TYPE, 3, CAP);
    private final Object lock = new Object();
    private final Runnable analyse = new Runnable() {
        @Override // java.lang.Runnable
        public void run() {
            long j;
            int i;
            int i2;
            long[] jArr;
            float[][] fArr;
            try {
                String str = SensorHub.this.running;
                if (!str.isEmpty()) {
                    synchronized (SensorHub.this.lock) {
                        if (SensorHub.this.count > 0) {
                            j = SensorHub.this.t[((SensorHub.this.head - 1) + SensorHub.CAP) % SensorHub.CAP];
                        } else {
                            j = 0;
                        }
                        int i3 = ((SensorHub.this.head - SensorHub.this.count) + SensorHub.CAP) % SensorHub.CAP;
                        int i4 = SensorHub.this.count;
                        int i5 = 0;
                        while (true) {
                            if (i5 >= SensorHub.this.count) {
                                i = i4;
                                break;
                            }
                            if (j - SensorHub.this.t[(i3 + i5) % SensorHub.CAP] <= 8000000000L) {
                                i = i5;
                                break;
                            }
                            i5++;
                        }
                        i2 = SensorHub.this.count - i;
                        int i6 = str.equals("mag") ? 3 : 1;
                        jArr = new long[i2];
                        fArr = (float[][]) Array.newInstance((Class<?>) Float.TYPE, i6, i2);
                        for (int i7 = 0; i7 < i2; i7++) {
                            int i8 = ((i3 + i) + i7) % SensorHub.CAP;
                            jArr[i7] = SensorHub.this.t[i8];
                            for (int i9 = 0; i9 < i6; i9++) {
                                fArr[i9][i7] = SensorHub.this.v[i9][i8];
                            }
                        }
                    }
                    if (i2 >= 16) {
                        SensorHub.this.latest = Resonance.analyze(str, jArr, fArr, i2, System.currentTimeMillis() - ((SystemClock.elapsedRealtimeNanos() - jArr[0]) / 1000000));
                    }
                }
            } catch (Throwable th) {
                SensorHub.this.error = "analysis: " + th.getMessage();
            }
            Handler handler = SensorHub.this.handler;
            if (handler != null) {
                handler.postDelayed(this, 2000L);
            }
        }
    };

    SensorHub(Context context) {
        this.ctx = context;
        this.sm = (SensorManager) context.getSystemService("sensor");
        Sensor defaultSensor = this.sm == null ? null : this.sm.getDefaultSensor(14);
        if (defaultSensor == null && this.sm != null) {
            defaultSensor = this.sm.getDefaultSensor(2);
        }
        this.mag = defaultSensor;
    }

    @Override // com.frostether.frostchain.Api.Platform
    public Map<String, Object> sensors() {
        boolean hasSystemFeature = this.ctx.getPackageManager().hasSystemFeature("android.hardware.microphone");
        Object[] objArr = new Object[10];
        objArr[0] = "mag";
        objArr[1] = Boolean.valueOf(this.mag != null);
        objArr[2] = "mic";
        objArr[3] = Boolean.valueOf(hasSystemFeature);
        objArr[4] = "running";
        objArr[5] = this.running;
        objArr[6] = "error";
        objArr[7] = this.error;
        objArr[8] = "magName";
        objArr[9] = this.mag == null ? "" : this.mag.getName();
        return Json.o(objArr);
    }

    @Override // com.frostether.frostchain.Api.Platform
    public synchronized void startSensor(String str) throws Exception {
        if (!str.equals(this.running)) {
            stopSensor();
            this.error = "";
            clear();
            if (this.handler == null) {
                this.thread = new HandlerThread("frostoise-sensor");
                this.thread.start();
                this.handler = new Handler(this.thread.getLooper());
                this.handler.postDelayed(this.analyse, 2000L);
            }
            if (str.equals("mag")) {
                startMag();
            } else {
                if (!str.equals("mic")) {
                    throw new IllegalArgumentException("unknown sensor " + str);
                }
                startMic();
            }
            this.running = str;
            updateService();
        }
    }

    @Override // com.frostether.frostchain.Api.Platform
    public synchronized void stopSensor() {
        if (this.magListener != null && this.sm != null) {
            this.sm.unregisterListener(this.magListener);
        }
        this.magListener = null;
        AudioRecord audioRecord = this.rec;
        this.rec = null;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (RuntimeException e) {
            }
            audioRecord.release();
        }
        if (this.micThread != null) {
            this.micThread.interrupt();
            this.micThread = null;
        }
        this.running = "";
        this.latest = null;
    }

    @Override // com.frostether.frostchain.Api.Platform
    public Resonance.Reading latestReading() {
        if (this.running.isEmpty()) {
            return null;
        }
        return this.latest;
    }

    @Override // com.frostether.frostchain.Api.Platform
    public void miningChanged(boolean z) {
        this.mining = z;
        updateService();
    }

    void miningStopped() {
        this.mining = false;
        stopSensor();
    }

    @Override // com.frostether.frostchain.Api.Platform
    public String name() {
        return "Android " + Build.VERSION.RELEASE + " · " + Build.MANUFACTURER + " " + Build.MODEL;
    }

    /** Battery temperatures (°C) where heat levels 1, 2 and 3 start. A level holds until 2 °C below its start. */
    private static final double[] HEAT_START = {39.0d, 42.0d, 45.0d};
    private volatile int heat;

    /**
     * How hot the phone is, for Frostoise to back off: 1 halves the threads, 2 leaves one, 3 pauses.
     * Uses the battery temperature, and on Android 10+ the system's own thermal status too.
     */
    @Override // com.frostether.frostchain.Api.Platform
    public int thermalLevel() {
        int level = 0;
        try {
            Intent battery = this.ctx.registerReceiver(null, new IntentFilter("android.intent.action.BATTERY_CHANGED"));
            int tenths = battery == null ? Integer.MIN_VALUE : battery.getIntExtra("temperature", Integer.MIN_VALUE);
            if (tenths != Integer.MIN_VALUE) {
                double celsius = tenths / 10.0d;
                for (int i = 0; i < HEAT_START.length; i++) {
                    double start = i < this.heat ? HEAT_START[i] - 2.0d : HEAT_START[i];
                    if (celsius >= start) {
                        level = i + 1;
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                PowerManager powerManager = (PowerManager) this.ctx.getSystemService("power");
                if (powerManager != null) {
                    // LIGHT 1, MODERATE 2, SEVERE and worse 3.
                    level = Math.max(level, Math.min(3, powerManager.getCurrentThermalStatus()));
                }
            }
        } catch (RuntimeException e) {
            Log.w("frostoise", "thermal: " + e);
        }
        this.heat = level;
        return level;
    }

    boolean micRunning() {
        return "mic".equals(this.running) && this.rec != null;
    }

    boolean isMining() {
        return this.mining;
    }

    private void startMag() {
        if (this.mag == null) {
            throw new IllegalArgumentException("this phone has no magnetometer: try the microphone");
        }
        this.magListener = new SensorEventListener() {
            @Override // android.hardware.SensorEventListener
            public void onSensorChanged(SensorEvent sensorEvent) {
                SensorHub.this.add(sensorEvent.timestamp, sensorEvent.values[0], sensorEvent.values[1], sensorEvent.values[2]);
            }

            @Override // android.hardware.SensorEventListener
            public void onAccuracyChanged(Sensor sensor, int i) {
            }
        };
        if (!this.sm.registerListener(this.magListener, this.mag, 0, this.handler)) {
            this.magListener = null;
            throw new IllegalArgumentException("couldn't start the magnetometer");
        }
    }

    private void startMic() {
        if (this.ctx.checkSelfPermission("android.permission.RECORD_AUDIO") != 0) {
            throw new IllegalArgumentException("Frostoise needs microphone permission");
        }
        int minBufferSize = AudioRecord.getMinBufferSize(44100, 16, 2);
        if (minBufferSize <= 0) {
            throw new IllegalArgumentException("this phone's microphone can't record at 44.1 kHz");
        }
        int i = 6;
        AudioManager audioManager = (AudioManager) this.ctx.getSystemService("audio");
        if (audioManager != null && "true".equals(audioManager.getProperty("android.media.property.SUPPORT_AUDIO_SOURCE_UNPROCESSED"))) {
            i = 9;
        }
        final AudioRecord audioRecord = new AudioRecord(i, 44100, 16, 2, Math.max(minBufferSize, 44100));
        if (audioRecord.getState() != 1) {
            audioRecord.release();
            throw new IllegalArgumentException("couldn't open the microphone");
        }
        audioRecord.startRecording();
        this.rec = audioRecord;
        this.micThread = new Thread(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                short[] sArr = new short[441];
                while (SensorHub.this.rec == audioRecord && !Thread.currentThread().isInterrupted()) {
                    int i2 = 0;
                    while (i2 < sArr.length) {
                        int read = audioRecord.read(sArr, i2, sArr.length - i2);
                        if (read <= 0) {
                            if (SensorHub.this.rec == audioRecord) {
                                SensorHub.this.error = "microphone stopped";
                                return;
                            }
                            return;
                        }
                        i2 += read;
                    }
                    int i3 = 0;
                    long j = 0;
                    while (i3 < sArr.length) {
                        long abs = Math.abs((int) sArr[i3]) + j;
                        i3++;
                        j = abs;
                    }
                    SensorHub.this.add(SystemClock.elapsedRealtimeNanos(), j / sArr.length, 0.0f, 0.0f);
                }
            }
        }, "frostoise-mic");
        this.micThread.setPriority(10);
        this.micThread.start();
    }

    private void clear() {
        synchronized (this.lock) {
            this.head = 0;
            this.count = 0;
        }
    }

    public void add(long j, float f, float f2, float f3) {
        synchronized (this.lock) {
            this.t[this.head] = j;
            this.v[0][this.head] = f;
            this.v[1][this.head] = f2;
            this.v[2][this.head] = f3;
            this.head = (this.head + 1) % CAP;
            if (this.count < CAP) {
                this.count++;
            }
        }
    }

    private void updateService() {
        Intent intent = new Intent(this.ctx, (Class<?>) NodeService.class);
        try {
            if (this.mining) {
                this.ctx.startForegroundService(intent.setAction("com.frostether.graysons.UPDATE"));
            } else if (NodeService.running) {
                this.ctx.startService(intent.setAction("com.frostether.graysons.STOP"));
            }
        } catch (RuntimeException e) {
            Log.w("frost", "mining service: " + e);
        }
    }
}
