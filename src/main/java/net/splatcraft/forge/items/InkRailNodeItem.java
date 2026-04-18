package net.splatcraft.forge.items;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.splatcraft.forge.blocks.AbstractInkRailBlock;
import net.splatcraft.forge.util.InkRailUtils;
import org.jetbrains.annotations.NotNull;

public class InkRailNodeItem extends InkRailBlockItem
{
    public InkRailNodeItem(net.minecraft.world.level.block.Block block)
    {
        super(block);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        if (context.getPlayer() != null && context.getPlayer().isCrouching() && level.getBlockState(context.getClickedPos()).getBlock() instanceof AbstractInkRailBlock)
        {
            boolean handled = InkRailUtils.handleLinkUse(level, context.getClickedPos(), context.getPlayer(), context.getItemInHand());
            return handled ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.FAIL;
        }

        return super.useOn(context);
    }
}
