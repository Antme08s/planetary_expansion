"""Re-sombreado "estilo moderno" para texturas 16x16 de items y bloques.

No cambia la resolucion: reordena el color como lo hacen Create o Farmer's Delight:
  - contorno selectivo con el tono del propio material (claro arriba/izquierda, oscuro abajo/derecha)
  - luz fija desde arriba-izquierda: borde interior claro arriba/izquierda, sombra abajo/derecha
  - sombras hacia frio/violeta, luces hacia calido, un poco mas de saturacion
  - fusion de tonos casi iguales para quitar el ruido
Uso:  from restyle import restyle_item, restyle_block
"""
import colorsys
import numpy as np
from PIL import Image


def _hsv(c):
    return colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)


def _rgb(h, s, v):
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, min(1, max(0, s)), min(1, max(0, v)))
    return (round(r * 255), round(g * 255), round(b * 255))


def _toward(h, target, amount):
    """Mueve el matiz h hacia target (0..1) por el camino mas corto."""
    d = ((target - h + 0.5) % 1.0) - 0.5
    return h + d * amount


WARM, COOL = 0.12, 0.72  # amarillo-naranja / azul-violeta


def _shift(c, light, sat=1.0):
    """light en [-1,1]: sube/baja valor y empuja el matiz a calido/frio."""
    h, s, v = _hsv(c)
    if light > 0:
        v = v + (1 - v) * 0.55 * light
        h = _toward(h, WARM, 0.07 * light)
        s = s * (1 - 0.18 * light)
    elif light < 0:
        k = -light
        v = v * (1 - 0.34 * k)
        h = _toward(h, COOL, 0.10 * k)
        s = min(1, s * (1 + 0.20 * k))
    return _rgb(h, s * sat, v)


def _merge(colors, thr):
    """Fusiona colores muy parecidos (distancia RGB) hacia el mas frecuente."""
    from collections import Counter
    cnt = Counter(colors)
    order = [c for c, _ in cnt.most_common()]
    reps = []
    mp = {}
    for c in order:
        for r in reps:
            if sum((a - b) ** 2 for a, b in zip(c, r)) ** 0.5 < thr:
                mp[c] = r
                break
        else:
            reps.append(c)
            mp[c] = c
    return mp


def restyle_item(im, strength=1.0, sat=1.12, merge=14, rim=0.45, shade=0.55):
    im = im.convert("RGBA")
    W, H = im.size
    px = np.array(im)
    A = px[:, :, 3] > 100

    def opaque(x, y):
        return 0 <= x < W and 0 <= y < H and A[y, x]

    # 1) fusion de tonos casi iguales (solo opacos)
    cols = [tuple(int(v) for v in px[y, x, :3]) for y in range(H) for x in range(W) if A[y, x]]
    mp = _merge(cols, merge)
    out = px.copy()
    for y in range(H):
        for x in range(W):
            if A[y, x]:
                out[y, x, :3] = mp[tuple(int(v) for v in px[y, x, :3])]

    base = out.copy()
    edge = np.zeros((H, W), bool)
    for y in range(H):
        for x in range(W):
            if A[y, x] and not all(opaque(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                edge[y, x] = True

    # 2) luz interior: el 1er pixel hacia dentro de un borde arriba/izq se aclara, abajo/der se oscurece
    res = base.copy()
    for y in range(H):
        for x in range(W):
            if not A[y, x] or edge[y, x]:
                continue
            c = tuple(int(v) for v in base[y, x, :3])
            lit = 0.0
            if edge[y - 1, x] if y > 0 else False:
                lit += rim
            if edge[y, x - 1] if x > 0 else False:
                lit += rim * 0.8
            if edge[y + 1, x] if y + 1 < H else False:
                lit -= shade
            if edge[y, x + 1] if x + 1 < W else False:
                lit -= shade * 0.8
            lit = max(-1, min(1, lit)) * strength
            if abs(lit) > 0.01:
                c = _shift(c, lit)
            res[y, x, :3] = c

    # 3) contorno selectivo: color del material vecino, oscurecido y desplazado a frio
    for y in range(H):
        for x in range(W):
            if not edge[y, x]:
                continue
            ns = []
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    xx, yy = x + dx, y + dy
                    if (dx or dy) and opaque(xx, yy) and not edge[yy, xx]:
                        ns.append(base[yy, xx, :3].astype(float))
            if not ns:  # pixel suelto o linea de 1px: oscurece su propio color
                ns = [base[y, x, :3].astype(float)]
            c = tuple(int(v) for v in np.mean(ns, axis=0))
            h, s, v = _hsv(c)
            top_left = (not opaque(x, y - 1)) or (not opaque(x - 1, y))
            bot_right = (not opaque(x, y + 1)) or (not opaque(x + 1, y))
            f = 0.50 if (top_left and not bot_right) else (0.30 if bot_right and not top_left else 0.38)
            v = v * f
            s = min(1, s * 1.05 + 0.10)
            h = _toward(h, COOL, 0.07)
            res[y, x, :3] = _rgb(h, s, v)

    # 4) un poco mas de saturacion global
    for y in range(H):
        for x in range(W):
            if A[y, x]:
                h, s, v = _hsv(tuple(int(t) for t in res[y, x, :3]))
                res[y, x, :3] = _rgb(h, s * sat, v)
    return Image.fromarray(res, "RGBA")


def restyle_block(im, sat=1.10, merge=18, contrast=1.10):
    """Bloques: sin contorno. Fusion de ruido + contraste de tonos + luz/sombra calida/fria."""
    im = im.convert("RGBA")
    W, H = im.size
    px = np.array(im)
    cols = [tuple(int(v) for v in px[y, x, :3]) for y in range(H) for x in range(W)]
    mp = _merge(cols, merge)
    vs = [_hsv(c)[2] for c in mp.values()]
    mean_v = sum(vs) / len(vs)
    out = px.copy()
    for y in range(H):
        for x in range(W):
            c = mp[tuple(int(v) for v in px[y, x, :3])]
            h, s, v = _hsv(c)
            d = (v - mean_v) * contrast
            light = max(-1, min(1, d * 3.2))
            c2 = _shift(c, light * 0.55, sat)
            out[y, x, :3] = c2
    return Image.fromarray(out, "RGBA")
