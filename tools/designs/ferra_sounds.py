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


# ------------------------------------------------------------------ voz orgánica
# (1.0.3, el usuario: "que suenen más monstruo, se sienten extraños, como algo no natural"). En vez de ondas de sierra
# limpias, una garganta: pulsos irregulares (jitter y shimmer), duplicación de periodo (ronquera), aire, varias voces
# un poco desafinadas (bicho grande), filtro de vocal, saturación y una cola de cueva.

def glottal(f0, jitter=0.07, shimmer=0.3, fry=0.0, sub=0.0):
    """Tren de pulsos de garganta que sigue la curva f0. fry: pulsos sueltos y graves; sub: alterna pulsos fuertes y
    flojos (subarmónico, voz rota)."""
    n = len(f0)
    out = np.zeros(n)
    i, k = 0, 0
    while i < n:
        f = max(18.0, f0[i] * (1 + jitter * RNG.standard_normal() * 0.5))
        if fry > 0 and RNG.random() < fry:
            f *= 0.5 + 0.3 * RNG.random()
        L = int(SR / f)
        pl = min(L, n - i)
        tt = np.arange(pl) / max(L, 1)
        pulse = np.where(tt < 0.35, np.sin(np.pi * tt / 0.35) ** 2, 0.0) - 0.35 * np.exp(-((tt - 0.4) * 9) ** 2)
        a = max(0.1, 1 + shimmer * RNG.standard_normal() * 0.5) * (1 - sub if k % 2 else 1 + sub)
        out[i:i + pl] += a * pulse
        i += max(L, 1)
        k += 1
    return out


def creature(f0, f1, f2, f3=None, voices=3, detune=0.03, jitter=0.07, shimmer=0.3, fry=0.0, sub=0.0, breath=0.25,
             drive=2.5, lp=5000, bw=(120, 180, 260)):
    """Voz de bicho: varias gargantas desafinadas + aire, por un filtro de vocal y saturadas."""
    n = len(f0)
    src = np.zeros(n)
    for v in range(voices):
        d = 1 + detune * (v - (voices - 1) / 2)
        src += glottal(f0 * d, jitter, shimmer, fry, sub)
    src = unit(src) + breath * unit(bandpass(noise(n), 400, 5000))
    y = formants(src, f1, f2, f3, bw=bw)
    y = np.tanh(drive * unit(y)) / np.tanh(drive)
    return unit(lowpass(y, lp))


def cave(x, amount=0.35, length=0.8):
    return room(x, amount, length)


def subbass(n, f_start, f_end, decay=3.0):
    return osc(curve([(0, f_start), (1, f_end)], n)) * np.exp(-np.linspace(0, decay, n))


# ------------------------------------------------------------------ gusano de óxido

def worm_dig(v):
    dur = [0.9, 0.8, 1.0][v]
    n = len(t_of(dur))
    rumble = unit(lowpass(noise(n), 90 + 15 * v, 4)) * (1 + 0.4 * np.sin(2 * np.pi * 7 * t_of(dur)))
    y = (0.9 * rumble + 0.45 * gravel(n, 30 + 8 * v, 300, 2500)) * env(n, 0.15, 0.4)
    return norm(room(y, 0.15), 0.8)


def worm_emerge(v):
    """Rugido grave y ronco de algo enorme que sale de la tierra, con la tierra que revienta."""
    dur = 1.9
    n = len(t_of(dur))
    boom = unit(lowpass(noise(n), 110, 4)) * np.exp(-np.linspace(0, 4, n))
    burst = gravel(n, 160, 400, 6000) * curve([(0, 0.0), (0.04, 1.0), (0.5, 0.4), (1, 0.0)], n)
    f0 = curve([(0, 48 + 6 * v), (0.15, 85), (0.55, 78), (1, 40)], n)
    roar = creature(f0, curve([(0, 300), (0.2, 520), (1, 330)], n), curve([(0, 700), (0.2, 1100), (1, 650)], n),
                    curve([(0, 1800), (1, 1500)], n), voices=4, detune=0.045, jitter=0.12, shimmer=0.45, fry=0.15, sub=0.45,
                    breath=0.5, drive=3.5, lp=3800)
    roar *= curve([(0, 0), (0.06, 0.7), (0.2, 1.0), (0.8, 0.8), (1, 0)], n)
    return norm(cave(0.8 * boom + 0.45 * burst + 0.9 * roar + 0.5 * subbass(n, 55, 30, 2.5), 0.4, 0.9), 0.95)


