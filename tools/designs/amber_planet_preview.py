"""Vista previa (concepto) de los biomas del planeta de Ámbar (1.0.3, 2026-11-13).

Uso: python3 -I tools/designs/amber_planet_preview.py <salida.png>

Dibuja en isométrico un trozo de terreno de cada bioma con la paleta propuesta (verdes y azules, especies propias):
pradera de pasto alto, pradera de pasto bajo, bosque gigante, bosque de árboles pequeños, pantano, y océano con río.
Es solo un concepto para aprobar colores y formas; el terreno real lo hará el generador del juego.
"""
import math
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

W, D, H = 44, 44, 56      # tamaño de cada trozo (x, z, y)
S = 7                     # px por bloque

PAL = {
    # bloques
    "grass": (77, 176, 138), "grass_short": (104, 196, 150), "dirt": (92, 74, 70), "stone": (110, 116, 126),
    "sand": (196, 204, 196), "mud": (60, 74, 72), "water": (36, 120, 150), "swamp_water": (44, 112, 104),
    "deep_water": (24, 82, 132),
    # plantas
    "tall_grass": (70, 200, 170), "tall_grass_tip": (140, 230, 210), "short_tuft": (120, 210, 165),
    "flower_blue": (90, 150, 255), "flower_glow": (170, 245, 255), "bush": (46, 140, 120),
    # árboles
    "giant_bark": (196, 206, 214), "giant_bark_dark": (150, 162, 176), "giant_leaf": (44, 88, 170),
    "giant_leaf_hi": (70, 140, 210),
    "small_bark": (70, 60, 92), "small_leaf": (40, 150, 120), "small_leaf_b": (52, 120, 176),
    "swamp_bark": (64, 84, 80), "swamp_leaf": (52, 128, 110), "vine": (60, 170, 150),
    "glow_shroom": (120, 255, 235), "shroom_stem": (200, 220, 220), "lily": (60, 110, 200),
}
GLOW = {"flower_glow", "glow_shroom"}


class World:
    def __init__(self, seed):
        self.v = {}
        self.rng = random.Random(seed)

    def set(self, x, y, z, c):
        if 0 <= x < W and 0 <= z < D and 0 <= y < H:
            self.v[(x, y, z)] = c

    def get(self, x, y, z):
        return self.v.get((x, y, z))


def noise2(seed, fx, fz):
    """Ruido suave barato: suma de senos con fases aleatorias."""
    r = random.Random(seed)
    terms = [(r.uniform(0.6, 1.4), r.uniform(0, 6.28), r.uniform(0, 6.28), r.uniform(0.5, 1.0)) for _ in range(4)]

    def f(x, z):
        return sum(a * math.sin(x * fx * k + p) * math.cos(z * fz * k + q) for k, p, q, a in terms) / 2.5
    return f


def ground(w, base, amp, scale, top, seed, water_level=None, water="water", under="dirt"):
    n = noise2(seed, scale, scale)
    hmap = np.zeros((W, D), int)
    for x in range(W):
        for z in range(D):
            h = int(round(base + amp * n(x, z)))
            hmap[x, z] = h
            for y in range(0, h):
                w.set(x, y, z, "stone" if y < h - 3 else under)
            w.set(x, h, z, top)
            if water_level is not None and h < water_level:
                w.set(x, h, z, "sand" if h >= water_level - 2 else under)
                for y in range(h + 1, water_level + 1):
                    w.set(x, y, z, water)
    return hmap


def blob(w, cx, cy, cz, rx, ry, rz, c, c2=None, p2=0.0):
    for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
        for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
            for z in range(int(cz - rz) - 1, int(cz + rz) + 2):
                d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
                if d <= 1.0 and w.get(x, y, z) is None:
                    w.set(x, y, z, c2 if c2 and w.rng.random() < p2 else c)


def giant_tree(w, x, z, h0):
    r = w.rng
    hgt = r.randint(30, 40)
    for y in range(h0 + 1, h0 + hgt):
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                w.set(x + dx, y, z + dz, "giant_bark" if (dx + dz + y) % 5 else "giant_bark_dark")
    for dx, dz in ((-2, 0), (2, 0), (0, -2), (0, 2)):          # raíces
        for y in range(h0 + 1, h0 + 4):
            w.set(x + dx, y, z + dz, "giant_bark_dark")
    # copas en pisos: discos anchos y planos (como sombrillas), más pequeños hacia arriba
    for k, (dy, rad) in enumerate(((hgt - 14, 9), (hgt - 7, 7.5), (hgt, 5))):
        blob(w, x, h0 + dy, z, rad, 1.6, rad, "giant_leaf", "giant_leaf_hi", 0.25)
        for a in range(0, 360, 60):                             # ramas hacia el disco
            ax, az = math.cos(math.radians(a + 30 * k)), math.sin(math.radians(a + 30 * k))
            for t in range(2, int(rad) - 1):
                w.set(int(round(x + ax * t)), h0 + dy - 2, int(round(z + az * t)), "giant_bark_dark")


