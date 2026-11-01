package com.zmlworks.calc7seg;

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
import android.widget.FrameLayout;

/**
 * Main LCD module: dark bezel + {@link LcdView}. Two looks, toggled by tapping (instant):
 * <ul>
 *   <li>flat (0.8.0): the panel sits flush in the body;</li>
 *   <li>raised ("L"): the panel is propped up out of its recess. It is drawn sheared into a
 *       parallelogram (full-width bottom edge hinged at the bottom of the recess, top edge shifted
 *       right) and slightly foreshortened; the empty recess shows as a shallow sunken strip on the
 *       left and along the top, with a faint shadow.</li>
 * </ul>
 * Everything is vector drawing with a canvas matrix; no bitmaps. The top-right corner overhangs
 * the panel's own bounds by the shear amount, so the parent must not clip children.
 */
public final class LcdPanel extends FrameLayout {
    /** Top-edge shift as a fraction of the panel width (≈23 px on a 1080 px screen, ≈4°). */
    static final float SHEAR = 0.0225f;
    /** Apparent height of the raised panel (cos of the lift angle). */
    static final float LIFT_SCALE = 0.98f;
    /**
     * Raised panel grows upward only (0.9.4): its bottom edge and everything below stay put; the
     * top edge rises into the gap above, stopping this far short of the view above (the memory LCD strip).
     */
    static final float TOP_GAP_DP = 3f;
    private static final long ANIM_MS = 180;
    private static final int SHADOW_STEPS = 8;

    private final float dp;
    private float radius;
    private final Paint recessFill = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint(Paint.ANTI_ALIAS_FLAG),
            line = new Paint(Paint.ANTI_ALIAS_FLAG), shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline = new Path(), mapped = new Path();
    private final RectF r = new RectF();
    private final Matrix matrix = new Matrix();
    private Shader topShade, leftShade, hingeShade;
    private boolean raised;
    private LcdView lcd;
    private android.animation.ValueAnimator anim;

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
        bz.setStroke(Math.round(dp), 0xFF63625F);   // moulded edge where the dark bezel meets the grey body
        setBackground(bz);
        int bp = Math.round(padDp * dp);
        setPadding(bp, bp, bp, bp);
        if (lcdView instanceof LcdView) lcd = (LcdView) lcdView;
        addView(lcdView, new FrameLayout.LayoutParams(-1, -2));
        return this;
    }

    boolean raised() { return raised; }

    /** 0 = flat, 1 = fully raised (animated). */
    private float t;
    /** Current upward extension in px (= t × available gap). */
    private int ext;

    /** Switch look; animate only when asked (user tap), not on restore. */
    void setRaised(boolean up, boolean animate) {
        raised = up;
        float to = up ? 1f : 0f;
        if (anim != null) anim.cancel();
        if (animate && isLaidOut()) {
            anim = android.animation.ValueAnimator.ofFloat(t, to).setDuration(ANIM_MS);
            anim.addUpdateListener(a -> apply((float) a.getAnimatedValue()));
            anim.start();
        } else apply(to);
        invalidate();
    }

    void setRaised(boolean up) { setRaised(up, false); }

    /** Room above the panel's flat position, minus a small gap to the view above; 0 if unknown. */
    private int room() {
        if (!(getParent() instanceof android.view.ViewGroup) || !isLaidOut()) return 0;
        android.view.ViewGroup p = (android.view.ViewGroup) getParent();
        int i = p.indexOfChild(this);
        int above = i > 0 ? p.getChildAt(i - 1).getBottom() : p.getPaddingTop();
        int flatTop = getTop() + ext;
        return Math.max(0, flatTop - above - Math.round(TOP_GAP_DP * dp));
    }

    /** Extend the top edge upward by t × room: taller by e, top margin −e, so siblings don't move. */
    private void apply(float k) {
        t = k;
        int e = Math.round(k * room());
        if (lcd != null && lcd.getWidth() > 0) {
            float base = lcd.baseHeight();
            lcd.setStretch(base > 0 ? (base + e) / base : 1f);
        }
        if (e != ext && getLayoutParams() instanceof MarginLayoutParams) {
            ext = e;
            MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
            lp.topMargin = -e;
            setLayoutParams(lp);
        }
    }

    @Override protected void onLayout(boolean changed, int l, int tp, int r, int b) {
        super.onLayout(changed, l, tp, r, b);
        // restored raised state (or size change): the gap is only known after the first layout
        if (t > 0 && (anim == null || !anim.isRunning()) && Math.round(t * room()) != ext) post(() -> apply(t));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        outline.rewind();
        r.set(0, 0, w, h);
        outline.addRoundRect(r, radius, radius, Path.Direction.CW);
        // shallow recess floor: only a little darker than the body, a touch deeper toward the top
        recessFill.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF9D9B97, 0xFFABA9A5, 0xFFB3B1AC},
                new float[]{0, 0.4f, 1}, Shader.TileMode.CLAMP));
        topShade = new LinearGradient(0, 0, 0, 10 * dp, 0x38000000, 0x00000000, Shader.TileMode.CLAMP);
        leftShade = new LinearGradient(0, 0, 6 * dp, 0, 0x30000000, 0x00000000, Shader.TileMode.CLAMP);
        hingeShade = new LinearGradient(0, h - 4 * dp, 0, h, 0x00000000, 0x30000000, Shader.TileMode.CLAMP);
        panelMatrix(w, h);
        outline.transform(matrix, mapped);
    }

    /** Raised panel transform: x' = x + s·(h−y)/h (bottom fixed, full width), y foreshortened to the bottom. */
    private void panelMatrix(float w, float h) {
        float s = SHEAR * w, sy = LIFT_SCALE;
        matrix.setValues(new float[]{1, -s / h, s, 0, sy, h * (1 - sy), 0, 0, 1});
    }

    @Override public void draw(Canvas c) {
        if (!raised) { super.draw(c); return; }
        float w = getWidth(), h = getHeight();

        // 1. the recess left behind in the body
        c.save();
        c.clipPath(outline);
        c.drawPath(outline, recessFill);
        shade.setShader(topShade);
        c.drawRect(0, 0, w, 10 * dp, shade);
        shade.setShader(leftShade);
        c.drawRect(0, 0, 6 * dp, h, shade);
        // 2. faint shadow of the raised panel in the recess (soft edge from nested strokes)
        c.save();
        c.translate(2 * dp, -dp);
        for (int i = SHADOW_STEPS; i >= 1; i--) {
            shadow.setStrokeWidth(i * dp);
            shadow.setAlpha(8);
            c.drawPath(mapped, shadow);
        }
        c.restore();
        shade.setShader(hingeShade);
        c.drawRect(0, h - 4 * dp, w, h, shade);
        c.restore();
        // recess rim moulded into the body
        line.setStrokeWidth(dp);
        line.setColor(0xFF84827F);
        r.set(dp / 2, dp / 2, w - dp / 2, h - dp / 2);
        c.drawRoundRect(r, radius, radius, line);

        // 3. thin dark edge on the panel's left slanted side (its thickness, seen at an angle)
        c.save();
        c.translate(-1.6f * dp, 0.8f * dp);
        shade.setShader(null);
        shade.setColor(0xB0505154);
        c.drawPath(mapped, shade);
        c.restore();

        // 4. the panel itself (bezel background + LCD), sheared
        c.save();
        c.concat(matrix);
        super.draw(c);
        c.restore();
    }
}
