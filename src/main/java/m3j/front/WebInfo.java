// Java port of CM3's m3-sys/m3front/src/misc/WebInfo.i3 and WebInfo.m3.
// Original: Copyright (C) 1994, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.M3ID;

// WebInfo.i3: Last modified on Mon Dec 12 15:52:49 PST 1994 by kalsow
// WebInfo.m3: Last modified on Mon Dec 12 15:56:41 PST 1994 by kalsow

/**
 * This interface defines the routines used to generate the WWW
 * browser information.
 *
 * <p>Port: the types (see M3CG for the interpretation of these types)
 * Name and TypeUID are {@code int}, Offset and Size are {@code long}.
 */
public final class WebInfo {
    private WebInfo() {}

    private static M3Buf.T info = null;

    /** Port: Wr.EOL, the line separator of the host. */
    private static final String EOL = System.lineSeparator();

    //----------------------------------------------------- set up/tear down ---

    /** Port: indexed by 0 (FALSE) and 1 (TRUE). */
    private static final char[] ResetTag = {'A', 'B'};

    /** initializes the info. */
    public static void reset() {
        String[] file = new String[1];
        long[] line = new long[1];
        if ((info == null)) {
            info = M3Buf.New();
        }

        Scanner.localHere(file, line);
        outC('@');
        outT(file[0]);
        nl();

        outC(ResetTag[Module.isInterface() ? 1 : 0]);
        outN(Module.name(null));
        nl();
    }

    /** returns the accumulated browser info. */
    public static String finish() {
        return M3Buf.toText(info);
    }

    //---------------------------------------------------- compilation units ---

    public static void import_unit(int n) {
        outC('C');
        outN(n);
        nl();
    }

    public static void export_unit(int n) {
        outC('D');
        outN(n);
        nl();
    }

    //------------------------------------------ debugging type declarations ---

    public static void declare_typename(int t, Value.T x) {
        outX(t, 'E');
        outV(x);
        nl();
    }

    public static void declare_array(int t, int index, int elt, long s) {
        outX(t, 'F');
        outU(index);
        outU(elt);
        outI(s);
        nl();
    }

    public static void declare_open_array(int t, int elt, long s) {
        outX(t, 'G');
        outU(elt);
        outI(s);
        nl();
    }

    public static void declare_enum(int t, long n_elts, long s) {
        outX(t, 'H');
        outI(n_elts);
        outI(s);
        nl();
    }

    public static void declare_enum_elt(int n) {
        outC('I');
        outN(n);
        nl();
    }

    public static void declare_packed(int t, long s, int base) {
        outX(t, 'J');
        outI(s);
        outU(base);
        nl();
    }

    public static void declare_record(int t, long s, long n_fields) {
        outX(t, 'K');
        outI(s);
        outI(n_fields);
        nl();
    }

    public static void declare_field(int n, long o, long s, int t) {
        outC('L');
        outN(n);
        outI(o);
        outI(s);
        outU(t);
        nl();
    }

    public static void declare_set(int t, int domain, long s) {
        outX(t, 'M');
        outU(domain);
        outI(s);
        nl();
    }

    public static void declare_subrange(int t, int domain, int[] min, int[] max, long s) {
        outX(t, 'N');
        outU(domain);
        outZ(min);
        outZ(max);
        outI(s);
        nl();
    }

    /** Port: indexed by 0 (FALSE) and 1 (TRUE). */
    private static final char[] PointerTag = {'O', 'P'};

    public static void declare_pointer(int t, int target, String brand, boolean traced) {
        outX(t, PointerTag[traced ? 1 : 0]);
        outU(target);
        if ((brand != null)) {
            outC(' ');
            outT(brand);
        }
        nl();
    }

    public static void declare_indirect(int t, int target) {
        outX(t, 'Q');
        outU(target);
        nl();
    }

    public static void declare_proctype(int t, long n_formals, int result, long n_raises) {
        outX(t, 'R');
        outI(n_formals);
        outU(result);
        outI(n_raises);
        nl();
    }

    public static void declare_formal(int n, int t) {
        outC('S');
        outN(n);
        outU(t);
        nl();
    }

    public static void declare_raises(int n) {
        outC('T');
        outN(n);
        nl();
    }

    /** Port: indexed by 0 (FALSE) and 1 (TRUE). */
    private static final char[] ObjectTag = {'U', 'V'};

    public static void declare_object(int t, int super_, String brand, boolean traced, long n_fields, long n_methods, long n_overrides, long field_size) {
        outX(t, ObjectTag[traced ? 1 : 0]);
        outU(super_);
        outI(n_fields);
        outI(n_methods);
        outI(n_overrides);
        outI(field_size);
        if ((brand != null)) {
            outC(' ');
            outT(brand);
        }
        nl();
    }

    public static void declare_method(int n, int signature, Expr.T dfault) {
        Value.T[] proc = new Value.T[1];
        outC('W');
        outN(n);
        outU(signature);
        if ((dfault == null)) {
            // skip
        } else if (UserProc.isProcedureLiteral(dfault, proc)) {
            outV(proc[0]);
        } else {
            outT(" NIL");
        }
        nl();
    }

    public static void declare_override(int n, Expr.T dfault) {
        Value.T[] proc = new Value.T[1];
        outC('X');
        outN(n);
        if ((dfault == null)) {
            outT(" (??)");
        } else if (UserProc.isProcedureLiteral(dfault, proc)) {
            outV(proc[0]);
        } else {
            outT(" NIL");
        }
        nl();
    }

    public static void declare_opaque(int t, int super_) {
        outX(t, 'Y');
        outU(super_);
        nl();
    }

    public static void reveal_opaque(int lhs, int rhs) {
        outX(lhs, 'Z');
        outU(rhs);
        nl();
    }

    //-------------------------------------------------------- low-level I/O ---

    private static void outC(char c) {
        M3Buf.putChar(info, c);
    }

    private static void outI(long i) {
        M3Buf.putChar(info, ' ');
        M3Buf.putInt(info, i);
    }

    private static void outU(int t) {
        M3Buf.putChar(info, ' ');
        putHex(t);
    }

    private static void outN(int n) {
        M3ID.put(M3.ids, info, n);
    }

    private static void outX(int t, char c) {
        M3Buf.putChar(info, c);
        putHex(t);
    }

    private static void outV(Value.T v) {
        Scope.IDStack s = new Scope.IDStack();
        M3Buf.putChar(info, ' ');
        s.top = 0;
        Scope.nameToPrefix(v, s, /* considerExternal := */ true,
                           /* dots := */ true, /* with_module := */ true);
        Scope.putStack(info, s);
    }

    private static void outZ(int[] i) {
        M3Buf.putChar(info, ' ');
        M3Buf.putIntt(info, i);
    }

    private static void outT(String t) {
        M3Buf.putText(info, t);
    }

    private static final char[] HexDigit = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    private static void putHex(long i) {
        char[] buf = new char[8];
        for (int j = 7; j >= 0; j--) {
            buf[j] = HexDigit[(int) Long.remainderUnsigned(i, 16)];
            i = Long.divideUnsigned(i, 16);
        }
        M3Buf.putSub(info, buf);
    }

    private static void nl() {
        M3Buf.putText(info, EOL);
    }
}
