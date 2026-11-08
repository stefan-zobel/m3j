// Java port of CM3's m3-sys/m3middle/src/M3CG_Wr.i3 and M3CG_Wr.m3.
// Original: Copyright (C) 1993, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.middle;

import java.io.Writer;
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

/**
 * A code generator that writes its calls as readable ASCII.
 *
 * <p>Port: the procedures of the module that take the U as their first
 * parameter are methods of {@link U}. Wr.T is a {@link Writer} (see
 * {@link M3Buf}); Wr.EOL is the line separator of the host. M3ID.Put needs
 * the M3ID state, which {@link #New} takes. IntRefTbl.T is a {@link HashMap}.
 */
public final class M3CG_Wr {
    private M3CG_Wr() {}

    /** Wr.EOL. */
    static final String EOL = System.lineSeparator();

    private static final class WrVar extends Var {
        final long tag;

        WrVar(long tag) {
            this.tag = tag;
        }
    }

    private static final class WrProc extends Proc {
        final long tag;

        WrProc(long tag) {
            this.tag = tag;
        }
    }

    private static final class RuntimeHook {
        final int name;
        Proc proc = null;

        RuntimeHook(int name) {
            this.name = name;
        }
    }

    /**
     * Returns a fresh, initialized code generator that writes its
     * calls as readable ASCII on 'wr'.
     */
    public static M3CG.T New(Writer output, M3ID.State ids) {
        M3Buf.T mbuf = M3Buf.New();
        M3Buf.attachDrain(mbuf, output);
        U u = new U(ids);
        u.wr = output;
        u.buf = mbuf;
        u.buf_len = 0;
        u.runtime = new HashMap<>(20);
        return u;
    }

    private static final class U extends M3CG.T {
        final M3ID.State ids;
        Writer wr = null;
        M3Buf.T buf = null;
        long buf_len = 0;
        /** Name -&gt; RuntimeHook */
        Map<Integer, RuntimeHook> runtime = null;
        long next_label_id = 1;
        long next_var = 1;
        long next_proc = 1;
        long next_scope = 1;

        U(M3ID.State ids) {
            this.ids = ids;
        }

        /*------------------------------------------------------------------- I/O ---*/

        private void nl() {
            outT(EOL);
        }

        private void cmd(String cmd) {
            int len = cmd.length();
            outC('\t');
            outT(cmd);
            outC('\t');
            if (len < 8) {
                outC('\t');
            }

            // FOR i := 0 TO 14-len DO OutC (u, ' ') END;
            // OutC (u, ' ');
            // OutC (u, ' ');
        }

        private void zName(int n) {
            outC(' ');
            if (n == M3ID.NoID) {
                outC('*');
            } else {
                outN(n);
            }
        }

        private void vName(Var v) {
            if (v == null) {
                outT(" *");
            } else if (v instanceof WrVar x) {
                outT(" v.");
                outI(x.tag);
            } else {
                outT(" v.???");
            }
        }

        private void pName(Proc p) {
            if (p == null) {
                outT(" *");
            } else if (p instanceof WrProc x) {
                outT(" p.");
                outI(x.tag);
            } else {
                outT(" p.???");
            }
        }

        private void tName(CGType t) {
            outC(' ');
            outT(Target.TypeNamesDotted[t.ordinal()]);
        }

        private static final String[] FType = {" R ", " L ", " X "};

        private void flt(Target.Float f) {
            char[] buf = new char[64 + 1];
            int len = TFloat.toChars(f, buf);
            outT(FType[TFloat.prec(f).ordinal()]);
            outS(buf, 0, len);
        }

        private void bool(boolean b) {
            outC(' ');
            outC(b ? 'T' : 'F');
        }

        private void lab(long i) {
            outC(' ');
            if (i == M3CG.No_label) {
                outC('*');
            } else {
                outT("L.");
                outI(i);
            }
        }

        private void tipe(int t) {
            outT(" ");
            outI(t);
        }

        /** Int (u, i); Port: "int" is a Java keyword. */
        private void int_(long i) {
            outC(' ');
            outI(i);
        }

        private void tInt(int[] i) {
            char[] buf = new char[TInt.Size + 1];
            int len = TInt.toChars(i, buf);
            outC(' ');
            outS(buf, 0, len);
        }

        private void bInt(long i) {
            int_(i);
            // since the reader doesn't know how to read 'bytes+bits'
        }

        // Port, omitted: the BInt that writes 'bytes+bits' (commented out in
        // the original).

        /** VanillaChars = SET OF CHAR { ' ', '!', '#' .. '[', ']' .. '~' } */
        private static boolean vanilla(char c) {
            return c == ' ' || c == '!' || ('#' <= c && c <= '[') || (']' <= c && c <= '~');
        }

        private static final char[] Digits = {'0', '1', '2', '3', '4', '5', '6', '7'};

