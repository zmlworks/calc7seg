package com.zmlworks.calc7seg;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 10-digit "commercial" calculator engine (immediate execution, like typical Casio/Sharp desk models).
 *
 * <ul>
 *   <li>Chaining executes left to right: 2 + 3 × 4 = 20.</li>
 *   <li>"=" repeats the last operation with the last second operand (constant calc):
 *       2 + 3 = 5, = 8, = 11. "a op =" uses a as the operand (5 × = 25).</li>
 *   <li>Every result is cut (truncated toward zero) to fit 10 digits; trailing zeros are stripped.</li>
 *   <li>|result| ≥ 10^10 → E (overflow), display shows the value × 10^-10 like real calculators.
 *       Division by zero, √ of a negative → E with 0. Only CA / CE leave the error state.</li>
 *   <li>M+ / M- complete a pending operation first, then add / subtract the displayed value.</li>
 *   <li>1/x replaces the displayed value (entry or result) with its reciprocal; 1/0 → E.</li>
 *   <li>Ans. recalls the result of the most recent "=" (including repeated constant "=" and the "="
 *       implied by M+/M- on a pending operation) as a new operand, like MR. Intermediate chain results
 *       are not stored. CA does not clear Ans (like memory); it starts at 0.</li>
 * </ul>
 * Pure Java, no Android dependencies, so it is unit-tested on the JVM.
 */
public final class Calculator {
    public static final int DIGITS = 10;

    public enum Key {
        D0, D1, D2, D3, D4, D5, D6, D7, D8, D9, D00, DOT,
        ADD, SUB, MUL, DIV, EQ, SQRT, NEG, BS, CA, CE,
        MC, MR, M_MINUS, M_PLUS,
        /** Reciprocal 1/x. */
        RECIP,
        /** Recall the last "=" result. */
        ANS
    }

    private static final BigDecimal LIMIT = BigDecimal.TEN.pow(DIGITS);
    private static final MathContext MC = new MathContext(32, RoundingMode.HALF_EVEN);

    private static final class CalcError extends RuntimeException {
        final String display;
        CalcError(String display) { super(null, null, false, false); this.display = display; }
    }

    private BigDecimal acc = BigDecimal.ZERO;      // left operand / running total
    private BigDecimal x = BigDecimal.ZERO;        // value shown when not typing
    private BigDecimal constVal = BigDecimal.ZERO; // operand repeated by "="
    private BigDecimal memory = BigDecimal.ZERO;
    /** Memory "in use": set by M+ / M- (even if the sum is 0), cleared only by MC. */
    private boolean memInUse;
    private BigDecimal ans = BigDecimal.ZERO;      // last "=" result
    private char op;                               // pending operator (+ - × ÷) or 0
    private char constOp;                          // operator repeated by "=" or 0
    private boolean typing;                        // a number is being keyed in
    private boolean xNew;                          // x is a fresh operand (keyed, MR, √, +/-)
    private boolean error;
    private String errorText = "0.";
    private final StringBuilder entry = new StringBuilder(); // keyed digits and optional '.', no sign
    private boolean entryNeg;

    // ---------------------------------------------------------------- input

    public void press(Key k) {
        if (error) {
            if (k == Key.CA || k == Key.CE) clearAll();
            return;
        }
        try {
            switch (k) {
                case D00: digits("00"); break;
                case DOT: dot(); break;
                case ADD: operator('+'); break;
                case SUB: operator('-'); break;
                case MUL: operator('×'); break;
                case DIV: operator('÷'); break;
                case EQ: equals(); break;
                case SQRT: sqrt(); break;
                case NEG: negate(); break;
                case BS: backspace(); break;
                case CA: clearAll(); break;
                case CE: clearEntry(); break;
                case MC: memory = BigDecimal.ZERO; memInUse = false; break;
                case MR: x = memory; typing = false; xNew = true; break;
                case M_PLUS: memoryAdd(false); break;
                case M_MINUS: memoryAdd(true); break;
                case RECIP: reciprocal(); break;
                case ANS: x = ans; typing = false; xNew = true; break;
                default: digits(String.valueOf((char) ('0' + (k.ordinal() - Key.D0.ordinal())))); break;
            }
        } catch (CalcError e) {
            error = true;
            errorText = e.display;
            op = 0; constOp = 0; typing = false; xNew = false;
        }
    }

