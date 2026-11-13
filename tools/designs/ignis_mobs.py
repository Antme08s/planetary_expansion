"""Diseños de las criaturas de Ignis (2026-11-12): caracol de magma y caracol titán.

Uso: python3 -I tools/designs/ignis_mobs.py <carpeta_salida>

Usa las mismas herramientas que thalassa_mobs.py (cajas, textura procedural, .bbmodel, modelo JSON del juego y vista
previa). Coordenadas de Blockbench: 16 unidades = 1 bloque, y hacia arriba, el frente mira a -z.
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
from thalassa_mobs import Box, seg  # noqa: E402


def coil_seg(group, a, b, th, tw, mat):
    """Tramo de tubo de `a` a `b` en el plano yz (el ancho va siempre a lo largo de x)."""
    a, b = np.array(a, float), np.array(b, float)
    d = b - a
    L = float(np.linalg.norm(d)); d /= L
    w = np.array([1.0, 0.0, 0.0])
    u = np.cross(w, d)
    R = np.stack([d, u, w], 1)
    back = th * 0.35
    return Box(group, [a[0] - back, a[1] - th / 2, a[2] - tw / 2], [a[0] + L + th * 0.35, a[1] + th / 2, a[2] + tw / 2],
               mat, rot=T.euler_from(R), origin=a.tolist())


def coil(S, s, shell, whorl):
    """Concha en espiral de verdad: un tubo que sale de la abertura (abajo, delante), sube por delante, pasa por
    arriba y por detrás y se enrolla hacia dentro, cada vez más fino y estrecho, alrededor de un disco central más
    estrecho para que la espiral se vea en relieve desde los lados."""
    C = np.array([0.0, 10.0, 2.0])
    out = []
    start, end, step = math.radians(225), math.radians(-275), math.radians(-18)
    k = math.log(6.0 / 1.1) / abs(end - start)
    pts = []
    phi = start
    while phi >= end - 1e-9:
        R = 6.0 * math.exp(-k * abs(phi - start))
        pts.append((C + R * np.array([0.0, math.sin(phi), math.cos(phi)]), R))
        phi += step
    n = len(pts) - 1
    for i, ((p0, r0), (p1, r1)) in enumerate(zip(pts, pts[1:])):
        r = (r0 + r1) / 2
        f = i / max(1, n - 1)   # de la abertura (claro) hacia dentro (oscuro)
        mat = dict(shell, base=tuple(int(a * (1 - f) + b * f) for a, b in zip(shell["light"], shell["base"])))
        th = max(1.2, 0.78 * r)
        tw = 9.4 * (r / 6.0) ** 0.28
        out.append(coil_seg("shell", S(p0.tolist()), S(p1.tolist()), th * s, tw * s, mat))
    for h, half in ((3.4, 2.2), (2.2, 2.7), (1.2, 3.0)):   # disco central (cruz de dos cajas)
        out.append(Box("shell", S([-half, C[1] - h, C[2] - h * 0.9]), S([half, C[1] + h, C[2] + h * 0.9]), whorl))
    return out


def shell_surface(shell, whorl):
    """Devuelve f(x, z) = altura (sin escalar) de la cara de arriba de la concha en espiral, buscando de arriba abajo
    el primer punto que cae dentro de alguna de sus cajas."""
    boxes = coil(lambda v: v, 1.0, shell, whorl)
    mats = [(T.rot_matrix(b.rot), np.array(b.origin, float), np.array(b.frm), np.array(b.to)) for b in boxes]

    def inside(p):
        for R, o, lo, hi in mats:
            q = R.T @ (p - o) + o
            if np.all(q >= lo - 1e-6) and np.all(q <= hi + 1e-6):
                return True
        return False

    def f(x, z):
        y = 22.0
        while y > 0 and not inside(np.array([x, y, z])):
            y -= 0.05
        return y
    return f


def snail(titan=False):
    """Caracol de magma (~1 bloque de largo) o caracol titán (~2,6 veces más grande, raro).

    Concha de obsidiana en espiral con grietas naranjas que brillan; cuerpo de roca fundida con costra oscura y una
    suela incandescente; cuello alzado, dos antenas largas con la punta encendida y dos tentáculos cortos.
    El titán tiene la costra más gruesa, la concha cubierta de racimos de oro y piedra negra dorada, y una boca que
    brilla (por ahí escupe las bolas de magma)."""
    s = 2.6 if titan else 1.0

    def S(v):
        return [x * s for x in v]

    def box(g, a, b, m, **kw):
        if "origin" in kw:
            kw["origin"] = S(kw["origin"])
        return Box(g, S(a), S(b), m, **kw)

    crust = dict(name="body", base=(92, 44, 26), belly=(190, 74, 22), pattern="speckle", density=0.35, noise=0.1,
                 cracks=(255, 196, 72))
    sole = dict(name="sole", base=(255, 138, 36), noise=0.08, emissive=True)
    shell = dict(name="shell", base=(30, 20, 36), light=(92, 70, 108), pattern="plates", noise=0.08, cracks=(255, 120, 30))
    # el centro de la espiral: roca al rojo vivo que se ve entre las vueltas
    whorl = dict(name="whorl", base=(168, 58, 20), pattern="speckle", density=0.35, noise=0.1, cracks=(255, 205, 90))
    rim = dict(name="rim", base=(150, 64, 24), pattern="speckle", density=0.3, noise=0.08, cracks=(255, 210, 90))
    stalk = dict(name="stalk", base=(120, 54, 26), pattern="speckle", density=0.2, noise=0.08)
    tip = dict(name="tip", base=(255, 210, 90), noise=0.04, emissive=True)
    if titan:
        crust = dict(crust, base=(70, 36, 24), density=0.45)
        shell = dict(shell, base=(26, 18, 30), light=(80, 62, 92))

    B = [
        # pie (la "babosa"): ancho y bajo, se afina hacia la cola
        box("body", [-4, 0.6, -9], [4, 4, 9], crust),
        box("body", [-4.2, 0, -8.5], [4.2, 0.8, 8.5], sole),
        box("tail", [-3, 0.4, 9], [3, 2.8, 13], crust),
        box("tail", [-2, 0.2, 13], [2, 1.6, 16], crust),
        box("tail", [-3.2, 0, 9], [3.2, 0.6, 15.5], sole),
        # cuello alzado
        box("body", [-3, 1, -12], [3, 7, -6], crust),
        box("body", [-3.2, 0, -11.5], [3.2, 0.8, -6], sole),
        # cabeza
        box("head", [-3.4, 5, -14], [3.4, 9.5, -8.5], crust, rot=(-8, 0, 0), origin=[0, 7, -8.5]),
        box("head", [-2.6, 4.2, -14.6], [2.6, 6, -12.5], rim, rot=(-8, 0, 0), origin=[0, 7, -8.5]),   # morro
        # labio de la abertura de la concha
        box("shell", [-3.5, 3.5, -4.5], [3.5, 5, 1], rim),
    ]
    B += coil(S, s, shell, whorl)
    side = []
    # antenas (ojos) largas con punta encendida, y tentáculos cortos
    for sx in (-1, 1):
        a, e, _ = seg("head", S([1.6 * sx, 9, -11.5]), [0.35 * sx, 1.0, -0.55], 5.5 * s, 0.9 * s, 0.9 * s, stalk)
        B.append(a)
        B.append(Box("head", [x - 0.8 * s for x in e], [x + 0.8 * s for x in e], tip))
        a, e, _ = seg("head", S([2.2 * sx, 6, -13.8]), [0.45 * sx, -0.3, -1.0], 2.6 * s, 0.8 * s, 0.8 * s, stalk)
        B.append(a)
    if titan:
        mouth = dict(name="mouth", base=(255, 176, 60), noise=0.05, emissive=True)
        B.append(box("head", [-1.6, 4.6, -14.9], [1.6, 5.4, -14.4], mouth, rot=(-8, 0, 0), origin=[0, 7, -8.5]))
        # placas de costra en el lomo del pie
        for z in (-7, -2, 3):
            B.append(box("body", [-3.4, 4, z], [3.4, 4.8, z + 3], rim))
        # racimos de oro y piedra negra dorada en la concha
        gold = dict(name="gold", base=(250, 202, 70), noise=0.06, pattern="plates")
        gilded = dict(name="gilded", base=(52, 40, 40), pattern="speckle", density=0.35, noise=0.06, cracks=(250, 200, 70))
        rnd = random.Random(11)
        # 1.0.1: cada racimo se apoya en la superficie real de la concha (antes flotaban por encima)
        surf = shell_surface(shell, whorl)
        for (x, z) in [(-1.6, 0.8), (1.9, 3.2), (0.2, 4.3), (-2.2, -2.0), (2.3, -1.6), (0.4, -0.4)]:
            y = surf(x, z)
            g = (surf(x, z + 0.7) - surf(x, z - 0.7)) / 1.4      # pendiente de la concha a lo largo de z
            tilt = -math.degrees(math.atan(g))                    # inclina el racimo para que salga perpendicular
            B.append(box("shell", [x - 1.4, y - 0.9, z - 1.4], [x + 1.4, y + 0.35, z + 1.4], gilded,
                         rot=(tilt, 0, 0), origin=[x, y, z]))
            for k in range(3):
                h = rnd.uniform(1.6, 3.2)
                w = rnd.uniform(0.7, 1.1)
                ox, oz = rnd.uniform(-0.8, 0.8), rnd.uniform(-0.8, 0.8)
                yb = y - 0.6   # la base del cristal queda hundida en la piedra dorada
                B.append(box("shell", [x + ox - w / 2, yb, z + oz - w / 2], [x + ox + w / 2, yb + h, z + oz + w / 2],
                             gold, rot=(tilt + rnd.uniform(-20, 20), rnd.uniform(0, 45), rnd.uniform(-20, 20)),
                             origin=[x + ox, yb, z + oz]))
    pivots = {"body": S([0, 3, 0]), "tail": S([0, 1.5, 9]), "head": S([0, 6, -8.5]), "shell": S([0, 4, 1])}
    name = "magma_titan" if titan else "magma_snail"
    return name, B + side + T.mirror_x(side), (256 if titan else 64), pivots


def build(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    mobs = [
        (lambda: snail(False), "Caracol de magma (magma_snail) — ~1 bloque; pasivo, se esconde en la concha", 11),
        (lambda: snail(True), "Caracol titán (magma_titan) — ~2,6 bloques; raro; escupe bolas de magma si lo atacas", 4.6),
    ]
    panels = []
    for fn, title, scale in mobs:
        name, boxes, tex_w, pivots = fn()
        tex_h = T.pack(boxes, tex_w)
        tex, glow = T.paint(boxes, tex_w, tex_h, seed=zlib.crc32(name.encode()) & 0xffff)
        tex.save(f"{out_dir}/{name}.png")
        glow.save(f"{out_dir}/{name}_glow.png")
        json.dump(T.to_mob_json(boxes, pivots, tex_w, tex_h), open(f"{out_dir}/{name}.json", "w"), indent=1)
        json.dump(T.to_bbmodel(name, boxes, tex, tex_w, tex_h), open(f"{out_dir}/{name}.bbmodel", "w"))
        views = [T.render(boxes, tex, a, p, scale, (440, 330), (220, 230)) for a, p in ((-35, -20), (140, -22), (90, -4))]
        panels.append((title, views, tex))
    W, H = 440 * 3 + 290, 380 * len(panels)
    sheet = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    d = ImageDraw.Draw(sheet)
    for n, (title, views, tex) in enumerate(panels):
        y = n * 380
        arr = np.zeros((380, W, 4), np.uint8)
        g = np.linspace(0, 1, 380)[:, None]
        arr[..., 0] = (34 + 50 * g).astype(np.uint8)
        arr[..., 1] = (20 + 14 * g).astype(np.uint8)
        arr[..., 2] = (18 + 6 * g).astype(np.uint8)
        arr[..., 3] = 255
        sheet.alpha_composite(Image.fromarray(arr, "RGBA"), (0, y))
        d.text((14, y + 10), title, fill=(255, 225, 200, 255), font=T.title_font(18))
        for k, v in enumerate(views):
            sheet.alpha_composite(v, (k * 440, y + 40))
        tv = tex.copy()
        tv = tv.resize((tv.width * max(1, 256 // tv.width), tv.height * max(1, 256 // tv.width)), Image.NEAREST)
        tv.thumbnail((270, 320), Image.NEAREST)
        sheet.alpha_composite(Image.new("RGBA", (tv.width + 8, tv.height + 8), (60, 40, 36, 255)), (1326, y + 46))
        sheet.alpha_composite(tv, (1330, y + 50))
    sheet.save(f"{out_dir}/ignis_mobs_preview.png")
    print("ok", out_dir)


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else ".")
