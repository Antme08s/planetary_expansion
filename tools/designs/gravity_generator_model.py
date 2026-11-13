"""Modelo 3D del generador de gravedad (1 de ancho x 2 de alto; aprobado por el usuario, en el mod desde 0.7.34).

Define el modelo como cajas (unidades de 1/16 de bloque; ocupa x,z 0..16 e y 0..32) y pinta cada cara por código.
Uso: python3 gravity_generator_model.py <vista_previa.png>
     python3 gravity_generator_model.py --export <carpeta>   (textura gravity_generator_3d.png y modelos JSON)
"""
import math, random, sys
from PIL import Image, ImageDraw, ImageFont

H = lambda s: tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
BR_HI, BR_L, BR_M, BR_D, BR_DD = 'f0d080', 'e0b866', 'cea05a', '9e6947', '6b4433'
V_DD, V_D, V_M, V_L, V_HI = '24123a', '4a2585', '7a3fd0', 'a86bff', 'efe0ff'
DARK, DARK2 = '2b2024', '3a2c30'
SH_L, SH_M, SH_D = 'b6b1a6', '8f8a80', '67635c'

# (x1, y1, z1, x2, y2, z2, material, giro en Y en grados)
ELEMENTS = [
    (0, 0, 0, 16, 3, 16, 'plate', 0),          # pie
    (1, 3, 1, 15, 11, 15, 'body', 0),          # caja de la máquina
    # cojinetes: medio píxel más cortos que el eje de Create (que llega al borde del bloque); si acabaran en el
    # mismo plano, las dos caras se pelean y parpadean (lo vio el usuario en 0.7.34)
    (0.5, 5, 5, 1, 11, 11, 'bearing', 0),
    (15, 5, 5, 15.5, 11, 11, 'bearing', 0),
    (0, 6, 6, 16, 10, 10, 'shaft', 0),         # eje de Create (lo dibuja Create; aquí solo para la vista previa)
    (2, 11, 2, 14, 13, 14, 'plate', 0),        # collar
    (5, 13, 5, 11, 15, 11, 'emitter', 0),      # emisor de abajo
    (2, 13, 2, 4, 28, 4, 'post', 0), (12, 13, 2, 14, 28, 4, 'post', 0),      # jaula abierta
    (2, 13, 12, 4, 28, 14, 'post', 0), (12, 13, 12, 14, 28, 14, 'post', 0),
    (5, 17.5, 5, 11, 23.5, 11, 'core', 22.5),  # núcleo flotante (en el juego giraría al estar encendido)
    (5, 26, 5, 11, 28, 11, 'emitter_down', 0), # emisor de arriba
    (2, 28, 2, 14, 30, 14, 'plate', 0),        # tapa
    (5, 30, 5, 11, 31, 11, 'plate', 0),
    (6, 31, 6, 10, 32, 10, 'emitter', 0),      # lente de arriba
]


BRASS = ['5c382b', '7d4f38', '9e6947', 'bd8a4e', 'd6a85c', 'e8c26e', 'f6dc92']      # oscuro -> claro
VIOLET = ['160a26', '24123a', '3a1c6b', '5a2ca8', '7a3fd0', 'a86bff', 'cfa8ff', 'efe0ff']
IRON = ['1a1214', '2b2024', '3a2c30', '4d3d40']
STONE = ['4a4742', '67635c', '8f8a80', 'b6b1a6', 'cfcabd']


