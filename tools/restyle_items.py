"""Re-sombreado de items 16x16 (1.0.4): contorno con el color del material, volumen por zonas y luz arriba-izquierda.

Mantiene la silueta y el dibujo de cada item; cambia como se reparte el color:
  1. el contorno oscuro y parejo pasa a ser selectivo (claro arriba/izquierda, oscuro abajo/derecha, con el tono del
     material y desplazado a frio);
  2. cada zona de color (mismo matiz, conectada) se sombrea como un volumen: luz arriba-izquierda, centro mas claro,
     bordes mas oscuros, respetando el sombreado que ya trae el dibujo;
  3. 6 tonos por zona con sombras frias y luces calidas, y un brillo en las zonas grandes.
Uso: from restyle_items import restyle
"""
import colorsys
import math

import numpy as np
from PIL import Image

WARM, COOL = 0.11, 0.70


def _hsv(c):
    return colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)


def _rgb(h, s, v):
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, min(1, max(0, s)), min(1, max(0, v)))
    return (round(r * 255), round(g * 255), round(b * 255))


def _toward(h, target, amount):
    d = ((target - h + 0.5) % 1.0) - 0.5
    return h + d * amount


def _lum(c):
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255


STEPS = (-1.0, -0.6, -0.25, 0.0, 0.35, 0.75)   # 6 tonos de oscuro a claro


def _tone(base, step, sat=1.08):
    h, s, v = _hsv(base)
    if step < 0:
        k = -step
        v = v * (1 - 0.46 * k)
        h = _toward(h, COOL, 0.12 * k)
        s = min(1, s * (1 + 0.28 * k))
    elif step > 0:
        v = v + (1 - v) * 0.62 * step
        h = _toward(h, WARM, 0.08 * step)
        s = s * (1 - 0.22 * step)
    return _rgb(h, s * sat, v)


def restyle(im, sat=1.08, outline_dark=0.40):
    im = im.convert("RGBA")
    W, H = im.size
    px = np.array(im)
    A = px[:, :, 3] > 100

    def op(x, y):
        return 0 <= x < W and 0 <= y < H and A[y, x]

    N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
    edge = np.zeros((H, W), bool)
    for y in range(H):
        for x in range(W):
            if A[y, x] and not all(op(x + dx, y + dy) for dx, dy in N4):
                edge[y, x] = True

    lum = np.zeros((H, W))
    for y in range(H):
        for x in range(W):
            lum[y, x] = _lum(px[y, x, :3])

    # contorno = borde muy oscuro (los bordes claros, como una punta de colmillo, se tratan como relleno)
    outl = edge & (lum < 0.26)
    fill = A & ~outl

    # familias de color: mismo matiz y saturacion parecida (el valor no cuenta: es el sombreado del dibujo)
    def family(c):
        h, s, v = _hsv(c)
        if s < 0.16 or v < 0.12:
            return ("g", round(v * 3))          # grises: por claridad
        return ("c", int(h * 12) % 12)
    fam = {}
    for y in range(H):
        for x in range(W):
            if fill[y, x]:
                fam[(x, y)] = family(px[y, x, :3])

    seen, regions = set(), []
    for p in fam:
        if p in seen:
            continue
        stack, comp = [p], []
        seen.add(p)
        while stack:
            q = stack.pop()
            comp.append(q)
            for dx, dy in N4:
                r = (q[0] + dx, q[1] + dy)
                if r in fam and r not in seen and fam[r] == fam[p]:
                    seen.add(r)
                    stack.append(r)
        regions.append(comp)

    out = px.copy()
    for comp in regions:
        pts = set(comp)
        cols = [tuple(int(v) for v in px[y, x, :3]) for x, y in comp]
        mean = tuple(int(sum(c[i] for c in cols) / len(cols)) for i in range(3))
        if len(comp) < 4:                       # detalles minusculos: se conservan, con algo de saturacion
            for (x, y), c in zip(comp, cols):
                h, s, v = _hsv(c)
                out[y, x, :3] = _rgb(h, s * sat, v)
            continue
        cx = sum(x for x, _ in comp) / len(comp)
        cy = sum(y for _, y in comp) / len(comp)
        rad = max(1.0, max(math.hypot(x - cx, y - cy) for x, y in comp))
        # distancia al borde de la zona (Manhattan, aproximada con relleno por capas)
        dist = {}
        layer = [p for p in comp if any((p[0] + dx, p[1] + dy) not in pts for dx, dy in N4)]
        for p in layer:
            dist[p] = 1
        d = 1
        while layer:
            nxt = []
            for p in layer:
                for dx, dy in N4:
                    r = (p[0] + dx, p[1] + dy)
                    if r in pts and r not in dist:
                        dist[r] = d + 1
                        nxt.append(r)
            layer, d = nxt, d + 1
        dmax = max(dist.values())
        lums = [_lum(c) for c in cols]
        lmean = sum(lums) / len(lums)
        lspan = max(0.08, max(lums) - min(lums))
        best_i, best_L = None, -9
        for (x, y), c, l in zip(comp, cols, lums):
            direction = ((cx - x) + (cy - y)) / (1.4 * rad)               # + = arriba-izquierda de la zona
            bulge = (dist[(x, y)] - 1) / max(1, dmax - 1) if dmax > 1 else 0.3
            own = (l - lmean) / lspan                                       # sombreado que ya traia el dibujo
            L = 0.42 * direction + 0.30 * (bulge - 0.35) + 0.55 * own
            idx = int(round(2.4 + L * 2.2))
            idx = max(0, min(5, idx))
            if L > best_L:
                best_L, best_i = L, (x, y)
            out[y, x, :3] = _tone(mean, STEPS[idx], sat)
        if len(comp) >= 10 and best_i:          # brillo en la zona grande
            x, y = best_i
            out[y, x, :3] = _tone(mean, 0.95, sat)

    # contorno selectivo, con el color del material de al lado
    for y in range(H):
        for x in range(W):
            if not outl[y, x]:
                continue
            ns = [out[y + dy, x + dx, :3].astype(float) for dx in (-1, 0, 1) for dy in (-1, 0, 1)
                  if (dx or dy) and 0 <= x + dx < W and 0 <= y + dy < H and fill[y + dy, x + dx]]
            if not ns:
                c = tuple(int(v) for v in px[y, x, :3])
                h, s, v = _hsv(c)
                out[y, x, :3] = _rgb(h, s, v * 0.9)
                continue
            c = tuple(int(v) for v in np.mean(ns, axis=0))
            h, s, v = _hsv(c)
            tl = (not op(x, y - 1)) or (not op(x - 1, y))
            br = (not op(x, y + 1)) or (not op(x + 1, y))
            f = 0.56 if (tl and not br) else (0.34 if (br and not tl) else outline_dark)
            out[y, x, :3] = _rgb(_toward(h, COOL, 0.08), min(1, s * 1.1 + 0.12), v * f)
    return Image.fromarray(out, "RGBA")
