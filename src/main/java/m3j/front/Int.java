// Java port of CM3's m3-sys/m3front/src/builtinTypes/Int.i3 and Int.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.util.AssertError;

// File: Int.m3
// Last Modified On Tue May 23 15:32:32 PDT 1995 by kalsow
//      Modified On Fri Dec 21 01:48:33 1990 by muller

public final class Int {
    private Int() {}

    public static Type.T T;

    public static final class P extends Type.T {
        @Override
        public void check() {
            Int.check(this);
        }

        @Override
        public boolean no_straddle(long offset, boolean IsEltOrField) {
            return TypeRep.scalarNoStraddle(this, offset, IsEltOrField);
        }

        @Override
        public boolean isEqual(Type.T t, Type.Assumption x) {
            return TypeRep.neverEqual(this, t, x);
        }

        @Override
        public boolean isSubtype(Type.T t) {
            return TypeRep.noSubtypes(this, t);
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
            TypeRep.initToZeros(this, zeroed);
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

    private static void check(P p) {
        p.info.size = M3.target.Integer.size;
        p.info.min_size = M3.target.Integer.size;
        p.info.alignment = M3.target.Integer.align;
        p.info.mem_type = M3.target.Integer.cg_type;
        p.info.stk_type = M3.target.Integer.cg_type;
        p.info.class_ = Type.Class.Integer;
        p.info.isTraced = false;
        p.info.isEmpty = false;
        p.info.isSolid = true;
        p.info.hash = 5;
    }

    private static void compiler(P p) {
    }

    private static long initCoster(Type.T t, boolean zeroed) {
        return 0;
    }

    private static void fPrinter(Type.T t, M3.FPInfo x) {
        x.tag = "$integer";
        x.n_nodes = 0;
    }

    private static void genMap(P p, long offset, long size, boolean refs_only) {
        long IntBytes = M3.target.Integer.bytes;
        AssertError.check(size == M3.target.Integer.size, "Int");
        if ((refs_only)) {
            // skip
        } else if ((IntBytes == 8)) {
            TipeMap.add(offset, TipeMap.Op.Int_8, 0);
        } else if ((IntBytes == 4)) {
            TipeMap.add(offset, TipeMap.Op.Int_4, 0);
        } else if ((IntBytes == 2)) {
            TipeMap.add(offset, TipeMap.Op.Int_2, 0);
        } else if ((IntBytes == 1)) {
            TipeMap.add(offset, TipeMap.Op.Int_1, 0);
        } else {
            AssertError.check(false, "Int");
        }
    }

    private static void genDesc(P p) {
        TipeDesc.addO(TipeDesc.Op.Integer, p);
    }

    public static void initialize() {
        T = new P();
        TypeRep.init(T, Type.Class.Integer);
        Tipe.define("INTEGER", T, true);
    }
}
