"""Items redibujados, lote 1 (1.0.4). Ver items_v2.py para las reglas de estilo."""
import math
import random

from items_v2 import Canvas, ramp, hexc, T


def strip(c, p0, p1, w0, w1, rp, lit=(-0.7071, -0.7071), spec=True, bands=()):
    """Trazo ahusado de p0 (ancho w0) a p1 (ancho w1): el lado que mira a la luz sale claro y el otro oscuro."""
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    ln = math.hypot(dx, dy)
    ax, ay = dx / ln, dy / ln
    nx, ny = -ay, ax
    if nx * lit[0] + ny * lit[1] < 0:
        nx, ny = -nx, -ny                                   # n apunta hacia la luz
    for y in range(T):
        for x in range(T):
            px_, py_ = x + 0.5 - p0[0], y + 0.5 - p0[1]
            t = (px_ * ax + py_ * ay) / ln
            if t < -0.02 or t > 1.0:
                continue
            w = w0 + (w1 - w0) * t
            s = px_ * nx + py_ * ny
            if abs(s) > w:
                continue
            k = s / w                                       # -1 (sombra) .. 1 (luz)
            idx = 3 + int(round(k * 1.7))
            idx = max(1, min(4, idx))
            if spec and k > 0.55 and 0.15 < t < 0.8 and w >= 1.0:
                idx = 5 if (round(t * ln) % 3 == 0) else 4
            col = rp[idx]
            for (t0, half, brp) in bands:                       # franjas de color pintadas sobre el trazo
                if abs(t - t0) * ln <= half:
                    col = brp[max(1, min(5, idx))]
            c.set(x, y, col)


