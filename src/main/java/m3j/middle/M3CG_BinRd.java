// Java port of CM3's m3-sys/m3middle/src/M3CG_BinRd.i3 and M3CG_BinRd.m3.
// Original: Copyright 1996-2000, Critical Mass, Inc.
// Distributed under the Critical Mass license in licenses/COPYRIGHT-CMASS.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.middle;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

import m3j.middle.M3CG.AtomicOp;
import m3j.middle.M3CG.CompareOp;
import m3j.middle.M3CG.ConvertOp;
import m3j.middle.M3CG.MemoryOrder;
import m3j.middle.M3CG.RuntimeError;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;
import m3j.util.RangeError;

/**
 * Reads binary intermediate code (M3CG_Binary) and calls the corresponding
 * methods of a code generator.
 *
 * <p>Port: Rd.T is an {@link InputStream} of bytes. The M3ID, Target and
 * TargetMap state the original uses as module variables are fields of
 * {@link State}; Error writes to {@code out} (Stdio.stdout in the
 * original). A value assigned to a subrange type (TypeUID, Frequency, the
 * byte of a calling convention) is range-checked as in the original.
 */
public final class M3CG_BinRd {
    private M3CG_BinRd() {}

    private static final int BufSize = 0x10000; // 64K

    private static final class State {
        InputStream rd;
        M3CG.T cg;
        byte[] buf;
        int buf_len;
        int buf_ptr;
        boolean hit_eof;
        M3CG.Var[] vars;
        M3CG.Proc[] procs;
        long[] labels;
        // Port: the module variables of the modules the original uses.
        M3ID.State ids;
        Target.State target;
        TargetMap.State targetMap;
        PrintStream out;
    }

    private record Cmd(M3CG_Binary.Op bop, Consumer<State> proc) {}

