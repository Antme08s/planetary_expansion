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

T = 16


def rgba(c, a=255):
    return (int(c[0]), int(c[1]), int(c[2]), a)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3])


def noise_tile(rng, base, var=0.08, dots=(), n_dots=0):
    """Ruido de píxel suave con motas de otros colores."""
    img = Image.new("RGBA", (T, T))
    px = img.load()
    for y in range(T):
        for x in range(T):
            px[x, y] = rgba(shade(base, 1 + rng.uniform(-var, var)))
    for _ in range(n_dots):
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba(rng.choice(dots))
    return img


# ------------------------------------------------------------------ bloques

def grass_top(rng, base, light, dark, blue):
    img = noise_tile(rng, base, 0.06)
    px = img.load()
    for _ in range(46):
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba(light if rng.random() < 0.55 else dark)
    for _ in range(5):
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba(blue)
    return img


def soil(rng):
    base = (74, 64, 82)
    return noise_tile(rng, base, 0.07, dots=[(56, 48, 66), (96, 86, 104), (62, 80, 96)], n_dots=34)


def grass_side(rng, top, soil_img, light, dark):
    img = soil_img.copy()
    px = img.load()
    for x in range(T):
        depth = 3 + (1 if rng.random() < 0.5 else 0) + (1 if rng.random() < 0.25 else 0)
        for y in range(depth):
            c = top if rng.random() < 0.7 else (light if rng.random() < 0.5 else dark)
            px[x, y] = rgba(shade(c, 1 + rng.uniform(-0.06, 0.06)))
    return img


def stone(rng):
    img = noise_tile(rng, (104, 110, 122), 0.05, dots=[(84, 90, 102), (124, 130, 142)], n_dots=60)
    d = ImageDraw.Draw(img)
    for _ in range(3):                                  # grietas finas
        x, y = rng.randrange(T), rng.randrange(T)
        for _ in range(rng.randint(3, 6)):
            d.point((x, y), fill=rgba((76, 82, 94)))
            x += rng.choice((-1, 0, 1))
            y += rng.choice((0, 1))
    return img


def pale_sand(rng):
    return noise_tile(rng, (198, 204, 198), 0.04, dots=[(176, 184, 182), (214, 220, 214), (150, 170, 176)], n_dots=40)


def mud(rng):
    return noise_tile(rng, (58, 72, 70), 0.07, dots=[(44, 56, 56), (74, 92, 88)], n_dots=40)


def bark(rng, base, dark, light, streak=True):
    img = noise_tile(rng, base, 0.04)
    px = img.load()
    for x in range(T):
        if streak and rng.random() < 0.35:              # vetas verticales
            c = dark if rng.random() < 0.7 else light
            y0 = rng.randrange(T)
            for y in range(y0, y0 + rng.randint(4, 12)):
                px[x, y % T] = rgba(shade(c, 1 + rng.uniform(-0.05, 0.05)))
    return img


def silver_bark(rng):
    img = bark(rng, (196, 206, 214), (150, 162, 176), (226, 232, 238))
    px = img.load()
    for _ in range(4):                                  # "ojos" horizontales como en la corteza de abedul, en gris azulado
        x, y = rng.randrange(T - 3), rng.randrange(T)
        for k in range(rng.randint(2, 4)):
            px[x + k, y] = rgba((110, 124, 146))
    return img


def log_top(rng, outer, ring_a, ring_b):
    img = Image.new("RGBA", (T, T))
    px = img.load()
    for y in range(T):
        for x in range(T):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d > 6.5:
                c = outer
            else:
                c = ring_a if int(d) % 2 == 0 else ring_b
            px[x, y] = rgba(shade(c, 1 + rng.uniform(-0.05, 0.05)))
    return img


def leaves(rng, base, light, dark, holes=0.03, glow=None, glow_n=0):
    """Hojas en racimos, como las de Minecraft: fondo en sombra y encima racimos redondos con su lado de luz (arriba a
    la izquierda) y su sombra (abajo a la derecha). Los racimos que se salen por un borde entran por el contrario, así
    el bloque se repite sin costuras. Algunos huecos pequeños en la sombra."""
    img = Image.new("RGBA", (T, T))
    px = img.load()
    for y in range(T):
        for x in range(T):
            px[x, y] = rgba(shade(dark, 1 + rng.uniform(-0.05, 0.05)))
    for _ in range(14):
        cx, cy, r = rng.uniform(0, T), rng.uniform(0, T), rng.uniform(1.6, 2.8)
        for dy in range(-3, 4):
            for dx in range(-3, 4):
                d2 = dx * dx + dy * dy
                if d2 > r * r:
                    continue
                x, y = math.floor(cx + dx) % T, math.floor(cy + dy) % T
                if dx + dy <= -2:
                    c = light
                elif dx + dy >= 2:
                    c = shade(base, 0.86)
                else:
                    c = base
                px[x, y] = rgba(shade(c, 1 + rng.uniform(-0.04, 0.04)))
    for _ in range(int(holes * T * T)):
        x, y = rng.randrange(T), rng.randrange(T)
        if px[x, y][:3] == rgba(dark)[:3] or rng.random() < 0.3:
            px[x, y] = (0, 0, 0, 0)
    for _ in range(glow_n):
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba(glow)
    return img


# ------------------------------------------------------------------ plantas (sprites en cruz)

