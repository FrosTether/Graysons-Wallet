package com.frostether.frostchain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ResonanceTest {

    private static final long T = Consensus.GENESIS_TIME + 3600;

    @Test
    public void validProofPasses() {
        assertNull(TestKit.proof(T).check(T));
    }

    @Test
    public void frequencyMustBeNear783() {
        Resonance.Proof p = TestKit.proof(T);
        p.hzMilli = 11110;
        assertEquals("resonance not near 7.83 Hz", p.check(T));
        p.hzMilli = 7499;
        assertEquals("resonance not near 7.83 Hz", p.check(T));
    }

    @Test
    public void peakMustBeTenTimesTheNoise() {
        Resonance.Proof p = TestKit.proof(T);
        p.snrX100 = 999;
        assertEquals("resonance peak too weak", p.check(T));
    }

    @Test
    public void spectrumPeakMustMatchTheFrequency() {
        Resonance.Proof p = TestKit.proof(T);
        p.spectrum[15] = 20;
        p.spectrum[30] = (byte) 255; // 11.5 Hz
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
