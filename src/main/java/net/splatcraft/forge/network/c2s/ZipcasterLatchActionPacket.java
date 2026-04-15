package net.splatcraft.forge.network.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.splatcraft.forge.items.weapons.ZipcasterSpecialItem;
import net.splatcraft.forge.util.AbilityAccessUtils;

public class ZipcasterLatchActionPacket extends PlayC2SPacket
{
    private final int action;

    public ZipcasterLatchActionPacket(int action)
    {
        this.action = action;
    }

    @Override
    public void execute(Player player)
    {
        if (!AbilityAccessUtils.canUseInkAbilities(player))
            return;

        ZipcasterSpecialItem.handleLatchAction(player, action);
    }

    @Override
    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(action);
    }

    public static ZipcasterLatchActionPacket decode(FriendlyByteBuf buffer)
    {
        return new ZipcasterLatchActionPacket(buffer.readVarInt());
    }
}
