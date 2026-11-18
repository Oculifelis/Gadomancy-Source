package com.gadomancyawakened.machine;

import com.gadomancyawakened.GDComponents;
import com.gadomancyawakened.GDRegistry;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Arcane Packager. Slots 0-8 are packed, 9 takes leather, 10 string, 11 is the package out. A package costs a leather
 * and a string, or 4 Fabrico essentia (the original's Pannus) when "use essentia" is on. Packing starts on a rising
 * redstone edge, or every 5 ticks with auto-start, and takes 47 ticks. A disguised package looks like a loot bag.
 * A package already inside a package can hold no further package (the original's depth-2 limit); trying blows the
 * contents out with a small harmless bang. Hoppers put in through the front face and take out from the bottom.
 */
public class ArcanePackagerBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, IEssentiaTransport {
    public static final int SLOTS = 12;
    public static final int LEATHER = 9;
    public static final int STRING = 10;
    public static final int OUTPUT = 11;
    public static final int PACK_TICKS = 47;
    public static final int AUTO_START = 1;
    public static final int USE_ESSENTIA = 2;
    public static final int DISGUISE = 4;
    private static final int FABRICO_COST = 4;
    private static final int FABRICO_MAX = 8;
    private static final int[] FRONT = {9, 10, 0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final int[] BOTTOM = {OUTPUT};

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    /** The packing cycle. Saved by name; {@code progress} belongs to PACKING and is saved with it. */
    public enum Cycle {
        IDLE("idle"),
        PACKING("packing");

        private final String serialName;

        Cycle(String serialName) {
            this.serialName = serialName;
        }

        public String serialName() {
            return serialName;
        }

        static Cycle byName(String name) {
            return PACKING.serialName.equals(name) ? PACKING : IDLE;
        }
    }

    private int settings;
    private Cycle cycle = Cycle.IDLE;
    /** PACKING only: ticks packed so far, 0..PACK_TICKS-1. Changed only through {@link #enterCycle} and the tick. */
    private int progress;
    private int fabrico;
    /** Last redstone level seen; saved so a reload under a held signal is not a new rising edge. */
    private boolean powered;
    private int clock;

    /**
     * What the menu shows (settings, progress or -1 when idle, Fabrico). Read-only: the machine's state is never written
     * through the menu (the client screen has its own copy, filled by the server's sync).
     */
    final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> settings;
                case 1 -> cycle == Cycle.PACKING ? progress : -1;
                default -> fabrico;
            };
        }

        @Override
        public void set(int i, int v) {
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public ArcanePackagerBlockEntity(BlockPos pos, BlockState state) {
        super(GDRegistry.ARCANE_PACKAGER_BE.get(), pos, state);
    }

    void toggle(int flag, boolean on) {
        settings = on ? settings | flag : settings & ~flag;
        setChanged();
    }

    public Cycle cycle() {
        return cycle;
    }

    /** The one place the cycle changes: PACKING starts at tick 0, IDLE drops the progress. */
    private void enterCycle(Cycle next) {
        cycle = next;
        progress = 0;
        setChanged();
    }

    void redstone(boolean now) {
        if (now && !powered && cycle == Cycle.IDLE && canPack()) {
            enterCycle(Cycle.PACKING);
        }
        if (powered != now) {
            powered = now;
            setChanged();
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ArcanePackagerBlockEntity be) {
        be.clock++;
        if (be.cycle == Cycle.IDLE) {
            if ((be.settings & AUTO_START) != 0 && be.clock % 5 == 0 && be.canPack()) {
                be.enterCycle(Cycle.PACKING);
            }
            return;
        }
        if (++be.progress >= PACK_TICKS) {
            be.enterCycle(Cycle.IDLE);
            if (be.canPack()) {
                be.pack(level, pos);
            }
        }
        be.setChanged();
    }

    private boolean canPack() {
        if (!items.get(OUTPUT).isEmpty()) {
            return false;
        }
        boolean any = false;
        for (int i = 0; i < 9; i++) {
            any |= !items.get(i).isEmpty();
        }
        if (!any) {
            return false;
        }
        return (settings & USE_ESSENTIA) != 0 ? fabrico >= FABRICO_COST
                : items.get(LEATHER).is(Items.LEATHER) && items.get(STRING).is(Items.STRING);
    }

    private void pack(Level level, BlockPos pos) {
        List<ItemStack> merged = new ArrayList<>();
        boolean nested = false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof ArcanePackageItem) {
                ItemContainerContents inner = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
                nested |= inner.nonEmptyItemCopyStream().anyMatch(s -> s.getItem() instanceof ArcanePackageItem);
            }
            mergeInto(merged, stack.copy());
        }
        if (nested || merged.size() > 256) {
            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1.0F, Level.ExplosionInteraction.NONE);
            for (int i = 0; i < 9; i++) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, items.get(i));
                items.set(i, ItemStack.EMPTY);
            }
            return;
        }
        merged.sort(Comparator.comparing((ItemStack s) -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
                .thenComparing(s -> -s.getCount()));
        boolean essentia = (settings & USE_ESSENTIA) != 0;
        ItemStack out = new ItemStack(essentia ? GDRegistry.ARCANE_PACKAGE_ESSENTIA.get() : GDRegistry.ARCANE_PACKAGE.get());
        out.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(merged));
        if ((settings & DISGUISE) != 0) {
            out.set(GDComponents.DISGUISED.get(), Unit.INSTANCE);
        }
        if (essentia) {
            fabrico -= FABRICO_COST;
        } else {
            items.get(LEATHER).shrink(1);
            items.get(STRING).shrink(1);
        }
        for (int i = 0; i < 9; i++) {
            items.set(i, ItemStack.EMPTY);
        }
        items.set(OUTPUT, out);
        level.playSound(null, pos, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 1.0F, 0.8F);
        setChanged();
    }

    private static void mergeInto(List<ItemStack> into, ItemStack stack) {
        for (ItemStack have : into) {
            if (ItemStack.isSameItemSameComponents(have, stack) && have.getCount() < have.getMaxStackSize()) {
                int move = Math.min(stack.getCount(), have.getMaxStackSize() - have.getCount());
                have.grow(move);
                stack.shrink(move);
                if (stack.isEmpty()) {
                    return;
                }
            }
        }
        into.add(stack);
    }

    // ---- container ----

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.gadomancyawakened.arcane_packager");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new ArcanePackagerMenu(id, inventory, this, data);
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case LEATHER -> stack.is(Items.LEATHER) && (settings & USE_ESSENTIA) == 0;
            case STRING -> stack.is(Items.STRING) && (settings & USE_ESSENTIA) == 0;
            case OUTPUT -> false;
            default -> true;
        };
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) {
            return BOTTOM;
        }
        return side == getBlockState().getValue(ArcanePackagerBlock.FACING) ? FRONT : new int[0];
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return side != Direction.DOWN && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
    }

    // ---- essentia: Fabrico, like a jar ----

    @Override
    public boolean isConnectable(Direction face) {
        return true;
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return true;
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
        return level == null || fabrico >= FABRICO_MAX ? null : Aspects.resolve(level, TTAspects.FABRICO);
    }

    @Override
    public int getSuctionAmount(Direction face) {
        return fabrico < FABRICO_MAX ? 64 : 0;
    }

    @Override
    public int takeEssentia(Holder<IAspect> type, int amount, Direction face) {
        return 0;
    }

    @Override
    public int addEssentia(Holder<IAspect> type, int amount, Direction face) {
        if (!type.is(TTAspects.FABRICO)) {
            return 0;
        }
        int added = Math.min(amount, FABRICO_MAX - fabrico);
        fabrico += added;
        if (added > 0) {
            setChanged();
        }
        return added;
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction face) {
        return level == null || fabrico <= 0 ? null : Aspects.resolve(level, TTAspects.FABRICO);
    }

    @Override
    public int getEssentiaAmount(Direction face) {
        return fabrico;
    }

    @Override
    public int getMinimumSuction() {
        return 0;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(input, items);
        settings = input.getIntOr("settings", 0);
        cycle = Cycle.byName(input.getStringOr("cycle", Cycle.IDLE.serialName()));
        progress = cycle == Cycle.PACKING ? Math.max(0, Math.min(PACK_TICKS - 1, input.getIntOr("progress", 0))) : 0;
        fabrico = input.getIntOr("fabrico", 0);
        powered = input.getBooleanOr("powered", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        net.minecraft.world.ContainerHelper.saveAllItems(output, items);
        output.putInt("settings", settings);
        output.putString("cycle", cycle.serialName());
        if (cycle == Cycle.PACKING) {
            output.putInt("progress", progress);
        }
        output.putInt("fabrico", fabrico);
        output.putBoolean("powered", powered);
    }

    /** Saved cycle fields in one line, for the save/load round-trip self-test. */
    public String snapshot() {
        return "settings=" + settings + " cycle=" + cycle.serialName() + " progress=" + progress + " shown=" + data.get(1)
                + " fabrico=" + fabrico + " powered=" + powered;
    }

    public void selfTestSet(Cycle c, int ticks, int settings, int fabrico, boolean powered) {
        this.cycle = c;
        this.progress = c == Cycle.PACKING ? ticks : 0;
        this.settings = settings;
        this.fabrico = fabrico;
        this.powered = powered;
    }

    /** Self-test: a menu write must not change the machine. */
    public void selfTestMenuWrite(int value) {
        data.set(1, value);
        data.set(0, value);
    }
}
