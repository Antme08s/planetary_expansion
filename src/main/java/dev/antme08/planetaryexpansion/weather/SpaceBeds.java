package dev.antme08.planetaryexpansion.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

/**
 * Camas fuera de la Tierra (0.7.41, petición del usuario).
 *
 * <ul>
 *   <li>En ningún planeta ni en el espacio explotan (mixin/BedBlockMixin).</li>
 *   <li>Te puedes acostar a cualquier hora. La noche no se salta: el día de cada planeta lo marca su Sol real en
 *       Cosmonautics (no la hora de Minecraft), así que al rato te levantas como si nada.</li>
 *   <li>En los planetas (Luna, Marte, Venus, mundo helado, Ignis, Thalassa) la cama además guarda el punto de
 *       reaparición, como en la Tierra.</li>
 *   <li>0.7.67 (petición del usuario): también en el espacio profundo guarda el punto de reaparición. Sable 2.0.6
 *       ya sabe usar camas en naves (distancia medida en la nave, acostarse, y la reaparición sigue a la nave aunque
 *       se mueva). Como Cosmonautics marca el espacio como "no natural", {@code mixin/SpaceBedNaturalMixin} hace que
 *       Minecraft lo trate como natural solo para acostarse; si ese mixin no se aplicara, aquí se guarda a mano.</li>
 *   <li>Nunca se salta la noche ni cambia la hora ({@link #onSleepFinished}): te acuestas, se guarda la cama y al
 *       rato te levantas.</li>
 * </ul>
 * Siguen valiendo los avisos normales: cama demasiado lejos, tapada o monstruos cerca.
 */
public final class SpaceBeds {
    private SpaceBeds() {}

    public static boolean isSpaceOrPlanet(Level level) {
        if (level == null) return false;
        String ns = level.dimension().location().getNamespace();
        return ns.equals("rocketnautics") || ns.equals("planetary_expansion");
    }

    private static boolean onlyLieDown(Player.BedSleepingProblem p) {
        // "aquí no se puede" (espacio: dimensión no natural) y "solo de noche"
        return p == Player.BedSleepingProblem.NOT_POSSIBLE_HERE || p == Player.BedSleepingProblem.NOT_POSSIBLE_NOW;
    }

    @SubscribeEvent
    public static void onCanSleep(CanPlayerSleepEvent event) {
        if (!isSpaceOrPlanet(event.getLevel())) return;
        if (event.getProblem() == Player.BedSleepingProblem.NOT_POSSIBLE_HERE) {
            // Solo llega aquí si SpaceBedNaturalMixin no se aplicó: Minecraft no comprobó si la cama está tapada ni
            // guardó la reaparición. Se hace aquí (el alcance ya lo limita la distancia para usar la cama).
            BlockPos pos = event.getPos();
            Level level = event.getLevel();
            if (level.getBlockState(pos.above()).isSuffocating(level, pos.above())) {
                event.setProblem(Player.BedSleepingProblem.OBSTRUCTED);
                return;
            }
            event.getEntity().setRespawnPosition(level.dimension(), pos, event.getEntity().getYRot(), false, true);
        }
        if (onlyLieDown(event.getProblem())) event.setProblem(null);
    }

    /** Dormir fuera de la Tierra nunca adelanta la hora (pedido del usuario: solo acostarse y guardar la cama). */
    @SubscribeEvent
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (event.getLevel() instanceof Level level && isSpaceOrPlanet(level)) event.setTimeAddition(level.getDayTime());
    }

    @SubscribeEvent
    public static void onContinueSleeping(CanContinueSleepingEvent event) {
        if (!isSpaceOrPlanet(event.getEntity().level())) return;
        if (event.getProblem() == null || onlyLieDown(event.getProblem())) event.setContinueSleeping(true);
    }
}
