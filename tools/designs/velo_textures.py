"""Velo, luna de cristal de Cerúlea (1.0.3): texturas 16×16 y vistas previas (propuesta, aún no en el mod).

Uso: python3 -I tools/designs/velo_textures.py <carpeta_salida>
     python3 -I tools/designs/velo_textures.py --export tools/designs/velo   (texturas del juego en velo/block)
  - <salida>/velo_texturas.png: bloques y cristales (planos a 4× y como cubo);
  - <salida>/velo_biomas.png: los cuatro biomas en isométrico, con Cerúlea en el cielo;
  - <salida>/textures/*.png: cada textura.
Mismas herramientas que Cerúlea (amber_textures: pnoise, posterize, edges; cerulea_food_ores: ore, crystal_ore).
"""
import math
import os
import random
import sys
import zlib

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import amber_planet_preview as AP  # noqa: E402
import amber_textures as AT  # noqa: E402
import calm_textures as CT  # noqa: E402
import cerulea_food_ores as CF  # noqa: E402

T = 16
rgba, shade = AT.rgba, AT.shade

CRYSTAL = CF.CRYSTAL   # (brillo, claro, medio, oscuro, hondo): la misma paleta que el cristal cerúleo de Cerúlea


# ------------------------------------------------------------------ bloques

def dust(rng):
    """Polvo de Velo: blanco con un toque lavanda, granos finos y alguna chispa de cristal."""
    f = AT.pnoise(rng, ((4, 0.45), (2, 0.55)))
    img = AT.posterize(f * 0.8 + 0.2, AT.ramp((198, 200, 214), (246, 246, 252), 5), rng, 0.12)
    px = img.load()
    for _ in range(5):
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba((176, 178, 196))
    for _ in range(2):
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba(CRYSTAL[1])
    return img


def rock(rng):
    """Roca de Velo: gris azulada clara, con grietas finas."""
    f = AT.pnoise(rng)
    img = AT.edges(AT.posterize(f, AT.ramp((118, 126, 146), (186, 192, 208), 5), rng, 0.07))
    px = img.load()
    x, y = rng.randrange(T), rng.randrange(T)
    for _ in range(9):                                      # una grieta que serpentea
        px[x % T, y % T] = rgba((96, 102, 122))
        x += rng.choice((1, 1, 0)); y += rng.choice((0, 1, 1, -1))
    return img


def basalt(rng):
    """Basalto vítreo de los mares: casi negro azulado, en capas, con brillos de vidrio."""
    f = AT.pnoise(rng, ((8, 0.4), (4, 0.35), (2, 0.25)))
    for y in range(T):
        f[y, :] = 0.6 * f[y, :] + 0.4 * (0.5 + 0.5 * np.sin(y * math.pi / 4 + 0.8 * np.sin(np.arange(T) * math.pi / 8)))
    img = AT.edges(AT.posterize(f, AT.ramp((20, 22, 38), (62, 66, 96), 5), rng, 0.06))
    px = img.load()
    for _ in range(4):                                      # destellos de vidrio
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba((120, 150, 200))
        px[(x + 1) % T, y] = rgba((70, 90, 140))
    return img


def basalt_top(rng):
    """Cara de arriba del basalto: liso y oscuro, con grietas hexagonales tenues."""
    f = AT.pnoise(rng, ((8, 0.6), (4, 0.4)))
    img = AT.posterize(f * 0.6 + 0.2, AT.ramp((24, 26, 44), (58, 62, 92), 4), rng, 0.05)
    px = img.load()
    for (x, y) in [(3, 2), (11, 4), (6, 9), (13, 12), (2, 13)]:
        for k in range(3):
            px[(x + k) % T, y] = rgba((14, 14, 26))
            px[x, (y + k) % T] = rgba((14, 14, 26))
    px[9, 7] = rgba((120, 150, 200))
    return img


