package net.splatcraft.forge.client.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.entities.InkstrikeProfile;
import net.splatcraft.forge.handlers.SpecialHandler;
import net.splatcraft.forge.items.weapons.InkstrikeSpecialItem;
import net.splatcraft.forge.items.weapons.TripleInkstrikeSpecialItem;
import net.splatcraft.forge.network.SplatcraftPacketHandler;
import net.splatcraft.forge.network.c2s.InkstrikeTargetPacket;
import net.splatcraft.forge.util.ColorUtils;

@Mod.EventBusSubscriber(modid = Splatcraft.MODID, value = Dist.CLIENT)
public class InkstrikeTacticalOverlayHandler
{
	private static final int RADIUS = InkstrikeSpecialItem.TACTICAL_RADIUS;
	private static final int SAMPLE_STEP = 2;
	private static final int CELLS = RADIUS * 2 / SAMPLE_STEP + 1;
	private static final int PAD = 6;
	private static final int BG = FastColor.ARGB32.color(200, 10, 14, 22);
	private static final int FRAME = FastColor.ARGB32.color(240, 220, 226, 236);

	private static String cachedDim = "";
	private static int cachedCX = Integer.MIN_VALUE;
	private static int cachedCZ = Integer.MIN_VALUE;
	private static int[][] cachedColors = new int[CELLS][CELLS];

	public static double cursorOffX;
	public static double cursorOffZ;
	private static boolean cursorInit;
	private static boolean targetSent;

	public static boolean isOverlayActive(Player player)
	{
		if (!(player instanceof LocalPlayer))
			return false;

		Minecraft mc = Minecraft.getInstance();
		if (mc.screen != null || mc.isPaused())
			return false;

		if (targetSent)
		{
			ItemStack activeSpecial = SpecialHandler.getActiveSpecialStack(player);
			if (!(activeSpecial.getItem() instanceof InkstrikeSpecialItem)
					|| activeSpecial.getItem() instanceof TripleInkstrikeSpecialItem
					|| !InkstrikeSpecialItem.hasTargetCenter(player))
				targetSent = false;
			return false;
		}

		ItemStack activeSpecial = SpecialHandler.getActiveSpecialStack(player);
		if (!(activeSpecial.getItem() instanceof InkstrikeSpecialItem)
				|| activeSpecial.getItem() instanceof TripleInkstrikeSpecialItem
				|| !InkstrikeSpecialItem.hasTargetCenter(player))
		{
			targetSent = false;
			return false;
		}

		return !InkstrikeSpecialItem.hasPendingLaunch(player);
	}

	public static boolean interceptUseClick()
	{
		LocalPlayer player = Minecraft.getInstance().player;
		if (!isOverlayActive(player))
			return false;

		ensureCursor(player);
		int tx = Mth.floor(InkstrikeSpecialItem.getTargetCenterX(player) + cursorOffX);
		int tz = Mth.floor(InkstrikeSpecialItem.getTargetCenterZ(player) + cursorOffZ);
		targetSent = true;
		SplatcraftPacketHandler.sendToServer(new InkstrikeTargetPacket(tx, tz));
		return true;
	}

	public static void onSpecialEnd()
	{
		targetSent = false;
		cursorInit = false;
	}

	public static void consumeMouse(double dx, double dy)
	{
		LocalPlayer player = Minecraft.getInstance().player;
		if (!isOverlayActive(player))
			return;

		ensureCursor(player);
		cursorOffX = Mth.clamp(cursorOffX + dx * 0.3D, -RADIUS, RADIUS);
		cursorOffZ = Mth.clamp(cursorOffZ + dy * 0.3D, -RADIUS, RADIUS);
	}

