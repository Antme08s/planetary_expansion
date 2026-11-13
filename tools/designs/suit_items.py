"""Iconos (16×16) de la progresión de trajes y de los caracoles de Ignis (2026-11-12).

Uso: python3 -I tools/designs/suit_items.py <carpeta_salida>

- Piezas del traje espacial básico y del traje térmico: el casco se repinta a partir del casco Impact dibujado por el
  usuario (impact_helmet.png, mismos colores por zona); el resto usa las mismas formas que los otros trajes
  (ICON_SHAPES de generate.py) con la paleta de cada traje.
- Plantillas de mejora: placa de titanio con remaches, centro del color del planeta y el casco del traje grabado.
- Materiales de los planetas y objetos de los caracoles de magma.
"""
import ast
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from thalassa_items import autoshade, img, lamp, mix, poly, put, shade  # noqa: E402


def icon_shapes():
    src = open(os.path.join(HERE, '..', 'generate.py'), encoding='utf-8').read()
    for node in ast.parse(src).body:
        if isinstance(node, ast.Assign) and getattr(node.targets[0], 'id', '') == 'ICON_SHAPES':
            return ast.literal_eval(node.value)
    raise SystemExit('ICON_SHAPES no encontrado')


def from_rows(rows, colors):
    im = img()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in colors:
                put(im, [(x, y)], colors[ch])
    return im


# colores del casco Impact del usuario -> zona
IMPACT_HELMET = {(30, 30, 32): 'K', (53, 54, 55): 'D', (71, 75, 77): 'M', (215, 99, 35): 'O',
                 (154, 66, 18): 'Od', (235, 128, 70): 'Ol', (214, 214, 214): 'W', (122, 125, 128): 'G'}


def helmet(colors):
    src = Image.open(os.path.join(HERE, 'impact_helmet.png')).convert('RGBA')
    im = img()
    for y in range(16):
        for x in range(16):
            c = src.getpixel((x, y))
            if c[3]:
                put(im, [(x, y)], colors[IMPACT_HELMET[c[:3]]])
    return im


SUITS = {
    # K borde, D base, M panel, L detalle, O visor/cinturón; Od/Ol visor oscuro/claro; W/G respirador
    'basic': {'K': (72, 34, 10), 'D': (214, 96, 26), 'M': (240, 134, 52), 'L': (150, 156, 162), 'O': (54, 58, 64),
              'boots': {'K': (30, 32, 36), 'D': (86, 90, 96), 'M': (118, 124, 130), 'L': (214, 96, 26), 'O': (150, 156, 162)},
              'helmet': {'K': (70, 74, 80), 'D': (214, 218, 222), 'M': (240, 242, 244), 'O': (34, 44, 56),
                         'Od': (18, 22, 30), 'Ol': (96, 120, 142), 'W': (170, 175, 180), 'G': (100, 104, 110)}},
    'thermal': {'K': (52, 47, 43), 'D': (196, 191, 181), 'M': (226, 222, 214), 'L': (232, 110, 30), 'O': (214, 160, 30),
                'helmet': {'K': (52, 47, 43), 'D': (196, 191, 181), 'M': (226, 222, 214), 'O': (220, 164, 30),
                           'Od': (150, 96, 10), 'Ol': (255, 228, 128), 'W': (80, 72, 66), 'G': (120, 112, 104)}},
}


