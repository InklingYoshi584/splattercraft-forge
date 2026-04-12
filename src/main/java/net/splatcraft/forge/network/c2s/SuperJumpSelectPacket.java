package net.splatcraft.forge.network.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.splatcraft.forge.commands.SuperJumpCommand;

import java.util.Optional;
import java.util.UUID;

public class SuperJumpSelectPacket extends PlayC2SPacket
{
	private final boolean spawnTarget;
	private final UUID playerTarget;

	public SuperJumpSelectPacket()
	{
		this(true, null);
	}

	public SuperJumpSelectPacket(UUID playerTarget)
	{
		this(false, playerTarget);
	}

	private SuperJumpSelectPacket(boolean spawnTarget, UUID playerTarget)
	{
		this.spawnTarget = spawnTarget;
		this.playerTarget = playerTarget;
	}

	@Override
	public void execute(Player player)
	{
		if (!(player instanceof ServerPlayer serverPlayer) || !SuperJumpCommand.canStartSuperJump(serverPlayer))
			return;

		Optional<Vec3> target = spawnTarget ? SuperJumpCommand.resolveSpawnDestination(serverPlayer) : resolvePlayerTarget(serverPlayer);
		if (target.isEmpty())
			return;

		SuperJumpCommand.startSuperJump(serverPlayer, target.get());
	}

	private Optional<Vec3> resolvePlayerTarget(ServerPlayer player)
	{
		if (playerTarget == null)
			return Optional.empty();

		Entity target = player.level().getPlayerByUUID(playerTarget);
		return target == null ? Optional.empty() : SuperJumpCommand.resolveEntityDestination(player, target, true);
	}

	@Override
	public void encode(FriendlyByteBuf buffer)
	{
		buffer.writeBoolean(spawnTarget);
		if (!spawnTarget && playerTarget != null)
			buffer.writeUUID(playerTarget);
	}

	public static SuperJumpSelectPacket decode(FriendlyByteBuf buffer)
	{
		boolean spawnTarget = buffer.readBoolean();
		return spawnTarget ? new SuperJumpSelectPacket() : new SuperJumpSelectPacket(false, buffer.readUUID());
	}
}
