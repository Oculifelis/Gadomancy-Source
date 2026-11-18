package com.gadomancyawakened.jar;

import com.gadomancyawakened.GDComponents;
import com.leclowndu93150.thaumaturge.content.essentia.jar.JarItem;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Sneak-use in the air to clear the item's link. The tooltip names its network with two made-up words. */
public class RemoteJarItem extends JarItem {
    private static final String[] SYLLABLES = {"ar", "vel", "mor", "tha", "um", "zi", "ok", "rin", "dra", "es", "qua", "lo",
            "nyx", "pe", "sor", "ix", "an", "ul", "ber", "go", "ith", "ca", "fen", "ro"};

    public RemoteJarItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || !stack.has(GDComponents.JAR_NETWORK.get())) {
            return super.use(level, player, hand);
        }
        if (!level.isClientSide()) {
            stack.remove(GDComponents.JAR_NETWORK.get());
            player.sendOverlayMessage(Component.translatable("gadomancyawakened.remote_jar.clear"));
        }
        return InteractionResult.SUCCESS;
    }

    /** A stable, pronounceable two-word name for a network (the original drew random words from its lang file). */
    public static Component networkName(UUID id) {
        long bits = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        return Component.literal(word(bits) + " " + word(bits >>> 24));
    }

    private static String word(long bits) {
        StringBuilder out = new StringBuilder();
        int count = 2 + (int) Math.floorMod(bits, 2L);
        for (int i = 0; i < count; i++) {
            out.append(SYLLABLES[(int) Math.floorMod(bits >>> (i * 5 + 1), (long) SYLLABLES.length)]);
        }
        out.setCharAt(0, Character.toUpperCase(out.charAt(0)));
        return out.toString();
    }
}
