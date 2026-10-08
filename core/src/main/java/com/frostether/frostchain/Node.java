package com.frostether.frostchain;

import com.frostether.frostchain.Chain;
import com.frostether.frostchain.ChainState;
import com.frostether.frostchain.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Node {
    public static final String AGENT = "Frostchain/0.1 (Graysons Wallet)";
    static final long MAX_REORG_BYTES = 25165824;
    static final int MAX_RESPONSE = 6291456;
    private Thread beaconThread;
    public final Chain chain;
    public final File dir;
    public final Mempool mempool;
    public final Miner miner;
    private volatile int port;
    private volatile boolean running;
    private HttpServer server;
    private Thread syncThread;
    private DatagramSocket udp;
    private Thread udpThread;
    public final Wallets wallets;
    private final Set<String> peers = Collections.synchronizedSet(new LinkedHashSet());
    private final Map<String, Map<String, Object>> peerInfo = Collections.synchronizedMap(new LinkedHashMap());
    private final Set<String> selfAddrs = Collections.synchronizedSet(new HashSet());
    private final Set<String> manualPeers = Collections.synchronizedSet(new LinkedHashSet());
    private final Map<String, Integer> failures = Collections.synchronizedMap(new LinkedHashMap());
    private final String nodeId = Bytes.hex(Bytes.random(8));
    private final ExecutorService relay = Executors.newFixedThreadPool(2);
    private Map<String, Object> settings = new LinkedHashMap();
    private volatile String syncStatus = "idle";
    private final Object syncWake = new Object();

    public Node(File file, Chain.Clock clock) throws IOException {
        this.dir = file;
        if (!file.isDirectory() && !file.mkdirs()) {
            throw new IOException("can't create " + file);
        }
        this.chain = new Chain(file, clock);
        this.mempool = new Mempool(this.chain);
        this.miner = new Miner(this);
        this.wallets = new Wallets(new File(file, "wallets"), this);
        loadSettings();
        loadPeers();
    }

    public synchronized void start(int i, boolean z) throws IOException {
        if (!this.running) {
            this.running = true;
            this.server = new HttpServer(new HttpServer.Handler() {
                @Override // com.frostether.frostchain.HttpServer.Handler
                public HttpServer.Response handle(HttpServer.Request request) throws Exception {
                    return Node.this.handleP2P(request);
                }
            });
            try {
                this.port = this.server.start(z ? "0.0.0.0" : "127.0.0.1", i);
            } catch (IOException e) {
                this.port = this.server.start(z ? "0.0.0.0" : "127.0.0.1", 0);
            }
            this.selfAddrs.add("127.0.0.1:" + this.port);
            this.selfAddrs.add("localhost:" + this.port);
            Iterator<String> it = localIps().iterator();
            while (it.hasNext()) {
                this.selfAddrs.add(it.next() + ":" + this.port);
            }
            this.syncThread = new Thread(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    Node.this.syncLoop();
                }
            }, "frostchain-sync");
            this.syncThread.setDaemon(true);
            this.syncThread.start();
            if (z) {
                startDiscovery();
            }
            Log.i("node", "Frostchain node on port " + this.port + ", height " + this.chain.height());
        }
    }

    public synchronized void stop() {
        this.running = false;
        this.miner.stop();
        if (this.server != null) {
            this.server.stop();
        }
        if (this.udp != null) {
            this.udp.close();
        }
        synchronized (this.syncWake) {
            this.syncWake.notifyAll();
        }
        this.relay.shutdownNow();
        this.wallets.closeAll();
    }

    public int port() {
        return this.port;
    }

    public boolean offline() {
        return Json.bool(this.settings, "offline", false);
    }

    public synchronized void setOffline(boolean z) {
        this.settings.put("offline", Boolean.valueOf(z));
        saveSettings();
        wakeSync();
    }

    public Map<String, Object> settings() {
        return new LinkedHashMap(this.settings);
    }

    public synchronized void putSetting(String str, Object obj) {
        this.settings.put(str, obj);
        saveSettings();
    }

    public Chain.Result submitBlock(Block block, String str) {
        Chain.Result addBlock = this.chain.addBlock(block);
        if (addBlock.ok) {
            this.mempool.revalidate(null);
            this.miner.refresh();
            this.wallets.onNewBlock();
            broadcast("/p2p/block", Json.write(block.toJson()), str);
        } else if (str != null && !this.chain.has(block.hash()) && block.height > this.chain.height() - 2) {
            wakeSync();
        }
        return addBlock;
    }

    public String submitTx(Tx tx, String str) {
        boolean has = this.mempool.has(tx.id(this.chain.chainId));
        String add = this.mempool.add(tx);
        if (add == null && !has) {
            this.miner.refresh();
            broadcast("/p2p/tx", Json.write(tx.toJson()), str);
        }
        return add;
    }

    public ChainState.Account pendingAccount(byte[] bArr) {
        ChainState stateCopy = this.chain.stateCopy();
        Iterator<Tx> it = this.mempool.all().iterator();
        while (it.hasNext()) {
            stateCopy.apply(it.next());
        }
        return stateCopy.account(bArr);
    }

    public List<Map<String, Object>> peerList() {
        ArrayList arrayList = new ArrayList();
        synchronized (this.peers) {
            for (String str : this.peers) {
                Map<String, Object> o = Json.o("addr", str);
                Map<String, Object> map = this.peerInfo.get(str);
                if (map != null) {
                    o.putAll(map);
                }
                arrayList.add(o);
            }
        }
        return arrayList;
    }

    public String addPeer(String str) {
        String normalizePeer = normalizePeer(str);
        if (normalizePeer == null) {
            throw new IllegalArgumentException("peer must look like host:port or host");
        }
        if (!this.selfAddrs.contains(normalizePeer)) {
            this.manualPeers.add(normalizePeer);
            if (this.peers.size() < 64 && this.peers.add(normalizePeer)) {
                savePeers();
                wakeSync();
            }
        }
        return normalizePeer;
    }

    public void removePeer(String str) {
        String normalizePeer = normalizePeer(str);
        if (normalizePeer != null) {
            this.manualPeers.remove(normalizePeer);
            if (this.peers.remove(normalizePeer)) {
                this.peerInfo.remove(normalizePeer);
                savePeers();
            }
        }
    }

    private void failed(String str, String str2) {
        this.peerInfo.put(str, Json.o("ok", false, "error", str2, "seen", Long.valueOf(System.currentTimeMillis())));
        Integer num = this.failures.get(str);
        int intValue = num == null ? 1 : num.intValue() + 1;
        this.failures.put(str, Integer.valueOf(intValue));
        if (intValue < 6 || this.manualPeers.contains(str)) {
            return;
        }
        this.peers.remove(str);
        this.peerInfo.remove(str);
        this.failures.remove(str);
        savePeers();
    }

    public String syncStatus() {
        return this.syncStatus;
    }

    static String normalizePeer(String str) {
        if (str == null) {
            return null;
        }
        String lowerCase = str.trim().toLowerCase(Locale.ROOT);
        if (lowerCase.startsWith("http://")) {
            lowerCase = lowerCase.substring(7);
        }
        if (lowerCase.endsWith("/")) {
            lowerCase = lowerCase.substring(0, lowerCase.length() - 1);
        }
        if (lowerCase.isEmpty() || lowerCase.contains("/") || lowerCase.contains(" ") || lowerCase.contains("@")) {
            return null;
        }
        int lastIndexOf = lowerCase.lastIndexOf(58);
        if (lastIndexOf < 0) {
            return lowerCase + ":" + Consensus.P2P_PORT;
        }
        try {
            int parseInt = Integer.parseInt(lowerCase.substring(lastIndexOf + 1));
            if (parseInt <= 0 || parseInt > 65535 || lastIndexOf == 0) {
                return null;
            }
            return lowerCase;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void wakeSync() {
        synchronized (this.syncWake) {
            this.syncWake.notifyAll();
        }
    }

    /**
     * Every 10 seconds (or when woken): sync with each peer, then every minute swap peer lists.
     * Rebuilt from the 0.3.0 bytecode; the decompiled version never reached the wait.
     */
    public void syncLoop() {
        long lastExchange = 0;
        while (this.running) {
            if (!offline()) {
                ArrayList<String> snapshot;
                synchronized (this.peers) {
                    snapshot = new ArrayList<>(this.peers);
                }
                int reachable = 0;
                for (String peer : snapshot) {
                    if (!this.running) {
                        return;
                    }
                    try {
                        if (syncWith(peer)) {
                            reachable++;
                        }
                    } catch (Throwable th) {
                        failed(peer, th instanceof OutOfMemoryError ? "sent too much data" : String.valueOf(th.getMessage()));
                    }
                }
                this.syncStatus = snapshot.isEmpty() ? "no peers yet" : reachable + " of " + snapshot.size() + " peers reachable";
                if (System.currentTimeMillis() - lastExchange > 60000) {
                    lastExchange = System.currentTimeMillis();
                    for (String peer : snapshot) {
                        try {
                            exchangePeers(peer);
                        } catch (Throwable th2) {
                        }
                    }
                }
            } else {
                this.syncStatus = "offline";
            }
            synchronized (this.syncWake) {
                try {
                    this.syncWake.wait(10000L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    boolean syncWith(String str) throws IOException {
        boolean z = false;
        Map<String, Object> obj = Json.obj(get(str, "/p2p/info"));
        if (this.nodeId.equals(Json.str(obj, "node", ""))) {
            this.selfAddrs.add(str);
            this.peers.remove(str);
            this.peerInfo.remove(str);
            this.manualPeers.remove(str);
            savePeers();
            return false;
        }
        if (!Bytes.hex(this.chain.chainId).equals(Json.str(obj, "chain", ""))) {
            failed(str, "different chain");
            return true;
        }
        long num = Json.num(obj, "height");
        BigInteger bigInteger = new BigInteger(Json.str(obj, "work"));
        boolean z2 = this.peerInfo.containsKey(str) && Json.bool(this.peerInfo.get(str), "ok", false);
        this.peerInfo.put(str, Json.o("ok", true, "height", Long.valueOf(num), "seen", Long.valueOf(System.currentTimeMillis()), "agent", Json.str(obj, "agent", "")));
        this.failures.remove(str);
        if (!z2) {
            savePeers();
        }
        if (bigInteger.compareTo(this.chain.work()) <= 0) {
            pullMempool(str);
            return true;
        }
        this.syncStatus = "syncing from " + str;
        long findFork = findFork(str, num);
        long j = 1 + findFork;
        if (findFork == this.chain.height()) {
            while (true) {
                boolean z3 = z;
                if (j > num || z3) {
                    break;
                }
                List list = (List) Json.parse(get(str, "/p2p/blocks?from=" + j + "&count=50"));
                if (list.isEmpty()) {
                    break;
                }
                Iterator it = list.iterator();
                while (true) {
                    if (!it.hasNext()) {
                        z = z3;
                        break;
                    }
                    Block fromJson = Block.fromJson((Map) it.next());
                    Chain.Result addBlock = this.chain.addBlock(fromJson);
                    if (!addBlock.ok) {
                        Log.w("sync", "block " + fromJson.height + " from " + str + ": " + addBlock.error);
                        z = true;
                        break;
                    }
                }
                j += list.size();
            }
            this.mempool.revalidate(null);
        } else {
            ArrayList arrayList = new ArrayList();
            long j2 = 0;
            long j3 = j;
            while (j3 <= num && arrayList.size() < 5000 && j2 < MAX_REORG_BYTES) {
                String body = get(str, "/p2p/blocks?from=" + j3 + "&count=50");
                long length = body.length() + j2;
                List list2 = (List) Json.parse(body);
                if (list2.isEmpty()) {
                    break;
                }
                Iterator it2 = list2.iterator();
                while (it2.hasNext()) {
                    arrayList.add(Block.fromJson((Map) it2.next()));
                }
                j3 += list2.size();
                j2 = length;
            }
            if (arrayList.isEmpty()) {
                return true;
            }
            StringBuilder sb = new StringBuilder();
            List<Tx> tryReorg = this.chain.tryReorg(findFork, arrayList, sb);
            if (tryReorg == null) {
                Log.w("sync", "not switching to " + str + "'s chain: " + ((Object) sb));
            } else {
                Log.i("sync", "switched to " + str + "'s chain at fork " + findFork);
                this.mempool.revalidate(tryReorg);
            }
        }
        this.miner.refresh();
        this.wallets.onNewBlock();
        pullMempool(str);
        return true;
    }

    private long findFork(String str, long j) throws IOException {
        long min = Math.min(this.chain.height(), j);
        long max = Math.max(0L, min - 199);
        while (true) {
            long j2 = max;
            List list = (List) Json.parse(get(str, "/p2p/hashes?from=" + j2 + "&count=" + ((min - j2) + 1)));
            for (long min2 = Math.min(min, (list.size() + j2) - 1); min2 >= j2; min2--) {
                Block at = this.chain.at(min2);
                if (at != null && at.hashHex().equals(list.get((int) (min2 - j2)))) {
                    return min2;
                }
            }
            if (j2 == 0) {
                return 0L;
            }
            min = j2 - 1;
            max = Math.max(0L, min - 1999);
        }
    }

    private void pullMempool(String str) {
        try {
            Iterator it = ((List) Json.parse(get(str, "/p2p/mempool"))).iterator();
            while (it.hasNext()) {
                try {
                    Tx fromJson = Tx.fromJson((Map) it.next());
                    if (!this.mempool.has(fromJson.id(this.chain.chainId))) {
                        this.mempool.add(fromJson);
                    }
                } catch (RuntimeException e) {
                }
            }
        } catch (Exception e2) {
        }
    }

    private void exchangePeers(String str) {
        String normalizePeer;
        try {
            post(str, "/p2p/hello", Json.write(Json.o("port", Long.valueOf(this.port))));
            for (Object obj : (List) Json.parse(get(str, "/p2p/peers"))) {
                if ((obj instanceof String) && this.peers.size() < 32 && (normalizePeer = normalizePeer((String) obj)) != null && !this.selfAddrs.contains(normalizePeer)) {
                    this.peers.add(normalizePeer);
                }
            }
        } catch (Exception e) {
        }
    }

    private void broadcast(final String str, final String str2, String str3) {
        ArrayList<String> arrayList;
        if (!offline()) {
            synchronized (this.peers) {
                arrayList = new ArrayList(this.peers);
            }
            for (final String str4 : arrayList) {
                if (!str4.equals(str3)) {
                    try {
                        this.relay.execute(new Runnable() {
                            @Override // java.lang.Runnable
                            public void run() {
                                try {
                                    Node.this.post(str4, str, str2);
                                } catch (Exception e) {
                                }
                            }
                        });
                    } catch (RuntimeException e) {
                    }
                }
            }
        }
    }

    String get(String str, String str2) throws IOException {
        return http("GET", str, str2, null);
    }

    String post(String str, String str2, String str3) throws IOException {
        return http("POST", str, str2, str3);
    }

    private String http(String str, String str2, String str3, String str4) throws IOException {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL("http://" + str2 + str3).openConnection();
        try {
            httpURLConnection.setConnectTimeout(Resonance.MIN_DUR_MS);
            httpURLConnection.setReadTimeout(20000);
            httpURLConnection.setRequestMethod(str);
            httpURLConnection.setRequestProperty("User-Agent", AGENT);
            httpURLConnection.setRequestProperty("X-Frost-Port", String.valueOf(this.port));
            httpURLConnection.setInstanceFollowRedirects(false);
            if (str4 != null) {
                byte[] utf8 = Bytes.utf8(str4);
                httpURLConnection.setDoOutput(true);
                httpURLConnection.setRequestProperty("Content-Type", "application/json");
                httpURLConnection.setFixedLengthStreamingMode(utf8.length);
                OutputStream outputStream = httpURLConnection.getOutputStream();
                outputStream.write(utf8);
                outputStream.close();
            }
            int responseCode = httpURLConnection.getResponseCode();
            InputStream inputStream = responseCode < 400 ? httpURLConnection.getInputStream() : httpURLConnection.getErrorStream();
            String str5 = inputStream == null ? "" : new String(readAll(inputStream, MAX_RESPONSE), Bytes.UTF8);
            if (responseCode >= 400) {
                try {
                    str5 = Json.str(Json.obj(str5), "error");
                } catch (RuntimeException e) {
                }
                throw new IOException(str5);
            }
            return str5;
        } finally {
            httpURLConnection.disconnect();
        }
    }

    static byte[] readAll(InputStream inputStream, int i) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] bArr = new byte[16384];
        do {
            try {
                int read = inputStream.read(bArr);
                if (read > 0) {
                    byteArrayOutputStream.write(bArr, 0, read);
                } else {
                    inputStream.close();
                    return byteArrayOutputStream.toByteArray();
                }
            } catch (Throwable th) {
                inputStream.close();
                throw th;
            }
        } while (byteArrayOutputStream.size() <= i);
        throw new IOException("response too large");
    }

    HttpServer.Response handleP2P(HttpServer.Request request) {
        String str = request.path;
        String str2 = request.headers.get("x-frost-port");
        String str3 = (str2 == null || !str2.matches("[0-9]{1,5}")) ? null : request.remote + ":" + str2;
        if (str.equals("/p2p/info") || str.equals("/api/status")) {
            Block tip = this.chain.tip();
            return HttpServer.Response.json(Json.o("chain", Bytes.hex(this.chain.chainId), "node", this.nodeId, "height", Long.valueOf(tip.height), "tip", tip.hashHex(), "work", this.chain.work().toString(), "difficulty", U64.str(this.chain.nextDifficulty()), "agent", AGENT, "coin", Consensus.COIN, "mempool", Long.valueOf(this.mempool.size()), "generated", U64.format(this.chain.generated())));
        }
        if (str.equals("/p2p/hashes")) {
            return HttpServer.Response.json(this.chain.hashes(Long.parseLong(request.q("from", "0")), Math.min(Mempool.MAX, Integer.parseInt(request.q("count", "100")))));
        }
        if (str.equals("/p2p/blocks")) {
            long parseLong = Long.parseLong(request.q("from", "0"));
            int min = Math.min(50, Integer.parseInt(request.q("count", "10")));
            ArrayList arrayList = new ArrayList();
            int i = 0;
            for (Block block : this.chain.range(parseLong, min)) {
                Map<String, Object> json = block.toJson();
                int size = (block.txs.size() * 5300) + 400 + i;
                arrayList.add(json);
                if (size > 3000000) {
                    break;
                }
                i = size;
            }
            return HttpServer.Response.json(arrayList);
        }
        if (str.equals("/p2p/block") && request.method.equals("POST")) {
            if (offline()) {
                return HttpServer.Response.error(403, "node is offline");
            }
            Block fromJson = Block.fromJson(Json.obj(request.bodyText()));
            if (str3 != null && !this.selfAddrs.contains(str3)) {
                addPeerQuietly(str3);
            }
            Chain.Result submitBlock = submitBlock(fromJson, str3);
            return (submitBlock.ok || this.chain.has(fromJson.hash())) ? HttpServer.Response.json(Json.o("ok", true)) : HttpServer.Response.error(400, submitBlock.error);
        }
        if (str.equals("/p2p/tx") && request.method.equals("POST")) {
            if (offline()) {
                return HttpServer.Response.error(403, "node is offline");
            }
            Tx fromJson2 = Tx.fromJson(Json.obj(request.bodyText()));
            String submitTx = submitTx(fromJson2, str3);
            return submitTx == null ? HttpServer.Response.json(Json.o("ok", true, "txid", Bytes.hex(fromJson2.id(this.chain.chainId)))) : HttpServer.Response.error(400, submitTx);
        }
        if (str.equals("/p2p/mempool")) {
            ArrayList arrayList2 = new ArrayList();
            for (Tx tx : this.mempool.all()) {
                if (arrayList2.size() >= 200) {
                    break;
                }
                arrayList2.add(tx.toJson());
            }
            return HttpServer.Response.json(arrayList2);
        }
        if (str.equals("/p2p/peers")) {
            ArrayList arrayList3 = new ArrayList();
            synchronized (this.peers) {
                for (String str4 : this.peers) {
                    Map<String, Object> map = this.peerInfo.get(str4);
                    if (map != null && Json.bool(map, "ok", false)) {
                        arrayList3.add(str4);
                    }
                }
            }
            return HttpServer.Response.json(arrayList3);
        }
        if (str.equals("/p2p/hello") && request.method.equals("POST")) {
            long num = Json.num(Json.obj(request.bodyText()), "port", 0L);
            if (num > 0 && num < 65536 && !offline()) {
                addPeerQuietly(request.remote + ":" + num);
            }
            return HttpServer.Response.json(Json.o("ok", true));
        }
        if (str.equals("/api/resolve")) {
            return HttpServer.Response.json(resolveJson(request.q("addr", "")));
        }
        if (str.equals("/api/account")) {
            return HttpServer.Response.json(accountJson(resolve(request.q("addr", ""))));
        }
        if (str.equals("/api/history")) {
            byte[] resolve = resolve(request.q("addr", ""));
            ArrayList arrayList4 = new ArrayList();
            Iterator<Chain.Event> it = this.chain.history(resolve).iterator();
            while (it.hasNext()) {
                arrayList4.add(it.next().toJson());
            }
            return HttpServer.Response.json(arrayList4);
        }
        if (str.equals("/api/block")) {
            Block at = this.chain.at(Long.parseLong(request.q("h", String.valueOf(this.chain.height()))));
            if (at == null) {
                return HttpServer.Response.error(404, "no such block");
            }
            Map<String, Object> json2 = at.toJson();
            json2.put("hash", at.hashHex());
            return HttpServer.Response.json(json2);
        }
        if (str.equals("/") || str.equals("/p2p")) {
            HttpServer.Response response = new HttpServer.Response();
            response.type = "text/plain; charset=utf-8";
            response.body = Bytes.utf8("Frostchain/0.1 (Graysons Wallet)\nFrostchain node. Height " + this.chain.height() + ".\n");
            return response;
        }
        return null;
    }

    public void addPeerQuietly(String str) {
        String normalizePeer = normalizePeer(str);
        if (normalizePeer == null || this.selfAddrs.contains(normalizePeer) || this.peers.size() >= 64) {
            return;
        }
        this.peers.add(normalizePeer);
    }

    public byte[] resolve(String str) {
        String strip = Address.strip(str);
        if (strip.isEmpty()) {
            throw new IllegalArgumentException("enter a .frostchain name or address");
        }
        if (Address.isRaw(strip)) {
            return Address.decodeRaw(strip);
        }
        if (!Address.validName(strip)) {
            throw new IllegalArgumentException("\"" + str.trim() + "\" isn't a valid .frostchain name or address");
        }
        byte[] lookupName = this.chain.lookupName(strip);
        if (lookupName == null && strip.equals(Consensus.FOUNDER_NAME)) {
            throw new IllegalArgumentException(strip + ".frostchain is reserved: it goes to whoever mines block 1");
        }
        if (lookupName == null) {
            throw new IllegalArgumentException(strip + ".frostchain isn't registered yet");
        }
        return lookupName;
    }

    public Map<String, Object> resolveJson(String str) {
        byte[] resolve = resolve(str);
        String nameOf = this.chain.nameOf(resolve);
        Object[] objArr = new Object[6];
        objArr[0] = "id";
        objArr[1] = Bytes.hex(resolve);
        objArr[2] = "raw";
        objArr[3] = Address.raw(resolve);
        objArr[4] = "name";
        objArr[5] = nameOf == null ? "" : Address.display(nameOf);
        return Json.o(objArr);
    }

    public Map<String, Object> accountJson(byte[] bArr) {
        ChainState.Account account = this.chain.account(bArr);
        long[] pendingFor = this.mempool.pendingFor(bArr);
        Map<String, Object> o = Json.o("id", Bytes.hex(bArr), "raw", Address.raw(bArr));
        o.put("name", (account == null || account.name == null) ? "" : Address.display(account.name));
        long j = account == null ? 0L : account.balance;
        long j2 = account == null ? 0L : account.immature;
        o.put("balance", U64.str(j));
        o.put("balanceText", U64.format(j));
        o.put("immature", U64.str(j2));
        o.put("immatureText", U64.format(j2));
        o.put("pendingOut", U64.str(pendingFor[0]));
        o.put("pendingOutText", U64.format(pendingFor[0]));
        o.put("pendingIn", U64.str(pendingFor[1]));
        o.put("pendingInText", U64.format(pendingFor[1]));
        o.put("keyIndex", Long.valueOf(account == null ? 0 : account.keyIndex));
        o.put("leavesUsed", Long.valueOf(account != null ? account.q : 0));
        o.put("leaves", 1024L);
        return o;
    }

    private void startDiscovery() {
        try {
            this.udp = new DatagramSocket((SocketAddress) null);
            this.udp.setReuseAddress(true);
            this.udp.setBroadcast(true);
            this.udp.bind(new InetSocketAddress(Consensus.P2P_PORT));
        } catch (SocketException e) {
            Log.i("node", "LAN discovery listener unavailable: " + e.getMessage());
            try {
                this.udp = new DatagramSocket();
                this.udp.setBroadcast(true);
            } catch (SocketException e2) {
                return;
            }
        }
        final DatagramSocket datagramSocket = this.udp;
        this.udpThread = new Thread(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                byte[] bArr = new byte[256];
                while (Node.this.running && !datagramSocket.isClosed()) {
                    try {
                        DatagramPacket datagramPacket = new DatagramPacket(bArr, bArr.length);
                        datagramSocket.receive(datagramPacket);
                        String[] split = new String(datagramPacket.getData(), 0, datagramPacket.getLength(), Bytes.UTF8).trim().split(" ");
                        if (split.length == 3 && split[0].equals("FROSTCHAIN1") && split[1].equals(Bytes.hex(Node.this.chain.chainId).substring(0, 16)) && !Node.this.offline() && Json.bool(Node.this.settings, "discovery", true)) {
                            String hostAddress = datagramPacket.getAddress().getHostAddress();
                            String str = hostAddress + ":" + Integer.parseInt(split[2]);
                            if (Integer.parseInt(split[2]) != Node.this.port || !Node.localIps().contains(hostAddress)) {
                                if (!Node.this.selfAddrs.contains(str) && !Node.this.peers.contains(str)) {
                                    Node.this.addPeerQuietly(str);
                                    Log.i("node", "found peer on LAN: " + str);
                                    Node.this.wakeSync();
                                }
                            } else {
                                Node.this.selfAddrs.add(str);
                            }
                        }
                    } catch (Throwable th) {
                        if (!Node.this.running) {
                            return;
                        }
                    }
                }
            }
        }, "frostchain-lan-listen");
        this.udpThread.setDaemon(true);
        this.udpThread.start();
        this.beaconThread = new Thread(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                while (Node.this.running) {
                    if (!Node.this.offline() && Json.bool(Node.this.settings, "discovery", true)) {
                        Node.this.beacon(datagramSocket);
                    }
                    try {
                        Thread.sleep(30000L);
                    } catch (InterruptedException e3) {
                        return;
                    }
                }
            }
        }, "frostchain-lan-beacon");
        this.beaconThread.setDaemon(true);
        this.beaconThread.start();
    }

    public void beacon(DatagramSocket datagramSocket) {
        byte[] utf8 = Bytes.utf8("FROSTCHAIN1 " + Bytes.hex(this.chain.chainId).substring(0, 16) + " " + this.port);
        LinkedHashSet linkedHashSet = new LinkedHashSet();
        try {
            linkedHashSet.add(InetAddress.getByName("255.255.255.255"));
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces != null) {
                if (!networkInterfaces.hasMoreElements()) {
                    break;
                }
                NetworkInterface nextElement = networkInterfaces.nextElement();
                if (nextElement.isUp() && !nextElement.isLoopback()) {
                    for (InterfaceAddress interfaceAddress : nextElement.getInterfaceAddresses()) {
                        if (interfaceAddress.getBroadcast() != null) {
                            linkedHashSet.add(interfaceAddress.getBroadcast());
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
        Iterator it = linkedHashSet.iterator();
        while (it.hasNext()) {
            try {
                datagramSocket.send(new DatagramPacket(utf8, utf8.length, (InetAddress) it.next(), Consensus.P2P_PORT));
            } catch (Exception e2) {
            }
        }
    }

    static List<String> localIps() {
        ArrayList arrayList = new ArrayList();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces != null) {
                if (!networkInterfaces.hasMoreElements()) {
                    break;
                }
                Enumeration<InetAddress> inetAddresses = networkInterfaces.nextElement().getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    arrayList.add(inetAddresses.nextElement().getHostAddress());
                }
            }
        } catch (Exception e) {
        }
        return arrayList;
    }

    private void loadPeers() {
        String normalizePeer;
        String normalizePeer2;
        File file = new File(this.dir, "peers.json");
        if (file.exists()) {
            try {
                Object parse = Json.parse(readFile(file));
                List arrayList = new ArrayList();
                List arrayList2 = new ArrayList();
                if (parse instanceof List) {
                    arrayList = (List) parse;
                } else if (parse instanceof Map) {
                    arrayList = Json.list((Map) parse, "peers");
                    arrayList2 = Json.list((Map) parse, "manual");
                }
                for (Object obj : arrayList2) {
                    if ((obj instanceof String) && (normalizePeer2 = normalizePeer((String) obj)) != null) {
                        this.manualPeers.add(normalizePeer2);
                        this.peers.add(normalizePeer2);
                    }
                }
                for (Object obj2 : arrayList) {
                    if ((obj2 instanceof String) && (normalizePeer = normalizePeer((String) obj2)) != null) {
                        this.peers.add(normalizePeer);
                    }
                }
            } catch (Exception e) {
                Log.w("node", "peers.json: " + e);
            }
        }
    }

    private void savePeers() {
        ArrayList arrayList = new ArrayList();
        synchronized (this.peers) {
            for (String str : this.peers) {
                Map<String, Object> map = this.peerInfo.get(str);
                if (this.manualPeers.contains(str) || (map != null && Json.bool(map, "ok", false))) {
                    arrayList.add(str);
                }
            }
        }
        writeFile(new File(this.dir, "peers.json"), Json.write(Json.o("manual", new ArrayList(this.manualPeers), "peers", arrayList)));
    }

    private void loadSettings() {
        File file = new File(this.dir, "settings.json");
        if (file.exists()) {
            try {
                this.settings = Json.obj(readFile(file));
            } catch (Exception e) {
                Log.w("node", "settings.json: " + e);
            }
        }
    }

    private void saveSettings() {
        writeFile(new File(this.dir, "settings.json"), Json.write(this.settings));
    }

    static String readFile(File file) throws IOException {
        return new String(readAll(new FileInputStream(file), 67108864), Bytes.UTF8);
    }

    static void writeFile(File file, String str) {
        File file2 = new File(file.getPath() + ".tmp");
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            try {
                fileOutputStream.write(Bytes.utf8(str));
                fileOutputStream.getFD().sync();
                fileOutputStream.close();
                if (file2.renameTo(file)) {
                    return;
                }
                file.delete();
                if (!file2.renameTo(file)) {
                    throw new IOException("rename failed");
                }
            } catch (Throwable th) {
                fileOutputStream.close();
                throw th;
            }
        } catch (IOException e) {
            Log.w("node", "couldn't write " + file.getName() + ": " + e);
        }
    }
}
