package net.splatcraft.forge.network.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.splatcraft.forge.items.weapons.UltraStampSpecialItem;
import net.splatcraft.forge.util.AbilityAccessUtils;

public class UseUltraStampThrowPacket extends PlayC2SPacket
{
    @Override
    public void execute(Player player)
    {
        if (!AbilityAccessUtils.canUseInkAbilities(player))
            return;

        UltraStampSpecialItem.tryStartThrow(player);
    }

    @Override
    public void encode(FriendlyByteBuf buffer)
    {
    }

    public static UseUltraStampThrowPacket decode(FriendlyByteBuf buffer)
    {
        return new UseUltraStampThrowPacket();
    }
}
