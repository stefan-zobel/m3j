// Java port of CM3's m3-sys/m3front/src/values/Module.i3 and Module.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.io.InputStream;
import m3j.middle.M3Buf;
import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: Module.def
// Last modified on Mon Aug 29 09:53:53 PDT 1994 by kalsow
//      modified on Sat Mar 16 01:55:39 1991 by muller

// File: Module.m3
// Last modified on Wed Apr 12 08:36:02 PDT 1995 by kalsow
//      modified on Tue May 25 10:53:07 PDT 1993 by muller

public final class Module {
    private Module() {}

    public static final class DataSeg {
        public long size;
        public M3CG.Var seg;

        DataSeg() {}
    }

    /**
     * Port: Module.T with the fields and methods of its revelation in
     * Module.m3. The fields are private, as the revelation is. Like the
     * original, the fields external and lazyAligned hide the fields of the
     * same names of Value.T; code outside Module that means the fields of
     * Value.T must use a Value.T reference.
     */
    public static class T extends Value.T {
        public boolean safe;
        public boolean interface_;
        public boolean external;
        /** Port, addition: the class of <*EXTERNAL "class":JAVA*> INTERFACE, or null. */
        public String javaClass;
        public boolean has_errors;
        public int genericBase;
        public String genericFile;
        public External.Set externals;
        public Scope.T importScope;
        public Scope.T localScope;
        public Revelation.Set revelations;
        public Stmt.T block;
        public InitBody body;
        public char[] counter = new char[5];
        public ESet.T fails;
        public long body_origin;
        public long visit_age;
        public long compile_age;
        /** Port: ARRAY BOOLEAN (*const*) OF DataSeg, indexed by ord (is_const). */
        public DataSeg[] globals = { new DataSeg(), new DataSeg() };
        public long import_offs;
        public long last_import;
        public String data_name;
        public Tracer.T trace;
        public Type.ModuleInfo type_info;
        public Value.T value_info;
        public boolean lazyAligned;
        public boolean containsLazyAlignments;
        public M3CG.Var jmpbuf_size = null;
        public M3CG.Proc alloca = null;
        public M3CG.Proc setjmp = null;
        public Jmpbufs.Proc jmpbufs;

        @Override
        public void typeCheck(M3.CheckState cs) {
            typeCheckMethod(this, cs);
        }

        @Override
        public Type.T typeOf() {
            return ValueRep.typeVoid(this);
        }

        @Override
        public Type.T repTypeOf() {
            return ValueRep.typeVoid(this);
        }

        @Override
        public long add_fp_tag(M3.FPInfo x) {
            return addFPTag(this, x);
        }

        @Override
        public Type.T fp_type() {
            return fpType(this);
        }

        // Port: the overrides set_globals := ValueRep.NoInit, load :=
        // ValueRep.NoLoader, declare := ValueRep.Never, const_init :=
        // ValueRep.NoInit, need_init := ValueRep.Never, lang_init :=
        // ValueRep.NoInit, user_init := ValueRep.NoInit, toExpr :=
        // ValueRep.NoExpr, toType := ValueRep.NoType and base :=
        // ValueRep.Self restate the inherited methods.
    }

    public static final class InitBody extends ProcBody.T {
        public T self;
        public M3CG.Var arg = null;

        @Override
        public void gen_decl() {
            emitDecl(this);
        }

        @Override
        public void gen_body() {
            emitBody(this);
        }
    }

    // Port: TK = Token.T.

    /** Port: ORD (b), the index of an ARRAY BOOLEAN OF ... */
    private static int ord(boolean b) {
        return b ? 1 : 0;
    }

    public static final class Builtin {
        public int name;
        public T t;

        Builtin() {}
    }

    // VAR (* CONST *)
    private static long n_builtins = 0;
    private static final Builtin[] builtins = newBuiltins();

    private static Builtin[] newBuiltins() {
        Builtin[] a = new Builtin[11];
        for (int i = 0; i < a.length; i++) {
            a[i] = new Builtin();
        }
        return a;
    }

    private static T curModule = null;
    private static int[] parseStack = new int[201];
    private static long parseDepth = 0;
    /** used to compose error messages */
    private static M3Buf.T error_buf = null;
    private static long visit_age = 0;
    private static Visitor visit_proc = null;
    private static long compile_age = 0;
    /** M3.Add ("RT0__ModulePtr") */
    private static int m3id_RT0_ModulePtr = 0;

    /** special CG.TypeUID used for all interface records */
    private static final int ModuleTypeUID = -1;

    private static final char[] InitialCounter = { '_', '0', '0', '0', '0' };

    // Port: indexed by ord (t.interface).
    private static final String[] GlobalDataPrefix = { "M_", "I_" };
    private static final String[] MainBodySuffix = { "_M3", "_I3" };
    private static final String[] UnitKindRW = { "MODULE ", "INTERFACE " };
    private static final String[] UnitKindText = { "module", "interface" };
    private static final String[] UnitKindSuffix = { ".mg", ".ig" };

    /** Port: Wr.EOL, the line separator of the host. */
    private static final String EOL = System.lineSeparator();

    private static int m3id_RT0_ModulePtr() {
        int id = m3id_RT0_ModulePtr;
        if (id == 0) {
            id = M3ID.add(M3.ids, "RT0__ModulePtr");
            m3id_RT0_ModulePtr = id;
        }
        return id;
    }

    public static void reset() {
        curModule = null;
        parseDepth = 0;
        compile_age++;
    }

    /**
     * Exception handling support, using setjmp/longjmp, without front/middle-end
     * knowing jmpbuf size -- use alloca at runtime getting size from
     * constant initialized in C; these functions are in Module
     * to accomodate hypothetical multi-threaded m3front,
     * i.e. instead of having globals initialized on-demand in Jmpbufs.
     * i.e. Module compilation still assumed single threaded, but
     * possibly multiple Modules compiled on separate threads.
     */
    public static M3CG.Proc getAlloca(Module.T t) {
        boolean[] new_ = {false};
        // alloca must be special cased by backends to mean
        //   alloca, _alloca, chkstk, etc.
        if (t.alloca == null) {
            t.alloca = CG.import_procedure(M3ID.add(M3.ids, "alloca"), 1, CGType.Addr,
                                           M3.target.DefaultCall, new_);
            if (new_[0]) {
                CG.declare_param(M3ID.NoID, M3.target.Word.size, M3.target.Word.align,
                                 M3.target.Word.cg_type, 0, /* in_memory := */ false,
                                 /* up_level := */ false, /* f := */ CG.Never);
            }
        }
        return t.alloca;
    }


