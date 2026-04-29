package net.splatcraft.forge.items;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.splatcraft.forge.util.ColorUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InkRailBlockItem extends BlockItem
{
    public InkRailBlockItem(Block block)
    {
        super(block);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(@NotNull BlockPos pos, Level level, @Nullable Player player, @NotNull ItemStack stack, @NotNull BlockState state)
    {
        boolean updated = super.updateCustomBlockEntityTag(pos, level, player, stack, state);

        if (level.getServer() == null || level.getBlockEntity(pos) == null)
            return updated;

        int color = ColorUtils.getInkColor(stack);
        if (color != -1)
            ColorUtils.setInkColor(level.getBlockEntity(pos), color);
        ColorUtils.setInverted(level, pos, ColorUtils.isInverted(stack));
        return true;
    }
}
