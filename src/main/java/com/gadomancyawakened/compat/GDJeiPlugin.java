package com.gadomancyawakened.compat;

import com.gadomancyawakened.GDRegistry;
import com.gadomancyawakened.GadomancyAwakened;
import com.gadomancyawakened.aura.AuraEffect;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCrystalAccess;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * JEI: an Aura Pylon category, one page per entry of the synced {@code gadomancyawakened:aura_effect} registry (the essentia
 * that feeds it, what it does, how often and how far), and information pages for the crafting-grid specials (sticky
 * jars, familiar runes, the revealing lens) and the devices. Arcane, crucible and infusion recipes are Thaumaturge's
 * own types, which its plugin shows. Loaded only with JEI.
 */
@JeiPlugin
public final class GDJeiPlugin implements IModPlugin {
    public record Aura(Identifier aspect, ItemStack essentia, AuraEffect effect) {}

    static final IRecipeType<Aura> AURA_PYLON = IRecipeType.create(GadomancyAwakened.id("aura_pylon"), Aura.class);

    private static final String[] INFO = {"arcane_package", "arcane_package_essentia", "arcane_packager", "ethereal_familiar",
            "familiar_augment", "remote_jar", "revealing_lens", "infusion_claw", "block_protector", "knowledge_book",
            "essentia_compressor", "node_manipulator", "aura_core", "aura_pylon"};

    @Override
    public Identifier getPluginUid() {
        return GadomancyAwakened.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new AuraCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(AURA_PYLON, GDRegistry.AURA_PYLON_PEAK.get(), GDRegistry.AURA_PYLON.get());
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<Aura> auras = new ArrayList<>();
        var level = Minecraft.getInstance().level;
        if (level != null) {
            var aspects = level.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY);
            level.registryAccess().lookup(AuraEffect.REGISTRY_KEY).ifPresent(registry -> registry.listElements().forEach(entry ->
                    aspects.get(entry.value().aspect()).ifPresent(aspect -> auras.add(new Aura(aspect.key().identifier(),
                            EssentiaCrystalAccess.create(aspect, 1), entry.value())))));
        }
        auras.sort((a, b) -> a.aspect().compareTo(b.aspect()));
        registration.addRecipes(AURA_PYLON, auras);

        for (String name : INFO) {
            BuiltInRegistries.ITEM.getOptional(GadomancyAwakened.id(name)).ifPresent(item ->
                    registration.addIngredientInfo(item, Component.translatable("jei.gadomancyawakened.info." + name)));
        }
    }

    private static final class AuraCategory extends AbstractRecipeCategory<Aura> {
        AuraCategory(IGuiHelper gui) {
            super(AURA_PYLON, Component.translatable("jei.gadomancyawakened.aura_pylon"),
                    gui.createDrawableItemLike(GDRegistry.AURA_PYLON_PEAK.get()), 160, 54);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Aura recipe, IFocusGroup focuses) {
            builder.addInputSlot(4, 5).setStandardSlotBackground().add(recipe.essentia());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Aura recipe, IFocusGroup focuses) {
            builder.addText(Component.translatable("jei.gadomancyawakened.aura_pylon.reach", recipe.effect().interval(),
                    recipe.effect().range()), 130, 10).setPosition(28, 9).setColor(0xFF707070);
            builder.addText(Component.translatable("jei.gadomancyawakened.aura." + recipe.aspect().getPath()), 160, 30)
                    .setPosition(0, 26).setColor(0xFF404040);
        }
    }
}
