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


def ruminant():
    """Rumiador de tolina (~1,7 bloques de largo, 1,3 de alto con las velas): animal grande, bajo y ancho de cuatro patas
    gruesas, cubierto de placas ocres que se solapan, con una cresta de velas finas en el lomo (membrana naranja con
    espinas oscuras). Cabeza ancha y baja con dos protuberancias como cuernos romos y ojos pequeños ámbar."""
    def plate_fn(p, n, c):
        if n[1] > 0.4 and (p[2] + 30) % 5.0 < 0.9:
            return mix(c, (96, 58, 30), 0.65), False                 # borde de cada placa (franjas en el lomo)
        if abs(n[0]) > 0.6 and (p[2] + 30) % 5.0 < 0.9 and p[1] > 12:
            return mix(c, (96, 58, 30), 0.55), False
        if spots(p, 3.0, 0.3, 0.9, 31):
            return mix(c, (214, 162, 96), 0.45), False               # manchas claras de tolina
        return c, False
    plate = mat("plate", (176, 116, 58), (206, 176, 128), 0.06, plate_fn, belly_y=10)
    skin = mat("skin", (150, 98, 54), (196, 164, 118), 0.06, belly_y=9)
    leg = mat("leg", (128, 84, 46), (150, 104, 62), 0.06, belly_y=2)
    hoof = mat("hoof", (52, 36, 26), noise=0.04)
    horn = mat("horn", (88, 62, 40), noise=0.05)

    def sail_fn(p, n, c):
        if (p[2] + 29) % 4.0 < 0.6:
            return (92, 50, 28), False                                # espina central de cada vela
        k = max(0.0, min(1.0, (p[1] - 20) / 8.0))
        return mix(c, (240, 168, 80), k), False                       # más clara hacia la punta
    sail = mat("sail", (214, 112, 46), noise=0.05, fn=sail_fn)
    eye = mat("eye", (255, 190, 80), noise=0.0)
    B = [
        Box("body", [-9, 7, -12], [9, 19, 14], plate),                  # tronco ancho y bajo
        Box("body", [-8, 6, -11], [8, 7, 13], skin),                    # vientre
        Box("body", [-9.6, 13, -11], [9.6, 18, 12], plate),             # placas de los lados (sobresalen)
        Box("tail", [-3, 10, 13], [3, 15, 22], plate, rot=(-15, 0, 0), origin=[0, 13, 13]),
        Box("head", [-5.5, 7, -21], [5.5, 15, -11], skin),              # cabeza ancha y baja
        Box("head", [-4.5, 6, -25], [4.5, 12, -21], skin),              # hocico
        Box("head", [-6, 12.5, -20], [6, 15.5, -13], plate),            # placa de la frente
        Box("head", [-0.9, 9.5, -25.2], [0.9, 11, -24.9], hoof),        # fosas nasales (centro)
    ]
    side = [
        Box("head", [4.5, 14, -18], [7.5, 16.5, -15], horn, rot=(0, 0, -20), origin=[5, 15, -16.5]),   # protuberancias
        Box("head", [5.6, 10.5, -19], [5.7, 12, -17.5], eye),
    ]
    piv = {"body": [0, 13, 0], "head": [0, 11, -11], "tail": [0, 13, 13], "sails": [0, 19, 0]}
    # velas del lomo: membranas finas, más altas en el centro
    for i, (z, h) in enumerate(((-9, 5), (-5, 8), (-1, 10), (3, 10), (7, 8), (11, 5))):
        B.append(Box("sails", [-0.4, 19, z - 1.9], [0.4, 19 + h, z + 1.9], sail, rot=(-8, 0, 0), origin=[0, 19, z]))
    for k, z in ((0, -7), (1, 9)):
        side += [Box(f"leg_l{k}", [4.5, 1, z - 3], [9.5, 9, z + 3], leg),
                 Box(f"leg_l{k}", [4.2, 0, z - 3.4], [9.8, 1.2, z + 3.4], hoof)]
        piv[f"leg_l{k}"] = [7, 9, z]
        piv[f"leg_r{k}"] = [-7, 9, z]
    return "tholin_ruminant", B + side + mirror_x(side), 128, piv


def eel():
    """Anguila de metano (~1,8 bloques de largo, plana): cuerpo largo azul pálido con puntos naranjas que brillan en el
    lomo, aletas finas a los lados y una aleta de cola. Seis tramos (seg0..seg5) para que ondule al nadar."""
    def body_fn(p, n, c):
        if n[1] > 0.4 and spots(p, 2.2, 0.55, 0.55, 41):
            return (255, 150, 60), True                                 # puntos que brillan
        if abs(n[0]) > 0.6 and abs(p[1] - 2.0) < 0.35:
            return mix(c, (90, 130, 170), 0.6), False                  # línea lateral
        return c, False
    body = mat("body", (150, 192, 220), (210, 226, 236), 0.05, body_fn, belly_y=1.0)
    fin = mat("fin", (110, 160, 200), noise=0.06)
    eye = mat("eye", (255, 170, 70), emissive=True, noise=0.0)
    mouth = mat("mouth", (40, 50, 70), noise=0.03)
    B, piv = [], {}
    z = -14.0
    widths = [5.0, 5.5, 5.0, 4.4, 3.6, 2.8]
    for i, w in enumerate(widths):
        L = 5.0
        g = f"seg{i}"
        hw = w / 2
        B.append(Box(g, [-hw, 0.6, z], [hw, 3.4, z + L + 0.4], body))
        if i in (1, 2, 3):                                              # aletas laterales finas
            B.append(Box(g, [hw, 1.6, z + 0.5], [hw + 2.2, 1.9, z + L - 0.5], fin))
            B.append(Box(g, [-hw - 2.2, 1.6, z + 0.5], [-hw, 1.9, z + L - 0.5], fin))
        piv[g] = [0, 2, z]
        z += L
    B.append(Box("seg5", [-0.2, -0.6, z - 1], [0.2, 4.6, z + 4], fin))   # aleta de la cola (vertical)
    # cabeza: hocico plano con ojos a los lados
    B += [Box("seg0", [-2.2, 0.8, -18], [2.2, 3.0, -14], body),
          Box("seg0", [-1.8, 0.7, -18.2], [1.8, 1.1, -15], mouth),
          Box("seg0", [2.2, 2.0, -16.6], [2.35, 2.8, -15.6], eye),
          Box("seg0", [-2.35, 2.0, -16.6], [-2.2, 2.8, -15.6], eye)]
    return "methane_eel", B, 64, piv


