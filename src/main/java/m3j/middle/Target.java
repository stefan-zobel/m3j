// Java port of CM3's m3-sys/m3middle/src/Target.i3 and Target.m3, with
// the constants of m3-sys/m3middle/src/ext64/Ext.i3.
// Original: Copyright (C) 1993, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.middle;

import java.util.EnumSet;
import java.util.Set;

import m3j.util.AssertError;

/**
 * Modula-3 target description.
 *
 * <p>This interface defines the host representation of target
 * values and the target architecture.  Its primary client
 * is the Modula-3 compiler.
 *
 * <p>Unless otherwise specified, all sizes and alignments are
 * specified in bits.
 *
 * <p>'pack' is defined to be 'size' rounded up to an alignment
 * boundary (i.e. (size + align - 1) DIV align * align).
 *
 * <p>Port: the module variables ("VAR (*CONST*)") are the fields of
 * {@link State}, which {@link #init} initializes. Int_type, Float_type
 * and Float are mutable classes like the Modula-3 records; an assignment
 * is a {@code copy ()}. The constants of these types must not be changed.
 */
public final class Target {
    private Target() {}

    public static final int MaxAlign = 64;
    // We 64-bit align certain 64-bit operands (e.g. LONGINT, LONGREAL, EXTENDED),
    // even on 32-bit targets.  To ensure this, the compiler has to be able
    // to assume that the constant and global areas, the stack area, and
    // heap-allocated objects are provided with this alignment.

    public static final int UnknownAlign = MaxAlign + 1;

    public static final String[] OSNames = {"POSIX", "WIN32"};
    public static final String[] EndianNames = {"LITTLE", "BIG"};

    public enum M3BackendMode_t {
        // The primary modes are currently 0 and 3.
        /** "0"  -- don't call m3_backend, M3CG produces object code */
        IntegratedObject,
        /** "1"  -- don't call m3_backend, M3CG produces assembly code, run asm. */
        IntegratedAssembly,
        // External modes emit cm3 IR to a file, then the gcc-derived backend.
        /** "2"  -- call m3_backend, it produces object code */
        ExternalObject,
        /** "3"  -- call m3_backend, it produces assembly code, run asm */
        ExternalAssembly,
        /** "4"  -- don't call m3_backend, call compile_c, M3CG produces C */
        C,
        /** "5"  -- M3CG uses llvm to directly produce object code. */
        IntLlvmObj,
        /** "6"  -- M3CG uses llvm to directly produce assembly code, run asm. */
        IntLlvmAsm,
        /** "7"  -- M3CG produces llvm bitcode.  call compile_llvm. It produces object code. */
        ExtLlvmObj,
        /** "8"  -- M3CG produces llvm bitcode.  call compile_llvm. It produces assembly code, run asm. */
        ExtLlvmAsm,
        // StAloneLlvm modes emit cm3 IR to a file, then run a stand-alone executable to
        // translate it to llvm IR.
        /** "9"  -- call m3llvm, then call compile_llvm. It produces object code. */
        StAloneLlvmObj,
        /** "10" -- call m3llvm, then call compile_llvm. It produces assembly code, run asm. */
        StAloneLlvmAsm
    }

    public static final String[] BackendModeStrings = {
        "IntegratedObject",
        "IntegratedAssembly",
        "ExternalObject",
        "ExternalAssembly",
        "C",
        "IntLlvmObj",
        "IntLlvmAsm",
        "ExtLlvmObj",
        "ExtLlvmAsm",
        "StAloneLlvmObj",
        "StAloneLlvmAsm"
    };

    /** Modes where cm3 executable produces assembly or object code. */
    public static final Set<M3BackendMode_t> BackendIntegratedSet = EnumSet.of(
        M3BackendMode_t.IntegratedObject, M3BackendMode_t.IntegratedAssembly,
        M3BackendMode_t.IntLlvmObj, M3BackendMode_t.IntLlvmAsm);
    // Check: Do we want to consider C integrated?

    /** Modes using the external gcc-derived code generator m3cc. */
    public static final Set<M3BackendMode_t> BackendM3ccSet = EnumSet.of(
        M3BackendMode_t.ExternalObject, M3BackendMode_t.ExternalAssembly);

    /** Modes linking to the llvm infrastructure to generate assembly or object code. */
    public static final Set<M3BackendMode_t> BackendLlvmSet = EnumSet.of(
        M3BackendMode_t.ExtLlvmObj, M3BackendMode_t.ExtLlvmAsm,
        M3BackendMode_t.IntLlvmObj, M3BackendMode_t.IntLlvmAsm);

