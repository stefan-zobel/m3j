// Java port of CM3's m3-sys/m3middle/src/M3RT.i3 and M3RT.m3.
// Original: Copyright (C) 1993, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.middle;

import m3j.util.AssertError;

/**
 * Modula-3 runtime interface.
 *
 * <p>This interface defines the data structures needed by the compiler
 * to interface to the Modula-3 runtime.
 *
 * <p>Note that all runtime structures have their fields naturally aligned.
 * see also m3-libs/m3core/src/runtime/ex*
 *
 * <p>Port: the module variables are the fields of {@link State}, which
 * {@link Target#init} initializes through {@link #init}.
 */
public final class M3RT {
    private M3RT() {}

    public static final long CL_marker_value = -1;

    public enum HandlerClass { Except, ExceptElse, Finally, FinallyProc, Raises, RaisesNone, Lock }

    /** Reference types are represented by global variables call "typecells". */
    public enum TypeKind { Unknown, Ref, Obj, Array }

    // bit offsets and sizes of the fields within a REF's header word
    public static final long RH_forwarded_offset = 0;
    public static final long RH_forwarded_size   = 1;
    public static final long RH_typecode_offset  = RH_forwarded_offset + RH_forwarded_size;
    public static final long RH_typecode_size    = 20;
    public static final long RH_dirty_offset     = RH_typecode_offset + RH_typecode_size;
    public static final long RH_dirty_size       = 1;
    public static final long RH_gray_offset      = RH_dirty_offset + RH_dirty_size;
    public static final long RH_gray_size        = 1;

    // builtin, constant typecodes
    public static final long NULL_typecode          = 0;
    public static final long TEXT_typecode          = 1;
    public static final long ADDRESS_typecode       = 2;
    public static final long REFANY_typecode        = 3;
    public static final long ROOT_typecode          = 4;
    public static final long UNTRACED_ROOT_typecode = 5;
    public static final long MUTEX_typecode         = 6;

