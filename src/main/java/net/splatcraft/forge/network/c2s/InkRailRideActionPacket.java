package net.splatcraft.forge.network.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.splatcraft.forge.handlers.InkRailHandler;

public class InkRailRideActionPacket extends PlayC2SPacket
{
    public static final int ACTION_DETACH = 0;
    public static final int ACTION_MOVE = 1;
    private final int action;
    private final int value;

    public InkRailRideActionPacket(int action)
    {
        this(action, 0);
    }

    public InkRailRideActionPacket(int action, int value)
    {
        this.action = action;
        this.value = value;
    }

    @Override
    public void execute(Player player)
    {
        if (action == ACTION_DETACH)
            InkRailHandler.jumpOff(player);
        else if (action == ACTION_MOVE)
            InkRailHandler.handleMoveInput(player, value);
    }

    @Override
    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(action);
        buffer.writeVarInt(value);
    }

    public static InkRailRideActionPacket decode(FriendlyByteBuf buffer)
    {
        return new InkRailRideActionPacket(buffer.readVarInt(), buffer.readVarInt());
    }
}
