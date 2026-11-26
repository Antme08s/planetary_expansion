package dev.antme08.planetaryexpansion.fulgor.mobs;

import com.google.common.collect.ImmutableSet;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.weather.StormManager;
import dev.antme08.planetaryexpansion.weather.StormType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Criaturas de Fulgor (1.0.4): arco (pequeño, neutral; descarga eléctrica al que lo golpea, más fuerte con armadura de
 * metal), zancudo de fulgurita (pasivo; un rayo cerca lo sobrecarga) y atrapa-rayos (monstruo de las agujas; los rayos
 * lo recargan, atrae los rayos si no hay pararrayos y se aleja de los pararrayos). Las tres son nativas de Fulgor: no
 * respiran, no les afecta el fuego ni la lava y los rayos no les hacen daño, los cargan.
 * Con botín y recetas nuevas: lámpara de arco, vidrio de tormenta y pararrayos de tormenta (atrae los rayos de Fulgor
 * desde 256 bloques y da un pulso de redstone). Modelos: tools/designs/fulgor_mobs.py; objetos: fulgor_items.py,
 * storm_rod_model.py; sonidos: fulgor_sounds.py.
 */
public final class FulgorMobs {
    private FulgorMobs() {}

    private static final String MODID = PlanetaryExpansion.MODID;
    public static final String FULGOR = "planetary_expansion:fulgor";

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    public static final DeferredRegister<PoiType> POIS = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, MODID);

    // ------------------------------------------------------------------ criaturas (todas inmunes al fuego)
    public static final DeferredHolder<EntityType<?>, EntityType<ArcCrawler>> ARC = ENTITIES.register("arc_crawler",
            () -> EntityType.Builder.of(ArcCrawler::new, MobCategory.CREATURE).sized(0.7F, 0.45F).fireImmune()
                    .clientTrackingRange(8).build("arc_crawler"));
    public static final DeferredHolder<EntityType<?>, EntityType<FulguriteStrider>> STRIDER = ENTITIES.register("fulgurite_strider",
            () -> EntityType.Builder.of(FulguriteStrider::new, MobCategory.CREATURE).sized(1.0F, 1.8F).fireImmune()
                    .clientTrackingRange(10).build("fulgurite_strider"));
    public static final DeferredHolder<EntityType<?>, EntityType<SpireCatcher>> CATCHER = ENTITIES.register("spire_catcher",
            () -> EntityType.Builder.of(SpireCatcher::new, MobCategory.MONSTER).sized(0.9F, 2.3F).fireImmune()
                    .clientTrackingRange(12).build("spire_catcher"));

    public static final DeferredItem<DeferredSpawnEggItem> ARC_EGG = ITEMS.register("arc_crawler_spawn_egg",
            () -> new DeferredSpawnEggItem(ARC, 0x221e2c, 0xce80ff, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> STRIDER_EGG = ITEMS.register("fulgurite_strider_spawn_egg",
            () -> new DeferredSpawnEggItem(STRIDER, 0x968cac, 0xe2aaff, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> CATCHER_EGG = ITEMS.register("spire_catcher_spawn_egg",
            () -> new DeferredSpawnEggItem(CATCHER, 0x3a3644, 0xc4703c, new Item.Properties()));

    // ------------------------------------------------------------------ objetos
    public static final DeferredItem<Item> ARC_GLAND = ITEMS.registerSimpleItem("arc_gland");
    public static final DeferredItem<Item> CRYSTAL_FILAMENT = ITEMS.registerSimpleItem("crystal_filament");
    public static final DeferredItem<Item> COPPER_ANTENNA = ITEMS.registerSimpleItem("copper_antenna",
            new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON));
    public static final DeferredItem<Item> FULGURITE_SHARD = ITEMS.registerSimpleItem("fulgurite_shard");

    // ------------------------------------------------------------------ bloques
    public static final DeferredBlock<ArcLampBlock> ARC_LAMP = BLOCKS.register("arc_lamp", () -> new ArcLampBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.5F).sound(SoundType.GLASS)
                    .lightLevel(s -> s.getValue(ArcLampBlock.STORM) ? 15 : 11)));
    public static final DeferredBlock<TransparentBlock> STORM_GLASS = BLOCKS.register("storm_glass", () -> new TransparentBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).mapColor(MapColor.COLOR_PURPLE).strength(0.6F, 12.0F)) {});
    public static final DeferredBlock<StormRodBlock> STORM_ROD = BLOCKS.register("storm_rod", () -> new StormRodBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.LIGHTNING_ROD).mapColor(MapColor.COLOR_ORANGE).noOcclusion()));
    public static final DeferredItem<BlockItem> ARC_LAMP_ITEM = ITEMS.registerSimpleBlockItem(ARC_LAMP);
    public static final DeferredItem<BlockItem> STORM_GLASS_ITEM = ITEMS.registerSimpleBlockItem(STORM_GLASS);
    public static final DeferredItem<BlockItem> STORM_ROD_ITEM = ITEMS.registerSimpleBlockItem(STORM_ROD);

    /** Punto de interés del pararrayos de tormenta, para encontrarlo rápido (como el pararrayos normal). */
    public static final DeferredHolder<PoiType, PoiType> STORM_ROD_POI = POIS.register("storm_rod",
            () -> new PoiType(ImmutableSet.copyOf(STORM_ROD.get().getStateDefinition().getPossibleStates()), 0, 1));

    // ------------------------------------------------------------------ sonidos (tools/designs/fulgor_sounds.py)
    private static DeferredHolder<SoundEvent, SoundEvent> s(String name) {
        return SOUNDS.register("entity." + name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MODID, "entity." + name)));
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_AMBIENT = s("arc_crawler.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_ZAP = s("arc_crawler.zap");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_HURT = s("arc_crawler.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_DEATH = s("arc_crawler.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRIDER_AMBIENT = s("fulgurite_strider.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRIDER_HURT = s("fulgurite_strider.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRIDER_DEATH = s("fulgurite_strider.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRIDER_CHARGE = s("fulgurite_strider.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> CATCHER_AMBIENT = s("spire_catcher.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> CATCHER_LEAP = s("spire_catcher.leap");
    public static final DeferredHolder<SoundEvent, SoundEvent> CATCHER_CHARGE = s("spire_catcher.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> CATCHER_HURT = s("spire_catcher.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> CATCHER_DEATH = s("spire_catcher.death");

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        SOUNDS.register(modBus);
        POIS.register(modBus);
        modBus.addListener(FulgorMobs::attributes);
        modBus.addListener(FulgorMobs::spawnPlacements);
        modBus.addListener(EventPriority.LOWEST, FulgorMobs::creativeTab);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, FulgorMobs::breathe);
        NeoForge.EVENT_BUS.addListener(FulgorMobs::lightning);
    }

    /** Fulgor no tiene aire respirable: sus criaturas no respiran. */
    private static void breathe(LivingBreatheEvent event) {
        if (!event.canBreathe() && event.getEntity() instanceof Chargeable) event.setCanBreathe(true);
    }

    /**
     * Cada rayo de verdad que cae: pulso del pararrayos de tormenta si le da a uno, y aviso a las criaturas de Fulgor
     * que haya a menos de 24 bloques (cada una decide a qué distancia le afecta).
     */
    private static void lightning(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof LightningBolt bolt)) return;
        Vec3 p = bolt.position();
        BlockPos hit = BlockPos.containing(p.x, p.y - 1.0E-6, p.z);
        if (level.getBlockState(hit).getBlock() instanceof StormRodBlock rod) rod.struck(level.getBlockState(hit), level, hit);
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(p, p).inflate(24.0), e -> e instanceof Chargeable)) {
            ((Chargeable) m).nearbyLightning(m.distanceToSqr(p));
        }
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(ARC.get(), ArcCrawler.attributes().build());
        event.put(STRIDER.get(), FulguriteStrider.attributes().build());
        event.put(CATCHER.get(), SpireCatcher.attributes().build());
    }

    static boolean inFulgor(LevelAccessor level) {
        return level instanceof ServerLevelAccessor s && s.getLevel().dimension().location().toString().equals(FULGOR);
    }

    static boolean inFulgor(Level level) {
        return level.dimension().location().toString().equals(FULGOR);
    }

    /** ¿Hay tormenta eléctrica? (la de Fulgor en Fulgor; las de la Tierra en el resto) */
    static boolean stormy(Level level) {
        if (inFulgor(level)) return StormManager.intensity(StormType.FULGOR_STORM) > 0.3F;
        return level.isThundering();
    }

    /** Pararrayos (normal o de tormenta) más cercano a menos de {@code range} bloques. */
    static Optional<BlockPos> nearestRod(ServerLevel level, BlockPos from, int range) {
        return level.getPoiManager().findClosest(poi -> poi.is(PoiTypes.LIGHTNING_ROD) || poi.is(STORM_ROD_POI.getKey()),
                from, range, PoiManager.Occupancy.ANY);
    }

    /**
     * Para FulgorStorms: si no hay pararrayos que atraiga el rayo, un atrapa-rayos a menos de 24 bloques del punto lo
     * atrae (su antena de cobre es lo más alto de por ahí).
     */
    public static Optional<BlockPos> catcherNear(ServerLevel level, BlockPos pos) {
        List<SpireCatcher> list = level.getEntitiesOfClass(SpireCatcher.class, new AABB(pos).inflate(24.0, 64.0, 24.0));
        SpireCatcher best = null;
        for (SpireCatcher c : list) if (best == null || c.distanceToSqr(Vec3.atCenterOf(pos)) < best.distanceToSqr(Vec3.atCenterOf(pos))) best = c;
        return best == null ? Optional.empty() : Optional.of(best.blockPosition());
    }

    private static void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        var op = RegisterSpawnPlacementsEvent.Operation.REPLACE;
        event.register(ARC.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, random) -> inFulgor(level) && level.getBlockState(pos.below()).isSolid()
                        && (reason != MobSpawnType.NATURAL
                        || level.getEntitiesOfClass(ArcCrawler.class, new AABB(pos).inflate(32)).size() < 8), op);
        event.register(STRIDER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, random) -> inFulgor(level) && level.getBlockState(pos.below()).isSolid(), op);
        // atrapa-rayos: 1 de cada 2 intentos y nunca más de 3 a 48 bloques (de día o de noche: en Fulgor siempre hay tormenta)
        event.register(CATCHER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, random) -> inFulgor(level) && level.getBlockState(pos.below()).isSolid()
                        && (reason != MobSpawnType.NATURAL || (random.nextInt(2) == 0
                        && level.getEntitiesOfClass(SpireCatcher.class, new AABB(pos).inflate(48)).size() < 3)), op);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        boolean eggs = tab.getNamespace().equals("minecraft") && tab.getPath().equals("spawn_eggs");
        boolean world = tab.getNamespace().equals("rocketnautics") && tab.getPath().equals("world_tab");
        if (!eggs && !world) return;
        try {
            List<Supplier<? extends Item>> list = eggs ? List.of(ARC_EGG, STRIDER_EGG, CATCHER_EGG)
                    : List.of(ARC_EGG, STRIDER_EGG, CATCHER_EGG, ARC_GLAND, FULGURITE_SHARD, CRYSTAL_FILAMENT, COPPER_ANTENNA,
                    ARC_LAMP_ITEM, STORM_GLASS_ITEM, STORM_ROD_ITEM);
            for (Supplier<? extends Item> it : list)
                event.accept(it.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } catch (Exception e) {
            PlanetaryExpansion.LOGGER.error("[Fulgor] No se pudieron añadir las criaturas a la pestaña {}", tab, e);
        }
    }

    static boolean isRedstone(net.minecraft.world.item.ItemStack s) { return s.is(Items.REDSTONE); }
}
