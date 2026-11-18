package com.gadomancyawakened.machine;

import com.gadomancyawakened.GDRegistry;
import com.gadomancyawakened.GDTags;
import com.gadomancyawakened.machine.EssentiaCompressorBlock.Part;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import com.leclowndu93150.thaumaturge.content.infusion.BlockEntityPedestal;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One part of a formed compressor. The bottom (master) stores essentia: each aspect has its own cap of
 * 200 + 350 x size, size 0..8. The middle exposes it to tubes on its four sides with suction 16. Every 40 ticks the
 * master looks within 3 blocks on its own level for a compressor pedestal holding an Element: Darkness, drinks it over
 * 400 ticks and grows by one; once grown it drags nearby entities towards itself. Values from Gadomancy 1.5.17.
 * A part that loses its partners bursts, spilling flux instead of the original's taint.
 */
public class EssentiaCompressorBlockEntity extends BlockEntity implements IEssentiaTransport {
    public static final int MAX_SIZE = 8;
    private static final int BASE_CAP = 200;
    private static final int CAP_STEP = (3000 - BASE_CAP) / MAX_SIZE;
    private static final int ABSORB_TICKS = 400;
    private static final Codec<Map<ResourceKey<IAspect>, Integer>> STORE_CODEC =
            Codec.unboundedMap(ResourceKey.codec(IAspect.REGISTRY_KEY), Codec.INT);

    private final Map<ResourceKey<IAspect>, Integer> store = new LinkedHashMap<>();
    private int size;
    private @Nullable BlockPos absorbing;
    private int absorbTime;
    private int clock;

    public EssentiaCompressorBlockEntity(BlockPos pos, BlockState state) {
        super(GDRegistry.ESSENTIA_COMPRESSOR_BE.get(), pos, state);
    }

    private Part part() {
        return getBlockState().getValue(EssentiaCompressorBlock.PART);
    }

    public int capPerAspect() {
        return BASE_CAP + CAP_STEP * size;
    }

    private @Nullable EssentiaCompressorBlockEntity master() {
        if (level == null || part() == Part.NONE) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(worldPosition.below(part().depth()));
        return be instanceof EssentiaCompressorBlockEntity c && c.part() == Part.BOTTOM ? c : null;
    }

    private boolean intact(Level level) {
        BlockPos base = worldPosition.below(part().depth());
        for (Part p : new Part[] {Part.BOTTOM, Part.MIDDLE, Part.TOP}) {
            BlockState s = level.getBlockState(base.above(p.depth()));
            if (!s.is(GDRegistry.ESSENTIA_COMPRESSOR.get()) || s.getValue(EssentiaCompressorBlock.PART) != p) {
                return false;
            }
        }
        return true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, EssentiaCompressorBlockEntity be) {
        if (!be.intact(level)) {
            be.burst((ServerLevel) level);
            return;
        }
        if (be.part() != Part.BOTTOM) {
            return;
        }
        be.clock++;
        be.grow((ServerLevel) level);
        if (be.size > 0) {
            be.vortex(level);
        }
    }

