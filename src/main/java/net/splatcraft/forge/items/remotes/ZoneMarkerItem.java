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
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();

        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        CompoundTag nbt = stack.getOrCreateTag();

        if (nbt.contains("SavedStage"))
        {
            context.getPlayer().displayClientMessage(
                Component.translatable("status.zone.already_saved"), true);
            return InteractionResult.FAIL;
        }

        if (!nbt.contains("PointA"))
        {
            nbt.put("PointA", NbtUtils.writeBlockPos(pos));
            nbt.putString("Dimension", level.dimension().location().toString());
            context.getPlayer().displayClientMessage(
                Component.translatable("status.zone.point_a", pos.getX(), pos.getY(), pos.getZ()), true);
            return InteractionResult.SUCCESS;
        }
        else if (!nbt.contains("PointB"))
        {
            nbt.put("PointB", NbtUtils.writeBlockPos(pos));
            BlockPos a = NbtUtils.readBlockPos(nbt.getCompound("PointA"));
            context.getPlayer().displayClientMessage(
                Component.translatable("status.zone.point_b", a.getX(), a.getY(), a.getZ(),
                    pos.getX(), pos.getY(), pos.getZ()), true);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
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

            stage.addZone(a, b);

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
            tooltip.add(Component.translatable("item.remote.coords.b",
                a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()));
        }
        else if (nbt.contains("PointA"))
        {
            BlockPos a = NbtUtils.readBlockPos(nbt.getCompound("PointA"));
            tooltip.add(Component.translatable("item.remote.coords.a",
                a.getX(), a.getY(), a.getZ()));
        }
    }

    @Override
    public RemoteResult onRemoteUse(Level usedOnWorld, BlockPos posA, BlockPos posB, ItemStack stack, int colorIn, int mode, Collection<ServerPlayer> targets)
    {
        return createResult(true, null);
    }
}