        private void txt(String t) {
            char c;
            outC(' ');
            if (t == null) {
                outC('*');
                return;
            }
            outC('"');
            for (int i = 0; i <= t.length() - 1; i++) {
                c = t.charAt(i);
                if (vanilla(c)) {
                    outC(c);
                } else {
                    outC('\\');
                    outC(Digits[c / 64]);
                    outC(Digits[c % 64 / 8]);
                    outC(Digits[c % 8]);
                }
            }
            outC('"');
        }

        /*--------------------------------------------------------- low level I/O ---*/

        private void flush() {
            M3Buf.flush(buf, wr);
            buf_len = 0;
        }

        private void outC(char c) {
            M3Buf.putChar(buf, c);
            buf_len++;
            if (buf_len >= 0xF000) {
                flush();
            }
        }

        private void outT(String txt) {
            M3Buf.putText(buf, txt);
            buf_len += txt.length();
            if (buf_len >= 0xF000) {
                flush();
            }
        }

        private void outN(int n) {
            M3ID.put(ids, buf, n);
            buf_len += 10; // we don't really care if it's accurate
            if (buf_len >= 0xF000) {
                flush();
            }
        }

        private void outS(char[] b, int start, int n) {
            M3Buf.putSub(buf, b, start, n);
            buf_len += n;
            if (buf_len >= 0xF000) {
                flush();
            }
        }

        private void outI(long i) {
            M3Buf.putInt(buf, i);
            buf_len += 4; // we don't really care if it's accurate
            if (buf_len >= 0xF000) {
                flush();
            }
        }

        /*----------------------------------------------------------- ID counters ---*/

        @Override
        public long next_label(long n) {
            long x = next_label_id;
            next_label_id += n;
            return x;
        }

        /*------------------------------------------------ READONLY configuration ---*/

        @Override
        public void set_error_handler(M3CG_Ops.ErrorHandler p) {
            // skip -- we don't generate any errors
        }

        /*----------------------------------------------------- compilation units ---*/

        @Override
        public void begin_unit(long optimize) {
            // called before any other method to initialize the compilation unit
            cmd("begin_unit");
            int_(optimize);
            nl();
        }

        @Override
        public void end_unit() {
            // called after all other methods to finalize the unit and write the
            // resulting object
            cmd("end_unit");
            nl();
            flush();
        }

        @Override
        public void import_unit(int n) {
            // note that the current compilation unit imports the interface 'n'
            cmd("import_unit");
            zName(n);
            nl();
        }

        @Override
        public void export_unit(int n) {
            // note that the current compilation unit exports the interface 'n'
            cmd("export_unit");
            zName(n);
            nl();
        }

        /*------------------------------------------------ debugging line numbers ---*/

        @Override
        public void set_source_file(String file) {
            // Sets the current source file name.  Subsequent statements
            // and expressions are associated with this source location.
            outT("\t\t\t\t\t-----FILE ");
            outT(file);
            outT("  -----");
            outT(EOL);
        }

        @Override
        public void set_source_line(long line) {
            // Sets the current source line number.  Subsequent statements
            // and expressions are associated with this source location.
            outT("\t\t\t\t\t-----LINE");
            int_(line);
            outT("  -----");
            outT(EOL);
        }

        /*------------------------------------------- debugging type declarations ---*/

        @Override
        public void declare_typename(int t, int n) {
            cmd("declare_typename");
            tipe(t);
            zName(n);
            nl();
        }

        @Override
        public void declare_array(int t, int index, int elt, long s, int element_typename) {
            cmd("declare_array");
            tipe(t);
            tipe(index);
            tipe(elt);
            bInt(s);
            nl();
        }

        @Override
        public void declare_open_array(int t, int elt, long s, int element_typename) {
            cmd("declare_open_array");
            tipe(t);
            tipe(elt);
            bInt(s);
            nl();
        }

        @Override
        public void declare_enum(int t, long n_elts, long s) {
            cmd("declare_enum");
            tipe(t);
            int_(n_elts);
            bInt(s);
            nl();
        }

        @Override
        public void declare_enum_elt(int n) {
            cmd("declare_enum_elt");
            zName(n);
            nl();
        }

        @Override
        public void declare_packed(int t, long s, int base, int base_typename) {
            cmd("declare_packed");
            tipe(t);
            bInt(s);
            tipe(base);
            nl();
        }

        @Override
        public void declare_record(int t, long s, long n_fields) {
            cmd("declare_record");
            tipe(t);
            bInt(s);
            int_(n_fields);
            nl();
        }

        @Override
        public void declare_field(int n, long o, long s, int t, int typename) {
            cmd("declare_field");
            zName(n);
            bInt(o);
            bInt(s);
            tipe(t);
            nl();
        }

        @Override
        public void declare_set(int t, int domain, long s, int domain_typename) {
            cmd("declare_set");
            tipe(t);
            tipe(domain);
            bInt(s);
            nl();
        }

