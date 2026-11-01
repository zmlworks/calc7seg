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
        /**
         * Floating minus (main LCD): the cell whose middle bar (segment g) shows the minus sign — the
         * cell immediately left of the most significant digit. -1 = the spare sign cell left of cell 0,
         * used only when a negative number fills all 10 digits.
         */
        int signCell = -2;
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
        if (f.neg) {
            int first = 0;
            while (first < CELLS - 1 && f.seg[first] == 0) first++;
            f.signCell = first - 1;
        }
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
        String main = "0.", mem = "";
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
        /** Main LCD: minus floats next to the number; the sign slot is a full 7-segment cell. */
        final boolean floatSign;

        Digits(float right, float top, float cellW, float aspect, float thick) {
            this(right, top, cellW, aspect, thick, false);
        }

        Digits(float right, float top, float cellW, float aspect, float thick, boolean floatSign) {
            this.floatSign = floatSign;
            this.cellW = cellW;
            this.top = top;
            pitch = cellW * 1.32f;
            cellH = cellW * aspect;
            th = cellW * thick;
            gap = th * 0.11f;
            signW = cellW * 0.9f;                      // sign slot: just room for a middle bar
            dpR = th * 0.55f;
            left = right - (signW + CELLS * pitch) + (pitch - cellW) * 0.1f;
        }

        /** Decimal point centre: sits a little below the digit baseline, like a real LCD. */
        float dotY() { return top + cellH + dpR * 0.15f; }   // 0.9.4: half a radius lower (was −0.35)

        /** Thousands tick top: raised a little above the digit tops. */
        float tickTop() { return top - cellH * 0.07f; }

        static float unitsWide() { return 0.9f + CELLS * 1.32f; }

        /** Left x of digit cell i (-1 = the narrow sign slot). */
        float cellLeft(int i) {
            return i < 0 ? left - cellW * 0.1f : left + signW + i * pitch;
        }
    }

    /**
     * Main LCD, derived from the width only:
     * <pre>
     *  [E]          [+][−][×][÷]          (annunciators: centre, slightly right)
     *  [E]            [+][−][×][÷]        (cut-out blocks; operators centre, slightly right)
     *   -  8 8 8 8 8 8 8 8 8 8               (- = sign slot: middle bar only, for 10-digit negatives)
     * </pre>
     * The minus otherwise floats in the cell left of the most significant digit. E is a cut-out block
     * like the operators, at the far left of the top row, above the sign slot.
     */
    static final class Metrics {
        final float width, height, pad;
        final Digits d;
        final float annTop, annS, annPitch, annLeft;
        /** E block centre (same size and row as the operator blocks). */
        final float eCx, eCy;

        Metrics(float width) {
            this.width = width;
            pad = width * 0.035f;
            float iw = width - 2 * pad;
            float cellW = iw / Digits.unitsWide();     // same digit size as 0.8.0
            annS = cellW * 0.62f;                       // annunciator square
            annTop = pad * 0.45f;                       // 0.9.4: operator row higher (was 0.85)
            annPitch = annS * 1.45f;
            // 0.9.4: digits 6% taller (aspect 2.15 → 2.28), growing upward so the baseline and the
            // panel height stay as before
            float top = pad * 0.85f + annS + annS * 0.45f - cellW * (DIGIT_ASPECT - 2.15f);
            d = new Digits(width - pad, top, cellW, DIGIT_ASPECT, 0.27f, true);
            height = top + d.cellH + pad;
            float blockW = 3 * annPitch + annS;
            annLeft = width * 0.585f - blockW / 2;      // operator row: centre, slightly right
            eCx = pad * 1.3f + annS / 2;                // where "+" sat in 0.8.0
            // 1.5x as tall as an operator block: top edge aligned with them, extra height hangs down
            // beside the digit tops, over the (empty) upper half of the narrow sign slot
            eCy = annTop + annS / 2 - blockHalf() + eHalfH();
        }

        /** Half height of the E block (1.5x an operator block). */
        float eHalfH() { return blockHalf() * E_TALL; }

        static final float E_TALL = 1.5f;
        static final float DIGIT_ASPECT = 2.28f;

        /** Half side of a cut-out block. */
        float blockHalf() { return annS * 0.58f; }
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
        float hb = m.blockHalf(), hh = m.eHalfH(), ex = m.eCx, ey = m.eCy;
        p.poly(new float[]{ex - hb, ey - hh, ex + hb, ey - hh, ex + hb, ey + hh, ex - hb, ey + hh}, s.error ? ON : GHOST);
        int ebg = bgAt(ey / m.height);
        for (float[] poly : letterE(ex, ey, m.annS * 0.8f, Metrics.E_TALL)) p.poly(poly, ebg);
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
        // narrow sign slot: a middle bar only. Memory: the minus; main LCD: only for 10-digit negatives
        boolean slotLit = d.floatSign ? f.signCell == -1 : f.neg;
        p.poly(segment(6, d.cellLeft(-1), d.top, d.signW, d.cellH, d.th, d.gap, d.slant), slotLit ? ON : GHOST);
        for (int i = 0; i < CELLS; i++) {
            float l = d.cellLeft(i);
            int mask = f.seg[i] | (d.floatSign && f.signCell == i ? MINUS : 0);
            for (int sg = 0; sg < 7; sg++) {
                float[] pt = segment(sg, l, d.top, d.cellW, d.cellH, d.th, d.gap, d.slant);
                p.poly(pt, GHOST);
                if ((mask & (1 << sg)) != 0) p.poly(pt, ON);
            }
            float gx = l + d.cellW + (d.pitch - d.cellW) * 0.45f;   // gap centre at the baseline
            p.circle(gx - (d.dotY() - d.top - d.cellH) * d.slant, d.dotY(), d.dpR, f.dp[i] ? ON : GHOST);
            if (i < CELLS - 1) p.poly(tick(gx + (d.top + d.cellH - d.tickTop()) * d.slant, d.tickTop(), d), f.sep[i] ? ON : GHOST);
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

    /** Cut-out "E" centred in a box of side s, stretched {@code tall}x vertically (stroke kept). */
    static java.util.List<float[]> letterE(float cx, float cy, float s, float tall) {
        java.util.List<float[]> out = new java.util.ArrayList<>();
        float h = s * 0.9f * tall, t = s * 0.2f, w = s * 0.62f, l = cx - w / 2;
        out.add(rect(l + t / 2, cy, t, h, 0));                       // spine
        out.add(rect(cx, cy - h / 2 + t / 2, w, t, 0));              // top
        out.add(rect(cx - w * 0.06f, cy, w * 0.88f, t, 0));          // middle, a bit shorter
        out.add(rect(cx, cy + h / 2 - t / 2, w, t, 0));              // bottom
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
