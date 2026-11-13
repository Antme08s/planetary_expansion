"""Diseños de las criaturas de Thalassa (2026-11-11): pez farol, cangrejo de chimenea, cangrejo rey azul y acechador abisal.

Uso: python3 -I tools/designs/thalassa_mobs.py <carpeta_salida>

Para cada criatura:
  - genera la textura (pixel art procedural, UV de caja empaquetado automáticamente);
  - exporta un .bbmodel (Blockbench, "Modded Entity", UV de caja, textura incrustada) para abrirlo y retocarlo;
  - dibuja una vista previa con textura desde dos ángulos (render por software).
Coordenadas de Blockbench: 16 unidades = 1 bloque, y hacia arriba, el frente mira a -z.
"""
import base64
import io
import json
import math
import random
import sys
import uuid
import zlib

import numpy as np
from PIL import Image, ImageDraw, ImageFont

# ------------------------------------------------------------------ modelo


class Box:
    def __init__(self, group, frm, to, mat, rot=(0, 0, 0), origin=None, mirror=False):
        self.group, self.frm, self.to, self.mat = group, list(map(float, frm)), list(map(float, to)), mat
        self.rot = rot
        self.origin = origin if origin is not None else [(frm[i] + to[i]) / 2 for i in range(3)]
        self.mirror = mirror
        self.uv = (0, 0)

    @property
    def size(self):
        return [self.to[i] - self.frm[i] for i in range(3)]

    def uv_size(self):
        w, h, d = self.size
        return math.ceil(2 * d + 2 * w), math.ceil(d + h)


def mirror_x(boxes):
    """Copia simétrica (x → −x) de una lista de cajas."""
    out = []
    for b in boxes:
        frm = [-b.to[0], b.frm[1], b.frm[2]]
        to = [-b.frm[0], b.to[1], b.to[2]]
        rot = (b.rot[0], -b.rot[1], -b.rot[2])
        origin = [-b.origin[0], b.origin[1], b.origin[2]]
        # las patas tienen un grupo cada una (leg_l0, leg_l1…): la copia simétrica es la del otro lado
        out.append(Box(b.group.replace("leg_l", "leg_r"), frm, to, b.mat, rot, origin))
    return out


def pack(boxes, tex_w):
    """Empaqueta los UV de caja en filas (de mayor a menor alto)."""
    order = sorted(boxes, key=lambda b: -b.uv_size()[1])
    x = y = row_h = 0
    for b in order:
        w, h = b.uv_size()
        if x + w > tex_w:
            x, y, row_h = 0, y + row_h, 0
        b.uv = (x, y)
        x += w
        row_h = max(row_h, h)
    need = y + row_h
    tex_h = 16
    while tex_h < need:
        tex_h *= 2
    return tex_h


def face_rects(b):
    """UV de caja con las medidas reales (decimales incluidos), igual que ModelPart.Cube de Minecraft."""
    u, v = b.uv
    w, h, d = b.size
    return {
        "up": (u + d, v, w, d), "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h),
    }

# ------------------------------------------------------------------ textura


def clamp(c):
    return tuple(int(max(0, min(255, x))) for x in c)


def mul(c, k):
    return clamp([x * k for x in c[:3]])


