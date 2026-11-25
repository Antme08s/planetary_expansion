package dev.antme08.planetaryexpansion.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Vector3D;
import dev.devce.rocketnautics.shaded.orekit.time.AbsoluteDate;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Noche permanente del mundo helado con el universo de Cosmonautics en el cielo (0.7.53). La dimensión tiene
 * {@code fixed_time 18000} (pedido por el usuario: cielo nocturno con la aurora de Complementary), pero el Sol del
 * universo va por la órbita real y salía en el cielo con su halo. Aquí no se dibuja el Sol en esa dimensión; los demás
 * planetas, las lunas y las estrellas siguen moviéndose como siempre.
 */
@Mixin(value = DeepSpaceHandler.class, remap = false)
public abstract class NightSunMixin {
    @Inject(require = 0, method = "renderPlanet", at = @At("HEAD"), cancellable = true, remap = false)
    private static void planetaryExpansion$noSunAtNight(CubePlanet planet, Vector3D pos, PoseStack poseStack, AbsoluteDate date,
                                                       float a, float b, CallbackInfoReturnable<Boolean> cir) {
        var level = Minecraft.getInstance().level;
        if (level == null || !"sol".equals(planet.frame().getName())) return;
        if (level.dimension().location().toString().equals("planetary_expansion:ice_world")) cir.setReturnValue(false);
    }
}