    public static M3CG.Var getJmpbufSize(Module.T t) {
        // m3_jmpbuf_size is a "constant variable" initialized in
        //   C via:
        //      #include <setjmp.h>
        //      typedef const INTEGER const_INTEGER;
        //      extern const_INTEGER m3_jmpbuf_size = sizeof(jmp_buf);
        //   As an optimization, and to avoid any matters involving dynamically
        //   importing data on Win32, Uconstants is always statically linked.
        //
        //   This isolates the front/middle end from the target.
        if (t.jmpbuf_size == null) {
            t.jmpbuf_size = CG.import_global(M3ID.add(M3.ids, "m3_jmpbuf_size"),
                                             M3.target.Word.size, M3.target.Word.align,
                                             M3.target.Word.cg_type, 0, M3ID.add(M3.ids, "const_INTEGER"));
        }
        return t.jmpbuf_size;
    }

    public static M3CG.Proc getSetjmp(Module.T t) {
        boolean[] new_ = {false};
        //    int setjmp(void* );
        // or int sigsetjmp(void*, int);
        if (t.setjmp == null) {
            t.setjmp = CG.import_procedure(M3ID.add(M3.ids, M3.target.Setjmp), 1 + ord(M3.target.Sigsetjmp),
                                           M3.target.Integer.cg_type,
                                           M3.target.DefaultCall, new_);
            if (new_[0]) {
                CG.declare_param(M3ID.add(M3.ids, "jmpbuf"), M3.target.Address.size,
                                 M3.target.Address.align, CGType.Addr, 0,
                                 /* in_memory := */ false, /* up_level := */ false,
                                 /* f := */ CG.Never);
                if (M3.target.Sigsetjmp) {
                    CG.declare_param(M3ID.add(M3.ids, "save_mask"), Target.Int32.size, // int
                                     Target.Int32.align, CGType.Int32, 0,
                                     /* in_memory := */ false, /* up_level := */ false,
                                     /* f := */ CG.Never);
                }
            }
        }
        return t.setjmp;
    }

    private static T create(int name) {
        T t;
        t = new T();
        ValueRep.init(t, name, Value.Class.Module);
        t.readonly = true;
        t.safe = true;
        t.interface_ = true;
        t.external = false;
        t.has_errors = false;
        t.genericBase = M3ID.NoID;
        t.genericFile = null;
        t.externals = External.newSet();
        t.importScope = null;
        t.localScope = null;
        t.block = null;
        t.body = null;
        t.revelations = Revelation.newSet(t);
        t.fails = null;
        t.data_name = null;
        t.body_origin = Scanner.offset;
        t.visit_age = 0;
        t.compile_age = compile_age;
        t.globals[ord(false)].size = 0;
        t.globals[ord(false)].seg = null;
        t.globals[ord(true)].size = 0;
        t.globals[ord(true)].seg = null;
        t.import_offs = -1;
        t.last_import = 0;
        t.trace = null;
        t.type_info = null;
        t.value_info = null;
        t.counter = InitialCounter.clone();
        t.lazyAligned = false;
        t.containsLazyAlignments = false;
        return t;
    }

    private static T switch_(T new_) {
        T old = curModule;
        Type.ModuleInfo old_types = null;
        Value.T old_values = null;
        Type.ModuleInfo new_types = null;
        Value.T new_values = null;
        if ((new_ != null)) {
            new_types = new_.type_info;
            new_values = new_.value_info;
        }
        old_types = Type.setModule(new_types);
        old_values = Value.setModule(new_values);
        if ((old != null)) {
            old.type_info = old_types;
            old.value_info = old_values;
        }
        curModule = new_;
        return old;
    }

    public static Module.T newDefn(String name, boolean safe, Scope.T syms) {
        T save, t;
        Scope.T zz;
        Revelation.Set yy;
        t = create(M3ID.add(M3.ids, name));
        {
            Builtin z = builtins[(int) n_builtins];
            z.name = t.name;
            z.t = t;
        }
        n_builtins++;
        t.safe = safe;
        save = switch_(t);
        yy = Revelation.push(t.revelations);
        zz = Scope.push(Scope.Initial);
        t.importScope = Scope.pushNew(true, M3ID.NoID, /* module := */ true);
        if ((syms != null)) {
            t.localScope = syms;
        } else {
            t.localScope = Scope.pushNew(true, t.name, /* module := */ true);
            Scope.popNew();
        }
        Scope.popNew();
        Scope.pop(zz);
        Revelation.pop(yy);
        recordInterface(t);
        switch_(save);
        return t;
    }

