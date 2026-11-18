package com.gadomancyawakened.aura;

import com.gadomancyawakened.GDRegistry;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import com.leclowndu93150.thaumaturge.content.wands.WandVisHelper;
import com.mojang.serialization.MapCodec;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Aura Pylon: three pylons with a Pylon Peak on top, struck on the peak with a wand (150 of each primal), form one
 * pylon. The parts are BASE (bottom, takes essentia), SHAFT and CORE (holds the crystal and runs the effects).
 */
public class AuraPylonBlock extends BaseEntityBlock {
    public static final MapCodec<AuraPylonBlock> CODEC = simpleCodec(AuraPylonBlock::new);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 16, 15);
    static final Map<ResourceKey<IAspect>, Integer> FORM_COST = Map.of(TTAspects.AER, 150, TTAspects.TERRA, 150,
            TTAspects.IGNIS, 150, TTAspects.AQUA, 150, TTAspects.ORDO, 150, TTAspects.PERDITIO, 150);

    public AuraPylonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.LOOSE));
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
        return SHAPE;
    }

    /** Called by the peak: forms the pylon below it if three loose pylons stand there and the wand can pay. */
    static InteractionResult tryForm(ItemStack wand, Level level, BlockPos peak, Player player) {
        if (!(wand.getItem() instanceof ItemWand)) {
            return InteractionResult.PASS;
        }
        for (int i = 1; i <= 3; i++) {
            BlockState s = level.getBlockState(peak.below(i));
            if (!s.is(GDRegistry.AURA_PYLON.get()) || s.getValue(PART) != Part.LOOSE) {
                return InteractionResult.PASS;
            }
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!WandVisHelper.payCosts(wand, player, FORM_COST, false, false)) {
            player.sendOverlayMessage(Component.translatable("gadomancyawakened.pylon.no_vis"));
            return InteractionResult.FAIL;
        }
        BlockState pylon = GDRegistry.AURA_PYLON.get().defaultBlockState();
        level.setBlock(peak.below(), pylon.setValue(PART, Part.CORE), 3);
        level.setBlock(peak.below(2), pylon.setValue(PART, Part.SHAFT), 3);
        level.setBlock(peak.below(3), pylon.setValue(PART, Part.BASE), 3);
        level.playSound(null, peak, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    /** True while every part this block's pylon needs is in place. */
    static boolean intact(Level level, BlockPos pos, BlockState state) {
        Part part = state.getValue(PART);
        if (part == Part.LOOSE) {
            return true;
        }
        BlockPos core = pos.above(part.depth());
        if (!level.getBlockState(core.above()).is(GDRegistry.AURA_PYLON_PEAK.get())) {
            return false;
        }
        for (Part p : new Part[] {Part.CORE, Part.SHAFT, Part.BASE}) {
            BlockState s = level.getBlockState(core.below(p.depth()));
            if (!s.is(GDRegistry.AURA_PYLON.get()) || s.getValue(PART) != p) {
                return false;
            }
        }
        return true;
    }

    /** Turns whatever is left of a broken pylon back into loose pylons. */
    static void unform(Level level, BlockPos pos, BlockState state) {
        BlockPos core = pos.above(state.getValue(PART).depth());
        for (int i = 0; i < 3; i++) {
            BlockState s = level.getBlockState(core.below(i));
            if (s.is(GDRegistry.AURA_PYLON.get()) && s.getValue(PART) != Part.LOOSE) {
                level.setBlock(core.below(i), s.setValue(PART, Part.LOOSE), 3);
            }
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AuraPylonBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || state.getValue(PART) == Part.LOOSE ? null
                : createTickerHelper(type, GDRegistry.AURA_PYLON_BE.get(), AuraPylonBlockEntity::tick);
    }

    public enum Part implements StringRepresentable {
        LOOSE, CORE, SHAFT, BASE;

        /** How far below the core this part sits. */
        int depth() {
            return this == LOOSE ? 0 : ordinal() - 1;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The Pylon Peak: sits on a pylon; a wand here forms the pylon, an empty hand takes the crystal back. */
    public static class Peak extends Block {
        public static final MapCodec<Peak> CODEC = simpleCodec(Peak::new);
        private static final VoxelShape PEAK = box(3, 0, 3, 13, 10, 13);

        public Peak(Properties properties) {
            super(properties.lightLevel(s -> 10));
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return PEAK;
        }

        @Override
        protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
            return level.getBlockState(pos.below()).is(GDRegistry.AURA_PYLON.get());
        }

        @Override
        protected BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level,
                                         net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos,
                                         net.minecraft.core.Direction direction, BlockPos neighbourPos, BlockState neighbour,
                                         net.minecraft.util.RandomSource random) {
            return canSurvive(state, level, pos) ? state : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
            InteractionResult r = tryForm(stack, level, pos, player);
            return r == InteractionResult.PASS ? super.useItemOn(stack, state, level, pos, player, hand, hit) : r;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level.getBlockEntity(pos.below()) instanceof AuraPylonBlockEntity core) {
                if (!level.isClientSide()) {
                    ItemStack crystal = core.takeCrystal();
                    if (!crystal.isEmpty()) {
                        player.getInventory().placeItemBackInInventory(crystal);
                    }
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
    }
}
