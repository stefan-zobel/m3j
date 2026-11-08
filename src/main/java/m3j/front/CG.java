// Java port of CM3's m3-sys/m3front/src/misc/CG.i3 and CG.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.HashMap;
import java.util.Map;

import m3j.middle.M3CG;
import m3j.middle.M3CG.CompareOp;
import m3j.middle.M3CG.RuntimeError;
import m3j.middle.M3CG.Sign;
import m3j.middle.M3CG_Check;
import m3j.middle.M3ID;
import m3j.middle.M3RT;
import m3j.middle.TFloat;
import m3j.middle.TInt;
import m3j.middle.TWord;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;
import m3j.util.RangeError;

// File: CG.i3
// Last modified on Tue Jun 20 15:58:36 PDT 1995 by kalsow
//      modified on Tue May 25 14:29:03 PDT 1993 by muller

// File: CG.m3
// Last modified on Tue Jun 20 15:58:21 PDT 1995 by kalsow
//      modified on Tue May 25 11:19:53 PDT 1993 by muller

/**
 * This interface provides a single front-end specific, sometimes
 * quite thick, veneer over M3CG, M3CG_Ops and M3RT.
 *
 * <p>Port: the types of the interface (see M3CG for their interpretation)
 * are: Type, MType, IType, RType, AType and ZType are {@link CGType};
 * Sign, Var, Proc, Cmp (CompareOp), Cvt (ConvertOp), AtomicOp, MemoryOrder
 * and RuntimeError are those of {@link M3CG}; CallingConvention is
 * {@link Target.CallingConvention}; Name and TypeUID are {@code int};
 * Offset, Size, Alignment and Label are {@code long}; Frequency is
 * {@code int}. NOTE: Notwithstanding the comment at M3CG.Alignment,
 * alignments in this interface are in *bits*.
 */
public final class CG {
    private CG() {}

    private static boolean debug = false;

    /** max size of a single init_chars string */
    private static final long Max_init_chars = 256;

    // see M3CG for the interpretation of these values

    public static final long No_label = M3CG.No_label;

    public static final int Never = M3CG.Never;

    public static final int Maybe = M3CG.Maybe;

    public static final int Likely = M3CG.Likely;

    public static final int Always = M3CG.Always;

    public static final long noAlign = 1;

    /** maximum possible machine alignment */
    public static long Max_alignment;

    public enum VKind {   // TYPE   VALUE
        Integer,           // Int    int
        Float,             // Float  float
        Stacked,           // any    S0.type
        Direct,            // any    Value(ADR(base) + offset);
                           //        bits=NIL; ADR(base) + offset satisfies old_align
        Absolute,          // Addr   ADR(base) + OFFS
        Indirect,          // Addr   Value(base) + OFFS
        Pointer            // Addr   S0.A + OFFS
    } // where OFFS == offset + Value(bits)

    /**
     * Port: the fields that are not initialized in the original start as
     * zero values, as the storage of the original does.
     */
    public static class ValRec {
        /** type of descriptor */
        public VKind kind = VKind.Integer;
        /** type of the value */
        public CGType type = CGType.Word8;
        /** TRUE => base is a temp. */
        public boolean temp_base;
        /** TRUE => bits is a temp. */
        public boolean temp_bits;
        /** known alignment of ??? */
        public long old_align;
        /** alignment of ADR(base). */
        public long base_align;
        /**
         * ^Alignment of MEM(base) (Direct,Absolute, or Indirect),
         *  or of MEM(S0.A) (Stacked or Pointer.
         */
        public long base_value_align;
        /** when type=Addr, alignment of VALUE. */
        public long addr_align;
        // NOTE: Originally, field 'align' was highly inconsistent on whether it
        //       was the alignment where a value is (or could be) stored, or, when
        //       type=Addr, the alignment of where the value pointed.  Most calls
        //       outside of CG passed in the former, while most uses inside CG
        //       expected the latter.  This is quite complicated to unravel, so
        //       to reduce breakage risk and facilitate fixes thereof, three values
        //       are maintained.  'old_align' is as 'align' was.  'base_align' is
        //       the former, and 'addr_align' is the latter.  It should be possible
        //       to eliminate 'old_align' altogether, after ample testing and
        //       removal of all uses thereof.
        /** base address */
        public M3CG.Var base;
        /** non-constant bit offset */
        public M3CG.Var bits;
        /** constant bit offset */
        public long offset;
        /** link for lists */
        public Val next;
        /** literal integer value */
        public final int[] int_ = TInt.newInt();
        /** literal floating point value */
        public final Target.Float float_ = new Target.Float();

        /** Port: the record assignment {@code this := x}. */
        void assign(ValRec x) {
            kind = x.kind;
            type = x.type;
            temp_base = x.temp_base;
            temp_bits = x.temp_bits;
            old_align = x.old_align;
            base_align = x.base_align;
            base_value_align = x.base_value_align;
            addr_align = x.addr_align;
            base = x.base;
            bits = x.bits;
            offset = x.offset;
            next = x.next;
            TInt.assign(int_, x.int_);
            float_.assign(x.float_);
        }
    }

    private static String vKindImage(VKind vkind) {
        switch (vkind) {
            case Integer: return "Integer";
            case Float: return "Float";
            case Stacked: return "Stacked";
            case Direct: return "Direct";
            case Absolute: return "Absolute";
            case Indirect: return "Indirect";
            case Pointer: return "Pointer";
            default: throw new AssertError("CG");
        }
    }

    public static final class TempWrapper {
        public TempWrapper next;
        public M3CG.Var temp;
        public long size;
        public long align;
        public CGType type;
        public boolean in_mem;
        public long block;
    }

    public static class Node {
        public Node next;
        // file : String.T;
        // line : INTEGER;
        public long o;

        void dump() {
            throw new AssertError("CG");
        }
    }

    /** Stacking some per-procedure values for nested procedures: */
    public static final class ProcStackNodeRef {
        public ProcStackNodeRef link = null;
        public TempWrapper free_temps = null;
        public TempWrapper busy_temps = null;
        public long block_cnt = 0;
    }

    private static ProcStackNodeRef ProcStackRoot = null;

    public static final class FloatNode extends Node {
        public Target.Float f;

        @Override
        void dump() {
            dumpFloat(this);
        }
    }

    public static final class CharsNode extends Node {
        public String t;

        @Override
        void dump() {
            dumpChars(this);
        }
    }

    public static final class ProcNode extends Node {
        public M3CG.Proc v;

        @Override
        void dump() {
            dumpProc(this);
        }
    }

    public static final class LabelNode extends Node {
        public long v;

        @Override
        void dump() {
            dumpLabel(this);
        }
    }

    public static final class VarNode extends Node {
        public M3CG.Var v;
        public long b;

        @Override
        void dump() {
            dumpVar(this);
        }
    }

    public static final class OffsetNode extends Node {
        public M3CG.Var v;

        @Override
        void dump() {
            dumpOffset(this);
        }
    }

    public static final class CommentNode extends Node {
        public String a, b, c, d;

        @Override
        void dump() {
            dumpComment(this);
        }
    }

    public static final class IntNode extends Node {
        public long s;
        public int[] v;

        @Override
        void dump() {
            dumpInt(this);
        }
    }

    public static final class FieldNode extends Node {
        public int n;
        public long s;
        public int t;

        @Override
        void dump() {
            dumpField(this);
        }
    }

    private static M3CG.T cg_wr = null;
    private static M3CG.T cg_check = null;
    private static M3CG.T cg = null;
    private static long last_offset = -2;
    private static String last_file = null;
    private static long last_line = -2;
    /** Port: ARRAY BOOLEAN OF Node, indexed by {@link #ix}. */
    private static final Node[] pending = new Node[2];
    /** Port: ARRAY BOOLEAN OF Node, indexed by {@link #ix}. */
    private static final Node[] fields = new Node[2];
    private static boolean in_init = false;
    private static long init_pc = 0;
    private static final int[] init_bits = TInt.copy(TInt.Zero);
    // In free_temps and free_values, "free" means they are allocated at runtime,
    // but not currently in use.
    private static TempWrapper free_temps = null;
    private static TempWrapper busy_temps = null;
    private static Val free_values = null;
    private static Val busy_values = null;
    private static Map<Integer, Integer> indirects = null;
    private static Map<Integer, M3CG.Var> variables = null;
    private static Map<Integer, M3CG.Proc> procedures = null;
    private static long block_cnt = 0;
    /** ^top-of-stack (lowest *unused* stack element. */
    private static long tos = 0;
    private static final ValRec[] stack = new ValRec[100];

    static {
        for (int i = 0; i < stack.length; i++) {
            stack[i] = new ValRec();
        }
    }

    /** (*CONST*) */
    private static final CGType[] StackType = new CGType[CGType.values().length];

    /** Port: the index of ARRAY BOOLEAN OF ... */
    private static int ix(boolean b) {
        return b ? 1 : 0;
    }

    /** Port: an assignment to a CARDINAL. */
    private static long cardinal(long x) {
        if (x < 0) {
            throw new RangeError("CG");
        }
        return x;
    }

    /*---------------------------------------------------------------------------*/

    /** creates a fresh, initialized code generator */
    public static void init() {
        Max_alignment = Target.Alignments[Target.Alignments.length - 1];

        for (CGType t : CGType.values()) {
            StackType[t.ordinal()] = t;
        }
        for (int t = CGType.Word8.ordinal(); t <= CGType.Int64.ordinal(); t++) {
            if (M3.targetMap.CG_Size[t] <= M3.target.Integer.size) {
                StackType[t] = M3.target.Integer.cg_type;
            } else {
                StackType[t] = M3.target.Longint.cg_type;
            }
        }

        cg_wr = Host.env.init_code_generator();
        if (cg_wr == null) {
            Error.msg("unable to create a code generator");
            return;
        }
        // RTObject.PatchMethods (cg_wr);

        // Port: M3CG_Check.New also takes the state of M3ID, Target and
        // TargetMap.
        cg_check = M3CG_Check.New(cg_wr,
                                  /* clean_jumps  := */ Host.clean_jumps,
                                  /* clean_stores := */ Host.clean_stores,
                                  /* nested_calls := */ Host.nested_calls,
                                  /* nested_procs := */ Host.inline_nested_procs,
                                  M3.ids, M3.target, M3.targetMap);
        // RTObject.PatchMethods (cg_check);
        cg = cg_check;

        cg.set_error_handler(Error::msg);

        last_offset    = -2;
        last_file      = null;
        last_line      = -2;
        pending[0]     = null;
        pending[1]     = null;
        fields[0]      = null;
        fields[1]      = null;
        in_init        = false;
        init_pc        = 0;
        TInt.assign(init_bits, TInt.Zero);
        free_temps     = null;
        busy_temps     = null;
        free_values    = null;
        busy_values    = null;
        indirects      = null;
        variables      = null;
        procedures     = null;
        block_cnt      = 0;
        tos            = 0;
    }

    /*----------------------------------------------------------- ID counters ---*/

    /** allocates and returns the next 'n_labels' labels */
    public static long next_label(long n_labels) {
        return cg.next_label(n_labels);
    }

    public static long next_label() {
        return next_label(1);
    }

    /*----------------------------------------------------- compilation units ---*/

    /** called before any other procedures to initialize the compilation unit. */
    public static void begin_unit(long optimize) {
        ProcStackRoot = null;
        cg.begin_unit(optimize);
    }

    public static void begin_unit() {
        begin_unit(0);
    }

    /**
     * called after all other procedures to finalize the unit and write the
     * resulting object.
     */
    public static void end_unit() {
        free_all_values();
        free_all_temps();
        AssertError.check(ProcStackRoot == null, "CG");
        cg.end_unit();
    }

    public static boolean isInsideProc() {
        return ProcStackRoot != null;
    }

    /** note that the current compilation unit imports the interface 'n' */
    public static void import_unit(int n) {
        cg.import_unit(n);
        WebInfo.import_unit(n);
    }

    /** note that the current compilation unit exports the interface 'n' */
    public static void export_unit(int n) {
        cg.export_unit(n);
        WebInfo.export_unit(n);
    }

    /*------------------------------------------------ debugging line numbers ---*/

    /** generate the source file and line number info for 'here' */
    public static void gen_location(long here) {
        String[] file = new String[1];
        long save;
        long[] line = new long[1];
        if (here == last_offset) {
            return;
        }

        save = Scanner.offset;
        Scanner.offset = here;
        Scanner.localHere(file, line);

        if ((last_file == null) || !last_file.equals(file[0])) {
            cg.set_source_file(file[0]);
            last_file = file[0];
        }

        if (last_line != line[0]) {
            cg.set_source_line(line[0]);
            last_line = line[0];
        }

        Scanner.offset = save;
        last_offset = here;
    }

    /*------------------------------------------- debugging type declarations ---*/

    // The debugging information for a type is identified by a small integer
    // within a compilation unit.  The information is identified by a global
    // TypeUID (an INTEGER hash code on the type's structure) across compilation
    // units. The following procedures generate the symbol table entries needed
    // to describe Modula-3 types to the debugger.

    public static void declare_typename(int t, int n) {
        if (n != M3ID.NoID) {
            cg.declare_typename(t, n);
        }
    }

    public static void declare_array(int t, int index, int elt, long s) {
        cg.declare_array(t, index, elt, s);
        WebInfo.declare_array(t, index, elt, s);
    }

    public static void declare_open_array(int t, int elt, long s) {
        cg.declare_open_array(t, elt, s);
        WebInfo.declare_open_array(t, elt, s);
    }

    public static void declare_enum(int t, long n_elts, long s) {
        cg.declare_enum(t, n_elts, s);
        WebInfo.declare_enum(t, n_elts, s);
    }

    public static void declare_enum_elt(int n) {
        cg.declare_enum_elt(n);
        WebInfo.declare_enum_elt(n);
    }

    public static void declare_packed(int t, long s, int base) {
        cg.declare_packed(t, s, base);
        WebInfo.declare_packed(t, s, base);
    }

    public static void declare_record(int t, long s, long n_fields) {
        cg.declare_record(t, s, n_fields);
        WebInfo.declare_record(t, s, n_fields);
    }

    public static void declare_field(int n, long o, long s, int t) {
        cg.declare_field(n, o, s, t);
        WebInfo.declare_field(n, o, s, t);
    }

    public static void declare_set(int t, int domain, long s) {
        cg.declare_set(t, domain, s);
        WebInfo.declare_set(t, domain, s);
    }

    public static void declare_subrange(int t, int domain, int[] min, int[] max, long s) {
        cg.declare_subrange(t, domain, min, max, s);
        WebInfo.declare_subrange(t, domain, min, max, s);
    }

    public static void declare_pointer(int t, int target, String brand, boolean traced, int target_typename) {
        cg.declare_pointer(t, target, brand, traced, target_typename);
        WebInfo.declare_pointer(t, target, brand, traced);
    }

    public static void declare_pointer(int t, int target, String brand, boolean traced) {
        declare_pointer(t, target, brand, traced, M3ID.NoID);
    }

    /** an automatically dereferenced pointer! (WITH variables, VAR formals, ...) */
    public static int declare_indirect(int target, int target_typename) {
        int x;
        if (indirects == null) {
            indirects = newIntTbl();
        }
        Integer found = indirects.get(target);
        if (found == null) {
            // Port: the complement of a TypeUID is a TypeUID.
            x = ~target;  // !! fingerprint HACK !!
            cg.declare_indirect(x, target, target_typename);
            WebInfo.declare_indirect(x, target);
            indirects.put(target, x);
        } else {
            x = found;
        }
        return x;
    }

    public static int declare_indirect(int target) {
        return declare_indirect(target, M3ID.NoID);
    }

    public static void declare_proctype(int t, long n_formals, int result, long n_raises,
                                        Target.CallingConvention cc, int result_typename) {
        cg.declare_proctype(t, n_formals, result, n_raises, cc, result_typename);
        WebInfo.declare_proctype(t, n_formals, result, n_raises);
    }

    public static void declare_proctype(int t, long n_formals, int result, long n_raises, Target.CallingConvention cc) {
        declare_proctype(t, n_formals, result, n_raises, cc, M3ID.NoID);
    }

    public static void declare_formal(int n, int t, int typename) {
        cg.declare_formal(n, t, typename);
        WebInfo.declare_formal(n, t);
    }

    public static void declare_formal(int n, int t) {
        declare_formal(n, t, M3ID.NoID);
    }

    public static void declare_raises(int n) {
        cg.declare_raises(n);
        WebInfo.declare_raises(n);
    }

    public static void declare_object(int t, int super_, String brand, boolean traced,
                                      long n_fields, long n_methods, long n_overrides,
                                      long field_size,
                                      long field_offset, long method_offset) {
        cg.declare_object(t, super_, brand, traced,
                          n_fields, n_methods, field_size, field_offset, method_offset);
        WebInfo.declare_object(t, super_, brand, traced,
                               n_fields, n_methods, n_overrides, field_size);
    }

    public static void declare_method(int n, int signature, Expr.T dfault) {
        cg.declare_method(n, signature);
        WebInfo.declare_method(n, signature, dfault);
    }

    public static void declare_override(int n, Expr.T dfault) {
        WebInfo.declare_override(n, dfault);
    }

    public static void declare_opaque(int t, int super_) {
        cg.declare_opaque(t, super_);
        WebInfo.declare_opaque(t, super_);
    }

    public static void reveal_opaque(int lhs, int rhs) {
        cg.reveal_opaque(lhs, rhs);
        WebInfo.reveal_opaque(lhs, rhs);
    }

    public static void declare_global_field(int n, long o, long s, int t, boolean is_const) {
        FieldNode x = new FieldNode();
        x.next = fields[ix(is_const)];
        x.n = n;
        x.o = o;
        x.s = s;
        x.t = t;
        fields[ix(is_const)] = x;
    }

    private static void dumpField(FieldNode x) {
        // DumpNode (x);  -- no file & line number info
        cg.declare_field(x.n, x.o, x.s, x.t);
    }

    public static void emit_global_record(long s, boolean is_const) {
        Node n = fields[ix(is_const)];
        int cnt = 0;
        Node[] xx;
        // build a sorted array of fields
        while (n != null) {
            cnt++;
            n = n.next;
        }
        xx = new Node[cnt];
        n = fields[ix(is_const)];
        cnt = 0;
        while (n != null) {
            xx[cnt] = n;
            cnt++;
            n = n.next;
        }
        sortNodes(xx);

        // finally, declare the record
        cg.declare_record(-1, s, xx.length);
        for (int i = 0; i <= xx.length - 1; i++) {
            xx[i].dump();
        }
        fields[ix(is_const)] = null;
    }

    /**
     * declares an exception named 'n' identified with the address 'base+offset'
     * that carries an argument of type 'arg_type'.  If 'raise_proc', then
     * 'base+offset+BYTESIZE(ADDRESS)' is a pointer to the procedure that
     * packages the argument and calls the runtime to raise the exception.
     */
    public static void declare_exception(int n, int arg_type, boolean raise_proc, M3CG.Var base, long offset) {
        cg.declare_exception(n, arg_type, raise_proc, base, toBytes(offset));
    }

    /** Informs that the WIDECHAR has BITSIZE size. */
    public static void widechar_size(long size) {
        cg.widechar_size(size);
    }

    /*--------------------------------------------------------- RunTyme hooks ---*/

    /** declares 'n' as a runtime procedure 'p'. */
    public static void set_runtime_proc(int n, M3CG.Proc p) {
        cg.set_runtime_proc(n, p);
    }

    /*------------------------------------------------- variable declarations ---*/

    // Clients must declare a variable before generating any statements or
    // expressions that refer to it;  declarations of global variables and
    // temps can be intermixed with generation of statements and expressions.
    //
    // In the declarations that follow:
    //
    //    n: Name            is the name of the variable.  If it's NIL, the
    //                         the back-end is free to choose its own unique name.
    //    s: Size            is the size in bits of the declared variable
    //    a: Alignment       is the minimum required alignment of the variable
    //    t: Type            is the machine reprentation type of the variable
    //    m3t: TypeUID       is the UID of the Modula-3 type of the variable
    //    in_memory: BOOLEAN specifies whether the variable must have an address
    //    exported: BOOLEAN  specifies whether the variable must be visible in
    //                         other compilation units
    //    init: BOOLEAN      indicates whether an explicit static initialization
    //                         immediately follows this declaration.
    //    up_level: BOOLEAN  specifies whether the variable is accessed from
    //                         nested procedures.
    //    f: Frequency       is the front-end estimate of how frequently the
    //                         variable is accessed.

    /** imports the specified global variable. */
    public static M3CG.Var import_global(int n, long s, long a, CGType t, int m3t, int typename) {
        M3CG.Var v;
        if (debug) {
            System.err.print("Import_global n:");
            System.err.print(M3ID.toText(M3.ids, n));
            System.err.print(" typename:");
            System.err.print(Long.toString(typename));
            System.err.print("\n");
            System.err.flush();
        }
        if (variables == null) {
            variables = newNameTbl();
        }
        if (variables.containsKey(n)) {
            return variables.get(n);
        }
        v = cg.import_global(n, toVarSize(s, a), byteAlign(a), t, m3t, typename);
        variables.put(n, v);
        return v;
    }

    public static M3CG.Var import_global(int n, long s, long a, CGType t, int m3t) {
        return import_global(n, s, a, t, m3t, M3ID.NoID);
    }

    /**
     * Together Declare_segment and Bind_segment accomplish what
     * Declare_global("is_const = FALSE") or Declare_constant("is_const=TRUE")
     * does, but Declare_segment returns to the front-end, a
     * handle on the variable before its size, type, or initial values
     * are known.  Every declared segment must be bound exactly once.
     */
    public static M3CG.Var declare_segment(int n, int m3t, boolean is_const) {
        return cg.declare_segment(n, m3t, is_const);
    }

    public static void bind_segment(M3CG.Var seg, long s, long a, CGType t,
                                    boolean exported, boolean init, boolean is_const) {
        cg.bind_segment(seg, toVarSize(s, a), byteAlign(a), t, exported, init);
        if (init) {
            begin_init(seg);
            dumpPendingNodes(is_const);
            end_init(seg);
        }
    }

