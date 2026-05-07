package net.splatcraft.forge.data.match;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

public class ZonesData
{
    public BlockPos min;
    public BlockPos max;

    public ZonesData(BlockPos min, BlockPos max)
    {
        this.min = new BlockPos(
            Math.min(min.getX(), max.getX()),
            Math.min(min.getY(), max.getY()),
            Math.min(min.getZ(), max.getZ())
        );
        this.max = new BlockPos(
            Math.max(min.getX(), max.getX()),
            Math.max(min.getY(), max.getY()),
            Math.max(min.getZ(), max.getZ())
        );
    }

    public boolean contains(BlockPos pos)
    {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public int blockCount()
    {
        return (max.getX() - min.getX() + 1)
             * (max.getY() - min.getY() + 1)
             * (max.getZ() - min.getZ() + 1);
    }

    public CompoundTag writeNBT()
    {
        CompoundTag nbt = new CompoundTag();
        nbt.put("Min", NbtUtils.writeBlockPos(min));
        nbt.put("Max", NbtUtils.writeBlockPos(max));
        return nbt;
    }

    public static ZonesData readNBT(CompoundTag nbt)
    {
        BlockPos min = NbtUtils.readBlockPos(nbt.getCompound("Min"));
        BlockPos max = NbtUtils.readBlockPos(nbt.getCompound("Max"));
        return new ZonesData(min, max);
    }
}
