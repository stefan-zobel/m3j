// Java port of CM3's m3-sys/m3middle/src/M3CG_Ops.i3.
// Original: Copyright (C) 1993, Digital Equipment Corporation, and
// Portions Copyright 1996-2000, Critical Mass, Inc.
// Distributed under the licenses in licenses/COPYRIGHT-DEC and
// licenses/COPYRIGHT-CMASS.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.middle;

import m3j.middle.Target.CGType;

/**
 * Modula-3 code generator operations.
 *
 * <p>This interface defines the myriad operations that are available
 * on an M3CG.T.
 *
 * <p>Port: the object type Public is an abstract class; M3CG.T extends it.
 * Method names are those of the original. The parameter types map as
 * follows: Name (M3ID.T) and TypeUID are {@code int}s; INTEGER, CARDINAL,
 * Label, the sizes, offsets and alignments are {@code long}s; Frequency is an
 * {@code int}; Type and its subranges (MType, ZType, AType, IType, RType)
 * are {@link CGType}s, which are not range-checked; Target.Int is an
 * {@code int[]}. Parameters named "return" and "super" are "return_" and
 * "super_". The defaults of
 * trailing parameters are overloads.
 */
public final class M3CG_Ops {
    private M3CG_Ops() {}

    /** ErrorHandler = PROCEDURE (msg: TEXT). */
    @FunctionalInterface
    public interface ErrorHandler {
        void apply(String msg);
    }

    /** REVEAL M3CG.T &lt;: Public. */
    public abstract static class Public {

        /*------------------------------------------------ READONLY configuration ---*/

        /**
         * The default methods simply call the corresponding method in 'child',
         * hence a vanilla 'M3CG.T' can be used as a filter where you override
         * only the methods of interest.
         */
        public M3CG.T child = null;

        /*---------------------------------------- ID counters ---*/

        /**
         * reserve 'n' consecutive labels and return the first one
         */
        public abstract long next_label(long n);

        /** next_label with the default of the last 1 parameter. */
        public final long next_label() {
            return next_label(1);
        }

        /*---------------------------------------- READONLY configuration ---*/

        /**
         * Register 'p' as a callback, which a consumer of this IR will call to communicate
         *   failures (i.e. creating a stack or module that's too big) back to the producer.
         *   This mechanism is not used for client or implementation programming errors (bugs),
         *   which result in crashes.  This mechanism only works if the producer and consumer
         *   of the IR are in the same executable.  Otherwise, 'p' will not be called.
         */
        public abstract void set_error_handler(ErrorHandler p);

        /*---------------------------------------- compilation units ---*/

        /**
         * called before any other method to initialize the compilation unit.
         */
        public abstract void begin_unit(long optimize);

        /** begin_unit with the default of the last 1 parameter. */
        public final void begin_unit() {
            begin_unit(0);
        }

        /**
         * called after all other methods to finalize the unit and write the
         *   resulting object.
         */
        public abstract void end_unit();

        public abstract void import_unit(int n);

        /**
         * note that the current compilation unit imports/exports the interface 'n'
         */
        public abstract void export_unit(int n);

        /*---------------------------------------- debugging line numbers ---*/

        public abstract void set_source_file(String file);

        /**
         * Sets the current source file and line number.  Subsequent statements
         *   and expressions are associated with this source location.
         */
        public abstract void set_source_line(long line);

        /*---------------------------------------- debugging type declarations ---*/

        /**
         * Associate the name 'n', in the current scope, with type 't'.
         *   There can be multiple names for a TypeUID, in multiple scopes.
         *   'n' can contain a character-coded line number, for an anonymous
         *   type expression.
         */
        public abstract void declare_typename(int t, int n);

        public abstract void declare_array(int t, int index, int elt, long s, int element_typename);

        /** declare_array with the default of the last 1 parameter. */
        public final void declare_array(int t, int index, int elt, long s) {
            declare_array(t, index, elt, s, M3ID.NoID);
        }

        /**
         * s describes the dope vector
         */
        public abstract void declare_open_array(int t, int elt, long s, int element_typename);

        /** declare_open_array with the default of the last 1 parameter. */
        public final void declare_open_array(int t, int elt, long s) {
            declare_open_array(t, elt, s, M3ID.NoID);
        }

        /**
         * Enumeration type.  The subsequent n_elts occurrences of declare_enum_elt
         *   give the value names.  These all preceed the next occurrence of declare_enum.
         */
        public abstract void declare_enum(int t, long n_elts, long s);

        /**
         * An element of the most recent declare_enum type, in order.
         */
        public abstract void declare_enum_elt(int n);

        public abstract void declare_packed(int t, long s, int base, int base_typename);

