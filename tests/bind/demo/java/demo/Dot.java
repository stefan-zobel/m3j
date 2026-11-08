// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package demo;

import java.io.IOException;

/** A class with what m3jbind has to rename or leave out. */
public class Dot implements Comparable<Dot> {
    public static final int END = 3;
    public static final long LIMIT = 1L << 40;
    public static final String LABEL = "dot";
    public static final Dot ORIGIN = new Dot(0, 0);
    public static final int count = 7;
    public static final Object HIDDEN = new Object();
    public static int notFinal = 1;

    /** The classes of dots. */
    public enum Kind { NEAR, FAR }

    private final int x;
    private final int y;

    public Dot() {
        this(1, 1);
    }

    public Dot(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Dot plus(Dot o) {
        return new Dot(x + o.x, y + o.y);
    }

    public Dot plus(int d) {
        return new Dot(x + d, y + d);
    }

    public long New(long n) {
        return n + x;
    }

    public int count() {
        return x + y;
    }

    public double half() {
        return x / 2.0;
    }

    public char tag(char c) {
        return (char) (c + 1);
    }

    public Kind kind() {
        return x + y < 10 ? Kind.NEAR : Kind.FAR;
    }

    public void fail() throws IOException {
        throw new IOException("no disk");
    }

    public static long sum(long[] a) {
        long s = 0;
        for (long e : a) {
            s += e;
        }
        return s;
    }

    public static Dot parse(String s) {
        int c = s.indexOf(',');
        return new Dot(Integer.parseInt(s.substring(0, c)), Integer.parseInt(s.substring(c + 1)));
    }

    int hiddenFromBindings() {
        return 0;
    }

    @Override
    public int compareTo(Dot o) {
        return Integer.compare(count(), o.count());
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
