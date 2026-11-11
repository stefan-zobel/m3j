// Java port of CM3's m3-sys/m3front/src/exprs/TextExpr.i3 and TextExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.M3CG;
import m3j.middle.M3RT;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: TextExpr.i3
// Last Modified On Fri Jun 24 08:48:56 PDT 1994 By kalsow

// File: TextExpr.m3
// Last modified on Fri Feb 24 16:47:35 PST 1995 by kalsow
//      modified on Sun Feb 24 04:07:17 1991 by muller

public final class TextExpr {
    private TextExpr() {}

    public static final class P extends Expr.T {
        public M3String.T value8;
        public M3WString.T value32;
        /**
         * m3j: for a text that depends on the system, its values on POSIX and
         * on WIN32 (value8 and value32 are the one of the system that the
         * compiler runs on); null for every other text. The texts made of
         * OSConfig.LineSep, such as Wr.EOL, are such: a program of m3j runs
         * on either system, and the back end takes the value of the system
         * that it runs on.
         */
        public P posix, win32;

        @Override
        public void compile(boolean StaticOnly) {
            TextExpr.compile(this, StaticOnly);
        }

        @Override
        public Expr.T evaluate() {
            return ExprRep.self(this);
        }

        @Override
        public boolean isEqual(Expr.T e, Type.Assumption x) {
            return eqCheck(this, e, x);
        }

        @Override
        public boolean isZeroes(boolean lhs) {
            return TextExpr.isZeroes(this, lhs);
        }

        @Override
        public void genFPLiteral(M3Buf.T buf) {
            TextExpr.genFPLiteral(this, buf);
        }

        @Override
        public void prepLiteral(Type.T type, boolean is_const) {
            TextExpr.prepLiteral(this, type, is_const);
        }

        @Override
        public void genLiteral(long offset, Type.T type, boolean is_const) {
            TextExpr.genLiteral(this, offset, type, is_const);
        }

        @Override
        public long exprAlign() {
            return ExprRep.exprAddrAlign(this);
        }

        // Port: the overrides typeOf, repTypeOf := ExprRep.NoType, check :=
        // ExprRep.NoCheck, need_addr := ExprRep.NotAddressable, prep :=
        // ExprRep.NoPrep, prepLV := ExprRep.NotLValue, compileLV :=
        // ExprRep.NotLValueBool, prepBR, compileBR := ExprRep.NotBoolean,
        // getBounds := ExprRep.NoBounds, isWritable, isDesignator :=
        // ExprRep.IsNever and note_write := ExprRep.NotWritable restate the
        // inherited methods.
    }

    // NOTE! These UIDs have nothing to do with the UIDs that are hashes
    //       and used many places.

    private static long nextUID = 0;

    // Port: LiteralTable = REF ARRAY OF INTEGER is long[].
    // LiteralTable[uid] is the offset w/in the global constant area where
    // the uid-th literal is stored.

    private static M3CG.Var globalConstsCGVar = null;
    private static long[] literals = null;
    /**
     * ^Offset w/in global constant area of a list of addresses of
     * TextLiteral.T's overrides of Text.T methods.
     */
    private static long methodListOffset = -1;

    public static void reset() {
        nextUID = 0;
        globalConstsCGVar = null;
        methodListOffset = -1;
        // literals := NIL;
        if ((literals != null)) {
            for (int i = 0; i <= literals.length - 1; i++) {
                literals[i] = 0;
            }
        }
    }

    public static Expr.T new8(M3String.T value) {
        P p = new P();
        ExprRep.init(p);
        p.value8 = value;
        p.value32 = null;
        p.type = Textt.T;
        p.repType = Textt.T;
        p.checked = true;
        return p;
    }