    /** Modes using standalone translator m3llvm, from cm3 IR to llvm IR. */
    public static final Set<M3BackendMode_t> BackendStAloneLlvmSet = EnumSet.of(
        M3BackendMode_t.StAloneLlvmObj, M3BackendMode_t.StAloneLlvmAsm);

    /** Modes using the C-generating code generator plus a C compiler. */
    public static final Set<M3BackendMode_t> BackendCSet = EnumSet.of(M3BackendMode_t.C);

    /**
     * Modes that require the builder to run the assembler.
     * NOTE: C may require separate assembly, but the C compiler does it.
     */
    public static final Set<M3BackendMode_t> BackendAsmSet = EnumSet.of(
        M3BackendMode_t.IntegratedAssembly, M3BackendMode_t.ExternalAssembly,
        M3BackendMode_t.ExtLlvmAsm, M3BackendMode_t.IntLlvmAsm,
        M3BackendMode_t.StAloneLlvmAsm);

    public static final Set<M3BackendMode_t> BackendLlvmAsmSet = EnumSet.of(
        M3BackendMode_t.ExtLlvmAsm, M3BackendMode_t.IntLlvmAsm, M3BackendMode_t.StAloneLlvmAsm);

    public static final Set<M3BackendMode_t> BackendSet = EnumSet.allOf(M3BackendMode_t.class);

    // Provoke compile errors:
    public static final boolean[] BackendIntegratedXXX =
        {true, true, false, false, true, false, false, false, false, false, false};

    public static final boolean[] BackendUsesLlvmXXX =
        {false, false, false, false, false, true, true, true, true, true, true};

    /*------------------------------------------ machine/code generator types ---*/

    /** Machine supported types. */
    public enum CGType {
        /** 8-bit, unsigned & signed integer */
        Word8, Int8,
        /** 16-bit, unsigned & signed integer */
        Word16, Int16,
        /** 32-bit, unsigned & signed integer */
        Word32, Int32,
        /** 64-bit, unsigned & signed integer */
        Word64, Int64,
        /** single precision reals */
        Reel,
        /** double precision reals */
        LReel,
        /** extended precision reals */
        XReel,
        /** addresses */
        Addr,
        /** a block of memory */
        Struct,
        /** not-a-type */
        Void
    }

    public static final String[] TypeNamesFixedWidth = {
        "Word8  ", "Int8   ", "Word16 ", "Int16  ",
        "Word32 ", "Int32  ", "Word64 ", "Int64  ",
        "Reel   ", "LReel  ", "XReel  ",
        "Addr   ", "Struct ", "Void   "
    };

    public static final String[] TypeNamesDotted = {
        "Word.8",  "Int.8",
        "Word.16", "Int.16",
        "Word.32", "Int.32",
        "Word.64", "Int.64",
        "Reel", "LReel", "XReel",
        "Addr",
        "Struct",
        "Void"
    };

    public static final String[] TypeNames = {
        "Word8",  "Int8",
        "Word16", "Int16",
        "Word32", "Int32",
        "Word64", "Int64",
        "Reel", "LReel", "XReel",
        "Addr",
        "Struct",
        "Void"
    };

    public static final boolean[] SignedType = {
        false, true,  false, true,   // Word8 .. Int16
        false, true,  false, true,   // Word32 .. Int64
        true,  true,  true,          // Reel .. XReel
        false, false, false          // Addr .. Void
    };

    public static final boolean[] OrdinalType = {
        true,  true,  true,  true,   // Word.8, Int.8, Word.16, Int.16
        true,  true,  true,  true,   // Word.32, Int.32, Word.64, Int.64
        false, false, false,         // Reel, LReel, XReel
        true,  false, false          // Addr, Struct, Void
    };

    public static final boolean[] FloatType = {
        false, false, false, false,  // Word.8, Int.8, Word.16, Int.16
        false, false, false, false,  // Word.32, Int.32, Word.64, Int.64
        true,  true,  true,          // Reel, LReel, XReel
        false, false, false          // Addr, Struct, Void
    };

    /*-------------------------------------------------------- integer values ---*/

    // The bits of a target integer (in 2's complement) are stored in
    // an array of small host values, with the low order bits in the first
    // element of the array. Port: Int = TInt.Int is an int[9].

    public static final class Int_type {
        /** representation */
        public CGType cg_type;
        /** bit size */
        public long size;
        /** minimum bit alignment */
        public long align;
        /** minimum width bit packing */
        public long pack;
        /** byte size */
        public long bytes;
        /** minimum value of this type */
        public int[] min;
        /** maximum value of this type */
        public int[] max;

