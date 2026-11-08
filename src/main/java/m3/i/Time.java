// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

/** The interface Time of m3core: seconds since 1970 as a LONGREAL. */
public final class Time {
    private Time() {}

    public static final boolean M3$PROVIDED = true;

    /** The resolution of Now. */
    public static double Grain = 1.0e-7;

    public static double Now() {
        java.time.Instant i = java.time.Instant.now();
        return i.getEpochSecond() + i.getNano() * 1.0e-9;
    }
}
