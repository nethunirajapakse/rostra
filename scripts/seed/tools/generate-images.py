#!/usr/bin/env python3
"""Draws the illustrations in scripts/seed/images/ (two per auction in auctions.txt).

They are simple vector-style pictures, not photographs, so the demo needs no copyrighted images.
Re-run only if you change the items:   python3 scripts/seed/tools/generate-images.py   (needs Pillow)
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent.parent / "images"
W, H, SS = 800, 600, 2  # final size, supersampling factor
INK, PAPER = (27, 36, 48), (246, 244, 238)

def rgb(h): return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))

def gradient(top, bottom):
    img = Image.new("RGB", (W * SS, H * SS))
    px = ImageDraw.Draw(img)
    for y in range(H * SS):
        t = y / (H * SS - 1)
        px.line([(0, y), (W * SS, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return img

class Pen:
    """Draws in a 100-units-per-100px coordinate space around (cx, cy), scaled by s."""
    def __init__(self, d, cx, cy, s): self.d, self.cx, self.cy, self.s = d, cx * SS, cy * SS, s * SS
    def p(self, x, y): return (self.cx + x * self.s, self.cy + y * self.s)
    def box(self, x0, y0, x1, y1): return [*self.p(x0, y0), *self.p(x1, y1)]
    def rect(self, x0, y0, x1, y1, fill, r=0):
        (self.d.rounded_rectangle(self.box(x0, y0, x1, y1), radius=r * self.s, fill=fill) if r else self.d.rectangle(self.box(x0, y0, x1, y1), fill=fill))
    def ell(self, x0, y0, x1, y1, fill=None, outline=None, w=0): self.d.ellipse(self.box(x0, y0, x1, y1), fill=fill, outline=outline, width=round(w * self.s))
    def circ(self, x, y, r, fill=None, outline=None, w=0): self.ell(x - r, y - r, x + r, y + r, fill, outline, w)
    def line(self, pts, fill, w): self.d.line([self.p(*q) for q in pts], fill=fill, width=max(1, round(w * self.s)), joint="curve")
    def poly(self, pts, fill): self.d.polygon([self.p(*q) for q in pts], fill=fill)
    def arc(self, x0, y0, x1, y1, a0, a1, fill, w): self.d.arc(self.box(x0, y0, x1, y1), a0, a1, fill=fill, width=round(w * self.s))

# ---- one drawing per item; each draws around (0, 0) in a roughly 4 x 3 unit box ----
def camera(p, c):
    p.rect(-1.9, -0.8, 1.9, 0.9, c[0], 0.12); p.rect(-1.9, -0.8, 1.9, -0.45, c[1], 0.1)
    p.rect(-1.5, -1.1, -0.5, -0.8, c[1], 0.05); p.rect(0.3, -1.05, 0.9, -0.8, c[1], 0.05)
    p.rect(-1.65, -0.65, -1.2, -0.5, PAPER, 0.04); p.circ(1.4, -1.0, 0.18, c[1])
    p.circ(0.0, 0.1, 0.75, INK); p.circ(0.0, 0.1, 0.6, (60, 70, 85)); p.circ(0.0, 0.1, 0.38, INK); p.circ(-0.12, -0.02, 0.1, (150, 170, 190))
    p.rect(-1.9, 0.35, -1.55, 0.9, INK, 0.05)
def watch(p, c):
    p.rect(-0.55, -1.6, 0.55, 1.6, INK, 0.2); p.rect(-0.5, -1.55, 0.5, -0.9, c[1], 0.15); p.rect(-0.5, 0.9, 0.5, 1.55, c[1], 0.15)
    p.circ(0, 0, 1.15, c[0]); p.circ(0, 0, 1.0, PAPER)
    for i in range(12):
        a = i * math.pi / 6; p.line([(0.8 * math.sin(a), -0.8 * math.cos(a)), (0.93 * math.sin(a), -0.93 * math.cos(a))], INK, 0.05)
    p.line([(0, 0), (0.35, -0.35)], INK, 0.08); p.line([(0, 0), (0, -0.7)], INK, 0.06); p.circ(0, 0, 0.07, c[1])
def keyboard(p, c):
    p.rect(-1.95, -0.95, 1.95, 0.95, c[0], 0.15); p.rect(-1.8, -0.8, 1.8, 0.8, c[1], 0.08)
    for r in range(4):
        for k in range(12 if r < 3 else 8):
            x0 = -1.7 + k * 0.29 + (0.0 if r < 3 else 0.5); y0 = -0.7 + r * 0.36
            p.rect(x0, y0, x0 + 0.25, y0 + 0.29, PAPER, 0.04)
    p.rect(-0.9, 0.38, 0.9, 0.67, PAPER, 0.04)
def teaset(p, c):
    p.ell(-1.0, -0.2, 0.6, 1.0, c[0]); p.rect(-0.55, -0.45, 0.15, -0.15, c[0], 0.1); p.circ(-0.2, -0.55, 0.1, c[1])
    p.poly([(0.5, 0.15), (1.15, -0.35), (1.25, -0.28), (0.6, 0.45)], c[0]); p.arc(-1.7, -0.1, -0.8, 0.8, 90, 270, c[0], 0.12)
    p.ell(-0.95, 0.15, 0.55, 0.3, fill=c[1])
    for x in (1.0,):
        p.ell(x - 0.5, 0.95, x + 0.6, 1.1, c[1]); p.rect(x - 0.3, 0.55, x + 0.4, 1.02, c[0], 0.1); p.arc(x + 0.3, 0.6, x + 0.65, 0.9, -90, 90, c[0], 0.07)
    p.ell(-1.6, 1.0, -0.2, 1.15, c[1]); p.rect(-1.35, 0.6, -0.45, 1.05, c[0], 0.12)
def mat(p, c):
    p.rect(-1.6, -1.25, 1.6, -1.1, INK, 0.05); p.rect(-1.4, -1.1, 1.4, 1.3, c[0])
    for i in range(8):
        y = -1.0 + i * 0.28; p.rect(-1.4, y, 1.4, y + 0.1, c[1] if i % 2 else PAPER)
    for k in range(3):
        x = -0.8 + k * 0.8; p.poly([(x, -0.5), (x + 0.35, 0.0), (x, 0.5), (x - 0.35, 0.0)], INK); p.poly([(x, -0.25), (x + 0.17, 0.0), (x, 0.25), (x - 0.17, 0.0)], PAPER)
    for k in range(15): p.line([(-1.4 + k * 0.2, 1.3), (-1.4 + k * 0.2, 1.55)], c[1], 0.04)
def bike(p, c):
    for x in (-1.2, 1.2): p.circ(x, 0.55, 0.85, outline=INK, w=0.09); p.circ(x, 0.55, 0.08, INK)
    p.line([(-1.2, 0.55), (-0.1, 0.55), (0.35, -0.55), (-0.5, -0.55), (-1.2, 0.55)], c[0], 0.11)
    p.line([(0.35, -0.55), (1.2, 0.55)], c[0], 0.11); p.line([(0.35, -0.55), (0.5, -1.0), (0.2, -1.1)], INK, 0.08); p.line([(-0.5, -0.55), (-0.55, -0.85)], INK, 0.08)
    p.rect(-0.9, -0.95, -0.2, -0.8, INK, 0.07); p.circ(-0.1, 0.55, 0.22, outline=INK, w=0.06)
def vinyl(p, c):
    p.circ(0.55, 0, 1.15, INK); p.circ(0.55, 0, 0.4, c[1]); p.circ(0.55, 0, 0.05, PAPER)
    for r in (0.6, 0.78, 0.96): p.circ(0.55, 0, r, outline=(60, 70, 85), w=0.02)
    p.rect(-1.9, -1.15, 0.55, 1.15, c[0]); p.rect(-1.65, -0.9, 0.3, 0.2, PAPER); p.circ(-0.67, -0.35, 0.4, c[1]); p.rect(-1.65, 0.45, 0.0, 0.58, INK); p.rect(-1.65, 0.7, -0.5, 0.8, INK)
def guitar(p, c):
    p.line([(-0.2, 0.0), (1.9, -1.15)], INK, 0.22); p.poly([(1.8, -1.35), (2.25, -1.55), (2.35, -1.2), (1.9, -1.0)], INK)
    p.ell(-1.9, -1.05, -0.2, 1.05, c[0]); p.ell(-1.0, -0.65, 0.35, 0.65, c[0]); p.circ(-0.55, 0, 0.32, INK); p.circ(-0.55, 0, 0.42, outline=c[1], w=0.05)
    p.rect(-1.25, 0.55, -0.35, 0.68, INK, 0.04)
def lamp(p, c):
    p.ell(-1.0, 1.1, 0.4, 1.4, c[0]); p.line([(-0.3, 1.2), (-0.1, 0.0), (0.9, -0.55)], c[0], 0.1); p.circ(-0.1, 0.0, 0.09, INK)
    p.poly([(0.55, -0.9), (1.7, -0.5), (1.9, 0.15), (0.7, -0.05)], c[1]); p.circ(1.25, 0.1, 0.1, (255, 233, 160))
def polaroid(p, c):
    p.rect(-1.55, -1.1, 1.55, 1.1, PAPER, 0.2); p.rect(-1.55, -1.1, 1.55, -0.55, c[1], 0.2); p.rect(-1.55, -0.75, 1.55, -0.55, c[1])
    p.circ(0.0, 0.1, 0.72, INK); p.circ(0.0, 0.1, 0.55, (60, 70, 85)); p.circ(-0.12, -0.02, 0.1, (150, 170, 190))
    p.rect(0.85, -0.95, 1.4, -0.7, INK, 0.05); p.rect(-1.0, 0.95, 1.0, 1.02, INK)
    for i, col in enumerate(((220, 80, 60), (240, 170, 50), (80, 160, 90), (60, 110, 190))): p.rect(-1.5 + i * 0.1, -0.45, -1.42 + i * 0.1, 0.1, col)
def flyrod(p, c):
    p.line([(-2.0, 1.2), (2.0, -1.2)], c[0], 0.07); p.line([(-2.0, 1.2), (-1.35, 0.8)], INK, 0.2)
    p.circ(-1.0, 0.95, 0.38, INK); p.circ(-1.0, 0.95, 0.25, c[1]); p.circ(-1.0, 0.95, 0.05, INK)
    for t in (-0.6, 0.0, 0.7, 1.4): p.circ(t * 1.0, 0.45 - t * 0.6, 0.06, outline=INK, w=0.03)
    p.arc(0.8, -2.0, 2.4, -0.6, 200, 300, c[1], 0.02)
def chess(p, c):
    for r in range(4):
        for k in range(8): p.rect(-1.9 + k * 0.475, 0.0 + r * 0.3, -1.9 + (k + 1) * 0.475, 0.3 + r * 0.3, c[0] if (r + k) % 2 else PAPER)
    def piece(x, y, h, col, king=False):
        p.ell(x - 0.28, y - 0.08, x + 0.28, y + 0.08, col); p.poly([(x - 0.2, y), (x + 0.2, y), (x + 0.1, y - h), (x - 0.1, y - h)], col); p.circ(x, y - h - 0.08, 0.17, col)
        if king: p.rect(x - 0.03, y - h - 0.45, x + 0.03, y - h - 0.2, col); p.rect(x - 0.12, y - h - 0.36, x + 0.12, y - h - 0.31, col)
    piece(-0.3, 0.1, 0.9, c[1], True); piece(0.5, 0.1, 0.75, INK); piece(-1.1, 0.1, 0.6, INK); piece(1.3, 0.1, 0.6, c[1])
def risograph(p, c):
    for i, (x, y, col) in enumerate(((-1.2, -0.7, c[0]), (-0.5, -0.45, c[1]), (0.2, -0.2, PAPER))):
        p.rect(x, y, x + 1.9, y + 1.45, col, 0.03); p.rect(x, y, x + 1.9, y + 1.45, col)
    p.circ(0.5, 0.15, 0.5, c[0]); p.circ(0.85, 0.35, 0.3, c[1]); p.rect(-0.1, 0.9, 1.7, 0.98, INK); p.rect(-0.1, 1.05, 1.1, 1.1, INK)
def typewriter(p, c):
    p.poly([(-1.8, 1.0), (-1.4, -0.2), (1.4, -0.2), (1.8, 1.0)], c[0]); p.rect(-1.8, 0.8, 1.8, 1.1, INK, 0.08)
    p.rect(-1.5, -0.95, 1.5, -0.65, INK, 0.12); p.rect(-1.0, -1.7, 1.0, -0.95, PAPER, 0.03); p.rect(-0.85, -1.5, 0.85, -1.45, INK); p.rect(-0.85, -1.3, 0.6, -1.25, INK)
    for r in range(3):
        for k in range(9 - r): p.circ(-1.15 + k * 0.29 + r * 0.14, 0.05 + r * 0.25, 0.09, PAPER)
    p.circ(-1.65, -0.8, 0.2, c[1]); p.circ(1.65, -0.8, 0.2, c[1])
def dutchoven(p, c):
    p.ell(-1.5, -0.7, 1.5, 1.35, c[0]); p.rect(-1.5, -0.1, 1.5, 0.55, c[0]); p.ell(-1.55, -0.8, 1.55, 0.15, c[1])
    p.ell(-1.4, -0.7, 1.4, 0.0, c[0]); p.circ(0, -0.85, 0.2, INK); p.rect(-0.05, -0.8, 0.05, -0.65, INK)
    p.circ(-1.6, 0.25, 0.22, outline=INK, w=0.1); p.circ(1.6, 0.25, 0.22, outline=INK, w=0.1)

ITEMS = {  # key: (drawing, (top, bottom background), (object colour, accent))
    "polaroid": (polaroid, ("#e9eef0", "#c9d3d8"), ("#2f6f8f", "#d7533f")),
    "flyrod": (flyrod, ("#e8efe4", "#bccdb3"), ("#a8762f", "#3b5d3a")),
    "chess": (chess, ("#efe9df", "#d4c7b0"), ("#6b4423", "#b8864b")),
    "camera": (camera, ("#e6e9ee", "#bcc4d0"), ("#7d848f", "#2a3038")),
    "watch": (watch, ("#efe7d9", "#d9c7a4"), ("#b88a3e", "#5a3a22")),
    "keyboard": (keyboard, ("#e9ecf4", "#c4cae0"), ("#3d4a6b", "#1f2638")),
    "teaset": (teaset, ("#f4ece3", "#e3cfb8"), ("#f6efe0", "#c9a24a")),
    "mat": (mat, ("#efe5d8", "#d3b998"), ("#8a3b2a", "#d1a14b")),
    "bike": (bike, ("#e3ece9", "#b5cdc5"), ("#1f5a46", "#7a8a85")),
    "vinyl": (vinyl, ("#ece6ee", "#cdbfd3"), ("#8a4f7d", "#e0b24a")),
    "guitar": (guitar, ("#f1e8dc", "#dcc3a1"), ("#c98b45", "#3a2a1e")),
    "lamp": (lamp, ("#efe9dc", "#d6c9ac"), ("#b8893a", "#e8d8a0")),
    "canon": (camera, ("#e8e6e1", "#c9c5bc"), ("#9a9a9a", "#262626")),
    "risograph": (risograph, ("#f3e6df", "#e5c4b3"), ("#f06a3c", "#1f8f8a")),
    "typewriter": (typewriter, ("#e7ebe6", "#b9c6bb"), ("#4e6b5a", "#c9c19a")),
    "dutchoven": (dutchoven, ("#f2e4e0", "#e1bdb4"), ("#b8382c", "#e8a29a")),
}
CAPTIONS = {"polaroid": "Polaroid OneStep", "flyrod": "Bamboo fly rod", "chess": "Walnut chess set", "camera": "Leica M6", "watch": "Seiko 5", "keyboard": "Keychron Q1",
            "teaset": "Noritake tea set", "mat": "Dumbara weaving", "bike": "Raleigh roadster", "vinyl": "Jazz LPs", "guitar": "Yamaha FG800", "lamp": "Brass desk lamp",
            "canon": "Canon AE-1", "risograph": "Risograph prints", "typewriter": "Olympia SM9", "dutchoven": "Dutch oven"}

def render(key, variant):
    draw_fn, (top, bottom), (c0, c1) = ITEMS[key]
    if variant == 2: top, bottom = bottom, top
    img = gradient(rgb(top), rgb(bottom)).convert("RGBA")
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    s, cx, cy = (120, 400, 285) if variant == 1 else (260, 470, 330)  # variant 2 is a close-up
    if variant == 1:
        sh = Pen(d, cx, cy, s); sh.ell(-2.2, 1.35, 2.2, 1.75, (0, 0, 0, 38))
    draw_fn(Pen(d, cx, cy, s), (rgb(c0), rgb(c1)))
    img.alpha_composite(layer)
    img = img.convert("RGB").resize((W, H), Image.LANCZOS)
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 15)
    ImageDraw.Draw(img).text((22, H - 34), f"{CAPTIONS[key]}  -  illustration", fill=(27, 36, 48), font=font)
    return img

if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for key in ITEMS:
        for v in (1, 2):
            render(key, v).save(OUT / f"{key}-{v}.jpg", quality=86, optimize=True)
    print("wrote", len(list(OUT.glob("*.jpg"))), "images to", OUT)
