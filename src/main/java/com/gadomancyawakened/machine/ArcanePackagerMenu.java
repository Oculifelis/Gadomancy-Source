package com.gadomancyawakened.machine;

import com.gadomancyawakened.GDRegistry;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Packager menu. Buttons 0-5 as in the original: 0/1 essentia on/off, 2/3 auto-start on/off, 4/5 disguise on/off. */
public class ArcanePackagerMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;

    public ArcanePackagerMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(ArcanePackagerBlockEntity.SLOTS), new SimpleContainerData(3));
    }

    ArcanePackagerMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(GDRegistry.ARCANE_PACKAGER_MENU.get(), id);
        this.container = container;
        this.data = data;
        for (int i = 0; i < 9; i++) {
            addSlot(new Gate(container, i, 30 + (i % 3) * 18, 17 + (i / 3) * 18));
        }
        addSlot(new Gate(container, ArcanePackagerBlockEntity.LEATHER, 98, 17));
        addSlot(new Gate(container, ArcanePackagerBlockEntity.STRING, 98, 53));
        addSlot(new Gate(container, ArcanePackagerBlockEntity.OUTPUT, 134, 35));
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    public int settings() {
        return data.get(0);
    }

    public int progress() {
        return data.get(1);
    }

    public int fabrico() {
        return data.get(2);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(container instanceof ArcanePackagerBlockEntity be) || id < 0 || id > 5) {
            return false;
        }
        int flag = switch (id / 2) {
            case 0 -> ArcanePackagerBlockEntity.USE_ESSENTIA;
            case 1 -> ArcanePackagerBlockEntity.AUTO_START;
            default -> ArcanePackagerBlockEntity.DISGUISE;
        };
        be.toggle(flag, id % 2 == 0);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int own = ArcanePackagerBlockEntity.SLOTS;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, ArcanePackagerBlockEntity.LEATHER, ArcanePackagerBlockEntity.OUTPUT, false)
                && !moveItemStackTo(stack, 0, 9, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    /** A slot that asks the container what it accepts. */
    private static final class Gate extends Slot {
        Gate(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return container.canPlaceItem(getContainerSlot(), stack);
        }
    }
}
