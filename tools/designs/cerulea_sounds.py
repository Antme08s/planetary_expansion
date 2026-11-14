"""Sonidos propios de las criaturas de Cerúlea (1.0.3), sintetizados (nada sacado de Minecraft ni de otros juegos).

Uso: python3 -I tools/designs/cerulea_sounds.py <carpeta_salida>
Escribe <carpeta>/<criatura>/<sonido><n>.ogg (mono, 44,1 kHz, Vorbis) y <carpeta>/preview.mp3 con todos seguidos;
imprime la lista con el segundo en que empieza cada uno.

Idea de cada voz (todo en clave "alienígena": glissandos, formantes que cambian, modulación en anillo, pulsos):
- pastoreador: mugido grave tipo ballena con vocal que cambia (oo→aa→oo) y un batido en anillo;
- saltarín: trinos agudos con modulación rápida, chillidos; un "pop" suave al saltar;
- planeador: silbidos de dos notas con aire; chirrido; "fuuush" al lanzarse a planear;
- acechador: gruñido muy grave con burbujas; siseo; golpe al morder;
- croador: croar a pulsos que sube al final ("brrrr-up");
- corredor: gañidos en ráfaga de día, gruñido grave con chasquidos de noche, aullido corto al recibir daño.
"""
import math
import os
import subprocess
import sys
import wave

import numpy as np
from scipy import signal

SR = 44100
RNG = np.random.default_rng(1203)


# ------------------------------------------------------------------ piezas

def t_of(dur):
    return np.arange(int(SR * dur)) / SR


def curve(points, n):
    """Curva por tramos lineales: points = [(fracción 0..1, valor), ...]."""
    xs = np.linspace(0, 1, n)
    px, py = zip(*points)
    return np.interp(xs, px, py)


def env(n, a=0.02, r=0.2, shape=1.0):
    """Envolvente: ataque a, caída final r (en fracción de la duración)."""
    x = np.linspace(0, 1, n)
    e = np.minimum(1, x / max(a, 1e-4)) * np.minimum(1, (1 - x) / max(r, 1e-4))
    return np.clip(e, 0, 1) ** shape


def osc(freq, harmonics=(1.0,), phase0=0.0):
    """Oscilador con la frecuencia que cambia en el tiempo (array) y unos armónicos."""
    ph = 2 * np.pi * np.cumsum(freq) / SR + phase0
    out = np.zeros_like(freq)
    for k, amp in enumerate(harmonics, 1):
        out += amp * np.sin(k * ph)
    return out


def saw(freq, n_harm=24):
    return osc(freq, tuple(1.0 / k for k in range(1, n_harm + 1)))


def noise(n):
    return RNG.standard_normal(n)


def bandpass(x, lo, hi, order=2):
    b, a = signal.butter(order, [lo / (SR / 2), min(hi / (SR / 2), 0.99)], btype="band")
    return signal.lfilter(b, a, x)


def lowpass(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), btype="low")
    return signal.lfilter(b, a, x)


def highpass(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), btype="high")
    return signal.lfilter(b, a, x)


def formants(x, f1, f2, f3=None, bw=(90, 120, 160)):
    """Filtro de vocal que cambia en el tiempo (f1, f2, f3 son arrays): se procesa a trozos de 10 ms."""
    out = np.zeros_like(x)
    hop = 441
    zi = {}
    for i in range(0, len(x), hop):
        seg = x[i:i + hop]
        acc = np.zeros_like(seg)
        for j, (fs, g) in enumerate(((f1, 1.0), (f2, 0.6), (f3, 0.3))):
            if fs is None:
                continue
            fc = float(fs[min(i, len(fs) - 1)])
            w = bw[j]
            b, a = signal.iirpeak(fc / (SR / 2), fc / w)
            z = zi.get(j)
            if z is None:
                z = signal.lfilter_zi(b, a) * 0
            y, z = signal.lfilter(b, a, seg, zi=z)
            zi[j] = z
            acc += g * y
        out[i:i + hop] = acc
    return out


def room(x, amount=0.18, length=0.35):
    """Cola corta de reverberación (convolución con ruido que se apaga)."""
    n = int(SR * length)
    ir = noise(n) * np.exp(-np.linspace(0, 7, n))
    ir = lowpass(ir, 5000)
    ir[0] = 1.0 / max(amount, 1e-3)
    y = signal.fftconvolve(x, ir)[:len(x) + n]
    return y * amount


