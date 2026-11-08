// Java port of CM3's m3-sys/m3middle/src/M3CG_Binary.i3 and M3CG_Binary.m3.
// Original: Copyright 1996-2000, Critical Mass, Inc.
// Distributed under the Critical Mass license in licenses/COPYRIGHT-CMASS.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.middle;

/**
 * The operations of the binary intermediate code.
 *
 * <p>NOTE: If you alter this file substantively, do the following:
 * <ol>
 * <li>Please update CONST Version, so front/back end mismatches will be
 *     detected.</li>
 * <li>Also make matching changes to cm3/m3-sys/m3cggen/src/Main.m3.</li>
 * <li>Recompile cm3, m3cggen and lots of packages they depend on, in order.
 *     cm3/scripts/do-cm3-front.sh will do it.</li>
 * <li>Run the executable m3cggen, copying its std output to
 *     cm3/m3-sys/m3cc/gcc/gcc/m3cg/m3cg.h.</li>
 * <li>Recompile m3cc.</li>
 * </ol>
 * This will create a compatible cm3/cm3cg pair.
 *
 * <pre>
 *   The binary intermediate code has the following format:
 *
 *     &lt;I-code&gt;     ::= &lt;version&gt; { &lt;cmd&gt; }
 *     &lt;version&gt;    ::= I(Version)
 *     &lt;cmd&gt;        ::= &lt;method&gt; { &lt;arg&gt; }
 *     &lt;method&gt;     ::= I(ORD(Op.&lt;method&gt;))
 *     &lt;arg&gt;        ::= &lt;int&gt; | &lt;text&gt; | &lt;name&gt; | &lt;typeUID&gt; | &lt;bool&gt;
 *                      &lt;callconv&gt; | &lt;var&gt; | &lt;proc&gt; | &lt;type&gt; | &lt;label&gt;
 *                      &lt;float&gt; | &lt;casetbl&gt; | &lt;sign&gt;
 *     &lt;int:i&gt;      ::= I(ORD(i))
 *     &lt;text:t&gt;     ::= I(Text.Length(t)) S(t)
 *     &lt;name:n&gt;     ::= I(Text.Length(M3ID.ToText(n))) S(M3ID.ToText(n))
 *     &lt;typeUID&gt;    ::= I(ORD(uid))
 *     &lt;bool:b&gt;     ::= I(ORD(b))
 *     &lt;callconv:c&gt; ::= I(c.m3cg_id)
 *     &lt;var:v&gt;      ::= I(v.uid)
 *     &lt;proc:p&gt;     ::= I(p.uid)
 *     &lt;type:t&gt;     ::= I(ORD(t))
 *     &lt;label:l&gt;    ::= I(l)
 *     &lt;float:f&gt;    ::= ??
 *     &lt;casetbl:t&gt;  ::= I(NUMBER(t)) I(t[0]) I(t[1]) ...
 *     &lt;sign:s&gt;     ::= I(ORD(s))
 *
 *   Where I(x) is the variable length encoding of the integer value 'x'
 *   and S(x) are the bytes of the string 'x'.
 *
 * </pre>
 */
public final class M3CG_Binary {
    private M3CG_Binary() {}

    /** version 1.20 (Please update Version if you change the codes in this file.) */
    public static final long Version = 0x120;

