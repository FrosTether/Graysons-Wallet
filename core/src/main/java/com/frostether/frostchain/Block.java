package com.frostether.frostchain;

import com.frostether.frostchain.Resonance;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class Block {
    public static final int HEADER_LEN = 152;
    public static final int NONCE_OFFSET = 144;
    private byte[] cachedHash;
    public long difficulty;
    public long height;
    public long nonce;
    public transient long paid;
    public Resonance.Proof reso;
    public long time;
    public int version = 1;
    public byte[] prev = new byte[32];
    public byte[] root = new byte[32];
    public byte[] miner = new byte[20];
    public byte[] resoHash = new byte[32];
    public List<Tx> txs = new ArrayList();

    public byte[] header() {
        byte[] bArr = new byte[HEADER_LEN];
        Bytes.u32(bArr, 0, this.version);
        Bytes.u64(bArr, 4, this.height);
        System.arraycopy(this.prev, 0, bArr, 12, 32);
        Bytes.u64(bArr, 44, this.time);
        Bytes.u64(bArr, 52, this.difficulty);
        System.arraycopy(this.root, 0, bArr, 60, 32);
        System.arraycopy(this.miner, 0, bArr, 92, 20);
        System.arraycopy(this.resoHash, 0, bArr, 112, 32);
        Bytes.u64(bArr, NONCE_OFFSET, this.nonce);
        return bArr;
    }

    public byte[] hash() {
        if (this.cachedHash == null) {
            this.cachedHash = Sha256.hash2(header());
        }
        return this.cachedHash;
    }

    public void invalidateHash() {
        this.cachedHash = null;
    }

    public String hashHex() {
        return Bytes.hex(hash());
    }

    public static byte[] txRoot(List<Tx> list, byte[] bArr) {
        Sha256 update = new Sha256().update(Bytes.utf8("frostchain/txroot")).update(Bytes.u32(list.size()));
        Iterator<Tx> it = list.iterator();
        while (it.hasNext()) {
            update.update(it.next().wid(bArr));
        }
        return update.digest();
    }

    public Map<String, Object> toJson() {
        Map<String, Object> o = Json.o("v", Long.valueOf(this.version), "h", Long.valueOf(this.height), "prev", Bytes.hex(this.prev), "t", Long.valueOf(this.time), "d", U64.str(this.difficulty), "root", Bytes.hex(this.root), "miner", Bytes.hex(this.miner), "resohash", Bytes.hex(this.resoHash), "nonce", U64.str(this.nonce));
        if (this.reso != null) {
            o.put("reso", this.reso.toJson());
        }
        ArrayList arrayList = new ArrayList();
        Iterator<Tx> it = this.txs.iterator();
        while (it.hasNext()) {
            arrayList.add(it.next().toJson());
        }
        o.put("txs", arrayList);
        return o;
    }

    public static Block fromJson(Map<String, Object> map) {
        Block block = new Block();
        block.version = (int) Json.num(map, "v");
        block.height = Json.num(map, "h");
        block.prev = Bytes.unhex(Json.str(map, "prev"), 32);
        block.time = Json.num(map, "t");
        block.difficulty = U64.parse(Json.str(map, "d"));
        block.root = Bytes.unhex(Json.str(map, "root"), 32);
        block.miner = Bytes.unhex(Json.str(map, "miner"), 20);
        block.resoHash = Bytes.unhex(Json.str(map, "resohash"), 32);
        block.nonce = U64.parse(Json.str(map, "nonce"));
        if (map.get("reso") != null) {
            block.reso = Resonance.Proof.fromJson((Map) map.get("reso"));
        }
        List<Object> list = Json.list(map, "txs");
        if (list.size() > 200) {
            throw new IllegalArgumentException("too many transactions");
        }
        Iterator<Object> it = list.iterator();
        while (it.hasNext()) {
            block.txs.add(Tx.fromJson((Map) it.next()));
        }
        return block;
    }

    public static Block genesis() {
        Block block = new Block();
        block.height = 0L;
        block.time = Consensus.GENESIS_TIME;
        block.difficulty = Consensus.INITIAL_DIFFICULTY;
        block.resoHash = Resonance.genesisMarker();
        block.root = txRoot(new ArrayList(), new byte[32]);
        block.nonce = 783L;
        return block;
    }
}
