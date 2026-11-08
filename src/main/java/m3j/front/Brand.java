// Java port of CM3's m3-sys/m3front/src/types/Brand.i3 and Brand.m3.
// Original: Copyright 1996-2000, Critical Mass, Inc.  All rights reserved.
// Distributed under the Critical Mass license in licenses/COPYRIGHT-CMASS.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.M3ID;
import m3j.middle.Target;

/** The brands of reference and object types. */
public final class Brand {
    private Brand() {}

    public static class T {
        public long origin = 0;
        public Type.T type = null;
        public Expr.T expr = null;
        public M3String.T val8 = null;
        public M3WString.T val32 = null;
        public T next = null;
        public boolean error = false;
    }

    private static M3Buf.T brand_buf = null;
    private static final T[] all_brands = new T[98];

    public static void reset() {
        T t, u;
        for (int i = 0; i <= all_brands.length - 1; i++) {
            t = all_brands[i];
            while ((t != null)) {
                u = t.next;
                t.next = null;
                t.error = false;
                t = u;
            }
            all_brands[i] = null;
        }
    }

    public static Brand.T parse() {
        T t = null;
        if ((Scanner.cur.token == Token.T.tBRANDED)) {
            t = new T();
            t.origin = Scanner.offset;
            Scanner.getToken(); // BRANDED
            if ((Token.ExprStart.contains(Scanner.cur.token))) {
                t.expr = Expr.parse();
            } else {
                t.expr = generateBrand();
            }
        }
        return t;
    }

    public static Brand.T new_(String txt) {
        T t = new T();
        t.origin = Scanner.offset;
        t.expr = TextExpr.new8(M3String.add(txt));
        return t;
    }

    private static Expr.T generateBrand() {
        // Port: Suffix = ARRAY BOOLEAN OF CHAR { 'M', 'I' }.
        char[] counter = new char[5];
        Module.getNextCounter(counter);

        // build the string
        if ((brand_buf == null)) {
            brand_buf = M3Buf.New();
        }
        M3ID.put(M3.ids, brand_buf, Module.name(null));
        M3Buf.putText(brand_buf, " # AuTo-BrAnD # ");
        M3Buf.putSub(brand_buf, counter);
        M3Buf.putChar(brand_buf, Module.isInterface() ? 'I' : 'M');

        return TextExpr.new8(M3String.add(M3Buf.toText(brand_buf)));
    }

    public static void check(Brand.T t, Type.T holder, long[] hash, M3.CheckState cs) {
        Expr.T e;
        long xx;
        if ((t == null)) {
            return;
        }
        t.type = holder;
        Expr.typeCheck(t.expr, cs);
        e = Expr.constValue(t.expr);
        if ((e == null)) {
            Error.msg("brand is not a constant");
        } else if (split8(e, t)) {
            t.expr = e;
            xx = M3String.hash(t.val8);
            hash[0] = hash[0] * 37 + xx;
            checkDuplicate(t, xx);
        } else if (split32(e, t)) {
            t.expr = e;
            xx = M3WString.hash(t.val32);
            hash[0] = hash[0] * 37 + xx;
            checkDuplicate(t, xx);
        } else {
            Error.msg("brand is not a TEXT constant");
        }
    }

    /**
     * Port: TextExpr.Split8 (e, t.val8); the VAR parameter is t.val8
     * itself, so it is copied back after the call.
     */
    private static boolean split8(Expr.T e, T t) {
        M3String.T[] val8 = { t.val8 };
        boolean b = TextExpr.split8(e, val8);
        t.val8 = val8[0];
        return b;
    }

    /**
     * Port: TextExpr.Split32 (e, t.val32); the VAR parameter is t.val32
     * itself, so it is copied back after the call.
     */
    private static boolean split32(Expr.T e, T t) {
        M3WString.T[] val32 = { t.val32 };
        boolean b = TextExpr.split32(e, val32);
        t.val32 = val32[0];
        return b;
    }

