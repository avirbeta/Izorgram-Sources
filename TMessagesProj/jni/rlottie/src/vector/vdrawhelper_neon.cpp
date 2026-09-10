#if defined(__ARM_NEON__) || defined(__ARM64_NEON__)

#include "vdrawhelper.h"

extern "C" void pixman_composite_src_n_8888_asm_neon(int32_t w, int32_t h,
                                                     uint32_t *dst,
                                                     int32_t   dst_stride,
                                                     uint32_t  src);

extern "C" void pixman_composite_over_n_8888_asm_neon(int32_t w, int32_t h,
                                                      uint32_t *dst,
                                                      int32_t   dst_stride,
                                                      uint32_t  src);

void memfill32(uint32_t *dest, uint32_t value, int length)
{
    pixman_composite_src_n_8888_asm_neon(length, 1, dest, length, value);
}

void comp_func_solid_SourceOver_neon(uint32_t *dest, int length, uint32_t color,
                                     uint32_t const_alpha)
{
    if (const_alpha != 255) color = BYTE_MUL(color, const_alpha);

    pixman_composite_over_n_8888_asm_neon(length, 1, dest, length, color);
}

#include <arm_neon.h>

/* horizontal reductions: single-instruction on AArch64, pairwise on ARMv7 */
static inline uint8_t neon_hmax_u8(uint8x8_t v)
{
#if defined(__aarch64__)
    return vmaxv_u8(v);
#else
    v = vpmax_u8(v, v);
    v = vpmax_u8(v, v);
    v = vpmax_u8(v, v);
    return vget_lane_u8(v, 0);
#endif
}

static inline uint8_t neon_hmin_u8(uint8x8_t v)
{
#if defined(__aarch64__)
    return vminv_u8(v);
#else
    v = vpmin_u8(v, v);
    v = vpmin_u8(v, v);
    v = vpmin_u8(v, v);
    return vget_lane_u8(v, 0);
#endif
}

/* Solid color over ARGB32-premultiplied dest through a per-pixel 8-bit
 * coverage mask (SrcOver).  Bit-exact with the scalar loop in
 * blendMaskARGB: BYTE_MUL truncates ((c*a)>>8 per channel), and the
 * cov==0 / cov==255 pixels take the same special cases (dest untouched /
 * direct value), which the general formula would miss by 1. */
void rlottie_blend_mask_solid_neon(uint32_t *dest, const uint8_t *cov,
                                   int length, uint32_t color, int opaque)
{
    const uint8x8_t cB = vdup_n_u8(color & 0xff);
    const uint8x8_t cG = vdup_n_u8((color >> 8) & 0xff);
    const uint8x8_t cR = vdup_n_u8((color >> 16) & 0xff);
    const uint8x8_t cA = vdup_n_u8(color >> 24);
    const uint8x8_t colorIA = vdup_n_u8(255 - (color >> 24));
    const uint8x8_t k255 = vdup_n_u8(255);
    int             x = 0;

    for (; x + 8 <= length; x += 8) {
        uint8x8_t a = vld1_u8(cov + x);

        if (neon_hmax_u8(a) == 0) continue;

        if (opaque && neon_hmin_u8(a) == 255) {
            uint32x4_t c4 = vdupq_n_u32(color);
            vst1q_u32(dest + x, c4);
            vst1q_u32(dest + x + 4, c4);
            continue;
        }

        uint8x8x4_t d = vld4_u8((const uint8_t *)(dest + x));
        uint8x8x4_t r;

        uint8x8_t sB = vshrn_n_u16(vmull_u8(cB, a), 8);
        uint8x8_t sG = vshrn_n_u16(vmull_u8(cG, a), 8);
        uint8x8_t sR = vshrn_n_u16(vmull_u8(cR, a), 8);
        uint8x8_t sA = vshrn_n_u16(vmull_u8(cA, a), 8);

        uint8x8_t ia = opaque ? vmvn_u8(a) : vmvn_u8(sA);

        r.val[0] = vadd_u8(sB, vshrn_n_u16(vmull_u8(d.val[0], ia), 8));
        r.val[1] = vadd_u8(sG, vshrn_n_u16(vmull_u8(d.val[1], ia), 8));
        r.val[2] = vadd_u8(sR, vshrn_n_u16(vmull_u8(d.val[2], ia), 8));
        r.val[3] = vadd_u8(sA, vshrn_n_u16(vmull_u8(d.val[3], ia), 8));

        {
            /* cov == 255: dest = color (opaque) or color + dst*colorIA */
            uint8x8_t is255 = vceq_u8(a, k255);
            uint8x8_t fB, fG, fR, fA;
            if (opaque) {
                fB = cB; fG = cG; fR = cR; fA = cA;
            } else {
                fB = vadd_u8(cB, vshrn_n_u16(vmull_u8(d.val[0], colorIA), 8));
                fG = vadd_u8(cG, vshrn_n_u16(vmull_u8(d.val[1], colorIA), 8));
                fR = vadd_u8(cR, vshrn_n_u16(vmull_u8(d.val[2], colorIA), 8));
                fA = vadd_u8(cA, vshrn_n_u16(vmull_u8(d.val[3], colorIA), 8));
            }
            r.val[0] = vbsl_u8(is255, fB, r.val[0]);
            r.val[1] = vbsl_u8(is255, fG, r.val[1]);
            r.val[2] = vbsl_u8(is255, fR, r.val[2]);
            r.val[3] = vbsl_u8(is255, fA, r.val[3]);
        }
        {
            /* cov == 0: dest untouched */
            uint8x8_t is0 = vceq_u8(a, vdup_n_u8(0));
            r.val[0] = vbsl_u8(is0, d.val[0], r.val[0]);
            r.val[1] = vbsl_u8(is0, d.val[1], r.val[1]);
            r.val[2] = vbsl_u8(is0, d.val[2], r.val[2]);
            r.val[3] = vbsl_u8(is0, d.val[3], r.val[3]);
        }

        vst4_u8((uint8_t *)(dest + x), r);
    }

    for (; x < length; x++) {
        const int a = cov[x];
        if (!a) continue;
        if (a == 255) {
            dest[x] = opaque ? color
                             : color + BYTE_MUL(dest[x], 255 - (color >> 24));
        } else if (opaque) {
            dest[x] = BYTE_MUL(color, a) + BYTE_MUL(dest[x], 255 - a);
        } else {
            const uint32_t s = BYTE_MUL(color, a);
            dest[x] = s + BYTE_MUL(dest[x], 255 - (s >> 24));
        }
    }
}
#endif
