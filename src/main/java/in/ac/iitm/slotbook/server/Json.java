package in.ac.iitm.slotbook.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Just enough JSON for the solver service: the portal sends one object of strings, and the reply
 * is objects, arrays, strings and numbers. Written here rather than adding a library (P020).
 *
 * <p>Parsed values are {@code Map<String, Object>} (insertion ordered), {@code List<Object>},
 * {@code String}, {@code Double}, {@code Boolean} or {@code null}.
 */
public final class Json {
    private static final int MAX_DEPTH = 32;

    private final String text;
    private int position;

    private Json(String text) {
        this.text = text;
    }

    /** Parses one JSON value; throws {@link IllegalArgumentException} on malformed input. */
    public static Object parse(String text) {
        Json parser = new Json(text);
        parser.skipWhitespace();
        Object value = parser.value(0);
        parser.skipWhitespace();
        if (parser.position != text.length()) throw parser.error("unexpected text after the value");
        return value;
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) throw error("nested too deeply");
        if (position >= text.length()) throw error("unexpected end of input");
        char c = text.charAt(position);
        switch (c) {
            case '{':
                return object(depth);
            case '[':
                return array(depth);
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return number();
                throw error("unexpected character '" + c + "'");
        }
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        position++;
        skipWhitespace();
        if (peek('}')) {
            position++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (!peek('"')) throw error("expected a string key");
            String key = string();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, value(depth + 1));
            skipWhitespace();
            if (peek(',')) {
                position++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> array(int depth) {
        List<Object> list = new ArrayList<>();
        position++;
        skipWhitespace();
        if (peek(']')) {
            position++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(value(depth + 1));
            skipWhitespace();
            if (peek(',')) {
                position++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String string() {
        position++;
        StringBuilder out = new StringBuilder();
        while (position < text.length()) {
            char c = text.charAt(position++);
            if (c == '"') return out.toString();
            if (c < 0x20) throw error("control character in string");
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (position >= text.length()) break;
            char escaped = text.charAt(position++);
            switch (escaped) {
                case '"', '\\', '/' -> out.append(escaped);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (position + 4 > text.length()) throw error("short unicode escape");
                    try {
                        out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                    } catch (NumberFormatException bad) {
                        throw error("bad unicode escape");
                    }
                    position += 4;
                }
                default -> throw error("bad escape \\" + escaped);
            }
        }
        throw error("unterminated string");
    }

    private Double number() {
        int start = position;
        if (peek('-')) position++;
        while (position < text.length() && "0123456789.eE+-".indexOf(text.charAt(position)) >= 0) position++;
        try {
            return Double.valueOf(text.substring(start, position));
        } catch (NumberFormatException bad) {
            throw error("bad number");
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, position)) throw error("unexpected token");
        position += word.length();
        return value;
    }

    private void skipWhitespace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++;
    }

    private boolean peek(char c) {
        return position < text.length() && text.charAt(position) == c;
    }

    private void expect(char c) {
        if (!peek(c)) throw error("expected '" + c + "'");
        position++;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("Invalid JSON at character " + position + ": " + message);
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            quote(s, out);
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            out.append(value);
        } else if (value instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) out.append("null");
            else out.append(String.format(Locale.ROOT, "%.3f", d));
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                quote(String.valueOf(entry.getKey()), out);
                out.append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof Iterable<?> items) {
            out.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) out.append(',');
                first = false;
                write(item, out);
            }
            out.append(']');
        } else {
            quote(value.toString(), out);
        }
    }

    private static void quote(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }
}
