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
import net.minecraft.world.phys.Vec3;
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
import net.splatcraft.forge.data.match.ZonesData;
import net.splatcraft.forge.network.SplatcraftPacketHandler;
import net.splatcraft.forge.network.s2c.SyncMatchStatePacket;
import net.splatcraft.forge.network.s2c.MatchResultPacket;
import net.splatcraft.forge.network.s2c.SyncZonesStatePacket;
import net.splatcraft.forge.util.InkBlockUtils;

import java.util.*;

@Mod.EventBusSubscriber(modid = Splatcraft.MODID)
public class MatchHandler
{
    private static final Map<UUID, Match> ACTIVE_MATCHES = new HashMap<>();
    private static final Map<UUID, UUID> PLAYER_TO_MATCH = new HashMap<>();
    private static final Map<String, UUID> STAGE_TO_MATCH = new HashMap<>();

    public static void addMatch(Match match)
    {
        ACTIVE_MATCHES.put(match.id, match);
        STAGE_TO_MATCH.put(match.stageName, match.id);
        for (UUID playerUUID : match.getPlayerUUIDs())
        {
            PLAYER_TO_MATCH.put(playerUUID, match.id);
        }
    }

    public static Match getMatch(UUID matchId)
    {
        return ACTIVE_MATCHES.get(matchId);
    }

    public static Match getMatchByStage(String stageName)
    {
        UUID matchId = STAGE_TO_MATCH.get(stageName);
        return matchId != null ? ACTIVE_MATCHES.get(matchId) : null;
    }

    public static Set<String> getActiveStages()
    {
        return STAGE_TO_MATCH.keySet();
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

        STAGE_TO_MATCH.remove(match.stageName);

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

        if (match.type == MatchType.TURF)
        {
            if (gameTime % 20 == (Math.abs(match.id.hashCode()) % 20))
            {
                scanTurf(match, server);
            }
        }
        else if (match.type == MatchType.ZONES)
        {
            if (gameTime % 10 == (Math.abs(match.id.hashCode()) % 10))
            {
                scanZones(match, server);
                syncZones(match, server);
            }
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
            if (match.type == MatchType.ZONES && !match.overtimeActive)
            {
                if (tryStartOvertime(match, server))
                {
                    match.remainingTimeTicks = 0;
                    syncZones(match, server);
                    syncMatch(match, server);
                    return;
                }
            }

            if (!match.overtimeActive)
            {
                match.phase = MatchPhase.FINISHED;

                if (match.type == MatchType.TURF)
                    scanTurf(match, server);

                sendTitleToMatch(match, "\u00a7c\u00a7lGAME!", null, 5, 40, 10);
                return;
            }
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

        // Step 1: teleport to overview (tick 1)
        if (match.finishedTicks == 1)
        {
            Stage stage = match.getStage(server);
            if (stage != null)
            {
                Vec3 center = match.getCenterPos(stage);
                for (UUID uuid : match.getPlayerUUIDs())
                {
                    ServerPlayer player = match.getPlayer(uuid);
                    if (player != null)
                    {
                        player.setGameMode(GameType.SPECTATOR);
                        player.connection.teleport(center.x, center.y, center.z, 0, 90);
                    }
                }
            }
        }

        // Lock camera downward during overview
        if (match.finishedTicks >= 1 && match.finishedTicks < 140)
        {
            for (UUID uuid : match.getPlayerUUIDs())
            {
                ServerPlayer player = match.getPlayer(uuid);
                if (player != null)
                    player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), 90);
            }
        }

