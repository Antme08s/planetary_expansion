"""Añade sombreado de pixel art a la textura de una armadura del mod.

Uso: python3 -I tools/shade_armor.py <armor_models/x.json> <textura.png> <salida.png>

Solo toca los píxeles que se ven en el modelo (las caras de las cajas); el resto de la plantilla queda igual.
Para cada cara:
  - relieve: luz desde arriba a la izquierda. El borde de arriba y de la izquierda de cada zona de un mismo
    color se aclara y el de abajo y de la derecha se oscurece, así los marcos y paneles parecen en relieve;
  - degradado: la cara se oscurece un poco hacia abajo;
  - desgaste: una variación muy pequeña por píxel (siempre la misma) para que no se vea plano;
  - visera (colores cálidos): un reflejo en diagonal.
"""
import json
import random
import sys

from PIL import Image


def face_rects(box):
    u, v = box["uv"]
    w, h, d = (round(x) for x in box["size"])
    return [
        (u + d, v, w, d), (u + d + w, v, w, d),                                # arriba, abajo
        (u, v + d, d, h), (u + d, v + d, w, h),                                # derecha, delante
        (u + d + w, v + d, d, h), (u + 2 * d + w, v + d, w, h),               # izquierda, detrás
    ]


def is_warm(c):
    r, g, b = c[:3]
    return r > 150 and r > b + 60


def shade(model, img, seed=7):
    src = img.convert("RGBA")
    out = src.copy()
    W, H = src.size
    px = src.load()
    po = out.load()
    rng = random.Random(seed)
    done = set()
    for parts in model["pieces"].values():
        for nodes in parts.values():
            for node in nodes:
                for box in node["boxes"]:
                    for (x0, y0, w, h) in face_rects(box):
                        if w <= 0 or h <= 0:
                            continue
                        for j in range(h):
                            for i in range(w):
                                x, y = x0 + i, y0 + j
                                if not (0 <= x < W and 0 <= y < H) or (x, y) in done:
                                    continue
                                done.add((x, y))
                                c = px[x, y]
                                if c[3] < 128:
                                    continue

                                def same(dx, dy):
                                    ii, jj = i + dx, j + dy
                                    if not (0 <= ii < w and 0 <= jj < h):
                                        return False
                                    o = px[x + dx, y + dy]
                                    if is_warm(c) and is_warm(o):
                                        return True  # la visera tiene varios naranjas: es una sola zona
                                    return o[:3] == c[:3]

                                f = 1.0
                                if not same(0, -1):
                                    f += 0.16
                                if not same(-1, 0):
                                    f += 0.08
                                if not same(0, 1):
                                    f -= 0.16
                                if not same(1, 0):
                                    f -= 0.08
                                if h > 2:
                                    f -= 0.10 * (j / (h - 1))
                                f += rng.uniform(-0.025, 0.025)
                                rgb = [c[0] * f, c[1] * f, c[2] * f]
                                if is_warm(c) and (i + j) % max(w, 4) in (5, 6):
                                    rgb = [ch + (255 - ch) * 0.40 for ch in rgb]
                                po[x, y] = tuple(max(0, min(255, round(ch))) for ch in rgb) + (c[3],)
    return out


if __name__ == "__main__":
    model = json.load(open(sys.argv[1]))
    shade(model, Image.open(sys.argv[2])).save(sys.argv[3])
