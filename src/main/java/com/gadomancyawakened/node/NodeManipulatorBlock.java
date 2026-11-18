package com.gadomancyawakened.node;

import com.gadomancyawakened.GDRegistry;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import com.leclowndu93150.thaumaturge.content.wands.WandVisHelper;
import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Node Manipulator block. Unformed, a wand used on it forms the structure (100 of each primal from that wand,
 * 150 with a Portal Focus).
 * Formed, it is a wand pedestal: use a wand to put it in, an empty hand to take it out.
 */
public class NodeManipulatorBlock extends BaseEntityBlock {
    public static final MapCodec<NodeManipulatorBlock> CODEC = simpleCodec(NodeManipulatorBlock::new);
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 14, 15);
    private static final Map<ResourceKey<IAspect>, Integer> FORM_COST = new HashMap<>();
    private static final Map<ResourceKey<IAspect>, Integer> PORTAL_FORM_COST = new HashMap<>();

    static {
        NodeManipulatorBlockEntity.PRIMALS.forEach(a -> FORM_COST.put(a, 100));
        NodeManipulatorBlockEntity.PRIMALS.forEach(a -> PORTAL_FORM_COST.put(a, 150));
    }

    public NodeManipulatorBlock(Properties properties) {
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof ItemWand) || !(level.getBlockEntity(pos) instanceof NodeManipulatorBlockEntity be)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!be.formed()) {
            if (!NodeManipulatorBlockEntity.pattern(level, pos)) {
                player.sendOverlayMessage(Component.translatable("gadomancyawakened.manipulator.incomplete"));
                return InteractionResult.FAIL;
            }
            Map<ResourceKey<IAspect>, Integer> cost = level.getBlockState(pos.above()).is(GDRegistry.PORTAL_FOCUS.get()) ? PORTAL_FORM_COST : FORM_COST;
            if (!WandVisHelper.payCosts(stack, player, cost, false, false)) {
                player.sendOverlayMessage(Component.translatable("gadomancyawakened.manipulator.no_vis"));
                return InteractionResult.FAIL;
            }
            be.setFormed(true);
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.8F);
            return InteractionResult.SUCCESS;
        }
        if (be.wand().isEmpty()) {
            be.setWand(stack.copyWithCount(1));
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof NodeManipulatorBlockEntity be && !be.wand().isEmpty()) {
            if (!level.isClientSide()) {
                player.getInventory().placeItemBackInInventory(be.wand());
                be.setWand(ItemStack.EMPTY);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NodeManipulatorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, GDRegistry.NODE_MANIPULATOR_BE.get(), NodeManipulatorBlockEntity::tick);
    }

    /** The Randomization Focus: a small block that stands only on a Node Manipulator. */
    public static class Focus extends Block {
        public static final MapCodec<Focus> CODEC = simpleCodec(Focus::new);
        private static final VoxelShape FOCUS = box(3, 0, 3, 13, 6, 13);

        public Focus(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return FOCUS;
        }

        @Override
        protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return level.getBlockState(pos.below()).is(GDRegistry.NODE_MANIPULATOR.get());
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks,
                                         BlockPos pos, net.minecraft.core.Direction direction, BlockPos neighbourPos,
                                         BlockState neighbour, net.minecraft.util.RandomSource random) {
            return canSurvive(state, level, pos) ? state : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
    }
}
