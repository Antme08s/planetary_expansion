"""Motor de distorsión (4x4 bloques) y consola de navegación (2x2): vistas previas (propuesta, aún no en el mod).

Mismo método que gravity_generator_model.py: el modelo son cajas en píxeles (1 bloque = 16) y cada cara se pinta
por código con rampas y biseles. Uso: python3 jump_drive_model.py <salida_motor.png> <salida_consola.png>
"""
import math, random, sys
from PIL import Image, ImageDraw, ImageFont

H = lambda s: tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
RAMP = {
    'steel': ['1c1d22', '262830', '30333c', '3c404b', '4b505d', '5f6574', '7a8191'],
    'brass': ['5c382b', '7d4f38', '9e6947', 'bd8a4e', 'd6a85c', 'e8c26e', 'f6dc92'],
    'frost': ['2c4f66', '3f6d88', '5a8fae', '7db3cf', 'a6d3e8', 'cdeaf6', 'f0fbff'],
    'violet': ['160a26', '24123a', '3a1c6b', '5a2ca8', '7a3fd0', 'a86bff', 'cfa8ff', 'efe0ff'],
    'dark': ['07060b', '0d0b14', '15111f', '1e182c'],
    'cyan': ['0f3a40', '1d6f74', '2fb3b0', '5ff2e6', 'c8fff8'],
    'shaft': ['4a4742', '67635c', '8f8a80', 'b6b1a6', 'cfcabd'],
}


def ramp(cols, t):
    t = max(0.0, min(0.999, t))
    return H(cols[min(len(cols) - 1, int(t * (len(cols) - 1) + 0.5))])


PLANET_COL = {'sun': ('ffd75e', 'fff3b0'), 'venus': ('d9b46a', 'f1d79a'), 'earth': ('3f7fd0', '7fc06a'),
              'mars': ('c4502e', 'e07a50'), 'giant': ('c08a52', 'e2b97c'), 'ice': ('a9d8ec', 'eaf8ff'),
              'thalassa': ('1f4f9a', '3f7fd0'), 'ignis': ('d8401a', 'ffb03a')}


