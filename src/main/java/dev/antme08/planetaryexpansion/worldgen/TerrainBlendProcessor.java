package dev.antme08.planetaryexpansion.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Funde con el terreno las estructuras que el usuario construye sobre una losa de arena (0.7.45). En 0.7.44 la losa
 * cuadrada de arena roja asomaba sobre el terreno (en laderas y en zonas donde Marte no es de arena roja) y se veía
 * como una plataforma naranja.
 *
 * <ul>
 *   <li>La losa (las {@code slab} capas de abajo, del bloque {@code blend}) no se pone nunca: queda el terreno
 *       natural. El aire dentro de la losa (cráteres) solo excava por debajo de la superficie.</li>
 *   <li>Los montoncitos de arena por encima de la losa (la arena que tapa los restos) se ponen con el bloque de la
 *       superficie de esa columna, así que son del mismo color que el suelo de alrededor.</li>
 *   <li>El resto de bloques de la estructura, tal cual.</li>
 * </ul>
 */
public class TerrainBlendProcessor extends StructureProcessor {
    public static final DeferredRegister<StructureProcessorType<?>> PROCESSORS =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, PlanetaryExpansion.MODID);

    public static final MapCodec<TerrainBlendProcessor> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("slab").forGetter(p -> p.slab),
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("blend").forGetter(p -> p.blend),
            Codec.INT.optionalFieldOf("foundation", 0).forGetter(p -> p.foundation)
    ).apply(i, TerrainBlendProcessor::new));

    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<TerrainBlendProcessor>> TYPE =
            PROCESSORS.register("terrain_blend", () -> () -> CODEC);

    public static void register(IEventBus modBus) {
        PROCESSORS.register(modBus);
    }

    private final int slab;
    private final Block blend;
    private final int foundation; // 0.7.47: profundidad máxima del relleno bajo la estructura (0 = sin cimientos)
    private static final double SLOPE = 2.0; // bloques que baja el talud por cada bloque de distancia

    public TerrainBlendProcessor(int slab, Block blend, int foundation) {
        this.slab = slab;
        this.blend = blend;
        this.foundation = foundation;
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos,
                                                             StructureTemplate.StructureBlockInfo local,
                                                             StructureTemplate.StructureBlockInfo world,
                                                             StructurePlaceSettings settings) {
        // la losa (y < slab) se resuelve en finalizeProcessing: hace falta para saber el contorno de la plantilla
        if (local.pos().getY() < slab) return world;
        BlockState s = world.state();
        if (s.is(blend)) {
            BlockPos p = world.pos();
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, p.getX(), p.getZ()); // primer hueco libre
            BlockState ground = level.getBlockState(new BlockPos(p.getX(), top - 1, p.getZ()));
            return new StructureTemplate.StructureBlockInfo(p, groundOr(ground, s), null);
        }
        return world;
    }

    private BlockState groundOr(BlockState ground, BlockState fallback) {
        if (ground.isAir() || !ground.getFluidState().isEmpty() || !ground.isSolid() || ground.hasBlockEntity()) return fallback;
        return ground;
    }

    /**
     * 0.7.47: cimientos. En los bordes de un barranco o una ladera fuerte, parte de la estructura quedaba flotando.
     * Debajo de cada columna de la estructura se rellena hasta el suelo con el bloque de la superficie de esa columna
     * (como arena acumulada) y alrededor, dentro del contorno de la plantilla, se hace un talud que baja 2 bloques por
     * cada bloque de distancia, para que no quede un pilar recto.
     */
    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level, BlockPos offset, BlockPos pos,
                                                                         List<StructureTemplate.StructureBlockInfo> original,
                                                                         List<StructureTemplate.StructureBlockInfo> processed,
                                                                         StructurePlaceSettings settings) {
        BoundingBox box = settings.getBoundingBox();
        List<StructureTemplate.StructureBlockInfo> out = new ArrayList<>(processed.size());
        Set<Long> footprint = new HashSet<>();
        Map<Long, Integer> bottom = new HashMap<>(); // columna -> y del bloque más bajo de la estructura
        Map<Long, Integer> tops = new HashMap<>();
        for (int i = 0; i < processed.size(); i++) {
            StructureTemplate.StructureBlockInfo w = processed.get(i);
            BlockPos p = w.pos();
            long col = ChunkPos.asLong(p.getX(), p.getZ());
            footprint.add(col);
            boolean inside = box == null || (p.getX() >= box.minX() && p.getX() <= box.maxX() && p.getZ() >= box.minZ() && p.getZ() <= box.maxZ());
            if (i < original.size() && original.get(i).pos().getY() < slab) {
                BlockState s = w.state();
                if (s.is(blend)) continue; // la losa nunca: se queda el terreno natural
                if (s.isAir()) {
                    if (!inside) continue; // fuera de este chunk no se coloca nada de todos modos
                    int top = tops.computeIfAbsent(col, c -> level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, p.getX(), p.getZ()));
                    if (p.getY() >= top) continue; // por encima del suelo no hay nada que excavar
                    out.add(w);
                    continue;
                }
            }
            out.add(w);
            if (!w.state().isAir()) bottom.merge(col, p.getY(), Math::min);
        }
        if (box == null || foundation <= 0 || bottom.isEmpty()) return out;

        List<long[]> cols = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : bottom.entrySet())
            cols.add(new long[]{ChunkPos.getX(e.getKey()), ChunkPos.getZ(e.getKey()), e.getValue()});
        for (long col : footprint) {
            int x = ChunkPos.getX(col), z = ChunkPos.getZ(col);
            if (x < box.minX() || x > box.maxX() || z < box.minZ() || z > box.maxZ()) continue;
            Integer b = bottom.get(col);
            int target;
            if (b != null) target = b - 1;
            else {
                double best = Double.NEGATIVE_INFINITY;
                for (long[] c : cols) {
                    double d = Math.sqrt((c[0] - x) * (c[0] - x) + (c[1] - z) * (c[1] - z));
                    best = Math.max(best, c[2] - 1 - SLOPE * d);
                }
                target = (int) Math.floor(best);
            }
            int groundTop = tops.computeIfAbsent(col, c -> level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z)) - 1;
            if (target <= groundTop) continue;
            BlockState fill = groundOr(level.getBlockState(new BlockPos(x, groundTop, z)), blend.defaultBlockState());
            for (int y = Math.max(groundTop + 1, target - foundation + 1); y <= target; y++)
                out.add(new StructureTemplate.StructureBlockInfo(new BlockPos(x, y, z), fill, null));
        }
        return out;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return TYPE.get();
    }
}
