package dev.antme08.planetaryexpansion;

import dev.antme08.planetaryexpansion.weather.ClientStormState;
import dev.antme08.planetaryexpansion.weather.StormConfig;
import dev.antme08.planetaryexpansion.weather.StormManager;
import dev.antme08.planetaryexpansion.weather.StormPhysics;
import dev.antme08.planetaryexpansion.weather.StormSyncPayload;
import dev.antme08.planetaryexpansion.weather.client.ClientStormEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cosmonautics: Expansión Planetaria.
 *
 * Los planetas, dimensiones y la generación de terreno son datos (src/main/resources/data).
 * El código Java añade el clima de los planetas y compatibilidades opcionales con otros mods.
 */
@Mod(PlanetaryExpansion.MODID)
public final class PlanetaryExpansion {
    public static final String MODID = "planetary_expansion";
    public static final Logger LOGGER = LoggerFactory.getLogger("PlanetaryExpansion");

    public PlanetaryExpansion(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, StormConfig.SPEC);

        modBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1")
                .playToClient(StormSyncPayload.TYPE, StormSyncPayload.CODEC,
                        (payload, context) -> context.enqueueWork(() -> ClientStormState.receive(payload)))
                .playToClient(dev.antme08.planetaryexpansion.weather.ThrustPayload.TYPE, dev.antme08.planetaryexpansion.weather.ThrustPayload.CODEC,
                        (payload, context) -> context.enqueueWork(() -> dev.antme08.planetaryexpansion.client.ClientThrust.receive(payload))));
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            StormPhysics.register();
            dev.antme08.planetaryexpansion.weather.StructureLoadGuard.register();
        }));

        NeoForge.EVENT_BUS.register(StormManager.class);
        NeoForge.EVENT_BUS.register(dev.antme08.planetaryexpansion.weather.PlanetCold.class);
        NeoForge.EVENT_BUS.register(dev.antme08.planetaryexpansion.weather.VenusHazards.class);
        NeoForge.EVENT_BUS.register(dev.antme08.planetaryexpansion.weather.ShipTransferGuard.class);
        NeoForge.EVENT_BUS.register(dev.antme08.planetaryexpansion.weather.ShipFreeze.class);
        NeoForge.EVENT_BUS.register(dev.antme08.planetaryexpansion.weather.OrbitRescue.class);
        NeoForge.EVENT_BUS.register(dev.antme08.planetaryexpansion.weather.ThrustTracker.class);
        NeoForge.EVENT_BUS.register(dev.antme08.planetaryexpansion.weather.SpaceBeds.class);
        NeoForge.EVENT_BUS.register(dev.antme08.planetaryexpansion.advancement.Achievements.class);
        NeoForge.EVENT_BUS.register(dev.antme08.planetaryexpansion.worldgen.IceWorldFluids.class);
        NeoForge.EVENT_BUS.register(dev.antme08.planetaryexpansion.diag.BlockLossLog.class);
        dev.antme08.planetaryexpansion.armor.BasicSuit.register(modBus);      // 0.7.64: traje de inicio
        dev.antme08.planetaryexpansion.armor.ImpactArmor.register(modBus);
        dev.antme08.planetaryexpansion.armor.GlacialArmor.register(modBus);
        dev.antme08.planetaryexpansion.armor.HazardArmor.register(modBus);
        dev.antme08.planetaryexpansion.armor.SeaDiverArmor.register(modBus);
        dev.antme08.planetaryexpansion.armor.ThermalSuit.register(modBus);    // 0.7.64: traje de Ignis
        dev.antme08.planetaryexpansion.armor.SuitUpgrades.register(modBus);   // 0.7.64: plantillas y materiales
        dev.antme08.planetaryexpansion.armor.SurvivalKits.register(modBus);   // 0.7.66: kits para la primera expedición
        dev.antme08.planetaryexpansion.worldgen.VenusLavaVentFeature.register(modBus);
        dev.antme08.planetaryexpansion.gravity.GravityGenerator.register(modBus);
        dev.antme08.planetaryexpansion.dampener.InertialDampener.register(modBus);
        dev.antme08.planetaryexpansion.warp.Warp.register(modBus);
        dev.antme08.planetaryexpansion.worldgen.TerrainBlendProcessor.register(modBus);
        dev.antme08.planetaryexpansion.creatures.Creatures.register(modBus); // 0.7.61: criaturas de Thalassa
        dev.antme08.planetaryexpansion.creatures.IgnisCreatures.register(modBus); // 0.7.64: caracoles de magma
        dev.antme08.planetaryexpansion.cerulea.CeruleaBlocks.register(modBus); // 1.0.3: Cerúlea, el planeta de Ámbar
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientStormEffects.register();
            modBus.addListener(dev.antme08.planetaryexpansion.client.ArmorModels::onRegisterReloadListeners);
            NeoForge.EVENT_BUS.register(dev.antme08.planetaryexpansion.client.SuitBodyHider.class);
            NeoForge.EVENT_BUS.register(dev.antme08.planetaryexpansion.client.PlanetMapRefresh.class);
            dev.antme08.planetaryexpansion.gravity.GravityGeneratorClient.register(modBus);
            dev.antme08.planetaryexpansion.dampener.InertialDampenerClient.register(modBus);
            dev.antme08.planetaryexpansion.warp.client.WarpClient.register(modBus);
            NeoForge.EVENT_BUS.register(dev.antme08.planetaryexpansion.client.SpaceNavOverlay.class);
            NeoForge.EVENT_BUS.register(dev.antme08.planetaryexpansion.client.ThalassaLeviathan.class); // 0.7.54
            NeoForge.EVENT_BUS.register(dev.antme08.planetaryexpansion.client.ThalassaAmbience.class); // 0.7.58
            NeoForge.EVENT_BUS.register(dev.antme08.planetaryexpansion.client.DeepSpaceSound.class); // 1.0.3
            dev.antme08.planetaryexpansion.creatures.client.CreaturesClient.register(modBus); // 0.7.61
            NeoForge.EVENT_BUS.register(dev.antme08.planetaryexpansion.client.ThermalLavaVision.class); // 0.7.64
        }

        // Create Deep Seas (modid create_submarine) es opcional: la clase de compatibilidad
        // solo se carga si el mod está presente, así no hace falta tenerlo instalado.
        if (ModList.get().isLoaded("create_submarine")) {
            NeoForge.EVENT_BUS.register(DeepSeasOxygenCompat.class);
            DeepSeasOxygenCompat.installStormShelter();
            LOGGER.info("Compatibilidad de oxígeno con Create Deep Seas activada");
        }
    }
}
