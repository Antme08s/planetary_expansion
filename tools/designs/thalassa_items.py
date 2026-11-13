"""Texturas (16×16) de los objetos y bloques de las criaturas de Thalassa (0.7.61), y la del escudo de caparazón.

Uso: python3 -I tools/designs/thalassa_items.py <carpeta_salida> <client_extra.jar>
El jar del cliente se usa para tomar la cubeta y la base del escudo de Minecraft.
"""
import io
import math
import random
import sys
import zipfile

from PIL import Image

T = (0, 0, 0, 0)


def img():
    return Image.new("RGBA", (16, 16), T)


def put(im, pts, c):
    for x, y in pts:
        if 0 <= x < 16 and 0 <= y < 16:
            im.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3])


def outline(im, c=(16, 16, 20)):
    """Contorno oscuro de 1 px alrededor de lo dibujado (como los iconos de Minecraft)."""
    px = im.load()
    add = []
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and px[x + dx, y + dy][3] > 0
                                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                add.append((x, y))
    put(im, add, c)
    return im


def autoshade(im, light=1.28, dark=0.72, outline_k=0.42):
    """Sombreado de pixel art: borde de arriba/izquierda más claro, de abajo/derecha más oscuro y contorno del color de
    cada zona (más oscuro), como los iconos de Minecraft."""
    src = im.copy()
    px, sp = im.load(), src.load()
    op = lambda x, y: 0 <= x < 16 and 0 <= y < 16 and sp[x, y][3] > 0
    for y in range(16):
        for x in range(16):
            if not op(x, y):
                continue
            c = sp[x, y][:3]
            if not op(x, y - 1) or not op(x - 1, y):
                c = shade(c, light)
            elif not op(x, y + 1) or not op(x + 1, y):
                c = shade(c, dark)
            px[x, y] = c + (255,)
    for y in range(16):
        for x in range(16):
            if op(x, y):
                continue
            nb = [sp[x + dx, y + dy][:3] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if op(x + dx, y + dy)]
            if nb:
                darkest = min(nb, key=sum)
                px[x, y] = shade(darkest, outline_k) + (255,)
    return im


def poly(im, pts, c):
    from PIL import ImageDraw
    ImageDraw.Draw(im).polygon(pts, fill=c + (255,))


def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))


def fish(body, belly, lure, fin, eye=(235, 240, 245)):
    im = img()
    # cuerpo de pez de lado (cabeza a la izquierda, ligeramente inclinado como los peces de Minecraft)
    for x in range(2, 12):
        k = (x - 2) / 9.0
        top = round(6.5 - 3.2 * math.sin(k * math.pi * 0.95))
        bot = round(9.5 + 2.6 * math.sin(k * math.pi * 0.9))
        for y in range(top, bot + 1):
            t = (y - top) / max(1, bot - top)
            c = mix(body, belly, max(0.0, (t - 0.45) / 0.55))
            if t < 0.45 and (x + y) % 3 == 0:
                c = shade(c, 0.86)  # escamas
            put(im, [(x, y)], c)
    # cola en horquilla
    poly(im, [(11, 7), (14, 3), (15, 4), (13, 8), (15, 12), (14, 13), (11, 9)], fin)
    # aleta dorsal y pectoral
    put(im, [(6, 3), (7, 3), (7, 2), (8, 3), (9, 4)], fin)
    put(im, [(6, 9), (7, 10), (6, 10)], shade(fin, 0.9))
    autoshade(im)
    # detalles encima del sombreado
    put(im, [(4, 6), (5, 6), (4, 7)], eye)
    put(im, [(5, 7)], (10, 10, 16))
    put(im, [(3, 9), (4, 9)], (150, 40, 55))
    put(im, [(3, 8)], (240, 240, 230))
    for x in (6, 8, 10):
        put(im, [(x, 10)], lure)
    # antena y señuelo
    put(im, [(5, 3), (4, 2), (3, 1), (2, 1)], shade(body, 1.6))
    put(im, [(1, 2), (1, 3), (0, 2), (0, 3)], lure)
    put(im, [(1, 2)], (240, 255, 252))
    return im


def blob(c, hi, seed=1):
    im = img()
    rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 6.0, (y - 9.0) / 5.0) + 0.08 * math.sin(x * 1.7 + y * 0.9)
            if d < 1 and y > 3:
                put(im, [(x, y)], shade(c, 0.85 + 0.35 * (1 - d)))
    autoshade(im, 1.2, 0.75, 0.45)
    put(im, [(5, 6), (6, 6), (5, 7), (4, 7)], hi)          # brillo
    put(im, [(9, 9), (10, 11), (7, 11)], shade(hi, 0.92))   # burbujas
    put(im, [(9, 10)], shade(c, 0.7))
    put(im, [(7, 4), (8, 3)], shade(c, 1.1))                # gota
    return im


