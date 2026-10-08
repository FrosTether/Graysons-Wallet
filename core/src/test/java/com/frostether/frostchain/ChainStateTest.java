package com.frostether.frostchain;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The transaction rule (ChainState.apply), which was rebuilt by hand from the 0.3.0 bytecode. */
public class ChainStateTest {

    private static final byte[] CHAIN = new byte[32];
    private static final Lms.PrivateKey ALICE = TestKit.key(1);
    private static final Lms.PrivateKey BOB = TestKit.key(3);
    private static final long QOIN = TestKit.QOIN;

    /** Alice mined block 1 and her 500 QOIN have matured. */
    private static ChainState funded() {
        ChainState s = new ChainState();
        s.beginBlock(1, TestKit.id(ALICE));
        s.credit(TestKit.id(ALICE), 500 * QOIN, 13);
        s.unlock(13);
        return s;
    }

    @Test
    public void theFounderNameBelongsToItsAddressFromGenesis() {
        ChainState s = new ChainState();
        assertArrayEquals(Consensus.founderAccount(), s.lookupName("jacobfrost"));
        assertArrayEquals(Consensus.founderAccount(), s.lookupName("agorajay"));
        assertEquals("jacobfrost", s.account(Consensus.founderAccount()).name);
        assertEquals(2, s.nameCount());
        // Mining block 1 doesn't come with a name any more.
        s = funded();
        assertNull(s.account(TestKit.id(ALICE)).name);
        assertArrayEquals(Consensus.founderAccount(), s.lookupName("jacobfrost"));
        // A copy keeps it.
        assertArrayEquals(Consensus.founderAccount(), s.copy().lookupName("jacobfrost"));
    }

    @Test
    public void minedCoinsStayLockedUntilTheirHeight() {
        ChainState s = new ChainState();
        s.credit(TestKit.id(ALICE), 7 * QOIN, 20);
        s.unlock(19);
        assertEquals(0, s.account(TestKit.id(ALICE)).balance);
        assertEquals(7 * QOIN, s.account(TestKit.id(ALICE)).immature);
        s.unlock(20);
        assertEquals(7 * QOIN, s.account(TestKit.id(ALICE)).balance);
        assertEquals(0, s.account(TestKit.id(ALICE)).immature);
    }

    @Test
    public void sendMovesAmountAndFee() {
        ChainState s = funded();
        assertNull(s.apply(TestKit.send(ALICE, 0, TestKit.id(BOB), 120 * QOIN, CHAIN)));
        assertEquals(500 * QOIN - 120 * QOIN - Consensus.MIN_FEE, s.account(TestKit.id(ALICE)).balance);
        assertEquals(120 * QOIN, s.account(TestKit.id(BOB)).balance);
        assertEquals(1, s.account(TestKit.id(ALICE)).q);
        assertArrayEquals(ALICE.pub, s.account(TestKit.id(ALICE)).pub);
    }

    @Test
    public void cannotSpendMoreThanTheBalance() {
        ChainState s = funded();
        assertEquals("insufficient funds", s.apply(TestKit.send(ALICE, 0, TestKit.id(BOB), 500 * QOIN, CHAIN)));
        assertEquals(500 * QOIN, s.account(TestKit.id(ALICE)).balance);
    }

    @Test
    public void emptyAccountCannotSpend() {
        ChainState s = funded();
        assertEquals("insufficient funds", s.apply(TestKit.send(BOB, 0, TestKit.id(ALICE), QOIN, CHAIN)));
    }

    @Test
    public void oneTimeKeyCannotBeReused() {
        ChainState s = funded();
        assertNull(s.apply(TestKit.send(ALICE, 0, TestKit.id(BOB), QOIN, CHAIN)));
        assertEquals("one-time key 0 already used (next is 1)", s.apply(TestKit.send(ALICE, 0, TestKit.id(BOB), QOIN, CHAIN)));
        assertNull(s.apply(TestKit.send(ALICE, 4, TestKit.id(BOB), QOIN, CHAIN)));
        assertEquals(5, s.account(TestKit.id(ALICE)).q);
    }

