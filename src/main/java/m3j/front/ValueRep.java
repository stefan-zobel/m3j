// Java port of CM3's m3-sys/m3front/src/values/ValueRep.i3 and of the
// procedures of ValueRep in m3-sys/m3front/src/values/Value.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.M3ID;
import m3j.util.AssertError;

// File: ValueRep.i3
// Last modified on Wed Mar  1 08:47:26 PST 1995 by kalsow
//      modified on Fri Feb 23 03:41:40 1990 by muller

public final class ValueRep {
    private ValueRep() {}

    // Port: the revelation of M3.Value is the class Value.T.

    // Port: the bodies below are those of MODULE Value EXPORTS Value,
    // ValueRep; FPStart comes first, as in Value.m3.

    /** add "tag", the name, external name, and offset of 'v' to 'x's buffer */
    public static void fpStart(Value.T t, M3.FPInfo x, String tag, long offset, boolean global) {
        Scope.IDStack s = new Scope.IDStack();
        M3Buf.putText(x.buf, tag);
        if ((global)) {
            s.top = 0;
            Scope.nameToPrefix(t, s, true, /* dots := */ true);
            Scope.putStack(x.buf, s);
        } else {
            M3ID.put(M3.ids, x.buf, t.name);
        }
        if ((offset != 0)) {
            M3Buf.putChar(x.buf, '@');
            M3Buf.putInt(x.buf, offset);
        }
        if ((t.external)) {
            M3Buf.putChar(x.buf, '$');
            M3ID.put(M3.ids, x.buf, t.extName);
        }
    }

    /** initializes a Value.T */
    public static void init(Value.T t, int name, Value.Class c) {
        t.origin = Scanner.offset;
        t.name = name;
        t.extName = M3ID.NoID;
        t.scope = null;
        t.checkDepth = Value.NOT_CHECKED;
        t.class_ = c;
        t.checked = false;
        t.readonly = false;
        t.traced = false;
        t.external = false;
        t.unused = false;
        t.obsolete = false;
        t.up_level = false;

        if ((c != Value.Class.Module)) {
            t.vnext = Value.all;
            Value.all = t;
        }

        t.error = false;
        t.used = false;
        t.declared = false;
        t.inited = false;
        t.compiled = false;
        t.imported = !Scanner.in_main;
        t.exported = false;
        t.exportable = false;
        t.inTypeOf = false;
        t.inToExpr = false;
        t.inToType = false;
    }

    /** == ASSERT (FALSE) */
    public static Expr.T noExpr(Value.T t) {
        Error.msg("Internal compiler error Value.NoExpr");
        throw new AssertError("Value");
    }

    /** == ASSERT (FALSE) */
    public static Type.T noType(Value.T t) {
        Error.msg("Internal compiler error Value.NoType");
        throw new AssertError("Value");
    }

    /** == ASSERT (FALSE) */
    public static void noLoader(Value.T t) {
        throw new AssertError("Value");
    }

    /** returns FALSE */
    public static boolean never(Value.T t) {
        return false;
    }

    /** == noop */
    public static void noInit(Value.T t) {
    }

    /** returns TRUE */
    public static boolean always(Value.T t) {
        return true;
    }

    /** == RETURN Void.T */
    public static Type.T typeVoid(Value.T t) {
        // Sic: returns NIL, not Void.T as the interface says.
        return null;
    }

    /** == RETURN v */
    public static Value.T self(Value.T t) {
        return t;
    }
}
