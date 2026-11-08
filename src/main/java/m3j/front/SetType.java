// Java port of CM3's m3-sys/m3front/src/types/SetType.i3 and SetType.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: SetType.m3
// Last modified on Tue May 23 15:28:01 PDT 1995 by kalsow
//      modified on Wed Sep 26 19:01:24 1990 by muller

public final class SetType {
    private SetType() {}

    public static final class P extends Type.T {
        public Type.T domType;
        public boolean broken;

        @Override
        public void check() {
            SetType.check(this);
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
            TypeRep.initToZeros(this, zeroed);
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

    // EXPORTED:
    public static Type.T parse() {
        P p = new P();
        TypeRep.init(p, Type.Class.Set);
        Scanner.match(Token.T.tSET);
        Scanner.match(Token.T.tOF);
        p.domType = Type.parse();
        return p;
    }

    private static P reduce(Type.T t) {
        t = Type.strip(t); // StripPacked?
        if ((t == null) || (t.info.class_ != Type.Class.Set)) {
            return null;
        }
        return (P) t;
    }

    /** Port: VAR range is {@code domType[0]}. */
    // EXPORTED:
    public static boolean split(Type.T t, Type.T[] domType) {
        P p = reduce(t);
        if ((p == null)) {
            return false;
        }
        domType[0] = p.domType;
        return true;
    }

    private static void check(P p) {
        Type.Info domInfo = new Type.Info();
        int[] domNumT = TInt.newInt(), domMinT = TInt.newInt(), domMaxT = TInt.newInt();
        long[] domNumW = new long[1], domMinI = new long[1], domMaxI = new long[1];
        long wordCtW;
        String RangeTxt;
        int i;
        boolean b;
        p.broken = false;
        p.domType = Type.checkInfo(p.domType, domInfo);
        if (!Type.isOrdinal(p.domType)) {
            Error.msg("Domain of a SET type must be an ordinal type (2.2.6).");
            p.domType = ErrType.T;
            TInt.assign(domNumT, TInt.One);
            TInt.assign(domMinT, TInt.Zero);
            TInt.assign(domMaxT, TInt.Zero);
            p.broken = true;
        } else {

            // Checks against target range limits:
            TInt.assign(domNumT, Type.number(p.domType));
            if (TInt.GT(domNumT, M3.target.Word.max)) {
                Error.txt(TInt.toText(domNumT),
                          "Domain cardinality of SET type exceeds LAST(INTEGER) (2.2.6).");
                TInt.assign(domNumT, TInt.One);
                TInt.assign(domMinT, TInt.Zero);
                TInt.assign(domMaxT, TInt.Zero);
                p.broken = true;
            } else {
                b = Type.getBounds(p.domType, domMinT, domMaxT);
                AssertError.check(b, "SetType");
            }

            if (TInt.GT(domMaxT, M3.target.Integer.max)) {
                Error.txt(TInt.toText(domMaxT),
                          "Upper bound of SET domain type exceeds LAST(INTEGER) (2.2.6).");
                p.broken = true;
            }
            if (TInt.LT(domMinT, M3.target.Integer.min)) {
                Error.txt(TInt.toText(domMinT),
                          "Lower bound of SET domain type less that FIRST(INTEGER) (2.2.6).");
                p.broken = true;
            }


            // Checks against CM3 limits, specifically, all within host Word or Int.
            // TODO: Code below will handle a target Word, if we only had a TWord.ToWord.
            if (!TInt.toInt(domNumT, domNumW)
                || TInt.LT(domNumT, TInt.Zero)) {
                RangeTxt = "[0.." + Long.toString(Long.MAX_VALUE) + "]";
                Error.txt(TInt.toText(domNumT),
                          "Domain cardinality of SET type lies outside CM3 limits of "
                          + RangeTxt + "(2.2.6).");
                p.broken = true;
            }
            if (!TInt.toInt(domMaxT, domMaxI)) {
                RangeTxt =
                    "[" + Long.toString(Long.MIN_VALUE) + ".." + Long.toString(Long.MAX_VALUE) + "]";
                Error.txt(TInt.toText(domMaxT),
                          "Upper bound of SET domain type lies outside of CM3 limit of "
                          + RangeTxt + "(2.2.6).");
                p.broken = true;
            }
            if (!TInt.toInt(domMinT, domMinI)) {
                RangeTxt =
                    "[" + Long.toString(Long.MIN_VALUE) + ".." + Long.toString(Long.MAX_VALUE) + "]";
                Error.txt(TInt.toText(domMinT),
                          "Lower bound of SET domain type lies outside of CM3 limit of "
                          + RangeTxt + "(2.2.6).");
                p.broken = true;
            }

            // Unconditional properties:
            p.info.class_ = Type.Class.Set;
            p.info.addr_align = 8; // Irrelevant or will change.
            p.info.hash = 811 * domInfo.hash;
            p.info.isTraced = false;
            p.info.isEmpty = false;
            p.info.isSolid = true;

            // Representation-dependent properties.
            if (p.broken) {
                p.info.size = M3.target.Word.size;
                p.info.min_size = M3.target.Word.size;
                p.info.alignment = M3.target.Word.align;
                p.info.stk_type = M3.target.Word.cg_type;
                p.info.mem_type = M3.target.Word.cg_type;
            } else {
                i = 0;
                while (true) {
                    Target.Int_type wordType = M3.targetMap.Word_types[i];
                    if (domNumW[0] <= wordType.size) { // It fits.
                        p.info.size = wordType.size;
                        p.info.min_size = domNumW[0];
                        p.info.alignment = wordType.align;
                        p.info.stk_type = M3.target.Word.cg_type;
                        p.info.mem_type = wordType.cg_type;
                        break;
                    } else if (i >= M3.targetMap.Word_types.length - 1
                               || wordType.cg_type == M3.target.Word.cg_type) {
                        // It's bigger than one word.
                        AssertError.check(domNumW[0] >= M3.target.Word.size, "SetType");
                        // Preclude overflow below.
                        wordCtW = Long.divideUnsigned(domNumW[0] - 1, M3.target.Word.size) + 1;
                        p.info.size = wordCtW * M3.target.Word.size;
                        p.info.min_size = p.info.size;
                        p.info.alignment = M3.target.Word.align;
                        p.info.stk_type = Target.CGType.Addr;
                        p.info.addr_align = M3.target.Address.align;
                        p.info.mem_type = Target.CGType.Struct;
                        break;
                    } else {
                        i++;
                    }
                } // LOOP
            } // IF
        } // IF
    }

    // TODO: It should be possible to unify NoStraddle in SetType, EnumType, and
    //       SubrangeType.  But what about Type.ScalarNoStraddle?
    private static boolean noStraddle(P p, long offset, boolean IsEltOrField) {
        // Port: IsEltOrField is <*UNUSED*>.
        long z0;
        if (p.info.size > M3.target.Word.size) {
            return true;
        }
        if (p.info.lazyAligned) {
            z0 = Math.floorDiv(offset, Target.Byte) * Target.Byte;
        } else {
            z0 = Math.floorDiv(offset, M3.target.Integer.align) * M3.target.Integer.align;
        }
        return (offset + p.info.size) <= (z0 + M3.target.Integer.size);
    }

    /**
     * Fits within a word.  Handled as a scalar.
     * This includes a packed small set type, regardless of its use.
     */
    // EXPORTED:
    public static boolean isSmallSet(Type.T t) {
        Type.T s;
        if (t == null) {
            return false;
        }
        if (t.info.class_ == Type.Class.Named) {
            t = Type.strip(t);
        }
        if (t.info.class_ == Type.Class.Set) {
            return t.info.size <= M3.target.Word.size;
        }
        if (t.info.class_ == Type.Class.Packed) {
            s = PackedType.base(t);
            if (s == null) {
                return false;
            }
            if (s.info.class_ == Type.Class.Set) {
                return t.info.size <= M3.target.Word.size;
            }
        }
        return false;
    }

    private static void compiler(P p) {
        Type.Info info = new Type.Info();
        Type.compile(p.domType);
        Type.checkInfo(p, info);
        // Port: CG.TypeUID is int.
        CG.declare_set((int) Type.globalUID(p), (int) Type.globalUID(p.domType), info.size);
    }

    private static boolean equalChk(P a, Type.T t, Type.Assumption x) {
        P b = (P) t;
        return Type.isEqual(a.domType, b.domType, x);
    }

    private static boolean subtyper(P a, Type.T t) {
        return Type.isEqual(a, t, null);
    }

    private static long initCoster(P p, boolean zeroed) {
        // Port: p and zeroed are <*UNUSED*>.
        return 0;
    }

    private static void genMap(P p, long offset, long size, boolean refs_only) {
        // Port: p and refs_only are <*UNUSED*>.
        TipeMap.add(offset, TipeMap.Op.Set_1, Math.floorDiv(size, Target.Byte));
    }

    private static void genDesc(P p) {
        if (TipeDesc.addO(TipeDesc.Op.Set, p)) {
            TipeDesc.addX(Type.number(p.domType));
        }
    }

    private static void fPrinter(P p, M3.FPInfo x) {
        x.tag = "SET";
        x.n_nodes = 1;
        x.nodes[0] = p.domType;
    }
}