    private static final Cmd[] CmdMap = {
        new Cmd(M3CG_Binary.Op.begin_unit, M3CG_BinRd::begin_unit),
        new Cmd(M3CG_Binary.Op.end_unit, M3CG_BinRd::end_unit),
        new Cmd(M3CG_Binary.Op.import_unit, M3CG_BinRd::import_unit),
        new Cmd(M3CG_Binary.Op.export_unit, M3CG_BinRd::export_unit),
        new Cmd(M3CG_Binary.Op.set_source_file, M3CG_BinRd::set_source_file),
        new Cmd(M3CG_Binary.Op.set_source_line, M3CG_BinRd::set_source_line),
        new Cmd(M3CG_Binary.Op.declare_typename, M3CG_BinRd::declare_typename),
        new Cmd(M3CG_Binary.Op.declare_array, M3CG_BinRd::declare_array),
        new Cmd(M3CG_Binary.Op.declare_open_array, M3CG_BinRd::declare_open_array),
        new Cmd(M3CG_Binary.Op.declare_enum, M3CG_BinRd::declare_enum),
        new Cmd(M3CG_Binary.Op.declare_enum_elt, M3CG_BinRd::declare_enum_elt),
        new Cmd(M3CG_Binary.Op.declare_packed, M3CG_BinRd::declare_packed),
        new Cmd(M3CG_Binary.Op.declare_record, M3CG_BinRd::declare_record),
        new Cmd(M3CG_Binary.Op.declare_field, M3CG_BinRd::declare_field),
        new Cmd(M3CG_Binary.Op.declare_set, M3CG_BinRd::declare_set),
        new Cmd(M3CG_Binary.Op.declare_subrange, M3CG_BinRd::declare_subrange),
        new Cmd(M3CG_Binary.Op.declare_pointer, M3CG_BinRd::declare_pointer),
        new Cmd(M3CG_Binary.Op.declare_indirect, M3CG_BinRd::declare_indirect),
        new Cmd(M3CG_Binary.Op.declare_proctype, M3CG_BinRd::declare_proctype),
        new Cmd(M3CG_Binary.Op.declare_formal, M3CG_BinRd::declare_formal),
        new Cmd(M3CG_Binary.Op.declare_raises, M3CG_BinRd::declare_raises),
        new Cmd(M3CG_Binary.Op.declare_object, M3CG_BinRd::declare_object),
        new Cmd(M3CG_Binary.Op.declare_method, M3CG_BinRd::declare_method),
        new Cmd(M3CG_Binary.Op.declare_opaque, M3CG_BinRd::declare_opaque),
        new Cmd(M3CG_Binary.Op.reveal_opaque, M3CG_BinRd::reveal_opaque),
        new Cmd(M3CG_Binary.Op.declare_exception, M3CG_BinRd::declare_exception),
        new Cmd(M3CG_Binary.Op.set_runtime_proc, M3CG_BinRd::set_runtime_proc),
        new Cmd(M3CG_Binary.Op.import_global, M3CG_BinRd::import_global),
        new Cmd(M3CG_Binary.Op.declare_segment, M3CG_BinRd::declare_segment),
        new Cmd(M3CG_Binary.Op.bind_segment, M3CG_BinRd::bind_segment),
        new Cmd(M3CG_Binary.Op.declare_global, M3CG_BinRd::declare_global),
        new Cmd(M3CG_Binary.Op.declare_constant, M3CG_BinRd::declare_constant),
        new Cmd(M3CG_Binary.Op.declare_local, M3CG_BinRd::declare_local),
        new Cmd(M3CG_Binary.Op.declare_param, M3CG_BinRd::declare_param),
        new Cmd(M3CG_Binary.Op.declare_temp, M3CG_BinRd::declare_temp),
        new Cmd(M3CG_Binary.Op.free_temp, M3CG_BinRd::free_temp),
        new Cmd(M3CG_Binary.Op.begin_init, M3CG_BinRd::begin_init),
        new Cmd(M3CG_Binary.Op.end_init, M3CG_BinRd::end_init),
        new Cmd(M3CG_Binary.Op.init_int, M3CG_BinRd::init_int),
        new Cmd(M3CG_Binary.Op.init_proc, M3CG_BinRd::init_proc),
        new Cmd(M3CG_Binary.Op.init_label, M3CG_BinRd::init_label),
        new Cmd(M3CG_Binary.Op.init_var, M3CG_BinRd::init_var),
        new Cmd(M3CG_Binary.Op.init_offset, M3CG_BinRd::init_offset),
        new Cmd(M3CG_Binary.Op.init_chars, M3CG_BinRd::init_chars),
        new Cmd(M3CG_Binary.Op.init_float, M3CG_BinRd::init_float),
        new Cmd(M3CG_Binary.Op.import_procedure, M3CG_BinRd::import_procedure),
        new Cmd(M3CG_Binary.Op.declare_procedure, M3CG_BinRd::declare_procedure),
        new Cmd(M3CG_Binary.Op.begin_procedure, M3CG_BinRd::begin_procedure),
        new Cmd(M3CG_Binary.Op.end_procedure, M3CG_BinRd::end_procedure),
        new Cmd(M3CG_Binary.Op.begin_block, M3CG_BinRd::begin_block),
        new Cmd(M3CG_Binary.Op.end_block, M3CG_BinRd::end_block),
        new Cmd(M3CG_Binary.Op.note_procedure_origin, M3CG_BinRd::note_procedure_origin),
        new Cmd(M3CG_Binary.Op.set_label, M3CG_BinRd::set_label),
        new Cmd(M3CG_Binary.Op.jump, M3CG_BinRd::jump),
        new Cmd(M3CG_Binary.Op.if_true, M3CG_BinRd::if_true),
        new Cmd(M3CG_Binary.Op.if_false, M3CG_BinRd::if_false),
        new Cmd(M3CG_Binary.Op.if_eq, M3CG_BinRd::if_eq),
        new Cmd(M3CG_Binary.Op.if_ne, M3CG_BinRd::if_ne),
        new Cmd(M3CG_Binary.Op.if_gt, M3CG_BinRd::if_gt),
        new Cmd(M3CG_Binary.Op.if_ge, M3CG_BinRd::if_ge),
        new Cmd(M3CG_Binary.Op.if_lt, M3CG_BinRd::if_lt),
        new Cmd(M3CG_Binary.Op.if_le, M3CG_BinRd::if_le),
        new Cmd(M3CG_Binary.Op.case_jump, M3CG_BinRd::case_jump),
        new Cmd(M3CG_Binary.Op.exit_proc, M3CG_BinRd::exit_proc),
        new Cmd(M3CG_Binary.Op.load, M3CG_BinRd::load),
        new Cmd(M3CG_Binary.Op.load_address, M3CG_BinRd::load_address),
        new Cmd(M3CG_Binary.Op.load_indirect, M3CG_BinRd::load_indirect),
        new Cmd(M3CG_Binary.Op.store, M3CG_BinRd::store),
        new Cmd(M3CG_Binary.Op.store_indirect, M3CG_BinRd::store_indirect),
        new Cmd(M3CG_Binary.Op.load_nil, M3CG_BinRd::load_nil),
        new Cmd(M3CG_Binary.Op.load_integer, M3CG_BinRd::load_integer),
        new Cmd(M3CG_Binary.Op.load_float, M3CG_BinRd::load_float),
        new Cmd(M3CG_Binary.Op.eq, M3CG_BinRd::eq),
        new Cmd(M3CG_Binary.Op.ne, M3CG_BinRd::ne),
        new Cmd(M3CG_Binary.Op.gt, M3CG_BinRd::gt),
        new Cmd(M3CG_Binary.Op.ge, M3CG_BinRd::ge),
        new Cmd(M3CG_Binary.Op.lt, M3CG_BinRd::lt),
        new Cmd(M3CG_Binary.Op.le, M3CG_BinRd::le),
        new Cmd(M3CG_Binary.Op.add, M3CG_BinRd::add),
        new Cmd(M3CG_Binary.Op.subtract, M3CG_BinRd::subtract),
        new Cmd(M3CG_Binary.Op.multiply, M3CG_BinRd::multiply),
        new Cmd(M3CG_Binary.Op.divide, M3CG_BinRd::divide),
        new Cmd(M3CG_Binary.Op.negate, M3CG_BinRd::negate),
        new Cmd(M3CG_Binary.Op.abs, M3CG_BinRd::abs),
        new Cmd(M3CG_Binary.Op.max, M3CG_BinRd::max),
        new Cmd(M3CG_Binary.Op.min, M3CG_BinRd::min),
        new Cmd(M3CG_Binary.Op.round, M3CG_BinRd::round),
        new Cmd(M3CG_Binary.Op.trunc, M3CG_BinRd::trunc),
        new Cmd(M3CG_Binary.Op.floor, M3CG_BinRd::floor),
        new Cmd(M3CG_Binary.Op.ceiling, M3CG_BinRd::ceiling),
        new Cmd(M3CG_Binary.Op.cvt_float, M3CG_BinRd::cvt_float),
        new Cmd(M3CG_Binary.Op.div, M3CG_BinRd::div),
        new Cmd(M3CG_Binary.Op.mod, M3CG_BinRd::mod),
        new Cmd(M3CG_Binary.Op.set_union, M3CG_BinRd::set_union),
        new Cmd(M3CG_Binary.Op.set_difference, M3CG_BinRd::set_difference),
        new Cmd(M3CG_Binary.Op.set_intersection, M3CG_BinRd::set_intersection),
        new Cmd(M3CG_Binary.Op.set_sym_difference, M3CG_BinRd::set_sym_difference),
        new Cmd(M3CG_Binary.Op.set_member, M3CG_BinRd::set_member),
        new Cmd(M3CG_Binary.Op.set_eq, M3CG_BinRd::set_eq),
        new Cmd(M3CG_Binary.Op.set_ne, M3CG_BinRd::set_ne),
        new Cmd(M3CG_Binary.Op.set_lt, M3CG_BinRd::set_lt),
        new Cmd(M3CG_Binary.Op.set_le, M3CG_BinRd::set_le),
        new Cmd(M3CG_Binary.Op.set_gt, M3CG_BinRd::set_gt),
        new Cmd(M3CG_Binary.Op.set_ge, M3CG_BinRd::set_ge),
        new Cmd(M3CG_Binary.Op.set_range, M3CG_BinRd::set_range),
        new Cmd(M3CG_Binary.Op.set_singleton, M3CG_BinRd::set_singleton),
        new Cmd(M3CG_Binary.Op.not, M3CG_BinRd::not),
        new Cmd(M3CG_Binary.Op.and, M3CG_BinRd::and),
        new Cmd(M3CG_Binary.Op.or, M3CG_BinRd::or),
        new Cmd(M3CG_Binary.Op.xor, M3CG_BinRd::xor),
        new Cmd(M3CG_Binary.Op.shift, M3CG_BinRd::shift),
        new Cmd(M3CG_Binary.Op.shift_left, M3CG_BinRd::shift_left),
        new Cmd(M3CG_Binary.Op.shift_right, M3CG_BinRd::shift_right),
        new Cmd(M3CG_Binary.Op.rotate, M3CG_BinRd::rotate),
        new Cmd(M3CG_Binary.Op.rotate_left, M3CG_BinRd::rotate_left),
        new Cmd(M3CG_Binary.Op.rotate_right, M3CG_BinRd::rotate_right),
        new Cmd(M3CG_Binary.Op.widen, M3CG_BinRd::widen),
        new Cmd(M3CG_Binary.Op.chop, M3CG_BinRd::chop),
        new Cmd(M3CG_Binary.Op.extract, M3CG_BinRd::extract),
        new Cmd(M3CG_Binary.Op.extract_n, M3CG_BinRd::extract_n),
        new Cmd(M3CG_Binary.Op.extract_mn, M3CG_BinRd::extract_mn),
        new Cmd(M3CG_Binary.Op.insert, M3CG_BinRd::insert),
        new Cmd(M3CG_Binary.Op.insert_n, M3CG_BinRd::insert_n),
        new Cmd(M3CG_Binary.Op.insert_mn, M3CG_BinRd::insert_mn),
        new Cmd(M3CG_Binary.Op.swap, M3CG_BinRd::swap),
        new Cmd(M3CG_Binary.Op.pop, M3CG_BinRd::pop),
        new Cmd(M3CG_Binary.Op.copy_n, M3CG_BinRd::copy_n),
        new Cmd(M3CG_Binary.Op.copy, M3CG_BinRd::copy),
        new Cmd(M3CG_Binary.Op.zero_n, M3CG_BinRd::zero_n),
        new Cmd(M3CG_Binary.Op.zero, M3CG_BinRd::zero),
        new Cmd(M3CG_Binary.Op.loophole, M3CG_BinRd::loophole),
        new Cmd(M3CG_Binary.Op.abort, M3CG_BinRd::abort),
        new Cmd(M3CG_Binary.Op.check_nil, M3CG_BinRd::check_nil),
        new Cmd(M3CG_Binary.Op.check_lo, M3CG_BinRd::check_lo),
        new Cmd(M3CG_Binary.Op.check_hi, M3CG_BinRd::check_hi),
        new Cmd(M3CG_Binary.Op.check_range, M3CG_BinRd::check_range),
        new Cmd(M3CG_Binary.Op.check_index, M3CG_BinRd::check_index),
        new Cmd(M3CG_Binary.Op.check_eq, M3CG_BinRd::check_eq),
        new Cmd(M3CG_Binary.Op.add_offset, M3CG_BinRd::add_offset),
        new Cmd(M3CG_Binary.Op.index_address, M3CG_BinRd::index_address),
        new Cmd(M3CG_Binary.Op.start_call_direct, M3CG_BinRd::start_call_direct),
        new Cmd(M3CG_Binary.Op.call_direct, M3CG_BinRd::call_direct),
        new Cmd(M3CG_Binary.Op.start_call_indirect, M3CG_BinRd::start_call_indirect),
        new Cmd(M3CG_Binary.Op.call_indirect, M3CG_BinRd::call_indirect),
        new Cmd(M3CG_Binary.Op.start_try, M3CG_BinRd::start_try),
        new Cmd(M3CG_Binary.Op.end_try, M3CG_BinRd::end_try),
        new Cmd(M3CG_Binary.Op.invoke_direct, M3CG_BinRd::invoke_direct),
        new Cmd(M3CG_Binary.Op.invoke_indirect, M3CG_BinRd::invoke_indirect),
        new Cmd(M3CG_Binary.Op.landing_pad, M3CG_BinRd::landing_pad),
        new Cmd(M3CG_Binary.Op.pop_param, M3CG_BinRd::pop_param),
        new Cmd(M3CG_Binary.Op.pop_struct, M3CG_BinRd::pop_struct),
        new Cmd(M3CG_Binary.Op.pop_static_link, M3CG_BinRd::pop_static_link),
        new Cmd(M3CG_Binary.Op.load_procedure, M3CG_BinRd::load_procedure),
        new Cmd(M3CG_Binary.Op.load_static_link, M3CG_BinRd::load_static_link),
        new Cmd(M3CG_Binary.Op.comment, M3CG_BinRd::comment),
        new Cmd(M3CG_Binary.Op.store_ordered, M3CG_BinRd::store_ordered),
        new Cmd(M3CG_Binary.Op.load_ordered, M3CG_BinRd::load_ordered),
        new Cmd(M3CG_Binary.Op.exchange, M3CG_BinRd::exchange),
        new Cmd(M3CG_Binary.Op.compare_exchange, M3CG_BinRd::compare_exchange),
        new Cmd(M3CG_Binary.Op.fence, M3CG_BinRd::fence),
        new Cmd(M3CG_Binary.Op.fetch_and_add, M3CG_BinRd::fetch_and_add),
        new Cmd(M3CG_Binary.Op.fetch_and_sub, M3CG_BinRd::fetch_and_sub),
        new Cmd(M3CG_Binary.Op.fetch_and_or, M3CG_BinRd::fetch_and_or),
        new Cmd(M3CG_Binary.Op.fetch_and_and, M3CG_BinRd::fetch_and_and),
        new Cmd(M3CG_Binary.Op.fetch_and_xor, M3CG_BinRd::fetch_and_xor),
        new Cmd(M3CG_Binary.Op.widechar_size, M3CG_BinRd::widechar_size)
    };

