package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.client.StripToLinesSource;
import dev.devce.rocketnautics.client.render.HologramTableRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Arreglo para Cosmonautics (0.7.15): la mesa de hologramas dibuja órbitas y trayectorias con
 * {@code RenderType.lineStrip()}, que comparte un solo búfer, así que cada trazo empezaba donde acabó el anterior
 * (líneas sueltas entre planetas y de una mesa a otra). Toda la mesa dibuja ahora con un MultiBufferSource que
 * convierte cada trazo en segmentos sueltos (RenderType.lines()): cada llamada a getBuffer(lineStrip) es un trazo
 * nuevo que no se une con nada. (0.7.14 lo intentaba vaciando el búfer y no funcionaba con Iris/Sodium.)
 */
@Mixin(value = HologramTableRenderer.class, remap = false)
public abstract class HologramLineStripMixin {

    @ModifyVariable(
            method = "renderSafe(Ldev/devce/rocketnautics/content/blocks/HologramTableBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private MultiBufferSource planetaryExpansion$separateStrips(MultiBufferSource source) {
        return source instanceof StripToLinesSource ? source : new StripToLinesSource(source);
    }
}
