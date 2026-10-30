#!/usr/bin/env python3
"""Generates the adaptive launcher icon (vector drawables) and, optionally, a PNG preview sheet.

0.8 composition — a zoomed-in crop of the app itself (after the owner's close-up photo): the
bottom-right corner of the main LCD fills the upper ~60 % (pale green glass, thick dark bezel with a
rounded bottom-right corner), large lit 7-segment "0.8" with a round decimal point, ghost "8"s and
ghost ' separators trailing off the left edge, then a strip of the SFC-grey body and the glossy top
of the dark M+ key (with its neighbour cut off at the left) at the bottom.

The lit digits and the M+ label stay inside the 66-unit safe circle (centre 54,54, r 33).
Segment geometry matches Lcd.segment() (hexagonal, slanted). The same shape list is written as
VectorDrawable XML (res/drawable/ic_bg.xml, ic_fg.xml, ic_mono.xml) and as SVG for the preview.

usage: make_icon.py [preview.png [launcher-sheet-prefix]]
"""
import math, os, sys

RES = "app/src/main/res/drawable"

# palette (ARGB)
BODY_TOP, BODY_MID, BODY_BOTTOM = "#FFD6D4D0", "#FFCECCC7", "#FFC3C1BC"
BEZEL, BEZEL_HI, BEZEL_EDGE = "#FF141518", "#FF2A2C31", "#FF6A6966"
LCD_TOP, LCD_BOTTOM = "#FFB4BCA0", "#FF9AA386"
ON, GHOST = "#FF161A12", "#1C161A12"
KEY_TOP, KEY_BOTTOM, KEY_WALL, KEY_RIM = "#FF4C5158", "#FF33363C", "#FF1E2024", "#33FFFFFF"
WELL = "#40000000"
LABEL = "#FFE4E6E9"

# geometry (viewport 108)
LCD = (-12.0, -12.0, 74.5, 54.5, 2.6)        # glass l,t,r,b,corner
BZ_T = 5.4                                   # bezel thickness
CELL_W, CELL_H, TH, SLANT = 15.0, 26.0, 3.9, 0.07
GAP = TH * 0.11
TOP = 23.5
PITCH = 20.5
LIT = {31.0: 0b0111111, 51.5: 0b1111111}     # cell left → segment mask ("0", "8")
GHOST_CELLS = (-10.0, 10.5)
DP_R = TH * 0.58
KEY_TOP_Y = 67.5
KEYS = ((-26.0, 36.0), (42.5, 86.0))         # key cap x-ranges (left one is cut off by the edge)
KEY_R = 7.0


def segment(s, l, t, w, h, th, gap, slant):
    x0, x1 = l + th / 2, l + w - th / 2
    y0, ym, y1 = t + th / 2, t + h / 2, t + h - th / 2
    def horiz(y):
        a, b, r = x0 + gap, x1 - gap, th / 2
        return [(a, y), (a + r, y - r), (b - r, y - r), (b, y), (b - r, y + r), (a + r, y + r)]
    def vert(x, ya, yb):
        a, b, r = ya + gap, yb - gap, th / 2
        return [(x, a), (x + r, a + r), (x + r, b - r), (x, b), (x - r, b - r), (x - r, a + r)]
    p = {0: horiz(y0), 6: horiz(ym), 3: horiz(y1), 5: vert(x0, y0, ym), 1: vert(x1, y0, ym),
         4: vert(x0, ym, y1), 2: vert(x1, ym, y1)}[s]
    bottom = t + h
    return [(x + (bottom - y) * slant, y) for x, y in p]


def poly(pts):
    return "M" + "L".join(f"{x:.1f},{y:.1f}" for x, y in pts) + "Z"


def rrect(l, t, r, b, rad):
    return (f"M{l + rad:.1f},{t:.1f}H{r - rad:.1f}A{rad},{rad} 0 0 1 {r:.1f},{t + rad:.1f}"
            f"V{b - rad:.1f}A{rad},{rad} 0 0 1 {r - rad:.1f},{b:.1f}H{l + rad:.1f}"
            f"A{rad},{rad} 0 0 1 {l:.1f},{b - rad:.1f}V{t + rad:.1f}A{rad},{rad} 0 0 1 {l + rad:.1f},{t:.1f}Z")