    static {
        for (int op = 0; op < CmdMap.length; op++) {
            AssertError.check(CmdMap[op].bop().ordinal() == op, "M3CG_BinRd");
        }
    }

    /**
     * Reads the binary intermediate code from 'rd' and calls the
     * corresponding methods of 'cg'.
     */
    public static void inhale(InputStream rd, M3CG.T cg, M3ID.State ids, Target.State target,
                              TargetMap.State targetMap, PrintStream out) {
        State s = new State();
        long op;
        long cmd;
        s.rd = rd;
        s.cg = cg;
        s.buf = new byte[BufSize];
        s.buf_len = 0;
        s.buf_ptr = 0;
        s.hit_eof = false;
        s.vars = new M3CG.Var[400];
        s.procs = new M3CG.Proc[50];
        s.labels = new long[400];
        s.ids = ids;
        s.target = target;
        s.targetMap = targetMap;
        s.out = out;

        for (int i = 0; i < s.labels.length; i++) {
            s.labels[i] = M3CG.No_label;
        }

        cmd = scan_int(s);
        if (cmd != M3CG_Binary.Version) {
            error(s, "** bad intermediate code version: expected ",
                  M3CG_Binary.Version + ", but found ",
                  Long.toString(cmd));
        }

        for (;;) {
            op = getByte(s);
            if (s.hit_eof) {
                break;
            } else if (op < 0 || M3CG_Binary.Op.values().length - 1 < op) {
                error(s, "** bad intermediate code operator: ", Long.toString(op), null);
            } else {
                // Sic: CmdMap covers the operators up to widechar_size; a
                // larger one is a subscript fault.
                CmdMap[(int) op].proc().accept(s);
            }
        }
    }

    private static void error(State s, String a, String b, String c) {
        // <*FATAL Wr.Failure, Thread.Alerted*>
        String msg = M3CG_Wr.EOL + "** ERROR in M3CG_BinRd.Inhale: ";
        if (a != null) {
            msg = msg + a;
        }
        if (b != null) {
            msg = msg + b;
        }
        if (c != null) {
            msg = msg + c;
        }
        msg = msg + " **" + M3CG_Wr.EOL;
        s.out.print(msg);
        s.out.flush();
    }

    private static void error(State s, String a, String b) {
        error(s, a, b, null);
    }

    /*--------------------------------------------------------------- parsing ---*/

    private static int scan_name(State s) {
        String txt = scan_text(s);
        if (txt == null) {
            return M3ID.NoID;
        } else {
            return M3ID.add(s.ids, txt);
        }
    }

    private static String scan_text(State s) {
        long len = scan_int(s);
        StringBuilder txt;
        char[] buf = new char[256];
        if (len < 0) {
            return null;
        }
        txt = new StringBuilder();
        while (len > buf.length) {
            for (int i = 0; i < buf.length; i++) {
                buf[i] = (char) getByte(s);
            }
            txt.append(buf);
            len -= buf.length;
        }
        if (len > 0) {
            for (int i = 0; i <= len - 1; i++) {
                buf[i] = (char) getByte(s);
            }
            txt.append(buf, 0, (int) len);
        }
        return txt.toString();
    }

    private static CGType scan_type(State s) {
        int x = getByte(s);
        if (0 <= x && x <= CGType.values().length - 1) {
            return CGType.values()[x];
        } else {
            error(s, "illegal type: ", Integer.toString(x));
            return CGType.Int32;
        }
    }

    private static boolean scan_bool(State s) {
        int x = getByte(s);
        switch (x) {
            case 0:
                return false;
            case 1:
                return true;
            default:
                error(s, "illegal boolean: ", Integer.toString(x));
        }
        return true;
    }

