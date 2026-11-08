// Java port of CM3's m3-sys/m3front/src/builtinOps/Subarray.i3 and Subarray.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: Subarray.m3
// Last Modified On Wed Jun 29 17:03:23 PDT 1994 By kalsow
//      Modified On Thu Mar  7 20:18:53 1991 By muller

public final class Subarray {
    private Subarray() {}

    private static CallExpr.MethodList Z;

    /** Called indirectly through MethodList. */
    private static Type.T typeOf(CallExpr.T ce) {
        return ArrayType.openCousin(Type.base(Expr.typeOf(ce.args[0])));
    }

    /** Called indirectly through MethodList. */
    private static void check(CallExpr.T ce, M3.CheckState cs) {
        Type.T t, u, v;
        Type.T[] index = new Type.T[1];
        Type.T[] elt = new Type.T[1];
        boolean IsOK = true;
        t = Type.base(Expr.typeOf(ce.args[0]));
        u = Expr.typeOf(ce.args[1]);
        v = Expr.typeOf(ce.args[2]);
        ce.type = ErrType.T; // May change.
        if ((!ArrayType.split(t, index, elt))) {
            Error.msg("SUBARRAY: first argument must be an array (2.6.3).");
            IsOK = false;
        }
        if ((!Type.isAssignable(Card.T, u))) {
            Error.msg("SUBARRAY: second argument must be assignable to CARDINAL (2.6.3).");
            IsOK = false;
        }
        if ((!Type.isAssignable(Card.T, v))) {
            Error.msg("SUBARRAY: third argument must be assignable to CARDINAL (2.6.3).");
            IsOK = false;
        }
        if (Math.floorMod(ArrayType.eltAlign(t), (long) Target.Byte) != 0) {
            Error.msg("CM3 restriction: SUBARRAY elements must be byte-aligned (2.2.5).");
            IsOK = false;
        }
        if (IsOK) {
            ce.args[1] = rtCheckNonNeg(ce.args[1], cs);
            ce.args[2] = rtCheckNonNeg(ce.args[2], cs);
            Expr.needsAddress(ce.args[0]);
            t = ArrayType.openCousin(t);
            ce.type = Type.check(t);
        }
    }

    private static Expr.T rtCheckNonNeg(Expr.T e, M3.CheckState cs) {
        int[] min = TInt.newInt();
        int[] max = TInt.newInt();
        if ((e == null)) {
            return null;
        }
        // TODO: Warn on statically-detectable negative value.
        Expr.getBounds(e, min, max);
        if (TInt.LT(min, TInt.Zero) || TInt.LT(max, min)) {
            e = CheckExpr.newLower(e, TInt.Zero, M3CG.RuntimeError.ValueOutOfRange);
            Expr.typeCheck(e, cs);
        }
        return e;
    }

    /** Called indirectly through MethodList. */
    private static void needsAddress(CallExpr.T ce) {
        // yes, all subarrays get memory addresses
    }

    /**
     * Called indirectly through MethodList.
     *
     * <p>TODO: Take advantage of static values of ce.args[1] and ce.args[2].
     */
    private static long subarrayExprAlign(CallExpr.T ce) {
        Expr.T arrayExpr = ce.args[0];
        Type.T arrayType = Type.base(Expr.typeOf(arrayExpr));
        long eltPack, align;
        long[] len = new long[1];
        if (OpenArrayType.is(arrayType)) {
            align = OpenArrayType.eltAlign(arrayType);
            eltPack = OpenArrayType.eltPack(arrayType);
        } else { // Fixed array.
            align = Expr.alignment(arrayExpr);
            if (IntegerExpr.toInt(Expr.constValue(ce.args[2]), len)) {
                if (len[0] <= 1) {
                    return align;
                }
            }
            eltPack = ArrayType.eltPack(arrayType);
        }
        return CG.GCD(align, eltPack);
    }

    /** Called indirectly through MethodList. */
    private static void prep(CallExpr.T ce) {
        prepLV(ce, false);
    }

