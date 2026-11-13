package dev.antme08.planetaryexpansion.worldgen;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Mundo helado sin lava (0.7.42, petición del usuario). Con acuíferos, Minecraft llena de lava los huecos por debajo de
 * y -54; en el mundo helado esos huecos quedan vacíos (mixin/ThalassaFluidPickerMixin). Aquí se guarda cuál es la
 * configuración de terreno del mundo helado para reconocerla.
 */
public final class IceWorldFluids {
    private IceWorldFluids() {}

    public static volatile NoiseGeneratorSettings SETTINGS;

    /** Selector de fluido del mundo helado: donde Minecraft pondría lava, nada. */
    public record NoLava(net.minecraft.world.level.levelgen.Aquifer.FluidPicker original, int minY)
            implements net.minecraft.world.level.levelgen.Aquifer.FluidPicker {
        @Override
        public net.minecraft.world.level.levelgen.Aquifer.FluidStatus computeFluid(int x, int y, int z) {
            var s = original.computeFluid(x, y, z);
            return s.at(y).is(net.minecraft.world.level.block.Blocks.LAVA)
                    ? new net.minecraft.world.level.levelgen.Aquifer.FluidStatus(minY - 1, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())
                    : s;
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerAboutToStartEvent event) {
        SETTINGS = event.getServer().registryAccess().registryOrThrow(Registries.NOISE_SETTINGS)
                .get(ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "ice_world"));
    }
}