    private static long scan_label(State s) {
        long val;
        long x;
        val = scan_int(s);
        if (val < 0) {
            return M3CG.No_label;
        }

        while (val > s.labels.length - 1) {
            expandLabels(s);
        }

        x = s.labels[(int) val];
        if (x == M3CG.No_label) {
            x = s.cg.next_label();
            s.labels[(int) val] = x;
        }
        return x;
    }

    private static void expandLabels(State s) {
        long[] nw = new long[2 * s.labels.length];
        System.arraycopy(s.labels, 0, nw, 0, s.labels.length);
        for (int i = s.labels.length; i < nw.length; i++) {
            nw[i] = M3CG.No_label;
        }
        s.labels = nw;
    }

    /** Scan_tipe; Port: the INTEGER is range-checked as a TypeUID. */
    private static int scan_tipe(State s) {
        return typeUID(scan_int(s));
    }

    private static M3CG.Var scan_var(State s) {
        long id = scan_int(s);
        if (id <= 0) {
            return null;
        } else {
            return s.vars[Math.toIntExact(id)];
        }
    }

    private static M3CG.Proc scan_proc(State s) {
        long id = scan_int(s);
        if (id <= 0) {
            return null;
        } else {
            return s.procs[Math.toIntExact(id)];
        }
    }

    private static M3CG.Sign scan_sign(State s) {
        long x = scan_int(s);
        if (x == M3CG.Sign.Positive.ordinal()) {
            return M3CG.Sign.Positive;
        } else if (x == M3CG.Sign.Negative.ordinal()) {
            return M3CG.Sign.Negative;
        } else if (x == M3CG.Sign.Unknown.ordinal()) {
            return M3CG.Sign.Unknown;
        } else {
            error(s, "bad sign: ", Long.toString(x));
        }
        return M3CG.Sign.Unknown;
    }

    private static Target.CallingConvention scan_callConv(State s) {
        int id = getByte(s);
        Target.CallingConvention cc = Target.conventionFromID(s.target, id);
        if (cc == null) {
            error(s, "unknown calling convention: ", Integer.toString(id));
        }
        return cc;
    }

    private static Target.Float scan_float(State s) {
        int i = getByte(s);
        Target.Precision pre = Target.Precision.Short;
        int n_bytes;
        int[] bytes = new int[4 * 4 + 1];
        Target.Float result = new Target.Float();
        switch (i) {
            case 0 -> pre = Target.Precision.Short;
            case 1 -> pre = Target.Precision.Long;
            case 2 -> pre = Target.Precision.Extended;
            default -> error(s, "bad floating-point precision: ", Integer.toString(i));
        }
        n_bytes = (int) (s.targetMap.Float_types[pre.ordinal()].size / 8);
        for (int x = 0; x <= n_bytes - 1; x++) {
            bytes[x] = getByte(s);
        }
        int[] sub = new int[n_bytes];
        System.arraycopy(bytes, 0, sub, 0, n_bytes);
        TFloat.fromBytes(s.targetMap, sub, pre, result);
        return result;
    }

    private static int[] scan_Tint(State s) {
        int i = getByte(s);
        int n_bytes;
        int sign;
        int shift;
        int[] val = TInt.newInt();
        int[] bite = TInt.newInt();
        boolean ok;
        switch (i) {
            case M3CG_Binary.Int1 -> { n_bytes = 1; sign = +1; }
            case M3CG_Binary.NInt1 -> { n_bytes = 1; sign = -1; }
            case M3CG_Binary.Int2 -> { n_bytes = 2; sign = +1; }
            case M3CG_Binary.NInt2 -> { n_bytes = 2; sign = -1; }
            case M3CG_Binary.Int4 -> { n_bytes = 4; sign = +1; }
            case M3CG_Binary.NInt4 -> { n_bytes = 4; sign = -1; }
            case M3CG_Binary.Int8 -> { n_bytes = 8; sign = +1; }
            case M3CG_Binary.NInt8 -> { n_bytes = 8; sign = -1; }
            default -> {
                ok = TInt.fromInt(i, val);
                AssertError.check(ok, "M3CG_BinRd");
                return val;
            }
        }

        TInt.assign(val, TInt.Zero);
        shift = 0;
        for (int k = 0; k <= n_bytes - 1; k++) {
            ok = TInt.fromInt(getByte(s), bite);
            AssertError.check(ok, "M3CG_BinRd");
            TWord.shift(bite, shift, bite);
            TWord.or(val, bite, val);
            shift += 8;
        }
        if (sign < 0) {
            TWord.subtract(TInt.Zero, val, val);
        }

        return val;
    }

    private static long scan_int(State s) {
        int i = getByte(s);
        int n_bytes;
        int sign;
        long val;
        int shift;
        switch (i) {
            case M3CG_Binary.Int1:
                return getByte(s);
            case M3CG_Binary.NInt1:
                return -getByte(s);
            case M3CG_Binary.Int2:
                n_bytes = 2;
                sign = +1;
                break;
            case M3CG_Binary.NInt2:
                n_bytes = 2;
                sign = -1;
                break;
            case M3CG_Binary.Int4:
                n_bytes = 4;
                sign = +1;
                break;
            case M3CG_Binary.NInt4:
                n_bytes = 4;
                sign = -1;
                break;
            case M3CG_Binary.Int8:
                n_bytes = 8;
                sign = +1;
                break;
            case M3CG_Binary.NInt8:
                n_bytes = 8;
                sign = -1;
                break;
            default:
                return i;
        }
        val = 0;
        shift = 0;
        for (int x = 0; x <= n_bytes - 1; x++) {
            val = val | ((long) getByte(s) << shift);
            shift += 8;
        }
        return sign * val;
    }

    private static RuntimeError scan_error(State s) {
        long x = scan_int(s);
        if (x < 0 || RuntimeError.values().length - 1 < x) {
            error(s, "bad error code: ", Long.toString(x));
            x = 0;
        }
        return RuntimeError.values()[(int) x];
    }

    /** Byte = [0..255] */
    private static int getByte(State s) {
        int val;
        if (s.buf_ptr >= s.buf_len) {
            refillBuffer(s);
        }
        val = s.buf[s.buf_ptr] & 0xff;
        s.buf_ptr++;
        return val;
    }

    private static void refillBuffer(State s) {
        // <*FATAL Rd.Failure, Thread.Alerted*>
        s.buf_ptr = 0;
        try {
            // Rd.GetSub: as many bytes as fit, up to the end of the input.
            int n = 0;
            while (n < s.buf.length) {
                int k = s.rd.read(s.buf, n, s.buf.length - n);
                if (k < 0) {
                    break;
                }
                n += k;
            }
            s.buf_len = n;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (s.buf_len <= 0) {
            s.hit_eof = true;
            s.buf_len = 1;
            s.buf[0] = 0;
        }
    }

    /** A TypeUID ([-16_7fffffff-1 .. 16_7fffffff]) from an INTEGER. */
    private static int typeUID(long x) {
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE) {
            throw new RangeError("M3CG_BinRd");
        }
        return (int) x;
    }

    /** A Frequency ([0..100]) from an INTEGER. */
    private static int frequency(long x) {
        if (x < 0 || x > 100) {
            throw new RangeError("M3CG_BinRd");
        }
        return (int) x;
    }

