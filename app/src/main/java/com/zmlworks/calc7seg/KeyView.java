package com.zmlworks.calc7seg;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.View;

/** A physical-looking calculator key: rounded face over a darker skirt; sinks when pressed. */
final class KeyView extends View {
    final Calculator.Key key;
    private final String label;
    private final int face, skirt, text;
    private final float labelScale;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), tp = new Paint(Paint.ANTI_ALIAS_FLAG),
            hl = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    KeyView(Context c, Calculator.Key key, String label, String description, int face, int text, float labelScale) {
        super(c);
        this.key = key;
        this.label = label;
        this.face = face;
        this.skirt = darken(face, 0.55f);
        this.text = text;
        this.labelScale = labelScale;
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        hl.setColor(0x14FFFFFF);
        setClickable(true);
        setFocusable(true);
        setContentDescription(description);
        setHapticFeedbackEnabled(true);
    }

    @Override public boolean performClick() {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        return super.performClick();
    }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float inset = Math.min(w, h) * 0.07f, depth = Math.min(w, h) * 0.07f, rad = Math.min(w, h) * 0.16f;
        boolean down = isPressed();
        float sink = down ? depth * 0.75f : 0;
        // skirt / shadow
        p.setColor(0x55000000);
        r.set(inset, inset + depth * 0.6f, w - inset, h - inset + depth * 0.4f);
        c.drawRoundRect(r, rad, rad, p);
        p.setColor(skirt);
        r.set(inset, inset + depth, w - inset, h - inset);
        c.drawRoundRect(r, rad, rad, p);
        // face
        p.setColor(down ? darken(face, 0.88f) : face);
        r.set(inset, inset + sink, w - inset, h - inset - depth + sink);
        c.drawRoundRect(r, rad, rad, p);
        // top highlight
        r.set(inset + rad * 0.5f, inset + sink + depth * 0.3f, w - inset - rad * 0.5f, inset + sink + depth * 0.3f + h * 0.12f);
        c.drawRoundRect(r, rad * 0.5f, rad * 0.5f, hl);
        // label
        float faceH = h - 2 * inset - depth;
        tp.setColor(text);
        tp.setTextSize(Math.min(faceH * 0.46f, (w - 2 * inset) * 0.40f) * labelScale);
        float cy = inset + sink + faceH / 2 - (tp.descent() + tp.ascent()) / 2;
        c.drawText(label, w / 2, cy, tp);
    }

    static int darken(int c, float f) {
        int a = c >>> 24, rr = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (a << 24) | (Math.round(rr * f) << 16) | (Math.round(g * f) << 8) | Math.round(b * f);
    }
}
