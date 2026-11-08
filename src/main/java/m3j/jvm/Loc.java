// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

import m3j.front.Type;

/**
 * A location: where a variable, a field or an element lives. A location of
 * a scalar type loads and stores the value; storing is split in two, so
 * that the operands of the location (an object, an array and an index) can
 * be pushed before the value. A location of a record type loads the record
 * object; storing copies into it. Arrays and large sets are {@link Arr}.
 */
sealed interface Loc {

    JType type();

    /** Pushes the value. */
    void load(CodeBuilder cb);

    /** Pushes what a store needs before the value. */
    default void storePre(CodeBuilder cb) {}

    /** Stores the value on the stack (after storePre). */
    void storePost(CodeBuilder cb);

    /** Stores the value on the stack, for locations without operands. */
    default void store(CodeBuilder cb) {
        storePost(cb);
    }

    /** A JVM local. */
    record Local(int slot, JType type) implements Loc {
        @Override
        public void load(CodeBuilder cb) {
            cb.loadLocal(type.typeKind(), slot);
        }

        @Override
        public void storePost(CodeBuilder cb) {
            cb.storeLocal(type.typeKind(), slot);
        }
    }

    /** A static field. */
    record Static(ClassDesc owner, String name, JType type) implements Loc {
        @Override
        public void load(CodeBuilder cb) {
            cb.getstatic(owner, name, type.desc);
        }

        @Override
        public void storePost(CodeBuilder cb) {
            cb.putstatic(owner, name, type.desc);
        }
    }

    /** A field of the object that obj loads. */
    record Field(Loc obj, ClassDesc owner, String name, JType type) implements Loc {
        @Override
        public void load(CodeBuilder cb) {
            obj.load(cb);
            cb.getfield(owner, name, type.desc);
        }

        @Override
        public void storePre(CodeBuilder cb) {
            obj.load(cb);
        }

        @Override
        public void storePost(CodeBuilder cb) {
            cb.putfield(owner, name, type.desc);
        }

        @Override
        public void store(CodeBuilder cb) {
            throw new IllegalStateException("Field.store needs storePre");
        }
    }

    /** An element of an array: the array and an int index, both in locals. */
    record Elem(Loc array, Loc index, Aggregates.Store store, JType type) implements Loc {
        @Override
        public void load(CodeBuilder cb) {
            array.load(cb);
            index.load(cb);
            Aggregates.arrayLoad(cb, store);
        }

        @Override
        public void storePre(CodeBuilder cb) {
            array.load(cb);
            index.load(cb);
        }

        @Override
        public void storePost(CodeBuilder cb) {
            Aggregates.arrayStore(cb, store);
        }

        @Override
        public void store(CodeBuilder cb) {
            throw new IllegalStateException("Elem.store needs storePre");
        }
    }

    /** A VAR parameter of a scalar type: a reference object (LongRef, IntRef, ...). */
    record VarRef(Loc ref, JType type) implements Loc {
        @Override
        public void load(CodeBuilder cb) {
            ref.load(cb);
            cb.invokevirtual(Refs.refClass(type), "get", MethodTypeDesc.of(Refs.valueDesc(type)));
            Refs.narrow(cb, type);
        }

        @Override
        public void storePre(CodeBuilder cb) {
            ref.load(cb);
        }

        @Override
        public void storePost(CodeBuilder cb) {
            Refs.widen(cb, type);
            cb.invokevirtual(Refs.refClass(type), "set",
                MethodTypeDesc.of(ConstantDescs.CD_void, Refs.valueDesc(type)));
        }

        @Override
        public void store(CodeBuilder cb) {
            throw new IllegalStateException("VarRef.store needs storePre");
        }
    }

    /**
     * The value in a cell (a variable passed VAR lives in one): the field v
     * of the cell that holder loads, in the cell's representation (Object
     * for references), seen as type.
     */
    record Cell(Loc holder, JType type) implements Loc {
        @Override
        public void load(CodeBuilder cb) {
            holder.load(cb);
            cb.getfield(Refs.cellClass(type), "v", Refs.valueDesc(type));
            Refs.narrow(cb, type);
        }

        @Override
        public void storePre(CodeBuilder cb) {
            holder.load(cb);
        }

        @Override
        public void storePost(CodeBuilder cb) {
            Refs.widen(cb, type);
            cb.putfield(Refs.cellClass(type), "v", Refs.valueDesc(type));
        }

        @Override
        public void store(CodeBuilder cb) {
            throw new IllegalStateException("Cell.store needs storePre");
        }
    }

    /** A constant int (an offset or a dimension known at compile time). */
    record ConstInt(int value) implements Loc {
        @Override
        public JType type() {
            return JType.INT;
        }

        @Override
        public void load(CodeBuilder cb) {
            cb.loadConstant(value);
        }

        @Override
        public void storePost(CodeBuilder cb) {
            throw new IllegalStateException("store to a constant");
        }
    }

    /**
     * An array or a large set: its storage, the offset of its first leaf,
     * and its open dimensions (none for fixed arrays and sets).
     */
    record Arr(Loc storage, Loc offset, Loc[] dims, Type.T arrayType, JType type) implements Loc {
        @Override
        public void load(CodeBuilder cb) {
            storage.load(cb);
        }

        @Override
        public void storePost(CodeBuilder cb) {
            throw new IllegalStateException("store to an array location");
        }

        boolean isOpen() {
            return dims.length > 0;
        }
    }

    static TypeKind kind(JType t) {
        return t.typeKind();
    }
}
