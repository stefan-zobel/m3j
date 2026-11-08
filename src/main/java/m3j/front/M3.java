// Java port of CM3's m3-sys/m3front/src/misc/M3.i3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.M3ID;
import m3j.middle.M3RT;
import m3j.middle.Target;
import m3j.middle.TargetMap;

/**
 * This interface defines some base object types so that circular
 * dependencies between other interfaces may be avoided.
 *
 * <p>Port: the opaque types Scope, Stmt, Expr, Value, Type, ExSet,
 * ExSetList and EqAssumption are the classes {@link Scope.T},
 * {@link Stmt.T}, {@link Expr.T}, {@link Value.T}, {@link Type.T},
 * {@link ESet.T}, {@link ESet.TList} and {@link Type.Assumption}. Flag is
 * {@code boolean}. The state of the m3middle modules that the front end
 * uses is held in the static fields {@link #target}, {@link #targetMap},
 * {@link #rt} and {@link #ids}; the driver sets them before a compilation.
 */
public final class M3 {
    private M3() {}

    /** Port: the variables of Target. */
    public static Target.State target;

    /** Port: the variables of TargetMap. */
    public static TargetMap.State targetMap;

    /** Port: the variables of M3RT. */
    public static M3RT.State rt;

    /** Port: the variables of M3ID. */
    public static M3ID.State ids;

    /** an optionally module qualified name (qualified identifier) */
    public static final class QID {
        public int module = M3ID.NoID;
        public int item = M3ID.NoID;

        public QID() {}

        public QID copy() {
            QID r = new QID();
            r.module = module;
            r.item = item;
            return r;
        }
    }

    //------------------------------------------------------------- AST nodes ---

    public static class Node {
        public long origin;
    }

    public static class ValueNode extends Node {
        public Value.T next = null;
    }

    //--------------------------------------------------------- type checking ---

    /** the "global state" that is passed around during type checking */
    public static final class CheckState {
        public boolean raises_others;
        public ESet.TList ok_to_raise;
        public ESet.TList no_error;
        public Jmpbufs.CheckState jmpbufs = new Jmpbufs.CheckState();

        public CheckState() {}

        public CheckState copy() {
            CheckState r = new CheckState();
            r.raises_others = raises_others;
            r.ok_to_raise = ok_to_raise;
            r.no_error = no_error;
            r.jmpbufs = jmpbufs.copy();
            return r;
        }

        /** Port: the assignment {@code this := x}. */
        public void assign(CheckState x) {
            raises_others = x.raises_others;
            ok_to_raise = x.ok_to_raise;
            no_error = x.no_error;
            jmpbufs = x.jmpbufs.copy();
        }
    }

    /** Port: a constant; never modify it, assign its copy. */
    public static final CheckState OuterCheckState = new CheckState();

    //-------------------------------------------------------- fingerprinting ---

    /**
     * "fp" methods must either assign a non-NIL value to "tag"
     * or build a string in "buf".  They must also set "n_nodes" and
     * either "nodes" or "others".  If "n_nodes <= NUMBER (nodes)",
     * "nodes" must be used, otherwise "others" is used.
     */
    public static final class FPInfo {
        public String tag;
        public M3Buf.T buf;
        public long n_nodes;
        public Type.T[] nodes = new Type.T[6];
        public Type.T[] others;

        public FPInfo() {}
    }
}
