package com.zmlworks.calc7seg;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    static final int BODY = 0xFF2B2D31;
    private static final int NUM = 0xFF3B3E44, NUM_T = 0xFFF2F2EE;
    private static final int OPR = 0xFF6A7079, OPR_T = 0xFFFFFFFF;
    private static final int MEM = 0xFF4A4E55, MEM_T = 0xFFE4E6E9;
    private static final int CLR = 0xFFD2572B, CLR_T = 0xFFFFFFFF;
    private static final int EQL = 0xFF8E959E, EQL_T = 0xFFFFFFFF;

    private final Calculator calc = new Calculator();
    private LcdView lcd;

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

        TextView brand = new TextView(this);
        brand.setText("ZML  ·  10 DIGITS");
        brand.setTextColor(0xFF9A9EA6);
        brand.setTextSize(11);
        brand.setLetterSpacing(0.18f);
        brand.setGravity(Gravity.END);
        brand.setPadding(0, 0, Ui.dp(this, 6), Ui.dp(this, 6));
        root.addView(brand, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout bezel = new FrameLayout(this);
        GradientDrawable bz = new GradientDrawable();
        bz.setColor(0xFF15161A);
        bz.setCornerRadius(Ui.dp(this, 10));
        bz.setStroke(Ui.dp(this, 1), 0xFF0A0A0C);
        bezel.setBackground(bz);
        int bp = Ui.dp(this, 9);
        bezel.setPadding(bp, bp, bp, bp);
        lcd = new LcdView(this);
        bezel.addView(lcd, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.bottomMargin = Ui.dp(this, 14);
        root.addView(bezel, blp);

        LinearLayout pad4 = new LinearLayout(this);
        pad4.setOrientation(LinearLayout.VERTICAL);
        View.OnClickListener click = v -> onKey(((KeyView) v).key);
        for (int row = 0; row < ROWS.length / 4; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 4; col++) {
                Object[] k = ROWS[row * 4 + col];
                KeyView kv = makeKey((String) k[0], (Calculator.Key) k[1]);
                kv.setOnClickListener(click);
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
        lcd.show(Lcd.State.of(calc));
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
}
