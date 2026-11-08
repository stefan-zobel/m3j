// Java port of CM3's m3-sys/m3front/src/types/ArrayType.i3 and ArrayType.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: ArrayType.m3
// Last modified on Tue May 23 15:24:26 PDT 1995 by kalsow
//      modified on Sun Feb 24 04:39:01 1991 by muller

public final class ArrayType {
    private ArrayType() {}

    private static long MaxBitSize = Long.MAX_VALUE;

    public static final class P extends Type.T {
        public Type.T indexType = null;
        public Type.T elementType = null;
        public long alignment = 0;
        public long n_elts = 0;
        public long elt_align = 0;
        public long elt_pack = 0;
        public long total_size = 0;
        /** # of dimensions, lazily computed. */
        public long fixedDepth = 0;
        /** == ARRAY OF elementType */
        public Type.T openCousin = null;
        /**
         * Could be FALSE even if elements are BITS n FOR, if
         * n is divisible by 8.
         */
        public boolean eltsBitAddressed = false;

        @Override
        public void check() {
            ArrayType.check(this);
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
        P p, p0;
        Scanner.match(Token.T.tARRAY);
        if ((Token.TypeStart.contains(Scanner.cur.token))) {
            p0 = (P) new_(null, null);
            p = p0;
            while (true) {
                p.indexType = Type.parse();
                if ((Scanner.cur.token != Token.T.tCOMMA)) {
                    break;
                }
                Scanner.getToken(); // ,
                p.elementType = new_(null, null);
                p = (P) p.elementType;
            }
            Scanner.match(Token.T.tOF);
            p.elementType = Type.parse();
            return p0;
        } else {
            // must be an open array
            Scanner.match(Token.T.tOF);
            return OpenArrayType.new_(Type.parse());
        }
    }

    private static P reduce(Type.T t) {
        // Strip Named and Packed.  NIL if that's not an array type.
        t = Type.stripPacked(t);
        if ((t == null) || (t.info.class_ != Type.Class.Array)) {
            return null;
        }
        return (P) t;
    }

    // EXPORTED:
    public static Type.T new_(Type.T index, Type.T element) {
        P p;
        p = new P();
        TypeRep.init(p, Type.Class.Array);
        p.indexType = index;
        p.elementType = element;
        p.fixedDepth = -1;
        return p;
    }

    private static long fixedDepth(P p) {
        P eltType;
        if (p == null) {
            return 0;
        }
        if (p.fixedDepth <= 0) { // Compute it lazily and cache.
            p.fixedDepth = 1;
            eltType = reduce(p.elementType);
            if (eltType != null) {
                p.fixedDepth += fixedDepth(eltType);
            }
        }
        return p.fixedDepth;
    }

    /**
     * Total number of dimensions of t, open + fixed.
     * Works on a fixed or open array.
     */
    // EXPORTED:
    public static long totalDepth(Type.T t) {
        // Total number of dimensions of t, open + fixed.
        // Works on open array t too.
        P p;
        long openDepth;
        if (t == null) {
            return 0;
        }
        if (OpenArrayType.is(t)) {
            openDepth = OpenArrayType.openDepth(t);
            p = reduce(OpenArrayType.nonopenEltType(t));
            return openDepth + fixedDepth(p);
        } else {
            p = reduce(t);
            return fixedDepth(p);
        }
    }

    /** An array type, open or fixed. */
    // EXPORTED:
    public static boolean is(Type.T t) {
        P p = reduce(t);
        if (p != null) {
            return true;
        } else {
            return OpenArrayType.is(t);
        }
    }

    /** A fixed array type. */
    // EXPORTED:
    public static boolean isFixed(Type.T t) {
        P p = reduce(t);
        return p != null;
    }

    /**
     * If 'array' is a fixed array type, returns TRUE and sets index and
     * element to the appropriate types.  If 'array' is an open array type,
     * returns TRUE, sets index to NIL, and sets element to the appropriate
     * type.  Otherwise, returns FALSE
     */
    // EXPORTED:
    public static boolean split(Type.T t, Type.T[] index, Type.T[] element) {
        // Succeeds on an open array too.
        P p = reduce(t);
        if ((p != null)) {
            index[0] = p.indexType;
            element[0] = p.elementType;
            return true;
        } else if (OpenArrayType.split(t, element)) {
            index[0] = null;
            return true;
        } else {
            return false;
        }
    }