def _inside_dist(m, pts):
    """Distancia (en pasos de 4 vecinos) de cada pixel de la pieza a su borde."""
    dist = {}
    layer = [p for p in pts if any((p[0] + dx, p[1] + dy) not in pts for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for p in layer:
        dist[p] = 1
    d = 1
    while layer:
        nxt = []
        for p in layer:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (p[0] + dx, p[1] + dy)
                if q in pts and q not in dist:
                    dist[q] = d + 1
                    nxt.append(q)
        layer, d = nxt, d + 1
    return dist


def _paint(m, cells, color, only_inside=True):
    for (x, y) in cells:
        if m.get(x, y):
            m.set(x, y, color)


def meat_body(cooked, cx, cy, rx, ry, tilt, shape):
    """Cuerpo de la carne con su detalle. shape: 'steak' (cuerpo con grasa) o 'drum' (muslo)."""
    m = Canvas()
    base = (176, 94, 50) if cooked else (212, 82, 96)
    rp = ramp(base)
    m.ellipsoid(cx, cy, rx, ry, rp, tilt=tilt)
    pts = set(m.px)
    dist = _inside_dist(m, pts)
    top = [p for p in pts if p[1] + 0.5 < cy - 0.2 * ry]
    if cooked:
        gold = ramp((232, 176, 82))
        crust = ramp((120, 58, 30))
        for p, dd in dist.items():                          # costra: borde doscuro abajo/derecha, dorado arriba/izquierda
            x, y = p
            lower_right = (x + 0.5 - cx) + (y + 0.5 - cy) > 0.5
            if dd == 1:
                m.set(x, y, crust[1] if lower_right else crust[3])
            elif dd == 2 and lower_right:
                m.set(x, y, rp[1])
        if shape == "steak":
            for (x, y) in [(3, 6), (4, 7), (5, 8), (6, 9), (7, 10), (5, 5), (6, 6), (7, 7), (8, 8), (9, 9),
                           (8, 4), (9, 5), (10, 6), (11, 7), (4, 10), (5, 11), (6, 12)]:
                if m.get(x, y) and dist.get((x, y), 9) > 1:
                    m.set(x, y, rp[0])                      # marcas de parrilla cruzadas
                    if m.get(x, y - 1) and dist.get((x, y - 1), 9) > 1 and (x, y - 1) not in ():
                        q = m.get(x, y - 1)
                        if q not in (rp[0],):
                            m.set(x, y - 1, rp[4])          # filo claro sobre la marca
            for (x, y) in ((6, 4), (4, 5), (9, 7)):          # grasa dorada y brillante
                if m.get(x, y):
                    m.set(x, y, gold[4])
            for (x, y) in ((5, 4), (7, 5), (3, 8), (10, 8)):
                if m.get(x, y):
                    m.set(x, y, rp[5])
            for (x, y) in ((8, 10), (5, 12), (9, 11)):       # zonas quemadas
                if m.get(x, y) and dist.get((x, y), 9) > 1:
                    m.set(x, y, crust[2])
        else:
            for (x, y) in ((4, 3), (3, 5), (6, 3)):          # solo brillos y un par de zonas tostadas
                if m.get(x, y):
                    m.set(x, y, rp[5])
            for (x, y) in ((8, 8), (5, 8)):
                if m.get(x, y) and dist.get((x, y), 9) > 1:
                    m.set(x, y, crust[2])
    else:
        fat = ramp((246, 226, 214))
        if shape == "steak":
            for p, dd in dist.items():                       # borde de grasa alrededor, menos donde sale el hueso
                if dd != 1 or (p[0] >= 11 and 6 <= p[1] <= 11):
                    continue
                lower_right = (p[0] + 0.5 - cx) + (p[1] + 0.5 - cy) > 1.0
                m.set(p[0], p[1], fat[2] if lower_right else (fat[4] if (p[0] + p[1]) % 2 else fat[3]))
            veins = [(3, 8), (4, 8), (5, 7), (6, 7), (7, 6), (8, 5), (5, 9), (4, 10),
                     (7, 10), (8, 10), (9, 9), (10, 9), (8, 11), (6, 5), (10, 7)]
            for (x, y) in veins:
                if m.get(x, y) and dist.get((x, y), 9) > 1:
                    m.set(x, y, fat[4] if (x + y) % 3 else fat[3])
                    if (x + y) % 3 == 0 and m.get(x, y + 1) and dist.get((x, y + 1), 9) > 1 and m.get(x, y + 1) not in fat:
                        m.set(x, y + 1, rp[3])           # halo rosado bajo la grasa
            for (x, y) in ((4, 6), (7, 8), (9, 11), (5, 5)):  # brillo humedo
                if m.get(x, y) and m.get(x, y) not in fat:
                    m.set(x, y, rp[5])
            for (x, y) in ((6, 9), (9, 7), (4, 9)):          # carne mas oscura entre la grasa
                if m.get(x, y) and m.get(x, y) not in fat:
                    m.set(x, y, rp[1])
        else:
            for (x, y) in ((4, 3), (3, 5), (6, 4)):          # solo brillo humedo
                if m.get(x, y):
                    m.set(x, y, rp[5])
    m.outline()
    return m


def meat(cooked):
    c = Canvas()
    bone = ramp((236, 224, 194))
    b = Canvas()
    b.poly([(10.6, 7.6), (13.8, 7.6), (13.8, 9.7), (10.6, 9.7)], bone[3])
    b.line(11, 7, 13, 7, bone[4])
    b.line(11, 9, 13, 9, bone[2])
    b.ellipsoid(13.7, 7.1, 1.4, 1.4, bone, spec=False)
    b.ellipsoid(13.7, 10.2, 1.4, 1.4, bone, spec=False)
    b.outline()
    c.merge(b)
    c.merge(meat_body(cooked, 6.8, 8.4, 5.7, 5.0, -0.18, "steak"))
    return c.image()


def drumstick(cooked):
    c = Canvas()
    bone = ramp((236, 224, 194))
    b = Canvas()
    strip(b, (7.6, 7.6), (13.0, 13.0), 1.2, 1.1, bone)
    b.ellipsoid(14.1, 12.2, 1.4, 1.4, bone, spec=False)
    b.ellipsoid(12.2, 14.1, 1.4, 1.4, bone, spec=False)
    b.outline()
    c.merge(b)
    c.merge(meat_body(cooked, 5.6, 5.6, 4.7, 4.4, 0.3, "drum"))
    return c.image()


def crystal(base=(56, 148, 232)):
    c = Canvas()
    rp = ramp(base)
    C = (8.0, 8.4)
    top, bot = (8.0, 1.2), (8.0, 14.8)
    ul, ur, ll, lr = (3.8, 5.2), (12.2, 5.2), (3.8, 10.4), (12.2, 10.4)
    for pts, idx in (([top, ul, C], 4), ([top, ur, C], 3), ([ul, ll, C], 3), ([ur, lr, C], 1),
                     ([ll, bot, C], 2), ([lr, bot, C], 0)):
        c.poly(pts, rp[idx])
    for (x, y) in ((6, 3), (6, 4), (5, 5), (5, 6)):         # arista brillante del lado de la luz
        if c.get(x, y):
            c.set(x, y, rp[5])
    c.set(5, 4, hexc("#f2fcff"))
    c.set(9, 6, rp[4])
    c.line(8, 8, 8, 13, rp[2])                              # arista central en sombra
    c.outline(factor=0.38)
    return c.image()


def _lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def ice_shard(c, base, tip, w, rp):
    """Prisma de hielo inclinado de base a punta: cara izquierda clara, derecha en sombra, faceta de la punta,
    arista brillante del lado de la luz. Cada prisma lleva su propio contorno."""
    s = Canvas()
    ax, ay = tip[0] - base[0], tip[1] - base[1]
    ln = math.hypot(ax, ay)
    ux, uy = ax / ln, ay / ln
    nx, ny = uy, -ux                                       # normal hacia la izquierda de la direccion base->punta
    if nx > 0:
        nx, ny = -nx, -ny                                  # izquierda = lado de la luz
    bl, br = (base[0] + nx * w, base[1] + ny * w), (base[0] - nx * w, base[1] - ny * w)
    sh = _lerp(base, tip, 0.70)
    sl, sr = (sh[0] + nx * w * 0.92, sh[1] + ny * w * 0.92), (sh[0] - nx * w * 0.92, sh[1] - ny * w * 0.92)
    mid_b, mid_s = base, sh
    s.poly([bl, sl, tip, mid_b], rp[4])                    # cara de la luz
    s.poly([mid_b, tip, sr, br], rp[2])                    # cara en sombra
    s.poly([sl, tip, _lerp(sl, tip, 0.5), mid_s], rp[5])   # faceta clara de la punta (lado luz)
    s.poly([mid_s, tip, sr], rp[3])                        # faceta de la punta (lado sombra)
    n = int(ln * 2)
    for k in range(n):                                     # arista brillante en el borde de la luz
        t = k / n
        if 0.1 < t < 0.65:
            p = _lerp((bl[0] + nx * 0.0, bl[1]), sl, t / 0.65)
            s.set(int(p[0] + 0.55), int(p[1]), rp[5])
    s.outline(factor=0.34)
    c.merge(s)


def cryo():
    c = Canvas()
    rp = ramp((140, 208, 244))
    ice_shard(c, (4.6, 14.2), (1.4, 5.6), 2.0, rp)
    ice_shard(c, (11.4, 14.2), (14.8, 6.4), 2.0, rp)
    ice_shard(c, (8.0, 14.6), (8.8, 0.9), 2.6, rp)
    ice_shard(c, (6.0, 15.2), (5.2, 9.6), 1.3, rp)
    snow = ramp((226, 240, 250))
    for x in range(3, 14):
        if c.get(x, 15) is None and c.get(x, 14):
            pass
    for (x, y, k) in ((4, 14, 4), (5, 14, 3), (9, 14, 4), (10, 14, 3), (12, 14, 4), (7, 14, 3)):
        c.set(x, y, snow[k])
    return c.image()


def dagger():
    c = Canvas()
    bone = ramp((238, 228, 204))
    metal = ramp((70, 120, 170))
    wood = ramp((96, 66, 56))
    strip(c, (3.0, 13.0), (5.8, 10.2), 0.9, 0.9, wood, spec=False)
    c.ellipsoid(2.3, 13.7, 1.3, 1.3, metal)
    strip(c, (4.6, 7.8), (8.6, 11.8), 0.8, 0.8, metal)
    strip(c, (6.4, 9.6), (14.4, 1.6), 1.7, 0.4, bone)
    c.outline()
    return c.image()


def magma_core():
    c = Canvas()
    rp = ramp((238, 108, 28), sat=1.05)
    c.ellipsoid(8, 8, 6.4, 6.4, rp)
    crust = ramp((70, 38, 36))
    rng = random.Random(8)
    for y in range(T):
        for x in range(T):
            q = c.get(x, y)
            if not q:
                continue
            r = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if r > 3.6 and rng.random() < (0.55 if (x + y) > 14 else 0.28) and (x * 3 + y * 5) % 4 != 0:
                c.set(x, y, crust[2 if (x + y) > 14 else 3])
    core = ramp((255, 214, 90))
    for (x, y, i) in ((6, 6, 4), (7, 6, 4), (6, 7, 4), (7, 7, 3), (5, 5, 5), (8, 8, 2)):
        c.set(x, y, core[i])
    c.outline(factor=0.34)
    return c.image()


def blueberries():
    c = Canvas()
    leaf = ramp((60, 170, 100))
    l = Canvas()
    strip(l, (8.0, 6.0), (10.6, 2.0), 0.6, 0.5, leaf, spec=False)
    l.ellipsoid(11.8, 2.9, 2.0, 1.2, leaf, tilt=-0.5)
    l.outline(factor=0.4)
    c.merge(l)
    rp = ramp((52, 84, 208))
    cal = ramp((26, 30, 100))
    for (cx, cy, r) in ((8.0, 6.8, 3.4), (4.6, 11.0, 3.2), (11.2, 10.8, 3.2)):
        b = Canvas()
        b.ellipsoid(cx, cy, r, r, rp)
        b.set(int(cx) - 1, int(cy - r + 0.9), cal[2])
        b.set(int(cx), int(cy - r + 0.9), cal[1])
        b.outline(factor=0.36)
        c.merge(b)
    return c.image()


BATCH1 = {
    "cooked_grazer_meat": lambda: meat(True),
    "grazer_meat": lambda: meat(False),
    "cooked_hopper_meat": lambda: drumstick(True),
    "hopper_meat": lambda: drumstick(False),
    "cerulean_crystal": crystal,
    "cryo_crystal": cryo,
    "fang_dagger": dagger,
    "magma_core": magma_core,
    "blueberries": blueberries,
}


# ====================================================================== lote 2: carnes de mar y de croaker

def crab_meat(cooked):
    """Filete de carne de cangrejo: fibras blancas en haces, con borde y brillo."""
    c = Canvas()
    base = (232, 112, 62) if cooked else (238, 150, 154)
    rp = ramp(base)
    m = Canvas()
    m.ellipsoid(8.0, 8.4, 6.6, 4.7, rp, tilt=-0.12)
    pts = set(m.px)
    dist = _inside_dist(m, pts)
    fib = ramp((255, 238, 226) if not cooked else (255, 222, 170))
    rows = {5: (4, 11), 6: (3, 12), 7: (3, 12), 8: (3, 12), 9: (4, 12), 10: (5, 11)}
    for y, (x0, x1) in rows.items():                       # haces de fibra: tramos de 2-3 pixeles alternados
        for x in range(x0, x1 + 1):
            if (x + y * 2) % 5 in (0, 1) and m.get(x, y) and dist.get((x, y), 9) > 1:
                m.set(x, y, fib[4] if (x + y) % 2 else fib[3])
    edge = ramp((196, 70, 40) if cooked else (214, 96, 110))
    for p, dd in dist.items():                             # borde: capa mas oscura abajo y a la derecha
        if dd == 1:
            lower_right = (p[0] + 0.5 - 8) + (p[1] + 0.5 - 8.4) > 1.0
            m.set(p[0], p[1], edge[1] if lower_right else edge[3])
    for (x, y) in ((5, 4), (7, 4), (4, 6)):
        if m.get(x, y):
            m.set(x, y, rp[5])
    m.outline()
    c.merge(m)
    return c.image()


def king_leg(cooked):
    """Pata de rey cangrejo: caparazon largo y ahusado con franjas de color y espinas; la carne asoma por los dos
    extremos (blanca si esta cocinada, rosada si esta cruda)."""
    c = Canvas()
    shell = ramp((222, 78, 44) if cooked else (48, 88, 206))
    acc = ramp((255, 196, 120) if cooked else (250, 150, 70))
    flesh = ramp((250, 236, 226))                            # carne palida en las dos (cruda y cocinada)
    for (x, y) in ((2.5, 13.5), (13.1, 2.5)):              # carne en los dos extremos (detras del caparazon)
        e = Canvas()
        e.ellipsoid(x, y, 1.7, 1.7, flesh)
        e.set(int(x) + (0 if x < 8 else -1), int(y) + (0 if x < 8 else 1), flesh[2])
        e.outline(factor=0.5)
        c.merge(e)
    body = Canvas()
    strip(body, (3.6, 12.4), (12.4, 3.6), 1.7, 1.0, shell, bands=((0.30, 0.9, acc), (0.62, 0.9, acc)))
    body.outline()
    c.merge(body)
    for (x, y) in ((5, 8), (9, 10), (8, 4), (11, 6)):      # espinas a ambos lados
        if not c.get(x, y):
            c.set(x, y, shell[1])
    return c.image()


def croaker_legs(cooked):
    """Dos patas de rana cruzadas: muslo grueso, pierna fina y pie con dedos."""
    c = Canvas()
    base = (186, 116, 58) if cooked else (62, 170, 150)
    rp = ramp(base)
    legs = [((2.6, 2.6), (7.6, 6.8), (12.6, 12.8), +1), ((13.2, 2.6), (8.2, 6.8), (3.2, 12.8), -1)]
    for (top, knee, foot, side) in legs:
        s = Canvas()
        strip(s, top, knee, 1.7, 1.5, rp)
        strip(s, knee, foot, 1.3, 0.8, rp)
        s.ellipsoid(knee[0], knee[1], 1.6, 1.6, rp)
        for k in (-1, 0, 1):                               # dedos del pie
            s.set(int(foot[0]) + (1 if side > 0 else -1) * (1 + (k == 0)), int(foot[1]) + k, rp[2])
        if not cooked:
            spot = ramp((40, 90, 190))
            s.set(int(top[0] + 2), int(top[1] + 1), spot[4])
            s.set(int(knee[0]) + side, int(knee[1]) + 2, spot[3])
        else:
            crisp = ramp((120, 60, 28))
            s.set(int(top[0] + 2), int(top[1] + 2), crisp[1])
            s.set(int(knee[0]) + side, int(knee[1]) + 2, rp[5])
        s.outline()
        c.merge(s)
    return c.image()


BATCH2 = {
    "crab_meat": lambda: crab_meat(False),
    "cooked_crab_meat": lambda: crab_meat(True),
    "king_crab_leg": lambda: king_leg(False),
    "cooked_king_crab_leg": lambda: king_leg(True),
    "croaker_legs": lambda: croaker_legs(False),
    "cooked_croaker_legs": lambda: croaker_legs(True),
}


# ====================================================================== lote 3: pescados, cubetas, cueros, colmillos

def recolor(c, region, src_rp, dst_rp):
    """Cambia, dentro de region(x, y), cada tono de src_rp por el del mismo nivel de dst_rp."""
    for (x, y), col in list(c.px.items()):
        if col in src_rp and region(x, y):
            c.set(x, y, dst_rp[src_rp.index(col)])


def _tail(c, x0, y0, spread, length, rp, notch=1.6, ribs=3):
    """Aleta caudal en abanico hacia la derecha: panel claro arriba, oscuro abajo, con radios."""
    t = Canvas()
    t.poly([(x0, y0 - 0.9), (x0 + length, y0 - spread), (x0 + length - notch, y0), (x0 + length, y0 + spread), (x0, y0 + 0.9)], rp[3])
    t.poly([(x0, y0 - 0.9), (x0 + length, y0 - spread), (x0 + length - notch, y0), (x0, y0)], rp[4])
    for k in range(ribs):
        f = (k + 1) / (ribs + 1)
        y1 = y0 - spread + 2 * spread * f
        t.line(x0 + 1.2, y0, x0 + length - 1, y1, rp[1] if y1 > y0 else rp[2])
    t.outline()
    c.merge(t)


def lantern_fish(cooked):
    c = Canvas()
    body_rp = ramp((176, 104, 56) if cooked else (36, 58, 112))
    belly_rp = ramp((236, 206, 150) if cooked else (74, 156, 178))
    fin_rp = ramp((150, 80, 40) if cooked else (28, 44, 92))
    glow = ramp((250, 206, 100) if cooked else (110, 250, 236))
    _tail(c, 11.4, 8.4, 3.4, 4.0, fin_rp)
    d = Canvas()
    d.poly([(5.6, 5.8), (8.0, 3.2), (11.4, 6.4), (11.0, 7.6)], fin_rp[3])             # aleta dorsal
    d.poly([(5.6, 5.8), (8.0, 3.2), (8.6, 6.0)], fin_rp[4])
    d.outline()
    c.merge(d)
    b = Canvas()
    b.ellipsoid(7.6, 8.8, 5.4, 3.5, body_rp)
    recolor(b, lambda x, y: y + 0.5 > 9.6, body_rp, belly_rp)                          # vientre claro
    pts = set(b.px)
    dist = _inside_dist(b, pts)
    if cooked:
        for (x, y), dd in dist.items():
            if (x + y) % 4 == 0 and dd > 1 and y < 10:
                b.set(x, y, body_rp[1])                                                # piel dorada, escamas tostadas
    else:
        for x in (5, 7, 9, 11):                                                        # fotoforos del vientre
            if b.get(x, 11):
                b.set(x, 11, glow[4])
    for (x, y) in ((6, 7), (8, 6)):
        if b.get(x, y):
            b.set(x, y, body_rp[5])
    b.set(4, 8, (250, 250, 250))                                                       # ojo
    b.set(5, 8, (20, 20, 40))
    for (x, y) in ((2, 10), (3, 11), (4, 11)):                                         # boca con dientes
        if not b.get(x, y):
            b.set(x, y, (240, 240, 230))
    b.outline()
    c.merge(b)
    s = Canvas()                                                                        # senuelo
    strip(s, (4.6, 5.6), (2.6, 2.6), 0.45, 0.45, fin_rp, spec=False)
    bulb = Canvas()
    bulb.ellipsoid(2.0, 2.2, 1.7, 1.7, glow)
    bulb.outline(factor=0.5)
    c.merge(s)
    c.merge(bulb)
    return c.image()


def ribbonfin(cooked):
    c = Canvas()
    body_rp = ramp((204, 134, 62) if cooked else (52, 122, 216))
    belly_rp = ramp((244, 220, 170) if cooked else (150, 214, 236))
    rib = ramp((220, 128, 52) if cooked else (70, 226, 172))
    for (x0, y0, x1, y1, w) in ((11.2, 8.4, 15.4, 3.0, 0.9), (11.2, 8.6, 15.6, 8.6, 0.9), (11.2, 8.8, 15.2, 13.6, 0.9)):
        r = Canvas()
        strip(r, (x0, y0), (x1, y1), w, 0.5, rib, spec=False)
        r.outline()
        c.merge(r)
    dr = Canvas()
    strip(dr, (5.4, 5.8), (11.4, 1.6), 0.9, 0.5, rib, spec=False)                       # cinta dorsal
    dr.outline()
    c.merge(dr)
    b = Canvas()
    b.ellipsoid(7.4, 8.8, 5.0, 3.4, body_rp)
    recolor(b, lambda x, y: y + 0.5 > 9.7, body_rp, belly_rp)
    pts = set(b.px)
    dist = _inside_dist(b, pts)
    if cooked:
        for (x, y), dd in dist.items():                                                 # marcas de parrilla
            if (x - y) % 5 == 0 and dd > 1 and 4 < x < 11:
                b.set(x, y, body_rp[0])
    else:
        for (x, y) in ((8, 7), (10, 8), (9, 10), (7, 9)):                               # escamas brillantes
            if b.get(x, y):
                b.set(x, y, body_rp[5])
    for (x, y) in ((5, 7), (7, 6)):
        if b.get(x, y):
            b.set(x, y, body_rp[5])
    b.set(4, 8, (250, 250, 250))
    b.set(4, 9, (20, 20, 40))
    for y in (8, 9, 10):                                                                 # branquia
        if b.get(6, y):
            b.set(6, y, body_rp[1])
    b.outline()
    c.merge(b)
    return c.image()


def hide(striped):
    """Cuero (liso) o piel rayada: piel extendida con cuerpo, cuatro patas y cuello."""
    c = Canvas()
    base = (58, 120, 150) if striped else (74, 146, 160)
    rp = ramp(base, sat=1.0)
    h = Canvas()
    h.ellipsoid(8.0, 8.4, 3.8, 4.9, rp)
    for (x0, y0, x1, y1, w) in ((6.6, 5.4, 2.4, 3.2, 1.5), (9.4, 5.4, 13.6, 3.2, 1.5), (6.6, 11.6, 2.6, 13.6, 1.5),
                                (9.4, 11.6, 13.4, 13.6, 1.5), (8.0, 4.4, 8.0, 1.8, 1.4)):
        strip(h, (x0, y0), (x1, y1), w, w * 0.7, rp, spec=False)
    pts = set(h.px)
    dist = _inside_dist(h, pts)
    if striped:
        dark = ramp((22, 44, 78))
        for (x, y) in list(h.px):
            if (x * 2 + y) % 5 in (0, 1) and dist.get((x, y), 9) > 1 and y > 4:
                h.set(x, y, dark[3] if (x + y) % 2 else dark[2])
    else:
        for (x, y) in ((7, 7), (9, 9), (6, 10), (10, 7)):                              # arrugas
            if h.get(x, y):
                h.set(x, y, rp[1])
        for (x, y) in ((7, 6), (6, 8)):
            if h.get(x, y):
                h.set(x, y, rp[5])
    h.outline()
    c.merge(h)
    return c.image()


def glider_membrane():
    c = Canvas()
    rp = ramp((64, 112, 222))
    m = Canvas()
    m.poly([(2.0, 3.0), (14.4, 4.4), (13.6, 10.0), (11.0, 8.4), (9.4, 13.2), (7.2, 9.6), (4.8, 12.6)], rp[3])
    m.poly([(2.0, 3.0), (14.4, 4.4), (11.0, 8.4), (7.2, 9.6), (4.8, 12.6)], rp[4])      # panel mas claro
    m.poly([(11.0, 8.4), (13.6, 10.0), (14.4, 4.4)], rp[2])
    for (x, y) in ((6, 7), (8, 6), (10, 5), (5, 5), (11, 7)):                           # brillos de la membrana
        if m.get(x, y):
            m.set(x, y, rp[5])
    bone = ramp((40, 70, 150))
    for (x1, y1) in ((4.8, 12.2), (7.2, 9.4), (11.0, 8.2), (13.4, 9.6)):               # nervaduras
        m.line(2, 3, x1, y1, bone[1])
    m.outline()
    s = Canvas()
    strip(s, (1.6, 2.6), (14.6, 4.2), 0.8, 0.6, ramp((44, 70, 160)))                    # borde de ataque
    s.outline()
    c.merge(m)
    c.merge(s)
    return c.image()


_BUCKET_BASE = None


def _bucket_base():
    """Barril original del mod (sin el contenido), guardado en items_v2_assets/bucket_base.png."""
    global _BUCKET_BASE
    if _BUCKET_BASE is None:
        import os
        from PIL import Image
        _BUCKET_BASE = Image.open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "items_v2_assets", "bucket_base.png")).convert("RGBA")
    return _BUCKET_BASE.copy()


