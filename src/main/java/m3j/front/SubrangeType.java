// Java port of CM3's m3-sys/m3front/src/types/SubrangeType.i3 and SubrangeType.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.TInt;
import m3j.middle.TWord;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: SubrangeType.m3
// Last modified on Tue May 23 15:28:34 PDT 1995 by kalsow
//      modified on Thu Jan 31 23:22:08 1991 by muller

public final class SubrangeType {
    private SubrangeType() {}

    public static final class P extends Type.T {
        public Type.T baseType;
        public Expr.T minE, maxE;
        public int[] min = TInt.newInt(), max = TInt.newInt();
        public CGType rep;
        /** => CARDINAL */
        public boolean builtin;
        public boolean sealed;

        @Override
        public void check() {
            SubrangeType.check(this);
        }

        @Override
        public boolean no_straddle(long offset, boolean IsEltOrField) {
            return noStraddle(this, offset, IsEltOrField);
        }

        @Override
        public boolean isEqual(Type.T t, Type.Assumption x) {
            return equalChk(this, t, x);
        }

        @Override
        public boolean isSubtype(Type.T t) {
            return subtyper(this, t);
        }

        @Override
        public void compile() {
            compiler(this);
        }

        @Override
        public long initCost(boolean zeroed) {
            return initCoster(this, zeroed);
        }

        @Override
        public void initValue(boolean zeroed) {
            genInit(this, zeroed);
        }

        @Override
        public void mapper(long offset, long size, boolean refs) {
            genMap(this, offset, size, refs);
        }

        @Override
        public void gen_desc() {
            genDesc(this);
        }

        @Override
        public void fprint(M3.FPInfo x) {
            fPrinter(this, x);
        }
    }

    public static Type.T parse() {
        P p = (P) new_(TInt.Zero, TInt.MOne, null, false);
        Scanner.match(Token.T.tLBRACKET);
        p.minE = Expr.parse();
        Scanner.match(Token.T.tDOTDOT);
        p.maxE = Expr.parse();
        Scanner.match(Token.T.tRBRACKET);
        return p;
    }

    public static Type.T new_(int[] min, int[] max, Type.T base, boolean builtin) {
        P p;
        p = new P();
        TypeRep.init(p, Type.Class.Subrange);
        p.baseType = base;
        TInt.assign(p.min, min);
        TInt.assign(p.max, max);
        p.builtin = builtin;
        return p;
    }

    private static P reduce(Type.T t) {
        t = Type.strip(t); // StripPacked?
        if ((t == null) || (t.info.class_ != Type.Class.Subrange)) {
            return null;
        }
        return (P) t;
    }

    /** Port: min and max are written. */
    public static boolean split(Type.T t, int[] min, int[] max) {
        P p = reduce(t);
        if ((p == null)) {
            return false;
        }
        TInt.assign(min, p.min);
        TInt.assign(max, p.max);
        return true;
    }

    private static void setRep(P p) {
        if (TInt.LT(p.max, p.min)) {
            TInt.assign(p.min, TInt.Zero);
            TInt.assign(p.max, TInt.MOne);
            if (Type.isSubtype(p.baseType, LInt.T)) {
                p.rep = M3.target.Longint.cg_type;
            } else {
                p.rep = M3.target.Integer.cg_type;
            }
            return;
        }

        if (TInt.LE(TInt.Zero, p.min)) {
            // look for an unsigned type
            for (int i = 0; i < M3.targetMap.Word_types.length; i++) {
                Target.Int_type z = M3.targetMap.Word_types[i];
                if (TWord.LE(p.max, z.max)) {
                    p.rep = z.cg_type;
                    return;
                }
            }
        } else {
            // look for a signed type
            for (int i = 0; i < M3.targetMap.Integer_types.length; i++) {
                Target.Int_type z = M3.targetMap.Integer_types[i];
                if (TInt.LE(z.min, p.min) && TInt.LE(p.max, z.max)) {
                    p.rep = z.cg_type;
                    return;
                }
            }
        }
    }

