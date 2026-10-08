package com.frostether.frostchain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LmsTest {

    private static final Lms.PrivateKey KEY = TestKit.key(7);

    @Test
    public void keyUsesTheRfc8554ParameterSet() {
        assertTrue(Lms.validPublicKey(KEY.pub));
        assertEquals(56, KEY.pub.length);
        assertEquals(1024, KEY.leaves());
        assertEquals(6, Bytes.readU32(KEY.pub, 0)); // LMS_SHA256_M32_H10
        assertEquals(3, Bytes.readU32(KEY.pub, 4)); // LMOTS_SHA256_N32_W4
    }

    @Test
    public void signatureVerifiesAndCarriesItsLeaf() {
        byte[] msg = Bytes.utf8("send 10 QOIN to pidoge");
        byte[] sig = Lms.sign(KEY, 5, msg);
        assertEquals(Lms.SIG_LEN, sig.length);
        assertEquals(5, Lms.sigLeaf(sig));
        assertTrue(Lms.verify(KEY.pub, msg, sig));
    }

    @Test
    public void tamperedMessageFails() {
        byte[] sig = Lms.sign(KEY, 6, Bytes.utf8("send 10 QOIN"));
        assertFalse(Lms.verify(KEY.pub, Bytes.utf8("send 99 QOIN"), sig));
    }

    @Test
    public void otherKeyFails() {
        byte[] msg = Bytes.utf8("hello");
        byte[] sig = Lms.sign(KEY, 7, msg);
        assertFalse(Lms.verify(TestKit.key(9).pub, msg, sig));
    }

    @Test
    public void sameSeedGivesSameKey() {
        assertEquals(Bytes.hex(KEY.pub), Bytes.hex(TestKit.key(7).pub));
    }
}
