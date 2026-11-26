package dev.antme08.planetaryexpansion.titan.mobs;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.creatures.SpawnLimits;
import dev.antme08.planetaryexpansion.titan.TitanBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Supplier;

/**
 * Criaturas de Titán (1.0.4, elegidas por el usuario): rumiador de tolina (pasivo, en manadas; se defiende en grupo),
 * anguila de metano (nada en los lagos de metano) y acechador de bruma (monstruo de las tierras altas de hielo, casi
 * invisible de lejos y atraído por la luz). Con botín y recetas nuevas: grasa de bitumen (combustible), placas de tolina,
 * escama criogénica (tónico contra el frío), colmillo de escarcha (daga y flechas de hielo) y linterna de bruma (lámpara).
 * Modelos: tools/designs/titan_mobs.py; objetos: titan_items.py; sonidos: titan_sounds.py.
 */
public final class TitanMobs {
    private TitanMobs() {}

    private static final String MODID = PlanetaryExpansion.MODID;
    public static final String TITAN = "planetary_expansion:titan";

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);

    // ------------------------------------------------------------------ criaturas
    public static final DeferredHolder<EntityType<?>, EntityType<TholinRuminant>> RUMINANT = ENTITIES.register("tholin_ruminant",
            () -> EntityType.Builder.of(TholinRuminant::new, MobCategory.CREATURE).sized(1.3F, 1.3F).clientTrackingRange(10).build("tholin_ruminant"));
    public static final DeferredHolder<EntityType<?>, EntityType<MethaneEel>> EEL = ENTITIES.register("methane_eel",
            () -> EntityType.Builder.of(MethaneEel::new, MobCategory.WATER_AMBIENT).sized(0.7F, 0.35F).clientTrackingRange(6).build("methane_eel"));
    public static final DeferredHolder<EntityType<?>, EntityType<MistStalker>> STALKER = ENTITIES.register("mist_stalker",
            () -> EntityType.Builder.of(MistStalker::new, MobCategory.MONSTER).sized(1.2F, 1.9F).clientTrackingRange(12).build("mist_stalker"));
    public static final DeferredHolder<EntityType<?>, EntityType<IceArrow>> ICE_ARROW_ENTITY = ENTITIES.register("ice_arrow",
            () -> EntityType.Builder.<IceArrow>of(IceArrow::new, MobCategory.MISC).sized(0.5F, 0.5F).eyeHeight(0.13F)
                    .clientTrackingRange(4).updateInterval(20).build("ice_arrow"));

    public static final DeferredItem<DeferredSpawnEggItem> RUMINANT_EGG = ITEMS.register("tholin_ruminant_spawn_egg",
            () -> new DeferredSpawnEggItem(RUMINANT, 0xb07436, 0xe08a3a, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> EEL_EGG = ITEMS.register("methane_eel_spawn_egg",
            () -> new DeferredSpawnEggItem(EEL, 0x96c0dc, 0xff9a3c, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> STALKER_EGG = ITEMS.register("mist_stalker_spawn_egg",
            () -> new DeferredSpawnEggItem(STALKER, 0x6e7c8e, 0xffd28c, new Item.Properties()));

    // ------------------------------------------------------------------ efecto: adaptación criogénica (tónico)
    public static final DeferredHolder<MobEffect, MobEffect> CRYO_ADAPTATION = EFFECTS.register("cryo_adaptation",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x6cc4f0) {});

    // ------------------------------------------------------------------ objetos
    private static FoodProperties food(int n, float s) { return new FoodProperties.Builder().nutrition(n).saturationModifier(s).build(); }

    private static DeferredItem<Item> food(String name, int n, float s) {
        return ITEMS.registerSimpleItem(name, new Item.Properties().food(food(n, s)));
    }

    public static final DeferredItem<Item> RAW_RUMINANT_MEAT = food("raw_ruminant_meat", 3, 0.3F);
    public static final DeferredItem<Item> COOKED_RUMINANT_MEAT = food("cooked_ruminant_meat", 8, 0.85F);
    /** Grasa de bitumen: combustible de horno de larga duración (y para el tónico criogénico). */
    public static final DeferredItem<Item> BITUMEN_TALLOW = ITEMS.registerSimpleItem("bitumen_tallow");
    public static final DeferredItem<Item> THOLIN_PLATE = ITEMS.registerSimpleItem("tholin_plate");
    public static final DeferredItem<Item> RAW_METHANE_EEL = food("raw_methane_eel", 2, 0.2F);
    public static final DeferredItem<Item> COOKED_METHANE_EEL = food("cooked_methane_eel", 6, 0.7F);
    public static final DeferredItem<Item> CRYO_SCALE = ITEMS.registerSimpleItem("cryo_scale", new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON));
    public static final DeferredItem<Item> FROST_FANG = ITEMS.registerSimpleItem("frost_fang");
    public static final DeferredItem<Item> MIST_LANTERN = ITEMS.registerSimpleItem("mist_lantern", new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON));
    /** Daga de hielo: rápida como la de colmillo y además congela y ralentiza a lo que golpea. */
    public static final DeferredItem<IceDaggerItem> ICE_DAGGER = ITEMS.register("ice_dagger",
            () -> new IceDaggerItem(Tiers.IRON, new Item.Properties().attributes(SwordItem.createAttributes(Tiers.IRON, 2, -1.6F))));
    public static final DeferredItem<IceArrowItem> ICE_ARROW = ITEMS.register("ice_arrow", () -> new IceArrowItem(new Item.Properties()));
    public static final DeferredItem<CryoTonicItem> CRYO_TONIC = ITEMS.register("cryo_tonic",
            () -> new CryoTonicItem(new Item.Properties().stacksTo(16)));

    // ------------------------------------------------------------------ bloques
    /** Lámpara de bruma: la linterna del acechador en un marco de acero; luz máxima, textura que parpadea. */
    public static final DeferredBlock<Block> MIST_LAMP = BLOCKS.registerSimpleBlock("mist_lamp",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.5F).sound(SoundType.LANTERN).lightLevel(s -> 15));
    public static final DeferredBlock<Block> THOLIN_PLATE_BLOCK = BLOCKS.registerSimpleBlock("tholin_plate_block",
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(2.0F, 4.0F).sound(SoundType.NETHER_BRICKS)
                    .requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> MIST_LAMP_ITEM = ITEMS.registerSimpleBlockItem(MIST_LAMP);
    public static final DeferredItem<BlockItem> THOLIN_PLATE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(THOLIN_PLATE_BLOCK);

    // ------------------------------------------------------------------ sonidos (tools/designs/titan_sounds.py)
    private static DeferredHolder<SoundEvent, SoundEvent> s(String name) {
        return SOUNDS.register("entity." + name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MODID, "entity." + name)));
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> RUMINANT_AMBIENT = s("tholin_ruminant.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUMINANT_HURT = s("tholin_ruminant.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUMINANT_DEATH = s("tholin_ruminant.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> STALKER_AMBIENT = s("mist_stalker.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> STALKER_LURE = s("mist_stalker.lure");
    public static final DeferredHolder<SoundEvent, SoundEvent> STALKER_ATTACK = s("mist_stalker.attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> STALKER_HURT = s("mist_stalker.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> STALKER_DEATH = s("mist_stalker.death");

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        EFFECTS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(TitanMobs::attributes);
        modBus.addListener(TitanMobs::spawnPlacements);
        modBus.addListener(EventPriority.LOWEST, TitanMobs::creativeTab);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, TitanMobs::breathe);
    }

    /** Titán no tiene aire respirable: sus criaturas no lo necesitan (la anguila se ahoga fuera del metano a su manera). */
    private static void breathe(LivingBreatheEvent event) {
        var e = event.getEntity();
        if (!event.canBreathe() && (e instanceof TholinRuminant || e instanceof MethaneEel || e instanceof MistStalker)) {
            event.setCanBreathe(true);
        }
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(RUMINANT.get(), TholinRuminant.attributes().build());
        event.put(EEL.get(), MethaneEel.attributes().build());
        event.put(STALKER.get(), MistStalker.attributes().build());
    }

    static boolean inTitan(LevelAccessor level) {
        return level instanceof ServerLevelAccessor s && s.getLevel().dimension().location().toString().equals(TITAN);
    }

    static boolean isMethane(LevelAccessor level, BlockPos pos) {
        return level.getFluidState(pos).getFluidType() == TitanBlocks.METHANE_TYPE.get();
    }

    private static void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        var op = RegisterSpawnPlacementsEvent.Operation.REPLACE;
        // 1.0.4: límites (SpawnLimits): una manada por zona, pocas anguilas, un acechador cada vez
        event.register(RUMINANT.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, random) -> inTitan(level) && level.getBlockState(pos.below()).isSolid()
                        && level.getFluidState(pos).isEmpty()
                        && SpawnLimits.allow(level, reason, pos, random, TholinRuminant.class, 64, 5, 0.3F, 0.35F), op);
        event.register(EEL.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.OCEAN_FLOOR,
                (t, level, reason, pos, random) -> inTitan(level) && isMethane(level, pos) && isMethane(level, pos.above())
                        && SpawnLimits.allow(level, reason, pos, random, MethaneEel.class, 40, 5, 0.25F, 0.5F), op);
        event.register(STALKER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, random) -> inTitan(level) && level.getBlockState(pos.below()).isSolid()
                        && (reason != MobSpawnType.NATURAL || dark(level, pos))
                        && SpawnLimits.allow(level, reason, pos, random, MistStalker.class, 80, 1, 0.08F, 0.0F), op);
    }

    private static boolean dark(ServerLevelAccessor level, BlockPos pos) {
        ServerLevel sl = level.getLevel();
        return sl.isNight() || sl.getMaxLocalRawBrightness(pos) <= 7;
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        boolean eggs = tab.getNamespace().equals("minecraft") && tab.getPath().equals("spawn_eggs");
        boolean world = tab.getNamespace().equals("rocketnautics") && tab.getPath().equals("world_tab");
        if (!eggs && !world) return;
        try {
            List<Supplier<? extends Item>> list = eggs ? List.of(RUMINANT_EGG, EEL_EGG, STALKER_EGG)
                    : List.of(RUMINANT_EGG, EEL_EGG, STALKER_EGG, RAW_RUMINANT_MEAT, COOKED_RUMINANT_MEAT, BITUMEN_TALLOW,
                    THOLIN_PLATE, THOLIN_PLATE_BLOCK_ITEM, RAW_METHANE_EEL, COOKED_METHANE_EEL, CRYO_SCALE, CRYO_TONIC,
                    FROST_FANG, ICE_DAGGER, ICE_ARROW, MIST_LANTERN, MIST_LAMP_ITEM);
            for (Supplier<? extends Item> it : list)
                event.accept(it.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } catch (Exception e) {
            PlanetaryExpansion.LOGGER.error("[Titán] No se pudieron añadir las criaturas a la pestaña {}", tab, e);
        }
    }
}
