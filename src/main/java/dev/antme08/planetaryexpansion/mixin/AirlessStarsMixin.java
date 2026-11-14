package dev.antme08.planetaryexpansion.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Estrellas también de día en los planetas sin atmósfera (0.7.48). Las estrellas que se ven en los planetas son las
 * de Minecraft (las de Cosmonautics solo salen con {@code enableCustomSky} o en el espacio profundo), y Minecraft solo
 * las dibuja de noche. En Ignis el cielo es negro (no tiene aire), así que de día quedaba un cielo negro vacío. Ahí el
 * brillo de las estrellas es siempre el de la noche cerrada (0,5). En los planetas con atmósfera no cambia nada: de día
 * el cielo tiene color y no se ven, como en la Tierra.
 */
@Mixin(ClientLevel.class)
public abstract class AirlessStarsMixin {
    private static final Set<ResourceLocation> AIRLESS = Set.of(
            ResourceLocation.fromNamespaceAndPath("planetary_expansion", "ignis"),
            ResourceLocation.fromNamespaceAndPath("planetary_expansion", "velo"));   // 1.0.3

    @Inject(method = "getStarBrightness", at = @At("RETURN"), cancellable = true)
    private void planetaryExpansion$airlessStars(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (AIRLESS.contains(((ClientLevel) (Object) this).dimension().location()))
            cir.setReturnValue(Math.max(cir.getReturnValueF(), 0.5f));
    }
}
