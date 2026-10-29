package com.zmlworks.calc7seg;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/** Hand-drawn 10-digit 7-segment LCD with ghost segments, decimal points, minus, M and E. */
public final class LcdView extends View {
    static final int BG_TOP = 0xFFB4BCA0, BG_BOTTOM = 0xFF9AA386, ON = 0xF0161A12, GHOST = 0x14101808;

    private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG), ghost = new Paint(Paint.ANTI_ALIAS_FLAG),
            bg = new Paint(Paint.ANTI_ALIAS_FLAG), ind = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path path = new Path();
    private final RectF rect = new RectF();
    private Lcd.Metrics m;
    private Lcd.Frame frame = Lcd.layout("0.");
    private boolean memory, error;

    public LcdView(Context c) {
        super(c);
        on.setColor(ON);
        ghost.setColor(GHOST);
        ind.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        ind.setTextAlign(Paint.Align.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    public void show(String text, boolean memory, boolean error) {
        frame = Lcd.layout(text);
        this.memory = memory;
        this.error = error;
        setContentDescription((error ? "E " : "") + (memory ? "M " : "") + text);
        invalidate();
    }

    @Override protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws);
        int h = Math.round(w * 0.31f);
        if (MeasureSpec.getMode(hs) == MeasureSpec.AT_MOST) h = Math.min(h, MeasureSpec.getSize(hs));
        else if (MeasureSpec.getMode(hs) == MeasureSpec.EXACTLY) h = MeasureSpec.getSize(hs);
        setMeasuredDimension(w, h);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        m = new Lcd.Metrics(w, h);
        bg.setShader(new LinearGradient(0, 0, 0, h, BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP));
        ind.setTextSize(m.indSize);
    }

    @Override protected void onDraw(Canvas c) {
        if (m == null) return;
        float w = getWidth(), h = getHeight(), r = h * 0.05f;
        rect.set(0, 0, w, h);
        c.drawRoundRect(rect, r, r, bg);

        // indicators (ghosted when off, like printed LCD annunciators)
        float iy = m.indTop + m.indSize * 0.85f;
        drawInd(c, "M", m.indLeft + m.indSize * 0.6f, iy, memory);
        drawInd(c, "E", m.indLeft + m.indSize * 2.0f, iy, error);

        // minus sign cell: only the middle bar
        float sl = m.left, st = m.top;
        drawSeg(c, 6, sl - m.cellW * 0.1f, st, m.signW, m.cellH, frame.neg ? on : ghost);

        for (int i = 0; i < Lcd.CELLS; i++) {
            float l = m.cellLeft(i);
            int mask = frame.seg[i];
            for (int s = 0; s < 7; s++) {
                drawSeg(c, s, l, st, m.cellW, m.cellH, ghost);
                if ((mask & (1 << s)) != 0) drawSeg(c, s, l, st, m.cellW, m.cellH, on);
            }
            float dx = l + m.cellW + (m.pitch - m.cellW) * 0.45f, dy = st + m.cellH - m.dpR;
            c.drawCircle(dx, dy, m.dpR, frame.dp[i] ? on : ghost);
        }
    }

    private void drawInd(Canvas c, String s, float x, float y, boolean lit) {
        ind.setColor(lit ? ON : GHOST);
        c.drawText(s, x, y, ind);
    }

    private void drawSeg(Canvas c, int s, float l, float t, float w, float h, Paint p) {
        float[] pt = Lcd.segment(s, l, t, w, h, m.th, m.gap, m.slant);
        path.rewind();
        path.moveTo(pt[0], pt[1]);
        for (int i = 2; i < pt.length; i += 2) path.lineTo(pt[i], pt[i + 1]);
        path.close();
        c.drawPath(path, p);
    }
}
