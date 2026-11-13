package dev.antme08.planetaryexpansion.armor;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Kits de supervivencia (0.7.66): consumibles que se fabrican en la Tierra para la primera expedición a un planeta,
 * cuando todavía no tienes su traje. Durante 3 minutos el peligro del planeta te afecta solo al 40 % (−60 %). A
 * propósito no dan inmunidad (lo pidió el usuario), para no quitarle el sentido a las armaduras; se suman a la
 * protección de la armadura que lleves.
 *  - gel refrigerante: calor de Venus (VenusHazards);
 *  - calentador químico: frío del mundo helado y de Thalassa, ventiscas incluidas (GlacialArmor.coldFactor);
 *  - filtro de polvo: tormentas de arena de Marte (ImpactArmor.dustStormFactor).
 */
public final class SurvivalKits {
    private SurvivalKits() {}

    /** Parte del peligro que sigue llegando con el kit puesto. */
    public static final float KIT_FACTOR = 0.4f;
    /** 3 minutos. */
    public static final int DURATION = 3 * 60 * 20;

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, PlanetaryExpansion.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PlanetaryExpansion.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> COOLED = EFFECTS.register("cooled", () -> new KitEffect(0x6fd4ff));
    public static final DeferredHolder<MobEffect, MobEffect> WARMED = EFFECTS.register("warmed", () -> new KitEffect(0xff9a3c));
    public static final DeferredHolder<MobEffect, MobEffect> FILTERED = EFFECTS.register("filtered", () -> new KitEffect(0xc9b08a));

    public static final DeferredItem<KitItem> COOLING_GEL = ITEMS.register("cooling_gel",
            () -> new KitItem(COOLED, UseAnim.DRINK, 32, SoundEvents.HONEY_DRINK, "cooling_gel"));
    public static final DeferredItem<KitItem> CHEMICAL_HEATER = ITEMS.register("chemical_heater",
            () -> new KitItem(WARMED, UseAnim.BOW, 20, SoundEvents.FIRECHARGE_USE, "chemical_heater"));
    public static final DeferredItem<KitItem> DUST_FILTER = ITEMS.register("dust_filter",
            () -> new KitItem(FILTERED, UseAnim.BOW, 20, SoundEvents.ARMOR_EQUIP_LEATHER.value(), "dust_filter"));

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(EventPriority.LOWEST, SurvivalKits::addToCreativeTab);
    }

    private static float factor(LivingEntity entity, Holder<MobEffect> effect) {
        return entity.hasEffect(effect) ? KIT_FACTOR : 1f;
    }

    /** Multiplicadores que usan los peligros (se aplican además de la armadura). */
    public static float heatFactor(LivingEntity entity) { return factor(entity, COOLED); }
    public static float coldFactor(LivingEntity entity) { return factor(entity, WARMED); }
    public static float dustFactor(LivingEntity entity) { return factor(entity, FILTERED); }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!tab.getNamespace().equals("rocketnautics") || !tab.getPath().equals("world_tab")) return;
        try {
            for (DeferredItem<? extends Item> item : List.of(COOLING_GEL, CHEMICAL_HEATER, DUST_FILTER)) {
                event.accept(item.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        } catch (RuntimeException e) {
            PlanetaryExpansion.LOGGER.error("[Kits] No se pudieron añadir los kits a la pestaña {}", tab, e);
        }
    }

    /** Efecto beneficioso sin nada propio: los peligros comprueban si lo tienes. */
    public static class KitEffect extends MobEffect {
        public KitEffect(int color) {
            super(MobEffectCategory.BENEFICIAL, color);
        }
    }

    /** Se usa como una poción (el gel se bebe; el calentador y el filtro se activan) y da 3 min de su efecto. */
    public static class KitItem extends Item {
        private final Holder<MobEffect> effect;
        private final UseAnim anim;
        private final int useTicks;
        private final SoundEvent sound;
        private final String key;

        public KitItem(Holder<MobEffect> effect, UseAnim anim, int useTicks, SoundEvent sound, String key) {
            super(new Item.Properties().stacksTo(16));
            this.effect = effect;
            this.anim = anim;
            this.useTicks = useTicks;
            this.sound = sound;
            this.key = key;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            return ItemUtils.startUsingInstantly(level, player, hand);
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) { return anim; }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity entity) { return useTicks; }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (!level.isClientSide) {
                entity.addEffect(new MobEffectInstance(effect, DURATION, 0, false, true, true));
                level.playSound(null, entity.blockPosition(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
                if (entity instanceof ServerPlayer sp) sp.awardStat(Stats.ITEM_USED.get(this));
            }
            if (!(entity instanceof Player p && p.getAbilities().instabuild)) stack.shrink(1);
            return stack;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip." + PlanetaryExpansion.MODID + "." + key).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip." + PlanetaryExpansion.MODID + ".kit_note").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
