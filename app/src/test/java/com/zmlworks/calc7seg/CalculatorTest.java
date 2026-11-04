package com.zmlworks.calc7seg;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.zmlworks.calc7seg.Calculator.Key;
import org.junit.Before;
import org.junit.Test;

public class CalculatorTest {
    private Calculator c;

    @Before public void setUp() { c = new Calculator(); }

    private String run(String keys) { c.keys(keys); return c.display(); }

    private static String keysThen(Calculator x, String keys) { x.keys(keys); return x.display(); }

    @Test public void initialZero() { assertEquals("0.", c.display()); }

    @Test public void chainingImmediateExecution() {
        assertEquals("20.", run("2+3*4="));
        assertEquals("3.666666666", run("[CA]2+3*4-9/3="));   // ((2+3)*4-9)/3
        assertEquals("-1.", run("[CA]1-2="));
    }

    @Test public void chainingShowsIntermediate() {
        assertEquals("5.", run("2+3*"));
        assertEquals("4.", run("4"));
        assertEquals("20.", run("="));
    }

    @Test public void operatorReplace() { assertEquals("4.", run("8+-*/2=")); assertEquals("6.", run("[CA]8+-2=")); }

    @Test public void constantRepeat() {
        assertEquals("5.", run("2+3="));
        assertEquals("8.", run("="));
        assertEquals("11.", run("="));
        assertEquals("13.", run("10="));      // new number with constant +3
        c.keys("[CA]");
        assertEquals("6.", run("2*3="));
        assertEquals("18.", run("="));        // repeats ×3
        c.keys("[CA]");
        assertEquals("25.", run("100/4="));
        assertEquals("6.25", run("="));
        c.keys("[CA]");
        assertEquals("7.", run("10-3="));
        assertEquals("4.", run("="));
    }

    @Test public void squareWithOpEquals() { assertEquals("25.", run("5*=")); assertEquals("125.", run("=")); }

    @Test public void caKeepsMemoryCeClearsEntry() {
        c.keys("5[M_PLUS]");
        assertEquals("12.", run("8+3[CE]4="));
        c.keys("[CA]");
        assertEquals("0.", c.display());
        assertTrue(c.hasMemory());
        assertEquals("5.", run("[MR]"));
    }

    @Test public void memory() {
        assertFalse(c.hasMemory());
        c.keys("10[M_PLUS]");
        assertTrue(c.hasMemory());
        c.keys("3[M_PLUS]2[M_MINUS]");
        assertEquals("11.", run("[MR]"));
        assertEquals("16.", run("+5="));
        c.keys("2*3[M_PLUS]");           // completes pending 2×3=6, adds to memory
        assertEquals("6.", c.display());
        assertEquals("17.", run("[MR]"));
        c.keys("[MC]");
        assertFalse(c.hasMemory());
        assertEquals("0.", run("[MR]"));
        // M-: memory goes negative, still shows M
        c.keys("[CA]4[M_MINUS]");
        assertTrue(c.hasMemory());
        assertEquals("-4.", run("[MR]"));
        // MR as operand
        assertEquals("6.", run("[CA]10+[MR]="));
    }

    @Test public void backspace() {
        assertEquals("123.", run("1234[BS]"));
        assertEquals("1.", run("[BS][BS]"));
        assertEquals("0.", run("[BS]"));
        assertEquals("0.", run("[BS]"));
        assertEquals("1.2", run("[CA]1.25[BS]"));
        assertEquals("1.", run("[BS]"));
        assertEquals("1.", run("[BS]"));      // removed the "." then nothing more than "1"? -> "1" stays
        // BS does not touch a result
        assertEquals("5.", run("[CA]2+3=[BS]"));
    }

    @Test public void doubleZero() {
        assertEquals("0.", run("[D00]"));
        assertEquals("100.", run("1[D00]"));
        assertEquals("10000.", run("[D00]"));
        assertEquals("1.005", run("[CA]1.[D00]5"));
        // limit: 9 digits + 00 -> only one zero fits
        assertEquals("1234567890.", run("[CA]123456789[D00]"));
    }

