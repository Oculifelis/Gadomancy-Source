package com.gadomancyawakened.aura;

import com.gadomancyawakened.GDComponents;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Aura Core: blank until attuned by {@link AuraCoreRitual}; its primal is the {@code gadomancyawakened:core_aspect} component. */
public class AuraCoreItem extends Item {
    public AuraCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        ResourceKey<IAspect> aspect = stack.get(GDComponents.CORE_ASPECT.get());
        out.accept(aspect == null ? Component.translatable("gadomancyawakened.aura_core.blank").withStyle(ChatFormatting.GRAY)
                : Component.translatable("aspect." + aspect.identifier().getNamespace() + "." + aspect.identifier().getPath())
                        .withStyle(ChatFormatting.GOLD));
    }
}
