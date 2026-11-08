// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import m3j.fingerprint.Fingerprint;
import m3j.fingerprint.Poly;
import m3j.middle.M3FP;

/**
 * Java side of the fingerprint test: reads cases from stdin and writes the
 * results of the ported Fingerprint, M3FP and Poly to stdout, in the format
 * of the Modula-3 program in tools/fpdump.
 *
 * <p>Each input line is an operation code followed by arguments separated
 * by single blanks. HEX is a sequence of bytes as pairs of hexadecimal
 * digits, or "-" for no bytes; INT is a decimal integer.
 * <pre>
 *   t HEX        Fingerprint.FromText
 *   c HEX HEX    Fingerprint.Combine of the texts' fingerprints
 *   m HEX HEX    M3FP.Combine of the texts' fingerprints
 *   f HEX HEX    Fingerprint.FromChars (chars2, FromText (text1))
 *   x HEX INT    M3FP.ExtendByInt (FromText (text), INT)
 *   n INT        M3FP.FromInt
 *   p INT        Poly.Power
 *   r HEX HEX    Poly.Product of Poly.FromBytes of two 8-byte values
 * </pre>
 * Every output line starts with the input line, followed by " =&gt; " and
 * the result. A fingerprint is written as its 8 bytes in hexadecimal,
 * byte[0] first. A checked runtime error ends the program with exit
 * status 1.
 */
public final class FpDump {
    private FpDump() {}

    public static void main(String[] args) throws IOException {
        PrintStream out = new PrintStream(System.out, false, StandardCharsets.ISO_8859_1);
        BufferedReader in = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.ISO_8859_1));
        String line;
        try {
            while ((line = in.readLine()) != null) {
                out.print(line + " => " + doLine(line) + "\n");
                out.flush();
            }
        } catch (RuntimeException e) {
            out.flush();
            System.err.println("*** runtime error: " + e.getMessage());
            System.exit(1);
        }
        out.flush();
    }

    private static String doLine(String line) {
        String[] f = line.split(" ", -1);
        String op = f[0];
        String a = f.length > 1 ? f[1] : "";
        String b = f.length > 2 ? f[2] : "";
        switch (op) {
            case "t": {
                Fingerprint.T fp = fp(a);
                char[] buf = new char[M3FP.CharBufLength];
                M3FP.toChars(fp, buf);
                return fpText(fp) + " " + new String(buf)
                    + " " + M3FP.toInt(fp) + " " + Fingerprint.hash(fp);
            }
            case "c":
                return fpText(Fingerprint.combine(fp(a), fp(b)));
            case "m":
                return fpText(M3FP.combine(fp(a), fp(b)));
            case "f":
                return fpText(Fingerprint.fromChars(chars(b), fp(a)));
            case "x":
                return fpText(M3FP.extendByInt(fp(a), Integer.parseInt(b)));
            case "n":
                return fpText(M3FP.fromInt(Long.parseLong(a)));
            case "p":
                return polyText(Poly.power(Integer.parseInt(a)));
            case "r":
                return polyText(Poly.product(polyOf(a), polyOf(b)));
            default:
                throw new IllegalArgumentException("unknown operation: " + op);
        }
    }

    private static char[] chars(String hex) {
        if (hex.equals("-")) {
            return new char[0];
        }
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("odd number of hex digits: " + hex);
        }
        char[] r = new char[hex.length() / 2];
        for (int i = 0; i < r.length; i++) {
            r[i] = (char) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return r;
    }

    private static Fingerprint.T fp(String hex) {
        return Fingerprint.fromText(new String(chars(hex)));
    }

    private static String fpText(Fingerprint.T fp) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(String.format("%02x", fp.byteAt(i)));
        }
        return sb.toString();
    }

    private static String polyText(int[] p) {
        return p[0] + " " + p[1];
    }

    private static int[] polyOf(String hex) {
        char[] c = chars(hex);
        if (c.length != 8) {
            throw new IllegalArgumentException("a polynomial needs 8 bytes: " + hex);
        }
        int[] bytes = new int[8];
        for (int i = 0; i < 8; i++) {
            bytes[i] = c[i];
        }
        return Poly.fromBytes(bytes);
    }
}