def _mouth(base, water_rp, rim_rp):
    """Boca del barril: aro de metal, agua adentro. Devuelve (base con aro trasero+agua, capa del borde delantero)."""
    from PIL import Image
    cx, cy = 7.5, 5.5
    front = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dark = base.getpixel((2, 5))
    for y in range(16):
        for x in range(16):
            ro = ((x - cx) / 5.8) ** 2 + ((y - cy) / 3.0) ** 2
            ri = ((x - cx) / 4.5) ** 2 + ((y - cy + 0.1) / 2.0) ** 2
            if ro <= 1.0:
                if ro > 0.78:
                    col = dark
                elif ri > 1.0:
                    col = rim_rp[4] if (x + y < 12) else rim_rp[3]
                else:
                    t = 0.0 if (y < cy - 0.6) else 1.0
                    col = water_rp[3] if t < 0.5 else water_rp[2]
                    if (x * 3 + y * 5) % 11 == 0:
                        col = water_rp[4]
                base.putpixel((x, y), tuple(col) + (255,) if len(col) == 3 else col)
                if y >= cy + 0.4 and (ro > 0.78 or ri > 1.0):
                    front.putpixel((x, y), tuple(col) + (255,) if len(col) == 3 else col)
    return base, front


def bucket(kind):
    """La cubeta de siempre (barril gris con gotas) con el pez o la rana nadando dentro, tapados por el borde delantero."""
    from PIL import Image
    base = _bucket_base()
    # limpiar la zona de la boca del barril original para redibujarla
    for y in range(1, 8):
        for x in range(3, 13):
            base.putpixel((x, y), (0, 0, 0, 0))
    water_rp = ramp((58, 150, 232))
    rim_rp = ramp((166, 170, 182))
    base, front = _mouth(base, water_rp, rim_rp)
    f = Canvas()
    if kind == "lantern":
        rp = ramp((48, 80, 150)); fin = ramp((34, 56, 112)); glow = ramp((110, 250, 236))
        _tail(f, 10.4, 3.8, 1.8, 2.2, fin, notch=0.8, ribs=1)
        f.ellipsoid(7.4, 3.9, 3.9, 2.5, rp)
        recolor(f, lambda x, y: y > 4.6, rp, ramp((74, 156, 178)))
        f.set(5, 3, (250, 250, 250)); f.set(6, 3, (20, 20, 40))
        for x in (4, 6):
            f.set(x, 5, glow[4])
        f.outline()
        strip(f, (5.6, 1.8), (3.8, 0.6), 0.4, 0.4, fin, spec=False)
        l = Canvas(); l.ellipsoid(3.2, 1.0, 1.2, 1.2, glow); l.outline(factor=0.5)
        f.merge(l)
    elif kind == "ribbonfin":
        rp = ramp((52, 122, 216)); rib = ramp((70, 226, 172))
        for (x1, y1) in ((13.2, 1.6), (13.6, 3.6)):
            strip(f, (10.4, 3.8), (x1, y1), 0.8, 0.4, rib, spec=False)
        strip(f, (5.4, 2.0), (9.6, 0.4), 0.8, 0.4, rib, spec=False)
        f.ellipsoid(7.4, 3.9, 3.9, 2.5, rp)
        recolor(f, lambda x, y: y > 4.8, rp, ramp((150, 214, 236)))
        f.set(5, 3, (250, 250, 250)); f.set(5, 4, (20, 20, 40))
        f.set(7, 3, rp[1]); f.set(7, 4, rp[1])
        f.outline()
    else:
        rp = ramp((84, 196, 160))
        f.ellipsoid(7.8, 4.2, 4.2, 2.6, rp)
        recolor(f, lambda x, y: y > 4.9, rp, ramp((172, 226, 188)))
        for ex in (5.4, 10.2):
            e = Canvas(); e.ellipsoid(ex, 2.0, 1.5, 1.5, rp)
            e.set(int(ex), 1, (250, 214, 90)); e.set(int(ex) + 1, 1, (20, 20, 40)); e.set(int(ex), 2, (20, 20, 40))
            e.outline()
            f.merge(e)
        f.line(5, 5, 10, 5, rp[1])
        f.set(7, 3, rp[5]); f.set(9, 3, rp[5])
        f.outline()
    fish = f.image()
    wl = 4                                                   # fila de la superficie del agua
    wtone = (58, 150, 232)

    def inside(x, y):                                        # dentro del agua de la boca
        return ((x - 7.5) / 4.5) ** 2 + ((y - 5.4) / 2.0) ** 2 <= 1.0

    sub = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            r, g, bl, al = fish.getpixel((x, y))
            if al == 0:
                continue
            if y >= wl:                                      # sumergido: el agua lo tine poco a poco, sin salto en la superficie
                if y > wl and not inside(x, y):
                    continue
                depth = y - wl
                k = (0.14, 0.28, 0.44, 0.58, 0.68, 0.72)[min(depth, 5)]
                if (x + y) % 2 == 0 and depth < 3:           # trama alternada en la zona de transicion
                    k += 0.10
                dark = 1.0 - 0.05 * min(depth, 4)
                r = int((r * (1 - k) + wtone[0] * k) * dark)
                g = int((g * (1 - k) + wtone[1] * k) * dark)
                bl = int((bl * (1 - k) + wtone[2] * k) * (1.0 - 0.02 * min(depth, 4)))
            sub.putpixel((x, y), (r, g, bl, 255))
    out = Image.alpha_composite(base, sub)
    # ondas de la superficie: arcos continuos alrededor del pez, con tramos de largo distinto
    import random as _r, math as _m
    rr = _r.Random({"lantern": 11, "ribbonfin": 23}.get(kind, 37))
    xs = [x for x in range(16) if sub.getpixel((x, wl))[3] and sub.getpixel((x, wl + 1))[3]]
    if xs:
        cxf = (min(xs) + max(xs)) / 2.0
        for (dy, rx_extra, ry, col, gap_p) in ((0.6, 1.6, 1.1, water_rp[5], 0.30), (1.9, 2.6, 1.0, water_rp[4], 0.45)):
            rx = (max(xs) - min(xs)) / 2.0 + rx_extra
            skip = 0
            for xi in range(int(cxf - rx) - 1, int(cxf + rx) + 2):
                t = (xi - cxf) / rx
                if abs(t) > 1.0:
                    continue
                yy = int(round(wl + dy + ry * _m.sqrt(max(0.0, 1 - t * t))))
                if skip > 0:
                    skip -= 1
                    continue
                if rr.random() < gap_p:
                    skip = rr.choice((1, 1, 2))
                    continue
                if inside(xi, yy):
                    px = sub.getpixel((xi, yy))
                    if px[3] == 0:
                        out.putpixel((xi, yy), tuple(col) + (255,))
    out = Image.alpha_composite(out, front)
    return out