        /** declare_packed with the default of the last 1 parameter. */
        public final void declare_packed(int t, long s, int base) {
            declare_packed(t, s, base, M3ID.NoID);
        }

        /**
         * Record type.  The subsequent n_fields occurrences of declare_field
         *   define the fields.  These all preceed the next occurrence of declare_record
         *   or declare_object.
         */
        public abstract void declare_record(int t, long s, long n_fields);

        /**
         * A field of the most recent declare record or declare_object.
         */
        public abstract void declare_field(int n, long o, long s, int t, int typename);

        /** declare_field with the default of the last 1 parameter. */
        public final void declare_field(int n, long o, long s, int t) {
            declare_field(n, o, s, t, M3ID.NoID);
        }

        public abstract void declare_set(int t, int domain, long s, int domain_typename);

        /** declare_set with the default of the last 1 parameter. */
        public final void declare_set(int t, int domain, long s) {
            declare_set(t, domain, s, M3ID.NoID);
        }

        public abstract void declare_subrange(int t, int domain, int[] min, int[] max, long s, int domain_typename);

        /** declare_subrange with the default of the last 1 parameter. */
        public final void declare_subrange(int t, int domain, int[] min, int[] max, long s) {
            declare_subrange(t, domain, min, max, s, M3ID.NoID);
        }

        /**
         * 'brand'=NIL ==&gt; t is unbranded
         */
        public abstract void declare_pointer(int t, int target, String brand, boolean traced, int target_typename);

        /** declare_pointer with the default of the last 1 parameter. */
        public final void declare_pointer(int t, int target, String brand, boolean traced) {
            declare_pointer(t, target, brand, traced, M3ID.NoID);
        }

        /**
         * an automatically dereferenced pointer! (WITH variables, VAR formals, ...)
         */
        public abstract void declare_indirect(int t, int target, int target_typename);

        /** declare_indirect with the default of the last 1 parameter. */
        public final void declare_indirect(int t, int target) {
            declare_indirect(t, target, M3ID.NoID);
        }

        /**
         * Procedure type.  The subsequent n_formals occurrences of declare_formal
         *   define the formal parameters.  The subsequent n_raises occurrences of
         *   declare_raises are names of raised exceptions.  n_raises &lt; 0 =&gt; RAISES ANY.
         *   These occurrences of declare_formal and declare_raises all preceed the next
         *   occurrence of declare_proctype.
         */
        public abstract void declare_proctype(int t, long n_formals, int result, long n_raises, Target.CallingConvention cc, int result_typename);

        /** declare_proctype with the default of the last 1 parameter. */
        public final void declare_proctype(int t, long n_formals, int result, long n_raises, Target.CallingConvention cc) {
            declare_proctype(t, n_formals, result, n_raises, cc, M3ID.NoID);
        }

        /**
         * A formal of the most recent procedure *type*, i.e., introduced by
         *   declare_proctype.
         */
        public abstract void declare_formal(int n, int t, int typename);

        /** declare_formal with the default of the last 1 parameter. */
        public final void declare_formal(int n, int t) {
            declare_formal(n, t, M3ID.NoID);
        }

        /**
         * A raised exception of the most recent procedure *type*, i.e., introduced by
         *   declare_proctype.  'n' is qualified, unmangled (i.e., with a dot.
         */
        public abstract void declare_raises(int n);

        /**
         * Object type.  The subsequent 'n_fields' occurrences of declare_field
         *   define the fields.  The subsequent 'n_methods' occurrences of declare_method
         *   define the methods.  These occurrences of declare_field and declare_formal all
         *   preceed the next occurrence of declare_object or declare_record.
         *   'brand'=NIL ==&gt; t is unbranded.
         */
        public abstract void declare_object(int t, int super_, String brand, boolean traced, long n_fields, long n_methods, long field_size, long field_offset, long method_offset, int super_typename);

        /** declare_object with the default of the last 1 parameter. */
        public final void declare_object(int t, int super_, String brand, boolean traced, long n_fields, long n_methods, long field_size, long field_offset, long method_offset) {
            declare_object(t, super_, brand, traced, n_fields, n_methods, field_size, field_offset, method_offset, M3ID.NoID);
        }

        /**
         * A method of the most recent declare_object.
         */
        public abstract void declare_method(int n, int signature);

        /**
         * TYPE t &lt;: super, declared in this compilation unit.
         * NOTE: Since an opaque type can never be anonymous, there will always
         *         be a declare_typename(t,n) elsewhere in this compilation.
         */
        public abstract void declare_opaque(int t, int super_);

        /**
         * REVEAL lhs = rhs, in containing compilation unit.
         */
        public abstract void reveal_opaque(int lhs, int rhs);

