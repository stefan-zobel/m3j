// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package late;

/** A class whose members mention a class that is removed after compiling. */
public class Needs {
    public Needs(Gone g) {}

    public Gone make() {
        return new Gone();
    }

    public static int one() {
        return 1;
    }
}
