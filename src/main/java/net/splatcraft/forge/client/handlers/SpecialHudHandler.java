package net.splatcraft.forge.client.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.items.weapons.WeaponBaseItem;
import net.splatcraft.forge.items.weapons.ZipcasterSpecialItem;

@Mod.EventBusSubscriber(modid = Splatcraft.MODID, value = Dist.CLIENT)
public class SpecialHudHandler
{
    private static final int BG = FastColor.ARGB32.color(170, 20, 24, 32);
    private static final int FRAME = FastColor.ARGB32.color(220, 230, 234, 240);
    private static final int FILL = FastColor.ARGB32.color(255, 242, 186, 73);
    private static final int READY = FastColor.ARGB32.color(255, 112, 205, 106);
    private static final int READY_TEXT = FastColor.ARGB32.color(255, 159, 255, 139);

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event)
    {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id()))
            return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui)
            return;

        ItemStack weaponStack = minecraft.player.getMainHandItem();
        if (!(weaponStack.getItem() instanceof WeaponBaseItem<?>))
            return;

        ItemStack subStack = WeaponBaseItem.getStoredSubWeapon(weaponStack);
        ItemStack specialStack = WeaponBaseItem.getStoredSpecialWeapon(weaponStack);
        if (specialStack.isEmpty())
            return;

        int current = ZipcasterSpecialItem.isActive(minecraft.player) ? ZipcasterSpecialItem.getDisplayCurrent(minecraft.player, weaponStack) : WeaponBaseItem.getSpecialPoints(weaponStack);
        int required = Math.max(ZipcasterSpecialItem.isActive(minecraft.player) ? ZipcasterSpecialItem.getDisplayRequired(minecraft.player) : WeaponBaseItem.getRequiredSpecialPoints(weaponStack), 1);

        renderHud(event.getGuiGraphics(), minecraft.font, subStack, specialStack, current, required, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
    }

    private static void renderHud(GuiGraphics guiGraphics, Font font, ItemStack subStack, ItemStack specialStack, int current, int required, int screenWidth, int screenHeight)
    {
        int barWidth = 72;
        int barHeight = 8;
        int panelWidth = 124;
        int panelHeight = 36;
        int x = screenWidth - panelWidth - 8;
        int y = screenHeight - panelHeight - 8;
        boolean isReady = current >= required;
        int fillWidth = Math.min(barWidth, current * barWidth / required);
        int fillColor = isReady ? READY : FILL;

        guiGraphics.fill(x, y, x + panelWidth, y + panelHeight, BG);
        if (!subStack.isEmpty())
            guiGraphics.renderItem(subStack, x + 6, y + 10);
        guiGraphics.renderItem(specialStack, x + 26, y + 10);

        int barX = x + 48;
        int barY = y + 11;
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, FRAME);
        guiGraphics.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + barHeight - 1, BG);
        if (fillWidth > 0)
            guiGraphics.fill(barX + 1, barY + 1, barX + 1 + Math.max(fillWidth - 2, 1), barY + barHeight - 1, fillColor);

        String text = current + "/" + required;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(0.75F, 0.75F, 1.0F);
        guiGraphics.drawString(font, text, (int) (((barX + barWidth / 2) - font.width(text) * 0.75F / 2) / 0.75F), (int) ((y + 23) / 0.75F), 0xFFFFFF, false);
        if (isReady)
            guiGraphics.drawString(font, "READY!", (int) (((barX + barWidth / 2) - font.width("READY!") * 0.75F / 2) / 0.75F), (int) ((y + 3) / 0.75F), READY_TEXT, false);
        guiGraphics.pose().popPose();
    }
}