def crystal_block(rng, budding=False):
    """Bloque de cristal cerúleo: facetas (celdas de Voronoi envueltas, sin costuras), cada una con su tono; arista
    brillante arriba a la izquierda de cada faceta y sombra abajo a la derecha. El brotante lleva huecos oscuros con
    un brote asomando."""
    hl, light, mid, dark, deep = CRYSTAL
    seeds = [(rng.uniform(0, T), rng.uniform(0, T)) for _ in range(8)]
    tones = [rng.choice([light, mid, mid, dark, (60, 150, 222)]) for _ in seeds]
    img = Image.new("RGBA", (T, T))
    px = img.load()
    for y in range(T):
        for x in range(T):
            ds = []
            for n, (sx, sy) in enumerate(seeds):
                dx = (x + 0.5 - sx + T / 2) % T - T / 2
                dy = (y + 0.5 - sy + T / 2) % T - T / 2
                ds.append((dx * dx + dy * dy, n, dx, dy))
            ds.sort()
            d0, n, dx, dy = ds[0]
            gap = math.sqrt(ds[1][0]) - math.sqrt(d0)
            c = tones[n]
            if gap < 0.9:
                c = hl if dx + dy < 0 else deep                # arista: brilla del lado de la luz
            elif dx + dy < -3:
                c = shade(c, 1.12)
            elif dx + dy > 3:
                c = shade(c, 0.85)
            px[x, y] = rgba(c)
    if budding:
        for (x, y) in [(3, 3), (11, 5), (6, 11), (13, 13)]:
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                px[(x + dx) % T, (y + dy) % T] = rgba((12, 26, 64))
            px[x, y] = rgba(hl)
    return img


def crystal_sprite(stage):
    """Brotes de cristal (planta en cruz): 0 pequeño, 1 mediano, 2 grande, 3 racimo."""
    hl, light, mid, dark, deep = CRYSTAL
    img = Image.new("RGBA", (T, T))
    px = img.load()
    spikes = {0: [(8, 4, 2)], 1: [(7, 7, 2), (10, 4, 1)], 2: [(8, 11, 3), (4, 6, 2), (12, 7, 2)],
              3: [(8, 14, 3), (4, 10, 2), (12, 11, 2), (6, 6, 1), (11, 5, 1)]}[stage]
    for (cx, h, w) in spikes:
        top = 15 - h
        for y in range(top, 16):
            half = w if y > top + w else max(0, y - top)
            for x in range(cx - half, cx + half + 1):
                if not (0 <= x < T): continue
                c = light if x < cx else dark if x > cx else mid
                if y == top or (x == cx - half and y < top + 3): c = hl
                if y == 15: c = shade(c, 0.8)
                px[x, y] = rgba(c)
    return img


def crystal_lamp(lit=True):
    """Lámpara de cristal: marco de roca de Velo pulida con un cristal cerúleo tallado en el centro que brilla."""
    hl, light, mid, dark, deep = CRYSTAL
    img = Image.new("RGBA", (T, T))
    px = img.load()
    frame = [(232, 234, 242), (206, 210, 222), (170, 176, 192), (128, 134, 152)]
    facets = crystal_block(random.Random(12)).load()
    for y in range(T):
        for x in range(T):
            e = min(x, y, T - 1 - x, T - 1 - y)
            if e <= 2:
                c = frame[0] if (x == 0 or y == 0) else frame[3] if (x == T - 1 or y == T - 1) else frame[1] if e == 1 else frame[2]
                if e == 2: c = (110, 116, 134)               # junta interior
                px[x, y] = rgba(c)
            else:
                # cristal tallado (las facetas del bloque de cristal); encendida: más claro y un resplandor en el centro
                c = facets[x, y][:3]
                r = math.hypot(x - 7.5, y - 7.5)
                if lit:
                    k = 1.0 + max(0.0, 0.6 * (1 - r / 5.5))
                    c = tuple(min(255, int(v * k + 70 * max(0.0, 1 - r / 4.0))) for v in c)
                else:
                    c = shade(c, 0.42)
                px[x, y] = rgba(c)
    for (x, y) in [(1, 1), (14, 1), (1, 14), (14, 14)]:      # remaches
        px[x, y] = rgba((150, 156, 172))
    return img


def titanium_colors():
    return ([(122, 140, 168), (206, 220, 240), (62, 72, 96)], "chunks")


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
            tone.setdefault(bx, rng.uniform(0.94, 1.06))
            if y % 4 == 3 or (x + off) % 8 == 7:
                c = (112, 118, 138)
            else:
                c = shade((176, 182, 198), tone[bx] * (0.94 + 0.12 * tex[y, x]))
                if y % 4 == 0: c = shade(c, 1.08)
            px[x, y] = rgba(c)
    return img


