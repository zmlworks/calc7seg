package com.zmlworks.calc7seg;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * A physical-looking calculator key, drawn with gradients only (no bitmaps):
 * soft shadow → darker side wall (skirt) → convex bevel lit from above → slightly concave dish → printed label.
 * Pressed: the cap sinks onto the skirt, darkens and its shadow shrinks.
 */
final class KeyView extends View {
    interface Feedback { void onPress(View v); }

    final Calculator.Key key;
    private final String label;
    private final int face, text;
    private final float labelScale;
    private Feedback feedback;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), tp = new Paint(Paint.ANTI_ALIAS_FLAG),
            line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private Shader skirt, bevelUp, dishUp, bevelDown, dishDown;
    private float inset, depth, rad, bevel, sink;

    KeyView(Context c, Calculator.Key key, String label, String description, int face, int text, float labelScale) {
        super(c);
        this.key = key;
        this.label = label;
        this.face = face;
        this.text = text;
        this.labelScale = labelScale;
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tp.setSubpixelText(true);
        line.setStyle(Paint.Style.STROKE);
        setClickable(true);
        setFocusable(true);
        setContentDescription(description);
    }

    void setFeedback(Feedback f) { feedback = f; }

    /** Click sound / vibration on touch-down, like a real key, not on release. */
    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && isEnabled() && feedback != null) feedback.onPress(this);
        return super.onTouchEvent(e);
    }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float m = Math.min(w, h);
        inset = m * 0.06f;
        depth = m * 0.085f;
        rad = m * 0.17f;
        bevel = m * 0.05f;
        sink = depth * 0.7f;
        float top = inset, bottom = h - inset - depth;
        skirt = new LinearGradient(0, top, 0, h - inset, darken(face, 0.62f), darken(face, 0.38f), Shader.TileMode.CLAMP);
        bevelUp = new LinearGradient(0, top, 0, bottom, mix(face, 0xFFFFFFFF, 0.22f), darken(face, 0.72f), Shader.TileMode.CLAMP);
        dishUp = new LinearGradient(0, top + bevel, 0, bottom - bevel, darken(face, 0.93f), mix(face, 0xFFFFFFFF, 0.06f), Shader.TileMode.CLAMP);
        int fd = darken(face, 0.86f);
        bevelDown = new LinearGradient(0, top + sink, 0, bottom + sink, mix(fd, 0xFFFFFFFF, 0.14f), darken(fd, 0.75f), Shader.TileMode.CLAMP);
        dishDown = new LinearGradient(0, top + sink + bevel, 0, bottom + sink - bevel, darken(fd, 0.92f), mix(fd, 0xFFFFFFFF, 0.04f), Shader.TileMode.CLAMP);
        line.setStrokeWidth(Math.max(1f, m * 0.012f));
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        boolean down = isPressed();
        float off = down ? sink : 0;
        float l = inset, rr = w - inset, top = inset, bottom = h - inset - depth;

        // soft shadow under the key (smaller when pressed)
        p.setShader(null);
        int layers = down ? 1 : 3;
        for (int i = layers; i >= 1; i--) {
            float s = i * inset * 0.28f;
            p.setColor((0x16 + (layers - i) * 0x0C) << 24);
            r.set(l - s * 0.5f, top + depth * 0.6f + s * 0.4f, rr + s * 0.5f, h - inset + s);
            c.drawRoundRect(r, rad + s, rad + s, p);
        }
        // skirt / side wall
        p.setShader(skirt);
        r.set(l, top + depth * 0.5f, rr, h - inset);
        c.drawRoundRect(r, rad, rad, p);
        // cap: bevel (lit from above)
        p.setShader(down ? bevelDown : bevelUp);
        r.set(l, top + off, rr, bottom + off);
        c.drawRoundRect(r, rad, rad, p);
        // cap: slightly concave dish (reverse gradient reads as a dip)
        p.setShader(down ? dishDown : dishUp);
        r.set(l + bevel, top + off + bevel * 0.8f, rr - bevel, bottom + off - bevel * 1.2f);
        c.drawRoundRect(r, rad * 0.8f, rad * 0.8f, p);
        p.setShader(null);
        // specular edge along the top
        line.setColor(down ? 0x22FFFFFF : 0x44FFFFFF);
        c.drawLine(l + rad, top + off + line.getStrokeWidth(), rr - rad, top + off + line.getStrokeWidth(), line);

        // printed label: faint dark offset under it gives a slight ink-on-plastic feel
        float faceH = bottom - top;
        tp.setTextSize(Math.min(faceH * 0.46f, (rr - l) * 0.40f) * labelScale);
        float cy = top + off + faceH / 2 - (tp.descent() + tp.ascent()) / 2;
        float px = Math.max(1f, tp.getTextSize() * 0.03f);
        tp.setColor(0x66000000);
        c.drawText(label, w / 2, cy + px, tp);
        tp.setColor(down ? darken(text, 0.88f) : text);
        c.drawText(label, w / 2, cy, tp);
    }

    static int darken(int c, float f) {
        int a = c >>> 24, rr = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (a << 24) | (Math.round(rr * f) << 16) | (Math.round(g * f) << 8) | Math.round(b * f);
    }

    static int mix(int c, int d, float t) {
        int a = c >>> 24;
        int rr = Math.round(((c >> 16) & 0xFF) * (1 - t) + ((d >> 16) & 0xFF) * t);
        int g = Math.round(((c >> 8) & 0xFF) * (1 - t) + ((d >> 8) & 0xFF) * t);
        int b = Math.round((c & 0xFF) * (1 - t) + (d & 0xFF) * t);
        return (a << 24) | (rr << 16) | (g << 8) | b;
    }
}
