"""Vista previa de escaleras, losas, puertas y trampillas de Cerúlea (1.0.3, 2026-11-13).

Uso: python3 -I tools/designs/cerulea_building_preview.py <salida.png>   (antes: cerulea_assets.py)
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import amber_textures as AT  # noqa: E402
import amber_plants_3d as P3  # noqa: E402

TEX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cerulea", "block")
ITX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cerulea", "item")


def tex(n):
    return Image.open(os.path.join(TEX, n + ".png")).convert("RGBA")


def crop(t, u0, v0, u1, v1):
    """Trozo de la textura (coordenadas 0..1, v hacia abajo), como hace Minecraft con las caras de las cajas."""
    W = t.width
    return t.crop((round(u0 * W), round(v0 * W), max(round(u0 * W) + 1, round(u1 * W)), max(round(v0 * W) + 1, round(v1 * W))))


def box(sc, x0, y0, z0, x1, y1, z1, top, side, front=None, off=(0, 0, 0), back=None, bottom=None):
    """Caja con coordenadas locales del bloque (0..1, para recortar la textura) desplazada a off."""
    front = front or side
    ox, oy, oz = off
    P = lambda x, y, z: (x + ox, y + oy, z + oz)          # noqa: E731
    sc.quad(P(x0, y1, z0), P(x1, y1, z0), P(x0, y1, z1), crop(top, x0, z0, x1, z1), 1.0)
    sc.quad(P(x0, y1, z1), P(x1, y1, z1), P(x0, y0, z1), crop(front, x0, 1 - y1, x1, 1 - y0), 0.8)
    sc.quad(P(x0, y1, z1), P(x0, y1, z0), P(x0, y0, z1), crop(side, 1 - z1, 1 - y1, 1 - z0, 1 - y0), 0.62)
    sc.quad(P(x1, y1, z0), P(x1, y1, z1), P(x1, y0, z0), crop(side, z0, 1 - y1, z1, 1 - y0), 0.7)
    sc.quad(P(x0, y1, z0), P(x1, y1, z0), P(x0, y0, z0), back if back is not None else crop(side, x0, 1 - y1, x1, 1 - y0), 0.7)
    if bottom is not None:
        sc.quad(P(x0, y0, z0), P(x1, y0, z0), P(x0, y0, z1), bottom, 0.5)


def main(out):
    mats = [("cerulean_stone", "Piedra"), ("cerulean_cobblestone", "Roca"), ("cerulean_stone_bricks", "Ladrillos"),
            ("polished_cerulean_stone", "Piedra pulida"), ("deep_cerulean_stone", "Piedra profunda"),
            ("silverwood_planks", "Tablones plateados"), ("duskwood_planks", "Tablones violeta"), ("mirewood_planks", "Tablones del pantano")]
    cw, ch, cols = 260, 230, 4
    F, Fm, f = AT.fonts()
    rows_mats = math.ceil(len(mats) / cols)
    sheet = AT.sky(cols * cw, 60 + rows_mats * ch + 560)
    d = ImageDraw.Draw(sheet)
    d.text((16, 14), "Cerúlea — escaleras, losas, puertas y trampillas", fill=(25, 35, 45, 255), font=F)
    P3.SCALE = 52
    for i, (m, title) in enumerate(mats):
        t = tex(m)
        sc = P3.Scene()
        box(sc, 0, 0, 0, 1, 0.5, 1, t, t)                     # escalera: abajo entera
        box(sc, 0, 0.5, 0.5, 1, 1, 1, t, t)                   # y el escalón de atrás
        box(sc, 0, 0, 0, 1, 0.5, 1, t, t, off=(1.6, 0, 0))   # losa
        x0, y0 = (i % cols) * cw, 60 + (i // cols) * ch
        img = sc.render((cw, ch - 30), np.array([cw * 0.38, ch * 0.62]))
        sheet.alpha_composite(img, (x0, y0 + 24))
        d.text((x0 + 12, y0 + 4), title, fill=(25, 35, 45, 255), font=Fm)
    y0 = 60 + rows_mats * ch
    d.text((16, y0), "Puertas y trampillas (cerrada y abierta)", fill=(25, 35, 45, 255), font=Fm)
    P3.SCALE = 70
    for j, w in enumerate(("silverwood", "duskwood", "mirewood")):
        top, bot, trap = tex(f"{w}_door_top"), tex(f"{w}_door_bottom"), tex(f"{w}_trapdoor")
        full = Image.new("RGBA", (16, 32))
        full.alpha_composite(top, (0, 0))
        full.alpha_composite(bot, (0, 16))
        x0 = 20 + j * 345
        sheet.alpha_composite(full.resize((80, 160), Image.NEAREST), (x0, y0 + 40))
        sc = P3.Scene()
        edge = crop(bot, 0, 0, 0.19, 1)
        box(sc, 0, 0, 0.81, 1, 1, 1, crop(bot, 0, 0.81, 1, 1), edge, bot)
        box(sc, 0, 0, 0.81, 1, 1, 1, crop(top, 0, 0.81, 1, 1), crop(top, 0, 0, 0.19, 1), top, off=(0, 1, 0))
        img = sc.render((200, 300), np.array([90, 210]))
        sheet.alpha_composite(img, (x0 + 85, y0 + 10))
        P3.SCALE = 56
        sc = P3.Scene()
        box(sc, 0, 0, 0, 1, 0.1875, 1, trap, crop(trap, 0, 0, 1, 0.19), bottom=trap)      # cerrada
        box(sc, 0, 0, 0.81, 1, 1, 1, crop(trap, 0, 0, 1, 0.19), crop(trap, 0, 0, 0.19, 1), trap, off=(1.6, 0, 0), back=trap)  # abierta
        img = sc.render((260, 190), np.array([80, 130]))
        sheet.alpha_composite(img, (x0 + 85, y0 + 290))
        sheet.alpha_composite(Image.open(os.path.join(ITX, f"{w}_door.png")).resize((64, 64), Image.NEAREST), (x0 + 8, y0 + 205))
        sheet.alpha_composite(trap.resize((64, 64), Image.NEAREST), (x0 + 8, y0 + 300))
        d.text((x0, y0 + 268), {"silverwood": "Plateado", "duskwood": "Violeta", "mirewood": "Pantano"}[w], fill=(25, 35, 45, 255), font=Fm)
        P3.SCALE = 70
    sheet.save(out)
    print("ok", out)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "cerulea_construccion.png")
