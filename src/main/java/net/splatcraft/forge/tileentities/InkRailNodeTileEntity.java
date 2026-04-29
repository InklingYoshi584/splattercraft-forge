package net.splatcraft.forge.tileentities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.splatcraft.forge.registries.SplatcraftTileEntities;

public class InkRailNodeTileEntity extends AbstractInkRailTileEntity
{
    public InkRailNodeTileEntity(BlockPos pos, BlockState state)
    {
        super(SplatcraftTileEntities.inkRailNodeTileEntity.get(), pos, state);
    }
}
