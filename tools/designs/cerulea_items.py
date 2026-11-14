"""Texturas (16×16) de lo que sueltan las criaturas de Cerúlea y de lo que se fabrica con ello (2026-11-13).

Uso: python3 -I tools/designs/cerulea_items.py <carpeta_salida> <client_extra.jar>
Usa las herramientas de thalassa_items.py (autoshade, poly, lamp…). El jar del cliente se usa para la cubeta.
"""
import io
import math
import os
import random
import sys
import zipfile

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from thalassa_items import autoshade, img, mix, poly, put, shade  # noqa: E402


def font(sz):
    from PIL import ImageFont
    try:
        return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", sz)
    except OSError:
        return ImageFont.load_default()


# ------------------------------------------------------------------ herramientas de detalle (v2)

VANILLA = {}


def vanilla(name):
    """Textura de Minecraft (solo para tomar la silueta o la cubeta; se recolorea)."""
    return VANILLA[name].copy()


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def ramp_color(ramp, t):
    t = min(1.0, max(0.0, t)) * (len(ramp) - 1)
    i = min(len(ramp) - 2, int(t))
    return mix(ramp[i], ramp[i + 1], t - i)


def recolor(im, ramp, region=None):
    """Recolorea por luminancia (se conserva el sombreado del dibujo original). `region(x, y)` limita la zona."""
    im = im.copy()
    px = im.load()
    pts = [(x, y) for y in range(16) for x in range(16) if px[x, y][3] and (region is None or region(x, y))]
    ls = [lum(px[x, y]) for x, y in pts]
    lo, hi = min(ls), max(ls)
    for (x, y), l in zip(pts, ls):
        px[x, y] = ramp_color(ramp, (l - lo) / max(1, hi - lo)) + (255,)
    return im


