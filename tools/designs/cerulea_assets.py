"""Exporta las texturas finales de Cerúlea con los nombres del juego (1.0.3, 2026-11-13).

Uso: python3 -I tools/designs/cerulea_assets.py
Escribe tools/designs/cerulea/block/*.png y tools/designs/cerulea/item/*.png, que generate.py copia al mod.
Usa los mismos generadores que las vistas previas (amber_textures.py y cerulea_food_ores.py) y añade lo que solo
hace falta en el juego: tablones, retoños y las etapas que faltaban.
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import amber_textures as AT  # noqa: E402
import cerulea_food_ores as CF  # noqa: E402

T = 16
rgba, shade = AT.rgba, AT.shade
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cerulea")


def planks(rng, base):
    """Cuatro tablas de 4 px con su tono, vetas, canto de luz arriba, junta oscura abajo y clavitos."""
    img = Image.new("RGBA", (T, T))
    px = img.load()
    grain = AT.pnoise(rng, ((8, 0.3), (2, 0.7)))
    for row in range(4):
        tone = rng.uniform(0.9, 1.08)
        cut = rng.randrange(4, 12) if row % 2 else None            # junta vertical en tablas alternas
        for y in range(row * 4, row * 4 + 4):
            for x in range(T):
                c = shade(base, tone * (0.9 + 0.2 * grain[y, x]))
                if y % 4 == 0:
                    c = shade(c, 1.12)
                elif y % 4 == 3:
                    c = shade(base, 0.62)
                elif (x + row * 5) % 7 == 0:
                    c = shade(c, 0.86)                              # veta
                if cut is not None and x == cut and y % 4 != 3:
                    c = shade(base, 0.66)
                px[x, y] = rgba(c)
        for x in (1, 14):
            if rng.random() < 0.5:
                px[x, row * 4 + 1] = rgba(shade(base, 0.55))       # clavito
    return img


def sapling(rng, leaf, light, dark, trunk):
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    for y in range(9, T):
        px[7, y] = rgba(trunk)
        px[8, y] = rgba(shade(trunk, 0.75))
    for (x, y) in ((6, 12), (9, 11)):
        px[x, y] = rgba(trunk)
    for (cx, cy, r) in ((8, 6, 3.6), (5, 8, 2.4), (11, 8, 2.4)):
        for y in range(T):
            for x in range(T):
                dx, dy = x - cx, y - cy
                if dx * dx + dy * dy <= r * r and rng.random() < 0.9:
                    k = dx + dy
                    px[x, y] = rgba(light if k < -2 else dark if k > 1.5 else leaf)
    return img


WOOD_COLORS = {"silverwood": (176, 186, 198), "duskwood": (104, 88, 132), "mirewood": (86, 112, 104)}
GLASS = [(150, 210, 235), (190, 232, 248), (110, 170, 205)]


def door(rng, wood):
    """Puerta de 16×32 (arriba y abajo por separado) y su icono. Tablas verticales con marco; cada madera con su
    dibujo: plateado con ventana de cuatro cristales, violeta con cruz en diagonal y ojo de buey, pantano con
    travesaños y rendijas. Pomo oscuro a la derecha."""
    base = WOOD_COLORS[wood]
    img = Image.new("RGBA", (T, 2 * T), (0, 0, 0, 0))
    px = img.load()
    grain = AT.pnoise(rng, ((8, 0.4), (2, 0.6)))
    for y in range(2 * T):
        for x in range(T):
            board = x // 4
            c = shade(base, (0.94 + 0.06 * (board % 2)) * (0.9 + 0.2 * grain[y % T, x]))
            if x % 4 == 3:
                c = shade(base, 0.72)                                # junta entre tablas
            if x in (0, T - 1) or y in (0, 2 * T - 1):
                c = shade(base, 0.6)                                 # marco
            elif x in (1, T - 2) or y in (1, 2 * T - 2):
                c = shade(base, 1.12 if x == 1 or y == 1 else 0.78)
            px[x, y] = rgba(c)
    d = ImageDraw.Draw(img)
    frame_dark, frame_light = shade(base, 0.58), shade(base, 1.18)
    if wood == "silverwood":
        d.rectangle([3, 3, 12, 13], fill=rgba(frame_dark))
        for (x0, y0) in ((4, 4), (9, 4), (4, 9), (9, 9)):
            d.rectangle([x0, y0, x0 + 2, y0 + 3], fill=rgba(GLASS[0]))
            d.point((x0, y0), fill=rgba(GLASS[1]))
            d.point((x0 + 2, y0 + 3), fill=rgba(GLASS[2]))
        d.line([(3, 19), (12, 19)], fill=rgba(frame_dark))
        d.line([(3, 26), (12, 26)], fill=rgba(frame_dark))
    elif wood == "duskwood":
        d.ellipse([4, 3, 11, 10], fill=rgba(frame_dark))
        d.ellipse([5, 4, 10, 9], fill=rgba(GLASS[0]))
        d.point((6, 5), fill=rgba(GLASS[1]))
        d.line([(3, 15), (12, 28)], fill=rgba(frame_dark), width=2)
        d.line([(12, 15), (3, 28)], fill=rgba(frame_dark), width=2)
        d.line([(3, 14), (12, 14)], fill=rgba(frame_light))
    else:
        for y0 in (4, 13, 22):
            d.rectangle([2, y0, 13, y0 + 2], fill=rgba(shade(base, 0.82)))
            d.line([(2, y0), (13, y0)], fill=rgba(frame_light))
        for x0 in (5, 9):
            d.line([(x0, 8), (x0, 11)], fill=rgba(shade(base, 0.45)))    # rendijas (oscuras, no huecas)
            d.line([(x0 + 1, 8), (x0 + 1, 11)], fill=rgba(shade(base, 1.1)))
    d.rectangle([12, 17, 13, 18], fill=rgba((40, 40, 46)))           # pomo
    d.point((12, 17), fill=rgba((120, 120, 130)))
    top, bottom = img.crop((0, 0, T, T)), img.crop((0, T, T, 2 * T))
    return top, bottom, door_icon(wood)


def door_icon(wood):
    """Icono de la puerta dibujado a su tamaño (16×16) con los mismos detalles que la puerta: no se reduce la textura
    grande, que perdía el dibujo."""
    base = WOOD_COLORS[wood]
    icon = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = icon.load()
    for y in range(T):
        for x in range(3, 13):
            c = shade(base, 0.95 + 0.07 * ((x - 3) // 3 % 2))
            if x in (3, 12) or y in (0, 15):
                c = shade(base, 0.58)
            elif x == 4 or y == 1:
                c = shade(base, 1.15)
            px[x, y] = rgba(c)
    dark, light = shade(base, 0.55), shade(base, 1.18)
    if wood == "silverwood":
        for (x0, y0) in ((5, 2), (8, 2), (5, 5), (8, 5)):
            for dx in range(2):
                for dy in range(2):
                    px[x0 + dx, y0 + dy] = rgba(GLASS[0] if (dx, dy) != (0, 0) else GLASS[1])
        for x in range(5, 11):
            px[x, 7] = px[x, 4] = rgba(dark) if px[x, 4][:3] != GLASS[0] else px[x, 4]
            px[x, 10] = px[x, 13] = rgba(dark)
        for y in range(2, 7):
            px[7, y] = rgba(dark)                                      # parteluz
    elif wood == "duskwood":
        for (x, y) in ((7, 2), (8, 2), (6, 3), (9, 3), (6, 4), (9, 4), (7, 5), (8, 5)):
            px[x, y] = rgba(dark)
        for (x, y) in ((7, 3), (8, 3), (7, 4), (8, 4)):
            px[x, y] = rgba(GLASS[0])
        px[7, 3] = rgba(GLASS[1])
        for k in range(6):
            px[5 + k, 8 + k] = rgba(dark)
            px[10 - k, 8 + k] = rgba(dark)
        for x in range(5, 11):
            px[x, 7] = rgba(light)
    else:
        for y0 in (2, 7, 12):
            for x in range(5, 11):
                px[x, y0] = rgba(light)
                px[x, y0 + 1] = rgba(shade(base, 0.82))
        for x0 in (6, 9):
            px[x0, 4] = px[x0, 5] = rgba(shade(base, 0.45))
    px[11, 8] = rgba((40, 40, 46))                                    # pomo
    px[11, 9] = rgba((40, 40, 46))
    return icon


def trapdoor(rng, wood):
    """Trampilla: marco y, dentro, el dibujo de cada madera con huecos (se ve a través)."""
    base = WOOD_COLORS[wood]
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    grain = AT.pnoise(rng, ((8, 0.4), (2, 0.6)))
    for y in range(T):
        for x in range(T):
            px[x, y] = rgba(shade(base, 0.9 + 0.2 * grain[y, x]))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, T - 1, T - 1], outline=rgba(shade(base, 0.6)))
    d.line([(1, 1), (T - 2, 1)], fill=rgba(shade(base, 1.15)))
    if wood == "silverwood":
        for (x0, y0) in ((3, 3), (9, 3), (3, 9), (9, 9)):
            d.rectangle([x0, y0, x0 + 3, y0 + 3], fill=(0, 0, 0, 0))
    elif wood == "duskwood":
        # cuatro huecos triangulares entre los brazos de una cruz en diagonal, y un círculo de madera en el centro
        for y in range(2, T - 2):
            for x in range(2, T - 2):
                u, v = x - 7.5, y - 7.5
                if abs(abs(u) - abs(v)) > 1.6 and math.hypot(u, v) > 3.2:
                    px[x, y] = (0, 0, 0, 0)
        d.ellipse([5, 5, 10, 10], outline=rgba(shade(base, 0.62)))
        d.point((7, 7), fill=rgba(GLASS[0]))
        d.point((8, 8), fill=rgba(GLASS[0]))
    else:
        for y0 in (3, 7, 11):
            d.rectangle([2, y0, 13, y0 + 1], fill=(0, 0, 0, 0))
    return img


def hanging_fruit_block(fruit):
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    img.alpha_composite(fruit.resize((12, 12), Image.NEAREST), (2, 4))
    d = ImageDraw.Draw(img)
    d.line([(8, 0), (8, 4)], fill=rgba((92, 80, 118)))
    return img


def fill_holes(img):
    """1.0.3: hojas sin huecos (los pocos píxeles transparentes se veían como puntos sueltos). Cada hueco toma el color
    de un vecino opaco; el resto de la textura no cambia."""
    img = img.copy()
    px = img.load()
    for y in range(T):
        for x in range(T):
            if px[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1)):
                    c = px[(x + dx) % T, (y + dy) % T]
                    if c[3]:
                        px[x, y] = c
                        break
    return img


def main():
    blk, itm = os.path.join(OUT, "block"), os.path.join(OUT, "item")
    os.makedirs(blk, exist_ok=True)
    os.makedirs(itm, exist_ok=True)
    t = CF.make_textures()
    rng = random.Random(4242)
    B = {
        "cerulean_grass_block_top": t["amber_grass_top"], "cerulean_grass_block_side": t["amber_grass_side"],
        "cerulean_meadow_grass_block_top": t["amber_short_grass_block_top"],
        "cerulean_meadow_grass_block_side": t["amber_short_grass_block_side"],
        "cerulean_soil": t["amber_soil"], "cerulean_stone": t["cerulean_stone"], "cerulean_cobblestone": t["cerulean_cobblestone"],
        "cerulean_stone_bricks": t["cerulean_stone_bricks"], "polished_cerulean_stone": t["polished_cerulean_stone"],
        "deep_cerulean_stone": t["deep_cerulean_stone"], "cerulean_sand": t["amber_sand"], "cerulean_mud": t["amber_mud"],
        "silverwood_log": t["silverwood_log"], "silverwood_log_top": t["silverwood_log_top"],
        "silverwood_leaves": t["silverwood_leaves"],
        "duskwood_log": t["duskwood_log"], "duskwood_log_top": t["duskwood_log_top"], "duskwood_leaves": t["duskwood_leaves"],
        "blue_duskwood_leaves": t["duskwood_leaves_blue"],
        "mirewood_log": t["mirewood_log"], "mirewood_log_top": t["mirewood_log_top"], "mirewood_leaves": t["mirewood_leaves"],
        "cerulean_bush": t["bush_leaves"],
        "tall_cerulean_grass_bottom": t["tall_grass_bottom"], "tall_cerulean_grass_top": t["tall_grass_top"],
        "cerulean_grass": t["short_grass"], "azure_flower": t["azure_flower"], "glowbloom": t["glow_flower"],
        "glowcap": t["glow_mushroom"], "mire_vine": t["mire_vine"], "azure_lily_pad": t["azure_lily"],
        "blueberry_bush_stage0": t["blueberry_bush_young"], "blueberry_bush_stage1": t["blueberry_bush"],
        "duskfruit": hanging_fruit_block(t["violet_fruit"]),
        "mire_reed_bottom": t["mire_reed_bottom"], "mire_reed_top": t["mire_reed_top"],
        "silverwood_planks": planks(rng, (176, 186, 198)), "duskwood_planks": planks(rng, (104, 88, 132)),
        "mirewood_planks": planks(rng, (86, 112, 104)),
        "silverwood_sapling": sapling(rng, (44, 88, 170), (70, 140, 210), (30, 62, 130), (196, 206, 214)),
        "duskwood_sapling": sapling(rng, (40, 150, 120), (62, 178, 144), (26, 112, 92), (70, 60, 92)),
        "mirewood_sapling": sapling(rng, (52, 128, 110), (74, 156, 132), (36, 96, 84), (64, 84, 80)),
        "cerulean_crystal_ore": t["cerulean_crystal_ore"], "deep_cerulean_crystal_ore": t["deep_cerulean_crystal_ore"],
    }
    for s in range(4):
        B[f"cerulean_roots_stage{s}"] = t[f"cerulean_root_stage{s}"]
    I = {}
    for w in WOOD_COLORS:
        top, bottom, icon = door(random.Random(len(w) * 7), w)
        B[f"{w}_door_top"], B[f"{w}_door_bottom"] = top, bottom
        I[f"{w}_door"] = icon
        B[f"{w}_trapdoor"] = trapdoor(random.Random(len(w) * 11), w)
    for k in CF.ORES:
        B[f"cerulean_{k}_ore"] = t[f"cerulean_{k}_ore"]
        B[f"deep_cerulean_{k}_ore"] = t[f"deep_cerulean_{k}_ore"]
    I.update({"blueberries": t["blue_berries"], "duskfruit": t["violet_fruit"], "cerulean_root": t["cerulean_root"],
         "baked_cerulean_root": t["baked_cerulean_root"], "reed_pod": t["reed_pod"], "glowcap_stew": t["glowcap_stew"],
         "cerulean_crystal": t["cerulean_crystal"]})
    for k in [k for k in B if k.endswith("_leaves") or k == "cerulean_bush"]:
        B[k] = fill_holes(B[k])
    for k, v in B.items():
        v.save(os.path.join(blk, k + ".png"))
    for k, v in I.items():
        v.save(os.path.join(itm, k + ".png"))
    print("ok", len(B), "bloques,", len(I), "objetos ->", OUT)


if __name__ == "__main__":
    main()
