package net.splatcraft.forge.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfo;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfoCapability;
import net.splatcraft.forge.network.SplatcraftPacketHandler;
import net.splatcraft.forge.network.s2c.UpdatePlayerInfoPacket;
import net.splatcraft.forge.tileentities.InkRailTileEntity;
import net.splatcraft.forge.util.AbilityAccessUtils;
import net.splatcraft.forge.util.ColorUtils;
import net.splatcraft.forge.util.InkRailUtils;

@Mod.EventBusSubscriber
public class InkRailHandler
{
    private static final int DETACH_COOLDOWN_TICKS = 10;
    private static final double NODE_SWITCH_EPSILON = 0.2D;
    private static final double NODE_SWITCH_MARGIN = 0.03D;
    private static final Map<UUID, RideData> ACTIVE_RIDES = new HashMap<>();
    private static final Map<UUID, Long> DETACH_COOLDOWNS = new HashMap<>();

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START || event.player.level().isClientSide)
            return;

        Player player = event.player;
        RideData ride = ACTIVE_RIDES.get(player.getUUID());
        if (ride != null)
            tickRide(player, ride);
        else if (!isDetachOnCooldown(player))
            tryEnterRail(player);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event)
    {
        if (event.getEntity() instanceof Player player)
            detach(player);
    }

    public static void onHostDisabled(Level level, BlockPos hostPos)
    {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, RideData> entry : ACTIVE_RIDES.entrySet())
        {
            RideData ride = entry.getValue();
            if (ride.hostPos.equals(hostPos) && ride.dimension.equals(level.dimension().location().toString()))
                toRemove.add(entry.getKey());
        }

        for (UUID uuid : toRemove)
            if (level.getPlayerByUUID(uuid) != null)
            {
                Player player = level.getPlayerByUUID(uuid);
                detach(player);
            }
    }

    public static void handleMoveInput(Player player, int moveIntent)
    {
        RideData ride = ACTIVE_RIDES.get(player.getUUID());
        if (ride != null)
            ride.moveIntent = Math.max(-1, Math.min(1, moveIntent));
    }

    public static void jumpOff(Player player)
    {
        Vec3 launch = new Vec3(player.getLookAngle().x * 0.25D, 0.42D, player.getLookAngle().z * 0.25D);
        detach(player, launch, true);
    }

    private static boolean isDetachOnCooldown(Player player)
    {
        Long until = DETACH_COOLDOWNS.get(player.getUUID());
        if (until == null)
            return false;
        if (player.level().getGameTime() >= until)
        {
            DETACH_COOLDOWNS.remove(player.getUUID());
            return false;
        }
        return true;
    }

    private static void tryEnterRail(Player player)
    {
        if (!AbilityAccessUtils.canUseInkAbilities(player) || !PlayerInfoCapability.hasCapability(player) || !PlayerInfoCapability.get(player).isSquid())
            return;

        Candidate candidate = null;
        Vec3 look = player.getLookAngle().normalize();
        BlockPos playerPos = player.blockPosition();
        for (int x = -2; x <= 2; x++)
        {
            for (int y = -2; y <= 2; y++)
            {
                for (int z = -2; z <= 2; z++)
                {
                    BlockPos pos = playerPos.offset(x, y, z);
                    InkRailTileEntity host = InkRailUtils.getHost(player.level(), pos);
                    if (host == null || !host.isActive() || !ColorUtils.colorEquals(player.level(), host.getBlockPos(), host.getColor(), ColorUtils.getEntityColor(player)))
                        continue;

                    for (BlockPos link : InkRailUtils.getActiveLinks(player.level(), pos))
                    {
                        Vec3 start = InkRailUtils.getConnectionPoint(player.level(), pos);
                        Vec3 end = InkRailUtils.getConnectionPoint(player.level(), link);
                        Vec3 closest = InkRailUtils.closestPointOnSegment(player.position(), start, end);
                        double distance = closest.distanceToSqr(player.position());
                        if (distance > InkRailUtils.ENTER_DISTANCE * InkRailUtils.ENTER_DISTANCE)
                            continue;

                        double progress = InkRailUtils.segmentProgress(closest, start, end);
                        Vec3 lineDir = end.subtract(start).normalize();
                        double alignment = Math.abs(look.dot(lineDir));
                        boolean forward = look.dot(lineDir) >= 0.0D;
                        Candidate current = new Candidate(host.getBlockPos(), forward ? pos : link, forward ? link : pos, forward ? progress : 1.0D - progress, distance, alignment);
                        if (candidate == null || current.alignment > candidate.alignment + 1.0E-4D || (Math.abs(current.alignment - candidate.alignment) <= 1.0E-4D && current.distance < candidate.distance))
                            candidate = current;
                    }
                }
            }
        }

        if (candidate != null)
            attach(player, candidate.hostPos, candidate.fromPos, candidate.toPos, candidate.progress);
    }

    private static void attach(Player player, BlockPos hostPos, BlockPos fromPos, BlockPos toPos, double progress)
    {
        ACTIVE_RIDES.put(player.getUUID(), new RideData(player.level().dimension().location().toString(), hostPos, fromPos, toPos, progress));
        player.setNoGravity(true);
        player.noPhysics = true;
        player.fallDistance = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        updatePlayerState(player, true, false);
    }

    private static void tickRide(Player player, RideData ride)
    {
        if (!player.isAlive() || !PlayerInfoCapability.hasCapability(player) || !PlayerInfoCapability.get(player).isSquid() || !AbilityAccessUtils.canUseInkAbilities(player))
        {
            detach(player);
            return;
        }

        if (!ride.dimension.equals(player.level().dimension().location().toString()))
        {
            detach(player);
            return;
        }

        InkRailTileEntity host = InkRailUtils.getHost(player.level(), ride.hostPos);
        if (host == null || !host.isActive() || !ColorUtils.colorEquals(player.level(), host.getBlockPos(), host.getColor(), ColorUtils.getEntityColor(player)))
        {
            detach(player);
            return;
        }

        player.stopUsingItem();
        player.setNoGravity(true);
        player.noPhysics = true;
        player.fallDistance = 0.0F;

        Vec3 start = InkRailUtils.getConnectionPoint(player.level(), ride.fromPos);
        Vec3 end = InkRailUtils.getConnectionPoint(player.level(), ride.toPos);
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length <= 1.0E-4D)
        {
            detach(player);
            return;
        }

        if (ride.moveIntent != 0)
        {
            boolean switched = false;
            if (ride.progress <= NODE_SWITCH_EPSILON)
                switched = trySwitchSegment(player, ride, true);
            else if (ride.progress >= 1.0D - NODE_SWITCH_EPSILON)
                switched = trySwitchSegment(player, ride, false);

            if (switched)
            {
                start = InkRailUtils.getConnectionPoint(player.level(), ride.fromPos);
                end = InkRailUtils.getConnectionPoint(player.level(), ride.toPos);
                delta = end.subtract(start);
                length = delta.length();
                if (length <= 1.0E-4D)
                {
                    detach(player);
                    return;
                }
            }
        }

        double speedStep = InkRailUtils.RAIL_SPEED / length;
        int movementDirection = getMovementDirection(player, ride.moveIntent, delta.normalize());
        if (movementDirection > 0)
            ride.progress = Math.min(1.0D, ride.progress + speedStep);
        else if (movementDirection < 0)
            ride.progress = Math.max(0.0D, ride.progress - speedStep);

        if ((ride.progress <= 0.0D && movementDirection < 0) || (ride.progress >= 1.0D && movementDirection > 0))
            movementDirection = 0;

        Vec3 point = start.add(delta.scale(ride.progress));
        Vec3 velocity = movementDirection == 0 ? Vec3.ZERO : delta.normalize().scale(InkRailUtils.RAIL_SPEED * movementDirection);
        BlockPos occupiedPos = ride.progress >= 0.999D ? ride.toPos : ride.progress <= 0.001D ? ride.fromPos : null;
        setOccupiedPos(player.level(), ride, occupiedPos);
        movePlayer(player, point, velocity);
        updatePlayerState(player, true, occupiedPos != null);
    }

    private static boolean trySwitchSegment(Player player, RideData ride, boolean atStart)
    {
        BlockPos endpoint = atStart ? ride.fromPos : ride.toPos;
        BlockPos currentOther = atStart ? ride.toPos : ride.fromPos;
        Vec3 desired = getDesiredTravelDirection(player, ride.moveIntent);
        if (desired.lengthSqr() <= 1.0E-6D)
            return false;

        double currentAlignment = getLinkAlignment(player.level(), endpoint, currentOther, desired);
        double bestAlignment = currentAlignment;
        BlockPos bestLink = currentOther;

        for (BlockPos link : InkRailUtils.getActiveLinks(player.level(), endpoint))
        {
            double alignment = getLinkAlignment(player.level(), endpoint, link, desired);
            if (alignment > bestAlignment + 1.0E-4D)
            {
                bestAlignment = alignment;
                bestLink = link;
            }
        }

        if (!bestLink.equals(currentOther) && bestAlignment > currentAlignment + NODE_SWITCH_MARGIN)
        {
            ride.fromPos = endpoint;
            ride.toPos = bestLink;
            ride.progress = 0.0D;
            return true;
        }

        return false;
    }

    private static Vec3 getDesiredTravelDirection(Player player, int moveIntent)
    {
        if (moveIntent == 0)
            return Vec3.ZERO;

        Vec3 look = player.getLookAngle().normalize();
        return moveIntent > 0 ? look : look.scale(-1.0D);
    }

    private static double getLinkAlignment(Level level, BlockPos start, BlockPos end, Vec3 desired)
    {
        Vec3 direction = InkRailUtils.getConnectionPoint(level, end).subtract(InkRailUtils.getConnectionPoint(level, start));
        if (direction.lengthSqr() <= 1.0E-6D)
            return -1.0D;
        return desired.dot(direction.normalize());
    }

    private static int getMovementDirection(Player player, int moveIntent, Vec3 segmentDir)
    {
        if (moveIntent == 0)
            return 0;

        double lookDot = player.getLookAngle().normalize().dot(segmentDir);
        if (Math.abs(lookDot) < 0.15D)
            return moveIntent;

        return lookDot >= 0.0D ? moveIntent : -moveIntent;
    }

    private static void setOccupiedPos(Level level, RideData ride, BlockPos occupiedPos)
    {
        if (ride.occupiedPos != null && !ride.occupiedPos.equals(occupiedPos))
            InkRailUtils.setOccupied(level, ride.occupiedPos, false);
        if (occupiedPos != null && !occupiedPos.equals(ride.occupiedPos))
            InkRailUtils.setOccupied(level, occupiedPos, true);
        ride.occupiedPos = occupiedPos;
    }

    private static void movePlayer(Player player, Vec3 point, Vec3 velocity)
    {
        if (player instanceof ServerPlayer serverPlayer)
            serverPlayer.connection.teleport(point.x, point.y, point.z, player.getYRot(), player.getXRot(), RelativeMovement.ROTATION);
        else player.setPos(point.x, point.y, point.z);

        player.setOldPosAndRot();
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
    }

    public static void detach(Player player)
    {
        detach(player, Vec3.ZERO, false);
    }

    private static void detach(Player player, Vec3 exitVelocity, boolean preventReattach)
    {
        RideData removed = ACTIVE_RIDES.remove(player.getUUID());
        if (removed != null && removed.occupiedPos != null)
            InkRailUtils.setOccupied(player.level(), removed.occupiedPos, false);

        player.noPhysics = false;
        player.setNoGravity(false);
        if (preventReattach)
            DETACH_COOLDOWNS.put(player.getUUID(), player.level().getGameTime() + DETACH_COOLDOWN_TICKS);
        player.setDeltaMovement(exitVelocity);
        player.hurtMarked = true;
        updatePlayerState(player, false, false);
    }

    private static void updatePlayerState(Player player, boolean riding, boolean hidden)
    {
        if (!PlayerInfoCapability.hasCapability(player))
            return;

        PlayerInfo info = PlayerInfoCapability.get(player);
        boolean changed = info.isInkRailRiding() != riding || info.isInkRailHidden() != hidden;
        if (!changed)
            return;

        info.setInkRailRiding(riding);
        info.setInkRailHidden(hidden);

        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer)
            SplatcraftPacketHandler.sendToTrackersAndSelf(new UpdatePlayerInfoPacket(serverPlayer), serverPlayer);
    }

    private record Candidate(BlockPos hostPos, BlockPos fromPos, BlockPos toPos, double progress, double distance, double alignment)
    {
    }

    private static class RideData
    {
        private final String dimension;
        private final BlockPos hostPos;
        private BlockPos fromPos;
        private BlockPos toPos;
        private double progress;
        private int moveIntent;
        private BlockPos occupiedPos;

        private RideData(String dimension, BlockPos hostPos, BlockPos fromPos, BlockPos toPos, double progress)
        {
            this.dimension = dimension;
            this.hostPos = hostPos;
            this.fromPos = fromPos;
            this.toPos = toPos;
            this.progress = progress;
            this.moveIntent = 0;
            this.occupiedPos = progress >= 0.999D ? toPos : progress <= 0.001D ? fromPos : null;
        }
    }
}