def polished(rng):
    f = AT.pnoise(rng, ((8, 0.5), (4, 0.5)))
    img = AT.posterize(f * 0.4 + 0.3, AT.ramp((170, 176, 192), (214, 218, 230), 5), rng, 0.04)
    d = ImageDraw.Draw(img)
    d.line([(0, 0), (T - 1, 0)], fill=rgba((230, 232, 240))); d.line([(0, 0), (0, T - 1)], fill=rgba((222, 226, 236)))
    d.line([(0, T - 1), (T - 1, T - 1)], fill=rgba((130, 136, 154))); d.line([(T - 1, 0), (T - 1, T - 1)], fill=rgba((140, 146, 162)))
    d.rectangle([4, 4, 11, 11], outline=rgba((158, 164, 182)))
    return img


def make_textures():
    rng = random.Random(4242)
    t = {}
    t["velo_dust"] = dust(rng)
    t["velo_rock"] = CT.calmed(rock, CT.velo_rock, rng, 9107)
    t["velo_rock_bricks"] = bricks(rng)
    t["polished_velo_rock"] = polished(rng)
    t["vitreous_basalt"] = basalt(rng)
    t["vitreous_basalt_top"] = basalt_top(rng)
    t["cerulean_crystal_block"] = crystal_block(rng)
    t["budding_cerulean_crystal"] = crystal_block(random.Random(7), budding=True)
    for i, n in enumerate(["small_cerulean_bud", "medium_cerulean_bud", "large_cerulean_bud", "cerulean_crystal_cluster"]):
        t[n] = crystal_sprite(i)
    for name in ["iron", "gold", "diamond"]:
        cols, shape = CF.ORES[name]
        t[f"velo_{name}_ore"] = CF.ore(random.Random(zlib.crc32(name.encode())), t["velo_rock"], cols, shape)
    cols, shape = titanium_colors()
    t["velo_titanium_ore"] = CF.ore(random.Random(99), t["velo_rock"], cols, shape)
    t["velo_crystal_ore"] = CF.crystal_ore(random.Random(31), t["velo_rock"])
    t["cerulean_crystal_lamp"] = crystal_lamp(True)
    t["cerulean_crystal_lamp_off"] = crystal_lamp(False)
    return t


LABELS = {"velo_dust": "Polvo de Velo", "velo_rock": "Roca de Velo", "velo_rock_bricks": "Ladrillos de Velo",
          "polished_velo_rock": "Roca de Velo pulida", "vitreous_basalt": "Basalto vítreo",
          "cerulean_crystal_block": "Bloque de cristal cerúleo", "budding_cerulean_crystal": "Cristal cerúleo brotante",
          "velo_iron_ore": "Hierro", "velo_gold_ore": "Oro", "velo_diamond_ore": "Diamante",
          "velo_titanium_ore": "Titanio (Cosmonautics)", "velo_crystal_ore": "Cristal cerúleo",
          "small_cerulean_bud": "Brote pequeño", "medium_cerulean_bud": "Brote mediano",
          "large_cerulean_bud": "Brote grande", "cerulean_crystal_cluster": "Racimo de cristal",
          "cerulean_crystal_lamp": "Lámpara de cristal", "cerulean_crystal_lamp_off": "Lámpara (apagada)"}


# ------------------------------------------------------------------ vista previa del terreno

SPRITE_KEYS = {"bud_s": "small_cerulean_bud", "bud_m": "medium_cerulean_bud", "bud_l": "large_cerulean_bud", "cluster": "cerulean_crystal_cluster"}


def patch_renderer():
    AT.CUBES = {"dust": ("velo_dust", "velo_dust"), "rock": ("velo_rock", "velo_rock"),
                "basalt": ("vitreous_basalt_top", "vitreous_basalt"), "crystal": ("cerulean_crystal_block", "cerulean_crystal_block"),
                "budding": ("budding_cerulean_crystal", "budding_cerulean_crystal"), "ore_c": ("velo_crystal_ore", "velo_crystal_ore"),
                "ore_t": ("velo_titanium_ore", "velo_titanium_ore")}
    AT.SPRITES = dict(SPRITE_KEYS)
    AT.GLOWING = set(SPRITE_KEYS.values())
    AT.WATER = {}