    /**
     * If 'array' is an array type, returns the packed size in bits of
     * its elements.  If 'array' is an open array type, this is for the
     * outermost fixed array dimension, if such exists.  If t is not an
     * array type, returns 0.
     */
    // EXPORTED:
    public static long eltPack(Type.T t) {
        P p = reduce(t);
        if ((p != null)) {
            return p.elt_pack;
        } else if (OpenArrayType.is(t)) {
            return OpenArrayType.eltPack(t);
        } else {
            return 0;
        }
    }

    /**
     * If 'array' is an array type, returns the bit alignment of its
     * elements.  If t is open, returns the nearest non-open or non-array
     * alignment. If array is not an array type, returns Target.Byte.
     */
    // EXPORTED:
    public static long eltAlign(Type.T t) {
        P p = reduce(t);
        if ((p != null)) {
            return p.elt_align;
        } else if (OpenArrayType.is(t)) {
            return OpenArrayType.eltAlign(t);
        } else { // Not an array.
            return Target.Byte;
        }
    }

    /**
     * If 't' is an 'ARRAY I OF X', returns 'ARRAY OF X', otherwise
     * returns 't'.
     */
    // EXPORTED:
    public static Type.T openCousin(Type.T t) {
        P p = reduce(t);
        if ((p != null)) {
            if ((p.openCousin == null)) {
                p.openCousin = OpenArrayType.new_(p.elementType);
            }
            return p.openCousin;
        } else {
            return t;
        }
    }

    /**
     * Returns TRUE if the array's elements are not guaranteed to be byte aligned.
     * Works on a fixed or open array.
     * <p>PRE: t is Checked.
     */
    // EXPORTED:
    public static boolean eltsAreBitAddressed(Type.T t) {
        P p = reduce(t);
        return (p != null) && (p.eltsBitAddressed);
    }

    /**
     * Given "ADR(a)" and "index" on the stack, generate code to replace them
     * by "ADR(a[index])" on the stack.  Beginning and ending "addresses" are
     * CG 'ValRec's and may include a 'bits' expression. Generates no bounds
     * checks.
     * <p>Works for an open array too, but uses elt_pack of the first nonopen element
     * type, so, for an open array of depth > 1, 'index' must have been already
     * multiplied by the product of element counts of inner open dimensions.
     * <p>PRE: t is Checked.
     */
    // EXPORTED:
    public static void genIndex(Type.T t) {
        Type.Info array_info = new Type.Info();
        P p = reduce(t); // If non-NIL, p is a fixed array type.
        CG.Val bit_index;
        long array_align; // of the elements, not the dope fields.
        long elt_pack;
        boolean eltsBitAddressed;
        if ((p == null)) { // t is an open array.
            eltsBitAddressed = OpenArrayType.eltsAreBitAddressed(t);
            t = Type.checkInfo(Type.stripPacked(t), array_info);
            array_align = array_info.alignment;
            elt_pack = OpenArrayType.eltPack(t);
        } else { // Fixed array, p is its narrow type.
            eltsBitAddressed = p.eltsBitAddressed;
            array_align = p.alignment;
            elt_pack = p.elt_pack;
        }
        if (!eltsBitAddressed) {
            AssertError.check(Math.floorMod(elt_pack, Target.Byte) == 0, "ArrayType");
            CG.index_bytes(elt_pack);
        } else { // We have an array with non-byte-aligned packed elements.
            if ((elt_pack != 1)) {
                // compute the bit-offset of the indexed element
                CG.load_intt(elt_pack);
                CG.multiply(M3.target.Integer.cg_type);
            }
            if (true
                || p != null /* fixed array */ && p.total_size <= p.alignment) {
                CG.index_bits();
            } else {
                bit_index = CG.pop();
                CG.push(bit_index);
                CG.load_intt(array_align);
                CG.div(M3.target.Integer.cg_type, M3CG.Sign.Positive, M3CG.Sign.Positive);
                CG.index_bytes(array_align);
                CG.push(bit_index);
                CG.load_intt(array_align);
                CG.mod(M3.target.Integer.cg_type, M3CG.Sign.Positive, M3CG.Sign.Positive);
                CG.index_bits();
                CG.free(bit_index);
            }
        }
    }

