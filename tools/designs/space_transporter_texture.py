"""Texturas del Transportador espacial (1.0.4): acero oscuro con una flecha morada que sube hacia un anillo (la orbita).
end.png: cara del eje (agujero central con aro de laton). Bloque suelto de maquina: puede tener dibujo central.
Salida en tools/designs/space_transporter/: side.png, end.png (16x16)."""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "space_transporter")
BASE, LIGHT, DARK, DARKEST = (56, 54, 72), (90, 86, 112), (32, 30, 44), (18, 16, 26)
GLOW, GLOW_DIM, CORE = (214, 140, 255), (120, 70, 170), (250, 225, 255)
BRASS, BRASS_D = (200, 160, 80), (130, 98, 44)


def noise(x, y, amp=5):
    h = (x * 73856093) ^ (y * 19349663) ^ 0x2f6b1c55
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
    for cx, cy in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img.putpixel((cx, cy), LIGHT + (255,))


def side():
    img = Image.new("RGBA", (16, 16))
    plate(img)
    # anillo (la orbita) arriba
    for x in range(4, 12):
        img.putpixel((x, 3), GLOW_DIM + (255,))
    for x in (3, 12):
        img.putpixel((x, 4), GLOW_DIM + (255,))
    for x in range(4, 12):
        img.putpixel((x, 5), GLOW_DIM + (255,))
    # flecha que sube (punta estrecha arriba, ancha abajo)
    head = {6: (7, 8), 7: (6, 9), 8: (5, 10)}
    for y, (a, b) in head.items():
        for x in range(a, b + 1):
            img.putpixel((x, y), (CORE if y == 6 else GLOW) + (255,))
    for y in range(9, 13):
        img.putpixel((7, y), GLOW + (255,))
        img.putpixel((8, y), GLOW + (255,))
    # base emisora
    for x in range(5, 11):
        img.putpixel((x, 13), GLOW_DIM + (255,))
    return img


def end():
    img = Image.new("RGBA", (16, 16))
    plate(img)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d < 2.6:
                img.putpixel((x, y), DARKEST + (255,))
            elif d < 3.6:
                img.putpixel((x, y), (BRASS if (x + y) % 2 else BRASS_D) + (255,))
            elif d < 4.4:
                img.putpixel((x, y), GLOW_DIM + (255,))
    return img


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    side().save(os.path.join(OUT, "side.png"))
    end().save(os.path.join(OUT, "end.png"))
    print("ok")
