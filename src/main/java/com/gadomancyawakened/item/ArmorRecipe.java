package com.gadomancyawakened.item;

import com.gadomancyawakened.GDComponents;
import com.gadomancyawakened.GDRegistry;
import com.gadomancyawakened.GadomancyAwakened;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Two armour crafts in the grid, both kept as components so nothing is lost:
 * <ul>
 * <li><b>Revealer</b>: a helmet with a Revealing Lens gains Thaumaturge's goggles upgrade (sees nodes and aspects).</li>
 * <li><b>Disguise</b>: a piece of armour, a slimeball and another piece for the same slot makes the first look like the
 * second; with a potion of invisibility instead it looks like nothing. The armour's {@code equippable} model is swapped
 * and the old one kept in {@code gadomancyawakened:disguise}; armour, slimeball and water bottle take the disguise off.</li>
 * </ul>
 */
public class ArmorRecipe extends CustomRecipe {
    public static final ArmorRecipe INSTANCE = new ArmorRecipe();
    public static final RecipeSerializer<ArmorRecipe> SERIALIZER = new RecipeSerializer<>(MapCodec.unit(INSTANCE),
            StreamCodec.<RegistryFriendlyByteBuf, ArmorRecipe>unit(INSTANCE));
    /** Deliberately has no asset file: a missing equipment asset draws nothing, and an empty one fails to load. */
    public static final ResourceKey<EquipmentAsset> INVISIBLE = ResourceKey.create(EquipmentAssets.ROOT_ID, GadomancyAwakened.id("invisible"));

    private static @Nullable EquipmentSlot armourSlot(ItemStack s) {
        Equippable e = s.get(DataComponents.EQUIPPABLE);
        return e != null && e.slot().isArmor() && e.assetId().isPresent() ? e.slot() : null;
    }

    private static @Nullable ItemStack result(CraftingInput input) {
        ItemStack armour = null;
        ItemStack other = null;
        boolean slime = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (s.is(Items.SLIME_BALL) && !slime) {
                slime = true;
            } else if (armour == null && armourSlot(s) != null) {
                armour = s;
            } else if (other == null) {
                other = s;
            } else {
                return null;
            }
        }
        if (armour == null || other == null) {
            return null;
        }
        Equippable worn = armour.get(DataComponents.EQUIPPABLE);
        if (!slime) {
            if (other.is(GDRegistry.REVEALING_LENS.get()) && worn.slot() == EquipmentSlot.HEAD && !armour.has(TTDataComponents.GOGGLES_UPGRADE.get())) {
                ItemStack out = armour.copyWithCount(1);
                out.set(TTDataComponents.GOGGLES_UPGRADE.get(), Unit.INSTANCE);
                return out;
            }
            return null;
        }
        Equippable original = armour.getOrDefault(GDComponents.DISGUISE.get(), worn);
        Optional<ResourceKey<EquipmentAsset>> look;
        PotionContents potion = other.get(DataComponents.POTION_CONTENTS);
        if (potion != null && (potion.is(Potions.INVISIBILITY) || potion.is(Potions.LONG_INVISIBILITY))) {
            look = Optional.of(INVISIBLE);
        } else if (potion != null && potion.is(Potions.WATER)) {
            if (!armour.has(GDComponents.DISGUISE.get())) {
                return null;
            }
            ItemStack out = armour.copyWithCount(1);
            out.set(DataComponents.EQUIPPABLE, original);
            out.remove(GDComponents.DISGUISE.get());
            return out;
        } else if (armourSlot(other) == worn.slot()) {
            look = other.get(DataComponents.EQUIPPABLE).assetId();
        } else {
            return null;
        }
        ItemStack out = armour.copyWithCount(1);
        out.set(GDComponents.DISGUISE.get(), original);
        out.set(DataComponents.EQUIPPABLE, new Equippable(worn.slot(), worn.equipSound(), look, worn.cameraOverlay(),
                worn.allowedEntities(), worn.dispensable(), worn.swappable(), worn.damageOnHurt(), worn.equipOnInteract(),
                worn.canBeSheared(), worn.shearingSound()));
        return out;
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
    public RecipeSerializer<ArmorRecipe> getSerializer() {
        return SERIALIZER;
    }
}
