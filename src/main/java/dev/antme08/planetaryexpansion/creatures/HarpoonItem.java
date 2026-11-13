package dev.antme08.planetaryexpansion.creatures;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Arpón (0.7.61): se carga como el tridente y se lanza; bajo el agua casi no pierde velocidad. Hecho con colmillo abisal. */
public class HarpoonItem extends Item {
    public HarpoonItem(Properties props) {
        super(props);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.SPEAR; }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getDamageValue() >= stack.getMaxDamage() - 1) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        if (getUseDuration(stack, entity) - timeLeft < 10) return;
        if (!level.isClientSide) {
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
            HarpoonEntity harpoon = new HarpoonEntity(level, player, stack.copyWithCount(1));
            harpoon.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 2.6F, 1.0F);
            if (player.hasInfiniteMaterials()) harpoon.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            level.addFreshEntity(harpoon);
            level.playSound(null, harpoon, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
            if (!player.hasInfiniteMaterials()) player.getInventory().removeItem(stack);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(Creatures.ABYSSAL_FANG.get());
    }

    @Override
    public int getEnchantmentValue() { return 1; }
}
