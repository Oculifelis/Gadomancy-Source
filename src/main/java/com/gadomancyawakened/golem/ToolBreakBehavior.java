package com.gadomancyawakened.golem;

import com.leclowndu93150.thaumaturge.api.golems.GolemHelper;
import com.leclowndu93150.thaumaturge.api.golems.IGolemAPI;
import com.leclowndu93150.thaumaturge.api.golems.seals.ISealBehavior;
import com.leclowndu93150.thaumaturge.api.golems.seals.ISealEntity;
import com.leclowndu93150.thaumaturge.api.golems.seals.ISealFilter;
import com.leclowndu93150.thaumaturge.api.golems.seals.SealArea;
import com.leclowndu93150.thaumaturge.api.golems.tasks.Task;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Gadomancy's Break core as a seal: the golem mines the blocks in the seal's area with a real tool it carries, so the
 * tool's speed, enchantments (Fortune, Silk Touch) and durability all apply. Thaumaturge's own breaker seal chips at a
 * fixed rate with bare hands and no Fortune; this is the tooled version.
 *
 * <p>Filter slots hold blocks to break (empty means any) and, as in the original, non-block items name the tools the
 * golem may use. A golem without a fitting tool takes one from the container the seal is placed on (the original's
 * "home chest"), returning a worn-out or wrong tool there first. Mining speed follows the original: one swing every
 * {@code 7 - min(6, strength)} work calls, each swing adding the tool's dig speed against the block's hardness.
 */
public final class ToolBreakBehavior implements ISealBehavior {
    private static final int SCAN_STAGGER = 31;
    private static final float WORK_PER_HARDNESS = 30.0F;
    private static final int FINAL_CRACK = 10;
    private static final int XP = 1;

    private final Map<Integer, BlockPos> claims = new HashMap<>();
    private int clock;

    @Override
    public void tick(ServerLevel level, ISealEntity seal) {
        int step = clock++;
        if (step % 100 == 0) {
            claims.values().removeIf(pos -> !isWorkable(level, seal, pos));
        }
        BlockPos cell = SealArea.cell(seal, step + SCAN_STAGGER);
        if (!level.hasChunkAt(cell) || claims.containsValue(cell) || !isWorkable(level, seal, cell)) {
            return;
        }
        Task task = Task.atBlock(seal.pos(), cell);
        task.setPriority(seal.priority());
        task.setData((int) (level.getBlockState(cell).getDestroySpeed(level, cell) * WORK_PER_HARDNESS) + 1);
        GolemHelper.addGolemTask(level, task);
        claims.put(task.id(), cell.immutable());
    }

    private static boolean isWorkable(Level level, ISealEntity seal, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.liquid() || state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        ISealFilter filter = seal.filter().orElse(null);
        if (filter == null) {
            return true;
        }
        boolean anyBlock = false;
        boolean matched = false;
        for (ItemStack entry : filter.stacks()) {
            if (entry.getItem() instanceof BlockItem blockItem) {
                anyBlock = true;
                matched |= blockItem.getBlock() == state.getBlock();
            }
        }
        return !anyBlock || matched != filter.isBlacklist();
    }

    /** A tool fits when it is a non-block item matching a tool slot of the filter, or any tool when no slot names one. */
    static boolean fitsTool(@Nullable ISealFilter filter, ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof BlockItem || !stack.has(DataComponents.TOOL)) {
            return false;
        }
        if (filter == null) {
            return true;
        }
        boolean anyTool = false;
        for (ItemStack entry : filter.stacks()) {
            if (!entry.isEmpty() && !(entry.getItem() instanceof BlockItem)) {
                anyTool = true;
                if (entry.is(stack.getItem())) {
                    return true;
                }
            }
        }
        return !anyTool;
    }

    @Override
    public boolean canPerform(ISealEntity seal, IGolemAPI golem, Task task) {
        if (claims.containsKey(task.id()) && isWorkable(golem.level(), seal, task.pos())) {
            return true;
        }
        task.end();
        return false;
    }

    @Override
    public boolean completeTask(ServerLevel level, ISealEntity seal, IGolemAPI golem, Task task) {
        BlockPos pos = task.pos();
        if (!claims.containsKey(task.id()) || !isWorkable(level, seal, pos)) {
            finish(task);
            return true;
        }
        ISealFilter filter = seal.filter().orElse(null);
        ItemStack tool = heldTool(golem, filter);
        if (tool.isEmpty() && !fetchTool(level, seal, golem, filter)) {
            finish(task); // nothing to dig with: leave the task for later
            return true;
        }
        tool = heldTool(golem, filter);
        golem.asEntity().setItemSlot(EquipmentSlot.MAINHAND, tool);
        BlockState state = level.getBlockState(pos);
        if (task.life() < 10) {
            task.setLife(10);
        }
        float speed = Math.max(1.0F, tool.getDestroySpeed(state));
        int strength = Math.max(1, (int) golem.properties().material().damage());
        int chip = Math.max(1, Mth.floor(speed * (1 + Math.min(6, strength)) * 0.75F));
        int left = task.data() - chip;
        golem.swingArm();
        int breaker = golem.asEntity().getId();
        if (left > 0) {
            task.setData(left);
            SoundType sound = state.getSoundType(level, pos, golem.asEntity());
            level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 8.0F, sound.getPitch() * 0.5F);
            float total = state.getDestroySpeed(level, pos) * WORK_PER_HARDNESS + 1;
            level.destroyBlockProgress(breaker, pos, Mth.clamp(Mth.floor(9 * (1.0F - left / total)), 0, 9));
            return false;
        }
        level.destroyBlockProgress(breaker, pos, FINAL_CRACK);
        Block.dropResources(state, level, pos, level.getBlockEntity(pos), golem.asEntity(), tool);
        level.destroyBlock(pos, false, golem.asEntity());
        tool.hurtAndBreak(1, level, golem.asEntity(), item -> {});
        golem.addRankXp(XP);
        finish(task);
        return true;
    }

    private void finish(Task task) {
        claims.remove(task.id());
        task.end();
    }

    private static ItemStack heldTool(IGolemAPI golem, @Nullable ISealFilter filter) {
        for (ItemStack stack : golem.hands().contents()) {
            if (fitsTool(filter, stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Swaps tools with the container under the seal: puts back anything tool-like it holds, then takes a fitting tool. */
    private static boolean fetchTool(ServerLevel level, ISealEntity seal, IGolemAPI golem, @Nullable ISealFilter filter) {
        BlockPos home = seal.pos().pos();
        Direction face = seal.pos().face();
        ResourceHandler<ItemResource> chest = level.getCapability(Capabilities.Item.BLOCK, home, face);
        if (chest == null || golem.asEntity().distanceToSqr(home.getCenter()) > 32 * 32) {
            return false;
        }
        for (int slot = 0; slot < chest.size(); slot++) {
            ItemResource resource = chest.getResource(slot);
            ItemStack candidate = resource.toStack(1);
            if (resource.isEmpty() || !fitsTool(filter, candidate) || golem.hands().room(candidate) <= 0) {
                continue;
            }
            try (Transaction tx = Transaction.openRoot()) {
                if (chest.extract(slot, resource, 1, tx) == 1) {
                    tx.commit();
                    golem.hands().hold(candidate);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onTaskSuspended(ServerLevel level, ISealEntity seal, Task task) {
        claims.remove(task.id());
    }
}