    public static Module.T parse(boolean interfaceOnly) {
        T t, save;
        int[] id = new int[1];
        long n;
        InputStream[] genericReader = {null};
        Revelation.Set yy;
        boolean topLevel = !interfaceOnly;
        long[] n_errs = new long[1], n_warns = new long[1], n_initial_errs = new long[1];
        Target.CallingConvention[] cc = new Target.CallingConvention[1];
        boolean[] got_cc = new boolean[1];
        boolean[] isJava = new boolean[1];
        // ETimer.Push (M3Timers.parse);
        Error.count(n_initial_errs, n_warns);

        t = create(M3ID.NoID);
        yy = Revelation.push(t.revelations);
        save = switch_(t);

        if ((Scanner.cur.token == Token.T.tEXTERNAL)) {
            Decl.parseExternalPragma(id, cc, got_cc, isJava);
            if (isJava[0]) {
                t.javaClass = M3ID.toText(M3.ids, id[0]);
            } else if ((id[0] != M3ID.NoID)) {
                Error.ID(id[0], "external module name ignored");
            }
            t.external = true;
        }

        if ((Scanner.cur.token == Token.T.tUNSAFE)) {
            t.safe = false;
            Scanner.getToken();
        }

        t.interface_ = (Scanner.cur.token == Token.T.tINTERFACE);
        if (interfaceOnly) {
            if ((Scanner.cur.token == Token.T.tINTERFACE)) {
                Scanner.getToken();
            } else {
                Scanner.fail("missing INTERFACE keyword");
            }
            t.interface_ = true;
        } else if ((Scanner.cur.token == Token.T.tINTERFACE) || (Scanner.cur.token == Token.T.tMODULE)) {
            Scanner.getToken();
        } else {
            Scanner.fail("missing INTERFACE or MODULE keyword");
        }

        if (t.external && !t.interface_) {
            Error.msg("Only interfaces can be <*EXTERNAL*>");
        }

        id[0] = Scanner.matchID();
        t.name = id[0];

        if ((t.interface_)) {
            recordInterface(t);
            if ((topLevel)) {
                pushInterface(id[0]);
                parseDepth++;
            }
        }

        n = 0; // In case we don't parse any export names.
        if ((Scanner.cur.token == Token.T.tEXPORTS)) {
            if ((t.interface_)) {
                Error.msg("EXPORTS clause not allowed in an interface");
                t.interface_ = false;
            }
            Scanner.getToken();
            n = Ident.parseList();
            // Leave the export names on the Ident stack for now.
        } else if ((!t.interface_)) {
            External.noteExport(t.externals, t.name);
        }

        if ((Scanner.cur.token == Token.T.tSEMI)) {
            // this is a simple module/interface, just fall through
            Scanner.getToken(); // ;
        } else if ((Scanner.cur.token == Token.T.tEQUAL)) {
            // this is an instantiation of a generic module/interface
            Scanner.getToken(); // =
            t.genericBase = pushGeneric(t, genericReader);
        } else {
            Scanner.fail("missing \';\' or \'=\', assuming \';\'");
        }

        // Now we know whether the generic was UNSAFE, so can process the exports.
        for (long i = 0; i <= n - 1; i++) {
            External.noteExport(t.externals, Ident.stack[(int) (Ident.top - n + i)]);
        }
        Ident.top -= n;

        // parse the imports
        External.parseImports(t.externals, t);

        // build my scopes and fill them!
        t.importScope = Scope.pushNew(true, M3ID.NoID, /* module := */ true);
        // this scope must be created after the imports & exports are
        //   parsed so that their module scopes aren't nested inside this one.

        // copy the imports and exports into my scope
        External.loadImports(t.externals, t);

        // open my private, local scope
        t.localScope = Scope.pushNew(true, id[0], /* module := */ true);

        while ((Token.DeclStart.contains(Scanner.cur.token))) {
            ESet.T[] fails = {t.fails};
            Decl.parse(t.interface_, true, fails);
            t.fails = fails[0];
        }

        t.body_origin = Scanner.offset;
        if ((topLevel)) {
            InitBody b = new InitBody();
            b.self = t;
            b.name = binderName(t.name, t.interface_);
            t.body = b;
            ProcBody.push(t.body);
        }
        if ((!t.interface_)) {
            Scanner.match(Token.T.tBEGIN);
            t.trace = BlockStmt.parseTrace();
            t.block = Stmt.parse();
        }
        if ((topLevel)) {
            ProcBody.pop();
        }

        if ((t.genericBase != M3ID.NoID)) {
            parseFinalEndID(t, t.genericBase);
            Scanner.pop();
        }
        Host.closeFile(genericReader[0]);
        parseFinalEndID(t, t.name);

        Scope.popNew(); // localScope

        Scope.popNew(); // importScope
        Revelation.pop(yy);
        if ((t.interface_) && (topLevel)) {
            parseDepth--;
        }
        switch_(save);

        Error.count(n_errs, n_warns);
        if ((n_errs[0] > n_initial_errs[0])) {
            t.has_errors = true;
        }

        // ETimer.Pop ();
        return t;
    }

    public static Module.T parse() {
        return parse(false);
    }

    /**
     * instantiate a call on a generic interface or module
     *
     * <p>Port: rd is VAR.
     */
    private static int pushGeneric(T t, InputStream[] rd) {
        int genericName, id;
        long nActuals, aBase;
        long nFormals, fBase;
        int formal, actual;
        String filename;
        T im;
        long old_file = Scanner.offset;
        long save;
        String old_filename;
        {
            String[] f = new String[1];
            long[] l = new long[1];
            Scanner.here(f, l);
            old_filename = f[0];
            save = l[0];
        }

        genericName = Scanner.matchID();
        if ((genericName == M3ID.NoID)) {
            return genericName;
        }

        // parse the list of actuals
        nActuals = parseGenericArgs();

        // open the external file
        {
            String[] f = new String[1];
            rd[0] = Host.openUnit(genericName, t.interface_, true, f);
            filename = f[0];
        }
        if ((rd[0] == null)) {
            Error.ID(genericName, "unable to find generic");
            return M3ID.NoID;
        }

        // build a synthetic file name & start reading
        filename = old_filename + " => " + filename;
        Scanner.push(filename, rd[0], /* is_main := */ Scanner.in_main);
        t.genericFile = filename;

        // Is the generic UNSAFE?
        if (Scanner.cur.token == Token.T.tUNSAFE) {
            t.safe = false; // Then so is the instantiation.
            Scanner.getToken();
        }

        // make sure we got what we wanted
        Scanner.match(Token.T.tGENERIC);
        if ((t.interface_)) {
            Scanner.match(Token.T.tINTERFACE);
        } else {
            Scanner.match(Token.T.tMODULE);
        }

        // get the generic's name
        id = Scanner.matchID();
        if ((id != M3ID.NoID)) {
            if ((id != genericName)) {
                Error.msg
                  ("Instantiated " + UnitKindText[ord(t.interface_)] + " in file \"" +
                   M3ID.toText(M3.ids, genericName) + UnitKindSuffix[ord(t.interface_)] +
                   "\" has wrong name: \"" + M3ID.toText(M3.ids, id) + "\"");
                genericName = id;
            }
        }

        // parse the list of formals
        nFormals = parseGenericArgs();
        Scanner.match(Token.T.tSEMI);

        // finally, generate the rewriting
        if ((nActuals != nFormals)) {
            save = Scanner.offset;
            Scanner.offset = old_file;
            Error.msg("number of actuals doesn\'t match number of generic formals");
            Scanner.offset = save;
        }
        fBase = Ident.top - nFormals;
        aBase = fBase - nActuals;
        for (long i = 0, last = Math.max(nActuals, nFormals) - 1; i <= last; i++) {
            if ((i < nFormals)) {
                formal = Ident.stack[(int) (fBase + i)];
            } else {
                formal = Ident.stack[(int) (aBase + i)]; // use the actual instead
            }
            if ((i < nActuals)) {
                actual = Ident.stack[(int) (aBase + i)];
            } else {
                actual = formal; // use the actual instead
            }
            im = lookUp(actual, /* internal := */ false);
            External.noteImport(t.externals, im, formal);
        }
        Ident.top -= nActuals + nFormals;

        return genericName;
    }

