package com.gadomancyawakened.machine;

import com.gadomancyawakened.GDRegistry;
import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Infusion Claw block. Use a wand on it to hand it the wand, an empty hand to take the wand back, and sneak with an
 * empty hand to lock or unlock it (a locked claw keeps a wand that still has vis enough to work). The placer owns it.
 */
public class InfusionClawBlock extends BaseEntityBlock {
    public static final MapCodec<InfusionClawBlock> CODEC = simpleCodec(InfusionClawBlock::new);

    public InfusionClawBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof InfusionClawBlockEntity claw) {
            claw.setOwner(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof ItemWand) || !(level.getBlockEntity(pos) instanceof InfusionClawBlockEntity claw)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!claw.wand().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            if (claw.owner() == null) {
                claw.setOwner(player.getUUID());
            }
            claw.setWand(stack.copyWithCount(1));
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof InfusionClawBlockEntity claw)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            boolean locked = claw.toggleLock();
            player.sendOverlayMessage(Component.translatable(locked ? "gadomancyawakened.claw.locked" : "gadomancyawakened.claw.unlocked"));
        } else if (!claw.wand().isEmpty() && claw.canRemoveWand()) {
            player.getInventory().placeItemBackInInventory(claw.wand());
            claw.setWand(ItemStack.EMPTY);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moved) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof InfusionClawBlockEntity claw) {
            claw.redstone(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof InfusionClawBlockEntity claw ? claw.signal() : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InfusionClawBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, GDRegistry.INFUSION_CLAW_BE.get(), InfusionClawBlockEntity::tick);
    }
}
