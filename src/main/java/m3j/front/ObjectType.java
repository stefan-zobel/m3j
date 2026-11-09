// Java port of CM3's m3-sys/m3front/src/types/ObjectType.i3 and ObjectType.m3.
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
import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: ObjectType.i3
// Last Modified On Mon Jul 25 08:44:06 PDT 1994 By kalsow
//      Modified On Fri Oct  5 07:29:44 1990 By muller

// File: ObjectType.m3
// Last modified on Tue Jun 20 09:50:29 PDT 1995 by kalsow
//      modified on Thu Jun 15 12:45:03 PDT 1995 by ericv
//      modified on Thu Dec  5 17:22:00 PST 1991 by muller

/** Object types. */
public final class ObjectType {
    private ObjectType() {}

    private static final long Unknown_w_magic = -1;
    private static final long Unknown_wo_magic = -2;
    private static final long Unchecked_offset = -3;

    public static final class P extends Type.T {
        public Brand.T brand;
        public Type.T superType;
        public Scope.T fields;
        public long fieldOffset;
        public long fieldSize;
        public long fieldAlign;
        public Scope.T methods;
        public long methodSize;
        public long methodOffset;
        public long overrideSize;
        public Module.T tc_module;
        public boolean inPrimLookUp;
        public boolean isTraced;
        public String user_name;
        /** Port, addition: the opaque type declared as a subtype of this object type, or null. */
        public OpaqueType.P opaque;

        @Override
        public void check() {
            ObjectType.check(this);
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
            TypeRep.genRefDesc(this);
        }

        @Override
        public void fprint(M3.FPInfo x) {
            fPrinter(this, x);
        }
    }

    private static long NIL_ID = 0;
    private static long ROOT_ID = 0;
    private static long UROOT_ID = 0;

    // EXPORTED:
    public static Type.T parse(Type.T super_, boolean traced, Brand.T brand) {
        P p;
        while (true) {
            p = (P) new_(super_, traced, brand, null, null);
            Scanner.match(Token.T.tOBJECT);

            p.fields = Scope.pushNew(false, M3ID.NoID);
            RecordType.parseFieldList();
            Scope.popNew();

            p.methods = Scope.pushNew(false, M3ID.NoID);
            if ((Scanner.cur.token == Token.T.tMETHODS)) {
                Scanner.getToken(); // METHODS
                p.methodSize = parseMethodList(p, /* overrides := */ false);
            }
            if ((Scanner.cur.token == Token.T.tOVERRIDES)) {
                Scanner.getToken(); // OVERRIDES
                p.overrideSize = parseMethodList(p, /* overrides := */ true);
            }
            Scope.popNew();

            Scanner.match(Token.T.tEND);
            brand = Brand.parse();
            if ((Scanner.cur.token != Token.T.tOBJECT)) {
                if ((brand != null)) {
                    Error.msg("dangling brand");
                }
                break;
            }
            super_ = p;
            traced = false;
        }
        return p;
    }

    private static long parseMethodList(P p, boolean overrides) {
        Method.Info info = new Method.Info();
        info.offset = 0;
        info.parent = p;
        info.override = overrides;
        while ((Scanner.cur.token == Token.T.tIDENT)) {
            info.name = Scanner.cur.id;
            Scanner.getToken(); // ID

            info.signature = null;
            if ((Scanner.cur.token == Token.T.tLPAREN)) {
                info.signature = ProcType.parseSignature(M3ID.NoID,
                                                         M3.target.DefaultCall);
            }

            info.dfault = null;
            if ((Scanner.cur.token == Token.T.tEQUAL)) {
                Error.msg("default value must begin with ':='");
                Scanner.cur.token = Token.T.tASSIGN;
            }
            if (Scanner.cur.token == Token.T.tASSIGN) {
                Scanner.getToken(); // :=
                info.dfault = Expr.parse();
            }

            if (overrides) {
                if (info.signature != null) {
                    Error.ID(info.name, "overrides cannot have a signature (2.2.9).");
                } else if (info.dfault == null) {
                    Error.ID(info.name, "missing default value in method override (2.2.9).");
                }
            } else {
                if (info.signature == null) {
                    Error.ID(info.name, "missing method signature (2.2.9).");
                }
            }

            Method.new_(info);
            info.offset += M3.target.Address.size;

            if ((Scanner.cur.token != Token.T.tSEMI)) {
                break;
            }
            Scanner.getToken(); // ;
        }

        return info.offset;
    }

    // EXPORTED:
    public static Type.T new_(Type.T super_, boolean traced, Brand.T brand, Scope.T fields, Scope.T methods) {
        P p;
        if ((super_ == null)) {
            if ((traced)) {
                super_ = ObjectRef.T;
            } else {
                super_ = ObjectAdr.T;
            }
        }
        p = new P();
        TypeRep.init(p, Type.Class.Object);
        p.isTraced = traced;
        p.brand = brand;
        p.superType = super_;
        p.fields = fields;
        p.fieldOffset = Unchecked_offset;
        p.fieldSize = -1;
        p.fieldAlign = -1;
        p.methods = methods;
        p.methodSize = 0;
        p.methodOffset = Unchecked_offset;
        p.overrideSize = 0;
        p.tc_module = null;
        p.inPrimLookUp = false;
        p.user_name = null;
        return p;
    }

