#!/usr/bin/env python3
"""Texturas de Saturno (1.0.4): la esfera (1024x1024, bandas planas como el gigante gaseoso de Cosmonautics) y la tira
radial de los anillos (512x4: u = 0 en el borde interior, 1 en el exterior; la transparencia dibuja los huecos).

Uso: python3 saturn_assets.py <carpeta_salida>   ->   saturn.png y saturn_rings.png
"""
import math
import os
import random
import sys

from PIL import Image


def lerp(a, b, t):
    return a + (b - a) * t


def saturn(path):
    size = 1024
    rng = random.Random(1907)
    # bandas (altura relativa, color): crema y dorado pálido, con bandas finas más oscuras como las de Saturno
    palette = [(238, 224, 178), (224, 204, 150), (208, 184, 128), (232, 214, 164), (196, 170, 118), (244, 232, 190)]
    # alturas de las bandas: más anchas en el ecuador y más estrechas hacia los polos
    edges, y, k = [], 0.0, 0
    while y < size:
        h = rng.uniform(40, 100) if 0.25 < y / size < 0.75 else rng.uniform(22, 58)
        edges.append((y, y + h, palette[k % len(palette)] if rng.random() > 0.25 else palette[rng.randrange(len(palette))]))
        y += h
        k += 1
    # ondulación de los bordes: suma de tres senos por borde (cada columna cambia un poco el borde)
    waves = [(rng.uniform(0, 6.28), rng.uniform(2, 5), rng.uniform(1.5, 4.5)) for _ in edges]
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
    # polos algo más oscuros y grisáceos (casquete)
    for yy in list(range(0, 52)) + list(range(size - 52, size)):
        for x in range(size):
            r, g, b = px[x, yy]
            t = 0.35 * (1 - min(yy, size - 1 - yy) / 52)
            px[x, yy] = (int(lerp(r, 150, t)), int(lerp(g, 150, t)), int(lerp(b, 138, t)))
    img.save(path)


def rings(path):
    w, h = 512, 4
    img = Image.new("RGBA", (w, h))
    px = img.load()
    rng = random.Random(77)
    fine = [rng.random() for _ in range(w)]
    for x in range(w):
        u = x / (w - 1)
        # (alfa, color) por zona: anillo C tenue, B brillante, división de Cassini, A, hueco de Encke, F fino
        if u < 0.16:        # C: transparente y oscuro
            a, col = 0.10 + 0.25 * (u / 0.16), (150, 130, 104)
        elif u < 0.52:      # B: el más denso
            a, col = 0.88 + 0.08 * math.sin(u * 140), (236, 218, 176)
            col = (int(lerp(col[0], 214, fine[x] * 0.5)), int(lerp(col[1], 192, fine[x] * 0.5)), int(lerp(col[2], 148, fine[x] * 0.5)))
        elif u < 0.60:      # división de Cassini
            a, col = 0.06 + 0.04 * fine[x], (170, 150, 120)
        elif u < 0.93:      # A
            a, col = 0.74 + 0.1 * math.sin(u * 220), (214, 194, 156)
            if 0.84 < u < 0.855:
                a = 0.12  # hueco de Encke
        elif u < 0.95:
            a, col = 0.05, (170, 150, 120)
        else:               # F: línea fina exterior
            a = 0.55 if u < 0.965 else 0.0
            col = (220, 204, 170)
        # bordes interior y exterior suaves
        a *= min(1.0, u / 0.012, (1 - u) / 0.012)
        for y in range(h):
            px[x, y] = (*col, max(0, min(255, int(a * 255))))
    img.save(path)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    saturn(os.path.join(out, "saturn.png"))
    rings(os.path.join(out, "saturn_rings.png"))
    print("OK", out)