def norm(x, peak=0.85):
    x = x - np.mean(x)
    m = np.max(np.abs(x)) or 1.0
    return x / m * peak


def pad(x, before=0.0, after=0.05):
    return np.concatenate([np.zeros(int(SR * before)), x, np.zeros(int(SR * after))])


# ------------------------------------------------------------------ voces

def grazer_ambient(v):
    dur = [1.5, 1.2, 1.7][v]
    n = len(t_of(dur))
    glide = [[(0, 105), (0.35, 150), (0.75, 120), (1, 88)],
             [(0, 130), (0.5, 175), (1, 110)],
             [(0, 95), (0.25, 125), (0.5, 105), (0.8, 140), (1, 92)]][v]
    f = curve(glide, n) * (1 + 0.018 * np.sin(2 * np.pi * 5.2 * t_of(dur)))
    x = saw(f, 30)
    f1 = curve([(0, 320), (0.45, 720), (1, 300)], n)
    f2 = curve([(0, 800), (0.45, 1150), (1, 760)], n)
    y = formants(x, f1, f2, curve([(0, 2300), (1, 2500)], n))
    y *= 1 + 0.35 * np.sin(2 * np.pi * (31 + 6 * v) * t_of(dur))      # batido "alienígena"
    y += 0.25 * lowpass(osc(f * 0.5), 300)                                # cuerpo grave
    y *= env(n, 0.12, 0.35, 1.2)
    return norm(room(y, 0.2))


def grazer_hurt(v):
    dur = 0.42
    n = len(t_of(dur))
    f = curve([(0, 230 + 20 * v), (1, 150)], n)
    x = saw(f, 25) * (1 + 0.6 * np.sign(np.sin(2 * np.pi * 34 * t_of(dur))))
    y = formants(x, curve([(0, 700), (1, 500)], n), curve([(0, 1300), (1, 1000)], n))
    return norm(room(y * env(n, 0.03, 0.4), 0.15))


def grazer_death(v):
    dur = 1.6
    n = len(t_of(dur))
    f = curve([(0, 180), (0.3, 150), (1, 55)], n) * (1 + 0.03 * np.sin(2 * np.pi * 7 * t_of(dur)))
    x = saw(f, 30)
    y = formants(x, curve([(0, 650), (1, 300)], n), curve([(0, 1100), (1, 700)], n))
    y *= 1 + 0.5 * np.sin(2 * np.pi * curve([(0, 14), (1, 4)], n) * t_of(dur))
    return norm(room(y * env(n, 0.05, 0.6, 1.3), 0.25))


def hopper_ambient(v):
    parts = []
    k = [3, 4, 2][v]
    for i in range(k):
        dur = 0.07 + 0.03 * RNG.random()
        n = len(t_of(dur))
        base = 2400 + 600 * RNG.random()
        f = curve([(0, base), (0.5, base * 1.25), (1, base * 0.95)], n)
        x = osc(f, (1, 0.3, 0.1)) * (0.6 + 0.4 * np.sin(2 * np.pi * 70 * t_of(dur)))
        parts.append(x * env(n, 0.1, 0.4))
        parts.append(np.zeros(int(SR * (0.04 + 0.03 * RNG.random()))))
    return norm(room(np.concatenate(parts), 0.12), 0.6)


def hopper_hurt(v):
    dur = 0.18
    n = len(t_of(dur))
    x = osc(curve([(0, 1600 + 200 * v), (0.4, 3100), (1, 2400)], n), (1, 0.4, 0.2)) + 0.15 * highpass(noise(n), 3000)
    return norm(x * env(n, 0.05, 0.4), 0.7)


def hopper_death(v):
    dur = 0.6
    n = len(t_of(dur))
    x = osc(curve([(0, 2600), (1, 700)], n), (1, 0.35, 0.15)) * (0.7 + 0.3 * np.sin(2 * np.pi * 22 * t_of(dur)))
    return norm(room(x * env(n, 0.03, 0.5), 0.15), 0.7)


def hopper_hop(v):
    dur = 0.1
    n = len(t_of(dur))
    x = osc(curve([(0, 210 + 30 * v), (1, 95)], n)) * env(n, 0.02, 0.8, 1.5)
    x += 0.08 * bandpass(noise(n), 1500, 4000) * env(n, 0.01, 0.95, 3)
    return norm(x, 0.5)


