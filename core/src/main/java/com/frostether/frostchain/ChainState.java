package com.frostether.frostchain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ChainState {
    public long generated;
    final Map<String, Account> accounts = new HashMap();
    final Map<String, String> names = new HashMap();
    final TreeMap<Long, List<Credit>> locked = new TreeMap<>();
    public long height = -1;

    /** A new state, at genesis: the founder's names are already registered (v0.4). */
    public ChainState() {
        byte[] founder = Consensus.founderAccount();
        String hex = Bytes.hex(founder);
        getOrCreate(founder).name = Consensus.FOUNDER_NAME;
        this.names.put(Consensus.FOUNDER_NAME, hex);
        for (String alias : Consensus.FOUNDER_ALIASES) {
            this.names.put(alias, hex);
        }
    }

    public static final class Account {
        public long balance;
        public final byte[] id;
        public long immature;
        public int keyIndex;
        public String name;
        public byte[] pub;
        public int q;

        Account(byte[] bArr) {
            this.id = bArr;
        }

        Account copy() {
            Account account = new Account(this.id);
            account.balance = this.balance;
            account.immature = this.immature;
            account.keyIndex = this.keyIndex;
            account.q = this.q;
            account.pub = this.pub;
            account.name = this.name;
            return account;
        }
    }

    static final class Credit {
        final String acct;
        final long amount;

        Credit(String str, long j) {
            this.acct = str;
            this.amount = j;
        }
    }

    public ChainState copy() {
        ChainState chainState = new ChainState();
        for (Map.Entry<String, Account> entry : this.accounts.entrySet()) {
            chainState.accounts.put(entry.getKey(), entry.getValue().copy());
        }
        chainState.names.putAll(this.names);
        for (Map.Entry<Long, List<Credit>> entry2 : this.locked.entrySet()) {
            chainState.locked.put(entry2.getKey(), new ArrayList(entry2.getValue()));
        }
        chainState.generated = this.generated;
        chainState.height = this.height;
        return chainState;
    }

    public Account account(byte[] bArr) {
        return this.accounts.get(Bytes.hex(bArr));
    }

    public Account accountOrEmpty(byte[] bArr) {
        Account account = account(bArr);
        return account != null ? account : new Account(bArr);
    }

    public byte[] lookupName(String str) {
        String str2 = this.names.get(str);
        if (str2 == null) {
            return null;
        }
        return Bytes.unhex(str2);
    }

    public int accountCount() {
        return this.accounts.size();
    }

    public int nameCount() {
        return this.names.size();
    }

    private Account getOrCreate(byte[] bArr) {
        String hex = Bytes.hex(bArr);
        Account account = this.accounts.get(hex);
        if (account != null) {
            return account;
        }
        Account account2 = new Account((byte[]) bArr.clone());
        this.accounts.put(hex, account2);
        return account2;
    }

    /**
     * Applies one transaction. Returns null when it applies, or the reason it was rejected.
     * Rebuilt by hand from the 0.3.0 bytecode: the decompiler could not restructure this method.
     */
    public String apply(Tx tx) {
        Account account = account(tx.from);
        if (account == null || account.pub == null) {
            if (tx.k != 0) {
                return "first transaction must use key 0";
            }
            if (!Bytes.equal(Address.accountId(tx.pub), tx.from)) {
                return "public key doesn't belong to this account";
            }
        } else {
            if (tx.k != account.keyIndex) {
                return "wrong key index (expected " + account.keyIndex + ")";
            }
            if (!Bytes.equal(tx.pub, account.pub)) {
                return "wrong public key for this account";
            }
        }
        int nextQ = account == null ? 0 : account.q;
        if (tx.q < nextQ) {
            return "one-time key " + tx.q + " already used (next is " + nextQ + ")";
        }
        long balance = account == null ? 0L : account.balance;
        long spend;
        try {
            spend = tx.spend();
        } catch (ArithmeticException e) {
            return "amount overflow";
        }
        if (U64.cmp(spend, balance) > 0) {
            return "insufficient funds";
        }
        if (tx.type == Tx.NAME) {
            if (account != null && account.name != null) {
                return "this account already has a name";
            }
            if (this.names.containsKey(tx.name)) {
                return "name already taken";
            }
        }
        if (tx.type == Tx.SEND) {
            Account recipient = account(tx.to);
            try {
                U64.add(recipient != null ? recipient.balance : 0L, tx.amount);
            } catch (ArithmeticException e) {
                return "recipient balance overflow";
            }
        }
        Account from = getOrCreate(tx.from);
        if (from.pub == null) {
            from.pub = tx.pub;
        }
        from.balance = U64.sub(from.balance, spend);
        from.q = tx.q + 1;
        switch (tx.type) {
            case Tx.SEND:
                Account to = getOrCreate(tx.to);
                to.balance = U64.add(to.balance, tx.amount);
                break;
            case Tx.NAME:
                from.name = tx.name;
                this.names.put(tx.name, Bytes.hex(tx.from));
                break;
            case Tx.REKEY:
                from.pub = tx.newPub;
                from.keyIndex++;
                from.q = 0;
                break;
            default:
                break;
        }
        return null;
    }

    public void beginBlock(long j, byte[] bArr) {
        unlock(j);
    }

    void unlock(long j) {
        while (!this.locked.isEmpty() && this.locked.firstKey().longValue() <= j) {
            for (Credit credit : this.locked.pollFirstEntry().getValue()) {
                Account account = this.accounts.get(credit.acct);
                account.immature = U64.sub(account.immature, credit.amount);
                account.balance = U64.add(account.balance, credit.amount);
            }
        }
    }

    void credit(byte[] bArr, long j, long j2) {
        if (j != 0) {
            Account orCreate = getOrCreate(bArr);
            orCreate.immature = U64.add(orCreate.immature, j);
            List<Credit> list = this.locked.get(Long.valueOf(j2));
            if (list == null) {
                list = new ArrayList<>();
                this.locked.put(Long.valueOf(j2), list);
            }
            list.add(new Credit(Bytes.hex(bArr), j));
        }
    }
}