    private static long parseGenericArgs() {
        long n = 0;
        Scanner.match(Token.T.tLPAREN);
        if ((Scanner.cur.token == Token.T.tIDENT)) {
            n = Ident.parseList();
        }
        Scanner.match(Token.T.tRPAREN);
        return n;
    }

    private static void parseFinalEndID(T t, int goal) {
        int id;
        Scanner.match(Token.T.tEND);
        id = Scanner.matchID();
        if ((goal != id)) {
            Error.msg("Initial " + UnitKindText[ord(t.interface_)] + " name \"" +
                      M3ID.toText(M3.ids, goal) + "\" is not matched by final name \"" +
                      M3ID.toText(M3.ids, id) + "\"");
        }
        Scanner.match(Token.T.tDOT);
        if ((Scanner.cur.token != Token.T.tEOF)) {
            Scanner.fail("extra tokens ignored");
        }
    }

    private static boolean pushInterface(int name) {
        long i;
        // check for a cycle in the active imports
        parseStack[(int) parseDepth] = name;
        i = 0;
        while ((parseStack[(int) i] != name)) {
            i++;
        }
        if ((i == parseDepth)) {
            return true;
        }

        if ((error_buf == null)) {
            error_buf = M3Buf.New();
        }
        M3ID.put(M3.ids, error_buf, name);
        for (long j = i + 1; j <= parseDepth; j++) {
            M3Buf.putText(error_buf, " -> ");
            M3ID.put(M3.ids, error_buf, parseStack[(int) j]);
        }
        Error.txt(M3Buf.toText(error_buf), "circular imports");
        return false;
    }

    /** find and return the named interface module */
    public static Module.T lookUp(int name, boolean internal) {
        T t;
        long save;
        String[] filename = new String[1];
        M3.CheckState cs = M3.OuterCheckState.copy();
        InputStream rd;
        if (!internal) {
            if (!pushInterface(name)) {
                return null;
            }
        }

        t = (T) Host.env.find_ast(name);
        if ((t != null)) {
            if ((t.has_errors)) {
                Error.ID(name, "imported interface contains errors");
            }
            makeCurrent(t);
        } else {
            // open the external file & parse the interface
            rd = Host.openUnit(name, true, false, filename);
            if ((rd == null)) {
                Error.ID(name, "unable to find interface");
                return null;
            }

            Scanner.push(filename[0], rd, /* is_main := */ false);
            parseDepth++;
            t = parse(true);
            parseDepth--;
            Scanner.pop();
            Host.closeFile(rd);
            rd = null;

            // make sure we got what we wanted
            if ((t == null)) {
                Error.ID(name, "imported object is not an interface");
                return null;
            }
            if ((t.name != name)) {
                save = Scanner.offset;
                Scanner.offset = t.origin;
                Error.ID(name, "imported interface has wrong name");
                Scanner.offset = save;
                return null;
            }
            if ((!t.interface_)) {
                save = Scanner.offset;
                Scanner.offset = t.origin;
                Error.ID(name, "imported unit is not an interface");
                Scanner.offset = save;
                return null;
            }
            recordInterface(t);
            Value.typeCheck(t, cs);
        }
        if ((curModule != null) && (curModule.safe)
            && (!t.safe) && (!internal)) {
            Error.ID(name, "cannot import an unsafe interface in a safe module");
        }
        return t;
    }

    /** refresh 't' and its imports for the current compilation */
    public static void makeCurrent(Module.T t) {
        if ((t != null) && (t.compile_age < compile_age)) {
            t.compile_age = compile_age;
            t.globals[ord(false)].seg = null;
            t.globals[ord(true)].seg = null;
            t.import_offs = -1;
            t.last_import = 0;
            t.used = false;
            t.imported = true;
            t.exported = false;
            Value.reuse(t.value_info);
            Revelation.reuse(t.revelations);
            External.visit(t.externals, Module::makeCurrent);
        }
    }

    /**
     * we must be careful not to overwrite the cached values of
     * the builtin interfaces (e.g. Word), because the versions generated
     * from source don't have the special procedure methods needed
     * for code generation and constant evaluation.
     */
    private static void recordInterface(T t) {
        if ((t == null) || (t.name == M3ID.NoID)) {
            return;
        }
        for (long i = 0; i <= n_builtins - 1; i++) {
            {
                Builtin z = builtins[(int) i];
                if ((z.name == t.name) && (t != z.t)) {
                    return;
                }
            }
        }
        Host.env.note_ast(t.name, t);
    }

    public static void importRevelations(Module.T t, Value.T source) {
        Revelation.inherit(t.revelations, source);
    }

    private static void typeCheckMethod(T t, M3.CheckState cs) {
        typeCheck(t, false, cs);
    }

