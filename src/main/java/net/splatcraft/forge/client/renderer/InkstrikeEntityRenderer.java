package net.splatcraft.forge.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.splatcraft.forge.entities.InkstrikeEntity;

public class InkstrikeEntityRenderer extends ItemStackEntityRenderer<InkstrikeEntity>
{
    public InkstrikeEntityRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    protected float getPitchOffset(InkstrikeEntity entity)
    {
        return -90.0F;
    }
}
