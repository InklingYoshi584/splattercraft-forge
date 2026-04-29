package net.splatcraft.forge.network.s2c;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.splatcraft.forge.client.handlers.DeathRecapClientHandler;

public class OpenDeathRecapPacket extends PlayS2CPacket
{
    private final Component deathMessage;
    private final Vec3 deathPos;
    private final ResourceLocation deathDimension;
    private final UUID killerId;
    private final int inkColor;
    private final int respawnDelayTicks;

    public OpenDeathRecapPacket(Component deathMessage, Vec3 deathPos, ResourceLocation deathDimension, UUID killerId, int inkColor, int respawnDelayTicks)
    {
        this.deathMessage = deathMessage;
        this.deathPos = deathPos;
        this.deathDimension = deathDimension;
        this.killerId = killerId;
        this.inkColor = inkColor;
        this.respawnDelayTicks = respawnDelayTicks;
    }

    public static OpenDeathRecapPacket decode(FriendlyByteBuf buffer)
    {
        Component deathMessage = buffer.readComponent();
        Vec3 deathPos = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        ResourceLocation deathDimension = buffer.readResourceLocation();
        UUID killerId = buffer.readBoolean() ? buffer.readUUID() : null;
        int inkColor = buffer.readInt();
        int respawnDelayTicks = buffer.readInt();
        return new OpenDeathRecapPacket(deathMessage, deathPos, deathDimension, killerId, inkColor, respawnDelayTicks);
    }

    @Override
    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeComponent(deathMessage);
        buffer.writeDouble(deathPos.x);
        buffer.writeDouble(deathPos.y);
        buffer.writeDouble(deathPos.z);
        buffer.writeResourceLocation(deathDimension);
        buffer.writeBoolean(killerId != null);
        if (killerId != null)
            buffer.writeUUID(killerId);
        buffer.writeInt(inkColor);
        buffer.writeInt(respawnDelayTicks);
    }

    @Override
    public void execute()
    {
        DeathRecapClientHandler.startRecap(new DeathRecapClientHandler.DeathRecapData(deathMessage, deathPos, deathDimension, killerId, inkColor, respawnDelayTicks));
    }
}
