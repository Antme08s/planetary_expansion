"""Logo del mod (2026-11-12): pixel art de 64×64 escalado a 512×512 (icono de CurseForge/Modrinth y logo del mod).

Uso: python3 -I tools/designs/logo.py <carpeta_salida>

Escena original: espacio oscuro con estrellas; Marte grande abajo a la izquierda (cráteres y casquete polar); en órbita
los otros planetas del mod: Thalassa (hielo azul con franjas), Venus (ámbar con remolinos), Ignis (roca negra con
grietas de lava) y el mundo helado (blanco); una nave con estela cruzando la órbita.
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image

N = 64
rnd = random.Random(7)


def disc(img, cx, cy, r, shade_fn, light=(-0.6, -0.7)):
    """Planeta con iluminación: shade_fn(u, v, x, y) -> color base; luz desde arriba a la izquierda."""
    lx, ly = light
    ln = math.hypot(lx, ly)
    for y in range(N):
        for x in range(N):
            dx, dy = (x + 0.5 - cx) / r, (y + 0.5 - cy) / r
            d2 = dx * dx + dy * dy
            if d2 > 1:
                continue
            dz = math.sqrt(1 - d2)
            lam = max(0.0, ((dx * lx + dy * ly) / ln) * 0.75 + dz * 0.45)
            base = np.array(shade_fn(dx, dy, x, y), float)
            k = 0.28 + 0.95 * lam
            c = np.clip(base * k, 0, 255)
            if d2 > 0.82 and lam > 0.35:              # borde con atmósfera
                c = c * 0.75 + np.array(shade_fn(dx, dy, x, y), float) * 0.45
            img[y, x] = np.clip(c, 0, 255)


def mars(dx, dy, x, y):
    c = np.array([196, 84, 44])
    n = math.sin(x * 0.9 + y * 0.4) + math.sin(x * 0.3 - y * 1.1)
    c = c * (0.92 + 0.06 * n)
    for cx, cy, cr in ((-0.35, 0.1, 0.16), (0.2, 0.35, 0.11), (0.35, -0.2, 0.09), (-0.1, 0.55, 0.08)):
        d = math.hypot(dx - cx, dy - cy)
        if d < cr:
            c = c * (0.72 if d < cr * 0.7 else 1.12)
    if dy < -0.80 + 0.05 * math.sin(dx * 11) and abs(dx) < 0.45:
        c = np.array([250, 246, 242])                 # casquete polar
    if abs(dy - 0.05 - 0.15 * math.sin(dx * 4)) < 0.05 and dx > -0.2:
        c = c * 0.78                                  # cañón
    return c


def thalassa(dx, dy, x, y):
    c = np.array([150, 196, 236])
    if abs(math.sin(dx * 7 + dy * 3)) < 0.18 or abs(math.sin(dx * 3 - dy * 8)) < 0.12:
        c = np.array([150, 112, 92])                  # lineae
    return c


def venus(dx, dy, x, y):
    s = math.sin(dy * 9 + math.sin(dx * 4) * 1.5)
    return np.array([222, 172, 84]) * (0.88 + 0.12 * s)


def ignis(dx, dy, x, y):
    if abs(math.sin(dx * 4.5 + dy * 2.2)) < 0.13 or abs(dy - 0.3 * dx - 0.35) < 0.07:
        return np.array([255, 150, 50]) / 0.6          # grietas al rojo (compensan la sombra)
    return np.array([66, 50, 48])


def ice(dx, dy, x, y):
    return np.array([232, 240, 248]) if (x + y) % 5 else np.array([206, 222, 238])


def build(out):
    img = np.zeros((N, N, 3), float)
    for y in range(N):                                # fondo: azul noche con un poco de nebulosa morada
        for x in range(N):
            t = y / N
            neb = math.exp(-((x - 46) ** 2 + (y - 14) ** 2) / 260.0)
            img[y, x] = np.array([8, 10, 26]) * (1 - t) + np.array([14, 12, 34]) * t + np.array([46, 20, 70]) * neb
    for _ in range(70):                               # estrellas
        x, y = rnd.randrange(N), rnd.randrange(N)
        b = rnd.choice((120, 170, 230, 255))
        img[y, x] = (b, b, min(255, b + 20))
    for x, y in ((50, 6), (12, 9), (58, 30)):          # estrellas grandes en cruz
        for ddx, ddy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            img[y + ddy, x + ddx] = (255, 250, 220) if (ddx, ddy) == (0, 0) else (150, 150, 190)
    # órbita (elipse de puntos)
    for a in np.linspace(0, 2 * math.pi, 220):
        x, y = 30 + 31 * math.cos(a), 30 + 13 * math.sin(a) - 8 * math.cos(a)
        xi, yi = int(x), int(y)
        if 0 <= xi < N and 0 <= yi < N and int(a * 30) % 3 == 0:
            img[yi, xi] = img[yi, xi] * 0.4 + np.array([120, 150, 210]) * 0.6
    disc(img, 47, 13, 7.5, venus)
    disc(img, 12, 25, 5.5, thalassa)
    disc(img, 54, 41, 6.0, ignis)
    disc(img, 33, 9, 3.2, ice)
    disc(img, 22, 50, 21, mars)                       # Marte, grande, delante
    # nave con estela
    trail = [(40 + i, 33 - i // 2) for i in range(-9, 0)]
    for i, (x, y) in enumerate(trail):
        a = (i + 1) / len(trail)
        img[y, x] = img[y, x] * (1 - 0.7 * a) + np.array([255, 190, 90]) * 0.7 * a
    ship = {(41, 32): (230, 230, 236), (42, 32): (210, 214, 222), (43, 31): (230, 230, 236), (42, 31): (180, 186, 196),
            (44, 31): (120, 200, 255), (43, 30): (150, 156, 168), (41, 33): (255, 170, 60)}
    for (x, y), c in ship.items():
        img[y, x] = c
    rgba = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGB').convert('RGBA')
    # esquinas redondeadas (2 px)
    a = rgba.load()
    for (x, y) in ((0, 0), (1, 0), (0, 1), (63, 0), (62, 0), (63, 1), (0, 63), (1, 63), (0, 62), (63, 63), (62, 63), (63, 62)):
        a[x, y] = (0, 0, 0, 0)
    os.makedirs(out, exist_ok=True)
    big = rgba.resize((512, 512), Image.NEAREST)
    big.save(os.path.join(out, 'logo.png'))
    rgba.save(os.path.join(out, 'logo_64.png'))
    # vista previa a varios tamaños
    prev = Image.new('RGBA', (512 + 256 + 128 + 64 + 32 + 120, 532), (40, 42, 48, 255))
    x = 10
    for s in (512, 256, 128, 64, 32):
        prev.alpha_composite(rgba.resize((s, s), Image.NEAREST), (x, 10))
        x += s + 20
    prev.save(os.path.join(out, 'logo_preview.png'))
    print('ok')


if __name__ == '__main__':
    build(sys.argv[1])
