package dev.antme08.planetaryexpansion.cerulea.mobs;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.cerulea.CeruleaBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Supplier;

/**
 * Criaturas de Cerúlea (1.0.3, elegidas por el usuario): pastoreador, saltarín, planeador, acechador, croador, pez de
 * cinta y corredor, con lo que sueltan y lo que se fabrica con ello (daga y flechas de colmillo, lámpara de bulbo,
 * bloque de escamas luminosas…). Modelos: tools/designs/cerulea_mobs.py; objetos: tools/designs/cerulea_items.py.
 */
public final class CeruleaMobs {
    private CeruleaMobs() {}

    private static final String MODID = PlanetaryExpansion.MODID;
    public static final String CERULEA = "planetary_expansion:cerulea";
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);

    // ------------------------------------------------------------------ entidades
    public static final DeferredHolder<EntityType<?>, EntityType<Grazer>> GRAZER = ENTITIES.register("grazer",
            () -> EntityType.Builder.of(Grazer::new, MobCategory.CREATURE).sized(1.1F, 1.6F).clientTrackingRange(10).build("grazer"));
    public static final DeferredHolder<EntityType<?>, EntityType<Hopper>> HOPPER = ENTITIES.register("hopper",
            () -> EntityType.Builder.of(Hopper::new, MobCategory.CREATURE).sized(0.5F, 0.6F).clientTrackingRange(8).build("hopper"));
    public static final DeferredHolder<EntityType<?>, EntityType<Glider>> GLIDER = ENTITIES.register("glider",
            () -> EntityType.Builder.of(Glider::new, MobCategory.CREATURE).sized(0.6F, 0.45F).clientTrackingRange(10).build("glider"));
    public static final DeferredHolder<EntityType<?>, EntityType<Lurker>> LURKER = ENTITIES.register("lurker",
            () -> EntityType.Builder.of(Lurker::new, MobCategory.MONSTER).sized(1.0F, 0.95F).clientTrackingRange(10).build("lurker"));
    public static final DeferredHolder<EntityType<?>, EntityType<Croaker>> CROAKER = ENTITIES.register("croaker",
            () -> EntityType.Builder.of(Croaker::new, MobCategory.CREATURE).sized(0.55F, 0.5F).clientTrackingRange(8).build("croaker"));
    public static final DeferredHolder<EntityType<?>, EntityType<Ribbonfin>> RIBBONFIN = ENTITIES.register("ribbonfin",
            () -> EntityType.Builder.of(Ribbonfin::new, MobCategory.WATER_AMBIENT).sized(0.4F, 0.5F).clientTrackingRange(4).build("ribbonfin"));
    public static final DeferredHolder<EntityType<?>, EntityType<Runner>> RUNNER = ENTITIES.register("runner",
            () -> EntityType.Builder.of(Runner::new, MobCategory.CREATURE).sized(0.7F, 1.0F).clientTrackingRange(10).build("runner"));
    public static final DeferredHolder<EntityType<?>, EntityType<FangArrow>> FANG_ARROW_ENTITY = ENTITIES.register("fang_arrow",
            () -> EntityType.Builder.<FangArrow>of(FangArrow::new, MobCategory.MISC).sized(0.5F, 0.5F).eyeHeight(0.13F)
                    .clientTrackingRange(4).updateInterval(20).build("fang_arrow"));

    // ------------------------------------------------------------------ bloques
    public static final DeferredBlock<Block> BULB_LAMP = BLOCKS.registerSimpleBlock("bulb_lamp",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(0.3F).sound(SoundType.GLASS).lightLevel(s -> 15));
    public static final DeferredBlock<Block> GLOWING_SCALE_BLOCK = BLOCKS.registerSimpleBlock("glowing_scale_block",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(1.5F, 3.0F).sound(SoundType.CALCITE).lightLevel(s -> 12));

    // ------------------------------------------------------------------ objetos
    private static FoodProperties food(int n, float s) { return new FoodProperties.Builder().nutrition(n).saturationModifier(s).build(); }

    private static DeferredItem<Item> food(String name, int n, float s) {
        return ITEMS.registerSimpleItem(name, new Item.Properties().food(food(n, s)));
    }

    public static final DeferredItem<Item> GRAZER_MEAT = food("grazer_meat", 3, 0.3F);
    public static final DeferredItem<Item> COOKED_GRAZER_MEAT = food("cooked_grazer_meat", 8, 0.8F);
    public static final DeferredItem<Item> CERULEAN_LEATHER = ITEMS.registerSimpleItem("cerulean_leather");
    public static final DeferredItem<Item> ANTENNA_BULB = ITEMS.registerSimpleItem("antenna_bulb");
    public static final DeferredItem<Item> HOPPER_MEAT = food("hopper_meat", 3, 0.3F);
    public static final DeferredItem<Item> COOKED_HOPPER_MEAT = food("cooked_hopper_meat", 5, 0.6F);
    public static final DeferredItem<Item> HOPPER_FOOT = ITEMS.registerSimpleItem("hopper_foot");
    public static final DeferredItem<Item> GLIDER_MEMBRANE = ITEMS.registerSimpleItem("glider_membrane");
    public static final DeferredItem<Item> GLOWING_SCALE = ITEMS.registerSimpleItem("glowing_scale");
    public static final DeferredItem<Item> LURKER_FANG = ITEMS.registerSimpleItem("lurker_fang");
    public static final DeferredItem<Item> RUNNER_FANG = ITEMS.registerSimpleItem("runner_fang");
    public static final DeferredItem<Item> STRIPED_PELT = ITEMS.registerSimpleItem("striped_pelt");
    /** Daga de colmillo: rápida (2,4 golpes/s) y con 5 de daño; durabilidad de hierro. */
    public static final DeferredItem<SwordItem> FANG_DAGGER = ITEMS.register("fang_dagger",
            () -> new SwordItem(Tiers.IRON, new Item.Properties().attributes(SwordItem.createAttributes(Tiers.IRON, 2, -1.6F))));
    public static final DeferredItem<FangArrowItem> FANG_ARROW = ITEMS.register("fang_arrow", () -> new FangArrowItem(new Item.Properties()));
    public static final DeferredItem<Item> CROAKER_SLIME = ITEMS.registerSimpleItem("croaker_slime");
    public static final DeferredItem<Item> CROAKER_LEGS = food("croaker_legs", 2, 0.3F);
    public static final DeferredItem<Item> COOKED_CROAKER_LEGS = food("cooked_croaker_legs", 5, 0.6F);
    public static final DeferredItem<Item> RIBBONFIN_ITEM = food("ribbonfin", 2, 0.1F);
    public static final DeferredItem<Item> COOKED_RIBBONFIN = food("cooked_ribbonfin", 6, 0.8F);
    public static final DeferredItem<MobBucketItem> RIBBONFIN_BUCKET = ITEMS.register("ribbonfin_bucket",
            () -> new MobBucketItem(RIBBONFIN.get(), Fluids.WATER, SoundEvents.BUCKET_EMPTY_FISH, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<MobBucketItem> CROAKER_BUCKET = ITEMS.register("croaker_bucket",
            () -> new MobBucketItem(CROAKER.get(), Fluids.WATER, SoundEvents.BUCKET_EMPTY_AXOLOTL, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BlockItem> BULB_LAMP_ITEM = ITEMS.registerSimpleBlockItem(BULB_LAMP);
    public static final DeferredItem<BlockItem> GLOWING_SCALE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(GLOWING_SCALE_BLOCK);

    public static final DeferredItem<DeferredSpawnEggItem> GRAZER_EGG = egg("grazer", GRAZER, 0x3c70aa, 0x40a076);
    public static final DeferredItem<DeferredSpawnEggItem> HOPPER_EGG = egg("hopper", HOPPER, 0x7eccb0, 0x467ace);
    public static final DeferredItem<DeferredSpawnEggItem> GLIDER_EGG = egg("glider", GLIDER, 0x708cb0, 0x3460b0);
    public static final DeferredItem<DeferredSpawnEggItem> LURKER_EGG = egg("lurker", LURKER, 0x1a2636, 0x50e8d6);
    public static final DeferredItem<DeferredSpawnEggItem> CROAKER_EGG = egg("croaker", CROAKER, 0x369680, 0xe8be46);
    public static final DeferredItem<DeferredSpawnEggItem> RIBBONFIN_EGG = egg("ribbonfin", RIBBONFIN, 0x407ade, 0x50c8a0);
    public static final DeferredItem<DeferredSpawnEggItem> RUNNER_EGG = egg("runner", RUNNER, 0x2c604a, 0x34548a);

    private static DeferredItem<DeferredSpawnEggItem> egg(String name, Supplier<? extends EntityType<? extends Mob>> type, int a, int b) {
        return ITEMS.register(name + "_spawn_egg", () -> new DeferredSpawnEggItem(type, a, b, new Item.Properties()));
    }

    static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(MODID, path); }

    // ------------------------------------------------------------------ registro

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        CeruleaSounds.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(CeruleaMobs::attributes);
        modBus.addListener(CeruleaMobs::spawnPlacements);
        modBus.addListener(CeruleaMobs::creativeTab);
        NeoForge.EVENT_BUS.addListener(CeruleaMobs::brewing);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(GRAZER.get(), Grazer.attributes().build());
        event.put(HOPPER.get(), Hopper.attributes().build());
        event.put(GLIDER.get(), Glider.attributes().build());
        event.put(LURKER.get(), Lurker.attributes().build());
        event.put(CROAKER.get(), Croaker.attributes().build());
        event.put(RIBBONFIN.get(), Ribbonfin.attributes().build());
        event.put(RUNNER.get(), Runner.attributes().build());
    }

    /** Pata de saltarín: poción de salto (como la pata de conejo). */
    private static void brewing(RegisterBrewingRecipesEvent event) {
        event.getBuilder().addMix(Potions.AWKWARD, HOPPER_FOOT.get(), Potions.LEAPING);
    }

    // ------------------------------------------------------------------ spawn

    static boolean inCerulea(LevelAccessor level) {
        return level instanceof ServerLevelAccessor s && s.getLevel().dimension().location().toString().equals(CERULEA);
    }

    private static boolean grassy(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(BlockTags.DIRT) && level.getRawBrightness(pos, 0) > 8;
    }

    private static boolean waterNear(LevelAccessor level, BlockPos pos, int r) {
        for (BlockPos q : BlockPos.betweenClosed(pos.offset(-r, -2, -r), pos.offset(r, 0, r)))
            if (level.getFluidState(q).is(FluidTags.WATER)) return true;
        return false;
    }

    private static void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        var op = RegisterSpawnPlacementsEvent.Operation.REPLACE;
        event.register(GRAZER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, random) -> inCerulea(level) && grassy(level, pos), op);
        event.register(HOPPER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, random) -> inCerulea(level) && grassy(level, pos), op);
        // corredor: en manadas, poco frecuente y nunca muy cerca de otra manada
        event.register(RUNNER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, random) -> inCerulea(level) && level.getBlockState(pos.below()).is(BlockTags.DIRT)
                        && (reason != net.minecraft.world.entity.MobSpawnType.NATURAL
                        || level.getEntitiesOfClass(Runner.class, new AABB(pos).inflate(48)).size() < 4), op);
        // planeador: encima de las hojas de las copas (el heightmap con hojas da la copa al generar el chunk)
        event.register(GLIDER.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING,
                (t, level, reason, pos, random) -> inCerulea(level) && level.getBlockState(pos.below()).is(BlockTags.LEAVES)
                        && level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above()), op);
        // acechador: de noche (oscuro), raro: nunca dos a menos de 32 bloques ni más de 3 en 96
        event.register(LURKER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, random) -> inCerulea(level) && level instanceof ServerLevelAccessor s
                        && Monster.checkMonsterSpawnRules(t, s, reason, pos, random)
                        && (reason != net.minecraft.world.entity.MobSpawnType.NATURAL || (random.nextInt(4) == 0
                        && level.getEntitiesOfClass(Lurker.class, new AABB(pos).inflate(32)).isEmpty()
                        && level.getEntitiesOfClass(Lurker.class, new AABB(pos).inflate(96)).size() < 3)), op);
        // croador: en la orilla o encima de un nenúfar, con agua cerca
        event.register(CROAKER.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, random) -> inCerulea(level) && level.isEmptyBlock(pos)
                        && (level.getBlockState(pos.below()).is(BlockTags.DIRT) || level.getBlockState(pos.below()).is(CeruleaBlocks.LILY.get())
                        || level.getBlockState(pos.below()).is(BlockTags.SAND))
                        && waterNear(level, pos, 3), op);
        event.register(RIBBONFIN.get(), SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, random) -> inCerulea(level) && level.getFluidState(pos).is(FluidTags.WATER)
                        && level.getFluidState(pos.above()).is(FluidTags.WATER), op);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        try {
            List<DeferredItem<?>> eggs = List.of(GRAZER_EGG, HOPPER_EGG, GLIDER_EGG, LURKER_EGG, CROAKER_EGG, RIBBONFIN_EGG, RUNNER_EGG);
            if (tab.getNamespace().equals("minecraft") && tab.getPath().equals("spawn_eggs")) {
                for (DeferredItem<?> egg : eggs)
                    event.accept(egg.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
            if (tab.getNamespace().equals("rocketnautics") && tab.getPath().equals("world_tab")) {
                for (DeferredItem<?> item : List.of(GRAZER_MEAT, COOKED_GRAZER_MEAT, CERULEAN_LEATHER, ANTENNA_BULB, BULB_LAMP_ITEM,
                        HOPPER_MEAT, COOKED_HOPPER_MEAT, HOPPER_FOOT, GLIDER_MEMBRANE, GLOWING_SCALE, GLOWING_SCALE_BLOCK_ITEM,
                        LURKER_FANG, RUNNER_FANG, STRIPED_PELT, FANG_DAGGER, FANG_ARROW, CROAKER_SLIME, CROAKER_LEGS,
                        COOKED_CROAKER_LEGS, CROAKER_BUCKET, RIBBONFIN_ITEM, COOKED_RIBBONFIN, RIBBONFIN_BUCKET))
                    event.accept(item.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                for (DeferredItem<?> egg : eggs)
                    event.accept(egg.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        } catch (Exception e) {
            PlanetaryExpansion.LOGGER.error("[Criaturas] No se pudieron añadir las criaturas de Cerúlea a la pestaña {}", tab, e);
        }
    }
}
