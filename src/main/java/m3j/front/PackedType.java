// Java port of CM3's m3-sys/m3front/src/types/PackedType.i3 and PackedType.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: PackedType.m3
// Last modified on Tue May 23 15:25:39 PDT 1995 by kalsow
//      modified on Fri Dec 21 01:25:20 1990 by muller

public final class PackedType {
    private PackedType() {}

    private static final long NO_SIZE = -1;

    public static final class P extends Type.T {
        public Expr.T sizeE;
        public long newSize;
        public Type.T baseType;

        @Override
        public void check() {
            PackedType.check(this);
        }

        @Override
        public boolean no_straddle(long offset, boolean IsEltOrField) {
            return isStraddleFree(this, offset, IsEltOrField);
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

    // EXPORTED:
    public static Type.T parse() {
        P p = (P) new_(NO_SIZE, null);
        Scanner.match(Token.T.tBITS);
        p.sizeE = Expr.parse();
        Scanner.match(Token.T.tFOR);
        p.baseType = Type.parse();
        return p;
    }

    // EXPORTED:
    public static Type.T new_(long size, Type.T base) {
        P p;
        p = new P();
        TypeRep.init(p, Type.Class.Packed);
        p.sizeE = null;
        p.newSize = size;
        p.baseType = base;
        return p;
    }

    /** Including named packed type. */
    // EXPORTED:
    public static boolean is(Type.T t) {
        t = NamedType.strip(t);
        if (t == null) {
            return false;
        } else if (t instanceof P) {
            return true;
        } else {
            return false;
        }
    }

    private static P reduce(Type.T t) {
        t = Type.strip(t);
        if ((t == null) || (t.info.class_ != Type.Class.Packed)) {
            return null;
        }
        return (P) t;
    }

    /**
     * This mostly duplicates code in Check, but doesn't depend on the
     * base type's having been checked, won't adjust for some errors, and
     * may leave p.newSize as NO_SIZE (and return NO_SIZE).  If Check has
     * happened, Check's result will prevail.
     */
    private static long getPackedSize(P p) {
        long[] newSize = new long[1];
        Expr.T e;
        if (p == null) {
            return NO_SIZE;
        }
        if ((p.newSize == NO_SIZE) && (p.sizeE != null)) {
            e = Expr.constValue(p.sizeE);
            if ((e == null) || !IntegerExpr.toInt(e, newSize)) {
                Error.msg("BITS FOR size must be a constant integer (2.2.5)");
            } else {
                p.sizeE = e;
                p.newSize = newSize[0];
            }
        }
        return p.newSize;
    }

    /** Port: VAR size is {@code size[0]}, VAR base is {@code base[0]}. */
    // EXPORTED:
    public static void split(Type.T t, long[] size, Type.T[] base) {
        P p = reduce(t);
        size[0] = getPackedSize(p);
        base[0] = p.baseType;
    }

    // EXPORTED:
    public static Type.T base(Type.T t) {
        P p = (P) t;
        return p.baseType;
    }

    // Externally dispatched-to:
    private static void check(P p) {
        M3.CheckState cs = M3.OuterCheckState.copy();
        Type.Info baseInfo = new Type.Info();
        Expr.T e;
        long[] newSize = new long[1];
        boolean ok;
        while (true) {
            if (p.baseType == null) {
                break;
            } else if (p.baseType instanceof P packedBaseType) {
                p.baseType = packedBaseType.baseType;
            } else {
                break;
            }
        }

        p.baseType = Type.checkInfo(p.baseType, baseInfo);
        if (baseInfo.class_ == Type.Class.OpenArray) {
            Error.msg("CM3 restriction: 'BITS' cannot be applied to an open array type"
                      + "(2.2.5).");
            p.info.assign(baseInfo);
            p.info.class_ = Type.Class.Packed;
            return;
        }

        if ((p.sizeE != null)) {
            Expr.typeCheck(p.sizeE, cs);
            e = Expr.constValue(p.sizeE);
            // Port: p.newSize is the VAR parameter of IntegerExpr.ToInt.
            ok = false;
            if (e != null) {
                newSize[0] = p.newSize;
                ok = IntegerExpr.toInt(e, newSize);
                p.newSize = newSize[0];
            }
            if ((e == null) || !ok) {
                Error.msg("BITS FOR size must be a constant integer (2.2.5).");
                p.newSize = baseInfo.size;
            } else {
                p.sizeE = e;
                if (p.newSize < baseInfo.min_size) {
                    Error.int_(baseInfo.min_size,
                               "BITS FOR size too small for base type (2.2.5), must be at least:");
                    p.newSize = baseInfo.min_size;
                }
            }
        }

        p.info.size = p.newSize;
        p.info.min_size = p.newSize;
        p.info.alignment = baseInfo.alignment; // Inherit from base type.
        p.info.mem_type = baseInfo.mem_type;
        p.info.stk_type = baseInfo.stk_type;
        p.info.class_ = Type.Class.Packed;
        p.info.isTraced = baseInfo.isTraced;
        p.info.isEmpty = baseInfo.isEmpty;
        p.info.isSolid = baseInfo.isSolid;
        p.info.hash = 61 * baseInfo.hash + p.newSize;
    }

    // Externally dispatched-to:
    private static boolean isStraddleFree(P p, long offset, boolean IsEltOrField) {
        long z0;
        long sz;
        if (IsEltOrField) { // The BITS FOR affects allocation.
            if (p.info.lazyAligned) {
                z0 = Math.floorDiv(offset, Target.Byte) * Target.Byte;
            } else {
                z0 = Math.floorDiv(offset, M3.target.Word.align) * M3.target.Word.align;
            }
            sz = getPackedSize(p);
            if ((offset + sz) <= (z0 + M3.target.Word.size)) {
                // The entire thing fits in a word.
                return true;
            } else { // It crosses a word boundary...
                if (Type.isStructured(p)) {
                    // but maybe none of its scalar components do.
                    return Type.straddleFreeScalars(p.baseType, offset, IsEltOrField);
                } else {
                    return false;
                }
            }
        } else { // Not an element or field, just delegate to base type.
            return Type.straddleFreeScalars(p.baseType, offset, false);
        }
    }

    // Externally dispatched-to:
    private static void compiler(P p) {
        Type.compile(p.baseType);
        // Port: CG.TypeUID is int.
        CG.declare_packed((int) Type.globalUID(p), p.newSize,
                          (int) Type.globalUID(p.baseType));
    }

    // Externally dispatched-to:
    private static boolean equalChk(P a, Type.T t, Type.Assumption x) {
        P b = (P) t;
        return getPackedSize(a) == getPackedSize(b)
            && Type.isEqual(a.baseType, b.baseType, x);
    }

    // Externally dispatched-to:
    private static boolean subtyper(P a, Type.T b) {
        return Type.isEqual(b, a.baseType, null);
    }

    // Externally dispatched-to:
    private static long initCoster(P p, boolean zeroed) {
        return Type.initCost(p.baseType, zeroed);
    }

    // Externally dispatched-to:
    private static void genInit(P p, boolean zeroed) {
        Type.initValue(p.baseType, zeroed); // BUG!!
    }

    // Externally dispatched-to:
    private static void genMap(P p, long offset, long size, boolean refs_only) {
        AssertError.check(size <= p.newSize, "PackedType");
        Type.genMap(p.baseType, offset, size, refs_only);
    }

    // Externally dispatched-to:
    private static void genDesc(P p) {
        if (TipeDesc.addO(TipeDesc.Op.Packed, p)) {
            TipeDesc.addI(p.newSize);
            Type.genDesc(p.baseType);
        }
    }

    // Externally dispatched-to:
    private static void fPrinter(P p, M3.FPInfo x) {
        M3Buf.putText(x.buf, "BITS-FOR ");
        M3Buf.putInt(x.buf, p.newSize);
        x.n_nodes = 1;
        x.nodes[0] = p.baseType;
    }
}
