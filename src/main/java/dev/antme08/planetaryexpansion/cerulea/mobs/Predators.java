package dev.antme08.planetaryexpansion.cerulea.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Miedo a la luz de los depredadores de Cerúlea (acechador y corredor, idea del usuario): no se acercan a la luz de
 * antorchas, fogatas, lámparas… (luz de bloque 10 o más) ni a un jugador que lleve una fuente de luz en la mano, y si
 * están en un sitio iluminado se van a la oscuridad. La luz del sol no cuenta.
 */
public final class Predators {
    private Predators() {}

    public static final int LIGHT = 10;

    public static boolean inLight(Level level, BlockPos pos) {
        return level.getBrightness(LightLayer.BLOCK, pos) >= LIGHT;
    }

    private static boolean glows(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi && bi.getBlock().defaultBlockState().getLightEmission() >= LIGHT;
    }

    /** ¿Está protegido por la luz este ser? (en un sitio iluminado o con una luz en la mano) */
    public static boolean lit(LivingEntity e) {
        if (e == null) return false;
        if (inLight(e.level(), e.blockPosition())) return true;
        return glows(e.getItemInHand(InteractionHand.MAIN_HAND)) || glows(e.getItemInHand(InteractionHand.OFF_HAND));
    }

    /** Un jugador con una luz en la mano, cerca. */
    public static Player litPlayerNear(PathfinderMob mob, double range) {
        List<Player> ps = mob.level().getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(range),
                p -> !p.isSpectator() && lit(p));
        return ps.isEmpty() ? null : ps.get(0);
    }

    /** Huye de la luz: si está iluminado o hay un jugador con luz cerca, corre a un sitio oscuro y lejos de la luz. */
    public static class AvoidLightGoal extends Goal {
        private final PathfinderMob mob;
        private final double speed;
        private Vec3 from;

        public AvoidLightGoal(PathfinderMob mob, double speed) {
            this.mob = mob;
            this.speed = speed;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private boolean afraid() {
            if (mob instanceof Runner r && r.isTame()) return false;
            if (inLight(mob.level(), mob.blockPosition())) {
                from = mob.position();
                return true;
            }
            Player p = litPlayerNear(mob, 7.0);
            if (p != null) {
                from = p.position();
                return true;
            }
            return false;
        }

        @Override
        public boolean canUse() {
            if (mob.tickCount % 5 != 0 || !afraid()) return false;
            return pickTarget();
        }

        private boolean pickTarget() {
            for (int i = 0; i < 10; i++) {
                Vec3 v = LandRandomPos.getPosAway(mob, 14, 5, from);
                if (v != null && !inLight(mob.level(), BlockPos.containing(v))) {
                    mob.setTarget(null);
                    return mob.getNavigation().moveTo(v.x, v.y, v.z, speed);
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() { return !mob.getNavigation().isDone(); }
    }
}