    /** Convenience for tests: "12+3=" style key strings. Tokens: 0-9 . + - * / = and words in [..]. */
    public void keys(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') continue;
            if (c == '[') {
                int j = s.indexOf(']', i);
                press(Key.valueOf(s.substring(i + 1, j)));
                i = j;
                continue;
            }
            switch (c) {
                case '+': press(Key.ADD); break;
                case '-': press(Key.SUB); break;
                case '*': case '×': press(Key.MUL); break;
                case '/': case '÷': press(Key.DIV); break;
                case '=': press(Key.EQ); break;
                case '.': press(Key.DOT); break;
                default:
                    if (c >= '0' && c <= '9') press(Key.values()[Key.D0.ordinal() + (c - '0')]);
                    else throw new IllegalArgumentException("key " + c);
            }
        }
    }

    private void startEntryIfNeeded() {
        if (!typing) {
            entry.setLength(0);
            entryNeg = false;
            typing = true;
        }
    }

    private int entryDigits() {
        int n = 0;
        for (int i = 0; i < entry.length(); i++) if (entry.charAt(i) != '.') n++;
        return n;
    }

    private void digits(String ds) {
        startEntryIfNeeded();
        for (int i = 0; i < ds.length(); i++) {
            if (entry.length() == 1 && entry.charAt(0) == '0') entry.setLength(0); // replace leading 0
            if (entryDigits() >= DIGITS) break;
            entry.append(ds.charAt(i));
        }
        syncEntry();
    }

    private void dot() {
        startEntryIfNeeded();
        if (entry.length() == 0) entry.append('0');
        if (entry.indexOf(".") < 0) entry.append('.');
        syncEntry();
    }

    private void backspace() {
        if (!typing) return; // like real calculators: only edits the number being keyed in
        if (entry.length() > 0) entry.setLength(entry.length() - 1);
        if (entry.length() == 0) { entry.append('0'); entryNeg = false; }
        syncEntry();
    }

    private void syncEntry() {
        String s = entry.length() == 0 ? "0" : entry.toString();
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        BigDecimal v = new BigDecimal(s);
        x = entryNeg ? v.negate() : v;
        xNew = true;
    }

    private void negate() {
        if (typing) {
            entryNeg = !entryNeg;
            syncEntry();
        } else {
            x = x.negate();
            xNew = true;
        }
    }

    private void operator(char o) {
        if (op != 0 && xNew) acc = calc(acc, op, x);
        else if (op == 0) acc = x;
        // op != 0 && !xNew: operator key pressed twice -> just change the operator
        op = o;
        x = acc;
        typing = false;
        xNew = false;
    }

    private void equals() {
        if (op != 0) {
            BigDecimal b = xNew ? x : acc;
            BigDecimal r = calc(acc, op, b);
            constOp = op;
            constVal = b;
            op = 0;
            acc = x = r;
            ans = r;
        } else if (constOp != 0) {
            acc = x = calc(x, constOp, constVal);
            ans = x;
        }
        typing = false;
        xNew = false;
    }

    private void sqrt() {
        if (x.signum() < 0) throw new CalcError("0.");
        x = fit(sqrt(x));
        typing = false;
        xNew = true;
    }

    private void reciprocal() {
        if (x.signum() == 0) throw new CalcError("0.");
        x = calc(BigDecimal.ONE, '÷', x);
        typing = false;
        xNew = true;
    }

    private void memoryAdd(boolean minus) {
        if (op != 0) equals();
        BigDecimal m = minus ? memory.subtract(x) : memory.add(x);
        memory = fit(m);
        memInUse = true;
        typing = false;
        xNew = false;
    }

    private void clearEntry() {
        entry.setLength(0);
        entryNeg = false;
        typing = false;
        x = BigDecimal.ZERO;
        xNew = true;
    }

    private void clearAll() {
        acc = x = constVal = BigDecimal.ZERO;
        op = constOp = 0;
        typing = xNew = error = false;
        entry.setLength(0);
        entryNeg = false;
        errorText = "0.";
    }

    // ---------------------------------------------------------------- arithmetic

    static BigDecimal calc(BigDecimal a, char o, BigDecimal b) {
        BigDecimal r;
        switch (o) {
            case '+': r = a.add(b); break;
            case '-': r = a.subtract(b); break;
            case '×': r = a.multiply(b); break;
            case '÷':
                if (b.signum() == 0) throw new CalcError("0.");
                r = a.divide(b, MC);
                break;
            default: throw new IllegalStateException("op " + o);
        }
        return fit(r);
    }

    /** Cut v to at most 10 significant display digits; throws E on overflow. */
    static BigDecimal fit(BigDecimal v) {
        BigDecimal r = cut(v);
        if (r == null) {
            BigDecimal shifted = cut(v.movePointLeft(DIGITS)); // what a real 10-digit calc shows with E
            throw new CalcError(shifted == null ? "0." : format(shifted));
        }
        return r;
    }

    private static BigDecimal cut(BigDecimal v) {
        BigDecimal a = v.abs();
        if (a.compareTo(LIMIT) >= 0) return null;
        int intDigits = a.compareTo(BigDecimal.ONE) < 0 ? 1 : a.setScale(0, RoundingMode.DOWN).precision();
        BigDecimal r = v.setScale(DIGITS - intDigits, RoundingMode.DOWN);
        if (r.signum() == 0) return BigDecimal.ZERO;
        r = r.stripTrailingZeros();
        if (r.scale() < 0) r = r.setScale(0);
        return r;
    }

    /** Newton square root (BigDecimal.sqrt needs API 33). */
    static BigDecimal sqrt(BigDecimal v) {
        if (v.signum() == 0) return BigDecimal.ZERO;
        BigDecimal g = new BigDecimal(Math.sqrt(v.doubleValue()), MC);
        BigDecimal two = BigDecimal.valueOf(2);
        for (int i = 0; i < 8; i++) g = g.add(v.divide(g, MC)).divide(two, MC);
        // make exact squares exact: if the 12-digit rounding squares back to v, use it
        BigDecimal r = g.round(new MathContext(12, RoundingMode.HALF_EVEN));
        return r.multiply(r).compareTo(v) == 0 ? r : g;
    }

    /** Plain string with a decimal point always present ("0.", "12.5", "-3."). */
    static String format(BigDecimal v) {
        String s = v.signum() == 0 ? "0" : v.toPlainString();
        return s.indexOf('.') < 0 ? s + "." : s;
    }

    // ---------------------------------------------------------------- output

    /** Text for the LCD: optional '-', up to 10 digits, exactly one '.' (possibly trailing). */
    public String display() {
        if (error) return errorText;
        if (typing) {
            String s = entry.length() == 0 ? "0" : entry.toString();
            if (s.indexOf('.') < 0) s += ".";
            return (entryNeg ? "-" : "") + s;
        }
        return format(x);
    }

    public boolean isError() { return error; }
    /** True once M+ / M- has been used, until MC (even when the stored value is 0). */
    public boolean hasMemory() { return memInUse; }
    public BigDecimal memory() { return memory; }
    public BigDecimal ans() { return ans; }
    /** Memory value as LCD text; "" (blank panel) when memory is not in use. */
    public String memoryDisplay() { return memInUse ? format(memory) : ""; }
    /** Pending operator ('+', '-', '×', '÷') for the LCD annunciator, or 0 (none / after =, CA, E). */
    public char pendingOp() { return op; }

    // ---------------------------------------------------------------- state (rotation / restart)

    public String save() {
        return String.join("\u001f", acc.toPlainString(), x.toPlainString(), constVal.toPlainString(),
                memory.toPlainString(), String.valueOf((int) op), String.valueOf((int) constOp),
                bit(typing), bit(xNew), bit(error), errorText, "e" + entry, bit(entryNeg),
                ans.toPlainString(), bit(memInUse));
    }

    public boolean restore(String s) {
        if (s == null) return false;
        String[] p = s.split("\u001f", -1);
        if (p.length < 12 || p.length > 14) return false; // 12 = v0.1.0 (no Ans), 13 = no in-use flag
        try {
            BigDecimal a = new BigDecimal(p[0]), xx = new BigDecimal(p[1]), c = new BigDecimal(p[2]),
                    m = new BigDecimal(p[3]);
            acc = a; x = xx; constVal = c; memory = m;
            op = (char) Integer.parseInt(p[4]);
            constOp = (char) Integer.parseInt(p[5]);
            typing = p[6].equals("1"); xNew = p[7].equals("1"); error = p[8].equals("1");
            errorText = p[9];
            entry.setLength(0);
            entry.append(p[10].substring(1));
            entryNeg = p[11].equals("1");
            ans = p.length > 12 ? new BigDecimal(p[12]) : BigDecimal.ZERO;
            memInUse = p.length > 13 ? p[13].equals("1") : m.signum() != 0;
            return true;
        } catch (RuntimeException e) {
            clearAll();
            memory = ans = BigDecimal.ZERO;
            memInUse = false;
            return false;
        }
    }

    private static String bit(boolean b) { return b ? "1" : "0"; }

}
