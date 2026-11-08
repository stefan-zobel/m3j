// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import m3j.convert.Convert;
import m3j.fingerprint.Fingerprint;
import m3j.middle.M3Buf;
import m3j.middle.M3FP;
import m3j.middle.M3ID;
import m3j.middle.M3RT;
import m3j.middle.TFloat;
import m3j.middle.TInt;
import m3j.middle.TWord;
import m3j.middle.Target;
import m3j.middle.TargetMap;

/**
 * Java side of the m3middle test: reads cases from stdin and writes the
 * results of the ported TInt, TWord, TFloat, Convert, M3Buf, M3ID and
 * Target to stdout, in the format of the Modula-3 program in
 * tools/middump, which describes the operations and their arguments. The
 * state of M3ID and Target carries over from line to line. A checked
 * runtime error ends the program with exit status 1.
 */
public final class MidDump {
    private MidDump() {}

    private static final Target.State target = new Target.State();
    private static final TargetMap.State targetMap = new TargetMap.State();
    private static final M3RT.State m3rt = new M3RT.State();
    private static final M3ID.State ids = new M3ID.State();

    public static void main(String[] args) throws IOException {
        PrintStream out = new PrintStream(System.out, false, StandardCharsets.ISO_8859_1);
        ids.io = out;
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
            System.err.println("*** runtime error: " + e);
            System.exit(1);
        }
        out.flush();
    }

    /*------------------------------------------------------------ parsing ---*/

    private static char[] bytes(String hex) {
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

    private static int[] parseI(String hex) {
        char[] b = bytes(hex);
        if (b.length != TInt.NUMBER) {
            throw new IllegalArgumentException("not an Int: " + hex);
        }
        int[] r = TInt.newInt();
        for (int i = 0; i < r.length; i++) {
            r[i] = b[i];
        }
        return r;
    }

    private static long parseBits(String hex) {
        if (hex.length() != 16) {
            throw new IllegalArgumentException("not 64 bits: " + hex);
        }
        return Long.parseUnsignedLong(hex, 16);
    }

    private static Target.Precision parseP(String t) {
        return switch (t) {
            case "S" -> Target.Precision.Short;
            case "L" -> Target.Precision.Long;
            case "X" -> Target.Precision.Extended;
            default -> throw new IllegalArgumentException("precision " + t);
        };
    }

    private static Target.Float parseF(String t) {
        String[] p = t.split(":", -1);
        return new Target.Float(parseP(p[0]), Long.parseLong(p[2]),
                                Double.longBitsToDouble(parseBits(p[1])));
    }

    private static long n(String t) {
        return Long.parseLong(t);
    }

    /*----------------------------------------------------------- printing ---*/

    private static String hex2(int b) {
        return String.format("%02x", b);
    }

    private static String hexChars(char[] a, int start, int n) {
        if (n == 0) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(hex2(a[start + i]));
        }
        return sb.toString();
    }

    private static String hexText(String x) {
        if (x == null) {
            return "NIL";
        }
        return hexChars(x.toCharArray(), 0, x.length());
    }

    private static String fpHex(Fingerprint.T fp) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(hex2(fp.byteAt(i)));
        }
        return sb.toString();
    }

    private static String iText(int[] i) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < i.length; k++) {
            sb.append(hex2(i[k]));
        }
        return sb.toString();
    }

    private static String bitsText(long x) {
        return String.format("%016x", x);
    }

    private static final String[] PNames = {"S", "L", "X"};

    private static String fText(Target.Float f) {
        return PNames[f.pre.ordinal()] + ":" + bitsText(Double.doubleToRawLongBits(f.fraction))
            + ":" + f.exponent;
    }

    private static String b(boolean x) {
        return x ? "T" : "F";
    }

    private static String bi(boolean ok, int[] i) {
        return b(ok) + " " + iText(i);
    }

    private static String bf(boolean ok, Target.Float f) {
        return b(ok) + " " + fText(f);
    }

    private static String intTypeText(Target.Int_type x) {
        return x.cg_type.ordinal() + "," + x.size + "," + x.align + "," + x.pack + ","
            + x.bytes + "," + iText(x.min) + "," + iText(x.max);
    }

    private static String floatTypeText(Target.Float_type x) {
        return x.cg_type.ordinal() + "," + PNames[x.pre.ordinal()] + "," + x.size + ","
            + x.align + "," + x.bytes + "," + fText(x.min) + "," + fText(x.max);
    }

    private static String ccText(Target.CallingConvention cc) {
        if (cc == null) {
            return "NIL";
        }
        return cc.name + "," + cc.m3cg_id + "," + b(cc.args_left_to_right)
            + b(cc.results_on_left) + b(cc.standard_structs);
    }

    private static final String[] CCNames = {
        "__cdecl", "__stdcall", "C", "WINAPIV", "WINAPI",
        "CALLBACK", "APIENTRY", "APIPRIVATE", "PASCAL", "pascal", ""};

    private static final String[] M3RTNames = {
        "CL_marker", "CL_proc", "CL_frame", "CL_SIZE", "ED_uid", "ED_name", "ED_implicit",
        "ED_SIZE", "EX_class", "EX_outermost", "EX_end_of_list", "EX_pad", "EX_start",
        "EX_stop", "EX_excepts", "EX_offset", "EX_SIZE", "EA_exception", "EA_arg",
        "EA_module", "EA_line", "EA_pc", "EA_info0", "EA_info1", "EA_un_except",
        "EA_un_arg", "EA_SIZE", "EF_next", "EF_class", "EF_SIZE", "EF1_handles",
        "EF1_info", "EF1_jmpbuf", "EF1_SIZE", "EF2_handler", "EF2_frame", "EF2_info",
        "EF2_SIZE", "EF3_raises", "EF3_SIZE", "EF4_mutex", "EF4_SIZE", "TC_typecode",
        "TC_selfID", "TC_fp", "TC_traced", "TC_kind", "TC_link_state", "TC_dataAlignment",
        "TC_dataSize", "TC_type_map", "TC_gc_map", "TC_type_desc", "TC_initProc",
        "TC_brand", "TC_name", "TC_next", "TC_SIZE", "TC_ALIGN", "OTC_parentID",
        "OTC_linkProc", "OTC_dataOffset", "OTC_methodOffset", "OTC_methodSize",
        "OTC_defaultMethods", "OTC_parent", "OTC_SIZE", "ATC_nDimensions",
        "ATC_elementSize", "ATC_SIZE", "RV_lhs_id", "RV_rhs_id", "RV_SIZE", "OA_elt_ptr",
        "OA_sizes", "OA_size_0", "OA_size_1", "MI_file", "MI_type_cells",
        "MI_type_cell_ptrs", "MI_full_rev", "MI_part_rev", "MI_proc_info",
        "MI_try_scopes", "MI_var_map", "MI_gc_map", "MI_imports", "MI_link_state",
        "MI_binder", "MI_gc_flags", "MI_SIZE", "II_import", "II_binder", "II_next",
        "II_SIZE", "PI_proc", "PI_name", "PI_SIZE", "MUTEX_acquire", "MUTEX_release"};

    private static String targetText(boolean ok) {
        StringBuilder t = new StringBuilder(b(ok));
        if (!ok) {
            return t.toString();
        }
        t.append(" Address=").append(intTypeText(target.Address));
        t.append(" Integer=").append(intTypeText(target.Integer));
        t.append(" Word=").append(intTypeText(target.Word));
        t.append(" Longint=").append(intTypeText(target.Longint));
        t.append(" Long=").append(intTypeText(target.Long));
        t.append(" Longreal=").append(floatTypeText(target.Longreal));
        t.append(" Extended=").append(floatTypeText(target.Extended));
        t.append(" Atomic=");
        for (boolean x : target.Atomic_lock_free) {
            t.append(b(x));
        }
        t.append(" endian=").append(target.endian.ordinal());
        t.append(" packed=").append(b(target.Allow_packed_byte_aligned));
        t.append(" walker=").append(b(target.Has_stack_walker));
        t.append(" sigsetjmp=").append(b(target.Sigsetjmp));
        t.append(" setjmp=").append(target.Setjmp);
        t.append(" typenames=").append(b(target.Typenames));
        t.append(" default=").append(ccText(target.DefaultCall));
        for (String nm : CCNames) {
            t.append(" cc=").append(ccText(Target.findConvention(target, nm)));
        }
        for (int i = 0; i <= 8; i++) {
            t.append(" id=").append(ccText(Target.conventionFromID(target, i)));
        }
        t.append(" map=");
        for (int c = 0; c < Target.CGType.values().length; c++) {
            t.append(targetMap.CG_Align[c]).append(",")
             .append(targetMap.CG_Align_bytes[c]).append(",")
             .append(targetMap.CG_Size[c]).append(",")
             .append(targetMap.CG_Bytes[c]).append(";");
        }
        for (int i = 0; i <= 3; i++) {
            t.append(" wt=").append(intTypeText(targetMap.Word_types[i]));
            t.append(" it=").append(intTypeText(targetMap.Integer_types[i]));
        }
        for (Target.Float_type ft : targetMap.Float_types) {
            t.append(" ft=").append(floatTypeText(ft));
        }
        try {
            for (String nm : M3RTNames) {
                t.append(" ").append(nm).append("=").append(M3RT.State.class.getField(nm).getLong(m3rt));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return t.toString();
    }

    private static String charsText(char[] buf, int len) {
        if (len < 0) {
            return Integer.toString(len);
        }
        return len + " " + hexChars(buf, 0, len);
    }

    private static Convert.Style parseStyle(String s) {
        return Convert.Style.valueOf(s);
    }

    /*--------------------------------------------------------- operations ---*/

    private static String doLine(String line) {
        String[] fs = line.split(" ", -1);
        String op = fs[0];
        String a = fs.length > 1 ? fs[1] : "";
        String b = fs.length > 2 ? fs[2] : "";
        String c = fs.length > 3 ? fs[3] : "";
        String d = fs.length > 4 ? fs[4] : "";
        int[] i = TInt.newInt();
        int[] j = TInt.newInt();
        Target.Float f = TFloat.ZeroR.copy();
        boolean ok;
        switch (op) {
            case "ifi":
                ok = TInt.fromInt(n(a), i);
                return bi(ok, i);
            case "iti": {
                long[] x = {0};
                ok = TInt.toInt(parseI(a), x);
                return b(ok) + " " + x[0];
            }
            case "inew":
                ok = TInt.New(bytes(a), i);
                return bi(ok, i);
            case "iadd":
                ok = TInt.add(parseI(a), parseI(b), i);
                return bi(ok, i);
            case "isub":
                ok = TInt.subtract(parseI(a), parseI(b), i);
                return bi(ok, i);
            case "imul":
                ok = TInt.multiply(parseI(a), parseI(b), i);
                return bi(ok, i);
            case "idiv":
                ok = TInt.div(parseI(a), parseI(b), i);
                return bi(ok, i);
            case "imod":
                ok = TInt.mod(parseI(a), parseI(b), i);
                return bi(ok, i);
            case "iinc":
                i = parseI(a);
                ok = TInt.inc(i);
                return bi(ok, i);
            case "idec":
                i = parseI(a);
                ok = TInt.dec(i);
                return bi(ok, i);
            case "ineg":
                ok = TInt.negate(parseI(a), i);
                return bi(ok, i);
            case "iabs":
                ok = TInt.abs(parseI(a), i);
                return bi(ok, i);
            case "icmp":
                i = parseI(a);
                j = parseI(b);
                return b(TInt.EQ(i, j)) + b(TInt.LT(i, j)) + b(TInt.LE(i, j))
                    + b(TInt.NE(i, j)) + b(TInt.GT(i, j)) + b(TInt.GE(i, j));
            case "itxt":
                return TInt.toText(parseI(a));
            case "ichr": {
                char[] buf = new char[(int) n(b)];
                return charsText(buf, TInt.toChars(parseI(a), buf));
            }
            case "iub": {
                int[] ub = new int[9];
                int k = TInt.toUnsignedBytes(parseI(a), ub);
                StringBuilder sb = new StringBuilder(k + " ");
                for (int q = 0; q < k; q++) {
                    sb.append(hex2(ub[q]));
                }
                return sb.toString();
            }
            case "iext":
                ok = TInt.extend(parseI(a), (int) n(b), i);
                return bi(ok, i);

            case "wnew":
                ok = TWord.New(bytes(b), 0, bytes(b).length, (int) n(a), i);
                return bi(ok, i);
            case "wadd":
                TWord.add(parseI(a), parseI(b), i);
                return iText(i);
            case "wsub":
                TWord.subtract(parseI(a), parseI(b), i);
                return iText(i);
            case "wmul":
                TWord.multiply(parseI(a), parseI(b), i);
                return iText(i);
            case "wdiv":
                ok = TWord.div(parseI(a), parseI(b), i);
                return bi(ok, i);
            case "wmod":
                ok = TWord.mod(parseI(a), parseI(b), i);
                return bi(ok, i);
            case "wdm":
                TWord.divMod(parseI(a), parseI(b), i, j);
                return iText(i) + " " + iText(j);
            case "wcmp":
                i = parseI(a);
                j = parseI(b);
                return b(TWord.LT(i, j)) + b(TWord.LE(i, j)) + b(TWord.GT(i, j)) + b(TWord.GE(i, j));
            case "wand":
                TWord.and(parseI(a), parseI(b), i);
                return iText(i);
            case "wor":
                TWord.or(parseI(a), parseI(b), i);
                return iText(i);
            case "wxor":
                TWord.xor(parseI(a), parseI(b), i);
                return iText(i);
            case "wnot":
                TWord.not(parseI(a), i);
                return iText(i);
            case "wsh":
                TWord.shift(parseI(a), n(b), i);
                return iText(i);
            case "wls":
                TWord.leftShift(parseI(a), n(b), i);
                return iText(i);
            case "wrs":
                TWord.rightShift(parseI(a), n(b), i);
                return iText(i);
            case "wrot":
                TWord.rotate(parseI(a), n(b), n(c), i);
                return iText(i);
            case "wext":
                ok = TWord.extract(parseI(a), n(b), n(c), i);
                return bi(ok, i);
            case "wins":
                ok = TWord.insert(parseI(a), parseI(b), n(c), n(d), i);
                return bi(ok, i);
            case "wtr":
                ok = TWord.truncate(parseI(a), n(b), i);
                return bi(ok, i);
            case "wnb":
                TWord.nBitsOnRight(n(a), i);
                return iText(i);

            case "fnew":
                ok = TFloat.New(bytes(b), parseP(a), f);
                return bf(ok, f);
            case "fadd":
                ok = TFloat.add(parseF(a), parseF(b), f);
                return bf(ok, f);
            case "fsub":
                ok = TFloat.subtract(parseF(a), parseF(b), f);
                return bf(ok, f);
            case "fmul":
                ok = TFloat.multiply(parseF(a), parseF(b), f);
                return bf(ok, f);
            case "fdiv":
                ok = TFloat.divide(parseF(a), parseF(b), f);
                return bf(ok, f);
            case "fmod":
                ok = TFloat.mod(parseF(a), parseF(b), f);
                return bf(ok, f);
            case "fcmp": {
                Target.Float fa = parseF(a);
                Target.Float fb = parseF(b);
                return b(TFloat.EQ(fa, fb)) + b(TFloat.LT(fa, fb)) + b(TFloat.LE(fa, fb));
            }
            case "fff":
                ok = TFloat.floatF(parseF(a), parseP(b), f);
                return bf(ok, f);
            case "ffi":
                ok = TFloat.floatI(n(a), parseP(b), f);
                return bf(ok, f);
            case "frnd": {
                Target.Float fa = parseF(a);
                return TFloat.trunc(fa) + " " + TFloat.round(fa) + " " + TFloat.floor(fa)
                    + " " + TFloat.ceiling(fa);
            }
            case "fchr": {
                char[] buf = new char[(int) n(b)];
                return charsText(buf, TFloat.toChars(parseF(a), buf));
            }
            case "fbyt": {
                int[] fb = new int[16];
                int k = TFloat.toBytes(targetMap, parseF(a), fb);
                StringBuilder sb = new StringBuilder(k + " ");
                for (int q = 0; q < k; q++) {
                    sb.append(hex2(fb[q]));
                }
                return sb.toString();
            }
            case "ffb": {
                char[] cb = bytes(b);
                int[] fb = new int[cb.length];
                for (int q = 0; q < cb.length; q++) {
                    fb[q] = cb[q];
                }
                TFloat.fromBytes(targetMap, fb, parseP(a), f);
                return fText(f);
            }

            case "cfi":
            case "cfu": {
                char[] buf = new char[(int) n(d)];
                try {
                    int x;
                    if (op.equals("cfi")) {
                        x = Convert.fromInt(buf, n(a), (int) n(b), c.equals("T"));
                    } else {
                        x = Convert.fromUnsigned(buf, n(a), (int) n(b), c.equals("T"));
                    }
                    return charsText(buf, x);
                } catch (Convert.Failed e) {
                    return "Failed";
                }
            }
            case "cff":
            case "cfl":
            case "cfx": {
                char[] buf = new char[(int) n(d)];
                try {
                    int x;
                    if (op.equals("cff")) {
                        x = Convert.fromFloat(buf, Float.intBitsToFloat(Integer.parseUnsignedInt(c, 16)),
                                              (int) n(b), parseStyle(a));
                    } else if (op.equals("cfl")) {
                        x = Convert.fromLongFloat(buf, Double.longBitsToDouble(parseBits(c)),
                                                  (int) n(b), parseStyle(a));
                    } else {
                        x = Convert.fromExtended(buf, Double.longBitsToDouble(parseBits(c)),
                                                 (int) n(b), parseStyle(a));
                    }
                    return charsText(buf, x);
                } catch (Convert.Failed e) {
                    return "Failed";
                }
            }
            case "ctf": {
                char[] cs = bytes(a);
                try {
                    Convert.FloatResult r = Convert.toFloat(cs, 0, cs.length);
                    return String.format("%08x", Float.floatToRawIntBits(r.value())) + " " + r.used();
                } catch (Convert.Failed e) {
                    return "Failed";
                }
            }
            case "ctl":
            case "ctx": {
                char[] cs = bytes(a);
                try {
                    Convert.DoubleResult r = op.equals("ctl")
                        ? Convert.toLongFloat(cs, 0, cs.length)
                        : Convert.toExtended(cs, 0, cs.length);
                    return bitsText(Double.doubleToRawLongBits(r.value())) + " " + r.used();
                } catch (Convert.Failed e) {
                    return "Failed";
                }
            }

            case "bint": {
                M3Buf.T m = M3Buf.New();
                M3Buf.putInt(m, n(a));
                return M3Buf.toText(m);
            }
            case "bintt": {
                M3Buf.T m = M3Buf.New();
                M3Buf.putIntt(m, parseI(a));
                return M3Buf.toText(m);
            }
            case "bflt": {
                M3Buf.T m = M3Buf.New();
                M3Buf.putFloat(m, parseF(a));
                return M3Buf.toText(m);
            }
            case "btxt": {
                M3Buf.T m = M3Buf.New();
                M3Buf.putSubText(m, new String(bytes(a)), n(b), n(c));
                return hexText(M3Buf.toText(m));
            }
            case "bbig": {
                M3Buf.T m1 = M3Buf.New();
                M3Buf.T m2 = M3Buf.New();
                long nn = n(a);
                long seed = n(b);
                long k = 0;
                while (k < nn) {
                    int len = (int) Math.min(nn - k, Long.remainderUnsigned(k * seed, 700) + 1);
                    char[] piece = new char[len];
                    for (int q = 0; q < len; q++) {
                        piece[q] = (char) Long.remainderUnsigned((k + q) * seed + q, 256);
                    }
                    switch ((int) Long.remainderUnsigned(k, 3)) {
                        case 0 -> {
                            for (int q = 0; q < len; q++) {
                                M3Buf.putChar(m1, piece[q]);
                                M3Buf.putChar(m2, piece[q]);
                            }
                        }
                        case 1 -> {
                            M3Buf.putSub(m1, piece);
                            M3Buf.putSub(m2, piece);
                        }
                        default -> {
                            M3Buf.putText(m1, new String(piece));
                            M3Buf.putText(m2, new String(piece));
                        }
                    }
                    k += len;
                }
                Fingerprint.T fp = M3Buf.toFP(m1);
                String txt = M3Buf.toText(m2);
                return fpHex(fp) + " " + txt.length() + " " + fpHex(M3FP.fromText(txt));
            }

            case "dadd":
                return Integer.toString(M3ID.add(ids, new String(bytes(a)), (int) n(b)));
            case "dfs":
                return Integer.toString(M3ID.fromStr(ids, bytes(a), n(b)));
            case "dtxt":
                return hexText(M3ID.toText(ids, (int) n(a)));
            case "dcls":
                return Integer.toString(M3ID.getClass(ids, (int) n(a)));
            case "dhash":
                return Long.toString(M3ID.hash(ids, (int) n(a)));
            case "dlt":
                return b(M3ID.isLT(ids, (int) n(a), (int) n(b)));
            case "dadv":
                M3ID.advanceMarks(ids);
                return "";
            case "dmark":
                return b(M3ID.setMark(ids, (int) n(a)));
            case "dput": {
                M3Buf.T m = M3Buf.New();
                M3ID.put(ids, m, (int) n(a));
                return hexText(M3Buf.toText(m));
            }

            case "tinit": {
                Target.M3BackendMode_t mode = Target.M3BackendMode_t.valueOf(c);
                ok = Target.init(target, targetMap, m3rt, a, b, mode);
                return targetText(ok);
            }
            default:
                throw new IllegalArgumentException("unknown operation: " + op);
        }
    }
}