def small_tree(w, x, z, h0, leaf):
    r = w.rng
    hgt = r.randint(5, 8)
    for y in range(h0 + 1, h0 + hgt):
        w.set(x, y, z, "small_bark")
    blob(w, x, h0 + hgt, z, r.uniform(2.2, 3.0), r.uniform(1.8, 2.6), r.uniform(2.2, 3.0), leaf, "flower_glow", 0.03)


def swamp_tree(w, x, z, h0):
    r = w.rng
    hgt = r.randint(8, 11)
    for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):       # raíces en arco sobre el agua
        for y in range(h0 + 1, h0 + 4):
            w.set(x + dx * (1 if y < h0 + 3 else 0), y, z + dz * (1 if y < h0 + 3 else 0), "swamp_bark")
    for y in range(h0 + 3, h0 + hgt):
        w.set(x, y, z, "swamp_bark")
    blob(w, x, h0 + hgt, z, 4.0, 1.8, 4.0, "swamp_leaf")
    for _ in range(14):                                         # lianas colgantes
        vx, vz = x + r.randint(-4, 4), z + r.randint(-4, 4)
        if (vx - x) ** 2 + (vz - z) ** 2 > 16:
            continue
        for y in range(h0 + hgt - 2, h0 + hgt - 2 - r.randint(2, 6), -1):
            if w.get(vx, y, vz) is None:
                w.set(vx, y, vz, "vine")


def plants(w, hmap, kind, density, water_level=None):
    r = w.rng
    for x in range(W):
        for z in range(D):
            h = hmap[x, z]
            if water_level is not None and h < water_level:
                continue
            if w.get(x, h + 1, z) is not None or r.random() > density:
                continue
            if kind == "tall":
                w.set(x, h + 1, z, "tall_grass")
                if r.random() < 0.7:
                    w.set(x, h + 2, z, "tall_grass_tip" if r.random() < 0.25 else "tall_grass")
            elif kind == "short":
                w.set(x, h + 1, z, "short_tuft")
            elif kind == "flower":
                w.set(x, h + 1, z, "flower_glow" if r.random() < 0.4 else "flower_blue")


# ------------------------------------------------------------------ biomas

def tall_prairie():
    w = World(1)
    hm = ground(w, 14, 3.5, 0.13, "grass", 11)
    plants(w, hm, "tall", 0.62)
    plants(w, hm, "flower", 0.05)
    small_tree(w, 30, 12, hm[30, 12], "small_leaf_b")
    return w


def short_prairie():
    w = World(2)
    hm = ground(w, 13, 1.6, 0.09, "grass_short", 12)
    plants(w, hm, "short", 0.22)
    plants(w, hm, "flower", 0.06)
    for _ in range(5):
        x, z = w.rng.randint(5, W - 6), w.rng.randint(5, D - 6)
        blob(w, x, hm[x, z] + 1, z, 1.6, 1.1, 1.6, "bush")
    for _ in range(3):
        x, z = w.rng.randint(5, W - 6), w.rng.randint(5, D - 6)
        blob(w, x, hm[x, z], z, 1.8, 1.3, 1.5, "stone")
    return w


def giant_forest():
    w = World(3)
    hm = ground(w, 12, 2.0, 0.1, "grass", 13)
    for (x, z) in ((13, 14), (31, 28), (12, 34), (33, 9)):
        giant_tree(w, x, z, hm[x, z])
    plants(w, hm, "short", 0.18)
    plants(w, hm, "flower", 0.05)
    return w


def small_forest():
    w = World(4)
    hm = ground(w, 13, 2.5, 0.12, "grass", 14)
    pts = []
    for _ in range(400):
        x, z = w.rng.randint(3, W - 4), w.rng.randint(3, D - 4)
        if all((x - a) ** 2 + (z - b) ** 2 > 36 for a, b in pts):
            pts.append((x, z))
    for x, z in pts:
        small_tree(w, x, z, hm[x, z], "small_leaf" if w.rng.random() < 0.6 else "small_leaf_b")
    plants(w, hm, "short", 0.25)
    plants(w, hm, "flower", 0.04)
    return w


def swamp():
    w = World(5)
    hm = ground(w, 11, 1.8, 0.16, "grass", 15, water_level=12, water="swamp_water", under="mud")
    for (x, z) in ((12, 12), (30, 16), (20, 32), (36, 34)):
        swamp_tree(w, x, z, hm[x, z])
    for x in range(W):
        for z in range(D):
            h = hm[x, z]
            if h < 12 and w.get(x, 13, z) is None and w.rng.random() < 0.06:
                w.set(x, 13, z, "lily")
            elif h >= 12 and w.get(x, h + 1, z) is None and w.rng.random() < 0.05:
                w.set(x, h + 1, z, "shroom_stem")
                w.set(x, h + 2, z, "glow_shroom")
    return w


