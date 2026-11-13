#!/usr/bin/env python3
"""Valida referencias cruzadas de los datos generados.

Uso: python3 -I tools/validate.py <resources> <vanilla_data_root> <cosmonautics_jar_extraido>
"""
import json
import os
import re
import sys

RES, VAN, COSMO = sys.argv[1:4]
NS = "planetary_expansion"
errors = []


def exists(kind, rid):
    ns, path = rid.split(":", 1)
    roots = {"minecraft": VAN, "rocketnautics": COSMO, NS: RES}
    root = roots.get(ns)
    if root is None:
        return False
    return os.path.isfile(os.path.join(root, "data", ns, kind, path + ".json"))


def load(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)


ours = os.path.join(RES, "data", NS)
for dp, _, files in os.walk(ours):
    for fn in files:
        p = os.path.join(dp, fn)
        if fn.endswith(".nbt"):   # plantillas de estructura (binarias)
            continue
        try:
            load(p)
        except Exception as e:  # noqa
            errors.append(f"JSON inválido {p}: {e}")

# 1) funciones de densidad / ruidos referenciados
for kind_dir in ("worldgen/density_function", "worldgen/noise_settings"):
    for dp, _, files in os.walk(os.path.join(ours, kind_dir)):
        for fn in files:
            p = os.path.join(dp, fn)
            obj = load(p)

            def walk(o, key=None):
                if isinstance(o, dict):
                    shift = o.get("type") in ("minecraft:shift_a", "minecraft:shift_b", "minecraft:shift")
                    for k, v in o.items():
                        if k in ("surface_rule", "default_block", "default_fluid", "result_state", "spawn_target"):
                            continue
                        walk(v, "noise" if (shift and k == "argument") else k)
                elif isinstance(o, list):
                    for x in o:
                        walk(x, key)
                elif isinstance(o, str) and ":" in o and key != "type":
                    if key == "noise":
                        if not exists("worldgen/noise", o):
                            errors.append(f"{p}: ruido inexistente {o}")
                    elif not exists("worldgen/density_function", o):
                        errors.append(f"{p}: función de densidad inexistente {o}")
                    if o.startswith("minecraft:overworld"):
                        errors.append(f"{p}: sigue apuntando a vanilla {o}")
            walk(obj)
            if kind_dir.endswith("noise_settings"):
                for n in re.findall(r'"noise": "([^"]+)"', json.dumps(obj["surface_rule"])):
                    if not exists("worldgen/noise", n):
                        errors.append(f"{p}: ruido de superficie inexistente {n}")
                for b in re.findall(r'"biome_is": \[([^\]]+)\]', json.dumps(obj["surface_rule"])):
                    for bid in re.findall(r'"([^"]+)"', b):
                        if not exists("worldgen/biome", bid):
                            errors.append(f"{p}: bioma inexistente en regla {bid}")

# 2) biomas: features y carvers
for fn in os.listdir(os.path.join(ours, "worldgen/biome")):
    b = load(os.path.join(ours, "worldgen/biome", fn))
    for step in b["features"]:
        for f in step:
            if not exists("worldgen/placed_feature", f):
                errors.append(f"bioma {fn}: placed_feature inexistente {f}")
    for c in b["carvers"]["air"]:
        if not exists("worldgen/configured_carver", c):
            errors.append(f"bioma {fn}: carver inexistente {c}")
    if len(b["features"]) != 11:
        errors.append(f"bioma {fn}: {len(b['features'])} pasos de features")

# 3) dimensiones: biomas, tipo, settings, orden de features
for fn in os.listdir(os.path.join(ours, "dimension")):
    d = load(os.path.join(ours, "dimension", fn))
    if not exists("dimension_type", d["type"]):
        errors.append(f"dim {fn}: tipo inexistente {d['type']}")
    if not exists("worldgen/noise_settings", d["generator"]["settings"]):
        errors.append(f"dim {fn}: noise_settings inexistente")
    order = {}
    src = d["generator"]["biome_source"]
    pts = src["biomes"] if "biomes" in src else [{"biome": src["biome"]}]
    for pt in pts:
        bid = pt["biome"]
        if not exists("worldgen/biome", bid):
            errors.append(f"dim {fn}: bioma inexistente {bid}")
            continue
        feats = load(os.path.join(ours, "worldgen/biome", bid.split(":")[1] + ".json"))["features"]
        for si, step in enumerate(feats):
            for i, a in enumerate(step):
                for bb in step[i + 1:]:
                    if order.get((si, bb, a)):
                        errors.append(f"dim {fn}: ciclo de orden {a} / {bb}")
                    order[(si, a, bb)] = True

