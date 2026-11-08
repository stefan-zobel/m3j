// Java port of CM3's m3-sys/m3middle/src/M3CG_Check.i3 and M3CG_Check.m3.
// Original: Copyright (C) 1993, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.middle;

import java.util.HashMap;
import java.util.Map;

import m3j.middle.M3CG.AtomicOp;
import m3j.middle.M3CG.CompareOp;
import m3j.middle.M3CG.ConvertOp;
import m3j.middle.M3CG.MemoryOrder;
import m3j.middle.M3CG.Proc;
import m3j.middle.M3CG.RuntimeError;
import m3j.middle.M3CG.Sign;
import m3j.middle.M3CG.Var;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// M3CG_Check.i3:
// Last modified on Wed Dec  8 16:17:00 PST 1993 by kalsow
//      modified on Mon Apr 13 09:55:12 PDT 1992 by muller

// M3CG_Check.m3:
// Last modified on Tue Jun 20 16:03:47 PDT 1995 by kalsow
//      modified on Tue Jun  1 14:56:30 PDT 1993 by muller

/**
 * A code generator that checks the M3CG stream it passes on.
 *
 * <p>Port: the procedures of the module that take the U as their first
 * parameter are methods of {@link U}. The module variable T_to_ST, which
 * is constant after "begin_unit", is a field of U, as are the
 * state objects of M3ID, Target and TargetMap that {@link #New} takes in
 * addition to the parameters of the original. IntIntTbl.T is a
 * {@link HashMap}.
 */
public final class M3CG_Check {
    private M3CG_Check() {}

    /** stack data types */
    private enum ST {
        Addr, Int32, Int64, Reel, LReel, XReel, Void,
        IType, RType, AType,
        Any, Missing, DontCare, Match
    }

    private static final String[] ST_name = {
        "Addr ", "Int32 ", "Int64 ", "Real ", "LReal ", "ExReal ", "Void ",
        "W,I ", "R,L,E ", "W,I,R,L,E ",
        "any ", "", "<dontCare> ", "<=match "
    };

    /*----------------------------------------------- binary/ASCII conversion ---*/

    // Port: the cache Ints of the texts of the small integers is not kept;
    // the texts are the same.

    private static String int_(long i) {
        return " " + Long.toString(i);
    }

    /*--------------------------------------------------------- low level I/O ---*/

    private static String unNil(String a) {
        if (a == null) {
            return "**NIL**";
        } else {
            return a;
        }
    }

    /*---------------------------------------------------------------------------*/

    /**
     * Returns a fresh, initialized code generator that passes its calls
     * to 'child' and checks that the stream obeys the M3CG restrictions.
     * If 'clean_jumps', it also checks that the stack is empty at every
     * branch point.  If 'clean_stores', it also checks that the stack is
     * empty on every store instruction.  If 'nested_calls' is false, it
     * checks that function calls are not nested.  If 'nested_procs' is false,
     * it checks that function bodies do not overlap.
     *
     * <p>Port: 'ids', 'target' and 'targetMap' are the state of M3ID, Target
     * and TargetMap that the checks use.
     */
    public static M3CG.T New(M3CG.T child,
                             boolean clean_jumps, boolean clean_stores,
                             boolean nested_calls, boolean nested_procs,
                             M3ID.State ids, Target.State target,
                             TargetMap.State targetMap) {
        child.set_error_handler(M3CG_Check::crashAndBurn);
        U u = new U(ids, target, targetMap);
        u.child = child;
        u.note_error = M3CG_Check::crashAndBurn;
        u.runtime = new HashMap<>(20);
        u.clean_jumps = clean_jumps;
        u.clean_stores = clean_stores;
        u.nested_calls = nested_calls;
        u.nested_procs = nested_procs;
        return u;
    }

    private static void crashAndBurn(String msg) {
        System.out.print("Unhandled M3CG_Check error: " + msg);
        System.out.flush();
        System.err.flush();
        throw new AssertError("M3CG_Check");
    }

    /*------------------------------------------------------------ load/store ---*/

    // ZMap = ARRAY ZType OF BOOLEAN, MMap = ARRAY MType OF BOOLEAN.
    // Port: a ZType t is at index t.ordinal() - Word32.ordinal(), an MType at
    // index t.ordinal().

    private static final boolean T = true;
    private static final boolean F = false;

    private static final boolean[][] LegalLoad = {
        //            W32 I32 W64 I64 Reel LReel XReel Addr
        /* Word8  */ { T,  T,  T,  T,  F,   F,    F,    F },
        /* Int8   */ { T,  T,  T,  T,  F,   F,    F,    F },
        /* Word16 */ { T,  T,  T,  T,  F,   F,    F,    F },
        /* Int16  */ { T,  T,  T,  T,  F,   F,    F,    F },
        /* Word32 */ { T,  T,  T,  T,  F,   F,    F,    F },
        /* Int32  */ { T,  T,  T,  T,  F,   F,    F,    F },
        /* Word64 */ { F,  F,  T,  T,  F,   F,    F,    F },
        /* Int64  */ { F,  F,  T,  T,  F,   F,    F,    F },
        /* Reel   */ { F,  F,  F,  F,  T,   F,    F,    F },
        /* LReel  */ { F,  F,  F,  F,  F,   T,    F,    F },
        /* XReel  */ { F,  F,  F,  F,  F,   F,    T,    F },
        /* Addr   */ { F,  F,  F,  F,  F,   F,    F,    T }
    };

