package dev.antme08.planetaryexpansion.creatures;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Criaturas de Ignis (0.7.64, idea del usuario): caracol de magma y caracol titán, con sus objetos (concha ígnea,
 * núcleo de magma, baba de magma y lámpara de magma). Salen en Ignis solo donde hay mucha lava cerca: orillas del mar
 * de lava, lagos y ríos. El núcleo de magma sirve de combustible sobrecalentado para el blaze burner de Create
 * (data map de Create, en generate.py).
 */
public final class IgnisCreatures {
    private IgnisCreatures() {}

    private static final String MODID = PlanetaryExpansion.MODID;
    public static final String IGNIS = "planetary_expansion:ignis";
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<MagmaSnail>> MAGMA_SNAIL = ENTITIES.register("magma_snail",
            () -> EntityType.Builder.of(MagmaSnail::new, MobCategory.CREATURE).sized(0.7F, 0.9F).fireImmune()
                    .clientTrackingRange(8).build("magma_snail"));
    public static final DeferredHolder<EntityType<?>, EntityType<MagmaTitan>> MAGMA_TITAN = ENTITIES.register("magma_titan",
            () -> EntityType.Builder.of(MagmaTitan::new, MobCategory.CREATURE).sized(1.8F, 2.3F).fireImmune()
                    .clientTrackingRange(10).build("magma_titan"));

    public static final DeferredBlock<Block> MAGMA_LAMP = BLOCKS.registerSimpleBlock("magma_lamp",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.3F).sound(SoundType.GLASS).lightLevel(s -> 15));

    public static final DeferredItem<Item> IGNEOUS_SHELL = ITEMS.registerSimpleItem("igneous_shell", new Item.Properties().fireResistant());
    public static final DeferredItem<Item> MAGMA_CORE = ITEMS.registerSimpleItem("magma_core", new Item.Properties().fireResistant());
    public static final DeferredItem<Item> MAGMA_SLIME = ITEMS.registerSimpleItem("magma_slime", new Item.Properties().fireResistant());
    public static final DeferredItem<BlockItem> MAGMA_LAMP_ITEM = ITEMS.registerSimpleBlockItem(MAGMA_LAMP);
    public static final DeferredItem<DeferredSpawnEggItem> MAGMA_SNAIL_EGG = ITEMS.register("magma_snail_spawn_egg",
            () -> new DeferredSpawnEggItem(MAGMA_SNAIL, 0x2c1e38, 0xff8a24, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> MAGMA_TITAN_EGG = ITEMS.register("magma_titan_spawn_egg",
            () -> new DeferredSpawnEggItem(MAGMA_TITAN, 0x1e1626, 0xfac846, new Item.Properties()));

    static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(MODID, path); }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(IgnisCreatures::attributes);
        modBus.addListener(IgnisCreatures::spawnPlacements);
        modBus.addListener(IgnisCreatures::creativeTab);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH, IgnisCreatures::breathe);
    }

    /**
     * 1.0.1: Ignis no tiene aire y Cosmonautics (prioridad HIGHEST) deja sin respirar a toda criatura que esté ahí,
     * así que los caracoles se ahogaban a los pocos segundos. Son de Ignis: no necesitan aire (salvo bajo el agua).
     */
    private static void breathe(net.neoforged.neoforge.event.entity.living.LivingBreatheEvent event) {
        if (event.canBreathe() || !(event.getEntity() instanceof MagmaSnail snail)) return;
        if (snail.isEyeInFluid(FluidTags.WATER)) return;
        event.setCanBreathe(true);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(MAGMA_SNAIL.get(), MagmaSnail.attributes().build());
        event.put(MAGMA_TITAN.get(), MagmaTitan.attributes().build());
    }

    static boolean inIgnis(ServerLevelAccessor level) {
        return level.getLevel().dimension().location().toString().equals(IGNIS);
    }

    /** Bloques de lava en un cuadrado de 11×11 y 5 de alto alrededor de pos (para el spawn). */
    static int lavaAround(ServerLevelAccessor level, BlockPos pos, int need) {
        int n = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -5; dx <= 5; dx++)
                for (int dz = -5; dz <= 5; dz++) {
                    if (level.getFluidState(p.setWithOffset(pos, dx, dy, dz)).is(FluidTags.LAVA) && ++n >= need) return n;
                }
        return n;
    }

    private static void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        // caracol: en el suelo, con al menos 12 bloques de lava cerca
        event.register(MAGMA_SNAIL.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> inIgnis(level) && level.getBlockState(pos.below()).isSolid()
                        && lavaAround(level, pos, 12) >= 12,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        // titán: raro, junto a mucha lava (40+), nunca dos a menos de 64 bloques
        event.register(MAGMA_TITAN.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> inIgnis(level) && random.nextInt(4) == 0
                        && level.getBlockState(pos.below()).isSolid() && lavaAround(level, pos, 40) >= 40
                        && level.getEntitiesOfClass(MagmaTitan.class, new AABB(pos).inflate(64)).isEmpty(),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        try {
            if (tab.getNamespace().equals("minecraft") && tab.getPath().equals("spawn_eggs")) {
                for (DeferredItem<?> egg : List.of(MAGMA_SNAIL_EGG, MAGMA_TITAN_EGG))
                    event.accept(egg.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
            if (tab.getNamespace().equals("rocketnautics") && tab.getPath().equals("world_tab")) {
                for (DeferredItem<?> item : List.of(IGNEOUS_SHELL, MAGMA_CORE, MAGMA_SLIME, MAGMA_LAMP_ITEM, MAGMA_SNAIL_EGG, MAGMA_TITAN_EGG))
                    event.accept(item.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        } catch (Exception e) {
            PlanetaryExpansion.LOGGER.error("[Criaturas] No se pudieron añadir los caracoles a la pestaña {}", tab, e);
        }
    }
}
