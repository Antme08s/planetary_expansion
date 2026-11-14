"""Criaturas hostiles de Ferra (1.0.3, idea del usuario: "monstruos gusanos de tierra" y "monstruos cuadrúpedos que
cacen en manada, ciegos como el Warden, que cacen por ruido"). Propuesta: vista previa antes de meterlos en el juego.

Uso: python3 -I tools/designs/ferra_mobs.py <carpeta_salida>

Mismas herramientas que cerulea_mobs.py (cajas, pintura por posición en el mundo, modelo JSON, .bbmodel y render).
Coordenadas de Blockbench: 16 unidades = 1 bloque, y hacia arriba, el frente mira a -z.
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
from thalassa_mobs import Box, mirror_x  # noqa: E402
from cerulea_mobs import paint, mat, mix, spots  # noqa: E402


def worm():
    """Gusano de óxido (~5 bloques, 1 de grueso): vive bajo el polvo y sale de golpe donde nota pasos. Ocho anillos con
    placas de hierro oxidado que se solapan por encima, carne rosada entre las placas y por debajo, y una boca redonda de
    lamprea en toda la cara con dos coronas de dientes y tres garfios. Cada anillo es un grupo (seg0..seg7) para que ondule."""
    def plate_fn(p, n, c):
        if spots(p, 3.2, 0.35, 1.2, 11):
            return mix(c, (64, 30, 22), 0.6), False                 # óxido oscuro
        if spots(p, 4.0, 0.25, 0.9, 12):
            return mix(c, (60, 150, 124), 0.6), False               # pátina verde
        if n[1] > 0.5 and (p[0] * 0.7 + p[2]) % 5 < 0.6:
            return mix(c, (226, 150, 96), 0.4), False               # rayaduras claras
        return c, False

    def flesh_fn(p, n, c):
        if (p[2] % 2.0) < 0.5:
            return mix(c, (120, 50, 52), 0.5), False                 # pliegues
        return c, False
    plate = mat("plate", (150, 74, 42), (120, 60, 40), 0.07, plate_fn)
    flesh = mat("flesh", (178, 104, 96), (196, 132, 118), 0.06, flesh_fn)
    tooth = mat("tooth", (226, 214, 190), noise=0.04)
    pit = mat("pit", (255, 168, 90), emissive=True, noise=0.0)    # fosas que notan las vibraciones (brillan)
    B, piv = [], {}
    z = -4.0
    widths = [15, 16, 16, 15, 14, 12, 10, 8]
    for i, w in enumerate(widths):
        L = 10.0 if i < 6 else 9.0
        hw = w / 2
        h0, h1 = 8 - hw, 8 + hw
        g = f"seg{i}"
        B.append(Box(g, [-hw + 0.6, h0 + 0.6, z], [hw - 0.6, h1 - 0.6, z + L], flesh))
        # placa: por encima y los lados, un poco más ancha y adelantada (se solapa con el anillo de delante)
        B.append(Box(g, [-hw - 0.4, h0 + hw * 0.55, z - 1.2], [hw + 0.4, h1 + 0.5, z + L - 1.5], plate))
        B.append(Box(g, [-hw - 0.2, h0 + 1.5, z - 0.6], [-hw + 0.8, h0 + hw * 0.6, z + L - 2.0], plate))
        B.append(Box(g, [hw - 0.8, h0 + 1.5, z - 0.6], [hw + 0.2, h0 + hw * 0.6, z + L - 2.0], plate))
        if i in (1, 3, 5):                                        # fosas sensoras a los lados
            B.append(Box(g, [hw + 0.2, 8.5, z + 3], [hw + 0.6, 10, z + 5], pit))
            B.append(Box(g, [-hw - 0.6, 8.5, z + 3], [-hw - 0.2, 10, z + 5], pit))
        piv[g] = [0, 8, z]
        z += L
    # cabeza: boca redonda de lamprea que ocupa toda la cara: garganta negra, dos anillos de dientes pintados y dientes
    # de verdad (cajas) que salen hacia delante en dos coronas
    def mouth_fn(p, n, c):
        if n[2] > -0.5:
            return c, False
        r = math.hypot(p[0], p[1] - 8)
        a = math.degrees(math.atan2(p[1] - 8, p[0])) % 360
        if r < 2.6:
            return (20, 6, 10), False
        if r < 3.8:
            return ((230, 218, 196) if int(a / 30) % 2 == 0 else (90, 20, 30)), False
        if r < 5.0:
            return (100, 26, 38), False
        if r < 6.0:
            return ((236, 224, 200) if int(a / 22.5) % 2 == 0 else (120, 34, 44)), False
        if r < 7.0:
            return (170, 84, 86), False
        return c, False
    mouthm = mat("mouth", (150, 74, 42), (120, 60, 40), 0.06, lambda p, n, c: mouth_fn(p, n, plate_fn(p, n, c)[0]))
    head = [Box("head", [-8.5, -0.5, -11], [8.5, 16.5, -4], mouthm)]
    for (rr, k, ln) in ((6.2, 12, 2.2), (3.4, 8, 1.6)):
        for i in range(k):
            a = i * math.pi * 2 / k + (0.13 if k == 8 else 0)
            x, y = rr * math.cos(a), 8 + rr * math.sin(a)
            head.append(Box("head", [x - 0.45, y - 0.45, -11 - ln], [x + 0.45, y + 0.45, -11], tooth))
    # tres garfios alrededor de la boca (para sujetar a la presa)
    for ang in (90, 210, 330):
        a = math.radians(ang)
        x, y = 7.6 * math.cos(a), 8 + 7.6 * math.sin(a)
        head.append(Box("head", [x - 0.9, y - 0.9, -14], [x + 0.9, y + 0.9, -10.5], plate))
        head.append(Box("head", [x * 0.88 - 0.5, (y - 8) * 0.88 + 8 - 0.5, -15.5], [x * 0.88 + 0.5, (y - 8) * 0.88 + 8 + 0.5, -14], tooth))
    piv["head"] = [0, 8, -4]
    return "rust_worm", B + head, 128, piv


def stalker():
    """Rastreador ciego (~1,8 bloques de largo, 1,2 de alto): cazador de manada, sin ojos (como el Warden) y caza por
    el ruido. Cuerpo flaco y encorvado de piel gris parda con placas de óxido en el lomo, costillas marcadas, cabeza
    alargada y lisa sin ojos, dos abanicos de "orejas" con órganos que brillan cuando oye algo, mandíbula grande con
    colmillos y patas largas con garras."""
    def skin_fn(p, n, c):
        if n[1] > 0.3 and p[1] > 15 and spots(p, 3.0, 0.45, 1.3, 21):
            return mix(c, (150, 72, 40), 0.75), False                # placas de óxido en el lomo
        if abs(n[0]) > 0.6 and 11 < p[1] < 16 and (p[2] + 20) % 2.6 < 0.8 and p[2] < 4:
            return mix(c, (40, 34, 34), 0.55), False                 # costillas
        return c, False
    skin = mat("skin", (96, 86, 82), (140, 124, 116), 0.06, skin_fn, belly_y=12)
    skin_h = mat("skin", (104, 92, 86), (150, 132, 122), 0.06, skin_fn, belly_y=16)
    leg = mat("leg", (82, 72, 70), (110, 96, 90), 0.06, belly_y=3)
    claw = mat("claw", (36, 30, 30), noise=0.03)
    mouth = mat("mouth", (60, 16, 24), noise=0.04)
    fang = mat("fang", (230, 222, 200), noise=0.03)
    sense = mat("sense", (255, 176, 96), emissive=True, noise=0.0)
    fin = mat("fin", (72, 58, 56), noise=0.06)
    B = [
        Box("body", [-4.0, 11, -10], [4.0, 18.5, -1], skin),            # pecho alto
        Box("body", [-3.2, 11.5, -1], [3.2, 17.0, 9], skin),            # cintura
        Box("body", [-3.6, 11.5, 9], [3.6, 16.5, 13], skin),            # cadera
        Box("neck", [-2.4, 14, -15], [2.4, 18.4, -9], skin_h, rot=(-18, 0, 0), origin=[0, 16, -10]),
        Box("head", [-3.0, 13.5, -22], [3.0, 18.5, -14], skin_h),       # cabeza lisa, sin ojos
        Box("head", [-2.2, 14.0, -26.5], [2.2, 17.2, -22], skin_h),     # hocico
        Box("head", [-2.0, 13.2, -26], [2.0, 14.0, -15], mouth),
        Box("jaw", [-2.4, 11.4, -26], [2.4, 13.2, -15.5], skin_h, rot=(14, 0, 0), origin=[0, 13.2, -15.5]),
        Box("jaw", [-2.0, 13.2, -25.5], [2.0, 13.7, -16], mouth, rot=(14, 0, 0), origin=[0, 13.2, -15.5]),
        Box("tail", [-1.2, 13.5, 12.5], [1.2, 15.5, 24], skin, rot=(18, 0, 0), origin=[0, 14.5, 12.5]),
    ]
    # colmillos arriba y abajo
    for x in (-1.6, -0.5, 0.5, 1.6):
        B.append(Box("head", [x - 0.35, 12.4, -25.6], [x + 0.35, 13.3, -25.0], fang))
        B.append(Box("jaw", [x - 0.35, 13.2, -25.2], [x + 0.35, 14.1, -24.6], fang, rot=(14, 0, 0), origin=[0, 13.2, -15.5]))
    # órgano que brilla en el pecho (como el corazón del Warden) y cresta de placas
    B.append(Box("body", [-1.6, 12.5, -10.4], [1.6, 15.5, -9.9], sense))
    for z, h in ((-8, 2.2), (-5, 2.8), (-2, 2.4), (1, 2.0), (4, 1.6), (7, 1.2)):
        B.append(Box("body", [-1.2, 17.0, z], [1.2, 17.0 + h, z + 2.4], skin_h, rot=(-25, 0, 0), origin=[0, 17.5, z + 1.2]))
    side = [
        # abanico de "orejas": tres aletas con órganos que brillan (oyen)
        Box("ear_l", [2.8, 16, -19], [3.4, 23, -15], fin, rot=(18, 0, -20), origin=[3, 17.5, -17]),
        Box("ear_l", [3.0, 18, -18.6], [3.6, 22, -16.6], sense, rot=(18, 0, -20), origin=[3, 17.5, -17]),
        Box("ear_l", [2.9, 15, -16], [3.5, 21, -12.5], fin, rot=(-10, 0, -35), origin=[3, 16.5, -14]),
        Box("ear_l", [3.1, 17, -15.4], [3.7, 20, -13.6], sense, rot=(-10, 0, -35), origin=[3, 16.5, -14]),
    ]
    piv = {"body": [0, 14, 0], "neck": [0, 16, -10], "head": [0, 16, -14], "jaw": [0, 13.2, -15.5], "tail": [0, 14.5, 12.5],
           "ear_l": [3, 17, -16], "ear_r": [-3, 17, -16]}
    for k, z in ((0, -6), (1, 9)):
        top = 15 if k == 0 else 14
        side += [Box(f"leg_l{k}", [3.2, 7.5, z - 1.8], [5.6, top, z + 1.8], skin),
                 Box(f"leg_l{k}", [3.6, 1.0, z - 1.2], [5.2, 8.0, z + 1.2], leg),
                 Box(f"leg_l{k}", [3.3, 0, z - 3.0], [5.5, 1.2, z + 1.0], leg)]
        for cx in (3.7, 4.4, 5.1):
            side.append(Box(f"leg_l{k}", [cx - 0.25, 0, z - 4.0], [cx + 0.25, 0.8, z - 3.0], claw))
        piv[f"leg_l{k}"] = [4.4, top, z]
        piv[f"leg_r{k}"] = [-4.4, top, z]
    mirrored = mirror_x(side)
    for b in mirrored:
        b.group = b.group.replace("ear_l", "ear_r")
    return "blind_stalker", B + side + mirrored, 64, piv


def red_bg(w, h):
    g = np.linspace(0, 1, h)[:, None]
    arr = np.zeros((h, w, 4), np.uint8)
    for i, (t0, t1) in enumerate(((22, 120), (16, 58), (20, 40))):
        arr[..., i] = (t0 * (1 - g) + t1 * g).astype(np.uint8)
    arr[..., 3] = 255
    return Image.fromarray(arr, "RGBA")


MOBS = [
    (worm, "Gusano de óxido — vive bajo el polvo y sale donde nota pasos; ~5 bloques", 4.2,
     ((-40, -16), (150, -20), (-20, -6)), (230, 190)),
    (stalker, "Rastreador ciego — caza en manada por el ruido, como el Warden; ~1,8 bloques", 6.2,
     ((-38, -14), (140, -20), (-14, -2)), (220, 220)),
]


def build(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    W, H = 440, 320
    sheet = Image.new("RGBA", (W * 3 + 300, len(MOBS) * (H + 50) + 10), (26, 18, 18, 255))
    d = ImageDraw.Draw(sheet)
    y = 0
    for fn, title, scale, angles, ctr in MOBS:
        name, boxes, tw, piv = fn()
        th = T.pack(boxes, tw)
        tex, glow = paint(boxes, tw, th, seed=zlib.crc32(name.encode()) & 0xffff)
        tex.save(f"{out_dir}/{name}.png")
        if glow.getbbox():
            glow.save(f"{out_dir}/{name}_glow.png")
        json.dump(T.to_mob_json(boxes, piv, tw, th), open(f"{out_dir}/{name}.json", "w"), indent=1)
        json.dump(T.to_bbmodel(name, boxes, tex, tw, th), open(f"{out_dir}/{name}.bbmodel", "w"))
        d.text((14, y + 12), title, fill=(240, 222, 210, 255), font=T.title_font(17))
        for k, (a, p_) in enumerate(angles):
            sc = scale * (1.9 if k == 2 else 1.0)
            c = ctr if k < 2 else ((170, 170) if name == "rust_worm" else (130, 250))
            r = T.render(boxes, tex, a, p_, sc, (W, H), c)
            bg = red_bg(W, H)
            bg.alpha_composite(r)
            sheet.alpha_composite(bg, (k * W, y + 42))
        tv = tex.resize((tex.width * 2, tex.height * 2), Image.NEAREST)
        tv.thumbnail((280, 300), Image.NEAREST)
        sheet.alpha_composite(tv, (W * 3 + 10, y + 46))
        y += H + 50
    sheet.save(f"{out_dir}/ferra_mobs_preview.png")
    print("ok", out_dir)


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else ".")