    /** VAL (x, MemoryOrder). */
    private static MemoryOrder memoryOrder(long x) {
        if (x < 0 || x >= MemoryOrder.values().length) {
            throw new RangeError("M3CG_BinRd");
        }
        return MemoryOrder.values()[(int) x];
    }

    /*----------------------------------------------------- compilation units ---*/

    private static void begin_unit(State s) {
        var optimize = scan_int(s);
        s.cg.begin_unit(optimize);
    }

    private static void end_unit(State s) {
        s.cg.end_unit();
    }

    private static void import_unit(State s) {
        var name = scan_name(s);
        s.cg.import_unit(name);
    }

    private static void export_unit(State s) {
        var name = scan_name(s);
        s.cg.export_unit(name);
    }

    /*------------------------------------------------ debugging line numbers ---*/

    private static void set_source_file(State s) {
        var file = scan_text(s);
        s.cg.set_source_file(file);
    }

    private static void set_source_line(State s) {
        var line = scan_int(s);
        s.cg.set_source_line(line);
    }

    /*------------------------------------------- debugging type declarations ---*/

    private static void declare_typename(State s) {
        var type = scan_tipe(s);
        var name = scan_name(s);
        s.cg.declare_typename(type, name);
    }

    private static void declare_array(State s) {
        var type = scan_tipe(s);
        var index = scan_tipe(s);
        var elt = scan_tipe(s);
        var size = scan_int(s);
        s.cg.declare_array(type, index, elt, size);
    }

    private static void declare_open_array(State s) {
        var type = scan_tipe(s);
        var elt = scan_tipe(s);
        var size = scan_int(s);
        s.cg.declare_open_array(type, elt, size);
    }

    private static void declare_enum(State s) {
        var type = scan_tipe(s);
        var n_elts = scan_int(s);
        var size = scan_int(s);
        s.cg.declare_enum(type, n_elts, size);
    }

    private static void declare_enum_elt(State s) {
        var name = scan_name(s);
        s.cg.declare_enum_elt(name);
    }

    private static void declare_packed(State s) {
        var type = scan_tipe(s);
        var size = scan_int(s);
        var base = scan_tipe(s);
        s.cg.declare_packed(type, size, base);
    }

    private static void declare_record(State s) {
        var type = scan_tipe(s);
        var size = scan_int(s);
        var n_fields = scan_int(s);
        s.cg.declare_record(type, size, n_fields);
    }

    private static void declare_field(State s) {
        var name = scan_name(s);
        var offset = scan_int(s);
        var size = scan_int(s);
        var type = scan_tipe(s);
        s.cg.declare_field(name, offset, size, type);
    }

    private static void declare_set(State s) {
        var type = scan_tipe(s);
        var domain = scan_tipe(s);
        var size = scan_int(s);
        s.cg.declare_set(type, domain, size);
    }

    private static void declare_subrange(State s) {
        var type = scan_tipe(s);
        var domain = scan_tipe(s);
        var min = scan_Tint(s);
        var max = scan_Tint(s);
        var size = scan_int(s);
        s.cg.declare_subrange(type, domain, min, max, size);
    }

    private static void declare_pointer(State s) {
        var type = scan_tipe(s);
        var target = scan_tipe(s);
        var brand = scan_text(s);
        var traced = scan_bool(s);
        s.cg.declare_pointer(type, target, brand, traced);
    }

    private static void declare_indirect(State s) {
        var type = scan_tipe(s);
        var target = scan_tipe(s);
        // TODO target_typename but there are no downstream users
        s.cg.declare_indirect(type, target);
    }

    private static void declare_proctype(State s) {
        var type = scan_tipe(s);
        var n_formals = scan_int(s);
        var result = scan_tipe(s);
        var n_raises = scan_int(s);
        var calling = scan_callConv(s);
        int result_typename = M3ID.NoID; // TODO typename but it is not used downstream and can be omitted indefinitely
        s.cg.declare_proctype(type, n_formals, result, n_raises, calling, result_typename);
    }

    private static void declare_formal(State s) {
        var name = scan_name(s);
        var type = scan_tipe(s);
        int typename = M3ID.NoID; // TODO typename but it is not used downstream and can be omitted indefinitely
        s.cg.declare_formal(name, type, typename);
    }

    private static void declare_raises(State s) {
        var name = scan_name(s);
        s.cg.declare_raises(name);
    }

    private static void declare_object(State s) {
        var type = scan_tipe(s);
        var super_ = scan_tipe(s);
        var brand = scan_text(s);
        var traced = scan_bool(s);
        var n_fields = scan_int(s);
        var n_methods = scan_int(s);
        var field_size = scan_int(s);
        var field_offset = scan_int(s);
        var method_offset = scan_int(s);
        s.cg.declare_object(type, super_, brand, traced, n_fields, n_methods, field_size, field_offset, method_offset);
    }

    private static void declare_method(State s) {
        var name = scan_name(s);
        var type = scan_tipe(s);
        s.cg.declare_method(name, type);
    }

    private static void declare_opaque(State s) {
        var type = scan_tipe(s);
        var super_ = scan_tipe(s);
        s.cg.declare_opaque(type, super_);
    }

    private static void reveal_opaque(State s) {
        var lhs = scan_tipe(s);
        var rhs = scan_tipe(s);
        s.cg.reveal_opaque(lhs, rhs);
    }

    private static void declare_exception(State s) {
        var name = scan_name(s);
        var arg_type = scan_tipe(s);
        var raise_proc = scan_bool(s);
        var base = scan_var(s);
        var offset = scan_int(s);
        s.cg.declare_exception(name, arg_type, raise_proc, base, offset);
    }

    private static void widechar_size(State s) {
        var size = scan_int(s);
        s.cg.widechar_size(size);
    }

    /*--------------------------------------------------------- runtime hooks ---*/

    private static void set_runtime_proc(State s) {
        var name = scan_name(s);
        var proc = scan_proc(s);
        s.cg.set_runtime_proc(name, proc);
    }

    /*------------------------------------------------- variable declarations ---*/

    private static void addVar(State s, long id, M3CG.Var v) {
        while (id >= s.vars.length) {
            expandVars(s);
        }
        s.vars[(int) id] = v;
    }

    private static void expandVars(State s) {
        M3CG.Var[] nw = new M3CG.Var[2 * s.vars.length];
        System.arraycopy(s.vars, 0, nw, 0, s.vars.length);
        s.vars = nw;
    }

    private static void import_global(State s) {
        var name = scan_name(s);
        var size = scan_int(s);
        var align = scan_int(s);
        var type = scan_type(s);
        var m3t = scan_tipe(s);
        var v = scan_int(s);
        // TODO typename
        addVar(s, v, s.cg.import_global(name, size, align, type, m3t));
    }

    private static void declare_segment(State s) {
        var name = scan_name(s);
        var m3t = scan_tipe(s);
        var is_const = scan_bool(s);
        var v = scan_int(s);
        addVar(s, v, s.cg.declare_segment(name, m3t, is_const));
    }

    private static void bind_segment(State s) {
        var v = scan_var(s);
        var size = scan_int(s);
        var align = scan_int(s);
        var type = scan_type(s);
        var export = scan_bool(s);
        var init = scan_bool(s);
        s.cg.bind_segment(v, size, align, type, export, init);
    }

