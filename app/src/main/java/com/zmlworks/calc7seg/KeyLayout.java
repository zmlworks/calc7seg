package com.zmlworks.calc7seg;

import static com.zmlworks.calc7seg.Calculator.Key.*;

/** Key grid, top to bottom, exactly 4 columns (pure Java so the layout is unit-testable). */
final class KeyLayout {
    private KeyLayout() {}

    static final int COLUMNS = 4;

    static final String[] LABELS = {
            "MC", "MR", "M-", "M+",
            "1/x", "Ans.", "CA", "CE",
            "BS", "√", "+/-", "÷",
            "7", "8", "9", "×",
            "4", "5", "6", "−",
            "1", "2", "3", "+",
            "0", "00", ".", "=",
    };

    static final Calculator.Key[] KEYS = {
            MC, MR, M_MINUS, M_PLUS,
            RECIP, ANS, CA, CE,
            BS, SQRT, NEG, DIV,
            D7, D8, D9, MUL,
            D4, D5, D6, SUB,
            D1, D2, D3, ADD,
            D0, D00, DOT, EQ,
    };

    static int rows() { return KEYS.length / COLUMNS; }
}
