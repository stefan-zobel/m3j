// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package demo;

/** A method that a subinterface declares again with a more specific result. */
public interface Source {
    Object get();

    static String show(Source s) {
        return (String) s.get();
    }
}
