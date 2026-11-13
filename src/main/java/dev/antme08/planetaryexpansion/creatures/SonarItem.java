package dev.antme08.planetaryexpansion.creatures;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Sonar (0.7.61, hecho con el órgano sonar del acechador abisal): al usarlo marca con contorno brillante a todas las
 * criaturas a 48 bloques durante 10 s, también a través de la roca y la oscuridad. Se recarga en 10 s.
 */
public class SonarItem extends Item {
    private static final double RANGE = 48.0;

    public SonarItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE),
                    e -> e != player && e.isAlive() && e.distanceToSqr(player) < RANGE * RANGE);
            for (LivingEntity e : found) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
            player.displayClientMessage(Component.translatable("message.planetary_expansion.sonar", found.size()), true);
            level.playSound(null, player.blockPosition(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
            player.getCooldowns().addCooldown(this, 200);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
