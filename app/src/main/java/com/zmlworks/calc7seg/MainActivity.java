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
    static final int BODY = 0xFF2B2D31;
    private static final int NUM = 0xFF3B3E44, NUM_T = 0xFFF2F2EE;
    private static final int OPR = 0xFF6A7079, OPR_T = 0xFFFFFFFF;
    private static final int MEM = 0xFF4A4E55, MEM_T = 0xFFE4E6E9;
    private static final int CLR = 0xFFD2572B, CLR_T = 0xFFFFFFFF;
    private static final int EQL = 0xFF8E959E, EQL_T = 0xFFFFFFFF;

    private final Calculator calc = new Calculator();
    private LcdView lcd, memLcd;
    private Settings settings;
    private KeyClick click;

    /** Key grid, top to bottom, exactly 4 columns (owner's layout; digits 1-2-3 on top). */
    private static final Object[][] ROWS = {
            {"MC", Calculator.Key.MC}, {"MR", Calculator.Key.MR}, {"M-", Calculator.Key.M_MINUS}, {"M+", Calculator.Key.M_PLUS},
            {"1/x", Calculator.Key.RECIP}, {"Ans.", Calculator.Key.ANS}, {"CA", Calculator.Key.CA}, {"CE", Calculator.Key.CE},
            {"BS", Calculator.Key.BS}, {"√", Calculator.Key.SQRT}, {"+/-", Calculator.Key.NEG}, {"÷", Calculator.Key.DIV},
            {"1", Calculator.Key.D1}, {"2", Calculator.Key.D2}, {"3", Calculator.Key.D3}, {"×", Calculator.Key.MUL},
            {"4", Calculator.Key.D4}, {"5", Calculator.Key.D5}, {"6", Calculator.Key.D6}, {"−", Calculator.Key.SUB},
            {"7", Calculator.Key.D7}, {"8", Calculator.Key.D8}, {"9", Calculator.Key.D9}, {"+", Calculator.Key.ADD},
            {"0", Calculator.Key.D0}, {"00", Calculator.Key.D00}, {".", Calculator.Key.DOT}, {"=", Calculator.Key.EQ},
    };

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        String st = saved != null ? saved.getString("calc") : null;
        if (st == null) st = prefs().getString("calc", null);
        calc.restore(st);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BODY);
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
        printed.addView(printedText("ZML", 13, 0.25f, Gravity.START), new LinearLayout.LayoutParams(-1, -2));
        printed.addView(printedText("10 DIGITS", 9, 0.2f, Gravity.START), new LinearLayout.LayoutParams(-1, -2));
        View spacer = new View(this);
        printed.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));
        printed.addView(printedText("MEMORY", 9, 0.22f, Gravity.END), new LinearLayout.LayoutParams(-1, -2));
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
        root.addView(bezel(lcd, 9, 10), blp);

        LinearLayout pad4 = new LinearLayout(this);
        pad4.setOrientation(LinearLayout.VERTICAL);
        View.OnClickListener onClick = v -> onKey(((KeyView) v).key);
        KeyView.Feedback feedback = this::keyFeedback;
        for (int row = 0; row < ROWS.length / 4; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 4; col++) {
                Object[] k = ROWS[row * 4 + col];
                KeyView kv = makeKey((String) k[0], (Calculator.Key) k[1]);
                kv.setOnClickListener(onClick);
                kv.setFeedback(feedback);
                if (k[1] == Calculator.Key.EQ) {
                    // long-press "=" opens settings; a consumed long press suppresses the click (no compute)
                    kv.setOnLongClickListener(v -> {
                        if (settings.vibration()) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        showSettings();
                        return true;
                    });
                }
                line.addView(kv, new LinearLayout.LayoutParams(0, -1, 1));
            }
            // memory / func rows a bit shorter, like real calculators
            pad4.addView(line, new LinearLayout.LayoutParams(-1, 0, row < 2 ? 0.82f : 1f));
        }
        root.addView(pad4, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        Ui.edgeToEdge(this, root);
        refresh();
    }

    private TextView printedText(String s, float sp, float spacing, int gravity) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFF9A9EA6);
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
        bz.setStroke(Ui.dp(this, 1), 0xFF3A3C41);
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
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        Switch vib = settingSwitch("バイブレーション", settings.vibration());
        vib.setOnCheckedChangeListener((b, on) -> settings.setVibration(on));
        Switch snd = settingSwitch("キー音", settings.sound());
        snd.setOnCheckedChangeListener((b, on) -> {
            settings.setSound(on);
            if (on) click.play();
        });
        box.addView(vib);
        box.addView(snd);
        TextView hint = new TextView(this);
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

    private Switch settingSwitch(String label, boolean on) {
        Switch s = new Switch(this);
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
