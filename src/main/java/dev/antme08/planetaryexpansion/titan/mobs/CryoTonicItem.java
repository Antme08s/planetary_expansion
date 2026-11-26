package dev.antme08.planetaryexpansion.titan.mobs;

import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Tónico criogénico (1.0.4): escama criogénica de la anguila de metano + grasa de bitumen en un frasco. Se bebe y da
 * "Adaptación criogénica" 4 minutos: el frío de los planetas (Titán, Thalassa) te congela 4 veces más despacio. Se suma a
 * la armadura glacial y al calentador químico (ver armor/GlacialArmor.coldFactor).
 */
public class CryoTonicItem extends Item {
    public static final int DURATION = 20 * 60 * 4;

    public CryoTonicItem(Properties props) {
        super(props);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            entity.addEffect(new MobEffectInstance(TitanMobs.CRYO_ADAPTATION, DURATION, 0));
            entity.setTicksFrozen(0);
        }
        if (entity instanceof ServerPlayer sp) sp.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
        if (entity instanceof Player p && p.getAbilities().instabuild) return stack;
        stack.shrink(1);
        if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
        if (entity instanceof Player p && !p.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) p.drop(new ItemStack(Items.GLASS_BOTTLE), false);
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) { return 32; }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }

    @Override
    public SoundEvent getDrinkingSound() { return SoundEvents.HONEY_DRINK; }

    @Override
    public SoundEvent getEatingSound() { return SoundEvents.HONEY_DRINK; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.planetary_expansion.cryo_tonic.desc").withStyle(ChatFormatting.AQUA));
    }
}
