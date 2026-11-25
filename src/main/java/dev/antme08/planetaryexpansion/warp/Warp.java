package dev.antme08.planetaryexpansion.warp;

import com.simibubi.create.api.stress.BlockStressValues;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.joml.Vector3d;

import java.util.function.Supplier;

/**
 * Motor de distorsión y consola de navegación (0.7.37, idea del usuario): bloques de final de juego para saltar con la
 * nave a la órbita de cualquier planeta. Ver {@link WarpDriveBlockEntity} y el cliente en warp/client.
 */
public final class Warp {
    private Warp() {}

    /** Estrés por RPM de cada una de las dos entradas de eje. A 256 RPM son 16 384 SU por entrada. */
    public static final double STRESS_IMPACT = 64.0;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PlanetaryExpansion.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PlanetaryExpansion.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PlanetaryExpansion.MODID);

    private static BlockBehaviour.Properties props(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(6.0f, 1200.0f).sound(SoundType.NETHERITE_BLOCK)
                .requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK);
    }

    private static BlockBehaviour.Properties partProps(MapColor color) {
        return props(color).noLootTable().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false);
    }

    public static final DeferredBlock<WarpDriveBlock> DRIVE = BLOCKS.register("warp_drive",
            () -> new WarpDriveBlock(props(MapColor.COLOR_PURPLE).lightLevel(s -> 7)));
    public static final DeferredBlock<WarpPartBlock> DRIVE_PART = BLOCKS.register("warp_drive_part",
            () -> new WarpPartBlock(partProps(MapColor.COLOR_PURPLE)));
    public static final DeferredBlock<WarpDriveInputBlock> DRIVE_INPUT = BLOCKS.register("warp_drive_input",
            () -> new WarpDriveInputBlock(partProps(MapColor.COLOR_PURPLE)));
    public static final DeferredBlock<NavConsoleBlock> CONSOLE = BLOCKS.register("nav_console",
            () -> new NavConsoleBlock(props(MapColor.COLOR_CYAN).lightLevel(s -> 9)));
    public static final DeferredBlock<WarpPartBlock> CONSOLE_PART = BLOCKS.register("nav_console_part",
            () -> new WarpPartBlock(partProps(MapColor.COLOR_CYAN)));

    public static final DeferredItem<BlockItem> DRIVE_ITEM =
            ITEMS.registerSimpleBlockItem(DRIVE, new Item.Properties().rarity(Rarity.EPIC).stacksTo(1));
    public static final DeferredItem<BlockItem> CONSOLE_ITEM =
            ITEMS.registerSimpleBlockItem(CONSOLE, new Item.Properties().rarity(Rarity.RARE).stacksTo(1));
    /** 1.0.3: cristal de helio-3 (al triturar regolito lunar suelto con Create); ingrediente lunar del núcleo estelar. */
    public static final DeferredItem<Item> HELIUM3 = ITEMS.registerSimpleItem("helium3_crystal", new Item.Properties().rarity(Rarity.UNCOMMON));
    /** 1.0.3: núcleo de distorsión estelar: clic derecho sobre un motor de distorsión para que pueda saltar entre estrellas. */
    public static final DeferredItem<StellarCoreItem> STELLAR_CORE = ITEMS.register("stellar_warp_core",
            () -> new StellarCoreItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(16).fireResistant()));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WarpDriveBlockEntity>> DRIVE_BE =
            BLOCK_ENTITIES.register("warp_drive", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new WarpDriveBlockEntity(Warp.DRIVE_BE.get(), pos, state), DRIVE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WarpDriveInputBlockEntity>> DRIVE_INPUT_BE =
            BLOCK_ENTITIES.register("warp_drive_input", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new WarpDriveInputBlockEntity(Warp.DRIVE_INPUT_BE.get(), pos, state), DRIVE_INPUT.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NavConsoleBlockEntity>> CONSOLE_BE =
            BLOCK_ENTITIES.register("nav_console", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new NavConsoleBlockEntity(Warp.CONSOLE_BE.get(), pos, state), CONSOLE.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() ->
                BlockStressValues.IMPACTS.register(DRIVE_INPUT.get(), () -> STRESS_IMPACT)));
        modBus.addListener(Warp::registerCapabilities);
        modBus.addListener(EventPriority.LOWEST, Warp::addToCreativeTab);
        modBus.addListener(Warp::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(NavStatusRequestPayload.TYPE, NavStatusRequestPayload.CODEC,
                        (p, ctx) -> ctx.enqueueWork(() -> onStatusRequest(p, ctx)))
                .playToServer(WarpJumpRequestPayload.TYPE, WarpJumpRequestPayload.CODEC,
                        (p, ctx) -> ctx.enqueueWork(() -> onJumpRequest(p, ctx)))
                .playToClient(NavStatusPayload.TYPE, NavStatusPayload.CODEC,
                        (p, ctx) -> ctx.enqueueWork(() -> dev.antme08.planetaryexpansion.warp.client.WarpClient.receiveStatus(p)))
                .playToClient(WarpEffectPayload.TYPE, WarpEffectPayload.CODEC,
                        (p, ctx) -> ctx.enqueueWork(() -> dev.antme08.planetaryexpansion.warp.client.WarpClient.receiveEffect(p)));
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // nitrógeno por tuberías de Create: vale cualquier bloque del motor
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, DRIVE_BE.get(), (be, side) -> be.tank());
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) ->
                level.getBlockEntity(Footprint.anchorOf(pos, state)) instanceof WarpDriveBlockEntity drive ? drive.tank() : null,
                DRIVE_PART.get(), DRIVE_INPUT.get());
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!tab.getNamespace().equals("rocketnautics") || !tab.getPath().equals("world_tab")) return;
        for (Supplier<? extends Item> item : java.util.List.<Supplier<? extends Item>>of(CONSOLE_ITEM, DRIVE_ITEM, STELLAR_CORE, HELIUM3)) {
            try {
                event.accept(item.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            } catch (RuntimeException e) {
                PlanetaryExpansion.LOGGER.error("[Distorsión] No se pudo añadir {} a la pestaña {}", item, tab, e);
            }
        }
    }

    // ------------------------------------------------------------------ utilidades

    /** Nave de Sable que contiene la posición (o null si el bloque no está en una nave). */
    public static ServerSubLevel shipAt(Level level, BlockPos pos) {
        try {
            SubLevelAccess s = Sable.HELPER.getContaining(level, (net.minecraft.core.Vec3i) pos);
            return s instanceof ServerSubLevel ship && !ship.isRemoved() ? ship : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Centro del bloque en coordenadas del mundo (si está en una nave, con la posición y el giro de la nave). */
    public static Vector3d toWorld(Level level, BlockPos pos) {
        Vector3d v = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        ServerSubLevel ship = shipAt(level, pos);
        if (ship != null) {
            try {
                v = ship.logicalPose().transformPosition(v, new Vector3d());
            } catch (Throwable ignored) {
            }
        }
        return v;
    }

    private static boolean nearConsole(ServerPlayer player, BlockPos console) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(console) || !(level.getBlockEntity(console) instanceof NavConsoleBlockEntity)) return false;
        Vector3d c = toWorld(level, console);
        return player.distanceToSqr(c.x, c.y, c.z) < 16 * 16;
    }

    private static void onStatusRequest(NavStatusRequestPayload p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !nearConsole(player, p.console())) return;
        ServerLevel level = player.serverLevel();
        // 1.0.4: en la superficie de un planeta, si la nave lleva transportador espacial, manda él
        var transporter = onSurface(level) ? dev.antme08.planetaryexpansion.transporter.SpaceTransporterBlockEntity.findFor(level, p.console()) : null;
        if (transporter != null) {
            PacketDistributor.sendToPlayer(player, transporter.statusFor(level, p.console(), p.target()));
            return;
        }
        WarpDriveBlockEntity drive = WarpDriveBlockEntity.findFor(level, p.console(), p.target());
        NavStatusPayload out = drive == null
                ? new NavStatusPayload(p.console(), WarpStatus.NO_DRIVE.ordinal(), 0, 0, 0, 0, "", false, false)
                : new NavStatusPayload(p.console(), drive.status(level).ordinal(), (float) drive.charge(), (float) drive.heat(),
                drive.nitrogen(), (float) drive.rpm(), drive.currentOrbit(level), drive.isStellar(), false);
        PacketDistributor.sendToPlayer(player, out);
    }

    private static void onJumpRequest(WarpJumpRequestPayload p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !nearConsole(player, p.console())) return;
        ServerLevel level = player.serverLevel();
        var transporter = onSurface(level) ? dev.antme08.planetaryexpansion.transporter.SpaceTransporterBlockEntity.findFor(level, p.console()) : null;
        if (transporter != null) {
            transporter.tryLaunch(player, p.target(), Double.isFinite(p.altitudeKm()) ? p.altitudeKm() : 0);
            return;
        }
        WarpDriveBlockEntity drive = WarpDriveBlockEntity.findFor(level, p.console(), p.target());
        if (drive == null) {
            player.displayClientMessage(WarpStatus.NO_DRIVE.text(), true);
            return;
        }
        drive.tryJump(player, p.target(), Double.isFinite(p.altitudeKm()) ? p.altitudeKm() : 0);
    }

    private static boolean onSurface(ServerLevel level) {
        return !level.dimension().location().equals(WarpDriveBlockEntity.DEEP_SPACE);
    }

    static boolean isPart(Block b) {
        return b == DRIVE_PART.get() || b == DRIVE_INPUT.get() || b == CONSOLE_PART.get();
    }
}
