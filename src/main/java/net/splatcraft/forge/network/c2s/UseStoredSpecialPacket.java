package net.splatcraft.forge.network.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.splatcraft.forge.items.weapons.WeaponBaseItem;
import net.splatcraft.forge.util.CommonUtils;

public class UseStoredSpecialPacket extends PlayC2SPacket
{
    @Override
    public void execute(Player player)
    {
        if (CommonUtils.anyWeaponOnCooldown(player))
            return;

        ItemStack mainWeapon = player.getMainHandItem();
        if (!(mainWeapon.getItem() instanceof WeaponBaseItem<?>))
            return;

        WeaponBaseItem.tryUseStoredSpecial(player.level(), player, mainWeapon);
    }

    @Override
    public void encode(FriendlyByteBuf buffer)
    {
    }

    public static UseStoredSpecialPacket decode(FriendlyByteBuf buffer)
    {
        return new UseStoredSpecialPacket();
    }
}
