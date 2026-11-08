// Java port of CM3's m3-sys/m3front/src/builtinTypes/ErrType.i3 and ErrType.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.Target.CGType;

// File: ErrType.m3
// Last Modified On Tue May 23 15:32:21 PDT 1995 by kalsow
//      Modified On Thu Dec  5 17:24:02 PST 1991 by muller

public final class ErrType {
    private ErrType() {}

    public static Type.T T;

    public static final class P extends Type.T {
        @Override
        public void check() {
            ErrType.check(this);
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
        p.info.size = 0;
        p.info.min_size = 0;
        p.info.alignment = 1;
        p.info.mem_type = CGType.Addr;
        p.info.stk_type = CGType.Addr;
        p.info.class_ = Type.Class.Error;
        p.info.isTraced = false;
        p.info.isEmpty = false;
        p.info.isSolid = true;
        p.info.hash = 4;
    }

    private static void compiler(P p) {
    }

    private static long initCoster(Type.T t, boolean zeroed) {
        return 0;
    }

    private static void fPrinter(Type.T t, M3.FPInfo x) {
        x.tag = "$ErrType";
        x.n_nodes = 0;
    }

    private static void genMap(P p, long offset, long size, boolean refs_only) {
        // generate nothing
    }

    private static void genDesc(P p) {
        // generate nothing
    }

    public static void initialize() {
        T = new P();
        TypeRep.init(T, Type.Class.Error);
        Tipe.define("_ERROR", T, false);
    }
}
