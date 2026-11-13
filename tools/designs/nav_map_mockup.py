"""Maqueta de la pantalla del mapa de la consola de navegación y del efecto de salto (propuesta, aún no en el mod).
Uso: python3 nav_map_mockup.py <mapa.png> <efecto.png>"""
import math, random, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FR = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
F = lambda n, b=False: ImageFont.truetype(FB if b else FR, n)
W, H = 1280, 720


def starfield(w, h, seed=3, n=520):
    img = Image.new('RGBA', (w, h), (6, 8, 18, 255))
    d = ImageDraw.Draw(img)
    rnd = random.Random(seed)
    for _ in range(n):
        x, y = rnd.randrange(w), rnd.randrange(h)
        c = rnd.randint(90, 230)
        d.point((x, y), fill=(c, c, min(255, c + 25), 255))
        if rnd.random() < 0.06: d.rectangle([x, y, x + 1, y + 1], fill=(c, c, 255, 255))
    return img


def glow(img, x, y, r, col, a=110):
    g = Image.new('RGBA', img.size, (0, 0, 0, 0))
    ImageDraw.Draw(g).ellipse([x - r, y - r, x + r, y + r], fill=col + (a,))
    img.alpha_composite(g.filter(ImageFilter.GaussianBlur(r / 2)))


def planet(d, x, y, r, base, light, band=None):
    d.ellipse([x - r, y - r, x + r, y + r], fill=base)
    if band:
        for k in range(-2, 3):
            yy = y + k * r * 0.35
            hw = math.sqrt(max(0, r * r - (yy - y) ** 2))
            d.line([(x - hw, yy), (x + hw, yy)], fill=band, width=max(1, int(r * 0.15)))
    d.ellipse([x - r * 0.7, y - r * 0.75, x - r * 0.05, y - r * 0.1], fill=light)
    d.arc([x - r, y - r, x + r, y + r], -45, 135, fill=(0, 0, 0, 120), width=max(2, int(r * 0.25)))