def paint(mat, face, w, h, seed):
    """Imagen w x h de una cara, con sombreado. face: up, down, north(-z), south(+z), west(-x), east(+x)."""
    rnd = random.Random(seed)
    im = Image.new('RGBA', (w, h))
    px = im.load()
    side = face not in ('up', 'down')

    def ramp(cols, t, x, y):
        """t en 0..1 -> color de la rampa, con tramado en damero para suavizar los escalones."""
        v = max(0.0, min(0.999, t)) * (len(cols) - 1)
        return H(cols[min(int(v + 0.5), len(cols) - 1)])

    def metal(cols, base=0.62, grad=0.34, noise=0.035):
        """Metal: en los lados, más claro arriba y más oscuro abajo; bisel claro arriba-izquierda, oscuro abajo-derecha."""
        for y in range(h):
            for x in range(w):
                t = base + rnd.uniform(-noise, noise)
                if side: t += grad * (0.5 - y / max(1, h - 1))
                else: t += 0.08 - 0.008 * (x + y)            # luz desde una esquina en las caras de arriba
                if w >= 3 and h >= 2:
                    if y == 0 or x == 0: t += 0.20
                    if y == h - 1 or x == w - 1: t -= 0.24
                    if h >= 5 and w >= 5:
                        if y == 1 or x == 1: t += 0.07
                        if y == h - 2 or x == w - 2: t -= 0.08
                px[x, y] = ramp(cols, t, x, y)

    def rivet(x, y, cols=BRASS):
        if 0 <= x < w - 1 and 0 <= y < h - 1:
            px[x, y] = H(cols[-1]); px[x + 1, y + 1] = H(cols[0])

    if mat in ('plate', 'knob'):
        metal(BRASS)
        if face == 'up' and w >= 12:
            for (x, y) in [(1, 1), (w - 3, 1), (1, h - 3), (w - 3, h - 3)]: rivet(x, y)
            # sombra de lo que tiene encima (oclusión): un marco más oscuro hacia el centro
            for y in range(3, h - 3):
                for x in range(3, w - 3):
                    edge = min(x - 3, y - 3, w - 4 - x, h - 4 - y)
                    if edge == 0: px[x, y] = H(BRASS[1])
                    elif edge == 1: px[x, y] = H(BRASS[2])
    elif mat == 'body':
        metal(BRASS, base=0.55, grad=0.34)
        for x in range(w): px[x, 0] = H(BRASS[1])                 # sombra bajo el collar
        if face in ('north', 'south'):                            # panel hundido con rejillas que brillan
            for y in range(1, h - 1):
                for x in range(1, w - 1):
                    if x in (1, w - 2) or y in (1, h - 2):
                        px[x, y] = H(BRASS[0] if (y == 1 or x == 1) else BRASS[3]); continue
                    if y % 2 == 0:
                        c = abs(x - (w - 1) / 2) / (w / 2)
                        px[x, y] = ramp(VIOLET, 0.95 - 0.75 * c, x, y)
                    else:
                        px[x, y] = H(IRON[0] if y == 3 else IRON[1])
        else:
            for y in range(1, h - 1):
                for x in (2, w - 3): px[x, y] = H(BRASS[1]); px[x + (1 if x == 2 else -1), y] = H(BRASS[5])
            rivet(1, h - 3); rivet(w - 3, h - 3)
    elif mat == 'bearing':
        metal(IRON, base=0.5, grad=0.5, noise=0.05)
    elif mat == 'shaft':
        if face in ('east', 'west'):
            metal(STONE, base=0.45, grad=0.3)
            for i in range(1, min(w, h) - 1): px[i, i] = H(STONE[0]); px[min(w, h) - 1 - i, i] = H(STONE[0])
        else:
            for y in range(h):
                for x in range(w):
                    px[x, y] = H([STONE[4], STONE[3], STONE[2], STONE[1]][y % 4] if h == 4 else STONE[2])
    elif mat == 'post':
        for y in range(h):
            ao = min(y, h - 1 - y)                               # más oscuro pegado a la tapa y al collar
            for x in range(w):
                t = (0.82 if x == 0 else 0.48) - (0.22 if ao == 0 else 0.10 if ao == 1 else 0) + rnd.uniform(-0.04, 0.04)
                px[x, y] = ramp(BRASS, t, x, y)
            if y % 5 == 2:                                       # abrazaderas: línea oscura con brillo encima
                for x in range(w): px[x, y] = H(BRASS[0])
                if y > 0:
                    for x in range(w): px[x, y - 1] = H(BRASS[6] if x == 0 else BRASS[4])
    elif mat == 'ring':
        metal(VIOLET, base=0.6, grad=0.2)
    elif mat in ('emitter', 'emitter_down'):
        metal(VIOLET, base=0.30, grad=0.30, noise=0.04)
        glow_face = (mat == 'emitter' and face == 'up') or (mat == 'emitter_down' and face == 'down')
        if glow_face:
            c = (w - 1) / 2
            for y in range(h):
                for x in range(w):
                    r = max(abs(x - c), abs(y - c)) / max(1.0, c)
                    px[x, y] = H(VIOLET[1]) if r > 0.95 else ramp(VIOLET, 1.05 - 0.75 * r, x, y)
        elif side:
            for x in range(w): px[x, 0 if mat == 'emitter' else h - 1] = H(VIOLET[5])   # borde que refleja el brillo
    elif mat == 'core':
        cx, cy = (w - 1) / 2, (h - 1) / 2
        for y in range(h):
            for x in range(w):
                r = math.hypot(x - cx, y - cy) / math.hypot(cx, cy)
                t = 1.0 - 0.62 * r + 0.10 * ((cx - x) + (cy - y)) / (w + h)     # centro brillante, luz arriba-izquierda
                if x == w - 1 or y == h - 1: t -= 0.12
                px[x, y] = ramp(VIOLET[2:], t, x, y)
    return im


