package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.weather.SpaceBeds;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sin la lluvia de la Tierra en los planetas ni en el espacio (0.7.43). En Minecraft todas las dimensiones que no son
 * el Overworld comparten su clima: si llovía en la Tierra, en el mundo helado nevaba, en Thalassa y Marte el cielo se
 * nublaba y desaparecían las estrellas. El clima de cada planeta ya lo llevan sus propias tormentas (StormManager), así
 * que en las dimensiones {@code planetary_expansion:*} y {@code rocketnautics:*} la lluvia y los truenos son siempre 0
 * (en el cliente y en el servidor: tampoco caen rayos ni se acumula nieve).
 */
@Mixin(Level.class)
public abstract class PlanetWeatherMixin {
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void planetaryExpansion$noEarthRain(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (SpaceBeds.isSpaceOrPlanet((Level) (Object) this)) cir.setReturnValue(0.0f);
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void planetaryExpansion$noEarthThunder(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (SpaceBeds.isSpaceOrPlanet((Level) (Object) this)) cir.setReturnValue(0.0f);
    }
}
