"""Vista previa de una armadura del mod sin abrir el juego.

Uso: python3 -I tools/preview_armor.py <armor_models/x.json> <textura.png> <salida.png> [titulo]

Dibuja el traje (casco, pechera, pantalones y botas) desde delante-izquierda y desde atrás-derecha con
la misma luz por cara que usa Minecraft (arriba 1.0, delante/detrás 0.8, lados 0.6, abajo 0.5).
Sirve para comparar texturas antes de meterlas en el jar.
"""
import json
import math
import sys

import numpy as np
from PIL import Image, ImageDraw

PIVOTS = {
    "head": (0, 0, 0), "body": (0, 0, 0),
    "right_arm": (-5, 2, 0), "left_arm": (5, 2, 0),
    "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0),
}
NORMALS = {"front": np.array([0, 0, -1.0]), "back": np.array([0, 0, 1.0]), "right": np.array([-1.0, 0, 0]),
           "left": np.array([1.0, 0, 0]), "top": np.array([0, -1.0, 0]), "bottom": np.array([0, 1.0, 0])}
SHADE = {"top": 1.0, "bottom": 0.5, "front": 0.8, "back": 0.8, "right": 0.6, "left": 0.6}


def rot_zyx(rx, ry, rz):
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def box_faces(box):
    """Caras de una caja con UV de caja: (nombre, origen, eje_u, eje_v, rect_uv). Coordenadas de modelo
    de Minecraft: y hacia abajo, el frente mira a -z, la derecha del jugador es -x."""
    x0, y0, z0 = box["min"]
    w, h, d = box["size"]
    inf = box.get("inflate", 0)
    x0 -= inf; y0 -= inf; z0 -= inf
    W, H, D = w + 2 * inf, h + 2 * inf, d + 2 * inf
    x1, y1, z1 = x0 + W, y0 + H, z0 + D
    u, v = box["uv"]
    wi, hi, di = round(w), round(h), round(d)
    rects = {
        "top": (u + di, v, wi, di), "bottom": (u + di + wi, v, wi, di),
        "right": (u, v + di, di, hi), "front": (u + di, v + di, wi, hi),
        "left": (u + di + wi, v + di, di, hi), "back": (u + 2 * di + wi, v + di, wi, hi),
    }
    X, Y, Z = np.eye(3)
    faces = {
        "front": ((x0, y0, z0), X * W, Y * H),
        "back": ((x1, y0, z1), -X * W, Y * H),
        "right": ((x0, y0, z1), -Z * D, Y * H),
        "left": ((x1, y0, z0), Z * D, Y * H),
        "top": ((x0, y0, z1), X * W, -Z * D),
        "bottom": ((x0, y1, z0), X * W, Z * D),
    }
    out = []
    for name, (o, a, b) in faces.items():
        rect = rects[name]
        if box.get("mirror"):
            # En espejo Minecraft intercambia los lados y da la vuelta a cada cara en horizontal.
            swap = {"right": "left", "left": "right"}.get(name, name)
            rect = rects[swap]
            rect = (rect[0] + rect[2], rect[1], -rect[2], rect[3])
        out.append((name, np.array(o, float), a, b, rect))
    return out


def render(model, tex, yaw, pitch, scale=15, size=(360, 560)):
    tex = np.asarray(tex.convert("RGBA")).astype(float)
    th, tw = tex.shape[:2]
    Wimg, Himg = size
    color = np.zeros((Himg, Wimg, 4))
    zbuf = np.full((Himg, Wimg), np.inf)
    cyw, syw = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)
    view = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cyw, 0, syw], [0, 1, 0], [-syw, 0, cyw]])
    center = np.array([Wimg / 2, Himg * 0.27])

    def to_screen(p):
        q = view @ p
        return np.array([center[0] + q[0] * scale, center[1] + q[1] * scale]), q[2]

    for piece, parts in model["pieces"].items():
        for part, nodes in parts.items():
            pivot = np.array(PIVOTS[part], float)
            for node in nodes:
                R = rot_zyx(*node["rotation"])
                off = np.array(node["offset"], float)
                for box in node["boxes"]:
                    for name, o, a, b, (ru, rv, rw, rh) in box_faces(box):
                        O = pivot + off + R @ o
                        A = R @ a
                        B = R @ b
                        n = view @ (R @ NORMALS[name])
                        if n[2] >= -1e-6:  # cara de espaldas a la cámara (la cámara mira hacia +z)
                            continue
                        so, zo = to_screen(O)
                        sa = (view @ A)[:2] * scale
                        sb = (view @ B)[:2] * scale
                        za, zb = (view @ A)[2], (view @ B)[2]
                        corners = np.array([so, so + sa, so + sb, so + sa + sb])
                        xmin, ymin = np.floor(corners.min(0)).astype(int)
                        xmax, ymax = np.ceil(corners.max(0)).astype(int)
                        xmin, ymin = max(xmin, 0), max(ymin, 0)
                        xmax, ymax = min(xmax, Wimg), min(ymax, Himg)
                        if xmax <= xmin or ymax <= ymin:
                            continue
                        M = np.array([[sa[0], sb[0]], [sa[1], sb[1]]])
                        if abs(np.linalg.det(M)) < 1e-9:
                            continue
                        Minv = np.linalg.inv(M)
                        ys, xs = np.mgrid[ymin:ymax, xmin:xmax]
                        px = np.stack([xs + 0.5 - so[0], ys + 0.5 - so[1]], -1)
                        st = px @ Minv.T
                        s, t = st[..., 0], st[..., 1]
                        inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
                        tu = np.clip(np.floor(ru + s * rw).astype(int) if rw > 0 else np.floor(ru + s * rw - 1e-6).astype(int), 0, tw - 1)
                        tv = np.clip(np.floor(rv + t * rh).astype(int), 0, th - 1)
                        texel = tex[tv, tu]
                        inside &= texel[..., 3] > 127
                        depth = zo + s * za + t * zb
                        yy, xx = ys[inside], xs[inside]
                        dd = depth[inside]
                        closer = dd < zbuf[yy, xx]
                        yy, xx, dd = yy[closer], xx[closer], dd[closer]
                        zbuf[yy, xx] = dd
                        c = texel[inside][closer].copy()
                        c[:, :3] *= SHADE[name]
                        color[yy, xx] = c
    img = Image.fromarray(color.clip(0, 255).astype(np.uint8), "RGBA")
    return img


def sheet(model, tex, title=""):
    views = [render(model, tex, math.radians(-30), math.radians(-12)),
             render(model, tex, math.radians(150), math.radians(-12))]
    w = sum(v.width for v in views)
    out = Image.new("RGBA", (w, views[0].height + 30), (205, 140, 95, 255))
    x = 0
    for v in views:
        out.alpha_composite(v, (x, 30))
        x += v.width
    if title:
        ImageDraw.Draw(out).text((10, 8), title, fill=(30, 20, 15, 255))
    return out


if __name__ == "__main__":
    model = json.load(open(sys.argv[1]))
    tex = Image.open(sys.argv[2])
    sheet(model, tex, sys.argv[4] if len(sys.argv) > 4 else "").save(sys.argv[3])
