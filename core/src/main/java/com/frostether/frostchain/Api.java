package com.frostether.frostchain;

import com.frostether.frostchain.Miner;
import com.frostether.frostchain.Resonance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Api {
    private final Node node;
    private final Platform platform;
    private final TemporalStore capsules;

    public interface Platform {
        Resonance.Reading latestReading();

        void miningChanged(boolean z);

        String name();

        Map<String, Object> sensors();

        void startSensor(String str) throws Exception;

        void stopSensor();

        /** How hot the device is, 0 (fine) to 3 (too hot to mine). See Miner.ThermalSource. */
        default int thermalLevel() {
            return 0;
        }
    }

    public Api(Node node, Platform platform) {
        this.node = node;
        this.platform = platform;
        this.capsules = new TemporalStore(node.dir);
        node.miner.setSource(new Miner.ResonanceSource() {
            @Override // com.frostether.frostchain.Miner.ResonanceSource
            public Resonance.Reading latest() {
                return Api.this.platform.latestReading();
            }
        });
        node.miner.setThermal(new Miner.ThermalSource() {
            @Override // com.frostether.frostchain.Miner.ThermalSource
            public int level() {
                return Api.this.platform.thermalLevel();
            }
        });
    }

    /** Rebuilt from the 0.3.0 bytecode: "bad request" only covers unreadable input. */
    public String call(String str, String str2) {
        Map<String, Object> obj;
        try {
            obj = (str2 == null || str2.trim().isEmpty()) ? Json.o(new Object[0]) : Json.obj(str2);
        } catch (IllegalArgumentException e) {
            return Json.write(Json.o("error", "bad request"));
        }
        try {
            return Json.write(Json.o("ok", true, "result", dispatch(str, obj)));
        } catch (IllegalArgumentException e2) {
            return Json.write(Json.o("error", e2.getMessage()));
        } catch (IllegalStateException e3) {
            return Json.write(Json.o("error", e3.getMessage()));
        } catch (Exception e4) {
            Log.w("api", str + ": " + e4);
            return Json.write(Json.o("error", "something went wrong: " + e4.getClass().getSimpleName()));
        }
    }

    private Object dispatch(String str, Map<String, Object> map) throws Exception {
        Wallets wallets = this.node.wallets;
        if (str.equals("status")) {
            return status();
        }
        if (str.equals("wallets.list")) {
            return wallets.list();
        }
        if (str.equals("wallet.create")) {
            return wallets.create(Json.str(map, "label", ""), Json.str(map, "password", ""));
        }
        if (str.equals("wallet.restore")) {
            return wallets.restore(Json.str(map, "label", ""), Json.str(map, "password", ""), Json.str(map, "seed", ""));
        }
        if (str.equals("wallet.watch")) {
            return wallets.watch(Json.str(map, "label", ""), Json.str(map, "address", ""));
        }
        if (str.equals("wallet.open")) {
            return wallets.open(Json.str(map, "file", ""), Json.str(map, "password", ""));
        }
        if (str.equals("wallet.close")) {
            wallets.close();
            return Json.o("open", false);
        }
        if (str.equals("wallet.delete")) {
            wallets.delete(Json.str(map, "file", ""));
            return Json.o("deleted", true);
        }
        if (str.equals("wallet.info")) {
            return wallets.info();
        }
        if (str.equals("wallet.history")) {
            return wallets.history();
        }
        if (str.equals("wallet.seed")) {
            return Json.o("seed", wallets.seedWords(Json.str(map, "password", "")));
        }
        if (str.equals("wallet.send")) {
            return wallets.send(Json.str(map, "to", ""), Json.str(map, "amount", ""), Json.str(map, "memo", ""), Json.bool(map, "dry", false));
        }
        if (str.equals("wallet.register")) {
            return wallets.register(Json.str(map, "name", ""));
        }
        if (str.equals("wallet.max")) {
            return wallets.max();
        }
        if (str.equals("wallet.rekey")) {
            return wallets.rekey();
        }
        if (str.equals("resolve")) {
            return this.node.resolveJson(Json.str(map, "addr", ""));
        }
        if (str.equals("account")) {
            return this.node.accountJson(this.node.resolve(Json.str(map, "addr", "")));
        }
        if (str.startsWith("temporal.")) {
            return temporal(str.substring(9), map);
        }
        if (str.equals("node.peers")) {
            return this.node.peerList();
        }
        if (str.equals("node.addPeer")) {
            return Json.o("addr", this.node.addPeer(Json.str(map, "addr", "")));
        }
        if (str.equals("node.removePeer")) {
            this.node.removePeer(Json.str(map, "addr", ""));
            return Json.o("ok", true);
        }
        if (str.equals("node.setOffline")) {
            this.node.setOffline(Json.bool(map, "offline", false));
            return Json.o("offline", Boolean.valueOf(this.node.offline()));
        }
        if (str.equals("node.setDiscovery")) {
            this.node.putSetting("discovery", Boolean.valueOf(Json.bool(map, "on", true)));
            return Json.o("ok", true);
        }
        if (str.equals("node.blocks")) {
            return recentBlocks((int) Json.num(map, "count", 10L));
        }
        if (str.equals("sensor.start")) {
            this.platform.startSensor(Json.str(map, "sensor", "mag"));
            return this.platform.sensors();
        }
        if (str.equals("sensor.stop")) {
            this.node.miner.stop();
            this.platform.miningChanged(false);
            this.platform.stopSensor();
            return this.platform.sensors();
        }
        if (str.equals("miner.start")) {
            return startMining(map);
        }
        if (str.equals("miner.stop")) {
            this.node.miner.stop();
            this.platform.miningChanged(false);
            return this.node.miner.status();
        }
        if (str.equals("miner.status")) {
            return this.node.miner.status();
        }
        throw new IllegalArgumentException("unknown method " + str);
    }

    private Map<String, Object> status() {
        Block tip = this.node.chain.tip();
        Map<String, Object> o = Json.o("height", Long.valueOf(tip.height), "tip", tip.hashHex(), "tipTime", Long.valueOf(tip.time), "peers", Long.valueOf(this.node.peerList().size()), "sync", this.node.syncStatus(), "port", Long.valueOf(this.node.port()), "offline", Boolean.valueOf(this.node.offline()), "mempool", Long.valueOf(this.node.mempool.size()), "difficulty", U64.str(this.node.chain.nextDifficulty()), "generated", U64.format(this.node.chain.generated()), "chain", Bytes.hex(this.node.chain.chainId).substring(0, 16), "discovery", Boolean.valueOf(Json.bool(this.node.settings(), "discovery", true)), "accounts", Long.valueOf(this.node.chain.accountCount()));
        Resonance.Reading latestReading = this.platform.latestReading();
        Object[] objArr = new Object[18];
        objArr[0] = "node";
        objArr[1] = o;
        objArr[2] = "wallet";
        objArr[3] = this.node.wallets.info();
        objArr[4] = "miner";
        objArr[5] = this.node.miner.status();
        objArr[6] = "temporal";
        objArr[7] = Temporal.info(this.node.chain);
        objArr[8] = "resonance";
        objArr[9] = latestReading == null ? null : latestReading.toJson();
        objArr[10] = "sensors";
        objArr[11] = this.platform.sensors();
        objArr[12] = "platform";
        objArr[13] = this.platform.name();
        objArr[14] = "coin";
        objArr[15] = Consensus.COIN;
        objArr[16] = "now";
        objArr[17] = Long.valueOf(System.currentTimeMillis() / 1000);
        return Json.o(objArr);
    }

    /** Temporal, the time machine: see Temporal for the rules. Capsule words only leave here once they've opened. */
    private Object temporal(String what, Map<String, Object> map) throws Exception {
        Chain chain = this.node.chain;
        long now = chain.now();
        switch (what) {
            case "info": {
                Map<String, Object> o = Temporal.info(chain);
                o.put("now", Long.valueOf(now));
                o.put("genesis", Long.valueOf(Consensus.GENESIS_TIME));
                return o;
            }
            case "at": {
                Temporal.At at = Temporal.at(chain, Json.num(map, "time", now));
                Map<String, Object> o = Json.o("block", blockRow(at.block), "mined", U64.str(at.mined), "minedText", U64.format(at.mined),
                        "seals", Long.valueOf(at.seals));
                byte[] account = this.node.wallets.openAccount();
                if (account != null) {
                    long had = balanceAt(account, at.block.time);
                    o.put("balance", U64.str(had));
                    o.put("balanceText", U64.format(had));
                }
                return o;
            }
            case "seal": {
                long opens = Json.num(map, "opens", 0L);
                if (opens < now + Temporal.MIN_LEAD) {
                    throw new IllegalArgumentException("pick an opening time at least 15 minutes from now, so the seal lands in a block before it opens");
                }
                String salt = Json.str(map, "salt", "");
                Map<String, Object> c = TemporalStore.capsule(opens, salt.isEmpty() ? Temporal.newSalt() : salt, Json.str(map, "text", ""));
                String fp = (String) c.get("fingerprint");
                String memo = Temporal.memo(opens, fp);
                boolean dry = Json.bool(map, "dry", false);
                Map<String, Object> r = this.node.wallets.send(Temporal.BURN_ADDRESS, U64.format(Temporal.GAS), memo, dry);
                r.put("fingerprint", fp);
                r.put("salt", c.get("salt"));
                r.put("opens", Long.valueOf(opens));
                r.put("burnAddress", Temporal.BURN_ADDRESS);
                if (!dry) {
                    c.put("made", Long.valueOf(now));
                    c.put("txid", r.get("txid"));
                    this.capsules.put(c);
                }
                return r;
            }
            case "capsules": {
                List<Object> out = new ArrayList<>();
                for (Map<String, Object> c : this.capsules.all()) {
                    out.add(capsuleView(c, now));
                }
                return out;
            }
            case "code": {
                Map<String, Object> c = this.capsules.get(Json.str(map, "fingerprint", ""));
                if (c == null) {
                    throw new IllegalArgumentException("no capsule with that fingerprint here");
                }
                return Json.o("code", TemporalStore.code(c));
            }
            case "add": {
                Map<String, Object> c = TemporalStore.decode(Json.str(map, "code", ""));
                if (this.capsules.get((String) c.get("fingerprint")) == null) {
                    c.put("made", Long.valueOf(now));
                    this.capsules.put(c);
                }
                return capsuleView(this.capsules.get((String) c.get("fingerprint")), now);
            }
            case "after": {
                // the blocks after one, for riding forward in time
                long from = Json.num(map, "height", 0L);
                int count = (int) Math.max(1L, Math.min(24L, Json.num(map, "count", 12L)));
                List<Object> out = new ArrayList<>();
                for (long h = from + 1; h <= Math.min(chain.height(), from + count); h++) {
                    out.add(blockRow(chain.at(h)));
                }
                return out;
            }
            case "forget":
                return Json.o("forgotten", Boolean.valueOf(this.capsules.remove(Json.str(map, "fingerprint", ""))));
            default:
                throw new IllegalArgumentException("unknown method temporal." + what);
        }
    }

    private Map<String, Object> capsuleView(Map<String, Object> c, long now) {
        String fp = String.valueOf(c.get("fingerprint"));
        long opens = Json.num(c, "opens", 0L);
        boolean open = now >= opens;
        Temporal.Seal seal = Temporal.find(this.node.chain, fp);
        Map<String, Object> o = Json.o("fingerprint", fp, "opens", Long.valueOf(opens), "open", Boolean.valueOf(open),
                "made", Long.valueOf(Json.num(c, "made", 0L)), "txid", Json.str(c, "txid", ""));
        o.put("seal", seal == null ? null : seal.toJson());
        o.put("chars", Long.valueOf(Json.str(c, "text", "").length()));
        if (open) {
            o.put("text", Json.str(c, "text", ""));
        }
        return o;
    }

    /** What this account held at a moment: everything in, less everything out, up to that time. */
    private long balanceAt(byte[] account, long time) {
        long had = 0;
        for (Chain.Event e : this.node.chain.history(account)) {
            if (e.time > time) {
                break;
            }
            if ("mined".equals(e.kind) || "received".equals(e.kind)) {
                had += e.amount;
            } else if ("sent".equals(e.kind)) {
                had -= e.amount + e.fee;
            } else {
                had -= e.fee;
            }
        }
        return Math.max(0L, had);
    }

    private Map<String, Object> startMining(Map<String, Object> map) throws Exception {
        String str = Json.str(map, "payout", "");
        byte[] openAccount = str.trim().isEmpty() ? this.node.wallets.openAccount() : this.node.resolve(str);
        if (openAccount == null) {
            throw new IllegalArgumentException("open a wallet (or enter a payout address) so Frostoise knows where to pay");
        }
        int num = (int) Json.num(map, "threads", 1L);
        String str2 = Json.str(map, "sensor", "");
        if (!str2.isEmpty()) {
            this.platform.startSensor(str2);
        }
        this.node.miner.start(openAccount, num);
        this.platform.miningChanged(true);
        return this.node.miner.status();
    }

    private List<Object> recentBlocks(int i) {
        ArrayList arrayList = new ArrayList();
        long height = this.node.chain.height();
        while (true) {
            long j = height;
            if (j < 1 || arrayList.size() >= Math.min(50, i)) {
                break;
            }
            Block at = this.node.chain.at(j);
            Map<String, Object> row = blockRow(at);
            arrayList.add(row);
            height = j - 1;
        }
        return arrayList;
    }

    private Map<String, Object> blockRow(Block at) {
        String nameOf = at.height == 0 ? null : this.node.chain.nameOf(at.miner);
        Object[] objArr = new Object[18];
        objArr[0] = "height";
        objArr[1] = Long.valueOf(at.height);
        objArr[2] = "hash";
        objArr[3] = at.hashHex();
        objArr[4] = "time";
        objArr[5] = Long.valueOf(at.time);
        objArr[6] = "txs";
        objArr[7] = Long.valueOf(at.txs.size());
        objArr[8] = "miner";
        objArr[9] = at.height == 0 ? "" : nameOf != null ? Address.display(nameOf) : Address.raw(at.miner);
        objArr[10] = "paid";
        objArr[11] = U64.format(at.paid);
        objArr[12] = "hz";
        objArr[13] = Double.valueOf(at.reso == null ? 0.0d : at.reso.hzMilli / 1000.0d);
        objArr[14] = "sensor";
        objArr[15] = at.reso == null ? "" : at.reso.sensor;
        objArr[16] = "difficulty";
        objArr[17] = U64.str(at.difficulty);
        Map<String, Object> row = Json.o(objArr);
        int band = at.reso == null ? -1 : Resonance.bandOfMilli(at.reso.hzMilli);
        row.put("band", band >= 0 ? Resonance.BAND_NAMES[band] : "");
        return row;
    }
}
