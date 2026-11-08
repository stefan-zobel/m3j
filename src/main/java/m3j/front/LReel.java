// Java port of CM3's m3-sys/m3front/src/builtinTypes/LReel.i3 and LReel.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: LReel.m3
// Last Modified On Tue May 23 15:32:43 PDT 1995 by kalsow
//      Modified On Wed Sep 26 18:57:30 1990 by muller

public final class LReel {
    private LReel() {}

    public static Type.T T;

    public static final class P extends Type.T {
        @Override
        public void check() {
            LReel.check(this);
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
        p.info.size = M3.target.Longreal.size;
        p.info.min_size = M3.target.Longreal.size;
        p.info.alignment = M3.target.Longreal.align;
        p.info.mem_type = CGType.LReel;
        p.info.stk_type = CGType.LReel;
        p.info.class_ = Type.Class.Longreal;
        p.info.isTraced = false;
        p.info.isEmpty = false;
        p.info.isSolid = true;
        p.info.hash = 2;
    }

    private static void compiler(P p) {
    }

    private static long initCoster(Type.T t, boolean zeroed) {
        // not every bit pattern is a legal LONGREAL
        if (zeroed || Target.All_floats_legal) {
            return 0;
        } else {
            return 1;
        }
    }

    private static void fPrinter(Type.T t, M3.FPInfo x) {
        x.tag = "$longreal";
        x.n_nodes = 0;
    }

    private static void genMap(P p, long offset, long size, boolean refs_only) {
        AssertError.check(size == M3.target.Longreal.size, "LReel");
        if (!refs_only) {
            TipeMap.add(offset, TipeMap.Op.Longreal, 0);
        }
    }

    private static void genDesc(P p) {
        TipeDesc.addO(TipeDesc.Op.Longreal, p);
    }

    public static void initialize() {
        T = new P();
        TypeRep.init(T, Type.Class.Longreal);
        Tipe.define("LONGREAL", T, true);
    }
}
