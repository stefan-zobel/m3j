// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** A VAR parameter of a scalar type whose JVM type is float. */
public abstract class FloatRef {
    protected FloatRef() {}

    public abstract float get();

    public abstract void set(float v);
}
