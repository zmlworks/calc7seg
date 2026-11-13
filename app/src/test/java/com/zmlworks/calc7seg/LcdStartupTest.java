package com.zmlworks.calc7seg;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LcdStartupTest {
    /** If set, startup screenshots are written here (optional; tests do not depend on it). */
    static final String OUT = System.getenv().getOrDefault("CALC7SEG_SHOTS", "");

    /** Confirms View.isLaidOut() is still false inside the first onLayout() — the pitfall LcdPanel.room() must not use. */
    @Test public void isLaidOutFalseDuringFirstOnLayout() {
        Activity a = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> log = new ArrayList<>();
        View v = new View(a) {
            @Override protected void onLayout(boolean c, int l, int t, int r, int b) {
                super.onLayout(c, l, t, r, b);
                log.add("onLayout#" + log.size() + " isLaidOut=" + isLaidOut());
            }
        };
        FrameLayout root = new FrameLayout(a);
        root.addView(v, new FrameLayout.LayoutParams(200, 100));
        a.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 400);
        assertFalse("isLaidOut should be false during the first onLayout",
                log.get(0).contains("isLaidOut=true"));
        assertTrue(v.isLaidOut());
        v.requestLayout();
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 400);
        assertTrue("isLaidOut should be true during a subsequent onLayout",
                log.get(1).contains("isLaidOut=true"));
    }

    @Test public void raisedRestoreMatchesToggleAfterFirstLayout() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().putBoolean(Settings.RAISED_LCD, true).commit();

        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class);
        MainActivity a = c.setup().get();
        ShadowLooper.idleMainLooper();
        // The posted apply() from the first onLayout may request another layout; flush it.
        layout(a);
        ShadowLooper.idleMainLooper();
        layout(a);

        LcdPanel panel = findPanel(a);
        LcdView lcd = findMainLcd(panel);
        assertTrue(panel.raised());
        float launchStretch = lcd.stretch();
        int launchMargin = margin(panel);
        int launchHeight = panel.getHeight();
        System.out.println(snapshot("launch", panel, lcd));
        saveShot(a, "01-launch-raised.png");

        assertTrue("saved raised look must already be taller than flat after first layout",
                launchStretch > 1f);
        assertTrue("saved raised look must already have a negative top margin (grows upward)",
                launchMargin < 0);

        panel.performClick(); // to flat
        ShadowLooper.idleMainLooper();
        layout(a);
        ShadowLooper.idleMainLooper();
        layout(a);
        System.out.println(snapshot("toggledFlat", panel, lcd));
        saveShot(a, "02-toggled-flat.png");
        assertFalse(panel.raised());
        assertEquals(1f, lcd.stretch(), 0f);
        assertEquals(0, margin(panel));

        panel.performClick(); // back to raised, now fully laid out
        ShadowLooper.idleMainLooper();
        layout(a);
        ShadowLooper.idleMainLooper();
        layout(a);
        System.out.println(snapshot("toggledRaised", panel, lcd));
        saveShot(a, "03-toggled-raised.png");

        assertTrue(panel.raised());
        assertEquals("cold-start stretch must match a later toggle", launchStretch, lcd.stretch(), 0.0001f);
        assertEquals("cold-start topMargin must match a later toggle", launchMargin, margin(panel));
        assertEquals("cold-start height must match a later toggle", launchHeight, panel.getHeight());

        c.pause().stop().destroy();
    }

    @Test public void flatLaunchStaysUnstretched() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().putBoolean(Settings.RAISED_LCD, false).commit();
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class);
        MainActivity a = c.setup().get();
        ShadowLooper.idleMainLooper();
        layout(a);
        LcdPanel panel = findPanel(a);
        LcdView lcd = findMainLcd(panel);
        System.out.println(snapshot("flatLaunch", panel, lcd));
        saveShot(a, "00-flat-launch.png");
        assertFalse(panel.raised());
        assertEquals(1f, lcd.stretch(), 0f);
        assertEquals(0, margin(panel));
        c.pause().stop().destroy();
    }

    static void layout(Activity a) {
        View root = a.findViewById(android.R.id.content);
        if (root.getWidth() <= 0 || root.getHeight() <= 0) return;
        root.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.EXACTLY));
        root.layout(root.getLeft(), root.getTop(), root.getRight(), root.getBottom());
    }

    static String snapshot(String tag, LcdPanel panel, LcdView lcd) {
        return tag + " raised=" + panel.raised()
                + " stretch=" + lcd.stretch()
                + " topMargin=" + margin(panel)
                + " panel=" + panel.getWidth() + "x" + panel.getHeight()
                + " top=" + panel.getTop()
                + " lcd=" + lcd.getWidth() + "x" + lcd.getHeight()
                + " laidOut=" + panel.isLaidOut();
    }

    static int margin(View v) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams)
            return ((ViewGroup.MarginLayoutParams) lp).topMargin;
        return 99999;
    }

    static LcdPanel findPanel(Activity a) {
        return find(a.getWindow().getDecorView(), LcdPanel.class);
    }

    static LcdView findMainLcd(LcdPanel panel) {
        return (LcdView) panel.getChildAt(0);
    }

    @SuppressWarnings("unchecked")
    static <T extends View> T find(View v, Class<T> cls) {
        if (cls.isInstance(v)) return (T) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                T f = find(g.getChildAt(i), cls);
                if (f != null) return f;
            }
        }
        return null;
    }

    static void saveShot(Activity a, String name) throws Exception {
        if (OUT.isEmpty()) return;
        View root = a.getWindow().getDecorView();
        int w = root.getWidth(), h = root.getHeight();
        if (w <= 0 || h <= 0) return;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        root.draw(canvas);
        File dir = new File(OUT);
        dir.mkdirs();
        File out = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        System.out.println("wrote " + out + " " + w + "x" + h);
    }
}