    @Test public void digitLimit() {
        assertEquals("1234567890.", run("12345678901"));
        assertEquals("0.123456789", run("[CA].1234567899"));
        assertEquals("1.", run("[CA]00001"));
        assertEquals("1.5", run("[CA]1..5"));
    }

    @Test public void sqrt() {
        assertEquals("1.414213562", run("2[SQRT]"));
        assertEquals("4.", run("[CA]16[SQRT]"));
        assertEquals("12.", run("[CA]9+[SQRT]="));      // 9 + √9
        assertEquals("0.", run("[CA]0[SQRT]"));
        assertEquals("1.1", run("[CA]1.21[SQRT]"));
    }

    @Test public void sqrtChainAndNegative() {
        assertEquals("52.", run("3*3+4*4="));
        assertEquals("7.21110255", run("[SQRT]"));     // 7.211102550 -> trailing zero stripped
        c.keys("[CA]4[NEG][SQRT]");
        assertTrue(c.isError());
    }

    @Test public void sign() {
        assertEquals("-5.", run("5[NEG]"));
        assertEquals("5.", run("[NEG]"));
        assertEquals("-12.", run("[CA]12[NEG]"));
        assertEquals("-9.", run("+3="));          // -12 + 3
        assertEquals("9.", run("[NEG]"));         // negate result
        assertEquals("93.", run("93"));           // digits after +/- on a result start fresh
        assertEquals("0.", run("[CA]5+[NEG]="));  // 5 + (-5)
        assertEquals("-0.5", run("[CA].5[NEG]"));
    }

    @Test public void signBeforeNumberIsKept() {
        assertEquals("-5.", run("[CA][NEG]5"));
        assertEquals("-8.", run("[CA][NEG]5-3="));
        assertEquals("2.", run("[CA]5+[NEG]3="));      // 5 + (-3), sign not dropped
        assertEquals("-15.", run("[CA]5*[NEG]3="));
        assertEquals("8.", run("[CA]5+[NEG][NEG]3="));  // toggled back
        assertEquals("-0.5", run("[CA][NEG].5"));
        assertEquals("3.", run("[CA]2+5=[NEG]3"));       // negated result, then a new number: positive
        assertEquals("4.", run("[CA][NEG]+4="));        // armed sign cleared by another key
    }

    @Test public void restoreRoundTripAndArmedSign() {
        c.keys("12+[NEG]");
        Calculator d = new Calculator();
        assertTrue(d.restore(c.save()));
        d.keys("3=");
        assertEquals("9.", d.display());
    }

    @Test public void restoreRejectsDamagedState() {
        c.keys("7[M_PLUS]12+3");
        String good = c.save();
        String[] p = good.split("\u001f", -1);
        String[][] bad = {
                {"4", "120"},                 // unknown operator 'x'
                {"10", "e12a"},               // non-digit in entry
                {"10", "e12345678901"},       // 11 digits
                {"10", "1.2"},                // missing 'e' prefix
                {"0", "1e400"},               // out of range
                {"1", "12345678901"},         // > 10 digits
                {"9", "<script>"},            // error text
                {"6", "yes"},                 // flag
        };
        for (String[] b : bad) {
            String[] q = p.clone();
            q[Integer.parseInt(b[0])] = b[1];
            Calculator d = new Calculator();
            d.keys("5[M_PLUS]");
            assertFalse(b[0] + "=" + b[1], d.restore(String.join("\u001f", q)));
            assertEquals("0.", d.display());
            assertFalse(d.hasMemory());
        }
        Calculator d = new Calculator();
        assertTrue(d.restore(good));
        assertEquals("3.", d.display());
        d.keys("=");
        assertEquals("15.", d.display());
        // older formats still load
        assertTrue(new Calculator().restore(String.join("\u001f", java.util.Arrays.copyOf(p, 12))));
        assertTrue(new Calculator().restore(String.join("\u001f", java.util.Arrays.copyOf(p, 13))));
        assertTrue(new Calculator().restore(String.join("\u001f", java.util.Arrays.copyOf(p, 14))));
    }

