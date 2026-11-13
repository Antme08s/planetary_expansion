package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.weather.SpaceBeds;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Camas en el espacio y en los planetas (0.7.41, petición del usuario): en las dimensiones de Cosmonautics y de este
 * mod las camas no explotan. Minecraft usa {@code canSetSpawn} para las dos cosas (explotar al usarla y servir de punto
 * de reaparición). Ver {@link SpaceBeds}.
 */
@Mixin(BedBlock.class)
public abstract class BedBlockMixin {
    @Inject(method = "canSetSpawn", at = @At("HEAD"), cancellable = true)
    private static void planetaryExpansion$spaceBeds(Level level, CallbackInfoReturnable<Boolean> cir) {
        if (SpaceBeds.isSpaceOrPlanet(level)) cir.setReturnValue(true);
    }
}
