package com.gadomancyawakened.jar;

import com.gadomancyawakened.GDComponents;
import com.gadomancyawakened.GDRegistry;
import com.leclowndu93150.thaumaturge.registry.TTBlocks;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/** Places sticky jar items against any face, and adds the sticky and network lines to jar tooltips. */
public final class JarEvents {
    private JarEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(JarEvents::onUseBlock);
        NeoForge.EVENT_BUS.addListener(JarEvents::onTooltip);
    }

    static @Nullable JarKind kindOf(ItemStack stack) {
        if (stack.is(TTBlocks.JAR_NORMAL.get().asItem())) {
            return JarKind.NORMAL;
        }
        if (stack.is(TTBlocks.JAR_VOID.get().asItem())) {
            return JarKind.VOID;
        }
        return stack.is(GDRegistry.REMOTE_JAR.get().asItem()) ? JarKind.REMOTE : null;
    }

    private static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        JarKind kind = kindOf(stack);
        Player player = event.getEntity();
        if (kind == null || !stack.has(GDComponents.STICKY.get()) || player.isShiftKeyDown()) {
            return;
        }
        Level level = event.getLevel();
        Direction face = event.getFace() == null ? Direction.UP : event.getFace();
        BlockPos target = event.getPos().relative(face);
        BlockPlaceContext ctx = new BlockPlaceContext(player, event.getHand(), stack, event.getHitVec());
        BlockState here = level.getBlockState(target);
        if (!here.canBeReplaced(ctx) || !player.mayUseItemAt(target, face, stack)) {
            return;
        }
        BlockState jar = GDRegistry.STICKY_JAR.get().defaultBlockState()
                .setValue(StickyJarBlock.MOUNT, face.getOpposite()).setValue(StickyJarBlock.KIND, kind);
        if (!jar.canSurvive(level, target)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide()) {
            return;
        }
        level.setBlock(target, jar, 3);
        if (level.getBlockEntity(target) instanceof GDJarBlockEntity be) {
            be.applyComponentsFromItemStack(stack);
            be.setFacing(face.getAxis().isHorizontal() ? face : player.getDirection().getOpposite());
            be.setChanged();
        }
        level.playSound(null, target, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        stack.consume(1, player);
    }

    private static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.has(GDComponents.STICKY.get())) {
            event.getToolTip().add(1, Component.translatable("gadomancyawakened.jar.sticky").withStyle(ChatFormatting.GREEN));
        }
        UUID network = stack.get(GDComponents.JAR_NETWORK.get());
        if (network != null) {
            event.getToolTip().add(1, Component.translatable("gadomancyawakened.remote_jar.network", RemoteJarItem.networkName(network))
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
    }
}
