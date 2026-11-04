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
 *       right) and slightly foreshortened; the empty recess (exactly the flat display's footprint;
 *       the upward extension above it is plain body) shows as a sunken wedge on the left, with a faint shadow.</li>
 * </ul>
 * Everything is vector drawing with a canvas matrix; no bitmaps. The top-right corner overhangs
 * the panel's own bounds by the shear amount, so the parent must not clip children.
 */
public final class LcdPanel extends FrameLayout {
    /** Top-edge shift as a fraction of the panel width (0.9.5: ≈46 px on a 1080 px screen; was 0.0225). */
    static final float SHEAR = 0.045f;
    /** Apparent height of the raised panel (cos of the lift angle). */
    static final float LIFT_SCALE = 0.96f;
    /**
     * Raised panel grows upward only (0.9.4): its bottom edge and everything below stay put; the
     * top edge rises into the gap above, stopping this far short of the view above (the memory LCD strip).
     * 0.9.5: negative = the raised panel's frame overlaps the memory LCD strip (drawn in front of it,
     * covering the bottom of its bezel and the printed MEMORY label).
     */
    static final float TOP_GAP_DP = -13f;
    /**
     * 0.9.6: when raised, the whole module (recess, shadow, frame, glass and contents together) is
     * drawn shifted left by this × the top-edge shift, so the slanted panel sits centred on the screen
     * (bottom-left and top-right corners equally far from the edges). Replaces 0.9.5's content-only shift.
     */
    static final float PANEL_SHIFT = 0.5f;
    private static final int SHADOW_STEPS = 8;

    private final float dp;
    private float radius;
    private final Paint recessFill = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint(Paint.ANTI_ALIAS_FLAG),
            line = new Paint(Paint.ANTI_ALIAS_FLAG), shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline = new Path(), mapped = new Path(), recess = new Path();
    private final RectF r = new RectF();
    private final Matrix matrix = new Matrix();
    private Shader topShade, leftShade, hingeShade;
    private boolean raised;
    private LcdView lcd;

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

    /** 0 = flat, 1 = raised. */
    private float t;
    /** Current upward extension in px (= t × available gap). */
    private int ext;

    /** Switch look instantly (0.9.4: no animation). */
    void setRaised(boolean up) {
        raised = up;
        apply(up ? 1f : 0f);
        invalidate();
    }

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
        if (t > 0 && Math.round(t * room()) != ext) post(() -> apply(t));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        outline.rewind();
        r.set(0, 0, w, h);
        outline.addRoundRect(r, radius, radius, Path.Direction.CW);
        recessOutline();
        // shallow recess floor: only a little darker than the body, a touch deeper toward the top
        recessFill.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF9D9B97, 0xFFABA9A5, 0xFFB3B1AC},
                new float[]{0, 0.4f, 1}, Shader.TileMode.CLAMP));
        topShade = new LinearGradient(0, 0, 0, 10 * dp, 0x38000000, 0x00000000, Shader.TileMode.CLAMP);
        leftShade = new LinearGradient(0, 0, 6 * dp, 0, 0x30000000, 0x00000000, Shader.TileMode.CLAMP);
        hingeShade = new LinearGradient(0, h - 4 * dp, 0, h, 0x00000000, 0x30000000, Shader.TileMode.CLAMP);
        panelMatrix(w, h);
        outline.transform(matrix, mapped);
    }

    /**
     * 0.9.4: the recess is exactly where the flat display sits (the hole it was lifted out of): the
     * bottom h−ext px of this view. The band above it (the upward extension) is plain body.
     */
    private void recessOutline() {
        recess.rewind();
        r.set(0, ext, getWidth(), getHeight());
        recess.addRoundRect(r, radius, radius, Path.Direction.CW);
    }

    /** Raised panel transform: x' = x + s·(h−y)/h (bottom fixed, full width), y foreshortened to the bottom. */
    private void panelMatrix(float w, float h) {
        float s = SHEAR * w, sy = LIFT_SCALE;
        matrix.setValues(new float[]{1, -s / h, s, 0, sy, h * (1 - sy), 0, 0, 1});
    }

    @Override public void draw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (!raised) { super.draw(c); return; }
        c.save();
        c.translate(-PANEL_SHIFT * SHEAR * w, 0);

        // 1. the recess left behind in the body = the flat display's footprint
        float rt = ext;
        recessOutline();
        c.save();
        c.clipPath(recess);
        c.translate(0, rt);
        float rh = h - rt;
        recessFill.setShader(new LinearGradient(0, 0, 0, rh, new int[]{0xFF9D9B97, 0xFFABA9A5, 0xFFB3B1AC},
                new float[]{0, 0.4f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, rh, recessFill);
        shade.setShader(topShade);
        c.drawRect(0, 0, w, 10 * dp, shade);
        shade.setShader(leftShade);
        c.drawRect(0, 0, 6 * dp, rh, shade);
        c.translate(0, -rt);
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
        // recess rim moulded into the body (the flat display's outline)
        line.setStrokeWidth(dp);
        line.setColor(0xFF84827F);
        r.set(dp / 2, rt + dp / 2, w - dp / 2, h - dp / 2);
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
        c.restore();
    }
}
