package com.frostether.frostchain;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Temporal, a time machine for Frostchain, with QNR as the gas it burns.
 *
 * <p>Seal: a capsule is a message with an opening time. Its fingerprint goes on the chain in the memo of a payment of
 * at least {@link #GAS} to {@link #BURN_ADDRESS}, an address no key can spend, and the block that carries it proves the
 * message existed by then. The message stays with whoever sealed it, and Temporal keeps it shut until it opens.
 *
 * <p>Travel: the chain as it stood at any moment since genesis.
 *
 * <p>None of this is a consensus rule. A seal is an ordinary payment, so Temporal runs on the v0.4 chain as it is. The
 * page at get.finux.tech/temporal follows the same rules byte for byte; TemporalTest pins a vector both reproduce.
 */
public final class Temporal {
    /** Versions the fingerprint, the burn address and the memo tag together. */
    public static final String WORDS = "temporal/v1";
    /** The burn account is a hash of fixed words, not of a public key, so nobody can hold a key for it. */
    public static final byte[] BURN_ID = Bytes.slice(Sha256.hash(Bytes.utf8("frostchain/burn"), Bytes.utf8(WORDS)), 0, 20);
    public static final String BURN_ADDRESS = Address.raw(BURN_ID);
    /** The gas for one seal: 1 QNR, burned for good. */
    public static final long GAS = U64.COIN;
    public static final String TAG = "T1";
    public static final int MAX_TEXT = 4000;
    /** A seal needs a few blocks to land before it opens. */
    public static final long MIN_LEAD = 15 * 60;
    private static final SecureRandom RNG = new SecureRandom();

    private Temporal() {
    }

    /** First 20 bytes of SHA-256("temporal/v1\n" + opens + "\n" + salt + "\n" + text), in hex. */
    public static String fingerprint(long opens, String salt, String text) {
        return Bytes.hex(Bytes.slice(Sha256.hash(Bytes.utf8(WORDS + "\n" + opens + "\n" + salt + "\n" + text)), 0, 20));
    }

    /** "T1 1823017020 25ced2cf…": 54 bytes, inside the 64-byte memo limit. */
    public static String memo(long opens, String fingerprint) {
        return TAG + " " + opens + " " + fingerprint;
    }

    public static String newSalt() {
        byte[] b = new byte[16];
        RNG.nextBytes(b);
        return Bytes.hex(b);
    }

    /** {opens, fingerprint} from a seal memo, or null for any other memo. */
    static Object[] parse(String memo) {
        if (memo == null) {
            return null;
        }
        String[] p = memo.trim().split(" ");
        if (p.length != 3 || !p[0].equals(TAG) || !p[1].matches("[0-9]{1,12}") || !p[2].matches("[0-9a-f]{40}")) {
            return null;
        }
        return new Object[]{Long.valueOf(Long.parseLong(p[1])), p[2]};
    }

    /** A seal found on the chain. */
    public static final class Seal {
        public final long opens;
        public final String fingerprint;
        public final long height;
        public final long time;
        public final long burned;
        public final String txid;
        /** The sender's account id in hex. */
        public final String from;

        Seal(long opens, String fingerprint, Chain.Event e) {
            this.opens = opens;
            this.fingerprint = fingerprint;
            this.height = e.height;
            this.time = e.time;
            this.burned = e.amount;
            this.txid = e.txid;
            this.from = e.other;
        }

        /** It landed in a block before its opening time, so the block proves the words came first. */
        public boolean onTime() {
            return this.time < this.opens;
        }

        public Map<String, Object> toJson() {
            Map<String, Object> o = Json.o("opens", Long.valueOf(this.opens), "fingerprint", this.fingerprint,
                    "height", Long.valueOf(this.height), "time", Long.valueOf(this.time), "burned", U64.str(this.burned),
                    "burnedText", U64.format(this.burned), "txid", this.txid);
            o.put("from", this.from.isEmpty() ? "" : Address.raw(Bytes.unhex(this.from)));
            o.put("onTime", Boolean.valueOf(onTime()));
            return o;
        }
    }

    /** Every seal on the chain, oldest first. Payments under the gas and repeats of a fingerprint don't count. */
    public static List<Seal> seals(Chain chain) {
        List<Seal> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Chain.Event e : chain.history(BURN_ID)) {
            if (!"received".equals(e.kind) || U64.cmp(e.amount, GAS) < 0) {
                continue;
            }
            Object[] m = parse(e.memo);
            if (m != null && seen.add((String) m[1])) {
                out.add(new Seal(((Long) m[0]).longValue(), (String) m[1], e));
            }
        }
        return out;
    }

    /** The first seal of this fingerprint, or null. */
    public static Seal find(Chain chain, String fingerprint) {
        String fp = fingerprint == null ? "" : fingerprint.trim().toLowerCase(java.util.Locale.ROOT);
        for (Seal s : seals(chain)) {
            if (s.fingerprint.equals(fp)) {
                return s;
            }
        }
        return null;
    }

    /** Everything ever sent to the burn address, gas or not. None of it can move again. */
    public static long burned(Chain chain) {
        ChainState.Account a = chain.account(BURN_ID);
        return a == null ? 0L : U64.add(a.balance, a.immature);
    }

    public static Map<String, Object> info(Chain chain) {
        long burned = burned(chain);
        Map<String, Object> o = Json.o("burnAddress", BURN_ADDRESS, "gas", U64.str(GAS), "gasText", U64.format(GAS),
                "burned", U64.str(burned), "burnedText", U64.format(burned));
        o.put("count", Long.valueOf(seals(chain).size()));
        o.put("minLead", Long.valueOf(MIN_LEAD));
        o.put("maxText", Long.valueOf(MAX_TEXT));
        return o;
    }

    /** The chain at one moment: the last block mined by then, the QNR mined by then and the seals made by then. */
    public static final class At {
        public final Block block;
        public final long mined;
        public final int seals;

        At(Block block, long mined, int seals) {
            this.block = block;
            this.mined = mined;
            this.seals = seals;
        }
    }

    public static At at(Chain chain, long time) {
        // Block times only go up (each block is at least MIN_BLOCK_SPACING after the last), so search by time.
        long lo = 0, hi = chain.height();
        while (lo < hi) {
            long mid = (lo + hi + 1) >>> 1;
            Block b = chain.at(mid);
            if (b != null && b.time <= time) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        Block block = chain.at(lo);
        long mined = 0;
        for (long h = 1; h <= block.height; h++) {
            mined = U64.add(mined, Consensus.reward(h, mined));
        }
        int seals = 0;
        for (Seal s : seals(chain)) {
            if (s.time <= time) {
                seals++;
            }
        }
        return new At(block, mined, seals);
    }
}
