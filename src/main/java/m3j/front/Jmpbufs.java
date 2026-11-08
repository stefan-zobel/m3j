// Java port of CM3's m3-sys/m3front/src/misc/Jmpbufs.i3 and Jmpbufs.m3.
// Original: the files carry no copyright line.
// Distributed with m3front under the Critical Mass license in licenses/COPYRIGHT-CMASS.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: Jmpbufs.i3
// File: Jmpbufs.m3

/**
 * Manage jmp_bufs used by some versions of exception handling
 * -- the type passed to setjmp/longjmp.
 * In particular, count the number of TRYs in a function.
 * At the start of a function, allocate a jmpbuf per TRY.
 * At the n'th TRY, use the n'th jmpbuf.
 *
 * <p>Allocation is done wither with alloca(external variable * n),
 * or in future, for the C backend only, using "jmp_buf".
 */
public final class Jmpbufs {
    private Jmpbufs() {}

    public static final class CheckState {
        public Proc proc = null;
        public long try_count = 0;
        /** Port: REF CheckState; the referent is never modified. */
        public CheckState save = null;

        public CheckState() {}

        public CheckState copy() {
            CheckState r = new CheckState();
            r.proc = proc;
            r.try_count = try_count;
            r.save = save;
            return r;
        }

        /** Port: the assignment {@code this := x}. */
        public void assign(CheckState x) {
            proc = x.proc;
            try_count = x.try_count;
            save = x.save;
        }
    }

    /** procedure or module */
    public static final class Proc {
        public long try_count = 0;
        public M3CG.Var[] jmpbufs = null;
        public int name = M3ID.NoID;
    }

    /** opaque */
    public static final class Try {
        public Proc proc = null;
        public long try_index = 0;

        public Try() {}

        public Try copy() {
            Try r = new Try();
            r.proc = proc;
            r.try_index = try_index;
            return r;
        }
    }

    private static void debugPrint(String t) {
        // Error.Warn (2, t);
    }

    public static Jmpbufs.Proc checkProcPush(Jmpbufs.CheckState cs, int name) {
        Proc proc = new Proc();
        CheckState save = null;
        debugPrint("CheckProcPush " + idToText(name));
        // Port: cs # CheckState{} compares all fields with the defaults.
        if (cs.proc != null || cs.try_count != 0 || cs.save != null) {
            save = cs.copy();
        }
        cs.save = save;
        cs.proc = proc;
        cs.try_count = 0;
        proc.name = name;
        return proc;
    }

    public static Jmpbufs.Proc checkProcPush(Jmpbufs.CheckState cs) {
        return checkProcPush(cs, 0);
    }

    public static void checkProcPop(Jmpbufs.CheckState cs, Jmpbufs.Proc proc) {
        debugPrint("CheckProcPop 1 " + idToText(proc.name) + " try_count:"
                   + Long.toString(cs.try_count));
        proc.try_count = cs.try_count;
        if (cs.save != null) {
            cs.assign(cs.save);
        } else {
            cs.assign(new CheckState());
        }
        if (cs.proc != null) {
            debugPrint("CheckProcPop 2 " + idToText(cs.proc.name) + " try_count:"
                       + Long.toString(cs.try_count));
        }
    }

    public static void checkTry(Jmpbufs.CheckState cs, Jmpbufs.Try try_) {
        debugPrint("CheckTry " + idToText(cs.proc.name) + " try_index:"
                   + Long.toString(cs.try_count));
        try_.proc = cs.proc;
        try_.try_index = cs.try_count;
        cs.try_count++;
    }

