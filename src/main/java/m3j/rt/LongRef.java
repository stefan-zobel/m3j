// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** A VAR parameter of a scalar type whose JVM type is long. */
public abstract class LongRef {
    protected LongRef() {}

    public abstract long get();

    public abstract void set(long v);
}
