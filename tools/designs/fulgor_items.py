"""Objetos y bloques de las criaturas de Fulgor (1.0.4), con el estilo de items_v2.
Uso: python3 -I tools/designs/fulgor_items.py <carpeta>."""
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from items_v2 import Canvas, ramp, T  # noqa: E402
from items_v2_draw import strip  # noqa: E402

VIOLET = (206, 128, 255)


def arc_gland():
    """Glándula de arco: saco violeta translúcido con venas que brillan y un nudo de carne oscura."""
    c = Canvas()
    rp = ramp((150, 80, 210))
    s = Canvas()
    s.ellipsoid(8.0, 9.0, 5.6, 5.0, rp, bounce=0.25)
    s.ellipsoid(8.4, 4.4, 2.0, 1.8, ramp((70, 50, 80)), spec=False)
    vein = ramp((236, 190, 255))
    for (x0, y0, x1, y1) in ((6, 7, 4, 11), (9, 7, 11, 12), (8, 8, 8, 13)):
        s.line(x0, y0, x1, y1, vein[3])
    s.set(5, 7, vein[5]); s.set(6, 6, vein[5])
    s.outline(factor=0.42)
    c.merge(s)
    return c.image()


def crystal_filament():
    """Filamento de cristal: hebra fina y retorcida de vidrio claro con destellos."""
    c = Canvas()
    rp = ramp((200, 190, 230))
    s = Canvas()
    pts = [(2.0, 14.0), (5.0, 10.5), (4.2, 7.6), (8.4, 5.8), (10.4, 3.0), (14.0, 2.0)]
    for k in range(len(pts) - 1):
        strip(s, pts[k], pts[k + 1], 0.75, 0.75, rp)
    for (x, y) in ((5, 10), (8, 6), (12, 2)):
        if s.get(x, y):
            s.set(x, y, (255, 250, 255))
    s.outline(factor=0.42)
    c.merge(s)
    return c.image()


def copper_antenna():
    """Antena de cobre: varilla de cobre con dos anillos y la punta violeta cargada."""
    c = Canvas()
    cu = ramp((196, 112, 64))
    s = Canvas()
    strip(s, (3.0, 13.6), (11.6, 5.0), 0.9, 0.7, cu)
    for (cx, cy) in ((5.6, 11.0), (8.4, 8.2)):
        s.ellipsoid(cx, cy, 1.5, 1.5, cu)
    for (x, y) in ((6, 9), (9, 7)):
        s.set(x, y, (84, 168, 146))                                   # verdín
    s.outline(factor=0.42)
    c.merge(s)
    t = Canvas()
    t.ellipsoid(12.6, 3.6, 2.2, 2.2, ramp(VIOLET))
    t.set(12, 3, (255, 240, 255))
    t.outline(factor=0.4)
    c.merge(t)
    return c.image()


def fulgurite_shard():
    """Fragmento de fulgurita: astilla de vidrio de rayo, gris violáceo y retorcida."""
    c = Canvas()
    rp = ramp((150, 136, 170))
    s = Canvas()
    s.poly([(4.0, 14.0), (6.8, 6.0), (9.6, 1.6), (11.0, 4.0), (9.8, 9.0), (7.2, 14.4)], rp[3])
    s.poly([(4.0, 14.0), (6.8, 6.0), (9.6, 1.6), (8.0, 8.0), (7.2, 14.4)], rp[4])
    s.line(7, 12, 9, 4, rp[1])
    for (x, y) in ((7, 6), (8, 4)):
        s.set(x, y, (246, 236, 255))
    s.outline(factor=0.42)
    c.merge(s)
    return c.image()


def arc_lamp_frames(storm):
    """Lámpara de arco: marco de cobre y un arco eléctrico violeta en el centro. Con tormenta, más brillo y parpadeo."""
    frames = []
    levels = (1.0, 0.45, 0.9, 0.3, 1.0, 0.6) if storm else (0.75, 0.68, 0.72, 0.66)
    cu = ramp((180, 104, 60))
    for k, b in enumerate(levels):
        im = Image.new("RGBA", (16, 16))
        px = im.load()
        for y in range(16):
            for x in range(16):
                if x in (0, 15) or y in (0, 15):
                    col = cu[1]
                elif x in (1, 14) or y in (1, 14):
                    col = cu[4] if (x == 1 or y == 1) else cu[2]
                else:
                    d = math.hypot(x - 7.5, y - 7.5) / 7.0
                    g = max(0.0, 1.0 - d) * b
                    col = (int(40 + 200 * g), int(24 + 120 * g), int(60 + 195 * g))
                px[x, y] = col + (255,)
        # el arco: zigzag de luz de arriba abajo, cambia en cada fotograma
        xs = [7, 8, 7, 6, 7, 8, 9, 8, 7, 6, 7, 8] if k % 2 == 0 else [8, 7, 8, 9, 8, 7, 6, 7, 8, 9, 8, 7]
        for i, y in enumerate(range(2, 14)):
            v = min(255, int(170 + 85 * b))
            px[xs[i], y] = (v, int(v * 0.86), 255, 255)
        frames.append(im)
    out = Image.new("RGBA", (16, 16 * len(frames)))
    for i, f in enumerate(frames):
        out.paste(f, (0, 16 * i))
    return out


def storm_glass():
    """Vidrio de tormenta: vidrio violeta translúcido con un borde claro y dos rayitas de brillo (patrón de vidrio)."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = (190, 150, 240, 200)
            else:
                px[x, y] = (150, 100, 220, 70)
    for (x, y) in ((3, 4), (4, 3), (5, 2), (11, 12), (12, 11)):
        px[x, y] = (236, 210, 255, 180)
    return im


ITEMS = {"arc_gland": arc_gland, "crystal_filament": crystal_filament, "copper_antenna": copper_antenna,
         "fulgurite_shard": fulgurite_shard}


def build(out):
    os.makedirs(out, exist_ok=True)
    imgs = []
    for name, fn in ITEMS.items():
        im = fn()
        im.save(os.path.join(out, name + ".png"))
        imgs.append(im)
    arc_lamp_frames(False).save(os.path.join(out, "arc_lamp.png"))
    arc_lamp_frames(True).save(os.path.join(out, "arc_lamp_storm.png"))
    storm_glass().save(os.path.join(out, "storm_glass.png"))
    sheet = Image.new("RGBA", (6 * 72 + 16, 88), (40, 30, 50, 255))
    for i, im in enumerate(imgs + [arc_lamp_frames(True).crop((0, 0, 16, 16)), storm_glass()]):
        sheet.alpha_composite(im.resize((64, 64), Image.NEAREST), (8 + i * 72, 12))
    sheet.save(os.path.join(out, "fulgor_items_preview.png"))
    print("ok")


if __name__ == "__main__":
    build(sys.argv[1])