    /** The module variables of M3RT (offsets and sizes in bits). */
    public static final class State {
        public long CL_marker; // : INTEGER
        public long CL_proc; // : PROCEDURE()
        public long CL_frame; // : ADDRESS
        public long CL_SIZE;
        public long ED_uid; // : INTEGER (ExceptionUID)
        public long ED_name; // : ADDRESS (String)
        public long ED_implicit; // : INTEGER (boolean)
        public long ED_SIZE;
        public long EX_class; // : CHAR(HandlerClass)
        public long EX_outermost; // : CHAR(BOOLEAN)
        public long EX_end_of_list; // : CHAR(BOOLEAN)
        public long EX_pad; // : CHAR(BOOLEAN)
        public long EX_start; // : ADDRESS
        public long EX_stop; // : ADDRESS
        public long EX_excepts; // : ADDRESS
        public long EX_offset; // : INTEGER
        public long EX_SIZE;
        public long EA_exception; // : ADDRESS
        public long EA_arg; // : ADDRESS
        public long EA_module; // : ADDRESS
        public long EA_line; // : INTEGER
        public long EA_pc; // : ADDRESS
        public long EA_info0; // : ADDRESS
        public long EA_info1; // : ADDRESS
        public long EA_un_except; // : ADDRESS
        public long EA_un_arg; // : ADDRESS
        public long EA_SIZE;
        public long EF_next; // : ADDRESS
        public long EF_class; // : INTEGER(HandlerClass)
        public long EF_SIZE;
        public long EF1_handles; // : ADDRESS
        public long EF1_info; // : RTException.Activation
        public long EF1_jmpbuf; // : jmp_buf
        public long EF1_SIZE;
        public long EF2_handler; // : ADDRESS (PROC)
        public long EF2_frame; // : ADDRESS
        public long EF2_info; // : ADDRESS
        public long EF2_SIZE;
        public long EF3_raises; // : ADDRESS
        public long EF3_SIZE;
        public long EF4_mutex; // : MUTEX
        public long EF4_SIZE;
        public long TC_typecode; // : INTEGER
        public long TC_selfID; // : INTEGER
        public long TC_fp; // : 64-bit fingerprint
        public long TC_traced; // : BYTE = ORD (BOOLEAN)
        public long TC_kind; // : BYTE = ORD (TypeKind)
        public long TC_link_state; // : BYTE
        public long TC_dataAlignment; // : BYTE
        public long TC_dataSize; // : INTEGER
        public long TC_type_map; // : ADDRESS
        public long TC_gc_map; // : ADDRESS
        public long TC_type_desc; // : ADDRESS
        public long TC_initProc; // : PROC()
        public long TC_brand; // : ADDRESS
        public long TC_name; // : ADDRESS
        public long TC_next; // : ADDRESS
        public long TC_SIZE;
        public long TC_ALIGN;
        public long OTC_parentID; // : INTEGER
        public long OTC_linkProc; // : PROC()
        public long OTC_dataOffset; // : INTEGER
        public long OTC_methodOffset; // : INTEGER
        public long OTC_methodSize; // : INTEGER
        public long OTC_defaultMethods; // : ADDRESS
        public long OTC_parent; // : ADDRESS
        public long OTC_SIZE;
        public long ATC_nDimensions; // : INTEGER
        public long ATC_elementSize; // : INTEGER
        public long ATC_SIZE;
        public long RV_lhs_id; // : INTEGER
        public long RV_rhs_id; // : INTEGER
        public long RV_SIZE;
        public long OA_elt_ptr; // : ADDRESS
        public long OA_sizes; // : ARRAY [0..depth] OF INT
        public long OA_size_0; // : INTEGER
        public long OA_size_1; // : INTEGER
        public long MI_file; // : ADDRESS
        public long MI_type_cells; // : ADDRESS
        public long MI_type_cell_ptrs; // : ADDRESS
        public long MI_full_rev; // : ADDRESS
        public long MI_part_rev; // : ADDRESS
        public long MI_proc_info; // : ADDRESS
        public long MI_try_scopes; // : ADDRESS
        public long MI_var_map; // : ADDRESS
        public long MI_gc_map; // : ADDRESS
        public long MI_imports; // : ADDRESS
        public long MI_link_state; // : INTEGER
        public long MI_binder; // : PROC()
        public long MI_gc_flags; // : INTEGER
        public long MI_SIZE;
        public long II_import; // : ADDRESS (ModulePtr)
        public long II_binder; // : ADDRESS (Binder)
        public long II_next; // : ADDRESS (ImportPtr)
        public long II_SIZE;
        public long PI_proc; // : ADDRESS
        public long PI_name; // : ADDRESS
        public long PI_SIZE;
        public long MUTEX_acquire; // : PROC()
        public long MUTEX_release; // : PROC()
    }

