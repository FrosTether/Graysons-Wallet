package com.frostether.frostchain;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Mempool {
    public static final int MAX = 2000;
    private final Chain chain;
    private final LinkedHashMap<String, Tx> txs = new LinkedHashMap<>();

    public Mempool(Chain chain) {
        this.chain = chain;
    }

    public synchronized int size() {
        return this.txs.size();
    }

    public synchronized boolean has(byte[] bArr) {
        return this.txs.containsKey(Bytes.hex(bArr));
    }

    public synchronized List<Tx> all() {
        return new ArrayList(this.txs.values());
    }

    public String add(Tx tx) {
        String checkTx = this.chain.checkTx(tx);
        if (checkTx == null) {
            ChainState stateCopy = this.chain.stateCopy();
            synchronized (this) {
                String hex = Bytes.hex(tx.id(this.chain.chainId));
                if (this.txs.containsKey(hex)) {
                    return null;
                }
                if (this.txs.size() >= 2000) {
                    return "transaction pool is full";
                }
                for (Tx tx2 : this.txs.values()) {
                    if (tx2.type == 2 && tx.type == 2 && tx2.name.equals(tx.name)) {
                        return "name already being registered";
                    }
                    stateCopy.apply(tx2);
                }
                String apply = stateCopy.apply(tx);
                if (apply == null) {
                    this.txs.put(hex, tx);
                    return null;
                }
                return apply;
            }
        }
        return checkTx;
    }

    public List<Tx> select(int i, byte[] bArr) {
        int i2;
        ChainState stateCopy = this.chain.stateCopy();
        stateCopy.beginBlock(stateCopy.height + 1, bArr);
        ArrayList arrayList = new ArrayList();
        int i3 = 0;
        synchronized (this) {
            for (Tx tx : this.txs.values()) {
                if (arrayList.size() >= i) {
                    break;
                }
                if (tx.type != 2 || i3 < 20) {
                    if (stateCopy.apply(tx) == null) {
                        arrayList.add(tx);
                        if (tx.type == 2) {
                            i2 = i3 + 1;
                            i3 = i2;
                        }
                    }
                    i2 = i3;
                    i3 = i2;
                }
            }
        }
        return arrayList;
    }

    public void revalidate(List<Tx> list) {
        ChainState stateCopy = this.chain.stateCopy();
        synchronized (this) {
            LinkedHashMap linkedHashMap = new LinkedHashMap();
            ArrayList<Tx> arrayList = new ArrayList();
            if (list != null) {
                arrayList.addAll(list);
            }
            arrayList.addAll(this.txs.values());
            for (Tx tx : arrayList) {
                String hex = Bytes.hex(tx.id(this.chain.chainId));
                if (!linkedHashMap.containsKey(hex) && stateCopy.apply(tx) == null) {
                    linkedHashMap.put(hex, tx);
                }
            }
            this.txs.clear();
            this.txs.putAll(linkedHashMap);
        }
    }

    public synchronized long[] pendingFor(byte[] bArr) {
        long j;
        long j2;
        long j3;
        long j4;
        j = 0;
        j2 = 0;
        j3 = 0;
        for (Tx tx : this.txs.values()) {
            if (Bytes.equal(tx.from, bArr)) {
                j3 = U64.add(j3, tx.spend());
                j++;
            }
            if (tx.type == 1 && Bytes.equal(tx.to, bArr)) {
                j2 = U64.add(j2, tx.amount);
                j4 = j + 1;
            } else {
                j4 = j;
            }
            j = j4;
        }
        return new long[]{j3, j2, j};
    }

    public synchronized List<Tx> pendingTxs(byte[] bArr) {
        ArrayList arrayList;
        arrayList = new ArrayList();
        for (Tx tx : this.txs.values()) {
            if (Bytes.equal(tx.from, bArr) || (tx.type == 1 && Bytes.equal(tx.to, bArr))) {
                arrayList.add(tx);
            }
        }
        return arrayList;
    }

    public synchronized boolean nameTaken(String str) {
        boolean z;
        Iterator<Tx> it = this.txs.values().iterator();
        while (true) {
            if (!it.hasNext()) {
                z = false;
                break;
            }
            Tx next = it.next();
            if (next.type == 2 && next.name.equals(str)) {
                z = true;
                break;
            }
        }
        return z;
    }

    public synchronized void clear() {
        this.txs.clear();
    }

    synchronized void removeIf(byte[] bArr) {
        Iterator<Map.Entry<String, Tx>> it = this.txs.entrySet().iterator();
        String hex = Bytes.hex(bArr);
        while (it.hasNext()) {
            if (it.next().getKey().equals(hex)) {
                it.remove();
            }
        }
    }
}
