package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.transporter.ShipPassengers;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.0.4.1: las criaturas que viajan en una nave al cambiar de dimensión (ver {@link ShipPassengers}). */
@Mixin(targets = "dev.egg.SubLevelWarper", remap = false)
public abstract class SubLevelWarperEntityMixin {
    @Inject(method = "TeleportEntity", at = @At("HEAD"), require = 0)
    private static void planetaryExpansion$passenger(Entity entity,
                                                    dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer from,
                                                    dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer to,
                                                    org.joml.Vector3d a, org.joml.Vector3d b,
                                                    dev.ryanhcode.sable.sublevel.SubLevel sub,
                                                    java.util.HashMap<java.util.UUID, dev.egg.DimensionalSable.Pair<java.util.UUID, net.minecraft.core.Vec3i>> map,
                                                    java.util.Set<java.util.UUID> done, CallbackInfo ci) {
        ShipPassengers.moving(entity);
    }
}
