package com.frostether.frostchain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** The calls the apps make through Api, on a node with a mined block. */
public class ApiTest {

    private static final Lms.PrivateKey MINER = TestKit.key(41);

    static final class NoSensors implements Api.Platform {
        public Resonance.Reading latestReading() { return null; }
        public void miningChanged(boolean z) { }
        public String name() { return "test"; }
        public Map<String, Object> sensors() { return Json.o("mag", false, "mic", false, "running", "", "error", ""); }
        public void startSensor(String str) { throw new IllegalArgumentException("no sensors"); }
        public void stopSensor() { }
    }

    @Test
    public void recentBlocksListTheMinedBlock() throws Exception {
        TestKit.FakeClock clock = new TestKit.FakeClock(Consensus.GENESIS_TIME + 300);
        File dir = Files.createTempDirectory("api").toFile();
        Node node = new Node(dir, clock);
        Api api = new Api(node, new NoSensors());
        TestKit.mine(node.chain, clock, TestKit.id(MINER), Collections.<Tx>emptyList());

        Map<String, Object> reply = Json.obj(api.call("node.blocks", "{\"count\":5}"));
        assertEquals("node.blocks failed: " + reply, Boolean.TRUE, reply.get("ok"));
        List<?> rows = (List<?>) reply.get("result");
        assertEquals(1, rows.size());
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertEquals(1L, ((Number) row.get("height")).longValue());
        assertTrue(row.containsKey("difficulty"));
        assertEquals("theta", row.get("band"));
    }
}
