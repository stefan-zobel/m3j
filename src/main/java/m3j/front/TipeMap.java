// Java port of CM3's m3-sys/m3front/src/misc/TipeMap.i3 and TipeMap.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.Target;
import m3j.util.AssertError;

// File: TipeMap.i3
// Last Modified On Tue Jul  5 14:21:29 PDT 1994 by kalsow

// File: TipeMap.m3
// Last Modified On Tue Jul  5 15:33:54 PDT 1994 by kalsow

public final class TipeMap {
    private TipeMap() {}

    public enum Op {
        /* 0*/ Stop, Mark, PushPtr, Return,
        /* 4*/ Ref, UntracedRef, Proc,
        /* 7*/ Real, Longreal, Extended,
        /*10*/ Int_Field, Word_Field,
        /*12*/ Int_1,  Int_2,  Int_4,  Int_8,
        /*16*/ Word_1, Word_2, Word_4, Word_8,
        /*20*/ Set_1,  Set_2,  Set_3,  Set_4,
        /*24*/ OpenArray_1, OpenArray_2,
        /*26*/ Array_1, Array_2, Array_3, Array_4, Array_5, Array_6, Array_7, Array_8,
        /*34*/ Skip_1,  Skip_2,  Skip_3,  Skip_4,  Skip_5,  Skip_6,  Skip_7,  Skip_8,
        /*42*/ SkipF_1, SkipF_2, SkipF_3, SkipF_4, SkipF_5, SkipF_6, SkipF_7, SkipF_8,
        /*50*/ SkipB_1, SkipB_2, SkipB_3, SkipB_4, SkipB_5, SkipB_6, SkipB_7, SkipB_8
    }

    /** # of bytes of operand in the map; indexed by {@code Op.ordinal()} */
    private static final long[] ArgBytes = {
        0,            // Stop
        0,            // Mark
        0,            // PushPtr
        0,            // Return
        0,            // Ref
        0,            // UntracedRef
        0,            // Proc
        0,            // Real
        0,            // Longreal
        0,            // Extended
        2,            // Int_Field      - bit offset, bit size
        2,            // Word_Field     - bit offset, bit size
        0,            // Int_1
        0,            // Int_2
        0,            // Int_4
        0,            // Int_8
        0,            // Word_1
        0,            // Word_2
        0,            // Word_4
        0,            // Word_8
        1,            // Set_1         - n = number of bytes
        2,            // Set_2
        3,            // Set_3
        4,            // Set_4
        1,            // OpenArray_1   - n = number of open dimensions
        2,            // OpenArray_2
        1,            // Array_1       - n = number of elements
        2,            // Array_2
        3,            // Array_3
        4,            // Array_4
        5,            // Array_5
        6,            // Array_6
        7,            // Array_7
        8,            // Array_8
        0,            // Skip_1
        0,            // Skip_2
        0,            // Skip_3
        0,            // Skip_4
        0,            // Skip_5
        0,            // Skip_6
        0,            // Skip_7
        0,            // Skip_8
        1,            // SkipF_1       - n = number of bytes to skip forward
        2,            // SkipF_2
        3,            // SkipF_3
        4,            // SkipF_4
        5,            // SkipF_5
        6,            // SkipF_6
        7,            // SkipF_7
        8,            // SkipF_8
        1,            // SkipB_1       - n = number of bytes to skip backward
        2,            // SkipB_2
        3,            // SkipB_3
        4,            // SkipB_4
        5,            // SkipB_5
        6,            // SkipB_6
        7,            // SkipB_7
        8             // SkipB_8
    };

    /** Port: indexed by {@code Op.ordinal()}. */
    private static final long[] CursorUpdate = {
        0,                    // Stop
        0,                    // Mark
        -4,                   // PushPtr
        -5,                   // Return
        -8,                   // Ref
        -8,                   // UntracedRef
        -8,                   // Proc
        -9,                   // Real
        -10,                  // Longreal
        -11,                  // Extended
        0,                    // Int_Field
        0,                    // Word_Field
        1 * Target.Byte,      // Int_1
        2 * Target.Byte,      // Int_2
        4 * Target.Byte,      // Int_4
        8 * Target.Byte,      // Int_8
        1 * Target.Byte,      // Word_1
        2 * Target.Byte,      // Word_2
        4 * Target.Byte,      // Word_4
        8 * Target.Byte,      // Word_8
        -3,                   // Set_1
        -3,                   // Set_2
        -3,                   // Set_3
        -3,                   // Set_4
        -2,                   // OpenArray_1
        -2,                   // OpenArray_2
        -1,                   // Array_1
        -1,                   // Array_2
        -1,                   // Array_3
        -1,                   // Array_4
        -1,                   // Array_5
        -1,                   // Array_6
        -1,                   // Array_7
        -1,                   // Array_8
        1 * Target.Byte,      // Skip_1
        2 * Target.Byte,      // Skip_2
        3 * Target.Byte,      // Skip_3
        4 * Target.Byte,      // Skip_4
        5 * Target.Byte,      // Skip_5
        6 * Target.Byte,      // Skip_6
        7 * Target.Byte,      // Skip_7
        8 * Target.Byte,      // Skip_8
        -6,                   // SkipF_1
        -6,                   // SkipF_2
        -6,                   // SkipF_3
        -6,                   // SkipF_4
        -6,                   // SkipF_5
        -6,                   // SkipF_6
        -6,                   // SkipF_7
        -6,                   // SkipF_8
        -7,                   // SkipB_1
        -7,                   // SkipB_2
        -7,                   // SkipB_3
        -7,                   // SkipB_4
        -7,                   // SkipB_5
        -7,                   // SkipB_6
        -7,                   // SkipB_7
        -7                    // SkipB_8
    };

