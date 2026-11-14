"""Plantas del mar de Cerúlea (1.0.3, 2026-11-13): texturas y vista previa en 3D bajo el agua.

Uso: python3 -I tools/designs/cerulea_sea_plants.py <carpeta_salida>

- Alga de cinta (ribbon_kelp / ribbon_kelp_plant): como el kelp, crece en columna; hojas anchas en cinta que ondulan,
  verde azulado con borde azul y vejigas de aire claras. El tallo se repite cada 16 px (empalma sin cortes).
- Pasto marino cerúleo (cerulean_seagrass) y alto (tall_cerulean_seagrass_bottom/top, dibujado entero en 16×32).
- Tubos luminosos (glow_tubes): grupo de esponjas en tubo con la boca que brilla (luz 10).
- Abanico azul (azure_fan): abanico de red con ramas azules y puntas claras.
- Alga de perlas (pearl_weed / pearl_weed_plant, con _lit si tiene fruto): idea del usuario, "como las bayas luminosas
  pero marinas"; crece en columna y algunos trozos llevan racimos de perlas que brillan (luz 14) y se cosechan.
Segunda versión (a pedido del usuario): más detalle en todas.
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import amber_plants_3d as P3  # noqa: E402
import amber_textures as AT  # noqa: E402
from amber_textures import T, rgba, shade  # noqa: E402


def ribbon_kelp(rng, tip=False):
    """Tallo central con nudos que ondula un poco y hojas anchas en cinta (4 px) alternadas cada 8 px, que suben hacia
    fuera como el kelp que flota. Cada hoja: degradado de la base (oscura) a la punta (clara), nervio claro, borde azul
    arriba y sombra abajo, ondas a lo largo; vejiga de aire con brillo en la base. El tallo repite cada 16 px; la punta
    deja de crecer arriba y termina en una hoja nueva enrollada."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    stem_l, stem, stem_d = (64, 140, 116), (38, 108, 94), (24, 74, 70)
    leaf_d, leaf, leaf_l, rib, edge = (36, 112, 104), (56, 156, 130), (90, 196, 158), (140, 222, 186), (46, 98, 172)
    bladder, bl_hi = (150, 226, 218), (230, 255, 250)
    top = 5 if tip else -99

    def put(x, y, c):
        y %= T
        if 0 <= x < T and y >= top:
            px[x, y] = rgba(c)
    sx = lambda y: 7 + int(round(0.8 * math.sin(2 * math.pi * y / T)))  # noqa: E731
    for y0, side in ((3, 1), (11, -1)):
        for k in range(10):
            f = k / 9
            x = sx(y0) + side * (1 + int(round(k * 0.62)))
            y = y0 - int(round(k * 0.75)) + int(round(1.2 * math.sin(f * math.pi * 1.5)))
            base = AT_mix(leaf_d, leaf_l, f)
            wave = 1.0 + 0.08 * math.sin(k * 1.7)
            put(x, y, edge if k > 1 else shade(base, 0.9))                       # borde de arriba
            put(x, y + 1, shade(base, 1.08 * wave))
            put(x, y + 2, rib if k % 3 else shade(rib, 0.88))                   # nervio
            put(x, y + 3, shade(base, 0.92 * wave))
            put(x, y + 4, shade(leaf_d, 0.85) if k < 8 else shade(base, 0.8))   # sombra de abajo
            if k == 9:
                put(x + side, y + 2, shade(leaf_l, 1.15))                      # punta redondeada
        put(sx(y0) + side, y0 + 2, bladder)
        put(sx(y0) + side, y0 + 3, shade(bladder, 0.78))
        put(sx(y0) + side * 2, y0 + 2, shade(bladder, 0.9))
        put(sx(y0) + side, y0 + 1, bl_hi)
    for y in range(T):                                                           # tallo con nudos
        x = sx(y)
        node = y % 8 == 4
        put(x, y, shade(stem_l if not node else stem, 1 + 0.06 * math.sin(y * 1.3)))
        put(x + 1, y, stem_d if not node else shade(stem_d, 0.8))
        if node:
            put(x - 1, y, stem)
    if tip:                                                                      # hoja nueva enrollada
        x = sx(5)
        put(x, 4, leaf)
        put(x + 1, 4, leaf_d)
        put(x, 3, leaf_l)
        put(x + 1, 3, rib)
        put(x, 2, shade(leaf_l, 1.15))
    return img


