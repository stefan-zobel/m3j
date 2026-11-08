// Java port of CM3's m3-sys/m3front/src/types/Type.i3 and Type.m3.
// Original: Copyright (C) 1992, 1994, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.fingerprint.Fingerprint;
import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.TFloat;
import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: Type.i3
// Last Modified On Tue May 23 15:17:25 PDT 1995 by kalsow
//      Modified On Fri Dec 21 00:57:36 1990 by muller

// File: Type.m3
// Last Modified On Tue May 23 15:37:08 PDT 1995 by kalsow
//      Modified On Fri May 28 16:29:19 PDT 1993 by muller

/**
 * The type nodes of the front end.
 *
 * <p>The following sets of procedures may be called during the
 * various phases of the compilation:
 * <pre>
 *  initialization:
 *    { Initialize* }
 *  parsing:
 *    { Parse, NoteDeclaration }
 *  type checking:
 *    { Check, Number, GetBounds, Base, IsEqual,
 *      IsSubtype, IsAssignable }
 *  code generation:
 *    { Name, Number, GetBounds, Base, IsEqual,
 *      IsSubtype, IsAssignable }
 *
 *  ( * =&gt; may only be called once )
 * </pre>
 *
 * <p>Port: the module Type also exports TypeRep; the procedures of
 * TypeRep are package-private static methods here, in the original order,
 * and {@link TypeRep} forwards to them.
 */
public final class Type {
    private Type() {}

    /**
     * Port: M3.Type, with the fields and methods of its revelation in
     * TypeRep (TypeRep.TT).
     */
    public static class T extends M3.Node {
        /** Port: NEW zero-fills the fields; the fingerprint starts as zero. */
        public Fingerprint.T fp = Fingerprint.Zero;
        public Info info = new Info();
        public long uid;
        public long scc_id;
        public long rep_id;
        public long checkDepth;
        public boolean checked;
        public boolean errored;
        /** linked list of all types in the same module */
        public T next;

        public void check() {
            throw new AssertError("TypeRep.check");
        }

        public boolean no_straddle(long offset, boolean IsEltOrField) {
            throw new AssertError("TypeRep.no_straddle");
        }

        public boolean isEqual(T t, Assumption x) {
            throw new AssertError("TypeRep.isEqual");
        }

        public boolean isSubtype(T t) {
            return TypeRep.noSubtypes(this, t);
        }

        public void compile() {
            throw new AssertError("TypeRep.compile");
        }

        public long initCost(boolean zeroed) {
            throw new AssertError("TypeRep.initCost");
        }

        public void initValue(boolean zeroed) {
            throw new AssertError("TypeRep.initValue");
        }

        public void mapper(long offset, long size, boolean refs) {
            throw new AssertError("TypeRep.mapper");
        }

        public void gen_desc() {
            throw new AssertError("TypeRep.gen_desc");
        }

        public void fprint(M3.FPInfo x) {
            throw new AssertError("TypeRep.fprint");
        }
    }

    /**
     * Port: M3.EqAssumption, revealed as an untraced reference to an
     * AssumptionRec. The original links records that are local variables
     * of IsEqual on the call chain; here each is a new object, linked the
     * same way.
     */
    public static class Assumption {
        public Assumption prev;
        public T a, b;
    }

    public static class ModuleInfo {
        public T module_types = null;
        public CellInfo full_cells = null;
        public CellPtr cell_ptrs = null;
        public T next_to_set = null;
        public Module.T module = null;
        public boolean in_order = false;
    }

    // Port: BitAlignT = [0 .. 255] is long.

    public enum Class { Error, Named, Integer, Longint, Real, Longreal, Extended, Array, Enum, Object, Opaque, OpenArray, Packed, Procedure, Record, Ref, Set, Subrange }

    /**
     * incremented(decremented) every time the type checker enters(leaves)
     * one of the types that's allowed to introduce recursions.
     * (ie. REF, OBJECT, PROC)
     */
    public static long recursionDepth = 0;

    public static final class Info {
        /** size in bits, -1 if variable sized */
        public long size;
        /** minimum size in bits. */
        public long min_size;
        /** minimum alignment in bits */
        public long alignment;
        /** When stk_type = CG.Type.Addr, the alignment of dereferenced location. */
        public long addr_align = Target.Word8.align;
        /** internal hash code */
        public long hash;
        public int name = M3ID.NoID;
        /** code generator representation on operator stack */
        public CGType stk_type;
        /** code generator representation as a variable */
        public CGType mem_type;
        public Class class_;
        public boolean isTraced;
        public boolean isEmpty;
        public boolean isSolid;
        public boolean lazyAligned;

        public Info() {}

        public Info copy() {
            Info r = new Info();
            r.assign(this);
            return r;
        }

        /** Port: the assignment {@code this := x}. */
        public void assign(Info x) {
            size = x.size;
            min_size = x.min_size;
            alignment = x.alignment;
            addr_align = x.addr_align;
            hash = x.hash;
            name = x.name;
            stk_type = x.stk_type;
            mem_type = x.mem_type;
            class_ = x.class_;
            isTraced = x.isTraced;
            isEmpty = x.isEmpty;
            isSolid = x.isSolid;
            lazyAligned = x.lazyAligned;
        }
    }

    // Port: RTParams.IsPresent ("m3front-debug-type") in the module body
    // is FALSE.
    private static boolean debug = false;

    private static final long NOT_CHECKED = -1;

