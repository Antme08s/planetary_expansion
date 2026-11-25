"""Modelo 3D del Congelador del tiempo espacial (1.0.4): jaula de acero con un reloj de arena de hielo flotando entre dos
emisores. Apagado: hielo gris. Encendido: hielo cian brillante.
Uso: python3 time_freezer_model.py <vista_previa.png>
     python3 time_freezer_model.py --export <carpeta>   (time_freezer_3d_off.png, time_freezer_3d_on.png y modelos JSON)
"""
import json, math, os, random, sys
from PIL import Image, ImageDraw, ImageFont
from box_model import H, ramp, faces, render, atlas, element, BLOCK_DISPLAY

STEEL = ['171a21', '232833', '323947', '465063', '5f697f', '828ca3', 'aab3c8']
ON = ['08202e', '0f3f57', '1a6f8f', '33a9cc', '6fdcf5', 'baf4ff', 'f0ffff']
OFF = ['15181f', '222731', '343b49', '4d5668', '6b768c', '8f9ab0', 'b5bed0']
ACTIVE = [False]

ELEMENTS = [
    (0, 0, 0, 16, 2, 16, 'base', 0),
    (1, 2, 1, 3, 14, 3, 'post', 0), (13, 2, 1, 15, 14, 3, 'post', 0),
    (1, 2, 13, 3, 14, 15, 'post', 0), (13, 2, 13, 15, 14, 15, 'post', 0),
    (3, 2, 1.5, 13, 3, 2.5, 'rail', 0), (3, 2, 13.5, 13, 3, 14.5, 'rail', 0),
    (1.5, 2, 3, 2.5, 3, 13, 'rail', 0), (13.5, 2, 3, 14.5, 3, 13, 'rail', 0),
    (3, 13, 1.5, 13, 14, 2.5, 'rail', 0), (3, 13, 13.5, 13, 14, 14.5, 'rail', 0),
    (1.5, 13, 3, 2.5, 14, 13, 'rail', 0), (13.5, 13, 3, 14.5, 14, 13, 'rail', 0),
    (4, 2, 4, 12, 3, 12, 'emitter', 0),
    (4, 13, 4, 12, 14, 12, 'emitter_down', 0),
    (0, 14, 0, 16, 16, 16, 'cap', 0),
    # reloj de arena de hielo
    (4.5, 4, 4.5, 11.5, 5, 11.5, 'core', 0),
    (5.5, 5, 5.5, 10.5, 6.5, 10.5, 'core', 0),
    (7, 6.5, 7, 9, 9.5, 9, 'core', 0),
    (5.5, 9.5, 5.5, 10.5, 11, 10.5, 'core', 0),
    (4.5, 11, 4.5, 11.5, 12, 11.5, 'core', 0),
]


def glow_pal():
    return ON if ACTIVE[0] else OFF


def glowing(mat, face):
    return ACTIVE[0] and (mat == 'core' or (mat == 'emitter' and face == 'up') or (mat == 'emitter_down' and face == 'down'))


