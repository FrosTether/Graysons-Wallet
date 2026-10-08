package com.frostether.frostchain;

import com.frostether.frostchain.Chain;
import com.frostether.frostchain.ChainState;
import com.frostether.frostchain.Lms;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class Wallets {
    public static final int PBKDF2_ITERATIONS = 100000;
    public static final int REKEY_AT = 1000;
    public static final int RESTORE_MARGIN = 64;
    private final File dir;
    private final Node node;
    private Wallet open;

    public static final class Plan {
        public long amount;
        public long balance;
        public long fee;
        public int k;
        public int q;
        public boolean rekeyFirst;
        public byte[] to;
        public long total;
    }

    public static final class Wallet {
        public byte[] account;
        public final File file;
        Map<String, Object> json;
        public String label;
        byte[] seed;
        public boolean watch;
        final Map<Integer, Integer> nextQ = new HashMap();
        final Map<Integer, Lms.PrivateKey> keys = new HashMap();
        volatile String keygen = "";

        Wallet(File file) {
            this.file = file;
        }
    }

    public Wallets(File file, Node node) {
        this.dir = file;
        this.node = node;
    }

    /**
     * Signing keys are derived per chain. 0.3.x derived them from the seed under "frostchain/lms/", so the same
     * 25 words on the v0.4 chain would sign with one-time keys the old chain already used. Two signatures from one
     * LM-OTS key let anyone who sees both forge more, so v0.4 derives its keys under its own tag: old words give
     * a fresh account here. A future chain that keeps the same seeds must change this tag again.
     */
    static final String KEY_TAG = "frostchain/v0.4/lms/";

    static byte[] lmsI(byte[] bArr, int i) {
        return Bytes.slice(Sha256.hash(Bytes.utf8(KEY_TAG + "I"), bArr, Bytes.u32(i)), 0, 16);
    }

    static byte[] lmsSeed(byte[] bArr, int i) {
        return Sha256.hash(Bytes.utf8(KEY_TAG + "seed"), bArr, Bytes.u32(i));
    }

    public static byte[] accountFor(byte[] bArr) {
        return Address.accountId(Lms.generate(lmsI(bArr, 0), lmsSeed(bArr, 0), null).pub);
    }

    public Lms.PrivateKey key(Wallet wallet, int i) {
        Lms.PrivateKey privateKey;
        synchronized (wallet.keys) {
            privateKey = wallet.keys.get(Integer.valueOf(i));
            if (privateKey == null) {
                wallet.keygen = "preparing key " + i;
                privateKey = Lms.generate(lmsI(wallet.seed, i), lmsSeed(wallet.seed, i), null);
                wallet.keys.put(Integer.valueOf(i), privateKey);
                wallet.keygen = "";
            }
        }
        return privateKey;
    }

    public synchronized List<Map<String, Object>> list() {
        ArrayList arrayList;
        arrayList = new ArrayList();
        File[] listFiles = this.dir.listFiles();
        if (listFiles != null) {
            Arrays.sort(listFiles);
            for (File file : listFiles) {
                if (file.getName().endsWith(".json")) {
                    try {
                        Map<String, Object> obj = Json.obj(Node.readFile(file));
                        byte[] unhex = Bytes.unhex(Json.str(obj, "account"), 20);
                        String nameOf = this.node.chain.nameOf(unhex);
                        Object[] objArr = new Object[12];
                        objArr[0] = "file";
                        objArr[1] = file.getName();
                        objArr[2] = "label";
                        objArr[3] = Json.str(obj, "label", file.getName());
                        objArr[4] = "watch";
                        objArr[5] = Boolean.valueOf(Json.bool(obj, "watch", false));
                        objArr[6] = "address";
                        objArr[7] = Address.raw(unhex);
                        objArr[8] = "name";
                        objArr[9] = nameOf == null ? "" : Address.display(nameOf);
                        objArr[10] = "open";
                        objArr[11] = Boolean.valueOf(this.open != null && this.open.file.equals(file));
                        arrayList.add(Json.o(objArr));
                    } catch (Exception e) {
                        Log.w("wallet", file.getName() + ": " + e);
                    }
                }
            }
        }
        return arrayList;
    }

    public synchronized Map<String, Object> create(String str, String str2) throws Exception {
        return createFrom(str, str2, Bytes.random(32), true);
    }

    public synchronized Map<String, Object> restore(String str, String str2, String str3) throws Exception {
        return createFrom(str, str2, Mnemonic.decode(str3), false);
    }

    private Map<String, Object> createFrom(String str, String str2, byte[] bArr, boolean z) throws Exception {
        checkPassword(str2);
        String cleanLabel = cleanLabel(str);
        Lms.PrivateKey generate = Lms.generate(lmsI(bArr, 0), lmsSeed(bArr, 0), null);
        byte[] accountId = Address.accountId(generate.pub);
        for (Map<String, Object> map : list()) {
            if (!Json.bool(map, "watch", false) && Address.raw(accountId).equals(Json.str(map, "address"))) {
                throw new IllegalArgumentException("this seed is already in wallet \"" + Json.str(map, "label") + "\"");
            }
        }
        if (!this.dir.isDirectory() && !this.dir.mkdirs()) {
            throw new IOException("can't create wallet folder");
        }
        File uniqueFile = uniqueFile(cleanLabel);
        byte[] random = Bytes.random(16);
        byte[] random2 = Bytes.random(12);
        byte[] aes = aes(1, str2, random, random2, accountId, bArr);
        Object[] objArr = new Object[16];
        objArr[0] = "v";
        objArr[1] = 1L;
        objArr[2] = "label";
        objArr[3] = cleanLabel;
        objArr[4] = "created";
        objArr[5] = Long.valueOf(System.currentTimeMillis() / 1000);
        objArr[6] = "watch";
        objArr[7] = false;
        objArr[8] = "account";
        objArr[9] = Bytes.hex(accountId);
        objArr[10] = "pub0";
        objArr[11] = Bytes.hex(generate.pub);
        objArr[12] = "enc";
        objArr[13] = Json.o("kdf", "pbkdf2-hmac-sha256", "iter", 100000L, "salt", Bytes.hex(random), "iv", Bytes.hex(random2), "ct", Bytes.hex(aes));
        objArr[14] = "nextq";
        objArr[15] = z ? Json.o("0", 0L) : new LinkedHashMap();
        Map<String, Object> o = Json.o(objArr);
        writeStrict(uniqueFile, Json.write(o));
        Wallet wallet = new Wallet(uniqueFile);
        wallet.label = cleanLabel;
        wallet.account = accountId;
        wallet.seed = bArr;
        wallet.json = o;
        wallet.keys.put(0, generate);
        if (z) {
            wallet.nextQ.put(0, 0);
        }
        closeLocked();
        this.open = wallet;
        Map<String, Object> info = info();
        if (z) {
            info.put("seed", Mnemonic.encode(bArr));
        }
        return info;
    }

    public synchronized Map<String, Object> watch(String str, String str2) throws Exception {
        byte[] resolve = this.node.resolve(str2);
        if (!this.dir.isDirectory() && !this.dir.mkdirs()) {
            throw new IOException("can't create wallet folder");
        }
        if (str.trim().isEmpty()) {
            str = Address.strip(str2);
        }
        String cleanLabel = cleanLabel(str);
        File uniqueFile = uniqueFile(cleanLabel);
        Map<String, Object> o = Json.o("v", 1L, "label", cleanLabel, "created", Long.valueOf(System.currentTimeMillis() / 1000), "watch", true, "account", Bytes.hex(resolve));
        writeStrict(uniqueFile, Json.write(o));
        Wallet wallet = new Wallet(uniqueFile);
        wallet.label = cleanLabel;
        wallet.account = resolve;
        wallet.watch = true;
        wallet.json = o;
        closeLocked();
        this.open = wallet;
        return info();
    }

    public synchronized Map<String, Object> open(String str, String str2) throws Exception {
        File fileFor = fileFor(str);
        Map<String, Object> obj = Json.obj(Node.readFile(fileFor));
        Wallet wallet = new Wallet(fileFor);
        wallet.json = obj;
        wallet.label = Json.str(obj, "label", fileFor.getName());
        wallet.account = Bytes.unhex(Json.str(obj, "account"), 20);
        wallet.watch = Json.bool(obj, "watch", false);
        if (!wallet.watch) {
            Map<String, Object> map = Json.map(obj, "enc");
            try {
                wallet.seed = aes(2, str2 == null ? "" : str2, Bytes.unhex(Json.str(map, "salt")), Bytes.unhex(Json.str(map, "iv")), wallet.account, Bytes.unhex(Json.str(map, "ct")), (int) Json.num(map, "iter"));
                Object obj2 = obj.get("nextq");
                if (obj2 instanceof Map) {
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj2).entrySet()) {
                        wallet.nextQ.put(Integer.valueOf(Integer.parseInt((String) entry.getKey())), Integer.valueOf(((Number) entry.getValue()).intValue()));
                    }
                }
            } catch (AEADBadTagException e) {
                throw new IllegalArgumentException("wrong password");
            }
        }
        closeLocked();
        this.open = wallet;
        if (!wallet.watch) {
            prepareKeysInBackground(wallet);
        }
        return info();
    }

    public synchronized void close() {
        closeLocked();
    }

    public synchronized void closeAll() {
        closeLocked();
    }

    private void closeLocked() {
        if (this.open != null && this.open.seed != null) {
            Arrays.fill(this.open.seed, (byte) 0);
        }
        this.open = null;
    }

    public synchronized boolean isOpen() {
        return this.open != null;
    }

    public synchronized byte[] openAccount() {
        return this.open == null ? null : (byte[]) this.open.account.clone();
    }

    public synchronized boolean isWatchOnly() {
        return this.open != null && this.open.watch;
    }

    public synchronized String seedWords(String str) throws Exception {
        Wallet requireOpen;
        requireOpen = requireOpen();
        if (requireOpen.watch) {
            throw new IllegalArgumentException("a watch-only wallet has no seed");
        }
        Map<String, Object> map = Json.map(requireOpen.json, "enc");
        try {
            aes(2, str, Bytes.unhex(Json.str(map, "salt")), Bytes.unhex(Json.str(map, "iv")), requireOpen.account, Bytes.unhex(Json.str(map, "ct")), (int) Json.num(map, "iter"));
        } catch (AEADBadTagException e) {
            throw new IllegalArgumentException("wrong password");
        }
        return Mnemonic.encode(requireOpen.seed);
    }

    public synchronized void delete(String str) throws IOException {
        File fileFor = fileFor(str);
        if (this.open != null && this.open.file.equals(fileFor)) {
            closeLocked();
        }
        if (!fileFor.delete()) {
            throw new IOException("couldn't delete " + str);
        }
    }

    public synchronized Map<String, Object> info() {
        Map<String, Object> accountJson;
        int nextLeaf;
        synchronized (this) {
            Wallet wallet = this.open;
            if (wallet == null) {
                accountJson = Json.o("open", false);
            } else {
                accountJson = this.node.accountJson(wallet.account);
                accountJson.put("open", true);
                accountJson.put("label", wallet.label);
                accountJson.put("file", wallet.file.getName());
                accountJson.put("watch", Boolean.valueOf(wallet.watch));
                accountJson.put("keygen", wallet.keygen);
                ChainState.Account pendingAccount = this.node.pendingAccount(wallet.account);
                int i = pendingAccount == null ? 0 : pendingAccount.keyIndex;
                if (wallet.watch) {
                    nextLeaf = pendingAccount == null ? 0 : pendingAccount.q;
                } else {
                    nextLeaf = nextLeaf(wallet, pendingAccount, i);
                }
                accountJson.put("keyIndex", Long.valueOf(i));
                accountJson.put("leavesUsed", Long.valueOf(nextLeaf));
                boolean z = (pendingAccount == null || pendingAccount.name == null) ? false : true;
                accountJson.put("pendingName", (z && Json.str(accountJson, "name", "").isEmpty()) ? Address.display(pendingAccount.name) : "");
                accountJson.put("canRegister", Boolean.valueOf((wallet.watch || z) ? false : true));
            }
        }
        return accountJson;
    }

    public synchronized List<Map<String, Object>> history() {
        ArrayList<Map> arrayList;
        Wallet requireOpen = requireOpen();
        arrayList = new ArrayList();
        for (Tx tx : this.node.mempool.pendingTxs(requireOpen.account)) {
            Map<String, Object> o = Json.o("height", -1L, "time", Long.valueOf(System.currentTimeMillis() / 1000), "pending", true, "txid", Bytes.hex(tx.id(this.node.chain.chainId)), "fee", U64.str(tx.fee), "memo", tx.memo);
            boolean equal = Bytes.equal(tx.from, requireOpen.account);
            if (tx.type == 1) {
                o.put("kind", equal ? "sent" : "received");
                o.put("amount", U64.str(tx.amount));
                o.put("other", Bytes.hex(equal ? tx.to : tx.from));
            } else {
                o.put("kind", tx.type == 2 ? "name" : "rekey");
                o.put("amount", "0");
                o.put("name", tx.name);
                o.put("other", "");
            }
            arrayList.add(o);
        }
        List<Chain.Event> history = this.node.chain.history(requireOpen.account);
        long height = this.node.chain.height();
        for (int size = history.size() - 1; size >= 0 && arrayList.size() < 500; size--) {
            Map<String, Object> json = history.get(size).toJson();
            json.put("confirmations", Long.valueOf((height - history.get(size).height) + 1));
            if (history.get(size).kind.equals("mined")) {
                json.put("unlocksAt", Long.valueOf(history.get(size).height + 12));
            }
            arrayList.add(json);
        }
        for (Map map : arrayList) {
            map.put("amountText", U64.format(U64.parse(Json.str(map, "amount"))));
            String str = Json.str(map, "other", "");
            if (str.length() == 40) {
                byte[] unhex = Bytes.unhex(str);
                String nameOf = this.node.chain.nameOf(unhex);
                map.put("otherText", nameOf != null ? Address.display(nameOf) : Address.raw(unhex));
            } else {
                map.put("otherText", "");
            }
        }
        return (List) arrayList;
    }

    public synchronized Map<String, Object> send(String str, String str2, String str3, boolean z) throws Exception {
        int i;
        Map<String, Object> map;
        Wallet requireSpendable = requireSpendable();
        byte[] resolve = this.node.resolve(str);
        if (Bytes.equal(resolve, requireSpendable.account)) {
            throw new IllegalArgumentException("that's this wallet's own address");
        }
        long parseCoins = U64.parseCoins(str2);
        if (parseCoins == 0) {
            throw new IllegalArgumentException("enter an amount");
        }
        String trim = str3 == null ? "" : str3.trim();
        if (Bytes.utf8(trim).length > 64) {
            throw new IllegalArgumentException("memo is limited to 64 bytes");
        }
        ChainState.Account pendingAccount = this.node.pendingAccount(requireSpendable.account);
        int i2 = pendingAccount == null ? 0 : pendingAccount.keyIndex;
        int nextLeaf = nextLeaf(requireSpendable, pendingAccount, i2);
        boolean z2 = nextLeaf >= 1000;
        long add = z2 ? U64.add(Consensus.MIN_FEE, Consensus.MIN_FEE) : 10000000L;
        long add2 = U64.add(parseCoins, add);
        long j = pendingAccount == null ? 0L : pendingAccount.balance;
        if (U64.cmp(add2, j) > 0) {
            ChainState.Account account = this.node.chain.account(requireSpendable.account);
            long j2 = account == null ? 0L : account.immature;
            throw new IllegalArgumentException("not enough unlocked " + Consensus.COIN + ": you can send up to " + U64.format(j > add ? j - add : 0L) + (j2 != 0 ? " (" + U64.format(j2) + " more is still locked from mining)" : ""));
        }
        String nameOf = this.node.chain.nameOf(resolve);
        Object[] objArr = new Object[26];
        objArr[0] = "to";
        objArr[1] = nameOf != null ? Address.display(nameOf) : Address.raw(resolve);
        objArr[2] = "toRaw";
        objArr[3] = Address.raw(resolve);
        objArr[4] = "amount";
        objArr[5] = U64.str(parseCoins);
        objArr[6] = "amountText";
        objArr[7] = U64.format(parseCoins);
        objArr[8] = "fee";
        objArr[9] = U64.str(add);
        objArr[10] = "feeText";
        objArr[11] = U64.format(add);
        objArr[12] = "total";
        objArr[13] = U64.str(add2);
        objArr[14] = "totalText";
        objArr[15] = U64.format(add2);
        objArr[16] = "after";
        objArr[17] = U64.format(j - add2);
        objArr[18] = "memo";
        objArr[19] = trim;
        objArr[20] = "leaf";
        objArr[21] = Long.valueOf(nextLeaf);
        objArr[22] = "keyIndex";
        objArr[23] = Long.valueOf(i2);
        objArr[24] = "rekey";
        objArr[25] = Boolean.valueOf(z2);
        Map<String, Object> o = Json.o(objArr);
        if (z) {
            map = o;
        } else {
            ArrayList arrayList = new ArrayList();
            if (z2) {
                Lms.PrivateKey key = key(requireSpendable, i2 + 1);
                Tx base = base(requireSpendable, pendingAccount, i2, nextLeaf);
                base.type = 3;
                base.fee = Consensus.MIN_FEE;
                base.newPub = key.pub;
                sign(requireSpendable, base, i2, nextLeaf, i2 + 1);
                arrayList.add(base);
                i2++;
                i = requireSpendable.nextQ.containsKey(Integer.valueOf(i2)) ? requireSpendable.nextQ.get(Integer.valueOf(i2)).intValue() : 0;
            } else {
                i = nextLeaf;
            }
            Tx base2 = base(requireSpendable, pendingAccount, i2, i);
            if (z2) {
                base2.pub = key(requireSpendable, i2).pub;
            }
            base2.type = 1;
            base2.to = resolve;
            base2.amount = parseCoins;
            base2.fee = Consensus.MIN_FEE;
            base2.memo = trim;
            sign(requireSpendable, base2, i2, i);
            arrayList.add(base2);
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                String submitTx = this.node.submitTx((Tx) it.next(), null);
                if (submitTx != null) {
                    throw new IllegalArgumentException("the network rejected it: " + submitTx);
                }
            }
            o.put("txid", Bytes.hex(base2.id(this.node.chain.chainId)));
            map = o;
        }
        return map;
    }

    public synchronized Map<String, Object> max() {
        Map<String, Object> o;
        synchronized (this) {
            Wallet requireSpendable = requireSpendable();
            ChainState.Account pendingAccount = this.node.pendingAccount(requireSpendable.account);
            long j = pendingAccount == null ? 0L : pendingAccount.balance;
            long j2 = nextLeaf(requireSpendable, pendingAccount, pendingAccount != null ? pendingAccount.keyIndex : 0) >= 1000 ? Consensus.INITIAL_DIFFICULTY : Consensus.MIN_FEE;
            long j3 = U64.cmp(j, j2) > 0 ? j - j2 : 0L;
            o = Json.o("amount", U64.str(j3), "amountText", U64.format(j3).replace(",", ""));
        }
        return o;
    }

    public synchronized Map<String, Object> register(String str) throws Exception {
        Map<String, Object> o;
        synchronized (this) {
            Wallet requireSpendable = requireSpendable();
            String strip = Address.strip(str);
            if (!Address.validName(strip)) {
                throw new IllegalArgumentException("names are 3-24 letters, digits or inner hyphens (no double hyphens)");
            }
            if (this.node.chain.lookupName(strip) != null || this.node.mempool.nameTaken(strip)) {
                throw new IllegalArgumentException(strip + ".frostchain is taken");
            }
            ChainState.Account pendingAccount = this.node.pendingAccount(requireSpendable.account);
            if (pendingAccount != null && pendingAccount.name != null) {
                throw new IllegalArgumentException("this wallet already has " + Address.display(pendingAccount.name));
            }
            int i = pendingAccount != null ? pendingAccount.keyIndex : 0;
            int nextLeaf = nextLeaf(requireSpendable, pendingAccount, i);
            if (nextLeaf >= 1024) {
                throw new IllegalArgumentException("this key is used up; send once to rotate it first");
            }
            Tx base = base(requireSpendable, pendingAccount, i, nextLeaf);
            base.type = 2;
            base.name = strip;
            base.fee = 0L;
            sign(requireSpendable, base, i, nextLeaf);
            String submitTx = this.node.submitTx(base, null);
            if (submitTx != null) {
                throw new IllegalArgumentException("the network rejected it: " + submitTx);
            }
            o = Json.o("name", Address.display(strip), "txid", Bytes.hex(base.id(this.node.chain.chainId)));
        }
        return o;
    }

    public synchronized Map<String, Object> rekey() throws Exception {
        Map<String, Object> o;
        synchronized (this) {
            Wallet requireSpendable = requireSpendable();
            ChainState.Account pendingAccount = this.node.pendingAccount(requireSpendable.account);
            if (U64.cmp(Consensus.MIN_FEE, pendingAccount == null ? 0L : pendingAccount.balance) > 0) {
                throw new IllegalArgumentException("rotating the key costs a " + U64.format(Consensus.MIN_FEE) + " " + Consensus.COIN + " fee");
            }
            int i = pendingAccount != null ? pendingAccount.keyIndex : 0;
            int nextLeaf = nextLeaf(requireSpendable, pendingAccount, i);
            Lms.PrivateKey key = key(requireSpendable, i + 1);
            Tx base = base(requireSpendable, pendingAccount, i, nextLeaf);
            base.type = 3;
            base.fee = Consensus.MIN_FEE;
            base.newPub = key.pub;
            sign(requireSpendable, base, i, nextLeaf, i + 1);
            String submitTx = this.node.submitTx(base, null);
            if (submitTx != null) {
                throw new IllegalArgumentException("the network rejected it: " + submitTx);
            }
            o = Json.o("keyIndex", Long.valueOf(i + 1), "txid", Bytes.hex(base.id(this.node.chain.chainId)));
        }
        return o;
    }

    public void onNewBlock() {
    }

    private int nextLeaf(Wallet wallet, ChainState.Account account, int i) {
        int i2 = account == null ? 0 : account.q;
        return wallet.nextQ.containsKey(Integer.valueOf(i)) ? Math.max(i2, wallet.nextQ.get(Integer.valueOf(i)).intValue()) : Math.max(i2, Math.min(i2 + 64, 1022));
    }

    private Tx base(Wallet wallet, ChainState.Account account, int i, int i2) {
        Lms.PrivateKey key = key(wallet, i);
        if (account != null && account.pub != null && account.keyIndex == i && !Bytes.equal(account.pub, key.pub)) {
            throw new IllegalStateException("this seed doesn't match the account's current key");
        }
        Tx tx = new Tx();
        tx.from = wallet.account;
        tx.k = i;
        tx.q = i2;
        tx.pub = key.pub;
        return tx;
    }

    private void sign(Wallet wallet, Tx tx, int i, int i2) throws IOException {
        sign(wallet, tx, i, i2, -1);
    }

    private void sign(Wallet wallet, Tx tx, int i, int i2, int i3) throws IOException {
        if (i2 >= 1024) {
            throw new IllegalArgumentException("one-time keys for key " + i + " are used up");
        }
        Map map = (Map) wallet.json.get("nextq");
        if (map == null) {
            map = new LinkedHashMap();
            wallet.json.put("nextq", map);
        }
        map.put(String.valueOf(i), Long.valueOf(i2 + 1));
        boolean z = i3 >= 0 && !wallet.nextQ.containsKey(Integer.valueOf(i3));
        if (z) {
            map.put(String.valueOf(i3), 0L);
        }
        writeStrict(wallet.file, Json.write(wallet.json));
        wallet.nextQ.put(Integer.valueOf(i), Integer.valueOf(i2 + 1));
        if (z) {
            wallet.nextQ.put(Integer.valueOf(i3), 0);
        }
        tx.sig = Lms.sign(key(wallet, i), i2, tx.message(this.node.chain.chainId));
    }

    private void prepareKeysInBackground(final Wallet wallet) {
        Thread thread = new Thread(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                try {
                    ChainState.Account account = Wallets.this.node.chain.account(wallet.account);
                    Wallets.this.key(wallet, account == null ? 0 : account.keyIndex);
                } catch (Throwable th) {
                    Log.w("wallet", "key preparation: " + th);
                }
            }
        }, "graysons-keygen");
        thread.setDaemon(true);
        thread.start();
    }

    private Wallet requireOpen() {
        if (this.open == null) {
            throw new IllegalArgumentException("open a wallet first");
        }
        return this.open;
    }

    private Wallet requireSpendable() {
        Wallet requireOpen = requireOpen();
        if (requireOpen.watch) {
            throw new IllegalArgumentException("this is a watch-only wallet: it can't spend");
        }
        return requireOpen;
    }

    private static void checkPassword(String str) {
        if (str == null || str.length() < 4) {
            throw new IllegalArgumentException("choose a password of at least 4 characters");
        }
    }

    private static String cleanLabel(String str) {
        String trim = str == null ? "" : str.trim();
        if (trim.isEmpty()) {
            trim = "My wallet";
        }
        return trim.length() > 40 ? trim.substring(0, 40) : trim;
    }

    private File uniqueFile(String str) {
        File file;
        String replaceAll = str.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (replaceAll.isEmpty()) {
            replaceAll = "wallet";
        }
        do {
            file = new File(this.dir, replaceAll + "-" + Bytes.hex(Bytes.random(2)) + ".json");
        } while (file.exists());
        return file;
    }

    private File fileFor(String str) {
        if (str == null || !str.matches("[a-z0-9-]{1,60}\\.json")) {
            throw new IllegalArgumentException("unknown wallet");
        }
        File file = new File(this.dir, str);
        if (file.isFile()) {
            return file;
        }
        throw new IllegalArgumentException("unknown wallet");
    }

    private static byte[] aes(int i, String str, byte[] bArr, byte[] bArr2, byte[] bArr3, byte[] bArr4) throws Exception {
        return aes(i, str, bArr, bArr2, bArr3, bArr4, PBKDF2_ITERATIONS);
    }

    private static byte[] aes(int i, String str, byte[] bArr, byte[] bArr2, byte[] bArr3, byte[] bArr4, int i2) throws Exception {
        byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(str.toCharArray(), bArr, i2, 256)).getEncoded();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(i, new SecretKeySpec(encoded, "AES"), new GCMParameterSpec(128, bArr2));
        cipher.updateAAD(bArr3);
        return cipher.doFinal(bArr4);
    }

    private static void writeStrict(File file, String str) throws IOException {
        File file2 = new File(file.getPath() + ".tmp");
        FileOutputStream fileOutputStream = new FileOutputStream(file2);
        try {
            fileOutputStream.write(Bytes.utf8(str));
            fileOutputStream.getFD().sync();
            fileOutputStream.close();
            if (!file2.renameTo(file)) {
                if (!file.delete() || !file2.renameTo(file)) {
                    throw new IOException("couldn't save " + file.getName());
                }
            }
        } catch (Throwable th) {
            fileOutputStream.close();
            throw th;
        }
    }
}