        public Int_type(CGType cg_type, long size, long align, long pack, long bytes, int[] min, int[] max) {
            this.cg_type = cg_type;
            this.size = size;
            this.align = align;
            this.pack = pack;
            this.bytes = bytes;
            this.min = TInt.copy(min);
            this.max = TInt.copy(max);
        }

        /** A copy of this record value. */
        public Int_type copy() {
            return new Int_type(cg_type, size, align, pack, bytes, min, max);
        }
    }

    /*------------------------------------------------- floating point values ---*/

    public enum Precision { Short, Long, Extended }

    /** (*OPAQUE*) RECORD; Port: EXTENDED is a double on the target. */
    public static final class Float {
        public Precision pre;
        public long exponent;
        public double fraction;

        public Float() {
            this.pre = Precision.Short;
        }

        public Float(Precision pre, long exponent, double fraction) {
            this.pre = pre;
            this.exponent = exponent;
            this.fraction = fraction;
        }

        /** A copy of this record value. */
        public Float copy() {
            return new Float(pre, exponent, fraction);
        }

        /** {@code this := f}. */
        public void assign(Float f) {
            this.pre = f.pre;
            this.exponent = f.exponent;
            this.fraction = f.fraction;
        }
    }

    public static final class Float_type {
        /** representation */
        public CGType cg_type;
        /** precision */
        public Precision pre;
        /** bit size */
        public long size;
        /** minimum bit alignment */
        public long align;
        /** byte size */
        public long bytes;
        /** minimum value of this type */
        public Float min;
        /** maximum value of this type */
        public Float max;

        public Float_type(CGType cg_type, Precision pre, long size, long align, long bytes, Float min, Float max) {
            this.cg_type = cg_type;
            this.pre = pre;
            this.size = size;
            this.align = align;
            this.bytes = bytes;
            this.min = min.copy();
            this.max = max.copy();
        }

        /** A copy of this record value. */
        public Float_type copy() {
            return new Float_type(cg_type, pre, size, align, bytes, min, max);
        }
    }

    /*----------------------------------------------- machine supported types ---*/

    // Build a generic 32-bit/IEEE system description.
    // Code in Target.m3 will change values as appropriate for 64-bit and any other
    // target properties.

    // Ext.i3 (ext64): BitSize = 64; BitAlign = 64; ByteSize = 8;
    // Min = -1.79769313486231570x+308; Max = 1.79769313486231570x+308.
    private static final int Ext_BitSize = 64;
    private static final int Ext_BitAlign = 64;
    private static final int Ext_ByteSize = 8;
    private static final double Ext_Min = -1.79769313486231570e+308;
    private static final double Ext_Max = 1.79769313486231570e+308;

    //                                    cg_type,   size, align, pack, bytes, min, max
    public static final Int_type Addr32 = new Int_type(CGType.Addr,   32, 32, 32, 4, TInt.Zero, TWord.Max32);
    public static final Int_type Addr64 = new Int_type(CGType.Addr,   64, 64, 64, 8, TInt.Zero, TWord.Max64);
    public static final Int_type Int8   = new Int_type(CGType.Int8,    8,  8,  8, 1, TInt.Min8, TInt.Max8);
    public static final Int_type Int16  = new Int_type(CGType.Int16,  16, 16, 16, 2, TInt.Min16, TInt.Max16);
    public static final Int_type Int32  = new Int_type(CGType.Int32,  32, 32, 32, 4, TInt.Min32, TInt.Max32);
    public static final Int_type Int64  = new Int_type(CGType.Int64,  64, 64, 64, 8, TInt.Min64, TInt.Max64);
    public static final Int_type Word8  = new Int_type(CGType.Word8,   8,  8,  8, 1, TInt.Zero,  TWord.Max8);
    public static final Int_type Word16 = new Int_type(CGType.Word16, 16, 16, 16, 2, TInt.Zero, TWord.Max16);
    public static final Int_type Word32 = new Int_type(CGType.Word32, 32, 32, 32, 4, TInt.Zero, TWord.Max32);
    public static final Int_type Word64 = new Int_type(CGType.Word64, 64, 64, 64, 8, TInt.Zero, TWord.Max64);
    public static final Int_type Char   = Word8;
    public static final Int_type Void   = new Int_type(CGType.Void,  0,  8,  0, 0, TInt.Zero, TInt.Zero);
    public static final Float_type Real = new Float_type(CGType.Reel, Precision.Short, 32, 32, 4,
        new Float(Precision.Short, 0, -3.40282346638528860e+38),
        new Float(Precision.Short, 0, 3.40282346638528860e+38));

