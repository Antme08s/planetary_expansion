#!/usr/bin/env python3
"""
Generador de datos para "Cosmonautics: Expansión Planetaria".

Uso:
    python3 -I tools/generate.py <carpeta_datos_vanilla> <carpeta_salida_resources>

<carpeta_datos_vanilla> es la carpeta que contiene data/minecraft/worldgen
(extraída del jar "client-1.21.1-*-extra.jar").

Todo el contenido del mod es data-driven: Cosmonautics lee los planetas de
data/<ns>/universe_planets, Sable lee la física de data/<ns>/dimension_physics
y Minecraft lee dimensiones/biomas/generación de los registros de datapack.
"""
import base64
import json
import math
import struct
import zlib
import os
import shutil
import sys

NS = "planetary_expansion"
MOD_VERSION = "1.0.4"

VANILLA = sys.argv[1]
OUT = sys.argv[2]
VWG = os.path.join(VANILLA, "data", "minecraft", "worldgen")


# --------------------------------------------------------------------------
# utilidades
# --------------------------------------------------------------------------
def write(rel, obj):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        if isinstance(obj, str):
            f.write(obj)
        else:
            json.dump(obj, f, indent=2, ensure_ascii=False)
            f.write("\n")


def tag_add(rel, value):
    """Añade un valor a un tag (sin borrar lo que otros bloques ya pusieron en el mismo archivo)."""
    path = os.path.join(OUT, rel)
    values = []
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            values = json.load(f).get("values", [])
    if value not in values:
        values.append(value)
    write(rel, {"replace": False, "values": values})


def datamap_add(rel, values):
    """Añade valores a un data map de NeoForge (sin borrar los que ya estaban; 1.0.3)."""
    path = os.path.join(OUT, rel)
    cur = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            cur = json.load(f).get("values", {})
    cur.update(values)
    write(rel, {"values": cur})


def data(rel, obj):
    write(f"data/{NS}/{rel}.json", obj)


def rl(path):
    return f"{NS}:{path}"


def block(name, **props):
    b = {"Name": name}
    if props:
        b["Properties"] = {k: str(v) for k, v in props.items()}
    return b


def rgb(r, g, b):
    return (r << 16) | (g << 8) | b


# --------------------------------------------------------------------------
# Clonado del enrutador de ruido vanilla con ruidos propios por planeta.
#
# Los ruidos se siembran con (semilla del mundo + id del ruido). Copiando los
# ruidos y funciones de densidad vanilla bajo <ns>:<planeta>/... cada planeta
# obtiene un relieve distinto del Overworld y de los demás planetas, y además
# no le afectan los mods que sobrescriben minecraft:overworld/* (p. ej. Tectonic).
# --------------------------------------------------------------------------
# tipos cuyo campo "argument" es un ruido y no una función de densidad
SHIFT_TYPES = {"minecraft:shift_a", "minecraft:shift_b", "minecraft:shift"}


class RouterCloner:
    def __init__(self, planet, noise_tweaks=None):
        self.planet = planet
        self.df_done = {}
        self.noise_done = {}
        self.noise_tweaks = noise_tweaks or {}

    def _vanilla_file(self, kind, name):
        if not isinstance(name, str) or not name.startswith("minecraft:"):
            return None
        p = os.path.join(VWG, kind, name.split(":", 1)[1] + ".json")
        return p if os.path.isfile(p) else None

    def noise(self, name):
        src = self._vanilla_file("noise", name)
        if src is None:
            return name
        if name in self.noise_done:
            return self.noise_done[name]
        path = name.split(":", 1)[1]
        new = rl(f"{self.planet}/{path}")
        self.noise_done[name] = new
        with open(src) as f:
            obj = json.load(f)
        tweak = self.noise_tweaks.get(path)
        if tweak:
            obj = tweak(obj)
        data(f"worldgen/noise/{self.planet}/{path}", obj)
        return new

    def df(self, name):
        src = self._vanilla_file("density_function", name)
        if src is None:
            return name
        if name in self.df_done:
            return self.df_done[name]
        path = name.split(":", 1)[1]
        new = rl(f"{self.planet}/{path}")
        self.df_done[name] = new
        with open(src) as f:
            obj = json.load(f)
        data(f"worldgen/density_function/{self.planet}/{path}", self.transform(obj))
        return new

    def transform(self, obj):
        if isinstance(obj, str):
            return self.df(obj)
        if isinstance(obj, list):
            return [self.transform(x) for x in obj]
        if isinstance(obj, dict):
            out = {}
            shift = obj.get("type") in SHIFT_TYPES
            for k, v in obj.items():
                if k == "type":
                    out[k] = v
                elif isinstance(v, str) and (k == "noise" or (shift and k == "argument")):
                    out[k] = self.noise(v)
                else:
                    out[k] = self.transform(v)
            return out
        return obj

    def router_from(self, preset):
        with open(os.path.join(VWG, "noise_settings", preset + ".json")) as f:
            settings = json.load(f)
        return self.transform(settings["noise_router"])


# --------------------------------------------------------------------------
# Reglas de superficie (helpers)
# --------------------------------------------------------------------------
def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def cond(c, then):
    return {"type": "minecraft:condition", "if_true": c, "then_run": then}


def put(b):
    return {"type": "minecraft:block", "result_state": b}


def biome_is(*ids):
    return {"type": "minecraft:biome", "biome_is": [rl(i) for i in ids]}


def floor(offset=0, add_surface_depth=False, secondary=0):
    return {"type": "minecraft:stone_depth", "offset": offset, "surface_type": "floor",
            "add_surface_depth": add_surface_depth, "secondary_depth_range": secondary}


ON_FLOOR = floor()
UNDER_FLOOR = floor(add_surface_depth=True)
DEEP_UNDER_FLOOR = floor(add_surface_depth=True, secondary=6)
STEEP = {"type": "minecraft:steep"}
ABOVE_PRELIM = {"type": "minecraft:above_preliminary_surface"}
BANDLANDS = {"type": "minecraft:bandlands"}


def noise_band(noise, lo, hi):
    return {"type": "minecraft:noise_threshold", "noise": noise, "min_threshold": lo, "max_threshold": hi}


def not_(c):
    return {"type": "minecraft:not", "invert": c}


BEDROCK_FLOOR = cond({"type": "minecraft:vertical_gradient", "random_name": "minecraft:bedrock_floor",
                      "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}},
                     put(block("minecraft:bedrock")))
DEEPSLATE = cond({"type": "minecraft:vertical_gradient", "random_name": "minecraft:deepslate",
                  "true_at_and_below": {"absolute": 0}, "false_at_and_above": {"absolute": 8}},
                 put(block("minecraft:deepslate", axis="y")))


# --------------------------------------------------------------------------
# Biomas
# --------------------------------------------------------------------------
STEPS = 11  # pasos de GenerationStep.Decoration
ALL_BIOMES = []


def features(**by_step):
    order = ["raw_generation", "lakes", "local_modifications", "underground_structures",
             "surface_structures", "strongholds", "underground_ores", "underground_decoration",
             "fluid_springs", "vegetal_decoration", "top_layer_modification"]
    out = [[] for _ in range(STEPS)]
    for k, v in by_step.items():
        out[order.index(k)] = list(v)
    return out


ORES_EARTHLIKE = [
    "minecraft:ore_gravel", "minecraft:ore_granite_upper", "minecraft:ore_granite_lower",
    "minecraft:ore_diorite_upper", "minecraft:ore_diorite_lower", "minecraft:ore_andesite_upper",
    "minecraft:ore_andesite_lower", "minecraft:ore_tuff", "minecraft:ore_coal_upper",
    "minecraft:ore_coal_lower", "minecraft:ore_iron_upper", "minecraft:ore_iron_middle",
    "minecraft:ore_iron_small", "minecraft:ore_gold", "minecraft:ore_gold_lower",
    "minecraft:ore_redstone", "minecraft:ore_redstone_lower", "minecraft:ore_diamond",
    "minecraft:ore_diamond_medium", "minecraft:ore_diamond_large", "minecraft:ore_diamond_buried",
    "minecraft:ore_lapis", "minecraft:ore_lapis_buried", "minecraft:ore_copper",
]


def spawners(**cats):
    base = {k: [] for k in ["ambient", "axolotls", "creature", "misc", "monster",
                            "underground_water_creature", "water_ambient", "water_creature"]}
    for k, v in cats.items():
        base[k] = [{"type": t, "weight": w, "minCount": a, "maxCount": b} for (t, w, a, b) in v]
    return base


def biome(name, *, temperature, downfall, precipitation, sky, fog, water, water_fog,
          carvers, feats, spawn=None, particle=None, grass=None, foliage=None):
    effects = {"sky_color": sky, "fog_color": fog, "water_color": water, "water_fog_color": water_fog}
    if grass is not None:
        effects["grass_color"] = grass
    if foliage is not None:
        effects["foliage_color"] = foliage
    if particle:
        effects["particle"] = particle
    ALL_BIOMES.append(name)
    data(f"worldgen/biome/{name}", {
        "has_precipitation": precipitation,
        "temperature": temperature,
        "downfall": downfall,
        "effects": effects,
        "spawners": spawn or spawners(),
        "spawn_costs": {},
        "carvers": {"air": carvers},
        "features": feats,
    })


# --------------------------------------------------------------------------
# Fuente de biomas multi_noise
# --------------------------------------------------------------------------
FULL = [-1.0, 1.0]
WIDE = [-2.0, 2.0]


def province(planet, name, first_octave, amplitudes, base, k):
    """0.7.9: suma a un parámetro de clima un ruido a escala de planeta (millones de bloques) para que el mapa y el
    holograma de Cosmonautics (que muestrean cada ~65 000-130 000 bloques) muestren zonas grandes. Solo cambia qué
    bioma toca, no el relieve. Sin cache_2d (C2ME no admite cachés anidadas)."""
    data(f"worldgen/noise/{planet}/{name}", {"firstOctave": first_octave, "amplitudes": amplitudes})
    big = {"type": "minecraft:noise", "noise": rl(f"{planet}/{name}"), "xz_scale": 1.0, "y_scale": 0.0}
    return {"type": "minecraft:add", "argument1": base, "argument2": {"type": "minecraft:mul", "argument1": big, "argument2": k}}


def point(biome_id, temperature=FULL, humidity=FULL, continentalness=WIDE, erosion=FULL,
          weirdness=FULL, depth=0.0, offset=0.0):
    return {"biome": rl(biome_id), "parameters": {
        "temperature": temperature, "humidity": humidity, "continentalness": continentalness,
        "erosion": erosion, "weirdness": weirdness, "depth": depth, "offset": offset}}


def noise_settings(planet, router, *, default_block, default_fluid, sea_level, aquifers,
                   surface_rule, spawn_target, min_y=-64, height=384):
    data(f"worldgen/noise_settings/{planet}", {
        "sea_level": sea_level,
        "disable_mob_generation": False,
        "aquifers_enabled": aquifers,
        "ore_veins_enabled": False,
        "legacy_random_source": False,
        "default_block": default_block,
        "default_fluid": default_fluid,
        "noise": {"min_y": min_y, "height": height, "size_horizontal": 1, "size_vertical": 2},
        "noise_router": router,
        "spawn_target": spawn_target,
        "surface_rule": surface_rule,
    })


SPAWN_TARGET_LAND = [{"temperature": FULL, "humidity": FULL, "continentalness": [-0.11, 1.0],
                      "erosion": FULL, "weirdness": FULL, "depth": 0.0, "offset": 0.0}]


def dimension(planet, points):
    data(f"dimension/{planet}", {
        "type": rl(planet),
        "generator": {
            "type": "minecraft:noise",
            "settings": rl(planet),
            "biome_source": {"type": "minecraft:multi_noise", "biomes": points},
        },
    })


def dimension_type(planet, *, effects, ultrawarm=False, fixed_time=None, min_y=-64, height=384):
    extra = {"fixed_time": fixed_time} if fixed_time is not None else {}
    data(f"dimension_type/{planet}", {
        **extra,
        "ambient_light": 0.0,
        "bed_works": False,
        "coordinate_scale": 1.0,
        "effects": effects,
        "has_ceiling": False,
        "has_raids": False,
        "has_skylight": True,
        "height": height,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": height,
        "min_y": min_y,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 7},
        "natural": True,
        "piglin_safe": True,
        "respawn_anchor_works": True,
        "ultrawarm": ultrawarm,
    })


def dimension_physics(planet, **fields):
    obj = {"dimension": rl(planet), "priority": 1000}
    obj.update(fields)
    data(f"dimension_physics/{planet}", obj)


def color(r, g, b, *, biomes=(), tags=(), priority=0, ocean=False):
    c = {"red": r, "green": g, "blue": b}
    if biomes:
        c["matching_biomes"] = [rl(x) for x in biomes]
    if tags:
        c["matching_tags"] = list(tags)
    if priority:
        c["priority"] = priority
    if ocean:
        c["flags"] = ["ocean"]
    return c


def drag_curve(full_until, zero_at):
    return [
        {"altitude": float(full_until), "value": 1.0, "slope": 0.0},
        {"altitude": float(zero_at), "value": 0.0, "slope": -6.0e-4},
    ]


LANG_EN = {}
LANG_ES = {}


def name(biome_id, en, es):
    LANG_EN[f"biome.{NS}.{biome_id}"] = en
    LANG_ES[f"biome.{NS}.{biome_id}"] = es


# ==========================================================================
# MARTE  — sustituye al Marte decorativo de Cosmonautics por uno aterrizable
# ==========================================================================
def build_mars():
    P = "mars"
    router = RouterCloner(P).router_from("large_biomes")
    # provincias: regiones frías con muchos casquetes y regiones oscuras (altiplanos) / claras (dunas)
    router["temperature"] = province(P, "province_cold", -23, [1.0, 0.5, 0.25], router["temperature"], 0.55)
    router["vegetation"] = province(P, "province_dark", -22, [1.0, 0.5], router["vegetation"], 1.2)

    red_sand = block("minecraft:red_sand")
    red_sandstone = block("minecraft:red_sandstone")
    terracotta = block("minecraft:terracotta")
    surface = seq(
        BEDROCK_FLOOR,
        cond(ABOVE_PRELIM, seq(
            # Casquetes polares: nieve sobre hielo compacto
            cond(biome_is("mars_polar_cap"), seq(
                cond(ON_FLOOR, put(block("minecraft:snow_block"))),
                cond(DEEP_UNDER_FLOOR, put(block("minecraft:packed_ice"))),
            )),
            # Cañones: arena roja en el fondo, paredes con bandas de terracota
            cond(biome_is("mars_canyons"), seq(
                cond(STEEP, cond(floor(offset=20), BANDLANDS)),
                cond(ON_FLOOR, put(red_sand)),
                cond(floor(offset=20), BANDLANDS),
            )),
            # Cuencas: el antiguo fondo oceánico, más oscuro
            cond(biome_is("mars_basin"), seq(
                cond(ON_FLOOR, seq(
                    cond(noise_band("minecraft:gravel", 0.25, 2.0), put(block("minecraft:gravel"))),
                    put(block("minecraft:brown_terracotta")),
                )),
                cond(DEEP_UNDER_FLOOR, put(terracotta)),
            )),
            # Altiplanos rocosos
            cond(biome_is("mars_highlands"), seq(
                cond(STEEP, put(block("minecraft:red_terracotta"))),
                cond(ON_FLOOR, seq(
                    cond(noise_band("minecraft:surface", 0.3, 2.0), put(red_sand)),
                    put(block("minecraft:red_terracotta")),
                )),
                cond(DEEP_UNDER_FLOOR, put(terracotta)),
            )),
            # Por defecto (dunas): arena roja sobre arenisca roja
            cond(STEEP, put(red_sandstone)),
            cond(ON_FLOOR, put(red_sand)),
            cond(UNDER_FLOOR, put(red_sand)),
            cond(DEEP_UNDER_FLOOR, put(red_sandstone)),
        )),
        # Corteza rojiza bajo la superficie (también en paredes de cuevas cercanas)
        cond(floor(offset=12, add_surface_depth=True, secondary=10), put(terracotta)),
        DEEPSLATE,
    )

    noise_settings(P, router,
                   default_block=block("minecraft:stone"),
                   default_fluid=block("minecraft:water", level=0),
                   sea_level=-64,          # sin océanos: las cuencas quedan secas
                   aquifers=False,
                   surface_rule=surface,
                   spawn_target=SPAWN_TARGET_LAND)

    # Cráteres usando el carver de Cosmonautics
    data("worldgen/configured_carver/mars_crater", {
        "type": "rocketnautics:crater",
        "config": {
            "probability": 0.02,
            "y": {"below_top": 0},
            "yScale": 1.0,
            "lava_level": {"above_bottom": 0},
            "replaceable": f"#{NS}:mars_carvable",
            "radius": {"type": "rocketnautics:config_biased_to_bottom", "bias": 5, "inner": 3.0,
                       "min_inclusive": 4.0, "max_exclusive": 80.0},
            "heightOffsetFactor": {"type": "minecraft:uniform", "min_inclusive": 0.4, "max_exclusive": 0.6},
            "horizontalRadiusFactor": {"type": "minecraft:uniform", "min_inclusive": 1.0, "max_exclusive": 1.3},
        },
    })
    data("tags/block/mars_carvable", {"values": [
        "minecraft:red_sand", "minecraft:red_sandstone", "#minecraft:terracotta", "minecraft:gravel",
        "minecraft:stone", "minecraft:snow_block", "minecraft:packed_ice", "minecraft:deepslate",
        "minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:tuff",
    ]})

    carvers = [rl("mars_crater"), "minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"]
    # 0.7.46: meteoritos como cráteres (cuenco excavado + núcleo del usuario en el fondo); antes eran estructuras
    data(f"worldgen/configured_feature/{P}/meteorite_crater", {"type": rl("meteorite_crater"), "config": {
        "templates": [rl("mars/meteorite_1"), rl("mars/meteorite_1"), rl("mars/meteorite_2"), rl("mars/meteorite_3")],
        "anchor": [4, 0, 5], "radius_min": 4.0, "radius_max": 8.0, "depth": 0.55}})
    data(f"worldgen/placed_feature/{P}/meteorite_crater", {"feature": rl(f"{P}/meteorite_crater"), "placement": [
        {"type": "minecraft:rarity_filter", "chance": 420}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]})
    meteors = [rl(f"{P}/meteorite_crater")]
    base_feats = dict(
        lakes=["minecraft:lake_lava_underground"],
        local_modifications=["minecraft:amethyst_geode"],
        underground_ores=ORES_EARTHLIKE + ores_mars(),
        fluid_springs=["minecraft:spring_lava"],
    )
    sky, fog = rgb(214, 160, 112), rgb(196, 132, 90)
    common = dict(downfall=0.0, precipitation=False, sky=sky, fog=fog,
                  water=rgb(150, 110, 80), water_fog=rgb(90, 60, 40), carvers=carvers,
                  grass=rgb(150, 110, 70), foliage=rgb(150, 110, 70))
    dust = {"options": {"type": "minecraft:ash"}, "probability": 0.004}

    biome("mars_dunes", temperature=0.6, feats=features(**base_feats, surface_structures=meteors), particle=dust, **common)
    biome("mars_highlands", temperature=0.4, feats=features(**base_feats, surface_structures=meteors), **common)
    biome("mars_canyons", temperature=0.5, feats=features(**base_feats, surface_structures=meteors), **common)
    biome("mars_basin", temperature=0.5, feats=features(**base_feats, surface_structures=meteors), particle=dust, **common)
    biome("mars_polar_cap", temperature=-0.8,
          feats=features(**base_feats, top_layer_modification=["minecraft:freeze_top_layer"]),
          **{**common, "sky": rgb(200, 170, 140)})
    name("mars_dunes", "Martian Dunes", "Dunas marcianas")
    name("mars_highlands", "Martian Highlands", "Altiplanos marcianos")
    name("mars_canyons", "Martian Canyons", "Cañones marcianos")
    name("mars_basin", "Martian Basin", "Cuenca marciana")
    name("mars_polar_cap", "Martian Polar Cap", "Casquete polar marciano")

    land = [-0.19, 2.0]
    warm = [-0.55, 2.0]
    valley = [-0.06, 0.06]
    anyh = [-2.0, 2.0]
    pts = [
        point("mars_basin", continentalness=[-2.0, -0.19], temperature=[-2.0, 2.0], humidity=anyh),
        point("mars_polar_cap", temperature=[-2.0, -0.55], continentalness=land, humidity=anyh),
        point("mars_canyons", temperature=warm, continentalness=[-0.11, 2.0], weirdness=valley, humidity=anyh),
    ]
    for w in ([-1.0, -0.06], [0.06, 1.0]):
        # provincia oscura: todo altiplano; provincia clara: todo dunas; en medio, según la erosión
        pts.append(point("mars_highlands", temperature=warm, continentalness=land, humidity=[-2.0, -0.25], weirdness=w))
        pts.append(point("mars_dunes", temperature=warm, continentalness=land, humidity=[0.45, 2.0], weirdness=w))
        pts.append(point("mars_highlands", temperature=warm, continentalness=land, erosion=[-1.0, -0.2], humidity=[-0.25, 0.45], weirdness=w))
        pts.append(point("mars_dunes", temperature=warm, continentalness=land, erosion=[-0.2, 1.0], humidity=[-0.25, 0.45], weirdness=w))
    # zona costera (-0.19..-0.11) en la franja de valle: dunas
    pts.append(point("mars_dunes", temperature=warm, continentalness=[-0.19, -0.11], weirdness=valley, humidity=anyh))
    dimension(P, pts)

    dimension_type(P, effects="minecraft:overworld")
    dimension_physics(P, universal_drag=0.002, base_gravity=[0.0, -3.7, 0.0],
                      pressure_function=[{"altitude": 64.0, "value": 0.12, "slope": 0.0},
                                         {"altitude": 1500.0, "value": 0.0, "slope": 0.0}])

    data(f"universe_planets/{P}", {
        "name": "mars",
        "priority": 1000,
        "dimension_data": {
            "linked_dimension": rl(P),
            "allowed_transfer": "all",
            "dimension_transfer_height": 20000,
            "atmosphere_composition": {"21000": ["low_density", "drowning"]},
            "render_universe_in_dimension": True,  # 0.7.51: universo en el cielo de todos los planetas
            "dimension_day_time_controller_name": "sol",
            "apply_gravity_correction_to_entities_in_dimension": True,
            "entity_drag_multiplier": drag_curve(1500, 4000),
        },
        "planet_atmosphere": {"enabled": True, "radius": 1.12, "intensity": 0.6, "densityFalloff": 6.0,
                              "scatteringStrength": 0.05, "wavelengths": [450.0, 600.0, 700.0]},
        "planet_texture": {
            "type": "biome_sampler",
            "fallback_red": 190, "fallback_green": 95, "fallback_blue": 55,
            "colors": [
                color(235, 235, 240, biomes=["mars_polar_cap"], priority=10),
                color(150, 70, 40, biomes=["mars_canyons"], priority=5),
                color(120, 75, 55, biomes=["mars_basin"], ocean=True),
                color(115, 50, 32, biomes=["mars_highlands"]),
                color(215, 122, 70, biomes=["mars_dunes"]),
            ],
        },
    })


# ==========================================================================
# MUNDO HELADO — sustituye al ice_world decorativo por uno aterrizable
# ==========================================================================
def build_ice_world():
    P = "ice_world"
    router = RouterCloner(P).router_from("amplified")
    # provincias: regiones de agujas de hielo (azul) y de glaciar (blanco)
    router["vegetation"] = province(P, "province_spires", -22, [1.0, 0.5], router["vegetation"], 1.2)

    snow = block("minecraft:snow_block")
    packed = block("minecraft:packed_ice")
    blue = block("minecraft:blue_ice")
    surface = seq(
        BEDROCK_FLOOR,
        cond(ABOVE_PRELIM, seq(
            cond(biome_is("ice_frozen_sea"), seq(
                cond(ON_FLOOR, put(block("minecraft:gravel"))),
                cond(UNDER_FLOOR, put(packed)),
            )),
            cond(biome_is("ice_crystal_peaks"), seq(
                cond(STEEP, put(blue)),
                cond(ON_FLOOR, seq(
                    cond(noise_band("minecraft:ice", 0.0, 2.0), put(blue)),
                    put(snow),
                )),
                cond(DEEP_UNDER_FLOOR, put(packed)),
            )),
            cond(biome_is("ice_spires"), seq(
                cond(STEEP, put(packed)),
                cond(ON_FLOOR, put(snow)),
                cond(DEEP_UNDER_FLOOR, put(packed)),
            )),
            # glaciares (por defecto)
            cond(STEEP, put(packed)),
            cond(ON_FLOOR, seq(
                cond(noise_band("minecraft:powder_snow", 0.45, 0.58), put(block("minecraft:powder_snow"))),
                put(snow),
            )),
            cond(UNDER_FLOOR, put(snow)),
            cond(DEEP_UNDER_FLOOR, put(packed)),
        )),
        DEEPSLATE,
    )

    noise_settings(P, router,
                   default_block=block("minecraft:stone"),
                   default_fluid=block("minecraft:water", level=0),
                   sea_level=63,
                   aquifers=True,
                   surface_rule=surface,
                   spawn_target=SPAWN_TARGET_LAND)

    carvers = ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"]
    # 0.7.42: sin lava (petición del usuario); en su lugar, bolsas de nitrógeno líquido comunes y de tamaños variados
    data(f"worldgen/configured_feature/{P}/nitrogen_pocket", {"type": rl("nitrogen_pocket"), "config": {
        "small_min": 2.5, "small_max": 4.5, "big_min": 6.5, "big_max": 11.0, "big_chance": 0.3}})
    data(f"worldgen/placed_feature/{P}/nitrogen_pocket", {"feature": rl(f"{P}/nitrogen_pocket"), "placement": [
        {"type": "minecraft:count", "count": 2}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                      "min_inclusive": {"absolute": -48}, "max_inclusive": {"absolute": 48}}},
        {"type": "minecraft:biome"}]})
    base = dict(
        lakes=[rl(f"{P}/nitrogen_pocket")],
        local_modifications=["minecraft:amethyst_geode"],
        underground_ores=ORES_EARTHLIKE + ["minecraft:underwater_magma"] + ores_ice_world(),
        fluid_springs=["minecraft:spring_water"],
        top_layer_modification=["minecraft:freeze_top_layer"],
    )
    spikes = dict(base, surface_structures=["minecraft:ice_spike", "minecraft:ice_patch"])
    mobs = spawners()  # sin mobs de la Tierra
    common = dict(temperature=-0.7, downfall=0.5, precipitation=True,
                  sky=rgb(170, 200, 235), fog=rgb(210, 225, 240),
                  water=rgb(57, 56, 201), water_fog=rgb(5, 5, 51), carvers=carvers, spawn=mobs,
                  grass=rgb(128, 180, 151), foliage=rgb(96, 161, 123))

    biome("ice_glacier", feats=features(**base),
          particle={"options": {"type": "minecraft:white_ash"}, "probability": 0.002}, **common)
    biome("ice_spires", feats=features(**spikes), **common)
    biome("ice_crystal_peaks", feats=features(**spikes), **common)
    biome("ice_frozen_sea", feats=features(**base), **{**common, "temperature": -0.5})
    name("ice_glacier", "Glacier", "Glaciar")
    name("ice_spires", "Ice Spires", "Agujas de hielo")
    name("ice_crystal_peaks", "Crystal Peaks", "Picos de cristal")
    name("ice_frozen_sea", "Frozen Sea", "Mar congelado")

    land = [-0.19, 2.0]
    dimension(P, [
        point("ice_frozen_sea", continentalness=[-2.0, -0.19], humidity=[-2.0, 2.0]),
        point("ice_crystal_peaks", continentalness=land, erosion=[-1.0, -0.375], humidity=[-2.0, 2.0]),
        point("ice_spires", continentalness=land, erosion=[-0.375, 1.0], humidity=[0.3, 2.0]),
        point("ice_glacier", continentalness=land, erosion=[-0.375, 1.0], humidity=[-2.0, 0.3]),
    ])

    # Noche permanente: el planeta helado no recibe luz del sol (hora fija = medianoche).
    dimension_type(P, effects="minecraft:overworld", fixed_time=18000)
    dimension_physics(P, universal_drag=0.01)

    data(f"universe_planets/{P}", {
        "name": "ice_world",
        "priority": 1000,
        "dimension_data": {
            "linked_dimension": rl(P),
            "allowed_transfer": "all",
            "dimension_transfer_height": 20000,
            "atmosphere_composition": {"5000": [], "21000": ["low_density", "drowning"]},
            "render_universe_in_dimension": True,  # 0.7.51: universo en el cielo de todos los planetas
            # sin dimension_day_time_controller_name: la hora fija (noche) del dimension_type manda
            "apply_gravity_correction_to_entities_in_dimension": True,
            "entity_drag_multiplier": drag_curve(4000, 7000),
        },
        # el bloque se sustituye entero: hay que repetir light_source_name del mod base
        "planet_extras": {"has_clouds": True, "light_source_name": "sol"},
        "planet_atmosphere": {"enabled": True, "radius": 1.25, "intensity": 0.9, "densityFalloff": 4.0,
                              "scatteringStrength": 0.07, "wavelengths": [700.0, 560.0, 460.0]},
        "planet_texture": {
            "type": "biome_sampler",
            "fallback_red": 225, "fallback_green": 235, "fallback_blue": 245,
            "colors": [
                color(150, 195, 230, biomes=["ice_frozen_sea"], ocean=True),
                color(120, 175, 235, biomes=["ice_crystal_peaks"], priority=5),
                color(140, 185, 235, biomes=["ice_spires"]),
                color(244, 247, 251, biomes=["ice_glacier"]),
            ],
        },
    })


# ==========================================================================
# IGNIS — luna volcánica nueva en órbita del gigante gaseoso
# ==========================================================================
def build_ignis():
    P = "ignis"
    router = RouterCloner(P).router_from("overworld")
    # provincias: regiones de azufre (amarillo) y de ceniza (gris)
    router["temperature"] = province(P, "province_sulfur", -23, [1.0, 0.5, 0.25], router["temperature"], 1.2)

    basalt = block("minecraft:basalt", axis="y")
    smooth_basalt = block("minecraft:smooth_basalt")
    blackstone = block("minecraft:blackstone")
    magma = block("minecraft:magma_block")
    surface = seq(
        BEDROCK_FLOOR,
        cond(ABOVE_PRELIM, seq(
            cond(biome_is("ignis_lava_sea"), seq(
                cond(ON_FLOOR, put(magma)),
                cond(DEEP_UNDER_FLOOR, put(blackstone)),
            )),
            cond(biome_is("ignis_sulfur_fields"), seq(
                cond(ON_FLOOR, seq(
                    cond(noise_band("minecraft:surface", 0.35, 2.0), put(block("minecraft:white_terracotta"))),
                    cond(noise_band("minecraft:surface", -2.0, -0.45), put(block("minecraft:orange_terracotta"))),
                    put(block("minecraft:yellow_terracotta")),
                )),
                cond(DEEP_UNDER_FLOOR, put(smooth_basalt)),
            )),
            cond(biome_is("ignis_volcanic_peaks"), seq(
                cond(STEEP, put(basalt)),
                cond(ON_FLOOR, put(blackstone)),
                cond(DEEP_UNDER_FLOOR, put(basalt)),
            )),
            # llanuras de ceniza (por defecto)
            cond(ON_FLOOR, seq(
                cond(noise_band("minecraft:gravel", 0.3, 2.0), put(magma)),
                put(smooth_basalt),
            )),
            cond(DEEP_UNDER_FLOOR, put(basalt)),
        )),
    )

    noise_settings(P, router,
                   default_block=blackstone,
                   default_fluid=block("minecraft:lava", level=0),
                   sea_level=40,
                   aquifers=True,
                   surface_rule=surface,
                   spawn_target=SPAWN_TARGET_LAND)

    carvers = ["minecraft:nether_cave"]
    base = dict(lakes=["minecraft:lake_lava_surface", "minecraft:lake_lava_underground"],
                underground_ores=ores_ignis(),
                underground_decoration=["minecraft:ore_magma"],
                fluid_springs=["minecraft:spring_lava"])
    deltas = dict(base, surface_structures=["minecraft:delta", "minecraft:small_basalt_columns",
                                            "minecraft:large_basalt_columns"])
    # 0.7.64: caracoles de magma (las reglas de IgnisCreatures solo los dejan salir con mucha lava cerca)
    mobs = spawners(creature=[(rl("magma_snail"), 12, 1, 3), (rl("magma_titan"), 2, 1, 1)])
    common = dict(downfall=0.0, precipitation=False, sky=0, fog=rgb(40, 30, 25),
                  water=rgb(63, 118, 228), water_fog=rgb(5, 5, 51), carvers=carvers, spawn=mobs)
    ash = {"options": {"type": "minecraft:white_ash"}, "probability": 0.03}

    biome("ignis_ash_plains", temperature=2.0, feats=features(**deltas), particle=ash, **common)
    biome("ignis_sulfur_fields", temperature=2.0, feats=features(**base), **common)
    biome("ignis_volcanic_peaks", temperature=2.0, feats=features(**deltas), particle=ash, **common)
    biome("ignis_lava_sea", temperature=2.0, feats=features(**base), **common)
    name("ignis_ash_plains", "Ash Plains", "Llanuras de ceniza")
    name("ignis_sulfur_fields", "Sulfur Fields", "Campos de azufre")
    name("ignis_volcanic_peaks", "Volcanic Peaks", "Picos volcánicos")
    name("ignis_lava_sea", "Lava Sea", "Mar de lava")

    land = [-0.19, 2.0]
    flat = [-0.375, 1.0]
    dimension(P, [
        point("ignis_lava_sea", continentalness=[-2.0, -0.19], temperature=[-2.0, 2.0]),
        point("ignis_volcanic_peaks", continentalness=land, erosion=[-1.0, -0.375], temperature=[-2.0, 2.0]),
        point("ignis_sulfur_fields", continentalness=land, erosion=flat, temperature=[0.2, 2.0]),
        point("ignis_ash_plains", continentalness=land, erosion=flat, temperature=[-2.0, 0.2]),
    ])

    dimension_type(P, effects="rocketnautics:moon", ultrawarm=True)
    dimension_physics(P, universal_drag=0.0, base_gravity=[0.0, -1.8, 0.0], base_pressure=0.0,
                      magnetic_north=[0.0, 0.0, 0.0])

    data(f"universe_planets/{P}", {
        "name": "ignis",
        "parent": "gas_giant",
        "priority": 1000,
        "acceleration_at_surface": 1.8,
        "radius": 800000.0,
        # Órbita circular a ~5 radios del gigante gaseoso (μ ≈ 3.9e15)
        "position": {"type": "circular_orbit_period",
                     "orbit_axis": {"x": 0.0, "y": 1.0, "z": 0.0},
                     "position_direction": {"x": 1.0, "y": 0.0, "z": 0.0},
                     "period_seconds": 50000.0},
        "rotation": {"type": "tidal_lock", "rotations_per_orbit": -1.0},
        "planet_extras": {"light_source_name": "sol"},
        "dimension_data": {
            "linked_dimension": rl(P),
            "allowed_transfer": "all",
            "dimension_transfer_height": 20000,
            "atmosphere_composition": {"21000": ["low_density", "drowning"]},
            # 0.7.50: prueba del universo en el cielo (el usuario lo pidió). El recorte de estrellas de
            # Cosmonautics (su culling ignora la rotación del planeta) lo arregla SpaceStarsCullMixin.
            "render_universe_in_dimension": True,
            "dimension_day_time_controller_name": "sol",
            "apply_gravity_correction_to_entities_in_dimension": True,
            "entity_drag_multiplier": drag_curve(4000, 7000),
        },
        "planet_texture": {
            "type": "biome_sampler",
            "fallback_red": 200, "fallback_green": 170, "fallback_blue": 60,
            "colors": [
                color(255, 110, 20, biomes=["ignis_lava_sea"], ocean=True),
                color(60, 45, 40, biomes=["ignis_volcanic_peaks"], priority=5),
                color(225, 200, 70, biomes=["ignis_sulfur_fields"]),
                color(110, 100, 90, biomes=["ignis_ash_plains"]),
            ],
        },
    })



# ==========================================================================
# THALASSA — luna oceánica nueva en órbita del gigante gaseoso (idea del usuario, 2026-11-06)
#   - corteza de hielo de 3-6 bloques en la superficie (y 58-63) con grietas y algún agujero
#   - mar vacío de ~120 bloques
#   - fondo de hielo azul (~y -60) y, debajo, zonas de piedra con hierro, carbón y titanio
#     abundantes y diamante raro
#   - aire respirable solo en una capa fina por encima del hielo; frío en la superficie
#   - gravedad baja (2 m/s²)
# ==========================================================================
THALASSA_MIN_Y = -320   # 0.7.26: más hondo para las mega cuevas submarinas (antes -128)
THALASSA_HEIGHT = 576
THALASSA_SEA = 75      # superficie (techo de la corteza de hielo)
THALASSA_AIR_TOP = 90  # aire respirable hasta aquí (15 bloques por encima del hielo)
# OJO: con acuíferos desactivados Minecraft rellena con LAVA todo hueco por debajo de y -54
# (NoiseBasedChunkGenerator: lava si y < min(-54, sea_level)). El fondo del mar tiene que quedar
# siempre por encima: fondo en y -45 ±6 → mar de ~120 bloques con la superficie en y 75.


def build_thalassa():
    P = "thalassa"

    def noise_def(name, first_octave, amplitudes):
        data(f"worldgen/noise/{P}/{name}", {"firstOctave": first_octave, "amplitudes": amplitudes})
        return rl(f"{P}/{name}")

    n_floor = noise_def("floor", -6, [1.0, 1.0, 0.5])
    n_thick = noise_def("sheet_thickness", -5, [1.0, 1.0])
    n_cracks = noise_def("cracks", -6, [1.0, 0.5])
    n_holes = noise_def("holes", -7, [1.0, 1.0])
    # Las reglas de superficie solo leen ruido en 2D (x, z): para que las zonas de piedra no sean columnas,
    # la zona bajo el hielo azul se reparte en 4 franjas de altura, cada una con su propio ruido.
    n_zones = [noise_def(f"stone_zones_{i}", -5, [1.0, 1.0, 0.5]) for i in range(4)]
    n_snow = noise_def("snow", -4, [1.0])
    # Ruidos de clima (solo para elegir bioma: zonas de hielo azul y "lineae", las franjas de Europa)
    n_temp = noise_def("temperature", -9, [1.0, 1.0])
    n_lineae = noise_def("lineae", -8, [1.0, 0.5])
    # 0.7.26: mega cuevas submarinas (idea del usuario; vista previa aprobada)
    n_tun1 = noise_def("cave_tunnel_a", -8, [1.0, 0.3])
    n_tun2 = noise_def("cave_tunnel_b", -8, [1.0, 0.3])
    n_cav = noise_def("cave_cavern", -7, [1.0, 0.5])
    n_shaft = noise_def("cave_shaft", -6, [1.0])
    n_trench = noise_def("trench", -9, [1.0, 0.3])
    n_tmask = noise_def("trench_mask", -10, [1.0])

    def noise2d(n):
        # cache_2d: el ruido no depende de y, se calcula una vez por columna (rendimiento)
        return {"type": "minecraft:cache_2d",
                "argument": {"type": "minecraft:noise", "noise": n, "xz_scale": 1.0, "y_scale": 0.0}}

    def clamp(x, lo, hi):
        return {"type": "minecraft:clamp", "input": x, "min": lo, "max": hi}

    def grad(y0, y1, v0, v1):
        return {"type": "minecraft:y_clamped_gradient", "from_y": y0, "to_y": y1, "from_value": v0, "to_value": v1}

    def add(a, b):
        return {"type": "minecraft:add", "argument1": a, "argument2": b}

    def mul(a, b):
        return {"type": "minecraft:mul", "argument1": a, "argument2": b}

    def dmin(a, b):
        return {"type": "minecraft:min", "argument1": a, "argument2": b}

    def dmax(a, b):
        return {"type": "minecraft:max", "argument1": a, "argument2": b}

    S = THALASSA_SEA
    # Fondo: sólido por debajo de y -45, con ±6 bloques de relieve (ruido acotado: nunca baja de y -51)
    floor_df = add(grad(-75, -15, 1.5, -1.5), mul(clamp(noise2d(n_floor), -1.0, 1.0), 0.3))
    # Corteza: banda sólida entre ~y S-5 (variable) y y S
    band_bottom = add(grad(S - 8, S - 2, -1.0, 1.0), mul(noise2d(n_thick), 0.6))
    band_top = grad(S - 1, S + 1, 1.0, -1.0)
    cracks = mul(add({"type": "minecraft:abs", "argument": noise2d(n_cracks)}, -0.025), 25.0)
    holes = mul(add(noise2d(n_holes), -0.55), -10.0)
    sheet = dmin(dmin(band_bottom, band_top), dmin(cracks, holes))

    # --- Mega cuevas submarinas (0.7.26) --------------------------------------------------------------
    # Todo con ruidos 3D o cache_2d sin anidar (C2ME OpenCL falla con cache_2d dentro de cache_2d).
    def noise3d(n, y_scale):
        return {"type": "minecraft:noise", "noise": n, "xz_scale": 1.0, "y_scale": y_scale}

    def absf(x):
        return {"type": "minecraft:abs", "argument": x}

    def env(top, bot, fade):
        # 1 en el centro de la franja y baja a 0 hacia los extremos: túneles y cavernas se cierran redondeados
        return dmin(grad(top - fade, top, 1.0, 0.0), grad(bot, bot + fade, 0.0, 1.0))

    Y = grad(-512, 512, -512.0, 512.0)
    # túneles enormes (red): donde dos ruidos lentos pasan a la vez cerca de 0
    tunnels = mul(add(dmax(absf(noise3d(n_tun1, 0.6)), absf(noise3d(n_tun2, 0.6))),
                      mul(env(-60, -300, 35), -0.17)), 12.0)
    # mega cavernas: manchas grandes de un ruido lento
    caverns = mul(add(add(1.10, mul(env(-100, -295, 45), -0.8)), mul(noise3d(n_cav, 0.55), -1.0)), 6.0)
    # simas: embudos verticales desde el fondo que se estrechan al bajar
    shafts = mul(add(add(add(1.34, mul(env(-30, -230, 25), -0.6)), grad(-45, -230, 0.0, 0.08)),
                     mul(noise2d(n_shaft), -1.0)), 20.0)
    # fosas: cañones con perfil en U en el fondo del mar; los túneles salen por sus paredes (entradas horizontales)
    t = mul(absf(noise2d(n_trench)), 1.0 / 0.06)
    prof = mul(clamp(add(1.0, mul(mul(t, t), -1.0)), 0.0, 1.0), clamp(mul(noise2d(n_tmask), 3.0), 0.0, 1.0))
    tfloor = add(add(-48.0, mul(prof, -75.0)),
                 mul({"type": "minecraft:noise", "noise": n_floor, "xz_scale": 3.0, "y_scale": 0.0}, 4.0))
    trenches = dmax(mul(add(tfloor, mul(Y, -1.0)), 0.15), mul(add(0.02, mul(prof, -1.0)), 250.0))
    carve = dmin(dmin(tunnels, caverns), dmin(shafts, trenches))
    final = dmax(dmin(floor_df, carve), sheet)
    zero = 0.0
    router = {
        "barrier": zero, "fluid_level_floodedness": zero, "fluid_level_spread": zero, "lava": zero,
        # 0.7.9: provincias de hielo azul y "lineae" a escala de planeta (red de líneas como en Europa)
        "temperature": province(P, "province_blue", -23, [1.0, 0.5, 0.25], noise2d(n_temp), 1.0),
        "vegetation": zero, "continents": zero, "erosion": zero, "depth": zero,
        "ridges": dmin({"type": "minecraft:abs", "argument": noise2d(n_lineae)},
                       mul({"type": "minecraft:abs", "argument": province(P, "lineae_big", -22, [1.0], 0.0, 1.0)}, 0.8)),
        "initial_density_without_jaggedness": floor_df, "final_density": final,
        "vein_toggle": zero, "vein_ridged": zero, "vein_gap": zero,
    }

    def y_above(y):
        return {"type": "minecraft:y_above", "anchor": {"absolute": y}, "surface_depth_multiplier": 0,
                "add_stone_depth": False}

    blue = block("minecraft:blue_ice")
    packed = block("minecraft:packed_ice")
    surface = seq(
        BEDROCK_FLOOR,
        # corteza de la superficie
        cond(y_above(S - 23), seq(
            cond(ON_FLOOR, seq(
                cond(biome_is("thalassa_blue_ice_fields"), put(blue)),
                cond(biome_is("thalassa_lineae"), put(packed)),
                cond(noise_band(n_snow, 0.25, 10.0), put(block("minecraft:snow_block"))),
                put(packed),
            )),
            put(packed),
        )),
        # fondo: capa de hielo azul de 7-12 bloques (solo en el fondo del mar, no en el suelo de las cuevas)
        cond(y_above(-60), cond(floor(offset=6, add_surface_depth=True), put(blue))),
        # debajo: zonas de piedra (con minerales) y el resto hielo compacto, en 4 franjas
        *[cond(y_above(top), seq(cond(noise_band(n_zones[i], -10.0, -0.05), put(packed)),
                                 put(block("minecraft:stone"))))
          for i, top in enumerate((-75, -92, -110, -200))],
    )

    noise_settings(P, router,
                   default_block=block("minecraft:stone"),
                   default_fluid=block("minecraft:water", level=0),
                   sea_level=THALASSA_SEA,
                   aquifers=False,
                   surface_rule=surface,
                   spawn_target=SPAWN_TARGET_LAND,
                   min_y=THALASSA_MIN_Y, height=THALASSA_HEIGHT)

    # Minerales: solo en las zonas de piedra bajo el hielo azul (y -126 a -60)
    def placed(name, feature, mods, lo=-300, hi=-60):
        placement = mods + [{"type": "minecraft:in_square"},
                            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                             "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}},
                            {"type": "minecraft:biome"}]
        data(f"worldgen/placed_feature/{P}/{name}", {"feature": feature, "placement": placement})
        return rl(f"{P}/{name}")

    def ores(name, feature, count):
        return placed(name, feature, [{"type": "minecraft:count", "count": count}], lo=-310, hi=-55)

    # 0.7.26: la piedra baja hasta y -320 y las cuevas dejan las vetas a la vista (el usuario pidió más minerales)
    ore_list = [
        ores("ore_iron", "minecraft:ore_iron", 70),
        ores("ore_coal", "minecraft:ore_coal", 55),
        ores("ore_titanium", "rocketnautics:titanium_ore", 60),
        ores("ore_diamond", "minecraft:ore_diamond_small", 9),
        ores("ore_diamond_large", "minecraft:ore_diamond_large", 1),
    ]
    # Cristales: geodas de amatista y prismarina SIN brotes (los brotes dan luz; las cuevas son totalmente oscuras)
    geode = json.load(open(os.path.join(VANILLA, "data/minecraft/worldgen/configured_feature/amethyst_geode.json")))
    gb = geode["config"]["blocks"]
    gb["filling_provider"] = {"type": "minecraft:simple_state_provider", "state": block("minecraft:water", level=0)}
    gb["inner_layer_provider"] = {"type": "minecraft:simple_state_provider", "state": block("minecraft:amethyst_block")}
    gb["alternate_inner_layer_provider"] = {"type": "minecraft:simple_state_provider", "state": block("minecraft:prismarine")}
    gb["middle_layer_provider"] = {"type": "minecraft:simple_state_provider", "state": block("minecraft:dark_prismarine")}
    gb["outer_layer_provider"] = {"type": "minecraft:simple_state_provider", "state": block("minecraft:smooth_basalt")}
    gb["inner_placements"] = [block("minecraft:prismarine")]
    geode["config"]["use_potential_placements_chance"] = 0.0
    geode["config"]["use_alternate_layer0_chance"] = 0.35
    data(f"worldgen/configured_feature/{P}/crystal_geode", geode)
    geodes = placed("crystal_geode", rl(f"{P}/crystal_geode"), [{"type": "minecraft:rarity_filter", "chance": 5}], lo=-295, hi=-70)
    # Columnas y estalactitas (poco comunes) y chimeneas hidrotermales (raras), en el suelo de las cuevas
    columns = placed("cave_columns", "minecraft:large_dripstone", [{"type": "minecraft:rarity_filter", "chance": 4},
                                                                  {"type": "minecraft:count", "count": 3}], lo=-290, hi=-70)
    data(f"worldgen/configured_feature/{P}/vent", {"type": rl("thalassa_vent"), "config": {}})
    vents = placed("vent", rl(f"{P}/vent"), [
        {"type": "minecraft:rarity_filter", "chance": 9},
        {"type": "minecraft:count", "count": 2}], lo=-290, hi=-70)
    # bajar hasta el suelo: el respiradero se pone en el primer bloque de agua sobre roca
    vp = json.load(open(os.path.join(OUT, f"data/{NS}/worldgen/placed_feature/{P}/vent.json")))
    vp["placement"].insert(-1, {"type": "minecraft:environment_scan", "direction_of_search": "down", "max_steps": 32,
                                "target_condition": {"type": "minecraft:solid"},
                                "allowed_search_condition": {"type": "minecraft:matching_fluids", "fluids": "minecraft:water"}})
    vp["placement"].insert(-1, {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": 1})
    data(f"worldgen/placed_feature/{P}/vent", vp)

    # 0.7.42: bolsas de nitrógeno líquido poco comunes en la corteza (petición del usuario)
    data(f"worldgen/configured_feature/{P}/nitrogen_pocket", {"type": rl("nitrogen_pocket"), "config": {
        "small_min": 2.5, "small_max": 4.5, "big_min": 6.5, "big_max": 10.0, "big_chance": 0.35}})
    pockets = placed("nitrogen_pocket", rl(f"{P}/nitrogen_pocket"), [{"type": "minecraft:rarity_filter", "chance": 6}], lo=-290, hi=-80)
    thal = dict(temperature=0.5, downfall=0.0, precipitation=False,
                sky=rgb(120, 150, 200), fog=rgb(190, 210, 235), water=rgb(30, 80, 170), water_fog=rgb(5, 15, 45),
                carvers=[], feats=features(lakes=[pockets], local_modifications=[geodes], underground_ores=ore_list,
                                           underground_decoration=[columns, vents]),
                # 0.7.61: criaturas (los cangrejos de chimenea salen al generarse cada chimenea)
                spawn=spawners(water_ambient=[(rl("lantern_fish"), 25, 4, 8)], creature=[(rl("king_crab"), 4, 1, 1)],
                               monster=[(rl("abyssal_stalker"), 20, 1, 1)]))
    biome("thalassa_ice_shell", **thal)
    biome("thalassa_blue_ice_fields", **thal)
    biome("thalassa_lineae", **thal)
    name("thalassa_ice_shell", "Ice Shell", "Corteza de hielo")
    name("thalassa_blue_ice_fields", "Blue Ice Fields", "Campos de hielo azul")
    name("thalassa_lineae", "Lineae", "Líneas")

    # Las "lineae" son franjas finas donde el ruido de rugosidad pasa por 0; los campos de hielo azul,
    # manchas grandes donde la temperatura es alta. Solo cambian la capa de arriba y el color desde el espacio.
    out_l = [[-1.0, -0.05], [0.05, 1.0]]
    # (desde 0.7.9 la rugosidad es >= 0: mínimo de dos valores absolutos)
    pts = [point("thalassa_lineae", weirdness=[-0.05, 0.05], temperature=[-2.0, 2.0])]
    for w in out_l:
        pts.append(point("thalassa_ice_shell", temperature=[-2.0, 0.35], weirdness=w))
        pts.append(point("thalassa_blue_ice_fields", temperature=[0.35, 2.0], weirdness=w))
    data(f"dimension/{P}", {
        "type": rl(P),
        "generator": {"type": "minecraft:noise", "settings": rl(P),
                      "biome_source": {"type": "minecraft:multi_noise", "biomes": pts}},
    })
    dimension_type(P, effects="minecraft:overworld", min_y=THALASSA_MIN_Y, height=THALASSA_HEIGHT)
    dimension_physics(P, universal_drag=0.004, base_gravity=[0.0, -2.0, 0.0],
                      pressure_function=[{"altitude": 64.0, "value": 0.2, "slope": 0.0},
                                         {"altitude": 600.0, "value": 0.0, "slope": 0.0}])

    data(f"universe_planets/{P}", {
        "name": "thalassa",
        "parent": "gas_giant",
        "priority": 1000,
        "acceleration_at_surface": 2.0,
        "radius": 1100000.0,
        # más lejos del gigante que Ignis (periodo 50 000 s) y empezando en otra dirección
        "position": {"type": "circular_orbit_period",
                     "orbit_axis": {"x": 0.0, "y": 1.0, "z": 0.0},
                     "position_direction": {"x": 0.0, "y": 0.0, "z": 1.0},
                     "period_seconds": 120000.0},
        "rotation": {"type": "tidal_lock", "rotations_per_orbit": -1.0},
        "planet_extras": {"light_source_name": "sol"},
        "dimension_data": {
            "linked_dimension": rl(P),
            "allowed_transfer": "all",
            "dimension_transfer_height": 20000,
            # aire solo en la capa fina sobre el hielo
            "atmosphere_composition": {str(THALASSA_AIR_TOP): [], "21000": ["low_density", "drowning"]},
            "render_universe_in_dimension": True,  # 0.7.51: universo en el cielo de todos los planetas
            "dimension_day_time_controller_name": "sol",
            "apply_gravity_correction_to_entities_in_dimension": True,
            "entity_drag_multiplier": drag_curve(600, 3000),
        },
        "planet_atmosphere": {"enabled": True, "radius": 1.06, "intensity": 0.35, "densityFalloff": 8.0,
                              "scatteringStrength": 0.05, "wavelengths": [700.0, 560.0, 440.0]},
        "planet_texture": {
            "type": "biome_sampler",
            "fallback_red": 215, "fallback_green": 232, "fallback_blue": 245,
            "colors": [color(150, 112, 92, biomes=["thalassa_lineae"], priority=5),
                       color(150, 190, 238, biomes=["thalassa_blue_ice_fields"]),
                       color(222, 234, 246, biomes=["thalassa_ice_shell"])],
        },
    })

# ==========================================================================
# VENUS — planeta nuevo en órbita del Sol, por dentro de la Tierra (idea del usuario, 2026-11-06)
#   - llanuras de basalto (~y 70), volcanes en escudo con caldera de magma y mesetas de teselas agrietadas
#   - sin aire respirable, atmósfera densa (presión alta de Sable), neblina amarilla permanente (60 bloques; 40 en la lluvia fuerte)
#   - gravedad casi como la de la Tierra
#   - calor en todas partes y tormentas de lluvia ácida frecuentes (weather/VenusHazards)
#   - hierro, cobre y oro normales; minerales de Create Nuclear más comunes que en la Tierra (si está instalado)
# ==========================================================================
VENUS_PLAINS_Y = 70


def build_venus():
    P = "venus"

    def noise_def(name, first_octave, amplitudes):
        data(f"worldgen/noise/{P}/{name}", {"firstOctave": first_octave, "amplitudes": amplitudes})
        return rl(f"{P}/{name}")

    n_plains = noise_def("plains", -6, [1.0, 1.0, 0.5])
    n_volc = noise_def("volcanoes", -9, [1.0, 0.25])
    n_tess = noise_def("tesserae", -8, [1.0, 0.5])
    n_crack = noise_def("tesserae_cracks", -6, [1.0, 0.5])
    n_rock = noise_def("rock_patches", -4, [1.0, 0.5])
    # 0.7.7: provincias a escala de planeta (millones de bloques) para que el mapa/holograma tenga zonas grandes
    # de colores: llanuras oscuras/claras y "tierras altas" donde se juntan las teselas.
    n_prov = noise_def("provinces", -23, [1.0, 0.5, 0.25])
    n_high = noise_def("highlands", -22, [1.0, 0.5])

    def noise2d(n):
        return {"type": "minecraft:noise", "noise": n, "xz_scale": 1.0, "y_scale": 0.0}

    def clamp(x, lo, hi):
        return {"type": "minecraft:clamp", "input": x, "min": lo, "max": hi}

    def add(a, b):
        return {"type": "minecraft:add", "argument1": a, "argument2": b}

    def mul(a, b):
        return {"type": "minecraft:mul", "argument1": a, "argument2": b}

    def dmax(a, b):
        return {"type": "minecraft:max", "argument1": a, "argument2": b}

    v, c = noise2d(n_volc), noise2d(n_crack)
    # OJO: sin cache_2d aquí. Va dentro de la caché de la altura y C2ME (aceleración OpenCL) no admite cache_2d
    # anidados: crashea al abrir el mundo con "Cannot register more caches" (pasó en 0.7.7).
    t = add(noise2d(n_tess), mul(noise2d(n_high), 0.5))
    # Altura del terreno H(x, z) en bloques (todo 2D: se calcula una vez por columna)
    plains = add(float(VENUS_PLAINS_Y), mul(noise2d(n_plains), 4.0))
    # Volcán en escudo: sube suave donde el ruido de volcanes es alto y en la cima se hunde la caldera:
    # borde a ~+30 (y 100) y fondo plano a ~+13 (y 83), 17 bloques más abajo.
    dome = mul(clamp(add(v, -0.22), 0.0, 0.33), 130.0)
    caldera = mul(clamp(add(v, -0.45), 0.0, 0.10), 300.0)
    volcano = add(dome, mul(caldera, -1.0))
    # Teselas: mesetas de +28 con bordes empinados, cortadas por grietas en V de hasta 14 de profundidad
    plateau = clamp(mul(add(t, -0.30), 6.0), 0.0, 1.0)
    crack = clamp(add(1.0, mul({"type": "minecraft:abs", "argument": c}, -1.0 / 0.07)), 0.0, 1.0)
    tesserae = mul(plateau, add(28.0, mul(crack, -14.0)))
    height = {"type": "minecraft:cache_2d", "argument": add(plains, dmax(volcano, tesserae))}
    # densidad = (H - y) / 2: sólido por debajo de H. Nunca deja huecos por debajo de y -54 (ahí iría lava).
    minus_y = {"type": "minecraft:y_clamped_gradient", "from_y": -64, "to_y": 320,
               "from_value": 64.0, "to_value": -320.0}
    density = mul(add(height, minus_y), 0.5)
    zero = 0.0
    router = {
        "barrier": zero, "fluid_level_floodedness": zero, "fluid_level_spread": zero, "lava": zero,
        # solo para elegir bioma: temperatura = volcanes, humedad = teselas
        "temperature": {"type": "minecraft:cache_2d", "argument": v},
        "vegetation": {"type": "minecraft:cache_2d", "argument": t},
        # provincias: llanuras oscuras (< -0.05) o claras
        "continents": {"type": "minecraft:cache_2d", "argument": noise2d(n_prov)},
        "erosion": zero, "depth": zero, "ridges": zero,
        "initial_density_without_jaggedness": density, "final_density": density,
        "vein_toggle": zero, "vein_ridged": zero, "vein_gap": zero,
    }

    basalt = block("minecraft:basalt", axis="y")
    smooth = block("minecraft:smooth_basalt")
    blackstone = block("minecraft:blackstone")
    magma = block("minecraft:magma_block")
    tuff = block("minecraft:tuff")
    surface = seq(
        BEDROCK_FLOOR,
        cond(biome_is("venus_shield_volcanoes"), seq(
            cond(ON_FLOOR, seq(
                # fondo de la caldera: lago de magma en el centro, piedra negra alrededor
                cond(noise_band(n_volc, 0.60, 10.0), seq(
                    cond(noise_band(n_rock, 0.35, 10.0), put(blackstone)),
                    put(magma))),
                cond(noise_band(n_volc, 0.52, 10.0), put(blackstone)),
                cond(STEEP, put(basalt)),
                cond(noise_band(n_rock, -0.12, 0.12), put(blackstone)),
                put(smooth),
            )),
            cond(UNDER_FLOOR, put(basalt)),
        )),
        cond(biome_is("venus_tesserae"), seq(
            cond(ON_FLOOR, seq(
                cond(STEEP, put(block("minecraft:deepslate", axis="y"))),
                cond(noise_band(n_rock, 0.3, 10.0), put(smooth)),
                put(tuff),
            )),
            cond(UNDER_FLOOR, put(tuff)),
        )),
        # llanuras de basalto oscuro (0.7.7): basalto y piedra negra
        cond(biome_is("venus_dark_plains"), seq(
            cond(ON_FLOOR, seq(
                cond(noise_band(n_rock, -10.0, -0.2), put(blackstone)),
                cond(noise_band(n_rock, 0.5, 10.0), put(smooth)),
                put(basalt),
            )),
            cond(UNDER_FLOOR, put(basalt)),
        )),
        # llanuras de basalto (por defecto)
        cond(ON_FLOOR, seq(
            cond(noise_band(n_rock, 0.45, 10.0), put(basalt)),
            cond(noise_band(n_rock, -10.0, -0.5), put(blackstone)),
            put(smooth),
        )),
        cond(UNDER_FLOOR, put(smooth)),
        DEEPSLATE,
    )

    noise_settings(P, router,
                   default_block=block("minecraft:stone"),
                   default_fluid=block("minecraft:lava", level=0),
                   sea_level=-64,
                   aquifers=False,
                   surface_rule=surface,
                   spawn_target=SPAWN_TARGET_LAND)

    def ore(name, feature, count, lo, hi, shape="minecraft:uniform", condition=None):
        placement = [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
                     {"type": "minecraft:height_range", "height": {"type": shape,
                      "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}},
                     {"type": "minecraft:biome"}]
        obj = {"feature": feature, "placement": placement}
        if condition:
            obj = {"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": condition}], **obj}
        data(f"worldgen/placed_feature/{P}/{name}", obj)
        return rl(f"{P}/{name}")

    ore_list = [
        ore("ore_iron", "minecraft:ore_iron", 12, -60, 72, "minecraft:trapezoid"),
        ore("ore_iron_small", "minecraft:ore_iron_small", 10, -64, 72),
        ore("ore_copper", "minecraft:ore_copper_small", 14, -16, 90, "minecraft:trapezoid"),
        ore("ore_gold", "minecraft:ore_gold", 5, -64, 32, "minecraft:trapezoid"),
        ore("ore_tuff", "minecraft:ore_tuff", 2, -64, 0),
    ]
    # Create Nuclear (dependencia obligatoria desde 0.7.2). Más que en la Tierra: allí uranio 4, torio 6,
    # plomo 10, nitrato 6 por chunk.
    cn = [
        ore("cn_uranium", "createnuclear:uranium_ore", 20, -63, 40, "minecraft:trapezoid"),
        ore("cn_thorium", "createnuclear:thorium_ore", 24, -40, 70, "minecraft:trapezoid"),
        ore("cn_lead", "createnuclear:lead_ore", 32, -30, 70, "minecraft:trapezoid"),
        ore("cn_nitrate", "createnuclear:nitrate_ore", 20, -63, 70, "minecraft:trapezoid"),
    ]
    data("tags/worldgen/biome/venus_biomes", {"values": [rl(b) for b in VENUS_BIOMES]})
    data("neoforge/biome_modifier/venus_createnuclear_ores", {
        "type": "neoforge:add_features",
        "biomes": f"#{NS}:venus_biomes",
        "features": cn,
        "step": "underground_ores",
    })

    haze = {"options": {"type": "minecraft:dust", "color": [0.85, 0.72, 0.35], "scale": 1.0}, "probability": 0.002}
    common = dict(temperature=2.0, downfall=0.0, precipitation=False,
                  sky=rgb(196, 150, 74), fog=rgb(178, 132, 62), water=rgb(150, 160, 60), water_fog=rgb(70, 70, 20),
                  carvers=["minecraft:cave", "minecraft:cave_extra_underground"],
                  feats=features(underground_ores=ore_list), spawn=spawners(), particle=haze)
    # ríos de lava (0.7.7): respiraderos en las faldas altas de los volcanes; la lava baja sola por la pendiente
    data(f"worldgen/configured_feature/{P}/lava_vent", {"type": rl("venus_lava_vent"), "config": {}})
    data(f"worldgen/placed_feature/{P}/lava_vent", {"feature": rl(f"{P}/lava_vent"), "placement": [
        {"type": "minecraft:rarity_filter", "chance": 6}, {"type": "minecraft:in_square"}, {"type": "minecraft:biome"}]})
    for b in VENUS_BIOMES:
        if b == "venus_shield_volcanoes":
            biome(b, **{**common, "feats": features(underground_ores=ore_list, fluid_springs=[rl(f"{P}/lava_vent")])})
        else:
            biome(b, **common)
    name("venus_basalt_plains", "Basalt Plains", "Llanuras de basalto")
    name("venus_dark_plains", "Dark Basalt Plains", "Llanuras de basalto oscuro")
    name("venus_shield_volcanoes", "Shield Volcanoes", "Volcanes en escudo")
    name("venus_tesserae", "Tesserae", "Teselas")

    # volcán un poco dentro de la falda (empieza a subir en 0.22), teselas donde la meseta ya es alta
    data(f"dimension/{P}", {
        "type": rl(P),
        "generator": {"type": "minecraft:noise", "settings": rl(P), "biome_source": {"type": "minecraft:multi_noise", "biomes": [
            point("venus_shield_volcanoes", temperature=[0.30, 1.0]),
            point("venus_tesserae", temperature=[-1.0, 0.30], humidity=[0.36, 1.0]),
            point("venus_dark_plains", temperature=[-1.0, 0.30], humidity=[-1.0, 0.36], continentalness=[-2.0, -0.05]),
            point("venus_basalt_plains", temperature=[-1.0, 0.30], humidity=[-1.0, 0.36], continentalness=[-0.05, 2.0]),
        ]}},
    })
    # no "ultracaliente": el agua se evapora fuera de las estructuras físicas (mixin/VenusWaterMixin)
    dimension_type(P, effects="minecraft:overworld")
    dimension_physics(P, universal_drag=0.01, base_gravity=[0.0, -8.9, 0.0],
                      pressure_function=[{"altitude": 64.0, "value": 2.5, "slope": 0.0},
                                         {"altitude": 4000.0, "value": 0.0, "slope": 0.0}])

    data(f"universe_planets/{P}", {
        "name": "venus",
        "parent": "sol",
        "priority": 1000,
        "acceleration_at_surface": 8.9,
        "radius": 2850000.0,
        # por dentro de la órbita de la Tierra (604 800 s): 0.615 años terrestres, como el real
        "position": {"type": "circular_orbit_period",
                     "orbit_axis": {"x": 0.0, "y": 1.0, "z": 0.0},
                     "position_direction": {"x": -1.0, "y": 0.0, "z": 0.0},
                     "period_seconds": 372000.0},
        # gira al revés que la Tierra: el Sol sale por el oeste
        "rotation": {"type": "period", "period_seconds": 1200.0, "rotation_axis": {"x": 0.0, "y": 1.0, "z": 0.0}},
        "planet_extras": {"light_source_name": "sol", "has_clouds": True},
        "dimension_data": {
            "linked_dimension": rl(P),
            "allowed_transfer": "all",
            "dimension_transfer_height": 20000,
            "atmosphere_composition": {"21000": ["drowning"]},
            "render_universe_in_dimension": True,  # 0.7.51: universo en el cielo de todos los planetas
            "dimension_day_time_controller_name": "sol",
            "apply_gravity_correction_to_entities_in_dimension": True,
            "entity_drag_multiplier": drag_curve(6000, 10000),
        },
        "planet_atmosphere": {"enabled": True, "radius": 1.18, "intensity": 1.2, "densityFalloff": 4.0,
                              "scatteringStrength": 0.08, "wavelengths": [480.0, 510.0, 700.0]},
        "planet_texture": {
            "type": "biome_sampler",
            "fallback_red": 214, "fallback_green": 180, "fallback_blue": 110,
            "colors": [color(110, 65, 38, biomes=["venus_shield_volcanoes"], priority=5),
                       color(232, 208, 156, biomes=["venus_tesserae"]),
                       color(140, 100, 62, biomes=["venus_dark_plains"]),
                       color(215, 175, 105, biomes=["venus_basalt_plains"])],
        },
    })


VENUS_BIOMES = ["venus_basalt_plains", "venus_dark_plains", "venus_shield_volcanoes", "venus_tesserae"]

# Lo que la lluvia ácida de Venus corroe si está a la intemperie (petición del usuario: lana, madera y plantas)
ACID_CORRODES = [
    "#minecraft:wool", "#minecraft:wool_carpets", "#minecraft:beds", "#minecraft:banners",
    "#minecraft:logs", "#minecraft:planks", "#minecraft:wooden_slabs", "#minecraft:wooden_stairs",
    "#minecraft:wooden_fences", "#minecraft:fence_gates", "#minecraft:wooden_doors", "#minecraft:wooden_trapdoors",
    "#minecraft:wooden_buttons", "#minecraft:wooden_pressure_plates", "#minecraft:all_signs",
    "#minecraft:bamboo_blocks", "minecraft:crafting_table", "minecraft:bookshelf", "minecraft:chiseled_bookshelf",
    "minecraft:ladder", "minecraft:composter", "minecraft:lectern", "minecraft:barrel", "minecraft:chest",
    "minecraft:trapped_chest", "minecraft:scaffolding", "minecraft:note_block", "minecraft:jukebox",
    "minecraft:loom", "minecraft:cartography_table", "minecraft:fletching_table", "minecraft:smithing_table",
    "minecraft:beehive", "minecraft:bee_nest", "minecraft:hay_block",
    # plantas
    "#minecraft:leaves", "#minecraft:saplings", "#minecraft:flowers", "#minecraft:crops",
    "#minecraft:replaceable_by_trees", "#minecraft:sword_efficient", "#minecraft:cave_vines",
    "minecraft:moss_block", "minecraft:moss_carpet", "minecraft:cactus", "minecraft:sugar_cane",
    "minecraft:bamboo", "minecraft:brown_mushroom", "minecraft:red_mushroom", "minecraft:brown_mushroom_block",
    "minecraft:red_mushroom_block", "minecraft:mushroom_stem", "minecraft:pumpkin", "minecraft:carved_pumpkin",
    "minecraft:jack_o_lantern", "minecraft:melon", "minecraft:dried_kelp_block", "minecraft:kelp",
    "minecraft:kelp_plant", "minecraft:lily_pad", "minecraft:sweet_berry_bush", "minecraft:big_dripleaf",
    "minecraft:small_dripleaf", "minecraft:spore_blossom", "minecraft:azalea", "minecraft:flowering_azalea",
    "minecraft:mangrove_roots", "minecraft:muddy_mangrove_roots",
    {"id": "#c:chests/wooden", "required": False}, {"id": "#c:barrels/wooden", "required": False},
    {"id": "#c:fences/wooden", "required": False}, {"id": "#c:fence_gates/wooden", "required": False},
]


def build_venus_hazard_data():
    data("tags/block/acid_rain_corrodes", {"values": ACID_CORRODES})
    for dmg, en, es in [
        ("venus_heat", "%1$s was cooked by the heat of Venus", "%1$s se coció con el calor de Venus"),
        ("acid_rain", "%1$s was dissolved by acid rain", "%1$s se disolvió con la lluvia ácida"),
    ]:
        data(f"damage_type/{dmg}", {"message_id": f"{NS}.{dmg}", "exhaustion": 0.0, "scaling": "never"})
        LANG_EN[f"death.attack.{NS}.{dmg}"] = en
        LANG_ES[f"death.attack.{NS}.{dmg}"] = es
        LANG_EN[f"death.attack.{NS}.{dmg}.player"] = en + " while fighting %2$s"
        LANG_ES[f"death.attack.{NS}.{dmg}.player"] = es + " mientras peleaba con %2$s"
    # el daño ya pasó por la armadura (se desgasta primero): que la armadura no lo reduzca otra vez
    data("tags/damage_type/bypasses_armor", {"replace": False, "values": [rl("venus_heat"), rl("acid_rain")]})
    data("tags/damage_type/no_knockback", {"replace": False, "values": [rl("venus_heat"), rl("acid_rain")]})
    W = f"message.{NS}.venus"
    for key, en, es in [
        ("armor_heat", "⚠ Your armor is breaking from the heat!", "⚠ ¡Tu armadura se está rompiendo por el calor!"),
        ("armor_acid", "⚠ Your armor is breaking from the acid rain!", "⚠ ¡Tu armadura se está rompiendo por la lluvia ácida!"),
        ("armor_both", "⚠ Your armor is breaking from the heat and the acid rain!",
         "⚠ ¡Tu armadura se está rompiendo por el calor y la lluvia ácida!"),
        ("hurt_heat", "⚠ The heat of Venus is burning you!", "⚠ ¡El calor de Venus te está quemando!"),
        ("hurt_acid", "⚠ The acid rain is burning you!", "⚠ ¡La lluvia ácida te está quemando!"),
        ("hurt_both", "⚠ The heat and the acid rain are burning you!", "⚠ ¡El calor y la lluvia ácida te están quemando!"),
    ]:
        LANG_EN[f"{W}.{key}"] = en
        LANG_ES[f"{W}.{key}"] = es
    # Hazard Armor: cuenta como traje antirradiación para Create Nuclear (logros)
    write("data/createnuclear/tags/item/anti_radiation_armor.json",
          {"replace": False, "values": [rl(f"hazard_{p}") for p in PIECES]})
    # OJO: NO en createnuclear:anti_radiation_helmet: esa etiqueta pone la vista de máscara de gas de Create Nuclear
    # (pantalla verde con el borde negro) y el usuario no la quiere (0.7.3).


# ==========================================================================
# LUNA de Cosmonautics — solo se cambia un campo (fusión por prioridad)
# ==========================================================================
def build_moon_fix():
    # OJO: Cosmonautics no fusiona campo a campo dentro de dimension_data: el bloque de
    # mayor prioridad sustituye al entero. Por eso se copia el bloque original de la Luna
    # (jar 26.08.307) cambiando solo render_universe_in_dimension.
    data("universe_planets/moon", {
        "name": "moon",
        "priority": 1000,
        "dimension_data": {
            "linked_dimension": "rocketnautics:moon",
            "allowed_transfer": "all",
            "dimension_transfer_height": 20000,
            "atmosphere_composition": {"21000": ["low_density", "drowning"]},
            "render_universe_in_dimension": True,  # 0.7.51: universo en el cielo de todos los planetas
            "dimension_day_time_controller_name": "sol",
            "apply_gravity_correction_to_entities_in_dimension": True,
            "entity_drag_multiplier": drag_curve(4000, 7000),
        },
    })


# ==========================================================================
# Sin mobs de la Tierra en los planetas
# ==========================================================================
PLANET_MOBS = ["lantern_fish", "vent_crab", "king_crab", "abyssal_stalker", "magma_snail", "magma_titan"]
CERULEA_MOBS = ["grazer", "hopper", "glider", "lurker", "croaker", "ribbonfin", "runner", "ironjaw"]   # 1.0.3


def build_no_earth_mobs():
    # Etiqueta con todos los biomas de este mod (solo los nuestros; la Tierra no se toca)
    data("tags/worldgen/biome/planet_biomes", {"values": [rl(b) for b in sorted(ALL_BIOMES)]})
    # Biome modifier de NeoForge: se aplica en la fase REMOVE, después de que los demás mods
    # añadan sus spawns, y borra cualquier tipo de entidad solo en nuestros biomas.
    data("neoforge/biome_modifier/no_earth_mobs", {
        "type": "neoforge:remove_spawns",
        "biomes": f"#{NS}:planet_biomes",
        # 0.7.61: todo menos las criaturas propias de los planetas (las de Thalassa)
        "entity_types": {"type": "neoforge:not", "value": [rl(m) for m in PLANET_MOBS + CERULEA_MOBS + ["rust_worm", "blind_stalker"]]},
    })


# ==========================================================================
# Clima: tipo de daño de la tormenta de arena
# ==========================================================================
WINDMILL_SAIL_POWER = {
    # quemadas: rinden menos
    "0.5": ["burnt:smoldering_sail_frame"],
    "0.75": ["burnt:burnt_sail", "burnt:burnt_symmetric_sail", "burnt:smoldering_sail", "burnt:smoldering_symmetric_sail"],
    "1": ["create_flight_control:copycat_integrated_sail_i", "createpropulsion:copycat_wing_8"],
    # velas de barco (Create Deep Seas), más grandes que las de Create
    "1.25": ["#create_high_seas:sails", "createpropulsion:copycat_wing_12"],
    # alas: perfil aerodinámico
    "1.5": ["createpropulsion:wing", "createpropulsion:copycat_wing", "create_flight_control:copycat_integrated_sail_ii",
            "alexsmobs:phantom_sail"],
    "2": ["createpropulsion:tempered_wing", "create_flight_control:copycat_integrated_sail_iii", "alexsmobs:spectre_sail"],
}

WIND_WHEELS = [
    # Offroad (incluido en Create Aeronautics)
    "offroad:wheel_mount",
    # Offroad Step Up
    "offroadstepup:centered_wheel_mount", "offroadstepup:compact_wheel_mount", "offroadstepup:lateral_wheel_mount",
    # Aero No Horizon: ruedas, tren de aterrizaje y orugas
    "aero_no_horizon:small_simple_wheel", "aero_no_horizon:simple_wheel", "aero_no_horizon:med_simple_wheel",
    "aero_no_horizon:large_simple_wheel", "aero_no_horizon:small_simple_wheel_part", "aero_no_horizon:simple_wheel_part",
    "aero_no_horizon:med_simple_wheel_part", "aero_no_horizon:large_simple_wheel_part", "aero_no_horizon:oleo_wheel",
    "aero_no_horizon:phys_track", "aero_no_horizon:med_phys_track", "aero_no_horizon:large_phys_track",
    "aero_no_horizon:suspension_track", "aero_no_horizon:med_suspension_track", "aero_no_horizon:large_suspension_track",
]


def build_weather_data():
    # Bloques que hacen que una estructura de Sable cuente como vehículo con ruedas para el viento.
    # required=false: si el mod no está instalado, se ignora sin error.
    data("tags/block/wind_wheels", {"values": [{"id": b, "required": False} for b in WIND_WHEELS]})
    # Patas de Let it Walk!: la estructura cuenta como apoyada en el suelo y el viento no la mueve (0.7.5)
    data("tags/block/wind_legs", {"values": [{"id": f"let_it_walk:{b}", "required": False} for b in
                                             ("walker_core", "leg_hinge", "leg_swivel", "leg_foot", "jet_foot", "climbing_foot")]})
    # Potencia de velas y alas en los molinos de Create (1 = vela de Create). Ver WindSails.java.
    # Los bloques que no aparecen aquí y están en create:windmill_sails valen 1 (velas de Create,
    # lana, velas simétricas de Simulated, terciopelo de Design n' Decor...).
    for power, blocks in WINDMILL_SAIL_POWER.items():
        data(f"tags/block/windmill_sail_power/{power}",
             {"values": [{"id": b, "required": False} for b in blocks]})
    data("damage_type/dust_storm", {
        "message_id": f"{NS}.dust_storm",
        "exhaustion": 0.1,
        "scaling": "never",
    })
    # 1.0.3: también en Ferra, así que sin "marciana"
    LANG_EN[f"death.attack.{NS}.dust_storm"] = "%1$s was shredded by a dust storm"
    LANG_EN[f"death.attack.{NS}.dust_storm.player"] = "%1$s was shredded by a dust storm while fighting %2$s"
    LANG_ES[f"death.attack.{NS}.dust_storm"] = "%1$s fue triturado por una tormenta de arena"
    LANG_ES[f"death.attack.{NS}.dust_storm.player"] = "%1$s fue triturado por una tormenta de arena mientras peleaba con %2$s"

    # Nombres de los cuerpos celestes en la mesa holográfica (clave = nombre del archivo de universe_planets)
    for body, en, es in [
        ("sol", "Sun", "Sol"), ("overworld", "Earth", "Tierra"), ("moon", "Moon", "Luna"),
        ("mars", "Mars", "Marte"), ("gas_giant", "Gas Giant", "Gigante gaseoso"),
        ("ice_world", "Ice World", "Mundo helado"), ("ignis", "Ignis", "Ignis"),
        ("thalassa", "Thalassa", "Thalassa"), ("venus", "Venus", "Venus"),
    ]:
        LANG_EN[f"hologram.{NS}.body.{body}"] = en
        LANG_ES[f"hologram.{NS}.body.{body}"] = es
    LANG_EN[f"hologram.{NS}.you"] = "You"
    LANG_ES[f"hologram.{NS}.you"] = "Tú"
    LANG_EN[f"hologram.{NS}.far"] = "(off scale)"
    LANG_ES[f"hologram.{NS}.far"] = "(fuera de escala)"


# ==========================================================================
# Metadatos del mod
# ==========================================================================

# --------------------------------------------------------------------------
# Armaduras (diseños de Blockbench en tools/designs/<nombre>.bbmodel)
# --------------------------------------------------------------------------
DESIGNS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "designs")

# Partes del modelo humano de Minecraft y su punto de giro (coordenadas de Minecraft).
# Acepta los nombres de la plantilla de GeckoLib (bipedX) y los nombres simples (head, body...).
ARMOR_PARTS = {
    "bipedhead": ("head", (0.0, 0.0, 0.0)), "head": ("head", (0.0, 0.0, 0.0)),
    "bipedbody": ("body", (0.0, 0.0, 0.0)), "body": ("body", (0.0, 0.0, 0.0)),
    "bipedrightarm": ("right_arm", (-5.0, 2.0, 0.0)), "right_arm": ("right_arm", (-5.0, 2.0, 0.0)),
    "bipedleftarm": ("left_arm", (5.0, 2.0, 0.0)), "left_arm": ("left_arm", (5.0, 2.0, 0.0)),
    "bipedrightleg": ("right_leg", (-1.9, 12.0, 0.0)), "right_leg": ("right_leg", (-1.9, 12.0, 0.0)),
    "bipedleftleg": ("left_leg", (1.9, 12.0, 0.0)), "left_leg": ("left_leg", (1.9, 12.0, 0.0)),
}
ARMOR_GROUP_PIECE = {
    "armorhead": "helmet", "armorbody": "chestplate", "armorrightarm": "chestplate", "armorleftarm": "chestplate",
    "armorrightleg": "leggings", "armorleftleg": "leggings", "armorrightboot": "boots", "armorleftboot": "boots",
}
DEFAULT_PIECE = {"head": "helmet", "body": "chestplate", "right_arm": "chestplate", "left_arm": "chestplate",
                 "right_leg": "leggings", "left_leg": "leggings"}
PIECES = ("helmet", "chestplate", "leggings", "boots")


def convert_bbmodel(path):
    """Blockbench (GeckoLib/Modded Entity) -> piezas de armadura en coordenadas de Minecraft.

    Blockbench: y hacia arriba, pies en y=0, x al revés que Minecraft. Minecraft: y hacia abajo desde y=24.
    Las rotaciones de cubo se convierten en sub-partes (Minecraft solo gira partes, no cubos).
    """
    bb = json.load(open(path, encoding="utf-8"))
    tex_w, tex_h = bb["resolution"]["width"], bb["resolution"]["height"]
    elements = {e["uuid"]: e for e in bb["elements"]}
    groups = {g["uuid"]: g for g in bb.get("groups", [])}
    pieces = {p: {} for p in PIECES}
    skipped = []

    def mc(v):
        return (-v[0], 24.0 - v[1], v[2])

    def walk(node, part, pivot, piece, ignored):
        if isinstance(node, str):
            e = elements.get(node)
            if e is None or e.get("type", "cube") != "cube":
                return
            if ignored or not e.get("export", True) or e["name"].lower() == "donttouch":
                return
            if part is None or piece is None:
                skipped.append(e["name"])
                return
            f, t = e["from"], e["to"]
            size = [t[i] - f[i] for i in range(3)]
            corner = (-t[0], 24.0 - t[1], f[2])  # esquina mínima en coordenadas de Minecraft
            rot = e.get("rotation", [0, 0, 0])
            box = {"uv": e.get("uv_offset", [0, 0]), "size": size,
                   "inflate": e.get("inflate", 0.0), "mirror": bool(e.get("mirror_uv", False))}
            nodes = pieces[piece].setdefault(part, [])
            if any(abs(r) > 1e-6 for r in rot):
                o = mc(e["origin"])
                box["min"] = [corner[i] - o[i] for i in range(3)]
                nodes.append({"offset": [o[i] - pivot[i] for i in range(3)],
                              "rotation": [math.radians(-rot[0]), math.radians(-rot[1]), math.radians(rot[2])],
                              "boxes": [box]})
            else:
                box["min"] = [corner[i] - pivot[i] for i in range(3)]
                if not nodes or nodes[0].get("rotation") != [0.0, 0.0, 0.0] or nodes[0].get("offset") != [0.0, 0.0, 0.0]:
                    nodes.insert(0, {"offset": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0], "boxes": []})
                nodes[0]["boxes"].append(box)
            return
        g = groups[node["uuid"]]
        name = g["name"].lower()
        if name in ARMOR_PARTS and part is None:
            part, pivot = ARMOR_PARTS[name]
            piece = DEFAULT_PIECE[part]
        elif name in ARMOR_GROUP_PIECE:
            piece = ARMOR_GROUP_PIECE[name]
        else:
            for p in PIECES:
                if name.startswith(p):
                    piece = p
            if name.startswith("reference") or name.startswith("donttouch"):
                ignored = True
        if any(abs(r) > 1e-6 for r in g.get("rotation", [0, 0, 0])) and name not in ARMOR_PARTS:
            raise SystemExit(f"{path}: el grupo {g['name']} está girado; gira los cubos, no los grupos")
        for child in node.get("children", []):
            walk(child, part, pivot, piece, ignored)

    for root in bb["outliner"]:
        walk(root, None, None, None, False)
    if skipped:
        print(f"  aviso: cubos fuera de las partes del cuerpo, ignorados: {skipped}")
    texture = None
    for t in bb.get("textures", []):
        if t.get("source", "").startswith("data:image/png;base64,"):
            texture = base64.b64decode(t["source"].split(",", 1)[1])
            break
    return {"texture": [tex_w, tex_h], "pieces": pieces}, texture


# Iconos provisionales 16x16 (se sustituyen cuando haya iconos dibujados en tools/designs/<item>.png)
ICON_SHAPES = {
    "helmet": [
        "................", "................", "....KKKKKKKK....", "...KMMMMMMMMK...",
        "..KMDDDDDDDDMK..", "..KDDDDDDDDDDK..", "..KOOOOOOOOOOK..", "..KOOOOOOOOOOK..",
        "..KDDDDDDDDDDK..", "..KDDD.KK.DDDK..", "..KDDK....KDDK..", "...KK......KK...",
        "................", "................", "................", "................"],
    "chestplate": [
        "................", "..KKKK....KKKK..", ".KMMMMKKKKMMMMK.", ".KMDDDLLLLDDDMK.",
        ".KDDDDLLLLDDDDK.", ".KKDDDDDDDDDDKK.", "..KDDOOOOOODDK..", "..KDDDDDDDDDDK..",
        "..KDDDDDDDDDDK..", "..KDDDDDDDDDDK..", "..KMMMMMMMMMMK..", "..KDDDDDDDDDDK..",
        "..KKKKKKKKKKKK..", "................", "................", "................"],
    "leggings": [
        "................", "...KKKKKKKKKK...", "...KOOOOOOOOK...", "...KDDDDDDDDK...",
        "...KDDDKKDDDK...", "...KDDDKKDDDK...", "...KDDLKKLDDK...", "...KDDLKKLDDK...",
        "...KDDDKKDDDK...", "...KDDDKKDDDK...", "...KMMMKKMMMK...", "...KDDDKKDDDK...",
        "...KKKKKKKKKK...", "................", "................", "................"],
    "boots": [
        "................", "................", "................", "................",
        "................", "..KKKK....KKKK..", "..KDDK....KDDK..", "..KDDK....KDDK..",
        "..KDLK....KDLK..", ".KKDDK...KKDDK..", ".KOODK...KOODK..", "KDDDDK..KDDDDK..",
        "KMMMMK..KMMMMK..", "KKKKKK..KKKKKK..", "................", "................"],
}
ICON_COLORS = {"K": (30, 30, 32, 255), "D": (53, 54, 55, 255), "M": (71, 75, 77, 255),
               "L": (186, 190, 193, 255), "O": (215, 99, 35, 255)}


def png_bytes(rows, colors):
    raw = b"".join(b"\x00" + b"".join(bytes(colors.get(ch, (0, 0, 0, 0))) for ch in row) for row in rows)

    def chunk(kind, data_):
        return struct.pack(">I", len(data_)) + kind + data_ + struct.pack(">I", zlib.crc32(kind + data_) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def write_bytes(rel, payload):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(payload)


ARMOR_SETS = {
    # nombre del diseño -> (prefijo de los objetos, nombres en, nombres es)
    "impact_armor": ("impact", "Impact", "de impacto"),
    "glacial_armor": ("glacial", "Glacial", "glacial"),
    "hazard_armor": ("hazard", "Hazard", "Hazard"),
    "sea_diver_armor": ("sea_diver", "Sea Diver", "Sea Diver"),
    # 0.7.64: traje de inicio y traje de Ignis (diseños de tools/designs/new_suits.py)
    "basic_armor": ("basic", "Basic Space", "espacial básico"),
    "thermal_armor": ("thermal", "Thermal", "térmico"),
}
# Nombres exactos (concordancia de género en español) de los trajes nuevos
ARMOR_NAMES = {
    "basic": {"helmet": ("Basic Space Helmet", "Casco espacial básico"), "chestplate": ("Basic Space Chestplate", "Pechera espacial básica"),
              "leggings": ("Basic Space Leggings", "Pantalones espaciales básicos"), "boots": ("Basic Space Boots", "Botas espaciales básicas")},
    "thermal": {"helmet": ("Thermal Helmet", "Casco térmico"), "chestplate": ("Thermal Chestplate", "Pechera térmica"),
                "leggings": ("Thermal Leggings", "Pantalones térmicos"), "boots": ("Thermal Boots", "Botas térmicas")},
}
# Colores de los iconos provisionales por traje (K borde, D base, M panel, L detalle, O visor)
SET_ICON_COLORS = {
    "hazard": {"K": (8, 9, 8, 255), "D": (30, 33, 29, 255), "M": (44, 48, 42, 255),
               "L": (232, 200, 40, 255), "O": (60, 200, 60, 255)},
    "sea_diver": {"K": (40, 24, 12, 255), "D": (176, 122, 58, 255), "M": (214, 162, 78, 255),
                  "L": (26, 44, 96, 255), "O": (40, 70, 140, 255)},
    "glacial": {"K": (88, 92, 98, 255), "D": (196, 199, 204, 255), "M": (220, 223, 228, 255),
                "L": (150, 160, 172, 255), "O": (25, 70, 150, 255)},
}
PIECE_NAMES = {"helmet": ("Helmet", "Casco"), "chestplate": ("Chestplate", "Pechera"),
               "leggings": ("Leggings", "Pantalones"), "boots": ("Boots", "Botas")}


def build_armor():
    for design, (prefix, en_word, es_word) in ARMOR_SETS.items():
        model, texture = convert_bbmodel(os.path.join(DESIGNS, design + ".bbmodel"))
        write(f"assets/{NS}/armor_models/{design}.json", model)
        if texture:
            write_bytes(f"assets/{NS}/textures/models/armor/{design}.png", texture)
        for piece in PIECES:
            item = f"{prefix}_{piece}"
            icon = os.path.join(DESIGNS, item + ".png")
            if os.path.exists(icon):
                write_bytes(f"assets/{NS}/textures/item/{item}.png", open(icon, "rb").read())
            else:
                write_bytes(f"assets/{NS}/textures/item/{item}.png",
                            png_bytes(ICON_SHAPES[piece], SET_ICON_COLORS.get(prefix, ICON_COLORS)))
            write(f"assets/{NS}/models/item/{item}.json",
                  {"parent": "minecraft:item/generated", "textures": {"layer0": rl(f"item/{item}")}})
            en, es = PIECE_NAMES[piece]
            LANG_EN[f"item.{NS}.{item}"] = f"{en_word} {en}"
            es_adj = es_word + "es" if es_word.endswith("l") and piece in ("leggings", "boots") else es_word
            LANG_ES[f"item.{NS}.{item}"] = f"{es} {es_adj}"
            if prefix in ARMOR_NAMES:
                LANG_EN[f"item.{NS}.{item}"], LANG_ES[f"item.{NS}.{item}"] = ARMOR_NAMES[prefix][piece]
        data(f"tags/item/{design}", {"values": [rl(f"{prefix}_{p}") for p in PIECES]})

    # Etiquetas de otros mods: la pechera es fuente de aire de Create (como el jetpack de Cosmonautics)
    prefixes = [p for p, _, _ in ARMOR_SETS.values()]
    write("data/create/tags/item/pressurized_air_sources.json",
          {"replace": False, "values": [{"id": rl(f"{p}_chestplate"), "required": False} for p in prefixes]})
    for tag, piece in (("head_armor", "helmet"), ("chest_armor", "chestplate"),
                       ("leg_armor", "leggings"), ("foot_armor", "boots")):
        write(f"data/minecraft/tags/item/{tag}.json", {"replace": False, "values": [rl(f"{p}_{piece}") for p in prefixes]})

    T = f"tooltip.{NS}.impact"
    LANG_EN[f"{T}.helmet"] = "Works like the Space Helmet: breathe in space with an air supply on your back."
    LANG_ES[f"{T}.helmet"] = "Funciona como el Casco espacial: respiras en el espacio con aire en la espalda."
    LANG_EN[f"{T}.chestplate"] = "Works like the Cosmonautics Jetpack: flight, lava fuel and air for the helmet."
    LANG_ES[f"{T}.chestplate"] = "Funciona como el Jetpack de Cosmonautics: vuelo, combustible de lava y aire para el casco."
    LANG_EN[f"{T}.leggings"] = "Works like the Copper Leg Thrusters."
    LANG_ES[f"{T}.leggings"] = "Funcionan como los Propulsores de pierna de cobre."
    LANG_EN[f"{T}.boots"] = "Works like the Copper Anchor Boots. Reduces fall damage by 60%."
    LANG_ES[f"{T}.boots"] = "Funcionan como las Botas ancla de cobre. Reducen el daño por caída un 60 %."
    # 0.7.64: la protección contra el fuego y la lava pasó al traje térmico
    LANG_EN[f"{T}.piece"] = "Each piece: -25% Mars dust storm damage, -10% explosion and projectile damage."
    LANG_ES[f"{T}.piece"] = "Cada pieza: -25 % de daño de las tormentas de arena de Marte y -10 % de daño de explosiones y proyectiles."
    LANG_EN[f"{T}.set"] = "Full set: immune to Mars dust storms."
    LANG_ES[f"{T}.set"] = "Traje completo: inmune a las tormentas de arena de Marte."

    T = f"tooltip.{NS}.glacial"
    LANG_EN[f"{T}.helmet"] = LANG_EN[f"tooltip.{NS}.impact.helmet"]
    LANG_ES[f"{T}.helmet"] = LANG_ES[f"tooltip.{NS}.impact.helmet"]
    LANG_EN[f"{T}.chestplate"] = LANG_EN[f"tooltip.{NS}.impact.chestplate"]
    LANG_ES[f"{T}.chestplate"] = LANG_ES[f"tooltip.{NS}.impact.chestplate"]
    LANG_EN[f"{T}.leggings"] = LANG_EN[f"tooltip.{NS}.impact.leggings"]
    LANG_ES[f"{T}.leggings"] = LANG_ES[f"tooltip.{NS}.impact.leggings"]
    LANG_EN[f"{T}.boots"] = "Works like the Copper Anchor Boots. Lets you walk on powder snow."
    LANG_ES[f"{T}.boots"] = "Funcionan como las Botas ancla de cobre. Permiten andar sobre la nieve polvo."
    LANG_EN[f"{T}.piece"] = "Each piece: -25% blizzard freezing, -15% freezing damage, -20% wind push."
    LANG_ES[f"{T}.piece"] = "Cada pieza: -25 % de congelación en las ventiscas, -15 % de daño por congelación y -20 % de empuje del viento."
    LANG_EN[f"{T}.set"] = "Full set: never freezes; storm wind pushes you only 20%."
    LANG_ES[f"{T}.set"] = "Traje completo: nunca te congelas y el viento de las tormentas solo te empuja un 20 %."

    T = f"tooltip.{NS}.hazard"
    for piece in ("helmet", "chestplate", "leggings", "boots"):
        LANG_EN[f"{T}.{piece}"] = LANG_EN[f"tooltip.{NS}.impact.{piece}"] if piece != "boots" else "Works like the Copper Anchor Boots."
        LANG_ES[f"{T}.{piece}"] = LANG_ES[f"tooltip.{NS}.impact.{piece}"] if piece != "boots" else "Funcionan como las Botas ancla de cobre."
    LANG_EN[f"{T}.piece"] = "Each piece: -25% Venus heat and acid rain, +25% radiation resistance (Create Nuclear)."
    LANG_ES[f"{T}.piece"] = "Cada pieza: -25 % de calor y lluvia ácida de Venus y +25 % de resistencia a la radiación (Create Nuclear)."
    LANG_EN[f"{T}.set"] = "Full set: immune to Venus heat, acid rain and radiation."
    LANG_ES[f"{T}.set"] = "Traje completo: inmune al calor y la lluvia ácida de Venus y a la radiación."


def build_new_suit_texts():
    """0.7.64: textos del traje espacial básico y del traje térmico."""
    for suit in ("basic", "thermal"):
        T = f"tooltip.{NS}.{suit}"
        for piece in ("helmet", "chestplate", "leggings", "boots"):
            LANG_EN[f"{T}.{piece}"] = LANG_EN[f"tooltip.{NS}.hazard.{piece}"]
            LANG_ES[f"{T}.{piece}"] = LANG_ES[f"tooltip.{NS}.hazard.{piece}"]
    LANG_EN[f"tooltip.{NS}.basic.piece"] = "No special protection. Upgrade it at a smithing table with a planet's upgrade template."
    LANG_ES[f"tooltip.{NS}.basic.piece"] = "Sin protecciones especiales. Mejóralo en la mesa de herrería con la plantilla de un planeta."
    LANG_EN[f"tooltip.{NS}.thermal.piece"] = "Each piece: -15% damage from fire, lava and magma blocks. The helmet lets you see inside lava."
    LANG_ES[f"tooltip.{NS}.thermal.piece"] = "Cada pieza: -15 % de daño por fuego, lava y bloques de magma. El casco deja ver dentro de la lava."
    LANG_EN[f"tooltip.{NS}.thermal.set"] = "Full set: immune to fire and lava, never stays burning, and halves the heat of Venus."
    LANG_ES[f"tooltip.{NS}.thermal.set"] = "Traje completo: inmune al fuego y la lava, no te quedas ardiendo y reduce a la mitad el calor de Venus."


def build_sea_diver_texts():
    T = f"tooltip.{NS}.sea_diver"
    for piece in ("helmet", "chestplate", "leggings"):
        LANG_EN[f"{T}.{piece}"] = LANG_EN[f"tooltip.{NS}.impact.{piece}"]
        LANG_ES[f"{T}.{piece}"] = LANG_ES[f"tooltip.{NS}.impact.{piece}"]
    LANG_EN[f"{T}.boots"] = "Light boots: they don't sink you like anchor boots, so you can swim."
    LANG_ES[f"{T}.boots"] = "Botas ligeras: no te hunden como las botas ancla, así que puedes nadar."
    LANG_EN[f"{T}.piece"] = "Each piece: +12.5% swim speed and the cold protection of the Glacial Armor."
    LANG_ES[f"{T}.piece"] = "Cada pieza: +12.5 % de velocidad de nado y la protección contra el frío de la Glacial."
    LANG_EN[f"{T}.set"] = "Full set: never freezes, swims 50% faster and withstands sea pressure down to 600 m (Create Deep Seas)."
    LANG_ES[f"{T}.set"] = "Traje completo: nunca te congelas, nadas un 50 % más rápido y aguantas la presión del mar hasta 600 m (Create Deep Seas)."


# Recetas de las armaduras, tal como las dejó el usuario en cofres del planeta helado (2026-11-06):
# Impact = láminas de hierro industrial (Create Deco), Glacial = láminas de hierro (Create).
# N lingote de netherita, T lámina de aleación de titanio, G vidrio tintado, H depósito de fluidos,
# Z tobera de titanio, S lámina (de cada traje).
ARMOR_RECIPES = {
    "helmet": ["NTN", "GGG", "S S"],
    "chestplate": ["N N", "THT", "SZS"],
}
ARMOR_SHEETS = {"impact": "createdeco:industrial_iron_sheet", "glacial": "create:iron_sheet",
                "hazard": f"{NS}:lead_sheet",
                "sea_diver": "create:brass_sheet"}  # 0.7.24: láminas de latón, como la escafandra  # lámina de plomo (prensa de Create + plomo de Create Nuclear)
ARMOR_KEYS = {"N": "minecraft:netherite_ingot", "T": "rocketnautics:titanium_alloy_sheet",
              "G": "minecraft:tinted_glass", "H": "create:fluid_tank", "Z": "rocketnautics:titanium_nozzle"}


def build_armor_recipes():
    """0.7.64: solo el traje espacial básico se fabrica en la mesa de trabajo (sin netherita: hierro y láminas de hierro).
    Los trajes de los planetas salen de mejorarlo en la mesa de herrería (build_suit_progression)."""
    pieces = {"helmet": ["ITI", "GGG", "S S"], "chestplate": ["I I", "THT", "SZS"],
              "leggings": ["ISI", "T T", "S S"], "boots": ["SIS", "T T"]}
    keys = dict(ARMOR_KEYS, I="minecraft:iron_ingot", S="create:iron_sheet")
    for piece, pattern in pieces.items():
        used = set("".join(pattern)) - {" "}
        data(f"recipe/basic_{piece}", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
                                       "key": {k: {"item": keys[k]} for k in sorted(used)},
                                       "result": {"id": rl(f"basic_{piece}"), "count": 1}})
    # Lámina de plomo (prensa de Create + plomo de Create Nuclear). Desde 0.7.64 ya no hace falta para la Hazard
    # (que sale de la plantilla de Venus), pero se mantiene: hay jugadores con láminas y es c:plates/lead para otros mods.
    write_bytes(f"assets/{NS}/textures/item/lead_sheet.png", open(os.path.join(DESIGNS, "lead_sheet.png"), "rb").read())
    write(f"assets/{NS}/models/item/lead_sheet.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rl("item/lead_sheet")}})
    LANG_EN[f"item.{NS}.lead_sheet"] = "Lead Sheet"
    LANG_ES[f"item.{NS}.lead_sheet"] = "Lámina de plomo"
    data("recipe/pressing/lead_sheet", {"type": "create:pressing", "ingredients": [{"tag": "c:ingots/lead"}],
                                        "results": [{"id": rl("lead_sheet")}]})
    write("data/c/tags/item/plates/lead.json", {"replace": False, "values": [rl("lead_sheet")]})


def build_inertial_dampener():
    """Amortiguador inercial (0.7.29): multibloque 3x3x3; ver dampener/InertialDampener.java."""
    for tex in ["casing", "drive_end", "drive_side", "coolant_port", "core",
                "casing_connected", "drive_end_connected", "drive_side_connected", "coolant_port_connected",
                "redstone_port", "redstone_port_connected"]:
        n = f"inertial_dampener_{tex}"
        write_bytes(f"assets/{NS}/textures/block/{n}.png", open(os.path.join(DESIGNS, f"{n}.png"), "rb").read())
    blocks = {
        "inertial_dampener_core": ("Inertial Dampener Core", "Núcleo del amortiguador inercial"),
        "inertial_dampener_casing": ("Inertial Dampener Casing", "Carcasa del amortiguador inercial"),
        "inertial_dampener_drive": ("Inertial Dampener Rotation Input", "Entrada de rotación del amortiguador"),
        "inertial_dampener_coolant_port": ("Inertial Dampener Coolant Port", "Puerto de refrigerante del amortiguador"),
        "inertial_dampener_redstone_port": ("Inertial Dampener Redstone Input", "Entrada de redstone del amortiguador"),
    }
    for name, (en, es) in blocks.items():
        if name == "inertial_dampener_drive":
            write(f"assets/{NS}/models/block/{name}.json", {
                "parent": "minecraft:block/cube_column",
                "textures": {"end": rl("block/inertial_dampener_drive_end"), "side": rl("block/inertial_dampener_drive_side"),
                             "particle": rl("block/inertial_dampener_casing")}})
            write(f"assets/{NS}/blockstates/{name}.json", {"variants": {
                "axis=y": {"model": rl(f"block/{name}")},
                "axis=x": {"model": rl(f"block/{name}"), "x": 90, "y": 90},
                "axis=z": {"model": rl(f"block/{name}"), "x": 90}}})
        else:
            write(f"assets/{NS}/models/block/{name}.json", {
                "parent": "minecraft:block/cube_all", "textures": {"all": rl(f"block/{name}")}})
            write(f"assets/{NS}/blockstates/{name}.json", {"variants": {"": {"model": rl(f"block/{name}")}}})
        write(f"assets/{NS}/models/item/{name}.json", {"parent": rl(f"block/{name}")})
        data(f"loot_table/blocks/{name}", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": rl(name)}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        tag_add("data/minecraft/tags/block/mineable/pickaxe.json", rl(name))
        tag_add("data/minecraft/tags/block/needs_diamond_tool.json", rl(name))
        tag_add("data/create/tags/block/wrench_pickup.json", rl(name))
        LANG_EN[f"block.{NS}.{name}"] = en
        LANG_ES[f"block.{NS}.{name}"] = es
    # Recetas: caras a propósito (el usuario lo pidió "muy caro de conseguir")
    data("recipe/inertial_dampener_core", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["NPN", "PSP", "NPN"],
        "key": {"N": {"item": "minecraft:netherite_ingot"}, "P": {"item": "create:precision_mechanism"},
                "S": {"item": "minecraft:nether_star"}},
        "result": {"id": rl("inertial_dampener_core"), "count": 1}})
    data("recipe/inertial_dampener_casing", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["BSB", "SRS", "BSB"],
        "key": {"B": {"item": "create:brass_sheet"}, "S": {"item": "create:sturdy_sheet"},
                "R": {"item": "create:railway_casing"}},
        "result": {"id": rl("inertial_dampener_casing"), "count": 4}})
    data("recipe/inertial_dampener_drive", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": rl("inertial_dampener_casing")}, {"item": "create:gearbox"},
                        {"item": "create:precision_mechanism"}],
        "result": {"id": rl("inertial_dampener_drive"), "count": 1}})
    data("recipe/inertial_dampener_coolant_port", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": rl("inertial_dampener_casing")}, {"item": "create:fluid_tank"},
                        {"item": "create:copper_sheet"}],
        "result": {"id": rl("inertial_dampener_coolant_port"), "count": 1}})
    data("recipe/inertial_dampener_redstone_port", {
        "type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": [{"item": rl("inertial_dampener_casing")}, {"item": "minecraft:redstone_block"},
                        {"item": "minecraft:comparator"}],
        "result": {"id": rl("inertial_dampener_redstone_port"), "count": 1}})
    m = f"message.{NS}."
    for key, en, es in [
            ("dampener_no_redstone", "Needs a Redstone Input to be fired", "Necesita una entrada de redstone para activarse"),
            ("dampener_incomplete", "Inertial dampener incomplete: %s/26 casing blocks around the core",
             "Amortiguador inercial incompleto: %s/26 bloques de carcasa alrededor del núcleo"),
            ("dampener_not_charged", "Inertial dampener not charged yet (%s%%)", "El amortiguador inercial aún no está cargado (%s%%)"),
            ("dampener_cooling", "Inertial dampener still cooling down (heat %s%%)", "El amortiguador inercial aún se está enfriando (calor %s%%)"),
            ("dampener_no_ship", "The inertial dampener only works on a ship", "El amortiguador inercial solo funciona en una nave"),
            ("dampener_no_orbit", "Inertial dampener: no orbit to stabilize", "Amortiguador inercial: no hay órbita que estabilizar"),
            ("dampener_stopped", "Inertial dampener: ship stopped", "Amortiguador inercial: nave detenida"),
            ("dampener_orbit", "Inertial dampener: circular orbit at %s km", "Amortiguador inercial: órbita circular a %s km"),
            ("dampener_structure", "Structure incomplete: %s/26 casing blocks", "Estructura incompleta: %s/26 bloques de carcasa"),
            ("dampener_no_drive", "Needs a Rotation Input to charge", "Necesita una entrada de rotación para cargarse"),
            ("dampener_charge", "Charge: %s%%", "Carga: %s%%"),
            ("dampener_heat", "Heat: %s%%", "Calor: %s%%"),
            ("dampener_coolant", "Coolant: %s (%s/%s mB)", "Refrigerante: %s (%s/%s mB)"),
            ("dampener_coolant_none", "No coolant: air cooling (slow)", "Sin refrigerante: enfriamiento por aire (lento)"),
            ("dampener_ready", "Ready: send a redstone signal", "Listo: envía una señal de redstone")]:
        LANG_EN[m + key] = en
        LANG_ES[m + key] = es


def build_gravity_generator():
    """Generador de gravedad (0.7.17): bloque cinético de Create; ver gravity/GravityGenerator.java."""
    name = "gravity_generator"
    # 0.7.34: modelo 3D de 1x2 (petición del usuario). La textura y los modelos salen de
    # tools/designs/gravity_generator_model.py --export; el núcleo es un modelo suelto que dibuja el cliente.
    write_bytes(f"assets/{NS}/textures/block/{name}_3d.png", open(os.path.join(DESIGNS, f"{name}_3d.png"), "rb").read())
    for model, dest in [("block", f"block/{name}"), ("core", f"block/{name}_core"), ("item", f"item/{name}")]:
        with open(os.path.join(DESIGNS, f"{name}_{model}.json"), encoding="utf-8") as f:
            write(f"assets/{NS}/models/{dest}.json", json.load(f))
    write(f"assets/{NS}/blockstates/{name}.json", {"variants": {
        "axis=x": {"model": rl(f"block/{name}")},
        "axis=z": {"model": rl(f"block/{name}"), "y": 90}}})
    # parte de arriba: invisible (solo partículas al romperla)
    write(f"assets/{NS}/models/block/{name}_top.json", {"textures": {"particle": "create:block/brass_casing"}})
    write(f"assets/{NS}/blockstates/{name}_top.json", {"variants": {"": {"model": rl(f"block/{name}_top")}}})
    tag_add("data/minecraft/tags/block/mineable/pickaxe.json", rl(f"{name}_top"))
    LANG_EN[f"block.{NS}.{name}_top"] = "Gravity Generator"
    LANG_ES[f"block.{NS}.{name}_top"] = "Generador de gravedad"
    data(f"loot_table/blocks/{name}", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": rl(name)}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    tag_add("data/minecraft/tags/block/mineable/pickaxe.json", rl(name))
    tag_add("data/create/tags/block/wrench_pickup.json", rl(name))
    data(f"recipe/{name}", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["BPB", "SCS", "BEB"],
        "key": {"B": {"item": "create:brass_sheet"}, "P": {"item": "create:precision_mechanism"},
                "S": {"item": "create:shaft"}, "C": {"item": "create:brass_casing"},
                "E": {"item": "minecraft:ender_eye"}},
        "result": {"id": rl(name), "count": 1}})
    LANG_EN[f"block.{NS}.{name}"] = "Gravity Generator"
    LANG_ES[f"block.{NS}.{name}"] = "Generador de gravedad"
    LANG_EN[f"message.{NS}.gravity_on"] = "Generating gravity on this ship"
    LANG_ES[f"message.{NS}.gravity_on"] = "Generando gravedad en esta nave"
    LANG_EN[f"message.{NS}.gravity_off"] = "Not enough rotation: no gravity"
    LANG_ES[f"message.{NS}.gravity_off"] = "Sin rotación suficiente: sin gravedad"
    LANG_EN[f"message.{NS}.gravity_no_signal"] = "Off: needs a redstone signal"
    LANG_ES[f"message.{NS}.gravity_no_signal"] = "Apagado: necesita señal de redstone"
    # Lista de mods de NeoForge (nombre y descripción traducibles)
    LANG_EN[f"fml.menu.mods.info.displayname.{NS}"] = "Cosmonautics: Planetary Expansion"
    LANG_ES[f"fml.menu.mods.info.displayname.{NS}"] = "Cosmonautics: Expansión Planetaria"
    LANG_EN[f"fml.menu.mods.info.description.{NS}"] = ("Landable planets for Create: Cosmonautics: Mars, the Ice World, Ignis, Thalassa and Venus, "
                                                      "each with its own dimension, terrain and weather. Also adds planet suits, the Gravity Generator, the Inertial Dampener and the Warp Drive.")
    LANG_ES[f"fml.menu.mods.info.description.{NS}"] = ("Planetas aterrizables para Create: Cosmonautics: Marte, el Mundo helado, Ignis, Thalassa y Venus, "
                                                      "cada uno con su dimensión, terreno y clima. Además añade trajes planetarios, el Generador de gravedad, el Amortiguador inercial y el Motor de distorsión.")
    # Tormentas y comando /planet_weather (weather/StormType.java, StormManager.java)
    for key, en, es in [("dust_storm", "Dust storm", "Tormenta de arena"), ("blizzard", "Blizzard", "Ventisca"),
                        ("thalassa_blizzard", "Blizzard", "Ventisca"), ("acid_rain", "Acid rain", "Lluvia ácida")]:
        LANG_EN[f"storm.{NS}.{key}"] = en
        LANG_ES[f"storm.{NS}.{key}"] = es
    cmd = f"command.{NS}.weather"
    for key, en, es in [
            ("no_storms", "This planet has no storms: %s", "Este planeta no tiene tormentas: %s"),
            ("severe", "(severe)", "(fuerte)"), ("normal", "(normal)", "(normal)"),
            ("started", "%1$s %2$s started on %3$s%4$s", "%1$s %2$s iniciada en %3$s%4$s"),
            ("stopping", "%1$s dying down on %2$s", "%1$s disipándose en %2$s"),
            ("status", "%1$s%2$s [%3$s]: %4$s%5$s, intensity %6$s%%", "%1$s%2$s [%3$s]: %4$s%5$s, intensidad %6$s%%"),
            ("phase.calm", "calm, next storm in", "calma, próxima tormenta en"),
            ("phase.rising", "starting", "empezando"),
            ("phase.storm", "in progress, ends in", "en curso, termina en"),
            ("phase.falling", "ending", "terminando")]:
        LANG_EN[f"{cmd}.{key}"] = en
        LANG_ES[f"{cmd}.{key}"] = es
    # Navegación espacial con el HUD del casco (client/SpaceNavOverlay.java)
    LANG_EN[f"hud.{NS}.prograde"] = "Prograde"
    LANG_ES[f"hud.{NS}.prograde"] = "Prógrado"
    LANG_EN[f"hud.{NS}.retrograde"] = "Retrograde"
    LANG_ES[f"hud.{NS}.retrograde"] = "Retrógrado"
    LANG_EN[f"hud.{NS}.thrust"] = "Thrust %s m/s²"
    LANG_ES[f"hud.{NS}.thrust"] = "Empuje %s m/s²"
    # Nave congelada al desconectarse (weather/ShipFreeze.java)
    LANG_EN[f"message.{NS}.ship_unfreeze"] = "Ship frozen while you were away: releasing in %s..."
    LANG_ES[f"message.{NS}.ship_unfreeze"] = "Nave congelada mientras no estabas: se suelta en %s..."
    LANG_EN[f"message.{NS}.ship_released"] = "Ship released"
    LANG_ES[f"message.{NS}.ship_released"] = "Nave liberada"


def build_warp():
    """Motor de distorsión y consola de navegación (0.7.37); ver warp/Warp.java.
    Modelos y texturas: tools/designs/jump_drive_model.py --export (el motor va a media escala, el cliente lo dibuja
    al doble)."""
    for tex in ["warp_drive", "warp_drive_item", "nav_console", "nav_console_item"]:
        write_bytes(f"assets/{NS}/textures/block/{tex}.png", open(os.path.join(DESIGNS, f"{tex}.png"), "rb").read())
    write_bytes(f"assets/{NS}/textures/block/warp_drive_stellar.png", open(os.path.join(DESIGNS, "warp_drive_stellar.png"), "rb").read())
    parts = ["warp_drive_frame", "warp_drive_glow", "warp_drive_glass", "warp_drive_fluid", "warp_drive_core",
             "warp_drive_frame_stellar", "warp_drive_core_stellar", "warp_drive_shards", "nav_console_table", "nav_console_glow",
             "nav_console_rings"] + [f"nav_console_body_{b}" for b in
                                     ["sun", "venus", "earth", "mars", "giant", "ice", "thalassa", "ignis"]]
    for m in parts:
        with open(os.path.join(DESIGNS, f"{m}.json"), encoding="utf-8") as f:
            write(f"assets/{NS}/models/block/{m}.json", json.load(f))
    for item, src in [("warp_drive", "warp_drive_item"), ("nav_console", "nav_console_item")]:
        with open(os.path.join(DESIGNS, f"{src}.json"), encoding="utf-8") as f:
            model = json.load(f)
        model = {"parent": "minecraft:block/block", **model}   # posiciones de mano/inventario de un bloque normal
        write(f"assets/{NS}/models/item/{item}.json", model)
    particles = {"warp_drive": "minecraft:block/amethyst_block", "warp_drive_part": "minecraft:block/amethyst_block",
                 "warp_drive_input": "minecraft:block/amethyst_block", "nav_console": "create:block/brass_casing",
                 "nav_console_part": "create:block/brass_casing"}
    for name, particle in particles.items():
        # bloques invisibles: el modelo 3D lo dibuja el BlockEntityRenderer del ancla
        write(f"assets/{NS}/models/block/{name}.json", {"textures": {"particle": particle}})
        write(f"assets/{NS}/blockstates/{name}.json", {"variants": {"": {"model": rl(f"block/{name}")}}})
        tag_add("data/minecraft/tags/block/mineable/pickaxe.json", rl(name))
        tag_add("data/minecraft/tags/block/needs_diamond_tool.json", rl(name))
    for name in ["warp_drive", "nav_console"]:
        data(f"loot_table/blocks/{name}", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": rl(name)}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    names = {"warp_drive": ("Warp Drive", "Motor de distorsión"), "nav_console": ("Navigation Console", "Consola de navegación"),
             "warp_drive_part": ("Warp Drive", "Motor de distorsión"), "warp_drive_input": ("Warp Drive Shaft Input", "Entrada de eje del motor de distorsión"),
             "nav_console_part": ("Navigation Console", "Consola de navegación")}
    for name, (en, es) in names.items():
        LANG_EN[f"block.{NS}.{name}"] = en
        LANG_ES[f"block.{NS}.{name}"] = es
    # Recetas: de final de juego (el usuario lo pidió "muy caro")
    data("recipe/warp_drive", {
        "type": "create:mechanical_crafting", "accept_mirrored": False, "category": "misc",
        "key": {"A": {"item": "rocketnautics:titanium_alloy_sheet"}, "N": {"item": "minecraft:netherite_ingot"},
                "T": {"item": "create:fluid_tank"}, "P": {"item": "create:precision_mechanism"},
                "S": {"item": "minecraft:nether_star"}, "E": {"item": "minecraft:end_crystal"}},
        "pattern": ["ANTNA", "NPSPN", "TSEST", "NPSPN", "ANTNA"],
        "result": {"count": 1, "id": rl("warp_drive")}, "show_notification": True})
    data("recipe/nav_console", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["PHP", "NBN", "TET"],
        "key": {"P": {"item": "create:precision_mechanism"}, "H": {"item": "rocketnautics:hologram_block"},
                "N": {"item": "minecraft:netherite_ingot"}, "B": {"item": "create:brass_casing"},
                "T": {"item": "rocketnautics:titanium_alloy_sheet"}, "E": {"item": "minecraft:ender_eye"}},
        "result": {"id": rl("nav_console"), "count": 1}})

    # 1.0.3 motor estelar: núcleo de distorsión estelar (un objeto de cada astro del sistema solar) y cristal de helio-3
    # (Luna). Texturas: tools/designs/stellar_items.py
    for item, en, es in [("helium3_crystal", "Helium-3 Crystal", "Cristal de helio-3"),
                         ("stellar_warp_core", "Stellar Warp Core", "Núcleo de distorsión estelar")]:
        write_bytes(f"assets/{NS}/textures/item/{item}.png", open(os.path.join(DESIGNS, "stellar", f"{item}.png"), "rb").read())
        write(f"assets/{NS}/models/item/{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rl(f"item/{item}")}})
        LANG_EN[f"item.{NS}.{item}"] = en
        LANG_ES[f"item.{NS}.{item}"] = es
    LANG_EN[f"item.{NS}.stellar_warp_core.tooltip"] = "Right-click a Warp Drive to let it jump between stars"
    LANG_ES[f"item.{NS}.stellar_warp_core.tooltip"] = "Clic derecho sobre un motor de distorsión para que pueda saltar entre estrellas"
    data("recipe/stellar_warp_core", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        # Tierra (estrella del Nether y netherita), Luna, Marte, Venus, mundo helado, Thalassa e Ignis
        "pattern": ["HNM", "VSC", "KNI"],
        "key": {"H": {"item": rl("helium3_crystal")}, "N": {"item": "minecraft:netherite_ingot"},
                "M": {"item": rl("meteorite_fragment")}, "V": {"item": rl("venusian_sulfur")},
                "S": {"item": "minecraft:nether_star"}, "C": {"item": rl("cryo_crystal")},
                "K": {"item": rl("king_shell")}, "I": {"item": rl("magma_core")}},
        "result": {"id": rl("stellar_warp_core"), "count": 1}, "show_notification": True})
    # el regolito lunar suelto no tenía molienda en Cosmonautics: Luna -> regolito -> fragmentado -> suelto -> helio-3
    data("recipe/crushing/helium3_from_lunar_loose_regolith", {
        "type": "create:crushing", "ingredients": [{"item": "rocketnautics:lunar_loose_regolith"}], "processing_time": 300,
        "results": [{"chance": 0.15, "id": rl("helium3_crystal")}, {"chance": 0.5, "id": "minecraft:sand"},
                    {"chance": 0.25, "id": "create:experience_nugget"}]})

    m = f"message.{NS}."
    for key, en, es in [
            ("warp_summary", "Warp drive: charge %s%% · heat %s%% · nitrogen %s/%s mB",
             "Motor de distorsión: carga %s%% · calor %s%% · nitrógeno %s/%s mB"),
            ("warp_charge", "Charge: %s%%", "Carga: %s%%"),
            ("warp_heat", "Heat: %s%%", "Calor: %s%%"),
            ("warp_nitrogen", "Liquid nitrogen: %s/%s mB", "Nitrógeno líquido: %s/%s mB"),
            ("warp_needs_coolant", "Can't cool down without liquid nitrogen", "No se puede enfriar sin nitrógeno líquido"),
            ("warp_ready", "Ready: choose a planet on the Navigation Console", "Listo: elige un planeta en la consola de navegación"),
            ("warp_not_ready", "Warp drive not ready: %s", "El motor de distorsión no está listo: %s"),
            ("warp_bad_target", "That body can't be reached with the warp drive", "No se puede llegar a ese astro con el motor de distorsión"),
            ("warp_already_there", "You are already orbiting it", "Ya lo estás orbitando"),
            # 1.0.3 motor estelar
            ("warp_upgraded", "Stellar Warp Core installed: this drive can now jump between stars",
             "Núcleo estelar instalado: este motor ya puede saltar entre estrellas"),
            ("warp_already_stellar", "This warp drive already has a Stellar Warp Core", "Este motor ya tiene un núcleo de distorsión estelar"),
            ("warp_needs_stellar", "Jumping to another star system needs a Stellar Warp Drive",
             "Saltar a otro sistema solar requiere un motor de distorsión estelar"),
            ("warp_needs_overcharge", "An interstellar jump needs 200%% charge (now %s%%)",
             "Un salto interestelar necesita 200%% de carga (ahora %s%%)"),
            ("warp_needs_n2_interstellar", "An interstellar jump needs %s mB of liquid nitrogen",
             "Un salto interestelar necesita %s mB de nitrógeno líquido"),
            ("warp_ready_interstellar", "Overcharged: ready for an interstellar jump", "Sobrecargado: listo para un salto interestelar"),
            ("warp_stellar_name", "Stellar Warp Drive", "Motor de distorsión estelar")]:
        LANG_EN[m + key] = en
        LANG_ES[m + key] = es
    n = f"nav.{NS}."
    for key, en, es in [
            ("title", "Navigation Console", "Consola de navegación"),
            ("hint", "Scroll: zoom · Drag: move · Right-click a planet: options",
             "Rueda: zoom · Arrastrar: mover · Clic derecho en un planeta: opciones"),
            ("panel_hint", "Hover over a planet to see its information. Left-click pins it here; right-click opens the travel menu.",
             "Pon el ratón sobre un planeta para ver su información. Con clic izquierdo se queda fija aquí; con clic derecho se abre el menú de viaje."),
            ("no_data", "No navigation data yet", "Aún no hay datos de navegación"),
            ("center_sun", "Star", "Estrella"), ("center_me", "My position", "Mi posición"),
            ("system", "System: %s", "Sistema: %s"),
            ("travel", "Travel (orbit)", "Viajar (orbitar)"), ("read", "Read description", "Leer descripción"),
            ("travel_interstellar", "Interstellar jump (orbit)", "Salto interestelar (orbitar)"),
            ("drive_stellar", "Stellar warp drive:", "Motor estelar:"),
            ("arrived_system", "%s system", "Sistema %s"),
            ("blocked_sun", "The warp drive can't orbit a star", "El motor de distorsión no puede orbitar una estrella"),
            ("orbit.title", "Orbit: %s", "Órbita: %s"),
            ("orbit.altitude", "Altitude: %s km", "Altitud: %s km"),
            ("orbit.radii", "%s planet radii from the center", "%s radios del astro desde el centro"),
            ("orbit.period", "Orbital period: %s", "Periodo orbital: %s"),
            ("orbit.speed", "Orbital speed: %s km/s", "Velocidad orbital: %s km/s"),
            ("orbit.range", "Allowed: %s to %s km", "Permitido: de %s a %s km"),
            ("orbit.closest", "Closest", "Más cerca"), ("orbit.default", "Default", "Predeterminada"),
            ("orbit.farthest", "Farthest", "Más lejos"),
            ("orbit.go", "Orbit", "Orbitar"), ("orbit.go_interstellar", "Jump and orbit", "Saltar y orbitar"),
            ("orbit.cancel", "Cancel", "Cancelar"),
            ("orbit.hint", "Drag the slider (or scroll over it) to choose how far to orbit.",
             "Arrastra el control (o usa la rueda encima) para elegir a qué distancia orbitar."),
            ("blocked_here", "You are already here", "Ya estás aquí"),
            ("you_here", "You are here", "Estás aquí"), ("orbiting_here", "You are orbiting here", "Estás en su órbita"),
            ("status_wait", "Contacting the warp drive...", "Contactando con el motor de distorsión..."),
            ("drive", "Warp drive:", "Motor de distorsión:"),
            ("charge", "Charge %s%%", "Carga %s%%"), ("heat", "Heat %s%%", "Calor %s%%"),
            ("nitrogen", "Nitrogen %s/%s mB", "Nitrógeno %s/%s mB"), ("rpm", "%s RPM", "%s RPM"),
            ("arrived", "Orbit: %s km", "Órbita: %s km"),
            ("label.gravity", "Gravity", "Gravedad"), ("label.radius", "Radius", "Radio"),
            ("label.air", "Air", "Aire"), ("label.temp", "Temperature", "Temperatura"),
            ("label.hazards", "Hazards", "Peligros"), ("label.armor", "Recommended armor", "Armadura recomendada"),
            ("label.resources", "Resources", "Recursos"), ("label.distance", "Distance", "Distancia"),
            ("status.ready", "Ready", "Listo"), ("status.no_drive", "No warp drive on this ship", "No hay motor de distorsión en esta nave"),
            ("status.jumping", "Jumping...", "Saltando..."), ("status.cooling", "Cooling down", "Enfriándose"),
            ("status.no_coolant", "Overheated: needs liquid nitrogen to cool down", "Sobrecalentado: necesita nitrógeno líquido para enfriarse"),
            ("status.no_rotation", "No rotation: can't charge", "Sin rotación: no se carga"),
            ("status.charging", "Charging", "Cargando"),
            ("status.needs_nitrogen", "Needs 4000 mB of liquid nitrogen", "Necesita 4000 mB de nitrógeno líquido"),
            ("status.not_on_ship", "Must be built on a ship", "Tiene que estar montado en una nave"),
            ("status.not_in_space", "Only works in deep space", "Solo funciona en el espacio profundo")]:
        LANG_EN[n + key] = en
        LANG_ES[n + key] = es
    # Fichas de los astros (la gravedad, el radio y la distancia los calcula el juego)
    bodies = {
        "sol": {"type": ("Star", "Estrella"),
                "desc": ("The center of the system. Every planet orbits it.", "El centro del sistema. Todos los planetas giran a su alrededor.")},
        "overworld": {"type": ("Home planet", "Planeta natal"),
                      "desc": ("Earth: where every journey begins.", "La Tierra: donde empieza todo viaje."),
                      "air": ("Breathable", "Respirable"), "temp": ("Mild", "Templada"), "hazards": ("None", "Ninguno"),
                      "armor": ("Not needed", "No hace falta"), "resources": ("Everything in the Overworld", "Todo lo del mundo normal")},
        "moon": {"type": ("Moon of Earth", "Luna de la Tierra"),
                 "desc": ("Grey dusty plains and craters, close to home.", "Llanuras grises de polvo y cráteres, cerca de casa."),
                 "air": ("None", "Ninguno"), "temp": ("Cold", "Fría"), "hazards": ("No air", "Sin aire"),
                 "armor": ("A space suit", "Un traje espacial"), "resources": ("Lunar regolith", "Regolito lunar")},
        "venus": {"type": ("Rocky planet", "Planeta rocoso"),
                  "desc": ("A scorching world under amber clouds, with basalt plains, shield volcanoes and rivers of lava.",
                           "Un mundo abrasador bajo nubes ámbar, con llanuras de basalto, volcanes en escudo y ríos de lava."),
                  "air": ("Toxic, not breathable", "Tóxico, irrespirable"), "temp": ("Scorching", "Abrasadora"),
                  "hazards": ("Heat, acid rain, lava", "Calor, lluvia ácida, lava"), "armor": ("Hazard Armor", "Hazard Armor"),
                  "resources": ("Uranium, thorium, lead and nitrate (Create Nuclear), iron, copper, gold",
                                "Uranio, torio, plomo y nitrato (Create Nuclear), hierro, cobre, oro")},
        "mars": {"type": ("Rocky planet", "Planeta rocoso"),
                 "desc": ("A cold red desert with canyons, polar caps and violent dust storms.",
                          "Un desierto rojo y frío con cañones, casquetes polares y violentas tormentas de arena."),
                 "air": ("Thin, not breathable", "Fino, irrespirable"), "temp": ("Cold", "Fría"),
                 "hazards": ("Dust storms", "Tormentas de arena"), "armor": ("Impact Armor", "Impact Armor"),
                 "resources": ("Zinc, iron and huge crimsite and ochrum veins", "Zinc, hierro y vetas enormes de crimsita y ocrum")},
        "gas_giant": {"type": ("Gas giant", "Gigante gaseoso"),
                      "desc": ("No surface to land on, but two moons orbit it: Ignis and Thalassa.",
                               "No tiene superficie donde aterrizar, pero lo orbitan dos lunas: Ignis y Thalassa."),
                      "hazards": ("No surface", "Sin superficie")},
        "ignis": {"type": ("Volcanic moon", "Luna volcánica"),
                  "desc": ("A moon of blackstone and basalt orbiting the gas giant.", "Una luna de piedra negra y basalto que orbita el gigante gaseoso."),
                  "air": ("None", "Ninguno"), "temp": ("Hot", "Caliente"), "hazards": ("No air, lava", "Sin aire, lava"),
                  "armor": ("Thermal Suit", "Traje térmico"),   # desde 0.7.64 el calor y la lava son del traje térmico
                  "resources": ("Gold, gilded blackstone, copper and huge ochrum and veridium veins",
                                "Oro, piedra negra dorada, cobre y vetas enormes de ocrum y veridio")},
        "thalassa": {"type": ("Ocean moon", "Luna oceánica"),
                     "desc": ("A frozen crust over a deep ocean with huge underwater caves and hydrothermal vents.",
                              "Una corteza helada sobre un océano profundo con enormes cuevas submarinas y chimeneas hidrotermales."),
                     "air": ("Only a thin layer just above the ice", "Solo una capa fina justo encima del hielo"),
                     "temp": ("Freezing", "Helada"), "hazards": ("Blizzards, deep-sea pressure", "Ventiscas, presión del fondo del mar"),
                     "armor": ("Sea Diver Armor", "Sea Diver Armor"), "resources": ("Titanium, iron and diamond", "Titanio, hierro y diamante")},
        "ice_world": {"type": ("Ice planet", "Planeta helado"),
                      "desc": ("The farthest planet: endless ice fields swept by blizzards, under an eternal night.",
                               "El planeta más lejano: campos de hielo sin fin barridos por ventiscas, bajo una noche eterna."),
                      "air": ("Breathable near the surface", "Respirable cerca de la superficie"), "temp": ("Freezing", "Helada"),
                      "hazards": ("Blizzards, strong gravity", "Ventiscas, gravedad fuerte"), "armor": ("Glacial Armor", "Glacial Armor"),
                      "resources": ("Huge asurine veins, zinc and diamond", "Vetas enormes de asurina, zinc y diamante")},
    }
    for body, fields in bodies.items():
        for field, (en, es) in fields.items():
            LANG_EN[f"{n}body.{body}.{field}"] = en
            LANG_ES[f"{n}body.{body}.{field}"] = es


def build_advancements():
    """Logros (0.7.41): pestaña "Planetary Expansion". Vista previa aprobada por el usuario; "Space Race" y
    "One Small Step" son ideas suyas. Los que dependen de algo que no ve Minecraft (tormentas, máquinas, saltos) usan
    minecraft:impossible y los da el código (advancement/Achievements.java)."""
    import itertools
    from PIL import Image
    import random as _r
    # fondo de la pestaña: cielo estrellado de 16x16
    rnd = _r.Random(3)
    tile = Image.new("RGBA", (16, 16), (8, 9, 18, 255))
    for y in range(16):
        for x in range(16):
            v = rnd.randint(0, 6)
            tile.putpixel((x, y), (8 + v, 9 + v, 20 + v, 255))
    for _ in range(5):
        x, y = rnd.randrange(16), rnd.randrange(16)
        b = rnd.randint(140, 230)
        tile.putpixel((x, y), (b, b, min(255, b + 20), 255))
    import io
    buf = io.BytesIO(); tile.save(buf, "PNG")
    write_bytes(f"assets/{NS}/textures/gui/advancements/backgrounds/space.png", buf.getvalue())

    def dim(to, frm=None):
        c = {"to": to}
        if frm: c["from"] = frm
        return {"trigger": "minecraft:changed_dimension", "conditions": c}

    def where(dim_id, **pred):
        loc = {"dimension": dim_id}
        if "max_y" in pred: loc["position"] = {"y": {"max": pred["max_y"]}}
        predicate = {"location": loc}
        if "head" in pred: predicate["equipment"] = {"head": {"items": pred["head"]}}
        return {"trigger": "minecraft:location", "conditions": {"player": [{
            "condition": "minecraft:entity_properties", "entity": "this", "predicate": predicate}]}}

    def suit(prefix):
        eq = {slot: {"items": f"{NS}:{prefix}_{piece}"} for slot, piece in
              [("head", "helmet"), ("chest", "chestplate"), ("legs", "leggings"), ("feet", "boots")]}
        return {"trigger": "minecraft:location", "conditions": {"player": [{
            "condition": "minecraft:entity_properties", "entity": "this", "predicate": {"equipment": eq}}]}}

    def placed(block_id):
        return {"trigger": "minecraft:placed_block", "conditions": {"location": [
            {"condition": "minecraft:block_state_property", "block": block_id}]}}

    IMPOSSIBLE = {"trigger": "minecraft:impossible"}
    SUITS = ["impact", "glacial", "hazard", "sea_diver", "thermal"]
    PLANETS = {"moon": "rocketnautics:moon", "mars": f"{NS}:mars", "venus": f"{NS}:venus", "ice_world": f"{NS}:ice_world",
               "ignis": f"{NS}:ignis", "thalassa": f"{NS}:thalassa",
               # 1.0.4: los mundos del sistema de Ámbar y Titán (Saturno y Aurelia no tienen superficie)
               "cerulea": f"{NS}:cerulea", "velo": f"{NS}:velo", "ferra": f"{NS}:ferra", "titan": f"{NS}:titan",
               "fulgor": f"{NS}:fulgor"}
    # destinos del logro "Viajero interplanetario": los ocho originales y los cuerpos nuevos de 1.0.4
    JUMP_TARGETS_OLD = ["overworld", "moon", "venus", "mars", "gas_giant", "ice_world", "ignis", "thalassa"]
    JUMP_TARGETS = JUMP_TARGETS_OLD + ["saturn", "titan", "cerulea", "velo", "ferra", "aurelia", "fulgor"]

    # (id, padre, icono, marco, criterios, requisitos o None, xp, EN título, EN descripción, ES título, ES descripción)
    A = [
        ("space_race", None, "rocketnautics:space_helmet", "task", {"left_earth": dim("rocketnautics:deep_space", "minecraft:overworld")}, None, 0,
         "Space Race", "Leave the Earth behind", "Carrera espacial", "Sal de la Tierra"),
        ("one_small_step", "space_race", "rocketnautics:lunar_regolith", "goal", {"moon": dim("rocketnautics:moon")}, None, 0,
         "One Small Step", "That's one small step for man, one giant leap for mankind",
         "Un pequeño paso", "Un pequeño paso para el hombre, un gran salto para la humanidad"),
        ("red_dust", "space_race", "minecraft:red_sand", "task", {"mars": dim(f"{NS}:mars")}, None, 0,
         "Red Dust", "Set foot on Mars", "Polvo rojo", "Pisa Marte"),
        ("sandblasted", "red_dust", "minecraft:sand", "task", {"survived": IMPOSSIBLE}, None, 0,
         "Sandblasted", "Live through a dust storm (Mars or Ferra)", "Lijado por la arena", "Sobrevive a una tormenta de arena (Marte o Ferra)"),
        ("rich_veins", "red_dust", "minecraft:raw_iron", "task", {"mined": IMPOSSIBLE}, None, 0,
         "Rich Veins", "Mine an ore on another planet", "Vetas ricas", "Mina un mineral en otro planeta"),
        ("under_the_clouds", "space_race", "minecraft:magma_block", "task", {"venus": dim(f"{NS}:venus")}, None, 0,
         "Under the Clouds", "Set foot on Venus", "Bajo las nubes", "Pisa Venus"),
        ("acid_test", "under_the_clouds", f"{NS}:hazard_helmet", "task", {"survived": IMPOSSIBLE}, None, 0,
         "Acid Test", "Live through the acid rain of Venus", "Prueba de ácido", "Sal vivo de la lluvia ácida de Venus"),
        ("eternal_night", "space_race", "minecraft:packed_ice", "task", {"ice_world": dim(f"{NS}:ice_world")}, None, 0,
         "Eternal Night", "Set foot on the Ice World", "Noche eterna", "Pisa el mundo helado"),
        ("whiteout", "eternal_night", "minecraft:snowball", "task", {"survived": IMPOSSIBLE}, None, 0,
         "Whiteout", "Live through a severe blizzard", "Todo blanco", "Aguanta una ventisca fuerte"),
        ("diamonds_under_the_ice", "eternal_night", "minecraft:diamond", "task",
         {"diamond": {"trigger": "minecraft:inventory_changed", "conditions": {
             "player": [{"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"location": {"dimension": f"{NS}:ice_world"}}}],
             "items": [{"items": "minecraft:diamond"}]}}}, None, 0,
         "Diamonds Under the Ice", "Get a diamond on the Ice World", "Diamantes bajo el hielo", "Consigue un diamante en el mundo helado"),
        ("fire_moon", "space_race", "minecraft:blackstone", "task", {"ignis": dim(f"{NS}:ignis")}, None, 0,
         "Fire Moon", "Set foot on Ignis", "Luna de fuego", "Pisa Ignis"),
        ("frozen_ocean", "space_race", "minecraft:blue_ice", "task", {"thalassa": dim(f"{NS}:thalassa")}, None, 0,
         "Frozen Ocean", "Set foot on Thalassa", "Océano congelado", "Pisa Thalassa"),
        ("pressure_point", "frozen_ocean", f"{NS}:sea_diver_helmet", "task",
         {"deep": where(f"{NS}:thalassa", max_y=-30.0, head=f"{NS}:sea_diver_helmet")}, None, 0,
         "Pressure Point", "Reach the bottom of Thalassa's ocean wearing the Sea Diver helmet",
         "Punto de presión", "Llega al fondo del mar de Thalassa con el casco de la Sea Diver"),
        ("hydrocarbon_seas", "warp_speed", f"{NS}:methane_bucket", "task", {"titan": dim(f"{NS}:titan")}, None, 0,
         "Hydrocarbon Seas", "Set foot on Titan, the moon of methane lakes", "Mares de hidrocarburo",
         "Pisa Titán, la luna de los lagos de metano"),
        ("amber_dawn", "beyond_the_sun", f"{NS}:cerulean_grass_block", "task", {"cerulea": dim(f"{NS}:cerulea")}, None, 0,
         "Amber Dawn", "Set foot on Cerulea, under the light of a second star", "Amanecer ámbar",
         "Pisa Cerúlea, bajo la luz de una segunda estrella"),
        ("glass_moon", "amber_dawn", f"{NS}:velo_rock", "task", {"velo": dim(f"{NS}:velo")}, None, 0,
         "Glass Moon", "Set foot on Velo, the crystal moon", "Luna de cristal", "Pisa Velo, la luna de cristal"),
        ("rust_moon", "amber_dawn", f"{NS}:ferra_rock", "task", {"ferra": dim(f"{NS}:ferra")}, None, 0,
         "Rust Moon", "Set foot on Ferra, the moon of rust", "Luna oxidada", "Pisa Ferra, la luna de óxido"),
        ("storm_chaser", "beyond_the_sun", f"{NS}:fulgurite", "task", {"fulgor": dim(f"{NS}:fulgor")}, None, 0,
         "Storm Chaser", "Set foot on Fulgor, the moon of electric storms", "Cazatormentas",
         "Pisa Fulgor, la luna de las tormentas eléctricas"),
        ("grand_tour", "space_race", "minecraft:map", "challenge", {k: dim(v) for k, v in PLANETS.items()}, None, 500,
         "Grand Tour", "Set foot on every planet and moon", "Gran gira", "Pisa todos los planetas y lunas"),
        ("suit_up", "space_race", f"{NS}:impact_chestplate", "task", {p: suit(p) for p in SUITS}, [SUITS], 0,
         "Suit Up", "Wear a full planetary suit", "Vístete", "Ponte una armadura planetaria completa"),
        ("fashion_for_every_world", "suit_up", f"{NS}:glacial_chestplate", "challenge", {p: suit(p) for p in SUITS}, None, 250,
         "Fashion for Every World", "Wear each of the five planetary suits", "Moda para cada mundo", "Ponte las cinco armaduras planetarias"),
        ("artificial_gravity", "space_race", f"{NS}:gravity_generator", "task", {"on": IMPOSSIBLE}, None, 0,
         "Artificial Gravity", "Turn on a Gravity Generator", "Gravedad artificial", "Enciende un generador de gravedad"),
        ("full_stop", "space_race", f"{NS}:inertial_dampener_core", "task", {"fired": IMPOSSIBLE}, None, 0,
         "Full Stop", "Stop a ship with an Inertial Dampener", "Alto total", "Frena una nave con el amortiguador inercial"),
        ("perfect_circle", "full_stop", "minecraft:clock", "goal", {"orbit": IMPOSSIBLE}, None, 0,
         "Perfect Circle", "Use an Inertial Dampener in deep space to settle into a circular orbit",
         "Círculo perfecto", "Usa el amortiguador en el espacio profundo y queda en órbita circular"),
        ("captain_on_the_bridge", "space_race", f"{NS}:nav_console", "task", {"placed": placed(f"{NS}:nav_console")}, None, 0,
         "Captain on the Bridge", "Build a Navigation Console", "Capitán en el puente", "Construye una consola de navegación"),
        ("space_folding", "captain_on_the_bridge", f"{NS}:warp_drive", "task", {"placed": placed(f"{NS}:warp_drive")}, None, 0,
         "Space Folding", "Build a Warp Drive", "Doblando el espacio", "Construye un motor de distorsión"),
        ("warp_speed", "space_folding", "minecraft:ender_eye", "goal", {"jumped": IMPOSSIBLE}, None, 0,
         "Warp Speed", "Jump to another planet with the Warp Drive", "Velocidad de distorsión", "Salta a otro planeta con el motor de distorsión"),
        ("interplanetary_commuter", "warp_speed", "minecraft:nether_star", "challenge",
         {**{t: IMPOSSIBLE for t in JUMP_TARGETS}, "five": IMPOSSIBLE},
         # 5 destinos distintos de los 15. Minecraft no sabe contar "5 de N" sin listar todas las combinaciones (1365 grupos
         # con 15 destinos), así que el código da el criterio "five" al llegar a 5 (Achievements.checkCommuter). Los grupos
         # originales (5 de los 8 primeros) se mantienen, con "five" en cada uno: quien ya lo tenía completado lo sigue teniendo.
         # Todos los criterios tienen que salir en algun grupo (si no, Minecraft descarta el logro entero: "requirements did not
         # exactly match specified criteria"); los destinos nuevos van en el primer grupo, que por si solo no completa nada.
         [list(c) + ["five"] + (JUMP_TARGETS[len(JUMP_TARGETS_OLD):] if i == 0 else [])
          for i, c in enumerate(itertools.combinations(JUMP_TARGETS_OLD, len(JUMP_TARGETS_OLD) - 5 + 1))], 500,
         "Interplanetary Commuter", "Warp to five different destinations", "Viajero interplanetario", "Salta a cinco destinos distintos"),
        ("time_freezer", "space_race", f"{NS}:time_freezer", "goal", {"froze": IMPOSSIBLE}, None, 0,
         "Time Out", "Freeze a ship's orbit with a Space Time Freezer", "Tiempo muerto",
         "Congela la órbita de una nave con un congelador del tiempo espacial"),
        ("beyond_the_sun", "warp_speed", f"{NS}:stellar_warp_core", "challenge", {"jumped": IMPOSSIBLE}, None, 500,
         "Beyond the Sun", "Jump to another star system with a Stellar Warp Drive",
         "Más allá del Sol", "Salta a otro sistema solar con el motor de distorsión estelar"),
    ]
    for (aid, parent, icon, frame, criteria, reqs, xp, en_t, en_d, es_t, es_d) in A:
        key = f"advancements.{NS}.{aid}"
        LANG_EN[key + ".title"] = en_t
        LANG_EN[key + ".description"] = en_d
        LANG_ES[key + ".title"] = es_t
        LANG_ES[key + ".description"] = es_d
        display = {"icon": {"id": icon}, "title": {"translate": key + ".title"},
                   "description": {"translate": key + ".description"}, "frame": frame,
                   "show_toast": True, "announce_to_chat": True, "hidden": False}
        if parent is None:
            display["background"] = f"{NS}:textures/gui/advancements/backgrounds/space.png"
        adv = {"display": display, "criteria": criteria,
               "requirements": reqs if reqs is not None else [[k] for k in criteria]}
        if parent is not None: adv["parent"] = rl(f"space/{parent}")
        if xp: adv["rewards"] = {"experience": xp}
        data(f"advancement/space/{aid}", adv)


def build_structures():
    """Estructuras de Marte (0.7.44) construidas por el usuario en el juego con bloques de estructura.
    tools/structures/prepare.py convierte las de raw/ (quita el aire por encima de la losa de la base y pone el botín
    en los cofres); aquí se registran. Van hundidas: la losa de arena (3 capas) queda bajo tierra, el cráter se
    excava y el terreno de alrededor tapa parte de los restos, como si la arena los hubiera ido cubriendo."""
    sdir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "structures")
    for name in ["meteorite_1", "meteorite_2", "meteorite_3", "old_structure_1", "old_structure_2", "old_structure_3"]:
        write_bytes(f"data/{NS}/structure/mars/{name}.nbt", open(os.path.join(sdir, "mars", name + ".nbt"), "rb").read())
    # 0.7.45: la losa de arena de la base no se pone y la arena de encima toma el bloque del suelo de alrededor
    data("worldgen/processor_list/mars/blend", {"processors": [
        {"processor_type": rl("terrain_blend"), "slab": 3, "blend": "minecraft:red_sand", "foundation": 24}]})
    data("tags/worldgen/biome/has_structure/mars_surface", {"values": [
        rl("mars_dunes"), rl("mars_highlands"), rl("mars_canyons"), rl("mars_basin")]})

    def pool(name, pieces):
        data(f"worldgen/template_pool/mars/{name}", {"name": rl(f"mars/{name}"), "fallback": "minecraft:empty", "elements": [
            {"weight": w, "element": {"element_type": "minecraft:single_pool_element", "location": rl(f"mars/{p}"),
                                      "projection": "rigid", "processors": rl("mars/blend")}} for p, w in pieces]})

    def structure(name, start_pool, sink):
        data(f"worldgen/structure/mars/{name}", {
            "type": "minecraft:jigsaw", "biomes": f"#{NS}:has_structure/mars_surface", "step": "surface_structures",
            "terrain_adaptation": "none", "spawn_overrides": {}, "start_pool": rl(f"mars/{start_pool}"), "size": 1,
            "start_height": {"absolute": -sink}, "project_start_to_heightmap": "WORLD_SURFACE_WG",
            "max_distance_from_center": 80, "use_expansion_hack": False})

    def structure_set(name, structs, spacing, separation, salt):
        data(f"worldgen/structure_set/mars/{name}", {
            "structures": [{"structure": rl(f"mars/{s}"), "weight": 1} for s in structs],
            "placement": {"type": "minecraft:random_spread", "spacing": spacing, "separation": separation, "salt": salt}})

    # meteoritos: 3 variantes (titanio, uranio, torio en el núcleo); bastantes pero sin exagerar
    # (los meteoritos son un cráter desde 0.7.46: worldgen/MeteoriteCraterFeature; sus plantillas se siguen usando)
    # restos viejos: poco comunes, un poco más hundidos
    for n in ["old_structure_1", "old_structure_2", "old_structure_3"]:
        pool(n, [(n, 1)])
        structure(n, n, 4)
    structure_set("old_structures", ["old_structure_1", "old_structure_2", "old_structure_3"], 36, 14, 26492331)

    def entry(item, weight, lo, hi):
        e = {"type": "minecraft:item", "name": item, "weight": weight}
        if hi > 1:
            e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
        return e

    # Estructura 1: minerales en bruto al azar (petición del usuario)
    data("loot_table/chests/mars_old_structure_1", {"type": "minecraft:chest", "pools": [{
        "rolls": {"type": "minecraft:uniform", "min": 4, "max": 7}, "entries": [
            entry("minecraft:raw_iron", 20, 2, 6), entry("minecraft:raw_copper", 20, 2, 8),
            entry("create:raw_zinc", 15, 2, 6), entry("minecraft:raw_gold", 8, 1, 4),
            entry("rocketnautics:raw_titanium", 10, 1, 4), entry("createnuclear:raw_lead", 8, 1, 4),
            entry("createnuclear:raw_uranium", 3, 1, 2), entry("createnuclear:raw_thorium", 3, 1, 2)]}]})
    # Estructura 3 (estación de oxígeno): cubetas vacías, lingotes de hierro, algo de cobre y tuberías de cobre
    data("loot_table/chests/mars_old_structure_3", {"type": "minecraft:chest", "pools": [{
        "rolls": {"type": "minecraft:uniform", "min": 3, "max": 6}, "entries": [
            entry("minecraft:bucket", 15, 1, 3), entry("minecraft:iron_ingot", 20, 2, 7),
            entry("minecraft:copper_ingot", 10, 1, 4), entry("create:fluid_pipe", 15, 2, 8)]}]})


# --- Minerales abundantes por planeta (0.7.28, idea de un amigo del usuario) ---------------------------------------
# Cada planeta es "el sitio" de ciertos minerales de Create, Create Nuclear y Cosmonautics. Es contenido de final de
# juego, así que las cantidades son altas a propósito (el usuario lo pidió así).
def rich_ore(planet, name, targets, size, count, lo, hi, shape="minecraft:uniform", discard=0.0):
    """targets: lista de (bloque, tag de piedra que reemplaza)."""
    data(f"worldgen/configured_feature/{planet}/{name}", {"type": "minecraft:ore", "config": {
        "discard_chance_on_air_exposure": discard, "size": size,
        "targets": [{"state": block(b), "target": {"predicate_type": "minecraft:tag_match", "tag": tag}}
                    for b, tag in targets]}})
    height = {"type": shape, "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}
    if shape == "minecraft:trapezoid":
        height = {"type": "minecraft:trapezoid", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}
    data(f"worldgen/placed_feature/{planet}/{name}", {"feature": rl(f"{planet}/{name}"), "placement": [
        {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": height}, {"type": "minecraft:biome"}]})
    return rl(f"{planet}/{name}")


STONE = "minecraft:stone_ore_replaceables"
DEEP = "minecraft:deepslate_ore_replaceables"
OVERWORLD_ROCK = "minecraft:base_stone_overworld"
NETHER_ROCK = "minecraft:base_stone_nether"


def ores_mars():
    P = "mars"
    return [
        # zinc de Create (en la Tierra: 1 veta de 12 por chunk aprox.)
        rich_ore(P, "rich_zinc", [("create:zinc_ore", STONE), ("create:deepslate_zinc_ore", DEEP)], 12, 16, -64, 110),
        rich_ore(P, "rich_zinc_large", [("create:zinc_ore", STONE), ("create:deepslate_zinc_ore", DEEP)], 24, 4, -64, 40),
        # crimsita (piedra de Create que al triturarla da hierro) en vetas enormes, y algo de ocrum (oro)
        rich_ore(P, "crimsite_veins", [("create:crimsite", OVERWORLD_ROCK)], 64, 6, -64, 120),
        rich_ore(P, "ochrum_veins", [("create:ochrum", OVERWORLD_ROCK)], 48, 2, -64, 60),
        rich_ore(P, "rich_iron", [("minecraft:iron_ore", STONE), ("minecraft:deepslate_iron_ore", DEEP)], 12, 14, -64, 120),
    ]


def ores_ice_world():
    P = "ice_world"
    return [
        # asurina (piedra azul de Create que da zinc) en vetas enormes, y zinc
        rich_ore(P, "asurine_veins", [("create:asurine", OVERWORLD_ROCK)], 64, 6, -64, 110),
        rich_ore(P, "rich_zinc", [("create:zinc_ore", STONE), ("create:deepslate_zinc_ore", DEEP)], 12, 12, -64, 100),
        # diamante enterrado bajo el hielo (en la Tierra: ~1 veta pequeña por chunk)
        rich_ore(P, "rich_diamond", [("minecraft:diamond_ore", STONE), ("minecraft:deepslate_diamond_ore", DEEP)], 8, 6, -64, 16, discard=0.3),
        rich_ore(P, "rich_diamond_large", [("minecraft:diamond_ore", STONE), ("minecraft:deepslate_diamond_ore", DEEP)], 14, 2, -64, -16, discard=0.5),
    ]


def ores_ignis():
    P = "ignis"
    # Ignis es de piedra negra y basalto: las vetas reemplazan la roca del Nether
    return [
        rich_ore(P, "rich_gold", [("minecraft:gold_ore", NETHER_ROCK)], 12, 16, -64, 120),
        rich_ore(P, "rich_gold_large", [("minecraft:gold_ore", NETHER_ROCK)], 22, 4, -64, 40),
        rich_ore(P, "gilded_blackstone", [("minecraft:gilded_blackstone", NETHER_ROCK)], 10, 8, -64, 120),
        rich_ore(P, "rich_copper", [("minecraft:copper_ore", NETHER_ROCK)], 16, 16, -64, 120),
        # ocrum (oro) y veridio (cobre) de Create en vetas enormes
        rich_ore(P, "ochrum_veins", [("create:ochrum", NETHER_ROCK)], 64, 5, -64, 120),
        rich_ore(P, "veridium_veins", [("create:veridium", NETHER_ROCK)], 64, 5, -64, 120),
    ]


def build_thalassa_creatures():
    """0.7.61: criaturas de Thalassa (pez farol, cangrejo de chimenea, cangrejo rey azul, acechador abisal) y sus objetos.
    Modelos y texturas: tools/designs/thalassa_mobs.py y thalassa_items.py (salida en tools/designs/mobs)."""
    mobs = os.path.join(DESIGNS, "mobs")
    for m in PLANET_MOBS:
        write_bytes(f"assets/{NS}/mob_models/{m}.json", open(os.path.join(mobs, f"{m}.json"), "rb").read())
        write_bytes(f"assets/{NS}/textures/entity/{m}.png", open(os.path.join(mobs, f"{m}.png"), "rb").read())
        write_bytes(f"assets/{NS}/textures/entity/{m}_glow.png", open(os.path.join(mobs, f"{m}_glow.png"), "rb").read())
    items_dir = os.path.join(mobs, "items")
    write_bytes(f"assets/{NS}/textures/entity/king_shell_shield.png", open(os.path.join(items_dir, "entity_king_shell_shield.png"), "rb").read())
    names = {
        "lantern_fish": ("Lantern Fish", "Pez farol"), "cooked_lantern_fish": ("Cooked Lantern Fish", "Pez farol cocinado"),
        "glow_gel": ("Glow Gel", "Gel luminoso"), "lantern_fish_bucket": ("Bucket of Lantern Fish", "Cubeta con pez farol"),
        "crab_meat": ("Raw Crab Meat", "Carne de cangrejo cruda"), "cooked_crab_meat": ("Cooked Crab Meat", "Carne de cangrejo cocinada"),
        "volcanic_chitin": ("Volcanic Chitin", "Quitina volcánica"),
        "king_crab_leg": ("Raw King Crab Leg", "Pata de cangrejo rey cruda"),
        "cooked_king_crab_leg": ("Cooked King Crab Leg", "Pata de cangrejo rey cocinada"),
        "king_shell": ("Blue King Shell", "Caparazón de rey azul"), "king_claw": ("King's Claw", "Pinza del rey"),
        "abyssal_fang": ("Abyssal Fang", "Colmillo abisal"), "harpoon": ("Harpoon", "Arpón"),
        "sonar_organ": ("Sonar Organ", "Órgano sonar"), "sonar": ("Sonar", "Sonar"),
        "glowing_barbel": ("Glowing Barbel", "Barbilla luminosa"),
    }
    for item, (en, es) in names.items():
        write_bytes(f"assets/{NS}/textures/item/{item}.png", open(os.path.join(items_dir, f"item_{item}.png"), "rb").read())
        parent = "minecraft:item/handheld" if item == "harpoon" else "minecraft:item/generated"
        write(f"assets/{NS}/models/item/{item}.json", {"parent": parent, "textures": {"layer0": rl(f"item/{item}")}})
        LANG_EN[f"item.{NS}.{item}"] = en
        LANG_ES[f"item.{NS}.{item}"] = es
    # escudo: modelo de entidad (lo dibuja ShellShieldRenderer), como el escudo de Minecraft
    shield_display = {"thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [10, 6, -4], "scale": [1, 1, 1]},
                      "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [10, 6, 12], "scale": [1, 1, 1]},
                      "firstperson_righthand": {"rotation": [0, 180, 5], "translation": [-10, 2, -10], "scale": [1.25, 1.25, 1.25]},
                      "firstperson_lefthand": {"rotation": [0, 180, 5], "translation": [10, 0, -10], "scale": [1.25, 1.25, 1.25]},
                      "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
                      "fixed": {"rotation": [0, 180, 0], "translation": [-4.5, 4.5, -5], "scale": [0.55, 0.55, 0.55]},
                      "ground": {"rotation": [0, 0, 0], "translation": [2, 4, 2], "scale": [0.25, 0.25, 0.25]}}
    blocking_display = {"thirdperson_righthand": {"rotation": [45, 155, 0], "translation": [-3.49, 11, -2], "scale": [1, 1, 1]},
                        "thirdperson_lefthand": {"rotation": [45, 155, 0], "translation": [11.51, 7, 2.5], "scale": [1, 1, 1]},
                        "firstperson_righthand": {"rotation": [0, 180, -5], "translation": [-15, 5, -11], "scale": [1.25, 1.25, 1.25]},
                        "firstperson_lefthand": {"rotation": [0, 180, -5], "translation": [5, 5, -11], "scale": [1.25, 1.25, 1.25]},
                        "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]}}
    write(f"assets/{NS}/models/item/king_shell_shield.json", {
        "parent": "builtin/entity", "gui_light": "front", "textures": {"particle": rl("item/king_shell")},
        "display": shield_display,
        "overrides": [{"predicate": {"blocking": 1}, "model": rl("item/king_shell_shield_blocking")}]})
    write(f"assets/{NS}/models/item/king_shell_shield_blocking.json", {
        "parent": "builtin/entity", "gui_light": "front", "textures": {"particle": rl("item/king_shell")}, "display": blocking_display})
    LANG_EN[f"item.{NS}.king_shell_shield"] = "King Shell Shield"
    LANG_ES[f"item.{NS}.king_shell_shield"] = "Escudo de caparazón de rey"
    # bloques
    for block, (en, es) in {"gel_lamp": ("Gel Lamp", "Lámpara de gel"), "abyssal_lamp": ("Abyssal Lamp", "Lámpara abisal")}.items():
        write_bytes(f"assets/{NS}/textures/block/{block}.png", open(os.path.join(items_dir, f"block_{block}.png"), "rb").read())
        write(f"assets/{NS}/blockstates/{block}.json", {"variants": {"": {"model": rl(f"block/{block}")}}})
        write(f"assets/{NS}/models/block/{block}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": rl(f"block/{block}")}})
        write(f"assets/{NS}/models/item/{block}.json", {"parent": rl(f"block/{block}")})
        LANG_EN[f"block.{NS}.{block}"] = en
        LANG_ES[f"block.{NS}.{block}"] = es
        data(f"loot_table/blocks/{block}", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": rl(block)}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # criaturas: nombres, huevos
    mob_names = {"lantern_fish": ("Lantern Fish", "Pez farol"), "vent_crab": ("Vent Crab", "Cangrejo de chimenea"),
                 "king_crab": ("Blue King Crab", "Cangrejo rey azul"), "abyssal_stalker": ("Abyssal Stalker", "Acechador abisal")}
    for m, (en, es) in mob_names.items():
        LANG_EN[f"entity.{NS}.{m}"] = en
        LANG_ES[f"entity.{NS}.{m}"] = es
        write(f"assets/{NS}/models/item/{m}_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})
        LANG_EN[f"item.{NS}.{m}_spawn_egg"] = f"{en} Spawn Egg"
        LANG_ES[f"item.{NS}.{m}_spawn_egg"] = f"Huevo generador de {es.lower()}"
    LANG_EN[f"entity.{NS}.harpoon"] = "Harpoon"
    LANG_ES[f"entity.{NS}.harpoon"] = "Arpón"
    LANG_EN[f"message.{NS}.sonar"] = "Sonar: %s creatures detected"
    LANG_ES[f"message.{NS}.sonar"] = "Sonar: %s criaturas detectadas"
    LANG_EN[f"lore.{NS}.chitin_reinforced"] = "Reinforced with volcanic chitin"
    LANG_ES[f"lore.{NS}.chitin_reinforced"] = "Reforzada con quitina volcánica"
    # respiran bajo el agua (canBreatheUnderwater es final en 1.21: va por esta etiqueta)
    write("data/minecraft/tags/entity_type/can_breathe_under_water.json",
          {"replace": False, "values": [rl("vent_crab"), rl("king_crab"), rl("abyssal_stalker")]})

    # botín
    def drop(item, lo, hi, looting=1.0, chance=None, smelt=None):
        fns = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}},
               {"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                "count": {"type": "minecraft:uniform", "min": 0, "max": looting}}]
        if smelt:
            fns.append({"function": "minecraft:furnace_smelt", "conditions": [{"condition": "minecraft:entity_properties",
                        "entity": "this", "predicate": {"flags": {"is_on_fire": True}}}]})
        pool = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": rl(item), "functions": fns}]}
        if chance is not None:
            pool["conditions"] = [{"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
                                   "unenchanted_chance": chance, "enchanted_chance": {"type": "minecraft:linear", "base": chance + 0.05,
                                                                                    "per_level_above_first": 0.05}}]
        return pool
    loot = {
        "lantern_fish": [drop("lantern_fish", 1, 1, 0, smelt=True), drop("glow_gel", 0, 1)],
        "vent_crab": [drop("crab_meat", 1, 2, smelt=True), drop("volcanic_chitin", 0, 1)],
        "king_crab": [drop("king_crab_leg", 2, 4, smelt=True), drop("king_shell", 1, 2), drop("king_claw", 1, 1, 0, chance=0.12)],
        "abyssal_stalker": [drop("abyssal_fang", 1, 2), drop("sonar_organ", 1, 1, 0), drop("glowing_barbel", 0, 2)],
    }
    for m, pools in loot.items():
        data(f"loot_table/entities/{m}", {"type": "minecraft:entity", "pools": pools})

    # recetas
    def cook(raw, cooked, xp=0.35):
        for kind, t, suffix in (("smelting", 200, ""), ("smoking", 100, "_from_smoking"), ("campfire_cooking", 600, "_from_campfire")):
            data(f"recipe/{cooked}{suffix}", {"type": f"minecraft:{kind}", "ingredient": {"item": rl(raw)},
                                              "result": {"id": rl(cooked)}, "experience": xp, "cookingtime": t})
    cook("lantern_fish", "cooked_lantern_fish")
    cook("crab_meat", "cooked_crab_meat")
    cook("king_crab_leg", "cooked_king_crab_leg", 0.5)

    def shaped(name, pattern, key, result, count=1):
        data(f"recipe/{name}", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
                                "key": {k: ({"tag": v[1:]} if v.startswith("#") else {"item": v}) for k, v in key.items()},
                                "result": {"id": result, "count": count}})
    shaped("gel_lamp", [" G ", "GXG", " G "], {"G": rl("glow_gel"), "X": "minecraft:glass"}, rl("gel_lamp"))
    shaped("abyssal_lamp", [" B ", "BXB", " B "], {"B": rl("glowing_barbel"), "X": "minecraft:glass"}, rl("abyssal_lamp"))
    shaped("king_shell_shield", ["SIS", "SSS", " S "], {"S": rl("king_shell"), "I": "minecraft:iron_ingot"}, rl("king_shell_shield"))
    shaped("harpoon", ["  F", " I ", "I  "], {"F": rl("abyssal_fang"), "I": "minecraft:iron_ingot"}, rl("harpoon"))
    shaped("sonar", [" G ", "CSC", "CRC"], {"G": "minecraft:glass_pane", "C": "minecraft:copper_ingot",
                                            "S": rl("sonar_organ"), "R": "minecraft:redstone"}, rl("sonar"))
    # Sea Diver Armor reforzada: mesa de herrería con quitina volcánica (plantilla) + pieza + quitina volcánica
    lore = json.dumps({"translate": f"lore.{NS}.chitin_reinforced", "color": "gold", "italic": False})
    for piece in ("helmet", "chestplate", "leggings", "boots"):
        data(f"recipe/sea_diver_{piece}_chitin", {
            "type": "minecraft:smithing_transform",
            "template": {"item": rl("volcanic_chitin")}, "base": {"item": rl(f"sea_diver_{piece}")},
            "addition": {"item": rl("volcanic_chitin")},
            "result": {"id": rl(f"sea_diver_{piece}"), "count": 1,
                       "components": {"minecraft:custom_data": {"chitin_reinforced": True}, "minecraft:lore": [lore]}}})


SUIT_ITEMS = os.path.join(DESIGNS, "suit_items")   # iconos de tools/designs/suit_items.py


def simple_item(item, en, es, icon=None):
    write_bytes(f"assets/{NS}/textures/item/{item}.png", open(os.path.join(SUIT_ITEMS, f"{icon or item}.png"), "rb").read())
    write(f"assets/{NS}/models/item/{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rl(f"item/{item}")}})
    LANG_EN[f"item.{NS}.{item}"] = en
    LANG_ES[f"item.{NS}.{item}"] = es


def build_suit_progression():
    """0.7.64: plantillas de mejora por planeta y materiales que solo existen en cada planeta (idea de un amigo del usuario).
    - Primera plantilla: materiales del planeta alrededor de una lámina de aleación de titanio.
    - Duplicar: plantilla + 7 diamantes + 1 material del planeta → 2 plantillas.
    - Mesa de herrería: plantilla + pieza del traje básico + lingote de netherita → pieza del traje del planeta.
    - Materiales nuevos: con modificadores de botín (neoforge:add_table) limitados a la dimensión del planeta."""
    simple_item("meteorite_fragment", "Meteorite Fragment", "Fragmento de meteorito")
    simple_item("cryo_crystal", "Cryo Crystal", "Cristal criogénico")
    simple_item("venusian_sulfur", "Venusian Sulfur", "Azufre venusiano")
    planets = {"impact": ("Mars", "Marte"), "glacial": ("Ice World", "mundo helado"), "hazard": ("Venus", "Venus"),
               "sea_diver": ("Thalassa", "Thalassa"), "thermal": ("Ignis", "Ignis")}
    suit_names = {"impact": ("Impact Armor", "armadura de impacto"), "glacial": ("Glacial Armor", "armadura glacial"),
                  "hazard": ("Hazard Armor", "armadura Hazard"), "sea_diver": ("Sea Diver Armor", "armadura Sea Diver"),
                  "thermal": ("Thermal Suit", "traje térmico")}
    tnames = {"impact": ("Impact", "de impacto"), "glacial": ("Glacial", "glacial"), "hazard": ("Hazard", "Hazard"),
              "sea_diver": ("Sea Diver", "Sea Diver"), "thermal": ("Thermal", "térmica")}
    K = f"item.{NS}.smithing_template"
    LANG_EN[f"{K}.applies_to"] = "Basic Space Suit"
    LANG_ES[f"{K}.applies_to"] = "Traje espacial básico"
    LANG_EN[f"{K}.ingredients"] = "Netherite Ingot"
    LANG_ES[f"{K}.ingredients"] = "Lingote de netherita"
    LANG_EN[f"{K}.base_slot_description"] = "Add a piece of the Basic Space Suit"
    LANG_ES[f"{K}.base_slot_description"] = "Pon una pieza del traje espacial básico"
    LANG_EN[f"{K}.additions_slot_description"] = "Add a Netherite Ingot"
    LANG_ES[f"{K}.additions_slot_description"] = "Pon un lingote de netherita"
    for suit, (pen, pes) in planets.items():
        item = f"{suit}_upgrade_template"
        ten, tes = tnames[suit]
        simple_item(item, f"{ten} Upgrade ({pen})", f"Plantilla {tes} ({pes})")
        LANG_EN[f"{K}.{suit}.upgrade"] = f"{suit_names[suit][0]} upgrade"
        LANG_ES[f"{K}.{suit}.upgrade"] = f"Mejora a {suit_names[suit][1]}"

    # materiales: botín extra al minar en el planeta (con Fortuna salen más)
    def gen(name, block_tables, dim, item, chance, fortune=0.05):
        data(f"loot_table/gameplay/planet_materials/{name}", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": rl(item)}],
            # table_bonus lee la Fortuna de la herramienta; random_chance_with_enchanted_bonus pide
            # la entidad atacante, que el botín de bloques no tiene (aviso al cargar y Fortuna ignorada)
            "conditions": [{"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                            "chances": [round(chance + k * fortune, 4) for k in range(4)]}]}]})
        mods = []
        for i, table in enumerate(block_tables):
            mid = f"{name}_{i}"
            data(f"loot_modifiers/{mid}", {"type": "neoforge:add_table", "table": rl(f"gameplay/planet_materials/{name}"),
                                           "conditions": [{"condition": "neoforge:loot_table_id", "loot_table_id": table},
                                                          {"condition": "minecraft:location_check", "predicate": {"dimension": dim}}]})
            mods.append(rl(mid))
        return mods
    entries = []
    # Marte: el basalto lunar envejecido solo está en los núcleos de los meteoritos (12-14 bloques por meteorito)
    entries += gen("meteorite_fragment", ["rocketnautics:blocks/lunar_aged_basalt"], f"{NS}:mars", "meteorite_fragment", 0.6, 0.1)
    # 0.7.66: 6 % → 10 % y 5 % → 10 % (pedido del usuario: menos tiempo expuesto sin el traje del planeta)
    entries += gen("cryo_crystal", ["minecraft:blocks/blue_ice", "minecraft:blocks/packed_ice"], f"{NS}:ice_world", "cryo_crystal", 0.10)
    entries += gen("venusian_sulfur", ["minecraft:blocks/basalt", "minecraft:blocks/smooth_basalt", "minecraft:blocks/magma_block"],
                   f"{NS}:venus", "venusian_sulfur", 0.10)
    write("data/neoforge/loot_modifiers/global_loot_modifiers.json", {"replace": False, "entries": entries})

    def shaped(name, pattern, key, result, count=1):
        data(f"recipe/{name}", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
                                "key": {k: {"item": v} for k, v in key.items()}, "result": {"id": result, "count": count}})
    T = "rocketnautics:titanium_alloy_sheet"
    first = {"impact": ({"M": rl("meteorite_fragment")}, ["MMM", "MTM", "MMM"]),
             "glacial": ({"M": rl("cryo_crystal")}, ["MMM", "MTM", "MMM"]),
             "hazard": ({"M": rl("venusian_sulfur")}, ["MMM", "MTM", "MMM"]),
             "sea_diver": ({"A": rl("king_shell"), "B": rl("abyssal_fang")}, ["ABA", "BTB", "ABA"]),
             "thermal": ({"A": rl("igneous_shell"), "B": rl("magma_core")}, ["ABA", "BTB", "ABA"])}
    dup = {"impact": "meteorite_fragment", "glacial": "cryo_crystal", "hazard": "venusian_sulfur",
           "sea_diver": "king_shell", "thermal": "igneous_shell"}
    for suit, (key, pattern) in first.items():
        tpl = rl(f"{suit}_upgrade_template")
        shaped(f"{suit}_upgrade_template", pattern, dict(key, T=T), tpl)
        shaped(f"{suit}_upgrade_template_duplicate", ["DXD", "DMD", "DDD"],
               {"D": "minecraft:diamond", "X": tpl, "M": rl(dup[suit])}, tpl, 2)
        for piece in PIECES:
            data(f"recipe/{suit}_{piece}_smithing", {
                "type": "minecraft:smithing_transform", "template": {"item": tpl}, "base": {"item": rl(f"basic_{piece}")},
                "addition": {"item": "minecraft:netherite_ingot"}, "result": {"id": rl(f"{suit}_{piece}"), "count": 1}})


def build_survival_kits():
    """0.7.66: kits de supervivencia para la primera expedición (SurvivalKits.java). 3 min con el peligro al 40 %;
    no dan inmunidad (pedido del usuario: que no le quiten el sentido a las armaduras). Se fabrican en la Tierra."""
    kits = {"cooling_gel": ("Cooling Gel", "Gel refrigerante", "cooled", "Cooled", "Refrigerado",
                            "3 min: the heat of Venus affects you 60% less.", "3 min: el calor de Venus te afecta un 60 % menos."),
            "chemical_heater": ("Chemical Heater", "Calentador químico", "warmed", "Warmed", "Abrigado",
                                "3 min: the cold of the Ice World and Thalassa affects you 60% less.",
                                "3 min: el frío del mundo helado y de Thalassa te afecta un 60 % menos."),
            "dust_filter": ("Dust Filter", "Filtro de polvo", "filtered", "Filtered", "Filtrado",
                            "3 min: Mars dust storms affect you 60% less.", "3 min: las tormentas de arena de Marte te afectan un 60 % menos.")}
    for item, (en, es, effect, een, ees, ten, tes) in kits.items():
        simple_item(item, en, es)
        write_bytes(f"assets/{NS}/textures/mob_effect/{effect}.png", open(os.path.join(SUIT_ITEMS, f"effect_{effect}.png"), "rb").read())
        LANG_EN[f"effect.{NS}.{effect}"] = een
        LANG_ES[f"effect.{NS}.{effect}"] = ees
        LANG_EN[f"tooltip.{NS}.{item}"] = ten
        LANG_ES[f"tooltip.{NS}.{item}"] = tes
    LANG_EN[f"tooltip.{NS}.kit_note"] = "Doesn't replace the planet's suit."
    LANG_ES[f"tooltip.{NS}.kit_note"] = "No sustituye al traje del planeta."

    def shapeless(name, ingredients, count):
        data(f"recipe/{name}", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                "ingredients": [{"tag": i[1:]} if i.startswith("#") else {"item": i} for i in ingredients],
                                "result": {"id": rl(name), "count": count}})
    shapeless("cooling_gel", ["minecraft:packed_ice", "minecraft:packed_ice", "minecraft:slime_ball",
                              "minecraft:glass_bottle", "minecraft:glass_bottle"], 2)
    shapeless("chemical_heater", ["minecraft:iron_nugget", "minecraft:iron_nugget", "#minecraft:coals",
                                  "minecraft:paper", "minecraft:paper"], 2)
    shapeless("dust_filter", ["#minecraft:wool", "minecraft:charcoal", "minecraft:string", "minecraft:iron_nugget"], 2)


def build_ignis_creatures():
    """0.7.64: caracol de magma y caracol titán de Ignis (modelos de tools/designs/ignis_mobs.py, salida en
    tools/designs/mobs; iconos de tools/designs/suit_items.py). El núcleo de magma es combustible sobrecalentado del
    blaze burner de Create (data map) y combustible de horno."""
    simple_item("igneous_shell", "Igneous Shell Fragment", "Fragmento de concha ígnea")
    simple_item("magma_core", "Magma Core", "Núcleo de magma")
    simple_item("magma_slime", "Magma Slime", "Baba de magma")
    write_bytes(f"assets/{NS}/textures/block/magma_lamp.png", open(os.path.join(SUIT_ITEMS, "magma_lamp.png"), "rb").read())
    write(f"assets/{NS}/blockstates/magma_lamp.json", {"variants": {"": {"model": rl("block/magma_lamp")}}})
    write(f"assets/{NS}/models/block/magma_lamp.json", {"parent": "minecraft:block/cube_all", "textures": {"all": rl("block/magma_lamp")}})
    write(f"assets/{NS}/models/item/magma_lamp.json", {"parent": rl("block/magma_lamp")})
    LANG_EN[f"block.{NS}.magma_lamp"] = "Magma Lamp"
    LANG_ES[f"block.{NS}.magma_lamp"] = "Lámpara de magma"
    data("loot_table/blocks/magma_lamp", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": rl("magma_lamp")}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    for m, (en, es) in {"magma_snail": ("Magma Snail", "Caracol de magma"), "magma_titan": ("Magma Titan", "Caracol titán")}.items():
        LANG_EN[f"entity.{NS}.{m}"] = en
        LANG_ES[f"entity.{NS}.{m}"] = es
        write(f"assets/{NS}/models/item/{m}_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})
        LANG_EN[f"item.{NS}.{m}_spawn_egg"] = f"{en} Spawn Egg"
        LANG_ES[f"item.{NS}.{m}_spawn_egg"] = f"Huevo generador de {es.lower()}"

    def drop(item, lo, hi, chance=None):
        pool = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}},
            {"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
             "count": {"type": "minecraft:uniform", "min": 0, "max": 1}}]}]}
        if chance is not None:
            pool["conditions"] = [{"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
                                   "unenchanted_chance": chance, "enchanted_chance": {"type": "minecraft:linear", "base": chance + 0.08,
                                                                                    "per_level_above_first": 0.08}}]
        return pool
    data("loot_table/entities/magma_snail", {"type": "minecraft:entity", "pools": [
        drop(rl("magma_slime"), 1, 2), drop(rl("igneous_shell"), 1, 1, 0.6), drop(rl("magma_core"), 1, 1, 0.25)]})
    data("loot_table/entities/magma_titan", {"type": "minecraft:entity", "pools": [
        drop(rl("igneous_shell"), 3, 5), drop(rl("magma_core"), 1, 2), drop(rl("magma_slime"), 2, 4), drop("minecraft:raw_gold", 2, 5)]})
    data("recipe/magma_lamp", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": [" S ", "SXS", " S "],
                               "key": {"S": {"item": rl("magma_slime")}, "X": {"item": "minecraft:glass"}},
                               "result": {"id": rl("magma_lamp"), "count": 1}})
    # combustible: blaze burner de Create en modo sobrecalentado (como el blaze cake) y horno (como 4 varas de blaze)
    write("data/create/data_maps/item/superheated_blaze_burner_fuels.json", {"values": {rl("magma_core"): {"burn_time": 3200}}})
    datamap_add("data/neoforge/data_maps/item/furnace_fuels.json", {rl("magma_core"): {"burn_time": 9600}})



# ==========================================================================
# SEGUNDO SISTEMA SOLAR (1.0.3, prueba mínima): la estrella Ámbar (enana naranja) y un planeta de prueba
# ==========================================================================
# Ámbar cuelga de la raíz del universo, como el Sol, en una posición fija lejísimos (el mundo helado, lo más lejano
# del sistema solar, está a ~1,6e10 m). Con dos estrellas de posición fija, a la nave la controla la más cercana.
AMBER_POS = 1.5e12
AMBER_MU = 0.7 * 6.907424484959378e18      # 70 % de la masa del Sol
OVERWORLD_COLORS = [  # los mismos colores que usa Cosmonautics para la Tierra (biomas de Minecraft)
    {"blue": 245, "green": 240, "matching_tags": ["c:is_snowy"], "priority": 10, "red": 240},
    {"blue": 40, "green": 90, "matching_tags": ["minecraft:is_badlands"], "priority": 10, "red": 195},
    {"blue": 25, "green": 85, "matching_tags": ["minecraft:is_jungle"], "priority": 1, "red": 15},
    {"blue": 135, "flags": ["ocean"], "green": 45, "matching_tags": ["minecraft:is_ocean", "minecraft:is_deep_ocean"], "red": 15},
    {"blue": 215, "green": 95, "matching_tags": ["minecraft:is_river"], "red": 25},
    {"blue": 155, "green": 205, "matching_tags": ["minecraft:is_beach"], "red": 225},
    {"blue": 115, "green": 195, "matching_tags": ["c:is_desert"], "red": 215},
    {"blue": 55, "green": 145, "matching_tags": ["c:is_plains"], "red": 45},
    {"blue": 35, "green": 105, "matching_tags": ["minecraft:is_forest"], "red": 25},
    {"blue": 55, "green": 75, "matching_tags": ["minecraft:is_taiga"], "red": 30},
    {"blue": 70, "green": 140, "matching_tags": ["minecraft:is_savanna"], "red": 160},
    {"blue": 40, "green": 70, "matching_tags": ["c:is_swamp"], "red": 50},
    {"blue": 80, "green": 100, "matching_tags": ["c:is_windswept"], "red": 80},
    {"blue": 100, "green": 90, "matching_tags": ["c:is_mushroom"], "red": 100},
    {"blue": 135, "green": 135, "matching_tags": ["minecraft:is_mountain", "c:is_stony_shores"], "red": 135},
]


def build_amber_system():
    write_bytes(f"assets/{NS}/textures/planet/amber.png", open(os.path.join(DESIGNS, "amber_star.png"), "rb").read())
    data("universe_planets/amber", {
        "name": "amber",
        "parent": "root",
        "priority": 1000,
        "mu": AMBER_MU,
        "radius": 2.2e8,
        "position": {"type": "fixed", "position": {"x": AMBER_POS, "y": 0.0, "z": 0.0}},
        "rotation": {"type": "period", "period_seconds": 30000.0, "rotation_axis": {"x": 0.0, "y": 1.0, "z": 0.0}},
        "planet_extras": {"is_star": True},
        "planet_texture": {"type": "resloc", "texture": f"{NS}:textures/planet/amber.png"},
    })

    # 1.0.3: el planeta de prueba se sustituyó por Cerúlea (build_cerulea)
    for body, en, es in [("amber", "Amber", "Ámbar")]:
        LANG_EN[f"hologram.{NS}.body.{body}"] = en
        LANG_ES[f"hologram.{NS}.body.{body}"] = es
    n = f"nav.{NS}."
    for field, (en, es) in {"type": ("Star (orange dwarf)", "Estrella (enana naranja)"),
                            "desc": ("A smaller, cooler star than the Sun, far beyond its system. Its light is warm and orange.",
                                     "Una estrella más pequeña y fría que el Sol, muy lejos de su sistema. Su luz es cálida y naranja.")}.items():
        LANG_EN[f"{n}body.amber.{field}"] = en
        LANG_ES[f"{n}body.amber.{field}"] = es


# ==========================================================================
# CERÚLEA (1.0.3): el planeta de Ámbar. Alienígena pero habitable: verdes y azules, especies propias.
# Texturas: tools/designs/cerulea_assets.py -> tools/designs/cerulea/{block,item}
# ==========================================================================
CERULEA_TEX = os.path.join(DESIGNS, "cerulea")
CERULEA_ORES = ["coal", "iron", "copper", "gold", "redstone", "lapis", "diamond", "emerald", "zinc", "crystal"]
ORE_NAMES = {"coal": ("Coal", "carbón"), "iron": ("Iron", "hierro"), "copper": ("Copper", "cobre"), "gold": ("Gold", "oro"),
             "redstone": ("Redstone", "redstone"), "lapis": ("Lapis Lazuli", "lapislázuli"), "diamond": ("Diamond", "diamante"),
             "emerald": ("Emerald", "esmeralda"), "zinc": ("Zinc", "zinc"), "crystal": ("Cerulean Crystal", "cristal cerúleo")}
CERULEA_BIOMES = ["cerulea_tall_meadow", "cerulea_short_meadow", "cerulea_giant_forest", "cerulea_grove", "cerulea_swamp",
                  "cerulea_river", "cerulea_shore", "cerulea_ocean"]


def cerulea_spawns(b):
    """1.0.3: criaturas de Cerúlea por bioma (los demás spawns los quita no_earth_mobs)."""
    c = lambda m, w, a, z: (rl(m), w, a, z)  # noqa: E731
    table = {
        "cerulea_tall_meadow": dict(creature=[c("grazer", 12, 3, 6), c("hopper", 10, 2, 4), c("runner", 3, 2, 4)]),
        "cerulea_short_meadow": dict(creature=[c("grazer", 14, 3, 6), c("hopper", 12, 2, 4), c("runner", 2, 2, 3)]),
        "cerulea_grove": dict(creature=[c("hopper", 8, 2, 3), c("grazer", 4, 2, 4), c("runner", 4, 2, 4)]),
        "cerulea_giant_forest": dict(creature=[c("glider", 14, 1, 2), c("hopper", 4, 2, 3), c("runner", 3, 2, 3)]),
        "cerulea_swamp": dict(creature=[c("croaker", 12, 2, 3)], monster=[c("lurker", 100, 1, 1)]),
        "cerulea_river": dict(water_ambient=[c("ribbonfin", 15, 3, 6)], creature=[c("croaker", 3, 1, 2)]),
        "cerulea_ocean": dict(water_ambient=[c("ribbonfin", 20, 4, 8)], monster=[c("ironjaw", 10, 1, 1)]),
        "cerulea_shore": dict(),
    }
    return spawners(**table.get(b, {}))


def build_cerulea_creatures():
    """1.0.3: criaturas de Cerúlea (modelos de tools/designs/cerulea_mobs.py y objetos de tools/designs/cerulea_items.py,
    salida en tools/designs/mobs y tools/designs/mobs/items_cerulea): nombres, huevos, botín, recetas y etiquetas."""
    mobs = os.path.join(DESIGNS, "mobs")
    items_dir = os.path.join(mobs, "items_cerulea")
    for m in CERULEA_MOBS:
        write_bytes(f"assets/{NS}/mob_models/{m}.json", open(os.path.join(mobs, f"{m}.json"), "rb").read())
        write_bytes(f"assets/{NS}/textures/entity/{m}.png", open(os.path.join(mobs, f"{m}.png"), "rb").read())
        g = os.path.join(mobs, f"{m}_glow.png")
        if os.path.exists(g):
            write_bytes(f"assets/{NS}/textures/entity/{m}_glow.png", open(g, "rb").read())
    write_bytes(f"assets/{NS}/textures/entity/ribbonfin_mire.png", open(os.path.join(mobs, "ribbonfin_mire.png"), "rb").read())
    write_bytes(f"assets/{NS}/textures/entity/projectiles/fang_arrow.png", open(os.path.join(items_dir, "entity_fang_arrow.png"), "rb").read())
    names = {
        "grazer_meat": ("Raw Grazer Meat", "Carne de pastoreador cruda"), "cooked_grazer_meat": ("Grazer Steak", "Filete de pastoreador"),
        "cerulean_leather": ("Cerulean Leather", "Cuero cerúleo"), "antenna_bulb": ("Antenna Bulb", "Bulbo de antena"),
        "hopper_meat": ("Raw Hopper", "Carne de saltarín cruda"), "cooked_hopper_meat": ("Cooked Hopper", "Saltarín cocinado"),
        "hopper_foot": ("Hopper Foot", "Pata de saltarín"), "glider_membrane": ("Glider Membrane", "Membrana de planeador"),
        "glowing_scale": ("Glowing Scale", "Escama luminosa"), "lurker_fang": ("Lurker Fang", "Colmillo de acechador"),
        "runner_fang": ("Runner Fang", "Colmillo de corredor"), "striped_pelt": ("Striped Pelt", "Piel rayada"),
        "fang_dagger": ("Fang Dagger", "Daga de colmillo"), "fang_arrow": ("Fang Arrow", "Flecha de colmillo"),
        "croaker_slime": ("Croaker Slime", "Baba de croador"), "croaker_legs": ("Raw Croaker Legs", "Ancas de croador"),
        "cooked_croaker_legs": ("Cooked Croaker Legs", "Ancas de croador cocinadas"),
        "ribbonfin": ("Raw Ribbonfin", "Pez de cinta crudo"), "cooked_ribbonfin": ("Cooked Ribbonfin", "Pez de cinta cocinado"),
        "ribbonfin_bucket": ("Bucket of Ribbonfin", "Cubeta con pez de cinta"), "croaker_bucket": ("Bucket of Croaker", "Cubeta con croador"),
        "armor_plate": ("Armor Plate", "Placa de coraza"), "ironjaw_blade": ("Ironjaw Blade", "Cuchilla de mandíbula"),
    }
    for item, (en, es) in names.items():
        write_bytes(f"assets/{NS}/textures/item/{item}.png", open(os.path.join(items_dir, f"item_{item}.png"), "rb").read())
        parent = "minecraft:item/handheld" if item == "fang_dagger" else "minecraft:item/generated"
        write(f"assets/{NS}/models/item/{item}.json", {"parent": parent, "textures": {"layer0": rl(f"item/{item}")}})
        LANG_EN[f"item.{NS}.{item}"] = en
        LANG_ES[f"item.{NS}.{item}"] = es
    for block, (en, es) in {"bulb_lamp": ("Bulb Lamp", "Lámpara de bulbo"),
                            "glowing_scale_block": ("Block of Glowing Scales", "Bloque de escamas luminosas")}.items():
        write_bytes(f"assets/{NS}/textures/block/{block}.png", open(os.path.join(items_dir, f"block_{block}.png"), "rb").read())
        write(f"assets/{NS}/blockstates/{block}.json", {"variants": {"": {"model": rl(f"block/{block}")}}})
        write(f"assets/{NS}/models/block/{block}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": rl(f"block/{block}")}})
        write(f"assets/{NS}/models/item/{block}.json", {"parent": rl(f"block/{block}")})
        LANG_EN[f"block.{NS}.{block}"] = en
        LANG_ES[f"block.{NS}.{block}"] = es
        data(f"loot_table/blocks/{block}", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": rl(block)}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    tag_add("data/minecraft/tags/block/mineable/pickaxe.json", rl("glowing_scale_block"))
    mob_names = {"grazer": ("Grazer", "Pastoreador"), "hopper": ("Hopper", "Saltarín"), "glider": ("Glider", "Planeador"),
                 "lurker": ("Lurker", "Acechador"), "croaker": ("Croaker", "Croador"), "ribbonfin": ("Ribbonfin", "Pez de cinta"),
                 "runner": ("Runner", "Corredor"), "ironjaw": ("Ironjaw", "Ironjaw")}
    for m, (en, es) in mob_names.items():
        LANG_EN[f"entity.{NS}.{m}"] = en
        LANG_ES[f"entity.{NS}.{m}"] = es
        write(f"assets/{NS}/models/item/{m}_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})
        LANG_EN[f"item.{NS}.{m}_spawn_egg"] = f"{en} Spawn Egg"
        LANG_ES[f"item.{NS}.{m}_spawn_egg"] = f"Huevo generador de {es.lower()}"
    # sonidos propios (tools/designs/cerulea_sounds.py → tools/designs/mobs/sounds_cerulea)
    sys.path.insert(0, DESIGNS)
    import cerulea_sounds  # noqa: E402
    snd_dir = os.path.join(mobs, "sounds_cerulea")
    sounds_json = {}
    for name, (_fn, count, en, es) in cerulea_sounds.SOUNDS.items():
        mob, kind = name.split(".")
        files = []
        for v in range(1, count + 1):
            write_bytes(f"assets/{NS}/sounds/cerulea/{mob}/{kind}{v}.ogg", open(os.path.join(snd_dir, mob, f"{kind}{v}.ogg"), "rb").read())
            files.append(rl(f"cerulea/{mob}/{kind}{v}"))
        sub = f"subtitles.{NS}.entity.{name}"
        sounds_json[f"entity.{name}"] = {"subtitle": sub, "sounds": files}
        LANG_EN[sub] = en
        LANG_ES[sub] = es
    # 1.0.3: monstruos de Ferra (tools/designs/ferra_sounds.py → tools/designs/mobs/sounds_ferra)
    import ferra_sounds  # noqa: E402
    fdir = os.path.join(mobs, "sounds_ferra")
    for name, (_fn, count, en, es) in ferra_sounds.SOUNDS.items():
        mob, kind = name.split(".")
        files = []
        for v in range(1, count + 1):
            write_bytes(f"assets/{NS}/sounds/ferra/{mob}/{kind}{v}.ogg", open(os.path.join(fdir, mob, f"{kind}{v}.ogg"), "rb").read())
            files.append(rl(f"ferra/{mob}/{kind}{v}"))
        sub = f"subtitles.{NS}.entity.{name}"
        sounds_json[f"entity.{name}"] = {"subtitle": sub, "sounds": files}
        LANG_EN[sub] = en
        LANG_ES[sub] = es
    write(f"assets/{NS}/sounds.json", sounds_json)
    LANG_EN[f"entity.{NS}.fang_arrow"] = "Fang Arrow"
    LANG_ES[f"entity.{NS}.fang_arrow"] = "Flecha de colmillo"

    # botín (domado, el corredor no suelta nada: lo decide Runner.shouldDropLoot)
    def drop(item, lo, hi, chance=None, smelt=False, looting=1.0):
        fns = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
        if looting:
            fns.append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                        "count": {"type": "minecraft:uniform", "min": 0, "max": looting}})
        if smelt:
            fns.append({"function": "minecraft:furnace_smelt", "conditions": [{"condition": "minecraft:entity_properties",
                        "entity": "this", "predicate": {"flags": {"is_on_fire": True}}}]})
        pool = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "functions": fns}]}
        if chance is not None:
            pool["conditions"] = [{"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
                                   "unenchanted_chance": chance, "enchanted_chance": {"type": "minecraft:linear", "base": chance + 0.05,
                                                                                    "per_level_above_first": 0.05}}]
        return pool
    loot = {
        "grazer": [drop(rl("grazer_meat"), 1, 3, smelt=True), drop(rl("cerulean_leather"), 0, 2), drop(rl("antenna_bulb"), 1, 1, 0.2, looting=0)],
        "hopper": [drop(rl("hopper_meat"), 1, 1, smelt=True, looting=0), drop(rl("hopper_foot"), 1, 1, 0.1, looting=0)],
        "glider": [drop(rl("glider_membrane"), 1, 2)],
        "lurker": [drop(rl("glowing_scale"), 1, 3), drop(rl("lurker_fang"), 0, 1)],
        "runner": [drop(rl("runner_fang"), 0, 2), drop(rl("striped_pelt"), 0, 1)],
        "croaker": [drop(rl("croaker_slime"), 0, 2), drop(rl("croaker_legs"), 0, 1, smelt=True)],
        "ribbonfin": [drop(rl("ribbonfin"), 1, 1, smelt=True, looting=0), drop("minecraft:bone_meal", 1, 1, 0.05, looting=0)],
        "ironjaw": [drop(rl("armor_plate"), 1, 3), drop(rl("ironjaw_blade"), 1, 1, 0.15, looting=0)],
    }
    for m, pools in loot.items():
        data(f"loot_table/entities/{m}", {"type": "minecraft:entity", "pools": pools})

    # recetas
    def cook(raw, cooked, xp=0.35):
        for kind, t, suffix in (("smelting", 200, ""), ("smoking", 100, "_from_smoking"), ("campfire_cooking", 600, "_from_campfire")):
            data(f"recipe/{cooked}{suffix}", {"type": f"minecraft:{kind}", "ingredient": {"item": rl(raw)},
                                              "result": {"id": rl(cooked)}, "experience": xp, "cookingtime": t})
    cook("grazer_meat", "cooked_grazer_meat")
    cook("hopper_meat", "cooked_hopper_meat")
    cook("croaker_legs", "cooked_croaker_legs")
    cook("ribbonfin", "cooked_ribbonfin")

    def ing(v):
        return {"tag": v[1:]} if v.startswith("#") else {"item": v}

    def shaped(name, pattern, key, result, count=1, cat="misc"):
        data(f"recipe/{name}", {"type": "minecraft:crafting_shaped", "category": cat, "pattern": pattern,
                                "key": {k: ing(v) for k, v in key.items()}, "result": {"id": result, "count": count}})

    def shapeless(name, items, result, count=1):
        data(f"recipe/{name}", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                "ingredients": [ing(i) for i in items], "result": {"id": result, "count": count}})
    shaped("bulb_lamp", [" B ", "BGB", " B "], {"B": rl("antenna_bulb"), "G": "minecraft:glass"}, rl("bulb_lamp"), cat="building")
    shaped("glowing_scale_block", ["SS", "SS"], {"S": rl("glowing_scale")}, rl("glowing_scale_block"), cat="building")
    shapeless("glowing_scale_from_block", [rl("glowing_scale_block")], rl("glowing_scale"), 4)
    shapeless("cyan_dye_from_glowing_scale", [rl("glowing_scale")], "minecraft:cyan_dye", 1)
    shapeless("leather_from_cerulean_leather", [rl("cerulean_leather")], "minecraft:leather", 1)
    shapeless("leather_from_striped_pelt", [rl("striped_pelt")], "minecraft:leather", 1)
    shapeless("slime_ball_from_croaker_slime", [rl("croaker_slime")], "minecraft:slime_ball", 1)
    shaped("fang_dagger", ["F", "P"], {"F": f"#{NS}:fangs", "P": rl("striped_pelt")}, rl("fang_dagger"), cat="equipment")
    shaped("fang_arrow", ["F", "S", "E"], {"F": f"#{NS}:fangs", "S": "minecraft:stick", "E": "minecraft:feather"},
           rl("fang_arrow"), 4, cat="equipment")

    # etiquetas
    for f in ("runner_fang", "lurker_fang"):
        tag_add(f"data/{NS}/tags/item/fangs.json", rl(f))
    tag_add("data/minecraft/tags/item/arrows.json", rl("fang_arrow"))
    tag_add("data/minecraft/tags/item/swords.json", rl("fang_dagger"))
    for f in ("grazer_meat", "cooked_grazer_meat", "hopper_meat", "cooked_hopper_meat"):
        tag_add("data/minecraft/tags/item/meat.json", rl(f))
    for f in ("ribbonfin", "cooked_ribbonfin"):
        tag_add("data/minecraft/tags/item/fishes.json", rl(f))
    for f in ("croaker_slime",):
        tag_add("data/c/tags/item/slime_balls.json", rl(f))
    for f in ("cerulean_leather", "striped_pelt"):
        tag_add("data/c/tags/item/leathers.json", rl(f))
    for m in ("lurker", "croaker", "ironjaw"):
        tag_add("data/minecraft/tags/entity_type/can_breathe_under_water.json", rl(m))
    tag_add("data/minecraft/tags/entity_type/arrows.json", rl("fang_arrow"))
    tag_add("data/minecraft/tags/entity_type/impact_projectiles.json", rl("fang_arrow"))


def build_cerulea():
    P = "cerulea"
    B = lambda n: rl(n)          # noqa: E731
    tex_block = os.path.join(CERULEA_TEX, "block")
    for f in sorted(os.listdir(tex_block)):
        write_bytes(f"assets/{NS}/textures/block/{f}", open(os.path.join(tex_block, f), "rb").read())
    tex_item = os.path.join(CERULEA_TEX, "item")
    for f in sorted(os.listdir(tex_item)):
        write_bytes(f"assets/{NS}/textures/item/{f}", open(os.path.join(tex_item, f), "rb").read())

    def name_(kind, n, en, es):
        LANG_EN[f"{kind}.{NS}.{n}"] = en
        LANG_ES[f"{kind}.{NS}.{n}"] = es

    def bstate(n, variants):
        write(f"assets/{NS}/blockstates/{n}.json", {"variants": variants})

    def model(n, parent, textures, render=None):
        m = {"parent": parent, "textures": {k: (rl(f"block/{v}") if ":" not in v else v) for k, v in textures.items()}}
        if render:
            m["render_type"] = render
        write(f"assets/{NS}/models/block/{n}.json", m)

    def item_block(n, model_name=None):
        write(f"assets/{NS}/models/item/{n}.json", {"parent": rl(f"block/{model_name or n}")})

    def item_sprite(n, texture):
        write(f"assets/{NS}/models/item/{n}.json", {"parent": "minecraft:item/generated",
                                                     "textures": {"layer0": rl(texture)}})

    def loot(n, pools):
        data(f"loot_table/blocks/{n}", {"type": "minecraft:block", "pools": pools,
                                         "random_sequence": rl(f"blocks/{n}")})

    def pool(entry, conditions=None):
        p = {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry]}
        if conditions:
            p["conditions"] = conditions
        return p

    SURVIVES = [{"condition": "minecraft:survives_explosion"}]
    SILK = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
    SHEARS = {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}}
    SILK_OR_SHEARS = {"condition": "minecraft:any_of", "terms": [SHEARS, SILK]}

    def drop_self(n):
        loot(n, [pool({"type": "minecraft:item", "name": B(n)}, SURVIVES)])

    def silk_else(n, other, fn=None):
        alt = {"type": "minecraft:item", "name": other}
        if fn:
            alt["functions"] = fn
        loot(n, [pool({"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": B(n), "conditions": [SILK]}, dict(alt, conditions=SURVIVES)]})])

    def cube(n, en, es, texture=None, render=None):
        model(n, "minecraft:block/cube_all", {"all": texture or n}, render)
        bstate(n, {"": {"model": rl(f"block/{n}")}})
        item_block(n)
        name_("block", n, en, es)

    # --------------------------------------------------------------- suelo y piedra
    for n, en, es, soil_drop in (("cerulean_grass_block", "Cerulean Grass Block", "Bloque de pasto cerúleo", True),
                                 ("cerulean_meadow_grass_block", "Cerulean Meadow Grass", "Pasto de pradera cerúleo", True)):
        model(n, "minecraft:block/cube_bottom_top", {"top": f"{n}_top", "side": f"{n}_side", "bottom": "cerulean_soil"})
        bstate(n, {"": [{"model": rl(f"block/{n}")}, {"model": rl(f"block/{n}"), "y": 90},
                        {"model": rl(f"block/{n}"), "y": 180}, {"model": rl(f"block/{n}"), "y": 270}]})
        item_block(n)
        name_("block", n, en, es)
        silk_else(n, B("cerulean_soil"))
    for n, en, es in (("cerulean_soil", "Cerulean Soil", "Tierra cerúlea"), ("cerulean_mud", "Cerulean Mud", "Lodo cerúleo"),
                      ("cerulean_sand", "Pale Sand", "Arena pálida"), ("cerulean_cobblestone", "Cerulean Cobblestone", "Roca cerúlea"),
                      ("cerulean_stone_bricks", "Cerulean Stone Bricks", "Ladrillos de piedra cerúlea"),
                      ("polished_cerulean_stone", "Polished Cerulean Stone", "Piedra cerúlea pulida"),
                      ("deep_cerulean_stone", "Deep Cerulean Stone", "Piedra cerúlea profunda")):
        cube(n, en, es)
        drop_self(n)
    cube("cerulean_stone", "Cerulean Stone", "Piedra cerúlea")
    silk_else("cerulean_stone", B("cerulean_cobblestone"))

    # --------------------------------------------------------------- minerales
    ORE_DROP = {"coal": ("minecraft:coal", None), "iron": ("minecraft:raw_iron", None), "copper": ("minecraft:raw_copper", (2, 5)),
                "gold": ("minecraft:raw_gold", None), "redstone": ("minecraft:redstone", (4, 5)), "lapis": ("minecraft:lapis_lazuli", (4, 9)),
                "diamond": ("minecraft:diamond", None), "emerald": ("minecraft:emerald", None), "zinc": ("create:raw_zinc", None),
                "crystal": (B("cerulean_crystal"), (1, 2))}
    for ore in CERULEA_ORES:
        item, count = ORE_DROP[ore]
        fns = []
        if count:
            fns.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}})
        if ore in ("redstone",):
            fns.append({"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:uniform_bonus_count",
                        "parameters": {"bonusMultiplier": 1}})
        else:
            fns.append({"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"})
        fns.append({"function": "minecraft:explosion_decay"})
        en, es = ORE_NAMES[ore]
        for deep in (False, True):
            n = f"{'deep_' if deep else ''}cerulean_{ore}_ore"
            cube(n, f"{'Deep ' if deep else ''}Cerulean {en} Ore",
                 f"Mena de {es} en piedra cerúlea{' profunda' if deep else ''}")
            loot(n, [pool({"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "name": B(n), "conditions": [SILK]},
                {"type": "minecraft:item", "name": item, "functions": fns}]})])

    # --------------------------------------------------------------- árboles
    WOODS = {"silverwood": ("Silverwood", "plateado"), "duskwood": ("Duskwood", "violeta"), "mirewood": ("Mirewood", "del pantano")}
    for w, (en, es) in WOODS.items():
        n = f"{w}_log"
        model(n, "minecraft:block/cube_column", {"end": f"{w}_log_top", "side": f"{w}_log"})
        model(n + "_horizontal", "minecraft:block/cube_column_horizontal", {"end": f"{w}_log_top", "side": f"{w}_log"})
        bstate(n, {"axis=y": {"model": rl(f"block/{n}")}, "axis=z": {"model": rl(f"block/{n}_horizontal"), "x": 90},
                   "axis=x": {"model": rl(f"block/{n}_horizontal"), "x": 90, "y": 90}})
        item_block(n)
        name_("block", n, f"{en} Log", f"Tronco {es}")
        drop_self(n)
        cube(f"{w}_planks", f"{en} Planks", f"Tablones de árbol {es}")
        drop_self(f"{w}_planks")
        data(f"recipe/cerulea/{w}_planks", {"type": "minecraft:crafting_shapeless", "category": "building", "group": "planks",
                                            "ingredients": [{"item": B(n)}], "result": {"id": B(f"{w}_planks"), "count": 4}})
        s_ = f"{w}_sapling"
        model(s_, "minecraft:block/cross", {"cross": s_}, "minecraft:cutout")
        bstate(s_, {"stage=0": {"model": rl(f"block/{s_}")}, "stage=1": {"model": rl(f"block/{s_}")}})
        item_sprite(s_, f"block/{s_}")
        name_("block", s_, f"{en} Sapling", f"Retoño de árbol {es}")
        drop_self(s_)
    leaves = {"silverwood_leaves": ("Silverwood Leaves", "Hojas de árbol plateado", "silverwood_sapling", 0.04),
              "duskwood_leaves": ("Duskwood Leaves", "Hojas de árbol violeta", "duskwood_sapling", 0.05),
              "blue_duskwood_leaves": ("Blue Duskwood Leaves", "Hojas azules de árbol violeta", "duskwood_sapling", 0.05),
              "mirewood_leaves": ("Mirewood Leaves", "Hojas de árbol del pantano", "mirewood_sapling", 0.05),
              "cerulean_bush": ("Cerulean Bush", "Arbusto cerúleo", None, 0.0)}
    for n, (en, es, sap, chance) in leaves.items():
        model(n, "minecraft:block/leaves", {"all": n}, "minecraft:cutout_mipped")
        bstate(n, {"": {"model": rl(f"block/{n}")}})
        item_block(n)
        name_("block", n, en, es)
        children = [{"type": "minecraft:item", "name": B(n), "conditions": [SILK_OR_SHEARS]}]
        if sap:
            children.append({"type": "minecraft:item", "name": B(sap), "conditions": [
                SURVIVES[0], {"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                              "chances": [chance, chance * 1.25, chance * 1.6, chance * 2.4]}]})
        pools = [pool({"type": "minecraft:alternatives", "children": children})]
        pools.append(pool({"type": "minecraft:item", "name": "minecraft:stick", "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}},
            {"function": "minecraft:explosion_decay"}]}, [{"condition": "minecraft:inverted", "term": SILK_OR_SHEARS},
                                                         {"condition": "minecraft:random_chance", "chance": 0.02}]))
        loot(n, pools)

    # --------------------------------------------------------------- escaleras, losas, puertas y trampillas
    FACING_Y = {"east": 0, "south": 90, "west": 180, "north": 270}
    shapes = {"straight": "", "inner_left": "_inner", "inner_right": "_inner", "outer_left": "_outer", "outer_right": "_outer"}
    MATS = [("cerulean_stone", "cerulean_stone", "Cerulean Stone", "de piedra cerúlea", "stone"),
            ("cerulean_cobblestone", "cerulean_cobblestone", "Cerulean Cobblestone", "de roca cerúlea", "stone"),
            ("cerulean_stone_brick", "cerulean_stone_bricks", "Cerulean Stone Brick", "de ladrillos de piedra cerúlea", "stone"),
            ("polished_cerulean_stone", "polished_cerulean_stone", "Polished Cerulean Stone", "de piedra cerúlea pulida", "stone"),
            ("deep_cerulean_stone", "deep_cerulean_stone", "Deep Cerulean Stone", "de piedra cerúlea profunda", "stone"),
            ("silverwood", "silverwood_planks", "Silverwood", "de árbol plateado", "wood"),
            ("duskwood", "duskwood_planks", "Duskwood", "de árbol violeta", "wood"),
            ("mirewood", "mirewood_planks", "Mirewood", "de árbol del pantano", "wood")]
    stair_ids, slab_ids, wood_stairs, wood_slabs = [], [], [], []
    for prefix, base, en, es, kind in MATS:
        st_, sl_ = f"{prefix}_stairs", f"{prefix}_slab"
        tx = {"bottom": base, "top": base, "side": base}
        model(st_, "minecraft:block/stairs", tx)
        model(st_ + "_inner", "minecraft:block/inner_stairs", tx)
        model(st_ + "_outer", "minecraft:block/outer_stairs", tx)
        var = {}
        for facing, y0 in FACING_Y.items():
            for half in ("bottom", "top"):
                for shape, suffix in shapes.items():
                    y = y0
                    if shape in ("inner_left", "outer_left") and half == "bottom":
                        y += 270
                    if shape in ("inner_right", "outer_right") and half == "top":
                        y += 90
                    v = {"model": rl(f"block/{st_}{suffix}")}
                    if half == "top":
                        v["x"] = 180
                    if y % 360:
                        v["y"] = y % 360
                    if "x" in v or "y" in v:
                        v["uvlock"] = True
                    var[f"facing={facing},half={half},shape={shape}"] = v
        bstate(st_, var)
        item_block(st_)
        name_("block", st_, f"{en} Stairs", f"Escaleras {es}")
        drop_self(st_)
        model(sl_, "minecraft:block/slab", tx)
        model(sl_ + "_top", "minecraft:block/slab_top", tx)
        bstate(sl_, {"type=bottom": {"model": rl(f"block/{sl_}")}, "type=top": {"model": rl(f"block/{sl_}_top")},
                     "type=double": {"model": rl(f"block/{base}")}})
        item_block(sl_)
        name_("block", sl_, f"{en} Slab", f"Losa {es}")
        loot(sl_, [pool({"type": "minecraft:item", "name": B(sl_), "functions": [
            {"function": "minecraft:set_count", "count": 2.0, "add": False,
             "conditions": [{"condition": "minecraft:block_state_property", "block": B(sl_), "properties": {"type": "double"}}]},
            {"function": "minecraft:explosion_decay"}]})])
        data(f"recipe/cerulea/{st_}", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["#  ", "## ", "###"],
                                       "key": {"#": {"item": B(base)}}, "result": {"id": B(st_), "count": 4}})
        data(f"recipe/cerulea/{sl_}", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["###"],
                                       "key": {"#": {"item": B(base)}}, "result": {"id": B(sl_), "count": 6}})
        if kind == "stone":
            for out, cnt in ((st_, 1), (sl_, 2)):
                data(f"recipe/cerulea/{out}_from_{base}_stonecutting", {"type": "minecraft:stonecutting",
                     "ingredient": {"item": B(base)}, "result": {"id": B(out), "count": cnt}})
        stair_ids.append(B(st_))
        slab_ids.append(B(sl_))
        if kind == "wood":
            wood_stairs.append(B(st_))
            wood_slabs.append(B(sl_))
    door_ids, trap_ids = [], []
    for w, (en, es) in WOODS.items():
        dn, tn = f"{w}_door", f"{w}_trapdoor"
        for part in ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open",
                     "top_left", "top_left_open", "top_right", "top_right_open"):
            model(f"{dn}_{part}", f"minecraft:block/door_{part}", {"bottom": f"{dn}_bottom", "top": f"{dn}_top"}, "minecraft:cutout")
        var = {}
        for facing, y0 in FACING_Y.items():
            for half, hp in (("lower", "bottom"), ("upper", "top")):
                for hinge in ("left", "right"):
                    for opened in (False, True):
                        y = y0 + ((90 if hinge == "left" else 270) if opened else 0)
                        v = {"model": rl(f"block/{dn}_{hp}_{hinge}{'_open' if opened else ''}")}
                        if y % 360:
                            v["y"] = y % 360
                        var[f"facing={facing},half={half},hinge={hinge},open={'true' if opened else 'false'}"] = v
        bstate(dn, var)
        item_sprite(dn, f"item/{dn}")
        name_("block", dn, f"{en} Door", f"Puerta de árbol {es}")
        loot(dn, [pool({"type": "minecraft:item", "name": B(dn), "conditions": [
            {"condition": "minecraft:block_state_property", "block": B(dn), "properties": {"half": "lower"}}]}, SURVIVES)])
        for part in ("bottom", "top", "open"):
            model(f"{tn}_{part}", f"minecraft:block/template_orientable_trapdoor_{part}", {"texture": tn}, "minecraft:cutout")
        TY = {"north": 0, "east": 90, "south": 180, "west": 270}
        var = {}
        for facing, y0 in TY.items():
            for half in ("bottom", "top"):
                for opened in (False, True):
                    if opened:
                        v = {"model": rl(f"block/{tn}_open")}
                        y = (y0 + 180) % 360 if half == "top" else y0
                        if half == "top":
                            v["x"] = 180
                    else:
                        v = {"model": rl(f"block/{tn}_{half}")}
                        y = y0
                    if y:
                        v["y"] = y
                    var[f"facing={facing},half={half},open={'true' if opened else 'false'}"] = v
        bstate(tn, var)
        item_block(tn, f"{tn}_bottom")
        name_("block", tn, f"{en} Trapdoor", f"Trampilla de árbol {es}")
        drop_self(tn)
        planks_id = B(f"{w}_planks")
        data(f"recipe/cerulea/{dn}", {"type": "minecraft:crafting_shaped", "category": "redstone", "group": "wooden_door",
                                      "pattern": ["##", "##", "##"], "key": {"#": {"item": planks_id}}, "result": {"id": B(dn), "count": 3}})
        data(f"recipe/cerulea/{tn}", {"type": "minecraft:crafting_shaped", "category": "redstone", "group": "wooden_trapdoor",
                                      "pattern": ["###", "###"], "key": {"#": {"item": planks_id}}, "result": {"id": B(tn), "count": 2}})
        door_ids.append(B(dn))
        trap_ids.append(B(tn))

    # --------------------------------------------------------------- plantas
    def cross(n, en, es, tex=None, drops=None, shears=False):
        model(n, "minecraft:block/cross", {"cross": tex or n}, "minecraft:cutout")
        bstate(n, {"": {"model": rl(f"block/{n}")}})
        item_sprite(n, f"block/{tex or n}")
        name_("block", n, en, es)
        if shears:
            children = [{"type": "minecraft:item", "name": B(n), "conditions": [SHEARS]}]
            if drops:
                children.append(drops)
            loot(n, [pool({"type": "minecraft:alternatives", "children": children})])
        else:
            drop_self(n)

    root_seed = {"type": "minecraft:item", "name": B("cerulean_root"), "conditions": [
        {"condition": "minecraft:random_chance", "chance": 0.05}]}
    cross("cerulean_grass", "Cerulean Grass", "Pasto cerúleo", drops=root_seed, shears=True)
    cross("azure_flower", "Azure Flower", "Flor azul")
    cross("glowbloom", "Glowbloom", "Flor luminosa")
    cross("glowcap", "Glowcap", "Hongo luminoso")
    cross("mire_vine", "Mire Vine", "Liana del pantano", shears=True)
    # pasto alto y junco (dos bloques)
    for n, en, es, drop in (("tall_cerulean_grass", "Tall Cerulean Grass", "Pasto cerúleo alto", None),
                            ("mire_reed", "Mire Reed", "Junco del pantano", "reed_pod")):
        model(f"{n}_bottom", "minecraft:block/cross", {"cross": f"{n}_bottom"}, "minecraft:cutout")
        model(f"{n}_top", "minecraft:block/cross", {"cross": f"{n}_top"}, "minecraft:cutout")
        bstate(n, {"half=lower": {"model": rl(f"block/{n}_bottom")}, "half=upper": {"model": rl(f"block/{n}_top")}})
        name_("block", n, en, es)
        lower = [{"condition": "minecraft:block_state_property", "block": B(n), "properties": {"half": "lower"}}]
        if drop:
            loot(n, [pool({"type": "minecraft:item", "name": B(drop), "functions": [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]}, lower)])
        else:
            loot(n, [pool({"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "name": B(n), "conditions": [SHEARS]}, dict(root_seed)]}, lower)])
    item_sprite("tall_cerulean_grass", "block/tall_cerulean_grass_top")
    # nenúfar
    model("azure_lily_pad", "minecraft:block/lily_pad", {"texture": "azure_lily_pad", "particle": "azure_lily_pad"}, "minecraft:cutout")
    bstate("azure_lily_pad", {"": [{"model": rl("block/azure_lily_pad")}, {"model": rl("block/azure_lily_pad"), "y": 90},
                                   {"model": rl("block/azure_lily_pad"), "y": 180}, {"model": rl("block/azure_lily_pad"), "y": 270}]})
    item_sprite("azure_lily_pad", "block/azure_lily_pad")
    name_("block", "azure_lily_pad", "Azure Lily Pad", "Nenúfar azul")
    drop_self("azure_lily_pad")

    # --------------------------------------------------------------- plantas del mar (cerulea_sea_plants.py)
    for n in ("ribbon_kelp", "ribbon_kelp_plant", "cerulean_seagrass", "glow_tubes", "azure_fan"):
        model(n, "minecraft:block/cross", {"cross": n}, "minecraft:cutout")
    bstate("ribbon_kelp", {f"age={a}": {"model": rl("block/ribbon_kelp")} for a in range(26)})
    bstate("ribbon_kelp_plant", {"": {"model": rl("block/ribbon_kelp_plant")}})
    for n in ("pearl_weed", "pearl_weed_lit", "pearl_weed_plant", "pearl_weed_plant_lit"):
        model(n, "minecraft:block/cross", {"cross": n}, "minecraft:cutout")
    bstate("pearl_weed", {f"age={a},berries={b}": {"model": rl("block/pearl_weed" + ("_lit" if b == "true" else ""))}
                          for a in range(26) for b in ("false", "true")})
    bstate("pearl_weed_plant", {f"berries={b}": {"model": rl("block/pearl_weed_plant" + ("_lit" if b == "true" else ""))}
                                for b in ("false", "true")})
    bstate("cerulean_seagrass", {"": {"model": rl("block/cerulean_seagrass")}})
    model("tall_cerulean_seagrass_bottom", "minecraft:block/cross", {"cross": "tall_cerulean_seagrass_bottom"}, "minecraft:cutout")
    model("tall_cerulean_seagrass_top", "minecraft:block/cross", {"cross": "tall_cerulean_seagrass_top"}, "minecraft:cutout")
    bstate("tall_cerulean_seagrass", {"half=lower": {"model": rl("block/tall_cerulean_seagrass_bottom")},
                                      "half=upper": {"model": rl("block/tall_cerulean_seagrass_top")}})
    for n in ("glow_tubes", "azure_fan"):
        bstate(n, {f"waterlogged={w}": {"model": rl(f"block/{n}")} for w in ("false", "true")})
        item_sprite(n, f"block/{n}")
        drop_self(n)
    model("dried_ribbon_kelp_block", "minecraft:block/cube_bottom_top", {"top": "dried_ribbon_kelp_block_top",
          "bottom": "dried_ribbon_kelp_block_top", "side": "dried_ribbon_kelp_block_side"})
    bstate("dried_ribbon_kelp_block", {"": {"model": rl("block/dried_ribbon_kelp_block")}})
    item_block("dried_ribbon_kelp_block")
    drop_self("dried_ribbon_kelp_block")
    item_sprite("ribbon_kelp", "block/ribbon_kelp")
    item_sprite("cerulean_seagrass", "block/cerulean_seagrass")
    item_sprite("glow_pearls", "item/glow_pearls")
    item_sprite("dried_ribbon_kelp", "item/dried_ribbon_kelp")
    for n, en, es in (("ribbon_kelp", "Ribbon Kelp", "Alga de cinta"), ("ribbon_kelp_plant", "Ribbon Kelp Plant", "Planta de alga de cinta"),
                      ("pearl_weed", "Pearl Weed", "Alga de perlas"), ("pearl_weed_plant", "Pearl Weed Plant", "Planta de alga de perlas"),
                      ("cerulean_seagrass", "Cerulean Seagrass", "Pasto marino cerúleo"),
                      ("tall_cerulean_seagrass", "Tall Cerulean Seagrass", "Pasto marino cerúleo alto"),
                      ("glow_tubes", "Glow Tubes", "Tubos luminosos"), ("azure_fan", "Azure Fan", "Abanico azul"),
                      ("dried_ribbon_kelp_block", "Dried Ribbon Kelp Block", "Bloque de alga de cinta seca")):
        name_("block", n, en, es)
    name_("item", "glow_pearls", "Glow Pearls", "Perlas luminosas")
    name_("item", "dried_ribbon_kelp", "Dried Ribbon Kelp", "Alga de cinta seca")
    for n in ("ribbon_kelp", "ribbon_kelp_plant"):
        loot(n, [pool({"type": "minecraft:item", "name": B("ribbon_kelp")}, SURVIVES)])
    for n in ("pearl_weed", "pearl_weed_plant"):
        loot(n, [pool({"type": "minecraft:item", "name": B("glow_pearls")},
                      [{"condition": "minecraft:block_state_property", "block": B(n), "properties": {"berries": "true"}}])])
    loot("cerulean_seagrass", [pool({"type": "minecraft:item", "name": B("cerulean_seagrass")}, [SHEARS])])
    loot("tall_cerulean_seagrass", [pool({"type": "minecraft:item", "name": B("cerulean_seagrass"), "functions": [
        {"function": "minecraft:set_count", "count": 2}]}, [SHEARS, {"condition": "minecraft:block_state_property",
                                                                  "block": B("tall_cerulean_seagrass"), "properties": {"half": "lower"}}])])
    for kind, t, suf in (("smelting", 200, ""), ("smoking", 100, "_from_smoking"), ("campfire_cooking", 600, "_from_campfire")):
        data(f"recipe/cerulea/dried_ribbon_kelp{suf}", {"type": f"minecraft:{kind}", "ingredient": {"item": B("ribbon_kelp")},
                                                       "result": {"id": B("dried_ribbon_kelp")}, "experience": 0.1, "cookingtime": t})
    data("recipe/cerulea/dried_ribbon_kelp_block", {"type": "minecraft:crafting_shaped", "category": "building",
                                                   "pattern": ["###", "###", "###"], "key": {"#": {"item": B("dried_ribbon_kelp")}},
                                                   "result": {"id": B("dried_ribbon_kelp_block")}})
    data("recipe/cerulea/dried_ribbon_kelp_from_block", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                        "ingredients": [{"item": B("dried_ribbon_kelp_block")}],
                                                        "result": {"id": B("dried_ribbon_kelp"), "count": 9}})
    data("recipe/cerulea/glow_ink_sac_from_glow_pearls", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                         "ingredients": [{"item": B("glow_pearls")}, {"item": B("glow_pearls")}],
                                                         "result": {"id": "minecraft:glow_ink_sac"}})
    datamap_add("data/neoforge/data_maps/item/furnace_fuels.json", {B("dried_ribbon_kelp_block"): {"burn_time": 4001}})
    datamap_add("data/neoforge/data_maps/item/compostables.json", {
        B("ribbon_kelp"): {"chance": 0.3}, B("dried_ribbon_kelp"): {"chance": 0.3}, B("dried_ribbon_kelp_block"): {"chance": 0.5},
        B("cerulean_seagrass"): {"chance": 0.3}, B("glow_pearls"): {"chance": 0.3}, B("glow_tubes"): {"chance": 0.65},
        B("azure_fan"): {"chance": 0.65}})
    tag_add("data/minecraft/tags/block/mineable/hoe.json", B("dried_ribbon_kelp_block"))
    for n in ("ribbon_kelp", "ribbon_kelp_plant", "pearl_weed", "pearl_weed_plant"):
        tag_add("data/minecraft/tags/block/sword_efficient.json", B(n))

    # --------------------------------------------------------------- comestibles
    for k in (0, 1):
        model(f"blueberry_bush_stage{k}", "minecraft:block/cross", {"cross": f"blueberry_bush_stage{k}"}, "minecraft:cutout")
    bstate("blueberry_bush", {f"age={a}": {"model": rl(f"block/blueberry_bush_stage{0 if a < 2 else 1}")} for a in range(4)})
    name_("block", "blueberry_bush", "Blueberry Bush", "Arbusto de bayas azules")
    loot("blueberry_bush", [
        pool({"type": "minecraft:item", "name": B("blueberries"), "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 3}},
            {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:uniform_bonus_count",
             "parameters": {"bonusMultiplier": 1}}]},
             [{"condition": "minecraft:block_state_property", "block": B("blueberry_bush"), "properties": {"age": "3"}}]),
        pool({"type": "minecraft:item", "name": B("blueberries"), "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}}]},
             [{"condition": "minecraft:block_state_property", "block": B("blueberry_bush"), "properties": {"age": "2"}}])])
    for s_ in range(4):
        model(f"cerulean_roots_stage{s_}", "minecraft:block/crop", {"crop": f"cerulean_roots_stage{s_}"}, "minecraft:cutout")
    bstate("cerulean_roots", {f"age={a}": {"model": rl(f"block/cerulean_roots_stage{a}")} for a in range(4)})
    name_("block", "cerulean_roots", "Cerulean Roots", "Raíces cerúleas")
    ripe = [{"condition": "minecraft:block_state_property", "block": B("cerulean_roots"), "properties": {"age": "3"}}]
    loot("cerulean_roots", [
        pool({"type": "minecraft:item", "name": B("cerulean_root")}),
        pool({"type": "minecraft:item", "name": B("cerulean_root"), "functions": [
            {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:binomial_with_bonus_count",
             "parameters": {"extra": 3, "probability": 0.5714286}}]}, ripe)])
    model("duskfruit", "minecraft:block/cross", {"cross": "duskfruit"}, "minecraft:cutout")
    bstate("duskfruit", {"": {"model": rl("block/duskfruit")}})
    name_("block", "duskfruit", "Duskfruit", "Fruta violeta")
    loot("duskfruit", [pool({"type": "minecraft:item", "name": B("duskfruit")})])
    for n, en, es in (("blueberries", "Blueberries", "Bayas azules"), ("duskfruit", "Duskfruit", "Fruta violeta"),
                      ("cerulean_root", "Cerulean Root", "Raíz cerúlea"), ("baked_cerulean_root", "Baked Cerulean Root", "Raíz cerúlea asada"),
                      ("reed_pod", "Reed Pod", "Vaina de junco"), ("glowcap_stew", "Glowcap Stew", "Sopa de hongo luminoso"),
                      ("cerulean_crystal", "Cerulean Crystal", "Cristal cerúleo")):
        item_sprite(n, f"item/{n}")
        name_("item", n, en, es)

    # --------------------------------------------------------------- recetas
    def smelt(n, ing, out, xp=0.1, kinds=("smelting",)):
        for k in kinds:
            data(f"recipe/cerulea/{n}_{k}", {"type": f"minecraft:{k}", "category": "food" if "root" in out else "blocks",
                                              "ingredient": {"item": ing}, "result": {"id": out}, "experience": xp,
                                              "cookingtime": {"smelting": 200, "smoking": 100, "campfire_cooking": 600}[k]})
    smelt("baked_cerulean_root", B("cerulean_root"), B("baked_cerulean_root"), 0.35, ("smelting", "smoking", "campfire_cooking"))
    smelt("cerulean_stone", B("cerulean_cobblestone"), B("cerulean_stone"))
    data("recipe/cerulea/glowcap_stew", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                         "ingredients": [{"item": "minecraft:bowl"}, {"item": B("glowcap")}, {"item": B("glowcap")}],
                                         "result": {"id": B("glowcap_stew"), "count": 1}})
    for out, ing in (("polished_cerulean_stone", "cerulean_stone"), ("cerulean_stone_bricks", "polished_cerulean_stone")):
        data(f"recipe/cerulea/{out}", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["##", "##"],
                                       "key": {"#": {"item": B(ing)}}, "result": {"id": B(out), "count": 4}})
        data(f"recipe/cerulea/{out}_stonecutting", {"type": "minecraft:stonecutting", "ingredient": {"item": B("cerulean_stone")},
                                                    "result": {"id": B(out), "count": 1}})

    # --------------------------------------------------------------- etiquetas
    def tag(kind, tagname, values):
        for v in values:
            tag_add(f"data/{tagname.split(':')[0]}/tags/{kind}/{tagname.split(':')[1]}.json", v)

    grass_like = [B("cerulean_grass_block"), B("cerulean_meadow_grass_block"), B("cerulean_soil"), B("cerulean_mud")]
    stones = [B("cerulean_stone"), B("deep_cerulean_stone")]
    ores_all = [B(f"{d}cerulean_{o}_ore") for o in CERULEA_ORES for d in ("", "deep_")]
    logs = [B(f"{w}_log") for w in WOODS]
    planks = [B(f"{w}_planks") for w in WOODS]
    leaf_ids = [B(n) for n in leaves]
    saplings = [B(f"{w}_sapling") for w in WOODS]
    tag("block", "minecraft:dirt", grass_like)
    tag("block", "minecraft:sand", [B("cerulean_sand")])
    tag("block", "minecraft:mineable/shovel", grass_like + [B("cerulean_sand")])
    tag("block", "minecraft:mineable/pickaxe", stones + ores_all + [B("cerulean_cobblestone"), B("cerulean_stone_bricks"),
                                                                    B("polished_cerulean_stone")])
    tag("block", "minecraft:mineable/axe", logs + planks)
    tag("block", "minecraft:mineable/hoe", leaf_ids)
    tag("block", "minecraft:needs_stone_tool", [B(f"{d}cerulean_{o}_ore") for o in ("iron", "copper", "lapis", "zinc") for d in ("", "deep_")])
    tag("block", "minecraft:needs_iron_tool", [B(f"{d}cerulean_{o}_ore") for o in ("gold", "redstone", "diamond", "emerald", "crystal")
                                              for d in ("", "deep_")])
    tag("block", "minecraft:base_stone_overworld", stones)
    tag("block", "minecraft:overworld_carver_replaceables", stones + grass_like + [B("cerulean_sand")])
    tag("block", "minecraft:logs", logs)
    tag("block", "minecraft:logs_that_burn", logs)
    tag("block", "minecraft:planks", planks)
    tag("block", "minecraft:leaves", leaf_ids)
    tag("block", "minecraft:saplings", saplings)
    tag("block", "minecraft:climbable", [B("mire_vine")])
    for kind_ in ("block", "item"):
        tag(kind_, "minecraft:stairs", stair_ids)
        tag(kind_, "minecraft:slabs", slab_ids)
        tag(kind_, "minecraft:wooden_stairs", wood_stairs)
        tag(kind_, "minecraft:wooden_slabs", wood_slabs)
        tag(kind_, "minecraft:doors", door_ids)
        tag(kind_, "minecraft:wooden_doors", door_ids)
        tag(kind_, "minecraft:trapdoors", trap_ids)
        tag(kind_, "minecraft:wooden_trapdoors", trap_ids)
    tag("block", "minecraft:mineable/pickaxe", [x for x in stair_ids + slab_ids if x not in wood_stairs + wood_slabs])
    tag("block", "minecraft:mineable/axe", wood_stairs + wood_slabs + door_ids + trap_ids)
    tag("block", "minecraft:crops", [B("cerulean_roots")])
    tag("block", "minecraft:small_flowers", [B("azure_flower"), B("glowbloom")])
    tag("block", "minecraft:replaceable_by_trees", [B("cerulean_grass"), B("tall_cerulean_grass"), B("azure_flower"), B("glowbloom")])
    tag("item", "minecraft:logs", logs)
    tag("item", "minecraft:logs_that_burn", logs)
    tag("item", "minecraft:planks", planks)
    tag("item", "minecraft:leaves", leaf_ids)
    tag("item", "minecraft:saplings", saplings)
    tag("item", "minecraft:small_flowers", [B("azure_flower"), B("glowbloom")])
    tag("item", "minecraft:stone_crafting_materials", [B("cerulean_cobblestone")])
    tag("item", "minecraft:stone_tool_materials", [B("cerulean_cobblestone")])
    tag("item", "minecraft:villager_plantable_seeds", [B("cerulean_root")])
    for o in CERULEA_ORES:
        both = [B(f"cerulean_{o}_ore"), B(f"deep_cerulean_{o}_ore")]
        if o not in ("zinc", "crystal"):
            tag("block", f"minecraft:{o}_ores", both)
            tag("item", f"minecraft:{o}_ores", both)
        tag("block", f"c:ores/{o}", both)
        tag("item", f"c:ores/{o}", both)
        tag("block", "c:ores", both)
        tag("item", "c:ores", both)
    # zinc de Create: Create funde las menas con la etiqueta c:ores/zinc
    tag("item", "c:foods/berry", [B("blueberries")])
    tag("item", "c:foods/fruit", [B("duskfruit")])
    tag("item", "c:crops", [B("cerulean_root")])

    # --------------------------------------------------------------- biomas
    def bfeat(*ids):
        return [rl(f"{P}/{i}") for i in ids]

    def placed(name, feature, placement):
        data(f"worldgen/placed_feature/{P}/{name}", {"feature": feature, "placement": placement})

    def configured(name, obj):
        data(f"worldgen/configured_feature/{P}/{name}", obj)
        return rl(f"{P}/{name}")

    SURFACE = [{"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}]

    def st(n, props=None):
        s_ = {"Name": B(n)}
        if props:
            s_["Properties"] = props
        return s_

    def patch(name, state, tries, spread=7, count=None, chance=None, extra_pred=None, water=False):
        pred = [{"type": "minecraft:matching_blocks", "blocks": "minecraft:air"} if not water else
                {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                {"type": "minecraft:would_survive", "state": state}]
        if extra_pred:
            pred.append(extra_pred)
        cf = configured(name, {"type": "minecraft:random_patch", "config": {"tries": tries, "xz_spread": spread, "y_spread": 3, "feature": {
            "feature": {"type": "minecraft:simple_block", "config": {"to_place": {"type": "minecraft:simple_state_provider", "state": state}}},
            "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": pred}}]}}})
        pl = []
        if chance:
            pl.append({"type": "minecraft:rarity_filter", "chance": chance})
        if count:
            pl.append({"type": "minecraft:count", "count": count})
        placed(name, cf, pl + [{"type": "minecraft:in_square"}] + (
            [{"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}] if water else SURFACE))
        return rl(f"{P}/{name}")

    NEAR_WATER = {"type": "minecraft:any_of", "predicates": [
        {"type": "minecraft:matching_fluids", "offset": [dx, -1, dz], "fluids": ["minecraft:water", "minecraft:flowing_water"]}
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]}
    tall_grass = patch("tall_grass", st("tall_cerulean_grass", {"half": "lower"}), 96, count=6)
    tall_grass_few = patch("tall_grass_few", st("tall_cerulean_grass", {"half": "lower"}), 32, count=1)
    short_grass = patch("short_grass", st("cerulean_grass"), 64, count=6)
    short_grass_few = patch("short_grass_few", st("cerulean_grass"), 32, count=2)
    flowers = patch("flowers", st("azure_flower"), 48, count=2)
    glowblooms = patch("glowblooms", st("glowbloom"), 24, chance=3)
    glowcaps = patch("glowcaps", st("glowcap"), 32, count=2)
    berries = patch("blueberry_bushes", st("blueberry_bush", {"age": "3"}), 24, chance=6)
    wild_roots = patch("wild_roots", st("cerulean_roots", {"age": "3"}), 12, chance=10)
    reeds = patch("reeds", st("mire_reed", {"half": "lower"}), 48, count=3, extra_pred=NEAR_WATER)
    reeds_few = patch("reeds_few", st("mire_reed", {"half": "lower"}), 24, chance=2, extra_pred=NEAR_WATER)
    lilies = patch("lilies", st("azure_lily_pad"), 24, count=3, water=True)
    # 1.0.3: el arbusto es un bloque de hojas (sobrevive en el aire); sin esto el y_spread lo dejaba flotando
    ON_GROUND = {"type": "minecraft:matching_block_tag", "offset": [0, -1, 0], "tag": "minecraft:dirt"}
    bushes = patch("bushes", st("cerulean_bush", {"persistent": "true", "distance": "7", "waterlogged": "false"}), 8, chance=3,
                   extra_pred=ON_GROUND)

    # árboles
    def tree_cf(name, leaves_id):
        leaf = {"Name": B(leaves_id), "Properties": {"distance": "7", "persistent": "false", "waterlogged": "false"}}
        return configured(name, {"type": "minecraft:tree", "config": {
            "trunk_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": B("duskwood_log"), "Properties": {"axis": "y"}}},
            "trunk_placer": {"type": "minecraft:straight_trunk_placer", "base_height": 4, "height_rand_a": 2, "height_rand_b": 1},
            "foliage_provider": {"type": "minecraft:simple_state_provider", "state": leaf},
            "foliage_placer": {"type": "minecraft:blob_foliage_placer", "radius": 2, "offset": 0, "height": 3},
            "minimum_size": {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1},
            "dirt_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": B("cerulean_soil")}},
            "decorators": [{"type": "minecraft:attached_to_leaves", "probability": 0.08, "exclusion_radius_xz": 1,
                            "exclusion_radius_y": 0, "required_empty_blocks": 2,
                            "block_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": B("duskfruit")}},
                            "directions": ["down"]}],
            "ignore_vines": True, "force_dirt": False}})
    duskwood_green = tree_cf("duskwood_tree", "duskwood_leaves")
    duskwood_blue = tree_cf("duskwood_tree_blue", "blue_duskwood_leaves")
    silverwood = configured("silverwood_tree", {"type": B("cerulea_tree"), "config": {"variant": "silverwood"}})
    mirewood = configured("mirewood_tree", {"type": B("cerulea_tree"), "config": {"variant": "mirewood"}})

    def trees(name, choices, count, extra=0.1):
        cf = configured(name + "_choice", {"type": "minecraft:random_selector", "config": {
            "features": [{"feature": {"feature": c, "placement": []}, "chance": ch} for c, ch in choices[:-1]],
            "default": {"feature": choices[-1][0], "placement": []}}})
        placed(name, cf, [{"type": "minecraft:count", "count": {"type": "minecraft:weighted_list", "distribution": [
            {"data": count, "weight": 9}, {"data": count + 1, "weight": 1}]}},
            {"type": "minecraft:in_square"}, {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
            {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
            # 1.0.3: el heightmap OCEAN_FLOOR cuenta las hojas; sin este filtro salían árboles encima de otros árboles
            {"type": "minecraft:block_predicate_filter", "predicate": ON_GROUND}, {"type": "minecraft:biome"}])
        return rl(f"{P}/{name}")
    t_grove = trees("grove_trees", [(duskwood_green, 0.55), (duskwood_blue, 0.0)], 9)
    t_meadow = trees("meadow_trees", [(duskwood_green, 0.5), (duskwood_blue, 0.0)], 0)
    t_giant = trees("giant_trees", [(silverwood, 0.85), (duskwood_blue, 0.0)], 2)
    cf = configured("swamp_tree_choice", {"type": "minecraft:random_selector", "config": {"features": [],
                    "default": {"feature": mirewood, "placement": []}}})
    placed("swamp_trees", cf, [{"type": "minecraft:count", "count": 2}, {"type": "minecraft:in_square"},
                               {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 2},
                               {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
                               {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:any_of", "predicates": [
                                   ON_GROUND, {"type": "minecraft:matching_block_tag", "offset": [0, -1, 0], "tag": "minecraft:sand"}]}},
                               {"type": "minecraft:biome"}])
    t_swamp = rl(f"{P}/swamp_trees")

    # minerales
    def ore(name, ore_id, size, count, lo, hi, shape="minecraft:trapezoid", discard=0.0):
        cf_ = configured(f"ore_{name}", {"type": "minecraft:ore", "config": {"size": size, "discard_chance_on_air_exposure": discard,
            "targets": [{"target": {"predicate_type": "minecraft:block_match", "block": B("cerulean_stone")}, "state": st(f"cerulean_{ore_id}_ore")},
                        {"target": {"predicate_type": "minecraft:block_match", "block": B("deep_cerulean_stone")},
                         "state": st(f"deep_cerulean_{ore_id}_ore")}]}})
        placed(f"ore_{name}", cf_, [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
                                    {"type": "minecraft:height_range", "height": {"type": shape, "min_inclusive": {"absolute": lo},
                                                                                  "max_inclusive": {"absolute": hi}}},
                                    {"type": "minecraft:biome"}])
        return rl(f"{P}/ore_{name}")
    ore_list = [
        ore("coal", "coal", 17, 30, 0, 192, "minecraft:uniform"), ore("coal_lower", "coal", 17, 20, 0, 192, "minecraft:trapezoid", 0.5),
        ore("iron", "iron", 9, 10, -24, 56), ore("iron_upper", "iron", 9, 90, 80, 384), ore("iron_small", "iron", 4, 10, -64, 72, "minecraft:uniform"),
        ore("copper", "copper", 10, 16, -16, 112), ore("gold", "gold", 9, 4, -64, 32), ore("gold_lower", "gold", 9, 1, -64, -48, "minecraft:uniform", 0.5),
        ore("redstone", "redstone", 8, 4, -64, 15, "minecraft:uniform"), ore("redstone_lower", "redstone", 8, 8, -96, -32),
        ore("lapis", "lapis", 7, 2, -32, 32), ore("lapis_buried", "lapis", 7, 4, -64, 64, "minecraft:uniform", 1.0),
        ore("diamond", "diamond", 4, 7, -144, 16, "minecraft:trapezoid", 0.5), ore("diamond_large", "diamond", 12, 1, -144, 16, "minecraft:trapezoid", 0.7),
        ore("emerald", "emerald", 3, 6, -16, 320), ore("zinc", "zinc", 12, 8, -63, 70, "minecraft:uniform"),
        ore("crystal", "crystal", 5, 3, -64, 40, "minecraft:trapezoid", 0.3),
    ]

    sky = {"cerulea_tall_meadow": rgb(120, 178, 204), "cerulea_short_meadow": rgb(126, 184, 206), "cerulea_giant_forest": rgb(108, 166, 196),
           "cerulea_grove": rgb(114, 172, 200), "cerulea_swamp": rgb(112, 160, 176), "cerulea_river": rgb(120, 178, 204),
           "cerulea_shore": rgb(126, 184, 206), "cerulea_ocean": rgb(118, 176, 206)}
    water = {"cerulea_swamp": (rgb(44, 112, 104), rgb(16, 40, 40)), "cerulea_ocean": (rgb(28, 96, 150), rgb(8, 30, 56))}
    def sea(name, kind, count, tries, radius, chance=None):
        cf = configured(f"sea_{name}", {"type": B("cerulea_sea_plants"), "config": {"kind": kind, "tries": tries, "radius": radius}})
        mods = [{"type": "minecraft:count", "count": count}] if count > 1 else []
        if chance:
            mods.append({"type": "minecraft:rarity_filter", "chance": chance})
        placed(f"sea_{name}", cf, mods + [{"type": "minecraft:in_square"}, {"type": "minecraft:biome"}])
        return rl(f"{P}/sea_{name}")
    sea_grass = sea("seagrass", "seagrass", 5, 40, 7)
    sea_kelp = sea("kelp", "kelp", 4, 10, 6)
    sea_pearl = sea("pearl", "pearl", 1, 6, 4)
    sea_tubes = sea("tubes", "tubes", 2, 5, 4)
    sea_fan = sea("fan", "fan", 2, 4, 4)
    sea_grass_river = sea("seagrass_river", "seagrass", 2, 24, 5)
    sea_tubes_river = sea("tubes_river", "tubes", 1, 4, 3, chance=3)
    veg = {
        "cerulea_tall_meadow": [t_meadow, tall_grass, short_grass_few, flowers, glowblooms, berries, wild_roots, bushes],
        "cerulea_short_meadow": [t_meadow, short_grass, flowers, glowblooms, berries, wild_roots, bushes],
        "cerulea_giant_forest": [t_giant, short_grass, tall_grass_few, flowers, glowblooms],
        "cerulea_grove": [t_grove, short_grass, flowers, glowblooms, berries],
        "cerulea_swamp": [t_swamp, short_grass, glowcaps, reeds, lilies, glowblooms],
        "cerulea_river": [reeds_few, short_grass_few, sea_grass_river, sea_tubes_river],
        "cerulea_shore": [reeds_few],
        "cerulea_ocean": [sea_grass, sea_kelp, sea_pearl, sea_tubes, sea_fan],
    }
    for b in CERULEA_BIOMES:
        wc, wf = water.get(b, (rgb(36, 128, 160), rgb(10, 36, 52)))
        biome(b, temperature=0.7, downfall=0.6, precipitation=True, sky=sky[b], fog=rgb(240, 214, 168), water=wc, water_fog=wf,
              carvers=["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"],
              feats=features(underground_ores=ore_list, vegetal_decoration=veg[b]), spawn=cerulea_spawns(b))
    for b, en, es in (("cerulea_tall_meadow", "Tall Meadow", "Pradera de pasto alto"), ("cerulea_short_meadow", "Short Meadow", "Pradera de pasto bajo"),
                      ("cerulea_giant_forest", "Giant Forest", "Bosque gigante"), ("cerulea_grove", "Duskwood Grove", "Bosque violeta"),
                      ("cerulea_swamp", "Mire", "Pantano"), ("cerulea_river", "River", "Río"), ("cerulea_shore", "Shore", "Costa"),
                      ("cerulea_ocean", "Ocean", "Océano")):
        name(b, en, es)

    # reparto de biomas (como el de la Tierra: continentalidad para el mar, humedad para el tipo de tierra)
    FULLR = [-1.0, 1.0]
    land = [-0.11, 1.0]
    w_out = ([-1.0, -0.05], [0.05, 1.0])
    pts = [point("cerulea_ocean", continentalness=[-1.2, -0.19]),
           point("cerulea_shore", continentalness=[-0.19, -0.11]),
           point("cerulea_river", continentalness=[-0.11, 0.55], weirdness=[-0.05, 0.05], erosion=[-0.375, 1.0])]
    for w in w_out:
        pts += [point("cerulea_short_meadow", humidity=[-1.0, -0.35], continentalness=land, weirdness=w),
                point("cerulea_tall_meadow", humidity=[-0.35, 0.1], continentalness=land, weirdness=w),
                point("cerulea_grove", humidity=[0.1, 0.3], continentalness=land, weirdness=w),
                point("cerulea_swamp", humidity=[0.3, 1.0], erosion=[0.45, 1.0], continentalness=land, weirdness=w),
                point("cerulea_giant_forest", humidity=[0.3, 1.0], erosion=[-1.0, 0.45], temperature=[-1.0, 0.2], continentalness=land, weirdness=w),
                point("cerulea_grove", humidity=[0.3, 1.0], erosion=[-1.0, 0.45], temperature=[0.2, 1.0], continentalness=land, weirdness=w)]
    # los valles altos (continentalidad > 0,55) no son ríos: tierra normal
    pts += [point("cerulea_tall_meadow", continentalness=[0.55, 1.0], weirdness=[-0.05, 0.05], humidity=[-1.0, 0.1]),
            point("cerulea_grove", continentalness=[0.55, 1.0], weirdness=[-0.05, 0.05], humidity=[0.1, 1.0])]

    ABOVE_WATER = {"type": "minecraft:water", "offset": -1, "surface_depth_multiplier": 0, "add_stone_depth": False}
    wet = ["cerulea_ocean", "cerulea_shore", "cerulea_river"]
    surface = seq(
        BEDROCK_FLOOR,
        cond(ABOVE_PRELIM, seq(
            cond(biome_is(*wet), seq(cond(ON_FLOOR, put(st("cerulean_sand"))), cond(UNDER_FLOOR, put(st("cerulean_sand"))))),
            cond(biome_is("cerulea_swamp"), seq(
                cond(ON_FLOOR, seq(cond(ABOVE_WATER, put(st("cerulean_grass_block"))), put(st("cerulean_mud")))),
                cond(UNDER_FLOOR, put(st("cerulean_mud"))))),
            cond(biome_is("cerulea_short_meadow"), cond(ON_FLOOR, seq(cond(ABOVE_WATER, put(st("cerulean_meadow_grass_block"))),
                                                                      put(st("cerulean_soil"))))),
            cond(ON_FLOOR, seq(cond(ABOVE_WATER, put(st("cerulean_grass_block"))), put(st("cerulean_soil")))),
            cond(UNDER_FLOOR, put(st("cerulean_soil"))),
        )),
        cond({"type": "minecraft:vertical_gradient", "random_name": rl("deep_cerulean"),
              "true_at_and_below": {"absolute": 0}, "false_at_and_above": {"absolute": 8}}, put(st("deep_cerulean_stone"))),
    )
    router = RouterCloner(P).router_from("large_biomes")
    with open(os.path.join(VWG, "noise_settings", "overworld.json")) as f:
        vanilla = json.load(f)
    noise_settings(P, router, default_block=st("cerulean_stone"), default_fluid=vanilla["default_fluid"], sea_level=63,
                   aquifers=True, surface_rule=surface, spawn_target=vanilla["spawn_target"])
    dimension(P, pts)
    dimension_type(P, effects="minecraft:overworld")
    dimension_physics(P, universal_drag=0.01)

    data(f"universe_planets/{P}", {
        "name": P,
        "parent": "amber",
        "priority": 1000,
        "acceleration_at_surface": 9.8,
        "radius": 3000000.0,
        # año de 604 800 s, como la Tierra; con la masa de Ámbar queda a ~3,5e9 m de su estrella
        "position": {"type": "circular_orbit_period", "orbit_axis": {"x": 0.0, "y": 1.0, "z": 0.0}, "period_seconds": 604800.0},
        "rotation": {"type": "period", "period_seconds": 1200.0, "rotation_axis": {"x": 0.0, "y": -1.0, "z": 0.0}},
        "planet_extras": {"light_source_name": "amber", "has_clouds": True},
        "dimension_data": {
            "linked_dimension": rl(P), "allowed_transfer": "all", "dimension_transfer_height": 20000,
            "atmosphere_composition": {"5000": [], "21000": ["low_density", "drowning"]},
            "render_universe_in_dimension": True, "dimension_day_time_controller_name": "amber",
            "apply_gravity_correction_to_entities_in_dimension": True, "entity_drag_multiplier": drag_curve(4000, 7000),
        },
        "planet_atmosphere": {"enabled": True, "radius": 1.12, "intensity": 0.8, "densityFalloff": 5.0,
                              "scatteringStrength": 0.06, "wavelengths": [680.0, 540.0, 450.0]},
        "planet_texture": {"type": "biome_sampler", "fallback_red": 60, "fallback_green": 160, "fallback_blue": 130,
                           "colors": [color(28, 96, 150, biomes=["cerulea_ocean"], ocean=True), color(40, 130, 165, biomes=["cerulea_river"]),
                                      color(196, 204, 198, biomes=["cerulea_shore"]), color(77, 176, 138, biomes=["cerulea_tall_meadow"]),
                                      color(104, 196, 150, biomes=["cerulea_short_meadow"]), color(44, 88, 170, biomes=["cerulea_giant_forest"]),
                                      color(40, 140, 120, biomes=["cerulea_grove"]), color(52, 110, 100, biomes=["cerulea_swamp"])]},
    })
    LANG_EN[f"hologram.{NS}.body.{P}"] = "Cerulea"
    LANG_ES[f"hologram.{NS}.body.{P}"] = "Cerúlea"
    n = f"nav.{NS}."
    for field, (en, es) in {
            "type": ("Living planet", "Planeta con vida"),
            "desc": ("A world of turquoise meadows, giant blue forests and glowing swamps under the warm light of Amber.",
                     "Un mundo de praderas turquesa, bosques gigantes azules y pantanos que brillan, bajo la luz cálida de Ámbar."),
            "air": ("Breathable", "Respirable"), "temp": ("Mild", "Templada"), "hazards": ("None", "Ninguno"),
            "armor": ("Not needed", "No hace falta"),
            "resources": ("Every Minecraft ore, zinc, cerulean crystal, new woods and food",
                          "Todos los minerales de Minecraft, zinc, cristal cerúleo, maderas nuevas y comida")}.items():
        LANG_EN[f"{n}body.{P}.{field}"] = en
        LANG_ES[f"{n}body.{P}.{field}"] = es


# ==========================================================================
# VELO — luna de cristal de Cerúlea (1.0.3; el usuario la quiso "prácticamente vacía, solo polvo blanco y cristales
# celestes"). Texturas: tools/designs/velo_textures.py --export tools/designs/velo  →  velo/block
# ==========================================================================
VELO_TEX = os.path.join(DESIGNS, "velo")
VELO_BIOMES = ["velo_dust_plains", "velo_dunes", "velo_crystal_fields", "velo_craters"]


def build_velo():
    P = "velo"
    B = rl
    for f in sorted(os.listdir(os.path.join(VELO_TEX, "block"))):
        write_bytes(f"assets/{NS}/textures/block/{f}", open(os.path.join(VELO_TEX, "block", f), "rb").read())

    def lang(kind, n, en, es):
        LANG_EN[f"{kind}.{NS}.{n}"] = en
        LANG_ES[f"{kind}.{NS}.{n}"] = es

    def model(n, parent, textures, render=None):
        m = {"parent": parent, "textures": {k: (rl(f"block/{v}") if ":" not in v else v) for k, v in textures.items()}}
        if render:
            m["render_type"] = render
        write(f"assets/{NS}/models/block/{n}.json", m)

    def bstate(n, variants):
        write(f"assets/{NS}/blockstates/{n}.json", {"variants": variants})

    def item_block(n, m=None):
        write(f"assets/{NS}/models/item/{n}.json", {"parent": rl(f"block/{m or n}")})

    def loot(n, pools):
        data(f"loot_table/blocks/{n}", {"type": "minecraft:block", "pools": pools, "random_sequence": rl(f"blocks/{n}")})

    def pool(entry, conditions=None):
        p = {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry]}
        if conditions:
            p["conditions"] = conditions
        return p

    SURVIVES = [{"condition": "minecraft:survives_explosion"}]
    SILK = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}

    def drop_self(n):
        loot(n, [pool({"type": "minecraft:item", "name": B(n)}, SURVIVES)])

    def cube(n, en, es, tex=None):
        model(n, "minecraft:block/cube_all", {"all": tex or n})
        bstate(n, {"": {"model": rl(f"block/{n}")}})
        item_block(n)
        lang("block", n, en, es)

    def tag(kind, tagname, values):
        for v in values:
            tag_add(f"data/{tagname.split(':')[0]}/tags/{kind}/{tagname.split(':')[1]}.json", v)

    # --------------------------------------------------------------- bloques
    for n, en, es in (("velo_dust", "Velo Dust", "Polvo de Velo"), ("velo_rock", "Velo Rock", "Roca de Velo"),
                      ("velo_rock_bricks", "Velo Rock Bricks", "Ladrillos de roca de Velo"),
                      ("polished_velo_rock", "Polished Velo Rock", "Roca de Velo pulida"),
                      ("cerulean_crystal_block", "Block of Cerulean Crystal", "Bloque de cristal cerúleo"),
                      ("budding_cerulean_crystal", "Budding Cerulean Crystal", "Cristal cerúleo brotante")):
        cube(n, en, es)
    for n in ("velo_dust", "velo_rock", "velo_rock_bricks", "polished_velo_rock", "cerulean_crystal_block"):
        drop_self(n)
    loot("budding_cerulean_crystal", [])                                  # como la amatista brotante: no se recoge
    # brotes y racimo (modelos de cruz orientados como la amatista)
    FACING = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
              "east": {"x": 90, "y": 90}, "west": {"x": 90, "y": 270}}
    buds = [("small_cerulean_bud", "Small Cerulean Bud", "Brote de cristal cerúleo pequeño"),
            ("medium_cerulean_bud", "Medium Cerulean Bud", "Brote de cristal cerúleo mediano"),
            ("large_cerulean_bud", "Large Cerulean Bud", "Brote de cristal cerúleo grande"),
            ("cerulean_crystal_cluster", "Cerulean Crystal Cluster", "Racimo de cristal cerúleo")]
    for n, en, es in buds:
        model(n, "minecraft:block/cross", {"cross": n}, "minecraft:cutout")
        bstate(n, {f"facing={f},waterlogged={w}": {"model": rl(f"block/{n}"), **rot}
                   for f, rot in FACING.items() for w in ("false", "true")})
        write(f"assets/{NS}/models/item/{n}.json", {"parent": "minecraft:item/amethyst_cluster", "textures": {"layer0": rl(f"block/{n}")}})
        lang("block", n, en, es)
    for n, _, _ in buds[:3]:
        loot(n, [pool({"type": "minecraft:item", "name": B(n)}, [SILK])])
    crystal = B("cerulean_crystal")
    loot("cerulean_crystal_cluster", [pool({"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": B("cerulean_crystal_cluster"), "conditions": [SILK]},
        {"type": "minecraft:item", "name": crystal, "conditions": [{"condition": "minecraft:match_tool",
                                                                   "predicate": {"items": "#minecraft:cluster_max_harvestables"}}],
         "functions": [{"function": "minecraft:set_count", "count": 4.0, "add": False},
                       {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"}]},
        {"type": "minecraft:item", "name": crystal, "functions": [{"function": "minecraft:set_count", "count": 2.0, "add": False},
                                                                  {"function": "minecraft:explosion_decay"}]}]})])
    # lámpara (como la de redstone: dos modelos según LIT)
    model("cerulean_crystal_lamp", "minecraft:block/cube_all", {"all": "cerulean_crystal_lamp"})
    model("cerulean_crystal_lamp_on", "minecraft:block/cube_all", {"all": "cerulean_crystal_lamp_on"})
    bstate("cerulean_crystal_lamp", {"lit=false": {"model": rl("block/cerulean_crystal_lamp")},
                                     "lit=true": {"model": rl("block/cerulean_crystal_lamp_on")}})
    item_block("cerulean_crystal_lamp")
    lang("block", "cerulean_crystal_lamp", "Cerulean Crystal Lamp", "Lámpara de cristal cerúleo")
    drop_self("cerulean_crystal_lamp")
    # minerales
    VORES = {"iron": ("minecraft:raw_iron", None, "Iron", "hierro"), "gold": ("minecraft:raw_gold", None, "Gold", "oro"),
             "diamond": ("minecraft:diamond", None, "Diamond", "diamante"),
             "crystal": (crystal, (1, 2), "Cerulean Crystal", "cristal cerúleo"),
             "titanium": ("rocketnautics:raw_titanium", None, "Titanium", "titanio")}
    for o, (item, count, en, es) in VORES.items():
        n = f"velo_{o}_ore"
        cube(n, f"Velo {en} Ore", f"Mena de {es} en roca de Velo")
        fns = []
        if count:
            fns.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}})
        fns += [{"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]
        loot(n, [pool({"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": B(n), "conditions": [SILK]}, {"type": "minecraft:item", "name": item, "functions": fns}]})])
    # escaleras y losas
    FACING_Y = {"east": 0, "south": 90, "west": 180, "north": 270}
    shapes = {"straight": "", "inner_left": "_inner", "inner_right": "_inner", "outer_left": "_outer", "outer_right": "_outer"}
    stair_ids, slab_ids = [], []
    for prefix, base, en, es in (("velo_rock", "velo_rock", "Velo Rock", "de roca de Velo"),
                                 ("velo_rock_brick", "velo_rock_bricks", "Velo Rock Brick", "de ladrillos de roca de Velo"),
                                 ("polished_velo_rock", "polished_velo_rock", "Polished Velo Rock", "de roca de Velo pulida")):
        st_, sl_ = f"{prefix}_stairs", f"{prefix}_slab"
        tx = {"bottom": base, "top": base, "side": base}
        model(st_, "minecraft:block/stairs", tx)
        model(st_ + "_inner", "minecraft:block/inner_stairs", tx)
        model(st_ + "_outer", "minecraft:block/outer_stairs", tx)
        var = {}
        for facing, y0 in FACING_Y.items():
            for half in ("bottom", "top"):
                for shape, suffix in shapes.items():
                    y = y0
                    if shape in ("inner_left", "outer_left") and half == "bottom":
                        y += 270
                    if shape in ("inner_right", "outer_right") and half == "top":
                        y += 90
                    v = {"model": rl(f"block/{st_}{suffix}")}
                    if half == "top":
                        v["x"] = 180
                    if y % 360:
                        v["y"] = y % 360
                    if "x" in v or "y" in v:
                        v["uvlock"] = True
                    var[f"facing={facing},half={half},shape={shape}"] = v
        bstate(st_, var)
        item_block(st_)
        lang("block", st_, f"{en} Stairs", f"Escaleras {es}")
        drop_self(st_)
        model(sl_, "minecraft:block/slab", tx)
        model(sl_ + "_top", "minecraft:block/slab_top", tx)
        bstate(sl_, {"type=bottom": {"model": rl(f"block/{sl_}")}, "type=top": {"model": rl(f"block/{sl_}_top")},
                     "type=double": {"model": rl(f"block/{base}")}})
        item_block(sl_)
        lang("block", sl_, f"{en} Slab", f"Losa {es}")
        loot(sl_, [pool({"type": "minecraft:item", "name": B(sl_), "functions": [
            {"function": "minecraft:set_count", "count": 2.0, "add": False,
             "conditions": [{"condition": "minecraft:block_state_property", "block": B(sl_), "properties": {"type": "double"}}]},
            {"function": "minecraft:explosion_decay"}]})])
        data(f"recipe/velo/{st_}", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["#  ", "## ", "###"],
                                    "key": {"#": {"item": B(base)}}, "result": {"id": B(st_), "count": 4}})
        data(f"recipe/velo/{sl_}", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["###"],
                                    "key": {"#": {"item": B(base)}}, "result": {"id": B(sl_), "count": 6}})
        for out, cnt in ((st_, 1), (sl_, 2)):
            data(f"recipe/velo/{out}_from_{base}_stonecutting", {"type": "minecraft:stonecutting",
                 "ingredient": {"item": B(base)}, "result": {"id": B(out), "count": cnt}})
        stair_ids.append(B(st_))
        slab_ids.append(B(sl_))

    # --------------------------------------------------------------- recetas
    for out, ing in (("polished_velo_rock", "velo_rock"), ("velo_rock_bricks", "polished_velo_rock")):
        data(f"recipe/velo/{out}", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["##", "##"],
                                    "key": {"#": {"item": B(ing)}}, "result": {"id": B(out), "count": 4}})
        data(f"recipe/velo/{out}_stonecutting", {"type": "minecraft:stonecutting", "ingredient": {"item": B("velo_rock")},
                                                 "result": {"id": B(out), "count": 1}})
    data("recipe/velo/cerulean_crystal_block", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["##", "##"],
                                                "key": {"#": {"item": crystal}}, "result": {"id": B("cerulean_crystal_block"), "count": 1}})
    # idea del usuario: lámpara de cristal (roca pulida en las esquinas, 4 cristales y polvo de piedra luminosa)
    data("recipe/velo/cerulean_crystal_lamp", {"type": "minecraft:crafting_shaped", "category": "redstone",
                                               "pattern": ["PCP", "CGC", "PCP"],
                                               "key": {"P": {"item": B("polished_velo_rock")}, "C": {"item": crystal},
                                                       "G": {"item": "minecraft:glowstone_dust"}},
                                               "result": {"id": B("cerulean_crystal_lamp"), "count": 1}})
    for o, item in (("iron", "minecraft:iron_ingot"), ("gold", "minecraft:gold_ingot")):
        for k, t in (("smelting", 200), ("blasting", 100)):
            data(f"recipe/velo/{o}_ingot_from_velo_ore_{k}", {"type": f"minecraft:{k}", "category": "misc",
                 "ingredient": {"item": B(f"velo_{o}_ore")}, "result": {"id": item}, "experience": 0.7 if o == "iron" else 1.0,
                 "cookingtime": t})

    # --------------------------------------------------------------- etiquetas
    ores = [B(f"velo_{o}_ore") for o in VORES]
    rocks = [B("velo_rock"), B("velo_rock_bricks"), B("polished_velo_rock")]
    crystals = [B("cerulean_crystal_block"), B("budding_cerulean_crystal")] + [B(n) for n, _, _ in buds]
    tag("block", "minecraft:mineable/shovel", [B("velo_dust")])
    tag("block", "minecraft:sand", [B("velo_dust")])
    tag("block", "minecraft:mineable/pickaxe", rocks + ores + crystals + stair_ids + slab_ids + [B("cerulean_crystal_lamp")])
    tag("block", "minecraft:needs_stone_tool", [B("velo_iron_ore"), B("velo_titanium_ore")])
    tag("block", "minecraft:needs_iron_tool", [B("velo_gold_ore"), B("velo_diamond_ore"), B("velo_crystal_ore")])
    tag("block", "minecraft:base_stone_overworld", [B("velo_rock")])
    tag("block", "minecraft:overworld_carver_replaceables", [B("velo_rock"), B("velo_dust")])
    tag("block", "minecraft:crystal_sound_blocks", [B("cerulean_crystal_block"), B("budding_cerulean_crystal")])
    for kind_ in ("block", "item"):
        tag(kind_, "minecraft:stairs", stair_ids)
        tag(kind_, "minecraft:slabs", slab_ids)
        for o in ("iron", "gold", "diamond"):
            tag(kind_, f"minecraft:{o}_ores", [B(f"velo_{o}_ore")])
        for o in VORES:
            tag(kind_, f"c:ores/{o}", [B(f"velo_{o}_ore")])
            tag(kind_, "c:ores", [B(f"velo_{o}_ore")])
    tag("item", "minecraft:stone_crafting_materials", [B("velo_rock")])
    tag("item", "minecraft:stone_tool_materials", [B("velo_rock")])
    data("tags/block/velo_carvable", {"values": [B("velo_dust"), B("velo_rock")] + ores})

    # --------------------------------------------------------------- terreno
    # relieve propio y sencillo: altura base 72 con lomas suaves (dos ruidos 2D). Los cráteres los hace el carver de
    # Cosmonautics, como en Marte. Biomas: "regiones" (continentalidad) y "cristal" (rareza).
    def noise(n, first, amps):
        data(f"worldgen/noise/{P}/{n}", {"firstOctave": first, "amplitudes": amps})
        return rl(f"{P}/{n}")
    hills, detail = noise("hills", -8, [1.0, 1.0, 0.5]), noise("detail", -5, [1.0, 0.5])
    regions, crystal_n = noise("regions", -10, [1.0, 1.0]), noise("crystal", -9, [1.0, 0.5])
    n2 = lambda n: {"type": "minecraft:noise", "noise": n, "xz_scale": 1.0, "y_scale": 0.0}           # noqa: E731
    base_h = 72
    # densidad = (altura - y) / 16 + lomas: 10 bloques de lomas y 3 de detalle
    density = {"type": "minecraft:add",
               "argument1": {"type": "minecraft:y_clamped_gradient", "from_y": -64, "to_y": 320,
                             "from_value": (base_h + 64) / 16.0, "to_value": (base_h - 320) / 16.0},
               "argument2": {"type": "minecraft:add",
                             "argument1": {"type": "minecraft:mul", "argument1": n2(hills), "argument2": 10 / 16.0},
                             "argument2": {"type": "minecraft:mul", "argument1": n2(detail), "argument2": 3 / 16.0}}}
    zero = 0.0
    router = {"barrier": zero, "fluid_level_floodedness": zero, "fluid_level_spread": zero, "lava": zero,
              "temperature": zero, "vegetation": zero, "continents": n2(regions), "erosion": zero, "depth": zero,
              "ridges": zero, "initial_density_without_jaggedness": density,
              "final_density": {"type": "minecraft:interpolated", "argument": density},
              "vein_toggle": zero, "vein_ridged": zero, "vein_gap": zero}
    # 1.0.3: la "rareza" (weirdness) de los biomas sale del campo "ridges" del router (no hay campo "weirdness": se
    # ignoraba y los campos de cristal no salían nunca)
    router["ridges"] = n2(crystal_n)
    dust = st_ = {"Name": B("velo_dust")}
    surface = seq(BEDROCK_FLOOR, cond(ABOVE_PRELIM, seq(
        cond(ON_FLOOR, put(dust)),
        cond(UNDER_FLOOR, put(dust)),
        cond(floor(offset=3), put(dust)))))
    noise_settings(P, router, default_block={"Name": B("velo_rock")}, default_fluid={"Name": "minecraft:air"},
                   sea_level=-64, aquifers=False, surface_rule=surface, spawn_target=[
                       {"temperature": FULL, "humidity": FULL, "continentalness": WIDE, "erosion": FULL, "weirdness": FULL,
                        "depth": 0.0, "offset": 0.0}])

    def crater_carver(name, probability):
        data(f"worldgen/configured_carver/{P}/{name}", {"type": "rocketnautics:crater", "config": {
            "probability": probability, "y": {"below_top": 0}, "yScale": 1.0, "lava_level": {"above_bottom": 0},
            "replaceable": f"#{NS}:velo_carvable",
            "radius": {"type": "rocketnautics:config_biased_to_bottom", "bias": 5, "inner": 3.0, "min_inclusive": 4.0, "max_exclusive": 72.0},
            "heightOffsetFactor": {"type": "minecraft:uniform", "min_inclusive": 0.4, "max_exclusive": 0.6},
            "horizontalRadiusFactor": {"type": "minecraft:uniform", "min_inclusive": 1.0, "max_exclusive": 1.3}}})
        return rl(f"{P}/{name}")
    craters_few, craters_many = crater_carver("crater", 0.025), crater_carver("crater_field", 0.12)

    def configured(name, obj):
        data(f"worldgen/configured_feature/{P}/{name}", obj)
        return rl(f"{P}/{name}")

    def placed(name, feature, placement):
        data(f"worldgen/placed_feature/{P}/{name}", {"feature": feature, "placement": placement})
        return rl(f"{P}/{name}")

    def bud_state(n):
        return {"Name": B(n), "Properties": {"facing": "up", "waterlogged": "false"}}

    def geode(name, crack):
        with open(os.path.join(VWG, "configured_feature", "amethyst_geode.json")) as fh:
            g = json.load(fh)
        bl = g["config"]["blocks"]
        bl["inner_layer_provider"]["state"] = {"Name": B("cerulean_crystal_block")}
        bl["alternate_inner_layer_provider"]["state"] = {"Name": B("budding_cerulean_crystal")}
        bl["middle_layer_provider"]["state"] = {"Name": B("polished_velo_rock")}
        bl["outer_layer_provider"]["state"] = {"Name": B("velo_rock")}
        bl["inner_placements"] = [bud_state(n) for n, _, _ in buds]
        g["config"]["crack"]["generate_crack_chance"] = crack
        if crack >= 1.0:
            g["config"]["crack"]["base_crack_size"] = 3.0   # geodas rotas de los cráteres: grieta grande
        return configured(name, g)
    geode_deep = placed("geode", geode("geode", 0.95), [
        {"type": "minecraft:rarity_filter", "chance": 20}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"above_bottom": 6},
                                                      "max_inclusive": {"absolute": 40}}}, {"type": "minecraft:biome"}])
    # geoda rota medio enterrada en la superficie (cráteres)
    geode_surface = placed("geode_surface", geode("geode_broken", 1.0), [
        {"type": "minecraft:rarity_filter", "chance": 3}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
        {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": -4}, {"type": "minecraft:biome"}])
    spires = placed("crystal_spires", configured("crystal_spire", {"type": B("velo_crystal_spire"), "config": {}}), [
        {"type": "minecraft:count", "count": {"type": "minecraft:weighted_list", "distribution": [
            {"data": 1, "weight": 3}, {"data": 2, "weight": 2}, {"data": 3, "weight": 1}]}},
        {"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}])

    def bud_patch(name, n, tries, count=None, chance=None):
        cf = configured(name, {"type": "minecraft:random_patch", "config": {"tries": tries, "xz_spread": 7, "y_spread": 3, "feature": {
            "feature": {"type": "minecraft:simple_block", "config": {"to_place": {"type": "minecraft:simple_state_provider",
                                                                                  "state": bud_state(n)}}},
            "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
                {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                {"type": "minecraft:matching_blocks", "offset": [0, -1, 0], "blocks": [B("velo_dust"), B("velo_rock")]}]}}]}}})
        pl = ([{"type": "minecraft:rarity_filter", "chance": chance}] if chance else []) + \
             ([{"type": "minecraft:count", "count": count}] if count else [])
        return placed(name, cf, pl + [{"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
                                      {"type": "minecraft:biome"}])
    buds_field = [bud_patch("buds_small", "small_cerulean_bud", 24, count=2), bud_patch("buds_medium", "medium_cerulean_bud", 16, count=1),
                  bud_patch("buds_large", "large_cerulean_bud", 10, chance=2), bud_patch("clusters", "cerulean_crystal_cluster", 8, chance=2)]
    buds_rare = bud_patch("buds_rare", "small_cerulean_bud", 6, chance=6)

    def ore(name, block_id, size, count, lo, hi, shape="minecraft:trapezoid"):
        cf = configured(f"ore_{name}", {"type": "minecraft:ore", "config": {"size": size, "discard_chance_on_air_exposure": 0.0,
            "targets": [{"target": {"predicate_type": "minecraft:block_match", "block": B("velo_rock")}, "state": {"Name": B(block_id)}}]}})
        return placed(f"ore_{name}", cf, [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
                                          {"type": "minecraft:height_range", "height": {"type": shape, "min_inclusive": {"absolute": lo},
                                                                                        "max_inclusive": {"absolute": hi}}},
                                          {"type": "minecraft:biome"}])
    ores_f = [ore("iron", "velo_iron_ore", 9, 18, -64, 64), ore("gold", "velo_gold_ore", 9, 5, -64, 32),
              ore("diamond", "velo_diamond_ore", 6, 6, -64, 16), ore("crystal", "velo_crystal_ore", 5, 7, -40, 56),
              ore("titanium", "velo_titanium_ore", 8, 10, -64, 64, "minecraft:uniform")]

    sky, fog = 0, rgb(20, 22, 30)
    common = dict(temperature=0.0, downfall=0.0, precipitation=False, sky=sky, fog=fog, water=rgb(63, 118, 228),
                  water_fog=rgb(5, 5, 51))
    caves = ["minecraft:cave", "minecraft:cave_extra_underground"]
    biome("velo_dust_plains", carvers=[craters_few] + caves,
          feats=features(local_modifications=[geode_deep], underground_ores=ores_f, vegetal_decoration=[buds_rare]), **common)
    biome("velo_dunes", carvers=[craters_few] + caves,
          feats=features(local_modifications=[geode_deep], underground_ores=ores_f), **common)
    biome("velo_crystal_fields", carvers=[craters_few] + caves,
          feats=features(local_modifications=[geode_deep], underground_ores=ores_f, surface_structures=[spires],
                         vegetal_decoration=buds_field), **common)
    biome("velo_craters", carvers=[craters_many] + caves,
          feats=features(local_modifications=[geode_deep], underground_ores=ores_f, surface_structures=[geode_surface],
                         vegetal_decoration=[buds_rare]), **common)
    name("velo_dust_plains", "Dust Plains", "Llanuras de polvo")
    name("velo_dunes", "Dust Dunes", "Dunas de polvo")
    name("velo_crystal_fields", "Crystal Fields", "Campos de cristal")
    name("velo_craters", "Crater Fields", "Campos de cráteres")
    pts = [point("velo_dunes", continentalness=[-2.0, -0.25], weirdness=[-2.0, 0.45], temperature=[-1.0, 1.0]),
           point("velo_dust_plains", continentalness=[-0.25, 0.4], weirdness=[-2.0, 0.45], temperature=[-1.0, 1.0]),
           point("velo_craters", continentalness=[0.4, 2.0], weirdness=[-2.0, 0.45], temperature=[-1.0, 1.0]),
           point("velo_crystal_fields", continentalness=WIDE, weirdness=[0.45, 2.0], temperature=[-1.0, 1.0])]
    dimension(P, pts)
    dimension_type(P, effects="rocketnautics:moon")
    dimension_physics(P, universal_drag=0.0, base_gravity=[0.0, -1.6, 0.0], base_pressure=0.0, magnetic_north=[0.0, 0.0, 0.0])

    data(f"universe_planets/{P}", {
        "name": P,
        "parent": "cerulea",
        "priority": 1000,
        "acceleration_at_surface": 1.6,
        "radius": 700000.0,
        # como la Luna de la Tierra (8 h de órbita); Ferra irá más lejos y en otra dirección
        "position": {"type": "circular_orbit_period", "orbit_axis": {"x": 0.0, "y": 1.0, "z": 0.0},
                     "position_direction": {"x": 1.0, "y": 0.0, "z": 0.0}, "period_seconds": 28800.0},
        "rotation": {"type": "tidal_lock", "rotations_per_orbit": -1.0},
        "planet_extras": {"light_source_name": "amber"},
        "dimension_data": {
            "linked_dimension": rl(P), "allowed_transfer": "all", "dimension_transfer_height": 20000,
            "atmosphere_composition": {"21000": ["low_density", "drowning"]},
            "render_universe_in_dimension": True, "dimension_day_time_controller_name": "amber",
            "apply_gravity_correction_to_entities_in_dimension": True, "entity_drag_multiplier": drag_curve(4000, 7000),
        },
        "planet_texture": {"type": "biome_sampler", "fallback_red": 236, "fallback_green": 236, "fallback_blue": 244,
                           "colors": [color(240, 240, 248, biomes=["velo_dust_plains"]), color(226, 226, 238, biomes=["velo_dunes"]),
                                      color(120, 196, 245, biomes=["velo_crystal_fields"], priority=5),
                                      color(200, 202, 216, biomes=["velo_craters"])]},
    })
    LANG_EN[f"hologram.{NS}.body.{P}"] = "Velo"
    LANG_ES[f"hologram.{NS}.body.{P}"] = "Velo"
    n = f"nav.{NS}."
    for field, (en, es) in {
            "type": ("Moon of Cerulea", "Luna de Cerúlea"),
            "desc": ("A silent, almost empty moon: white dust to the horizon and fields of glowing cerulean crystal.",
                     "Una luna silenciosa y casi vacía: polvo blanco hasta el horizonte y campos de cristal cerúleo que brilla."),
            "air": ("None", "Ninguno"), "temp": ("Cold", "Fría"), "hazards": ("No air, low gravity", "Sin aire, gravedad baja"),
            "armor": ("Space suit", "Traje espacial"),
            "resources": ("Cerulean crystal, iron, gold, diamond and titanium", "Cristal cerúleo, hierro, oro, diamante y titanio")}.items():
        LANG_EN[f"{n}body.{P}.{field}"] = en
        LANG_ES[f"{n}body.{P}.{field}"] = es


# ==========================================================================
# FERRA — luna de óxido de Cerúlea (1.0.3). Ideas del usuario: tormentas como en Marte, una luna hostil y que
# "aparezca demasiado hierro, titanio, cobre, redstone y oro". Texturas: tools/designs/ferra_textures.py --export
# tools/designs/ferra  →  ferra/block
# ==========================================================================
FERRA_TEX = os.path.join(DESIGNS, "ferra")


def build_ferra():
    P = "ferra"
    B = rl
    for f in sorted(os.listdir(os.path.join(FERRA_TEX, "block"))):
        write_bytes(f"assets/{NS}/textures/block/{f}", open(os.path.join(FERRA_TEX, "block", f), "rb").read())

    def lang(kind, n, en, es):
        LANG_EN[f"{kind}.{NS}.{n}"] = en
        LANG_ES[f"{kind}.{NS}.{n}"] = es

    def model(n, parent, textures):
        write(f"assets/{NS}/models/block/{n}.json", {"parent": parent, "textures": {k: rl(f"block/{v}") for k, v in textures.items()}})

    def bstate(n, variants):
        write(f"assets/{NS}/blockstates/{n}.json", {"variants": variants})

    def item_block(n):
        write(f"assets/{NS}/models/item/{n}.json", {"parent": rl(f"block/{n}")})

    def loot(n, pools):
        data(f"loot_table/blocks/{n}", {"type": "minecraft:block", "pools": pools, "random_sequence": rl(f"blocks/{n}")})

    def pool(entry, conditions=None):
        p = {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry]}
        if conditions:
            p["conditions"] = conditions
        return p

    SURVIVES = [{"condition": "minecraft:survives_explosion"}]
    SILK = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}

    def cube(n, en, es):
        model(n, "minecraft:block/cube_all", {"all": n})
        bstate(n, {"": {"model": rl(f"block/{n}")}})
        item_block(n)
        lang("block", n, en, es)

    def tag(kind, tagname, values):
        for v in values:
            tag_add(f"data/{tagname.split(':')[0]}/tags/{kind}/{tagname.split(':')[1]}.json", v)

    # --------------------------------------------------------------- bloques
    for n, en, es in (("ferra_dust", "Rust Dust", "Polvo de óxido"), ("ferra_rock", "Ferra Rock", "Roca de Ferra"),
                      ("ferra_rock_bricks", "Ferra Rock Bricks", "Ladrillos de roca de Ferra"),
                      ("polished_ferra_rock", "Polished Ferra Rock", "Roca de Ferra pulida"),
                      ("ferra_ochre", "Ochre Rock", "Roca ocre"), ("ferra_verdigris", "Copper Patina", "Pátina de cobre")):
        cube(n, en, es)
        loot(n, [pool({"type": "minecraft:item", "name": B(n)}, SURVIVES)])
    cube("ferra_iron_vein", "Iron Vein", "Veta de hierro")
    loot("ferra_iron_vein", [pool({"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": B("ferra_iron_vein"), "conditions": [SILK]},
        {"type": "minecraft:item", "name": "minecraft:raw_iron", "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 3, "max": 6}},
            {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
            {"function": "minecraft:explosion_decay"}]}]})])
    FORES = {"iron": ("minecraft:raw_iron", None, "Iron", "hierro"), "copper": ("minecraft:raw_copper", (2, 5), "Copper", "cobre"),
             "gold": ("minecraft:raw_gold", None, "Gold", "oro"), "redstone": ("minecraft:redstone", (4, 5), "Redstone", "redstone"),
             "titanium": ("rocketnautics:raw_titanium", None, "Titanium", "titanio")}
    for o, (item, count, en, es) in FORES.items():
        n = f"ferra_{o}_ore"
        if o == "redstone":
            model(n, "minecraft:block/cube_all", {"all": n})
            write(f"assets/{NS}/models/block/{n}_on.json", {"parent": "minecraft:block/cube_all", "textures": {"all": rl(f"block/{n}")}})
            bstate(n, {"lit=false": {"model": rl(f"block/{n}")}, "lit=true": {"model": rl(f"block/{n}_on")}})
            item_block(n)
            lang("block", n, f"Ferra {en} Ore", f"Mena de {es} en roca de Ferra")
        else:
            cube(n, f"Ferra {en} Ore", f"Mena de {es} en roca de Ferra")
        fns = []
        if count:
            fns.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}})
        fns.append({"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                    "formula": "minecraft:uniform_bonus_count" if o == "redstone" else "minecraft:ore_drops",
                    **({"parameters": {"bonusMultiplier": 1}} if o == "redstone" else {})})
        fns.append({"function": "minecraft:explosion_decay"})
        loot(n, [pool({"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": B(n), "conditions": [SILK]}, {"type": "minecraft:item", "name": item, "functions": fns}]})])
    FACING_Y = {"east": 0, "south": 90, "west": 180, "north": 270}
    shapes = {"straight": "", "inner_left": "_inner", "inner_right": "_inner", "outer_left": "_outer", "outer_right": "_outer"}
    stair_ids, slab_ids = [], []
    for prefix, base, en, es in (("ferra_rock", "ferra_rock", "Ferra Rock", "de roca de Ferra"),
                                 ("ferra_rock_brick", "ferra_rock_bricks", "Ferra Rock Brick", "de ladrillos de roca de Ferra"),
                                 ("polished_ferra_rock", "polished_ferra_rock", "Polished Ferra Rock", "de roca de Ferra pulida")):
        st_, sl_ = f"{prefix}_stairs", f"{prefix}_slab"
        tx = {"bottom": base, "top": base, "side": base}
        model(st_, "minecraft:block/stairs", tx)
        model(st_ + "_inner", "minecraft:block/inner_stairs", tx)
        model(st_ + "_outer", "minecraft:block/outer_stairs", tx)
        var = {}
        for facing, y0 in FACING_Y.items():
            for half in ("bottom", "top"):
                for shape, suffix in shapes.items():
                    y = y0
                    if shape in ("inner_left", "outer_left") and half == "bottom":
                        y += 270
                    if shape in ("inner_right", "outer_right") and half == "top":
                        y += 90
                    v = {"model": rl(f"block/{st_}{suffix}")}
                    if half == "top":
                        v["x"] = 180
                    if y % 360:
                        v["y"] = y % 360
                    if "x" in v or "y" in v:
                        v["uvlock"] = True
                    var[f"facing={facing},half={half},shape={shape}"] = v
        bstate(st_, var)
        item_block(st_)
        lang("block", st_, f"{en} Stairs", f"Escaleras {es}")
        loot(st_, [pool({"type": "minecraft:item", "name": B(st_)}, SURVIVES)])
        model(sl_, "minecraft:block/slab", tx)
        model(sl_ + "_top", "minecraft:block/slab_top", tx)
        bstate(sl_, {"type=bottom": {"model": rl(f"block/{sl_}")}, "type=top": {"model": rl(f"block/{sl_}_top")},
                     "type=double": {"model": rl(f"block/{base}")}})
        item_block(sl_)
        lang("block", sl_, f"{en} Slab", f"Losa {es}")
        loot(sl_, [pool({"type": "minecraft:item", "name": B(sl_), "functions": [
            {"function": "minecraft:set_count", "count": 2.0, "add": False,
             "conditions": [{"condition": "minecraft:block_state_property", "block": B(sl_), "properties": {"type": "double"}}]},
            {"function": "minecraft:explosion_decay"}]})])
        data(f"recipe/ferra/{st_}", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["#  ", "## ", "###"],
                                     "key": {"#": {"item": B(base)}}, "result": {"id": B(st_), "count": 4}})
        data(f"recipe/ferra/{sl_}", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["###"],
                                     "key": {"#": {"item": B(base)}}, "result": {"id": B(sl_), "count": 6}})
        for out, cnt in ((st_, 1), (sl_, 2)):
            data(f"recipe/ferra/{out}_from_{base}_stonecutting", {"type": "minecraft:stonecutting",
                 "ingredient": {"item": B(base)}, "result": {"id": B(out), "count": cnt}})
        stair_ids.append(B(st_))
        slab_ids.append(B(sl_))
    for out, ing in (("polished_ferra_rock", "ferra_rock"), ("ferra_rock_bricks", "polished_ferra_rock")):
        data(f"recipe/ferra/{out}", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["##", "##"],
                                     "key": {"#": {"item": B(ing)}}, "result": {"id": B(out), "count": 4}})
        data(f"recipe/ferra/{out}_stonecutting", {"type": "minecraft:stonecutting", "ingredient": {"item": B("ferra_rock")},
                                                  "result": {"id": B(out), "count": 1}})
    for o, item, xp in (("iron", "minecraft:iron_ingot", 0.7), ("copper", "minecraft:copper_ingot", 0.7),
                        ("gold", "minecraft:gold_ingot", 1.0), ("redstone", "minecraft:redstone", 0.7)):
        for k, t in (("smelting", 200), ("blasting", 100)):
            data(f"recipe/ferra/{o}_from_ferra_ore_{k}", {"type": f"minecraft:{k}", "category": "misc",
                 "ingredient": {"item": B(f"ferra_{o}_ore")}, "result": {"id": item}, "experience": xp, "cookingtime": t})

    ores = [B(f"ferra_{o}_ore") for o in FORES] + [B("ferra_iron_vein")]
    rocks = [B("ferra_rock"), B("ferra_rock_bricks"), B("polished_ferra_rock"), B("ferra_ochre"), B("ferra_verdigris")]
    tag("block", "minecraft:mineable/shovel", [B("ferra_dust")])
    tag("block", "minecraft:sand", [B("ferra_dust")])
    tag("block", "minecraft:mineable/pickaxe", rocks + ores + stair_ids + slab_ids)
    tag("block", "minecraft:needs_stone_tool", [B("ferra_iron_ore"), B("ferra_copper_ore"), B("ferra_titanium_ore"), B("ferra_iron_vein")])
    tag("block", "minecraft:needs_iron_tool", [B("ferra_gold_ore"), B("ferra_redstone_ore")])
    tag("block", "minecraft:base_stone_overworld", [B("ferra_rock")])
    tag("block", "minecraft:overworld_carver_replaceables", [B("ferra_rock"), B("ferra_dust"), B("ferra_ochre")])
    for kind_ in ("block", "item"):
        tag(kind_, "minecraft:stairs", stair_ids)
        tag(kind_, "minecraft:slabs", slab_ids)
        for o in ("iron", "copper", "gold", "redstone"):
            tag(kind_, f"minecraft:{o}_ores", [B(f"ferra_{o}_ore")])
        for o in FORES:
            tag(kind_, f"c:ores/{o}", [B(f"ferra_{o}_ore")])
            tag(kind_, "c:ores", [B(f"ferra_{o}_ore")])
    tag("item", "minecraft:stone_crafting_materials", [B("ferra_rock")])
    tag("item", "minecraft:stone_tool_materials", [B("ferra_rock")])
    data("tags/block/ferra_carvable", {"values": [B("ferra_dust"), B("ferra_rock"), B("ferra_ochre")] + ores})

    # --------------------------------------------------------------- terreno
    # meseta a 76 con lomas, cortada por cañones de ~30 bloques donde el ruido "canyon" pasa cerca de 0 (el mismo
    # ruido decide el bioma de cañón: rareza ±0,17). Charcas en las zonas bajas ("regions" < -0,3).
    def noise(n, first, amps):
        data(f"worldgen/noise/{P}/{n}", {"firstOctave": first, "amplitudes": amps})
        return rl(f"{P}/{n}")
    hills, detail = noise("hills", -8, [1.0, 1.0, 0.5]), noise("detail", -5, [1.0, 0.5])
    # 1.0.3 (el usuario solo encontraba cañones): las regiones van a escala de ~512 bloques (antes ~1024) y los cañones son
    # franjas estrechas (|ruido| < 0,035 del todo, hasta 0,06 la pared); antes |ruido| < 0,17 era casi la mitad del mapa
    regions, canyon = noise("regions", -9, [1.0, 1.0]), noise("canyon", -9, [1.0, 0.6])
    n2 = lambda n: {"type": "minecraft:noise", "noise": n, "xz_scale": 1.0, "y_scale": 0.0}           # noqa: E731
    base_h = 76
    cut = {"type": "minecraft:clamp", "min": 0.0, "max": 1.0, "input": {"type": "minecraft:add", "argument1": 2.4, "argument2": {
        "type": "minecraft:mul", "argument1": {"type": "minecraft:abs", "argument": n2(canyon)}, "argument2": -40.0}}}
    density = {"type": "minecraft:add",
               "argument1": {"type": "minecraft:y_clamped_gradient", "from_y": -64, "to_y": 320,
                             "from_value": (base_h + 64) / 16.0, "to_value": (base_h - 320) / 16.0},
               "argument2": {"type": "minecraft:add",
                             "argument1": {"type": "minecraft:add",
                                           "argument1": {"type": "minecraft:mul", "argument1": n2(hills), "argument2": 8 / 16.0},
                                           "argument2": {"type": "minecraft:mul", "argument1": n2(detail), "argument2": 3 / 16.0}},
                             "argument2": {"type": "minecraft:mul", "argument1": cut, "argument2": -30 / 16.0}}}
    # 1.0.3: cuevas de ruido como las de la Tierra (el usuario: "se cortan las cuevas y casi ninguna sale a la superficie";
    # antes solo había carvers, que se cortan en el borde de su alcance): entradas que salen a la superficie, túneles
    # (espagueti y fideos) y cavernas (queso). Mismo esquema que el final_density de la Tierra, sin pilares.
    cl = RouterCloner(P)
    entr = cl.df("minecraft:overworld/caves/entrances")
    spag = {"type": "minecraft:add", "argument1": cl.df("minecraft:overworld/caves/spaghetti_2d"),
            "argument2": cl.df("minecraft:overworld/caves/spaghetti_roughness_function")}
    noodle = cl.df("minecraft:overworld/caves/noodle")
    cheese = {"type": "minecraft:add",
              "argument1": {"type": "minecraft:mul", "argument1": 4.0, "argument2": {"type": "minecraft:square", "argument": {
                  "type": "minecraft:noise", "noise": cl.noise("minecraft:cave_layer"), "xz_scale": 1.0, "y_scale": 8.0}}},
              "argument2": {"type": "minecraft:add",
                            "argument1": {"type": "minecraft:clamp", "min": -1.0, "max": 1.0, "input": {
                                "type": "minecraft:add", "argument1": 0.27, "argument2": {
                                    "type": "minecraft:noise", "noise": cl.noise("minecraft:cave_cheese"), "xz_scale": 1.0, "y_scale": 0.6666666666666666}}},
                            "argument2": {"type": "minecraft:clamp", "min": 0.0, "max": 0.5, "input": {
                                "type": "minecraft:add", "argument1": 1.5, "argument2": {"type": "minecraft:mul", "argument1": -0.64, "argument2": density}}}}}
    deep = {"type": "minecraft:min", "argument1": {"type": "minecraft:min", "argument1": cheese, "argument2": entr}, "argument2": spag}
    caved = {"type": "minecraft:range_choice", "input": density, "min_inclusive": -1000000.0, "max_exclusive": 1.5625,
             "when_in_range": {"type": "minecraft:min", "argument1": density, "argument2": {"type": "minecraft:mul", "argument1": 5.0, "argument2": entr}},
             "when_out_of_range": deep}
    final = {"type": "minecraft:min", "argument1": {"type": "minecraft:interpolated", "argument": caved}, "argument2": noodle}
    zero = 0.0
    router = {"barrier": zero, "fluid_level_floodedness": zero, "fluid_level_spread": zero, "lava": zero,
              "temperature": zero, "vegetation": zero, "continents": n2(regions), "erosion": zero, "depth": zero,
              # la rareza de los biomas sale de "ridges" (antes se puso en un campo "weirdness" que no existe y se ignoraba:
              # rareza siempre 0 = todo Ferra era bioma de cañón)
              "ridges": n2(canyon), "initial_density_without_jaggedness": density,
              "final_density": final,
              "vein_toggle": zero, "vein_ridged": zero, "vein_gap": zero}
    dust, ochre = {"Name": B("ferra_dust")}, {"Name": B("ferra_ochre")}
    surface = seq(BEDROCK_FLOOR, cond(ABOVE_PRELIM, seq(
        cond(STEEP, put(ochre)),
        cond(biome_is("ferra_copper_pools"), cond(ON_FLOOR, cond(noise_band("minecraft:surface", 0.25, 2.0),
                                                                  put({"Name": B("ferra_verdigris")})))),
        cond(ON_FLOOR, put(dust)),
        cond(UNDER_FLOOR, put(dust)))),
        cond(biome_is("ferra_canyons"), cond(floor(offset=34), put(ochre))))
    noise_settings(P, router, default_block={"Name": B("ferra_rock")}, default_fluid={"Name": "minecraft:air"},
                   sea_level=-64, aquifers=False, surface_rule=surface, spawn_target=[
                       {"temperature": FULL, "humidity": FULL, "continentalness": WIDE, "erosion": FULL, "weirdness": FULL,
                        "depth": 0.0, "offset": 0.0}])
    data(f"worldgen/configured_carver/{P}/crater", {"type": "rocketnautics:crater", "config": {
        "probability": 0.015, "y": {"below_top": 0}, "yScale": 1.0, "lava_level": {"above_bottom": 0},
        "replaceable": f"#{NS}:ferra_carvable",
        "radius": {"type": "rocketnautics:config_biased_to_bottom", "bias": 5, "inner": 3.0, "min_inclusive": 4.0, "max_exclusive": 48.0},
        "heightOffsetFactor": {"type": "minecraft:uniform", "min_inclusive": 0.4, "max_exclusive": 0.6},
        "horizontalRadiusFactor": {"type": "minecraft:uniform", "min_inclusive": 1.0, "max_exclusive": 1.3}}})
    carvers = [rl(f"{P}/crater"), "minecraft:cave", "minecraft:cave_extra_underground"]

    def configured(name, obj):
        data(f"worldgen/configured_feature/{P}/{name}", obj)
        return rl(f"{P}/{name}")

    def placed(name, feature, placement):
        data(f"worldgen/placed_feature/{P}/{name}", {"feature": feature, "placement": placement})
        return rl(f"{P}/{name}")

    SURF = [{"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]
    pillars = placed("rust_pillars", configured("rust_pillar", {"type": B("ferra_rust_pillar"), "config": {}}),
                     [{"type": "minecraft:count", "count": {"type": "minecraft:weighted_list", "distribution": [
                         {"data": 1, "weight": 3}, {"data": 2, "weight": 2}, {"data": 3, "weight": 1}]}}] + SURF)
    pillars_few = placed("rust_pillars_few", rl(f"{P}/rust_pillar"), [{"type": "minecraft:rarity_filter", "chance": 6}] + SURF)
    pools = placed("copper_pools", configured("copper_pool", {"type": "minecraft:lake", "config": {
        "fluid": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:water", "Properties": {"level": "0"}}},
        "barrier": {"type": "minecraft:simple_state_provider", "state": {"Name": B("ferra_verdigris")}}}}),
        # el lago ocupa 16x16 bloques desde su origen y mira el bioma de cada columna (para helarse);
        # si el origen cae al fondo del chunk se sale de la zona que se puede leer al generar
        # ("Requested chunk unavailable during world generation"). Lo centramos: origen 8 bloques atrás.
        [{"type": "minecraft:rarity_filter", "chance": 2}, {"type": "minecraft:in_square"},
         {"type": "minecraft:random_offset", "xz_spread": -8, "y_spread": 0}] + SURF[1:])

    def ore(name, block_id, size, count, lo, hi, shape="minecraft:uniform"):
        cf = configured(f"ore_{name}", {"type": "minecraft:ore", "config": {"size": size, "discard_chance_on_air_exposure": 0.0,
            "targets": [{"target": {"predicate_type": "minecraft:tag_match", "tag": f"{NS}:ferra_ore_replaceable"},
                         "state": {"Name": B(block_id)}}]}})
        return placed(f"ore_{name}", cf, [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
                                          {"type": "minecraft:height_range", "height": {"type": shape, "min_inclusive": {"absolute": lo},
                                                                                        "max_inclusive": {"absolute": hi}}},
                                          {"type": "minecraft:biome"}])
    data("tags/block/ferra_ore_replaceable", {"values": [B("ferra_rock"), B("ferra_ochre")]})
    # el usuario: "que aparezca demasiado hierro, titanio, cobre, redstone y oro" (varias veces lo de la Tierra)
    ores_f = [ore("iron", "ferra_iron_ore", 14, 40, -64, 140), ore("iron_vein", "ferra_iron_vein", 22, 5, -64, 100),
              ore("titanium", "ferra_titanium_ore", 10, 22, -64, 110), ore("copper", "ferra_copper_ore", 16, 28, -64, 140),
              ore("redstone", "ferra_redstone_ore", 10, 18, -64, 60, "minecraft:trapezoid"),
              ore("gold", "ferra_gold_ore", 10, 14, -64, 80, "minecraft:trapezoid")]

    sky, fog = rgb(112, 58, 42), rgb(150, 78, 50)
    common = dict(temperature=0.8, downfall=0.0, precipitation=False, sky=sky, fog=fog, water=rgb(84, 186, 156),
                  water_fog=rgb(30, 90, 74), carvers=carvers)
    rust = {"options": {"type": "minecraft:ash"}, "probability": 0.004}
    # criaturas (el usuario): el gusano en las zonas despejadas, los rastreadores en las chimeneas y los cañones
    worms = spawners(monster=[(rl("rust_worm"), 10, 1, 1)])
    packs = spawners(monster=[(rl("blind_stalker"), 10, 2, 4)])
    biome("ferra_rust_plains", feats=features(underground_ores=ores_f, surface_structures=[pillars_few]), particle=rust, spawn=worms, **common)
    biome("ferra_canyons", feats=features(underground_ores=ores_f), spawn=packs, **common)
    biome("ferra_copper_pools", feats=features(lakes=[pools], underground_ores=ores_f), spawn=worms, **common)
    biome("ferra_iron_chimneys", feats=features(underground_ores=ores_f, surface_structures=[pillars]), particle=rust, spawn=packs, **common)
    name("ferra_rust_plains", "Rust Plains", "Llanuras de óxido")
    name("ferra_canyons", "Rust Canyons", "Cañones de óxido")
    name("ferra_copper_pools", "Copper Pools", "Charcas de cobre")
    name("ferra_iron_chimneys", "Iron Chimneys", "Chimeneas de hierro")
    out_c = [-2.0, -0.06], [0.06, 2.0]
    pts = [point("ferra_canyons", continentalness=WIDE, weirdness=[-0.06, 0.06], temperature=[-1.0, 1.0])]
    for w in out_c:
        pts += [point("ferra_copper_pools", continentalness=[-2.0, -0.22], weirdness=w, temperature=[-1.0, 1.0]),
                point("ferra_rust_plains", continentalness=[-0.22, 0.22], weirdness=w, temperature=[-1.0, 1.0]),
                point("ferra_iron_chimneys", continentalness=[0.22, 2.0], weirdness=w, temperature=[-1.0, 1.0])]
    dimension(P, pts)
    dimension_type(P, effects="minecraft:overworld")
    dimension_physics(P, universal_drag=0.002, base_gravity=[0.0, -2.4, 0.0],
                      pressure_function=[{"altitude": 64.0, "value": 0.1, "slope": 0.0},
                                         {"altitude": 1500.0, "value": 0.0, "slope": 0.0}])

    data(f"universe_planets/{P}", {
        "name": P,
        "parent": "cerulea",
        "priority": 1000,
        "acceleration_at_surface": 2.4,
        "radius": 900000.0,
        # más lejos que Velo (16,7 h) y empezando en el lado contrario
        "position": {"type": "circular_orbit_period", "orbit_axis": {"x": 0.0, "y": 1.0, "z": 0.0},
                     "position_direction": {"x": -1.0, "y": 0.0, "z": 0.0}, "period_seconds": 60000.0},
        "rotation": {"type": "tidal_lock", "rotations_per_orbit": -1.0},
        "planet_extras": {"light_source_name": "amber"},
        "dimension_data": {
            "linked_dimension": rl(P), "allowed_transfer": "all", "dimension_transfer_height": 20000,
            "atmosphere_composition": {"21000": ["low_density", "drowning"]},
            "render_universe_in_dimension": True, "dimension_day_time_controller_name": "amber",
            "apply_gravity_correction_to_entities_in_dimension": True, "entity_drag_multiplier": drag_curve(1500, 4000),
        },
        "planet_atmosphere": {"enabled": True, "radius": 1.08, "intensity": 0.45, "densityFalloff": 7.0,
                              "scatteringStrength": 0.05, "wavelengths": [450.0, 600.0, 700.0]},
        "planet_texture": {"type": "biome_sampler", "fallback_red": 180, "fallback_green": 90, "fallback_blue": 48,
                           "colors": [color(196, 98, 52, biomes=["ferra_rust_plains"]), color(150, 70, 40, biomes=["ferra_canyons"], priority=5),
                                      color(84, 170, 140, biomes=["ferra_copper_pools"], ocean=True),
                                      color(120, 66, 46, biomes=["ferra_iron_chimneys"])]},
    })
    # modelos y texturas de las criaturas (tools/designs/ferra_mobs.py tools/designs/mobs)
    mobs = os.path.join(DESIGNS, "mobs")
    for m, en, es in (("rust_worm", "Rust Worm", "Gusano de óxido"), ("blind_stalker", "Blind Stalker", "Rastreador ciego")):
        write_bytes(f"assets/{NS}/mob_models/{m}.json", open(os.path.join(mobs, f"{m}.json"), "rb").read())
        write_bytes(f"assets/{NS}/textures/entity/{m}.png", open(os.path.join(mobs, f"{m}.png"), "rb").read())
        write_bytes(f"assets/{NS}/textures/entity/{m}_glow.png", open(os.path.join(mobs, f"{m}_glow.png"), "rb").read())
        LANG_EN[f"entity.{NS}.{m}"] = en
        LANG_ES[f"entity.{NS}.{m}"] = es
        write(f"assets/{NS}/models/item/{m}_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})
        LANG_EN[f"item.{NS}.{m}_spawn_egg"] = f"{en} Spawn Egg"
        LANG_ES[f"item.{NS}.{m}_spawn_egg"] = f"Huevo generador de {es.lower()}"

    def drop(item, lo, hi, chance=None):
        fns = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}},
               {"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                "count": {"type": "minecraft:uniform", "min": 0, "max": 1}}]
        p = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "functions": fns}]}
        if chance is not None:
            p["conditions"] = [{"condition": "minecraft:killed_by_player"}, {"condition": "minecraft:random_chance", "chance": chance}]
        return p
    data("loot_table/entities/rust_worm", {"type": "minecraft:entity", "pools": [
        drop("minecraft:raw_iron", 2, 4), drop("rocketnautics:raw_titanium", 0, 2), drop("minecraft:raw_copper", 0, 2)]})
    data("loot_table/entities/blind_stalker", {"type": "minecraft:entity", "pools": [
        drop("minecraft:bone", 0, 2), drop("minecraft:echo_shard", 1, 1, chance=0.05)]})

    LANG_EN[f"hologram.{NS}.body.{P}"] = "Ferra"
    LANG_ES[f"hologram.{NS}.body.{P}"] = "Ferra"
    LANG_EN[f"storm.{NS}.ferra_dust_storm"] = "Rust storm"
    LANG_ES[f"storm.{NS}.ferra_dust_storm"] = "Tormenta de óxido"
    n = f"nav.{NS}."
    for field, (en, es) in {
            "type": ("Moon of Cerulea", "Luna de Cerúlea"),
            "desc": ("A hostile rust moon of canyons, iron chimneys and green copper pools, swept by frequent rust storms.",
                     "Una luna hostil de óxido: cañones, chimeneas de hierro y charcas verdes de cobre, azotada por tormentas de óxido."),
            "air": ("Thin, not breathable", "Fina, no respirable"), "temp": ("Cold", "Fría"),
            "hazards": ("Frequent rust storms", "Tormentas de óxido frecuentes"),
            "armor": ("Impact (against the storms)", "De impacto (contra las tormentas)"),
            "resources": ("Huge amounts of iron, titanium, copper, redstone and gold",
                          "Muchísimo hierro, titanio, cobre, redstone y oro")}.items():
        LANG_EN[f"{n}body.{P}.{field}"] = en
        LANG_ES[f"{n}body.{P}.{field}"] = es

# ==========================================================================
# 1.0.4: SATURNO (gigante gaseoso con anillos) y TITÁN (su luna de combustibles y lagos de metano)
# ==========================================================================
SATURN_TEX = os.path.join(DESIGNS, "saturn")
TITAN_TEX = os.path.join(DESIGNS, "titan")


def build_saturn():
    write_bytes(f"assets/{NS}/textures/planet/saturn.png", open(os.path.join(SATURN_TEX, "saturn.png"), "rb").read())
    write_bytes(f"assets/{NS}/textures/planet/saturn_rings.png", open(os.path.join(SATURN_TEX, "saturn_rings.png"), "rb").read())
    # entre el gigante gaseoso (período 28 días) y el mundo helado (56 días); los anillos los pinta SaturnRingsMixin
    data("universe_planets/saturn", {
        "name": "saturn",
        "parent": "sol",
        "priority": 1000,
        "acceleration_at_surface": 10.4,
        "radius": 1.06e7,
        "position": {"type": "circular_orbit_period", "orbit_axis": {"x": 0.0, "y": 1.0, "z": 0.0},
                     "position_direction": {"x": -0.6, "y": 0.0, "z": -0.8}, "period_seconds": 3.6e6},
        "rotation": {"type": "period", "period_seconds": 540.0, "rotation_axis": {"x": 0.0, "y": -1.0, "z": 0.0}},
        "planet_extras": {"light_source_name": "sol"},
        "planet_texture": {"type": "resloc", "texture": f"{NS}:textures/planet/saturn.png"},
    })
    LANG_EN[f"hologram.{NS}.body.saturn"] = "Saturn"
    LANG_ES[f"hologram.{NS}.body.saturn"] = "Saturno"
    n = f"nav.{NS}.body.saturn."
    for field, (en, es) in {
            "type": ("Ringed gas giant", "Gigante gaseoso con anillos"),
            "desc": ("A pale golden giant wrapped in a wide ring system. It has no surface to land on, but its moon Titan does.",
                     "Un gigante dorado pálido rodeado de anillos anchos. No tiene superficie donde aterrizar, pero su luna Titán sí."),
            "hazards": ("No surface", "Sin superficie")}.items():
        LANG_EN[n + field] = en
        LANG_ES[n + field] = es


AURELIA_TEX = os.path.join(DESIGNS, "aurelia")


def build_aurelia():
    """Aurelia (1.0.4): gigante gaseoso de la estrella Ámbar, sin superficie. Su luna Fulgor (build_fulgor) es aterrizable."""
    write_bytes(f"assets/{NS}/textures/planet/aurelia.png", open(os.path.join(AURELIA_TEX, "aurelia.png"), "rb").read())
    # más lejos que Cerúlea (período 7 días): 3,0e6 s ≈ 35 días
    data("universe_planets/aurelia", {
        "name": "aurelia",
        "parent": "amber",
        "priority": 1000,
        "acceleration_at_surface": 12.0,
        "radius": 8.5e6,
        "position": {"type": "circular_orbit_period", "orbit_axis": {"x": 0.0, "y": 1.0, "z": 0.0},
                     "position_direction": {"x": -0.7, "y": 0.0, "z": 0.7}, "period_seconds": 3.0e6},
        "rotation": {"type": "period", "period_seconds": 600.0, "rotation_axis": {"x": 0.0, "y": -1.0, "z": 0.0}},
        "planet_extras": {"light_source_name": "amber"},
        "planet_texture": {"type": "resloc", "texture": f"{NS}:textures/planet/aurelia.png"},
    })
    LANG_EN[f"hologram.{NS}.body.aurelia"] = "Aurelia"
    LANG_ES[f"hologram.{NS}.body.aurelia"] = "Aurelia"
    n = f"nav.{NS}.body.aurelia."
    for field, (en, es) in {
            "type": ("Gas giant", "Gigante gaseoso"),
            "desc": ("A giant banded in amber and copper, with a great storm spinning in its southern hemisphere. It has no surface to land on, but its moon Fulgor does.",
                     "Un gigante a bandas de ámbar y cobre, con una gran tormenta girando en su hemisferio sur. No tiene superficie donde aterrizar, pero su luna Fulgor sí."),
            "hazards": ("No surface", "Sin superficie")}.items():
        LANG_EN[n + field] = en
        LANG_ES[n + field] = es


def build_methane_engine():
    """Soporte de motor de metano (1.0.4): bloque de MethaneEngine.java. El modelo hereda el del soporte de Cosmonautics y
    solo cambia la textura (tools/designs/methane_engine_texture.py)."""
    write_bytes(f"assets/{NS}/textures/block/methane_thruster_mount.png",
                open(os.path.join(DESIGNS, "methane_engine", "engine_mount.png"), "rb").read())
    tex = {"0": rl("block/methane_thruster_mount"), "particle": rl("block/methane_thruster_mount")}
    write(f"assets/{NS}/models/block/methane_thruster_mount.json", {"parent": "rocketnautics:block/thruster_mount", "textures": tex})
    write(f"assets/{NS}/models/item/methane_thruster_mount.json", {"parent": "rocketnautics:item/thruster_mount", "textures": tex})
    model = rl("block/methane_thruster_mount")
    write(f"assets/{NS}/blockstates/methane_thruster_mount.json", {"variants": {
        "facing=up": {"model": model},
        "facing=down": {"model": model, "x": 180},
        "facing=north": {"model": model, "x": 90},
        "facing=south": {"model": model, "x": 90, "y": 180},
        "facing=east": {"model": model, "x": 90, "y": 90},
        "facing=west": {"model": model, "x": 90, "y": 270}}})
    data("loot_table/blocks/methane_thruster_mount", {"type": "minecraft:block", "pools": [{
        "rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": rl("methane_thruster_mount")}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}], "random_sequence": rl("blocks/methane_thruster_mount")})
    tag_add("data/minecraft/tags/block/mineable/pickaxe.json", {"id": rl("methane_thruster_mount"), "required": False})
    tag_add("data/create/tags/block/wrench_pickup.json", {"id": rl("methane_thruster_mount"), "required": False})
    # receta (cuadrícula 3x3): mejora del soporte de Cosmonautics, que va en el centro. Láminas de aleación de titanio en las
    # esquinas, tubería de fluidos arriba, láminas de plomo a los lados y una tobera de titanio abajo. No depende de otros mods.
    data("recipe/methane_thruster_mount", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"A": {"tag": "c:plates/titanium_alloy"}, "P": {"item": "create:fluid_pipe"},
                "L": {"item": rl("lead_sheet")}, "M": {"item": "rocketnautics:thruster_mount"},
                "N": {"item": "rocketnautics:titanium_nozzle"}},
        "pattern": ["APA", "LML", "ANA"],
        "result": {"id": rl("methane_thruster_mount"), "count": 1}})
    LANG_EN[f"block.{NS}.methane_thruster_mount"] = "Methane Thruster Mount"
    LANG_ES[f"block.{NS}.methane_thruster_mount"] = "Soporte de motor de metano"


def build_titan():
    P = "titan"
    B = rl
    for f in sorted(os.listdir(os.path.join(TITAN_TEX, "block"))):
        write_bytes(f"assets/{NS}/textures/block/{f}", open(os.path.join(TITAN_TEX, "block", f), "rb").read())
    write_bytes(f"assets/{NS}/textures/item/methane_bucket.png", open(os.path.join(TITAN_TEX, "item", "methane_bucket.png"), "rb").read())

    def lang(kind, n, en, es):
        LANG_EN[f"{kind}.{NS}.{n}"] = en
        LANG_ES[f"{kind}.{NS}.{n}"] = es

    def model(n, parent, textures):
        write(f"assets/{NS}/models/block/{n}.json", {"parent": parent, "textures": {k: rl(f"block/{v}") for k, v in textures.items()}})

    def bstate(n, variants):
        write(f"assets/{NS}/blockstates/{n}.json", {"variants": variants})

    def item_block(n):
        write(f"assets/{NS}/models/item/{n}.json", {"parent": rl(f"block/{n}")})

    def loot(n, pools):
        data(f"loot_table/blocks/{n}", {"type": "minecraft:block", "pools": pools, "random_sequence": rl(f"blocks/{n}")})

    def pool(entry, conditions=None):
        pl = {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry]}
        if conditions:
            pl["conditions"] = conditions
        return pl

    SURVIVES = [{"condition": "minecraft:survives_explosion"}]
    SILK = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}

    def cube(n, en, es):
        model(n, "minecraft:block/cube_all", {"all": n})
        bstate(n, {"": {"model": rl(f"block/{n}")}})
        item_block(n)
        lang("block", n, en, es)

    def tag(kind, tagname, values):
        for v in values:
            tag_add(f"data/{tagname.split(':')[0]}/tags/{kind}/{tagname.split(':')[1]}.json", v)

    # --------------------------------------------------------------- bloques
    for n, en, es in (("tholin_sand", "Tholin Sand", "Arena de tolina"), ("tholin_crust", "Tholin Crust", "Corteza de tolina"),
                      ("titan_ice_rock", "Titan Ice Rock", "Roca de hielo de Titán"),
                      ("titan_ice_rock_bricks", "Titan Ice Rock Bricks", "Ladrillos de roca de hielo de Titán"),
                      ("polished_titan_ice_rock", "Polished Titan Ice Rock", "Roca de hielo de Titán pulida"),
                      ("bitumen", "Bitumen", "Bitumen")):
        cube(n, en, es)
        loot(n, [pool({"type": "minecraft:item", "name": B(n)}, SURVIVES)])

    def fortune(formula="minecraft:ore_drops", **extra):
        return {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": formula, **extra}
    TORES = {"coal": ("minecraft:coal", None, "Coal", "carbón", 0.1),
             "redstone": ("minecraft:redstone", (4, 5), "Redstone", "redstone", 0.7),
             "lapis": ("minecraft:lapis_lazuli", (4, 9), "Lapis Lazuli", "lapislázuli", 0.2),
             "sulfur": (B("venusian_sulfur"), (2, 4), "Sulfur", "azufre", 0.3)}
    for o, (item, count, en, es, xp) in TORES.items():
        n = f"titan_{o}_ore"
        if o == "redstone":
            model(n, "minecraft:block/cube_all", {"all": n})
            write(f"assets/{NS}/models/block/{n}_on.json", {"parent": "minecraft:block/cube_all", "textures": {"all": rl(f"block/{n}")}})
            bstate(n, {"lit=false": {"model": rl(f"block/{n}")}, "lit=true": {"model": rl(f"block/{n}_on")}})
            item_block(n)
            lang("block", n, f"Titan {en} Ore", f"Mena de {es} en roca de hielo de Titán")
        else:
            cube(n, f"Titan {en} Ore", f"Mena de {es} en roca de hielo de Titán")
        fns = []
        if count:
            fns.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}})
        fns.append(fortune("minecraft:uniform_bonus_count", parameters={"bonusMultiplier": 1}) if o == "redstone" else fortune())
        fns.append({"function": "minecraft:explosion_decay"})
        loot(n, [pool({"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": B(n), "conditions": [SILK]}, {"type": "minecraft:item", "name": item, "functions": fns}]})])
        smelt = {"coal": "minecraft:coal", "redstone": "minecraft:redstone", "lapis": "minecraft:lapis_lazuli", "sulfur": B("venusian_sulfur")}[o]
        for k, t in (("smelting", 200), ("blasting", 100)):
            data(f"recipe/titan/{o}_from_titan_ore_{k}", {"type": f"minecraft:{k}", "category": "misc",
                 "ingredient": {"item": B(n)}, "result": {"id": smelt}, "experience": xp, "cookingtime": t})

    FACING_Y = {"east": 0, "south": 90, "west": 180, "north": 270}
    shapes = {"straight": "", "inner_left": "_inner", "inner_right": "_inner", "outer_left": "_outer", "outer_right": "_outer"}
    stair_ids, slab_ids = [], []
    for prefix, base, en, es in (("titan_ice_rock", "titan_ice_rock", "Titan Ice Rock", "de roca de hielo de Titán"),
                                 ("titan_ice_rock_brick", "titan_ice_rock_bricks", "Titan Ice Rock Brick", "de ladrillos de roca de hielo de Titán"),
                                 ("polished_titan_ice_rock", "polished_titan_ice_rock", "Polished Titan Ice Rock", "de roca de hielo de Titán pulida")):
        st_, sl_ = f"{prefix}_stairs", f"{prefix}_slab"
        tx = {"bottom": base, "top": base, "side": base}
        model(st_, "minecraft:block/stairs", tx)
        model(st_ + "_inner", "minecraft:block/inner_stairs", tx)
        model(st_ + "_outer", "minecraft:block/outer_stairs", tx)
        var = {}
        for facing, y0 in FACING_Y.items():
            for half in ("bottom", "top"):
                for shape, suffix in shapes.items():
                    y = y0
                    if shape in ("inner_left", "outer_left") and half == "bottom":
                        y += 270
                    if shape in ("inner_right", "outer_right") and half == "top":
                        y += 90
                    v = {"model": rl(f"block/{st_}{suffix}")}
                    if half == "top":
                        v["x"] = 180
                    if y % 360:
                        v["y"] = y % 360
                    if "x" in v or "y" in v:
                        v["uvlock"] = True
                    var[f"facing={facing},half={half},shape={shape}"] = v
        bstate(st_, var)
        item_block(st_)
        lang("block", st_, f"{en} Stairs", f"Escaleras {es}")
        loot(st_, [pool({"type": "minecraft:item", "name": B(st_)}, SURVIVES)])
        model(sl_, "minecraft:block/slab", tx)
        model(sl_ + "_top", "minecraft:block/slab_top", tx)
        bstate(sl_, {"type=bottom": {"model": rl(f"block/{sl_}")}, "type=top": {"model": rl(f"block/{sl_}_top")},
                     "type=double": {"model": rl(f"block/{base}")}})
        item_block(sl_)
        lang("block", sl_, f"{en} Slab", f"Losa {es}")
        loot(sl_, [pool({"type": "minecraft:item", "name": B(sl_), "functions": [
            {"function": "minecraft:set_count", "count": 2.0, "add": False,
             "conditions": [{"condition": "minecraft:block_state_property", "block": B(sl_), "properties": {"type": "double"}}]},
            {"function": "minecraft:explosion_decay"}]})])
        data(f"recipe/titan/{st_}", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["#  ", "## ", "###"],
                                     "key": {"#": {"item": B(base)}}, "result": {"id": B(st_), "count": 4}})
        data(f"recipe/titan/{sl_}", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["###"],
                                     "key": {"#": {"item": B(base)}}, "result": {"id": B(sl_), "count": 6}})
        for out, cnt in ((st_, 1), (sl_, 2)):
            data(f"recipe/titan/{out}_from_{base}_stonecutting", {"type": "minecraft:stonecutting",
                 "ingredient": {"item": B(base)}, "result": {"id": B(out), "count": cnt}})
        stair_ids.append(B(st_))
        slab_ids.append(B(sl_))
    for out, ing in (("polished_titan_ice_rock", "titan_ice_rock"), ("titan_ice_rock_bricks", "polished_titan_ice_rock")):
        data(f"recipe/titan/{out}", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["##", "##"],
                                     "key": {"#": {"item": B(ing)}}, "result": {"id": B(out), "count": 4}})
        data(f"recipe/titan/{out}_stonecutting", {"type": "minecraft:stonecutting", "ingredient": {"item": B("titan_ice_rock")},
                                                  "result": {"id": B(out), "count": 1}})

    ores = [B(f"titan_{o}_ore") for o in TORES]
    rocks = [B("titan_ice_rock"), B("titan_ice_rock_bricks"), B("polished_titan_ice_rock"), B("bitumen")]
    tag("block", "minecraft:mineable/shovel", [B("tholin_sand"), B("tholin_crust")])
    tag("block", "minecraft:sand", [B("tholin_sand")])
    tag("block", "minecraft:mineable/pickaxe", rocks + ores + stair_ids + slab_ids)
    tag("block", "minecraft:needs_stone_tool", [B("titan_coal_ore"), B("titan_sulfur_ore")])
    tag("block", "minecraft:needs_iron_tool", [B("titan_lapis_ore"), B("titan_redstone_ore")])
    tag("block", "minecraft:base_stone_overworld", [B("titan_ice_rock")])
    tag("block", "minecraft:overworld_carver_replaceables", [B("titan_ice_rock"), B("tholin_sand"), B("tholin_crust")])
    for kind_ in ("block", "item"):
        tag(kind_, "minecraft:stairs", stair_ids)
        tag(kind_, "minecraft:slabs", slab_ids)
        tag(kind_, "minecraft:coal_ores", [B("titan_coal_ore")])
        tag(kind_, "minecraft:redstone_ores", [B("titan_redstone_ore")])
        tag(kind_, "minecraft:lapis_ores", [B("titan_lapis_ore")])
        for o in ("coal", "redstone", "lapis"):
            tag(kind_, f"c:ores/{o}", [B(f"titan_{o}_ore")])
        for o in TORES:
            tag(kind_, "c:ores", [B(f"titan_{o}_ore")])
    tag("item", "minecraft:stone_crafting_materials", [B("titan_ice_rock")])
    tag("item", "minecraft:stone_tool_materials", [B("titan_ice_rock")])
    data("tags/block/titan_ore_replaceable", {"values": [B("titan_ice_rock")]})

    # --------------------------------------------------------------- combustibles (el bitumen y el metano arden en los hornos)
    datamap_add("data/neoforge/data_maps/item/furnace_fuels.json", {B("bitumen"): {"burn_time": 16000},
                                                                   B("methane_bucket"): {"burn_time": 24000}})

    # --------------------------------------------------------------- metano líquido
    write(f"assets/{NS}/models/block/methane.json", {"textures": {"particle": rl("block/methane_still")}})
    bstate("methane", {"": {"model": rl("block/methane")}})
    write(f"assets/{NS}/models/item/methane_bucket.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rl("item/methane_bucket")}})
    lang("block", "methane", "Liquid Methane", "Metano líquido")
    lang("item", "methane_bucket", "Liquid Methane Bucket", "Cubo de metano líquido")
    # el metano a 111 K: tampoco se puede beber ni apaga el fuego; los fluidos propios se marcan para que no se confundan con agua
    write("data/c/tags/fluid/methane.json", {"values": [B("methane"), B("flowing_methane")]})
    # combustible de cohete: Cosmonautics solo acepta en sus motores los fluidos de rocketnautics:rocket_fuel (el oxidante
    # sigue siendo el suyo: agua o aire). El rendimiento propio del metano lo da RocketFuelPropertiesMixin.
    tag_add("data/rocketnautics/tags/fluid/rocket_fuel.json", B("methane"))
    tag_add("data/rocketnautics/tags/fluid/rocket_fuel.json", B("flowing_methane"))
    # 1.0.4: combustible de Create Diesel Generators (registro de datapack createdieselgenerators:fuel_type; si el mod no esta
    # instalado la carpeta se ignora). Queda entre la gasolina (4096) y el diesel (6144) en motores normales; se ha dejado a
    # los valores de la gasolina el resto (velocidad, consumo) para que el metano no sea mejor que lo que ya existe en ese mod.
    data("createdieselgenerators/fuel_type/methane", {
        "fluid": "#c:methane", "sound_pitch": 0.9,
        "normal": {"speed": 96.0, "strength": 5120.0, "burn_rate": 0.05},
        "modular": {"speed": 96.0, "strength": 7168.0, "burn_rate": 0.05},
        "huge": {"speed": 224.0, "strength": 14336.0, "burn_rate": 0.05},
        "burner_multiplier": 1.05})

    # --------------------------------------------------------------- terreno
    # Se queda en y 0..256 (min_y 0): así el selector de fluidos de Minecraft nunca pone lava en el fondo (solo lo hace
    # por debajo de y -54). Nivel del mar 57 con metano; las cuencas de los lagos bajan el terreno hasta ~40 donde el ruido
    # "lake" pasa de 0,15; las tierras altas (ruido "regions" > 0,25) suben ~16 bloques.
    def noise(n, first, amps):
        data(f"worldgen/noise/{P}/{n}", {"firstOctave": first, "amplitudes": amps})
        return rl(f"{P}/{n}")
    hills, detail = noise("hills", -8, [1.0, 1.0, 0.5]), noise("detail", -5, [1.0, 0.5])
    regions, lake = noise("regions", -9, [1.0, 1.0]), noise("lake", -8, [1.0, 0.6])
    n2 = lambda nn: {"type": "minecraft:noise", "noise": nn, "xz_scale": 1.0, "y_scale": 0.0}           # noqa: E731
    base_h, sea = 78, 57
    basin = {"type": "minecraft:clamp", "min": 0.0, "max": 1.0, "input": {"type": "minecraft:mul", "argument1": 6.0, "argument2": {
        "type": "minecraft:add", "argument1": n2(lake), "argument2": -0.15}}}
    high = {"type": "minecraft:clamp", "min": 0.0, "max": 1.0, "input": {"type": "minecraft:mul", "argument1": 3.0, "argument2": {
        "type": "minecraft:add", "argument1": n2(regions), "argument2": -0.25}}}
    density = {"type": "minecraft:add",
               "argument1": {"type": "minecraft:y_clamped_gradient", "from_y": 0, "to_y": 256,
                             "from_value": base_h / 16.0, "to_value": (base_h - 256) / 16.0},
               "argument2": {"type": "minecraft:add",
                             "argument1": {"type": "minecraft:add",
                                           "argument1": {"type": "minecraft:mul", "argument1": n2(hills), "argument2": 8 / 16.0},
                                           "argument2": {"type": "minecraft:mul", "argument1": n2(detail), "argument2": 3 / 16.0}},
                             "argument2": {"type": "minecraft:add",
                                           "argument1": {"type": "minecraft:mul", "argument1": basin, "argument2": -40 / 16.0},
                                           "argument2": {"type": "minecraft:mul", "argument1": high, "argument2": 16 / 16.0}}}}
    # cuevas de ruido como las de Ferra (entradas, túneles y cavernas); por debajo del nivel del mar se llenan de metano
    cl = RouterCloner(P)
    entr = cl.df("minecraft:overworld/caves/entrances")
    spag = {"type": "minecraft:add", "argument1": cl.df("minecraft:overworld/caves/spaghetti_2d"),
            "argument2": cl.df("minecraft:overworld/caves/spaghetti_roughness_function")}
    noodle = cl.df("minecraft:overworld/caves/noodle")
    cheese = {"type": "minecraft:add",
              "argument1": {"type": "minecraft:mul", "argument1": 4.0, "argument2": {"type": "minecraft:square", "argument": {
                  "type": "minecraft:noise", "noise": cl.noise("minecraft:cave_layer"), "xz_scale": 1.0, "y_scale": 8.0}}},
              "argument2": {"type": "minecraft:add",
                            "argument1": {"type": "minecraft:clamp", "min": -1.0, "max": 1.0, "input": {
                                "type": "minecraft:add", "argument1": 0.27, "argument2": {
                                    "type": "minecraft:noise", "noise": cl.noise("minecraft:cave_cheese"), "xz_scale": 1.0, "y_scale": 0.6666666666666666}}},
                            "argument2": {"type": "minecraft:clamp", "min": 0.0, "max": 0.5, "input": {
                                "type": "minecraft:add", "argument1": 1.5, "argument2": {"type": "minecraft:mul", "argument1": -0.64, "argument2": density}}}}}
    deep = {"type": "minecraft:min", "argument1": {"type": "minecraft:min", "argument1": cheese, "argument2": entr}, "argument2": spag}
    caved = {"type": "minecraft:range_choice", "input": density, "min_inclusive": -1000000.0, "max_exclusive": 1.5625,
             "when_in_range": {"type": "minecraft:min", "argument1": density, "argument2": {"type": "minecraft:mul", "argument1": 5.0, "argument2": entr}},
             "when_out_of_range": deep}
    final = {"type": "minecraft:min", "argument1": {"type": "minecraft:interpolated", "argument": caved}, "argument2": noodle}
    zero = 0.0
    router = {"barrier": zero, "fluid_level_floodedness": zero, "fluid_level_spread": zero, "lava": zero,
              "temperature": zero, "vegetation": zero, "continents": n2(regions), "erosion": zero, "depth": zero,
              "ridges": n2(lake), "initial_density_without_jaggedness": density,
              "final_density": final,
              "vein_toggle": zero, "vein_ridged": zero, "vein_gap": zero}
    sand, crust, bitumen = {"Name": B("tholin_sand")}, {"Name": B("tholin_crust")}, {"Name": B("bitumen")}
    surface = seq(BEDROCK_FLOOR, cond(ABOVE_PRELIM, seq(
        cond(biome_is("titan_methane_lake"), cond(UNDER_FLOOR, put(bitumen))),
        cond(biome_is("titan_methane_shore"), cond(UNDER_FLOOR, seq(
            cond(noise_band("minecraft:surface", 0.2, 2.0), put(bitumen)), put(sand)))),
        cond(biome_is("titan_dunes"), cond(UNDER_FLOOR, put(sand))),
        cond(biome_is("titan_tholin_plains"), cond(UNDER_FLOOR, put(crust))),
        cond(biome_is("titan_ice_highlands"), cond(ON_FLOOR, cond(noise_band("minecraft:surface", 0.25, 2.0), put(crust)))))))
    noise_settings(P, router, default_block={"Name": B("titan_ice_rock")}, default_fluid=block(B("methane"), level=0),
                   sea_level=sea, aquifers=False, surface_rule=surface, min_y=0, height=256, spawn_target=[
                       {"temperature": FULL, "humidity": FULL, "continentalness": [-0.4, 0.2], "erosion": FULL,
                        "weirdness": [-2.0, 0.0], "depth": 0.0, "offset": 0.0}])

    def configured(nm, obj):
        data(f"worldgen/configured_feature/{P}/{nm}", obj)
        return rl(f"{P}/{nm}")

    def placed(nm, feature, placement):
        data(f"worldgen/placed_feature/{P}/{nm}", {"feature": feature, "placement": placement})
        return rl(f"{P}/{nm}")

    def ore(nm, block_id, size, count, lo, hi, shape="minecraft:uniform"):
        cf = configured(f"ore_{nm}", {"type": "minecraft:ore", "config": {"size": size, "discard_chance_on_air_exposure": 0.0,
            "targets": [{"target": {"predicate_type": "minecraft:tag_match", "tag": f"{NS}:titan_ore_replaceable"},
                         "state": {"Name": B(block_id)}}]}})
        return placed(f"ore_{nm}", cf, [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
                                        {"type": "minecraft:height_range", "height": {"type": shape, "min_inclusive": {"absolute": lo},
                                                                                      "max_inclusive": {"absolute": hi}}},
                                        {"type": "minecraft:biome"}])
    # especialidad de Titán: combustibles (carbón, redstone, lapislázuli y azufre), varias veces lo de la Tierra
    ores_t = [ore("coal", "titan_coal_ore", 18, 60, 0, 190), ore("sulfur", "titan_sulfur_ore", 12, 26, 0, 150),
              ore("redstone", "titan_redstone_ore", 12, 26, 0, 110, "minecraft:trapezoid"),
              ore("lapis", "titan_lapis_ore", 10, 20, 0, 120, "minecraft:trapezoid")]

    sky, fog = rgb(140, 84, 38), rgb(170, 104, 48)
    common = dict(temperature=-0.5, downfall=0.0, precipitation=False, sky=sky, fog=fog, water=rgb(20, 70, 82),
                  water_fog=rgb(8, 30, 38), carvers=[])
    for nm in ("titan_dunes", "titan_tholin_plains", "titan_ice_highlands", "titan_methane_shore", "titan_methane_lake"):
        biome(nm, feats=features(underground_ores=ores_t), **common)
    name("titan_dunes", "Tholin Dunes", "Dunas de tolina")
    name("titan_tholin_plains", "Tholin Plains", "Llanuras de tolina")
    name("titan_ice_highlands", "Ice Highlands", "Tierras altas de hielo")
    name("titan_methane_shore", "Methane Shore", "Orilla de metano")
    name("titan_methane_lake", "Methane Lake", "Lago de metano")
    pts = [point("titan_methane_lake", weirdness=[0.12, 2.0]), point("titan_methane_shore", weirdness=[0.0, 0.12]),
           point("titan_dunes", continentalness=[-2.0, -0.2], weirdness=[-2.0, 0.0]),
           point("titan_tholin_plains", continentalness=[-0.2, 0.25], weirdness=[-2.0, 0.0]),
           point("titan_ice_highlands", continentalness=[0.25, 2.0], weirdness=[-2.0, 0.0])]
    dimension(P, pts)
    dimension_type(P, effects="minecraft:overworld", min_y=0, height=256)
    # atmósfera densa (1,4 bar): mucho rozamiento para las naves y para quien se lance de lo alto
    dimension_physics(P, universal_drag=0.008, base_gravity=[0.0, -1.35, 0.0],
                      pressure_function=[{"altitude": 80.0, "value": 1.4, "slope": 0.0},
                                         {"altitude": 3000.0, "value": 0.0, "slope": 0.0}])

    data(f"universe_planets/{P}", {
        "name": P,
        "parent": "saturn",
        "priority": 1000,
        "acceleration_at_surface": 1.35,
        "radius": 1.2e6,
        "position": {"type": "circular_orbit_period", "orbit_axis": {"x": 0.0, "y": 1.0, "z": 0.0},
                     "position_direction": {"x": 1.0, "y": 0.0, "z": 0.0}, "period_seconds": 100000.0},
        "rotation": {"type": "tidal_lock", "rotations_per_orbit": -1.0},
        "planet_extras": {"light_source_name": "sol"},
        "dimension_data": {
            "linked_dimension": rl(P), "allowed_transfer": "all", "dimension_transfer_height": 20000,
            "atmosphere_composition": {"21000": ["drowning"]},
            "render_universe_in_dimension": True, "dimension_day_time_controller_name": "sol",
            "apply_gravity_correction_to_entities_in_dimension": True, "entity_drag_multiplier": drag_curve(2500, 6000),
        },
        "planet_atmosphere": {"enabled": True, "radius": 1.12, "intensity": 0.8, "densityFalloff": 4.0,
                              "scatteringStrength": 0.09, "wavelengths": [450.0, 540.0, 700.0]},
        "planet_texture": {"type": "biome_sampler", "fallback_red": 190, "fallback_green": 120, "fallback_blue": 56,
                           "colors": [color(196, 124, 54, biomes=["titan_dunes"]), color(156, 96, 46, biomes=["titan_tholin_plains"]),
                                      color(176, 150, 128, biomes=["titan_ice_highlands"], priority=5),
                                      color(104, 74, 48, biomes=["titan_methane_shore"]),
                                      color(24, 64, 76, biomes=["titan_methane_lake"], ocean=True)]},
    })
    LANG_EN[f"hologram.{NS}.body.{P}"] = "Titan"
    LANG_ES[f"hologram.{NS}.body.{P}"] = "Titán"
    n = f"nav.{NS}."
    for field, (en, es) in {
            "type": ("Moon of Saturn", "Luna de Saturno"),
            "desc": ("A frozen moon under a thick orange haze, with tholin dunes and lakes of liquid methane.",
                     "Una luna helada bajo una espesa bruma naranja, con dunas de tolina y lagos de metano líquido."),
            "air": ("Dense, not breathable", "Densa, no respirable"), "temp": ("Freezing (-179 °C)", "Helada (-179 °C)"),
            "hazards": ("Extreme cold, liquid methane", "Frío extremo, metano líquido"),
            "armor": ("Glacial Armor", "Glacial Armor"),
            "resources": ("Huge amounts of coal, redstone, lapis lazuli and sulfur; bitumen and liquid methane as fuel",
                          "Muchísimo carbón, redstone, lapislázuli y azufre; bitumen y metano líquido como combustible")}.items():
        LANG_EN[f"{n}body.{P}.{field}"] = en
        LANG_ES[f"{n}body.{P}.{field}"] = es



FULGOR_TEX = os.path.join(DESIGNS, "fulgor")


def build_fulgor():
    """Fulgor (1.0.4): luna de tormentas eléctricas de Aurelia, no respirable. Bloques de FulgorBlocks.java; rayos en
    weather/FulgorStorms.java (tipo de tormenta FULGOR_STORM). Especialidad: minerales conductores (cobre, zinc, redstone)."""
    P = "fulgor"
    B = rl
    for f in sorted(os.listdir(os.path.join(FULGOR_TEX, "block"))):
        write_bytes(f"assets/{NS}/textures/block/{f}", open(os.path.join(FULGOR_TEX, "block", f), "rb").read())

    def lang(kind, n, en, es):
        LANG_EN[f"{kind}.{NS}.{n}"] = en
        LANG_ES[f"{kind}.{NS}.{n}"] = es

    def model(n, parent, textures):
        write(f"assets/{NS}/models/block/{n}.json", {"parent": parent, "textures": {k: rl(f"block/{v}") for k, v in textures.items()}})

    def bstate(n, variants):
        write(f"assets/{NS}/blockstates/{n}.json", {"variants": variants})

    def item_block(n):
        write(f"assets/{NS}/models/item/{n}.json", {"parent": rl(f"block/{n}")})

    def loot(n, pools):
        data(f"loot_table/blocks/{n}", {"type": "minecraft:block", "pools": pools, "random_sequence": rl(f"blocks/{n}")})

    def pool(entry, conditions=None):
        pl = {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry]}
        if conditions:
            pl["conditions"] = conditions
        return pl

    SURVIVES = [{"condition": "minecraft:survives_explosion"}]
    SILK = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}

    def cube(n, en, es):
        model(n, "minecraft:block/cube_all", {"all": n})
        bstate(n, {"": {"model": rl(f"block/{n}")}})
        item_block(n)
        lang("block", n, en, es)

    def tag(kind, tagname, values):
        for v in values:
            tag_add(f"data/{tagname.split(':')[0]}/tags/{kind}/{tagname.split(':')[1]}.json", v)

    # --------------------------------------------------------------- bloques
    for n, en, es in (("fulgor_basalt", "Storm Basalt", "Basalto de tormenta"),
                      ("fulgor_basalt_bricks", "Storm Basalt Bricks", "Ladrillos de basalto de tormenta"),
                      ("polished_fulgor_basalt", "Polished Storm Basalt", "Basalto de tormenta pulido"),
                      ("storm_dust", "Storm Dust", "Polvo de tormenta"), ("fulgurite", "Fulgurite", "Fulgurita")):
        cube(n, en, es)
        loot(n, [pool({"type": "minecraft:item", "name": B(n)}, SURVIVES)])

    def fortune(formula="minecraft:ore_drops", **extra):
        return {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": formula, **extra}
    FORES = {"copper": ("minecraft:raw_copper", (2, 5), "Copper", "cobre", "minecraft:copper_ingot", 0.7),
             "zinc": ("create:raw_zinc", (1, 2), "Zinc", "zinc", "create:zinc_ingot", 0.7),
             "redstone": ("minecraft:redstone", (4, 5), "Redstone", "redstone", "minecraft:redstone", 0.7)}
    for o, (item, count, en, es, smelt, xp) in FORES.items():
        n = f"fulgor_{o}_ore"
        if o == "redstone":
            model(n, "minecraft:block/cube_all", {"all": n})
            write(f"assets/{NS}/models/block/{n}_on.json", {"parent": "minecraft:block/cube_all", "textures": {"all": rl(f"block/{n}")}})
            bstate(n, {"lit=false": {"model": rl(f"block/{n}")}, "lit=true": {"model": rl(f"block/{n}_on")}})
            item_block(n)
            lang("block", n, f"Storm Basalt {en} Ore", f"Mena de {es} en basalto de tormenta")
        else:
            cube(n, f"Storm Basalt {en} Ore", f"Mena de {es} en basalto de tormenta")
        fns = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}},
               fortune("minecraft:uniform_bonus_count", parameters={"bonusMultiplier": 1}) if o == "redstone" else fortune(),
               {"function": "minecraft:explosion_decay"}]
        loot(n, [pool({"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": B(n), "conditions": [SILK]}, {"type": "minecraft:item", "name": item, "functions": fns}]})])
        if o != "redstone":
            # la materia prima se funde (cobre y zinc en bruto); la redstone ya sale lista
            raw = {"copper": "minecraft:raw_copper", "zinc": "create:raw_zinc"}[o]
            data_ing = {"item": B(n)}
            for k, t in (("smelting", 200), ("blasting", 100)):
                data(f"recipe/fulgor/{o}_ingot_from_fulgor_ore_{k}", {"type": f"minecraft:{k}", "category": "misc",
                     "ingredient": data_ing, "result": {"id": smelt}, "experience": xp, "cookingtime": t})
        else:
            for k, t in (("smelting", 200), ("blasting", 100)):
                data(f"recipe/fulgor/redstone_from_fulgor_ore_{k}", {"type": f"minecraft:{k}", "category": "misc",
                     "ingredient": {"item": B(n)}, "result": {"id": smelt}, "experience": xp, "cookingtime": t})

    FACING_Y = {"east": 0, "south": 90, "west": 180, "north": 270}
    shapes = {"straight": "", "inner_left": "_inner", "inner_right": "_inner", "outer_left": "_outer", "outer_right": "_outer"}
    stair_ids, slab_ids = [], []
    for prefix, base, en, es in (("fulgor_basalt", "fulgor_basalt", "Storm Basalt", "de basalto de tormenta"),
                                 ("fulgor_basalt_brick", "fulgor_basalt_bricks", "Storm Basalt Brick", "de ladrillos de basalto de tormenta"),
                                 ("polished_fulgor_basalt", "polished_fulgor_basalt", "Polished Storm Basalt", "de basalto de tormenta pulido")):
        st_, sl_ = f"{prefix}_stairs", f"{prefix}_slab"
        tx = {"bottom": base, "top": base, "side": base}
        model(st_, "minecraft:block/stairs", tx)
        model(st_ + "_inner", "minecraft:block/inner_stairs", tx)
        model(st_ + "_outer", "minecraft:block/outer_stairs", tx)
        var = {}
        for facing, y0 in FACING_Y.items():
            for half in ("bottom", "top"):
                for shape, suffix in shapes.items():
                    y = y0
                    if shape in ("inner_left", "outer_left") and half == "bottom":
                        y += 270
                    if shape in ("inner_right", "outer_right") and half == "top":
                        y += 90
                    v = {"model": rl(f"block/{st_}{suffix}")}
                    if half == "top":
                        v["x"] = 180
                    if y % 360:
                        v["y"] = y % 360
                    if "x" in v or "y" in v:
                        v["uvlock"] = True
                    var[f"facing={facing},half={half},shape={shape}"] = v
        bstate(st_, var)
        item_block(st_)
        lang("block", st_, f"{en} Stairs", f"Escaleras {es}")
        loot(st_, [pool({"type": "minecraft:item", "name": B(st_)}, SURVIVES)])
        model(sl_, "minecraft:block/slab", tx)
        model(sl_ + "_top", "minecraft:block/slab_top", tx)
        bstate(sl_, {"type=bottom": {"model": rl(f"block/{sl_}")}, "type=top": {"model": rl(f"block/{sl_}_top")},
                     "type=double": {"model": rl(f"block/{base}")}})
        item_block(sl_)
        lang("block", sl_, f"{en} Slab", f"Losa {es}")
        loot(sl_, [pool({"type": "minecraft:item", "name": B(sl_), "functions": [
            {"function": "minecraft:set_count", "count": 2.0, "add": False,
             "conditions": [{"condition": "minecraft:block_state_property", "block": B(sl_), "properties": {"type": "double"}}]},
            {"function": "minecraft:explosion_decay"}]})])
        data(f"recipe/fulgor/{st_}", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["#  ", "## ", "###"],
                                      "key": {"#": {"item": B(base)}}, "result": {"id": B(st_), "count": 4}})
        data(f"recipe/fulgor/{sl_}", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["###"],
                                      "key": {"#": {"item": B(base)}}, "result": {"id": B(sl_), "count": 6}})
        for out, cnt in ((st_, 1), (sl_, 2)):
            data(f"recipe/fulgor/{out}_from_{base}_stonecutting", {"type": "minecraft:stonecutting",
                 "ingredient": {"item": B(base)}, "result": {"id": B(out), "count": cnt}})
        stair_ids.append(B(st_))
        slab_ids.append(B(sl_))
    for out, ing in (("polished_fulgor_basalt", "fulgor_basalt"), ("fulgor_basalt_bricks", "polished_fulgor_basalt")):
        data(f"recipe/fulgor/{out}", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["##", "##"],
                                      "key": {"#": {"item": B(ing)}}, "result": {"id": B(out), "count": 4}})
        data(f"recipe/fulgor/{out}_stonecutting", {"type": "minecraft:stonecutting", "ingredient": {"item": B("fulgor_basalt")},
                                                   "result": {"id": B(out), "count": 1}})

    ores = [B(f"fulgor_{o}_ore") for o in FORES]
    rocks = [B("fulgor_basalt"), B("fulgor_basalt_bricks"), B("polished_fulgor_basalt"), B("fulgurite")]
    tag("block", "minecraft:mineable/shovel", [B("storm_dust")])
    tag("block", "minecraft:sand", [B("storm_dust")])
    tag("block", "minecraft:mineable/pickaxe", rocks + ores + stair_ids + slab_ids)
    tag("block", "minecraft:needs_stone_tool", [B("fulgor_copper_ore"), B("fulgor_zinc_ore")])
    tag("block", "minecraft:needs_iron_tool", [B("fulgor_redstone_ore")])
    tag("block", "minecraft:base_stone_overworld", [B("fulgor_basalt")])
    tag("block", "minecraft:overworld_carver_replaceables", [B("fulgor_basalt"), B("storm_dust")])
    for kind_ in ("block", "item"):
        tag(kind_, "minecraft:stairs", stair_ids)
        tag(kind_, "minecraft:slabs", slab_ids)
        tag(kind_, "minecraft:copper_ores", [B("fulgor_copper_ore")])
        tag(kind_, "minecraft:redstone_ores", [B("fulgor_redstone_ore")])
        tag(kind_, "c:ores/copper", [B("fulgor_copper_ore")])
        tag(kind_, "c:ores/zinc", [B("fulgor_zinc_ore")])
        tag(kind_, "c:ores/redstone", [B("fulgor_redstone_ore")])
        for o in FORES:
            tag(kind_, "c:ores", [B(f"fulgor_{o}_ore")])
    tag("item", "minecraft:stone_crafting_materials", [B("fulgor_basalt")])
    tag("item", "minecraft:stone_tool_materials", [B("fulgor_basalt")])
    data("tags/block/fulgor_ore_replaceable", {"values": [B("fulgor_basalt")]})

    # --------------------------------------------------------------- terreno
    # y 0..256 como Titán. Sin líquidos (default_fluid aire). Montes de basalto ("regions" > 0,25 sube ~24 bloques) sobre
    # llanuras de polvo; el relieve es algo más quebrado que el de Titán.
    def noise(n, first, amps):
        data(f"worldgen/noise/{P}/{n}", {"firstOctave": first, "amplitudes": amps})
        return rl(f"{P}/{n}")
    hills, detail = noise("hills", -8, [1.0, 1.0, 0.5]), noise("detail", -5, [1.0, 0.5])
    regions, lake = noise("regions", -9, [1.0, 1.0]), noise("lake", -8, [1.0, 0.6])
    n2 = lambda nn: {"type": "minecraft:noise", "noise": nn, "xz_scale": 1.0, "y_scale": 0.0}           # noqa: E731
    base_h = 74
    high = {"type": "minecraft:clamp", "min": 0.0, "max": 1.0, "input": {"type": "minecraft:mul", "argument1": 3.0, "argument2": {
        "type": "minecraft:add", "argument1": n2(regions), "argument2": -0.25}}}
    density = {"type": "minecraft:add",
               "argument1": {"type": "minecraft:y_clamped_gradient", "from_y": 0, "to_y": 256,
                             "from_value": base_h / 16.0, "to_value": (base_h - 256) / 16.0},
               "argument2": {"type": "minecraft:add",
                             "argument1": {"type": "minecraft:add",
                                           "argument1": {"type": "minecraft:mul", "argument1": n2(hills), "argument2": 10 / 16.0},
                                           "argument2": {"type": "minecraft:mul", "argument1": n2(detail), "argument2": 4 / 16.0}},
                             "argument2": {"type": "minecraft:mul", "argument1": high, "argument2": 24 / 16.0}}}
    cl = RouterCloner(P)
    entr = cl.df("minecraft:overworld/caves/entrances")
    spag = {"type": "minecraft:add", "argument1": cl.df("minecraft:overworld/caves/spaghetti_2d"),
            "argument2": cl.df("minecraft:overworld/caves/spaghetti_roughness_function")}
    noodle = cl.df("minecraft:overworld/caves/noodle")
    cheese = {"type": "minecraft:add",
              "argument1": {"type": "minecraft:mul", "argument1": 4.0, "argument2": {"type": "minecraft:square", "argument": {
                  "type": "minecraft:noise", "noise": cl.noise("minecraft:cave_layer"), "xz_scale": 1.0, "y_scale": 8.0}}},
              "argument2": {"type": "minecraft:add",
                            "argument1": {"type": "minecraft:clamp", "min": -1.0, "max": 1.0, "input": {
                                "type": "minecraft:add", "argument1": 0.27, "argument2": {
                                    "type": "minecraft:noise", "noise": cl.noise("minecraft:cave_cheese"), "xz_scale": 1.0, "y_scale": 0.6666666666666666}}},
                            "argument2": {"type": "minecraft:clamp", "min": 0.0, "max": 0.5, "input": {
                                "type": "minecraft:add", "argument1": 1.5, "argument2": {"type": "minecraft:mul", "argument1": -0.64, "argument2": density}}}}}
    deep = {"type": "minecraft:min", "argument1": {"type": "minecraft:min", "argument1": cheese, "argument2": entr}, "argument2": spag}
    caved = {"type": "minecraft:range_choice", "input": density, "min_inclusive": -1000000.0, "max_exclusive": 1.5625,
             "when_in_range": {"type": "minecraft:min", "argument1": density, "argument2": {"type": "minecraft:mul", "argument1": 5.0, "argument2": entr}},
             "when_out_of_range": deep}
    final = {"type": "minecraft:min", "argument1": {"type": "minecraft:interpolated", "argument": caved}, "argument2": noodle}
    zero = 0.0
    router = {"barrier": zero, "fluid_level_floodedness": zero, "fluid_level_spread": zero, "lava": zero,
              "temperature": zero, "vegetation": zero, "continents": n2(regions), "erosion": zero, "depth": zero,
              "ridges": n2(lake), "initial_density_without_jaggedness": density,
              "final_density": final,
              "vein_toggle": zero, "vein_ridged": zero, "vein_gap": zero}
    dust, fulg = {"Name": B("storm_dust")}, {"Name": B("fulgurite")}
    surface = seq(BEDROCK_FLOOR, cond(ABOVE_PRELIM, seq(
        cond(biome_is("fulgor_ash_flats"), cond(UNDER_FLOOR, put(dust))),
        cond(biome_is("fulgor_fulgurite_fields"), seq(
            cond(ON_FLOOR, cond(noise_band("minecraft:surface", 0.2, 2.0), put(fulg))),
            cond(UNDER_FLOOR, put(dust)))),
        cond(biome_is("fulgor_basalt_plains"), cond(ON_FLOOR, cond(noise_band("minecraft:surface", 0.1, 2.0), put(dust)))))))
    noise_settings(P, router, default_block={"Name": B("fulgor_basalt")}, default_fluid=block("minecraft:air"),
                   sea_level=40, aquifers=False, surface_rule=surface, min_y=0, height=256, spawn_target=[
                       {"temperature": FULL, "humidity": FULL, "continentalness": [-0.4, 0.2], "erosion": FULL,
                        "weirdness": [-2.0, 0.0], "depth": 0.0, "offset": 0.0}])

    def configured(nm, obj):
        data(f"worldgen/configured_feature/{P}/{nm}", obj)
        return rl(f"{P}/{nm}")

    def placed(nm, feature, placement):
        data(f"worldgen/placed_feature/{P}/{nm}", {"feature": feature, "placement": placement})
        return rl(f"{P}/{nm}")

    def ore(nm, block_id, size, count, lo, hi, shape="minecraft:uniform"):
        cf = configured(f"ore_{nm}", {"type": "minecraft:ore", "config": {"size": size, "discard_chance_on_air_exposure": 0.0,
            "targets": [{"target": {"predicate_type": "minecraft:tag_match", "tag": f"{NS}:fulgor_ore_replaceable"},
                         "state": {"Name": B(block_id)}}]}})
        return placed(f"ore_{nm}", cf, [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
                                        {"type": "minecraft:height_range", "height": {"type": shape, "min_inclusive": {"absolute": lo},
                                                                                      "max_inclusive": {"absolute": hi}}},
                                        {"type": "minecraft:biome"}])
    # especialidad de Fulgor: minerales conductores, varias veces lo de la Tierra
    ores_f = [ore("copper", "fulgor_copper_ore", 16, 56, 0, 200), ore("zinc", "fulgor_zinc_ore", 12, 36, 0, 160),
              ore("redstone", "fulgor_redstone_ore", 12, 30, 0, 120, "minecraft:trapezoid")]

    sky, fog = rgb(58, 48, 92), rgb(84, 72, 120)
    common = dict(temperature=0.2, downfall=0.0, precipitation=False, sky=sky, fog=fog, water=rgb(60, 60, 110),
                  water_fog=rgb(30, 30, 64), carvers=[])
    for nm in ("fulgor_ash_flats", "fulgor_basalt_plains", "fulgor_spires", "fulgor_fulgurite_fields"):
        biome(nm, feats=features(underground_ores=ores_f), **common)
    name("fulgor_ash_flats", "Ash Flats", "Llanuras de ceniza")
    name("fulgor_basalt_plains", "Basalt Plains", "Llanuras de basalto")
    name("fulgor_spires", "Basalt Spires", "Agujas de basalto")
    name("fulgor_fulgurite_fields", "Fulgurite Fields", "Campos de fulgurita")
    pts = [point("fulgor_spires", continentalness=[0.25, 2.0], weirdness=WIDE),
           point("fulgor_fulgurite_fields", continentalness=[-2.0, 0.25], weirdness=[0.12, 2.0]),
           point("fulgor_basalt_plains", continentalness=[-0.2, 0.25], weirdness=[-2.0, 0.12]),
           point("fulgor_ash_flats", continentalness=[-2.0, -0.2], weirdness=[-2.0, 0.12])]
    dimension(P, pts)
    dimension_type(P, effects="minecraft:overworld", min_y=0, height=256)
    dimension_physics(P, universal_drag=0.005, base_gravity=[0.0, -1.4, 0.0],
                      pressure_function=[{"altitude": 80.0, "value": 1.0, "slope": 0.0},
                                         {"altitude": 3000.0, "value": 0.0, "slope": 0.0}])

    data(f"universe_planets/{P}", {
        "name": P,
        "parent": "aurelia",
        "priority": 1000,
        "acceleration_at_surface": 1.4,
        "radius": 1.1e6,
        "position": {"type": "circular_orbit_period", "orbit_axis": {"x": 0.0, "y": 1.0, "z": 0.0},
                     "position_direction": {"x": 1.0, "y": 0.0, "z": 0.0}, "period_seconds": 90000.0},
        "rotation": {"type": "tidal_lock", "rotations_per_orbit": -1.0},
        "planet_extras": {"light_source_name": "amber"},
        "dimension_data": {
            "linked_dimension": rl(P), "allowed_transfer": "all", "dimension_transfer_height": 20000,
            "atmosphere_composition": {"21000": ["drowning"]},
            "render_universe_in_dimension": True, "dimension_day_time_controller_name": "amber",
            "apply_gravity_correction_to_entities_in_dimension": True, "entity_drag_multiplier": drag_curve(2500, 6000),
        },
        "planet_atmosphere": {"enabled": True, "radius": 1.12, "intensity": 0.8, "densityFalloff": 4.0,
                              "scatteringStrength": 0.08, "wavelengths": [520.0, 600.0, 430.0]},
        "planet_texture": {"type": "biome_sampler", "fallback_red": 110, "fallback_green": 104, "fallback_blue": 140,
                           "colors": [color(122, 116, 150, biomes=["fulgor_ash_flats"]),
                                      color(70, 74, 104, biomes=["fulgor_basalt_plains"]),
                                      color(48, 52, 78, biomes=["fulgor_spires"], priority=5),
                                      color(190, 184, 232, biomes=["fulgor_fulgurite_fields"])]},
    })
    LANG_EN[f"hologram.{NS}.body.{P}"] = "Fulgor"
    LANG_ES[f"hologram.{NS}.body.{P}"] = "Fulgor"
    LANG_EN[f"storm.{NS}.fulgor_storm"] = "Electric storm"
    LANG_ES[f"storm.{NS}.fulgor_storm"] = "Tormenta eléctrica"
    n = f"nav.{NS}."
    for field, (en, es) in {
            "type": ("Moon of Aurelia", "Luna de Aurelia"),
            "desc": ("A dark basalt moon under a violet sky, swept by electric storms that fuse its dust into glass.",
                     "Una luna de basalto oscuro bajo un cielo violeta, azotada por tormentas eléctricas que funden su polvo en vidrio."),
            "air": ("Not breathable", "No respirable"), "temp": ("Mild (12 °C)", "Templada (12 °C)"),
            "hazards": ("Electric storms (lightning)", "Tormentas eléctricas (rayos)"),
            "armor": ("A space suit; lightning rods", "Un traje espacial; pararrayos"),
            "resources": ("Huge amounts of copper, zinc and redstone; fulgurite",
                          "Muchísimo cobre, zinc y redstone; fulgurita")}.items():
        LANG_EN[f"{n}body.{P}.{field}"] = en
        LANG_ES[f"{n}body.{P}.{field}"] = es


def build_meta():
    # logo del mod (tools/designs/logo.py): pixel art de 512×512, sin difuminar en la lista de mods
    write_bytes("logo.png", open(os.path.join(DESIGNS, "logo.png"), "rb").read())
    write("META-INF/neoforge.mods.toml", f'''modLoader="javafml"
loaderVersion="[4,)"
license="GPL-3.0-or-later"

[[mixins]]
config="{NS}.mixins.json"

[[mods]]
modId="{NS}"
version="{MOD_VERSION}"
displayName="Cosmonautics: Planetary Expansion"
authors="Antme08"
logoFile="logo.png"
logoBlur=false
displayURL="https://github.com/Antme08s/planetary_expansion"
description=\'\'\'
Landable worlds for Create: Cosmonautics, each with its own dimension, terrain, weather, creatures and dangers:
Mars, Venus, Thalassa, Ignis and the Ice World around the Sun, plus Saturn (with rings) and its moon Titan,
with methane lakes and liquid methane as rocket fuel. Around Amber, a second star reached with the Stellar Warp
Drive: Cerulea with its moons Velo and Ferra, and the gas giant Aurelia with its stormy moon Fulgor.
Planet suits with upgrade templates (Impact, Glacial, Hazard, Sea Diver and Thermal), survival kits,
and ship machines: Warp Drive with Navigation Console, Gravity Generator, Inertial Dampener and the Methane
Thruster Mount.
\'\'\'

[[dependencies.{NS}]]
modId="neoforge"
type="required"
versionRange="[21.1.1,)"
ordering="NONE"
side="BOTH"

[[dependencies.{NS}]]
modId="minecraft"
type="required"
versionRange="[1.21.1]"
ordering="NONE"
side="BOTH"

[[dependencies.{NS}]]
modId="create"
type="required"
versionRange="[6.0,)"
ordering="AFTER"
side="BOTH"

[[dependencies.{NS}]]
modId="rocketnautics"
type="required"
versionRange="[0,)"
ordering="AFTER"
side="BOTH"

[[dependencies.{NS}]]
modId="sable"
type="required"
versionRange="[2.0.3,)"
ordering="AFTER"
side="BOTH"

# Obligatorio desde 0.7.2: minerales de Venus, láminas de plomo y protección contra la radiación de la Hazard Armor
[[dependencies.{NS}]]
modId="createnuclear"
type="required"
versionRange="[2.0,)"
ordering="AFTER"
side="BOTH"

# Obligatorio desde 0.7.24 (petición del usuario): difusores de oxígeno en planetas sin aire y presión del mar
# para la Sea Diver Armor
[[dependencies.{NS}]]
modId="create_submarine"
type="required"
versionRange="[3.4,)"
ordering="AFTER"
side="BOTH"
''')
    write(f"{NS}.mixins.json", {
        "required": True,
        "minVersion": "0.8",
        "package": "dev.antme08.planetaryexpansion.mixin",
        "compatibilityLevel": "JAVA_21",
        "plugin": "dev.antme08.planetaryexpansion.PlanetaryMixinPlugin",
        "mixins": ["CreateWindmillBearingMixin", "BearingContraptionSailMixin", "AsteroidSpawnerMixin", "GlobalSpacePhysicsMixin", "SableRemovalMassGuardMixin", "SablePhysicsRemovalMassGuardMixin", "VenusWaterMixin", "EntityGravityMixin", "DeepSpaceThrustMixin", "DiverPressureMixin", "DiverPressureAccessor", "ThalassaFluidPickerMixin", "SableSnatchPointerMixin", "BedBlockMixin", "SpaceBedNaturalMixin", "IceWorldAquiferMixin", "PlanetWeatherMixin", "RocketFuelPropertiesMixin", "DeepSpacePositionFreezeMixin"],
        "client": ["HologramTableRendererMixin", "HologramLineStripMixin", "JetpackLayerMixin", "AirlessStarsMixin", "SpaceStarsCullMixin", "UniverseSkyMixin", "PlanetSkyColorMixin", "UniverseUnderwaterMixin", "NightSunMixin", "StarTintMixin", "SaturnRingsMixin"],
        "injectors": {"defaultRequire": 1},
    })
    write("pack.mcmeta", {"pack": {"description": "Cosmonautics: Planetary Expansion", "pack_format": 34}})
    lang_dir = f"assets/{NS}/lang"
    write(f"{lang_dir}/en_us.json", dict(sorted(LANG_EN.items())))
    for loc in ("es_es", "es_mx", "es_ar", "es_cl", "es_ec", "es_uy", "es_ve"):
        write(f"{lang_dir}/{loc}.json", dict(sorted(LANG_ES.items())))


# --------------------------------------------------------------------------
# 1.0.4: items redibujados (tools/designs/items_v2.py + items_v2_draw.py). Se escriben encima de los de siempre:
# mismo nombre y tamaño (16x16), distinto sombreado y volumen. Los que no están en ningún lote conservan el original.
# --------------------------------------------------------------------------
def build_items_v2():
    sys.path.insert(0, DESIGNS)
    import io
    import items_v2_draw as IV2
    count = 0
    for batch in (IV2.BATCH1, IV2.BATCH2, IV2.BATCH3, IV2.BATCH4, IV2.BATCH5, IV2.BATCH6, IV2.BATCH7):
        for name, draw in batch.items():
            rel = f"assets/{NS}/textures/item/{name}.png"
            if not os.path.exists(os.path.join(OUT, rel)):
                raise SystemExit(f"items_v2: no existe la textura original de {name}")
            buf = io.BytesIO()
            draw().save(buf, format="PNG")
            write_bytes(rel, buf.getvalue())
            count += 1
    print(f"items_v2: {count} items redibujados")


# Trituracion de Create (create:crushing) de las menas del mod (1.0.4). Create solo tiene recetas para las menas de vanilla y de
# Cosmonautics: sin esto las menas nuevas solo se fundian. Se copian las proporciones de Create: menas "normales" como la
# mena de piedra de vanilla (subproducto: adoquin) y menas "deep_" como las de pizarra abisal (el doble, mas tiempo, subproducto:
# pizarra abisal adoquinada). Los resultados trituran a los mismos materiales que sueltan al picarse; los metales dan sus
# "crushed_raw_*" (que ya tienen lavado y fundicion en Create / Cosmonautics).
# nombre: (resultado, tiempo normal, cantidad normal, prob. extra normal, pepitas de experiencia,
#          tiempo deep, cantidad deep, prob. extra deep)
ORE_CRUSHING = {
    "coal":     ("minecraft:coal", 150, 1, 0.75, 1, 300, 2, 0.25),
    "iron":     ("create:crushed_raw_iron", 250, 1, 0.75, 1, 350, 2, 0.25),
    "copper":   ("create:crushed_raw_copper", 250, 5, 0.25, 1, 350, 7, 0.25),
    "gold":     ("create:crushed_raw_gold", 250, 1, 0.75, 2, 350, 2, 0.25),
    "zinc":     ("create:crushed_raw_zinc", 250, 1, 0.75, 1, 350, 2, 0.25),
    "redstone": ("minecraft:redstone", 250, 6, 0.5, 1, 350, 7, 0.5),
    "lapis":    ("minecraft:lapis_lazuli", 250, 10, 0.5, 1, 350, 12, 0.5),
    "diamond":  ("minecraft:diamond", 350, 1, 0.75, 1, 450, 2, 0.25),
    "emerald":  ("minecraft:emerald", 350, 1, 0.75, 1, 450, 2, 0.25),
    "titanium": ("rocketnautics:crushed_raw_titanium", 350, 1, 0.75, 1, 450, 2, 0.25),
    "crystal":  ("planetary_expansion:cerulean_crystal", 250, 3, 0.5, 1, 350, 4, 0.5),
    "sulfur":   ("planetary_expansion:venusian_sulfur", 250, 5, 0.5, 1, 350, 6, 0.5),
}


def build_ore_crushing():
    ores = sorted(f[:-5] for f in os.listdir(os.path.join(OUT, f"assets/{NS}/blockstates")) if f.endswith("_ore.json"))
    done = 0
    for n in ores:
        deep = n.startswith("deep_")
        kind = n[:-4].rsplit("_", 1)[1]
        if kind not in ORE_CRUSHING:
            raise SystemExit(f"ore_crushing: mena sin receta de trituracion: {n}")
        out, t, c, ex, xp, td, cd, exd = ORE_CRUSHING[kind]
        if deep:
            t, c, ex = td, cd, exd
            rock = "minecraft:cobbled_deepslate"
        else:
            rock = "minecraft:cobblestone"
        main = {"id": out} if c == 1 else {"count": c, "id": out}
        xp_r = {"chance": 0.75, "id": "create:experience_nugget"}
        if xp > 1:
            xp_r["count"] = xp
        data(f"recipe/crushing/{n}", {
            "type": "create:crushing", "ingredients": [{"item": rl(n)}], "processing_time": t,
            "results": [main, {"chance": ex, "id": out}, xp_r, {"chance": 0.125, "id": rock}]})
        done += 1
    print(f"ore_crushing: {done} menas con receta de trituracion")


# --------------------------------------------------------------------------
# 1.0.4: Congelador del tiempo espacial (freezer/TimeFreezer.java). Idea del usuario: con redstone la nave se queda quieta en
# su orbita (el reloj de la instancia del espacio profundo se para); el empuje sigue cambiando la orbita y la nave puede girar.
# --------------------------------------------------------------------------
def build_time_freezer():
    name = "time_freezer"
    src = os.path.join(DESIGNS, "time_freezer")
    for f, dest in (("side_off", "side_off"), ("side_on", "side_on"), ("top", "top")):
        write_bytes(f"assets/{NS}/textures/block/{name}_{dest}.png", open(os.path.join(src, f + ".png"), "rb").read())
    for state in ("off", "on"):
        write(f"assets/{NS}/models/block/{name}_{state}.json", {"parent": "minecraft:block/cube_column", "textures": {
            "end": rl(f"block/{name}_top"), "side": rl(f"block/{name}_side_{state}"), "particle": rl(f"block/{name}_top")}})
    write(f"assets/{NS}/models/item/{name}.json", {"parent": rl(f"block/{name}_off")})
    write(f"assets/{NS}/blockstates/{name}.json", {"variants": {
        f"active={a},powered={p}": {"model": rl(f"block/{name}_{'on' if a == 'true' else 'off'}")}
        for a in ("false", "true") for p in ("false", "true")}})
    data(f"loot_table/blocks/{name}", {"type": "minecraft:block", "pools": [{
        "rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": rl(name)}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}], "random_sequence": rl(f"blocks/{name}")})
    tag_add("data/minecraft/tags/block/mineable/pickaxe.json", rl(name))
    tag_add("data/minecraft/tags/block/needs_iron_tool.json", rl(name))
    tag_add("data/create/tags/block/wrench_pickup.json", rl(name))
    # receta: reloj en el centro, mecanismos de precision arriba y abajo, cristal de helio-3 a los lados (se consigue en la Luna)
    # y laminas de aleacion de titanio en las esquinas
    data(f"recipe/{name}", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["APA", "HCH", "APA"],
        "key": {"A": {"tag": "c:plates/titanium_alloy"}, "P": {"item": "create:precision_mechanism"},
                "H": {"item": rl("helium3_crystal")}, "C": {"item": "minecraft:clock"}},
        "result": {"id": rl(name), "count": 1}})
    LANG_EN[f"block.{NS}.{name}"] = "Space Time Freezer"
    LANG_ES[f"block.{NS}.{name}"] = "Congelador del tiempo espacial"
    LANG_EN[f"message.{NS}.freezer_on"] = "Time frozen: the orbit holds still. Thrust still changes it."
    LANG_ES[f"message.{NS}.freezer_on"] = "Tiempo congelado: la órbita no avanza. El empuje sí la cambia."
    LANG_EN[f"message.{NS}.freezer_off"] = "Off: needs a redstone signal"
    LANG_ES[f"message.{NS}.freezer_off"] = "Apagado: necesita señal de redstone"
    LANG_EN[f"message.{NS}.freezer_no_space"] = "Only works in deep space"
    LANG_ES[f"message.{NS}.freezer_no_space"] = "Solo funciona en el espacio profundo"


if __name__ == "__main__":
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    build_mars()
    build_ice_world()
    build_ignis()
    build_thalassa()
    build_venus()
    build_venus_hazard_data()
    build_moon_fix()
    build_cerulea()          # 1.0.3: antes de no_earth_mobs (la etiqueta de biomas del mod)
    build_velo()             # 1.0.3: luna de cristal de Cerúlea
    build_ferra()            # 1.0.3: luna de óxido de Cerúlea
    build_saturn()           # 1.0.4: Saturno con anillos
    build_titan()            # 1.0.4: su luna Titán
    build_aurelia()          # 1.0.4: gigante gaseoso de Ámbar
    build_fulgor()           # 1.0.4: su luna de tormentas Fulgor
    build_methane_engine()   # 1.0.4: soporte de motor de metano (receta con la palanca de Simulated)
    build_ore_crushing()     # 1.0.4: trituracion de Create para las menas del mod
    build_time_freezer()     # 1.0.4: congelador del tiempo espacial
    build_no_earth_mobs()
    build_weather_data()
    build_armor()
    build_armor_recipes()
    build_sea_diver_texts()
    build_new_suit_texts()
    build_suit_progression()
    build_survival_kits()
    build_gravity_generator()
    build_inertial_dampener()
    build_warp()
    build_advancements()
    build_structures()
    build_thalassa_creatures()
    build_ignis_creatures()
    build_cerulea_creatures()   # 1.0.3
    build_amber_system()
    build_items_v2()            # 1.0.4: items redibujados
    build_meta()
    n = sum(len(f) for _, _, f in os.walk(OUT))
    print(f"Generados {n} archivos en {OUT}")
