package net.splatcraft.forge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfo;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfoCapability;
import net.splatcraft.forge.network.SplatcraftPacketHandler;
import net.splatcraft.forge.network.s2c.PlayerSetSquidS2CPacket;
import net.splatcraft.forge.network.s2c.UpdatePlayerInfoPacket;
import net.splatcraft.forge.util.AbilityAccessUtils;
import net.splatcraft.forge.util.ColorUtils;
import net.splatcraft.forge.util.CommonUtils;
import net.splatcraft.forge.util.PlayerCooldown;

import java.util.Optional;

public class SuperJumpCommand
{
	public static final int STARTUP_TICKS = 20;
	public static final int TRAVEL_TICKS = 60;
	public static final int TOTAL_TICKS = STARTUP_TICKS + TRAVEL_TICKS;

	private static final int[][] LANDING_OFFSETS = new int[][]{
			{0, 0},
			{1, 0}, {-1, 0}, {0, 1}, {0, -1},
			{1, 1}, {1, -1}, {-1, 1}, {-1, -1},
			{2, 0}, {-2, 0}, {0, 2}, {0, -2}
	};
	private static final int[] LANDING_HEIGHTS = new int[]{0, -1, 1, -2, 2};

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
	{
		dispatcher.register(Commands.literal("superjump").requires(commandSource -> commandSource.hasPermission(2))
				.then(Commands.argument("to", BlockPosArgument.blockPos()).executes(context ->
				{
					BlockPos target = BlockPosArgument.getLoadedBlockPos(context, "to");
					return execute(context, new Vec3(target.getX() + 0.5D, target.getY() + 0.05D, target.getZ() + 0.5D));
				}))
				.then(Commands.argument("target", EntityArgument.entity()).executes(context -> execute(context, EntityArgument.getEntity(context, "target")))));
	}

	private static int execute(CommandContext<CommandSourceStack> context, Vec3 target) throws CommandSyntaxException
	{
		ServerPlayer player = context.getSource().getPlayerOrException();
		return startSuperJump(player, target) ? 1 : 0;
	}

	private static int execute(CommandContext<CommandSourceStack> context, Entity target) throws CommandSyntaxException
	{
		ServerPlayer player = context.getSource().getPlayerOrException();
		Optional<Vec3> resolved = resolveEntityDestination(player, target, false);
		return resolved.isPresent() && startSuperJump(player, resolved.get()) ? 1 : 0;
	}

	public static boolean canStartSuperJump(Player player)
	{
		if (player == null || player.isSpectator() || !player.isAlive())
			return false;

		if (!AbilityAccessUtils.canUseInkAbilities(player))
			return false;

		if (!PlayerInfoCapability.hasCapability(player))
			return false;

		PlayerInfo info = PlayerInfoCapability.get(player);
		if (info.hasActiveSpecial())
			return false;

		return !PlayerCooldown.hasPlayerCooldown(player)
				&& !CommonUtils.anyWeaponOnCooldown(player)
				&& !player.isUsingItem()
				&& !player.isPassenger();
	}

	public static boolean isSuperJumping(Player player)
	{
		return player != null && PlayerCooldown.hasOverloadedPlayerCooldown(player) && PlayerCooldown.getPlayerCooldown(player) instanceof SuperJump;
	}

	public static Optional<Vec3> resolveSpawnDestination(Player player)
	{
		if (player == null || player.level() == null || !PlayerInfoCapability.hasCapability(player))
			return Optional.empty();

		PlayerInfo info = PlayerInfoCapability.get(player);
		BlockPos respawnPos = info.getSuperJumpSpawnPos();
		String respawnDimension = info.getSuperJumpSpawnDimension();

		if (player instanceof ServerPlayer serverPlayer)
		{
			if (respawnPos == null)
				respawnPos = serverPlayer.getRespawnPosition();
			if (respawnDimension.isEmpty() && serverPlayer.getRespawnDimension() != null)
				respawnDimension = serverPlayer.getRespawnDimension().location().toString();
		}

		if (respawnPos == null)
			return Optional.empty();

		if (!respawnDimension.isEmpty() && !respawnDimension.equals(player.level().dimension().location().toString()))
			return Optional.empty();

		BlockState state = player.level().getBlockState(respawnPos);
		float respawnAngle = player instanceof ServerPlayer serverPlayer ? serverPlayer.getRespawnAngle() : 0.0F;
		Optional<Vec3> resolved = state.getBlock().getRespawnPosition(state, player.getType(), player.level(), respawnPos, respawnAngle, player);

		if (resolved.isPresent())
			return resolved;

		return findSafeLanding(player, respawnPos);
	}

