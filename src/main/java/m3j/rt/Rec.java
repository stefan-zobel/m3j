// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** The operations of every record class, for arrays of records. */
public interface Rec {
    /** Copies the fields of another record of the same type into this one. */
    void assign(Object other);

    /** True if another record of the same type has equal fields. */
    boolean same(Object other);

    /** A copy of this record. */
    Object duplicate();
}
