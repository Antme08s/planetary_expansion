"""Criaturas de Fulgor (1.0.4, elegidas por el usuario después de las de Titán): arco (bicho eléctrico pequeño de las
llanuras de ceniza), zancudo de fulgurita (pasivo de patas largas de los campos de fulgurita, se sobrecarga con los rayos)
y atrapa-rayos (monstruo alto de las agujas de basalto que se sube a lo más alto y se recarga con los rayos).

Uso: python3 -I tools/designs/fulgor_mobs.py <carpeta_salida>

Mismas herramientas que titan_mobs.py (y su comprobación de caras en el mismo plano, que parpadean en el juego).
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
from thalassa_mobs import Box, mirror_x, seg  # noqa: E402
from cerulea_mobs import paint, mat, mix, spots  # noqa: E402
from titan_mobs import coplanar_check  # noqa: E402

VIOLET = (206, 128, 255)


def to_ground(g, start, d, y_end, th, tw, m, back=None):
    """Segmento desde `start` en la dirección d (normalizada aquí) hasta la altura y_end."""
    d = np.array(d, float)
    d /= np.linalg.norm(d)
    L = (start[1] - y_end) / -d[1]
    return seg(g, start, d.tolist(), L, th, tw, m, back)


def knob(g, p, r, m):
    return Box(g, [v - r for v in p], [v + r for v in p], m)


def arc():
    """Arco (~0,7 bloques de largo): escarabajo-reptil negro con caparazón de tres placas escalonadas, venas violetas que
    brillan (más cuanto más cerca cae un rayo), cresta luminosa, cabeza con mandíbulas, ojos y dos antenas con una chispa
    en la punta, seis patas de dos tramos y una cola curvada hacia arriba con un bulbo cargado."""
    def shell_fn(p, n, c):
        if abs(n[0]) > 0.5 and abs(((p[2] * 0.8 + p[1] * 1.3) % 2.4) - 1.2) < 0.32 and p[1] > 2.0:
            return VIOLET, True                                         # venas en zigzag
        if n[1] > 0.5 and spots(p, 1.6, 0.35, 0.4, 61):
            return mix(c, (70, 64, 92), 0.6), False                    # brillo del caparazón
        return c, False
    shell = mat("shell", (34, 30, 44), (28, 26, 36), 0.06, shell_fn)
    shell_dk = mat("shell_dk", (24, 22, 32), noise=0.05, fn=shell_fn)
    belly = mat("belly", (66, 58, 80), noise=0.05)
    leg = mat("leg", (40, 36, 52), noise=0.06)
    fang = mat("fang", (150, 140, 170), noise=0.04)
    glow = mat("glow", VIOLET, emissive=True, noise=0.0)
    eye = mat("eye", (240, 200, 255), emissive=True, noise=0.0)
    B = [
        Box("body", [-2.6, 1.6, -3.0], [2.6, 4.2, 1.0], shell),          # tórax
        Box("body", [-3.0, 1.5, 0.6], [3.0, 4.8, 5.6], shell),           # abdomen
        Box("body", [-2.2, 1.8, 5.2], [2.2, 4.2, 7.4], shell_dk),        # punta del abdomen
        Box("body", [-2.3, 1.2, -2.5], [2.3, 1.9, 6.5], belly),          # vientre
        Box("body", [-0.5, 4.5, 0.8], [0.5, 5.3, 5.4], glow),            # cresta luminosa
        Box("head", [-1.8, 1.7, -5.4], [1.8, 3.6, -2.6], shell_dk),
    ]
    side = [Box("head", [1.75, 2.6, -4.8], [1.95, 3.2, -4.0], eye)]
    m1, _, _ = seg("head", [1.2, 2.0, -5.2], [-0.4, 0.0, -1.0], 1.8, 0.6, 0.6, fang)
    a1, e, _ = seg("head", [0.8, 3.4, -4.8], [0.35, 0.7, -0.6], 3.2, 0.4, 0.4, leg)
    side += [m1, a1, knob("head", e, 0.32, glow)]
    t1, e, _ = seg("tail", [0, 3.2, 6.8], [0, 0.6, 1.0], 2.6, 1.4, 1.4, shell_dk)
    t2, e, _ = seg("tail", e, [0, 1.0, 0.2], 1.6, 1.0, 1.0, shell_dk)
    B += [t1, t2, knob("tail", e, 0.6, glow)]
    piv = {"body": [0, 3, 0], "head": [0, 2.6, -2.8], "tail": [0, 3.2, 6.8]}
    for k, z in enumerate((-1.6, 1.4, 4.4)):
        dz = (-0.4, 0.0, 0.4)[k]
        g = f"leg_l{k}"
        hip = [2.2, 2.4, z]
        u, e, _ = seg(g, hip, [1.0, 0.5, dz * 0.6], 2.2, 0.7, 0.7, leg)
        l, _, _ = to_ground(g, e, [0.4, -1.0, dz * 0.4], 0.0, 0.55, 0.55, leg)
        side += [u, l]
        piv[g] = hip
        piv[f"leg_r{k}"] = [-hip[0], hip[1], hip[2]]
    return "arc_crawler", B + side + mirror_x(side), 64, piv


def strider():
    """Zancudo de fulgurita (~1,8 bloques de alto): ave-insecto de cristal ahumado con cuerpo facetado, cristales en el
    lomo y plumas de cristal en la cola, cuello largo de dos tramos, cabeza con cresta, ojos y pico largo, y cuatro patas
    muy finas con rodilla y tres dedos. Cuando se sobrecarga con un rayo brillan sus vetas (capa de brillo)."""
    def glass_fn(p, n, c):
        if (int(p[0] * 1.3 + p[1] * 0.7 + p[2] * 1.1) % 4) == 0:
            return mix(c, (236, 226, 250), 0.45), False                 # facetas claras
        if spots(p, 2.4, 0.3, 0.5, 71):
            return (226, 170, 255), True                                # vetas cargadas (brillan)
        return c, False
    glass = mat("glass", (150, 140, 172), (190, 182, 206), 0.05, glass_fn, belly_y=18.4)
    glass_dk = mat("glass_dk", (110, 100, 136), noise=0.05, fn=glass_fn)
    belly = mat("belly", (196, 188, 210), noise=0.04)
    leg = mat("leg", (92, 84, 112), noise=0.05)
    joint = mat("joint", (170, 160, 196), noise=0.04)
    crystal = mat("crystal", (220, 200, 250), noise=0.03)
    beak = mat("beak", (236, 230, 246), noise=0.03)
    eye = mat("eye", (255, 230, 120), emissive=True, noise=0.0)
    B = [
        Box("body", [-2.6, 18.0, -4.0], [2.6, 22.5, 5.0], glass),
        Box("body", [-2.0, 17.4, -3.0], [2.0, 18.6, 4.0], belly),
        Box("body", [-2.9, 19.0, -3.4], [2.9, 21.8, 2.0], glass_dk),      # alas plegadas (más anchas)
    ]
    for (z, h, r) in ((-2.0, 3.0, -20), (0.8, 3.8, -28), (3.4, 2.8, -36)):
        B.append(Box("body", [-0.6, 22.0, z - 0.6], [0.6, 22.0 + h, z + 0.6], crystal, rot=(r, 0, 0), origin=[0, 22.0, z]))
    for (dx, r) in ((-0.9, 30), (0.9, 30), (0.0, 18)):
        B.append(Box("body", [dx - 0.35, 19.6, 4.6], [dx + 0.35, 21.6 + abs(dx) * 0.4, 11.0], crystal, rot=(r, 0, 0),
                     origin=[dx, 20.6, 4.6]))
    # cuello de dos tramos y cabeza
    n1, e, _ = seg("neck", [0, 21.0, -3.4], [0, 0.9, -0.45], 7.0, 1.6, 1.6, glass)
    n2, hp, _ = seg("neck", e, [0, 0.3, -1.0], 2.0, 1.4, 1.4, glass)
    B += [n1, n2]
    hx, hy, hz = hp
    B += [Box("head", [hx - 1.2, hy - 1.0, hz - 2.2], [hx + 1.2, hy + 1.2, hz + 0.8], glass),
          Box("head", [hx - 0.25, hy + 1.0, hz - 1.6], [hx + 0.25, hy + 3.0, hz + 0.2], crystal, rot=(-25, 0, 0),
              origin=[hx, hy + 1.0, hz - 0.7])]
    bk, _, _ = seg("head", [hx, hy - 0.3, hz - 2.0], [0, -0.25, -1.0], 4.2, 0.7, 0.7, beak)
    B.append(bk)
    side = [Box("head", [hx + 1.15, hy + 0.1, hz - 1.4], [hx + 1.35, hy + 0.7, hz - 0.6], eye)]
    piv = {"body": [0, 20, 0], "neck": [0, 21.0, -3.4], "head": [hx, hy, hz]}
    # cuatro patas finas: muslo, rodilla, caña larga y tres dedos
    for k, z in ((0, -1.6), (1, 3.0)):
        g = f"leg_l{k}"
        hip = [2.0, 18.6, z]
        th, knee, _ = seg(g, hip, [0.55, -0.35, -0.1 if k == 0 else 0.1], 4.0, 1.4, 1.4, glass_dk)
        sh, ank, _ = to_ground(g, knee, [0.12, -1.0, 0.02], 0.6, 0.8, 0.8, leg)
        side += [th, sh, knob(g, knee, 0.95, joint), knob(g, ank, 0.6, joint)]
        for ang in (-35, 0, 35):
            a = math.radians(ang)
            t, _, _ = seg(g, ank, [math.sin(a) * 0.9, -0.12, -math.cos(a)], 2.2, 0.4, 0.5, leg, back=0.3)
            side.append(t)
        piv[g] = hip
        piv[f"leg_r{k}"] = [-hip[0], hip[1], hip[2]]
    return "fulgurite_strider", B + side + mirror_x(side), 128, piv


def catcher():
    """Atrapa-rayos (~2,5 bloques con la antena): criatura alta y flaca de basalto con placas de cobre, piernas de zancos
    con la rodilla al revés y pies de tres dedos, brazos largos con garras, cabeza estrecha con un visor que brilla y una
    antena de cobre con anillos y una punta violeta que se carga con los rayos."""
    def basalt_fn(p, n, c):
        if (int(p[1] * 1.1) + int(p[0] * 0.9 + p[2] * 0.6)) % 5 == 0:
            return mix(c, (40, 36, 48), 0.5), False                     # grietas del basalto
        if spots(p, 2.0, 0.25, 0.45, 81):
            return mix(c, (120, 110, 140), 0.4), False
        return c, False
    basalt = mat("basalt", (66, 60, 78), (52, 48, 62), 0.06, basalt_fn)
    basalt_dk = mat("basalt_dk", (48, 44, 58), noise=0.06, fn=basalt_fn)

    def copper_fn(p, n, c):
        if spots(p, 1.8, 0.3, 0.5, 82):
            return (84, 168, 146), False                                # verdín
        if abs(n[2]) > 0.5 and int(p[1] * 2) % 4 == 0:
            return VIOLET, True                                         # líneas de carga en las placas
        return c, False
    copper = mat("copper", (196, 112, 64), noise=0.05, fn=copper_fn)
    copper_plain = mat("copper_plain", (180, 104, 60), noise=0.05, fn=lambda p, n, c: ((84, 168, 146), False) if spots(p, 1.6, 0.25, 0.4, 83) else (c, False))
    claw = mat("claw", (30, 26, 34), noise=0.04)
    glow = mat("glow", VIOLET, emissive=True, noise=0.0)
    visor = mat("visor", (230, 170, 255), emissive=True, noise=0.0)
    R = (-8, 0, 0)
    O = [0, 21.5, 0]
    B = [
        Box("body", [-2.4, 19.0, -1.6], [2.4, 22.0, 1.6], basalt_dk),        # pelvis
        Box("body", [-2.3, 21.5, -1.8], [2.3, 30.0, 1.6], basalt, rot=R, origin=O),      # torso
        Box("body", [-2.6, 25.0, -2.2], [2.6, 29.4, -1.2], copper, rot=R, origin=O),     # peto de cobre
        Box("body", [-1.9, 22.2, -2.1], [1.9, 24.6, -1.4], copper_plain, rot=R, origin=O),
        Box("body", [-3.8, 28.4, -1.4], [3.8, 30.2, 1.4], basalt_dk, rot=R, origin=O),   # hombros
        Box("body", [-1.4, 23.0, 1.2], [1.4, 29.0, 2.1], basalt_dk, rot=R, origin=O),    # espinazo
    ]
    hx, hy, hz = 0.0, 30.8, -1.3
    B += [Box("head", [-1.6, 30.4, -3.2], [1.6, 33.4, 0.4], basalt),
          Box("head", [-1.7, 31.6, -3.45], [1.7, 32.3, -1.2], visor),                    # visor
          Box("head", [-1.2, 29.6, -3.0], [1.2, 30.6, -0.4], basalt_dk),                 # mandíbula
          Box("head", [-0.35, 33.2, -1.6], [0.35, 39.0, -0.9], copper_plain),            # antena
          Box("head", [-0.75, 35.0, -1.65], [0.75, 35.8, -0.85], copper),
          Box("head", [-0.65, 37.0, -1.68], [0.65, 37.6, -0.82], copper),
          Box("head", [-0.6, 38.8, -1.85], [0.6, 40.0, -0.65], glow)]
    piv = {"body": [0, 21, 0], "head": [hx, hy, hz], "arm_l": [3.4, 29.3, -0.6], "arm_r": [-3.4, 29.3, -0.6],
           "leg_l0": [1.6, 20.0, 0], "leg_r0": [-1.6, 20.0, 0]}
    side = []
    # brazo: hombro, brazo, antebrazo y tres garras
    sh = [3.4, 29.3, -0.6]
    ua, el, _ = seg("arm_l", sh, [0.25, -1.0, -0.1], 7.0, 1.3, 1.3, basalt)
    fa, wr, _ = seg("arm_l", el, [0.1, -1.0, -0.5], 7.0, 1.1, 1.1, basalt_dk)
    side += [knob("arm_l", sh, 1.2, copper_plain), ua, knob("arm_l", el, 0.9, copper_plain), fa]
    for dx in (-0.4, 0.0, 0.4):
        c, _, _ = seg("arm_l", wr, [dx, -1.0, -0.6], 2.6, 0.35, 0.35, claw, back=0.3)
        side.append(c)
    # pierna de zancos: muslo hacia delante, rodilla al revés, caña hacia atrás, tarso y pie de tres dedos
    hip = [1.6, 20.0, 0.0]
    t1, kn, _ = seg("leg_l0", hip, [0.15, -0.6, -0.8], 8.0, 2.0, 2.0, basalt)
    t2, an, _ = to_ground("leg_l0", kn, [0.05, -0.75, 0.66], 4.0, 1.5, 1.5, basalt_dk)
    t3, ft, _ = to_ground("leg_l0", an, [0.0, -1.0, -0.25], 0.9, 1.1, 1.1, basalt)
    side += [t1, knob("leg_l0", kn, 1.25, copper_plain), t2, knob("leg_l0", an, 0.9, copper_plain), t3]
    for ang in (-30, 0, 30):
        a = math.radians(ang)
        c, _, _ = seg("leg_l0", ft, [math.sin(a), -0.15, -math.cos(a)], 2.8, 0.6, 0.7, claw, back=0.4)
        side.append(c)
    mirrored = mirror_x(side)
    for b in mirrored:
        b.group = b.group.replace("arm_l", "arm_r")
    return "spire_catcher", B + side + mirrored, 128, piv


def storm_bg(w, h):
    g = np.linspace(0, 1, h)[:, None]
    arr = np.zeros((h, w, 4), np.uint8)
    for i, (t0, t1) in enumerate(((70, 30), (50, 24), (96, 40))):
        arr[..., i] = (t0 * (1 - g) + t1 * g).astype(np.uint8)
    arr[..., 3] = 255
    return Image.fromarray(arr, "RGBA")


MOBS = [
    (arc, "Arco — bicho eléctrico de las llanuras de ceniza; sus venas brillan más cuando caen rayos; ~0,7 bloques", 11.0,
     ((-38, -20), (140, -20), (-30, -8)), (220, 200)),
    (strider, "Zancudo de fulgurita — de cristal, patas muy largas; un rayo cerca lo sobrecarga; ~1,8 bloques", 4.4,
     ((-38, -12), (140, -16), (-14, -2)), (220, 270)),
    (catcher, "Atrapa-rayos — de basalto y cobre; se sube a las agujas y los rayos lo recargan; ~2,5 bloques", 3.4,
     ((-38, -10), (140, -14), (-14, -2)), (220, 290)),
]


def build(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    W, H = 440, 340
    sheet = Image.new("RGBA", (W * 3 + 300, len(MOBS) * (H + 50) + 10), (22, 16, 30, 255))
    d = ImageDraw.Draw(sheet)
    y = 0
    bad = False
    for fn, title, scale, angles, ctr in MOBS:
        name, boxes, tw, piv = fn()
        if coplanar_check(boxes, name):
            print(f"  ¡{name}: hay caras que pueden parpadear!")
            bad = True
        th = T.pack(boxes, tw)
        tex, glow = paint(boxes, tw, th, seed=zlib.crc32(name.encode()) & 0xffff)
        tex.save(f"{out_dir}/{name}.png")
        glow.save(f"{out_dir}/{name}_glow.png")
        json.dump(T.to_mob_json(boxes, piv, tw, th), open(f"{out_dir}/{name}.json", "w"), indent=1)
        json.dump(T.to_bbmodel(name, boxes, tex, tw, th), open(f"{out_dir}/{name}.bbmodel", "w"))
        d.text((14, y + 12), title, fill=(236, 222, 250, 255), font=T.title_font(17))
        for k, (a, p_) in enumerate(angles):
            sc = scale * (1.6 if k == 2 else 1.0)
            c = ctr if k < 2 else (220, ctr[1] + 30 if name != "arc_crawler" else 190)
            r = T.render(boxes, tex, a, p_, sc, (W, H), c)
            bg = storm_bg(W, H)
            bg.alpha_composite(r)
            sheet.alpha_composite(bg, (k * W, y + 42))
        tv = tex.resize((tex.width * 2, tex.height * 2), Image.NEAREST)
        tv.thumbnail((280, 300), Image.NEAREST)
        sheet.alpha_composite(tv, (W * 3 + 10, y + 46))
        y += H + 50
    sheet.save(f"{out_dir}/fulgor_mobs_preview.png")
    print("ok" if not bad else "con avisos", out_dir)


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else ".")
