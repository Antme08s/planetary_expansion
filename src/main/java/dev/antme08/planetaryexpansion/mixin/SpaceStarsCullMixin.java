package dev.antme08.planetaryexpansion.mixin;

import dev.devce.rocketnautics.client.SkyHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import dev.antme08.planetaryexpansion.client.UniverseSky;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Arreglo del recorte de estrellas de Cosmonautics en la superficie de los planetas (0.7.50).
 * {@code SkyHandler.renderSpaceStars} solo dibuja las estrellas que caen en un cono de unos 140° alrededor de hacia
 * dónde mira la cámara ({@code starDot < 0.35f}), pero calcula esa dirección sin la rotación del planeta que
 * {@code renderUniverseForLevel} aplica al cielo. Fuera de la Tierra el cono apunta a otro sitio y se ve medio cielo
 * con estrellas y el otro medio vacío (por eso estaba apagado {@code render_universe_in_dimension} en los planetas).
 * Aquí, fuera de la Tierra, el límite pasa a -2 (se dibujan todas: son 1600 y no se nota). En la Tierra no cambia.
 * 0.7.51: en los planetas con atmósfera las estrellas y la nebulosa se desvanecen de día ({@link UniverseSky#starFade}).
 */
@Mixin(value = SkyHandler.class, remap = false)
public abstract class SpaceStarsCullMixin {
    @ModifyConstant(method = "renderSpaceStars", constant = @Constant(floatValue = 0.35f), remap = false)
    private static float planetaryExpansion$noStarCulling(float original) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.dimension() != Level.OVERWORLD) return -2.0f;
        return original;
    }

    @ModifyVariable(method = "renderSpaceStars", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private static float planetaryExpansion$fadeStars(float visibility) {
        return visibility * UniverseSky.starFade();
    }

    // renderCosmicNebula(PoseStack, Camera, float celestialAngle, float visibility): la visibilidad es el 2.º float
    @ModifyVariable(method = "renderCosmicNebula", at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false)
    private static float planetaryExpansion$fadeNebula(float visibility) {
        return visibility * UniverseSky.starFade();
    }
}
