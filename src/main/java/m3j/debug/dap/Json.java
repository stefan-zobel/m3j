// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.debug.dap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON for the debug adapter: a value is a Map (an object, its members in
 * order), a List (an array), a String, a Long or a Double (a number), a
 * Boolean or null.
 */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    /**
     * The value of the JSON text s.
     *
     * @throws IllegalArgumentException if s is not JSON
     */
    public static Object parse(String s) {
        Json p = new Json(s);
        p.space();
        Object v = p.value();
        p.space();
        if (p.i < s.length()) {
            throw p.error("text after the value");
        }
        return v;
    }

    /** The value v as JSON text. */
    public static String write(Object v) {
        StringBuilder b = new StringBuilder();
        write(b, v);
        return b.toString();
    }

    private static void write(StringBuilder b, Object v) {
        switch (v) {
            case null -> b.append("null");
            case String t -> string(b, t);
            case Boolean x -> b.append(x);
            case Double d -> {
                if (d.isNaN() || d.isInfinite()) {
                    b.append("null");
                } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                    b.append(d.longValue());
                } else {
                    b.append(d);
                }
            }
            case Number n -> b.append(n.longValue());
            case Map<?, ?> m -> {
                b.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    b.append(first ? "" : ",");
                    first = false;
                    string(b, String.valueOf(e.getKey()));
                    b.append(':');
                    write(b, e.getValue());
                }
                b.append('}');
            }
            case List<?> l -> {
                b.append('[');
                for (int k = 0; k < l.size(); k++) {
                    b.append(k > 0 ? "," : "");
                    write(b, l.get(k));
                }
                b.append(']');
            }
            default -> string(b, v.toString());
        }
    }

    private static void string(StringBuilder b, String t) {
        b.append('"');
        for (int k = 0; k < t.length(); k++) {
            char c = t.charAt(k);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        b.append('"');
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("bad JSON at offset " + i + ": " + what);
    }

    private void space() {
        while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
    }

    private Object value() {
        if (i >= s.length()) {
            throw error("end of the text");
        }
        char c = s.charAt(i);
        switch (c) {
            case '{' -> {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                space();
                if (peek('}')) {
                    return m;
                }
                do {
                    space();
                    if (i >= s.length() || s.charAt(i) != '"') {
                        throw error("a name expected");
                    }
                    String k = string();
                    space();
                    expect(':');
                    space();
                    m.put(k, value());
                    space();
                } while (peek(','));
                expect('}');
                return m;
            }
            case '[' -> {
                i++;
                List<Object> l = new ArrayList<>();
                space();
                if (peek(']')) {
                    return l;
                }
                do {
                    space();
                    l.add(value());
                    space();
                } while (peek(','));
                expect(']');
                return l;
            }
            case '"' -> {
                return string();
            }
            default -> {
                if (s.startsWith("true", i)) {
                    i += 4;
                    return Boolean.TRUE;
                }
                if (s.startsWith("false", i)) {
                    i += 5;
                    return Boolean.FALSE;
                }
                if (s.startsWith("null", i)) {
                    i += 4;
                    return null;
                }
                return number();
            }
        }
    }

    private boolean peek(char c) {
        if (i < s.length() && s.charAt(i) == c) {
            i++;
            return true;
        }
        return false;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("'" + c + "' expected");
        }
    }

    private String string() {
        i++;
        StringBuilder b = new StringBuilder();
        while (true) {
            if (i >= s.length()) {
                throw error("end of the text in a string");
            }
            char c = s.charAt(i++);
            if (c == '"') {
                return b.toString();
            }
            if (c != '\\') {
                b.append(c);
                continue;
            }
            if (i >= s.length()) {
                throw error("end of the text in a string");
            }
            char e = s.charAt(i++);
            switch (e) {
                case '"', '\\', '/' -> b.append(e);
                case 'b' -> b.append('\b');
                case 'f' -> b.append('\f');
                case 'n' -> b.append('\n');
                case 'r' -> b.append('\r');
                case 't' -> b.append('\t');
                case 'u' -> {
                    if (i + 4 > s.length()) {
                        throw error("a short \\u escape");
                    }
                    try {
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    } catch (NumberFormatException x) {
                        throw error("a bad \\u escape");
                    }
                    i += 4;
                }
                default -> throw error("a bad escape");
            }
        }
    }

    private Object number() {
        int start = i;
        peek('-');
        while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        String t = s.substring(start, i);
        if (t.isEmpty() || t.equals("-")) {
            throw error("a value expected");
        }
        try {
            if (t.indexOf('.') < 0 && t.indexOf('e') < 0 && t.indexOf('E') < 0) {
                return Long.parseLong(t);
            }
            return Double.parseDouble(t);
        } catch (NumberFormatException x) {
            throw error("a bad number");
        }
    }
}
