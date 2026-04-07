package net.splatcraft.forge.items.remotes;

import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.splatcraft.forge.blocks.IColoredBlock;
import net.splatcraft.forge.data.capabilities.worldink.WorldInk;
import net.splatcraft.forge.data.capabilities.worldink.WorldInkCapability;
import net.splatcraft.forge.registries.SplatcraftItemGroups;
import net.splatcraft.forge.util.InkBlockUtils;

import java.util.Collection;

public class InkDisruptorItem extends RemoteItem
{
    private static final int HALF_CLEAR_RANGE = 5;

    public InkDisruptorItem()
    {
        super(new Properties().stacksTo(1));
    }

    @Override
    public RemoteResult onRemoteUse(Level usedOnWorld, BlockPos posA, BlockPos posB, ItemStack stack, int colorIn, int mode, Collection<ServerPlayer> targets)
    {
        return clearInk(getLevel(usedOnWorld, stack), posA, posB);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide)
            return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);

        BlockPos center = player.blockPosition();
        RemoteResult remoteResult = clearInk(level,
                center.offset(-HALF_CLEAR_RANGE, -HALF_CLEAR_RANGE, -HALF_CLEAR_RANGE),
                center.offset(HALF_CLEAR_RANGE, HALF_CLEAR_RANGE, HALF_CLEAR_RANGE));

        if (remoteResult.getOutput() != null)
            player.displayClientMessage(remoteResult.getOutput(), true);

        level.playSound(null, player.getX(), player.getY(), player.getZ(), net.splatcraft.forge.registries.SplatcraftSounds.remoteUse, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1);
        return new InteractionResultHolder<>(remoteResult.wasSuccessful() ? InteractionResult.SUCCESS : InteractionResult.FAIL, stack);
    }

    public static RemoteResult clearInk(Level level, BlockPos posA, BlockPos posB)
    {
        BlockPos blockpos2 = new BlockPos(Math.min(posA.getX(), posB.getX()), Math.min(posB.getY(), posA.getY()), Math.min(posA.getZ(), posB.getZ()));
        BlockPos blockpos3 = new BlockPos(Math.max(posA.getX(), posB.getX()), Math.max(posB.getY(), posA.getY()), Math.max(posA.getZ(), posB.getZ()));

        if (!level.isInWorldBounds(blockpos2) || !level.isInWorldBounds(blockpos3))
            return createResult(false, Component.translatable("status.clear_ink.out_of_world"));

        /*
        for (int j = blockpos2.getZ(); j <= blockpos3.getZ(); j += 16)
        {
            for (int k = blockpos2.getX(); k <= blockpos3.getX(); k += 16)
            {
                if (!level.isLoaded(new BlockPos(k, blockpos3.getY() - blockpos2.getY(), j)))
                {
                    return createResult(false, Component.translatable("status.clear_ink.out_of_world"));
                }
            }
        }
        */
        int count = 0;
        int blockTotal = 0;
        for (int x = blockpos2.getX(); x <= blockpos3.getX(); x++)
        {
            for (int y = blockpos2.getY(); y <= blockpos3.getY(); y++)
            {
                for (int z = blockpos2.getZ(); z <= blockpos3.getZ(); z++)
                {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    if(InkBlockUtils.isInked(level, pos) && InkBlockUtils.clearInk(level, pos, false))
                        count++;
                    else if (state.getBlock() instanceof IColoredBlock block && block.remoteInkClear(level, pos))
                        count++;

                    blockTotal++;
                }
            }
        }

        return createResult(true, Component.translatable("status.clear_ink." + (count > 0 ? "success" : "no_ink"), count)).setIntResults(count, count * 15 / blockTotal);
    }
}
