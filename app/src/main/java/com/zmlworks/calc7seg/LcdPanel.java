package com.zmlworks.calc7seg;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * Main LCD module: dark bezel + {@link LcdView}. Two looks, toggled by tapping:
 * <ul>
 *   <li>flat (0.8.0): the panel sits flush in the body;</li>
 *   <li>raised ("L"): the panel is propped up out of its recess. It is drawn sheared into a
 *       parallelogram (top edge shifted right, ≈6°) and foreshortened, its bottom edge hinged at the
 *       bottom of the recess; the empty recess (sunken well with inner shadow) shows around it and the
 *       panel casts a soft shadow into it.</li>
 * </ul>
 * Everything is vector drawing with a canvas matrix; no bitmaps.
 */
public final class LcdPanel extends FrameLayout {
    /** Top-edge shift as a fraction of the panel width (≈32 px on a 1080 px screen). */
    static final float SHEAR = 0.0315f;
    /** Apparent height of the raised panel (cos of the lift angle). */
    static final float LIFT_SCALE = 0.9f;

    private final float dp;
    private float radius;
    private final Paint recessFill = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint(Paint.ANTI_ALIAS_FLAG),
            line = new Paint(Paint.ANTI_ALIAS_FLAG), shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline = new Path(), mapped = new Path();
    private final RectF r = new RectF();
    private final Matrix matrix = new Matrix();
    private static final int SHADOW_STEPS = 12;
    private float t;                       // 0 = flat, 1 = raised
    private ValueAnimator anim;

    public LcdPanel(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        shadow.setColor(0xFF000000);
        shadow.setStyle(Paint.Style.STROKE);
        shadow.setStrokeJoin(Paint.Join.ROUND);
        line.setStyle(Paint.Style.STROKE);
    }

    /** Dark bezel (padDp thick, radiusDp corners) around the LCD view. */
    LcdPanel holding(View lcdView, int padDp, int radiusDp) {
        radius = radiusDp * dp;
        GradientDrawable bz = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0F1013, 0xFF1C1D21});
        bz.setCornerRadius(radius);
        bz.setStroke(Math.round(dp), 0xFF6A6966);   // moulded edge where the dark bezel meets the grey body
        setBackground(bz);
        int bp = Math.round(padDp * dp);
        setPadding(bp, bp, bp, bp);
        addView(lcdView, new FrameLayout.LayoutParams(-1, -2));
        return this;
    }

    boolean raised() { return t > 0.5f; }

    /** Switch look; animates (short, decelerating) unless {@code animate} is false. */
    void setRaised(boolean up, boolean animate) {
        if (anim != null) anim.cancel();
        float target = up ? 1 : 0;
        if (!animate) { setT(target); return; }
        anim = ValueAnimator.ofFloat(t, target);
        anim.setDuration(Math.round(240 * Math.abs(target - t)) + 1);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> setT((float) a.getAnimatedValue()));
        anim.start();
    }

    private void setT(float v) {
        t = v;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        outline.rewind();
        r.set(0, 0, w, h);
        outline.addRoundRect(r, radius, radius, Path.Direction.CW);
        // recess floor: darker toward the top where the wall shades it
        recessFill.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF8C8A85, 0xFFA3A19C, 0xFFB0AEA9},
                new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP));
    }

    /** Panel transform for lift amount t: shear (top → right) + foreshortening, hinged on the bottom edge. */
    private void panelMatrix(float w, float h) {
        float s = t * SHEAR * w, sy = 1 - t * (1 - LIFT_SCALE), sx = (w - s) / w;
        matrix.setValues(new float[]{sx, -s / h, s, 0, sy, h * (1 - sy), 0, 0, 1});
    }

    @Override public void draw(Canvas c) {
        if (t <= 0) { super.draw(c); return; }
        float w = getWidth(), h = getHeight();
        int a = Math.round(255 * Math.min(1, t * 1.6f));

        // 1. the recess left behind in the body
        c.save();
        c.clipPath(outline);
        recessFill.setAlpha(a);
        c.drawPath(outline, recessFill);
        // inner shadow under the top and left walls
        shade.setShader(new LinearGradient(0, 0, 0, 16 * dp, 0x80000000, 0x00000000, Shader.TileMode.CLAMP));
        shade.setAlpha(a);
        c.drawRect(0, 0, w, 16 * dp, shade);
        shade.setShader(new LinearGradient(0, 0, 8 * dp, 0, 0x55000000, 0x00000000, Shader.TileMode.CLAMP));
        shade.setAlpha(a);
        c.drawRect(0, 0, 8 * dp, h, shade);
        // lit lower and right walls
        shade.setShader(new LinearGradient(0, h - 4 * dp, 0, h, 0x00FFFFFF, 0x70FFFFFF, Shader.TileMode.CLAMP));
        shade.setAlpha(a);
        c.drawRect(0, h - 4 * dp, w, h, shade);
        shade.setShader(new LinearGradient(w - 3 * dp, 0, w, 0, 0x00FFFFFF, 0x50FFFFFF, Shader.TileMode.CLAMP));
        shade.setAlpha(a);
        c.drawRect(w - 3 * dp, 0, w, h, shade);

        // 2. the raised panel's shadow, cast into the recess (light from the upper left)
        panelMatrix(w, h);
        outline.transform(matrix, mapped);
        // soft edge built from nested strokes (hardware friendly, no blur filter / software layer)
        c.save();
        c.translate(4 * dp * t, -2 * dp * t);
        for (int i = SHADOW_STEPS; i >= 1; i--) {
            shadow.setStrokeWidth(i * 1.2f * dp);
            shadow.setAlpha(Math.round(15 * t));
            c.drawPath(mapped, shadow);
        }
        c.restore();
        // contact shadow along the hinge at the bottom of the recess
        shade.setShader(new LinearGradient(0, h - 6 * dp, 0, h, 0x00000000, 0x66000000, Shader.TileMode.CLAMP));
        shade.setAlpha(Math.round(255 * t));
        c.drawRect(0, h - 6 * dp, w, h, shade);
        c.restore();

        // recess rim moulded into the body
        line.setStrokeWidth(dp);
        line.setColor(0xFF7E7C78);
        line.setAlpha(a);
        r.set(dp / 2, dp / 2, w - dp / 2, h - dp / 2);
        c.drawRoundRect(r, radius, radius, line);

        // 3. thin dark edge on the panel's left slanted side (its thickness, seen at an angle)
        c.save();
        c.translate(-2.2f * dp * t, 1.2f * dp * t);
        shade.setShader(null);
        shade.setColor(0xFF45464A);
        shade.setAlpha(Math.round(230 * t));
        c.drawPath(mapped, shade);
        c.restore();

        // 4. the panel itself (bezel background + LCD), sheared
        c.save();
        c.concat(matrix);
        super.draw(c);
        c.restore();
    }
}
