// Java port of CM3's m3-sys/m3middle/src/M3CG.i3 and M3CG.m3.
// Original: Copyright (C) 1993, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.middle;

import m3j.middle.Target.CGType;
import m3j.util.AssertError;

/**
 * Modula-3 code generator interface.
 *
 * <p>This interface in conjunction with M3CG_Ops provides an intermediate
 * language for compiler front ends to generate Modula-3 procedures
 * and object modules.   Its primary client is the Modula-3 compiler.
 * The bulk of this interface was derived (i.e. stolen) from "M2CG" by
 * John Ellis and John DeTreville.
 *
 * <p>The intermediate language (this interface) allows for different actual
 * code generators, providing two degrees of freedom:
 * <ul>
 * <li>multiple target architectures</li>
 * <li>code generators optimized for different goals (code quality
 *     versus fast turn-around)</li>
 * </ul>
 *
 * <p>The interface to the intermediate language is procedural: clients call
 * methods to construct intermediate-language programs.  Thus a
 * fast-turn-around code generator can compile the intermediate language
 * on the fly without much consing, while a high-quality slower code
 * generator may construct an entire intermediate-language module before
 * compiling it.
 *
 * <p>This interface defines a single object type, M3CG.T.  The operations
 * available on an M3CG.T are defined in M3CG_Ops.
 *
 * <p>Port: Type is {@link Target.CGType}; MType, ZType, AType, IType and
 * RType are subranges of it that are not represented separately. Name is
 * an M3ID.T ({@code int}), TypeUID an {@code int}, Label a {@code long}.
 */
public final class M3CG {
    private M3CG() {}

    public enum UnitKind { Interface, Module }

    // Changes to the compiler intermediate representation have to be
    // consistified in *many* places.  To help with compatibility problems,
    // here is a versioning system.  Through CIV_WC_size, there was no
    // explicit versioning.  Starting with CIV_ext1, the version can
    // optionally be placed into the IR.

    public static final long CIV_SRC = 0;
    public static final long CIV_PM3 = 1;
    /** The earliest to be vetted. From Critical Mass? */
    public static final long CIV_CM3 = 2;
    /** Jun 22, 2007. d099f0b. */
    public static final long CIV_AtomicOps1 = 3;
    /** Feb 17, 2009. e66950a. */
    public static final long CIV_AtomicOps2 = 4;
    /** Feb 3,  2010. 56e677d. */
    public static final long CIV_AtomicOps3 = 5;
    /** Mar 10, 2010. b352ffc. */
    public static final long CIV_extract = 6;
    /** Aug 27, 2010. 6ee95af. */
    public static final long CIV_pop_struct = 7;
    /** Aug 27, 2010. dda2a77. */
    public static final long CIV_RT_hook = 8;
    /** Feb 26, 2014. 655eeac. */
    public static final long CIV_WC_size = 9;
    public static final long CIV_ext1 = 10;

    // The code generator manipulates scalar values of the types
    // listed above.  The notation "sN.B" denotes the stack value
    // that is "N" elements from the top of stack and has the type
    // abbreviated by "B".  Only loads and stores manipulate
    // MTypes.  When loaded, values are sign-extended or zero-extended
    // as necessary to produce ZTypes.

    /** extra compile-time information for DIV and MOD */
    public enum Sign { Positive, Negative, Unknown }

    public enum CompareOp { EQ, NE, GT, GE, LT, LE }

    public enum ConvertOp { Round, Trunc, Floor, Ceiling }

    public enum MemoryOrder { Relaxed, Release, Acquire, AcquireRelease, Sequential }

    public enum AtomicOp { Add, Sub, Or, And, Xor }

    /** A op B  ===  B SwappedCompare[op] A */
    public static final CompareOp[] SwappedCompare = {
        CompareOp.EQ, CompareOp.NE, CompareOp.LT,
        CompareOp.LE, CompareOp.GT, CompareOp.GE };

    /** A op B  ===  NOT (A NotCompare[op] B) */
    public static final CompareOp[] NotCompare = {
        CompareOp.NE, CompareOp.EQ, CompareOp.LE,
        CompareOp.LT, CompareOp.GE, CompareOp.GT };

    /** represents a variable (BRANDED "M3CG.Var" OBJECT END) */
    public static class Var {}

