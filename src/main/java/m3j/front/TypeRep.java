// Java port of CM3's m3-sys/m3front/src/types/TypeRep.i3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

// File: TypeRep.i3
// Last Modified On Wed Nov 16 14:38:18 PST 1994 by kalsow
//      Modified On Fri Dec 21 00:53:14 1990 by muller

/**
 * The representation of types.
 *
 * <p>Port: TT = M3.Type is {@link Type.T}; the revelation of M3.Type
 * (its fields and methods) is the class {@link Type.T}. The procedures
 * are implemented by the module Type (Type EXPORTS Type, TypeRep), so they
 * forward to package-private methods of {@link Type}.
 */
public final class TypeRep {
    private TypeRep() {}

    public static final long NO_UID = -1;

    public static final long NO_SCC = 0;

    /** initialize the shared fields of a Type.T */
    public static void init(Type.T t, Type.Class c) {
        Type.init(t, c);
    }

    /** == RETURN FALSE */
    public static boolean neverEqual(Type.T a, Type.T b, Type.Assumption x) {
        return Type.neverEqual(a, b, x);
    }

    /** == RETURN FALSE */
    public static boolean noSubtypes(Type.T a, Type.T b) {
        return Type.noSubtypes(a, b);
    }

    /** == initialize Size(t) bits to zero */
    public static void initToZeros(Type.T t, boolean zeroed) {
        Type.initToZeros(t, zeroed);
    }

    /** == TypeMap.Add (offset, Op.{Untraced}Ref, 0) */
    public static void genRefMap(Type.T t, long offset, long size, boolean refs_only) {
        Type.genRefMap(t, offset, size, refs_only);
    }

    /** == TypeDesc.AddO (Op.{Untraced}Ref); TypeDesc.AddI (UID(t)) */
    public static void genRefDesc(Type.T t) {
        Type.genRefDesc(t);
    }

    /**
     * == RETURN (offset MOD t.alignment = 0)
     * To preclude word-boundary-straddle, this assumes t's size
     * is no greater than its alignment.
     */
    public static boolean scalarNoStraddle(Type.T t, long offset, boolean IsEltOrField) {
        return Type.scalarNoStraddle(t, offset, IsEltOrField);
    }

    /** == RETURN (offset MOD Target.Address.align = 0) */
    public static boolean addrNoStraddle(Type.T t, long offset, boolean IsEltOrField) {
        return Type.addrNoStraddle(t, offset, IsEltOrField);
    }
}
