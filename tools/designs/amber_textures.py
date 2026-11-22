"""Texturas (16×16) de los bloques del planeta de Ámbar y vista previa con texturas (1.0.3, 2026-11-13).

Uso: python3 -I tools/designs/amber_textures.py <carpeta_salida>

Escribe un PNG por textura en <carpeta_salida>/textures/ y dos vistas previas:
  - amber_texturas.png: todas las texturas, planas (a 8×) y como cubo;
  - amber_biomas_texturas.png: los seis biomas en isométrico con las texturas.
Todo es pixel art procedural (con semilla fija), nada copiado de Minecraft.
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import amber_planet_preview as AP  # noqa: E402  (mismos biomas que el concepto)
import calm_textures as CT  # noqa: E402  (suelos y rocas sin rasgos que se repitan)

T = 16


def rgba(c, a=255):
    return (int(c[0]), int(c[1]), int(c[2]), a)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3])


def noise_tile(rng, base, var=0.08, dots=(), n_dots=0):
    """Ruido de píxel suave con motas de otros colores (se usa poco; lo detallado va con pnoise + ramp)."""
    img = Image.new("RGBA", (T, T))
    px = img.load()
    for y in range(T):
        for x in range(T):
            px[x, y] = rgba(shade(base, 1 + rng.uniform(-var, var)))
    for _ in range(n_dots):
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba(rng.choice(dots))
    return img


# ------------------------------------------------------------------ herramientas de detalle (1.0.3, segunda pasada)

def _smooth(t):
    return t * t * (3 - 2 * t)


def pnoise(rng, octaves=((8, 0.5), (4, 0.3), (2, 0.2))):
    """Ruido de valor periódico (se repite cada 16 px, sin costuras), normalizado a 0..1."""
    out = np.zeros((T, T))
    for period, w in octaves:
        n = max(1, T // period)
        g = np.array([[rng.random() for _ in range(n)] for _ in range(n)])
        for y in range(T):
            fy = y / period
            iy, ty = int(fy) % n, _smooth(fy - int(fy))
            jy = (iy + 1) % n
            for x in range(T):
                fx = x / period
                ix, tx = int(fx) % n, _smooth(fx - int(fx))
                jx = (ix + 1) % n
                v = (g[iy, ix] * (1 - tx) * (1 - ty) + g[iy, jx] * tx * (1 - ty)
                     + g[jy, ix] * (1 - tx) * ty + g[jy, jx] * tx * ty)
                out[y, x] += w * v
    out -= out.min()
    return out / max(1e-9, out.max())


def ramp(a, b, n):
    """n colores de a (oscuro) a b (claro)."""
    return [tuple(int(a[i] + (b[i] - a[i]) * k / (n - 1)) for i in range(3)) for k in range(n)]


def posterize(field, palette, rng=None, jitter=0.08):
    """Convierte un campo 0..1 en píxeles de la paleta (pocos tonos, como el pixel art), con algo de tramado."""
    img = Image.new("RGBA", (T, T))
    px = img.load()
    n = len(palette)
    for y in range(T):
        for x in range(T):
            v = field[y, x] + (rng.uniform(-jitter, jitter) if rng else 0)
            px[x, y] = rgba(palette[max(0, min(n - 1, int(v * n)))])
    return img


def edges(img, k_dark=0.82):
    """Oscurece un poco los píxeles cuyo vecino de arriba-izquierda es más claro: da relieve a las manchas."""
    px = img.load()
    src = img.copy().load()
    for y in range(T):
        for x in range(T):
            a, b = src[x, y], src[(x - 1) % T, (y - 1) % T]
            if sum(b[:3]) > sum(a[:3]) + 30:
                px[x, y] = rgba(shade(a, k_dark))
    return img


def pebble(px, x, y, light, mid, dark):
    for (dx, dy, c) in ((0, 0, light), (1, 0, mid), (0, 1, mid), (1, 1, dark)):
        px[(x + dx) % T, (y + dy) % T] = rgba(c)


# ------------------------------------------------------------------ bloques

def soil(rng):
    img = posterize(pnoise(rng), ramp((48, 40, 58), (104, 92, 116), 5), rng)
    img = edges(img)
    px = img.load()
    for _ in range(5):                                      # piedrecitas
        pebble(px, rng.randrange(T), rng.randrange(T), (128, 120, 140), (98, 92, 112), (52, 46, 62))
    for _ in range(3):                                      # raicillas claras
        x, y = rng.randrange(T), rng.randrange(T)
        for k in range(rng.randint(2, 4)):
            px[(x + k) % T, (y + (k // 2)) % T] = rgba((132, 150, 140))
    return img


def grass_top(rng, base, light, dark, blue):
    pal = [shade(dark, 0.88), dark, base, light, shade(light, 1.1)]
    img = posterize(pnoise(rng, ((8, 0.4), (4, 0.35), (2, 0.25))), pal, rng, 0.12)
    px = img.load()
    for _ in range(16):                                     # briznas cortas vistas desde arriba
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba(shade(light, 1.12))
        px[x, (y + 1) % T] = rgba(dark)
    for _ in range(3):                                      # florecitas azules con su sombra
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba(blue)
        px[(x + 1) % T, (y + 1) % T] = rgba(shade(dark, 0.8))
    return img


def grass_side(rng, top, soil_img, light, dark):
    img = soil_img.copy()
    px = img.load()
    prof = pnoise(rng, ((4, 0.7), (2, 0.3)))[0]
    for x in range(T):
        depth = 3 + int(prof[x] * 3)
        if rng.random() < 0.18:
            depth += 2                                       # gotas que bajan más
        for y in range(depth):
            c = light if y == 0 else top if y < depth - 1 else dark
            if y > 0 and y < depth - 1 and rng.random() < 0.25:
                c = light if rng.random() < 0.5 else dark
            px[x, y] = rgba(shade(c, 1 + rng.uniform(-0.05, 0.05)))
        px[x, depth] = rgba(shade(px[x, depth][:3], 0.75))   # sombra bajo el pasto
    return img


def stone(rng):
    pal = ramp((80, 86, 100), (140, 146, 158), 5)
    img = edges(posterize(pnoise(rng, ((8, 0.45), (4, 0.35), (2, 0.2))), pal, rng, 0.1))
    px = img.load()
    for _ in range(2):                                       # grietas finas
        x, y = rng.randrange(T), rng.randrange(T)
        for _ in range(rng.randint(4, 7)):
            px[x % T, y % T] = rgba((66, 72, 86))
            px[(x + 1) % T, y % T] = rgba((150, 156, 168))   # borde iluminado de la grieta
            x += rng.choice((-1, 0, 1))
            y += 1
    return img


def pale_sand(rng):
    pal = ramp((170, 178, 176), (222, 228, 222), 5)
    img = posterize(pnoise(rng, ((4, 0.5), (2, 0.5))), pal, rng, 0.18)
    px = img.load()
    for _ in range(4):                                       # conchitas y granos oscuros
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba((240, 244, 240))
        px[(x + 1) % T, y] = rgba((150, 170, 180))
    for _ in range(6):
        px[rng.randrange(T), rng.randrange(T)] = rgba((140, 150, 152))
    return img


def mud(rng):
    pal = ramp((34, 44, 44), (78, 98, 94), 5)
    img = edges(posterize(pnoise(rng), pal, rng, 0.1))
    px = img.load()
    for _ in range(5):                                       # brillos de humedad
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba((120, 150, 144))
        px[(x + 1) % T, y] = rgba((96, 122, 118))
    return img


def bark(rng, base, dark, light, streak=True):
    """Corteza con surcos verticales continuos (ondulan un poco y se repiten de arriba abajo), cada surco con su
    borde de luz a la izquierda, y un nudo."""
    pal = [shade(base, 0.86), base, shade(base, 1.08)]
    img = posterize(pnoise(rng, ((8, 0.3), (2, 0.7))), pal, rng, 0.1)
    px = img.load()
    cols = sorted(rng.sample(range(T), 5))
    for x0 in cols:
        ph = rng.uniform(0, 6.28)
        for y in range(T):
            x = (x0 + int(round(0.8 * math.sin(2 * math.pi * y / T + ph)))) % T
            px[x, y] = rgba(dark)
            px[(x - 1) % T, y] = rgba(light if y % 5 else base)
    kx, ky = rng.randrange(3, 12), rng.randrange(3, 12)            # nudo
    for (dx, dy) in ((0, -1), (-1, 0), (1, 0), (0, 1)):
        px[kx + dx, ky + dy] = rgba(dark)
    px[kx, ky] = rgba(shade(dark, 0.7))
    return img


def silver_bark(rng):
    pal = ramp((168, 178, 192), (232, 238, 244), 4)
    img = posterize(pnoise(rng, ((8, 0.4), (4, 0.4), (2, 0.2))), pal, rng, 0.08)
    px = img.load()
    for _ in range(6):                                        # "ojos" horizontales de corteza, con brillo encima
        x, y = rng.randrange(T), rng.randrange(1, T)
        L = rng.randint(2, 5)
        for k in range(L):
            px[(x + k) % T, y] = rgba((96, 110, 134) if 0 < k < L - 1 else (130, 142, 162))
            px[(x + k) % T, y - 1] = rgba((242, 246, 250))
    for x0 in rng.sample(range(T), 2):                         # dos vetas suaves
        for y in range(T):
            if rng.random() < 0.7:
                px[x0, y] = rgba(shade(px[x0, y][:3], 0.9))
    return img


def log_top(rng, outer, ring_a, ring_b):
    """Anillos redondos (un poco irregulares), borde de corteza y una grieta del centro hacia fuera."""
    img = Image.new("RGBA", (T, T))
    px = img.load()
    wob = pnoise(rng, ((4, 0.6), (2, 0.4)))
    for y in range(T):
        for x in range(T):
            d = math.hypot(x - 7.5, y - 7.5) + (wob[y, x] - 0.5) * 1.2
            if x in (0, T - 1) or y in (0, T - 1):
                c = shade(outer, 0.85)
            elif x in (1, T - 2) or y in (1, T - 2):
                c = outer
            else:
                c = ring_a if int(d) % 3 == 0 else ring_b if int(d) % 3 == 1 else shade(ring_b, 1.06)
            px[x, y] = rgba(c)
    px[7, 7] = px[8, 8] = rgba(shade(ring_a, 0.8))
    x, y = 8, 8
    for _ in range(5):                                       # grieta
        x += 1
        y += rng.choice((0, 1))
        if 1 < x < T - 2 and 1 < y < T - 2:
            px[x, y] = rgba(shade(ring_a, 0.7))
    return img


def leaves(rng, base, light, dark, holes=0.03, glow=None, glow_n=0):
    """Hojas en racimos con cinco tonos: fondo en sombra profunda, racimos redondos con su lado de luz (arriba a la
    izquierda), un brillo y una sombra abajo a la derecha; dentro, hojitas sueltas marcadas. Se repiten sin costuras."""
    deep = shade(dark, 0.78)
    img = Image.new("RGBA", (T, T))
    px = img.load()
    for y in range(T):
        for x in range(T):
            px[x, y] = rgba(deep if rng.random() < 0.6 else dark)
    for _ in range(16):
        cx, cy, r = rng.uniform(0, T), rng.uniform(0, T), rng.uniform(1.7, 2.9)
        for dy in range(-3, 4):
            for dx in range(-3, 4):
                if dx * dx + dy * dy > r * r:
                    continue
                x, y = math.floor(cx + dx) % T, math.floor(cy + dy) % T
                k = dx + dy
                c = shade(light, 1.12) if k <= -3 else light if k <= -1 else shade(base, 0.82) if k >= 2 else base
                px[x, y] = rgba(c)
        # hojita marcada dentro del racimo (una raya clara de 2 px)
        x, y = math.floor(cx) % T, math.floor(cy) % T
        px[x, y] = rgba(shade(light, 1.05))
        px[(x + 1) % T, (y - 1) % T] = rgba(shade(light, 1.05))
    for _ in range(int(holes * T * T)):
        x, y = rng.randrange(T), rng.randrange(T)
        if px[x, y][:3] == rgba(deep)[:3]:
            px[x, y] = (0, 0, 0, 0)
    for _ in range(glow_n):
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba(glow)
        px[(x + 1) % T, y] = rgba(shade(glow, 0.8))
    return img


# ------------------------------------------------------------------ plantas (sprites en cruz)

def blades(rng, cols, n, hmin, hmax, height=T, tip=None, heads=0.0):
    """Briznas continuas de dos tonos (borde de luz a la izquierda), que salen del borde de abajo y se curvan poco a
    poco; algunas con espiga en la punta."""
    img = Image.new("RGBA", (T, height), (0, 0, 0, 0))
    px = img.load()
    xs = sorted(rng.sample(range(1, T - 1), min(n, T - 2)))
    for x0 in xs:
        h = rng.randint(hmin, hmax)
        bend = rng.uniform(-3.0, 3.0)
        c = rng.choice(cols)
        head = rng.random() < heads
        for k in range(h):
            f = k / max(1, h - 1)
            x = int(round(x0 + bend * f * f))
            y = height - 1 - k
            if not (0 <= x < T and 0 <= y < height):
                break
            col = shade(c, 0.66 + 0.42 * f)
            px[x, y] = rgba(col)
            if f < 0.45 and x + 1 < T:                       # base más gruesa, con sombra a la derecha
                px[x + 1, y] = rgba(shade(c, 0.58 + 0.3 * f))
            if 0.3 < f < 0.9 and k % 3 == 0 and x - 1 >= 0 and px[x - 1, y][3] == 0:
                px[x - 1, y] = rgba(shade(c, 0.95 + 0.3 * f))  # brillo
        if head and tip:
            for k in range(3):
                yy = height - h - k
                xx = int(round(x0 + bend))
                if 0 <= xx < T and 0 <= yy < height:
                    px[xx, yy] = rgba(tip if k < 2 else shade(tip, 1.1))
                    if xx + 1 < T and k == 1:
                        px[xx + 1, yy] = rgba(shade(tip, 0.85))
    return img


def tall_grass_pair(rng):
    """El pasto alto se dibuja entero en 16×32 y luego se parte: así las briznas siguen sin cortes de un bloque al otro."""
    cols = [(70, 200, 170), (58, 176, 152), (92, 214, 186), (64, 188, 176)]
    full = blades(rng, cols, 10, 18, 29, height=2 * T, tip=(160, 240, 222), heads=0.5)
    return full.crop((0, T, T, 2 * T)), full.crop((0, 0, T, T))


def short_grass(rng):
    return blades(rng, [(110, 206, 160), (90, 186, 146), (130, 220, 176)], 9, 5, 11, tip=(170, 236, 200), heads=0.2)


def flower(rng, petal, center, stem=(60, 150, 120), glow=False):
    """Flor de cinco pétalos unidos al centro (forma polar, sin píxeles sueltos: con mods que dan volumen a los sprites
    los pétalos no se ven separados), con nervio, sombra entre pétalos, tallo y dos hojas pegadas al tallo."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    # tallo y hojas, todo unido
    for y in range(8, 16):
        px[7, y] = rgba(stem)
        px[8, y] = rgba(shade(stem, 0.78))
    for (x, y, c) in ((6, 12, stem), (5, 12, shade(stem, 1.15)), (4, 11, shade(stem, 1.2)), (5, 11, stem), (6, 13, shade(stem, 0.8)),
                      (9, 11, shade(stem, 0.8)), (10, 11, stem), (10, 10, shade(stem, 1.15)), (11, 10, shade(stem, 1.2)), (9, 12, shade(stem, 0.7))):
        px[x, y] = rgba(c)
    # cabeza: cinco pétalos que salen del centro (forma polar), sin huecos entre pétalo y centro
    cx, cy = 7.5, 4.6
    dark, light, vein = shade(petal, 0.7), shade(petal, 1.2), shade(petal, 0.84)
    tip = shade(petal, 1.32)
    for y in range(0, 10):
        for x in range(1, 15):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r = math.hypot(dx, dy)
            th = math.atan2(dy, dx) + math.pi / 2          # un pétalo hacia arriba
            lobe = abs(math.cos(2.5 * th)) ** 0.7
            rmax = 2.0 + 2.9 * lobe
            if r > rmax:
                continue
            if r < 1.45:
                k = dx + dy
                px[x, y] = rgba(shade(center, 1.08) if k < -0.6 else shade(center, 0.72) if k > 0.6 else center)
                continue
            k = (dx + dy) / max(r, 1e-6)
            c = light if k < -0.45 else dark if k > 0.55 else petal
            if lobe > 0.93 and r < rmax - 0.9:
                c = vein                                       # nervio del pétalo
            if r > rmax - 0.8 and lobe > 0.6 and k < 0.3:
                c = tip if glow else light
            if lobe < 0.45:
                c = dark                                       # sombra entre pétalos
            px[x, y] = rgba(c)
    return img