    private static final boolean[][] LegalStore = {
        //            Word8 Int8 W16 I16 W32 I32 W64 I64 Reel LReel XReel Addr
        /* Word32 */ { T,    T,   T,  T,  T,  T,  F,  F,  F,   F,    F,    F },
        /* Int32  */ { T,    T,   T,  T,  T,  T,  F,  F,  F,   F,    F,    F },
        /* Word64 */ { T,    T,   T,  T,  T,  T,  T,  T,  F,   F,    F,    F },
        /* Int64  */ { T,    T,   T,  T,  T,  T,  T,  T,  F,   F,    F,    F },
        /* Reel   */ { F,    F,   F,  F,  F,  F,  F,  F,  T,   F,    F,    F },
        /* LReel  */ { F,    F,   F,  F,  F,  F,  F,  F,  F,   T,    F,    F },
        /* XReel  */ { F,    F,   F,  F,  F,  F,  F,  F,  F,   F,    T,    F },
        /* Addr   */ { F,    F,   F,  F,  F,  F,  F,  F,  F,   F,    F,    T }
    };

    /** LegalLoad [t, u] for an MType t and a ZType u. */
    private static boolean legalLoad(CGType t, CGType u) {
        return LegalLoad[t.ordinal()][u.ordinal() - CGType.Word32.ordinal()];
    }

    /** LegalStore [t, u] for a ZType t and an MType u. */
    private static boolean legalStore(CGType t, CGType u) {
        return LegalStore[t.ordinal() - CGType.Word32.ordinal()][u.ordinal()];
    }

    /*--------------------------------------------------------- atomics ---*/

    /** FType = ARRAY Target.Precision OF RType. */
    private static final CGType[] FType = { CGType.Reel, CGType.LReel, CGType.XReel };

    private static final class U extends M3CG.T {
        final M3ID.State ids;
        final Target.State target;
        final TargetMap.State targetMap;

        /** CONST after "begin_unit" call */
        final ST[] T_to_ST = new ST[CGType.values().length];

        boolean clean_stores = false;
        boolean clean_jumps = false;
        boolean nested_calls = true;
        boolean nested_procs = false;
        long cur_line = 0;
        long next_var = 1;
        long next_proc = 1;
        long next_scope = 1;
        long n_errors = 0;
        long proc_count = 0;
        long block_count = 0;
        long call_count = 0;
        long top_of_stack = 0;
        long in_init = 0;
        long init_cursor = 0;
        boolean invoke = false;
        M3CG_Ops.ErrorHandler note_error = null;
        /** Name -&gt; BOOL */
        Map<Integer, Long> runtime = null;
        // temps : IntIntTbl.T := NIL; (* Var -> line number *)
        final CGType[] stack = new CGType[51];

        U(M3ID.State ids, Target.State target, TargetMap.State targetMap) {
            this.ids = ids;
            this.target = target;
            this.targetMap = targetMap;
            java.util.Arrays.fill(T_to_ST, ST.Void);
            java.util.Arrays.fill(stack, CGType.Word8);
        }

        /*----------------------------------------------- low level I/O ---*/

        private void putErr(String a, String b, String c) {
            a = unNil(a);
            b = unNil(b);
            c = unNil(c);
            note_error.apply("********* M3CG_Check ERROR *********** " + a + b + c);
            child.comment("********* M3CG_Check ERROR *********** ", a, b, c);
            n_errors++;
        }

        private void putErr(String a, String b) {
            putErr(a, b, null);
        }

        private void putErr(String a) {
            putErr(a, null, null);
        }

        /*-------------------------------------------------------- stack checking ---*/

        private ST stack_Get(long depth) {
            long x = top_of_stack - depth - 1;
            if ((0 <= x) && (x <= stack.length - 1)) {
                return T_to_ST[stack[(int) x].ordinal()];
            } else {
                return ST.Missing;
            }
        }

        private ST tst(CGType t) {
            return T_to_ST[t.ordinal()];
        }

        /** Port: a method, because it reads T_to_ST. */
        private boolean isOK(ST need, ST got, ST prev) {
            switch (need) {
                case IType:
                    return (tst(CGType.Word32).compareTo(got) <= 0) && (got.compareTo(tst(CGType.Int64)) <= 0);
                case RType:
                    return (tst(CGType.Reel).compareTo(got) <= 0) && (got.compareTo(tst(CGType.XReel)) <= 0);
                case AType:
                    return (tst(CGType.Word32).compareTo(got) <= 0) && (got.compareTo(tst(CGType.XReel)) <= 0);
                case Any:
                    return (tst(CGType.Word8).compareTo(got) <= 0) && (got.compareTo(tst(CGType.Void)) <= 0);
                case Void:
                    return (got == ST.Missing);
                case DontCare:
                    return true;
                case Match:
                    return got == prev;
                default:
                    return (got == need);
            }
        }

        private static String st_Name(ST a, ST prev) {
            if (a == ST.Match) {
                a = prev;
            }
            return ST_name[a.ordinal()];
        }

        private void stack_Pop(ST a, ST b, ST c, ST d) {
            ST s0 = stack_Get(0);
            ST s1 = stack_Get(1);
            ST s2 = stack_Get(2);
            ST s3 = stack_Get(3);
            if (isOK(a, s0, a) && isOK(b, s1, a)
                    && isOK(c, s2, b) && isOK(d, s3, c)) {
                // no error
            } else {
                putErr("bad stack:  expected [ ",
                    st_Name(a, a) + " " + st_Name(b, a) + " "
                    + st_Name(c, b) + " " + st_Name(d, c),
                    " ] got [ "
                    + st_Name(s0, s0) + " " + st_Name(s1, s0) + " "
                    + st_Name(s2, s1) + " " + st_Name(s3, s2) + " ]");
            }
            if (d != ST.DontCare) {
                top_of_stack -= 4;
            } else if (c != ST.DontCare) {
                top_of_stack -= 3;
            } else if (b != ST.DontCare) {
                top_of_stack -= 2;
            } else { // a # ST.DontCare
                top_of_stack--;
            }
            if (top_of_stack < 0) {
                top_of_stack = 0;
            }
        }

