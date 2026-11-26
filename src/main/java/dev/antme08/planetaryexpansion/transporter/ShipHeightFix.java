package dev.antme08.planetaryexpansion.transporter;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.egg.registries.BlockEntityRegistry;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 1.0.4: las naves perdían los bloques con datos (copycats, consolas, propulsores…) y luego desaparecían al entrar o
 * salir de Thalassa.
 * <p>
 * Cuando una nave cambia de dimensión, Cosmonautics (Dimensional Sable, {@code dev.egg.SubLevelTemplate}) guarda sus
 * secciones por índice (contando desde el fondo del mundo) y sus bloques con datos relativos a (centro del plot + fondo
 * del mundo). El centro del plot es la mitad de la altura del mundo, así que si las dos dimensiones no tienen la misma
 * mitad (Thalassa va de −320 a 256: mitad −32; el espacio, la Tierra y los demás planetas: mitad 128) los bloques caen
 * en una altura y sus datos en otra: los datos no encuentran su bloque ("Invalid block entity … got air") y se pierden.
 * <p>
 * Además, con esas cuentas una nave que sale de Thalassa quedaría por encima del techo del espacio y una que entra, por
 * debajo del fondo de Thalassa.
 * <p>
 * Arreglo: al guardar se apunta el fondo y el centro del origen; al cargar se mueven las secciones, se corrigen las
 * alturas de los bloques con datos y el desplazamiento que Cosmonautics usa para las entidades y referencias, para que
 * la nave quede a la misma altura respecto al centro del plot, como cuando las dos dimensiones son iguales.
 */
public final class ShipHeightFix {
    private ShipHeightFix() {}

    private static final String SRC_MIN = "planetary_expansion:src_min_y";
    private static final String SRC_CENTER = "planetary_expansion:src_center_y";

    public static void recordSource(ServerLevelPlot plot, CompoundTag tag) {
        if (plot == null || tag == null) return;
        try {
            Level level = plot.getSubLevel().getLevel();
            tag.putInt(SRC_MIN, level.getMinBuildHeight());
            tag.putInt(SRC_CENTER, plot.getCenterBlock().getY());
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.warn("[Naves] No se pudo apuntar la altura de origen de la nave", t);
        }
    }

    public static void align(ServerLevelPlot plot, CompoundTag tag, BlockEntityRegistry.MoveInfo info) {
        if (plot == null || tag == null) return;
        remapDescents(info);
        if (!tag.contains(SRC_MIN) || !tag.contains(SRC_CENTER)) return;
        UUID oldId = tag.hasUUID("SubLevelID") ? tag.getUUID("SubLevelID") : null;
        int srcMin = tag.getInt(SRC_MIN), srcCenter = tag.getInt(SRC_CENTER);
        tag.remove(SRC_MIN);
        tag.remove(SRC_CENTER);
        try {
            Level level = plot.getSubLevel().getLevel();
            int dstMin = level.getMinBuildHeight(), dstCenter = plot.getCenterBlock().getY();
            int sections = level.getSectionsCount();
            // misma altura respecto al centro: y' − centroDestino = y − centroOrigen
            int shift = Math.round(((srcMin - srcCenter) - (dstMin - dstCenter)) / 16.0F);   // en secciones
            int beDelta = srcCenter - dstCenter + 16 * shift;                                  // corrección de los datos
            if (shift == 0 && beDelta == 0) return;
            CompoundTag chunks = tag.getCompound("chunks");
            int dropped = 0;
            for (String key : chunks.getAllKeys()) {
                CompoundTag chunk = chunks.getCompound(key);
                if (chunk.contains("sections", Tag.TAG_COMPOUND)) {
                    CompoundTag old = chunk.getCompound("sections");
                    CompoundTag moved = new CompoundTag();
                    for (String sk : old.getAllKeys()) {
                        int idx;
                        try {
                            idx = Integer.parseInt(sk);
                        } catch (NumberFormatException e) {
                            moved.put(sk, old.get(sk));
                            continue;
                        }
                        int to = idx + shift;
                        if (to < 0 || to >= sections) {
                            dropped++;
                            continue;
                        }
                        moved.put(String.valueOf(to), old.get(sk));
                    }
                    chunk.put("sections", moved);
                }
                if (beDelta != 0 && chunk.contains("block_entities", Tag.TAG_LIST)) {
                    ListTag list = chunk.getList("block_entities", Tag.TAG_COMPOUND);
                    for (int i = 0; i < list.size(); i++) {
                        CompoundTag be = list.getCompound(i);
                        be.putInt("y", be.getInt("y") + beDelta);
                    }
                }
            }
            // lo que Cosmonautics mueve con la nave (entidades dentro del plot, referencias de los bloques con datos) usa
            // este desplazamiento: se corrige igual que los bloques
            if (info != null && oldId != null && beDelta != 0) {
                var map = info.oldToNewSubLevelMap();
                var pair = map.get(oldId);
                if (pair != null && pair.second() != null)
                    map.put(oldId, dev.egg.DimensionalSable.Pair.of(pair.first(), pair.second().offset(0, beDelta, 0)));
            }
            PlanetaryExpansion.LOGGER.info("[Naves] Nave pasada a {}: alturas alineadas ({} secciones; datos {} bloques){}",
                    level.dimension().location(), shift, beDelta, dropped > 0 ? " — " + dropped + " secciones fuera del mundo" : "");
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.error("[Naves] No se pudieron alinear las alturas de la nave", t);
        }
    }

    /** La nave cambia de id al pasar de dimensión: el transportador sigue a la copia nueva. */
    private static void remapDescents(BlockEntityRegistry.MoveInfo info) {
        if (info == null) return;
        try {
            List<UUID[]> pairs = new ArrayList<>();
            info.oldToNewSubLevelMap().forEach((oldId, p) -> {
                if (p != null && p.first() != null) pairs.add(new UUID[]{oldId, p.first()});
            });
            for (UUID[] p : pairs) SpaceLaunch.renamed(p[0], p[1]);
        } catch (Throwable ignored) {
        }
    }
}
