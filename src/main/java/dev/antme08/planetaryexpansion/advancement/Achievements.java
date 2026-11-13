package dev.antme08.planetaryexpansion.advancement;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.joml.Vector3d;

/**
 * Logros de la pestaña "Planetary Expansion" (0.7.41) que no puede detectar Minecraft solo: los criterios son
 * {@code minecraft:impossible} en los JSON (tools/generate.py, build_advancements) y aquí se conceden.
 */
public final class Achievements {
    private Achievements() {}

    private static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores"));

    public static void award(ServerPlayer player, String id, String criterion) {
        if (player == null || player.getServer() == null) return;
        try {
            AdvancementHolder holder = player.getServer().getAdvancements()
                    .get(ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "space/" + id));
            if (holder != null) player.getAdvancements().award(holder, criterion);
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.debug("[Logros] No se pudo dar {} a {}", id, player.getScoreboardName(), t);
        }
    }

    /** A todos los jugadores de la dimensión. */
    public static void awardAll(ServerLevel level, String id, String criterion) {
        for (ServerPlayer p : level.players()) if (p.isAlive()) award(p, id, criterion);
    }

    /** A los jugadores a menos de {@code radius} bloques de un punto (coordenadas del mundo). */
    public static void awardNear(ServerLevel level, Vector3d at, double radius, String id, String criterion) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at.x, at.y, at.z) < radius * radius) award(p, id, criterion);
        }
    }

    /** "Rich Veins": minar un mineral en otro planeta (no en la Tierra ni en el espacio). */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ResourceLocation dim = level.dimension().location();
        boolean planet = dim.getNamespace().equals(PlanetaryExpansion.MODID) || dim.toString().equals("rocketnautics:moon");
        if (planet && event.getState().is(ORES)) award(player, "rich_veins", "mined");
    }
}
