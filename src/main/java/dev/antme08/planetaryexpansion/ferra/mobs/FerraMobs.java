package dev.antme08.planetaryexpansion.ferra.mobs;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Criaturas hostiles de Ferra (1.0.3, plan del usuario): el gusano de óxido (bajo el polvo de las llanuras y las charcas,
 * sale donde nota pasos) y el rastreador ciego (manadas en las chimeneas de hierro y los cañones, cazan por el ruido como
 * el Warden). Modelos: tools/designs/ferra_mobs.py.
 */
public final class FerraMobs {
    private FerraMobs() {}

    private static final String MODID = PlanetaryExpansion.MODID;
    public static final String FERRA = "planetary_expansion:ferra";
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<RustWorm>> RUST_WORM = ENTITIES.register("rust_worm",
            () -> EntityType.Builder.of(RustWorm::new, MobCategory.MONSTER).sized(1.2F, 2.8F).clientTrackingRange(10).build("rust_worm"));
    public static final DeferredHolder<EntityType<?>, EntityType<BlindStalker>> BLIND_STALKER = ENTITIES.register("blind_stalker",
            () -> EntityType.Builder.of(BlindStalker::new, MobCategory.MONSTER).sized(0.8F, 1.2F).clientTrackingRange(10).build("blind_stalker"));

    public static final DeferredItem<DeferredSpawnEggItem> RUST_WORM_EGG = ITEMS.register("rust_worm_spawn_egg",
            () -> new DeferredSpawnEggItem(RUST_WORM, 0x96502c, 0xffa85a, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> BLIND_STALKER_EGG = ITEMS.register("blind_stalker_spawn_egg",
            () -> new DeferredSpawnEggItem(BLIND_STALKER, 0x605652, 0xffb060, new Item.Properties()));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(FerraMobs::attributes);
        modBus.addListener(FerraMobs::spawnPlacements);
        modBus.addListener(FerraMobs::creativeTab);
        NeoForge.EVENT_BUS.addListener(FerraMobs::onBreak);
        NeoForge.EVENT_BUS.addListener(FerraMobs::onProjectile);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(RUST_WORM.get(), RustWorm.attributes().build());
        event.put(BLIND_STALKER.get(), BlindStalker.attributes().build());
    }

    static boolean inFerra(LevelAccessor level) {
        return level instanceof ServerLevelAccessor s && s.getLevel().dimension().location().toString().equals(FERRA);
    }

    static boolean inFerra(Level level) {
        return level.dimension().location().toString().equals(FERRA);
    }

    /** Ferra es hostil: salen de día y de noche (no dependen de la luz), pero con límites para que no se amontonen. */
    private static void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        var op = RegisterSpawnPlacementsEvent.Operation.REPLACE;
        // gusano: enemigo normal, no común; nunca otro a menos de 64 bloques
        event.register(RUST_WORM.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, random) -> inFerra(level) && level.getBlockState(pos.below()).isSolid()
                        && (reason != MobSpawnType.NATURAL || (random.nextInt(3) == 0
                        && level.getEntitiesOfClass(RustWorm.class, new AABB(pos).inflate(64)).isEmpty())), op);
        // rastreadores: en manada; como mucho unos 8 en 48 bloques
        event.register(BLIND_STALKER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, random) -> inFerra(level) && level.getBlockState(pos.below()).isSolid()
                        && (reason != MobSpawnType.NATURAL
                        || level.getEntitiesOfClass(BlindStalker.class, new AABB(pos).inflate(48)).size() < 8), op);
    }

    // ------------------------------------------------------------------ ruido

    /** Ruidos recientes en Ferra (bloques rotos, impactos de proyectiles): los rastreadores sin presa van a mirar. */
    public record Noise(Vec3 pos, long time) {}

    static final Deque<Noise> NOISES = new ArrayDeque<>();

    static synchronized void noise(Vec3 pos, long time) {
        NOISES.addFirst(new Noise(pos, time));
        while (NOISES.size() > 32) NOISES.removeLast();
    }

    /** El ruido más reciente (de menos de 3 s) a menos de {@code range} bloques, o null. */
    static synchronized Noise nearestNoise(Vec3 from, double range, long now) {
        for (Noise n : NOISES) {
            if (now - n.time() > 60) break;
            if (n.pos().distanceToSqr(from) < range * range) return n;
        }
        return null;
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && inFerra(level)) {
            BlockPos p = event.getPos();
            noise(Vec3.atCenterOf(p), level.getGameTime());
            dev.antme08.planetaryexpansion.creatures.AbyssalStalker.markLoud(event.getPlayer(), level.getGameTime());
        }
    }

    /** Una bola de nieve o una flecha que cae lejos hace ruido: sirve para distraer a la manada. */
    private static void onProjectile(ProjectileImpactEvent event) {
        Level level = event.getProjectile().level();
        if (!level.isClientSide && inFerra(level)) noise(event.getRayTraceResult().getLocation(), level.getGameTime());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        boolean eggs = tab.getNamespace().equals("minecraft") && tab.getPath().equals("spawn_eggs");
        boolean world = tab.getNamespace().equals("rocketnautics") && tab.getPath().equals("world_tab");
        if (!eggs && !world) return;
        try {
            for (DeferredItem<?> egg : List.of(RUST_WORM_EGG, BLIND_STALKER_EGG))
                event.accept(egg.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } catch (Exception e) {
            PlanetaryExpansion.LOGGER.error("[Ferra] No se pudieron añadir los huevos a la pestaña {}", tab, e);
        }
    }
}