def fill(im, inside, base, seed=0, light=1.25, dark=0.7, noise=0.05, outline=0.4):
    """Rellena la forma `inside(x, y)` con degradado de luz (arriba a la izquierda), ruido de píxel, borde de luz y
    de sombra, y contorno oscuro del mismo color (como los iconos de Minecraft)."""
    rnd = random.Random(seed)
    px = im.load()
    pts = [(x, y) for y in range(16) for x in range(16) if inside(x, y)]
    if not pts:
        return im
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    S = set(pts)
    for x, y in pts:
        g = ((x - x0) / max(1, x1 - x0) + (y - y0) / max(1, y1 - y0)) / 2
        k = light - (light - dark) * g * 0.75 + rnd.uniform(-noise, noise)
        if (x, y - 1) not in S or (x - 1, y) not in S:
            k += 0.12
        elif (x, y + 1) not in S or (x + 1, y) not in S:
            k -= 0.14
        px[x, y] = shade(base, k) + (255,)
    for y in range(16):
        for x in range(16):
            if (x, y) in S or px[x, y][3]:
                continue
            if any((x + dx, y + dy) in S for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = shade(base, outline) + (255,)
    return im


def ell(cx, cy, rx, ry, rot=0.0):
    ca, sa = math.cos(rot), math.sin(rot)

    def f(x, y):
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        u, v = dx * ca + dy * sa, -dx * sa + dy * ca
        return (u / rx) ** 2 + (v / ry) ** 2 <= 1
    return f


# ------------------------------------------------------------------ comida

def steak(meat, fat, cooked=False):
    """Filete de pastoreador: losa gruesa (cara de arriba y canto más oscuro), vetas de grasa, hueso con médula;
    cocinado con marcas de parrilla."""
    im = img()
    top = ell(7.2, 7.0, 6.4, 4.4, -0.35)
    side = lambda x, y: ell(7.2, 9.2, 6.4, 4.4, -0.35)(x, y) and not top(x, y)
    fill(im, side, shade(meat, 0.62), 2, 1.0, 0.8, 0.04)
    fill(im, top, meat, 3, 1.2, 0.82, 0.05, 0.5)
    if not cooked:
        for t in range(10):                                     # vetas de grasa (marmoleado)
            x = 3 + t
            y = round(6.5 + 1.8 * math.sin(t * 0.9) - t * 0.25)
            if top(x, y):
                put(im, [(x, y)], fat if t % 3 else shade(fat, 0.9))
        for x, y in ((5, 9), (9, 8), (10, 5), (6, 4)):
            put(im, [(x, y)], shade(fat, 0.94))
        put(im, [(4, 5), (5, 4), (7, 3)], shade(meat, 1.4))     # brillo
    else:
        for k in (0, 1, 2):                                     # marcas de parrilla
            for t in range(7):
                x, y = 3 + k * 3 + t // 2, 3 + t
                if top(x, y):
                    put(im, [(x, y)], shade(meat, 0.55))
        for x, y in ((5, 7), (9, 6), (11, 9)):
            put(im, [(x, y)], shade(fat, 0.85))
        put(im, [(4, 5), (6, 4)], shade(meat, 1.3))
    # hueso: asoma por la derecha, con médula
    put(im, [(12, 8), (13, 8), (14, 9), (14, 10), (13, 11), (12, 11), (12, 10), (12, 9), (13, 9), (13, 10)], (230, 226, 210))
    put(im, [(13, 9), (13, 10)], (186, 120, 110) if not cooked else (150, 100, 70))
    put(im, [(15, 9), (15, 10), (14, 11), (12, 12), (13, 12)], (150, 146, 132))
    put(im, [(12, 7), (13, 7), (14, 8)], (246, 244, 236))
    return im


def drumstick(meat, cooked=False):
    """Carne de saltarín: muslito redondo con piel brillante, pliegue y hueso con nudo."""
    im = img()
    for t in range(6):                                          # hueso primero
        put(im, [(9 + t, 9 + t), (10 + t, 9 + t), (9 + t, 10 + t)], (226, 222, 206))
    put(im, [(14, 12), (15, 13), (13, 15), (12, 14), (15, 15)], (240, 238, 228))
    put(im, [(15, 14), (14, 15)], (170, 166, 150))
    fill(im, ell(6.5, 6.3, 5.2, 4.5, 0.6), meat, 4, 1.22, 0.74, 0.05, 0.45)
    put(im, [(3, 4), (4, 3), (5, 3), (3, 5)], shade(meat, 1.45))     # brillo de la piel
    put(im, [(8, 9), (9, 8), (10, 7)], shade(meat, 0.7))             # pliegue
    if cooked:
        rnd = random.Random(5)
        for _ in range(7):
            x, y = rnd.randint(3, 10), rnd.randint(3, 10)
            if ell(6.5, 6.3, 5.2, 4.5, 0.6)(x, y):
                put(im, [(x, y)], shade(meat, rnd.choice((0.65, 1.25))))
    else:
        put(im, [(5, 7), (7, 5), (6, 9)], shade(meat, 1.15))
    return im


def frog_legs(skin, cooked=False):
    """Ancas de croador: dos patas cruzadas en X (muslo grueso arriba, se afina hacia el tobillo) con pies palmeados de
    tres dedos; crudas con manchas azules, cocinadas doradas con tostado."""
    im = img()
    for side in (-1, 1):
        def leg(x, y, side=side):
            t = (y - 2) / 10.0
            if not 0 <= t <= 1:
                return False
            cx = 7.5 - side * (4.2 - t * 8.4)
            w = 2.1 - 1.2 * t
            return abs(x + 0.5 - cx) <= w
        fill(im, leg, skin, 7 + side, 1.25, 0.75, 0.05, 0.45)
    web = shade(skin, 1.2) if not cooked else shade(skin, 0.9)
    for side in (-1, 1):
        fx = round(7.5 + side * 4.6)
        put(im, [(fx - 1, 13), (fx, 13), (fx + 1, 13), (fx - 2, 14), (fx - 1, 14), (fx, 14), (fx + 1, 14), (fx + 2, 14)], web)
        put(im, [(fx - 2, 15), (fx, 15), (fx + 2, 15)], shade(web, 0.65))     # puntas de los dedos
    if cooked:
        rnd = random.Random(3)
        for _ in range(8):
            put(im, [(rnd.randint(2, 13), rnd.randint(2, 10))], shade(skin, 0.68))
        put(im, [(3, 2), (12, 2)], shade(skin, 1.35))
    else:
        put(im, [(3, 3), (4, 3), (12, 4), (11, 3), (6, 6), (9, 7)], (46, 90, 170))
        put(im, [(4, 2), (11, 2), (5, 2)], (214, 236, 200))
    return im


def ribbon_fish(body, belly, fin, cooked=False):
    """Pez de cinta de lado: cuerpo alto con escamas, línea de agallas, dos ojos, aleta del lomo en cinta ondulada,
    cinta del vientre y cola en abanico con radios."""
    im = img()

    def bounds(x):
        k = (x - 2) / 9.0
        return round(7 - 3.6 * math.sin(k * math.pi * 0.95)), round(9 + 3.0 * math.sin(k * math.pi * 0.9))

    def shape(x, y):
        if not 2 <= x <= 11:
            return False
        a, b = bounds(x)
        return a <= y <= b
    tail = lambda x, y: 11 <= x <= 15 and abs(y - 8) <= (x - 10) * 1.3 and not (x == 15 and abs(y - 8) < 2)
    fill(im, tail, fin, 3, 1.2, 0.8, 0.05, 0.45)
    fill(im, shape, body, 4, 1.22, 0.78, 0.04, 0.42)
    px = im.load()
    for y in range(16):
        for x in range(16):
            if shape(x, y):
                a, b = bounds(x)
                t = (y - a) / max(1, b - a)
                if t > 0.55:
                    px[x, y] = mix(px[x, y][:3], belly, min(1, (t - 0.55) / 0.35)) + (255,)
                elif (x + 2 * y) % 4 == 0 and x > 4:
                    px[x, y] = shade(px[x, y][:3], 0.84) + (255,)           # escamas
    for y in (5, 8, 11):
        for x in range(12, 15):
            if tail(x, y):
                put(im, [(x, y)], shade(fin, 1.25))                         # radios de la cola
    wave = [(4, 3), (5, 2), (6, 2), (7, 1), (8, 1), (9, 2), (10, 1), (11, 0), (12, 0)]
    put(im, wave, fin)
    put(im, [(p[0], p[1] + 1) for p in wave[2:6]], shade(fin, 0.8))
    put(im, [(6, 12), (7, 13), (8, 13), (9, 14), (10, 15)], shade(fin, 0.9))
    put(im, [(5, 5), (5, 6), (5, 7), (5, 8)], shade(body, 0.62))           # agallas
    if not cooked:
        put(im, [(3, 6), (4, 6)], (244, 226, 130))
        put(im, [(4, 6)], (12, 12, 18))
        put(im, [(3, 8)], (230, 210, 120))
        for x in range(6, 11):
            put(im, [(x, 8)], mix(body, (170, 240, 255), 0.65))             # línea lateral
        put(im, [(4, 4), (6, 4)], shade(body, 1.5))
    else:
        put(im, [(3, 6), (4, 6), (3, 8)], (70, 44, 30))
        for k in range(3):
            for t in range(4):
                put(im, [(6 + k * 2 + t // 2, 5 + t)], shade(body, 0.62))   # marcas de parrilla
    return im


# ------------------------------------------------------------------ materiales

def hide(ramp, stripe=None, tufts=None):
    """Cuero / piel: silueta y sombreado del cuero de Minecraft, recoloreado, con rayas (que siguen la curva de la
    piel) y mechones de pelo en el borde."""
    im = recolor(vanilla("leather"), ramp)
    px = im.load()
    if stripe:
        for y in range(16):
            for x in range(16):
                if px[x, y][3] and lum(px[x, y]) > lum(ramp[0]) + 8 and \
                        (y + 1.3 * math.sin(x * 0.7) + 40) % 4.2 < 1.4:
                    px[x, y] = mix(px[x, y][:3], stripe, 0.8) + (255,)
    if tufts:
        for x, y in ((1, 6), (14, 6), (1, 11), (14, 11)):
            if not px[x, y][3] and (px[x + (1 if x < 8 else -1), y][3]):
                px[x, y] = tufts + (255,)
    return im


def bulb():
    """Bulbo de antena: esfera con capas de luz, núcleo brillante y destellos, sobre un trozo de antena con anillos."""
    im = img()
    for t in range(7):
        c = (52, 84, 120) if t % 3 else (80, 120, 160)
        put(im, [(3 + t // 2, 15 - t), (4 + t // 2, 15 - t)], c)
    put(im, [(2, 15), (3, 15)], (34, 56, 84))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 9.5, y + 0.5 - 6.0) / 5.0
            if d < 1:
                c = ramp_color([(30, 120, 120), (60, 200, 176), (130, 250, 220), (230, 255, 250)], (1 - d) ** 0.8)
                if 0.55 < d < 0.68:
                    c = mix(c, (40, 150, 140), 0.4)                    # anillo interior
                put(im, [(x, y)], c)
    autoshade(im, 1.08, 0.86, 0.5)
    put(im, [(7, 3), (8, 3), (7, 4)], (250, 255, 252))
    put(im, [(12, 9), (11, 10)], (170, 255, 235))
    put(im, [(14, 3)], (200, 255, 245))
    put(im, [(15, 6)], (150, 240, 220))
    return im


def foot():
    """Pata de saltarín: pie largo de tres dedos con garras oscuras, pelaje con motas y mechón claro en el tobillo."""
    im = img()
    leg = lambda x, y: (8 <= x <= 12 and 1 <= y <= 7 and abs(x - (11 - y * 0.25)) <= 2.0) or ell(7.0, 10.0, 4.6, 2.6, -0.5)(x, y)
    fill(im, leg, (112, 196, 168), 6, 1.22, 0.75, 0.06, 0.42)
    rnd = random.Random(2)
    for _ in range(10):
        x, y = rnd.randint(3, 12), rnd.randint(2, 12)
        if leg(x, y):
            put(im, [(x, y)], (84, 168, 150))                                 # motas
    for (x, y) in ((2, 12), (3, 13), (4, 14), (6, 14)):                       # dedos y garras
        put(im, [(x, y)], (96, 176, 152))
    put(im, [(1, 13), (2, 14), (3, 15), (6, 15)], (30, 44, 50))
    for x in range(9, 14):                                                    # mechón del tobillo
        put(im, [(x, 0 if x % 2 else 1)], (232, 246, 236))
    put(im, [(9, 1), (10, 1), (11, 1), (12, 1)], (214, 236, 222))
    put(im, [(9, 3), (8, 6), (5, 9)], (160, 226, 200))
    return im


def membrane():
    """Membrana de planeador: trozo de membrana con el hueso del dedo en el borde de arriba, nervios que se ramifican,
    degradado a azul oscuro y puntos luminosos en el borde."""
    im = img()
    shape = lambda x, y: (1 <= x <= 14 and y >= 2 + x * 0.05 and y <= 14 - abs(x - 6) * 0.35 and y <= 2 + (14 - x) * 1.1 + 2)
    fill(im, shape, (56, 100, 180), 8, 1.18, 0.62, 0.04, 0.38)
    for t in range(14):                                                       # hueso del borde
        put(im, [(1 + t, 1 + round(t * 0.05))], (150, 170, 196) if t % 4 else (190, 204, 222))
    for (sx, sy, dx, dy, n) in ((2, 2, 0.35, 1, 11), (5, 2, 0.75, 1, 9), (9, 2, 0.9, 0.8, 6)):
        x, y = float(sx), float(sy)
        for i in range(n):
            x += dx
            y += dy
            if shape(int(x), int(y)):
                put(im, [(int(x), int(y))], (106, 156, 226))
            if i == n // 2:                                                    # rama
                for j in range(3):
                    if shape(int(x) + j + 1, int(y) + j // 2):
                        put(im, [(int(x) + j + 1, int(y) + j // 2)], (96, 142, 214))
    for x, y in ((13, 4), (11, 8), (8, 12), (4, 13)):
        put(im, [(x, y)], (170, 250, 255))
    return im


def fang(base, gum, curve=2.4, length=12, cracked=False):
    """Colmillo: raíz con trozo de encía, esmalte con degradado y brillo, punta algo más oscura."""
    im = img()
    y0 = 2 + (14 - length) // 2

    def f(x, y):
        t = y - y0
        if not 0 <= t < length:
            return False
        cx = 4.5 + t * 0.62 + curve * math.sin(t / (length - 1) * math.pi * 0.85)
        w = 2.5 * (1 - t / length) + 0.45
        return abs(x + 0.5 - cx) <= w
    fill(im, f, base, 11, 1.14, 0.78, 0.03, 0.42)
    px = im.load()
    for y in range(y0 + length - 3, y0 + length):
        for x in range(16):
            if f(x, y):
                px[x, y] = shade(px[x, y][:3], 0.86) + (255,)
    put(im, [(x, y0 - 1) for x in (3, 4, 5, 6, 7)] + [(4, y0 - 2), (5, y0 - 2), (6, y0 - 2)], gum)
    put(im, [(3, y0 - 1), (7, y0 - 1)], shade(gum, 0.7))
    put(im, [(5, y0 - 2)], shade(gum, 1.4))
    put(im, [(5, y0 + 2), (6, y0 + 4), (7, y0 + 6)], (255, 255, 250))
    if cracked:
        put(im, [(7, y0 + 3), (8, y0 + 4), (8, y0 + 5)], shade(base, 0.7))
    return im


def scale():
    """Escama luminosa del acechador: escama en forma de gota, oscura, con el borde turquesa que brilla, una cresta
    central y manchitas luminosas."""
    im = img()
    px = im.load()
    for y in range(1, 15):
        for x in range(16):
            dx, dy = (x - 7.5) / (5.8 * (0.55 + 0.45 * (y / 14))), (y - 9.0) / 6.2
            if dx * dx + dy * dy < 1 or (y < 9 and abs(x - 7.5) < 0.9 + 4.4 * (y - 1) / 8):
                d = math.hypot(dx, dy)
                px[x, y] = shade((30, 44, 62), 1.25 - 0.45 * min(1, d)) + (255,)
    src = im.copy().load()
    for y in range(16):
        for x in range(16):
            if src[x, y][3] and any(not (0 <= x + a < 16 and 0 <= y + b < 16) or src[x + a, y + b][3] == 0
                                    for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = ((80, 232, 214) if y < 12 else (60, 190, 180)) + (255,)
    for y in range(3, 13):
        put(im, [(7, y)], (54, 74, 96) if y % 3 else (70, 96, 120))     # cresta central
    put(im, [(5, 8), (10, 7), (9, 11), (5, 11)], (110, 255, 230))
    put(im, [(7, 2), (8, 2)], (200, 255, 245))
    return im


def scale_block():
    """Bloque de escamas: escamas redondas superpuestas como las de un pez (2 por fila, filas de 4 px desplazadas
    media escama), oscuras, con el borde turquesa luminoso. Se repite sin costuras."""
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            hit = None
            for row in range(-2, 6):                      # la fila de más abajo tapa a la de arriba
                off = 4 if row % 2 else 0
                for col in range(-1, 3):
                    cx, cy = col * 8 + off + 3.5, row * 4 - 0.5
                    dx = ((x - cx + 8) % 16) - 8
                    dy = y - cy
                    r = math.hypot(dx / 4.3, dy / 4.6)
                    if r < 1 and dy > -0.5:
                        hit = (r, dx, dy)
            if hit is None:
                c = (16, 24, 34)
            else:
                r, dx, dy = hit
                c = shade((36, 54, 76), 1.3 - 0.55 * r - 0.04 * dx)
                if r > 0.85:
                    c = (90, 240, 220) if dx < 1 else (58, 186, 176)
                elif r < 0.3 and dy < 1.5:
                    c = shade((60, 86, 110), 1.1)
            px[x, y] = c + (255,)
    return im


def slime():
    """Baba de croador: la bola de slime de Minecraft recoloreada a verde azulado, con burbujas y un hilo que gotea."""
    im = recolor(vanilla("slime_ball"), [(22, 70, 74), (50, 140, 132), (96, 200, 178), (200, 255, 240)])
    put(im, [(10, 10), (9, 11)], (150, 230, 214))
    put(im, [(6, 11)], (120, 214, 196))
    put(im, [(8, 15)], (60, 150, 140))
    return im


# ------------------------------------------------------------------ armas

def dagger():
    """Daga de colmillo: hoja de colmillo con nervio central y filo claro, guarda de hueso con nudos, empuñadura
    envuelta en piel rayada (verde y azul) y pomo con un cristal cerúleo."""
    im = img()
    for t in range(9):                                   # hoja
        x, y = 6 + t, 9 - t
        put(im, [(x, y), (x + 1, y), (x, y - 1)], (226, 222, 204))
    put(im, [(14, 1), (15, 0), (15, 1)], (240, 238, 226))
    autoshade(im, 1.15, 0.78, 0.45)
    for t in range(7):
        put(im, [(7 + t, 7 - t)], (255, 255, 248))       # filo
        put(im, [(7 + t, 8 - t)], (196, 190, 168))       # nervio
    put(im, [(3, 8), (4, 9), (5, 10), (6, 11), (7, 12), (8, 13)], (206, 200, 180))     # guarda
    put(im, [(3, 7), (8, 14)], (232, 228, 212))
    put(im, [(4, 8), (7, 13)], (160, 154, 134))
    for t in range(4):                                    # empuñadura
        c = (44, 96, 74) if t % 2 else (36, 66, 128)
        put(im, [(2 + t, 13 - t), (3 + t, 13 - t), (2 + t, 13 - t + 1)], c)
        put(im, [(3 + t, 14 - t)], shade(c, 0.6))
    put(im, [(0, 15), (1, 15), (0, 14), (1, 14)], (90, 210, 220))                      # cristal del pomo
    put(im, [(0, 14)], (200, 255, 255))
    put(im, [(1, 15)], (40, 120, 150))
    return im


def fang_arrow():
    """Flecha de colmillo: asta con veta, punta de colmillo con sombra y plumas de dos colores con rayas."""
    im = img()
    for t in range(10):
        put(im, [(3 + t, 12 - t)], (110, 84, 60) if t % 3 else (84, 62, 44))
    put(im, [(4, 12), (7, 9), (10, 6)], (70, 52, 36))
    poly(im, [(11, 2), (13, 0), (15, 0), (15, 2), (13, 4), (11, 4)], (226, 222, 204))   # punta
    put(im, [(13, 1), (14, 1), (12, 2)], (255, 255, 248))
    put(im, [(14, 3), (13, 4), (15, 2)], (170, 164, 140))
    put(im, [(10, 3), (12, 5)], (40, 34, 30))                                           # amarre
    for t in range(4):                                                                   # plumas
        put(im, [(1 + t, 10 - t), (1 + t, 11 - t)], (52, 96, 170) if t % 2 else (70, 124, 200))
        put(im, [(4 + t, 14 - t), (5 + t, 14 - t)], (44, 140, 110) if t % 2 else (66, 170, 134))
    put(im, [(0, 12), (1, 13), (2, 14), (3, 15)], (30, 50, 70))
    put(im, [(2, 12), (3, 13)], (20, 30, 40))
    return im


def fish_bucket(fish_ramp, eye=(244, 226, 130)):
    """Cubeta con pez: la del bacalao de Minecraft con el pez recoloreado (solo la parte de arriba, el pez)."""
    cod, water = vanilla("cod_bucket"), vanilla("water_bucket")
    cp, wp = cod.load(), water.load()
    region = lambda x, y: y <= 7 and cp[x, y] != wp[x, y] and not (x in (2, 13) or (y == 7 and x >= 10))
    im = recolor(cod, fish_ramp, region)
    put(im, [(5, 3)], eye)
    put(im, [(6, 3)], (12, 12, 18))
    put(im, [(9, 1), (10, 1), (11, 2)], (80, 200, 160))      # cinta del lomo asomando
    return im


def croaker_bucket():
    """Cubeta con croador: la del bacalao de Minecraft con el animal recoloreado verde azulado, manchas azules y ojo
    dorado."""
    cod, water = vanilla("cod_bucket"), vanilla("water_bucket")
    cp, wp = cod.load(), water.load()
    region = lambda x, y: y <= 7 and cp[x, y] != wp[x, y] and not (x in (2, 13) or (y == 7 and x >= 10))
    im = recolor(cod, [(20, 70, 60), (44, 130, 112), (70, 176, 150), (200, 236, 200)], region)
    put(im, [(5, 2), (6, 2)], (232, 190, 70))
    put(im, [(6, 2)], (14, 16, 18))
    put(im, [(8, 3), (10, 2), (9, 5)], (46, 90, 170))
    return im


def arrow_entity():
    """Textura de la flecha de colmillo volando: la de Minecraft con las plumas azul y verde y la punta de colmillo."""
    im = vanilla("arrow_entity")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            c = px[x, y]
            if not c[3]:
                continue
            grey = abs(c[0] - c[1]) < 6 and abs(c[1] - c[2]) < 6
            if grey and x < 6:
                px[x, y] = ((52, 96, 170) if (x + y) % 2 else (44, 140, 110)) + (255,)
            elif grey and x >= 11:
                px[x, y] = mix((226, 222, 204), (255, 255, 246), (c[0] - 150) / 105.0) + (255,)
    return im


def armor_plate():
    """Placa de coraza del Ironjaw: placa gruesa azul pizarra con borde biselado, una grieta turquesa que brilla y
    marcas de crecimiento."""
    im = img()
    shape = lambda x, y: 2 <= x <= 13 and 2 <= y <= 13 and (x + y) >= 6 and (x + y) <= 24 and abs(x - y) <= 9  # noqa: E731
    fill(im, shape, (62, 82, 110), 21, 1.3, 0.72, 0.04, 0.4)
    for t in range(8):
        put(im, [(4 + t, 10 - t // 2 + (1 if t % 3 == 0 else 0))], (90, 232, 214))
    put(im, [(6, 9), (9, 7)], (180, 255, 245))
    for k in range(3):
        for t in range(6):
            put(im, [(3 + t + k * 3, 12 - k)], shade((62, 82, 110), 0.8))
    return im


def ironjaw_blade():
    """Cuchilla de mandíbula: pétalo de hueso en punta con una fila de dientes de sierra en el filo y la carne roja de
    la base."""
    im = img()
    blade = lambda x, y: 3 <= y <= 14 and abs(x + 0.5 - 8) <= (y - 2) * 0.42  # noqa: E731
    fill(im, blade, (224, 218, 198), 22, 1.12, 0.8, 0.03, 0.42)
    for y in range(4, 13, 2):                                      # dientes de sierra
        w = int((y - 2) * 0.42)
        put(im, [(8 - w - 1, y), (8 + w, y)], (246, 244, 234))
    for x in range(5, 11):
        put(im, [(x, 14)], (150, 46, 74))
        put(im, [(x, 15)], (110, 30, 56))
    put(im, [(7, 3), (8, 3)], shade((224, 218, 198), 0.75))
    for y in (6, 9, 12):
        put(im, [(7, y), (8, y)], shade((224, 218, 198), 0.88))
    return im


def bulb_lamp():
    """Lámpara de bulbo: marco azul oscuro con remaches y, dentro, tres bulbos que brillan sobre un fondo turquesa."""
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    frame = (40, 70, 110)
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c = shade(frame, 0.75 if (x + y) % 2 else 0.9)
            elif x in (1, 14) or y in (1, 14):
                c = shade(frame, 1.3)
            else:
                d = min(math.hypot(x - bx, y - by) / r for bx, by, r in ((5, 5, 3.2), (10.5, 6.5, 2.8), (7, 11, 3.0)))
                c = ramp_color([(40, 120, 120), (70, 190, 170), (140, 250, 220), (235, 255, 250)], max(0, 1 - d) ** 0.7) \
                    if d < 1 else (34, 96, 104)
            px[x, y] = c + (255,)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px[x, y] = (150, 186, 220, 255)
    for x, y in ((4, 4), (10, 5), (6, 10)):
        px[x, y] = (255, 255, 255, 255)
    return im


# ------------------------------------------------------------------ main

ITEMS = [
    # (nombre, inglés, español, función, criatura)
    ("grazer_meat", "Raw Grazer Meat", "Carne de pastoreador cruda", lambda: steak((178, 72, 104), (236, 214, 220)), "Pastoreador"),
    ("cooked_grazer_meat", "Grazer Steak", "Filete de pastoreador", lambda: steak((128, 74, 52), (214, 170, 120), True), "Pastoreador"),
    ("cerulean_leather", "Cerulean Leather", "Cuero cerúleo", lambda: hide([(22, 40, 66), (46, 90, 132), (74, 132, 176), (130, 180, 214)], (60, 150, 120)), "Pastoreador"),
    ("antenna_bulb", "Antenna Bulb", "Bulbo de antena", bulb, "Pastoreador"),
    ("hopper_meat", "Raw Hopper", "Carne de saltarín cruda", lambda: drumstick((200, 104, 120)), "Saltarín"),
    ("cooked_hopper_meat", "Cooked Hopper", "Saltarín cocinado", lambda: drumstick((150, 92, 56), True), "Saltarín"),
    ("hopper_foot", "Hopper Foot", "Pata de saltarín", foot, "Saltarín"),
    ("glider_membrane", "Glider Membrane", "Membrana de planeador", membrane, "Planeador"),
    ("glowing_scale", "Glowing Scale", "Escama luminosa", scale, "Acechador"),
    ("lurker_fang", "Lurker Fang", "Colmillo de acechador", lambda: fang((214, 226, 222), (40, 60, 80), 2.6, 12, True), "Acechador"),
    ("runner_fang", "Runner Fang", "Colmillo de corredor", lambda: fang((236, 230, 206), (70, 24, 44), 1.6, 10), "Corredor"),
    ("striped_pelt", "Striped Pelt", "Piel rayada", lambda: hide([(18, 36, 30), (36, 80, 62), (56, 116, 90), (100, 160, 130)], (30, 58, 120), (52, 96, 170)), "Corredor"),
    ("fang_dagger", "Fang Dagger", "Daga de colmillo", dagger, "Corredor"),
    ("fang_arrow", "Fang Arrow", "Flecha de colmillo", fang_arrow, "Corredor"),
    ("croaker_slime", "Croaker Slime", "Baba de croador", slime, "Croador"),
    ("croaker_legs", "Croaker Legs", "Ancas de croador", lambda: frog_legs((54, 150, 128)), "Croador"),
    ("cooked_croaker_legs", "Cooked Croaker Legs", "Ancas de croador cocinadas", lambda: frog_legs((150, 104, 64), True), "Croador"),
    ("armor_plate", "Armor Plate", "Placa de coraza", armor_plate, "Ironjaw"),
    ("ironjaw_blade", "Ironjaw Blade", "Cuchilla de mandíbula", ironjaw_blade, "Ironjaw"),
    ("ribbonfin", "Raw Ribbonfin", "Pez de cinta crudo", lambda: ribbon_fish((64, 122, 222), (206, 226, 244), (80, 200, 160)), "Pez de cinta"),
    ("cooked_ribbonfin", "Cooked Ribbonfin", "Pez de cinta cocinado", lambda: ribbon_fish((150, 98, 60), (220, 176, 128), (176, 120, 76), True), "Pez de cinta"),
]


def build(out, client_jar):
    os.makedirs(out, exist_ok=True)
    z = zipfile.ZipFile(client_jar)
    for n in ("leather", "slime_ball", "cod_bucket", "water_bucket"):
        VANILLA[n] = Image.open(io.BytesIO(z.read(f"assets/minecraft/textures/item/{n}.png"))).convert("RGBA")
    VANILLA["arrow_entity"] = Image.open(io.BytesIO(z.read("assets/minecraft/textures/entity/projectiles/arrow.png"))).convert("RGBA")
    arrow_entity().save(f"{out}/entity_fang_arrow.png")
    tex = {}
    for name, en, es, fn, mob in ITEMS:
        tex[name] = fn()
        tex[name].save(f"{out}/item_{name}.png")
    tex["ribbonfin_bucket"] = fish_bucket([(24, 50, 110), (50, 100, 200), (90, 150, 240), (200, 226, 250)])
    tex["ribbonfin_bucket"].save(f"{out}/item_ribbonfin_bucket.png")
    tex["croaker_bucket"] = croaker_bucket()
    tex["croaker_bucket"].save(f"{out}/item_croaker_bucket.png")
    blocks = {"bulb_lamp": bulb_lamp(), "glowing_scale_block": scale_block()}
    for n, b in blocks.items():
        b.save(f"{out}/block_{n}.png")
    # hoja de vista previa: por criatura, icono a 64 px con su nombre
    entries = [(n, es, mob) for n, en, es, fn, mob in ITEMS]
    entries.insert(entries.index(("cooked_ribbonfin", "Pez de cinta cocinado", "Pez de cinta")) + 1,
                   ("ribbonfin_bucket", "Cubeta con pez de cinta", "Pez de cinta"))
    entries.insert(entries.index(("cooked_croaker_legs", "Ancas de croador cocinadas", "Croador")) + 1,
                   ("croaker_bucket", "Cubeta con croador", "Croador"))
    groups = []
    for n, es, mob in entries:
        if not groups or groups[-1][0] != mob:
            groups.append((mob, []))
        groups[-1][1].append((n, es, tex[n]))
    groups.insert(1, ("Bloques", [("bulb_lamp", "Lámpara de bulbo", blocks["bulb_lamp"]),
                                  ("glowing_scale_block", "Bloque de escamas luminosas", blocks["glowing_scale_block"])]))
    cell_w, cell_h = 150, 120
    W = 5 * cell_w + 170
    rows = sum(math.ceil(len(g[1]) / 5) for g in groups)
    sheet = Image.new("RGBA", (W, rows * cell_h + 20), (26, 32, 40, 255))
    d = ImageDraw.Draw(sheet)
    y = 10
    for mob, its in groups:
        d.text((12, y + 40), mob, fill=(200, 230, 225, 255), font=font(17))
        for k, (n, es, im) in enumerate(its):
            cx = 170 + (k % 5) * cell_w
            cy = y + (k // 5) * cell_h
            if n.endswith("_block") or n == "bulb_lamp":
                tile = Image.new("RGBA", (64, 64))
                tile.alpha_composite(im.resize((64, 64), Image.NEAREST))
                sheet.alpha_composite(tile, (cx + 43, cy + 6))
            else:
                sheet.alpha_composite(im.resize((64, 64), Image.NEAREST), (cx + 43, cy + 6))
            words, line, lines = es.split(), "", []
            for w in words:
                if len(line + " " + w) > 20:
                    lines.append(line); line = w
                else:
                    line = (line + " " + w).strip()
            lines.append(line)
            for i, ln in enumerate(lines):
                tw = d.textlength(ln, font=font(12))
                d.text((cx + 75 - tw / 2, cy + 76 + i * 14), ln, fill=(220, 226, 230, 255), font=font(12))
        y += math.ceil(len(its) / 5) * cell_h
    sheet.save(f"{out}/cerulea_items_preview.png")
    print("ok", out)


if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2])
