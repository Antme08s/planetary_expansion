package dev.antme08.planetaryexpansion.creatures.client;

import dev.antme08.planetaryexpansion.creatures.AbyssalStalker;
import dev.antme08.planetaryexpansion.creatures.Creatures;
import dev.antme08.planetaryexpansion.creatures.KingCrab;
import dev.antme08.planetaryexpansion.creatures.IgnisCreatures;
import dev.antme08.planetaryexpansion.creatures.LanternFish;
import dev.antme08.planetaryexpansion.creatures.MagmaSnail;
import dev.antme08.planetaryexpansion.creatures.MagmaTitan;
import dev.antme08.planetaryexpansion.creatures.VentCrab;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Parte de cliente de las criaturas de Thalassa: renderers, animaciones, escudo y arpón. */
public final class CreaturesClient {
    private CreaturesClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(CreaturesClient::renderers);
        modBus.addListener(CreaturesClient::extensions);
        modBus.addListener(CreaturesClient::setup);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Creatures.LANTERN_FISH.get(), ctx -> new CreatureRenderer<LanternFish>(ctx, "lantern_fish",
                0.2F, 1.0F, CreatureRenderer.Swim.FISH, CreaturesClient::fish, e -> 0.85 + 0.15 * Math.sin(e.tickCount * 0.15)));
        event.registerEntityRenderer(Creatures.VENT_CRAB.get(), ctx -> new CreatureRenderer<VentCrab>(ctx, "vent_crab",
                0.7F, 1.0F, CreatureRenderer.Swim.NONE, (m, e, ls, la, age, hy, hp) -> crab(m, e, ls, la, age, hy, 1.0F),
                e -> 0.75 + 0.25 * Math.sin(e.tickCount * 0.09)));
        event.registerEntityRenderer(Creatures.KING_CRAB.get(), ctx -> new CreatureRenderer<KingCrab>(ctx, "king_crab",
                1.4F, 1.0F, CreatureRenderer.Swim.NONE, (m, e, ls, la, age, hy, hp) -> crab(m, e, ls, la, age, hy, 0.6F), e -> 1.0));
        event.registerEntityRenderer(Creatures.ABYSSAL_STALKER.get(), ctx -> new CreatureRenderer<AbyssalStalker>(ctx, "abyssal_stalker",
                1.2F, 1.0F, CreatureRenderer.Swim.SWIMMER, CreaturesClient::stalker,
                e -> e.isHunting() ? 0.9 + 0.1 * Math.sin(e.tickCount * 0.5) : 0.35 + 0.1 * Math.sin(e.tickCount * 0.05)));
        event.registerEntityRenderer(Creatures.HARPOON_ENTITY.get(), HarpoonRenderer::new);
        // 0.7.64: caracoles de magma de Ignis
        event.registerEntityRenderer(IgnisCreatures.MAGMA_SNAIL.get(), ctx -> new CreatureRenderer<MagmaSnail>(ctx, "magma_snail",
                0.45F, 1.0F, CreatureRenderer.Swim.NONE, (m, e, ls, la, age, hy, hp) -> snail(m, e, ls, la, age, hy, 1.0F),
                e -> 0.8 + 0.2 * Math.sin(e.tickCount * 0.1)));
        event.registerEntityRenderer(IgnisCreatures.MAGMA_TITAN.get(), ctx -> new CreatureRenderer<MagmaTitan>(ctx, "magma_titan",
                1.2F, 1.0F, CreatureRenderer.Swim.NONE, (m, e, ls, la, age, hy, hp) -> snail(m, e, ls, la, age, hy, 2.6F),
                e -> e.isSpitting() ? 1.0 : 0.75 + 0.15 * Math.sin(e.tickCount * 0.07)));
    }

    private static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private ShellShieldRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new ShellShieldRenderer();
                return renderer;
            }
        }, Creatures.KING_SHELL_SHIELD.get());
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(Creatures.KING_SHELL_SHIELD.get(), ResourceLocation.withDefaultNamespace("blocking"),
                (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F));
    }

    // ------------------------------------------------------------------ animaciones

    private static void add(ModelPart p, float x, float y, float z) {
        if (p == null) return;
        p.xRot += x;
        p.yRot += y;
        p.zRot += z;
    }

    private static void fish(JsonMobModel<LanternFish> m, LanternFish e, float ls, float la, float age, float hy, float hp) {
        float speed = e.isInWater() ? 0.6F : 1.6F; // fuera del agua da coletazos rápidos
        float s = Mth.sin(age * speed);
        add(m.part("tail"), 0, s * 0.45F, 0);
        add(m.part("body"), 0, -s * 0.08F, 0);
        add(m.part("lure"), Mth.sin(age * 0.12F) * 0.12F, 0, Mth.sin(age * 0.09F) * 0.08F);
        add(m.part("jaw"), (Mth.sin(age * 0.15F) + 1) * 0.06F, 0, 0);
    }

    /**
     * Cangrejos (0.7.65: cada pata tiene su grupo). Al caminar las patas van en dos grupos alternos, como los cangrejos
     * de verdad: unas se levantan y avanzan mientras las otras empujan hacia atrás apoyadas, y el cuerpo sube y baja un
     * poco con cada paso. Quietos, las patas se mueven apenas. {@code rate}: el rey camina más pausado.
     */
    private static <T extends Mob> void crab(JsonMobModel<T> m, T e, float ls, float la, float age, float hy, float rate) {
        float amt = Math.min(1F, la * 4F);
        float w = ls * 1.6F * rate;
        float bob = -Math.abs(Mth.sin(w)) * 0.6F * amt;
        for (String g : new String[]{"body", "spines", "claw_r", "claw_l", "eyes"}) {
            ModelPart p = m.part(g);
            if (p != null) p.y += bob;
        }
        for (int i = 0; i < 8; i++) {
            for (int side = 0; side < 2; side++) {
                ModelPart leg = m.part((side == 0 ? "leg_l" : "leg_r") + i);
                if (leg == null) continue;
                // grupos alternos: pata par de un lado con impar del otro
                float phase = w + ((i + side) % 2 == 0 ? 0F : Mth.PI);
                float swing = Mth.cos(phase) * 0.38F * amt;                 // adelante/atrás
                float lift = Math.max(0F, Mth.sin(phase)) * 0.30F * amt;    // solo se levanta al avanzar
                float idle = Mth.sin(age * 0.05F * rate + i * 1.7F + side * 2.3F) * 0.03F;
                float s = side == 0 ? 1F : -1F;                            // el lado derecho va en espejo
                add(leg, 0, swing * s, (lift + idle) * s);
                leg.y += bob;
            }
        }
        float idle = Mth.sin(age * 0.07F * rate) * 0.06F;
        float hit = Mth.sin(m.attackTime * Mth.PI);
        add(m.part("claw_r"), -idle - hit * 0.9F, -hit * 0.3F, 0);
        add(m.part("claw_l"), idle - hit * 0.5F, hit * 0.2F, 0);
        add(m.part("eyes"), 0, hy * Mth.DEG_TO_RAD * 0.3F, 0);
    }

    private static void stalker(JsonMobModel<AbyssalStalker> m, AbyssalStalker e, float ls, float la, float age, float hy, float hp) {
        float rate = e.isHunting() ? 0.22F : 0.12F;
        float s = Mth.sin(age * rate);
        add(m.part("tail"), 0, s * 0.35F, 0);
        add(m.part("body"), 0, -s * 0.07F, 0);
        add(m.part("head"), 0, s * 0.04F, 0);
        float hit = Mth.sin(m.attackTime * Mth.PI);
        add(m.part("jaw"), 0.05F + 0.08F * (Mth.sin(age * 0.08F) + 1) + (e.isHunting() ? 0.15F : 0) + hit * 0.45F, 0, 0);
    }

    /**
     * Caracoles de magma: el pie se estira y encoge al arrastrarse, la cabeza mira y se balancea, y al esconderse la
     * cabeza y la cola se meten en la concha (encogen y van hacia ella). El titán levanta la cabeza al escupir.
     * {@code s}: escala del modelo (1 el caracol, 2,6 el titán).
     */
    private static <T extends MagmaSnail> void snail(JsonMobModel<T> m, T e, float ls, float la, float age, float hy, float s) {
        float p = e.hideProgress(age - e.tickCount);
        float walk = Math.min(1F, la * 3F) * (1F - p);
        ModelPart body = m.part("body"), head = m.part("head"), tail = m.part("tail"), shell = m.part("shell");
        float pulse = Mth.sin(ls * 1.3F) * 0.07F * walk;
        if (body != null) {
            body.zScale = 1F + pulse;
            body.yScale = 1F - 0.35F * p;
        }
        if (tail != null) {
            tail.z += pulse * 6F * s - p * 6F * s;
            tail.xScale = tail.yScale = tail.zScale = Math.max(0.01F, 1F - p);
        }
        if (head != null) {
            head.z -= pulse * 5F * s;
            head.z += p * 7F * s;
            head.y += p * 1.5F * s;
            head.xScale = head.yScale = head.zScale = Math.max(0.01F, 1F - 0.95F * p);
            add(head, Mth.sin(age * 0.06F) * 0.05F, hy * Mth.DEG_TO_RAD * 0.4F * (1F - p), Mth.sin(age * 0.04F) * 0.04F);
            if (e instanceof MagmaTitan t && t.isSpitting()) add(head, -0.3F, 0, 0);
        }
        if (shell != null) {
            shell.y += p * 1.2F * s;
            add(shell, 0, 0, Mth.sin(ls * 0.65F) * 0.025F * walk);
        }
    }
}
