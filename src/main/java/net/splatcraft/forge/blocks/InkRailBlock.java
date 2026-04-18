package net.splatcraft.forge.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.splatcraft.forge.registries.SplatcraftBlocks;
import net.splatcraft.forge.registries.SplatcraftTileEntities;
import net.splatcraft.forge.tileentities.InkRailTileEntity;
import net.splatcraft.forge.util.BlockInkedResult;
import net.splatcraft.forge.util.InkBlockUtils;
import net.splatcraft.forge.util.InkRailUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InkRailBlock extends AbstractInkRailBlock implements EntityBlock
{
    private static final VoxelShape SHAPE = box(3.0D, 0.0D, 3.0D, 13.0D, 12.0D, 13.0D);
    private static final VoxelShape OCCUPIED_SHAPE = box(1.5D, 0.0D, 1.5D, 14.5D, 14.0D, 14.5D);

    public InkRailBlock()
    {
        super(Properties.of().mapColor(MapColor.METAL).strength(2.8F).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops(), SHAPE, OCCUPIED_SHAPE);
        SplatcraftBlocks.inkColoredBlocks.add(this);
    }

    @Override
    public BlockInkedResult inkBlock(Level level, BlockPos pos, int color, float damage, InkBlockUtils.InkType inkType)
    {
        InkRailTileEntity host = InkRailUtils.getHost(level, pos);
        if (host == null)
            return BlockInkedResult.FAIL;
        return InkRailUtils.handleInkHit(host, color);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state)
    {
        return SplatcraftTileEntities.inkRailTileEntity.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return type == SplatcraftTileEntities.inkRailTileEntity.get() ? (lvl, pos, blockState, blockEntity) -> InkRailTileEntity.tick(lvl, pos, blockState, (InkRailTileEntity) blockEntity) : null;
    }
}
