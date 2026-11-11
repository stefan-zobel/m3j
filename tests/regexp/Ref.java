// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The reference of tools/regexp_test.py: the commands of a batch of
 * tests/regexp/src/Main.m3, carried out with java.util.regex directly.
 * Where Java itself finds a bad replacement only at a match, the result is
 * "nomatch" and the text when nothing matches.
 *
 * <p>"cnt K OPT LIM PAT TEXT START" gives the reads of characters that the
 * search for match K (from 0) makes, "cntm" those of a match of the whole
 * text, "cnts MAX" those of a split, "cntr ALL" those of a replacement.
 */
public final class Ref {

    static String show(String t) {
        StringBuilder sb = new StringBuilder("w");
        for (int i = 0; i < t.length(); i++) sb.append(String.format("%04x", (int) t.charAt(i)));
        return sb.toString();
    }

    static String wide(String t) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < t.length(); i += 4) sb.append((char) Integer.parseInt(t.substring(i, i + 4), 16));
        return sb.toString();
    }

    static int flags(int bits) {
        int f = 0;
        if ((bits & 1) != 0) f |= Pattern.CASE_INSENSITIVE;
        if ((bits & 2) != 0) f |= Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
        if ((bits & 4) != 0) f |= Pattern.MULTILINE;
        if ((bits & 8) != 0) f |= Pattern.DOTALL;
        if ((bits & 16) != 0) f |= Pattern.LITERAL;
        if ((bits & 32) != 0) f |= Pattern.COMMENTS;
        return f;
    }

    static String bounds(Matcher m) {
        StringBuilder sb = new StringBuilder();
        for (int g = 0; g <= m.groupCount(); g++) {
            if (g > 0) sb.append(',');
            sb.append(m.start(g)).append(':').append(m.end(g));
        }
        return sb.toString();
    }

    static final class Counting implements CharSequence {
        final String s;
        long count;

        Counting(String s) {
            this.s = s;
        }

        public char charAt(int i) {
            count++;
            return s.charAt(i);
        }

        public int length() {
            return s.length();
        }

        public CharSequence subSequence(int a, int b) {
            return s.subSequence(a, b);
        }

        public String toString() {
            return s;
        }
    }

    static String run(String[] a) {
        String op = a[0];
        if (op.equals("q")) {
            String t = wide(a[1]);
            return show(Pattern.quote(t)) + " " + show(Matcher.quoteReplacement(t));
        }
        int k = 0;
        if (op.equals("cnt")) {
            k = Integer.parseInt(a[1]);
            String[] b = new String[a.length - 1];
            b[0] = op;
            System.arraycopy(a, 2, b, 1, a.length - 2);
            a = b;
        }
        Pattern p;
        try {
            p = Pattern.compile(wide(a[3]), flags(Integer.parseInt(a[1])));
        } catch (PatternSyntaxException e) {
            String s = e.getDescription();
            if (e.getIndex() >= 0) s += " at index " + e.getIndex();
            return "error " + show(s);
        }
        switch (op) {
            case "c":
                return "ok " + p.matcher("").groupCount();
            case "g": {
                Integer n = p.namedGroups().get(wide(a[4]));
                return Integer.toString(n == null ? -1 : n);
            }
            case "f": {
                String t = wide(a[4]);
                int start = Integer.parseInt(a[5]);
                if (start > t.length()) return "none";
                Matcher m = p.matcher(t);
                if (!m.find(start)) return "none";
                StringBuilder sb = new StringBuilder(bounds(m));
                while (m.find()) sb.append(';').append(bounds(m));
                return sb.toString();
            }
            case "m": {
                Matcher m = p.matcher(wide(a[4]));
                return m.matches() ? bounds(m) : "none";
            }
            case "r": {
                String t = wide(a[4]);
                String r = wide(a[5]);
                boolean all = Integer.parseInt(a[6]) != 0;
                Matcher m = p.matcher(t);
                if (!m.find()) return "nomatch " + show(t);
                m.reset();
                try {
                    return show(all ? m.replaceAll(r) : m.replaceFirst(r));
                } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                    return "error";
                }
            }
            case "w": {
                Matcher m = p.matcher(wide(a[4]));
                boolean all = Integer.parseInt(a[5]) != 0;
                java.util.function.Function<java.util.regex.MatchResult, String> f =
                        r -> Matcher.quoteReplacement("<" + r.group() + ">");
                return show(all ? m.replaceAll(f) : m.replaceFirst(f));
            }
            case "s": {
                String[] parts = p.split(wide(a[4]), Integer.parseInt(a[5]));
                StringBuilder sb = new StringBuilder(Integer.toString(parts.length));
                for (String s : parts) sb.append(' ').append(show(s));
                return sb.toString();
            }
            case "cnt": {
                Counting c = new Counting(wide(a[4]));
                Matcher m = p.matcher(c);
                int start = Integer.parseInt(a[5]);
                for (int i = 0; i < k; i++) {
                    if (!(i == 0 ? m.find(start) : m.find())) return "none";
                }
                c.count = 0;
                boolean found = k == 0 ? m.find(start) : m.find();
                return c.count + (found ? " found" : " none");
            }
            case "cntm": {
                Counting c = new Counting(wide(a[4]));
                boolean found = p.matcher(c).matches();
                return c.count + (found ? " found" : " none");
            }
            case "cnts": {
                Counting c = new Counting(wide(a[4]));
                p.split(c, Integer.parseInt(a[5]));
                return count(c);
            }
            case "cntr": {
                Counting c = new Counting(wide(a[4]));
                Matcher m = p.matcher(c);
                if (Integer.parseInt(a[5]) != 0) m.replaceAll("x"); else m.replaceFirst("x");
                return count(c);
            }
            default:
                return "bad command";
        }
    }

    static String count(Counting c) {
        return Long.toString(c.count);
    }

    public static void main(String[] args) throws IOException {
        try (BufferedReader in = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.US_ASCII);
             PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(args[1]), StandardCharsets.US_ASCII))) {
            String line;
            while ((line = in.readLine()) != null) out.print(run(line.split(" ", -1)) + "\n");
        }
    }
}