    private static void seal(P p) {
        Expr.T emin, emax;
        Type.T[] tmin = new Type.T[1], tmax = new Type.T[1];
        if ((p.sealed)) {
            return;
        }
        if ((p.minE != null)) {
            emin = Expr.constValue(p.minE);
            if ((emin == null)) {
                Error.msg("subrange lower bound is not constant");
                TInt.assign(p.min, TInt.Zero);
                tmin[0] = Int.T;
            } else if (IntegerExpr.split(emin, p.min, tmin)) {
                // ok
            } else if (EnumExpr.split(emin, p.min, tmin)) {
                // ok
            } else {
                Error.msg("subrange lower bound is not an ordinal value");
                TInt.assign(p.min, TInt.Zero);
                tmin[0] = Int.T;
            }

            emax = Expr.constValue(p.maxE);
            if ((emax == null)) {
                Error.msg("subrange upper bound is not constant");
                TInt.assign(p.max, p.min);
                tmax[0] = tmin[0];
            } else if (IntegerExpr.split(emax, p.max, tmax)) {
                // ok
            } else if (EnumExpr.split(emax, p.max, tmax)) {
                // ok
            } else {
                Error.msg("subrange upper bound is not an ordinal value");
                TInt.assign(p.max, p.min);
                tmax[0] = tmin[0];
            }

            p.baseType = tmin[0];
            if (!Type.isEqual(tmin[0], tmax[0], null)) {
                Error.msg("subrange endpoints must be of same type");
            }
        }

        setRep(p);
        p.info.size = M3.targetMap.CG_Size[p.rep.ordinal()];
        p.sealed = true;
    }

    private static void check(P p) {
        long hash;
        M3.CheckState cs = M3.OuterCheckState.copy();
        long[] i = new long[1];
        Type.Info baseInfo = new Type.Info();
        seal(p);
        Expr.typeCheck(p.minE, cs);
        Expr.typeCheck(p.maxE, cs);
        p.baseType = Type.checkInfo(p.baseType, baseInfo);

        hash = baseInfo.hash;
        if (!TInt.toInt(p.min, i)) {
            i[0] = 19;
        }
        hash = hash * 487 + i[0];
        if (!TInt.toInt(p.max, i)) {
            i[0] = 23;
        }
        hash = hash * 487 + i[0];

        AssertError.check(p.info.size == M3.targetMap.CG_Size[p.rep.ordinal()], "SubrangeType");
        p.info.min_size = minSize(p);
        p.info.alignment = M3.targetMap.CG_Align[p.rep.ordinal()];
        p.info.mem_type = p.rep;
        if (Type.isSubtype(p.baseType, LInt.T)) {
            if (Target.SignedType[p.rep.ordinal()]) {
                p.info.stk_type = M3.target.Longint.cg_type;
            } else {
                p.info.stk_type = M3.target.Long.cg_type;
            }
        } else {
            if (Target.SignedType[p.rep.ordinal()]) {
                p.info.stk_type = M3.target.Integer.cg_type;
            } else {
                p.info.stk_type = M3.target.Word.cg_type;
            }
        }
        p.info.class_ = Type.Class.Subrange;
        p.info.isTraced = false;
        p.info.isSolid = true;
        p.info.isEmpty = TInt.LT(p.max, p.min);
        p.info.hash = hash;
    }

    private static boolean noStraddle(P p, long offset, boolean IsEltOrField) {
        // Port: IsEltOrField is <*UNUSED*>.
        long sz = M3.targetMap.CG_Size[p.rep.ordinal()];
        long z0;
        if (p.info.lazyAligned) {
            z0 = Math.floorDiv(offset, Target.Byte) * Target.Byte;
        } else {
            z0 = Math.floorDiv(offset, M3.target.Integer.align) * M3.target.Integer.align;
        }
        return (offset + sz) <= (z0 + M3.target.Integer.size);
    }

    private static void compiler(P p) {
        Type.compile(p.baseType);
        // Port: CG.TypeUID is int.
        CG.declare_subrange((int) Type.globalUID(p), (int) Type.globalUID(p.baseType),
                            p.min, p.max, M3.targetMap.CG_Size[p.rep.ordinal()]);
    }

    public static Type.T base(Type.T t) {
        P p = (P) t;
        seal(p);
        t = p.baseType;
        if ((t == null)) {
            t = Expr.typeOf(p.minE);
        }
        return t;
    }

    private static boolean equalChk(P a, Type.T t, Type.Assumption x) {
        P b = (P) t;
        seal(a);
        seal(b);
        return (a.builtin == b.builtin)
            && TInt.EQ(a.min, b.min)
            && TInt.EQ(a.max, b.max)
            && Type.isEqual(a.baseType, b.baseType, x);
    }