    // Externally dispatched-to:
    private static void check(P p) {
        long min_size;
        Type.Info elt_info = new Type.Info();
        long[] n_elts = new long[1];
        boolean ok;
        p.indexType = Type.check(p.indexType);
        if (!Type.isOrdinal(p.indexType)) {
            Error.msg("Array index type must be an ordinal type (2.2.3).");
            p.indexType = ErrType.T;
        }

        p.elementType = Type.checkInfo(p.elementType, /*OUT*/ elt_info);
        if ((elt_info.class_ == Type.Class.OpenArray)) {
            Error.msg("Fixed array element type cannot be open array (2.2.3).");
        }

        // Port: p.n_elts is the VAR parameter of TInt.ToInt.
        n_elts[0] = p.n_elts;
        ok = TInt.toInt(Type.number(p.indexType), /*OUT*/ n_elts);
        p.n_elts = n_elts[0];
        if (!ok) {
            Error.msg("CM3 restriction: array type has too many elements");
            // FIXME: Tell the user how many are allowed.
            // FIXME: Cross-compile 32-bit host to 64-bit target.
            p.n_elts = 1;
        }

        p.elt_align = elt_info.alignment;
        if ((elt_info.class_ == Type.Class.Packed)) {
            // Find an element alignment that won't cause word straddle.
            p.elt_pack = elt_info.size; // The BITS n size.
            p.alignment = arrayAlignWithPackedElts(p);
            p.eltsBitAddressed =
                (p.elt_pack < Target.Byte) || (Math.floorMod(p.elt_pack, p.alignment) != 0);
        } else {
            // Natural element alignment is the alignment of the whole array.
            p.elt_pack = RecordType.roundUp(elt_info.size, elt_info.alignment);
            p.alignment = elt_info.alignment;
            p.eltsBitAddressed = false;
        }

        if ((p.n_elts > 0) && (p.elt_pack > 0)
            && (p.n_elts > Math.floorDiv(MaxBitSize, p.elt_pack))) {
            Error.msg("CM3 restriction: array type too large");
            // FIXME: Tell the user how large it can be.
            // FIXME: Cross-compile 32-bit host to 64-bit target.
            min_size = 0;
            p.total_size = 0;
        } else {
            min_size = p.elt_pack * p.n_elts;
            p.total_size = RecordType.roundUp(min_size, p.alignment);
        }

        p.info.size = p.total_size;
        p.info.min_size = min_size;
        p.info.alignment = p.alignment;
        p.info.mem_type = Target.CGType.Struct;
        p.info.stk_type = Target.CGType.Addr;
        p.info.class_ = Type.Class.Array;
        p.info.isTraced = elt_info.isTraced;
        p.info.isEmpty = elt_info.isEmpty;
        p.info.isSolid = elt_info.isSolid
                         && (p.elt_pack <= elt_info.size)
                         && (p.total_size <= min_size);
        p.info.hash = 23 * p.n_elts + 29 * p.elt_pack;
    }

    /**
     * The smallest alignment of the whole array that will statically ensure
     * elements won't straddle word boundaries, ensured either because
     * the entire array ends before crossing a word boundary, or because
     * word boundaries occur only at element boundaries.
     */
    private static long arrayAlignWithPackedElts(P p) {
        long trialAlign, offset;
        for (int a = 0; a < Target.Alignments.length; a++) { // Smallest first
            trialAlign = Target.Alignments[a];
            if (trialAlign > M3.target.Word.align) {
                break;
            }
            offset = M3.target.Word.size;
            while (true) { // Backwards through multiples of trialAlign within a word.
                offset -= trialAlign;
                if (!isStraddleFree(p, offset, /* IsEltOrField := */ true)) {
                    break;
                } else if (offset <= 0) {
                    return trialAlign;
                }
            }
        }
        Error.msg("CM3 restriction: Scalars in packed array elements cannot cross "
                  + "word boundaries (2.2.5).");
        return Target.Byte;
    }

    // Externally dispatched-to:
    private static boolean isStraddleFree(P p, long offset, boolean IsEltOrField) {
        // Port: IsEltOrField is <*UNUSED*>.
        long x0 = Math.floorMod(offset, M3.target.Word.size);
        long x = x0;
        for (long i = 0; i <= p.n_elts - 1; i++) {
            if (!Type.straddleFreeScalars(p.elementType, /* offs := */ x,
                                          /* IsEltOrField := */ true)) {
                return false;
            }
            x = Math.floorMod(x + p.elt_pack, M3.target.Word.size);
            if ((x == x0)) {
                break;
            }
        }
        return true;
    }