def terrain(seed, base, amp, scale, top="dust", under="dust", depth=2):
    w = AP.World(seed)
    n = AP.noise2(seed, scale, scale)
    hm = np.zeros((AP.W, AP.D), int)
    for x in range(AP.W):
        for z in range(AP.D):
            h = int(round(base + amp * n(x, z)))
            hm[x, z] = h
            for y in range(h):
                w.set(x, y, z, "rock" if y < h - depth else under)
            w.set(x, h, z, top)
    return w, hm


def scatter(w, hm, kind, p, rng):
    for x in range(AP.W):
        for z in range(AP.D):
            if rng.random() < p and w.get(x, hm[x, z] + 1, z) is None:
                w.set(x, hm[x, z] + 1, z, kind)


def dust_plains():
    w, hm = terrain(11, 8, 1.6, 0.16)
    r = random.Random(1)
    for _ in range(4):                                        # pocas rocas sueltas
        x, z = r.randrange(3, AP.W - 3), r.randrange(3, AP.D - 3)
        AP.blob(w, x, hm[x, z] + 1, z, r.uniform(1, 2.2), r.uniform(0.8, 1.6), r.uniform(1, 2.2), "rock")
    scatter(w, hm, "bud_s", 0.012, r)
    return w


def dunes():
    """Dunas de polvo: lomas largas y suaves, nada más (la luna está casi vacía)."""
    w, hm = terrain(21, 9, 3.2, 0.09)
    scatter(w, hm, "bud_s", 0.004, random.Random(2))
    return w


def glass_sea():
    w, hm = terrain(21, 6, 0.6, 0.08, top="basalt", under="basalt", depth=4)
    r = random.Random(2)
    n = AP.noise2(5, 0.2, 0.2)
    for x in range(AP.W):
        for z in range(AP.D):
            if n(x, z) > 0.35:                                # manchas de polvo sobre el basalto
                w.set(x, hm[x, z], z, "dust")
    scatter(w, hm, "bud_s", 0.006, r)
    return w


def spire(w, x, z, h0, hgt, r):
    """Aguja de cristal: columna que se estrecha, un poco inclinada."""
    lean = (r.choice((-1, 0, 1)), r.choice((-1, 0, 1)))
    for k in range(hgt):
        rad = max(0, int(2.4 * (1 - k / hgt) ** 1.4 + 0.3))
        cx, cz = x + int(lean[0] * k / 5), z + int(lean[1] * k / 5)
        for dx in range(-rad, rad + 1):
            for dz in range(-rad, rad + 1):
                if dx * dx + dz * dz <= rad * rad + 0.5:
                    w.set(cx + dx, h0 + 1 + k, cz + dz, "budding" if (k == 0 and r.random() < 0.3) else "crystal")
    w.set(x + int(lean[0] * hgt / 5), h0 + 1 + hgt, z + int(lean[1] * hgt / 5), "cluster")


def crystal_fields():
    w, hm = terrain(31, 9, 2.0, 0.14)
    r = random.Random(3)
    for (x, z, hgt) in [(14, 12, 14), (28, 18, 10), (18, 30, 8), (33, 34, 12), (8, 24, 6), (36, 8, 7)]:
        spire(w, x, z, hm[x, z], hgt, r)
    for kind, p in (("bud_s", 0.03), ("bud_m", 0.02), ("bud_l", 0.012), ("cluster", 0.008)):
        scatter(w, hm, kind, p, r)
    return w


def craters():
    w, hm = terrain(41, 10, 1.0, 0.12)
    cx, cz, R = 22, 22, 15
    for x in range(AP.W):
        for z in range(AP.D):
            d = math.hypot(x - cx, z - cz) / R
            if d < 1.25:
                # cuenco con borde levantado
                h = int(round(10 + (5 * (d * d - 1) if d < 1 else 3 * (1.25 - d) / 0.25)))
                for y in range(h + 6):
                    if y > h: w.v.pop((x, y, z), None)
                for y in range(h + 1):
                    w.set(x, y, z, "rock" if y < h - 1 else "dust")
                hm[x, z] = h
    # geoda rota en el centro: cáscara de basalto, cristal por dentro y racimos
    gx, gy, gz = cx, hm[cx, cz] + 1, cz
    for x in range(gx - 5, gx + 6):
        for y in range(gy - 3, gy + 5):
            for z in range(gz - 5, gz + 6):
                d = math.sqrt((x - gx) ** 2 + ((y - gy) * 1.2) ** 2 + (z - gz) ** 2)
                cut = x + z > gx + gz + 1                   # la mitad de delante está rota (se ve el interior)
                if d > 5:
                    continue
                if cut or d <= 3.2:
                    w.v.pop((x, y, z), None)                 # hueco
                elif d > 4:
                    w.set(x, y, z, "rock")
                else:
                    w.set(x, y, z, "budding" if random.Random(x * 7 + y * 3 + z).random() < 0.25 else "crystal")
    r = random.Random(4)
    for x in range(gx - 4, gx + 5):
        for z in range(gz - 4, gz + 5):
            for y in range(gy - 3, gy + 4):
                if w.get(x, y, z) is None and w.get(x, y - 1, z) in ("crystal", "budding", "rock", "dust") and r.random() < 0.35:
                    w.set(x, y, z, r.choice(["bud_m", "bud_l", "cluster"]))
                    break
    scatter(w, hm, "bud_s", 0.01, r)
    return w


