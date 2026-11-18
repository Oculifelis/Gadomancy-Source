package com.gadomancyawakened.machine;

import com.gadomancyawakened.GDRegistry;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Holds up to 8 Ordo, taken from a tube or container below. Every 15 ticks it steps its range towards the redstone
 * power it gets: growing costs 1 Ordo and then pauses for (16 - range) cycles, holding costs 1 Ordo a cycle, and
 * running dry or losing power shrinks it by one. Values as in Gadomancy 1.5.17.
 */
public class BlockProtectorBlockEntity extends BlockEntity implements IEssentiaTransport {
    public static final int CAPACITY = 8;
    private static final int SUCTION = 64;

    private int ordo;
    private int saturation;
    private int clock;

    public BlockProtectorBlockEntity(BlockPos pos, BlockState state) {
        super(GDRegistry.BLOCK_PROTECTOR_BE.get(), pos, state);
    }

    public int range() {
        return getBlockState().getValue(BlockProtectorBlock.RANGE);
    }

    /** The protected cube, or null when the field is down. */
    public @Nullable AABB field() {
        int r = range();
        return r <= 0 ? null : new AABB(worldPosition).inflate(r);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BlockProtectorBlockEntity be) {
        Protectors.track(be);
        be.clock++;
        if (be.clock % 5 == 0 && be.ordo < CAPACITY) {
            be.pullOrdo(level, pos);
        }
        if (be.clock % 15 != 0) {
            return;
        }
        if (be.saturation > 0) {
            be.saturation--;
            return;
        }
        int range = be.range();
        int target = Math.min(15, level.getBestNeighborSignal(pos));
        int next = range;
        if (range < target) {
            if (be.ordo > 0) {
                be.ordo--;
                next = range + 1;
                be.saturation = 16 - next;
            }
        } else if (range == target && range > 0) {
            if (be.ordo > 0) {
                be.ordo--;
            } else {
                next = range - 1;
            }
        } else if (range > target) {
            next = range - 1;
        }
        if (next != range) {
            level.setBlock(pos, state.setValue(BlockProtectorBlock.RANGE, next), 3);
        }
        be.setChanged();
    }

    private void pullOrdo(Level level, BlockPos pos) {
        IEssentiaTransport below = level.getCapability(EssentiaCapabilities.TRANSPORT, pos.below(), Direction.UP);
        if (below == null || !below.canOutputTo(Direction.UP)) {
            return;
        }
        Holder<IAspect> type = below.getEssentiaType(Direction.UP);
        if (type != null && type.is(TTAspects.ORDO) && below.takeEssentia(type, 1, Direction.UP) == 1) {
            ordo++;
            setChanged();
        }
    }

    @Override
    public void setRemoved() {
        Protectors.untrack(this);
        super.setRemoved();
    }

    // ---- essentia: Ordo in from below only ----

    private @Nullable Holder<IAspect> ordoHolder() {
        return level == null ? null : Aspects.resolve(level, TTAspects.ORDO);
    }

    @Override
    public boolean isConnectable(Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    public boolean canOutputTo(Direction face) {
        return false;
    }

    @Override
    public void setSuction(Holder<IAspect> type, int amount) {
    }

    @Override
    public @Nullable Holder<IAspect> getSuctionType(Direction face) {
        return ordo < CAPACITY ? ordoHolder() : null;
    }

    @Override
    public int getSuctionAmount(Direction face) {
        return ordo < CAPACITY && face == Direction.DOWN ? SUCTION : 0;
    }

    @Override
    public int takeEssentia(Holder<IAspect> type, int amount, Direction face) {
        return 0;
    }

    @Override
    public int addEssentia(Holder<IAspect> type, int amount, Direction face) {
        if (face != Direction.DOWN || !type.is(TTAspects.ORDO)) {
            return 0;
        }
        int added = Math.min(amount, CAPACITY - ordo);
        ordo += added;
        if (added > 0) {
            setChanged();
        }
        return added;
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction face) {
        return ordo > 0 ? ordoHolder() : null;
    }

    @Override
    public int getEssentiaAmount(Direction face) {
        return ordo;
    }

    @Override
    public int getMinimumSuction() {
        return 0;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ordo = input.getIntOr("ordo", 0);
        saturation = input.getIntOr("saturation", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("ordo", ordo);
        output.putInt("saturation", saturation);
    }
}
