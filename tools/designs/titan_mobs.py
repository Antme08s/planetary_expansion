"""Criaturas de Titán (1.0.4, elegidas por el usuario: "solo Titán primero"): rumiador de tolina (pasivo, manadas),
anguila de metano (en los lagos de metano) y acechador de bruma (monstruo casi invisible en la bruma que se acerca a la luz).

Uso: python3 -I tools/designs/titan_mobs.py <carpeta_salida>

Mismas herramientas que ferra_mobs.py / cerulea_mobs.py. Coordenadas de Blockbench: 16 unidades = 1 bloque, y hacia
arriba, el frente mira a -z.
"""
import json
import math
import os
import sys
import zlib

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import thalassa_mobs as T  # noqa: E402
from thalassa_mobs import Box, mirror_x, seg  # noqa: E402
from cerulea_mobs import paint, mat, mix, spots  # noqa: E402


def coplanar_check(boxes, name):
    """Busca caras de cajas distintas en el mismo plano, mirando al mismo lado y solapadas (eso parpadea en el juego).
    Solo compara cajas sin girar o con el mismo giro y el mismo origen (en reposo)."""
    probs = []
    eps = 1e-4
    def faces(b):
        out = []
        for ax in range(3):
            o1, o2 = [i for i in range(3) if i != ax]
            for sgn, val in ((-1, b.frm[ax]), (1, b.to[ax])):
                out.append((ax, sgn, val, (b.frm[o1], b.to[o1]), (b.frm[o2], b.to[o2])))
        return out
    for i, a in enumerate(boxes):
        for j in range(i + 1, len(boxes)):
            b = boxes[j]
            if tuple(a.rot) != tuple(b.rot):
                continue
            if any(abs(x) > eps for x in a.rot) and [round(v, 3) for v in a.origin] != [round(v, 3) for v in b.origin]:
                continue
            for fa in faces(a):
                for fb in faces(b):
                    if fa[0] != fb[0] or fa[1] != fb[1] or abs(fa[2] - fb[2]) > eps:
                        continue
                    ov1 = min(fa[3][1], fb[3][1]) - max(fa[3][0], fb[3][0])
                    ov2 = min(fa[4][1], fb[4][1]) - max(fa[4][0], fb[4][0])
                    if ov1 > eps and ov2 > eps:
                        probs.append((i, j, a.group, b.group, "xyz"[fa[0]], fa[1], fa[2]))
    for p in probs:
        print(f"  [{name}] caras en el mismo plano: cajas {p[0]} ({p[2]}) y {p[1]} ({p[3]}), eje {p[4]}{'+' if p[5] > 0 else '-'} = {p[6]}")
    return probs