def paint(boxes, tex_w, tex_h, seed):
    """Devuelve (textura, textura_de_brillo). La de brillo solo tiene los píxeles que brillan en la oscuridad."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
    glow = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
    px, gx = img.load(), glow.load()

    def put(x, y, c, lit=False):
        if 0 <= x < tex_w and 0 <= y < tex_h:
            px[x, y] = clamp(c) + (255,)
            if lit:
                gx[x, y] = clamp(c) + (255,)

    for b in boxes:
        m = b.mat
        for face, (fu, fv, fwf, fhf) in face_rects(b).items():
            u0, v0 = int(math.floor(fu)), int(math.floor(fv))
            fw = max(1, int(math.ceil(fu + fwf)) - u0)
            fh = max(1, int(math.ceil(fv + fhf)) - v0)
            for j in range(fh):
                for i in range(fw):
                    t = j / max(1, fh - 1)  # 0 arriba, 1 abajo en las caras laterales
                    c = list(m["base"])
                    if face in ("north", "south", "east", "west") and m.get("belly"):
                        k = max(0.0, (t - 0.55) / 0.45)  # vientre más claro hacia abajo
                        c = [c[q] * (1 - k) + m["belly"][q] * k for q in range(3)]
                    if face == "down" and m.get("belly"):
                        c = list(m["belly"])
                    if face == "up":
                        c = [x * 1.06 for x in c]
                    if face in ("north", "south", "east", "west"):
                        c = [x * (1.06 - 0.12 * t) for x in c]
                    n = 1 + rnd.uniform(-m.get("noise", 0.05), m.get("noise", 0.05))
                    c = [x * n for x in c]
                    pat = m.get("pattern")
                    if pat == "speckle" and rnd.random() < m.get("density", 0.12):
                        c = [x * 0.78 for x in c]
                    elif pat == "blotch":
                        if (math.sin((u0 + i) * 0.9 + (v0 + j) * 0.5) + math.sin((u0 + i) * 0.37 - (v0 + j) * 1.1)) > 1.2:
                            c = [x * 0.82 for x in c]
                    elif pat == "plates":
                        if i % 4 == 0 or j % 3 == 0:
                            c = [x * 0.75 for x in c]
                    elif pat == "scales":
                        if (i + (j // 2) % 2) % 3 == 0 and j % 2 == 0:
                            c = [x * 0.86 for x in c]
                    put(u0 + i, v0 + j, c, m.get("emissive", False))
            if m.get("glowdots") and face in ("east", "west") and fw >= 3 and fh >= 3:
                row = v0 + int(fh * m.get("dot_row", 0.55))
                for i in range(1, fw - 1, m.get("dot_step", 3)):
                    put(u0 + i, row, m["glowdots"], True)
            if m.get("cracks") and face in ("up", "north", "south", "east", "west") and fw >= 4:
                for _ in range(max(1, fw * fh // 40)):
                    x, y = rnd.randrange(fw), rnd.randrange(fh)
                    for _s in range(rnd.randint(2, 5)):
                        if 0 <= x < fw and 0 <= y < fh:
                            put(u0 + x, v0 + y, m["cracks"], True)
                        x += rnd.choice((-1, 0, 1))
                        y += rnd.choice((0, 1))
            if m.get("gums") and face in ("north", "east", "west") and fh >= 2:
                for i in range(fw):
                    put(u0 + i, v0 + fh - 1, m["gums"])
    return img, glow


def to_mob_json(boxes, pivots, tex_w, tex_h):
    """Modelo para el juego (formato propio, lo lee client/mobs/JsonMobModel): partes por grupo con su pivote, y
    dentro nodos con cubos (los cubos girados van en su propio nodo, porque Minecraft solo gira partes).
    Coordenadas de Minecraft: x al revés que en Blockbench, y hacia abajo desde 24."""
    def mc(v):
        return [-v[0], 24.0 - v[1], v[2]]

    parts = {}
    for b in boxes:
        piv = mc(pivots.get(b.group, [0, 0, 0]))
        part = parts.setdefault(b.group, {"pivot": piv, "nodes": [{"offset": [0, 0, 0], "rotation": [0, 0, 0], "boxes": []}]})
        corner = [-b.to[0], 24.0 - b.to[1], b.frm[2]]
        box = {"uv": list(b.uv), "size": [round(x, 4) for x in b.size], "mirror": False}
        if any(abs(r) > 1e-6 for r in b.rot):
            o = mc(b.origin)
            box["min"] = [round(corner[i] - o[i], 4) for i in range(3)]
            part["nodes"].append({"offset": [round(o[i] - piv[i], 4) for i in range(3)],
                                  "rotation": [math.radians(-b.rot[0]), math.radians(-b.rot[1]), math.radians(b.rot[2])],
                                  "boxes": [box]})
        else:
            box["min"] = [round(corner[i] - piv[i], 4) for i in range(3)]
            part["nodes"][0]["boxes"].append(box)
    return {"texture": [tex_w, tex_h], "parts": parts}


# ------------------------------------------------------------------ render


def rot_matrix(r):
    rx, ry, rz = (math.radians(a) for a in r)
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


SHADE = {"up": 1.0, "down": 0.55, "north": 0.82, "south": 0.82, "east": 0.68, "west": 0.68}
NORMAL = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}


def faces3d(b):
    x0, y0, z0 = b.frm
    x1, y1, z1 = b.to
    w, h, d = x1 - x0, y1 - y0, z1 - z0
    X, Y, Z = np.eye(3)
    return {
        "north": ((x1, y1, z0), -X * w, -Y * h),
        "south": ((x0, y1, z1), X * w, -Y * h),
        "east": ((x1, y1, z1), -Z * d, -Y * h),
        "west": ((x0, y1, z0), Z * d, -Y * h),
        "up": ((x1, y1, z0), -X * w, Z * d),
        "down": ((x1, y0, z1), -X * w, -Z * d),
    }


def render(boxes, tex, yaw, pitch, scale, size, center, glow_names=()):
    T = np.asarray(tex.convert("RGBA")).astype(float)
    th, tw = T.shape[:2]
    W, H = size
    color = np.zeros((H, W, 4))
    zbuf = np.full((H, W), np.inf)
    cy_, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    view = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy_, 0, sy_], [0, 1, 0], [-sy_, 0, cy_]])
    light = np.array([-0.4, 0.8, -0.45]); light /= np.linalg.norm(light)
    for b in boxes:
        R = rot_matrix(b.rot)
        org = np.array(b.origin, float)
        rects = face_rects(b)
        for name, (o, a, bb) in faces3d(b).items():
            O = R @ (np.array(o, float) - org) + org
            A, B = R @ a, R @ bb
            n = view @ (R @ np.array(NORMAL[name], float))
            if n[2] >= -1e-6:
                continue
            q = view @ O
            so = np.array([center[0] + q[0] * scale, center[1] - q[1] * scale])
            sa = np.array([(view @ A)[0], -(view @ A)[1]]) * scale
            sb = np.array([(view @ B)[0], -(view @ B)[1]]) * scale
            za, zb = (view @ A)[2], (view @ B)[2]
            cs = np.array([so, so + sa, so + sb, so + sa + sb])
            xmin, ymin = np.floor(cs.min(0)).astype(int)
            xmax, ymax = np.ceil(cs.max(0)).astype(int)
            xmin, ymin, xmax, ymax = max(xmin, 0), max(ymin, 0), min(xmax, W), min(ymax, H)
            if xmax <= xmin or ymax <= ymin:
                continue
            M = np.array([[sa[0], sb[0]], [sa[1], sb[1]]])
            if abs(np.linalg.det(M)) < 1e-9:
                continue
            Minv = np.linalg.inv(M)
            ys, xs = np.mgrid[ymin:ymax, xmin:xmax]
            st = np.stack([xs + 0.5 - so[0], ys + 0.5 - so[1]], -1) @ Minv.T
            s, t = st[..., 0], st[..., 1]
            inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
            ru, rv, rw, rh = rects[name]
            tu = np.clip(np.floor(ru + s * rw).astype(int), 0, tw - 1)
            tv = np.clip(np.floor(rv + t * rh).astype(int), 0, th - 1)
            texel = T[tv, tu]
            inside &= texel[..., 3] > 127
            depth = q[2] + s * za + t * zb
            yy, xx, dd = ys[inside], xs[inside], depth[inside]
            closer = dd < zbuf[yy, xx]
            yy, xx, dd = yy[closer], xx[closer], dd[closer]
            zbuf[yy, xx] = dd
            c = texel[inside][closer].copy()
            if not b.mat.get("emissive"):
                lam = 0.55 + 0.45 * max(0.0, float(-(view @ (R @ np.array(NORMAL[name], float))) @ (view @ light) * -1))
                c[:, :3] *= SHADE[name] * 0.6 + lam * 0.4
            color[yy, xx] = c
    return Image.fromarray(color.clip(0, 255).astype(np.uint8), "RGBA")

# ------------------------------------------------------------------ bbmodel


def to_bbmodel(name, boxes, tex, tex_w, tex_h):
    groups = {}
    elements = []
    for b in boxes:
        e = {
            "name": b.mat.get("name", "cube"), "box_uv": True, "rescale": False, "locked": False,
            "render_order": "default", "allow_mirror_modeling": True,
            "from": b.frm, "to": b.to, "autouv": 0, "color": 0,
            "origin": b.origin, "uv_offset": list(b.uv), "type": "cube", "uuid": str(uuid.uuid4()),
            "faces": {f: {"uv": [0, 0, 0, 0], "texture": 0} for f in ("north", "east", "south", "west", "up", "down")},
        }
        if any(abs(r) > 1e-6 for r in b.rot):
            e["rotation"] = list(b.rot)
        # caras con el UV real (Blockbench lo recalcula con box_uv, pero así abre bien)
        for f, (u, v, w, h) in face_rects(b).items():
            e["faces"][f]["uv"] = [u, v, u + w, v + h]
        elements.append(e)
        groups.setdefault(b.group, []).append(e["uuid"])
    buf = io.BytesIO()
    tex.save(buf, "PNG")
    outliner = []
    for g, kids in groups.items():
        outliner.append({"name": g, "origin": [0, 0, 0], "color": 0, "uuid": str(uuid.uuid4()), "export": True,
                         "isOpen": True, "locked": False, "visibility": True, "autouv": 0, "children": kids})
    return {
        "meta": {"format_version": "4.5", "model_format": "modded_entity", "box_uv": True},
        "name": name, "model_identifier": name, "modded_entity_version": "1.17",
        "visible_box": [4, 4, 0], "variable_placeholders": "", "variable_placeholder_buttons": [],
        "timeline_setups": [], "unhandled_root_fields": {},
        "resolution": {"width": tex_w, "height": tex_h},
        "elements": elements, "outliner": outliner,
        "textures": [{"path": "", "name": f"{name}.png", "folder": "", "namespace": "", "id": "0", "particle": False,
                      "render_mode": "default", "visible": True, "mode": "bitmap", "saved": False,
                      "uuid": str(uuid.uuid4()), "relative_path": "", "width": tex_w, "height": tex_h,
                      "uv_width": tex_w, "uv_height": tex_h,
                      "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()}],
    }

# ------------------------------------------------------------------ criaturas


def lantern_fish():
    """Pez farol v2 (más detallado): cuerpo que se afina en tramos hasta una cola en horquilla, mandíbula inferior
    salida con colmillos, ojo grande plateado, línea de las agallas, aleta dorsal con radios, aletas finas y dos filas
    de fotóforos turquesa en el vientre. La antena sale de la frente, se curva hacia delante y cuelga el señuelo
    luminoso delante de la boca."""
    back = dict(name="body", base=(24, 36, 62), belly=(172, 186, 196), pattern="scales", noise=0.06,
                glowdots=(110, 255, 230), dot_row=0.78, dot_step=2)
    back2 = dict(name="body", base=(24, 36, 62), belly=(172, 186, 196), pattern="scales", noise=0.06,
                 glowdots=(110, 255, 230), dot_row=0.9, dot_step=2)
    head = dict(name="head", base=(20, 30, 54), belly=(160, 176, 188), pattern="speckle", density=0.1, noise=0.05)
    fin = dict(name="fin", base=(58, 84, 120), pattern="speckle", density=0.25, noise=0.1)
    ray = dict(name="fin_ray", base=(110, 140, 170), noise=0.05)
    jaw = dict(name="jaw", base=(30, 40, 62), belly=(150, 160, 172), noise=0.05, gums=(160, 60, 72))
    mouth = dict(name="mouth", base=(12, 14, 22), noise=0.03)
    tooth = dict(name="tooth", base=(232, 236, 228), noise=0.03)
    gill = dict(name="gill", base=(14, 20, 36), noise=0.03)
    ring = dict(name="eye", base=(196, 206, 214), noise=0.04)
    pupil = dict(name="pupil", base=(6, 8, 12), noise=0.0)
    glint = dict(name="glint", base=(255, 255, 255), noise=0.0, emissive=True)
    stalk = dict(name="stalk", base=(40, 58, 90), noise=0.05)
    glow = dict(name="lure", base=(150, 255, 235), noise=0.04, emissive=True)
    core = dict(name="lure_core", base=(235, 255, 250), noise=0.0, emissive=True)
    B = [
        Box("head", [-2.5, 1, -8], [2.5, 7, -3], head),
        Box("head", [-2.2, 5.5, -9], [2.2, 7, -7.5], head),                    # frente
        Box("head", [-1.9, 1.3, -9], [1.9, 2.4, -7.6], mouth),                 # boca
        Box("jaw", [-2.3, 0.0, -9.6], [2.3, 1.6, -4.5], jaw, rot=(10, 0, 0), origin=[0, 1.6, -4.5]),
        Box("body", [-2.25, 1.2, -3], [2.25, 6.8, 1], back),
        Box("body", [-1.75, 1.8, 1], [1.75, 6.2, 4], back2),
        Box("tail", [-1.1, 2.6, 4], [1.1, 5.4, 7], back2),
        Box("tail", [-0.6, 3.2, 7], [0.6, 4.8, 9], back2),
        Box("tail", [-0.25, 4.2, 8], [0.25, 8.6, 11.5], fin, rot=(-35, 0, 0), origin=[0, 4.2, 8.5]),   # cola en horquilla
        Box("tail", [-0.25, -0.6, 8], [0.25, 3.8, 11.5], fin, rot=(35, 0, 0), origin=[0, 3.8, 8.5]),
        Box("body", [-0.25, 6.6, -2.5], [0.25, 8.6, 3], fin, rot=(-8, 0, 0), origin=[0, 6.6, 0]),      # aleta dorsal
        Box("body", [-0.3, 6.6, -2.2], [0.3, 9.6, -1.6], ray, rot=(-20, 0, 0), origin=[0, 6.6, -1.9]),
        Box("body", [-0.3, 6.6, -0.4], [0.3, 9.2, 0.2], ray, rot=(-25, 0, 0), origin=[0, 6.6, -0.1]),
        Box("body", [-0.3, 6.4, 1.4], [0.3, 8.6, 2.0], ray, rot=(-30, 0, 0), origin=[0, 6.4, 1.7]),
        Box("body", [-0.25, 0.2, 1.5], [0.25, 1.8, 5], fin, rot=(15, 0, 0), origin=[0, 1.8, 2]),       # aleta anal
        # antena curvada y señuelo
        Box("lure", [-0.3, 7, -6.6], [0.3, 10.6, -6.0], stalk, rot=(-12, 0, 0), origin=[0, 7, -6.3]),
        Box("lure", [-0.3, 10.0, -10.6], [0.3, 10.6, -6.2], stalk),
        Box("lure", [-0.3, 8.6, -11.0], [0.3, 10.6, -10.4], stalk),
        Box("lure", [-1.0, 6.6, -11.7], [1.0, 8.8, -9.7], glow),
        Box("lure", [-0.5, 7.1, -11.95], [0.5, 8.3, -10.95], core),
        # colmillos
        Box("head", [-1.6, 0.9, -8.9], [-1.0, 2.3, -8.3], tooth), Box("head", [1.0, 0.9, -8.9], [1.6, 2.3, -8.3], tooth),
        Box("head", [-0.3, 1.2, -9.0], [0.3, 2.3, -8.4], tooth),
        Box("jaw", [-1.9, 1.2, -9.4], [-1.3, 2.8, -8.8], tooth, rot=(10, 0, 0), origin=[0, 1.6, -4.5]),
        Box("jaw", [1.3, 1.2, -9.4], [1.9, 2.8, -8.8], tooth, rot=(10, 0, 0), origin=[0, 1.6, -4.5]),
    ]
    side = [
        Box("head", [2.45, 3.6, -7.4], [2.85, 6.4, -4.6], ring),                                      # ojo grande
        Box("head", [2.6, 4.2, -6.8], [3.0, 5.8, -5.2], pupil),
        Box("head", [2.8, 5.0, -6.6], [3.1, 5.5, -6.1], glint),
        Box("head", [2.4, 1.6, -3.7], [2.65, 6.4, -3.25], gill),                                      # agallas
        Box("body", [2.1, 2.4, -2.6], [2.4, 4.6, 1.2], fin, rot=(0, 30, 25), origin=[2.2, 3.5, -2.4]), # pectoral
        Box("body", [1.6, 0.6, 0.5], [1.9, 2.0, 3.0], fin, rot=(0, 15, -25), origin=[1.7, 1.8, 0.8]), # pélvica
    ]
    piv = {"body": [0, 4, 0], "head": [0, 4, -3], "jaw": [0, 1.6, -4.5], "tail": [0, 4, 4], "lure": [0, 7, -6]}
    return "lantern_fish", B + side + mirror_x(side), 64, piv


def vent_crab():
    """Cangrejo de chimenea (~1,3 bloques de ancho): caparazón de basalto con grietas naranjas que brillan por el calor
    de la chimenea, pinzas grandes (la derecha más grande), ojos en pedúnculos y seis patas."""
    shell = dict(name="shell", base=(58, 56, 60), pattern="plates", noise=0.07, cracks=(255, 140, 50))
    rock = dict(name="rock", base=(44, 42, 46), pattern="speckle", density=0.25, noise=0.1, cracks=(255, 120, 40))
    belly = dict(name="belly", base=(150, 112, 90), pattern="plates", noise=0.05)
    limb = dict(name="leg", base=(92, 64, 56), pattern="speckle", density=0.15, noise=0.06)
    claw = dict(name="claw", base=(70, 60, 62), pattern="plates", noise=0.07, cracks=(255, 140, 50))
    tip = dict(name="claw_tip", base=(30, 26, 28), noise=0.05)
    eye = dict(name="eye", base=(255, 170, 60), noise=0.02, emissive=True)
    stalk = dict(name="stalk", base=(92, 64, 56), noise=0.05)
    B = [
        Box("body", [-8, 5, -6], [8, 11, 7], shell),
        Box("body", [-7, 3.5, -5], [7, 5, 6], belly),
        Box("body", [-5, 11, -3], [-1, 14, 1], rock, rot=(0, 15, 8)),      # rocas sobre el caparazón
        Box("body", [1, 11, -1], [6, 13, 4], rock, rot=(0, -10, -6)),
        Box("body", [-2, 11, 2], [1, 15, 5], rock, rot=(10, 0, 0)),
        Box("body", [-8.5, 8, -6.5], [8.5, 9, -5.5], shell),               # borde del caparazón
    ]
    # pinzas unidas al frente del caparazón: la derecha (−x) grande, la izquierda más pequeña
    B += crab_claw("claw_r", [-6.5, 7, -5], -1, 1.0, limb, claw, tip)
    B += crab_claw("claw_l", [6.5, 7, -5], 1, 0.75, limb, claw, tip)
    side = [
        Box("eyes", [3, 10, -7], [4, 14, -6], stalk, rot=(-15, 0, 0), origin=[3.5, 10, -6.5]),
        Box("eyes", [2.6, 13.5, -8.4], [4.4, 15.3, -6.6], eye),
    ]
    legs = []
    for k, z in enumerate((-2, 1.5, 5)):
        legs += [
            Box(f"leg_l{k}", [8, 7, z], [15, 9, z + 2], limb, rot=(0, 0, -25 + k * 5), origin=[8, 8, z + 1]),
            Box(f"leg_l{k}", [13, 0, z + 0.2], [15, 7.5, z + 1.8], limb, rot=(0, 0, 12), origin=[14, 7, z + 1]),
        ]
    side += legs
    piv = {"body": [0, 6, 0], "claw_r": [-6.5, 7, -5], "claw_l": [6.5, 7, -5], "eyes": [0, 10, -6.5]}
    for k, z in enumerate((-2, 1.5, 5)):   # cada pata gira desde la cadera
        piv[f"leg_l{k}"] = [8, 8, z + 1]
        piv[f"leg_r{k}"] = [-8, 8, z + 1]
    return "vent_crab", B + side + mirror_x(side), 128, piv


def euler_from(R):
    """Ángulos (x, y, z) en grados tales que rot_matrix(...) == R (orden Rz·Ry·Rx)."""
    b = math.asin(max(-1.0, min(1.0, -R[2, 0])))
    a = math.atan2(R[2, 1], R[2, 2])
    c = math.atan2(R[1, 0], R[0, 0])
    return (math.degrees(a), math.degrees(b), math.degrees(c))


def seg(group, start, d, length, th, tw, mat, back=None):
    """Segmento orientado en cualquier dirección `d` (con su "arriba" lo más cerca posible de +y), de `th` de alto y
    `tw` de ancho, que empieza un poco por detrás de `start` para que no queden huecos en las articulaciones.
    Devuelve (caja, punto final, ejes (d, arriba, lado))."""
    d = np.array(d, float); d /= np.linalg.norm(d)
    w = np.cross(d, [0.0, 1.0, 0.0]); w /= np.linalg.norm(w)
    u = np.cross(w, d)
    R = np.stack([d, u, w], 1)
    back = min(th, tw) / 2 if back is None else back
    sx, sy, sz = start
    b = Box(group, [sx - back, sy - th / 2, sz - tw / 2], [sx + length, sy + th / 2, sz + tw / 2], mat,
            rot=euler_from(R), origin=[sx, sy, sz])
    return b, (np.array(start, float) + d * length).tolist(), (d, u, w)


def crab_claw(group, shoulder, side, k, arm, hand, tip, joint=None):
    """Pinza de cangrejo de verdad, con las piezas unidas: brazo (mero) hacia afuera y adelante, antebrazo (carpo)
    hacia adelante, mano (propodio) apuntando hacia adelante y un poco hacia el centro, y los dos dedos: el fijo
    abajo y el móvil arriba, un poco abierto. `side` = −1 para la derecha (−x), +1 para la izquierda; `k` escala."""
    B = []
    a, e, _ = seg(group, shoulder, [0.72 * side, -0.12, -0.68], 5.5 * k, 2.6 * k, 2.6 * k, arm); B.append(a)
    if joint: B.append(Box(group, [x - 1.7 * k for x in e], [x + 1.7 * k for x in e], joint))
    a, e, _ = seg(group, e, [0.12 * side, 0.08, -1], 4.0 * k, 3.0 * k, 3.0 * k, arm); B.append(a)
    if joint: B.append(Box(group, [x - 1.9 * k for x in e], [x + 1.9 * k for x in e], joint))
    hh, hw, hl = 5.5 * k, 4.2 * k, 6.5 * k
    a, e, (d, u, w) = seg(group, e, [-0.32 * side, 0.0, -0.95], hl, hh, hw, hand, back=1.5 * k); B.append(a)
    e = np.array(e)
    fixed = e - u * (hh / 2 - 1.0 * k)
    a, _, _ = seg(group, fixed.tolist(), d - 0.10 * u, 4.6 * k, 2.0 * k, hw * 0.75, tip, back=0.6 * k); B.append(a)
    moving = e + u * (hh / 2 - 1.1 * k)
    a, _, _ = seg(group, moving.tolist(), d + 0.22 * u, 4.2 * k, 2.2 * k, hw * 0.7, tip, back=0.6 * k); B.append(a)
    return B


def limb(group, start, yaw, roll, length, t, mat):
    """Segmento de pata a lo largo de +x desde `start`, girado (guiñada y alabeo) alrededor de `start`.
    Devuelve (caja, punto final) para encadenar el siguiente segmento."""
    sx, sy, sz = start
    b = Box(group, [sx, sy - t / 2, sz - t / 2], [sx + length, sy + t / 2, sz + t / 2], mat, rot=(0, yaw, roll), origin=[sx, sy, sz])
    end = np.array(start, float) + rot_matrix((0, yaw, roll)) @ np.array([length, 0, 0])
    return b, end.tolist()


def king_crab():
    """Cangrejo rey azul (~2 bloques de caparazón, ~6 de punta a punta de las patas): variedad gigante del fondo del
    mar. Caparazón azul en forma de corazón cubierto de púas, vientre y cara inferior de las patas crema-anaranjado,
    patas larguísimas con púas, pinza derecha más grande y ojos pequeños en pedúnculos."""
    shell = dict(name="shell", base=(46, 86, 168), belly=(230, 170, 120), pattern="speckle", density=0.18, noise=0.07)
    shell_top = dict(name="shell_top", base=(56, 100, 186), pattern="blotch", noise=0.06)
    spine = dict(name="spine", base=(130, 170, 230), noise=0.06)
    spine_tip = dict(name="spine_tip", base=(20, 34, 70), noise=0.04)
    leg = dict(name="leg", base=(48, 88, 170), belly=(232, 176, 128), pattern="speckle", density=0.12, noise=0.06)
    joint = dict(name="joint", base=(236, 150, 90), noise=0.05)
    claw = dict(name="claw", base=(52, 94, 178), belly=(236, 180, 130), pattern="speckle", density=0.15, noise=0.06)
    tip = dict(name="claw_tip", base=(24, 30, 52), noise=0.04)
    belly = dict(name="belly", base=(232, 184, 140), pattern="plates", noise=0.05)
    eye = dict(name="eye", base=(250, 240, 180), noise=0.02)
    stalk = dict(name="stalk", base=(232, 176, 128), noise=0.04)
    B = [
        Box("body", [-14, 14, -10], [14, 24, 14], shell),
        Box("body", [-10, 15, -16], [10, 23, -10], shell),                       # frente del caparazón
        Box("body", [-11, 24, -8], [11, 27, 11], shell_top),                     # lomo abombado
        Box("body", [-6, 27, -4], [6, 29, 7], shell_top),
        Box("body", [-12, 11, -8], [12, 14, 12], belly),
        Box("body", [-1, 19, -22], [1, 21, -16], spine, rot=(8, 0, 0), origin=[0, 20, -16]),   # rostro (pico)
        Box("body", [-3, 18, -19], [-1, 20, -16], spine, rot=(0, -20, 0), origin=[-1, 19, -16]),
        Box("body", [1, 18, -19], [3, 20, -16], spine, rot=(0, 20, 0), origin=[1, 19, -16]),
    ]
    rnd = random.Random(7)
    for x, z, h in [(-7, -5, 4), (0, -2, 5), (7, -5, 4), (-9, 3, 4), (9, 3, 4), (-4, 6, 5), (4, 6, 5), (0, 9, 4),
                    (-11, -1, 3), (11, -1, 3), (-5, -8, 3), (5, -8, 3), (0, 4, 6)]:
        base_y = 27 if abs(x) < 7 and -5 < z < 8 else 24
        if abs(x) < 6 and -4 <= z <= 7:
            base_y = 29
        B.append(Box("spines", [x - 1, base_y, z - 1], [x + 1, base_y + h, z + 1], spine,
                     rot=(rnd.uniform(-12, 12), 0, rnd.uniform(-12, 12)), origin=[x, base_y, z]))
        B.append(Box("spines", [x - 0.5, base_y + h, z - 0.5], [x + 0.5, base_y + h + 1.5, z + 0.5], spine_tip,
                     rot=(rnd.uniform(-12, 12), 0, rnd.uniform(-12, 12)), origin=[x, base_y, z]))
    # pinzas unidas bajo el frente: la derecha (−x) grande, la izquierda más pequeña
    B += crab_claw("claw_r", [-8, 15.5, -13], -1, 1.9, leg, claw, tip, joint)
    B += crab_claw("claw_l", [8, 15.5, -13], 1, 1.45, leg, claw, tip, joint)
    # tres pares de patas larguísimas: suben, bajan hasta el suelo y la punta se apoya
    side = []
    for k, (z, yaw) in enumerate([(-6, 25), (2, 0), (10, -25)]):
        a, e = limb(f"leg_l{k}", [13, 18, z], yaw, 35, 22, 4.5, leg); side.append(a)
        side.append(Box(f"leg_l{k}", [e[0] - 2.4, e[1] - 2.4, e[2] - 2.4], [e[0] + 2.4, e[1] + 2.4, e[2] + 2.4], joint))
        a, e = limb(f"leg_l{k}", e, yaw, -48, 26, 3.6, leg); side.append(a)
        for q in (0.35, 0.65):  # púas en el segundo tramo
            p = np.array(a.origin) + rot_matrix(a.rot) @ np.array([26 * q, 1.8, 0])
            side.append(Box(f"leg_l{k}", [p[0] - 0.6, p[1], p[2] - 0.6], [p[0] + 0.6, p[1] + 2.2, p[2] + 0.6], spine_tip,
                            rot=(0, 0, -20), origin=[p[0], p[1], p[2]]))
        a, e = limb(f"leg_l{k}", e, yaw, -78, 14, 2.4, leg); side.append(a)
    side += [Box("eyes", [3, 21, -17], [4.5, 25, -15.5], stalk, rot=(-15, 0, 0), origin=[3.75, 21, -16]),
             Box("eyes", [2.6, 24.5, -17.6], [4.9, 26.6, -15.3], eye)]
    piv = {"body": [0, 18, 0], "spines": [0, 24, 0], "claw_r": [-8, 15.5, -13], "claw_l": [8, 15.5, -13],
           "eyes": [0, 21, -16]}
    for k, z in enumerate((-6, 2, 10)):    # cada pata gira desde la cadera
        piv[f"leg_l{k}"] = [13, 18, z]
        piv[f"leg_r{k}"] = [-13, 18, z]
    return "king_crab", B + side + mirror_x(side), 256, piv


def abyssal_stalker():
    """Acechador abisal (~4 bloques): depredador ciego de las fosas, pálido como los peces de las profundidades, con
    una mandíbula enorme llena de dientes, "orejas" sensoriales para cazar por sonido, una línea lateral rosada que
    brilla un poco, barbillas colgando de la barbilla y aletas pectorales largas."""
    skin = dict(name="skin", base=(176, 182, 186), belly=(206, 204, 198), pattern="blotch", noise=0.05)
    skin2 = dict(name="skin_dark", base=(140, 148, 156), belly=(190, 190, 186), pattern="blotch", noise=0.06,
                 glowdots=(255, 120, 150), dot_row=0.45, dot_step=3)
    jaw = dict(name="jaw", base=(150, 156, 160), belly=(196, 194, 188), pattern="speckle", density=0.15, noise=0.05,
               gums=(150, 40, 55))
    mouth = dict(name="mouth", base=(70, 20, 28), noise=0.08)
    tooth = dict(name="tooth", base=(236, 232, 214), noise=0.04)
    fin = dict(name="fin", base=(150, 160, 172), pattern="speckle", density=0.25, noise=0.08)
    ear = dict(name="sensor", base=(196, 170, 176), pattern="speckle", density=0.3, noise=0.06)
    barbel = dict(name="barbel", base=(255, 130, 160), noise=0.05, emissive=True)
    B = [
        # cabeza enorme
        Box("head", [-9, 6, -26], [9, 18, -10], skin),
        Box("head", [-7, 8, -32], [7, 17, -26], skin),                       # hocico
        Box("head", [-6.5, 7.5, -31.5], [6.5, 8.5, -11], mouth),            # techo de la boca
        # mandíbula abierta
        Box("jaw", [-8, 0, -31], [8, 4, -12], jaw, rot=(-22, 0, 0), origin=[0, 6, -12]),
        Box("jaw", [-6.5, 3.6, -30], [6.5, 4.4, -13], mouth, rot=(-22, 0, 0), origin=[0, 6, -12]),
        # cuerpo, cada tramo más fino
        Box("body", [-8, 4, -10], [8, 17, 6], skin2),
        Box("body", [-6.5, 5, 6], [6.5, 15, 20], skin2),
        Box("body", [-5, 6, 20], [5, 13.5, 32], skin2),
        Box("tail", [-3.5, 7, 32], [3.5, 12, 42], skin2),
        Box("tail", [-1, 2, 40], [1, 17, 50], fin),                           # aleta de la cola
        # cresta dorsal
        Box("body", [-1, 17, -8], [1, 22, 4], fin, rot=(-12, 0, 0), origin=[0, 17, -2]),
        Box("body", [-1, 15, 8], [1, 19, 18], fin, rot=(-8, 0, 0), origin=[0, 15, 13]),
    ]
    teeth = []
    for z in (-30, -27.5, -25, -22, -19, -16):
        big = z <= -27.5
        for x in (-5.5, -2.5, 1.0, 4.0) if z == -30 else (-6.0, 5.0):
            teeth.append(Box("head", [x, 4.0 if big else 5.5, z], [x + 1, 7.6, z + 1], tooth))
    for z in (-29, -26, -23, -20, -17, -14):
        for x in (-5.5, 4.5):
            teeth.append(Box("jaw", [x, 4, z], [x + 1, 6.5 if z < -24 else 5.5, z + 1], tooth, rot=(-22, 0, 0), origin=[0, 6, -12]))
    side = [
        Box("head", [9, 10, -22], [11, 18, -16], ear, rot=(0, -25, -20), origin=[9, 12, -19]),     # "oreja" sensorial
        Box("head", [10, 14, -21], [12, 21, -17], ear, rot=(0, -25, -35), origin=[10, 14, -19]),
        Box("body", [8, 6, -6], [22, 7, 4], fin, rot=(0, -20, -15), origin=[8, 6.5, -1]),          # aleta pectoral larga
        Box("body", [6.5, 7, 10], [14, 8, 16], fin, rot=(0, -25, -10), origin=[6.5, 7.5, 13]),
        Box("jaw", [4, -6, -26], [5, 1, -25], barbel, rot=(-10, 0, 0), origin=[4.5, 1, -25.5]),    # barbillas luminosas
        Box("jaw", [6, -4, -20], [7, 1, -19], barbel, rot=(-10, 0, 0), origin=[6.5, 1, -19.5]),
    ]
    piv = {"head": [0, 12, -10], "jaw": [0, 6, -12], "body": [0, 10, 0], "tail": [0, 10, 32]}
    return "abyssal_stalker", B + teeth + side + mirror_x(side), 128, piv


# ------------------------------------------------------------------ main

def title_font(sz):
    try:
        return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", sz)
    except OSError:
        return ImageFont.load_default()


def build(out_dir):
    mobs = [
        (lantern_fish, "Pez farol (lantern_fish) v2 — más detallado; ~0,7 bloques; el señuelo brilla", 15),
        (vent_crab, "Cangrejo de chimenea (vent_crab) — ~1,5 bloques; caparazón de basalto con grietas calientes", 9),
        (king_crab, "Cangrejo rey azul (king_crab) — NUEVO; gigante del fondo del mar: ~2 bloques de caparazón, ~6 con las patas", 4.2),
        (abyssal_stalker, "Acechador abisal (abyssal_stalker) — ~5 bloques; ciego, caza por sonido; por debajo de y −150", 5.2),
    ]
    panels = []
    for fn, title, scale in mobs:
        name, boxes, tex_w, pivots = fn()
        tex_h = pack(boxes, tex_w)
        tex, glow = paint(boxes, tex_w, tex_h, seed=zlib.crc32(name.encode()) & 0xffff)
        tex.save(f"{out_dir}/{name}.png")
        glow.save(f"{out_dir}/{name}_glow.png")
        json.dump(to_mob_json(boxes, pivots, tex_w, tex_h), open(f"{out_dir}/{name}.json", "w"), indent=1)
        json.dump(to_bbmodel(name, boxes, tex, tex_w, tex_h), open(f"{out_dir}/{name}.bbmodel", "w"))
        views = [render(boxes, tex, a, p, scale, (520, 360), (260, 205)) for a, p in ((-35, -18), (140, -22))]
        texv = tex.resize((tex_w * (256 // tex_w) if tex_w <= 128 else tex_w, tex_h * (256 // tex_w) if tex_w <= 128 else tex_h), Image.NEAREST)
        panels.append((title, views, texv))
    W = 520 * 2 + 280
    H = sum(40 + 360 for _ in panels)
    sheet = Image.new("RGBA", (W, H), (14, 22, 32, 255))
    d = ImageDraw.Draw(sheet)
    y = 0
    for title, views, texv in panels:
        d.text((14, y + 10), title, fill=(220, 235, 240, 255), font=title_font(18))
        # fondo de agua oscura con degradado
        for k, v in enumerate(views):
            bg = Image.new("RGBA", v.size, (0, 0, 0, 0))
            arr = np.zeros((v.height, v.width, 4), np.uint8)
            g = np.linspace(1, 0, v.height)[:, None]
            arr[..., 0] = (18 + 14 * g).astype(np.uint8)
            arr[..., 1] = (40 + 30 * g).astype(np.uint8)
            arr[..., 2] = (62 + 40 * g).astype(np.uint8)
            arr[..., 3] = 255
            bg = Image.fromarray(arr, "RGBA")
            bg.alpha_composite(v)
            sheet.alpha_composite(bg, (k * 520, y + 40))
        tv = texv.copy()
        tv.thumbnail((260, 340), Image.NEAREST)
        sheet.alpha_composite(Image.new("RGBA", (tv.width + 8, tv.height + 8), (40, 40, 48, 255)), (1046, y + 46))
        sheet.alpha_composite(tv, (1050, y + 50))
        d.text((1050, y + 56 + tv.height), "textura", fill=(160, 180, 190, 255), font=title_font(13))
        y += 400
    sheet.save(f"{out_dir}/thalassa_mobs_preview.png")
    print("ok", out_dir)


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else ".")
