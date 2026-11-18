package com.gadomancyawakened;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Block tags that say which blocks Gadomancy's machines work with, so packs can extend them. */
public final class GDTags {
    /** Pedestals the Essentia Compressor drinks Elements from (the original's Ancient Pedestal). */
    public static final TagKey<Block> COMPRESSOR_PEDESTALS = TagKey.create(Registries.BLOCK, GadomancyAwakened.id("compressor_pedestals"));
    /** Blocks a Book of Knowledge can rest on (the original's arcane pedestal). */
    public static final TagKey<Block> KNOWLEDGE_BOOK_SUPPORTS = TagKey.create(Registries.BLOCK, GadomancyAwakened.id("knowledge_book_supports"));

    /** Blocks that stand at the Node Manipulator's corners, level with it (the original's ancient pedestal stone). */
    public static final TagKey<Block> MANIPULATOR_PILLARS = TagKey.create(Registries.BLOCK, GadomancyAwakened.id("manipulator_pillars"));
    /** Blocks on top of the pillars, level with the focus (the original's ancient stone). */
    public static final TagKey<Block> MANIPULATOR_CAPS = TagKey.create(Registries.BLOCK, GadomancyAwakened.id("manipulator_caps"));

    private GDTags() {
    }
}