def suit_icons():
    shapes = icon_shapes()
    out = {}
    for suit, pal in SUITS.items():
        out[f'{suit}_helmet'] = helmet(pal['helmet'])
        for piece in ('chestplate', 'leggings', 'boots'):
            colors = {k: v for k, v in pal.items() if len(k) == 1}
            colors.update(pal.get(piece, {}))
            out[f'{suit}_{piece}'] = from_rows(shapes[piece], colors)
    # detalles propios: panel de luces del básico, radiador del térmico
    b = out['basic_chestplate']
    put(b, [(6, 6), (7, 6), (8, 6), (9, 6)], (120, 126, 132))
    put(b, [(7, 6)], (90, 170, 240)); put(b, [(8, 6)], (240, 80, 60))
    put(b, [(12, 3)], (47, 95, 168)); put(b, [(12, 4)], (255, 255, 255))       # parche
    t = out['thermal_chestplate']
    for x in range(4, 12):
        put(t, [(x, 8)], (70, 64, 60) if x % 2 else (255, 140, 40))             # placa cerámica con raya al rojo
        put(t, [(x, 9)], (60, 55, 51))
    tl = out['thermal_leggings']
    put(tl, [(5, 8), (6, 8), (9, 8), (10, 8)], (70, 64, 60))                    # rodilleras
    put(tl, [(5, 7), (10, 7)], (255, 140, 40))
    tb = out['thermal_boots']
    for x0 in (1, 9):
        put(tb, [(x0 + i, 13) for i in range(5)], (36, 32, 30))                 # suela gruesa
    return out


# ------------------------------------------------------------------ plantillas
TEMPLATES = {
    # traje -> (color del planeta, color del grabado, color del visor)
    'impact': ((150, 62, 34), (36, 36, 38), (215, 99, 35)),        # Marte
    'glacial': ((136, 186, 226), (214, 218, 224), (25, 70, 150)),  # mundo helado
    'hazard': ((196, 150, 62), (24, 26, 22), (60, 200, 60)),       # Venus
    'sea_diver': ((34, 96, 132), (214, 162, 78), (40, 70, 140)),   # Thalassa
    'thermal': ((70, 34, 22), (200, 195, 185), (230, 170, 40)),    # Ignis
}


def template(planet, engrave, visor, seed):
    im = img()
    rnd = random.Random(seed)
    frame = (150, 156, 164)
    poly(im, [(2, 1), (13, 1), (13, 14), (2, 14)], frame)                       # placa de titanio
    for y in range(3, 13):                                                      # centro del color del planeta
        for x in range(4, 12):
            put(im, [(x, y)], shade(planet, 1 + rnd.uniform(-0.08, 0.08)))
    autoshade(im, 1.25, 0.72)
    for x, y in ((3, 2), (12, 2), (3, 13), (12, 13)):                           # remaches
        put(im, [(x, y)], (220, 226, 232))
    for y in range(3, 13):                                                      # bisel del centro
        put(im, [(4, y)], shade(planet, 0.7)); put(im, [(11, y)], shade(planet, 1.25))
    for x in range(4, 12):
        put(im, [(x, 3)], shade(planet, 0.7)); put(im, [(x, 12)], shade(planet, 1.25))
    # casco grabado
    hel = [(6, 5), (7, 5), (8, 5), (9, 5), (5, 6), (10, 6), (5, 7), (10, 7), (5, 8), (10, 8), (5, 9), (6, 9), (9, 9), (10, 9),
           (6, 10), (9, 10)]
    put(im, hel, engrave)
    put(im, [(6, 6), (7, 6), (8, 6), (9, 6), (6, 8), (7, 8), (8, 8), (9, 8)], shade(engrave, 0.8))
    put(im, [(6, 7), (7, 7), (8, 7), (9, 7)], visor)
    put(im, [(6, 7)], mix(visor, (255, 255, 255), 0.5))
    put(im, [(7, 9), (8, 9)], shade(engrave, 0.6))
    return im


# ------------------------------------------------------------------ materiales de los planetas
def meteorite():
    """Fragmento de meteorito (Marte): roca negra de fusión, con hierro brillante y la corteza quemada."""
    im = img()
    poly(im, [(1, 9), (3, 5), (6, 4), (7, 2), (11, 2), (13, 5), (15, 6), (14, 10), (12, 13), (8, 14), (6, 12), (3, 13)], (54, 46, 44))
    rnd = random.Random(5)
    px = im.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3]:
                c = shade((54, 46, 44), 1 + rnd.uniform(-0.15, 0.12))
                if rnd.random() < 0.12:
                    c = (120, 64, 40)                                           # óxido
                px[x, y] = c + (255,)
    autoshade(im, 1.3, 0.7)
    put(im, [(6, 6), (7, 6), (7, 7), (10, 9), (11, 9), (5, 10), (9, 5)], (190, 196, 204))   # hierro
    put(im, [(6, 6), (10, 9)], (240, 244, 250))
    put(im, [(4, 5), (5, 4), (8, 3), (9, 3)], (150, 80, 46))                   # corteza quemada
    return im