def panel(img, box, title=None, border=(95, 214, 230)):
    ov = Image.new('RGBA', img.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    od.rectangle(box, fill=(10, 16, 30, 225))
    img.alpha_composite(ov)
    d = ImageDraw.Draw(img)
    d.rectangle(box, outline=border + (255,), width=2)
    x0, y0, x1, y1 = box
    for (cx, cy) in [(x0, y0), (x1, y0), (x0, y1), (x1, y1)]:
        d.rectangle([cx - 3, cy - 3, cx + 3, cy + 3], fill=(214, 168, 92, 255))
    return d


def mapa(out):
    img = starfield(W, H)
    d = ImageDraw.Draw(img)
    cx, cy = 560, 380
    sq = 0.42                                          # inclinación de la vista (órbitas en elipse)
    orbits = [('Venus', 105, 230, (217, 180, 106), (241, 215, 154), 9, None),
              ('Tierra', 160, 140, (63, 127, 208), (127, 192, 106), 10, None),
              ('Marte', 225, 330, (196, 80, 46), (224, 122, 80), 8, None),
              ('Gigante gaseoso', 330, 62, (192, 138, 82), (226, 185, 124), 22, (150, 98, 60)),
              ('Mundo helado', 450, 205, (169, 216, 236), (234, 248, 255), 11, None)]
    for _, R, *_ in orbits:
        d.ellipse([cx - R, cy - R * sq, cx + R, cy + R * sq], outline=(60, 150, 170, 255), width=1)
    glow(img, cx, cy, 70, (255, 200, 80), 120)
    d = ImageDraw.Draw(img)
    planet(d, cx, cy, 30, (255, 207, 84, 255), (255, 243, 176, 255))
    d.text((cx, cy + 42), 'Sol', fill=(255, 225, 140, 255), font=F(14, True), anchor='mm')
    pos = {}
    for name, R, ang, base, light, r, band in orbits:
        a = math.radians(ang)
        x, y = cx + R * math.cos(a), cy + R * math.sin(a) * sq
        pos[name] = (x, y, r)
        planet(d, x, y, r, base + (255,), light + (255,), band and band + (255,))
        d.text((x, y + r + 12), name, fill=(225, 235, 245, 255), font=F(13, True), anchor='mm')
    # lunas
    ex, ey, _ = pos['Tierra']
    d.ellipse([ex - 22, ey - 22 * sq, ex + 22, ey + 22 * sq], outline=(60, 150, 170, 180))
    mx, my = ex + 22 * math.cos(1.0), ey + 22 * sq * math.sin(1.0)
    planet(d, mx, my, 4, (160, 160, 160, 255), (210, 210, 210, 255))
    d.text((mx + 20, my + 2), 'Luna', fill=(190, 200, 210, 255), font=F(11), anchor='mm')
    gx, gy, _ = pos['Gigante gaseoso']
    for (nm, R, ang, b, l) in [('Ignis', 38, 200, (216, 64, 26), (255, 176, 58)), ('Thalassa', 58, 20, (31, 79, 154), (63, 127, 208))]:
        d.ellipse([gx - R, gy - R * sq, gx + R, gy + R * sq], outline=(60, 150, 170, 180))
        a = math.radians(ang)
        px_, py_ = gx + R * math.cos(a), gy + R * sq * math.sin(a)
        pos[nm] = (px_, py_, 6)
        planet(d, px_, py_, 6, b + (255,), l + (255,))
        d.text((px_, py_ + 16), nm, fill=(225, 235, 245, 255), font=F(12, True), anchor='mm')
    # tu nave, junto a la Tierra
    sx, sy = ex - 34, ey - 18
    d.polygon([(sx, sy - 7), (sx + 6, sy + 5), (sx, sy + 2), (sx - 6, sy + 5)], fill=(95, 242, 230, 255))
    d.text((sx, sy - 16), 'Tú', fill=(95, 242, 230, 255), font=F(12, True), anchor='mm')
    # planeta bajo el ratón (Thalassa) resaltado + ventana de descripción
    tx, ty, _ = pos['Thalassa']
    d.ellipse([tx - 13, ty - 13, tx + 13, ty + 13], outline=(255, 255, 255, 255), width=2)
    d.polygon([(tx + 4, ty + 4), (tx + 4, ty + 20), (tx + 8, ty + 16), (tx + 12, ty + 23), (tx + 14, ty + 22), (tx + 10, ty + 15), (tx + 16, ty + 15)], fill=(255, 255, 255, 255), outline=(0, 0, 0, 255))
    bx, by = 905, 70
    d = panel(img, [bx, by, bx + 350, by + 300])
    d.text((bx + 14, by + 12), 'Thalassa', fill=(140, 200, 255, 255), font=F(20, True))
    d.text((bx + 14, by + 40), 'Luna del gigante gaseoso', fill=(150, 170, 190, 255), font=F(12))
    desc = ['Un océano sin fin bajo una capa de hielo.', 'Debajo, enormes redes de cuevas',
            'submarinas, frías y totalmente oscuras.']
    for i, l in enumerate(desc): d.text((bx + 14, by + 66 + i * 18), l, fill=(220, 228, 236, 255), font=F(13))
    rows = [('Gravedad', '2,0 m/s²'), ('Aire', 'solo sobre el hielo'), ('Temperatura', 'helada'),
            ('Peligros', 'frío, presión del agua'), ('Armadura', 'Sea Diver'), ('Recursos', 'hierro, titanio, diamante'),
            ('Distancia', '412 000 km')]
    for i, (k, v) in enumerate(rows):
        d.text((bx + 14, by + 132 + i * 21), k, fill=(150, 170, 190, 255), font=F(13))
        d.text((bx + 120, by + 132 + i * 21), v, fill=(240, 240, 240, 255), font=F(13, True))
    d.text((bx + 14, by + 280), 'Clic derecho en el planeta: opciones', fill=(95, 214, 230, 255), font=F(12))
    # menú del clic derecho sobre Marte
    mxp, myp, _ = pos['Marte']
    d.ellipse([mxp - 15, myp - 15, mxp + 15, myp + 15], outline=(255, 214, 120, 255), width=2)
    bx2, by2 = int(mxp) - 234, int(myp) - 130
    d = panel(img, [bx2, by2, bx2 + 210, by2 + 112], border=(214, 168, 92))
    d.text((bx2 + 12, by2 + 8), 'Marte', fill=(255, 214, 120, 255), font=F(15, True))
    d.rectangle([bx2 + 8, by2 + 34, bx2 + 202, by2 + 62], fill=(30, 90, 100, 255))
    d.text((bx2 + 16, by2 + 39), '➜  Viajar (orbitar)', fill=(200, 255, 250, 255), font=F(14, True))
    d.text((bx2 + 16, by2 + 72), '?  Leer descripción', fill=(220, 228, 236, 255), font=F(14))
    d.text((bx2 + 16, by2 + 92), 'Gasta 4 cubetas de nitrógeno', fill=(150, 170, 190, 255), font=F(11))
    # barra superior
    d = panel(img, [10, 10, 1270, 52])
    d.text((24, 20), 'CONSOLA DE NAVEGACIÓN', fill=(95, 242, 230, 255), font=F(17, True))
    d.text((1256, 22), 'Zoom ×4   ⊕ ⊖', fill=(200, 210, 220, 255), font=F(14), anchor='ra')
    # barra inferior con el estado del motor
    d = panel(img, [10, 650, 1270, 710])
    d.text((24, 660), 'Motor de distorsión:', fill=(200, 210, 220, 255), font=F(14))
    d.text((190, 660), 'LISTO', fill=(120, 255, 140, 255), font=F(14, True))
    d.text((260, 660), 'Nitrógeno 12/16 cubetas    Rotación 256 RPM    Calor 0 %', fill=(200, 210, 220, 255), font=F(14))
    d.text((24, 684), 'Rueda: acercar / alejar    Arrastrar: mover el mapa    Pasar el ratón: descripción    Clic derecho: viajar',
           fill=(130, 150, 170, 255), font=F(12))
    img.save(out)


def efecto(out):
    w, h = 480, 270
    sheet = Image.new('RGBA', (w * 2 + 30, h * 2 + 90), (34, 37, 46, 255))
    sd = ImageDraw.Draw(sheet)
    rnd = random.Random(5)
    stars = [(rnd.uniform(-1, 1), rnd.uniform(-1, 1), rnd.uniform(0.4, 1)) for _ in range(260)]

    def frame(kind):
        img = starfield(w, h, 9, 0)
        d = ImageDraw.Draw(img)
        cx, cy = w / 2, h / 2
        if kind == 'stretch':
            for x, y, z in stars:
                px, py = cx + x * w / 2, cy + y * h / 2
                L = 0.35 * z
                d.line([(px, py), (px + (px - cx) * L, py + (py - cy) * L)], fill=(int(180 * z) + 60, int(170 * z) + 60, 255, 255), width=1)
            glow(img, cx, cy, 60, (170, 120, 255), 90)
            # marco de la cabina
            d = ImageDraw.Draw(img)
            d.rectangle([0, h - 40, w, h], fill=(40, 42, 50, 255))
        elif kind == 'flash':
            img = Image.new('RGBA', (w, h), (255, 255, 255, 255))
            glow(img, cx, cy, 120, (200, 170, 255), 200)
        elif kind == 'tunnel':
            img = Image.new('RGBA', (w, h), (20, 6, 40, 255))
            d = ImageDraw.Draw(img)
            for k in range(26, 0, -1):
                r = k * 13
                c = (90 + k * 5, 40 + k * 3, 200 + k * 2) if k % 2 else (40, 160 + k * 3, 200)
                d.ellipse([cx - r * 1.4, cy - r, cx + r * 1.4, cy + r], outline=c + (255,), width=4)
            for x, y, z in stars[:120]:
                a = math.atan2(y, x); r = 30 + z * 140
                d.line([(cx + math.cos(a) * r * 1.4, cy + math.sin(a) * r), (cx + math.cos(a) * (r + 40) * 1.4, cy + math.sin(a) * (r + 40))], fill=(230, 220, 255, 255))
            img = img.filter(ImageFilter.GaussianBlur(1.2))
            glow(img, cx, cy, 30, (255, 255, 255), 255)
        elif kind == 'arrive':
            img = starfield(w, h, 11, 220)
            d = ImageDraw.Draw(img)
            glow(img, 330, 330, 170, (240, 120, 70), 60)
            d = ImageDraw.Draw(img)
            d.ellipse([330 - 200, 330 - 200, 330 + 200, 330 + 200], fill=(196, 80, 46, 255))
            d.ellipse([200, 160, 300, 220], fill=(224, 122, 80, 255))
            d.ellipse([360, 190, 420, 230], fill=(170, 64, 36, 255))
            fade = Image.new('RGBA', (w, h), (255, 255, 255, 70))
            img.alpha_composite(fade)
            d = ImageDraw.Draw(img)
            d.text((cx, 52), 'MARTE', fill=(255, 255, 255, 255), font=F(28, True), anchor='mm')
            d.text((cx, 84), 'Órbita a 300 km', fill=(235, 235, 235, 255), font=F(15), anchor='mm')
        return img

    names = [('stretch', '1. Carga: las estrellas se estiran (3 s)'), ('flash', '2. Destello blanco y violeta'),
             ('tunnel', '3. Túnel de distorsión (2 s): aquí cambia de sitio'), ('arrive', '4. Se desvanece: ya estás en órbita')]
    for i, (k, label) in enumerate(names):
        x, y = 10 + (i % 2) * (w + 10), 40 + (i // 2) * (h + 40)
        sheet.alpha_composite(frame(k), (x, y))
        sd.text((x, y + h + 8), label, fill=(210, 210, 210, 255), font=F(14))
    sd.text((10, 10), 'Efecto del salto (lo ven todos los que van en la nave)', fill=(240, 240, 240, 255), font=F(18, True))
    sheet.save(out)


if __name__ == '__main__':
    mapa(sys.argv[1])
    efecto(sys.argv[2])
