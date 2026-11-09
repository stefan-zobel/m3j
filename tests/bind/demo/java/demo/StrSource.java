// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package demo;

/** The result of the method of Source, made more specific. */
public interface StrSource extends Source {
    @Override
    String get();

    static String shown(StrSource s) {
        return s.get();
    }
}
