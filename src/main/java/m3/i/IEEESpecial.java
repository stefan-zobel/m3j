// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

/**
 * The interface IEEESpecial of m3core: infinities and NaN. The NaNs have the
 * bits of CM3's (RealRep.Nan, LongRealRep.Nan: the exponent all ones and a
 * significand of 1), signaling NaNs, as LongFloat.Class reports them.
 */
public final class IEEESpecial {
    private IEEESpecial() {}

    public static final boolean M3$PROVIDED = true;

    public static float RealNegInf = Float.NEGATIVE_INFINITY;
    public static float RealPosInf = Float.POSITIVE_INFINITY;
    public static float RealNan = Float.intBitsToFloat(0x7f800001);
    public static double LongNegInf = Double.NEGATIVE_INFINITY;
    public static double LongPosInf = Double.POSITIVE_INFINITY;
    public static double LongNan = Double.longBitsToDouble(0x7ff0000100000000L);
    public static double ExtdNegInf = Double.NEGATIVE_INFINITY;
    public static double ExtdPosInf = Double.POSITIVE_INFINITY;
    public static double ExtdNan = Double.longBitsToDouble(0x7ff0000100000000L);
}