def paint(mat, face, w, h, seed):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (w, h))
    px = im.load()
    if mat.startswith('p_'):
        a, b = PLANET_COL[mat[2:]]
        for y in range(h):
            for x in range(w): px[x, y] = H(b if (x == 0 or y == 0) else a)
        return im
    side = face not in ('up', 'down')
    base = {'plate': 'steel', 'body': 'steel', 'trim': 'brass', 'tank': 'frost', 'ring': 'brass', 'ringv': 'violet',
            'core': 'violet', 'bearing': 'dark', 'shaft': 'shaft', 'pipe': 'frost', 'screen': 'dark', 'glass': 'cyan',
            'holo': 'cyan', 'leg': 'steel', 'panel': 'steel', 'button': 'brass', 'vent': 'steel',
            'post': 'steel', 'band': 'frost', 'tglass': 'frost', 'n2': 'frost'}[mat]
    cols = RAMP[base]

    def metal(b=0.6, g=0.32, n=0.035):
        for y in range(h):
            for x in range(w):
                t = b + rnd.uniform(-n, n)
                if side and h > 1: t += g * (0.5 - y / (h - 1))
                elif not side: t += 0.06 - 0.006 * (x + y)
                if face == 'down': t -= 0.25
                if w >= 3 and h >= 2:
                    if x == 0 or y == 0: t += 0.2
                    if x == w - 1 or y == h - 1: t -= 0.24
                px[x, y] = ramp(cols, t)

    cx, cy = (w - 1) / 2, (h - 1) / 2
    if mat == 'tglass':
        # cristal escarchado del tanque (translúcido): bordes y vetas de escarcha más opacos
        for y in range(h):
            for x in range(w):
                a = 42 + rnd.randint(-6, 6)
                c = (205, 238, 252)
                if x == 0 or x == w - 1 or y == 0 or y == h - 1: a, c = 120, (226, 246, 255)
                elif rnd.random() < 0.04: a, c = 120, (240, 252, 255)
                elif (x + 2 * y) % 13 == 0 and side: a = 80
                px[x, y] = c + (a,)
        return im
    if mat == 'n2':
        # nitrógeno líquido: azul claro brillante, la superficie más clara, con burbujas
        for y in range(h):
            for x in range(w):
                if face == 'up':
                    c = (205, 243, 255); a = 225
                    if rnd.random() < 0.12: c = (240, 252, 255)
                else:
                    k = y / max(1, h - 1)
                    c = (int(120 - 40 * k), int(205 - 45 * k), 255); a = 225
                    if rnd.random() < 0.04: c = (225, 248, 255)
                    if y == 0: c = (200, 240, 255)
                px[x, y] = c + (a,)
        return im
    if mat in ('plate', 'leg', 'trim', 'ring', 'button', 'panel', 'post', 'band'):
        metal()
        if mat == 'plate' and face == 'up' and w >= 16:
            for (x, y) in [(2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)]:
                px[x, y] = H(RAMP['steel'][6]); px[x + 1, y + 1] = H(RAMP['steel'][0])
            for i in range(4, w - 4):                       # surcos de rejilla en el suelo de la máquina
                if i % 8 == 0:
                    for j in range(4, h - 4): px[i, j] = H(RAMP['steel'][1])
        if mat == 'panel' and face == 'up':
            for y in range(2, h - 2):
                for x in range(2, w - 2):
                    px[x, y] = ramp(RAMP['dark'], 0.5 + 0.02 * ((x * 7 + y * 3) % 5))
    elif mat == 'body':
        metal(0.55, 0.35)
        if side and h >= 8 and w >= 8:                       # paneles con franja de estado cian
            for x in range(2, w - 2):
                px[x, 2] = H(RAMP['steel'][1])
                px[x, h - 3] = H(RAMP['steel'][1])
            for x in range(3, w - 3):
                px[x, h // 2] = ramp(RAMP['cyan'], 0.85 if (x // 3) % 2 == 0 else 0.6)
    elif mat == 'vent':
        metal(0.45, 0.25)
        if side:
            for y in range(1, h - 1):
                for x in range(1, w - 1):
                    if y % 2 == 0: px[x, y] = H(RAMP['dark'][1])
    elif mat in ('tank', 'pipe'):
        for y in range(h):
            for x in range(w):
                t = 0.62 + rnd.uniform(-0.05, 0.05)
                if side:
                    t += 0.30 * (0.5 - abs(x - cx) / max(1, w)) - 0.12 * (x / max(1, w - 1))
                    if rnd.random() < 0.08: t += 0.35                   # escarcha
                if face == 'down': t -= 0.3
                px[x, y] = ramp(cols, t)
        if mat == 'tank' and side and h >= 10:
            for x in range(w):
                for y in (1, h - 2): px[x, y] = ramp(RAMP['brass'], 0.55 if x else 0.85)
            for y in range(4, h - 4):                                  # visor del nivel de nitrógeno
                for x in range(max(1, int(cx) - 1), min(w - 1, int(cx) + 1)):
                    px[x, y] = ramp(RAMP['cyan'], 0.9 if y > h // 3 else 0.35)
    elif mat == 'ringv':
        for y in range(h):
            for x in range(w):
                px[x, y] = ramp(cols, 0.62 + 0.18 * math.sin((x + y) * 0.9) + rnd.uniform(-0.04, 0.04))
    elif mat == 'core':
        for y in range(h):
            for x in range(w):
                r = math.hypot(x - cx, y - cy) / max(1.0, math.hypot(cx, cy))
                a = math.atan2(y - cy, x - cx)
                swirl = math.sin(a * 3 + r * 7)
                t = 0.15 + 0.85 * max(0.0, 1 - r) ** 1.4 + 0.12 * swirl
                if r < 0.18: t = 1.0
                px[x, y] = ramp(cols, t)
    elif mat == 'bearing':
        metal(0.6, 0.4, 0.05)
    elif mat == 'shaft':
        if face in ('east', 'west'):
            for y in range(h):
                for x in range(w): px[x, y] = H(RAMP['shaft'][1])
            for i in range(min(w, h)): px[i, i] = H(RAMP['shaft'][0]); px[min(w, h) - 1 - i, i] = H(RAMP['shaft'][0])
        else:
            for y in range(h):
                for x in range(w): px[x, y] = H([RAMP['shaft'][4], RAMP['shaft'][3], RAMP['shaft'][2], RAMP['shaft'][1]][y % 4])
    elif mat == 'screen':
        for y in range(h):
            for x in range(w):
                px[x, y] = ramp(RAMP['dark'], 0.4 + rnd.uniform(-0.05, 0.05))
        if face == 'up' and w > 6 and h > 6:                            # mapa en la pantalla
            for y in range(h):
                for x in range(w):
                    r = math.hypot(x - cx, y - cy)
                    for R in (3, 5.5, 8, 11):
                        if abs(r - R) < 0.5 and R < min(w, h) / 2: px[x, y] = ramp(RAMP['cyan'], 0.45)
                    if r < 1.2: px[x, y] = H('ffd75e')
            for (x, y, c) in [(int(cx + 3), int(cy), 'e8c070'), (int(cx - 4), int(cy + 3), '4a9ae6'), (int(cx + 5), int(cy - 6), 'd8483a')]:
                if 0 <= x < w and 0 <= y < h: px[x, y] = H(c)
    elif mat == 'glass':
        for y in range(h):
            for x in range(w): px[x, y] = ramp(cols, 0.55 + 0.25 * (1 - y / max(1, h - 1)))
    elif mat == 'holo':
        for y in range(h):
            for x in range(w): px[x, y] = ramp(cols, 0.8 + rnd.uniform(-0.1, 0.1))
    return im


def rot_of(el):
    """(eje, grados): un número es un giro en y; ('z', g) un giro en z."""
    r = el[7]
    if isinstance(r, tuple): return r
    return ('y', r)


def faces(el):
    x1, y1, z1, x2, y2, z2, mat = el[:7]
    axis, ang = rot_of(el)
    cx, cy, cz = (x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2
    a = math.radians(ang)
    ca, sa = math.cos(a), math.sin(a)

    def D(v):
        if axis == 'z':   # como Minecraft: +x gira hacia +y
            return (v[0] * ca - v[1] * sa, v[0] * sa + v[1] * ca, v[2])
        return (v[0] * ca - v[2] * sa, v[1], v[0] * sa + v[2] * ca)

    def R(p):
        d = D((p[0] - cx, p[1] - cy, p[2] - cz))
        return (cx + d[0], cy + d[1], cz + d[2])

    raw = [('up', (x1, y2, z1), (1, 0, 0), (0, 0, 1), (0, 1, 0), x2 - x1, z2 - z1),
           ('down', (x1, y1, z2), (1, 0, 0), (0, 0, -1), (0, -1, 0), x2 - x1, z2 - z1),
           ('south', (x1, y2, z2), (1, 0, 0), (0, -1, 0), (0, 0, 1), x2 - x1, y2 - y1),
           ('north', (x2, y2, z1), (-1, 0, 0), (0, -1, 0), (0, 0, -1), x2 - x1, y2 - y1),
           ('east', (x2, y2, z2), (0, 0, -1), (0, -1, 0), (1, 0, 0), z2 - z1, y2 - y1),
           ('west', (x1, y2, z1), (0, 0, 1), (0, -1, 0), (-1, 0, 0), z2 - z1, y2 - y1)]
    return [(n, R(o), D(u), D(v), D(nm), w, h) for (n, o, u, v, nm, w, h) in raw]


GLOW = ('core', 'holo', 'glass', 'screen')
GLOW_PREFIX = 'p_'


def render(img, elements, ox, oy, S, center, turn=0):
    d = ImageDraw.Draw(img)
    c0, c2 = center

    def T(p):
        x, y, z = p[0] - c0, p[1], p[2] - c2
        for _ in range(turn): x, z = -z, x
        return (x, y, z)

    def P(p):
        x, y, z = T(p)
        return (ox + (x - z) * 0.866 * S, oy + (x + z) * 0.5 * S - y * S)

    quads = []
    for i, el in enumerate(elements):
        for (name, o, u, v, n, w, h) in faces(el):
            nx, ny, nz = T((n[0] + c0, n[1], n[2] + c2))
            if nx + ny + nz <= 0.01: continue
            shade = max(0.45, min(1.0, 0.72 + 0.28 * ny + 0.10 * nz - 0.10 * nx))
            tw, th = max(1, math.ceil(w - 1e-6)), max(1, math.ceil(h - 1e-6))
            tex = paint(el[6], name, tw, th, i * 7 + len(name)).load()
            su, sv = w / tw, h / th
            f = 1.0 if (el[6] in GLOW or el[6].startswith(GLOW_PREFIX)) else shade
            for j in range(th):
                for k in range(tw):
                    a = [o[m] + u[m] * su * k + v[m] * sv * j for m in range(3)]
                    b = [a[m] + u[m] * su for m in range(3)]
                    c = [b[m] + v[m] * sv for m in range(3)]
                    e = [a[m] + v[m] * sv for m in range(3)]
                    ctr = T([(a[m] + c[m]) / 2 for m in range(3)])
                    col = tex[k, j]
                    quads.append((ctr[0] + ctr[1] + ctr[2], [P(a), P(b), P(c), P(e)], tuple(int(q * f) for q in col[:3]) + (col[3],)))
    quads.sort(key=lambda q: q[0])
    W_, H_ = img.size
    for _, pts, col in quads:
        if col[3] < 255:   # translúcido (cristal, nitrógeno): se mezcla con lo que ya hay detrás
            mx = sum(p[0] for p in pts) / 4; my = sum(p[1] for p in pts) / 4
            if not (0 <= mx < W_ and 0 <= my < H_): continue
            bg = img.getpixel((int(mx), int(my)))
            al = col[3] / 255
            col = tuple(int(col[m] * al + bg[m] * (1 - al)) for m in range(3)) + (255,)
        d.polygon(pts, fill=col)


# ---------------------------------------------------------------- motor de distorsión: 4x4 bloques, 3 de alto
DRIVE = [
    (0, 0, 0, 64, 4, 64, 'plate', 0),                                   # plataforma
    (4, 4, 4, 60, 12, 60, 'body', 0),                                   # cuerpo bajo
    (5, 12, 5, 59, 13, 59, 'plate', 0),                                 # tapa de acero
    (0.5, 2, 18, 2, 14, 30, 'bearing', 0), (62, 2, 18, 63.5, 14, 30, 'bearing', 0),   # entradas de eje (2 lados)
    (0, 6, 22, 64, 10, 26, 'shaft', 0),
    (20, 13, 24, 44, 15, 40, 'trim', 0),                                # base del anillo
]
# tanques de nitrógeno en las 4 esquinas (0.7.38): cristal escarchado con el nitrógeno dentro (sube y baja con el
# que tiene el motor), cuatro postes, aros de abajo y de arriba y tapa de latón. El cristal se mete medio píxel en
# los aros para que ninguna cara coincida con otra (si coinciden, parpadean).
TANKS = [(2, 2), (52, 2), (2, 52), (52, 52)]
FLUID_Y0, FLUID_H = 7.05, 19.75
for (x, z) in TANKS:
    DRIVE += [(x, 4, z, x + 10, 7, z + 10, 'band', 0), (x, 27, z, x + 10, 30, z + 10, 'band', 0),
              (x + 0.5, 6.5, z + 0.5, x + 9.5, 27.5, z + 9.5, 'tglass', 0)]
    for (px_, pz) in [(x, z), (x + 8.5, z), (x, z + 8.5), (x + 8.5, z + 8.5)]:
        DRIVE.append((px_, 7, pz, px_ + 1.5, 27, pz + 1.5, 'post', 0))
    DRIVE += [(x + 1, 30, z + 1, x + 9, 32, z + 9, 'trim', 0), (x + 3, 32, z + 3, x + 7, 33, z + 7, 'trim', 0)]
# nitrógeno de cada tanque: altura completa; el cliente lo escala según lo lleno que esté el motor
FLUID = [(x + 1, 0, z + 1, x + 9, FLUID_H, z + 9, 'n2', 0) for (x, z) in TANKS]


def ring_segment(cx, cy, r, a_deg, thick, length, z1, z2, mat):
    """Segmento del anillo orientado a lo largo de la circunferencia. Minecraft solo gira de 22,5 en 22,5 grados
    (entre -45 y 45): se usa el giro equivalente y se intercambian largo y grosor si hace falta."""
    a = math.radians(a_deg)
    x, y = cx + r * math.cos(a), cy + r * math.sin(a)
    q = round(a_deg / 90.0)
    th = a_deg - 90 * q
    if th > 45: th -= 90; q += 1
    if th < -45: th += 90; q -= 1
    lx, ly = (thick, length) if q % 2 == 0 else (length, thick)
    return (x - lx / 2, y - ly / 2, z1, x + lx / 2, y + ly / 2, z2, mat, ('z', th) if th else 0)


# anillo vertical que dobla el espacio (0.7.38): 16 segmentos de latón y 16 violetas por dentro, girados a lo largo
# de la circunferencia y sin tocarse entre ellos (antes eran 28 cubos que se solapaban y parpadeaban)
RC, RY, RR = 32, 31, 15
for k in range(16):
    DRIVE.append(ring_segment(RC, RY, RR, k * 22.5, 4, 5.1, 29, 35, 'ring'))
    DRIVE.append(ring_segment(RC, RY, 11.2, k * 22.5, 2, 3.9, 30, 34, 'ringv'))
DRIVE += [(14, 13, 28, 18, 22, 36, 'trim', 0), (46, 13, 28, 50, 22, 36, 'trim', 0)]   # soportes del anillo
# núcleo de distorsión flotando (casi esfera: tres cajas cruzadas)
DRIVE += [(27, 26, 27, 37, 36, 37, 'core', 45), (25.5, 28, 28.5, 38.5, 34, 35.5, 'core', 45), (28.5, 24.5, 29, 35.5, 37.5, 35, 'core', 45)]
# tuberías de nitrógeno: de cada tanque a la base del anillo
DRIVE += [(12, 13, 6, 24, 15, 9, 'pipe', 0), (22, 13, 9, 25, 15, 24, 'pipe', 0),
          (40, 13, 55, 52, 15, 58, 'pipe', 0), (39, 13, 40, 42, 15, 55, 'pipe', 0),
          (12, 13, 55, 22, 15, 58, 'pipe', 0), (42, 13, 6, 52, 15, 9, 'pipe', 0)]
# rejillas de ventilación en el cuerpo
DRIVE += [(18, 5, 3.5, 46, 11, 4, 'vent', 0), (18, 5, 60, 46, 11, 60.5, 'vent', 0)]

# ---------------------------------------------------------------- consola de navegación: 2x2 bloques
CONSOLE = [
    (0, 0, 0, 32, 2, 32, 'plate', 0),
    (3, 2, 3, 7, 12, 7, 'leg', 0), (25, 2, 3, 29, 12, 7, 'leg', 0), (3, 2, 25, 7, 12, 29, 'leg', 0), (25, 2, 25, 29, 12, 29, 'leg', 0),
    (6, 4, 6, 26, 12, 26, 'body', 0),
    (0, 12, 0, 32, 15, 32, 'trim', 0),                                   # tablero de latón
    (2, 15, 2, 30, 16, 30, 'panel', 0),
    (5, 16, 5, 27, 16.5, 27, 'screen', 0),                               # mapa en la mesa
    (4, 16, 2, 10, 17, 4.5, 'button', 0), (12, 16, 2, 14, 17, 4.5, 'glass', 0), (22, 16, 2, 28, 17, 4.5, 'button', 0),
    (14, 16.5, 14, 18, 18, 18, 'trim', 0),                               # proyector
    # pequeño sistema solar holográfico encima: Sol y planetas con sus colores, órbitas en puntos
    (15, 23.5, 15, 17, 25.5, 17, 'p_sun', 0),
    (20.5, 24, 16, 21.5, 25, 17, 'p_venus', 0), (9.5, 24, 12, 11, 25.5, 13.5, 'p_earth', 0),
    (19.5, 24, 6.5, 20.5, 25, 7.5, 'p_mars', 0), (25, 23.8, 22, 27, 25.8, 24, 'p_giant', 0),
    (5, 24, 21, 6, 25, 22, 'p_ice', 0),
]
for R in (4, 6.5, 9, 11.5, 13.5):   # Venus, Tierra, Marte, gigante, mundo helado (el cliente los hace girar)
    n = int(R * 2.6)
    for k in range(n):
        a = k * 2 * math.pi / n
        x, z = 16 + R * math.cos(a), 16 + R * math.sin(a)
        CONSOLE.append((x - 0.2, 24.4, z - 0.2, x + 0.2, 24.6, z + 0.2, 'holo', 0))


def sheet(elements, center, size, S, oy, title, labels, out):
    W, Hh = size
    img = Image.new('RGBA', (W, Hh), (34, 37, 46, 255))
    d = ImageDraw.Draw(img)
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 20)
    f = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 15)
    for i, lab in enumerate(labels):
        render(img, elements, W * (i * 2 + 1) / (2 * len(labels)), oy, S, center, turn=i)
        d.text((W * i / len(labels) + 16, Hh - 30), lab, fill=(205, 205, 205, 255), font=f)
    d.text((16, 12), title, fill=(240, 240, 240, 255), font=F)
    img.save(out)


def export(outdir, ns='planetary_expansion'):
    """Textura y modelos JSON para el mod. El motor va a media escala (Minecraft solo admite -16..32 en un modelo);
    el BlockEntityRenderer lo dibuja al doble. Piezas: motor (marco y núcleo), consola (mesa, anillos del holograma
    y un cubo por astro)."""
    import json, os

    def pack(groups, name):
        imgs = []
        for gi, (elements, _) in enumerate(groups):
            for i, el in enumerate(elements):
                for (fname, o, u, v, n, w, h) in faces(el):
                    tw, th = max(1, math.ceil(w - 1e-6)), max(1, math.ceil(h - 1e-6))
                    imgs.append(((gi, i, fname), paint(el[6], fname, tw, th, i * 7 + len(fname))))
        for size in (64, 128, 256, 512):
            order = sorted(range(len(imgs)), key=lambda k: (-imgs[k][1].size[1], -imgs[k][1].size[0]))
            pos, x, y, row = {}, 0, 0, 0
            for k in order:
                w, h = imgs[k][1].size
                if x + w > size: x, y, row = 0, y + row, 0
                pos[k] = (x, y); x += w; row = max(row, h)
            if y + row <= size: break
        atlas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        uv = {}
        for k, (key, im) in enumerate(imgs):
            atlas.paste(im, pos[k])
            w, h = im.size
            f = 16.0 / size
            uv[key] = [round(pos[k][0] * f, 5), round(pos[k][1] * f, 5), round((pos[k][0] + w) * f, 5), round((pos[k][1] + h) * f, 5)]
        atlas.save(os.path.join(outdir, name + '.png'))
        return uv, size

    def element(gi, i, el, uv, scale, shift=(0, 0, 0)):
        x1, y1, z1, x2, y2, z2, mat = el[:7]
        axis, rot = rot_of(el)
        k = lambda v, j: round((v + shift[j]) * scale, 4)
        e = {'from': [k(x1, 0), k(y1, 1), k(z1, 2)], 'to': [k(x2, 0), k(y2, 1), k(z2, 2)],
             'faces': {n: {'uv': uv[(gi, i, n)], 'texture': '#t'} for n in ('up', 'down', 'north', 'south', 'east', 'west')}}
        if rot:
            e['rotation'] = {'origin': [k((x1 + x2) / 2, 0), k((y1 + y2) / 2, 1), k((z1 + z2) / 2, 2)], 'axis': axis, 'angle': rot}
        if mat in GLOW or mat.startswith(GLOW_PREFIX): e['shade'] = False
        for j in range(3):
            assert -16 <= e['from'][j] <= 32 and -16 <= e['to'][j] <= 32, (mat, e['from'], e['to'])
        return e

    def save(name, elements, tex, translucent=False):
        model = {'ambientocclusion': False, 'textures': {'t': f'{ns}:block/{tex}', 'particle': f'{ns}:block/{tex}'},
                 'elements': elements}
        if translucent: model['render_type'] = 'minecraft:translucent'
        with open(os.path.join(outdir, name + '.json'), 'w') as fh:
            json.dump(model, fh)

    # motor
    drive = [el for el in DRIVE if el[6] != 'shaft']
    uv, size = pack([(drive, 'drive'), (FLUID, 'fluid')], 'warp_drive')
    lit = lambda m: m in GLOW or m == 'ringv'   # piezas que brillan: se dibujan aparte a plena luz
    clear = lambda m: m == 'tglass'              # cristal de los tanques: translúcido, aparte
    save('warp_drive_frame', [element(0, i, el, uv, 0.5) for i, el in enumerate(drive)
                              if el[6] != 'core' and not lit(el[6]) and not clear(el[6])], 'warp_drive')
    save('warp_drive_glass', [element(0, i, el, uv, 0.5) for i, el in enumerate(drive) if clear(el[6])], 'warp_drive')
    # nitrógeno: altura completa desde y = 0; el cliente lo sube a FLUID_Y0 y lo escala en y según el nivel
    save('warp_drive_fluid', [element(1, i, el, uv, 0.5) for i, el in enumerate(FLUID)], 'warp_drive')
    save('warp_drive_glow', [element(0, i, el, uv, 0.5) for i, el in enumerate(drive) if el[6] != 'core' and lit(el[6])], 'warp_drive')
    core = [(i, el) for i, el in enumerate(drive) if el[6] == 'core']
    c = (32, 31, 32)  # centro del núcleo: el modelo suelto va centrado en el origen para girarlo
    save('warp_drive_core', [element(0, i, el, uv, 0.5, (-c[0], -c[1], -c[2])) for i, el in core], 'warp_drive')
    # consola: mesa + anillos (puntos) + un cubo por astro, centrado en el origen
    table = [el for el in CONSOLE if el[6] not in ('holo',) and not el[6].startswith('p_')]
    rings = [el for el in CONSOLE if el[6] == 'holo']
    bodies = [(m, 2.2 if m == 'sun' else 1.4) for m in PLANET_COL]
    cubes = [(-s / 2, -s / 2, -s / 2, s / 2, s / 2, s / 2, 'p_' + m, 0) for m, s in bodies]
    uv, size = pack([(table, 't'), (rings, 'r'), (cubes, 'c')], 'nav_console')
    save('nav_console_table', [element(0, i, el, uv, 1) for i, el in enumerate(table) if el[6] not in GLOW], 'nav_console')
    save('nav_console_glow', [element(0, i, el, uv, 1) for i, el in enumerate(table) if el[6] in GLOW], 'nav_console')
    save('nav_console_rings', [element(1, i, el, uv, 1) for i, el in enumerate(rings)], 'nav_console')
    for i, (m, _) in enumerate(bodies):
        save('nav_console_body_' + m, [element(2, i, cubes[i], uv, 1)], 'nav_console')
    # objetos (inventario): la consola completa a 1/2 y el motor completo a 1/4, con la misma textura de cada uno
    statics = [el for el in CONSOLE if el[6].startswith('p_')]
    uv2, _ = pack([(table, 't'), (rings, 'r'), (statics, 's')], 'nav_console_item')
    save('nav_console_item', [element(0, i, el, uv2, 0.5) for i, el in enumerate(table)]
         + [element(2, i, el, uv2, 0.5) for i, el in enumerate(statics)], 'nav_console_item')
    fluid70 = [(x1, FLUID_Y0, z1, x2, FLUID_Y0 + (y2 - y1) * 0.7, z2, m, r) for (x1, y1, z1, x2, y2, z2, m, r) in FLUID]
    uv3, _ = pack([(drive, 'd'), (fluid70, 'f')], 'warp_drive_item')
    save('warp_drive_item', [element(0, i, el, uv3, 0.25) for i, el in enumerate(drive)]
         + [element(1, i, el, uv3, 0.25) for i, el in enumerate(fluid70)], 'warp_drive_item', translucent=True)
    return [m for m, _ in bodies]


if __name__ == '__main__':
    if sys.argv[1] == '--export':
        print(export(sys.argv[2]))
        sys.exit(0)
    fill = float(sys.argv[3]) if len(sys.argv) > 3 else 0.65
    shown = DRIVE + [(x1, FLUID_Y0 + y1 * fill, z1, x2, FLUID_Y0 + y2 * fill, z2, m, r) for (x1, y1, z1, x2, y2, z2, m, r) in FLUID] if fill > 0 else DRIVE
    sheet(shown, (32, 32), (1400, 720), 4.6, 520, f'Motor de distorsión (4 × 4 bloques, 3 de alto) — nitrógeno al {int(fill * 100)} %',
          ['Frente', 'Lado (entrada de eje)'], sys.argv[1])
    sheet(CONSOLE, (16, 16), (1100, 560), 7.5, 420, 'Consola de navegación (2 × 2 bloques)',
          ['Frente', 'Lado'], sys.argv[2])
