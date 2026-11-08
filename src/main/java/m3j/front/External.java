// Java port of CM3's m3-sys/m3front/src/values/External.i3 and External.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

// File: External.i3
// Last Modified On Fri Jul  1 08:11:44 PDT 1994 By kalsow

// File: External.m3
// Last Modified On Wed Mar  1 08:42:34 PST 1995 By kalsow

public final class External {
    private External() {}

    // Port: TK = Token.T.

    /** Port: the revelation BRANDED "Import.Set" REF RECORD ... END. */
    public static class Set {
        public Port exports;
        public Port imports;
        public T importObjs;
        public T last_obj;
    }

    public static final class Port {
        public Port next;
        public Module.T module;
        public long origin;
        public T source;
        public int name;
        public boolean direct;
        public boolean export;
    }

    public static final class T extends Value.T {
        /** Port: hides M3.ValueNode.next, as in the original. */
        public T next;
        public Value.T obj;
        public Port home;

        @Override
        public void typeCheck(M3.CheckState cs) {
            check(this, cs);
        }

        @Override
        public void load() {
            External.load(this);
        }

        @Override
        public boolean declare() {
            return External.declare(this);
        }

        @Override
        public void const_init() {
            constInit(this);
        }

        @Override
        public boolean need_init() {
            return needInit(this);
        }

        @Override
        public void lang_init() {
            langInit(this);
        }

        @Override
        public void user_init() {
            userInit(this);
        }

        @Override
        public Expr.T toExpr() {
            return External.toExpr(this);
        }

        @Override
        public Type.T toType() {
            return External.toType(this);
        }

        @Override
        public Type.T typeOf() {
            return External.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return External.repTypeOf(this);
        }

        @Override
        public Value.T base() {
            return External.base(this);
        }

        // Port: the override set_globals := ValueRep.NoInit restates the
        // inherited method; add_fp_tag and fp_type stay NIL.
    }

    public static Set newSet() {
        Set s = new Set();
        s.exports    = null;
        s.imports    = null;
        s.importObjs = null;
        s.last_obj   = null;
        return s;
    }

    public static void noteExport(Set s, int name) {
        Module.T ex;
        Port p;
        ex = Module.lookUp(name, /* internal := */ false);
        if ((ex == null)) {
            return;
        }
        // Port: s.exports is a VAR parameter of Push.
        Port[] list = {s.exports};
        p = push(list, ex, name);
        s.exports = list[0];
        p.direct = true;
        p.export = true;
    }

    public static void noteImport(Set s, Module.T im, int name) {
        Port p;
        if ((im == null)) {
            return;
        }
        // Port: s.imports is a VAR parameter of Push.
        Port[] list = {s.imports};
        p = push(list, im, name);
        s.imports = list[0];
        p.source = importObj(s, im, name, Scanner.cur.offset, p);
        p.direct = true;
    }

    public static void parseImports(Set s, Module.T self) {
        Module.T[] runtime = new Module.T[1];
        int[] name = new int[1];
        // parse the "magic" runtime import
        RunTyme.import_();
        RunTyme.bind(self, runtime, name);
        if ((runtime[0] != null)) {
            noteImport(s, runtime[0], name[0]);
            s.last_obj.used = true; // so the user doesn't get any warnings
        }

        // parse the explicit imports
        while (true) {
            if ((Scanner.cur.token == Token.T.tIMPORT)) {
                parseImport(s);
            } else if ((Scanner.cur.token == Token.T.tFROM)) {
                parseFromImport(s);
            } else {
                break;
            }
        }

        resolveImports(s, self);
    }

    private static void parseImport(Set s) {
        int id, alias;
        Module.T im;
        Scanner.match(Token.T.tIMPORT);
        while (true) {
            id = Scanner.matchID();
            alias = id;

            if ((Scanner.cur.token == Token.T.tAS)) {
                Scanner.getToken(); // AS
                alias = Scanner.matchID();
            }

            im = Module.lookUp(id, /* internal := */ false);
            noteImport(s, im, alias);

            if ((Scanner.cur.token != Token.T.tCOMMA)) {
                break;
            }
            Scanner.getToken(); // ,
        }
        Scanner.match(Token.T.tSEMI);
    }