    public static final class CellInfo {
        public CellInfo next = null;
        public T type = null;
        public Module.T unit = null;
        public long offset = 0;
    }

    public static final class CellPtr {
        public CellPtr next = null;
        public T type = null;
        public long offset = 0;
    }

    // Port: the tables are one-element arrays because TypeTbl.Put takes
    // them as VAR parameters.
    private static boolean compile_started = false;
    private static ModuleInfo cur = null;
    /** type -> BOOLEAN */
    private static final TypeTbl.T[] compiled = { null };
    /** type -> CellInfo */
    private static final TypeTbl.T[] visible_cells = { null };
    /** type -> BOOLEAN */
    private static final TypeTbl.T[] visible_types = { null };

    //------------------------------------------------------------------------

    //*** phase 0 ***

    // EXPORTED:
    public static void initialize() {
        UserProc.initialize();
    }

    /** initializes the module and all other type modules. */
    // EXPORTED:
    public static void reset() {
        recursionDepth = 0;
        compile_started = false;
        cur = null;
        TypeTbl.reset(compiled[0]);
        TypeTbl.reset(visible_cells[0]);
        TypeTbl.reset(visible_types[0]);
    }

    //------------------------------------------------------------------------

    //*** phase 1 ***

    /** parse a type expression */
    // EXPORTED:
    public static Type.T parse() {
        T t;
        String case_ = "";
        switch (Scanner.cur.token) {
        case tIDENT:
            case_ = "NamedType";
            t = NamedType.parse();
            break;
        case tARRAY:
            case_ = "ArrayType";
            t = ArrayType.parse();
            break;
        case tBITS:
            case_ = "PackedType";
            t = PackedType.parse();
            break;
        case tBRANDED:
            case_ = "RefType";
            t = RefType.parse();
            break;
        case tLBRACE:
            case_ = "EnumType";
            t = EnumType.parse();
            break;
        case tUNTRACED:
            case_ = "RefType";
            t = RefType.parse();
            break;
        case tOBJECT:
            case_ = "ObjectType";
            t = ObjectType.parse(null, true, null);
            break;
        case tCALLCONV:
            case_ = "ProcType1";
            t = ProcType.parse();
            break;
        case tPROCEDURE:
            case_ = "ProcType2";
            t = ProcType.parse();
            break;
        case tRECORD:
            case_ = "RecordType";
            t = RecordType.parse();
            break;
        case tREF:
            case_ = "RefType";
            t = RefType.parse();
            break;
        case tSET:
            case_ = "SetType";
            t = SetType.parse();
            break;
        case tLBRACKET:
            case_ = "SubrangeType";
            t = SubrangeType.parse();
            break;
        case tLPAREN:
            case_ = "(";
            Scanner.getToken(); // (
            t = parse();
            Scanner.match(Token.T.tRPAREN);
            if ((Scanner.cur.token == Token.T.tBRANDED)) {
                case_ = "(branded";
                t = ObjectType.parse(t, false, Brand.parse());
            } else if ((Scanner.cur.token == Token.T.tOBJECT)) {
                case_ = "(object";
                t = ObjectType.parse(t, false, null);
            }
            break;
        default:
            Scanner.fail("bad type expression");
            t = null;
        }
        if (debug) {
            System.err.print("Type.Parse ");
            System.err.print(case_);
            System.err.print(" ");
            // Port: RTIO.PutRef prints the address of t; the identity
            // hash code stands for it.
            System.err.print("0x" + Integer.toHexString(System.identityHashCode(t)));
            System.err.print("\n");
            System.err.flush();
        }
        return t;
    }

    /** initialize the shared fields of a Type.T */
    // EXPORTED (TypeRep.i3):
    static void init(T t, Class c) {
        t.origin = Scanner.offset;
        t.info.class_ = c;
        t.uid = TypeRep.NO_UID;
        t.scc_id = TypeRep.NO_SCC;
        t.rep_id = TypeRep.NO_UID;
        t.checkDepth = NOT_CHECKED;
        t.checked = false;
        t.errored = false;
        t.next = cur.module_types;
        cur.module_types = t;
        t.info.lazyAligned = false;
    }

    /**
     * sets the current module's type info to 'x' and returns
     * the previous module's type info.  This routine is only called
     * when the "current" module changes.
     */
    // EXPORTED:
    public static ModuleInfo setModule(ModuleInfo x) {
        ModuleInfo old = cur;
        if ((x == null)) {
            x = new ModuleInfo();
        }
        cur = x;
        return old;
    }

    private static void reorder() {
        T a, b, c;
        if (cur.in_order) {
            return;
        }
        a = cur.module_types;
        b = null;
        while ((a != null)) {
            c = a.next;
            a.next = b;
            b = a;
            a = c;
        }
        cur.module_types = b;
        cur.in_order = true;
    }

    //------------------------------------------------------------------------

    //*** phase 2 ***

    /**
     * type check type 't'.   Return the underlying constructed
     * (ie. class # Class.Named) type node.
     */
    // EXPORTED:
    public static Type.T check(Type.T t) {
        // ensure that 't' is checked and return the underlying constructed type
        long save_offset, save_depth;
        if ((t == null)) {
            t = ErrType.T;
        }
        if ((!t.checked)) {
            if ((t.checkDepth == recursionDepth)) {
                illegalRecursion(t);
                return ErrType.T;
            } else {
                // this node is not currently being checked at the current depth
                save_offset = Scanner.offset;
                save_depth = t.checkDepth;
                Scanner.offset = t.origin;
                t.checkDepth = recursionDepth;
                t.check();
                Scanner.offset = save_offset;
                t.checkDepth = save_depth;
                t.checked = true;
            }
        }
        return strip(t);
    }

