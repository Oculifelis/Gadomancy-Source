package com.gadomancyawakened;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Gadomancy's item data components (the original's item NBT flags). */
public final class GDComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, GadomancyAwakened.MODID);

    /** The remote jar network a jar or jar item belongs to. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> JAR_NETWORK = COMPONENTS.registerComponentType("jar_network",
            b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    /** Marks a jar item as sticky: it can be placed on walls and ceilings. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> STICKY = COMPONENTS.registerComponentType("sticky",
            b -> b.persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC));

    /** An arcane package that shows as a plain package, hiding what is inside. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> DISGUISED = COMPONENTS.registerComponentType("disguised",
            b -> b.persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC));

    /** The primal an aura core was attuned to; absent on a blank core. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceKey<IAspect>>> CORE_ASPECT = COMPONENTS.registerComponentType("core_aspect",
            b -> b.persistent(ResourceKey.codec(IAspect.REGISTRY_KEY)).networkSynchronized(ResourceKey.streamCodec(IAspect.REGISTRY_KEY)));

    /** An ethereal familiar's aspect and augment levels. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.gadomancyawakened.familiar.FamiliarData>> FAMILIAR =
            COMPONENTS.registerComponentType("familiar", b -> b.persistent(com.gadomancyawakened.familiar.FamiliarData.CODEC)
                    .networkSynchronized(com.gadomancyawakened.familiar.FamiliarData.STREAM_CODEC));

    /** Which augment an augment rune teaches, and at what level. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.gadomancyawakened.familiar.AugmentRune.Rune>> AUGMENT =
            COMPONENTS.registerComponentType("augment", b -> b.persistent(com.gadomancyawakened.familiar.AugmentRune.Rune.CODEC)
                    .networkSynchronized(com.gadomancyawakened.familiar.AugmentRune.Rune.STREAM_CODEC));

    /** A disguised armour piece's own equippable, kept so a water bottle can wash the disguise off. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.world.item.equipment.Equippable>> DISGUISE =
            COMPONENTS.registerComponentType("disguise", b -> b.persistent(net.minecraft.world.item.equipment.Equippable.CODEC)
                    .networkSynchronized(net.minecraft.world.item.equipment.Equippable.STREAM_CODEC));

    private GDComponents() {
    }

    static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }
}