        /** s_pop (s0, s1, s2, s3 := ST.DontCare) */
        private void s_pop(ST s0, ST s1, ST s2, ST s3) {
            stack_Pop(s0, s1, s2, s3);
        }

        private void s_pop(ST s0, ST s1, ST s2) {
            stack_Pop(s0, s1, s2, ST.DontCare);
        }

        private void s_pop(ST s0, ST s1) {
            stack_Pop(s0, s1, ST.DontCare, ST.DontCare);
        }

        private void s_pop(ST s0) {
            stack_Pop(s0, ST.DontCare, ST.DontCare, ST.DontCare);
        }

        private void s_push(CGType t) {
            if (top_of_stack <= stack.length - 1) {
                stack[(int) top_of_stack] = t;
            } else {
                putErr("stack overflow");
            }
            top_of_stack++;
        }

        private void s_repush() {
            top_of_stack++;
        }

        private void s_empty() {
            if (top_of_stack > 0) {
                putErr("non-empty stack: ", stack_Dump());
                top_of_stack = 0;
            }
        }

        // Port, omitted: the debugging procedure SDump (commented out in the
        // original).

        private String stack_Dump() {
            String s = "[ ";
            for (long i = 0; i <= Math.min(top_of_stack - 1, 4); i++) {
                s = s + ST_name[stack_Get(i).ordinal()];
            }
            if (top_of_stack > 5) {
                s = s + "... ";
            }
            s = s + "]";
            return s;
        }

        /*------------------------------------------------ READONLY configuration ---*/

        @Override
        public void set_error_handler(M3CG_Ops.ErrorHandler p) {
            note_error = p;
            child.set_error_handler(p);
        }

        /*----------------------------------------------------- compilation units ---*/

        /** called before any other method to initialize the compilation unit */
        @Override
        public void begin_unit(long optimize) {
            final ST[] IntMap = {ST.Int32, ST.Int64};
            ST ST_Int = IntMap[(int) Math.floorDiv(target.Integer.bytes, 8)];
            // initialize the mapping of stack types
            ST[] m = {
                ST_Int, ST_Int, ST_Int, ST_Int,      // Word8, Int8, Word16, Int16
                ST_Int, ST_Int, ST.Int64, ST.Int64,  // Word32, Int32, Word64, Int64
                ST.Reel, ST.LReel, ST.XReel,         // Reel, LReel, XReel
                ST.Addr, ST.Addr,                    // Addr, Struct
                ST.Void};
            System.arraycopy(m, 0, T_to_ST, 0, m.length);

            s_empty();
            child.begin_unit(optimize);
        }

        /**
         * called after all other methods to finalize the unit and write the
         * resulting object
         */
        @Override
        public void end_unit() {
            s_empty();
            child.end_unit();
            if (n_errors <= 0) {
                // ok
            } else if (n_errors == 1) {
                note_error.apply("1 code generation error");
            } else { // self.n_errors > 1
                note_error.apply(int_(n_errors) + " code generation errors");
            }
        }

        /*------------------------------------------------ debugging line numbers ---*/

        @Override
        public void set_source_line(long line) {
            cur_line = line;
            child.set_source_line(line);
        }

        /*--------------------------------------------------------- runtime hooks ---*/

        @Override
        public void set_runtime_proc(int n, Proc p) {
            checkProc(p);
            // Port: IntIntTbl.put returns TRUE if the key was present; the
            // values are never null.
            if (runtime.put(n, 0L) != null) {
                putErr("redefined runtime proc: ", M3ID.toText(ids, n));
            }
            child.set_runtime_proc(n, p);
        }

        /*------------------------------------------------- variable declarations ---*/

        private void checkVar(Var v) {
            if (v == null) {
                putErr("NIL variable");
            }
        }

        @Override
        public void bind_segment(Var seg, long s, long a, CGType t, boolean exported, boolean inited) {
            checkVar(seg);
            child.bind_segment(seg, s, a, t, exported, inited);
        }

        @Override
        public Var declare_temp(long s, long a, CGType t, boolean in_memory, int typename) {
            Var v;
            // IF (self.temps = NIL) THEN
            //   self.temps := NEW (IntIntTbl.Default).init (); END;
            v = child.declare_temp(s, a, t, in_memory, typename);
            // IF self.temps.put (v, self.cur_line) THEN
            //   PutErr (self, "temporary reused while live!");
            // END;
            return v;
        }

        @Override
        public void free_temp(Var v) {
            // VAR line: INTEGER;
            checkVar(v);
            // IF (self.temps = NIL) THEN
            //   self.temps := NEW (IntIntTbl.Default).init (); END;
            // IF NOT self.temps.delete (v, line) THEN
            //   PutErr (self, "temp freed twice");
            // END;
            child.free_temp(v);
        }

        @Override
        public void widechar_size(long size) {
            if (size != 16 && size != 32) {
                putErr("invalid widechar size");
            }
            child.widechar_size(size);
        }

        /*---------------------------------------- static variable initialization ---*/