        @Override
        public void declare_subrange(int t, int domain, int[] min, int[] max, long s, int domain_typename) {
            cmd("declare_subrange");
            tipe(t);
            tipe(domain);
            tInt(min);
            tInt(max);
            bInt(s);
            nl();
        }

        @Override
        public void declare_pointer(int t, int target, String brand, boolean traced, int target_typename) {
            cmd("declare_pointer");
            tipe(t);
            tipe(target);
            txt(brand);
            bool(traced);
            nl();
        }

        @Override
        public void declare_indirect(int t, int target, int target_typename) {
            cmd("declare_indirect");
            tipe(t);
            tipe(target);
            // TODO target_typename but there are no downstream users
            nl();
        }

        @Override
        public void declare_proctype(int t, long n_formals, int result, long n_raises, Target.CallingConvention cc, int result_typename) {
            cmd("declare_proctype");
            tipe(t);
            int_(n_formals);
            tipe(result);
            int_(n_raises);
            int_(cc.m3cg_id);
            // TODO result_typename but it is not used downstream
            nl();
        }

        @Override
        public void declare_formal(int n, int t, int typename) {
            cmd("declare_formal");
            zName(n);
            tipe(t);
            // TODO typename but it is not used downstream and can be omitted indefinitely
            nl();
        }

        @Override
        public void declare_raises(int n) {
            cmd("declare_raises");
            zName(n);
            nl();
        }

        @Override
        public void declare_object(int t, int super_, String brand, boolean traced, long n_fields, long n_methods, long field_size, long field_offset, long method_offset, int super_typename) {
            cmd("declare_object");
            tipe(t);
            tipe(super_);
            txt(brand);
            bool(traced);
            int_(n_fields);
            int_(n_methods);
            bInt(field_size);
            int_(field_offset);
            int_(method_offset);
            nl();
        }

        @Override
        public void declare_method(int n, int signature) {
            cmd("declare_method");
            zName(n);
            tipe(signature);
            nl();
        }

        @Override
        public void declare_opaque(int t, int super_) {
            cmd("declare_opaque");
            tipe(t);
            tipe(super_);
            nl();
        }

        @Override
        public void reveal_opaque(int lhs, int rhs) {
            cmd("reveal_opaque");
            tipe(lhs);
            tipe(rhs);
            nl();
        }

        @Override
        public void declare_exception(int n, int arg_type, boolean raise_proc, Var base, long offset) {
            cmd("declare_exception");
            zName(n);
            tipe(arg_type);
            bool(raise_proc);
            vName(base);
            int_(offset);
            nl();
        }

        @Override
        public void widechar_size(long size) {
            cmd("widechar_size");
            int_(size);
            nl();
        }

        private RuntimeHook getRuntimeHook(int n) {
            RuntimeHook e = runtime.get(n);
            if (e == null) {
                e = new RuntimeHook(n);
                runtime.put(n, e);
            }
            return e;
        }

        @Override
        public void set_runtime_proc(int n, Proc p) {
            RuntimeHook e = getRuntimeHook(n);
            e.proc = p;
            cmd("set_runtime_proc");
            zName(n);
            pName(p);
            nl();
        }

        private Var newVar() {
            Var v = new WrVar(next_var);
            next_var++;
            return v;
        }

        @Override
        public Var import_global(int n, long s, long a, CGType t, int m3t, int typename) {
            var v = newVar();
            cmd("import_global");
            zName(n);
            int_(s);
            int_(a);
            tName(t);
            tipe(m3t);
            vName(v);
            nl();
            // TODO typename
            return v;
        }

        @Override
        public Var declare_segment(int n, int m3t, boolean is_const) {
            var v = newVar();
            cmd("declare_segment");
            zName(n);
            tipe(m3t);
            bool(is_const);
            vName(v);
            nl();
            return v;
        }

        @Override
        public void bind_segment(Var seg, long s, long a, CGType t, boolean exported, boolean inited) {
            cmd("bind_segment");
            vName(seg);
            int_(s);
            int_(a);
            tName(t);
            bool(exported);
            bool(inited);
            nl();
        }

        @Override
        public Var declare_global(int n, long s, long a, CGType t, int m3t, boolean exported, boolean inited, int typename) {
            var v = newVar();
            cmd("declare_global");
            zName(n);
            int_(s);
            int_(a);
            tName(t);
            tipe(m3t);
            bool(exported);
            bool(inited);
            vName(v);
            nl();
            return v;
        }

        @Override
        public Var declare_constant(int n, long s, long a, CGType t, int m3t, boolean exported, boolean inited, int typename) {
            var v = newVar();
            cmd("declare_constant");
            zName(n);
            int_(s);
            int_(a);
            tName(t);
            tipe(m3t);
            bool(exported);
            bool(inited);
            vName(v);
            nl();
            return v;
        }

