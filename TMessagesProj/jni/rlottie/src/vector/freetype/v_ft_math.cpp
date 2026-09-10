/***************************************************************************/
/*                                                                         */
/*  fttrigon.c                                                             */
/*                                                                         */
/*    FreeType trigonometric functions (body).                             */
/*                                                                         */
/*  Copyright 2001-2005, 2012-2013 by                                      */
/*  David Turner, Robert Wilhelm, and Werner Lemberg.                      */
/*                                                                         */
/*  This file is part of the FreeType project, and may only be used,       */
/*  modified, and distributed under the terms of the FreeType project      */
/*  license, LICENSE.TXT.  By continuing to use, modify, or distribute     */
/*  this file you indicate that you have read the license and              */
/*  understand and accept it fully.                                        */
/*                                                                         */
/***************************************************************************/

#include "v_ft_math.h"
#include <math.h>

//form https://github.com/chromium/chromium/blob/59afd8336009c9d97c22854c52e0382b62b3aa5e/third_party/abseil-cpp/absl/base/internal/bits.h

#if defined(_MSC_VER)
#include <intrin.h>
static unsigned int __inline clz(unsigned int x) {
	unsigned long r = 0;
	if (x != 0)
	{
		_BitScanReverse(&r, x);
	}
	return  r;
}
#define SW_FT_MSB(x)  (clz(x))
#elif defined(__GNUC__)
#define SW_FT_MSB(x)  (31 - __builtin_clz(x))
#else
static unsigned int __inline clz(unsigned int x) {
	int c = 31;
	x &= ~x + 1;
	if (n & 0x0000FFFF) c -= 16;
	if (n & 0x00FF00FF) c -= 8;
	if (n & 0x0F0F0F0F) c -= 4;
	if (n & 0x33333333) c -= 2;
	if (n & 0x55555555) c -= 1;
	return c;
}
#define SW_FT_MSB(x)  (clz(x))
#endif





#define SW_FT_PAD_FLOOR(x, n) ((x) & ~((n)-1))
#define SW_FT_PAD_ROUND(x, n) SW_FT_PAD_FLOOR((x) + ((n) / 2), n)
#define SW_FT_PAD_CEIL(x, n) SW_FT_PAD_FLOOR((x) + ((n)-1), n)

#define SW_FT_BEGIN_STMNT do {
#define SW_FT_END_STMNT \
    }                   \
    while (0)
/* transfer sign leaving a positive number */
#define SW_FT_MOVE_SIGN(x, s) \
    SW_FT_BEGIN_STMNT         \
    if (x < 0) {              \
        x = -x;               \
        s = -s;               \
    }                         \
    SW_FT_END_STMNT

SW_FT_Long SW_FT_MulFix(SW_FT_Long a, SW_FT_Long b)
{
    SW_FT_Int  s = 1;
    SW_FT_Long c;

    SW_FT_MOVE_SIGN(a, s);
    SW_FT_MOVE_SIGN(b, s);

    c = (SW_FT_Long)(((SW_FT_Int64)a * b + 0x8000L) >> 16);

    return (s > 0) ? c : -c;
}

SW_FT_Long SW_FT_MulDiv(SW_FT_Long a, SW_FT_Long b, SW_FT_Long c)
{
    SW_FT_Int  s = 1;
    SW_FT_Long d;

    SW_FT_MOVE_SIGN(a, s);
    SW_FT_MOVE_SIGN(b, s);
    SW_FT_MOVE_SIGN(c, s);

    d = (SW_FT_Long)(c > 0 ? ((SW_FT_Int64)a * b + (c >> 1)) / c : 0x7FFFFFFFL);

    return (s > 0) ? d : -d;
}

SW_FT_Long SW_FT_DivFix(SW_FT_Long a, SW_FT_Long b)
{
    SW_FT_Int  s = 1;
    SW_FT_Long q;

    SW_FT_MOVE_SIGN(a, s);
    SW_FT_MOVE_SIGN(b, s);

    q = (SW_FT_Long)(b > 0 ? (((SW_FT_UInt64)a << 16) + (b >> 1)) / b
                           : 0x7FFFFFFFL);

    return (s < 0 ? -q : q);
}

/*************************************************************************/
/*                                                                       */
/* Trigonometric / polar conversions on 16.16 fixed-point angles in     */
/* degrees.  Upstream FreeType implements these with an integer CORDIC  */
/* (~22 shift-add pseudo-rotations plus prenorm/downscale per call),    */
/* written for FPU-less targets.  On the arm64/arm CPUs we ship to, the */
/* hardware FPU evaluates sin/cos/atan2/sqrt in a fraction of that      */
/* time, and the stroker calls these on every curve segment of every    */
/* stroked shape each frame.  So they are implemented with float math   */
/* here (sincosf/atan2f/hypotf); at emoji-scale coordinates float32     */
/* stays within a few 16.16 ULPs of CORDIC, and float libm is           */
/* measurably faster than double on the in-order cores where decode     */
/* time actually hurts.                                                 */
/*                                                                       */
/*************************************************************************/