    /** represents a procedure (BRANDED "M3CG.Proc" OBJECT END) */
    public static class Proc {}

    /* Frequency = [0..100]: estimated frequency that a branch will be taken or variable referenced. */
    public static final int Never = 0;
    public static final int Always = 100;
    public static final int Maybe = (Never + Always) / 2;
    public static final int Likely = (Never + 8 * Always) / 10;

    /**
     * TypeUID = [-16_7fffffff-1 .. 16_7fffffff]: a 32-bit unique id: a
     * fingerprint or hash of the type's structure.
     */
    public static final int NO_UID = -1;
    // TODO: There are duplicate declarations of NO_UID in m3front/src/type/TypeRep.i3,
    //       m3front/src/misc/M3String.m3, and m3front/src/misc/M3WString.m3.
    //       Also, m3cc/gcc*/gcc/m3cg/parse.c contain C declarations, and
    //       cm3ide/src/nodes/Type.i3 contains a declaration with zero as value.
    //       Consistify and centralize this.

    /**
     * Label = INTEGER: a unique numbering for each label.  The client is
     * responsible for allocating these id's with the 'next_label' method of
     * M3CG_OpsPublic.
     */
    public static final long No_label = -1;

    public enum RuntimeError {
        /** 0 &lt;*ASSERT*&gt; condition was FALSE. */
        AssertFailed,
        /** 1 An enumeration or subrange value was out of range. */
        ValueOutOfRange,
        /** 2 An array subscript was out of range. */
        SubscriptOutOfRange,
        /** 3 open array had the wrong shape */
        IncompatibleArrayShape,
        /** 4 attempted to reference a bad location in memory. */
        BadMemoryReference,
        /** 5 An explicit or implicit NARROW() operation failed. */
        NarrowFailed,
        /** 6 A function failed to return a value. */
        NoReturnValue,
        /** 7 no handler for the current CASE value. */
        UnhandledCase,
        /** 8 no handler for the given type in a TYPECASE stmt. */
        UnhandledTypecase,
        /** 9 A thread stack overflowed */
        StackOverflow,
        /** 10 NEW() was unable to allocate more memory. */
        OutOfMemory,
        /** 11 attempted to get the address of an unaligned value */
        UnalignedAddress,
        /** 12 An exception was raised, but not handled. */
        UnhandledException,
        /** 13 An exception was blocked by a RAISES clause. */
        BlockedException,
        /** 14 integer result too large to represent */
        IntegerOverflow,
        /** 15 Attempt to DIV or MOD by zero. */
        IntegerDivideByZero,
        /** 16 Attempted floating-point division by zero. */
        FloatDivideByZero,
        /** 17 floating-point result too large to represent */
        FloatOverflow,
        /** 18 floating-point result too small to represent */
        FloatUnderflow,
        /** 19 floating-point result is inexact */
        FloatInexact,
        /** 20 invalid floating-point argument */
        FloatInvalid,
        /** 21 two types with the same brand exist */
        DuplicateBrand,
        /** 22 a compile-time type is missing at link time */
        MissingType,
        /** 23 supertypes of an object form a cycle */
        SupertypeCycle,
        /** 24 multiple full revelations of an opaque type */
        OpaqueTypeRedefined,
        /** 25 partial revelations don't match type declarations */
        InconsistentRevelation,
        /** 26 a NIL-valued method was invoked */
        UndefinedMethod,
        /** 27 a privileged machine instruction was attempted */
        PrivilegedInstruction,
        /** 28 a low-level OS or machine error occurred */
        SystemError,
        /** 29 */
        Unknown
        // NOTE: going past 31 requires backend and runtime change
        // NOTE: This enumeration must be kept in synch with the version
        // of RuntimeError.T used by the runtime system.
    }

    /*----------------------------------------------------------- UIDs ----------*/

