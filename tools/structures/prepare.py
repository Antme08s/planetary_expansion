"""Prepara las estructuras que construye el usuario (bloque de estructura, carpeta raw/) para el mod.

- El aire por encima de la losa de la base se quita (en una plantilla, lo que no está en la lista no se coloca, así
  que el terreno se conserva: sobre una ladera, la arena natural tapa parte de la estructura).
- El aire dentro de la losa (cráteres, huecos) se queda: la losa va bajo tierra y excava el cráter.
- A los cofres se les pone su tabla de botín.

Uso: python3 tools/structures/prepare.py
"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import nbt

HERE = os.path.dirname(os.path.abspath(__file__))
NS = "planetary_expansion"

# nombre en raw/ -> (destino, capas de la losa, tabla de botín de los cofres)
STRUCTURES = {
    "mars_meteorite_1": ("mars/meteorite_1", 3, None),
    "mars_meteorite_2": ("mars/meteorite_2", 3, None),
    "mars_meteorite_3": ("mars/meteorite_3", 3, None),
    "mars_old_structure_1": ("mars/old_structure_1", 3, f"{NS}:chests/mars_old_structure_1"),
    "mars_old_structure_2": ("mars/old_structure_2", 3, None),
    "mars_old_structure_3": ("mars/old_structure_3", 3, f"{NS}:chests/mars_old_structure_3"),
}


def main():
    for src, (dst, slab, loot) in STRUCTURES.items():
        root = nbt.load(os.path.join(HERE, "raw", src + ".nbt"))
        palette = root["palette"].value[1]
        names = [p.value["Name"].value for p in palette]
        blocks = root["blocks"].value
        kept, removed, chests = [], 0, 0
        for b in blocks[1]:
            st = b.value["state"].value
            y = b.value["pos"].value[1][1].value
            name = names[st]
            if name == "minecraft:air" and y >= slab:
                removed += 1
                continue
            if loot and name in ("minecraft:chest", "minecraft:barrel", "minecraft:trapped_chest"):
                data = b.value.get("nbt")
                comp = data.value if data else {}
                comp.pop("Items", None)
                comp["LootTable"] = nbt.Tag(nbt.STRING, loot)
                if data is None: b.value["nbt"] = nbt.Tag(nbt.COMPOUND, comp)
                chests += 1
            kept.append(b)
        root["blocks"] = nbt.Tag(nbt.LIST, (nbt.COMPOUND, kept))
        out = os.path.join(HERE, dst + ".nbt")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        nbt.save(out, root)
        size = [t.value for t in root["size"].value[1]]
        print(f"{src} -> {dst}: {size}, {len(kept)} bloques, {removed} de aire quitados, {chests} cofres con botín")


if __name__ == "__main__":
    main()
