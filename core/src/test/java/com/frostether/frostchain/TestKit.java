package com.frostether.frostchain;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Shared helpers: deterministic keys, signed transactions, valid resonance proofs, and a test miner. */
final class TestKit {

    static final long QOIN = U64.COIN;

    private TestKit() {
    }

    /** A clock the test controls, so blocks can be spaced out without waiting. */
    static final class FakeClock implements Chain.Clock {
        long now;

        FakeClock(long start) {
            this.now = start;
        }

        @Override
        public long nowSec() {
            return now;
        }
    }

    /** Deterministic LMS key: the same seed byte always gives the same key. */
    static Lms.PrivateKey key(int seedByte) {
        byte[] i = new byte[16];
        byte[] seed = new byte[32];
        Arrays.fill(i, (byte) seedByte);
        Arrays.fill(seed, (byte) (seedByte + 1));
        return Lms.generate(i, seed, null);
    }

    static byte[] id(Lms.PrivateKey key) {
        return Address.accountId(key.pub);
    }

    static Tx send(Lms.PrivateKey key, int q, byte[] to, long amount, byte[] chainId) {
        Tx tx = base(key, Tx.SEND, q);
        tx.to = to.clone();
        tx.amount = amount;
        tx.fee = Consensus.MIN_FEE;
        return sign(tx, key, chainId);
    }

    static Tx claimName(Lms.PrivateKey key, int q, String name, byte[] chainId) {
        Tx tx = base(key, Tx.NAME, q);
        tx.name = name;
        tx.fee = Consensus.MIN_FEE;
        return sign(tx, key, chainId);
    }

    private static Tx base(Lms.PrivateKey key, int type, int q) {
        Tx tx = new Tx();
        tx.type = type;
        tx.from = id(key);
        tx.k = 0;
        tx.q = q;
        tx.pub = key.pub;
        return tx;
    }

    private static Tx sign(Tx tx, Lms.PrivateKey key, byte[] chainId) {
        tx.sig = Lms.sign(key, tx.q, tx.message(chainId));
        return tx;
    }

    /** A proof that passes every consensus check for a block at the given time. */
    static Resonance.Proof proof(long blockTimeSec) {
        return proof(blockTimeSec, 7.83);
    }

    /** A valid proof locked on the given tone. */
    static Resonance.Proof proof(long blockTimeSec, double hz) {
        Resonance.Proof p = new Resonance.Proof();
        p.sensor = "mic";
        p.hzMilli = (int) Math.round(hz * 1000);
        p.snrX100 = 2390;
        p.ampMilli = 120;
        p.samples = 800;
        p.rateCenti = 10000;
        p.startMs = (blockTimeSec - 20) * 1000;
        p.durMs = 8000;
        p.digest = new byte[32];
        Arrays.fill(p.digest, (byte) 0x5a);
        p.spectrum = new byte[33];
        Arrays.fill(p.spectrum, (byte) 20);
        p.spectrum[(int) Math.round((hz - Resonance.GRID_MIN) / Resonance.SPECTRUM_STEP_HZ)] = (byte) 255;
        return p;
    }

    /** Mines the next block on a node from its mempool, then submits it like Frostoise does. */
    static Block mine(Node node, FakeClock clock, byte[] miner) throws InterruptedException {
        Block found = search(node.chain, clock, miner, node.mempool.select(200, miner));
        Chain.Result r = node.submitBlock(found, null);
        if (!r.ok) {
            throw new AssertionError("block " + found.height + " rejected: " + r.error);
        }
        return found;
    }

    /** Builds the next block the way Frostoise does, searches for a nonce, and adds it to the chain. */
    static Block mine(Chain chain, FakeClock clock, byte[] miner, List<Tx> txs) throws InterruptedException {
        Block block = search(chain, clock, miner, txs);
        Chain.Result r = chain.addBlock(block);
        if (!r.ok) {
            throw new AssertionError("block " + block.height + " rejected: " + r.error);
        }
        return block;
    }

    private static Block search(Chain chain, FakeClock clock, byte[] miner, List<Tx> txs) throws InterruptedException {
        Block tip = chain.tip();
        Block b = new Block();
        b.height = tip.height + 1;
        b.prev = tip.hash();
        b.time = Math.max(clock.nowSec(), chain.minNextTime());
        b.difficulty = chain.nextDifficulty();
        b.txs.addAll(txs);
        b.root = Block.txRoot(b.txs, chain.chainId);
        b.miner = miner.clone();
        b.reso = proof(b.time);
        b.resoHash = b.reso.hash();

        final byte[] target = Consensus.target(b.difficulty);
        final Object template = b.toJson();
        final int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        final AtomicReference<Block> found = new AtomicReference<>();
        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int start = t;
            workers[t] = new Thread(() -> {
                @SuppressWarnings("unchecked")
                Block mine = Block.fromJson((java.util.Map<String, Object>) template);
                for (long nonce = start; found.get() == null; nonce += threads) {
                    mine.nonce = nonce;
                    mine.invalidateHash();
                    if (Consensus.meetsTarget(mine.hash(), target)) {
                        found.compareAndSet(null, mine);
                    }
                }
            });
            workers[t].start();
        }
        for (Thread w : workers) {
            w.join();
        }
        return found.get();
    }
}
