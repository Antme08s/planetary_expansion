"""Fulgor (1.0.4): luna de tormentas eléctricas del gigante Aurelia. Texturas 16×16 de los bloques.

Uso: python3 -I tools/designs/fulgor_textures.py <carpeta_salida>             (hoja de vista previa en <salida>)
     python3 -I tools/designs/fulgor_textures.py --export tools/designs/fulgor  (texturas del juego en fulgor/block)

Mismo estilo que Titán: poco ruido en bucle, tonos limitados, bordes suaves. Basalto azul oscuro con tinte violeta, polvo
de tormenta gris violáceo y fulgurita (el vidrio que funde el rayo) con vetas luminosas. Las menas reutilizan las vetas de
cerulea_food_ores sobre el basalto.
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


def basalt(rng):
    """Basalto de tormenta: azul grisáceo muy oscuro con tinte violeta y alguna fisura clara."""
    img = AT.edges(_calm(rng, AT.ramp((30, 32, 48), (88, 90, 120), 6), ((4, 0.5), (2, 0.5)), 0.07), 0.86)
    px = img.load()
    x, y = rng.randrange(T), rng.randrange(T)
    for _ in range(6):                                  # fisura corta violeta
        px[x % T, y % T] = rgba((112, 100, 150))
        x += rng.choice((1, 1, 0))
        y += rng.choice((0, 1, -1))
    return img


def basalt_bricks(rng):
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
                c = (16, 16, 28)
            else:
                c = shade((64, 66, 92), tone[bx] * (0.93 + 0.14 * tex[y, x]))
                if y % 4 == 0:
                    c = shade(c, 1.12)
            px[x, y] = rgba(c)
    return img


def polished_basalt(rng):
    f = AT.pnoise(rng, ((8, 0.5), (4, 0.5)))
    img = AT.posterize(f * 0.4 + 0.3, AT.ramp((54, 56, 80), (92, 96, 128), 5), rng, 0.04)
    d = ImageDraw.Draw(img)
    d.line([(0, 0), (T - 1, 0)], fill=rgba((128, 132, 170)))
    d.line([(0, 0), (0, T - 1)], fill=rgba((118, 122, 158)))
    d.line([(0, T - 1), (T - 1, T - 1)], fill=rgba((24, 24, 40)))
    d.line([(T - 1, 0), (T - 1, T - 1)], fill=rgba((28, 28, 44)))
    d.rectangle([4, 4, 11, 11], outline=rgba((44, 46, 70)))
    return img


def storm_dust(rng):
    """Polvo de tormenta: ceniza gris violácea, granos finos."""
    img = _calm(rng, AT.ramp((70, 66, 88), (150, 144, 172), 6), ((4, 0.45), (2, 0.55)), 0.10)
    px = img.load()
    for _ in range(4):
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba((176, 170, 198))
    return img


def fulgurite(rng):
    """Fulgurita: vidrio blanco violáceo fundido por rayos, con una vena ramificada luminosa."""
    img = _calm(rng, AT.ramp((150, 140, 196), (236, 232, 255), 6), ((4, 0.45), (2, 0.55)), 0.05, 0.1, 0.9)
    px = img.load()
    # vena que baja en zigzag desde arriba con una rama: colores brillantes (la luz del bloque es 9)
    x = rng.randrange(5, 11)
    for y in range(T):
        px[x % T, y] = rgba((255, 250, 170))
        if y % 3 == 0:
            px[(x + 1) % T, y] = rgba((255, 224, 110))
        x += rng.choice((-1, 0, 0, 1))
        if y == 7:
            bx = x
            for k in range(1, 6):
                bx += rng.choice((1, 1, 0))
                px[bx % T, (y + k) % T] = rgba((255, 236, 140))
    return img


def make_textures():
    rng = random.Random(8112)
    t = {}
    t["fulgor_basalt"] = basalt(rng)
    t["fulgor_basalt_bricks"] = basalt_bricks(rng)
    t["polished_fulgor_basalt"] = polished_basalt(rng)
    t["storm_dust"] = storm_dust(rng)
    t["fulgurite"] = fulgurite(rng)
    for name in ("redstone", "copper", "zinc"):
        cols, shape = CF.ORES[name]
        t[f"fulgor_{name}_ore"] = CF.ore(random.Random(zlib.crc32(name.encode()) + 11), t["fulgor_basalt"], cols, shape)
    return t


LABELS = {"fulgor_basalt": "Basalto", "fulgor_basalt_bricks": "Ladrillos", "polished_fulgor_basalt": "Pulido",
          "storm_dust": "Polvo", "fulgurite": "Fulgurita", "fulgor_redstone_ore": "Redstone",
          "fulgor_copper_ore": "Cobre", "fulgor_zinc_ore": "Zinc"}


def sheet(tex, out):
    keys = list(LABELS)
    cols, cw = 4, 120
    rows = math.ceil(len(keys) / cols)
    img = Image.new("RGBA", (cols * cw, rows * 140 + 20), (18, 16, 26, 255))
    d = ImageDraw.Draw(img)
    for i, k in enumerate(keys):
        x0, y0 = (i % cols) * cw, 10 + (i // cols) * 140
        img.alpha_composite(tex[k].resize((96, 96), Image.NEAREST), (x0 + 12, y0))
        d.text((x0 + 6, y0 + 102), LABELS[k], fill=(230, 226, 245, 255))
    tile = Image.new("RGBA", (cols * cw, 3 * 32 + 8), (18, 16, 26, 255))
    for i, k in enumerate(("fulgor_basalt", "storm_dust", "fulgurite", "fulgor_copper_ore")):
        for ty in range(3):
            for tx in range(3):
                tile.alpha_composite(tex[k].resize((32, 32), Image.NEAREST), (i * cw + 8 + tx * 32, 4 + ty * 32))
    full = Image.new("RGBA", (img.width, img.height + tile.height), (18, 16, 26, 255))
    full.paste(img, (0, 0))
    full.paste(tile, (0, img.height))
    full.save(out)


def export(out_dir):
    os.makedirs(os.path.join(out_dir, "block"), exist_ok=True)
    for k, im in make_textures().items():
        im.save(os.path.join(out_dir, "block", k + ".png"))


if __name__ == "__main__":
    if sys.argv[1] == "--export":
        export(sys.argv[2])
    else:
        os.makedirs(sys.argv[1], exist_ok=True)
        sheet(make_textures(), os.path.join(sys.argv[1], "fulgor_texturas.png"))