    @Test public void divisionByZero() {
        c.keys("5/0=");
        assertTrue(c.isError());
        assertEquals("0.", c.display());
        c.keys("123+");                     // ignored while in error
        assertTrue(c.isError());
        c.keys("[CE]");
        assertFalse(c.isError());
        assertEquals("3.", run("1+2="));
        c.keys("[M_PLUS]8/0+");             // error raised by chained operator too
        assertTrue(c.isError());
        c.keys("[CA]");
        assertFalse(c.isError());
        assertTrue(c.hasMemory());          // memory survives the error
    }

    @Test public void overflow() {
        c.keys("9999999999+1=");
        assertTrue(c.isError());
        assertEquals("1.", c.display());              // 10000000000 × 10^-10
        c.keys("[CA]");
        c.keys("12345678*1000=");
        assertTrue(c.isError());
        assertEquals("1.2345678", c.display());
        c.keys("[CA]99999*99999=");
        assertFalse(c.isError());
        assertEquals("9999800001.", c.display());
        c.keys("=");                                    // constant × again -> overflow
        assertTrue(c.isError());
        c.keys("[CA]9999999999[NEG]-1=");
        assertTrue(c.isError());
        assertEquals("-1.", c.display());
    }

    @Test public void memoryOverflow() {
        c.keys("9999999999[M_PLUS][M_PLUS]");
        assertTrue(c.isError());
        c.keys("[CA][MR]");
        assertEquals("9999999999.", c.display());
    }

    @Test public void truncationAndStrip() {
        assertEquals("0.333333333", run("1/3="));
        assertEquals("0.999999999", run("*3="));
        assertEquals("0.666666666", run("[CA]2/3="));
        assertEquals("3333333.333", run("[CA]10000000/3="));
        assertEquals("2.5", run("[CA]1.25*2="));       // 2.50 -> 2.5
        assertEquals("3.", run("[CA]1.5+1.5="));
        assertEquals("0.", run("[CA].0000001*.0001=")); // underflow -> 0
        assertEquals("0.3", run("[CA].1+.2="));
    }

    @Test public void reciprocal() {
        assertEquals("0.25", run("4[RECIP]"));
        assertEquals("4.", run("[RECIP]"));
        assertEquals("0.333333333", run("[CA]3[RECIP]"));        // truncated to 10 digits
        assertEquals("-0.5", run("[CA]2[NEG][RECIP]"));
        assertEquals("0.2", run("[CA]2+3=[RECIP]"));             // on a result
        assertEquals("10.5", run("[CA]10+2[RECIP]="));            // as the second operand
        assertEquals("5.2", run("[CA]5+[RECIP]="));               // 5 + 1/5 (like 9 + √)
        assertEquals("3.", run("[CA]1[RECIP]+2="));
        c.keys("[CA]0[RECIP]");
        assertTrue(c.isError());
        assertEquals("0.", c.display());
        c.keys("[CE]");
        assertFalse(c.isError());
        c.keys(".00000001[RECIP]");                             // 1e8 fits
        assertEquals("100000000.", c.display());
        c.keys("[CA].000000001[RECIP]");                        // 1e9 fits
        assertFalse(c.isError());
    }

    @Test public void ans() {
        assertEquals("0.", run("[ANS]"));                       // starts at 0
        c.keys("[CA]2+3=");
        assertEquals("5", c.ans().toPlainString());
        assertEquals("15.", run("10+[ANS]="));                  // Ans as operand
        assertEquals("15", c.ans().toPlainString());
        assertEquals("18.", run("[ANS]+3="));                   // starts a new chain like MR
        assertEquals("21.", run("="));                          // constant = updates Ans too
        assertEquals("21", c.ans().toPlainString());
        c.keys("[CA]7*6+");                                     // intermediate 42 is not an "=" result
        assertEquals("21", c.ans().toPlainString());
        c.keys("[CA]");
        assertEquals("21.", run("[ANS]"));                      // CA keeps Ans
        assertEquals("7.", run("7"));                           // digits after Ans start a fresh entry
    }

