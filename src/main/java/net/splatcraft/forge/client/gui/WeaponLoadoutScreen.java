package net.splatcraft.forge.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.player.Inventory;
import net.splatcraft.forge.tileentities.container.WeaponLoadoutContainer;

public class WeaponLoadoutScreen extends AbstractContainerScreen<WeaponLoadoutContainer>
{
    private static final int PANEL = FastColor.ARGB32.color(255, 231, 234, 240);
    private static final int PANEL_SHADOW = FastColor.ARGB32.color(255, 166, 172, 184);
    private static final int PANEL_DARK = FastColor.ARGB32.color(255, 78, 88, 108);
    private static final int PANEL_ACCENT = FastColor.ARGB32.color(255, 242, 186, 73);
    private static final int SLOT = FastColor.ARGB32.color(255, 196, 202, 212);
    private static final int SLOT_INNER = FastColor.ARGB32.color(255, 248, 249, 252);

    public WeaponLoadoutScreen(WeaponLoadoutContainer menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY)
    {
        drawPanel(guiGraphics, leftPos, topPos, imageWidth, imageHeight);
        drawHeader(guiGraphics);
        drawSlotFrame(guiGraphics, leftPos + 43, topPos + 19);
        drawSlotFrame(guiGraphics, leftPos + 115, topPos + 19);
    }

    private void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height)
    {
        guiGraphics.fill(x + 3, y + 3, x + width, y + height, PANEL_SHADOW);
        guiGraphics.fill(x, y, x + width - 3, y + height - 3, PANEL);
        guiGraphics.fill(x, y, x + width - 3, y + 18, PANEL_DARK);
        guiGraphics.fill(x, y + 18, x + width - 3, y + 20, PANEL_SHADOW);
        guiGraphics.fill(x + 12, y + 46, x + width - 15, y + 48, PANEL_SHADOW);
    }

    private void drawHeader(GuiGraphics guiGraphics)
    {
        guiGraphics.fill(leftPos + 23, topPos + 5, leftPos + 31, topPos + 13, PANEL_ACCENT);
        guiGraphics.fill(leftPos + 145, topPos + 5, leftPos + 153, topPos + 13, PANEL_ACCENT);
    }

    private void drawSlotFrame(GuiGraphics guiGraphics, int x, int y)
    {
        guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, PANEL_DARK);
        guiGraphics.fill(x, y, x + 16, y + 16, SLOT);
        guiGraphics.fill(x + 1, y + 1, x + 15, y + 15, SLOT_INNER);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY)
    {
        guiGraphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0xFFFFFF, false);
        guiGraphics.drawString(font, Component.translatable("gui.splatcraft.weapon_loadout.sub"), 34, 40, 0x4C566A, false);
        guiGraphics.drawString(font, Component.translatable("gui.splatcraft.weapon_loadout.special"), 96, 40, 0x4C566A, false);
        guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
        guiGraphics.drawString(font, Component.translatable("gui.splatcraft.weapon_loadout.hotkey_hint"), 8, 61, 0x6B7280, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks)
    {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
