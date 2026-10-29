package com.zmlworks.calc7seg;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * A photo-like plastic calculator key. Both states (up / pressed) are rendered once per colour+size into
 * shared bitmaps with a software canvas (blurs, gradients, a procedural grain tile — no bundled images),
 * so onDraw is a single bitmap blit.
 *
 * Layers, bottom to top: recessed well in the body (inner shadow at the top, lit lower lip) →
 * blurred contact shadow / ambient occlusion → side wall → cap face (top-lit gradient) →
 * rounded-edge light falloff → concave dish → soft specular sheen → top rim highlight →
 * fine plastic grain; the pad-printed label is drawn live on top.
 */
final class KeyView extends View {
    interface Feedback { void onPress(View v); }

    final Calculator.Key key;
    private final String label;
    private final int face, text;
    private final float labelScale;
    private Feedback feedback;
    private Bitmap up, down;
    private final RectF capUp = new RectF(), capDown = new RectF();
    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG),
            inkEdge = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

    private static Bitmap grain;
    /** Key bodies are shared by colour, size and state (labels are drawn live), so ~14 renders, not 56. */
    private static final java.util.HashMap<String, Bitmap> CACHE = new java.util.HashMap<>();

    KeyView(Context c, Calculator.Key key, String label, String description, int face, int text, float labelScale) {
        super(c);
        this.key = key;
        this.label = label;
        this.face = face;
        this.text = text;
        this.labelScale = labelScale;
        Typeface tf = Typeface.create("sans-serif-medium", Typeface.BOLD);
        for (Paint t : new Paint[]{ink, inkEdge}) { t.setTextAlign(Paint.Align.CENTER); t.setTypeface(tf); }
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
        if (w <= 0 || h <= 0) return;
        up = body(w, h, false);
        down = body(w, h, true);
        capRect(w, h, false, capUp);
        capRect(w, h, true, capDown);
        float size = Math.min(capUp.height() * 0.5f, capUp.width() * 0.42f) * labelScale;
        ink.setTextSize(size);
        inkEdge.setTextSize(size);
        float px = Math.max(1f, size * 0.025f);
        inkEdge.setMaskFilter(new BlurMaskFilter(px * 0.8f, BlurMaskFilter.Blur.NORMAL));
        inkEdge.setColor(0x55000000);
    }

    @Override protected void onDraw(Canvas c) {
        boolean pressed = isPressed();
        Bitmap b = pressed ? down : up;
        if (b == null) return;
        c.drawBitmap(b, 0, 0, blit);
        // pad-printed label: slightly translucent ink (grain shows through) with a faint dark edge below
        RectF cap = pressed ? capDown : capUp;
        float cx = cap.centerX(), cy = cap.centerY() - (ink.descent() + ink.ascent()) / 2;
        float px = Math.max(1f, ink.getTextSize() * 0.025f);
        c.drawText(label, cx, cy + px, inkEdge);
        int col = pressed ? darken(text, 0.9f) : text;
        ink.setColor((0xEE << 24) | (col & 0xFFFFFF));
        c.drawText(label, cx, cy, ink);
    }

    private Bitmap body(int w, int h, boolean pressed) {
        String k = face + ":" + w + "x" + h + (pressed ? "d" : "u");
        synchronized (CACHE) {
            Bitmap b = CACHE.get(k);
            if (b == null) CACHE.put(k, b = render(w, h, pressed));
            return b;
        }
    }

    /** Cap face rectangle for a key of size w×h (shared by the body renderer and the label). */
    static void capRect(int w, int h, boolean pressed, RectF out) {
        float m = Math.min(w, h);
        float side = m * 0.085f, travel = m * 0.032f;
        float depth = pressed ? m * 0.028f : m * 0.06f;
        float sink = pressed ? travel : 0;
        out.set(side, side * 0.78f + sink, w - side, h - side * 0.9f - depth + sink * 0.6f);
    }

    // ------------------------------------------------------------------ rendering

    private Bitmap render(int w, int h, boolean pressed) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        float m = Math.min(w, h);

        // geometry
        float wi = m * 0.035f;                                   // well inset in the body
        RectF well = new RectF(wi, wi, w - wi, h - wi);
        float wellR = m * 0.2f;
        float depth = pressed ? m * 0.028f : m * 0.06f;           // visible side wall below the cap
        RectF cap = new RectF();
        capRect(w, h, pressed, cap);
        float capR = m * 0.15f;
        RectF base = new RectF(cap.left - m * 0.012f, cap.top + m * 0.02f, cap.right + m * 0.012f, cap.bottom + depth);
        int f = pressed ? KeyView.darken(face, 0.9f) : face;

        // 1. recessed well: darker opening with an inner shadow under its top edge and a lit lower lip
        p.setColor(0x3C000000);
        c.drawRoundRect(well, wellR, wellR, p);
        c.save();
        Path wp = new Path();
        wp.addRoundRect(well, wellR, wellR, Path.Direction.CW);
        c.clipPath(wp);
        p.setColor(0xFF000000);                               // opaque paint: shader alpha only
        p.setShader(new LinearGradient(0, well.top, 0, well.top + well.height() * 0.45f, 0x50000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(well, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(m * 0.05f);
        p.setColor(0x58000000);
        p.setMaskFilter(new BlurMaskFilter(m * 0.03f, BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(well, wellR, wellR, p);
        p.setMaskFilter(null);
        c.restore();
        p.setStrokeWidth(Math.max(1f, m * 0.008f));
        p.setColor(0x1CFFFFFF);                                  // light catching the lower lip of the well
        c.drawArc(new RectF(well.left, well.bottom - 2 * wellR, well.left + 2 * wellR, well.bottom), 90, 90, false, p);
        c.drawArc(new RectF(well.right - 2 * wellR, well.bottom - 2 * wellR, well.right, well.bottom), 0, 90, false, p);
        c.drawLine(well.left + wellR, well.bottom, well.right - wellR, well.bottom, p);
        p.setStyle(Paint.Style.FILL);

        // 2. ambient occlusion / contact shadow into the well (tight + soft)
        p.setColor(pressed ? 0xA0000000 : 0x90000000);
        p.setMaskFilter(new BlurMaskFilter(pressed ? m * 0.018f : m * 0.035f, BlurMaskFilter.Blur.NORMAL));
        RectF ao = new RectF(base);
        ao.offset(0, pressed ? m * 0.006f : m * 0.02f);
        c.drawRoundRect(ao, capR, capR, p);
        if (!pressed) {
            p.setColor(0x40000000);
            p.setMaskFilter(new BlurMaskFilter(m * 0.07f, BlurMaskFilter.Blur.NORMAL));
            ao.offset(0, m * 0.025f);
            c.drawRoundRect(ao, capR * 1.2f, capR * 1.2f, p);
        }
        p.setMaskFilter(null);

        // 3. side wall: darker plastic, a little lighter toward its top edge
        p.setColor(0xFF000000);                               // opaque paint: shader alpha only
        p.setShader(new LinearGradient(0, base.top, 0, base.bottom,
                new int[]{darken(f, 0.7f), darken(f, 0.55f), darken(f, 0.42f)}, new float[]{0, 0.7f, 1}, Shader.TileMode.CLAMP));
        c.drawRoundRect(base, capR * 1.05f, capR * 1.05f, p);
        p.setShader(null);
        // wall edge highlight on the sides (rounded corners catch the light)
        p.setColor(0xFF000000);                               // opaque paint: shader alpha only
        p.setShader(new LinearGradient(base.left, 0, base.right, 0,
                new int[]{0x22FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x14FFFFFF}, new float[]{0, 0.08f, 0.92f, 1}, Shader.TileMode.CLAMP));
        c.drawRoundRect(base, capR * 1.05f, capR * 1.05f, p);
        p.setShader(null);

        // 4. cap face, lit from above
        Path capPath = new Path();
        capPath.addRoundRect(cap, capR, capR, Path.Direction.CW);
        p.setColor(0xFF000000);                               // opaque paint: shader alpha only
        p.setShader(new LinearGradient(0, cap.top, 0, cap.bottom,
                new int[]{mix(f, 0xFFFFFFFF, 0.17f), mix(f, 0xFFFFFFFF, 0.04f), darken(f, 0.9f)}, new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP));
        c.drawPath(capPath, p);
        p.setShader(null);

        c.save();
        c.clipPath(capPath);
        // 5. rounded edges: light falls off toward the border (blurred dark inner rim, stronger at the bottom)
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(m * 0.05f);
        p.setColor(0x2C000000);
        p.setMaskFilter(new BlurMaskFilter(m * 0.035f, BlurMaskFilter.Blur.NORMAL));
        RectF rim = new RectF(cap);
        rim.offset(0, m * 0.012f);
        c.drawRoundRect(rim, capR, capR, p);
        // top rim highlight: the rounded top edge facing the light
        p.setStrokeWidth(m * 0.03f);
        p.setColor(0xFF000000);                               // opaque paint: shader alpha only
        p.setShader(new LinearGradient(0, cap.top, 0, cap.top + cap.height() * 0.5f,
                pressed ? 0x30FFFFFF : 0x5AFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)); // fades down the sides
        p.setMaskFilter(new BlurMaskFilter(m * 0.012f, BlurMaskFilter.Blur.NORMAL));
        RectF hl = new RectF(cap.left + m * 0.012f, cap.top + m * 0.01f, cap.right - m * 0.012f, cap.bottom);
        c.drawRoundRect(hl, capR, capR, p);
        p.setMaskFilter(null);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);

        // 6. concave dish: upper part shaded by the front lip, lower part catches light
        float di = m * 0.075f;
        RectF dish = new RectF(cap.left + di, cap.top + di * 0.8f, cap.right - di, cap.bottom - di * 1.05f);
        p.setColor(0xFF000000);                               // opaque paint: shader alpha only
        p.setShader(new LinearGradient(0, dish.top, 0, dish.bottom,
                new int[]{0x1A000000, 0x04000000, 0x12FFFFFF}, new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
        p.setMaskFilter(new BlurMaskFilter(m * 0.025f, BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(dish, capR * 0.75f, capR * 0.75f, p);
        p.setMaskFilter(null);
        p.setShader(null);

        // 7. soft specular sheen (broad, elliptical, upper left) — satin plastic, not glossy
        float sx = cap.left + cap.width() * 0.34f, sy = cap.top + cap.height() * 0.2f, sr = cap.width() * 0.62f;
        RadialGradient sheen = new RadialGradient(sx, sy, sr, new int[]{pressed ? 0x16FFFFFF : 0x2AFFFFFF, 0x0CFFFFFF, 0x00FFFFFF},
                new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP);
        Matrix sm = new Matrix();
        sm.setScale(1f, 0.45f, sx, sy);
        sheen.setLocalMatrix(sm);
        p.setColor(0xFF000000);
        p.setShader(sheen);
        c.drawRect(cap, p);
        p.setShader(null);

        // 9. fine plastic grain over the whole cap (label is drawn on top, translucent)
        p.setColor(0xFF000000);                               // opaque paint: shader alpha only
        p.setShader(new BitmapShader(grain(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        c.drawRect(cap, p);
        p.setShader(null);
        c.restore();

        // grain on the side wall too, fainter
        p.setColor(0xFF000000);
        p.setAlpha(110);
        p.setShader(new BitmapShader(grain(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        c.save();
        c.clipRect(base.left, cap.bottom - capR, base.right, base.bottom);
        c.drawRoundRect(base, capR, capR, p);
        c.restore();
        return bmp;
    }

    /** 64×64 tile of fine luminance noise (light and dark specks at low alpha), generated once. */
    static synchronized Bitmap grain() {
        if (grain == null) {
            int n = 64;
            int[] px = new int[n * n];
            long seed = 0x5DEECE66DL;
            for (int i = 0; i < px.length; i++) {
                seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
                int v = (int) (seed >>> 40);                     // 0..255
                int a = Math.abs(v - 128) / 9;                   // 0..14 alpha
                px[i] = (a << 24) | (v >= 128 ? 0xFFFFFF : 0x000000);
            }
            grain = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
        }
        return grain;
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