	@SubscribeEvent
	public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event)
	{
		Minecraft mc = Minecraft.getInstance();
		if (!isOverlayActive(mc.player))
			return;

		if (event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id()))
			renderOverlay(event);

		event.setCanceled(true);
	}

	private static void renderOverlay(RenderGuiOverlayEvent event)
	{
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (mc.options.hideGui)
			return;

		ensureCursor(player);
		rebuildCache(player);

		int sw = event.getWindow().getGuiScaledWidth();
		int sh = event.getWindow().getGuiScaledHeight();
		int avail = Math.min(sw, sh) - 24;
		int cellPx = Math.max(1, avail / CELLS);
		int mapPx = CELLS * cellPx;
		int left = (sw - mapPx) / 2;
		int top = (sh - mapPx) / 2;
		GuiGraphics g = event.getGuiGraphics();

		g.fill(left - PAD, top - PAD, left + mapPx + PAD, top + mapPx + PAD, BG);
		g.fill(left - PAD, top - PAD, left + mapPx + PAD, top - PAD + 1, FRAME);
		g.fill(left - PAD, top + mapPx + PAD - 1, left + mapPx + PAD, top + mapPx + PAD, FRAME);
		g.fill(left - PAD, top - PAD, left - PAD + 1, top + mapPx + PAD, FRAME);
		g.fill(left + mapPx + PAD - 1, top - PAD, left + mapPx + PAD, top + mapPx + PAD, FRAME);

		for (int z = 0; z < CELLS; z++)
		{
			for (int x = 0; x < CELLS; x++)
			{
				int px = left + x * cellPx;
				int py = top + z * cellPx;
				g.fill(px, py, px + cellPx, py + cellPx, cachedColors[z][x]);
			}
		}

		int inkColor = ColorUtils.getPlayerColor(player);
		int preview = (inkColor & 0x00FFFFFF) | 0x55000000;
		int cx = left + toPixel(cursorOffX, cellPx);
		int cz = top + toPixel(cursorOffZ, cellPx);

		float tornadoRadius = InkstrikeProfile.SINGLE.tornadoDiameter() * 0.5F;
		int previewRadiusPx = Math.round(tornadoRadius / SAMPLE_STEP * cellPx);
		drawCircle(g, cx, cz, previewRadiusPx, preview);

		int px = left + toPixel(player.getX() - InkstrikeSpecialItem.getTargetCenterX(player), cellPx);
		int pz = top + toPixel(player.getZ() - InkstrikeSpecialItem.getTargetCenterZ(player), cellPx);
		g.fill(px - 2, pz - 2, px + 2, pz + 2, 0xFFFFFFFF);

		int cursorHalf = Math.max(1, cellPx / 2);
		int cursorLen = Math.max(4, cellPx * 2);
		g.fill(cx - cursorHalf, cz - cursorLen, cx + cursorHalf, cz + cursorLen, 0xFFFFF242);
		g.fill(cx - cursorLen, cz - cursorHalf, cx + cursorLen, cz + cursorHalf, 0xFFFFF242);

		int titleWidth = mc.font.width("INKSTRIKE TARGETING");
		g.drawString(mc.font, "INKSTRIKE TARGETING", left + (mapPx - titleWidth) / 2, top - 14, 0xFFF7F1C1, false);

		String hint = "(Right Click) Launch    WASD Move    Move Mouse to Aim";
		int hintWidth = mc.font.width(hint);
		g.drawString(mc.font, hint, left + (mapPx - hintWidth) / 2, top + mapPx + 14, 0xFFDDDDDD, false);
	}

	private static void ensureCursor(LocalPlayer player)
	{
		if (cursorInit && cachedCX == InkstrikeSpecialItem.getTargetCenterX(player) && cachedCZ == InkstrikeSpecialItem.getTargetCenterZ(player))
			return;

		cursorOffX = Mth.clamp(player.getX() - InkstrikeSpecialItem.getTargetCenterX(player), -RADIUS, RADIUS);
		cursorOffZ = Mth.clamp(player.getZ() - InkstrikeSpecialItem.getTargetCenterZ(player), -RADIUS, RADIUS);
		cursorInit = true;
	}

	private static void rebuildCache(LocalPlayer player)
	{
		String dim = player.level().dimension().location().toString();
		int cx = InkstrikeSpecialItem.getTargetCenterX(player);
		int cz = InkstrikeSpecialItem.getTargetCenterZ(player);
		if (dim.equals(cachedDim) && cx == cachedCX && cz == cachedCZ)
			return;

		cachedDim = dim;
		cachedCX = cx;
		cachedCZ = cz;

		int baseH = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz);
		for (int gz = 0; gz < CELLS; gz++)
		{
			int wz = cz + (gz * SAMPLE_STEP - RADIUS);
			for (int gx = 0; gx < CELLS; gx++)
			{
				int wx = cx + (gx * SAMPLE_STEP - RADIUS);
				int h = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz);
				int diff = h - baseH;
				cachedColors[gz][gx] = getHeightColor(diff);
			}
		}
	}

	private static int getHeightColor(int diff)
	{
		int t = Mth.clamp(Math.abs(diff) * 5, 0, 72);
		if (diff > 0)
			return FastColor.ARGB32.color(255, 82 + t, 98 + t / 2, 56);
		if (diff < 0)
			return FastColor.ARGB32.color(255, 42, 80 + t / 2, 102 + t);
		return FastColor.ARGB32.color(255, 54, 62, 70);
	}

	private static int toPixel(double blockOff, int cellPx)
	{
		double c = Mth.clamp(blockOff, -RADIUS, RADIUS);
		return (int)Math.round((c + RADIUS) / SAMPLE_STEP * cellPx);
	}

	private static void drawCircle(GuiGraphics g, int cx, int cz, int r, int color)
	{
		int rsq = r * r;
		for (int y = -r; y <= r; y++)
		{
			for (int x = -r; x <= r; x++)
			{
				if (x * x + y * y <= rsq)
					g.fill(cx + x, cz + y, cx + x + 1, cz + y + 1, color);
			}
		}
	}
}
