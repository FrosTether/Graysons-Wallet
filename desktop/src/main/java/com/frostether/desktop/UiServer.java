package com.frostether.desktop;

import com.frostether.frostchain.Api;
import com.frostether.frostchain.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Serves the app's screens to a browser window on this computer only, and answers the wallet API at
 * POST /wallet/api.
 *
 * The wallet API can spend coins, so it only answers a request that:
 *  - comes to 127.0.0.1 or localhost on this port (the Host header), which stops DNS-rebinding pages; and
 *  - carries this run's random token in the X-Frost-Token header. A web page on another site can't set that
 *    header on a request to this server without a CORS preflight, which this server never approves.
 */
final class UiServer {
    interface Hooks {
        /** A desktop-only API call, or null to pass it to the chain API. */
        Object desktopCall(String method, Map<String, Object> params) throws Exception;
    }

    private static final Pattern FILE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final int MAX_BODY = 1 << 20;
    private static final String CSP = "default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; "
            + "script-src 'self'; connect-src 'self'; media-src 'self' blob:; object-src 'none'; base-uri 'none'; "
            + "frame-ancestors 'none'; form-action 'none'";

    private final Api api;
    private final String token;
    private final Hooks hooks;
    private HttpServer server;
    private int port;

    UiServer(Api api, String token, Hooks hooks) {
        this.api = api;
        this.token = token;
        this.hooks = hooks;
    }

    /** Starts on the preferred port, or any free one if it's busy. Returns the port. */
    int start(int preferred) throws IOException {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try {
            server = HttpServer.create(new InetSocketAddress(loopback, preferred), 32);
        } catch (BindException e) {
            server = HttpServer.create(new InetSocketAddress(loopback, 0), 32);
        }
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "graysons-ui");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        port = server.getAddress().getPort();
        return port;
    }

    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    int port() {
        return port;
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            if (!hostAllowed(ex.getRequestHeaders().getFirst("Host"))) {
                send(ex, 403, "text/plain", "forbidden");
                return;
            }
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if (path.equals("/wallet/api")) {
                if (!method.equals("POST")) {
                    send(ex, 405, "text/plain", "POST only");
                } else if (!tokenOk(ex.getRequestHeaders().getFirst("X-Frost-Token"))) {
                    send(ex, 403, "application/json", Json.write(Json.o("error", "this window's session ended: reopen Graysons Vault")));
                } else {
                    send(ex, 200, "application/json", apiCall(readBody(ex)));
                }
                return;
            }
            if (path.equals("/desktop/ping")) {
                boolean ok = tokenOk(ex.getRequestHeaders().getFirst("X-Frost-Token"));
                send(ex, ok ? 200 : 403, "text/plain", ok ? "graysons-vault" : "forbidden");
                return;
            }
            if (!method.equals("GET") && !method.equals("HEAD")) {
                send(ex, 405, "text/plain", "GET only");
                return;
            }
            String name = path.equals("/") ? "index.html" : path.substring(1);
            if (!FILE.matcher(name).matches() || name.contains("..")) {
                send(ex, 404, "text/plain", "not found");
                return;
            }
            byte[] body = resource(name);
            if (body == null) {
                send(ex, 404, "text/plain", "not found");
                return;
            }
            send(ex, 200, contentType(name), body);
        } catch (RuntimeException e) {
            send(ex, 500, "text/plain", "error");
        } finally {
            ex.close();
        }
    }

    String apiCall(String body) {
        Map<String, Object> req;
        try {
            req = Json.obj(body);
        } catch (RuntimeException e) {
            return Json.write(Json.o("error", "bad request"));
        }
        String method = Json.str(req, "method", "");
        Object params = req.get("params");
        @SuppressWarnings("unchecked")
        Map<String, Object> p = params instanceof Map ? (Map<String, Object>) params : Json.o();
        try {
            Object result = hooks == null ? null : hooks.desktopCall(method, p);
            if (result != null) {
                return Json.write(Json.o("ok", true, "result", result));
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Json.write(Json.o("error", e.getMessage()));
        } catch (Exception e) {
            return Json.write(Json.o("error", "something went wrong: " + e.getClass().getSimpleName()));
        }
        return api.call(method, Json.write(p));
    }

    boolean hostAllowed(String host) {
        if (host == null) {
            return false;
        }
        String h = host.trim().toLowerCase();
        return h.equals("127.0.0.1:" + port) || h.equals("localhost:" + port);
    }

    boolean tokenOk(String given) {
        if (given == null) {
            return false;
        }
        return MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BODY) {
                    throw new IOException("request too large");
                }
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = UiServer.class.getResourceAsStream("/ui/" + name)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        }
    }

    static String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".woff2")) return "font/woff2";
        if (name.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        send(ex, code, type, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        ex.getResponseHeaders().set("Content-Security-Policy", CSP);
        boolean head = ex.getRequestMethod().equals("HEAD");
        ex.sendResponseHeaders(code, head ? -1 : body.length);
        if (!head) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
