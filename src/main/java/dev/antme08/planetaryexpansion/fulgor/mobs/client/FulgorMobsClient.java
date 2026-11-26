package dev.antme08.planetaryexpansion.fulgor.mobs.client;

import dev.antme08.planetaryexpansion.creatures.client.CreatureRenderer;
import dev.antme08.planetaryexpansion.creatures.client.JsonMobModel;
import dev.antme08.planetaryexpansion.fulgor.mobs.ArcCrawler;
import dev.antme08.planetaryexpansion.fulgor.mobs.FulgorMobs;
import dev.antme08.planetaryexpansion.fulgor.mobs.FulguriteStrider;
import dev.antme08.planetaryexpansion.fulgor.mobs.SpireCatcher;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Cliente de las criaturas de Fulgor (1.0.4): renderers, animaciones y brillo según la carga. */
public final class FulgorMobsClient {
    private FulgorMobsClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(FulgorMobsClient::renderers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FulgorMobs.ARC.get(), ctx -> new CreatureRenderer<ArcCrawler>(ctx, "arc_crawler",
                0.35F, 1.0F, CreatureRenderer.Swim.NONE, FulgorMobsClient::arc,
                e -> e.charge() > 0 ? 0.85 + 0.15 * Math.sin(e.tickCount * 0.9) : 0.4 + 0.1 * Math.sin(e.tickCount * 0.08)));
        event.registerEntityRenderer(FulgorMobs.STRIDER.get(), ctx -> new CreatureRenderer<FulguriteStrider>(ctx, "fulgurite_strider",
                0.6F, 1.0F, CreatureRenderer.Swim.NONE, FulgorMobsClient::strider,
                e -> e.overcharge() > 0 ? 0.8 + 0.2 * Math.sin(e.tickCount * 0.5) : 0.18));
        event.registerEntityRenderer(FulgorMobs.CATCHER.get(), ctx -> new CreatureRenderer<SpireCatcher>(ctx, "spire_catcher",
                0.6F, 1.0F, CreatureRenderer.Swim.NONE, FulgorMobsClient::catcher,
                e -> e.supercharge() > 0 ? 0.85 + 0.15 * Math.sin(e.tickCount * 0.7) : 0.5));
    }

    private static void add(ModelPart p, float x, float y, float z) {
        if (p == null) return;
        p.xRot += x;
        p.yRot += y;
        p.zRot += z;
    }

    /** Arco: seis patas en dos trípodes alternos, cola que se mece (más deprisa cargado), cabeza que mira. */
    private static void arc(JsonMobModel<ArcCrawler> m, ArcCrawler e, float ls, float la, float age, float hy, float hp) {
        float w = ls * 1.3F;
        for (int k = 0; k < 3; k++) {
            float phL = (k % 2 == 0) ? 0F : Mth.PI, phR = phL + Mth.PI;
            add(m.part("leg_l" + k), 0, Mth.sin(w + phL) * 0.4F * la, Math.max(0F, Mth.cos(w + phL)) * 0.3F * la);
            add(m.part("leg_r" + k), 0, Mth.sin(w + phR) * 0.4F * la, -Math.max(0F, Mth.cos(w + phR)) * 0.3F * la);
        }
        float sp = e.charge() > 0 ? 0.35F : 0.1F;
        add(m.part("tail"), Mth.sin(age * sp) * 0.12F, Mth.sin(age * sp * 0.7F) * 0.2F, 0);
        add(m.part("head"), hp * Mth.DEG_TO_RAD * 0.4F, hy * Mth.DEG_TO_RAD * 0.5F, 0);
        add(m.part("body"), 0, 0, Mth.sin(w) * 0.04F * la);
    }

    /** Zancudo: zancadas largas de patas diagonales, balanceo del cuerpo y la cabeza que picotea al andar. */
    private static void strider(JsonMobModel<FulguriteStrider> m, FulguriteStrider e, float ls, float la, float age, float hy, float hp) {
        float w = ls * 0.6F;
        float a = Mth.cos(w) * 0.6F * la, b = Mth.cos(w + Mth.PI) * 0.6F * la;
        add(m.part("leg_l0"), a, 0, 0);
        add(m.part("leg_r1"), a, 0, 0);
        add(m.part("leg_r0"), b, 0, 0);
        add(m.part("leg_l1"), b, 0, 0);
        add(m.part("body"), 0, 0, Mth.sin(w) * 0.04F * la);
        float peck = Mth.sin(w * 2F) * 0.08F * la + Mth.sin(age * 0.05F) * 0.03F;
        add(m.part("head"), hp * Mth.DEG_TO_RAD * 0.35F + peck, hy * Mth.DEG_TO_RAD * 0.5F, 0);
        if (e.isBaby()) {
            ModelPart head = m.part("head");
            if (head != null) head.xScale = head.yScale = head.zScale = 1.25F;
        }
    }

    /** Atrapa-rayos: piernas de zancos, brazos que se balancean y se alzan al atacar; en el aire, brazos adelante. */
    private static void catcher(JsonMobModel<SpireCatcher> m, SpireCatcher e, float ls, float la, float age, float hy, float hp) {
        float w = ls * 0.7F;
        float a = Mth.cos(w) * 0.7F * la;
        add(m.part("leg_l0"), a, 0, 0);
        add(m.part("leg_r0"), -a, 0, 0);
        float atk = e.getAttackAnim(age - e.tickCount);
        float air = e.onGround() ? 0F : 1F;
        float idle = Mth.sin(age * 0.06F) * 0.05F;
        add(m.part("arm_l"), -a * 0.6F - Mth.sin(atk * Mth.PI) * 1.4F - air * 0.9F, 0, -idle - 0.05F);
        add(m.part("arm_r"), a * 0.6F - Mth.sin(atk * Mth.PI) * 1.4F - air * 0.9F, 0, idle + 0.05F);
        add(m.part("head"), hp * Mth.DEG_TO_RAD * 0.6F, hy * Mth.DEG_TO_RAD * 0.7F, 0);
        add(m.part("body"), air * 0.25F + Mth.sin(age * 0.05F) * 0.015F, 0, 0);
    }
}
