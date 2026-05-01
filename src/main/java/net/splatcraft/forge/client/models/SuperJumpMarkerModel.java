package net.splatcraft.forge.client.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.splatcraft.forge.Splatcraft;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class SuperJumpMarkerModel
{
	private static final float ANIMATION_LENGTH = 2.0F;
	private static final float ARROW_BOB_SEGMENT = 0.25F;
	private static final ResourceLocation TEXTURE = new ResourceLocation(Splatcraft.MODID, "textures/entity/super_jump_marker.png");
	private static final float PIXEL = 1.0F / 16.0F;
	private static final float U_SCALE = 1.0F / 64.0F;
	private static final float V_SCALE = 1.0F / 32.0F;

	public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay, int color, boolean highlighted, float animationTime)
	{
		float red = ((color >> 16) & 0xFF) / 255.0F;
		float green = ((color >> 8) & 0xFF) / 255.0F;
		float blue = (color & 0xFF) / 255.0F;
		float loopTime = animationTime % ANIMATION_LENGTH;

		poseStack.pushPose();
		poseStack.scale(highlighted ? 1.1F : 1.0F, highlighted ? 1.1F : 1.0F, highlighted ? 1.1F : 1.0F);

		VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		renderRing(poseStack, consumer, packedLight, packedOverlay, red, green, blue, highlighted ? 1.0F : 0.92F, loopTime);
		renderArrow(poseStack, consumer, packedLight, packedOverlay, red, green, blue, highlighted ? 1.0F : 0.92F, loopTime);

		poseStack.popPose();
	}

	private void renderRing(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha, float animationTime)
	{
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(getRingRotation(animationTime)));
		poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));

		PoseStack.Pose pose = poseStack.last();
		float minX = -7.0F * PIXEL;
		float maxX = 7.0F * PIXEL;
		float minY = 1.0F * PIXEL;
		float maxY = 9.0F * PIXEL;
		float minZ = -7.0F * PIXEL;
		float maxZ = 7.0F * PIXEL;

		addQuad(consumer, pose.pose(), pose.normal(),
				maxX, maxY, minZ, u(10.0F), v(0.0F),
				minX, maxY, minZ, u(20.0F), v(0.0F),
				minX, minY, minZ, u(20.0F), v(6.0F),
				maxX, minY, minZ, u(10.0F), v(6.0F),
				0.0F, 0.0F, -1.0F, packedLight, packedOverlay, red, green, blue, alpha);
		addQuad(consumer, pose.pose(), pose.normal(),
				minX, maxY, maxZ, u(10.0F), v(12.0F),
				maxX, maxY, maxZ, u(20.0F), v(12.0F),
				maxX, minY, maxZ, u(20.0F), v(18.0F),
				minX, minY, maxZ, u(10.0F), v(18.0F),
				0.0F, 0.0F, 1.0F, packedLight, packedOverlay, red, green, blue, alpha);
		addQuad(consumer, pose.pose(), pose.normal(),
				maxX, maxY, minZ, u(10.0F), v(6.0F),
				maxX, maxY, maxZ, u(20.0F), v(6.0F),
				maxX, minY, maxZ, u(20.0F), v(12.0F),
				maxX, minY, minZ, u(10.0F), v(12.0F),
				1.0F, 0.0F, 0.0F, packedLight, packedOverlay, red, green, blue, alpha);
		addQuad(consumer, pose.pose(), pose.normal(),
				minX, maxY, maxZ, u(10.0F), v(18.0F),
				minX, maxY, minZ, u(20.0F), v(18.0F),
				minX, minY, minZ, u(20.0F), v(24.0F),
				minX, minY, maxZ, u(10.0F), v(24.0F),
				-1.0F, 0.0F, 0.0F, packedLight, packedOverlay, red, green, blue, alpha);
		addQuad(consumer, pose.pose(), pose.normal(),
				minX, maxY, minZ, u(10.0F), v(10.0F),
				maxX, maxY, minZ, u(0.0F), v(10.0F),
				maxX, maxY, maxZ, u(0.0F), v(0.0F),
				minX, maxY, maxZ, u(10.0F), v(0.0F),
				0.0F, 1.0F, 0.0F, packedLight, packedOverlay, red, green, blue, alpha);
		addQuad(consumer, pose.pose(), pose.normal(),
				minX, minY, maxZ, u(10.0F), v(10.0F),
				maxX, minY, maxZ, u(0.0F), v(10.0F),
				maxX, minY, minZ, u(0.0F), v(20.0F),
				minX, minY, minZ, u(10.0F), v(20.0F),
				0.0F, -1.0F, 0.0F, packedLight, packedOverlay, red, green, blue, alpha);

		poseStack.popPose();
	}

	private void renderArrow(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha, float animationTime)
	{
		poseStack.pushPose();
		poseStack.translate(0.0F, getArrowYOffset(animationTime) * PIXEL, 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(getArrowRotation(animationTime)));

		PoseStack.Pose pose = poseStack.last();
		float halfThickness = PIXEL / 32.0F;
		float minY = 14.0F * PIXEL;
		float maxY = 29.0F * PIXEL;
		float minZ = -8.0F * PIXEL;
		float maxZ = 8.0F * PIXEL;

		addQuad(consumer, pose.pose(), pose.normal(),
				halfThickness, maxY, minZ, u(32.0F), v(0.0F),
				halfThickness, maxY, maxZ, u(42.0F), v(0.0F),
				halfThickness, minY, maxZ, u(42.0F), v(9.0F),
				halfThickness, minY, minZ, u(32.0F), v(9.0F),
				1.0F, 0.0F, 0.0F, packedLight, packedOverlay, red, green, blue, alpha);
		addQuad(consumer, pose.pose(), pose.normal(),
				-halfThickness, maxY, maxZ, u(32.0F), v(9.0F),
				-halfThickness, maxY, minZ, u(42.0F), v(9.0F),
				-halfThickness, minY, minZ, u(42.0F), v(18.0F),
				-halfThickness, minY, maxZ, u(32.0F), v(18.0F),
				-1.0F, 0.0F, 0.0F, packedLight, packedOverlay, red, green, blue, alpha);

		poseStack.popPose();
	}

	private static float getRingRotation(float animationTime)
	{
		int step = Math.min(3, (int) (animationTime / 0.5F));
		return 90.0F * (step + 1);
	}

	private static float getArrowRotation(float animationTime)
	{
		return -45.0F - 180.0F * animationTime;
	}

	private static float getArrowYOffset(float animationTime)
	{
		float segmentTime = animationTime % (ARROW_BOB_SEGMENT * 2.0F);
		float normalized = segmentTime / ARROW_BOB_SEGMENT;
		return normalized <= 1.0F ? normalized - 1.0F : 1.0F - normalized;
	}

	private static float u(float coordinate)
	{
		return coordinate * U_SCALE;
	}

	private static float v(float coordinate)
	{
		return coordinate * V_SCALE;
	}

	private static void addQuad(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x1, float y1, float z1, float u1, float v1, float x2, float y2, float z2, float u2, float v2, float x3, float y3, float z3, float u3, float v3, float x4, float y4, float z4, float u4, float v4, float normalX, float normalY, float normalZ, int packedLight, int packedOverlay, float red, float green, float blue, float alpha)
	{
		consumer.vertex(pose, x1, y1, z1).color(red, green, blue, alpha).uv(u1, v1).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, normalX, normalY, normalZ).endVertex();
		consumer.vertex(pose, x2, y2, z2).color(red, green, blue, alpha).uv(u2, v2).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, normalX, normalY, normalZ).endVertex();
		consumer.vertex(pose, x3, y3, z3).color(red, green, blue, alpha).uv(u3, v3).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, normalX, normalY, normalZ).endVertex();
		consumer.vertex(pose, x4, y4, z4).color(red, green, blue, alpha).uv(u4, v4).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, normalX, normalY, normalZ).endVertex();
	}
}
