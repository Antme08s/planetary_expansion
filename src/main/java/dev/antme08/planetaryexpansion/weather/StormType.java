package dev.antme08.planetaryexpansion.weather;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Definición fija de cada tipo de tormenta y del planeta donde ocurre.
 * Los tiempos están en ticks (20 ticks = 1 segundo, 24000 ticks = 1 día de juego).
 */
public enum StormType {
    /** Marte: tormentas de arena frecuentes. Golpes de piedras a quien esté a la intemperie. */
    DUST_STORM("mars", "Dust storm",
            24_000, 48_000,     // calma entre tormentas: 1 a 2 días
            3_600, 9_600,       // duración de la tormenta: 3 a 8 minutos
            600,                // 30 s para llegar a plena intensidad (y para disiparse)
            12.0,               // velocidad del viento (m/s) hacia la que se empuja a las naves
            6.0,                // fuerza del viento por bloque² de superficie expuesta de una nave
            0.012,              // aceleración del viento sobre entidades (bloques/tick²)
            0.35f,              // probabilidad de que sea una tormenta fuerte (con niebla)
            new float[]{0.80f, 0.45f, 0.18f}, 16f),   // naranja de arena roja; se ve hasta 16 bloques
    /** Planeta helado: ventiscas poco frecuentes. Congelan a quien esté a la intemperie. */
    BLIZZARD("ice_world", "Blizzard",
            72_000, 144_000,    // calma: 3 a 6 días
            2_400, 6_000,       // duración: 2 a 5 minutos
            600,
            14.0,
            7.0,
            0.014,
            0.40f,
            new float[]{0.96f, 0.98f, 1.0f}, 16f),    // blanco de bloque de nieve
    /**
     * Thalassa (0.6.2, idea del usuario): ventiscas como las del planeta helado pero más frecuentes y casi
     * siempre normales; la fuerte (con niebla) es rara. Mismo viento, partículas y congelación que BLIZZARD.
     */
    THALASSA_BLIZZARD("thalassa", "Blizzard",
            24_000, 48_000,     // calma: 1 a 2 días (en el planeta helado, 3 a 6)
            2_400, 6_000,       // duración: 2 a 5 minutos
            600,
            14.0,
            7.0,
            0.014,
            0.15f,              // fuerte: 15 % (en el planeta helado, 40 %)
            new float[]{0.96f, 0.98f, 1.0f}, 16f),
    /**
     * Venus (0.7.0): lluvia ácida frecuente. Daña a quien esté a la intemperie (primero la armadura) y corroe
     * lana, madera y plantas expuestas (ver VenusHazards). La fuerte trae más niebla (40 bloques) y rayos.
     * Poco viento: en la superficie de Venus el aire casi no se mueve.
     */
    ACID_RAIN("venus", "Acid rain",
            12_000, 24_000,     // calma: medio día a un día
            3_600, 9_600,       // duración: 3 a 8 minutos
            600,
            4.0,
            2.0,
            0.004,
            0.30f,              // fuerte: 30 %
            new float[]{0.55f, 0.55f, 0.22f}, 40f),    // amarillo verdoso; se ve hasta 40 bloques
    /**
     * Ferra (1.0.3, el usuario: "tormentas como en Marte" y "una luna hostil"): tormentas de polvo de óxido como las de
     * Marte, pero más frecuentes, con más viento y fuertes la mitad de las veces.
     */
    FERRA_DUST_STORM("ferra", "Rust storm",
            12_000, 30_000,     // calma: medio día a día y cuarto
            3_600, 9_600,       // duración: 3 a 8 minutos
            600,
            15.0,
            7.0,
            0.014,
            0.50f,              // fuerte: 50 %
            new float[]{0.72f, 0.32f, 0.16f}, 14f);   // rojo óxido; se ve hasta 14 bloques

    /** Tormentas de polvo (Marte y Ferra): golpes de piedras, partículas de arena. */
    public boolean isDust() {
        return this == DUST_STORM || this == FERRA_DUST_STORM;
    }

    public final ResourceKey<Level> dimension;
    public final String displayName;
    public final int minCalm, maxCalm, minStorm, maxStorm, rampTicks;
    public final double windSpeed, shipForcePerArea, entityAccel;
    /** Probabilidad de que una tormenta sea fuerte (niebla y más daño), como la tormenta eléctrica frente a la lluvia. */
    public final float severeChance;
    /** Color de la niebla de la tormenta fuerte (RGB 0..1) y distancia de visión a plena intensidad. */
    public final float[] fogColor;
    public final float fogDistance;

    StormType(String planet, String displayName, int minCalm, int maxCalm, int minStorm, int maxStorm,
              int rampTicks, double windSpeed, double shipForcePerArea, double entityAccel,
              float severeChance, float[] fogColor, float fogDistance) {
        this.dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, planet));
        this.displayName = displayName;
        this.minCalm = minCalm;
        this.maxCalm = maxCalm;
        this.minStorm = minStorm;
        this.maxStorm = maxStorm;
        this.rampTicks = rampTicks;
        this.windSpeed = windSpeed;
        this.shipForcePerArea = shipForcePerArea;
        this.entityAccel = entityAccel;
        this.severeChance = severeChance;
        this.fogColor = fogColor;
        this.fogDistance = fogDistance;
    }

    /** Nombre traducido de la tormenta (storm.planetary_expansion.dust_storm, ...). */
    public Component displayComponent() {
        return Component.translatable("storm." + PlanetaryExpansion.MODID + "." + name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Nombre traducido del planeta (el mismo que usa la mesa de hologramas). */
    public Component planetComponent() {
        return Component.translatable("hologram." + PlanetaryExpansion.MODID + ".body." + planetName());
    }

    /** Nombre corto del planeta para comandos ("mars", "ice_world"). */
    public String planetName() {
        return dimension.location().getPath();
    }

    public static StormType forDimension(ResourceKey<Level> dim) {
        for (StormType t : values()) {
            if (t.dimension.equals(dim)) return t;
        }
        return null;
    }

    public static StormType byPlanetName(String name) {
        for (StormType t : values()) {
            if (t.planetName().equalsIgnoreCase(name)) return t;
        }
        return null;
    }
}
