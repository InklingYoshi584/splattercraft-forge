package net.splatcraft.forge.tileentities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.splatcraft.forge.handlers.InkRailHandler;
import net.splatcraft.forge.registries.SplatcraftTileEntities;
import net.splatcraft.forge.util.InkRailUtils;
import org.jetbrains.annotations.NotNull;

public class InkRailTileEntity extends AbstractInkRailTileEntity
{
    public static final int ACTIVE_TICKS = 200;
    public static final int FLASHING_TICKS = 60;
    public static final int ENEMY_HIT_PENALTY = 60;

    private int activeTicks;

    public InkRailTileEntity(BlockPos pos, BlockState state)
    {
        super(SplatcraftTileEntities.inkRailTileEntity.get(), pos, state);
    }

    public boolean isActive()
    {
        return activeTicks > 0;
    }

    public boolean isFlashing()
    {
        return activeTicks > 0 && activeTicks <= FLASHING_TICKS;
    }

    public boolean shouldShowFlashingModel()
    {
        return isFlashing() && (activeTicks / 5) % 2 == 0;
    }

    public int getActiveTicks()
    {
        return activeTicks;
    }

    public void activate(int color)
    {
        setColor(color);
        activeTicks = ACTIVE_TICKS;
        sync();
        if (level != null && !level.isClientSide)
            InkRailUtils.refreshNetworkStates(level, getBlockPos());
    }

    public void refreshActiveTime()
    {
        activeTicks = ACTIVE_TICKS;
        sync();
        if (level != null && !level.isClientSide)
            InkRailUtils.refreshNetworkStates(level, getBlockPos());
    }

    public void reduceActiveTime(int ticks)
    {
        int oldTicks = activeTicks;
        activeTicks = Math.max(0, activeTicks - ticks);
        sync();
        if (level != null && !level.isClientSide)
        {
            if (activeTicks == 0 && oldTicks > 0)
                InkRailHandler.onHostDisabled(level, getBlockPos());
            InkRailUtils.refreshNetworkStates(level, getBlockPos());
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, InkRailTileEntity tileEntity)
    {
        if (level.isClientSide || tileEntity.activeTicks <= 0)
            return;

        int previous = tileEntity.activeTicks;
        tileEntity.activeTicks--;
        tileEntity.setChanged();

        if (tileEntity.activeTicks == 0)
        {
            InkRailHandler.onHostDisabled(level, pos);
            tileEntity.sync();
            InkRailUtils.refreshNetworkStates(level, pos);
        }
        else if ((previous > FLASHING_TICKS && tileEntity.activeTicks <= FLASHING_TICKS) || tileEntity.activeTicks == ACTIVE_TICKS - 1 || (tileEntity.isFlashing() && previous / 5 != tileEntity.activeTicks / 5))
        {
            tileEntity.sync();
            InkRailUtils.refreshNetworkStates(level, pos);
        }
    }

    @Override
    public void saveAdditional(CompoundTag nbt)
    {
        nbt.putInt("ActiveTicks", activeTicks);
        super.saveAdditional(nbt);
    }

    @Override
    public void load(@NotNull CompoundTag nbt)
    {
        activeTicks = nbt.getInt("ActiveTicks");
        super.load(nbt);
    }
}