    private static void declare_global(State s) {
        var name = scan_name(s);
        var size = scan_int(s);
        var align = scan_int(s);
        var type = scan_type(s);
        var m3t = scan_tipe(s);
        var export = scan_bool(s);
        var init = scan_bool(s);
        var v = scan_int(s);
        addVar(s, v, s.cg.declare_global(name, size, align, type, m3t, export, init));
    }

    private static void declare_constant(State s) {
        var name = scan_name(s);
        var size = scan_int(s);
        var align = scan_int(s);
        var type = scan_type(s);
        var m3t = scan_tipe(s);
        var export = scan_bool(s);
        var init = scan_bool(s);
        var v = scan_int(s);
        addVar(s, v, s.cg.declare_constant(name, size, align, type, m3t, export,init));
    }

    private static void declare_local(State s) {
        var name = scan_name(s);
        var size = scan_int(s);
        var align = scan_int(s);
        var type = scan_type(s);
        var m3t = scan_tipe(s);
        var in_mem = scan_bool(s);
        var up_lev = scan_bool(s);
        var freq = frequency(scan_int(s));
        var v = scan_int(s);
        addVar(s, v, s.cg.declare_local(name, size, align, type, m3t, in_mem, up_lev, freq));
    }

    private static void declare_param(State s) {
        var name = scan_name(s);
        var size = scan_int(s);
        var align = scan_int(s);
        var type = scan_type(s);
        var m3t = scan_tipe(s);
        var in_mem = scan_bool(s);
        var up_lev = scan_bool(s);
        var freq = frequency(scan_int(s));
        var v = scan_int(s);
        int typename = M3ID.NoID; // TODO typename but it is not used downstream and can be omitted indefinitely
        addVar(s, v, s.cg.declare_param(name, size, align, type, m3t, in_mem, up_lev, freq, typename));
    }

    private static void declare_temp(State s) {
        var size = scan_int(s);
        var align = scan_int(s);
        var type = scan_type(s);
        var in_mem = scan_bool(s);
        var v = scan_int(s);
        addVar(s, v, s.cg.declare_temp(size, align, type, in_mem));
    }

    private static void free_temp(State s) {
        var v = scan_var(s);
        s.cg.free_temp(v);
    }

    /*---------------------------------------- static variable initialization ---*/

    private static void begin_init(State s) {
        var v = scan_var(s);
        s.cg.begin_init(v);
    }

    private static void end_init(State s) {
        var v = scan_var(s);
        s.cg.end_init(v);
    }

    private static void init_int(State s) {
        var offset = scan_int(s);
        var value = scan_Tint(s);
        var type = scan_type(s);
        s.cg.init_int(offset, value, type);
    }

    private static void init_proc(State s) {
        var offset = scan_int(s);
        var value = scan_proc(s);
        s.cg.init_proc(offset, value);
    }

    private static void init_label(State s) {
        var offset = scan_int(s);
        var value = scan_label(s);
        s.cg.init_label(offset, value);
    }

    private static void init_var(State s) {
        var offset = scan_int(s);
        var value = scan_var(s);
        var bias = scan_int(s);
        s.cg.init_var(offset, value, bias);
    }

    private static void init_offset(State s) {
        var offset = scan_int(s);
        var value = scan_var(s);
        s.cg.init_offset(offset, value);
    }

    private static void init_chars(State s) {
        var offset = scan_int(s);
        var value = scan_text(s);
        s.cg.init_chars(offset, value);
    }

    private static void init_float(State s) {
        var offset = scan_int(s);
        var value = scan_float(s);
        s.cg.init_float(offset, value);
    }

    /*------------------------------------------------------------ procedures ---*/

    private static void addProc(State s, long id, M3CG.Proc p) {
        while (id >= s.procs.length) {
            expandProcs(s);
        }
        s.procs[(int) id] = p;
    }

    private static void expandProcs(State s) {
        M3CG.Proc[] nw = new M3CG.Proc[2 * s.procs.length];
        System.arraycopy(s.procs, 0, nw, 0, s.procs.length);
        s.procs = nw;
    }

    private static void import_procedure(State s) {
        var name = scan_name(s);
        var n_params = scan_int(s);
        var ret_type = scan_type(s);
        var calling = scan_callConv(s);
        var p = scan_int(s);
        // TODO return_typeid, return_typename but nobody uses it
        addProc(s, p, s.cg.import_procedure(name, n_params, ret_type, calling));
    }

    private static void declare_procedure(State s) {
        var name = scan_name(s);
        var n_params = scan_int(s);
        var ret_type = scan_type(s);
        var level = scan_int(s);
        var calling = scan_callConv(s);
        var export = scan_bool(s);
        var parent = scan_proc(s);
        var p = scan_int(s);
        // TODO return_typeid, return_typename but it is not used downstream and can be omitted indefinitely
        addProc(s, p, s.cg.declare_procedure(name, n_params, ret_type, level, calling, export, parent));
    }

    private static void begin_procedure(State s) {
        var p = scan_proc(s);
        s.cg.begin_procedure(p);
    }

    private static void end_procedure(State s) {
        var p = scan_proc(s);
        s.cg.end_procedure(p);
    }

    private static void begin_block(State s) {
        s.cg.begin_block();
    }

    private static void end_block(State s) {
        s.cg.end_block();
    }

    private static void note_procedure_origin(State s) {
        var p = scan_proc(s);
        s.cg.note_procedure_origin(p);
    }

    /*------------------------------------------------------------ statements ---*/

    private static void set_label(State s) {
        var label = scan_label(s);
        var barrier = scan_bool(s);
        s.cg.set_label(label, barrier);
    }

    private static void jump(State s) {
        var label = scan_label(s);
        s.cg.jump(label);
    }

    private static void if_true(State s) {
        var type = scan_type(s);
        var label = scan_label(s);
        var freq = frequency(scan_int(s));
        s.cg.if_true(type, label, freq);
    }

    private static void if_false(State s) {
        var type = scan_type(s);
        var label = scan_label(s);
        var freq = frequency(scan_int(s));
        s.cg.if_false(type, label, freq);
    }

    private static void if_eq(State s) {
        if_compare(s, CompareOp.EQ);
    }

    private static void if_ne(State s) {
        if_compare(s, CompareOp.NE);
    }

    private static void if_gt(State s) {
        if_compare(s, CompareOp.GT);
    }

    private static void if_ge(State s) {
        if_compare(s, CompareOp.GE);
    }

    private static void if_lt(State s) {
        if_compare(s, CompareOp.LT);
    }

    private static void if_le(State s) {
        if_compare(s, CompareOp.LE);
    }

    private static void if_compare(State s, CompareOp op) {
        var type = scan_type(s);
        var label = scan_label(s);
        var freq = frequency(scan_int(s));
        s.cg.if_compare(type, op, label, freq);
    }

    private static void case_jump(State s) {
        var type = scan_type(s);
        var n = scan_int(s);
        long[] x = new long[Math.toIntExact(n)];
        for (int i = 0; i <= n - 1; i++) {
            x[i] = scan_label(s);
        }
        s.cg.case_jump(type, x);
    }

    private static void exit_proc(State s) {
        var type = scan_type(s);
        s.cg.exit_proc(type);
    }

    /*------------------------------------------------------------ load/store ---*/