        @Override
        public Var declare_local(int n, long s, long a, CGType t, int m3t, boolean in_memory, boolean up_level, int f, int typename) {
            var v = newVar();
            cmd("declare_local");
            zName(n);
            int_(s);
            int_(a);
            tName(t);
            tipe(m3t);
            bool(in_memory);
            bool(up_level);
            int_(f);
            vName(v);
            nl();
            return v;
        }

        @Override
        public Var declare_param(int n, long s, long a, CGType t, int m3t, boolean in_memory, boolean up_level, int f, int typename) {
            var v = newVar();
            cmd("declare_param");
            zName(n);
            int_(s);
            int_(a);
            tName(t);
            tipe(m3t);
            bool(in_memory);
            bool(up_level);
            int_(f);
            vName(v);
            nl();
            // TODO typename but it is not used downstream and can be omitted indefinitely
            return v;
        }

        @Override
        public Var declare_temp(long s, long a, CGType t, boolean in_memory, int typename) {
            var v = newVar();
            cmd("declare_temp");
            int_(s);
            int_(a);
            tName(t);
            bool(in_memory);
            vName(v);
            nl();
            return v;
        }

        @Override
        public void free_temp(Var v) {
            cmd("free_temp");
            vName(v);
            nl();
        }

        /*---------------------------------------- static variable initialization ---*/

        @Override
        public void begin_init(Var v) {
            cmd("begin_init");
            vName(v);
            nl();
        }

        @Override
        public void end_init(Var v) {
            cmd("end_init");
            vName(v);
            nl();
        }

        @Override
        public void init_int(long o, int[] value, CGType t) {
            cmd("init_int");
            int_(o);
            tInt(value);
            tName(t);
            nl();
        }

        @Override
        public void init_proc(long o, Proc value) {
            cmd("init_proc");
            int_(o);
            pName(value);
            nl();
        }

        @Override
        public void init_label(long o, long value) {
            cmd("init_label");
            int_(o);
            lab(value);
            nl();
        }

        @Override
        public void init_var(long o, Var value, long bias) {
            cmd("init_var");
            int_(o);
            vName(value);
            int_(bias);
            nl();
        }

        @Override
        public void init_offset(long o, Var value) {
            cmd("init_offset");
            int_(o);
            vName(value);
            nl();
        }

        @Override
        public void init_chars(long o, String value) {
            cmd("init_chars");
            int_(o);
            txt(value);
            nl();
        }

        @Override
        public void init_float(long o, Target.Float f) {
            cmd("init_float");
            int_(o);
            flt(f);
            nl();
        }

        private Proc newProc() {
            Proc p = new WrProc(next_proc);
            next_proc++;
            return p;
        }

        @Override
        public Proc import_procedure(int n, long n_params, CGType ret_type, Target.CallingConvention cc, int return_typeid, int return_typename) {
            var p = newProc();
            cmd("import_procedure");
            zName(n);
            int_(n_params);
            tName(ret_type);
            int_(cc.m3cg_id);
            pName(p);
            nl();
            // TODO return_typename but it not used
            return p;
        }

        @Override
        public Proc declare_procedure(int n, long n_params, CGType return_type, long lev, Target.CallingConvention cc, boolean exported, Proc parent, int return_typeid, int return_typename) {
            var p = newProc();
            cmd("declare_procedure");
            zName(n);
            int_(n_params);
            tName(return_type);
            int_(lev);
            int_(cc.m3cg_id);
            bool(exported);
            pName(parent);
            pName(p);
            nl();
            // TODO return_typeid, return_typename but it is not used downstream and can be omitted indefinitely
            return p;
        }

        @Override
        public void begin_procedure(Proc p) {
            cmd("begin_procedure");
            pName(p);
            nl();
        }

        @Override
        public void end_procedure(Proc p) {
            cmd("end_procedure");
            pName(p);
            nl();
        }

        @Override
        public void begin_block() {
            // marks the beginning of a nested anonymous block
            cmd("begin_block");
            nl();
        }

        @Override
        public void end_block() {
            // marks the ending of a nested anonymous block
            cmd("end_block");
            nl();
        }

        @Override
        public void note_procedure_origin(Proc p) {
            cmd("note_procedure_origin");
            pName(p);
            nl();
        }

        /*------------------------------------------------------------ statements ---*/

        @Override
        public void set_label(long l, boolean barrier) {
            // define 'l' to be at the current pc
            outT(".");
            lab(l);
            bool(barrier);
            nl();
        }

        @Override
        public void jump(long l) {
            // GOTO l
            cmd("jump");
            lab(l);
            nl();
        }

        @Override
        public void if_true(CGType t, long l, int f) {
            // IF (s0.t # 0) GOTO l ; pop
            cmd("if_true");
            tName(t);
            lab(l);
            int_(f);
            nl();
        }

        @Override
        public void if_false(CGType t, long l, int f) {
            // IF (s0.t = 0) GOTO l ; pop
            cmd("if_false");
            tName(t);
            lab(l);
            int_(f);
            nl();
        }

