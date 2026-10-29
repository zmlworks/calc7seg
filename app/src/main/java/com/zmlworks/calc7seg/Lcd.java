package com.zmlworks.calc7seg;

/**
 * Pure-Java 7-segment geometry and text→segment mapping, shared by the Android view and the
 * JVM preview renderer. Segment bit order: a=0 (top), b=1, c=2, d=3 (bottom), e=4, f=5, g=6 (middle).
 */
final class Lcd {
    private Lcd() {}

    static final int CELLS = Calculator.DIGITS;

    static final int[] DIGIT = {
            0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110,
            0b1101101, 0b1111101, 0b0100111, 0b1111111, 0b1101111};
    static final int MINUS = 0b1000000;

    /** Right-aligned cells for display text like "-123.45": masks, decimal points, minus sign. */
    static final class Frame {
        final int[] seg = new int[CELLS];
        final boolean[] dp = new boolean[CELLS];
        boolean neg;
    }

    static Frame layout(String text) {
        Frame f = new Frame();
        int cell = CELLS - 1;
        for (int i = text.length() - 1; i >= 0 && cell >= -1; i--) {
            char c = text.charAt(i);
            if (c == '.') {
                if (cell >= 0) f.dp[cell] = true; // dot belongs to the digit on its left = next cell
            } else if (c == '-') {
                f.neg = true;
            } else if (c >= '0' && c <= '9') {
                if (cell < 0) break;
                f.seg[cell] = DIGIT[c - '0'];
                cell--;
            }
        }
        return f;
    }

    /**
     * Hexagonal segment polygon (6 points, x/y interleaved) for segment s of a digit cell at (l,t)
     * sized w×h, stroke thickness th, end gap gap, italic slant (dx per unit of height above bottom).
     */
    static float[] segment(int s, float l, float t, float w, float h, float th, float gap, float slant) {
        float x0 = l + th / 2, x1 = l + w - th / 2;
        float y0 = t + th / 2, ym = t + h / 2, y1 = t + h - th / 2;
        float[] p;
        switch (s) {
            case 0: p = horiz(x0, x1, y0, th, gap); break;
            case 6: p = horiz(x0, x1, ym, th, gap); break;
            case 3: p = horiz(x0, x1, y1, th, gap); break;
            case 5: p = vert(x0, y0, ym, th, gap); break;
            case 1: p = vert(x1, y0, ym, th, gap); break;
            case 4: p = vert(x0, ym, y1, th, gap); break;
            default: p = vert(x1, ym, y1, th, gap); break; // c
        }
        float bottom = t + h;
        for (int i = 0; i < p.length; i += 2) p[i] += (bottom - p[i + 1]) * slant;
        return p;
    }

    private static float[] horiz(float x0, float x1, float y, float th, float gap) {
        float a = x0 + gap, b = x1 - gap, r = th / 2;
        return new float[]{a, y, a + r, y - r, b - r, y - r, b, y, b - r, y + r, a + r, y + r};
    }

    private static float[] vert(float x, float y0, float y1, float th, float gap) {
        float a = y0 + gap, b = y1 - gap, r = th / 2;
        return new float[]{x, a, x + r, a + r, x + r, b - r, x, b, x - r, b - r, x - r, a + r};
    }

    /** Geometry of the whole LCD for a given size. All fields in pixels. */
    static final class Metrics {
        float cellW, cellH, pitch, th, gap, slant, left, top, signW, dpR, indTop, indSize, indLeft;

        Metrics(float width, float height) {
            float pad = Math.min(width, height) * 0.08f;
            float iw = width - 2 * pad, ih = height - 2 * pad;
            indSize = ih * 0.16f;
            float digitTop = pad + indSize * 1.2f, digitAreaH = height - pad - digitTop;
            // units: sign cell 0.9w + 10 × (w + 0.32w spacing)
            float wByWidth = iw / (0.9f + CELLS * 1.32f);
            float wByHeight = digitAreaH / 1.8f;
            cellW = Math.min(wByWidth, wByHeight);
            cellH = cellW * 1.8f;
            pitch = cellW * 1.32f;
            th = cellW * 0.21f;
            gap = th * 0.14f;
            slant = 0.07f;
            signW = cellW * 0.9f;
            float total = signW + CELLS * pitch;
            left = width - pad - total + (pitch - cellW) * 0.1f;
            top = digitTop + (digitAreaH - cellH) * 0.55f;
            dpR = th * 0.6f;
            indTop = pad * 0.7f;
            indLeft = left;
        }

        float cellLeft(int i) { return left + signW + i * pitch; }
    }
}
