package net.splatcraft.forge.client.gui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.splatcraft.forge.client.handlers.DeathRecapClientHandler;

public class DeathRecapScreen extends Screen
{
    private static final int PANEL_BG = FastColor.ARGB32.color(180, 20, 24, 32);
    private static final int PANEL_FRAME = FastColor.ARGB32.color(220, 230, 234, 240);
    private static final int TEXT = FastColor.ARGB32.color(255, 255, 252, 242);
    private static final int SHADOW = FastColor.ARGB32.color(110, 8, 10, 14);

    private final DeathRecapClientHandler.DeathRecapData recap;
    private final long durationMs;
    private long startTimeMs;
    private boolean sentRespawn;

    public DeathRecapScreen(DeathRecapClientHandler.DeathRecapData recap)
    {
        super(Component.empty());
        this.recap = recap;
        this.durationMs = Math.max(1L, recap.respawnDelayTicks() * 50L);
    }

    @Override
    protected void init()
    {
        startTimeMs = Util.getMillis();
        sentRespawn = false;
    }

    @Override
    public void tick()
    {
        super.tick();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null)
            return;

        if (!sentRespawn && Util.getMillis() - startTimeMs >= durationMs)
        {
            sentRespawn = true;
            DeathRecapClientHandler.requestRespawn();
            minecraft.player.connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks)
    {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderDeathMessage(guiGraphics);
        renderCountdown(guiGraphics);
    }

    private void renderDeathMessage(GuiGraphics guiGraphics)
    {
        int maxWidth = Math.max(120, width - 40);
        List<FormattedCharSequence> lines = font.split(recap.deathMessage(), maxWidth);
        int y = 18;

        for (FormattedCharSequence line : lines)
        {
            int lineWidth = font.width(line);
            int x = width / 2 - lineWidth / 2;
            guiGraphics.fill(x - 6, y - 3, x + lineWidth + 6, y + font.lineHeight + 3, SHADOW);
            guiGraphics.drawString(font, line, x, y, TEXT, true);
            y += font.lineHeight + 6;
        }
    }

    private void renderCountdown(GuiGraphics guiGraphics)
    {
        long elapsedMs = Util.getMillis() - startTimeMs;
        float progress = Mth.clamp((float)elapsedMs / (float)durationMs, 0.0F, 1.0F);
        long remainingMs = Math.max(durationMs - elapsedMs, 0L);
        int secondsRemaining = (int)Math.ceil(remainingMs / 1000.0D);
        Component text = Component.translatable("hud.splatcraft.death_recap.respawning", secondsRemaining);

        int panelWidth = 154;
        int panelHeight = 34;
        int x = width - panelWidth - 12;
        int y = height - panelHeight - 12;
        int barX = x + 8;
        int barY = y + 20;
        int barWidth = panelWidth - 16;
        int barHeight = 8;
        int fillWidth = Math.min(barWidth - 2, Math.round((barWidth - 2) * progress));
        int fillColor = recap.inkColor() | 0xFF000000;

        guiGraphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL_BG);
        guiGraphics.fill(x, y, x + panelWidth, y + 1, PANEL_FRAME);
        guiGraphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, PANEL_FRAME);
        guiGraphics.fill(x, y, x + 1, y + panelHeight, PANEL_FRAME);
        guiGraphics.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, PANEL_FRAME);
        guiGraphics.drawString(font, text, x + 8, y + 7, TEXT, false);

        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, PANEL_FRAME);
        guiGraphics.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + barHeight - 1, PANEL_BG);
        if (fillWidth > 0)
            guiGraphics.fill(barX + 1, barY + 1, barX + 1 + fillWidth, barY + barHeight - 1, fillColor);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc()
    {
        return false;
    }
}