    private static boolean subtyper(P a, Type.T t) {
        P b;
        Type.T aBase, tBase;
        seal(a);
        aBase = Type.base(a.baseType);
        tBase = Type.base(t);
        if (Type.isEqual(aBase, tBase, null)) {
            if (t.info.class_ != Type.Class.Subrange) {
                return true;
            }
            // Bases are equal, both are subranges.
        } else if (Type.isEqual(aBase, Charr.T, null)) {
            if (Type.isEqual(t, WCharr.T, null)) {
                return true;
            } else if (!Type.isEqual(tBase, WCharr.T, null)) {
                return false;
            }
            // Lhs subrange of CHAR, Rhs subrange of WIDECHAR.
        } else if (!Type.isEqual(aBase, WCharr.T, null)
                   || !Type.isEqual(tBase, Charr.T, null)) {
            return false;
        } else if (Type.isEqual(t, Charr.T, null)) {
            return TInt.LE(a.max, TWord.Max8);
            // Lhs subrange of WIDECHAR, Rhs subrange of CHAR.
        }
        if (TInt.LT(a.max, a.min)) { // a is empty
            return true;
        }
        b = (P) t;
        return TInt.LE(b.min, a.min) && TInt.LE(a.max, b.max);
    }

    private static long minSize(P p) {
        long[] z1 = new long[1], z2 = new long[1];
        boolean[] n1 = new boolean[1], n2 = new boolean[1];
        // compute the minimum size of these elements
        if (TInt.LT(p.max, p.min)) {
            return 0;
        }
        bitWidth(p.min, z1, n1);
        bitWidth(p.max, z2, n2);
        z1[0] = Math.max(z1[0], z2[0]);
        if ((n1[0] || n2[0])) {
            z1[0]++;
        }
        if (Type.isSubtype(p.baseType, LInt.T)) {
            return Math.min(z1[0], M3.target.Longint.size);
        } else {
            return Math.min(z1[0], M3.target.Integer.size);
        }
    }

    /**
     * Port: n is passed by value and modified, so it is copied on entry;
     * VAR width is {@code width[0]}, VAR neg is {@code neg[0]}.
     */
    private static void bitWidth(int[] n, long[] width, boolean[] neg) {
        //**  valid for  Target.Longint.min <= n <= Target.Longint.max **
        int[] tmp = TInt.newInt();
        n = TInt.copy(n);
        neg[0] = TInt.LT(n, TInt.Zero);
        if ((neg[0])) {
            if (!TInt.add(n, TInt.One, tmp)
                || !TInt.subtract(TInt.Zero, tmp, n)) {
                // value too large???
                width[0] = M3.target.Longint.size;
                return;
            }
        }

        if (!powers_done) {
            buildPowerTables();
        }
        width[0] = M3.target.Longint.size;
        for (int i = 0; i <= power.length - 1; i++) {
            if (TInt.LE(n, power[i])) {
                width[0] = i;
                break;
            }
        }
    }

    // CONST
    private static final int[][] power = new int[TInt.Size + 1][];
    private static boolean powers_done = false;

    static {
        for (int i = 0; i < power.length; i++) {
            power[i] = TInt.newInt();
        }
    }

    private static void buildPowerTables() {
        TInt.assign(power[0], TInt.One);
        for (int i = 1; i <= power.length - 1; i++) {
            if (!TInt.add(power[i - 1], power[i - 1], power[i])) {
                TInt.assign(power[i], M3.target.Longint.max);
            }
        }
        powers_done = true;
    }

    private static long initCoster(P p, boolean zeroed) {
        int[] rep_min = TInt.newInt(), rep_max = TInt.newInt();
        long size;
        seal(p);

        if (Type.isSubtype(p.baseType, LInt.T)) {
            size = M3.target.Longint.size;
            TInt.assign(rep_min, M3.target.Longint.min);
            TInt.assign(rep_max, M3.target.Longint.max);
        } else {
            size = M3.target.Integer.size;
            TInt.assign(rep_min, M3.target.Integer.min);
            TInt.assign(rep_max, M3.target.Integer.max);
        }

        if (zeroed
            && TInt.LE(p.min, TInt.Zero)
            && TInt.LE(TInt.Zero, p.max)) {
            return 0;
        }

        if ((M3.targetMap.CG_Size[p.rep.ordinal()] == size)
            && !(TInt.EQ(p.min, rep_min) && TInt.EQ(p.max, rep_max))) {
            // this rep uses a full integer word, but doesn't actually
            // use all possible bit patterns => unsigned word =>  CARDINAL
            return 1;
        }

        switch (p.rep) {
            case Word8 -> { TInt.assign(rep_min, Target.Word8.min); TInt.assign(rep_max, Target.Word8.max); }
            case Word16 -> { TInt.assign(rep_min, Target.Word16.min); TInt.assign(rep_max, Target.Word16.max); }
            case Word32 -> { TInt.assign(rep_min, Target.Word32.min); TInt.assign(rep_max, Target.Word32.max); }
            case Word64 -> { TInt.assign(rep_min, Target.Word64.min); TInt.assign(rep_max, Target.Word64.max); }
            case Int8 -> { TInt.assign(rep_min, Target.Int8.min); TInt.assign(rep_max, Target.Int8.max); }
            case Int16 -> { TInt.assign(rep_min, Target.Int16.min); TInt.assign(rep_max, Target.Int16.max); }
            case Int32 -> { TInt.assign(rep_min, Target.Int32.min); TInt.assign(rep_max, Target.Int32.max); }
            case Int64 -> { TInt.assign(rep_min, Target.Int64.min); TInt.assign(rep_max, Target.Int64.max); }
            default -> { TInt.assign(rep_min, TInt.Zero); TInt.assign(rep_max, TInt.MOne); }
        }

        if (TInt.EQ(p.min, rep_min) && TInt.EQ(p.max, rep_max)) {
            return 0;
        } else {
            return 1;
        }
    }

