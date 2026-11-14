"""Diseños de las criaturas de Cerúlea (2026-11-13): pastoreador, saltarín, planeador, acechador, croador y pez de
cinta (dos colores); 2026-11-13 (después): corredor.

Uso: python3 -I tools/designs/cerulea_mobs.py <carpeta_salida>

Usa las herramientas de thalassa_mobs.py (cajas, UV de caja, .bbmodel, modelo JSON del juego y render por software),
pero pinta la textura con la posición de cada píxel en el mundo del modelo: así las rayas y las manchas siguen de una
caja a la siguiente sin cortarse. Coordenadas de Blockbench: 16 unidades = 1 bloque, y hacia arriba, el frente mira a -z.
"""
import json
import math
import os
import random
import sys
import zlib

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import thalassa_mobs as T  # noqa: E402
from thalassa_mobs import Box, mirror_x  # noqa: E402


# ------------------------------------------------------------------ pintura por posición en el mundo

def clamp(c):
    return tuple(int(max(0, min(255, x))) for x in c)


def mix(a, b, k):
    return [a[i] * (1 - k) + b[i] * k for i in range(3)]


def h3(x, y, z, seed=0):
    """Número pseudoaleatorio estable (0..1) para una celda entera."""
    n = (int(x) * 73856093) ^ (int(y) * 19349663) ^ (int(z) * 83492791) ^ (seed * 2654435761)
    return (zlib.crc32(n.to_bytes(8, "little", signed=True)) & 0xffff) / 65535.0


def spots(p, cell, prob, radius, seed):
    """¿Está p dentro de una mancha? Celdas de `cell` unidades; en cada una, con probabilidad `prob`, una mancha."""
    c = [math.floor(p[i] / cell) for i in range(3)]
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                q = (c[0] + dx, c[1] + dy, c[2] + dz)
                if h3(*q, seed) > prob:
                    continue
                ctr = [(q[i] + 0.2 + 0.6 * h3(q[0] + i * 7, q[1], q[2], seed + 1)) * cell for i in range(3)]
                if sum((p[i] - ctr[i]) ** 2 for i in range(3)) < radius * radius:
                    return True
    return False