def paint(mat, face, w, h, seed):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (w, h))
    px = im.load()
    side = face not in ('up', 'down')
    G = glow_pal()

    def metal(cols, base=0.55, grad=0.34, noise=0.035):
        for y in range(h):
            for x in range(w):
                t = base + rnd.uniform(-noise, noise)
                if side: t += grad * (0.5 - y / max(1, h - 1))
                else: t += 0.08 - 0.008 * (x + y)
                if w >= 3 and h >= 2:
                    if y == 0 or x == 0: t += 0.20
                    if y == h - 1 or x == w - 1: t -= 0.24
                px[x, y] = ramp(cols, t)

    if mat in ('base', 'cap'):
        metal(STEEL)
        if face == 'up' and w >= 12:
            for (x, y) in [(1, 1), (w - 3, 1), (1, h - 3), (w - 3, h - 3)]:
                px[x, y] = H(STEEL[-1]); px[x + 1, y + 1] = H(STEEL[0])
            if mat == 'cap':   # rejilla de ventilación
                for y in range(5, 11):
                    for x in range(5, 11):
                        px[x, y] = H(STEEL[0] if y % 2 else STEEL[3])
        elif side and h >= 2:  # franja de estado
            y = h // 2
            for x in range(2, w - 2):
                px[x, y] = H(G[4] if ACTIVE[0] else G[2])
    elif mat == 'post':
        for y in range(h):
            ao = min(y, h - 1 - y)
            for x in range(w):
                t = (0.80 if x == 0 else 0.45) - (0.22 if ao == 0 else 0.10 if ao == 1 else 0) + rnd.uniform(-0.04, 0.04)
                px[x, y] = ramp(STEEL, t)
            if side and y % 4 == 2:   # bandas de escarcha
                for x in range(w): px[x, y] = H(G[3])
    elif mat == 'rail':
        metal(STEEL, base=0.45, grad=0.2)
    elif mat in ('emitter', 'emitter_down'):
        metal(STEEL, base=0.30, grad=0.30, noise=0.04)
        on_face = (mat == 'emitter' and face == 'up') or (mat == 'emitter_down' and face == 'down')
        if on_face:
            c = (w - 1) / 2
            for y in range(h):
                for x in range(w):
                    r = max(abs(x - c), abs(y - c)) / max(1.0, c)
                    px[x, y] = H(G[1]) if r > 0.95 else ramp(G, 1.0 - 0.7 * r)
        elif side:
            for x in range(w): px[x, 0 if mat == 'emitter' else h - 1] = H(G[4])
    elif mat == 'core':
        cx, cy = (w - 1) / 2, (h - 1) / 2
        for y in range(h):
            for x in range(w):
                r = math.hypot(x - cx, y - cy) / max(0.5, math.hypot(cx, cy))
                t = 0.95 - 0.5 * r + 0.08 * ((cx - x) + (cy - y)) / (w + h)
                if (x + 2 * y + seed) % 7 == 0: t += 0.12     # destellos de hielo
                px[x, y] = ramp(G[1:], t)
    return im


def export(outdir, ns='planetary_expansion'):
    models = {}
    for state in ('off', 'on'):
        ACTIVE[0] = state == 'on'
        uv = atlas(ELEMENTS, paint, os.path.join(outdir, f'time_freezer_3d_{state}.png'))
        tex = {'t': f'{ns}:block/time_freezer_3d_{state}', 'particle': f'{ns}:block/time_freezer_3d_{state}'}
        glow = lambda i: glowing(ELEMENTS[i][6], 'up') or glowing(ELEMENTS[i][6], 'down')
        models[state] = {'ambientocclusion': False, 'textures': tex,
                         'elements': [element(ELEMENTS, uv, i, shade=not (ACTIVE[0] and ELEMENTS[i][6] == 'core'))
                                      for i in range(len(ELEMENTS))]}
    ACTIVE[0] = False
    item = dict(models['off'])
    item['display'] = BLOCK_DISPLAY
    item['gui_light'] = 'side'
    for name, obj in (('time_freezer_3d_off', models['off']), ('time_freezer_3d_on', models['on']), ('time_freezer_3d_item', item)):
        with open(os.path.join(outdir, name + '.json'), 'w') as fh: json.dump(obj, fh, indent=1)


if __name__ == '__main__':
    if sys.argv[1] == '--export':
        export(sys.argv[2]); sys.exit(0)
    W, Hh, S = 1000, 520, 13
    img = Image.new('RGBA', (W, Hh), (28, 30, 36, 255))
    d = ImageDraw.Draw(img)
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 16)
    for k, state in enumerate((False, True)):
        ACTIVE[0] = state
        render(img, 270 + 460 * k, 400, S, ELEMENTS, paint, glowing)
        d.text((270 + 460 * k - 60, 470), 'Encendido' if state else 'Apagado', fill=(220, 220, 220, 255), font=F)
    d.text((20, 16), 'Congelador del tiempo espacial: propuesta de modelo 3D', fill=(235, 235, 235, 255), font=F)
    img.save(sys.argv[1])