        @Override
        public void begin_init(Var v) {
            checkVar(v);
            if (in_init > 0) {
                putErr("nested static initialization");
            }
            in_init++;
            init_cursor = 0;
            child.begin_init(v);
        }

        @Override
        public void end_init(Var v) {
            checkVar(v);
            if (in_init > 0) {
                in_init--;
                init_cursor = 0;
            } else {
                putErr("missing begin_init");
            }
            child.end_init(v);
        }

        private void doInit(long o, long s) {
            if (in_init <= 0) {
                putErr("missing begin_init");
            }
            if (o >= init_cursor) {
                init_cursor = o + s;
            } else {
                putErr("decreasing offsets");
            }
        }

        @Override
        public void init_int(long o, int[] value, CGType t) {
            doInit(o, targetMap.CG_Bytes[t.ordinal()]);
            child.init_int(o, value, t);
        }

        @Override
        public void init_proc(long o, Proc value) {
            doInit(o, target.Address.bytes);
            child.init_proc(o, value);
        }

        @Override
        public void init_label(long o, long value) {
            doInit(o, target.Address.bytes);
            child.init_label(o, value);
        }

        @Override
        public void init_var(long o, Var value, long bias) {
            doInit(o, target.Address.bytes);
            child.init_var(o, value, bias);
        }

        @Override
        public void init_offset(long o, Var value) {
            doInit(o, target.Integer.bytes);
            child.init_offset(o, value);
        }

        @Override
        public void init_chars(long o, String value) {
            doInit(o, value.length() * Target.Char.bytes);
            child.init_chars(o, value);
        }

        @Override
        public void init_float(long o, Target.Float f) {
            doInit(o, targetMap.Float_types[f.pre.ordinal()].bytes);
            child.init_float(o, f);
        }

        /*------------------------------------------------------------ procedures ---*/

        private void checkProc(Proc p) {
            if (p == null) {
                putErr("NIL procedure");
            }
        }

        @Override
        public void begin_procedure(Proc p) {
            checkProc(p);
            if ((proc_count > 0) && (!nested_procs)) {
                putErr("nested procedure declaration");
            }
            proc_count++;
            child.begin_procedure(p);
        }

        @Override
        public void end_procedure(Proc p) {
            checkProc(p);
            if (proc_count > 0) {
                proc_count--;
            } else {
                putErr("missing begin_procedure");
            }
            if ((block_count > 0) && (!nested_procs)) {
                putErr("missing end_blocks: ", int_(block_count));
                block_count = 0;
            }
            s_empty();
            // IF (self.temps # NIL) THEN
            //   VAR it := self.temps.iterate (); k: REFANY; line: INTEGER; BEGIN
            //     WHILE it.next (tag, line) DO
            //       PutErr (self, "temp not freed, created on line ", Int (line));
            //     END;
            //   END;
            // END;
            child.end_procedure(p);
        }

        /** marks the beginning of a nested anonymous block */
        @Override
        public void begin_block() {
            if (proc_count <= 0) {
                putErr("begin_block not in procedure");
            }
            s_empty();
            block_count++;
            child.begin_block();
        }

        /** marks the ending of a nested anonymous block */
        @Override
        public void end_block() {
            if (block_count > 0) {
                block_count--;
            } else {
                putErr("missing begin_block");
            }
            s_empty();
            child.end_block();
        }

        @Override
        public void note_procedure_origin(Proc p) {
            checkProc(p);
            s_empty();
            child.note_procedure_origin(p);
        }

        /*------------------------------------------------------------ statements ---*/

        private void checkLabel(long l) {
            if (l < 0) { // OR (self.next_label <= l)
                putErr("undefined label: ", int_(l));
            }
        }

        /** define 'l' to be at the current pc */
        @Override
        public void set_label(long l, boolean barrier) {
            if (clean_jumps && !invoke) {
                s_empty();
            }
            checkLabel(l);
            child.set_label(l, barrier);
            invoke = false;
        }

        /** GOTO l */
        @Override
        public void jump(long l) {
            if (clean_jumps) {
                s_empty();
            }
            checkLabel(l);
            child.jump(l);
        }

        /** IF (s0.t # 0) GOTO l ; pop */
        @Override
        public void if_true(CGType t, long l, int f) {
            s_pop(tst(t));
            if (clean_jumps) {
                s_empty();
            }
            checkLabel(l);
            child.if_true(t, l, f);
        }

        /** IF (s0.t = 0) GOTO l ; pop */
        @Override
        public void if_false(CGType t, long l, int f) {
            s_pop(tst(t));
            if (clean_jumps) {
                s_empty();
            }
            checkLabel(l);
            child.if_false(t, l, f);
        }

        /** IF (s1.t op s0.t) GOTO l ; pop(2) */
        @Override
        public void if_compare(CGType t, CompareOp op, long l, int f) {
            s_pop(tst(t), ST.Match);
            if (clean_jumps) {
                s_empty();
            }
            checkLabel(l);
            child.if_compare(t, op, l, f);
        }

        /** "GOTO labels[s0.t] ; pop" with no range checking on s0.t */
        @Override
        public void case_jump(CGType t, long[] labels) {
            s_pop(tst(t));
            if (clean_jumps) {
                s_empty();
            }
            for (int i = 0; i <= labels.length - 1; i++) {
                checkLabel(labels[i]);
            }
            child.case_jump(t, labels);
        }

        /** Returns s0.t if the stack is non-empty, otherwise returns no value. */
        @Override
        public void exit_proc(CGType t) {
            if (t != CGType.Void) {
                s_pop(tst(t));
            }
            s_empty();
            child.exit_proc(t);
        }