def circle(cx, cy, r):
    return f"M{cx - r:.1f},{cy:.1f}A{r:.1f},{r:.1f} 0 1 0 {cx + r:.1f},{cy:.1f}A{r:.1f},{r:.1f} 0 1 0 {cx - r:.1f},{cy:.1f}Z"


def dot(cell_left):
    """Round decimal point after a cell (like Digits.dotY(): a little below the cell's bottom edge)."""
    cx = cell_left + CELL_W + (PITCH - CELL_W) * 0.45
    return circle(cx, TOP + CELL_H - DP_R * 0.35, DP_R)


def tick(cell_left):
    """Thousands ' between this cell and the next (like Lcd tickTop(): just above the cells)."""
    gx = cell_left + CELL_W + (PITCH - CELL_W) * 0.45
    top = TOP - CELL_H * 0.07
    cx = gx + (TOP + CELL_H - top) * SLANT
    ln, wt, wb = CELL_H * 0.2, TH * 0.32, TH * 0.15
    return poly([(cx - wt, top), (cx + wt, top), (cx + wb - ln * SLANT, top + ln), (cx - wb - ln * SLANT, top + ln)])


def label_mplus(cx, cy, h=8.6):
    """Bold geometric "M+" outline polygons, centred at cx,cy."""
    s = h / 8.0
    mw, gap, pw = 9.4 * s, 1.6 * s, 7.0 * s
    x0 = cx - (mw + gap + pw) / 2
    y0 = cy - h / 2
    m = [(0, 8), (0, 0), (2.4, 0), (4.7, 4.3), (7.0, 0), (9.4, 0), (9.4, 8), (7.3, 8), (7.3, 3.6),
         (5.5, 6.8), (3.9, 6.8), (2.1, 3.6), (2.1, 8)]
    M = [(x0 + x * s, y0 + y * s) for x, y in m]
    pcx, pcy, a, w = x0 + mw + gap + pw / 2, cy, pw / 2, 1.05 * s
    P = [(pcx - w, pcy - a), (pcx + w, pcy - a), (pcx + w, pcy - w), (pcx + a, pcy - w), (pcx + a, pcy + w),
         (pcx + w, pcy + w), (pcx + w, pcy + a), (pcx - w, pcy + a), (pcx - w, pcy + w), (pcx - a, pcy + w),
         (pcx - a, pcy - w), (pcx - w, pcy - w)]
    return poly(M) + poly(P)


def grad(c0, c1, y0, y1, x0=0, x1=0):
    return ("grad", c0, c1, x0, y0, x1, y1)


