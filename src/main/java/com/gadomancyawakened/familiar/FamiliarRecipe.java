package com.gadomancyawakened.familiar;

import com.gadomancyawakened.GDComponents;
import com.gadomancyawakened.GDRegistry;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Crafting-grid steps for the familiar. The original fixed a familiar's aspect and each augment level inside one
 * infusion that copied the familiar through; Thaumaturge's infusion makes fixed results, so the port splits it:
 * infusion makes the blank familiar and each augment rune (same costs and research), and the grid joins them.
 * A familiar with a vis crystal takes the crystal's aspect (once); a familiar with an augment rune gains that augment
 * if the rune's level is exactly the next level and the original's exclusions allow it.
 */
public class FamiliarRecipe extends CustomRecipe {
    public static final FamiliarRecipe INSTANCE = new FamiliarRecipe();
    public static final RecipeSerializer<FamiliarRecipe> SERIALIZER = new RecipeSerializer<>(MapCodec.unit(INSTANCE),
            StreamCodec.<RegistryFriendlyByteBuf, FamiliarRecipe>unit(INSTANCE));

    private static @Nullable ItemStack result(CraftingInput input) {
        ItemStack familiar = null;
        ItemStack other = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (s.getItem() instanceof EtherealFamiliarItem && familiar == null) {
                familiar = s;
            } else if (other == null) {
                other = s;
            } else {
                return null;
            }
        }
        if (familiar == null || other == null) {
            return null;
        }
        FamiliarData data = EtherealFamiliarItem.data(familiar);
        AspectInstance crystal = other.get(TTDataComponents.CRYSTAL_ASPECT.get());
        if (crystal != null && data.aspect().isEmpty() && crystal.aspect().unwrapKey().isPresent()) {
            ItemStack out = familiar.copyWithCount(1);
            out.set(GDComponents.FAMILIAR.get(), data.withAspect(crystal.aspect().unwrapKey().get()));
            return out;
        }
        AugmentRune.Rune rune = other.is(GDRegistry.FAMILIAR_AUGMENT.get()) ? other.get(GDComponents.AUGMENT.get()) : null;
        if (rune != null && data.aspect().isPresent() && data.level(rune.augment()) + 1 == rune.level() && data.canAdd(rune.augment())) {
            ItemStack out = familiar.copyWithCount(1);
            out.set(GDComponents.FAMILIAR.get(), data.with(rune.augment()));
            return out;
        }
        return null;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return result(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack out = result(input);
        return out == null ? ItemStack.EMPTY : out;
    }

    @Override
    public RecipeSerializer<FamiliarRecipe> getSerializer() {
        return SERIALIZER;
    }
}