    /** Including (opaque type or revelation) of an object type. */
    // EXPORTED:
    public static boolean is(Type.T t) {
        // Including (opaque type or revelation) of an object type.
        Revelation.TypeList m;
        Type.T u;
        Revelation.TypeSet x = new Revelation.TypeSet();
        if ((t == null)) {
            return false;
        }
        t = Type.stripPacked(t);
        if ((t.info.class_ == Type.Class.Named)) {
            t = Type.strip(t);
        }

        // try for TYPE t = OBJECT ... END
        if ((t.info.class_ == Type.Class.Object)) {
            return true;
        }

        if ((t.info.class_ != Type.Class.Opaque)) {
            return false;
        }

        // try for TYPE t <: ObjectType
        u = OpaqueType.super_(t);
        if (is(u)) {
            return true;
        }

        // **************
        // (* try for REVEAL t = OBJECT ... END *)
        // u := Revelation.LookUp (t);
        // IF (u # NIL) AND (u.class = Type.Class.Object) THEN RETURN TRUE END;
        // *******************

        Revelation.lookUpAll(t, x);

        // try for REVEAL t <: OBJECT ... END
        for (long i = 0; i <= x.cnt - 1; i++) {
            u = Type.strip(x.types[(int) i]);
            if ((u != null) && (u.info.class_ == Type.Class.Object)) {
                return true;
            }
        }
        m = x.others;
        while ((m != null)) {
            u = Type.strip(m.type);
            if ((u != null) && (u.info.class_ == Type.Class.Object)) {
                return true;
            }
            m = m.next;
        }

        // try for REVEAL t <: U where U is an object type
        for (long i = 0; i <= x.cnt - 1; i++) {
            if (is(x.types[(int) i])) {
                return true;
            }
        }
        m = x.others;
        while ((m != null)) {
            if (is(m.type)) {
                return true;
            }
            m = m.next;
        }

        return false;
    }

    // EXPORTED:
    public static boolean isBranded(Type.T t) {
        Type.Info info = new Type.Info();
        t = Type.stripPacked(t);
        t = Type.checkInfo(t, info);
        if ((info.class_ != Type.Class.Object)) {
            return false;
        }

        // try for TYPE t = BRANDED OBJECT ... END
        if ((info.class_ == Type.Class.Object)) {
            return (((P) t).brand != null);
        }

        if ((info.class_ != Type.Class.Opaque)) {
            return false;
        }

        // try for REVEAL t = BRANDED OBJECT ... END
        t = Revelation.lookUp(t);
        if ((t == null)) {
            return false;
        }
        t = Type.checkInfo(t, info);
        if ((info.class_ == Type.Class.Object)) {
            return (((P) t).brand != null);
        }

        return false;
    }

    /**
     * Returns the super type of 't' if 't' is an object and it
     * has been typechecked, otherwise NIL
     */
    // EXPORTED:
    public static Type.T super_(Type.T t) {
        Type.Info info = new Type.Info();
        t = Type.stripPacked(t);
        t = Type.checkInfo(t, info);
        if ((info.class_ != Type.Class.Object)) {
            return null;
        }
        return ((P) t).superType;
    }

    // EXPORTED:
    public static boolean lookUp(Type.T t, int id, Value.T[] value, Type.T[] visible) {
        P p;
        Value.T v;
        Type.T z;
        Type.Info info = new Type.Info();
        Revelation.TypeSet x = new Revelation.TypeSet();
        while (true) {
            t = Type.stripPacked(t);
            t = Type.checkInfo(t, info);

            if ((info.class_ == Type.Class.Error)) {
                value[0] = null;
                visible[0] = ErrType.T;
                return false;

            } else if ((info.class_ == Type.Class.Object)) {
                // found an object type => try it!
                p = (P) t;
                v = Scope.lookUp(p.methods, id, true);
                if ((v != null)) {
                    // find the first non-override declaration for this method
                    p = primaryMethodDeclaration(p, v);
                    if ((p == null)) {
                        return false;
                    }
                } else {
                    // try for a field
                    v = Scope.lookUp(p.fields, id, true);
                }
                if ((v != null)) {
                    value[0] = v;
                    visible[0] = p;
                    return true;
                }
                t = p.superType;

            } else if ((info.class_ == Type.Class.Opaque)) {
                // try any revelations that are visible
                z = Revelation.lookUp(t);
                if ((z != null)) {
                    // use the concrete type
                    t = z;
                } else {
                    // try any subtype revelations that are visible
                    Revelation.lookUpAll(t, x);
                    for (long i = 0; i <= x.cnt - 1; i++) {
                        if (lookUp(x.types[(int) i], id, value, visible)) {
                            return true;
                        }
                    }
                    while ((x.others != null)) {
                        if (lookUp(x.others.type, id, value, visible)) {
                            return true;
                        }
                        x.others = x.others.next;
                    }
                    t = OpaqueType.super_(t);
                }

            } else { // ???
                return false;
            }

        } // LOOP
    }