    /** Sorted list of supported machine alignments. */
    public static final long[] Alignments = {8, 16, 32, 64, 128};

    /*------------------------------------------------------- procedure calls ---*/

    /** REF RECORD. */
    public static final class CallingConvention {
        public String name;
        public long m3cg_id;
        public boolean args_left_to_right;
        public boolean results_on_left;
        public boolean standard_structs;

        public CallingConvention(String name) {
            this.name = name;
        }
    }

    /** use with ConventionFromID */
    public static final int CDECL = 0;
    /** use with ConventionFromID */
    public static final int STDCALL = 1;

    /*
      name => the name recognized in an <*EXTERNAL*> pragma, or as a prefix
              to a PROCEDURE declaration.

      m3cg_id => tag used to indicate convention to the back end.

      args_left_to_right => Procedure arguments should be pushed
                            left->right or right->left.

      results_on_left => when the front-end is passing structures the return
                         result is the left-most parameter.  Otherwise, it is
                         the right-most parameter.

      standard_structs =>
         TRUE => the front-end will take care of all structure passing:
           by VALUE parameters: callers pass the address of the structure
              and the callee copies it into a temporary.
           return results: the caller passes as the left-most or right-most
              parameter the address of the temporary that will hold the result
              and the callee copies the value there.
         FALSE =>
           by VALUE parameters: the back-end is responsible.
              (ie. M3CG.T.Pop_struct will be called)
           return results: the caller passes as the left-most or right-most
              parameter the address of the temporary that will hold the result
              and the callee copies the value there.  The start_call, call,
              and exit_proc methods are called with t=Struct.
    */

    /*--------------------------------------------------- misc. configuration ---*/

    public enum Endian { Undefined, Little, Big }

    /** minimum addressable unit (in bits) */
    public static final int Byte = 8;

    /**
     * Is the jmpbuf for RTExFrame allocated with alloca,
     * or does the frontend know its size?
     * RTExFrame.Alloca_jmpbuf and Target.Alloca_jmpbuf must match.
     * This could become FALSE again, if the backend learns of jmp_buf,
     * and jmp_buf is again embedded in the larger structs and m3front
     * does not do that layout.
     */
    public static final boolean Alloca_jmpbuf = true;

    /**
     * If Alloca_jmpbuf = FALSE, then this is the size of a jmp_buf.
     * If Alloca_jmpbuf = TRUE, then this is not used.
     */
    public static final int Jumpbuf_size = 0;

    /**
     * Every structure size must be a multiple of this.
     * In gcc, this is 8 almost always.
     */
    public static final long Structure_size_boundary = 8;

    /**
     * TRUE => the C compiler uses the type rather than the size of
     * a bit-field to compute the alignment of the struct.
     */
    public static final boolean PCC_bitfield_type_matters = true;

    /**
     * NIL checking: read or write references to addresses in the range
     * [0..First_readable-1] will cause an address faults.  Hence, no explicit
     * NIL checks are needed for dereferencing with referent sizes in this range.
     */
    public static final long First_readable_addr = 4096; // (* * Char.size *)

    /**
     * If all bit patterns are "legal" floating point values (i.e. they can
     * be assigned without causing traps or faults).
     * This is true for all targets except VAX.
     */
    public static final boolean All_floats_legal = true;

    /**
     * TRUE => all procedure values are aligned to at least Integer.align
     * and can be safely dereferenced.  Otherwise, the code generated to
     * test for nested procedures passed as parameters must be more
     * elaborate (e.g. HPPA).
     */
    public static final boolean Aligned_procedures = false;

    public static final long WideChar16Max = 0xFFFF;
    public static final long WideChar32Max = 0x10FFFF;

    // Just a few systems need special handling and are listed here.
    // Far more targets than this are supported.
    // See m3-sys/cminstall/src/config-no-install and scripts/python/targets.txt
    // for an idea as to the working targets.
    //
    // SystemNames and Systems need to roughly match.
    enum Systems {
        Undefined,
        FreeBSD4,
        I386_CYGWIN,
        I386_INTERIX,
        I386_MINGW,
        I386_NT,
        LINUXLIBC6,
        NT386,
        SOLSUN, // Old name for SPARC32_SOLARIS with Sun C compiler
        SOLGNU, // Old name for SPARC32_SOLARIS with GNU C compiler
        SPARC32_SOLARIS,
        SPARC64_SOLARIS,
        I386_SOLARIS,
        AMD64_SOLARIS,
        Other
    }

