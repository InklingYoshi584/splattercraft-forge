package net.splatcraft.forge.client.handlers;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.client.models.SuperJumpMarkerModel;
import net.splatcraft.forge.commands.SuperJumpCommand;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfoCapability;
import net.splatcraft.forge.items.weapons.WeaponBaseItem;
import net.splatcraft.forge.network.SplatcraftPacketHandler;
import net.splatcraft.forge.network.c2s.SuperJumpSelectPacket;
import net.splatcraft.forge.util.ColorUtils;
import net.splatcraft.forge.util.PlayerCooldown;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = Splatcraft.MODID)
public class SuperJumpOverlayHandler
{
	private static final int FULL_BRIGHT = LightTexture.pack(15, 15);
	private static final double MIN_SELECTION_DOT = 0.85D;
	private static final float DISTANCE_SCALE = 0.06F;
	private static final float ACTIVE_MARKER_START_OFFSET = 3.2F;
	private static final float CIRCLE_HEIGHT = 0.0625F;
	private static final int CIRCLE_SEGMENTS = 28;
	private static final SuperJumpMarkerModel JUMP_MARKER_MODEL = new SuperJumpMarkerModel();
	private static final RenderStateShard.TransparencyStateShard TRANSLUCENT_TRANSPARENCY = new RenderStateShard.TransparencyStateShard("translucent_transparency", () -> {
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
	}, () -> {
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
	});
	private static final RenderStateShard.TransparencyStateShard GLOW_TRANSPARENCY = new RenderStateShard.TransparencyStateShard("superjump_glow_transparency", () -> {
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
	}, () -> {
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
	});
	private static final RenderType MARKER_FILL_RENDER = RenderType.create("splatcraft:superjump_marker_fill", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_FAN, 256, false, true,
			RenderType.CompositeState.builder()
					.setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
					.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
					.setCullState(new RenderStateShard.CullStateShard(false))
					.createCompositeState(false));
	private static final RenderType MARKER_GLOW_RENDER = RenderType.create("splatcraft:superjump_marker_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_FAN, 256, false, true,
			RenderType.CompositeState.builder()
					.setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
					.setTransparencyState(GLOW_TRANSPARENCY)
					.setCullState(new RenderStateShard.CullStateShard(false))
					.createCompositeState(false));
	private static final RenderType MARKER_BAR_RENDER = RenderType.create("splatcraft:superjump_marker_bar", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
			RenderType.CompositeState.builder()
					.setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
					.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
					.setCullState(new RenderStateShard.CullStateShard(false))
					.createCompositeState(false));
	private static final RenderType MARKER_BAR_GLOW_RENDER = RenderType.create("splatcraft:superjump_marker_bar_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
			RenderType.CompositeState.builder()
					.setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
					.setTransparencyState(GLOW_TRANSPARENCY)
					.setCullState(new RenderStateShard.CullStateShard(false))
					.createCompositeState(false));

	public static boolean interceptUseClick()
	{
		LocalPlayer player = Minecraft.getInstance().player;
		if (!isOverlayActive(player))
			return false;

		TargetCandidate hoveredTarget = getHoveredTarget(player, collectTargets(player));
		if (hoveredTarget != null)
		{
			if (hoveredTarget.spawnTarget)
				SplatcraftPacketHandler.sendToServer(new SuperJumpSelectPacket());
			else if (hoveredTarget.playerTarget != null)
				SplatcraftPacketHandler.sendToServer(new SuperJumpSelectPacket(hoveredTarget.playerTarget));
		}

		return true;
	}

	public static boolean isOverlayActive(Player player)
	{
		return player instanceof LocalPlayer
				&& SplatcraftKeyHandler.isSuperJumpOverlayKeyDown()
				&& canOpenOverlay(player);
	}

	private static boolean canOpenOverlay(Player player)
	{
		if (player == null || Minecraft.getInstance().screen != null || Minecraft.getInstance().isPaused())
			return false;

		ItemStack mainHand = player.getMainHandItem();
		return SuperJumpCommand.canStartSuperJump(player)
				&& (!(mainHand.getItem() instanceof WeaponBaseItem<?>) || !WeaponBaseItem.hasActiveSpecial(mainHand));
	}

	@SubscribeEvent
	public static void renderLevel(RenderLevelStageEvent event)
	{
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
			return;

		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null)
			return;