        /*------------------------------------------------------------ load/store ---*/

        @Override
        public void load(Var v, long o, CGType t, CGType u) {
            checkVar(v);
            if (!legalLoad(t, u)) {
                putErr("illegal load conversion");
            }
            s_push(u);
            child.load(v, o, t, u);
        }

        @Override
        public void store(Var v, long o, CGType t, CGType u) {
            checkVar(v);
            if (!legalStore(t, u)) {
                putErr("illegal store conversion");
            }
            s_pop(tst(t));
            if (clean_stores) {
                s_empty();
            }
            child.store(v, o, t, u);
        }

        @Override
        public void load_address(Var v, long o) {
            checkVar(v);
            s_push(CGType.Addr);
            child.load_address(v, o);
        }

        @Override
        public void load_indirect(long o, CGType t, CGType u) {
            if (!legalLoad(t, u)) {
                putErr("illegal load conversion");
            }
            s_pop(ST.Addr);
            s_push(u);
            child.load_indirect(o, t, u);
        }

        @Override
        public void store_indirect(long o, CGType t, CGType u) {
            if (!legalStore(t, u)) {
                putErr("illegal store conversion");
            }
            s_pop(tst(t), ST.Addr);
            if (clean_stores) {
                s_empty();
            }
            child.store_indirect(o, t, u);
        }

        /*-------------------------------------------------------------- literals ---*/

        /** push ; s0.A := a */
        @Override
        public void load_nil() {
            s_push(CGType.Addr);
            child.load_nil();
        }

        /** push ; s0.t := i */
        @Override
        public void load_integer(CGType t, int[] i) {
            s_push(t);
            child.load_integer(t, i);
        }

        /** push ; s0.t := f */
        @Override
        public void load_float(CGType t, Target.Float f) {
            if (t != FType[f.pre.ordinal()]) {
                putErr("floating-point literal doesn't match type");
            }
            s_push(t);
            child.load_float(t, f);
        }

        /*------------------------------------------------------------ arithmetic ---*/

        /** s1.lhs := s1.rhs 'op' s0.rhs ; pop */
        private void binary(CGType lhs, CGType rhs) {
            s_pop(tst(rhs), ST.Match);
            s_push(lhs);
        }

        /** s1.lhs := 'op' (s1.rhs) */
        private void unary(CGType lhs, CGType rhs) {
            s_pop(tst(rhs));
            s_push(lhs);
        }

        /** s1.u := (s1.t op s0.t)  ; pop */
        @Override
        public void compare(CGType t, CGType u, CompareOp op) {
            binary(u, t);
            child.compare(t, u, op);
        }

        /** s1.t := s1.t + s0.t ; pop */
        @Override
        public void add(CGType t) {
            binary(t, t);
            child.add(t);
        }

        /** s1.t := s1.t - s0.t ; pop */
        @Override
        public void subtract(CGType t) {
            binary(t, t);
            child.subtract(t);
        }

        /** s1.t := s1.t * s0.t ; pop */
        @Override
        public void multiply(CGType t) {
            binary(t, t);
            child.multiply(t);
        }

        /** s1.t := s1.t / s0.t ; pop */
        @Override
        public void divide(CGType t) {
            binary(t, t);
            child.divide(t);
        }

        /** s1.t := s1.t DIV s0.t ; pop */
        @Override
        public void div(CGType t, Sign a, Sign b) {
            s_pop(tst(t), ST.Match);
            s_push(t);
            child.div(t, a, b);
        }

        /** s1.t := s1.t MOD s0.t ; pop */
        @Override
        public void mod(CGType t, Sign a, Sign b) {
            s_pop(tst(t), ST.Match);
            s_push(t);
            child.mod(t, a, b);
        }

        /** s0.t := - s0.t */
        @Override
        public void negate(CGType t) {
            unary(t, t);
            child.negate(t);
        }

        /** s0.t := ABS (s0.t) (noop on Words) */
        @Override
        public void abs(CGType t) {
            unary(t, t);
            child.abs(t);
        }

        /** s1.t := MAX (s1.t, s0.t) ; pop */
        @Override
        public void max(CGType t) {
            binary(t, t);
            child.max(t);
        }

        /** s1.t := MIN (s1.t, s0.t) ; pop */
        @Override
        public void min(CGType t) {
            binary(t, t);
            child.min(t);
        }

        /** s0.u := op (s0.t) */
        @Override
        public void cvt_int(CGType t, CGType u, ConvertOp op) {
            unary(u, t);
            child.cvt_int(t, u, op);
        }

        /** s0.u := FLOAT (s0.t, u) */
        @Override
        public void cvt_float(CGType t, CGType u) {
            unary(u, t);
            child.cvt_float(t, u);
        }

        /*------------------------------------------------------------------ sets ---*/

        /** s2.B := s1.B + s0.B ; pop(3) */
        @Override
        public void set_union(long s) {
            s_pop(ST.Addr, ST.Addr, ST.Addr);
            child.set_union(s);
        }

        /** s2.B := s1.B - s0.B ; pop(3) */
        @Override
        public void set_difference(long s) {
            s_pop(ST.Addr, ST.Addr, ST.Addr);
            child.set_difference(s);
        }

        /** s2.B := s1.B * s0.B ; pop(3) */
        @Override
        public void set_intersection(long s) {
            s_pop(ST.Addr, ST.Addr, ST.Addr);
            child.set_intersection(s);
        }

