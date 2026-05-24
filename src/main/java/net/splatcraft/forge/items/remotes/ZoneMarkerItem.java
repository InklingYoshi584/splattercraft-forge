package net.splatcraft.forge.items.remotes;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.splatcraft.forge.data.Stage;
import net.splatcraft.forge.data.capabilities.saveinfo.SaveInfoCapability;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class ZoneMarkerItem extends RemoteItem
{
    public ZoneMarkerItem()
    {
        super(new Properties().stacksTo(1), 2);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        int mode = getRemoteMode(stack);

        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        if (mode == 1)
        {
            return removeZoneAt(level, stack, pos, context.getPlayer());
        }

        CompoundTag nbt = stack.getOrCreateTag();

        if (nbt.contains("SavedStage"))
        {
            context.getPlayer().displayClientMessage(
                Component.translatable("status.zone.already_saved"), true);
            return InteractionResult.FAIL;
        }

        BlockPos flatPos = new BlockPos(pos.getX(), 0, pos.getZ());

        if (!nbt.contains("PointA"))
        {
            nbt.put("PointA", NbtUtils.writeBlockPos(flatPos));
            nbt.putString("Dimension", level.dimension().location().toString());
            context.getPlayer().displayClientMessage(
                Component.translatable("status.zone.point_a", pos.getX(), pos.getZ()), true);
            return InteractionResult.SUCCESS;
        }
        else if (!nbt.contains("PointB"))
        {
            nbt.put("PointB", NbtUtils.writeBlockPos(flatPos));
            BlockPos a = NbtUtils.readBlockPos(nbt.getCompound("PointA"));
            context.getPlayer().displayClientMessage(
                Component.translatable("status.zone.point_b", a.getX(), a.getZ(),
                    pos.getX(), pos.getZ()), true);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private static InteractionResult removeZoneAt(Level level, ItemStack stack, BlockPos pos, Player player)
    {
        if (player == null) return InteractionResult.FAIL;

        List<Stage> stages = Stage.getStagesForPosition(level, Vec3.atCenterOf(pos));
        if (stages.isEmpty())
        {
            player.displayClientMessage(
                Component.translatable("status.zone.no_stage"), true);
            return InteractionResult.FAIL;
        }

        Stage stage = stages.get(0);
        for (int i = 0; i < stage.getZones().size(); i++)
        {
            if (stage.getZones().get(i).contains(pos))
            {
                stage.removeZone(i);

                String stageName = null;
                for (var entry : SaveInfoCapability.get(level.getServer()).getStages().entrySet())
                {
                    if (entry.getValue() == stage) { stageName = entry.getKey(); break; }
                }

                player.displayClientMessage(
                    Component.translatable("status.zone.removed", stageName != null ? stageName : "unknown", i), true);
                return InteractionResult.SUCCESS;
            }
        }

        player.displayClientMessage(
            Component.translatable("status.zone.not_in_zone"), true);
        return InteractionResult.FAIL;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && hasBothCoords(stack))
        {
            CompoundTag nbt = stack.getOrCreateTag();
            if (nbt.contains("SavedStage"))
            {
                player.displayClientMessage(
                    Component.translatable("status.zone.already_saved"), true);
                return InteractionResultHolder.fail(stack);
            }

            List<Stage> stages = Stage.getStagesForPosition(level, player.position());
            if (stages.isEmpty())
            {
                player.displayClientMessage(
                    Component.translatable("status.zone.no_stage"), true);
                return InteractionResultHolder.fail(stack);
            }

            Stage stage = stages.get(0);
            BlockPos a = NbtUtils.readBlockPos(nbt.getCompound("PointA"));
            BlockPos b = NbtUtils.readBlockPos(nbt.getCompound("PointB"));

            int minY = Math.min(stage.cornerA.getY(), stage.cornerB.getY());
            int maxY = Math.max(stage.cornerA.getY(), stage.cornerB.getY());
            BlockPos zoneA = new BlockPos(a.getX(), minY, a.getZ());
            BlockPos zoneB = new BlockPos(b.getX(), maxY, b.getZ());

            stage.addZone(zoneA, zoneB);

            String stageName = null;
            for (var entry : SaveInfoCapability.get(level.getServer()).getStages().entrySet())
            {
                if (entry.getValue() == stage)
                {
                    stageName = entry.getKey();
                    break;
                }
            }

            nbt.putString("SavedStage", stageName != null ? stageName : "unknown");
            nbt.putInt("SavedIndex", stage.getZones().size() - 1);

            player.displayClientMessage(
                Component.translatable("status.zone.saved", stageName != null ? stageName : "unknown",
                    stage.getZones().size() - 1), true);

            return InteractionResultHolder.success(stack);
        }

        return super.use(level, player, hand);
    }

    private static boolean hasBothCoords(ItemStack stack)
    {
        CompoundTag nbt = stack.getOrCreateTag();
        return nbt.contains("PointA") && nbt.contains("PointB");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        CompoundTag nbt = stack.getOrCreateTag();

        if (nbt.contains("SavedStage"))
        {
            tooltip.add(Component.translatable("item.zone.saved_to",
                nbt.getString("SavedStage"), nbt.getInt("SavedIndex")));
        }
        else if (nbt.contains("PointA") && nbt.contains("PointB"))
        {
            BlockPos a = NbtUtils.readBlockPos(nbt.getCompound("PointA"));
            BlockPos b = NbtUtils.readBlockPos(nbt.getCompound("PointB"));
            tooltip.add(Component.literal("(" + a.getX() + ", " + a.getZ() + ") to (" + b.getX() + ", " + b.getZ() + ")"));
        }
        else if (nbt.contains("PointA"))
        {
            BlockPos a = NbtUtils.readBlockPos(nbt.getCompound("PointA"));
            tooltip.add(Component.literal("(" + a.getX() + ", " + a.getZ() + ")"));
        }
    }

    @Override
    public RemoteResult onRemoteUse(Level usedOnWorld, BlockPos posA, BlockPos posB, ItemStack stack, int colorIn, int mode, Collection<ServerPlayer> targets)
    {
        return createResult(true, null);
    }
}