    // These are uppercase to facilitate case insensitivity.
    private static final String[] SystemNames = {
        "",
        "FREEBSD4",
        "I386_CYGWIN",
        "I386_INTERIX",
        "I386_MINGW",
        "I386_NT",
        "LINUXLIBC6",
        "NT386",
        "SOLSUN",
        "SOLGNU",
        "SPARC32_SOLARIS",
        "SPARC64_SOLARIS",
        "I386_SOLARIS",
        "AMD64_SOLARIS"
    };

    /** The module variables of Target. */
    public static final class State {
        /** initialized by "Init" */
        public String System_name = null;
        /** initialized by "Init" */
        public String OS_name = null;

        // The following differ in size and alignment for 32/64-bit targets.
        public Int_type Address = Addr32.copy();
        public Int_type Integer = Int32.copy();
        public Int_type Word = Word32.copy();
        // The following differ only in alignment.
        public Int_type Longint = Int64.copy();
        public Int_type Long = Word64.copy();
        public Float_type Longreal = new Float_type(CGType.LReel, Precision.Long, 64, 64, 8,
            new Float(Precision.Long, 0, -1.79769313486231570e+308),
            new Float(Precision.Long, 0, 1.79769313486231570e+308));
        public Float_type Extended = new Float_type(CGType.XReel, Precision.Extended,
            Ext_BitSize, Ext_BitAlign, Ext_ByteSize,
            new Float(Precision.Extended, 0, Ext_Min),
            new Float(Precision.Extended, 0, Ext_Max));

        public CallingConvention DefaultCall = null;

        /**
         * ARRAY [CGType.Word8 .. CGType.Addr] OF BOOLEAN, indexed by the
         * ordinal of the CGType. TRUE => platform has lock-free atomic
         * primitives for this type.
         */
        public boolean[] Atomic_lock_free = new boolean[CGType.Addr.ordinal() + 1];

        /** Little => byte[0] of an integer contains its least-significant bits */
        public Endian endian = Endian.Undefined;

        /**
         * Allow the compiler to align scalar types on byte boundaries when packing.
         * The target processor must support byte alignment of scalar store and
         * loads. This does not remove the restriction that bitfields may not cross
         * word boundaries.
         */
        public boolean Allow_packed_byte_aligned;

        /**
         * TRUE => generate PC-tables for exception scopes.  Otherwise, generate
         * an explicit stack of exception handler frames.
         */
        public boolean Has_stack_walker;

        /** one param or two? */
        public boolean Sigsetjmp;
        /**
         * The C name of the routine used to capture the machine state in
         * an exception handler frame.
         */
        public String Setjmp;

        /**
         * Backend uses contextual typenames. i.e. on declare_param, etc.
         * These are expensive? to produce so optional.
         */
        public boolean Typenames = false;

        /** Ideally the value 0 would be uninitialized, but it is used publically in Quake? */
        public M3BackendMode_t BackendMode = M3BackendMode_t.IntegratedObject;
        public boolean BackendModeInitialized = false;

        // Module variables of Target.m3.
        CallingConvention[] CCs = new CallingConvention[9];
        Systems System = Systems.Undefined;

        // The directory containing derived files. This is to aid the C
        // backend in converging target-dependent output to be target-independent.
        // It occurs unnecessarily in some places such as line directives.
        // m3front communicates to m3back via m3middle, a string that backend
        // might want to change.
        String Build_dir;
        int Build_dir_length;

        long wideCharSize = 16;
        long wideCharMax = WideChar16Max;
    }

    /*-------------------------------------------------------- initialization ---*/

    /** Override default primitive sizes for 64-bit architectures. */
    private static void init64(State t) {
        t.Integer = Int64.copy();
        t.Word = Word64.copy();
        t.Address = Word64.copy();
        t.Address.cg_type = CGType.Addr;
    }

    private static boolean solaris(State t) {
        return EnumSet.of(Systems.SOLSUN,
                          Systems.SOLGNU,
                          Systems.SPARC32_SOLARIS,
                          Systems.SPARC64_SOLARIS,
                          Systems.I386_SOLARIS,
                          Systems.AMD64_SOLARIS).contains(t.System);
    }

