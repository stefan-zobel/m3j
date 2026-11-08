// Java port of CM3's m3-sys/m3front/src/exprs/ExprParse.i3 and ExprParse.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;
import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.TInt;
import m3j.util.AssertError;

// File: ExprParse.i3
// Last Modified On Fri Jun 24 13:36:37 PDT 1994 By kalsow

// File: ExprParse.m3
// Last Modified On Tue Dec 20 15:25:42 PST 1994 By kalsow
//      Modified On Sat Aug 18 01:24:21 1990 By muller

public final class ExprParse {
    private ExprParse() {}

    // Port: FROM Scanner IMPORT Match, MatchID, GetToken, Fail, cur, offset
    // are written Scanner.match, Scanner.matchID, Scanner.getToken,
    // Scanner.fail, Scanner.cur and Scanner.offset; TK = Token.T.

    /** parse an expression, types = TRUE => allow type expressions */
    public static Expr.T E0(boolean types) {
        Expr.T a;
        long here = Scanner.offset;
        a = E1(types);
        while ((Scanner.cur.token == Token.T.tOR)) {
            Scanner.getToken(); // OR
            a = OrExpr.new_(a, E1(false));
            a.origin = here;
            here = Scanner.offset;
        }
        return a;
    }

    private static Expr.T E1(boolean types) {
        Expr.T a;
        long here = Scanner.offset;
        a = E2(types);
        while ((Scanner.cur.token == Token.T.tAND)) {
            Scanner.getToken(); // AND
            a = AndExpr.new_(a, E2(false));
            a.origin = here;
            here = Scanner.offset;
        }
        return a;
    }

    private static Expr.T E2(boolean types) {
        Expr.T a;
        long n;
        long here = Scanner.offset;
        n = 0;
        while ((Scanner.cur.token == Token.T.tNOT)) {
            Scanner.getToken(); // NOT
            n++;
        }
        a = E3(types);
        if ((n > 0)) {
            a = NotExpr.new_(a);
            a.origin = here;
            if ((Math.floorMod(n, 2) == 0)) {
                a = NotExpr.new_(a);
                a.origin = here;
            }
        }
        return a;
    }

    private static final EnumSet<Token.T> RelOps =
        EnumSet.of(Token.T.tEQUAL, Token.T.tSHARP, Token.T.tLESS, Token.T.tLSEQUAL,
                   Token.T.tGREATER, Token.T.tGREQUAL, Token.T.tIN);

    private static Expr.T E3(boolean types) {
        Expr.T a, b;
        Token.T t;
        long here = Scanner.offset;
        a = E4(types);
        while (RelOps.contains(Scanner.cur.token)) {
            t = Scanner.cur.token;
            Scanner.getToken();
            b = E4(false);
            switch (t) {
                case tEQUAL -> a = EqualExpr.new_(a, b, M3CG.CompareOp.EQ);
                case tSHARP -> a = EqualExpr.new_(a, b, M3CG.CompareOp.NE);
                case tLESS -> a = CompareExpr.new_(a, b, M3CG.CompareOp.LT);
                case tLSEQUAL -> a = CompareExpr.new_(a, b, M3CG.CompareOp.LE);
                case tGREATER -> a = CompareExpr.new_(a, b, M3CG.CompareOp.GT);
                case tGREQUAL -> a = CompareExpr.new_(a, b, M3CG.CompareOp.GE);
                case tIN -> a = InExpr.new_(a, b);
                default -> throw new AssertError("ExprParse");
            }
            a.origin = here;
            here = Scanner.offset;
        }
        return a;
    }

    private static final EnumSet<Token.T> AddOps =
        EnumSet.of(Token.T.tPLUS, Token.T.tMINUS, Token.T.tAMPERSAND);

    private static Expr.T E4(boolean types) {
        Expr.T a, b;
        Token.T t;
        long here = Scanner.offset;
        a = E5(types);
        while (AddOps.contains(Scanner.cur.token)) {
            t = Scanner.cur.token;
            Scanner.getToken();
            b = E5(false);
            switch (t) {
                case tPLUS -> a = AddExpr.new_(a, b);
                case tMINUS -> a = SubtractExpr.new_(a, b);
                case tAMPERSAND -> a = ConcatExpr.new_(a, b);
                default -> throw new AssertError("ExprParse");
            }
            a.origin = here;
            here = Scanner.offset;
        }
        return a;
    }

    private static final EnumSet<Token.T> MulOps =
        EnumSet.of(Token.T.tASTERISK, Token.T.tSLASH, Token.T.tDIV, Token.T.tMOD);

    private static Expr.T E5(boolean types) {
        Expr.T a, b;
        Token.T t;
        long here = Scanner.offset;
        a = E6(types);
        while (MulOps.contains(Scanner.cur.token)) {
            t = Scanner.cur.token;
            Scanner.getToken();
            b = E6(false);
            switch (t) {
                case tASTERISK -> a = MultiplyExpr.new_(a, b);
                case tSLASH -> a = DivideExpr.new_(a, b);
                case tDIV -> a = DivExpr.new_(a, b);
                case tMOD -> a = ModExpr.new_(a, b);
                default -> throw new AssertError("ExprParse");
            }
            a.origin = here;
            here = Scanner.offset;
        }
        return a;
    }

