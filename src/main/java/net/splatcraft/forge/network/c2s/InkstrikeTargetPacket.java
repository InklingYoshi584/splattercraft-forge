package net.splatcraft.forge.network.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.splatcraft.forge.items.weapons.InkstrikeSpecialItem;
import net.splatcraft.forge.items.weapons.WeaponBaseItem;
import net.splatcraft.forge.util.AbilityAccessUtils;

public class InkstrikeTargetPacket extends PlayC2SPacket
{
    private final int targetX;
    private final int targetZ;

    public InkstrikeTargetPacket(int targetX, int targetZ)
    {
        this.targetX = targetX;
        this.targetZ = targetZ;
    }

    @Override
    public void execute(Player player)
    {
        if (!(player instanceof ServerPlayer) || !AbilityAccessUtils.canUseInkAbilities(player))
            return;

        ItemStack mainWeapon = player.getMainHandItem();
        if (!(mainWeapon.getItem() instanceof WeaponBaseItem<?>))
            return;

        InkstrikeSpecialItem.tryQueueTarget(player, mainWeapon, targetX, targetZ);
    }

    @Override
    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeInt(targetX);
        buffer.writeInt(targetZ);
    }

    public static InkstrikeTargetPacket decode(FriendlyByteBuf buffer)
    {
        return new InkstrikeTargetPacket(buffer.readInt(), buffer.readInt());
    }
}