    /**
     * type check type 't'.  Return the underlying constructed
     * (ie. class # Class.Named) type node and in 'x' its info.
     */
    // EXPORTED:
    public static Type.T checkInfo(Type.T t, Type.Info x) {
        T u = check(t);
        x.assign(u.info);
        return u;
    }

    public static void typename(Type.T t, int[] typename) {
        typename[0] = t.info.name;
    }

    //------------------------------------------------------------------------

    /**
     * Returns TRUE iff no scalars within a value of type 't', located at
     * a bit offset of 'offs' from a word boundary, cross word boundaries.
     */
    // EXPORTED:
    public static boolean straddleFreeScalars(Type.T t, long offs, boolean IsEltOrField) {
        // Returns TRUE iff no scalars within a value of type 't', located at
        // a bit offset of 'offs' from a word boundary, cross word boundaries.
        if (t == null) {
            return true;
        }
        return t.no_straddle(offs, IsEltOrField);
    }

    // internal to Strip
    // Remove typenames.
    private static boolean stripOne(T[] u) {
        if ((u[0].info.class_ != Class.Named)) {
            return true;
        }
        u[0] = NamedType.strip(u[0]);
        return false;
    }

    /** return the constructed type of 't' (ie. strip renaming) */
    // EXPORTED:
    // Remove typenames.
    public static Type.T strip(Type.T t) {
        T[] u = { t };
        T[] v = { t };
        if ((u[0] == null)) {
            return null;
        }
        while (true) {
            // If there is a cycle, then advancing one at a time
            // and two at a time will eventually make them equal.
            if (stripOne(u)) {
                return u[0];
            }
            if (stripOne(v) || stripOne(v)) {
                return v[0];
            }
            if ((u[0] == v[0])) {
                illegalRecursion(t);
                return ErrType.T;
            }
        }
    }

    // internal to StripPacked
    // Remove typenames and packing.
    private static boolean stripPackedOne(T[] u) {
        switch (u[0].info.class_) {
        case Named:
            u[0] = NamedType.strip(u[0]);
            break;
        case Packed:
            u[0] = PackedType.base(u[0]);
            break;
        default:
            return true;
        }
        return false;
    }

    /** return the unpacked type of 't' (i.e., strip renaming and packing.) */
    // EXPORTED:
    // Remove typenames and packing.
    public static Type.T stripPacked(Type.T t) {
        T[] u = { t };
        T[] v = { t };
        if ((u[0] == null)) {
            return null;
        }
        while (true) {
            // If there is a cycle, then advancing one at a time
            // and two at a time will eventually make them equal.
            if (stripPackedOne(u)) {
                return u[0];
            }
            if (stripPackedOne(v) || stripPackedOne(v)) {
                return v[0];
            }
            if ((u[0] == v[0])) {
                illegalRecursion(t);
                return ErrType.T;
            }
        }
    }

    // internal to Base
    // Remove typenames and packing and subrange.
    private static boolean baseOne(T[] u) {
        switch (u[0].info.class_) {
        case Named:
            u[0] = NamedType.strip(u[0]);
            break;
        case Packed:
            u[0] = PackedType.base(u[0]);
            break;
        case Subrange:
            u[0] = SubrangeType.base(u[0]);
            break;
        default:
            return true;
        }
        return false;
    }

    /** return the base type of 't' (strip renaming, packing &amp; subranges) */
    // EXPORTED:
    // Remove typenames and packing and subrange.
    public static Type.T base(Type.T t) {
        T[] u = { t };
        T[] v = { t };
        if ((u[0] == null)) {
            return null;
        }
        while (true) {
            // If there is a cycle, then advancing one at a time
            // and two at a time will eventually make them equal.
            if (baseOne(u)) {
                return u[0];
            }
            if (baseOne(v) || baseOne(v)) {
                return v[0];
            }
            if ((u[0] == v[0])) {
                illegalRecursion(t);
                return ErrType.T;
            }
        }
    }

    /**
     * returns the code generator's representation for 't', either
     * as a variable in memory or as an operand on the evaluation stack
     * depending on 'in_memory'
     */
    // EXPORTED:
    public static CGType cgType(Type.T t, boolean in_memory) {
        t = check(t);
        if ((in_memory)) {
            return t.info.mem_type;
        } else {
            return t.info.stk_type;
        }
    }

    public static CGType cgType(Type.T t) {
        return cgType(t, false);
    }

    /**
     * Always represented as an address on the CG stack (record, array, or large set)
     * PRE: t need not be checked.
     */
    // EXPORTED:
    public static boolean isStructured(Type.T t) {
        // Always represented as an address on the CG stack (record, array, or large set)
        // PRE: t need not be checked.
        if (t == null) {
            return false;
        }
        switch (check(t).info.class_) {
        case Named:
        case Packed:
            return isStructured(base(t));
        case Record:
        case Array:
        case OpenArray:
            return true;
        case Set:
            // Sic: the size is taken from t, not from Check (t); a named
            // type holds a copy of the info of its base.
            return (t.info.size > M3.target.Word.size);
        default:
            return false;
        }
    }

