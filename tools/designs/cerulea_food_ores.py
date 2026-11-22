"""Cerúlea: plantas comestibles, familia de piedra y minerales (texturas y vistas previas, 1.0.3, 2026-11-13).

Uso: python3 -I tools/designs/cerulea_food_ores.py <carpeta_salida>

Escribe las texturas en <carpeta_salida>/textures/ y dos vistas previas:
  - cerulea_comestibles.png: las plantas en 3D (con sus etapas de crecimiento) y los objetos de comida;
  - cerulea_piedra_minerales.png: la familia de piedra, los minerales (capa normal y capa profunda, opcional) y el
    cristal cerúleo (opcional).
Pixel art procedural con semilla fija; los minerales usan formas propias, no las texturas de Minecraft.
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
import calm_textures as CT  # noqa: E402
import amber_plants_3d as P3  # noqa: E402

T = 16
rgba, shade = AT.rgba, AT.shade


def canvas():
    return Image.new("RGBA", (T, T), (0, 0, 0, 0))


def outline(img, color):
    """Contorno oscuro de 1 px alrededor de lo pintado (estilo de los objetos de Minecraft)."""
    a = np.asarray(img)[..., 3] > 0
    out = img.copy()
    px = out.load()
    for y in range(T):
        for x in range(T):
            if a[y, x]:
                continue
            if any(0 <= x + dx < T and 0 <= y + dy < T and a[y + dy, x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = rgba(color)
    return out


def ball(img, cx, cy, r, base, light, dark):
    px = img.load()
    for y in range(T):
        for x in range(T):
            dx, dy = x - cx, y - cy
            if dx * dx + dy * dy <= r * r:
                k = (dx + dy) / max(1.0, r)
                c = light if k < -0.6 else dark if k > 0.6 else base
                px[x, y] = rgba(c)


# ------------------------------------------------------------------ plantas comestibles

def berry_bush(rng, berries):
    """Arbusto redondo hecho con racimos de hojas (como las texturas de hojas, pero recortado en forma de mata), con
    ramitas violetas abajo y, si está maduro, bayas de 2×2 con brillo."""
    lv = AT.leaves(random.Random(rng.randint(0, 9999)), (46, 140, 120), (70, 172, 144), (28, 100, 88), holes=0.0)
    img = canvas()
    src, px = lv.load(), img.load()
    for y in range(3, T - 1):
        half = 6.8 * math.sin(math.pi * (y - 2) / 14) + 0.6
        for x in range(T):
            if abs(x - 7.5) <= half and (abs(x - 7.5) < half - 1 or rng.random() < 0.6):
                px[x, y] = src[x, y]
    for (x, y) in ((6, 15), (6, 14), (7, 13), (9, 15), (9, 14), (8, 13)):
        px[x, y] = rgba((76, 64, 100) if x < 8 else (58, 48, 80))
    if berries:
        for (x, y) in ((3, 7), (10, 5), (7, 8), (12, 9), (4, 11), (9, 11), (6, 4)):
            px[x, y] = rgba((150, 190, 255))
            px[x + 1, y] = rgba((80, 120, 235))
            px[x, y + 1] = rgba((70, 105, 220))
            px[x + 1, y + 1] = rgba((36, 60, 160))
    return img


def blue_berries():
    img = canvas()
    d = ImageDraw.Draw(img)
    d.line([(8, 6), (9, 3), (11, 2)], fill=rgba((60, 150, 120)))
    d.line([(12, 2), (13, 3)], fill=rgba((90, 190, 150)))
    px = img.load()
    for (cx, cy) in ((5.5, 9), (10.5, 8), (8, 12)):
        ball(img, cx, cy, 2.8, (70, 110, 230), (150, 190, 255), (40, 70, 180))
        px[int(cx) - 1, int(cy) - 1] = rgba((220, 235, 255))       # brillo
        px[int(cx), int(cy) - 2] = rgba((40, 60, 140))              # cáliz
    return outline(img, (20, 30, 70))


def violet_fruit():
    """Fruta del árbol violeta: gota turquesa con cuatro tonos, brillo, pecas azules y rabito con hoja."""
    img = canvas()
    px = img.load()
    for y in range(T):
        for x in range(T):
            dx, dy = (x - 7.5) / 5.4, (y - 9.0) / (6.0 if y > 9 else 4.6)
            r = dx * dx + dy * dy
            if r <= 1:
                k = dx * 0.8 + dy
                c = (176, 246, 226) if k < -0.95 else (120, 222, 200) if k < -0.35 else (40, 150, 140) if k > 0.75 else \
                    (60, 176, 160) if k > 0.25 else (84, 200, 180)
                px[x, y] = rgba(c)
    for (x, y) in ((7, 10), (10, 12), (5, 12), (9, 8), (8, 14), (11, 10)):
        px[x, y] = rgba((52, 110, 190))
    px[5, 6], px[6, 6], px[5, 7] = rgba((235, 255, 250)), rgba((200, 250, 240)), rgba((200, 250, 240))
    d = ImageDraw.Draw(img)
    d.line([(8, 4), (9, 1)], fill=rgba((92, 80, 118)))
    for (x, y, c) in ((10, 1, (90, 200, 160)), (11, 1, (62, 170, 140)), (12, 2, (62, 170, 140)), (11, 2, (40, 130, 110))):
        px[x, y] = rgba(c)
    return outline(img, (16, 48, 50))


def hanging_fruit():
    """La fruta colgando bajo las hojas (sprite en cruz)."""
    img = canvas()
    fr = violet_fruit().resize((10, 10), Image.NEAREST)
    img.alpha_composite(fr, (3, 4))
    ImageDraw.Draw(img).line([(8, 0), (8, 4)], fill=rgba((92, 80, 118)))
    return img


def root_crop(rng, stage):
    """Raíz cerúlea: matas de hojas en abanico (con nervio claro) que crecen en cuatro etapas; en la última asoman
    los tubérculos morados de la tierra."""
    img = canvas()
    px = img.load()
    hgt = [4, 7, 10, 12][stage]
    for x0, lean in ((3, -1), (8, 0), (13, 1)):
        for leaf in (-1, 0, 1):
            h = hgt - abs(leaf) * 2
            for k in range(max(1, h)):
                f = k / max(1, h - 1)
                x = int(round(x0 + leaf * f * 2 + lean * f))
                y = T - 1 - k
                if 0 <= x < T:
                    px[x, y] = rgba(shade((70, 190, 150), 0.7 + 0.45 * f))
                    if stage >= 2 and 0.3 < f < 0.9 and 0 <= x + leaf < T and leaf != 0:
                        px[x + leaf, y] = rgba(shade((50, 160, 128), 0.8 + 0.3 * f))
            if stage >= 1 and 0 <= x0 + leaf * 2 < T:
                px[x0 + leaf * 2, T - h] = rgba((150, 236, 200))
    if stage == 3:
        for x0 in (3, 13):
            for (dx, dy, c) in ((-1, 0, (120, 96, 186)), (0, 0, (96, 70, 160)), (1, 0, (96, 70, 160)), (-1, 1, (96, 70, 160)),
                                (0, 1, (70, 50, 128)), (1, 1, (60, 44, 110)), (0, -1, (150, 130, 210))):
                x, y = x0 + dx, 13 + dy
                if 0 <= x < T:
                    px[x, y] = rgba(c)
    return img


def root_item(cooked=False):
    """Raíz cerúlea (cruda: morada con anillos y raicillas; asada: dorada con grietas tostadas)."""
    img = canvas()
    px = img.load()
    if cooked:
        tones = [(236, 196, 140), (208, 160, 110), (170, 120, 86), (126, 86, 64)]
    else:
        tones = [(170, 150, 226), (124, 98, 190), (96, 70, 160), (64, 46, 116)]
    for y in range(T):
        for x in range(T):
            u, v = (x - 8) * 0.8 + (y - 8) * 0.6, -(x - 8) * 0.6 + (y - 8) * 0.8
            if (u / 6.6) ** 2 + (v / 3.7) ** 2 <= 1:
                k = (u / 6.6) * 0.25 + v / 3.7
                c = tones[0] if k < -0.6 else tones[1] if k < -0.1 else tones[2] if k < 0.5 else tones[3]
                if not cooked and int(u + 20) % 4 == 0:
                    c = shade(c, 0.85)                           # anillos
                px[x, y] = rgba(c)
    if cooked:
        for (x, y) in ((6, 7), (7, 8), (9, 9), (10, 10), (8, 11)):
            px[x, y] = rgba((96, 60, 40))
        px[5, 6] = rgba((250, 226, 180))
    else:
        d = ImageDraw.Draw(img)
        d.line([(3, 4), (2, 1)], fill=rgba((70, 190, 150)))
        d.line([(4, 4), (5, 1)], fill=rgba((110, 220, 180)))
        d.point((13, 13), fill=rgba((150, 130, 200)))
        d.point((14, 14), fill=rgba((150, 130, 200)))
    return outline(img, (30, 20, 50) if not cooked else (60, 36, 24))


def reed(rng, top):
    """Junco del pantano (2 bloques): tallos de dos tonos con nudos cada 5 px y hojas largas que salen de los nudos;
    arriba, mazorcas azules (como las espadañas) con su luz, su sombra y una puntita."""
    img = canvas()
    px = img.load()
    for x0 in (3, 8, 12):
        y_top = 0 if not top else 5 + (x0 % 3)
        for y in range(y_top, T):
            node = (y + x0) % 5 == 0
            px[x0, y] = rgba((50, 120, 108) if node else (76, 168, 146))
            px[x0 + 1, y] = rgba((40, 100, 90) if node else (58, 140, 124))
            if node and y < T - 2:                                # hoja que sale del nudo
                side = 1 if (x0 + y) % 2 else -1
                for k in range(1, 4):
                    xx, yy = x0 + (2 if side > 0 else -1) + side * (k - 1), y - k + 1
                    if 0 <= xx < T and 0 <= yy < T:
                        px[xx, yy] = rgba((96, 190, 164) if k < 3 else (130, 214, 186))
    if top:
        for x0 in (3, 8, 12):
            yt = 5 + (x0 % 3)
            for y in range(yt - 6, yt):
                for dx, c in ((-1, (90, 130, 220)), (0, (60, 100, 196)), (1, (60, 100, 196)), (2, (36, 66, 150))):
                    xx = x0 + dx
                    if 0 <= xx < T and 0 <= y < T and not (dx in (-1, 2) and y in (yt - 6, yt - 1)):
                        px[xx, y] = rgba(c)
            for y in range(max(0, yt - 9), yt - 6):
                px[x0, y] = rgba((90, 168, 150))                  # puntita
    return img


def reed_pod():
    """Vainas de junco: tres mazorcas azules en su tallo, con luz, sombra y puntita."""
    img = canvas()
    px = img.load()
    d = ImageDraw.Draw(img)
    d.line([(2, 14), (13, 2)], fill=rgba((76, 168, 146)))
    for (x, y) in ((5, 11), (8, 8), (11, 5)):
        for (dx, dy, c) in ((-1, -2, (130, 170, 240)), (0, -2, (90, 130, 220)), (-1, -1, (90, 130, 220)), (0, -1, (60, 100, 196)),
                            (1, -1, (60, 100, 196)), (-1, 0, (60, 100, 196)), (0, 0, (60, 100, 196)), (1, 0, (36, 66, 150)),
                            (0, 1, (36, 66, 150)), (1, -2, (60, 100, 196))):
            px[x + dx, y + dy] = rgba(c)
        px[x + 1, y - 3] = rgba((120, 200, 176))
    return outline(img, (20, 34, 80))


def glow_stew():
    """Tazón de madera con vetas y sopa luminosa con trocitos de hongo."""
    img = canvas()
    px = img.load()
    for y in range(6, 16):
        for x in range(1, 15):
            dx, dy = (x - 7.5) / 6.6, (y - 8.0) / 7.0
            if dx * dx + dy * dy <= 1 and y >= 8:
                k = dx
                c = (150, 116, 88) if k < -0.5 else (110, 82, 62) if k > 0.4 else (130, 98, 74)
                if (x + y) % 5 == 0:
                    c = shade(c, 0.86)                           # vetas
                px[x, y] = rgba(c)
    for x in range(2, 14):                                       # borde
        px[x, 7] = rgba((168, 132, 100))
    for x in range(3, 13):
        px[x, 8] = rgba((80, 220, 210) if x % 3 else (60, 190, 184))
        px[x, 7] = rgba((120, 240, 230)) if 4 <= x <= 11 else px[x, 7]
    for (x, y, c) in ((5, 6, (200, 255, 250)), (6, 6, (120, 240, 230)), (9, 6, (200, 255, 250)), (10, 6, (120, 240, 230)),
                      (7, 7, (230, 255, 255))):
        px[x, y] = rgba(c)
    return outline(img, (40, 30, 24))


def stone_family(rng, base_stone):
    t = {}
    t["cerulean_stone"] = base_stone
    # roca (cobblestone): celdas de Voronoi envueltas (cada píxel es de la piedra más cercana, contando las copias
    # de los bordes), juntas oscuras donde dos piedras se tocan y cada piedra con su tono y su luz arriba a la izquierda
    seeds = [(rng.uniform(0, T), rng.uniform(0, T)) for _ in range(9)]
    tones = [rng.choice([(110, 116, 130), (98, 104, 118), (124, 130, 144), (90, 96, 110)]) for _ in seeds]
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
            if math.sqrt(ds[1][0]) - math.sqrt(d0) < 0.9:
                c = (62, 68, 82)                                 # junta
            else:
                k = 1.16 if dx + dy < -2.5 else 1.06 if dx + dy < -1 else 0.84 if dx + dy > 2 else 1.0
                c = shade(tones[n], k + rng.uniform(-0.03, 0.03))
            px[x, y] = rgba(c)
    t["cerulean_cobblestone"] = img
    # ladrillos: cada ladrillo con su tono, textura, canto de luz arriba y sombra abajo
    img = Image.new("RGBA", (T, T))
    px = img.load()
    tex = AT.pnoise(rng, ((4, 0.5), (2, 0.5)))
    tone = {}
    for y in range(T):
        for x in range(T):
            row = y // 4
            off = 4 if row % 2 else 0
            bx = ((x + off) // 8, row)
            if bx not in tone:
                tone[bx] = rng.uniform(0.92, 1.08)
            if y % 4 == 3 or (x + off) % 8 == 7:
                c = (70, 76, 90)
            else:
                c = shade((118, 124, 138), tone[bx] * (0.92 + 0.16 * tex[y, x]))
                if y % 4 == 0:
                    c = shade(c, 1.12)
                elif y % 4 == 2:
                    c = shade(c, 0.9)
                if (x + off) % 8 == 0:
                    c = shade(c, 1.06)
            px[x, y] = rgba(c)
    t["cerulean_stone_bricks"] = img
    # pulida: lisa con bisel y un dibujo de losa en el centro
    f = AT.pnoise(rng, ((8, 0.5), (4, 0.5)))
    img = AT.posterize(f * 0.4 + 0.3, AT.ramp((108, 114, 128), (150, 156, 168), 5), rng, 0.04)
    d = ImageDraw.Draw(img)
    d.line([(0, 0), (T - 1, 0)], fill=rgba((168, 174, 186)))
    d.line([(0, 0), (0, T - 1)], fill=rgba((158, 164, 176)))
    d.line([(0, T - 1), (T - 1, T - 1)], fill=rgba((80, 86, 100)))
    d.line([(T - 1, 0), (T - 1, T - 1)], fill=rgba((88, 94, 108)))
    d.rectangle([4, 4, 11, 11], outline=rgba((112, 118, 132)))
    d.line([(5, 5), (10, 5)], fill=rgba((150, 156, 168)))
    t["polished_cerulean_stone"] = img
    return t


def deep_stone(rng):
    """Piedra profunda: más oscura y azulada, con estratos horizontales que ondulan (como la pizarra profunda)."""
    f = AT.pnoise(rng, ((8, 0.35), (4, 0.35), (2, 0.3)))
    for y in range(T):
        f[y, :] = 0.55 * f[y, :] + 0.45 * (0.5 + 0.5 * np.sin(y * math.pi / 2 + 0.6 * np.sin(np.arange(T) * math.pi / 8)))
    img = AT.edges(AT.posterize(f, AT.ramp((38, 40, 56), (92, 96, 118), 5), rng, 0.08))
    px = img.load()
    for _ in range(3):
        x, y = rng.randrange(T), rng.randrange(T)
        px[x, y] = rgba((118, 122, 146))
    return img


ORES = {
    # nombre: (colores del mineral, forma)
    "coal": ([(46, 46, 54), (110, 112, 128), (14, 14, 20)], "blobs"),
    "iron": ([(214, 176, 150), (236, 206, 182), (170, 132, 108)], "chunks"),
    "copper": ([(222, 124, 82), (126, 200, 160), (176, 90, 60)], "chunks"),
    "gold": ([(250, 214, 70), (255, 244, 150), (200, 150, 40)], "chunks"),
    "redstone": ([(220, 30, 30), (255, 90, 80), (150, 10, 10)], "ruby"),
    "lapis": ([(40, 80, 200), (90, 140, 240), (24, 50, 140)], "strands"),
    "diamond": ([(90, 230, 220), (200, 255, 250), (40, 160, 160)], "gems"),
    "emerald": ([(40, 200, 90), (140, 255, 170), (20, 130, 60)], "specks"),
    "zinc": ([(170, 190, 180), (220, 236, 228), (120, 140, 132)], "chunks"),
}


# Vetas de mineral (1.0.4): piezas dibujadas a mano (no copiadas de Minecraft). Tono por pixel:
# 5 = brillo, 4 = luz (arriba-izquierda), 3 = medio, 2 = sombra, 1 = fondo (abajo-derecha). Al pintar, cada tono se
# reparte en 7 colores de la paleta del mineral (con algo de variacion entre pixeles vecinos), y la piedra que rodea
# a cada veta se oscurece para que parezca incrustada en un hueco. Cada bloque mezcla una veta grande, varias
# medianas y unas motas minusculas, y cada mineral tiene su silueta y su composicion.
PIECES = {
    # grandes
    "BK": [".4443.", "443333", "433332", "333322", ".32221"],
    "BC": ["5433..", "443332", "433322", ".33222", "..2211"],
    "BR": [".5433", "44333", "43332", ".3322", "..221"],
    "BG": [".54.", "5433", "4332", ".322"],
    "BE": [".22.", "2542", "2332", ".22."],
    # medianas
    "K1": ["4333.", "43332", ".3322", "..221"], "K2": [".4333", "43333", "33322", ".2221"],
    "K3": ["433", "333", "322", ".21"], "K4": ["4333", "3332", ".322", ".211"],
    "C1": [".543", "4333", "3322", ".211"], "C2": ["5433", "4332", ".222"], "C3": ["543", "432", "321"],
    "C4": [".54", "433", "332", ".21"], "C5": ["5433.", "43332", ".3221"],
    "R1": [".54.", "4333", "3322", ".21."], "R2": ["543", "433", "322"], "R3": ["54..", "4332", ".322"],
    "R4": [".54", "433", "32."],
    "L1": ["5443..", ".43332", "..3322"], "L2": ["..543", "54333", "43322", ".221."], "L3": ["543", "433", "322"],
    "L4": ["54.", "433", ".32"],
    "D1": ["54.", "433", ".32"], "D2": [".54", "433", "32."], "D3": ["54", "43"], "D4": ["5433", ".322"],
    "D5": [".5.", "543", ".32"],
    "E2": [".2.", "243", ".2."], "E3": ["54", "32"],
    # motas
    "t1": ["5"], "t2": ["43"], "t3": ["4", "2"], "t4": ["3"],
}
LAYOUTS = {
    "blobs": [[("BK", 0, 1), ("K2", 9, 0), ("K3", 7, 6), ("K4", 11, 8), ("BC", 1, 9), ("C2", 9, 13), ("t1", 7, 2), ("t4", 15, 5), ("t4", 6, 15)],
              [("BR", 1, 0), ("K4", 9, 1), ("K1", 10, 6), ("K3", 6, 6), ("BK", 0, 10), ("K2", 9, 11), ("t3", 7, 2), ("t1", 14, 3), ("t4", 3, 6)]],
    "chunks": [[("BC", 0, 0), ("C3", 9, 1), ("C5", 10, 6), ("C1", 3, 7), ("C4", 0, 11), ("C2", 8, 12), ("t1", 7, 4), ("t2", 13, 12), ("t4", 14, 3)],
               [("BR", 9, 0), ("C4", 1, 1), ("C3", 5, 5), ("C5", 8, 10), ("BC", 0, 10), ("C1", 12, 6), ("t2", 7, 14), ("t1", 14, 14), ("t4", 5, 1)]],
    "ruby": [[("BR", 0, 0), ("R2", 8, 1), ("R3", 12, 5), ("R4", 6, 6), ("R1", 9, 9), ("R2", 1, 8), ("R3", 2, 13), ("t1", 13, 0), ("t4", 5, 11), ("t2", 13, 13)],
             [("R1", 1, 1), ("BR", 8, 1), ("R4", 12, 8), ("R2", 5, 6), ("R3", 0, 7), ("R1", 7, 11), ("t1", 6, 0), ("t4", 14, 4), ("t2", 3, 12), ("t4", 13, 14)]],
    "strands": [[("BC", 0, 0), ("L2", 9, 1), ("L3", 4, 6), ("L4", 11, 7), ("L1", 8, 11), ("L3", 0, 10), ("t1", 7, 4), ("t4", 15, 9), ("t2", 5, 14)],
                [("L2", 0, 1), ("BC", 9, 2), ("L4", 5, 6), ("L3", 1, 8), ("L1", 9, 10), ("L4", 5, 12), ("t1", 7, 0), ("t4", 15, 9), ("t2", 12, 14)]],
    "gems": [[("BG", 1, 0), ("D2", 8, 0), ("D5", 12, 2), ("D3", 5, 6), ("D4", 8, 6), ("D1", 0, 8), ("BG", 9, 10), ("D3", 3, 12), ("t1", 6, 3), ("t4", 14, 7), ("t2", 14, 13), ("t1", 6, 9)],
             [("D5", 0, 0), ("BG", 6, 1), ("D1", 12, 0), ("D2", 1, 5), ("D4", 9, 7), ("BG", 0, 9), ("D3", 8, 12), ("D5", 12, 11), ("t1", 4, 8), ("t4", 14, 6), ("t2", 5, 14)]],
    "specks": [[("BE", 1, 1), ("E2", 9, 0), ("E3", 10, 5), ("E2", 5, 7), ("BE", 0, 10), ("E3", 8, 11), ("E2", 12, 9), ("t1", 6, 3), ("t4", 14, 13), ("t1", 13, 2)],
               [("E2", 0, 0), ("BE", 6, 0), ("E3", 13, 4), ("BE", 0, 6), ("E3", 8, 6), ("E2", 12, 8), ("BE", 9, 12), ("t1", 4, 13), ("t4", 14, 1), ("t1", 5, 5)]],
}
SHAPE_ALIAS = {"dots": "ruby"}
ORE_SALT = {}      # variante de colocacion por mineral (se puede fijar a ojo si alguna queda mal repartida)


def _mix(a, b, k):
    return tuple(int(a[i] + (b[i] - a[i]) * k) for i in range(3))


def ore_tones(base, light, dark):
    base = shade(base, 0.86)
    return (shade(dark, 0.5), shade(dark, 0.88), _mix(dark, base, 0.55), base, _mix(base, light, 0.3),
            _mix(base, light, 0.6), _mix(base, light, 0.85))


# Digito de la pieza -> posibles indices de la paleta de 7 tonos (el primero es el mas probable)
_TONE_MAP = {"1": ((0, 1.0),), "2": ((1, 0.6), (2, 0.4)), "3": ((3, 0.6), (4, 0.2), (2, 0.2)),
             "4": ((5, 0.4), (4, 0.6)), "5": ((6, 0.5), (5, 0.5))}


def _compose(rng, key):
    """Composicion irregular, como una veta de verdad: una veta grande hacia el centro (no siempre exacta), unas
    medianas repartidas al azar con mas densidad cerca del centro (alguna pegada a otra, formando una masa) y unas
    motas minusculas junto a las demas piezas. Sin simetria ni angulos fijos, para que no dibuje letras."""
    names = sorted({n for lay in LAYOUTS[key] for n, _, _ in lay})
    bigs = [n for n in names if n.startswith("B")]
    tiny = [n for n in names if n.startswith("t")]
    mids = [n for n in names if n not in bigs and n not in tiny]
    n_mid = rng.choice((3, 4)) if key in ("ruby", "gems", "specks") else rng.choice((2, 3, 3))
    occ, cells, placed = set(), {}, []

    def shape_pts(name, ox, oy):
        return {(ox + dx, oy + dy) for dy, row in enumerate(PIECES[name]) for dx, ch in enumerate(row) if ch != "."}

    def put(name, tx, ty, margin, score=None):
        rows = PIECES[name]
        w, h = max(len(r) for r in rows), len(rows)
        best = None
        for _ in range(600):
            ox, oy = rng.randint(0, T - w), rng.randint(0, T - h)
            pts = shape_pts(name, ox, oy)
            m = margin
            if any((x + a, y + b) in occ for x, y in pts for a in range(-m, m + 1) for b in range(-m, m + 1)):
                continue
            c = (ox + w / 2, oy + h / 2)
            sc = score(c, pts) if score else math.hypot(c[0] - tx, c[1] - ty)
            if best is None or sc < best[0]:
                best = (sc, ox, oy, pts)
        if best is None:
            return
        _, ox, oy, pts = best
        occ.update(pts)
        placed.append(pts)
        for dy, row in enumerate(PIECES[name]):
            for dx, ch in enumerate(row):
                if ch != ".":
                    cells[(ox + dx, oy + dy)] = ch

    put(rng.choice(bigs), 8 + rng.uniform(-2.2, 2.2), 8 + rng.uniform(-2.2, 2.2), 1)
    pick = rng.sample(mids, min(n_mid, len(mids)))
    big_c = (sum(x for x, _ in placed[0]) / len(placed[0]), sum(y for _, y in placed[0]) / len(placed[0]))
    first = None
    for k, name in enumerate(pick):
        if k == 1 and first:                               # la segunda, hacia el lado contrario, para equilibrar
            tx = 2 * big_c[0] - first[0] + rng.uniform(-3, 3)
            ty = 2 * big_c[1] - first[1] + rng.uniform(-3, 3)
        else:
            tx, ty = rng.gauss(8, 4.4), rng.gauss(8, 4.4)
        tx, ty = min(13.5, max(2.5, tx)), min(13.5, max(2.5, ty))
        if k == 0:
            first = (tx, ty)
        put(name, tx, ty, 0 if k == 0 else 1)             # la primera mediana puede quedar pegada a la grande
    def near_others(c, pts):                               # la mota: a 2-3 pixeles de alguna pieza, sin tocarla
        d = min(min(abs(c[0] - x) + abs(c[1] - y) for x, y in p) for p in placed) if placed else 3
        return abs(d - 3.0) + rng.random() * 0.8
    put(rng.choice(tiny), 8, 8, 1, near_others)
    return cells


def ore(rng, stone_img, colors, shape):
    """Mineral sobre la piedra del planeta, incrustado en un hueco de la roca."""
    img = stone_img.copy()
    px = img.load()
    tones = ore_tones(*colors)
    key = SHAPE_ALIAS.get(shape, shape)
    cells = _compose(rng, key if key in LAYOUTS else "chunks")
    # 1) borde: la piedra que toca la veta se oscurece (mas arriba/izquierda, menos abajo/derecha), un poco fria
    ring = {}
    soft = 0.45 if sum(colors[0]) < 200 else 1.0          # minerales muy oscuros (carbon): borde mas suave
    for (x, y) in cells:
        for dx, dy, k in ((0, -1, 0.56), (-1, 0, 0.58), (-1, -1, 0.66), (1, -1, 0.7), (-1, 1, 0.7),
                          (0, 1, 0.74), (1, 0, 0.76), (1, 1, 0.8)):
            q = (x + dx, y + dy)
            if q not in cells and 0 <= q[0] < T and 0 <= q[1] < T:
                ring[q] = min(1 - (1 - k) * soft, ring.get(q, 1.0))
    for q, f in ring.items():
        c = stone_img.getpixel(q)[:3]
        px[q] = rgba((int(c[0] * f * 0.97), int(c[1] * f), min(255, int(c[2] * f * 1.04))))
    # 2) la veta, con 7 tonos
    for (x, y), ch in cells.items():
        opts = _TONE_MAP[ch]
        r, acc = rng.random(), 0.0
        idx = opts[-1][0]
        for t, p in opts:
            acc += p
            if r < acc:
                idx = t
                break
        px[x, y] = rgba(tones[idx])
    return img


CRYSTAL = [(230, 252, 255), (130, 218, 255), (76, 172, 236), (42, 112, 192), (24, 66, 138)]


def crystal_ore(rng, stone_img):
    """Cristal cerúleo como mineral de verdad: tres vetas de cristales tallados (rombos que se solapan, cada uno con
    su faceta de luz arriba a la izquierda, su faceta en sombra abajo a la derecha y una arista brillante) metidas en
    un hueco de la piedra con el borde en sombra, más una chispa suelta. Las vetas que se salen por un borde entran
    por el contrario, así el bloque se repite sin costuras."""
    img = stone_img.copy()
    px = img.load()
    hl, light, mid, dark, deep = CRYSTAL
    inside = {}
    spots = [(4, 4), (11, 3), (8, 10), (13, 12), (3, 12)]
    rng.shuffle(spots)
    for (cx, cy) in spots[:3]:
        gems = [(cx, cy, rng.choice((2, 3)))]
        for _ in range(rng.randint(1, 2)):                       # rombos pegados al primero
            gems.append((cx + rng.choice((-2, -1, 1, 2)), cy + rng.choice((-2, -1, 1, 2)), rng.choice((1, 2))))
        for (gx, gy, r) in gems:
            for dy in range(-r, r + 1):
                for dx in range(-r, r + 1):
                    if abs(dx) + abs(dy) > r:
                        continue
                    if dx == 0 and dy < 0 or dy == 0 and dx < 0:
                        c = hl if abs(dx) + abs(dy) < r else light   # aristas que miran a la luz
                    elif dx <= 0 and dy <= 0:
                        c = light
                    elif dx >= 0 and dy >= 0:
                        c = dark if abs(dx) + abs(dy) < r else deep
                    else:
                        c = mid
                    inside[((gx + dx) % T, (gy + dy) % T)] = c
    for (x, y) in list(inside):                                  # hueco: borde en sombra alrededor del cristal
        for (ex, ey, k) in ((1, 0, 0.55), (0, 1, 0.55), (1, 1, 0.5), (-1, 0, 0.75), (0, -1, 0.75)):
            q = ((x + ex) % T, (y + ey) % T)
            if q not in inside:
                px[q] = rgba(shade(stone_img.getpixel(q)[:3], k))
    for (x, y), c in inside.items():
        px[x, y] = rgba(c)
    for _ in range(2):                                           # chispas sueltas en la roca
        x, y = rng.randrange(T), rng.randrange(T)
        if (x, y) not in inside and ((x + 1) % T, y) not in inside:
            px[x, y] = rgba(light)
            px[(x + 1) % T, y] = rgba(dark)
    return img


def crystal_item():
    """Cristal cerúleo (objeto): punta doble con cuatro facetas y arista brillante, con la misma paleta que el mineral."""
    hl, light, mid, dark, deep = CRYSTAL
    img = canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(8, 1), (12, 5), (12, 10), (8, 15), (4, 10), (4, 5)], fill=rgba(mid))
    d.polygon([(8, 1), (4, 5), (4, 10), (8, 8)], fill=rgba(light))
    d.polygon([(8, 1), (12, 5), (8, 8)], fill=rgba(mid))
    d.polygon([(12, 5), (12, 10), (8, 15), (8, 8)], fill=rgba(dark))
    d.polygon([(4, 10), (8, 15), (8, 8)], fill=rgba(mid))
    d.line([(8, 2), (8, 8)], fill=rgba(hl))
    d.line([(5, 6), (7, 4)], fill=rgba(hl))
    d.point((10, 12), fill=rgba(deep))
    return outline(img, (14, 40, 80))

def make_textures():
    rng = random.Random(77)
    base = AT.make_textures()
    t = {}
    t["blueberry_bush_young"] = berry_bush(rng, False)
    t["blueberry_bush"] = berry_bush(random.Random(78), True)
    t["blue_berries"] = blue_berries()
    t["violet_fruit"] = violet_fruit()
    t["hanging_violet_fruit"] = hanging_fruit()
    for s in range(4):
        t[f"cerulean_root_stage{s}"] = root_crop(rng, s)
    t["cerulean_root"] = root_item()
    t["baked_cerulean_root"] = root_item(cooked=True)
    t["mire_reed_bottom"] = reed(rng, False)
    t["mire_reed_top"] = reed(rng, True)
    t["reed_pod"] = reed_pod()
    t["glowcap_stew"] = glow_stew()
    t.update(stone_family(rng, base["amber_stone"]))
    t["deep_cerulean_stone"] = CT.calmed(deep_stone, CT.deep_stone, rng, 9104)
    for name, (cols, shape) in ORES.items():
        t[f"cerulean_{name}_ore"] = ore(random.Random(zlib.crc32(name.encode()) + ORE_SALT.get(name, 0)), t["cerulean_stone"], cols, shape)
        t[f"deep_cerulean_{name}_ore"] = ore(random.Random(zlib.crc32(name.encode()) + ORE_SALT.get(name, 0)), t["deep_cerulean_stone"], cols, shape)
    t["cerulean_crystal_ore"] = crystal_ore(random.Random(31), t["cerulean_stone"])
    t["deep_cerulean_crystal_ore"] = crystal_ore(random.Random(31), t["deep_cerulean_stone"])
    t["cerulean_crystal"] = crystal_item()
    base.update(t)
    return base


# ------------------------------------------------------------------ vistas previas

def cube_img(top, side, s=34):
    a, b = AT.face_sprites(top, s), AT.face_sprites(side, s)
    img = Image.new("RGBA", (2 * s, 3 * s), (0, 0, 0, 0))
    for part in (a["top"], b["left"], b["right"]):
        img.alpha_composite(part)
    return img


def food_sheet(tex, out):
    F, Fm, f = AT.fonts()
    panels = []

    def plant(title, build):
        sc = P3.Scene()
        build(sc)
        panels.append((title, sc))

    gt, gs = tex["amber_grass_top"], tex["amber_grass_side"]
    plant("Arbusto de bayas (joven)", lambda sc: (sc.cube(0, -1, 0, gt, gs), sc.cross(0, 0, 0, tex["blueberry_bush_young"])))
    plant("Arbusto de bayas (con bayas)", lambda sc: (sc.cube(0, -1, 0, gt, gs), sc.cross(0, 0, 0, tex["blueberry_bush"])))
    plant("Fruta colgando del árbol violeta", lambda sc: (sc.cube(0, 1, 0, tex["duskwood_leaves"], tex["duskwood_leaves"]),
                                                         sc.cross(0, 0, 0, tex["hanging_violet_fruit"])))
    plant("Junco del pantano (2 bloques)", lambda sc: (sc.cube(0, -1, 0, tex["amber_mud"], tex["amber_mud"]),
                                                       sc.cross(0, 0, 0, tex["mire_reed_bottom"]), sc.cross(0, 1, 0, tex["mire_reed_top"])))
    farm = AT.noise_tile(random.Random(5), (60, 52, 70), 0.06, dots=[(46, 40, 56)], n_dots=30)   # tierra arada
    for s in range(4):
        plant(f"Raíz cerúlea — etapa {s + 1}", lambda sc, s=s: (sc.cube(0, -1, 0, farm, tex["amber_soil"]),
                                                                 sc.cross(0, 0, 0, tex[f"cerulean_root_stage{s}"])))
    cw, ch, cols = 300, 330, 4
    rows = math.ceil(len(panels) / cols)
    items = [("blue_berries", "Bayas azules"), ("violet_fruit", "Fruta violeta"), ("cerulean_root", "Raíz cerúlea"),
             ("baked_cerulean_root", "Raíz asada"), ("reed_pod", "Vainas de junco"), ("glowcap_stew", "Sopa de hongo luminoso")]
    sheet = AT.sky(cols * cw, 60 + rows * ch + 200)
    d = ImageDraw.Draw(sheet)
    d.text((16, 14), "Cerúlea — plantas comestibles", fill=(25, 35, 45, 255), font=F)
    for i, (title, sc) in enumerate(panels):
        x0, y0 = (i % cols) * cw, 60 + (i // cols) * ch
        sheet.alpha_composite(sc.render((cw, ch - 30), np.array([cw / 2, ch * 0.6])), (x0, y0 + 24))
        d.text((x0 + 14, y0 + 4), title, fill=(25, 35, 45, 255), font=Fm)
    y0 = 60 + rows * ch
    d.text((16, y0), "Objetos (comida)", fill=(25, 35, 45, 255), font=Fm)
    for j, (key, title) in enumerate(items):
        x = 30 + j * 195
        sheet.alpha_composite(tex[key].resize((112, 112), Image.NEAREST), (x + 20, y0 + 26))
        d.text((x, y0 + 146), title, fill=(25, 35, 45, 255), font=f)
    sheet.save(out)


def ore_sheet(tex, out):
    F, Fm, f = AT.fonts()
    fam = [("cerulean_stone", "Piedra"), ("cerulean_cobblestone", "Roca"), ("cerulean_stone_bricks", "Ladrillos"),
           ("polished_cerulean_stone", "Piedra pulida"), ("deep_cerulean_stone", "Piedra profunda")]
    names = {"coal": "Carbón", "iron": "Hierro", "copper": "Cobre", "gold": "Oro", "redstone": "Redstone",
             "lapis": "Lapislázuli", "diamond": "Diamante", "emerald": "Esmeralda", "zinc": "Zinc (Create)"}
    cw = 150
    cols = 9
    sheet = AT.sky(cols * cw, 60 + 4 * 210 + 30)
    d = ImageDraw.Draw(sheet)
    d.text((16, 14), "Cerúlea — piedra y minerales", fill=(25, 35, 45, 255), font=F)

    def cell(x0, y0, key, title):
        sheet.alpha_composite(cube_img(tex[key], tex[key]), (x0 + 14, y0 + 30))
        sheet.alpha_composite(tex[key].resize((48, 48), Image.NEAREST), (x0 + 88, y0 + 58))
        d.text((x0 + 8, y0 + 140), title, fill=(25, 35, 45, 255), font=f)

    y0 = 60
    d.text((16, y0), "Familia de piedra", fill=(25, 35, 45, 255), font=Fm)
    for i, (k, n) in enumerate(fam):
        cell(i * cw, y0 + 10, k, n)
    cell(5 * cw, y0 + 10, "cerulean_crystal_ore", "Cristal cerúleo")
    cell(6 * cw, y0 + 10, "deep_cerulean_crystal_ore", "Cristal (profundo)")
    sheet.alpha_composite(tex["cerulean_crystal"].resize((64, 64), Image.NEAREST), (7 * cw + 40, y0 + 70))
    d.text((7 * cw + 8, y0 + 150), "Cristal (objeto)", fill=(25, 35, 45, 255), font=f)
    y0 += 210
    d.text((16, y0), "Minerales en la piedra de Cerúlea (dan lo mismo que los normales)", fill=(25, 35, 45, 255), font=Fm)
    for i, (k, n) in enumerate(names.items()):
        cell(i * cw, y0 + 10, f"cerulean_{k}_ore", n)
    y0 += 210
    d.text((16, y0), "Capa profunda", fill=(25, 35, 45, 255), font=Fm)
    for i, (k, n) in enumerate(names.items()):
        cell(i * cw, y0 + 10, f"deep_cerulean_{k}_ore", n)
    y0 += 210
    # pared de muestra: piedra con algunos minerales, para ver cómo se ven juntos
    d.text((16, y0), "Pared de muestra", fill=(25, 35, 45, 255), font=Fm)
    wall = Image.new("RGBA", (16 * 12, 16 * 3))
    r = random.Random(4)
    keys = ["cerulean_stone"] * 20 + [f"cerulean_{k}_ore" for k in ("coal", "iron", "copper", "gold", "diamond")]
    for x in range(12):
        for y in range(3):
            wall.alpha_composite(tex[r.choice(keys)], (x * 16, y * 16))
    sheet.alpha_composite(wall.resize((wall.width * 3 // 2 * 2, wall.height * 3 // 2 * 2), Image.NEAREST), (20, y0 + 30))
    wall2 = Image.new("RGBA", (16 * 12, 16 * 3))
    keys2 = ["deep_cerulean_stone"] * 20 + [f"deep_cerulean_{k}_ore" for k in ("redstone", "lapis", "diamond", "emerald", "gold")]
    for x in range(12):
        for y in range(3):
            wall2.alpha_composite(tex[r.choice(keys2)], (x * 16, y * 16))
    sheet.alpha_composite(wall2.resize((wall2.width * 3, wall2.height * 3), Image.NEAREST), (20 + 600, y0 + 30))
    sheet.save(out)


def stone_tiling_sheet(tex, out):
    """Continuidad: cada bloque de piedra y mineral repetido 3×3."""
    F, Fm, f = AT.fonts()
    keys = ["cerulean_stone", "cerulean_cobblestone", "cerulean_stone_bricks", "polished_cerulean_stone", "deep_cerulean_stone",
            "cerulean_crystal_ore", "deep_cerulean_crystal_ore"] + \
           [f"cerulean_{k}_ore" for k in ORES] + [f"deep_cerulean_{k}_ore" for k in ORES]
    cw, ch, cols = 3 * 48 + 16, 3 * 48 + 34, 8
    rows = math.ceil(len(keys) / cols)
    sheet = Image.new("RGBA", (cols * cw, 56 + rows * ch), (60, 70, 80, 255))
    d = ImageDraw.Draw(sheet)
    d.text((16, 14), "Cerúlea — continuidad de piedra y minerales (3×3 repetidas)", fill=(240, 240, 240, 255), font=F)
    for i, key in enumerate(keys):
        x0, y0 = (i % cols) * cw + 8, 56 + (i // cols) * ch
        t = tex[key].resize((48, 48), Image.NEAREST)
        for a in range(3):
            for b in range(3):
                sheet.alpha_composite(t, (x0 + a * 48, y0 + 18 + b * 48))
        d.text((x0, y0 + 2), key.replace("cerulean_", "").replace("_ore", ""), fill=(230, 230, 230, 255), font=f)
    sheet.save(out)


def main(out_dir):
    os.makedirs(os.path.join(out_dir, "textures"), exist_ok=True)
    tex = make_textures()
    for k, v in tex.items():
        v.save(os.path.join(out_dir, "textures", k + ".png"))
    food_sheet(tex, os.path.join(out_dir, "cerulea_comestibles.png"))
    ore_sheet(tex, os.path.join(out_dir, "cerulea_piedra_minerales.png"))
    stone_tiling_sheet(tex, os.path.join(out_dir, "cerulea_continuidad_piedra.png"))
    print("ok", out_dir)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "cerulea_out")