    @Test public void ansViaMemoryCompletion() {
        c.keys("4*5[M_PLUS]");                                  // M+ completes 4×5=
        assertEquals("20", c.ans().toPlainString());
        assertEquals("20.", run("[CA][MR]"));
    }

    @Test public void pendingOperator() {
        assertEquals(0, c.pendingOp());
        c.keys("2+");
        assertEquals('+', c.pendingOp());
        c.keys("3");
        assertEquals('+', c.pendingOp());                       // still shown while keying the 2nd number
        c.keys("*");
        assertEquals('×', c.pendingOp());
        c.keys("-");
        assertEquals('-', c.pendingOp());                       // operator replaced
        c.keys("/4");
        assertEquals('÷', c.pendingOp());
        c.keys("=");
        assertEquals(0, c.pendingOp());                         // cleared by =
        c.keys("=");
        assertEquals(0, c.pendingOp());                         // constant = does not show it
        c.keys("5+");
        c.keys("[CA]");
        assertEquals(0, c.pendingOp());                         // cleared by CA
        c.keys("5/0=");
        assertEquals(0, c.pendingOp());                         // cleared by E
        c.keys("[CA]5+2[M_PLUS]");
        assertEquals(0, c.pendingOp());                         // M+ completes the operation
        c.keys("[CA]9+[SQRT]");
        assertEquals('+', c.pendingOp());                       // √ keeps the pending operation
        c.keys("[CE]");
        assertEquals('+', c.pendingOp());                       // CE keeps it too
    }

    @Test public void lcdState() {
        Lcd.State e = Lcd.State.of(c);
        assertEquals("", e.mem);                                 // memory panel blank (ghosts only) when empty
        assertEquals(0, e.op);
        c.keys("12[M_PLUS]3+4");
        Lcd.State s = Lcd.State.of(c);
        assertEquals("4.", s.main);
        assertEquals("12.", s.mem);
        assertEquals('+', s.op);
        assertFalse(s.error);
        c.keys("=[MC]");
        assertEquals("", Lcd.State.of(c).mem);
        c.keys("5[M_MINUS]");
        assertEquals("-5.", Lcd.State.of(c).mem);
        assertArrayEquals(new int[Lcd.CELLS], Lcd.layout(new Calculator().memoryDisplay()).seg);
        Lcd.Frame blank = Lcd.layout("");
        assertFalse(blank.neg);
        for (int i = 0; i < Lcd.CELLS; i++) assertFalse(blank.dp[i]);
    }

    @Test public void operatorAnnunciatorMapping() {
        assertEquals(-1, Lcd.opIndex((char) 0));
        assertEquals(0, Lcd.opIndex('+'));
        assertEquals(1, Lcd.opIndex('-'));
        assertEquals(2, Lcd.opIndex('×'));
        assertEquals(3, Lcd.opIndex('÷'));
        // every operator the engine can report maps to its own fixed annunciator
        String[] keys = {"1+", "1-", "1*", "1/"};
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (String k : keys) {
            Calculator x = new Calculator();
            x.keys(k);
            int i = Lcd.opIndex(Lcd.State.of(x).op);
            assertTrue(i >= 0);
            seen.add(i);
        }
        assertEquals(4, seen.size());
        assertEquals(2, Lcd.opSymbol('+', 0, 0, 10).size());
        assertEquals(1, Lcd.opSymbol('-', 0, 0, 10).size());
        assertEquals(2, Lcd.opSymbol('×', 0, 0, 10).size());
        assertEquals(3, Lcd.opSymbol('÷', 0, 0, 10).size());
    }

