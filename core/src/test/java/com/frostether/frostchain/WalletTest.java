package com.frostether.frostchain;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** The 25 words and the encrypted wallet file are what protect people's coins. */
public class WalletTest {

    private static void expectError(String expected, ThrowingRunnable r) throws Exception {
        try {
            r.run();
            throw new AssertionError("expected: " + expected);
        } catch (IllegalArgumentException e) {
            assertTrue("got: " + e.getMessage(), e.getMessage().startsWith(expected));
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Test
    public void seedWordsRoundTrip() {
        for (int n = 0; n < 50; n++) {
            byte[] seed = Bytes.random(32);
            String words = Mnemonic.encode(seed);
            assertEquals(25, words.split(" ").length);
            assertArrayEquals(seed, Mnemonic.decode(words));
            assertArrayEquals(seed, Mnemonic.decode("  " + words.toUpperCase() + "  "));
        }
    }

    @Test
    public void typosAreCaught() throws Exception {
        String words = Mnemonic.encode(Bytes.random(32));
        String[] w = words.split(" ");
        String badChecksum = words.substring(0, words.lastIndexOf(' ')) + " " + (w[24].startsWith("abb") ? "zoo" : "abbey");
        expectError("checksum word doesn't match", () -> Mnemonic.decode(badChecksum));
        expectError("a seed has 25 words", () -> Mnemonic.decode("one two three"));
        w[3] = "qqqq";
        expectError("word 4 \"qqqq\" is not in the word list", () -> Mnemonic.decode(String.join(" ", w)));
    }

    @Test
    public void createLockReopenAndRestore() throws Exception {
        Node node = new Node(Files.createTempDirectory("wallet-test").toFile(), null);
        Wallets wallets = node.wallets;

        Map<String, Object> created = wallets.create("Jacob", "tone7830");
        String seed = (String) created.get("seed");
        assertEquals(25, seed.split(" ").length);
        String file = (String) created.get("file");
        String address = (String) wallets.list().get(0).get("address");
        assertTrue(address.startsWith("fc"));

        wallets.close();
        expectError("wrong password", () -> wallets.open(file, "wrong"));
        wallets.open(file, "tone7830");
        assertEquals(seed, wallets.seedWords("tone7830"));
        expectError("wrong password", () -> wallets.seedWords("nope"));

        // The same words always restore the same account, and can't be added twice.
        expectError("this seed is already in wallet", () -> wallets.restore("Again", "pw12", seed));
        wallets.delete(file);
        wallets.restore("Restored", "pw12", seed);
        List<Map<String, Object>> list = wallets.list();
        assertEquals(1, list.size());
        assertEquals(address, list.get(0).get("address"));
        assertArrayEquals(Wallets.accountFor(Mnemonic.decode(seed)), Address.decodeRaw(address));
    }
}