def AT_mix(a, b, k):
    return tuple(int(a[i] * (1 - k) + b[i] * k) for i in range(3))


def blades3(rng, cols, n, hmin, hmax, height=T, bubbles=0):
    """Briznas de pasto marino: finas (1 px, base de 2 px solo abajo), ondulan como si las moviera el agua, degradado
    de oscuro abajo a claro arriba con la punta más clara, una línea de luz en el tramo medio de algunas, y burbujitas."""
    img = Image.new("RGBA", (T, height), (0, 0, 0, 0))
    px = img.load()
    slots = list(range(1, T - 1, 2))                       # separadas: así se ven briznas y no una pared
    xs = sorted(rng.sample(slots, min(n, len(slots))))
    for x0 in xs:
        h = rng.randint(hmin, hmax)
        ph = rng.uniform(0, math.pi * 2)
        amp = rng.uniform(0.8, 1.8)
        c = rng.choice(cols)
        lit = rng.random() < 0.5
        prev = None
        for k in range(h):
            f = k / max(1, h - 1)
            x = int(round(x0 + amp * math.sin(ph + f * 3.2) * f))
            y = height - 1 - k
            if not (0 <= x < T and 0 <= y < height):
                break
            col = shade(c, 0.62 + 0.5 * f)
            if f > 0.88:
                col = shade(c, 1.28)
            px[x, y] = rgba(col)
            if prev is not None and abs(prev - x) == 1 and y + 1 < height:
                px[x, y + 1] = rgba(shade(col, 0.9))           # sin huecos en las curvas
            if f < 0.22 and x + 1 < T:
                px[x + 1, y] = rgba(shade(c, 0.5 + 0.3 * f))   # base un poco más gruesa
            if lit and 0.35 < f < 0.75 and x - 1 >= 0 and px[x - 1, y][3] == 0:
                px[x - 1, y] = rgba(shade(c, 1.05 + 0.25 * f))
            prev = x
    for _ in range(bubbles):
        x, y = rng.randint(1, T - 2), rng.randint(1, height - 8)
        if px[x, y][3] == 0:
            px[x, y] = rgba((200, 245, 250))
            if y + 1 < height and px[x, y + 1][3] == 0:
                px[x, y + 1] = rgba((150, 220, 235))
    return img


SEAGRASS_COLS = [(54, 160, 140), (44, 140, 132), (70, 180, 160), (48, 132, 150), (62, 170, 128)]


def seagrass(rng):
    return blades3(rng, SEAGRASS_COLS, 6, 6, 13, bubbles=2)


def tall_seagrass(rng):
    full = blades3(rng, SEAGRASS_COLS, 6, 16, 31, height=2 * T, bubbles=3)
    return full.crop((0, T, T, 2 * T)), full.crop((0, 0, T, T))