def ruminant():
    """Rumiador de tolina (~1,7 bloques de largo, 1,3 de alto con las velas): animal grande, bajo y ancho de cuatro patas
    gruesas con muslo, caña y pezuña de dos dedos; lomo de placas ocres escalonadas como tejas, hombros y caderas
    acorazados, faldón de placas en los costados, cuello corto con placa, cabeza ancha con frente acorazada, mejillas,
    mandíbula, hocico con almohadilla, ojos ámbar en su cuenca, orejas y dos cuernos curvos; cola de dos tramos con maza.
    En el lomo, seis velas de membrana naranja tensadas por una espina."""
    def plate_fn(p, n, c):
        if spots(p, 3.0, 0.3, 0.9, 31):
            return mix(c, (214, 162, 96), 0.4), False                 # manchas claras de tolina
        if spots(p, 2.2, 0.25, 0.5, 33):
            return mix(c, (110, 66, 34), 0.5), False                  # picaduras
        return c, False
    plate = mat("plate", (176, 116, 58), (150, 100, 56), 0.06, plate_fn)
    plate_hi = mat("plate_hi", (196, 136, 70), (160, 108, 60), 0.05, plate_fn)
    plate_dk = mat("plate_dk", (150, 96, 48), (126, 84, 44), 0.06, plate_fn)

    def skin_fn(p, n, c):
        if abs(n[0]) > 0.6 and (int(p[1] * 1.5) + int(p[2] * 1.5)) % 5 == 0:
            return mix(c, (120, 78, 44), 0.35), False                 # arrugas
        return c, False
    skin = mat("skin", (150, 100, 58), (204, 172, 128), 0.06, skin_fn, belly_y=9)
    belly = mat("belly", (206, 178, 132), noise=0.05)
    leg = mat("leg", (136, 90, 52), (160, 116, 72), 0.06, belly_y=2)
    hoof = mat("hoof", (58, 40, 28), noise=0.04)

    def nose_fn(p, n, c):
        if n[2] < -0.5 and abs(abs(p[0]) - 1.4) < 0.6 and 10.2 < p[1] < 11.4:
            return (30, 20, 16), False                                 # fosas nasales
        return c, False
    nose = mat("nose", (88, 60, 48), noise=0.04, fn=nose_fn)

    def jaw_fn(p, n, c):
        if abs(n[0]) > 0.6 and abs(p[1] - 8.0) < 0.3:
            return (60, 34, 28), False                                 # línea de la boca
        return c, False
    jaw = mat("jaw", (190, 150, 106), noise=0.05, fn=jaw_fn)
    horn = mat("horn", (96, 70, 46), (70, 50, 34), 0.05, belly_y=15)
    horn_tip = mat("horn_tip", (214, 196, 160), noise=0.04)
    socket = mat("socket", (60, 40, 30), noise=0.03)
    eye = mat("eye", (255, 186, 70), noise=0.0)

    def sail_fn(p, n, c):
        k = max(0.0, min(1.0, (p[1] - 19.0) / 9.0))
        c = mix(c, (248, 178, 92), k)
        if int(p[1] * 1.2) % 3 == 0:
            c = mix(c, (170, 70, 30), 0.35)                            # venas
        return c, False
    sail = mat("sail", (210, 104, 44), noise=0.04, fn=sail_fn)
    spine = mat("spine", (92, 54, 32), noise=0.05)

    B = [
        Box("body", [-8.5, 7.5, -11], [8.5, 18, 13], plate_dk),           # tronco
        Box("body", [-7.5, 6.5, -9], [7.5, 8.0, 11], belly),              # vientre
        Box("body", [-7.8, 8.2, -12.6], [7.8, 16.5, -10], skin),          # pecho
        Box("body", [-8.0, 8.5, 12], [8.0, 17.2, 14.6], plate_dk),        # grupa
    ]
    # lomo: cinco bandas de placas como tejas, cada una a otra altura y anchura que la de al lado (sin caras en el mismo plano)
    for i in range(5):
        z0 = -10.5 + i * 4.6
        w, top, bot = (9.3, 19.4, 13.5) if i % 2 == 0 else (9.05, 19.1, 13.8)
        B.append(Box("body", [-w, bot, z0], [w, top, z0 + 5.4], plate if i % 2 == 0 else plate_hi))
    side = [
        Box("body", [8.0, 9.0, -8], [9.0, 13.2, 10], plate),               # faldón de placas
        Box("body", [6.0, 9.3, -9], [9.6, 15.5, -3.2], plate_hi),          # hombro
        Box("body", [6.0, 9.5, 7], [9.5, 15.2, 12.5], plate_hi),           # cadera
    ]
    # cuello y cabeza
    B += [Box("neck", [-4.5, 9.5, -16], [4.5, 15.8, -10.5], skin),
          Box("neck", [-5.2, 14.5, -15.5], [5.2, 16.8, -11], plate)]
    B += [Box("head", [-5, 8, -22], [5, 15, -15.2], skin),                 # cráneo
          Box("head", [-5.6, 13.2, -21.0], [5.6, 16.0, -15.6], plate_hi),  # frente acorazada
          Box("head", [-4, 7.5, -26], [4, 12.5, -21.5], skin),            # hocico
          Box("head", [-3.2, 9.5, -26.4], [3.2, 12.0, -25.4], nose),      # almohadilla de la nariz
          Box("head", [-3.6, 6.6, -25.2], [3.6, 8.2, -17], jaw)]          # mandíbula
    side += [Box("head", [4.6, 8.6, -20.5], [5.8, 12.6, -16], plate),     # mejilla
             Box("head", [4.95, 12.1, -21.9], [5.1, 13.7, -20.3], socket),
             Box("head", [4.9, 12.4, -21.6], [5.2, 13.4, -20.6], eye),
             Box("head", [5.0, 13.0, -16.6], [7.6, 14.0, -15.8], skin, rot=(0, 0, -25), origin=[5.0, 13.5, -16.2])]   # oreja
    # cuernos curvos (tres tramos encadenados)
    a, e1, _ = seg("head", [4.2, 15.2, -18.0], [0.7, 0.55, 0.35], 3.2, 2.2, 2.2, horn)
    b, e2, _ = seg("head", e1, [0.25, 0.9, 0.55], 2.8, 1.6, 1.6, horn)
    c, _, _ = seg("head", e2, [-0.15, 0.5, 1.0], 1.8, 1.0, 1.0, horn_tip)
    side += [a, b, c]
    # cola de dos tramos con maza
    t1, te1, _ = seg("tail", [0, 14, 13.5], [0, -0.3, 1], 6.0, 4.0, 5.5, plate)
    t2, te2, _ = seg("tail", te1, [0, -0.55, 1], 5.5, 3.0, 4.0, plate_hi)
    t3, _, _ = seg("tail", te2, [0, -0.6, 1], 2.4, 3.6, 5.0, plate_dk, back=1.0)
    B += [t1, t2, t3]
    piv = {"body": [0, 13, 0], "neck": [0, 12.5, -11], "head": [0, 12.5, -15.5], "tail": [0, 14, 13.5], "sails": [0, 19, 0]}
    # velas del lomo: membrana fina tensada por una espina
    for (cz, h) in ((-9, 5), (-5, 8), (-1, 10), (3, 10), (7, 8), (11, 5)):
        o = [0, 19, cz]
        B.append(Box("sails", [-0.3, 18.6, cz - 2.0], [0.3, 18.6 + h, cz + 2.0], sail, rot=(-10, 0, 0), origin=o))
        B.append(Box("sails", [-0.45, 18.4, cz - 0.35], [0.45, 19.2 + h, cz + 0.35], spine, rot=(-10, 0, 0), origin=o))
    # patas: muslo, caña, rodillera, pezuña y dos dedos
    for k, z in ((0, -6.5), (1, 9.0)):
        g = f"leg_l{k}"
        side += [Box(g, [4.3, 4.5, z - 3], [9.3, 12.5, z + 3], leg),
                 Box(g, [4.8, 1.2, z - 2.4], [8.8, 5.2, z + 2.4], leg),
                 Box(g, [5.0, 3.8, z - 3.4], [8.6, 6.2, z - 2.0], plate),
                 Box(g, [4.5, 0.1, z - 3.2], [9.1, 1.6, z + 2.6], hoof),
                 Box(g, [4.7, 0.0, z - 4.2], [6.7, 1.0, z - 3.0], hoof),
                 Box(g, [6.9, 0.0, z - 4.2], [8.9, 1.0, z - 3.0], hoof)]
        piv[g] = [6.8, 10, z]
        piv[f"leg_r{k}"] = [-6.8, 10, z]
    boxes = B + side + mirror_x(side)
    return "tholin_ruminant", boxes, 128, piv