        /** s2.B := s1.B / s0.B ; pop(3) */
        @Override
        public void set_sym_difference(long s) {
            s_pop(ST.Addr, ST.Addr, ST.Addr);
            child.set_sym_difference(s);
        }

        /** s1.t := (s0.t IN s1.B) ; pop */
        @Override
        public void set_member(long s, CGType t) {
            s_pop(tst(t), ST.Addr);
            s_push(t);
            child.set_member(s, t);
        }

        /** s1.t := (s1.B op s0.B)  ; pop */
        @Override
        public void set_compare(long s, CompareOp op, CGType t) {
            binary(t, CGType.Addr);
            child.set_compare(s, op, t);
        }

        /** s2.A [s1.t .. s0.t] := 1's; pop(3) */
        @Override
        public void set_range(long s, CGType t) {
            s_pop(tst(t), tst(t), ST.Addr);
            child.set_range(s, t);
        }

        /** s1.A [s0.t] := 1; pop(2) */
        @Override
        public void set_singleton(long s, CGType t) {
            s_pop(tst(t), ST.Addr);
            child.set_singleton(s, t);
        }

        /*------------------------------------------------- Word.T bit operations ---*/

        /** s0.t := Word.Not (s0.t) */
        @Override
        public void not(CGType t) {
            unary(t, t);
            child.not(t);
        }

        /** s1.t := Word.And (s1.t, s0.t) ; pop */
        @Override
        public void and(CGType t) {
            binary(t, t);
            child.and(t);
        }

        /** s1.t := Word.Or  (s1.t, s0.t) ; pop */
        @Override
        public void or(CGType t) {
            binary(t, t);
            child.or(t);
        }

        /** s1.t := Word.Xor (s1.t, s0.t) ; pop */
        @Override
        public void xor(CGType t) {
            binary(t, t);
            child.xor(t);
        }

        /** s1.t := Word.Shift  (s1.t, s0.t) ; pop */
        @Override
        public void shift(CGType t) {
            s_pop(tst(target.Integer.cg_type), tst(t));
            s_push(t);
            child.shift(t);
        }

        /** s1.t := Word.Shift  (s1.t, s0.t) ; pop */
        @Override
        public void shift_left(CGType t) {
            s_pop(tst(target.Integer.cg_type), tst(t));
            s_push(t);
            child.shift_left(t);
        }

        /** s1.t := Word.Shift  (s1.t, -s0.t) ; pop */
        @Override
        public void shift_right(CGType t) {
            s_pop(tst(target.Integer.cg_type), tst(t));
            s_push(t);
            child.shift_right(t);
        }

        /** s1.t := Word.Rotate (s1.t, s0.t) ; pop */
        @Override
        public void rotate(CGType t) {
            s_pop(tst(target.Integer.cg_type), tst(t));
            s_push(t);
            child.rotate(t);
        }

        /** s1.t := Word.Rotate (s1.t, s0.t) ; pop */
        @Override
        public void rotate_left(CGType t) {
            s_pop(tst(target.Integer.cg_type), tst(t));
            s_push(t);
            child.rotate_left(t);
        }

        /** s1.t := Word.Rotate (s1.t, -s0.t) ; pop */
        @Override
        public void rotate_right(CGType t) {
            s_pop(tst(target.Integer.cg_type), tst(t));
            s_push(t);
            child.rotate_right(t);
        }

        /** s0.I64 := s0.I32;  IF sign THEN Sign-extend s0; */
        @Override
        public void widen(boolean sign) {
            s_pop(ST.Int32);
            s_push(CGType.Int64);
            child.widen(sign);
        }

        /** s0.I32 := Word.And (s0.I64, 16_ffffffff); */
        @Override
        public void chop() {
            s_pop(ST.Int64);
            s_push(CGType.Int32);
            child.chop();
        }

        /**
         * s2.t := Word.Extract(s2.t, s1.t, s0.t);
         * IF sign THEN SignExtend s2 END; pop(2)
         */
        @Override
        public void extract(CGType t, boolean sign) {
            s_pop(tst(target.Integer.cg_type),
                  tst(target.Integer.cg_type),
                  tst(t));
            s_push(t);
            child.extract(t, sign);
        }

        /**
         * s1.t := Word.Extract(s1.t, s0.t, n);
         * IF sign THEN SignExtend s1 END; pop(1)
         */
        @Override
        public void extract_n(CGType t, boolean sign, long n) {
            s_pop(tst(target.Integer.cg_type),
                  tst(t));
            s_push(t);
            child.extract_n(t, sign, n);
        }

        /**
         * s0.t := Word.Extract(s0.t, m, n);
         * IF sign THEN SignExtend s0 END;
         */
        @Override
        public void extract_mn(CGType t, boolean sign, long m, long n) {
            unary(t, t);
            child.extract_mn(t, sign, m, n);
        }

        /** s3.t := Word.Insert (s3.t, s2.t, s1.t, s0.t) ; pop(3) */
        @Override
        public void insert(CGType t) {
            s_pop(tst(target.Integer.cg_type),
                  tst(target.Integer.cg_type),
                  tst(t), tst(t));
            s_push(t);
            child.insert(t);
        }

        /** s2.t := Word.Insert (s2.t, s1.t, s0.t, n) ; pop(2) */
        @Override
        public void insert_n(CGType t, long n) {
            s_pop(tst(target.Integer.cg_type), tst(t), tst(t));
            s_push(t);
            child.insert_n(t, n);
        }

        /** s1.t := Word.Insert (s1.t, s0.t, m, n) ; pop(2) */
        @Override
        public void insert_mn(CGType t, long m, long n) {
            s_pop(tst(t), tst(t));
            s_push(t);
            child.insert_mn(t, m, n);
        }

