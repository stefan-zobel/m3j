// Java port of CM3's m3-sys/m3front/src/misc/TipeDesc.i3 and TipeDesc.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.TInt;
import m3j.middle.TWord;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: TipeDesc.i3
// Last Modified On Tue Jul  5 15:39:08 PDT 1994 by kalsow

// File: TipeDesc.m3
// Last Modified On Tue Jul  5 16:45:55 PDT 1994 by kalsow

/**
 * A "type description" is a stream of bytes.  It represents a Modula-3
 * type.  It begins with an integer equal to the number of graph nodes,
 * then a preorder traversal of the corresponding AST nodes.  Each of the
 * type constructors is represented by a single byte (=ORD(Op)) and zero
 * or more operands.
 *
 * <p>Integer values are encoded in a variable-length byte stream. The
 * first byte contains two flag bits: the sign bit (s=16_80) and
 * the special bit (x=16_40).  The rest of the first byte (v=16_3f)
 * represents the values 0..16_3f.  So, given a stream x0, x1, x2, ...
 * of 0..255 values where x0 is split into the fields (s/x/v), the
 * represented integer value is:
 *
 * <pre>
 *        (x=0)             value = sign(s) * v
 *        (x=1) (v=1..8)    value = sign(s) * (SUM(i=1 to v) xi&lt;&lt;8*i)
 *  (s=0) (x=1) (v=16_3e)   value = +16_7fffffff  = +2^31 - 1
 *  (s=1) (x=1) (v=16_3e)   value = -16_80000000  = -2^31
 *  (s=0) (x=1) (v=16_3f)   value = +16_7fffffffffffffff  = +2^63 - 1
 *  (s=1) (x=1) (v=16_3f)   value = -16_8000000000000000  = -2^63
 * </pre>
 *
 * <p>where sign(0)=+1, sign(1)=-1.
 */
public final class TipeDesc {
    private TipeDesc() {}

    /** This must be kept in sync with type RTTipe.m3.Op, in runtime. */
    public enum Op {
        // opcode         -- op  --- operands -------------------
        Address,       // 00
        Array,         // 01, #elements: INT, element: TYPE
        Boolean,       // 02
        Cardinal,      // 03
        Char,          // 04
        Enum,          // 05, #elements: INT
        Extended,      // 06
        Integer,       // 07
        Longcard,      // 08
        Longint,       // 09
        Longreal,      // 0a
        Null,          // 0b
        Object,        // 0c, #fields: INT, {fields: TYPE}
        OpenArray,     // 0d, #dimensions: INT, element: TYPE
        Packed,        // 0e, bit size: INT, base type: TYPE
        Proc,          // 0f
        Real,          // 10
        Record,        // 11, #fields: INT, {fields: TYPE}
        Ref,           // 12, self id: UID
        Refany,        // 13
        Set,           // 14, #elements: INT
        Subrange,      // 15, min, max: INT
        UntracedRef,   // 16, self id: UID
        // Widechar is denoted as Enum, with #elements = 16_10000 or 16_110000
        //   This could be fixed by a coordinated change, here, in
        //   Enumtype.m3, and RTTipe.m3, but would invalidate
        //   compiled code.
        // Widechar Tipe.
        OldN,          // 17, node #: INT
        Old0           // 18
    } // Old1, Old2, ... Old(255-ORD(Old0))

    // Port: ByteList = REF ARRAY OF [0..255] is int[];
    // TypeList = REF ARRAY OF Type.T is Type.T[].

    private static boolean busy = false;
    private static int[] bytes = null;
    private static long n_bytes = 0;
    private static Type.T[] types = null;
    private static long n_types = 0;

    /** begin the construction of a description */
    public static void start() {
        AssertError.check(!busy, "TipeDesc");
        busy = true;
        if ((bytes == null)) {
            bytes = new int[200];
            types = new Type.T[100];
        }
        n_bytes = 0;
        n_types = 0;
    }

    /**
     * finish the description, allocate global constant space for it and emit it.
     * Returns the offset of the generated map in the global constant pool.
     */
    public static long finish(String a, String b, String c, String d) {
        long base, offset, n_data_bytes;
        if ((n_bytes == 0)) {
            busy = false;
            return -1;
        }

        // add the op count
        n_data_bytes = n_bytes;
        addI(n_types);

        // allocate space
        base = Module.allocate(n_bytes * Target.Int8.size,
                               Target.Int8.align, true, "type_desc");
        CG.comment(base, true, a, b, c, d);

        // emit the op count
        offset = base;
        for (long i = n_data_bytes; i <= n_bytes - 1; i++) {
            CG.init_intt(offset, Target.Int8.size, bytes[(int) i], /* is_const := */ true);
            offset += Target.Int8.size;
        }

        // generate the bytes
        for (long i = 0; i <= n_data_bytes - 1; i++) {
            CG.init_intt(offset, Target.Int8.size, bytes[(int) i], /* is_const := */ true);
            offset += Target.Int8.size;
        }

        busy = false;
        return base;
    }

    public static long finish(String a, String b, String c) {
        return finish(a, b, c, null);
    }

    public static long finish(String a, String b) {
        return finish(a, b, null, null);
    }

    public static long finish(String a) {
        return finish(a, null, null, null);
    }