    private static P primaryMethodDeclaration(P p, Value.T v) {
        Method.Info method = new Method.Info();
        Type.T[] visible = new Type.T[1];
        Value.T[] obj = new Value.T[1];
        Method.splitX(v, method);
        if (!method.override) {
            return p;
        }
        if (p.inPrimLookUp) {
            Error.msg("illegal recursive supertype (2.4.8).");
        } else {
            p.inPrimLookUp = true;
            if (lookUp(p.superType, method.name, obj, visible)) {
                p.inPrimLookUp = false;
                return (P) visible[0];
            }
            p.inPrimLookUp = false;
        }
        return null;
    }

    // Externally dispatched-to.
    private static void check(P p) {
        Type.T super_;
        int name;
        long n;
        Value.T o;
        Value.T[] v = new Value.T[1];
        Type.T[] t1 = new Type.T[1];
        long[] hash = new long[1];
        Method.Info method = new Method.Info();
        M3.CheckState cs = M3.OuterCheckState.copy();
        Type.Info super_info = new Type.Info();
        hash[0] = 0;
        p.info.size = M3.target.Address.size;
        p.info.min_size = M3.target.Address.size;
        p.info.alignment = M3.target.Address.align;
        p.info.addr_align = Target.Word8.align;
        p.info.mem_type = CGType.Addr;
        p.info.stk_type = CGType.Addr;
        p.info.class_ = Type.Class.Object;
        p.info.isEmpty = false;
        p.info.isSolid = true;
        p.info.hash = hash[0];

        // check out my super type
        super_ = p.superType;
        if (super_ == null) { // p is ROOT or UNTRACED ROOT
            p.info.addr_align = M3.target.Address.align;
        } else { // some super type specified
            super_ = Type.checkInfo(super_, super_info);
            p.superType = super_;
            if (is(super_) || Module.isJavaType(super_)) {
                // super type is an object type, or a Java class
                p.isTraced = super_info.isTraced;
                p.info.addr_align = super_info.addr_align;
                hash[0] = super_info.hash * 37;
                if ((super_ == p)) {
                    Error.msg("illegal recursive supertype (2.4.8).");
                    super_ = null;
                    p.superType = null;
                }
            } else {
                // super type isn't an object!
                Error.msg("super type must be an object type (2.2.9).");
                p.superType = null;
                p.isTraced = super_info.isTraced;
            }
        }
        p.info.isTraced = p.isTraced;

        Brand.check(p.brand, p, hash, cs);

        // include the fields in my hash value
        o = Scope.toList(p.fields);
        n = 0;
        while ((o != null)) {
            name = Value.cName(o);
            hash[0] = hash[0] * 23 + M3ID.hash(M3.ids, name);
            hash[0] = hash[0] * 23 + n;
            if ((Scope.lookUp(p.methods, name, true) != null)) {
                Error.ID(name, "field and method with the same name (2.2.9).");
            }
            o = o.next;
            n++;
        }

        // include the methods in my hash value
        o = Scope.toList(p.methods);
        while ((o != null)) {
            name = Value.cName(o);
            hash[0] = hash[0] * 23 + M3ID.hash(M3.ids, name);
            hash[0] = hash[0] * 23 + 617;
            o = o.next;
        }

        Type.recursionDepth++; //------------------------------------
        p.checked = true;

        // bind method overrides to their original declarations
        o = Scope.toList(p.methods);
        while ((o != null)) {
            Method.splitX(o, method);
            if ((method.override)) {
                if (lookUp(super_, method.name, v, t1)
                    && Method.split(v[0], method)) {
                    Method.noteOverride(o, v[0]);
                } else {
                    Scanner.offset = o.origin;
                    Error.ID(method.name, "no method to override in supertype (2.2.9).");
                }
            }
            o = o.next;
        }

        // checkout my fields & methods
        Scope.typeCheck(p.fields, cs);
        Scope.typeCheck(p.methods, cs);

        Type.recursionDepth--; //------------------------------------

        // compute the size & alignment requirements of my fields
        getSizes(p);

        if ((!p.isTraced) && Module.isSafe()) {
            checkTracedFields(p);
        }
    }

    private static void checkTracedFields(P p) {
        Value.T v = Scope.toList(p.fields);
        Type.Info info = new Type.Info();
        while ((v != null)) {
            Type.checkInfo(Value.typeOf(v), info);
            if (info.isTraced) {
                Error.ID(Value.cName(v), "unsafe: untraced object contains a traced field");
            }
            v = v.next;
        }
    }