def glider_ambient(v):
    notes = [[(1300, 2300), (2100, 1500)], [(1700, 2600), (2500, 2600), (2600, 1800)], [(1500, 1900), (2400, 1300)]][v]
    parts = []
    for a, b in notes:
        dur = 0.22 + 0.06 * RNG.random()
        n = len(t_of(dur))
        f = curve([(0, a), (1, b)], n) * (1 + 0.012 * np.sin(2 * np.pi * 28 * t_of(dur)))
        x = osc(f, (1, 0.12)) + 0.12 * bandpass(noise(n), 1200, 4500)
        parts.append(x * env(n, 0.15, 0.35))
        parts.append(np.zeros(int(SR * 0.06)))
    return norm(room(np.concatenate(parts), 0.22), 0.65)


def glider_hurt(v):
    dur = 0.28
    n = len(t_of(dur))
    mod = np.sin(2 * np.pi * 180 * t_of(dur)) * 900
    x = osc(curve([(0, 2000 + 200 * v), (1, 2600)], n) + mod, (1, 0.5, 0.3))
    return norm(room(x * env(n, 0.02, 0.4), 0.1), 0.65)


def glider_death(v):
    dur = 1.1
    n = len(t_of(dur))
    x = osc(curve([(0, 2200), (1, 380)], n) * (1 + 0.02 * np.sin(2 * np.pi * 9 * t_of(dur))), (1, 0.2))
    x += 0.1 * bandpass(noise(n), 800, 3000)
    return norm(room(x * env(n, 0.03, 0.5), 0.2), 0.65)


def glider_glide(v):
    dur = 0.7
    n = len(t_of(dur))
    nz = noise(n)
    fc = curve([(0, 350), (0.35, 1500), (1, 300)], n)
    y = np.zeros(n)
    for i in range(0, n, 441):   # banda que sube y baja: "fuuush"
        lo, hi = fc[i] * 0.6, fc[i] * 1.6
        y[i:i + 441] = bandpass(nz[max(0, i - 2000):i + 441], lo, hi)[-len(nz[i:i + 441]):]
    return norm(y * env(n, 0.3, 0.6, 1.2), 0.5)


def lurker_ambient(v):
    dur = [1.8, 1.4, 2.0][v]
    n = len(t_of(dur))
    f = curve([(0, 58), (0.5, 72 + 6 * v), (1, 52)], n)
    growl = saw(f, 40) * (1 + 0.7 * np.abs(np.sin(2 * np.pi * (11 + 3 * v) * t_of(dur))))
    y = formants(growl, curve([(0, 380), (1, 300)], n), curve([(0, 900), (1, 750)], n))
    bub = np.zeros(n)
    for _ in range(14 + 4 * v):   # burbujas
        s = int(RNG.random() * (n - SR * 0.06))
        d = int(SR * (0.025 + 0.03 * RNG.random()))
        fb = 250 + 450 * RNG.random()
        bub[s:s + d] += osc(np.linspace(fb, fb * 1.8, d)) * env(d, 0.05, 0.7)
    y = 0.8 * y / (np.max(np.abs(y)) + 1e-9) + 0.35 * bub
    return norm(room(y * env(n, 0.15, 0.3), 0.3), 0.9)


def lurker_hurt(v):
    dur = 0.45
    n = len(t_of(dur))
    hiss = bandpass(noise(n), 2500 + 300 * v, 7000) * env(n, 0.02, 0.5)
    growl = saw(curve([(0, 120), (1, 80)], n), 30) * env(n, 0.02, 0.6)
    return norm(room(0.6 * hiss / np.max(np.abs(hiss)) + 0.6 * growl / np.max(np.abs(growl)), 0.15), 0.85)


def lurker_death(v):
    dur = 2.0
    n = len(t_of(dur))
    f = curve([(0, 110), (0.3, 80), (1, 35)], n)
    x = saw(f, 40) * (1 + 0.8 * np.abs(np.sin(2 * np.pi * curve([(0, 13), (1, 3)], n) * t_of(dur))))
    y = formants(x, curve([(0, 500), (1, 250)], n), curve([(0, 1000), (1, 600)], n))
    y += 0.3 * np.max(np.abs(y)) * bandpass(noise(n), 300, 900) * env(n, 0.3, 0.3)
    return norm(room(y * env(n, 0.03, 0.5), 0.3), 0.9)