def meat(raw, fat, cooked=False):
    im = img()
    poly(im, [(2, 9), (4, 5), (8, 3), (12, 4), (14, 7), (13, 11), (9, 13), (4, 13)], raw)
    autoshade(im)
    for k, (x, y) in enumerate([(5, 6), (7, 5), (9, 5), (11, 6), (4, 9), (6, 8), (8, 8), (10, 8), (12, 9), (6, 11), (9, 11)]):
        put(im, [(x, y), (x + 1, y)], fat if k % 2 == 0 else shade(fat, 0.9))
    if cooked:
        put(im, [(3, 10), (5, 12), (12, 5), (13, 8)], shade(raw, 0.6))
        put(im, [(8, 6), (10, 10)], shade(raw, 0.75))
    else:
        put(im, [(3, 8), (4, 7), (12, 10)], (200, 60, 50))  # restos de caparazón
    return im


def chitin():
    im = img()
    base = (60, 58, 64)
    poly(im, [(2, 5), (6, 2), (11, 2), (14, 6), (13, 12), (8, 14), (3, 12)], base)
    rnd = random.Random(3)
    px = im.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3]:
                c = shade(base, 1 + rnd.uniform(-0.1, 0.1))
                if (x - y) % 4 == 0:
                    c = shade(base, 0.78)  # crestas
                px[x, y] = c + (255,)
    autoshade(im, 1.35, 0.7)
    lava = [(5, 6), (6, 7), (7, 7), (8, 8), (9, 8), (10, 9), (6, 10), (7, 11), (10, 5), (11, 6)]
    put(im, lava, (255, 140, 50))
    put(im, [(7, 7), (9, 8)], (255, 210, 120))
    put(im, [(5, 4), (6, 3), (7, 3)], (130, 128, 136))
    return im


def leg(color, joint, cooked=False):
    im = img()
    segs = [((2, 14), (6, 10)), ((7, 9), (10, 6)), ((11, 5), (14, 2))]
    for (x0, y0), (x1, y1) in segs:
        poly(im, [(x0 - 1, y0), (x0, y0 + 1), (x1 + 1, y1), (x1, y1 - 1)], color)
    for x, y in ((6, 9), (10, 5)):
        put(im, [(x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)], joint)
    put(im, [(14, 1), (15, 1), (15, 0)], shade(color, 0.7))
    autoshade(im)
    under = (240, 196, 150) if not cooked else (255, 214, 170)
    put(im, [(3, 14), (4, 13), (8, 9), (9, 8), (12, 5), (13, 4)], under)
    spike = shade(color, 0.5) if not cooked else (120, 30, 20)
    put(im, [(4, 10), (8, 6), (12, 2)], spike)
    return im


def shell():
    im = img()
    for y in range(16):
        for x in range(16):
            if ((x - 7.5) / 7.2) ** 2 + ((y - 9.5) / 5.6) ** 2 < 1 and y > 4:
                put(im, [(x, y)], (46, 86, 168) if (x * 3 + y) % 5 else (60, 104, 190))
    autoshade(im)
    for x, y in ((4, 5), (7, 4), (10, 5), (5, 8), (9, 8), (7, 11), (3, 10), (12, 10)):
        put(im, [(x, y)], (150, 190, 240))
        put(im, [(x, y - 1)], (130, 170, 230))
        put(im, [(x, y - 2)], (20, 34, 70))
    put(im, [(3, 13), (4, 14), (11, 14), (12, 13)], (230, 170, 120))  # borde de abajo crema
    return im


def claw():
    im = img()
    blue, tip = (52, 94, 178), (28, 32, 56)
    poly(im, [(1, 14), (2, 9), (5, 6), (9, 6), (10, 9), (7, 13), (3, 15)], blue)     # palma
    poly(im, [(8, 6), (10, 2), (13, 0), (14, 1), (12, 4), (10, 8)], tip)               # dedo de arriba
    poly(im, [(10, 9), (13, 7), (15, 6), (15, 8), (12, 11)], tip)                      # dedo de abajo
    autoshade(im)
    put(im, [(3, 10), (4, 9), (5, 8)], (150, 190, 240))
    put(im, [(2, 13), (3, 13), (4, 12)], (236, 180, 130))
    put(im, [(6, 9), (7, 10)], (130, 170, 230))
    return im


def fang():
    im = img()
    pts = []
    for t in range(12):
        cx = 4.5 + t * 0.62 + 2.4 * math.sin(t / 11 * math.pi * 0.85)
        w = 2.4 * (1 - t / 12) + 0.4
        for x in range(16):
            if abs(x - cx) <= w:
                pts.append((x, 2 + t))
    put(im, pts, (236, 232, 214))
    put(im, [(3, 1), (4, 1), (5, 1), (6, 1), (7, 1), (4, 0), (5, 0), (6, 0)], (160, 50, 64))  # encía
    autoshade(im, 1.12, 0.8)
    put(im, [(5, 4), (6, 6), (7, 8)], (255, 255, 248))
    return im