	public static Optional<Vec3> resolvePlayerDestination(Player player, Player target)
	{
		return resolvePlayerDestination(player, target, true);
	}

	public static Optional<Vec3> resolvePlayerDestination(Player player, Player target, boolean requireFriendlyColor)
	{
		if (player == null || target == null || !target.isAlive() || target.isSpectator())
			return Optional.empty();

		if (player.level() != target.level())
			return Optional.empty();

		if (requireFriendlyColor && ColorUtils.getPlayerColor(player) != ColorUtils.getPlayerColor(target))
			return Optional.empty();

		return findSafeLanding(player, BlockPos.containing(target.getX(), target.getBoundingBox().minY, target.getZ()));
	}

	public static Optional<Vec3> resolveEntityDestination(Player player, Entity target, boolean requireFriendlyPlayer)
	{
		if (target instanceof Player targetPlayer)
			return resolvePlayerDestination(player, targetPlayer, requireFriendlyPlayer);

		if (target instanceof LivingEntity livingTarget)
		{
			Optional<Vec3> safeLanding = findSafeLanding(player, BlockPos.containing(livingTarget.getX(), livingTarget.getBoundingBox().minY, livingTarget.getZ()));
			if (safeLanding.isPresent())
				return safeLanding;
		}

		return Optional.of(target.position());
	}

	public static boolean startSuperJump(ServerPlayer player, Vec3 target)
	{
		return startJump(player, target, true, false);
	}

	public static boolean startForcedSuperJump(ServerPlayer player, Vec3 target)
	{
		return startJump(player, target, false, true);
	}

	public static boolean startPendingSuperJump(ServerPlayer player, Vec3 target)
	{
		if (!canStartSuperJump(player) || player == null || player.isSpectator() || !player.isAlive() || PlayerCooldown.hasPlayerCooldown(player))
			return false;

		SuperJump jump = new SuperJump(player.getInventory().selected, player.position(), target, player.noPhysics);
		jump.setPending(true);
		PlayerCooldown.setPlayerCooldown(player, jump);

		setJumpSquid(player, true);
		SplatcraftPacketHandler.sendToTrackersAndSelf(new UpdatePlayerInfoPacket(player), player);
		return true;
	}

	public static boolean isInStartup(LivingEntity entity)
	{
		if (!(entity instanceof Player player) || !isSuperJumping(player))
			return false;

		PlayerCooldown cooldown = PlayerCooldown.getPlayerCooldown(player);
		return cooldown instanceof SuperJump && cooldown.getTime() > TRAVEL_TICKS;
	}

	public static void syncSpawnPosition(ServerPlayer player)
	{
		if (!PlayerInfoCapability.hasCapability(player))
			return;

		PlayerInfo info = PlayerInfoCapability.get(player);
		if (player.getRespawnPosition() != null && player.getRespawnDimension() != null)
			info.setSuperJumpSpawn(player.getRespawnPosition(), player.getRespawnDimension().location().toString());
		else info.clearSuperJumpSpawn();

		SplatcraftPacketHandler.sendToTrackersAndSelf(new UpdatePlayerInfoPacket(player), player);
	}

	public static Optional<Vec3> resolveSafeLanding(Player player, BlockPos origin)
	{
		return findSafeLanding(player, origin);
	}

