// Java port of CM3's m3-sys/m3front/src/types/EnumType.i3 and EnumType.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.M3ID;
import m3j.middle.TInt;
import m3j.middle.TWord;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: EnumType.m3
// Last modified on Tue May 23 15:21:50 PDT 1995 by kalsow
//      modified on Thu Jan 31 02:30:29 1991 by muller

public final class EnumType {
    private EnumType() {}

    // Port: Rep = [FIRST (TargetMap.Word_types) .. LAST (TargetMap.Word_types)]
    // is int, an index into M3.targetMap.Word_types.

    public static final class P extends Type.T {
        public long n_elts;
        public Scope.T scope;
        public int rep;

        @Override
        public void check() {
            EnumType.check(this);
        }

        @Override
        public boolean no_straddle(long offset, boolean IsEltOrField) {
            return noStraddle(this, offset, IsEltOrField);
        }

        @Override
        public boolean isEqual(Type.T t, Type.Assumption x) {
            return equalChk(this, t, x);
        }

        @Override
        public boolean isSubtype(Type.T t) {
            return subtyper(this, t);
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

    public static Type.T parse() {
        long n, j;
        P p;
        int[] val = TInt.newInt();
        boolean b;
        p = create(Scope.pushNew(false, M3ID.NoID));
        n = 0;
        Scanner.match(Token.T.tLBRACE);
        if ((Scanner.cur.token == Token.T.tIDENT)) {
            n = Ident.parseList();
            j = Ident.top - n;
            for (long i = 0; i <= n - 1; i++) {
                b = TInt.fromInt(i, val);
                AssertError.check(b, "EnumType");
                Scope.insert(EnumElt.new_(Ident.stack[(int) (j + i)], val, p));
            }
            Ident.top -= n;
        }
        Scanner.match(Token.T.tRBRACE);
        Scope.popNew();
        p.n_elts = n;
        setRep(p);
        return p;
    }

    public static Type.T new_(long n_elts, Scope.T elts) {
        P p;
        p = create(elts);
        p.n_elts = n_elts;
        setRep(p);
        return p;
    }

    public static Type.T build(String[] elt_nms) {
        P p;
        int[] val = TInt.newInt();
        boolean b;
        p = create(Scope.pushNew(false, M3ID.NoID));
        for (int i = 0; i <= elt_nms.length - 1; i++) {
            b = TInt.fromInt(i, val);
            AssertError.check(b, "EnumType");
            Scope.insert(EnumElt.new_(M3ID.add(M3.ids, elt_nms[i]), val, p));
        }
        Scope.popNew();
        p.n_elts = elt_nms.length;
        setRep(p);
        return p;
    }

    private static P reduce(Type.T t) {
        t = Type.strip(t); // StripPacked?
        if ((t == null) || (t.info.class_ != Type.Class.Enum)) {
            return null;
        }
        return (P) t;
    }

    public static boolean is(Type.T t) {
        return (reduce(t) != null);
    }

    /** Port: VAR value is {@code value[0]}. */
    public static boolean lookUp(Type.T t, int name, Value.T[] value) {
        P p = reduce(t);
        if ((p == null)) {
            return false;
        }
        value[0] = Scope.lookUp(p.scope, name, true);
        return (value[0] != null);
    }

    public static long numElts(Type.T t) {
        P p = (P) t;
        return p.n_elts;
    }

    //**********************************************************************

    private static P create(Scope.T elts) {
        P p;
        p = new P();
        TypeRep.init(p, Type.Class.Enum);
        p.scope = elts;
        p.n_elts = 0;
        return p;
    }

    private static void setRep(P p) {
        int[] max = TInt.newInt();
        if (!TInt.fromInt(p.n_elts - 1, max)
            || TInt.LT(max, M3.target.Integer.min)
            || TInt.LT(M3.target.Integer.max, max)) {
            Error.msg("enumeration type too large");
        }
        for (int i = 0; i <= M3.targetMap.Word_types.length - 1; i++) {
            Target.Int_type t = M3.targetMap.Word_types[i];
            if ((t.size <= M3.target.Word.size)
                && TWord.LE(max, t.max)) {
                p.rep = i;
                p.info.size = M3.targetMap.Word_types[p.rep].size;
                return;
            }
        }
        p.rep = M3.targetMap.Word_types.length - 1;
        p.info.size = M3.targetMap.Word_types[p.rep].size;
    }

    private static void check(P p) {
        Value.T v;
        long hash;
        M3.CheckState cs = M3.OuterCheckState.copy();
        Scope.typeCheck(p.scope, cs);
        v = Scope.toList(p.scope);
        hash = 37;
        while ((v != null)) {
            hash = hash * 67 + M3ID.hash(M3.ids, Value.cName(v));
            v = v.next;
        }

        AssertError.check(p.info.size == M3.targetMap.Word_types[p.rep].size, "EnumType");
        p.info.min_size = minSize(p);
        p.info.alignment = M3.targetMap.Word_types[p.rep].align;
        p.info.mem_type = M3.targetMap.Word_types[p.rep].cg_type;
        p.info.stk_type = M3.target.Word.cg_type;
        p.info.class_ = Type.Class.Enum;
        p.info.isTraced = false;
        p.info.isEmpty = (p.n_elts <= 0);
        p.info.isSolid = true;
        p.info.hash = hash;
    }

    private static boolean noStraddle(P p, long offset, boolean IsEltOrField) {
        // Port: IsEltOrField is <*UNUSED*>.
        long sz = M3.targetMap.Word_types[p.rep].size;
        long z0;
        if (p.info.lazyAligned) {
            z0 = Math.floorDiv(offset, Target.Byte) * Target.Byte;
        } else {
            z0 = Math.floorDiv(offset, M3.target.Integer.align) * M3.target.Integer.align;
        }
        return (offset + sz) <= (z0 + M3.target.Integer.size);
    }

    private static void compiler(P p) {
        Value.T v = Scope.toList(p.scope);
        // Port: CG.TypeUID is int.
        CG.declare_enum((int) Type.globalUID(p), p.n_elts,
                        M3.targetMap.Word_types[p.rep].size);
        while ((v != null)) {
            CG.declare_enum_elt(Value.cName(v));
            v = v.next;
        }
    }

    private static boolean equalChk(P a, Type.T t, Type.Assumption x) {
        // Port: x is <*UNUSED*>.
        P b = (P) t;
        Value.T oa, ob;
        if ((a.n_elts != b.n_elts)) {
            return false;
        }
        if ((a.n_elts == 0)) {
            return true;
        }
        // .scope = NIL IFF it's predefined, i.e., CHAR, WIDECHAR, or BOOLEAN.
        if ((a.scope == null) || (b.scope == null)) {
            return (a.scope == b.scope);
        }

        // get a handle on the elements
        oa = Scope.toList(a.scope);
        ob = Scope.toList(b.scope);

        // compare the elements
        while ((oa != null) && (ob != null) && EnumElt.isEqual(oa, ob)) {
            oa = oa.next;
            ob = ob.next;
        }

        return (oa == null) && (ob == null);
    }

    private static boolean subtyper(P a, Type.T t) {
        Type.T tBase;
        int[] tMin = TInt.newInt(), tMax = TInt.newInt(), aMax = TInt.newInt(),
              aMin = TInt.newInt();
        if (Type.isEqual(a, t, null)) {
            return true;
        }
        if (Type.isEqual(a, Charr.T, null)) {
            if (Type.isEqual(t, WCharr.T, null)) {
                return true;
            }
            tBase = Type.base(t);
            if (!Type.isEqual(tBase, WCharr.T, null)) {
                return false;
            }
            if (!SubrangeType.split(t, tMin, tMax)) {
                return false;
            }
            if (!SubrangeType.split(a, aMin, aMax)) {
                return false;
            }
            return TInt.LE(tMax, aMax);
        }
        return false;
    }

    private static long minSize(P p) {
        long i, j, n;
        j = 1;
        i = 2;
        n = p.n_elts;
        while ((n > i)) {
            j++;
            i += i;
        }
        return j;
    }

    private static long initCoster(P p, boolean zeroed) {
        int[] max = TInt.newInt();
        if ((p.n_elts <= 0) || (zeroed)) {
            return 0;
        }
        if (!TInt.fromInt(p.n_elts - 1, max)) {
            return 1;
        }
        if (TInt.EQ(M3.targetMap.Word_types[p.rep].max, max)) {
            return 0;
        } else {
            return 1;
        }
    }

    private static void genMap(P p, long offset, long size, boolean refs_only) {
        // Port: p is <*UNUSED*>.
        long bit_offset = Math.floorMod(offset, Target.Byte);
        TipeMap.Op op;
        if ((refs_only)) {
            return;
        }
        if ((bit_offset != 0)) {
            op = TipeMap.Op.Word_Field;
        } else if ((size == 1 * Target.Byte)) {
            op = TipeMap.Op.Word_1;
        } else if ((size == 2 * Target.Byte)) {
            op = TipeMap.Op.Word_2;
        } else if ((size == 4 * Target.Byte)) {
            op = TipeMap.Op.Word_4;
        } else if ((size == 8 * Target.Byte)) {
            op = TipeMap.Op.Word_8;
        } else { // weird size
            op = TipeMap.Op.Word_Field;
        }
        TipeMap.add(offset, op, bit_offset + 256 * size);
    }

    private static void genDesc(P p) {
        if (Type.isEqual(p, Charr.T, null)) {
            TipeDesc.addO(TipeDesc.Op.Char, p);
            // Widechar Tipe.
            // ELSIF Type.IsEqual (p, WCharr.T, NIL) THEN
            //   EVAL TipeDesc.AddO (TipeDesc.Op.Widechar, p);
        } else if (Type.isEqual(p, Bool.T, null)) {
            TipeDesc.addO(TipeDesc.Op.Boolean, p);
        } else if (TipeDesc.addO(TipeDesc.Op.Enum, p)) {
            TipeDesc.addI(p.n_elts);
        }
    }

    private static void fPrinter(P p, M3.FPInfo x) {
        Value.T v;
        x.n_nodes = 0;
        if (Type.isEqual(p, Charr.T, null)) {
            x.tag = "$char";
        } else if (Type.isEqual(p, Bool.T, null)) {
            x.tag = "$boolean";
        } else if (Type.isEqual(p, WCharr.T, null)) {
            x.tag = "$widechar";
        } else {
            M3Buf.putText(x.buf, "ENUM");
            v = Scope.toList(p.scope);
            while ((v != null)) {
                M3Buf.putChar(x.buf, ' ');
                M3ID.put(M3.ids, x.buf, Value.cName(v));
                v = v.next;
            }
        }
    }
}