    public static void typeCheck(Module.T t, boolean main, M3.CheckState cs) {
        T save;
        Revelation.Set yy;
        Scope.T[] z1 = new Scope.T[1], z2 = new Scope.T[1];
        boolean save_main;
        long[] n_errs = new long[1], n_warns = new long[1], n_initial_errs = new long[1];
        if ((t.checked)) {
            return;
        }
        // ETimer.Push (M3Timers.check);
        Error.count(n_initial_errs, n_warns);

        save = switch_(t);
        save_main = Scanner.in_main;
        Scanner.in_main = main;
        yy = Revelation.push(t.revelations);
        softPush(t.importScope, z1);
        Scope.typeCheck(t.importScope, cs);
        softPush(t.localScope, z2);
        ESet.typeCheck(t.fails);
        ESet.push(cs, null, t.fails, /* stop := */ true);

        Revelation.typeCheck(t.revelations);
        Scope.typeCheck(t.localScope, cs);
        if ((!t.interface_)) {
            t.jmpbufs = Jmpbufs.checkProcPush(cs.jmpbufs, 0);
            BlockStmt.checkTrace(t.trace, cs);
            Stmt.typeCheck(t.block, cs);
            Jmpbufs.checkProcPop(cs.jmpbufs, t.jmpbufs);
        }

        ESet.pop(cs, null, t.fails, /* stop := */ true);
        softPop(t.localScope, z2[0]);
        softPop(t.importScope, z1[0]);
        Revelation.pop(yy);
        checkDuplicates(t);
        if ((main)) {
            noteVisibility(t);
            Scope.warnUnused(t.importScope);
            Scope.warnUnused(t.localScope);
        }

        Error.count(n_errs, n_warns);
        if ((n_errs[0] > n_initial_errs[0])) {
            t.has_errors = true;
        }

        setGlobals(t);
        Scanner.in_main = save_main;
        switch_(save);

        Error.count(n_errs, n_warns);
        if ((n_errs[0] > n_initial_errs[0])) {
            t.has_errors = true;
        }
        // ETimer.Pop ();

        // This is a horrible hack!  Since we want to call Module.TypeCheck
        //   with "main:=TRUE" directly from M3Compiler.Compile, we bypass
        //   the normal flag setting done by Value.TypeCheck.
        t.checkDepth = 0;
        t.checked = true;
    }

    /**
     * the scopes may be NIL when there's illegal cycles in the import graph
     *
     * <p>Port: tmp is VAR.
     */
    private static void softPush(Scope.T s, Scope.T[] tmp) {
        if ((s != null)) {
            tmp[0] = Scope.push(s);
        }
    }

    private static void softPop(Scope.T s, Scope.T tmp) {
        if ((s != null)) {
            Scope.pop(tmp);
        }
    }

    /**
     * Interface record offsets are allocated here.  We don't
     * allocate them during typechecking, since the order is
     * unpredictable.  Here, the offsets are allocated to
     * objects in the order that they appear in the source.
     */
    private static void setGlobals(T t) {
        Value.T v = Scope.toList(t.localScope);
        if ((t.has_errors)) {
            // don't bother
            return;
        }
        if ((Host.verbose) || (Host.load_map && Scanner.in_main)) {
            out(true, EOL, EOL, " global constants for ");
            out(true, dataName(t), EOL);
            out(false, EOL, EOL, " global data allocation for ");
            out(false, dataName(t), EOL);
        }
        if ((t.globals[ord(false)].size == 0)) {
            allocate(M3.rt.MI_SIZE, M3.target.Address.align, false, "*module info*");
        }
        Type.beginSetGlobals();
        while ((v != null)) {
            Type.setGlobals(v.origin);
            Value.setGlobals(v);
            v = v.next;
        }
        Type.setGlobals(Long.MAX_VALUE);
    }

    /**
     * allocate 'size' bits of space with the specified alignment
     * in the current module's global data or constant segment.  Return the
     * bit offset of allocated data.
     */
    public static long allocate(long size, long align, boolean is_const, String tag, int id) {
        long offset;
        align = Math.max(align, Target.Byte);
        align = Math.floorDiv(align + Target.Byte - 1, Target.Byte) * Target.Byte;
        size = Math.floorDiv(size + Target.Byte - 1, Target.Byte) * Target.Byte;
        offset = Math.floorDiv(curModule.globals[ord(is_const)].size + align - 1, align) * align;
        curModule.globals[ord(is_const)].size = offset + size;

        if ((Host.verbose) || (Host.load_map && Scanner.in_main)) {
            outI(Math.floorDiv(offset, Target.Byte), 6, is_const);
            outI(Math.floorDiv(size, Target.Byte), 6, is_const);
            outI(Math.floorDiv(align, Target.Byte), 3, is_const);
            out(is_const, "  ", tag);
            if ((id != M3ID.NoID)) {
                M3ID.put(M3.ids, load_map[ord(is_const)], id);
            }
            out(is_const, EOL);
        }

        return offset;
    }

    public static long allocate(long size, long align, boolean is_const, String tag) {
        return allocate(size, align, is_const, tag, M3ID.NoID);
    }

    public static long allocate(long size, long align, boolean is_const) {
        return allocate(size, align, is_const, null, M3ID.NoID);
    }

    /** Port: ARRAY BOOLEAN (*is_const*) OF M3Buf.T, indexed by ord (is_const). */
    private static M3Buf.T[] load_map = { null, null };
    private static final String[] Pads = { "", " ", "  ", "   ", "    ", "     " };

    private static void initLoadMap() {
        if ((load_map[ord(false)] == null)) {
            load_map[ord(false)] = M3Buf.New();
            load_map[ord(true)] = M3Buf.New();
        }
    }

    private static void outI(long n, long width, boolean is_const) {
        long x = 10;
        long pad = width - 1;
        if ((load_map[ord(false)] == null)) {
            initLoadMap();
        }
        while ((pad > 0) && (n >= x)) {
            pad--;
            x = 10 * x;
        }
        if ((pad > 0)) {
            M3Buf.putText(load_map[ord(is_const)], Pads[(int) pad]);
        }
        M3Buf.putInt(load_map[ord(is_const)], n);
    }

    private static void out(boolean is_const, String a, String b, String c, String d) {
        if ((load_map[ord(false)] == null)) {
            initLoadMap();
        }
        if ((a != null)) {
            M3Buf.putText(load_map[ord(is_const)], a);
        }
        if ((b != null)) {
            M3Buf.putText(load_map[ord(is_const)], b);
        }
        if ((c != null)) {
            M3Buf.putText(load_map[ord(is_const)], c);
        }
        if ((d != null)) {
            M3Buf.putText(load_map[ord(is_const)], d);
        }
    }

    private static void out(boolean is_const, String a, String b, String c) {
        out(is_const, a, b, c, null);
    }

    private static void out(boolean is_const, String a, String b) {
        out(is_const, a, b, null, null);
    }

    private static void out(boolean is_const, String a) {
        out(is_const, a, null, null, null);
    }