def faces(el):
    """Caras de una caja: (nombre, esquina origen, vector u, vector v, normal) con u,v de longitud 1 texel."""
    x1, y1, z1, x2, y2, z2, mat, rot = el
    cx, cz = (x1 + x2) / 2, (z1 + z2) / 2
    a = math.radians(rot)

    def R(p):
        dx, dz = p[0] - cx, p[2] - cz
        return (cx + dx * math.cos(a) - dz * math.sin(a), p[1], cz + dx * math.sin(a) + dz * math.cos(a))

    def D(v):
        return (v[0] * math.cos(a) - v[2] * math.sin(a), v[1], v[0] * math.sin(a) + v[2] * math.cos(a))

    raw = [('up', (x1, y2, z1), (1, 0, 0), (0, 0, 1), (0, 1, 0), x2 - x1, z2 - z1),
           ('down', (x1, y1, z2), (1, 0, 0), (0, 0, -1), (0, -1, 0), x2 - x1, z2 - z1),
           ('south', (x1, y2, z2), (1, 0, 0), (0, -1, 0), (0, 0, 1), x2 - x1, y2 - y1),
           ('north', (x2, y2, z1), (-1, 0, 0), (0, -1, 0), (0, 0, -1), x2 - x1, y2 - y1),
           ('east', (x2, y2, z2), (0, 0, -1), (0, -1, 0), (1, 0, 0), z2 - z1, y2 - y1),
           ('west', (x1, y2, z1), (0, 0, 1), (0, -1, 0), (-1, 0, 0), z2 - z1, y2 - y1)]
    return [(n, R(o), D(u), D(v), D(nm), w, h) for (n, o, u, v, nm, w, h) in raw]


def render(img, ox, oy, S, turn=0):
    """Isométrico con el observador en +x,+y,+z. turn: cuartos de vuelta del modelo sobre Y."""
    d = ImageDraw.Draw(img)

    def T(p):
        x, y, z = p[0] - 8, p[1], p[2] - 8
        for _ in range(turn): x, z = -z, x
        return (x, y, z)

    def P(p):
        x, y, z = T(p)
        return (ox + (x - z) * 0.866 * S, oy + (x + z) * 0.5 * S - y * S)

    quads = []
    for i, el in enumerate(ELEMENTS):
        for (name, o, u, v, n, w, h) in faces(el):
            nx, ny, nz = T((n[0] + 8, n[1], n[2] + 8))
            if nx + ny + nz <= 0.01: continue
            shade = max(0.45, min(1.0, 0.72 + 0.28 * ny + 0.10 * nz - 0.10 * nx))
            tw, th = max(1, math.ceil(w)), max(1, math.ceil(h))
            tex = paint(el[6], name, tw, th, i * 7 + len(name)).load()
            su, sv = w / tw, h / th
            for j in range(th):
                for k in range(tw):
                    a = [o[m] + u[m] * su * k + v[m] * sv * j for m in range(3)]
                    b = [a[m] + u[m] * su for m in range(3)]
                    c = [b[m] + v[m] * sv for m in range(3)]
                    e = [a[m] + v[m] * sv for m in range(3)]
                    ctr = T([(a[m] + c[m]) / 2 for m in range(3)])
                    col = tex[k, j]
                    glow = el[6] in ('core',) or (el[6].startswith('emitter') and name in ('up', 'down'))
                    f = 1.0 if glow else shade
                    quads.append((ctr[0] + ctr[1] + ctr[2], [P(a), P(b), P(c), P(e)], tuple(int(x * f) for x in col[:3]) + (255,)))
    quads.sort(key=lambda q: q[0])
    for _, pts, col in quads: d.polygon(pts, fill=col)