        /*------------------------------------------------ misc. stack/memory ops ---*/

        /** tmp := s1 ; s1 := s0 ; s0 := tmp */
        @Override
        public void swap(CGType a, CGType b) {
            s_pop(tst(b), tst(a));
            s_push(b);
            s_push(a);
            child.swap(a, b);
        }

        /** pop(1) (i.e. discard s0) */
        @Override
        public void pop(CGType t) {
            s_pop(tst(t));
            child.pop(t);
        }

        /** Mem[s2.A:s0.t] := Mem[s1.A:s0.t]; pop(3) */
        @Override
        public void copy_n(CGType u, CGType t, boolean overlap) {
            s_pop(tst(u), ST.Addr, ST.Addr);
            child.copy_n(u, t, overlap);
        }

        /** Mem[s2.A:sz] := Mem[s1.A:sz]; pop(2) */
        @Override
        public void copy(long n, CGType t, boolean overlap) {
            s_pop(ST.Addr, ST.Addr);
            child.copy(n, t, overlap);
        }

        /** Mem[s1.A:s0.u] := 0; pop(2) */
        @Override
        public void zero_n(CGType u, CGType t) {
            s_pop(tst(u), ST.Addr);
            child.zero_n(u, t);
        }

        /** Mem[s1.A:sz] := 0; pop(1) */
        @Override
        public void zero(long n, CGType t) {
            s_pop(ST.Addr);
            child.zero(n, t);
        }

        /*----------------------------------------------------------- conversions ---*/

        /** s0.to := LOOPHOLE(s0.from, to) */
        @Override
        public void loophole(CGType from, CGType two) {
            s_pop(tst(from));
            s_push(two);
            child.loophole(from, two);
        }

        /*------------------------------------------------ traps & runtime checks ---*/

        @Override
        public void abort(RuntimeError code) {
            // self.s_empty ();
            // 1) This is very difficult for producers to ensure.
            // 2) None of 4 code generators make any access to the operand stack,
            //    for the abort operation.
            child.abort(code);
        }

        /** IF (s0.A = NIL) THEN abort(code) */
        @Override
        public void check_nil(RuntimeError code) {
            s_pop(ST.Addr);
            s_push(CGType.Addr);
            child.check_nil(code);
        }

        /** IF (s0.t &lt; i) THEN abort(code) */
        @Override
        public void check_lo(CGType t, int[] i, RuntimeError code) {
            unary(t, t);
            child.check_lo(t, i, code);
        }

        /** IF (i &lt; s0.t) THEN abort(code) */
        @Override
        public void check_hi(CGType t, int[] i, RuntimeError code) {
            unary(t, t);
            child.check_hi(t, i, code);
        }

        /** IF (s0.t &lt; a) OR (b &lt; s0.t) THEN abort(code) */
        @Override
        public void check_range(CGType t, int[] a, int[] b, RuntimeError code) {
            unary(t, t);
            child.check_range(t, a, b, code);
        }

        /** IF NOT (0 &lt;= s1.t &lt; s0.t) THEN abort(code) END; pop */
        @Override
        public void check_index(CGType t, RuntimeError code) {
            binary(t, t);
            child.check_index(t, code);
        }

        /** IF (s0.I # s1.I) THEN abort(code);  Pop (2) */
        @Override
        public void check_eq(CGType t, RuntimeError code) {
            s_pop(tst(t), tst(t));
            child.check_eq(t, code);
        }

        /*---------------------------------------------------- address arithmetic ---*/

        /** s0.A := s0.A + i */
        @Override
        public void add_offset(long i) {
            s_pop(ST.Addr);
            s_push(CGType.Addr);
            child.add_offset(i);
        }

        /** s1.A := s1.A + s0.t * size ; pop */
        @Override
        public void index_address(CGType t, long size) {
            s_pop(tst(t), ST.Addr);
            s_push(CGType.Addr);
            child.index_address(t, size);
        }

        /*------------------------------------------------------- procedure calls ---*/

        /** begin a procedure call to a procedure at static level 'lev'. */
        @Override
        public void start_call_direct(Proc p, long lev, CGType t) {
            checkProc(p);
            if (clean_jumps) {
                s_empty();
            }
            if ((call_count > 0) && (!nested_calls)) {
                putErr("nested procedure call");
            }
            call_count++;
            child.start_call_direct(p, lev, t);
        }

        /** begin a procedure call to a procedure at static level 'lev'. */
        @Override
        public void start_call_indirect(CGType t, Target.CallingConvention cc) {
            if (clean_jumps) {
                s_empty();
            }
            if ((call_count > 0) && (!nested_calls)) {
                putErr("nested procedure call");
            }
            call_count++;
            child.start_call_indirect(t, cc);
        }

        /** pop s0 and make it the "next" parameter in the current call */
        @Override
        public void pop_param(CGType t) {
            if (call_count <= 0) {
                putErr("missing start_call");
            }
            s_pop(tst(t));
            if (clean_stores) {
                s_empty();
            }
            child.pop_param(t);
        }

        /** pop s0 and make it the "next" parameter in the current call */
        @Override
        public void pop_struct(int t, long s, long a) {
            if (call_count <= 0) {
                putErr("missing start_call");
            }
            s_pop(ST.Addr);
            if (clean_stores) {
                s_empty();
            }
            child.pop_struct(t, s, a);
        }

        @Override
        public void pop_static_link() {
            if (call_count <= 0) {
                putErr("missing start_call");
            }
            s_pop(ST.Addr);
            if (clean_stores) {
                s_empty();
            }
            child.pop_static_link();
        }

