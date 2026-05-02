package net.splatcraft.forge.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.client.models.InkstrikeLandingModel;
import net.splatcraft.forge.entities.InkstrikeLandingIndicatorEntity;
import net.splatcraft.forge.util.ColorUtils;

public class InkstrikeLandingIndicatorRenderer extends EntityRenderer<InkstrikeLandingIndicatorEntity>
{
    private static final ResourceLocation TEXTURE = new ResourceLocation(Splatcraft.MODID, "textures/entity/inkstrike_landing_indicator.png");
    private final InkstrikeLandingModel model;

    public InkstrikeLandingIndicatorRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        this.model = new InkstrikeLandingModel(context.bakeLayer(InkstrikeLandingModel.LAYER_LOCATION));
    }

    @Override
    public void render(InkstrikeLandingIndicatorEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight)
    {
        float[] rgb = ColorUtils.hexToRGB(entity.getColor());
        float age = entity.tickCount + partialTicks;
        float pulse = 0.9F + 0.1F * (float)Math.sin(age * 0.4F);

        poseStack.pushPose();
        poseStack.translate(0.0D, -1.95D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 12.0F));
        poseStack.scale(pulse * 2.5F, pulse * 2.5F, pulse * 2.5F);
        model.setupAnim(entity, 0.0F, 0.0F, age, 0.0F, 0.0F);
        model.renderToBuffer(poseStack, buffer.getBuffer(model.renderType(getTextureLocation(entity))), packedLight, OverlayTexture.NO_OVERLAY, rgb[0], rgb[1], rgb[2], 1.0F);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(InkstrikeLandingIndicatorEntity entity)
    {
        return TEXTURE;
    }
}
