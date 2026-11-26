"""Sonidos propios de las criaturas de Titán (1.0.4), sintetizados con las mismas piezas que ferra_sounds.py (nada
sacado de Minecraft ni de otros juegos). La anguila de metano usa los sonidos de pez de Minecraft.

Uso: python3 -I tools/designs/titan_sounds.py <carpeta_salida>

- Rumiador de tolina: bramido grave y nasal, como una bocina lejana en la bruma; quejido corto; muerte con un bramido que
  se apaga.
- Acechador de bruma: silbido hueco y desafinado, como viento que pasa por hielo, con chasquidos; zumbido suave de la
  linterna (un tono que oscila) cuando ronda una luz; chillido agudo y crujido de hielo al morder; quejido y muerte.
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cerulea_sounds as C  # noqa: E402
from cerulea_sounds import SR, t_of, curve, env, osc, noise, bandpass, lowpass, room, norm  # noqa: E402
from ferra_sounds import creature, unit, cave, clicks, gravel, subbass  # noqa: E402

RNG = np.random.default_rng(6260)


# ------------------------------------------------------------------ rumiador de tolina

def ruminant_ambient(v):
    """Bramido grave y nasal que sube y baja despacio."""
    dur = [2.2, 1.8, 2.6][v]
    n = len(t_of(dur))
    f0 = curve([(0, 62 + 6 * v), (0.3, 78 + 4 * v), (0.75, 72), (1, 55)], n)
    y = creature(f0, curve([(0, 300), (0.4, 420), (1, 340)], n), curve([(0, 800), (0.4, 950), (1, 760)], n),
                 curve([(0, 2100), (1, 1900)], n), voices=3, detune=0.025, jitter=0.04, shimmer=0.2, sub=0.25,
                 breath=0.25, drive=2.0, lp=2600, bw=(90, 140, 220))
    hum = osc(f0 * 0.5) * 0.35                                    # pecho grande
    return norm(cave((y + hum) * env(n, 0.18, 0.3), 0.45, 1.2), 0.8)


def ruminant_hurt(v):
    dur = 0.8
    n = len(t_of(dur))
    f0 = curve([(0, 120 + 15 * v), (0.2, 150), (1, 80)], n)
    y = creature(f0, curve([(0, 450), (1, 380)], n), curve([(0, 1000), (1, 850)], n), voices=3, jitter=0.08,
                 shimmer=0.35, sub=0.35, breath=0.4, drive=3.0, lp=3000)
    return norm(room(y * env(n, 0.02, 0.5), 0.3, 0.8), 0.9)


def ruminant_death(v):
    dur = 2.6
    n = len(t_of(dur))
    f0 = curve([(0, 110), (0.3, 85), (1, 32)], n)
    y = creature(f0, curve([(0, 420), (1, 260)], n), curve([(0, 950), (1, 600)], n), voices=3, jitter=0.12,
                 shimmer=0.45, fry=0.3, sub=0.45, breath=0.5, drive=2.5, lp=2400)
    return norm(cave(y * env(n, 0.03, 0.55) + 0.3 * subbass(n, 60, 30, 2.0), 0.45, 1.2), 0.9)


# ------------------------------------------------------------------ acechador de bruma

def whistle(n, f, wobble=0.04, rate=3.0):
    """Silbido hueco: ruido estrecho alrededor de f, que oscila."""
    t = np.arange(n) / SR
    fc = f * (1 + wobble * np.sin(2 * np.pi * rate * t))
    out = np.zeros(n)
    step = 2048
    for s0 in range(0, n, step):
        d = min(step + 256, n - s0)
        c = float(np.mean(fc[s0:s0 + d]))
        seg = bandpass(noise(d), c * 0.94, c * 1.06) * np.hanning(d)
        out[s0:s0 + d] += seg
    return unit(out)


def stalker_ambient(v):
    """Viento que silba entre el hielo, dos tonos desafinados, con chasquidos sueltos."""
    dur = [2.4, 2.0, 2.8][v]
    n = len(t_of(dur))
    a = whistle(n, 900 + 120 * v, 0.06, 2.2 + v)
    b = whistle(n, 1340 + 90 * v, 0.05, 3.1)
    c = clicks(n, 5 + 2 * v, 3000, 0.004, 0.6)
    y = (0.8 * a + 0.5 * b + 0.25 * c) * curve([(0, 0), (0.3, 1), (0.7, 0.8), (1, 0)], n)
    return norm(cave(y, 0.55, 1.4), 0.6)


def stalker_lure(v):
    """Zumbido suave de la linterna: un tono puro que oscila, como una lámpara que parpadea."""
    dur = 1.6
    n = len(t_of(dur))
    t = np.arange(n) / SR
    f = 520 + 60 * v
    tone = np.sin(2 * np.pi * f * t + 0.8 * np.sin(2 * np.pi * 5.5 * t)) + 0.35 * np.sin(2 * np.pi * f * 2.01 * t)
    flick = 0.6 + 0.4 * (np.sin(2 * np.pi * (7 + 3 * v) * t) > 0)
    return norm(room(tone * flick * env(n, 0.3, 0.3), 0.4, 1.0), 0.45)


def stalker_attack(v):
    """Chillido agudo con crujido de hielo."""
    dur = 0.7
    n = len(t_of(dur))
    f0 = curve([(0, 420 + 40 * v), (0.2, 640), (1, 300)], n)
    shriek = creature(f0, curve([(0, 1100), (1, 900)], n), curve([(0, 2600), (1, 2100)], n), curve([(0, 3600), (1, 3300)], n),
                      voices=3, detune=0.05, jitter=0.12, shimmer=0.45, sub=0.3, breath=0.9, drive=4.0, lp=7500,
                      bw=(180, 280, 400))
    ice = gravel(n, 40, 2500, 9000) * np.exp(-np.linspace(0, 6, n))
    return norm(room(0.8 * shriek * env(n, 0.01, 0.5) + 0.5 * ice, 0.3, 0.8), 0.92)


def stalker_hurt(v):
    dur = 0.6
    n = len(t_of(dur))
    y = creature(curve([(0, 520 + 60 * v), (0.3, 600), (1, 260)], n), curve([(0, 1200), (1, 800)], n),
                 curve([(0, 2600), (1, 1800)], n), voices=3, jitter=0.14, shimmer=0.5, sub=0.3, breath=0.8, drive=3.5, lp=7000)
    return norm(room(y * env(n, 0.02, 0.55), 0.35, 0.9), 0.9)


def stalker_death(v):
    dur = 2.4
    n = len(t_of(dur))
    f0 = curve([(0, 560), (0.3, 380), (1, 60)], n)
    y = creature(f0, curve([(0, 1200), (1, 400)], n), curve([(0, 2600), (1, 900)], n), voices=3, jitter=0.2,
                 shimmer=0.55, fry=0.4, sub=0.45, breath=0.9, drive=3.5, lp=6000)
    wind = whistle(n, 700, 0.1, 1.5) * curve([(0, 0), (0.4, 0.6), (1, 0)], n)
    return norm(cave(y * env(n, 0.02, 0.45) + 0.4 * wind, 0.5, 1.4), 0.9)


SOUNDS = {
    "tholin_ruminant.ambient": (ruminant_ambient, 3, "Tholin Ruminant bellows", "Rumiador de tolina brama"),
    "tholin_ruminant.hurt": (ruminant_hurt, 2, "Tholin Ruminant hurts", "Rumiador de tolina herido"),
    "tholin_ruminant.death": (ruminant_death, 1, "Tholin Ruminant dies", "Rumiador de tolina muere"),
    "mist_stalker.ambient": (stalker_ambient, 3, "Something whistles in the haze", "Algo silba en la bruma"),
    "mist_stalker.lure": (stalker_lure, 2, "A lantern hums in the haze", "Una linterna zumba en la bruma"),
    "mist_stalker.attack": (stalker_attack, 2, "Mist Stalker shrieks", "Acechador de bruma chilla"),
    "mist_stalker.hurt": (stalker_hurt, 2, "Mist Stalker hurts", "Acechador de bruma herido"),
    "mist_stalker.death": (stalker_death, 1, "Mist Stalker dies", "Acechador de bruma muere"),
}

if __name__ == "__main__":
    C.SOUNDS = SOUNDS
    C.build(sys.argv[1])
