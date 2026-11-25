"""Utilidades comunes para los modelos 3D de máquinas hechos con cajas (sacadas de gravity_generator_model.py):
caras de cada caja, vista previa isométrica y empaquetado del atlas + elementos JSON.
Cada caja: (x1, y1, z1, x2, y2, z2, material, giro en Y). paint(mat, cara, w, h, semilla) -> Image."""
import json, math, os
from PIL import Image, ImageDraw

H = lambda s: tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def ramp(cols, t):
    v = max(0.0, min(0.999, t)) * (len(cols) - 1)
    return H(cols[min(int(v + 0.5), len(cols) - 1)])


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


def render(img, ox, oy, S, ELEMENTS, paint, glowing, turn=0):
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
                    glow = glowing(el[6], name)
                    f = 1.0 if glow else shade
                    quads.append((ctr[0] + ctr[1] + ctr[2], [P(a), P(b), P(c), P(e)], tuple(int(x * f) for x in col[:3]) + (255,)))
    quads.sort(key=lambda q: q[0])
    for _, pts, col in quads: d.polygon(pts, fill=col)




def atlas(ELEMENTS, paint, png_path):
    """Pinta todas las caras en un atlas y devuelve {(i, cara): uv} en unidades de modelo (0..16)."""
    imgs = []
    for i, el in enumerate(ELEMENTS):
        for (name, o, u, v, n, w, h) in faces(el):
            tw, th = max(1, math.ceil(w)), max(1, math.ceil(h))
            imgs.append((i, name, paint(el[6], name, tw, th, i * 7 + len(name))))
    for size in (32, 64, 128, 256):
        order = sorted(range(len(imgs)), key=lambda k: (-imgs[k][2].size[1], -imgs[k][2].size[0]))
        pos, x, y, row = {}, 0, 0, 0
        for k in order:
            w, h = imgs[k][2].size
            if x + w > size: x, y, row = 0, y + row, 0
            pos[k] = (x, y); x += w; row = max(row, h)
        if y + row <= size: break
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    uv = {}
    for k, (i, name, im) in enumerate(imgs):
        img.paste(im, pos[k])
        w, h = im.size
        f = 16.0 / size
        uv[(i, name)] = [round(pos[k][0] * f, 4), round(pos[k][1] * f, 4), round((pos[k][0] + w) * f, 4), round((pos[k][1] + h) * f, 4)]
    img.save(png_path)
    return uv


def element(ELEMENTS, uv, i, shade=True):
    x1, y1, z1, x2, y2, z2, mat, rot = ELEMENTS[i]
    e = {'from': [x1, y1, z1], 'to': [x2, y2, z2],
         'faces': {n: {'uv': uv[(i, n)], 'texture': '#t'} for n in ('up', 'down', 'north', 'south', 'east', 'west')}}
    if rot: e['rotation'] = {'origin': [(x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2], 'axis': 'y', 'angle': rot}
    if not shade: e['shade'] = False
    return e


BLOCK_DISPLAY = {
    'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]},
    'ground': {'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
    'fixed': {'scale': [0.5, 0.5, 0.5]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
    'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [0.40, 0.40, 0.40]},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'scale': [0.40, 0.40, 0.40]}}
