package com.gadomancyawakened.machine;

import com.gadomancyawakened.GDRegistry;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.content.infusion.BlockEntityInfusionMatrix;
import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import com.leclowndu93150.thaumaturge.content.wands.WandVisHelper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Infusion Claw. Holds a wand; on a rising redstone edge, if an infusion matrix sits right below and the wand holds at
 * least 250 of every primal, it winds up for 290 ticks, then strikes the matrix with the wand on its owner's behalf
 * (the owner's research decides the recipe), paying 50 of each primal; 440 ticks in all before it can fire again.
 * Every 21 ticks it tops the wand up by 100 of each primal, drawing vis from the chunk's aura.
 * Differences from the original: the owner must be online when it strikes (Thaumaturge keeps research only on the
 * live player), and the matrix's instability is the matrix's own, as Thaumaturge has no way to add to it.
 */
public class InfusionClawBlockEntity extends BlockEntity {
    public static final int COOLDOWN = 440;
    public static final int STRIKE_AT = 150;
    private static final int MIN_VIS = 250;
    private static final List<ResourceKey<IAspect>> PRIMALS = List.of(TTAspects.AER, TTAspects.TERRA, TTAspects.IGNIS,
            TTAspects.AQUA, TTAspects.ORDO, TTAspects.PERDITIO);
    private static final Map<ResourceKey<IAspect>, Integer> STRIKE_COST = new LinkedHashMap<>();

    static {
        PRIMALS.forEach(a -> STRIKE_COST.put(a, 50));
    }

    private ItemStack wand = ItemStack.EMPTY;
    private @Nullable UUID owner;
    private boolean locked;
    private boolean powered;
    private int cooldown;
    private int clock;

    public InfusionClawBlockEntity(BlockPos pos, BlockState state) {
        super(GDRegistry.INFUSION_CLAW_BE.get(), pos, state);
    }

    public ItemStack wand() {
        return wand;
    }

    public boolean running() {
        return cooldown > 0;
    }

    void setOwner(UUID id) {
        owner = id;
        setChanged();
    }

    @Nullable UUID owner() {
        return owner;
    }

    boolean toggleLock() {
        locked = !locked;
        setChanged();
        return locked;
    }

    void setWand(ItemStack stack) {
        wand = stack;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** May a hopper or hand take the wand out now? Not while working, nor while locked with a wand still able to work. */
    boolean canRemoveWand() {
        return !running() && !(locked && enoughVis());
    }

    private boolean enoughVis() {
        if (!(wand.getItem() instanceof ItemWand)) {
            return false;
        }
        for (ResourceKey<IAspect> a : PRIMALS) {
            if (WandVisHelper.storedIn(wand, a) < MIN_VIS) {
                return false;
            }
        }
        return true;
    }

    void redstone(boolean now) {
        if (now && !powered && !running() && enoughVis() && level != null
                && level.getBlockEntity(worldPosition.below()) instanceof BlockEntityInfusionMatrix) {
            cooldown = COOLDOWN;
            level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.6F, 0.6F);
            setChanged();
        }
        powered = now;
    }

    public int signal() {
        return level != null && level.getBlockEntity(worldPosition.below()) instanceof BlockEntityInfusionMatrix m && m.isCrafting() ? 15 : 0;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, InfusionClawBlockEntity be) {
        ServerLevel server = (ServerLevel) level;
        if (++be.clock % 21 == 0) {
            be.recharge(server);
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
        if (be.cooldown <= 0) {
            return;
        }
        be.cooldown--;
        if (be.cooldown % 10 == 0) {
            server.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 6, 0.2, 0.1, 0.2, 0.4);
        }
        if (be.cooldown == STRIKE_AT) {
            be.strike(server);
        }
        be.setChanged();
    }

    private void strike(ServerLevel level) {
        if (!(level.getBlockEntity(worldPosition.below()) instanceof BlockEntityInfusionMatrix matrix) || owner == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player == null || !WandVisHelper.payExact(wand, STRIKE_COST, false)) {
            return;
        }
        matrix.onCasterRightClick(level, wand, player, worldPosition.below(), Direction.UP, InteractionHand.MAIN_HAND);
        level.playSound(null, worldPosition, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    private void recharge(ServerLevel level) {
        if (!(wand.getItem() instanceof ItemWand)) {
            return;
        }
        int max = WandVisHelper.capacityOf(wand);
        boolean changed = false;
        for (ResourceKey<IAspect> a : PRIMALS) {
            int room = Math.min(100, max - WandVisHelper.storedIn(wand, a));
            if (room <= 0) {
                continue;
            }
            float drawn = AuraHelper.drainVis(level, worldPosition, room / 100.0F, false);
            int add = (int) (drawn * 100);
            if (add > 0) {
                WandVisHelper.topUpCentivis(wand, a, add, false);
                changed = true;
            }
        }
        if (changed) {
            setChanged();
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !wand.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, wand);
            wand = ItemStack.EMPTY;
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        wand = input.read("wand", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        locked = input.getBooleanOr("locked", false);
        powered = input.getBooleanOr("powered", false);
        cooldown = input.getIntOr("cooldown", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!wand.isEmpty()) {
            output.store("wand", ItemStack.OPTIONAL_CODEC, wand);
        }
        output.storeNullable("owner", UUIDUtil.CODEC, owner);
        output.putBoolean("locked", locked);
        output.putBoolean("powered", powered);
        output.putInt("cooldown", cooldown);
    }
}