def worm_ambient(v):
    """Gruñido profundo y húmedo, como un estómago enorme, con los dientes rechinando."""
    dur = [1.6, 2.0, 1.4][v]
    n = len(t_of(dur))
    f0 = curve([(0, 38 + 5 * v), (0.4, 52), (1, 36)], n)
    growl = creature(f0, curve([(0, 280), (1, 360)], n), curve([(0, 620), (1, 760)], n), voices=3, detune=0.05,
                     jitter=0.15, shimmer=0.5, fry=0.3, sub=0.5, breath=0.35, drive=3.0, lp=2200)
    wet = np.zeros(n)
    for _ in range(8 + 3 * v):   # gorgoteo grave
        s0 = int(RNG.random() * (n - 5000))
        d = int(SR * (0.05 + 0.06 * RNG.random()))
        wet[s0:s0 + d] += osc(curve([(0, 90 + 80 * RNG.random()), (1, 200 + 200 * RNG.random())], d)) * np.hanning(d)
    teeth = gravel(n, 10 + 3 * v, 1200, 5000)
    y = (0.9 * growl + 0.3 * unit(wet) + 0.2 * teeth) * env(n, 0.2, 0.35)
    return norm(cave(y, 0.3), 0.85)


def worm_bite(v):
    dur = 0.6
    n = len(t_of(dur))
    crunch = gravel(n, 25, 800, 6000) * np.exp(-np.linspace(0, 5, n))
    snap = osc(curve([(0, 150 - 20 * v), (1, 60)], n)) * env(n, 0.005, 0.9, 2.5)
    snarl = creature(curve([(0, 90), (1, 60)], n), np.full(n, 450.0), np.full(n, 1000.0), voices=3, jitter=0.15,
                     sub=0.4, breath=0.5, drive=3.5, lp=3000) * env(n, 0.02, 0.6)
    return norm(room(crunch + 0.6 * snap + 0.6 * snarl, 0.2), 0.92)


def worm_hurt(v):
    dur = 0.7
    n = len(t_of(dur))
    clank = sum(np.sin(2 * np.pi * f * t_of(dur)) for f in (300 + 40 * v, 820)) * np.exp(-np.linspace(0, 12, n))
    roar = creature(curve([(0, 110 + 15 * v), (0.3, 130), (1, 70)], n), curve([(0, 600), (1, 420)], n),
                    curve([(0, 1300), (1, 900)], n), voices=3, jitter=0.12, shimmer=0.5, sub=0.5, breath=0.45, drive=4.0, lp=3500)
    return norm(cave(0.35 * unit(clank) + roar * env(n, 0.02, 0.55), 0.25, 0.6), 0.92)


def worm_death(v):
    dur = 3.0
    n = len(t_of(dur))
    f0 = curve([(0, 120), (0.3, 80), (1, 22)], n)
    roar = creature(f0, curve([(0, 650), (1, 260)], n), curve([(0, 1300), (1, 550)], n), voices=4, detune=0.05,
                    jitter=0.2, shimmer=0.6, fry=0.4, sub=0.6, breath=0.5, drive=3.5, lp=3000)
    rattle = gravel(n, 70, 600, 3500) * curve([(0, 0.1), (0.7, 0.6), (1, 0)], n)
    return norm(cave((roar + 0.4 * rattle) * env(n, 0.03, 0.5) + 0.4 * subbass(n, 50, 25, 2.0), 0.4, 1.0), 0.92)


def worm_dive(v):
    dur = 1.1
    n = len(t_of(dur))
    slide = gravel(n, 90, 300, 3000) * curve([(0, 1), (1, 0)], n)
    whoosh = unit(bandpass(noise(n), 150, 900)) * curve([(0, 0.2), (0.3, 1), (1, 0)], n)
    grunt = creature(curve([(0, 60), (1, 40)], n), np.full(n, 320.0), np.full(n, 700.0), voices=3, jitter=0.15, sub=0.5,
                     breath=0.4, drive=3.0, lp=2000) * curve([(0, 0.8), (0.4, 0.5), (1, 0)], n)
    return norm(room(0.7 * slide + 0.5 * whoosh + 0.6 * grunt, 0.25), 0.85)


# ------------------------------------------------------------------ rastreador ciego

def clicks(n, rate, freq=2600, q=0.004, jitter=0.3):
    """Tren de chasquidos (sonar): chasquidos secos de garganta, no electrónicos."""
    out = np.zeros(n)
    t = 0.0
    while t < n / SR - 0.02:
        s0 = int(t * SR)
        d = int(SR * q * (0.7 + 0.6 * RNG.random()))
        out[s0:s0 + d] += bandpass(noise(d), freq * 0.5, freq * 1.4) * np.exp(-np.linspace(0, 7, d)) * (0.5 + RNG.random())
        t += 1.0 / rate * (1 + jitter * (RNG.random() - 0.5))
    return unit(out)


