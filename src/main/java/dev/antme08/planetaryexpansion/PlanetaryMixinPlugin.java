package dev.antme08.planetaryexpansion;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Protege el arranque de los mixins que enganchan campos o métodos de otros mods con "@Shadow" o "@Invoker": a diferencia
 * de los "@Inject" (que con require = 0 se saltan solos), un campo o método que ya no existe en la clase destino hace
 * fallar el arranque entero. Antes de aplicar cada uno se lee la clase destino y se comprueba que sigan estando; si falta
 * algo, ese mixin se salta con un aviso y el resto del mod funciona. Si la comprobación misma falla, se aplica como siempre.
 */
public final class PlanetaryMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("PlanetaryExpansion/Mixin");

    /** Mixin (nombre simple) -> miembros que tiene que tener la clase destino: campos y métodos. */
    private record Needs(Set<String> fields, Set<String> methods) {}

    private static final Map<String, Needs> NEEDS = Map.of(
            "RocketFuelPropertiesMixin", new Needs(Set.of("fuelTank", "currentIspMultiplier", "currentEfficiencyMultiplier"),
                    Set.of("updateFuelProperties")),
            "BearingContraptionSailMixin", new Needs(Set.of("sailBlocks"), Set.of("addBlock")),
            "SableSnatchPointerMixin", new Needs(Set.of("loadedHoldingChunks", "level"), Set.of("snatchAndLoad")),
            "DiverPressureAccessor", new Needs(Set.of(), Set.of("depthOf")));

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        Needs needs = NEEDS.get(simple);
        if (needs == null) {
            return true;
        }
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
            List<String> missing = needs.fields().stream()
                    .filter(f -> node.fields.stream().map((FieldNode n) -> n.name).noneMatch(f::equals))
                    .map(f -> "campo " + f).collect(Collectors.toList());
            needs.methods().stream()
                    .filter(m -> node.methods.stream().map((MethodNode n) -> n.name).noneMatch(m::equals))
                    .forEach(m -> missing.add("método " + m + "()"));
            if (!missing.isEmpty()) {
                LOGGER.warn("[Mixin] {} no se aplica: {} ya no tiene {}. La función que arreglaba queda desactivada; "
                        + "hace falta actualizar Planetary Expansion para esta versión del otro mod.", simple, targetClassName, missing);
                return false;
            }
        } catch (Throwable t) {
            LOGGER.debug("[Mixin] No se pudo comprobar {} ({}); se aplica igualmente", simple, targetClassName, t);
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