def paint(boxes, tex_w, tex_h, seed):
    """Textura y capa de brillo. Material: base, belly (vientre, por la normal), noise, y fn(p, n, c) → (color, brilla)
    para los dibujos que dependen de la posición."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
    glow = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
    px, gx = img.load(), glow.load()
    for b in boxes:
        m = b.mat
        R = T.rot_matrix(b.rot)
        org = np.array(b.origin, float)
        geo = T.faces3d(b)
        for face, (ru, rv, rw, rh) in T.face_rects(b).items():
            o, A, B = geo[face]
            o = np.array(o, float)
            n = R @ np.array(T.NORMAL[face], float)
            u0, v0 = int(math.floor(ru)), int(math.floor(rv))
            fw = max(1, int(math.ceil(ru + rw)) - u0)
            fh = max(1, int(math.ceil(rv + rh)) - v0)
            for j in range(fh):
                for i in range(fw):
                    s = min(1.0, max(0.0, (u0 + i + 0.5 - ru) / max(rw, 1e-6)))
                    t = min(1.0, max(0.0, (v0 + j + 0.5 - rv) / max(rh, 1e-6)))
                    p = R @ (o + s * A + t * B - org) + org
                    c = list(m["base"])
                    if m.get("belly") is not None:
                        k = min(1.0, max(0.0, (-n[1] - 0.1) / 0.6)) if abs(n[1]) > 0.3 else \
                            min(1.0, max(0.0, (m.get("belly_y", -1e9) - p[1]) / 2.5))
                        c = mix(c, m["belly"], k)
                    lit = m.get("emissive", False)
                    if m.get("fn"):
                        c, l2 = m["fn"](p, n, c)
                        lit = lit or l2
                    if not lit:
                        c = [x * (1.04 if n[1] > 0.5 else 1.0) for x in c]
                        e = m.get("noise", 0.05)
                        c = [x * (1 + rnd.uniform(-e, e)) for x in c]
                    x_, y_ = u0 + i, v0 + j
                    if 0 <= x_ < tex_w and 0 <= y_ < tex_h:
                        px[x_, y_] = clamp(c) + (255,)
                        if lit:
                            gx[x_, y_] = clamp(c) + (255,)
    return img, glow


def mat(name, base, belly=None, noise=0.05, fn=None, emissive=False, belly_y=None):
    d = dict(name=name, base=base, noise=noise, emissive=emissive)
    if belly is not None:
        d["belly"] = belly
    if belly_y is not None:
        d["belly_y"] = belly_y
    if fn:
        d["fn"] = fn
    return d


def mir(boxes):
    """Copia simétrica de cajas con grupos _l → _r."""
    out = mirror_x(boxes)
    for b in out:
        b.group = b.group.replace("_l", "_r") if b.group.endswith("_l") or "_l" in b.group else b.group
    return out


# ------------------------------------------------------------------ criaturas


def grazer():
    """Pastoreador (~2 bloques de largo, 1,7 de alto): herbívoro de manada con seis patas. Lomo azul con rayas verdes
    que cruzan la espalda, vientre verde pálido, joroba, cuello bajo para pastar, hocico ancho y pálido, dos pares de
    ojos y dos antenas con una bolita luminosa en la punta. Cola corta con mechón."""
    def stripes(p, n, c):
        if n[1] > -0.4 and p[1] > 17:
            band = (p[2] + 1.6 * math.sin(p[0] * 0.35) + 40) % 6.5
            if band < 2.4:
                return mix(c, (64, 160, 118), 0.85), False
            if band < 2.9:
                return mix(c, (40, 96, 120), 0.6), False
        return c, False
    hide = mat("hide", (60, 112, 170), (186, 216, 196), 0.05, stripes, belly_y=17)
    hide_n = mat("hide", (60, 112, 170), (186, 216, 196), 0.05, stripes, belly_y=18)
    snout = mat("snout", (176, 206, 196), noise=0.04)
    nostril = mat("nostril", (40, 62, 70), noise=0.02)
    eye = mat("eye", (16, 24, 30), noise=0.0)
    glint = mat("glint", (150, 255, 220), emissive=True, noise=0.0)
    horn = mat("antenna", (52, 84, 120), noise=0.05)
    bulb = mat("bulb", (140, 255, 220), emissive=True, noise=0.02)
    hoof = mat("hoof", (36, 44, 58), noise=0.05)
    leg = mat("leg", (50, 92, 146), (88, 128, 160), 0.05, belly_y=4)
    tuft = mat("tuft", (70, 160, 120), noise=0.1)
    B = [
        Box("body", [-7, 12, -12], [7, 25, 12], hide),
        Box("body", [-6, 25, -9], [6, 28, 6], hide),                     # joroba
        Box("body", [-6, 11, -10], [6, 12, 10], hide),
        Box("neck", [-3.5, 15, -20], [3.5, 22, -10], hide_n, rot=(-18, 0, 0), origin=[0, 19, -11]),
        Box("head", [-4, 13, -27], [4, 20, -19], hide_n),
        Box("head", [-3.2, 10.5, -31], [3.2, 16.5, -25], snout),         # hocico
        Box("head", [-2.4, 13.6, -31.3], [-1.2, 14.8, -31], nostril),
        Box("head", [1.2, 13.6, -31.3], [2.4, 14.8, -31], nostril),
        Box("tail", [-1, 20, 11], [1, 22, 18], hide, rot=(28, 0, 0), origin=[0, 21, 12]),
        # 1.0.3: el mechón va al final de la cola en su marco sin girar (antes quedaba suelto debajo)
        Box("tail", [-1.6, 19.2, 17.0], [1.6, 22.8, 20.5], tuft, rot=(28, 0, 0), origin=[0, 21, 12]),
    ]
    side = [
        Box("head", [3.9, 17, -24.5], [4.4, 18.8, -22.5], eye), Box("head", [4.2, 17.9, -24.2], [4.5, 18.6, -23.5], glint),
        Box("head", [3.9, 14.6, -22.8], [4.3, 15.8, -21.4], eye), Box("head", [4.1, 15.2, -22.6], [4.4, 15.7, -22.1], glint),
        Box("head", [2, 19.5, -22], [3, 27, -21], horn, rot=(-25, 0, 18), origin=[2.5, 19.5, -21.5]),
    ]
    tip = T.rot_matrix((-25, 0, 18)) @ np.array([0, 7.3, 0]) + np.array([2.5, 19.5, -21.5])
    side.append(Box("head", (tip - 0.9).tolist(), (tip + 0.9).tolist(), bulb))
    piv = {"body": [0, 18, 0], "neck": [0, 19, -11], "head": [0, 17, -19], "tail": [0, 21, 12]}
    for k, z in enumerate((-8, 0, 8)):
        side += [Box(f"leg_l{k}", [4, 6, z - 2], [7.5, 14, z + 2], leg),
                 Box(f"leg_l{k}", [4.4, 1.2, z - 1.6], [7.1, 6.5, z + 1.6], leg),
                 Box(f"leg_l{k}", [4.1, 0, z - 2], [7.4, 1.5, z + 2], hoof)]
        piv[f"leg_l{k}"] = [5.5, 13, z]
        piv[f"leg_r{k}"] = [-5.5, 13, z]
    return "grazer", B + side + mirror_x(side), 128, piv


def hopper():
    """Saltarín (~0,6 bloques): bichito redondo de pelaje verde agua que salta con dos patas traseras largas; orejas
    largas y caídas como hojas azules, ojos negros grandes, bracitos y cola de bola."""
    def fur_fn(p, n, c):
        if n[1] > -0.3 and spots(p, 2.2, 0.35, 0.75, 11):
            return mix(c, (84, 168, 150), 0.7), False
        return c, False
    fur = mat("fur", (126, 204, 176), (222, 242, 226), 0.06, fur_fn, belly_y=5)
    ear = mat("ear", (70, 122, 206), noise=0.06,
              fn=lambda p, n, c: ((mix(c, (150, 196, 240), 0.6), False) if abs(n[0]) > 0.5 and (p[1] % 3) < 1 else (c, False)))
    eye = mat("eye", (12, 16, 22), noise=0.0)
    glint = mat("glint", (255, 255, 255), noise=0.0)
    nose = mat("nose", (150, 120, 190), noise=0.02)
    puff = mat("puff", (232, 246, 236), noise=0.05)
    foot = mat("foot", (96, 170, 150), noise=0.05)
    B = [
        Box("body", [-3.5, 2.5, -3], [3.5, 9, 4], fur),
        Box("head", [-3, 6.5, -5.5], [3, 11.5, -0.5], fur),
        Box("head", [-0.5, 8.4, -5.8], [0.5, 9.1, -5.5], nose),
        Box("body", [-1.6, 4, 3.6], [1.6, 7, 6.6], puff),                # cola de bola
    ]
    side = [
        Box("head", [1.3, 9, -5.8], [2.9, 10.8, -5.5], eye), Box("head", [2.0, 10.0, -5.9], [2.6, 10.6, -5.7], glint),
        Box("ear_l", [1.8, 10.5, -2.6], [2.6, 18.5, 0.6], ear, rot=(-30, 0, -32), origin=[2.2, 11, -1]),
        Box(f"leg_l0", [2.4, 1.2, -0.5], [5, 6.5, 4.5], fur),            # muslo
        Box(f"leg_l0", [2.6, 0, -4], [4.8, 1.3, 3.5], foot),             # pie largo
        Box("arm_l", [1.6, 3.2, -3.8], [2.6, 5.8, -2.8], fur, rot=(-25, 0, 0), origin=[2, 5.8, -3.3]),
    ]
    piv = {"body": [0, 5, 0], "head": [0, 8, -1], "ear_l": [2.2, 11, -1], "ear_r": [-2.2, 11, -1],
           "leg_l0": [3.7, 5, 2], "leg_r0": [-3.7, 5, 2], "arm_l": [2, 5.8, -3.3], "arm_r": [-2, 5.8, -3.3]}
    out = mirror_x(side)
    for b in out:
        b.group = b.group.replace("ear_l", "ear_r").replace("arm_l", "arm_r")
    return "hopper", B + side + out, 64, piv


def glider():
    """Planeador (~0,8 bloques de cuerpo, 2 de punta a punta de las membranas): reptil del bosque gigante que planea
    entre los árboles plateados. Escamas gris azulado, membranas azules con nervios claros y una fila de puntos que
    brillan en el borde, cresta turquesa, cola larga con aleta. Pose de vuelo (en el suelo pliega las membranas)."""
    def scale_fn(p, n, c):
        if n[1] > 0.3 and (int(math.floor(p[0] * 1.2)) + int(math.floor(p[2] * 1.2))) % 3 == 0:
            return [x * 0.86 for x in c], False
        return c, False

    def membrane_fn(p, n, c):
        ax = abs(p[0])
        r = math.hypot(ax - 2.5, p[2] * 0.9)
        if ax > 14.2 or p[2] > 4.6 or p[2] < -4.6:
            return (110, 170, 230), False                                    # borde
        if abs(n[1]) > 0.5 and spots([ax * 1.0, 0, p[2] * 1.0], 2.6, 0.5, 0.55, 5) and ax > 10:
            return (160, 245, 255), True                                     # puntos luminosos
        ang = math.atan2(p[2], ax - 2.5)
        if abs((ang * 7) % 1.0 - 0.5) < 0.09 and r > 2:
            return mix(c, (96, 150, 220), 0.7), False                        # nervios
        return mix(c, (30, 56, 120), min(1, r / 18)), False
    scales = mat("scales", (112, 140, 176), (204, 214, 226), 0.05, scale_fn, belly_y=5.2)
    membrane = mat("membrane", (52, 96, 176), noise=0.04, fn=membrane_fn)
    crest = mat("crest", (90, 214, 210), noise=0.05)
    eye = mat("eye", (210, 240, 110), noise=0.0, emissive=True)
    pupil = mat("pupil", (10, 14, 20), noise=0.0)
    claw = mat("claw", (40, 48, 64), noise=0.04)
    B = [
        Box("body", [-2.5, 4, -6], [2.5, 7.5, 6], scales),
        Box("head", [-2, 4.3, -11], [2, 8, -6], scales),
        Box("head", [-1.4, 4.5, -14.5], [1.4, 6.8, -11], scales),
        Box("head", [-0.35, 7.6, -11], [0.35, 11, -5], crest, rot=(-22, 0, 0), origin=[0, 7.6, -8]),
        Box("body", [-0.35, 7.3, -4], [0.35, 9.2, 5], crest),
        Box("tail", [-1.1, 4.6, 5.5], [1.1, 6.6, 14], scales),
        Box("tail", [-0.6, 5.0, 14], [0.6, 6.2, 19], scales),
        Box("tail", [-2.6, 5.4, 16.5], [2.6, 5.8, 21], membrane),
    ]
    side = [
        Box("head", [1.9, 6.1, -10.2], [2.25, 7.4, -8.8], eye), Box("head", [2.15, 6.4, -9.9], [2.35, 7.1, -9.3], pupil),
        Box("wing_l", [2.5, 6.0, -4.6], [14.6, 6.5, 4.6], membrane, rot=(0, 0, -6), origin=[2.5, 6.25, 0]),
        Box("wing_l", [2.2, 5.5, -6], [15.2, 7.0, -4.4], scales, rot=(0, -8, -6), origin=[2.5, 6.25, -5.2]),   # brazo
        Box("wing_l", [2.2, 5.5, 4.2], [14.0, 7.0, 5.6], scales, rot=(0, 8, -6), origin=[2.5, 6.25, 4.9]),     # pata
        Box("wing_l", [14.6, 5.7, -7.4], [15.4, 6.6, -5.6], claw, rot=(0, -8, -6), origin=[2.5, 6.25, -5.2]),
        Box("wing_l", [15.0, 5.7, -6.0], [16.2, 6.6, -5.2], claw, rot=(0, -8, -6), origin=[2.5, 6.25, -5.2]),
        Box("wing_l", [13.4, 5.7, 5.4], [14.2, 6.6, 7.0], claw, rot=(0, 8, -6), origin=[2.5, 6.25, 4.9]),
        Box("wing_l", [13.8, 5.7, 4.6], [15.0, 6.6, 5.4], claw, rot=(0, 8, -6), origin=[2.5, 6.25, 4.9]),
    ]
    out = mirror_x(side)
    for b in out:
        b.group = b.group.replace("wing_l", "wing_r")
    piv = {"body": [0, 6, 0], "head": [0, 6, -6], "tail": [0, 5.6, 5.5], "wing_l": [2.5, 6.25, 0], "wing_r": [-2.5, 6.25, 0]}
    return "glider", B + side + out, 128, piv


def lurker():
    """Acechador (~2,5 bloques con la cola): depredador nocturno del pantano. Piel azul casi negra, manchas turquesa
    que brillan de noche, cresta de púas en el lomo, cabeza ancha y plana con tres pares de ojos luminosos y la boca
    entreabierta con dientes; cuatro patas largas de felino con garras y cola larga con la punta luminosa."""
    def skin_fn(p, n, c):
        if n[1] > -0.35 and p[1] > 9 and spots(p, 3.4, 0.4, 0.8, 21):
            return (80, 232, 214), True
        return c, False
    skin = mat("skin", (26, 38, 54), (64, 80, 92), 0.06, skin_fn, belly_y=10.5)
    skin_head = mat("skin", (30, 44, 60), (70, 86, 96), 0.06, skin_fn, belly_y=10)
    mouth = mat("mouth", (60, 18, 40), noise=0.06)
    tooth = mat("tooth", (220, 230, 226), noise=0.03)
    eye = mat("eye", (110, 255, 230), emissive=True, noise=0.0)
    spine = mat("spine", (40, 96, 110), noise=0.05)
    claw = mat("claw", (14, 18, 24), noise=0.04)
    tip = mat("tail_tip", (110, 255, 230), emissive=True, noise=0.02)
    B = [
        Box("body", [-5.5, 9, -12], [5.5, 18, 2], skin),
        Box("body", [-4.8, 9.5, 2], [4.8, 16.5, 13], skin),
        Box("head", [-5.5, 10.5, -21], [5.5, 16, -12], skin_head),
        Box("head", [-4.2, 11, -26], [4.2, 15, -21], skin_head),
        Box("head", [-3.8, 10.4, -25.6], [3.8, 11.2, -13], mouth),
        Box("jaw", [-4.6, 8, -25], [4.6, 10.4, -13], skin_head, rot=(10, 0, 0), origin=[0, 10.4, -13]),
        Box("jaw", [-3.8, 10.1, -24.5], [3.8, 10.7, -13.5], mouth, rot=(10, 0, 0), origin=[0, 10.4, -13]),
        Box("tail", [-3.2, 11, 12], [3.2, 15, 22], skin),
        Box("tail", [-2.2, 11.6, 22], [2.2, 14.2, 32], skin),
        Box("tail", [-1.3, 12.0, 32], [1.3, 13.8, 39], skin),
        Box("tail", [-1.6, 11.7, 39], [1.6, 14.1, 43], tip),
    ]
    for z, h in ((-11, 5), (-7, 6), (-3, 5.5), (1, 5), (5, 4), (9, 3.5)):
        B.append(Box("body", [-0.5, 16.5 if z < 2 else 15.5, z], [0.5, (16.5 if z < 2 else 15.5) + h, z + 1.6], spine,
                     rot=(-28, 0, 0), origin=[0, 17, z + 0.8]))
    for z in (-25, -22, -19, -16):
        for x in (-3.3, 2.3):
            B.append(Box("head", [x, 9.2, z], [x + 1, 10.6, z + 0.9], tooth))
            B.append(Box("jaw", [x, 10.2, z + 0.5], [x + 1, 11.4, z + 1.3], tooth, rot=(10, 0, 0), origin=[0, 10.4, -13]))
    side = [
        Box("head", [5.3, 14.0, -20.5], [5.7, 15.2, -19.1], eye),
        Box("head", [5.3, 13.0, -18.3], [5.6, 13.9, -17.3], eye),
        Box("head", [5.3, 12.2, -16.4], [5.55, 12.9, -15.6], eye),
    ]
    piv = {"body": [0, 13, 0], "head": [0, 13, -12], "jaw": [0, 10.4, -13], "tail": [0, 13, 12]}
    for k, z in ((0, -9), (1, 9)):
        side += [Box(f"leg_l{k}", [4.6, 7, z - 2], [8, 16, z + 2], skin, rot=(0, 0, -12), origin=[5.5, 15, z]),
                 Box(f"leg_l{k}", [6.2, 0.8, z - 1.5], [8.6, 8.5, z + 1.5], skin),
                 Box(f"leg_l{k}", [5.8, 0, z - 3], [9.0, 1.2, z + 1.6], skin),
                 Box(f"leg_l{k}", [6.0, 0, z - 4], [6.8, 0.8, z - 2.8], claw),
                 Box(f"leg_l{k}", [7.0, 0, z - 4.2], [7.8, 0.8, z - 2.8], claw),
                 Box(f"leg_l{k}", [8.0, 0, z - 4], [8.8, 0.8, z - 2.8], claw)]
        piv[f"leg_l{k}"] = [5.5, 15, z]
        piv[f"leg_r{k}"] = [-5.5, 15, z]
    return "lurker", B + side + mirror_x(side), 128, piv


def croaker():
    """Croador (~0,7 bloques): anfibio de los nenúfares del pantano. Piel verde azulada con manchas azules, vientre
    crema verdoso, ojos dorados grandes encima de la cabeza y un tercer ojo pequeño en la frente, saco vocal azul
    pálido bajo la boca, patas traseras grandes plegadas y pies palmeados."""
    def skin_fn(p, n, c):
        if n[1] > -0.3 and spots(p, 2.4, 0.45, 0.8, 31):
            return mix(c, (46, 90, 170), 0.8), False
        return c, False
    skin = mat("skin", (54, 150, 128), (204, 228, 190), 0.06, skin_fn, belly_y=3.5)
    eye = mat("eye", (232, 190, 70), noise=0.03)
    pupil = mat("pupil", (14, 16, 18), noise=0.0)
    sac = mat("sac", (176, 222, 242), noise=0.04,
              fn=lambda p, n, c: ((mix(c, (130, 190, 230), 0.6), False) if (p[0] * 1.5 % 2) < 0.5 else (c, False)))
    mouth = mat("mouth", (24, 60, 56), noise=0.02)
    web = mat("web", (150, 200, 170), noise=0.05)
    B = [
        Box("body", [-4.5, 2, -4], [4.5, 8, 5], skin, rot=(-14, 0, 0), origin=[0, 2, 1]),
        Box("head", [-4, 4, -9], [4, 9, -3], skin),
        Box("head", [-3.9, 5.2, -9.2], [3.9, 5.7, -8.9], mouth),
        Box("sac", [-3, 2.6, -9.6], [3, 5.2, -5], sac),
        Box("head", [-0.7, 8.6, -8.5], [0.7, 9.8, -7.1], eye),               # tercer ojo
        Box("head", [-0.25, 8.9, -8.6], [0.25, 9.6, -8.4], pupil),
    ]
    side = [
        Box("head", [1.8, 8.3, -7.8], [4.4, 10.9, -5.2], eye),
        Box("head", [2.7, 8.9, -8.0], [3.6, 10.4, -7.7], pupil),
        Box("head", [4.3, 8.9, -7.0], [4.6, 10.4, -6.0], pupil),
        Box("leg_l0", [3.0, 0.5, -7], [4.4, 4.5, -5.6], skin, rot=(-15, 0, 0), origin=[3.7, 4.5, -6.3]),
        Box("leg_l0", [2.6, 0, -8.6], [5.2, 0.6, -5.6], web),
        Box("leg_l1", [3.6, 1, -0.5], [7.4, 5.5, 6], skin, rot=(0, -12, 0), origin=[4, 3, 1]),
        Box("leg_l1", [4.2, 0, -3], [8.4, 0.7, 4], web, rot=(0, -12, 0), origin=[4, 3, 1]),
    ]
    piv = {"body": [0, 4, 0], "head": [0, 6, -4], "sac": [0, 4, -6],
           "leg_l0": [3.7, 4.5, -6.3], "leg_r0": [-3.7, 4.5, -6.3], "leg_l1": [4, 3, 1], "leg_r1": [-4, 3, 1]}
    return "croaker", B + side + mirror_x(side), 64, piv


def ribbonfin(variant="azure"):
    """Pez de cinta (~0,6 bloques): pez alto y aplanado de los ríos y el mar de Cerúlea, con dos ojos por lado, una
    aleta dorsal larga como una cinta y dos cintas que cuelgan del vientre. Dos colores: azul con aletas verde agua y
    verde con rayas oscuras y aletas turquesa."""
    if variant == "azure":
        body_c, belly_c, fin_c, stripe = (64, 122, 222), (206, 226, 244), (80, 200, 160), None
    else:
        body_c, belly_c, fin_c, stripe = (62, 140, 96), (214, 236, 210), (110, 222, 232), (30, 78, 66)

    def body_fn(p, n, c):
        if stripe and abs(n[0]) > 0.5 and p[1] > 3.2 and (p[2] + 20) % 2.6 < 0.9:
            return mix(c, stripe, 0.8), False
        if abs(n[0]) > 0.5 and abs(p[1] - 4.3) < 0.25 and not stripe:
            return mix(c, (170, 240, 255), 0.7), False                    # línea lateral
        return c, False

    def fin_fn(p, n, c):
        if (p[2] * 1.3) % 1.6 < 0.45:
            return mix(c, (230, 250, 250), 0.3), False
        return c, False
    body = mat("body", body_c, belly_c, 0.05, body_fn, belly_y=3)
    fin = mat("fin", fin_c, noise=0.05, fn=fin_fn)
    eye = mat("eye", (240, 220, 120), noise=0.02)
    pupil = mat("pupil", (10, 12, 16), noise=0.0)
    B = [
        Box("body", [-1.5, 1.8, -4], [1.5, 7.2, 3], body),
        Box("body", [-1.3, 2.4, -6.2], [1.3, 6.6, -4], body),
        Box("body", [-0.9, 3.2, -7], [0.9, 5.4, -6.2], body),
        Box("tail", [-0.8, 3.2, 3], [0.8, 5.8, 6], body),
        Box("tail", [-0.25, 2.0, 5.5], [0.25, 7.0, 8.6], fin),
        Box("body", [-0.25, 7.0, -3.0], [0.25, 8.8, 3.0], fin),
        Box("body", [-0.2, 7.0, 2.6], [0.2, 8.0, 6.5], fin, rot=(-6, 0, 0), origin=[0, 7.5, 2.8]),
    ]
    side = [
        Box("body", [1.2, 4.9, -5.8], [1.4, 5.9, -4.8], eye), Box("body", [1.3, 5.2, -5.6], [1.5, 5.7, -5.1], pupil),
        Box("body", [1.2, 3.6, -5.3], [1.4, 4.3, -4.6], eye), Box("body", [1.3, 3.8, -5.15], [1.5, 4.15, -4.8], pupil),
        Box("body", [1.4, 3.4, -3.0], [1.6, 4.8, -1.0], fin, rot=(0, 25, 10), origin=[1.5, 4, -3]),
        Box("body", [0.4, 1.0, -1.5], [0.6, 2.0, 3.5], fin, rot=(10, 0, 0), origin=[0.5, 2.0, -1.5]),
    ]
    piv = {"body": [0, 4.5, 0], "tail": [0, 4.5, 3]}
    return ("ribbonfin" if variant == "azure" else "ribbonfin_mire"), B + side + mirror_x(side), 64, piv


def runner():
    """Corredor (~1,4 bloques de largo, 0,9 de alto): depredador de manada de praderas y bosques. Flaco y de patas
    largas, verde oscuro con rayas azules (camuflaje en el pasto alto), cresta de pelo en el lomo, dos pares de ojos que
    brillan un poco, hocico largo con colmillos y cola larga con la punta azul. Acecha agachado y salta sobre la presa."""
    def stripes(p, n, c):
        if n[1] > -0.4 and p[1] > 11:
            band = (p[2] + 1.2 * math.sin(p[0] * 0.5) + 40) % 4.2
            if band < 1.3:
                return mix(c, (36, 66, 128), 0.8), False
        return c, False
    fur = mat("fur", (44, 96, 74), (132, 170, 140), 0.06, stripes, belly_y=11.5)
    fur_h = mat("fur", (48, 102, 80), (140, 176, 146), 0.06, stripes, belly_y=15)
    leg = mat("leg", (38, 82, 66), (70, 112, 92), 0.06, belly_y=3)
    crest = mat("crest", (52, 96, 170), noise=0.08)
    eye = mat("eye", (190, 255, 150), emissive=True, noise=0.0)
    nose = mat("nose", (18, 26, 30), noise=0.02)
    mouth = mat("mouth", (70, 24, 44), noise=0.05)
    fang = mat("fang", (226, 232, 220), noise=0.03)
    paw = mat("paw", (24, 40, 40), noise=0.04)
    B = [
        Box("body", [-3.4, 10, -8], [3.4, 16.5, -1], fur),
        Box("body", [-2.7, 10.8, -1], [2.7, 15.4, 7.5], fur),
        Box("neck", [-2.2, 13, -12], [2.2, 17.2, -7], fur_h, rot=(-28, 0, 0), origin=[0, 15, -8]),
        Box("head", [-2.9, 14.5, -16], [2.9, 19.5, -11], fur_h),
        Box("head", [-1.8, 14.6, -21], [1.8, 17.6, -16], fur_h),
        Box("head", [-0.9, 16.8, -21.3], [0.9, 17.8, -20.6], nose),
        Box("head", [-1.7, 13.9, -15.2], [1.7, 14.6, -20.6], mouth),
        Box("jaw", [-1.6, 12.8, -20.2], [1.6, 13.9, -15.2], fur_h, rot=(8, 0, 0), origin=[0, 13.9, -15.2]),
        Box("head", [-1.5, 13.6, -20.6], [-0.9, 14.7, -20.0], fang), Box("head", [0.9, 13.6, -20.6], [1.5, 14.7, -20.0], fang),
        Box("tail", [-0.9, 13.6, 7], [0.9, 15.2, 16], fur, rot=(22, 0, 0), origin=[0, 14.5, 7]),
        # 1.0.3: la punta va al final de la cola en su marco sin girar (antes estaba bajada y además girada: quedaba suelta)
        Box("tail", [-1.3, 13.1, 15.2], [1.3, 15.7, 19.4], crest, rot=(22, 0, 0), origin=[0, 14.5, 7]),
    ]
    for z, h in ((-7.5, 3.0), (-5, 3.6), (-2.5, 3.2), (0, 2.6), (2.5, 2.0)):
        B.append(Box("body", [-0.7, 15.6, z], [0.7, 15.6 + h, z + 2.2], crest, rot=(-35, 0, 0), origin=[0, 16, z + 1.1]))
    side = [
        Box("head", [2.8, 17.4, -15.4], [3.1, 18.4, -14.2], eye),
        Box("head", [2.8, 16.0, -14.2], [3.05, 16.7, -13.4], eye),
        Box("head", [1.4, 19.2, -13.5], [2.4, 22.5, -12.3], crest, rot=(35, 0, 12), origin=[1.9, 19.2, -12.9]),
    ]
    piv = {"body": [0, 13, 0], "neck": [0, 15, -8], "head": [0, 16, -11], "jaw": [0, 13.9, -15.2], "tail": [0, 14.5, 7]}
    for k, z in ((0, -5), (1, 4.5)):
        side += [Box(f"leg_l{k}", [2.0, 6.5, z - 1.5], [4.2, 13.5, z + 1.5], leg),
                 Box(f"leg_l{k}", [2.4, 0.8, z - 1.0], [3.8, 7.0, z + 1.0], leg),
                 Box(f"leg_l{k}", [2.2, 0, z - 2.2], [4.0, 1.0, z + 1.2], paw)]
        piv[f"leg_l{k}"] = [3.1, 13, z]
        piv[f"leg_r{k}"] = [-3.1, 13, z]
    return "runner", B + side + mirror_x(side), 64, piv


# ------------------------------------------------------------------ hoja de vista previa

SIDE = ((-35, -18), (145, -24), (90, -4))
MOBS = [
    (grazer, "Pastoreador (grazer) — herbívoro de manada, seis patas; ~2 bloques de largo, 1,7 de alto", 5.6, SIDE, (220, 230)),
    (runner, "Corredor (runner) — NUEVO; depredador de manada de praderas y bosques; ~1,4 bloques; domable", 8.5, SIDE, (220, 240)),
    (hopper, "Saltarín (hopper) — salta entre el pasto alto; ~0,6 bloques", 13, SIDE, (220, 215)),
    (glider, "Planeador (glider) — planea entre los árboles plateados; 2 bloques de punta a punta", 10,
     ((-30, -42), (150, -38), (0, -88)), (220, 190)),
    (lurker, "Acechador (lurker) — depredador nocturno del pantano; ~2,5 bloques con la cola; manchas que brillan", 6.0,
     ((-55, -16), (150, -26), (90, -4)), (220, 200)),
    (croaker, "Croador (croaker) — anfibio de los nenúfares; ~0,7 bloques; tres ojos y saco vocal", 15, SIDE, (220, 225)),
    (lambda: ribbonfin("azure"), "Pez de cinta (ribbonfin) — ríos y mar; ~0,6 bloques; dos colores", 17, SIDE, (220, 230)),
    (lambda: ribbonfin("mire"), "Pez de cinta — variante verde rayada", 17, SIDE, (220, 230)),
]


def sky_bg(w, h, night=False):
    g = np.linspace(0, 1, h)[:, None]
    top, bot = ((18, 28, 46), (36, 58, 72)) if night else ((120, 176, 196), (190, 214, 196))
    arr = np.zeros((h, w, 4), np.uint8)
    for i in range(3):
        arr[..., i] = (top[i] * (1 - g) + bot[i] * g).astype(np.uint8)
    arr[..., 3] = 255
    return Image.fromarray(arr, "RGBA")


def build(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    panels = []
    for fn, title, scale, angles, ctr in MOBS:
        name, boxes, tex_w, pivots = fn()
        tex_h = T.pack(boxes, tex_w)
        tex, glow = paint(boxes, tex_w, tex_h, seed=zlib.crc32(name.encode()) & 0xffff)
        tex.save(f"{out_dir}/{name}.png")
        if glow.getbbox():
            glow.save(f"{out_dir}/{name}_glow.png")
        json.dump(T.to_mob_json(boxes, pivots, tex_w, tex_h), open(f"{out_dir}/{name}.json", "w"), indent=1)
        json.dump(T.to_bbmodel(name, boxes, tex, tex_w, tex_h), open(f"{out_dir}/{name}.bbmodel", "w"))
        views = [T.render(boxes, tex, a, p, scale, (440, 320), ctr) for a, p in angles]
        night = None
        if glow.getbbox():
            dark = T.render(boxes, Image.alpha_composite(
                Image.fromarray((np.asarray(tex.convert("RGBA")).astype(float) * [0.22, 0.22, 0.3, 1]).astype(np.uint8), "RGBA"), glow),
                angles[0][0], angles[0][1], scale, (440, 320), ctr)
            night = dark
        panels.append((title, views, night, tex))
    W = 440 * 3 + 300
    H = len(panels) * 360
    sheet = Image.new("RGBA", (W, H), (22, 30, 38, 255))
    d = ImageDraw.Draw(sheet)
    y = 0
    for title, views, night, tex in panels:
        d.text((14, y + 10), title, fill=(225, 240, 235, 255), font=T.title_font(17))
        for k, v in enumerate(views):
            if k == 2 and night is not None:
                v = night
            bg = sky_bg(v.width, v.height, night=(k == 2 and night is not None))
            bg.alpha_composite(v)
            sheet.alpha_composite(bg, (k * 440, y + 36))
        if night is not None:
            d.text((2 * 440 + 10, y + 40), "de noche", fill=(180, 220, 230, 255), font=T.title_font(13))
        tv = tex.copy()
        k = max(1, min(256 // tex.width * 2, 4))
        tv = tv.resize((tex.width * k, tex.height * k), Image.NEAREST)
        tv.thumbnail((280, 300), Image.NEAREST)
        sheet.alpha_composite(Image.new("RGBA", (tv.width + 8, tv.height + 8), (44, 48, 56, 255)), (1336, y + 40))
        sheet.alpha_composite(tv, (1340, y + 44))
        y += 360
    sheet.save(f"{out_dir}/cerulea_mobs_preview.png")
    print("ok", out_dir)


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else ".")
