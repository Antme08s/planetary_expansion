"""Ferra, luna de óxido de Cerúlea (1.0.3): texturas 16×16 y vistas previas (propuesta, aún no en el mod).

Uso: python3 -I tools/designs/ferra_textures.py <carpeta_salida>
     python3 -I tools/designs/ferra_textures.py --export tools/designs/ferra   (texturas del juego en ferra/block)
  - <salida>/ferra_texturas.png: bloques (planos y como cubo);
  - <salida>/ferra_biomas.png: los cuatro biomas en isométrico, con Cerúlea en el cielo;
  - <salida>/textures/*.png.
Mismas herramientas que Cerúlea y Velo (amber_textures, cerulea_food_ores, velo_textures).
"""
import math
import os
import random
import sys
import zlib

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import amber_planet_preview as AP  # noqa: E402
import amber_textures as AT  # noqa: E402
import calm_textures as CT  # noqa: E402
import cerulea_food_ores as CF  # noqa: E402
import velo_textures as VT  # noqa: E402

T = 16
rgba, shade = AT.rgba, AT.shade


# ------------------------------------------------------------------ bloques

def dust(rng):
    """Polvo de óxido: naranja rojizo, granos finos y alguna mota oscura de hierro."""
    f = AT.pnoise(rng, ((4, 0.45), (2, 0.55)))
    img = AT.posterize(f * 0.8 + 0.2, AT.ramp((132, 56, 30), (214, 116, 64), 5), rng, 0.12)
    px = img.load()
    for _ in range(6):
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba((96, 40, 26))
    for _ in range(3):
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba((232, 150, 90))
    return img


def rock(rng):
    """Roca de Ferra: marrón óxido oscuro, con grietas y vetas naranjas."""
    f = AT.pnoise(rng)
    img = AT.edges(AT.posterize(f, AT.ramp((64, 34, 28), (132, 74, 52), 5), rng, 0.07))
    px = img.load()
    x, y = rng.randrange(T), rng.randrange(T)
    for _ in range(10):
        px[x % T, y % T] = rgba((176, 84, 40))               # veta de óxido
        x += rng.choice((1, 1, 0)); y += rng.choice((0, 1, -1))
    return img


