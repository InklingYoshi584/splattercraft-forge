package net.splatcraft.forge.network.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.splatcraft.forge.items.weapons.SubWeaponItem;
import net.splatcraft.forge.items.weapons.WeaponBaseItem;
import net.splatcraft.forge.items.weapons.ZipcasterSpecialItem;
import net.splatcraft.forge.util.AbilityAccessUtils;
import net.splatcraft.forge.util.ColorUtils;
import net.splatcraft.forge.util.CommonUtils;

public class UseStoredSubWeaponPacket extends PlayC2SPacket
{
    @Override
    public void execute(Player player)
    {
        if (!AbilityAccessUtils.canUseInkAbilities(player))
            return;

        if (CommonUtils.anyWeaponOnCooldown(player))
            return;

        ItemStack mainWeapon = player.getMainHandItem();
        if (!(mainWeapon.getItem() instanceof WeaponBaseItem<?>))
            return;

        if (ZipcasterSpecialItem.isActive(player))
        {
            ZipcasterSpecialItem.tryUseZip(player, mainWeapon);
            return;
        }

        ItemStack subStack = WeaponBaseItem.getStoredSubWeapon(mainWeapon);
        if (!(subStack.getItem() instanceof SubWeaponItem subWeapon))
            return;

        subStack = subStack.copy();
        subStack.setCount(1);
        ColorUtils.setInkColor(subStack, ColorUtils.getInkColor(mainWeapon));
        ColorUtils.setColorLocked(subStack, ColorUtils.isColorLocked(mainWeapon));
        subWeapon.useFromMainWeapon(player.level(), player, subStack);
    }

    @Override
    public void encode(FriendlyByteBuf buffer)
    {
    }

    public static UseStoredSubWeaponPacket decode(FriendlyByteBuf buffer)
    {
        return new UseStoredSubWeaponPacket();
    }
}
