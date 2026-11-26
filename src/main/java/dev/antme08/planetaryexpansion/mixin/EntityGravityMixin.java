package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.gravity.GravityFields;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Generador de gravedad (0.7.17): Cosmonautics reduce la gravedad de las entidades en el espacio profundo (y con la
 * altura) cambiando el valor que devuelve {@code Entity.getGravity}. Si la entidad está en una nave con un generador
 * funcionando, devolvemos la gravedad normal. Da igual el orden respecto a Cosmonautics: si corremos antes, al fijar
 * el valor se sale del método sin pasar por el suyo; si corremos después, lo sobrescribimos.
 */
@Mixin(value = Entity.class, priority = 1500)
public abstract class EntityGravityMixin {
    @Shadow public abstract boolean isNoGravity();
    @Shadow protected abstract double getDefaultGravity();

    /** 1.0.4: getGravity se llama varias veces por tick; la búsqueda de naves cercanas se hace una vez por tick. */
    @Unique private long planetaryExpansion$fieldTick = Long.MIN_VALUE;
    @Unique private boolean planetaryExpansion$fieldCached;

    @Inject(method = "getGravity", at = @At("RETURN"), cancellable = true, require = 0)
    private void planetaryExpansion$artificialGravity(CallbackInfoReturnable<Double> cir) {
        if (isNoGravity()) return;
        double normal = getDefaultGravity();
        if (cir.getReturnValue() >= normal) return;
        Entity self = (Entity) (Object) this;
        long now = self.level().getGameTime();
        if (now != planetaryExpansion$fieldTick) {
            planetaryExpansion$fieldTick = now;
            planetaryExpansion$fieldCached = GravityFields.inField(self);
        }
        if (planetaryExpansion$fieldCached) cir.setReturnValue(normal);
    }
}
