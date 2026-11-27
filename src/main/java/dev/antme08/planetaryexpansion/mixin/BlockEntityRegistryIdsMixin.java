package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.transporter.ShipGroups;
import dev.egg.registries.BlockEntityRegistry;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.0.5: después de los arreglos de datos de Cosmonautics (y los nuestros) para un bloque de una nave que cambia de
 * dimensión, las ids de naves del viaje que queden en sus datos pasan a las nuevas (ver {@link ShipGroups#remapIds}).
 */
@Mixin(value = BlockEntityRegistry.class, remap = false)
public abstract class BlockEntityRegistryIdsMixin {
    @Inject(method = "modifyNBT", at = @At("RETURN"), require = 0)
    private static void planetaryExpansion$remapIds(BlockEntityRegistry.BlockEntityInfo info, CallbackInfoReturnable<CompoundTag> cir) {
        ShipGroups.remapIds(cir.getReturnValue(), info == null ? null : info.moveInfo());
    }
}
