#!/usr/bin/env python3
"""Generates the NEXUS icon atlas (white line glyphs on transparency) and the mod icon.

Usage: python3 tools/gen_icons.py   (run from the project root; requires Pillow)
Order must match dev.nexus.client.render.Icon.
"""
import math
import os
from PIL import Image, ImageDraw, ImageChops

S = 128             # drawing resolution per glyph (downsampled to GLYPH)
GLYPH, CELL, COLS = 32, 40, 8
W = 11              # stroke width at drawing resolution
WHITE = (255, 255, 255, 255)

ORDER = ["LOGO", "HOME", "MODULES", "HUD", "PROFILES", "SETTINGS", "INFO", "SEARCH",
         "EYE", "MONITOR", "LAYOUT", "WRENCH", "BOLT", "CLOSE", "BACK", "CHEVRON_RIGHT",
         "CHEVRON_DOWN", "CHECK", "PLUS", "TRASH", "EDIT", "REFRESH", "KEYBOARD", "SUN",
         "MOON", "POWER", "RESET", "COPY", "GRID", "MAGNET", "FOLDER", "MOVE"]


def canvas():
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def line(d, pts, w=W):
    d.line(pts, fill=WHITE, width=w, joint="curve")
    for p in (pts[0], pts[-1]):  # round caps
        d.ellipse([p[0] - w / 2, p[1] - w / 2, p[0] + w / 2, p[1] + w / 2], fill=WHITE)


def rrect(d, box, r, w=W, fill=False):
    if fill:
        d.rounded_rectangle(box, r, fill=WHITE)
    else:
        d.rounded_rectangle(box, r, outline=WHITE, width=w)


def circle(d, cx, cy, r, w=W, fill=False):
    box = [cx - r, cy - r, cx + r, cy + r]
    if fill:
        d.ellipse(box, fill=WHITE)
    else:
        d.ellipse(box, outline=WHITE, width=w)


def arc(d, cx, cy, r, start, end, w=W):
    d.arc([cx - r, cy - r, cx + r, cy + r], start, end, fill=WHITE, width=w)
    for a in (start, end):
        x = cx + (r - w / 2) * math.cos(math.radians(a))
        y = cy + (r - w / 2) * math.sin(math.radians(a))
        d.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=WHITE)


def poly(d, pts):
    d.polygon(pts, fill=WHITE)


def g_logo(d):
    cx, cy, r = 64, 64, 52
    pts = [(cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a))) for a in range(-90, 270, 60)]
    d.polygon(pts, outline=WHITE, width=W)
    for p in pts:  # smooth the corners
        d.ellipse([p[0] - W / 2, p[1] - W / 2, p[0] + W / 2, p[1] + W / 2], fill=WHITE)
    for i in (0, 2, 4):
        line(d, [(cx, cy), pts[i]], 9)
    circle(d, cx, cy, 13, fill=True)


def g_home(d):
    line(d, [(18, 62), (64, 20), (110, 62)])
    d.line([(32, 54), (32, 108), (96, 108), (96, 54)], fill=WHITE, width=W, joint="curve")
    rrect(d, [54, 76, 74, 108], 4, fill=True)


def g_modules(d):
    for x in (16, 70):
        for y in (16, 70):
            rrect(d, [x, y, x + 42, y + 42], 10)


def g_hud(d):
    rrect(d, [10, 22, 118, 106], 12)
    rrect(d, [24, 36, 52, 50], 4, fill=True)
    rrect(d, [76, 36, 104, 50], 4, fill=True)
    rrect(d, [24, 78, 70, 92], 4, fill=True)


def g_profiles(d):
    circle(d, 64, 42, 22)
    arc(d, 64, 118, 44, 200, 340)


def g_settings(d):
    img_cx = 64
    teeth = 8
    for i in range(teeth):
        a = math.radians(i * 360 / teeth)
        x1, y1 = img_cx + 38 * math.cos(a), img_cx + 38 * math.sin(a)
        x2, y2 = img_cx + 54 * math.cos(a), img_cx + 54 * math.sin(a)
        d.line([(x1, y1), (x2, y2)], fill=WHITE, width=18)
    circle(d, 64, 64, 40, fill=True)
    d.ellipse([64 - 16, 64 - 16, 64 + 16, 64 + 16], fill=(0, 0, 0, 0))


def g_info(d):
    circle(d, 64, 64, 52)
    circle(d, 64, 40, 8, fill=True)
    line(d, [(64, 60), (64, 92)])