    /** Called indirectly through MethodList, and directly from herein. */
    private static void prepLV(CallExpr.T ce, boolean traced) {
        Expr.T base = ce.args[0];
        Expr.T start = ce.args[1];
        Expr.T len = ce.args[2];
        Type.T array = Type.base(Expr.typeOf(base));
        Type.T open = ArrayType.openCousin(array);
        long src_depth = OpenArrayType.openDepth(array);
        long elt_pack = ArrayType.eltPack(array);

        Type.T[] index = new Type.T[1];
        Type.T[] element = new Type.T[1];
        M3CG.Var t_result;
        CG.Val t_base, t_start;
        long[] i_start = new long[1];
        long[] i_len = new long[1];
        int case_;
        int[] n_elts = TInt.newInt();
        int[] x_len = TInt.newInt();
        int[] x_start = TInt.newInt();
        int[] max = TInt.newInt();
        Type.compile(array);
        Type.compile(open);
        ArrayType.split(array, index, element);

        Expr.prepLValue(base, traced);

        // determine which case to use
        case_ = 0;
        if ((src_depth != 0)) {
            // Open array
            case_ = 0b100;
        } else {
            TInt.assign(n_elts, Type.number(index[0]));
        }
        if (getCard(start, i_start, x_start)) {
            // Constant stert
            case_ += 0b010;
        } else {
            Expr.prep(start);
        }
        if (getCard(len, i_len, x_len)) {
            // Constant length
            case_ += 0b001;
        } else {
            Expr.prep(len);
        }

        // declare space for the result
        t_result = OpenArrayType.declareDopeTemp(open);

        switch (case_) {
        case 0b000: { // fixed array, var start, var len ------------------------------

            // initialize the new count
            Expr.compile(len);
            CG.store_int(M3.target.Integer.cg_type, t_result, M3.rt.OA_size_0);

            if (!Host.doRangeChk) {
                Expr.compileAddress(base, traced);
                Expr.compile(start);
            } else {
                Expr.compile(start);         t_start = CG.pop();
                CG.push(t_start);
                CG.load_int(M3.target.Integer.cg_type, t_result, M3.rt.OA_size_0);
                CG.add(M3.target.Integer.cg_type);
                CG.check_hi(M3.target.Integer.cg_type, n_elts,
                            M3CG.RuntimeError.ValueOutOfRange);
                CG.discard(M3.target.Integer.cg_type);
                Expr.compileAddress(base, traced);
                CG.push(t_start);
                CG.free(t_start);
            }

            // initialize the new data pointer
            CG.index_bytes(elt_pack);
            CG.store_addr(t_result, M3.rt.OA_elt_ptr);
            break;
        }
        case 0b001: { // fixed array, var start, const len ----------------------------

            // initialize the new count
            CG.load_integer(M3.target.Integer.cg_type, x_len);
            CG.store_int(M3.target.Integer.cg_type, t_result, M3.rt.OA_size_0);

            if (!Host.doRangeChk) {
                Expr.compileAddress(base, traced);
                Expr.compile(start);
            } else if (!TInt.subtract(n_elts, x_len, max)) {
                // cannot compute n-len at compile time
                Expr.compile(start);         t_start = CG.pop();
                CG.push(t_start);
                CG.load_integer(M3.target.Integer.cg_type, x_len);
                CG.add(M3.target.Integer.cg_type);
                CG.check_hi(M3.target.Integer.cg_type,
                            n_elts, M3CG.RuntimeError.ValueOutOfRange);
                CG.discard(M3.target.Integer.cg_type);
                Expr.compileAddress(base, traced);
                CG.push(t_start);
                CG.free(t_start);
            } else if (TInt.LT(max, TInt.Zero)) {
                Error.warn(2, "SUBARRAY length out of range");
                Expr.compileAddress(base, traced);
                Expr.compile(start);
                CG.check_hi(M3.target.Integer.cg_type,
                            max, M3CG.RuntimeError.ValueOutOfRange);
            } else {
                Expr.compileAddress(base, traced);
                Expr.compile(start);
                CG.check_hi(M3.target.Integer.cg_type, max,
                            M3CG.RuntimeError.ValueOutOfRange);
            }

            // initialize the new data pointer
            CG.index_bytes(elt_pack);
            CG.store_addr(t_result, M3.rt.OA_elt_ptr);
            break;
        }
        case 0b010: { // fixed array, const start, var len ----------------------------

            if (!Host.doRangeChk) {
                // no check => initialize the new count
                Expr.compile(len);
                CG.store_int(M3.target.Integer.cg_type, t_result, M3.rt.OA_size_0);
            } else if (!TInt.subtract(n_elts, x_start, max)) {
                // initialize the new count
                Expr.compile(len);
                CG.store_int(M3.target.Integer.cg_type, t_result, M3.rt.OA_size_0);
                // cannot compute n-start at compile time
                CG.load_int(M3.target.Integer.cg_type, t_result, M3.rt.OA_size_0);
                CG.load_integer(M3.target.Integer.cg_type, x_start);
                CG.add(M3.target.Integer.cg_type);
                CG.check_hi(M3.target.Integer.cg_type, n_elts,
                            M3CG.RuntimeError.ValueOutOfRange);
                CG.discard(M3.target.Integer.cg_type);
            } else if (TInt.LT(max, TInt.Zero)) {
                // initialize and check the new count
                Error.warn(2, "SUBARRAY initial index out of range");
                Expr.compile(len);
                CG.check_hi(M3.target.Integer.cg_type,
                            max, M3CG.RuntimeError.ValueOutOfRange);
                CG.store_int(M3.target.Integer.cg_type, t_result, M3.rt.OA_size_0);
            } else {
                // initialize and check the new count
                Expr.compile(len);
                CG.check_hi(M3.target.Integer.cg_type, max,
                            M3CG.RuntimeError.ValueOutOfRange);
                CG.store_int(M3.target.Integer.cg_type, t_result, M3.rt.OA_size_0);
            }

            // initialize the new data pointer
            Expr.compileAddress(base, traced);
            CG.add_offset(i_start[0] * elt_pack);
            CG.store_addr(t_result, M3.rt.OA_elt_ptr);
            break;
        }
        case 0b011: { // fixed array, const start, const len --------------------------

            // initialize the new count
            CG.load_integer(M3.target.Integer.cg_type, x_len);
            CG.store_int(M3.target.Integer.cg_type, t_result, M3.rt.OA_size_0);

            if (!Host.doRangeChk) {
                // no check
            } else if (!TInt.add(x_start, x_len, max)) {
                // cannot compute start+len at compile time
                CG.load_integer(M3.target.Integer.cg_type, x_start);
                CG.load_integer(M3.target.Integer.cg_type, x_len);
                CG.add(M3.target.Integer.cg_type);
                CG.check_hi(M3.target.Integer.cg_type, n_elts,
                            M3CG.RuntimeError.ValueOutOfRange);
                CG.discard(M3.target.Integer.cg_type);
            } else if (TInt.LT(n_elts, max)) {
                // oops, they're too big
                Error.warn(2, "SUBARRAY start+length out of range (2.6.3).");
                CG.load_integer(M3.target.Integer.cg_type, max);
                CG.check_hi(M3.target.Integer.cg_type, n_elts,
                            M3CG.RuntimeError.ValueOutOfRange);
                CG.discard(M3.target.Integer.cg_type);
            } else {
                // ok
            }

            // initialize the new data pointer
            Expr.compileAddress(base, traced);
            CG.add_offset(i_start[0] * elt_pack);
            CG.store_addr(t_result, M3.rt.OA_elt_ptr);
            break;
        }
        case 0b100: { // open array, var start, var len -------------------------------

            Expr.compileAddress(base, traced);   t_base  = CG.pop();
            Expr.compile(start);                 t_start = CG.pop();

            // initialize the new counts
            Expr.compile(len);
            CG.store_int(M3.target.Integer.cg_type, t_result, M3.rt.OA_size_0);
            copyDopeVector(t_base, t_result, src_depth);

            if (Host.doRangeChk) {
                CG.push(t_start);
                CG.load_int(M3.target.Integer.cg_type, t_result, M3.rt.OA_size_0);
                CG.add(M3.target.Integer.cg_type);
                CG.push(t_base);
                CG.open_size(0);
                CG.subtract(M3.target.Integer.cg_type);
                CG.check_hi(M3.target.Integer.cg_type, TInt.Zero,
                            M3CG.RuntimeError.ValueOutOfRange);
                CG.discard(M3.target.Integer.cg_type);
            }

            // initialize the new data pointer
            CG.push(t_base);
            CG.open_elt_ptr(ArrayType.eltAlign(array));
            CG.push(t_start);
            computeOffset(t_result, src_depth, elt_pack);

            CG.free(t_base);
            CG.free(t_start);
            break;
        }
        case 0b101: { // open array, var start, const len -----------------------------

            Expr.compileAddress(base, traced);   t_base  = CG.pop();
            Expr.compile(start);                 t_start = CG.pop();

            // initialize the new counts
            CG.load_integer(M3.target.Integer.cg_type, x_len);
            CG.store_int(M3.target.Integer.cg_type, t_result, M3.rt.OA_size_0);
            copyDopeVector(t_base, t_result, src_depth);

            if (Host.doRangeChk) {
                CG.push(t_start);
                CG.load_integer(M3.target.Integer.cg_type, x_len);
                CG.add(M3.target.Integer.cg_type);
                CG.push(t_base);
                CG.open_size(0);
                CG.subtract(M3.target.Integer.cg_type);
                CG.check_hi(M3.target.Integer.cg_type, TInt.Zero,
                            M3CG.RuntimeError.ValueOutOfRange);
                CG.discard(M3.target.Integer.cg_type);
            }

            // initialize the new data pointer
            CG.push(t_base);
            CG.open_elt_ptr(ArrayType.eltAlign(array));
            CG.push(t_start);
            computeOffset(t_result, src_depth, elt_pack);

            CG.free(t_base);
            CG.free(t_start);
            break;
        }
        case 0b110: { // open array, const start, var len -----------------------------

            Expr.compileAddress(base, traced);   t_base  = CG.pop();

            // initialize the new counts
            Expr.compile(len);
            CG.store_int(M3.target.Integer.cg_type, t_result, M3.rt.OA_size_0);
            copyDopeVector(t_base, t_result, src_depth);

            if (Host.doRangeChk) {
                CG.load_int(M3.target.Integer.cg_type, t_result, M3.rt.OA_size_0);
                CG.load_integer(M3.target.Integer.cg_type, x_start);
                CG.add(M3.target.Integer.cg_type);
                CG.push(t_base);
                CG.open_size(0);
                CG.subtract(M3.target.Integer.cg_type);
                CG.check_hi(M3.target.Integer.cg_type, TInt.Zero,
                            M3CG.RuntimeError.ValueOutOfRange);
                CG.discard(M3.target.Integer.cg_type);
            }

            // initialize the new data pointer
            CG.push(t_base);
            CG.open_elt_ptr(ArrayType.eltAlign(array));
            if ((src_depth <= 1)) {
                CG.add_offset(i_start[0] * elt_pack);
                CG.store_addr(t_result, M3.rt.OA_elt_ptr);
            } else {
                CG.load_integer(M3.target.Integer.cg_type, x_start);
                computeOffset(t_result, src_depth, elt_pack);
            }

            CG.free(t_base);
            break;
        }
        case 0b111: { // open array, const start, const len ---------------------------

            Expr.compileAddress(base, traced);   t_base  = CG.pop();

            // initialize the new counts
            CG.load_integer(M3.target.Integer.cg_type, x_len);
            CG.store_int(M3.target.Integer.cg_type, t_result, M3.rt.OA_size_0);
            copyDopeVector(t_base, t_result, src_depth);

            if (!Host.doRangeChk) {
                // no check
            } else if (TInt.add(x_start, x_len, max)) {
                CG.load_integer(M3.target.Integer.cg_type, max);
                CG.push(t_base);
                CG.open_size(0);
                CG.subtract(M3.target.Integer.cg_type);
                CG.check_hi(M3.target.Integer.cg_type, TInt.Zero,
                            M3CG.RuntimeError.ValueOutOfRange);
                CG.discard(M3.target.Integer.cg_type);
            } else {
                CG.load_integer(M3.target.Integer.cg_type, x_start);
                CG.load_integer(M3.target.Integer.cg_type, x_len);
                CG.add(M3.target.Integer.cg_type);
                CG.push(t_base);
                CG.open_size(0);
                CG.subtract(M3.target.Integer.cg_type);
                CG.check_hi(M3.target.Integer.cg_type, TInt.Zero,
                            M3CG.RuntimeError.ValueOutOfRange);
                CG.discard(M3.target.Integer.cg_type);
            }

            // initialize the new data pointer
            CG.push(t_base);
            CG.open_elt_ptr(ArrayType.eltAlign(array));
            if ((src_depth <= 1)) {
                CG.add_offset(i_start[0] * elt_pack);
                CG.store_addr(t_result, M3.rt.OA_elt_ptr);
            } else {
                CG.load_integer(M3.target.Integer.cg_type, x_start);
                computeOffset(t_result, src_depth, elt_pack);
            }

            CG.free(t_base);
            break;
        }
        default:
            throw new AssertError("Subarray");
        } // CASE

        // leave the new subarray as the result.
        CG.load_addr_of_temp(t_result, 0, M3.target.Address.align);
        ce.tmp = CG.pop();

    }

