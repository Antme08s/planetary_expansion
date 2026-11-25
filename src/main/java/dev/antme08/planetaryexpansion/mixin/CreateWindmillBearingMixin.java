package dev.antme08.planetaryexpansion.mixin;

import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.weather.StormManager;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Los molinos de Create giran más rápido durante las tormentas de Marte y del planeta helado.
 *
 * La velocidad del molino depende del número de velas. Aquí se multiplica por un factor que
 * depende de la tormenta (normal hasta ×1.6, fuerte hasta ×2.5), solo si las aspas están a la
 * intemperie. El factor se revisa una vez por segundo en el servidor y, si cambia, se recalcula la
 * red cinética (updateGeneratedRotation), igual que cuando se añaden velas.
 *
 * El chequeo va en RETURN (todas las salidas de tick), no en TAIL: en el servidor el tick de
 * Create sale antes de tiempo con "if (!queuedReassembly) return;", así que TAIL casi nunca se
 * ejecutaba (por eso en 0.3.3 y 0.3.4 los molinos no aceleraban).
 *
 * remap = false: las clases de Create no están ofuscadas.
 */
@Mixin(value = WindmillBearingBlockEntity.class, remap = false)
public abstract class CreateWindmillBearingMixin extends MechanicalBearingBlockEntity {
    /** Factor de tormenta aplicado actualmente a este molino (1 = sin tormenta). */
    @Unique
    private float planetaryExpansion$stormMultiplier = 1f;

    protected CreateWindmillBearingMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(require = 0, method = "getGeneratedSpeed", at = @At("RETURN"), cancellable = true)
    private void planetaryExpansion$stormSpeed(CallbackInfoReturnable<Float> cir) {
        // Sin aspas montadas Create devuelve la última velocidad guardada, que ya incluye el
        // factor: no se vuelve a multiplicar.
        if (movedContraption == null || !running || planetaryExpansion$stormMultiplier == 1f) return;
        cir.setReturnValue(cir.getReturnValue() * planetaryExpansion$stormMultiplier);
    }

    @Inject(require = 0, method = "tick", at = @At("RETURN"))
    private void planetaryExpansion$checkStorm(CallbackInfo ci) {
        if (level == null || level.isClientSide || level.getGameTime() % 20 != 0) return;
        BlockState state = getBlockState();
        Direction facing = state.hasProperty(BlockStateProperties.FACING) ? state.getValue(BlockStateProperties.FACING) : null;
        float target = StormManager.windmillMultiplier(level, getBlockPos(), facing);
        if (Math.abs(target - planetaryExpansion$stormMultiplier) < 0.05f && !(target == 1f && planetaryExpansion$stormMultiplier != 1f)) {
            return;
        }
        if ((target == 1f) != (planetaryExpansion$stormMultiplier == 1f)) {
            PlanetaryExpansion.LOGGER.info("[Clima] Molino en {} {}: x{}", level.dimension().location(),
                    getBlockPos().toShortString(), String.format("%.2f", target));
        }
        planetaryExpansion$stormMultiplier = target;
        if (running && movedContraption != null) {
            updateGeneratedRotation();
        }
    }
}
