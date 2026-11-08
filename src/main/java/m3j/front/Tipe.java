// Java port of CM3's m3-sys/m3front/src/values/Tipe.i3 and Tipe.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3ID;

// File: Tipe.i3
// Last Modified On Tue Dec 20 15:09:17 PST 1994 By kalsow
//      Modified On Fri Feb 23 03:41:39 1990 By muller

// File: Tipe.m3
// Last Modified On Wed Mar  1 08:45:33 PST 1995 By kalsow
//      Modified On Tue Nov 27 22:16:56 1990 By muller

public final class Tipe {
    private Tipe() {}

    public static final class T extends Value.T {
        public Type.T value;
        public boolean is_new_ref;

        @Override
        public void typeCheck(M3.CheckState cs) {
            check(this, cs);
        }

        @Override
        public void set_globals() {
            setGlobals(this);
        }

        @Override
        public boolean declare() {
            return compile(this);
        }

        @Override
        public Type.T toType() {
            return Tipe.toType(this);
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
            return Tipe.toType(this);
        }

        // Port: the overrides load := ValueRep.NoLoader, const_init :=
        // ValueRep.NoInit, need_init := ValueRep.Never, lang_init,
        // user_init := ValueRep.NoInit, toExpr := ValueRep.NoExpr and base
        // := ValueRep.Self restate the inherited methods.
    }

    public static void parse(Decl.Attributes att) {
        // Port: att is passed by value and written below; work on a copy.
        att = att.copy();
        T t;
        int id;
        Scanner.match(Token.T.tTYPE);

        while ((Scanner.cur.token == Token.T.tIDENT ||
                Scanner.cur.token == Token.T.tLAZYALIGN ||
                Scanner.cur.token == Token.T.tSTRICTALIGN)) {
            if (Scanner.cur.token == Token.T.tLAZYALIGN) {
                att.isLazyAligned = true;
                Module.setLazyAlignment(true);
                Scanner.getToken(); // LAZYALIGN
                Scanner.match(Token.T.tENDPRAGMA);
            } else if (Scanner.cur.token == Token.T.tSTRICTALIGN) {
                att.isLazyAligned = false;
                Module.setLazyAlignment(false);
                Scanner.getToken(); // LAZYALIGN
                Scanner.match(Token.T.tENDPRAGMA);
            }
            if (Scanner.cur.token == Token.T.tIDENT) {
                id = Scanner.matchID();
                t = create(id);
                t.unused = att.isUnused;
                t.obsolete = att.isObsolete;
                t.lazyAligned = att.isLazyAligned;
                Scope.insert(t);
                switch (Scanner.cur.token) {
                    case tEQUAL -> {
                        Scanner.getToken(); // =
                        t.value = Type.parse();
                        Type.setLazyAlignment(t.value, t.lazyAligned);
                    }
                    case tSUBTYPE -> {
                        Scanner.getToken(); // <:
                        t.value = OpaqueType.new_(Type.parse(), t);
                        Type.setLazyAlignment(t.value, t.lazyAligned);
                    }
                    default -> {
                        Scanner.fail("missing '=' or '<:'");
                    }
                }
                // t.value.info.name := id;
                Scanner.match(Token.T.tSEMI);
            }
        }

    }

    private static T create(int name) {
        T t;
        t = new T();
        ValueRep.init(t, name, Value.Class.Type);
        t.readonly   = true;
        t.value      = null;
        t.is_new_ref = false;
        return t;
    }

    public static void define(String name, Type.T type, boolean reserved) {
        T t;
        t = create(M3ID.add(M3.ids, name));
        t.value = type;
        type.info.name = t.name;
        Scope.insert(t);
        if ((reserved)) {
            Scanner.noteReserved(t.name, t);
        }
    }

    public static Type.T defineOpaque(String name, Type.T super_) {
        T t;
        t = create(M3ID.add(M3.ids, name));
        Scope.insert(t);
        t.value = OpaqueType.new_(super_, t);
        t.value.info.name = t.name;
        Scanner.noteReserved(t.name, t);
        return t.value;
    }

    private static void check(T t, M3.CheckState cs) {
        Type.Info info = new Type.Info();
        Type.T initial = t.value;
        M3.QID typename = new M3.QID();
        String name;
        t.value = Type.checkInfo(t.value, info);

        if ((!t.imported)
            && ((info.class_ == Type.Class.Ref) || (info.class_ == Type.Class.Object))
            && (!NamedType.split(initial, typename))) {
            name = Value.globalName(t);
            if ((info.class_ == Type.Class.Ref)) {
                RefType.noteRefName(t.value, name);
            } else {
                ObjectType.noteRefName(t.value, name);
            }
        }
    }

    private static boolean compile(T t) {
        long uid;
        String name;
        Type.compile(t.value);
        // IF NOT t.imported THEN
        uid  = Type.globalUID(t.value);
        name = Value.globalName(t, /* dots := */ true, /* with_module := */ false); // Type, not Module.Type, not Module__Type
        // Port: a type UID is a 32-bit CG.TypeUID.
        CG.declare_typename((int) uid, M3ID.add(M3.ids, name));
        WebInfo.declare_typename((int) uid, t);
        // END;
        return true;
    }

    private static void setGlobals(T t) {
        // Type.SetGlobals (t.value);
    }

    private static long addFPTag(T t, M3.FPInfo x) {
        ValueRep.fpStart(t, x, "TYPE ", 0, /* global := */ true);
        return 1;
    }

    private static Type.T toType(T t) {
        return t.value;
    }
}
