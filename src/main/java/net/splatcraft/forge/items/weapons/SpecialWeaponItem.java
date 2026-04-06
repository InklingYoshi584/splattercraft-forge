package net.splatcraft.forge.items.weapons;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SpecialWeaponItem extends Item
{
    private final int pointsRequired;

    public SpecialWeaponItem()
    {
        this(60);
    }

    public SpecialWeaponItem(int pointsRequired)
    {
        super(new Properties().stacksTo(1));
        this.pointsRequired = pointsRequired;
    }

    public int getPointsRequired(ItemStack stack)
    {
        return pointsRequired;
    }

    public boolean canUseSpecial(Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return WeaponBaseItem.getSpecialPoints(mainWeapon) >= getPointsRequired(specialStack);
    }

    public boolean useSpecial(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        player.displayClientMessage(Component.translatable("status.special.dummy"), false);
        return true;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.splatcraft.dummy_special.tooltip"));
        tooltip.add(Component.translatable("item.splatcraft.special.points", getPointsRequired(stack)));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
