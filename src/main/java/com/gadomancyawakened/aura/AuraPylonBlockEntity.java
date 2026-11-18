package com.gadomancyawakened.aura;

import com.gadomancyawakened.GDRegistry;
import com.gadomancyawakened.aura.AuraPylonBlock.Part;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One block of a formed Aura Pylon. The core (just under the peak) draws in the nearest vis crystal within 8 blocks
 * and takes its aspect; the base takes that aspect from a tube below (suction 64, 1 every 16 ticks, up to 15). While
 * the base holds essentia the core runs the aspect's {@link AuraEffect}s every tick and burns 1 essentia every 32
 * ticks. Values from Gadomancy 1.5.17; the crystal is kept inside the core instead of floating, and use the peak with
 * an empty hand to take it back.
 */
public class AuraPylonBlockEntity extends BlockEntity implements IEssentiaTransport {
    public static final int CAPACITY = 15;
    private static final int SUCTION = 64;

    private ItemStack crystal = ItemStack.EMPTY;
    private int essentia;
    private int clock;

    public AuraPylonBlockEntity(BlockPos pos, BlockState state) {
        super(GDRegistry.AURA_PYLON_BE.get(), pos, state);
    }

    private Part part() {
        return getBlockState().getValue(AuraPylonBlock.PART);
    }

    private @Nullable AuraPylonBlockEntity at(BlockPos pos) {
        return level != null && level.getBlockEntity(pos) instanceof AuraPylonBlockEntity p ? p : null;
    }

    /** The core of this pylon, from any part. */
    private @Nullable AuraPylonBlockEntity core() {
        return switch (part()) {
            case CORE -> this;
            case SHAFT -> at(worldPosition.above());
            case BASE -> at(worldPosition.above(2));
            case LOOSE -> null;
        };
    }

    private @Nullable AuraPylonBlockEntity base() {
        return part() == Part.CORE ? at(worldPosition.below(2)) : null;
    }

    public @Nullable Holder<IAspect> aspect() {
        AuraPylonBlockEntity core = core();
        if (core == null || core.crystal.isEmpty()) {
            return null;
        }
        AspectInstance a = core.crystal.get(TTDataComponents.CRYSTAL_ASPECT.get());
        return a == null ? null : a.aspect();
    }

    ItemStack takeCrystal() {
        ItemStack out = crystal;
        crystal = ItemStack.EMPTY;
        AuraPylonBlockEntity base = base();
        if (base != null) {
            base.essentia = 0;
            base.setChanged();
        }
        setChanged();
        return out;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AuraPylonBlockEntity be) {
        ServerLevel server = (ServerLevel) level;
        be.clock++;
        if (be.clock % 4 == 0 && !AuraPylonBlock.intact(level, pos, state)) {
            AuraPylonBlock.unform(level, pos, state);
            return;
        }
        switch (be.part()) {
            case BASE -> {
                if (be.clock % 16 == 0 && be.essentia < CAPACITY) {
                    be.pull(level, pos);
                }
            }
            case CORE -> be.coreTick(server, pos);
            default -> {
            }
        }
    }

    private void pull(Level level, BlockPos pos) {
        Holder<IAspect> want = aspect();
        IEssentiaTransport below = level.getCapability(EssentiaCapabilities.TRANSPORT, pos.below(), Direction.UP);
        if (want == null || below == null || !below.canOutputTo(Direction.UP)) {
            return;
        }
        if (want.equals(below.getEssentiaType(Direction.UP)) && below.getSuctionAmount(Direction.UP) < SUCTION
                && below.takeEssentia(want, 1, Direction.UP) == 1) {
            essentia++;
            setChanged();
        }
    }

    private void coreTick(ServerLevel level, BlockPos pos) {
        if (crystal.isEmpty()) {
            if (clock % 5 == 0) {
                attract(level, pos);
            }
            return;
        }
        AuraPylonBlockEntity base = base();
        Holder<IAspect> aspect = aspect();
        if (base == null || aspect == null || base.essentia <= 0) {
            return;
        }
        ResourceKey<IAspect> key = aspect.unwrapKey().orElse(null);
        if (key != null) {
            BlockPos centre = pos.below();
            level.registryAccess().lookupOrThrow(AuraEffect.REGISTRY_KEY).stream()
                    .filter(e -> e.aspect().equals(key))
                    .forEach(e -> e.pulse(level, centre, clock));
        }
        if (clock % 32 == 0) {
            base.essentia--;
            base.setChanged();
        }
        if (clock % 10 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    /** Draws loose vis crystals in from 8 blocks round and swallows the first that reaches the column. */
    private void attract(ServerLevel level, BlockPos pos) {
        Vec3 mouth = Vec3.atCenterOf(pos.above());
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(8),
                e -> e.getItem().has(TTDataComponents.CRYSTAL_ASPECT.get()))) {
            Vec3 to = mouth.subtract(item.position());
            if (to.length() < 1.5) {
                crystal = item.getItem().split(1);
                if (item.getItem().isEmpty()) {
                    item.discard();
                }
                level.sendParticles(ParticleTypes.ENCHANT, mouth.x, mouth.y, mouth.z, 20, 0.3, 0.3, 0.3, 0.6);
                setChanged();
                return;
            }
            item.setDeltaMovement(to.normalize().scale(0.15));
            item.setNoGravity(true);
            item.hurtMarked = true;
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !crystal.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, crystal);
            crystal = ItemStack.EMPTY;
        }
    }

    // ---- essentia: the base takes its pylon's aspect from below ----

    private boolean inlet(@Nullable Direction face) {
        return part() == Part.BASE && face == Direction.DOWN;
    }

    @Override
    public boolean isConnectable(Direction face) {
        return inlet(face);
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return inlet(face);
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
        return inlet(face) && essentia < CAPACITY ? aspect() : null;
    }

    @Override
    public int getSuctionAmount(Direction face) {
        return inlet(face) && essentia < CAPACITY && aspect() != null ? SUCTION : 0;
    }

    @Override
    public int takeEssentia(Holder<IAspect> type, int amount, Direction face) {
        return 0;
    }

    @Override
    public int addEssentia(Holder<IAspect> type, int amount, Direction face) {
        if (!inlet(face) || !type.equals(aspect())) {
            return 0;
        }
        int added = Math.min(amount, CAPACITY - essentia);
        essentia += added;
        if (added > 0) {
            setChanged();
        }
        return added;
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction face) {
        return essentia > 0 ? aspect() : null;
    }

    @Override
    public int getEssentiaAmount(Direction face) {
        return essentia;
    }

    @Override
    public int getMinimumSuction() {
        return 0;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        crystal = input.read("crystal", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        essentia = input.getIntOr("essentia", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!crystal.isEmpty()) {
            output.store("crystal", ItemStack.OPTIONAL_CODEC, crystal);
        }
        output.putInt("essentia", essentia);
    }

    static Holder<IAspect> resolve(Level level, ResourceKey<IAspect> key) {
        return Aspects.resolve(level, key);
    }
}