	private static boolean startJump(ServerPlayer player, Vec3 target, boolean validate, boolean instantStartup)
	{
		if ((validate && !canStartSuperJump(player)) || player == null || player.isSpectator() || !player.isAlive() || (!instantStartup && PlayerCooldown.hasPlayerCooldown(player)))
			return false;

		SuperJump jump = new SuperJump(player.getInventory().selected, player.position(), target, player.noPhysics);
		if (instantStartup)
			jump.setTime(TRAVEL_TICKS);
		PlayerCooldown.setPlayerCooldown(player, jump);

		player.stopUsingItem();
		player.stopFallFlying();
		player.getAbilities().flying = false;
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;
		player.fallDistance = 0;

		setJumpSquid(player, true);
		spawnJumpParticles(player);
		SplatcraftPacketHandler.sendToTrackersAndSelf(new UpdatePlayerInfoPacket(player), player);
		return true;
	}

	private static Optional<Vec3> findSafeLanding(Player player, BlockPos origin)
	{
		Level level = player.level();
		for (int yOffset : LANDING_HEIGHTS)
		{
			for (int[] offset : LANDING_OFFSETS)
			{
				BlockPos candidate = origin.offset(offset[0], yOffset, offset[1]);
				Vec3 safe = DismountHelper.findSafeDismountLocation(player.getType(), level, candidate, false);
				if (safe != null)
					return Optional.of(safe);
			}
		}

		return Optional.empty();
	}

	private static Vec3 getTravelPosition(SuperJump jump)
	{
		float progress = jump.getTravelProgress();
		Vec3 start = jump.getStart();
		Vec3 target = jump.getTarget();

		double x = Mth.lerp(progress, start.x, target.x);
		double z = Mth.lerp(progress, start.z, target.z);
		double baseY = Mth.lerp(progress, start.y, target.y);
		double arcHeight = Mth.clamp(start.subtract(target).horizontalDistance() * 0.25D, 4.0D, 12.0D) + 40.0D;
		double y = baseY + Math.sin(progress * Math.PI) * arcHeight;

		return new Vec3(x, y, z);
	}

	private static void setJumpSquid(Player player, boolean squid)
	{
		if (!PlayerInfoCapability.hasCapability(player))
			return;

		PlayerInfo info = PlayerInfoCapability.get(player);
		if (info.isSquid() == squid)
			return;

		info.setIsSquid(squid);
		if (!player.level().isClientSide())
			SplatcraftPacketHandler.sendToTrackersAndSelf(new PlayerSetSquidS2CPacket(player.getUUID(), squid), player);
	}

