// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.debug;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * The types of the units of a program: the types of the descriptions of
 * its units (Info) taken together. A unit describes the types that its
 * variables need; the names of types and the concrete types of opaque
 * types may come from other units.
 */
public final class TypeTable {

    private final Map<String, Info.Type> types = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();

    /** Adds the types of a unit. */
    public void add(Info info) {
        for (Map.Entry<String, Info.Type> e : info.types.entrySet()) {
            Info.Type old = types.get(e.getKey());
            Info.Type t = e.getValue();
            if (old instanceof Info.Opaque o && t instanceof Info.Opaque n) {
                // the revelation and the declaration may be in different units
                t = new Info.Opaque(n.supertype() != null ? n.supertype() : o.supertype(),
                                    n.concrete() != null ? n.concrete() : o.concrete());
            } else if (old != null && !(old instanceof Info.Opaque)) {
                continue;
            }
            types.put(e.getKey(), t);
        }
        for (Map.Entry<String, List<String>> e : info.names.entrySet()) {
            names.putIfAbsent(e.getKey(), e.getValue().getFirst());
        }
    }

    /** The description of type t, or null (a builtin type, or a type that no unit describes). */
    public Info.Type get(String t) {
        return types.get(t);
    }

    /** The type t with indirections, packing and revealed opaque types resolved. */
    public String resolve(String t) {
        for (int i = 0; i < 20; i++) {
            Info.Type d = types.get(t);
            if (d instanceof Info.Indirect x) {
                t = x.target();
            } else if (d instanceof Info.Packed p) {
                t = p.base();
            } else if (d instanceof Info.Opaque o && o.concrete() != null) {
                t = o.concrete();
            } else {
                break;
            }
        }
        return t;
    }

    /** The name of type t (Unit.Name, or a builtin type), or null. */
    public String name(String t) {
        if (Info.BUILTINS.contains(t)) {
            return t;
        }
        return names.get(t);
    }

    /** Type t as Modula-3: its name if it has one, else its structure. */
    public String show(String t) {
        return show(t, 3);
    }

    private String show(String t, int depth) {
        String n = name(t);
        if (n != null) {
            return n;
        }
        Info.Type d = types.get(t);
        if (d == null || depth == 0) {
            return d == null ? "<" + t + ">" : "...";
        }
        int k = depth - 1;
        return switch (d) {
            case Info.Enum e -> "{" + String.join(", ", e.elements()) + "}";
            case Info.Subrange s -> "[" + ordinal(s.base(), s.first()) + ".." + ordinal(s.base(), s.last()) + "]";
            case Info.Array a -> "ARRAY " + show(a.index(), k) + " OF " + show(a.element(), k);
            case Info.OpenArray a -> "ARRAY OF " + show(a.element(), k);
            case Info.SetType s -> "SET OF " + show(s.base(), k);
            case Info.Record r -> "RECORD " + fields(r.fields(), k) + "END";
            case Info.Ref r -> (r.traced() ? "REF " : "UNTRACED REF ") + show(r.target(), k);
            case Info.ObjectType o -> (o.supertype().equals("ROOT") ? "" : show(o.supertype(), k) + " ")
                + "OBJECT " + fields(o.fields(), k) + "END";
            case Info.Opaque o -> o.concrete() != null ? show(o.concrete(), k)
                : "<: " + (o.supertype() == null ? "?" : show(o.supertype(), k));
            case Info.Packed p -> "BITS " + p.bits() + " FOR " + show(p.base(), k);
            case Info.Indirect x -> show(x.target(), k);
        };
    }

    private String fields(List<Info.Field> fs, int depth) {
        StringJoiner j = new StringJoiner("; ", "", fs.isEmpty() ? "" : " ");
        for (Info.Field f : fs) {
            j.add(f.name() + ": " + show(f.type(), depth));
        }
        return j.toString();
    }

    /** The value v of an ordinal type t: an element of an enumeration by its name, else the number. */
    public String ordinal(String t, long v) {
        String r = resolve(t);
        if (types.get(r) instanceof Info.Subrange s) {
            return ordinal(s.base(), v);
        }
        if (types.get(r) instanceof Info.Enum e && v >= 0 && v < e.elements().size()) {
            return e.elements().get((int) v);
        }
        if (r.equals("BOOLEAN") && (v == 0 || v == 1)) {
            return v == 0 ? "FALSE" : "TRUE";
        }
        if (r.equals("CHAR") && v >= 0 && v <= 0xFF) {
            return charLiteral((int) v, false);
        }
        if (r.equals("WIDECHAR") && v >= 0 && v <= 0x10FFFF) {
            return charLiteral((int) v, true);
        }
        return Long.toString(v);
    }

    /** Character c as a literal of Modula-3: 'a', '\n', '\377'; W'a', W'\x20ac' if wide. */
    public static String charLiteral(int c, boolean wide) {
        return (wide ? "W'" : "'") + escape(c, wide, '\'') + "'";
    }

    /** Character c in a literal of Modula-3 delimited by quote. */
    public static String escape(int c, boolean wide, char quote) {
        return switch (c) {
            case '\n' -> "\\n";
            case '\t' -> "\\t";
            case '\r' -> "\\r";
            case '\f' -> "\\f";
            case '\\' -> "\\\\";
            default -> {
                if (c == quote) {
                    yield "\\" + quote;
                } else if (c >= 0x20 && c < 0x7F) {
                    yield String.valueOf((char) c);
                } else if (!wide || c <= 0xFF) {
                    yield String.format("\\%03o", c);
                } else {
                    yield String.format("\\x%04x", c);
                }
            }
        };
    }
}
