"""Texturas del amortiguador inercial (0.7.29b): netherita y estrella del Nether, con texturas conectadas de Create.

Uso: python3 inertial_dampener_draw.py <brass_casing_connected.png de Create> <carpeta_salida>
La hoja conectada usa la misma disposición OMNIDIRECTIONAL de Create (8x8 casillas, 47 usadas): se lee de la hoja de
la carcasa de latón qué bordes y esquinas interiores lleva cada casilla y se dibuja la nuestra con esos datos.
"""
import math, os, random, sys
from PIL import Image

H = lambda s: tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
REF, OUT = sys.argv[1], sys.argv[2]

# netherita (bloque y lingote vanilla)
N_DARK, N_D2, N_MID1, N_MID2, N_MID3, N_MID4, N_LIGHT, N_HI = '241e1f', '31292a', '3c3232', '3b393b', '434043', '484548', '5a575a', '737173'
# estrella del Nether
S_DIM, S_TEAL, S_MID, S_PALE, S_WHITE, S_CORE = '556b6b', '649090', '88a4a4', 'b9c9c9', 'dae2e2', 'fdffa8'


def inner_plate():
    """Placa de netherita que se repite sin costuras (cada casilla es igual: no hay bordes propios)."""
    rnd = random.Random(7)
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = H(rnd.choice([N_MID1, N_MID2, N_MID3, N_MID3, N_MID4, N_MID2]))
    # vetas de la estrella: diagonales que pasan por el centro y las esquinas -> rejilla de rombos en una cara grande
    for i in range(16):
        for (x, y) in ((i, i), (i, 15 - i)):
            d = abs(x - 7.5)
            if d > 1.5:
                px[x, y] = H(S_DIM if (x + y) % 3 else N_LIGHT)
    return im


def star(px, cx, cy, size):
    """Destello de estrella del Nether centrado entre (cx, cy) y (cx+1, cy+1)."""
    for d in range(size + 1):
        col = S_CORE if d == 0 else (S_WHITE if d == 1 else (S_PALE if d < size else S_TEAL))
        for (x, y) in [(cx - d, cy), (cx + 1 + d, cy), (cx - d, cy + 1), (cx + 1 + d, cy + 1),
                       (cx, cy - d), (cx, cy + 1 + d), (cx + 1, cy - d), (cx + 1, cy + 1 + d)]:
            if 0 <= x < 16 and 0 <= y < 16: px[x, y] = H(col)
    for (x, y) in [(cx - 1, cy - 1), (cx + 2, cy - 1), (cx - 1, cy + 2), (cx + 2, cy + 2)]:
        px[x, y] = H(S_MID)


def decorate_casing(px):
    """Solo la equis de vetas, también por el centro (sin destello: petición del usuario)."""
    for i in range(6, 10):
        for (x, y) in ((i, i), (i, 15 - i)):
            px[x, y] = H(S_DIM if (x + y) % 3 else N_LIGHT)


def decorate_port(px):
    c = 7.5
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - c, y - c)
            if r < 2.4: px[x, y] = H(S_WHITE if (x + y) % 3 == 0 else (S_PALE if r < 1.5 else S_MID))
            elif r < 3.2: px[x, y] = H(S_TEAL)
            elif r < 4.6: px[x, y] = H(N_HI if (x + y) < 15 else N_LIGHT)
            elif r < 5.2: px[x, y] = H(N_DARK)
    for (x, y) in [(4, 4), (11, 4), (4, 11), (11, 11)]:
        px[x, y] = H(N_HI)


def decorate_redstone(px):
    """Entrada de redstone: anillo de netherita con un cristal de redstone y cuatro pistas hacia los lados."""
    c = 7.5
    R_DARK, R_MID, R_HI = '5a0a00', 'aa1a05', 'ff4a26'
    for i in range(3, 13):
        if i in (6, 7, 8, 9): continue
        px[i, 7] = H(R_DARK); px[i, 8] = H(R_DARK)
        px[7, i] = H(R_DARK); px[8, i] = H(R_DARK)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - c, y - c)
            if r < 1.6: px[x, y] = H(R_HI if (x + y) % 2 else 'ffb199')
            elif r < 2.6: px[x, y] = H(R_MID)
            elif r < 3.3: px[x, y] = H(N_DARK)
            elif r < 4.0: px[x, y] = H(N_HI if (x + y) < 15 else N_LIGHT)


def decorate_drive_end(px):
    c = 7.5
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - c, y - c)
            if r < 3.2: px[x, y] = H('0b0909')
            elif r < 4.3: px[x, y] = H(N_HI if (x + y) < 15 else N_LIGHT)
            elif r < 4.9: px[x, y] = H(N_DARK)


