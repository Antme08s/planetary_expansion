package dev.antme08.planetaryexpansion.weather;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuración del servidor (archivo serverconfig/planetary_expansion-server.toml de cada mundo).
 * Permite ajustar las tormentas sin recompilar el mod.
 */
public final class StormConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue FREQUENCY;
    public static final ModConfigSpec.DoubleValue DURATION;
    public static final ModConfigSpec.DoubleValue WIND_STRENGTH;
    public static final ModConfigSpec.BooleanValue PUSH_SHIPS;
    public static final ModConfigSpec.BooleanValue PUSH_FLYING_SHIPS;
    public static final ModConfigSpec.DoubleValue DUST_DAMAGE;
    public static final ModConfigSpec.BooleanValue BLIZZARD_FREEZING;
    public static final ModConfigSpec.BooleanValue WINDMILL_BOOST;
    public static final ModConfigSpec.DoubleValue VENUS_HEAT;
    public static final ModConfigSpec.DoubleValue ACID_RAIN_DAMAGE;
    public static final ModConfigSpec.BooleanValue ACID_CORROSION;
    public static final ModConfigSpec.BooleanValue ACID_LIGHTNING;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Planet storms from Planetary Expansion").push("storms");
        ENABLED = b.comment("Enable storms (Mars: dust storms, Ice World and Thalassa: blizzards, Venus: acid rain)")
                .define("enabled", true);
        FREQUENCY = b.comment("Frequency multiplier. 2.0 = twice as many storms, 0.5 = half")
                .defineInRange("frequency", 1.0, 0.05, 20.0);
        DURATION = b.comment("Duration multiplier for each storm")
                .defineInRange("duration", 1.0, 0.1, 10.0);
        WIND_STRENGTH = b.comment("Wind strength multiplier on players, entities and ships. 0 = no push")
                .defineInRange("windStrength", 1.0, 0.0, 5.0);
        PUSH_SHIPS = b.comment("Wind pushes Sable physics structures that have wheels (vehicles).",
                        "Structures without wheels resting on the ground (bases) never move.")
                .define("pushShips", true);
        PUSH_FLYING_SHIPS = b.comment("Wind also pushes structures WITHOUT wheels while they are airborne (flying ships)")
                .define("pushFlyingShips", true);
        DUST_DAMAGE = b.comment("Damage of each rock hit in the dust storm (1.0 = half a heart). 0 = no damage")
                .defineInRange("dustDamage", 1.0, 0.0, 20.0);
        BLIZZARD_FREEZING = b.comment("Blizzards freeze players who are outdoors")
                .define("blizzardFreezing", true);
        WINDMILL_BOOST = b.comment("Outdoor Create windmills spin faster during storms (normal up to x1.6, severe up to x2.5)")
                .define("windmillBoost", true);
        VENUS_HEAT = b.comment("Venus heat per second without Hazard Armor (1.0 = half a heart per second once the armor is worn out). 0 = no heat")
                .defineInRange("venusHeat", 1.0, 0.0, 20.0);
        ACID_RAIN_DAMAGE = b.comment("Acid rain damage per second outdoors (1.0 = half a heart in severe rain, half that in normal rain). 0 = no damage")
                .defineInRange("acidRainDamage", 1.0, 0.0, 20.0);
        ACID_CORROSION = b.comment("Acid rain destroys wool, wood and plants that are outdoors")
                .define("acidRainCorrosion", true);
        ACID_LIGHTNING = b.comment("Lightning (visual only: no fire or damage) during severe acid rain")
                .define("acidRainLightning", true);
        b.pop();
        SPEC = b.build();
    }

    private StormConfig() {}

    /** Lectura segura: si la config aún no cargó (p. ej. en el cliente), devuelve el valor por defecto. */
    public static double get(ModConfigSpec.DoubleValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    public static boolean get(ModConfigSpec.BooleanValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }
}
