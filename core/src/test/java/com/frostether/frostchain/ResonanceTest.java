package com.frostether.frostchain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ResonanceTest {

    private static final long T = Consensus.GENESIS_TIME + 3600;

    @Test
    public void gridAndSpectrumLineUp() {
        assertEquals(257, Resonance.GRID); // 2.0 to 14.8 Hz in 0.05 Hz steps
        assertEquals(0.4, Resonance.SPECTRUM_STEP_HZ, 1e-12);
    }

    @Test
    public void allThreeMiningTonesPass() {
        assertNull(TestKit.proof(T, 4.0).check(T));
        assertNull(TestKit.proof(T, 7.83).check(T));
        assertNull(TestKit.proof(T, 11.11).check(T));
    }

    @Test
    public void bandsAreMatchedToTheirTones() {
        assertEquals(0, Resonance.bandOfMilli(4000));
        assertEquals(1, Resonance.bandOfMilli(7830));
        assertEquals(2, Resonance.bandOfMilli(11110));
        assertEquals(-1, Resonance.bandOfMilli(6000));
        assertEquals(0, Resonance.lockBand(4.2));
        assertEquals(1, Resonance.lockBand(7.70));
        assertEquals(2, Resonance.lockBand(11.30));
        assertEquals(-1, Resonance.lockBand(9.5));
    }

    @Test
    public void otherFrequenciesAreRejected() {
        Resonance.Proof p = TestKit.proof(T, 6.0);
        assertEquals("resonance not at a mining tone", p.check(T));
        p = TestKit.proof(T, 7.83);
        p.hzMilli = 12000;
        assertEquals("resonance not at a mining tone", p.check(T));
    }

    @Test
    public void fastTonesNeedAFastSensor() {
        Resonance.Proof p = TestKit.proof(T, 11.11);
        p.rateCenti = 2000; // 20 Hz sampling can't capture 11.11 Hz
        assertEquals("resonance sample rate too low for its frequency", p.check(T));
        p = TestKit.proof(T, 7.83);
        p.rateCenti = 1600; // 16 Hz is still enough for 7.83, as in 0.3.x
        assertNull(p.check(T));
    }

    @Test
    public void peakMustBeTenTimesTheNoise() {
        Resonance.Proof p = TestKit.proof(T);
        p.snrX100 = 999;
        assertEquals("resonance peak too weak", p.check(T));
    }

    @Test
    public void spectrumPeakMustMatchTheFrequency() {
        Resonance.Proof p = TestKit.proof(T, 7.83);
        java.util.Arrays.fill(p.spectrum, (byte) 20);
        p.spectrum[30] = (byte) 255; // 14.0 Hz
        assertEquals("resonance spectrum peak doesn't match its frequency", p.check(T));
    }

    @Test
    public void readingMustBeRecent() {
        Resonance.Proof p = TestKit.proof(T);
        assertEquals("resonance window too old for this block", p.check(T + 16 * 60));
    }

    @Test
    public void onlyKnownSensors() {
        Resonance.Proof p = TestKit.proof(T);
        p.sensor = "camera";
        assertEquals("unknown resonance sensor", p.check(T));
    }
}
