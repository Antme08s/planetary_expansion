"""Armaduras con más detalle y más piezas 3D (propuestas aprobadas por el usuario; en el mod desde 0.7.35).

Uso: python3 armor_concepts.py tools/designs/armor_v1 <carpeta de salida> [tools/designs]
Lee la versión 1 de cada traje (modelo y textura generados, en armor_v1/) y dibuja antes/después. Con el tercer
argumento escribe además los .bbmodel nuevos (armor_v1/<traje>.bbmodel + cajas nuevas + textura nueva), que son los
que usa generate.py y los que se pueden seguir editando en Blockbench.
Parte del modelo y la textura actuales de cada traje, añade cajas nuevas (con UV de caja en zonas libres de la misma
textura de 128x128, pintadas por código) y dibuja antes/después con tools/preview_armor.py.
"""
import copy, json, math, os, random, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
import preview_armor as PV

H = lambda s: tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
ORANGE = [H(c) for c in ('7a2e08', 'b5480f', 'e0661a', 'ff9a3c', 'ffd08a')]
DARK = [H(c) for c in ('0b0b0c', '141415', '1d1e20', '292b2d')]


def sample(tex, rects, n=6, keep=None):
    """Rampa de n tonos (oscuro -> claro) con los colores que ya usa la textura del traje en esas zonas."""
    cols = []
    px = tex.load()
    for (x0, y0, w, h) in rects:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c = px[x, y]
                if c[3] > 0 and (keep is None or keep(c)): cols.append(c)
    cols.sort(key=lambda c: 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2])
    return [cols[min(len(cols) - 1, int(q * (len(cols) - 1)))][:3] + (255,) for q in np.linspace(0.03, 0.97, n)]


def panel(ramp, w, h, rnd, face):
    """Mismo estilo que las texturas del traje: panel con borde oscuro, filo claro arriba-izquierda y centro medio."""
    n = len(ramp) - 1
    im = Image.new('RGBA', (w, h))
    px = im.load()
    for y in range(h):
        for x in range(w):
            if w >= 4 and h >= 4:
                border = x in (0, w - 1) or y in (0, h - 1)
                if border: i = 0 if (x in (0, w - 1) and y in (0, h - 1)) else 1
                elif y == 1 or x == 1: i = n
                elif y == h - 2 or x == w - 2: i = n - 3
                else: i = n - 1 if rnd.random() < 0.7 else n - 2
            elif w >= 2 and h >= 2:
                i = n - 1 if (x == 0 or y == 0) else 1 if (x == w - 1 and y == h - 1) else n - 3
                if w == 2 and h > 2: i = (n - 1 if x == 0 else n - 3) if 0 < y < h - 1 else (n if y == 0 and x == 0 else 1)
                if h == 2 and w > 2: i = (n - 1 if y == 0 else n - 3) if 0 < x < w - 1 else (n if y == 0 and x == 0 else 1)
            else:
                k = (x if h == 1 else y) / max(1, max(w, h) - 1)
                i = round(n - 1 - k * (n - 2))
            if face == 'bottom': i = max(0, i - 2)
            px[x, y] = ramp[max(0, min(n, i))]
    return im