    /** declares a global variable. */
    public static M3CG.Var declare_global(int n, long s, long a, CGType t,
                                          int m3t, boolean exported, boolean init) {
        return cg.declare_global(n, toVarSize(s, a), byteAlign(a),
                                 t, m3t, exported, init);
    }

    /** declares a read-only global variable */
    public static M3CG.Var declare_constant(int n, long s, long a, CGType t,
                                            int m3t, boolean exported, boolean init) {
        return cg.declare_constant(n, toVarSize(s, a), byteAlign(a),
                                   t, m3t, exported, init);
    }

    /**
     * declares a local variable.  Local variables must be declared in the
     * procedure that contains them.  The lifetime of a local variable extends
     * from the beginning to end of the closest enclosing begin_block/end_block.
     */
    public static M3CG.Var declare_local(int n, long s, long a, CGType t,
                                         int m3t, boolean in_memory, boolean up_level,
                                         int f) {
        return cg.declare_local(n, toVarSize(s, a), byteAlign(a),
                                t, m3t, in_memory, up_level, f);
    }

    /**
     * declares a formal parameter.  Formals are declared in their lexical
     * order immediately following the 'declare_procedure' or
     * 'import_procedure' that contains them.
     */
    public static M3CG.Var declare_param(int n, long s, long a, CGType t,
                                         int m3t, boolean in_memory, boolean up_level,
                                         int f, int typename) {
        return cg.declare_param(n, toVarSize(s, a), byteAlign(a),
                                t, m3t, in_memory, up_level, f, typename);
    }

    public static M3CG.Var declare_param(int n, long s, long a, CGType t, int m3t,
                                         boolean in_memory, boolean up_level, int f) {
        return declare_param(n, s, a, t, m3t, in_memory, up_level, f, M3ID.NoID);
    }

    /*----------------------------------------------------------- temporaries ---*/

    /**
     * declares an anonymous local variable.  Temps are declared
     * and freed between their containing procedure's begin_procedure and
     * end_procedure calls.  Temps are never referenced by nested procedures.
     */
    public static M3CG.Var declare_temp(long s, long a, CGType t, boolean in_memory) {
        // If possible, get a temp off the free_temps list.  Otherwise, emit code to
        // allocate a temp and use it.  Either way, put the temp on the busy_temps
        // list.
        TempWrapper w = free_temps;
        TempWrapper last_w = null;
        M3CG.Var tmp;
        while (true) {
            if (w == null) {
                // we need to allocate a fresh one
                tmp = cg.declare_temp(toVarSize(s, a), byteAlign(a), t, in_memory);
                TempWrapper nw = new TempWrapper();
                nw.size = s;
                nw.align = a;
                nw.type = t;
                nw.in_mem = in_memory;
                nw.temp = tmp;
                nw.block = block_cnt;
                nw.next = busy_temps;
                busy_temps = nw;
                return tmp;
            } else if ((w.size == s) && (w.align == a) && (w.type == t)
                    && (w.in_mem == in_memory)) {
                // we found a match
                if (last_w == null) {
                    free_temps = w.next;
                } else {
                    last_w.next = w.next;
                }
                w.next = busy_temps;
                busy_temps = w;
                return w.temp;
            } else {
                // try the next one
                last_w = w;
                w = w.next;
            }
        }
    }

    private static M3CG.Var declare_addr_temp(boolean in_memory) {
        return declare_temp(M3.target.Address.size, M3.target.Address.align,
                            CGType.Addr, in_memory);
    }

    /**
     * releases the space occupied by temp variable 'v' so that it may be reused
     * by other new temporaries.
     * NOTE This is disabled. The only way is to call Free_temps and hope you
     * are done with them all.
     */
    public static void free_temp(M3CG.Var v) {
    }

    /** free "temps" (CG.Vars) that are marked busy. */
    public static void free_temps() {
        // Move all temps from the busy_temps wrapper list to the free_temps wrapper
        // list.
        // No deallocation of any CG.Var or TempWrapper.
        // No backend free_temp code emitted.
        TempWrapper w = busy_temps;
        sEmpty("Free_temps");
        if (w != null) {
            while (w.next != null) {
                w = w.next;
            }
            w.next = free_temps;
            free_temps = busy_temps;
            busy_temps = null;
        }
    }

    // Port, omitted: the procedure Free_one_temp (commented out in the
    // original).

    private static void free_all_temps() {
        // Emit code to deallocate all busy and free temps.
        // Allow TempWrappers and CG temps (Vars) to be GC'd.
        TempWrapper w;
        free_temps();
        AssertError.check(busy_temps == null, "CG");
        w = free_temps;
        while (w != null) {
            cg.free_temp(w.temp);
            w = w.next;
        }
        free_temps = null;
    }

    private static void free_block_temps(long block) {
        // Emit code to deallocate busy and free temps belonging to block.
        // Allow TempWrappers and CG temps (Vars) to be GC'd.
        TempWrapper w, prev_w;
        free_temps();
        AssertError.check(busy_temps == null, "CG");
        w = free_temps;
        prev_w = null;
        while (w != null) {
            if (w.block == block) {
                cg.free_temp(w.temp);
                if (prev_w != null) {
                    prev_w.next = w.next;
                } else {
                    free_temps = w.next;
                }
            }
            // This only works if all temps of block are on the front of the list.
            // Either prev_w:=w here, or if the above is always true, stop the loop
            // when the first unequal block is encountered.
            w = w.next;
        }
    }

    /*--------------------------------------------- direct stack manipulation ---*/

    // NOTE: This naming is very confusing.  A CG.Var and CG.Val are different
    //       things.  But both are referred-to in many places as "temporaries",
    //       "temps", "tmps", etc.  Declare_temp, Free_temp and Free_temps refer
    //       to CG.Vars.

    /** a stack value: R-value or L-value (BRANDED "CG.Val" REF ValRec) */
    public static final class Val extends ValRec {
        private Val() {}
    }

    /** return s0;  pop -- if necessary, create a temp and store s0 in it. */
    public static Val pop() {
        // POST: result.Kind IN {Direct, Absolute,
        M3CG.Var z;
        Val v;
        // get a free value
        v = free_values;
        if (v == null) {
            v = new Val();
        } else {
            free_values = v.next;
        }

        // fill it in
        {
            ValRec x = stack[(int) sCheck(1, "Pop")];
            v.assign(x);
        }
        sPop(1, "Pop");

        // put it on the busy_values list.
        v.next = busy_values;
        busy_values = v;

        // If it's on the M3CG stack, pop into a temporary.
        if (v.kind == VKind.Stacked) {
            z = declare_temp(M3.targetMap.CG_Size[v.type.ordinal()], M3.targetMap.CG_Align[v.type.ordinal()],
                             v.type, /* in_memory := */ false);
            cg.store(z, 0, StackType[v.type.ordinal()], v.type);
            v.kind      = VKind.Direct;
            v.temp_base = true;
            v.temp_bits = false;
            v.old_align = M3.targetMap.CG_Align[v.type.ordinal()];
            v.base      = z;
            v.base_align = M3.targetMap.CG_Align[v.type.ordinal()];
            v.bits      = null;
            v.offset    = 0;

        } else if (v.kind == VKind.Pointer) {
            z = declare_addr_temp(/* in_memory := */ false);
            cg.store(z, 0, CGType.Addr, CGType.Addr);
            v.kind      = VKind.Indirect;
            v.type      = CGType.Addr;
            v.temp_base = true;
            v.temp_bits = false;
            v.base      = z;
            v.base_align = M3.target.Address.align;
            // v.bits      := NIL;
            // ^This would violate the commented definition of Pointer, by losing 'bits'.
        }

        return v;
    }

    /** return s0;  pop -- create a temp and store s0 in it. */
    public static Val pop_temp() {
        // POST: Result.Kind = Direct AND Result.offset = 0 AND Result.bits = NIL.
        forceStacked();
        return pop();
    }

    /** push;  s0 := v */
    public static void push(Val v) {
        ValRec x = stack[(int) sCheck(0, "Push")];
        x.assign(v);
        x.temp_base = false;
        x.temp_bits = false;
        x.next      = null;
        tos++;
    }

    /** v := s0;  pop  -- v must have been created by "Pop_temp" */
    public static void store_temp(Val v) {
        AssertError.check(v.kind == VKind.Direct && v.offset == 0, "CG");
        store(v.base, 0, M3.targetMap.CG_Size[v.type.ordinal()], M3.targetMap.CG_Align[v.type.ordinal()], v.type);
    }

    /**
     * free any "temporaries" that "v" created.
     * NOTE: This naming is very confusing.  A CG.Var and CG.Val are different
     * things.  But both are referred-to in many places as temporaries,
     * temps, tmps, etc.  'Free' frees a CG.Val.  It claims to also free
     * CG.Vars that the Val points to, but this is disabled.
     */
    public static void free(Val v) {
        Val x = busy_values;
        Val last_x = null;
        if (v == null) {
            return;
        }
        // remove 'v' from the busy list
        while (true) {
            if (x == null) {
                err("non-busy value freed");
                break;
            } else if (x == v) {
                // we found the match
                if (last_x == null) {
                    busy_values = v.next;
                } else {
                    last_x.next = v.next;
                }
                v.next = free_values;
                free_values = v;
                break;
            } else {
                last_x = x;
                x = x.next;
            }
        }

        // finally, free v's temps
        release_temps(v);
    }

    private static void free_all_values() {
        while (busy_values != null) {
            free(busy_values);
        }
    }

    // Port, omitted: the procedure XForce (commented out in the original).

    /**
     * force s0 to be materialized on the M3CG stack.  If s0 is an L-value,
     * a byte-aligned address is generated.
     * s needs to be provided only if s0 could be stuctured and packed.
     */
    public static void forceStacked(long s) {
        // s needs to be provided only if s0 could be structured and packed.
        // Otherwise, ForceStacked will use the size of S0's type.
        // TODO: It would be a lot cleaner and more consistent if a ValRec had a size
        //       field (in bits), which could be used here.

        VKind OrigKind;

        ValRec x = stack[(int) sCheck(1, "Force")];
        if (s == 0) {
            s = M3.targetMap.CG_Size[x.type.ordinal()];
        }

        OrigKind = x.kind;

        // force the value onto the stack
        switch (x.kind) {

            case Integer -> {
                if (x.type == M3.target.Word.cg_type) {
                    x.type = M3.target.Integer.cg_type;
                } else if (x.type == M3.target.Long.cg_type) {
                    x.type = M3.target.Longint.cg_type;
                } else if (x.type == M3.target.Integer.cg_type
                        || x.type == M3.target.Longint.cg_type) { // ok
                } else {
                    throw new AssertError("CG");
                }
                cg.load_integer(x.type, x.int_);
            }

            case Float -> {
                x.type = M3.targetMap.Float_types[TFloat.prec(x.float_).ordinal()].cg_type;
                cg.load_float(x.type, x.float_);
            }

            case Stacked -> {
                // value is already on the M3CG stack
            }

            case Direct -> {
                if (x.bits != null) {
                    err("attempt to force a Direct from a bit-level address...");
                }
                // REVIEW: Force_byte_align will need to use the value alignment of
                //         ADR(base+offset).  Right now, it is using base_value_align.
                //         Is this what base_value_align means? Or do does it only take
                //         into account the value of base, IWC we need to GCD in the offset
                //         here and pass to Force_byte_align.
                force_byte_align(x, s);
                cg.load(x.base, asBytes(x.offset), x.type, StackType[x.type.ordinal()]);
                x.type = StackType[x.type.ordinal()];
                x.base_align = M3.targetMap.CG_Align[x.type.ordinal()];
                // x.base_value_align := x.addr_align; Does not change.
            }

            case Absolute -> {
                if (x.bits != null) {
                    err("attempt to force Absolute with variable bits.");
                }
                force_byte_align(x, s);
                cg.load_address(x.base, asBytes(x.offset));
                x.base_value_align = x.base_align;
                x.base_align = M3.target.Address.align;
            }

            case Indirect -> {
                if (x.bits != null) {
                    err("attempt to force Indirect with variable bits.");
                }
                force_byte_align(x, s);
                cg.load(x.base, 0, CGType.Addr, CGType.Addr);
                // x.base_value_align is unchanged.
                if (x.offset != 0) {
                    cg.add_offset(asBytes(x.offset));
                    x.addr_align = GCD(x.addr_align, x.offset);
                }
                x.base_align = M3.target.Address.align;
            }

            case Pointer -> {
                if (x.bits != null) {
                    err("attempt to force Pointer with variable bits.");
                }
                force_byte_align(x, s);
                // x.base_value_align is unchanged.
                if (x.offset != 0) {
                    cg.add_offset(asBytes(x.offset));
                    x.addr_align = GCD(x.addr_align, x.offset);
                }
            }
        }

        // free any temps that we used
        release_temps(x);

        // finish the descriptor
        if (x.type != StackType[x.type.ordinal()]) {

            // Error.Warn
            //   (2, "Not StackType in ForceStacked, Kind = "
            //       & VKindImage (OrigKind));
            x.type = StackType[x.type.ordinal()];
            // REVIEW: This seems peculiar.  It converts any Word* type to its
            //         same-sized Int* counterpart on the stack.  ZTypes (in
            //         M3CG.i3), which claims to be the set of arithmetic types
            //         used on the stack, includes Word* types.  Perhaps this is
            //         just an un-updated leftover from Modula2 or 2+.  Perhaps
            //         it all works because no direct stack operators care about
            //         signedness.  Operands of short memory types *are*
            //         sign-extended or zero-extended when loaded, as here.

        }
        x.kind      = VKind.Stacked;
        x.offset    = 0;
        x.next      = null;
    }

    public static void forceStacked() {
        forceStacked(0);
    }

    // FIXME: This code seems, from reading, to have several bugs.  A experiment
    //    shows that the copy case does not get executed, when compiling most of the
    //    Modula3 distribution and several other things.
    //    Problems:
    //      - The size parameter 's' passed to Load is wrong for what to load and
    //        store in the temp.  The needed size of the referenced value is not
    //        available here, but the remainder of the referenced word should be
    //        big enough, and unnecessary extra bits would be harmless.
    //      - For Indirect and Pointer, OFFS should not be included in the
    //        address of what to load.  (But should, for Absolute and Direct).
    //      - Load requires its alignment parameter to be for where the base
    //        Var is stored, but x.align (now x.old_align) is often the
    //        alignment of the referenced location.  The needed value would be
    //        x.base_align.
    //      - For Pointer, x.base, passed to Load, is not meaningful.
    //      - Do we really know that this is always RHS side context, where making
    //        a copy of the referenced value is correct?
    //     2018-05-23, rodney.m.bates@acm.org.

    /**
     * Force x's base alignment as large as statically known, but at least
     * byte-aligned.
     * Make a copy, if necessary.  Works only on things that fit in a word.
     */
    private static void force_byte_align(ValRec x, long s) {
        long best_align;
        long word_size = M3.target.Word.size;
        long word_align = M3.target.Word.align;
        M3CG.Var tmp;
        int[] shift_TInt = TInt.newInt();
        best_align = lv_align(x);
        if (Math.floorMod(best_align, Target.Byte) == 0) { // Already byte-aligned.
            x.old_align = best_align;
        }
        if (x.type == CGType.Addr && Math.floorMod(x.base_value_align, Target.Byte) != 0) {
            // Address is not statically known to be byte-aligned.
            // This could not happen unless Addr is smaller than Word.
            AssertError.check(M3.targetMap.CG_Size[x.type.ordinal()] <= word_size, "CG");
            // ^Because front end won't allow a BITS component to cross a word boundary.
            Error.warn(2, "Forcing in Force_byte_align, Kind = "
                          + vKindImage(x.kind));
            // Extract into a temp and change x to refer to it.
            load(x.base, x.offset, s, x.base_align, word_align, M3.target.Word.cg_type);
            tmp = declare_temp(word_size, word_align, M3.target.Word.cg_type, /* in_memory := */ true);
            if (M3.target.endian == Target.Endian.Big && word_size > s) {
                // Left-justify the value in tmp.
                TInt.fromInt(word_size - s, /*VAR*/ shift_TInt);
                cg.load_integer(M3.target.Word.cg_type, shift_TInt);
                cg.shift_left(M3.target.Word.cg_type);
            }
            store(tmp, 0, word_size, word_align, M3.target.Word.cg_type);
            x.kind = VKind.Absolute;
            x.type = CGType.Addr;
            x.base = tmp;
            x.temp_base = true;
            x.offset = 0;
            x.bits = null;
            x.temp_bits = false;
            x.old_align = word_align;
            x.base_align = M3.target.Address.align;
            x.base_value_align = word_align;
            x.addr_align = word_align;
        }
    }

    // Port, omitted: the procedure Force_LValue (commented out in the
    // original).

    private static void release_temps(ValRec x) {
        if (x.temp_base) {
            free_temp(x.base);
        }
        if (x.temp_bits) {
            free_temp(x.bits);
        }
        // NOTE: Free_temp is a NOP.
        x.temp_base = false;
        x.temp_bits = false;
        x.base      = null;
        x.bits      = null;
    }

    private static void forceStacked1(String tag) {
        forceStacked();
        sPop(1, tag);
    }

    private static boolean forceStacked2(String tag, boolean commute) {
        boolean swapped = force_pair(commute);
        sPop(2, tag);
        return swapped;
    }

    /*---------------------------------------- static variable initialization ---*/

    // Global variables may be initialized only once.  All of their init_*
    // calls must be bracketed by begin_init and end_init.  Within a begin/end
    // pair, init_* calls must be made in ascending offset order.  Begin/end
    // pairs may not be nested.  Any space in a global variable that's not
    // explicitly initialized is zeroed.

    /** must precede any init calls */
    public static void begin_init(M3CG.Var v) {
        cg.begin_init(v);
        in_init = true;
        init_pc = 0;
        TInt.assign(init_bits, TInt.Zero);
    }

    /** must follow any init calls */
    public static void end_init(M3CG.Var v) {
        advanceInit(init_pc + Target.Byte - 1); // flush any pending bits
        cg.end_init(v);
        in_init = false;
    }

    private static void dumpPendingNodes(boolean is_const) {
        Node n = pending[ix(is_const)];
        int cnt = 0;
        Node[] xx;
        while (n != null) {
            cnt++;
            n = n.next;
        }
        xx = new Node[cnt];
        n = pending[ix(is_const)];
        cnt = 0;
        while (n != null) {
            xx[cnt] = n;
            cnt++;
            n = n.next;
        }
        sortNodes(xx);
        for (int i = 0; i <= xx.length - 1; i++) {
            xx[i].dump();
        }
        pending[ix(is_const)] = null;
    }

    // Port: in SortNodes, QuickSort and InsertionSort the indices into the
    // array are int.

    private static void sortNodes(Node[] x) {
        quickSort(x, 0, x.length);
        insertionSort(x, 0, x.length);
    }

    private static void quickSort(Node[] a, int lo, int hi) {
        final int CutOff = 9;
        int i, j;
        Node key, tmp;
        while (hi - lo > CutOff) { // sort a[lo..hi)

            // use median-of-3 to select a key
            i = Math.floorDiv(hi + lo, 2);
            if (a[lo].o < a[i].o) {
                if (a[i].o < a[hi - 1].o) {
                    key = a[i];
                } else if (a[lo].o < a[hi - 1].o) {
                    key = a[hi - 1];  a[hi - 1] = a[i];  a[i] = key;
                } else {
                    key = a[lo];  a[lo] = a[hi - 1];  a[hi - 1] = a[i];  a[i] = key;
                }
            } else {
                if (a[hi - 1].o < a[i].o) {
                    key = a[i];  tmp = a[hi - 1];  a[hi - 1] = a[lo];  a[lo] = tmp;
                } else if (a[lo].o < a[hi - 1].o) {
                    key = a[lo];  a[lo] = a[i];  a[i] = key;
                } else {
                    key = a[hi - 1];  a[hi - 1] = a[lo];  a[lo] = a[i];  a[i] = key;
                }
            }

            // partition the array
            i = lo + 1;
            j = hi - 2;

            // find the first hole
            while (a[j].o > key.o) {
                j--;
            }
            tmp = a[j];
            j--;

            while (true) {
                if (i > j) {
                    break;
                }

                while (a[i].o < key.o) {
                    i++;
                }
                if (i > j) {
                    break;
                }
                a[j + 1] = a[i];
                i++;

                while (a[j].o > key.o) {
                    j--;
                }
                if (i > j) {
                    if (j == i - 1) {
                        j--;
                    }
                    break;
                }
                a[i - 1] = a[j];
                j--;
            }

            // fill in the last hole
            a[j + 1] = tmp;
            i = j + 2;

            // then, recursively sort the smaller subfile
            if (i - lo < hi - i) {
                quickSort(a, lo, i - 1);
                lo = i;
            } else {
                quickSort(a, i, hi);
                hi = i - 1;
            }

        } // WHILE (hi-lo > CutOff)
    }