    private static final EnumSet<Token.T> SelectStart =
        EnumSet.of(Token.T.tARROW, Token.T.tDOT, Token.T.tLBRACKET, Token.T.tLPAREN,
                   Token.T.tLBRACE, Token.T.tBRANDED, Token.T.tOBJECT);

    private static Expr.T E6(boolean types) {
        Expr.T a, b;
        long p, m;
        long here = Scanner.offset;
        p = 0;
        m = 0;
        while (true) {
            if ((Scanner.cur.token == Token.T.tPLUS)) {
                Scanner.getToken();
                p++;
            } else if ((Scanner.cur.token == Token.T.tMINUS)) {
                Scanner.getToken();
                m++;
            } else {
                break;
            }
        }
        a = E8(types);
        b = null;
        while ((a != b) && SelectStart.contains(Scanner.cur.token)) {
            b = a;
            a = eSelector(types, a);
            a.origin = here;
            here = Scanner.offset;
        }
        if ((p + m > 0)) {
            if ((Math.floorMod(m, 2) == 1)) {
                a = NegateExpr.new_(a);
            } else {
                a = PlusExpr.new_(a); // get the typechecking
            }
            a.origin = here;
        }
        return a;
    }

    private static Expr.T E8(boolean types) {
        Expr.T a;
        long here = Scanner.offset;
        switch (Scanner.cur.token) {
            case tIDENT -> {
                a = NamedExpr.new_(Scanner.cur.id, Scanner.cur.defn);
                Scanner.getToken();
            }
            case tCARDCONST -> {
                a = IntegerExpr.new_(Int.T, Scanner.cur.int_);
                Scanner.getToken();
            }
            case tLONGCARDCONST -> {
                a = IntegerExpr.new_(LInt.T, Scanner.cur.int_);
                Scanner.getToken();
            }
            case tCHARCONST -> {
                a = EnumExpr.new_(Charr.T, Scanner.cur.int_);
                Scanner.getToken();
            }
            case tWCHARCONST -> {
                a = EnumExpr.new_(WCharr.T, Scanner.cur.int_);
                Scanner.getToken();
            }
            case tTEXTCONST -> {
                a = TextExpr.new8(Scanner.cur.str);
                Scanner.getToken();
            }
            case tWTEXTCONST -> {
                a = TextExpr.new32(Scanner.cur.wstr);
                Scanner.getToken();
            }
            case tREALCONST -> {
                a = ReelExpr.new_(Scanner.cur.float_);
                Scanner.getToken();
            }
            case tLONGREALCONST -> {
                a = ReelExpr.new_(Scanner.cur.float_);
                Scanner.getToken();
            }
            case tEXTENDEDCONST -> {
                a = ReelExpr.new_(Scanner.cur.float_);
                Scanner.getToken();
            }

            case tLPAREN -> {
                Scanner.getToken();
                a = E0(types);
                Scanner.match(Token.T.tRPAREN);
            }

            case tARRAY, tBITS, tRECORD, tSET -> {
                a = TypeExpr.new_(Type.parse());
                a.origin = here;
                if ((!types) && (Scanner.cur.token != Token.T.tLBRACE)) {
                    Error.msg("expected a constructor");
                }
            }

            case tBRANDED, tLBRACE, tUNTRACED, tOBJECT,
                 tPROCEDURE, tREF, tLBRACKET, tCALLCONV -> {
                if (!types) {
                    Error.msg("unexpected type expression");
                }
                a = TypeExpr.new_(Type.parse());
                a.origin = here;
            }

            default -> {
                Scanner.fail("bad expression");
                a = IntegerExpr.new_(Int.T, TInt.Zero);
            }
        }
        return a;
    }

    private static Expr.T eSelector(boolean types, Expr.T a) {
        Expr.T[] args;
        Type.T t;
        boolean[] open = new boolean[1];
        int[] name = new int[1], module = new int[1];
        Brand.T brand;
        long here = Scanner.offset;
        switch (Scanner.cur.token) {
            case tARROW -> {
                Scanner.getToken(); // ^
                a = DerefExpr.new_(a);
                a.origin = here;
            }
            case tDOT -> {
                Scanner.getToken(); // .
                a = QualifyExpr.new_(a, Scanner.matchID());
                a.origin = here;
            }
            case tLBRACKET -> {
                Scanner.getToken(); // [
                while (true) {
                    a = SubscriptExpr.new_(a, E0(false));
                    a.origin = here;
                    here = Scanner.offset;
                    if ((Scanner.cur.token != Token.T.tCOMMA)) {
                        break;
                    }
                    Scanner.getToken(); // ,
                }
                Scanner.match(Token.T.tRBRACKET);
            }
            case tLPAREN -> {
                Scanner.getToken(); // (
                args = parseArgList();
                Scanner.match(Token.T.tRPAREN);
                a = CallExpr.new_(a, args);
                a.origin = here;
            }
            case tLBRACE -> {
                Scanner.getToken(); // {
                args = parseConsList(open);
                Scanner.match(Token.T.tRBRACE);
                a = ConsExpr.new_(a, args, open[0]);
                a.origin = here;
            }
            case tBRANDED, tOBJECT -> {
                if ((types)) {
                    brand = Brand.parse();
                    if (NamedExpr.splitName(a, name)) {
                        t = NamedType.create(M3ID.NoID, name[0]);
                    } else if (QualifyExpr.splitQID(a, module, name)) {
                        t = NamedType.create(module[0], name[0]);
                    } else {
                        t = null;
                        Scanner.fail("bad selector");
                    }
                    a = TypeExpr.new_(ObjectType.parse(t, true, brand));
                    a.origin = here;
                }
            }
            default -> Scanner.fail("bad selector");
        }
        return a;
    }