	private static void finishSuperJump(Player player, SuperJump jump, boolean cancelled)
	{
		player.noPhysics = jump.hadNoPhysics();
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;
		player.fallDistance = 0;

		if (!cancelled)
		{
			Vec3 target = jump.getTarget();
			if (player instanceof ServerPlayer serverPlayer)
				serverPlayer.connection.teleport(target.x, target.y, target.z, player.getYRot(), player.getXRot());
			else player.moveTo(target.x, target.y, target.z, player.getYRot(), player.getXRot());

			spawnJumpParticles(player);
		}

		setJumpSquid(player, false);
		PlayerCooldown.setPlayerCooldown(player, null);

		if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer)
			SplatcraftPacketHandler.sendToTrackersAndSelf(new UpdatePlayerInfoPacket(serverPlayer), serverPlayer);
	}

	private static void spawnJumpParticles(Player player)
	{
		if (!(player.level() instanceof ServerLevel serverLevel))
			return;

		for (int i = 0; i < 3; i++)
			ColorUtils.addInkSplashParticle(serverLevel, player, 1.2F);
	}

	@Mod.EventBusSubscriber(modid = Splatcraft.MODID)
	public static class Subscriber
	{
		@SubscribeEvent(priority = EventPriority.LOWEST)
		public static void playerTick(TickEvent.PlayerTickEvent event)
		{
			if (event.phase != TickEvent.Phase.START)
				return;

			Player player = event.player;
			if (!PlayerCooldown.hasOverloadedPlayerCooldown(player))
				return;

			PlayerCooldown cooldown = PlayerCooldown.getPlayerCooldown(player);
			if (!(cooldown instanceof SuperJump jump))
				return;

			if (!player.isAlive())
			{
				finishSuperJump(player, jump, true);
				return;
			}

			player.stopUsingItem();
			player.stopFallFlying();
			player.getAbilities().flying = false;
			player.fallDistance = 0;

			if (jump.isPending())
			{
				if (!player.onGround())
				{
					setJumpSquid(player, true);
					return;
				}
				jump.setPending(false);
				jump.start = player.position();
			}

			if (jump.getTime() > TRAVEL_TICKS)
			{
				player.noPhysics = player.level().isClientSide() || jump.hadNoPhysics();
				player.setDeltaMovement(Vec3.ZERO);
				player.hurtMarked = true;
				setJumpSquid(player, true);
				return;
			}

			if (jump.getTime() <= 1)
			{
				finishSuperJump(player, jump, false);
				return;
			}

			player.noPhysics = true;
			Vec3 targetPos = getTravelPosition(jump);
			player.setDeltaMovement(targetPos.subtract(player.position()));
			player.hurtMarked = true;
			setJumpSquid(player, jump.getTravelProgress() < 0.8F);
		}

		@SubscribeEvent
		public static void onLivingDeath(LivingDeathEvent event)
		{
			if (!(event.getEntity() instanceof Player player))
				return;

			if (!PlayerCooldown.hasOverloadedPlayerCooldown(player))
				return;

			PlayerCooldown cooldown = PlayerCooldown.getPlayerCooldown(player);
			if (cooldown instanceof SuperJump jump)
				finishSuperJump(player, jump, true);
		}
	}

	public static class SuperJump extends PlayerCooldown
	{
		private Vec3 start;
		private final Vec3 target;
		private final boolean noPhysics;
		private boolean pending;

		public SuperJump(int slotIndex, Vec3 start, Vec3 target, boolean canClip)
		{
			super(ItemStack.EMPTY, TOTAL_TICKS, slotIndex, InteractionHand.OFF_HAND, false, false, true, false);
			this.start = start;
			this.target = target;
			this.noPhysics = canClip;
			this.pending = false;
		}

		public SuperJump(CompoundTag nbt)
		{
			this(nbt.getInt("SlotIndex"),
					new Vec3(nbt.getDouble("StartX"), nbt.getDouble("StartY"), nbt.getDouble("StartZ")),
					new Vec3(nbt.getDouble("TargetX"), nbt.getDouble("TargetY"), nbt.getDouble("TargetZ")),
					nbt.getBoolean("CanClip"));
			setTime(nbt.getInt("Time"));
			this.pending = nbt.getBoolean("Pending");
		}

		public Vec3 getStart()
		{
			return start;
		}

		public Vec3 getTarget()
		{
			return target;
		}

		public boolean hadNoPhysics()
		{
			return noPhysics;
		}

		public boolean isPending()
		{
			return pending;
		}

		public void setPending(boolean pending)
		{
			this.pending = pending;
		}

		public float getTravelProgress()
		{
			return Mth.clamp((TRAVEL_TICKS - Math.max(2, getTime())) / (float) (TRAVEL_TICKS - 1), 0.0F, 1.0F);
		}

		@Override
		public CompoundTag writeNBT(CompoundTag nbt)
		{
			super.writeNBT(nbt);
			nbt.putDouble("StartX", start.x);
			nbt.putDouble("StartY", start.y);
			nbt.putDouble("StartZ", start.z);
			nbt.putDouble("TargetX", target.x);
			nbt.putDouble("TargetY", target.y);
			nbt.putDouble("TargetZ", target.z);
			nbt.putBoolean("SuperJump", true);
			nbt.putBoolean("CanClip", noPhysics);
			nbt.putBoolean("Pending", pending);
			return nbt;
		}
	}
}