    /**
     * If 't' is not a structured type, generate code to load the scalar
     * pointed to by the address on the CG stack
     */
    // EXPORTED:
    public static void loadScalar(Type.T t) {
        t = check(t);
        switch (t.info.class_) {
        case Integer:
        case Longint:
        case Real:
        case Longreal:
        case Extended:
        case Enum:
        case Object:
        case Opaque:
        case Procedure:
        case Ref:
        case Subrange:
            CG.load_indirect(t.info.stk_type, 0, t.info.size, t.info.alignment);
            break;
        case Packed:
            if (!isStructured(t)) {
                CG.load_indirect(t.info.stk_type, 0, t.info.size, t.info.alignment);
            }
            break;
        case Set:
            if ((t.info.size <= M3.target.Integer.size)) {
                CG.load_indirect(t.info.stk_type, 0, t.info.size, t.info.alignment);
            }
            break;
        case Error:
        case Named:
        case Array:
        case OpenArray:
        case Record:
            // skip -- either it's structured or it's an error
            break;
        }
    }

    // EXPORTED:
    public static boolean isLazyAligned(Type.T t) {
        if (t == null) {
            return false;
        }
        return t.info.lazyAligned;
    }

    // EXPORTED:
    public static void setLazyAlignment(Type.T t, boolean on) {
        if (t != null) {
            t.info.lazyAligned = on;
        }
    }

    //*** phase 3 ***

    /** Prepares the types of the current module for calls to SetGlobals */
    // EXPORTED:
    public static void beginSetGlobals() {
        reorder();
        cur.next_to_set = cur.module_types;
        cur.module = Module.current();
        TypeTbl.reset(visible_cells[0]);
        initPredefinedCells();
        Module.visitImports(Type::noteCells);
    }

    private static void noteCells(Module.T m) {
        ModuleInfo mi = Module.getTypeInfo(m);
        CellInfo c = mi.full_cells;
        while ((c != null)) {
            TypeTbl.put(visible_cells, c.type, c);
            c = c.next;
        }
    }

    /**
     * assign offsets to any needed global data for any types in the
     * current module that occured lexically at or before 'origin'.
     */
    // EXPORTED:
    public static void setGlobals(long origin) {
        T t = cur.next_to_set;
        T u;
        while ((t != null) && (t.origin <= origin)) {
            u = check(t);
            if ((u.info.class_ == Class.Ref) || (u.info.class_ == Class.Object)) {
                if ((TypeTbl.get(visible_cells[0], u) == null)) {
                    addCell(u);
                }
            }
            t = t.next;
        }
        cur.next_to_set = t;
        if ((t == null)) {
            // done
            TypeTbl.reset(visible_cells[0]);
        }
    }

    private static void initPredefinedCells() {
        TypeTbl.put(visible_cells, check(Null.T), Null.T);
        TypeTbl.put(visible_cells, check(Addr.T), Addr.T);
        TypeTbl.put(visible_cells, check(Reff.T), Reff.T);
        TypeTbl.put(visible_cells, check(ObjectRef.T), ObjectRef.T);
        TypeTbl.put(visible_cells, check(ObjectAdr.T), ObjectAdr.T);
        // ********* opaque types don't have typecells ************
        // EVAL TypeTbl.Put (visible_cells, Check(Textt.T),     Textt.T);
        // EVAL TypeTbl.Put (visible_cells, Check(Mutex.T),     Mutex.T);
        // ********************************************************
    }

    /**
     * Creates a typecell for t.  Normally, this done after Check
     * time by Module.SetGlobals, but that only gets types created during
     * the Check phase.  This is for types that can be only created later.
     * In particular, see ArrayExpr.InnerPrep.
     */
    // EXPORTED:
    public static void addCell(Type.T t) {
        CellInfo c = new CellInfo();
        long size = M3.rt.TC_SIZE;
        T[] u = new T[1];
        t = strip(t);
        if ((t.info.class_ == Class.Object)) {
            size = M3.rt.OTC_SIZE;
        } else if (RefType.split(t, u) && OpenArrayType.is(u[0])) {
            size = M3.rt.ATC_SIZE;
        }
        c.next = cur.full_cells;
        cur.full_cells = c;
        c.type = t;
        c.unit = cur.module;
        c.offset = Module.allocate(size, M3.target.Address.align, false, "typecell");
        TypeTbl.put(visible_cells, t, c);
    }

    /** return TRUE if the type is an ordinal (Integer, Enum, Subrange) */
    // EXPORTED:
    public static boolean isOrdinal(Type.T t) {
        T u = stripPacked(check(t));
        Class c = u.info.class_;
        return (c == Class.Integer) || (c == Class.Longint) || (c == Class.Subrange)
            || (c == Class.Enum) || (c == Class.Error);
    }

    /** return the number of values of the type;  -1 if t is not an ordinal type */
    // EXPORTED:
    public static int[] number(Type.T t) {
        T u = stripPacked(check(t));
        Class c = u.info.class_;
        boolean b;
        int[] min = TInt.newInt();
        int[] max = TInt.newInt();
        int[] tmp = TInt.newInt();
        if ((c == Class.Subrange)) {
            b = SubrangeType.split(u, min, max);
            AssertError.check(b, "Type");
        } else if ((c == Class.Enum)) {
            b = TInt.fromInt(EnumType.numElts(u), max);
            AssertError.check(b, "Type");
            return max;
        } else if ((c == Class.Integer)) {
            TInt.assign(min, M3.target.Integer.min);
            TInt.assign(max, M3.target.Integer.max);
        } else if ((c == Class.Longint)) {
            TInt.assign(min, M3.target.Longint.min);
            TInt.assign(max, M3.target.Longint.max);
        } else if ((c == Class.Error)) {
            return TInt.copy(TInt.Zero);
        } else {
            Error.msg("INTERNAL ERROR: Type.Number applied to a non-ordinal type");
            throw new AssertError("Type");
        }
        if (TInt.subtract(max, min, tmp)
            && TInt.add(tmp, TInt.One, max)
            && !TInt.LT(max, M3.target.Integer.min)
            && !TInt.LT(M3.target.Integer.max, max)) {
            return max;
        }
        Error.msg("type has too many elements");
        return TInt.copy(M3.target.Integer.max);
    }

