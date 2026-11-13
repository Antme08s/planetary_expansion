package dev.antme08.planetaryexpansion.creatures;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.armor.SeaDiverArmor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Supplier;

/**
 * Criaturas de Thalassa (0.7.61, diseños aprobados por el usuario): pez farol, cangrejo de chimenea, cangrejo rey azul
 * y acechador abisal, con sus objetos (comida, gel y lámparas, cubeta, quitina para reforzar la Sea Diver Armor,
 * escudo de caparazón, pinza del rey, arpón, sonar y barbilla luminosa).
 */
public final class Creatures {
    private Creatures() {}

    private static final String MODID = PlanetaryExpansion.MODID;
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final String THALASSA = "planetary_expansion:thalassa";

    // ------------------------------------------------------------------ entidades
    public static final DeferredHolder<EntityType<?>, EntityType<LanternFish>> LANTERN_FISH = ENTITIES.register("lantern_fish",
            () -> EntityType.Builder.of(LanternFish::new, MobCategory.WATER_AMBIENT).sized(0.6F, 0.45F).clientTrackingRange(4).build("lantern_fish"));
    public static final DeferredHolder<EntityType<?>, EntityType<VentCrab>> VENT_CRAB = ENTITIES.register("vent_crab",
            () -> EntityType.Builder.of(VentCrab::new, MobCategory.CREATURE).sized(1.3F, 0.85F).clientTrackingRange(8).build("vent_crab"));
    public static final DeferredHolder<EntityType<?>, EntityType<KingCrab>> KING_CRAB = ENTITIES.register("king_crab",
            () -> EntityType.Builder.of(KingCrab::new, MobCategory.CREATURE).sized(2.4F, 1.7F).clientTrackingRange(10).build("king_crab"));
    public static final DeferredHolder<EntityType<?>, EntityType<AbyssalStalker>> ABYSSAL_STALKER = ENTITIES.register("abyssal_stalker",
            () -> EntityType.Builder.of(AbyssalStalker::new, MobCategory.MONSTER).sized(2.2F, 1.5F).clientTrackingRange(10).build("abyssal_stalker"));
    public static final DeferredHolder<EntityType<?>, EntityType<HarpoonEntity>> HARPOON_ENTITY = ENTITIES.register("harpoon",
            () -> EntityType.Builder.<HarpoonEntity>of(HarpoonEntity::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(4).updateInterval(20).build("harpoon"));

    // ------------------------------------------------------------------ bloques
    public static final DeferredBlock<Block> GEL_LAMP = BLOCKS.registerSimpleBlock("gel_lamp",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(0.3F).sound(SoundType.GLASS).lightLevel(s -> 15));
    public static final DeferredBlock<Block> ABYSSAL_LAMP = BLOCKS.registerSimpleBlock("abyssal_lamp",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.3F).sound(SoundType.GLASS).lightLevel(s -> 15));

    // ------------------------------------------------------------------ objetos
    private static FoodProperties food(int n, float s) { return new FoodProperties.Builder().nutrition(n).saturationModifier(s).build(); }