    private static void load(State s) {
        var v = scan_var(s);
        var offset = scan_int(s);
        var src = scan_type(s);
        var dest = scan_type(s);
        s.cg.load(v, offset, src, dest);
    }

    private static void store(State s) {
        var v = scan_var(s);
        var offset = scan_int(s);
        var src = scan_type(s);
        var dest = scan_type(s);
        s.cg.store(v, offset, src, dest);
    }

    private static void load_address(State s) {
        var v = scan_var(s);
        var offset = scan_int(s);
        s.cg.load_address(v, offset);
    }

    private static void load_indirect(State s) {
        var offset = scan_int(s);
        var src = scan_type(s);
        var dest = scan_type(s);
        s.cg.load_indirect(offset, src, dest);
    }

    private static void store_indirect(State s) {
        var offset = scan_int(s);
        var src = scan_type(s);
        var dest = scan_type(s);
        s.cg.store_indirect(offset, src, dest);
    }

    /*-------------------------------------------------------------- literals ---*/

    private static void load_nil(State s) {
        s.cg.load_nil();
    }

    private static void load_integer(State s) {
        var type = scan_type(s);
        var value = scan_Tint(s);
        s.cg.load_integer(type, value);
    }

    private static void load_float(State s) {
        var type = scan_type(s);
        var value = scan_float(s);
        s.cg.load_float(type, value);
    }

    /*------------------------------------------------------------ arithmetic ---*/

    private static void eq(State s) {
        compare(s, CompareOp.EQ);
    }

    private static void ne(State s) {
        compare(s, CompareOp.NE);
    }

    private static void gt(State s) {
        compare(s, CompareOp.GT);
    }

    private static void ge(State s) {
        compare(s, CompareOp.GE);
    }

    private static void lt(State s) {
        compare(s, CompareOp.LT);
    }

    private static void le(State s) {
        compare(s, CompareOp.LE);
    }

    private static void compare(State s, CompareOp op) {
        var src = scan_type(s);
        var dest = scan_type(s);
        s.cg.compare(src, dest, op);
    }

    private static void add(State s) {
        var type = scan_type(s);
        s.cg.add(type);
    }

    private static void subtract(State s) {
        var type = scan_type(s);
        s.cg.subtract(type);
    }

    private static void multiply(State s) {
        var type = scan_type(s);
        s.cg.multiply(type);
    }

    private static void divide(State s) {
        var type = scan_type(s);
        s.cg.divide(type);
    }

    private static void div(State s) {
        var type = scan_type(s);
        var a = scan_sign(s);
        var b = scan_sign(s);
        s.cg.div(type, a, b);
    }

    private static void mod(State s) {
        var type = scan_type(s);
        var a = scan_sign(s);
        var b = scan_sign(s);
        s.cg.mod(type, a, b);
    }

    private static void negate(State s) {
        var type = scan_type(s);
        s.cg.negate(type);
    }

    private static void abs(State s) {
        var type = scan_type(s);
        s.cg.abs(type);
    }

    private static void max(State s) {
        var type = scan_type(s);
        s.cg.max(type);
    }

    private static void min(State s) {
        var type = scan_type(s);
        s.cg.min(type);
    }

    private static void round(State s) {
        cvt_int(s, ConvertOp.Round);
    }

    private static void trunc(State s) {
        cvt_int(s, ConvertOp.Trunc);
    }

    private static void floor(State s) {
        cvt_int(s, ConvertOp.Floor);
    }

    private static void ceiling(State s) {
        cvt_int(s, ConvertOp.Ceiling);
    }

    private static void cvt_int(State s, ConvertOp op) {
        var src = scan_type(s);
        var dest = scan_type(s);
        s.cg.cvt_int(src, dest, op);
    }

    private static void cvt_float(State s) {
        var src = scan_type(s);
        var dest = scan_type(s);
        s.cg.cvt_float(src, dest);
    }

    /*------------------------------------------------------------------ sets ---*/

    private static void set_union(State s) {
        var size = scan_int(s);
        s.cg.set_union(size);
    }

    private static void set_difference(State s) {
        var size = scan_int(s);
        s.cg.set_difference(size);
    }

    private static void set_intersection(State s) {
        var size = scan_int(s);
        s.cg.set_intersection(size);
    }

    private static void set_sym_difference(State s) {
        var size = scan_int(s);
        s.cg.set_sym_difference(size);
    }

    private static void set_member(State s) {
        var size = scan_int(s);
        var type = scan_type(s);
        s.cg.set_member(size, type);
    }

    private static void set_eq(State s) {
        set_compare(s, CompareOp.EQ);
    }

    private static void set_ne(State s) {
        set_compare(s, CompareOp.NE);
    }

    private static void set_gt(State s) {
        set_compare(s, CompareOp.GT);
    }

    private static void set_ge(State s) {
        set_compare(s, CompareOp.GE);
    }

    private static void set_lt(State s) {
        set_compare(s, CompareOp.LT);
    }

    private static void set_le(State s) {
        set_compare(s, CompareOp.LE);
    }

    private static void set_compare(State s, CompareOp op) {
        var size = scan_int(s);
        var type = scan_type(s);
        s.cg.set_compare(size, op, type);
    }

    private static void set_range(State s) {
        var size = scan_int(s);
        var type = scan_type(s);
        s.cg.set_range(size, type);
    }

    private static void set_singleton(State s) {
        var size = scan_int(s);
        var type = scan_type(s);
        s.cg.set_singleton(size, type);
    }

    /*------------------------------------------------- Word.T bit operations ---*/

    private static void not(State s) {
        var type = scan_type(s);
        s.cg.not(type);
    }

    private static void and(State s) {
        var type = scan_type(s);
        s.cg.and(type);
    }

    private static void or(State s) {
        var type = scan_type(s);
        s.cg.or(type);
    }

    private static void xor(State s) {
        var type = scan_type(s);
        s.cg.xor(type);
    }

    private static void shift(State s) {
        var type = scan_type(s);
        s.cg.shift(type);
    }

    private static void shift_left(State s) {
        var type = scan_type(s);
        s.cg.shift_left(type);
    }

    private static void shift_right(State s) {
        var type = scan_type(s);
        s.cg.shift_right(type);
    }

    private static void rotate(State s) {
        var type = scan_type(s);
        s.cg.rotate(type);
    }

    private static void rotate_left(State s) {
        var type = scan_type(s);
        s.cg.rotate_left(type);
    }

    private static void rotate_right(State s) {
        var type = scan_type(s);
        s.cg.rotate_right(type);
    }

    private static void widen(State s) {
        var sign_extend = scan_bool(s);
        s.cg.widen(sign_extend);
    }

    private static void chop(State s) {
        s.cg.chop();
    }

    private static void extract(State s) {
        var type = scan_type(s);
        var sign_extend = scan_bool(s);
        s.cg.extract(type, sign_extend);
    }

    private static void extract_n(State s) {
        var type = scan_type(s);
        var sign_extend = scan_bool(s);
        var width = scan_int(s);
        s.cg.extract_n(type, sign_extend, width);
    }

    private static void extract_mn(State s) {
        var type = scan_type(s);
        var sign_extend = scan_bool(s);
        var offset = scan_int(s);
        var width = scan_int(s);
        s.cg.extract_mn(type, sign_extend, offset, width);
    }

    private static void insert(State s) {
        var type = scan_type(s);
        s.cg.insert(type);
    }

