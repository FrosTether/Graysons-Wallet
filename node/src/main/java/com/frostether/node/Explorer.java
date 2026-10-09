package com.frostether.node;

import com.frostether.frostchain.Address;
import com.frostether.frostchain.Block;
import com.frostether.frostchain.Bytes;
import com.frostether.frostchain.Chain;
import com.frostether.frostchain.ChainState;
import com.frostether.frostchain.Consensus;
import com.frostether.frostchain.Json;
import com.frostether.frostchain.Log;
import com.frostether.frostchain.Node;
import com.frostether.frostchain.Resonance;
import com.frostether.frostchain.Tx;
import com.frostether.frostchain.U64;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FrostExplorer: a read-only, Rez-style block explorer served next to frostnode on its own port.
 *
 *   /                          the explorer page
 *   /api/explorer/status       height, tip, next block window, difficulty, QOIN mined
 *   /api/explorer/blocks       newest blocks, ?before=HEIGHT&count=30
 *   /api/explorer/block?h=     one block with its transactions
 *   /api/explorer/tx?id=       one transaction by its ID: pending in the mempool, or confirmed in a block
 *   /api/explorer/search?q=    a height, a block hash, a transaction ID, an address or a .frostchain name
 *
 * It only reads the node's chain, so it's safe to put on the public internet (or behind a Cloudflare tunnel).
 */
final class Explorer {
    private static final int MAX_COUNT = 100;
    private final Node node;
    private final byte[] page;
    private HttpServer http;
    private ExecutorService pool;

    Explorer(Node node) throws IOException {
        this.node = node;
        try (InputStream in = Explorer.class.getResourceAsStream("/explorer.html")) {
            if (in == null) {
                throw new IOException("explorer.html is missing from the frostnode jar");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) {
                out.write(buf, 0, n);
            }
            this.page = out.toByteArray();
        }
    }

    void start(int port) throws IOException {
        http = HttpServer.create(new InetSocketAddress(port), 0);
        pool = Executors.newFixedThreadPool(4);
        http.setExecutor(pool);
        http.createContext("/", this::handle);
        http.start();
    }

    int port() {
        return http.getAddress().getPort();
    }

    void stop() {
        if (http != null) {
            http.stop(0);
            pool.shutdownNow();
        }
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            if (method.equals("OPTIONS")) {
                // CORS preflight, including Chrome's for pages reaching a node on this machine or network
                ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
                ex.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
                ex.getResponseHeaders().set("Access-Control-Allow-Private-Network", "true");
                ex.getResponseHeaders().set("Access-Control-Max-Age", "600");
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (!method.equals("GET") && !method.equals("HEAD")) {
                send(ex, 405, "text/plain; charset=utf-8", bytes("FrostExplorer is read-only"));
                return;
            }
            String path = ex.getRequestURI().getPath();
            Map<String, String> q = query(ex.getRequestURI().getRawQuery());
            switch (path) {
                case "/":
                case "/index.html":
                    ex.getResponseHeaders().set("Cache-Control", "max-age=300");
                    send(ex, 200, "text/html; charset=utf-8", page);
                    return;
                case "/api/explorer/status":
                    json(ex, 200, status());
                    return;
                case "/api/explorer/blocks":
                    json(ex, 200, blocks(q));
                    return;
                case "/api/explorer/block":
                    json(ex, 200, block(q));
                    return;
                case "/api/explorer/tx":
                    json(ex, 200, tx(q.getOrDefault("id", "")));
                    return;
                case "/api/explorer/search":
                    json(ex, 200, search(q));
                    return;
                default:
                    send(ex, 404, "text/plain; charset=utf-8", bytes("not found"));
            }
        } catch (IllegalArgumentException e) {
            json(ex, 400, Json.o("error", String.valueOf(e.getMessage())));
        } catch (Throwable t) {
            Log.w("explorer", "request failed: " + t);
            json(ex, 500, Json.o("error", "the explorer hit an error"));
        } finally {
            ex.close();
        }
    }