    @Test public void annunciatorsDoNotOverlapDigitsOrE() {
        for (float w : new float[]{400, 1000, 1440}) {
            Lcd.Metrics m = new Lcd.Metrics(w);
            float hb = m.annS * 0.58f;                              // inverted block half-size
            float cy = m.annTop + m.annS / 2;
            assertTrue(cy + hb < m.d.tickTop());                    // above the digits and their ticks
            assertTrue(m.annTop + m.annS / 2 - hb > 0);
            float c0 = m.annLeft + m.annS / 2;
            for (int i = 0; i < 3; i++)                             // blocks don't touch each other
                assertTrue(c0 + i * m.annPitch + hb < c0 + (i + 1) * m.annPitch - hb);
            float mid = (c0 - hb + c0 + 3 * m.annPitch + hb) / 2;
            assertTrue(mid > w * 0.5f && mid < w * 0.65f);          // centre, slightly right
            // E: cut-out block of operator size, far left of the top row, clear of everything
            assertEquals(hb, m.blockHalf(), 1e-4);
            float hh = m.eHalfH();
            assertEquals(1.5f * hb, hh, 1e-4);                      // 1.5x as tall
            assertEquals(cy - hb, m.eCy - hh, 1e-4);                // top aligned with the operators
            assertTrue(m.eCy - hh > 0);
            assertTrue(m.eCx - hb > 0);                             // inside the glass
            assertTrue(m.eCx + hb < c0 - hb - m.annS);              // well left of the "+" block
            assertTrue(m.eCx < m.d.cellLeft(0));                    // over the sign slot, far left
            assertTrue(m.eCx + hb < m.d.cellLeft(0));               // clear of the first digit cell
            // clear of the sign-slot minus bar (segment g, centred at mid cell height)
            assertTrue(m.eCy + hh < m.d.top + m.d.cellH / 2 - m.d.th * 1.5f);
            // sign slot is narrow (middle bar only) and digits are back to the 0.8.0 size
            assertEquals(m.d.cellW * 0.9f, m.d.signW, 1e-3);
            assertEquals((w - 2 * m.pad) / Lcd.Digits.unitsWide(), m.d.cellW, 1e-3);
            // rightmost digit stays inside the glass
            assertTrue(m.d.cellLeft(Lcd.CELLS - 1) + m.d.cellW + m.d.cellH * m.d.slant < w);
        }
    }

    @Test public void floatingMinusSitsLeftOfMostSignificantDigit() {
        assertEquals(4, Lcd.layout("-1234.5").signCell);            // 5 digits in cells 5..9, minus in 4
        assertEquals(8, Lcd.layout("-5.").signCell);
        assertEquals(7, Lcd.layout("-0.5").signCell);               // leading 0 is a digit
        assertEquals(-2, Lcd.layout("12.5").signCell);              // positive: no minus anywhere
        assertEquals(-1, Lcd.layout("-1234567890.").signCell);      // 10 digits: spare sign cell
        assertEquals(-1, Lcd.layout("-0.123456789").signCell);
        assertEquals(Lcd.MINUS, 1 << 6);                             // middle bar (segment g)
        // negatives keep the full 10-digit range (sign cell is extra, not a digit)
        Calculator c = new Calculator();
        c.keys("9999999999[NEG]");
        assertEquals("-9999999999.", c.display());
        assertEquals(-1, Lcd.layout(c.display()).signCell);
        c.keys("-1=");
        assertTrue(c.isError());                                     // -10^10 overflows like +10^10
        Calculator d = new Calculator();
        d.keys("1-1235.5=");
        assertEquals("-1234.5", d.display());
        assertEquals("-1'234.5", Lcd.grouped(d.display()));
        assertEquals(4, Lcd.layout(d.display()).signCell);
    }

