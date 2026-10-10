package com.frostether.desktop;

import com.frostether.frostchain.Api;
import com.frostether.frostchain.Bytes;
import com.frostether.frostchain.Consensus;
import com.frostether.frostchain.Json;
import com.frostether.frostchain.Log;
import com.frostether.frostchain.Node;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Graysons Vault for Windows and Linux.
 *
 * Runs a full Frostchain node and the wallet on this computer, and shows the same screens as the Android app in
 * a window. It never mines: Frostoise stays on phones. The node keeps running when the window closes, so the
 * computer keeps serving the network until Quit.
 *
 *   GraysonsVault [--data DIR] [--port 7830] [--no-window]
 */
public final class GraysonsDesktop {
    /** The window's preferred port. Any free port works if it's busy. */
    static final int UI_PORT = 7835;

    private static final CountDownLatch QUIT = new CountDownLatch(1);
    private static volatile File dataDir;

    private GraysonsDesktop() {
    }

    public static void main(String[] args) throws Exception {
        File dir = dataDir();
        int p2pPort = Consensus.P2P_PORT;
        boolean window = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--data":
                    dir = new File(args[++i]).getAbsoluteFile();
                    break;
                case "--port":
                    p2pPort = Integer.parseInt(args[++i]);
                    break;
                case "--no-window":
                    window = false;
                    break;
                case "-h":
                case "--help":
                    System.out.println("GraysonsVault [--data DIR] [--port 7830] [--no-window]");
                    return;
                default:
                    System.err.println("unknown option: " + args[i]);
                    System.exit(2);
            }
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("can't create " + dir);
        }
        dataDir = dir;

        // Already running? Show its window instead of starting a second node on the same files.
        File lock = new File(dir, "desktop.json");
        String running = runningUrl(lock);
        if (running != null) {
            if (window) {
                Window.open(running, dir);
            }
            System.out.println("Graysons Vault is already running: " + running);
            return;
        }

        PrintWriter log = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(dir, "desktop.log"), true), StandardCharsets.UTF_8), true);
        Log.setSink((level, tag, msg) -> {
            String line = Instant.now() + " " + level + " " + tag + ": " + msg;
            log.println(line);
            System.out.println(line);
        });

        Node node = new Node(new File(dir, "node"), null);
        node.start(p2pPort, true);
        Api api = new Api(node, new DesktopPlatform());
        String token = randomToken();
        UiServer ui = new UiServer(api, token, (method, params) -> desktopCall(method, params));
        int port = ui.start(UI_PORT);
        String url = "http://127.0.0.1:" + port + "/?t=" + token + "#wallet";
        writeLock(lock, port, token);
        Log.i("desktop", "Graysons Vault " + version() + ", chain " + Bytes.hex(node.chain.chainId).substring(0, 16)
                + ", node on port " + node.port() + ", window on 127.0.0.1:" + port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            lock.delete();
            ui.stop();
            node.stop();
        }, "graysons-shutdown"));

        Tray.install(url, dir, GraysonsDesktop::quit);
        if (window) {
            Window.open(url, dir);
        }
        System.out.println("Graysons Vault is running. Open " + url);
        QUIT.await();
        System.exit(0);
    }

    static void quit() {
        QUIT.countDown();
    }

    private static Object desktopCall(String method, Map<String, Object> params) {
        if (method.equals("desktop.quit")) {
            // Answer first, then stop: the window shows the goodbye before the server goes away.
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                }
                quit();
            }, "graysons-quit");
            t.setDaemon(true);
            t.start();
            return Json.o("quitting", true);
        }
        if (method.equals("desktop.info")) {
            return Json.o("version", version(), "os", System.getProperty("os.name"), "dataDir", (dataDir != null ? dataDir : dataDir()).getPath());
        }
        return null;
    }

    static String version() {
        String v = GraysonsDesktop.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    /** %APPDATA%\GraysonsVault on Windows, ~/.local/share/graysons-vault on Linux (or $XDG_DATA_HOME). */
    static File dataDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return new File(appData != null ? appData : home, "GraysonsVault");
        }
        if (os.contains("mac")) {
            return new File(home, "Library/Application Support/GraysonsVault");
        }
        String xdg = System.getenv("XDG_DATA_HOME");
        return new File(xdg != null && !xdg.isEmpty() ? xdg : home + "/.local/share", "graysons-vault");
    }

    private static String randomToken() {
        byte[] b = new byte[24];
        new SecureRandom().nextBytes(b);
        return Bytes.hex(b);
    }

    private static void writeLock(File lock, int port, String token) throws IOException {
        Files.write(lock.toPath(), Json.write(Json.o("port", (long) port, "token", token)).getBytes(StandardCharsets.UTF_8));
        lock.setReadable(false, false);
        lock.setReadable(true, true);
        lock.setWritable(false, false);
        lock.setWritable(true, true);
        lock.deleteOnExit();
    }

    /** The URL of a Graysons Vault already running on these files, or null. */
    private static String runningUrl(File lock) {
        if (!lock.isFile()) {
            return null;
        }
        try {
            Map<String, Object> o = Json.obj(new String(Files.readAllBytes(lock.toPath()), StandardCharsets.UTF_8));
            long port = Json.num(o, "port");
            String token = Json.str(o, "token");
            HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/desktop/ping").openConnection();
            c.setConnectTimeout(1500);
            c.setReadTimeout(1500);
            c.setRequestProperty("X-Frost-Token", token);
            if (c.getResponseCode() == 200) {
                return "http://127.0.0.1:" + port + "/?t=" + token + "#wallet";
            }
        } catch (Exception ignored) {
            // A lock left by a run that crashed: start fresh.
        }
        return null;
    }
}
