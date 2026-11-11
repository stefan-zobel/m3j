// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The regular expressions behind the module Regexp of m3j, a layer on
 * java.util.regex that the interface M3jRegexp binds.
 *
 * <p>The layer exists to bound the time of a match. The engine of the Java
 * platform backtracks, and some patterns take time that grows exponentially
 * with the text (bounded repetitions, nested ".*", back references); deep
 * repetitions of a group overflow the stack. The engine reads the text only
 * through {@link CharSequence#charAt}, so the text is wrapped in a sequence
 * that counts the reads and stops the match when a budget is spent. The
 * budget of a call is the limit of the pattern times the number of
 * characters it searches plus {@link #BASE}; a limit of 0 means none, and
 * the text is then passed bare. A spent budget and a stack overflow give
 * {@link #TOO_COMPLEX}, or null where a call returns a string.
 *
 * <p>An object is immutable and may be used by several threads; the matches
 * of one search go through a {@link RegexpScan}.
 */
public final class Regexp {

    /** The characters that each budget gets in addition. */
    static final long BASE = 100_000;

    /** The result of a match that went over its budget or the stack. */
    static final int[] TOO_COMPLEX = new int[0];

    /** The bits of the options, in the order of Regexp.Option. */
    private static final int IGNORE_CASE = 1, UNICODE = 2, MULTILINE = 4, DOTALL = 8, LITERAL = 16,
            COMMENTS = 32;

    private final Pattern pattern;
    private final String error;
    private final long limit;

    private Regexp(Pattern pattern, String error, long limit) {
        this.pattern = pattern;
        this.error = error;
        this.limit = limit;
    }

    /**
     * The pattern compiled with the options (bits as above), or an object
     * whose {@link #error} says why it is not a pattern.
     */
    public static Regexp compile(String pattern, int options, long limit) {
        int flags = 0;
        if ((options & IGNORE_CASE) != 0) flags |= Pattern.CASE_INSENSITIVE;
        if ((options & UNICODE) != 0) flags |= Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
        if ((options & MULTILINE) != 0) flags |= Pattern.MULTILINE;
        if ((options & DOTALL) != 0) flags |= Pattern.DOTALL;
        if ((options & LITERAL) != 0) flags |= Pattern.LITERAL;
        if ((options & COMMENTS) != 0) flags |= Pattern.COMMENTS;
        try {
            return new Regexp(Pattern.compile(pattern, flags), null, limit);
        } catch (PatternSyntaxException e) {
            String s = e.getDescription();
            if (e.getIndex() >= 0) s += " at index " + e.getIndex();
            return new Regexp(null, s, limit);
        } catch (StackOverflowError e) {
            return new Regexp(null, "the pattern is nested too deeply", limit);
        }
    }

    /** Why the pattern did not compile, or null. */
    public String error() {
        return error;
    }

    /** The number of capturing groups. */
    public int groups() {
        return pattern.matcher("").groupCount();
    }

    /** The number of the group of that name, or -1. */
    public int groupNumber(String name) {
        Integer n = pattern.namedGroups().get(name);
        return n == null ? -1 : n;
    }

    public static String quote(String text) {
        return Pattern.quote(text);
    }

    public static String quoteReplacement(String text) {
        return Matcher.quoteReplacement(text);
    }

    /** The pieces joined. */
    public static String concat(String[] pieces) {
        return String.join("", pieces);
    }

    /** A spent budget. */
    static final class Over extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Over() {
            super(null, null, false, false);
        }
    }

    static final Over OVER = new Over();

    /** A text that counts its reads. */
    static final class Chars implements CharSequence {
        final String s;
        long left;

        Chars(String s) {
            this.s = s;
        }

        @Override
        public char charAt(int i) {
            if (--left < 0) throw OVER;
            return s.charAt(i);
        }

        @Override
        public int length() {
            return s.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return s.subSequence(start, end);
        }

        @Override
        public String toString() {
            return s;
        }
    }

    /** The reads that a search of n characters may make. */
    long budget(int n) {
        long c = n + BASE;
        return limit > Long.MAX_VALUE / c ? Long.MAX_VALUE : limit * c;
    }

    /** The sequence to match on: text itself without a limit. */
    Chars chars(String text) {
        return limit == 0 ? null : new Chars(text);
    }

    Matcher matcher(String text, Chars chars) {
        return pattern.matcher(chars == null ? text : chars);
    }

    static int[] bounds(Matcher m) {
        int[] b = new int[2 * (m.groupCount() + 1)];
        for (int i = 0; i <= m.groupCount(); i++) {
            b[2 * i] = m.start(i);
            b[2 * i + 1] = m.end(i);
        }
        return b;
    }

    /**
     * The bounds of the groups if the whole text matches (start and end of
     * each, -1 for a group that took no part), null if it does not,
     * {@link #TOO_COMPLEX} if the match went over its budget.
     */
    public int[] fullMatch(String text) {
        Chars c = chars(text);
        if (c != null) c.left = budget(text.length());
        try {
            Matcher m = matcher(text, c);
            return m.matches() ? bounds(m) : null;
        } catch (Over | StackOverflowError e) {
            return TOO_COMPLEX;
        }
    }

    /** The matches in text from the index from on. */
    public RegexpScan scan(String text, int from) {
        return new RegexpScan(this, text, from);
    }

    /**
     * The pieces of a replacement: strings and the numbers of groups. "\c"
     * stands for c, "$n" for the group n, as many digits as name a group,
     * and "${name}" for the group of that name.
     */
    private List<Object> pieces(String r) {
        List<Object> out = new ArrayList<>();
        Map<String, Integer> names = pattern.namedGroups();
        int groups = groups();
        StringBuilder lit = new StringBuilder();
        int n = r.length();
        int i = 0;
        while (i < n) {
            char c = r.charAt(i);
            if (c == '\\') {
                if (i + 1 == n) throw new IllegalArgumentException("a \"\\\" at the end at index " + i);
                lit.append(r.charAt(i + 1));
                i += 2;
            } else if (c == '$') {
                int at = i;
                i++;
                if (i == n) throw new IllegalArgumentException("a \"$\" at the end at index " + at);
                int g;
                if (r.charAt(i) == '{') {
                    int start = ++i;
                    while (i < n && isAsciiLetterOrDigit(r.charAt(i))) i++;
                    if (i == start) throw new IllegalArgumentException("a group name is missing at index " + at);
                    if (i == n || r.charAt(i) != '}') {
                        throw new IllegalArgumentException("a \"}\" is missing at index " + at);
                    }
                    String name = r.substring(start, i);
                    i++;
                    if (name.charAt(0) <= '9') {
                        throw new IllegalArgumentException("a group name starts with a digit at index " + at);
                    }
                    Integer k = names.get(name);
                    if (k == null) throw new IllegalArgumentException("no group " + name + " at index " + at);
                    g = k;
                } else {
                    int d = r.charAt(i) - '0';
                    if (d < 0 || d > 9) throw new IllegalArgumentException("a group is missing at index " + at);
                    if (d > groups) throw new IllegalArgumentException("no group " + d + " at index " + at);
                    g = d;
                    i++;
                    while (i < n) {
                        d = r.charAt(i) - '0';
                        if (d < 0 || d > 9 || g * 10 + d > groups) break;
                        g = g * 10 + d;
                        i++;
                    }
                }
                if (lit.length() > 0) {
                    out.add(lit.toString());
                    lit.setLength(0);
                }
                out.add(g);
            } else {
                lit.append(c);
                i++;
            }
        }
        if (lit.length() > 0) out.add(lit.toString());
        return out;
    }

    private static boolean isAsciiLetterOrDigit(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9';
    }

    /** Why replacement is not a replacement for this pattern, or null. */
    public String checkReplacement(String replacement) {
        try {
            pieces(replacement);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    /**
     * text with the first match or all replaced, null if a match went over
     * the budget of the call (for the whole text). The replacement must
     * pass {@link #checkReplacement}.
     */
    public String replace(String text, String replacement, boolean all) {
        List<Object> pieces = pieces(replacement);
        Chars c = chars(text);
        if (c != null) c.left = budget(text.length());
        try {
            Matcher m = matcher(text, c);
            if (!m.find()) return text;
            StringBuilder sb = new StringBuilder();
            int last = 0;
            do {
                sb.append(text, last, m.start());
                for (Object p : pieces) {
                    if (p instanceof String s) {
                        sb.append(s);
                    } else {
                        int g = (Integer) p;
                        if (m.start(g) >= 0) sb.append(text, m.start(g), m.end(g));
                    }
                }
                last = m.end();
            } while (all && m.find());
            sb.append(text, last, text.length());
            return sb.toString();
        } catch (Over | StackOverflowError e) {
            return null;
        }
    }

    /**
     * text split around the matches as by {@link Pattern#split(CharSequence,
     * int)}, null if a match went over the budget of the call.
     */
    public String[] split(String text, int max) {
        Chars c = chars(text);
        if (c != null) c.left = budget(text.length());
        try {
            return pattern.split(c == null ? text : c, max);
        } catch (Over | StackOverflowError e) {
            return null;
        }
    }
}