        /**
         * Declare an exception in the current compilation unit, named 'n', identified
         *   with the address 'base+offset', that carries an argument of type 'arg_type'.
         *   If 'raise_proc', then 'base+offset+BYTESIZE(ADDRESS)' will contain at
         *   runtime, a pointer to a compiler-generated procedure that packages the
         *   argument and calls the runtime to raise the exception.
         */
        public abstract void declare_exception(int n, int arg_type, boolean raise_proc, M3CG.Var base, long offset);

        /**
         * Communicates the runtime BITSIZE of WIDECHAR.
         * CIV_WC_size
         */
        public abstract void widechar_size(long bitsize);

        /*---------------------------------------- runtime hooks ---*/

        /**
         * declares 'n' as the simple name of a runtime procedure 'p'.
         */
        public abstract void set_runtime_proc(int n, M3CG.Proc p);

        /*---------------------------------------- variable declarations ---*/

        /**
         * imports the specified global variable.  Only used for an &lt;*EXTERNAL*&gt;
         *   variable.  n is its simple external name.
         */
        public abstract M3CG.Var import_global(int n, long s, long a, CGType t, int m3t, int typename);

        /** import_global with the default of the last 1 parameter. */
        public final M3CG.Var import_global(int n, long s, long a, CGType t, int m3t) {
            return import_global(n, s, a, t, m3t, M3ID.NoID);
        }

        public abstract M3CG.Var declare_segment(int n, int m3t, boolean is_const);

        /**
         * Together declare_segment and bind_segment accomplish what
         *   declare_global("is_const = FALSE") or declare_constant("is_const=TRUE")
         *   does, but declare_segment returns to the front-end, a
         *   handle on the variable before its size, type, or initial values
         *   are known.  Every declared segment must be bound exactly once.
         */
        public abstract void bind_segment(M3CG.Var seg, long s, long a, CGType t, boolean exported, boolean init);

        /**
         * Declare a global variable, local to the current unit.
         * 'n' is a mangled linker name.
         * As of 2016-12-7, this is generated only by coverage generation, for a
         *   statistics block, and that has a null name.
         */
        public abstract M3CG.Var declare_global(int n, long s, long a, CGType t, int m3t, boolean exported, boolean init, int typename);

        /** declare_global with the default of the last 1 parameter. */
        public final M3CG.Var declare_global(int n, long s, long a, CGType t, int m3t, boolean exported, boolean init) {
            return declare_global(n, s, a, t, m3t, exported, init, M3ID.NoID);
        }

        /**
         * Declare a read-only global variable, local to the current unit.
         * 'n' is a mangled linker name.
         * As of 2016-12-07, this is not generated by the front end.
         */
        public abstract M3CG.Var declare_constant(int n, long s, long a, CGType t, int m3t, boolean exported, boolean init, int typename);

        /** declare_constant with the default of the last 1 parameter. */
        public final M3CG.Var declare_constant(int n, long s, long a, CGType t, int m3t, boolean exported, boolean init) {
            return declare_constant(n, s, a, t, m3t, exported, init, M3ID.NoID);
        }

        /**
         * Declare a local variable.  This can appear either within a
         *   signature, (i.e., after a declare_procedure but before its
         *   begin_procedure) or inside the body, (i.e., within a
         *   begin_procedure/end_procedure pair.)
         *
         *   In a signature, it may be interspersed with declare_param
         *   occurences.  If it has name 'n' of "_result", it is a compiler-
         *   generated local to hold the result of scalar function result.
         *   (Compare this to declare_param named "_result".)
         *
         *   Note that declare_local does not occur as part of an imported
         *   signature, i.e., attached to an occurrence of import_procedure.  An
         *   imported signature can occur inside a body, but any declare_local
         *   there belongs to the procedure body, not the imported signature.
         *
         *   Inside a body, the local belongs to the closest enclosing
         *   begin_block/end_block pair, which denotes its lifetime and scope.
         *   Note that every body has a begin_block/end_block pair for the body
         *   itself.
         */
        public abstract M3CG.Var declare_local(int n, long s, long a, CGType t, int m3t, boolean in_memory, boolean up_level, int f, int typename);

        /** declare_local with the default of the last 1 parameter. */
        public final M3CG.Var declare_local(int n, long s, long a, CGType t, int m3t, boolean in_memory, boolean up_level, int f) {
            return declare_local(n, s, a, t, m3t, in_memory, up_level, f, M3ID.NoID);
        }

        /**
         * Declare a formal parameter, belonging to the most recent
         *   declare_procedure or import_procedure.  Formals are declared in
         *   their lexical order, relative to each other, but many other things
         *   can be interspersed.  import_procedure and its following parameters
         *   could be inside the body of a different procedure, i.e., between
         *   begin_procedure and end_procedure.
         *
         *   NOTE: If 'n' is "_result", we are in the signature (i.e., between the
         *   declare_procedure and its matching begin_procedure) of a function
         *   procedure with a nonscalar result, and this is a compiler-generated
         *   VAR parameter used to return the result.
         */
        public abstract M3CG.Var declare_param(int n, long s, long a, CGType t, int m3t, boolean in_memory, boolean up_level, int f, int typename);

