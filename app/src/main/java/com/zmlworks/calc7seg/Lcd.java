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
        /** sep[i]: thousands tick in the gap to the right of cell i (top of the gap). */
        final boolean[] sep = new boolean[CELLS];
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
        markGroups(f);
        return f;
    }

    /**
     * Thousands ticks: counting integer digits leftwards from the decimal point, a tick goes after
     * every 3rd digit if a further integer digit follows to its left ("1'234'567.89").
     */
    static void markGroups(Frame f) {
        int point = -1;                       // cell holding the last integer digit
        for (int i = 0; i < CELLS; i++) if (f.dp[i]) { point = i; break; }
        if (point < 0) point = CELLS - 1;     // no point shown: integer ends at the right edge
        int first = 0;                        // leftmost lit cell (leading blanks are 0 masks)
        while (first < point && f.seg[first] == 0) first++;
        for (int r = 3; point - r >= first; r += 3) f.sep[point - r] = true;
    }

    /** Separator positions as text, for tests and accessibility: "1'234'567.89". */
    static String grouped(String text) {
        Frame f = layout(text);
        StringBuilder b = new StringBuilder();
        if (f.neg) b.append('-');
        boolean started = false;
        for (int i = 0; i < CELLS; i++) {
            if (f.seg[i] == 0 && !started && !f.dp[i]) continue;
            started = true;
            int d = -1;
            for (int k = 0; k < 10; k++) if (DIGIT[k] == f.seg[i]) d = k;
            b.append(d < 0 ? ' ' : (char) ('0' + d));
            if (f.dp[i]) b.append('.');
            if (f.sep[i]) b.append('\'');
        }
        return b.toString();
    }

    /**
     * Chiselled segment polygon (x/y interleaved) for segment s of a digit cell at (l,t) sized w×h,
     * stroke thickness th, joint gap gap, italic slant (dx per unit of height above bottom).
     * Outer segments are mitred at 45° so the digit has square outer corners; the vertical
     * segments meet the middle bar in sharp points (classic hexagonal/trapezoidal LCD shapes).
     */
    static float[] segment(int s, float l, float t, float w, float h, float th, float gap, float slant) {
        float r = l + w, b = t + h, ym = t + h / 2, hm = th / 2, g = gap;
        float[] p;
        switch (s) {
            case 0: p = new float[]{l + g, t, r - g, t, r - th - g, t + th, l + th + g, t + th}; break;
            case 3: p = new float[]{l + th + g, b - th, r - th - g, b - th, r - g, b, l + g, b}; break;
            case 6: p = new float[]{l + hm + g, ym, l + th + g, ym - hm, r - th - g, ym - hm,
                    r - hm - g, ym, r - th - g, ym + hm, l + th + g, ym + hm}; break;
            case 5: p = new float[]{l, t + g, l + th, t + th + g, l + th, ym - hm - g, l + hm, ym - g, l, ym - hm - g}; break;
            case 4: p = new float[]{l, ym + hm + g, l + hm, ym + g, l + th, ym + hm + g, l + th, b - th - g, l, b - g}; break;
            case 1: p = new float[]{r, t + g, r, ym - hm - g, r - hm, ym - g, r - th, ym - hm - g, r - th, t + th + g}; break;
            default: p = new float[]{r, ym + hm + g, r, b - g, r - th, b - th - g, r - th, ym + hm + g, r - hm, ym + g}; break; // c
        }
        for (int i = 0; i < p.length; i += 2) p[i] += (b - p[i + 1]) * slant;
        return p;
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
            gap = th * 0.13f;
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
            annPitch = annS * 1.45f;
            annLeft = pad * 1.3f;
            float top = annTop + annS + annS * 0.45f;
            d = new Digits(width - pad, top, cellW, 2.15f, 0.27f);
            height = top + d.cellH + pad;
            eSize = annS * 1.05f;
            eX = annLeft + 4 * annPitch + eSize * 1.9f;   // clear gap after the ÷ block
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
            // inverted annunciator: dark block with the symbol cut out (LCD background shows through)
            float hb = m.annS * 0.58f;
            p.poly(new float[]{cx - hb, cy - hb, cx + hb, cy - hb, cx + hb, cy + hb, cx - hb, cy + hb}, i == lit ? ON : GHOST);
            int bg = bgAt(cy / m.height);
            for (float[] poly : opSymbol(OPS.charAt(i), cx, cy, m.annS * 0.8f)) p.poly(poly, bg);
        }
        p.text("E", m.eX, m.eBase, m.eSize, s.error ? ON : GHOST);
        digits(p, layout(s.main), m.d);
    }

    /** LCD background colour at relative height f (matches the vertical BG_TOP→BG_BOTTOM gradient). */
    static int bgAt(float f) {
        f = Math.max(0, Math.min(1, f));
        int c = 0xFF000000;
        for (int sh = 0; sh <= 16; sh += 8) {
            int a = (BG_TOP >> sh) & 0xFF, b = (BG_BOTTOM >> sh) & 0xFF;
            c |= Math.round(a + (b - a) * f) << sh;
        }
        return c;
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
            float gx = l + d.cellW + (d.pitch - d.cellW) * 0.56f, q = d.dpR * 0.78f;  // square dot, clear of the c/d corner
            float yb = d.top + d.cellH;
            p.poly(new float[]{gx - q, yb - 2 * q, gx + q, yb - 2 * q, gx + q, yb, gx - q, yb}, f.dp[i] ? ON : GHOST);
            if (i < CELLS - 1) p.poly(tick(gx + d.cellH * d.slant, d.top, d), f.sep[i] ? ON : GHOST);
        }
    }

    /** Thousands tick: a short wedge at the top of the inter-digit gap, slanted like the digits. */
    static float[] tick(float cx, float top, Digits d) {
        float len = d.cellH * 0.2f, wTop = d.th * 0.3f, wBot = d.th * 0.13f, k = d.slant;
        float y0 = top, y1 = top + len;
        return new float[]{cx - wTop, y0, cx + wTop, y0, cx + wBot - len * k, y1, cx - wBot - len * k, y1};
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
