package com.gadomancyawakened.machine;

import com.gadomancyawakened.GDDataMaps;
import com.gadomancyawakened.GDRegistry;
import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import com.leclowndu93150.thaumaturge.content.wands.WandVisHelper;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Essentia Compressor: three of these stacked and struck with a wand form one tall store. The bottom holds the essentia,
 * tubes connect to the four sides of the middle, and breaking any part blows the whole thing apart.
 */
public class EssentiaCompressorBlock extends BaseEntityBlock {
    public static final MapCodec<EssentiaCompressorBlock> CODEC = simpleCodec(EssentiaCompressorBlock::new);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    private static final VoxelShape FORMED = box(1, 0, 1, 15, 16, 15);

    public EssentiaCompressorBlock(Properties properties) {
        super(properties.lightLevel(s -> s.getValue(PART) == Part.BOTTOM ? 8 : 0));
        registerDefaultState(stateDefinition.any().setValue(PART, Part.NONE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PART) == Part.NONE ? Shapes.block() : FORMED;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof ItemWand) || state.getValue(PART) != Part.NONE) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        BlockPos bottom = pos;
        while (isLoose(level, bottom.below())) {
            bottom = bottom.below();
        }
        if (!isLoose(level, bottom.above()) || !isLoose(level, bottom.above(2))) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!WandVisHelper.payCosts(stack, player, GDDataMaps.formCost(this), false, false)) {
            player.sendOverlayMessage(Component.translatable("gadomancyawakened.compressor.no_vis"));
            return InteractionResult.FAIL;
        }
        level.setBlock(bottom, defaultBlockState().setValue(PART, Part.BOTTOM), 3);
        level.setBlock(bottom.above(), defaultBlockState().setValue(PART, Part.MIDDLE), 3);
        level.setBlock(bottom.above(2), defaultBlockState().setValue(PART, Part.TOP), 3);
        level.playSound(null, bottom.above(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 0.6F);
        return InteractionResult.SUCCESS;
    }

    private boolean isLoose(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(this) && state.getValue(PART) == Part.NONE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EssentiaCompressorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || state.getValue(PART) == Part.NONE ? null
                : createTickerHelper(type, GDRegistry.ESSENTIA_COMPRESSOR_BE.get(), EssentiaCompressorBlockEntity::tick);
    }

    public enum Part implements StringRepresentable {
        NONE, BOTTOM, MIDDLE, TOP;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** How far below this part the master (bottom) block sits. */
        public int depth() {
            return ordinal() - 1;
        }
    }
}