    /**
     * System_name is uppercased to facilitate case insensitivity.
     * Is this too broad?
     */
    private static boolean isX86orAmd64(State t, String System_name) {
        final String[] start = {"AMD64", "86",
                                "X86", "I386", "I486", "I586", "I686",
                                "I86",  "386",  "486",  "586",  "686"};
        final String[] end = {"AMD64", "86"};
        if (EnumSet.of(Systems.FreeBSD4,
                       Systems.I386_CYGWIN,
                       Systems.I386_INTERIX,
                       Systems.I386_MINGW,
                       Systems.I386_NT,
                       Systems.LINUXLIBC6,
                       Systems.NT386).contains(t.System)) {
            return true;
        }
        for (String s : start) {
            if (System_name.startsWith(s)) {
                return true;
            }
        }
        for (String s : end) {
            if (System_name.endsWith(s)) {
                return true;
            }
        }
        return false;
    }

    /** TextUtils.Upper: ASCII.Upper of every character. */
    private static String upper(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'a' && c <= 'z') {
                c = (char) (c - 'a' + 'A');
            }
            b.append(c);
        }
        return b.toString();
    }

    /**
     * Initialize the variables of this interface to reflect the architecture
     * of "system".  Returns TRUE iff the "system" was known and the initialization
     * was successful.
     *
     * <p>Port: the module variables of Target, TargetMap and M3RT are the
     * fields of {@code t}, {@code map} and {@code rt}. The defaults of the
     * original are osname = "POSIX" and backend_mode = ExternalAssembly.
     */
    public static boolean init(State t, TargetMap.State map, M3RT.State rt,
                               String system, String in_OS_name, M3BackendMode_t backend_mode) {
        int sys = 0;
        int max_align = 128;
        String casePreservedSystem = system;
        // lookup the system -- linear search
        if (system == null) {
            return false;
        }
        system = upper(system); // Uppercase for case insensitivity.
        while (!system.equals(SystemNames[sys])) {
            sys++;
            if (sys >= SystemNames.length) {
                break;
            }
        }
        t.System = Systems.values()[sys];
        t.System_name = casePreservedSystem;
        t.OS_name = in_OS_name;

        // common values

        java.util.Arrays.fill(t.Atomic_lock_free, true);
        // this is overly optimistic...

        t.Allow_packed_byte_aligned = false;
        t.endian = Endian.Little;

        // This is messy. See CsetjmpC.c.
        if (backend_mode == M3BackendMode_t.C) {
            t.Setjmp = "m3_setjmp";
            t.Sigsetjmp = false;
        } else if (solaris(t)) {
            t.Setjmp = "sigsetjmp";
            t.Sigsetjmp = true;
        } else {
            t.Setjmp = "_setjmp";
            t.Sigsetjmp = false;
        }

        if (backend_mode == M3BackendMode_t.C) {
            t.Typenames = true; // on declare_param, etc.
        }

        // There is no portable stack walker, and therefore few systems have one.
        // Addendum: Actually libunwind circa 2010 is mostly portable.
        // Having a stack walker theoretically speeds up everything nicely.  If
        // you are familiar with NT exception handling, all but x86 have a stack
        // walker.  Not having a stack walker means that functions that have
        // try/except/finally/raise incur a codegen cost even if there is never a
        // raise -- for having the try.  Having a stack walker means "zero cost"
        // for exception handling code that does not actually raise an exception.
        //
        // If there is NOT a stack walker, prologues/epilogues for functions that
        // try/except/finally/raise call PushEFrame / PopEFrame, essentially to
        // build a walkable parallel stack.
        //
        // If there is a stack walker, then raise can discover what PushEFrame /
        // PopEFrame effectively recorded.
        //
        // NT/x86 has a highly optimized equivalent of PushEFrame / PopEFrame, not
        // currently used by Modula-3.
        //
        // AMD64_LINUX has a stack walker see m3core for details.

        t.Has_stack_walker = false;

        // EXTENDED is 128-bit (_Float128) only on AMD64_LINUX.
        // On all other targets it maps to LONGREAL (64-bit IEEE double).
        // The module-level initializer uses Ext.BitSize which is baked in at
        // compile time from the HOST target's ExtendedRep; reset here for any
        // host/target mismatch.
        if (!system.equals("AMD64_LINUX")) {
            t.Extended = new Float_type(CGType.XReel, Precision.Extended, 64, 64, 8,
                new Float(Precision.Extended, 0, -1.79769313486231570e+308),
                new Float(Precision.Extended, 0, 1.79769313486231570e+308));
        }

        // add the system-specific customization

        // 32bit or 64bit
        if ((system.startsWith("ALPHA")
                || system.contains("64")) && !system.contains("32")) { // possibly IA64, Alpha
            init64(t);
        } else if (backend_mode != M3BackendMode_t.C) {
            // Change only alignment.  Size is always 64:
            // Aligning these types to 32 is incorrect on many but not all 32bit targets.
            // C backend cannot portably reduce alignment but it can portably increase
            // alignment. These types were 64-aligned for a very long time on
            // all 32bit targets.
            t.Longint.align = 32;
            t.Long.align = 32;
            t.Longreal.align = 32;
            t.Extended.align = 32;
        }

        // big endian

        if (system.startsWith("PA")
                || (system.startsWith("MIPS")
                    && !system.startsWith("MIPSEL")
                    && !system.startsWith("MIPS32EL")
                    && !system.startsWith("MIPS64EL"))
                || system.startsWith("PPC") // ambiguous
                || system.startsWith("SPARC")
                || system.startsWith("SOL")
                || system.startsWith("IA64_HPUX")) {
            t.endian = Endian.Big;
        }

        // x86 and AMD64 allow unaligned loads/stores but converge C

        if (backend_mode != M3BackendMode_t.C) {
            if (isX86orAmd64(t, system)) {
                t.Allow_packed_byte_aligned = true;
            }
        }

        // NT/x86 is the only system with multiple calling conventions,
        // ignoring little-used vectorcall of NT/amd64 and NT/x86.
        initCallingConventions(t, backend_mode,
                               EnumSet.of(Systems.I386_INTERIX,
                                          Systems.NT386,
                                          Systems.I386_NT,
                                          Systems.I386_CYGWIN,
                                          Systems.I386_MINGW).contains(t.System));

        // check the "bytes" and "pack" fields
        checkI(t.Address, max_align);
        checkI(t.Integer, max_align);
        checkI(t.Word, max_align);
        checkI(t.Longint, max_align);
        checkI(t.Long, max_align);
        checkF(Real, max_align);
        checkF(t.Longreal, max_align);
        checkF(t.Extended, max_align);
        checkI(Int8, max_align);
        checkI(Int16, max_align);
        checkI(Int32, max_align);
        checkI(Int64, max_align);
        checkI(Word8, max_align);
        checkI(Word16, max_align);
        checkI(Word32, max_align);
        checkI(Word64, max_align);
        checkI(Void, max_align);
        checkI(Char, max_align);

        // fix the alignments
        for (long a : Alignments) {
            AssertError.check(a == Math.min(a, max_align), "Target");
        }

        // initialize the other target-specific modules
        TargetMap.init(map, t);
        M3RT.init(rt, t);

        return true;
    }

    private static void initCallingConventions(State t, M3BackendMode_t backend_mode,
                                               boolean target_has_calling_conventions) {
        boolean integrated = BackendIntegratedSet.contains(backend_mode);
        boolean llvm = BackendLlvmSet.contains(backend_mode);

        // 0 is __cdecl, 1 is __stdcall.
        t.CCs = new CallingConvention[] {
            newConvention("__cdecl",    0, backend_mode, target_has_calling_conventions, integrated, llvm), // must be first
            newConvention("__stdcall",  1, backend_mode, target_has_calling_conventions, integrated, llvm), // must be second
            newConvention("C",          0, backend_mode, target_has_calling_conventions, integrated, llvm),
            newConvention("WINAPIV",    0, backend_mode, target_has_calling_conventions, integrated, llvm),
            newConvention("WINAPI",     1, backend_mode, target_has_calling_conventions, integrated, llvm),
            newConvention("CALLBACK",   1, backend_mode, target_has_calling_conventions, integrated, llvm),
            newConvention("APIENTRY",   1, backend_mode, target_has_calling_conventions, integrated, llvm),
            newConvention("APIPRIVATE", 1, backend_mode, target_has_calling_conventions, integrated, llvm),
            newConvention("PASCAL",     1, backend_mode, target_has_calling_conventions, integrated, llvm) };

        t.DefaultCall = t.CCs[0];
    }

    /** The local procedure New of InitCallingConventions. */
    private static CallingConvention newConvention(String name, int id, M3BackendMode_t backend_mode,
                                                   boolean target_has_calling_conventions,
                                                   boolean integrated, boolean llvm) {
        CallingConvention cc = new CallingConvention(name);
        if (backend_mode == M3BackendMode_t.C || target_has_calling_conventions) {
            cc.m3cg_id = id;
        } else {
            cc.m3cg_id = 0;
        }
        if (backend_mode == M3BackendMode_t.C) {
            cc.args_left_to_right = true;
            cc.results_on_left = true;
            cc.standard_structs = true;
        } else if (llvm) {
            cc.args_left_to_right = true;
            cc.results_on_left = false;
            cc.standard_structs = true;
            // CHECK: ^Are these right for llvm? They are same as gcc.
        } else if (integrated) {
            cc.args_left_to_right = false;
            cc.results_on_left = true;
            cc.standard_structs = false;
        } else { // gcc-derived back end.
            cc.args_left_to_right = true;
            cc.results_on_left = false;
            cc.standard_structs = true;
        }
        return cc;
    }

    private static void checkI(Int_type i, int max_align) {
        AssertError.check(i.align == Math.min(i.align, max_align), "Target");
        AssertError.check(i.bytes == i.size / Byte, "Target");
        AssertError.check(i.pack == (i.size + i.align - 1) / i.align * i.align, "Target");
    }

    private static void checkF(Float_type f, int max_align) {
        AssertError.check(f.align == Math.min(f.align, max_align), "Target");
        AssertError.check(f.bytes == f.size / Byte, "Target");
        // ASSERT f.pack  = (f.size + f.align - 1) DIV f.align * f.align
    }

    /** Return the convention with name "nm".  Otherwise, return NIL. */
    public static CallingConvention findConvention(State t, String nm) {
        CallingConvention cc;
        for (int i = 0; i < t.CCs.length; i++) {
            cc = t.CCs[i];
            if (cc != null && nm.equals(cc.name)) {
                return cc;
            }
        }
        return null;
    }

    /** Return the convention with "m3cg_id" "id".  Otherwise, return NIL. */
    public static CallingConvention conventionFromID(State t, long id) {
        // Sic: an id outside [0..8] is a subscript out of range, not NIL.
        return t.CCs[Math.toIntExact(id)];
    }

    public static void setBuild_dir(State t, String build_dir) {
        build_dir = "../" + build_dir + "/";
        t.Build_dir = build_dir;
        t.Build_dir_length = build_dir.length();
    }

    private static boolean pathCharEqual(char ch1, char ch2) {
        return ch1 == ch2
            || (ch1 == '\\' && ch2 == '/')
            || (ch2 == '\\' && ch1 == '/');
    }

    public static String cleanupSourcePath(State t, String file) {
        int length;
        String build_dir;
        int build_dir_length;
        int pos;

        AssertError.check(t.BackendModeInitialized, "Target");

        // Review: Is this function too lossy?
        // Would we be better off with just substituting build_dir with "build_dir"?

        if (file != null && t.BackendMode == M3BackendMode_t.C) {
            file = file.replace('\\', '/');
            length = file.length();
            build_dir = t.Build_dir;
            build_dir_length = t.Build_dir_length;

            // m3front does like:
            // set_source_file("../AMD64_DARWIN/WordMod.m3 => ../src/builtinWord/Mod.mg")
            // which damages debugging (this file does not exist) and is unnecessarily
            // target specific, damaging portable distribution format.
            if (length > 0 && file.charAt(length - 1) == 'g') {
                pos = file.indexOf(" => ");
                if (pos != -1) {
                    file = file.substring(pos + 4, length);
                    length = file.length();
                }
            }

            // Remove build_dir from start for portable distribution format.
            // This is typically generic instantations but could be anything.
            if (length > t.Build_dir_length) {
                for (int i = 0; i <= build_dir_length - 1; i++) {
                    if (!pathCharEqual(file.charAt(i), build_dir.charAt(i))) {
                        break;
                    }
                    if (i == build_dir_length - 1) {
                        file = file.substring(build_dir_length, length);
                    }
                }
            }
        }

        return file;
    }

    /*-------------------------- range of WIDECHAR ------------------------------*/

    /** WideCharSize() = 32 */
    public static boolean isWideChar32(State t) {
        return t.wideCharSize == 32;
    }

    public static long wideCharSize(State t) {
        return t.wideCharSize;
    }

    public static long wideCharMax(State t) {
        return t.wideCharMax;
    }

    /** WideCharMax() + 1 */
    public static long wideCharNumber(State t) {
        return t.wideCharMax + 1;
    }

    public static void setWideChar16(State t) {
        t.wideCharSize = 16;
        t.wideCharMax = WideChar16Max;
    }

    public static void setWideChar32(State t) {
        t.wideCharSize = 32;
        t.wideCharMax = WideChar32Max;
    }
}