    // Externally dispatched-to.
    private static void compiler(P p) {
        Value.T fields, methods, v;
        long nFields, nMethods, nOverrides;
        Type.compile(p.superType);

        fields = Scope.toList(p.fields);
        methods = Scope.toList(p.methods);

        // count the fields & methods
        v = fields;
        nFields = 0;
        while ((v != null)) {
            nFields++;
            v = v.next;
        }
        nMethods = Math.floorDiv(p.methodSize, M3.target.Address.size);
        nOverrides = Math.floorDiv(p.overrideSize, M3.target.Address.size);

        // declare my field and method types
        genMethods(methods, false);
        genOverrides(methods, false);
        genFields(fields, false);

        getOffsets(p, true);

        // declare myself, my fields, and my methods
        // Port: CG.TypeUID is a 32-bit int; the uids come from M3FP.ToInt.
        CG.declare_object((int) Type.globalUID(p), (int) Type.globalUID(p.superType),
                          Brand.toText(p.brand), p.isTraced, nFields,
                          nMethods, nOverrides, p.fieldSize,
                          p.fieldOffset, p.methodOffset);
        genMethods(methods, true);
        genOverrides(methods, true);
        genFields(fields, true);
        // YUCK!  m3gdb assumes the methods are declared first.
    }

    private static void genFields(Value.T fields, boolean declare) {
        while ((fields != null)) {
            if ((declare)) {
                Field.emitDeclaration(fields);
            } else {
                Type.compile(Value.typeOf(fields));
            }
            fields = fields.next;
        }
    }

    private static void genMethods(Value.T methods, boolean declare) {
        Method.Info method = new Method.Info();
        while ((methods != null)) {
            Method.splitX(methods, method);
            if ((method.override)) {
                // skip
            } else if ((declare)) {
                CG.declare_method(method.name, (int) Type.globalUID(method.signature),
                                  method.dfault);
            } else {
                Type.compile(method.signature);
            }
            methods = methods.next;
        }
    }

    private static void genOverrides(Value.T methods, boolean declare) {
        Method.Info method = new Method.Info();
        while ((methods != null)) {
            Method.splitX(methods, method);
            if ((method.override) && (declare)) {
                CG.declare_override(method.name, method.dfault);
            }
            methods = methods.next;
        }
    }

    /**
     * If 'u' is an object type, generate the magic information for the
     * field offsets of 'u' under the name of 't'.
     */
    // EXPORTED:
    public static void noteOffsets(Type.T t, Type.T u) {
        P p = confirm(u);
        if ((p == null)) {
            // not an object
            return;
        }
        getOffsets(p, /* use_magic := */ !Module.isInterface());
        Host.env.note_opaque_magic(Type.globalUID(t),
                                   Type.globalUID(p.superType),
                                   p.fieldSize, p.fieldAlign, p.methodSize);
    }

    /** record a user name for the ref type 't' */
    // EXPORTED:
    public static void noteRefName(Type.T t, String name) {
        P p = confirm(t);
        if ((p != null)) {
            p.user_name = name;
        }
    }