    private static boolean getCard(Expr.T e, long[] i, int[] x) {
        Type.T[] t = new Type.T[1];
        e = Expr.constValue(e);
        if ((e == null)) {
            return false;
        }
        return IntegerExpr.split(e, x, t)
            && TInt.toInt(x, i)
            && (0 <= i[0]);
    }

    private static void copyDopeVector(CG.Val src, M3CG.Var dest, long depth) {
        for (long i = 1; i <= depth - 1; i++) {
            CG.push(src);
            CG.open_size(i);
            CG.store_int(M3.target.Integer.cg_type,
                         dest, M3.rt.OA_sizes + i * M3.target.Integer.pack);
        }
    }

    private static void computeOffset(M3CG.Var array, long depth, long elt_pack) {
        for (long i = 1; i <= depth - 1; i++) {
            CG.load_int(M3.target.Integer.cg_type,
                        array, M3.rt.OA_sizes + i * M3.target.Integer.pack);
            CG.multiply(M3.target.Integer.cg_type);
        }
        CG.index_bytes(elt_pack);
        CG.store_addr(array, M3.rt.OA_elt_ptr);
    }

    /** Called indirectly through MethodList. */
    private static void compile(CallExpr.T ce) {
        compileLV(ce, false);
    }

    /** Called indirectly through MethodList. */
    private static void compileLV(CallExpr.T ce, boolean traced) {
        // all the real work was done by PrepLV
        CG.push(ce.tmp);
        CG.boost_addr_alignment(M3.target.Address.align);
        CG.free(ce.tmp);
        ce.tmp = null;
    }

    /** Called indirectly through MethodList. */
    private static boolean isWritable(CallExpr.T ce, boolean lhs) {
        return Expr.isWritable(ce.args[0], lhs);
    }

    /** Called indirectly through MethodList. */
    private static boolean isDesignator(CallExpr.T ce, boolean lhs) {
        return Expr.isDesignator(ce.args[0]);
    }

    /** Called indirectly through MethodList. */
    private static void noteWrites(CallExpr.T ce) {
        Expr.noteWrite(ce.args[0]);
    }

    // EXPORTED:
    public static void initialize() {
        Z = CallExpr.newMethodList(3, 3, true, false, true, null,
                                   Subarray::typeOf,
                                   Subarray::typeOf,
                                   Subarray::needsAddress,
                                   Subarray::check,
                                   Subarray::prep,
                                   Subarray::compile,
                                   Subarray::prepLV,
                                   Subarray::compileLV,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   CallExpr::noValue, // fold
                                   CallExpr::noBounds,
                                   Subarray::isWritable,
                                   Subarray::isDesignator,
                                   Subarray::noteWrites,
                                   Subarray::subarrayExprAlign);
        Procedure.definePredefined("SUBARRAY", Z, true);
    }
}
