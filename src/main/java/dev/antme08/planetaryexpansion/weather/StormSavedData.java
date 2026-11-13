package dev.antme08.planetaryexpansion.weather;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.Map;

/** Estado de las tormentas de todos los planetas, guardado en el mundo (data/planetary_expansion_storms.dat). */
public final class StormSavedData extends SavedData {
    public static final String ID = "planetary_expansion_storms";

    public enum Phase { CALM, RISING, STORM, FALLING }

    public static final class State {
        public Phase phase = Phase.CALM;
        /** Ticks que quedan en la fase CALM o STORM. */
        public int ticksLeft = -1;
        /** 0 = calma, 1 = tormenta a plena intensidad. */
        public float intensity = 0f;
        /** Dirección hacia la que sopla el viento, en radianes (0 = +X, PI/2 = +Z). */
        public float windAngle = 0f;
        /** Tormenta fuerte: niebla y más daño. Normal: solo viento, partículas y poco daño. */
        public boolean severe = false;
    }

    private final Map<StormType, State> states = new EnumMap<>(StormType.class);

    public StormSavedData() {
        for (StormType t : StormType.values()) states.put(t, new State());
    }

    public State get(StormType type) {
        return states.get(type);
    }

    public static StormSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StormSavedData::new, StormSavedData::load, null), ID);
    }

    public static StormSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        StormSavedData data = new StormSavedData();
        for (StormType t : StormType.values()) {
            if (!tag.contains(t.planetName())) continue;
            CompoundTag s = tag.getCompound(t.planetName());
            State st = data.states.get(t);
            int phase = s.getInt("phase");
            st.phase = Phase.values()[Math.max(0, Math.min(phase, Phase.values().length - 1))];
            st.ticksLeft = s.getInt("ticksLeft");
            st.intensity = s.getFloat("intensity");
            st.windAngle = s.getFloat("windAngle");
            st.severe = s.getBoolean("severe");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        for (StormType t : StormType.values()) {
            State st = states.get(t);
            CompoundTag s = new CompoundTag();
            s.putInt("phase", st.phase.ordinal());
            s.putInt("ticksLeft", st.ticksLeft);
            s.putFloat("intensity", st.intensity);
            s.putFloat("windAngle", st.windAngle);
            s.putBoolean("severe", st.severe);
            tag.put(t.planetName(), s);
        }
        return tag;
    }
}