        // Step 2: after 3-second pause (60 ticks), send result packet to start animation
        if (match.finishedTicks == 61 && !match.resultPacketSent)
        {
            match.resultPacketSent = true;
            String winner = match.getWinnerTeam();
            Stage stage = match.getStage(server);
            int teamCount = match.getTeamNames().size();
            float[] pcts = new float[teamCount];
            String[] names = new String[teamCount];
            int[] colors = new int[teamCount];
            int i = 0;

            boolean knockout = false;
            if (match.type == MatchType.ZONES)
            {
                for (String team : match.getTeamNames())
                {
                    if (match.zoneTimers.getOrDefault(team, 100) <= 0)
                    {
                        knockout = true;
                        break;
                    }
                }
            }

            for (String team : match.getTeamNames())
            {
                if (match.type == MatchType.ZONES)
                {
                    int timer = match.zoneTimers.getOrDefault(team, 100);
                    if (knockout && timer > 0)
                        pcts[i] = 0;
                    else
                        pcts[i] = 100 - timer;
                }
                else
                {
                    pcts[i] = match.teamPercentages.getOrDefault(team, 0.0F);
                }
                names[i] = team;
                colors[i] = stage != null ? stage.getTeamColor(team) : 0;
                i++;
            }

            MatchResultPacket resultPacket = new MatchResultPacket(match.id, winner, pcts, names, colors, knockout);
            for (UUID uuid : match.getPlayerUUIDs())
            {
                ServerPlayer player = match.getPlayer(uuid);
                if (player != null)
                    SplatcraftPacketHandler.sendToPlayer(resultPacket, player);
            }
        }

