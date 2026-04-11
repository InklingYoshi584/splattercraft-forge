package net.splatcraft.forge.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.splatcraft.forge.entities.UltraStampThrownEntity;

public class UltraStampThrownEntityRenderer extends ItemStackEntityRenderer<UltraStampThrownEntity>
{
    public UltraStampThrownEntityRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    protected float getVerticalOffset(UltraStampThrownEntity entity)
    {
        return 1.1F;
    }

    @Override
    protected ItemDisplayContext getDisplayContext(UltraStampThrownEntity entity)
    {
        return ItemDisplayContext.FIXED;
    }

    @Override
    protected float getScale(UltraStampThrownEntity entity)
    {
        return 3.0F;
    }
}
