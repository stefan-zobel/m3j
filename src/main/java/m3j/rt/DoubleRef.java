// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** A VAR parameter of a scalar type whose JVM type is double. */
public abstract class DoubleRef {
    protected DoubleRef() {}

    public abstract double get();

    public abstract void set(double v);
}