def fang(root, big=False):
    """Colmillo curvo de marfil: base ancha con una banda corta de color (raiz), punta afilada."""
    c = Canvas()
    ivory = ramp((240, 232, 212))
    rt = ramp(root)
    pts = [(3.2, 2.8), (5.6, 5.0), (8.6, 8.2), (12.8, 13.8)]
    w = [2.5 if big else 2.1, 2.1 if big else 1.8, 1.3 if big else 1.0, 0.2]
    for k in range(3, 0, -1):
        s2 = Canvas()
        strip(s2, pts[k - 1], pts[k], w[k - 1], w[k], ivory)
        s2.outline(factor=0.45)
        c.merge(s2)
    cap = Canvas()
    strip(cap, (2.2, 1.8), (3.8, 3.4), w[0] + 0.2, w[0], rt)
    cap.outline()
    c.merge(cap)
    for (x, y) in ((5, 5), (7, 7)):
        if c.get(x, y) in ivory[2:5]:
            c.set(x, y, ivory[5])
    return c.image()


BATCH3 = {
    "lantern_fish": lambda: lantern_fish(False),
    "cooked_lantern_fish": lambda: lantern_fish(True),
    "ribbonfin": lambda: ribbonfin(False),
    "cooked_ribbonfin": lambda: ribbonfin(True),
    "lantern_fish_bucket": lambda: bucket("lantern"),
    "ribbonfin_bucket": lambda: bucket("ribbonfin"),
    "croaker_bucket": lambda: bucket("croaker"),
    "cerulean_leather": lambda: hide(False),
    "striped_pelt": lambda: hide(True),
    "glider_membrane": glider_membrane,
    "abyssal_fang": lambda: fang((196, 52, 64), big=True),
    "lurker_fang": lambda: fang((96, 64, 92)),
    "runner_fang": lambda: fang((92, 50, 70)),
}