    private static void checkDuplicate(T t, long hash) {
        long cell = Math.floorMod(hash, (long) all_brands.length);
        T node = all_brands[(int) cell];
        while (true) {
            if ((node == null)) {
                // add an entry to the table
                t.next = all_brands[(int) cell];
                all_brands[(int) cell] = t;
                return;
            } else if ((node == t) || (node.type == t.type)) {
                // ok, this is a repeated check of an existing brand
                return;
            } else if ((node.val8 == t.val8) && (node.val32 == t.val32)) {
                if ((node.type.origin != t.type.origin)) {
                    // error, duplicate brand
                    duplicateError(t);
                    duplicateError(node);
                }
                return;
            }
            node = node.next;
        }
    }

    private static void duplicateError(T t) {
        long save = Scanner.offset;
        if (!t.error) {
            t.error = true;
            Scanner.offset = t.origin;
            Error.txt(toText(t), "duplicate brand");
            Scanner.offset = save;
        }
    }

    /**
     * Return the offset in the global constant pool of the
     * initialized structure containing the brand.
     */
    public static long compile(Brand.T t) {
        long offset = -1;
        long len, n_bytes;
        if ((t == null)) {
            // no brand
        } else if ((t.val8 != null)) {
            len = Target.Char.size * (M3String.length(t.val8) + 1);
            n_bytes = Math.floorDiv(len - Target.Char.size, Target.Int8.size);
            offset = Module.allocate(M3.target.Integer.size + len,
                                     M3.target.Integer.align, true, "brand");
            CG.init_intt(offset, M3.target.Integer.size, n_bytes, true);
            M3String.init_chars(offset + M3.target.Integer.size, t.val8, true);
        } else if ((t.val32 != null)) {
            len = Target.Word32.size * (M3WString.length(t.val32) + 1);
            n_bytes = Math.floorDiv(len - Target.Word32.size, Target.Int8.size);
            offset = Module.allocate(M3.target.Integer.size + len,
                                     M3.target.Integer.align, true, "brand");
            CG.init_intt(offset, M3.target.Integer.size, n_bytes, true);
            M3WString.init_chars(offset + M3.target.Integer.size, t.val32, true);
        }
        return offset;
    }

    public static void genFPrint(Brand.T t, M3.FPInfo x) {
        if ((t == null)) {
            // no brand info
        } else if ((t.val8 != null)) {
            M3Buf.putText(x.buf, "-BRAND8 ");
            M3Buf.putInt(x.buf, M3String.length(t.val8));
            M3Buf.putChar(x.buf, ' ');
            M3String.put(x.buf, t.val8);
        } else if ((t.val32 != null)) {
            M3Buf.putText(x.buf, "-BRAND16 ");
            // Even though the characters of this brand now occupy 32 bits, let's keep
            // this string "-BRAND16", to avoid altering fingerprints and undermining
            // pickles written earlier.
            M3Buf.putInt(x.buf, M3WString.length(t.val32));
            M3Buf.putChar(x.buf, ' ');
            M3WString.putLiteral(x.buf, t.val32);
        }
    }

    public static boolean equal(Brand.T a, Brand.T b) {
        if ((a == null) || (b == null)) {
            return (a == b);
        } else if ((a.val8 != null) && (b.val8 != null)) {
            return (a.val8 == b.val8);
        } else if ((a.val32 != null) && (b.val32 != null)) {
            return (a.val32 == b.val32);
        } else if ((a.val8 != null) && (b.val32 != null)) {
            return false;
        } else if ((a.val32 != null) && (b.val8 != null)) {
            return false;
        } else {
            return Expr.isEqual(a.expr, b.expr, null);
        }
    }

    public static String toText(Brand.T t) {
        String txt = null;
        if ((t == null)) {
            txt = null;
        } else if ((t.val8 != null)) {
            txt = M3String.toText(t.val8);
        } else if ((t.val32 != null)) {
            txt = M3WString.toLiteral(t.val32);
        }
        return txt;
    }
}