# 4) planetas -> dimensiones
for fn in os.listdir(os.path.join(ours, "universe_planets")):
    u = load(os.path.join(ours, "universe_planets", fn))
    dd = u.get("dimension_data", {})
    if dd.get("linked_dimension") and not exists("dimension", dd["linked_dimension"]):
        errors.append(f"planeta {fn}: dimensión inexistente {dd['linked_dimension']}")
    tex = u.get("planet_texture", {})
    for c in tex.get("colors", []):
        for bid in c.get("matching_biomes", []):
            if not exists("worldgen/biome", bid):
                errors.append(f"planeta {fn}: bioma de textura inexistente {bid}")

# 5) dimension_physics
for fn in os.listdir(os.path.join(ours, "dimension_physics")):
    ph = load(os.path.join(ours, "dimension_physics", fn))
    if not exists("dimension", ph["dimension"]):
        errors.append(f"física {fn}: dimensión inexistente {ph['dimension']}")

# 6) etiqueta de biomas del mod
tag = load(os.path.join(ours, "tags/worldgen/biome/planet_biomes.json"))
for bid in tag["values"]:
    if not exists("worldgen/biome", bid):
        errors.append(f"etiqueta planet_biomes: bioma inexistente {bid}")
allb = {f[:-5] for f in os.listdir(os.path.join(ours, "worldgen/biome"))}
if {b.split(":")[1] for b in tag["values"]} != allb:
    errors.append("etiqueta planet_biomes no cubre todos los biomas del mod")
if any(not b.startswith(NS + ":") for b in tag["values"]):
    errors.append("etiqueta planet_biomes incluye biomas ajenos")

# 7) C2ME (aceleración OpenCL) crashea con cache_2d dentro de otro cache_2d ("Cannot register more caches", 0.7.7)
def _nested_cache(n, inside=False):
    if isinstance(n, dict):
        if n.get("type") == "minecraft:cache_2d":
            return inside or _nested_cache(n.get("argument"), True)
        return any(_nested_cache(v, inside) for v in n.values())
    if isinstance(n, list):
        return any(_nested_cache(v, inside) for v in n)
    return False
# 8) los rangos de clima de multi_noise solo admiten valores entre -2 y 2 (si no, falla al crear el mundo, 0.7.9)
for fn in os.listdir(os.path.join(ours, "dimension")):
    gen = load(os.path.join(ours, "dimension", fn)).get("generator", {})
    for b in gen.get("biome_source", {}).get("biomes", []) if isinstance(gen.get("biome_source"), dict) else []:
        for k, v in b["parameters"].items():
            vals = v if isinstance(v, list) else [v]
            if any(abs(x) > 2.0 for x in vals):
                errors.append(f"dimensión {fn}: {b['biome']} {k}={v} fuera de [-2, 2]")
for fn in os.listdir(os.path.join(ours, "worldgen/noise_settings")):
    if _nested_cache(load(os.path.join(ours, "worldgen/noise_settings", fn))):
        errors.append(f"noise_settings {fn}: cache_2d anidado (crashea con C2ME OpenCL)")

# 9) límites de los modificadores de colocación (fallan al crear el mundo: environment_scan max_steps 1-32, 0.7.26)
for root, _, files in os.walk(os.path.join(ours, "worldgen/placed_feature")):
    for fn in files:
        for m in load(os.path.join(root, fn)).get("placement", []):
            if m.get("type") == "minecraft:environment_scan" and not 1 <= m.get("max_steps", 0) <= 32:
                errors.append(f"placed_feature {fn}: environment_scan max_steps={m.get('max_steps')} fuera de [1, 32]")
            if m.get("type") == "minecraft:random_offset":
                for k in ("xz_spread", "y_spread"):
                    v = m.get(k)
                    if isinstance(v, int) and not -16 <= v <= 16:
                        errors.append(f"placed_feature {fn}: {k}={v} fuera de [-16, 16]")

print("\n".join(errors) if errors else "OK: sin referencias rotas")
sys.exit(1 if errors else 0)
