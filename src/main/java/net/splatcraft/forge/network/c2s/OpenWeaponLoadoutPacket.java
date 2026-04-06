package net.splatcraft.forge.network.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.splatcraft.forge.items.weapons.WeaponBaseItem;

public class OpenWeaponLoadoutPacket extends PlayC2SPacket
{
    @Override
    public void execute(Player player)
    {
        if (player instanceof ServerPlayer serverPlayer && player.getMainHandItem().getItem() instanceof WeaponBaseItem<?>)
            WeaponBaseItem.openLoadoutScreen(serverPlayer, InteractionHand.MAIN_HAND);
    }

    @Override
    public void encode(FriendlyByteBuf buffer)
    {
    }

    public static OpenWeaponLoadoutPacket decode(FriendlyByteBuf buffer)
    {
        return new OpenWeaponLoadoutPacket();
    }
}