def layers():
    """Returns (bg, fg, mono): lists of (pathData, fill, evenOdd) where fill is '#AARRGGBB' or grad()."""
    bg = [("M0,0h108v108h-108z", grad(BODY_TOP, BODY_BOTTOM, 0, 108), False)]
    l, t, r, b, rad = LCD
    bz = (l - BZ_T, t - BZ_T, r + BZ_T, b + BZ_T)
    fg = []
    # body detail: soft contact shadow under the raised bezel, key wells
    fg.append((rrect(bz[0], bz[1] + 1.2, bz[2] + 0.8, bz[3] + 1.6, rad + BZ_T + 1.5), "#1A000000", False))
    for kl, kr in KEYS:
        fg.append((rrect(kl - 1.6, KEY_TOP_Y - 1.6, kr + 1.6, 140, KEY_R + 1.6), WELL, False))
    # keys: side wall, cap (glossy vertical gradient), rim highlight, label
    for kl, kr in KEYS:
        fg.append((rrect(kl, KEY_TOP_Y, kr, 140, KEY_R), KEY_WALL, False))
        fg.append((rrect(kl + 0.9, KEY_TOP_Y + 0.5, kr - 0.9, 140, KEY_R - 0.6), grad(KEY_TOP, KEY_BOTTOM, KEY_TOP_Y, KEY_TOP_Y + 30), False))
        fg.append((rrect(kl + 2.2, KEY_TOP_Y + 0.9, kr - 2.2, KEY_TOP_Y + 6.5, KEY_R - 2), grad("#30FFFFFF", "#00FFFFFF", KEY_TOP_Y + 0.9, KEY_TOP_Y + 6.5), False))
    fg.append((label_mplus((KEYS[1][0] + KEYS[1][1]) / 2 - 1.0, KEY_TOP_Y + 13.5, 7.6), LABEL, False))
    # bezel: edge line, dark frame, faint inner lip, glass
    fg.append((rrect(bz[0] - 0.6, bz[1] - 0.6, bz[2] + 0.6, bz[3] + 0.6, rad + BZ_T + 0.6), BEZEL_EDGE, False))
    fg.append((rrect(*bz, rad + BZ_T), grad(BEZEL_HI, BEZEL, bz[1], bz[3]), False))
    fg.append((rrect(l - 0.7, t - 0.7, r + 0.7, b + 0.7, rad + 0.7), "#FF0B0C0E", False))
    fg.append((rrect(*LCD), grad(LCD_TOP, LCD_BOTTOM, 20, b), False))
    ghosts, lit = [], []
    for cl in GHOST_CELLS:
        ghosts += [poly(segment(sg, cl, TOP, CELL_W, CELL_H, TH, GAP, SLANT)) for sg in range(7)]
    for cl, mask in LIT.items():
        for sg in range(7):
            (lit if mask & (1 << sg) else ghosts).append(poly(segment(sg, cl, TOP, CELL_W, CELL_H, TH, GAP, SLANT)))
    cells = list(GHOST_CELLS) + list(LIT)
    for cl in cells:
        ghosts.append(tick(cl))
        if cl != 31.0:                       # the lit point follows the "0"
            ghosts.append(dot(cl))
    lit.append(dot(31.0))
    fg.append(("".join(ghosts), GHOST, False))
    fg.append(("".join(lit), ON, False))
    # monochrome: bezel corner (frame minus glass), lit "0.8", M+ key cap with the label cut out
    kl, kr = KEYS[1]
    mono = [(rrect(*bz, rad + BZ_T) + rrect(*LCD), "#FFFFFFFF", True),
            ("".join(lit), "#FFFFFFFF", False),
            (rrect(kl, KEY_TOP_Y, kr, 140, KEY_R) + label_mplus((kl + kr) / 2 - 1.0, KEY_TOP_Y + 13.5, 7.6), "#FFFFFFFF", True)]
    return bg, fg, mono


def split(shapes, limit=700):
    """Lint (VectorPath) flags pathData over 800 chars: split multi-subpath entries into chunks."""
    out = []
    for d, fill, eo in shapes:
        if len(d) <= limit or eo:
            out.append((d, fill, eo))
            continue
        parts = ["M" + x for x in d.split("M") if x]
        cur = ""
        for part in parts:
            if cur and len(cur) + len(part) > limit:
                out.append((cur, fill, eo))
                cur = ""
            cur += part
        out.append((cur, fill, eo))
    return out


def vector_xml(shapes):
    shapes = split(shapes)
    out = ['<?xml version="1.0" encoding="utf-8"?>\n<!-- generated by tools/icon/make_icon.py -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"\n'
           '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n']
    for d, fill, eo in shapes:
        ft = ' android:fillType="evenOdd"' if eo else ""
        if isinstance(fill, tuple):
            _, c0, c1, x0, y0, x1, y1 = fill
            out.append(f'    <path android:pathData="{d}"{ft}><aapt:attr name="android:fillColor"><gradient '
                       f'android:type="linear" android:startX="{x0}" android:startY="{y0}" android:endX="{x1}" '
                       f'android:endY="{y1}" android:startColor="{c0}" android:endColor="{c1}"/></aapt:attr></path>\n')
        else:
            out.append(f'    <path android:fillColor="{fill}"{ft} android:pathData="{d}"/>\n')
    out.append('</vector>\n')
    return "".join(out)