        /** declare_param with the default of the last 1 parameter. */
        public final M3CG.Var declare_param(int n, long s, long a, CGType t, int m3t, boolean in_memory, boolean up_level, int f) {
            return declare_param(n, s, a, t, m3t, in_memory, up_level, f, M3ID.NoID);
        }

        /**
         * Declare an anonymous local temporary variable.  Temps are declared
         *   and freed between their containing procedure's begin_procedure and
         *   end_procedure calls.  Temps are never referenced by nested procedures.
         */
        public abstract M3CG.Var declare_temp(long s, long a, CGType t, boolean in_memory, int typename);

        /** declare_temp with the default of the last 1 parameter. */
        public final M3CG.Var declare_temp(long s, long a, CGType t, boolean in_memory) {
            return declare_temp(s, a, t, in_memory, M3ID.NoID);
        }

        /**
         * Release the space occupied by temp 'v' prior to its containing end_procedure,
         *   so that it may be reused by other new temporaries.
         */
        public abstract void free_temp(M3CG.Var v);

        /*---------------------------------------- static variable initialization ---*/

        public abstract void begin_init(M3CG.Var v);

        /**
         * Must bracket any init_* calls
         */
        public abstract void end_init(M3CG.Var v);

        /**
         * initializes the integer static variable at 'ADR(v)+o' with
         *   the low order bits of 'value' which is of integer type 't'.
         */
        public abstract void init_int(long o, int[] value, CGType t);

        /**
         * initializes the static variable at 'ADR(v)+o' with the address
         *   of procedure 'value'.
         */
        public abstract void init_proc(long o, M3CG.Proc value);

        /**
         * initializes the static variable at 'ADR(v)+o' with the address
         *   of the label 'value'.
         */
        public abstract void init_label(long o, long value);

        /**
         * (init_address_of_var would have been a more meaningful name here.)
         *   initializes the static variable at 'ADR(v)+o' with the address
         *   of 'value+bias'.
         */
        public abstract void init_var(long o, M3CG.Var value, long bias);

        /**
         * initializes the static variable at 'ADR(v)+o' with the integer
         *   frame offset of the local variable 'var' relative to the frame
         *   pointers returned at runtime in RTStack.Frames
         */
        public abstract void init_offset(long o, M3CG.Var var);

        /**
         * initializes the static variable at 'ADR(v)+o' with the characters
         *   of 'value', each of which will be in ISO 8859-1.
         */
        public abstract void init_chars(long o, String value);

        /**
         * initializes the static variable at 'ADR(v)+o' with the
         *   floating point value 'f'
         */
        public abstract void init_float(long o, Target.Float f);

        /*---------------------------------------- procedures ---*/

        /**
         * declare and import the external procedure with name 'n' and 'n_params'
         *   formal parameters.  It must be a top-level (=0) procedure that returns
         *   values of type 'return'.  'cc' is derived from the procedure's &lt;*EXTERNAL*&gt;
         *   pragma.  The formal parameters are specified by the subsequent 'n_params'
         *   'declare_param' calls.  'n' is a mangled linker name
         *   (e.g.: 'Interface__Procedure').
         */
        public abstract M3CG.Proc import_procedure(int n, long n_params, CGType return_, Target.CallingConvention cc, int return_typeid, int return_typename);

        /** import_procedure with the default of the last 1 parameter. */
        public final M3CG.Proc import_procedure(int n, long n_params, CGType return_, Target.CallingConvention cc, int return_typeid) {
            return import_procedure(n, n_params, return_, cc, return_typeid, M3ID.NoID);
        }

        /** import_procedure with the defaults of the last 2 parameters. */
        public final M3CG.Proc import_procedure(int n, long n_params, CGType return_, Target.CallingConvention cc) {
            return import_procedure(n, n_params, return_, cc, 0, M3ID.NoID);
        }

        /**
         * Declare a procedure with simple name 'n' within the current scope,
         *   with 'n_params' formal parameters, at static nesting level 'lev'.
         *   Set the "current procedure" to this procedure.  If the name n is M3ID.NoID,
         *   a new unique name will be supplied by the back-end.  The type of the
         *   procedure's result is specifed in 'return'.  If the new procedure is a
         *   nested procedure (level &gt; 1) then 'parent' is the immediately enclosing
         *   procedure, otherwise 'parent' is NIL.  The formal parameters are specified
         *   by the subsequent 'n_params' 'declare_param' calls.  'n' is a mangled linker
         *   name (e.g.: 'Interface__Procedure').
         */
        public abstract M3CG.Proc declare_procedure(int n, long n_params, CGType return_, long lev, Target.CallingConvention cc, boolean exported, M3CG.Proc parent, int return_typeid, int return_typename);