def eel():
    """Anguila de metano (~1,9 bloques de largo, plana): seis tramos que adelgazan hacia la cola (cada uno metido dentro
    del más grande, sin caras en el mismo plano), cresta dorsal y aleta ventral, aletas pectorales, aleta de cola de dos
    lóbulos, cabeza con hocico, mandíbula, ojos y barbillas que brillan, agallas, y puntos naranjas luminosos."""
    def body_fn(p, n, c):
        if n[1] > 0.4 and spots(p, 2.2, 0.55, 0.55, 41):
            return (255, 150, 60), True                                 # puntos que brillan
        if abs(n[0]) > 0.6 and abs(p[1] - 2.0) < 0.3 and int(p[2]) % 2 == 0:
            return (255, 176, 90), True                                 # línea lateral de puntos
        if abs(n[0]) > 0.6 and int(math.floor(p[2] + 14)) in (0, 2) and 1.3 < p[1] < 2.9 and p[2] < -11:
            return mix(c, (70, 96, 130), 0.55), False                   # dos agallas finas
        if n[1] > 0.4:
            return mix(c, (110, 150, 190), 0.25), False                 # lomo más oscuro
        return c, False
    body = mat("body", (150, 192, 222), (214, 228, 238), 0.05, body_fn, belly_y=1.0)
    head_m = mat("head", (140, 182, 214), (210, 224, 236), 0.05, belly_y=1.2)
    jaw = mat("jaw", (196, 214, 230), noise=0.04)
    fin = mat("fin", (104, 150, 196), noise=0.06, fn=lambda p, n, c: (mix(c, (200, 230, 250), 0.4), False) if int(p[2] * 2) % 3 == 0 else (c, False))
    eye = mat("eye", (255, 170, 70), emissive=True, noise=0.0)
    barbel = mat("barbel", (255, 160, 80), emissive=True, noise=0.0)
    dims = [(4.6, 3.0), (5.4, 3.4), (5.0, 3.2), (4.4, 2.8), (3.6, 2.3), (2.6, 1.7)]
    B, piv = [], {}
    z = -14.0
    cy = 2.0
    for i, (w, h) in enumerate(dims):
        g = f"seg{i}"
        hw, hh = w / 2, h / 2
        z0 = z - (0.6 if i >= 2 else 0.0)                               # el tramo más pequeño entra en el más grande
        z1 = z + 5.0 + (0.6 if i == 0 else 0.0)
        B.append(Box(g, [-hw, cy - hh, z0], [hw, cy + hh, z1], body))
        if i >= 1:                                                      # cresta dorsal (grosor alterno)
            t = 0.22 if i % 2 else 0.17
            B.append(Box(g, [-t, cy + hh - 0.3, z - 0.2], [t, cy + hh + 1.5 - i * 0.15, z + 5.2], fin))
        if i >= 3:                                                      # aleta ventral
            t = 0.2 if i % 2 else 0.15
            B.append(Box(g, [-t, cy - hh - 1.1 + (i - 3) * 0.15, z - 0.2], [t, cy - hh + 0.3, z + 5.2], fin))
        piv[g] = [0, cy, z]
        z += 5.0
    # aleta de la cola: dos lóbulos
    B.append(Box("seg5", [-0.2, cy, z - 1.0], [0.2, cy + 3.2, z + 3.4], fin, rot=(-25, 0, 0), origin=[0, cy, z - 1.0]))
    B.append(Box("seg5", [-0.16, cy - 3.0, z - 1.0], [0.16, cy, z + 3.0], fin, rot=(25, 0, 0), origin=[0, cy, z - 1.0]))
    # aletas pectorales
    side = [Box("seg1", [2.3, 1.5, -8.6], [5.2, 1.8, -5.4], fin, rot=(0, -20, -12), origin=[2.3, 1.65, -7.0])]
    # cabeza
    B += [Box("seg0", [-2.1, 0.7, -18.6], [2.1, 3.3, -13.4], head_m),
          Box("seg0", [-1.7, 0.9, -20.6], [1.7, 2.75, -18.2], head_m),
          Box("seg0", [-1.9, 0.35, -20.1], [1.9, 1.15, -15.0], jaw)]
    side += [Box("seg0", [2.0, 2.0, -17.6], [2.3, 2.9, -16.4], eye),
             Box("seg0", [0.9, -1.5, -19.9], [1.2, 0.9, -19.6], barbel, rot=(20, 0, 15), origin=[1.05, 0.9, -19.75])]
    return "methane_eel", B + side + mirror_x(side), 64, piv


