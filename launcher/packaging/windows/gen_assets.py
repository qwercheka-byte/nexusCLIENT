#!/usr/bin/env python3
"""Generates the Windows assets of NEXUS Launcher from the vector logo (same shape as Icons.LOGO):

  nexus.ico            - exe / shortcut / installer icon (16..256 px)
  installer-side.bmp   - 164x314 picture of the installer welcome/finish pages
  installer-header.bmp - 150x57 picture in the installer page header

Usage: python3 packaging/windows/gen_assets.py   (requires Pillow)
"""
import math
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
VIOLET, CYAN = (139, 92, 246), (56, 189, 248)       # Theme.ACCENTS["violet"]
BG_TOP, BG_BOTTOM = (19, 17, 31), (11, 11, 18)


def hexagon(cx, cy, r):
    # pointy-top hexagon, like the 24x24 path: (12,1.5) (21.1,6.75) (21.1,17.25) (12,22.5) (2.9,17.25) (2.9,6.75)
    return [(cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a))) for a in (-90, -30, 30, 90, 150, 210)]


def gradient(size, c0, c1, diagonal=True):
    w, h = size
    g = Image.new("RGBA", size)
    px = g.load()
    for y in range(h):
        for x in range(w):
            t = ((x / max(w - 1, 1)) + (y / max(h - 1, 1))) / 2 if diagonal else y / max(h - 1, 1)
            px[x, y] = tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(3)) + (255,)
    return g


def logo(size):
    """Hexagon ring with the violet->cyan gradient on transparency, supersampled."""
    ss = 8
    S = size * ss
    mask = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(mask)
    c = S / 2
    d.polygon(hexagon(c, c, S * 10.5 / 24), fill=255)
    d.polygon(hexagon(c, c, S * 5.6 / 24), fill=0)
    mask = mask.resize((size, size), Image.LANCZOS)
    img = gradient((size, size), VIOLET, CYAN)
    img.putalpha(mask)
    return img


def font(px, bold=True):
    for name in ("DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf",):
        for d in ("/usr/share/fonts/truetype/dejavu", "/usr/share/fonts/dejavu"):
            p = os.path.join(d, name)
            if os.path.exists(p):
                return ImageFont.truetype(p, px)
    return ImageFont.load_default()


def glow(base, center, radius, color, alpha):
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    px = layer.load()
    cx, cy = center
    for y in range(base.size[1]):
        for x in range(base.size[0]):
            dist = math.hypot(x - cx, y - cy) / radius
            if dist < 1:
                px[x, y] = color + (int(alpha * (1 - dist) ** 2),)
    return Image.alpha_composite(base, layer)


def main():
    sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256]
    big = logo(256)
    big.save(os.path.join(HERE, "nexus.ico"), sizes=[(s, s) for s in sizes],
             append_images=[logo(s) for s in sizes if s != 256])

    # welcome / finish page picture
    side = gradient((164, 314), BG_TOP, BG_BOTTOM, diagonal=False)
    side = glow(side, (82, 120), 120, VIOLET, 90)
    side = glow(side, (140, 290), 90, CYAN, 45)
    lg = logo(84)
    side.alpha_composite(lg, (40, 70))
    d = ImageDraw.Draw(side)
    f = font(22)
    w = d.textlength("NEXUS", font=f)
    d.text(((164 - w) / 2, 172), "NEXUS", font=f, fill=(240, 240, 250))
    f2 = font(11, bold=False)
    w2 = d.textlength("LAUNCHER", font=f2)
    d.text(((164 - w2) / 2, 202), "LAUNCHER", font=f2, fill=(160, 160, 190))
    d.line([(52, 224), (112, 224)], fill=VIOLET, width=2)
    side.convert("RGB").save(os.path.join(HERE, "installer-side.bmp"))

    # header picture (shown on the right of the white header)
    head = Image.new("RGBA", (150, 57), (255, 255, 255, 255))
    head.alpha_composite(logo(40), (100, 8))
    head.convert("RGB").save(os.path.join(HERE, "installer-header.bmp"))


if __name__ == "__main__":
    main()