def ocean_river():
    w = World(6)
    n = noise2(16, 0.11, 0.11)
    hm = np.zeros((W, D), int)
    for x in range(W):
        for z in range(D):
            coast = (x + z) / (W + D)                        # mar abajo a la izquierda
            h = int(round(4 + coast * 18 + 1.5 * n(x, z)))
            river = abs((z - 26) - 7 * math.sin(x * 0.18))   # río que baja hacia el mar
            if river < 2.6 and h > 12:
                h = 11
            hm[x, z] = h
            for y in range(0, h):
                w.set(x, y, z, "stone" if y < h - 3 else "dirt")
            top = "sand" if h <= 13 else "grass"
            w.set(x, h, z, top)
            for y in range(h + 1, 13):
                w.set(x, y, z, "deep_water" if h < 7 else "water")
            if river < 2.6 and h == 11:
                w.set(x, 12, z, "water")
    plants(w, hm, "tall", 0.3, water_level=14)
    small_tree(w, 36, 8, hm[36, 8], "small_leaf")
    small_tree(w, 40, 18, hm[40, 18], "small_leaf_b")
    return w


# ------------------------------------------------------------------ dibujo

def render(w, size=(560, 650)):
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    glow = Image.new("RGBA", size, (0, 0, 0, 0))
    d, g = ImageDraw.Draw(img), ImageDraw.Draw(glow)
    ox, oy = size[0] // 2, 250
    rnd = random.Random(7)

    def iso(x, y, z):
        return ox + (x - z) * S, oy + (x + z) * S / 2 - y * S

    keys = sorted(w.v.keys(), key=lambda k: (k[0] + k[2], k[1], k[0]))
    for (x, y, z) in keys:
        c = w.v[(x, y, z)]
        base = np.array(PAL[c], float) * rnd.uniform(0.92, 1.06)
        transparent = c in ("water", "swamp_water", "deep_water")
        if transparent and w.get(x, y + 1, z) in ("water", "swamp_water", "deep_water"):
            continue

        def col(k):
            v = np.clip(base * k, 0, 255).astype(int)
            return tuple(v) + ((200,) if transparent else (255,))
        p = lambda a, b, cc: iso(x + a, y + b, z + cc)
        if w.get(x, y + 1, z) is None or transparent:
            poly = [p(0, 1, 0), p(1, 1, 0), p(1, 1, 1), p(0, 1, 1)]
            d.polygon(poly, fill=col(1.0))
            if c in GLOW:
                g.polygon(poly, fill=PAL[c] + (255,))
        if not transparent and w.get(x, y, z + 1) is None:
            poly = [p(0, 1, 1), p(1, 1, 1), p(1, 0, 1), p(0, 0, 1)]
            d.polygon(poly, fill=col(0.78))
            if c in GLOW:
                g.polygon(poly, fill=PAL[c] + (255,))
        if not transparent and w.get(x + 1, y, z) is None:
            poly = [p(1, 1, 0), p(1, 1, 1), p(1, 0, 1), p(1, 0, 0)]
            d.polygon(poly, fill=col(0.62))
    from PIL import ImageFilter
    halo = glow.filter(ImageFilter.GaussianBlur(5))
    out = Image.alpha_composite(img, halo)
    return out


def main(out):
    biomes = [
        (tall_prairie, "Pradera de pasto alto", "Pasto turquesa de dos bloques, flores azules y algunas que brillan"),
        (short_prairie, "Pradera de pasto bajo", "Llana y abierta: matas cortas, arbustos y rocas"),
        (giant_forest, "Bosque gigante", "Troncos plateados de 3×3 y 30–40 de alto; copas azules en pisos, como sombrillas"),
        (small_forest, "Bosque de árboles pequeños", "Tronco violeta oscuro y copa redonda verde azulada o azul"),
        (swamp, "Pantano", "Agua verdosa, raíces en arco, lianas, nenúfares azules y hongos que brillan"),
        (ocean_river, "Océano, costa y río", "Agua azul profundo, arena gris pálida y un río que baja al mar"),
    ]
    cols, cw, ch = 3, 560, 690
    sheet = Image.new("RGBA", (cols * cw, 2 * ch + 60), (0, 0, 0, 255))
    sky = np.zeros((sheet.height, sheet.width, 4), np.uint8)
    t = np.linspace(0, 1, sheet.height)[:, None]
    top, hor = np.array([120, 175, 200]), np.array([246, 214, 160])   # cielo bajo la luz de Ámbar
    for i in range(3):
        sky[..., i] = (top[i] * (1 - t) + hor[i] * t).astype(np.uint8)
    sky[..., 3] = 255
    sheet = Image.fromarray(sky, "RGBA")
    dr = ImageDraw.Draw(sheet)
    try:
        F = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 20)
        f = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)
        F0 = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 24)
    except OSError:
        F = f = F0 = ImageFont.load_default()
    dr.text((16, 14), "Planeta de Ámbar — biomas (concepto)", fill=(30, 40, 50, 255), font=F0)
    for i, (fn, title, sub) in enumerate(biomes):
        x0, y0 = (i % cols) * cw, 60 + (i // cols) * ch
        sheet.alpha_composite(render(fn()), (x0, y0 + 40))
        dr.text((x0 + 16, y0 + 4), title, fill=(25, 35, 45, 255), font=F)
        dr.text((x0 + 16, y0 + 30), sub, fill=(40, 55, 65, 255), font=f)
    sheet.save(out)
    print("ok", out)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "amber_biomes.png")
