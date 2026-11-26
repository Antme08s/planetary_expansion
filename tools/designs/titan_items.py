"""Objetos de las criaturas de Titán (1.0.4), con el mismo estilo que items_v2 (luz arriba-izquierda, 6 tonos por material,
contorno del color del material). Uso: python3 -I tools/designs/titan_items.py <carpeta>  (escribe <nombre>.png y una
hoja de vista previa). La lámpara de bruma es un bloque con textura animada (4 fotogramas)."""
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from items_v2 import Canvas, ramp, T  # noqa: E402
from items_v2_draw import strip, meat_body  # noqa: E402


def ruminant_meat(cooked):
    """Costillar ancho: carne con dos costillas que asoman."""
    c = Canvas()
    bone = ramp((236, 224, 194))
    for (x0, y0) in ((9.5, 4.0), (12.0, 6.5)):
        b = Canvas()
        strip(b, (x0, y0), (x0 + 3.0, y0 - 3.0), 0.9, 0.8, bone, spec=False)
        b.ellipsoid(x0 + 3.2, y0 - 3.2, 1.1, 1.1, bone, spec=False)
        b.outline()
        c.merge(b)
    c.merge(meat_body(cooked, 7.0, 9.2, 6.0, 4.6, -0.55, "steak"))
    return c.image()


def tallow():
    """Grasa de bitumen: un bloque blando y brillante, casi negro, con un reflejo ámbar."""
    c = Canvas()
    rp = ramp((70, 50, 34))
    s = Canvas()
    s.ellipsoid(8.0, 9.2, 6.2, 4.6, rp, bounce=0.2)
    s.ellipsoid(6.0, 6.4, 3.2, 2.6, rp, bounce=0.2)
    s.ellipsoid(10.6, 6.8, 2.8, 2.3, rp, bounce=0.2)
    amber = ramp((214, 150, 70))
    for (x, y, k) in ((5, 5, 4), (6, 5, 5), (4, 6, 3), (9, 6, 3), (10, 5, 4), (5, 9, 3)):
        if s.get(x, y):
            s.set(x, y, amber[k])
    s.outline(factor=0.45)
    c.merge(s)
    return c.image()


def tholin_plate():
    """Placa de tolina: escama ocre gruesa con estrías."""
    c = Canvas()
    rp = ramp((182, 120, 60))
    s = Canvas()
    s.poly([(2.4, 4.0), (8.0, 1.6), (13.6, 4.0), (13.0, 11.0), (8.0, 14.6), (3.0, 11.0)], rp[3])
    s.poly([(2.4, 4.0), (8.0, 1.6), (8.0, 14.6), (3.0, 11.0)], rp[4])
    for y in (5, 8, 11):
        for x in range(3, 14):
            if s.get(x, y):
                s.set(x, y, rp[1] if x >= 8 else rp[2])
    for (x, y) in ((5, 3), (4, 4), (5, 6)):
        s.set(x, y, rp[5])
    s.outline(factor=0.42)
    c.merge(s)
    return c.image()


def eel(cooked):
    """Anguila: cuerpo largo en S, azul pálido con puntos naranjas (cruda) o dorada (cocinada)."""
    c = Canvas()
    rp = ramp((196, 126, 66)) if cooked else ramp((150, 192, 222))
    s = Canvas()
    pts = [(1.8, 12.6), (5.0, 10.2), (8.0, 11.0), (11.0, 7.0), (14.2, 3.6)]
    w = [0.7, 1.6, 1.8, 1.6, 1.0]
    for k in range(len(pts) - 1):
        strip(s, pts[k], pts[k + 1], w[k], w[k + 1], rp)
    s.ellipsoid(14.0, 3.4, 1.5, 1.3, rp)
    if not cooked:
        dots = ramp((255, 150, 60))
        for (x, y) in ((5, 10), (8, 10), (10, 7), (12, 5)):
            if s.get(x, y):
                s.set(x, y, dots[4])
        s.set(14, 3, (40, 50, 70))
    else:
        for (x, y) in ((5, 10), (8, 10), (11, 7)):
            if s.get(x, y):
                s.set(x, y, rp[1])
    s.outline(factor=0.42)
    c.merge(s)
    return c.image()


def cryo_scale():
    """Escama criogénica: escama translúcida azul hielo con escarcha y un borde blanco."""
    c = Canvas()
    rp = ramp((150, 206, 240))
    s = Canvas()
    s.poly([(8.0, 1.0), (12.8, 6.0), (11.6, 11.8), (8.0, 15.0), (4.4, 11.8), (3.2, 6.0)], rp[3])
    s.poly([(8.0, 1.0), (3.2, 6.0), (4.4, 11.8), (8.0, 15.0), (8.0, 8.0)], rp[4])
    s.poly([(8.0, 1.0), (12.8, 6.0), (11.6, 11.8), (8.0, 15.0), (8.0, 8.0)], rp[2])
    frost = ramp((236, 248, 255))
    s.line(8, 3, 8, 13, frost[3])
    for (x0, y0, x1, y1) in ((8, 6, 5, 8), (8, 6, 11, 8), (8, 9, 6, 11), (8, 9, 10, 11)):
        s.line(x0, y0, x1, y1, frost[2])
    for (x, y) in ((6, 4), (5, 6)):
        s.set(x, y, frost[5])
    s.outline(factor=0.4)
    c.merge(s)
    return c.image()


