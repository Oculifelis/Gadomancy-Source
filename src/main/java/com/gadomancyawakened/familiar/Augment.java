package com.gadomancyawakened.familiar;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;

/**
 * Familiar augments and their per-level vis cost per attack (Gadomancy 1.5.17 FamiliarAugment). The four elements
 * exclude each other; damage, range and speed exclude each other too and each needs an element first.
 */
public enum Augment implements StringRepresentable {
    SHOCK(3, true, Map.of(TTAspects.AER, 3, TTAspects.PERDITIO, 2)),
    FIRE(3, true, Map.of(TTAspects.IGNIS, 4, TTAspects.ORDO, 2)),
    POISON(3, true, Map.of(TTAspects.AQUA, 3, TTAspects.PERDITIO, 4)),
    WEAKNESS(3, true, Map.of(TTAspects.PERDITIO, 3, TTAspects.TERRA, 3)),
    DAMAGE(3, false, Map.of(TTAspects.IGNIS, 3)),
    RANGE(2, false, Map.of(TTAspects.AER, 2, TTAspects.ORDO, 1)),
    SPEED(2, false, Map.of(TTAspects.ORDO, 2, TTAspects.IGNIS, 1));

    public static final Codec<Augment> CODEC = StringRepresentable.fromEnum(Augment::values);
    public static final StreamCodec<ByteBuf, Augment> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Augment::ordinal);

    private final int maxLevel;
    private final boolean element;
    private final Map<ResourceKey<IAspect>, Integer> costPerLevel;

    Augment(int maxLevel, boolean element, Map<ResourceKey<IAspect>, Integer> costPerLevel) {
        this.maxLevel = maxLevel;
        this.element = element;
        this.costPerLevel = costPerLevel;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public boolean isElement() {
        return element;
    }

    public Map<ResourceKey<IAspect>, Integer> costPerLevel() {
        return costPerLevel;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
