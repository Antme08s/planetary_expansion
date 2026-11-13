package dev.antme08.planetaryexpansion.cerulea;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Árboles de Cerúlea que no se pueden hacer con el árbol de Minecraft (1.0.3):
 * - "silverwood": el gigante del bosque gigante. Tronco plateado de 3×3 y 26–36 de alto con raíces a los lados, y la
 *   copa azul en tres pisos anchos y planos (como sombrillas), cada uno sostenido por ramas.
 * - "mirewood": el del pantano. Raíces en arco que salen de las cuatro esquinas, tronco fino, copa ancha y lianas
 *   colgando. Puede crecer en agua poco profunda.
 * Las hojas quedan con la distancia correcta al tronco (se calcula al final), así se caen solas si se tala el árbol.
 */
public class CeruleaTreeFeature extends Feature<CeruleaTreeFeature.Config> {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, PlanetaryExpansion.MODID);
    public static final DeferredHolder<Feature<?>, CeruleaTreeFeature> TREE = FEATURES.register("cerulea_tree", CeruleaTreeFeature::new);

    public record Config(String variant) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("variant").forGetter(Config::variant)).apply(i, Config::new));
    }

    public CeruleaTreeFeature() {
        super(Config.CODEC);
    }

    private final class Builder {
        final WorldGenLevel level;
        final Map<BlockPos, BlockState> logs = new HashMap<>();
        final Set<BlockPos> leaves = new HashSet<>();
        final Map<BlockPos, BlockState> other = new HashMap<>();

        Builder(WorldGenLevel level) { this.level = level; }

        boolean free(BlockPos p) {
            BlockState s = level.getBlockState(p);
            return s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.getFluidState().isSource() && s.getBlock() == net.minecraft.world.level.block.Blocks.WATER;
        }

        void log(BlockPos p, BlockState s) {
            if (p.getY() >= level.getMaxBuildHeight() - 1) return;
            BlockState cur = level.getBlockState(p);
            if (free(p) || cur.is(BlockTags.DIRT) || cur.is(BlockTags.SAND) || cur.is(BlockTags.LOGS)) logs.put(p.immutable(), s);
            leaves.remove(p);
        }

        void leaf(BlockPos p) {
            if (p.getY() >= level.getMaxBuildHeight() - 1 || logs.containsKey(p)) return;
            BlockState cur = level.getBlockState(p);
            if (cur.isAir() || cur.canBeReplaced() && cur.getFluidState().isEmpty()) leaves.add(p.immutable());
        }

        void place(BlockState leaf) {
            for (var e : logs.entrySet()) level.setBlock(e.getKey(), e.getValue(), 2 | 16);
            // distancia de cada hoja al tronco (hasta 7): igual que hace el árbol de Minecraft
            Map<BlockPos, Integer> dist = new HashMap<>();
            ArrayDeque<BlockPos> q = new ArrayDeque<>();
            for (BlockPos p : logs.keySet()) {
                dist.put(p, 0);
                q.add(p);
            }
            while (!q.isEmpty()) {
                BlockPos p = q.poll();
                int d = dist.get(p);
                if (d >= 6) continue;
                for (Direction dir : Direction.values()) {
                    BlockPos n = p.relative(dir);
                    if (leaves.contains(n) && !dist.containsKey(n)) {
                        dist.put(n, d + 1);
                        q.add(n);
                    }
                }
            }
            for (BlockPos p : leaves) {
                int d = dist.getOrDefault(p, 7);
                BlockState s = leaf.setValue(LeavesBlock.DISTANCE, d).setValue(LeavesBlock.PERSISTENT, d >= 7);
                level.setBlock(p, s, 2 | 16);
            }
            for (var e : other.entrySet()) level.setBlock(e.getKey(), e.getValue(), 2 | 16);
        }
    }

    private static BlockState axis(BlockState log, Direction.Axis a) {
        return log.setValue(RotatedPillarBlock.AXIS, a);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        BlockPos o = ctx.origin();
        BlockState below = level.getBlockState(o.below());
        boolean swamp = "mirewood".equals(ctx.config().variant());
        if (!below.is(BlockTags.DIRT) && !(swamp && (below.is(BlockTags.SAND) || below.is(CeruleaBlocks.MUD.get())))) return false;
        return swamp ? mirewood(level, r, o) : silverwood(level, r, o);
    }

    private boolean silverwood(WorldGenLevel level, RandomSource r, BlockPos o) {
        int h = 26 + r.nextInt(11);
        if (o.getY() + h + 3 >= level.getMaxBuildHeight()) return false;
        Builder b = new Builder(level);
        BlockState log = CeruleaBlocks.SILVERWOOD_LOG.get().defaultBlockState();
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (!b.free(o.offset(dx, 1, dz)) && !level.getBlockState(o.offset(dx, 1, dz)).is(BlockTags.LOGS)) return false;
            }
        for (int y = 0; y < h; y++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) b.log(o.offset(dx, y, dz), axis(log, Direction.Axis.Y));
        for (Direction d : Direction.Plane.HORIZONTAL) {                  // raíces: contrafuerte y pie que se hunde
            for (int y = 0; y < 2 + r.nextInt(3); y++) b.log(o.relative(d, 2).above(y), axis(log, Direction.Axis.Y));
            b.log(o.relative(d, 3), axis(log, d.getAxis()));
            b.log(o.relative(d, 3).below(), axis(log, Direction.Axis.Y));
            b.log(o.relative(d, 2).below(), axis(log, Direction.Axis.Y));
        }
        int[][] layers = {{h - 14, 8}, {h - 7, 6}, {h, 4}};
        for (int li = 0; li < layers.length; li++) {
            int ly = layers[li][0];
            double rad = layers[li][1] + r.nextDouble() * 1.2;
            BlockPos c = o.above(ly);
            for (int dx = -(int) rad - 1; dx <= rad + 1; dx++)
                for (int dz = -(int) rad - 1; dz <= rad + 1; dz++) {
                    double d2 = dx * dx + dz * dz;
                    if (d2 <= rad * rad) {
                        b.leaf(c.offset(dx, 0, dz));
                        if (d2 <= (rad - 1.6) * (rad - 1.6)) b.leaf(c.offset(dx, 1, dz));
                        if (d2 <= (rad - 3.5) * (rad - 3.5) && li == 2) b.leaf(c.offset(dx, 2, dz));
                    } else if (d2 <= (rad + 1) * (rad + 1) && r.nextInt(3) == 0) {
                        b.leaf(c.offset(dx, -1, dz));                     // borde caído
                    }
                }
            int n = 6;
            double rot = r.nextDouble() * Math.PI;
            for (int k = 0; k < n; k++) {                                // ramas que sostienen el piso
                double a = rot + k * Math.PI * 2 / n;
                double ax = Math.cos(a), az = Math.sin(a);
                Direction.Axis axs = Math.abs(ax) > Math.abs(az) ? Direction.Axis.X : Direction.Axis.Z;
                for (int t = 2; t <= Math.max(2, (int) rad - 2); t++)
                    b.log(c.offset((int) Math.round(ax * t), -1, (int) Math.round(az * t)), axis(log, axs));
            }
        }
        b.place(CeruleaBlocks.SILVERWOOD_LEAVES.get().defaultBlockState());
        return true;
    }

    private boolean mirewood(WorldGenLevel level, RandomSource r, BlockPos o) {
        int h = 8 + r.nextInt(4);
        if (o.getY() + h + 3 >= level.getMaxBuildHeight()) return false;
        Builder b = new Builder(level);
        BlockState log = CeruleaBlocks.MIREWOOD_LOG.get().defaultBlockState();
        // raíces en arco desde las cuatro esquinas hasta el tronco, que empieza 3 bloques arriba
        for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            b.log(o.offset(c[0] * 2, 0, c[1] * 2), axis(log, Direction.Axis.Y));
            b.log(o.offset(c[0] * 2, -1, c[1] * 2), axis(log, Direction.Axis.Y));
            b.log(o.offset(c[0] * 2, 1, c[1] * 2), axis(log, Direction.Axis.Y));
            b.log(o.offset(c[0], 2, c[1]), axis(log, Direction.Axis.Y));
        }
        for (int y = 2; y < h; y++) b.log(o.above(y), axis(log, Direction.Axis.Y));
        BlockPos top = o.above(h);
        double rad = 3.6 + r.nextDouble() * 1.2;
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++)
                for (int dy = -1; dy <= 1; dy++) {
                    double d = (dx * dx + dz * dz) / (rad * rad) + dy * dy / 3.2;
                    if (d <= 1) b.leaf(top.offset(dx, dy, dz));
                }
        for (int k = 0; k < 4; k++) {                                   // ramas cortas hacia la copa
            Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(r);
            b.log(top.relative(d).below(), axis(log, d.getAxis()));
            b.log(top.relative(d, 2).below(), axis(log, d.getAxis()));
        }
        b.place(CeruleaBlocks.MIREWOOD_LEAVES.get().defaultBlockState());
        // lianas que cuelgan del borde inferior de la copa
        BlockState vine = CeruleaBlocks.MIRE_VINE.get().defaultBlockState();
        for (int i = 0; i < 18; i++) {
            BlockPos p = top.offset(r.nextInt(9) - 4, -1, r.nextInt(9) - 4);
            while (p.getY() < top.getY() + 2 && !level.getBlockState(p.above()).is(BlockTags.LEAVES)) p = p.above();
            if (!level.getBlockState(p.above()).is(BlockTags.LEAVES) || !level.isEmptyBlock(p)) continue;
            int len = 2 + r.nextInt(5);
            for (int k = 0; k < len && level.isEmptyBlock(p); k++) {
                level.setBlock(p, vine, 2);
                p = p.below();
            }
        }
        return true;
    }
}