def blades(rng, cols, n, hmin, hmax, height=T, tip=None):
    """Briznas continuas: cada una sale del borde de abajo y sube sin cortes, curvándose poco a poco hacia un lado;
    abajo es más oscura y gruesa (2 px) y se aclara y afina hacia la punta."""
    img = Image.new("RGBA", (T, height), (0, 0, 0, 0))
    px = img.load()
    xs = sorted(rng.sample(range(1, T - 1), min(n, T - 2)))
    for x0 in xs:
        h = rng.randint(hmin, hmax)
        bend = rng.uniform(-3.0, 3.0)
        c = rng.choice(cols)
        for k in range(h):
            f = k / max(1, h - 1)
            x = int(round(x0 + bend * f * f))
            y = height - 1 - k
            if not (0 <= x < T and 0 <= y < height):
                break
            col = shade(c, 0.72 + 0.4 * f)
            if tip and k >= h - 2:
                col = tip
            px[x, y] = rgba(col)
            if f < 0.35 and 0 <= x + 1 < T:                  # base más gruesa
                px[x + 1, y] = rgba(shade(c, 0.66 + 0.4 * f))
    return img


def tall_grass_pair(rng):
    """El pasto alto se dibuja entero en 16×32 y luego se parte: así las briznas siguen sin cortes de un bloque al otro."""
    cols = [(70, 200, 170), (58, 176, 152), (92, 214, 186)]
    full = blades(rng, cols, 9, 18, 31, height=2 * T, tip=(150, 236, 216))
    return full.crop((0, T, T, 2 * T)), full.crop((0, 0, T, T))


def short_grass(rng):
    return blades(rng, [(110, 206, 160), (90, 186, 146), (130, 220, 176)], 8, 5, 11)


def flower(rng, petal, center, stem=(60, 150, 120), glow=False):
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.line([(7, 15), (7, 7)], fill=rgba(stem))
    d.line([(7, 12), (5, 10)], fill=rgba(stem))                 # hoja
    d.line([(8, 11), (10, 9)], fill=rgba(stem))
    for dx, dy in ((0, -2), (-2, 0), (2, 0), (0, 2), (-1, -1), (1, -1), (-1, 1), (1, 1)):
        d.point((7 + dx, 5 + dy), fill=rgba(petal if abs(dx) + abs(dy) == 2 else shade(petal, 1.15)))
    d.point((7, 5), fill=rgba(center))
    if glow:
        d.point((7, 3), fill=rgba(shade(petal, 1.25)))
    return img


def glow_mushroom(rng):
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([7, 9, 8, 15], fill=rgba((200, 222, 222)))
    d.ellipse([3, 4, 12, 10], fill=rgba((90, 230, 215)))
    d.rectangle([3, 8, 12, 9], fill=rgba((60, 180, 175)))
    for x, y in ((5, 6), (9, 5), (10, 7), (6, 8)):
        d.point((x, y), fill=rgba((200, 255, 248)))
    d.rectangle([12, 12, 13, 15], fill=rgba((200, 222, 222)))  # hongo pequeño
    d.rectangle([11, 10, 14, 12], fill=rgba((90, 230, 215)))
    return img


def vine(rng):
    """Tallos continuos que ondulan con periodo 16 (la liana de un bloque empalma con la del de abajo) y hojitas a
    los lados alternadas."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    for x0, ph in ((2, 0.0), (7, 2.1), (12, 4.2)):
        for y in range(T):
            x = x0 + int(round(1.2 * math.sin(2 * math.pi * y / T + ph)))
            px[x, y] = rgba(shade((50, 150, 132), 1 + 0.08 * math.sin(y)))
            if (y + int(ph * 3)) % 4 == 0:                    # hoja a la izquierda o a la derecha
                side = -1 if (y // 4) % 2 else 1
                for k in (1, 2):
                    xx = x + side * k
                    if 0 <= xx < T:
                        px[xx, y] = rgba((84, 196, 170) if k == 1 else (110, 214, 186))
                if 0 <= x + side < T and y + 1 < T:
                    px[x + side, y + 1] = rgba((70, 180, 156))
    return img


def lily(rng):
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([1, 2, 14, 14], fill=rgba((60, 110, 200)))
    d.polygon([(8, 8), (14, 5), (14, 9)], fill=(0, 0, 0, 0))   # corte de la hoja
    for _ in range(14):
        x, y = rng.randrange(3, 13), rng.randrange(4, 13)
        if img.getpixel((x, y))[3]:
            d.point((x, y), fill=rgba((84, 140, 222) if rng.random() < 0.6 else (44, 86, 170)))
    d.point((6, 7), fill=rgba((200, 240, 255)))                # florecita blanca
    return img


def bush_leaves(rng):
    return leaves(rng, (46, 140, 120), (66, 168, 140), (30, 104, 92), holes=0.0)


def make_textures():
    rng = random.Random(2026)
    t = {}
    t["amber_soil"] = soil(rng)
    for name, base, light, dark in (("amber_grass", (77, 176, 138), (104, 202, 160), (56, 146, 116)),
                                    ("amber_short_grass_block", (104, 196, 150), (134, 220, 176), (82, 168, 128))):
        t[name + "_top"] = grass_top(rng, base, light, dark, (90, 150, 255))
        t[name + "_side"] = grass_side(rng, base, t["amber_soil"], light, dark)
    t["amber_stone"] = stone(rng)
    t["amber_sand"] = pale_sand(rng)
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
