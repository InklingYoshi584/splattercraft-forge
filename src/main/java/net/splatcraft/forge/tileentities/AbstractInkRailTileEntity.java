package net.splatcraft.forge.tileentities;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.splatcraft.forge.util.InkRailUtils;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractInkRailTileEntity extends InkColorTileEntity
{
    protected final LinkedHashSet<BlockPos> links = new LinkedHashSet<>();

    protected AbstractInkRailTileEntity(BlockEntityType<?> type, BlockPos pos, BlockState state)
    {
        super(type, pos, state);
    }

    public List<BlockPos> getLinks()
    {
        return new ArrayList<>(links);
    }

    public boolean hasLink(BlockPos pos)
    {
        return links.contains(pos);
    }

    public boolean addLink(BlockPos pos)
    {
        return !pos.equals(getBlockPos()) && links.add(pos.immutable());
    }

    public boolean removeLink(BlockPos pos)
    {
        return links.remove(pos);
    }

    public void clearLinks()
    {
        links.clear();
    }

    public void sync()
    {
        setChanged();
        if (level != null)
        {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    public void saveAdditional(CompoundTag nbt)
    {
        ListTag list = new ListTag();
        for (BlockPos link : links)
            list.add(NbtUtils.writeBlockPos(link));
        nbt.put("Links", list);
        super.saveAdditional(nbt);
    }

    @Override
    public void load(@NotNull CompoundTag nbt)
    {
        links.clear();
        ListTag list = nbt.getList("Links", Tag.TAG_COMPOUND);
        for (Tag tag : list)
            links.add(NbtUtils.readBlockPos((CompoundTag) tag));
        super.load(nbt);
    }

    @Override
    public AABB getRenderBoundingBox()
    {
        return new AABB(worldPosition).inflate(InkRailUtils.MAX_LINK_DISTANCE);
    }

    public Set<BlockPos> getLinkSet()
    {
        return links;
    }
}