def ochre(rng):
    """Roca ocre en estratos (paredes de los cañones): bandas horizontales que ondulan, de ocre a rojo."""
    bands = [(196, 120, 58), (176, 92, 46), (150, 70, 40), (204, 140, 76), (128, 58, 36), (186, 104, 52)]
    img = Image.new("RGBA", (T, T))
    px = img.load()
    f = AT.pnoise(rng, ((4, 0.5), (2, 0.5)))
    for y in range(T):
        for x in range(T):
            yy = y + 0.9 * math.sin(x * math.pi / 8)
            c = bands[int(yy // 2.7) % len(bands)]
            px[x, y] = rgba(shade(c, 0.92 + 0.16 * f[y, x]))
    return AT.edges(img, 0.88)


def verdigris(rng):
    """Pátina de cobre (orillas de las charcas): verde azulado con costras claras."""
    f = AT.pnoise(rng, ((4, 0.5), (2, 0.5)))
    img = AT.edges(AT.posterize(f, AT.ramp((40, 112, 96), (132, 214, 182), 5), rng, 0.1))
    px = img.load()
    for _ in range(5):
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba((176, 236, 210)); px[(x + 1) % T, y] = rgba((150, 220, 196))
    for _ in range(3):
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba((150, 82, 46))                       # restos de cobre sin oxidar
    return img


def iron_vein(rng):
    """Veta de hierro (bloque grande en los muros de las mesetas): placas metálicas grises con óxido en las juntas."""
    img = Image.new("RGBA", (T, T))
    px = img.load()
    seeds = [(rng.uniform(0, T), rng.uniform(0, T)) for _ in range(7)]
    tones = [rng.choice([(150, 146, 150), (128, 124, 130), (170, 166, 168), (112, 108, 114)]) for _ in seeds]
    for y in range(T):
        for x in range(T):
            ds = []
            for n, (sx, sy) in enumerate(seeds):
                dx = (x + 0.5 - sx + T / 2) % T - T / 2
                dy = (y + 0.5 - sy + T / 2) % T - T / 2
                ds.append((dx * dx + dy * dy, n, dx, dy))
            ds.sort()
            d0, n, dx, dy = ds[0]
            if math.sqrt(ds[1][0]) - math.sqrt(d0) < 0.9:
                c = (160, 74, 36) if rng.random() < 0.7 else (110, 50, 30)
            else:
                k = 1.14 if dx + dy < -2.5 else 0.86 if dx + dy > 2 else 1.0
                c = shade(tones[n], k)
            px[x, y] = rgba(c)
    return img


def bricks(rng):
    img = Image.new("RGBA", (T, T))
    px = img.load()
    tex = AT.pnoise(rng, ((4, 0.5), (2, 0.5)))
    tone = {}
    for y in range(T):
        for x in range(T):
            row = y // 4
            off = 4 if row % 2 else 0
            bx = ((x + off) // 8, row)
            tone.setdefault(bx, rng.uniform(0.92, 1.08))
            if y % 4 == 3 or (x + off) % 8 == 7:
                c = (58, 30, 24)
            else:
                c = shade((126, 66, 46), tone[bx] * (0.92 + 0.16 * tex[y, x]))
                if y % 4 == 0: c = shade(c, 1.1)
            px[x, y] = rgba(c)
    return img


def polished(rng):
    f = AT.pnoise(rng, ((8, 0.5), (4, 0.5)))
    img = AT.posterize(f * 0.4 + 0.3, AT.ramp((106, 56, 40), (146, 82, 58), 5), rng, 0.04)
    d = ImageDraw.Draw(img)
    d.line([(0, 0), (T - 1, 0)], fill=rgba((168, 98, 70))); d.line([(0, 0), (0, T - 1)], fill=rgba((158, 90, 64)))
    d.line([(0, T - 1), (T - 1, T - 1)], fill=rgba((66, 34, 26))); d.line([(T - 1, 0), (T - 1, T - 1)], fill=rgba((74, 38, 30)))
    d.rectangle([4, 4, 11, 11], outline=rgba((96, 50, 36)))
    return img


def make_textures():
    rng = random.Random(5151)
    t = {}
    t["ferra_dust"] = CT.calmed(dust, CT.ferra_dust, rng, 9105)
    t["ferra_rock"] = CT.calmed(rock, CT.ferra_rock, rng, 9106)
    t["ferra_rock_bricks"] = bricks(rng)
    t["polished_ferra_rock"] = polished(rng)
    t["ferra_ochre"] = ochre(rng)
    t["ferra_verdigris"] = verdigris(rng)
    t["ferra_iron_vein"] = iron_vein(rng)
    for name in ["iron", "copper", "gold", "redstone"]:
        cols, shape = CF.ORES[name]
        t[f"ferra_{name}_ore"] = CF.ore(random.Random(zlib.crc32(name.encode()) + 3), t["ferra_rock"], cols, shape)
    cols, shape = VT.titanium_colors()
    t["ferra_titanium_ore"] = CF.ore(random.Random(98), t["ferra_rock"], cols, shape)
    return t


LABELS = {"ferra_dust": "Polvo de óxido", "ferra_rock": "Roca de Ferra", "ferra_rock_bricks": "Ladrillos de Ferra",
          "polished_ferra_rock": "Roca de Ferra pulida", "ferra_ochre": "Roca ocre (estratos)",
          "ferra_verdigris": "Pátina de cobre", "ferra_iron_vein": "Veta de hierro",
          "ferra_iron_ore": "Hierro", "ferra_copper_ore": "Cobre", "ferra_gold_ore": "Oro", "ferra_redstone_ore": "Redstone",
          "ferra_titanium_ore": "Titanio"}


# ------------------------------------------------------------------ terreno

def patch_renderer():
    AT.CUBES = {k: (v, v) for k, v in {"dust": "ferra_dust", "rock": "ferra_rock", "ochre": "ferra_ochre", "verd": "ferra_verdigris",
                                       "vein": "ferra_iron_vein", "ore_i": "ferra_iron_ore", "ore_c": "ferra_copper_ore"}.items()}
    AT.SPRITES = {}
    AT.GLOWING = set()
    AT.WATER = {"water": (84, 186, 156)}


def rust_plains():
    w, hm = VT.terrain(51, 8, 1.4, 0.15)
    r = random.Random(1)
    for _ in range(8):
        x, z = r.randrange(3, AP.W - 3), r.randrange(3, AP.D - 3)
        AP.blob(w, x, hm[x, z] + 1, z, r.uniform(1, 2.4), r.uniform(0.8, 1.8), r.uniform(1, 2.4), "rock")
    return w


def canyons():
    """Meseta cortada por un cañón profundo y sinuoso; paredes en estratos ocres y fondo de polvo."""
    w = AP.World(61)
    n = AP.noise2(61, 0.12, 0.12)
    for x in range(AP.W):
        for z in range(AP.D):
            c = 22 + 5 * math.sin(x * 0.18) + 2.5 * n(x, z)          # centro del cañón (serpentea)
            d = abs(z - c)
            top = 22 + int(round(1.2 * n(x, z)))
            h = top if d > 7 else int(round(6 + (top - 6) * max(0.0, (d - 3) / 4))) if d > 3 else 6
            for y in range(h + 1):
                if y == h:
                    w.set(x, y, z, "dust")
                elif y >= 4:
                    w.set(x, y, z, "ochre")
                else:
                    w.set(x, y, z, "rock")
    return w


def copper_pools():
    w, hm = VT.terrain(71, 7, 1.6, 0.13)
    n = AP.noise2(9, 0.16, 0.16)
    lvl = 7
    for x in range(AP.W):
        for z in range(AP.D):
            if n(x, z) > 0.25:                                        # charca: hundida y llena de agua verde
                h = lvl - 1 - int(2 * (n(x, z) - 0.25))
                for y in range(h + 1, 20):
                    w.v.pop((x, y, z), None)
                w.set(x, h, z, "verd")
                for y in range(h + 1, lvl + 1):
                    w.set(x, y, z, "water")
            elif n(x, z) > 0.1:                                       # orilla de pátina
                w.set(x, hm[x, z], z, "verd")
    return w


def iron_mesas():
    """Chimeneas y mesetas de roca con vetas de hierro a la vista."""
    w, hm = VT.terrain(81, 7, 1.0, 0.14)
    r = random.Random(5)
    for (x, z, hgt, rad) in [(12, 12, 14, 3.2), (30, 16, 10, 2.4), (18, 31, 17, 2.8), (34, 33, 8, 3.6), (7, 26, 6, 2.0), (26, 6, 9, 2.2)]:
        for k in range(hgt):
            rr = rad * (1.0 if k < hgt - 2 else 0.8) + (0.6 if k < 2 else 0)
            for dx in range(-4, 5):
                for dz in range(-4, 5):
                    if dx * dx + dz * dz <= rr * rr:
                        c = "vein" if r.random() < 0.12 else "ore_i" if r.random() < 0.06 else "ochre" if (k // 3) % 2 else "rock"
                        w.set(x + dx, hm[x, z] + 1 + k, z + dz, c)
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                if dx * dx + dz * dz <= (rad * 0.9) ** 2:
                    w.set(x + dx, hm[x, z] + hgt, z + dz, "dust")
    return w


def red_sky(wd, ht, rng):
    """Cielo de Ferra: casi negro arriba y una bruma rojiza de polvo en el horizonte."""
    arr = np.zeros((ht, wd, 4), np.uint8)
    t = np.linspace(0, 1, ht)[:, None]
    top, hor = np.array([10, 8, 12]), np.array([96, 42, 28])
    for i in range(3):
        arr[..., i] = (top[i] * (1 - t ** 2) + hor[i] * t ** 2).astype(np.uint8)
    arr[..., 3] = 255
    img = Image.fromarray(arr, "RGBA")
    d = ImageDraw.Draw(img)
    for _ in range(int(wd * ht / 1400)):
        x, y = rng.randrange(wd), rng.randrange(int(ht * 0.6))
        b = rng.randint(80, 220)
        d.point((x, y), fill=(b, b, b, 255))
    return img


def texture_sheet(tex, out):
    F, Fm, f = AT.fonts()
    keys = list(LABELS)
    cw, ch, cols = 170, 170, 6
    rows = math.ceil(len(keys) / cols)
    sheet = red_sky(cols * cw, 70 + rows * ch, random.Random(1))
    d = ImageDraw.Draw(sheet)
    d.text((16, 16), "Ferra, luna de óxido — bloques (texturas 16×16)", fill=(240, 228, 220, 255), font=F)
    for i, key in enumerate(keys):
        x0, y0 = (i % cols) * cw, 70 + (i // cols) * ch
        a = AT.face_sprites(tex[key], 34)
        cube = Image.new("RGBA", (68, 102), (0, 0, 0, 0))
        for part in (a["top"], a["left"], a["right"]): cube.alpha_composite(part)
        sheet.alpha_composite(cube, (x0 + 10, y0 + 8))
        sheet.alpha_composite(tex[key].resize((64, 64), Image.NEAREST), (x0 + 90, y0 + 24))
        d.text((x0 + 10, y0 + 122), LABELS[key], fill=(235, 225, 218, 255), font=f)
    sheet.save(out)


def biome_sheet(tex, out):
    F, Fm, f = AT.fonts()
    patch_renderer()
    biomes = [(rust_plains, "Llanuras de óxido"), (canyons, "Cañones de óxido"),
              (copper_pools, "Charcas de cobre"), (iron_mesas, "Chimeneas de hierro")]
    cw, ch, cols = 760, 760, 2
    sheet = red_sky(cols * cw, 60 + 2 * ch, random.Random(2))
    VT.cerulea_disc(sheet, cols * cw - 170, 200, 120)
    d = ImageDraw.Draw(sheet)
    d.text((16, 14), "Ferra — biomas (concepto con las texturas; Cerúlea en el cielo)", fill=(240, 228, 220, 255), font=F)
    for i, (fn, title) in enumerate(biomes):
        x0, y0 = (i % cols) * cw, 60 + (i // cols) * ch
        sheet.alpha_composite(AT.render_textured(fn(), tex, s=8, size=(760, 760), oy=420), (x0, y0 + 10))
        d.text((x0 + 16, y0 + 6), title, fill=(240, 228, 220, 255), font=Fm)
    sheet.save(out)


def main(out_dir):
    os.makedirs(os.path.join(out_dir, "textures"), exist_ok=True)
    tex = make_textures()
    for k, im in tex.items(): im.save(os.path.join(out_dir, "textures", k + ".png"))
    texture_sheet(tex, os.path.join(out_dir, "ferra_texturas.png"))
    biome_sheet(tex, os.path.join(out_dir, "ferra_biomas.png"))


def export(out_dir):
    """Texturas del juego (nombres de los bloques) en <out_dir>/block."""
    os.makedirs(os.path.join(out_dir, "block"), exist_ok=True)
    for k, im in make_textures().items():
        im.save(os.path.join(out_dir, "block", k + ".png"))


if __name__ == "__main__":
    if sys.argv[1] == "--export":
        export(sys.argv[2])
    else:
        main(sys.argv[1])