    /** Explodes (no block damage beyond the compressor itself) and leaves flux, more if it held essentia. */
    private void burst(ServerLevel level) {
        int stored = store.values().stream().mapToInt(Integer::intValue).sum();
        BlockPos at = worldPosition;
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
        level.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 2.0F, Level.ExplosionInteraction.NONE);
        AuraHelper.addFlux(level, at, stored > 0 ? 4.0F + Math.min(40.0F, stored / 50.0F) : 2.0F);
    }

    private void grow(ServerLevel level) {
        if (absorbing != null) {
            BlockEntity be = level.getBlockEntity(absorbing);
            if (!(be instanceof BlockEntityPedestal pedestal) || !pedestal.getItem().is(GDRegistry.VOID_ELEMENT.get())) {
                absorbing = null;
                absorbTime = 0;
                return;
            }
            if (absorbTime % 8 == 0) {
                Vec3 from = Vec3.atCenterOf(absorbing).add(0, 0.8, 0);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, 4, 0.15, 0.15, 0.15, 0.02);
            }
            if (++absorbTime >= ABSORB_TICKS) {
                pedestal.setItem(net.minecraft.world.item.ItemStack.EMPTY);
                size = Math.min(MAX_SIZE, size + 1);
                absorbing = null;
                absorbTime = 0;
                level.sendParticles(ParticleTypes.SONIC_BOOM, worldPosition.getX() + 0.5, worldPosition.getY() + 1.5,
                        worldPosition.getZ() + 0.5, 1, 0, 0, 0, 0);
                setChanged();
            }
            return;
        }
        if (size >= MAX_SIZE || clock % 40 != 0) {
            return;
        }
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-3, 0, -3), worldPosition.offset(3, 0, 3))) {
            if (level.getBlockState(p).is(GDTags.COMPRESSOR_PEDESTALS) && level.getBlockEntity(p) instanceof BlockEntityPedestal pedestal
                    && pedestal.getItem().is(GDRegistry.VOID_ELEMENT.get())) {
                absorbing = p.immutable();
                absorbTime = 0;
                return;
            }
        }
    }

    private void vortex(Level level) {
        Vec3 centre = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5);
        double mult = size / (double) MAX_SIZE;
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(worldPosition).inflate(4))) {
            Vec3 d = centre.subtract(e.position()).scale(1 / 8.0);
            double len = d.length();
            if (len < 1.0E-3 || len > 1) {
                continue;
            }
            double f = (1 - len) * (1 - len);
            e.setDeltaMovement(e.getDeltaMovement().add(d.x / len * f * 0.08 * mult, d.y / len * f * 0.11 * mult, d.z / len * f * 0.08 * mult));
            e.hurtMarked = true;
        }
    }

    // ---- essentia (the middle block, horizontal faces, forwards to the master) ----

    private boolean port(@Nullable Direction face) {
        return part() == Part.MIDDLE && face != null && face.getAxis().isHorizontal();
    }

    @Override
    public boolean isConnectable(Direction face) {
        return port(face);
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return port(face);
    }

    @Override
    public boolean canOutputTo(Direction face) {
        return port(face);
    }

    @Override
    public void setSuction(Holder<IAspect> type, int amount) {
    }

    @Override
    public @Nullable Holder<IAspect> getSuctionType(Direction face) {
        return null;
    }

    @Override
    public int getSuctionAmount(Direction face) {
        return port(face) ? 16 : 0;
    }

    @Override
    public int takeEssentia(Holder<IAspect> type, int amount, Direction face) {
        EssentiaCompressorBlockEntity m = master();
        if (!port(face) || m == null) {
            return 0;
        }
        ResourceKey<IAspect> key = type.unwrapKey().orElse(null);
        int has = key == null ? 0 : m.store.getOrDefault(key, 0);
        int taken = Math.min(has, amount);
        if (taken > 0) {
            if (has == taken) {
                m.store.remove(key);
            } else {
                m.store.put(key, has - taken);
            }
            m.setChanged();
        }
        return taken;
    }

    @Override
    public int addEssentia(Holder<IAspect> type, int amount, Direction face) {
        EssentiaCompressorBlockEntity m = master();
        ResourceKey<IAspect> key = type.unwrapKey().orElse(null);
        if (!port(face) || m == null || key == null) {
            return 0;
        }
        int has = m.store.getOrDefault(key, 0);
        int added = Math.max(0, Math.min(amount, m.capPerAspect() - has));
        if (added > 0) {
            m.store.put(key, has + added);
            m.setChanged();
        }
        return added;
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction face) {
        EssentiaCompressorBlockEntity m = master();
        if (!port(face) || m == null || m.store.isEmpty() || level == null) {
            return null;
        }
        List<ResourceKey<IAspect>> keys = new ArrayList<>(m.store.keySet());
        return Aspects.resolve(level, keys.get(level.getRandom().nextInt(keys.size())));
    }

    @Override
    public int getEssentiaAmount(Direction face) {
        EssentiaCompressorBlockEntity m = master();
        Holder<IAspect> type = getEssentiaType(face);
        return m == null || type == null ? 0 : m.store.getOrDefault(type.unwrapKey().orElseThrow(), 0);
    }

    @Override
    public int getMinimumSuction() {
        return 0;
    }

    public Map<ResourceKey<IAspect>, Integer> contents() {
        EssentiaCompressorBlockEntity m = master();
        return m == null ? Map.of() : Map.copyOf(m.store);
    }

    public int size() {
        EssentiaCompressorBlockEntity m = master();
        return m == null ? 0 : m.size;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        store.clear();
        input.read("essentia", STORE_CODEC).ifPresent(store::putAll);
        size = input.getIntOr("size", 0);
        absorbing = input.read("absorbing", BlockPos.CODEC).orElse(null);
        absorbTime = input.getIntOr("absorb_time", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!store.isEmpty()) {
            output.store("essentia", STORE_CODEC, store);
        }
        output.putInt("size", size);
        output.storeNullable("absorbing", BlockPos.CODEC, absorbing);
        output.putInt("absorb_time", absorbTime);
    }
}
