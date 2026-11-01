package com.zmlworks.calc7seg;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

public final class MainActivity extends Activity {
    /** Super Famicom-style warm-grey ABS (0.9.2: ~7% darker); vertical shading top → bottom. */
    static final int BODY = 0xFFC0BEB9, BODY_TOP = 0xFFC7C5C1, BODY_BOTTOM = 0xFFB5B3AF;
    /** Pad-printed body graphics: charcoal brand, accent model line, mid-grey captions. */
    static final int PRINT = 0xFF2C2E32, PRINT_ACCENT = 0xFF962F12, PRINT_SUB = 0xFF4A4C51;
    /** Short-key height relative to a full key row (memory / function rows and BS √ +/-). */
    static final float SHORT_ROW = 0.82f;
    private static final int NUM = 0xFF26282D, NUM_T = 0xFFF2F2EE;
    private static final int OPR = 0xFF4B5159, OPR_T = 0xFFFFFFFF;
    private static final int MEM = 0xFF383C42, MEM_T = 0xFFE4E6E9;
    private static final int CLR = 0xFFA8401C, CLR_T = 0xFFFFFFFF;
    private static final int EQL = 0xFF666D76, EQL_T = 0xFFFFFFFF;

    private final Calculator calc = new Calculator();
    private LcdView lcd, memLcd;
    private Settings settings;
    private KeyClick click;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        String st = saved != null ? saved.getString("calc") : null;
        if (st == null) st = prefs().getString("calc", null);
        calc.restore(st);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(bodyBackground());
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC); // volume keys = media volume
        int pad = Ui.dp(this, 12);
        root.setPadding(pad, pad, pad, pad);

        settings = Settings.of(getSharedPreferences("settings", MODE_PRIVATE));
        click = new KeyClick(this);

        // top strip: printed brand / "MEMORY" on the body, memory value on its own small LCD panel
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.BOTTOM);
        LinearLayout printed = new LinearLayout(this);
        printed.setOrientation(LinearLayout.VERTICAL);
        printed.addView(printedText("ZML", 13, 0.25f, Gravity.START, PRINT), new LinearLayout.LayoutParams(-1, -2));
        printed.addView(printedText("10 DIGITS", 9, 0.2f, Gravity.START, PRINT_ACCENT), new LinearLayout.LayoutParams(-1, -2));
        View spacer = new View(this);
        printed.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));
        printed.addView(printedText("MEMORY", 9, 0.22f, Gravity.END, PRINT_SUB), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, -1, 0.4f);
        plp.rightMargin = Ui.dp(this, 8);
        top.addView(printed, plp);
        memLcd = new LcdView(this, true);
        top.addView(bezel(memLcd, 6, 7), new LinearLayout.LayoutParams(0, -2, 0.6f));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.bottomMargin = Ui.dp(this, 10);
        root.addView(top, tlp);

        lcd = new LcdView(this, false);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.bottomMargin = Ui.dp(this, 14);
        LcdPanel panel = new LcdPanel(this).holding(lcd, 9, 10);
        panel.setRaised(settings.raisedLcd());
        // tap the main display: flat ⇄ raised (propped up out of its recess, display 1.25x taller, short animation); remembered
        panel.setOnClickListener(v -> {
            boolean up = !panel.raised();
            panel.setRaised(up, true);
            settings.setRaisedLcd(up);
            if (settings.vibration()) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        });
        root.addView(panel, blp);
        // the raised panel's sheared top-right corner may overhang into the root padding
        root.setClipChildren(false);
        root.setClipToPadding(false);

        LinearLayout pad4 = new LinearLayout(this);
        pad4.setOrientation(LinearLayout.VERTICAL);
        View.OnClickListener onClick = v -> onKey(((KeyView) v).key);
        KeyView.Feedback feedback = this::keyFeedback;
        for (int row = 0; row < KeyLayout.rows(); row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 4; col++) {
                int idx = row * KeyLayout.COLUMNS + col;
                Calculator.Key key = KeyLayout.KEYS[idx];
                KeyView kv = makeKey(KeyLayout.LABELS[idx], key);
                kv.setOnClickListener(onClick);
                kv.setFeedback(feedback);
                if (key == Calculator.Key.EQ) {
                    // long-press "=" opens settings; a consumed long press suppresses the click (no compute)
                    kv.setOnLongClickListener(v -> {
                        if (settings.vibration()) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        showSettings();
                        return true;
                    });
                }
                if (KeyLayout.isShortInFullRow(idx)) {
                    // BS √ +/-: same height as the short keys above, top aligned with ÷, body below
                    LinearLayout col1 = new LinearLayout(this);
                    col1.setOrientation(LinearLayout.VERTICAL);
                    col1.setWeightSum(1f);
                    col1.addView(kv, new LinearLayout.LayoutParams(-1, 0, SHORT_ROW));
                    line.addView(col1, new LinearLayout.LayoutParams(0, -1, 1));
                } else {
                    line.addView(kv, new LinearLayout.LayoutParams(0, -1, 1));
                }
            }
            // memory / func rows a bit shorter, like real calculators
            pad4.addView(line, new LinearLayout.LayoutParams(-1, 0, row < 2 ? SHORT_ROW : 1f));
        }
        root.addView(pad4, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        Ui.edgeToEdge(this, root);
        Ui.lightBars(this);
        refresh();
    }

    /** Body: light-grey resin with soft vertical shading and the same fine grain as the keys. */
    private android.graphics.drawable.Drawable bodyBackground() {
        GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BODY_TOP, BODY, BODY_BOTTOM});
        android.graphics.drawable.BitmapDrawable grain =
                new android.graphics.drawable.BitmapDrawable(getResources(), KeyView.grain());
        grain.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT);
        return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{shade, grain});
    }

    private TextView printedText(String s, float sp, float spacing, int gravity, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setLetterSpacing(spacing);
        t.setGravity(gravity);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private FrameLayout bezel(View lcdView, int padDp, int radiusDp) {
        FrameLayout bezel = new FrameLayout(this);
        GradientDrawable bz = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0F1013, 0xFF1C1D21});
        bz.setCornerRadius(Ui.dp(this, radiusDp));
        bz.setStroke(Ui.dp(this, 1), 0xFF63625F);   // moulded edge where the dark bezel meets the grey body
        bezel.setBackground(bz);
        int bp = Ui.dp(this, padDp);
        bezel.setPadding(bp, bp, bp, bp);
        bezel.addView(lcdView, new FrameLayout.LayoutParams(-1, -2));
        return bezel;
    }

    private void keyFeedback(View v) {
        if (settings.sound()) click.play();
        if (settings.vibration()) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void showSettings() {
        int pad = Ui.dp(this, 20);
        // dialog stays dark: build its views with the dialog theme, not the light activity theme
        android.content.Context dc = new android.view.ContextThemeWrapper(this, android.R.style.Theme_Material_Dialog_Alert);
        LinearLayout box = new LinearLayout(dc);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        Switch vib = settingSwitch(dc, "バイブレーション", settings.vibration());
        vib.setOnCheckedChangeListener((b, on) -> settings.setVibration(on));
        Switch snd = settingSwitch(dc, "キー音", settings.sound());
        snd.setOnCheckedChangeListener((b, on) -> {
            settings.setSound(on);
            if (on) click.play();
        });
        box.addView(vib);
        box.addView(snd);
        TextView hint = new TextView(dc);
        hint.setText("「=」長押しでこの設定を開きます");
        hint.setTextSize(12);
        hint.setTextColor(0xFF8C9097);
        hint.setPadding(0, Ui.dp(this, 12), 0, 0);
        box.addView(hint);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("設定")
                .setView(box)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private Switch settingSwitch(android.content.Context dc, String label, boolean on) {
        Switch s = new Switch(dc);
        s.setText(label);
        s.setTextSize(16);
        s.setChecked(on);
        s.setMinHeight(Ui.dp(this, 52));
        return s;
    }

    private KeyView makeKey(String label, Calculator.Key k) {
        int face, text;
        float scale = 1f;
        String desc = label;
        switch (k) {
            case MC: case MR: case M_MINUS: case M_PLUS: face = MEM; text = MEM_T; scale = 0.72f; break;
            case RECIP: face = OPR; text = OPR_T; scale = 0.72f; desc = "reciprocal"; break;
            case ANS: face = MEM; text = MEM_T; scale = 0.68f; desc = "answer"; break;
            case CA: case CE: face = CLR; text = CLR_T; scale = 0.8f; break;
            case EQ: face = EQL; text = EQL_T; break;
            case BS: case SQRT: case NEG: face = OPR; text = OPR_T; scale = 0.8f; break;
            case ADD: case SUB: case MUL: case DIV: face = OPR; text = OPR_T; break;
            default: face = NUM; text = NUM_T; break;
        }
        if (k == Calculator.Key.SQRT) desc = "square root";
        else if (k == Calculator.Key.NEG) desc = "change sign";
        else if (k == Calculator.Key.BS) desc = "backspace";
        return new KeyView(this, k, label, desc, face, text, scale);
    }

    private void onKey(Calculator.Key k) {
        calc.press(k);
        refresh();
        prefs().edit().putString("calc", calc.save()).apply(); // survive kill/reinstall, not just pause
    }

    private void refresh() {
        Lcd.State st = Lcd.State.of(calc);
        lcd.show(st);
        memLcd.show(st);
    }

    private SharedPreferences prefs() { return getSharedPreferences("calc", MODE_PRIVATE); }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("calc", calc.save());
    }

    @Override protected void onPause() {
        super.onPause();
        prefs().edit().putString("calc", calc.save()).apply();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        click.release();
    }
}
