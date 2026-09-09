"""Paint per-skin garment overlays onto transparent canvases the same size as each mascot PNG.

Each overlay is registered to that skin's artwork: transparent everywhere except the garment,
so Mascot.kt can mesh-deform it with the body. Run from LullabyAI/: python tools/gen_wardrobe_overlays.py
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
NODPI = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"

# Face centres from MascotArt — garments hang off these, not invented pixels.
FACES = {
    "nongrak": dict(lx=0.407, ly=0.476, rx=0.603, ry=0.445, mx=0.513, my=0.528),
    "bunny": dict(lx=0.426, ly=0.495, rx=0.623, ry=0.457, mx=0.520, my=0.565),
    "coco": dict(lx=0.388, ly=0.332, rx=0.610, ry=0.345, mx=0.480, my=0.491),
    "pingu": dict(lx=0.407, ly=0.373, rx=0.612, ry=0.394, mx=0.502, my=0.443),
}

FALLBACK_SIZE = {"nongrak": 715, "bunny": 680, "coco": 577, "pingu": 620}


def canvas_size(skin: str) -> tuple[int, int]:
    path = NODPI / f"mascot_{skin}.png"
    if path.exists():
        with Image.open(path) as im:
            return im.size
    s = FALLBACK_SIZE[skin]
    return s, s


def ellipse(draw: ImageDraw.ImageDraw, cx, cy, rx, ry, fill, outline=None, width=1):
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline, width=width)


def star(draw: ImageDraw.ImageDraw, cx, cy, r, fill):
    pts = []
    for i in range(10):
        ang = -math.pi / 2 + i * math.pi / 5
        rad = r if i % 2 == 0 else r * 0.42
        pts.append((cx + rad * math.cos(ang), cy + rad * math.sin(ang)))
    draw.polygon(pts, fill=fill)


def paint_bow(draw, w, h, f):
    cx = (f["lx"] + f["rx"]) / 2 * w
    cy = f["my"] * h + 0.06 * h
    pink, knot = (255, 126, 182, 235), (232, 72, 140, 255)
    ellipse(draw, cx - 0.055 * w, cy, 0.055 * w, 0.038 * h, pink)
    ellipse(draw, cx + 0.055 * w, cy, 0.055 * w, 0.038 * h, pink)
    ellipse(draw, cx, cy, 0.022 * w, 0.022 * h, knot)


def paint_scarf(draw, w, h, f):
    cy = f["my"] * h + 0.10 * h
    blue, stripe = (59, 130, 246, 230), (255, 255, 255, 210)
    draw.rounded_rectangle(
        [0.28 * w, cy - 0.035 * h, 0.72 * w, cy + 0.035 * h],
        radius=0.03 * w, fill=blue,
    )
    # Dangling end on the trail side (left for NongRak's cape, otherwise right-ish).
    draw.rounded_rectangle(
        [0.62 * w, cy, 0.70 * w, cy + 0.16 * h],
        radius=0.02 * w, fill=blue,
    )
    draw.line([(0.30 * w, cy), (0.70 * w, cy)], fill=stripe, width=max(2, int(0.008 * h)))


def paint_flower(draw, w, h, f):
    cx = (f["lx"] + f["rx"]) / 2 * w
    cy = min(f["ly"], f["ry"]) * h - 0.16 * h
    petal, center = (244, 114, 182, 235), (251, 191, 36, 255)
    for i in range(5):
        ang = -math.pi / 2 + i * 2 * math.pi / 5
        ellipse(
            draw,
            cx + 0.045 * w * math.cos(ang),
            cy + 0.045 * h * math.sin(ang),
            0.032 * w, 0.032 * h, petal,
        )
    ellipse(draw, cx, cy, 0.022 * w, 0.022 * h, center)


def paint_star(draw, w, h, f):
    cx = (f["lx"] + f["rx"]) / 2 * w
    cy = f["my"] * h + 0.14 * h
    star(draw, cx, cy, 0.055 * min(w, h), (245, 158, 11, 245))


def paint_party(draw, w, h, f):
    top = f["my"] * h + 0.08 * h
    shirt = (167, 139, 250, 220)
    draw.rounded_rectangle(
        [0.30 * w, top, 0.70 * w, top + 0.28 * h],
        radius=0.04 * w, fill=shirt,
    )
    # Sleeve nubs so it reads as a shirt, not a rectangle.
    ellipse(draw, 0.30 * w, top + 0.06 * h, 0.05 * w, 0.04 * h, shirt)
    ellipse(draw, 0.70 * w, top + 0.06 * h, 0.05 * w, 0.04 * h, shirt)
    for dx, dy in ((-0.06, 0.10), (0.0, 0.16), (0.07, 0.11)):
        ellipse(draw, (0.50 + dx) * w, top + dy * h, 0.012 * w, 0.012 * h, (255, 255, 255, 230))


def paint_crown(draw, w, h, f):
    cx = (f["lx"] + f["rx"]) / 2 * w
    cy = min(f["ly"], f["ry"]) * h - 0.18 * h
    gold, jewel = (251, 191, 36, 245), (239, 68, 68, 255)
    band_h = 0.035 * h
    draw.rounded_rectangle(
        [cx - 0.11 * w, cy, cx + 0.11 * w, cy + band_h],
        radius=0.01 * w, fill=gold,
    )
    for i, dx in enumerate((-0.08, 0.0, 0.08)):
        peak = 0.09 * h if i == 1 else 0.06 * h
        x = cx + dx * w
        draw.polygon(
            [(x - 0.03 * w, cy), (x, cy - peak), (x + 0.03 * w, cy)],
            fill=gold,
        )
    ellipse(draw, cx, cy - 0.02 * h, 0.012 * w, 0.012 * h, jewel)


PAINTERS = {
    "bow": paint_bow,
    "scarf": paint_scarf,
    "flower": paint_flower,
    "star": paint_star,
    "party": paint_party,
    "crown": paint_crown,
}


def main():
    NODPI.mkdir(parents=True, exist_ok=True)
    written = 0
    for skin, face in FACES.items():
        w, h = canvas_size(skin)
        for item, painter in PAINTERS.items():
            im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
            painter(ImageDraw.Draw(im, "RGBA"), w, h, face)
            out = NODPI / f"wear_{item}_{skin}.png"
            im.save(out, "PNG")
            written += 1
            print(f"{out.name}  {w}x{h}")
    print(f"wrote {written} overlays")


if __name__ == "__main__":
    main()