def stalker():
    """Acechador de bruma (~2 bloques de alto, patas de araña muy largas): cuerpo estrecho y alargado gris azulado,
    casi translúcido en el juego, con una "linterna" que brilla al final de una antena sobre la cabeza (imita una luz).
    Seis patas finas articuladas (leg_l0..2 / leg_r0..2) y una mandíbula con colmillos de hielo."""
    def skin_fn(p, n, c):
        if spots(p, 3.0, 0.35, 0.8, 51):
            return mix(c, (196, 214, 228), 0.5), False                  # escarcha
        if n[1] > 0.4 and (p[2] + 30) % 4.0 < 0.7:
            return mix(c, (60, 70, 86), 0.5), False                     # segmentos
        return c, False
    skin = mat("skin", (110, 124, 142), (150, 164, 178), 0.06, skin_fn, belly_y=15)
    leg = mat("leg", (96, 108, 126), noise=0.06)
    tip = mat("tip", (200, 226, 240), noise=0.04)
    fang = mat("fang", (210, 238, 255), noise=0.03)
    mouth = mat("mouth", (30, 36, 50), noise=0.04)
    stalk = mat("stalk", (80, 92, 110), noise=0.05)
    lure = mat("lure", (255, 220, 150), emissive=True, noise=0.0)
    eye = mat("eye", (170, 230, 255), emissive=True, noise=0.0)
    B = [
        Box("body", [-3.2, 18, -7], [3.2, 23, 2], skin),                  # tórax
        Box("body", [-2.6, 18.5, 2], [2.6, 22.5, 12], skin, rot=(10, 0, 0), origin=[0, 20.5, 2]),   # abdomen largo
        Box("body", [-1.8, 19, 11], [1.8, 21.5, 17], skin, rot=(20, 0, 0), origin=[0, 20.5, 11]),
        Box("head", [-2.6, 18, -13], [2.6, 22, -7], skin),                # cabeza estrecha
        Box("head", [-1.8, 18.6, -16], [1.8, 21, -13], skin),             # hocico
        Box("head", [-1.6, 18.4, -15.8], [1.6, 18.9, -9], mouth),
        Box("jaw", [-1.8, 16.6, -15.6], [1.8, 18.4, -9.5], skin, rot=(10, 0, 0), origin=[0, 18.4, -9.5]),
        # antena con la linterna (el señuelo que imita una luz)
        Box("lure", [-0.4, 22, -12], [0.4, 30, -11.2], stalk, rot=(-35, 0, 0), origin=[0, 22, -11.6]),
        Box("lure", [-1.3, 29.0, -18.2], [1.3, 31.6, -15.6], lure),
    ]
    for x in (-1.2, 1.2):
        B.append(Box("jaw", [x - 0.3, 18.2, -15.4], [x + 0.3, 19.6, -14.8], fang, rot=(10, 0, 0), origin=[0, 18.4, -9.5]))
        B.append(Box("head", [x - 0.3, 17.4, -15.9], [x + 0.3, 18.6, -15.3], fang))
    side = [Box("head", [2.5, 20, -12], [2.75, 21.2, -10.6], eye)]       # ojos pequeños y pálidos
    piv = {"body": [0, 20, 0], "head": [0, 20, -7], "jaw": [0, 18.4, -9.5], "lure": [0, 22, -11.6]}
    # seis patas de araña: muslo hacia arriba y afuera, pierna larga hacia el suelo
    for k, z in enumerate((-5, 0, 5)):
        hip = [3.0, 20.0, z]
        out_z = (-0.45, 0.0, 0.45)[k]
        a, knee, _ = seg(f"leg_l{k}", hip, [0.75, 0.62, out_z * 0.6], 10.0, 1.6, 1.6, leg)
        b2, foot, _ = seg(f"leg_l{k}", knee, [0.35, -1.0, out_z * 0.35], 26.0, 1.2, 1.2, leg)
        c2, _, _ = seg(f"leg_l{k}", foot, [0.15, -1.0, out_z * 0.2], 2.5, 1.0, 1.0, tip, back=0.4)
        side += [a, b2, c2]
        piv[f"leg_l{k}"] = hip
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
    (eel, "Anguila de metano — en los lagos de metano, puntos que brillan; ~1,8 bloques", 7.0,
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
