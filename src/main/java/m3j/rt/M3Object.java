// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** The class of ROOT: every Modula-3 object type is a subclass. */
public abstract class M3Object {
    protected M3Object() {}

    /**
     * The keeper of a weak reference with a cleanup procedure to this
     * object, or null (see Weak).
     */
    volatile Object m3$keeper;
}
