package com.gadomancyawakened;

import com.gadomancyawakened.golem.GDGolems;
import com.gadomancyawakened.golem.GolemEvents;
import com.gadomancyawakened.jar.JarEvents;
import com.gadomancyawakened.jar.JarNetworks;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** Gadomancy (GTNH 1.5.17), ported from the Thaumcraft 4 addon to Thaumaturge (NeoForge 26.1.2). */
@Mod(GadomancyAwakened.MODID)
public final class GadomancyAwakened {
    public static final String MODID = "gadomancyawakened";
    /** The mod id before the 2026-11-14 rename; see {@link LegacyIds}. */
    public static final String LEGACY_MODID = "gadomancy";
    public static final Logger LOG = LogUtils.getLogger();

    public GadomancyAwakened(IEventBus modBus, Dist dist) {
        LegacyIds.register(modBus);
        GDGolems.register(modBus); // first: its items join GDRegistry.ITEMS before that registers
        GDRegistry.register(modBus);
        GDComponents.register(modBus);
        GDGolems.registerAccessories();
        GolemEvents.register();
        JarEvents.register();
        JarNetworks.register();
        GDDataMaps.register(modBus);
        com.gadomancyawakened.machine.Protectors.register();
        com.gadomancyawakened.aura.AuraCoreRitual.register();
        com.gadomancyawakened.node.GrowingNodes.register(modBus);
        modBus.addListener((net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry event) ->
                event.dataPackRegistry(com.gadomancyawakened.node.NodeOutcome.REGISTRY_KEY, com.gadomancyawakened.node.NodeOutcome.CODEC));
        // Synced (network codec = the full codec) so the optional JEI plugin can list the auras on a server too.
        modBus.addListener((net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry event) ->
                event.dataPackRegistry(com.gadomancyawakened.aura.AuraEffect.REGISTRY_KEY, com.gadomancyawakened.aura.AuraEffect.CODEC,
                        com.gadomancyawakened.aura.AuraEffect.CODEC));
        com.gadomancyawakened.aura.LightWards.register();
        SelfTest.register();
        com.gadomancyawakened.effect.GDEffects.register(modBus);
        modBus.addListener(GadomancyAwakened::packs);
        if (dist == Dist.CLIENT) {
            com.gadomancyawakened.client.GDClient.init(modBus);
        }
    }

    /** The original's ancient stone recipes were off by default; here they are a built-in data pack to enable. */
    private static void packs(net.neoforged.neoforge.event.AddPackFindersEvent event) {
        if (event.getPackType() == net.minecraft.server.packs.PackType.SERVER_DATA) {
            event.addPackFinders(id("resourcepacks_data/ancient_stones"), net.minecraft.server.packs.PackType.SERVER_DATA,
                    net.minecraft.network.chat.Component.translatable("pack.gadomancyawakened.ancient_stones"),
                    net.minecraft.server.packs.repository.PackSource.FEATURE, false,
                    net.minecraft.server.packs.repository.Pack.Position.TOP);
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    /** The same path under the old mod id. */
    public static Identifier legacyId(String path) {
        return Identifier.fromNamespaceAndPath(LEGACY_MODID, path);
    }
}