# ====================================================================== lote 4: cristales, geles y minerales

def _blob(c, cx, cy, rx, ry, rp, bumps=(), spec_pos=None):
    """Gota viscosa: elipsoide principal + abultamientos, con un brillo duro y luz rebotada."""
    c.ellipsoid(cx, cy, rx, ry, rp, bounce=0.18)
    for (bx, by, brx, bry) in bumps:
        c.ellipsoid(bx, by, brx, bry, rp, bounce=0.18)
    if spec_pos:
        sx, sy = spec_pos
        c.set(sx, sy, (246, 255, 252))
        c.set(sx + 1, sy, rp[5])
        c.set(sx, sy + 1, rp[5])


def glow_gel():
    c = Canvas()
    rp = ramp((70, 226, 208))
    _blob(c, 8.0, 8.8, 5.6, 5.2, rp, bumps=((8.2, 4.6, 2.4, 2.0),), spec_pos=(5, 6))
    core = ramp((200, 255, 240))
    for (x, y) in ((9, 10), (10, 10), (9, 11), (8, 11), (10, 11)):
        if c.get(x, y):
            c.set(x, y, core[3 if (x + y) % 2 else 2])
    for (x, y) in ((11, 8), (7, 9)):
        if c.get(x, y):
            c.set(x, y, core[4])
    c.set(11, 12, rp[1]); c.set(6, 12, rp[1])
    c.outline(factor=0.36)
    c.set(8, 14, rp[2])
    c.set(8, 15, rp[1])
    return c.image()

def cooling_gel():
    c = Canvas()
    glass = ramp((190, 214, 226))
    bt = Canvas()
    bt.poly([(5.6, 3.6), (10.4, 3.6), (10.4, 6.0), (13.2, 8.6), (13.2, 14.4), (2.8, 14.4), (2.8, 8.6), (5.6, 6.0)], glass[3])
    bt.poly([(5.6, 3.6), (7.0, 3.6), (7.0, 6.0), (2.8, 8.6), (2.8, 14.4), (4.2, 14.4), (4.2, 9.0), (5.6, 6.6)], glass[4])
    bt.outline(factor=0.42)
    c.merge(bt)
    gel = ramp((58, 150, 232))
    g = Canvas()
    g.poly([(3.6, 9.2), (12.4, 9.2), (12.4, 13.8), (3.6, 13.8)], gel[3])
    g.poly([(3.6, 9.2), (7.0, 9.2), (6.2, 13.8), (3.6, 13.8)], gel[4])
    g.poly([(10.0, 9.2), (12.4, 9.2), (12.4, 13.8), (9.4, 13.8)], gel[2])
    g.line(4, 9, 12, 9, gel[5])
    g.line(4, 13, 12, 13, gel[1])
    for (x, y) in ((5, 11), (9, 12), (7, 10)):
        g.set(x, y, gel[5])
    for (x, y) in ((6, 12), (10, 11)):
        g.set(x, y, (230, 250, 255))
    c.merge(g)
    c.set(5, 7, glass[5]); c.set(4, 8, glass[5])
    cap = ramp((240, 244, 248))
    k = Canvas()
    k.poly([(4.8, 1.2), (11.2, 1.2), (11.2, 3.4), (4.8, 3.4)], cap[3])
    k.poly([(4.8, 1.2), (7.0, 1.2), (7.0, 3.4), (4.8, 3.4)], cap[5])
    k.poly([(9.6, 1.2), (11.2, 1.2), (11.2, 3.4), (9.6, 3.4)], cap[1])
    k.line(5, 2, 11, 2, cap[2])
    k.outline(factor=0.42)
    c.merge(k)
    return c.image()