    // EXPORTED:
    public static void initTypecell(Type.T t, long offset, long prev) {
        P p = (P) t;
        Value.T fields = Scope.toList(p.fields);
        long brand = Brand.compile(p.brand);
        long type_map = genTypeMap(p, fields, /* refs_only := */ false);
        long gc_map = genTypeMap(p, fields, /* refs_only := */ true);
        long type_desc = genTypeDesc(p, fields);
        long defaults = genMethodList(p);
        M3CG.Proc initProc = genInitProc(p);
        M3CG.Proc linkProc = genLinkProc(p, defaults);
        long super_id = 0;
        long isz = M3.target.Integer.size;
        long name_offs = 0;
        Fingerprint.T fp = TypeFP.fromType(p);
        M3CG.Var globals = Module.globalData(/* is_const := */ false);
        M3CG.Var consts = Module.globalData(/* is_const := */ true);
        if ((p.superType != null)) {
            super_id = Type.globalUID(p.superType);
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
        CG.init_intt(offset + M3.rt.TC_kind, 8, M3RT.TypeKind.Obj.ordinal(), false);
        CG.init_intt(offset + M3.rt.TC_dataAlignment, 8,
                     Math.floorDiv(p.info.addr_align, Target.Byte), false);
        // ^V See comments in RT0.i3, regarding dataAlignment and dataSize.

        CG.init_intt(offset + M3.rt.TC_dataSize, isz,
                     Math.floorDiv(p.fieldSize, Target.Byte), false);
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

        // OBJECT specific extensions to the typecell
        CG.init_intt(offset + M3.rt.OTC_parentID, isz, super_id, false);
        if ((linkProc != null)) {
            CG.init_proc(offset + M3.rt.OTC_linkProc, linkProc, false);
        }
        CG.init_intt(offset + M3.rt.OTC_methodSize, isz,
                     Math.floorDiv(p.methodSize, Target.Byte), false);
        if ((defaults >= 0)) {
            CG.init_var(offset + M3.rt.OTC_defaultMethods, consts, defaults, false);
        }

        noteOffsets(p, p);
    }

    private static long genTypeMap(P p, Value.T fields, boolean refs_only) {
        // generate my "TypeMap" (used by the garbage collector)
        Field.Info field = new Field.Info();
        TipeMap.start();

        while ((fields != null)) {
            Field.split(fields, field);
            Type.genMap(field.type, field.offset, -1, refs_only);
            fields = fields.next;
        }

        return TipeMap.finish("type map for ", Type.name(p));
    }

    private static long genTypeDesc(P p, Value.T fields) {
        // generate my "TypeDesc" (used by the pickle machinery)
        Field.Info field = new Field.Info();
        long nFields = 0;
        Value.T v = fields;
        if (!p.isTraced) {
            return -1;
        }

        TipeDesc.start();

        if (TipeDesc.addO(TipeDesc.Op.Object, p)) {
            // count the fields
            while ((v != null)) {
                nFields++;
                v = v.next;
            }
            TipeDesc.addI(nFields);

            while ((fields != null)) {
                Field.split(fields, field);
                Type.genDesc(field.type);
                fields = fields.next;
            }
        }

        return TipeDesc.finish("type description for ", Type.name(p));
    }

    private static final long MaxShortMethodList = 32;

    public static final class MethodValue {
        public boolean known;
        public M3CG.Proc proc;
    }

    /** Port: an array of n fresh MethodValue records. */
    private static MethodValue[] newMethodValues(long n) {
        MethodValue[] m = new MethodValue[(int) n];
        for (int i = 0; i < m.length; i++) {
            m[i] = new MethodValue();
        }
        return m;
    }

    private static long genMethodList(P p) {
        long n = methodOffset(p);
        if (n < 0) {
            return -1; // don't know!
        }

        // # of methods
        n = Math.floorDiv(n + p.methodSize, M3.target.Address.size);

        if ((n <= 0)) {
            return -1;
        } else if ((n <= MaxShortMethodList)) {
            return genShortMethodList(p, n);
        } else {
            return genLongMethodList(p, n);
        }
    }

    private static long genShortMethodList(P p, long n_methods) {
        // Port: the local ARRAY [0..MaxShortMethodList-1] OF MethodValue,
        // of which SUBARRAY (methods, 0, n_methods) is passed.
        MethodValue[] methods = newMethodValues(n_methods);
        return doMethodList(p, methods);
    }

    private static long genLongMethodList(P p, long n_methods) {
        MethodValue[] methods = newMethodValues(n_methods);
        return doMethodList(p, methods);
    }

    private static long doMethodList(P p, MethodValue[] m) {
        long offset;
        for (int i = 0; i <= m.length - 1; i++) {
            m[i].known = false;
        }

        if (!fillMethods(p, m)) {
            return -1;
        }

        for (int i = 0; i <= m.length - 1; i++) {
            if (!m[i].known) {
                return -1;
            }
        }

        offset = Module.allocate(m.length * M3.target.Address.size,
                                 M3.target.Address.align, true, "method list");
        for (int i = 0; i <= m.length - 1; i++) {
            // Port: the block variable p of the original shadows the
            // parameter p; Java needs another name.
            M3CG.Proc proc = m[i].proc;
            if (proc != null) {
                CG.init_proc(offset + i * M3.target.Address.size, proc, true);
            }
        }
        return offset;
    }

    private static boolean fillMethods(Type.T t, MethodValue[] m) {
        P p;
        Value.T v;
        Method.Info method = new Method.Info();
        boolean b;
        Value.T[] top = new Value.T[1];
        Type.T[] tVisible = new Type.T[1];
        long m_offset;
        Expr.T expr;
        Value.T[] proc = new Value.T[1];
        int[] addr = TInt.newInt();
        if ((t == null)) {
            return true;
        }

        p = confirm(t);
        if (p == null) {
            return false;
        }

        if (!fillMethods(p.superType, m)) {
            return false;
        }

        // try splitting each of my methods/overrides
        v = Scope.toList(p.methods);
        while ((v != null)) {
            Method.splitX(v, method);
            b = lookUp(p, method.name, top, tVisible);
            AssertError.check(b, "ObjectType");
            m_offset = methodOffset(tVisible[0]);
            if ((m_offset < 0)) {
                return false;
            }
            m_offset = Math.floorDiv(m_offset + method.offset, M3.target.Address.size);
            MethodValue info = m[(int) m_offset];
            expr = Expr.constValue(method.dfault);
            if ((expr == null)) {
                // no method default specified or it is not a constant!
                info.known = false;
                info.proc = null;
            } else if (UserProc.isProcedureLiteral(expr, proc)) {
                // method default is a named procedure
                info.known = true;
                info.proc = Procedure.cgName((Procedure.T) proc[0]);
            } else if (AddressExpr.split(expr, addr) && TInt.EQ(addr, TInt.Zero)) {
                // method default is NIL
                // ************************
                //   The runtime will initialize this slot to a routine that
                //   raises an "undefined method" exception.  If we generate
                //   a constant *read-only* method list, the runtime initialization
                //   will fail!
                // info.known := TRUE;
                // info.proc  := NIL;
                // ************************
                info.known = false;
                info.proc = null;
            } else {
                info.known = false;
                info.proc = null;
            }
            v = v.next;
        }

        return true;
    }

    private static M3CG.Proc genInitProc(P p) {
        Value.T v = Scope.toList(p.fields);
        Field.Info field = new Field.Info();
        CG.Val ptr;
        M3CG.Var obj;
        boolean done = true;
        String name = null;
        M3CG.Proc proc = null;
        long fieldExprAlign;
        // check to see if we need any initialization code
        while ((v != null)) {
            Field.split(v, field);
            if ((field.dfault == null)) {
                if (Type.initCost(field.type, true) != 0) {
                    done = false;
                    break;
                }
            } else if (!Expr.isZeroes(field.dfault)) {
                done = false;
                break;
            }
            v = v.next;
        }
        if ((done)) {
            return null;
        }

        // generate the procedure body

        name = Type.linkName(p, "_INIT");
        CG.comment(-1, false, name + " (ObjectType)");
        Scanner.offset = p.origin;
        CG.gen_location(p.origin);
        proc = CG.declare_procedure(M3ID.add(M3.ids, name), 1, CGType.Void,
                                    0, M3.target.DefaultCall, /* exported := */ false,
                                    /* parent := */ null);
        obj = CG.declare_param(M3ID.NoID, M3.target.Address.size,
                               M3.target.Address.align, CGType.Addr,
                               (int) Type.globalUID(p),
                               /* in_memory := */ false, /* up_level := */ false,
                               /* f := */ CG.Always);
        CG.begin_procedure(proc);

        // allocate and initialize a pointer to the data fields
        CG.load_addr(obj, 0, p.fieldAlign);
        if ((p.fieldOffset >= 0)) {
            // the field offsets are constant
            CG.add_offset(p.fieldOffset);
        } else {
            // the field offsets are unknown
            Type.loadInfo(p, M3.rt.OTC_dataOffset);
            CG.index_bytes(Target.Byte);
        }
        ptr = CG.pop();

        // initialize each of the fields
        v = Scope.toList(p.fields);
        while ((v != null)) {
            Field.split(v, field);
            if ((field.dfault == null)) {
                if (Type.initCost(field.type, true) > 0) {
                    CG.push(ptr);
                    CG.boost_addr_alignment(p.fieldAlign);
                    CG.add_offset(field.offset);
                    Type.initValue(field.type, true);
                }
            } else if (!Expr.isZeroes(field.dfault)) {
                AssignStmt.prepForEmit(field.type, field.dfault, /* initializing := */ true);
                CG.push(ptr);
                CG.boost_addr_alignment(p.fieldAlign);
                CG.add_offset(field.offset);
                fieldExprAlign = CG.GCD(p.fieldAlign, field.offset);
                AssignStmt.doEmit(field.type, field.dfault, fieldExprAlign, /* initializing := */ true);
            }
            v = v.next;
        }

        CG.free(ptr);
        CG.exit_proc(CGType.Void);
        CG.end_procedure(proc);
        return proc;
    }

    private static M3CG.Proc genLinkProc(P p, long defaults) {
        Value.T v = Scope.toList(p.methods);
        Method.Info method = new Method.Info();
        Value.T[] top = new Value.T[1];
        Type.T[] tVisible = new Type.T[1];
        Type.T t_default;
        CG.Val ptr;
        boolean b;
        long m_offset;
        boolean done = true;
        String name = null;
        M3CG.Proc proc = null;
        M3CG.Var defn;
        // check for a statically initialized method list
        if ((defaults >= 0)) {
            return null;
        }

        // check to see if we need any setup code
        while ((v != null)) {
            Method.splitX(v, method);
            if ((method.dfault != null)) {
                done = false;
                break;
            }
            v = v.next;
        }
        if ((done)) {
            return null;
        }

        Type.genTag(p, "link-time setup code for ", -1);


        // get a pointer to my default method list
        name = Type.linkName(p, "_LINK");
        CG.comment(-1, false, name);
        Scanner.offset = p.origin;
        CG.gen_location(p.origin);
        proc = CG.declare_procedure(M3ID.add(M3.ids, name), 1, CGType.Void,
                                    0, M3.target.DefaultCall, /* exported := */ false,
                                    /* parent := */ null);
        defn = CG.declare_param(M3ID.NoID, M3.target.Address.size,
                                M3.target.Address.align, CGType.Addr,
                                (int) Type.globalUID(Addr.T),
                                /* in_memory := */ false, /* up_level := */ false,
                                /* f := */ CG.Always);
        CG.begin_procedure(proc);

        // grab the default methodlist pointer
        CG.load_addr(defn, 0, M3.target.Address.align);
        CG.load_indirect(CGType.Addr, M3.rt.OTC_defaultMethods, M3.target.Address.size);
        ptr = CG.pop();

        v = Scope.toList(p.methods);
        while ((v != null)) {
            Method.splitX(v, method);
            if ((method.dfault != null)) {
                t_default = Expr.typeOf(method.dfault);
                b = lookUp(p, method.name, top, tVisible);
                AssertError.check(b, "ObjectType");

                AssignStmt.prepForEmit(t_default, method.dfault, /* initializing := */ true);
                CG.push(ptr);
                CG.boost_addr_alignment(M3.target.Address.align);
                CG.add_offset(method.offset);
                m_offset = methodOffset(tVisible[0]);
                if ((m_offset >= 0)) {
                    CG.add_offset(m_offset);
                } else {
                    Type.loadInfo(tVisible[0], M3.rt.OTC_methodOffset);
                    CG.index_bytes(Target.Byte);
                }
                CG.boost_addr_alignment(M3.target.Address.align);
                AssignStmt.doEmit(t_default, method.dfault, M3.target.Address.align,
                                  /* initializing := */ true);
            }
            v = v.next;
        }

        CG.free(ptr);
        CG.exit_proc(CGType.Void);
        CG.end_procedure(proc);
        return proc;
    }

    // Externally dispatched-to.
    private static boolean equalChk(P a, Type.T t, Type.Assumption x) {
        // Note: it is important to do the surface syntax checks before
        // checking the types of the fields and methods.  Otherwise, we will
        // add "a = t" to the current set of assumptions and may decide that
        // other types are equal when they are not!
        P b = (P) t;
        Value.T fa, fb, ma, mb;
        if ((a == null)
            || (a.isTraced != b.isTraced)
            || !Brand.equal(a.brand, b.brand)) {
            return false;
        }

        fa = Scope.toList(a.fields);
        fb = Scope.toList(b.fields);

        // check the field names and offsets
        if (!Field.isEqualList(fa, fb, x, /* types := */ false)) {
            return false;
        }

        ma = Scope.toList(a.methods);
        mb = Scope.toList(b.methods);

        // check the method names and offsets
        if (!Method.isEqualList(ma, mb, x, /* types := */ false)) {
            return false;
        }

        // check the super types
        if (!Type.isEqual(a.superType, b.superType, x)) {
            return false;
        }

        // check the field types and default values
        if (!Field.isEqualList(fa, fb, x, /* types := */ true)) {
            return false;
        }

        // check the method types and defaults
        if (!Method.isEqualList(ma, mb, x, /* types := */ true)) {
            return false;
        }

        return true;
    }

    // Externally dispatched-to.
    private static boolean subtyper(P a, Type.T t) {
        Type.T root = Reff.T;
        if ((!a.isTraced)) {
            root = Addr.T;
        }
        if (Type.isEqual(t, root, null)) {
            return true;
        }
        return Type.isEqual(a, t, null)
            || ((a.superType != null) && Type.isSubtype(a.superType, t));
    }

    // Externally dispatched-to.
    private static long initCoster(P p, boolean zeroed) {
        if ((zeroed)) {
            return 0;
        } else {
            return 1;
        }
    }

    // Externally dispatched-to.
    private static void fPrinter(P p, M3.FPInfo x) {
        Value.T v;
        long n;
        if (Type.isEqual(p, ObjectRef.T, null)) {
            x.tag = "$objectref";
            x.n_nodes = 0;
        } else if (Type.isEqual(p, ObjectAdr.T, null)) {
            x.tag = "$objectadr";
            x.n_nodes = 0;
        } else {
            M3Buf.putText(x.buf, "OBJECT");
            if ((!p.isTraced)) {
                M3Buf.putText(x.buf, "-UNTRACED");
            }
            Brand.genFPrint(p.brand, x);

            // count the children
            n = 1; // for supertype
            v = Scope.toList(p.fields);
            while ((v != null)) {
                n += Value.addFPTag(v, x);
                v = v.next;
            }
            v = Scope.toList(p.methods);
            if ((v != null)) {
                M3Buf.putText(x.buf, " METHODS");
                while ((v != null)) {
                    n += Value.addFPTag(v, x);
                    v = v.next;
                }
            }
            x.n_nodes = n;

            // add the children
            if ((n <= x.nodes.length)) {
                x.nodes[0] = p.superType;
                n = 1;
            } else {
                x.others = new Type.T[(int) n];
                x.others[0] = p.superType;
                n = 1;
            }
            v = Scope.toList(p.fields);
            while ((v != null)) {
                n = Value.addFPEdges(v, x, n);
                v = v.next;
            }
            v = Scope.toList(p.methods);
            while ((v != null)) {
                n = Value.addFPEdges(v, x, n);
                v = v.next;
            }
        }
    }

    /**
     * Returns the bit offset of the methods defined in object type t if
     * all of its parents are visible, otherwise returns -1
     */
    // EXPORTED:
    public static long methodOffset(Type.T t) {
        P p = confirm(t);
        if ((p == null)) {
            return Unknown_w_magic;
        }
        getOffsets(p, /* use_magic := */ true);
        return p.methodOffset;
    }

    /**
     * Sets 'align' to the alignment of 't's fields.  If all the parents of 't'
     * are visible, sets 'offset' to the bit offset of 't's fields,
     * otherwise sets 'offset' to -1.
     */
    // EXPORTED:
    public static void getFieldsOffsetAndAlign(Type.T t, long[] offset, long[] align) {
        P p = confirm(t);
        if ((p == null)) {
            offset[0] = Unknown_w_magic;
            align[0] = Target.Byte;
        } else {
            getOffsets(p, /* use_magic := */ true);
            offset[0] = p.fieldOffset;
            align[0] = p.fieldAlign;
        }
    }

    /** Return the alignment of 't's fields. */
    // EXPORTED:
    public static long fieldAlignment(Type.T t) {
        P p = confirm(t);
        if ((p == null)) {
            return Target.Byte;
        }
        getSizes(p);
        return p.fieldAlign;
    }

    // ********
    // PROCEDURE FieldSize (t: Type.T): INTEGER =
    //   VAR p := Confirm (t);
    //   BEGIN
    //     IF (p = NIL) THEN RETURN Unknown_w_magic END;
    //     GetOffsets (p, use_magic := TRUE);
    //     IF (p.fieldOffset < 0) THEN RETURN Unknown_w_magic END;
    //     RETURN RecordType.RoundUp (p.fieldOffset + p.fieldSize, p.fieldAlign);
    //   END FieldSize;
    // **********

    private static void getSizes(P p) {
        long[] min_size = new long[1];
        boolean[] solid = new boolean[1];
        if ((p.fieldSize >= 0)) {
            // already done
            return;
        }

        if ((p.superType == null)) { // p is ROOT or UNTRACED ROOT
            p.fieldSize = 0;
            p.fieldAlign = M3.target.Address.align;
        } else {
            // compute the field sizes and alignments
            // Port: the OUT parameters p.fieldSize and p.fieldAlign are
            // one-element arrays, copied back after the call.
            long[] fieldSize = { p.fieldSize };
            long[] fieldAlign = { p.fieldAlign };
            RecordType.sizeAndAlignment(
                p.fields, p.info.lazyAligned, /*OUT*/ min_size, /*OUT*/ fieldSize,
                /*OUT*/ fieldAlign, /*OUT*/ solid
            );
            p.fieldSize = fieldSize[0];
            p.fieldAlign = fieldAlign[0];
            p.info.addr_align = Math.max(p.info.addr_align, p.fieldAlign);

            // round the object's size up to at least the size of a heap header
            p.fieldSize = RecordType.roundUp(p.fieldSize, M3.target.Address.size);
        }
    }

    private static void getOffsets(P p, boolean use_magic) {
        P super_;
        long[] d_size = new long[1];
        long[] m_size = new long[1];
        if ((p.fieldOffset >= 0)) {
            // already done
            return;
        } else if ((p.fieldOffset == Unchecked_offset)) {
            // we haven't tried yet
        } else if ((p.fieldOffset == Unknown_w_magic)) {
            // we've tried everything already
            return;
        } else if ((!use_magic)) {
            // we've already tried it without magic
            return;
        }

        getSizes(p);

        if ((p.superType == null)) { // p is ROOT or UNTRACED ROOT
            p.fieldOffset = M3.target.Address.size;
            p.methodOffset = 0;
        } else {
            if ((use_magic)) {
                p.fieldOffset = Unknown_w_magic;
                p.methodOffset = Unknown_w_magic;
            } else {
                p.fieldOffset = Unknown_wo_magic;
                p.methodOffset = Unknown_wo_magic;
            }

            // try to get my supertype's offset
            super_ = confirm(p.superType);
            if ((super_ != null)) { // supertype is visible
                getOffsets(super_, use_magic);
                if ((super_.fieldOffset >= 0)) {
                    p.fieldOffset = super_.fieldOffset + super_.fieldSize;
                    p.fieldOffset = RecordType.roundUp(p.fieldOffset, p.fieldAlign);
                    p.methodOffset = super_.methodOffset + super_.methodSize;
                }
            } else if ((use_magic)
                       && findMagic(Type.globalUID(p.superType), d_size, m_size)) {
                p.fieldOffset = RecordType.roundUp(d_size[0], p.fieldAlign);
                p.methodOffset = m_size[0];
            }
        }
    }

    private static boolean findMagic(long t_id, long[] d_size, long[] m_size) {
        long[] super_ = new long[1];
        long[] my_d_size = new long[1];
        long[] my_d_align = new long[1];
        long[] my_m_size = new long[1];
        long[] s_d_size = new long[1];
        long[] s_m_size = new long[1];
        if ((NIL_ID == 0)) {
            NIL_ID = Type.globalUID(null);
            ROOT_ID = Type.globalUID(ObjectRef.T);
            UROOT_ID = Type.globalUID(ObjectAdr.T);
        }

        if ((t_id == NIL_ID) || (t_id == ROOT_ID) || (t_id == UROOT_ID)) {
            d_size[0] = M3.target.Address.size;
            m_size[0] = 0;
            return true;

        } else if (!Host.env.find_opaque_magic(t_id, super_, my_d_size,
                                               my_d_align, my_m_size)) {
            return false;

        } else if (!findMagic(super_[0], s_d_size, s_m_size)) {
            return false;

        } else { // we did it!
            d_size[0] = s_d_size[0] + my_d_size[0];
            d_size[0] = RecordType.roundUp(d_size[0], my_d_align[0]);
            m_size[0] = s_m_size[0] + my_m_size[0];
            return true;

        }
    }

    private static P confirm(Type.T t) {
        Type.Info info = new Type.Info();
        t = Type.stripPacked(t);
        while (true) {
            t = Type.checkInfo(t, info);
            if ((info.class_ == Type.Class.Object)) {
                return (P) t;
            } else if ((info.class_ == Type.Class.Opaque)) {
                t = Revelation.lookUp(t);
            } else {
                return null;
            }
        }
    }
}
