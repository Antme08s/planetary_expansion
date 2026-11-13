from PIL import Image
import math
H=lambda s: tuple(int(s[i:i+2],16) for i in (0,2,4))+(255,)
P=Image.new('RGBA',(16,16))
px=P.load()
# marco de latón (estilo carcasa de latón de Create, dibujado a mano)
for y in range(16):
    for x in range(16):
        edge=min(x,y,15-x,15-y)
        if edge==0:
            c='9e6947' if (x not in (0,15) or y not in (0,15)) else '724731'
            if x==15 or y==15: c='724731'
            if (x,y) in ((0,0),): c='724731'
            if (x,y) in ((15,0),(0,15),(15,15)): c='592424'
        elif edge==1:
            c='ffeb8c' if (x==1 or y==1) else ('e4b763' if x==14 else 'cea05a')
            if (x,y) in ((1,1),(14,14)): c='cea05a'
            if (x,y) in ((14,1),(1,14)): c='f7cb6c'
        else:
            c=None
        if c: px[x,y]=H(c)
# ventana: cristal oscuro azul-violeta con anillos de "pozo de gravedad"
cx,cy=7.5,7.5
for y in range(2,14):
    for x in range(2,14):
        if x in (2,13) or y in (2,13):
            px[x,y]=H('2a1d3a'); continue   # junta oscura
        r=math.hypot(x-cx,y-cy)
        base=H('140f24')
        ring=(abs(r-4.6)<0.55) or (abs(r-2.4)<0.5)
        if ring: base=H('6a3fb4')
        if r<1.1: base=H('d9b8ff')
        elif r<1.7: base=H('9a6be0')
        px[x,y]=base
# flechas hacia abajo (gravedad) arriba y abajo del núcleo
for (x,y) in [(7,3),(8,3),(7,4),(8,4),(6,4),(9,4)]:
    pass
for (x,y,c) in [(7,11,'b58cff'),(8,11,'b58cff'),(6,10,'8a5ad6'),(9,10,'8a5ad6'),(7,10,'b58cff'),(8,10,'b58cff')]:
    px[x,y]=H(c)
P.save('gravity_generator_side.png')
