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
        assertEquals("0.", e.mem);                               // memory panel shows a lit "0." when empty
        assertEquals(0, e.op);
        c.keys("12[M_PLUS]3+4");
        Lcd.State s = Lcd.State.of(c);
        assertEquals("4.", s.main);
        assertEquals("12.", s.mem);
        assertEquals('+', s.op);
        assertFalse(s.error);
        c.keys("=[MC]");
        assertEquals("0.", Lcd.State.of(c).mem);
        c.keys("5[M_MINUS]");
        assertEquals("-5.", Lcd.State.of(c).mem);
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, Lcd.DIGIT[0]}, Lcd.layout(new Calculator().memoryDisplay()).seg);
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
            float annBottom = m.annTop + m.annS;
            assertTrue(annBottom < m.d.top);                       // above the digits
            float lastRight = m.annLeft + 3 * m.annPitch + m.annS;
            assertTrue(lastRight < m.eX - m.eSize * 0.4f);         // left of E
            assertTrue(m.eX + m.eSize < w);
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
}
