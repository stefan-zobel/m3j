// Java port of CM3's m3-sys/m3front/src/values/Value.i3 and Value.m3
// (except the procedures of ValueRep, which are in ValueRep.java).
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.fingerprint.Fingerprint;
import m3j.middle.M3Buf;
import m3j.middle.M3FP;
import m3j.middle.M3ID;
import m3j.util.AssertError;
import m3j.util.RangeError;

// File: Value.i3
// Last modified on Tue Feb 28 17:00:17 PST 1995 by kalsow
//      modified on Fri Dec  7 05:12:46 1990 by muller

// File: Value.m3
// Last modified on Wed Mar  1 08:51:59 PST 1995 by kalsow
//      modified on Wed Mar 27 03:00:56 1991 by muller

public final class Value {
    private Value() {}

    /**
     * Port: M3.Value, with the fields and methods of its revelation in
     * ValueRep.
     */
    public static class T extends M3.ValueNode {
        public int name;
        public int extName;
        public Scope.T scope;
        /** linked list of all values in the same module */
        public T vnext;
        // endian neutrality
        // checkDepth : BITS 12 FOR [-2048..2047];
        // class      : BITS 4 FOR Value.Class;
        /** Port: [-2048..2047]; assignments are range-checked. */
        public long checkDepth;
        public Class class_;
        public boolean checked;
        public boolean readonly;
        /** assigned a traced value */
        public boolean traced;
        public boolean external;
        public boolean unused;
        public boolean obsolete;
        public boolean up_level;
        /** reset in each compilation */
        public boolean error;
        /** reset in each compilation */
        public boolean used;
        /** reset in each compilation */
        public boolean declared;
        /** reset in each compilation */
        public boolean inited;
        /** reset in each compilation */
        public boolean compiled;
        /** reset in each compilation */
        public boolean imported;
        /** reset in each compilation */
        public boolean exported;
        /** reset in each compilation */
        public boolean exportable;
        /** reset in each compilation */
        public boolean inTypeOf;
        /** reset in each compilation */
        public boolean inToExpr;
        /** reset in each compilation */
        public boolean inToType;
        public boolean lazyAligned;

        public void typeCheck(M3.CheckState cs) {
            throw new AssertError("ValueRep.typeCheck");
        }

        public void set_globals() {
            ValueRep.noInit(this);
        }

        public void load() {
            ValueRep.noLoader(this);
        }

        public boolean declare() {
            return ValueRep.never(this);
        }

        public void const_init() {
            ValueRep.noInit(this);
        }

        public boolean need_init() {
            return ValueRep.never(this);
        }

        public void lang_init() {
            ValueRep.noInit(this);
        }

        public void user_init() {
            ValueRep.noInit(this);
        }

        public Expr.T toExpr() {
            return ValueRep.noExpr(this);
        }

        public Type.T toType() {
            return ValueRep.noType(this);
        }

        public Type.T typeOf() {
            return ValueRep.noType(this);
        }

        public Type.T repTypeOf() {
            return ValueRep.noType(this);
        }

        public T base() {
            return ValueRep.self(this);
        }

        public long add_fp_tag(M3.FPInfo x) {
            throw new AssertError("ValueRep.add_fp_tag");
        }

        public Type.T fp_type() {
            throw new AssertError("ValueRep.fp_type");
        }
    }

    // Port: CheckState = M3.CheckState.

    public enum Class { Expr, Var, Type, Exception, Procedure, Module, Field, Method, Formal, Error }

    static final long NOT_CHECKED = -1;
    private static final long CHECKED = 0;

    /** Port: the range check of an assignment to {@code checkDepth}. */
    private static long checkDepthValue(long x) {
        if (x < -2048 || x > 2047) {
            throw new RangeError("Value");
        }
        return x;
    }

    public static void typeCheck(Value.T t, M3.CheckState cs) {
        long save;
        if ((t == null)) {
            return;
        }
        if ((t.checked)) {
            return;
        }
        if ((t.checkDepth == NOT_CHECKED)) {
            // this node is not currently being checked
            save = Scanner.offset;
            Scanner.offset = t.origin;
            t.checkDepth = checkDepthValue(Type.recursionDepth);
            t.typeCheck(cs);
            t.checkDepth = CHECKED;
            t.checked = true;
            Scanner.offset = save;
        } else if ((t.checkDepth != Type.recursionDepth)) {
            // this is a legal recursion, just return
        } else {
            illegalRecursion(t);
        }
    }