def g_search(d):
    circle(d, 54, 54, 34)
    line(d, [(80, 80), (108, 108)], 13)


def g_eye(d):
    # almond outline from two circular arcs
    arc(d, 64, 120, 80, 222, 318)
    arc(d, 64, 8, 80, 42, 138)
    circle(d, 64, 64, 17, fill=True)


def g_monitor(d):
    rrect(d, [12, 18, 116, 88], 10)
    line(d, [(64, 92), (64, 106)])
    line(d, [(40, 110), (88, 110)])


def g_layout(d):
    rrect(d, [14, 14, 114, 114], 12)
    line(d, [(20, 46), (108, 46)])
    line(d, [(52, 50), (52, 108)])


def g_wrench(d):
    line(d, [(26, 102), (66, 62)], 16)
    arc(d, 82, 46, 30, 100, 350, 14)


def g_bolt(d):
    poly(d, [(72, 8), (26, 72), (60, 72), (52, 120), (102, 52), (68, 52), (80, 8)])


def g_close(d):
    line(d, [(30, 30), (98, 98)], 13)
    line(d, [(98, 30), (30, 98)], 13)


def g_back(d):
    line(d, [(24, 64), (104, 64)], 12)
    line(d, [(58, 30), (24, 64), (58, 98)], 12)


def g_chevron_right(d):
    line(d, [(46, 22), (88, 64), (46, 106)], 14)


def g_chevron_down(d):
    line(d, [(22, 46), (64, 88), (106, 46)], 14)


def g_check(d):
    line(d, [(20, 66), (50, 96), (108, 34)], 14)


def g_plus(d):
    line(d, [(64, 22), (64, 106)], 13)
    line(d, [(22, 64), (106, 64)], 13)


def g_trash(d):
    line(d, [(18, 32), (110, 32)])
    d.line([(48, 30), (50, 16), (78, 16), (80, 30)], fill=WHITE, width=9, joint="curve")
    d.line([(30, 38), (38, 112), (90, 112), (98, 38)], fill=WHITE, width=W, joint="curve")
    line(d, [(54, 52), (54, 96)], 8)
    line(d, [(74, 52), (74, 96)], 8)


def g_edit(d):
    poly(d, [(84, 14), (114, 44), (50, 108), (20, 78)])
    poly(d, [(18, 84), (44, 110), (12, 116)])
    d.line([(76, 26), (102, 52)], fill=(0, 0, 0, 0), width=6)


def _arrow_head(d, x, y, angle_deg, size=24):
    a = math.radians(angle_deg)
    left = (x + size * math.cos(a + 2.5), y + size * math.sin(a + 2.5))
    right = (x + size * math.cos(a - 2.5), y + size * math.sin(a - 2.5))
    tip = (x + size * 0.9 * math.cos(a), y + size * 0.9 * math.sin(a))
    poly(d, [tip, left, right])


def g_refresh(d):
    arc(d, 64, 64, 46, 20, 300)
    x = 64 + 40 * math.cos(math.radians(300))
    y = 64 + 40 * math.sin(math.radians(300))
    _arrow_head(d, x, y, 30)


def g_keyboard(d):
    rrect(d, [8, 30, 120, 100], 12)
    for row, y in enumerate((46, 62)):
        for i in range(6):
            x = 24 + i * 16 + (row * 6)
            if x > 104:
                continue
            d.rounded_rectangle([x - 5, y - 5, x + 5, y + 5], 2, fill=WHITE)
    rrect(d, [36, 76, 92, 86], 4, fill=True)


def g_sun(d):
    circle(d, 64, 64, 22)
    for i in range(8):
        a = math.radians(i * 45)
        line(d, [(64 + 38 * math.cos(a), 64 + 38 * math.sin(a)), (64 + 52 * math.cos(a), 64 + 52 * math.sin(a))], 10)


def g_moon(d):
    img = Image.new("L", (S, S), 0)
    m = ImageDraw.Draw(img)
    m.ellipse([16, 16, 112, 112], fill=255)
    m.ellipse([46, 4, 128, 86], fill=0)
    d._image.paste(WHITE, (0, 0), img)


def g_power(d):
    arc(d, 64, 68, 44, -50, 230)
    line(d, [(64, 14), (64, 60)])


