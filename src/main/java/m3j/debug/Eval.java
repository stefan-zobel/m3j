// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.debug;

/**
 * Expressions of a debugger in Modula-3: designators and the attributes
 * of arrays and ordinal types.
 *
 * <pre>
 *   Expr       = ("FIRST" | "LAST" | "NUMBER") "(" Designator ")" | Designator.
 *   Designator = Name {"." Name | "^" | "[" Index {"," Index} "]"}.
 *   Name       = Ident | Ident "." Ident.
 *   Index      = Literal | Designator.
 * </pre>
 *
 * A name is a variable in scope at the frame, else a variable of a unit
 * (Unit.name, also a variable of a module that its interface does not
 * export). A field of a reference to a record and an element of a
 * reference to an array are reached as in Modula-3, without ^. An index
 * is a literal of the type of the index of the array (a number, a
 * character, the name of an element of an enumeration, plain or
 * qualified) or a designator of an ordinal value.
 */
final class Eval {

    /** The variables that names stand for. */
    interface Names {
        /** The variable name in scope at the frame, or null. */
        Values.Part variable(String name);

        /** The variable name of unit, or null if unit has none of that name. */
        Values.Part global(String unit, String name);

        /** Whether a unit is called name. */
        boolean isUnit(String name);
    }

    private final Values values;
    private final TypeTable types;
    private final Names names;
    private String text;
    private int pos;

    Eval(Values values, TypeTable types, Names names) {
        this.values = values;
        this.types = types;
        this.names = names;
    }

    /** The value of the expression text. */
    Values.Part evaluate(String text) {
        start(text);
        Values.Part p;
        int save = pos;
        String id = ident();
        skip();
        if ((id.equals("FIRST") || id.equals("LAST") || id.equals("NUMBER")) && peek() == '(') {
            pos++;
            Values.Part d = designator();
            expect(')');
            p = values.attribute(id, d);
        } else {
            pos = save;
            p = designator();
        }
        end();
        return p;
    }

    /**
     * The part of p that the selectors in text select: ".f", "^", "[i]"
     * and more of them.
     */
    Values.Part select(Values.Part p, String text) {
        start(text);
        Values.Part r = selectors(p);
        end();
        return r;
    }

    private void start(String t) {
        text = t;
        pos = 0;
        skip();
        if (pos >= text.length()) {
            throw new IllegalArgumentException("an expression is needed");
        }
    }

    private void end() {
        skip();
        if (pos < text.length()) {
            throw error("unexpected " + text.substring(pos));
        }
    }

    private Values.Part designator() {
        skip();
        int at = pos;
        String id = ident();
        Values.Part p = names.variable(id);
        if (p == null) {
            int save = pos;
            skip();
            if (peek() == '.' && names.isUnit(id)) {
                pos++;
                skip();
                String n = ident();
                p = names.global(id, n);
                if (p == null) {
                    throw new IllegalArgumentException(id + " has no variable " + n);
                }
            } else {
                pos = save;
                throw new IllegalArgumentException("unknown name " + text.substring(at, pos));
            }
        }
        return selectors(p);
    }

    private Values.Part selectors(Values.Part start) {
        Values.Part p = start;
        while (true) {
            skip();
            char c = peek();
            if (c == '.') {
                pos++;
                skip();
                p = values.field(p, ident());
            } else if (c == '^') {
                pos++;
                p = values.deref(p);
            } else if (c == '[') {
                pos++;
                do {
                    String it = values.indexType(p);
                    p = values.element(p, index(it));
                    skip();
                } while (accept(','));
                expect(']');
            } else {
                return p;
            }
        }
    }

    /** An index of the type t (null: the index of an open array). */
    private long index(String t) {
        skip();
        char c = peek();
        int at = pos;
        if (c == '-' || c == '+' || Character.isDigit(c)) {
            pos++;
            while (pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_')) {
                pos++;
            }
            return literal(t, text.substring(at, pos));
        }
        if (c == '\'' || c == 'W' && pos + 1 < text.length() && text.charAt(pos + 1) == '\'') {
            pos += c == 'W' ? 2 : 1;
            while (pos < text.length() && text.charAt(pos) != '\'') {
                pos += text.charAt(pos) == '\\' ? 2 : 1;
            }
            if (pos >= text.length()) {
                throw error("a character literal is not closed");
            }
            pos++;
            return literal(t, text.substring(at, pos));
        }
        if (t != null && Character.isLetter(c)) {
            // the name of an element, plain or qualified (Color.Red), unless it is a variable
            String id = ident();
            if (Literal.isElement(types, t, id) && names.variable(id) == null) {
                return Literal.ordinal(types, t, id);
            }
            skip();
            if (peek() == '.' && names.variable(id) == null) {
                pos++;
                skip();
                if (Character.isLetter(peek())) {
                    String el = ident();
                    if (Literal.isElement(types, t, el)) {
                        return Literal.ordinal(types, t, el);
                    }
                }
            }
            pos = at;
        }
        return values.ordinalValue(designator());
    }

    private long literal(String t, String lit) {
        try {
            return t == null ? Literal.integer(lit) : Literal.ordinal(types, t, lit);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not an index: " + lit);
        }
    }

    private String ident() {
        int at = pos;
        if (pos < text.length() && Character.isLetter(text.charAt(pos))) {
            pos++;
            while (pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_')) {
                pos++;
            }
        }
        if (at == pos) {
            throw error(pos < text.length() ? "a name is needed at " + text.substring(pos) : "a name is needed");
        }
        return text.substring(at, pos);
    }

    private void skip() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private boolean accept(char c) {
        skip();
        if (peek() == c) {
            pos++;
            return true;
        }
        return false;
    }

    private void expect(char c) {
        if (!accept(c)) {
            throw error(c + " is needed" + (pos < text.length() ? " at " + text.substring(pos) : ""));
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("syntax error: " + message);
    }
}