    public enum Op {
        begin_unit, end_unit, import_unit, export_unit, set_source_file,
        set_source_line, declare_typename, declare_array,
        declare_open_array, declare_enum, declare_enum_elt,
        declare_packed, declare_record, declare_field, declare_set,
        declare_subrange, declare_pointer, declare_indirect,
        declare_proctype, declare_formal, declare_raises, declare_object,
        declare_method, declare_opaque, reveal_opaque, declare_exception,
        set_runtime_proc, import_global,
        declare_segment, bind_segment, declare_global, declare_constant,
        declare_local, declare_param, declare_temp, free_temp, begin_init,
        end_init, init_int, init_proc, init_label, init_var, init_offset,
        init_chars, init_float, import_procedure, declare_procedure,
        begin_procedure, end_procedure, begin_block, end_block,
        note_procedure_origin, set_label, jump, if_true, if_false,
        if_eq, if_ne, if_gt, if_ge, if_lt, if_le,       // file only; in-memory uses if_compare
        case_jump, exit_proc, load,
        load_address, load_indirect, store, store_indirect,
        load_nil, load_integer, load_float,
        eq, ne, gt, ge, lt,  le,                        // file only; in-memory uses compare
        add, subtract, multiply, divide, negate, abs, max,
        min,
        round, trunc, floor, ceiling,                   // file only; in-memory uses cvt_int
        cvt_float, div, mod, set_union,
        set_difference, set_intersection, set_sym_difference, set_member,
        set_eq, set_ne, set_lt, set_le, set_gt, set_ge, // file only; in-memory uses set_compare
        set_range,
        set_singleton, not, and, or, xor, shift, shift_left, shift_right,
        rotate, rotate_left, rotate_right, widen, chop, extract, extract_n,
        extract_mn, insert, insert_n, insert_mn, swap, pop, copy_n, copy,
        zero_n, zero, loophole, abort, check_nil, check_lo, check_hi,
        check_range, check_index, check_eq, add_offset, index_address,
        start_call_direct, call_direct, start_call_indirect, call_indirect,
        start_try, end_try, invoke_direct, invoke_indirect, landing_pad,
        pop_param, pop_struct, pop_static_link,
        load_procedure, load_static_link, comment,
        store_ordered, load_ordered, exchange, compare_exchange, fence,
        fetch_and_add,      // file only; in-memory uses fetch_and_op
        fetch_and_sub,      // file only; in-memory uses fetch_and_op
        fetch_and_or,       // file only; in-memory uses fetch_and_op
        fetch_and_and,      // file only; in-memory uses fetch_and_op
        fetch_and_xor,      // file only; in-memory uses fetch_and_op
        widechar_size ,     // file only; communicates byte size WIDECHAR
        set_error_handler,  // in-memory only; contains a pointer, so silently skipped in files
        compare,            // in-memory only; file converts to eq/ne/etc.
        cvt_int,            // in-memory only; file converts to trunc/ceiling/etc.
        fetch_and_op,       // in-memory only; file converts to fetch_and_add/fetch_and_sub/etc.
        if_compare,         // in-memory only; file converts to if_eq/if_ne/etc.
        set_compare         // in-memory only; file converts to set_eq/set_ne/etc.
    }

    // Integers are encoded as sequences of unsigned bytes, [0..255].
    // The length and format of the encoding is determined by the
    // first byte.  The special first-byte values are:

    /** Int1,x        =&gt; x */
    public static final int Int1  = 255;
    /** NInt1,x       =&gt; -x */
    public static final int NInt1 = 254;
    /** Int2,x,y      =&gt; x + 2^8*y */
    public static final int Int2  = 253;
    /** NInt2,x,y     =&gt; - (Int2,x,y) */
    public static final int NInt2 = 252;
    /** Int4,a,b,c,d  =&gt; a + 2^8*b + 2^16*c + 2^24*d */
    public static final int Int4  = 251;
    /** NInt4,a,b,c,d =&gt; - (Int4,a,b,c,d) */
    public static final int NInt4 = 250;
    /** Int8,a,...,h  =&gt; a + 2^8*b + ... + 2^56*h */
    public static final int Int8  = 249;
    /** NInt8,a,...,h =&gt; - (Int8,a,...,h) */
    public static final int NInt8 = 248;
    public static final int LastRegular = 247;
    // Integer values in [0..247] are simply passed thru as single bytes

    /**
     * OpText: the name of the operation. Port: the names in the Map of the
     * original are those of the enumeration constants, which the original
     * asserts for the ops; Map is omitted.
     */
    public static String opText(Op op) {
        return op.name();
    }
}