def harpoon():
    im = img()
    wood, iron = (130, 92, 54), (160, 164, 172)
    for t in range(11):
        put(im, [(1 + t, 14 - t), (2 + t, 14 - t)], wood if t % 4 else shade(wood, 0.8))
    for t in (2, 3, 7):
        put(im, [(1 + t, 14 - t), (2 + t, 14 - t), (1 + t, 13 - t)], iron)   # anillas
    poly(im, [(11, 4), (13, 1), (15, 0), (14, 2), (12, 5)], (236, 232, 214))   # punta de colmillo
    put(im, [(10, 3), (9, 3), (12, 6), (12, 7)], (210, 204, 186))               # púas
    autoshade(im, 1.25, 0.75)
    put(im, [(14, 1)], (255, 255, 250))
    put(im, [(3, 12), (4, 13)], (90, 60, 34))
    return im


def organ():
    im = img()
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 5.2, (y - 8.5) / 5.8) + 0.06 * math.sin(x * 2.1 + y)
            if d < 1:
                put(im, [(x, y)], shade((200, 150, 162), 1.12 - 0.3 * d))
    autoshade(im, 1.2, 0.75)
    for t in range(6):
        put(im, [(5 + t, 6 + (t % 2))], (150, 60, 86))
        put(im, [(4 + t, 10 - (t % 2))], (150, 60, 86))
    put(im, [(7, 8), (8, 8), (8, 9)], (255, 120, 150))  # núcleo que brilla
    put(im, [(5, 4), (6, 4)], (255, 220, 228))
    put(im, [(7, 14), (8, 15), (8, 14)], (170, 70, 90))  # resto de nervio
    return im