    @Test public void floatingMinusIsDrawnAsSegmentG() {
        // record the lit polygons and check the minus cell lights exactly one segment
        Lcd.Metrics m = new Lcd.Metrics(1000);
        final java.util.List<float[]> lit = new java.util.ArrayList<>();
        Lcd.Painter rec = new Lcd.Painter() {
            @Override public void poly(float[] xy, int color) { if (color == Lcd.ON) lit.add(xy); }
            @Override public void circle(float cx, float cy, float r, int color) {}
            @Override public void roundRect(float l, float t, float r, float b, float rad, int color) {}
            @Override public void text(String s, float cx, float baseline, float size, int color) {}
        };
        Lcd.State st = new Lcd.State();
        st.main = "-5.";
        Lcd.render(rec, m, st);
        float l = m.d.cellLeft(8), r = l + m.d.cellW + m.d.cellH * m.d.slant;
        int inCell = 0;
        for (float[] p : lit) if (p[0] >= l && p[0] <= r) inCell++;
        assertEquals(1, inCell);
        st.main = "5.";
        lit.clear();
        Lcd.render(rec, m, st);
        for (float[] p : lit) assertFalse(p[0] >= l && p[0] <= r);
        // sign slot (middle bar only) lights only for a negative that fills all 10 digits
        float sl = m.d.cellLeft(-1), sr = m.d.cellLeft(0) - m.d.cellW * 0.05f;
        st.main = "-1234567890.";
        lit.clear();
        Lcd.render(rec, m, st);
        int slot = 0;
        for (float[] p : lit) if (p[0] >= sl && p[0] < sr && p[1] > m.d.top) slot++;
        assertEquals(1, slot);
        st.main = "-123.";
        lit.clear();
        Lcd.render(rec, m, st);
        for (float[] p : lit) assertFalse(p[0] >= sl && p[0] < sr && p[1] > m.d.top);
        // E block lights only in the error state
        st.error = true;
        lit.clear();
        Lcd.render(rec, m, st);
        int eSegs = 0;
        for (float[] p : lit) if (p[0] < m.eCx + m.blockHalf() && p[1] < m.d.top) eSegs++;
        assertEquals(1, eSegs);                                      // one dark block, E cut out
    }

    @Test public void cutOutColourMatchesLcdGradient() {
        assertEquals(Lcd.BG_TOP, Lcd.bgAt(0));
        assertEquals(Lcd.BG_BOTTOM, Lcd.bgAt(1));
        assertEquals(Lcd.BG_TOP, Lcd.bgAt(-0.5f));
    }

    @Test public void thousandsSeparators() {
        assertEquals("0.", Lcd.grouped("0."));
        assertEquals("999.", Lcd.grouped("999."));
        assertEquals("1'000.", Lcd.grouped("1000."));
        assertEquals("12'345.", Lcd.grouped("12345."));
        assertEquals("1'234'567.89", Lcd.grouped("1234567.89"));
        assertEquals("9'999'999'999.", Lcd.grouped("9999999999."));
        assertEquals("-123'456.", Lcd.grouped("-123456."));
        assertEquals("1'234.5678", Lcd.grouped("1234.5678"));      // never right of the point
        assertEquals("0.123456789", Lcd.grouped("0.123456789"));
        assertEquals("12'345", Lcd.grouped("12345"));              // no point: count from the right
        // cell positions: "1234567.89" (9 digits) right-aligned in 10 cells -> cells 1..9, point on cell 7
        Lcd.Frame f = Lcd.layout("1234567.89");
        boolean[] want = new boolean[Lcd.CELLS];
        want[1] = true; want[4] = true;                          // after "1" and after "4"
        assertArrayEquals(want, f.sep);
        // grouping is purely visual: engine value unchanged
        Calculator x = new Calculator();
        x.keys("1234567");
        assertEquals("1234567.", x.display());
        assertEquals("1'234'567.", Lcd.grouped(x.display()));
        Calculator mc = new Calculator();
        mc.keys("1234[M_PLUS]");
        assertEquals("1'234.", Lcd.grouped(mc.memoryDisplay()));
    }