    public static final DeferredItem<Item> LANTERN_FISH_ITEM = ITEMS.registerSimpleItem("lantern_fish", new Item.Properties().food(food(2, 0.1F)));
    public static final DeferredItem<Item> COOKED_LANTERN_FISH = ITEMS.registerSimpleItem("cooked_lantern_fish", new Item.Properties().food(
            new FoodProperties.Builder().nutrition(5).saturationModifier(0.6F)
                    .effect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300), 1.0F).build()));
    public static final DeferredItem<Item> GLOW_GEL = ITEMS.registerSimpleItem("glow_gel");
    public static final DeferredItem<MobBucketItem> LANTERN_FISH_BUCKET = ITEMS.register("lantern_fish_bucket",
            () -> new MobBucketItem(LANTERN_FISH.get(), Fluids.WATER, SoundEvents.BUCKET_EMPTY_FISH, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CRAB_MEAT = ITEMS.registerSimpleItem("crab_meat", new Item.Properties().food(food(2, 0.3F)));
    public static final DeferredItem<Item> COOKED_CRAB_MEAT = ITEMS.registerSimpleItem("cooked_crab_meat", new Item.Properties().food(food(6, 0.8F)));
    public static final DeferredItem<Item> VOLCANIC_CHITIN = ITEMS.registerSimpleItem("volcanic_chitin");
    public static final DeferredItem<Item> KING_CRAB_LEG = ITEMS.registerSimpleItem("king_crab_leg", new Item.Properties().food(food(3, 0.3F)));
    public static final DeferredItem<Item> COOKED_KING_CRAB_LEG = ITEMS.registerSimpleItem("cooked_king_crab_leg", new Item.Properties().food(food(10, 1.0F)));
    public static final DeferredItem<Item> KING_SHELL = ITEMS.registerSimpleItem("king_shell");
    public static final DeferredItem<Item> KING_CLAW = ITEMS.register("king_claw", () -> new Item(new Item.Properties().stacksTo(1)
            .attributes(ItemAttributeModifiers.builder()
                    .add(Attributes.BLOCK_INTERACTION_RANGE, new AttributeModifier(rl("king_claw_block_reach"), 2.5, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND)
                    .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(rl("king_claw_entity_reach"), 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND)
                    .build())));
    public static final DeferredItem<ShellShieldItem> KING_SHELL_SHIELD = ITEMS.register("king_shell_shield",
            () -> new ShellShieldItem(new Item.Properties().durability(1008)));
    public static final DeferredItem<Item> ABYSSAL_FANG = ITEMS.registerSimpleItem("abyssal_fang");
    public static final DeferredItem<HarpoonItem> HARPOON = ITEMS.register("harpoon", () -> new HarpoonItem(new Item.Properties().durability(350)
            .attributes(ItemAttributeModifiers.builder()
                    .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 7.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                    .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.9, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                    .build())));
    public static final DeferredItem<Item> SONAR_ORGAN = ITEMS.registerSimpleItem("sonar_organ");
    public static final DeferredItem<SonarItem> SONAR = ITEMS.register("sonar", () -> new SonarItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> GLOWING_BARBEL = ITEMS.registerSimpleItem("glowing_barbel");
    public static final DeferredItem<net.minecraft.world.item.BlockItem> GEL_LAMP_ITEM = ITEMS.registerSimpleBlockItem(GEL_LAMP);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ABYSSAL_LAMP_ITEM = ITEMS.registerSimpleBlockItem(ABYSSAL_LAMP);

    public static final DeferredItem<DeferredSpawnEggItem> LANTERN_FISH_EGG = egg("lantern_fish", LANTERN_FISH, 0x18243e, 0x6effe6);
    public static final DeferredItem<DeferredSpawnEggItem> VENT_CRAB_EGG = egg("vent_crab", VENT_CRAB, 0x3a383c, 0xff8c32);
    public static final DeferredItem<DeferredSpawnEggItem> KING_CRAB_EGG = egg("king_crab", KING_CRAB, 0x2e56a8, 0xe6aa78);
    public static final DeferredItem<DeferredSpawnEggItem> ABYSSAL_STALKER_EGG = egg("abyssal_stalker", ABYSSAL_STALKER, 0xb0b6ba, 0xff7896);

    private static DeferredItem<DeferredSpawnEggItem> egg(String name, Supplier<? extends EntityType<? extends net.minecraft.world.entity.Mob>> type, int a, int b) {
        return ITEMS.register(name + "_spawn_egg", () -> new DeferredSpawnEggItem(type, a, b, new Item.Properties()));
    }

    static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(MODID, path); }

    // ------------------------------------------------------------------ registro

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(Creatures::attributes);
        modBus.addListener(Creatures::spawnPlacements);
        modBus.addListener(Creatures::creativeTab);
        NeoForge.EVENT_BUS.register(Creatures.class);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(LANTERN_FISH.get(), LanternFish.attributes().build());
        event.put(VENT_CRAB.get(), VentCrab.attributes().build());
        event.put(KING_CRAB.get(), KingCrab.attributes().build());
        event.put(ABYSSAL_STALKER.get(), AbyssalStalker.attributes().build());
    }

    static boolean inThalassa(ServerLevelAccessor level) {
        return level.getLevel().dimension().location().toString().equals(THALASSA);
    }

    private static boolean water(ServerLevelAccessor level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    private static void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        // pez farol: en el agua bajo el hielo (el hielo está en y ~67-75)
        event.register(LANTERN_FISH.get(), SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> inThalassa(level) && water(level, pos) && water(level, pos.above())
                        && pos.getY() < 64 && pos.getY() > -310,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        // cangrejo rey: raro, en el fondo, nunca dos a menos de 48 bloques
        event.register(KING_CRAB.get(), SpawnPlacementTypes.IN_WATER, Heightmap.Types.OCEAN_FLOOR,
                (type, level, reason, pos, random) -> inThalassa(level) && water(level, pos) && water(level, pos.above())
                        && level.getBlockState(pos.below()).isSolid() && pos.getY() < 30 && random.nextInt(3) == 0
                        && level.getEntitiesOfClass(KingCrab.class, new net.minecraft.world.phys.AABB(pos).inflate(48)).isEmpty(),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        // acechador abisal: muy raro, por debajo de y −150, nunca dos a menos de 64 bloques
        event.register(ABYSSAL_STALKER.get(), SpawnPlacementTypes.IN_WATER, Heightmap.Types.OCEAN_FLOOR,
                (type, level, reason, pos, random) -> inThalassa(level) && level.getDifficulty() != Difficulty.PEACEFUL
                        && water(level, pos) && water(level, pos.above()) && pos.getY() < -150 && random.nextInt(25) == 0
                        && level.getEntitiesOfClass(AbyssalStalker.class, new net.minecraft.world.phys.AABB(pos).inflate(64)).isEmpty(),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(VENT_CRAB.get(), SpawnPlacementTypes.IN_WATER, Heightmap.Types.OCEAN_FLOOR,
                (type, level, reason, pos, random) -> reason == MobSpawnType.SPAWN_EGG || reason == MobSpawnType.COMMAND,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /** Cangrejos de chimenea al generarse una chimenea (desde ThalassaVentFeature): 1-3 alrededor de la base. */
    public static void spawnVentCrabs(WorldGenLevel level, BlockPos base, RandomSource random) {
        int n = 1 + random.nextInt(3);
        for (int i = 0, tries = 0; i < n && tries < 12; tries++) {
            int dx = random.nextInt(7) - 3, dz = random.nextInt(7) - 3;
            if (dx * dx + dz * dz < 4) continue;
            BlockPos p = base.offset(dx, 0, dz);
            if (!level.getFluidState(p).is(FluidTags.WATER) || !level.getBlockState(p.below()).isSolid()) continue;
            VentCrab crab = VENT_CRAB.get().create(level.getLevel());
            if (crab == null) return;
            crab.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, random.nextFloat() * 360F, 0F);
            crab.setHome(base);
            crab.setPersistenceRequired();
            crab.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.CHUNK_GENERATION, null);
            level.addFreshEntityWithPassengers(crab);
            i++;
        }
    }

    // ------------------------------------------------------------------ eventos del juego

    /** Ruido para el acechador abisal, y los cangrejos de chimenea defienden su chimenea. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().location().toString().equals(THALASSA)) return;
        AbyssalStalker.LOUD.put(event.getPlayer().getUUID(), level.getGameTime());
        BlockState s = event.getState();
        if (s.is(Blocks.BASALT) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.SOUL_SAND)) {
            BlockPos pos = event.getPos();
            List<VentCrab> crabs = level.getEntitiesOfClass(VentCrab.class, new net.minecraft.world.phys.AABB(pos).inflate(10), c -> c.guards(pos));
            if (!event.getPlayer().isCreative()) for (VentCrab c : crabs) c.setTarget(event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Level level = event.getEntity().level();
        if (!level.isClientSide) AbyssalStalker.LOUD.put(event.getEntity().getUUID(), level.getGameTime());
    }

    /** Sea Diver Armor reforzada con quitina volcánica (mesa de herrería): +1,5 de dureza y +5 % de resistencia al empuje. */
    @SubscribeEvent
    public static void reinforcedArmor(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.copyTag().getBoolean("chitin_reinforced")) return;
        EquipmentSlotGroup slot;
        if (stack.is(SeaDiverArmor.HELMET.get())) slot = EquipmentSlotGroup.HEAD;
        else if (stack.is(SeaDiverArmor.CHESTPLATE.get())) slot = EquipmentSlotGroup.CHEST;
        else if (stack.is(SeaDiverArmor.LEGGINGS.get())) slot = EquipmentSlotGroup.LEGS;
        else if (stack.is(SeaDiverArmor.BOOTS.get())) slot = EquipmentSlotGroup.FEET;
        else return;
        String s = slot.getSerializedName();
        event.addModifier(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(rl("chitin_toughness_" + s), 1.5, AttributeModifier.Operation.ADD_VALUE), slot);
        event.addModifier(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(rl("chitin_knockback_" + s), 0.05, AttributeModifier.Operation.ADD_VALUE), slot);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        try {
            if (tab.getNamespace().equals("minecraft") && tab.getPath().equals("spawn_eggs")) {
                for (DeferredItem<?> egg : List.of(LANTERN_FISH_EGG, VENT_CRAB_EGG, KING_CRAB_EGG, ABYSSAL_STALKER_EGG))
                    event.accept(egg.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
            if (tab.getNamespace().equals("rocketnautics") && tab.getPath().equals("world_tab")) {
                for (DeferredItem<?> item : List.of(LANTERN_FISH_ITEM, COOKED_LANTERN_FISH, GLOW_GEL, LANTERN_FISH_BUCKET, GEL_LAMP_ITEM,
                        CRAB_MEAT, COOKED_CRAB_MEAT, VOLCANIC_CHITIN, KING_CRAB_LEG, COOKED_KING_CRAB_LEG, KING_SHELL, KING_SHELL_SHIELD,
                        KING_CLAW, ABYSSAL_FANG, HARPOON, SONAR_ORGAN, SONAR, GLOWING_BARBEL, ABYSSAL_LAMP_ITEM,
                        LANTERN_FISH_EGG, VENT_CRAB_EGG, KING_CRAB_EGG, ABYSSAL_STALKER_EGG))
                    event.accept(item.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        } catch (Exception e) {
            PlanetaryExpansion.LOGGER.error("[Criaturas] No se pudieron añadir a la pestaña {}", tab, e);
        }
    }
}