    // Port: RefExprList = REF ARRAY OF Expr.T is Expr.T[].

    /** Port: NUMBER (args) of the local arrays args. */
    private static final int NumArgs = 10;

    private static Expr.T[] parseArgList() {
        int i = 0;
        Expr.T e;
        Expr.T[] result;
        Expr.T[] args = new Expr.T[NumArgs];
        Expr.T[] args2 = null;
        if ((Scanner.cur.token != Token.T.tRPAREN)) {
            while (true) {
                e = eActual();
                if ((i < args.length)) {
                    args[i] = e;
                } else if ((i == args.length)) {
                    args2 = expand(args);
                    args2[i] = e;
                } else if ((i == args2.length)) {
                    args2 = expand(args2);
                    args2[i] = e;
                } else {
                    args2[i] = e;
                }
                i++;
                if ((Scanner.cur.token != Token.T.tCOMMA)) {
                    break;
                }
                Scanner.getToken(); // ,
            }
        }
        result = new Expr.T[i];
        if ((i <= args.length)) {
            for (int j = 0; j <= i - 1; j++) {
                result[j] = args[j];
            }
        } else {
            for (int j = 0; j <= i - 1; j++) {
                result[j] = args2[j];
            }
        }
        return result;
    }

    /** Port: VAR open is {@code open[0]}. */
    private static Expr.T[] parseConsList(boolean[] open) {
        int i = 0;
        Expr.T e;
        Expr.T[] result;
        Expr.T[] args = new Expr.T[NumArgs];
        Expr.T[] args2 = null;
        open[0] = false;
        if ((Scanner.cur.token != Token.T.tRBRACE)) {
            while (true) {
                if ((Scanner.cur.token == Token.T.tDOTDOT)) {
                    // must be the end of an array constructor
                    if ((i == 0)) {
                        Error.msg("array constructor has no values");
                    }
                    open[0] = true;
                    Scanner.getToken(); // ..
                    break;
                }
                e = eConstructor();
                if ((i < args.length)) {
                    args[i] = e;
                } else if ((i == args.length)) {
                    args2 = expand(args);
                    args2[i] = e;
                } else if ((i == args2.length)) {
                    args2 = expand(args2);
                    args2[i] = e;
                } else {
                    args2[i] = e;
                }
                i++;
                if ((Scanner.cur.token != Token.T.tCOMMA)) {
                    break;
                }
                Scanner.getToken(); // ,
            }
        }
        result = new Expr.T[i];
        if ((i <= args.length)) {
            for (int j = 0; j <= i - 1; j++) {
                result[j] = args[j];
            }
        } else {
            for (int j = 0; j <= i - 1; j++) {
                result[j] = args2[j];
            }
        }
        return result;
    }

    private static Expr.T[] expand(Expr.T[] old) {
        Expr.T[] new_ = new Expr.T[Math.max(200, 2 * old.length)];
        for (int i = 0; i <= old.length - 1; i++) {
            new_[i] = old[i];
        }
        return new_;
    }

    private static Expr.T eActual() {
        Expr.T a;
        int[] name = new int[1];
        long here = Scanner.offset;
        a = E0(true);
        if ((Scanner.cur.token == Token.T.tASSIGN)) {
            Scanner.getToken(); // :=
            if (NamedExpr.splitName(a, name)) {
                a = KeywordExpr.new_(name[0], E0(false));
                a.origin = here;
            } else {
                Error.msg("syntax error: expected 'keyword := value'");
            }
        }
        return a;
    }

    private static Expr.T eConstructor() {
        Expr.T a;
        int[] name = new int[1];
        long here = Scanner.offset;
        a = E0(false);
        if ((Scanner.cur.token == Token.T.tDOTDOT)) {
            Scanner.getToken(); // ..
            a = RangeExpr.new_(a, E0(false));
            a.origin = here;
        } else if ((Scanner.cur.token == Token.T.tASSIGN)) {
            Scanner.getToken(); // :=
            if (NamedExpr.splitName(a, name)) {
                a = KeywordExpr.new_(name[0], E0(false));
                a.origin = here;
            } else {
                Error.msg("syntax error: expected 'keyword := value'");
            }
        }
        return a;
    }
}
