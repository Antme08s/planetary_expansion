package dev.antme08.planetaryexpansion.engine;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Comprobación previa del soporte de metano. El soporte hereda las clases internas de Cosmonautics (ThrusterMountBlock y
 * ThrusterMountBlockEntity), que no son una API: si una versión nueva les cambia un método o un campo, registrar el
 * bloque haría fallar el arranque con un error de clase. Aquí se lee el archivo de cada clase (sin cargarla) para ver que sigan estando
 * los miembros que usamos; si falta alguno, el soporte de metano no se registra y el juego arranca igual, con un aviso
 * en el registro. Si la comprobación misma falla por otra causa, se registra como siempre.
 */
final class MethaneEngineCompat {
    private MethaneEngineCompat() {}

    private static final String BLOCK = "dev.devce.rocketnautics.content.blocks.ThrusterMountBlock";
    private static final String ENTITY = "dev.devce.rocketnautics.content.blocks.ThrusterMountBlockEntity";
    private static final String TAGS = "dev.devce.rocketnautics.registry.RocketTags";

    static boolean compatible() {
        try {
            ClassLoader cl = MethaneEngineCompat.class.getClassLoader();
            String missing = missing(cl, BLOCK, Set.of("getBlockEntityType", "getBlockEntityClass", "getTicker", "codec"), Set.of());
            missing += missing(cl, ENTITY, Set.of("getThrustModifier", "getEfficiencyModifier", "getHeatModifier", "tick",
                    "getInput1", "getInput2"), Set.of("tank1", "tank2", "combinedFluidHandler"));
            missing += missing(cl, TAGS, Set.of(), Set.of());
            if (!missing.isBlank()) {
                PlanetaryExpansion.LOGGER.warn("[Metano] Esta versión de Cosmonautics ya no tiene {}; el soporte de motor de metano "
                        + "no se registra (el resto del mod funciona). Hace falta actualizar Planetary Expansion.", missing.trim());
                return false;
            }
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.debug("[Metano] No se pudo comprobar la compatibilidad; se registra igualmente", t);
        }
        return true;
    }

    /**
     * Nombres de métodos y campos que faltan en la clase y en sus superclases de Cosmonautics, o la propia clase si no existe.
     * Se lee el archivo de la clase con ASM en vez de usar reflexión: así no se cargan los tipos de sus firmas (que en un
     * servidor pueden ser de cliente) y la comprobación no se rompe por eso.
     */
    private static String missing(ClassLoader cl, String className, Set<String> methods, Set<String> fields) throws java.io.IOException {
        Set<String> haveM = new HashSet<>(), haveF = new HashSet<>();
        String current = className.replace('.', '/');
        boolean found = false;
        while (current != null && current.startsWith("dev/devce/")) {
            try (InputStream in = cl.getResourceAsStream(current + ".class")) {
                if (in == null) {
                    break;
                }
                ClassNode node = new ClassNode();
                new ClassReader(in).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                found = true;
                node.methods.forEach(m -> haveM.add(m.name));
                node.fields.forEach(f -> haveF.add(f.name));
                current = node.superName;
            }
        }
        if (!found) {
            return className + " ";
        }
        String simple = className.substring(className.lastIndexOf('.') + 1);
        String m = methods.stream().filter(n -> !haveM.contains(n)).map(n -> simple + "." + n + "()").collect(Collectors.joining(" "));
        String f = fields.stream().filter(n -> !haveF.contains(n)).map(n -> simple + "." + n).collect(Collectors.joining(" "));
        return (m + " " + f + " ").replaceAll("^ +", "");
    }
}