def _fang(c, ivory):
    pts = [(3.2, 2.8), (5.6, 5.0), (8.6, 8.2), (12.8, 13.8)]
    w = [2.1, 1.8, 1.0, 0.2]
    for k in range(3, 0, -1):
        s2 = Canvas()
        strip(s2, pts[k - 1], pts[k], w[k - 1], w[k], ivory)
        s2.outline(factor=0.45)
        c.merge(s2)


def frost_fang():
    c = Canvas()
    _fang(c, ramp((200, 230, 250)))
    root = ramp((90, 104, 128))
    cap = Canvas()
    strip(cap, (2.2, 1.8), (3.8, 3.4), 2.3, 2.1, root)
    cap.outline()
    c.merge(cap)
    for (x, y) in ((5, 5), (7, 7), (9, 10)):
        if c.get(x, y):
            c.set(x, y, (246, 252, 255))
    return c.image()


def ice_dagger():
    c = Canvas()
    blade = ramp((176, 222, 250))
    guard = ramp((88, 104, 132))
    wood = ramp((96, 66, 56))
    strip(c, (3.0, 13.0), (5.8, 10.2), 0.9, 0.9, wood, spec=False)
    c.ellipsoid(2.3, 13.7, 1.3, 1.3, guard)
    strip(c, (4.6, 7.8), (8.6, 11.8), 0.8, 0.8, guard)
    strip(c, (6.4, 9.6), (14.4, 1.6), 1.7, 0.4, blade)
    for (x, y) in ((9, 6), (11, 4)):
        if c.get(x, y):
            c.set(x, y, (250, 254, 255))
    c.outline()
    return c.image()


def ice_arrow():
    c = Canvas()
    wood = ramp((126, 88, 62))
    ice = ramp((180, 224, 250))
    feather = ramp((140, 160, 184))
    sh = Canvas()
    strip(sh, (2.2, 14.0), (11.6, 4.6), 0.5, 0.5, wood)
    sh.outline(factor=0.4)
    c.merge(sh)
    for tt in (0.04, 0.17, 0.30):
        px, py = 2.2 + 9.4 * tt, 14.0 - 9.4 * tt
        for (dx, dy) in ((-2.6, -1.6), (1.6, 2.6)):
            f = Canvas()
            strip(f, (px, py), (px + dx, py + dy), 0.75, 0.3, feather, spec=False)
            f.outline(factor=0.4)
            c.merge(f)
    sh2 = Canvas()
    strip(sh2, (2.2, 14.0), (11.6, 4.6), 0.5, 0.5, wood)
    sh2.outline(factor=0.4)
    c.merge(sh2)
    h = Canvas()
    strip(h, (10.8, 5.4), (14.8, 1.2), 1.7, 0.3, ice)
    h.set(12, 4, (250, 254, 255))
    h.outline(factor=0.42)
    c.merge(h)
    return c.image()


def mist_lantern():
    """Linterna de bruma: el órgano luminoso del acechador, con un trozo de antena."""
    c = Canvas()
    stalk = ramp((90, 104, 126))
    st = Canvas()
    strip(st, (9.0, 8.6), (2.6, 14.6), 1.0, 0.8, stalk)
    st.outline(factor=0.4)
    c.merge(st)
    rp = ramp((255, 196, 110))
    b = Canvas()
    b.ellipsoid(10.6, 5.4, 4.6, 4.6, rp, bounce=0.25)
    b.set(9, 3, (255, 252, 236)); b.set(10, 3, rp[5]); b.set(9, 4, rp[5])
    b.outline(factor=0.36)
    c.merge(b)
    return c.image()


def cryo_tonic():
    """Tónico criogénico: frasco con líquido azul helado y un tapón de grasa de bitumen."""
    c = Canvas()
    glass = ramp((200, 220, 232))
    liquid = ramp((96, 190, 236))
    b = Canvas()
    b.ellipsoid(8.0, 10.4, 4.8, 4.6, glass, bounce=0.2)
    b.poly([(6.4, 3.0), (9.6, 3.0), (9.6, 6.6), (6.4, 6.6)], glass[3])
    b.outline(factor=0.45)
    c.merge(b)
    l = Canvas()
    l.ellipsoid(8.0, 11.0, 3.8, 3.6, liquid, bounce=0.2, mask=lambda x, y: y >= 9)
    for (x, y) in ((6, 10), (7, 10)):
        l.set(x, y, liquid[5])
    c.merge(l)
    cork = ramp((70, 50, 34))
    k = Canvas()
    k.poly([(6.0, 1.0), (10.0, 1.0), (10.0, 3.2), (6.0, 3.2)], cork[3])
    k.line(6, 1, 9, 1, cork[4])
    c.merge(k)
    for (x, y) in ((5, 9), (5, 10)):
        c.set(x, y, (248, 252, 255))
    return c.image()


