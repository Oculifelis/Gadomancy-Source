package com.gadomancyawakened.aura;

import com.gadomancyawakened.GadomancyAwakened;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * What an Aura Pylon fed with one aspect does, read from {@code data/<ns>/gadomancyawakened/aura_effect/*.json}. Each file names
 * its aspect, how often it pulses and how far it reaches, and a list of actions (see {@link AuraAction}). Gadomancy
 * ships one file per aspect that had an effect in 1.5.17, re-keyed to TC6 aspects; packs may change or add them.
 */
public record AuraEffect(ResourceKey<IAspect> aspect, int interval, int range, List<AuraAction> actions) {
    public static final ResourceKey<net.minecraft.core.Registry<AuraEffect>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(GadomancyAwakened.id("aura_effect"));

    public static final Codec<AuraEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(IAspect.REGISTRY_KEY).fieldOf("aspect").forGetter(AuraEffect::aspect),
            Codec.intRange(1, 1200).optionalFieldOf("interval", 8).forGetter(AuraEffect::interval),
            Codec.intRange(1, 64).optionalFieldOf("range", 8).forGetter(AuraEffect::range),
            AuraAction.CODEC.listOf().fieldOf("actions").forGetter(AuraEffect::actions)
    ).apply(i, AuraEffect::new));

    /** Runs every action whose pulse falls on this tick, centred on the pylon. */
    public void pulse(ServerLevel level, BlockPos centre, long tick) {
        if (tick % interval != 0) {
            return;
        }
        AABB box = new AABB(centre).inflate(range);
        for (AuraAction action : actions) {
            action.apply(level, centre, range, box);
        }
    }
}
