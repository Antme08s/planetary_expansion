"""Sonidos propios de los monstruos de Ferra (1.0.3, el usuario: "es buen momento para que los dos mobs ya tengan sonido
propio"), sintetizados con las mismas piezas que cerulea_sounds.py (nada sacado de Minecraft ni de otros juegos).

Uso: python3 -I tools/designs/ferra_sounds.py <carpeta_salida>
Escribe <carpeta>/<criatura>/<sonido><n>.ogg y <carpeta>/preview.mp3 con todos seguidos.

- Gusano de óxido: retumbo de tierra y grava al avanzar enterrado; estallido de tierra con un chillido metálico al salir;
  rechinar de placas y dientes fuera; crujido al morder; tierra que se cierra al meterse.
- Rastreador ciego: ráfagas de chasquidos (como un sonar) cuando escucha; el grito de alerta (agudo, desafinado, con la
  vocal que se abre) antes de cazar; mordisco seco; quejido y muerte con estertor.
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cerulea_sounds as C  # noqa: E402
from cerulea_sounds import SR, t_of, curve, env, osc, saw, noise, bandpass, lowpass, highpass, formants, room, norm  # noqa: E402

RNG = np.random.default_rng(5150)


def gravel(n, density=40, lo=500, hi=4000):
    """Granos de grava/tierra: muchos chasquidos cortos filtrados."""
    out = np.zeros(n)
    for _ in range(density):
        s0 = int(RNG.random() * max(1, n - 3000))
        d = int(SR * (0.004 + 0.02 * RNG.random()))
        out[s0:s0 + d] += bandpass(noise(d), lo, hi) * np.exp(-np.linspace(0, 6, d)) * (0.3 + RNG.random())
    return out / (np.max(np.abs(out)) + 1e-9)


def unit(x):
    return x / (np.max(np.abs(x)) + 1e-9)


# ------------------------------------------------------------------ gusano de óxido

def worm_dig(v):
    dur = [0.9, 0.8, 1.0][v]
    n = len(t_of(dur))
    rumble = unit(lowpass(noise(n), 90 + 15 * v, 4)) * (1 + 0.4 * np.sin(2 * np.pi * 7 * t_of(dur)))
    y = (0.9 * rumble + 0.45 * gravel(n, 30 + 8 * v, 300, 2500)) * env(n, 0.15, 0.4)
    return norm(room(y, 0.15), 0.8)


def worm_emerge(v):
    dur = 1.6
    n = len(t_of(dur))
    boom = unit(lowpass(noise(n), 120, 4)) * np.exp(-np.linspace(0, 4, n))
    burst = gravel(n, 160, 400, 6000) * curve([(0, 0.0), (0.05, 1.0), (0.6, 0.5), (1, 0.0)], n)
    # chillido metálico: sierra que sube, modulada en anillo y con vocal que se abre
    f = curve([(0, 180 + 20 * v), (0.25, 620), (0.7, 540), (1, 300)], n)
    scr = saw(f, 30) * np.sin(2 * np.pi * curve([(0, 37), (1, 61)], n) * t_of(dur))
    scr = formants(scr, curve([(0, 500), (0.3, 900), (1, 600)], n), curve([(0, 1200), (0.3, 2300), (1, 1500)], n))
    scr = unit(scr) * curve([(0, 0), (0.15, 0.0), (0.3, 0.9), (0.85, 0.6), (1, 0)], n)
    return norm(room(0.9 * boom + 0.6 * burst + 0.55 * scr, 0.3), 0.92)


def worm_ambient(v):
    dur = [1.4, 1.8, 1.2][v]
    n = len(t_of(dur))
    groan = formants(saw(curve([(0, 55 + 6 * v), (0.5, 70), (1, 50)], n), 30), curve([(0, 300), (1, 420)], n),
                     curve([(0, 700), (1, 900)], n))
    wet = np.zeros(n)
    for _ in range(10 + 4 * v):   # gorgoteo
        s0 = int(RNG.random() * (n - 4000))
        d = int(SR * (0.03 + 0.04 * RNG.random()))
        wet[s0:s0 + d] += osc(curve([(0, 200 + 300 * RNG.random()), (1, 600 + 600 * RNG.random())], d)) * np.hanning(d)
    teeth = gravel(n, 14 + 4 * v, 1500, 6000)
    y = 0.8 * unit(groan) + 0.35 * unit(wet) + 0.3 * teeth
    return norm(room(y * env(n, 0.15, 0.3), 0.25), 0.8)


def worm_bite(v):
    dur = 0.45
    n = len(t_of(dur))
    crunch = gravel(n, 25, 800, 6000) * np.exp(-np.linspace(0, 5, n))
    snap = osc(curve([(0, 160 - 20 * v), (1, 70)], n)) * env(n, 0.005, 0.9, 2.5)
    return norm(room(crunch + 0.8 * snap, 0.15), 0.92)


def worm_hurt(v):
    dur = 0.6
    n = len(t_of(dur))
    clank = sum(np.sin(2 * np.pi * f * t_of(dur)) for f in (340 + 40 * v, 910, 1530)) * np.exp(-np.linspace(0, 10, n))
    scr = unit(formants(saw(curve([(0, 300), (1, 220)], n), 25), np.full(n, 700.0), np.full(n, 1700.0))) * env(n, 0.05, 0.6)
    return norm(room(0.7 * unit(clank) + 0.8 * scr, 0.2), 0.9)


def worm_death(v):
    dur = 2.4
    n = len(t_of(dur))
    x = saw(curve([(0, 260), (0.3, 150), (1, 35)], n), 40) * (1 + 0.6 * np.abs(np.sin(2 * np.pi * curve([(0, 12), (1, 3)], n) * t_of(dur))))
    y = unit(formants(x, curve([(0, 700), (1, 250)], n), curve([(0, 1600), (1, 600)], n)))
    rattle = gravel(n, 70, 600, 3500) * curve([(0, 0.2), (0.7, 0.8), (1, 0)], n)
    return norm(room((0.9 * y + 0.5 * rattle) * env(n, 0.03, 0.4), 0.35), 0.9)


def worm_dive(v):
    dur = 1.0
    n = len(t_of(dur))
    slide = gravel(n, 90, 300, 3000) * curve([(0, 1), (1, 0)], n)
    whoosh = unit(bandpass(noise(n), 150, 900)) * curve([(0, 0.2), (0.3, 1), (1, 0)], n)
    return norm(room(0.8 * slide + 0.6 * whoosh, 0.2), 0.85)


# ------------------------------------------------------------------ rastreador ciego

def clicks(n, rate, freq=2600, q=0.004, jitter=0.3):
    """Tren de chasquidos (sonar)."""
    out = np.zeros(n)
    t = 0.0
    while t < n / SR - 0.02:
        s0 = int(t * SR)
        d = int(SR * q)
        out[s0:s0 + d] += bandpass(noise(d), freq * 0.6, freq * 1.6) * np.exp(-np.linspace(0, 8, d))
        t += 1.0 / rate * (1 + jitter * (RNG.random() - 0.5))
    return unit(out)


def stalker_ambient(v):
    dur = [1.2, 1.6, 1.0][v]
    n = len(t_of(dur))
    r = curve([(0, 8), (0.5, 22 + 6 * v), (1, 10)], n)
    c = clicks(n, float(np.mean(r)), 2400 + 300 * v)
    breath = unit(bandpass(noise(n), 300, 1200)) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.9 * t_of(dur)) ** 2)
    return norm(room((0.8 * c + 0.35 * breath) * env(n, 0.1, 0.25), 0.2), 0.75)


def stalker_listen(v):
    dur = 0.8
    n = len(t_of(dur))
    c = clicks(n, 30 + 8 * v, 3000, 0.003, 0.15) * curve([(0, 0.3), (0.5, 1), (1, 0.4)], n)
    return norm(room(c, 0.15), 0.7)


def stalker_scream(v):
    """Grito de alerta: arranca grave y sube a un chillido desafinado (dos voces), con la vocal que se abre y vibrato."""
    dur = [1.5, 1.3][v]
    n = len(t_of(dur))
    base = curve([(0, 220), (0.18, 760 + 60 * v), (0.75, 700), (1, 420)], n)
    vib = 1 + 0.025 * np.sin(2 * np.pi * 9 * t_of(dur))
    a = saw(base * vib, 28)
    b = saw(base * vib * 1.06, 28)          # segunda voz desafinada (disonancia)
    x = formants(a + 0.8 * b, curve([(0, 450), (0.2, 950), (1, 750)], n), curve([(0, 1100), (0.2, 2500), (1, 1900)], n),
                 curve([(0, 2400), (1, 3300)], n), bw=(140, 220, 300))
    rasp = unit(bandpass(noise(n), 1500, 6000)) * 0.35
    y = (unit(x) + rasp) * env(n, 0.04, 0.35)
    return norm(room(y, 0.35, 0.6), 0.95)


def stalker_attack(v):
    dur = 0.35
    n = len(t_of(dur))
    snap = unit(bandpass(noise(n), 1200, 7000)) * env(n, 0.002, 0.95, 4)
    growl = unit(formants(saw(curve([(0, 160), (1, 110)], n), 25), np.full(n, 600.0), np.full(n, 1400.0))) * env(n, 0.05, 0.6)
    return norm(room(snap + 0.6 * growl, 0.15), 0.9)


def stalker_hurt(v):
    dur = 0.55
    n = len(t_of(dur))
    x = formants(saw(curve([(0, 520 + 50 * v), (0.3, 640), (1, 300)], n), 25), curve([(0, 800), (1, 600)], n),
                 curve([(0, 2000), (1, 1400)], n))
    return norm(room(unit(x) * env(n, 0.02, 0.6) + 0.3 * clicks(n, 40) * env(n, 0.01, 0.9), 0.2), 0.9)


def stalker_death(v):
    dur = 2.0
    n = len(t_of(dur))
    x = saw(curve([(0, 600), (0.25, 420), (1, 90)], n), 30) * (1 + 0.8 * np.abs(np.sin(2 * np.pi * curve([(0, 18), (1, 4)], n) * t_of(dur))))
    y = unit(formants(x, curve([(0, 850), (1, 350)], n), curve([(0, 2100), (1, 800)], n)))
    c = clicks(n, 14, 2000) * curve([(0, 0.6), (1, 0.0)], n)
    return norm(room((0.9 * y + 0.3 * c) * env(n, 0.02, 0.5), 0.3), 0.9)


SOUNDS = {
    "rust_worm.dig": (worm_dig, 3, "Rust Worm burrows", "Gusano de óxido excava"),
    "rust_worm.emerge": (worm_emerge, 2, "Rust Worm bursts out", "Gusano de óxido sale de la tierra"),
    "rust_worm.ambient": (worm_ambient, 3, "Rust Worm grinds", "Gusano de óxido rechina"),
    "rust_worm.bite": (worm_bite, 2, "Rust Worm bites", "Gusano de óxido muerde"),
    "rust_worm.hurt": (worm_hurt, 2, "Rust Worm hurts", "Gusano de óxido herido"),
    "rust_worm.death": (worm_death, 1, "Rust Worm dies", "Gusano de óxido muere"),
    "rust_worm.dive": (worm_dive, 1, "Rust Worm burrows back", "Gusano de óxido se entierra"),
    "blind_stalker.ambient": (stalker_ambient, 3, "Blind Stalker clicks", "Rastreador ciego chasquea"),
    "blind_stalker.listen": (stalker_listen, 2, "Blind Stalker listens", "Rastreador ciego escucha"),
    "blind_stalker.scream": (stalker_scream, 2, "Blind Stalker screams", "Rastreador ciego grita"),
    "blind_stalker.attack": (stalker_attack, 2, "Blind Stalker bites", "Rastreador ciego muerde"),
    "blind_stalker.hurt": (stalker_hurt, 2, "Blind Stalker hurts", "Rastreador ciego herido"),
    "blind_stalker.death": (stalker_death, 1, "Blind Stalker dies", "Rastreador ciego muere"),
}


if __name__ == "__main__":
    C.SOUNDS = SOUNDS          # mismo build (ogg por variante + preview.mp3)
    C.build(sys.argv[1])
