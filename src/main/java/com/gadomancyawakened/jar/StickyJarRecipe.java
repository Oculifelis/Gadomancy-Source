package com.gadomancyawakened.jar;

import com.gadomancyawakened.GDComponents;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A jar (warded, void or remote) with a slimeball directly below it, and nothing else, becomes the same jar, contents
 * and all, made sticky. As in the original the slime must sit right under the jar, and a sticky jar cannot be re-done.
 */
public class StickyJarRecipe extends CustomRecipe {
    public static final StickyJarRecipe INSTANCE = new StickyJarRecipe();
    public static final RecipeSerializer<StickyJarRecipe> SERIALIZER = new RecipeSerializer<>(MapCodec.unit(INSTANCE),
            StreamCodec.<RegistryFriendlyByteBuf, StickyJarRecipe>unit(INSTANCE));

    private static @Nullable ItemStack jar(CraftingInput input) {
        ItemStack jar = null;
        int jarAt = -1;
        int slimeAt = -1;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(Items.SLIME_BALL) && slimeAt < 0) {
                slimeAt = i;
            } else if (jar == null && JarEvents.kindOf(stack) != null && !stack.has(GDComponents.STICKY.get())) {
                jar = stack;
                jarAt = i;
            } else {
                return null;
            }
        }
        return jar != null && slimeAt == jarAt + input.width() ? jar : null;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return jar(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack jar = jar(input);
        if (jar == null) {
            return ItemStack.EMPTY;
        }
        ItemStack out = jar.copyWithCount(1);
        out.set(GDComponents.STICKY.get(), Unit.INSTANCE);
        return out;
    }

    @Override
    public RecipeSerializer<StickyJarRecipe> getSerializer() {
        return SERIALIZER;
    }
}
