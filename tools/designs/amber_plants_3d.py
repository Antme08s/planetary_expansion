"""Plantas de Cerúlea en 3D, como se verían en el juego (1.0.3, 2026-11-13).

Uso: python3 -I tools/designs/amber_plants_3d.py <salida.png>

Cámara ortográfica inclinada (como mirar un bloque de pie a unos pasos). Cada planta va sobre su bloque de pasto:
las plantas normales son dos planos en cruz (como en Minecraft), el pasto alto ocupa dos bloques, la liana cuelga en
la cara de un bloque de hojas y el nenúfar flota sobre el agua. Usa las texturas de amber_textures.py.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import amber_textures as AT  # noqa: E402

YAW, PITCH = math.radians(38), math.radians(28)
SCALE = 74                      # px por bloque


def proj(p):
    x, y, z = p
    cx, sx = math.cos(YAW), math.sin(YAW)
    rx = x * cx - z * sx
    rz = x * sx + z * cx
    cp, sp = math.cos(PITCH), math.sin(PITCH)
    ry = y * cp - rz * sp
    depth = y * sp + rz * cp          # más grande = más cerca de la cámara
    return rx * SCALE, -ry * SCALE, depth


class Scene:
    def __init__(self):
        self.quads = []               # (depth, corners3d, tex, shade, glow)

    def quad(self, p0, p1, p3, tex, shade=1.0, glow=False, u0=0.0, u1=1.0):
        """Cuadrilátero p0 (u=0,v=0), p1 (u=1,v=0), p3 (u=0,v=1); u0..u1: trozo de la textura en horizontal."""
        p0, p1, p3 = map(np.array, (p0, p1, p3))
        c = (p0 + p1 + p3 + (p1 + p3 - p0)) / 4
        self.quads.append((proj(c)[2], (p0, p1, p3), tex, shade, glow, u0, u1))

    def cube(self, x, y, z, top, side, bottom=None):
        # caras visibles con esta cámara: arriba, +z (delante a la izquierda) y −x... se dibujan las seis y el orden manda
        T0 = (x, y + 1, z)
        self.quad((x, y + 1, z), (x + 1, y + 1, z), (x, y + 1, z + 1), top, 1.0)
        self.quad((x, y + 1, z + 1), (x + 1, y + 1, z + 1), (x, y, z + 1), side, 0.8)       # cara +z
        self.quad((x, y + 1, z + 1), (x, y + 1, z), (x, y, z + 1), side, 0.62)              # cara −x
        self.quad((x + 1, y + 1, z), (x + 1, y + 1, z + 1), (x + 1, y, z), side, 0.7)       # cara +x
        self.quad((x, y + 1, z), (x + 1, y + 1, z), (x, y, z), side, 0.7)                   # cara −z
        return T0

    def cross(self, x, y, z, tex, glow=False, h=1.0):
        """Dos planos en diagonal, partidos por la mitad para que el orden de dibujo salga bien."""
        m = (x + 0.5, z + 0.5)
        for (ax, az), (bx, bz) in (((x + 0.15, z + 0.15), (x + 0.85, z + 0.85)), ((x + 0.15, z + 0.85), (x + 0.85, z + 0.15))):
            # mitad 1: de a al centro; mitad 2: del centro a b
            self.quad((ax, y + h, az), (m[0], y + h, m[1]), (ax, y, az), tex, 1.0, glow, 0.0, 0.5)
            self.quad((m[0], y + h, m[1]), (bx, y + h, bz), (m[0], y, m[1]), tex, 1.0, glow, 0.5, 1.0)

    def render(self, size, origin):
        img = Image.new("RGBA", size, (0, 0, 0, 0))
        glow = Image.new("RGBA", size, (0, 0, 0, 0))
        for depth, (p0, p1, p3), tex, shade, is_glow, u0, u1 in sorted(self.quads, key=lambda q: q[0]):
            s0, s1, s3 = (np.array(proj(p)[:2]) + origin for p in (p0, p1, p3))
            # textura recortada a [u0, u1] y escalada
            tw = tex.width
            crop = tex.crop((int(round(u0 * tw)), 0, int(round(u1 * tw)), tex.height))
            k = 8
            big = crop.resize((crop.width * k, crop.height * k), Image.NEAREST)
            if shade != 1.0:
                arr = np.asarray(big).astype(float)
                arr[..., :3] *= shade
                big = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")
            # afín: salida (X, Y) -> textura (u*W, v*H)
            A = np.array([[s1[0] - s0[0], s3[0] - s0[0]], [s1[1] - s0[1], s3[1] - s0[1]]])
            if abs(np.linalg.det(A)) < 1e-6:
                continue
            inv = np.linalg.inv(A)
            W, H = big.size
            a, b = inv[0] * W
            d, e = inv[1] * H
            c = -(a * s0[0] + b * s0[1])
            f = -(d * s0[0] + e * s0[1])
            warped = big.transform(size, Image.AFFINE, (a, b, c, d, e, f), resample=Image.NEAREST)
            img.alpha_composite(warped)
            if is_glow:
                glow.alpha_composite(warped)
        al = np.asarray(glow.filter(ImageFilter.GaussianBlur(10)))[..., 3].astype(float)
        halo = np.zeros((size[1], size[0], 4), np.uint8)
        halo[..., 0], halo[..., 1], halo[..., 2] = 170, 245, 255
        halo[..., 3] = np.clip(al * 0.5, 0, 255).astype(np.uint8)
        return Image.alpha_composite(img, Image.fromarray(halo, "RGBA"))


def water_tex():
    t = Image.new("RGBA", (16, 16), (40, 120, 150, 210))
    px = t.load()
    for i in range(22):
        px[(i * 7) % 16, (i * 5) % 16] = (70, 160, 190, 220)
    return t


def panel(tex, kind):
    sc = Scene()
    grass_top, grass_side = tex["amber_grass_top"], tex["amber_grass_side"]
    if kind == "tall_grass":
        sc.cube(0, -1, 0, grass_top, grass_side)
        sc.cross(0, 0, 0, tex["tall_grass_bottom"])
        sc.cross(0, 1, 0, tex["tall_grass_top"])
    elif kind == "short_grass":
        sc.cube(0, -1, 0, tex["amber_short_grass_block_top"], tex["amber_short_grass_block_side"])
        sc.cross(0, 0, 0, tex["short_grass"])
    elif kind in ("azure_flower", "glow_flower"):
        sc.cube(0, -1, 0, grass_top, grass_side)
        sc.cross(0, 0, 0, tex[kind], glow=kind == "glow_flower")
    elif kind == "glow_mushroom":
        sc.cube(0, -1, 0, tex["amber_mud"], tex["amber_mud"])
        sc.cross(0, 0, 0, tex["glow_mushroom"], glow=True)
    elif kind == "mire_vine":
        sc.cube(0, 1, 0, tex["mirewood_leaves"], tex["mirewood_leaves"])
        lv = tex["mire_vine"]
        z = 1.02                                           # en la cara +z, colgando dos bloques
        sc.quad((0, 1, z), (1, 1, z), (0, 0, z), lv, 0.9)
        sc.quad((0, 0, z), (1, 0, z), (0, -1, z), lv, 0.9)
    elif kind == "azure_lily":
        w = water_tex()
        sc.cube(0, -1, 0, w, w)
        sc.quad((0, 0.02, 0), (1, 0.02, 0), (0, 0.02, 1), tex["azure_lily"], 1.0)
    elif kind == "bush":
        sc.cube(0, -1, 0, grass_top, grass_side)
        sc.cube(0, 0, 0, tex["bush_leaves"], tex["bush_leaves"])
    return sc


def main(out):
    tex = AT.make_textures()
    items = [("tall_grass", "Pasto alto (2 bloques)"), ("short_grass", "Pasto bajo"), ("azure_flower", "Flor azul"),
             ("glow_flower", "Flor luminosa"), ("glow_mushroom", "Hongo luminoso"), ("mire_vine", "Liana"),
             ("azure_lily", "Nenúfar azul"), ("bush", "Arbusto")]
    cw, ch, cols = 300, 340, 4
    rows = math.ceil(len(items) / cols)
    sheet = AT.sky(cols * cw, 60 + rows * ch)
    F, Fm, f = AT.fonts()
    d = ImageDraw.Draw(sheet)
    d.text((16, 14), "Cerúlea — plantas en 3D (como en el juego)", fill=(25, 35, 45, 255), font=F)
    for i, (kind, title) in enumerate(items):
        x0, y0 = (i % cols) * cw, 60 + (i // cols) * ch
        img = panel(tex, kind).render((cw, ch - 30), np.array([cw / 2, ch * 0.62]))
        sheet.alpha_composite(img, (x0, y0 + 24))
        d.text((x0 + 14, y0 + 4), title, fill=(25, 35, 45, 255), font=Fm)

    # escena de noche: flores y hongos luminosos brillando
    night = Scene()
    for (x, z, kind) in ((0, 0, "glow_flower"), (1, 0, "short_grass"), (2, 0, "glow_mushroom"), (0, 1, "azure_flower"),
                         (1, 1, "glow_flower"), (2, 1, "short_grass"), (0, 2, "short_grass"), (1, 2, "glow_mushroom"),
                         (2, 2, "glow_flower")):
        top = tex["amber_mud"] if kind == "glow_mushroom" else tex["amber_grass_top"]
        side = tex["amber_mud"] if kind == "glow_mushroom" else tex["amber_grass_side"]
        night.cube(x, -1, z, top, side)
        night.cross(x, 0, z, tex[kind], glow=kind in ("glow_flower", "glow_mushroom"))
    nw, nh = cols * cw, 420
    bg = np.zeros((nh, nw, 4), np.uint8)
    t = np.linspace(0, 1, nh)[:, None]
    for i, (a, b) in enumerate(((12, 30), (18, 40), (40, 70))):
        bg[..., i] = (a * (1 - t) + b * t).astype(np.uint8)
    bg[..., 3] = 255
    nimg = Image.fromarray(bg, "RGBA")
    rnd = np.random.default_rng(3)
    for _ in range(160):
        x, y = rnd.integers(0, nw), rnd.integers(0, nh // 2)
        nimg.putpixel((int(x), int(y)), (220, 230, 255, 255))
    scene = night.render((nw, nh), np.array([nw / 2, nh * 0.42]))
    arr = np.asarray(scene).astype(float)
    arr[..., :3] *= 0.45                                       # de noche todo más oscuro...
    lit = np.asarray(scene).astype(float)
    glowmask = (lit[..., 1] > 200) & (lit[..., 2] > 200)       # ...menos lo que brilla
    arr[glowmask, :3] = lit[glowmask, :3]
    nimg.alpha_composite(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA"))
    ImageDraw.Draw(nimg).text((16, 12), "De noche: lo luminoso brilla", fill=(220, 235, 255, 255), font=Fm)
    full = Image.new("RGBA", (nw, sheet.height + nh), (0, 0, 0, 255))
    full.alpha_composite(sheet, (0, 0))
    full.alpha_composite(nimg, (0, sheet.height))
    full.save(out)
    print("ok", out)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "cerulea_plantas_3d.png")
