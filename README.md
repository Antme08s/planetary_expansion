<p align="center"><img src="docs/logo.png" width="160" alt="Planetary Expansion logo"></p>

# Cosmonautics: Planetary Expansion

An add-on for **Create: Cosmonautics** (Minecraft 1.21.1, NeoForge) that adds landable worlds, each with its own
dimension, terrain, weather, creatures and dangers, plus planet suits and ship machines (warp drive and navigation
console, gravity generator, inertial dampener).

- **Sol system:** Mars, Venus, Thalassa, Ignis and the Ice World.
- **Amber system** (a second star, reached with the stellar warp drive): **Cerulea**, an alien green-and-blue world
  with its own plants, trees and creatures, and its two moons: **Velo** (white dust and sky-blue crystals) and
  **Ferra** (a hostile rust moon with dust storms, rust worms and packs of blind stalkers).

*Complemento de Create: Cosmonautics que añade planetas aterrizables.*

## Requirements (to play)
- Minecraft 1.21.1 + NeoForge 21.1
- Create 6.0+, Create: Cosmonautics, Sable
- Create: Nuclear, Create: Deep Seas

## Repository layout
| Path | What it is |
|---|---|
| `src/main/java` | Java source (package `dev.antme08.planetaryexpansion`) |
| `src/main/resources` | Generated data and assets (output of `tools/generate.py`; committed for reference) |
| `tools/generate.py` | Generates every JSON/texture/model in `src/main/resources` (dimensions, worldgen, recipes, loot, lang…) |
| `tools/validate.py` | Checks cross-references in the generated data |
| `tools/build_jar.sh` | Compiles the Java code and packages the jar (no Gradle) |
| `tools/designs/` | Model/texture/sound design scripts and the Blockbench models (`*.bbmodel`) |
| `tools/stubs/` | Compile-only stubs for classes that exist at runtime but not in the `libraries` folder (not packaged) |
| `tools/structures/` | Structure templates and the scripts that prepare them |

## Building
You need **Python 3.11+** (with `Pillow` and `numpy` for the design scripts), **JDK 21** and `zip`.

1. **Vanilla data:** extract `data/` from the Minecraft 1.21.1 client jar into a folder (e.g. `vanilla/data/...`).
2. **Generate the resources:**
   ```bash
   rm -rf src/main/resources
   python3 -I tools/generate.py <vanilla_folder> src/main/resources
   python3 -I tools/validate.py src/main/resources <vanilla_folder> <cosmonautics_jar_extracted>
   ```
3. **Compile and package** (the `libraries` folder of a NeoForge 1.21.1 install, e.g. a CurseForge instance, provides
   Minecraft, NeoForge and their libraries; the mod jars are the dependencies themselves):
   ```bash
   bash tools/build_jar.sh <libraries> <cosmonautics.jar> <create_submarine.jar> <sable.jar> \
        <sable-companion.jar> <create.jar> <folder_with_create_nested_jars>
   ```
   `sable-companion` is inside `sable.jar` (`META-INF/jarjar`); Flywheel, Ponder and Registrate are inside
   `create.jar`. The result is `build/planetary_expansion-<version>.jar`.

The version number lives in `tools/generate.py` (`MOD_VERSION`).

## Shaders
The mod works with shaders. The development setup used a personally patched copy of Complementary Reimagined;
**that patch and the patched shader are not included**, because Complementary's license does not allow redistribution.

## License
[GPL-3.0-or-later](LICENSE). Create: Cosmonautics is GPL-3.0; this add-on extends its classes and uses mixins.
Not affiliated with the Create, Cosmonautics, Sable, Create: Nuclear or Create: Deep Seas teams.
Dependencies are **not** bundled: download them from their official pages.

Author: Antme08