def export(outdir, ns='planetary_expansion'):
    """Escribe la textura (atlas) y los modelos JSON: bloque (sin eje ni núcleo), núcleo suelto e ítem (todo)."""
    import json, os
    imgs = []
    for i, el in enumerate(ELEMENTS):
        for (name, o, u, v, n, w, h) in faces(el):
            tw, th = max(1, math.ceil(w)), max(1, math.ceil(h))
            imgs.append((i, name, paint(el[6], name, tw, th, i * 7 + len(name))))
    for size in (64, 128, 256):                       # empaquetado por estantes
        order = sorted(range(len(imgs)), key=lambda k: (-imgs[k][2].size[1], -imgs[k][2].size[0]))
        pos, x, y, row = {}, 0, 0, 0
        for k in order:
            w, h = imgs[k][2].size
            if x + w > size: x, y, row = 0, y + row, 0
            pos[k] = (x, y); x += w; row = max(row, h)
        if y + row <= size: break
    atlas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    uv = {}
    for k, (i, name, im) in enumerate(imgs):
        atlas.paste(im, pos[k])
        w, h = im.size
        f = 16.0 / size
        uv[(i, name)] = [round(pos[k][0] * f, 4), round(pos[k][1] * f, 4), round((pos[k][0] + w) * f, 4), round((pos[k][1] + h) * f, 4)]
    atlas.save(os.path.join(outdir, 'gravity_generator_3d.png'))

    def element(i, rot=None, shade=True):
        x1, y1, z1, x2, y2, z2, mat, _ = ELEMENTS[i]
        e = {'from': [x1, y1, z1], 'to': [x2, y2, z2],
             'faces': {n: {'uv': uv[(i, n)], 'texture': '#t'} for n in ('up', 'down', 'north', 'south', 'east', 'west')}}
        if rot: e['rotation'] = {'origin': [(x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2], 'axis': 'y', 'angle': rot}
        if not shade: e['shade'] = False
        return e

    tex = {'t': f'{ns}:block/gravity_generator_3d', 'particle': 'create:block/brass_casing'}
    idx = lambda m: [i for i, el in enumerate(ELEMENTS) if el[6] == m]
    core, shaft = idx('core'), idx('shaft')
    block = {'ambientocclusion': False, 'textures': tex,
             'elements': [element(i) for i in range(len(ELEMENTS)) if i not in core + shaft]}
    core_model = {'ambientocclusion': False, 'textures': tex, 'elements': [element(i, shade=False) for i in core]}
    # el modelo de Minecraft gira al revés que la vista previa en Y; para el ítem da igual el sentido
    item = {'ambientocclusion': False, 'gui_light': 'side', 'textures': tex,
            'elements': [element(i, rot=22.5 if i in core else None, shade=i not in core) for i in range(len(ELEMENTS))],
            'display': {
                'gui': {'rotation': [30, 225, 0], 'translation': [0, -2.75, 0], 'scale': [0.40, 0.40, 0.40]},
                'ground': {'translation': [0, 1, 0], 'scale': [0.22, 0.22, 0.22]},
                'fixed': {'translation': [0, -3.6, 0], 'scale': [0.45, 0.45, 0.45]},
                'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 1.5, 0], 'scale': [0.28, 0.28, 0.28]},
                'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 1.5, 0], 'scale': [0.28, 0.28, 0.28]},
                'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.30, 0.30, 0.30]},
                'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.30, 0.30, 0.30]}}}
    for name, obj in (('gravity_generator_block', block), ('gravity_generator_core', core_model), ('gravity_generator_item', item)):
        with open(os.path.join(outdir, name + '.json'), 'w') as fh: json.dump(obj, fh, indent=1)
    return size


if __name__ == '__main__':
    if sys.argv[1] == '--export':
        print('atlas', export(sys.argv[2]))
        sys.exit(0)
    out = sys.argv[1]
    W, Hh, S = 1000, 720, 12
    img = Image.new('RGBA', (W, Hh), (28, 30, 36, 255))
    d = ImageDraw.Draw(img)
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 16)
    render(img, 270, 560, S, 0)
    render(img, 730, 560, S, 1)
    d.text((270 - 95, 670), 'Lado del eje a la derecha', fill=(220, 220, 220, 255), font=F)
    d.text((730 - 90, 670), 'Girado: panel al frente', fill=(220, 220, 220, 255), font=F)
    d.text((20, 16), 'Generador de gravedad: propuesta de modelo 3D (1 bloque de ancho × 2 de alto)', fill=(235, 235, 235, 255), font=F)
    img.save(out)
