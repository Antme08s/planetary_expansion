#!/usr/bin/env python3
"""
Crea una copia de Complementary Reimagined con un perfil de dimensión "espacio"
para los planetas sin atmósfera (Luna de Cosmonautics, Ignis...).

Uso: python3 -I patch_complementary.py <pack_original.zip> <pack_salida.zip> <dim_id> [<dim_id> ...]

Qué cambia SOLO en esas dimensiones (carpeta shaders/world_space, con #define SPACE_DIM):
  - cielo negro (GetSky devuelve negro), sin aurora ni nebulosa del shader
  - sin nubes volumétricas, sin sombras de nubes, sin niebla atmosférica ni rayos de luz
  - el cielo de Cosmonautics (estrellas/nebulosa) ya no se oscurece bajo el "horizonte"
El resto de dimensiones queda exactamente igual que el pack original.
Uso personal: la licencia de Complementary no permite redistribuir versiones modificadas.
"""
import re
import sys
import zipfile

SRC, DST, *DIMS = sys.argv[1:]
FOLDER = "world_space"


def patch_once(text, old, new, what):
    if old not in text:
        raise SystemExit(f"No se encontró el punto de parche: {what}")
    return text.replace(old, new, 1)


with zipfile.ZipFile(SRC) as zin:
    raw = {i.filename: zin.read(i.filename) for i in zin.infolist() if not i.is_dir()}

# Los archivos de texto del pack usan CRLF: se normalizan a LF para parchear y se
# restauran al escribir, así los archivos no tocados quedan idénticos.
TEXT_EXT = (".glsl", ".fsh", ".vsh", ".csh", ".properties", ".json", ".txt", ".lang")
CRLF = set()
files = {}
for n, d in raw.items():
    if n.endswith(TEXT_EXT):
        t = d.decode("utf-8", errors="surrogateescape")
        if "\r\n" in t:
            CRLF.add(n)
            t = t.replace("\r\n", "\n")
        files[n] = t.encode("utf-8", errors="surrogateescape")
    else:
        files[n] = d

root = next(n.split("shaders/")[0] for n in files if n.endswith("shaders/dimension.properties"))
S = root + "shaders/"
out = dict(files)

# 1) dimension.properties: nuestra carpeta antes del comodín de world0
dp = files[S + "dimension.properties"].decode()
dp = patch_once(dp, "dimension.world0=*", f"dimension.{FOLDER}={' '.join(DIMS)}\n\ndimension.world0=*", "dimension.properties")
out[S + "dimension.properties"] = dp.encode()

# 2) carpeta world_space = copia de world0 con SPACE_DIM
for name, data in files.items():
    if name.startswith(S + "world0/"):
        new_name = name.replace(S + "world0/", S + FOLDER + "/", 1)
        if name in CRLF:
            CRLF.add(new_name)
        txt = data.decode("utf-8", errors="surrogateescape")
        if "#define OVERWORLD" in txt:
            txt = txt.replace("#define OVERWORLD", "#define OVERWORLD\n#define SPACE_DIM", 1)
        out[new_name] = txt.encode("utf-8", errors="surrogateescape")

# 3) shaders.properties: mismos programas condicionales que world0
sp = files[S + "shaders.properties"].decode()
sp = re.sub(r"^(\s*)program\.world0/(\S+)$", lambda m: f"{m.group(0)}\n{m.group(1)}program.{FOLDER}/{m.group(2)}",
            sp, flags=re.M)
out[S + "shaders.properties"] = sp.encode()

# 4) common.glsl: apagar efectos atmosféricos en SPACE_DIM
cg = files[S + "lib/common.glsl"].decode()
anchor = "        #define LIGHTSHAFTS_ACTIVE\n    #endif\n"
cg = patch_once(cg, anchor, anchor + """
    #ifdef SPACE_DIM // Cosmonautics: dimensión sin atmósfera
        #undef VL_CLOUDS_ACTIVE
        #undef CLOUDS_REIMAGINED
        #undef CLOUDS_UNBOUND
        #undef CLOUD_SHADOWS
        #undef ATMOSPHERIC_FOG
        #undef LIGHTSHAFTS_ACTIVE
        #undef SPECIAL_BIOME_WEATHER
    #endif
""", "common.glsl")
out[S + "lib/common.glsl"] = cg.encode()