def space_sky(wd, ht, rng):
    img = Image.new("RGBA", (wd, ht), (6, 7, 14, 255))
    d = ImageDraw.Draw(img)
    for _ in range(int(wd * ht / 900)):
        x, y = rng.randrange(wd), rng.randrange(ht)
        b = rng.randint(90, 230)
        d.point((x, y), fill=(b, b, min(255, b + 20), 255))
    return img


def cerulea_disc(img, cx, cy, r):
    """Cerúlea en el cielo: mar azul, tierras verdes, nubes blancas y la cara de la noche en sombra."""
    def field(seed, n):
        g = np.random.default_rng(seed).random((n, n))
        return np.asarray(Image.fromarray((g * 255).astype(np.uint8)).resize((2 * r, 2 * r), Image.BICUBIC)) / 255
    land, cloud = field(5, 7), field(9, 12)
    arr = np.zeros((2 * r + 20, 2 * r + 20, 4), np.uint8)
    for y in range(-r - 10, r + 10):
        for x in range(-r - 10, r + 10):
            d = math.hypot(x, y) / r
            iy, ix = min(2 * r - 1, max(0, y + r)), min(2 * r - 1, max(0, x + r))
            if d <= 1:
                n = land[iy, ix]
                c = np.array((28, 96, 150)) if n < 0.5 else np.array((77, 176, 138)) if n < 0.72 else np.array((44, 88, 170))
                if cloud[iy, ix] > 0.68: c = np.array((232, 238, 244))
                lit = max(0.12, min(1.0, 0.55 - (x + 0.3 * y) / r * 0.8))
                arr[y + r + 10, x + r + 10] = list(np.clip(c * lit, 0, 255).astype(int)) + [255]
            elif d < 1.08:
                a = (1.08 - d) / 0.08
                arr[y + r + 10, x + r + 10] = [120, 190, 230, int(150 * a * a)]
    img.alpha_composite(Image.fromarray(arr, "RGBA"), (cx - r - 10, cy - r - 10))