def lurker_attack(v):
    dur = 0.38
    n = len(t_of(dur))
    snarl = bandpass(noise(n), 900, 4000) * (1 + np.sin(2 * np.pi * 45 * t_of(dur))) * env(n, 0.02, 0.5)
    snap = np.zeros(n)
    k = int(SR * 0.22)
    snap[k:k + 300] = noise(300) * np.exp(-np.linspace(0, 8, 300))
    return norm(snarl / np.max(np.abs(snarl)) + 1.4 * snap, 0.85)


def croaker_croak(v):
    dur = [0.65, 0.5, 0.8][v]
    n = len(t_of(dur))
    rate = [20, 26, 16][v]
    f = curve([(0, 130 + 10 * v), (0.75, 150), (1, 240)], n)   # sube al final: "brrrr-up"
    x = saw(f, 30)
    pulses = (np.sin(2 * np.pi * rate * t_of(dur)) > -0.2).astype(float)
    pulses = lowpass(pulses, 200)
    y = formants(x * pulses, curve([(0, 420), (1, 520)], n), curve([(0, 1200), (1, 1500)], n))
    return norm(room(y * env(n, 0.04, 0.2), 0.2), 0.8)


def croaker_hurt(v):
    dur = 0.22
    n = len(t_of(dur))
    x = saw(curve([(0, 300 + 30 * v), (1, 420)], n), 20)
    y = formants(x, np.full(n, 600.0), np.full(n, 1500.0))
    return norm(y * env(n, 0.03, 0.4), 0.75)


def croaker_death(v):
    dur = 0.9
    n = len(t_of(dur))
    x = saw(curve([(0, 220), (1, 70)], n), 25) * (np.sin(2 * np.pi * curve([(0, 22), (1, 8)], n) * t_of(dur)) > -0.3)
    y = formants(lowpass(x.astype(float), 3000), curve([(0, 500), (1, 300)], n), curve([(0, 1300), (1, 900)], n))
    return norm(room(y * env(n, 0.03, 0.5), 0.2), 0.8)


def runner_ambient(v):
    parts = []
    for i in range([4, 3, 5][v]):
        dur = 0.07 + 0.04 * RNG.random()
        n = len(t_of(dur))
        a = 850 + 250 * RNG.random()
        x = saw(curve([(0, a), (0.6, a * 1.5), (1, a * 1.2)], n), 12)
        y = formants(x, np.full(n, 900.0), np.full(n, 2100.0)) * env(n, 0.1, 0.5)
        parts += [y, np.zeros(int(SR * (0.05 + 0.04 * RNG.random())))]
    return norm(room(np.concatenate(parts), 0.18), 0.7)


def runner_growl(v):
    dur = [1.3, 1.0, 1.5][v]
    n = len(t_of(dur))
    f = curve([(0, 75), (0.5, 90 + 5 * v), (1, 70)], n)
    x = saw(f, 35)
    clicks = (np.sin(2 * np.pi * (34 + 6 * v) * t_of(dur)) > 0.7).astype(float)
    clicks = highpass(clicks, 1500) * 0.9
    y = formants(x, np.full(n, 600.0), np.full(n, 1300.0))
    y = y / (np.max(np.abs(y)) + 1e-9) * (0.7 + 0.3 * np.abs(np.sin(2 * np.pi * 6 * t_of(dur)))) + 0.35 * clicks
    return norm(room(y * env(n, 0.15, 0.3), 0.15), 0.8)


def runner_hurt(v):
    dur = 0.24
    n = len(t_of(dur))
    x = saw(curve([(0, 1150 + 80 * v), (1, 650)], n), 14)
    y = formants(x, np.full(n, 1000.0), np.full(n, 2300.0))
    return norm(room(y * env(n, 0.03, 0.5), 0.12), 0.8)


def runner_death(v):
    dur = 1.1
    n = len(t_of(dur))
    x = saw(curve([(0, 1000), (0.4, 1200), (1, 380)], n) * (1 + 0.03 * np.sin(2 * np.pi * 8 * t_of(dur))), 14)
    y = formants(x, curve([(0, 1000), (1, 600)], n), curve([(0, 2200), (1, 1500)], n))
    return norm(room(y * env(n, 0.05, 0.5), 0.2), 0.8)