    private static void parseFromImport(Set s) {
        int id;
        long j, n;
        Port p;
        Scanner.match(Token.T.tFROM);
        id = Scanner.matchID();
        Scanner.match(Token.T.tIMPORT);
        n = Ident.parseList();
        Scanner.match(Token.T.tSEMI);

        // Port: s.imports is a VAR parameter of Push.
        Port[] list = {s.imports};
        p = push(list, null, id);
        s.imports = list[0];

        j = Ident.top - n;
        for (long i = 0; i <= n - 1; i++) {
            importObj(s, null, Ident.stack[(int) (j + i)], Ident.offset[(int) (j + i)], p);
        }
        Ident.top -= n;
    }

    /** Port: list is a VAR parameter, a one-element array. */
    private static Port push(Port[] list, Module.T m, int name) {
        Port p;
        // search for a match
        p = list[0];
        while ((p != null)) {
            if ((p.name == name)) {
                if ((m == null) || (p.module == m)) {
                    // ok
                } else if ((p.module == null)) {
                    p.module = m;
                } else {
                    Error.ID(name, "inconsistent imports");
                }
                return p;
            }
            p = p.next;
        }

        // build a new entry
        p = new Port();
        p.next   = list[0];
        list[0] = p;
        p.module = m;
        p.name   = name;
        p.origin = Scanner.offset;
        p.source = null;
        p.direct = false;
        p.export = false;
        return p;
    }

    private static T importObj(Set s, Value.T obj, int name, long offset, Port port) {
        T t = new T();
        Value.Class c = Value.Class.Error;
        if ((s == null)) {
            return null;
        }
        if ((obj != null)) {
            c = obj.class_;
        }
        ValueRep.init(t, name, c);
        t.origin   = offset;
        t.next     = null;
        t.obj      = obj;
        t.home     = port;
        t.imported = true;
        t.exported = false;
        if ((port.export)) {
            t.exportable = true;
        }
        if ((s.importObjs == null)) {
            s.importObjs = t;
        } else {
            s.last_obj.next = t;
        }
        s.last_obj = t;
        return t;
    }

    private static void resolveImports(Set s, Module.T self) {
        Port p;
        T t;
        Module.T m;
        Value.T v;
        Scope.T syms;
        long save;
        save = Scanner.offset;

        // import the exported symbols
        p = s.exports;
        while ((p != null)) {
            m = p.module;
            if ((m != null) && (m != self)) {
                v = Scope.toList(Module.exportScope(m));
                while ((v != null)) {
                    importObj(s, v, v.name, p.origin, p);
                    v = v.next;
                }
            }
            p = p.next;
        }

        // resolve the deferred "FROM x IMPORT" modules
        p = s.imports;
        while ((p != null)) {
            if ((p.module == null)) {
                Scanner.offset = p.origin;
                p.module = lookUpInList(p.name, s.imports);
            }
            p = p.next;
        }

        // resolve the deferred "FROM x IMPORT y" imports
        t = s.importObjs;
        while ((t != null)) {
            if ((t.obj == null)) {
                // this item is from a "FROM x IMPORT" => look up that was deferred
                Scanner.offset = t.origin;
                p = t.home;
                if ((p.source != null)) {
                    p.source.used = true;
                }
                syms = Module.exportScope(p.module);
                if ((syms != null)) {
                    v = Scope.lookUp(syms, t.name, true);
                } else {
                    v = null; // probably a circular import!
                }
                if ((v != null)) {
                    t.obj   = v;
                    t.class_ = v.class_;
                } else {
                    M3.QID q = new M3.QID();
                    q.module = p.name;
                    q.item = t.name;
                    Error.QID(q, "symbol not exported");
                }
            }
            t = t.next;
        }

        Scanner.offset = save;
    }

    private static Module.T lookUpInList(int name, Port local) {
        while ((local != null)) {
            if ((local.name == name) && (local.module != null)) {
                return local.module;
            }
            local = local.next;
        }
        return Module.lookUp(name, /* internal := */ false);
    }