    /** returns the (semantic) type of 't' */
    public static Type.T typeOf(Value.T t) {
        Type.T x;
        if ((t == null)) {
            return ErrType.T;
        }
        if (t.error) {
            return ErrType.T;
        }
        if ((t.inTypeOf)) {
            illegalRecursion(t);
            return ErrType.T;
        }
        t.inTypeOf = true;
        x = t.typeOf();
        t.inTypeOf = false;
        return x;
    }

    /** returns the representation type of 't' */
    public static Type.T repTypeOf(Value.T t) {
        Type.T x;
        if ((t == null)) {
            return ErrType.T;
        }
        if (t.error) {
            return ErrType.T;
        }
        if ((t.inTypeOf)) {
            illegalRecursion(t);
            return ErrType.T;
        }
        t.inTypeOf = true;
        x = t.repTypeOf();
        t.inTypeOf = false;
        return x;
    }

    /** assign offsets to any needed global data. */
    public static void setGlobals(Value.T t) {
        if ((t == null)) {
            return;
        }
        AssertError.check(t.checked, "Value");
        t.set_globals();
    }

    /** generate code to load 't' on the evaluation stack */
    public static void load(Value.T t) {
        if ((t == null)) {
            return;
        }
        AssertError.check(t.checked, "Value");
        t.used = true;
        t.load();
    }

    public static Expr.T toExpr(Value.T t) {
        Expr.T e;
        if ((t == null)) {
            return null;
        }
        if ((t.inToExpr)) {
            illegalRecursion(t);
            return null;
        }
        t.inToExpr = true;
        e = t.toExpr();
        t.inToExpr = false;
        return e;
    }

    public static Type.T toType(Value.T t) {
        Type.T x;
        if ((t == null)) {
            return null;
        }
        if ((t.inToType)) {
            illegalRecursion(t);
            return null;
        }
        t.inToType = true;
        x = t.toType();
        t.inToType = false;
        return x;
    }

    public static Value.T base(Value.T t) {
        if ((t == null)) {
            return null;
        }
        return t.base();
    }

    public static void illegalRecursion(Value.T t) {
        if ((!t.error)) {
            Error.ID(t.name, "Illegal recursive declaration (2.4.8).");
            t.error = true;
        }
    }

    public static Value.Class classOf(Value.T t) {
        if ((t == null)) {
            return Class.Error;
        }
        return t.class_;
    }

    private static M3Buf.T mbuf = null;

    private static Fingerprint.T toFP(Value.T t) {
        long n;
        M3.FPInfo x = new M3.FPInfo();
        Fingerprint.T fp;
        if ((t == null)) {
            return M3FP.Zero;
        }
        t = base(t);
        if ((mbuf == null)) {
            mbuf = M3Buf.New();
        }

        // build the tag
        x.tag = null;
        x.buf = mbuf;
        mbuf = null;
        x.n_nodes = 0;
        x.others = null;
        n = addFPTag(t, x);
        if ((x.tag != null)) {
            fp = TypeFP.fromText(x.tag);
        } else {
            fp = TypeFP.fromBuf(x.buf);
        }
        mbuf = x.buf;
        x.buf = null;
        x.tag = null;

        // add any type information
        if ((n > 0)) {
            AssertError.check(n == 1, "Value");
            addFPEdges(t, x, 0);
            fp = TypeFP.fromPair(fp, TypeFP.fromType(x.nodes[0]));
        }

        return fp;
    }

    public static long addFPTag(Value.T t, M3.FPInfo x) {
        long n;
        if ((t == null)) {
            return 0;
        }
        t = t.base();
        M3Buf.putChar(x.buf, '<');
        n = t.add_fp_tag(x);
        M3Buf.putChar(x.buf, '>');
        return n;
    }

    public static long addFPEdges(Value.T t, M3.FPInfo x, long n) {
        Value.T u;
        Type.T type;
        if ((t == null)) {
            return n;
        }
        u = t.base();
        type = u.fp_type();
        if ((type != null)) {
            if ((x.others == null)) {
                x.nodes[(int) n] = type;
            } else {
                x.others[(int) n] = type;
            }
            n++;
        }
        return n;
    }

    // Port: FPStart is in ValueRep.java.

    public static final class VSFlags {
        public boolean need_vs;
        public boolean imported;
        public boolean implemented;

        VSFlags() {}
    }

