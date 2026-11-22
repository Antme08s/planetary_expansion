package dev.antme08.planetaryexpansion.warp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Huella de los bloques grandes del motor de distorsión (4x4x3) y la consola de navegación (2x2x1).
 *
 * El bloque que pone el jugador es el ancla. Las celdas son {@code ancla + a·d + b·r + y·arriba}, con d = hacia donde
 * mira el jugador al ponerlo y r = d girado a la izquierda (el giro antihorario de Minecraft). Así el eje +z del
 * modelo va en la dirección d y el +x en la dirección r, con un simple giro en Y.
 *
 * Los bloques de relleno guardan su desplazamiento hasta el ancla en tres propiedades (OX, OY, OZ), así cada parte
 * sabe a qué ancla pertenece sin necesitar BlockEntity (y sigue funcionando dentro de una nave de Sable).
 */
public final class Footprint {
    private Footprint() {}

    public static final IntegerProperty OX = IntegerProperty.create("ox", 0, 6);
    public static final IntegerProperty OY = IntegerProperty.create("oy", 0, 2);
    public static final IntegerProperty OZ = IntegerProperty.create("oz", 0, 6);

    /** Celda: a hacia delante, b hacia la izquierda, y hacia arriba. */
    public record Cell(int a, int b, int y) {}

    /** Motor: 4 de fondo (a 0..3), 4 de ancho (b -1..2: el ancla queda casi en medio del frente) y 3 de alto. */
    public static final List<Cell> DRIVE = cells(0, 3, -1, 2, 0, 2);
    /** Consola: 2x2, 1 de alto. */
    public static final List<Cell> CONSOLE = cells(0, 1, 0, 1, 0, 0);

    /** Entradas de eje del motor: en la segunda fila, a los dos lados, abajo. */
    public static final Cell INPUT_LEFT = new Cell(1, 2, 0);
    public static final Cell INPUT_RIGHT = new Cell(1, -1, 0);

    private static List<Cell> cells(int a0, int a1, int b0, int b1, int y0, int y1) {
        List<Cell> out = new ArrayList<>();
        for (int y = y0; y <= y1; y++)
            for (int a = a0; a <= a1; a++)
                for (int b = b0; b <= b1; b++)
                    if (a != 0 || b != 0 || y != 0) out.add(new Cell(a, b, y));
        return List.copyOf(out);
    }

    public static Direction left(Direction facing) {
        return facing.getCounterClockWise();
    }

    public static BlockPos pos(BlockPos anchor, Direction facing, Cell c) {
        return anchor.relative(facing, c.a()).relative(left(facing), c.b()).above(c.y());
    }

    /** Pone en el estado de una parte su desplazamiento hasta el ancla. */
    public static BlockState withOffset(BlockState state, BlockPos anchor, BlockPos part) {
        BlockState s = state.setValue(OX, part.getX() - anchor.getX() + 3).setValue(OZ, part.getZ() - anchor.getZ() + 3);
        if (s.hasProperty(OY)) s = s.setValue(OY, part.getY() - anchor.getY());
        return s;
    }


    /**
     * Gira el desplazamiento hasta el ancla (OX/OZ) igual que Minecraft gira posiciones (CLOCKWISE_90: (x,z) -> (-z,x)).
     * Sable llama a rotate() al ensamblar/desensamblar una nave con giro; sin esto las partes apuntan a un ancla
     * que ya no está donde dicen y el modelo se ve desplazado respecto a la hitbox.
     */
    public static BlockState rotateOffset(BlockState state, Rotation rotation) {
        if (!state.hasProperty(OX) || !state.hasProperty(OZ)) return state;
        int x = state.getValue(OX) - 3, z = state.getValue(OZ) - 3;
        int nx, nz;
        switch (rotation) {
            case CLOCKWISE_90 -> { nx = -z; nz = x; }
            case CLOCKWISE_180 -> { nx = -x; nz = -z; }
            case COUNTERCLOCKWISE_90 -> { nx = z; nz = -x; }
            default -> { return state; }
        }
        return state.setValue(OX, nx + 3).setValue(OZ, nz + 3);
    }

    public static BlockPos anchorOf(BlockPos part, BlockState state) {
        int oy = state.hasProperty(OY) ? state.getValue(OY) : 0;
        return part.offset(-(state.getValue(OX) - 3), -oy, -(state.getValue(OZ) - 3));
    }

    /** Celda (a, b, y) de una parte, conocida la orientación del ancla. */
    public static Cell cellOf(BlockPos part, BlockState state, Direction facing) {
        BlockPos anchor = anchorOf(part, state);
        int dx = part.getX() - anchor.getX(), dy = part.getY() - anchor.getY(), dz = part.getZ() - anchor.getZ();
        Direction r = left(facing);
        int a = dx * facing.getStepX() + dz * facing.getStepZ();
        int b = dx * r.getStepX() + dz * r.getStepZ();
        return new Cell(a, b, dy);
    }
}
