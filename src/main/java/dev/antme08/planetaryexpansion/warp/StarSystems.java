package dev.antme08.planetaryexpansion.warp;

import dev.devce.rocketnautics.api.orbit.FrameTree;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Sistemas solares (1.0.3). En el árbol de Cosmonautics las estrellas cuelgan de la raíz ("root") y todo lo demás
 * cuelga, directa o indirectamente, de una estrella. Antes se daba por hecho que solo existía "sol".
 */
public final class StarSystems {
    private StarSystems() {}

    /** ¿Es una estrella (hija directa de la raíz)? */
    public static boolean isStar(FrameTree f) {
        return f != null && f.getParent() != null && f.getParent().getParent() == null;
    }

    public static boolean isStar(CubePlanet p) {
        try {
            return p != null && isStar(p.frame());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Estrella del sistema al que pertenece este astro (él mismo si es una estrella). */
    public static FrameTree starOf(FrameTree f) {
        if (f == null) return null;
        FrameTree cur = f;
        while (cur.getParent() != null && cur.getParent().getParent() != null) cur = cur.getParent();
        return cur.getParent() == null ? null : cur;   // la raíz no pertenece a ningún sistema
    }

    /** Nombre de la estrella de su sistema ("sol" si no se puede saber). */
    public static String systemOf(FrameTree f) {
        FrameTree s = starOf(f);
        return s == null ? "sol" : s.getName();
    }

    public static String systemOf(CubePlanet p) {
        try {
            return p == null ? "sol" : systemOf(p.frame());
        } catch (Throwable t) {
            return "sol";
        }
    }

    /** Busca un astro por nombre en el universo y devuelve su sistema ("sol" si no existe). */
    public static String systemOf(UniverseDefinition u, String body) {
        if (u == null || body == null) return "sol";
        for (CubePlanet p : u.getPlanets()) if (p.frame().getName().equals(body)) return systemOf(p);
        return "sol";
    }

    /** Nombres de todas las estrellas, con "sol" primero. */
    public static List<String> stars(UniverseDefinition u) {
        List<String> out = new ArrayList<>();
        if (u == null) return out;
        for (CubePlanet p : u.getPlanets()) if (isStar(p)) out.add(p.frame().getName());
        out.sort((a, b) -> a.equals("sol") ? -1 : b.equals("sol") ? 1 : a.compareTo(b));
        return out;
    }
}