    /** generate the C declaration for t */
    public static void declare(Value.T t) {
        VSFlags f = new VSFlags();
        Fingerprint.T fp;
        if ((t == null)) {
            return;
        }
        if ((t.declared)) {
            return;
        }
        if ((!t.used) && (t.imported)) {
            return;
        }
        t.declared = true;
        getVSFlags(t, f);
        if (t.declare() && (Host.versionStamps) && (f.need_vs)) {
            fp = toFP(t);
            Host.env.note_version_stamp(Scope.moduleName(t), t.name,
                                        fp, f.imported, f.implemented);
        }
    }

    private static void getVSFlags(Value.T t, VSFlags f) {
        f.need_vs = t.imported || t.exported;

        if ((t.external)) {
            f.imported = !t.exported;
        } else {
            f.imported = t.imported;
        }

        f.implemented = !f.imported;
        if ((!t.external) && (t.class_ == Class.Procedure)) {
            f.implemented = Procedure.hasBody((Procedure.T) base(t));
        }

    }

    /** generate the static initializations needed by 't' */
    public static void constInit(Value.T t) {
        long save;
        if ((t == null)) {
            return;
        }
        if ((t.inited)) {
            return;
        }
        AssertError.check(t.checked, "Value");
        t.inited = true;
        save = Scanner.offset;
        Scanner.offset = t.origin;
        t.const_init();
        Scanner.offset = save;
    }

    /** returns TRUE if 't' needs runtime code to be initialized */
    public static boolean needsInit(Value.T t) {
        if ((t == null)) {
            return false;
        }
        return t.need_init();
    }

    /**
     * generate language required initialization for t if Declare &amp;
     * ConstInit did nothing
     */
    public static void langInit(Value.T t) {
        long save;
        if ((t == null)) {
            return;
        }
        if ((t.compiled)) {
            return;
        }
        AssertError.check(t.checked, "Value");
        t.compiled = true;
        save = Scanner.offset;
        Scanner.offset = t.origin;
        t.lang_init();
        CG.free_temps();
        Scanner.offset = save;
    }

    /** generate the user specified initialization for t */
    public static void userInit(Value.T t) {
        long save;
        if ((t == null)) {
            return;
        }
        save = Scanner.offset;
        Scanner.offset = t.origin;
        t.user_init();
        CG.free_temps();
        Scanner.offset = save;
    }

    /**
     * all values in the current module
     *
     * <p>Port: package-private, since ValueRep.init writes it.
     */
    static Value.T all;

    // Port: Init, NoExpr, NoType, NoLoader, Never, NoInit, Always, TypeVoid
    // and Self are in ValueRep.java.

    public static void reset() {
        all = null;
    }

    /**
     * sets the current module's list of values to 't' and
     * returns the previouse module's value list.  This routine
     * is only called when the "current" module changes.
     */
    public static Value.T setModule(Value.T new_) {
        Value.T old = all;
        all = new_;
        return old;
    }

    /**
     * prepares the values on list 't' for reuse in another
     * compilation ==&gt; reset a bunch of flags
     */
    public static void reuse(Value.T t) {
        while ((t != null)) {
            t.used = false;
            t.error = false;
            t.declared = false;
            t.inited = false;
            t.compiled = false;
            t.imported = (!Host.emitBuiltins);
            t.exported = false;
            t.exportable = false;
            t.inTypeOf = false;
            t.inToExpr = false;
            t.inToType = false;
            t = t.vnext;
        }
    }

    public static boolean isExternal(Value.T t) {
        return (t.external);
    }

    public static boolean isImported(Value.T t) {
        return (t != null) && (t.imported);
    }

    public static boolean isWritable(Value.T t, boolean traced) {
        if (traced) {
            t.traced = true;
        }
        return !t.readonly;
    }

    public static int cName(Value.T t) {
        if ((t == null)) {
            return M3ID.NoID;
        }
        return t.base().name;
    }

    public static String globalName(Value.T t, boolean dots, boolean with_module) {
        Scope.IDStack ss = new Scope.IDStack();
        if ((t == null)) {
            return null;
        }
        ss.top = 0;
        Scope.nameToPrefix(t, ss, !dots, dots, with_module);
        return Scope.stackToText(ss);
    }

    public static String globalName(Value.T t, boolean dots) {
        return globalName(t, dots, true);
    }

    public static String globalName(Value.T t) {
        return globalName(t, true, true);
    }
}