def decorate_drive_side(px):
    for y in range(3, 13):
        for x in (6, 7, 8, 9): px[x, y] = H(N_DARK)
    for y in range(4, 12):
        px[7, y] = H(S_PALE if y in (4, 11) else (S_CORE if y in (7, 8) else S_WHITE))
        px[8, y] = H(S_MID if y in (4, 11) else S_PALE)
    for y in (3, 12):
        for x in (6, 7, 8, 9): px[x, y] = H(N_HI)


def frame(px, T, B, L, R, nubs):
    """Borde de netherita solo en los lados que no se conectan; esquinas interiores donde hace falta."""
    for i in range(16):
        if T: px[i, 0] = H(N_DARK); px[i, 1] = H(N_HI)
        if B: px[i, 15] = H(N_DARK); px[i, 14] = H(N_MID1)
        if L: px[0, i] = H(N_DARK); px[1, i] = H(N_HI) if not (B and i >= 14) else px[1, i]
        if R: px[15, i] = H(N_DARK); px[14, i] = H(N_MID1) if not (T and i <= 1) else px[14, i]
    if T and L: px[1, 1] = H(N_HI)
    if B and R: px[14, 14] = H(N_MID1)
    if T and R: px[14, 1] = H(N_LIGHT)
    if B and L: px[1, 14] = H(N_LIGHT)
    for (fx, fy), on in zip([(0, 0), (15, 0), (0, 15), (15, 15)], nubs):
        if not on: continue
        sx = 1 if fx == 0 else -1
        sy = 1 if fy == 0 else -1
        px[fx, fy] = H(N_DARK); px[fx + sx, fy] = H(N_DARK); px[fx, fy + sy] = H(N_DARK)
        px[fx + sx, fy + sy] = H(N_HI)


def tile(deco, T, B, L, R, nubs=(False,) * 4):
    im = inner_plate()
    px = im.load()
    deco(px)
    frame(px, T, B, L, R, nubs)
    return im


def layout():
    """Lee la hoja conectada de Create: para cada casilla, qué bordes (T B L R) y esquinas interiores lleva."""
    ref = Image.open(REF).convert('RGBA')
    p = ref.load()
    br = lambda x, y: p[x, y][3] > 0 and p[x, y][0] > 180 and p[x, y][1] > 130
    out = {}
    for ty in range(8):
        for tx in range(8):
            X, Y = tx * 16, ty * 16
            if p[X + 8, Y + 8][3] == 0: continue
            T, B, L, R = br(X + 8, Y + 1), br(X + 8, Y + 14), br(X + 1, Y + 8), br(X + 14, Y + 8)
            nub = lambda cx, cy: any(br(X + cx + i, Y + cy + j) for i in range(3) for j in range(3))
            nubs = (not T and not L and nub(0, 0), not T and not R and nub(13, 0),
                    not B and not L and nub(0, 13), not B and not R and nub(13, 13))
            out[(tx, ty)] = (T, B, L, R, nubs)
    return out


def sheet(deco, lay):
    im = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    for (tx, ty), (T, B, L, R, nubs) in lay.items():
        im.paste(tile(deco, T, B, L, R, nubs), (tx * 16, ty * 16))
    return im


def core():
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = H(N_D2 if (x + y) % 5 else N_DARK)
    frame(px, True, True, True, True, (False,) * 4)
    # estrella del Nether grande
    c = 7.5
    for y in range(2, 14):
        for x in range(2, 14):
            dx, dy = abs(x - c), abs(y - c)
            arm = min(dx, dy)
            reach = max(dx, dy)
            if arm < 1 and reach < 6: col = S_CORE if reach < 1.5 else (S_WHITE if reach < 3.5 else S_PALE)
            elif arm < 2 and reach < 4: col = S_PALE if reach < 3 else S_MID
            elif dx + dy < 4.5: col = S_MID
            elif dx + dy < 5.5: col = S_DIM
            else: continue
            px[x, y] = H(col)
    return im


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    lay = layout()
    for name, deco in [('inertial_dampener_casing', decorate_casing), ('inertial_dampener_coolant_port', decorate_port), ('inertial_dampener_redstone_port', decorate_redstone),
                       ('inertial_dampener_drive_end', decorate_drive_end), ('inertial_dampener_drive_side', decorate_drive_side)]:
        tile(deco, True, True, True, True).save(os.path.join(OUT, name + '.png'))
        sheet(deco, lay).save(os.path.join(OUT, name + '_connected.png'))
    core().save(os.path.join(OUT, 'inertial_dampener_core.png'))