        private void doCall() {
            if (clean_jumps) {
                s_empty();
            }
            if (call_count > 0) {
                call_count--;
            } else {
                putErr("missing start_call");
            }
        }

        /**
         * call the procedure identified by block b.  The procedure
         * returns a value of type t.
         */
        @Override
        public void call_direct(Proc p, CGType t) {
            checkProc(p);
            doCall();
            if (t != CGType.Void) {
                s_push(t);
            }
            child.call_direct(p, t);
        }

        /**
         * call the procedure whose address is in s0.A and pop s0.  The
         * procedure returns a value of type t.
         */
        @Override
        public void call_indirect(CGType t, Target.CallingConvention cc) {
            s_pop(ST.Addr);
            doCall();
            if (t != CGType.Void) {
                s_push(t);
            }
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

        /**
         * call the procedure identified by block b.  The procedure
         * returns a value of type t.
         */
        @Override
        public void invoke_direct(Proc p, CGType t, long next, long handler) {
            checkProc(p);
            doCall();
            if (t != CGType.Void) {
                s_push(t);
            }
            child.invoke_direct(p, t, next, handler);
            invoke = true;
        }

        /**
         * call the procedure whose address is in s0.A and pop s0.  The
         * procedure returns a value of type t.
         */
        @Override
        public void invoke_indirect(CGType t, Target.CallingConvention cc, long next, long handler) {
            s_pop(ST.Addr);
            doCall();
            if (t != CGType.Void) {
                s_push(t);
            }
            child.invoke_indirect(t, cc, next, handler);
            invoke = true;
        }

        @Override
        public void landing_pad(CGType t, long handler, int[] catches) {
            s_push(t);
            child.landing_pad(t, handler, catches);
        }

        /*------------------------------------------- procedure and closure types ---*/

        /** push; s0.A := ADDR (p's body) */
        @Override
        public void load_procedure(Proc p) {
            checkProc(p);
            s_push(CGType.Addr);
            child.load_procedure(p);
        }

        /** push; s0.A := (static link needed to call p, NIL for top-level procs) */
        @Override
        public void load_static_link(Proc p) {
            checkProc(p);
            s_push(CGType.Addr);
            child.load_static_link(p);
        }

        /*--------------------------------------------------------------- atomics ---*/

        @Override
        public void store_ordered(CGType t, CGType u, MemoryOrder order) {
            if (!legalStore(t, u)) {
                putErr("illegal store conversion");
            }
            switch (order) {
                case Acquire, AcquireRelease -> putErr("illegal store memory order");
                default -> { } // ok
            }
            s_pop(tst(t), ST.Addr);
            if (clean_stores) {
                s_empty();
            }
            child.store_ordered(t, u, order);
        }

        @Override
        public void load_ordered(CGType t, CGType u, MemoryOrder order) {
            if (!legalLoad(t, u)) {
                putErr("illegal load conversion");
            }
            switch (order) {
                case Release, AcquireRelease -> putErr("illegal load memory order");
                default -> { } // ok
            }
            s_pop(ST.Addr);
            s_push(u);
            child.load_ordered(t, u, order);
        }

        @Override
        public void exchange(CGType t, CGType u, MemoryOrder order) {
            if (!legalStore(u, t)) {
                putErr("illegal store conversion");
            }
            if (!legalLoad(t, u)) {
                putErr("illegal load conversion");
            }
            s_pop(tst(u), ST.Addr);
            if (clean_stores) {
                s_empty();
            }
            s_push(u);
            child.exchange(t, u, order);
        }

        @Override
        public void compare_exchange(CGType t, CGType u, CGType r,
                                     MemoryOrder success, MemoryOrder failure) {
            if (!legalStore(u, t)) {
                putErr("illegal store conversion");
            }
            if (!legalLoad(t, u)) {
                putErr("illegal load conversion");
            }
            switch (failure) {
                case Release, AcquireRelease -> putErr("illegal load memory order");
                default -> { } // ok
            }
            if (failure.compareTo(success) > 0) {
                putErr("failure stronger than success");
            }
            s_pop(tst(u), ST.Addr, ST.Addr);
            //                 ^ This was T_to_ST [u], which fails to account
            //                   for the fact that this parameter is VAR, and
            //                   thus passed by reference.  See Atomic.ig,
            //                   PROCEDURE Compare_Swap.  The front end is putting
            //                   a T.Addr on the stack here, but all of the
            //                   backends and the definition of compare_exchange
            //                   in M3CG_Ops seem to suggest it is passed by value.
            //                   Then the local value is assigned-to, which would
            //                   lose the assignment.
            //                   I think this is the correct check to make here,
            //                   but the back ends need reviewing.
            //                   2016-10-07, rodney.m.bates@acm.org.
            if (clean_stores) {
                s_empty();
            }
            s_push(r); // The BOOLEAN result
            child.compare_exchange(t, u, r, success, failure);
        }

        @Override
        public void fence(MemoryOrder order) {
            child.fence(order);
        }

        @Override
        public void fetch_and_op(AtomicOp op, CGType t, CGType u, MemoryOrder order) {
            if (!legalStore(u, t)) {
                putErr("illegal store conversion");
            }
            if (!legalLoad(t, u)) {
                putErr("illegal load conversion");
            }
            s_pop(tst(u), ST.Addr);
            if (clean_stores) {
                s_empty();
            }
            s_push(u);
            child.fetch_and_op(op, t, u, order);
        }
    }
}
