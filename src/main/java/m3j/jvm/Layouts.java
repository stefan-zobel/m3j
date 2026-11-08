// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.util.List;
import java.util.StringJoiner;

import m3j.front.ArrayType;
import m3j.front.Field;
import m3j.front.ObjectType;
import m3j.front.OpenArrayType;
import m3j.front.Type;

/**
 * The layout of the referents of a type in the memory of CM3 (AMD64_NT),
 * for Pickle: every class of a record, reference or object type gets a
 * constant M3$PKL that describes where CM3 keeps each field, so that the
 * runtime (m3.i.M3jPickle) can write and read the bytes of a value as a
 * CM3 program does. The offsets and sizes come from the front end, which
 * computes them as CM3 does.
 *
 * <p>The description of a class is a kind, numbers and a list of fields:
 * <pre>
 *   R size items            a record of size bits
 *   P size items            REF T: the referent (size bits), its fields
 *                           (of the record T, or v for any other T)
 *   O size align items      one level of an object type: the fields that
 *                           the type adds, size and alignment in bits
 *   A depth pack elem       REF ARRAY OF ...: depth open dimensions, the
 *                           storage a with elements of pack bits
 *   !                       a type that cannot be described
 * </pre>
 * items are separated by ';', each NAME@OFFSET=ELEM, the offset in bits.
 * An element is: iN (an ordinal of N bits, signed), uN (unsigned), fN
 * (REAL), dN (LONGREAL, EXTENDED), sN (a set of N bits in a long), SN (a
 * larger set, in N/64 longs), r (a traced reference), xN (an untraced
 * reference or a procedure, which a pickle leaves out), { (a record: the
 * object's own class describes it), [N,STRIDE,ELEM] (a fixed array of N
 * elements STRIDE bits apart, stored flat as in Aggregates).
 */
final class Layouts {
    private Layouts() {}

    /** The description of the class of type t (a record, reference or object type). */
    static String describe(Type.T t) {
        try {
            Type.T u = strip(t);
            return switch (u.info.class_) {
                case Record -> "R " + u.info.size + " " + items(Types.fields(u));
                case Object -> object(u);
                case Ref -> ref(u);
                default -> "!";
            };
        } catch (RuntimeException e) {
            // Unsupported, or a type the front end cannot lay out: Pickle
            // reports that it cannot pickle it
            return "!";
        }
    }

    private static String object(Type.T u) {
        ObjectType.P p = (ObjectType.P) u;
        long align = ObjectType.fieldAlignment(u); // computes the sizes
        return "O " + p.fieldSize + " " + align + " " + items(Types.objectFields(u));
    }

    private static String ref(Type.T u) {
        Type.T target = Types.target(u);
        if (target == null) {
            throw new Unsupported();
        }
        Type.T tu = strip(target);
        switch (tu.info.class_) {
            case Record:
                return "P " + tu.info.size + " " + items(Types.fields(tu));
            case OpenArray: {
                Type.T e = Aggregates.nonOpenElement(tu);
                return "A " + Aggregates.openDepth(tu) + " " + OpenArrayType.eltPack(tu) + " " + elem(e);
            }
            default:
                return "P " + Type.check(target).info.size + " v@0=" + elem(target);
        }
    }

    private static String items(List<Field.T> fs) {
        StringJoiner j = new StringJoiner(";");
        for (Field.T f : fs) {
            j.add(Code.id(f.name) + "@" + f.offset + "=" + elem(f.tipe));
        }
        return j.toString();
    }

    /** The element of a value of type t (which may be packed). */
    private static String elem(Type.T t) {
        long size = Type.check(t).info.size;
        Type.T u = strip(t);
        switch (u.info.class_) {
            case Integer, Longint, Enum, Subrange: {
                JType j = JType.of(u);
                boolean signed = j.kind != JType.Kind.BOOL && j.kind != JType.Kind.CHAR
                    && j.kind != JType.Kind.WCHAR && j.min < 0;
                return (signed ? "i" : "u") + size;
            }
            case Real:
                return "f" + size;
            case Longreal, Extended:
                return "d" + size;
            case Set:
                return (JType.of(u).kind == JType.Kind.BIGSET ? "S" : "s") + size;
            case Ref, Object, Opaque:
                return u.info.isTraced ? "r" : "x" + size;
            case Procedure:
                return "x" + size;
            case Record:
                return "{";
            case Array: {
                Type.T[] idx = new Type.T[1], elt = new Type.T[1];
                ArrayType.split(u, idx, elt);
                long n = JType.toLong(Type.number(idx[0]));
                return "[" + n + "," + ArrayType.eltPack(u) + "," + elem(elt[0]) + "]";
            }
            default:
                throw new Unsupported();
        }
    }

    private static Type.T strip(Type.T t) {
        return Type.stripPacked(Type.check(t));
    }

    /** A type whose layout is not described. */
    private static final class Unsupported extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Unsupported() {
            super(null, null, false, false);
        }
    }
}
