"""Texturas del Congelador del tiempo espacial (1.0.4): acero oscuro con un anillo y un reloj de arena.
Apagado: anillo gris azulado. Activo (congelando): anillo cian brillante y escarcha en los bordes.
Salida en tools/designs/time_freezer/: side_off.png, side_on.png, top.png (16x16). Es un bloque suelto (no forma terreno),
asi que puede tener un dibujo central."""
import os
import sys
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "time_freezer")
BASE, LIGHT, DARK, DARKEST = (58, 64, 78), (92, 100, 118), (34, 38, 48), (20, 22, 30)


def noise(x, y, amp=5):
    h = (x * 73856093) ^ (y * 19349663) ^ 0x5bd1e995
    h = (h ^ (h >> 13)) * 0x85ebca6b & 0xFFFFFFFF
    return (h % (2 * amp + 1)) - amp


def shade(c, d):
    return tuple(max(0, min(255, v + d)) for v in c)


def plate(img):
    for y in range(16):
        for x in range(16):
            c = shade(BASE, noise(x, y))
            if x in (0, 15) or y in (0, 15):
                c = DARK
            elif x == 1 or y == 1:
                c = shade(LIGHT, noise(x, y, 3))
            elif x == 14 or y == 14:
                c = shade(DARK, 6)
            img.putpixel((x, y), c + (255,))
    for cx, cy in ((3, 3), (12, 3), (3, 12), (12, 12)):  # remaches
        img.putpixel((cx, cy), LIGHT + (255,))
        img.putpixel((cx + 1, cy + 1), DARKEST + (255,))


def side(active):
    img = Image.new("RGBA", (16, 16))
    plate(img)
    ring = (80, 224, 255) if active else (96, 108, 130)
    glow = (30, 96, 126) if active else (44, 50, 64)
    inner = (12, 52, 74) if active else (22, 26, 36)
    glass = (200, 252, 255) if active else (118, 128, 150)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d < 3.9:
                img.putpixel((x, y), inner + (255,))
            elif d < 5.0:
                img.putpixel((x, y), ring + (255,))
            elif d < 5.9:
                img.putpixel((x, y), glow + (255,))
    rows = {5: (5, 10), 6: (6, 9), 7: (7, 8), 8: (7, 8), 9: (6, 9), 10: (5, 10)}  # reloj de arena
    for y, (a, b) in rows.items():
        for x in range(a, b + 1):
            if y in (5, 10) or True:
                img.putpixel((x, y), glass + (255,))
    if active:  # escarcha en las esquinas
        for x, y in ((2, 2), (3, 2), (2, 3), (13, 2), (12, 2), (13, 3), (2, 13), (3, 13), (2, 12), (13, 13), (12, 13), (13, 12)):
            img.putpixel((x, y), (190, 236, 255, 255))
    return img


def top():
    img = Image.new("RGBA", (16, 16))
    plate(img)
    for y in range(5, 11):
        for x in range(5, 11):
            edge = x in (5, 10) or y in (5, 10)
            img.putpixel((x, y), (DARKEST if not edge else LIGHT) + (255,))
    for y in range(7, 9):
        for x in range(7, 9):
            img.putpixel((x, y), (96, 108, 130, 255))
    return img


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    side(False).save(os.path.join(OUT, "side_off.png"))
    side(True).save(os.path.join(OUT, "side_on.png"))
    top().save(os.path.join(OUT, "top.png"))
    # vista previa ampliada
    prev = Image.new("RGBA", (16 * 3 * 8 + 16, 16 * 8), (10, 10, 14, 255))
    for i, im in enumerate((side(False), side(True), top())):
        prev.paste(im.resize((128, 128), Image.NEAREST), (i * 136, 0))
    prev.save(os.path.join(OUT, "preview.png"))
    print("ok")