        if (match.finishedTicks >= 200)
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
            return;

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
            event.player.setDeltaMovement(Vec3.ZERO);
            event.player.xxa = 0;
            event.player.zza = 0;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerInteract(PlayerInteractEvent event)
    {
        if (isPlayerFrozen(event.getEntity()) && event.isCancelable())
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
            event.getEntity().setDeltaMovement(Vec3.ZERO);
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

    private static void scanZones(Match match, MinecraftServer server)
    {
        Stage stage = match.getStage(server);
        if (stage == null) return;

        java.util.List<ZonesData> zones = stage.getZones();
        if (zones.isEmpty()) return;

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
        if (level == null) return;

        java.util.Collection<String> teams = match.getTeamNames();
        String[] teamArr = teams.toArray(new String[0]);
        int teamCount = teamArr.length;

        if (teamCount == 0) return;

        int zoneCount = zones.size();
        match.zoneTeamPcts = new int[zoneCount][teamCount];

        int[] oldZoneControllers = match.zoneControllers;
        if (oldZoneControllers.length != zoneCount)
        {
            oldZoneControllers = new int[zoneCount];
            for (int i = 0; i < zoneCount; i++)
                oldZoneControllers[i] = -1;
        }

        int[] newZoneControllers = new int[zoneCount];
        for (int i = 0; i < zoneCount; i++) newZoneControllers[i] = -1;

        for (int zi = 0; zi < zoneCount; zi++)
        {
            ZonesData zone = zones.get(zi);
            int[] teamBlocks = new int[teamCount];
            int totalBlocks = 0;

            for (int x = zone.min.getX(); x <= zone.max.getX(); x++)
            {
                for (int z = zone.min.getZ(); z <= zone.max.getZ(); z++)
                {
                    BlockPos pos = getTopSolidBlock(level, x, z, zone.min.getY(), zone.max.getY());
                    if (pos.getY() > zone.max.getY())
                        continue;

                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                    if (!state.blocksMotion() || !state.getFluidState().isEmpty()
                        || InkBlockUtils.isUninkable(level, pos))
                        continue;

                    totalBlocks++;

                    net.splatcraft.forge.data.capabilities.worldink.WorldInk worldInk =
                        net.splatcraft.forge.data.capabilities.worldink.WorldInkCapability.get(level, pos);
                    int inkColor = -1;
                    if (worldInk.isInked(pos))
                        inkColor = worldInk.getInk(pos).color();

                    if (inkColor >= 0)
                    {
                        for (int ti = 0; ti < teamCount; ti++)
                        {
                            int teamColor = stage.getTeamColor(teamArr[ti]);
                            if (inkColor == teamColor)
                            {
                                teamBlocks[ti]++;
                                break;
                            }
                        }
                    }
                }
            }

            for (int ti = 0; ti < teamCount; ti++)
            {
                match.zoneTeamPcts[zi][ti] = totalBlocks > 0
                    ? teamBlocks[ti] * 100 / totalBlocks : 0;
            }

            int captor = -1;
            for (int ti = 0; ti < teamCount; ti++)
            {
                if (match.zoneTeamPcts[zi][ti] > 70)
                {
                    if (captor == -1)
                        captor = ti;
                    else
                    {
                        captor = -2;
                        break;
                    }
                }
            }

            if (captor >= 0)
            {
                newZoneControllers[zi] = captor;
            }
            else if (captor == -2)
            {
                newZoneControllers[zi] = -1;
            }
            else
            {
                int prev = zi < oldZoneControllers.length ? oldZoneControllers[zi] : -1;
                if (prev >= 0 && prev < teamCount && match.zoneTeamPcts[zi][prev] > 50)
                    newZoneControllers[zi] = prev;
                else
                    newZoneControllers[zi] = -1;
            }
        }
        match.zoneControllers = newZoneControllers;

        for (int ti = 0; ti < teamCount; ti++)
        {
            float totalPct = 0;
            for (int zi = 0; zi < zoneCount; zi++)
                totalPct += match.zoneTeamPcts[zi][ti];
            match.teamPercentages.put(teamArr[ti], zones.size() > 0 ? totalPct / zones.size() : 0);
        }

        String oldController = match.controllingTeam;
        String newController = null;

        boolean allSame = true;
        for (int zi = 0; zi < zoneCount; zi++)
        {
            if (newZoneControllers[zi] < 0) { allSame = false; break; }
            String tn = teamArr[newZoneControllers[zi]];
            if (newController == null)
                newController = tn;
            else if (!newController.equals(tn))
            { allSame = false; break; }
        }
        match.controllingTeam = allSame ? newController : null;

        if (match.controllingTeam != null && !match.controllingTeam.equals(oldController))
        {
            match.teamControlStartTimers.put(match.controllingTeam,
                match.zoneTimers.getOrDefault(match.controllingTeam, 100)
                + match.zonePenalties.getOrDefault(match.controllingTeam, 0));

            String penaltyTarget = oldController != null ? oldController : match.lastControllingTeam;
            if (penaltyTarget != null && !penaltyTarget.equals(match.controllingTeam) && !match.overtimeActive)
            {
                int start = match.teamControlStartTimers.getOrDefault(penaltyTarget, 100);
                int end = match.zoneTimers.getOrDefault(penaltyTarget, 100)
                    + match.zonePenalties.getOrDefault(penaltyTarget, 0);
                int penalty = (int) Math.round(0.75 * (start - end));
                if (start == 100) penalty += 1;

                if (penalty > 0)
                {
                    match.zonePenalties.merge(penaltyTarget, penalty, Integer::sum);
                    match.teamControlStartTimers.put(penaltyTarget,
                        match.zoneTimers.getOrDefault(penaltyTarget, 100)
                        + match.zonePenalties.getOrDefault(penaltyTarget, 0));
                }
            }

            match.lastControlLossTick = server.getTickCount();

            int ctrlColor = stage.getTeamColor(match.controllingTeam);
            if (ctrlColor >= 0)
            {
                for (ZonesData zone : zones)
                {
                    for (int x = zone.min.getX(); x <= zone.max.getX(); x++)
                        for (int y = zone.min.getY(); y <= zone.max.getY(); y++)
                            for (int z = zone.min.getZ(); z <= zone.max.getZ(); z++)
                            {
                                BlockPos pos = new BlockPos(x, y, z);
                                InkBlockUtils.inkBlock(level, pos, ctrlColor, 0, InkBlockUtils.InkType.NORMAL);
                            }
                }
            }

            match.lastControllingTeam = match.controllingTeam;
        }

        if (match.overtimeActive)
        {
            String losing = match.overtimeLosingTeam;
            String winning = match.overtimeWinningTeam;

            if (losing != null && winning != null)
            {
                int losingTimer = match.zoneTimers.getOrDefault(losing, 100);
                int winningTimer = match.zoneTimers.getOrDefault(winning, 100);
                if (losingTimer < winningTimer)
                {
                    match.phase = MatchPhase.FINISHED;
                    match.finishedTicks = 0;
                    sendTitleToMatch(match, "\u00a7c\u00a7lGAME!", null, 5, 40, 10);
                    return;
                }
            }

            if (match.controllingTeam != null && match.controllingTeam.equals(winning))
            {
                match.phase = MatchPhase.FINISHED;
                match.finishedTicks = 0;
                sendTitleToMatch(match, "\u00a7c\u00a7lGAME!", null, 5, 40, 10);
                return;
            }

            boolean losingControlsAll = match.controllingTeam != null
                && match.controllingTeam.equals(losing);

            if (losingControlsAll)
            {
                match.overtimeDrainTicks = -1;
            }
            else
            {
                if (match.overtimeDrainTicks < 0)
                    match.overtimeDrainTicks = 20;
                else
                    match.overtimeDrainTicks--;

                if (match.overtimeDrainTicks <= 0)
                {
                    match.phase = MatchPhase.FINISHED;
                    match.finishedTicks = 0;
                    sendTitleToMatch(match, "\u00a7c\u00a7lGAME!", null, 5, 40, 10);
                    return;
                }
            }
        }

        if (match.controllingTeam != null)
        {
            int penalty = match.zonePenalties.getOrDefault(match.controllingTeam, 0);
            if (penalty > 0)
            {
                match.zonePenalties.put(match.controllingTeam, penalty - 1);
            }
            else
            {
                int timer = match.zoneTimers.getOrDefault(match.controllingTeam, 100);
                if (timer > 0)
                {
                    match.zoneTimers.put(match.controllingTeam, timer - 1);
                    if (timer - 1 <= 0)
                    {
                        match.phase = MatchPhase.FINISHED;
                        match.finishedTicks = 0;
                        sendTitleToMatch(match, "\u00a7c\u00a7lGAME!", null, 5, 40, 10);
                    }
                }
            }
        }

        sendControlMessages(match, server, teamArr, oldController);
    }

    private static void sendControlMessages(Match match, MinecraftServer server, String[] teamArr, String oldController)
    {
        String currentController = match.controllingTeam;
        String oldCtrl = oldController;
        boolean controlChanged = !java.util.Objects.equals(currentController, oldCtrl);

        String currentLeader = null;
        int bestTimer = Integer.MAX_VALUE;
        boolean allEqual = true;
        for (int i = 0; i < teamArr.length; i++)
        {
            int timer = match.zoneTimers.getOrDefault(teamArr[i], 100);
            if (timer < bestTimer) { bestTimer = timer; currentLeader = teamArr[i]; }
            if (i > 0 && timer != match.zoneTimers.getOrDefault(teamArr[0], 100)) allEqual = false;
        }
        if (allEqual && teamArr.length >= 2) currentLeader = null;

        String oldLeader = match.previousLeader;
        boolean leaderChanged = currentLeader != null && !currentLeader.equals(oldLeader);

        for (UUID uuid : match.getPlayerUUIDs())
        {
            ServerPlayer player = match.getPlayer(uuid);
            if (player == null) continue;
            String playerTeam = match.getPlayerTeam(uuid);

            if (controlChanged)
            {
                if (currentController != null && currentController.equals(playerTeam))
                {
                    sendSubtitle(player, Component.translatable("status.zones.we_control").withStyle(net.minecraft.ChatFormatting.GREEN));
                    player.playNotifySound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.2F);
                }
                else if (oldCtrl != null && oldCtrl.equals(playerTeam))
                {
                    sendSubtitle(player, Component.translatable("status.zones.we_lost_control").withStyle(net.minecraft.ChatFormatting.RED));
                    player.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 0.6F);
                }
                else if (currentController != null)
                {
                    sendSubtitle(player, Component.translatable("status.zones.they_control").withStyle(net.minecraft.ChatFormatting.RED));
                }
                else if (oldCtrl != null)
                {
                    sendSubtitle(player, Component.translatable("status.zones.they_lost_control").withStyle(net.minecraft.ChatFormatting.GREEN));
                }
            }

            if (leaderChanged)
            {
                if (currentLeader.equals(playerTeam))
                {
                    sendSubtitle(player, Component.translatable("status.zones.we_lead").withStyle(net.minecraft.ChatFormatting.GOLD));
                    player.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.5F);
                }
                else if (oldLeader != null && oldLeader.equals(playerTeam))
                {
                    sendSubtitle(player, Component.translatable("status.zones.we_lost_lead").withStyle(net.minecraft.ChatFormatting.RED));
                    player.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 0.6F);
                }
            }
        }

        match.previousLeader = currentLeader;
    }

    private static void sendSubtitle(ServerPlayer player, Component msg)
    {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.empty()));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(msg));
    }

    private static boolean tryStartOvertime(Match match, MinecraftServer server)
    {
        java.util.Collection<String> teams = match.getTeamNames();
        String[] teamArr = teams.toArray(new String[0]);
        if (teamArr.length < 2) return false;

        String losingTeam = null;
        String winningTeam = null;
        int losingTimer = -1;
        int winningTimer = Integer.MAX_VALUE;
        for (String team : teamArr)
        {
            int timer = match.zoneTimers.getOrDefault(team, 100);
            if (timer > losingTimer)
            {
                losingTimer = timer;
                losingTeam = team;
            }
            if (timer < winningTimer)
            {
                winningTimer = timer;
                winningTeam = team;
            }
        }

        if (losingTeam == null || winningTeam == null) return false;

        boolean losingControls = match.controllingTeam != null
            && match.controllingTeam.equals(losingTeam);

        long ticksSinceLoss = server.getTickCount() - match.lastControlLossTick;
        int scansSinceLoss = (int) (ticksSinceLoss / 10);
        boolean lostRecently = scansSinceLoss < 20
            && (match.controllingTeam == null || !match.controllingTeam.equals(winningTeam));

        if (losingControls || lostRecently)
        {
            match.overtimeActive = true;
            match.overtimeLosingTeam = losingTeam;
            match.overtimeWinningTeam = winningTeam;
            match.overtimeDrainTicks = losingControls ? -1 : (20 - scansSinceLoss);
            return true;
        }

        return false;
    }

    private static void syncZones(Match match, MinecraftServer server)
    {
        java.util.Collection<String> teams = match.getTeamNames();
        String[] teamArr = teams.toArray(new String[0]);
        int teamCount = teamArr.length;

        int[] timers = new int[teamCount];
        int[] penalties = new int[teamCount];
        int[] colors = new int[teamCount];

        Stage stage = match.getStage(server);
        for (int i = 0; i < teamCount; i++)
        {
            timers[i] = match.zoneTimers.getOrDefault(teamArr[i], 100);
            penalties[i] = match.zonePenalties.getOrDefault(teamArr[i], 0);
            colors[i] = stage != null ? stage.getTeamColor(teamArr[i]) : 0;
        }

        int ctrlIdx = -1;
        if (match.controllingTeam != null)
        {
            for (int i = 0; i < teamCount; i++)
            {
                if (teamArr[i].equals(match.controllingTeam))
                {
                    ctrlIdx = i;
                    break;
                }
            }
        }

        java.util.List<ZonesData> zones = stage != null ? stage.getZones() : java.util.Collections.emptyList();
        BlockPos[] mins = new BlockPos[zones.size()];
        BlockPos[] maxs = new BlockPos[zones.size()];
        int[][] pcts = match.zoneTeamPcts;
        if (pcts.length != zones.size()) pcts = new int[zones.size()][teamCount];

        for (int i = 0; i < zones.size(); i++)
        {
            mins[i] = zones.get(i).min;
            maxs[i] = zones.get(i).max;
        }

        SyncZonesStatePacket packet = new SyncZonesStatePacket(match.id, teamArr, colors,
            timers, penalties, ctrlIdx, mins, maxs, pcts,
            match.overtimeActive, match.overtimeDrainTicks);

        for (UUID uuid : match.getPlayerUUIDs())
        {
            ServerPlayer player = match.getPlayer(uuid);
            if (player != null)
                SplatcraftPacketHandler.sendToPlayer(packet, player);
        }
    }
}
