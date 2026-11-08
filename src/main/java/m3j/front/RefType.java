// Java port of CM3's m3-sys/m3front/src/types/RefType.i3 and RefType.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.fingerprint.Fingerprint;
import m3j.middle.M3Buf;
import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.M3RT;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: RefType.m3
// Last modified on Tue May 23 15:26:35 PDT 1995 by kalsow
//      modified on Thu Dec  5 17:20:18 PST 1991 by muller

public final class RefType {
    private RefType() {}

    private static boolean debug = false;

    public static final class P extends Type.T {
        public Brand.T brand;
        public Type.T target;
        public boolean isTraced;
        public String user_name;

        @Override
        public void check() {
            RefType.check(this);
        }

        @Override
        public boolean no_straddle(long offset, boolean IsEltOrField) {
            return TypeRep.addrNoStraddle(this, offset, IsEltOrField);
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
            TypeRep.genRefMap(this, offset, size, refs);
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

    private static int root = M3ID.NoID;

    // EXPORTED
    public static Type.T parse() {
        Brand.T brand = null;
        boolean traced = true;
        Type.T super_ = null;
        if ((Scanner.cur.token == Token.T.tUNTRACED)) {
            Scanner.getToken(); // UNTRACED
            if ((Scanner.cur.token == Token.T.tIDENT)) {
                if (root == M3ID.NoID) {
                    root = M3ID.add(M3.ids, "ROOT");
                }
                if ((Scanner.cur.id != root)) {
                    Error.ID(Scanner.cur.id, "expected UNTRACED ROOT");
                }
                Scanner.getToken(); // IDENT
                super_ = ObjectAdr.T;
                if ((Scanner.cur.token != Token.T.tOBJECT)
                    && (Scanner.cur.token != Token.T.tBRANDED)) {
                    return super_;
                }
            }
            traced = false;
        }
        brand = Brand.parse();
        if ((Scanner.cur.token == Token.T.tREF)) {
            if ((super_ != null)) {
                Error.msg("expected OBJECT declaration");
            }
            Scanner.getToken(); // REF
            return new_(Type.parse(), traced, brand);
        } else { // must be an object type
            if ((super_ == null) && (!traced)) {
                Error.msg("expected UNTRACED ROOT OBJECT");
            }
            return ObjectType.parse(super_, traced, brand);
        }
    }

    // EXPORTED
    public static Type.T new_(Type.T target, boolean traced, Brand.T brand) {
        P p;
        p = new P();
        TypeRep.init(p, Type.Class.Ref);
        p.isTraced = traced;
        p.brand = brand;
        p.target = target;
        p.user_name = null;

        if (debug) {
            System.err.print("RefType.New:");
            System.err.print(refText(p));
            System.err.print(" target:");
            System.err.print(refText(target));
            System.err.print("\n");
            System.err.flush();
        }

        return p;
    }

    /**
     * Port: the text that RTIO.PutRef writes; Java has no addresses, so the
     * identity hash code stands for the address.
     */
    private static String refText(Object r) {
        return "0x" + (r == null ? "0" : Integer.toHexString(System.identityHashCode(r)));
    }

    private static P reduce(Type.T t) {
        while (true) {
            t = Type.strip(t); // StripPacked?
            if ((t == null)) {
                return null;
            }
            if ((t.info.class_ == Type.Class.Ref)) {
                return (P) t;
            }
            if ((t.info.class_ != Type.Class.Opaque)) {
                return null;
            }
            t = Revelation.lookUp(t);
        }
    }

    // EXPORTED
    public static boolean is(Type.T t) {
        return (reduce(t) != null);
    }
    // EXPORTED

    public static boolean isBranded(Type.T t) {
        P p = reduce(t);
        return (p != null) && (p.brand != null);
    }

    /** Port: VAR target is {@code target[0]}. */
    // EXPORTED
    public static boolean split(Type.T t, Type.T[] target) {
        P p = reduce(t);
        if ((p == null)) {
            return false;
        }
        target[0] = p.target;
        return true;
    }

    // Externally dispatched-to:
    private static void check(P p) {
        Type.T targetType;
        long targetAlign;
        long[] hash = {839};
        Type.Info targetInfo = new Type.Info();
        M3.CheckState cs = M3.OuterCheckState.copy();
        boolean targetIsTraced;
        Brand.check(p.brand, p, hash, cs);

        targetType = Type.strip(p.target); // Remove named.
        if (targetType != null) {
            if (targetType.info.class_ == Type.Class.Packed) {
                targetType = Type.stripPacked(targetType);
            }
            hash[0] = hash[0] * 43 + targetType.info.class_.ordinal();
        }

        p.info.size = M3.target.Address.size;
        p.info.min_size = M3.target.Address.size;
        p.info.alignment = M3.target.Address.align;
        p.info.mem_type = CGType.Addr;
        p.info.stk_type = CGType.Addr;
        p.info.class_ = Type.Class.Ref;
        p.info.isTraced = p.isTraced;
        p.info.isEmpty = false;
        p.info.isSolid = true;
        p.info.hash = hash[0];

        targetAlign = Target.Word8.align;
        targetIsTraced = false;
        Type.recursionDepth++; //------------------------------------
        p.checked = true;
        if ((p.target != null)) {
            p.target = Type.checkInfo(p.target, targetInfo);
            targetAlign = targetInfo.alignment;
            targetIsTraced = targetInfo.isTraced;
        }
        Type.recursionDepth--; //------------------------------------

        p.info.addr_align = Math.max(targetAlign, Target.Word8.align);
        // ^Target's type-alignment could be < 8, if the target has
        //  packed elements or fields, but a pointer can point only
        //  to a whole byte.

        if ((!p.isTraced) && (targetIsTraced) && Module.isSafe()) {
            Error.msg("Unsafe: untraced ref type to a traced type (2.2.7).");
        }
        // EVAL Type.StraddleFreeScalars (p.target, 0, IsEltOrField := FALSE);
        // CHECK: ^Why is this here? rodney.m.bates@acm.org.
        //   1) It appears StraddleFreeScalars and all its many and recursive
        //      overrides are side-effect-free functions, so why EVAL?
        //   2) With an offset of 0, how could it fail?
    }

    // Externally dispatched-to:
    private static void compiler(P p) {
        int typeid;
        String user_name;
        int target_typename = M3ID.NoID;
        Type.compile(p.target);
        // Port: CG.TypeUID is int.
        typeid = (int) Type.globalUID(p);
        user_name = p.user_name;
        if (user_name != null) {
            target_typename = M3ID.add(M3.ids, user_name);
        }
        CG.declare_pointer(typeid, (int) Type.globalUID(p.target),
                           Brand.toText(p.brand), p.isTraced, target_typename);
        if (user_name != null) {
            CG.declare_typename(typeid, target_typename);
        }
    }

    /** record a user name for the ref type 't' */
    // EXPORTED
    public static void noteRefName(Type.T t, String name) {
        P p = reduce(t);
        if ((p != null)) {
            p.user_name = name;
        }
    }

    private static final M3RT.TypeKind[] Kind = {M3RT.TypeKind.Ref, M3RT.TypeKind.Array};

    // EXPORTED
    public static void initTypecell(Type.T t, long offset, long prev) {
        // Port: Kind = ARRAY BOOLEAN OF TKind is indexed by ORD (BOOLEAN).
        P p = (P) t;
        long brand = Brand.compile(p.brand);
        long type_map = genTypeMap(p, /* refs_only := */ false);
        long gc_map = genTypeMap(p, /* refs_only := */ true);
        long type_desc = genTypeDesc(p);
        M3CG.Proc initProc = genInitProc(p);
        long dims;
        long targetSize;
        long elemSize;
        Type.T ta;
        long isz = M3.target.Integer.size;
        long name_offs = 0;
        Fingerprint.T fp = TypeFP.fromType(p);
        M3CG.Var globals = Module.globalData(/* is_const := */ false);
        M3CG.Var consts = Module.globalData(/* is_const := */ true);
        Type.Info targetInfo = new Type.Info();
        Type.checkInfo(p.target, targetInfo);
        ta = Type.base(p.target);

        dims = OpenArrayType.openDepth(ta);
        if ((dims == 0)) { // Not an open array.
            targetSize = targetInfo.size;
            elemSize = 8; // Dead
        } else { // target is an open array.  targetSize := dope size.
            targetSize = M3.target.Address.size; // Size of elements pointer.
            {
                long ia = M3.target.Integer.align;
                targetSize =
                    Math.floorDiv(targetSize + ia - 1, ia) * ia; // Padding, ahead of shape
                targetSize += M3.target.Integer.size * dims; // The shape words.
            }
            {
                long aa = p.info.addr_align;
                targetSize =
                    Math.floorDiv(targetSize + aa - 1, aa) * aa; // Padding, ahead of elements
            }
            elemSize = OpenArrayType.eltPack(ta);
            if (elemSize < Target.Byte) { // Sub-byte elements, 1, 2, or 4 bits.
                AssertError.check(Math.floorMod(Target.Byte, elemSize) == 0, "RefType");
                elemSize = Target.Byte;
                // FIXME: This is a quick hack for open arrays of element bitsize 1, 2, or 4.
                //        The RTS only knows byte counts for element sizes.  Until RTS can be
                //        fixed to understand bit sizes, this will at least make things work,
                //        at the cost of over-allocating a full byte for every element.
            } else {
                AssertError.check(Math.floorMod(elemSize, Target.Byte) == 0, "RefType");
            }
        }

        if ((p.user_name != null)) {
            name_offs = CG.emitText(p.user_name, /* is_const := */ true);
        }

        // generate my Type cell info
        CG.init_intt(offset + M3.rt.TC_selfID, isz, Type.globalUID(p), false);
        for (int i = 0; i <= 7; i++) {
            CG.init_intt(offset + M3.rt.TC_fp + i * 8, 8, fp.byteAt(i), false);
        }
        CG.init_intt(offset + M3.rt.TC_traced, 8, p.isTraced ? 1 : 0, false);
        CG.init_intt(offset + M3.rt.TC_kind, 8, Kind[dims > 0 ? 1 : 0].ordinal(), false);
        CG.init_intt(offset + M3.rt.TC_dataAlignment, 8,
                     Math.floorDiv(p.info.addr_align, Target.Byte), false);
        // ^VSee comments in RT0.i3, regarding dataAlignment and dataSize.

        CG.init_intt(offset + M3.rt.TC_dataSize, isz, Math.floorDiv(targetSize, Target.Byte), false);
        if ((type_map >= 0)) {
            CG.init_var(offset + M3.rt.TC_type_map, consts, type_map, false);
        }
        if ((gc_map >= 0)) {
            CG.init_var(offset + M3.rt.TC_gc_map, consts, gc_map, false);
        }
        if ((type_desc >= 0)) {
            CG.init_var(offset + M3.rt.TC_type_desc, consts, type_desc, false);
        }
        if ((initProc != null)) {
            CG.init_proc(offset + M3.rt.TC_initProc, initProc, false);
        }
        if ((brand >= 0)) {
            CG.init_var(offset + M3.rt.TC_brand, consts, brand, false);
        }
        if ((p.user_name != null)) {
            CG.init_var(offset + M3.rt.TC_name, consts, name_offs, false);
        }
        if ((prev != 0)) {
            CG.init_var(offset + M3.rt.TC_next, globals, prev, false);
        }

        if ((dims > 0)) {
            // REF ARRAY specific extensions to the typecell
            CG.init_intt(offset + M3.rt.ATC_nDimensions, isz, dims, false);
            CG.init_intt(offset + M3.rt.ATC_elementSize, isz,
                         Math.floorDiv(elemSize, Target.Byte), false);
        }

    }

    private static long genTypeMap(P p, boolean refs_only) {
        // generate my "TypeMap" (called by the garbage collector)
        TipeMap.start();
        Type.genMap(p.target, 0, -1, refs_only);
        return TipeMap.finish("type map for ", Type.name(p));
    }

    private static long genTypeDesc(P p) {
        // generate my "TypeDesc" (called by the pickle machinery)
        if (!p.isTraced) {
            return -1;
        }
        TipeDesc.start();
        Type.genDesc(p.target);
        return TipeDesc.finish("type description for ", Type.name(p));
    }

    private static M3CG.Proc genInitProc(P p) {
        String name;
        M3CG.Proc proc;
        M3CG.Var ref;
        if (Type.initCost(p.target, true) <= 0) {
            return null;
        }

        // generate the procedure body

        CG.gen_location(p.origin);
        name = Type.linkName(p, "_INIT");
        CG.comment(-1, false, name + " (RefType)");
        Scanner.offset = p.origin;
        CG.gen_location(p.origin);
        proc = CG.declare_procedure(M3ID.add(M3.ids, name), 1, CGType.Void,
                                    /* lev := */ 0, /* cc := */ M3.target.DefaultCall,
                                    /* exported := */ false, /* parent := */ null);
        ref = CG.declare_param(M3ID.NoID, M3.target.Address.size,
                               M3.target.Address.align, CGType.Addr,
                               (int) Type.globalUID(p),
                               /* in_memory := */ false, /* up_level := */ false,
                               /* f := */ CG.Always);
        CG.begin_procedure(proc);


        // initialize the referent
        CG.load_addr(ref, 0, p.info.addr_align);
        Type.initValue(p.target, true);

        CG.exit_proc(CGType.Void);
        CG.end_procedure(proc);
        return proc;
    }

    // Externally dispatched-to:
    private static boolean equalChk(P a, Type.T t, Type.Assumption x) {
        P b = (P) t;
        return (a.isTraced == b.isTraced)
            && Brand.equal(a.brand, b.brand)
            && ((a.target == null && b.target == null)
                || Type.isEqual(a.target, b.target, x));
    }

    // Externally dispatched-to:
    private static boolean subtyper(P a, Type.T b) {
        if (Type.isEqual(a, b, null)) {
            return true;
        }

        if (Type.isEqual(a, Null.T, null)) {
            return Type.isSubtype(b, Reff.T)
                || Type.isSubtype(b, Addr.T)
                || ProcType.is(b);
        }

        return ((a.isTraced) && Type.isEqual(b, Reff.T, null))
            || ((!a.isTraced) && Type.isEqual(b, Addr.T, null));
    }

    // Externally dispatched-to:
    private static long initCoster(P p, boolean zeroed) {
        // Port: p is <*UNUSED*>.
        if (!zeroed) {
            return 1;
        } else {
            return 0;
        }
    }

    // Externally dispatched-to:
    private static void genDesc(P p) {
        if (Type.isEqual(p, Reff.T, null)) {
            TipeDesc.addO(TipeDesc.Op.Refany, p);
        } else if (Type.isEqual(p, Addr.T, null)) {
            TipeDesc.addO(TipeDesc.Op.Address, p);
        } else if (Type.isEqual(p, Null.T, null)) {
            TipeDesc.addO(TipeDesc.Op.Null, p);
        } else {
            TypeRep.genRefDesc(p);
        }
    }

    // Externally dispatched-to:
    private static void fPrinter(P p, M3.FPInfo x) {
        if (Type.isEqual(p, Reff.T, null)) {
            x.tag = "$refany";
            x.n_nodes = 0;
        } else if (Type.isEqual(p, Addr.T, null)) {
            x.tag = "$address";
            x.n_nodes = 0;
        } else if (Type.isEqual(p, Null.T, null)) {
            x.tag = "$null";
            x.n_nodes = 0;
        } else {
            M3Buf.putText(x.buf, "REF");
            if ((!p.isTraced)) {
                M3Buf.putText(x.buf, "-UNTRACED");
            }
            Brand.genFPrint(p.brand, x);
            x.n_nodes = 1;
            x.nodes[0] = p.target;
        }
    }

    static {
        // Port: RTParams.IsPresent ("m3front-debug-reftype") is FALSE.
        debug = false;
    }
}