def cryo_crystal():
    """Cristal criogénico (mundo helado): dos prismas de hielo cian con el núcleo blanco."""
    im = img()
    poly(im, [(3, 13), (5, 4), (8, 1), (10, 4), (8, 14)], (120, 200, 236))
    poly(im, [(8, 14), (10, 8), (12, 6), (14, 9), (12, 15)], (96, 176, 222))
    autoshade(im, 1.3, 0.72, 0.4)
    put(im, [(6, 5), (6, 6), (7, 4), (7, 7), (6, 9)], (230, 250, 255))
    put(im, [(7, 3)], (255, 255, 255))
    put(im, [(11, 9), (12, 8)], (220, 246, 255))
    for x, y in ((5, 11), (6, 12), (11, 13)):
        put(im, [(x, y)], (70, 140, 200))
    return im


def sulfur():
    """Azufre venusiano (Venus): grumos cristalinos amarillos con caras más claras."""
    im = img()
    for cx, cy, r in ((6, 9, 3.6), (10, 7, 3.2), (9, 11, 2.8), (5, 5, 2.2)):
        for y in range(16):
            for x in range(16):
                if max(abs(x - cx), abs(y - cy)) + 0.5 * min(abs(x - cx), abs(y - cy)) < r:
                    put(im, [(x, y)], (222, 196, 52) if (x + y) % 3 else (206, 178, 40))
    autoshade(im, 1.25, 0.7, 0.4)
    put(im, [(5, 7), (6, 7), (9, 5), (10, 5), (8, 10), (4, 4)], (255, 246, 150))
    put(im, [(7, 11), (11, 9), (12, 8)], (160, 130, 24))
    return im


# ------------------------------------------------------------------ caracoles de magma
def igneous_shell():
    """Fragmento de concha ígnea: trozo curvo de obsidiana morada con la espiral y grietas al rojo."""
    im = img()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 8.5)
            if 3.2 < d < 7.0 and not (x > 9 and y > 9):
                put(im, [(x, y)], (62, 46, 76) if int(d * 1.6) % 2 else (46, 32, 56))
    autoshade(im, 1.35, 0.7)
    put(im, [(4, 5), (5, 4), (11, 4), (12, 6), (3, 9), (4, 11), (12, 8)], (255, 130, 40))
    put(im, [(5, 4), (12, 6)], (255, 210, 110))
    put(im, [(6, 3), (7, 3), (8, 3)], (120, 100, 140))
    return im


def magma_core():
    """Núcleo de magma: esfera al rojo vivo dentro de una costra oscura agrietada."""
    im = img()
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 6.0, (y - 8.0) / 6.0)
            if d < 1:
                put(im, [(x, y)], mix((255, 214, 90), (200, 60, 14), min(1.0, d * 1.15)))
    autoshade(im, 1.15, 0.7, 0.45)
    crust = (58, 34, 24)
    for x, y in ((3, 5), (4, 4), (5, 3), (11, 3), (12, 4), (13, 7), (13, 10), (12, 12), (3, 11), (4, 12), (2, 8),
                 (9, 13), (6, 13), (11, 12)):
        put(im, [(x, y)], crust)
    put(im, [(6, 6), (7, 6), (6, 7)], (255, 248, 200))
    return im


def magma_slime():
    """Baba de magma: gota naranja que brilla, con burbujas."""
    im = img()
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 6.0, (y - 9.5) / 4.8) + 0.08 * math.sin(x * 1.5 + y)
            if d < 1 and y > 4:
                put(im, [(x, y)], shade((236, 110, 30), 0.85 + 0.4 * (1 - d)))
    autoshade(im, 1.2, 0.72, 0.45)
    put(im, [(5, 7), (6, 7), (4, 8)], (255, 230, 150))
    put(im, [(9, 10), (10, 12), (7, 12)], (255, 190, 90))
    put(im, [(7, 4), (8, 3), (8, 4)], (250, 150, 50))
    return im