def texture_sheet(tex, out):
    F, Fm, f = AT.fonts()
    cubes = [("velo_dust", "velo_dust", "velo_dust"), ("velo_rock", "velo_rock", "velo_rock"),
             ("velo_rock_bricks", "velo_rock_bricks", "velo_rock_bricks"), ("polished_velo_rock", "polished_velo_rock", "polished_velo_rock"),
("cerulean_crystal_block", "cerulean_crystal_block", "cerulean_crystal_block"),
             ("budding_cerulean_crystal", "budding_cerulean_crystal", "budding_cerulean_crystal"),
             ("velo_iron_ore", "velo_iron_ore", "velo_iron_ore"), ("velo_gold_ore", "velo_gold_ore", "velo_gold_ore"),
             ("velo_diamond_ore", "velo_diamond_ore", "velo_diamond_ore"), ("velo_titanium_ore", "velo_titanium_ore", "velo_titanium_ore"),
             ("velo_crystal_ore", "velo_crystal_ore", "velo_crystal_ore"),
             ("cerulean_crystal_lamp", "cerulean_crystal_lamp", "cerulean_crystal_lamp"),
             ("cerulean_crystal_lamp_off", "cerulean_crystal_lamp_off", "cerulean_crystal_lamp_off")]
    sprites = ["small_cerulean_bud", "medium_cerulean_bud", "large_cerulean_bud", "cerulean_crystal_cluster"]
    cw, ch, cols = 170, 170, 6
    rows = math.ceil(len(cubes) / cols) + 1
    sheet = space_sky(cols * cw, 70 + rows * ch, random.Random(1))
    d = ImageDraw.Draw(sheet)
    d.text((16, 16), "Velo, luna de cristal — bloques (texturas 16×16)", fill=(235, 238, 245, 255), font=F)
    for i, (key, top, side) in enumerate(cubes):
        x0, y0 = (i % cols) * cw, 70 + (i // cols) * ch
        a, b = AT.face_sprites(tex[top], 34), AT.face_sprites(tex[side], 34)
        cube = Image.new("RGBA", (68, 102), (0, 0, 0, 0))
        for part in (a["top"], b["left"], b["right"]): cube.alpha_composite(part)
        sheet.alpha_composite(cube, (x0 + 10, y0 + 8))
        sheet.alpha_composite(tex[side].resize((64, 64), Image.NEAREST), (x0 + 90, y0 + 24))
        d.text((x0 + 10, y0 + 122), LABELS[key], fill=(220, 225, 235, 255), font=f)
    base = 70 + math.ceil(len(cubes) / cols) * ch
    for i, key in enumerate(sprites):
        x0, y0 = i * cw, base
        sp = tex[key].resize((96, 96), Image.NEAREST)
        glow = Image.new("RGBA", (130, 130), (0, 0, 0, 0)); glow.alpha_composite(sp, (17, 17))
        a = np.asarray(glow.filter(ImageFilter.GaussianBlur(8)))[..., 3].astype(float)
        halo = np.zeros((130, 130, 4), np.uint8); halo[..., 0], halo[..., 1], halo[..., 2] = 120, 210, 255
        halo[..., 3] = np.clip(a * 0.6, 0, 255).astype(np.uint8)
        sheet.alpha_composite(Image.fromarray(halo, "RGBA"), (x0 + 20, y0 - 5))
        sheet.alpha_composite(sp, (x0 + 37, y0 + 12))
        d.text((x0 + 10, y0 + 122), LABELS[key], fill=(220, 225, 235, 255), font=f)
    sheet.save(out)


def biome_sheet(tex, out):
    F, Fm, f = AT.fonts()
    patch_renderer()
    biomes = [(dust_plains, "Llanuras de polvo"), (dunes, "Dunas de polvo"),
              (crystal_fields, "Campos de cristal"), (craters, "Cráter con geoda rota")]
    cw, ch, cols = 760, 760, 2
    sheet = space_sky(cols * cw, 60 + 2 * ch, random.Random(2))
    cerulea_disc(sheet, cols * cw - 170, 200, 120)
    d = ImageDraw.Draw(sheet)
    d.text((16, 14), "Velo — biomas: solo polvo blanco y cristal (concepto; Cerúlea en el cielo)", fill=(235, 238, 245, 255), font=F)
    for i, (fn, title) in enumerate(biomes):
        x0, y0 = (i % cols) * cw, 60 + (i // cols) * ch
        sheet.alpha_composite(AT.render_textured(fn(), tex, s=8, size=(760, 760), oy=400), (x0, y0 + 10))
        d.text((x0 + 16, y0 + 6), title, fill=(235, 238, 245, 255), font=Fm)
    sheet.save(out)


def main(out_dir):
    os.makedirs(os.path.join(out_dir, "textures"), exist_ok=True)
    tex = make_textures()
    for k, im in tex.items(): im.save(os.path.join(out_dir, "textures", k + ".png"))
    texture_sheet(tex, os.path.join(out_dir, "velo_texturas.png"))
    biome_sheet(tex, os.path.join(out_dir, "velo_biomas.png"))


GAME_NAMES = {"cerulean_crystal_lamp": "cerulean_crystal_lamp_on", "cerulean_crystal_lamp_off": "cerulean_crystal_lamp"}
SKIP = {"vitreous_basalt", "vitreous_basalt_top"}   # el usuario quiso Velo solo con polvo blanco y cristal


def export(out_dir):
    """Texturas del juego (nombres de los bloques) en <out_dir>/block."""
    os.makedirs(os.path.join(out_dir, "block"), exist_ok=True)
    for k, im in make_textures().items():
        if k in SKIP:
            continue
        im.save(os.path.join(out_dir, "block", GAME_NAMES.get(k, k) + ".png"))


if __name__ == "__main__":
    if sys.argv[1] == "--export":
        export(sys.argv[2])
    else:
        main(sys.argv[1])
