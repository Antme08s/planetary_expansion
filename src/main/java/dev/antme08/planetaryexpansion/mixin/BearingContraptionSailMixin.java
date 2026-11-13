package dev.antme08.planetaryexpansion.mixin;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import dev.antme08.planetaryexpansion.weather.WindSails;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Velas con distinta potencia en los molinos de Create (ver {@link WindSails}).
 *
 * Al montar las aspas, Create llama a addBlock por cada bloque y suma 1 a sailBlocks si está en
 * create:windmill_sails. Aquí, antes de eso, se suma la diferencia entre la potencia del bloque y lo
 * que Create va a contar (1 o 0). Las fracciones se acumulan y se pasan al contador cuando llegan a
 * una vela entera. El total se guarda en el NBT de la contrapción como siempre ("Sails").
 *
 * remap = false: las clases de Create no están ofuscadas.
 */
@Mixin(value = BearingContraption.class, remap = false)
public abstract class BearingContraptionSailMixin extends Contraption {
    @Shadow
    protected int sailBlocks;

    @Unique
    private double planetaryExpansion$sailRemainder;

    @Inject(method = "addBlock", at = @At("HEAD"))
    private void planetaryExpansion$weightedSail(Level level, BlockPos pos,
                                                 Pair<StructureTemplate.StructureBlockInfo, BlockEntity> pair,
                                                 CallbackInfo ci) {
        if (pair == null || pair.getKey() == null || anchor == null) return;
        if (getBlocks().containsKey(pos.subtract(anchor))) return; // Create tampoco lo cuenta dos veces
        BlockState state = pair.getKey().state();
        double power = WindSails.power(state);
        if (power < 0) return;
        double countedByCreate = state.is(WindSails.CREATE_SAILS) ? 1.0 : 0.0;
        planetaryExpansion$sailRemainder += power - countedByCreate;
        int whole = (int) Math.floor(planetaryExpansion$sailRemainder);
        if (whole != 0) {
            sailBlocks += whole;
            planetaryExpansion$sailRemainder -= whole;
        }
    }
}