        /** declare_procedure with the default of the last 1 parameter. */
        public final M3CG.Proc declare_procedure(int n, long n_params, CGType return_, long lev, Target.CallingConvention cc, boolean exported, M3CG.Proc parent, int return_typeid) {
            return declare_procedure(n, n_params, return_, lev, cc, exported, parent, return_typeid, M3ID.NoID);
        }

        /** declare_procedure with the defaults of the last 2 parameters. */
        public final M3CG.Proc declare_procedure(int n, long n_params, CGType return_, long lev, Target.CallingConvention cc, boolean exported, M3CG.Proc parent) {
            return declare_procedure(n, n_params, return_, lev, cc, exported, parent, 0, M3ID.NoID);
        }

        /**
         * begin generating code for the procedure 'p'.  Sets "current procedure"
         *   to 'p'.  Implies a begin_block.
         */
        public abstract void begin_procedure(M3CG.Proc p);

        /**
         * marks the end of the code for procedure 'p'.  Sets "current procedure"
         *   to NIL.  Implies an end_block.
         */
        public abstract void end_procedure(M3CG.Proc p);

        public abstract void begin_block();

        /**
         * marks the beginning and ending of nested anonymous blocks
         */
        public abstract void end_block();

        /**
         * note that nested procedure 'p's body occured at the current location
         *   in the source.  In particular, nested in whatever procedures,
         *   anonymous blocks, or exception scopes surround this point.
         */
        public abstract void note_procedure_origin(M3CG.Proc p);

        /*---------------------------------------- statements ---*/

        /**
         * define 'l' to be at the current pc, if 'barrier', 'l' bounds an exception
         *   scope and backend must not move any code past it.
         */
        public abstract void set_label(long l, boolean barrier);

        /** set_label with the default of the last 1 parameter. */
        public final void set_label(long l) {
            set_label(l, false);
        }

        /**
         * GOTO l
         */
        public abstract void jump(long l);

        /**
         * tmp := s0.t; pop; IF (tmp # 0) GOTO l
         */
        public abstract void if_true(CGType t, long l, int f);

        /**
         * tmp := s0.t; pop; IF (tmp = 0) GOTO l
         */
        public abstract void if_false(CGType t, long l, int f);

        /**
         * == compare(t, Int32, op); if_true(Int32, l,f)
         */
        public abstract void if_compare(CGType t, M3CG.CompareOp op, long l, int f);

        /**
         * tmp := s0.t; pop; GOTO labels[tmp]  (NOTE: no range checking on s0.t)
         */
        public abstract void case_jump(CGType t, long[] labels);

        /**
         * Returns s0.t if t is not Void, otherwise returns no value.
         */
        public abstract void exit_proc(CGType t);

        /*---------------------------------------- load/store ---*/

        /**
         * push; s0.u := Mem [ ADR(v) + o ].t ;  The only allowed (t-&gt;u) conversions
         *   are {Int,Word}{8,16} -&gt; {Int,Word}{32,64} and {Int,Word}32 -&gt; {Int,Word}64.
         *   The source type, t, determines whether the value is sign-extended or
         *   zero-extended.
         */
        public abstract void load(M3CG.Var v, long o, CGType t, CGType u);

        /**
         * push; s0.A := ADR(v) + o
         */
        public abstract void load_address(M3CG.Var v, long o);

        /** load_address with the default of the last 1 parameter. */
        public final void load_address(M3CG.Var v) {
            load_address(v, 0);
        }

        /**
         * s0.u := Mem [s0.A + o].t
         * Same invariants on type conversions as 'load'.
         */
        public abstract void load_indirect(long o, CGType t, CGType u);

        /**
         * Mem [ ADR(v) + o ].u := s0.t; pop
         */
        public abstract void store(M3CG.Var v, long o, CGType t, CGType u);

        /**
         * Mem [s1.A + o].u := s0.t; pop (2)
         */
        public abstract void store_indirect(long o, CGType t, CGType u);

        /*---------------------------------------- expressions ---*/

        /*---------------------------------------- literals ---*/

        /**
         * push; s0.A := NIL
         */
        public abstract void load_nil();

        /**
         * push; s0.t := i
         */
        public abstract void load_integer(CGType t, int[] i);

        /**
         * push; s0.t := f
         */
        public abstract void load_float(CGType t, Target.Float f);

        /*---------------------------------------- arithmetic ---*/