    public static M3CG.Var compileTryGetJmpbuf(Jmpbufs.Try try_) {
        long try_index = try_.try_index;
        if (!Target.Alloca_jmpbuf) {
            return null;
        }
        // Sic: try.proc is dereferenced before the check for NIL below.
        debugPrint("CompileTryGetJmpbuf " + idToText(try_.proc.name));
        if (try_.proc == null) {
            Error.msg("CompileTryGetJmpbuf try.proc = NIL");
        }
        AssertError.check(try_.proc != null, "Jmpbufs");
        if (try_.proc.jmpbufs == null) {
            Error.msg("CompileTryGetJmpbuf try.proc.jmpbufs = NIL");
        }
        AssertError.check(try_.proc.jmpbufs != null, "Jmpbufs");
        if (try_index >= try_.proc.jmpbufs.length) {
            Error.msg("CompileTryGetJmpbuf try_index:" + Long.toString(try_index)
                      + " out of bounds:" + Long.toString(try_.proc.jmpbufs.length));
        }
        AssertError.check(try_index < try_.proc.jmpbufs.length, "Jmpbufs");
        return try_.proc.jmpbufs[(int) try_.try_index];
    }

    private static String idToText(int id) {
        String text = M3ID.toText(M3.ids, id);
        if (text == null) {
            text = "";
        }
        return text;
    }

    public static void compileProcAllocateJmpbufs(Jmpbufs.Proc p) {
        Module.T module;
        M3CG.Proc alloca;
        M3CG.Var size;
        long try_count;
        if ((!Target.Alloca_jmpbuf) || M3.target.Has_stack_walker) {
            return;
        }
        if (p == null) {
            return;
        }
        module = Module.current();
        alloca = Module.getAlloca(module);
        size = Module.getJmpbufSize(module);
        try_count = p.try_count;
        AssertError.check(try_count >= 0, "Jmpbufs");
        CG.comment(-1, false, "AllocateJmpbufs " + idToText(p.name)
                              + " try_count:" + Long.toString(try_count));
        if (try_count == 0) {
            return;
        }

        // We are going to alloca(size * n), where n is the number of TRYs;
        // then each TRY will use its jmp_buf.  As the jmp_buf size is not a
        // constant here, multiplication n * jmp_buf is not efficient.
        // Therefore, use addition to precompute the n pointers (TRY can occur
        // in a loop -- making it more valuable to store the individual
        // pointers.)

        p.jmpbufs = new M3CG.Var[(int) try_count];
        for (long i = 0; i <= try_count - 1; i++) {
            p.jmpbufs[(int) i] = CG.declare_local(M3ID.NoID, M3.target.Address.size,
                                                  M3.target.Address.align, CGType.Addr,
                                                  0, /* in_memory := */ false,
                                                  /* up_level := */ false, /* f := */ CG.Likely);
        }

        // If referencing the size more than once, store the global in a
        // local.

        if (try_count > 1) {
            CG.load_int(M3.target.Word.cg_type, size);
            size = CG.declare_temp(M3.target.Address.size, M3.target.Address.align,
                                   M3.target.Word.cg_type, /* in_memory := */ false);
            CG.store_int(M3.target.Word.cg_type, size);
        }

        // jmpbufs[0] = alloca (size * try_count) unless try_count == 1 then
        // jmpbufs[0] = alloca (size)

        CG.start_call_direct(alloca, 0, M3.target.Address.cg_type);
        CG.load_int(M3.target.Word.cg_type, size);
        if (try_count != 1) {
            CG.load_intt(try_count);
            CG.multiply(M3.target.Word.cg_type);
        } else {
            // x * 1 == x
        }
        CG.pop_param(M3.target.Word.cg_type);
        CG.call_direct(alloca, M3.target.Address.cg_type);

        CG.store_addr(p.jmpbufs[0]);

        // loop: jmpbufs[n] = jmpbufs[n - 1] + size

        if (try_count > 1) {
            for (long i = 1; i <= try_count - 1; i++) {
                CG.load_addr(p.jmpbufs[(int) (i - 1)], 0, M3.target.Address.align);
                CG.loophole(M3.target.Address.cg_type, M3.target.Word.cg_type);
                CG.load_int(M3.target.Word.cg_type, size);
                CG.add(M3.target.Word.cg_type);
                CG.loophole(M3.target.Word.cg_type, M3.target.Address.cg_type);
                CG.store_addr(p.jmpbufs[(int) i]);
            }
        }
    }
}