        @Override
        public void if_compare(CGType t, CompareOp op, long l, int f) {
            // IF (s1.t  op  s0.t) GOTO l ; pop(2)
            final String[] OpName = {"if_eq", "if_ne", "if_gt", "if_ge", "if_lt", "if_le"};
            cmd(OpName[op.ordinal()]);
            tName(t);
            lab(l);
            int_(f);
            nl();
        }

        @Override
        public void case_jump(CGType t, long[] labels) {
            // "GOTO labels[s0.t] ; pop" with no range checking on s0.t
            cmd("case_jump");
            tName(t);
            int_(labels.length);
            for (int i = 0; i < labels.length; i++) {
                lab(labels[i]);
            }
            nl();
        }

        @Override
        public void exit_proc(CGType t) {
            // Returns s0.t if the stack is non-empty, otherwise returns no value.
            cmd("exit_proc");
            tName(t);
            nl();
        }

        /*------------------------------------------------------------ load/store ---*/

        @Override
        public void load(Var v, long o, CGType t, CGType z) {
            cmd("load");
            vName(v);
            int_(o);
            tName(t);
            tName(z);
            nl();
        }

        @Override
        public void store(Var v, long o, CGType t, CGType z) {
            cmd("store");
            vName(v);
            int_(o);
            tName(t);
            tName(z);
            nl();
        }

        @Override
        public void load_address(Var v, long o) {
            cmd("load_address");
            vName(v);
            int_(o);
            nl();
        }

        @Override
        public void load_indirect(long o, CGType t, CGType z) {
            cmd("load_indirect");
            int_(o);
            tName(t);
            tName(z);
            nl();
        }

        @Override
        public void store_indirect(long o, CGType t, CGType z) {
            cmd("store_indirect");
            int_(o);
            tName(t);
            tName(z);
            nl();
        }

        /*-------------------------------------------------------------- literals ---*/

        @Override
        public void load_nil() {
            // push ; s0.A := a
            cmd("load_nil");
            nl();
        }

        @Override
        public void load_integer(CGType t, int[] i) {
            // push ; s0.t := i
            cmd("load_integer");
            tName(t);
            tInt(i);
            nl();
        }

        @Override
        public void load_float(CGType t, Target.Float f) {
            // push ; s0.t := f
            cmd("load_float");
            tName(t);
            flt(f);
            nl();
        }

        /*------------------------------------------------------------ arithmetic ---*/

        @Override
        public void compare(CGType t, CGType z, CompareOp op) {
            // s1.z := (s1.t = s0.t)  ; pop
            final String[] OpName = {"eq", "ne", "gt", "ge", "lt", "le"};
            cmd(OpName[op.ordinal()]);
            tName(t);
            tName(z);
            nl();
        }

        @Override
        public void add(CGType t) {
            // s1.t := s1.t + s0.t ; pop
            cmd("add");
            tName(t);
            nl();
        }

        @Override
        public void subtract(CGType t) {
            // s1.t := s1.t - s0.t ; pop
            cmd("subtract");
            tName(t);
            nl();
        }

        @Override
        public void multiply(CGType t) {
            // s1.t := s1.t * s0.t ; pop
            cmd("multiply");
            tName(t);
            nl();
        }

        @Override
        public void divide(CGType t) {
            // s1.t := s1.t / s0.t ; pop
            cmd("divide");
            tName(t);
            nl();
        }

        private static final String[] SignName = {" P", " N", " X"};

        @Override
        public void div(CGType t, Sign a, Sign b) {
            // s1.t := s1.t DIV s0.t ; pop
            cmd("div");
            tName(t);
            outT(SignName[a.ordinal()]);
            outT(SignName[b.ordinal()]);
            nl();
        }

        @Override
        public void mod(CGType t, Sign a, Sign b) {
            // s1.t := s1.t MOD s0.t ; pop
            cmd("mod");
            tName(t);
            outT(SignName[a.ordinal()]);
            outT(SignName[b.ordinal()]);
            nl();
        }

        @Override
        public void negate(CGType t) {
            // s0.t := - s0.t
            cmd("negate");
            tName(t);
            nl();
        }

        @Override
        public void abs(CGType t) {
            // s0.t := ABS (s0.t) (noop on Words)
            cmd("abs");
            tName(t);
            nl();
        }

        @Override
        public void max(CGType t) {
            // s1.t := MAX (s1.t, s0.t) ; pop
            cmd("max");
            tName(t);
            nl();
        }

        @Override
        public void min(CGType t) {
            // s1.t := MIN (s1.t, s0.t) ; pop
            cmd("min");
            tName(t);
            nl();
        }

        @Override
        public void cvt_int(CGType t, CGType x, ConvertOp op) {
            // s0.u := op (s0.t)
            final String[] OpName = {"round", "trunc", "floor", "ceiling"};
            cmd(OpName[op.ordinal()]);
            tName(t);
            tName(x);
            nl();
        }