    /**
     * return the bounds and true for ordinal types,
     * [0,-1] and FALSE for non-ordinal types
     */
    // EXPORTED:
    public static boolean getBounds(Type.T t, int[] min, int[] max) {
        T u = stripPacked(check(t));
        Class c = u.info.class_;
        boolean b;
        if ((c == Class.Subrange)) {
            b = SubrangeType.split(u, min, max);
            AssertError.check(b, "Type");
            return true;
        } else if ((c == Class.Enum)) {
            b = TInt.fromInt(EnumType.numElts(u), min);
            AssertError.check(b, "Type");
            b = TInt.subtract(min, TInt.One, max);
            AssertError.check(b, "Type");
            TInt.assign(min, TInt.Zero);
            return true;
        } else if ((c == Class.Integer)) {
            TInt.assign(min, M3.target.Integer.min);
            TInt.assign(max, M3.target.Integer.max);
            return true;
        } else if ((c == Class.Longint)) {
            TInt.assign(min, M3.target.Longint.min);
            TInt.assign(max, M3.target.Longint.max);
            return true;
        } else {
            TInt.assign(min, TInt.Zero);
            TInt.assign(max, TInt.MOne);
            return false;
        }
    }

    private static void illegalRecursion(T t) {
        M3.QID name = new M3.QID();
        Value.T[] v = new Value.T[1];
        if ((t.errored)) {
            // don't reissue the error message
        } else if (NamedType.splitV(t, v)) {
            Value.illegalRecursion(v[0]);
        } else if (NamedType.split(t, name)) {
            Error.QID(name, "illegal recursive type declaration");
        } else {
            Error.msg("illegal recursive type declaration");
        }
        t.errored = true;
    }

    //------------------------------------------------------------------------

    /** TRUE iff (a == b) given the assumptions x */
    // EXPORTED:
    public static boolean isEqual(Type.T a, Type.T b, Type.Assumption x) {
        // Considers ErrType.T to be equal to any type.
        Assumption assume;
        Assumption y;
        Class ac, bc;
        if ((a == null) || (b == null)) {
            return false;
        }
        if ((a == b) /*** OR (a = NIL) OR (b = NIL) ***/) {
            return true;
        }

        a = strip(a);
        b = strip(b);
        ac = a.info.class_;
        bc = b.info.class_;
        if ((a == b)) {
            return true;
        }
        if ((ac == Class.Error) || (bc == Class.Error)) {
            return true; // Reduce error cascade.
        }
        if ((ac != bc)) {
            return false;
        }

        // try their id's first
        if ((a.uid != TypeRep.NO_UID) && (b.uid != TypeRep.NO_UID)) {
            return (a.uid == b.uid);
        }

        // at this point, both types have the same class, but one or both
        // of them is unchecked

        // search the existing list of assumptions
        y = x;
        while ((y != null)) {
            if ((y.a == a)) {
                if ((y.b == b)) {
                    return true;
                }
            } else if ((y.a == b)) {
                if ((y.b == a)) {
                    return true;
                }
            }
            y = y.prev;
        }

        // add a new assumption
        // Port: the local record 'assume' is a new object.
        assume = new Assumption();
        assume.prev = x;
        assume.a = a;
        assume.b = b;
        y = assume;
        // Pretty edgy:  Building a linked list whose nodes are instances of
        // local variable 'assume' of procedures on the call chain!

        if (!a.isEqual(b, y)) {
            return false;
        }

        if ((x == null)) {
            // with no assumptions we now know that the types are equal...
            if ((a.uid == TypeRep.NO_UID)) {
                a.fp = b.fp;
                a.uid = b.uid;
            }
            if ((b.uid == TypeRep.NO_UID)) {
                b.fp = a.fp;
                b.uid = a.uid;
            }
        }

        return true;
    }

    //------------------------------------------------------------------------

    /** TRUE iff (a &lt;: b) */
    // EXPORTED:
    public static boolean isSubtype(Type.T a, Type.T b) {
        if ((a == null) || (b == null)) {
            return false;
        }
        if ((a == b) /*** OR (a = NIL) OR (b = NIL) ***/) {
            return true;
        }

        a = strip(a);
        b = strip(b);
        if ((a.info.class_ == Class.Error) || (b.info.class_ == Class.Error)) {
            return true; // Reduce error cascade.
        }
        a = stripPacked(a);
        b = stripPacked(b);

        // try their id's first
        if ((a.uid == TypeRep.NO_UID) || (b.uid == TypeRep.NO_UID)) {
            if (isEqual(a, b, null)) {
                return true;
            }
        } else if ((a.uid == b.uid)) {
            return true;
        }

        // I give up, call the methods.
        return a.isSubtype(b) || OpaqueType.isSubtype(a, b);
    }

    private static boolean isCharacterType(T t) {
        return isEqual(t, Charr.T, null) || isEqual(t, WCharr.T, null);
    }

