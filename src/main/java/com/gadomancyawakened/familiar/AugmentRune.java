package com.gadomancyawakened.familiar;

import com.gadomancyawakened.GDComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** An augment rune, made by infusion, that a familiar takes in the crafting grid. Its kind and level are a component. */
public class AugmentRune extends Item {
    public record Rune(Augment augment, int level) {
        public static final Codec<Rune> CODEC = RecordCodecBuilder.create(i -> i.group(
                Augment.CODEC.fieldOf("augment").forGetter(Rune::augment),
                Codec.intRange(1, 3).fieldOf("level").forGetter(Rune::level)).apply(i, Rune::new));
        public static final StreamCodec<ByteBuf, Rune> STREAM_CODEC = StreamCodec.composite(
                Augment.STREAM_CODEC, Rune::augment, ByteBufCodecs.VAR_INT, Rune::level, Rune::new);
    }

    public AugmentRune(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        Rune rune = stack.get(GDComponents.AUGMENT.get());
        if (rune != null) {
            out.accept(Component.translatable("gadomancyawakened.augment." + rune.augment().getSerializedName())
                    .append(" ").append(Component.translatable("enchantment.level." + rune.level())).withStyle(ChatFormatting.GOLD));
        }
    }
}