def croaker_slime():
    c = Canvas()
    rp = ramp((62, 170, 150))
    _blob(c, 8.0, 8.4, 6.0, 5.6, rp, spec_pos=(5, 4))
    inner = ramp((120, 220, 190))
    for (x, y, k) in ((9, 9, 3), (10, 9, 3), (9, 10, 2), (10, 10, 3), (11, 10, 2), (8, 11, 2)):
        if c.get(x, y):
            c.set(x, y, inner[k])
    for (x, y) in ((6, 7), (4, 9), (7, 12)):
        if c.get(x, y):
            c.set(x, y, rp[1])
    c.outline(factor=0.34)
    return c.image()


def magma_slime():
    c = Canvas()
    rp = ramp((248, 118, 28), sat=1.05)
    _blob(c, 8.0, 9.0, 6.2, 5.0, rp, bumps=((8.0, 4.4, 2.2, 2.0), (4.4, 6.2, 1.8, 1.6)), spec_pos=(5, 6))
    crust = ramp((84, 40, 36))
    rng = random.Random(41)
    for y in range(T):
        for x in range(T):
            q = c.get(x, y)
            if not q or y < 6:
                continue
            if rng.random() < (0.42 if (x + y) > 15 else 0.16) and (x * 3 + y) % 3 != 0:
                c.set(x, y, crust[2 if (x + y) > 15 else 3])
    hot = ramp((255, 222, 110))
    for (x, y, i) in ((7, 8, 4), (8, 8, 3), (8, 9, 4), (7, 9, 2)):
        c.set(x, y, hot[i])
    c.outline(factor=0.32)
    c.set(9, 14, rp[2]); c.set(9, 15, rp[1]) if False else None
    return c.image()


def glow_pearls():
    """Racimo de perlas luminosas: de atras hacia adelante, tamanos distintos."""
    c = Canvas()
    glow = ramp((90, 240, 224))
    deep = ramp((60, 200, 200))
    pearls = (
        (8.0, 4.6, 2.5, glow), (13.4, 7.4, 1.6, deep),
        (5.2, 8.4, 2.5, deep), (10.8, 8.6, 2.5, glow),
        (3.0, 12.6, 2.5, glow), (8.0, 12.4, 2.7, deep), (13.0, 12.8, 2.4, glow),
    )
    for (cx, cy, r, rp) in pearls:
        p = Canvas()
        p.ellipsoid(cx, cy, r, r, rp, bounce=0.16)
        p.set(int(cx) - 1, int(cy - r + 1.2), (250, 255, 252))
        p.set(int(cx), int(cy - r + 1.2), rp[5])
        if r > 2:
            p.set(int(cx) - 1, int(cy - r + 2.2), rp[5])
        p.outline(factor=0.34)
        c.merge(p)
    algae = ramp((70, 170, 110))
    s2 = Canvas()
    strip(s2, (8.0, 2.4), (10.4, 0.4), 0.5, 0.3, algae, spec=False)
    s2.outline(factor=0.4)
    c.merge(s2)
    return c.image()


BATCH4 = {
    "glow_gel": glow_gel,
    "cooling_gel": cooling_gel,
    "croaker_slime": croaker_slime,
    "magma_slime": magma_slime,
    "glow_pearls": glow_pearls,
}


# ====================================================================== lote 5: partes de criaturas y meteorito

