package net.splatcraft.forge.handlers;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.blocks.IColoredBlock;
import net.splatcraft.forge.data.SplatcraftTags;
import net.splatcraft.forge.data.Stage;
import net.splatcraft.forge.data.capabilities.saveinfo.SaveInfoCapability;
import net.splatcraft.forge.data.capabilities.worldink.WorldInk;
import net.splatcraft.forge.data.capabilities.worldink.WorldInkCapability;
import net.splatcraft.forge.data.match.Match;
import net.splatcraft.forge.data.match.MatchPhase;
import net.splatcraft.forge.data.match.MatchType;
import net.splatcraft.forge.network.SplatcraftPacketHandler;
import net.splatcraft.forge.network.s2c.SyncMatchStatePacket;
import net.splatcraft.forge.network.s2c.MatchResultPacket;
import net.splatcraft.forge.util.InkBlockUtils;

import java.util.*;

@Mod.EventBusSubscriber(modid = Splatcraft.MODID)
public class MatchHandler
{
    private static final Map<UUID, Match> ACTIVE_MATCHES = new HashMap<>();
    private static final Map<UUID, UUID> PLAYER_TO_MATCH = new HashMap<>();

    public static void addMatch(Match match)
    {
        ACTIVE_MATCHES.put(match.id, match);
        for (UUID playerUUID : match.getPlayerUUIDs())
        {
            PLAYER_TO_MATCH.put(playerUUID, match.id);
        }
    }

    public static Match getMatch(UUID matchId)
    {
        return ACTIVE_MATCHES.get(matchId);
    }

    public static Match getPlayerMatch(Player player)
    {
        UUID matchId = PLAYER_TO_MATCH.get(player.getUUID());
        if (matchId == null) return null;
        return ACTIVE_MATCHES.get(matchId);
    }

    public static UUID getPlayerMatchId(Player player)
    {
        return PLAYER_TO_MATCH.get(player.getUUID());
    }

    public static boolean isPlayerFrozen(Player player)
    {
        Match match = getPlayerMatch(player);
        if (match == null) return false;
        return match.phase == MatchPhase.COUNTDOWN || match.phase == MatchPhase.FINISHED;
    }

