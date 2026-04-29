package net.splatcraft.forge.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.splatcraft.forge.blocks.AbstractInkRailBlock;
import net.splatcraft.forge.blocks.InkRailBlock;
import net.splatcraft.forge.handlers.InkRailHandler;
import net.splatcraft.forge.tileentities.AbstractInkRailTileEntity;
import net.splatcraft.forge.tileentities.InkRailTileEntity;

public class InkRailUtils
{
    public static final double MAX_LINK_DISTANCE = 64.0D;
    public static final double ENTER_DISTANCE = 0.85D;
    public static final double RAIL_SPEED = 0.42D;
    public static final int NODE_PAUSE_TICKS = 5;

    private static final String TAG_SELECTED_POS = "InkRailSelectedPos";
    private static final String TAG_SELECTED_DIM = "InkRailSelectedDim";

    public static boolean handleLinkUse(Level level, BlockPos clickedPos, Player player, ItemStack stack)
    {
        if (level.isClientSide)
            return true;

        AbstractInkRailTileEntity clicked = getRail(level, clickedPos);
        if (clicked == null)
            return false;

        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(TAG_SELECTED_POS))
        {
            setSelection(stack, level, clickedPos);
            player.displayClientMessage(Component.translatable("status.ink_rail.selected", clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()).withStyle(ChatFormatting.AQUA), false);
            return true;
        }

        if (!level.dimension().location().toString().equals(tag.getString(TAG_SELECTED_DIM)))
        {
            clearSelection(stack);
            player.displayClientMessage(Component.translatable("status.ink_rail.selection_cleared").withStyle(ChatFormatting.YELLOW), false);
            return true;
        }

        BlockPos selectedPos = NbtUtils.readBlockPos(tag.getCompound(TAG_SELECTED_POS));
        if (selectedPos.equals(clickedPos))
        {
            clearSelection(stack);
            player.displayClientMessage(Component.translatable("status.ink_rail.selection_cleared").withStyle(ChatFormatting.YELLOW), false);
            return true;
        }

