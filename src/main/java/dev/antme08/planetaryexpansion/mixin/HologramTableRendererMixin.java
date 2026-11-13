package dev.antme08.planetaryexpansion.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.antme08.planetaryexpansion.client.HologramLabels;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import dev.devce.rocketnautics.client.render.HologramTableRenderer;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Vector3D;
import dev.devce.rocketnautics.shaded.orekit.frames.Frame;
import dev.devce.rocketnautics.shaded.orekit.time.AbsoluteDate;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Mesa holográfica de Cosmonautics: además de lo que dibuja el mod base, pone un marcador de color y
 * el nombre (con la distancia) de cada planeta, para que se vean aunque a escala del sistema solar
 * midan menos de un píxel.
 *
 * Se envuelve la llamada a DeepSpaceHandler.renderHologram: en ese punto la pila de transformaciones
 * ya está centrada en la posición de la mesa, igual que la usa Cosmonautics para colocar los planetas.
 * Se llama al método original sin cambios y después se dibujan las etiquetas.
 *
 * remap = false: las clases de Cosmonautics no están ofuscadas y NeoForge usa nombres de Mojang.
 */
@Mixin(value = HologramTableRenderer.class, remap = false)
public abstract class HologramTableRendererMixin {

    @Redirect(
            method = "renderSafe(Ldev/devce/rocketnautics/content/blocks/HologramTableBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/devce/rocketnautics/client/DeepSpaceHandler;renderHologram(Ldev/devce/rocketnautics/shaded/hipparchus/geometry/euclidean/threed/Vector3D;Ldev/devce/rocketnautics/shaded/orekit/frames/Frame;Ldev/devce/rocketnautics/content/orbit/universe/UniverseDefinition;Ldev/devce/rocketnautics/shaded/orekit/time/AbsoluteDate;DDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)Lit/unimi/dsi/fastutil/Pair;"))
    private Pair<Frame, List<CubePlanet>> planetaryExpansion$planetLabels(Vector3D posInFrame, Frame posFrame, UniverseDefinition universe,
                                                                         AbsoluteDate date, double scale, double scaleTest,
                                                                         PoseStack poseStack, MultiBufferSource source) {
        Pair<Frame, List<CubePlanet>> result = DeepSpaceHandler.renderHologram(posInFrame, posFrame, universe, date, scale, scaleTest, poseStack, source);
        HologramLabels.render(posInFrame, posFrame, universe, date, scale, scaleTest, poseStack, source);
        return result;
    }
}
