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

    // ------------------------------------------------------------------ rendering (shared)

    static final int BG_TOP = 0xFFB4BCA0, BG_BOTTOM = 0xFF9AA386;
    static final int ON = 0xF0161A12, GHOST = 0x14101808, INV_FG = 0xFFF2F5EA;

    /** Minimal drawing surface implemented by the Android view and the JVM preview. */
    interface Painter {
        void poly(float[] xy, int color);
        void circle(float cx, float cy, float r, int color);
        void roundRect(float l, float t, float r, float b, float rad, int color);
        /** Bold text centred on cx. */
        void text(String s, float cx, float baseline, float size, int color);
    }

    /** What the LCD shows. */
    static final class State {
        String main = "0.", mem = "0.";
        boolean memory, error;
        char op;

        static State of(Calculator c) {
            State s = new State();
            s.main = c.display();
            s.mem = c.memoryDisplay();
            s.memory = c.hasMemory();
            s.error = c.isError();
            s.op = c.pendingOp();
            return s;
        }
    }

    /**
     * Layout, all in pixels, derived from the width only:
     * top row  = [inverted operator box] M E ........ [small 10-digit memory display]
     * main row = [-] 10 large digits.
     */
    static final class Metrics {
        final float width, height, pad, slant = 0.07f;
        // main digits
        final float cellW, cellH, pitch, th, gap, signW, dpR, left, top;
        // memory digits
        final float sW, sH, sPitch, sTh, sGap, sSignW, sDpR, sLeft, sTop;
        // annunciators
        final float boxL, boxT, boxS, indSize, mX, eX, indBase;

        Metrics(float width) {
            this.width = width;
            pad = width * 0.035f;
            float iw = width - 2 * pad;
            cellW = iw / (0.9f + CELLS * 1.32f);
            pitch = cellW * 1.32f;
            cellH = cellW * 2.15f;         // tall, narrow digits
            th = cellW * 0.27f;            // bold segments
            gap = th * 0.11f;
            signW = cellW * 0.9f;
            dpR = th * 0.55f;
            left = width - pad - (signW + CELLS * pitch) + (pitch - cellW) * 0.1f;

            sH = cellH * 0.36f;
            sW = sH / 1.9f;
            sPitch = sW * 1.32f;
            sTh = sW * 0.26f;
            sGap = sTh * 0.12f;
            sSignW = sW * 0.9f;
            sDpR = sTh * 0.6f;
            sLeft = width - pad - (sSignW + CELLS * sPitch) + (sPitch - sW) * 0.1f;
            sTop = pad * 0.9f;

            top = sTop + sH + sH * 0.45f;
            height = top + cellH + pad;

            boxS = sH * 1.08f;
            boxL = pad * 1.2f;
            boxT = sTop + (sH - boxS) / 2;
            indSize = sH * 0.7f;
            mX = boxL + boxS + indSize * 0.95f;
            eX = mX + indSize * 1.25f;
            indBase = sTop + sH / 2 + indSize * 0.36f;
        }
    }

    static void render(Painter p, Metrics m, State s) {
        // inverted operator annunciator: black block, white symbol; faint ghost block when off
        p.roundRect(m.boxL, m.boxT, m.boxL + m.boxS, m.boxT + m.boxS, m.boxS * 0.14f, s.op != 0 ? ON : GHOST);
        if (s.op != 0) {
            for (float[] poly : opSymbol(s.op, m.boxL + m.boxS / 2, m.boxT + m.boxS / 2, m.boxS)) p.poly(poly, INV_FG);
        }
        p.text("M", m.mX, m.indBase, m.indSize, s.memory ? ON : GHOST);
        p.text("E", m.eX, m.indBase, m.indSize, s.error ? ON : GHOST);
        // memory display: ghost-only (blank) while memory is empty, like an unlit LCD
        digits(p, layout(s.memory ? s.mem : ""), m.sLeft, m.sTop, m.sW, m.sH, m.sPitch, m.sTh, m.sGap,
                m.sSignW, m.sDpR, m.slant);
        digits(p, layout(s.main), m.left, m.top, m.cellW, m.cellH, m.pitch, m.th, m.gap, m.signW, m.dpR, m.slant);
    }

    private static void digits(Painter p, Frame f, float left, float top, float w, float h, float pitch,
                               float th, float gap, float signW, float dpR, float slant) {
        p.poly(segment(6, left - w * 0.1f, top, signW, h, th, gap, slant), f.neg ? ON : GHOST);
        for (int i = 0; i < CELLS; i++) {
            float l = left + signW + i * pitch;
            for (int sg = 0; sg < 7; sg++) {
                float[] pt = segment(sg, l, top, w, h, th, gap, slant);
                p.poly(pt, GHOST);
                if ((f.seg[i] & (1 << sg)) != 0) p.poly(pt, ON);
            }
            p.circle(l + w + (pitch - w) * 0.45f, top + h - dpR, dpR, f.dp[i] ? ON : GHOST);
        }
    }

    /** + - × ÷ as polygons centred in a box of side s. */
    static java.util.List<float[]> opSymbol(char op, float cx, float cy, float s) {
        java.util.List<float[]> out = new java.util.ArrayList<>();
        float len = s * 0.62f, t = s * 0.15f;
        switch (op) {
            case '+':
                out.add(rect(cx, cy, len, t, 0));
                out.add(rect(cx, cy, t, len, 0));
                break;
            case '×':
                out.add(rect(cx, cy, len, t, Math.PI / 4));
                out.add(rect(cx, cy, len, t, -Math.PI / 4));
                break;
            case '÷':
                out.add(rect(cx, cy, len, t, 0));
                out.add(dot(cx, cy - s * 0.24f, t * 0.62f));
                out.add(dot(cx, cy + s * 0.24f, t * 0.62f));
                break;
            default: // '-'
                out.add(rect(cx, cy, len, t, 0));
        }
        return out;
    }

    private static float[] rect(float cx, float cy, float w, float h, double rot) {
        float[] p = {-w / 2, -h / 2, w / 2, -h / 2, w / 2, h / 2, -w / 2, h / 2};
        float c = (float) Math.cos(rot), sn = (float) Math.sin(rot);
        for (int i = 0; i < p.length; i += 2) {
            float x = p[i], y = p[i + 1];
            p[i] = cx + x * c - y * sn;
            p[i + 1] = cy + x * sn + y * c;
        }
        return p;
    }

    private static float[] dot(float cx, float cy, float r) {
        float[] p = new float[24];
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            p[2 * i] = cx + (float) Math.cos(a) * r;
            p[2 * i + 1] = cy + (float) Math.sin(a) * r;
        }
        return p;
    }
}