def mist_lamp_frames():
    """Lámpara de bruma (bloque): marco de acero oscuro y la linterna en el centro; 4 fotogramas que parpadean."""
    frames = []
    for k, bright in enumerate((1.0, 0.82, 0.95, 0.7)):
        im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
        px = im.load()
        steel = ramp((70, 78, 94))
        for y in range(16):
            for x in range(16):
                edge = x in (0, 15) or y in (0, 15)
                bar = x in (1, 14) or y in (1, 14)
                if edge:
                    col = steel[1]
                elif bar:
                    col = steel[3] if (x == 1 or y == 1) else steel[2]
                else:
                    d = math.hypot(x - 7.5, y - 7.5) / 7.0
                    g = max(0.0, 1.0 - d) * bright
                    base = (255 * (0.55 + 0.45 * g), 196 * (0.35 + 0.65 * g), 110 * (0.25 + 0.75 * g))
                    if (x in (5, 10) or y in (5, 10)) and 2 <= x <= 13 and 2 <= y <= 13:
                        base = steel[0]                                  # rejilla
                    col = tuple(int(min(255, v)) for v in base)
                px[x, y] = col + (255,)
        for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
            px[x, y] = steel[5] + (255,)
        frames.append(im)
    strip_im = Image.new("RGBA", (16, 16 * len(frames)))
    for i, f in enumerate(frames):
        strip_im.paste(f, (0, 16 * i))
    return strip_im


def tholin_plate_block():
    """Bloque de placas de tolina: escamas ocres que se solapan en filas (como tejas). Patrón regular de 8x8."""
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    rp = ramp((176, 116, 58))
    for y in range(16):
        for x in range(16):
            row = y // 4
            off = 4 if row % 2 else 0
            u = (x + off) % 8
            v = y % 4
            k = 3
            if v == 3:
                k = 1                                            # sombra bajo la fila
            elif v == 0:
                k = 4                                            # borde iluminado
            if u == 7:
                k = min(k, 2)                                    # junta entre placas
            if u == 0 and v < 3:
                k = 4
            px[x, y] = rp[k] + (255,)
    return im


ITEMS = {
    "raw_ruminant_meat": lambda: ruminant_meat(False),
    "cooked_ruminant_meat": lambda: ruminant_meat(True),
    "bitumen_tallow": tallow,
    "tholin_plate": tholin_plate,
    "raw_methane_eel": lambda: eel(False),
    "cooked_methane_eel": lambda: eel(True),
    "cryo_scale": cryo_scale,
    "frost_fang": frost_fang,
    "ice_dagger": ice_dagger,
    "ice_arrow": ice_arrow,
    "mist_lantern": mist_lantern,
    "cryo_tonic": cryo_tonic,
}


def ice_arrow_projectile():
    """Textura de la flecha de hielo en vuelo: la de la flecha de colmillo con la punta y las plumas de hielo."""
    import colorsys
    src = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mobs", "items_cerulea", "entity_fang_arrow.png")
    im = Image.open(src).convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if (v > 0.75 and s < 0.25) or 0.35 < h < 0.75:
                nr, ng, nb = colorsys.hsv_to_rgb(0.56, 0.25 + 0.3 * s, min(1, v * 1.02))
                px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    return im


def build(out):
    os.makedirs(out, exist_ok=True)
    ice_arrow_projectile().save(os.path.join(out, "entity_ice_arrow.png"))
    imgs = []
    for name, fn in ITEMS.items():
        im = fn()
        im.save(os.path.join(out, name + ".png"))
        imgs.append(im)
    mist_lamp_frames().save(os.path.join(out, "mist_lamp.png"))
    tholin_plate_block().save(os.path.join(out, "tholin_plate_block.png"))
    sheet = Image.new("RGBA", (len(imgs) * 72 + 16, 160), (40, 34, 30, 255))
    for i, im in enumerate(imgs):
        sheet.alpha_composite(im.resize((64, 64), Image.NEAREST), (8 + i * 72, 8))
    sheet.alpha_composite(mist_lamp_frames().crop((0, 0, 16, 16)).resize((64, 64), Image.NEAREST), (8, 88))
    tile = tholin_plate_block()
    big = Image.new("RGBA", (64, 64))
    for i in range(4):
        for j in range(4):
            big.paste(tile, (i * 16, j * 16))
    sheet.alpha_composite(big, (80, 88))
    sheet.save(os.path.join(out, "titan_items_preview.png"))
    print("ok")


if __name__ == "__main__":
    build(sys.argv[1])