    private static final char[] UIDCharTbl = {
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
        'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

    public static String formatUID(int tUID) {
        char[] resultChars = new char[6];
        long remainder;
        if (tUID == NO_UID) {
            return "zzzzzz";
        } else {
            remainder = tUID & 0xFFFFFFFFL;
            // ^In case this runs on a 64-bit machine.
            for (int ri = 5; ri >= 0; ri--) {
                resultChars[ri] = UIDCharTbl[(int) Long.remainderUnsigned(remainder, 62)];
                remainder = Long.divideUnsigned(remainder, 62);
            }
            AssertError.check(remainder == 0, "M3CG");
            return new String(resultChars);
        }
    }

    /**
     * A code generator (T &lt;: ROOT). The default methods simply call the
     * corresponding method in 'child'.
     */
    public static class T extends M3CG_Ops.Public {

        @Override
        public long next_label(long n) {
            return child.next_label(n);
        }

        @Override
        public void set_error_handler(M3CG_Ops.ErrorHandler p) {
            child.set_error_handler(p);
        }

        @Override
        public void begin_unit(long optimize) {
            child.begin_unit(optimize);
        }

        @Override
        public void end_unit() {
            child.end_unit();
        }

        @Override
        public void import_unit(int n) {
            child.import_unit(n);
        }

        @Override
        public void export_unit(int n) {
            child.export_unit(n);
        }

        @Override
        public void set_source_file(String file) {
            child.set_source_file(file);
        }

        @Override
        public void set_source_line(long line) {
            child.set_source_line(line);
        }

        @Override
        public void declare_typename(int t, int n) {
            child.declare_typename(t, n);
        }

        @Override
        public void declare_array(int t, int index, int elt, long s, int element_typename) {
            child.declare_array(t, index, elt, s, element_typename);
        }

        @Override
        public void declare_open_array(int t, int elt, long s, int element_typename) {
            child.declare_open_array(t, elt, s, element_typename);
        }

        @Override
        public void declare_enum(int t, long n_elts, long s) {
            child.declare_enum(t, n_elts, s);
        }

        @Override
        public void declare_enum_elt(int n) {
            child.declare_enum_elt(n);
        }

        @Override
        public void declare_packed(int t, long s, int base, int base_typename) {
            // Sic: base_typename is not passed on; the child gets the default.
            child.declare_packed(t, s, base);
        }

        @Override
        public void declare_record(int t, long s, long n_fields) {
            child.declare_record(t, s, n_fields);
        }

        @Override
        public void declare_field(int n, long o, long s, int t, int typename) {
            child.declare_field(n, o, s, t, typename);
        }

        @Override
        public void declare_set(int t, int domain, long s, int domain_typename) {
            // Sic: domain_typename is not passed on; the child gets the default.
            child.declare_set(t, domain, s);
        }

        @Override
        public void declare_subrange(int t, int domain, int[] min, int[] max, long s, int domain_typename) {
            // Sic: domain_typename is not passed on; the child gets the default.
            child.declare_subrange(t, domain, min, max, s);
        }

        @Override
        public void declare_pointer(int t, int target, String brand, boolean traced, int target_typename) {
            child.declare_pointer(t, target, brand, traced, target_typename);
        }

        @Override
        public void declare_indirect(int t, int target, int target_typename) {
            child.declare_indirect(t, target, target_typename);
        }

        @Override
        public void declare_proctype(int t, long n_formals, int result, long n_raises, Target.CallingConvention cc, int result_typename) {
            child.declare_proctype(t, n_formals, result, n_raises, cc, result_typename);
        }

        @Override
        public void declare_formal(int n, int t, int typename) {
            child.declare_formal(n, t, typename);
        }

        @Override
        public void declare_raises(int n) {
            child.declare_raises(n);
        }

        @Override
        public void declare_object(int t, int super_, String brand, boolean traced, long n_fields, long n_methods, long field_size, long field_offset, long method_offset, int super_typename) {
            child.declare_object(t, super_, brand, traced, n_fields, n_methods, field_size, field_offset, method_offset, super_typename);
        }

        @Override
        public void declare_method(int n, int signature) {
            child.declare_method(n, signature);
        }

        @Override
        public void declare_opaque(int t, int super_) {
            child.declare_opaque(t, super_);
        }

        @Override
        public void reveal_opaque(int lhs, int rhs) {
            child.reveal_opaque(lhs, rhs);
        }

        @Override
        public void declare_exception(int n, int arg_type, boolean raise_proc, Var base, long offset) {
            child.declare_exception(n, arg_type, raise_proc, base, offset);
        }

        @Override
        public void widechar_size(long bitsize) {
            child.widechar_size(bitsize);
        }

        @Override
        public void set_runtime_proc(int n, Proc p) {
            child.set_runtime_proc(n, p);
        }

        @Override
        public Var import_global(int n, long s, long a, CGType t, int m3t, int typename) {
            return child.import_global(n, s, a, t, m3t, typename);
        }

        @Override
        public Var declare_segment(int n, int m3t, boolean is_const) {
            return child.declare_segment(n, m3t, is_const);
        }

        @Override
        public void bind_segment(Var seg, long s, long a, CGType t, boolean exported, boolean init) {
            child.bind_segment(seg, s, a, t, exported, init);
        }

        @Override
        public Var declare_global(int n, long s, long a, CGType t, int m3t, boolean exported, boolean init, int typename) {
            // Sic: typename is not passed on; the child gets the default.
            return child.declare_global(n, s, a, t, m3t, exported, init);
        }

        @Override
        public Var declare_constant(int n, long s, long a, CGType t, int m3t, boolean exported, boolean init, int typename) {
            return child.declare_constant(n, s, a, t, m3t, exported, init, typename);
        }

        @Override
        public Var declare_local(int n, long s, long a, CGType t, int m3t, boolean in_memory, boolean up_level, int f, int typename) {
            return child.declare_local(n, s, a, t, m3t, in_memory, up_level, f, typename);
        }

        @Override
        public Var declare_param(int n, long s, long a, CGType t, int m3t, boolean in_memory, boolean up_level, int f, int typename) {
            return child.declare_param(n, s, a, t, m3t, in_memory, up_level, f, typename);
        }

        @Override
        public Var declare_temp(long s, long a, CGType t, boolean in_memory, int typename) {
            return child.declare_temp(s, a, t, in_memory, typename);
        }

        @Override
        public void free_temp(Var v) {
            child.free_temp(v);
        }

        @Override
        public void begin_init(Var v) {
            child.begin_init(v);
        }

        @Override
        public void end_init(Var v) {
            child.end_init(v);
        }

        @Override
        public void init_int(long o, int[] value, CGType t) {
            child.init_int(o, value, t);
        }

        @Override
        public void init_proc(long o, Proc value) {
            child.init_proc(o, value);
        }

        @Override
        public void init_label(long o, long value) {
            child.init_label(o, value);
        }

        @Override
        public void init_var(long o, Var value, long bias) {
            child.init_var(o, value, bias);
        }

        @Override
        public void init_offset(long o, Var var) {
            child.init_offset(o, var);
        }

        @Override
        public void init_chars(long o, String value) {
            child.init_chars(o, value);
        }

        @Override
        public void init_float(long o, Target.Float f) {
            child.init_float(o, f);
        }

        @Override
        public Proc import_procedure(int n, long n_params, CGType return_, Target.CallingConvention cc, int return_typeid, int return_typename) {
            return child.import_procedure(n, n_params, return_, cc, return_typeid, return_typename);
        }

        @Override
        public Proc declare_procedure(int n, long n_params, CGType return_, long lev, Target.CallingConvention cc, boolean exported, Proc parent, int return_typeid, int return_typename) {
            return child.declare_procedure(n, n_params, return_, lev, cc, exported, parent, return_typeid, return_typename);
        }

        @Override
        public void begin_procedure(Proc p) {
            child.begin_procedure(p);
        }

        @Override
        public void end_procedure(Proc p) {
            child.end_procedure(p);
        }

        @Override
        public void begin_block() {
            child.begin_block();
        }

        @Override
        public void end_block() {
            child.end_block();
        }

        @Override
        public void note_procedure_origin(Proc p) {
            child.note_procedure_origin(p);
        }

        @Override
        public void set_label(long l, boolean barrier) {
            child.set_label(l, barrier);
        }

        @Override
        public void jump(long l) {
            child.jump(l);
        }

        @Override
        public void if_true(CGType t, long l, int f) {
            child.if_true(t, l, f);
        }

        @Override
        public void if_false(CGType t, long l, int f) {
            child.if_false(t, l, f);
        }

        @Override
        public void if_compare(CGType t, CompareOp op, long l, int f) {
            child.if_compare(t, op, l, f);
        }

        @Override
        public void case_jump(CGType t, long[] labels) {
            child.case_jump(t, labels);
        }

        @Override
        public void exit_proc(CGType t) {
            child.exit_proc(t);
        }

        @Override
        public void load(Var v, long o, CGType t, CGType u) {
            child.load(v, o, t, u);
        }

        @Override
        public void load_address(Var v, long o) {
            child.load_address(v, o);
        }

        @Override
        public void load_indirect(long o, CGType t, CGType u) {
            child.load_indirect(o, t, u);
        }

        @Override
        public void store(Var v, long o, CGType t, CGType u) {
            child.store(v, o, t, u);
        }

        @Override
        public void store_indirect(long o, CGType t, CGType u) {
            child.store_indirect(o, t, u);
        }

        @Override
        public void load_nil() {
            child.load_nil();
        }

        @Override
        public void load_integer(CGType t, int[] i) {
            child.load_integer(t, i);
        }

        @Override
        public void load_float(CGType t, Target.Float f) {
            child.load_float(t, f);
        }

        @Override
        public void compare(CGType t, CGType u, CompareOp op) {
            child.compare(t, u, op);
        }

        @Override
        public void add(CGType t) {
            child.add(t);
        }

        @Override
        public void subtract(CGType t) {
            child.subtract(t);
        }

        @Override
        public void multiply(CGType t) {
            child.multiply(t);
        }

        @Override
        public void divide(CGType t) {
            child.divide(t);
        }

        @Override
        public void negate(CGType t) {
            child.negate(t);
        }

        @Override
        public void abs(CGType t) {
            child.abs(t);
        }

        @Override
        public void max(CGType t) {
            child.max(t);
        }

        @Override
        public void min(CGType t) {
            child.min(t);
        }

        @Override
        public void cvt_int(CGType t, CGType u, ConvertOp op) {
            child.cvt_int(t, u, op);
        }

        @Override
        public void cvt_float(CGType t, CGType u) {
            child.cvt_float(t, u);
        }

        @Override
        public void div(CGType t, Sign a, Sign b) {
            child.div(t, a, b);
        }

        @Override
        public void mod(CGType t, Sign a, Sign b) {
            child.mod(t, a, b);
        }

        @Override
        public void set_union(long s) {
            child.set_union(s);
        }

        @Override
        public void set_difference(long s) {
            child.set_difference(s);
        }

        @Override
        public void set_intersection(long s) {
            child.set_intersection(s);
        }

        @Override
        public void set_sym_difference(long s) {
            child.set_sym_difference(s);
        }

        @Override
        public void set_member(long s, CGType t) {
            child.set_member(s, t);
        }

        @Override
        public void set_compare(long s, CompareOp op, CGType t) {
            child.set_compare(s, op, t);
        }

        @Override
        public void set_range(long s, CGType t) {
            child.set_range(s, t);
        }

        @Override
        public void set_singleton(long s, CGType t) {
            child.set_singleton(s, t);
        }

        @Override
        public void not(CGType t) {
            child.not(t);
        }

        @Override
        public void and(CGType t) {
            child.and(t);
        }

        @Override
        public void or(CGType t) {
            child.or(t);
        }

        @Override
        public void xor(CGType t) {
            child.xor(t);
        }

        @Override
        public void shift(CGType t) {
            child.shift(t);
        }

        @Override
        public void shift_left(CGType t) {
            child.shift_left(t);
        }

        @Override
        public void shift_right(CGType t) {
            child.shift_right(t);
        }

        @Override
        public void rotate(CGType t) {
            child.rotate(t);
        }

        @Override
        public void rotate_left(CGType t) {
            child.rotate_left(t);
        }

        @Override
        public void rotate_right(CGType t) {
            child.rotate_right(t);
        }

        @Override
        public void widen(boolean sign) {
            child.widen(sign);
        }

        @Override
        public void chop() {
            child.chop();
        }

        @Override
        public void extract(CGType t, boolean sign) {
            child.extract(t, sign);
        }

        @Override
        public void extract_n(CGType t, boolean sign, long n) {
            child.extract_n(t, sign, n);
        }

        @Override
        public void extract_mn(CGType t, boolean sign, long m, long n) {
            child.extract_mn(t, sign, m, n);
        }

        @Override
        public void insert(CGType t) {
            child.insert(t);
        }

        @Override
        public void insert_n(CGType t, long n) {
            child.insert_n(t, n);
        }

        @Override
        public void insert_mn(CGType t, long m, long n) {
            child.insert_mn(t, m, n);
        }

        @Override
        public void swap(CGType a, CGType b) {
            child.swap(a, b);
        }

        @Override
        public void pop(CGType t) {
            child.pop(t);
        }

        @Override
        public void copy_n(CGType u, CGType t, boolean overlap) {
            child.copy_n(u, t, overlap);
        }

        @Override
        public void copy(long n, CGType t, boolean overlap) {
            child.copy(n, t, overlap);
        }

        @Override
        public void zero_n(CGType u, CGType t) {
            child.zero_n(u, t);
        }

        @Override
        public void zero(long n, CGType t) {
            child.zero(n, t);
        }

        @Override
        public void loophole(CGType from, CGType two) {
            child.loophole(from, two);
        }

        @Override
        public void abort(RuntimeError code) {
            child.abort(code);
        }

        @Override
        public void check_nil(RuntimeError code) {
            child.check_nil(code);
        }

        @Override
        public void check_lo(CGType t, int[] i, RuntimeError code) {
            child.check_lo(t, i, code);
        }

        @Override
        public void check_hi(CGType t, int[] i, RuntimeError code) {
            child.check_hi(t, i, code);
        }

        @Override
        public void check_range(CGType t, int[] a, int[] b, RuntimeError code) {
            child.check_range(t, a, b, code);
        }

        @Override
        public void check_index(CGType t, RuntimeError code) {
            child.check_index(t, code);
        }

        @Override
        public void check_eq(CGType t, RuntimeError code) {
            child.check_eq(t, code);
        }

        @Override
        public void add_offset(long i) {
            child.add_offset(i);
        }

        @Override
        public void index_address(CGType t, long size) {
            child.index_address(t, size);
        }

        @Override
        public void start_call_direct(Proc p, long lev, CGType t) {
            child.start_call_direct(p, lev, t);
        }

        @Override
        public void call_direct(Proc p, CGType t) {
            child.call_direct(p, t);
        }

        @Override
        public void start_call_indirect(CGType t, Target.CallingConvention cc) {
            child.start_call_indirect(t, cc);
        }

        @Override
        public void call_indirect(CGType t, Target.CallingConvention cc) {
            child.call_indirect(t, cc);
        }

        @Override
        public void start_try() {
            child.start_try();
        }

        @Override
        public void end_try() {
            child.end_try();
        }

        @Override
        public void invoke_direct(Proc p, CGType t, long next, long handler) {
            child.invoke_direct(p, t, next, handler);
        }

        @Override
        public void invoke_indirect(CGType t, Target.CallingConvention cc, long next, long handler) {
            child.invoke_indirect(t, cc, next, handler);
        }

        @Override
        public void landing_pad(CGType t, long handler, int[] catches) {
            child.landing_pad(t, handler, catches);
        }

        @Override
        public void pop_param(CGType t) {
            child.pop_param(t);
        }

        @Override
        public void pop_struct(int t, long s, long a) {
            child.pop_struct(t, s, a);
        }

        @Override
        public void pop_static_link() {
            child.pop_static_link();
        }

        @Override
        public void load_procedure(Proc p) {
            child.load_procedure(p);
        }

        @Override
        public void load_static_link(Proc p) {
            child.load_static_link(p);
        }

        @Override
        public void comment(String a, String b, String c, String d) {
            child.comment(a, b, c, d);
        }

        @Override
        public void store_ordered(CGType t, CGType u, MemoryOrder order) {
            child.store_ordered(t, u, order);
        }

        @Override
        public void load_ordered(CGType t, CGType u, MemoryOrder order) {
            child.load_ordered(t, u, order);
        }

        @Override
        public void exchange(CGType t, CGType u, MemoryOrder order) {
            child.exchange(t, u, order);
        }

        @Override
        public void compare_exchange(CGType t, CGType u, CGType r, MemoryOrder success, MemoryOrder failure) {
            child.compare_exchange(t, u, r, success, failure);
        }

        @Override
        public void fence(MemoryOrder o) {
            child.fence(o);
        }

        @Override
        public void fetch_and_op(AtomicOp op, CGType t, CGType u, MemoryOrder order) {
            child.fetch_and_op(op, t, u, order);
        }
    }
}