    @Test public void keyLayout() {
        assertEquals(4, KeyLayout.COLUMNS);
        assertEquals(7, KeyLayout.rows());
        assertEquals(KeyLayout.KEYS.length, KeyLayout.LABELS.length);
        String[] rows = {"MC MR M- M+", "1/x Ans. CA CE", "BS √ +/- ÷", "7 8 9 ×", "4 5 6 −", "1 2 3 +", "0 00 . ="};
        for (int r = 0; r < rows.length; r++)
            assertEquals(rows[r], String.join(" ", java.util.Arrays.copyOfRange(KeyLayout.LABELS, r * 4, r * 4 + 4)));
        Calculator.Key[] digitsRow4 = {Calculator.Key.D7, Calculator.Key.D8, Calculator.Key.D9, Calculator.Key.MUL};
        Calculator.Key[] digitsRow6 = {Calculator.Key.D1, Calculator.Key.D2, Calculator.Key.D3, Calculator.Key.ADD};
        assertArrayEquals(digitsRow4, java.util.Arrays.copyOfRange(KeyLayout.KEYS, 12, 16));
        assertEquals(Calculator.Key.SUB, KeyLayout.KEYS[19]);
        assertArrayEquals(digitsRow6, java.util.Arrays.copyOfRange(KeyLayout.KEYS, 20, 24));
        assertEquals(Calculator.Key.EQ, KeyLayout.KEYS[27]);
        // BS √ +/- are short keys in the ÷ row; nothing else is
        for (int i = 0; i < KeyLayout.KEYS.length; i++)
            assertEquals(i == 8 || i == 9 || i == 10, KeyLayout.isShortInFullRow(i));
        assertEquals(java.util.EnumSet.allOf(Calculator.Key.class).size(), new java.util.HashSet<>(java.util.Arrays.asList(KeyLayout.KEYS)).size());
    }

    @Test public void segmentsAreHexagonsInsideTheCell() {
        Lcd.Digits d = new Lcd.Digits(1000, 0, 100, 2.15f, 0.27f);
        for (int sg = 0; sg < 7; sg++) {
            float[] p = Lcd.segment(sg, 0, 0, 100, 215, d.th, d.gap, 0);
            assertEquals(12, p.length);                              // 0.2/0.3 hexagonal style
            for (int i = 0; i < p.length; i += 2) {
                assertTrue(p[i] >= -0.01f && p[i] <= 100.01f);
                assertTrue(p[i + 1] >= -0.01f && p[i + 1] <= 215.01f);
            }
        }
    }

    @Test public void dotLowTickHigh() {
        for (float w : new float[]{400, 1080}) {
            Lcd.Metrics m = new Lcd.Metrics(w);
            Lcd.Digits d = m.d;
            float base = d.top + d.cellH;
            assertTrue(d.dotY() > base - d.dpR);                     // lower than 0.3/0.4 (bottom-aligned)
            assertTrue(d.dotY() + d.dpR < m.height);                 // still on the glass
            assertTrue(d.tickTop() < d.top);                         // raised above the digit tops
            assertTrue(d.tickTop() > m.annTop + m.annS / 2 + m.annS * 0.58f); // below the operator blocks
            Lcd.MemMetrics mm = new Lcd.MemMetrics(w * 0.6f);
            assertTrue(mm.d.tickTop() > 0);
            assertTrue(mm.d.dotY() + mm.d.dpR < mm.height);
        }
    }

    @Test public void settingsDefaultsAndPersistence() {
        java.util.Map<String, Boolean> map = new java.util.HashMap<>();
        Settings.Store store = new Settings.Store() {
            @Override public boolean get(String k, boolean d) { Boolean v = map.get(k); return v == null ? d : v; }
            @Override public void put(String k, boolean v) { map.put(k, v); }
        };
        Settings st = new Settings(store);
        assertTrue(st.vibration());
        assertTrue(st.sound());
        st.setVibration(false);
        st.setSound(false);
        Settings again = new Settings(store);                       // new instance, same storage
        assertFalse(again.vibration());
        assertFalse(again.sound());
        again.setSound(true);
        assertTrue(new Settings(store).sound());
        assertFalse(new Settings(store).vibration());
        // main display look: flat by default, raised choice persists, independent of the others
        assertFalse(new Settings(store).raisedLcd());
        new Settings(store).setRaisedLcd(true);
        assertTrue(new Settings(store).raisedLcd());
        assertTrue(new Settings(store).sound());
        new Settings(store).setRaisedLcd(false);
        assertFalse(new Settings(store).raisedLcd());
    }