def glow_tubes(rng):
    """Esponjas en tubo de distinta altura: tubo con degradado (luz a la izquierda), motas y anillos de crecimiento,
    boca abierta con el borde que brilla más claro por fuera y el fondo oscuro, y pólipos diminutos en la base."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    body_l, body, body_d = (88, 156, 176), (60, 124, 150), (38, 86, 112)
    rim_o, rim_i, deep = (190, 255, 244), (120, 236, 222), (24, 70, 84)
    for x0, h, w in ((1, 9, 3), (5, 13, 4), (10, 7, 3), (12, 11, 3)):
        for k in range(h):
            y = T - 1 - k
            for dx in range(w):
                x = x0 + dx
                if not 0 <= x < T:
                    continue
                c = body_l if dx == 0 else (body_d if dx == w - 1 else body)
                c = shade(c, 0.9 + 0.12 * (k / h))
                if k % 4 == 2:
                    c = shade(c, 0.8)                              # anillo de crecimiento
                if rng.random() < 0.12 and 0 < dx < w - 1:
                    c = shade(c, 1.15)                             # motas
                px[x, y] = rgba(c)
        top = T - h
        for dx in range(w):
            x = x0 + dx
            if not 0 <= x < T:
                continue
            outer = dx in (0, w - 1)
            px[x, top - 1] = rgba(rim_o if outer else rim_i)          # borde que brilla
            px[x, top] = rgba(shade(rim_i, 0.85) if outer else deep)  # fondo oscuro de la boca
        if w >= 4:
            px[x0 + 1, top + 1] = rgba(shade(deep, 1.3))
    for x in (0, 4, 9, 15):                                            # pólipos de la base
        if 0 <= x < T and px[x, T - 1][3] == 0:
            px[x, T - 1] = rgba((120, 210, 200))
            if x + 1 < T and px[x, T - 2][3] == 0:
                px[x, T - 2] = rgba((170, 240, 226))
    return img


def azure_fan(rng):
    """Abanico de red: ramas gruesas abajo que se afinan hacia arriba, con degradado (claro arriba a la izquierda),
    travesaños de la red más finos y oscuros, nudos, y pólipos claros en las puntas y en algunos nudos."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    dark, mid, light, tipc, polyp = (36, 70, 150), (56, 104, 186), (92, 146, 222), (170, 214, 252), (210, 236, 255)
    base = (7.5, 15)
    ends = [(1, 3), (3, 1), (6, 0), (9, 0), (12, 1), (14, 3), (15, 7), (0, 7)]
    paths = []
    for ex, ey in ends:
        path = []
        for i in range(40):
            f = i / 39
            x = base[0] + (ex - base[0]) * f ** 0.8 + 0.6 * math.sin(f * 6 + ex)
            y = base[1] + (ey - base[1]) * f
            path.append((int(round(x)), int(round(y)), f))
        paths.append(path)
    for path in paths:                                   # ramas: más gruesas abajo
        for x, y, f in path:
            if 0 <= x < T and 0 <= y < T:
                c = AT_mix(dark, light, min(1.0, f * 1.1 + (0.2 if x < 8 else 0)))
                px[x, y] = rgba(c)
                if f < 0.35 and x + 1 < T:
                    px[x + 1, y] = rgba(shade(dark, 0.85))
    for row in (4, 6, 8, 10, 12):                         # travesaños de la red
        xs = sorted(x for path in paths for (x, y, f) in path if y == row)
        if not xs:
            continue
        for x in range(xs[0], xs[-1] + 1):
            if 0 <= x < T and px[x, row][3] == 0 and (x + row) % 2 == 0:
                px[x, row] = rgba(shade(mid, 0.8))
    for ex, ey in ends:                                    # pólipos en las puntas
        if 0 <= ex < T and 0 <= ey < T:
            px[ex, ey] = rgba(polyp)
    for x, y in ((4, 5), (11, 5), (7, 3), (2, 9), (13, 9), (8, 8)):
        if px[x, y][3]:
            px[x, y] = rgba(tipc)
    return img


def pearl_weed(rng, berries=False, tip=False):
    """Alga de perlas (como las bayas luminosas, pero del mar): tallo fino que sube enroscándose, hojitas en pares y,
    en los trozos con fruto, racimos de perlas que brillan (un brillo blanco, cuerpo turquesa y sombra). Repite cada 16
    px; la punta termina en un brote."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    stem, stem_d, leaf, leaf_l = (52, 128, 104), (32, 90, 80), (72, 170, 136), (110, 204, 166)
    pearl, pearl_l, pearl_d, pearl_hi = (110, 232, 220), (170, 252, 240), (60, 170, 176), (255, 255, 255)
    top = 4 if tip else -99

    def put(x, y, c):
        y %= T
        if 0 <= x < T and y >= top:
            px[x, y] = rgba(c)
    sx = lambda y: 7 + int(round(2.0 * math.sin(2 * math.pi * y / T)))  # noqa: E731
    for y in range(T):
        x = sx(y)
        put(x, y, stem)
        put(x + 1, y, stem_d)
        if y % 5 == 1:                                    # hojitas en pares
            for side in (-1, 1):
                xx = x + (side if side < 0 else 2)
                put(xx, y, leaf_l if side < 0 else leaf)
                put(xx + side, y - 1, leaf)
    if berries:
        # racimos de perlas: cada perla 2×2 (brillo blanco, cuerpo turquesa claro, cuerpo, sombra), colgando de un rabito
        for (cy, side) in ((4, -1), (11, 1)):
            sx0 = sx(cy)
            cx = sx0 - 4 if side < 0 else sx0 + 3
            for ox, oy in ((0, 0), (2, 1), (0, 2), (2, 3), (1, 4)):
                x, y = cx + ox, cy + oy
                put(x, y, pearl_hi)
                put(x + 1, y, pearl_l)
                put(x, y + 1, pearl)
                put(x + 1, y + 1, pearl_d)
            for k in range(1, 3):
                put(sx0 + (side * k if side < 0 else 1 + k), cy, stem_d)       # rabito
    if tip:
        x = sx(4)
        put(x, 4, leaf_l)
        put(x + 1, 4, leaf)
        put(x, 3, shade(leaf_l, 1.15))
    return img


def dried_kelp_item(rng):
    """Alga de cinta seca: tira de alga oscura y arrugada en zigzag, con brillos y bordes."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    base, dark, light = (44, 78, 70), (26, 48, 46), (78, 118, 100)
    pts = []
    for k in range(22):
        f = k / 21
        x = 2 + f * 11 + 1.6 * math.sin(f * 9)
        y = 13 - f * 11 + 1.2 * math.cos(f * 9)
        pts.append((x, y))
    for x, y in pts:
        for dx, dy, c in ((0, 0, base), (1, 0, dark), (0, -1, light)):
            xx, yy = int(round(x)) + dx, int(round(y)) + dy
            if 0 <= xx < T and 0 <= yy < T and (px[xx, yy][3] == 0 or c != light):
                px[xx, yy] = rgba(shade(c, 1 + rng.uniform(-0.06, 0.06)))
    for k in range(0, 22, 4):                          # arrugas
        x, y = pts[k]
        if 0 <= int(x) < T and 0 <= int(y) < T:
            px[int(x), int(y)] = rgba(dark)
    return img


