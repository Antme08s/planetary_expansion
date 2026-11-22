"""Titán (1.0.4): luna de Saturno. Texturas 16×16 de los bloques, del metano líquido y de la cubeta.

Uso: python3 -I tools/designs/titan_textures.py <carpeta_salida>             (hoja de vista previa en <salida>)
     python3 -I tools/designs/titan_textures.py --export tools/designs/titan  (texturas del juego en titan/block y titan/item)

Estilo de la 1.0.4: poco ruido en bucle (nada de motas fuertes que se vean repetidas al teselar), tonos limitados, bordes
suaves. Las menas usan las vetas de cerulea_food_ores (piezas dibujadas, no las de Minecraft) sobre el hielo de roca.
"""
import math
import os
import random
import sys
import zlib

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import amber_textures as AT  # noqa: E402
import cerulea_food_ores as CF  # noqa: E402

T = 16
rgba, shade = AT.rgba, AT.shade


def _calm(rng, palette, octaves=((4, 0.5), (2, 0.5)), jitter=0.06, lo=0.15, hi=0.85):
    f = AT.pnoise(rng, octaves)
    return AT.posterize(f * (hi - lo) + lo, palette, rng, jitter)


def tholin_sand(rng):
    """Arena de tolina: anaranjada y marrón, granos finos, sin motas que destaquen."""
    img = _calm(rng, AT.ramp((112, 64, 32), (206, 134, 68), 6), ((4, 0.45), (2, 0.55)), 0.10)
    px = img.load()
    for _ in range(4):                                  # unos pocos granos más claros, repartidos sin patrón
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba((222, 156, 88))
    return img


def tholin_crust(rng):
    """Corteza de tolina: marrón rojizo oscuro, grumosa, con poros pequeños."""
    img = AT.edges(_calm(rng, AT.ramp((58, 34, 28), (134, 82, 50), 6), ((4, 0.5), (2, 0.5)), 0.08), 0.84)
    px = img.load()
    for _ in range(5):
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba((38, 22, 20))                    # poro
        if rng.random() < 0.5:
            px[(x + 1) % T, y] = rgba((84, 52, 38))
    return img


def ice_rock(rng):
    """Hielo de roca: gris azulado duro con un tinte cálido de tolina en las fisuras."""
    img = AT.edges(_calm(rng, AT.ramp((62, 74, 92), (152, 168, 184), 6), ((4, 0.5), (2, 0.5)), 0.07), 0.86)
    px = img.load()
    x, y = rng.randrange(T), rng.randrange(T)
    for _ in range(7):                                  # fisura corta con un tinte anaranjado
        px[x % T, y % T] = rgba((128, 104, 96))
        x += rng.choice((1, 1, 0))
        y += rng.choice((0, 1, -1))
    return img