    Map<String, Object> status() {
        Chain chain = node.chain;
        Block tip = chain.tip();
        long now = chain.now();
        long opens = tip.time + Consensus.MIN_BLOCK_SPACING;
        Map<String, Object> o = Json.o("chain", Bytes.hex(chain.chainId), "height", Long.valueOf(tip.height), "tip", tip.hashHex(),
                "tipTime", Long.valueOf(tip.time), "now", Long.valueOf(now), "nextIn", Long.valueOf(Math.max(0L, opens - now)),
                "difficulty", U64.str(chain.nextDifficulty()), "generated", U64.format(chain.generated()));
        o.put("accounts", Long.valueOf(chain.accountCount()));
        o.put("mempool", Long.valueOf(node.mempool.size()));
        o.put("peers", Long.valueOf(node.peerList().size()));
        o.put("launch", Long.valueOf(Consensus.GENESIS_TIME));
        o.put("blockTime", Long.valueOf(Consensus.BLOCK_TIME));
        o.put("minSpacing", Long.valueOf(Consensus.MIN_BLOCK_SPACING));
        o.put("agent", Node.AGENT);
        o.put("coin", Consensus.COIN);
        return o;
    }

    List<Object> blocks(Map<String, String> q) {
        Chain chain = node.chain;
        long before = q.containsKey("before") ? number(q.get("before")) : chain.height() + 1;
        int count = (int) Math.max(1, Math.min(MAX_COUNT, q.containsKey("count") ? number(q.get("count")) : 30));
        long from = Math.max(1L, before - count);
        List<Object> out = new ArrayList<>();
        if (before <= from) {
            return out;
        }
        List<Block> list = chain.range(from, (int) (before - from));
        for (int i = list.size() - 1; i >= 0; i--) {
            out.add(summary(list.get(i), false));
        }
        return out;
    }

    Map<String, Object> block(Map<String, String> q) {
        Block b = node.chain.at(number(q.getOrDefault("h", "")));
        if (b == null) {
            throw new IllegalArgumentException("no such block yet");
        }
        return summary(b, true);
    }

