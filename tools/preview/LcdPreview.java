package com.zmlworks.calc7seg;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Off-device preview of the LCD, drawn by the same Lcd.render as LcdView. See tools/preview/render.sh. */
public class LcdPreview implements Lcd.Painter {
    static final int BODY = 0xFF2B2D31;
    private final Graphics2D g;

    LcdPreview(Graphics2D g) { this.g = g; }

    public static void main(String[] a) throws Exception {
        String dir = a.length > 0 ? a[0] : ".";
        String[] scripts = {"", "[CA]1234[M_PLUS]567+89", "[CA]7[RECIP]", "[CA]2.5[NEG]*", "[CA]100/8-", "[CA]9999999999*9="};
        int w = 1000;
        Lcd.Metrics m = new Lcd.Metrics(w);
        int h = Math.round(m.height);
        BufferedImage img = new BufferedImage(w + 40, scripts.length * (h + 20) + 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(BODY, true));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        Calculator c = new Calculator();
        for (int k = 0; k < scripts.length; k++) {
            c.keys(scripts[k]);
            int oy = 20 + k * (h + 20);
            g.translate(20, oy);
            g.setPaint(new GradientPaint(0, 0, new Color(Lcd.BG_TOP, true), 0, h, new Color(Lcd.BG_BOTTOM, true)));
            g.fill(new RoundRectangle2D.Float(0, 0, w, h, h * 0.1f, h * 0.1f));
            Lcd.render(new LcdPreview(g), m, Lcd.State.of(c));
            g.translate(-20, -oy);
        }
        ImageIO.write(img, "png", new File(dir, "lcd-preview.png"));
    }

    @Override public void poly(float[] p, int color) {
        Path2D.Float path = new Path2D.Float();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        path.closePath();
        g.setColor(new Color(color, true));
        g.fill(path);
    }

    @Override public void circle(float cx, float cy, float r, int color) {
        g.setColor(new Color(color, true));
        g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
    }

    @Override public void roundRect(float l, float t, float r, float b, float rad, int color) {
        g.setColor(new Color(color, true));
        g.fill(new RoundRectangle2D.Float(l, t, r - l, b - t, rad * 2, rad * 2));
    }

    @Override public void text(String s, float cx, float baseline, float size, int color) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size)));
        g.setColor(new Color(color, true));
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2f, baseline);
    }
}