    @Test public void memoryShownOnceUsedEvenAtZero() {
        assertEquals("", c.memoryDisplay());                     // fresh: blank
        c.keys("2[M_PLUS]");
        assertEquals("2.", c.memoryDisplay());
        c.keys("2[M_MINUS]");
        assertTrue(c.hasMemory());                               // in use although the value is 0
        assertEquals("0.", c.memoryDisplay());
        c.keys("[CA]");
        assertEquals("0.", c.memoryDisplay());                   // CA keeps it
        Calculator d = new Calculator();                          // persisted with the value
        assertTrue(d.restore(c.save()));
        assertTrue(d.hasMemory());
        assertEquals("0.", d.memoryDisplay());
        c.keys("[MC]");
        assertFalse(c.hasMemory());
        assertEquals("", c.memoryDisplay());
        Calculator e = new Calculator();
        assertTrue(e.restore(c.save()));
        assertEquals("", e.memoryDisplay());
        c.keys("[MR]");                                          // MR of empty memory recalls 0, stays blank
        assertEquals("0.", c.display());
        assertEquals("", c.memoryDisplay());
        c.keys("0[M_PLUS]");                                     // M+ of 0 still puts memory in use
        assertEquals("0.", c.memoryDisplay());
        // 0.9.1 state (13 fields): in use iff non-zero
        String v091 = String.join("\u001f", "0", "5", "0", "0", "0", "0", "0", "0", "0", "0.", "e", "0", "0");
        Calculator o = new Calculator();
        assertTrue(o.restore(v091));
        assertFalse(o.hasMemory());
        assertEquals("", o.memoryDisplay());
    }

    @Test public void saveRestore() {
        c.keys("7[M_PLUS]2+3*4");
        Calculator d = new Calculator();
        assertTrue(d.restore(c.save()));
        assertEquals("4.", d.display());
        assertTrue(d.hasMemory());
        d.keys("=");
        assertEquals("20.", d.display());
        assertFalse(new Calculator().restore("garbage"));
        // Ans persists
        Calculator e = new Calculator();
        e.keys("6*7=[CA]");
        Calculator f = new Calculator();
        assertTrue(f.restore(e.save()));
        assertEquals("42.", keysThen(f, "[ANS]"));
        // v0.1.0 state (12 fields, no Ans) still restores
        String old = String.join("\u001f", "0", "5", "0", "3", "0", "0", "0", "0", "0", "0.", "e", "0");
        Calculator o = new Calculator();
        assertTrue(o.restore(old));
        assertEquals("5.", o.display());
        assertTrue(o.hasMemory());
        assertEquals("0", o.ans().toPlainString());
    }

    @Test public void lcdLayout() {
        Lcd.Frame f = Lcd.layout("-12.5");
        assertTrue(f.neg);
        assertEquals(Lcd.DIGIT[5], f.seg[9]);
        assertEquals(Lcd.DIGIT[2], f.seg[8]);
        assertEquals(Lcd.DIGIT[1], f.seg[7]);
        assertEquals(0, f.seg[6]);
        assertTrue(f.dp[8]);
        assertFalse(f.dp[9]);
        Lcd.Frame z = Lcd.layout("0.");
        assertTrue(z.dp[9]);
        assertEquals(Lcd.DIGIT[0], z.seg[9]);
        Lcd.Frame full = Lcd.layout("1234567890.");
        assertArrayEquals(new int[]{Lcd.DIGIT[1], Lcd.DIGIT[2], Lcd.DIGIT[3], Lcd.DIGIT[4], Lcd.DIGIT[5],
                Lcd.DIGIT[6], Lcd.DIGIT[7], Lcd.DIGIT[8], Lcd.DIGIT[9], Lcd.DIGIT[0]}, full.seg);
    }

    @Test public void memoryMinusFloatsNextToNumber() {
        assertTrue(new Lcd.MemMetrics(500).d.floatSign);
        Lcd.Frame f = Lcd.layout("-5.");
        assertEquals(Lcd.CELLS - 2, f.signCell);   // the cell just left of the leading digit
    }
}
