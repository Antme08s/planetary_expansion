package dev.antme08.planetaryexpansion.worldgen;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Ríos de lava de Venus (0.7.7, idea del usuario): un respiradero en la falda alta de un volcán en escudo.
 * Se pone una fuente de lava en el bloque de la superficie, con un borde de piedra negra, y se programa su tick:
 * al cargar el chunk la lava baja sola por la pendiente y forma el río (como la lava normal de Minecraft).
 * Solo donde la superficie está entre y 86 y 106 (faldas altas y borde de la caldera) y hay pendiente cuesta abajo.
 */
public class VenusLavaVentFeature extends Feature<NoneFeatureConfiguration> {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, PlanetaryExpansion.MODID);
    public static final DeferredHolder<Feature<?>, VenusLavaVentFeature> LAVA_VENT =
            FEATURES.register("venus_lava_vent", () -> new VenusLavaVentFeature());

    public static void register(IEventBus modBus) {
        ThalassaVentFeature.touch(); // carga la clase: añade "thalassa_vent" al mismo registro antes de registrarlo
        NitrogenPocketFeature.touch(); // "nitrogen_pocket" (0.7.42)
        MeteoriteCraterFeature.touch(); // "meteorite_crater" (0.7.46)
        FEATURES.register(modBus);
    }

    public VenusLavaVentFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        int x = origin.getX(), z = origin.getZ();
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        if (top < 86 || top > 106) return false;

        // Tiene que haber pendiente: algún lado 2 bloques más abajo a 2-3 bloques de distancia.
        Direction downhill = null;
        int lowest = top;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int h = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x + d.getStepX() * 3, z + d.getStepZ() * 3) - 1;
            if (h < lowest) {
                lowest = h;
                downhill = d;
            }
        }
        if (downhill == null || top - lowest < 2) return false;

        BlockPos vent = new BlockPos(x, top, z);
        BlockState blackstone = Blocks.BLACKSTONE.defaultBlockState();
        // Costra alrededor (solo donde ya hay roca) y magma debajo
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos side = vent.relative(d);
            if (!level.getBlockState(side).isAir() && level.getFluidState(side).isEmpty()) {
                level.setBlock(side, blackstone, 2);
            }
        }
        level.setBlock(vent.below(), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
        level.setBlock(vent, Blocks.LAVA.defaultBlockState(), 2);
        level.scheduleTick(vent, Fluids.LAVA, 1);
        return true;
    }
}