        @Override
        public void cvt_float(CGType t, CGType x) {
            // s0.x := FLOAT (s0.t, x)
            cmd("cvt_float");
            tName(t);
            tName(x);
            nl();
        }

        /*------------------------------------------------------------------ sets ---*/

        @Override
        public void set_union(long s) {
            // s1.B := s1.B + s0.B ; pop
            cmd("set_union");
            int_(s);
            nl();
        }

        @Override
        public void set_difference(long s) {
            // s1.B := s1.B - s0.B ; pop
            cmd("set_difference");
            int_(s);
            nl();
        }

        @Override
        public void set_intersection(long s) {
            // s1.B := s1.B * s0.B ; pop
            cmd("set_intersection");
            int_(s);
            nl();
        }

        @Override
        public void set_sym_difference(long s) {
            // s1.B := s1.B / s0.B ; pop
            cmd("set_sym_difference");
            int_(s);
            nl();
        }

        @Override
        public void set_member(long s, CGType t) {
            // s1.t := (s0.t IN s1.B) ; pop
            cmd("set_member");
            int_(s);
            tName(t);
            nl();
        }

        @Override
        public void set_compare(long s, CompareOp op, CGType t) {
            // s1.t := (s1.B  op  s0.B)  ; pop
            final String[] OpName = {"set_eq", "set_ne", "set_gt", "set_ge", "set_lt", "set_le"};
            cmd(OpName[op.ordinal()]);
            int_(s);
            tName(t);
            nl();
        }

        @Override
        public void set_range(long s, CGType t) {
            // s2.A [s1.t .. s0.t] := 1's; pop(3)
            cmd("set_range");
            int_(s);
            tName(t);
            nl();
        }

        @Override
        public void set_singleton(long s, CGType t) {
            // s1.A [s0.t] := 1; pop(2)
            cmd("set_singleton");
            int_(s);
            tName(t);
            nl();
        }

        /*------------------------------------------------- Word.T bit operations ---*/

        @Override
        public void not(CGType t) {
            // s0.t := Word.Not (s0.t)
            cmd("not");
            tName(t);
            nl();
        }

        @Override
        public void and(CGType t) {
            // s1.t := Word.And (s1.t, s0.t) ; pop
            cmd("and");
            tName(t);
            nl();
        }

        @Override
        public void or(CGType t) {
            // s1.t := Word.Or  (s1.t, s0.t) ; pop
            cmd("or");
            tName(t);
            nl();
        }

        @Override
        public void xor(CGType t) {
            // s1.t := Word.Xor (s1.t, s0.t) ; pop
            cmd("xor");
            tName(t);
            nl();
        }

        @Override
        public void shift(CGType t) {
            // s1.t := Word.Shift  (s1.t, s0.t) ; pop
            cmd("shift");
            tName(t);
            nl();
        }

        @Override
        public void shift_left(CGType t) {
            // s1.t := Word.Shift  (s1.t, s0.t) ; pop
            cmd("shift_left");
            tName(t);
            nl();
        }

        @Override
        public void shift_right(CGType t) {
            // s1.t := Word.Shift  (s1.t, -s0.t) ; pop
            cmd("shift_right");
            tName(t);
            nl();
        }

        @Override
        public void rotate(CGType t) {
            // s1.r := Word.Rotate (s1.t, s0.t) ; pop
            cmd("rotate");
            tName(t);
            nl();
        }

        @Override
        public void rotate_left(CGType t) {
            // s1.t := Word.Rotate (s1.t, s0.t) ; pop
            cmd("rotate_left");
            tName(t);
            nl();
        }

        @Override
        public void rotate_right(CGType t) {
            // s1.t := Word.Rotate (s1.t, -s0.t) ; pop
            cmd("rotate_right");
            tName(t);
            nl();
        }

        @Override
        public void widen(boolean sign) {
            // s0.I64 := s0.I32;  IF sign THEN SignExtend s0;
            cmd("widen");
            bool(sign);
            nl();
        }

        @Override
        public void chop() {
            // s0.I32 := Word.And (s0.I64, 16_ffffffff);
            cmd("chop");
            nl();
        }

        @Override
        public void extract(CGType t, boolean sign) {
            // s2.t := Word.Extract(s2.t, s1.t, s0.t);
            // IF sign THEN SignExtend s2 END; pop(2)
            cmd("extract");
            tName(t);
            bool(sign);
            nl();
        }

        @Override
        public void extract_n(CGType t, boolean sign, long n) {
            // s1.t := Word.Extract(s1.t, s0.t, n);
            // IF sign THEN SignExtend s1 END; pop(1)
            cmd("extract_n");
            tName(t);
            bool(sign);
            int_(n);
            nl();
        }

        @Override
        public void extract_mn(CGType t, boolean sign, long m, long n) {
            // s0.t := Word.Extract(s0.t, m, n);
            // IF sign THEN SignExtend s0 END;
            cmd("extract_mn");
            tName(t);
            bool(sign);
            int_(m);
            int_(n);
            nl();
        }

