// Java port of CM3's m3-sys/m3middle/src/M3CG_BinWr.i3 and M3CG_BinWr.m3.
// Original: Copyright 1996-2000, Critical Mass, Inc.
// Distributed under the Critical Mass license in licenses/COPYRIGHT-CMASS.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.middle;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
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
import m3j.middle.M3CG_Binary.Op;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;
import m3j.util.RangeError;

/**
 * A code generator that writes its calls as binary intermediate code.
 * See {@link M3CG_Binary} for the binary format.
 *
 * <p>Port: the procedures of the module that take the U as their first
 * parameter are methods of {@link U}. Wr.T is an {@link OutputStream} of
 * bytes; the {@link M3Buf} writes to it through an ISO-8859-1
 * {@link Writer}, so that each character in [0..255] is one byte. M3ID.ToText
 * needs the M3ID state and TFloat.ToBytes the TargetMap state, which
 * {@link #New} takes. IntRefTbl.T is a {@link HashMap}.
 */
public final class M3CG_BinWr {
    private M3CG_BinWr() {}

    // Port: Bop = M3CG_Binary.Op is the imported Op.

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
        Var var = null;
        long offset = 0;

        RuntimeHook(int name) {
            this.name = name;
        }
    }

    private static final class IntDesc {
        boolean negative;
        int n_bytes;
        final int[] bytes = new int[8];
    }

    /**
     * Returns a fresh, initialized code generator that writes its
     * calls as binary intermediate code on 'wr'.  See M3CG_Binary
     * for the binary format.
     */
    public static M3CG.T New(OutputStream output, M3ID.State ids, TargetMap.State map) {
        // Port: the M3Buf drains into a Writer on 'output'.
        Writer wr = new OutputStreamWriter(output, StandardCharsets.ISO_8859_1);
        M3Buf.T mbuf = M3Buf.New();
        M3Buf.attachDrain(mbuf, wr);
        U u = new U(ids, map);
        u.wr = wr;
        u.buf = mbuf;
        u.buf_len = 0;
        u.runtime = new HashMap<>(20);
        return u;
    }

    private static final class U extends M3CG.T {
        final M3ID.State ids;
        final TargetMap.State map;
        Writer wr = null;
        M3Buf.T buf = null;
        long buf_len = 0;
        /** Name -&gt; RuntimeHook */
        Map<Integer, RuntimeHook> runtime = null;
        long next_label_id = 1;
        long next_var = 1;
        long next_proc = 1;
        long next_scope = 1;

        U(M3ID.State ids, TargetMap.State map) {
            this.ids = ids;
            this.map = map;
        }

        /*------------------------------------------------------------------- I/O ---*/

        private void cmd(Op cmd) {
            outB(cmd.ordinal());
        }

        private void zName(int n) {
            if (n == M3ID.NoID) {
                outI(-1);
            } else {
                txt(M3ID.toText(ids, n));
            }
        }

        private void vName(Var v) {
            if (v == null) {
                outI(0);
            } else if (v instanceof WrVar x) {
                outI(x.tag);
            } else {
                throw new AssertError("M3CG_BinWr");
            }
        }

        private void pName(Proc p) {
            if (p == null) {
                outI(0);
            } else if (p instanceof WrProc x) {
                outI(x.tag);
            } else {
                throw new AssertError("M3CG_BinWr");
            }
        }

        private void tName(CGType t) {
            outB(t.ordinal());
        }

        private void flt(Target.Float f) {
            int[] buf = new int[4 * 4 + 1];
            int len = TFloat.toBytes(map, f, buf);
            outB(TFloat.prec(f).ordinal());
            for (int i = 0; i <= len - 1; i++) {
                outB(buf[i]);
            }
        }

        private void bool(boolean b) {
            outB(b ? 1 : 0);
        }

        private void lab(long i) {
            outI(i);
        }

        private void tipe(int t) {
            outI(t);
        }

        /** Int (u, i); Port: "int" is a Java keyword. */
        private void int_(long i) {
            outI(i);
        }

        private void tInt(int[] i) {
            long[] x = new long[1];
            if (TInt.toInt(i, x)) {
                outI(x[0]);
            } else {
                addBigX(i);
            }
        }

        private void bInt(long i) {
            outI(i);
        }

        private void txt(String t) {
            long len;
            if (t == null) {
                outI(-1);
            } else {
                len = t.length();
                outI(len);
                M3Buf.putText(buf, t);
                buf_len += len;
                if (buf_len >= 0xF000) {
                    flush();
                }
            }
        }

        /*--------------------------------------------------------- low level I/O ---*/

        private void flush() {
            M3Buf.flush(buf, wr);
            buf_len = 0;
            // Port: the Writer of New buffers; flushing it passes the bytes
            // on to the OutputStream, as M3Buf.Flush does to the Wr.T.
            try {
                wr.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /** Port: i is range-checked as [0..255]. */
        private void outB(long i) {
            if (i < 0 || 255 < i) {
                throw new RangeError("M3CG_BinWr");
            }
            M3Buf.putChar(buf, (char) i);
            buf_len++;
            if (buf_len >= 0xF000) {
                flush();
            }
        }

        private void outI(long i) {
            if (0 <= i && i <= M3CG_Binary.LastRegular) {
                outB(i);
            } else if (i < 0) {
                if (-255 <= i) {
                    i = -i;
                    outB(M3CG_Binary.NInt1);
                    outB(i);
                } else if (i >= -0xFFFF) {
                    i = -i;
                    outB(M3CG_Binary.NInt2);
                    outB(i & 0xFF);
                    outB((i >>> 8) & 0xFF);
                } else {
                    addBigInt(i);
                }
            } else { // i > 0
                if (i <= 255) {
                    outB(M3CG_Binary.Int1);
                    outB(i);
                } else if (i <= 0xFFFF) {
                    outB(M3CG_Binary.Int2);
                    outB(i & 0xFF);
                    outB((i >>> 8) & 0xFF);
                } else {
                    addBigInt(i);
                }
            }
        }

        private void addBigInt(long i) {
            IntDesc z = new IntDesc();
            z.negative = (i < 0);
            if (i < 0) {
                i = 0 - i;
            }
            z.n_bytes = 0;
            while (i != 0) {
                z.bytes[z.n_bytes] = (int) (i & 0xff);
                z.n_bytes++;
                i = i >>> 8;
            }
            dumpInt(z);
        }

        private void addBigX(int[] i) {
            int[] n = TInt.copy(i);
            IntDesc z = new IntDesc();
            z.negative = TInt.LT(n, TInt.Zero);
            if (z.negative) {
                TWord.subtract(TInt.Zero, i, n);
            }
            z.n_bytes = TInt.toUnsignedBytes(n, z.bytes);
            dumpInt(z);
        }

        private static final int[] Tag4 = {M3CG_Binary.NInt4, M3CG_Binary.Int4};
        private static final int[] Tag8 = {M3CG_Binary.NInt8, M3CG_Binary.Int8};

        private void dumpInt(IntDesc i) {
            int tag;
            int cnt = i.n_bytes;
            // zero fill the rest of the bytes
            for (int x = cnt; x <= i.bytes.length - 1; x++) {
                i.bytes[x] = 0;
            }

            // select the encoding
            if (cnt <= 4) {
                tag = Tag4[i.negative ? 0 : 1];
                cnt = 4;
            } else {
                tag = Tag8[i.negative ? 0 : 1];
                cnt = 8;
            }

            // finally, dump the bytes
            outB(tag);
            for (int x = 0; x <= cnt - 1; x++) {
                outB(i.bytes[x]);
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
            int_(M3CG_Binary.Version);
            cmd(Op.begin_unit);
            int_(optimize);
        }

        @Override
        public void end_unit() {
            // called after all other methods to finalize the unit and write the
            // resulting object
            cmd(Op.end_unit);
            flush();
        }

        @Override
        public void import_unit(int n) {
            // note that the current compilation unit imports the interface 'n'
            cmd(Op.import_unit);
            zName(n);
        }

        @Override
        public void export_unit(int n) {
            // note that the current compilation unit exports the interface 'n'
            cmd(Op.export_unit);
            zName(n);
        }

        /*------------------------------------------------ debugging line numbers ---*/

        @Override
        public void set_source_file(String file) {
            // Sets the current source file name.  Subsequent statements
            // and expressions are associated with this source location.
            cmd(Op.set_source_file);
            txt(file);
        }

        @Override
        public void set_source_line(long line) {
            // Sets the current source line number.  Subsequent statements
            // and expressions are associated with this source location.
            cmd(Op.set_source_line);
            int_(line);
        }

        /*------------------------------------------- debugging type declarations ---*/

        @Override
        public void declare_typename(int t, int n) {
            cmd(Op.declare_typename);
            tipe(t);
            zName(n);
        }

        @Override
        public void declare_array(int t, int index, int elt, long s, int elt_typename) {
            cmd(Op.declare_array);
            tipe(t);
            tipe(index);
            tipe(elt);
            bInt(s);
        }

        @Override
        public void declare_open_array(int t, int elt, long s, int elt_typename) {
            cmd(Op.declare_open_array);
            tipe(t);
            tipe(elt);
            bInt(s);
        }

        @Override
        public void declare_enum(int t, long n_elts, long s) {
            cmd(Op.declare_enum);
            tipe(t);
            int_(n_elts);
            bInt(s);
        }

        @Override
        public void declare_enum_elt(int n) {
            cmd(Op.declare_enum_elt);
            zName(n);
        }

        @Override
        public void declare_packed(int t, long s, int base, int base_typename) {
            cmd(Op.declare_packed);
            tipe(t);
            bInt(s);
            tipe(base);
        }

        @Override
        public void declare_record(int t, long s, long n_fields) {
            cmd(Op.declare_record);
            tipe(t);
            bInt(s);
            int_(n_fields);
        }

        @Override
        public void declare_field(int n, long o, long s, int t, int typename) {
            cmd(Op.declare_field);
            zName(n);
            bInt(o);
            bInt(s);
            tipe(t);
        }

        @Override
        public void declare_set(int t, int domain, long s, int domain_typename) {
            cmd(Op.declare_set);
            tipe(t);
            tipe(domain);
            bInt(s);
        }

        @Override
        public void declare_subrange(int t, int domain, int[] min, int[] max, long s, int domain_typename) {
            cmd(Op.declare_subrange);
            tipe(t);
            tipe(domain);
            tInt(min);
            tInt(max);
            bInt(s);
        }

        @Override
        public void declare_pointer(int t, int target, String brand, boolean traced, int target_typename) {
            cmd(Op.declare_pointer);
            tipe(t);
            tipe(target);
            txt(brand);
            bool(traced);
        }

        @Override
        public void declare_indirect(int t, int target, int target_typename) {
            cmd(Op.declare_indirect);
            tipe(t);
            tipe(target);
            // TODO target_typename but there are no downstream users
        }

        @Override
        public void declare_proctype(int t, long n_formals, int result, long n_raises, Target.CallingConvention cc, int result_typename) {
            cmd(Op.declare_proctype);
            tipe(t);
            int_(n_formals);
            tipe(result);
            int_(n_raises);
            outB(cc.m3cg_id);
            // TODO result_typename but it is not used downstream
        }

        @Override
        public void declare_formal(int n, int t, int typename) {
            cmd(Op.declare_formal);
            zName(n);
            tipe(t);
            // TODO typename but it is not used downstream and can be omitted indefinitely
        }

        @Override
        public void declare_raises(int n) {
            cmd(Op.declare_raises);
            zName(n);
        }

        @Override
        public void declare_object(int t, int super_, String brand, boolean traced, long n_fields, long n_methods, long field_size, long field_offset, long method_offset, int super_typename) {
            cmd(Op.declare_object);
            tipe(t);
            tipe(super_);
            txt(brand);
            bool(traced);
            int_(n_fields);
            int_(n_methods);
            bInt(field_size);
            int_(field_offset);
            int_(method_offset);
        }

        @Override
        public void declare_method(int n, int signature) {
            cmd(Op.declare_method);
            zName(n);
            tipe(signature);
        }

        @Override
        public void declare_opaque(int t, int super_) {
            cmd(Op.declare_opaque);
            tipe(t);
            tipe(super_);
        }

        @Override
        public void reveal_opaque(int lhs, int rhs) {
            cmd(Op.reveal_opaque);
            tipe(lhs);
            tipe(rhs);
        }

        @Override
        public void declare_exception(int n, int arg_type, boolean raise_proc, Var base, long offset) {
            cmd(Op.declare_exception);
            zName(n);
            tipe(arg_type);
            bool(raise_proc);
            vName(base);
            int_(offset);
        }

        @Override
        public void widechar_size(long size) {
            cmd(Op.widechar_size);
            int_(size);
        }

        /*--------------------------------------------------------- runtime hooks ---*/

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
            cmd(Op.set_runtime_proc);
            zName(n);
            pName(p);
        }

        /*------------------------------------------------- variable declarations ---*/

        private Var newVar() {
            Var v = new WrVar(next_var);
            next_var++;
            return v;
        }

        @Override
        public Var import_global(int n, long s, long a, CGType t, int m3t, int typename) {
            var v = newVar();
            cmd(Op.import_global);
            zName(n);
            int_(s);
            int_(a);
            tName(t);
            tipe(m3t);
            vName(v);
            // TODO typename
            return v;
        }

        @Override
        public Var declare_segment(int n, int m3t, boolean is_const) {
            var v = newVar();
            cmd(Op.declare_segment);
            zName(n);
            tipe(m3t);
            bool(is_const);
            vName(v);
            return v;
        }

        @Override
        public void bind_segment(Var seg, long s, long a, CGType t, boolean exported, boolean inited) {
            cmd(Op.bind_segment);
            vName(seg);
            int_(s);
            int_(a);
            tName(t);
            bool(exported);
            bool(inited);
        }

        @Override
        public Var declare_global(int n, long s, long a, CGType t, int m3t, boolean exported, boolean inited, int typename) {
            var v = newVar();
            cmd(Op.declare_global);
            zName(n);
            int_(s);
            int_(a);
            tName(t);
            tipe(m3t);
            bool(exported);
            bool(inited);
            vName(v);
            return v;
        }

        @Override
        public Var declare_constant(int n, long s, long a, CGType t, int m3t, boolean exported, boolean inited, int typename) {
            var v = newVar();
            cmd(Op.declare_constant);
            zName(n);
            int_(s);
            int_(a);
            tName(t);
            tipe(m3t);
            bool(exported);
            bool(inited);
            vName(v);
            return v;
        }

        @Override
        public Var declare_local(int n, long s, long a, CGType t, int m3t, boolean in_memory, boolean up_level, int f, int typename) {
            var v = newVar();
            cmd(Op.declare_local);
            zName(n);
            int_(s);
            int_(a);
            tName(t);
            tipe(m3t);
            bool(in_memory);
            bool(up_level);
            int_(f);
            vName(v);
            return v;
        }

        @Override
        public Var declare_param(int n, long s, long a, CGType t, int m3t, boolean in_memory, boolean up_level, int f, int typename) {
            var v = newVar();
            cmd(Op.declare_param);
            zName(n);
            int_(s);
            int_(a);
            tName(t);
            tipe(m3t);
            bool(in_memory);
            bool(up_level);
            int_(f);
            vName(v);
            // TODO typename but it is not used downstream and can be omitted indefinitely
            return v;
        }

        @Override
        public Var declare_temp(long s, long a, CGType t, boolean in_memory, int typename) {
            var v = newVar();
            cmd(Op.declare_temp);
            int_(s);
            int_(a);
            tName(t);
            bool(in_memory);
            vName(v);
            return v;
        }

        @Override
        public void free_temp(Var v) {
            cmd(Op.free_temp);
            vName(v);
        }

        /*---------------------------------------- static variable initialization ---*/

        @Override
        public void begin_init(Var v) {
            cmd(Op.begin_init);
            vName(v);
        }

        @Override
        public void end_init(Var v) {
            cmd(Op.end_init);
            vName(v);
        }

        @Override
        public void init_int(long o, int[] value, CGType t) {
            cmd(Op.init_int);
            int_(o);
            tInt(value);
            tName(t);
        }

        @Override
        public void init_proc(long o, Proc value) {
            cmd(Op.init_proc);
            int_(o);
            pName(value);
        }

        @Override
        public void init_label(long o, long value) {
            cmd(Op.init_label);
            int_(o);
            lab(value);
        }

        @Override
        public void init_var(long o, Var value, long bias) {
            cmd(Op.init_var);
            int_(o);
            vName(value);
            int_(bias);
        }

        @Override
        public void init_offset(long o, Var value) {
            cmd(Op.init_offset);
            int_(o);
            vName(value);
        }

        @Override
        public void init_chars(long o, String value) {
            cmd(Op.init_chars);
            int_(o);
            txt(value);
        }

        @Override
        public void init_float(long o, Target.Float f) {
            cmd(Op.init_float);
            int_(o);
            flt(f);
        }

        /*------------------------------------------------------------ procedures ---*/

        private Proc newProc() {
            Proc p = new WrProc(next_proc);
            next_proc++;
            return p;
        }

        @Override
        public Proc import_procedure(int n, long n_params, CGType ret_type, Target.CallingConvention cc, int return_typeid, int return_typename) {
            var p = newProc();
            cmd(Op.import_procedure);
            zName(n);
            int_(n_params);
            tName(ret_type);
            outB(cc.m3cg_id);
            pName(p);
            return p;
        }

        @Override
        public Proc declare_procedure(int n, long n_params, CGType return_type, long lev, Target.CallingConvention cc, boolean exported, Proc parent, int return_typeid, int return_typename) {
            var p = newProc();
            cmd(Op.declare_procedure);
            zName(n);
            int_(n_params);
            tName(return_type);
            int_(lev);
            outB(cc.m3cg_id);
            bool(exported);
            pName(parent);
            pName(p);
            // TODO return_typename but it is not used downstream and can be omitted indefinitely
            return p;
        }

        @Override
        public void begin_procedure(Proc p) {
            cmd(Op.begin_procedure);
            pName(p);
        }

        @Override
        public void end_procedure(Proc p) {
            cmd(Op.end_procedure);
            pName(p);
        }

        @Override
        public void begin_block() {
            // marks the beginning of a nested anonymous block
            cmd(Op.begin_block);
        }

        @Override
        public void end_block() {
            // marks the ending of a nested anonymous block
            cmd(Op.end_block);
        }

        @Override
        public void note_procedure_origin(Proc p) {
            cmd(Op.note_procedure_origin);
            pName(p);
        }

        /*------------------------------------------------------------ statements ---*/

        @Override
        public void set_label(long l, boolean barrier) {
            // define 'l' to be at the current pc
            cmd(Op.set_label);
            lab(l);
            bool(barrier);
        }

        @Override
        public void jump(long l) {
            // GOTO l
            cmd(Op.jump);
            lab(l);
        }

        @Override
        public void if_true(CGType t, long l, int f) {
            // IF (s0.t # 0) GOTO l ; pop
            cmd(Op.if_true);
            tName(t);
            lab(l);
            int_(f);
        }

        @Override
        public void if_false(CGType t, long l, int f) {
            // IF (s0.t = 0) GOTO l ; pop
            cmd(Op.if_false);
            tName(t);
            lab(l);
            int_(f);
        }

        private static final Op[] IfCompareOpName = {
            Op.if_eq, Op.if_ne, Op.if_gt, Op.if_ge, Op.if_lt, Op.if_le};

        @Override
        public void if_compare(CGType t, CompareOp op, long l, int f) {
            // IF (s1.t  op  s0.t) GOTO l ; pop(2)
            cmd(IfCompareOpName[op.ordinal()]);
            tName(t);
            lab(l);
            int_(f);
        }

        @Override
        public void case_jump(CGType t, long[] labels) {
            // "GOTO labels[s0.t] ; pop" with no range checking on s0.t
            cmd(Op.case_jump);
            tName(t);
            int_(labels.length);
            for (int i = 0; i < labels.length; i++) {
                lab(labels[i]);
            }
        }

        @Override
        public void exit_proc(CGType t) {
            // Returns s0.t if the stack is non-empty, otherwise returns no value.
            cmd(Op.exit_proc);
            tName(t);
        }

        /*------------------------------------------------------------ load/store ---*/

        @Override
        public void load(Var v, long o, CGType t, CGType z) {
            cmd(Op.load);
            vName(v);
            int_(o);
            tName(t);
            tName(z);
        }

        @Override
        public void store(Var v, long o, CGType t, CGType z) {
            cmd(Op.store);
            vName(v);
            int_(o);
            tName(t);
            tName(z);
        }

        @Override
        public void load_address(Var v, long o) {
            cmd(Op.load_address);
            vName(v);
            int_(o);
        }

        @Override
        public void load_indirect(long o, CGType t, CGType z) {
            cmd(Op.load_indirect);
            int_(o);
            tName(t);
            tName(z);
        }

        @Override
        public void store_indirect(long o, CGType t, CGType z) {
            cmd(Op.store_indirect);
            int_(o);
            tName(t);
            tName(z);
        }

        /*-------------------------------------------------------------- literals ---*/

        @Override
        public void load_nil() {
            // push ; s0.A := a
            cmd(Op.load_nil);
        }

        @Override
        public void load_integer(CGType t, int[] i) {
            // push ; s0.t:= i
            cmd(Op.load_integer);
            tName(t);
            tInt(i);
        }

        @Override
        public void load_float(CGType t, Target.Float f) {
            // push ; s0.t := f
            cmd(Op.load_float);
            tName(t);
            flt(f);
        }

        /*------------------------------------------------------------ arithmetic ---*/

        private static final Op[] CompareOpName = {
            Op.eq, Op.ne, Op.gt, Op.ge, Op.lt, Op.le};

        @Override
        public void compare(CGType t, CGType z, CompareOp op) {
            // s1.z := (s1.t  op  s0.t)  ; pop
            cmd(CompareOpName[op.ordinal()]);
            tName(t);
            tName(z);
        }

        @Override
        public void add(CGType t) {
            // s1.t := s1.t + s0.t ; pop
            cmd(Op.add);
            tName(t);
        }

        @Override
        public void subtract(CGType t) {
            // s1.t := s1.t - s0.t ; pop
            cmd(Op.subtract);
            tName(t);
        }

        @Override
        public void multiply(CGType t) {
            // s1.t := s1.t * s0.t ; pop
            cmd(Op.multiply);
            tName(t);
        }

        @Override
        public void divide(CGType t) {
            // s1.t := s1.t / s0.t ; pop
            cmd(Op.divide);
            tName(t);
        }

        @Override
        public void div(CGType t, Sign a, Sign b) {
            // s1.t := s1.t DIV s0.t ; pop
            cmd(Op.div);
            tName(t);
            outB(a.ordinal());
            outB(b.ordinal());
        }

        @Override
        public void mod(CGType t, Sign a, Sign b) {
            // s1.t := s1.t MOD s0.t ; pop
            cmd(Op.mod);
            tName(t);
            outB(a.ordinal());
            outB(b.ordinal());
        }

        @Override
        public void negate(CGType t) {
            // s0.t := - s0.t
            cmd(Op.negate);
            tName(t);
        }

        @Override
        public void abs(CGType t) {
            // s0.t := ABS (s0.t) (noop on Words)
            cmd(Op.abs);
            tName(t);
        }

        @Override
        public void max(CGType t) {
            // s1.t := MAX (s1.t, s0.t) ; pop
            cmd(Op.max);
            tName(t);
        }

        @Override
        public void min(CGType t) {
            // s1.t := MIN (s1.t, s0.t) ; pop
            cmd(Op.min);
            tName(t);
        }

        private static final Op[] ConvertOpName = {
            Op.round, Op.trunc, Op.floor, Op.ceiling};

        @Override
        public void cvt_int(CGType t, CGType x, ConvertOp op) {
            // s0.x := op (s0.t)
            cmd(ConvertOpName[op.ordinal()]);
            tName(t);
            tName(x);
        }

        @Override
        public void cvt_float(CGType t, CGType x) {
            // s0.x := FLOAT (s0.t, x)
            cmd(Op.cvt_float);
            tName(t);
            tName(x);
        }

        /*------------------------------------------------------------------ sets ---*/

        @Override
        public void set_union(long s) {
            // s1.B := s1.B + s0.B ; pop
            cmd(Op.set_union);
            int_(s);
        }

        @Override
        public void set_difference(long s) {
            // s1.B := s1.B - s0.B ; pop
            cmd(Op.set_difference);
            int_(s);
        }

        @Override
        public void set_intersection(long s) {
            // s1.B := s1.B * s0.B ; pop
            cmd(Op.set_intersection);
            int_(s);
        }

        @Override
        public void set_sym_difference(long s) {
            // s1.B := s1.B / s0.B ; pop
            cmd(Op.set_sym_difference);
            int_(s);
        }

        @Override
        public void set_member(long s, CGType t) {
            // s1.t := (s0.t IN s1.B) ; pop
            cmd(Op.set_member);
            int_(s);
            tName(t);
        }

        private static final Op[] SetCompareOpName = {
            Op.set_eq, Op.set_ne, Op.set_gt, Op.set_ge, Op.set_lt, Op.set_le};

        @Override
        public void set_compare(long s, CompareOp op, CGType t) {
            // s1.t := (s1.B  op  s0.B) ; pop
            cmd(SetCompareOpName[op.ordinal()]);
            int_(s);
            tName(t);
        }

        @Override
        public void set_range(long s, CGType t) {
            // s2.A [s1.t .. s0.t] := 1's; pop(3)
            cmd(Op.set_range);
            int_(s);
            tName(t);
        }

        @Override
        public void set_singleton(long s, CGType t) {
            // s1.A [s0.t] := 1; pop(2)
            cmd(Op.set_singleton);
            int_(s);
            tName(t);
        }

        /*------------------------------------------------- Word.T bit operations ---*/

        @Override
        public void not(CGType t) {
            // s0.t := Word.Not (s0.t)
            cmd(Op.not);
            tName(t);
        }

        @Override
        public void and(CGType t) {
            // s1.t := Word.And (s1.t, s0.t) ; pop
            cmd(Op.and);
            tName(t);
        }

        @Override
        public void or(CGType t) {
            // s1.t := Word.Or  (s1.t, s0.t) ; pop
            cmd(Op.or);
            tName(t);
        }

        @Override
        public void xor(CGType t) {
            // s1.t := Word.Xor (s1.t, s0.t) ; pop
            cmd(Op.xor);
            tName(t);
        }

        @Override
        public void shift(CGType t) {
            // s1.t := Word.Shift  (s1.t, s0.t) ; pop
            cmd(Op.shift);
            tName(t);
        }

        @Override
        public void shift_left(CGType t) {
            // s1.t := Word.Shift  (s1.t, s0.t) ; pop
            cmd(Op.shift_left);
            tName(t);
        }

        @Override
        public void shift_right(CGType t) {
            // s1.t := Word.Shift  (s1.t, -s0.t) ; pop
            cmd(Op.shift_right);
            tName(t);
        }

        @Override
        public void rotate(CGType t) {
            // s1.t := Word.Rotate (s1.t, s0.t) ; pop
            cmd(Op.rotate);
            tName(t);
        }

        @Override
        public void rotate_left(CGType t) {
            // s1.t := Word.Rotate (s1.t, s0.t) ; pop
            cmd(Op.rotate_left);
            tName(t);
        }

        @Override
        public void rotate_right(CGType t) {
            // s1.t := Word.Rotate (s1.t, -s0.t) ; pop
            cmd(Op.rotate_right);
            tName(t);
        }

        @Override
        public void widen(boolean sign) {
            // s0.I64 := s0.I32;  IF sign THEN SignExtend s0;
            cmd(Op.widen);
            bool(sign);
        }

        @Override
        public void chop() {
            // s0.I32 := Word.And (s0.I64, 16_ffffffff);
            cmd(Op.chop);
        }

        @Override
        public void extract(CGType t, boolean sign) {
            // s2.t := Word.Extract(s2.t, s1.t, s0.t);
            // IF sign THEN SignExtend s2 END; pop(2)
            cmd(Op.extract);
            tName(t);
            bool(sign);
        }

        @Override
        public void extract_n(CGType t, boolean sign, long n) {
            // s1.t := Word.Extract(s1.t, s0.t, n);
            // IF sign THEN SignExtend s1 END; pop(1)
            cmd(Op.extract_n);
            tName(t);
            bool(sign);
            int_(n);
        }

        @Override
        public void extract_mn(CGType t, boolean sign, long m, long n) {
            // s0.t := Word.Extract(s0.t, m, n);
            // IF sign THEN SignExtend s0 END;
            cmd(Op.extract_mn);
            tName(t);
            bool(sign);
            int_(m);
            int_(n);
        }

        @Override
        public void insert(CGType t) {
            // s3.t := Word.Insert (s3.t, s2.t, s1.t, s0.t) ; pop(3)
            cmd(Op.insert);
            tName(t);
        }

        @Override
        public void insert_n(CGType t, long n) {
            // s2.t := Word.Insert (s2.t, s1.t, s0.t, n) ; pop(2)
            cmd(Op.insert_n);
            tName(t);
            int_(n);
        }

        @Override
        public void insert_mn(CGType t, long m, long n) {
            // s1.t := Word.Insert (s1.t, s0.t, m, n) ; pop(2)
            cmd(Op.insert_mn);
            tName(t);
            int_(m);
            int_(n);
        }

        /*------------------------------------------------ misc. stack/memory ops ---*/

        @Override
        public void swap(CGType a, CGType b) {
            // tmp := s1 ; s1 := s0 ; s0 := tmp
            cmd(Op.swap);
            tName(a);
            tName(b);
        }

        @Override
        public void pop(CGType t) {
            // pop(1) (i.e. discard s0)
            cmd(Op.pop);
            tName(t);
        }

        @Override
        public void copy_n(CGType z, CGType t, boolean overlap) {
            // Mem[s2.A:s0.z] := Mem[s1.A:s0.z]; pop(3)
            cmd(Op.copy_n);
            tName(z);
            tName(t);
            bool(overlap);
        }

        @Override
        public void copy(long n, CGType t, boolean overlap) {
            // Mem[s2.A:sz] := Mem[s1.A:sz]; pop(2)
            cmd(Op.copy);
            int_(n);
            tName(t);
            bool(overlap);
        }

        @Override
        public void zero_n(CGType z, CGType t) {
            // Mem[s1.A:s0.z] := 0; pop(2)
            cmd(Op.zero_n);
            tName(z);
            tName(t);
        }

        @Override
        public void zero(long n, CGType t) {
            // Mem[s1.A:sz] := 0; pop(1)
            cmd(Op.zero);
            int_(n);
            tName(t);
        }

        /*----------------------------------------------------------- conversions ---*/

        @Override
        public void loophole(CGType from, CGType two) {
            // s0.to := LOOPHOLE(s0.from, to)
            cmd(Op.loophole);
            tName(from);
            tName(two);
        }

        /*------------------------------------------------ traps & runtime checks ---*/

        @Override
        public void abort(RuntimeError code) {
            cmd(Op.abort);
            int_(code.ordinal());
        }

        @Override
        public void check_nil(RuntimeError code) {
            // IF (s0.A = NIL) THEN abort(code)
            cmd(Op.check_nil);
            int_(code.ordinal());
        }

        @Override
        public void check_lo(CGType t, int[] i, RuntimeError code) {
            // IF (s0.t < i) THEN abort(code)
            cmd(Op.check_lo);
            tName(t);
            tInt(i);
            int_(code.ordinal());
        }

        @Override
        public void check_hi(CGType t, int[] i, RuntimeError code) {
            // IF (i < s0.t) THEN abort(code)
            cmd(Op.check_hi);
            tName(t);
            tInt(i);
            int_(code.ordinal());
        }

        @Override
        public void check_range(CGType t, int[] a, int[] b, RuntimeError code) {
            // IF (s0.t < a) OR (b < s0.t) THEN abort(code)
            cmd(Op.check_range);
            tName(t);
            tInt(a);
            tInt(b);
            int_(code.ordinal());
        }

        @Override
        public void check_index(CGType t, RuntimeError code) {
            cmd(Op.check_index);
            tName(t);
            int_(code.ordinal());
        }

        @Override
        public void check_eq(CGType t, RuntimeError code) {
            // IF (s0.t # s1.t) THEN abort(code);  Pop (2)
            cmd(Op.check_eq);
            tName(t);
            int_(code.ordinal());
        }

        /*---------------------------------------------------- address arithmetic ---*/

        @Override
        public void add_offset(long i) {
            // s0.A := s0.A + i
            cmd(Op.add_offset);
            int_(i);
        }

        @Override
        public void index_address(CGType t, long size) {
            // s1.A := s1.A + s0.t * size ; pop
            cmd(Op.index_address);
            tName(t);
            int_(size);
        }

        /*------------------------------------------------------- procedure calls ---*/

        @Override
        public void start_call_direct(Proc p, long lev, CGType t) {
            // begin a procedure call to a procedure at static level 'lev'.
            cmd(Op.start_call_direct);
            pName(p);
            int_(lev);
            tName(t);
        }

        @Override
        public void start_call_indirect(CGType t, Target.CallingConvention cc) {
            // begin a procedure call to a procedure at static level 'lev'.
            cmd(Op.start_call_indirect);
            tName(t);
            outB(cc.m3cg_id);
        }

        @Override
        public void pop_param(CGType t) {
            // pop s0 and make it the "next" paramter in the current call
            cmd(Op.pop_param);
            tName(t);
        }

        @Override
        public void pop_struct(int t, long s, long a) {
            // pop s0 and make it the "next" paramter in the current call
            cmd(Op.pop_struct);
            tipe(t);
            int_(s);
            int_(a);
        }

        @Override
        public void pop_static_link() {
            cmd(Op.pop_static_link);
        }

        @Override
        public void call_direct(Proc p, CGType t) {
            // call the procedure identified by block b.  The procedure
            // returns a value of type t.
            cmd(Op.call_direct);
            pName(p);
            tName(t);
        }

        @Override
        public void call_indirect(CGType t, Target.CallingConvention cc) {
            // call the procedure whose address is in s0.A and pop s0.  The
            // procedure returns a value of type t.
            cmd(Op.call_indirect);
            tName(t);
            outB(cc.m3cg_id);
        }

        @Override
        public void start_try() {
            cmd(Op.start_try);
        }

        @Override
        public void end_try() {
            cmd(Op.end_try);
        }

        @Override
        public void invoke_direct(Proc p, CGType t, long next, long handler) {
            // call the procedure identified by proc p.  The procedure
            // returns a value of type t. if the outcome is normal resume at next
            // otherwise the unwinder will resume at handler.
            cmd(Op.invoke_direct);
            pName(p);
            tName(t);
            lab(next);
            lab(handler);
        }

        @Override
        public void invoke_indirect(CGType t, Target.CallingConvention cc, long next, long handler) {
            // call the procedure whose address is in s0.A and pop s0.  The procedure
            // returns a value of type t. if the outcome is normal resume at next
            // otherwise the unwinder will resume at handler.
            cmd(Op.invoke_indirect);
            tName(t);
            outB(cc.m3cg_id);
            lab(next);
            lab(handler);
        }

        @Override
        public void landing_pad(CGType t, long handler, int[] catches) {
            // push; build a landing pad as a result of a stack unwind
            cmd(Op.landing_pad);
            tName(t);
            lab(handler);
            int_(catches.length);
            for (int i = 0; i < catches.length; i++) {
                tipe(catches[i]);
            }
        }

        /*------------------------------------------- procedure and closure types ---*/

        @Override
        public void load_procedure(Proc p) {
            // push; s0.A := ADDR (p's body)
            cmd(Op.load_procedure);
            pName(p);
        }

        @Override
        public void load_static_link(Proc p) {
            // push; s0.A := (static link needed to call p, NIL for top-level procs)
            cmd(Op.load_static_link);
            pName(p);
        }

        /*----------------------------------------------------------------- misc. ---*/

        @Override
        public void comment(String a, String b, String c, String d) {
            String msg = "";
            if (a != null) {
                msg = msg + a;
            }
            if (b != null) {
                msg = msg + b;
            }
            if (c != null) {
                msg = msg + c;
            }
            if (d != null) {
                msg = msg + d;
            }
            cmd(Op.comment);
            txt(msg);
        }

        /*--------------------------------------------------------------- atomics ---*/

        @Override
        public void store_ordered(CGType t, CGType z, MemoryOrder order) {
            cmd(Op.store_ordered);
            tName(t);
            tName(z);
            int_(order.ordinal());
        }

        @Override
        public void load_ordered(CGType t, CGType z, MemoryOrder order) {
            cmd(Op.load_ordered);
            tName(t);
            tName(z);
            int_(order.ordinal());
        }

        @Override
        public void exchange(CGType t, CGType z, MemoryOrder order) {
            cmd(Op.exchange);
            tName(t);
            tName(z);
            int_(order.ordinal());
        }

        @Override
        public void compare_exchange(CGType t, CGType z, CGType r, MemoryOrder success, MemoryOrder failure) {
            cmd(Op.compare_exchange);
            tName(t);
            tName(z);
            tName(r);
            int_(success.ordinal());
            int_(failure.ordinal());
        }

        @Override
        public void fence(MemoryOrder order) {
            cmd(Op.fence);
            int_(order.ordinal());
        }

        private static final Op[] AtomicOpName = {
            Op.fetch_and_add, Op.fetch_and_sub,
            Op.fetch_and_or,  Op.fetch_and_and,
            Op.fetch_and_xor};

        @Override
        public void fetch_and_op(AtomicOp op, CGType t, CGType z, MemoryOrder order) {
            cmd(AtomicOpName[op.ordinal()]);
            tName(t);
            tName(z);
            int_(order.ordinal());
        }
    }
}