def stalker():
    """Acechador de bruma (~2 bloques de alto): tórax con dos placas de caparazón escarchadas y un collar, abdomen de
    cuatro tramos que se curva hacia arriba, cresta de cristales de hielo, cabeza estrecha con hocico, cuatro ojos
    pálidos, mandíbulas laterales y mandíbula con colmillos de hielo; antena de dos tramos con una linterna dentro de una
    jaula; seis patas de araña de cuatro tramos con rótulas (leg_l0..2 / leg_r0..2)."""
    def skin_fn(p, n, c):
        if spots(p, 3.0, 0.35, 0.8, 51):
            return mix(c, (196, 214, 228), 0.5), False                  # escarcha
        return c, False
    skin = mat("skin", (110, 124, 142), (150, 164, 178), 0.06, skin_fn, belly_y=19)
    plate = mat("plate", (128, 146, 166), (100, 114, 132), 0.05,
                lambda p, n, c: (mix(c, (220, 236, 248), 0.55), False) if spots(p, 2.0, 0.4, 0.6, 52) else (c, False))
    plate_dk = mat("plate_dk", (88, 100, 118), noise=0.05)
    leg = mat("leg", (96, 108, 126), noise=0.06)
    joint = mat("joint", (150, 170, 190), noise=0.05)
    tip = mat("tip", (200, 226, 240), noise=0.04)
    fang = mat("fang", (210, 238, 255), noise=0.03)
    mouth = mat("mouth", (30, 36, 50), noise=0.04)
    stalk = mat("stalk", (80, 92, 110), noise=0.05)
    cage = mat("cage", (60, 68, 82), noise=0.04)
    lure = mat("lure", (255, 220, 150), emissive=True, noise=0.0)
    eye = mat("eye", (170, 230, 255), emissive=True, noise=0.0)
    crystal = mat("crystal", (190, 230, 250), noise=0.03)
    B = [
        Box("body", [-3.2, 18, -7], [3.2, 23, 2], skin),                    # tórax
        Box("body", [-3.6, 22.4, -7.4], [3.6, 23.8, -2.6], plate),          # caparazón (dos placas a distinta altura)
        Box("body", [-3.4, 22.2, -3.2], [3.4, 23.5, 1.6], plate),
        Box("body", [-3.8, 18.8, -7.6], [3.8, 23.2, -6.8], plate_dk),       # collar
    ]
    # abdomen: cuatro tramos encadenados que se curvan hacia arriba
    a1, e, _ = seg("body", [0, 20.6, 1.4], [0, 0.12, 1], 5.0, 4.6, 5.4, skin)
    a2, e, _ = seg("body", e, [0, 0.25, 1], 4.5, 3.8, 4.4, skin)
    a3, e, _ = seg("body", e, [0, 0.45, 1], 4.0, 2.8, 3.2, skin)
    a4, _, _ = seg("body", e, [0, 0.7, 1], 2.2, 1.6, 1.8, plate_dk)
    B += [a1, a2, a3, a4]
    # cristales de hielo en el lomo
    for (y, z, h) in ((23.6, -5.6, 2.2), (23.3, -1.2, 2.8), (22.8, 3.0, 2.0)):
        B.append(Box("body", [-0.5, y - 0.4, z - 0.5], [0.5, y + h, z + 0.5], crystal, rot=(-30, 0, 0), origin=[0, y, z]))
    # cabeza
    B += [Box("head", [-2.6, 18.3, -13], [2.6, 22, -6.6], skin),
          Box("head", [-1.8, 18.7, -16], [1.8, 21.2, -12.6], skin),           # hocico
          Box("head", [-1.5, 18.4, -15.7], [1.5, 18.75, -12.8], mouth),
          Box("jaw", [-1.9, 16.9, -15.8], [1.9, 18.5, -10], skin, rot=(10, 0, 0), origin=[0, 18.4, -10])]
    for x in (-1.15, 1.15):
        B.append(Box("jaw", [x - 0.3, 18.3, -15.4], [x + 0.3, 19.7, -14.8], fang, rot=(10, 0, 0), origin=[0, 18.4, -10]))
        B.append(Box("head", [x - 0.3, 17.6, -15.9], [x + 0.3, 18.5, -15.3], fang))
    side = [Box("head", [2.55, 20.4, -11.6], [2.8, 21.2, -10.6], eye),
            Box("head", [0.6, 21.15, -14.6], [1.3, 21.5, -13.8], eye)]
    m1, _, _ = seg("head", [1.8, 18.8, -14.0], [0.25, -0.3, -1], 3.0, 0.9, 0.9, fang)   # mandíbulas laterales
    side.append(m1)
    # antena y linterna en su jaula
    s1, e, _ = seg("lure", [0, 22.6, -10.5], [0, 1, -0.35], 4.5, 0.8, 0.8, stalk)
    s2, e, _ = seg("lure", e, [0, 0.5, -1], 4.0, 0.7, 0.7, stalk)
    cx, cy, cz = e[0], e[1] - 1.4, e[2] - 0.4
    B += [s1, s2,
          Box("lure", [cx - 1.1, cy - 1.1, cz - 1.1], [cx + 1.1, cy + 1.1, cz + 1.1], lure),
          Box("lure", [cx - 1.7, cy + 1.3, cz - 1.7], [cx + 1.7, cy + 1.8, cz + 1.7], cage),
          Box("lure", [cx - 1.5, cy - 1.8, cz - 1.5], [cx + 1.5, cy - 1.35, cz + 1.5], cage)]
    for (dx, dz) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        B.append(Box("lure", [cx + dx * 1.25 - 0.15, cy - 1.4, cz + dz * 1.25 - 0.15], [cx + dx * 1.25 + 0.15, cy + 1.4, cz + dz * 1.25 + 0.15], cage))
    piv = {"body": [0, 20, 0], "head": [0, 20, -7], "jaw": [0, 18.4, -10], "lure": [0, 22.6, -10.5]}
    # seis patas: coxa, fémur, tibia y tarso, con rótulas
    for k, z in enumerate((-5.0, -0.5, 4.0)):
        dz = (-0.5, 0.0, 0.5)[k]
        hip = [3.0, 20.5, z]
        g = f"leg_l{k}"
        c1, e1, _ = seg(g, hip, [0.9, 0.2, dz * 0.5], 3.0, 2.0, 2.0, plate_dk)
        c2, e2, _ = seg(g, e1, [0.6, 0.8, dz * 0.6], 9.0, 1.6, 1.6, leg)
        d = np.array([0.32, -1.0, dz * 0.3]); d /= np.linalg.norm(d)
        L = (e2[1] - 4.0) / -d[1]
        c3, e3, _ = seg(g, e2, d.tolist(), L, 1.3, 1.3, leg)
        d4 = np.array([0.12, -1.0, dz * 0.15]); d4 /= np.linalg.norm(d4)
        c4, _, _ = seg(g, e3, d4.tolist(), (e3[1] - 0.2) / -d4[1], 0.9, 0.9, tip)
        side += [c1, c2, c3, c4,
                 Box(g, [v - 1.25 for v in e1], [v + 1.25 for v in e1], joint),
                 Box(g, [v - 1.05 for v in e2], [v + 1.05 for v in e2], joint),
                 Box(g, [v - 0.85 for v in e3], [v + 0.85 for v in e3], joint)]
        piv[g] = hip
        piv[f"leg_r{k}"] = [-hip[0], hip[1], hip[2]]
    return "mist_stalker", B + side + mirror_x(side), 128, piv


