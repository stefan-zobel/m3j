// Java port of CM3's m3-sys/m3front/src/types/OpenArrayType.i3 and OpenArrayType.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.middle.Target;

// File: OpenArrayType.m3
// Last modified on Tue May 23 15:24:22 PDT 1995 by kalsow
//      modified on Sun Feb 24 04:39:01 1991 by muller

public final class OpenArrayType {
    private OpenArrayType() {}

    public static final class P extends Type.T {
        public Type.T EltType = null;
        public Type.T NonopenEltType = null;
        public long openDepth = 0;
        public long NonopenEltAlign = 0;
        public long NonopenEltPack = 0;
        /**
         * Could be FALSE even if elements are BITS n FOR, if
         * n is divisible by 8.
         */
        public boolean NonopenEltsBitAddressed = false;

        @Override
        public void check() {
            OpenArrayType.check(this);
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
    public static Type.T new_(Type.T EltType) {
        P p;
        p = new P();
        TypeRep.init(p, Type.Class.OpenArray);
        p.EltType = EltType;
        p.openDepth = -1;
        return p;
    }

    private static P reduce(Type.T t) {
        // Strip Named and Packed.  NIL if that's not an open array type.
        t = Type.stripPacked(t);
        if ((t == null) || (t.info.class_ != Type.Class.OpenArray)) {
            return null;
        }
        return (P) t;
    }

    /** Return TRUE iff 't' is an open array type */
    // EXPORTED:
    public static boolean is(Type.T t) {
        return (reduce(t) != null);
    }

    /**
     * If 't' is an open array type, returns TRUE and sets 'element'
     * to the appropriate type.  Otherwise, returns FALSE
     */
    // EXPORTED:
    public static boolean split(Type.T t, Type.T[] EltType) {
        P p = reduce(t);
        if ((p == null)) {
            return false;
        }
        EltType[0] = p.EltType;
        return true;
    }

    /**
     * If 'array' is an array type, returns the packed size in bits of
     * its elements.  If 'array' is an open array type, this is for the
     * nearest fixed array dimension, if such exists.  If t is not an
     * array type, returns 0.
     */
    // EXPORTED:
    public static long eltPack(Type.T t) {
        // If 'array' is an open array type, returns the packed size in bits of
        // the nearest non-open elements.  Otherwise, returns 0.
        P p = reduce(t);
        if ((p != null)) {
            return p.NonopenEltPack;
        } else {
            return 0;
        }
    }

    /**
     * If 'array' is an open array type, returns the bit alignment of
     * the nearest non-open or non-array elements.
     * *BUT NOTE*: If elements are packed, this is the alignment of the
     * unpacked element type !!!
     * Otherwise, returns Target.Byte.
     * <p>PRE: t is Checked.
     */
    // EXPORTED:
    public static long eltAlign(Type.T t) {
        P p = reduce(t);
        if ((p != null)) {
            return p.NonopenEltAlign;
        } else {
            return Target.Byte;
        }
    }

    /**
     * Returns TRUE if t is an open array whose nearest non-open elements are not
     * at least byte-aligned.
     */
    // EXPORTED:
    public static boolean eltsAreBitAddressed(Type.T t) {
        P p = reduce(t);
        return (p != null) && (p.NonopenEltsBitAddressed);
    }

    /** If 't' is an n-dimensional open array, returns n else returns 0 */
    // EXPORTED:
    public static long openDepth(Type.T t) {
        P p = reduce(t);
        if ((p == null)) {
            return 0;
        }
        if ((p.openDepth <= 0)) {
            p.openDepth = 1 + openDepth(p.EltType);
        }
        return p.openDepth;
    }

    /**
     * If 't' is an n-dimensional open array, returns the type of the non-open
     * elements; otherwise, returns t. That is, strip all the ARRAY OF in
     * front of t
     */
    // EXPORTED:
    public static Type.T nonopenEltType(Type.T t) {
        // If 't' is an n-dimensional open array, returns the type of the base
        // elements; otherwise, returns t. That is, strip all the ARRAY OF in
        // front of t
        P p = reduce(t);
        if ((p == null)) {
            return t;
        }
        if ((p.NonopenEltType == null)) {
            p.NonopenEltType = nonopenEltType(p.EltType);
        }
        return p.NonopenEltType;
    }

    // Externally dispatched-to:
    private static void check(P p) {
        Type.T LNonopenEltType;
        Type.T[] NonopenEltBase = new Type.T[1];
        Type.Info NonopenEltInfo = new Type.Info();
        long dope_align =
            Math.max(Math.max(Target.Byte, Target.Structure_size_boundary),
                     Math.max(M3.target.Address.align, M3.target.Integer.align));
        long[] pack = new long[1];
        p.EltType = Type.check(p.EltType);
        LNonopenEltType = Type.checkInfo(nonopenEltType(p), NonopenEltInfo);
        p.NonopenEltAlign = NonopenEltInfo.alignment;

        if ((NonopenEltInfo.class_ == Type.Class.Packed)) {
            // Port: p.NonopenEltPack is the VAR parameter of PackedType.Split.
            pack[0] = p.NonopenEltPack;
            PackedType.split(LNonopenEltType, /*OUT*/ pack, /*OUT*/ NonopenEltBase);
            p.NonopenEltPack = pack[0];
            if (Type.isStructured(LNonopenEltType)) {
                if (p.NonopenEltPack >= Target.Byte
                    && Math.floorMod(p.NonopenEltPack, p.NonopenEltAlign) != 0) {
                    Error.msg("CM3 restriction: scalar components of packed, structured array "
                              + "elements cannot cross word boundaries");
                    p.NonopenEltPack =
                        RecordType.roundUp(NonopenEltInfo.size, p.NonopenEltAlign);
                }
            } else { // Scalar packed elements.
                if (Math.floorMod(Target.Word64.size, p.NonopenEltPack) != 0) {
                    // ^Allow 64-bit element pack, even on 32-bit target.
                    Error.msg("CM3 restriction: open array scalar element bit size must evenly "
                              + "divide 64 bits.");
                    p.NonopenEltPack = p.NonopenEltAlign;
                }
            }
            p.NonopenEltsBitAddressed = p.NonopenEltPack < Target.Byte;
        } else { // Not packed; Pack to satisfy their natural alignment.
            p.NonopenEltPack =
                RecordType.roundUp(NonopenEltInfo.size, p.NonopenEltAlign);
            p.NonopenEltsBitAddressed = false;
        }

        dope_align = Math.max(p.NonopenEltAlign, dope_align);

        p.info.size = -1;
        p.info.min_size = -1;
        p.info.alignment = dope_align;
        p.info.addr_align = p.NonopenEltAlign;
        p.info.mem_type = Target.CGType.Addr;
        p.info.stk_type = Target.CGType.Addr;
        p.info.class_ = Type.Class.OpenArray;
        p.info.isTraced = NonopenEltInfo.isTraced;
        p.info.isEmpty = NonopenEltInfo.isEmpty;
        p.info.isSolid = NonopenEltInfo.isSolid
                         && (p.NonopenEltPack <= NonopenEltInfo.size);
        p.info.hash = NonopenEltInfo.hash
                      + (23 * openDepth(p) + 37 * p.NonopenEltPack);
    }

    // Externally dispatched-to:
    private static boolean isStraddleFree(P p, long offset, boolean IsEltOrField) {
        // Port: IsEltOrField is <*UNUSED*>.
        long x0 = Math.floorMod(offset, M3.target.Word.size);
        long x = x0;
        Type.T t = nonopenEltType(p);
        do {
            if (!Type.straddleFreeScalars(t, /* offs := */ x, /* IsEltOrField := */ true)) {
                return false;
            }
            x = Math.floorMod(x + p.NonopenEltPack, M3.target.Word.size);
        } while (!(x == x0));
        return true;
    }

    /**
     * If 't' is an open array type, declare and return a temporary variable to
     * hold its dope vector, otherwise abort.
     */
    // EXPORTED:
    public static M3CG.Var declareDopeTemp(Type.T t) {
        P p = reduce(t);
        long size = M3.target.Address.pack + openDepth(p) * M3.target.Integer.pack;
        return CG.declare_temp(size, M3.target.Address.align,
                               Target.CGType.Struct, /* in_memory := */ true);
    }

    // Externally dispatched-to:
    private static void compiler(P p) {
        long size = M3.target.Address.pack + openDepth(p) * M3.target.Integer.pack;
        Type.compile(p.EltType);
        // Port: CG.TypeUID is int.
        CG.declare_open_array((int) Type.globalUID(p), (int) Type.globalUID(p.EltType), size);
    }

    // Externally dispatched-to:
    private static boolean equalChk(P a, Type.T t, Type.Assumption x) {
        P b = (P) t;
        return (openDepth(a) == openDepth(b))
            && Type.isEqual(a.EltType, b.EltType, x);
    }

    // Externally dispatched-to:
    private static boolean subtyper(P a, Type.T tb) {
        Type.T ta;
        Type.T[] ia = new Type.T[1], ea = new Type.T[1], ib = new Type.T[1],
                 eb = new Type.T[1];
        P b;
        ta = a;

        // peel off the common open dimensions
        while (true) {
            a = reduce(ta);
            b = reduce(tb);
            if ((a == null) || (b == null)) {
                break;
            }
            ta = a.EltType;
            tb = b.EltType;
        }

        // peel off the remaining fixed dimensions of A and open dimensions of B
        while (true) {
            b = reduce(tb);
            if ((b == null) || !ArrayType.split(ta, ia, ea)) {
                break;
            }
            ta = ea[0];
            tb = b.EltType;
        }

        // peel off the fixed dimensions as long as the sizes are equal
        while (ArrayType.split(ta, ia, ea) && ArrayType.split(tb, ib, eb)) {
            if (!TInt.EQ(Type.number(ia[0]), Type.number(ib[0]))) {
                return false;
            }
            ta = ea[0];
            tb = eb[0];
        }

        return Type.isEqual(ta, tb, null);
    }

    // Externally dispatched-to:
    private static long initCoster(P p, boolean zeroed) {
        int[] n = TInt.newInt(), m = TInt.newInt(), res = TInt.newInt();
        long[] x = new long[1];
        if (TInt.fromInt(Type.initCost(p.EltType, zeroed), m)
            && TInt.fromInt(20, n) // guess that there are 20 elements
            && TInt.multiply(m, n, res)
            && TInt.toInt(res, x)) {
            return x[0];
        } else {
            return Long.MAX_VALUE;
        }
    }

    // Externally dispatched-to:
    private static void genInit(P p, boolean zeroed) {
        long depth = openDepth(p);
        Type.T elt = nonopenEltType(p);
        long top;
        CG.Val cnt;
        CG.Val max;
        CG.Val array = CG.pop(); // capture the array's L-value

        // Gen RT code to compute the product number of non-open elements
        for (long i = 0; i <= depth - 1; i++) {
            CG.push(array);
            CG.open_size(i);
            if ((i != 0)) {
                CG.multiply(M3.target.Word.cg_type);
            }
        }
        max = CG.pop();

        // capture the pointer to the array elements
        CG.push(array);
        CG.open_elt_ptr(ArrayType.eltAlign(p));
        CG.free(array);
        array = CG.pop();

        // put down a loop to map the elements
        CG.load_integer(M3.target.Integer.cg_type, TInt.Zero);
        cnt = CG.pop_temp();
        top = CG.next_label(2);
        CG.jump(top + 1);
        CG.set_label(top);

        // map ARRAY[cnt]
        CG.push(array);
        CG.push(cnt);
        CG.index_bytes(p.NonopenEltPack);
        Type.initValue(elt, zeroed);

        // cnt := cnt + 1
        CG.push(cnt);
        CG.load_integer(M3.target.Integer.cg_type, TInt.One);
        CG.add(M3.target.Integer.cg_type);
        CG.store_temp(cnt);

        // IF (cnt < NUMBER(ARRAY) GOTO TOP-OF-LOOP
        CG.set_label(top + 1);
        CG.push(cnt);
        CG.push(max);
        CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.LT, top, CG.Likely);

        // release the temps
        CG.free(cnt);
        CG.free(max);
        CG.free(array);
    }

    // Externally dispatched-to:
    private static void genMap(P p, long offset, long size, boolean refs_only) {
        // Port: size is <*UNUSED*>.
        long a;
        TipeMap.add(offset, TipeMap.Op.OpenArray_1, openDepth(p));
        a = TipeMap.getCursor();
        Type.genMap(nonopenEltType(p), a, p.NonopenEltPack, refs_only);
        TipeMap.add(a + p.NonopenEltPack, TipeMap.Op.Stop, 0);
    }

    // Externally dispatched-to:
    private static void genDesc(P p) {
        if (TipeDesc.addO(TipeDesc.Op.OpenArray, p)) {
            TipeDesc.addI(openDepth(p));
            Type.genDesc(nonopenEltType(p));
        }
    }

    // Externally dispatched-to:
    private static void fPrinter(P p, M3.FPInfo x) {
        x.tag = "OPENARRAY";
        x.n_nodes = 1;
        x.nodes[0] = p.EltType;
    }
}
