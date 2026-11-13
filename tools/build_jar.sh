#!/usr/bin/env bash
# Compila el código Java y empaqueta el jar del mod.
#
# Uso: tools/build_jar.sh <carpeta_libs> <cosmonautics.jar> <create_submarine.jar> <sable.jar> <sable-companion.jar>
#        <create.jar> <carpeta_con_jars_anidados_de_create>
#   (sable-companion está dentro de sable.jar en META-INF/jarjar; flywheel/ponder/registrate dentro de create.jar)
#   <carpeta_libs> = la carpeta "libraries" de la instalación de Minecraft (CurseForge),
#                    de donde se toman Minecraft (nombres de Mojang), NeoForge, FML, bus y slf4j.
# Requisitos: JDK 21 (javac) y zip. Antes hay que ejecutar tools/generate.py.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIBS="$1"; COSMO="$2"; SUBM="$3"; SABLE="$4"; SABLE_COMPANION="$5"; CREATE="$6"; CREATE_NESTED="$7"
VERSION=$(grep -oP '^MOD_VERSION = "\K[^"]+' "$ROOT/tools/generate.py")

CP="$LIBS/net/neoforged/neoforge/21.1.262/neoforge-21.1.262-client.jar"
CP="$CP:$LIBS/net/minecraft/client/1.21.1-20240808.144430/client-1.21.1-20240808.144430-srg.jar"
CP="$CP:$LIBS/net/neoforged/neoforge/21.1.262/neoforge-21.1.262-universal.jar"
CP="$CP:$LIBS/net/neoforged/bus/8.0.5/bus-8.0.5.jar"
CP="$CP:$LIBS/net/neoforged/fancymodloader/loader/4.0.45/loader-4.0.45.jar"
CP="$CP:$LIBS/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar"
CP="$CP:$LIBS/org/joml/joml/1.10.8/joml-1.10.8.jar"
CP="$CP:$LIBS/com/electronwill/night-config/core/3.8.3/core-3.8.3.jar"
CP="$CP:$LIBS/com/electronwill/night-config/toml/3.8.3/toml-3.8.3.jar"
CP="$CP:$LIBS/com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar"
CP="$CP:$LIBS/io/netty/netty-buffer/4.1.97.Final/netty-buffer-4.1.97.Final.jar"
CP="$CP:$LIBS/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar"
CP="$CP:$LIBS/com/mojang/datafixerupper/8.0.16/datafixerupper-8.0.16.jar"
CP="$CP:$LIBS/it/unimi/dsi/fastutil/8.5.9/fastutil-8.5.9.jar"
CP="$CP:$LIBS/com/google/guava/guava/33.6.0-jre/guava-33.6.0-jre.jar"
CP="$CP:$LIBS/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar"
CP="$CP:$LIBS/com/mojang/logging/1.6.11/logging-1.6.11.jar"
CP="$CP:$LIBS/net/neoforged/mergetool/2.0.0/mergetool-2.0.0-api.jar"
CP="$CP:$LIBS/net/fabricmc/sponge-mixin/0.15.5+mixin.0.8.7/sponge-mixin-0.15.5+mixin.0.8.7.jar"
CP="$CP:$COSMO:$SUBM:$SABLE:$SABLE_COMPANION:$CREATE"
for j in "$CREATE_NESTED"/*.jar; do CP="$CP:$j"; done

OUT="$ROOT/build"
rm -rf "$OUT/classes" "$OUT/stubs" && mkdir -p "$OUT/classes" "$OUT/stubs"
mkdir -p "$OUT/noproc"
# stubs de compilación (no van al jar): clases que existen en el juego pero no en libraries
javac --release 21 -proc:none -processorpath "$OUT/noproc" -cp "$CP" -d "$OUT/stubs" $(find "$ROOT/tools/stubs" -name '*.java')
CP="$CP:$OUT/stubs"
javac --release 21 -Xlint:deprecation -proc:none -processorpath "$OUT/noproc" -encoding UTF-8 -cp "$CP" -d "$OUT/classes" \
      $(find "$ROOT/src/main/java" -name '*.java')

printf 'Manifest-Version: 1.0\nImplementation-Title: planetary_expansion\nImplementation-Version: %s\n\n' "$VERSION" \
      > "$ROOT/src/main/resources/META-INF/MANIFEST.MF"
JAR="$OUT/planetary_expansion-$VERSION.jar"
rm -f "$OUT"/*.jar
(cd "$ROOT/src/main/resources" && zip -q -r -X "$JAR" META-INF/MANIFEST.MF . -x '.*')
(cd "$OUT/classes" && zip -q -r -X "$JAR" .)
echo "$JAR"