        @Override
        public void insert(CGType t) {
            // s3.t := Word.Insert (s3.t, s2.t, s1.t, s0.t) ; pop(3)
            cmd("insert");
            tName(t);
            nl();
        }

        @Override
        public void insert_n(CGType t, long n) {
            // s2.t := Word.Insert (s2.t, s1.t, s0.t, n) ; pop(2)
            cmd("insert_n");
            tName(t);
            int_(n);
            nl();
        }

        @Override
        public void insert_mn(CGType t, long m, long n) {
            // s1.t := Word.Insert (s1.t, s0.t, m, n) ; pop(2)
            cmd("insert_mn");
            tName(t);
            int_(m);
            int_(n);
            nl();
        }

        /*------------------------------------------------ misc. stack/memory ops ---*/

        @Override
        public void swap(CGType a, CGType b) {
            // tmp := s1 ; s1 := s0 ; s0 := tmp
            cmd("swap");
            tName(a);
            tName(b);
            nl();
        }

        @Override
        public void pop(CGType t) {
            // pop(1) (i.e. discard s0)
            cmd("pop");
            tName(t);
            nl();
        }

        @Override
        public void copy_n(CGType z, CGType t, boolean overlap) {
            // Mem[s2.A:s0.z] := Mem[s1.A:s0.z]; pop(3)
            cmd("copy_n");
            tName(z);
            tName(t);
            bool(overlap);
            nl();
        }

        @Override
        public void copy(long n, CGType t, boolean overlap) {
            // Mem[s2.A:sz] := Mem[s1.A:sz]; pop(2)
            cmd("copy");
            int_(n);
            tName(t);
            bool(overlap);
            nl();
        }

        @Override
        public void zero_n(CGType z, CGType t) {
            // Mem[s1.A:s0.z] := 0; pop(2)
            cmd("zero_n");
            tName(z);
            tName(t);
            nl();
        }

        @Override
        public void zero(long n, CGType t) {
            // Mem[s1.A:sz] := 0; pop(1)
            cmd("zero");
            int_(n);
            tName(t);
            nl();
        }

        /*----------------------------------------------------------- conversions ---*/

        @Override
        public void loophole(CGType from, CGType two) {
            // s0.to := LOOPHOLE(s0.from, to)
            cmd("loophole");
            tName(from);
            tName(two);
            nl();
        }

        /*------------------------------------------------ traps & runtime checks ---*/

        @Override
        public void abort(RuntimeError code) {
            cmd("abort");
            int_(code.ordinal());
            nl();
        }

        @Override
        public void check_nil(RuntimeError code) {
            // IF (s0.A = NIL) THEN abort(code)
            cmd("check_nil");
            int_(code.ordinal());
            nl();
        }

        @Override
        public void check_lo(CGType t, int[] i, RuntimeError code) {
            // IF (s0.t < i) THEN abort(code)
            cmd("check_lo");
            tName(t);
            tInt(i);
            int_(code.ordinal());
            nl();
        }

        @Override
        public void check_hi(CGType t, int[] i, RuntimeError code) {
            // IF (i < s0.t) THEN abort(code)
            cmd("check_hi");
            tName(t);
            tInt(i);
            int_(code.ordinal());
            nl();
        }

        @Override
        public void check_range(CGType t, int[] a, int[] b, RuntimeError code) {
            // IF (s0.t < a) OR (b < s0.t) THEN abort(code)
            cmd("check_range");
            tName(t);
            tInt(a);
            tInt(b);
            int_(code.ordinal());
            nl();
        }

        @Override
        public void check_index(CGType t, RuntimeError code) {
            cmd("check_index");
            tName(t);
            int_(code.ordinal());
            nl();
        }

        @Override
        public void check_eq(CGType t, RuntimeError code) {
            // IF (s0.t # s1.t) THEN abort(code);  Pop (2)
            cmd("check_eq");
            tName(t);
            int_(code.ordinal());
            nl();
        }

        /*---------------------------------------------------- address arithmetic ---*/

        @Override
        public void add_offset(long i) {
            // s0.A := s0.A + i
            cmd("add_offset");
            int_(i);
            nl();
        }

        @Override
        public void index_address(CGType t, long size) {
            // s1.A := s1.A + s0.t * size ; pop
            cmd("index_address");
            tName(t);
            int_(size);
            nl();
        }

        /*------------------------------------------------------- procedure calls ---*/

        @Override
        public void start_call_direct(Proc p, long lev, CGType t) {
            // begin a procedure call to a procedure at static level 'lev'.
            cmd("start_call_direct");
            pName(p);
            int_(lev);
            tName(t);
            nl();
        }

        @Override
        public void start_call_indirect(CGType t, Target.CallingConvention cc) {
            // begin a procedure call to a procedure at static level 'lev'.
            cmd("start_call_indirect");
            tName(t);
            int_(cc.m3cg_id);
            nl();
        }