    public static void loadImports(Set s, Module.T self) {
        Port p;
        T t;
        Module.T m;
        long save;
        save = Scanner.offset;

        // load the imported symbols
        t = s.importObjs;
        while ((t != null)) {
            Scanner.offset = t.origin;
            if ((t.obj != null)) {
                Scope.insert(t);
            }
            t = t.next;
        }

        // get the revelations in imported interfaces
        p = s.imports;
        while ((p != null)) {
            if ((p.direct)) {
                m = p.module;
                Scanner.offset = p.origin;
                if ((m != null) && (m != self)) {
                    Module.importRevelations(m, p.source);
                }
            }
            p = p.next;
        }

        // get the revelations in exported interfaces
        p = s.exports;
        while ((p != null)) {
            if ((p.direct)) {
                m = p.module;
                Scanner.offset = p.origin;
                if ((m != null) && (m != self)) {
                    Module.importRevelations(m, p.source);
                }
            }
            p = p.next;
        }

        Scanner.offset = save;
    }

    public static boolean isExportable(Value.T v) {
        if (v == null) {
            return false;
        } else if (v instanceof T t) {
            return t.home.export;
        } else {
            return false;
        }
    }

    public static void redirect(Value.T intf, Value.T impl) {
        T t = (T) intf;
        t.obj = impl;
    }

    public static void genLinkInfo(Set s) {
        genInitLinks(s.exports, false, CG::export_unit);
        genInitLinks(s.imports, true,  CG::import_unit);
    }

    /** Port: the procedure type PROCEDURE (n: CG.Name). */
    public interface Note { void apply(int n); }

    private static void genInitLinks(Port p, boolean imported, Note note) {
        Port x, y;
        x = p;
        while ((x != null)) {
            y = p;
            while (true) {
                if ((x == y)) {
                    Module.importInterface(x.module);
                    Host.env.note_interface_use(x.module.name, imported);
                    note.apply(x.module.name);
                    break;
                }
                if ((x.module == y.module)) {
                    // duplicate
                    break;
                }
                y = y.next;
            }
            x = x.next;
        }
    }

    public static void genImports(Set s) {
        Port p;
        p = s.imports;
        while ((p != null)) {
            Scope.enter(Module.exportScope(p.module));
            p = p.next;
        }
    }

    //---------------------------------------------------------------------------

    public static void initGlobals(Set s) {
        Port x = s.exports;
        while ((x != null)) {
            initExports(x.module);
            x = x.next;
        }
    }

    private static void initExports(Module.T interface_) {
        Value.T o;
        o = Scope.toList(Module.exportScope(interface_));
        while ((o != null)) {
            if ((o.exported) && (Value.classOf(o) == Value.Class.Var)) {
                Variable.initGlobal((Variable.T) o);
            }
            o = o.next;
        }
    }

    public static void visit(Set s, Module.Visitor v) {
        Port p = s.exports;
        while ((p != null)) {
            v.apply(p.module);
            p = p.next;
        }
        p = s.imports;
        while ((p != null)) {
            v.apply(p.module);
            p = p.next;
        }
    }

    //---------------------------------------------------------------------------

    private static void check(T t, M3.CheckState cs) {
        Value.typeCheck(t.obj, cs);
    }

    private static void load(T t) {
        Value.load(t.obj);
    }

    private static boolean declare(T t) {
        boolean i, e, u;
        Value.T o;
        o = t.obj;
        if ((o != null)) {
            i = o.imported;
            e = o.exported;
            u = o.used;
            o.imported = t.imported;
            o.exported = t.exported;
            o.used = t.used;
            Value.declare(t.obj);
            o.imported = i;
            o.exported = e;
            o.used = u;
        }
        return false;
    }

    private static void constInit(T t) {
        Value.constInit(t.obj);
    }

    private static boolean needInit(T t) {
        return Value.needsInit(t.obj);
    }

    private static void langInit(T t) {
        Value.langInit(t.obj);
    }

    private static void userInit(T t) {
        Value.userInit(t.obj);
    }

    private static Expr.T toExpr(T t) {
        return Value.toExpr(t.obj);
    }

    private static Type.T toType(T t) {
        return Value.toType(t.obj);
    }

    private static Type.T typeOf(T t) {
        return Value.typeOf(t.obj);
    }

    private static Type.T repTypeOf(T t) {
        return Value.repTypeOf(t.obj);
    }

    private static Value.T base(T t) {
        return Value.base(t.obj);
    }
}