    public static void stopMatch(UUID matchId)
    {
        Match match = ACTIVE_MATCHES.remove(matchId);
        if (match == null) return;

        SyncMatchStatePacket clearPacket = SyncMatchStatePacket.createClearPacket();
        for (UUID uuid : match.getPlayerUUIDs())
        {
            PLAYER_TO_MATCH.remove(uuid);
            ServerPlayer player = match.getPlayer(uuid);
            if (player != null)
            {
                player.setGameMode(GameType.SURVIVAL);
                BlockPos spawn = player.level().getSharedSpawnPos();
                player.teleportTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
                SplatcraftPacketHandler.sendToPlayer(clearPacket, player);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (Match match : new ArrayList<>(ACTIVE_MATCHES.values()))
        {
            match.refreshPlayerCache(server);
            match.updatePlayerStates();

            switch (match.phase)
            {
                case COUNTDOWN:
                    tickCountdown(match, server);
                    break;
                case PLAYING:
                    tickPlaying(match, server);
                    break;
                case FINISHED:
                    tickFinished(match, server);
                    break;
            }
        }
    }

    private static void tickCountdown(Match match, MinecraftServer server)
    {
        match.countdownTicks--;

        if (match.countdownTicks == 59)
        {
            sendTitleToMatch(match, "Ready!", null, 5, 20, 5);
        }
        else if (match.countdownTicks == 39)
        {
            sendTitleToMatch(match, "Set!", null, 0, 20, 10);
        }
        else if (match.countdownTicks == 19)
        {
            sendTitleToMatch(match, "GO!", null, 0, 15, 5);
            match.phase = MatchPhase.PLAYING;
        }

        syncMatch(match, server);
    }

    private static void tickPlaying(Match match, MinecraftServer server)
    {
        match.remainingTimeTicks--;

        long gameTime = server.getTickCount();

        if (gameTime % 20 == (Math.abs(match.id.hashCode()) % 20))
        {
            scanTurf(match, server);
        }

        if (match.remainingTimeTicks == 20 * 60)
        {
            sendTitleToMatch(match, "\u00a7e1 minute remaining!", null, 10, 30, 10);
        }

        if (match.remainingTimeTicks > 0 && match.remainingTimeTicks <= 20 * 10)
        {
            int secs = (match.remainingTimeTicks + 19) / 20;
            sendActionBarToMatch(match, "\u00a7e" + secs);
        }

        if (match.remainingTimeTicks <= 0)
        {
            match.phase = MatchPhase.FINISHED;
            match.finishedTicks = 0;

            scanTurf(match, server);

            String winner = match.getWinnerTeam();
            int teamCount = match.getTeamNames().size();
            float[] pcts = new float[teamCount];
            String[] names = new String[teamCount];
            int[] colors = new int[teamCount];
            int i = 0;
            Stage stage = match.getStage(server);
            for (String team : match.getTeamNames())
            {
                pcts[i] = match.teamPercentages.getOrDefault(team, 0.0F);
                names[i] = team;
                colors[i] = stage != null ? stage.getTeamColor(team) : 0;
                i++;
            }
            MatchResultPacket resultPacket = new MatchResultPacket(match.id, winner, pcts, names, colors);
            for (UUID uuid : match.getPlayerUUIDs())
            {
                ServerPlayer player = match.getPlayer(uuid);
                if (player != null)
                    SplatcraftPacketHandler.sendToPlayer(resultPacket, player);
            }

            sendTitleToMatch(match, "\u00a7c\u00a7lGAME!", null, 5, 40, 10);
            return;
        }

        syncMatch(match, server);
    }

    private static void tickFinished(Match match, MinecraftServer server)
    {
        match.refreshPlayerCache(server);

        for (UUID uuid : match.getPlayerUUIDs())
        {
            ServerPlayer player = match.getPlayer(uuid);
            if (player != null && player.isDeadOrDying())
                return;
        }

        match.finishedTicks++;

        if (match.finishedTicks == 30)
        {
            Stage stage = match.getStage(server);
            if (stage != null)
            {
                BlockPos center = match.getCenterPos(stage);
                for (UUID uuid : match.getPlayerUUIDs())
                {
                    ServerPlayer player = match.getPlayer(uuid);
                    if (player != null)
                    {
                        player.setGameMode(GameType.SPECTATOR);
                        player.teleportTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
                    }
                }
            }
        }

        if (match.finishedTicks >= 140)
        {
            stopMatch(match.id);
        }
    }

    private static void scanTurf(Match match, MinecraftServer server)
    {
        Stage stage = match.getStage(server);
        if (stage == null) return;

        Level level = null;
        net.minecraft.resources.ResourceKey<Level> dimKey =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, stage.dimID);
        if (server != null)
            level = server.getLevel(dimKey);

        if (level == null)
        {
            for (UUID uuid : match.getPlayerUUIDs())
            {
                ServerPlayer player = match.getPlayer(uuid);
                if (player != null) { level = player.level(); break; }
            }
        }
        if (level == null)
        {
            for (UUID uuid : match.getPlayerUUIDs())
            {
                ServerPlayer p = match.getPlayer(uuid);
                if (p != null)
                    p.displayClientMessage(Component.literal("\u00a7c[Match] Could not find stage level!"), false);
            }
            return;
        }

        BlockPos minPos = new BlockPos(
            Math.min(stage.cornerA.getX(), stage.cornerB.getX()),
            Math.min(stage.cornerA.getY(), stage.cornerB.getY()),
            Math.min(stage.cornerA.getZ(), stage.cornerB.getZ())
        );
        BlockPos maxPos = new BlockPos(
            Math.max(stage.cornerA.getX(), stage.cornerB.getX()),
            Math.max(stage.cornerA.getY(), stage.cornerB.getY()),
            Math.max(stage.cornerA.getZ(), stage.cornerB.getZ())
        );

        Map<Integer, Integer> colorScores = new TreeMap<>();
        int blockTotal = 0;

        for (int x = minPos.getX(); x <= maxPos.getX(); x++)
        {
            for (int z = minPos.getZ(); z <= maxPos.getZ(); z++)
            {
                BlockPos checkPos = getTopSolidBlock(level, x, z, minPos.getY(), maxPos.getY());
                if (checkPos.getY() > maxPos.getY())
                    continue;

                BlockState checkState = level.getBlockState(checkPos);
                if (!checkState.blocksMotion() || !checkState.getFluidState().isEmpty() || InkBlockUtils.isUninkable(level, checkPos))
                    continue;

                blockTotal++;

                int inkColor = -1;
                WorldInk worldInk = WorldInkCapability.get(level, checkPos);
                if (worldInk.isInked(checkPos))
                    inkColor = worldInk.getInk(checkPos).color();
                else if (level.getBlockState(checkPos).is(SplatcraftTags.Blocks.SCAN_TURF_SCORED)
                    && level.getBlockState(checkPos).getBlock() instanceof IColoredBlock coloredBlock)
                    inkColor = coloredBlock.getColor(level, checkPos);

                if (inkColor >= 0)
                {
                    colorScores.merge(inkColor, 1, Integer::sum);
                }
            }
        }

        match.totalBlocks = blockTotal;

        for (String teamName : match.getTeamNames())
        {
            int teamColor = stage.getTeamColor(teamName);
            int score = colorScores.getOrDefault(teamColor, 0);
            match.teamScores.put(teamName, score);
            float pct = blockTotal > 0 ? score / (float) blockTotal * 100.0F : 0.0F;
            match.teamPercentages.put(teamName, pct);
        }

        // Debug broadcast
        if (match.phase == MatchPhase.FINISHED)
        {
            for (UUID uuid : match.getPlayerUUIDs())
            {
                ServerPlayer p = match.getPlayer(uuid);
                if (p != null)
                {
                    p.displayClientMessage(Component.literal(
                        "\u00a7e[Turf Scan]\u00a7r blocks=" + blockTotal +
                        " colors=" + colorScores), false);
                }
            }
        }
    }

    private static BlockPos getTopSolidBlock(Level level, int x, int z, int minY, int maxY)
    {
        for (BlockPos pos = new BlockPos(x, maxY, z); pos.getY() >= minY; pos = pos.below())
        {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.blocksMotion() && state.getFluidState().isEmpty() && !InkBlockUtils.isUninkable(level, pos))
                return pos;
        }
        return new BlockPos(x, maxY + 1, z);
    }

