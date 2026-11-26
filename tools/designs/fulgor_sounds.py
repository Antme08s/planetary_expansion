"""Sonidos propios de las criaturas de Fulgor (1.0.4), sintetizados con las mismas piezas que ferra_sounds.py.

Uso: python3 -I tools/designs/fulgor_sounds.py <carpeta_salida>

- Arco: chasquidos de mandíbulas con chisporroteo eléctrico; descarga seca (zap) al soltar la chispa.
- Zancudo de fulgurita: trinos de cristal (campanillas desafinadas); al sobrecargarse, un zumbido que sube.
- Atrapa-rayos: zumbido eléctrico grave con un gruñido metálico; chillido al saltar; crepitar al recargarse con un rayo.
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cerulea_sounds as C  # noqa: E402
from cerulea_sounds import SR, t_of, curve, env, osc, noise, bandpass, lowpass, highpass, room, norm  # noqa: E402
from ferra_sounds import creature, unit, cave, clicks, gravel  # noqa: E402

RNG = np.random.default_rng(7171)


def crackle(n, density=60, lo=2000, hi=9000):
    """Chisporroteo eléctrico: chasquidos muy cortos y agudos, irregulares."""
    out = np.zeros(n)
    for _ in range(density):
        s0 = int(RNG.random() * max(1, n - 400))
        d = int(SR * (0.0008 + 0.003 * RNG.random()))
        out[s0:s0 + d] += highpass(bandpass(noise(d), lo, hi), lo) * (0.3 + RNG.random())
    return unit(out)


def buzz(n, f, rough=0.4):
    """Zumbido de corriente: sierra a f con armónicos y algo de ruido."""
    t = np.arange(n) / SR
    s = sum(np.sin(2 * np.pi * f * k * t) / k for k in range(1, 9))
    return unit(s + rough * bandpass(noise(n), f * 4, f * 12))


def chime(n, freqs, decay=6.0):
    t = np.arange(n) / SR
    out = np.zeros(n)
    for i, f in enumerate(freqs):
        s0 = int(i * SR * 0.07)
        if s0 >= n:
            break
        tt = t[: n - s0]
        out[s0:] += (np.sin(2 * np.pi * f * tt) + 0.4 * np.sin(2 * np.pi * f * 2.76 * tt)) * np.exp(-decay * tt)
    return unit(out)


# ------------------------------------------------------------------ arco

def arc_ambient(v):
    dur = [1.0, 0.8, 1.2][v]
    n = len(t_of(dur))
    c = clicks(n, 14 + 4 * v, 3200, 0.003, 0.5)
    k = crackle(n, 30 + 10 * v)
    return norm(room((0.6 * c + 0.5 * k) * env(n, 0.05, 0.3), 0.2), 0.55)


def arc_zap(v):
    dur = 0.45
    n = len(t_of(dur))
    snap = crackle(n, 120, 1500, 11000) * np.exp(-np.linspace(0, 8, n))
    hum = buzz(n, 120 + 30 * v, 0.6) * np.exp(-np.linspace(0, 10, n))
    return norm(room(snap + 0.5 * hum, 0.15), 0.9)


def arc_hurt(v):
    dur = 0.4
    n = len(t_of(dur))
    y = creature(curve([(0, 900 + 100 * v), (1, 600)], n), np.full(n, 2200.0), np.full(n, 4200.0), voices=2, jitter=0.2,
                 shimmer=0.5, breath=0.6, drive=3.0, lp=9000)
    return norm(room(y * env(n, 0.01, 0.6) + 0.4 * crackle(n, 40), 0.15), 0.8)


def arc_death(v):
    dur = 1.0
    n = len(t_of(dur))
    y = creature(curve([(0, 800), (1, 200)], n), np.full(n, 1800.0), np.full(n, 3800.0), voices=2, jitter=0.25,
                 shimmer=0.6, breath=0.7, drive=3.0, lp=8000)
    fizz = crackle(n, 160) * curve([(0, 1), (1, 0)], n)
    return norm(room(y * env(n, 0.01, 0.5) + 0.6 * fizz, 0.2), 0.85)


# ------------------------------------------------------------------ zancudo de fulgurita

def strider_ambient(v):
    dur = [1.4, 1.2, 1.6][v]
    n = len(t_of(dur))
    base = [1320, 1480, 1240][v]
    notes = [base, base * 1.19, base * 1.5, base * 1.33][: 3 + v % 2]
    return norm(room(chime(n, notes, 5.0) * env(n, 0.005, 0.3), 0.45, 1.2), 0.5)


def strider_hurt(v):
    dur = 0.5
    n = len(t_of(dur))
    clink = chime(n, [2200 + 300 * v, 3100], 14.0)
    squeak = creature(curve([(0, 1400), (1, 900)], n), np.full(n, 2600.0), np.full(n, 4400.0), voices=2, jitter=0.1,
                      breath=0.4, drive=2.5, lp=9000) * env(n, 0.01, 0.5)
    return norm(room(0.7 * clink + 0.6 * squeak, 0.25), 0.85)


def strider_death(v):
    dur = 1.6
    n = len(t_of(dur))
    shatter = gravel(n, 140, 2500, 11000) * np.exp(-np.linspace(0, 4, n))
    bells = chime(n, [1800, 2400, 1500, 2900, 1200], 4.0)
    return norm(cave(0.7 * shatter + 0.6 * bells, 0.4, 1.0), 0.9)


def strider_charge(v):
    dur = 1.8
    n = len(t_of(dur))
    t = np.arange(n) / SR
    f = 180 * (1 + 3 * (t / dur) ** 1.5)
    ph = 2 * np.pi * np.cumsum(f) / SR
    hum = np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.3 * np.sin(3.01 * ph)
    return norm(room(unit(hum) * curve([(0, 0), (0.2, 0.7), (0.9, 1.0), (1, 0)], n) + 0.4 * crackle(n, 80), 0.3), 0.75)


# ------------------------------------------------------------------ atrapa-rayos

def catcher_ambient(v):
    dur = [2.0, 1.6, 2.4][v]
    n = len(t_of(dur))
    hum = buzz(n, 50 + 10 * v, 0.3) * (0.7 + 0.3 * np.sin(2 * np.pi * 0.8 * np.arange(n) / SR))
    growl = creature(curve([(0, 70 + 10 * v), (0.5, 90), (1, 65)], n), np.full(n, 420.0), np.full(n, 1100.0), voices=3,
                     jitter=0.12, shimmer=0.4, sub=0.4, breath=0.4, drive=3.0, lp=3000)
    return norm(cave((0.5 * hum + 0.7 * growl) * env(n, 0.15, 0.3) + 0.15 * crackle(n, 20), 0.35, 0.9), 0.75)


def catcher_leap(v):
    dur = 0.8
    n = len(t_of(dur))
    f0 = curve([(0, 260 + 30 * v), (0.2, 520), (1, 300)], n)
    scr = creature(f0, curve([(0, 900), (1, 700)], n), curve([(0, 2200), (1, 1800)], n), voices=3, jitter=0.15,
                   shimmer=0.5, sub=0.35, breath=0.7, drive=4.0, lp=7000)
    return norm(room(scr * env(n, 0.01, 0.5) + 0.5 * crackle(n, 60) * np.exp(-np.linspace(0, 4, n)), 0.3), 0.92)


def catcher_charge(v):
    dur = 1.6
    n = len(t_of(dur))
    boom = unit(lowpass(noise(n), 120, 4)) * np.exp(-np.linspace(0, 3, n))
    sizzle = crackle(n, 300, 1200, 10000) * curve([(0, 1), (0.6, 0.6), (1, 0)], n)
    hum = buzz(n, 90, 0.4) * curve([(0, 0.2), (0.3, 1), (1, 0.3)], n)
    return norm(cave(0.6 * boom + 0.6 * sizzle + 0.5 * hum, 0.3, 0.9), 0.95)


def catcher_hurt(v):
    dur = 0.6
    n = len(t_of(dur))
    clank = sum(np.sin(2 * np.pi * f * t_of(dur)) for f in (420 + 50 * v, 1130)) * np.exp(-np.linspace(0, 10, n))
    y = creature(curve([(0, 220 + 30 * v), (0.3, 260), (1, 140)], n), curve([(0, 700), (1, 520)], n),
                 curve([(0, 1600), (1, 1200)], n), voices=3, jitter=0.12, shimmer=0.5, sub=0.4, breath=0.5, drive=3.5, lp=5000)
    return norm(room(0.35 * unit(clank) + y * env(n, 0.02, 0.55), 0.25, 0.7), 0.9)


def catcher_death(v):
    dur = 2.4
    n = len(t_of(dur))
    y = creature(curve([(0, 260), (0.3, 170), (1, 40)], n), curve([(0, 700), (1, 300)], n), curve([(0, 1600), (1, 600)], n),
                 voices=3, jitter=0.2, shimmer=0.6, fry=0.4, sub=0.5, breath=0.6, drive=3.5, lp=4000)
    t = np.arange(n) / SR
    down = np.sin(2 * np.pi * np.cumsum(120 * (1 - 0.8 * t / dur)) / SR) * curve([(0, 0.6), (1, 0)], n)
    return norm(cave(y * env(n, 0.02, 0.45) + 0.4 * down + 0.3 * crackle(n, 80), 0.4, 1.1), 0.9)


SOUNDS = {
    "arc_crawler.ambient": (arc_ambient, 3, "Arc Crawler crackles", "Arco chisporrotea"),
    "arc_crawler.zap": (arc_zap, 2, "Arc Crawler discharges", "Arco suelta una descarga"),
    "arc_crawler.hurt": (arc_hurt, 2, "Arc Crawler hurts", "Arco herido"),
    "arc_crawler.death": (arc_death, 1, "Arc Crawler dies", "Arco muere"),
    "fulgurite_strider.ambient": (strider_ambient, 3, "Fulgurite Strider chimes", "Zancudo de fulgurita tintinea"),
    "fulgurite_strider.hurt": (strider_hurt, 2, "Fulgurite Strider hurts", "Zancudo de fulgurita herido"),
    "fulgurite_strider.death": (strider_death, 1, "Fulgurite Strider shatters", "Zancudo de fulgurita se rompe"),
    "fulgurite_strider.charge": (strider_charge, 1, "Fulgurite Strider overcharges", "Zancudo de fulgurita se sobrecarga"),
    "spire_catcher.ambient": (catcher_ambient, 3, "Spire Catcher hums", "Atrapa-rayos zumba"),
    "spire_catcher.leap": (catcher_leap, 2, "Spire Catcher leaps", "Atrapa-rayos salta"),
    "spire_catcher.charge": (catcher_charge, 1, "Spire Catcher absorbs lightning", "Atrapa-rayos absorbe un rayo"),
    "spire_catcher.hurt": (catcher_hurt, 2, "Spire Catcher hurts", "Atrapa-rayos herido"),
    "spire_catcher.death": (catcher_death, 1, "Spire Catcher dies", "Atrapa-rayos muere"),
}

if __name__ == "__main__":
    C.SOUNDS = SOUNDS
    C.build(sys.argv[1])