def glow_mushroom(rng):
    """Hongo luminoso: sombrero con manchas y borde, láminas debajo, pie con sombra; y uno pequeño al lado."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    cap, cap_l, cap_d = (90, 230, 215), (180, 255, 245), (50, 170, 165)
    for y in range(3, 10):
        for x in range(2, 13):
            dx, dy = (x - 7.0) / 5.4, (y - 8.5) / 5.0
            if dx * dx + dy * dy <= 1 and y <= 8:
                k = dx + dy
                px[x, y] = rgba(cap_l if k < -0.9 else cap_d if k > 0.15 else cap)
    for x in range(3, 12):                                      # láminas
        px[x, 9] = rgba((40, 120, 120) if x % 2 else (60, 150, 148))
    for (x, y) in ((5, 5), (8, 4), (10, 6), (6, 7)):            # manchas
        px[x, y] = rgba((230, 255, 252))
    for y in range(10, 16):                                     # pie
        px[7, y] = rgba((214, 232, 230))
        px[8, y] = rgba((170, 196, 196))
    for (x, y, c) in ((12, 11, cap), (13, 11, cap_l), (14, 11, cap_d), (12, 12, cap_d), (13, 12, cap_d), (14, 12, cap_d),
                      (13, 13, (214, 232, 230)), (13, 14, (214, 232, 230)), (13, 15, (170, 196, 196))):
        px[x, y] = rgba(c)
    return img


def vine(rng):
    """Tallos continuos que ondulan con periodo 16 (empalman con el bloque de abajo) y hojitas de dos tonos
    alternadas a los lados."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    for x0, ph in ((2, 0.0), (7, 2.1), (12, 4.2)):
        for y in range(T):
            x = x0 + int(round(1.2 * math.sin(2 * math.pi * y / T + ph)))
            px[x, y] = rgba(shade((50, 150, 132), 1 + 0.08 * math.sin(y)))
            if (y + int(ph * 3)) % 4 == 0:
                side = -1 if (y // 4) % 2 else 1
                for k, c in ((1, (84, 196, 170)), (2, (120, 222, 194))):
                    xx = x + side * k
                    if 0 <= xx < T:
                        px[xx, y] = rgba(c)
                if 0 <= x + side < T and y + 1 < T:
                    px[x + side, y + 1] = rgba((58, 160, 138))
                if 0 <= x + 2 * side < T and y + 1 < T:
                    px[x + 2 * side, y + 1] = rgba((70, 180, 156))
    return img


def lily(rng):
    """Nenúfar: hoja redonda con corte, nervios que salen del centro, borde oscuro y una flor blanca y azul."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    for y in range(T):
        for x in range(T):
            dx, dy = (x - 7.5) / 6.8, (y - 8.0) / 6.4
            r = dx * dx + dy * dy
            ang = math.atan2(y - 8.0, x - 7.5)
            if r <= 1 and not (-0.35 < ang < 0.25 and r > 0.05):
                c = (60, 110, 200)
                if r > 0.78:
                    c = (40, 80, 160)
                elif dx + dy < -0.5:
                    c = (84, 140, 222)
                px[x, y] = rgba(c)
    for a in (1.2, 2.3, 3.4, 4.5, 5.6):                          # nervios
        for k in range(2, 6):
            x, y = int(round(7.5 + math.cos(a) * k)), int(round(8 + math.sin(a) * k))
            if px[x, y][3]:
                px[x, y] = rgba((96, 154, 230))
    for (x, y, c) in ((5, 5, (240, 245, 255)), (6, 5, (200, 215, 245)), (5, 6, (200, 215, 245)), (4, 5, (240, 245, 255)),
                      (5, 4, (240, 245, 255)), (6, 6, (120, 160, 240))):
        px[x, y] = rgba(c)
    return img


def bush_leaves(rng):
    return leaves(rng, (46, 140, 120), (66, 168, 140), (30, 104, 92), holes=0.0)


def make_textures():
    rng = random.Random(2026)
    t = {}
    t["amber_soil"] = CT.calmed(soil, CT.soil, rng, 9101)
    for name, base, light, dark in (("amber_grass", (77, 176, 138), (104, 202, 160), (56, 146, 116)),
                                    ("amber_short_grass_block", (104, 196, 150), (134, 220, 176), (82, 168, 128))):
        t[name + "_top"] = grass_top(rng, base, light, dark, (90, 150, 255))
        t[name + "_side"] = grass_side(rng, base, t["amber_soil"], light, dark)
    t["amber_stone"] = CT.calmed(stone, CT.stone, rng, 9102)
    t["amber_sand"] = CT.calmed(pale_sand, CT.pale_sand, rng, 9103)
    t["amber_mud"] = mud(rng)
    t["silverwood_log"] = silver_bark(rng)
    t["silverwood_log_top"] = log_top(rng, (196, 206, 214), (170, 180, 194), (214, 222, 230))
    t["silverwood_leaves"] = leaves(rng, (44, 88, 170), (70, 140, 210), (30, 62, 130))
    t["duskwood_log"] = bark(rng, (70, 60, 92), (50, 42, 70), (92, 80, 118))
    t["duskwood_log_top"] = log_top(rng, (70, 60, 92), (140, 120, 170), (118, 100, 150))
    t["duskwood_leaves"] = leaves(rng, (40, 150, 120), (62, 178, 144), (26, 112, 92), glow=(170, 245, 255), glow_n=1)
    t["duskwood_leaves_blue"] = leaves(rng, (52, 120, 176), (76, 150, 206), (36, 92, 140), glow=(170, 245, 255), glow_n=1)
    t["mirewood_log"] = bark(rng, (64, 84, 80), (46, 62, 60), (84, 108, 102))
    t["mirewood_log_top"] = log_top(rng, (64, 84, 80), (110, 140, 126), (92, 120, 108))
    t["mirewood_leaves"] = leaves(rng, (52, 128, 110), (74, 156, 132), (36, 96, 84))
    t["bush_leaves"] = bush_leaves(rng)
    t["tall_grass_bottom"], t["tall_grass_top"] = tall_grass_pair(rng)
    t["short_grass"] = short_grass(rng)
    t["azure_flower"] = flower(rng, (90, 150, 255), (240, 240, 200))
    t["glow_flower"] = flower(rng, (150, 240, 255), (255, 255, 255), glow=True)
    t["glow_mushroom"] = glow_mushroom(rng)
    t["mire_vine"] = vine(rng)
    t["azure_lily"] = lily(rng)
    return t


GLOWING = {"glow_flower", "glow_mushroom"}

# bloque de la vista previa -> (arriba, lado) o sprite
CUBES = {
    "grass": ("amber_grass_top", "amber_grass_side"),
    "grass_short": ("amber_short_grass_block_top", "amber_short_grass_block_side"),
    "dirt": ("amber_soil", "amber_soil"), "stone": ("amber_stone", "amber_stone"), "sand": ("amber_sand", "amber_sand"),
    "mud": ("amber_mud", "amber_mud"),
    "giant_bark": ("silverwood_log_top", "silverwood_log"), "giant_bark_dark": ("silverwood_log_top", "silverwood_log"),
    "giant_leaf": ("silverwood_leaves", "silverwood_leaves"), "giant_leaf_hi": ("silverwood_leaves", "silverwood_leaves"),
    "small_bark": ("duskwood_log_top", "duskwood_log"), "small_leaf": ("duskwood_leaves", "duskwood_leaves"),
    "small_leaf_b": ("duskwood_leaves_blue", "duskwood_leaves_blue"),
    "swamp_bark": ("mirewood_log_top", "mirewood_log"), "swamp_leaf": ("mirewood_leaves", "mirewood_leaves"),
    "bush": ("bush_leaves", "bush_leaves"), "shroom_stem": None,
}
SPRITES = {
    "tall_grass": "tall_grass_bottom", "tall_grass_tip": "tall_grass_top", "short_tuft": "short_grass",
    "flower_blue": "azure_flower", "flower_glow": "glow_flower", "glow_shroom": "glow_mushroom", "vine": "mire_vine",
}
WATER = {"water": (36, 120, 150), "swamp_water": (44, 112, 104), "deep_water": (24, 82, 132)}


# ------------------------------------------------------------------ caras isométricas

def face_sprites(tex, s):
    """Proyecta una textura en las tres caras visibles de un cubo isométrico de s px por bloque."""
    src = np.asarray(tex.convert("RGBA")).astype(float)
    out = {}
    wpx, hpx = 2 * s, 2 * s

    def warp(kind, k):
        img = np.zeros((hpx + s, wpx, 4), np.uint8)
        for py in range(img.shape[0]):
            for px_ in range(wpx):
                X, Y = px_ - s + 0.5, py + 0.5          # origen: vértice de arriba del rombo en (s, 0)
                if kind == "top":
                    # rombo: x = (u - v) * s, y = (u + v) * s / 2
                    u = (Y / (s / 2) + X / s) / 2
                    v = (Y / (s / 2) - X / s) / 2
                elif kind == "left":                     # cara +z: de (−s, s/2) a (0, s), baja s
                    u = (X + s) / s
                    v = (Y - (s / 2 + u * s / 2)) / s
                else:                                    # cara +x: de (0, s) a (s, s/2)
                    u = X / s
                    v = (Y - (s - u * s / 2)) / s
                if 0 <= u < 1 and 0 <= v < 1:
                    c = src[int(v * T), int(u * T)] if kind != "top" else src[int(u * T), int(v * T)]
                    if c[3] > 0:
                        img[py, px_] = list(np.clip(c[:3] * k, 0, 255)) + [255]
        return Image.fromarray(img, "RGBA")
    out["top"], out["left"], out["right"] = warp("top", 1.0), warp("left", 0.8), warp("right", 0.64)
    return out


def render_textured(w, tex, s=12, size=(620, 760), oy=330):
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    glow = Image.new("RGBA", size, (0, 0, 0, 0))
    ox = size[0] // 2
    cache = {}

    def cube(name):
        if name not in cache:
            top, side = CUBES[name]
            a, b = face_sprites(tex[top], s), face_sprites(tex[side], s)
            cache[name] = (a["top"], b["left"], b["right"])
        return cache[name]

    def sprite(name):
        key = "spr_" + name
        if key not in cache:
            cache[key] = tex[name].resize((2 * s, 2 * s), Image.NEAREST)
        return cache[key]

    def water_face(c):
        key = "w_" + str(c)
        if key not in cache:
            t = Image.new("RGBA", (T, T), rgba(c, 205))
            px = t.load()
            r = random.Random(3)
            for _ in range(20):
                x, y = r.randrange(T), r.randrange(T)
                px[x, y] = rgba(shade(c, 1.18), 205)
            cache[key] = face_sprites(t, s)["top"]
            # face_sprites pone alfa 255; volver a hacerlo translúcido
            arr = np.asarray(cache[key]).copy()
            arr[..., 3] = np.where(arr[..., 3] > 0, 200, 0)
            cache[key] = Image.fromarray(arr, "RGBA")
        return cache[key]

    keys = sorted(w.v.keys(), key=lambda k: (k[0] + k[2], k[1], k[0]))
    for (x, y, z) in keys:
        c = w.v[(x, y, z)]
        sx = int(ox + (x - z) * s - s)
        sy = int(oy + (x + z) * s / 2 - (y + 1) * s)
        if c in WATER:
            if w.get(x, y + 1, z) in WATER:
                continue
            img.alpha_composite(water_face(WATER[c]), (sx, sy))
            continue
        if c in SPRITES or c == "lily" or c == "shroom_stem":
            if c == "shroom_stem":
                continue
            name = "azure_lily" if c == "lily" else SPRITES[c]
            spr = sprite(name)
            if c == "lily":
                spr = face_sprites(tex["azure_lily"], s)["top"]
                img.alpha_composite(spr, (sx, sy + s))
            else:
                # sprite en cruz visto de frente, centrado en el bloque
                img.alpha_composite(spr, (sx, int(sy + s / 2)))
                if name in GLOWING:
                    glow.alpha_composite(spr, (sx, int(sy + s / 2)))
            continue
        top, left, right = cube(c)
        transparent_above = w.get(x, y + 1, z) in (None,) or w.get(x, y + 1, z) in SPRITES or w.get(x, y + 1, z) in WATER
        if w.get(x, y + 1, z) is None or w.get(x, y + 1, z) in SPRITES or w.get(x, y + 1, z) in ("lily", "shroom_stem"):
            img.alpha_composite(top, (sx, sy))
        nz = w.get(x, y, z + 1)
        if nz is None or nz in SPRITES or nz in WATER or nz in ("lily", "shroom_stem"):
            img.alpha_composite(left, (sx, sy))
        nx = w.get(x + 1, y, z)
        if nx is None or nx in SPRITES or nx in WATER or nx in ("lily", "shroom_stem"):
            img.alpha_composite(right, (sx, sy))
    a = np.asarray(glow.filter(ImageFilter.GaussianBlur(max(3, s // 3))))[..., 3].astype(float)
    halo = np.zeros((size[1], size[0], 4), np.uint8)
    halo[..., 0], halo[..., 1], halo[..., 2] = 170, 245, 255
    halo[..., 3] = np.clip(a * 0.55, 0, 255).astype(np.uint8)
    return Image.alpha_composite(img, Image.fromarray(halo, "RGBA"))


def sky(wd, ht):
    arr = np.zeros((ht, wd, 4), np.uint8)
    t = np.linspace(0, 1, ht)[:, None]
    top, hor = np.array([120, 175, 200]), np.array([246, 214, 160])
    for i in range(3):
        arr[..., i] = (top[i] * (1 - t) + hor[i] * t).astype(np.uint8)
    arr[..., 3] = 255
    return Image.fromarray(arr, "RGBA")


def fonts():
    try:
        return (ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22),
                ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 15),
                ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 12))
    except OSError:
        f = ImageFont.load_default()
        return f, f, f


LABELS = {
    "amber_grass": "Pasto (pradera alta)", "amber_short_grass_block": "Pasto (pradera baja)", "amber_soil": "Tierra azulada",
    "amber_stone": "Piedra", "amber_sand": "Arena pálida", "amber_mud": "Lodo",
    "silverwood_log": "Tronco plateado", "silverwood_leaves": "Hojas plateado (azul)",
    "duskwood_log": "Tronco violeta", "duskwood_leaves": "Hojas violeta (verde)", "duskwood_leaves_blue": "Hojas violeta (azul)",
    "mirewood_log": "Tronco del pantano", "mirewood_leaves": "Hojas del pantano", "bush_leaves": "Arbusto",
    "tall_grass": "Pasto alto", "short_grass": "Pasto bajo", "azure_flower": "Flor azul", "glow_flower": "Flor luminosa",
    "glow_mushroom": "Hongo luminoso", "mire_vine": "Liana", "azure_lily": "Nenúfar azul",
}


def texture_sheet(tex, out):
    F, Fm, f = fonts()
    cubes = [("amber_grass", "amber_grass_top", "amber_grass_side"),
             ("amber_short_grass_block", "amber_short_grass_block_top", "amber_short_grass_block_side"),
             ("amber_soil", "amber_soil", "amber_soil"), ("amber_stone", "amber_stone", "amber_stone"),
             ("amber_sand", "amber_sand", "amber_sand"), ("amber_mud", "amber_mud", "amber_mud"),
             ("silverwood_log", "silverwood_log_top", "silverwood_log"), ("silverwood_leaves", "silverwood_leaves", "silverwood_leaves"),
             ("duskwood_log", "duskwood_log_top", "duskwood_log"), ("duskwood_leaves", "duskwood_leaves", "duskwood_leaves"),
             ("duskwood_leaves_blue", "duskwood_leaves_blue", "duskwood_leaves_blue"),
             ("mirewood_log", "mirewood_log_top", "mirewood_log"), ("mirewood_leaves", "mirewood_leaves", "mirewood_leaves"),
             ("bush_leaves", "bush_leaves", "bush_leaves")]
    sprites = ["tall_grass", "short_grass", "azure_flower", "glow_flower", "glow_mushroom", "mire_vine", "azure_lily"]
    cw, ch = 170, 190
    cols = 7
    rows = math.ceil(len(cubes) / cols) + math.ceil(len(sprites) / cols)
    sheet = sky(cols * cw, 70 + rows * ch)
    d = ImageDraw.Draw(sheet)
    d.text((16, 16), "Planeta de Ámbar — bloques y plantas (texturas 16×16)", fill=(25, 35, 45, 255), font=F)
    for i, (key, top, side) in enumerate(cubes):
        x0, y0 = (i % cols) * cw, 70 + (i // cols) * ch
        a, b = face_sprites(tex[top], 34), face_sprites(tex[side], 34)
        cubeimg = Image.new("RGBA", (68, 102), (0, 0, 0, 0))
        for part in (a["top"], b["left"], b["right"]):
            cubeimg.alpha_composite(part, (0, 0))
        sheet.alpha_composite(cubeimg, (x0 + 14, y0 + 12))
        sheet.alpha_composite(tex[side].resize((64, 64), Image.NEAREST), (x0 + 92, y0 + 30))
        d.text((x0 + 10, y0 + 128), LABELS[key], fill=(20, 30, 40, 255), font=f)
    base = 70 + math.ceil(len(cubes) / cols) * ch
    for i, key in enumerate(sprites):
        x0, y0 = (i % cols) * cw, base + (i // cols) * ch
        if key == "tall_grass":
            col = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
            col.alpha_composite(tex["tall_grass_top"], (0, 0))
            col.alpha_composite(tex["tall_grass_bottom"], (0, 16))
            sheet.alpha_composite(col.resize((56, 112), Image.NEAREST), (x0 + 55, y0 + 8))
        else:
            sheet.alpha_composite(tex[key].resize((96, 96), Image.NEAREST), (x0 + 36, y0 + 20))
        d.text((x0 + 10, y0 + 128), LABELS[key], fill=(20, 30, 40, 255), font=f)
    sheet.save(out)


def biome_sheet(tex, out):
    F, Fm, f = fonts()
    biomes = [(AP.tall_prairie, "Pradera de pasto alto"), (AP.short_prairie, "Pradera de pasto bajo"),
              (AP.giant_forest, "Bosque gigante"), (AP.small_forest, "Bosque de árboles pequeños"),
              (AP.swamp, "Pantano"), (AP.ocean_river, "Océano, costa y río")]
    cw, ch, cols = 760, 860, 3
    sheet = sky(cols * cw, 60 + 2 * ch)
    d = ImageDraw.Draw(sheet)
    d.text((16, 14), "Planeta de Ámbar — biomas con texturas (concepto)", fill=(25, 35, 45, 255), font=F)
    for i, (fn, title) in enumerate(biomes):
        x0, y0 = (i % cols) * cw, 60 + (i // cols) * ch
        sheet.alpha_composite(render_textured(fn(), tex, s=8, size=(760, 860), oy=430), (x0, y0 + 10))
        d.text((x0 + 16, y0 + 6), title, fill=(25, 35, 45, 255), font=Fm)
    sheet.save(out)


def closeup(tex, out):
    """Un trozo pequeño de pradera alta y bosque pequeño, de cerca, para ver bien las texturas."""
    w = AP.World(9)
    AP.W, AP.D = 18, 18
    hm = AP.ground(w, 6, 1.2, 0.2, "grass", 21)
    AP.plants(w, hm, "tall", 0.35)
    AP.plants(w, hm, "flower", 0.08)
    AP.small_tree(w, 12, 5, hm[12, 5], "small_leaf")
    AP.small_tree(w, 5, 12, hm[5, 12], "small_leaf_b")
    AP.W, AP.D = 44, 44
    img = render_textured(w, tex, s=28, size=(1040, 900), oy=430)
    bg = sky(img.width, img.height)
    bg.alpha_composite(img)
    bg.save(out)


def tiling_sheet(tex, out):
    """Comprobación de continuidad: cada textura de bloque repetida 3×3 (se verían las costuras), el pasto alto entero
    y la liana tres bloques seguidos."""
    F, Fm, f = fonts()
    keys = ["amber_grass_top", "amber_short_grass_block_top", "amber_grass_side", "amber_soil", "amber_stone", "amber_sand",
            "amber_mud", "silverwood_log", "silverwood_log_top", "silverwood_leaves", "duskwood_log", "duskwood_leaves",
            "duskwood_leaves_blue", "mirewood_log", "mirewood_leaves", "bush_leaves"]
    k, cw, ch, cols = 4, 3 * 16 * 4 + 20, 3 * 16 * 4 + 40, 6
    rows = math.ceil(len(keys) / cols) + 1
    sheet = Image.new("RGBA", (cols * cw, 60 + rows * ch), (60, 70, 80, 255))
    d = ImageDraw.Draw(sheet)
    d.text((16, 14), "Cerúlea — continuidad de las texturas (3×3 repetidas)", fill=(240, 240, 240, 255), font=F)
    for i, key in enumerate(keys):
        x0, y0 = (i % cols) * cw + 10, 60 + (i // cols) * ch
        t = tex[key].resize((64, 64), Image.NEAREST)
        for a in range(3):
            for b in range(3):
                sheet.alpha_composite(t, (x0 + a * 64, y0 + 20 + b * 64))
        d.text((x0, y0 + 2), key, fill=(230, 230, 230, 255), font=f)
    y0 = 60 + (rows - 1) * ch
    tg = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    tg.alpha_composite(tex["tall_grass_top"], (0, 0))
    tg.alpha_composite(tex["tall_grass_bottom"], (0, 16))
    sheet.alpha_composite(tg.resize((64, 128), Image.NEAREST), (20, y0 + 20))
    d.text((20, y0 + 2), "tall_grass (arriba+abajo)", fill=(230, 230, 230, 255), font=f)
    vn = Image.new("RGBA", (16, 48), (0, 0, 0, 0))
    for b in range(3):
        vn.alpha_composite(tex["mire_vine"], (0, 16 * b))
    sheet.alpha_composite(vn.resize((64, 192), Image.NEAREST).crop((0, 0, 64, 150)), (cw + 20, y0 + 20))
    d.text((cw + 20, y0 + 2), "mire_vine ×3", fill=(230, 230, 230, 255), font=f)
    for j, key in enumerate(["short_grass", "azure_flower", "glow_flower", "glow_mushroom", "azure_lily"]):
        sheet.alpha_composite(tex[key].resize((96, 96), Image.NEAREST), (2 * cw + 20 + j * 110, y0 + 40))
        d.text((2 * cw + 20 + j * 110, y0 + 2), key, fill=(230, 230, 230, 255), font=f)
    sheet.save(out)


def main(out_dir):
    os.makedirs(os.path.join(out_dir, "textures"), exist_ok=True)
    tex = make_textures()
    for k, v in tex.items():
        v.save(os.path.join(out_dir, "textures", k + ".png"))
    texture_sheet(tex, os.path.join(out_dir, "amber_texturas.png"))
    biome_sheet(tex, os.path.join(out_dir, "amber_biomas_texturas.png"))
    closeup(tex, os.path.join(out_dir, "amber_de_cerca.png"))
    tiling_sheet(tex, os.path.join(out_dir, "amber_continuidad.png"))
    print("ok", out_dir)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "amber_out")
