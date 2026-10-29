package com.zmlworks.calc7seg;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Off-device preview of the LCD using the same Lcd geometry as  See tools/preview/render.sh. */
public class LcdPreview {
    static final int BODY = 0xFF2B2D31, BG_TOP = 0xFFB4BCA0, BG_BOTTOM = 0xFF9AA386, ON = 0xF0161A12, GHOST = 0x14101808;

    public static void main(String[] a) throws Exception {
        String dir = a.length > 0 ? a[0] : ".";
        String[][] cases = {{"0.", "", ""}, {"-1234567.89", "M", ""}, {"1.23456789", "", "E"}, {"9876543210.", "M", ""}};
        int w = 1000, h = 310;
        BufferedImage img = new BufferedImage(w, cases.length * (h + 20) + 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(BODY, true));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int k = 0; k < cases.length; k++) {
            int oy = 20 + k * (h + 20);
            g.translate(20, oy);
            draw(g, w - 40, h, cases[k][0], cases[k][1].equals("M"), cases[k][2].equals("E"));
            g.translate(-20, -oy);
        }
        ImageIO.write(img, "png", new File(dir, "lcd-preview.png"));
    }

    private static void draw(Graphics2D g, int w, int h, String text, boolean mem, boolean err) {
        Lcd.Metrics m = new Lcd.Metrics(w, h);
        Lcd.Frame f = Lcd.layout(text);
        g.setPaint(new GradientPaint(0, 0, new Color(BG_TOP, true), 0, h, new Color(BG_BOTTOM, true)));
        g.fill(new RoundRectangle2D.Float(0, 0, w, h, h * 0.1f, h * 0.1f));
        Color on = new Color(ON, true), ghost = new Color(GHOST, true);
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, Math.round(m.indSize)));
        float iy = m.indTop + m.indSize * 0.85f;
        g.setColor(mem ? on : ghost); g.drawString("M", m.indLeft + m.indSize * 0.3f, iy);
        g.setColor(err ? on : ghost); g.drawString("E", m.indLeft + m.indSize * 1.7f, iy);
        seg(g, 6, m.left - m.cellW * 0.1f, m.top, m.signW, m.cellH, m, f.neg ? on : ghost);
        for (int i = 0; i < Lcd.CELLS; i++) {
            float l = m.cellLeft(i);
            for (int s = 0; s < 7; s++) {
                seg(g, s, l, m.top, m.cellW, m.cellH, m, ghost);
                if ((f.seg[i] & (1 << s)) != 0) seg(g, s, l, m.top, m.cellW, m.cellH, m, on);
            }
            float dx = l + m.cellW + (m.pitch - m.cellW) * 0.45f, dy = m.top + m.cellH - m.dpR;
            g.setColor(f.dp[i] ? on : ghost);
            g.fill(new Ellipse2D.Float(dx - m.dpR, dy - m.dpR, 2 * m.dpR, 2 * m.dpR));
        }
    }

    private static void seg(Graphics2D g, int s, float l, float t, float w, float h, Lcd.Metrics m, Color c) {
        float[] p = Lcd.segment(s, l, t, w, h, m.th, m.gap, m.slant);
        Path2D.Float path = new Path2D.Float();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        path.closePath();
        g.setColor(c);
        g.fill(path);
    }
}
