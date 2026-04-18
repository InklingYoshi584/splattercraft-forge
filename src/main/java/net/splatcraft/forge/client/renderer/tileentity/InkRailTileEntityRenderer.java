package net.splatcraft.forge.client.renderer.tileentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.splatcraft.forge.tileentities.AbstractInkRailTileEntity;

public class InkRailTileEntityRenderer<T extends AbstractInkRailTileEntity> implements BlockEntityRenderer<T>
{
    public InkRailTileEntityRenderer(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public void render(T tileEntity, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay)
    {
    }
}