def ice_bricks(rng):
    img = Image.new("RGBA", (T, T))
    px = img.load()
    tex = AT.pnoise(rng, ((4, 0.5), (2, 0.5)))
    tone = {}
    for y in range(T):
        for x in range(T):
            row = y // 4
            off = 4 if row % 2 else 0
            bx = ((x + off) // 8, row)
            tone.setdefault(bx, rng.uniform(0.93, 1.07))
            if y % 4 == 3 or (x + off) % 8 == 7:
                c = (34, 42, 56)
            else:
                c = shade((104, 120, 140), tone[bx] * (0.93 + 0.14 * tex[y, x]))
                if y % 4 == 0:
                    c = shade(c, 1.1)
            px[x, y] = rgba(c)
    return img


def polished_ice(rng):
    f = AT.pnoise(rng, ((8, 0.5), (4, 0.5)))
    img = AT.posterize(f * 0.4 + 0.3, AT.ramp((92, 106, 126), (132, 150, 170), 5), rng, 0.04)
    from PIL import ImageDraw as D
    d = D.Draw(img)
    d.line([(0, 0), (T - 1, 0)], fill=rgba((170, 186, 204)))
    d.line([(0, 0), (0, T - 1)], fill=rgba((160, 176, 194)))
    d.line([(0, T - 1), (T - 1, T - 1)], fill=rgba((52, 62, 78)))
    d.line([(T - 1, 0), (T - 1, T - 1)], fill=rgba((58, 68, 84)))
    d.rectangle([4, 4, 11, 11], outline=rgba((84, 98, 118)))
    return img


def bitumen(rng):
    """Bitumen: negro brillante, grumoso, con reflejos azulados."""
    img = AT.edges(_calm(rng, AT.ramp((10, 9, 12), (54, 46, 54), 6), ((4, 0.5), (2, 0.5)), 0.06), 0.8)
    px = img.load()
    for _ in range(4):
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba((92, 96, 118))                   # brillo
    return img


SULFUR = ([(232, 214, 72), (255, 244, 150), (176, 150, 36)], "chunks")


def methane_still(rng):
    """Metano líquido: azul verdoso muy oscuro con reflejos anaranjados difusos (el cielo de Titán)."""
    f = AT.pnoise(rng, ((8, 0.5), (4, 0.3), (2, 0.2)))
    img = AT.posterize(f, AT.ramp((18, 44, 54), (52, 104, 112), 6), rng, 0.05)
    px = img.load()
    for _ in range(5):
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba((120, 150, 130))
        px[(x + 1) % T, y] = rgba((92, 128, 122))
    return img


def methane_flow(rng):
    """Corriente: la misma tinta con vetas verticales (el agua sube y baja por el sprite)."""
    img = methane_still(rng)
    px = img.load()
    for x in range(0, T, 3):
        for y in range(T):
            if (y + x) % 5 < 2:
                r, g, b, a = px[x, y]
                px[x, y] = rgba(shade((r, g, b), 1.28))
    return img


def make_textures():
    rng = random.Random(6021)
    t = {}
    t["tholin_sand"] = tholin_sand(rng)
    t["tholin_crust"] = tholin_crust(rng)
    t["titan_ice_rock"] = ice_rock(rng)
    t["titan_ice_rock_bricks"] = ice_bricks(rng)
    t["polished_titan_ice_rock"] = polished_ice(rng)
    t["bitumen"] = bitumen(rng)
    for name, key in (("coal", "coal"), ("redstone", "redstone"), ("lapis", "lapis")):
        cols, shape = CF.ORES[key]
        t[f"titan_{name}_ore"] = CF.ore(random.Random(zlib.crc32(name.encode()) + 7), t["titan_ice_rock"], cols, shape)
    cols, shape = SULFUR
    t["titan_sulfur_ore"] = CF.ore(random.Random(51), t["titan_ice_rock"], cols, shape)
    t["methane_still"] = methane_still(random.Random(77))
    t["methane_flow"] = methane_flow(random.Random(78))
    return t


LABELS = {"tholin_sand": "Arena de tolina", "tholin_crust": "Corteza de tolina", "titan_ice_rock": "Hielo de roca",
          "titan_ice_rock_bricks": "Ladrillos de hielo", "polished_titan_ice_rock": "Hielo pulido", "bitumen": "Bitumen",
          "titan_coal_ore": "Carbón", "titan_redstone_ore": "Redstone", "titan_lapis_ore": "Lapislázuli",
          "titan_sulfur_ore": "Azufre", "methane_still": "Metano (quieto)", "methane_flow": "Metano (corriente)"}


# ------------------------------------------------------------------ cubeta de metano (item)

def methane_bucket(vanilla_dir):
    """Cubeta de metano a partir de la cubeta de agua de Minecraft: el agua pasa a tonos del metano."""
    src = Image.open(os.path.join(vanilla_dir, "assets/minecraft/textures/item/water_bucket.png")).convert("RGBA")
    px = src.load()
    ramp = AT.ramp((14, 38, 48), (86, 140, 142), 5)
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = px[x, y]
            if a and b > r + 40 and b > g - 10:           # píxel azul de agua
                k = (r + g + b) / 3 / 255
                px[x, y] = rgba(ramp[max(0, min(4, int(k * 6)))])
    return src


def sheet(tex, out):
    keys = list(LABELS)
    cols, cw = 6, 120
    rows = math.ceil(len(keys) / cols)
    img = Image.new("RGBA", (cols * cw, rows * 140 + 20), (22, 16, 12, 255))
    d = ImageDraw.Draw(img)
    for i, k in enumerate(keys):
        x0, y0 = (i % cols) * cw, 10 + (i // cols) * 140
        img.alpha_composite(tex[k].resize((96, 96), Image.NEAREST), (x0 + 12, y0))
        d.text((x0 + 6, y0 + 102), LABELS[k], fill=(235, 220, 200, 255))
    # teselado 3x3 de cada tipo de bloque, para revisar las repeticiones
    tile = Image.new("RGBA", (cols * cw, 3 * 48 + 8), (22, 16, 12, 255))
    for i, k in enumerate(("tholin_sand", "tholin_crust", "titan_ice_rock", "bitumen", "titan_coal_ore", "titan_sulfur_ore")):
        for ty in range(3):
            for tx in range(3):
                tile.alpha_composite(tex[k].resize((32, 32), Image.NEAREST), (i * cw + 8 + tx * 32, 4 + ty * 32 + 0))
    full = Image.new("RGBA", (img.width, img.height + tile.height), (22, 16, 12, 255))
    full.paste(img, (0, 0))
    full.paste(tile, (0, img.height))
    full.save(out)


def export(out_dir, vanilla_dir=None):
    os.makedirs(os.path.join(out_dir, "block"), exist_ok=True)
    os.makedirs(os.path.join(out_dir, "item"), exist_ok=True)
    for k, im in make_textures().items():
        im.save(os.path.join(out_dir, "block", k + ".png"))
    if vanilla_dir:
        methane_bucket(vanilla_dir).save(os.path.join(out_dir, "item", "methane_bucket.png"))


if __name__ == "__main__":
    if sys.argv[1] == "--export":
        export(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
    else:
        os.makedirs(sys.argv[1], exist_ok=True)
        sheet(make_textures(), os.path.join(sys.argv[1], "titan_texturas.png"))
