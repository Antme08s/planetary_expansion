package dev.antme08.planetaryexpansion.weather.client;

import dev.antme08.planetaryexpansion.weather.ClientStormState;
import dev.antme08.planetaryexpansion.weather.StormManager;
import dev.antme08.planetaryexpansion.weather.StormShelter;
import dev.antme08.planetaryexpansion.weather.StormSyncPayload;
import dev.antme08.planetaryexpansion.weather.StormType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;

/**
 * Efectos de tormenta en el cliente: niebla, color, partículas arrastradas por el viento, sonido
 * de viento y empuje del jugador local. Solo se registra en el cliente.
 */
public final class ClientStormEffects {
    private static final DustParticleOptions SAND_LIGHT = new DustParticleOptions(new Vector3f(0.74f, 0.42f, 0.25f), 0.9f);
    private static final DustParticleOptions SAND_DARK = new DustParticleOptions(new Vector3f(0.52f, 0.27f, 0.16f), 0.7f);
    private static final DustParticleOptions ASH_LIGHT = new DustParticleOptions(new Vector3f(0.58f, 0.54f, 0.74f), 0.9f);
    private static final DustParticleOptions ASH_DARK = new DustParticleOptions(new Vector3f(0.32f, 0.30f, 0.46f), 0.7f);
    private static final DustParticleOptions SNOW_DUST = new DustParticleOptions(new Vector3f(0.95f, 0.97f, 1.0f), 0.8f);
    private static final DustParticleOptions ACID_DROP = new DustParticleOptions(new Vector3f(0.72f, 0.78f, 0.22f), 0.7f);
    private static final DustParticleOptions ACID_DROP_DARK = new DustParticleOptions(new Vector3f(0.55f, 0.62f, 0.15f), 0.6f);
    /** Neblina permanente de Venus: se ve hasta ~60 bloques (40 en la lluvia ácida fuerte). 0.7.4: antes 40/30. */
    private static final float VENUS_HAZE = 60f;
    private static final float[] VENUS_FOG = {0.70f, 0.52f, 0.24f};
    /** Titán (1.0.4): bruma naranja de tolinas permanente (la atmósfera es densa). */
    private static final float TITAN_HAZE = 90f;
    private static final float[] TITAN_FOG = {0.50f, 0.30f, 0.12f};
    private static final net.minecraft.resources.ResourceLocation TITAN_DIM =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(dev.antme08.planetaryexpansion.PlanetaryExpansion.MODID, "titan");

    private static boolean inTitan(Minecraft mc) {
        return mc.level != null && mc.level.dimension().location().equals(TITAN_DIM);
    }

    /** Intensidad mostrada (suavizada) y la dimensión a la que corresponde. */
    private static float shown = 0f;
    /** Intensidad de la niebla mostrada (solo en tormentas fuertes). */
    private static float shownFog = 0f;
    private static float windAngle = 0f;
    private static ResourceKey<Level> shownDim = null;
    private static boolean playerSheltered = false;
    private static StormWindSound sound = null;
    private static int soundAge = 0;

    private ClientStormEffects() {}

    public static void register() {
        NeoForge.EVENT_BUS.register(ClientStormEffects.class);
    }

    static float shown() {
        return shown;
    }

    static float shownFog() {
        return shownFog;
    }

    static boolean playerSheltered() {
        return playerSheltered;
    }

    private static StormType currentType(Minecraft mc) {
        return mc.level == null ? null : StormType.forDimension(mc.level.dimension());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientStormState.clear();
        shown = 0f;
        shownFog = 0f;
        shownDim = null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            shown = 0f;
            shownFog = 0f;
            return;
        }
        if (mc.isPaused()) return;

        ResourceKey<Level> dim = level.dimension();
        if (!dim.equals(shownDim)) {
            shown = 0f;
            shownFog = 0f;
            shownDim = dim;
        }
        StormType type = StormType.forDimension(dim);
        StormSyncPayload state = type == null ? null : ClientStormState.get(dim.location());
        float target = state == null ? 0f : state.intensity();
        shown += (target - shown) * 0.05f;
        if (Math.abs(target - shown) < 0.002f) shown = target;
        float fogTarget = state != null && state.severe() ? target : 0f;
        shownFog += (fogTarget - shownFog) * 0.03f;
        if (Math.abs(fogTarget - shownFog) < 0.002f) shownFog = fogTarget;
        if (state != null) windAngle = state.windAngle();

        if (type == null || shown < 0.01f) {
            playerSheltered = true;
            return;
        }

        double dx = Math.cos(windAngle), dz = Math.sin(windAngle);
        double gust = StormManager.gust(level.getGameTime());
        playerSheltered = !StormShelter.underOpenSky(level, player.getEyePosition());