    public static Expr.T new32(M3WString.T value) {
        P p = new P();
        ExprRep.init(p);
        p.value8 = null;
        p.value32 = value;
        p.type = Textt.T;
        p.repType = Textt.T;
        p.checked = true;
        return p;
    }

    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        if (e instanceof P b) {
            return (a.value8 == b.value8) && (a.value32 == b.value32);
        } else {
            return false;
        }
    }

    private static long setUID(P p) {
        long Header_offset = 0;
        long Method_offset = Header_offset + M3.target.Address.pack;
        long Length_offset = Method_offset + M3.target.Address.pack;
        long Chars_offset = Length_offset + M3.target.Integer.pack;
        long uid;
        long len;
        long x;
        long width;
        long cnt;
        // assign this value a unique ID
        if (p.value8 != null) {
            width = Target.Char.size;
            len = M3String.length(p.value8);
            cnt = len;
            uid = M3String.getUID(p.value8);
            if (uid == M3String.NO_UID) {
                uid = nextUID;
                nextUID++;
                M3String.setUID(p.value8, uid);
            }
        } else {
            width = Target.wideCharSize(M3.target);
            len = M3WString.length(p.value32);
            cnt = -len;
            uid = M3WString.getUID(p.value32);
            if (uid == M3WString.NO_UID) {
                uid = nextUID;
                nextUID++;
                M3WString.setUID(p.value32, uid);
            }
        }

        // make sure there's room in the table
        if ((literals == null) || (literals.length - 1 < uid)) {
            expandLiterals();
        }

        x = literals[(int) uid];
        if ((x != 0)) {
            return uid;
        }

        if ((globalConstsCGVar == null)) {
            globalConstsCGVar = Module.globalData(/* is_const := */ true);
            methodListOffset = buildMethodList();
        }

        // allocate the variable with room for the trailing null character
        x = Module.allocate(Chars_offset + (len + 1) * width,
                            M3.target.Address.align, true, "*TEXT literal*");
        literals[(int) uid] = x;

        // initialize the variable
        //
        // CG.Init_intt (x+Header_offset + M3RT.RH_typecode_offset,
        //               M3RT.RH_typecode_size, M3RT.TEXT_typecode, is_const := TRUE);
        // Write an entire word instead for endian neutrality
        //
        // Port: Word.Shift by the positive RH_typecode_offset is a left shift.
        CG.init_intt(x + Header_offset,
                     M3.target.Integer.pack,
                     M3RT.TEXT_typecode << M3RT.RH_typecode_offset,
                     /* is_const := */ true);
        CG.init_var(x + Method_offset, globalConstsCGVar, methodListOffset,
                    /* is_const := */ true);
        CG.init_intt(x + Length_offset, M3.target.Integer.size, cnt,
                     /* is_const := */ true);
        if ((p.value8 != null)) {
            M3String.init_chars(x + Chars_offset, p.value8, true);
        } else {
            M3WString.init_chars(x + Chars_offset, p.value32, true);
        }

        return uid;
    }

    private static long buildMethodList() {
        // Port: Methods = [RunTyme.Hook.TextLitInfo ..
        // RunTyme.Hook.TextLitGetWideChars] is the range of ordinals
        // [first .. last].
        final int first = RunTyme.Hook.TextLitInfo.ordinal();
        final int last = RunTyme.Hook.TextLitGetWideChars.ordinal();
        long offs;
        if (methodListOffset >= 0) {
            return methodListOffset;
        }

        methodListOffset = Module.allocate((last - first + 1) * M3.target.Address.size,
                                           M3.target.Address.align, true, "TEXT literal methods");
        offs = methodListOffset;
        for (int i = first; i <= last; i++) {
            CG.init_proc(offs, Procedure.cgName(RunTyme.lookUpProc(RunTyme.Hook.values()[i])), true);
            offs += M3.target.Address.size;
        }

        return methodListOffset;
    }

    private static void expandLiterals() {
        long[] new_;
        if ((literals == null)) {
            new_ = new long[200];
        } else {
            new_ = new long[2 * literals.length];
            System.arraycopy(literals, 0, new_, 0, literals.length);
        }
        literals = new_;
    }

    private static void compile(P p, boolean StaticOnly) {
        long uid = setUID(p);
        AssertError.check(!StaticOnly, "TextExpr");
        // NOTE^ If ever duplicate text constants are removed as for arrays, this
        //  will need to change.
        CG.load_addr_of(globalConstsCGVar, literals[(int) uid] + M3.target.Address.pack,
                        M3.target.Address.align);
    }

    /** Port: VAR value is {@code value[0]}. */
    public static boolean split8(Expr.T e, M3String.T[] value) {
        if (e instanceof P p) {
            value[0] = p.value8;
            return (p.value8 != null);
        } else {
            return false;
        }
    }

    /** Port: VAR value is {@code value[0]}. */
    public static boolean split32(Expr.T e, M3WString.T[] value) {
        if (e instanceof P p) {
            value[0] = p.value32;
            return (p.value32 != null);
        } else {
            return false;
        }
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean cat(Expr.T a, Expr.T b, Expr.T[] c) {
        M3String.T sa, sb;
        M3WString.T wa, wb;
        if (a instanceof P p) {
            sa = p.value8;
            wa = p.value32;
        } else {
            return false;
        }
        if (b instanceof P q) {
            sb = q.value8;
            wb = q.value32;
        } else {
            return false;
        }
        if ((sa != null) && (sb != null)) {
            c[0] = new8(M3String.concat(sa, sb));
        } else if ((wa != null) && (wb != null)) {
            c[0] = new32(M3WString.concat(wa, wb));
        } else if ((sa != null)) { // wb # NIL
            c[0] = new32(M3WString.concat(M3WString.add(M3String.toText(sa)), wb));
        } else { // wa # NIL  AND  sb # NIL
            c[0] = new32(M3WString.concat(wa, M3WString.add(M3String.toText(sb))));
        }
        // m3j: the values on each system of a text that depends on it
        P pa = (P) a, pb = (P) b;
        if (pa.posix != null || pb.posix != null) {
            Expr.T[] x = new Expr.T[1], y = new Expr.T[1];
            cat(pa.posix != null ? pa.posix : pa, pb.posix != null ? pb.posix : pb, x);
            cat(pa.win32 != null ? pa.win32 : pa, pb.win32 != null ? pb.win32 : pb, y);
            ((P) c[0]).posix = (P) x[0];
            ((P) c[0]).win32 = (P) y[0];
        }
        return true;
    }

    /**
     * m3j: the text t as a text that depends on the system, with the values
     * posix and win32 there; t keeps its value.
     */
    public static Expr.T onSystem(P t, M3String.T posix, M3String.T win32) {
        P r = (P) (t.value8 != null ? new8(t.value8) : new32(t.value32));
        r.posix = (P) new8(posix);
        r.win32 = (P) new8(win32);
        return r;
    }

    private static boolean isZeroes(P p, boolean lhs) {
        return false;
    }

    private static void genFPLiteral(P p, M3Buf.T buf) {
        if ((p.value8 != null)) {
            M3Buf.putText(buf, "TEXT8<");
            M3Buf.putInt(buf, M3String.length(p.value8));
            M3Buf.putChar(buf, ',');
            M3String.put(buf, p.value8);
            M3Buf.putChar(buf, '>');
        } else {
            M3Buf.putText(buf, "TEXT16<");
            // ^Even though the characters of a literal now could occupy 32 bits,
            //  let's keep this string "TEXT16<", to avoid altering fingerprints and
            //  undermining pickles written earlier.
            M3Buf.putInt(buf, M3WString.length(p.value32));
            M3Buf.putChar(buf, ',');
            M3WString.putLiteral(buf, p.value32);
            M3Buf.putChar(buf, '>');
        }
    }

    private static void prepLiteral(P p, Type.T type, boolean is_const) {
        setUID(p);
    }

    /**
     * Put a pointer, at 'offset' w/in the 'is_const' static area, to the
     * literal 'p', (whose value is located in the global constant area)
     */
    private static void genLiteral(P p, long offset, Type.T type,
                                   boolean is_const) {
        long uid = setUID(p);
        CG.init_var(offset, globalConstsCGVar, literals[(int) uid] + M3.target.Address.pack,
                    is_const);
    }
}