    private static void checkDuplicates(T t) {
        Value.T v, v2;
        long save = Scanner.offset;
        M3ID.advanceMarks(M3.ids);

        // mark all the imports
        v = Scope.toList(t.importScope);
        while ((v != null)) {
            M3ID.setMark(M3.ids, v.name);
            v = v.next;
        }

        // check for anything already marked in the local scope
        v = Scope.toList(t.localScope);
        while ((v != null)) {
            if (M3ID.setMark(M3.ids, v.name)) {
                v2 = Scope.lookUp(t.importScope, v.name, /* strict := */ true);
                if ((v2 != null)) {
                    // possible duplicate
                    if ((!External.isExportable(v2))
                        || (Value.classOf(v) != Value.Class.Procedure)
                        || (Value.classOf(v2) != Value.Class.Procedure)) {
                        Scanner.offset = v.origin;
                        Error.ID(v.name, "symbol redefined");
                    } else {
                        Procedure.noteExport(v, v2);
                        External.redirect(v2, v);
                    }
                }
            }
            v = v.next;
        }

        Scanner.offset = save;
    }

    private static void noteVisibility(T t) {
        Value.T v = Scope.toList(t.localScope);
        while ((v != null)) {
            switch (Value.classOf(v)) {
                case Module,
                     Error -> {
                    // no change of import/export status
                }
                case Expr,
                     Var,
                     Type,
                     Exception -> {
                    if ((t.interface_)) {
                        AssertError.check(!v.imported, "Module");
                        v.exported = true;
                        v.exportable = true;
                        // ELSE no change of import/export status
                    }
                }
                case Procedure -> {
                    AssertError.check(!v.imported, "Module");
                    if ((t.interface_)) {
                        v.used = true; // force a version stamp
                        v.exported = true;
                        v.imported = false;
                        // ****
                        // v.exported := v.external;
                        // v.imported := NOT v.exported;
                        // ***
                        v.exportable = true;
                    }
                }
                case Field,
                     Method,
                     Formal ->
                    throw new AssertError("Module");
            }
            v = v.next;
        }
    }

    public static boolean isSafe() {
        return (curModule == null) || (curModule.safe);
    }

    public static boolean isInterface() {
        return (curModule == null) || (curModule.interface_);
    }

    public static boolean isExternal() {
        return (curModule != null) && (curModule.external);
    }

    /** Port, addition: the current unit is bound to a Java class. */
    public static boolean isJava() {
        return (curModule != null) && (curModule.javaClass != null);
    }

    /**
     * Port, addition: the Java class of t if t is the opaque type T of an
     * interface bound to a Java class or the object type that lists the
     * fields of that T, null otherwise.
     */
    public static String javaClassOf(Type.T t) {
        if (t instanceof ObjectType.P o) {
            return o.opaque == null ? null : javaClassOf(o.opaque);
        }
        if (t instanceof OpaqueType.P op && op.declared != null
                && Scope.toUnit(op.declared) instanceof T m && m.javaClass != null
                && M3ID.toText(M3.ids, op.declared.name).equals("T")) {
            return m.javaClass;
        }
        return null;
    }

    /**
     * Port, addition: t is the type of a Java class, or an opaque subtype
     * of one. Such a type can be the supertype of an object type even if
     * no object type is known above it.
     */
    public static boolean isJavaType(Type.T t) {
        while (t != null) {
            Type.T u = Type.stripPacked(Type.check(t));
            if (javaClassOf(u) != null) {
                return true;
            }
            t = u.info.class_ == Type.Class.Opaque ? OpaqueType.super_(u) : null;
        }
        return false;
    }

    public static boolean lazyAlignmentOn() {
        return curModule != null && curModule.lazyAligned;
    }

    public static void setLazyAlignment(boolean on) {
        if (curModule != null) {
            curModule.lazyAligned = on;
            if (on) {
                curModule.containsLazyAlignments = true;
            }
        }
    }

    public static Scope.T exportScope(Module.T t) {
        if ((t == null)) {
            return null;
        } else {
            return t.localScope;
        }
    }

    public static void compile(Module.T t) {
        T save;
        Scope.T zz;
        Revelation.Set yy;
        // ETimer.Push (M3Timers.emit);
        M3.target.Allow_packed_byte_aligned = t.containsLazyAlignments;
        save = switch_(t);
        Scanner.offset = t.origin;
        yy = Revelation.push(t.revelations);
        zz = Scope.push(t.localScope);
        WebInfo.reset();
        CG.begin_unit();
        CG.widechar_size(Target.wideCharSize(M3.target));
        CG.gen_location(t.origin);
        Host.env.note_unit(t.name, t.interface_);
        declareGlobalData(t);
        if ((t.body != null)) {
            emitDecl(t.body);
        }
        Type.compileAll();
        if ((t.interface_)) {
            compileInterface(t);
        } else {
            compileModule(t);
        }
        if ((load_map[ord(false)] != null)) {
            CG.comment(-1, false, "load map", EOL,
                       M3Buf.toText(load_map[ord(false)]),
                       M3Buf.toText(load_map[ord(true)]));
            load_map[ord(false)] = null;
            load_map[ord(true)] = null;
        }
        CG.end_unit();
        Host.env.note_webinfo(WebInfo.finish());
        Scope.pop(zz);
        Revelation.pop(yy);
        switch_(save);
        // ETimer.Pop ();
    }

    private static void compileInterface(T t) {
        long[] proc_info = new long[1], rev_full = new long[1], rev_part = new long[1];
        long type_map;
        // declare the modules that I import & export
        // * EVAL GlobalData (t); *
        CG.export_unit(t.name);
        Host.env.note_interface_use(t.name, /* imported := */ false);

        if ((t.genericBase != M3ID.NoID)) {
            Host.env.note_generic_use(t.genericBase);
        }
        External.genLinkInfo(t.externals);
        importImplementations();

        // declare my imports, exports and local variables
        External.genImports(t.externals);
        Scope.enter(t.importScope);
        Scope.enter(t.localScope);

        // declare any visible revelations
        Revelation.declare(t.revelations, rev_full, rev_part);

        // generate any internal procedures
        ProcBody.emitAll(proc_info);

        type_map = Variable.genGlobalMap(t.localScope);

        genLinkerInfo(t, proc_info[0], type_map, rev_full[0], rev_part[0]);

    }