    /** TRUE iff (a := b) typechecks */
    // EXPORTED:
    public static boolean isAssignable(Type.T a, Type.T b) {
        T[] i = new T[1];
        T[] e = new T[1];
        int[] min_a = TInt.newInt();
        int[] max_a = TInt.newInt();
        int[] min_b = TInt.newInt();
        int[] max_b = TInt.newInt();
        int[] min = TInt.newInt();
        int[] max = TInt.newInt();
        T base_a, base_b;
        if (isEqual(a, b, null) || isSubtype(b, a)) {
            return true;
        } else if (isOrdinal(a)) {
            // ordinal types:  OK if there is a common supertype
            // and they have at least one member in common.
            base_a = base(a);
            base_b = base(b);
            if ((isEqual(base_a, base_b, null)
                 || (isCharacterType(base_a) && isCharacterType(base_b)))
                && getBounds(a, min_a, max_a)
                && getBounds(b, min_b, max_b)) {
                // check for a non-empty intersection
                TInt.assign(min, min_a);
                if (TInt.LT(min, min_b)) {
                    TInt.assign(min, min_b);
                }
                TInt.assign(max, max_a);
                if (TInt.LT(max_b, max)) {
                    TInt.assign(max, max_b);
                }
                return TInt.LE(min, max);
            } else {
                return false;
            }
        } else if (isSubtype(a, b)) {
            // may be ok, but must narrow rhs before doing the assignment
            return isSubtype(b, Reff.T)
                || ArrayType.split(b, i, e)
                || (isSubtype(b, Addr.T)
                    && (!Module.isSafe() || !isEqual(b, Addr.T, null)));
        } else {
            return false;
        }
    }

    //------------------------------------------------------------------------

    /** return the unique id for the type in the generated code */
    // EXPORTED:
    public static long globalUID(Type.T t) {
        T u = check(t);
        if ((u.uid == TypeRep.NO_UID)) {
            TypeFP.fromType(u);
        }
        if ((t != null) && (t.uid == TypeRep.NO_UID)) {
            t.uid = u.uid;
            t.fp = u.fp;
        }
        return u.uid;
    }