def glowing_scale():
    c = Canvas()
    rp = ramp((40, 70, 110))
    glow = ramp((90, 236, 226))
    s = Canvas()
    s.poly([(8.0, 1.0), (12.6, 6.4), (11.4, 11.6), (8.0, 15.0), (4.6, 11.6), (3.4, 6.4)], rp[3])
    s.poly([(8.0, 1.0), (3.4, 6.4), (4.6, 11.6), (8.0, 15.0), (8.0, 8.0)], rp[4])      # mitad de la luz
    s.poly([(8.0, 1.0), (12.6, 6.4), (11.4, 11.6), (8.0, 15.0), (8.0, 8.0)], rp[2])    # mitad en sombra
    for (x, y) in ((6, 4), (5, 6), (5, 8)):
        s.set(x, y, rp[5])
    s.line(8, 4, 8, 12, glow[3])                                                        # nervio central luminoso
    s.line(8, 7, 5, 9, glow[2]); s.line(8, 7, 11, 9, glow[2])
    s.line(8, 10, 6, 12, glow[1]); s.line(8, 10, 10, 12, glow[1])
    s.set(8, 6, glow[5]); s.set(8, 9, glow[4])
    edge = [(x, y) for (x, y) in s.px if any((x + dx, y + dy) not in s.px for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for (x, y) in edge:                                                                 # borde luminoso
        s.set(x, y, glow[4] if (x + y) % 3 else glow[3])
    s.outline(factor=0.4)
    c.merge(s)
    return c.image()


def igneous_shell():
    """Herradura gruesa (casi un anillo abierto a la derecha): tubo redondo sombreado con trama, oscuro, con motas de lava."""
    c = Canvas()
    rp = ramp((60, 42, 90))
    cx, cy, R = 7.8, 8.2, 4.9
    n = 140
    sweep = math.radians(268)
    start = math.radians(78)
    samples = []
    for i in range(n + 1):
        t = i / n
        ang = start + t * sweep
        w = 2.25 - 0.55 * (abs(t - 0.5) * 2) ** 3                 # un poco mas fino en las puntas
        samples.append((cx + R * math.cos(ang), cy + R * math.sin(ang), w))
    L = (-0.5, -0.6, 0.62)
    bayer = ((0.0, 0.5), (0.75, 0.25))
    s = Canvas()
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            best = None
            for (sx, sy, w) in samples:
                d = math.hypot(px - sx, py - sy)
                if d <= w and (best is None or d / w < best[0]):
                    best = (d / w, sx, sy, w)
            if not best:
                continue
            r, sx, sy, w = best
            dx, dy = (px - sx) / w, (py - sy) / w
            nz = math.sqrt(max(0.0, 1 - r * r))
            lit = dx * L[0] + dy * L[1] + nz * L[2]
            f = 0.6 + max(0.0, min(1.0, (lit + 0.3) / 1.3)) * 3.4        # 0.6 .. 4.0
            idx = int(f + bayer[y % 2][x % 2])
            s.set(x, y, rp[max(0, min(5, idx))])
    hi = ramp((150, 120, 190))
    for (x, y) in ((3, 3), (4, 2), (2, 5), (2, 7)):          # brillo en el borde de la luz
        if s.get(x, y):
            s.set(x, y, hi[3])
    lava = ramp((255, 150, 40))
    for (x, y, i) in ((4, 4, 4), (10, 3, 4), (2, 8, 3), (4, 12, 4), (8, 13, 3), (13, 6, 4)):
        if s.get(x, y):
            s.set(x, y, lava[i])
    s.outline(factor=0.34)
    c.merge(s)
    return c.image()


def king_shell():
    c = Canvas()
    rp = ramp((52, 100, 204))
    sp = Canvas()
    for (b, t, w) in (((8.0, 4.0), (8.0, 0.4), 1.3), ((4.4, 5.2), (2.0, 2.2), 1.2), ((11.6, 5.2), (14.0, 2.2), 1.2),
                      ((3.0, 9.0), (0.6, 8.4), 1.1), ((13.0, 9.0), (15.4, 8.4), 1.1),
                      ((4.0, 13.0), (1.6, 15.0), 1.1), ((12.0, 13.0), (14.4, 15.0), 1.1), ((8.0, 14.0), (8.0, 15.8), 1.0)):
        ice_shard(sp, b, t, w, ramp((120, 170, 240)))
    c.merge(sp)
    b = Canvas()
    b.ellipsoid(8.0, 9.2, 6.2, 5.6, rp)
    for (x, y) in ((5, 7), (7, 5), (10, 6), (6, 10), (9, 11), (11, 9)):                 # tuberculos
        if b.get(x, y):
            b.set(x, y, rp[5] if (x + y) % 2 else rp[4])
            if b.get(x + 1, y + 1):
                b.set(x + 1, y + 1, rp[1])
    orange = ramp((248, 170, 90))
    for (x, y) in ((8, 8), (6, 12), (11, 12)):
        if b.get(x, y):
            b.set(x, y, orange[3])
    b.outline(factor=0.34)
    c.merge(b)
    return c.image()


def king_claw():
    c = Canvas()
    blue = ramp((52, 100, 204))
    navy = ramp((30, 36, 84))
    orange = ramp((250, 172, 92))
    arm = Canvas()
    strip(arm, (2.0, 14.0), (8.0, 9.6), 2.2, 3.0, blue)
    arm.ellipsoid(8.6, 9.2, 3.4, 3.2, blue)
    for (x, y) in ((5, 12), (7, 10), (9, 9)):
        arm.set(x, y, orange[3])
    arm.outline(factor=0.34)
    c.merge(arm)
    p1 = Canvas()
    strip(p1, (9.4, 8.4), (13.6, 1.0), 2.2, 0.5, navy)
    p1.outline(factor=0.4)
    c.merge(p1)
    p2 = Canvas()
    strip(p2, (10.4, 10.4), (15.0, 6.2), 1.8, 0.5, navy)
    p2.outline(factor=0.4)
    c.merge(p2)
    return c.image()


def hopper_foot():
    c = Canvas()
    rp = ramp((100, 196, 170))
    leg = Canvas()
    strip(leg, (11.4, 2.4), (6.6, 10.4), 2.0, 1.7, rp)
    leg.outline(factor=0.36)
    c.merge(leg)
    cut = ramp((236, 244, 232))
    top = Canvas()
    top.ellipsoid(11.8, 2.4, 2.4, 1.6, cut)
    top.set(10, 2, cut[5]); top.set(13, 2, rp[2])
    top.outline(factor=0.4)
    c.merge(top)
    for (b, t) in (((6.6, 10.4), (2.6, 13.4)), ((6.2, 11.0), (4.8, 14.8)), ((7.2, 11.2), (8.8, 14.6))):
        toe = Canvas()
        strip(toe, b, t, 1.2, 0.5, rp)
        toe.outline(factor=0.4)
        c.merge(toe)
    return c.image()


def glowing_barbel():
    c = Canvas()
    rp = ramp((236, 112, 150))
    pts = [(12.6, 1.6), (9.0, 3.6), (7.0, 7.0), (8.6, 10.4), (6.4, 13.8)]
    ws = [0.9, 1.2, 1.3, 1.2, 0.8]
    s = Canvas()
    for k in range(len(pts) - 1):
        strip(s, pts[k], pts[k + 1], ws[k], ws[k + 1], rp)
    s.outline(factor=0.34)
    c.merge(s)
    glow = ramp((255, 220, 236))
    for (x, y) in ((11, 2), (8, 5), (8, 9)):
        if c.get(x, y):
            c.set(x, y, glow[4])
    b = Canvas()
    b.ellipsoid(13.4, 1.6, 1.4, 1.4, ramp((255, 200, 120)))
    b.outline(factor=0.4)
    c.merge(b)
    return c.image()


def meteorite():
    c = Canvas()
    rp = ramp((64, 54, 56))
    s = Canvas()
    s.poly([(2.2, 9.4), (3.4, 4.6), (8.4, 2.4), (13.2, 4.8), (14.0, 10.4), (10.6, 14.2), (4.8, 13.8)], rp[3])
    s.poly([(3.4, 4.6), (8.4, 2.4), (7.6, 8.0), (2.2, 9.4)], rp[4])
    s.poly([(8.4, 2.4), (13.2, 4.8), (11.0, 8.4), (7.6, 8.0)], rp[3])
    s.poly([(2.2, 9.4), (7.6, 8.0), (7.0, 13.4), (4.8, 13.8)], rp[2])
    s.poly([(7.6, 8.0), (11.0, 8.4), (14.0, 10.4), (10.6, 14.2), (7.0, 13.4)], rp[1])
    for (x, y) in ((5, 4), (6, 3), (4, 6)):
        s.set(x, y, rp[5])
    metal = ramp((190, 200, 214))
    for (x, y, i) in ((9, 5, 5), (10, 5, 4), (5, 9, 4), (11, 11, 3), (7, 11, 4), (4, 6, 3), (12, 7, 4), (8, 9, 3), (6, 5, 4)):   # vetas metalicas
        s.set(x, y, metal[i])
    rust = ramp((200, 96, 40))
    for (x, y, i) in ((4, 11, 3), (5, 12, 2), (9, 8, 3), (12, 8, 2), (8, 6, 2)):
        s.set(x, y, rust[i])
    s.outline(factor=0.34)
    c.merge(s)
    return c.image()


BATCH5 = {
    "glowing_scale": glowing_scale,
    "igneous_shell": igneous_shell,
    "king_claw": king_claw,
    "hopper_foot": hopper_foot,
    "glowing_barbel": glowing_barbel,
    "meteorite_fragment": meteorite,
}


# ====================================================================== lote 6: raices, frutas y comida

def _leaf(c, base, tip, w, rp):
    l = Canvas()
    strip(l, base, tip, w, 0.35, rp, spec=False)
    l.outline(factor=0.4)
    c.merge(l)


def _root(baked):
    """Tuberculo redondeado en diagonal con hojas arriba-izquierda y cola abajo-derecha. Cruda y horneada comparten silueta."""
    c = Canvas()
    if baked:
        leaf = ramp((120, 98, 52))
        body_rp = ramp((198, 142, 84))
        tail_rp = ramp((150, 104, 62))
    else:
        leaf = ramp((70, 200, 170))
        body_rp = ramp((112, 80, 188))
        tail_rp = ramp((150, 120, 210))
    _leaf(c, (4.0, 4.2), (1.2, 0.8), 0.9, leaf)
    _leaf(c, (4.6, 3.8), (6.8, 0.4), 0.9, leaf)
    b = Canvas()
    b.ellipsoid(8.2, 8.4, 6.4, 4.5, body_rp, tilt=0.78)
    strip(b, (11.8, 11.8), (14.4, 14.8), 1.0, 0.4, tail_rp, spec=False)
    pts = set(b.px)
    if baked:
        dark = ramp((118, 72, 42))
        rng = random.Random(77)
        for (x, y) in sorted(pts):
            if rng.random() < 0.20 and (x + y) % 3 != 0 and b.get(x, y) in body_rp:
                b.set(x, y, dark[3 if y < 8 else 2])
        flesh = ramp((250, 226, 170))
        for (x, y, k) in ((6, 6, 4), (7, 7, 4), (8, 8, 3), (9, 9, 3), (10, 10, 2), (8, 7, 3), (7, 8, 3)):   # grieta a lo largo
            if b.get(x, y):
                b.set(x, y, flesh[k])
        for (x, y) in ((5, 7), (6, 5), (7, 5)):
            if b.get(x, y):
                b.set(x, y, body_rp[5])
    else:
        for (x, y) in ((6, 8), (8, 6), (9, 10), (10, 8), (7, 10)):
            if b.get(x, y):
                b.set(x, y, body_rp[1])                                                              # surcos
        for (x, y) in ((5, 6), (6, 5), (7, 7), (9, 7)):
            if b.get(x, y):
                b.set(x, y, body_rp[5])
    b.outline(factor=0.34)
    c.merge(b)
    return c.image()


def cerulean_root():
    return _root(False)


def baked_root():
    return _root(True)


def duskfruit():
    c = Canvas()
    stem = ramp((98, 70, 150))
    st = Canvas()
    strip(st, (8.0, 4.0), (8.6, 1.4), 0.6, 0.5, stem, spec=False)
    st.outline(factor=0.4)
    c.merge(st)
    leaf = ramp((70, 200, 170))
    _leaf(c, (8.6, 2.2), (13.4, 1.0), 1.0, leaf)
    f = Canvas()
    rp = ramp((70, 204, 176))
    f.ellipsoid(8.0, 9.4, 6.0, 5.8, rp)
    blue = ramp((46, 100, 204))
    for (x, y) in ((11, 8), (5, 11), (9, 12), (12, 11), (7, 6), (4, 8)):               # motas azules
        if f.get(x, y):
            f.set(x, y, blue[3])
            if f.get(x - 1, y - 1):
                f.set(x - 1, y - 1, blue[5])
    f.set(5, 6, (240, 255, 252)); f.set(6, 6, rp[5]); f.set(5, 7, rp[5])
    f.set(8, 4, rp[1])
    f.outline(factor=0.34)
    c.merge(f)
    return c.image()


def dried_kelp():
    c = Canvas()
    rp = ramp((70, 130, 106))
    pts = [(14.0, 0.8), (11.6, 2.6), (9.6, 2.8), (6.4, 5.0), (7.4, 8.0), (10.4, 8.4), (8.4, 11.6), (4.0, 12.4), (1.8, 15.0)]
    s = Canvas()
    for k in range(len(pts) - 1):
        strip(s, pts[k], pts[k + 1], 0.95, 0.85, rp)
    lighter = ramp((110, 176, 140))
    for (x, y) in ((12, 2), (9, 3), (7, 5), (8, 8), (9, 9), (5, 12)):
        if s.get(x, y):
            s.set(x, y, lighter[3])
    for (x, y) in ((10, 3), (6, 6), (9, 8)):
        if s.get(x, y):
            s.set(x, y, rp[1])
    s.outline(factor=0.34)
    c.merge(s)
    return c.image()


def reed_pod():
    c = Canvas()
    teal = ramp((70, 200, 170))
    stem = Canvas()
    strip(stem, (1.6, 15.0), (14.4, 1.2), 0.7, 0.6, teal, spec=False)
    stem.outline(factor=0.4)
    c.merge(stem)
    rp = ramp((54, 100, 206))
    for (cx, cy, rx, ry) in ((4.4, 11.6, 2.6, 2.0), (8.2, 7.8, 2.8, 2.2), (11.8, 4.2, 2.6, 2.0)):
        p = Canvas()
        p.ellipsoid(cx, cy, rx, ry, rp, tilt=-0.78)
        p.set(int(cx) - 1, int(cy) - 1, rp[5])
        p.set(int(cx), int(cy) + 1, rp[1])
        p.outline(factor=0.34)
        c.merge(p)
    return c.image()


def glowcap_stew():
    c = Canvas()
    wood = ramp((158, 112, 76))
    bowl = Canvas()
    bowl.ellipsoid(8.0, 8.6, 7.0, 5.6, wood)
    for (x, y) in list(bowl.px):
        if y < 6:
            del bowl.px[(x, y)]
    for y in (10, 12):                                                      # vetas de la madera
        for x in range(4, 12):
            if bowl.get(x, y) and (x + y) % 3:
                bowl.set(x, y, wood[1] if y == 12 else wood[2])
    bowl.outline(factor=0.4)
    c.merge(bowl)
    rim = Canvas()
    rim.ellipsoid(8.0, 6.6, 7.0, 2.4, ramp((178, 130, 92)))
    rim.outline(factor=0.4)
    c.merge(rim)
    stew = ramp((90, 236, 224))
    s = Canvas()
    s.ellipsoid(8.0, 6.8, 5.6, 1.7, stew)
    s.set(4, 6, (240, 255, 252)); s.set(5, 6, stew[5]); s.set(11, 7, stew[3]); s.set(8, 7, stew[2])
    c.merge(s)
    cap = ramp((120, 220, 230))
    for (cx, cy, r) in ((6.0, 4.6, 1.9), (10.6, 4.2, 1.8), (8.2, 3.2, 1.4)):          # gorros de hongo luminoso
        m = Canvas()
        m.ellipsoid(cx, cy, r, r * 0.7, cap)
        m.set(int(cx) - 1, int(cy) - 1, (240, 255, 252))
        m.outline(factor=0.4)
        c.merge(m)
    return c.image()


def antenna_bulb():
    c = Canvas()
    steel = ramp((70, 96, 130))
    st = Canvas()
    strip(st, (9.0, 8.6), (2.4, 14.6), 1.1, 0.9, steel)
    for (x, y) in ((7, 10), (5, 12)):
        st.set(x, y, steel[5])
    st.outline(factor=0.4)
    c.merge(st)
    rp = ramp((80, 232, 214))
    b = Canvas()
    b.ellipsoid(11.0, 5.0, 4.6, 4.6, rp, bounce=0.2)
    b.set(9, 3, (240, 255, 252)); b.set(10, 3, rp[5]); b.set(9, 4, rp[5])
    for (x, y) in ((12, 6), (13, 6), (12, 7)):
        if b.get(x, y):
            b.set(x, y, rp[4])
    b.outline(factor=0.34)
    c.merge(b)
    return c.image()


BATCH6 = {
    "cerulean_root": cerulean_root,
    "baked_cerulean_root": baked_root,
    "duskfruit": duskfruit,
    "dried_ribbon_kelp": dried_kelp,
    "reed_pod": reed_pod,
    "glowcap_stew": glowcap_stew,
    "antenna_bulb": antenna_bulb,
}


# ====================================================================== lote 7: armas y herramientas

def fang_arrow():
    c = Canvas()
    wood = ramp((126, 88, 62))
    bone = ramp((238, 230, 208))
    teal = ramp((64, 178, 150))
    blue = ramp((60, 112, 210))
    sh = Canvas()
    strip(sh, (2.2, 14.0), (11.6, 4.6), 0.5, 0.5, wood)
    sh.outline(factor=0.4)
    c.merge(sh)
    for tt in (0.04, 0.17, 0.30):                                                         # plumas: pares azul/turquesa a lo largo del astil
        px, py = 2.2 + 9.4 * tt, 14.0 - 9.4 * tt
        for (dx, dy, rp) in ((-2.6, -1.6, blue), (1.6, 2.6, teal)):
            f = Canvas()
            strip(f, (px, py), (px + dx, py + dy), 0.75, 0.3, rp, spec=False)
            f.outline(factor=0.4)
            c.merge(f)
    sh2 = Canvas()
    strip(sh2, (2.2, 14.0), (11.6, 4.6), 0.5, 0.5, wood)                                # astil encima de las plumas
    sh2.outline(factor=0.4)
    c.merge(sh2)
    h = Canvas()
    strip(h, (10.8, 5.4), (14.8, 1.2), 1.7, 0.3, bone)
    h.set(12, 4, bone[5])
    h.outline(factor=0.42)
    c.merge(h)
    return c.image()


BATCH7 = {
    "fang_arrow": fang_arrow,
}
