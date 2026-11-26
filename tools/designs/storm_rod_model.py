"""Modelo 3D del pararrayos de tormenta (1.0.4): base de cobre, varilla con dos anillos y una bobina, y un cristal de
fulgurita violeta en la punta. Uso: python3 storm_rod_model.py <vista.png> | --export <carpeta>"""
import json, math, os, random, sys
from PIL import Image, ImageDraw, ImageFont
from box_model import H, ramp, faces, render, atlas, element, BLOCK_DISPLAY

CU = ['5a2e1c', '7c4026', '9e5734', 'bf7044', 'd98a58', 'eda878', 'f6c89c']
VERD = ['1e4a40', '2c6a5a', '3f8a76', '58a892', '7cc4ae']
VIO = ['2a1240', '4a2280', '7038b8', '9a58e8', 'c08cff', 'e2c4ff', 'f6ecff']

ELEMENTS = [
    (5, 0, 5, 11, 1.5, 11, 'base', 0),
    (6.5, 1.5, 6.5, 9.5, 2.5, 9.5, 'base', 0),
    (7.25, 2.5, 7.25, 8.75, 13.0, 8.75, 'rod', 0),
    (6.4, 5.0, 6.4, 9.6, 6.2, 9.6, 'ring', 0),
    (6.6, 8.6, 6.6, 9.4, 9.6, 9.4, 'ring', 0),
    (6.9, 10.4, 6.9, 9.1, 12.2, 9.1, 'coil', 0),
    (6.6, 12.6, 6.6, 9.4, 13.6, 9.4, 'ring', 0),
    (7.0, 13.4, 7.0, 9.0, 16.0, 9.0, 'crystal', 45),
]


def glowing(mat, face):
    return mat == 'crystal'


def paint(mat, face, w, h, seed):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (w, h))
    px = im.load()
    side = face not in ('up', 'down')
    for y in range(h):
        for x in range(w):
            if mat in ('base', 'rod', 'ring'):
                t = 0.6 + rnd.uniform(-0.04, 0.04) + (0.3 * (0.5 - y / max(1, h - 1)) if side else 0.05)
                if x == 0 or y == 0: t += 0.18
                if x == w - 1 or y == h - 1: t -= 0.2
                c = ramp(CU, t)
                if mat == 'base' and rnd.random() < 0.12:
                    c = ramp(VERD, 0.5 + rnd.uniform(-0.1, 0.1))
                px[x, y] = c
            elif mat == 'coil':
                px[x, y] = ramp(CU, 0.85 if y % 2 == 0 else 0.35)
            elif mat == 'crystal':
                cx, cy = (w - 1) / 2, (h - 1) / 2
                r = math.hypot(x - cx, y - cy) / max(0.5, math.hypot(cx, cy))
                px[x, y] = ramp(VIO[2:], 1.0 - 0.55 * r)
    return im


def export(outdir, ns='planetary_expansion'):
    uv = atlas(ELEMENTS, paint, os.path.join(outdir, 'storm_rod_3d.png'))
    tex = {'t': f'{ns}:block/storm_rod_3d', 'particle': f'{ns}:block/storm_rod_3d'}
    block = {'ambientocclusion': False, 'textures': tex,
             'elements': [element(ELEMENTS, uv, i, shade=ELEMENTS[i][6] != 'crystal') for i in range(len(ELEMENTS))]}
    item = dict(block)
    item['display'] = BLOCK_DISPLAY
    item['gui_light'] = 'side'
    for name, obj in (('storm_rod_3d_block', block), ('storm_rod_3d_item', item)):
        with open(os.path.join(outdir, name + '.json'), 'w') as fh: json.dump(obj, fh, indent=1)


if __name__ == '__main__':
    if sys.argv[1] == '--export':
        export(sys.argv[2]); sys.exit(0)
    img = Image.new('RGBA', (500, 460), (30, 22, 40, 255))
    render(img, 250, 400, 18, ELEMENTS, paint, glowing)
    img.save(sys.argv[1])
