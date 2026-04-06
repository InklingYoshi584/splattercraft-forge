package net.splatcraft.forge.items.weapons;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfoCapability;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BombRushSpecialItem extends SpecialWeaponItem
{
    public static final int POINTS_REQUIRED = 500;
    public static final int ACTIVE_TICKS = 140;

    public BombRushSpecialItem()
    {
        super(POINTS_REQUIRED);
    }

    @Override
    public int getActiveTicks(ItemStack stack)
    {
        return ACTIVE_TICKS;
    }

    @Override
    public boolean useSpecial(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        if (!PlayerInfoCapability.hasCapability(player))
            return false;

        PlayerInfoCapability.get(player).startSpecial(player.getInventory().selected, 0, getActiveTicks(specialStack));
        WeaponBaseItem.setSpecialPoints(mainWeapon, getPointsRequired(specialStack));
        player.displayClientMessage(Component.translatable("status.special.bomb_rush"), false);
        return true;
    }

    @Override
    public void onSpecialActiveTick(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, int activeTicksRemaining)
    {
        WeaponBaseItem.setSpecialPoints(mainWeapon, Math.max(0, activeTicksRemaining * getPointsRequired(specialStack) / Math.max(getActiveTicks(specialStack), 1)));
    }

    @Override
    public void onSpecialEnd(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, boolean interrupted)
    {
        WeaponBaseItem.setSpecialPoints(mainWeapon, 0);
    }

    @Override
    public boolean grantsInfiniteInk(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return true;
    }

    @Override
    protected void appendSpecialTooltip(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.splatcraft.bomb_rush.tooltip"));
    }
}
