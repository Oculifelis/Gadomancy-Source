package com.gadomancyawakened.jar;

import com.gadomancyawakened.GDComponents;
import com.gadomancyawakened.GDRegistry;
import com.leclowndu93150.thaumaturge.content.essentia.jar.AbstractBlockEntityJar;
import com.leclowndu93150.thaumaturge.content.essentia.jar.AbstractBlockJar;
import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A jar stuck to a wall or ceiling with slime. MOUNT is the face the jar's bottom rests against; KIND says which jar it
 * is (warded, void or remote), and it drops as that jar, still sticky and with its contents.
 */
public class StickyJarBlock extends AbstractBlockJar {
    public static final MapCodec<StickyJarBlock> CODEC = simpleCodec(StickyJarBlock::new);
    public static final EnumProperty<Direction> MOUNT = EnumProperty.create("mount", Direction.class);
    public static final EnumProperty<JarKind> KIND = EnumProperty.create("kind", JarKind.class);
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.DOWN, box(3, 0, 3, 13, 14, 13));
        SHAPES.put(Direction.UP, box(3, 2, 3, 13, 16, 13));
        SHAPES.put(Direction.NORTH, box(3, 3, 0, 13, 13, 14));
        SHAPES.put(Direction.SOUTH, box(3, 3, 2, 13, 13, 16));
        SHAPES.put(Direction.WEST, box(0, 3, 3, 14, 13, 13));
        SHAPES.put(Direction.EAST, box(2, 3, 3, 16, 13, 13));
    }

    public StickyJarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MOUNT, Direction.DOWN).setValue(KIND, JarKind.NORMAL));
    }

    @Override
    protected MapCodec<StickyJarBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<? extends AbstractBlockEntityJar> jarType() {
        return GDRegistry.STICKY_JAR_BE.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MOUNT, KIND);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(MOUNT));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction mount = state.getValue(MOUNT);
        return level.getBlockState(pos.relative(mount)).isFaceSturdy(level, pos.relative(mount), mount.getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return GDJarBlockEntity.sticky(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, GDRegistry.STICKY_JAR_BE.get(), GDJarBlockEntity::tick);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        ItemStack jar = new ItemStack(state.getValue(KIND).item());
        if (be != null) {
            jar.applyComponents(be.collectComponents());
        }
        jar.set(GDComponents.STICKY.get(), Unit.INSTANCE);
        return List.of(jar);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        ItemStack jar = new ItemStack(state.getValue(KIND).item());
        jar.set(GDComponents.STICKY.get(), Unit.INSTANCE);
        return jar;
    }
}
