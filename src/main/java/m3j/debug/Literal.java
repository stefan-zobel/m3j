// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.debug;

/**
 * Literals of Modula-3 as a debugger takes them for a new value: numbers
 * (also based, 16_FF, and with the suffix L), characters ('a', W'\x20ac'),
 * texts, the names of the elements of enumerations (also qualified,
 * Color.Red), TRUE and FALSE, real numbers (1.5, 1.5E3, 2.5D0, 3.5X0) and
 * NIL. A number may have a sign; a real number may be written without a
 * point.
 */
final class Literal {

    private Literal() {}

    /** The value of the ordinal type t that text writes. */
    static long ordinal(TypeTable types, String t, String text) {
        String s = text.strip();
        String base = types.resolve(t);
        if (types.get(base) instanceof Info.Subrange r) {
            base = types.resolve(r.base());
        }
        switch (base) {
            case "BOOLEAN" -> {
                return switch (unqualified(s)) {
                    case "TRUE" -> 1;
                    case "FALSE" -> 0;
                    default -> throw bad(types, t, s);
                };
            }
            case "CHAR", "WIDECHAR" -> {
                if (s.startsWith("'") || s.startsWith("W'")) {
                    String c = quoted(s, '\'');
                    if (c.codePointCount(0, c.length()) != 1) {
                        throw bad(types, t, s);
                    }
                    return c.codePointAt(0);
                }
                throw bad(types, t, s);
            }
            default -> {
            }
        }
        if (types.get(base) instanceof Info.Enum e) {
            int i = e.elements().indexOf(unqualified(s));
            if (i < 0) {
                throw bad(types, t, s);
            }
            return i;
        }
        try {
            return integer(s);
        } catch (NumberFormatException x) {
            throw bad(types, t, s);
        }
    }

    /** Whether name is an element of the ordinal type t (an enumeration, or BOOLEAN). */
    static boolean isElement(TypeTable types, String t, String name) {
        String base = types.resolve(t);
        if (types.get(base) instanceof Info.Subrange r) {
            base = types.resolve(r.base());
        }
        if (base.equals("BOOLEAN")) {
            return name.equals("TRUE") || name.equals("FALSE");
        }
        return types.get(base) instanceof Info.Enum e && e.elements().contains(name);
    }

    /** An integer: [-]digits, [-]base_digits (base 2..16, the bits of a word), with or without L. */
    static long integer(String text) {
        String s = text;
        boolean minus = s.startsWith("-");
        if (minus || s.startsWith("+")) {
            s = s.substring(1);
        }
        if (s.endsWith("L")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty() || !Character.isDigit(s.charAt(0))) {
            throw new NumberFormatException(text);
        }
        int u = s.indexOf('_');
        long v;
        if (u < 0) {
            v = Long.parseLong(minus ? "-" + s : s);
            return v;
        }
        int radix = Integer.parseInt(s.substring(0, u));
        if (radix < 2 || radix > 16) {
            throw new NumberFormatException(text);
        }
        v = Long.parseUnsignedLong(s.substring(u + 1), radix);
        return minus ? -v : v;
    }

    /** A real number: [-]digits[.digits][(E|D|X)[+|-]digits]. */
    static double real(String text) {
        String s = text.strip();
        if (!s.matches("[-+]?[0-9]+(\\.[0-9]+)?([EeDdXx][-+]?[0-9]+)?")) {
            throw new IllegalArgumentException("not a real number: " + s);
        }
        return Double.parseDouble(s.replaceAll("[DdXx]", "E"));
    }

    /** A TEXT: "..." or W"...", or null for NIL. */
    static String text(String text) {
        String s = text.strip();
        if (s.equals("NIL")) {
            return null;
        }
        if (s.startsWith("\"") || s.startsWith("W\"")) {
            return quoted(s, '"');
        }
        throw new IllegalArgumentException("not a text: " + s);
    }

    /** The characters of the literal s, delimited by quote (and W before it), with its escapes. */
    private static String quoted(String s, char quote) {
        int start = s.startsWith("W") ? 2 : 1;
        if (s.length() < start + 1 || s.charAt(start - 1) != quote || s.charAt(s.length() - 1) != quote) {
            throw new IllegalArgumentException("not a literal: " + s);
        }
        StringBuilder b = new StringBuilder();
        int end = s.length() - 1;
        for (int i = start; i < end; ) {
            char c = s.charAt(i++);
            if (c == quote) {
                throw new IllegalArgumentException("not a literal: " + s);
            }
            if (c != '\\') {
                b.append(c);
                continue;
            }
            if (i >= end) {
                throw new IllegalArgumentException("not a literal: " + s);
            }
            char e = s.charAt(i++);
            switch (e) {
                case 'n' -> b.append('\n');
                case 't' -> b.append('\t');
                case 'r' -> b.append('\r');
                case 'f' -> b.append('\f');
                case '\\', '\'', '"' -> b.append(e);
                case 'x', 'u', 'U' -> {
                    int max = e == 'x' ? (start == 2 ? 4 : 2) : e == 'u' ? 4 : 6;
                    int k = i;
                    while (k < end && k - i < max && Character.digit(s.charAt(k), 16) >= 0) {
                        k++;
                    }
                    if (k == i) {
                        throw new IllegalArgumentException("not a literal: " + s);
                    }
                    b.appendCodePoint(Integer.parseInt(s.substring(i, k), 16));
                    i = k;
                }
                default -> {
                    if (e < '0' || e > '7' || i + 2 > end) {
                        throw new IllegalArgumentException("not a literal: " + s);
                    }
                    String oct = s.substring(i - 1, i + 2);
                    if (!oct.matches("[0-7]{3}")) {
                        throw new IllegalArgumentException("not a literal: " + s);
                    }
                    b.append((char) Integer.parseInt(oct, 8));
                    i += 2;
                }
            }
        }
        return b.toString();
    }

    /** The name after the last dot: Color.Red is Red. */
    private static String unqualified(String s) {
        return s.substring(s.lastIndexOf('.') + 1);
    }

    private static IllegalArgumentException bad(TypeTable types, String t, String s) {
        return new IllegalArgumentException("not a value of type " + types.show(t) + ": " + s);
    }
}