    private static void genInit(P p, boolean zeroed) {
        Type.Info info = new Type.Info();
        Type.checkInfo(p, info);
        if (TInt.LT(TInt.Zero, p.min) || TInt.LT(p.max, TInt.Zero)) {
            CG.load_integer(info.stk_type, p.min);
            CG.store_indirect(info.stk_type, 0, info.size);
        } else if (zeroed) {
            CG.discard(CGType.Addr);
        } else {
            CG.load_integer(info.stk_type, TInt.Zero);
            CG.store_indirect(info.stk_type, 0, info.size);
        }
    }

    private static void genMap(P p, long offset, long size, boolean refs_only) {
        long bit_offset = Math.floorMod(offset, Target.Byte);
        TipeMap.Op op;
        if (refs_only) {
            return;
        }
        if (TInt.LT(p.min, TInt.Zero)
            || TInt.LT(p.max, TInt.Zero)) {
            // value is signed
            if ((bit_offset != 0)) {
                op = TipeMap.Op.Int_Field;
            } else if ((size == 1 * Target.Byte)) {
                op = TipeMap.Op.Int_1;
            } else if ((size == 2 * Target.Byte)) {
                op = TipeMap.Op.Int_2;
            } else if ((size == 4 * Target.Byte)) {
                op = TipeMap.Op.Int_4;
            } else if ((size == 8 * Target.Byte)) {
                op = TipeMap.Op.Int_8;
            } else { // weird size
                op = TipeMap.Op.Int_Field;
            }
        } else { // value is unsigned
            if ((bit_offset != 0)) {
                op = TipeMap.Op.Word_Field;
            } else if ((size == 1 * Target.Byte)) {
                op = TipeMap.Op.Word_1;
            } else if ((size == 2 * Target.Byte)) {
                op = TipeMap.Op.Word_2;
            } else if ((size == 4 * Target.Byte)) {
                op = TipeMap.Op.Word_4;
            } else if ((size == 8 * Target.Byte)) {
                op = TipeMap.Op.Word_8;
            } else { // weird size
                op = TipeMap.Op.Word_Field;
            }
        }
        TipeMap.add(offset, op, bit_offset + 256 * size);
    }

    private static void genDesc(P p) {
        if (Type.isEqual(p, Card.T, null)) {
            TipeDesc.addO(TipeDesc.Op.Cardinal, p);
        } else if (Type.isEqual(p, LCard.T, null)) {
            TipeDesc.addO(TipeDesc.Op.Longcard, p);
        } else if (TipeDesc.addO(TipeDesc.Op.Subrange, p)) {
            TipeDesc.addX(p.min);
            TipeDesc.addX(p.max);
        }
    }

    private static void fPrinter(P p, M3.FPInfo x) {
        if (Type.isEqual(p, Card.T, null)) {
            x.tag = "$cardinal";
            x.n_nodes = 0;
        } else if (Type.isEqual(p, LCard.T, null)) {
            x.tag = "$longcard";
            x.n_nodes = 0;
        } else {
            M3Buf.putText(x.buf, "SUBRANGE ");
            M3Buf.putIntt(x.buf, p.min);
            M3Buf.putChar(x.buf, ' ');
            M3Buf.putIntt(x.buf, p.max);
            if ((p.baseType == Int.T) || (Type.base(p.baseType) == Int.T)) {
                x.n_nodes = 0;
            } else {
                x.n_nodes = 1;
                x.nodes[0] = p.baseType;
            }
        }
    }
}