# ------------------------------------------------------------------ kits de supervivencia (0.7.66)
def cooling_gel():
    """Gel refrigerante: frasco de vidrio con gel azul hielo y tapa blanca."""
    im = img()
    poly(im, [(5, 4), (10, 4), (10, 6), (12, 8), (12, 14), (3, 14), (3, 8), (5, 6)], (200, 226, 240))   # vidrio
    for y in range(8, 14):
        for x in range(4, 12):
            put(im, [(x, y)], mix((90, 200, 250), (40, 130, 210), (y - 8) / 6))                      # gel
    autoshade(im, 1.25, 0.75, 0.45)
    poly(im, [(5, 2), (10, 2), (10, 4), (5, 4)], (236, 240, 244))                                      # tapa
    autoshade(im, 1.2, 0.8)
    put(im, [(5, 9), (5, 10), (6, 9)], (220, 248, 255))
    put(im, [(9, 11), (8, 12)], (180, 236, 255))
    return im


def chemical_heater():
    """Calentador químico: bolsita naranja con muesca para abrir y el símbolo de calor."""
    im = img()
    poly(im, [(3, 3), (12, 3), (13, 4), (13, 13), (12, 14), (3, 14), (2, 13), (2, 4)], (228, 112, 36))
    autoshade(im, 1.25, 0.7, 0.45)
    for x in range(3, 13):
        put(im, [(x, 4)], (180, 80, 24))                                                               # soldadura
    put(im, [(12, 4)], (0, 0, 0, 0)); put(im, [(13, 4)], (0, 0, 0, 0))                                 # muesca
    for x, y in ((5, 11), (5, 10), (6, 9), (6, 8), (5, 7), (8, 11), (8, 10), (9, 9), (9, 8), (8, 7), (11, 11), (11, 10)):
        put(im, [(x, y)], (255, 230, 150))                                                             # ondas de calor
    put(im, [(4, 6), (4, 7)], (255, 170, 90))
    return im


def dust_filter():
    """Filtro de polvo: cartucho redondo de mascarilla, carcasa gris y malla color arena."""
    im = img()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 6.6:
                put(im, [(x, y)], (120, 126, 132) if d > 4.8 else ((206, 182, 140) if (x + y) % 2 else (186, 160, 118)))
    autoshade(im, 1.25, 0.72, 0.45)
    for x, y in ((7, 1), (8, 1), (7, 14), (8, 14), (1, 7), (1, 8), (14, 7), (14, 8)):
        put(im, [(x, y)], (84, 88, 94))                                                                # rosca
    put(im, [(5, 4), (4, 5)], (180, 186, 192))
    put(im, [(7, 7), (8, 8)], (150, 126, 90))
    return im


def effect_icon(item):
    """Icono de efecto (18×18): el del objeto con 1 píxel de margen."""
    out = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    out.alpha_composite(item, (1, 1))
    return out


NAMES = {
    'cooling_gel': 'Gel refrigerante (Venus)', 'chemical_heater': 'Calentador químico (frío)',
    'dust_filter': 'Filtro de polvo (Marte)',
    'basic_helmet': 'Casco espacial básico', 'basic_chestplate': 'Pechera espacial básica',
    'basic_leggings': 'Pantalones espaciales básicos', 'basic_boots': 'Botas espaciales básicas',
    'thermal_helmet': 'Casco térmico', 'thermal_chestplate': 'Pechera térmica',
    'thermal_leggings': 'Pantalones térmicos', 'thermal_boots': 'Botas térmicas',
    'impact_upgrade_template': 'Plantilla de impacto (Marte)', 'glacial_upgrade_template': 'Plantilla glacial (mundo helado)',
    'hazard_upgrade_template': 'Plantilla Hazard (Venus)', 'sea_diver_upgrade_template': 'Plantilla Sea Diver (Thalassa)',
    'thermal_upgrade_template': 'Plantilla térmica (Ignis)',
    'meteorite_fragment': 'Fragmento de meteorito (Marte)', 'cryo_crystal': 'Cristal criogénico (mundo helado)',
    'venusian_sulfur': 'Azufre venusiano (Venus)',
    'igneous_shell': 'Fragmento de concha ígnea', 'magma_core': 'Núcleo de magma', 'magma_slime': 'Baba de magma',
    'magma_lamp': 'Lámpara de magma (bloque)',
}