    private static void syncMatch(Match match, MinecraftServer server)
    {
        SyncMatchStatePacket packet = new SyncMatchStatePacket(match, server);
        for (UUID uuid : match.getPlayerUUIDs())
        {
            ServerPlayer player = match.getPlayer(uuid);
            if (player != null)
                SplatcraftPacketHandler.sendToPlayer(packet, player);
        }
    }

    private static void sendTitleToMatch(Match match, String title, String subtitle, int fadeIn, int stay, int fadeOut)
    {
        ClientboundSetTitlesAnimationPacket animPacket = new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut);
        ClientboundSetTitleTextPacket titlePacket = new ClientboundSetTitleTextPacket(Component.literal(title));
        ClientboundSetSubtitleTextPacket subtitlePacket = subtitle != null ?
            new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)) : null;

        for (UUID uuid : match.getPlayerUUIDs())
        {
            ServerPlayer player = match.getPlayer(uuid);
            if (player != null)
            {
                player.connection.send(animPacket);
                player.connection.send(titlePacket);
                if (subtitlePacket != null)
                    player.connection.send(subtitlePacket);
            }
        }
    }

    private static void sendActionBarToMatch(Match match, String text)
    {
        Component msg = Component.literal(text);
        for (UUID uuid : match.getPlayerUUIDs())
        {
            ServerPlayer player = match.getPlayer(uuid);
            if (player != null)
                player.displayClientMessage(msg, true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START || event.side.isClient()) return;

        Match match = getPlayerMatch(event.player);
        if (match == null) return;

        if (match.phase == MatchPhase.COUNTDOWN || match.phase == MatchPhase.FINISHED)
        {
            event.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            event.player.xxa = 0;
            event.player.zza = 0;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerInteract(PlayerInteractEvent event)
    {
        if (isPlayerFrozen(event.getEntity()))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event)
    {
        if (isPlayerFrozen(event.getEntity()))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingJump(LivingEvent.LivingJumpEvent event)
    {
        if (event.getEntity() instanceof Player player && isPlayerFrozen(player))
            event.getEntity().setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event)
    {
        if (event.getEntity() instanceof Player player && isPlayerFrozen(player))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItemTick(LivingEntityUseItemEvent.Tick event)
    {
        if (event.getEntity() instanceof Player player && isPlayerFrozen(player))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        UUID matchId = PLAYER_TO_MATCH.remove(event.getEntity().getUUID());
        if (matchId != null)
        {
            Match match = ACTIVE_MATCHES.get(matchId);
            if (match != null)
            {
                match.removePlayer(event.getEntity().getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Match match = getPlayerMatch(player);
        if (match != null)
        {
            match.playerAlive.put(player.getUUID(), false);
            match.playerSpecialReady.put(player.getUUID(), false);

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null)
                syncMatch(match, server);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Match match = getPlayerMatch(player);
        if (match != null)
        {
            match.playerAlive.put(player.getUUID(), true);

            // keep them in the match's stage
            Stage stage = match.getStage(player.getServer());
            if (stage != null && player.getServer() != null)
            {
                net.minecraft.resources.ResourceKey<Level> dimKey =
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, stage.dimID);
                // respawn happens via vanilla, we don't override location
            }
        }
    }
}
