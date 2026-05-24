package net.splatcraft.forge.client.handlers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
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
import net.splatcraft.forge.data.match.MatchType;

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

        String leaderTeam = computeLeaderTeam(teamNames);

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

        boolean hideTimer = ClientMatchData.matchType == MatchType.ZONES && ClientMatchData.overtimeActive;
        if (!hideTimer)
        {
            g.fill(timerX, timerY, timerX + timerWidth, timerY + timerHeight, BG_COLOR);
            g.drawCenteredString(mc.font, timeStr, sw / 2, timerY + TIMER_PAD_V, timerColor);
        }

        int leftX = timerX - ICON_GAP;
        int rightX = timerX + timerWidth + ICON_GAP;

        for (String team : leftTeams)
        {
            List<Integer> indices = teamPlayerIndices.get(team);
            if (indices == null) continue;
            boolean isLead = team.equals(leaderTeam);
            boolean trail = leaderTeam != null && !isLead;
            for (int idx : indices)
            {
                leftX -= iconTotal;
                renderPlayerIcon(g, mc, idx, leftX, timerY, iconSize, trail, isLead);
            }
        }

        for (String team : rightTeams)
        {
            List<Integer> indices = teamPlayerIndices.get(team);
            if (indices == null) continue;
            boolean isLead = team.equals(leaderTeam);
            boolean trail = leaderTeam != null && !isLead;
            for (int idx : indices)
            {
                renderPlayerIcon(g, mc, idx, rightX, timerY, iconSize, trail, isLead);
                rightX += iconTotal;
            }
        }

        if (ClientMatchData.matchType == MatchType.ZONES)
        {
            renderZonesHud(g, mc, timerX, timerY, timerWidth, timerHeight, sw);
        }
    }

    private static final int ZONE_BOX_H = 20;
    private static final int ZONE_BOX_W = 24;
    private static final int ZONE_GAP = 3;
    private static final int COUNTER_W = 50;
    private static final int COUNTER_H = 20;
    private static final int PENALTY_H = 13;

    private static void renderZonesHud(GuiGraphics g, Minecraft mc, int timerX, int timerY, int timerWidth, int timerHeight, int sw)
    {
        if (ClientMatchData.overtimeActive)
        {
            renderOvertimeHud(g, mc, timerX, timerY, timerWidth, timerHeight, sw);
        }

        String[] teams = ClientMatchData.teamNames;
        int[] timers = ClientMatchData.zoneTimers;
        int[] penalties = ClientMatchData.zonePenalties;
        int[] colors = ClientMatchData.teamColors;
        int ctrlIdx = ClientMatchData.controllingTeamIdx;
        BlockPos[] zoneMins = ClientMatchData.zoneMins;
        BlockPos[] zoneMaxs = ClientMatchData.zoneMaxs;
        int[][] zonePcts = ClientMatchData.zoneTeamPcts;

        if (teams.length < 2) return;

        List<String> leftTeams = new ArrayList<>();
        List<String> rightTeams = new ArrayList<>();
        for (int i = 0; i < teams.length; i++)
        {
            if (i % 2 == 0) leftTeams.add(teams[i]);
            else rightTeams.add(teams[i]);
        }

        int zoneCount = zoneMins.length;
        int totalZoneW = zoneCount * ZONE_BOX_W + Math.max(0, zoneCount - 1) * ZONE_GAP;
        int rowY = timerY + timerHeight + 4;

        int leftX = sw / 2 - COUNTER_W - totalZoneW / 2 - 8;
        int rightX = sw / 2 + totalZoneW / 2 + 8;

        int zoneStartX = sw / 2 - totalZoneW / 2;

        for (String team : leftTeams)
        {
            int idx = getTeamIndex(teams, team);
            if (idx < 0 || idx >= timers.length) continue;
            boolean isControlling = (idx == ctrlIdx);
            renderTeamCounter(g, mc, leftX, rowY, idx, teams, timers, penalties, colors, isControlling);
        }

        for (String team : rightTeams)
        {
            int idx = getTeamIndex(teams, team);
            if (idx < 0 || idx >= timers.length) continue;
            boolean isControlling = (idx == ctrlIdx);
            renderTeamCounter(g, mc, rightX, rowY, idx, teams, timers, penalties, colors, isControlling);
        }

        for (int zi = 0; zi < zoneCount; zi++)
        {
            int zx = zoneStartX + zi * (ZONE_BOX_W + ZONE_GAP);
            renderZoneIndicator(g, mc, zx, rowY, zi, zonePcts, ctrlIdx, teams);
        }
    }

    private static void renderTeamCounter(GuiGraphics g, Minecraft mc, int x, int y, int idx,
                                           String[] teams, int[] timers, int[] penalties,
                                           int[] colors, boolean isControlling)
    {
        int bgColor = isControlling ? (colors[idx] | 0xFF000000) : BG_COLOR;
        int textColor = isControlling ? 0xFFFFFFFF : (colors[idx] | 0xFF000000);

        g.fill(x, y, x + COUNTER_W, y + COUNTER_H, bgColor);
        g.fill(x + 1, y + 1, x + COUNTER_W - 1, y + COUNTER_H - 1, isControlling ? colors[idx] | 0xFF000000 : 0xFF202020);

        int remainingColor = isControlling ? 0x88FFFFFF : 0xFF777777;
        String remainingText = "REMAINING";

        g.pose().pushPose();
        float microScale = 0.5F;
        int remainingWidth = mc.font.width(remainingText);
        g.pose().translate(x + COUNTER_W / 2.0F - remainingWidth * microScale / 2.0F, y + 2, 0);
        g.pose().scale(microScale, microScale, 1);
        mc.font.drawInBatch(remainingText, 0, 0, remainingColor, false, g.pose().last().pose(), g.bufferSource(),
            net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
        g.pose().popPose();

        int timer = idx < timers.length ? timers[idx] : 100;
        String timerStr = String.valueOf(timer);
        int timerW = mc.font.width(timerStr);
        g.drawString(mc.font, timerStr, x + (COUNTER_W - timerW) / 2, y + 8, textColor);

        int penalty = idx < penalties.length ? penalties[idx] : 0;
        if (penalty > 0)
        {
            String penStr = "+" + penalty;
            int penW = mc.font.width(penStr);
            int penX = x + (COUNTER_W - penW) / 2;
            int penY = y + COUNTER_H + 3;
            int penBoxW = penW + 10;
            g.fill(penX - 5, penY, penX - 5 + penBoxW, penY + PENALTY_H, 0xAA000000);
            g.fill(penX - 4, penY + 1, penX - 4 + penBoxW - 2, penY + PENALTY_H - 1, BG_COLOR);
            g.drawString(mc.font, penStr, penX, penY + 2, 0xFFFFFFFF);
        }
    }

    private static void renderZoneIndicator(GuiGraphics g, Minecraft mc, int x, int y, int zoneIdx,
                                             int[][] zonePcts, int ctrlIdx, String[] teams)
    {
        int[] pcts = zoneIdx < zonePcts.length ? zonePcts[zoneIdx] : new int[0];
        if (pcts.length < 2) return;

        int borderColor = 0xFFFFFFFF;
        if (ctrlIdx >= 0 && ctrlIdx < pcts.length && pcts[ctrlIdx] > 70)
            borderColor = ClientMatchData.teamColors[ctrlIdx] | 0xFF000000;

        int bg = 0xFF1A1A1A;
        g.fill(x, y, x + ZONE_BOX_W, y + ZONE_BOX_H, borderColor);
        g.fill(x + 1, y + 1, x + ZONE_BOX_W - 1, y + ZONE_BOX_H - 1, bg);

        int barW = ZONE_BOX_W - 2;
        int drawnPct = 0;
        for (int ti = 0; ti < pcts.length; ti++)
        {
            int pct = Math.min(pcts[ti], 100);
            if (pct <= 0) continue;
            int segW = Math.max(1, pct * barW / 100);
            g.fill(x + 1 + drawnPct * barW / 100, y + 1,
                x + 1 + drawnPct * barW / 100 + segW, y + ZONE_BOX_H - 1,
                (ClientMatchData.teamColors[ti] & 0xFFFFFF) | 0xFF000000);
            drawnPct += pct;
        }
    }

    private static long lastOvertimeBellMs;

    private static void renderOvertimeHud(GuiGraphics g, Minecraft mc, int timerX, int timerY, int timerWidth, int timerHeight, int sw)
    {
        String[] teams = ClientMatchData.teamNames;
        if (teams.length < 2) return;

        int losingIdx = 0;
        int losingTimer = -1;
        for (int i = 0; i < teams.length; i++)
        {
            int t = i < ClientMatchData.zoneTimers.length ? ClientMatchData.zoneTimers[i] : 100;
            if (t > losingTimer) { losingTimer = t; losingIdx = i; }
        }

        int otColor = ClientMatchData.teamColors[losingIdx] | 0xFF000000;
        int drain = ClientMatchData.overtimeDrain;

        g.fill(timerX, timerY, timerX + timerWidth, timerY + timerHeight, otColor);

        if (drain >= 0 && drain <= 20)
        {
            int fillW = drain * timerWidth / 20;
            g.fill(timerX, timerY, timerX + fillW, timerY + timerHeight, 0x88000000);
        }

        g.pose().pushPose();
        float txtScale = 0.5F;
        String overtimeText = "\u00a7c\u00a7lOvertime!";
        int textW = mc.font.width(overtimeText);
        g.pose().translate(sw / 2.0F - textW * txtScale / 2.0F, timerY + timerHeight / 2.0F - mc.font.lineHeight * txtScale / 2.0F, 0);
        g.pose().scale(txtScale, txtScale, 1);
        mc.font.drawInBatch(overtimeText, 0, 0, 0xFFFFFFFF, false, g.pose().last().pose(), g.bufferSource(),
            net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
        g.pose().popPose();

        long now = System.currentTimeMillis();
        if (now - lastOvertimeBellMs >= 1000)
        {
            lastOvertimeBellMs = now;
            mc.player.playSound(net.minecraft.sounds.SoundEvents.BELL_BLOCK, 0.5F, 1.0F);
        }
    }

    private static int getTeamIndex(String[] teams, String name)
    {
        for (int i = 0; i < teams.length; i++)
            if (teams[i].equals(name)) return i;
        return -1;
    }

    private static void renderPlayerIcon(GuiGraphics g, Minecraft mc, int idx, int x, int y, int iconSize, boolean trailing, boolean leader)
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

        int actualSize = trailing ? Math.max(iconSize * 7 / 10, 8) : iconSize;
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

        if (leader)
            g.drawCenteredString(mc.font, "LEAD", x + BORDER + iconSize / 2, y - 6, 0xFFFFFF00);
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

        // ── Percentages / Scores inside the bar (fade in after crash) ──
        if (pctsVisible)
        {
            float pctFade = Mth.clamp((elapsedSec - PCTS_SHOW) / 0.3F, 0.0F, 1.0F);
            int alpha = (int) (pctFade * 255);
            int whiteColor = 0xFFFFFF | (alpha << 24);

            if (ClientMatchData.matchType == MatchType.ZONES)
            {
                String scoreText0 = ClientMatchData.zoneKnockout && pcts[0] >= 100
                    ? "KNOCKOUT!"
                    : String.valueOf((int) pcts[0]);
                String scoreText1 = ClientMatchData.zoneKnockout && pcts[1] >= 100
                    ? "KNOCKOUT!"
                    : String.valueOf((int) pcts[1]);

                int fontWidth0 = mc.font.width(scoreText0);
                int fontWidth1 = mc.font.width(scoreText1);
                int barCenterY = barY + barH / 2 - mc.font.lineHeight / 2;
                g.drawString(mc.font, scoreText1, barX + barWidth - fontWidth1 - 6, barCenterY, whiteColor);
                g.drawString(mc.font, scoreText0, barX + 6, barCenterY, whiteColor);
            }
            else
            {
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

    private static String computeLeaderTeam(String[] teams)
    {
        if (teams.length < 2) return null;
        if (ClientMatchData.matchType == MatchType.ZONES && ClientMatchData.zoneTimers.length == teams.length)
        {
            int best = Integer.MAX_VALUE;
            int idx = -1;
            boolean allEqual = true;
            for (int i = 0; i < teams.length; i++)
            {
                if (ClientMatchData.zoneTimers[i] < best) { best = ClientMatchData.zoneTimers[i]; idx = i; }
                if (ClientMatchData.zoneTimers[i] != ClientMatchData.zoneTimers[0]) allEqual = false;
            }
            if (allEqual) return null;
            return idx >= 0 ? teams[idx] : null;
        }
        else
        {
            float best = -1;
            int idx = -1;
            boolean allEqual = true;
            for (int i = 0; i < teams.length; i++)
            {
                float pct = i < ClientMatchData.teamPcts.length ? ClientMatchData.teamPcts[i] : 0;
                if (pct > best) { best = pct; idx = i; }
                if (pct != (0 < ClientMatchData.teamPcts.length ? ClientMatchData.teamPcts[0] : 0)) allEqual = false;
            }
            if (allEqual) return null;
            return idx >= 0 ? teams[idx] : null;
        }
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