def runner_attack(v):
    dur = 0.32
    n = len(t_of(dur))
    snarl = formants(saw(curve([(0, 150), (1, 110)], n), 30) * (1 + np.sin(2 * np.pi * 50 * t_of(dur))),
                     np.full(n, 700.0), np.full(n, 1600.0))
    snarl = snarl / (np.max(np.abs(snarl)) + 1e-9) * env(n, 0.02, 0.5)
    snap = np.zeros(n)
    k = int(SR * 0.18)
    snap[k:k + 250] = noise(250) * np.exp(-np.linspace(0, 9, 250))
    return norm(snarl + 1.3 * snap, 0.8)


def ironjaw_ambient(v):
    """Ironjaw: rechinar grave de placas y dientes bajo el agua, con un retumbo de fondo."""
    dur = [2.0, 1.6, 2.4][v]
    n = len(t_of(dur))
    rumble = lowpass(saw(curve([(0, 42), (0.5, 50 + 4 * v), (1, 38)], n), 20), 260)
    grind = np.zeros(n)
    for _ in range(18 + 6 * v):   # chasquidos de dientes / placas
        s0 = int(RNG.random() * (n - 2000))
        d = int(SR * (0.01 + 0.02 * RNG.random()))
        grind[s0:s0 + d] += bandpass(noise(d), 700, 2600) * np.exp(-np.linspace(0, 6, d))
    y = rumble / (np.max(np.abs(rumble)) + 1e-9) * 0.8 + 0.5 * grind / (np.max(np.abs(grind)) + 1e-9)
    y = lowpass(y, 3000)   # bajo el agua: apagado
    return norm(room(y * env(n, 0.2, 0.3), 0.35), 0.85)


def ironjaw_hurt(v):
    dur = 0.5
    n = len(t_of(dur))
    clank = np.zeros(n)
    clank[:int(SR * 0.25)] = sum(np.sin(2 * np.pi * f * t_of(0.25)) for f in (410 + 30 * v, 1130, 1870)) * np.exp(-np.linspace(0, 9, int(SR * 0.25)))
    growl = formants(saw(curve([(0, 90), (1, 70)], n), 30), np.full(n, 420.0), np.full(n, 900.0))
    growl = growl / (np.max(np.abs(growl)) + 1e-9) * env(n, 0.05, 0.5)
    return norm(room(0.7 * clank / np.max(np.abs(clank)) + 0.8 * growl, 0.2), 0.85)


def ironjaw_death(v):
    dur = 2.4
    n = len(t_of(dur))
    x = saw(curve([(0, 80), (0.4, 60), (1, 28)], n), 40) * (1 + 0.7 * np.abs(np.sin(2 * np.pi * curve([(0, 9), (1, 2)], n) * t_of(dur))))
    y = formants(x, curve([(0, 400), (1, 220)], n), curve([(0, 850), (1, 500)], n))
    y = lowpass(y, 2500)
    return norm(room(y * env(n, 0.03, 0.5), 0.4), 0.9)


def ironjaw_charge(v):
    """Rugido que sube al lanzarse a embestir, con el agua que se abre."""
    dur = 1.1
    n = len(t_of(dur))
    x = saw(curve([(0, 55), (0.7, 120 + 10 * v), (1, 100)], n), 40)
    y = formants(x, curve([(0, 350), (1, 700)], n), curve([(0, 800), (1, 1400)], n))
    y = y / (np.max(np.abs(y)) + 1e-9)
    rush = bandpass(noise(n), 300, 1800) * curve([(0, 0.0), (0.6, 0.6), (1, 0.2)], n)
    return norm(room(lowpass(y * env(n, 0.3, 0.2) + 0.5 * rush, 3500), 0.3), 0.9)


def ironjaw_bite(v):
    dur = 0.4
    n = len(t_of(dur))
    crunch = np.zeros(n)
    for k in range(5):   # crujido de muchos dientes
        s0 = int(SR * (0.02 + 0.03 * k + 0.01 * RNG.random()))
        d = int(SR * 0.03)
        crunch[s0:s0 + d] += bandpass(noise(d), 900, 5000) * np.exp(-np.linspace(0, 5, d))
    thud = osc(curve([(0, 120), (1, 60)], n)) * env(n, 0.01, 0.8, 2)
    return norm(room(crunch / np.max(np.abs(crunch)) + 0.7 * thud, 0.2), 0.9)


