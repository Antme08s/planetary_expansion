"""1.0.3 motor estelar: texturas de objetos (16x16).

- helium3_crystal: cristal de helio-3 (sale al triturar regolito lunar): tres prismas color crema-dorado sobre un
  trozo de roca lunar gris.
- stellar_warp_core: núcleo de distorsión estelar: una estrellita blanco-violeta dentro de un aro de latón inclinado,
  con gemas de colores (los planetas con los que se hizo).

Uso: python3 stellar_items.py <carpeta_salida> [vista_previa.png]
"""
import math, os, sys
from PIL import Image

H = lambda s: tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


HE3 = [
    "................",
    "........o.......",
    ".......o4o......",
    ".......o43o.....",
    "..o....o43o.....",
    ".o4o...o4w2o....",
    ".o43o..o432o..o.",
    "..o32o.o432o.o4o",
    "..o4w2oo432oo43o",
    "...o32oo432oo32o",
    "...o421o4321o21o",
    "..rr321rr321r1rr",
    ".rRRrrRRRrrRRrr.",
    ".rRcRRrRRcRRRr..",
    "..rrRRRrrRRrr...",
    "................"]


def helium3():
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    pal = {'o': '4a2e12', 'w': 'ffffff', '4': 'fbeeb4', '3': 'f2d27a', '2': 'd6a64a', '1': '9a6a2c',
           'r': '55555c', 'R': '8a8a92', 'c': '3a3a40'}
    for y, row in enumerate(HE3):
        for x, ch in enumerate(row):
            if ch in pal: px[x, y] = H(pal[ch])
    return im


def _helium3_old():
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    rock = ['3a3a40', '55555c', '76767e', '9a9aa2', 'bdbdc4']
    # roca lunar (base)
    for y in range(11, 15):
        for x in range(2, 14):
            if (y == 11 and x in (2, 3, 12, 13)) or (y == 14 and x in (2, 13)): continue
            t = 2 + (1 if (x * 5 + y * 3) % 7 == 0 else 0) - (1 if y == 14 or x == 13 else 0) + (1 if y == 11 else 0)
            if (x * 3 + y * 7) % 11 == 0: t = 0            # cráteres
            px[x, y] = H(rock[max(0, min(4, t))])
    gold = ['5e3f1c', '9a6a2c', 'd6a64a', 'f2d27a', 'fbeeb4', 'ffffff']

    def prism(x0, top, bot, w):
        # prisma vertical con punta; cara izquierda clara, derecha oscura
        for y in range(top, bot + 1):
            for x in range(x0, x0 + w):
                dx = x - x0
                tip = top + abs(dx - (w - 1) / 2)          # punta
                if y < tip: continue
                if dx == 0: c = 4
                elif dx == w - 1: c = 1
                elif dx < w / 2: c = 3
                else: c = 2
                if y == bot: c = max(0, c - 1)
                px[x, y] = H(gold[c])
        px[x0 + w // 2 - (1 if w % 2 == 0 else 0), top + 1] = H(gold[5])   # brillo en la punta

    prism(7, 1, 11, 4)     # el grande en medio
    prism(3, 5, 11, 4)
    prism(11, 6, 11, 3)
    # destellos de helio dentro del cristal
    for (x, y) in [(8, 5), (9, 8), (4, 8), (12, 9)]: px[x, y] = H(gold[5])
    # contorno oscuro donde el cristal toca el aire
    out = im.copy(); op = out.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3]: continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < 16 and 0 <= yy < 16 and px[xx, yy][3] and px[xx, yy][:3] in [H(g)[:3] for g in gold]:
                    op[x, y] = H('3a2410'); break
    return out


def stellar_core():
    """Orbe blanco-violeta engastado en un aro de latón con 7 gemas (los planetas del sistema solar)."""
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    st = ['2a0f4a', '4b1f86', '7a3fd0', 'a86bff', 'd3b8ff', 'f1e8ff', 'ffffff']
    brass = ['5c382b', '9e6947', 'd6a85c', 'f6dc92']
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - cx, y - cy)
            a = math.atan2(y - cy, x - cx)
            if r <= 4.4:
                t = 1 - r / 4.4 + 0.07 * math.sin(a * 3 + r * 1.5)
                if x + y < 13: t += 0.1                     # luz arriba a la izquierda
                k = max(1, min(6, int(t * 7 + 0.8)))
                px[x, y] = H(st[k])
            elif r <= 5.3:
                px[x, y] = H(st[0])                         # sombra entre orbe y aro
            elif r <= 7.4:
                lt = -(math.cos(a) + math.sin(a)) * 0.5     # arriba-izquierda más claro
                k = 2 + (1 if lt > 0.3 else 0) - (1 if lt < -0.3 else 0)
                if r > 6.7: k = max(0, k - 1)
                px[x, y] = H(brass[k])
    # brillo del orbe
    px[6, 5] = H(st[6]); px[5, 6] = H(st[6])
    # gemas: Tierra, Luna, Marte, Venus, mundo helado, Thalassa, Ignis
    gems = ['3f7fd0', 'd8d8d0', 'c4502e', 'e0b85a', 'a9e4f6', '1f4fb0', 'ff7a1a']
    for i, c in enumerate(gems):
        a = -math.pi / 2 + i * 2 * math.pi / 7
        x, y = int(round(cx + 6.3 * math.cos(a))), int(round(cy + 6.3 * math.sin(a)))
        px[x, y] = H(c)
    return im


if __name__ == '__main__':
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    items = {'helium3_crystal': helium3(), 'stellar_warp_core': stellar_core()}
    for n, im in items.items(): im.save(os.path.join(out, n + '.png'))
    if len(sys.argv) > 2:
        big = Image.new('RGBA', (2 * 160 + 30, 190), (52, 55, 66, 255))
        for i, im in enumerate(items.values()):
            big.paste(im.resize((160, 160), Image.NEAREST), (10 + i * 170, 15), im.resize((160, 160), Image.NEAREST))
        big.save(sys.argv[2])
