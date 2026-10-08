package com.frostether.frostchain;

import com.frostether.frostchain.ChainState;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Chain {
    public static final Clock SYSTEM_CLOCK = new Clock() {
        @Override // com.frostether.frostchain.Chain.Clock
        public long nowSec() {
            return System.currentTimeMillis() / 1000;
        }
    };
    public final byte[] chainId;
    private final Clock clock;
    private final File file;
    private ChainState state;
    private final List<Block> blocks = new ArrayList();
    private final Map<String, Integer> index = new LinkedHashMap();
    private BigInteger work = BigInteger.ZERO;
    private final Set<String> verifiedSigs = Collections.synchronizedSet(new HashSet());
    private Map<String, List<Event>> history = new LinkedHashMap();

    public interface Clock {
        long nowSec();
    }

    public static final class Event {
        public long amount;
        public long fee;
        public long height;
        public String kind;
        public long time;
        public String other = "";
        public String txid = "";
        public String memo = "";
        public String name = "";

        public Map<String, Object> toJson() {
            return Json.o("height", Long.valueOf(this.height), "time", Long.valueOf(this.time), "kind", this.kind, "amount", U64.str(this.amount), "fee", U64.str(this.fee), "other", this.other, "txid", this.txid, "memo", this.memo, "name", this.name);
        }
    }

    public static final class Result {
        public final String error;
        public final boolean ok;

        Result(boolean z, String str) {
            this.ok = z;
            this.error = str;
        }

        static Result ok() {
            return new Result(true, null);
        }

        static Result bad(String str) {
            return new Result(false, str);
        }
    }

    public Chain(File file, Clock clock) throws IOException {
        this.clock = clock == null ? SYSTEM_CLOCK : clock;
        this.chainId = Block.genesis().hash();
        if (file != null && !file.isDirectory() && !file.mkdirs()) {
            throw new IOException("can't create " + file);
        }
        this.file = file == null ? null : new File(file, "chain.jsonl");
        resetToGenesis();
        if (this.file == null || !this.file.exists()) {
            return;
        }
        load();
    }

    private void resetToGenesis() {
        this.blocks.clear();
        this.index.clear();
        Block genesis = Block.genesis();
        this.blocks.add(genesis);
        this.index.put(genesis.hashHex(), 0);
        this.state = new ChainState();
        this.state.height = 0L;
        this.work = BigInteger.valueOf(genesis.difficulty);
        this.history = new LinkedHashMap();
    }

    public synchronized long height() {
        return this.blocks.size() - 1;
    }

    public synchronized Block tip() {
        return this.blocks.get(this.blocks.size() - 1);
    }

    public synchronized Block at(long j) {
        if (j < 0 || j >= this.blocks.size()) {
            return null;
        }
        return this.blocks.get((int) j);
    }

    public synchronized BigInteger work() {
        return this.work;
    }

    public synchronized ChainState stateCopy() {
        return this.state.copy();
    }

    public synchronized boolean has(byte[] bArr) {
        return this.index.containsKey(Bytes.hex(bArr));
    }

    public synchronized List<String> hashes(long j, int i) {
        ArrayList arrayList;
        arrayList = new ArrayList();
        long max = Math.max(0L, j);
        while (true) {
            long j2 = max;
            if (j2 >= this.blocks.size() || arrayList.size() >= i) {
                break;
            }
            arrayList.add(this.blocks.get((int) j2).hashHex());
            max = 1 + j2;
        }
        return arrayList;
    }

    public synchronized List<Block> range(long j, int i) {
        ArrayList arrayList;
        arrayList = new ArrayList();
        long max = Math.max(0L, j);
        while (true) {
            long j2 = max;
            if (j2 >= this.blocks.size() || arrayList.size() >= i) {
                break;
            }
            arrayList.add(this.blocks.get((int) j2));
            max = 1 + j2;
        }
        return arrayList;
    }

    public synchronized ChainState.Account account(byte[] bArr) {
        ChainState.Account account;
        account = this.state.account(bArr);
        return account == null ? null : account.copy();
    }

    public synchronized byte[] lookupName(String str) {
        return this.state.lookupName(str);
    }

    public synchronized String nameOf(byte[] bArr) {
        ChainState.Account account;
        account = this.state.account(bArr);
        return account == null ? null : account.name;
    }

    public synchronized List<Event> history(byte[] bArr) {
        List<Event> list;
        list = this.history.get(Bytes.hex(bArr));
        return list == null ? new ArrayList() : new ArrayList(list);
    }

    public synchronized long generated() {
        return this.state.generated;
    }

    public synchronized int accountCount() {
        return this.state.accountCount();
    }

    public synchronized long nextDifficulty() {
        return nextDifficulty(this.blocks, this.blocks.size());
    }

    public synchronized long minNextTime() {
        return Math.max(medianTime(this.blocks, this.blocks.size()) + 1, tip().time + Consensus.MIN_BLOCK_SPACING);
    }

    public long now() {
        return this.clock.nowSec();
    }

    public synchronized Result addBlock(Block block) {
        Result ok;
        if (this.index.containsKey(block.hashHex())) {
            ok = Result.bad("already have this block");
        } else {
            if (Bytes.equal(block.prev, tip().hash())) {
                ChainState copy = this.state.copy();
                String validate = validate(block, this.blocks, this.blocks.size(), copy, true);
                if (validate != null) {
                    ok = Result.bad(validate);
                } else {
                    commitAppend(block, copy);
                    persistAppend(block);
                    ok = Result.ok();
                }
            } else {
                ok = Result.bad("does not extend the tip");
            }
        }
        return ok;
    }

    /**
     * Switches to an alternative chain that forks after height j, if it has more total work and every
     * block validates. Returns the transactions from abandoned blocks that the new chain doesn't include,
     * so they can go back to the mempool, or null with the reason in sb.
     * Rebuilt from the 0.3.0 bytecode; the decompiled version never left its loops.
     */
    public synchronized List<Tx> tryReorg(long j, List<Block> list, StringBuilder sb) {
        if (j < 0 || j >= this.blocks.size() || list.isEmpty()) {
            sb.append("bad fork point");
            return null;
        }
        if (!Bytes.equal(list.get(0).prev, this.blocks.get((int) j).hash())) {
            sb.append("alt chain doesn't connect");
            return null;
        }
        BigInteger altWork = BigInteger.ZERO;
        for (Block b : list) {
            altWork = altWork.add(new BigInteger(Long.toUnsignedString(b.difficulty)));
        }
        BigInteger ourWork = BigInteger.ZERO;
        for (int i = ((int) j) + 1; i < this.blocks.size(); i++) {
            ourWork = ourWork.add(new BigInteger(Long.toUnsignedString(this.blocks.get(i).difficulty)));
        }
        if (altWork.compareTo(ourWork) <= 0) {
            sb.append("alternative chain has no more work");
            return null;
        }
        ChainState replayed = replay(j);
        ArrayList<Block> candidate = new ArrayList<>(this.blocks.subList(0, ((int) j) + 1));
        for (Block next : list) {
            String error = validate(next, candidate, candidate.size(), replayed, true);
            if (error != null) {
                sb.append("block ").append(next.height).append(": ").append(error);
                return null;
            }
            candidate.add(next);
        }
        ArrayList<Tx> abandoned = new ArrayList<>();
        for (int i = ((int) j) + 1; i < this.blocks.size(); i++) {
            abandoned.addAll(this.blocks.get(i).txs);
        }
        this.blocks.clear();
        this.blocks.addAll(candidate);
        this.index.clear();
        for (int i = 0; i < this.blocks.size(); i++) {
            this.index.put(this.blocks.get(i).hashHex(), Integer.valueOf(i));
        }
        this.state = replayed;
        this.work = BigInteger.ZERO;
        for (Block b : this.blocks) {
            this.work = this.work.add(new BigInteger(Long.toUnsignedString(b.difficulty)));
        }
        rebuildHistory();
        persistAll();
        HashSet<String> included = new HashSet<>();
        for (Block b : list) {
            for (Tx tx : b.txs) {
                included.add(Bytes.hex(tx.id(this.chainId)));
            }
        }
        ArrayList<Tx> back = new ArrayList<>();
        for (Tx tx : abandoned) {
            if (!included.contains(Bytes.hex(tx.id(this.chainId)))) {
                back.add(tx);
            }
        }
        return back;
    }

    public String checkTx(Tx tx) {
        String hex = Bytes.hex(tx.wid(this.chainId));
        if (this.verifiedSigs.contains(hex)) {
            return null;
        }
        String checkStateless = tx.checkStateless(this.chainId);
        if (checkStateless == null) {
            if (this.verifiedSigs.size() > 20000) {
                this.verifiedSigs.clear();
            }
            this.verifiedSigs.add(hex);
            return checkStateless;
        }
        return checkStateless;
    }

    private String validate(Block block, List<Block> list, int i, ChainState chainState, boolean z) {
        String checkTx;
        Block block2 = list.get(i - 1);
        if (block.version != 1) {
            return "unknown block version";
        }
        if (block.height != i) {
            return "wrong height";
        }
        if (!Bytes.equal(block.prev, block2.hash())) {
            return "wrong previous block";
        }
        if (block.time <= medianTime(list, i)) {
            return "timestamp too early";
        }
        if (block.time > this.clock.nowSec() + Consensus.FUTURE_TIME_LIMIT) {
            return "timestamp too far in the future";
        }
        if (block.time < block2.time + Consensus.MIN_BLOCK_SPACING) {
            return "too soon after the last block";
        }
        if (block.difficulty != nextDifficulty(list, i)) {
            return "wrong difficulty";
        }
        if (!Consensus.meetsTarget(block.hash(), Consensus.target(block.difficulty))) {
            return "not enough proof of work";
        }
        if (block.reso == null) {
            return "missing resonance proof";
        }
        String check = block.reso.check(block.time);
        if (check == null) {
            if (!Bytes.equal(block.resoHash, block.reso.hash())) {
                return "resonance proof doesn't match header";
            }
            if (block.txs.size() > 200) {
                return "too many transactions";
            }
            if (!Bytes.equal(block.root, Block.txRoot(block.txs, this.chainId))) {
                return "transaction root mismatch";
            }
            if (Bytes.isZero(block.miner)) {
                return "no miner address";
            }
            chainState.beginBlock(block.height, block.miner);
            long j = 0;
            HashSet hashSet = new HashSet();
            int i2 = 0;
            for (Tx tx : block.txs) {
                if (!hashSet.add(Bytes.hex(tx.id(this.chainId)))) {
                    return "duplicate transaction";
                }
                if (tx.type == 2 && (i2 = i2 + 1) > 20) {
                    return "too many name registrations";
                }
                if (z && (checkTx = checkTx(tx)) != null) {
                    return "transaction: " + checkTx;
                }
                String apply = chainState.apply(tx);
                if (apply != null) {
                    return "transaction: " + apply;
                }
                j = U64.add(j, tx.fee);
            }
            long reward = Consensus.reward(block.height, chainState.generated);
            chainState.generated = U64.add(chainState.generated, reward);
            block.paid = U64.add(reward, j);
            chainState.credit(block.miner, block.paid, block.height + 12);
            chainState.height = block.height;
            return null;
        }
        return check;
    }

    private static long medianTime(List<Block> list, int i) {
        int min = Math.min(11, i);
        long[] jArr = new long[min];
        for (int i2 = 0; i2 < min; i2++) {
            jArr[i2] = list.get((i - 1) - i2).time;
        }
        Arrays.sort(jArr);
        return jArr[min / 2];
    }

    private static long nextDifficulty(List<Block> list, int i) {
        int max = Math.max(1, (i - 1) - 60);
        ArrayList arrayList = new ArrayList();
        ArrayList arrayList2 = new ArrayList();
        for (int i2 = max; i2 < i; i2++) {
            arrayList.add(Long.valueOf(list.get(i2).time));
            arrayList2.add(Long.valueOf(list.get(i2).difficulty));
        }
        return Consensus.nextDifficulty(arrayList, arrayList2);
    }

    private ChainState replay(long j) {
        ChainState chainState = new ChainState();
        chainState.height = 0L;
        int i = 1;
        while (true) {
            int i2 = i;
            if (i2 <= j) {
                Block block = this.blocks.get(i2);
                chainState.beginBlock(block.height, block.miner);
                long j2 = 0;
                for (Tx tx : block.txs) {
                    String apply = chainState.apply(tx);
                    if (apply != null) {
                        throw new IllegalStateException("stored block " + i2 + " no longer applies: " + apply);
                    }
                    j2 = U64.add(j2, tx.fee);
                }
                long reward = Consensus.reward(block.height, chainState.generated);
                chainState.generated = U64.add(chainState.generated, reward);
                block.paid = U64.add(reward, j2);
                chainState.credit(block.miner, block.paid, block.height + 12);
                chainState.height = i2;
                i = i2 + 1;
            } else {
                return chainState;
            }
        }
    }

    private void commitAppend(Block block, ChainState chainState) {
        this.blocks.add(block);
        this.index.put(block.hashHex(), Integer.valueOf(this.blocks.size() - 1));
        this.state = chainState;
        this.work = this.work.add(new BigInteger(Long.toUnsignedString(block.difficulty)));
        addHistory(block);
    }

    private void rebuildHistory() {
        this.history = new LinkedHashMap();
        int i = 1;
        while (true) {
            int i2 = i;
            if (i2 >= this.blocks.size()) {
                return;
            }
            addHistory(this.blocks.get(i2));
            i = i2 + 1;
        }
    }

    private void addHistory(Block block) {
        Event event = new Event();
        event.height = block.height;
        event.time = block.time;
        event.kind = "mined";
        event.amount = block.paid;
        event.txid = block.hashHex();
        push(block.miner, event);
        for (Tx tx : block.txs) {
            String hex = Bytes.hex(tx.id(this.chainId));
            Event event3 = new Event();
            event3.height = block.height;
            event3.time = block.time;
            event3.fee = tx.fee;
            event3.txid = hex;
            event3.memo = tx.memo;
            if (tx.type == 1) {
                event3.kind = "sent";
                event3.amount = tx.amount;
                event3.other = Bytes.hex(tx.to);
                push(tx.from, event3);
                Event event4 = new Event();
                event4.height = block.height;
                event4.time = block.time;
                event4.kind = "received";
                event4.amount = tx.amount;
                event4.other = Bytes.hex(tx.from);
                event4.txid = hex;
                event4.memo = tx.memo;
                push(tx.to, event4);
            } else if (tx.type == 2) {
                event3.kind = "name";
                event3.name = tx.name;
                push(tx.from, event3);
            } else {
                event3.kind = "rekey";
                push(tx.from, event3);
            }
        }
    }

    private void push(byte[] bArr, Event event) {
        String hex = Bytes.hex(bArr);
        List<Event> list = this.history.get(hex);
        if (list == null) {
            list = new ArrayList<>();
            this.history.put(hex, list);
        }
        list.add(event);
    }

    /**
     * Replays chain.jsonl. Loading stops at the first line that doesn't parse or validate,
     * and the file is rewritten without it. Rebuilt from the 0.3.0 bytecode.
     */
    private void load() throws IOException {
        int lines = 0;
        int loaded = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(this.file), Bytes.UTF8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                lines++;
                Block block;
                try {
                    block = Block.fromJson(Json.obj(line));
                } catch (RuntimeException e) {
                    break;
                }
                if (block.height == 0) {
                    if (!Bytes.equal(block.hash(), this.chainId)) {
                        break;
                    }
                    loaded++;
                    continue;
                }
                ChainState next = this.state.copy();
                if (validateStored(block, next) != null) {
                    break;
                }
                commitAppend(block, next);
                loaded++;
            }
        }
        if (loaded != lines) {
            persistAll();
        }
    }

    private String validateStored(Block block, ChainState chainState) {
        try {
            if (block.version != 1 || block.height != this.blocks.size()) {
                return "bad stored block";
            }
            if (!Bytes.equal(block.prev, tip().hash())) {
                return "bad link";
            }
            if (block.time < tip().time + Consensus.MIN_BLOCK_SPACING) {
                return "bad spacing";
            }
            if (block.difficulty != nextDifficulty(this.blocks, this.blocks.size())) {
                return "bad difficulty";
            }
            if (!Consensus.meetsTarget(block.hash(), Consensus.target(block.difficulty))) {
                return "bad pow";
            }
            if (block.reso == null || block.reso.check(block.time) != null || !Bytes.equal(block.resoHash, block.reso.hash())) {
                return "bad reso";
            }
            if (!Bytes.equal(block.root, Block.txRoot(block.txs, this.chainId))) {
                return "bad root";
            }
            chainState.beginBlock(block.height, block.miner);
            long j = 0;
            for (Tx tx : block.txs) {
                String apply = chainState.apply(tx);
                if (apply != null) {
                    return apply;
                }
                j = U64.add(j, tx.fee);
            }
            long reward = Consensus.reward(block.height, chainState.generated);
            chainState.generated = U64.add(chainState.generated, reward);
            block.paid = U64.add(reward, j);
            chainState.credit(block.miner, block.paid, block.height + 12);
            chainState.height = block.height;
            return null;
        } catch (RuntimeException e) {
            return e.toString();
        }
    }

    private void persistAppend(Block block) {
        if (this.file != null) {
            try {
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(this.file, true), Bytes.UTF8);
                try {
                    if (this.file.length() == 0) {
                        outputStreamWriter.write(Json.write(this.blocks.get(0).toJson()) + "\n");
                    }
                    outputStreamWriter.write(Json.write(block.toJson()) + "\n");
                } finally {
                    outputStreamWriter.close();
                }
            } catch (IOException e) {
                Log.w("chain", "couldn't save block " + block.height + ": " + e);
            }
        }
    }

    private void persistAll() {
        if (this.file != null) {
            File file = new File(this.file.getPath() + ".tmp");
            try {
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(file), Bytes.UTF8);
                try {
                    Iterator<Block> it = this.blocks.iterator();
                    while (it.hasNext()) {
                        outputStreamWriter.write(Json.write(it.next().toJson()) + "\n");
                    }
                    outputStreamWriter.close();
                    if (file.renameTo(this.file)) {
                        return;
                    }
                    if (!this.file.delete() || !file.renameTo(this.file)) {
                        throw new IOException("rename failed");
                    }
                } catch (Throwable th) {
                    outputStreamWriter.close();
                    throw th;
                }
            } catch (IOException e) {
                Log.w("chain", "couldn't save chain: " + e);
            }
        }
    }
}
