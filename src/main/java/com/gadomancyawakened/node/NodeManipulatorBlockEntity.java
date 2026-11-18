package com.gadomancyawakened.node;

import com.gadomancyawakened.GDRegistry;
import com.gadomancyawakened.GDTags;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode;
import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import com.leclowndu93150.thaumaturge.content.wands.WandVisHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Node Manipulator. Formed: a Randomization Focus on top, four pillars ({@code gadomancyawakened:manipulator_pillars})
 * at the corners and four caps ({@code gadomancyawakened:manipulator_caps}) on them, with a node two blocks above. With a wand
 * inside it moves 1 vis (100 centivis) a tick of a random primal into its store, up to 120 each; when the wand can give
 * no more and every primal has at least 70 it works for 300 ticks and then changes the node. The better fed, the more
 * likely bad outcomes are skipped. With a Portal Focus instead it fills each primal to 120 and, given four pedestals
 * holding Eldritch Eyes nearby, spends 400 ticks turning the node into a portal to a new labyrinth. Numbers from
 * Gadomancy 1.5.17.
 */
public class NodeManipulatorBlockEntity extends BlockEntity {
    public static final List<ResourceKey<IAspect>> PRIMALS = List.of(TTAspects.AER, TTAspects.TERRA, TTAspects.IGNIS,
            TTAspects.AQUA, TTAspects.ORDO, TTAspects.PERDITIO);
    private static final int STORE_MAX = 120;
    private static final int STORE_MIN = 70;
    private static final int WORK_TICKS = 300;
    private static final int PORTAL_STORE = 120;
    private static final int PORTAL_TICKS = 400;

    private ItemStack wand = ItemStack.EMPTY;
    private boolean formed;
    private final int[] store = new int[PRIMALS.size()];
    private int work = -1;
    private int clock;

    public NodeManipulatorBlockEntity(BlockPos pos, BlockState state) {
        super(GDRegistry.NODE_MANIPULATOR_BE.get(), pos, state);
    }

    public boolean formed() {
        return formed;
    }

    public ItemStack wand() {
        return wand;
    }

    void setWand(ItemStack stack) {
        wand = stack;
        setChanged();
    }

    void setFormed(boolean on) {
        formed = on;
        work = -1;
        java.util.Arrays.fill(store, 0);
        setChanged();
    }

    /** The structure round the manipulator, without the node. */
    static boolean pattern(Level level, BlockPos pos) {
        BlockState focus = level.getBlockState(pos.above());
        if (!focus.is(GDRegistry.RANDOMIZATION_FOCUS.get()) && !focus.is(GDRegistry.PORTAL_FOCUS.get())) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                if (!level.getBlockState(pos.offset(dx, 0, dz)).is(GDTags.MANIPULATOR_PILLARS)
                        || !level.getBlockState(pos.offset(dx, 1, dz)).is(GDTags.MANIPULATOR_CAPS)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, NodeManipulatorBlockEntity be) {
        if (!be.formed) {
            return;
        }
        if (++be.clock % 20 == 0 && !pattern(level, pos)) {
            be.setFormed(false);
            return;
        }
        if (be.work >= 0) {
            be.working((ServerLevel) level, pos);
            return;
        }
        if (!be.drainOne() && be.ready() && level.getBlockEntity(pos.above(2)) instanceof BlockEntityNode
                && (!be.portalMode() || be.eyes(level, pos).size() >= 4)) {
            be.work = 0;
            level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8F, 0.7F);
        }
    }

    private boolean drainOne() {
        if (!(wand.getItem() instanceof ItemWand)) {
            return false;
        }
        List<Integer> can = new ArrayList<>();
        for (int i = 0; i < PRIMALS.size(); i++) {
            if (store[i] < STORE_MAX && WandVisHelper.storedIn(wand, PRIMALS.get(i)) >= 100) {
                can.add(i);
            }
        }
        if (can.isEmpty() || level == null) {
            return false;
        }
        int i = can.get(level.getRandom().nextInt(can.size()));
        WandVisHelper.payExact(wand, java.util.Map.of(PRIMALS.get(i), 100), false);
        store[i]++;
        setChanged();
        return true;
    }

    private boolean portalMode() {
        return level != null && level.getBlockState(worldPosition.above()).is(GDRegistry.PORTAL_FOCUS.get());
    }

