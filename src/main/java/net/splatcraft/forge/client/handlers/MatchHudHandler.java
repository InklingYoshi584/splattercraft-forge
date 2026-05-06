package net.splatcraft.forge.client.handlers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.client.data.ClientMatchData;
import net.splatcraft.forge.data.match.MatchPhase;

import java.util.*;

@Mod.EventBusSubscriber(modid = Splatcraft.MODID, value = Dist.CLIENT)
public class MatchHudHandler
{
    private static final int ICON_SIZE_MIN = 12;
    private static final int ICON_SIZE_MAX = 20;
    private static final int BORDER = 2;
    private static final int ICON_GAP = 3;
    private static final int TIMER_PAD_H = 12;
    private static final int TIMER_PAD_V = 4;
    private static final int HUD_TOP = 10;
    private static final int BG_COLOR = 0xCC000000;
    private static final int TIMER_YELLOW = 0xFFFFAA00;
    private static final int TIMER_WHITE = 0xFFFFFFFF;
    private static final int DEATH_OVERLAY = 0x22000000;
    private static final int DEATH_X_COLOR = 0xFF888888;
    private static final float DEATH_X_SLAM_SCALE = 5F;
    private static final int DANGER_COLOR = 0xFFFF3333;
    private static final int RESULT_BG = 0xAA000000;
    private static final int BAR_HEIGHT = 18;
    private static final long DEATH_ANIM_DURATION_MS = 300;