# nombre del evento → (función, variantes, subtítulo en inglés, en español)
SOUNDS = {
    "grazer.ambient": (grazer_ambient, 3, "Grazer lows", "Pastoreador muge"),
    "grazer.hurt": (grazer_hurt, 2, "Grazer hurts", "Pastoreador herido"),
    "grazer.death": (grazer_death, 1, "Grazer dies", "Pastoreador muere"),
    "hopper.ambient": (hopper_ambient, 3, "Hopper trills", "Saltarín trina"),
    "hopper.hurt": (hopper_hurt, 2, "Hopper squeaks", "Saltarín chilla"),
    "hopper.death": (hopper_death, 1, "Hopper dies", "Saltarín muere"),
    "hopper.hop": (hopper_hop, 2, "Hopper hops", "Saltarín salta"),
    "glider.ambient": (glider_ambient, 3, "Glider whistles", "Planeador silba"),
    "glider.hurt": (glider_hurt, 2, "Glider hurts", "Planeador herido"),
    "glider.death": (glider_death, 1, "Glider dies", "Planeador muere"),
    "glider.glide": (glider_glide, 2, "Glider glides", "Planeador planea"),
    "lurker.ambient": (lurker_ambient, 3, "Lurker gurgles", "Acechador gorgotea"),
    "lurker.hurt": (lurker_hurt, 2, "Lurker hisses", "Acechador sisea"),
    "lurker.death": (lurker_death, 1, "Lurker dies", "Acechador muere"),
    "lurker.attack": (lurker_attack, 2, "Lurker bites", "Acechador muerde"),
    "croaker.croak": (croaker_croak, 3, "Croaker croaks", "Croador croa"),
    "croaker.hurt": (croaker_hurt, 2, "Croaker hurts", "Croador herido"),
    "croaker.death": (croaker_death, 1, "Croaker dies", "Croador muere"),
    "runner.ambient": (runner_ambient, 3, "Runner yips", "Corredor gañe"),
    "runner.growl": (runner_growl, 3, "Runner growls", "Corredor gruñe"),
    "runner.hurt": (runner_hurt, 2, "Runner yelps", "Corredor aúlla"),
    "runner.death": (runner_death, 1, "Runner dies", "Corredor muere"),
    "runner.attack": (runner_attack, 2, "Runner snarls", "Corredor ataca"),
    "ironjaw.ambient": (ironjaw_ambient, 3, "Ironjaw grinds", "Ironjaw rechina"),
    "ironjaw.hurt": (ironjaw_hurt, 2, "Ironjaw hurts", "Ironjaw herido"),
    "ironjaw.death": (ironjaw_death, 1, "Ironjaw dies", "Ironjaw muere"),
    "ironjaw.charge": (ironjaw_charge, 2, "Ironjaw charges", "Ironjaw embiste"),
    "ironjaw.bite": (ironjaw_bite, 2, "Ironjaw bites", "Ironjaw muerde"),
}


def write_wav(path, x):
    x = np.clip(x, -1, 1)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((x * 32767).astype(np.int16).tobytes())


def build(out):
    os.makedirs(out, exist_ok=True)
    preview, offset, lines = [], 0.0, []
    tmp = os.path.join(out, "_tmp.wav")
    for name, (fn, count, en, es) in SOUNDS.items():
        mob, kind = name.split(".")
        os.makedirs(os.path.join(out, mob), exist_ok=True)
        for v in range(count):
            x = pad(fn(v), 0.0, 0.05)
            write_wav(tmp, x)
            subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", tmp, "-c:a", "libvorbis", "-q:a", "5",
                            os.path.join(out, mob, f"{kind}{v + 1}.ogg")], check=True)
            if v == 0 or kind in ("ambient", "croak", "growl"):
                lines.append(f"{offset:5.1f} s  {es} ({v + 1})")
                preview += [x, np.zeros(int(SR * 0.6))]
                offset += len(x) / SR + 0.6
    os.remove(tmp)
    write_wav(tmp, np.concatenate(preview))
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", tmp, "-c:a", "libmp3lame", "-q:a", "3",
                    os.path.join(out, "preview.mp3")], check=True)
    os.remove(tmp)
    print("\n".join(lines))


if __name__ == "__main__":
    build(sys.argv[1])
