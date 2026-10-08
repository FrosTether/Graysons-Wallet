package com.frostether.frostchain;

import com.frostether.frostchain.Bytes;
import java.util.Map;

public final class Tx {
    /** Part of every transaction's signed bytes, so it keeps the coin's first ticker: changing it would make a new chain. */
    private static final byte[] DOMAIN = Bytes.utf8("QOIN-FROSTCHAIN-TX1");
    public static final int NAME = 2;
    public static final int REKEY = 3;
    public static final int SEND = 1;
    public long amount;
    private byte[] cachedId;
    public long fee;
    public byte[] from;
    public int k;
    public byte[] newPub;
    public byte[] pub;
    public int q;
    public byte[] sig;
    public int type;
    public byte[] to = new byte[20];
    public String name = "";
    public String memo = "";

    public byte[] message(byte[] bArr) {
        Bytes.Writer raw = new Bytes.Writer().raw(DOMAIN).raw(bArr).u8(this.type).raw(this.from).u32(this.k).u32(this.q).raw(this.to).u64(this.amount).u64(this.fee).str8(Bytes.utf8(this.name)).str8(Bytes.utf8(this.memo)).raw(this.pub);
        if (this.newPub != null) {
            raw.u8(1).raw(this.newPub);
        } else {
            raw.u8(0);
        }
        return raw.bytes();
    }

    public byte[] id(byte[] bArr) {
        if (this.cachedId == null) {
            this.cachedId = Sha256.hash(message(bArr));
        }
        return this.cachedId;
    }

    public byte[] wid(byte[] bArr) {
        return Sha256.hash(id(bArr), Sha256.hash(this.sig));
    }

    public long spend() {
        return this.type == 1 ? U64.add(this.amount, this.fee) : this.fee;
    }

    public String checkStateless(byte[] bArr) {
        if (this.type != 1 && this.type != 2 && this.type != 3) {
            return "unknown transaction type";
        }
        if (this.from == null || this.from.length != 20) {
            return "bad sender";
        }
        if (this.to == null || this.to.length != 20) {
            return "bad recipient";
        }
        if (!Lms.validPublicKey(this.pub)) {
            return "bad public key";
        }
        if (this.sig == null || this.sig.length != Lms.SIG_LEN) {
            return "bad signature size";
        }
        if (Lms.sigLeaf(this.sig) != this.q || this.q < 0 || this.q >= 1024) {
            return "signature leaf doesn't match";
        }
        if (this.k < 0 || this.k > 1000000) {
            return "bad key index";
        }
        if (Bytes.utf8(this.memo).length > 64) {
            return "memo too long";
        }
        switch (this.type) {
            case 1:
                if (this.amount == 0) {
                    return "amount must be more than zero";
                }
                if (U64.cmp(this.fee, Consensus.MIN_FEE) < 0) {
                    return "fee below minimum";
                }
                if (Bytes.isZero(this.to) || Bytes.equal(this.to, this.from)) {
                    return "bad recipient";
                }
                if (!this.name.isEmpty() || this.newPub != null) {
                    return "unexpected fields";
                }
                try {
                    U64.add(this.amount, this.fee);
                } catch (ArithmeticException e) {
                    return "amount overflow";
                }
                break;
            case NAME:
                if (!Address.validName(this.name)) {
                    return "invalid name";
                }
                if (this.amount != 0 || !Bytes.isZero(this.to) || this.newPub != null) {
                    return "unexpected fields";
                }
                break;
            case 3:
                if (!Lms.validPublicKey(this.newPub) || Bytes.equal(this.newPub, this.pub)) {
                    return "bad new key";
                }
                if (U64.cmp(this.fee, Consensus.MIN_FEE) < 0) {
                    return "fee below minimum";
                }
                if (this.amount != 0 || !Bytes.isZero(this.to) || !this.name.isEmpty()) {
                    return "unexpected fields";
                }
                break;
        }
        if (Lms.verify(this.pub, message(bArr), this.sig)) {
            return null;
        }
        return "signature doesn't verify";
    }

    public Map<String, Object> toJson() {
        Map<String, Object> o = Json.o("type", Long.valueOf(this.type), "from", Bytes.hex(this.from), "k", Long.valueOf(this.k), "q", Long.valueOf(this.q));
        if (this.type == 1) {
            o.put("to", Bytes.hex(this.to));
            o.put("amount", U64.str(this.amount));
        }
        o.put("fee", U64.str(this.fee));
        if (!this.name.isEmpty()) {
            o.put("name", this.name);
        }
        if (!this.memo.isEmpty()) {
            o.put("memo", this.memo);
        }
        o.put("pub", Bytes.hex(this.pub));
        if (this.newPub != null) {
            o.put("newpub", Bytes.hex(this.newPub));
        }
        o.put("sig", Bytes.hex(this.sig));
        return o;
    }

    public static Tx fromJson(Map<String, Object> map) {
        Tx tx = new Tx();
        tx.type = (int) Json.num(map, "type");
        tx.from = Bytes.unhex(Json.str(map, "from"), 20);
        tx.k = (int) Json.num(map, "k");
        tx.q = (int) Json.num(map, "q");
        tx.to = map.containsKey("to") ? Bytes.unhex(Json.str(map, "to"), 20) : new byte[20];
        tx.amount = map.containsKey("amount") ? U64.parse(Json.str(map, "amount")) : 0L;
        tx.fee = U64.parse(Json.str(map, "fee"));
        tx.name = Json.str(map, "name", "");
        tx.memo = Json.str(map, "memo", "");
        if (tx.name.length() > 64 || tx.memo.length() > 256) {
            throw new IllegalArgumentException("field too long");
        }
        tx.pub = Bytes.unhex(Json.str(map, "pub"), 56);
        tx.newPub = map.containsKey("newpub") ? Bytes.unhex(Json.str(map, "newpub"), 56) : null;
        tx.sig = Bytes.unhex(Json.str(map, "sig"), Lms.SIG_LEN);
        return tx;
    }
}
