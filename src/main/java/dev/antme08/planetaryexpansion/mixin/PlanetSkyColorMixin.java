package dev.antme08.planetaryexpansion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.antme08.planetaryexpansion.client.UniverseSky;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Color del cielo de día en los planetas con atmósfera (Marte, Thalassa, Venus) con el cielo personalizado de
 * Cosmonautics (0.7.52). Con {@code enableCustomSky = true}, {@code ClientLevelMixin.darkenSkyAtAltitude} de
 * Cosmonautics pone el cielo NEGRO en toda dimensión cuya hora controla el Sol (pensado para la Luna). En Marte de día
 * quedaba el cenit negro y una niebla gris (la niebla se mezcla con el color del cielo); con shaders, Complementary
 * también lo lee y se veía gris. Aquí, en esos planetas, el color se calcula como en Minecraft (color del bioma por la
 * luz del día, más el destello de los rayos) y por encima de 1000 m se oscurece hacia el negro como hace Cosmonautics.
 * {@code @WrapMethod} envuelve el método entero, también la inyección de Cosmonautics, que ahí no se llama.
 */
@Mixin(ClientLevel.class)
public abstract class PlanetSkyColorMixin {
    @WrapMethod(method = "getSkyColor")
    private Vec3 planetaryExpansion$atmosphereSky(Vec3 pos, float partialTick, Operation<Vec3> original) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (!UniverseSky.ATMOSPHERE_DIMS.contains(level.dimension().location().toString()))
            return original.call(pos, partialTick);

        float tod = level.getTimeOfDay(partialTick);
        BiomeManager biomes = level.getBiomeManager();
        Vec3 c = CubicSampler.gaussianSampleVec3(pos.subtract(2.0, 2.0, 2.0).scale(0.25),
                (x, y, z) -> Vec3.fromRGB24(biomes.getNoiseBiomeAtQuart(x, y, z).value().getSkyColor()));
        float light = Mth.clamp(Mth.cos(tod * ((float) Math.PI * 2F)) * 2.0F + 0.5F, 0.0F, 1.0F);
        float r = (float) c.x * light, g = (float) c.y * light, b = (float) c.z * light;
        int flash = level.getSkyFlashTime();
        if (flash > 0) {
            float f = Math.min(1.0F, flash - partialTick) * 0.45F;
            r = r * (1.0F - f) + 0.8F * f;
            g = g * (1.0F - f) + 0.8F * f;
            b = b * (1.0F - f) + 1.0F * f;
        }
        float dark = (float) Mth.clamp((pos.y - 1000.0) / 2000.0, 0.0, 1.0); // como Cosmonautics al subir
        return new Vec3(r * (1 - dark), g * (1 - dark), b * (1 - dark));
    }
}