    private static boolean sfxFillTick;
    private static boolean sfxCrash;
    private static boolean sfxWin;
    private static long resultAnimStart;
    private static UUID lastAnimMatchId;

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event)
    {
        if (ClientMatchData.currentMatchId == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (ClientMatchData.phase == MatchPhase.FINISHED)
        {
            if (event.getOverlay() == VanillaGuiOverlay.HOTBAR.type())
            {
                event.setCanceled(true);
                renderResultScreen(event.getGuiGraphics(), mc);
                return;
            }
        }
        else if (ClientMatchData.phase == MatchPhase.COUNTDOWN)
        {
            if (event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()
                || event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()
                || event.getOverlay() == VanillaGuiOverlay.JUMP_BAR.type()
                || event.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type()
                || event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()
                || event.getOverlay() == VanillaGuiOverlay.AIR_LEVEL.type()
                || event.getOverlay() == VanillaGuiOverlay.PLAYER_LIST.type()
                || event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type())
            {
                event.setCanceled(true);
            }
            if (event.getOverlay() == VanillaGuiOverlay.HOTBAR.type())
            {
                renderCountdownOnly(event.getGuiGraphics(), mc);
            }
        }
        else
        {
            if (event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type())
            {
                renderMatchHud(event.getGuiGraphics(), mc);
            }
        }
    }

    private static void renderMatchHud(GuiGraphics g, Minecraft mc)
    {
        int sw = mc.getWindow().getGuiScaledWidth();
        int iconSize = Math.max(ICON_SIZE_MIN, Math.min(ICON_SIZE_MAX, sw / 64));
        int iconTotal = iconSize + BORDER * 2 + ICON_GAP;

        String[] teamNames = ClientMatchData.teamNames;
        Map<String, List<Integer>> teamPlayerIndices = new HashMap<>();
        for (int i = 0; i < ClientMatchData.playerUUIDs.length; i++)
        {
            teamPlayerIndices.computeIfAbsent(ClientMatchData.playerTeams[i], k -> new ArrayList<>()).add(i);
        }

        Map<String, Boolean> teamDanger = computeDangerState(teamNames);

        List<String> leftTeams = new ArrayList<>();
        List<String> rightTeams = new ArrayList<>();
        for (int i = 0; i < teamNames.length; i++)
        {
            if (i % 2 == 0) leftTeams.add(teamNames[i]);
            else rightTeams.add(teamNames[i]);
        }

        String timeStr = formatTime(ClientMatchData.remainingSeconds);
        int timerColor = ClientMatchData.remainingSeconds <= 60 ? TIMER_YELLOW : TIMER_WHITE;
        int timerWidth = mc.font.width(timeStr) + TIMER_PAD_H * 2;
        int timerHeight = mc.font.lineHeight + TIMER_PAD_V * 2;
        int timerX = (sw - timerWidth) / 2;
        int timerY = HUD_TOP;

        g.fill(timerX, timerY, timerX + timerWidth, timerY + timerHeight, BG_COLOR);
        g.drawCenteredString(mc.font, timeStr, sw / 2, timerY + TIMER_PAD_V, timerColor);

        int leftX = timerX - ICON_GAP;
        int rightX = timerX + timerWidth + ICON_GAP;

        for (String team : leftTeams)
        {
            List<Integer> indices = teamPlayerIndices.get(team);
            if (indices == null) continue;
            boolean danger = teamDanger.getOrDefault(team, false);
            for (int idx : indices)
            {
                leftX -= iconTotal;
                renderPlayerIcon(g, mc, idx, leftX, timerY, iconSize, danger);
            }
        }

        for (String team : rightTeams)
        {
            List<Integer> indices = teamPlayerIndices.get(team);
            if (indices == null) continue;
            boolean danger = teamDanger.getOrDefault(team, false);
            for (int idx : indices)
            {
                renderPlayerIcon(g, mc, idx, rightX, timerY, iconSize, danger);
                rightX += iconTotal;
            }
        }
    }

    private static void renderPlayerIcon(GuiGraphics g, Minecraft mc, int idx, int x, int y, int iconSize, boolean danger)
    {
        int teamColor = 0xFF888888;
        for (int t = 0; t < ClientMatchData.teamNames.length; t++)
        {
            if (ClientMatchData.teamNames[t].equals(ClientMatchData.playerTeams[idx]))
            {
                teamColor = ClientMatchData.teamColors[t];
                break;
            }
        }

        int actualSize = danger ? Math.max(iconSize * 7 / 10, 8) : iconSize;
        int offset = (iconSize - actualSize) / 2;
        int cx = x + BORDER + offset;
        int cy = y + BORDER + offset;

        g.fill(x, y, x + BORDER * 2 + iconSize, y + BORDER * 2 + iconSize, teamColor | 0xFF000000);
        g.fill(x + BORDER, y + BORDER, x + BORDER + iconSize, y + BORDER + iconSize, 0xFF202020);

        UUID uuid = ClientMatchData.playerUUIDs[idx];
        AbstractClientPlayer clientPlayer = findClientPlayer(mc, uuid);
        boolean alive = ClientMatchData.playerAlive[idx];
        boolean specialReady = ClientMatchData.playerSpecialReady[idx];

        if (clientPlayer != null)
        {
            ResourceLocation skin = clientPlayer.getSkinTextureLocation();
            if (specialReady && alive)
            {
                int rainbow = rainbowColor();
                g.fill(x, y, x + BORDER * 2 + iconSize, y + BORDER * 2 + iconSize, rainbow);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }
            if (!alive)
                RenderSystem.setShaderColor(0.5F, 0.5F, 0.5F, 1.0F);

            g.blit(skin, cx, cy, actualSize, actualSize, 8.0F, 8.0F, 8, 8, 64, 64);

            if (!alive)
            {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                g.fill(cx, cy, cx + actualSize, cy + actualSize, DEATH_OVERLAY);
                renderDeathX(g, cx, cy, actualSize, uuid);
            }
        }
        else
        {
            g.fill(cx, cy, cx + actualSize, cy + actualSize, teamColor | 0xFF000000);
            if (!alive)
            {
                g.fill(cx, cy, cx + actualSize, cy + actualSize, DEATH_OVERLAY);
                renderDeathX(g, cx, cy, actualSize, uuid);
            }
        }

        if (danger)
            g.drawCenteredString(mc.font, "!", x + BORDER + iconSize / 2, y - 6, DANGER_COLOR);
    }

    private static void renderDeathX(GuiGraphics g, int x, int y, int size, UUID uuid)
    {
        Long animStart = ClientMatchData.playerDeathAnim.get(uuid);
        float progress = 1.0F;
        if (animStart != null)
        {
            long elapsed = System.currentTimeMillis() - animStart;
            progress = Math.min(1.0F, elapsed / (float) DEATH_ANIM_DURATION_MS);
        }

        float slamScale = DEATH_X_SLAM_SCALE - (DEATH_X_SLAM_SCALE - 1.0F) * progress;
        int thickness = Math.max(2, size / 6);
        int cx = x + size / 2;
        int cy = y + size / 2;
        int halfLen = (int) (size * 0.42F * slamScale);

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(45));
        g.fill(-halfLen, -thickness / 2, halfLen, thickness / 2, DEATH_X_COLOR);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-90));
        g.fill(-halfLen, -thickness / 2, halfLen, thickness / 2, DEATH_X_COLOR);
        g.pose().popPose();
    }

    private static void renderCountdownOnly(GuiGraphics g, Minecraft mc)
    {
        int sw = mc.getWindow().getGuiScaledWidth();
        String text = "...";
        if (ClientMatchData.remainingSeconds > 0)
            text = formatTime(ClientMatchData.remainingSeconds);
        g.drawCenteredString(mc.font, "\u00a7l" + text, sw / 2, HUD_TOP + 4, 0xFFFFFFFF);
    }

    // ─── Result Screen (Table Turf-style crash animation) ───

    private static void renderResultScreen(GuiGraphics g, Minecraft mc)
    {
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        String[] teams = ClientMatchData.teamNames;
        float[] pcts = ClientMatchData.teamPcts;
        int[] colors = ClientMatchData.teamColors;
        if (teams.length < 2) return;

        // Reset sound state when match animation restarts
        if (lastAnimMatchId == null || !lastAnimMatchId.equals(ClientMatchData.currentMatchId))
        {
            lastAnimMatchId = ClientMatchData.currentMatchId;
            resultAnimStart = 0;
            sfxFillTick = false;
            sfxCrash = false;
            sfxWin = false;
        }

        long now = System.currentTimeMillis();
        if (resultAnimStart == 0)
            resultAnimStart = now;

        float elapsedSec = (now - resultAnimStart) / 1000.0F;

        // ── Phase timings ──
        float FILL_END = 1.5F;
        float HOLD_END = 2.0F;
        float CRASH_END = 2.8F;
        float PCTS_SHOW = 2.8F;
        float WIN_SHOW = 3.8F;

        boolean pctsVisible;
        boolean winVisible;
        float winFade = 0.0F;

        if (elapsedSec < CRASH_END)
        {
            pctsVisible = false;
            winVisible = false;
        }
        else
        {
            pctsVisible = elapsedSec >= PCTS_SHOW;
            winVisible = elapsedSec >= WIN_SHOW;
            if (winVisible)
                winFade = Mth.clamp((elapsedSec - WIN_SHOW) / 0.5F, 0.0F, 1.0F);
        }

        // ── Sound effects ──
        if (!sfxFillTick && elapsedSec >= FILL_END - 0.05F)
        {
            sfxFillTick = true;
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.4F, 1.5F);
        }
        if (!sfxCrash && elapsedSec >= HOLD_END + 0.05F)
        {
            sfxCrash = true;
            mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.6F);
        }
        if (!sfxWin && winVisible && winFade > 0.01F)
        {
            sfxWin = true;
            if (isLocalWinner(mc, teams, pcts))
                mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 0.7F, 1.0F);
            else
                mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.3F, 0.3F);
        }

        // ── Background ──
        g.fill(0, 0, sw, sh, RESULT_BG);

        // ── Win/lose vignette ──
        if (winVisible)
            renderVignette(g, mc, sw, sh, teams, pcts, winFade);

        // ── Total ink pct ──
        float totalPct = pcts[0] + pcts[1];
        float finalRatio = totalPct > 0.001F ? pcts[0] / totalPct : 0.5F;

        int barWidth = sw * 6 / 10;
        int barX = (sw - barWidth) / 2;
        int barY = sh / 2 - 20;
        int barH = BAR_HEIGHT;

        int leftColor = colors[0] | 0xFF000000;
        int rightColor = colors[1] | 0xFF000000;
        float fillFraction = 0.4F;

        float fillProgress;
        float crashT;
        if (elapsedSec < FILL_END)
            fillProgress = easeInOutCubic(elapsedSec / FILL_END);
        else
            fillProgress = 1.0F;

        if (elapsedSec < HOLD_END)
            crashT = 0.0F;
        else if (elapsedSec < CRASH_END)
            crashT = easeInOutBack((elapsedSec - HOLD_END) / (CRASH_END - HOLD_END));
        else
            crashT = 1.0F;

        float splitX = barX + barWidth * finalRatio;
        float gapLeft  = barX + barWidth * fillFraction * fillProgress;
        float gapRight = barX + barWidth * (1.0F - fillFraction * fillProgress);

        float leftEnd  = gapLeft + (splitX - gapLeft) * crashT;
        float rightStart = gapRight + (splitX - gapRight) * crashT;

        // Left colored bar
        g.fill(barX, barY, (int) leftEnd, barY + barH, leftColor);
        // Right colored bar
        g.fill((int) rightStart, barY, barX + barWidth, barY + barH, rightColor);

        // ── Bar border ──
        g.fill(barX - 1, barY - 1, barX + barWidth + 1, barY, 0xFFFFFFFF);
        g.fill(barX - 1, barY + barH, barX + barWidth + 1, barY + barH + 1, 0xFFFFFFFF);
        g.fill(barX - 1, barY, barX, barY + barH, 0xFFFFFFFF);
        g.fill(barX + barWidth, barY, barX + barWidth + 1, barY + barH, 0xFFFFFFFF);

        // ── Split line (only after crash done) ──
        if (crashT >= 0.999F)
            g.fill((int) splitX - 1, barY - 3, (int) splitX + 1, barY + barH + 3, 0xFFFFFFFF);

        // ── Percentages inside the bar (fade in after crash) ──
        if (pctsVisible)
        {
            float pctFade = Mth.clamp((elapsedSec - PCTS_SHOW) / 0.3F, 0.0F, 1.0F);
            int alpha = (int) (pctFade * 255);
            int pctColor0 = (colors[0] & 0xFFFFFF) | (alpha << 24);
            int pctColor1 = (colors[1] & 0xFFFFFF) | (alpha << 24);
            String pctText0 = String.format("%.1f%%", pcts[0]);
            String pctText1 = String.format("%.1f%%", pcts[1]);

            int fontWidth0 = mc.font.width(pctText0);
            int fontWidth1 = mc.font.width(pctText1);

            int barCenterY = barY + barH / 2 - mc.font.lineHeight / 2;
            g.drawString(mc.font, pctText1, barX + barWidth - fontWidth1 - 6, barCenterY, pctColor1);
            g.drawString(mc.font, pctText0, barX + 6, barCenterY, pctColor0);
        }

        // ── YOU WIN / YOU LOSE (big text, after crash + delay) ──
        if (winVisible)
        {
            String localTeam = getLocalPlayerTeam(mc);
            boolean localWon = localTeam != null && localTeam.equals(getWinnerTeam(teams, pcts));
            String resultText = localWon ? "\u00a7a\u00a7lYOU WIN!" : "\u00a7c\u00a7lYOU LOSE";
            int textAlpha = (int) (winFade * 255);
            int textColor = localWon
                ? ((0x00FF00 & 0xFFFFFF) | (textAlpha << 24))
                : ((0xFF3333 & 0xFFFFFF) | (textAlpha << 24));

            g.pose().pushPose();
            float txtScale = 2.5F;
            g.pose().translate(sw / 2.0F, barY - 32, 0);
            g.pose().scale(txtScale, txtScale, 1);
            int tw = mc.font.width(resultText.replaceAll("\u00a7.", ""));
            mc.font.drawInBatch(resultText, -tw / 2.0F, 0, textColor, false, g.pose().last().pose(), g.bufferSource(), net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
            g.pose().popPose();
        }
    }

    private static boolean isLocalWinner(Minecraft mc, String[] teams, float[] pcts)
    {
        String winner = getWinnerTeam(teams, pcts);
        String localTeam = getLocalPlayerTeam(mc);
        return localTeam != null && localTeam.equals(winner);
    }

    private static String getWinnerTeam(String[] teams, float[] pcts)
    {
        float best = -1;
        int idx = 0;
        for (int i = 0; i < pcts.length; i++)
        {
            if (pcts[i] > best) { best = pcts[i]; idx = i; }
        }
        return best > 0 ? teams[idx] : null;
    }

    private static void renderVignette(GuiGraphics g, Minecraft mc, int sw, int sh, String[] teams, float[] pcts, float fade)
    {
        boolean won = isLocalWinner(mc, teams, pcts);
        int r, gr, b;
        if (won) { r = 0x33; gr = 0xFF; b = 0x33; }
        else     { r = 0xFF; gr = 0x33; b = 0x33; }

        int alpha = (int) (fade * 60);
        int color = (alpha << 24) | (r << 16) | (gr << 8) | b;

        int margin = 8;
        g.fill(0, 0, sw, margin, color);
        g.fill(0, sh - margin, sw, sh, color);
        g.fill(0, margin, margin, sh - margin, color);
        g.fill(sw - margin, margin, sw, sh - margin, color);
    }

    // ─── Easing functions ───

    private static float easeInOutCubic(float t)
    {
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3) / 2.0F;
    }

    private static float easeInOutBack(float t)
    {
        float c1 = 1.70158F;
        float c2 = c1 * 1.525F;
        return t < 0.5F
            ? ((2.0F * t) * (2.0F * t) * ((c2 + 1.0F) * (2.0F * t) - c2)) / 2.0F
            : ((2.0F * t - 2.0F) * (2.0F * t - 2.0F) * ((c2 + 1.0F) * (2.0F * t - 2.0F) + c2) + 2.0F) / 2.0F;
    }

    private static Map<String, Boolean> computeDangerState(String[] teams)
    {
        Map<String, Boolean> danger = new HashMap<>();
        if (teams.length < 2) return danger;
        float maxPct = -1;
        int maxIdx = -1;
        for (int i = 0; i < teams.length; i++)
        {
            float pct = i < ClientMatchData.teamPcts.length ? ClientMatchData.teamPcts[i] : 0;
            if (pct > maxPct) { maxPct = pct; maxIdx = i; }
        }
        if (maxIdx >= 0)
        {
            for (int i = 0; i < teams.length; i++)
            {
                float pct = i < ClientMatchData.teamPcts.length ? ClientMatchData.teamPcts[i] : 0;
                danger.put(teams[i], (maxPct - pct) > 15.0F);
            }
        }
        return danger;
    }

    private static AbstractClientPlayer findClientPlayer(Minecraft mc, UUID uuid)
    {
        if (mc.level == null) return null;
        for (AbstractClientPlayer player : mc.level.players())
        {
            if (player.getUUID().equals(uuid)) return player;
        }
        return null;
    }

    private static String getLocalPlayerTeam(Minecraft mc)
    {
        if (mc.player == null) return null;
        for (int i = 0; i < ClientMatchData.playerUUIDs.length; i++)
        {
            if (ClientMatchData.playerUUIDs[i].equals(mc.player.getUUID()))
                return ClientMatchData.playerTeams[i];
        }
        return null;
    }

    private static int rainbowColor()
    {
        float hue = (System.currentTimeMillis() % 1500) / 1500.0F;
        float r, g, b;
        int i = (int)(hue * 6);
        float f = hue * 6 - i;
        float q = 1.0F - f;
        switch (i % 6)
        {
            case 0: r = 1; g = f; b = 0; break;
            case 1: r = q; g = 1; b = 0; break;
            case 2: r = 0; g = 1; b = f; break;
            case 3: r = 0; g = q; b = 1; break;
            case 4: r = f; g = 0; b = 1; break;
            default: r = 1; g = 0; b = q; break;
        }
        int ir = (int)(r * 255);
        int ig = (int)(g * 255);
        int ib = (int)(b * 255);
        return (0xAA << 24) | (ir << 16) | (ig << 8) | ib;
    }

    private static String formatTime(int totalSeconds)
    {
        int min = totalSeconds / 60;
        int sec = totalSeconds % 60;
        if (min > 0) return String.format("%d:%02d", min, sec);
        else return String.format("%d", sec);
    }
}
