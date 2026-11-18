package com.gadomancyawakened.jar;

import com.gadomancyawakened.GDRegistry;
import com.leclowndu93150.thaumaturge.registry.TTBlocks;
import java.util.function.Supplier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ItemLike;

/** The jars that can be made sticky, as in the original: warded, void and remote. */
public enum JarKind implements StringRepresentable {
    NORMAL("normal", () -> TTBlocks.JAR_NORMAL.get()),
    VOID("void", () -> TTBlocks.JAR_VOID.get()),
    REMOTE("remote", () -> GDRegistry.REMOTE_JAR.get());

    private final String name;
    private final Supplier<ItemLike> item;

    JarKind(String name, Supplier<ItemLike> item) {
        this.name = name;
        this.item = item;
    }

    public ItemLike item() {
        return item.get();
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