# 4b) composite1: el bloque que guarda vlFactorM para el siguiente fotograma usa la variable aunque los rayos de luz
# estén apagados (en SPACE_DIM se quitan con #undef LIGHTSHAFTS_ACTIVE). Con la configuración por defecto
# (LIGHTSHAFT_BEHAVIOUR 1 y sombras activas) composite1 no compilaba: "undefined variable vlFactorM" (Ignis, 2026-11-08).
c1 = files[S + "program/composite1.glsl"].decode()
c1 = patch_once(c1, "    #if LIGHTSHAFT_QUALI_DEFINE > 0 && LIGHTSHAFT_BEHAVIOUR == 1 && SHADOW_QUALITY >= 1 && defined OVERWORLD || defined END",
                "    #if LIGHTSHAFT_QUALI_DEFINE > 0 && LIGHTSHAFT_BEHAVIOUR == 1 && SHADOW_QUALITY >= 1 && defined OVERWORLD && !defined SPACE_DIM || defined END",
                "composite1.glsl vlFactorM")
out[S + "program/composite1.glsl"] = c1.encode()

# 5) sky.glsl: cielo negro
sk = files[S + "lib/atmospherics/sky.glsl"].decode()
sig = "vec3 GetSky(float VdotU, float VdotS, float dither, bool doGlare, bool doGround) {"
sk = patch_once(sk, sig, sig + "\n        #ifdef SPACE_DIM\n            return vec3(0.0);\n        #endif", "sky.glsl")
out[S + "lib/atmospherics/sky.glsl"] = sk.encode()

# 6) deferred1: sin aurora ni nebulosa del shader en el espacio
d1 = files[S + "program/deferred1.glsl"].decode()
d1 = patch_once(d1, "            #ifdef OVERWORLD\n                #if AURORA_STYLE > 0",
                "            #if defined OVERWORLD && !defined SPACE_DIM\n                #if AURORA_STYLE > 0", "deferred1.glsl")
out[S + "program/deferred1.glsl"] = d1.encode()

# 6b) deferred1: nebulosa del shader siempre presente en el espacio (de día y de noche)
d1 = out[S + "program/deferred1.glsl"].decode()
d1 = patch_once(d1, "#if NIGHT_NEBULAE == 1\n    #include \"/lib/atmospherics/nightNebula.glsl\"",
                "#if NIGHT_NEBULAE == 1 || defined SPACE_DIM\n    #include \"/lib/atmospherics/nightNebula.glsl\"",
                "deferred1 include nebula")
d1 = patch_once(d1, "            #ifdef NETHER\n                color.rgb = netherColor",
                "            #ifdef SPACE_DIM\n                nightNebula += GetNightNebula(viewPos.xyz, VdotU, VdotS);\n                color.rgb += nightNebula;\n            #endif\n"
                "            #ifdef NETHER\n                color.rgb = netherColor", "deferred1 space nebula")
out[S + "program/deferred1.glsl"] = d1.encode()

nb = files[S + "lib/atmospherics/nightNebula.glsl"].decode()
nb = patch_once(nb, "    float nebulaFactor = pow2(max0(VdotU) * min1(nightFactor * 2.0)) * invRainFactor - maxBlindnessDarkness;",
                "    #ifdef SPACE_DIM\n        float nebulaFactor = sqrt1(max0(VdotU)) * 0.8 - maxBlindnessDarkness;\n    #else\n"
                "    float nebulaFactor = pow2(max0(VdotU) * min1(nightFactor * 2.0)) * invRainFactor - maxBlindnessDarkness;\n    #endif",
                "nightNebula.glsl")
out[S + "lib/atmospherics/nightNebula.glsl"] = nb.encode()

# 6c) estrellas del shader visibles también de día y hasta el horizonte en el espacio
stx = files[S + "lib/atmospherics/stars.glsl"].decode()
old = ("    star *= min1(VdotU * 3.0) * max0(1.0 - pow(abs(VdotS) * 1.002, 100.0));\n"
       "    star *= invRainFactor * pow2(pow2(invNoonFactor2)) * (1.0 - 0.5 * sunVisibility);\n")
stx = patch_once(stx, old, "    #ifdef SPACE_DIM\n        star *= min1(VdotU * 20.0) * max0(1.0 - pow(abs(VdotS) * 1.002, 100.0));\n    #else\n"
                 + old + "    #endif\n", "stars.glsl")