    @Test
    public void firstTransactionMustUseKeyZero() {
        ChainState s = funded();
        Tx tx = TestKit.send(ALICE, 0, TestKit.id(BOB), QOIN, CHAIN);
        tx.k = 1;
        assertEquals("first transaction must use key 0", s.apply(tx));
    }

    @Test
    public void publicKeyMustMatchTheAccount() {
        ChainState s = funded();
        Tx tx = TestKit.send(ALICE, 0, TestKit.id(BOB), QOIN, CHAIN);
        tx.pub = BOB.pub;
        assertEquals("public key doesn't belong to this account", s.apply(tx));
    }

    @Test
    public void namesAreClaimedOnceAndTheFounderNameIsTaken() {
        ChainState s = funded();
        assertNull(s.apply(TestKit.send(ALICE, 0, TestKit.id(BOB), 10 * QOIN, CHAIN)));
        assertEquals("name already taken", s.apply(TestKit.claimName(BOB, 0, "jacobfrost", CHAIN)));
        assertEquals("name already taken", s.apply(TestKit.claimName(BOB, 0, "agorajay", CHAIN)));
        assertNull(s.apply(TestKit.claimName(BOB, 0, "oofmaster", CHAIN)));
        assertArrayEquals(TestKit.id(BOB), s.lookupName("oofmaster"));
        assertEquals(10 * QOIN - Consensus.MIN_FEE, s.account(TestKit.id(BOB)).balance);
        assertEquals("this account already has a name", s.apply(TestKit.claimName(BOB, 1, "another", CHAIN)));
        // Alice mined block 1 but has no name: the founder name was never hers to get.
        assertEquals("name already taken", s.apply(TestKit.claimName(ALICE, 1, "oofmaster", CHAIN)));
    }

    @Test
    public void takenNamesAreRejected() {
        ChainState s = funded();
        Lms.PrivateKey carol = TestKit.key(5);
        assertNull(s.apply(TestKit.send(ALICE, 0, TestKit.id(BOB), 10 * QOIN, CHAIN)));
        assertNull(s.apply(TestKit.send(ALICE, 1, TestKit.id(carol), 10 * QOIN, CHAIN)));
        assertNull(s.apply(TestKit.claimName(BOB, 0, "oofmaster", CHAIN)));
        assertEquals("name already taken", s.apply(TestKit.claimName(carol, 0, "oofmaster", CHAIN)));
    }

    @Test
    public void rekeySwitchesToTheNewKey() {
        ChainState s = funded();
        Lms.PrivateKey next = TestKit.key(11);
        Tx tx = new Tx();
        tx.type = Tx.REKEY;
        tx.from = TestKit.id(ALICE);
        tx.k = 0;
        tx.q = 3;
        tx.pub = ALICE.pub;
        tx.newPub = next.pub;
        tx.fee = Consensus.MIN_FEE;
        tx.sig = Lms.sign(ALICE, 3, tx.message(CHAIN));
        assertNull(s.apply(tx));
        ChainState.Account a = s.account(TestKit.id(ALICE));
        assertArrayEquals(next.pub, a.pub);
        assertEquals(1, a.keyIndex);
        assertEquals(0, a.q);
        assertEquals("wrong key index (expected 1)", s.apply(TestKit.send(ALICE, 0, TestKit.id(BOB), QOIN, CHAIN)));
    }

    @Test
    public void copyIsIndependent() {
        ChainState s = funded();
        ChainState c = s.copy();
        assertNull(c.apply(TestKit.send(ALICE, 0, TestKit.id(BOB), QOIN, CHAIN)));
        assertEquals(500 * QOIN, s.account(TestKit.id(ALICE)).balance);
    }
}
