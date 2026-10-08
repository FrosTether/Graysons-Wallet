package com.frostether.frostchain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Test;

/** The real Frostoise miner: its threads, its fast hash loop and the resonance gate. */
public class MinerTest {

    /** What the phone's sensor hub reports while a speaker plays the tone. */
    static Resonance.Reading lockedReading(long nowMs, double hz) {
        Resonance.Reading r = new Resonance.Reading();
        r.sensor = "mic";
        r.locked = true;
        r.peakHz = hz;
        r.snr = 23.9;
        r.amplitude = 0.12;
        r.depth = 0.24;
        r.samples = 800;
        r.rateHz = 100;
        r.startMs = nowMs - 10_000;
        r.durMs = 8_000;
        r.digest = new byte[32];
        Arrays.fill(r.digest, (byte) 0x7e);
        Arrays.fill(r.power, 0.01);
        r.power[(int) Math.round((hz - Resonance.GRID_MIN) / Resonance.GRID_STEP)] = 1.0;
        r.why = "locked";
        return r;
    }

    private static boolean waitFor(java.util.function.BooleanSupplier ok, long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end && !ok.getAsBoolean()) {
            Thread.sleep(100);
        }
        return ok.getAsBoolean();
    }

    @Test
    public void minesOnlyWhileLocked() throws Exception {
        TestKit.FakeClock clock = new TestKit.FakeClock(Consensus.GENESIS_TIME + 300);
        Node node = new Node(Files.createTempDirectory("miner").toFile(), clock);
        byte[] payout = TestKit.id(TestKit.key(51));
        final Resonance.Reading[] reading = {null};
        node.miner.setSource(() -> reading[0]);

        node.miner.start(payout, 2);
        try {
            assertTrue(node.miner.running());
            Thread.sleep(3500);
            assertEquals(0, node.chain.height());
            assertEquals("resonance sensor is off", node.miner.status().get("gate"));

            reading[0] = lockedReading(clock.nowSec() * 1000, 7.83);
            node.miner.refresh();
            assertTrue("the miner should find block 1", waitFor(() -> node.chain.height() >= 1, 120_000));
            assertEquals(Bytes.hex(payout), Bytes.hex(node.chain.at(1).miner));
            assertTrue(((Number) node.miner.status().get("accepted")).longValue() >= 1);
        } finally {
            node.miner.stop();
        }
        assertTrue(!node.miner.running());
    }

    @Test
    public void startNeedsAPayoutAddress() throws Exception {
        Node node = new Node(Files.createTempDirectory("miner2").toFile(), null);
        try {
            node.miner.start(new byte[3], 1);
            throw new AssertionError("started without a payout address");
        } catch (IllegalArgumentException expected) {
            assertEquals("Frostoise needs a payout address", expected.getMessage());
        }
    }
}