    private static void compileModule(T t) {
        long[] proc_info = new long[1], rev_full = new long[1], rev_part = new long[1];
        long type_map;
        // declare the modules that I import & export
        if ((t.genericBase != M3ID.NoID)) {
            Host.env.note_generic_use(t.genericBase);
        }
        External.genLinkInfo(t.externals);

        // declare my imports, exports and local variables
        // **** moved below **** External.GenImports (t.externals);
        Scope.enter(t.importScope);
        Scope.enter(t.localScope);

        // declare any visible revelations
        Revelation.declare(t.revelations, rev_full, rev_part);

        // generate the tables for coverage
        Coverage.generateTables();

        // generate any internal procedures
        ProcBody.emitAll(proc_info);

        type_map = Variable.genGlobalMap(t.localScope);

        // declare my imports
        External.genImports(t.externals);
        // we deferred the import declarations until all the code
        //   has been generated to pick up imports that are used
        //   via "Value.Load", but not "Scope.LookUp".

        genLinkerInfo(t, proc_info[0], type_map, rev_full[0], rev_part[0]);

    }

    private static void declareGlobalData(T t) {
        CG.comment(-1, false, "module global constants");
        t.globals[ord(true)].seg = CG.declare_segment(M3ID.NoID,
                                                      ModuleTypeUID, /* is_const := */ true);
        CG.comment(-1, false, "module global data");
        t.globals[ord(false)].seg = CG.declare_segment(M3ID.add(M3.ids, dataName(t)),
                                                       ModuleTypeUID, /* is_const := */ false);
    }

    /** returns the current module's global data segment. */
    public static M3CG.Var globalData(boolean is_const) {
        AssertError.check(curModule.compile_age >= compile_age, "Module");
        return curModule.globals[ord(is_const)].seg;
    }

    /**
     * generate code to load the address of 't's global constant/data + 'offset'.
     * PRE: t = Current () OR NOT is_const.
     */
    public static void loadGlobalAddr(Module.T t, long offset, boolean is_const) {
        AssertError.check(t.compile_age >= compile_age, "Module");
        if ((t == curModule)) {
            CG.load_addr_of(t.globals[ord(is_const)].seg, offset, CG.Max_alignment);
        } else {
            AssertError.check(!is_const, "Module");
            importInterface(t);
            CG.load_addr
              (curModule.globals[ord(false)].seg, t.import_offs + M3.rt.II_import,
               CG.Max_alignment);
            CG.add_offset(offset);
        }
    }

    /**
     * generate the structures that force "t" to be imported and initialized at
     * link time.
     */
    public static void importInterface(Module.T t) {
        AssertError.check(t.compile_age >= compile_age, "Module");
        if ((t != curModule) && (t.import_offs < 0)) {
            // this is the first reference to the imported interface 't'
            t.import_offs = buildImportLink(t.name, binderName(t.name, t.interface_));
        }
    }

    private static long buildImportLink(int nm, String binder) {
        boolean[] new_proc = new boolean[1];
        long prev_link;
        long offset = allocate(M3.rt.II_SIZE, M3.target.Address.align, false,
                               "import ", nm);
        // return_typename is needed, in order to get it correct/consistent everywhere,
        // because RTLinkerX.i3 declares some of these functions this way.
        M3CG.Proc proc = CG.import_procedure(M3ID.add(M3.ids, binder), 1, CGType.Addr,
                                             M3.target.DefaultCall, new_proc,
                                             0, /* return_typename := */ m3id_RT0_ModulePtr());
        binderDeclareParam();
        if ((curModule.last_import == 0)) {
            prev_link = M3.rt.MI_imports;
        } else {
            prev_link = curModule.last_import + M3.rt.II_next;
        }
        curModule.last_import = offset;
        CG.init_var(prev_link, curModule.globals[ord(false)].seg, offset, false);
        CG.init_proc(offset + M3.rt.II_binder, proc, false);
        return offset;
    }

    /**
     * Generate the import and initialization links that cause the
     * importer of an interface to also bind to the implementations
     * of that interface.
     */
    private static void importImplementations() {
        M3Compiler.ImplList x = Host.env.get_implementations(curModule.name);
        while ((x != null)) {
            buildImportLink(x.impl, binderName(x.impl, /* interface := */ false));
            x = x.next;
        }
    }

    private static void emitDecl(InitBody x) {
        T t = x.self;
        if ((x.cg_proc != null)) {
            return;
        }
        Scanner.offset = t.body_origin;
        CG.gen_location(t.body_origin);
        // return_typename is needed, in order to get it correct/consistent everywhere,
        // because RTLinkerX.i3 declares some of these functions this way.
        x.cg_proc = CG.declare_procedure(M3ID.add(M3.ids, x.name), 1, CGType.Addr,
           /* lev := */ 0, /* cc := */ M3.target.DefaultCall, /* exported := */ true, /* parent := */ null,
           0, /* return_typename := */ m3id_RT0_ModulePtr());
        x.arg = binderDeclareParam();
    }

    private static void emitBody(InitBody x) {
        T t = x.self;
        Scope.T zz;
        long skip = CG.next_label();
        if ((x.cg_proc == null)) {
            return;
        }

        // restore my environment
        zz = Scope.push(t.localScope);

        // generate my initialization procedure
        CG.comment(-1, false, "module main body ", x.name);
        Scanner.offset = t.body_origin;
        CG.gen_location(t.body_origin);
        CG.begin_procedure(x.cg_proc);

        CG.load_int(M3.target.Integer.cg_type, x.arg);
        CG.if_false(skip, CG.Never);

        Scope.initValues(t.importScope);
        Scope.initValues(t.localScope);

        // initialize my exported variables
        External.initGlobals(t.externals);

        // perform the main body
        Tracer.push(t.trace);
        Jmpbufs.compileProcAllocateJmpbufs(t.jmpbufs);
        Stmt.compile(t.block);
        Tracer.pop(t.trace);

        CG.set_label(skip);

        CG.load_addr_of(t.globals[ord(false)].seg, 0, CG.Max_alignment);
        CG.exit_proc(CGType.Addr);
        CG.end_procedure(x.cg_proc);

        Scope.pop(zz);
    }

