// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package demo;

/** An abstract class has no constructors in its binding. */
public abstract class Shape {
    public Shape() {}

    public abstract int sides();

    public static Shape triangle() {
        return new Shape() {
            @Override
            public int sides() {
                return 3;
            }
        };
    }
}