    /** Pedestals within 8 across and -5..+10 up holding an Eldritch Eye, at most one per column. */
    private List<BlockPos> eyes(Level level, BlockPos pos) {
        List<BlockPos> found = new ArrayList<>();
        java.util.Set<Long> columns = new java.util.HashSet<>();
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-8, -5, -8), pos.offset(8, 10, 8))) {
            if (level.getBlockEntity(p) instanceof com.leclowndu93150.thaumaturge.content.infusion.BlockEntityPedestal ped
                    && ped.getItem().is(com.leclowndu93150.thaumaturge.registry.TTItems.ELDRITCH_EYE.get())
                    && columns.add(((long) p.getX() << 32) ^ (p.getZ() & 0xffffffffL))) {
                found.add(p.immutable());
            }
        }
        return found;
    }

    private boolean ready() {
        int min = portalMode() ? PORTAL_STORE : STORE_MIN;
        for (int v : store) {
            if (v < min) {
                return false;
            }
        }
        return true;
    }

    private void working(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos.above(2)) instanceof BlockEntityNode node)) {
            work = -1;
            java.util.Arrays.fill(store, 0);
            setChanged();
            return;
        }
        if (work % 10 == 0) {
            level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 2.5, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.8);
        }
        if (portalMode()) {
            List<BlockPos> eyes = eyes(level, pos);
            if (eyes.size() < 4) {
                work = -1;
                setChanged();
                return;
            }
            if (++work < PORTAL_TICKS) {
                return;
            }
            openPortal(level, pos.above(2), eyes.subList(0, 4));
            work = -1;
            java.util.Arrays.fill(store, 0);
            setChanged();
            return;
        }
        if (++work < WORK_TICKS) {
            return;
        }
        double avg = 0;
        for (int v : store) {
            avg += v - STORE_MIN;
        }
        double chance = Math.max(0, Math.min(100, avg / store.length / 50 * 100));
        int skipBad = (int) Math.round(chance / 5);
        List<NodeOutcome> outcomes = level.registryAccess().lookupOrThrow(NodeOutcome.REGISTRY_KEY).stream().toList();
        List<Holder<IAspect>> primals = PRIMALS.stream().map(k -> Aspects.resolve(level, k)).toList();
        for (int tries = 0; tries < 32 && !outcomes.isEmpty(); tries++) {
            NodeOutcome pick = roll(outcomes, level);
            if (pick == null) {
                break;
            }
            if (pick.polarity() == NodeOutcome.Polarity.NEGATIVE && level.getRandom().nextInt(100) < skipBad) {
                continue;
            }
            if (pick.apply(node, level.getRandom(), primals)) {
                break;
            }
        }
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 2.5, pos.getZ() + 0.5, 30, 0.5, 0.5, 0.5, 0.05);
        level.playSound(null, pos.above(2), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
        work = -1;
        java.util.Arrays.fill(store, 0);
        setChanged();
    }

    /** Eats the four eyes and the node and opens a Thaumaturge eldritch portal into a fresh labyrinth where the node was. */
    private static void openPortal(ServerLevel level, BlockPos at, List<BlockPos> eyes) {
        var maze = com.leclowndu93150.thaumaturge.api.labyrinth.LabyrinthHelper.open(level.getServer(),
                net.minecraft.core.GlobalPos.of(level.dimension(), at), java.util.Optional.empty());
        if (maze.isEmpty()) {
            return;
        }
        for (BlockPos p : eyes) {
            if (level.getBlockEntity(p) instanceof com.leclowndu93150.thaumaturge.content.infusion.BlockEntityPedestal ped) {
                ped.setItem(ItemStack.EMPTY);
            }
        }
        level.removeBlock(at, false);
        level.setBlockAndUpdate(at, com.leclowndu93150.thaumaturge.registry.TTBlocks.ELDRITCH_PORTAL.get().defaultBlockState());
        if (level.getBlockEntity(at) instanceof com.leclowndu93150.thaumaturge.content.eldritch.portal.BlockEntityEldritchPortal portal) {
            portal.setLink(com.leclowndu93150.thaumaturge.content.eldritch.portal.PortalLink.intoLabyrinth(maze.get().id()));
        }
        level.playSound(null, at, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private static @Nullable NodeOutcome roll(List<NodeOutcome> outcomes, ServerLevel level) {
        int total = outcomes.stream().mapToInt(NodeOutcome::weight).sum();
        if (total <= 0) {
            return null;
        }
        int r = level.getRandom().nextInt(total);
        for (NodeOutcome o : outcomes) {
            r -= o.weight();
            if (r < 0) {
                return o;
            }
        }
        return null;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !wand.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, wand);
            wand = ItemStack.EMPTY;
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        wand = input.read("wand", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        formed = input.getBooleanOr("formed", false);
        work = input.getIntOr("work", -1);
        int[] saved = input.getIntArray("store").orElse(new int[0]);
        for (int i = 0; i < store.length && i < saved.length; i++) {
            store[i] = saved[i];
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!wand.isEmpty()) {
            output.store("wand", ItemStack.OPTIONAL_CODEC, wand);
        }
        output.putBoolean("formed", formed);
        output.putInt("work", work);
        output.putIntArray("store", store.clone());
    }
}
