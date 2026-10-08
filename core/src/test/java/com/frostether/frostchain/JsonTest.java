package com.frostether.frostchain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** JSON carries every peer-to-peer message and every stored block, so it must round-trip exactly. */
public class JsonTest {

    private static Object roundTrip(Object value) {
        return Json.parse(Json.write(value));
    }

    @Test
    public void emptyObjectAndArray() {
        assertEquals(Collections.emptyMap(), Json.parse("{}"));
        assertEquals(Collections.emptyList(), Json.parse("[ ]"));
        assertEquals(Collections.emptyMap(), Json.parse(" { } "));
    }

    @Test
    public void nestedValuesRoundTrip() {
        Map<String, Object> value = Json.o(
                "height", 137L,
                "ok", true,
                "none", null,
                "txs", Arrays.asList(Json.o("memo", "tone test"), Json.o()),
                "nested", Json.o("list", Arrays.asList(1L, 2L, Arrays.asList()), "empty", Json.o()));
        assertEquals(value, roundTrip(value));
    }

    @Test
    public void stringsWithEscapesRoundTrip() {
        String tricky = "quote \" backslash \\ tab \t newline \n <script>   ⚡ 7.83 Hz";
        assertEquals(tricky, roundTrip(tricky));
    }

    @Test
    public void numbersParse() {
        assertEquals(-42L, ((Number) Json.parse("-42")).longValue());
        List<?> list = (List<?>) Json.parse("[0, 18446744073709551615, 3]");
        assertEquals(3, list.size());
    }

    @Test
    public void blockSurvivesJsonText() {
        Block g = Block.genesis();
        Block back = Block.fromJson(Json.obj(Json.write(g.toJson())));
        assertEquals(g.hashHex(), back.hashHex());
    }

    @Test
    public void malformedInputIsRejected() {
        for (String bad : new String[] {"{\"a\":1", "[1,2", "{\"a\" 1}", "[1 2]", "{} x", "{\"a\":1,}x"}) {
            try {
                Json.parse(bad);
                throw new AssertionError("accepted " + bad);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().startsWith("JSON"));
            }
        }
    }
}
