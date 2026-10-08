package com.frostether.graysons;

import android.content.Context;
import android.net.wifi.WifiManager;
import com.frostether.frostchain.Api;
import com.frostether.frostchain.Consensus;
import com.frostether.frostchain.Log;
import com.frostether.frostchain.Node;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class Core {
    private static volatile Api api;
    private static volatile String error;
    private static WifiManager.MulticastLock lan;
    private static volatile Node node;
    private static final CountDownLatch ready = new CountDownLatch(1);
    private static volatile SensorHub sensors;
    private static boolean started;
    private static int visibleCount;

    private Core() {
    }

    static synchronized void init(Context context) {
        synchronized (Core.class) {
            if (!started) {
                started = true;
                final Context applicationContext = context.getApplicationContext();
                Log.setSink(new Log.Sink() {
                    @Override // com.frostether.frostchain.Log.Sink
                    public void log(char c, String str, String str2) {
                        if (c != 'W') {
                            android.util.Log.i("frost." + str, str2);
                        } else {
                            android.util.Log.w("frost." + str, str2);
                        }
                    }
                });
                Thread thread = new Thread(new Runnable() {
                    @Override // java.lang.Runnable
                    public void run() {
                        try {
                            Node node2 = new Node(new File(applicationContext.getFilesDir(), "frostchain"), null);
                            SensorHub sensorHub = new SensorHub(applicationContext);
                            Api api2 = new Api(node2, sensorHub);
                            Node unused = Core.node = node2;
                            SensorHub unused2 = Core.sensors = sensorHub;
                            Api unused3 = Core.api = api2;
                        } catch (Throwable th) {
                            String unused4 = Core.error = String.valueOf(th);
                            android.util.Log.e("frost", "node failed to load", th);
                        } finally {
                            Core.ready.countDown();
                        }
                        if (Core.node != null) {
                            try {
                                Core.node.start(Consensus.P2P_PORT, true);
                            } catch (Throwable th2) {
                                android.util.Log.e("frost", "network start failed", th2);
                            }
                        }
                    }
                }, "frostchain-load");
                thread.setDaemon(true);
                thread.start();
            }
        }
    }

    static Api api() {
        try {
            ready.await(60L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return api;
    }

    static synchronized void visible(Context context, boolean z) {
        synchronized (Core.class) {
            visibleCount = Math.max(0, (z ? 1 : -1) + visibleCount);
            try {
            } catch (RuntimeException e) {
                android.util.Log.w("frost", "multicast lock: " + e);
            }
            if (lan == null) {
                WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService("wifi");
                if (wifiManager != null) {
                    lan = wifiManager.createMulticastLock("graysons:lan-discovery");
                    lan.setReferenceCounted(false);
                }
            }
            if (visibleCount > 0 && !lan.isHeld()) {
                lan.acquire();
            }
            if (visibleCount == 0 && lan.isHeld()) {
                lan.release();
            }
        }
    }

    static Node nodeNow() {
        return node;
    }

    static SensorHub sensorsNow() {
        return sensors;
    }

    static String error() {
        return error;
    }
}