def svg(shape_lists, clip=None, mono=None):
    """SVG of stacked layers (viewport 0..108), optional clip path 'circle'|'squircle'|'rounded'|None,
    showing only the visible 18..90 region when clipped (like a launcher)."""
    defs, body, gid = [], [], 0

    def col(c):
        c = c.lstrip("#")
        return f"#{c[2:]}", int(c[:2], 16) / 255

    for shapes in shape_lists:
        for d, fill, eo in shapes:
            rule = ' fill-rule="evenodd"' if eo else ""
            if mono:
                body.append(f'<path d="{d}" fill="{mono}"{rule}/>')
            elif isinstance(fill, tuple):
                _, c0, c1, x0, y0, x1, y1 = fill
                (h0, a0), (h1, a1) = col(c0), col(c1)
                gid += 1
                defs.append(f'<linearGradient id="g{gid}" gradientUnits="userSpaceOnUse" x1="{x0}" y1="{y0}" x2="{x1}" y2="{y1}">'
                            f'<stop offset="0" stop-color="{h0}" stop-opacity="{a0:.3f}"/><stop offset="1" stop-color="{h1}" stop-opacity="{a1:.3f}"/></linearGradient>')
                body.append(f'<path d="{d}" fill="url(#g{gid})"{rule}/>')
            else:
                h, a = col(fill)
                body.append(f'<path d="{d}" fill="{h}" fill-opacity="{a:.3f}"{rule}/>')
    vb = "0 0 108 108"
    clip_el, clip_attr = "", ""
    if clip:
        vb = "18 18 72 72"
        if clip == "circle":
            shape = '<circle cx="54" cy="54" r="36"/>'
        elif clip == "rounded":
            shape = '<rect x="18" y="18" width="72" height="72" rx="17"/>'
        else:   # superellipse n=4 (squircle)
            pts = []
            for i in range(360):
                a = 2 * math.pi * i / 360
                c, s = math.cos(a), math.sin(a)
                pts.append(f"{54 + 36 * math.copysign(abs(c) ** 0.5, c):.1f},{54 + 36 * math.copysign(abs(s) ** 0.5, s):.1f}")
            shape = f'<polygon points="{" ".join(pts)}"/>'
        clip_el = f'<clipPath id="m">{shape}</clipPath>'
        clip_attr = ' clip-path="url(#m)"'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}" width="512" height="512">'
            f'<defs>{"".join(defs)}{clip_el}</defs><g{clip_attr}>{"".join(body)}</g></svg>')


def write_xml():
    bg, fg, mono = layers()
    os.makedirs(RES, exist_ok=True)
    for name, shapes in (("ic_bg.xml", bg), ("ic_fg.xml", fg), ("ic_mono.xml", mono)):
        with open(os.path.join(RES, name), "w") as f:
            f.write(vector_xml(shapes))


def preview(path):
    import io
    import cairosvg
    from PIL import Image, ImageDraw
    bg, fg, mono = layers()

    def png(svgtext, size):
        return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svgtext.encode(), output_width=size, output_height=size))).convert("RGBA")

    N = 512
    full = png(svg([bg, fg]), N)
    # safe-zone guide on the full-bleed view
    guide = full.copy()
    g = ImageDraw.Draw(guide)
    k = N / 108
    g.ellipse([21 * k, 21 * k, 87 * k, 87 * k], outline=(255, 0, 160, 255), width=2)
    g.rectangle([18 * k, 18 * k, 90 * k, 90 * k], outline=(0, 120, 255, 255), width=1)
    circ, sq, rnd = (png(svg([bg, fg], clip=c), N) for c in ("circle", "squircle", "rounded"))
    themed = png(svg([[("M0,0h108v108h-108z", "#FFD8E3F0", False)], ], clip="circle"), N)
    themed.alpha_composite(png(svg([mono], clip="circle", mono="#1F3B5C"), N))
    W = 4 * N + 100
    sheet = Image.new("RGBA", (W, N + 300), (238, 238, 240, 255))
    for i, im in enumerate((guide, circ, sq, themed)):
        sheet.alpha_composite(im, (20 + i * (N + 20), 20))
    strip_y = N + 40
    ImageDraw.Draw(sheet).rectangle([0, strip_y, W, N + 300], fill=(52, 70, 92, 255))
    x = 40
    for size in (192, 144, 96, 72, 48, 36):
        sheet.alpha_composite(circ.resize((size, size), Image.LANCZOS), (x, strip_y + 24 + (192 - size) // 2))
        x += size + 30
    for size in (96, 48):
        sheet.alpha_composite(rnd.resize((size, size), Image.LANCZOS), (x, strip_y + 24 + (192 - size) // 2))
        x += size + 30
    sheet.convert("RGB").save(path)
    png(svg([bg, fg]), N).convert("RGB").save(os.path.splitext(path)[0] + "-512.png")
    print(path)


write_xml()
if len(sys.argv) > 1:
    preview(sys.argv[1])
