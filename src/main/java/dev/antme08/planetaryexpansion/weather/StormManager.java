package dev.antme08.planetaryexpansion.weather;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.weather.StormSavedData.Phase;
import dev.antme08.planetaryexpansion.weather.StormSavedData.State;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;

/**
 * Lógica de las tormentas en el servidor: ciclo calma → subida → tormenta → bajada, sincronización
 * con los clientes, golpes de piedras, congelación, empuje del viento y el comando /planet_weather.
 */
public final class StormManager {
    public static final ResourceKey<DamageType> DUST_STORM_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "dust_storm"));

    private static final RandomSource RNG = RandomSource.create();
    private static final float TAU = (float) (Math.PI * 2);

    /** Copia rápida del estado para el hilo de física de Sable: [intensidad, ángulo] por tipo. */
    private static final float[] CACHE_INTENSITY = new float[StormType.values().length];
    private static final float[] CACHE_ANGLE = new float[StormType.values().length];
    private static final boolean[] CACHE_SEVERE = new boolean[StormType.values().length];

    private StormManager() {}

    // ------------------------------------------------------------------ consulta

    public static float intensity(StormType type) {
        return CACHE_INTENSITY[type.ordinal()];
    }

    public static float windAngle(StormType type) {
        return CACHE_ANGLE[type.ordinal()];
    }

    public static boolean severe(StormType type) {
        return CACHE_SEVERE[type.ordinal()];
    }

    /**
     * Factor de velocidad para los molinos de Create: 1 sin tormenta; hasta ×1.6 en tormenta normal
     * y ×2.5 en tormenta fuerte, si las aspas están a cielo abierto (se mira la posición delante
     * del rodamiento, donde están las aspas, o justo encima de él).
     */
    public static float windmillMultiplier(Level level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction facing) {
        if (!StormConfig.get(StormConfig.WINDMILL_BOOST)) return 1f;
        StormType type = StormType.forDimension(level.dimension());
        if (type == null) return 1f;
        float i = intensity(type);
        if (i < 0.02f) return 1f;
        // Molinos sobre estructuras de Sable: sus bloques están en el "plot" (coordenadas enormes), así
        // que se mira el cielo en la posición real del mundo.
        boolean exposed = StormShelter.underOpenSky(level, worldPos(level, Vec3.atCenterOf(pos.above())))
                || (facing != null && StormShelter.underOpenSky(level, worldPos(level, Vec3.atCenterOf(pos.relative(facing)))));
        if (!exposed) return 1f;
        return 1f + i * (severe(type) ? 1.5f : 0.6f);
    }

    private static Vec3 worldPos(Level level, Vec3 pos) {
        try {
            return dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, pos);
        } catch (Throwable t) {
            return pos;
        }
    }

    /** Ráfagas: varía la fuerza del viento entre ~0.5 y 1.0 de forma suave. Igual en cliente y servidor. */
    public static double gust(long gameTime) {
        return 0.75 + 0.25 * Math.sin(gameTime * 0.11) * Math.sin(gameTime * 0.037 + 1.0);
    }

    // ------------------------------------------------------------------ ciclo

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        StormSavedData data = StormSavedData.get(server);
        boolean enabled = StormConfig.get(StormConfig.ENABLED);
        long tick = server.getTickCount();

        for (StormType type : StormType.values()) {
            State s = data.get(type);
            Phase before = s.phase;
            advance(type, s, enabled);
            if (s.intensity > 0f) {
                s.windAngle += (RNG.nextFloat() - 0.5f) * 0.004f; // el viento cambia de dirección poco a poco
            }
            CACHE_INTENSITY[type.ordinal()] = s.intensity;
            CACHE_ANGLE[type.ordinal()] = s.windAngle;
            CACHE_SEVERE[type.ordinal()] = s.severe;

            boolean changed = before != s.phase;
            if (changed) {
                PlanetaryExpansion.LOGGER.info("[Clima] {}: {} -> {}", type.displayName, before, s.phase);
                if (before == Phase.STORM) stormSurvived(server, type, s.severe);
            }
            if (changed || tick % 20 == 0) {
                data.setDirty();
                sync(server, type, s);
            }
        }
    }

    /** Logros (0.7.41): al acabar lo fuerte de la tormenta, a los que siguen vivos en ese planeta. */
    private static void stormSurvived(MinecraftServer server, StormType type, boolean severe) {
        net.minecraft.server.level.ServerLevel level = server.getLevel(type.dimension);
        if (level == null) return;
        String id = switch (type) {
            case DUST_STORM, FERRA_DUST_STORM -> "sandblasted";
            case ACID_RAIN -> "acid_test";
            case BLIZZARD, THALASSA_BLIZZARD -> severe ? "whiteout" : null;
        };
        if (id != null) dev.antme08.planetaryexpansion.advancement.Achievements.awardAll(level, id, "survived");
    }

    private static void advance(StormType t, State s, boolean enabled) {
        switch (s.phase) {
            case CALM -> {
                if (s.ticksLeft < 0) s.ticksLeft = calmDuration(t);
                if (!enabled) return;
                if (--s.ticksLeft <= 0) {
                    s.phase = Phase.RISING;
                    s.ticksLeft = -1;
                    s.windAngle = RNG.nextFloat() * TAU;
                    s.severe = RNG.nextFloat() < t.severeChance;
                }
            }
            case RISING -> {
                s.intensity = Math.min(1f, s.intensity + 1f / t.rampTicks);
                if (!enabled) {
                    s.phase = Phase.FALLING;
                } else if (s.intensity >= 1f) {
                    s.phase = Phase.STORM;
                    if (s.ticksLeft <= 0) s.ticksLeft = stormDuration(t);
                }
            }
            case STORM -> {
                if (!enabled || --s.ticksLeft <= 0) s.phase = Phase.FALLING;
            }
            case FALLING -> {
                s.intensity = Math.max(0f, s.intensity - 1f / t.rampTicks);
                if (s.intensity <= 0f) {
                    s.phase = Phase.CALM;
                    s.ticksLeft = calmDuration(t);
                }
            }
        }
    }

    private static int calmDuration(StormType t) {
        double base = t.minCalm + RNG.nextDouble() * (t.maxCalm - t.minCalm);
        return (int) Math.max(200, base / StormConfig.get(StormConfig.FREQUENCY));
    }

    private static int stormDuration(StormType t) {
        double base = t.minStorm + RNG.nextDouble() * (t.maxStorm - t.minStorm);
        return (int) Math.max(200, base * StormConfig.get(StormConfig.DURATION));
    }

    // ------------------------------------------------------------------ sincronización

    private static StormSyncPayload payload(StormType t, State s) {
        return new StormSyncPayload(t.dimension.location(), s.intensity, s.windAngle,
                (float) StormConfig.get(StormConfig.WIND_STRENGTH), s.severe);
    }

    private static void sync(MinecraftServer server, StormType t, State s) {
        ServerLevel level = server.getLevel(t.dimension);
        if (level != null && !level.players().isEmpty()) {
            PacketDistributor.sendToPlayersInDimension(level, payload(t, s));
        }
    }

    private static void syncTo(ServerPlayer player) {
        StormType t = StormType.forDimension(player.level().dimension());
        if (t == null || player.getServer() == null) return;
        PacketDistributor.sendToPlayer(player, payload(t, StormSavedData.get(player.getServer()).get(t)));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) syncTo(p);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) syncTo(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) syncTo(p);
    }

    // ------------------------------------------------------------------ efectos en el mundo

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        StormType type = StormType.forDimension(level.dimension());
        if (type == null) return;
        float intensity = intensity(type);
        if (intensity <= 0.01f) return;

        double angle = windAngle(type);
        double dx = Math.cos(angle), dz = Math.sin(angle);
        double gust = gust(level.getGameTime());
        double wind = StormConfig.get(StormConfig.WIND_STRENGTH);

        // Jugadores: daño / frío. (Su empuje se calcula en su propio cliente.)
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) continue;
            if (!StormShelter.exposedToHazards(player)) continue;
            boolean severe = severe(type);
            if (type == StormType.ACID_RAIN) {
                continue; // la lluvia ácida la gestiona VenusHazards (armadura primero, luego vida)
            } else if (type.isDust()) {
                dustHit(level, player, intensity, severe);
            } else {
                freeze(player, intensity, severe);
            }
        }

        // Resto de entidades: empuje del viento.
        if (wind <= 0) return;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Player || entity.isSpectator() || entity.noPhysics || entity.isPassenger()) continue;
            boolean living = entity instanceof LivingEntity;
            if (!living && !(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)
                    && !(entity instanceof FallingBlockEntity)) continue;
            if (!StormShelter.exposedToWind(entity)) continue;
            double a = type.entityAccel * intensity * wind * gust * (living ? 1.0 : 0.6);
            Vec3 m = entity.getDeltaMovement();
            entity.setDeltaMovement(m.x + dx * a, m.y, m.z + dz * a);
            entity.hasImpulse = true;
        }
    }

    /**
     * Tormenta de arena: de vez en cuando un golpe de piedra (afectado por la armadura).
     * Normal: ~1 golpe cada 8 s y 60 % del daño. Fuerte: ~1 cada 3 s y daño completo.
     */
    private static void dustHit(ServerLevel level, ServerPlayer player, float intensity, boolean severe) {
        // Armadura de impacto: -25 % por pieza, inmune con el traje completo
        double damage = StormConfig.get(StormConfig.DUST_DAMAGE) * (severe ? 1.0 : 0.6)
                * dev.antme08.planetaryexpansion.armor.ImpactArmor.dustStormFactor(player);
        if (damage <= 0 || intensity < 0.4f) return;
        if (RNG.nextFloat() >= (severe ? 0.016f : 0.006f) * intensity) return;
        DamageSource source = new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DUST_STORM_DAMAGE));
        player.hurt(source, (float) (damage * (0.5 + 0.5 * intensity)));
    }

    /**
     * Ventisca: sube el contador de congelación como la nieve polvo. Minecraft lo baja 2 por tick
     * cuando no estás en nieve polvo, así que al resguardo te descongelas solo. Al llegar al máximo,
     * Minecraft aplica el daño por congelación normal. Respeta la armadura de cuero.
     */
    private static void freeze(ServerPlayer player, float intensity, boolean severe) {
        if (!StormConfig.get(StormConfig.BLIZZARD_FREEZING) || !player.canFreeze()) return;
        int required = player.getTicksRequiredToFreeze();
        // Normal: ~35 s para congelarse del todo. Fuerte: ~12 s.
        float rate = severe ? 0.55f : 0.2f;
        // Armadura glacial: -25 % por pieza, nada con el traje completo
        float cold = dev.antme08.planetaryexpansion.armor.GlacialArmor.coldFactor(player);
        if (cold <= 0f) return;
        int add = 2 + (RNG.nextFloat() < rate * intensity * cold ? 1 : 0);
        player.setTicksFrozen(Math.min(required + 2, player.getTicksFrozen() + add));
    }

    // ------------------------------------------------------------------ comando

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        String[] planets = Arrays.stream(StormType.values()).map(StormType::planetName).toArray(String[]::new);
        d.register(Commands.literal("planet_weather")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("status").executes(StormManager::status))
                .then(Commands.literal("start")
                        .then(Commands.argument("planet", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(planets, b))
                                .executes(c -> start(c, null, -1))
                                .then(Commands.literal("normal")
                                        .executes(c -> start(c, false, -1))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 120))
                                                .executes(c -> start(c, false, IntegerArgumentType.getInteger(c, "minutes")))))
                                .then(Commands.literal("severe")
                                        .executes(c -> start(c, true, -1))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 120))
                                                .executes(c -> start(c, true, IntegerArgumentType.getInteger(c, "minutes")))))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("planet", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(planets, b))
                                .executes(StormManager::stop))));
    }

    private static StormType planetArg(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "planet");
        StormType t = StormType.byPlanetName(name);
        if (t == null) c.getSource().sendFailure(Component.translatable("command.planetary_expansion.weather.no_storms", name));
        return t;
    }

    private static int start(CommandContext<CommandSourceStack> c, Boolean severe, int minutes) {
        StormType t = planetArg(c);
        if (t == null) return 0;
        StormSavedData data = StormSavedData.get(c.getSource().getServer());
        State s = data.get(t);
        s.severe = severe != null ? severe : RNG.nextFloat() < t.severeChance;
        if (s.phase == Phase.STORM) {
            s.ticksLeft = minutes > 0 ? minutes * 1200 : Math.max(s.ticksLeft, 1200);
        } else {
            s.phase = Phase.RISING;
            s.ticksLeft = minutes > 0 ? minutes * 1200 : -1;
            if (s.intensity <= 0f) s.windAngle = RNG.nextFloat() * TAU;
        }
        data.setDirty();
        Component kind = Component.translatable("command.planetary_expansion.weather." + (s.severe ? "severe" : "normal"));
        String time = minutes > 0 ? " (" + minutes + " min)" : "";
        c.getSource().sendSuccess(() -> Component.translatable("command.planetary_expansion.weather.started",
                t.displayComponent(), kind, t.planetComponent(), time), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> c) {
        StormType t = planetArg(c);
        if (t == null) return 0;
        StormSavedData data = StormSavedData.get(c.getSource().getServer());
        State s = data.get(t);
        if (s.phase != Phase.CALM) s.phase = Phase.FALLING;
        data.setDirty();
        c.getSource().sendSuccess(() -> Component.translatable("command.planetary_expansion.weather.stopping",
                t.displayComponent(), t.planetComponent()), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        StormSavedData data = StormSavedData.get(c.getSource().getServer());
        for (StormType t : StormType.values()) {
            State s = data.get(t);
            String time = s.ticksLeft > 0 ? String.format(" (%.1f min)", s.ticksLeft / 1200.0) : "";
            Component kind = s.phase == Phase.CALM ? Component.empty() : Component.literal(" ").append(
                    Component.translatable("command.planetary_expansion.weather." + (s.severe ? "severe" : "normal")));
            Component phase = Component.translatable("command.planetary_expansion.weather.phase." + s.phase.name().toLowerCase(java.util.Locale.ROOT));
            Component line = Component.translatable("command.planetary_expansion.weather.status",
                    t.displayComponent(), kind, t.planetComponent(), phase, time, Math.round(s.intensity * 100));
            c.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    /** Dimensión de un nivel como tipo de tormenta (atajo para otras clases). */
    public static StormType typeOf(Level level) {
        return StormType.forDimension(level.dimension());
    }
}
