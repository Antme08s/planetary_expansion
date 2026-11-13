package dev.antme08.planetaryexpansion.gravity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.ShaftVisual;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Cliente del generador de gravedad. El eje lo dibuja Create (Flywheel instanciado o, si no está activo, el
 * renderizador normal). Desde 0.7.34 además se dibuja el núcleo flotante: gira, sube y baja y brilla mientras el
 * generador está encendido.
 */
public final class GravityGeneratorClient {
    private GravityGeneratorClient() {}

    /** Modelo suelto del núcleo; se crea al cargar la clase, antes de que se horneen los modelos. */
    public static final PartialModel CORE = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "block/gravity_generator_core"));

    public static class Renderer extends ShaftRenderer<GravityGeneratorBlockEntity> {
        public Renderer(BlockEntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        protected void renderSafe(GravityGeneratorBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                                  int light, int overlay) {
            super.renderSafe(be, partialTicks, ms, buffer, light, overlay); // el eje, si no lo dibuja Flywheel
            float angle = Mth.lerp(partialTicks, be.prevCoreAngle, be.coreAngle);
            float bob = Mth.sin(AnimationTickHolder.getRenderTime(be.getLevel()) * 0.08f) * 0.04f * be.coreGlow;
            int coreLight = be.coreGlow > 0.5f ? LightTexture.FULL_BRIGHT : light;
            SuperByteBuffer core = CachedBuffers.partial(CORE, be.getBlockState());
            core.translate(0.0f, bob, 0.0f);
            core.rotateCenteredDegrees(angle, Direction.Axis.Y);
            core.light(coreLight);
            core.renderInto(ms, buffer.getBuffer(RenderType.solid()));
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(GravityGenerator.BLOCK_ENTITY.get(), Renderer::new));
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() ->
                SimpleBlockEntityVisualizer.builder(GravityGenerator.BLOCK_ENTITY.get())
                        .factory(ShaftVisual::new)
                        .skipVanillaRender(be -> false) // el renderizador normal sigue dibujando el núcleo
                        .apply()));
    }
}