        /**
         * s1.u := (s1.t op s0.t); pop
         */
        public abstract void compare(CGType t, CGType u, M3CG.CompareOp op);

        /**
         * s1.t := s1.t + s0.t; pop
         */
        public abstract void add(CGType t);

        /**
         * s1.t := s1.t - s0.t; pop
         */
        public abstract void subtract(CGType t);

        /**
         * s1.t := s1.t * s0.t; pop
         */
        public abstract void multiply(CGType t);

        /**
         * s1.t := s1.t / s0.t; pop
         */
        public abstract void divide(CGType t);

        /**
         * s0.t := - s0.t
         */
        public abstract void negate(CGType t);

        /**
         * s0.t := ABS (s0.t) (noop on Words)
         */
        public abstract void abs(CGType t);

        /**
         * s1.t := MAX (s1.t, s0.t); pop
         */
        public abstract void max(CGType t);

        /**
         * s1.t := MIN (s1.t, s0.t); pop
         */
        public abstract void min(CGType t);

        /**
         * s0.u := op (s0.t)
         */
        public abstract void cvt_int(CGType t, CGType u, M3CG.ConvertOp op);

        /**
         * s0.u := FLOAT (s0.t, u)
         */
        public abstract void cvt_float(CGType t, CGType u);

        /**
         * s1.t := s1.t DIV s0.t;pop
         */
        public abstract void div(CGType t, M3CG.Sign a, M3CG.Sign b);

        /**
         * s1.t := s1.t MOD s0.t;pop
         * In div and mod, "a" is the sign of s1 and "b" is the sign of s0.
         */
        public abstract void mod(CGType t, M3CG.Sign a, M3CG.Sign b);

        /*---------------------------------------- sets ---*/

        /**
         * s2.B := s1.B + s0.B; pop(3)
         */
        public abstract void set_union(long s);

        /**
         * s2.B := s1.B - s0.B; pop(3)
         */
        public abstract void set_difference(long s);

        /**
         * s2.B := s1.B * s0.B; pop(3)
         */
        public abstract void set_intersection(long s);

        /**
         * s2.B := s1.B / s0.B; pop(3)
         */
        public abstract void set_sym_difference(long s);

        /**
         * s1.t := (s0.t IN s1.B); pop
         */
        public abstract void set_member(long s, CGType t);

        /**
         * s1.t := (s1.B op s0.B); pop
         */
        public abstract void set_compare(long s, M3CG.CompareOp op, CGType t);

        /**
         * s2.A[s1.t..s0.t] := 1...1; pop(3)
         */
        public abstract void set_range(long s, CGType t);

        /**
         * s1.A [s0.t] := 1; pop(2)
         */
        public abstract void set_singleton(long s, CGType t);

        /*---------------------------------------- Word.T bit operations ---*/

        /**
         * s0.t := Word.Not (s0.t)
         */
        public abstract void not(CGType t);

        /**
         * s1.t := Word.And (s1.t, s0.t); pop
         */
        public abstract void and(CGType t);

        /**
         * s1.t := Word.Or  (s1.t, s0.t); pop
         */
        public abstract void or(CGType t);

        /**
         * s1.t := Word.Xor (s1.t, s0.t); pop
         */
        public abstract void xor(CGType t);

        /**
         * s1.t := Word.Shift  (s1.t, s0.t); pop
         */
        public abstract void shift(CGType t);

        /**
         * s1.t := Word.Shift  (s1.t, s0.t); pop
         */
        public abstract void shift_left(CGType t);

        /**
         * s1.t := Word.Shift  (s1.t, -s0.t); pop
         */
        public abstract void shift_right(CGType t);

        /**
         * s1.t := Word.Rotate (s1.t, s0.t); pop
         */
        public abstract void rotate(CGType t);

        /**
         * s1.t := Word.Rotate (s1.t, s0.t); pop
         */
        public abstract void rotate_left(CGType t);

        /**
         * s1.t := Word.Rotate (s1.t, -s0.t); pop
         */
        public abstract void rotate_right(CGType t);

        /**
         * s0.I64 := s0.I32;  IF sign THEN SignExtend s0 ELSE ZeroExtend s0;
         */
        public abstract void widen(boolean sign);

        /**
         * s0.I32 := Word.And (s0.I64, 16_ffffffff);
         */
        public abstract void chop();

        /**
         * s2.t := Word.Extract(s2.t, s1.t, s0.t);
         *   IF sign THEN SignExtend s2; pop(2)
         */
        public abstract void extract(CGType t, boolean sign);

        /**
         * s1.t := Word.Extract(s1.t, s0.t, n);
         *   IF sign THEN SignExtend s1; pop(1)
         */
        public abstract void extract_n(CGType t, boolean sign, long n);

