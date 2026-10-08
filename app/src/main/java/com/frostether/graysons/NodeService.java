package com.frostether.graysons;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import com.frostether.frostchain.Json;
import com.frostether.frostchain.Node;
import java.util.Locale;
import java.util.Map;

public final class NodeService extends Service {
    static final String ACTION_STOP = "com.frostether.graysons.STOP";
    static final String ACTION_UPDATE = "com.frostether.graysons.UPDATE";
    static final String CHANNEL = "frostoise";
    static final int ID = 783;
    static volatile boolean running;
    private Handler bg;
    private volatile boolean foreground;
    private WifiManager.MulticastLock lan;
    private final Runnable tick = new Runnable() {
        @Override // java.lang.Runnable
        public void run() {
            if (NodeService.this.foreground) {
                NotificationManager notificationManager = (NotificationManager) NodeService.this.getSystemService("notification");
                if (notificationManager != null) {
                    notificationManager.notify(NodeService.ID, NodeService.this.build(NodeService.this.statusText()));
                }
                Handler handler = NodeService.this.bg;
                if (handler != null) {
                    handler.postDelayed(this, 5000L);
                }
            }
        }
    };
    private PowerManager.WakeLock wake;
    private HandlerThread worker;

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        running = true;
        this.worker = new HandlerThread("frostoise-service");
        this.worker.start();
        this.bg = new Handler(this.worker.getLooper());
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int i, final int i2) {
        PowerManager powerManager;
        Core.init(this);
        goForeground();
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            this.bg.post(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    Node nodeNow = Core.nodeNow();
                    if (nodeNow != null) {
                        nodeNow.miner.stop();
                    }
                    SensorHub sensorsNow = Core.sensorsNow();
                    if (sensorsNow != null) {
                        sensorsNow.miningStopped();
                    }
                    NodeService.this.stopSelfResult(i2);
                }
            });
        } else {
            if (this.wake == null && (powerManager = (PowerManager) getSystemService("power")) != null) {
                this.wake = powerManager.newWakeLock(1, "graysons:frostoise");
                this.wake.setReferenceCounted(false);
                this.wake.acquire(43200000L);
            }
            if (this.lan == null) {
                try {
                    WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService("wifi");
                    if (wifiManager != null) {
                        this.lan = wifiManager.createMulticastLock("graysons:mining-lan");
                        this.lan.setReferenceCounted(false);
                        this.lan.acquire();
                    }
                } catch (RuntimeException e) {
                }
            }
            this.bg.removeCallbacks(this.tick);
            this.bg.postDelayed(this.tick, 2000L);
        }
        return 2;
    }

    private void goForeground() {
        NotificationManager notificationManager = (NotificationManager) getSystemService("notification");
        if (notificationManager != null && notificationManager.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL, "Frostoise mining", 2);
            notificationChannel.setDescription("Shown while Frostoise is mining Qoin (QNR)");
            notificationChannel.setShowBadge(false);
            notificationManager.createNotificationChannel(notificationChannel);
        }
        Notification build = build(new String[]{"Frostoise is mining", "Listening for 7.83 Hz…"});
        if (Build.VERSION.SDK_INT >= 34) {
            int i = 1073741824;
            SensorHub sensorsNow = Core.sensorsNow();
            if (sensorsNow != null && sensorsNow.micRunning()) {
                i = 1073741952;
            }
            startForeground(ID, build, i);
        } else {
            startForeground(ID, build);
        }
        this.foreground = true;
    }

    public String[] statusText() {
        Node nodeNow = Core.nodeNow();
        if (nodeNow == null) {
            return new String[]{"Frostoise is mining", "Starting…"};
        }
        Map<String, Object> status = nodeNow.miner.status();
        boolean bool = Json.bool(status, "mining", false);
        return new String[]{bool ? "Frostoise: locked on 7.83 Hz" : "Frostoise: waiting for 7.83 Hz", (bool ? rate(Json.num(status, "hashrate", 0L)) + " · " : "") + "block " + nodeNow.chain.height() + " · found " + Json.num(status, "accepted", 0L)};
    }

    public Notification build(String[] strArr) {
        Intent intent = new Intent(this, (Class<?>) FrostoiseActivity.class);
        intent.setFlags(805306368);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_frostoise).setContentTitle(strArr[0]).setContentText(strArr[1]).setContentIntent(PendingIntent.getActivity(this, 1, intent, 201326592)).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).addAction(new Notification.Action.Builder((Icon) null, "Stop mining", PendingIntent.getService(this, 2, new Intent(this, (Class<?>) NodeService.class).setAction(ACTION_STOP), 201326592)).build()).build();
    }

    private static String rate(long j) {
        return j >= 1000000 ? String.format(Locale.ROOT, "%.2f MH/s", Double.valueOf(j / 1000000.0d)) : j >= 1000 ? String.format(Locale.ROOT, "%.1f kH/s", Double.valueOf(j / 1000.0d)) : j + " H/s";
    }

    @Override // android.app.Service
    public void onDestroy() {
        running = false;
        this.foreground = false;
        if (this.bg != null) {
            this.bg.removeCallbacksAndMessages(null);
        }
        if (this.worker != null) {
            this.worker.quitSafely();
        }
        this.bg = null;
        if (this.wake != null && this.wake.isHeld()) {
            this.wake.release();
        }
        this.wake = null;
        if (this.lan != null && this.lan.isHeld()) {
            this.lan.release();
        }
        this.lan = null;
        super.onDestroy();
    }
}
