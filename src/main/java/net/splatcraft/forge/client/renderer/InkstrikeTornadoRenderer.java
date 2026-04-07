package net.splatcraft.forge.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.entities.InkstrikeTornadoEntity;

public class InkstrikeTornadoRenderer extends EntityRenderer<InkstrikeTornadoEntity>
{
    public InkstrikeTornadoRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public void render(InkstrikeTornadoEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight)
    {
    }

    @Override
    public ResourceLocation getTextureLocation(InkstrikeTornadoEntity entity)
    {
        return new ResourceLocation(Splatcraft.MODID, "textures/particle/ink_splash_0.png");
    }
}