def haze_bg(w, h):
    g = np.linspace(0, 1, h)[:, None]
    arr = np.zeros((h, w, 4), np.uint8)
    for i, (t0, t1) in enumerate(((120, 60), (78, 40), (40, 26))):
        arr[..., i] = (t0 * (1 - g) + t1 * g).astype(np.uint8)
    arr[..., 3] = 255
    return Image.fromarray(arr, "RGBA")


MOBS = [
    (ruminant, "Rumiador de tolina — pasivo, en manadas; las velas del lomo se mueven; ~1,7 bloques", 5.2,
     ((-38, -14), (140, -18), (-14, -2)), (220, 220)),
    (eel, "Anguila de metano — en los lagos de metano, puntos que brillan; ~1,9 bloques", 6.4,
     ((-38, -30), (140, -24), (-60, -10)), (220, 170)),
    (stalker, "Acechador de bruma — patas de araña, la luz lo atrae; en el juego casi invisible de lejos; ~2 bloques", 4.3,
     ((-38, -12), (140, -16), (-14, -2)), (220, 250)),
]


def build(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    W, H = 440, 320
    sheet = Image.new("RGBA", (W * 3 + 300, len(MOBS) * (H + 50) + 10), (30, 22, 18, 255))
    d = ImageDraw.Draw(sheet)
    y = 0
    for fn, title, scale, angles, ctr in MOBS:
        name, boxes, tw, piv = fn()
        if coplanar_check(boxes, name):
            print(f"  ¡{name}: hay caras que pueden parpadear!")
        th = T.pack(boxes, tw)
        tex, glow = paint(boxes, tw, th, seed=zlib.crc32(name.encode()) & 0xffff)
        tex.save(f"{out_dir}/{name}.png")
        glow.save(f"{out_dir}/{name}_glow.png")
        json.dump(T.to_mob_json(boxes, piv, tw, th), open(f"{out_dir}/{name}.json", "w"), indent=1)
        json.dump(T.to_bbmodel(name, boxes, tex, tw, th), open(f"{out_dir}/{name}.bbmodel", "w"))
        d.text((14, y + 12), title, fill=(244, 226, 200, 255), font=T.title_font(17))
        for k, (a, p_) in enumerate(angles):
            sc = scale * (1.7 if k == 2 else 1.0)
            c = ctr if k < 2 else (170, 230 if name != "methane_eel" else 170)
            r = T.render(boxes, tex, a, p_, sc, (W, H), c)
            bg = haze_bg(W, H)
            bg.alpha_composite(r)
            sheet.alpha_composite(bg, (k * W, y + 42))
        tv = tex.resize((tex.width * 2, tex.height * 2), Image.NEAREST)
        tv.thumbnail((280, 300), Image.NEAREST)
        sheet.alpha_composite(tv, (W * 3 + 10, y + 46))
        y += H + 50
    sheet.save(f"{out_dir}/titan_mobs_preview.png")
    print("ok", out_dir)


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else ".")