def build(out):
    os.makedirs(out, exist_ok=True)
    icons = suit_icons()
    for i, (suit, (planet, eng, visor)) in enumerate(TEMPLATES.items()):
        icons[f'{suit}_upgrade_template'] = template(planet, eng, visor, i + 1)
    icons.update(meteorite_fragment=meteorite(), cryo_crystal=cryo_crystal(), venusian_sulfur=sulfur(),
                 igneous_shell=igneous_shell(), magma_core=magma_core(), magma_slime=magma_slime(),
                 magma_lamp=lamp((255, 130, 30), (52, 36, 40), 9, kind="magma"),
                 cooling_gel=cooling_gel(), chemical_heater=chemical_heater(), dust_filter=dust_filter())
    for effect, item in (("cooled", "cooling_gel"), ("warmed", "chemical_heater"), ("filtered", "dust_filter")):
        effect_icon(icons[item]).save(os.path.join(out, "effect_" + effect + ".png"))
    for name, im in icons.items():
        im.save(os.path.join(out, name + '.png'))
    # hoja con nombres
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 13)
    FB = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 16)
    groups = [('Traje espacial básico', [k for k in icons if k.startswith('basic_')]),
              ('Traje térmico', [k for k in icons if k.startswith('thermal_') and 'template' not in k]),
              ('Plantillas de mejora', [k for k in icons if k.endswith('_template')]),
              ('Materiales de los planetas', ['meteorite_fragment', 'cryo_crystal', 'venusian_sulfur']),
              ('Caracoles de magma (Ignis)', ['igneous_shell', 'magma_core', 'magma_slime', 'magma_lamp']),
              ('Kits de supervivencia', ['cooling_gel', 'chemical_heater', 'dust_filter'])]
    cell, cols = 190, 5
    rows = sum(1 + (len(g) + cols - 1) // cols for _, g in groups)
    sheet = Image.new('RGBA', (cell * cols, rows * 30 + sum((len(g) + cols - 1) // cols for _, g in groups) * 140 + 10),
                      (52, 56, 66, 255))
    d = ImageDraw.Draw(sheet)
    y = 8
    for title, keys in groups:
        d.text((12, y), title, fill=(240, 240, 240, 255), font=FB)
        y += 30
        for i, k in enumerate(keys):
            x0 = (i % cols) * cell
            if i and i % cols == 0:
                y += 140
            sheet.alpha_composite(Image.new('RGBA', (100, 100), (70, 75, 88, 255)), (x0 + 45, y))
            sheet.alpha_composite(icons[k].resize((96, 96), Image.NEAREST), (x0 + 47, y + 2))
            txt = NAMES[k]
            w = d.textlength(txt, font=F)
            if w > cell - 8:
                a, b = txt.rsplit(' ', 1) if '(' not in txt else txt.split(' (', 1)
                b = b if '(' not in txt else '(' + b
                d.text((x0 + (cell - d.textlength(a, font=F)) / 2, y + 104), a, fill=(220, 220, 225, 255), font=F)
                d.text((x0 + (cell - d.textlength(b, font=F)) / 2, y + 119), b, fill=(220, 220, 225, 255), font=F)
            else:
                d.text((x0 + (cell - w) / 2, y + 108), txt, fill=(220, 220, 225, 255), font=F)
        y += 140
    sheet = sheet.crop((0, 0, sheet.width, y + 4))
    sheet.save(os.path.join(out, 'iconos_trajes_y_caracoles.png'))
    print('ok', len(icons))


if __name__ == '__main__':
    build(sys.argv[1])
