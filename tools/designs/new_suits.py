"""Trajes nuevos (2026-11-12): traje espacial básico y traje térmico (Ignis).

Uso: python3 -I tools/designs/new_suits.py <carpeta de salida> [tools/designs]

Parten del mismo modelo base que los otros cuatro trajes (armor_v1/glacial_armor: casco con visor, mochila, hombreras),
con la textura repintada por zonas (casco, visor, cuerpo, guantes, botas) conservando el sombreado del original, y
cajas nuevas añadidas con armor_concepts.Suit (mismo estilo de panel que las demás armaduras).
Con el segundo argumento escribe también los .bbmodel (basic_armor.bbmodel y thermal_armor.bbmodel) en esa carpeta.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..'))
import armor_concepts as AC  # noqa: E402
import preview_armor as PV  # noqa: E402

H = AC.H
V1 = os.path.join(HERE, 'armor_v1')


def ramp(*hexes):
    return [H(c) for c in hexes]


def lum(c):
    return 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]


def recolor(s, zones):
    """Repinta la textura por zonas. zones: función (pieza, parte, caja, x, y) -> nombre de rampa o None.
    El tono de cada píxel sale de su luminosidad en el original (así se conserva el dibujo de los paneles)."""
    px = s.tex.load()
    src = s.tex.copy().load()
    done = set()
    for piece, parts in s.model['pieces'].items():
        for part, nodes in parts.items():
            for node in nodes:
                for b in node['boxes']:
                    u, v = b['uv']
                    w, h, d = [round(x) for x in b['size']]
                    for y in range(v, v + d + h):
                        for x in range(u, u + 2 * d + 2 * w):
                            if (x, y) in done or x >= s.tex.width or y >= s.tex.height:
                                continue
                            c = src[x, y]
                            if c[3] == 0:
                                continue
                            visor = c[2] > c[0] + 40
                            name = zones(piece, part, b, x - u, y - v, visor)
                            if not name:
                                continue
                            r = s.ramps[name]
                            lo, hi = s.lum_range.get(name, (80, 215) if name == 'visor' else (100, 248))
                            t = (lum(c) - lo) / (hi - lo)
                            i = int(round(max(0.0, min(1.0, t)) * (len(r) - 1)))
                            px[x, y] = r[i]
                            done.add((x, y))
    s.used = np.asarray(s.tex)[..., 3] > 0


def base_zones(piece, part, b, x, y, visor):
    w, h, d = [round(q) for q in b['size']]
    if piece == 'helmet':
        return 'visor' if visor else 'helmet'
    if piece == 'boots':
        return 'boot'
    if piece == 'chestplate' and part.endswith('arm') and w == 4 and h == 12:
        if y >= d + h - 3 or (y < d and d + w <= x < d + 2 * w):
            return 'glove'      # las tres filas de abajo del brazo y la cara de abajo (la palma): guantes
    if piece == 'chestplate' and part == 'body' and b['uv'] == [59, 34]:
        return 'pack'           # mochila de soporte vital
    if piece == 'chestplate' and part == 'body' and b['uv'] in ([60, 44], [67, 61]):
        return 'pack'
    return 'suit'


# ------------------------------------------------------------------ traje espacial básico
def basic(s):
    """Traje de presión naranja (como los de lanzamiento): casco blanco con visor ahumado, guantes y botas grises,
    mochila blanca. Sin protecciones especiales: solo lo justo para respirar en el espacio."""
    s.ramps.update(
        helmet=ramp('6f7378', '9da2a8', 'c3c7cc', 'dfe2e5', 'eef0f2', 'fafbfc'),
        visor=ramp('101418', '1d2630', '33424f', '5a6e80'),
        suit=ramp('6e2a08', '9c3e0c', 'c45412', 'e06a1a', 'f08532', 'ffa358'),
        glove=ramp('2c2f33', '3e4247', '565b61', '72777e', '8e949b', 'aab0b6'),
        boot=ramp('1e2023', '2c2f33', '3e4247', '565b61', '72777e', '8e949b'),
        pack=ramp('6f7378', '9da2a8', 'c3c7cc', 'dfe2e5', 'eef0f2', 'fafbfc'),
    )
    s.lum_range = {}
    recolor(s, base_zones)
    s.ramps['base'] = s.ramps['glove']
    s.ramps['accent'] = ramp('1b5f9e', '2f7fc4', '58a6e6', 'a8d4ff')          # luces del panel
    s.ramps['dark'] = s.ramps['boot']
    s.ramps['white'] = s.ramps['helmet']
    s.mat('ring', 'base', 'bolts'); s.mat('hose', 'base', 'hose'); s.mat('panel', 'base', 'screen')
    s.mat('sole', 'dark'); s.mat('cap', 'white')
    s.remove('chestplate', 'right_arm', (25, 70))                                   # sin hombreras (lo pidió el usuario)
    s.remove('chestplate', 'left_arm', (35, 79))
    s.add('helmet', 'head', (-5, -1, -5), (10, 1, 10), 'ring', inflate=0.25)         # anillo del cuello
    s.add('chestplate', 'body', (-2, 3, -3.2), (4, 3, 1), 'panel')                   # panel de control del pecho
    s.add('chestplate', 'body', (-5.2, -0.5, 1), (1, 5, 1), 'hose')                  # manguera de aire al casco
    s.pair('chestplate', 'arm', (-3.5, 7, -2.5), (5, 1, 5), 'ring')                  # anillos de los guantes
    s.add('chestplate', 'body', (-3, -1, 2.2), (6, 1, 3), 'cap')                     # tapa de la mochila
    s.pair('boots', 'leg', (-2.5, 11, -3), (5, 1, 6), 'sole')                        # suela
    # parche en el hombro izquierdo (bandera de la agencia): azul con una estrella blanca
    for (x, y, c) in [(52, 21, '2f5fa8'), (53, 21, '2f5fa8'), (52, 22, '2f5fa8'), (53, 22, 'ffffff')]:
        s.dot(x, y, H(c))


# ------------------------------------------------------------------ traje térmico (Ignis)
def thermal(s):
    """Traje aluminizado (como los de bomberos de proximidad): tela plateada que refleja el calor, visor dorado,
    placas cerámicas en hombros, pecho y rodillas, radiadores en la mochila que brillan al rojo, tubos de
    refrigerante naranjas y botas de suela gruesa aislante."""
    s.ramps.update(
        helmet=ramp('5c5650', '8a8378', 'b3ab9e', 'd4ccbe', 'e8e2d6', 'f8f4ec'),
        visor=ramp('6a4404', 'a8700c', 'e0a820', 'ffe27a'),
        suit=ramp('5a5650', '858077', 'aba599', 'cbc5b8', 'e2ddd2', 'f5f2ea'),
        glove=ramp('3a2418', '55351f', '74492a', '8f5d36', 'a87244', 'c08a58'),     # cuero aislante
        boot=ramp('1a1716', '27221f', '36302b', '4a423b', '5e554c', '72685e'),
        pack=ramp('3c3a38', '55524e', '6e6a65', '88837d', 'a09b94', 'b8b2aa'),
    )
    s.lum_range = {'visor': (55, 165)}
    recolor(s, base_zones)
    s.ramps['base'] = ramp('2a2624', '3a3532', '4c4642', '615a55', '78706a', '908780')    # cerámica oscura
    s.ramps['accent'] = ramp('8a2a06', 'd8540c', 'ff8a1e', 'ffd070')                       # rojo vivo
    s.ramps['dark'] = s.ramps['boot']
    s.ramps['silver'] = s.ramps['suit']
    s.ramps['copper'] = ramp('5a2a10', '7c3c18', 'a35424', 'c46d34', 'de8a4c', 'f2aa70')
    s.mat('plate', 'base', 'line'); s.mat('knee', 'base', 'dot'); s.mat('fin', 'base', 'fin')
    s.mat('glowfin', 'base', 'glow'); s.mat('hose', 'copper', 'hose'); s.mat('brim', 'silver', 'line')
    s.mat('sole', 'dark'); s.mat('pad', 'silver', 'pad'); s.mat('vent', 'base', 'heater'); s.mat('bolts', 'base', 'bolts')
    # casco: visera parasol sobre el visor dorado y respiraderos a los lados
    s.add('helmet', 'head', (-4.5, -8.8, -5.6), (9, 1, 2), 'brim')
    s.pair('helmet', 'head', (-5.2, -6, -2), (1, 3, 3), 'plate')
    s.add('helmet', 'head', (-5, -1, -5), (10, 1, 10), 'bolts', inflate=0.25)        # collar
    # pecho: placa cerámica con la raya al rojo; tubos de refrigerante hacia la mochila
    s.add('chestplate', 'body', (-3, 1, -3.3), (6, 5, 1), 'plate')
    s.pair('chestplate', 'body', (-5.1, 1, -1), (1, 7, 1), 'hose')
    s.pair('chestplate', 'body', (-4, 0, 4.6), (1, 1, 1), 'hose')
    # mochila: radiador de aletas con el núcleo encendido
    s.add('chestplate', 'body', (-3, 1, 5), (6, 6, 1), 'glowfin', inflate=-0.1)
    for x in (-3.5, -1.5, 0.5, 2.5):
        s.add('chestplate', 'body', (x, 0.5, 5), (1, 7, 2), 'fin', key=('fin', x))
    # brazos: hombreras cerámicas de dos capas y guanteletes aislantes
    s.pair('chestplate', 'arm', (-3.5, -3, -2.5), (5, 2, 5), 'plate')
    s.pair('chestplate', 'arm', (-4, -1.4, -2.5), (5, 2, 5), 'plate', inflate=-0.15)
    s.pair('chestplate', 'arm', (-3.5, 6.5, -2.5), (5, 3, 5), 'pad', inflate=-0.1)
    # piernas: rodilleras y espinilleras cerámicas
    s.pair('leggings', 'leg', (-1.5, 4.5, -3), (3, 3, 1), 'knee')
    s.pair('boots', 'leg', (-2.5, 7, -2.6), (5, 3, 5), 'pad', inflate=-0.1)
    s.pair('boots', 'leg', (-2.5, 11, -3), (5, 1, 6), 'sole')                        # suela aislante gruesa
    s.pair('boots', 'leg', (-1.5, 8, -3.2), (3, 3, 1), 'plate', inflate=-0.15)       # espinilleras


SUITS = [('basic', 'Traje espacial básico', basic), ('thermal', 'Traje térmico (Ignis)', thermal)]


def main(out, designs=None):
    os.makedirs(out, exist_ok=True)
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 20)
    f = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 15)
    mp = os.path.join(V1, 'armor_models', 'glacial_armor.json')
    tp = os.path.join(V1, 'textures/models/armor', 'glacial_armor.png')
    for name, title, build in SUITS:
        s = AC.Suit(mp, tp)
        build(s)
        if designs:
            AC.write_bbmodel(os.path.join(V1, 'glacial_armor.bbmodel'), os.path.join(designs, name + '_armor.bbmodel'), s)
        s.tex.save(os.path.join(out, name + '_armor_texture.png'))
        json.dump(s.model, open(os.path.join(out, name + '_armor_model.json'), 'w'))
        kw = dict(scale=17, size=(440, 640))
        views = [PV.render(s.model, s.tex, math.radians(a), math.radians(-12), **kw) for a in (-30, 30, 150)]
        big = PV.render(s.model, s.tex, math.radians(-32), math.radians(-10), scale=44, size=(1100, 1600))
        close = big.crop((550 - 300, 432 - 470, 550 + 300, 432 + 170)).resize((440, 469), Image.NEAREST)
        sheet = Image.new('RGBA', (440 * 4, 640 + 70), (66, 72, 86, 255))
        d = ImageDraw.Draw(sheet)
        for i, v in enumerate(views):
            sheet.alpha_composite(v, (i * 440, 50))
        sheet.alpha_composite(close, (3 * 440, 130))
        d.text((16, 12), title, fill=(240, 240, 240, 255), font=F)
        for i, lab in enumerate(['Frente', 'Otro lado', 'Espalda', 'Casco de cerca']):
            d.text((i * 440 + 16, 680), lab, fill=(200, 200, 200, 255), font=f)
        sheet.save(os.path.join(out, 'propuesta_' + name + '_armor.png'))
        print(name, 'ok')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
