package dev.antme08.planetaryexpansion.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;
import java.util.Optional;

/**
 * Cráter de meteorito de Marte (0.7.46). Las estructuras de meteorito de 0.7.44/0.7.45 salían muy enterradas o con
 * arena flotando; el usuario pidió que fueran como los cráteres que ya genera el planeta (el carver de Cosmonautics).
 * Ahora se excava un cuenco con forma de paraboloide a partir del suelo (de radio 4-8, un poco irregular) y en el
 * fondo se pone el núcleo del meteorito que construyó el usuario (basalto y el mineral de cada variante), sacado de su
 * plantilla sin la arena: medio clavado en el suelo y girado al azar. Solo se quita terreno: nunca queda nada flotando.
 */
public class MeteoriteCraterFeature extends Feature<MeteoriteCraterFeature.Config> {
    public static final DeferredHolder<Feature<?>, MeteoriteCraterFeature> CRATER =
            VenusLavaVentFeature.FEATURES.register("meteorite_crater", MeteoriteCraterFeature::new);

    static void touch() {}

    /** Plantillas de los núcleos (una se elige al azar; repetir una la hace más probable), punto de apoyo del núcleo. */
    public record Config(List<ResourceLocation> templates, BlockPos anchor, float radiusMin, float radiusMax, float depth)
            implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf().fieldOf("templates").forGetter(Config::templates),
                BlockPos.CODEC.fieldOf("anchor").forGetter(Config::anchor),
                Codec.FLOAT.fieldOf("radius_min").forGetter(Config::radiusMin),
                Codec.FLOAT.fieldOf("radius_max").forGetter(Config::radiusMax),
                Codec.FLOAT.fieldOf("depth").forGetter(Config::depth)
        ).apply(i, Config::new));
    }

    public MeteoriteCraterFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        Config c = ctx.config();
        BlockPos o = ctx.origin();
        int top0 = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, o.getX(), o.getZ()) - 1;
        if (top0 <= level.getMinBuildHeight() + 8) return false;
        BlockState centerGround = level.getBlockState(new BlockPos(o.getX(), top0, o.getZ()));
        if (!centerGround.getFluidState().isEmpty() || centerGround.isAir()) return false;

        float radius = c.radiusMin() + r.nextFloat() * Math.max(0f, c.radiusMax() - c.radiusMin());
        float depth = radius * c.depth() * (0.85f + 0.3f * r.nextFloat());
        float phase = r.nextFloat() * 6.283f, wobble = 0.08f + 0.08f * r.nextFloat();
        int ext = (int) Math.ceil(radius * (1 + wobble)) + 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -ext; dx <= ext; dx++)
            for (int dz = -ext; dz <= ext; dz++) {
                double ang = Math.atan2(dz, dx);
                double rr = radius * (1 + wobble * Math.sin(ang * 3 + phase) + 0.5 * wobble * Math.sin(ang * 5 - phase));
                double d = Math.sqrt(dx * dx + dz * dz) / rr;
                if (d >= 1.0) continue;
                int x = o.getX() + dx, z = o.getZ() + dz;
                int colTop = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                int floor = (int) Math.floor(top0 - depth * (1 - d * d)); // último bloque de suelo que queda
                for (int y = floor + 1; y <= colTop; y++) {
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || s.is(Blocks.BEDROCK) || !s.getFluidState().isEmpty()) continue;
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
            }

        // núcleo del meteorito: el bloque más bajo de la plantilla queda un bloque por debajo del fondo
        if (!c.templates().isEmpty()) {
            ResourceLocation id = c.templates().get(r.nextInt(c.templates().size()));
            Optional<StructureTemplate> template = level.getLevel().getStructureManager().get(id);
            if (template.isPresent()) {
                int floor0 = (int) Math.floor(top0 - depth);
                BlockPos target = new BlockPos(o.getX(), floor0, o.getZ());
                StructurePlaceSettings settings = new StructurePlaceSettings()
                        .setRotation(Rotation.getRandom(r))
                        .setRotationPivot(c.anchor())
                        .addProcessor(new BlockIgnoreProcessor(List.of(Blocks.RED_SAND, Blocks.AIR, Blocks.STRUCTURE_VOID)));
                template.get().placeInWorld(level, target.subtract(c.anchor()), target.subtract(c.anchor()), settings, r, 2);
            }
        }
        return true;
    }
}