        // Empuje del jugador (su movimiento se calcula en el cliente).
        if (state.windPush() > 0 && !player.isSpectator() && !player.getAbilities().flying
                && StormShelter.exposedToWind(player)) {
            double a = type.entityAccel * shown * state.windPush() * gust
                    * dev.antme08.planetaryexpansion.armor.GlacialArmor.windFactor(player); // armadura glacial
            Vec3 m = player.getDeltaMovement();
            player.setDeltaMovement(m.x + dx * a, m.y, m.z + dz * a);
        }

        if (type == StormType.ACID_RAIN) {
            spawnAcidRain(mc, level, player, dx, dz);
            return;
        }
        spawnParticles(mc, level, type, dx, dz, gust);
        updateSound(mc, type);
    }

    /** Lluvia ácida: gotas amarillo verdoso que caen casi rectas, humo donde tocan el suelo y sonido de lluvia. */
    private static void spawnAcidRain(Minecraft mc, ClientLevel level, LocalPlayer player, double dx, double dz) {
        // 0.7.7: mucho más densa (petición del usuario): más gotas, más cerca de la cámara y cada gota es una
        // estela de 2 partículas. La fuerte llega a ~300 gotas por tick.
        int count = (int) (300 * shown * (0.6f + 0.4f * shownFog));
        ParticleStatus status = mc.options.particles().get();
        if (status == ParticleStatus.DECREASED) count /= 2;
        else if (status == ParticleStatus.MINIMAL) count /= 5;
        Vec3 c = mc.gameRenderer.getMainCamera().getPosition();
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            double x = c.x + (r.nextDouble() - 0.5) * 24;
            double z = c.z + (r.nextDouble() - 0.5) * 24;
            double y = c.y - 2 + r.nextDouble() * 14;
            double ox = x - c.x, oy = y - c.y, oz = z - c.z;
            if (ox * ox + oy * oy + oz * oz < 4.0) continue;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            if (ground > y) continue;
            DustParticleOptions drop = r.nextInt(3) == 0 ? ACID_DROP_DARK : ACID_DROP;
            double vy = -1.0 - r.nextDouble() * 0.4;
            int life = 6 + r.nextInt(8);
            for (int seg = 0; seg < 2; seg++) {   // estela: dos partículas una sobre otra
                Particle p = mc.particleEngine.createParticle(drop, x, y + seg * 0.35, z, 0, 0, 0);
                if (p == null) continue;
                p.setParticleSpeed(dx * 0.05, vy, dz * 0.05);
                p.setLifetime(life);
            }
        }
        // humo donde la lluvia toca el suelo
        for (int i = 0; i < count / 5; i++) {
            int x = Mth.floor(c.x + (r.nextDouble() - 0.5) * 20);
            int z = Mth.floor(c.z + (r.nextDouble() - 0.5) * 20);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (Math.abs(y - c.y) > 12) continue;
            double px = x + r.nextDouble(), pz = z + r.nextDouble();
            level.addParticle(r.nextInt(3) == 0 ? ParticleTypes.SMOKE : ACID_DROP, px, y + 0.05, pz,
                    (r.nextDouble() - 0.5) * 0.1, 0.08, (r.nextDouble() - 0.5) * 0.1);   // salpicadura
        }
        // sonido de lluvia, como el de Minecraft: en un punto cercano del suelo
        if (r.nextInt(3) == 0) {
            int x = Mth.floor(c.x + (r.nextDouble() - 0.5) * 20);
            int z = Mth.floor(c.z + (r.nextDouble() - 0.5) * 20);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            boolean above = y > player.getEyeY() + 1 && playerSheltered;
            level.playLocalSound(x + 0.5, y, z + 0.5, above ? SoundEvents.WEATHER_RAIN_ABOVE : SoundEvents.WEATHER_RAIN,
                    SoundSource.WEATHER, (above ? 0.1f : 0.25f) * shown, above ? 0.5f : 0.9f + r.nextFloat() * 0.2f, false);
        }
    }

    private static void spawnParticles(Minecraft mc, ClientLevel level, StormType type, double dx, double dz, double gust) {
        // Tormenta normal: algo menos de partículas que la fuerte.
        int count = (int) ((type.isDust() ? 110 : 130) * shown * (0.7f + 0.3f * shownFog));
        ParticleStatus status = mc.options.particles().get();
        if (status == ParticleStatus.DECREASED) count /= 2;
        else if (status == ParticleStatus.MINIMAL) count /= 5;
        if (count <= 0) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 c = camera.getPosition();
        RandomSource r = level.random;
        double speed = type.windSpeed * shown * gust / 20.0 * 2.2; // m/s -> bloques por tick, exagerado para que se note

        for (int i = 0; i < count; i++) {
            // Aparecen un poco a barlovento para que crucen la vista.
            double x = c.x + (r.nextDouble() - 0.5) * 40 - dx * 10;
            double z = c.z + (r.nextDouble() - 0.5) * 40 - dz * 10;
            double y = c.y - 4 + r.nextDouble() * 14;
            // No justo delante de la cámara: de cerca se ven como cuadros enormes.
            double ox = x - c.x, oy = y - c.y, oz = z - c.z;
            if (ox * ox + oy * oy + oz * oz < 9.0) continue;
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) > y) continue;

            ParticleOptions options;
            double vy;
            if (type.isDust()) {
                boolean fulgor = type == StormType.FULGOR_STORM;
                options = r.nextInt(3) == 0 ? (fulgor ? ASH_DARK : SAND_DARK) : (fulgor ? ASH_LIGHT : SAND_LIGHT);
                vy = (r.nextDouble() - 0.5) * 0.04;
            } else {
                options = r.nextInt(4) == 0 ? SNOW_DUST : ParticleTypes.SNOWFLAKE;
                vy = -0.05 - r.nextDouble() * 0.05;
            }
            Particle p = mc.particleEngine.createParticle(options, x, y, z, 0, 0, 0);
            if (p == null) continue;
            double s = speed * (0.7 + r.nextDouble() * 0.6);
            p.setParticleSpeed(dx * s, vy, dz * s);
            p.setLifetime(20 + r.nextInt(20));
        }
    }

    private static void updateSound(Minecraft mc, StormType type) {
        soundAge++;
        boolean lost = sound != null && soundAge > 40 && !mc.getSoundManager().isActive(sound);
        if (sound == null || sound.isStopped() || lost) {
            sound = new StormWindSound(type.isDust() ? 0.55f : 0.75f);
            soundAge = 0;
            mc.getSoundManager().play(sound);
        }
    }

    // ------------------------------------------------------------------ niebla

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        StormType type = currentType(mc);
        if (type == null && inTitan(mc)) {
            if (event.getType() != FogType.NONE) return;
            float far = Math.min(event.getFarPlaneDistance(), TITAN_HAZE);
            event.setFarPlaneDistance(far);
            event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.3f));
            event.setCanceled(true);
            return;
        }
        if (type == StormType.ACID_RAIN) {
            if (event.getType() != FogType.NONE) return;
            // Venus: neblina amarilla siempre (60 bloques); en la lluvia ácida fuerte baja a 40
            float far = Math.min(event.getFarPlaneDistance(), Mth.lerp(shownFog, VENUS_HAZE, type.fogDistance));
            event.setFarPlaneDistance(far);
            event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.3f));
            event.setCanceled(true);
            return;
        }
        if (type == null || shownFog < 0.01f || event.getType() != FogType.NONE) return;
        float far0 = event.getFarPlaneDistance();
        // Interpolación exponencial: la visibilidad cae rápido al empezar la tormenta.
        float far = (float) (far0 * Math.pow(Math.min(1f, type.fogDistance / Math.max(1f, far0)), shownFog));
        float near = Mth.lerp(shownFog, event.getNearPlaneDistance(), 0f);
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(Math.min(near, far * 0.5f));
        event.setCanceled(true); // necesario para que NeoForge aplique los cambios
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        StormType type = currentType(mc);
        if (type == null && inTitan(mc)) {
            float day = Mth.clamp(mc.level.getSkyDarken((float) event.getPartialTick()), 0.35f, 1f);
            event.setRed(TITAN_FOG[0] * day);
            event.setGreen(TITAN_FOG[1] * day);
            event.setBlue(TITAN_FOG[2] * day);
            return;
        }
        if (type == StormType.ACID_RAIN && mc.level != null) {
            // Venus (0.7.6): neblina ámbar siempre. Con shaders (Complementary modificado) la niebla se pinta con
            // fogColor² × 1.8, así que un color claro salía casi blanco: este es más oscuro y saturado.
            float day = Mth.clamp(mc.level.getSkyDarken((float) event.getPartialTick()), 0.35f, 1f);
            float k = shownFog;
            event.setRed(Mth.lerp(k, VENUS_FOG[0], type.fogColor[0]) * day);
            event.setGreen(Mth.lerp(k, VENUS_FOG[1], type.fogColor[1]) * day);
            event.setBlue(Mth.lerp(k, VENUS_FOG[2], type.fogColor[2]) * day);
            return;
        }
        if (type == null || shownFog < 0.01f || mc.level == null) return;
        // De noche se oscurece un poco, pero no hasta el negro: arena naranja / nieve blanca.
        float daylight = Mth.clamp(mc.level.getSkyDarken((float) event.getPartialTick()), 0.5f, 1f);
        float k = shownFog;
        event.setRed(Mth.lerp(k, event.getRed(), type.fogColor[0] * daylight));
        event.setGreen(Mth.lerp(k, event.getGreen(), type.fogColor[1] * daylight));
        event.setBlue(Mth.lerp(k, event.getBlue(), type.fogColor[2] * daylight));
    }
}
