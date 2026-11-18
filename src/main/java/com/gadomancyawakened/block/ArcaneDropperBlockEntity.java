package com.gadomancyawakened.block;

import com.gadomancyawakened.GDRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Arcane Dropper: the dropped items in the 3x3x3 area two blocks in front of it act as its inventory. Pipes, hoppers
 * and golems can take items from that area, and anything put in is dropped out of the dropper's face. One slot per
 * item entity (up to 27) plus ten empty slots for insertion, as in the original.
 */
public class ArcaneDropperBlockEntity extends BlockEntity {
    static final int ENTITY_SLOTS = 27;
    static final int SLOTS = ENTITY_SLOTS + 10;

    private final @Nullable ItemEntity[] bound = new ItemEntity[SLOTS];
    private int signal;

    private final Slots handler = new Slots();

    private final class Slots extends ItemStacksResourceHandler {
        Slots() {
            super(SLOTS);
        }

        ItemStack raw(int index) {
            return stacks.get(index);
        }

        @Override
        public ItemResource getResource(int index) {
            refresh(index);
            return super.getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            refresh(index);
            return super.getAmountAsLong(index);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            refresh(index);
            return super.insert(index, resource, amount, transaction);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            refresh(index);
            return super.extract(index, resource, amount, transaction);
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            applyToWorld(index, stacks.get(index));
        }
    }

    public ArcaneDropperBlockEntity(BlockPos pos, BlockState state) {
        super(GDRegistry.ARCANE_DROPPER_BE.get(), pos, state);
    }

    public ResourceHandler<ItemResource> handler() {
        return handler;
    }

    int signal() {
        return signal;
    }

    private Direction facing() {
        return getBlockState().getValue(ArcaneDropperBlock.FACING);
    }

    /** The 3x3x3 area centred two blocks in front of the dropper. */
    public AABB area() {
        Vec3 centre = Vec3.atCenterOf(worldPosition.relative(facing(), 2));
        return AABB.ofSize(centre, 3, 3, 3);
    }

    /** Brings one slot back in line with its item entity, which may have been picked up or merged since. */
    private void refresh(int index) {
        ItemEntity entity = bound[index];
        if (entity == null) {
            return;
        }
        if (entity.isRemoved() || entity.getItem().isEmpty()) {
            bound[index] = null;
            setSlot(index, ItemStack.EMPTY);
        } else if (!ItemStack.matches(entity.getItem(), handler.raw(index))) {
            setSlot(index, entity.getItem());
        }
    }

    private void setSlot(int index, ItemStack stack) {
        handler.set(index, stack.isEmpty() ? ItemResource.EMPTY : ItemResource.of(stack), stack.getCount());
    }

    /** A committed change to a slot: shrink, grow or remove its item entity, or drop a new one out of the face. */
    private void applyToWorld(int index, ItemStack now) {
        if (level == null || level.isClientSide()) {
            return;
        }
        ItemEntity entity = bound[index];
        boolean alive = entity != null && !entity.isRemoved();
        if (now.isEmpty()) {
            if (alive) {
                entity.discard();
            }
            bound[index] = null;
        } else if (alive && ItemStack.isSameItemSameComponents(entity.getItem(), now)) {
            entity.setItem(now.copy());
        } else {
            if (alive) {
                entity.discard();
            }
            bound[index] = drop(now.copy());
        }
        setChanged();
    }

    private ItemEntity drop(ItemStack stack) {
        Direction side = facing();
        Vec3 at = Vec3.atCenterOf(worldPosition).add(side.getStepX() * 0.5, side.getStepY() * 0.5 - 0.25, side.getStepZ() * 0.5);
        ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z, stack);
        entity.setDeltaMovement(side.getStepX() * 0.1, side.getStepY() * 0.1, side.getStepZ() * 0.1);
        entity.setPickUpDelay(3);
        level.addFreshEntity(entity);
        return entity;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ArcaneDropperBlockEntity be) {
        List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, be.area(), Entity::isAlive);
        float fill = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemEntity entity = i < ENTITY_SLOTS && i < found.size() ? found.get(i) : null;
            be.bound[i] = entity;
            ItemStack stack = entity == null ? ItemStack.EMPTY : entity.getItem();
            be.setSlot(i, stack);
            if (!stack.isEmpty()) {
                fill += stack.getCount() / (float) stack.getMaxStackSize();
            }
        }
        int newSignal = fill <= 0 ? 0 : 1 + (int) (fill / SLOTS * 14);
        if (newSignal != be.signal) {
            be.signal = newSignal;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }
}
