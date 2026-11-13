package dev.antme08.planetaryexpansion.warp.client;

import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.ShaftVisual;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.warp.NavStatusPayload;
import dev.antme08.planetaryexpansion.warp.Warp;
import dev.antme08.planetaryexpansion.warp.WarpEffectPayload;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.LinkedHashMap;
import java.util.Map;

/** Cliente del motor de distorsión y la consola: modelos, renderizadores, mapa y efecto del salto. */
public final class WarpClient {
    private WarpClient() {}

    private static PartialModel partial(String name) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "block/" + name));
    }

    // modelos sueltos (se crean al cargar la clase, antes de hornear los modelos)
    public static final PartialModel DRIVE_FRAME = partial("warp_drive_frame");
    public static final PartialModel DRIVE_GLOW = partial("warp_drive_glow");
    public static final PartialModel DRIVE_GLASS = partial("warp_drive_glass");
    public static final PartialModel DRIVE_FLUID = partial("warp_drive_fluid");
    public static final PartialModel DRIVE_CORE = partial("warp_drive_core");
    public static final PartialModel CONSOLE_TABLE = partial("nav_console_table");
    public static final PartialModel CONSOLE_GLOW = partial("nav_console_glow");
    public static final PartialModel CONSOLE_RINGS = partial("nav_console_rings");
    public static final Map<String, PartialModel> CONSOLE_BODIES = new LinkedHashMap<>();

    static {
        for (String b : new String[]{"sun", "venus", "earth", "mars", "giant", "ice", "thalassa", "ignis"}) {
            CONSOLE_BODIES.put(b, partial("nav_console_body_" + b));
        }
    }

    /** Último estado del motor recibido para la consola abierta. */
    static NavStatusPayload lastStatus;

    public static void register(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerBlockEntityRenderer(Warp.DRIVE_BE.get(), WarpDriveRenderer::new);
            event.registerBlockEntityRenderer(Warp.CONSOLE_BE.get(), NavConsoleRenderer::new);
            event.registerBlockEntityRenderer(Warp.DRIVE_INPUT_BE.get(), ShaftRenderer::new);
        });
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() ->
                SimpleBlockEntityVisualizer.builder(Warp.DRIVE_INPUT_BE.get())
                        .factory(ShaftVisual::new)
                        .skipVanillaRender(be -> true)
                        .apply()));
        NeoForge.EVENT_BUS.register(WarpEffectOverlay.class);
    }

    public static void openMap(BlockPos console) {
        lastStatus = null;
        Minecraft.getInstance().setScreen(new NavMapScreen(console));
    }

    public static void receiveStatus(NavStatusPayload payload) {
        lastStatus = payload;
    }

    public static void receiveEffect(WarpEffectPayload payload) {
        if (payload.stage() == WarpEffectPayload.START) {
            if (Minecraft.getInstance().screen instanceof NavMapScreen) Minecraft.getInstance().setScreen(null);
            WarpEffectOverlay.start(payload.target(), payload.altitudeKm());
        } else {
            WarpEffectOverlay.stop();
        }
    }
}
