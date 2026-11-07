package in.ac.iitm.slotbook.server;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void parsesThePortalPayloadShape() {
        Object parsed = Json.parse("{\"solver\":\"cpsat\",\"student_preferences_csv\":\"student_id,course_id,rank\\nS1,EE1101,1\\n\"}");
        Map<?, ?> map = assertInstanceOf(Map.class, parsed);
        assertEquals("cpsat", map.get("solver"));
        assertEquals("student_id,course_id,rank\nS1,EE1101,1\n", map.get("student_preferences_csv"));
    }

    @Test
    void parsesEveryValueKind() {
        Map<?, ?> map = assertInstanceOf(Map.class,
                Json.parse(" { \"a\" : [1, -2.5, 3e2, true, false, null, {}, []], \"b\" : \"\\u00e9\\t\\\"q\\\" \\\\ \\/\" } "));
        assertEquals(List.of(1.0, -2.5, 300.0, true, false), ((List<?>) map.get("a")).subList(0, 5));
        assertEquals("\u00e9\t\"q\" \\ /", map.get("b"));
    }

    @Test
    void writesStringsThatParseBackUnchanged() {
        String tricky = "Reason, with comma | \"quotes\" \\ back\nnew line \u0001 \u00e9";
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("text", tricky);
        value.put("count", 19);
        value.put("objective", 20L);
        value.put("rows", List.of("a", "b"));
        String json = Json.write(value);
        assertEquals("{\"text\":", json.substring(0, 8));
        assertTrue(json.contains("\"count\":19,\"objective\":20,"), json);
        Map<?, ?> back = assertInstanceOf(Map.class, Json.parse(json));
        assertEquals(tricky, back.get("text"));
        assertEquals(List.of("a", "b"), back.get("rows"));
    }

    @Test
    void rejectsMalformedInputWithAPosition() {
        for (String bad : List.of("", "{", "{\"a\":}", "[1,]", "\"abc", "{} x", "tru", "{\"a\" 1}", "\"\\x\"", "\"\\u12\"")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> Json.parse(bad), bad);
            assertTrue(error.getMessage().startsWith("Invalid JSON at character"), error.getMessage());
        }
    }

    @Test
    void rejectsDeepNestingInsteadOfOverflowingTheStack() {
        String deep = "[".repeat(100) + "]".repeat(100);
        assertThrows(IllegalArgumentException.class, () -> Json.parse(deep));
    }
}