out[S + "lib/atmospherics/stars.glsl"] = stx.encode()

# 6d) Niebla de tormenta (Marte / planeta helado, perfil world0).
# El mod acorta la niebla vanilla durante la tormenta (fogStart/fogEnd) y tiñe fogColor.
# Complementary ignora esa niebla, así que aquí se lee de los uniforms de Iris y se pinta
# una niebla densa. Si Iris no diera fogEnd (= 0), no hace nada.
un = files[S + "lib/uniforms.glsl"].decode()
un = patch_once(un, "uniform vec3 fogColor;", "uniform vec3 fogColor;\nuniform float fogStart;\nuniform float fogEnd;",
                "uniforms.glsl fog")
out[S + "lib/uniforms.glsl"] = un.encode()

STORM_FUNCS = """// --- Tormentas de Expansión Planetaria (Cosmonautics) ---
float GetPlanetStormAmount() {
    if (fogEnd < 0.5 || far < 1.0 || isEyeInWater != 0) return 0.0;
    return clamp(1.0 - fogEnd / far, 0.0, 1.0);
}
vec3 GetPlanetStormColor() {
    vec3 c = max(fogColor, vec3(0.0));
    return c * c * 1.8;
}
void DoPlanetStormFog(inout vec4 color, float lViewPos) {
    float amount = GetPlanetStormAmount();
    if (amount < 0.02) return;
    float f = smoothstep(fogStart, max(fogEnd, fogStart + 1.0), lViewPos);
    f = max(f, amount * 0.12);
    color.rgb = mix(color.rgb, GetPlanetStormColor(), f * smoothstep(0.02, 0.3, amount));
}
void DoPlanetStormSky(inout vec4 color) {
    float amount = GetPlanetStormAmount();
    if (amount < 0.02) return;
    color.rgb = mix(color.rgb, GetPlanetStormColor(), smoothstep(0.02, 0.5, amount));
}

void DoFog("""
mf = files[S + "lib/atmospherics/fog/mainFog.glsl"].decode()
mf = patch_once(mf, "\nvoid DoFog(", "\n" + STORM_FUNCS, "mainFog.glsl funcs")
mf = patch_once(mf, "    if (darknessFactor > 0.00001) DoDarknessFog(color, lViewPos);\n}",
                "    if (darknessFactor > 0.00001) DoDarknessFog(color, lViewPos);\n"
                "    #if defined OVERWORLD && !defined SPACE_DIM\n        DoPlanetStormFog(color, lViewPos);\n    #endif\n}",
                "mainFog.glsl call")
out[S + "lib/atmospherics/fog/mainFog.glsl"] = mf.encode()

d1 = out[S + "program/deferred1.glsl"].decode()
d1 = patch_once(d1, "            color = mix(color, vec4(clouds.rgb, 0.0), clouds.a);\n        }\n    #endif\n",
                "            color = mix(color, vec4(clouds.rgb, 0.0), clouds.a);\n        }\n    #endif\n\n"
                "    #if defined OVERWORLD && !defined SPACE_DIM\n        if (z0 >= 1.0) DoPlanetStormSky(color);\n    #endif\n",
                "deferred1 storm sky")
out[S + "program/deferred1.glsl"] = d1.encode()

# 7) skytextured: no oscurecer el cielo personalizado (estrellas de Cosmonautics)
st = files[S + "program/gbuffers_skytextured.glsl"].decode()
st = patch_once(st, "                color.rgb *= color.rgb * smoothstep1(sqrt1(max0(VdotU)));",
                "                #ifndef SPACE_DIM\n                color.rgb *= color.rgb * smoothstep1(sqrt1(max0(VdotU)));\n                #endif",
                "gbuffers_skytextured.glsl")
out[S + "program/gbuffers_skytextured.glsl"] = st.encode()

with zipfile.ZipFile(DST, "w", zipfile.ZIP_DEFLATED) as zout:
    for name, data in out.items():
        if name in CRLF:
            data = data.decode("utf-8", errors="surrogateescape").replace("\n", "\r\n").encode("utf-8", errors="surrogateescape")
        zout.writestr(name, data)
print(f"OK: {DST} ({len(out)} archivos, dimensiones espaciales: {', '.join(DIMS)})")
