"""Modelo 3D del Transportador espacial (1.0.4): el eje de Create lo atraviesa de punta a punta; alrededor, tres anillos
de bobina moradas que brillan, cuatro columnas de latón y dos bridas con agujero para el eje. Se dibuja con el eje en Y
(el blockstate lo gira para X y Z), así que se ve bien en cualquier dirección.
Uso: python3 space_transporter_model.py <vista_previa.png>
     python3 space_transporter_model.py --export <carpeta>   (space_transporter_3d.png y modelos JSON)
"""
import json, math, os, random, sys
from PIL import Image, ImageDraw, ImageFont
from box_model import H, ramp, faces, render, atlas, element, BLOCK_DISPLAY

BRASS = ['5c382b', '7d4f38', '9e6947', 'bd8a4e', 'd6a85c', 'e8c26e', 'f6dc92']
VIOLET = ['160a26', '24123a', '3a1c6b', '5a2ca8', '7a3fd0', 'a86bff', 'cfa8ff', 'efe0ff']
IRON = ['1a1214', '2b2024', '3a2c30', '4d3d40', '5e4c50']
STONE = ['4a4742', '67635c', '8f8a80', 'b6b1a6', 'cfcabd']


def ring(y1, y2, outer, inner, mat):
    """Anillo cuadrado alrededor del eje (hueco en el centro) hecho con 4 cajas."""
    a, b = outer
    c, d = inner
    return [(a, y1, a, b, y2, c, mat, 0), (a, y1, d, b, y2, b, mat, 0),
            (a, y1, c, c, y2, d, mat, 0), (d, y1, c, b, y2, d, mat, 0)]


ELEMENTS = (
    ring(0, 2, (0, 16), (6, 10), 'flange') +          # brida de abajo (agujero para el eje)
    ring(14, 16, (0, 16), (6, 10), 'flange') +        # brida de arriba
    [(1, 2, 1, 3, 14, 3, 'post', 0), (13, 2, 1, 15, 14, 3, 'post', 0),
     (1, 2, 13, 3, 14, 15, 'post', 0), (13, 2, 13, 15, 14, 15, 'post', 0)] +
    ring(3.5, 5.5, (3, 13), (5.5, 10.5), 'coil') +
    ring(7, 9, (2.5, 13.5), (5.5, 10.5), 'coil') +
    ring(10.5, 12.5, (3, 13), (5.5, 10.5), 'coil') +
    [(6, 0, 6, 10, 16, 10, 'shaft', 0)]               # eje de Create (lo dibuja Create; solo vista previa e ítem)
)


def glowing(mat, face):
    return mat == 'coil'


def paint(mat, face, w, h, seed):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (w, h))
    px = im.load()
    side = face not in ('up', 'down')

    def metal(cols, base=0.6, grad=0.34, noise=0.035):
        for y in range(h):
            for x in range(w):
                t = base + rnd.uniform(-noise, noise)
                if side: t += grad * (0.5 - y / max(1, h - 1))
                else: t += 0.08 - 0.008 * (x + y)
                if w >= 3 and h >= 2:
                    if y == 0 or x == 0: t += 0.20
                    if y == h - 1 or x == w - 1: t -= 0.24
                px[x, y] = ramp(cols, t)

    if mat == 'flange':
        metal(BRASS)
        if face in ('up', 'down') and w >= 10 and h >= 4:
            for x in (1, w - 3):
                px[x, 1] = H(BRASS[-1]); px[x + 1, 2] = H(BRASS[0])
    elif mat == 'post':
        for y in range(h):
            ao = min(y, h - 1 - y)
            for x in range(w):
                t = (0.82 if x == 0 else 0.48) - (0.22 if ao == 0 else 0.10 if ao == 1 else 0) + rnd.uniform(-0.04, 0.04)
                px[x, y] = ramp(BRASS, t)
            if y % 5 == 2:
                for x in range(w): px[x, y] = H(BRASS[0])
    elif mat == 'coil':
        # bobina: espiras de cobre violáceo que brillan; en los lados, líneas horizontales
        for y in range(h):
            for x in range(w):
                if side:
                    t = 0.85 if y % 2 == 0 else 0.55
                    if x == 0 or x == w - 1: t -= 0.15
                else:
                    t = 0.7 + 0.15 * ((x + y) % 2)
                px[x, y] = ramp(VIOLET[2:], t + rnd.uniform(-0.03, 0.03))
    elif mat == 'shaft':
        if face in ('up', 'down'):
            metal(STONE, base=0.45, grad=0.3)
        else:
            for y in range(h):
                for x in range(w):
                    px[x, y] = H([STONE[4], STONE[3], STONE[2], STONE[1]][x % 4])
    return im


def export(outdir, ns='planetary_expansion'):
    uv = atlas(ELEMENTS, paint, os.path.join(outdir, 'space_transporter_3d.png'))
    tex = {'t': f'{ns}:block/space_transporter_3d', 'particle': 'create:block/brass_casing'}
    shaft = [i for i, el in enumerate(ELEMENTS) if el[6] == 'shaft']
    block = {'ambientocclusion': False, 'textures': tex,
             'elements': [element(ELEMENTS, uv, i, shade=ELEMENTS[i][6] != 'coil') for i in range(len(ELEMENTS)) if i not in shaft]}
    item = {'ambientocclusion': False, 'gui_light': 'side', 'textures': tex, 'display': BLOCK_DISPLAY,
            'elements': [element(ELEMENTS, uv, i, shade=ELEMENTS[i][6] != 'coil') for i in range(len(ELEMENTS))]}
    for name, obj in (('space_transporter_3d_block', block), ('space_transporter_3d_item', item)):
        with open(os.path.join(outdir, name + '.json'), 'w') as fh: json.dump(obj, fh, indent=1)


if __name__ == '__main__':
    if sys.argv[1] == '--export':
        export(sys.argv[2]); sys.exit(0)
    W, Hh, S = 1000, 520, 13
    img = Image.new('RGBA', (W, Hh), (28, 30, 36, 255))
    d = ImageDraw.Draw(img)
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 16)
    render(img, 270, 400, S, ELEMENTS, paint, glowing)
    # girado: eje horizontal (como queda si se pone de lado)
    import box_model
    side = [(y1, 16 - x2, z1, y2, 16 - x1, z2, m, r) for (x1, y1, z1, x2, y2, z2, m, r) in ELEMENTS]
    render(img, 730, 400, S, side, paint, glowing)
    d.text((270 - 70, 470), 'Eje vertical', fill=(220, 220, 220, 255), font=F)
    d.text((730 - 80, 470), 'Eje horizontal', fill=(220, 220, 220, 255), font=F)
    d.text((20, 16), 'Transportador espacial: propuesta de modelo 3D (el eje gira dentro de las bobinas)', fill=(235, 235, 235, 255), font=F)
    img.save(sys.argv[1])
