"""Recolorea engine_mount.png de Cosmonautics para el soporte de metano: el metal gris se enfría hacia el azul acero y
la cara de cobre (la salida de la tobera) pasa a verde azulado, el color del metano de Titán.
Uso: python3 methane_engine_texture.py <engine_mount.png de Cosmonautics> <salida.png>"""
import colorsys
import sys

from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGBA")
px = im.load()
for y in range(im.size[1]):
    for x in range(im.size[0]):
        r, g, b, a = px[x, y]
        if a == 0:
            continue
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        if s > 0.30:                      # cobre -> verde azulado
            h, s = 0.50, min(1.0, s * 0.95)
        else:                             # gris -> acero frío
            h, s = 0.58, max(s, 0.16)
            v = min(1.0, v * 1.02)
        r, g, b = colorsys.hsv_to_rgb(h, s, v)
        px[x, y] = (round(r * 255), round(g * 255), round(b * 255), a)
im.save(dst)
