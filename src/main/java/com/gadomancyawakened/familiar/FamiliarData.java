package com.gadomancyawakened.familiar;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;

/** The {@code gadomancyawakened:familiar} component: the familiar's aspect (from attuning) and its augment levels. */
public record FamiliarData(Optional<ResourceKey<IAspect>> aspect, Map<Augment, Integer> augments) {
    public static final FamiliarData EMPTY = new FamiliarData(Optional.empty(), Map.of());

    public static final Codec<FamiliarData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(IAspect.REGISTRY_KEY).optionalFieldOf("aspect").forGetter(FamiliarData::aspect),
            Codec.unboundedMap(Augment.CODEC, Codec.intRange(1, 3)).optionalFieldOf("augments", Map.of()).forGetter(FamiliarData::augments)
    ).apply(i, FamiliarData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FamiliarData> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(IAspect.REGISTRY_KEY).apply(ByteBufCodecs::optional), FamiliarData::aspect,
            ByteBufCodecs.map(i -> new EnumMap<>(Augment.class), Augment.STREAM_CODEC, ByteBufCodecs.VAR_INT), FamiliarData::sorted,
            (a, m) -> new FamiliarData(a, Map.copyOf(m)));

    /** The augments in enum order. Not {@code new EnumMap<>(map)}: that throws on an empty non-EnumMap. */
    public EnumMap<Augment, Integer> sorted() {
        EnumMap<Augment, Integer> m = new EnumMap<>(Augment.class);
        m.putAll(augments);
        return m;
    }

    public int level(Augment a) {
        return augments.getOrDefault(a, 0);
    }

    public Optional<Augment> element() {
        return augments.keySet().stream().filter(Augment::isElement).findFirst();
    }

    /** Whether one more level of {@code a} may be added, by the original's rules. */
    public boolean canAdd(Augment a) {
        int now = level(a);
        if (now >= a.maxLevel()) {
            return false;
        }
        if (a.isElement()) {
            return element().map(e -> e == a).orElse(true);
        }
        if (element().isEmpty()) {
            return false;
        }
        return augments.keySet().stream().noneMatch(o -> !o.isElement() && o != a);
    }

    public FamiliarData with(Augment a) {
        EnumMap<Augment, Integer> next = sorted();
        next.merge(a, 1, Integer::sum);
        return new FamiliarData(aspect, Map.copyOf(next));
    }

    public FamiliarData withAspect(ResourceKey<IAspect> key) {
        return new FamiliarData(Optional.of(key), augments);
    }
}