        /**
         * s0.t := Word.Extract(s0.t, m, n);
         *   IF sign THEN SignExtend s0
         */
        public abstract void extract_mn(CGType t, boolean sign, long m, long n);

        /**
         * s3.t := Word.Insert (s3.t, s2.t, s1.t, s0.t); pop(3)
         */
        public abstract void insert(CGType t);

        /**
         * s2.t := Word.Insert (s2.t, s1.t, s0.t, n); pop(2)
         */
        public abstract void insert_n(CGType t, long n);

        /**
         * s1.t := Word.Insert (s1.t, s0.t, m, n); pop(1)
         */
        public abstract void insert_mn(CGType t, long m, long n);

        /*---------------------------------------- misc. stack/memory ops ---*/

        /**
         * tmp := s1.a; s1.b := s0.b; s0.a := tmp
         */
        public abstract void swap(CGType a, CGType b);

        /**
         * pop(1) discard s0, not its side effects
         */
        public abstract void pop(CGType t);

        /**
         * copy s0.u units with 't's size and alignment from s1.A to s2.A; pop(3).
         *   'overlap' is true if the source and destination may partially overlap
         *   (ie. you need memmove, not just memcpy).
         */
        public abstract void copy_n(CGType u, CGType t, boolean overlap);

        /**
         * copy 'n' units with 't's size and alignment from s0.A to s1.A; pop(2).
         *   'overlap' is true if the source and destination may partially overlap
         *   (ie. you need memmove, not just memcpy).
         */
        public abstract void copy(long n, CGType t, boolean overlap);

        /**
         * zero s0.u units with 't's size and alignment starting at s1.A; pop(2)
         */
        public abstract void zero_n(CGType u, CGType t);

        /**
         * zero 'n' units with 't's size and alignment starting at s0.A; pop(1)
         */
        public abstract void zero(long n, CGType t);

        /*---------------------------------------- conversions ---*/

        /**
         * This used to say: "s0.two := LOOPHOLE(s0.from, two)"
         * But CG requires and various backends provide more cases:
         *
         *   s0.two := LH(s0.from, two),
         *
         *   where LH includes truncation, zero extension, or sign extension,
         *   as needed for size matching, in addition to LOOPHOLE.
         *   The exact semantics can only be inferred by extensive vetting of CG and
         *   all the back ends, which will probably be ambiguous, calling for further
         *   design decisions.
         */
        public abstract void loophole(CGType from, CGType two);

        /*---------------------------------------- traps & runtime checks ---*/

        /**
         * generate a checked runtime error for "code"
         */
        public abstract void abort(M3CG.RuntimeError code);

        /**
         * IF (s0.A = NIL) THEN abort(code)
         */
        public abstract void check_nil(M3CG.RuntimeError code);

        /**
         * IF (s0.t &lt; i) THEN abort(code)
         */
        public abstract void check_lo(CGType t, int[] i, M3CG.RuntimeError code);

        /**
         * IF (i &lt; s0.t) THEN abort(code)
         */
        public abstract void check_hi(CGType t, int[] i, M3CG.RuntimeError code);

        /**
         * IF (s0.t &lt; a) OR (b &lt; s0.t) THEN abort(code)
         */
        public abstract void check_range(CGType t, int[] a, int[] b, M3CG.RuntimeError code);

        /**
         * IF NOT (0 &lt;= s1.t &lt; s0.t) THEN abort(code) END; pop
         *   s0.t is guaranteed to be positive so the unsigned
         *   check (s0.W &lt; s1.W) is sufficient.
         */
        public abstract void check_index(CGType t, M3CG.RuntimeError code);

        /**
         * IF (s0.t # s1.t) THEN abort(code);  Pop (2)
         */
        public abstract void check_eq(CGType t, M3CG.RuntimeError code);

        /*---------------------------------------- address arithmetic ---*/

        /**
         * s0.A := s0.A + i bytes
         */
        public abstract void add_offset(long i);

        /**
         * s1.A := s1.A + s0.t * size; pop  -- where 'size' is in bytes
         */
        public abstract void index_address(CGType t, long size);

        /*---------------------------------------- procedure calls ---*/

        /**
         * begin a procedure call to procedure 'p' at static level 'lev' that
         *   will return a value of type 't'.
         * No nested call sequence appears between start_call_direct and call_direct.
         */
        public abstract void start_call_direct(M3CG.Proc p, long lev, CGType t);

        /**
         * call the procedure 'p'.  It returns a value of type t.
         */
        public abstract void call_direct(M3CG.Proc p, CGType t);

        /**
         * begin an indirect procedure call that will return a value of type 't'.
         * No nested call sequence appears between start_call_indirect and call_indirect.
         */
        public abstract void start_call_indirect(CGType t, Target.CallingConvention cc);

