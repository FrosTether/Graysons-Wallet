package com.frostether.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.frostether.frostchain.Api;
import com.frostether.frostchain.Json;
import com.frostether.frostchain.Node;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** The window's server must never let another web page or another host use the wallet. */
public class UiServerTest {
    private File dir;
    private Node node;
    private UiServer ui;
    private int port;
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("ui-test").toFile();
        node = new Node(new File(dir, "node"), null);
        Api api = new Api(node, new DesktopPlatform());
        ui = new UiServer(api, TOKEN, (m, p) -> m.equals("desktop.info") ? Json.o("version", "test") : null);
        port = ui.start(0);
    }

    @After
    public void tearDown() {
        ui.stop();
        node.stop();
    }

    private Object[] request(String method, String path, String host, String token, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        c.setRequestMethod(method);
        if (host != null) {
            c.setRequestProperty("Host", host);
        }
        if (token != null) {
            c.setRequestProperty("X-Frost-Token", token);
        }
        if (body != null) {
            c.setDoOutput(true);
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
        String text = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        return new Object[] {code, text, c.getHeaderField("Content-Security-Policy")};
    }

    @Test
    public void servesTheScreens() throws Exception {
        Object[] r = request("GET", "/", null, null, null);
        assertEquals(200, r[0]);
        assertTrue(((String) r[1]).contains("Graysons Vault"));
        assertTrue(((String) r[2]).contains("frame-ancestors 'none'"));
        assertEquals(200, request("GET", "/remix.js", null, null, null)[0]);
        assertEquals(404, request("GET", "/../build.gradle", null, null, null)[0]);
        assertEquals(404, request("GET", "/nope.js", null, null, null)[0]);
    }

    @Test
    public void walletNeedsTheToken() throws Exception {
        String body = Json.write(Json.o("method", "status", "params", Json.o()));
        assertEquals(403, request("POST", "/wallet/api", null, null, body)[0]);
        assertEquals(403, request("POST", "/wallet/api", null, "wrong", body)[0]);
        Object[] ok = request("POST", "/wallet/api", null, TOKEN, body);
        assertEquals(200, ok[0]);
        Map<String, Object> o = Json.obj((String) ok[1]);
        assertEquals("desktop", Json.str((Map<String, Object>) o.get("result"), "platform"));
    }

    @Test
    public void refusesOtherHosts() throws Exception {
        assertFalse(ui.hostAllowed("evil.example:" + port));
        assertFalse(ui.hostAllowed(null));
        assertTrue(ui.hostAllowed("localhost:" + port));
        assertTrue(ui.hostAllowed("127.0.0.1:" + port));
    }

    @Test
    public void desktopNeverMines() throws Exception {
        String r = ui.apiCall(Json.write(Json.o("method", "miner.start", "params", Json.o())));
        assertTrue(r, r.contains("doesn't mine"));
        String info = ui.apiCall(Json.write(Json.o("method", "desktop.info", "params", Json.o())));
        assertTrue(info, info.contains("\"version\":\"test\""));
    }
}