    /** Initializes the values above to reflect the values exported by Target. */
    static void init(State s, Target.State t) {
        long IP = t.Integer.pack; // 32 or 64, same as Target.Address.pack
        long AP = t.Address.pack; // 32 or 64, same as Target.Integer.pack
        long CP = Target.Char.pack;    // 8
        // closure offsets
        s.CL_marker = 0;                // : INTEGER
        s.CL_proc   = s.CL_marker + IP;   // : PROCEDURE()
        s.CL_frame  = s.CL_proc   + AP;   // : ADDRESS
        s.CL_SIZE   = s.CL_frame  + AP;
    
        // exception descriptors
        s.ED_uid      = 0;                // : INTEGER (ExceptionUID)
        s.ED_name     = s.ED_uid + IP;      // : ADDRESS (String)
        s.ED_implicit = s.ED_name + AP;     // : INTEGER (boolean)
        s.ED_SIZE     = s.ED_implicit + IP;
    
        // exception scope descriptors
        s.EX_class       = 0;                   // : CHAR(HandlerClass)
        s.EX_outermost   = s.EX_class + CP;       // : CHAR(BOOLEAN)
        s.EX_end_of_list = s.EX_outermost + CP;   // : CHAR(BOOLEAN)
        s.EX_pad         = s.EX_end_of_list + CP; // : CHAR(BOOLEAN)
        s.EX_start       = Math.max(s.EX_pad + CP, s.EX_class + AP); // : ADDRESS
        s.EX_stop        = s.EX_start + AP;       // : ADDRESS
        s.EX_excepts     = s.EX_stop  + AP;       // : ADDRESS
        s.EX_offset      = s.EX_excepts + AP;     // : INTEGER
        s.EX_SIZE        = s.EX_offset + IP;
    
        // exception info record
        s.EA_exception   = 0;                   // : ADDRESS
        s.EA_arg         = s.EA_exception + AP;   // : ADDRESS
        s.EA_module      = s.EA_arg + AP;         // : ADDRESS
        s.EA_line        = s.EA_module + AP;      // : INTEGER
        s.EA_pc          = s.EA_line + IP;        // : ADDRESS
        s.EA_info0       = s.EA_pc + AP;          // : ADDRESS
        s.EA_info1       = s.EA_info0 + AP;       // : ADDRESS
        s.EA_un_except   = s.EA_info1 + AP;       // : ADDRESS
        s.EA_un_arg      = s.EA_un_except + AP;   // : ADDRESS
        s.EA_SIZE        = s.EA_un_arg + AP;
    
        // all exception frames  (== all of a RaisesNone frame)
        s.EF_next        = 0;                   // : ADDRESS
        s.EF_class       = s.EF_next + AP;        // : INTEGER(HandlerClass)
        s.EF_SIZE        = s.EF_class + IP;
    
        // Except, ExceptElse, and Finally  frames
        s.EF1_handles    = s.EF_SIZE;             // : ADDRESS
        s.EF1_info       = s.EF1_handles + AP;    // : RTException.Activation
        s.EF1_jmpbuf     = s.EF1_info + s.EA_SIZE;  // : jmp_buf
    
        // TODO: Teach backend about jmp_buf and have backend do this layout
        AssertError.check(Target.Alloca_jmpbuf, "M3RT");
        if (Target.Alloca_jmpbuf) {
          s.EF1_SIZE       = s.EF1_jmpbuf + AP;
        } else {
          AssertError.check(Target.Jumpbuf_size > 0, "M3RT");
          s.EF1_jmpbuf     = roundUp (s.EF1_jmpbuf, 128); // : jmp_buf
          s.EF1_SIZE       = s.EF1_jmpbuf + Target.Jumpbuf_size;
        }
    
        // FinallyProc frames
        s.EF2_handler    = s.EF_SIZE;            // : ADDRESS (PROC)
        s.EF2_frame      = s.EF2_handler + AP;   // : ADDRESS
        s.EF2_info       = s.EF2_frame + AP;     // : ADDRESS
        s.EF2_SIZE       = s.EF2_info + AP;
    
        // Raises frames
        s.EF3_raises     = s.EF_SIZE;            // : ADDRESS
        s.EF3_SIZE       = s.EF3_raises + AP;
    
        // Lock frames
        s.EF4_mutex      = s.EF_SIZE;            // : MUTEX
        s.EF4_SIZE       = s.EF4_mutex + AP;
    
        // typecell offsets
        s.TC_typecode       = 0;                      // : INTEGER
        s.TC_selfID         = s.TC_typecode       + IP; // : INTEGER
        s.TC_fp             = s.TC_selfID         + IP; // : 64-bit fingerprint
        s.TC_traced         = s.TC_fp             + 64; // : BYTE = ORD (BOOLEAN)
        s.TC_kind           = s.TC_traced         + 8;  // : BYTE = ORD (TypeKind)
        s.TC_link_state     = s.TC_kind           + 8;  // : BYTE
        s.TC_dataAlignment  = s.TC_link_state     + 8;  // : BYTE
        s.TC_dataSize       = s.TC_traced         + IP; // : INTEGER
        s.TC_type_map       = s.TC_dataSize       + IP; // : ADDRESS
        s.TC_gc_map         = s.TC_type_map       + AP; // : ADDRESS
        s.TC_type_desc      = s.TC_gc_map         + AP; // : ADDRESS
        s.TC_initProc       = s.TC_type_desc      + AP; // : PROC()
        s.TC_brand          = s.TC_initProc       + AP; // : ADDRESS
        s.TC_name           = s.TC_brand          + AP; // : ADDRESS
        s.TC_next           = s.TC_name           + AP; // : ADDRESS
        s.TC_SIZE           = s.TC_next           + AP;
        s.TC_ALIGN          = Math.max(t.Address.align, t.Integer.align);
        AssertError.check(t.Address.align == t.Integer.align, "M3RT");
    
        s.OTC_parentID       = s.TC_SIZE;                 // : INTEGER
        s.OTC_linkProc       = s.OTC_parentID       + IP; // : PROC()
        s.OTC_dataOffset     = s.OTC_linkProc       + AP; // : INTEGER
        s.OTC_methodOffset   = s.OTC_dataOffset     + IP; // : INTEGER
        s.OTC_methodSize     = s.OTC_methodOffset   + IP; // : INTEGER
        s.OTC_defaultMethods = s.OTC_methodSize     + IP; // : ADDRESS
        s.OTC_parent         = s.OTC_defaultMethods + AP; // : ADDRESS
        s.OTC_SIZE           = s.OTC_parent         + AP;
    
        s.ATC_nDimensions    = s.TC_SIZE;                // : INTEGER
        s.ATC_elementSize    = s.ATC_nDimensions   + IP; // : INTEGER
        s.ATC_SIZE           = s.ATC_elementSize   + IP;
    
        s.RV_lhs_id  = 0;                  // : INTEGER
        s.RV_rhs_id  = s.RV_lhs_id + IP;     // : INTEGER
        s.RV_SIZE    = s.RV_rhs_id + IP;
    
        // dope vector offsets
        s.OA_elt_ptr = 0;                                // : ADDRESS
        s.OA_sizes   = s.OA_elt_ptr + AP; // : ARRAY [0..depth] OF INT
        s.OA_size_0  = s.OA_sizes;                         // : INTEGER
        s.OA_size_1  = s.OA_size_0 + IP;  // : INTEGER
    
        // offsets and size of an RT0.ModuleInfo record
        s.MI_file           = 0;                      // : ADDRESS
        s.MI_type_cells     = s.MI_file + AP;           // : ADDRESS
        s.MI_type_cell_ptrs = s.MI_type_cells + AP;     // : ADDRESS
        s.MI_full_rev       = s.MI_type_cell_ptrs + AP; // : ADDRESS
        s.MI_part_rev       = s.MI_full_rev + AP;       // : ADDRESS
        s.MI_proc_info      = s.MI_part_rev + AP;       // : ADDRESS
        s.MI_try_scopes     = s.MI_proc_info + AP;      // : ADDRESS
        s.MI_var_map        = s.MI_try_scopes + AP;     // : ADDRESS
        s.MI_gc_map         = s.MI_var_map + AP;        // : ADDRESS
        s.MI_imports        = s.MI_gc_map + AP;         // : ADDRESS
        s.MI_link_state     = s.MI_imports + AP;        // : INTEGER
        s.MI_binder         = s.MI_link_state + IP;     // : PROC()
        s.MI_gc_flags       = s.MI_binder + AP;         // : INTEGER
        s.MI_SIZE           = s.MI_gc_flags + IP;
    
        // offsets and size of an RT0.ImportInfo record
        s.II_import = 0;               // : ADDRESS (ModulePtr)
        s.II_binder = s.II_import + AP;  // : ADDRESS (Binder)
        s.II_next   = s.II_binder + AP;  // : ADDRESS (ImportPtr)
        s.II_SIZE   = s.II_next + AP;
    
        // offsets and size of an RT0.ProcInfo record
        s.PI_proc   = 0;               // : ADDRESS
        s.PI_name   = s.PI_proc + AP;    // : ADDRESS
        s.PI_SIZE   = s.PI_name + AP;
    
        // offsets in a MUTEX method list
        s.MUTEX_acquire = 0 * AP;          // : PROC()
        s.MUTEX_release = 1 * AP;          // : PROC()
    
    }

    private static long roundUp(long a, long b) {
        return Math.floorDiv(a + b - 1, b) * b;
    }
}
