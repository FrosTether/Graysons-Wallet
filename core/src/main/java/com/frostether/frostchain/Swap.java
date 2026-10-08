package com.frostether.frostchain;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Swap {
    public static final String DESK_NAME = "pidoge";
    public static final int DOGE_DECIMALS = 8;
    private static final BigInteger KOINU_PER_DOGE = BigInteger.TEN.pow(8);
    public static final String MEMO_PREFIX = "swap ";
    public static final String MYDOGE_URL = "https://mydoge.com/frostcoin";
    public static final String MYDOGE_USER = "frostcoin";
    public static final long RATE_DOGE = 10;
    public static final String RATE_QOIN = "3.141337";

    private Swap() {
    }

    public static long quote(String str) {
        BigInteger divide = parseDoge(str).multiply(BigInteger.valueOf(U64.parseCoins(RATE_QOIN))).divide(BigInteger.valueOf(10L).multiply(KOINU_PER_DOGE));
        if (divide.bitLength() > 64) {
            throw new IllegalArgumentException("amount too large");
        }
        return divide.longValue();
    }

    static BigInteger parseDoge(String str) {
        String str2;
        if (str == null) {
            throw new IllegalArgumentException("enter how much DOGE");
        }
        String replace = str.trim().replace(",", "");
        if (!replace.matches("[0-9]*(\\.[0-9]*)?") || replace.isEmpty() || replace.equals(".")) {
            throw new IllegalArgumentException("not a DOGE amount: " + str);
        }
        String str3 = "";
        int indexOf = replace.indexOf(46);
        if (indexOf >= 0) {
            str2 = replace.substring(0, indexOf);
            str3 = replace.substring(indexOf + 1);
        } else {
            str2 = replace;
        }
        if (str3.length() > 8) {
            throw new IllegalArgumentException("DOGE has 8 decimal places");
        }
        while (str3.length() < 8) {
            str3 = str3 + "0";
        }
        if (str2.isEmpty()) {
            str2 = "0";
        }
        BigInteger add = new BigInteger(str2).multiply(KOINU_PER_DOGE).add(new BigInteger(str3));
        if (add.signum() == 0) {
            throw new IllegalArgumentException("enter how much DOGE");
        }
        if (add.bitLength() > 62) {
            throw new IllegalArgumentException("amount too large");
        }
        return add;
    }

    static String dogeText(String str) {
        BigInteger[] divideAndRemainder = parseDoge(str).divideAndRemainder(KOINU_PER_DOGE);
        String bigInteger = divideAndRemainder[1].toString();
        while (bigInteger.length() < 8) {
            bigInteger = "0" + bigInteger;
        }
        String replaceAll = bigInteger.replaceAll("0+$", "");
        return divideAndRemainder[0] + (replaceAll.isEmpty() ? "" : "." + replaceAll);
    }

    public static Map<String, Object> info(Node node) {
        byte[] lookupName = node.chain.lookupName(DESK_NAME);
        byte[] openAccount = node.wallets.openAccount();
        boolean z = (lookupName == null || openAccount == null || !Bytes.equal(lookupName, openAccount) || node.wallets.isWatchOnly()) ? false : true;
        Object[] objArr = new Object[16];
        objArr[0] = "mydogeUser";
        objArr[1] = MYDOGE_USER;
        objArr[2] = "mydogeUrl";
        objArr[3] = MYDOGE_URL;
        objArr[4] = "desk";
        objArr[5] = Address.display(DESK_NAME);
        objArr[6] = "deskReady";
        objArr[7] = Boolean.valueOf(lookupName != null);
        objArr[8] = "rateDoge";
        objArr[9] = String.valueOf(10L);
        objArr[10] = "rateQoin";
        objArr[11] = RATE_QOIN;
        objArr[12] = "rateText";
        objArr[13] = "10 DOGE = 3.141337 QOIN";
        objArr[14] = "isDesk";
        objArr[15] = Boolean.valueOf(z);
        return Json.o(objArr);
    }

    public static Map<String, Object> pay(Node node, String str, String str2, String str3, boolean z) throws Exception {
        if (!Json.bool(info(node), "isDesk", false)) {
            throw new IllegalArgumentException("only " + Address.display(DESK_NAME) + " runs the swap desk");
        }
        long quote = quote(str2);
        if (quote == 0) {
            throw new IllegalArgumentException("that's less than the smallest QOIN amount");
        }
        String replaceAll = str3 == null ? "" : str3.trim().replaceAll("[^A-Za-z0-9]", "");
        if (replaceAll.length() > 16) {
            replaceAll = replaceAll.substring(0, 16);
        }
        Map<String, Object> send = node.wallets.send(str, U64.format(quote).replace(",", ""), MEMO_PREFIX + dogeText(str2) + " DOGE" + (replaceAll.isEmpty() ? "" : " " + replaceAll), z);
        send.put("doge", dogeText(str2));
        return send;
    }

    public static List<Map<String, Object>> history(Node node) {
        ArrayList arrayList = new ArrayList();
        for (Map<String, Object> map : node.wallets.history()) {
            if (Json.str(map, "memo", "").startsWith(MEMO_PREFIX)) {
                arrayList.add(map);
            }
        }
        return arrayList;
    }
}