    private static void insert_n(State s) {
        var type = scan_type(s);
        var width = scan_int(s);
        s.cg.insert_n(type, width);
    }

    private static void insert_mn(State s) {
        var type = scan_type(s);
        var offset = scan_int(s);
        var width = scan_int(s);
        s.cg.insert_mn(type, offset, width);
    }

    /*------------------------------------------------ misc. stack/memory ops ---*/

    private static void swap(State s) {
        var a = scan_type(s);
        var b = scan_type(s);
        s.cg.swap(a, b);
    }

    private static void pop(State s) {
        var type = scan_type(s);
        s.cg.pop(type);
    }

    private static void copy_n(State s) {
        var cnt_type = scan_type(s);
        var type = scan_type(s);
        var overlap = scan_bool(s);
        s.cg.copy_n(cnt_type, type, overlap);
    }

    private static void copy(State s) {
        var cnt = scan_int(s);
        var type = scan_type(s);
        var overlap = scan_bool(s);
        s.cg.copy(cnt, type, overlap);
    }

    private static void zero_n(State s) {
        var cnt_type = scan_type(s);
        var type = scan_type(s);
        s.cg.zero_n(cnt_type, type);
    }

    private static void zero(State s) {
        var cnt = scan_int(s);
        var type = scan_type(s);
        s.cg.zero(cnt, type);
    }

    /*----------------------------------------------------------- conversions ---*/

    private static void loophole(State s) {
        var from = scan_type(s);
        var two = scan_type(s);
        s.cg.loophole(from, two);
    }

    /*------------------------------------------------ traps & runtime checks ---*/

    private static void abort(State s) {
        var code = scan_error(s);
        s.cg.abort(code);
    }

    private static void check_nil(State s) {
        var code = scan_error(s);
        s.cg.check_nil(code);
    }

    private static void check_lo(State s) {
        var type = scan_type(s);
        var i = scan_Tint(s);
        var code = scan_error(s);
        s.cg.check_lo(type, i, code);
    }

    private static void check_hi(State s) {
        var type = scan_type(s);
        var i = scan_Tint(s);
        var code = scan_error(s);
        s.cg.check_hi(type, i, code);
    }

    private static void check_range(State s) {
        var type = scan_type(s);
        var a = scan_Tint(s);
        var b = scan_Tint(s);
        var code = scan_error(s);
        s.cg.check_range(type, a, b, code);
    }

    private static void check_index(State s) {
        var type = scan_type(s);
        var code = scan_error(s);
        s.cg.check_index(type, code);
    }

    private static void check_eq(State s) {
        var type = scan_type(s);
        var code = scan_error(s);
        s.cg.check_eq(type, code);
    }

    /*---------------------------------------------------- address arithmetic ---*/

    private static void add_offset(State s) {
        var i = scan_int(s);
        s.cg.add_offset(i);
    }

    private static void index_address(State s) {
        var type = scan_type(s);
        var size = scan_int(s);
        s.cg.index_address(type, size);
    }

    /*------------------------------------------------------- procedure calls ---*/

    private static void start_call_direct(State s) {
        var p = scan_proc(s);
        var level = scan_int(s);
        var type = scan_type(s);
        s.cg.start_call_direct(p, level, type);
    }

    private static void start_call_indirect(State s) {
        var type = scan_type(s);
        var calling = scan_callConv(s);
        s.cg.start_call_indirect(type, calling);
    }

    private static void pop_param(State s) {
        var type = scan_type(s);
        s.cg.pop_param(type);
    }

    private static void pop_struct(State s) {
        var type = scan_tipe(s);
        var size = scan_int(s);
        var align = scan_int(s);
        s.cg.pop_struct(type, size, align);
    }

    private static void pop_static_link(State s) {
        s.cg.pop_static_link();
    }

    private static void call_direct(State s) {
        var p = scan_proc(s);
        var type = scan_type(s);
        s.cg.call_direct(p, type);
    }

    private static void call_indirect(State s) {
        var type = scan_type(s);
        var calling = scan_callConv(s);
        s.cg.call_indirect(type, calling);
    }

    private static void start_try(State s) {
        s.cg.start_try();
    }

    private static void end_try(State s) {
        s.cg.end_try();
    }

    private static void invoke_direct(State s) {
        var p = scan_proc(s);
        var type = scan_type(s);
        var next = scan_label(s);
        var handler = scan_label(s);
        s.cg.invoke_direct(p, type, next, handler);
    }

    private static void invoke_indirect(State s) {
        var type = scan_type(s);
        var calling = scan_callConv(s);
        var next = scan_label(s);
        var handler = scan_label(s);
        s.cg.invoke_indirect(type, calling, next, handler);
    }

    private static void landing_pad(State s) {
        var type = scan_type(s);
        var handler = scan_label(s);
        var n = scan_int(s);
        int[] x = new int[Math.toIntExact(n)];
        for (int i = 0; i <= n - 1; i++) {
            x[i] = typeUID(scan_int(s));
        }
        s.cg.landing_pad(type, handler, x);
    }

    /*------------------------------------------- procedure and closure types ---*/

    private static void load_procedure(State s) {
        var p = scan_proc(s);
        s.cg.load_procedure(p);
    }

    private static void load_static_link(State s) {
        var p = scan_proc(s);
        s.cg.load_static_link(p);
    }

    /*----------------------------------------------------------------- misc. ---*/

    private static void comment(State s) {
        var x = scan_text(s);
        s.cg.comment(x);
    }

    /*--------------------------------------------------------------- atomics ---*/

    private static void store_ordered(State s) {
        var src = scan_type(s);
        var dest = scan_type(s);
        var order = scan_int(s);
        s.cg.store_ordered(src, dest, memoryOrder(order));
    }

    private static void load_ordered(State s) {
        var src = scan_type(s);
        var dest = scan_type(s);
        var order = scan_int(s);
        s.cg.load_ordered(src, dest, memoryOrder(order));
    }

    private static void exchange(State s) {
        var src = scan_type(s);
        var dest = scan_type(s);
        var order = scan_int(s);
        s.cg.exchange(src, dest, memoryOrder(order));
    }

    private static void compare_exchange(State s) {
        var src = scan_type(s);
        var dest = scan_type(s);
        var res = scan_type(s);
        var success = scan_int(s);
        var failure = scan_int(s);
        s.cg.compare_exchange(src, dest, res, memoryOrder(success), memoryOrder(failure));
    }

    private static void fence(State s) {
        var order = scan_int(s);
        s.cg.fence(memoryOrder(order));
    }

    private static void fetch_and_op(State s, AtomicOp op) {
        var src = scan_type(s);
        var dest = scan_type(s);
        var order = scan_int(s);
        s.cg.fetch_and_op(op, src, dest, memoryOrder(order));
    }

    private static void fetch_and_add(State s) {
        fetch_and_op(s, AtomicOp.Add);
    }

    private static void fetch_and_sub(State s) {
        fetch_and_op(s, AtomicOp.Sub);
    }

    private static void fetch_and_or(State s) {
        fetch_and_op(s, AtomicOp.Or);
    }

    private static void fetch_and_and(State s) {
        fetch_and_op(s, AtomicOp.And);
    }

    private static void fetch_and_xor(State s) {
        fetch_and_op(s, AtomicOp.Xor);
    }
}
