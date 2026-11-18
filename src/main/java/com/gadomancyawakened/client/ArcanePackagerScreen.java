package com.gadomancyawakened.client;

import com.gadomancyawakened.machine.ArcanePackagerBlockEntity;
import com.gadomancyawakened.machine.ArcanePackagerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Packager screen, drawn from flat panels (no texture): three toggle buttons, a progress bar and the Fabrico gauge. */
public class ArcanePackagerScreen extends AbstractContainerScreen<ArcanePackagerMenu> {
    private static final int PANEL = 0xFFC6B89A;
    private static final int EDGE_LIGHT = 0xFFF1E6C8;
    private static final int EDGE_DARK = 0xFF5C4A33;
    private static final int SLOT = 0xFF3A2F24;
    private static final int SLOT_INNER = 0xFF8B7A5E;
    private static final int FABRICO = 0xFF9C6FD1;

    private Button essentia;
    private Button auto;
    private Button disguise;

    public ArcanePackagerScreen(ArcanePackagerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        essentia = addRenderableWidget(Button.builder(Component.empty(), b -> press(ArcanePackagerBlockEntity.USE_ESSENTIA, 0))
                .bounds(leftPos + 154, topPos + 6, 16, 16).build());
        auto = addRenderableWidget(Button.builder(Component.empty(), b -> press(ArcanePackagerBlockEntity.AUTO_START, 2))
                .bounds(leftPos + 154, topPos + 26, 16, 16).build());
        disguise = addRenderableWidget(Button.builder(Component.empty(), b -> press(ArcanePackagerBlockEntity.DISGUISE, 4))
                .bounds(leftPos + 154, topPos + 46, 16, 16).build());
    }

    private void press(int flag, int base) {
        boolean on = (menu.settings() & flag) != 0;
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, on ? base + 1 : base);
        }
    }

    private static Component label(String key, boolean on) {
        return Component.literal(on ? "✓" : "✗").append(" ").append(Component.translatable("gadomancyawakened.packager." + key));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, EDGE_DARK);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, EDGE_LIGHT);
        g.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, PANEL);
        for (Slot slot : menu.slots) {
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, SLOT);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, SLOT_INNER);
        }
        int progress = menu.progress();
        int width = progress < 0 ? 0 : 20 * progress / ArcanePackagerBlockEntity.PACK_TICKS;
        g.fill(x + 110, y + 39, x + 130, y + 43, SLOT);
        g.fill(x + 110, y + 39, x + 110 + width, y + 43, EDGE_LIGHT);
        int fab = menu.fabrico();
        g.fill(x + 120, y + 16, x + 126, y + 34, SLOT);
        g.fill(x + 120, y + 34 - fab * 2, x + 126, y + 34, FABRICO);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int settings = menu.settings();
        essentia.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("essentia", (settings & ArcanePackagerBlockEntity.USE_ESSENTIA) != 0)));
        auto.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("auto", (settings & ArcanePackagerBlockEntity.AUTO_START) != 0)));
        disguise.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("disguise", (settings & ArcanePackagerBlockEntity.DISGUISE) != 0)));
        essentia.setMessage(Component.literal((settings & ArcanePackagerBlockEntity.USE_ESSENTIA) != 0 ? "E" : "L"));
        auto.setMessage(Component.literal((settings & ArcanePackagerBlockEntity.AUTO_START) != 0 ? "A" : "R"));
        disguise.setMessage(Component.literal((settings & ArcanePackagerBlockEntity.DISGUISE) != 0 ? "D" : "-"));
        super.extractRenderState(g, mouseX, mouseY, a);
    }
}