    Map<String, Object> search(Map<String, String> q) {
        String s = q.getOrDefault("q", "").trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("type a block number, a block hash, a transaction ID, an address or a name");
        }
        String plain = s.startsWith("#") ? s.substring(1) : s;
        if (plain.matches("[0-9]{1,15}")) {
            Block b = node.chain.at(Long.parseLong(plain));
            if (b == null) {
                throw new IllegalArgumentException("there's no block " + plain + " yet");
            }
            return Json.o("kind", "block", "block", summary(b, true));
        }
        if (plain.matches("(?i)[0-9a-f]{64}")) {
            String want = plain.toLowerCase();
            for (long h = node.chain.height(); h >= 0; h--) {
                Block b = node.chain.at(h);
                if (b != null && b.hashHex().equals(want)) {
                    return Json.o("kind", "block", "block", summary(b, true));
                }
            }
            Map<String, Object> t = findTx(want);
            if (t != null) {
                return Json.o("kind", "tx", "tx", t);
            }
            throw new IllegalArgumentException("no block or transaction with that ID on this chain yet");
        }
        byte[] id = node.resolve(s);
        ChainState.Account a = node.chain.account(id);
        List<Object> history = new ArrayList<>();
        List<Chain.Event> events = node.chain.history(id);
        for (int i = events.size() - 1; i >= 0 && history.size() < 50; i--) {
            history.add(events.get(i).toJson());
        }
        Map<String, Object> o = Json.o("kind", "account", "address", Address.raw(id), "name", nameOf(id),
                "balance", U64.format(a == null ? 0L : a.balance), "immature", U64.format(a == null ? 0L : a.immature));
        o.put("history", history);
        return o;
    }

    /** One transaction by ID, for /api/explorer/tx. */
    Map<String, Object> tx(String id) {
        String want = id.trim().toLowerCase();
        if (!want.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("a transaction ID is 64 letters and numbers (0-9, a-f)");
        }
        Map<String, Object> t = findTx(want);
        if (t == null) {
            throw new IllegalArgumentException("no transaction with that ID on this chain yet");
        }
        return t;
    }

    // Transaction ID -> block height, built as the chain grows. A reorg changes the hash of the block we last indexed
    // (or shortens the chain), and then the index is thrown away and rebuilt.
    private final Map<String, Long> txIndex = new HashMap<>();
    private long indexedTo = 0;
    private String indexedHash = null;

    private synchronized void catchUpIndex() {
        Chain chain = node.chain;
        Block seen = chain.at(indexedTo);
        if (indexedTo > 0 && (seen == null || !seen.hashHex().equals(indexedHash))) {
            txIndex.clear();
            indexedTo = 0;
        }
        long tip = chain.height();
        for (long h = indexedTo + 1; h <= tip; h++) {
            Block b = chain.at(h);
            if (b == null) {
                break;
            }
            for (Tx tx : b.txs) {
                txIndex.put(Bytes.hex(tx.id(chain.chainId)), Long.valueOf(h));
            }
            indexedTo = h;
        }
        Block last = chain.at(indexedTo);
        indexedHash = last == null ? null : last.hashHex();
    }

    /** The transaction with this ID: confirmed in a block, still pending in the mempool, or null. */
    private Map<String, Object> findTx(String want) {
        Chain chain = node.chain;
        Long height;
        synchronized (this) {
            catchUpIndex();
            height = txIndex.get(want);
        }
        if (height != null) {
            Block b = chain.at(height.longValue());
            if (b != null) {
                for (Tx tx : b.txs) {
                    if (Bytes.hex(tx.id(chain.chainId)).equals(want)) {
                        Map<String, Object> o = txJson(tx);
                        o.put("status", "confirmed");
                        o.put("height", Long.valueOf(b.height));
                        o.put("block", b.hashHex());
                        o.put("time", Long.valueOf(b.time));
                        o.put("confirmations", Long.valueOf(chain.height() - b.height + 1));
                        return o;
                    }
                }
            }
        }
        for (Tx tx : node.mempool.all()) {
            if (Bytes.hex(tx.id(chain.chainId)).equals(want)) {
                Map<String, Object> o = txJson(tx);
                o.put("status", "pending");
                o.put("confirmations", Long.valueOf(0));
                return o;
            }
        }
        return null;
    }

    private Map<String, Object> txJson(Tx tx) {
        boolean send = tx.type == Tx.SEND;
        Map<String, Object> t = Json.o("type", send ? "send" : tx.type == Tx.NAME ? "name" : "rekey",
                "from", Address.raw(tx.from), "fromName", nameOf(tx.from),
                "to", send ? Address.raw(tx.to) : "", "toName", send ? nameOf(tx.to) : "");
        t.put("amount", U64.format(tx.amount));
        t.put("fee", U64.format(tx.fee));
        t.put("memo", tx.memo == null ? "" : tx.memo);
        t.put("name", tx.name == null ? "" : tx.name);
        t.put("id", Bytes.hex(tx.id(node.chain.chainId)));
        return t;
    }

    private Map<String, Object> summary(Block b, boolean withTxs) {
        Map<String, Object> o = Json.o("height", Long.valueOf(b.height), "hash", b.hashHex(), "prev", Bytes.hex(b.prev),
                "time", Long.valueOf(b.time), "miner", b.height == 0 ? "" : Address.raw(b.miner),
                "minerName", b.height == 0 ? "" : nameOf(b.miner), "difficulty", U64.str(b.difficulty));
        o.put("nonce", U64.str(b.nonce));
        o.put("paid", U64.format(b.paid));
        o.put("txs", Long.valueOf(b.txs.size()));
        Resonance.Proof p = b.reso;
        if (p != null) {
            int band = Resonance.bandOfMilli(p.hzMilli);
            List<Object> spectrum = new ArrayList<>();
            if (p.spectrum != null) {
                for (byte v : p.spectrum) {
                    spectrum.add(Long.valueOf(v & 255));
                }
            }
            Map<String, Object> r = Json.o("sensor", p.sensor, "hzMilli", Long.valueOf(p.hzMilli),
                    "band", band >= 0 ? Resonance.BAND_NAMES[band] : "", "snrX100", Long.valueOf(p.snrX100),
                    "ampMilli", Long.valueOf(p.ampMilli), "samples", Long.valueOf(p.samples));
            r.put("rateCenti", Long.valueOf(p.rateCenti));
            r.put("durMs", Long.valueOf(p.durMs));
            r.put("spectrum", spectrum);
            o.put("reso", r);
        }
        if (withTxs) {
            List<Object> txs = new ArrayList<>();
            for (Tx tx : b.txs) {
                txs.add(txJson(tx));
            }
            o.put("txList", txs);
        }
        return o;
    }

    private String nameOf(byte[] id) {
        String n = node.chain.nameOf(id);
        return n == null ? "" : Address.display(n);
    }

    private static long number(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a number: " + s);
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> q = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return q;
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            String k = eq < 0 ? part : part.substring(0, eq);
            String v = eq < 0 ? "" : part.substring(eq + 1);
            q.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return q;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static void json(HttpExchange ex, int code, Object body) throws IOException {
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        send(ex, code, "application/json; charset=utf-8", bytes(Json.write(body)));
    }

    private static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        boolean head = ex.getRequestMethod().equals("HEAD");
        ex.sendResponseHeaders(code, head ? -1 : body.length);
        if (!head) {
            try (OutputStream o = ex.getResponseBody()) {
                o.write(body);
            }
        }
    }
}