    // Externally dispatched-to:
    private static void compiler(P p) {
        long self, index, elt;
        Type.compile(p.indexType);
        Type.compile(p.elementType);
        self = Type.globalUID(p);
        index = Type.globalUID(p.indexType);
        elt = Type.globalUID(p.elementType);
        // Port: CG.TypeUID is int.
        CG.declare_array((int) self, (int) index, (int) elt, p.total_size);
    }

    // Externally dispatched-to:
    private static boolean equalChk(P a, Type.T t, Type.Assumption x) {
        P b = (P) t;
        return Type.isEqual(a.elementType, b.elementType, x)
            && Type.isEqual(a.indexType, b.indexType, x);
    }

    // Externally dispatched-to:
    private static boolean subtyper(P a, Type.T tb) {
        Type.T ta;
        Type.T[] eb = new Type.T[1];
        P b;
        ta = a;

        // peel off the fixed dimensions of A and matching open dimensions of B
        while (true) {
            a = reduce(ta);
            if ((a == null) || !OpenArrayType.split(tb, eb)) {
                break;
            }
            ta = a.elementType;
            tb = eb[0];
        }

        // peel off the fixed dimensions as long as the sizes are equal
        while (true) {
            a = reduce(ta);
            b = reduce(tb);
            if ((a == null) || (b == null)) {
                break;
            }
            if ((a.indexType != b.indexType)) {
                if (!TInt.EQ(Type.number(a.indexType), Type.number(b.indexType))) {
                    return false;
                }
            }
            ta = a.elementType;
            tb = b.elementType;
        }

        return Type.isEqual(ta, tb, null);
    }

    // Externally dispatched-to:
    private static long initCoster(P p, boolean zeroed) {
        int[] n = TInt.newInt(), m = TInt.newInt(), res = TInt.newInt();
        long[] x = new long[1];
        x[0] = Type.initCost(p.elementType, zeroed);
        if (!TInt.fromInt(x[0], m)) {
            return Long.MAX_VALUE;
        }
        TInt.assign(n, Type.number(p.indexType));
        if (TInt.LT(n, TInt.Zero)) { // open array?
            return 20 * x[0];
        }
        if (!TInt.multiply(m, n, res)) {
            return Long.MAX_VALUE;
        }
        if (!TInt.toInt(res, x)) {
            return Long.MAX_VALUE;
        }
        return x[0];
    }

    // Externally dispatched-to:
    private static void genInit(P p, boolean zeroed) {
        long top;
        CG.Val cnt;
        CG.Val array = CG.pop(); // capture the array's l-value

        // put down a loop to initialize the additional elements
        CG.load_integer(M3.target.Integer.cg_type, TInt.Zero);
        cnt = CG.pop_temp();
        top = CG.next_label();
        CG.set_label(top);

        // init ARRAY[cnt]
        CG.push(array);
        CG.push(cnt);
        genIndex(p);
        Type.initValue(p.elementType, zeroed);

        // cnt := cnt + 1
        CG.push(cnt);
        CG.load_integer(M3.target.Integer.cg_type, TInt.One);
        CG.add(M3.target.Integer.cg_type);
        CG.store_temp(cnt);

        // IF (cnt < NUMBER(ARRAY) GOTO TOP-OF-LOOP
        CG.push(cnt);
        CG.load_intt(p.n_elts);
        CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.LT, top, CG.Likely);

        // release the temps
        CG.free(cnt);
        CG.free(array);
    }

    // Externally dispatched-to:
    private static void genMap(P p, long offset, long size, boolean refs_only) {
        // EVAL size;
        if ((p.n_elts <= 0)) {
            return;
        }
        TipeMap.add(offset, TipeMap.Op.Mark, 0);
        Type.genMap(p.elementType, offset, p.elt_pack, refs_only);
        TipeMap.add(offset + p.elt_pack, TipeMap.Op.Array_1, p.n_elts);
        TipeMap.setCursor(offset + p.total_size);
    }

    // Externally dispatched-to:
    private static void genDesc(P p) {
        if (TipeDesc.addO(TipeDesc.Op.Array, p)) {
            TipeDesc.addX(Type.number(p.indexType));
            Type.genDesc(p.elementType);
        }
    }

    // Externally dispatched-to:
    private static void fPrinter(P p, M3.FPInfo x) {
        x.tag = "ARRAY";
        x.n_nodes = 2;
        x.nodes[0] = p.indexType;
        x.nodes[1] = p.elementType;
    }
}
