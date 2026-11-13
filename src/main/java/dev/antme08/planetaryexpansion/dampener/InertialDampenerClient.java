package dev.antme08.planetaryexpansion.dampener;

import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.ShaftVisual;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import com.simibubi.create.foundation.model.ModelSwapper;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.List;

/**
 * Cliente del amortiguador: eje de la entrada de rotación y texturas conectadas (el sistema de Create, como sus
 * carcasas). Todas las piezas de la carcasa se conectan entre sí, así una cara del cubo se ve como un solo panel.
 */
public final class InertialDampenerClient {
    private InertialDampenerClient() {}

    private static CTSpriteShiftEntry omni(String name) {
        return CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
                ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "block/" + name),
                ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "block/" + name + "_connected"));
    }

    // se registran al cargar la clase (antes de coser el atlas de bloques)
    private static final CTSpriteShiftEntry CASING = omni("inertial_dampener_casing");
    private static final CTSpriteShiftEntry PORT = omni("inertial_dampener_coolant_port");
    private static final CTSpriteShiftEntry REDSTONE = omni("inertial_dampener_redstone_port");
    private static final CTSpriteShiftEntry DRIVE_END = omni("inertial_dampener_drive_end");
    private static final CTSpriteShiftEntry DRIVE_SIDE = omni("inertial_dampener_drive_side");

    /** Elige la hoja conectada según la textura de la cara; conecta con cualquier pieza de la carcasa. */
    private static final class ShellCT extends ConnectedTextureBehaviour.Base {
        private final List<CTSpriteShiftEntry> shifts;

        ShellCT(CTSpriteShiftEntry... shifts) {
            this.shifts = List.of(shifts);
        }

        @Override
        public CTSpriteShiftEntry getShift(BlockState state, Direction direction, TextureAtlasSprite sprite) {
            if (sprite == null) return shifts.get(0);
            for (CTSpriteShiftEntry s : shifts) {
                if (s.getOriginal() == sprite) return s;
            }
            return null;
        }

        @Override
        public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader, BlockPos pos,
                                  BlockPos otherPos, Direction face) {
            return InertialDampener.isShell(other) && !isBeingBlocked(state, reader, pos, otherPos, face);
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(InertialDampener.DRIVE_BE.get(), ShaftRenderer::new));
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() ->
                SimpleBlockEntityVisualizer.builder(InertialDampener.DRIVE_BE.get())
                        .factory(ShaftVisual::new)
                        .skipVanillaRender(be -> true)
                        .apply()));
        modBus.addListener(InertialDampenerClient::onModelBake);
    }

    private static void onModelBake(ModelEvent.ModifyBakingResult event) {
        wrap(event, InertialDampener.CASING.get(), new ShellCT(CASING));
        wrap(event, InertialDampener.COOLANT_PORT.get(), new ShellCT(PORT));
        wrap(event, InertialDampener.REDSTONE_PORT.get(), new ShellCT(REDSTONE));
        wrap(event, InertialDampener.DRIVE.get(), new ShellCT(DRIVE_SIDE, DRIVE_END));
    }

    private static void wrap(ModelEvent.ModifyBakingResult event, Block block, ConnectedTextureBehaviour behaviour) {
        try {
            ModelSwapper.swapModels(event.getModels(), ModelSwapper.getAllBlockStateModelLocations(block),
                    model -> new CTModel(model, behaviour));
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.warn("[Amortiguador] No se pudieron conectar las texturas de {}", block, t);
        }
    }
}
