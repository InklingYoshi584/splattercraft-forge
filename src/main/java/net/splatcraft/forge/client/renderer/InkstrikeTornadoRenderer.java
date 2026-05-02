package net.splatcraft.forge.client.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.entities.InkstrikeTornadoEntity;
import net.splatcraft.forge.util.ColorUtils;
import org.joml.Matrix4f;

public class InkstrikeTornadoRenderer extends EntityRenderer<InkstrikeTornadoEntity>
{
	private static final RenderType DISK_TYPE = RenderType.create("splatcraft:inkstrike_damage_disk",
			DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_FAN, 256, false, true,
			RenderType.CompositeState.builder()
					.setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
					.setTransparencyState(new RenderStateShard.TransparencyStateShard("translucent_transparency", () -> {
						RenderSystem.enableBlend();
						RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
					}, () -> {
						RenderSystem.disableBlend();
						RenderSystem.defaultBlendFunc();
					}))
					.setDepthTestState(new RenderStateShard.DepthTestStateShard("<= depth_test", 515))
					.setCullState(new RenderStateShard.CullStateShard(false))
					.createCompositeState(false));

	private static final int DISK_SEGMENTS = 32;

	public InkstrikeTornadoRenderer(EntityRendererProvider.Context context)
	{
		super(context);
	}

	@Override
	public void render(InkstrikeTornadoEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight)
	{
		float width = entity.getTornadoWidth();
		if (width <= 0.1F)
			return;

		float radius = width * 0.5F;
		float[] rgb = ColorUtils.hexToRGB(entity.getColor());
		float alpha = 0.35F;

		PoseStack.Pose pose = poseStack.last();
		VertexConsumer verts = buffer.getBuffer(DISK_TYPE);

		verts.vertex(pose.pose(), 0.0F, 0.05F, 0.0F).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
		for (int i = 0; i <= DISK_SEGMENTS; i++)
		{
			double angle = Math.PI * 2.0D * i / DISK_SEGMENTS;
			verts.vertex(pose.pose(), (float)(Math.cos(angle) * radius), 0.05F, (float)(Math.sin(angle) * radius)).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
		}

		VertexConsumer ringVerts = buffer.getBuffer(DISK_TYPE);
		float ringAlpha = 0.7F;
		for (int i = 0; i <= DISK_SEGMENTS; i++)
		{
			double angle = Math.PI * 2.0D * i / DISK_SEGMENTS;
			float innerR = radius * 0.92F;
			float outerR = radius * 1.0F;
			ringVerts.vertex(pose.pose(), (float)(Math.cos(angle) * innerR), 0.06F, (float)(Math.sin(angle) * innerR)).color(rgb[0], rgb[1], rgb[2], ringAlpha).endVertex();
			ringVerts.vertex(pose.pose(), (float)(Math.cos(angle) * outerR), 0.06F, (float)(Math.sin(angle) * outerR)).color(rgb[0], rgb[1], rgb[2], ringAlpha).endVertex();
		}
	}

	@Override
	public ResourceLocation getTextureLocation(InkstrikeTornadoEntity entity)
	{
		return new ResourceLocation(Splatcraft.MODID, "textures/particle/ink_splash_0.png");
	}
}
