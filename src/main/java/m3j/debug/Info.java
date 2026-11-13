// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The description of a unit for a debugger, as m3j writes it into the
 * SourceDebugExtension of the class of the unit (see m3j.jvm.DebugInfo for
 * the format), read.
 *
 * <p>A type is named by a string: the name of a builtin type (INTEGER,
 * TEXT, ...) or the UID of the type in 8 hex digits; {@link #types} and
 * {@link #names} describe the types of the UIDs.
 */
public final class Info {

    /** The first line of a description. */
    public static final String MAGIC = "M3J 1";

    /** The builtin types, by name. */
    public static final List<String> BUILTINS = List.of(
        "INTEGER", "LONGINT", "CARDINAL", "LONGCARD", "BOOLEAN", "CHAR", "WIDECHAR", "REAL", "LONGREAL",
        "EXTENDED", "TEXT", "MUTEX", "REFANY", "ROOT", "UNTRACED-ROOT", "ADDRESS", "NULL");

    /** A variable of a method. */
    public record Var(String name, String type, char kind, String home, int from, int to) {
        /** in the slot of its name (the LocalVariableTable gives its scope) */
        public boolean inSlot() {
            return home.equals(".");
        }

        /** in a field of the frame of its procedure */
        public boolean inFrame() {
            return !inSlot() && !home.equals("-");
        }
    }

    /**
     * A method of Modula-3 code: kind P (a procedure), B (the body of the
     * unit), C (a chunk of the method parent) or L (a loop of the body);
     * name is the name of the procedure (Unit.P, Unit.P.Q) or the unit.
     */
    public record Method(String method, char kind, String name, String parent, List<Var> vars) {}

    /** A field of a record or an object. */
    public record Field(String name, String type) {}

    /** The description of a type. */
    public sealed interface Type {}

    public record Enum(List<String> elements) implements Type {}

    public record Subrange(String base, long first, long last) implements Type {}

    public record Array(String index, String element) implements Type {}

    public record OpenArray(String element) implements Type {}

    public record SetType(String base) implements Type {}

    public record Record(List<Field> fields) implements Type {}

    public record Ref(String target, boolean traced) implements Type {}

    /** One level of an object type: its supertype and the fields it adds. */
    public record ObjectType(String supertype, List<Field> fields, boolean traced) implements Type {}

    /** An opaque type: its supertype, and its concrete type if the unit reveals it (else null). */
    public record Opaque(String supertype, String concrete) implements Type {}

    public record Packed(long bits, String base) implements Type {}

    public record Indirect(String target) implements Type {}

    /** the name of the unit */
    public final String unit;
    /** the generic unit of an instance (a file name), else null */
    public final String generic;
    /** the variables of the unit and their types */
    public final Map<String, String> globals;
    /** the methods of Modula-3 code, by their names */
    public final Map<String, Method> methods;
    /** the types, by UID */
    public final Map<String, Type> types;
    /** the names of types, by UID */
    public final Map<String, List<String>> names;

    private Info(String unit, String generic, Map<String, String> globals, Map<String, Method> methods,
                 Map<String, Type> types, Map<String, List<String>> names) {
        this.unit = unit;
        this.generic = generic;
        this.globals = Collections.unmodifiableMap(globals);
        this.methods = Collections.unmodifiableMap(methods);
        this.types = Collections.unmodifiableMap(types);
        this.names = Collections.unmodifiableMap(names);
    }

    /** The unit of class cls (m3.m.NAME, m3.i.NAME, m3.b.NAME), or null. */
    public static String unitOf(String cls) {
        if (cls.startsWith("m3.m.") || cls.startsWith("m3.i.") || cls.startsWith("m3.b.")) {
            String n = cls.substring(5);
            return n.contains(".") || n.contains("$") ? null : n;
        }
        return null;
    }

    /** Whether text is a description that this reader understands. */
    public static boolean isInfo(String text) {
        return text != null && (text.equals(MAGIC) || text.startsWith(MAGIC + "\n"));
    }

    /**
     * Reads the description text of the class of unit (the name of the
     * unit). A line that it does not understand is skipped, so that an
     * older reader can read a newer text of the same version.
     *
     * @throws IllegalArgumentException if text is not a description
     */
    public static Info parse(String unit, String text) {
        if (!isInfo(text)) {
            throw new IllegalArgumentException("not a description of m3j");
        }
        String generic = null;
        Map<String, String> globals = new LinkedHashMap<>();
        Map<String, Method> methods = new LinkedHashMap<>();
        Map<String, Type> types = new LinkedHashMap<>();
        Map<String, List<String>> names = new LinkedHashMap<>();
        List<Var> vars = null;
        // the record or object whose fields follow, the enumeration whose elements follow
        List<Field> fields = null;
        List<String> elements = null;
        String[] lines = text.split("\n");
        for (int i = 1; i < lines.length; i++) {
            String l = lines[i];
            if (l.isEmpty()) {
                continue;
            }
            String[] t = l.split(" ", -1);
            char tag = l.charAt(0);
            try {
                if (Character.isLowerCase(tag)) {
                    switch (t[0]) {
                        case "g" -> generic = t[1];
                        case "s" -> globals.put(t[1], t[2]);
                        case "m" -> {
                            char kind = t[2].charAt(0);
                            String parent = kind == 'C' || kind == 'L' ? t[3] : null;
                            String name;
                            if (kind == 'P') {
                                name = t.length > 3 ? t[3] : unit + "." + t[1].replace('$', '.');
                            } else if (kind == 'B') {
                                name = unit;
                            } else {
                                Method p = methods.get(parent);
                                name = p != null ? p.name() : unit;
                            }
                            vars = new ArrayList<>();
                            methods.put(t[1], new Method(t[1], kind, name, parent, Collections.unmodifiableList(vars)));
                        }
                        case "v" -> {
                            if (vars != null) {
                                boolean slot = t[4].equals(".");
                                vars.add(new Var(t[1], t[2], t[3].charAt(0), t[4],
                                    slot ? 0 : Integer.parseInt(t[5]), slot ? 0 : Integer.parseInt(t[6])));
                            }
                        }
                        default -> {
                        }
                    }
                    continue;
                }
                String uid = t[0].substring(1);
                if (tag == 'I') {
                    if (elements != null) {
                        elements.add(t[0].substring(1));
                    }
                    continue;
                }
                if (tag == 'L') {
                    if (fields != null) {
                        fields.add(new Field(t[0].substring(1), t[t.length - 1]));
                    }
                    continue;
                }
                fields = null;
                elements = null;
                switch (tag) {
                    case 'E' -> names.computeIfAbsent(uid, k -> new ArrayList<>()).add(t[1]);
                    case 'F' -> types.put(uid, new Array(t[1], t[2]));
                    case 'G' -> types.put(uid, new OpenArray(t[1]));
                    case 'H' -> {
                        elements = new ArrayList<>();
                        types.put(uid, new Enum(Collections.unmodifiableList(elements)));
                    }
                    case 'J' -> types.put(uid, new Packed(Long.parseLong(t[1]), t[2]));
                    case 'K' -> {
                        fields = new ArrayList<>();
                        types.put(uid, new Record(Collections.unmodifiableList(fields)));
                    }
                    case 'M' -> types.put(uid, new SetType(t[1]));
                    case 'N' -> types.put(uid, new Subrange(t[1], Long.parseLong(t[2]), Long.parseLong(t[3])));
                    case 'O', 'P' -> types.put(uid, new Ref(t[1], tag == 'P'));
                    case 'Q' -> types.put(uid, new Indirect(t[1]));
                    case 'U', 'V' -> {
                        fields = new ArrayList<>();
                        types.put(uid, new ObjectType(t[1], Collections.unmodifiableList(fields), tag == 'V'));
                    }
                    case 'Y' -> {
                        Type old = types.get(uid);
                        types.put(uid, new Opaque(t[1], old instanceof Opaque o ? o.concrete() : null));
                    }
                    case 'Z' -> {
                        Type old = types.get(uid);
                        types.put(uid, new Opaque(old instanceof Opaque o ? o.supertype() : null, t[1]));
                    }
                    default -> {
                    }
                }
            } catch (RuntimeException e) {
                // a line of another form: skipped
            }
        }
        return new Info(unit, generic, globals, methods, types, names);
    }
}
