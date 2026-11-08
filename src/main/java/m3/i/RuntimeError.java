// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import m3j.rt.M3Exception;
import m3j.rt.Rt;

/**
 * The interface RuntimeError of m3core: the checked runtime errors as the
 * exception E with an argument of type T.
 */
public final class RuntimeError {
    private RuntimeError() {}

    public static final boolean M3$PROVIDED = true;

    // The values of RuntimeError.T.
    public static final int AssertFailed = 0;
    public static final int ValueOutOfRange = 1;
    public static final int SubscriptOutOfRange = 2;
    public static final int IncompatibleArrayShape = 3;
    public static final int BadMemoryReference = 4;
    public static final int NarrowFailed = 5;
    public static final int NoReturnValue = 6;
    public static final int UnhandledCase = 7;
    public static final int UnhandledTypecase = 8;
    public static final int StackOverflow = 9;
    public static final int OutOfMemory = 10;
    public static final int UnalignedAddress = 11;
    public static final int UnhandledException = 12;
    public static final int BlockedException = 13;
    public static final int IntegerOverflow = 14;
    public static final int IntegerDivideByZero = 15;
    public static final int FloatDivideByZero = 16;
    public static final int FloatOverflow = 17;
    public static final int FloatUnderflow = 18;
    public static final int FloatInexact = 19;
    public static final int FloatInvalid = 20;
    public static final int DuplicateBrand = 21;
    public static final int MissingType = 22;
    public static final int SupertypeCycle = 23;
    public static final int OpaqueTypeRedefined = 24;
    public static final int InconsistentRevelation = 25;
    public static final int UndefinedMethod = 26;
    public static final int PrivilegedInstruction = 27;
    public static final int SystemError = 28;
    public static final int Unknown = 29;

    private static final String[] Msg = {
        "<*ASSERT*> failed.",
        "An enumeration or subrange value was out of range.",
        "An array subscript was out of range.",
        "An open array had the wrong shape.",
        "Attempt to reference an illegal memory location.",
        "An explicit or implicit NARROW() operation failed.",
        "A function failed to return a value.",
        "No handler specified for the current CASE value.",
        "No handler specified for the current type in a TYPECASE statement.",
        "A thread stack overflowed.",
        "NEW() was unable to allocate more memory.",
        "Attempted to compute the address of an unaligned value.",
        "An exception was raised, but not handled.",
        "An exception was blocked by a RAISES clause.",
        "Integer result too large to represent.",
        "Attempt to DIV or MOD by zero.",
        "Attempted floating-point division by zero.",
        "Floating-point result is too large to represent.",
        "Floating-point result is too small to represent.",
        "Floating-point result is inexact.",
        "Invalid floating-point number.",
        "Two types were defined with the same brand.",
        "A compile-time type is missing.",
        "The supertypes of an object form a cycle.",
        "Multiple full revelations of an opaque type.",
        "Partial revelations don't match type declarations.",
        "A NIL-valued method was invoked.",
        "A privileged machine instruction was attempted.",
        "A low-level OS or machine error occurred.",
        "A runtime error occurred."
    };

    /** EXCEPTION E (T) */
    public static final class E extends M3Exception {
        private static final long serialVersionUID = 1L;

        public final int code;

        /** The text of a failed &lt;*ASSERT*&gt;, or null. */
        public String info;

        public E(int code) {
            this.code = code;
            this.arg = code;
        }

        @Override
        public String m3Name() {
            return "RuntimeError.E";
        }
    }

    /** PROCEDURE Tag (t: T): TEXT */
    public static String Tag(int t) {
        return Msg[t];
    }

    /**
     * PROCEDURE Self (): RT0.ExceptionPtr: the runtime has no descriptors
     * of exceptions (RT0 is UNSAFE), so NIL.
     */
    public static Object Self() {
        return null;
    }

    /** PROCEDURE Raise (t: T): RAISE E (t) */
    public static void Raise(int t) {
        throw Rt.error(t);
    }
}