        /**
         * call the procedure whose address is in s0.A and pop s0.  The
         *   procedure returns a value of type t.
         */
        public abstract void call_indirect(CGType t, Target.CallingConvention cc);

        /**
         * start a try block
         */
        public abstract void start_try();

        /**
         * end a try block
         */
        public abstract void end_try();

        /**
         * call the procedure 'p' inside a try block.  It returns a value of type t.
         *   if the result of the call is normal, then resume at the next label otherwise
         *   the stack unwinder will resume at the handler label.
         */
        public abstract void invoke_direct(M3CG.Proc p, CGType t, long next, long handler);

        /**
         * call the procedure whose address is in s0.A and pop s0.  The
         *   procedure returns a value of type t.
         *   If the result of the call is normal, then resume at the next label otherwise
         *   the stack unwinder will resume at the handler label.
         */
        public abstract void invoke_indirect(CGType t, Target.CallingConvention cc, long next, long handler);

        /**
         * generate a landing pad for the handler label which should immediately precede the
         *   landing pad. This corresponds to the back edge of the invoke instruction in the
         *   event of a stack unwind result of type t, which should be an address of the exception.
         *   push the result
         */
        public abstract void landing_pad(CGType t, long handler, int[] catches);

        /**
         * pop s0.t and make it the "next" parameter in the current call.
         */
        public abstract void pop_param(CGType t);

        /**
         * pop s0.A, it's a pointer to a structure occupying 's' bytes that's
         *  'a' byte aligned; pass the structure by value as the "next" parameter
         *  in the current call.
         * NOTE: This is the only executable operator that denotes that a struct
         *         (could be any in-memory aggregate) is passed by value.  It occurs
         *         at the end of actual parameter evaluation, at a call site.  No
         *         operator(s) appear near begin_procedure that would indicate that a
         *         struct formal is passed by value.  This system strongly suggests
         *         that the copy is to be made at the call site.  A backend would
         *         have to use the formal's VALUE mode to trigger internal generating
         *         of copy code, if prolog site copy is desired.
         */
        public abstract void pop_struct(int t, long s, long a);

        /**
         * pop s0.A for the current indirect procedure call's static link
         */
        public abstract void pop_static_link();

        /*---------------------------------------- procedure and closure types ---*/

        /**
         * push; s0.A := ADDR (p's body)
         */
        public abstract void load_procedure(M3CG.Proc p);

        /**
         * push; s0.A := (static link needed to call p, NIL for top-level procs)
         */
        public abstract void load_static_link(M3CG.Proc p);

        /*---------------------------------------- misc. ---*/

        /**
         * annotate the output with a&b&c&d as a comment.  Note that any of a,b,c or d
         *   may be NIL.
         */
        public abstract void comment(String a, String b, String c, String d);

        /** comment with the default of the last 1 parameter. */
        public final void comment(String a, String b, String c) {
            comment(a, b, c, null);
        }

        /** comment with the defaults of the last 2 parameters. */
        public final void comment(String a, String b) {
            comment(a, b, null, null);
        }

        /** comment with the defaults of the last 3 parameters. */
        public final void comment(String a) {
            comment(a, null, null, null);
        }

        /** comment with the defaults of the last 4 parameters. */
        public final void comment() {
            comment(null, null, null, null);
        }

        /*---------------------------------------- atomics ---*/

        /**
         * Mem [s1.A].u := s0.t; pop (2)
         */
        public abstract void store_ordered(CGType t, CGType u, M3CG.MemoryOrder order);

        /**
         * s0.u := Mem [s0.A].t
         */
        public abstract void load_ordered(CGType t, CGType u, M3CG.MemoryOrder order);

        /**
         * tmp := Mem [s1.A].t;  Mem [s1.A].t := s0.u;  s0.u := tmp;  pop
         */
        public abstract void exchange(CGType t, CGType u, M3CG.MemoryOrder order);

        /**
         * tmp := Mem[s2.A].t;
         *   IF (tmp = s1.u)
         *   THEN Mem[s2.A].t := s0.u; s2.r := 1; pop(1);
         *   ELSE s1.u := tmp;         s2.r := 0; pop(1);
         *   END;
         *   This is permitted to fail spuriously, leaving s1 unchanged.
         */
        public abstract void compare_exchange(CGType t, CGType u, CGType r, M3CG.MemoryOrder success, M3CG.MemoryOrder failure);

        /**
         * Memory is affected as per o
         */
        public abstract void fence(M3CG.MemoryOrder o);

        /**
         * tmp := Mem [s1.A].t;
         *   Mem [s1.A].t := tmp op s0.u;
         *   s1.u := tmp; pop
         */
        public abstract void fetch_and_op(M3CG.AtomicOp op, CGType t, CGType u, M3CG.MemoryOrder order);
    }
}
