package com.gadomancyawakened;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/** Data maps Gadomancy reads; values live in {@code data/gadomancyawakened/data_maps/block/}. */
public final class GDDataMaps {
    /** How much a block helps a Book of Knowledge study (bookshelf 1, another book 2, as in the original). */
    public static final DataMapType<Block, Integer> KNOWLEDGE = DataMapType.builder(GadomancyAwakened.id("knowledge"), Registries.BLOCK,
            Codec.intRange(0, 64)).build();

    /**
     * Blocks that can attune an aura core: the vis a wand pays to start, and the core's starting primal weights
     * (a single-aspect cluster gave its aspect 60 in the original).
     */
    public record CoreSource(Map<ResourceKey<IAspect>, Integer> cost, Map<ResourceKey<IAspect>, Integer> weights) {
        static final Codec<Map<ResourceKey<IAspect>, Integer>> AMOUNTS = Codec.unboundedMap(ResourceKey.codec(IAspect.REGISTRY_KEY), Codec.INT);
        public static final Codec<CoreSource> CODEC = RecordCodecBuilder.create(i -> i.group(
                AMOUNTS.fieldOf("cost").forGetter(CoreSource::cost),
                AMOUNTS.fieldOf("weights").forGetter(CoreSource::weights)).apply(i, CoreSource::new));
    }

    public static final DataMapType<Block, CoreSource> AURA_CORE_SOURCE = DataMapType.builder(GadomancyAwakened.id("aura_core_source"),
            Registries.BLOCK, CoreSource.CODEC).build();

    /**
     * Vis a wand pays to form a multiblock, keyed by the block clicked: the Aura Pylon, the Essentia Compressor and the
     * Node Manipulator. The Portal Focus entry is the Node Manipulator's cost when a Portal Focus sits on it. A block with
     * no entry forms for free.
     */
    public static final DataMapType<Block, Map<ResourceKey<IAspect>, Integer>> FORM_COST = DataMapType.builder(
            GadomancyAwakened.id("form_cost"), Registries.BLOCK, CoreSource.AMOUNTS).build();

    public static Map<ResourceKey<IAspect>, Integer> formCost(Block block) {
        Map<ResourceKey<IAspect>, Integer> cost = block.builtInRegistryHolder().getData(FORM_COST);
        return cost == null ? Map.of() : cost;
    }

    private GDDataMaps() {
    }

    public static void register(IEventBus bus) {
        bus.addListener((RegisterDataMapTypesEvent event) -> {
            event.register(KNOWLEDGE);
            event.register(AURA_CORE_SOURCE);
            event.register(FORM_COST);
        });
    }
}