def sonar():
    im = img()
    copper = (194, 112, 72)
    poly(im, [(1, 4), (14, 4), (14, 14), (1, 14)], copper)
    autoshade(im)
    for y in range(6, 13):
        for x in range(3, 13):
            put(im, [(x, y)], (24, 10, 18))
    cx, cy = 7.5, 9.0
    for a in range(0, 360, 5):
        for r in (1.6, 3.2):
            put(im, [(round(cx + r * math.cos(math.radians(a))), round(cy + r * math.sin(math.radians(a))))], (110, 40, 64))
    for t in range(4):  # barrido
        put(im, [(8 + t, 8 - t // 2)], (255, 130, 160))
    put(im, [(7, 9), (8, 9)], (255, 200, 210))
    put(im, [(4, 10), (10, 7)], (255, 230, 120))  # ecos
    put(im, [(2, 5), (13, 5), (2, 13), (13, 13)], (240, 170, 120))  # remaches
    put(im, [(12, 3), (12, 2), (12, 1), (11, 1)], (90, 90, 96))  # antena
    put(im, [(13, 1)], (255, 120, 150))
    return im


def barbel():
    im = img()
    pts = []
    for t in range(12):
        x = 6 + round(2.6 * math.sin(t / 2.6))
        pts += [(x, 1 + t), (x + 1, 1 + t)]
    put(im, pts, (240, 120, 150))
    put(im, [(5, 13), (6, 13), (7, 13), (8, 13), (5, 14), (6, 14), (7, 14), (8, 14), (6, 15), (7, 15)], (255, 150, 180))
    autoshade(im, 1.25, 0.75, 0.38)
    put(im, [(6, 13), (6, 14)], (255, 235, 242))      # bulbo que brilla
    for t in (3, 6, 9):
        put(im, [(6 + round(2.6 * math.sin(t / 2.6)), 1 + t)], (255, 200, 215))
    return im


def lamp(glow, frame, seed, kind="gel"):
    im = Image.new("RGBA", (16, 16))
    rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            inner = x in (1, 14) or y in (1, 14)
            corner = (x in (0, 1, 14, 15)) and (y in (0, 1, 14, 15))
            if edge or corner:
                c = shade(frame, 0.8 if (x + y) % 2 else 1.0)
            elif inner:
                c = shade(frame, 1.35)
            else:
                d = math.hypot(x - 7.5, y - 7.5) / 6.5
                c = shade(glow, 1.2 - 0.45 * d + rnd.uniform(-0.05, 0.05))
                if kind == "gel" and rnd.random() < 0.1:
                    c = mix(c, (255, 255, 255), 0.5)
                if kind == "abyssal" and (abs(x - 7.5 - 2.5 * math.sin(y / 2.2)) < 0.8 or abs(x - 5 - 2 * math.cos(y / 2.0)) < 0.6):
                    c = shade(glow, 1.35)  # barbillas dentro
            im.putpixel((x, y), c + (255,))
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        im.putpixel((x, y), shade(frame, 1.6) + (255,))
    return im


def bucket(client_jar):
    base = Image.open(io.BytesIO(zipfile.ZipFile(client_jar).read("assets/minecraft/textures/item/water_bucket.png"))).convert("RGBA")
    f = fish((24, 36, 62), (172, 186, 196), (110, 255, 230), (58, 84, 120)).crop((0, 0, 16, 16)).resize((11, 11), Image.NEAREST)
    base.alpha_composite(f, (3, 1))
    return base


def shield(client_jar):
    """Textura del escudo (64×64, la disposición del escudo de Minecraft): placa de caparazón azul abombada, con púas
    claras de punta oscura, borde crema-anaranjado con sombra y el reverso más oscuro."""
    base = Image.open(io.BytesIO(zipfile.ZipFile(client_jar).read("assets/minecraft/textures/entity/shield_base_nopattern.png"))).convert("RGBA")
    rnd = random.Random(11)
    px = base.load()
    spikes = {(3, 4), (8, 4), (5, 8), (6, 8), (2, 11), (9, 11), (4, 14), (7, 14), (5, 18), (6, 18)}
    for y in range(0, 23):
        for x in range(0, 26):
            if px[x, y][3] == 0:
                continue
            front = x < 13
            fx, fy = (x - 1) if front else (x - 14), y - 1
            if fx < 0 or fx > 11 or fy < 0 or fy > 21:      # cantos de la placa
                px[x, y] = (200, 130, 80, 255)
                continue
            rim = fx in (0, 11) or fy in (0, 21)
            if rim:
                c = (236, 160, 100) if (fx == 0 or fy == 0) else (180, 110, 64)
            else:
                d = math.hypot((fx - 5.5) / 6, (fy - 10.5) / 11)
                c = shade((50, 92, 176), 1.25 - 0.45 * d + rnd.uniform(-0.05, 0.05))
                if (fx * 3 + fy) % 7 == 0:
                    c = shade(c, 0.85)
                if fx in (1, 10) or fy in (1, 20):
                    c = shade(c, 0.78)
                if not front:
                    c = shade(c, 0.65)
                elif (fx, fy) in spikes:
                    c = (150, 190, 240)
                elif (fx, fy - 1) in spikes:
                    c = (20, 34, 70)
                elif (fx, fy + 1) in spikes:
                    c = (110, 150, 214)
            px[x, y] = c + (255,)
    return base


def build(out, client_jar):
    items = {
        "lantern_fish": fish((24, 36, 62), (172, 186, 196), (110, 255, 230), (58, 84, 120)),
        "cooked_lantern_fish": fish((120, 72, 40), (210, 160, 110), (230, 200, 120), (150, 96, 60)),
        "glow_gel": blob((80, 220, 200), (220, 255, 250), 1),
        "crab_meat": meat((236, 150, 140), (250, 225, 220)),
        "cooked_crab_meat": meat((214, 110, 70), (250, 210, 170), True),
        "volcanic_chitin": chitin(),
        "king_crab_leg": leg((52, 94, 178), (236, 150, 90)),
        "cooked_king_crab_leg": leg((210, 70, 50), (250, 170, 110), True),
        "king_shell": shell(),
        "king_claw": claw(),
        "abyssal_fang": fang(),
        "harpoon": harpoon(),
        "sonar_organ": organ(),
        "sonar": sonar(),
        "glowing_barbel": barbel(),
        "lantern_fish_bucket": bucket(client_jar),
    }
    for name, im in items.items():
        im.save(f"{out}/item_{name}.png")
    lamp((80, 220, 200), (40, 70, 90), 4, "gel").save(f"{out}/block_gel_lamp.png")
    lamp((255, 130, 165), (80, 40, 60), 5, "abyssal").save(f"{out}/block_abyssal_lamp.png")
    shield(client_jar).save(f"{out}/entity_king_shell_shield.png")
    # hoja de vista previa
    names = list(items)
    sheet = Image.new("RGBA", (len(names) * 72 + 3 * 72, 90), (30, 34, 40, 255))
    for i, n in enumerate(names):
        sheet.alpha_composite(items[n].resize((64, 64), Image.NEAREST), (i * 72 + 4, 4))
    for j, b in enumerate(("block_gel_lamp", "block_abyssal_lamp")):
        sheet.alpha_composite(Image.open(f"{out}/{b}.png").resize((64, 64), Image.NEAREST), ((len(names) + j) * 72 + 4, 4))
    sheet.save(f"{out}/items_preview.png")
    print("ok")


if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2])
