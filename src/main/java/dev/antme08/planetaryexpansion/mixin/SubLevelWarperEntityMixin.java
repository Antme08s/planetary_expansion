package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.transporter.ShipPassengers;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.0.4.1: las criaturas que viajan en una nave al cambiar de dimensión (ver {@link ShipPassengers}).
 * 1.0.5: el grupo de naves que viaja junto incluye también las sujetas con imanes (ver
 * {@link dev.antme08.planetaryexpansion.transporter.ShipGroups}).
 */
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
        dev.antme08.planetaryexpansion.transporter.ShipSwimmers.beforeTransfer(entity, sub, map);
        ShipPassengers.moving(entity);
    }

    @Redirect(method = "WarpSubLevel(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/world/level/Level;Lorg/joml/Vector3d;Z)I",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/SubLevelHelper;getConnectedChain(Ldev/ryanhcode/sable/sublevel/SubLevel;)Ljava/util/Collection;"),
            require = 0)
    private static java.util.Collection<dev.ryanhcode.sable.sublevel.SubLevel> planetaryExpansion$group(dev.ryanhcode.sable.sublevel.SubLevel start) {
        return dev.antme08.planetaryexpansion.transporter.ShipGroups.chain(start);
    }
}