        @Override
        public void pop_param(CGType t) {
            // pop s0 and make it the "next" paramter in the current call
            cmd("pop_param");
            tName(t);
            nl();
        }

        @Override
        public void pop_struct(int t, long s, long a) {
            // pop s0 and make it the "next" paramter in the current call
            cmd("pop_struct");
            tipe(t);
            int_(s);
            int_(a);
            nl();
        }

        @Override
        public void pop_static_link() {
            cmd("pop_static_link");
            nl();
        }

        @Override
        public void call_direct(Proc p, CGType t) {
            // call the procedure identified by block b.  The procedure
            // returns a value of type t.
            cmd("call_direct");
            pName(p);
            tName(t);
            nl();
        }

        @Override
        public void call_indirect(CGType t, Target.CallingConvention cc) {
            // call the procedure whose address is in s0.A and pop s0.  The
            // procedure returns a value of type t.
            cmd("call_indirect");
            tName(t);
            int_(cc.m3cg_id);
            nl();
        }

        @Override
        public void start_try() {
            cmd("start_try");
            nl();
        }

        @Override
        public void end_try() {
            cmd("end_try");
            nl();
        }

        @Override
        public void invoke_direct(Proc p, CGType t, long next, long handler) {
            // call the procedure identified by block b.  The procedure
            // returns a value of type t.
            cmd("invoke_direct");
            pName(p);
            tName(t);
            lab(next);
            lab(handler);
            nl();
        }

        @Override
        public void invoke_indirect(CGType t, Target.CallingConvention cc, long next, long handler) {
            // call the procedure whose address is in s0.A and pop s0.  The
            // procedure returns a value of type t.
            cmd("invoke_indirect");
            tName(t);
            int_(cc.m3cg_id);
            lab(next);
            lab(handler);
            nl();
        }

        @Override
        public void landing_pad(CGType t, long handler, int[] catches) {
            // push; build a landingpad for a stack unwind. to capture the exception of type t.
            // handler should be the label immediately preceding this instruction.
            cmd("landing_pad");
            tName(t);
            lab(handler);
            int_(catches.length);
            for (int i = 0; i < catches.length; i++) {
                tipe(catches[i]);
            }
            nl();
        }

        /*------------------------------------------- procedure and closure types ---*/

        @Override
        public void load_procedure(Proc p) {
            // push; s0.A := ADDR (p's body)
            cmd("load_procedure");
            pName(p);
            nl();
        }

        @Override
        public void load_static_link(Proc p) {
            // push; s0.A := (static link needed to call p, NIL for top-level procs)
            cmd("load_static_link");
            pName(p);
            nl();
        }

        /*----------------------------------------------------------------- misc. ---*/

        @Override
        public void comment(String a, String b, String c, String d) {
            // Port: the width i is an int[1] (VAR width in Cmt).
            int[] i = {-1};
            cmt(a, i);
            cmt(b, i);
            cmt(c, i);
            cmt(d, i);
            cmt(EOL, i);
        }

        private void cmt(String t, int[] width) {
            char ch;
            if (t == null) {
                return;
            }
            for (int i = 0; i <= t.length() - 1; i++) {
                ch = t.charAt(i);
                if (width[0] == -1) {
                    outT("\t# ");
                    width[0] = 0;
                }
                if (ch == '\r') {
                    // eat carriage returns
                } else if (ch == '\n') {
                    outT(EOL);
                    width[0] = -1;
                } else {
                    outC(ch);
                }
            }
        }

        /*--------------------------------------------------------------- atomics ---*/

        @Override
        public void store_ordered(CGType t, CGType z, MemoryOrder order) {
            cmd("store_ordered");
            tName(t);
            tName(z);
            int_(order.ordinal());
            nl();
        }

        @Override
        public void load_ordered(CGType t, CGType z, MemoryOrder order) {
            cmd("load_ordered");
            tName(t);
            tName(z);
            int_(order.ordinal());
            nl();
        }

        @Override
        public void exchange(CGType t, CGType z, MemoryOrder order) {
            cmd("exchange");
            tName(t);
            tName(z);
            int_(order.ordinal());
            nl();
        }

        @Override
        public void compare_exchange(CGType t, CGType z, CGType r, MemoryOrder success, MemoryOrder failure) {
            cmd("compare_exchange");
            tName(t);
            tName(z);
            tName(r);
            int_(success.ordinal());
            int_(failure.ordinal());
            nl();
        }

        @Override
        public void fence(MemoryOrder order) {
            cmd("fence");
            int_(order.ordinal());
            nl();
        }

        @Override
        public void fetch_and_op(AtomicOp op, CGType t, CGType z, MemoryOrder order) {
            final String[] OpName = {"fetch_and_add", "fetch_and_sub", "fetch_and_or", "fetch_and_and", "fetch_and_xor"};
            cmd(OpName[op.ordinal()]);
            tName(t);
            tName(z);
            int_(order.ordinal());
            nl();
        }
    }
}