		PoseStack poseStack = event.getPoseStack();
		Vec3 cameraPos = event.getCamera().getPosition();
		MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

		renderActiveDestinations(player, poseStack, buffer, cameraPos);

		if (isOverlayActive(player))
		{
			List<TargetCandidate> targets = collectTargets(player);
			TargetCandidate hoveredTarget = getHoveredTarget(player, targets);
			for (TargetCandidate target : targets)
				renderSelectionTarget(poseStack, buffer, cameraPos, target, hoveredTarget == target);
		}

		buffer.endBatch();
	}

	private static void renderActiveDestinations(LocalPlayer localPlayer, PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos)
	{
		for (Player player : localPlayer.level().players())
		{
			if (!PlayerCooldown.hasPlayerCooldown(player))
				continue;

			PlayerCooldown cooldown = PlayerCooldown.getPlayerCooldown(player);
			if (!(cooldown instanceof SuperJumpCommand.SuperJump jump))
				continue;

			renderActiveTarget(poseStack, buffer, cameraPos, jump.getTarget(), ColorUtils.getPlayerColor(player));
		}
	}

	private static void renderActiveTarget(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos, Vec3 position, int color)
	{
		poseStack.pushPose();
		poseStack.translate(position.x - cameraPos.x, position.y - cameraPos.y, position.z - cameraPos.z);
		renderCircleAndArrow(poseStack, buffer, getMarkerColor(color), false);
		poseStack.popPose();
	}

	private static List<TargetCandidate> collectTargets(LocalPlayer player)
	{
		List<TargetCandidate> targets = new ArrayList<>();

		SuperJumpCommand.resolveSpawnDestination(player).ifPresent(destination -> {
			BlockState state = PlayerInfoCapability.hasCapability(player) && PlayerInfoCapability.get(player).getSuperJumpSpawnPos() != null
					? player.level().getBlockState(PlayerInfoCapability.get(player).getSuperJumpSpawnPos())
					: null;
			ItemStack icon = state == null || state.getBlock().asItem() == Items.AIR ? new ItemStack(Items.COMPASS) : new ItemStack(state.getBlock());
			Component label = icon.getHoverName();
			targets.add(new TargetCandidate(destination, true, null, label, icon, null, ColorUtils.getPlayerColor(player)));
		});

		int playerColor = ColorUtils.getPlayerColor(player);
		for (Player other : player.level().players())
		{
			if (other == player || other.isSpectator() || !other.isAlive())
				continue;

			if (ColorUtils.getPlayerColor(other) != playerColor)
				continue;

			SuperJumpCommand.resolvePlayerDestination(player, other).ifPresent(destination ->
					targets.add(new TargetCandidate(destination, false, other.getUUID(), other.getDisplayName(), ItemStack.EMPTY, other, playerColor)));
		}

		return targets;
	}

	private static TargetCandidate getHoveredTarget(LocalPlayer player, List<TargetCandidate> targets)
	{
		if (targets.isEmpty())
			return null;

		Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		Vec3 lookVector = player.getViewVector(1.0F);

		TargetCandidate result = null;
		double bestScore = Double.MAX_VALUE;
		for (TargetCandidate target : targets)
		{
			Vec3 offset = target.position.subtract(cameraPos);
			if (offset.lengthSqr() <= 1.0E-4D)
				continue;

			Vec3 direction = offset.normalize();
			double dot = direction.dot(lookVector);
			if (dot < MIN_SELECTION_DOT)
				continue;

			double score = 1.0D - dot;
			if (score < bestScore)
			{
				bestScore = score;
				result = target;
			}
		}

		return result;
	}

	private static void renderTarget(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos, TargetCandidate target, boolean highlighted, boolean scaleWithDistance, float elementYOffset)
	{
		poseStack.pushPose();
		poseStack.translate(target.position.x - cameraPos.x, target.position.y - cameraPos.y, target.position.z - cameraPos.z);
		if (scaleWithDistance)
		{
			float scale = getDistanceScale((float) target.position.distanceTo(cameraPos));
			poseStack.scale(scale, scale, scale);
		}

		renderCircleAndArrow(poseStack, buffer, getMarkerColor(target.color), highlighted);
		if (elementYOffset > 0.0F)
			renderProgressBar(poseStack, buffer, getMarkerColor(target.color), highlighted, elementYOffset);

		if (target.icon.isEmpty())
			renderPlayerHead(poseStack, buffer, target.displayPlayer, highlighted, elementYOffset);
		else renderItemIcon(poseStack, buffer, target.icon, highlighted, elementYOffset);

		renderLabel(poseStack, buffer, target.label, highlighted, elementYOffset);
		poseStack.popPose();
	}

	private static void renderSelectionTarget(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos, TargetCandidate target, boolean highlighted)
	{
		poseStack.pushPose();
		poseStack.translate(target.position.x - cameraPos.x, target.position.y - cameraPos.y, target.position.z - cameraPos.z);
		float scale = getDistanceScale((float) target.position.distanceTo(cameraPos));
		poseStack.scale(scale, scale, scale);

		renderSelectionCircle(poseStack, buffer, getMarkerColor(target.color), highlighted);
		if (target.icon.isEmpty())
			renderPlayerHead(poseStack, buffer, target.displayPlayer, highlighted, 0.0F);
		else renderItemIcon(poseStack, buffer, target.icon, highlighted, 0.0F);

		renderLabel(poseStack, buffer, target.label, highlighted, 0.0F);
		poseStack.popPose();
	}

	private static int getMarkerColor(int color)
	{
		return color == -1 ? 0xFFFFFF : color;
	}

	private static void renderCircleAndArrow(PoseStack poseStack, MultiBufferSource buffer, int color, boolean highlighted)
	{
		Minecraft minecraft = Minecraft.getInstance();
		float animationTime = (minecraft.level.getGameTime() + minecraft.getFrameTime()) / 20.0F;
		JUMP_MARKER_MODEL.render(poseStack, buffer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, highlighted, animationTime);
	}

	private static void renderSelectionCircle(PoseStack poseStack, MultiBufferSource buffer, int color, boolean highlighted)
	{
		float red = ((color >> 16) & 0xFF) / 255.0F;
		float green = ((color >> 8) & 0xFF) / 255.0F;
		float blue = (color & 0xFF) / 255.0F;
		float radius = highlighted ? 1.1F : 0.9F;
		float glowRadius = radius + (highlighted ? 0.22F : 0.18F);
		float fillAlpha = highlighted ? 0.38F : 0.28F;
		float glowAlpha = highlighted ? 0.24F : 0.18F;

		addDisk(buffer.getBuffer(MARKER_GLOW_RENDER), poseStack.last().pose(), glowRadius, red, green, blue, glowAlpha);
		addDisk(buffer.getBuffer(MARKER_FILL_RENDER), poseStack.last().pose(), radius, red, green, blue, fillAlpha);
	}

	private static void renderProgressBar(PoseStack poseStack, MultiBufferSource buffer, int color, boolean highlighted, float elementYOffset)
	{
		poseStack.pushPose();
		poseStack.translate(0.0D, 0.45D, 0.0D);
		poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());

		float red = ((color >> 16) & 0xFF) / 255.0F;
		float green = ((color >> 8) & 0xFF) / 255.0F;
		float blue = (color & 0xFF) / 255.0F;
		float width = highlighted ? 0.44F : 0.34F;
		float glowWidth = width + 0.12F;
		float bottom = 0.0F;
		float top = 1.55F + elementYOffset;

		addBillboardQuad(buffer.getBuffer(MARKER_BAR_GLOW_RENDER), poseStack.last().pose(), -glowWidth, bottom, glowWidth, top, red, green, blue, highlighted ? 0.18F : 0.14F);
		addBillboardQuad(buffer.getBuffer(MARKER_BAR_RENDER), poseStack.last().pose(), -width, bottom, width, top, red, green, blue, highlighted ? 0.22F : 0.16F);
		poseStack.popPose();
	}

	private static void renderItemIcon(PoseStack poseStack, MultiBufferSource buffer, ItemStack icon, boolean highlighted, float elementYOffset)
	{
		poseStack.pushPose();
		poseStack.translate(0.0D, 1.15D + elementYOffset, 0.0D);
		poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
		float scale = highlighted ? 0.55F : 0.45F;
		poseStack.scale(scale, -scale, scale);
		Minecraft.getInstance().getItemRenderer().renderStatic(icon, ItemDisplayContext.GUI, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poseStack, buffer, Minecraft.getInstance().level, 0);
		poseStack.popPose();
	}

	private static void renderPlayerHead(PoseStack poseStack, MultiBufferSource buffer, Player player, boolean highlighted, float elementYOffset)
	{
		if (!(player instanceof AbstractClientPlayer clientPlayer))
			return;

		ResourceLocation skin = clientPlayer.getSkinTextureLocation();
		VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(skin));

		poseStack.pushPose();
		poseStack.translate(0.0D, 1.18D + elementYOffset, 0.0D);
		poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
		float scale = highlighted ? 0.03F : 0.026F;
		poseStack.scale(scale, -scale, scale);

		Matrix4f pose = poseStack.last().pose();
		Matrix3f normal = poseStack.last().normal();
		renderFaceQuad(consumer, pose, normal, -8.0F, -8.0F, 8.0F, 8.0F, 8.0F / 64.0F, 16.0F / 64.0F, 16.0F / 64.0F, 8.0F / 64.0F, FULL_BRIGHT);
		renderFaceQuad(consumer, pose, normal, -8.5F, -8.5F, 8.5F, 8.5F, 40.0F / 64.0F, 16.0F / 64.0F, 48.0F / 64.0F, 8.0F / 64.0F, FULL_BRIGHT);
		poseStack.popPose();
	}

	private static void renderLabel(PoseStack poseStack, MultiBufferSource buffer, Component label, boolean highlighted, float elementYOffset)
	{
		String text = label.getString();
		if (text.isEmpty())
			return;

		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;

		poseStack.pushPose();
		poseStack.translate(0.0D, 1.85D + elementYOffset, 0.0D);
		poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
		float scale = highlighted ? 0.026F : 0.022F;
		poseStack.scale(-scale, -scale, scale);
		Matrix4f pose = poseStack.last().pose();
		float x = -font.width(text) / 2.0F;
		font.drawInBatch(text, x, 0.0F, highlighted ? 0xFFFFFFFF : 0xFFF0F0F0, false, pose, buffer, Font.DisplayMode.NORMAL, 0x55000000, FULL_BRIGHT);
		poseStack.popPose();
	}

	private static void addBillboardQuad(VertexConsumer consumer, Matrix4f pose, float minX, float minY, float maxX, float maxY, float red, float green, float blue, float alpha)
	{
		consumer.vertex(pose, minX, maxY, 0.0F).color(red, green, blue, alpha).endVertex();
		consumer.vertex(pose, maxX, maxY, 0.0F).color(red, green, blue, alpha).endVertex();
		consumer.vertex(pose, maxX, minY, 0.0F).color(red, green, blue, alpha).endVertex();
		consumer.vertex(pose, minX, minY, 0.0F).color(red, green, blue, alpha).endVertex();
	}

	private static void addDisk(VertexConsumer consumer, Matrix4f pose, float radius, float red, float green, float blue, float alpha)
	{
		consumer.vertex(pose, 0.0F, CIRCLE_HEIGHT, 0.0F).color(red, green, blue, alpha).endVertex();
		for (int i = 0; i <= CIRCLE_SEGMENTS; i++)
		{
			double angle = Math.PI * 2.0D * i / CIRCLE_SEGMENTS;
			consumer.vertex(pose, (float) (Math.cos(angle) * radius), CIRCLE_HEIGHT, (float) (Math.sin(angle) * radius)).color(red, green, blue, alpha).endVertex();
		}
	}

	private static void renderFaceQuad(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float minX, float minY, float maxX, float maxY, float minU, float minV, float maxU, float maxV, int light)
	{
		consumer.vertex(pose, minX, maxY, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).uv(minU, minV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
		consumer.vertex(pose, maxX, maxY, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).uv(maxU, minV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
		consumer.vertex(pose, maxX, minY, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).uv(maxU, maxV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
		consumer.vertex(pose, minX, minY, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).uv(minU, maxV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
	}

	private static float getDistanceScale(float distance)
	{
		return Math.max(0.65F, distance * DISTANCE_SCALE);
	}

	private static class TargetCandidate
	{
		private final Vec3 position;
		private final boolean spawnTarget;
		private final UUID playerTarget;
		private final Component label;
		private final ItemStack icon;
		private final Player displayPlayer;
		private final int color;

		private TargetCandidate(Vec3 position, boolean spawnTarget, UUID playerTarget, Component label, ItemStack icon, Player displayPlayer, int color)
		{
			this.position = position;
			this.spawnTarget = spawnTarget;
			this.playerTarget = playerTarget;
			this.label = label;
			this.icon = icon;
			this.displayPlayer = displayPlayer;
			this.color = color;
		}
	}
}