    private static void insertionSort(Node[] a, int lo, int hi) {
        int j;
        Node key;
        for (int i = lo + 1; i <= hi - 1; i++) {
            key = a[i];
            j = i - 1;
            while ((j >= lo) && (key.o < a[j].o)) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    private static void pushPending(Node n, boolean is_const) {
        // n.file := last_file;
        // n.line := last_line;
        n.next = pending[ix(is_const)];
        pending[ix(is_const)] = n;
    }

    private static void dumpNode(Node n) {
        // IF (last_file # n.file) THEN
        //   cg.set_source_file (n.file);
        //   last_file := n.file;
        // END;
        // IF (last_line # n.line) THEN
        //   cg.set_source_line (n.line);
        //   last_line := n.line;
        // END;
    }

    private static void advanceInit(long o) {
        long n_bytes = Math.floorDiv(o - init_pc, Target.Byte);
        int[] tmp = TInt.newInt();
        int[] new_bits = TInt.newInt();
        long size, excess;
        CGType[] t = new CGType[1];
        // <*ASSERT n_bytes >= 0*>
        AssertError.check(in_init, "CG");
        while (n_bytes > 0) {
            if (TInt.EQ(init_bits, TInt.Zero)) {
                // no more bits to flush
                n_bytes = 0;
                init_pc = Math.floorDiv(o, Target.Byte) * Target.Byte;
            } else {
                // send out some number of bytes
                findInitType(n_bytes, init_pc, t);
                size = cardinal(M3.targetMap.CG_Size[t[0].ordinal()]);
                excess = cardinal(TWord.Size - size);
                if (excess == 0) {
                    cg.init_int(Math.floorDiv(init_pc, Target.Byte), init_bits, t[0]);
                    TInt.assign(init_bits, TInt.Zero);
                } else if (M3.target.endian == Target.Endian.Little
                        && TWord.extract(init_bits, 0, size, tmp)
                        && TWord.extract(init_bits, size, excess, new_bits)) {
                    cg.init_int(Math.floorDiv(init_pc, Target.Byte), tmp, t[0]);
                    TInt.assign(init_bits, new_bits);
                } else if (M3.target.endian == Target.Endian.Big
                        && TWord.extract(init_bits, excess, size, tmp)
                        && TWord.extract(init_bits, 0, excess, new_bits)) {
                    cg.init_int(Math.floorDiv(init_pc, Target.Byte), tmp, t[0]);
                    TWord.leftShift(new_bits, size, init_bits);
                } else {
                    err("unable to convert or initialize bit field value??  n_bytes="
                        + Long.toString(n_bytes) + "  size=" + Long.toString(size));
                    // <*ASSERT FALSE*>
                }
                n_bytes -= M3.targetMap.CG_Bytes[t[0].ordinal()];
                init_pc += M3.targetMap.CG_Size[t[0].ordinal()];
            }
        }
    }

    private static boolean findInitType(long n_bytes, long offset, CGType[] t) {
        for (int i = M3.targetMap.Integer_types.length - 1; i >= 0; i--) {
            Target.Int_type z = M3.targetMap.Integer_types[i];
            if ((z.bytes <= n_bytes)
                    && (Math.floorMod(offset, z.align) == 0)) {
                t[0] = z.cg_type;
                return true;
            }
        }
        errI(n_bytes, "cg: unable to find suitable target machine type");
        t[0] = CGType.Void;
        return false;
    }

    private static long GDebugOffset;

    /**
     * initializes the integer static variable at 'ADR(v)+offset' with
     * the 's' low order bits of 'value'
     */
    public static void init_int(long o, long s, int[] value, boolean is_const) {
        long bit_offset;
        CGType[] itype = new CGType[1];
        int[] tmp = TInt.newInt();
        if (o == GDebugOffset) {
            bit_offset = 17;
        }
        AssertError.check(o >= 0, "CG");
        if (!in_init) {
            IntNode n = new IntNode();
            n.o = o;
            n.s = s;
            n.v = TInt.copy(value);
            pushPending(n, is_const);
            return;
        }

        advanceInit(o);
        if (M3.target.endian == Target.Endian.Little) {
            bit_offset = cardinal(o - init_pc);
        } else {
            bit_offset = cardinal(TWord.Size - (o - init_pc) - s);
        }

        if ((o == init_pc)
                && (s >= Target.Byte)
                && (findInitType(Math.floorDiv(s, Target.Byte), init_pc, itype))
                && (M3.targetMap.CG_Size[itype[0].ordinal()] == s)) {
            // simple, aligned integer initialization
            cg.init_int(Math.floorDiv(o, Target.Byte), value, itype[0]);
        } else if (TWord.insert(init_bits, value, bit_offset, s, tmp)) {
            TInt.assign(init_bits, tmp);
        } else {
            err("unable to stuff bit field value??");
            throw new AssertError("CG");
        }
    }

    /**
     * initializes the integer static variable at 'ADR(v)+offset' with
     * the 's' low order bits of 'value'
     */
    public static void init_intt(long o, long s, long value, boolean is_const) {
        int[] val = TInt.newInt();
        boolean b = TInt.fromInt(value, val);
        if (!b
                || TInt.LT(val, M3.target.Integer.min)
                || TInt.LT(M3.target.Integer.max, val)) {
            errI(value, "integer const not representable");
        }
        init_int(o, s, val, is_const);
    }

    private static void dumpInt(IntNode x) {
        dumpNode(x);
        init_int(x.o, x.s, x.v, false);
    }

    /**
     * initializes the static variable at 'ADR(v)+o' with the address
     * of procedure 'value'.
     */
    public static void init_proc(long o, M3CG.Proc value, boolean is_const) {
        AssertError.check(o >= 0, "CG");
        AssertError.check(Math.floorMod(o, M3.target.Address.align) == 0, "CG");
        if (in_init) {
            advanceInit(o);
            AssertError.check(o == init_pc, "CG");
            cg.init_proc(asBytes(o), value);
        } else {
            ProcNode n = new ProcNode();
            n.o = o;
            n.v = value;
            pushPending(n, is_const);
        }
    }

    private static void dumpProc(ProcNode x) {
        dumpNode(x);
        init_proc(x.o, x.v, false);
    }

    /**
     * initializes the static variable at 'ADR(v)+o' with the address
     * of the label 'value'.
     */
    public static void init_label(long o, long value, boolean is_const) {
        AssertError.check(o >= 0, "CG");
        AssertError.check(Math.floorMod(o, M3.target.Address.align) == 0, "CG");
        if (in_init) {
            advanceInit(o);
            AssertError.check(o == init_pc, "CG");
            cg.init_label(asBytes(o), value);
        } else {
            LabelNode n = new LabelNode();
            n.o = o;
            n.v = value;
            pushPending(n, is_const);
        }
    }

    private static void dumpLabel(LabelNode x) {
        dumpNode(x);
        init_label(x.o, x.v, false);
    }

    /**
     * 'Init_address_of_var' would have been a more meaningful name here.
     * initializes the static variable at 'ADR(v)+o' with the address
     * of 'value+bias'.
     */
    public static void init_var(long o, M3CG.Var value, long bias, boolean is_const) {
        // 'init_address_of_var' would have been a more meaningful name here.
        AssertError.check(o >= 0, "CG");
        AssertError.check(Math.floorMod(o, M3.target.Address.align) == 0, "CG");
        AssertError.check(Math.floorMod(bias, Target.Byte) == 0, "CG");
        if (in_init) {
            advanceInit(o);
            AssertError.check(o == init_pc, "CG");
            cg.init_var(asBytes(o), value, asBytes(bias));
        } else {
            VarNode n = new VarNode();
            n.o = o;
            n.v = value;
            n.b = bias;
            pushPending(n, is_const);
        }
    }

    private static void dumpVar(VarNode x) {
        dumpNode(x);
        init_var(x.o, x.v, x.b, false);
    }

    /**
     * initializes the static variable at 'ADR(v)+o' with the integer
     * frame offset of the local variable 'var'.
     */
    public static void init_offset(long o, M3CG.Var var_, boolean is_const) {
        AssertError.check(o >= 0, "CG");
        AssertError.check(Math.floorMod(o, M3.target.Integer.align) == 0, "CG");
        if (in_init) {
            advanceInit(o);
            AssertError.check(o == init_pc, "CG");
            cg.init_offset(asBytes(o), var_);
        } else {
            OffsetNode n = new OffsetNode();
            n.o = o;
            n.v = var_;
            pushPending(n, is_const);
        }
    }

    private static void dumpOffset(OffsetNode x) {
        dumpNode(x);
        init_offset(x.o, x.v, false);
    }

    /**
     * initializes the static variable at 'ADR(v)+offset' with the characters
     * of 'value'
     */
    public static void init_chars(long o, String value, boolean is_const) {
        long len, start;
        AssertError.check(o >= 0, "CG");
        AssertError.check(Math.floorMod(o, Target.Char.align) == 0, "CG");
        if (in_init) {
            advanceInit(o);
            AssertError.check(o == init_pc, "CG");
            start = 0;
            len = value.length();
            while (len - start > Max_init_chars) {
                cg.init_chars(asBytes(o), value.substring((int) start, (int) (start + Max_init_chars)));
                o += Max_init_chars * Target.Char.size;
                start += Max_init_chars;
            }
            if (start < len) {
                cg.init_chars(asBytes(o), value.substring((int) start));
            }
        } else {
            CharsNode n = new CharsNode();
            n.o = o;
            n.t = value;
            pushPending(n, is_const);
        }
    }

    private static void dumpChars(CharsNode x) {
        dumpNode(x);
        init_chars(x.o, x.t, false);
    }

    /**
     * initializes the static variable at 'ADR(v)+offset' with the
     * floating point value 'f'
     */
    public static void init_float(long o, Target.Float f, boolean is_const) {
        AssertError.check(o >= 0, "CG");
        AssertError.check(Math.floorMod(o, Target.Real.align) == 0, "CG");
        if (in_init) {
            advanceInit(o);
            AssertError.check(o == init_pc, "CG");
            cg.init_float(asBytes(o), f);
        } else {
            FloatNode n = new FloatNode();
            n.o = o;
            n.f = f.copy();
            pushPending(n, is_const);
        }
    }

    private static void dumpFloat(FloatNode x) {
        dumpNode(x);
        init_float(x.o, x.f, false);
    }

    /** Emits the zero terminated string and returns its global offset. */
    public static long emitText(String t, boolean is_const) {
        long len, size, align, offset;
        if (t == null) {
            t = "";
        }
        len    = t.length() + 1;
        size   = len * Target.Char.size;
        // align  := MAX (Target.Char.align, Target.Integer.align);
        align  = Target.Char.align;
        offset = Module.allocate(size, align, is_const, "*string*");
        AssertError.check(offset >= 0, "CG");
        CharsNode n = new CharsNode();
        n.o = offset;
        n.t = t;
        pushPending(n, is_const);
        return offset;
    }

    /*------------------------------------------------------------ procedures ---*/

    // Clients compile a procedure by doing:
    //
    //      proc := Declare_procedure (...)
    //        ...declare formals...
    //        ...declare locals...
    //        ...generate nested procedures (IF nested_procs_first)...
    //      Begin_procedure (proc)
    //        ...generate statements of procedure...
    //      End_procedure (...)
    //        ...generate nested procedures (IF NOT nested_procs_first)...
    //
    //  begin_/end_procedure should never be nested;  depending on the
    //  value of 'nested_procs_first', clients compile nested procedures
    //  either immediately before or after their enclosing procedure.

    /**
     * declare and import the external procedure with name 'n' and 'n_params'
     * formal parameters.  It must be a top-level (=0) procedure that returns
     * values of type 'ret_type'.  'cc' is the convention specified
     * in the procedure's &lt;*EXTERNAL*&gt; declaration.  If 'new' is 'TRUE', the formal
     * parameters must be specified by the subsequent 'declare_param' calls.
     *
     * <p>Port: 'new' (VAR OUT) is new_[0].
     */
    public static M3CG.Proc import_procedure(int n, long n_params, CGType ret_type,
                                             Target.CallingConvention cc,
                                             boolean[] new_,
                                             int return_typeid,
                                             int return_typename) {
        M3CG.Proc p;
        if (procedures == null) {
            procedures = newNameTbl();
        }
        if (procedures.containsKey(n)) {
            new_[0] = false;
            return procedures.get(n);
        }
        p = cg.import_procedure(n, n_params, ret_type, cc, return_typeid, return_typename);
        procedures.put(n, p);
        new_[0] = true;
        return p;
    }

    public static M3CG.Proc import_procedure(int n, long n_params, CGType ret_type, Target.CallingConvention cc, boolean[] new_, int return_typeid) {
        return import_procedure(n, n_params, ret_type, cc, new_, return_typeid, M3ID.NoID);
    }

    public static M3CG.Proc import_procedure(int n, long n_params, CGType ret_type, Target.CallingConvention cc, boolean[] new_) {
        return import_procedure(n, n_params, ret_type, cc, new_, 0, M3ID.NoID);
    }

    /**
     * declare a procedure named 'n' with 'n_params' formal parameters
     * at static level 'lev'.  Sets "current procedure" to this procedure.
     * If the name 'n' is NIL, a new unique name will be supplied by the back-end.
     * The type of the procedure's result is specifed in 'ret_type'.  If the new
     * procedure is a nested procedure (level &gt; 1) then 'parent' is
     * the immediately enclosing procedure, otherwise 'parent' is NIL.
     * The formal parameters are specified by the subsequent 'declare_param'
     * calls.
     */
    public static M3CG.Proc declare_procedure(int n, long n_params, CGType ret_type,
                                              long lev, Target.CallingConvention cc,
                                              boolean exported, M3CG.Proc parent,
                                              int return_typeid,
                                              int return_typename) {
        M3CG.Proc p;
        if (procedures == null) {
            procedures = newNameTbl();
        }

        AssertError.check(lev == 0 || !exported, "CG"); // nested functions cannot be exported

        p = cg.declare_procedure(n, n_params, ret_type,
                                 lev, cc, exported, parent,
                                 return_typeid, return_typename);
        procedures.put(n, p);
        return p;
    }

    public static M3CG.Proc declare_procedure(int n, long n_params, CGType ret_type, long lev, Target.CallingConvention cc, boolean exported, M3CG.Proc parent, int return_typeid) {
        return declare_procedure(n, n_params, ret_type, lev, cc, exported, parent, return_typeid, M3ID.NoID);
    }

    public static M3CG.Proc declare_procedure(int n, long n_params, CGType ret_type, long lev, Target.CallingConvention cc, boolean exported, M3CG.Proc parent) {
        return declare_procedure(n, n_params, ret_type, lev, cc, exported, parent, 0, M3ID.NoID);
    }

    /**
     * begin generating code for the procedure 'p'.  Sets "current procedure"
     * to 'p'.
     */
    public static void begin_procedure(M3CG.Proc p) {
        ProcStackNodeRef N = new ProcStackNodeRef();
        N.link = ProcStackRoot;
        ProcStackRoot = N;
        N.free_temps = free_temps;
        N.busy_temps = busy_temps;
        N.block_cnt = block_cnt;
        free_temps     = null;
        busy_temps     = null;
        block_cnt      = 0;
        cg.begin_procedure(p);
    }

    /**
     * marks the end of the code for procedure 'p'.  Sets "current procedure"
     * to NIL.
     */
    public static void end_procedure(M3CG.Proc p) {
        ProcStackNodeRef N = ProcStackRoot;
        free_all_values();
        free_all_temps();
        AssertError.check(free_temps == null, "CG");
        AssertError.check(busy_temps == null, "CG");
        AssertError.check(block_cnt == 0, "CG");
        AssertError.check(N != null, "CG");
        free_temps = N.free_temps;
        busy_temps = N.busy_temps;
        block_cnt = N.block_cnt;
        ProcStackRoot = N.link;
        cg.end_procedure(p);
    }

    /** marks the beginning of nested anonymous blocks */
    public static void begin_block() {
        cg.begin_block();
        block_cnt++;
    }

    /** marks the ending of nested anonymous blocks */
    public static void end_block() {
        free_block_temps(block_cnt);
        block_cnt--;
        cg.end_block();
    }

    /**
     * note that nested procedure 'p's body occured at the current location
     * in the source.  In particular, nested in whatever procedures,
     * anonymous blocks, or exception scopes surround this point.
     */
    public static void note_procedure_origin(M3CG.Proc p) {
        cg.note_procedure_origin(p);
    }

    /*------------------------------------------------------------ statements ---*/

    /**
     * define 'l' to be at the current pc, if 'barrier', 'l' bounds an exception
     * scope and no code is allowed to migrate past it.
     */
    public static void set_label(long l, boolean barrier) {
        cg.set_label(l, barrier);
    }

    public static void set_label(long l) {
        set_label(l, false);
    }

    /** GOTO l */
    public static void jump(long l) {
        cg.jump(l);
    }

    /** tmp := s0.I32; pop; IF (tmp # 0) GOTO l */
    public static void if_true(long l, int f) {
        forceStacked1("If_true");
        cg.if_true(M3.target.Integer.cg_type, l, f);
    }

    /** tmp := s0.I32; pop; IF (tmp = 0) GOTO l */
    public static void if_false(long l, int f) {
        forceStacked1("If_false");
        cg.if_false(M3.target.Integer.cg_type, l, f);
    }

    /** == compare(t, op); if_true(l) */
    public static void if_compare(CGType t, CompareOp op, long l, int f) {
        if (forceStacked2("If_compare", /* commute := */ true)) {
            op = M3CG.SwappedCompare[op.ordinal()];
        }
        cg.if_compare(t, op, l, f);
    }

    /**
     * == IF (true # No_Label) THEN  compare(t, op); if_true(true)
     *                         ELSE  compare(t, op); if_false(false)
     */
    public static void if_then(CGType t, CompareOp op, long true_, long false_, int f) {
        if (forceStacked2("If_compare", /* commute := */ true)) {
            op = M3CG.SwappedCompare[op.ordinal()];
        }
        if (true_ == No_label) {
            op = M3CG.NotCompare[op.ordinal()];
            true_ = false_;
        }
        cg.if_compare(t, op, true_, f);
    }

    /** tmp := s0.I32; pop; GOTO labels[tmp]  (NOTE: no range checking on s0.I32) */
    public static void case_jump(long[] labels) {
        forceStacked1("Case_jump");
        cg.case_jump(M3.target.Integer.cg_type, labels);
    }

    /** Returns s0.t if the stack is non-empty, otherwise returns no value. */
    public static void exit_proc(CGType t) {
        if (t != CGType.Void) {
            forceStacked1("Exit_proc");
        }
        cg.exit_proc(t);
    }

    /*----------------------------------------------------------- expressions ---*/

    //  The code to evaluate expressions is generated by calling the
    //  procedures listed below.  Each procedure corresponds to an
    //  instruction for a simple stack machine.  Values in the stack
    //  have a type and a size.  Operations on the stack values are
    //  also typed.  Type mismatches may cause bad code to be generated.
    //  Explicit type conversions must be used.
    //
    //  Integer values on the stack, regardless of how they are loaded,
    //  are sign-extended to full-width values.  Similarly, word values
    //  on the stack are always zero-extened to full-width values.
    //
    //  The expression stack must be empty at each label, jump, call,
    //  or store operation.  The stack must contain exactly one value
    //  prior to a conditional or indexed jump.
    //
    //  All addresses are bit addresses.  There is no boolean type;  boolean
    //  operators yield [0..1].
    //
    //  Operations on word values are performed MOD the word size and are
    //  not checked for overflow.  Operations on integer values may or may not
    //  cause checked runtime errors depending on the particular code generator.
    //
    //  The operators are declared below with a definition in terms of
    //  what they do to the execution stack.  For example,  ceiling(Reel)
    //  returns the ceiling, an integer, of the top value on the stack,
    //  a real:  s0.I := CEILING (s0.R).
    //
    //  Unless otherwise indicated, operators have the same meaning as in
    //  the Modula-3 report.

    /*------------------------------------------------------------ load/store ---*/

    /**
     * push ; s0.t := Mem [ ADR(v) + o : s ]
     * if t=A, addr_align applies to where s0.t points, otherwise irrelevant.
     */
    public static void load(M3CG.Var v, long o, long s,
                            long base_align /*of ADR(v)*/, long addr_align /*of Value(v)*/, CGType t) {
        // push ; s0.t := Mem [ ADR(v) + o : s ]
        // v, o, and addr_align describe the memory operand to be loaded. t is the
        //   stack type to load into.
        long tsize = M3.targetMap.CG_Size[t.ordinal()];
        long talign = M3.targetMap.CG_Align[t.ordinal()];
        long best_align;
        long best_size;
        CGType best_type;
        if ((tsize == s) && Math.floorMod(base_align + o, talign) == 0) {
            // a full t-size'd and t-align'ed load
            simpleLoad(v, o, t, base_align, addr_align);

        } else if (tsize < s) {
            errI(s, "memory size exceeds to-be-loaded size.");
            simpleLoad(v, o, t, base_align, addr_align);
            forceStacked();  // to connect the error message to the bad code

        } else if ((t == M3.target.Word.cg_type) || (t == M3.target.Integer.cg_type)
                || (t == M3.target.Long.cg_type) || (t == M3.target.Longint.cg_type)) {
            best_type = mayFindIntType(t, s, o, base_align);
            if (best_type != CGType.Void) {
                best_size  = M3.targetMap.CG_Size[best_type.ordinal()];
                best_align = M3.targetMap.CG_Align[best_type.ordinal()];
                talign = Math.floorMod(base_align + o, best_align);
                if ((s == best_size) && (talign == 0)) {
                    // this is a simple partial word load
                    simpleLoad(v, o, best_type, base_align, addr_align);
                } else {
                    // unaligned, partial load
                    cg.load(v, asBytes(o - talign), best_type, StackType[t.ordinal()]);
                    if (M3.target.endian == Target.Endian.Little) {
                        cg.extract_mn(StackType[t.ordinal()], Target.SignedType[t.ordinal()],
                                      talign, s);
                    } else {
                        cg.extract_mn(StackType[t.ordinal()], Target.SignedType[t.ordinal()],
                                      best_size - talign - s, s);
                    }
                    ValRec x = stack[(int) sCheck(0, "Load")];
                    x.kind = VKind.Stacked;
                    x.type = t;
                    x.base = null;
                    x.base_align = talign;
                    x.base_value_align = addr_align;
                    x.addr_align = addr_align;
                    x.bits = null;
                    x.offset = 0;
                    x.temp_base = false;
                    x.temp_bits = false;
                    x.next = null;
                    tos++;
                }
            } else if (Math.floorMod(base_align, Target.Byte) == 0) {
                load_addr_of(v, o, base_align);
                loadIndirectStraddling(t, o, s);
                // IMPROVEME: A (direct) LoadStraddling could generate better code.
            } else {
                err("unaligned word-straddling load, type=" + Target.TypeNames[t.ordinal()]
                    + "  size/offset/align=" + Long.toString(s) + "/" + Long.toString(o)
                    + "/" + Long.toString(base_align));
                simpleLoad(v, o, t, base_align, addr_align);
                forceStacked();  // to connect the error message to the bad code
            }
        } else {
            err("unaligned partial-word load, type=" + Target.TypeNames[t.ordinal()]
                + "  size/offset/align=" + Long.toString(s) + "/" + Long.toString(o)
                + "/" + Long.toString(base_align));
            simpleLoad(v, o, t, base_align, addr_align);
            forceStacked();  // to connect the error message to the bad code
        }
    }

    private static void simpleLoad(M3CG.Var v, long o, CGType t, long base_align, long addr_align) {
        ValRec x = stack[(int) sCheck(0, "SimpleLoad")];
        x.kind      = VKind.Direct;
        x.type      = t;
        x.temp_base = false;
        x.temp_bits = false;
        x.old_align = Target.Byte;
        x.base_align = base_align;
        x.base_value_align = addr_align;
        x.addr_align = GCD(addr_align, o);
        x.base      = v;
        x.bits      = null;
        x.offset    = o;
        x.next      = null;
        tos++;
    }

    /**
     * s0.t := Mem [s0.A + o : s]
     * PRE: (s0.A + o) is a byte multiple.
     * PRE: s &lt;= Target.Word.size.
     * i.e., the field starts on a byte boundary but may end within a byte
     * and may straddle one word boundary.
     */
    private static void loadIndirectStraddling(CGType t, long o, long s) {
        long wordAddrMask;
        // Port: byteOffsetMask is 0 if the word size is neither 32 nor 64.
        long byteOffsetMask = 0;
        M3CG.Var byteAddr, word0Addr, bitOffset, word1BitCt;
        M3CG.Var field;
        long mergeLab = next_label();
        long altLab = next_label();
        int[] fieldMask = TInt.newInt();
        if (M3.target.Word.size == Target.Word32.size) {
            byteOffsetMask = 0b11;
        } else if (M3.target.Word.size == Target.Word64.size) {
            byteOffsetMask = 0b111;
        }
        wordAddrMask = ~byteOffsetMask;

        cg.comment("LoadIndirectStraddling:");

        field = cg.declare_temp(M3.target.Word.bytes,
                                M3.targetMap.CG_Align_bytes[M3.target.Word.cg_type.ordinal()],
                                M3.target.Word.cg_type,
                                /* in_memory := */ false);

        // This uses a mix of cg.* calls, which only alter the M3CG stack,
        //   and CG calls, which manipulate the CG stack and M3CG stack,
        //   keeping them consistent.  This gets confusing.  The comments
        //   show the contents of both stacks, CG first.  Items on the M3CG
        //   stack in parentheses are conditionally there, and can be put
        //   there unconditionally by a Force. call.
        // On CG stack: starting byte address, AKA Ba.
        //                                        CG stack   ; M3CG stack
        //                                        --------     ----------
                                               // Ba.A       ; (Ba.A)

        // Save the byte address, with offset added, in temp byteAddr, AKA Bao.
        forceStacked();                        // Ba.A       ; Ba.A
        if (o != 0) {
            cg.add_offset(asBytes(o));         // Ba+o.A     ; Ba+o.A
        }
        byteAddr = cg.declare_temp(M3.target.Address.bytes,
                                   M3.targetMap.CG_Align_bytes[CGType.Addr.ordinal()],
                                   CGType.Addr,
                                   /* in_memory := */ false);
        cg.store(byteAddr, 0, CGType.Addr, CGType.Addr);
                                               // Ba+o.A     ;
        sPop(1, "");                           //            ;

        if (M3.target.endian == Target.Endian.Little) {

            // Compute and save address of full memory word zero in temp word0Addr,
            //   AKA w0a.
            word0Addr = cg.declare_temp(M3.target.Address.bytes,
                                        M3.targetMap.CG_Align_bytes[CGType.Addr.ordinal()],
                                        CGType.Addr,
                                        /* in_memory := */ false);
            cg.load(byteAddr, 0, CGType.Addr, CGType.Addr);
                                               //            ; Ba+o.A
            cg.loophole(CGType.Addr, M3.target.Word.cg_type);
                                               //            ; Ba+o.W
            cg_load_word(wordAddrMask); // AKA wam
                                               //            ; wam.W Ba+o.W
            cg.and(M3.target.Word.cg_type);    //            ; w0a.W
            cg.loophole(M3.target.Word.cg_type, CGType.Addr);
                                               //            ; w0a.A
            cg.store(word0Addr, 0, CGType.Addr, CGType.Addr);
                                               //            ;
            // Compute and save bit offset within word zero in temp bitOffset,
            //   AKA bo. Byte offset is AKA Bo.
            bitOffset = cg.declare_temp(M3.target.Word.bytes,
                                        M3.targetMap.CG_Align_bytes[M3.target.Word.cg_type.ordinal()],
                                        M3.target.Word.cg_type,
                                        /* in_memory := */ false);
            cg.load(byteAddr, 0, CGType.Addr, CGType.Addr);
                                               //            ; Ba+o.A
            cg.loophole(CGType.Addr, M3.target.Word.cg_type);
                                               //            ; Ba+o.W
            cg_load_word(byteOffsetMask);      //            ; Bom.W Ba+o.W
            cg.and(M3.target.Word.cg_type);    //            ; Bo.W
            cg_load_word(Log2OfByte);          //            ; shiftct.W, Bo.W
            cg.shift_left(M3.target.Word.cg_type); //        ; bo.W
            cg.store(bitOffset, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ;

            // Load word zero and mask out the to-be-stored-into bits, AKA w0m.
            cg.load(word0Addr, 0, CGType.Addr, CGType.Addr);
                                               //            ; w0a.A
            cg.load_indirect(0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ; w0.W

            // Right shift by bit offset to right-justify and zero-extend
            //   word zero's subfield, AKA f0.
            cg.load(bitOffset, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ; bo.W w0.W
            cg.shift_right(M3.target.Word.cg_type); //       ; f0.W
            cg.store(field, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //             ;
            // ^This could be the final result.  Or not.

            // Compute bit count from word one, AKA bc1.
            cg.load(bitOffset, 0, M3.target.Word.cg_type, M3.target.Integer.cg_type);
                                               //            ; bo.I
            cg_load_intt(s - M3.target.Word.size); //       ; const.I bo.I
                         // ^May be negative.
            cg.add(M3.target.Integer.cg_type); //            ; bc1.I
            word1BitCt = cg.declare_temp(M3.target.Integer.bytes,
                                         M3.targetMap.CG_Align_bytes[M3.target.Integer.cg_type.ordinal()],
                                         M3.target.Integer.cg_type,
                                         /* in_memory := */ false);
            cg.store(word1BitCt, 0, M3.target.Integer.cg_type, M3.target.Integer.cg_type);
                                               //            ;

            // Skip if no bytes from word one are involved.
            cg.load(word1BitCt, 0, M3.target.Integer.cg_type, M3.target.Integer.cg_type);
                                               //            ; bc1.I
            cg_load_word(0);                   //            ; 0.W bc1.I
            cg.if_compare(M3.target.Integer.cg_type, CompareOp.LE, altLab, Maybe);
                                               //            ;

            // Fetch word one, AKA w1.
            cg.load(word0Addr, 0, CGType.Addr, CGType.Addr);
                                               //            ; w0a.A
            cg.load_indirect(M3.target.Word.bytes, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ; w1.W

            // Mask out garbage bits of word one, giving its relevant subfield,
            //   AKA f1, which is already right-justified.
            cg_load_word(-1); // All ones.     //            ; ones.W w1.W
            cg.load(word1BitCt, 0, M3.target.Integer.cg_type, M3.target.Integer.cg_type);
                                               //            ; bc1.I ones.W w1.W
            cg.shift_left(M3.target.Word.cg_type); //        ; negmask.W, w1.W
            cg.not(M3.target.Word.cg_type);    //            ; mask.W w1.W
            cg.and(M3.target.Word.cg_type);    //            ; f1.W

            // Rotate word one right by bit count, to align its subfield,
            //   AKA f1a.
            cg.load(bitOffset, 0, M3.target.Word.cg_type, M3.target.Integer.cg_type);
                                               //            ; bo.I f1.W
            cg.rotate_right(M3.target.Word.cg_type); //      ; f1a.W

            // Combine subfields from both words, AKA f.
            cg.load(field, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ; f0.W f1a.W
            cg.or(M3.target.Word.cg_type);     //            ; f.W
            cg.store(field, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ;

            cg.jump(mergeLab);
            cg.set_label(altLab);

            // Zero-extend by masking out high bits.
            // Here, f0 almost equals f
            cg.load(field, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ; f0.W
            TWord.nBitsOnRight(s, /*OUT*/ fieldMask);
            cg.load_integer(M3.target.Word.cg_type, fieldMask);
                                               //            ; mask.W f0.W
            cg.and(M3.target.Word.cg_type);    //            ; f
            cg.store(field, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ;

            cg.set_label(mergeLab);
            cg.load(field, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ; f.W
            sPush(M3.target.Word.cg_type);     // f.W        ; f.W

        } else { // Big endian.

            // Port, omitted: the big endian code (commented out in the
            // original).

        } // Endianness.

        // Fix the type of the result on the CG stack.
        sPop(1, "LoadIndirectStrad, #6");      //             ; f.I
        sPush(t);                              // f.t         ; f.I
    }

    /**
     * push ; s0.A := ADR(v) + o
     * addr_align applies to where s0.A points.
     */
    public static void load_addr_of(M3CG.Var v, long o, long addr_align) {
        // push ; s0.A := ADR(v) + o
        // addr_align applies to where the address points.
        ValRec x = stack[(int) sCheck(0, "Load_addr_of")];
        x.kind      = VKind.Absolute;
        x.type      = CGType.Addr;
        x.temp_base = false;
        x.temp_bits = false;
        x.old_align = byteAlign(addr_align) * Target.Byte;
        x.base_align = M3.target.Address.align;
        x.base_value_align = addr_align;
        x.addr_align = GCD(addr_align, o);
        x.base      = v;
        x.bits      = null;
        x.offset    = o;
        x.next      = null;
        tos++;
    }

    /**
     * == Load_addr_of (v, o, addr_align) ;
     * Once this said "free v when this L-value is consumed", but this mechanism
     * is disabled, apparently replaced by Free_temps.
     */
    public static void load_addr_of_temp(M3CG.Var v, long o, long addr_align) {
        load_addr_of(v, o, addr_align);
        stack[(int) sCheck(1, "Load_addr_of_temp")].temp_base = true;
    }

    /**
     * Load_as_int' would have been a more meaningful name here.
     * It actually means load the *contents* of v and gives it integer type t.
     * == Load (v, o, t.size, t.align, t)
     */
    public static void load_int(CGType t, M3CG.Var v, long o) {
        simpleLoad(v, o, t, M3.targetMap.CG_Align[t.ordinal()], Target.Word8.align);
    }

    public static void load_int(CGType t, M3CG.Var v) {
        load_int(t, v, 0);
    }

    /**
     * 'Load_as_addr' would have been a more meaningful name here.
     * It actually means load the *contents* of v and give it type Addr.
     * == Load (v, o, Target.Address.size, Target.Address.align, addr_align, Type.Addr)
     */
    public static void load_addr(M3CG.Var v, long o, long addr_align) {
        // Actually, this means load the *value* of v and give it type Addr.
        simpleLoad(v, o, CGType.Addr, M3.target.Address.align, addr_align);
    }

    public static void load_addr(M3CG.Var v, long o) {
        load_addr(v, o, Target.Word8.align);
    }

    /**
     * s0.t := Mem [s0.A + o : s]
     * If t=A, addr_align applies to where final s0.t points, otherwise irrelevant.
     */
    public static void load_indirect(CGType t, long addedOffset, long s, long addr_align) {
        long t_size = M3.targetMap.CG_Size[t.ordinal()];
        long t_align = M3.targetMap.CG_Align[t.ordinal()];
        long best_align;
        long best_size;
        CGType best_type;
        long fetch_align;
        long oddStaticBitCt;
        M3CG.Var save_bits = null;
        M3CG.Var oddRTBits;
        boolean save_temp_bits;

        CGType best_type1;
        long a1;
        long oddStaticBitCt1;
        ValRec x1 = new ValRec(), x2 = new ValRec(), x3 = new ValRec(), x4 = new ValRec(),
               x5 = new ValRec(), x6 = new ValRec(), x7 = new ValRec(), x8 = new ValRec(),
               x9 = new ValRec(), x10 = new ValRec();

        // Load_indirect
        x1.assign(stack[(int) sCheck(1, "Load_indirect-x1")]);
        forceAddr2SAP(addedOffset);
        x2.assign(stack[(int) sCheck(1, "Load_indirect-x2")]);
        ValRec x = stack[(int) sCheck(1, "Load_indirect")];
        // x.VKind IN {Stacked, Absolute, Pointer}
        a1 = x.addr_align;

        if (s == t_size && Math.floorMod(x.addr_align, t_align) == 0) {
            // A full t_size'd and t_align'ed load.
            simpleIndirectLoad(x, t, addr_align);

        } else if (s == t_size && Math.floorMod(x.addr_align, Target.Byte) == 0
                && M3.target.Allow_packed_byte_aligned) {
            // A full t_size'd and byte-aligned load, supported by the processor.
            //   This is used by packed structures.
            simpleIndirectLoad(x, t, addr_align);

        } else if (s > t_size) {
            err("In Load_indirect, memory size exceeds stack size.");
            forceStacked(); // to connect the error message with the code
            x3.assign(stack[(int) sCheck(1, "Load_indirect-x3")]);
            simpleIndirectLoad(x, t, addr_align);

        } else if ((t == M3.target.Word.cg_type) || (t == M3.target.Integer.cg_type)
                || (t == M3.target.Long.cg_type) || (t == M3.target.Longint.cg_type)) {
            best_type = mayFindIntType(t, s, x.offset, x.base_value_align);
            if (best_type != CGType.Void) {
                best_type1 = best_type;
                best_size = M3.targetMap.CG_Size[best_type.ordinal()];
                best_align = M3.targetMap.CG_Align[best_type.ordinal()];
                oddStaticBitCt = Math.floorMod(x.offset, best_align);
                oddStaticBitCt1 = oddStaticBitCt;
                if ((oddStaticBitCt == 0) && (x.bits == null)) {
                    // A best_align'ed load.
                    simpleIndirectLoad(x, best_type, addr_align);
                    // x.kind IN {Direct, Stacked}
                    if (s != best_size) { // NOT all of best_size to be loaded.
                        forceStacked();
                        x4.assign(stack[(int) sCheck(1, "Load_indirect-x4")]);
                        if (M3.target.endian == Target.Endian.Little) {
                            cg.extract_mn(StackType[t.ordinal()],
                                          Target.SignedType[t.ordinal()], 0, s);
                        } else {
                            cg.extract_mn(StackType[t.ordinal()],
                                          Target.SignedType[t.ordinal()], best_size - s, s);
                        }
                        finishLoadIndirect(x, t, addr_align);
                    }
                } else if (x.bits == null) {
                    // Unaligned, partial best_size'd load.
                    x.offset -= oddStaticBitCt;
                    x5.assign(stack[(int) sCheck(1, "Load_indirect-x5")]);
                    simpleIndirectLoad(x, best_type, addr_align);
                    x6.assign(stack[(int) sCheck(1, "Load_indirect-x6")]);
                    forceStacked();
                    x7.assign(stack[(int) sCheck(1, "Load_indirect-x7")]);
                    if (M3.target.endian == Target.Endian.Little) {
                        cg.extract_mn(StackType[t.ordinal()], Target.SignedType[t.ordinal()],
                                      oddStaticBitCt, s);
                    } else {
                        cg.extract_mn(StackType[t.ordinal()], Target.SignedType[t.ordinal()],
                                      best_size - oddStaticBitCt - s, s);
                    }
                    finishLoadIndirect(x, t, addr_align);
                } else { // bits # NIL
                    // Unaligned, partial-word load with RT variable offset.
                    fetch_align = Math.min(x.addr_align, t_size);
                    if (best_size < fetch_align) {
                        // make sure we load the largest possible aligned value,
                        //   because we can't tell how far the variable bit-offset
                        //   will take us.
                        best_type = mustFindIntType(t, Math.max(s, fetch_align), Math.floorMod(x.offset, fetch_align),
                                                    x.base_value_align);
                        best_size  = M3.targetMap.CG_Size[best_type.ordinal()];
                        best_align = M3.targetMap.CG_Align[best_type.ordinal()];
                        oddStaticBitCt = Math.floorMod(x.offset, best_align);
                    }
                    if (x.base_value_align < best_align) {
                        err("unaligned fetch type in Load_indirect.");
                    }

                    oddRTBits = reduceBits(best_align);

                    // hide the runtime bit offset value.
                    save_bits = x.bits;
                    x.bits = null;
                    save_temp_bits = x.temp_bits;
                    x.temp_bits = false;

                    // generate the aligned load
                    x.offset -= oddStaticBitCt;
                    x8.assign(stack[(int) sCheck(1, "Load_indirect-x8")]);
                    simpleIndirectLoad(x, best_type, addr_align);
                    x9.assign(stack[(int) sCheck(1, "Load_indirect-x9")]);
                    forceStacked();
                    x10.assign(stack[(int) sCheck(1, "Load_indirect-x10")]);

                    // Runtime compute the full bit offset
                    if (M3.target.endian == Target.Endian.Little) {
                        cg.load(oddRTBits, 0, M3.target.Integer.cg_type, M3.target.Integer.cg_type);
                        if (oddStaticBitCt != 0) {
                            cg_load_intt(oddStaticBitCt);
                            cg.add(M3.target.Integer.cg_type);
                        }
                    } else { // big endian
                        cg_load_intt(best_size - oddStaticBitCt - s);
                        cg.load(oddRTBits, 0, M3.target.Integer.cg_type, M3.target.Integer.cg_type);
                        cg.subtract(M3.target.Integer.cg_type);
                    }

                    // extract the needed bits
                    cg.extract_n(StackType[t.ordinal()], Target.SignedType[t.ordinal()], s);
                    finishLoadIndirect(x, t, addr_align);

                    free_temp(oddRTBits);
                    if (save_temp_bits) {
                        free_temp(save_bits);
                    }
                }
            } else if (Math.floorMod(x.addr_align, Target.Byte) == 0) {
                loadIndirectStraddling(t, addedOffset, s);
            } else {
                err("unaligned word-straddling load_indirect, type=" + Target.TypeNames[t.ordinal()]
                    + "  s/a=" + Long.toString(s) + "/" + Long.toString(x.addr_align));
                forceStacked();  // to connect the error message
                simpleIndirectLoad(x, t, addr_align);
                forceStacked();
            }
        } else {
            err("unaligned partial-word load_indirect, type=" + Target.TypeNames[t.ordinal()]
                + "  s/a=" + Long.toString(s) + "/" + Long.toString(x.addr_align));
            forceStacked();  // to connect the error message
            simpleIndirectLoad(x, t, addr_align);
            forceStacked();
        }
    }

    public static void load_indirect(CGType t, long o, long s) {
        load_indirect(t, o, s, Target.Word8.align);
    }

    private static void finishLoadIndirect(ValRec x, CGType t, long addr_align) {
        x.kind = VKind.Stacked;
        x.type = StackType[t.ordinal()];
        x.base = null;
        x.offset = 0;
        x.temp_base = false;
        x.bits = null;
        x.temp_bits = false;
        x.old_align = Target.Byte;
        x.base_align = M3.targetMap.CG_Align[x.type.ordinal()];
        x.base_value_align = addr_align;
        x.addr_align = addr_align;
    }

    /**
     * PRE: x.kind IN {Stacked, Absolute, Pointer}.
     * Load full t-size'd and t-align'ed.
     */
    private static void simpleIndirectLoad(ValRec x, CGType t, long addr_align) {
        long offs;
        CGType stackType;
        switch (x.kind) {
            case Absolute -> {
                x.kind = VKind.Direct;
                x.type = t;
                x.addr_align = addr_align;
            }

            case Pointer, Stacked -> {
                offs = x.offset;
                x.offset = 0;
                stackType = StackType[t.ordinal()];
                cg.load_indirect(asBytes(offs), t, stackType);
                finishLoadIndirect(x, t, addr_align);
            }

            default -> { // ??
                errI(x.kind.ordinal(), "bad VKind in SimpleIndirectLoad");
                forceStacked();
                stackType = StackType[t.ordinal()];
                cg.load_indirect(0, t, stackType);
                finishLoadIndirect(x, t, addr_align);
            }
        }
    }

    /** Mem [ ADR(v) + o : s ] := s0.t ; pop */
    public static void store(M3CG.Var v, long o, long s, long base_align /*of ADR(v)*/,
                             CGType t) {
        long t_size = M3.targetMap.CG_Size[t.ordinal()];
        long t_align = M3.targetMap.CG_Align[t.ordinal()];
        long best_align;
        long best_size;
        CGType best_type;
        forceStacked();  // materialize the value to be stored

        if (s == t_size && Math.floorMod(base_align + o, t_align) == 0) {
            // a simple aligned store
            cg.store(v, asBytes(o), StackType[t.ordinal()], t);
        } else if (t_size < s) {
            err("store size too large");
            cg.store(v, asBytes(o), StackType[t.ordinal()], t);
        } else if ((t == M3.target.Word.cg_type) || (t == M3.target.Integer.cg_type)
                || (t == M3.target.Long.cg_type) || (t == M3.target.Longint.cg_type)) {
            best_type  = mayFindIntType(t, s, o, base_align);
            if (best_type != CGType.Void) {
                best_size  = M3.targetMap.CG_Size[best_type.ordinal()];
                best_align = M3.targetMap.CG_Align[best_type.ordinal()];
                t_align = Math.floorMod(base_align + o, best_align);
                if ((s == best_size) && (t_align == 0)) {
                    // this is a simple partial word store
                    cg.store(v, asBytes(o), StackType[t.ordinal()], best_type);
                } else {
                    // unaligned, partial store
                    cg.load(v, asBytes(o - t_align), best_type, StackType[t.ordinal()]);
                    cg.swap(t, t);
                    if (M3.target.endian == Target.Endian.Little) {
                        cg.insert_mn(StackType[t.ordinal()], t_align, s);
                    } else {
                        cg.insert_mn(StackType[t.ordinal()], best_size - t_align - s, s);
                    }
                    cg.store(v, asBytes(o - t_align), StackType[t.ordinal()], best_type);
                }
            } else if (Math.floorMod(base_align, Target.Byte) == 0) {
                load_addr_of(v, o, base_align);
                swap();
                storeIndirectStraddling(t, o, s);
            } else {
                err("unaligned word-straddling store, type=" + Target.TypeNames[t.ordinal()]
                    + "  size/offset/align=" + Long.toString(s) + "/" + Long.toString(o) + "/" + Long.toString(base_align));
                cg.store(v, toBytes(o), M3.target.Integer.cg_type, t);
            }
        } else {
            err("unaligned partial-word store, type=" + Target.TypeNames[t.ordinal()]
                + "  size/offset/align=" + Long.toString(s) + "/" + Long.toString(o) + "/" + Long.toString(base_align));
            cg.store(v, toBytes(o), M3.target.Integer.cg_type, t);
        }
        sPop(1, "Store");
    }

    /** == Store (v, o, t.size, t.align, t) */
    public static void store_int(CGType t, M3CG.Var v, long o) {
        store(v, o, M3.targetMap.CG_Size[t.ordinal()], M3.targetMap.CG_Align[t.ordinal()], t);
    }

    public static void store_int(CGType t, M3CG.Var v) {
        store_int(t, v, 0);
    }

    /** == Store (v, o, Target.Address.size, Target.Address.align, Type.Addr) */
    public static void store_addr(M3CG.Var v, long o) {
        store(v, o, M3.target.Address.size, M3.target.Address.align, CGType.Addr);
    }

    public static void store_addr(M3CG.Var v) {
        store_addr(v, 0);
    }

    /**
     * Force s0.A to have kind IN {Stacked, Absolute, Pointer}, while
     * incrementing its value by addedOffset.
     */
    private static void forceAddr2SAP(long addedOffset) {
        ValRec x = stack[(int) sCheck(1, "ForceAddr2SAP")];
        AssertError.check(x.type == CGType.Addr, "CG");
        AssertError.check(x.base_align == M3.target.Address.align, "CG");
        switch (x.kind) {
            case Stacked -> {
                AssertError.check(x.offset == 0, "CG");
                if (addedOffset != 0) {
                    x.kind = VKind.Pointer;
                    x.base_align = M3.target.Address.align;
                    x.offset = addedOffset;
                    x.addr_align = GCD(x.addr_align, addedOffset);
                }
            }

            case Pointer, Absolute -> {
                x.offset += addedOffset;
                x.addr_align = GCD(x.addr_align, addedOffset);
            }

            case Direct -> {
                AssertError.check(x.bits == null, "CG");
                if (addedOffset == 0) {
                    cg.load(x.base, asBytes(x.offset), CGType.Addr, CGType.Addr);
                    x.kind = VKind.Stacked;
                    x.base_align = M3.target.Address.align;
                    // x.base_value_align := x.addr_align; Does not change.
                    x.offset = 0;
                } else {
                    cg.load(x.base, asBytes(x.offset), CGType.Addr, CGType.Addr);
                    x.kind = VKind.Pointer;
                    x.base_align = M3.target.Address.align;
                    // x.base_value_align := x.addr_align; Does not change.
                    x.offset = addedOffset;
                    x.addr_align = GCD(x.addr_align, addedOffset);
                }
            }

            case Indirect -> {
                cg.load(x.base, 0, CGType.Addr, CGType.Addr);
                x.kind = VKind.Pointer;
                x.base_align = M3.target.Address.align;
                x.offset += addedOffset;
                // x.base_value_align := x.addr_align; Does not change.
                x.addr_align = GCD(x.addr_align, addedOffset);
            }
            default -> {
            }
        }
    }

    /** Mem [s1.A + o : s] := s0.t ; pop (2) */
    public static void store_indirect(CGType t, long addedOffset, long s) {
        long t_size = M3.targetMap.CG_Size[t.ordinal()];
        long t_align = M3.targetMap.CG_Align[t.ordinal()];
        long best_align;
        long best_size;
        CGType best_type;
        long addr_align;
        Val tmp;
        long oddStaticBitCt;
        M3CG.Var save_bits = null;
        M3CG.Var oddRTBits;
        boolean save_temp_bits = false;
        swap();
        forceAddr2SAP(addedOffset);
        // Address wherein to store is now Stacked, Absolute, or Pointer.
        swap();
        forceStacked(); // the value to be stored

        ValRec x = stack[(int) sCheck(2, "Store_indirect-addr")];

        if (s == t_size && Math.floorMod(x.addr_align, t_align) == 0) {
            // A full t_size'd and t_align'ed store.
            simpleIndirectStore(x, t, t);
        } else if (s == t_size
                && Math.floorMod(x.addr_align, Target.Byte) == 0
                && M3.target.Allow_packed_byte_aligned) {
            // A full t_size'd and byte-aligned store, supported by the processor.
            //   This is used by structures containing packed components.
            simpleIndirectStore(x, t, t);

        } else if (s > t_size) {
            err("In Store_indirect, memory size exceeds stack size.");
            simpleIndirectStore(x, t, t);

        } else if ((t == M3.target.Word.cg_type) || (t == M3.target.Integer.cg_type)
                || (t == M3.target.Long.cg_type) || (t == M3.target.Longint.cg_type)) {
            best_type = mayFindIntType(t, s, x.offset, x.base_value_align);
            if (best_type != CGType.Void) {
                best_size = M3.targetMap.CG_Size[best_type.ordinal()];
                best_align = M3.targetMap.CG_Align[best_type.ordinal()];
                oddStaticBitCt = Math.floorMod(x.offset, best_align);
                if ((oddStaticBitCt == 0) && (s == best_size) && (x.bits == null)) {
                    // A full best_size'd and best_align'ed store.
                    simpleIndirectStore(x, t, best_type);
                } else if ((oddStaticBitCt == 0) && (x.bits == null)) {
                    // A best_align'ed but only partial best_size'd store.
                    swap(); // Address on top
                    tmp = pop();
                    push(tmp);
                    forceAddr2SAP(0);
                    simpleIndirectLoad( // Fetched word on top.
                        stack[(int) sCheck(1, "Store_indirect-1")], best_type, 1);

                    // stuff the bits
                    forceStacked();
                    swap();
                    forceStacked();
                    if (M3.target.endian == Target.Endian.Little) {
                        cg.insert_mn(StackType[t.ordinal()], 0, s);
                    } else {
                        cg.insert_mn(StackType[t.ordinal()], best_size - s, s);
                    }
                    sPop(1, "Store_indirect #1");
                    push(tmp);
                    forceAddr2SAP(0);
                    swap(); // Value on top.
                    simpleIndirectStore(x, t, best_type);
                    free(tmp);
                } else if (x.bits == null) {
                    // Unaligned, partial best_size'd store.
                    x.offset -= oddStaticBitCt;
                    swap(); // Address on top
                    tmp = pop();
                    push(tmp);
                    forceAddr2SAP(0);
                    simpleIndirectLoad( // Fetched word on top.
                        stack[(int) sCheck(1, "Store_indirect-2")], best_type, 1);

                    // stuff the bits
                    forceStacked();
                    swap();
                    forceStacked();
                    if (M3.target.endian == Target.Endian.Little) {
                        cg.insert_mn(StackType[t.ordinal()], oddStaticBitCt, s);
                    } else {
                        cg.insert_mn(StackType[t.ordinal()], best_size - oddStaticBitCt - s, s);
                    }
                    sPop(1, "Store_indirect #2");

                    // finally, store the result
                    push(tmp);
                    forceAddr2SAP(0);
                    swap(); // Stuffed word on top, address below.
                    simpleIndirectStore(x, t, best_type);
                    free(tmp);
                } else { // bits # NIL
                    // Unaligned, partial best_size'd store with RT variable offset.
                    addr_align = Math.min(x.addr_align, t_size);
                    if (best_size < addr_align) {
                        // make sure we load and store the largest possible aligned value,
                        //   because we can't tell how far the variable bit-offset
                        //   will take us.
                        best_type
                            = mustFindIntType(t, Math.max(s, addr_align), Math.floorMod(x.offset, addr_align),
                                              x.base_value_align);
                        best_size  = M3.targetMap.CG_Size[best_type.ordinal()];
                        best_align = M3.targetMap.CG_Align[best_type.ordinal()];
                        oddStaticBitCt = Math.floorMod(x.offset, best_align);
                    }
                    if (x.base_value_align < best_align) {
                        err("unaligned fetch type in Store_indirect");
                    }

                    swap(); // Address on top
                    oddRTBits = reduceBits(best_align);
                    swap(); // Value back on top.

                    // Hide the unreduced runtime bit offset.
                    save_bits = x.bits;
                    x.bits = null;
                    save_temp_bits = x.temp_bits;
                    x.temp_bits = false;

                    // generate the aligned load
                    x.offset -= oddStaticBitCt;
                    swap(); // Address on top
                    tmp = pop();
                    push(tmp);
                    forceAddr2SAP(0);
                    simpleIndirectLoad(
                        stack[(int) sCheck(1, "Store_indirect-3")], best_type, addr_align);
                    // ^Fetched word on top, to-store value below.

                    // Code to compute the full bit offset.
                    forceStacked();
                    swap();
                    // Value on top, fetched word below.
                    forceStacked();
                    if (M3.target.endian == Target.Endian.Little) {
                        cg.load(oddRTBits, 0, M3.target.Integer.cg_type, M3.target.Integer.cg_type);
                        if (oddStaticBitCt != 0) {
                            cg_load_intt(oddStaticBitCt);
                            cg.add(M3.target.Integer.cg_type);
                        }
                    } else { // big endian
                        cg_load_intt(best_size - oddStaticBitCt - s);
                        cg.load(oddRTBits, 0, M3.target.Integer.cg_type, M3.target.Integer.cg_type);
                        cg.subtract(M3.target.Integer.cg_type);
                    }
                    // RT bit count, value, fetched word.

                    // stuff the bits
                    cg.insert_n(StackType[t.ordinal()], s);
                    sPop(1, "Store_indirect #3");

                    // finally, store the result
                    push(tmp);
                    forceAddr2SAP(0);
                    swap(); // Stuffed word on top, address below.
                    simpleIndirectStore(x, t, best_type);

                    free(tmp);
                    free_temp(oddRTBits);
                    if (save_temp_bits) {
                        free_temp(save_bits);
                    }
                }
            } else if (Math.floorMod(x.addr_align, Target.Byte) == 0) {
                storeIndirectStraddling(t, addedOffset, s);
                return; // ^This pops the operands.
            } else {
                err("unaligned word-straddling store_indirect, type=" + Target.TypeNames[t.ordinal()]
                    + "  s/a=" + Long.toString(s) + "/" + Long.toString(x.addr_align));
                simpleIndirectStore(x, t, t);
            }
        } else {
            err("unaligned partial-word store_indirect, type=" + Target.TypeNames[t.ordinal()]
                + "  s/a=" + Long.toString(s) + "/" + Long.toString(x.addr_align));
            simpleIndirectStore(x, t, t);
        }

        sPop(2, "Store_indirect");
    }

    /**
     * PRE: x.kind IN {Stacked, Absolute, Pointer}.
     * Store full t-size'd and t-align'ed into u.
     */
    private static void simpleIndirectStore(ValRec x, CGType t, CGType u) {
        switch (x.kind) {
            case Absolute -> cg.store(x.base, asBytes(x.offset), t, u);
            case Pointer, Stacked -> cg.store_indirect(asBytes(x.offset), t, u);
            default -> { // ??
                errI(x.kind.ordinal(), "bad VKind in SimpleIndirectStore");
                cg.store_indirect(asBytes(x.offset), t, u);
            }
        }
    }

    /**
     * Mem [s1.A + o : s] := s0.t ; pop (2)
     * PRE: (s0.A + o) is a byte multiple.
     * PRE: s &lt;= Target.Word.size.
     * i.e., the memory field starts on a byte boundary but may end on
     * any bit and may straddle one word boundary.
     */
    private static void storeIndirectStraddling(CGType t, long o, long s, boolean PleaseZextField) {
        M3CG.Var field, byteAddr, word0Addr, bitOffset, word1Mask;
        int[] fieldMask = TInt.newInt();
        // Port: byteOffsetMask is 0 if the word size is neither 32 nor 64.
        long byteOffsetMask = 0, wordAddrMask;
        long altLab = next_label();
        if (M3.target.Word.size == Target.Word32.size) {
            byteOffsetMask = 0b11;
        } else if (M3.target.Word.size == Target.Word64.size) {
            byteOffsetMask = 0b111;
        }
        wordAddrMask = ~byteOffsetMask;

        cg.comment("StoreIndirectStraddling:");
        // This uses a mix of cg.* calls, which manipulate only the M3CG
        //   stack; SPush and SPop, which manipulate only the CG stack, and
        //   CG calls, which manipulate them both, keeping them consistent.
        //   This can get very confusing.  The comments show the contents of
        //   both stacks, CG first.  Items on the M3CG stack in parentheses
        //   are conditionally there, and can be put there unconditionally
        //   by a call on Force, when at the TOS.
        // On CG stack: word-to-store, AKA ws, and starting byte address, AKA Ba.
        // Initially:                             CG stack   ; M3CG stack
        //                                        --------     ----------
                                               // ws.I Ba.A  ; (ws.I) (Ba.A)
        forceStacked();                        // ws.I Ba.A  ; ws.I (Ba.A)
        cg.loophole(M3.target.Integer.cg_type, M3.target.Word.cg_type);
                                               // ws.W Ba.A  ; ws.W (Ba.A)

        // If requested, mask out garbage bits to the field's left, AKA f.
        //   This is a static mask.
        field = cg.declare_temp(M3.target.Word.bytes,
                                M3.targetMap.CG_Align_bytes[M3.target.Word.cg_type.ordinal()],
                                M3.target.Word.cg_type,
                                /* in_memory := */ false);
        TWord.nBitsOnRight(s, /*OUT*/ fieldMask);
        if (PleaseZextField) {
            cg.load_integer(M3.target.Word.cg_type, fieldMask);
                                               // ws.W Ba.A  ; mask.W f.W (Ba.A)
            cg.and(M3.target.Word.cg_type);    // ws.W Ba.A  ; f.W (Ba.A)
        }
        cg.store(field, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               // ws.W Ba.A  ; (Ba)
        sPop(1, "");                           // Ba.A       ; (Ba)

        // Save the byte address, with offset added, in temp byteAddr, AKA Bao.
        forceStacked();                        // Ba.A       ; Ba.A
        if (o != 0) {
            cg.add_offset(asBytes(o));         // Ba+o.A     ; Ba+o.A
        }
        byteAddr = cg.declare_temp(M3.target.Address.bytes,
                                   M3.targetMap.CG_Align_bytes[CGType.Addr.ordinal()],
                                   CGType.Addr,
                                   /* in_memory := */ false);
        cg.store(byteAddr, 0, CGType.Addr, CGType.Addr);
                                               // Ba+o.A     ;
        sPop(1, "");                           //            ;

        if (M3.target.endian == Target.Endian.Little) {
            // Compute and save address of full memory word zero in temp word0Addr,
            //   AKA w0a.
            word0Addr = cg.declare_temp(M3.target.Address.bytes,
                                        M3.targetMap.CG_Align_bytes[CGType.Addr.ordinal()],
                                        CGType.Addr,
                                        /* in_memory := */ false);
            cg.load(byteAddr, 0, CGType.Addr, CGType.Addr);
                                               //            ; Ba+o.A
            cg.loophole(CGType.Addr, M3.target.Word.cg_type);
                                               //            ; Ba+o.W
            cg_load_word(wordAddrMask); // AKA wam
                                               //            ; wam.W Ba+o.W
            cg.and(M3.target.Word.cg_type);    //            ; w0a.W
            cg.loophole(M3.target.Word.cg_type, CGType.Addr);
                                               //            ; w0a.A
            cg.store(word0Addr, 0, CGType.Addr, CGType.Addr);
                                               //            ;

            // Compute and save bit offset within word zero in temp bitOffset,
            //   AKA bo. Byte offset is AKA Bo.
            bitOffset = cg.declare_temp(M3.target.Word.bytes,
                                        M3.targetMap.CG_Align_bytes[M3.target.Word.cg_type.ordinal()],
                                        M3.target.Word.cg_type,
                                        /* in_memory := */ false);
            cg.load(byteAddr, 0, CGType.Addr, CGType.Addr);
                                               //            ; Ba+o.A
            cg.loophole(CGType.Addr, M3.target.Word.cg_type);
                                               //            ; Ba+o.W
            cg_load_word(byteOffsetMask);      //            ; Bom.W Ba+o.W
            cg.and(M3.target.Word.cg_type);    //            ; Bo.W
            cg_load_word(Log2OfByte);          //            ; shiftct.W, Bo.W
            cg.shift_left(M3.target.Word.cg_type); //        ; bo.W
            cg.store(bitOffset, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ;

            // Load word zero and mask out the to-be-stored-into bits, AKA w0m.
            cg.load(word0Addr, 0, CGType.Addr, CGType.Addr);
                                               //            ; w0a.A
            // ^Keep this underneath, for future re-store of word-zero.
            cg.load(word0Addr, 0, CGType.Addr, CGType.Addr);
                                               //            ; w0a.A w0a.A
            cg.load_indirect(0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ; w0.W w0a.A
            cg.load_integer(M3.target.Word.cg_type, fieldMask);
                                               //            ; fm.W w0.W w0a.A
            cg.load(bitOffset, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ; bo.W fm.W w0.W w0a.A
            cg.shift_left(M3.target.Word.cg_type); //        ; fms.W w0.W w0a.A
            cg.not(M3.target.Word.cg_type);    //            ; fmsNeg.W w0.W w0a.A
            cg.and(M3.target.Word.cg_type);    //            ; w0m.W w0a.A

            // Combine components of updated word zero and re-store.
            cg.load(field, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ; f.W w0m.W w0a.A
            cg.load(bitOffset, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ; bo.W f.W w0m.W w0a.A
            cg.shift_left(M3.target.Word.cg_type); //        ; fs.W w0m.W w0a.A
            cg.or(M3.target.Word.cg_type);     //            ; w0Final.W w0a.A
            cg.store_indirect(0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ;

            // See if we are done, after storing into only word0
            cg.load(bitOffset, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ; bo.W
            cg_load_word(s);                   //            ; s.W bo.W
            cg.add(M3.target.Word.cg_type);    //            ; s+bo.W
            cg_load_word(M3.target.Word.size); //            ; sw.W s+bo.W
            cg.if_compare(M3.target.Word.cg_type, CompareOp.LE, altLab, Maybe);
                                               //            ;

            // Take care of contribution to word one.
            cg.load(word0Addr, 0, CGType.Addr, CGType.Addr);
                                               //            ; w0a.A
            // ^Keep this underneath, for future re-store of word-one.

            // Rotate original field to right-justify its contribution to word one.
            cg.load(field, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ; f.W w0a.W
            cg.load(bitOffset, 0, M3.target.Word.cg_type, M3.target.Integer.cg_type);
            // ^!!! cm3cg will crash on rotate_left or rotate_right, if the loaded
            //      type is not Integer.
                                               //            ; bo.W f.W w0a.A
            cg.rotate_left(M3.target.Word.cg_type); //       ; fr.W w0a.A

            // Compute a mask for the word-one contribution, AKA w1m.
            word1Mask = cg.declare_temp(M3.target.Word.bytes,
                                        M3.targetMap.CG_Align_bytes[M3.target.Word.cg_type.ordinal()],
                                        M3.target.Word.cg_type,
                                        /* in_memory := */ false);
            cg.load_integer(M3.target.Word.cg_type, TInt.MOne);
                                               //            ; -1.W fr.W w0a.A
            cg_load_word(2 * M3.target.Word.size - s); //    ; 2wms.W -1.W fr.W w0a.A
            cg.load(bitOffset, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ; bo.W 2wms.W -1.W fr.W w0a.A
            cg.subtract(M3.target.Integer.cg_type); //       ; shiftct.W -1.W fr.W w0a.A
            cg.shift_right(M3.target.Word.cg_type); //       ; w1m.W fr.W w0a.A
            cg.store(word1Mask, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ; fr.W w0a.A

            // Mask out garbage bits of word-one contribution of rotated field.
            cg.load(word1Mask, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ; w1m.W fr.W w0a.A
            cg.and(M3.target.Word.cg_type);    //            ; w1c.W w0a.A

            // Bit-flip word-one mask.
            cg.load(word1Mask, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ; w1m.W w1c.W w0a.A
            cg.not(M3.target.Word.cg_type);    //            ; w1mNeg.W w1c.W w0a.A


            // Mask out to-be-stored-into bits of word one.
            cg.load(word0Addr, 0, CGType.Addr, CGType.Addr);
                                               //            ; w0a.A w1mNeg.W w1c.W w0a.A
            cg.load_indirect(M3.target.Word.bytes, M3.target.Word.cg_type, M3.target.Word.cg_type);
                                               //            ; w1.W w1mNeg.W w1c.W w0a.A
            cg.and(M3.target.Word.cg_type);    //            ; w1m.W w1c.W w0a.A

            // Combine components of updated word one and re-store.
            cg.or(M3.target.Word.cg_type);     //            ; w1Final.W w0a.A
            cg.loophole(M3.target.Word.cg_type, M3.target.Integer.cg_type);
            cg.store_indirect(M3.target.Word.bytes, M3.target.Integer.cg_type, M3.target.Integer.cg_type);
                                               //            ;
            // Merge the word-zero-only path here.
            cg.set_label(altLab);
        } else { // Big endian target.
            err("Big endian StoreIndirectStraddling not implemented.");
        } // Endianness.

    }

    private static void storeIndirectStraddling(CGType t, long o, long s) {
        storeIndirectStraddling(t, o, s, true);
    }

    /*-------------------------------------------------------------- literals ---*/

    /** push ; s0.A := NIL */
    public static void load_nil() {
        sPush(CGType.Addr);
        cg.load_nil();
        ValRec x = stack[(int) sCheck(1, "Load_nil")];
        x.old_align = M3.target.Address.align;
        x.base_align = M3.target.Address.align;
        x.base_value_align = Target.Word64.align;
        x.addr_align = M3.target.Word.align;
    }

    /** push ; s0.A := x bytes */
    public static void load_byte_address(long intVal) {
        // push ; s0.A := intVal bytes
        sPush(CGType.Addr);
        cg.load_nil();
        cg.add_offset(intVal);
        ValRec x = stack[(int) sCheck(1, "Load_byte_address")];
        x.old_align = Target.Byte;
        x.base_align = M3.target.Address.align;
        x.addr_align = alignOfInt(intVal);
        x.base_value_align = x.addr_align;
    }

    /** push;  s0.I := i */
    public static void load_intt(long i) {
        int[] val = TInt.newInt();
        boolean b = TInt.fromInt(i, val);
        if (!b
                || TInt.LT(val, M3.target.Integer.min)
                || TInt.LT(M3.target.Integer.max, val)) {
            errI(i, "integer not representable");
        }
        load_integer(M3.target.Integer.cg_type, val);
    }

    /** push ; s0.t := i */
    public static void load_integer(CGType t, int[] tintVal) {
        sPush(t);
        ValRec x = stack[(int) sCheck(1, "Load_integer")];
        x.kind = VKind.Integer;
        TInt.assign(x.int_, tintVal);
        x.base_value_align = alignOfTInt(tintVal);
        x.addr_align = x.base_value_align;
    }

    /** push ; s0.t := f */
    public static void load_float(Target.Float f) {
        CGType t = M3.targetMap.Float_types[TFloat.prec(f).ordinal()].cg_type;
        sPush(t);
        ValRec x = stack[(int) sCheck(1, "Load_float")];
        x.kind  = VKind.Float;
        x.base_align = M3.targetMap.CG_Align[t.ordinal()];
        x.float_.assign(f);
    }

    /*------------------------------------------------------------ arithmetic ---*/

    // when any of these operators is passed t=Type.Word, the operator
    // does the unsigned comparison or arithmetic, but the operators
    // and the result are of type Integer

    /** s1.I32 := (s1.t op s0.t)  ; pop */
    public static void compare(CGType t, CompareOp op) {
        if (force_pair(/* commute := */ true)) {
            op = M3CG.SwappedCompare[op.ordinal()];
        }
        cg.compare(t, M3.target.Integer.cg_type, op);
        sPop(2, "Compare");
        sPush(CGType.Int32);
    }

    /** s1.t := s1.t + s0.t ; pop */
    public static void add(CGType t) {
        force_pair(/* commute := */ true);
        cg.add(t);
        sPop(2, "Add");
        sPush(t);
    }

    /** s1.t := s1.t - s0.t ; pop */
    public static void subtract(CGType t) {
        force_pair(/* commute := */ false);
        cg.subtract(t);
        sPop(2, "Subtract");
        sPush(t);
    }

    /** s1.t := s1.t * s0.t ; pop */
    public static void multiply(CGType t) {
        force_pair(/* commute := */ true);
        cg.multiply(t);
        sPop(2, "Multiply");
        sPush(t);
    }

    /** s1.t := s1.t / s0.t ; pop */
    public static void divide(CGType t) {
        force_pair(/* commute := */ false);
        cg.divide(t);
        sPop(2, "Divide");
        sPush(t);
    }

    /** s0.t := - s0.t */
    public static void negate(CGType t) {
        forceStacked();
        cg.negate(t);
        sPop(1, "Negate");
        sPush(t);
    }

    /** s0.t := ABS (s0.t) (noop on Words) */
    public static void abs(CGType t) {
        forceStacked();
        cg.abs(t);
        sPop(1, "Abs");
        sPush(t);
    }

    /** s1.t := MAX (s1.t, s0.t) ; pop */
    public static void max(CGType t) {
        force_pair(/* commute := */ true);
        cg.max(t);
        sPop(2, "Max");
        sPush(t);
    }

    /** s1.t := MIN (s1.t, s0.t) ; pop */
    public static void min(CGType t) {
        force_pair(/* commute := */ true);
        cg.min(t);
        sPop(2, "Min");
        sPush(t);
    }

    /** s0.u := op (s0.t) */
    public static void cvt_int(CGType t, CGType u, M3CG.ConvertOp op) {
        forceStacked();
        cg.cvt_int(t, u, op);
        sPop(1, "Cvt_int");
        sPush(u);
    }

    /** s0.u := FLOAT (s0.t, u) */
    public static void cvt_float(CGType t, CGType u) {
        forceStacked();
        cg.cvt_float(t, u);
        sPop(1, "Cvt_float");
        sPush(u);
    }

    /** s1.t := s1.t DIV s0.t;pop */
    public static void div(CGType t, Sign a, Sign b) {
        force_pair(/* commute := */ false);
        cg.div(t, a, b);
        sPop(2, "Div");
        sPush(t);
    }

    /** s1.t := s1.t MOD s0.t;pop */
    public static void mod(CGType t, Sign a, Sign b) {
        force_pair(/* commute := */ false);
        cg.mod(t, a, b);
        sPop(2, "Mod");
        sPush(t);
    }

    /*------------------------------------------------------------------ sets ---*/

    // Set sizes are in bits.  Sets not larger than an integer are
    // represented on the stack as integers.  Other "large" sets are
    // represented by their addresses.  The strict inequality operators
    // (lt, gt) are *not* supported for small sets, the front-end
    // must synthesize them from union, difference, eq, etc.

    /** s2.B := s1.B + s0.B ; pop(3) */
    public static void set_union(long s) {
        force_pair(/* commute := */ true);
        if (s <= M3.target.Integer.size) {
            cg.or(M3.target.Integer.cg_type);
            sPop(1, "Set_union");
        } else {
            cg.set_union(asBytes(s));
            sPop(3, "Set_union");
        }
    }

    /** s2.B := s1.B - s0.B ; pop(3) */
    public static void set_difference(long s) {
        force_pair(/* commute := */ false);
        if (s <= M3.target.Integer.size) {
            cg.not(M3.target.Integer.cg_type);
            cg.and(M3.target.Integer.cg_type);
            sPop(1, "Set_diff");
        } else {
            cg.set_difference(asBytes(s));
            sPop(3, "Set_diff");
        }
    }

    /** s2.B := s1.B * s0.B ; pop(3) */
    public static void set_intersection(long s) {
        force_pair(/* commute := */ true);
        if (s <= M3.target.Integer.size) {
            cg.and(M3.target.Integer.cg_type);
            sPop(1, "Set_inter");
        } else {
            cg.set_intersection(asBytes(s));
            sPop(3, "Set_inter");
        }
    }

    /** s2.B := s1.B / s0.B ; pop(3) */
    public static void set_sym_difference(long s) {
        force_pair(/* commute := */ true);
        if (s <= M3.target.Integer.size) {
            cg.xor(M3.target.Integer.cg_type);
            sPop(1, "Set_symd");
        } else {
            cg.set_sym_difference(asBytes(s));
            sPop(3, "Set_symd");
        }
    }

    /** s1.I32 := (s0.I IN s1.B); pop */
    public static void set_member(long s) {
        force_pair(/* commute := */ false);
        if (s <= M3.target.Integer.size) {
            cg.load_integer(M3.target.Integer.cg_type, TInt.One);
            cg.swap(M3.target.Integer.cg_type, M3.target.Integer.cg_type);
            cg.shift_left(M3.target.Integer.cg_type);
            cg.and(M3.target.Integer.cg_type);
            cg.load_integer(M3.target.Integer.cg_type, TInt.Zero);
            cg.compare(M3.target.Word.cg_type, M3.target.Integer.cg_type, CompareOp.NE);
        } else {
            cg.set_member(asBytes(s), M3.target.Integer.cg_type);
        }
        sPop(2, "Set_member");
        sPush(M3.target.Integer.cg_type);
    }

    /** s1.I := (s1.B op s0.B); pop */
    public static void set_compare(long s, CompareOp op) {
        Val a = null;
        Val b = null;

        // a op b => BOOLEAN

        // Comparison is commutative in that the comparison can be reversed
        //   if it is profitable to reverse the parameter order.

        if (force_pair(/* commute := */ true)) {
            op = M3CG.SwappedCompare[op.ordinal()];
        }

        if (s <= M3.target.Integer.size) {

            // The set fits in an integer, so handle things inline with integer operations
            //   NOTE that for the sake of code size, we should perhaps implement these with functions.

            if ((op == CompareOp.EQ) || (op == CompareOp.NE)) {

                compare(M3.target.Word.cg_type, op);

            } else {

                // Set a is less than or equal to set b, if all of set a's members are in set b.
                //   (a <= b) = ((a & b) = a)
                //   (a <  b) = (a <= b AND a # b)
                //   (b >  a) = (a < b)

                if ((op == CompareOp.GT) || (op == CompareOp.GE)) {
                    a = pop();
                    b = pop();
                } else {
                    b = pop();
                    a = pop();
                }

                push(a);
                push(b);
                and(M3.target.Word.cg_type);

                push(a);
                compare(M3.target.Word.cg_type, CompareOp.EQ);

                // NOTE that short circuiting for < and > is probably desirable, if one
                //   knows how to set up the labels and branches.

                if ((op == CompareOp.LT) || (op == CompareOp.GT)) {
                    push(b);
                    push(a);
                    compare(M3.target.Word.cg_type, CompareOp.EQ);
                    and(M3.target.Integer.cg_type);
                }

                free(a);
                free(b);
            }
        } else {
            cg.set_compare(asBytes(s), op, M3.target.Integer.cg_type);
            sPop(2, "Set_compare");
            sPush(CGType.Int32);
        }
    }

    /**
     * IF s &lt;= target word,
     *   s2.I := s2.I || {S1.I..S0.I} pop(3)
     *    --- S2.A must be forced
     * ELSE
     *   s2.A[s1.I..s0.I] := 1...1; pop(3)
     *    --- S2.A must be forced
     */
    public static void set_range(long s) {
        force_pair(/* commute := */ false);
        if (s <= M3.target.Word.size) {
            // Initial value on CG stack, in s2.  Final value will be left in s0.
            // given x, a, b:  compute  x || {a..b}

            cg.load_integer(M3.target.Integer.cg_type, TInt.MOne);
            // -1 = 16_ffffff = {0..N}
            cg.swap(M3.target.Integer.cg_type, M3.target.Integer.cg_type);
            cg_load_intt(M3.target.Integer.size - 1);
            cg.swap(M3.target.Integer.cg_type, M3.target.Integer.cg_type);
            cg.subtract(M3.target.Integer.cg_type);
            cg.shift_right(M3.target.Integer.cg_type);                  // x, a, {0..b}

            cg.swap(M3.target.Integer.cg_type, M3.target.Integer.cg_type); // x, {0..b}, a

            cg.load_integer(M3.target.Integer.cg_type, TInt.MOne);
            cg.swap(M3.target.Integer.cg_type, M3.target.Integer.cg_type);
            cg.shift_left(M3.target.Integer.cg_type);               // x, {0..b}, {a..N}

            cg.and(M3.target.Integer.cg_type);                      // x, {a..b}
            cg.or(M3.target.Integer.cg_type); // s0 is the value       x || {a..b}

            sPop(3, "Set_range-a");
            sPush(M3.target.Integer.cg_type);
        } else { // s2 is the *address* of the value.
            // CG stack s2 is the *address* of the value, which will be updated in
            //   memory, leaving nothing on either stack.
            cg.set_range(asBytes(s), M3.target.Integer.cg_type);
            sPop(3, "Set_range-b");
        }
    }

    /**
     * IF s &lt;= target word,
     *   s1.I := s1.I || Word.Shift (1, s0.I); pop(2)
     * ELSE
     *   s1.A [s0.I] := 1; pop(2)
     */
    public static void set_singleton(long s) {
        force_pair(/* commute := */ false);                       // cg stack:
        if (s <= M3.target.Integer.size) { // x is the value         x, a
            // Initial value is on CG stack, in s2.  Final value will be left in s0.
            cg.load_integer(M3.target.Integer.cg_type, TInt.One);       // x, a, 1
            cg.swap(M3.target.Integer.cg_type, M3.target.Integer.cg_type); // x, 1 , a
            cg.shift_left(M3.target.Integer.cg_type);                   // x, 10...0
            cg.or(M3.target.Integer.cg_type); // y is the result.          y
            sPop(2, "Set_singleton-a");
            sPush(M3.target.Integer.cg_type);
        } else { // mem is the *addr* of the value (and result)         mem, a
            // CG stack s1 is the *address* of the value, which will be updated in
            //   memory, leaving nothing on either stack.
            cg.set_singleton(asBytes(s), M3.target.Integer.cg_type);
            sPop(2, "Set_singleton-b");
        }
    }

    /*------------------------------------------ Word.T/Long.T bit operations ---*/

    /** s0.t := Word.Not (s0.t) */
    public static void not(CGType t) {
        forceStacked();
        cg.not(t);
        sPop(1, "Not");
        sPush(t);
    }

    /** s1.t := Word.And (s1.t, s0.t) ; pop */
    public static void and(CGType t) {
        force_pair(/* commute := */ true);
        cg.and(t);
        sPop(2, "And");
        sPush(t);
    }

    /** s1.t := Word.Or  (s1.t, s0.t) ; pop */
    public static void or(CGType t) {
        force_pair(/* commute := */ true);
        cg.or(t);
        sPop(2, "Or");
        sPush(t);
    }

    /** s1.t := Word.Xor (s1.t, s0.t) ; pop */
    public static void xor(CGType t) {
        force_pair(/* commute := */ true);
        cg.xor(t);
        sPop(2, "Xor");
        sPush(t);
    }

    /** s1.t := Word.Shift  (s1.t, s0.I) ; pop */
    public static void shift(CGType t) {
        force_pair(/* commute := */ false);
        cg.shift(t);
        sPop(2, "Shift");
        sPush(t);
    }

    /** s1.t := Word.Shift  (s1.t, s0.I) ; pop */
    public static void shift_left(CGType t) {
        force_pair(/* commute := */ false);
        cg.shift_left(t);
        sPop(2, "Shift_left");
        sPush(t);
    }

    /** s1.t := Word.Shift  (s1.t, -s0.I) ; pop */
    public static void shift_right(CGType t) {
        force_pair(/* commute := */ false);
        cg.shift_right(t);
        sPop(2, "Shift_right");
        sPush(t);
    }

    /** s1.t := Word.Rotate (s1.t, s0.I) ; pop */
    public static void rotate(CGType t) {
        force_pair(/* commute := */ false);
        cg.rotate(t);
        sPop(2, "Rotate");
        sPush(t);
    }

    /** s1.t := Word.Rotate (s1.t, s0.I) ; pop */
    public static void rotate_left(CGType t) {
        force_pair(/* commute := */ false);
        cg.rotate_left(t);
        sPop(2, "Rotate_left");
        sPush(t);
    }

    /** s1.t := Word.Rotate (s1.t, -s0.I) ; pop */
    public static void rotate_right(CGType t) {
        force_pair(/* commute := */ false);
        cg.rotate_right(t);
        sPop(2, "Rotate_right");
        sPush(t);
    }

    /**
     * s2.t := Word.Extract(s2.t, s1.I, s0.I);
     * IF sign THEN SignExtend s2 ; pop(2)
     */
    public static void extract(CGType t, boolean sign) {
        force_pair(/* commute := */ false);
        cg.extract(t, sign);
        sPop(3, "Extract");
        sPush(t);
    }

    /**
     * s1.t := Word.Extract(s1.t, s0.I, n);
     * IF sign THEN SignExtend s1; pop(1)
     */
    public static void extract_n(CGType t, boolean sign, long n) {
        force_pair(/* commute := */ false);
        cg.extract_n(t, sign, n);
        sPop(2, "Extract_n");
        sPush(t);
    }

    /**
     * s0.t := Word.Extract(s0.t, m, n);
     * IF sign THEN SignExtend s0
     */
    public static void extract_mn(CGType t, boolean sign, long m, long n) {
        forceStacked();
        cg.extract_mn(t, sign, m, n);
        sPop(1, "Extract_mn");
        sPush(t);
    }

    /** s3.t := Word.Insert (s3.t, s2.t, s1.I, s0.I) ; pop(3) */
    public static void insert(CGType t) {
        force_pair(/* commute := */ false);
        cg.insert(t);
        sPop(4, "Insert");
        sPush(t);
    }

    /** s2.t := Word.Insert (s2.t, s1.t, s0.I, n); pop(2) */
    public static void insert_n(CGType t, long n) {
        force_pair(/* commute := */ false);
        cg.insert_n(t, n);
        sPop(3, "Insert_n");
        sPush(t);
    }

    /** s1.t := Word.Insert (s1.t, s0.t, m, n); pop(1) */
    public static void insert_mn(CGType t, long m, long n) {
        force_pair(/* commute := */ false);
        cg.insert_mn(t, m, n);
        sPop(2, "Insert_mn");
        sPush(t);
    }

    /*------------------------------------------------ misc. stack/memory ops ---*/

    /** tmp := s1; s1 := s0; s0 := tmp */
    public static void swap() {
        ValRec tmp = new ValRec();
        ValRec xa = stack[(int) sCheck(2, "Swap-a")];
        ValRec xb = stack[(int) sCheck(1, "Swap-b")];

        // exchange the underlying values
        if (((xa.kind == VKind.Stacked) || (xa.kind == VKind.Pointer))
                && ((xb.kind == VKind.Stacked) || (xb.kind == VKind.Pointer))) {
            // Both values are on the M3CG stack => must swap them there.
            cg.swap(xa.type, xb.type);
        }

        // exchange the local copies
        tmp.assign(xa);
        xa.assign(xb);
        xb.assign(tmp);
    }

    /** pop(1) discard s0, not its side effects */
    public static void discard(CGType t) {
        sPop(1, "Discard");
        ValRec x = stack[(int) sCheck(0, "Pop")];
        if ((x.kind == VKind.Stacked) || (x.kind == VKind.Pointer)) {
            cg.pop(t);
        }
        release_temps(x);
    }

    /** {16_00, 16_FF, ..} */
    private static final int[] GMaskToShift = {0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};

    /**
     * Mem[s2.A:s0.I*s] := Mem[s1.A:s0.I*s]; pop(3)
     * -- s2.A &amp; s1.A must have byte value-alignment and must have been
     * ForceStacked by caller.  Copied data need not end on a byte boundary.
     * 'overlap' is true if the source and destination may partially overlap
     * (ie. you need memmove, not just memcpy).
     */
    public static void copy_n(long s, boolean overlap) {
        CGType t;
        long z;
        long a;
        long lhs_align, rhs_align;
        Val fromAddr, toAddr, ec, toByteAddr, byteCt, extractMask;
        long rshift, lshift, ecmask;

        rhs_align = stack_addr_align(2);
        lhs_align = stack_addr_align(3);
        if (Math.floorMod(lhs_align, Target.Byte) != 0) {
            errI(lhs_align, "non-byte-aligned LHS in Copy_n");
        }
        if (Math.floorMod(rhs_align, Target.Byte) != 0) {
            errI(rhs_align, "non-byte-aligned RHS in Copy_n");
        }
        a = Math.min(rhs_align, lhs_align);
        // This uses a mix of cg.* calls, which only alter the M3CG stack,
        //   and CG calls, which manipulate the CG stack and M3CG stack,
        //   keeping them consistent.  This gets confusing.  The comments
        //   show the contents of both stacks, CG first.  Items on the M3CG
        //   stack in parentheses are conditionally there, and can be put
        //   there unconditionally by a Force. call.  Growth is leftward.
        // Subscript AKA ec, from-address AKA fa, to-address AKA ta.
        //                                          CG stack   ; M3CG stack
        //                                          --------     ----------
                                                 // ec, fa, ta ; (ec.I), fa.A, ta.A
        forceStacked();                          // ec, fa, ta ; ec, fa, ta

        if (s == Target.Byte) {
            // Elements are exactly byte-sized.
            t = alignedType(s, Target.Byte);
            AssertError.check(M3.targetMap.CG_Size[t.ordinal()] == Target.Byte, "CG");
            cg.copy_n(M3.target.Word.cg_type, t, overlap); // ec, fa, ta ;
            sPop(3, "Copy_n, exactly bytes");    //            ;
        } else if (s < Target.Byte) {
            // Partial-byte elements.  Elements are 1, 2, or 4 bits.
            cg.comment("Copy_n, partial-byte elements:");
            if (Math.floorMod(Target.Byte, s) != 0) {
                errI(s, "CM3 restriction: copy bitsize must be a power of 2 or multiple of 8");
            }
            t = Target.Word8.cg_type;
            AssertError.check(M3.targetMap.CG_Size[t.ordinal()] == Target.Byte, "CG");

            switch ((int) s) {
                case 1 -> { rshift = 3; ecmask = 0b111; lshift = 0; }
                case 2 -> { rshift = 2; ecmask = 0b11;  lshift = 1; }
                case 4 -> { rshift = 1; ecmask = 0b1;   lshift = 2; }
                default -> throw new AssertError("CG");
            }
                                                 // ec, fa, ta ; ec, fa, (ta)
            // Compute number of whole bytes to copy.
            ec = pop();                          // fa, ta     ; fa, (ta)
            fromAddr = pop(); // Addr.           // ta         ; (ta)
            toAddr = pop(); // Addr.             //            ;
            push(ec);                            // ec         ; (ec)
            cg_load_word(rshift);                // ec         ; rs (ec)
            sPush(M3.target.Word.cg_type);       // rs, ec     ; rs (ec)
            shift_right(M3.target.Word.cg_type); // Whole byte count, AKA Bc.
                                                 // Bc         ; Bc
            byteCt = pop(); // Whole byte count, Word.
                                                 //            ;

            // Copy whole bytes.
            push(toAddr);
            forceStacked();                      // ta         ; ta
            push(fromAddr);
            forceStacked();                      // fa, ta     ; fa, ta
            push(byteCt);
            forceStacked();                      // Bc, fa, ta ; Bc, fa, ta
            cg.copy_n(M3.target.Integer.cg_type, Target.Word8.cg_type, overlap);
                                                 // Bc, fa, ta ;
            sPop(3, "bitpacked Copy_n #1");      //            ;

            // Fetch leftover byte of from-array.
            push(fromAddr);
            forceStacked();                      // fa         ; fa
            push(byteCt);
            forceStacked();                      // Bc, fa     ; Bc, fa
            cg.index_address(M3.target.Word.cg_type, 1);
            // Address of leftover from byte, AKA fBa.
                                                 // Bc, fa     ; fBa
            sPop(2, "bitpacked Copy_n #2");      //            ; fBa
            sPush(CGType.Addr);                  // fBa        ; fBa
            load_indirect(M3.target.Word.cg_type, 0, Target.Byte);
            forceStacked(); // TOS: Leftover from-byte, AKA fB.
                                                 // fB         ; fB

            // Compute bit mask for extracting elements.
            cg.load_integer(M3.target.Word.cg_type, GMaskToShift);
                                                 // fB         ; ms, fB
            push(ec);
            forceStacked();                      // ec, fB     ; ec, ms, fB
            cg_load_word(ecmask);                // ec, fB     ; ecm, ec, ms, fB
            cg.and(M3.target.Word.cg_type); // subscript w/in leftover byte, AKA swb
                                                 // ec, fB     ; swb, ms, fB
            if (lshift > 0) {
                cg_load_word(lshift);            // ec, fB     ; ls, swb, ms, fB
                cg.shift_left(M3.target.Word.cg_type); // Bit count to be copied, AKA bc.
                                                 // ec, fB     ; bc, ms, fB
            } else { // bc = swb, thus:          // ec, fB     ; bc, ms, fB
            }
            if (M3.target.endian == Target.Endian.Little) {
                cg.loophole(M3.target.Word.cg_type, M3.target.Integer.cg_type);
                cg.rotate_left(M3.target.Word.cg_type); // Extract mask, AKA em.
            } else {
                cg.shift_right(M3.target.Word.cg_type); // em
            }                                    // ec, fB     ; em, fB
            sPush(M3.target.Word.cg_type);       // em, ec, fB ; em, fB
            extractMask = pop(); // Word.        // ec, fB     ; fB

            // Extract leftover from-bits.
            push(extractMask);
            forceStacked();                      // em, ec, fB ; em, fB
            cg.and(M3.target.Word.cg_type);
            // from-bits in LSB, possible garbage bits higher, AKA fb.
                                                 // em, ec, fB ; fb
            sPop(3, "bitpacked Copy_n #3");      //            ; fb

            // Fetch leftover to-byte.
            push(toAddr);
            forceStacked();                      // ta         ; ta, fb
            push(byteCt);
            forceStacked();                      // Bc, ta     ; Bc, ta, fb
            cg.index_address(M3.target.Word.cg_type, 1);
            // Address of leftover to-byte, AKA tBa.
                                                 // Bc, ta     ; tBa, fb
            sPop(2, "bitpacked Copy_n #4");      //            ; tBa, fb
            sPush(CGType.Addr);                  // tBa        ; tBa, fb
            toByteAddr = pop(); // Addr          //            ; fb
            push(toByteAddr);
            forceStacked();                      // tBa        ; tBa, fb
            load_indirect(M3.target.Word.cg_type, 0, Target.Byte);
                                                 // tB         ; tB, fb
            sPop(1, "bitpacked Copy_n #5");      //            ; tB, fb

            // Zero to-be-filled bits of to-byte.
            push(extractMask);
            forceStacked();                      // em         ; em, tB, fb
            cg.not(M3.target.Word.cg_type); // Mask for blanking to byte, AKA bm
                                                 // em         ; bm, tB, fb
            sPop(1, "bitpacked Copy_n #6");      //            ; bm, tB, fb
            cg.and(M3.target.Word.cg_type); // blanked to-byte, AKA btB
                                                 //            ; btB, fb

            // Combine bits.
            cg.or(M3.target.Word.cg_type); // byte to store, AKA stB.
                                                 //            ; stB
            sPush(M3.target.Word.cg_type);       // stB        ; stB

            // Store leftover to-byte.
            push(toByteAddr);
            forceStacked();                      // tBa, stB   ; tBa, stB
            swap();                              // stB, tBa   ; stB, tBa
            cg.store_indirect(0, M3.target.Word.cg_type, Target.Word8.cg_type);
                                                 // stB, tBa   ;
            sPop(2, "bitpacked Copy_n #7");      //            ;

            // Temps used: fromAddr, toAddr, ec, toByteAddr, byteCt, extractMask

        } else { // s > Target.Byte               // ec, fa, ta ; ec, fa, (ta)
            if (Math.floorMod(s, Target.Byte) != 0) {
                errI(s, "CM3 restriction: copy bitsize must be a power of 2 or multiple of 8");
            }
            t = alignedType(s, GCD(lhs_align, rhs_align));
            z = M3.targetMap.CG_Size[t.ordinal()];
            if (z < s) {
                // convert the count into a multiple of a machine type's size
                if (Math.floorMod(s, z) != 0) {
                    errI(s, "impossible copy_n size");
                }
                cg_load_intt(Math.floorDiv(s, z));   // ec, fa, ta ; es, ec, fa, (ta)
                cg.multiply(M3.target.Integer.cg_type); // ec, fa, ta ; Bc, fa, (ta)
            }
            // CHECK ^ Force ta?
            cg.copy_n(M3.target.Integer.cg_type, t, overlap);
                                                 // ec, fa, ta ;
            sPop(3, "Copy_n");                   //            ;
        }
    }

    /**
     * Mem[s1.A:s] := Mem[s0.A:s]; pop(2).
     * 'overlap' is true if the source and destination may partially overlap
     * (ie. you need memmove, not just memcpy).
     */
    public static void copy(long s, boolean overlap) {
        long a;
        long lhs_align, rhs_align;
        CGType t;
        long z;
        rhs_align = stack_addr_align(1);
        lhs_align = stack_addr_align(2);
        a = Math.min(stack_addr_align(2), stack_addr_align(1));
        // ^We can count on both lhs and rhs addresses being 'a'-aligned.
        if (Math.floorMod(lhs_align, Target.Byte) != 0
                || Math.floorMod(rhs_align, Target.Byte) != 0
                || Math.floorMod(s, Target.Byte) != 0) { // Must do the copy by fetch and store.
            cg.comment("Copy, non-byte aligned or sized, via load/store:");
            if (s > M3.target.Integer.size) {
                errI(s, "CM3 restriction: non-byte-aligned or non-byte-multiple-sized "
                        + "copy must fit in a word.");
            }
            load_indirect(M3.target.Word.cg_type, 0, s, rhs_align);
            store_indirect(M3.target.Word.cg_type, 0, s);
        } else { // memory-to-memory copy, byte or larger natural units.
            t = alignedType(s, GCD(lhs_align, rhs_align));
            z = M3.targetMap.CG_Size[t.ordinal()];
            if (force_pair(/* commute := */ false)) {
                swap();
            }
            if (Math.floorMod(s, z) != 0) {
                errI(s, "impossible copy size");
            }
            cg.copy(Math.floorDiv(s, z), t, overlap);
            sPop(2, "Copy");
        }
    }

    /** Mem[s0.A:s] := 0; pop(1) */
    public static void zero(long s) {
        long a = stack_addr_align(1);
        CGType t = alignedType(s, a);
        long z = M3.targetMap.CG_Size[t.ordinal()];
        forceStacked();
        if (Math.floorMod(s, z) != 0) {
            errI(s, "impossible zero size");
        }
        cg.zero(Math.floorDiv(s, z), t);
        sPop(1, "Zero");
    }

    /*----------------------------------------------------------- conversions ---*/

    /** s0.to := LOOPHOLE(s0.from, to) */
    public static void loophole(CGType from, CGType to) {
        forceStacked();
        cg.loophole(from, to);
        sPop(1, "Loophole");
        sPush(to);
    }

    /*------------------------------------------------ traps & runtime checks ---*/

    /** Abort */
    public static void abort(RuntimeError code) {
        RunTyme.lookUpProc(RunTyme.Hook.Abort);
        cg.abort(code);
    }

    /** IF (s0.A = NIL) THEN abort(code) */
    public static void check_nil(RuntimeError code) {
        RunTyme.lookUpProc(RunTyme.Hook.Abort);
        forceStacked();
        cg.check_nil(code);
    }

    /** IF (s0.I &lt; i) THEN abort(code) */
    public static void check_lo(CGType t, int[] i, RuntimeError code) {
        RunTyme.lookUpProc(RunTyme.Hook.Abort);
        forceStacked();
        cg.check_lo(t, i, code);
    }

    /** IF (i &lt; s0.I) THEN abort(code) */
    public static void check_hi(CGType t, int[] i, RuntimeError code) {
        RunTyme.lookUpProc(RunTyme.Hook.Abort);
        forceStacked();
        cg.check_hi(t, i, code);
    }

    /** IF (s0.I &lt; a) OR (b &lt; s0.I) THEN abort(code) */
    public static void check_range(CGType t, int[] a, int[] b, RuntimeError code) {
        RunTyme.lookUpProc(RunTyme.Hook.Abort);
        forceStacked();
        cg.check_range(t, a, b, code);
    }

    /** IF NOT (0 &lt;= s1.I &lt; s0.I) THEN abort(code) END; pop */
    public static void check_index(RuntimeError code) {
        RunTyme.lookUpProc(RunTyme.Hook.Abort);
        force_pair(/* commute := */ false);
        cg.check_index(M3.target.Integer.cg_type, code);
        sPop(1, "Check_index");
    }

    /** IF (s0.t # s1.t) THEN abort(code);  Pop (2) */
    public static void check_eq(CGType t, RuntimeError code) {
        RunTyme.lookUpProc(RunTyme.Hook.Abort);
        force_pair(/* commute := */ true);
        cg.check_eq(t, code);
        sPop(2, "Check_eq");
    }

    /** IF unaligned(s0.A) THEN abort(RuntimeError.UnalignedAddress); */
    public static void check_byte_aligned() {
        // Emit RT code to ensure s0.a has byte addr_align.
        M3CG.Var extra_bits;
        boolean extra_is_temp;
        // REVIEW: What VKinds can happen here?
        ValRec x = stack[(int) sCheck(1, "Check_byte_aligned")];
        if (x.type != CGType.Addr) {
            err("Check_byte_aligned called for non-address.");
        }
        if (Math.floorMod(x.addr_align, Target.Byte) != 0) {
            err("unaligned addr_align in Check_byte_aligned.");
        } else if (Math.floorMod(x.offset, Target.Byte) != 0) {
            err("address's offset is not byte aligned");
        } else if (x.bits != null) {
            extra_bits = x.bits;
            extra_is_temp = x.temp_bits;
            x.bits = null;
            x.temp_bits = false;
            RunTyme.lookUpProc(RunTyme.Hook.Abort);
            cg.load(extra_bits, 0, M3.target.Integer.cg_type, M3.target.Integer.cg_type);
            cg_load_intt(Target.Byte - 1);
            cg.and(M3.target.Integer.cg_type);
            cg.load_integer(M3.target.Integer.cg_type, TInt.Zero);
            cg.check_eq(M3.target.Integer.cg_type, RuntimeError.UnalignedAddress);
            boost_addr_alignment(Target.Byte);
            forceStacked();
            cg.load(extra_bits, 0, M3.target.Integer.cg_type, M3.target.Integer.cg_type);
            cg_load_intt(Target.Byte);
            cg.div(M3.target.Integer.cg_type, Sign.Unknown, Sign.Positive);
            cg.index_address(M3.target.Integer.cg_type, 1);
            if (extra_is_temp) {
                free_temp(extra_bits);
            }
        }
    }

    /**
     * Not in CM3: if s0.A is an address with a constant bit offset from a
     * base aligned to a byte at least, and no bit offset known only at run
     * time, s0.A := s0.A - r and return r, with r the offset modulo a, the
     * alignment of the base up to a word, returned in align[0]; else return
     * -1 and leave s0.A.  The address that is left is aligned to a.
     * WithStmt binds a name to a packed field or element that need not
     * start at a byte this way.
     */
    public static long split_bit_offset(long[] align) {
        ValRec x = stack[(int) sCheck(1, "Split_bit_offset")];
        if (x.type != CGType.Addr || x.bits != null) {
            return -1;
        }
        switch (x.kind) {
            case Absolute, Indirect, Pointer -> {
                long a = Math.min(x.base_value_align, M3.target.Word.size);
                if (a < Target.Byte || Math.floorMod(a, Target.Byte) != 0) {
                    return -1;
                }
                long r = Math.floorMod(x.offset, a);
                x.offset -= r;
                x.addr_align = a;
                align[0] = a;
                return r;
            }
            default -> {
                return -1;
            }
        }
    }

    /*---------------------------------------------------- address arithmetic ---*/

    /** s0.A := s0.A + i bits */
    public static void add_offset(long i) {
        ValRec x = stack[(int) sCheck(1, "Add_offset")];
        if (x.type != CGType.Addr) {
            err("add_offset on non-address");
            forceStacked();
        } else {
            if (i == 0) {
                return;
            }
            switch (x.kind) {
                case Stacked -> {
                    x.kind = VKind.Pointer;
                    x.offset = i;
                    x.addr_align = GCD(x.addr_align, i);
                }
                case Direct -> {
                    forceStacked();
                    x.kind = VKind.Pointer;
                    x.offset = i;
                    x.addr_align = GCD(x.addr_align, i);
                }
                case Absolute, Indirect, Pointer -> {
                    x.offset += i;
                    x.addr_align = GCD(x.addr_align, i);
                }
                default -> {
                    err("add_offset on non-address form");
                    forceStacked();
                }
            }
        }
    }

    /**
     * Ensure x has a base and it is a temp.  Its runtime contents will
     * be uninitialized.
     */
    private static void ensureTempBase(CGType t, ValRec x) {
        if (x.base == null || !x.temp_base) {
            x.base = declare_temp(M3.targetMap.CG_Size[t.ordinal()], M3.targetMap.CG_Align[t.ordinal()], t,
                                  /* in_memory := */ false);
            x.base_align = M3.targetMap.CG_Align[t.ordinal()];
            x.temp_base = true;
        }
    }

    /**
     * s1.A := s1.A + s0.I * size ; pop.  size is in bits, but must be a multiple
     * of Target.Byte.
     */
    public static void index_bytes(long size) {
        long old_align, addr_align;
        Val index;
        AssertError.check(Math.floorMod(size, Target.Byte) == 0, "CG");
        ValRec addr = stack[(int) sCheck(2, "Index_bytes")];
        AssertError.check(addr.type == CGType.Addr, "CG");
        old_align = addr.old_align;
        addr_align = addr.addr_align;
        forceStacked(); // The index
        switch (addr.kind) {

            case Stacked, Pointer -> { // Address already on cg stack
                cg.index_address(M3.target.Integer.cg_type, asBytes(size));
                sPop(1, "Index_bytes, Stacked|Pointer");
                return;
            }

            case Direct -> {
                index = pop();
                AssertError.check(index.offset == 0 && index.bits == null, "CG");
                forceStacked(); // The starting address, now stacked.
                cg.load(index.base, 0, M3.target.Integer.cg_type, M3.target.Integer.cg_type);
                cg.index_address(M3.target.Integer.cg_type, asBytes(size));
                ensureTempBase(CGType.Addr, addr);
                cg.store(addr.base, 0, CGType.Addr, CGType.Addr);
                addr.kind = VKind.Indirect;
                addr.offset = 0;
                addr.bits = null;
                addr.temp_bits = false;
            }

            case Absolute -> {
                index = pop();
                AssertError.check(index.offset == 0 && index.bits == null, "CG");
                cg.load_address(addr.base, asBytes(addr.offset));
                cg.load(index.base, 0, M3.target.Integer.cg_type, M3.target.Integer.cg_type);
                cg.index_address(M3.target.Integer.cg_type, asBytes(size));
                ensureTempBase(CGType.Addr, addr);
                cg.store(addr.base, 0, CGType.Addr, CGType.Addr);
                addr.kind = VKind.Indirect;
                addr.offset = 0;
                // Leave bits unchanged.
            }

            case Indirect -> {
                index = pop();
                AssertError.check(index.offset == 0 && index.bits == null, "CG");
                cg.load(addr.base, 0, CGType.Addr, CGType.Addr);
                cg.load(index.base, 0, M3.target.Integer.cg_type, M3.target.Integer.cg_type);
                cg.index_address(M3.target.Integer.cg_type, asBytes(size));
                ensureTempBase(CGType.Addr, addr);
                cg.store(addr.base, 0, CGType.Addr, CGType.Addr);
                // Leave offset and bits unchanged.
            }

            default -> throw new AssertError("CG");
        }
        addr.old_align = GCD(addr.old_align, size);
        addr.addr_align = GCD(addr.addr_align, size);
    }

    /**
     * s1.A := s1.A + s0.I ; pop -- note that s0.I must be less than
     * or equal to the alignment of s1.A, otherwise bad code will be generated.
     */
    public static void index_bits(long bits_addr_align) {
        // TODO: Remove this restriction by moving code from ArrayType.GenIndex in here,
        //       and removing it from any other client sites.
        Val index = pop_temp(); // Direct, no offset or bits.
        ValRec x = stack[(int) sCheck(1, "Index_bits")];
        AssertError.check(index.temp_base, "CG");
        if (x.bits != null) {
            cg.load(index.base, 0, M3.target.Integer.cg_type, M3.target.Integer.cg_type);
            cg.load(x.bits, 0, M3.target.Integer.cg_type, M3.target.Integer.cg_type);
            cg.add(M3.target.Integer.cg_type);
            cg.store(index.base, 0, M3.target.Integer.cg_type, M3.target.Integer.cg_type);
        }
        if (x.kind == VKind.Stacked) {
            x.kind = VKind.Pointer;
        }
        x.bits = index.base;
        x.temp_bits = true;
        x.addr_align = GCD(x.addr_align, bits_addr_align);
        index.temp_base = false;
        free(index);
    }

    public static void index_bits() {
        index_bits(1);
    }

    /** note that the referent of s0.A has an alignment of at least 'a'. */
    public static void boost_addr_alignment(long a) {
        ValRec x = stack[(int) sCheck(1, "Boost_addr_alignment")];
        x.old_align = Math.max(x.old_align, a);
        x.addr_align = Math.max(x.addr_align, a);
        if (x.addr_align > x.base_value_align) {
            x.base_value_align = x.addr_align;
        }
    }

    /*------------------------------------------------------- procedure calls ---*/

    // To generate a direct procedure call:
    //
    //      Start_call_direct (proc, level, t);
    //
    //      for each actual parameter i
    //          <generate value for parameter i>
    //          Pop_param ();  -or-  Pop_struct();
    //
    //      Call_direct (proc, t);
    //
    //   or to generate an indirect call:
    //
    //      Start_call_indirect (t, cc);
    //
    //      If the target is a nested procedure,
    //          <evaluate the static link to be used>
    //          Pop_static_link ();
    //
    //      for each actual parameter i
    //          <generate value for parameter i>
    //          Pop_param ();  -or-  Pop_struct();
    //
    //      <evaluate the address of the procedure to call>
    //      Gen_Call_indirect (t, cc);

    /**
     * begin a procedure call to procedure 'p' at static level 'lev'
     * that will return a value of type 't'.
     */
    public static void start_call_direct(M3CG.Proc proc, long lev, CGType t) {
        sEmpty("Start_call_direct");
        cg.start_call_direct(proc, lev, t);
    }

    /** call the procedure 'p'.  It returns a value of type t. */
    public static void call_direct(M3CG.Proc p, CGType t) {
        sEmpty("Call_direct");
        cg.call_direct(p, t);
        pushResult(t);
    }

    /** begin an indirect procedure call that will return a value of type 't'. */
    public static void start_call_indirect(CGType t, Target.CallingConvention cc) {
        sEmpty("Start_call_indirect");
        cg.start_call_indirect(t, cc);
    }

    /**
     * call the procedure whose address is in s0.A and pop s0.  The
     * procedure returns a value of type t.   Note: may also generate
     * NIL checking code.
     */
    public static void gen_Call_indirect(CGType t, Target.CallingConvention cc) {
        if (Host.doProcChk) {
            check_nil(RuntimeError.BadMemoryReference);
        }
        forceStacked();
        cg.call_indirect(t, cc);
        sPop(1, "Call_indirect");
        sEmpty("Call_indirect");
        pushResult(t);
    }

    /** Start a try block. */
    public static void start_try() {
        cg.start_try();
    }

    /** End a try block. */
    public static void end_try() {
        cg.end_try();
    }

    /**
     * call the procedure 'p' from within a TRY block. If the outcome is
     * an exception resume execution at 'handler' which will be a
     * landing_pad, else at the label immediately succeeding the call.
     */
    public static void invoke_direct(M3CG.Proc p, CGType t, long handler) {
        long next = next_label();
        sEmpty("Invoke_direct");
        cg.invoke_direct(p, t, next, handler);
        cg.set_label(next);
        pushResult(t);
    }

    /**
     * call the procedure whose address is in s0.A from within a TRY block. If
     * the outcome is an exception resume executeion at 'handler' which will be
     * a landing_pad, else at the label immediately succeeding the call.
     */
    public static void invoke_indirect(CGType t, Target.CallingConvention cc, long handler) {
        long next = next_label();
        if (Host.doProcChk) {
            check_nil(RuntimeError.BadMemoryReference);
        }
        forceStacked();
        cg.invoke_indirect(t, cc, next, handler);
        cg.set_label(next);
        sPop(1, "Invoke_indirect");
        sEmpty("Invoke_indirect");
        pushResult(t);
    }

    /**
     * resume as a result of an exception which handles any of the 'catches'
     * type uids
     */
    public static void landing_pad(long handler, int[] catches) {
        sEmpty("Landing_pad");
        cg.landing_pad(CGType.Addr, handler, catches);
        pushResult(CGType.Addr);
    }

    private static void pushResult(CGType t) {
        if (t != CGType.Void) {
            sPush(t);
        }
    }

    /** pop s0.t and make it the "next" parameter in the current call */
    public static void pop_param(CGType t) {
        forceStacked();
        cg.pop_param(t);
        sPop(1, "Pop_param");
        sEmpty("Pop_param");
    }

    /**
     * pop s0.A, it's a pointer to a structure occupying 's' bits that's
     * 'a' bit aligned; pass it by value as the "next" parameter in the current
     * call.
     * PRE: s MOD Target.Byte = 0 AND a MOD Target.Byte = 0
     */
    public static void pop_struct(int t, long s, long a) {
        forceStacked(s);
        cg.pop_struct(t, toBytes(s), byteAlign(a));
        sPop(1, "Pop_struct");
        sEmpty("Pop_struct");
    }

    /** pop s0.A and make it the static link for the current indirect procedure call */
    public static void pop_static_link() {
        forceStacked();
        cg.pop_static_link();
        sPop(1, "Pop_static_link");
    }

    /*------------------------------------------- procedure and closure types ---*/

    /** Alignment of procedures, for the target. */
    public static long procAlign() {
        if (Target.Aligned_procedures) {
            return M3.target.Integer.align;
        } else {
            return Target.Byte;
        }
    }

    /** push; s0.A := ADDR (p's body) */
    public static void load_procedure(M3CG.Proc p) {
        cg.load_procedure(p);
        sPush(CGType.Addr, procAlign());

    }

    /** push; s0.A := (static link need to call p, NIL for top-level procs) */
    public static void load_static_link(M3CG.Proc p) {
        cg.load_static_link(p);
        sPush(CGType.Addr, M3.target.Address.align);
    }

    /*------------------------------------------------ builtin type operations --*/

    private static void ref_to_object_hdr() {
        boost_addr_alignment(M3.target.Address.align);
        load_indirect(M3.target.Integer.cg_type, -M3.target.Address.pack, M3.target.Address.size,
                      M3.target.Address.align);
    }

    private static void hdr_to_info(long offset, long size) {
        long base;
        forceStacked();
        if (true) { // endian neutrality Target.endian = Target.Endian.Little
            base = offset;
        } else {
            base = M3.target.Integer.size - offset - size;
        }
        cg.extract_mn(M3.target.Integer.cg_type, false, base, size);
        // REVIEW: No updates to CG stack, whose properties have changed?
    }

    public static void ref_to_info(long offset, long size) {
        ref_to_object_hdr();
        hdr_to_info(offset, size);
    }

    /*------------------------------------------------------------ open arrays --*/

    /**
     * == Load_indirect (Type.Addr, M3RT.OA_elt_ptr, Target.Address.align,
     * Target.Address.size);  Boost_addr_alignment (a)
     */
    public static void open_elt_ptr(long a) {
        boost_addr_alignment(M3.target.Address.align);
        load_indirect(CGType.Addr, M3.rt.OA_elt_ptr, M3.target.Address.size, a);
        ValRec x = stack[(int) sCheck(1, "Open_elt_ptr")];
        // Review: does Load_indirect take care of any of the below?
        x.old_align = a;
        x.base_align = M3.target.Address.align;
        x.base_value_align = a;
        x.addr_align = a;
    }

    /**
     * == Load_indirect (Type.Int, M3RT.OA_sizes + n * Target.Integer.pack,
     * Target.Integer.align, Target.Integer.size)
     */
    public static void open_size(long n) {
        boost_addr_alignment(M3.target.Address.align);
        load_indirect(M3.target.Integer.cg_type,
                      M3.rt.OA_sizes + n * M3.target.Integer.pack, M3.target.Integer.size);
    }

    /*------------------------------------------- procedure and closure types ---*/

    /**
     * x := (proc # NIL) AND ORD ((proc)^.CL_marker = CL_marker_value);
     * IF (x) GOTO true ELSE goto FALSE;
     * Note: either true or false must be No_label
     */
    public static void if_closure(Val proc, long true_, long false_, int freq) {
        long skip = next_label();
        long nope = skip;
        if (false_ != No_label) {
            nope = false_;
        }
        if (!Target.Aligned_procedures) {
            push(proc);
            forceStacked();
            cg.loophole(CGType.Addr, M3.target.Integer.cg_type);
            cg_load_intt(M3.targetMap.CG_Align_bytes[M3.target.Integer.cg_type.ordinal()] - 1);
            cg.and(M3.target.Integer.cg_type);
            cg.load_integer(M3.target.Integer.cg_type, TInt.Zero);
            cg.if_compare(M3.target.Integer.cg_type, CompareOp.NE, nope, Always - freq);
            sPop(1, "If_closure-unaligned");
        }
        push(proc);
        boost_addr_alignment(M3.target.Address.align);
        forceStacked();
        cg.load_nil();
        cg.if_compare(CGType.Addr, CompareOp.EQ, nope, Always - freq);
        push(proc);
        boost_addr_alignment(M3.target.Address.align);
        load_indirect(M3.target.Integer.cg_type, M3.rt.CL_marker, M3.target.Integer.size);
        cg_load_intt(M3RT.CL_marker_value);
        if (true_ != No_label) {
            cg.if_compare(M3.target.Integer.cg_type, CompareOp.EQ, true_, freq);
        } else {
            cg.if_compare(M3.target.Integer.cg_type, CompareOp.NE, false_, freq);
        }
        set_label(skip);
        sPop(2, "If_closure");
    }

    /** s0.A := (s0.A)^.CL_proc */
    public static void closure_proc() {
        boost_addr_alignment(M3.target.Address.align);
        load_indirect(CGType.Addr, M3.rt.CL_proc, M3.target.Address.size, procAlign());
    }

    /** s0.A := (s0.A)^.CL_frame */
    public static void closure_frame() {
        boost_addr_alignment(M3.target.Address.align);
        load_indirect(CGType.Addr, M3.rt.CL_frame, M3.target.Address.size, M3.target.Address.align);
    }

    /*----------------------------------------------------------------- misc. ---*/

    /** annotate the output with a&amp;b&amp;c&amp;d as a comment */
    public static void comment(long offset, boolean is_const, String a, String b, String c, String d) {
        long o = offset;
        if (o < 0) {
            cg.comment(a, b, c, d);
        } else {
            CommentNode n = new CommentNode();
            n.o = o - 1;
            n.a = a;
            n.b = b;
            n.c = c;
            n.d = d;
            pushPending(n, is_const);
        }
    }

    public static void comment(long offset, boolean is_const, String a, String b, String c) {
        comment(offset, is_const, a, b, c, null);
    }

    public static void comment(long offset, boolean is_const, String a, String b) {
        comment(offset, is_const, a, b, null, null);
    }

    public static void comment(long offset, boolean is_const, String a) {
        comment(offset, is_const, a, null, null, null);
    }

    public static void comment(long offset, boolean is_const) {
        comment(offset, is_const, null, null, null, null);
    }

    private static void dumpComment(CommentNode x) {
        dumpNode(x);
        cg.comment(x.a, x.b, x.c, x.d);
    }


    /*--------------------------------------------------------------- atomics ---*/

    // REVIEW: These need verification that s0.x = StackType[t].
    //         Otherwise, s0.x needs to be passed in as an additional
    //         parameter and used in place of StackType[t].
    //         If neither, CG errors will fly.

    public static void store_ordered(CGType t, M3CG.MemoryOrder order) {
        forceStacked2("Store_ordered", /* commute := */ false);
        cg.store_ordered(StackType[t.ordinal()], t, order);
    }

    public static void load_ordered(CGType t, M3CG.MemoryOrder order) {
        forceStacked1("Load_ordered");
        cg.load_ordered(t, StackType[t.ordinal()], order);
        sPush(StackType[t.ordinal()]);
    }

    /** s2.A must be forced */
    public static void exchange(CGType t, M3CG.MemoryOrder order) {
        forceStacked2("Exchange", /* commute := */ false);
        cg.exchange(t, StackType[t.ordinal()], order);
        sPush(StackType[t.ordinal()]);
    }

    public static void compare_exchange(CGType t, M3CG.MemoryOrder success, M3CG.MemoryOrder failure) {
        force_pair(/* commute := */ false);
        cg.compare_exchange(t, StackType[t.ordinal()],
                            M3.target.Integer.cg_type, success, failure);
        sPop(3, "Compare_exchange");
        sPush(CGType.Int32);
    }

    public static void fence(M3CG.MemoryOrder order) {
        cg.fence(order);
    }

    public static void fetch_and_op(M3CG.AtomicOp op, CGType t, M3CG.MemoryOrder order) {
        forceStacked2("Fetch_and_op", /* commute := */ false);
        cg.fetch_and_op(op, t, StackType[t.ordinal()], order);
        sPush(StackType[t.ordinal()]);
    }

    /*-------------------------------------------------------------- internal ---*/

    private static long byteAlign(long a) {
        return Math.floorDiv(Math.max(a, Target.Byte), Target.Byte);
    }

    /**
     * Largest target integer type whose size evenly divides s and whose
     * alignment evenly divides a.  Generate CG error, if none exists.
     */
    private static CGType alignedType(long s, long a) {
        if (isAlignedMultiple(s, a, M3.target.Integer)) {
            return M3.target.Integer.cg_type;
        }
        if ((Target.Int64.size <= M3.target.Integer.size)
                && isAlignedMultiple(s, a, Target.Int64)) {
            return CGType.Int64;
        }
        if (isAlignedMultiple(s, a, Target.Int32)) {
            return CGType.Int32;
        }
        if (isAlignedMultiple(s, a, Target.Int16)) {
            return CGType.Int16;
        }
        if (isAlignedMultiple(s, a, Target.Int8)) {
            return CGType.Int8;
        }
        err("UnalignedType, no possible type:  s/a=" + Long.toString(s) + "/" + Long.toString(a));
        return M3.target.Integer.cg_type;
    }

    private static boolean isAlignedMultiple(long s, long a, Target.Int_type t) {
        return (Math.floorMod(s, t.size) == 0)
            && ((a == t.align) || (Math.floorMod(a, t.align) == 0));
    }

    private static long toVarSize(long n, long a) {
        long n_bytes = Math.floorDiv(n + Target.Byte - 1, Target.Byte);
        long align = byteAlign(a);
        return Math.floorDiv(n_bytes + align - 1, align) * align;
    }

    private static long toBytes(long n) {
        return Math.floorDiv(n + Target.Byte - 1, Target.Byte);
    }

    private static long asBytes(long n) {
        long x = Math.floorDiv(n, Target.Byte);
        if (x * Target.Byte != n) {
            errI(n, "non-byte-aligned offset");
        }
        return x;
    }

    /**
     * Push host integer i on the M3CG stack, with target type Integer.
     * No action on CG stack.
     */
    private static void cg_load_intt(long i) {
        int[] val = TInt.newInt();
        boolean b = TInt.fromInt(i, val);
        if (!b
                || TInt.LT(val, M3.target.Integer.min)
                || TInt.LT(M3.target.Integer.max, val)) {
            errI(i, "integer not representable");
        }
        cg.load_integer(M3.target.Integer.cg_type, val);
    }

    /**
     * Push host integer i on the M3CG stack, with target type Word.
     * No action on CG stack.
     */
    private static void cg_load_word(long i) {
        int[] val = TInt.newInt();
        boolean b = TInt.fromInt(i, val);
        if (!b
                || TInt.LT(val, M3.target.Integer.min)
                || TInt.LT(M3.target.Integer.max, val)) {
            errI(i, "integer not representable");
        }
        cg.load_integer(M3.target.Word.cg_type, val);
    }

    // Port, omitted: the procedure cg_load_addr (commented out in the
    // original).

    /** Returns TRUE if it leaves the items are stacked in the wrong order */
    private static boolean force_pair(boolean commute) {
        boolean s1 = stack[(int) sCheck(1, "Force_pair")].kind == VKind.Stacked;
        boolean s2 = stack[(int) sCheck(2, "Force_pair")].kind == VKind.Stacked;
        if (s2) { // deeper element is already stacked
            if (!s1) { // only the top element needs to become stacked
                forceStacked();
            }
            return false;
        } else if (s1) { // only the deeper element needs to become stacked
            swap();
            forceStacked();
            if (!commute) {
                swap();
            }
        } else { // neither element is stacked
            if (!commute) {
                swap();
            }
            forceStacked();
            swap();
            forceStacked();
        }
        return commute;
    }

    // Port, omitted: the procedure stack_old_align (commented out in the
    // original).

    private static long stack_addr_align(long n) {
        return stack[(int) sCheck(n, "stack_addr_align")].addr_align;
    }

    // Port, omitted: the procedure SLV_align (commented out in the
    // original).

    /** Largest alignment x is statically sure to have. */
    private static long lv_align(ValRec x) {
        long align = x.old_align;
        if (x.offset != 0) {
            align = GCD(align, x.offset);
        }
        if (x.bits != null) {
            align = 1;
            // TODO: Probably: align := GCD (align, x.bits.old_align?
        }
        return align;
    }

    /** return the greatest x that divides both a and b. */
    public static long GCD(long a, long b) {
        long c;
        a = Math.abs(a);
        b = Math.abs(b);
        if (b == 0) {
            return a;
        }
        while (true) {
            c = Math.floorMod(a, b);
            if (c == 0) {
                return b;
            }
            a = b;
            b = c;
        }
    }

    /** base_align is what the caller guarantees to give the returned type. */
    private static CGType mustFindIntType(CGType t, long s, long o, long base_align) {
        CGType best_t;
        best_t = mayFindIntType(t, s, o, base_align);
        if (best_t == CGType.Void) {
            best_t = t;
            err("unable to find integer type?  type=" + Target.TypeNames[t.ordinal()]
                + "  size/offset/align=" + Long.toString(s) + "/" + Long.toString(o) + "/" + Long.toString(base_align));
        }
        return best_t;
    }

    /** base_align is what the caller guarantees to give the returned type. */
    private static CGType mayFindIntType(CGType t, long s, long o, long base_align) {
        CGType best_t;
        if (Target.SignedType[t.ordinal()]) {
            best_t = scanTypes(M3.targetMap.Integer_types, t, s, o, base_align);
        } else {
            best_t = scanTypes(M3.targetMap.Word_types, t, s, o, base_align);
        }
        return best_t;
    }

    /**
     * From Types, find and return a CG type 'tt', with that satisfies:
     * 1) align(tt) evenly divides base_align (thus align(tt) &lt;= base_align)
     * 2) A field of size(tt) and aligned align(tt) will fully contain a field
     *    of size s, that starts at offset o from a point with alignment
     *    base_align (thus size(tt) &gt;= s).
     * 3) size(tt) &lt;= size(t)
     * 4) align(tt) &lt;= align(t)
     * 5) no other GC type in Types has smaller size or alignment.
     */
    private static CGType scanTypes(Target.Int_type[] Types,
                                    CGType t, long s, long o, long base_align) {
        long best_s = M3.targetMap.CG_Size[t.ordinal()] + 1;
        long best_a = M3.targetMap.CG_Align[t.ordinal()] + 1;
        CGType best_t = CGType.Void;
        for (int i = 0; i <= Types.length - 1; i++) {
            Target.Int_type z = Types[i];
            if ((z.size < best_s) // size is better than we've previously found.
                    && (z.align <= best_a) // alignment is no worse.
                    && (s <= z.size) // It's big enough for the field.
                    && (Math.floorMod(base_align, z.align) == 0)
                        // Its alignment is satisfied by caller.
                    && (s + Math.floorMod(o, z.align) <= z.size)) { // The offset field fits.
                // remember this type
                best_t = z.cg_type;
                best_s = z.size;
                best_a = z.align;
            }
        }
        return best_t;
    }

    private static long alignOfInt(long IntVal) {
        long I;
        long TrialAlign;
        I = M3.targetMap.Word_types.length - 1;
        while (true) {
            if (I < 0) {
                return 1;
            }
            TrialAlign = M3.targetMap.Word_types[(int) I].align;
            if (Math.floorMod(IntVal, TrialAlign) == 0) {
                return TrialAlign;
            }
            I--;
        }
    }

    private static long alignOfTInt(int[] IntVal) {
        long I;
        long TrialAlign;
        int[] TrialTInt = TInt.newInt();
        int[] ModVal = TInt.newInt();
        I = M3.targetMap.Word_types.length - 1;
        while (true) {
            if (I < 0) {
                return 1;
            }
            TrialAlign = M3.targetMap.Word_types[(int) I].align;
            if (TInt.fromInt(TrialAlign, /*OUT*/ TrialTInt)
                    && TInt.mod(IntVal, TrialTInt, /*OUT*/ ModVal)
                    && java.util.Arrays.equals(ModVal, TInt.Zero)) {
                return TrialAlign;
            }
            I--;
        }
    }

    private static void sPush(CGType t, long addr_align) {
        ValRec x = stack[(int) sCheck(0, "SPush")];
        x.kind      = VKind.Stacked;
        x.type      = StackType[t.ordinal()];
        x.temp_base = false;
        x.temp_bits = false;
        x.old_align = M3.targetMap.CG_Align[t.ordinal()]; // Was Target.Byte
        x.base_align = M3.targetMap.CG_Align[x.type.ordinal()];
        x.base_value_align = addr_align;
        x.addr_align = addr_align;
        x.base      = null;
        x.bits      = null;
        x.offset    = 0;
        TInt.assign(x.int_, TInt.Zero);
        x.float_.assign(TFloat.ZeroR);
        x.next      = null;
        tos++;
    }

    private static void sPush(CGType t) {
        sPush(t, Target.Word8.align);
    }

    private static void sPop(long n, String tag) {
        if (tos < n) {
            errI(n, "SPop: stack underflow in " + tag);
            tos = 0;
        } else {
            tos -= n;
        }
    }

    private static long sCheck(long n, String tag) {
        if (tos < n) {
            errI(n, "CG.SCheck: stack underflow in " + tag);
            return 0;
        } else if (tos - n > stack.length - 1) {
            errI(n, "CG.SCheck: stack overflow in " + tag);
            return 0;
        } else {
            return tos - n;
        }
    }

    private static void err(String msg) {
        msg = "** INTERNAL CG ERROR *** " + msg;
        Error.msg(msg);
        cg.comment(msg);
    }

    private static void errI(long n, String msg) {
        msg = "** INTERNAL CG ERROR *** " + msg;
        Error.int_(n, msg);
        cg.comment(msg, ": ", Long.toString(n));
    }

    private static Map<Integer, Integer> newIntTbl() {
        return new HashMap<>();
    }

    private static <V> Map<Integer, V> newNameTbl() {
        return new HashMap<>();
    }

    /*------------------------------------------------------------- debugging ---*/

    private static final String[] Bool = {"F ", "T "};
    private static final String[] VName = {
        "Integer  ",
        "Float    ",
        "Stacked  ",
        "Direct   ",
        "Absolute ",
        "Indirect ",
        "Pointer  "
    };

    private static void sDump(String tag) {
        String msg;
        cg.comment(tag);
        cg.comment("------------ begin stack dump ------------");
        for (long i = tos - 1; i >= 0; i--) {
            ValRec x = stack[(int) i];
            msg = VName[x.kind.ordinal()];
            msg = msg + Target.TypeNamesFixedWidth[x.type.ordinal()];
            msg = msg + Bool[ix(x.temp_base)];
            msg = msg + Bool[ix(x.temp_bits)];
            msg = msg + Long.toString(x.old_align) + " ";
            msg = msg + Long.toString(x.base_align) + " ";
            msg = msg + Long.toString(x.addr_align) + " ";
            msg = msg + Long.toString(x.base_value_align) + " ";
            msg = msg + Long.toString(x.offset);
            cg.comment(msg);
        }
        cg.comment("------------- end stack dump -------------");
    }

    private static void sEmpty(String tag) {
        if (tos > 0) {
            forceStacked();
            errI(tos, "stack not empty, depth");
            sDump(tag);
        }
    }

    /** PRE: align in {8, 16, 32, 64} */
    private static long log2OfAlign(long align) {
        if (align == 8) {
            return 3;
        } else if (align == 16) {
            return 4;
        } else if (align == 32) {
            return 5;
        } else if (align == 64) {
            return 6;
        } else {
            return -1;
        }
    }

    /**
     * PRE: TOS.type = Addr.
     * POST: Result is a temp var containing, at RT, count of bits
     *       within align.  It needs to be freed.
     * Gen RT code to reduce TOS.bits to &lt; align and compensate
     * by increasing the Byte-aligned component of TOS.
     */
    private static M3CG.Var reduceBits(long align) {
        long bitCt;
        int[] bitsMask = TInt.newInt();
        M3CG.Var save_bits;
        M3CG.Var new_bits = null;
        {
            ValRec AddrVal = stack[(int) sCheck(1, "ReduceBits1")];
            save_bits = AddrVal.bits;
            AssertError.check(AddrVal.type == CGType.Addr, "CG");
            AddrVal.bits = null;
        }
        forceStacked(); // The whole-byte portion onto the cg stack.
        if (save_bits != null) {
            ValRec AddrVal = stack[(int) sCheck(1, "ReduceBits2")];
            new_bits = declare_temp(M3.target.Word.size, M3.target.Word.align,
                                    M3.target.Word.cg_type, /* in_memory := */ false);
            bitCt = log2OfAlign(align);
            TWord.nBitsOnRight(bitCt, /*OUT*/ bitsMask);
            cg.load(save_bits, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
            cg_load_intt(bitCt);
            cg.shift_right(M3.target.Word.cg_type);
            cg.index_address(M3.target.Word.cg_type, Math.floorDiv(align, Target.Byte));

            cg.load(save_bits, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
            cg.load_integer(M3.target.Word.cg_type, bitsMask);
            cg.and(M3.target.Word.cg_type);
            cg.store(new_bits, 0, M3.target.Word.cg_type, M3.target.Word.cg_type);
            AddrVal.kind = VKind.Pointer;
            AddrVal.bits = new_bits;
        }
        return new_bits;
    }

    private static long Log2OfByte = 3;

    static {
        AssertError.check((1L << Log2OfByte) == Target.Byte, "CG");
        // Port: RTParams.IsPresent ("m3front-debug-cg") is FALSE.
        debug = false;
    }
}
