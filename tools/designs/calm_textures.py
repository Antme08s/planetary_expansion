"""Versiones "calmadas" de las texturas de suelo y roca que, al repetirse en el mundo, mostraban un rasgo que se
reconocía en bucle (1.0.4).

Causas que se corrigen:
  - ruido de periodo 8: en un bloque de 16 px solo tiene 2x2 celdas y dibuja un remolino grande que se repite;
  - un unico rasgo fuerte por bloque (una grieta, una veta, tres puntos brillantes, una mancha oscura).
Ahora: ruido de manchas pequeñas (periodos 4 y 2) y detalles pequeños, de poco contraste, repartidos con separacion.

Todo se dibuja con un generador aleatorio propio; el generador compartido de cada script lo gasta la funcion antigua
(ver `calmed`), asi el resto de texturas que salen despues del mismo generador no cambian ni un pixel.
"""
import random

import numpy as np

T = 16


def _AT():
    import amber_textures as AT  # import perezoso: amber_textures importa este modulo
    return AT


def calmed(old_fn, new_fn, rng, seed):
    """Ejecuta la funcion antigua (para gastar los mismos numeros aleatorios del generador compartido) y devuelve
    la textura nueva, dibujada con un generador propio."""
    old_fn(rng)
    return new_fn(random.Random(seed))


def _lerp(a, b, k):
    return tuple(int(a[i] + (b[i] - a[i]) * k) for i in range(3))


def flecks(img, rng, n, factors, min_d=4, tint=None):
    """n motas de un solo pixel, separadas al menos min_d (con vuelta de borde). Cada una cambia el tono del suelo
    donde cae (factor) o lo acerca a un color (tint=(color, cantidad)): poco contraste, sin formar dibujo."""
    AT = _AT()
    px = img.load()
    pts = []
    tries = 0
    while len(pts) < n and tries < 600:
        tries += 1
        x, y = rng.randrange(T), rng.randrange(T)
        if all(min(abs(x - a), T - abs(x - a)) ** 2 + min(abs(y - b), T - abs(y - b)) ** 2 >= min_d * min_d
               for a, b in pts):
            pts.append((x, y))
            c = px[x, y][:3]
            if tint:
                px[x, y] = AT.rgba(_lerp(c, tint[0], tint[1]))
            else:
                px[x, y] = AT.rgba(AT.shade(c, rng.choice(factors)))
    return img


def _field(rng):
    return _AT().pnoise(rng, ((4, 0.55), (2, 0.45)))


def stone(rng):
    AT = _AT()
    img = AT.edges(AT.posterize(_field(rng), AT.ramp((80, 86, 100), (140, 146, 158), 5), rng, 0.12))
    return flecks(img, rng, 6, (0.9, 1.08))


def ferra_dust(rng):
    AT = _AT()
    f = AT.pnoise(rng, ((4, 0.3), (2, 0.7)))
    img = AT.posterize(f * 0.8 + 0.2, AT.ramp((132, 56, 30), (214, 116, 64), 5), rng, 0.2)
    return flecks(img, rng, 9, (0.84, 0.9, 1.1), min_d=3)


def ferra_rock(rng):
    AT = _AT()
    img = AT.edges(AT.posterize(_field(rng), AT.ramp((64, 34, 28), (132, 74, 52), 5), rng, 0.08))
    flecks(img, rng, 4, (0.85,), min_d=4)
    return flecks(img, rng, 4, (), min_d=5, tint=((176, 84, 40), 0.45))     # el óxido, en motas sueltas


def velo_rock(rng):
    AT = _AT()
    img = AT.edges(AT.posterize(_field(rng), AT.ramp((118, 126, 146), (186, 192, 208), 5), rng, 0.08))
    return flecks(img, rng, 7, (0.9, 0.94, 1.06))


def deep_stone(rng):
    """Estratos horizontales irregulares: el grosor y el tono de cada franja cambian, sin periodo fijo."""
    AT = _AT()
    n = 8
    g = [rng.random() for _ in range(n)]
    rowv = np.zeros(T)
    for y in range(T):                                   # valor suave por fila, sin repetir cada 4 px
        fy = y / 2.0
        i, t = int(fy) % n, fy - int(fy)
        rowv[y] = g[i] * (1 - t) + g[(i + 1) % n] * t
    streak = np.zeros((T, T))
    for y in range(T):                                   # variacion a lo largo de cada fila (veta alargada)
        gg = [rng.random() for _ in range(2)]
        for x in range(T):
            fx = x / 8.0
            i, t = int(fx) % 2, fx - int(fx)
            t = t * t * (3 - 2 * t)
            streak[y, x] = gg[i] * (1 - t) + gg[(i + 1) % 2] * t
    fine = AT.pnoise(rng, ((4, 0.5), (2, 0.5)))
    f = 0.42 * rowv[:, None] + 0.30 * streak + 0.28 * fine
    f -= f.min()
    f /= max(1e-9, f.max())
    img = AT.edges(AT.posterize(f, AT.ramp((38, 40, 56), (92, 96, 118), 5), rng, 0.07))
    return flecks(img, rng, 4, (1.12, 0.9))


def soil(rng):
    AT = _AT()
    img = AT.edges(AT.posterize(_field(rng), AT.ramp((48, 40, 58), (104, 92, 116), 5), rng, 0.10))
    flecks(img, rng, 4, (), min_d=5, tint=((126, 118, 140), 0.42))      # piedrecitas, discretas
    flecks(img, rng, 3, (), min_d=5, tint=((116, 134, 128), 0.38))      # raicillas, apenas verdosas
    return flecks(img, rng, 5, (0.88,), min_d=3)


def pale_sand(rng):
    AT = _AT()
    img = AT.posterize(AT.pnoise(rng, ((4, 0.5), (2, 0.5))), AT.ramp((170, 178, 176), (222, 228, 222), 5), rng, 0.16)
    flecks(img, rng, 6, (0.93, 1.05), min_d=3)
    return flecks(img, rng, 3, (), min_d=5, tint=((150, 166, 174), 0.30))
