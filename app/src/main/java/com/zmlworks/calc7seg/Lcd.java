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
    static final int ON = 0xF0161A12, GHOST = 0x14101808;

    /** Minimal drawing surface implemented by the Android view and the JVM preview. */
    interface Painter {
        void poly(float[] xy, int color);
        void circle(float cx, float cy, float r, int color);
        void roundRect(float l, float t, float r, float b, float rad, int color);
        /** Bold text centred on cx. */
        void text(String s, float cx, float baseline, float size, int color);
    }

    /** What the main LCD shows (the memory value lives on its own panel, see {@link #renderMemory}). */
    static final class State {
        String main = "0.", mem = "0.";
        boolean error;
        char op;

        static State of(Calculator c) {
            State s = new State();
            s.main = c.display();
            s.mem = c.memoryDisplay();
            s.error = c.isError();
            s.op = c.pendingOp();
            return s;
        }
    }

    /** Fixed annunciator positions, left to right, like printed segments on a real LCD. */
    static final String OPS = "+-×÷";

    /** Index of the annunciator to light for a pending operator, or -1. */
    static int opIndex(char op) { return op == 0 ? -1 : OPS.indexOf(op); }

    /** Digit block geometry. */
    static final class Digits {
        final float cellW, cellH, pitch, th, gap, signW, dpR, left, top, slant = 0.07f;

        Digits(float right, float top, float cellW, float aspect, float thick) {
            this.cellW = cellW;
            this.top = top;
            pitch = cellW * 1.32f;
            cellH = cellW * aspect;
            th = cellW * thick;
            gap = th * 0.11f;
            signW = cellW * 0.9f;
            dpR = th * 0.55f;
            left = right - (signW + CELLS * pitch) + (pitch - cellW) * 0.1f;
        }

        static float unitsWide() { return 0.9f + CELLS * 1.32f; }
    }

    /**
     * Main LCD, derived from the width only:
     * top row  = [+][−][×][÷] fixed operator annunciators ........ E
     * main row = [-] 10 large digits.
     */
    static final class Metrics {
        final float width, height, pad;
        final Digits d;
        final float annTop, annS, annPitch, annLeft, eX, eSize, eBase;

        Metrics(float width) {
            this.width = width;
            pad = width * 0.035f;
            float iw = width - 2 * pad;
            float cellW = iw / Digits.unitsWide();
            annS = cellW * 0.62f;                       // annunciator square
            annTop = pad * 0.85f;
            annPitch = annS * 1.55f;
            annLeft = pad * 1.3f;
            float top = annTop + annS + annS * 0.45f;
            d = new Digits(width - pad, top, cellW, 2.15f, 0.27f);
            height = top + d.cellH + pad;
            eSize = annS * 1.05f;
            eX = annLeft + 4 * annPitch + eSize * 0.9f;
            eBase = annTop + annS / 2 + eSize * 0.36f;
        }
    }

    /** Small memory panel: 10 digits only. */
    static final class MemMetrics {
        final float width, height, pad;
        final Digits d;

        MemMetrics(float width) {
            this.width = width;
            pad = width * 0.045f;
            float cellW = (width - 2 * pad) / Digits.unitsWide();
            d = new Digits(width - pad, pad, cellW, 1.95f, 0.25f);
            height = pad + d.cellH + pad;
        }
    }

    static void render(Painter p, Metrics m, State s) {
        int lit = opIndex(s.op);
        for (int i = 0; i < OPS.length(); i++) {
            float cx = m.annLeft + i * m.annPitch + m.annS / 2, cy = m.annTop + m.annS / 2;
            for (float[] poly : opSymbol(OPS.charAt(i), cx, cy, m.annS)) p.poly(poly, i == lit ? ON : GHOST);
        }
        p.text("E", m.eX, m.eBase, m.eSize, s.error ? ON : GHOST);
        digits(p, layout(s.main), m.d);
    }

    static void renderMemory(Painter p, MemMetrics m, String mem) {
        digits(p, layout(mem), m.d);
    }

    private static void digits(Painter p, Frame f, Digits d) {
        p.poly(segment(6, d.left - d.cellW * 0.1f, d.top, d.signW, d.cellH, d.th, d.gap, d.slant), f.neg ? ON : GHOST);
        for (int i = 0; i < CELLS; i++) {
            float l = d.left + d.signW + i * d.pitch;
            for (int sg = 0; sg < 7; sg++) {
                float[] pt = segment(sg, l, d.top, d.cellW, d.cellH, d.th, d.gap, d.slant);
                p.poly(pt, GHOST);
                if ((f.seg[i] & (1 << sg)) != 0) p.poly(pt, ON);
            }
            p.circle(l + d.cellW + (d.pitch - d.cellW) * 0.45f, d.top + d.cellH - d.dpR, d.dpR, f.dp[i] ? ON : GHOST);
        }
    }

    /** + - × ÷ as polygons centred in a box of side s. */
    static java.util.List<float[]> opSymbol(char op, float cx, float cy, float s) {
        java.util.List<float[]> out = new java.util.ArrayList<>();
        float len = s * 0.9f, t = s * 0.2f;
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
                out.add(dot(cx, cy - s * 0.34f, s * 0.11f));
                out.add(dot(cx, cy + s * 0.34f, s * 0.11f));
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