    // Port: ByteList = REF ARRAY OF [0..255] is int[].

    private static boolean busy = false;
    private static int[] bytes = null;
    private static long n_bytes = 0;
    private static long cursor = 0;

    /** begin the construction of a map */
    public static void start() {
        AssertError.check(!busy, "TipeMap");
        busy = true;
        if ((bytes == null)) {
            bytes = new int[100];
        }
        n_bytes = 0;
        cursor = 0;
    }

    /**
     * finish the map, allocate global constant space for it and emit it.  Returns
     * the offset of the generated map in the global constant pool.
     */
    public static long finish(String a, String b, String c, String d) {
        long base, offset;
        if ((n_bytes == 0)) {
            busy = false;
            return -1;
        }

        add(cursor, Op.Stop, 0);

        // allocate space
        base = Module.allocate(n_bytes * Target.Int8.size,
                               Target.Int8.align, true, "type_map");
        CG.comment(base, true, a, b, c, d);

        // generate the bytes
        offset = base;
        for (long i = 0; i <= n_bytes - 1; i++) {
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
     * add '(o, arg)' as the description for the bits
     * at 'offset' in the current map
     */
    public static void add(long offset, TipeMap.Op o, long arg) {
        long x, y, z, n;
        AssertError.check(busy, "TipeMap");

        x = Math.floorDiv(offset, Target.Byte);
        y = Math.floorDiv(cursor, Target.Byte);
        z = x - y;
        if ((z != 0)) {
            // we need to insert a skip
            if ((1 <= z) && (z <= 8)) {
                add(cursor, Op.values()[(int) (Op.Skip_1.ordinal() + z - 1)], 0);
            } else if ((z >= 0)) {
                n = intSize(z);
                add(cursor, Op.values()[(int) (Op.SkipF_1.ordinal() + n - 1)], z);
            } else { // z < 0
                z = -z;
                n = intSize(z);
                add(cursor, Op.values()[(int) (Op.SkipB_1.ordinal() + n - 1)], z);
            }
        }

        // fix the opcode to match the size of the argument
        x = ArgBytes[o.ordinal()];
        if ((x > 0)) {
            n = intSize(arg);
            if ((n > x)) {
                o = Op.values()[(int) (o.ordinal() + n - 1)];
                AssertError.check(ArgBytes[o.ordinal()] == n, "TipeMap");
            }
        }

        // stuff the opcode
        if ((n_bytes >= bytes.length)) {
            expandBytes();
        }
        bytes[(int) n_bytes] = o.ordinal();
        n_bytes++;

        // stuff the operand
        z = arg;
        x = ArgBytes[o.ordinal()];
        if ((n_bytes + x >= bytes.length)) {
            expandBytes();
        }
        while ((x > 0)) {
            bytes[(int) n_bytes] = (int) (z & 0xff);
            n_bytes++;
            z = z >>> 8;
            x--;
        }

        // update the cursor
        x = CursorUpdate[o.ordinal()];
        // Port: x is in [-11..64]; the CASE becomes a switch on int.
        switch ((int) x) {
            case -1 -> { // Op.Array_N
                // must do an explicit set cursor
            }
            case -2 -> // Op.OpenArray_N
                cursor += M3.target.Address.size + arg * M3.target.Integer.size;
            case -3 -> // Op.Set_N
                cursor += arg * Target.Byte;
            case -4 -> // Op.PushPtr
                cursor = 0;
            case -5 -> { // Op.Return
                // must do an explicit set cursor
            }
            case -6 -> // Op.SkipF_N
                cursor += arg * Target.Byte;
            case -7 -> // Op.SkipB_N
                cursor += arg * Target.Byte;
            case -8 -> cursor += M3.target.Address.size;
            case -9 -> cursor += Target.Real.size;
            case -10 -> cursor += M3.target.Longreal.size;
            case -11 -> cursor += M3.target.Extended.size;
            default -> // fixed update
                cursor += x;
        }
    }

    private static long intSize(long i) {
        long n = 0;
        do {
            n++;
            i = i >>> 8;
        } while (!(i == 0));
        return n;
    }

    /** get the current offset in the current map */
    public static long getCursor() {
        return cursor;
    }

    /** get the current offset in the current map without generating Skips */
    public static void setCursor(long x) {
        cursor = x;
    }

    private static void expandBytes() {
        int[] new_ = new int[2 * bytes.length];
        System.arraycopy(bytes, 0, new_, 0, bytes.length);
        bytes = new_;
    }
}
