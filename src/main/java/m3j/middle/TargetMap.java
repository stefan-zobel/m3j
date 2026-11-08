// Java port of CM3's m3-sys/m3middle/src/TargetMap.i3 and TargetMap.m3.
// Original: Copyright (C) 1993, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.middle;

import m3j.middle.Target.CGType;

/**
 * Modula-3 target description.
 *
 * <p>This interface defines some useful arrays that map over
 * target's types.
 *
 * <p>Port: the module variables are the fields of {@link State}; the arrays
 * indexed by CGType or Precision are indexed by the ordinal.
 */
public final class TargetMap {
    private TargetMap() {}

    /** The module variables of TargetMap. */
    public static final class State {
        // CG_Align, CG_Align_bytes, CG_Size, and CG_Bytes each extract a
        // column from the type definition table in Target.  They provide a
        // different view on the same data.

        /** Minimum type alignment in bits. */
        public final long[] CG_Align = new long[CGType.values().length];

        /** Minimum type alignment in 8-bit bytes. */
        public final long[] CG_Align_bytes = new long[CGType.values().length];

        /** Minimum type size in bits. */
        public final long[] CG_Size = new long[CGType.values().length];

        /** Minimum type size in 8-bit bytes. */
        public final long[] CG_Bytes = new long[CGType.values().length];

        // Word_types, Integer_types, and Float_types select subsets from
        // the rows of the same type definition table.

        /** Lists the 4 primitive unsigned integer types: 8-, 16-, 32-, and 64-bit. */
        public final Target.Int_type[] Word_types = new Target.Int_type[4];

        /** Lists the 4 primitive signed integer types: 8-, 16-, 32-, and 64-bit. */
        public final Target.Int_type[] Integer_types = new Target.Int_type[4];

        /** List the 3 primitive floating-point types: short, long, and extended. */
        public final Target.Float_type[] Float_types = new Target.Float_type[Target.Precision.values().length];
    }

    /**
     * Initializes the maps from the values exported by Target.
     *
     * <p>This is called from Target.Init to resolve initialization order
     * dependencies between the two modules.
     */
    static void init(State s, Target.State t) {
        initI(s, CGType.Int8,   Target.Int8);
        initI(s, CGType.Word8,  Target.Word8);
        initI(s, CGType.Int16,  Target.Int16);
        initI(s, CGType.Word16, Target.Word16);
        initI(s, CGType.Int32,  Target.Int32);
        initI(s, CGType.Word32, Target.Word32);
        initI(s, CGType.Int64,  t.Longint);
        initI(s, CGType.Word64, t.Long);
        initF(s, CGType.Reel,   Target.Real);
        initF(s, CGType.LReel,  t.Longreal);
        initF(s, CGType.XReel,  t.Extended);
        initI(s, CGType.Addr,   t.Address);
        initI(s, CGType.Struct, Target.Void);
        initI(s, CGType.Void,   Target.Void);

        s.Word_types[0] = Target.Word8.copy();
        s.Word_types[1] = Target.Word16.copy();
        s.Word_types[2] = Target.Word32.copy();
        s.Word_types[3] = Target.Word64.copy();

        s.Integer_types[0] = Target.Int8.copy();
        s.Integer_types[1] = Target.Int16.copy();
        s.Integer_types[2] = Target.Int32.copy();
        s.Integer_types[3] = Target.Int64.copy();
    }

    private static void initI(State s, CGType type, Target.Int_type x) {
        int t = type.ordinal();
        s.CG_Align[t] = x.align;
        s.CG_Align_bytes[t] = x.align / Target.Byte;
        s.CG_Size[t] = x.size;
        s.CG_Bytes[t] = x.bytes;
    }

    private static void initF(State s, CGType type, Target.Float_type x) {
        int t = type.ordinal();
        s.CG_Align[t] = x.align;
        s.CG_Align_bytes[t] = x.align / Target.Byte;
        s.CG_Size[t] = x.size;
        s.CG_Bytes[t] = x.bytes;
        s.Float_types[x.pre.ordinal()] = x.copy();
    }
}
