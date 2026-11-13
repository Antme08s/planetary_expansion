package dev.antme08.planetaryexpansion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.antme08.planetaryexpansion.weather.SpaceBeds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 0.7.67 (petición del usuario: que la cama guarde el punto de reaparición también en el espacio): Cosmonautics marca
 * el espacio profundo como dimensión "no natural", y Minecraft corta ahí {@code startSleepInBed} antes de comprobar la
 * distancia y si la cama está tapada, y antes de guardar la reaparición. Para esa comprobación concreta, en el espacio
 * y los planetas se toma como "natural"; así se hacen las comprobaciones normales y se guarda la cama (con Sable,
 * también en una nave que se mueve). No cambia nada más de la dimensión.
 * {@code require = 0}: si NeoForge cambiara ese método, {@link SpaceBeds} guarda la reaparición de todos modos.
 */
@Mixin(ServerPlayer.class)
public abstract class SpaceBedNaturalMixin {
    @WrapOperation(method = "startSleepInBed", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/dimension/DimensionType;natural()Z"))
    private boolean planetaryExpansion$spaceIsNaturalForBeds(DimensionType type, Operation<Boolean> original) {
        if (SpaceBeds.isSpaceOrPlanet(((ServerPlayer) (Object) this).level())) return true;
        return original.call(type);
    }
}
