#!/usr/bin/env python3
"""Textura de Aurelia, el gigante gaseoso de la estrella Ámbar (1.0.4): esfera de 1024x1024 con bandas planas en ámbar,
cobre y rojizo (como el gigante gaseoso de Cosmonautics, que no cambia de forma) y una gran tormenta ovalada con
remolino en el hemisferio sur. Sin anillos.

Uso: python3 aurelia_assets.py <carpeta_salida>   ->   aurelia.png
"""
import math
import os
import random
import sys

from PIL import Image


def lerp(a, b, t):
    return a + (b - a) * t


def aurelia(path):
    size = 1024
    rng = random.Random(4417)
    # bandas: ámbar claro, naranja quemado, cobre, rojizo oscuro y crema anaranjada
    palette = [(238, 170, 84), (214, 128, 58), (186, 96, 52), (150, 64, 44), (246, 204, 130), (200, 112, 60)]
    edges, y, k = [], 0.0, 0
    while y < size:
        h = rng.uniform(34, 86) if 0.22 < y / size < 0.78 else rng.uniform(20, 52)
        edges.append((y, y + h, palette[k % len(palette)] if rng.random() > 0.3 else palette[rng.randrange(len(palette))]))
        y += h
        k += 1
    waves = [(rng.uniform(0, 6.28), rng.uniform(2, 6), rng.uniform(2.0, 6.0)) for _ in edges]
    img = Image.new("RGB", (size, size))
    px = img.load()
    for x in range(size):
        for yy in range(size):
            for i, (y0, y1, col) in enumerate(edges):
                ph, f, a = waves[i]
                w0 = a * math.sin(ph + x / size * 6.283 * f) + 0.7 * a * math.sin(ph * 2 + x / size * 6.283 * f * 3)
                if y0 + w0 <= yy < y1 + (waves[i + 1][2] * math.sin(waves[i + 1][0] + x / size * 6.283 * waves[i + 1][1]) if i + 1 < len(edges) else 0):
                    px[x, yy] = col
                    break
            else:
                px[x, yy] = next((c for (a0, a1, c) in edges if a0 <= yy < a1), palette[0])
    # polos más oscuros y violáceos (casquete de neblina)
    for yy in list(range(0, 56)) + list(range(size - 56, size)):
        for x in range(size):
            r, g, b = px[x, yy]
            t = 0.4 * (1 - min(yy, size - 1 - yy) / 56)
            px[x, yy] = (int(lerp(r, 120, t)), int(lerp(g, 78, t)), int(lerp(b, 96, t)))
    # gran tormenta: óvalo con remolino (hemisferio sur, un cuarto del ancho del mapa)
    cx, cy, rx, ry = 690, 640, 120, 62
    for x in range(cx - rx - 20, cx + rx + 21):
        for yy in range(cy - ry - 20, cy + ry + 21):
            dx, dy = (x - cx) / rx, (yy - cy) / ry
            d = math.hypot(dx, dy)
            if d > 1.12:
                continue
            ang = math.atan2(dy, dx) + d * 5.2          # espiral
            ring = 0.5 + 0.5 * math.sin(ang * 3.0 - d * 9.0)
            base = (176, 52, 38) if d < 0.55 else (206, 92, 52)
            lite = (246, 178, 110)
            col = tuple(int(lerp(base[i], lite[i], ring * 0.55)) for i in range(3))
            edge = max(0.0, min(1.0, (1.12 - d) / 0.18))  # borde suave
            r, g, b = px[x % size, yy]
            px[x % size, yy] = tuple(int(lerp(o, n, edge)) for o, n in zip((r, g, b), col))
    img.save(path)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    aurelia(os.path.join(out, "aurelia.png"))
    print("OK", out)