def g_reset(d):
    arc(d, 64, 64, 46, 240, 520 - 360 + 360)  # 240 → 160 (through 0)
    x = 64 + 40 * math.cos(math.radians(240))
    y = 64 + 40 * math.sin(math.radians(240))
    _arrow_head(d, x, y, 150)


def g_copy(d):
    rrect(d, [40, 40, 112, 112], 12)
    d.line([(26, 88), (16, 88), (16, 16), (88, 16), (88, 26)], fill=WHITE, width=W, joint="curve")


def g_grid(d):
    rrect(d, [14, 14, 114, 114], 10)
    for p in (47, 81):
        line(d, [(p, 20), (p, 108)], 8)
        line(d, [(20, p), (108, p)], 8)


def g_magnet(d):
    d.arc([22, 30, 106, 114], 0, 180, fill=WHITE, width=24)
    d.rectangle([22, 18, 46, 72], fill=WHITE)
    d.rectangle([82, 18, 106, 72], fill=WHITE)
    d.rectangle([22, 18, 46, 32], fill=(0, 0, 0, 0))
    d.rectangle([82, 18, 106, 32], fill=(0, 0, 0, 0))
    d.rectangle([22, 10, 46, 26], fill=WHITE)
    d.rectangle([82, 10, 106, 26], fill=WHITE)
    d.rectangle([22, 28, 46, 32], fill=(0, 0, 0, 0))
    d.rectangle([82, 28, 106, 32], fill=(0, 0, 0, 0))


def g_folder(d):
    d.line([(14, 100), (14, 26), (50, 26), (60, 38), (114, 38), (114, 100), (14, 100)], fill=WHITE, width=W, joint="curve")
    line(d, [(20, 54), (108, 54)], 8)


def g_move(d):
    line(d, [(64, 14), (64, 114)], 10)
    line(d, [(14, 64), (114, 64)], 10)
    for ang, (x, y) in ((-90, (64, 14)), (90, (64, 114)), (180, (14, 64)), (0, (114, 64))):
        _arrow_head(d, x - 8 * math.cos(math.radians(ang)), y - 8 * math.sin(math.radians(ang)), ang, 22)


def glyph(name):
    img, d = canvas()
    d._image = img
    globals()["g_" + name.lower()](d)
    return img.resize((GLYPH, GLYPH), Image.LANCZOS)


def main():
    root = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "nexus")
    tex = os.path.join(root, "textures", "gui")
    os.makedirs(tex, exist_ok=True)
    rows = (len(ORDER) + COLS - 1) // COLS
    atlas = Image.new("RGBA", (COLS * CELL, rows * CELL), (0, 0, 0, 0))
    pad = (CELL - GLYPH) // 2
    for i, name in enumerate(ORDER):
        g = glyph(name)
        # force pure white RGB, keep only coverage in alpha (tinted by vertex colour at runtime)
        a = g.getchannel("A")
        white = Image.new("RGBA", g.size, WHITE)
        white.putalpha(a)
        atlas.paste(white, ((i % COLS) * CELL + pad, (i // COLS) * CELL + pad))
    atlas.save(os.path.join(tex, "icons.png"), optimize=True)
    with open(os.path.join(tex, "icons.png.mcmeta"), "w") as f:
        f.write('{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n')

    # mod icon: violet → cyan rounded tile with the logo
    size = 512
    grad = Image.new("RGBA", (size, size))
    c1, c2 = (139, 92, 246), (56, 189, 248)
    px = grad.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * (size - 1))
            px[x, y] = tuple(int(c1[k] + (c2[k] - c1[k]) * t) for k in range(3)) + (255,)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], 110, fill=255)
    icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    icon.paste(grad, (0, 0), mask)
    logo_img, d = canvas()
    d._image = logo_img
    g_logo(d)
    logo = logo_img.resize((300, 300), Image.LANCZOS)
    icon.alpha_composite(logo, ((size - 300) // 2, (size - 300) // 2))
    icon.resize((128, 128), Image.LANCZOS).save(os.path.join(root, "icon.png"), optimize=True)
    # preview sheet (not shipped): docs/icons.png
    prev = Image.new("RGBA", atlas.size, (21, 22, 28, 255))
    prev.alpha_composite(atlas)
    prev.resize((atlas.width * 2, atlas.height * 2), Image.NEAREST).save(os.path.join(os.path.dirname(__file__), "..", "docs", "icons.png"))
    print("wrote", os.path.join(tex, "icons.png"))


if __name__ == "__main__":
    main()
