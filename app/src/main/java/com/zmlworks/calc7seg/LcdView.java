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

/** Hand-drawn 7-segment LCD: either the main display (digits, + − × ÷ annunciators, E) or the memory panel. */
public final class LcdView extends View implements Lcd.Painter {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), bg = new Paint(Paint.ANTI_ALIAS_FLAG),
            txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final boolean memoryPanel;
    private Lcd.Metrics m;
    private Lcd.MemMetrics mm;
    private Lcd.State state = new Lcd.State();
    private Canvas canvas;
    /** Vertical stretch of the content (1 = normal); the raised panel sets it &gt; 1. */
    private float stretch = 1f;
    /** Horizontal content offset in px (0.9.5: raised panel shifts the readout left); 0 = none. */
    private float shiftX = 0f;

    public LcdView(Context c, boolean memoryPanel) {
        super(c);
        this.memoryPanel = memoryPanel;
        txt.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        txt.setTextAlign(Paint.Align.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    public void show(Lcd.State s) {
        state = s;
        setContentDescription(memoryPanel ? (s.mem.isEmpty() ? "memory empty" : "memory " + s.mem)
                : (s.error ? "E " : "") + (s.op != 0 ? s.op + " " : "") + s.main);
        invalidate();
    }

    /** Stretch the display vertically (taller view, digits drawn taller); 1 = normal. */
    float stretch() { return stretch; }

    /** Unstretched content height for the current width. */
    float baseHeight() {
        int w = getWidth();
        return memoryPanel ? new Lcd.MemMetrics(w).height : new Lcd.Metrics(w).height;
    }

    void setShiftX(float x) {
        if (x == shiftX) return;
        shiftX = x;
        invalidate();
    }

    void setStretch(float k) {
        if (k == stretch) return;
        stretch = k;
        requestLayout();
        invalidate();
    }

    @Override protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws);
        int h = Math.round((memoryPanel ? new Lcd.MemMetrics(w).height : new Lcd.Metrics(w).height) * stretch);
        if (MeasureSpec.getMode(hs) == MeasureSpec.AT_MOST) h = Math.min(h, MeasureSpec.getSize(hs));
        else if (MeasureSpec.getMode(hs) == MeasureSpec.EXACTLY) h = MeasureSpec.getSize(hs);
        setMeasuredDimension(w, h);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (memoryPanel) mm = new Lcd.MemMetrics(w);
        else m = new Lcd.Metrics(w);
        bg.setShader(new LinearGradient(0, 0, 0, h, Lcd.BG_TOP, Lcd.BG_BOTTOM, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas c) {
        if (m == null && mm == null) return;
        float r = getHeight() * 0.05f;
        rect.set(0, 0, getWidth(), getHeight());
        c.drawRoundRect(rect, r, r, bg);
        canvas = c;
        c.save();
        c.translate(shiftX, 0);
        c.scale(1f, stretch);
        if (memoryPanel) Lcd.renderMemory(this, mm, state.mem);
        else Lcd.render(this, m, state);
        c.restore();
        canvas = null;
    }

    @Override public void poly(float[] xy, int color) {
        path.rewind();
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) path.lineTo(xy[i], xy[i + 1]);
        path.close();
        fill.setColor(color);
        canvas.drawPath(path, fill);
    }

    @Override public void circle(float cx, float cy, float r, int color) {
        fill.setColor(color);
        canvas.drawCircle(cx, cy, r, fill);
    }

    @Override public void roundRect(float l, float t, float r, float b, float rad, int color) {
        fill.setColor(color);
        rect.set(l, t, r, b);
        canvas.drawRoundRect(rect, rad, rad, fill);
    }

    @Override public void text(String s, float cx, float baseline, float size, int color) {
        txt.setTextSize(size);
        txt.setColor(color);
        canvas.drawText(s, cx, baseline, txt);
    }
}