    private static final char[] digits = { '0', '1', '2', '3', '4', '5', '6', '7',
                                           '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    // EXPORTED:
    public static String name(Type.T t) {
        char[] buf = new char[10];
        long h = globalUID(t);
        buf[0] = '_';
        buf[1] = 't';
        for (int i = 9; i >= 2; i--) {
            buf[i] = digits[(int) Long.remainderUnsigned(h, 16)];
            h = Long.divideUnsigned(h, 16);
        }
        return new String(buf);
    }

    //------------------------------------------------------------------------

    //*** phase 4 ***

    /** compile all the local types for the current module */
    // EXPORTED:
    public static void compileAll() {
        T t = cur.module_types;
        while ((t != null)) {
            compile(t);
            t = t.next;
        }
    }

    /** generates the debugging declarations for 't' */
    // EXPORTED:
    public static void compile(Type.T t) {
        long save;
        T u = check(t);
        if ((!compile_started)) {
            initCompilation();
        }
        if ((TypeTbl.put(compiled, u, u) != null)) {
            return;
        }
        save = Scanner.offset;
        Scanner.offset = t.origin;
        t.compile();
        Scanner.offset = save;
        if ((TypeTbl.get(visible_types[0], u) != null)) {
            // it's visible in one of our imports
            Host.env.note_type(globalUID(u), /* imported := */ true);
        } else if (Module.isInterface()) {
            Host.env.note_type(globalUID(t), /* imported := */ false);
        }
    }

    private static void initCompilation() {
        compile_started = true;
        TypeTbl.reset(compiled[0]);
        TypeTbl.reset(visible_types[0]);
        if ((Host.emitBuiltins)) {
            return;
        }
        Module.visitImports(Type::noteTypes);
        TypeTbl.put(compiled, check(Addr.T), Addr.T);
        TypeTbl.put(compiled, check(Bool.T), Bool.T);
        TypeTbl.put(compiled, check(Charr.T), Charr.T);
        TypeTbl.put(compiled, check(Card.T), Card.T);
        TypeTbl.put(compiled, check(EReel.T), EReel.T);
        TypeTbl.put(compiled, check(Int.T), Int.T);
        TypeTbl.put(compiled, check(LInt.T), LInt.T);
        TypeTbl.put(compiled, check(LReel.T), LReel.T);
        TypeTbl.put(compiled, check(Mutex.T), Mutex.T);
        TypeTbl.put(compiled, check(Null.T), Null.T);
        TypeTbl.put(compiled, check(ObjectRef.T), ObjectRef.T);
        TypeTbl.put(compiled, check(ObjectAdr.T), ObjectAdr.T);
        TypeTbl.put(compiled, check(Reel.T), Reel.T);
        TypeTbl.put(compiled, check(Reff.T), Reff.T);
        TypeTbl.put(compiled, check(Textt.T), Textt.T);
        TypeTbl.put(compiled, check(WCharr.T), WCharr.T);
        TypeTbl.put(compiled, check(ErrType.T), ErrType.T);
    }

    private static void noteTypes(Module.T m) {
        ModuleInfo mi = Module.getTypeInfo(m);
        T t = mi.module_types;
        while ((t != null)) {
            if ((t.info.class_ != Class.Named)) {
                TypeTbl.put(visible_types, t, t);
            }
            t = t.next;
        }
    }

    private static CellPtr addCellPtr(T t) {
        CellPtr c = new CellPtr();
        c.next = cur.cell_ptrs;
        cur.cell_ptrs = c;
        c.type = t;
        c.offset = Module.allocate(2 * M3.target.Address.pack,
                                   M3.target.Address.align, false, "typecell ptr");
        return c;
    }

    private static CellPtr findCell(T t) {
        CellPtr c = cur.cell_ptrs;
        T u = check(t);
        while (true) {
            if ((c == null)) {
                return addCellPtr(u);
            }
            if (isEqual(c.type, u, null)) {
                return c;
            }
            c = c.next;
        }
    }

    /**
     * loads the specified field of 't's typecell.  If 'offset' is less than
     * zero, 'LoadInfo' loads the address of the typecell.
     */
    // EXPORTED:
    public static void loadInfo(Type.T t, long offset, boolean addr) {
        CellPtr c = findCell(t);
        M3CG.Var v = Module.globalData(false);
        if ((offset < 0)) {
            AssertError.check(!addr, "Type");
            CG.load_addr(v, c.offset, M3.rt.TC_ALIGN);
        } else if ((offset == M3.rt.TC_typecode)) {
            CG.load_int(M3.target.Integer.cg_type, v, c.offset + M3.target.Address.pack);
        } else {
            CG.load_addr(v, c.offset, M3.rt.TC_ALIGN);
            if ((addr)) {
                CG.load_indirect(CGType.Addr, offset, M3.target.Address.size);
                CG.boost_addr_alignment(M3.target.Address.align);
            } else {
                CG.load_indirect(M3.target.Integer.cg_type, offset, M3.target.Integer.size);
            }
        }
    }

    public static void loadInfo(Type.T t, long offset) {
        loadInfo(t, offset, false);
    }

    /** the cost of initializing a 't'.  (0 IFF no init required) */
    // EXPORTED:
    public static long initCost(Type.T t, boolean ifZeroed) {
        return check(t).initCost(ifZeroed);
    }

    /** emits the type map for type 't' occupying 'size' bits at 'offset'. */
    // EXPORTED:
    public static void genMap(Type.T t, long offset, long size, boolean refs_only) {
        T u = check(t);
        long nat_sz = u.info.size;
        if ((size < 0)) {
            size = nat_sz;
        }
        if ((refs_only) && (!u.info.isTraced)) {
            return;
        }
        u.mapper(offset, Math.min(size, nat_sz), refs_only);
    }

    /** generate the runtime description for type 't' */
    // EXPORTED:
    public static void genDesc(Type.T t) {
        check(t).gen_desc();
    }

    /** generate a comment with 'tag' and 't's name */
    // EXPORTED:
    public static void genTag(Type.T t, String tag, long offset) {
        //* CG.Gen_location (t.origin); *
        CG.comment(offset, false, tag, name(t));
    }

    /**
     * Return a name for "t" that includes "tag" and will be unique
     * to this compilation unit.
     */
    // EXPORTED:
    public static String linkName(Type.T t, String tag) {
        // Port: Insert = ARRAY BOOLEAN OF TEXT { "_M3", "_I3" }.
        return M3ID.toText(M3.ids, Module.name(null))
            + (Module.isInterface() ? "_I3" : "_M3") + name(t) + tag;
    }

    //*** phase 5 ***

    /**
     * generate the current module's linked list of typecells
     * and return its offset in the module global data.
     */
    // EXPORTED:
    public static long genCells() {
        CellInfo cell = cur.full_cells;
        long prev = 0;
        if ((cell == null)) {
            return -1;
        }
        while ((cell != null)) {
            CG.comment(cell.offset, false, "typecell for ", name(cell.type));
            if ((cell.type.info.class_ == Class.Ref)) {
                RefType.initTypecell(cell.type, cell.offset, prev);
            } else {
                ObjectType.initTypecell(cell.type, cell.offset, prev);
            }
            prev = cell.offset;
            cell = cell.next;
        }
        return prev;
    }

    /**
     * generate the current module's linked list of pointers to typecells
     * and return its offset in the module global data.
     */
    // EXPORTED:
    public static long genCellPtrs() {
        M3CG.Var unit = Module.globalData(false);
        CellPtr cell = cur.cell_ptrs;
        long prev = 0;
        // initialize the linked list of type cell pointers
        if ((cell == null)) {
            return -1;
        }
        while ((cell != null)) {
            if ((prev != 0)) {
                CG.init_var(cell.offset, unit, prev, false);
            }
            CG.init_intt(cell.offset + M3.target.Address.pack, M3.target.Integer.size,
                         globalUID(cell.type), false);
            prev = cell.offset;
            cell = cell.next;
        }
        return prev;
    }

    //********************* variable initialization **************************

    /**
     * initialize the variable addressed by s0.A to an arbitrary value of type 't'.
     * If 'zeroed' the variable is assumed to already have all bits set to zero.
     */
    // EXPORTED:
    public static void initValue(Type.T t, boolean zeroed) {
        long c1, c2;
        CG.Val tmp;
        t = check(t);

        c1 = initCost(t, zeroed);
        if ((c1 == 0)) {
            CG.discard(CGType.Addr);
            return;
        }

        if ((!zeroed)) {
            c2 = initCost(t, true);
            if ((c2 == 0)) {
                // all we need to do is zero it
                CG.boost_addr_alignment(t.info.alignment);
                zero(t);
                return;
            } else if ((c1 > 2 * c2)) {
                // it will pay to zero the variable first
                tmp = CG.pop();
                CG.push(tmp);
                CG.boost_addr_alignment(t.info.alignment);
                zero(t);
                CG.push(tmp);
                CG.boost_addr_alignment(t.info.alignment);
                t.initValue(true);
                CG.free(tmp);
                return;
            }
        }

        t.initValue(zeroed);
    }

    /** initialize the variable of type 't' addressed by s0.A to zeros. */
    // EXPORTED:
    public static void zero(Type.T full_t) {
        T u = check(full_t);
        T t = base(u);
        long size = u.info.size;
        switch (t.info.class_) {
        case Integer:
        case Longint:
        case Subrange:
        case Enum:
            CG.load_integer(u.info.stk_type, TInt.Zero);
            CG.store_indirect(u.info.stk_type, 0, size);
            break;
        case Real:
            CG.load_float(TFloat.ZeroR);
            CG.store_indirect(CGType.Reel, 0, size);
            break;
        case Longreal:
            CG.load_float(TFloat.ZeroL);
            CG.store_indirect(CGType.LReel, 0, size);
            break;
        case Extended:
            CG.load_float(TFloat.ZeroX);
            CG.store_indirect(CGType.XReel, 0, size);
            break;
        case Opaque:
        case Ref:
        case Object:
        case Procedure:
            CG.load_nil();
            CG.store_indirect(CGType.Addr, 0, size);
            break;
        case Set:
            if ((size <= M3.target.Integer.size)) {
                CG.load_integer(M3.target.Integer.cg_type, TInt.Zero);
                CG.store_indirect(M3.target.Integer.cg_type, 0, size);
            } else if ((size <= 8 * M3.target.Integer.size)) {
                zeroWords(size);
            } else {
                CG.zero(size);
            }
            break;
        default:
            if ((size <= 0)) {
                CG.discard(CGType.Addr);
            } else if ((size <= 8 * M3.target.Integer.size)
                       && (t.info.alignment >= M3.target.Integer.align)
                       && (Math.floorMod(size, M3.target.Integer.align) == 0)) {
                zeroWords(size);
            } else {
                CG.zero(size);
            }
        }
    }

    private static void zeroWords(long size) {
        CG.Val lv = CG.pop();
        long offset = 0;
        while ((size >= M3.target.Integer.size)) {
            CG.push(lv);
            CG.load_integer(M3.target.Integer.cg_type, TInt.Zero);
            CG.store_indirect(M3.target.Integer.cg_type, offset, M3.target.Integer.size);
            offset += M3.target.Integer.size;
            size -= M3.target.Integer.size;
        }
        CG.free(lv);
    }

    //************************* default methods *******************************

    /** == RETURN FALSE */
    // EXPORTED (TypeRep.i3):
    static boolean neverEqual(T a, T b, Assumption x) {
        AssertError.check(a != b, "Type");
        return false;
    }

    /** == RETURN FALSE */
    // EXPORTED (TypeRep.i3):
    static boolean noSubtypes(T a, T b) {
        // a is not a subtype of any type b
        return false;
    }

    /** == initialize Size(t) bits to zero */
    // EXPORTED (TypeRep.i3):
    static void initToZeros(T t, boolean zeroed) {
        if (!zeroed) {
            zero(t);
        } else {
            CG.discard(CGType.Addr);
        }
    }

    /** == TypeMap.Add (offset, Op.{Untraced}Ref, 0) */
    // EXPORTED (TypeRep.i3):
    static void genRefMap(T t, long offset, long size, boolean refs_only) {
        T u = check(t);
        AssertError.check(size == M3.target.Address.size, "Type");
        if (u.info.isTraced) {
            TipeMap.add(offset, TipeMap.Op.Ref, 0);
        } else if ((!refs_only)) {
            TipeMap.add(offset, TipeMap.Op.UntracedRef, 0);
        }
    }

    /** == TypeDesc.AddO (Op.{Untraced}Ref); TypeDesc.AddI (UID(t)) */
    // EXPORTED (TypeRep.i3):
    static void genRefDesc(T t) {
        // Port: XX = ARRAY BOOLEAN OF TT { TT.UntracedRef, TT.Ref }.
        T u = check(t);
        if (TipeDesc.addO(u.info.isTraced ? TipeDesc.Op.Ref : TipeDesc.Op.UntracedRef, u)) {
            TipeDesc.addU(globalUID(u));
        }
    }

    /**
     * == RETURN (offset MOD t.alignment = 0)
     * To preclude word-boundary-straddle, this assumes t's size
     * is no greater than its alignment.
     */
    // EXPORTED (TypeRep.i3):
    static boolean scalarNoStraddle(T t, long offset, boolean IsEltOrField) {
        // To preclude word-boundary-straddle, this assumes t's size
        // is no greater than its alignment.
        T u = check(t);
        if (u.info.lazyAligned) {
            return (Math.floorMod(offset, 8) == 0);
        } else {
            AssertError.check(u.info.size <= u.info.alignment, "Type");
            return (Math.floorMod(offset, u.info.alignment) == 0);
        }
    }

    /** == RETURN (offset MOD Target.Address.align = 0) */
    // EXPORTED (TypeRep.i3):
    static boolean addrNoStraddle(T t, long offset, boolean IsEltOrField) {
        T u = check(t);
        if (u.info.lazyAligned) {
            return (Math.floorMod(offset, 8) == 0);
        } else {
            return (Math.floorMod(offset, M3.target.Address.align) == 0);
        }
    }
}
