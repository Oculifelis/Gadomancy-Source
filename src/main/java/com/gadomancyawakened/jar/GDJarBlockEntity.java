package com.gadomancyawakened.jar;

import com.gadomancyawakened.GDComponents;
import com.gadomancyawakened.GDRegistry;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import com.leclowndu93150.thaumaturge.content.essentia.jar.AbstractBlockEntityJar;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A Thaumaturge jar with Gadomancy's extras: an optional remote network id (Remote Jar) and, for sticky jars, a mount
 * face. A sticky jar's "bottom" is the face it is stuck to, so tube faces are turned round to match, and it pulls from
 * whatever sits at its mouth instead of from above. Void sticky jars keep the void jar's overflow rules.
 */
public class GDJarBlockEntity extends AbstractBlockEntityJar {
    private static final int VOID_CAPACITY = 250;
    private static final int PULL_PERIOD = 5;

    private @Nullable UUID network;
    private int pullClock;

    public GDJarBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static GDJarBlockEntity remote(BlockPos pos, BlockState state) {
        return new GDJarBlockEntity(GDRegistry.REMOTE_JAR_BE.get(), pos, state);
    }

    public static GDJarBlockEntity sticky(BlockPos pos, BlockState state) {
        return new GDJarBlockEntity(GDRegistry.STICKY_JAR_BE.get(), pos, state);
    }

    public @Nullable UUID network() {
        return network;
    }

    public void setNetwork(@Nullable UUID id) {
        if (network != null) {
            JarNetworks.leave(network, this);
        }
        network = id;
        setChanged();
    }

    private JarKind kind() {
        BlockState state = getBlockState();
        return state.hasProperty(StickyJarBlock.KIND) ? state.getValue(StickyJarBlock.KIND) : JarKind.REMOTE;
    }

    private Direction mount() {
        BlockState state = getBlockState();
        return state.hasProperty(StickyJarBlock.MOUNT) ? state.getValue(StickyJarBlock.MOUNT) : Direction.DOWN;
    }

    /** World face to the face an upright jar would see: the mount face is its bottom, the opposite its mouth. */
    private @Nullable Direction local(@Nullable Direction face) {
        Direction mount = mount();
        if (face == null || mount == Direction.DOWN) {
            return face;
        }
        if (face == mount) {
            return Direction.DOWN;
        }
        return face == mount.getOpposite() ? Direction.UP : Direction.NORTH;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GDJarBlockEntity jar) {
        AbstractBlockEntityJar.serverTick(level, pos, state, jar);
        if (jar.network != null) {
            JarNetworks.join(jar.network, jar);
        }
        if (jar.mount() != Direction.DOWN && ++jar.pullClock % PULL_PERIOD == 0) {
            jar.pullFromMouth(level, pos);
        }
    }

    /** Sticky jars on walls and ceilings draw one essentia at a time from the tube at their mouth, with jar suction rules. */
    private void pullFromMouth(Level level, BlockPos pos) {
        Direction mouth = mount().getOpposite();
        IEssentiaTransport source = level.getCapability(EssentiaCapabilities.TRANSPORT, pos.relative(mouth), mouth.getOpposite());
        Direction from = mouth.getOpposite();
        if (source == null || !source.canOutputTo(from) || source.getEssentiaAmount(from) <= 0) {
            return;
        }
        int suction = getSuctionAmount(mouth);
        if (source.getSuctionAmount(from) >= suction || suction < source.getMinimumSuction()) {
            return;
        }
        Holder<IAspect> type = source.getEssentiaType(from);
        if (type != null && accepts(type) && source.takeEssentia(type, 1, from) == 1) {
            fill(type, 1);
        }
    }

    @Override
    protected boolean shouldFillFromAbove() {
        return mount() == Direction.DOWN && (kind() == JarKind.VOID || super.shouldFillFromAbove());
    }

    @Override
    protected int doAddToContainer(ResourceKey<IAspect> key, int requested) {
        if (kind() != JarKind.VOID) {
            return super.doAddToContainer(key, requested);
        }
        // The void jar's rule: a matching or empty jar takes everything and voids what does not fit, sometimes leaking flux.
        if (requested == 0) {
            return 0;
        }
        ResourceKey<IAspect> filter = aspectFilterKey();
        if (filter != null && !filter.equals(key)) {
            return requested;
        }
        int amount = amount();
        if (!key.equals(aspectKey()) && amount != 0) {
            return requested;
        }
        int total = amount + requested;
        super.doAddToContainer(key, Math.min(total, VOID_CAPACITY) - amount);
        if (total > VOID_CAPACITY && level != null && !level.isClientSide() && level.getRandom().nextInt(250) == 0) {
            AuraHelper.addFlux(level, getBlockPos(), 1.0F);
        }
        return 0;
    }

    @Override
    public int storageInsertLimit(int requested) {
        return kind() == JarKind.VOID ? requested : super.storageInsertLimit(requested);
    }

    @Override
    public int fill(Holder<IAspect> type, int requested) {
        if (kind() != JarKind.VOID) {
            return super.fill(type, requested);
        }
        Optional<ResourceKey<IAspect>> key = type.unwrapKey();
        return key.isEmpty() ? requested : requested - doAddToContainer(key.get(), requested);
    }

    @Override
    public boolean isConnectable(Direction face) {
        return super.isConnectable(local(face));
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return super.canInputFrom(local(face));
    }

    @Override
    public boolean canOutputTo(Direction face) {
        return super.canOutputTo(local(face));
    }

    @Override
    public @Nullable Holder<IAspect> getSuctionType(@Nullable Direction face) {
        return super.getSuctionType(local(face));
    }

    @Override
    public int getSuctionAmount(@Nullable Direction face) {
        return super.getSuctionAmount(local(face));
    }

    @Override
    public int takeEssentia(Holder<IAspect> type, int requested, Direction face) {
        return super.takeEssentia(type, requested, local(face));
    }

    @Override
    public int addEssentia(Holder<IAspect> type, int requested, Direction face) {
        return super.addEssentia(type, requested, local(face));
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(@Nullable Direction face) {
        return super.getEssentiaType(local(face));
    }

    @Override
    public int getEssentiaAmount(@Nullable Direction face) {
        return super.getEssentiaAmount(local(face));
    }

    @Override
    public void setRemoved() {
        if (network != null) {
            JarNetworks.leave(network, this);
        }
        super.setRemoved();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        network = input.read("network", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("network", UUIDUtil.CODEC, network);
    }

    @Override
    public void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (network != null) {
            components.set(GDComponents.JAR_NETWORK.get(), network);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        network = components.get(GDComponents.JAR_NETWORK.get());
    }
}