class Suit:
    def __init__(self, model_path, tex_path):
        self.model = json.load(open(model_path))
        self.tex = Image.open(tex_path).convert('RGBA')
        self.used = np.asarray(self.tex)[..., 3] > 0
        self.uv = {}
        self.n = 0
        self.ramps = {}
        self.mats = {}
        self.added = []      # (pieza, parte, material, caja) de cada caja nueva
        self.removed = []    # UV de las cajas viejas quitadas

    def mat(self, name, ramp, extra=None, accent=None):
        self.mats[name] = (ramp, extra, accent)

    # ---------------------------------------------------------------- pintura de las piezas nuevas
    def paint(self, mat, face, w, h, seed):
        rnd = random.Random(seed)
        ramp_name, extra, accent = self.mats[mat]
        ramp = self.ramps[ramp_name]
        acc = self.ramps.get(accent or 'accent')
        im = panel(ramp, w, h, rnd, face)
        px = im.load()
        side = face not in ('top', 'bottom')
        n = len(ramp) - 1
        cx, cy = (w - 1) / 2, (h - 1) / 2
        A = lambda k: acc[max(0, min(len(acc) - 1, k))]
        if extra == 'line' and side and h >= 3:                    # raya del color del visor, dentro del borde
            y = h - 2 if h >= 4 else h - 1
            for x in range(1 if w >= 4 else 0, w - 1 if w >= 4 else w): px[x, y] = A(2 if x % 3 else 3)
        elif extra == 'dot' and face == 'front':
            px[int(cx), int(cy)] = A(3); px[int(cx + 0.5), int(cy)] = A(2)
        elif extra == 'thruster':
            if face == 'bottom':
                for y in range(h):
                    for x in range(w):
                        r = max(abs(x - cx), abs(y - cy))
                        px[x, y] = ramp[0] if r > max(cx, cy) - 0.3 and min(w, h) > 2 else ORANGE[4 if r < 0.6 else 3 if r < 1.3 else 2]
            elif side and h >= 4:
                for x in range(1, w - 1): px[x, h - 2] = ORANGE[1]; px[x, h - 3] = ramp[0]
        elif extra == 'piston' and side:
            for y in range(h):
                for x in range(w):
                    if y < h // 2: px[x, y] = ramp[n if x == 0 else n - 1]       # vástago claro
        elif extra == 'pad' and side:                               # acolchado: costuras verticales
            for y in range(h):
                for x in range(w):
                    if x % 3 == 2 and 0 < y < h - 1: px[x, y] = ramp[n - 3]
                    elif y == 0: px[x, y] = ramp[n]
                    elif y == h - 1: px[x, y] = ramp[1]
            if h >= 2:
                for x in range(0, w, 3): px[x, h - 1] = A(2)       # pespunte del color del visor
        elif extra == 'pad':
            for y in range(h):
                for x in range(w):
                    if (x % 3 == 2) and 0 < y < h - 1 and 0 < x < w - 1: px[x, y] = ramp[n - 3]
        elif extra == 'stripe':
            for y in range(h):
                for x in range(w):
                    px[x, y] = A(3 if y == 0 else 2) if (x + y) % 4 < 2 else DARK[1 if y else 2]
        elif extra == 'canister':
            if face == 'front':                                     # boca del filtro: aro claro y rejilla oscura
                for y in range(h):
                    for x in range(w):
                        edge = x in (0, w - 1) or y in (0, h - 1)
                        corner = x in (0, w - 1) and y in (0, h - 1)
                        px[x, y] = ramp[1] if corner else ramp[n] if edge else DARK[0]
            elif side and h >= 3:
                for x in range(w):
                    px[x, 1] = A(2 if x % 2 else 3)                 # banda de aviso
                    for y in range(2, h - 1):
                        if y % 2 == 0: px[x, y] = ramp[n - 3]       # anillos
        elif extra == 'hose':
            for y in range(h):
                for x in range(w):
                    px[x, y] = ramp[n - 1 if y % 2 == 0 else 1]
        elif extra == 'tank' and side:
            for x in range(w):
                for y in (1, h - 2): px[x, y] = A(2 if x else 3)
            for y in range(2, h - 2): px[0, y] = ramp[n]
        elif extra == 'screen' and face in ('left', 'right', 'front'):
            for y in range(1, h - 1):
                for x in range(1, w - 1): px[x, y] = A(3 if (x, y) == (1, 1) else 2 if y <= cy else 1)
        elif extra == 'glow':
            for y in range(h):
                for x in range(w): px[x, y] = A(3 if max(abs(x - cx), abs(y - cy)) < 0.6 else 2)
        elif extra == 'frame' and face == 'front':                  # placa con marco del color de aviso y remaches
            for y in range(h):
                for x in range(w):
                    if x in (0, w - 1) or y in (0, h - 1): px[x, y] = A(2 if (x + y) % 3 else 3)
            for (x, y) in [(1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)]: px[x, y] = ramp[n]
        elif extra == 'heater' and face == 'front':
            for y in range(1, h - 1):
                for x in range(1, w - 1): px[x, y] = ORANGE[3 if y % 2 else 1] if x not in (1, w - 2) or True else px[x, y]
            px[1, 1] = ORANGE[4]
        elif extra == 'tip':
            if face == 'top':
                for y in range(h):
                    for x in range(w): px[x, y] = ORANGE[3]
            elif side: px[0, 0] = ORANGE[3]; px[w - 1, 0] = ORANGE[2]
        elif extra == 'bolts' and (face == 'top' or (side and h >= 3)):
            pts = [(1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)]
            if w >= 8: pts += [(int(cx), 1), (int(cx), h - 2)]
            if h >= 8: pts += [(1, int(cy)), (w - 2, int(cy))]
            for (x, y) in pts:
                if 0 <= x < w and 0 <= y < h: px[x, y] = ramp[n]
        elif extra == 'bar' and side:                               # barra del marco: remache en cada extremo
            if w >= 3: px[0, 0] = ramp[n]; px[w - 1, 0] = ramp[n]
            elif h >= 3: px[0, 0] = ramp[n]; px[0, h - 1] = ramp[n - 1]
        elif extra == 'window' and face in ('front', 'left', 'right') and w >= 4 and h >= 4:
            for y in range(1, h - 1):                               # ventanilla con el mismo cristal que el visor
                for x in range(1, w - 1): px[x, y] = A(3 if (x == 1 and y == 1) else 2 if y <= cy else 1)
            for (x, y) in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]: px[x, y] = ramp[n]
        elif extra == 'lamp' and face == 'front':
            for y in range(h):
                for x in range(w):
                    if w >= 3 and (x in (0, w - 1) or y in (0, h - 1)) and min(w, h) >= 3: continue
                    px[x, y] = H('fff6c0' if (x + y) == 0 else 'ffe27a')
        elif extra == 'fin':
            for y in range(h):
                for x in range(w):
                    if (x % 2 == 1) and 0 < y < h - 1: px[x, y] = ramp[n - 3]     # varillas de la aleta
            if side:
                for x in range(w): px[x, h - 1] = A(n)                               # filo de latón
        elif extra == 'gauge' and face == 'front':
            for y in range(1, h - 1):
                for x in range(1, w - 1): px[x, y] = H('f4f0e0')
            px[int(cx), int(cy)] = H('c02020')
        return im

    # ---------------------------------------------------------------- sitio en la textura y cajas
    def alloc(self, w, h, d, key):
        if key in self.uv: return self.uv[key]
        W, Hh = 2 * w + 2 * d, d + h
        th, tw = self.used.shape
        for v in range(0, th - Hh + 1):
            for u in range(0, tw - W + 1):
                if not self.used[v:v + Hh, u:u + W].any():
                    self.used[v:v + Hh + 1, u:u + W + 1] = True
                    self.uv[key] = (u, v)
                    return (u, v)
        raise SystemExit('sin sitio en la textura para ' + str(key))

    def add(self, piece, part, mn, size, mat, inflate=0.0, mirror=False, rot=None, offset=None, key=None):
        w, h, d = [round(s) for s in size]
        key = key or (mat, w, h, d, self.n)
        fresh = key not in self.uv
        u, v = self.alloc(w, h, d, key)
        if fresh:
            self.n += 1
            rects = {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
                     'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}
            for i, (face, (x, y, fw, fh)) in enumerate(rects.items()):
                self.tex.paste(self.paint(mat, face, fw, fh, self.n * 31 + i), (x, y))
        box = {'uv': [u, v], 'size': list(size), 'inflate': inflate, 'mirror': mirror, 'min': list(mn)}
        self.added.append((piece, part, mat, box))
        node = {'offset': list(offset or (0.0, 0.0, 0.0)), 'rotation': list(rot or (0.0, 0.0, 0.0)), 'boxes': [box]}
        self.model['pieces'][piece].setdefault(part, []).append(node)

    def pair(self, piece, part, mn, size, mat, inflate=0.0):
        """Lo mismo en el lado derecho (como se da) y en el izquierdo (espejo en x). part: 'arm', 'leg' o una parte central."""
        key = (mat, tuple(size), tuple(mn), part)
        mirrored = [-(mn[0] + size[0]), mn[1], mn[2]]
        if part in ('arm', 'leg'):
            self.add(piece, 'right_' + part, mn, size, mat, inflate, key=key)
            self.add(piece, 'left_' + part, mirrored, size, mat, inflate, mirror=True, key=key)
        else:
            self.add(piece, part, mn, size, mat, inflate, key=key)
            self.add(piece, part, mirrored, size, mat, inflate, mirror=True, key=key)

    def remove(self, piece, part, uv):
        """Quita del modelo las cajas existentes con ese UV (para sustituir una pieza vieja)."""
        for node in self.model['pieces'][piece].get(part, []):
            node['boxes'] = [b for b in node['boxes'] if tuple(b['uv']) != tuple(uv)]
        self.removed.append(tuple(uv))

    def line(self, rect, y, color):
        """Raya de un píxel en la textura existente (x0, x1 incluidos)."""
        px = self.tex.load()
        for x in range(rect[0], rect[1] + 1):
            if px[x, y][3] > 0: px[x, y] = color

    def dot(self, x, y, color):
        self.tex.putpixel((x, y), color)


HEAD_TOP, CHEST = (8, 16, 8, 8), (36, 4, 8, 12)
VISOR = (6, 26, 12, 3)
# caras de la textura actual: brazo derecho x 16..31 (filas 52..63), brazo izquierdo x 48..63 (filas 20..31),
# pierna derecha x 48..63 e izquierda x 32..47 (filas 52..59)
LIMBS = (((48, 63), 54), ((32, 47), 54), ((16, 31), 56), ((48, 63), 24))


def common(s, visor_keep=None):
    s.ramps['base'] = sample(s.tex, [HEAD_TOP, (24, 24, 8, 8)])
    s.ramps['accent'] = sample(s.tex, [VISOR], 4, keep=visor_keep)
    s.ramps['dark'] = [tuple(int(v * 0.5) for v in c[:3]) + (255,) for c in s.ramps['base']]


def impact(s):
    common(s, lambda c: c[0] > 150 and c[2] < 120)
    s.mat('plate', 'base'); s.mat('trim', 'base', 'line'); s.mat('knee', 'base', 'dot')
    s.mat('thruster', 'base', 'thruster'); s.mat('piston', 'base', 'piston'); s.mat('sole', 'dark')
    s.add('helmet', 'head', (-4, -3, 4), (8, 3, 1), 'plate')                        # protector de nuca
    s.pair('chestplate', 'arm', (-3.5, -4, -2.5), (4, 2, 5), 'plate')               # hombreras de dos capas
    s.pair('chestplate', 'arm', (-4.5, -2, -2.5), (5, 3, 5), 'trim')
    s.pair('chestplate', 'arm', (-3.5, 5, -2.5), (5, 4, 5), 'trim', inflate=-0.2)   # brazales
    s.add('chestplate', 'body', (-2, 5.5, -3), (4, 2, 1), 'plate')                  # placas del abdomen
    s.add('chestplate', 'body', (-3, 8, -3), (6, 2, 1), 'plate')
    s.pair('chestplate', 'body', (-6, 1, 2), (2, 5, 3), 'thruster')                 # propulsores laterales del jetpack
    s.pair('leggings', 'leg', (-1.5, 4.5, -3), (3, 3, 1), 'knee')                   # rodilleras
    s.pair('leggings', 'leg', (-3, 1, -1.5), (1, 4, 3), 'plate')                    # placas del muslo
    s.pair('leggings', 'leg', (-1, 3, 2), (2, 4, 1), 'thruster')                    # toberas de las piernas
    s.pair('boots', 'leg', (-2.5, 11, -3), (5, 1, 6), 'sole')                       # suela gruesa
    s.pair('boots', 'leg', (-2, 9, -3), (4, 2, 1), 'plate')                         # puntera
    s.pair('boots', 'leg', (-1, 7.5, 2), (2, 4, 1), 'piston')                       # amortiguador del talón
    for rect, y in LIMBS: s.line(rect, y, s.ramps['accent'][2])


def hazard(s):
    common(s, lambda c: c[1] > 120)
    s.ramps['warn'] = sample(s.tex, [(0, 0, 128, 128)], 4, keep=lambda c: c[0] > 150 and c[1] > 120 and c[2] < 90)
    s.mat('plate', 'base'); s.mat('trim', 'base', 'line', 'warn'); s.mat('stripe', 'base', 'stripe', 'warn')
    s.mat('canister', 'base', 'canister', 'warn'); s.mat('hose', 'base', 'hose'); s.mat('tank', 'base', 'tank', 'warn')
    s.mat('dosimeter', 'base', 'screen'); s.mat('beacon', 'base', 'glow'); s.mat('lead', 'base', 'frame', 'warn')
    s.mat('sole', 'dark')
    s.pair('helmet', 'head', (-6.6, -4.2, -4.8), (3, 3, 3), 'canister', inflate=-0.3)   # filtros
    s.pair('helmet', 'head', (-5, -3.5, -5.2), (1, 2, 1), 'plate')                  # tubo del filtro a la máscara
    s.add('helmet', 'head', (-1, -9, -1), (2, 1, 2), 'beacon')                      # baliza
    s.pair('chestplate', 'body', (-3, -3, 4.2), (1, 4, 1), 'hose')                  # mangueras
    s.pair('chestplate', 'body', (-6, 0, 2), (2, 8, 3), 'tank')                     # tanques depuradores
    s.add('chestplate', 'body', (-3, 5.5, -3), (6, 5, 1), 'lead')                   # placa de plomo
    s.pair('chestplate', 'arm', (-3.5, -3, -2.5), (5, 3, 5), 'trim')                # hombreras
    s.pair('chestplate', 'arm', (-3.5, 7.5, -2.5), (5, 2, 5), 'stripe')             # puños de aviso
    s.add('chestplate', 'left_arm', (3, 3, -1.5), (1, 3, 3), 'dosimeter')           # dosímetro
    s.pair('leggings', 'leg', (-1.5, 4.5, -3), (3, 3, 1), 'plate')                  # rodilleras
    s.pair('leggings', 'leg', (-3, 1, -1.5), (1, 4, 3), 'plate')                    # bolsillos
    s.pair('boots', 'leg', (-2.5, 7.5, -2.5), (5, 2, 5), 'stripe')                  # tobilleras de aviso
    s.pair('boots', 'leg', (-2.5, 11, -3), (5, 1, 6), 'sole')                       # suela
    for rect, y in LIMBS: s.line(rect, y, s.ramps['warn'][2])


def glacial(s):
    common(s, lambda c: c[2] > c[0] + 40)
    s.mat('plate', 'base'); s.mat('trim', 'base', 'line'); s.mat('pad', 'base', 'pad'); s.mat('knee', 'base', 'dot')
    s.mat('heater', 'base', 'heater'); s.mat('tank', 'base', 'tank'); s.mat('antenna', 'base', 'tip')
    s.mat('sole', 'dark'); s.mat('spike', 'dark')
    s.pair('helmet', 'head', (-6, -7, -2), (2, 5, 4), 'trim', inflate=-0.3)         # orejeras aisladas
    s.add('helmet', 'head', (3, -11, 2), (1, 3, 1), 'antenna')                      # antena
    s.add('chestplate', 'body', (-5, -0.6, -3), (10, 2, 6), 'pad')                  # cuello acolchado (envuelve los hombros)
    s.add('chestplate', 'body', (-2, 5.5, -3), (4, 4, 1), 'heater')                 # calefactor
    s.pair('chestplate', 'body', (-6, 1, 2), (2, 6, 3), 'tank')                     # tanques térmicos
    s.pair('chestplate', 'arm', (-3.5, -3, -2.5), (5, 3, 5), 'trim')                # hombreras
    s.pair('chestplate', 'arm', (-3.5, 7, -2.5), (5, 3, 5), 'pad')                  # puños acolchados
    s.pair('leggings', 'leg', (-1.5, 4.5, -3), (3, 3, 1), 'knee')                   # rodilleras
    s.pair('leggings', 'leg', (-3, 1, -1.5), (1, 4, 3), 'trim')
    s.pair('boots', 'leg', (-2.5, 7, -2.5), (5, 3, 5), 'pad')                       # caña acolchada
    s.pair('boots', 'leg', (-3, 11.5, -4), (6, 1, 7), 'sole', inflate=-0.2)         # raqueta de nieve
    s.pair('boots', 'leg', (-1.5, 10, -3.6), (1, 2, 1), 'spike', inflate=-0.2)      # crampones
    s.pair('boots', 'leg', (0.5, 10, -3.6), (1, 2, 1), 'spike', inflate=-0.2)
    for rect, y in LIMBS: s.line(rect, y, s.ramps['accent'][1])
    # tubos del calefactor pintados en el pecho, hacia los costados (frente del torso: x 36..43, filas 4..15)
    b = s.ramps['base']
    for x in (36, 37, 42, 43):
        s.dot(x, 4 + 7, b[1]); s.dot(x, 4 + 6, ORANGE[2] if x in (37, 42) else b[5])
    for x in range(38, 42): s.dot(x, 4 + 10, b[1])                                  # sombra bajo el calefactor


def sea_diver(s):
    s.ramps['base'] = sample(s.tex, [HEAD_TOP, (24, 24, 8, 8)], 7)                  # latón del casco
    s.ramps['navy'] = sample(s.tex, [(36, 5, 8, 8)], 6)                             # azul del traje
    glass = sample(s.tex, [VISOR], 4, keep=lambda c: c[2] > c[0] + 30)
    s.ramps['accent'] = [glass[0], glass[2], tuple(min(255, int(v * 1.5) + 20) for v in glass[3][:3]) + (255,),
                         tuple(min(255, int(v * 2.2) + 60) for v in glass[3][:3]) + (255,)]
    s.ramps['dark'] = [tuple(int(v * 0.5) for v in c[:3]) + (255,) for c in s.ramps['base']]
    s.mat('brass', 'base'); s.mat('bolts', 'base', 'bolts'); s.mat('bar', 'base', 'bar'); s.mat('window', 'base', 'window')
    s.mat('lamp', 'base', 'lamp'); s.mat('tank', 'base', 'tank', 'navy'); s.mat('fin', 'navy', 'fin', 'base')
    s.mat('gauge', 'base', 'gauge'); s.mat('weight', 'dark', 'bolts'); s.mat('prop', 'dark')
    # casco: marco atornillado alrededor del visor que ya tiene (el cristal es el del propio casco) y rejilla
    s.add('helmet', 'head', (-5, -7, -5), (10, 1, 1), 'bar')                        # marco: arriba
    s.add('helmet', 'head', (-5, -3, -5), (10, 1, 1), 'bar')                        # marco: abajo
    s.pair('helmet', 'head', (-5, -6, -5), (1, 3, 3), 'bar')                        # marco: lados (cubren la esquina del visor)
    s.pair('helmet', 'head', (-2, -6, -4.7), (1, 3, 1), 'brass', inflate=-0.25)     # rejilla
    s.remove('helmet', 'head', (62, 74))                                            # las orejeras viejas pasan a ser ventanillas
    s.pair('helmet', 'head', (-5, -7, -2), (1, 4, 4), 'window')                     # ventanillas laterales
    s.add('helmet', 'head', (-1, -10, -3), (2, 2, 3), 'lamp')                       # foco
    s.add('helmet', 'head', (-5, -1, -5), (10, 1, 10), 'bolts')                     # collar atornillado
    s.pair('chestplate', 'body', (-6, 0, 2), (2, 9, 3), 'tank')                     # tanques de aire
    s.add('chestplate', 'body', (-0.5, 0, 5), (1, 5, 2), 'fin')                     # aleta dorsal
    s.add('chestplate', 'body', (-1, 7, 5), (2, 2, 1), 'brass')                     # hélice
    s.add('chestplate', 'body', (-3, 7.5, 5.6), (6, 1, 1), 'prop', inflate=-0.25)
    s.add('chestplate', 'body', (-0.5, 5, 5.6), (1, 6, 1), 'prop', inflate=-0.25)
    s.pair('chestplate', 'body', (-3.5, 9.5, -3), (2, 2, 1), 'weight')              # lastres del cinturón
    s.pair('chestplate', 'arm', (-5, 3, -0.5), (2, 6, 1), 'fin', inflate=-0.2)      # aletas de los antebrazos
    s.add('chestplate', 'left_arm', (-0.5, 5.5, -3), (3, 3, 1), 'gauge')            # profundímetro
    s.pair('leggings', 'leg', (-1.5, 4.5, -3), (3, 3, 1), 'bolts')                  # rodilleras
    s.pair('leggings', 'leg', (-4, 2, 0), (2, 5, 1), 'fin', inflate=-0.2)           # aletas de las piernas
    s.pair('boots', 'leg', (-2, 11, -9), (4, 1, 7), 'fin')                          # aletas de buceo
    # cristal del visor: reflejo arriba a la izquierda, como el de las ventanillas
    for x, y in ((9, 26), (10, 26), (9, 27)): s.dot(x, y, s.ramps['accent'][3] if (x, y) == (9, 26) else s.ramps['accent'][2])


PIVOTS = {'head': (0.0, 0.0, 0.0), 'body': (0.0, 0.0, 0.0), 'right_arm': (-5.0, 2.0, 0.0), 'left_arm': (5.0, 2.0, 0.0),
          'right_leg': (-1.9, 12.0, 0.0), 'left_leg': (1.9, 12.0, 0.0)}
BB_GROUP = {('helmet', 'head'): 'armorHead', ('chestplate', 'body'): 'armorBody',
            ('chestplate', 'right_arm'): 'armorRightArm', ('chestplate', 'left_arm'): 'armorLeftArm',
            ('leggings', 'right_leg'): 'armorRightLeg', ('leggings', 'left_leg'): 'armorLeftLeg',
            ('boots', 'right_leg'): 'armorRightBoot', ('boots', 'left_leg'): 'armorLeftBoot'}
BB_ORIGIN = {'head': [0, 24, 0], 'body': [0, 24, 0], 'right_arm': [5, 22, 0], 'left_arm': [-5, 22, 0],
             'right_leg': [1.9, 12, 0], 'left_leg': [-1.9, 12, 0]}


def write_bbmodel(src, dst, suit):
    """Escribe el diseño de Blockbench con las cajas nuevas y la textura nueva (inverso de convert_bbmodel de generate.py)."""
    import base64, io, uuid
    bb = json.load(open(src, encoding='utf-8'))
    groups = {g['name']: g for g in bb['groups']}

    def find(node, gid):
        if isinstance(node, str): return None
        if node['uuid'] == gid: return node
        for c in node.get('children', []):
            r = find(c, gid)
            if r: return r
        return None

    # cajas quitadas (por su UV)
    gone = {e['uuid'] for e in bb['elements'] if e.get('export', True) and tuple(e.get('uv_offset', (-1, -1))) in suit.removed}
    bb['elements'] = [e for e in bb['elements'] if e['uuid'] not in gone]

    def prune(node):
        if isinstance(node, str): return
        node['children'] = [c for c in node.get('children', []) if not (isinstance(c, str) and c in gone)]
        for c in node['children']: prune(c)
    for root in bb['outliner']: prune(root)

    count = {}
    for piece, part, mat, box in suit.added:
        pv = PIVOTS[part]
        cx, cy, cz = [pv[i] + box['min'][i] for i in range(3)]          # esquina mínima en coordenadas de Minecraft
        w, h, d = box['size']
        to = [-cx, 24.0 - cy, cz + d]
        frm = [to[0] - w, to[1] - h, cz]
        u, v = box['uv']
        w, h, d = round(w), round(h), round(d)
        faces = {'east': [u, v + d, u + d, v + d + h], 'north': [u + d, v + d, u + d + w, v + d + h],
                 'west': [u + d + w, v + d, u + 2 * d + w, v + d + h], 'south': [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
                 'up': [u + d + w, v + d, u + d, v], 'down': [u + d + 2 * w, v, u + d + w, v + d]}
        if box['mirror']:
            faces['east'], faces['west'] = faces['west'], faces['east']
            faces = {k: [r[2], r[1], r[0], r[3]] for k, r in faces.items()}
        count[mat] = count.get(mat, 0) + 1
        e = {'name': f'new_{mat}{count[mat]}', 'box_uv': True, 'render_order': 'default', 'locked': False, 'export': True,
             'scope': 0, 'allow_mirror_modeling': True, 'from': frm, 'to': to, 'autouv': 0, 'color': 3,
             'inflate': box['inflate'], 'mirror_uv': bool(box['mirror']), 'origin': BB_ORIGIN[part], 'uv_offset': [u, v],
             'faces': {k: {'uv': r, 'texture': 0} for k, r in faces.items()}, 'type': 'cube', 'uuid': str(uuid.uuid4())}
        if not e['inflate']: del e['inflate']
        bb['elements'].append(e)
        gid = groups[BB_GROUP[(piece, part)]]['uuid']
        node = None
        for root in bb['outliner']:
            node = node or find(root, gid)
        node['children'].append(e['uuid'])
    buf = io.BytesIO()
    suit.tex.save(buf, 'PNG')
    bb['textures'][0]['source'] = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()
    json.dump(bb, open(dst, 'w', encoding='utf-8'))


SUITS = [('impact', 'Impact Armor', impact), ('hazard', 'Hazard Armor', hazard),
         ('glacial', 'Glacial Armor', glacial), ('sea_diver', 'Sea Diver Armor', sea_diver)]

if __name__ == '__main__':
    res, out = sys.argv[1], sys.argv[2]
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 20)
    f = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 15)
    for name, title, build in SUITS:
        mp = os.path.join(res, 'armor_models', name + '_armor.json')
        tp = os.path.join(res, 'textures/models/armor', name + '_armor.png')
        before = Suit(mp, tp)
        after = Suit(mp, tp)
        build(after)
        if len(sys.argv) > 3:                                   # carpeta de diseños: escribe ahí los .bbmodel nuevos
            write_bbmodel(os.path.join(sys.argv[3], 'armor_v1', name + '_armor.bbmodel'),
                          os.path.join(sys.argv[3], name + '_armor.bbmodel'), after)
        after.tex.save(os.path.join(out, name + '_concept_texture.png'))
        json.dump(after.model, open(os.path.join(out, name + '_concept_model.json'), 'w'))
        kw = dict(scale=17, size=(440, 640))
        views = [PV.render(before.model, before.tex, math.radians(-30), math.radians(-12), **kw),
                 PV.render(after.model, after.tex, math.radians(-30), math.radians(-12), **kw),
                 PV.render(after.model, after.tex, math.radians(150), math.radians(-12), **kw)]
        big = PV.render(after.model, after.tex, math.radians(-32), math.radians(-10), scale=44, size=(1100, 1600))
        close = big.crop((550 - 300, 432 - 470, 550 + 300, 432 + 170)).resize((440, 469), Image.NEAREST)
        sheet = Image.new('RGBA', (440 * 4, 640 + 70), (66, 72, 86, 255))
        d = ImageDraw.Draw(sheet)
        d.rectangle([0, 0, 439, 710], fill=(52, 57, 68, 255))
        for i, v in enumerate(views): sheet.alpha_composite(v, (i * 440, 50))
        sheet.alpha_composite(close, (3 * 440, 130))
        d.text((16, 12), title, fill=(240, 240, 240, 255), font=F)
        for i, lab in enumerate(['Ahora', 'Propuesta: frente', 'Propuesta: espalda', 'Propuesta: casco de cerca']):
            d.text((i * 440 + 16, 680), lab, fill=(200, 200, 200, 255), font=f)
        sheet.save(os.path.join(out, 'propuesta_' + name + '_armor.png'))
        print(name, len(after.uv), 'cajas nuevas')
