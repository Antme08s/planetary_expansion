"""Items 16x16 redibujados (1.0.4): mismo tamaño, otro sombreado.

Reglas de estilo (sacadas de Create y Farmer's Delight):
  - luz fija arriba-izquierda, con un poco de luz rebotada abajo-derecha para que la sombra no quede muerta;
  - 6 tonos por material, sombras frias y luces calidas;
  - un brillo (1-2 pixeles) en las superficies lisas;
  - el contorno NO es negro: toma el color del material, oscurecido, mas claro arriba-izquierda y mas oscuro abajo-derecha.
Todo se dibuja con volumenes (elipsoides, poligonos) sombreados por codigo; nada copiado de otros mods.
"""
import colorsys
import math

import numpy as np
from PIL import Image

T = 16
WARM, COOL = 0.11, 0.70
STEPS = (-1.0, -0.6, -0.25, 0.0, 0.35, 0.75)


def _toward(h, target, amount):
    d = ((target - h + 0.5) % 1.0) - 0.5
    return h + d * amount


def tone(base, step, sat=1.0):
    h, s, v = colorsys.rgb_to_hsv(*[c / 255 for c in base])
    if step < 0:
        k = -step
        v *= 1 - 0.46 * k
        h = _toward(h, COOL, 0.12 * k)
        s = min(1, s * (1 + 0.28 * k))
    elif step > 0:
        v = v + (1 - v) * 0.62 * step
        h = _toward(h, WARM, 0.08 * step)
        s *= 1 - 0.22 * step
    r, g, b = colorsys.hsv_to_rgb(h % 1, min(1, s * sat), min(1, v))
    return (round(r * 255), round(g * 255), round(b * 255))


def ramp(base, sat=1.0):
    """6 tonos de oscuro a claro a partir del color medio."""
    return [tone(base, s, sat) for s in STEPS]


class Canvas:
    def __init__(self):
        self.px = {}

    def set(self, x, y, c):
        if 0 <= x < T and 0 <= y < T:
            self.px[(x, y)] = tuple(c)

    def get(self, x, y):
        return self.px.get((x, y))

    # ---- volumenes ----
    def ellipsoid(self, cx, cy, rx, ry, rp, spec=True, bounce=0.10, light=(-0.5, -0.6, 0.62), tilt=0.0, mask=None):
        """Elipsoide sombreado con la paleta rp (6 tonos). tilt gira el elipsoide (radianes)."""
        lx, ly, lz = light
        n = math.sqrt(lx * lx + ly * ly + lz * lz)
        lx, ly, lz = lx / n, ly / n, lz / n
        ct, st = math.cos(tilt), math.sin(tilt)
        for y in range(T):
            for x in range(T):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                u, v = dx * ct + dy * st, -dx * st + dy * ct
                nx, ny = u / rx, v / ry
                d2 = nx * nx + ny * ny
                if d2 > 1.0:
                    continue
                if mask and not mask(x, y):
                    continue
                nz = math.sqrt(max(0.0, 1 - d2))
                # normal en el sistema de la pantalla
                wx, wy = nx * ct - ny * st, nx * st + ny * ct
                L = max(0.0, wx * lx + wy * ly + nz * lz)
                if wx > 0.25 and wy > 0.15:
                    L += bounce * (wx + wy)
                idx = int(round(0.4 + L * 4.2))
                idx = max(0, min(4, idx))
                if spec and L > 0.93 and nz > 0.55:
                    idx = 5
                self.set(x, y, rp[idx])

    def poly(self, pts, color):
        """Poligono relleno (coordenadas de pixel, con medio pixel de centro)."""
        n = len(pts)
        for y in range(T):
            for x in range(T):
                px_, py_ = x + 0.5, y + 0.5
                inside = False
                j = n - 1
                for i in range(n):
                    xi, yi = pts[i]
                    xj, yj = pts[j]
                    if (yi > py_) != (yj > py_) and px_ < (xj - xi) * (py_ - yi) / (yj - yi + 1e-12) + xi:
                        inside = not inside
                    j = i
                if inside:
                    self.set(x, y, color)

    def line(self, x0, y0, x1, y1, color):
        n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(n + 1):
            t = i / max(1, n)
            self.set(round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), color)

    def grid(self, rows, pal, ox=0, oy=0):
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch != "." and ch in pal:
                    self.set(ox + dx, oy + dy, pal[ch])

    # ---- acabado ----
    def outline(self, factor=0.42, cool=0.08):
        """Contorno exterior de 1 px con el color del material vecino, oscurecido (mas claro arriba-izquierda)."""
        out = {}
        for (x, y), c in list(self.px.items()):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if q in self.px or not (0 <= q[0] < T and 0 <= q[1] < T):
                    continue
                h, s, v = colorsys.rgb_to_hsv(*[t / 255 for t in c])
                f = factor * (1.25 if (dx < 0 or dy < 0) else 0.85)       # lado de la luz: mas claro
                col = colorsys.hsv_to_rgb(_toward(h, COOL, cool) % 1, min(1, s * 1.1 + 0.1), v * f)
                col = tuple(round(t * 255) for t in col)
                if q not in out or sum(col) < sum(out[q]):
                    out[q] = col
        self.px.update(out)

    def merge(self, other):
        """Pone otra capa encima (se usa para que cada pieza lleve su propio contorno)."""
        self.px.update(other.px)

    def image(self):
        im = Image.new("RGBA", (T, T), (0, 0, 0, 0))
        for (x, y), c in self.px.items():
            im.putpixel((x, y), tuple(c) + (255,))
        return im


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