    public static long finish() {
        return finish(null, null, null, null);
    }

    /**
     * add operator 'o' to the current description.  If 'type' has already been
     * seen, adds an 'Old' opcode and returns FALSE, otherwise returns TRUE.
     */
    public static boolean addO(TipeDesc.Op o, Type.T type) {
        final long MaxSmall = 255 - Op.Old0.ordinal();
        Type.T tt;
        for (long i = 0; i <= n_types - 1; i++) {
            tt = types[(int) i];
            if ((tt != null) && Type.isEqual(tt, type, null)) {
                if ((i <= MaxSmall)) {
                    stuff(Op.Old0.ordinal() + i);
                } else {
                    stuff(Op.OldN.ordinal());
                    addI(i);
                }
                if ((n_types >= types.length)) {
                    expandTypes();
                }
                types[(int) n_types] = null;
                n_types++;
                return false;
            }
        }
        if ((n_types >= types.length)) {
            expandTypes();
        }
        types[(int) n_types] = type;
        n_types++;
        stuff(o.ordinal());
        return true;
    }

    /** add the 32-bit type 'uid' to the current description. */
    public static void addU(long i) {
        stuff(i & 0xff);
        i = i >>> 8;
        stuff(i & 0xff);
        i = i >>> 8;
        stuff(i & 0xff);
        i = i >>> 8;
        stuff(i & 0xff);
    }

    /** add integer 'i' to the current description (max 32 bits!) */
    public static void addI(long i) {
        if ((0 <= i)) {
            if ((i <= 0x3f)) {
                stuff(i);
            } else if ((i <= 0xff)) {
                stuff(0x41);
                stuff(i);
            } else if ((i == 0x7fffffffL)) {
                stuff(0x7e);
            } else if ((i == Long.MAX_VALUE)) {
                stuff(0x7f);
            } else {
                addBigInt(i);
            }
        } else { // i < 0
            if ((i >= -0x3f)) {
                stuff(0x80 - i);
            } else if ((i >= -0xff)) {
                stuff(0xc1);
                stuff(i);
            } else if ((i == -0x7fffffffL - 1)) {
                stuff(0xfe);
            } else if ((i == Long.MIN_VALUE)) {
                stuff(0xff);
            } else {
                addBigInt(i);
            }
        }
    }

    /** Port: indexed by 0 (FALSE) and 1 (TRUE). */
    private static final long[] Sign = {0x40, 0xc0};

    private static void addBigInt(long i) {
        long key, n_bytes;
        long[] x = new long[8]; // BYTESIZE(INTEGER)
        key = Sign[(i < 0) ? 1 : 0];
        if ((i < 0)) {
            i = 0 - i;
        }

        // extract the bytes
        n_bytes = 0;
        while ((i != 0)) {
            x[(int) n_bytes] = i & 0xff;
            n_bytes++;
            i = i >>> 8;
        }

        // stuff'm
        stuff(key + n_bytes);
        for (int j = 0; j <= n_bytes - 1; j++) {
            stuff(x[j]);
        }
    }

    /** add a variable length target integer 'i' to the description */
    public static void addX(int[] i) {
        long[] x = new long[1];
        if (TInt.toInt(i, x)) {
            addI(x[0]);
        } else if (TInt.EQ(i, Target.Int32.max)) {
            stuff(0x7e);
        } else if (TInt.EQ(i, Target.Int32.min)) {
            stuff(0xfe);
        } else if (TInt.EQ(i, Target.Int64.max)) {
            stuff(0x7f);
        } else if (TInt.EQ(i, Target.Int64.min)) {
            stuff(0xff);
        } else {
            addBigX(i);
        }
    }

    private static void addBigX(int[] ii) {
        int[] x = new int[TInt.NUMBER]; // [0..LAST (Target.Int)]
        long key, n_bytes;
        int[] i = TInt.copy(ii);
        key = Sign[TInt.LT(i, TInt.Zero) ? 1 : 0];
        if ((key != 0x40)) {
            TWord.subtract(TInt.Zero, ii, i);
            TInt.extend(i, (int) M3.target.Integer.bytes, i);
        }

        // extract the bytes
        n_bytes = TInt.toUnsignedBytes(i, x);

        // stuff'm
        stuff(key + n_bytes);
        for (int j = 0; j <= n_bytes - 1; j++) {
            stuff(x[j]);
        }
    }

    private static void stuff(long i) {
        AssertError.check(busy, "TipeDesc");
        if ((n_bytes >= bytes.length)) {
            expandBytes();
        }
        bytes[(int) n_bytes] = (int) (i & 0xff);
        n_bytes++;
    }

    private static void expandBytes() {
        int[] new_ = new int[2 * bytes.length];
        System.arraycopy(bytes, 0, new_, 0, bytes.length);
        bytes = new_;
    }

    private static void expandTypes() {
        Type.T[] new_ = new Type.T[2 * types.length];
        System.arraycopy(types, 0, new_, 0, types.length);
        types = new_;
    }

    public static void reset() {
        busy = false;
        n_bytes = 0;
        n_types = 0;
        if ((types != null)) {
            for (int i = 0; i < types.length; i++) {
                types[i] = null;
            }
        }
    }
}