/* 16.16 degrees -> radians */
#define SW_FT_ANGLE_TO_RAD(a) ((float)(a) * (float)(M_PI / (180.0 * 65536.0)))
/* radians -> 16.16 degrees */
#define SW_FT_RAD_TO_ANGLE(r) ((r) * (float)(180.0 * 65536.0 / M_PI))

static inline void ft_sincos(SW_FT_Angle theta, float* s, float* c)
{
    float rad = SW_FT_ANGLE_TO_RAD(theta);
#if defined(__ANDROID__) || defined(__GLIBC__)
    sincosf(rad, s, c);
#else
    *s = sinf(rad);
    *c = cosf(rad);
#endif
}

/* documentation is in fttrigon.h */

SW_FT_Fixed SW_FT_Cos(SW_FT_Angle angle)
{
    return (SW_FT_Fixed) llroundf(cosf(SW_FT_ANGLE_TO_RAD(angle)) * 65536.0f);
}

/* documentation is in fttrigon.h */

SW_FT_Fixed SW_FT_Sin(SW_FT_Angle angle)
{
    return (SW_FT_Fixed) llroundf(sinf(SW_FT_ANGLE_TO_RAD(angle)) * 65536.0f);
}

/* documentation is in fttrigon.h */

SW_FT_Fixed SW_FT_Tan(SW_FT_Angle angle)
{
    return (SW_FT_Fixed) llroundf(tanf(SW_FT_ANGLE_TO_RAD(angle)) * 65536.0f);
}

/* documentation is in fttrigon.h */

SW_FT_Angle SW_FT_Atan2(SW_FT_Fixed dx, SW_FT_Fixed dy)
{
    if (dx == 0 && dy == 0) return 0;

    return (SW_FT_Angle) llroundf(SW_FT_RAD_TO_ANGLE(atan2f((float) dy, (float) dx)));
}

/* documentation is in fttrigon.h */

void SW_FT_Vector_Unit(SW_FT_Vector* vec, SW_FT_Angle angle)
{
    float s, c;
    ft_sincos(angle, &s, &c);
    vec->x = (SW_FT_Pos) llroundf(c * 65536.0f);
    vec->y = (SW_FT_Pos) llroundf(s * 65536.0f);
}

/* documentation is in fttrigon.h */

void SW_FT_Vector_Rotate(SW_FT_Vector* vec, SW_FT_Angle angle)
{
    float s, c, x, y;

    if (!angle || (vec->x == 0 && vec->y == 0)) return;

    ft_sincos(angle, &s, &c);
    x = (float)vec->x;
    y = (float)vec->y;
    vec->x = (SW_FT_Pos) llroundf(x * c - y * s);
    vec->y = (SW_FT_Pos) llroundf(x * s + y * c);
}

/* documentation is in fttrigon.h */

SW_FT_Fixed SW_FT_Vector_Length(SW_FT_Vector* vec)
{
    SW_FT_Vector v = *vec;

    /* handle trivial cases */
    if (v.x == 0) {
        return SW_FT_ABS(v.y);
    } else if (v.y == 0) {
        return SW_FT_ABS(v.x);
    }

    return (SW_FT_Fixed) llroundf(hypotf((float) v.x, (float) v.y));
}

/* documentation is in fttrigon.h */

void SW_FT_Vector_Polarize(SW_FT_Vector* vec, SW_FT_Fixed* length,
                           SW_FT_Angle* angle)
{
    SW_FT_Vector v = *vec;

    if (v.x == 0 && v.y == 0) return;

    *length = (SW_FT_Fixed) llroundf(hypotf((float) v.x, (float) v.y));
    *angle = (SW_FT_Angle) llroundf(SW_FT_RAD_TO_ANGLE(atan2f((float) v.y, (float) v.x)));
}

/* documentation is in fttrigon.h */

void SW_FT_Vector_From_Polar(SW_FT_Vector* vec, SW_FT_Fixed length,
                             SW_FT_Angle angle)
{
    float s, c;
    ft_sincos(angle, &s, &c);
    vec->x = (SW_FT_Pos) llroundf((float) length * c);
    vec->y = (SW_FT_Pos) llroundf((float) length * s);
}

/* documentation is in fttrigon.h */

SW_FT_Angle SW_FT_Angle_Diff( SW_FT_Angle  angle1, SW_FT_Angle  angle2 )
{
  SW_FT_Angle  delta = angle2 - angle1;

  while ( delta <= -SW_FT_ANGLE_PI )
    delta += SW_FT_ANGLE_2PI;

  while ( delta > SW_FT_ANGLE_PI )
    delta -= SW_FT_ANGLE_2PI;

  return delta;
}

/* END */