    private static void genLinkerInfo(T t, long proc_info, long type_map, long rev_full, long rev_part) {
        M3CG.Var v = t.globals[ord(false)].seg;
        M3CG.Var vc = t.globals[ord(true)].seg;
        String file;
        long line, offs;
        long type_cells, type_cell_ptrs;
        long exception_scopes = Marker.emitScopeTable();
        Scanner.offset = t.origin;
        if ((t.genericFile != null)) {
            file = t.genericFile;
        } else {
            String[] f = new String[1];
            long[] l = new long[1];
            Scanner.here(f, l);
            file = f[0];
            line = l[0];
        }
        file = Target.cleanupSourcePath(M3.target, file);
        offs = CG.emitText(file, /* is_const := */ true);
        CG.init_var(M3.rt.MI_file, vc, offs, /* is_const := */ false);
        CG.comment(offs, true, "file name");

        type_cells = Type.genCells();
        type_cell_ptrs = Type.genCellPtrs();
        // note: the type info cannot be generated until *all* types have
        //   have been declared

        if ((type_cells >= 0)) {
            CG.init_var(M3.rt.MI_type_cells, v, type_cells, false);
        }
        if ((type_cell_ptrs >= 0)) {
            CG.init_var(M3.rt.MI_type_cell_ptrs, v, type_cell_ptrs, false);
        }
        if ((rev_full >= 0)) {
            CG.init_var(M3.rt.MI_full_rev, vc, rev_full, false);
        }
        if ((rev_part >= 0)) {
            CG.init_var(M3.rt.MI_part_rev, vc, rev_part, false);
        }
        if ((proc_info >= 0)) {
            CG.init_var(M3.rt.MI_proc_info, vc, proc_info, false);
        }
        if ((exception_scopes >= 0)) {
            CG.init_var(M3.rt.MI_try_scopes, vc, exception_scopes, false);
        }
        if ((type_map >= 0)) {
            CG.init_var(M3.rt.MI_var_map, vc, type_map, false);
            CG.init_var(M3.rt.MI_gc_map, vc, type_map, false);
        }
        if ((t.body != null) && (t.body.cg_proc != null)) {
            CG.init_proc(M3.rt.MI_binder, t.body.cg_proc, false);
        }
        if ((Host.doIncGC) && (Host.doGenGC)) {
            CG.init_intt(M3.rt.MI_gc_flags, M3.target.Integer.size, 3, false);
        } else if ((Host.doIncGC)) {
            CG.init_intt(M3.rt.MI_gc_flags, M3.target.Integer.size, 2, false);
        } else if ((Host.doGenGC)) {
            CG.init_intt(M3.rt.MI_gc_flags, M3.target.Integer.size, 1, false);
        }

        // finish up the global data segment allocations
        allocate(0, M3.target.Address.align, false, "*TOTAL*");
        allocate(0, M3.target.Address.align, true, "*TOTAL*");

        // generate a debugging type descriptor for the global data
        CG.comment(-1, false, "global constant type descriptor");
        CG.emit_global_record(t.globals[ord(true)].size, true);
        CG.comment(-1, false, "global data type descriptor");
        CG.emit_global_record(t.globals[ord(false)].size, false);

        // finish the global data initializations
        CG.comment(-1, true, "module global constants");
        CG.bind_segment(t.globals[ord(true)].seg, t.globals[ord(true)].size, CG.Max_alignment,
                        CGType.Struct, /* exported := */ false, /* init := */ true,
                        /* is_const := */ true);
        CG.comment(-1, false, "module global data");
        CG.bind_segment(t.globals[ord(false)].seg, t.globals[ord(false)].size, CG.Max_alignment,
                        CGType.Struct, /* exported := */ false, /* init := */ true,
                        /* is_const := */ false);
    }

    private static long addFPTag(T t, M3.FPInfo x) {
        ValueRep.fpStart(t, x, UnitKindRW[ord(t.interface_)], 0, /* global := */ false);
        return 0;
    }

    private static Type.T fpType(T t) {
        return null;
    }

    /** The current module. */
    public static Module.T current() {
        return curModule;
    }

    /** t = NIL =&gt; use Current */
    public static int name(Module.T t) {
        if ((t == null)) {
            t = curModule;
        }
        if ((t == null)) {
            return M3ID.NoID;
        }
        return t.name;
    }

    private static String dataName(T t) {
        if ((t == null)) {
            t = curModule;
        }
        if ((t == null)) {
            return "";
        }
        if ((t.data_name == null)) {
            t.data_name = GlobalDataPrefix[ord(t.interface_)] + M3ID.toText(M3.ids, t.name);
        }
        return t.data_name;
    }

    private static String binderName(int nm, boolean interface_) {
        return M3ID.toText(M3.ids, nm) + MainBodySuffix[ord(interface_)];
    }

    private static M3CG.Var binderDeclareParam() {
        // Port: the uid is a 32-bit value (M3FP.ToInt).
        return CG.declare_param(M3ID.add(M3.ids, "mode"), M3.target.Integer.size,
                                M3.target.Integer.align, M3.target.Integer.cg_type,
                                (int) Type.globalUID(Int.T), /* in_memory */ false,
                                /* up_level */ false, /* frequency */ CG.Always);
    }

    /**
     * Return the next counter value for the current module and
     * increment the counter.
     *
     * <p>Port: c is VAR ARRAY [0..4] OF CHAR, a char[5] that receives the
     * value.
     */
    public static void getNextCounter(char[] c) {
        AssertError.check(curModule != null, "Module");
        {
            char[] cnt = curModule.counter;
            System.arraycopy(curModule.counter, 0, c, 0, 5);

            // bump the counter
            for (int j = cnt.length - 1; j >= 0; j--) {
                if ((cnt[j] == '9')) {
                    cnt[j] = '0';
                } else {
                    cnt[j] = (char) (cnt[j] + 1);
                    break;
                }
            }
        }
    }

    /** return the global type info for module 't' */
    public static Type.ModuleInfo getTypeInfo(Module.T t) {
        return t.type_info;
    }

    /**
     * Call 'v(m)' for each interface 'm' imported or exported,
     * directly or indirectly, by the current module.  Restrictions:
     * 'v' must be a top-level procedure and may not call 'VisitImports'
     * directly or indirectly.
     */
    public static void visitImports(Module.Visitor v) {
        AssertError.check(visit_proc == null, "Module");
        visit_proc = v;
        visit_age++;
        External.visit(curModule.externals, Module::innerVisit);
        visit_proc = null;
    }

    @FunctionalInterface
    public interface Visitor { void apply(T t); }

    private static void innerVisit(T t) {
        if ((t != null) && (t.visit_age < visit_age)) {
            t.visit_age = visit_age;
            External.visit(t.externals, Module::innerVisit);
            visit_proc.apply(t);
        }
    }
}
