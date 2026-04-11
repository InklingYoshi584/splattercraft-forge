package net.splatcraft.forge.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;

public class ItemStackEntityRenderer<T extends Entity & ItemSupplier> extends EntityRenderer<T>
{
    private final ItemRenderer itemRenderer;

    public ItemStackEntityRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight)
    {
        if (entity.isInvisible())
            return;

        poseStack.pushPose();
        poseStack.translate(0.0D, getVerticalOffset(entity), 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) + getYawOffset(entity)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, entity.xRotO, entity.getXRot()) + getPitchOffset(entity)));
        poseStack.mulPose(Axis.XP.rotationDegrees(getRollOffset(entity)));
        float scale = getScale(entity);
        poseStack.scale(scale, scale, scale);
        itemRenderer.renderStatic(entity.getItem(), getDisplayContext(entity), packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity)
    {
        return InventoryMenu.BLOCK_ATLAS;
    }

    protected float getVerticalOffset(T entity)
    {
        return 0.15F;
    }

    protected float getYawOffset(T entity)
    {
        return -90.0F;
    }

    protected float getPitchOffset(T entity)
    {
        return 90.0F;
    }

    protected float getRollOffset(T entity)
    {
        return 0.0F;
    }

    protected ItemDisplayContext getDisplayContext(T entity)
    {
        return ItemDisplayContext.GROUND;
    }

    protected float getScale(T entity)
    {
        return 1.0F;
    }
}
