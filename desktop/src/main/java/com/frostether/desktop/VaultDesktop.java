package com.frostether.desktop;

import com.frostether.frostchain.Api;
import com.frostether.frostchain.Bytes;
import com.frostether.frostchain.Consensus;
import com.frostether.frostchain.Json;
import com.frostether.frostchain.Log;
import com.frostether.frostchain.Node;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Graysons Vault, Frostoise and MyFrost on a Linux desktop.
 *
 *   graysons-vault [wallet|frostoise|myfrost] [--data DIR] [--no-browser]
 *   graysons-vault --quit [--data DIR]
 *
 * One process runs a full Frostchain node, like the phone app does, and serves the app's screens on
 * 127.0.0.1 behind a random token. The screens open in a Chromium-style app window, or the default browser.
 * Starting it again (from another menu entry) opens a window on the running process instead.
 * The node keeps running after the windows close, like the phone's, until --quit or logging out.
 */
public final class VaultDesktop {
    private static final String[] APP_BROWSERS = {
        "chromium", "chromium-browser", "google-chrome", "google-chrome-stable", "brave-browser", "microsoft-edge"
    };

    public static void main(String[] args) throws Exception {
        String mode = "wallet";
        File dir = defaultDir();
        boolean browser = true;
        boolean quit = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "wallet":
                case "frostoise":
                case "myfrost":
                    mode = args[i];
                    break;
                case "--data":
                    if (i + 1 >= args.length) {
                        usage();
                    }
                    dir = new File(args[++i]);
                    break;
                case "--no-browser":
                    browser = false;
                    break;
                case "--quit":
                    quit = true;
                    break;
                default:
                    usage();
            }
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("can't create " + dir);
        }

        // One process per data folder. A second start just opens another window on the first.
        File urlFile = new File(dir, "desktop.url");
        RandomAccessFile lockFile = new RandomAccessFile(new File(dir, "desktop.lock"), "rw");
        FileLock lock = lockFile.getChannel().tryLock();
        if (lock == null) {
            String url = runningUrl(urlFile);
            if (quit) {
                askToQuit(url);
            } else {
                open(url + "#" + mode, browser);
            }
            return;
        }
        if (quit) {
            System.out.println("Graysons Vault isn't running.");
            return;
        }

        Node node = new Node(new File(dir, "frostchain"), null);
        DesktopPlatform platform = new DesktopPlatform();
        Api api = new Api(node, platform);
        node.start(Consensus.P2P_PORT, true);
        if (node.port() != Consensus.P2P_PORT) {
            // Usually a frostnode service on this computer: sync from it.
            Log.i("desktop", "port " + Consensus.P2P_PORT + " is busy, using " + node.port() + " and syncing from the node on 7830");
            node.addPeer("127.0.0.1:" + Consensus.P2P_PORT);
        }

        byte[] secret = new byte[16];
        new SecureRandom().nextBytes(secret);
        String token = Bytes.hex(secret);
        HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        int port = http.getAddress().getPort();
        String host = "127.0.0.1:" + port;
        http.createContext("/wallet/api", ex -> apiCall(ex, api, host, token));
        http.createContext("/quit", ex -> {
            boolean ok = allowed(ex, host, token);
            send(ex, ok ? 200 : 403, "application/json", Bytes.utf8(Json.write(Json.o("quit", ok))));
            ex.close();
            if (ok) {
                new Thread(() -> System.exit(0)).start();
            }
        });
        http.createContext("/", ex -> serveUi(ex, host));
        http.setExecutor(Executors.newFixedThreadPool(4));
        http.start();

        String url = "http://" + host + "/?t=" + token;
        // Only this user may read the token: set the permissions before writing it.
        urlFile.delete();
        urlFile.createNewFile();
        urlFile.setReadable(false, false);
        urlFile.setReadable(true, true);
        Files.write(urlFile.toPath(), url.getBytes(StandardCharsets.UTF_8));
        System.out.println("Graysons Vault on " + url + " - Frostchain node on port " + node.port()
            + ", chain " + Bytes.hex(node.chain.chainId).substring(0, 16) + ", height " + node.chain.height());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            http.stop(0);
            node.stop();
            platform.stopSensor();
            urlFile.delete();
        }));
        open(url + "#" + mode, browser);

        while (true) {
            Thread.sleep(Long.MAX_VALUE);
        }
    }

    /** The running process's address. It writes the file just after taking the lock, so wait a moment for it. */
    private static String runningUrl(File urlFile) throws Exception {
        for (int i = 0; i < 50; i++) {
            if (urlFile.length() > 0) {
                return new String(Files.readAllBytes(urlFile.toPath()), StandardCharsets.UTF_8).trim();
            }
            Thread.sleep(200);
        }
        throw new IOException("Graysons Vault is starting but hasn't written " + urlFile + " yet. Try again.");
    }

    private static void askToQuit(String url) throws IOException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url.substring(0, url.indexOf("/?t=")) + "/quit").openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("X-Frost-Token", url.substring(url.indexOf("?t=") + 3));
        c.setDoOutput(true);
        c.getOutputStream().close();
        System.out.println(c.getResponseCode() == 200 ? "Graysons Vault stopped." : "Graysons Vault didn't stop: " + c.getResponseCode());
    }

    /** Only this computer's own pages: the Host check stops DNS rebinding, the token stops other sites. */
    private static boolean allowed(HttpExchange ex, String host, String token) {
        String sent = ex.getRequestHeaders().getFirst("X-Frost-Token");
        return "POST".equals(ex.getRequestMethod()) && host.equals(ex.getRequestHeaders().getFirst("Host"))
            && sent != null && MessageDigest.isEqual(sent.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    private static void usage() {
        System.err.println("usage: graysons-vault [wallet|frostoise|myfrost] [--data DIR] [--no-browser]\n"
            + "       graysons-vault --quit [--data DIR]");
        System.exit(2);
    }

    private static File defaultDir() {
        String xdg = System.getenv("XDG_DATA_HOME");
        File base = xdg != null && !xdg.isEmpty() ? new File(xdg) : new File(System.getProperty("user.home"), ".local/share");
        return new File(base, "graysons-vault");
    }

    /** POST /wallet/api {"method":…, "params":{…}}: the same calls the Android app makes through FrostBridge. */
    private static void apiCall(HttpExchange ex, Api api, String host, String token) throws IOException {
        try {
            if (!allowed(ex, host, token)) {
                send(ex, 403, "application/json", Bytes.utf8(Json.write(Json.o("error", "forbidden"))));
                return;
            }
            Map<String, Object> req;
            try {
                req = Json.obj(new String(readBody(ex.getRequestBody()), StandardCharsets.UTF_8));
            } catch (RuntimeException e) {
                send(ex, 400, "application/json", Bytes.utf8(Json.write(Json.o("error", "bad request"))));
                return;
            }
            Object params = req.get("params");
            String reply = api.call(Json.str(req, "method", ""), params == null ? "{}" : Json.write(params));
            send(ex, 200, "application/json; charset=utf-8", reply.getBytes(StandardCharsets.UTF_8));
        } finally {
            ex.close();
        }
    }

    private static byte[] readBody(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > 2_000_000) {
                throw new IOException("request too large");
            }
        }
        return out.toByteArray();
    }

    /** The app's screens, packed from app/src/main/assets/ui. */
    private static void serveUi(HttpExchange ex, String host) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            } else if (path.equals("/favicon.ico")) {
                path = "/graysons.svg";
            }
            String name = path.substring(1);
            if (!host.equals(ex.getRequestHeaders().getFirst("Host")) || !name.matches("[a-z0-9_-]+\\.(html|js|css|svg)")) {
                send(ex, 404, "text/plain", Bytes.utf8("not found"));
                return;
            }
            try (InputStream in = VaultDesktop.class.getResourceAsStream("/ui/" + name)) {
                if (in == null) {
                    send(ex, 404, "text/plain", Bytes.utf8("not found"));
                    return;
                }
                send(ex, 200, typeOf(name), readBody(in));
            }
        } finally {
            ex.close();
        }
    }

    private static String typeOf(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        return "image/svg+xml";
    }

    private static void send(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Frame-Options", "DENY");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    /** An app window from a Chromium-style browser if there is one, otherwise the default browser. */
    private static void open(String url, boolean browser) {
        if (!browser) {
            System.out.println("Open " + url);
            return;
        }
        for (String b : APP_BROWSERS) {
            if (onPath(b) && launch(b, "--app=" + url, "--class=GraysonsVault")) {
                return;
            }
        }
        if (!launch("xdg-open", url)) {
            System.out.println("Open " + url + " in your browser.");
        }
    }

    private static boolean onPath(String cmd) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String d : path.split(File.pathSeparator)) {
            if (new File(d, cmd).canExecute()) {
                return true;
            }
        }
        return false;
    }

    private static boolean launch(String... cmd) {
        try {
            new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