def glow_pearls_item(rng):
    """Perlas luminosas: racimo de 5 perlas con su brillo, en un rabito con una hojita."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    stem, leaf = (52, 128, 104), (90, 186, 150)
    pearl, pearl_l, pearl_d, hi = (110, 232, 220), (170, 252, 240), (52, 160, 168), (255, 255, 255)
    for k in range(5):
        px[8 + k // 2, 1 + k] = rgba(stem)
    px[11, 2] = rgba(leaf)
    px[12, 2] = rgba(leaf)
    px[12, 1] = rgba(shade(leaf, 1.15))
    for cx, cy, r in ((5.5, 8.5, 2.4), (10.5, 8.0, 2.4), (8.0, 11.8, 2.6), (4.2, 12.6, 1.9), (12.2, 12.4, 1.9)):
        for y in range(T):
            for x in range(T):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / r
                if d <= 1:
                    k = (x + 0.5 - cx) + (y + 0.5 - cy)
                    c = pearl_l if k < -1 else (pearl_d if k > 1.2 else pearl)
                    if d > 0.82 and k > 0:
                        c = shade(pearl_d, 0.8)
                    px[x, y] = rgba(c)
        px[int(cx - r * 0.4), int(cy - r * 0.4)] = rgba(hi)
    return img


def dried_kelp_block(rng):
    """Bloque de alga de cinta seca: costado con tiras verticales atadas con dos cuerdas; arriba y abajo, el rollo
    visto de frente (espiral)."""
    side = Image.new("RGBA", (T, T))
    sp = side.load()
    cols = [(40, 74, 66), (50, 88, 76), (34, 62, 58), (58, 98, 84)]
    for x in range(T):
        c = cols[(x * 7 + 3) % 4]
        for y in range(T):
            k = 1 + 0.07 * math.sin(y * 1.3 + x * 2.1) + rng.uniform(-0.04, 0.04)
            sp[x, y] = rgba(shade(c, k * (0.85 if x % 4 == 3 else 1.0)))
    for y0 in (3, 12):                                   # cuerdas
        for x in range(T):
            sp[x, y0] = rgba((150, 140, 100) if x % 3 else (120, 110, 76))
            sp[x, y0 + 1] = rgba((110, 100, 70))
    top = Image.new("RGBA", (T, T))
    tp = top.load()
    for y in range(T):
        for x in range(T):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            band = (r - a / (2 * math.pi) * 2.6) % 2.6
            c = (46, 82, 72) if band < 1.4 else (30, 56, 54)
            tp[x, y] = rgba(shade(c, 1 + rng.uniform(-0.05, 0.05)))
    return side, top


def make():
    rng = random.Random(4242)
    tex = {
        "ribbon_kelp_plant": ribbon_kelp(rng, False),
        "ribbon_kelp": ribbon_kelp(rng, True),
        "cerulean_seagrass": seagrass(rng),
        "glow_tubes": glow_tubes(rng),
        "azure_fan": azure_fan(rng),
        "pearl_weed_plant": pearl_weed(rng, False),
        "pearl_weed_plant_lit": pearl_weed(rng, True),
        "pearl_weed": pearl_weed(rng, False, True),
        "pearl_weed_lit": pearl_weed(rng, True, True),
    }
    tex["tall_cerulean_seagrass_bottom"], tex["tall_cerulean_seagrass_top"] = tall_seagrass(rng)
    tex["_sand"] = AT.pale_sand(random.Random(7))
    irng = random.Random(77)
    tex["@dried_ribbon_kelp"] = dried_kelp_item(irng)
    tex["@glow_pearls"] = glow_pearls_item(irng)
    tex["dried_ribbon_kelp_block_side"], tex["dried_ribbon_kelp_block_top"] = dried_kelp_block(irng)
    return tex


# ------------------------------------------------------------------ vista previa

def underwater(img, depth_tint=(28, 96, 120), k=0.28):
    arr = np.asarray(img).astype(float)
    a = arr[..., 3:4] / 255.0
    arr[..., :3] = arr[..., :3] * (1 - k) + np.array(depth_tint) * k
    arr[..., 3:4] = a * 255
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


def bg(w, h):
    g = np.linspace(0, 1, h)[:, None]
    top, bot = np.array((70, 170, 190)), np.array((14, 52, 76))
    arr = np.zeros((h, w, 4), np.uint8)
    arr[..., :3] = (top * (1 - g[..., None]) + bot * g[..., None]).astype(np.uint8)[:, :, :]
    arr[..., 3] = 255
    im = Image.fromarray(arr, "RGBA")
    rays = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(rays)
    for i in range(max(3, w // 160)):   # rayos de luz
        x = (i * 157 + 60) % w
        d.line([(x, 0), (x - 140, h)], fill=(160, 230, 240, 22), width=18)
    im.alpha_composite(rays.filter(ImageFilter.GaussianBlur(6)))
    return im


def scene_panel(tex, kind):
    sc = P3.Scene()
    sand = tex["_sand"]
    if kind == "kelp":
        sc.cube(0, -1, 0, sand, sand)
        for y in range(4):
            sc.cross(0, y, 0, tex["ribbon_kelp_plant"])
        sc.cross(0, 4, 0, tex["ribbon_kelp"])
    elif kind == "seagrass":
        sc.cube(0, -1, 0, sand, sand)
        sc.cross(0, 0, 0, tex["cerulean_seagrass"])
    elif kind == "tall_seagrass":
        sc.cube(0, -1, 0, sand, sand)
        sc.cross(0, 0, 0, tex["tall_cerulean_seagrass_bottom"])
        sc.cross(0, 1, 0, tex["tall_cerulean_seagrass_top"])
    elif kind == "glow_tubes":
        sc.cube(0, -1, 0, sand, sand)
        sc.cross(0, 0, 0, tex["glow_tubes"], glow=True)
    elif kind == "fan":
        sc.cube(0, -1, 0, sand, sand)
        sc.cross(0, 0, 0, tex["azure_fan"])
    elif kind == "pearl":
        sc.cube(0, -1, 0, sand, sand)
        for y, lit in ((0, False), (1, True), (2, False), (3, True)):
            sc.cross(0, y, 0, tex["pearl_weed_plant_lit" if lit else "pearl_weed_plant"], glow=lit)
        sc.cross(0, 4, 0, tex["pearl_weed_lit"], glow=True)
    return sc


def seabed(tex):
    """Un trozo de fondo de 7×7 con todo mezclado, como se vería al bucear."""
    sc = P3.Scene()
    sand = tex["_sand"]
    rnd = random.Random(11)
    for x in range(7):
        for z in range(7):
            sc.cube(x, -1, z, sand, sand)
            r = rnd.random()
            if r < 0.18:
                h = rnd.randint(2, 5)
                for y in range(h):
                    sc.cross(x, y, z, tex["ribbon_kelp_plant"])
                sc.cross(x, h, z, tex["ribbon_kelp"])
            elif r < 0.45:
                sc.cross(x, 0, z, tex["cerulean_seagrass"])
            elif r < 0.58:
                sc.cross(x, 0, z, tex["tall_cerulean_seagrass_bottom"])
                sc.cross(x, 1, z, tex["tall_cerulean_seagrass_top"])
            elif r < 0.66:
                sc.cross(x, 0, z, tex["glow_tubes"], glow=True)
            elif r < 0.74:
                sc.cross(x, 0, z, tex["azure_fan"])
            elif r < 0.82:
                h = rnd.randint(2, 4)
                for y in range(h):
                    lit = rnd.random() < 0.4
                    sc.cross(x, y, z, tex["pearl_weed_plant_lit" if lit else "pearl_weed_plant"], glow=lit)
                sc.cross(x, h, z, tex["pearl_weed_lit" if rnd.random() < 0.5 else "pearl_weed"], glow=True)
    return sc


def build(out):
    os.makedirs(out, exist_ok=True)
    tex = make()
    for k, v in tex.items():
        if not k.startswith("_") and not k.startswith("@"):
            v.save(os.path.join(out, f"{k}.png"))
    # texturas finales para el juego: tools/designs/cerulea/{block,item}
    game = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cerulea")
    for k, v in tex.items():
        if k.startswith("_"):
            continue
        if k.startswith("@"):
            v.save(os.path.join(game, "item", k[1:] + ".png"))
        else:
            v.save(os.path.join(game, "block", k + ".png"))
    from PIL import ImageFont
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 15)
    W, H = 1800, 1260
    sheet = Image.new("RGBA", (W, H), (16, 26, 34, 255))
    d = ImageDraw.Draw(sheet)
    names = [("kelp", "Alga de cinta (crece en columna)"), ("seagrass", "Pasto marino cerúleo"),
             ("tall_seagrass", "Pasto marino alto"), ("glow_tubes", "Tubos luminosos (luz 10)"), ("fan", "Abanico azul"),
             ("pearl", "Alga de perlas (NUEVA, luz 14)")]
    P3.SCALE = 74
    for i, (kind, title) in enumerate(names):
        panel_img = bg(290, 560)
        r = scene_panel(tex, kind).render((290, 560), (145, 470))
        panel_img.alpha_composite(underwater(r))
        sheet.alpha_composite(panel_img, (10 + i * 298, 40))
        d.text((16 + i * 298, 12), title, fill=(220, 240, 236, 255), font=font)
    # fondo completo
    P3.SCALE = 58
    big = bg(1780, 600)
    r = seabed(tex).render((1780, 600), (700, 330))
    big.alpha_composite(underwater(r, k=0.22))
    sheet.alpha_composite(big, (10, 640))
    d.text((16, 612), "Fondo del mar de Cerúlea con todo mezclado", fill=(220, 240, 236, 255), font=font)
    sheet.save(os.path.join(out, "sea_plants_preview.png"))
    # hoja de texturas grandes
    keys = ["ribbon_kelp", "ribbon_kelp_plant", "cerulean_seagrass", "tall_cerulean_seagrass_top",
            "tall_cerulean_seagrass_bottom", "glow_tubes", "azure_fan", "pearl_weed", "pearl_weed_plant",
            "pearl_weed_plant_lit", "@dried_ribbon_kelp", "@glow_pearls", "dried_ribbon_kelp_block_side",
            "dried_ribbon_kelp_block_top"]
    ts = Image.new("RGBA", (len(keys) * 140 + 10, 170), (40, 70, 86, 255))
    dd = ImageDraw.Draw(ts)
    for i, k in enumerate(keys):
        ts.alpha_composite(tex[k].resize((128, 128), Image.NEAREST), (10 + i * 140, 10))
        dd.text((10 + i * 140, 146), k, fill=(230, 240, 240, 255))
    # tallo repetido para ver que empalma
    col = Image.new("RGBA", (16, 64))
    for j in range(3):
        col.alpha_composite(tex["ribbon_kelp_plant"], (0, 16 + j * 16))
    col.alpha_composite(tex["ribbon_kelp"], (0, 0))
    ts2 = Image.new("RGBA", (ts.width + 140, 270), (40, 70, 86, 255))
    ts2.alpha_composite(ts, (0, 0))
    ts2.alpha_composite(col.resize((64, 256), Image.NEAREST), (ts.width + 30, 6))
    ts2.save(os.path.join(out, "sea_plants_textures.png"))
    print("ok", out)


if __name__ == "__main__":
    build(sys.argv[1])