def stalker_ambient(v):
    """Respiración ronca de animal grande con chasquidos de garganta (busca con el sonido)."""
    dur = [1.6, 2.0, 1.3][v]
    n = len(t_of(dur))
    r = curve([(0, 6), (0.5, 16 + 4 * v), (1, 8)], n)
    c = clicks(n, float(np.mean(r)), 1800 + 200 * v, 0.006)
    rasp = creature(curve([(0, 70 + 8 * v), (0.5, 85), (1, 65)], n), np.full(n, 500.0), np.full(n, 1200.0), voices=2,
                    jitter=0.2, shimmer=0.5, fry=0.5, sub=0.5, breath=1.2, drive=2.0, lp=3000)
    rasp *= 0.5 + 0.5 * np.sin(np.pi * np.linspace(0, 2 + v, n)) ** 2       # inspira / espira
    return norm(cave((0.6 * c + 0.7 * rasp) * env(n, 0.1, 0.25), 0.25, 0.6), 0.75)


def stalker_listen(v):
    dur = 0.9
    n = len(t_of(dur))
    c = clicks(n, 24 + 6 * v, 2200, 0.005, 0.2) * curve([(0, 0.3), (0.5, 1), (1, 0.4)], n)
    sniff = unit(bandpass(noise(n), 800, 4000)) * (np.sin(np.pi * np.linspace(0, 6, n)) ** 8)
    return norm(room(c + 0.5 * sniff, 0.15), 0.7)


def stalker_scream(v):
    """Grito de alerta de depredador: arranca en un gruñido y se rompe en un alarido ronco y desgarrado."""
    dur = [1.7, 1.5][v]
    n = len(t_of(dur))
    f0 = curve([(0, 110), (0.15, 380 + 40 * v), (0.6, 340), (1, 180)], n)
    scream = creature(f0, curve([(0, 500), (0.2, 950), (1, 700)], n), curve([(0, 1100), (0.2, 2100), (1, 1600)], n),
                      curve([(0, 2600), (1, 3000)], n), voices=4, detune=0.06, jitter=0.18, shimmer=0.6, sub=0.55,
                      breath=0.9, drive=5.0, lp=6500, bw=(160, 260, 380))
    low = creature(f0 * 0.5, np.full(n, 420.0), np.full(n, 950.0), voices=2, jitter=0.2, sub=0.6, breath=0.5, drive=3.0, lp=2500)
    y = (scream + 0.45 * low) * env(n, 0.03, 0.35)
    return norm(cave(y, 0.4, 0.9), 0.95)


def stalker_attack(v):
    dur = 0.5
    n = len(t_of(dur))
    snap = unit(bandpass(noise(n), 1200, 7000)) * env(n, 0.002, 0.95, 4)
    snarl = creature(curve([(0, 150), (0.3, 190), (1, 120)], n), curve([(0, 600), (1, 520)], n), curve([(0, 1500), (1, 1200)], n),
                     voices=3, jitter=0.18, shimmer=0.5, sub=0.5, breath=0.7, drive=4.5, lp=5000) * env(n, 0.02, 0.5)
    return norm(room(0.7 * snap + snarl, 0.2), 0.92)


def stalker_hurt(v):
    dur = 0.6
    n = len(t_of(dur))
    yelp = creature(curve([(0, 300 + 40 * v), (0.25, 360), (1, 160)], n), curve([(0, 850), (1, 600)], n),
                    curve([(0, 1900), (1, 1300)], n), voices=3, jitter=0.15, shimmer=0.5, sub=0.45, breath=0.6, drive=4.0, lp=5500)
    return norm(room(yelp * env(n, 0.02, 0.6), 0.25), 0.9)


def stalker_death(v):
    dur = 2.4
    n = len(t_of(dur))
    f0 = curve([(0, 330), (0.25, 240), (1, 45)], n)
    y = creature(f0, curve([(0, 850), (1, 330)], n), curve([(0, 2000), (1, 700)], n), voices=3, jitter=0.25, shimmer=0.6,
                 fry=0.5, sub=0.6, breath=0.8, drive=4.0, lp=4500)
    rattle = clicks(n, 10, 1500, 0.008) * curve([(0, 0.0), (0.6, 0.7), (1, 0.0)], n)   # estertor
    return norm(cave((y + 0.35 * rattle) * env(n, 0.02, 0.45), 0.35, 0.9), 0.92)


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