        LinkResult result = toggleLink(level, selectedPos, clickedPos);
        clearSelection(stack);
        showLinkMessage(player, result);
        return result.success();
    }

    public static boolean handlePlacedNode(Level level, BlockPos placedPos, Player player, ItemStack stack)
    {
        CompoundTag tag = stack.getTag();
        if (level.isClientSide || tag == null || !tag.contains(TAG_SELECTED_POS))
            return false;

        if (!level.dimension().location().toString().equals(tag.getString(TAG_SELECTED_DIM)))
        {
            clearSelection(stack);
            player.displayClientMessage(Component.translatable("status.ink_rail.selection_cleared").withStyle(ChatFormatting.YELLOW), false);
            return false;
        }

        BlockPos selectedPos = NbtUtils.readBlockPos(tag.getCompound(TAG_SELECTED_POS));
        LinkResult result = toggleLink(level, selectedPos, placedPos);
        showLinkMessage(player, result);

        if (result.success())
            setSelection(stack, level, placedPos);

        return result.success();
    }

    public static void clearSelection(ItemStack stack)
    {
        CompoundTag tag = stack.getOrCreateTag();
        tag.remove(TAG_SELECTED_POS);
        tag.remove(TAG_SELECTED_DIM);
    }

    public static void setSelection(ItemStack stack, Level level, BlockPos pos)
    {
        CompoundTag tag = stack.getOrCreateTag();
        tag.put(TAG_SELECTED_POS, NbtUtils.writeBlockPos(pos));
        tag.putString(TAG_SELECTED_DIM, level.dimension().location().toString());
    }

    private static void showLinkMessage(Player player, LinkResult result)
    {
        player.displayClientMessage(Component.translatable(result.translationKey()).withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
    }

    public static LinkResult toggleLink(Level level, BlockPos a, BlockPos b)
    {
        AbstractInkRailTileEntity first = getRail(level, a);
        AbstractInkRailTileEntity second = getRail(level, b);
        if (first == null || second == null)
            return LinkResult.INVALID_TARGET;

        if (first instanceof InkRailTileEntity && second instanceof InkRailTileEntity)
            return LinkResult.MULTIPLE_HOSTS;

        if (first.hasLink(b) && second.hasLink(a))
        {
            first.removeLink(b);
            second.removeLink(a);
            first.sync();
            second.sync();
            refreshStatesForNearbyHosts(level, a, b);
            return LinkResult.UNLINKED;
        }

        if (a.distSqr(b) > MAX_LINK_DISTANCE * MAX_LINK_DISTANCE)
            return LinkResult.TOO_FAR;

        if (!hasClearPath(level, a, b))
            return LinkResult.BLOCKED;

        Set<BlockPos> hosts = new HashSet<>(findHosts(level, a));
        hosts.addAll(findHosts(level, b));
        if (hosts.size() > 1)
            return LinkResult.MULTIPLE_HOSTS;

        first.addLink(b);
        second.addLink(a);
        first.sync();
        second.sync();

        BlockPos hostPos = hosts.isEmpty() ? null : hosts.iterator().next();
        if (hostPos != null)
            refreshNetworkStates(level, hostPos);

        return LinkResult.LINKED;
    }

    public static boolean hasClearPath(Level level, BlockPos a, BlockPos b)
    {
        Vec3 start = getConnectionPoint(level, a);
        Vec3 end = getConnectionPoint(level, b);
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        if (hit.getType() == HitResult.Type.MISS)
            return true;

        BlockPos hitPos = hit.getBlockPos();
        return hitPos.equals(a) || hitPos.equals(b);
    }

    public static AbstractInkRailTileEntity getRail(Level level, BlockPos pos)
    {
        return level.getBlockEntity(pos) instanceof AbstractInkRailTileEntity tileEntity ? tileEntity : null;
    }

    public static InkRailTileEntity getHost(Level level, BlockPos pos)
    {
        if (level.getBlockEntity(pos) instanceof InkRailTileEntity host)
            return host;

        for (BlockPos hostPos : findHosts(level, pos))
            if (level.getBlockEntity(hostPos) instanceof InkRailTileEntity host)
                return host;

        return null;
    }

    public static Set<BlockPos> findHosts(Level level, BlockPos start)
    {
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> hosts = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);

        while (!queue.isEmpty())
        {
            BlockPos current = queue.removeFirst();
            if (!visited.add(current))
                continue;

            AbstractInkRailTileEntity tileEntity = getRail(level, current);
            if (tileEntity == null)
                continue;

            if (tileEntity instanceof InkRailTileEntity)
                hosts.add(current);

            for (BlockPos link : tileEntity.getLinks())
                queue.add(link);
        }

        return hosts;
    }

    public static List<BlockPos> collectNetwork(Level level, BlockPos start)
    {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);

        while (!queue.isEmpty())
        {
            BlockPos current = queue.removeFirst();
            if (!visited.add(current))
                continue;

            AbstractInkRailTileEntity tileEntity = getRail(level, current);
            if (tileEntity == null)
                continue;

            result.add(current);
            for (BlockPos link : tileEntity.getLinks())
                queue.add(link);
        }

        return result;
    }

    public static void refreshNetworkStates(Level level, BlockPos hostPos)
    {
        if (level.isClientSide)
            return;

        InkRailTileEntity host = getHost(level, hostPos);
        if (host == null)
            return;

        boolean active = host.isActive();
        boolean flashing = host.shouldShowFlashingModel();

        for (BlockPos pos : collectNetwork(level, hostPos))
        {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof AbstractInkRailBlock))
                continue;

            BlockState updated = state.setValue(AbstractInkRailBlock.ACTIVE, active).setValue(AbstractInkRailBlock.FLASHING, active && flashing);
            if (!updated.equals(state))
                level.setBlock(pos, updated, 3);
        }
    }

    public static void clearNetworkStates(Level level, BlockPos start)
    {
        if (level.isClientSide)
            return;

        for (BlockPos pos : collectNetwork(level, start))
        {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof AbstractInkRailBlock))
                continue;

            BlockState updated = state.setValue(AbstractInkRailBlock.ACTIVE, false).setValue(AbstractInkRailBlock.FLASHING, false).setValue(AbstractInkRailBlock.OCCUPIED, false);
            if (!updated.equals(state))
                level.setBlock(pos, updated, 3);
        }
    }

    private static void refreshStatesForNearbyHosts(Level level, BlockPos... positions)
    {
        Set<BlockPos> hosts = new HashSet<>();
        for (BlockPos pos : positions)
        {
            Set<BlockPos> foundHosts = findHosts(level, pos);
            if (foundHosts.isEmpty())
                clearNetworkStates(level, pos);
            hosts.addAll(foundHosts);
        }
        for (BlockPos host : hosts)
            refreshNetworkStates(level, host);
    }

    public static void onRailBlockRemoved(Level level, BlockPos pos)
    {
        if (level.isClientSide)
            return;

        AbstractInkRailTileEntity tileEntity = getRail(level, pos);
        if (tileEntity == null)
            return;

        List<BlockPos> links = tileEntity.getLinks();
        tileEntity.clearLinks();
        for (BlockPos link : links)
        {
            AbstractInkRailTileEntity linked = getRail(level, link);
            if (linked != null && linked.removeLink(pos))
                linked.sync();
        }

        if (tileEntity instanceof InkRailTileEntity)
        {
            InkRailHandler.onHostDisabled(level, pos);
            refreshStatesForNearbyHosts(level, links.toArray(BlockPos[]::new));
        }
        else refreshStatesForNearbyHosts(level, links.toArray(BlockPos[]::new));
    }

    public static BlockInkedResult handleInkHit(InkRailTileEntity host, int color)
    {
        if (host.getLevel() == null)
            return BlockInkedResult.FAIL;

        Level level = host.getLevel();
        if (!host.isActive())
        {
            host.activate(color);
            return BlockInkedResult.SUCCESS;
        }

        if (ColorUtils.colorEquals(level, host.getBlockPos(), host.getColor(), color))
            host.refreshActiveTime();
        else host.reduceActiveTime(InkRailTileEntity.ENEMY_HIT_PENALTY);

        return BlockInkedResult.SUCCESS;
    }

    public static Vec3 getConnectionPoint(Level level, BlockPos pos)
    {
        double y = level.getBlockState(pos).getBlock() instanceof InkRailBlock ? 0.875D : 0.6875D;
        return Vec3.atLowerCornerOf(pos).add(0.5D, y, 0.5D);
    }

    public static Collection<BlockPos> getActiveLinks(Level level, BlockPos pos)
    {
        AbstractInkRailTileEntity tileEntity = getRail(level, pos);
        InkRailTileEntity host = getHost(level, pos);
        if (tileEntity == null || host == null || !host.isActive())
            return List.of();

        List<BlockPos> result = new ArrayList<>();
        for (BlockPos link : tileEntity.getLinks())
            if (host.equals(getHost(level, link)))
                result.add(link);
        return result;
    }

    public static boolean isEndpoint(Level level, BlockPos pos)
    {
        return getRail(level, pos) != null;
    }

    public static boolean setOccupied(Level level, BlockPos pos, boolean occupied)
    {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AbstractInkRailBlock) || state.getValue(AbstractInkRailBlock.OCCUPIED) == occupied)
            return false;

        level.setBlock(pos, state.setValue(AbstractInkRailBlock.OCCUPIED, occupied), 3);
        return true;
    }

    public static Vec3 closestPointOnSegment(Vec3 point, Vec3 start, Vec3 end)
    {
        Vec3 delta = end.subtract(start);
        double length = delta.lengthSqr();
        if (length <= 1.0E-6D)
            return start;
        double t = Math.max(0.0D, Math.min(1.0D, point.subtract(start).dot(delta) / length));
        return start.add(delta.scale(t));
    }

    public static double segmentProgress(Vec3 point, Vec3 start, Vec3 end)
    {
        Vec3 delta = end.subtract(start);
        double length = delta.lengthSqr();
        if (length <= 1.0E-6D)
            return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, point.subtract(start).dot(delta) / length));
    }

    public enum LinkResult
    {
        LINKED(true, "status.ink_rail.linked"),
        UNLINKED(true, "status.ink_rail.unlinked"),
        TOO_FAR(false, "status.ink_rail.too_far"),
        BLOCKED(false, "status.ink_rail.blocked"),
        MULTIPLE_HOSTS(false, "status.ink_rail.multiple_hosts"),
        INVALID_TARGET(false, "status.ink_rail.invalid_target");

        private final boolean success;
        private final String translationKey;

        LinkResult(boolean success, String translationKey)
        {
            this.success = success;
            this.translationKey = translationKey;
        }

        public boolean success()
        {
            return success;
        }

        public String translationKey()
        {
            return translationKey;
        }
    }
}
