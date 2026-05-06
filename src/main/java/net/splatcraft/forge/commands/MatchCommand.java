package net.splatcraft.forge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.splatcraft.forge.blocks.SpawnPadBlock;
import net.splatcraft.forge.tileentities.SpawnPadTileEntity;
import net.minecraft.world.level.Level;
import net.splatcraft.forge.data.Stage;
import net.splatcraft.forge.data.capabilities.saveinfo.SaveInfoCapability;
import net.splatcraft.forge.data.match.Match;
import net.splatcraft.forge.data.match.MatchType;
import net.splatcraft.forge.handlers.MatchHandler;
import net.splatcraft.forge.util.ClientUtils;
import net.splatcraft.forge.util.ColorUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MatchCommand
{
    public static final DynamicCommandExceptionType STAGE_NOT_FOUND = new DynamicCommandExceptionType(p ->
        Component.translatable("commands.match.stage_not_found", p));
    public static final DynamicCommandExceptionType NOT_ENOUGH_TEAMS = new DynamicCommandExceptionType(p ->
        Component.translatable("commands.match.not_enough_teams", p));
    public static final DynamicCommandExceptionType NO_PLAYERS = new DynamicCommandExceptionType(p ->
        Component.translatable("commands.match.no_players", p));
    public static final DynamicCommandExceptionType MATCH_NOT_FOUND = new DynamicCommandExceptionType(p ->
        Component.translatable("commands.match.not_found", p));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("match").requires(cs -> cs.hasPermission(2))
            .then(Commands.literal("start")
                .then(Commands.argument("stage", StringArgumentType.word()).suggests(MatchCommand::suggestStages)
                    .then(Commands.argument("time", IntegerArgumentType.integer(30, 3600))
                        .then(Commands.literal("turf").executes(MatchCommand::startMatch))
                    )
                )
            )
            .then(Commands.literal("stop")
                .then(Commands.argument("stage", StringArgumentType.word())
                    .suggests((ctx, builder) -> { MatchHandler.getActiveStages().forEach(builder::suggest); return builder.buildFuture(); })
                    .executes(MatchCommand::stopMatchCmd))
            )
        );
    }

    private static int startMatch(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        String stageName = StringArgumentType.getString(context, "stage");
        int time = IntegerArgumentType.getInteger(context, "time");

        HashMap<String, Stage> stages = SaveInfoCapability.get(source.getServer()).getStages();
        if (!stages.containsKey(stageName))
            throw STAGE_NOT_FOUND.create(stageName);

        Stage stage = stages.get(stageName);

        if (stage.getTeamIds().size() < 2)
            throw NOT_ENOUGH_TEAMS.create(stageName);

        // Find all players in the stage bounds
        List<ServerPlayer> allPlayers = source.getLevel().players();
        List<ServerPlayer> matchPlayers = new ArrayList<>();
        for (ServerPlayer player : allPlayers)
        {
            // Skip players already in a match
            if (MatchHandler.getPlayerMatchId(player) != null) continue;

            if (isPlayerInStage(player, stage))
                matchPlayers.add(player);
        }

        if (matchPlayers.isEmpty())
            throw NO_PLAYERS.create(stageName);

        UUID matchId = UUID.randomUUID();
        Match match = new Match(matchId, stageName, MatchType.TURF, time);

        // Auto-assign players to teams by ink color
        Map<String, Integer> teamColorMap = new HashMap<>();
        for (String teamName : stage.getTeamIds())
        {
            int color = stage.getTeamColor(teamName);
            teamColorMap.put(teamName, color);
            match.teamScores.put(teamName, 0);
            match.teamPercentages.put(teamName, 0.0F);
        }

        for (ServerPlayer player : matchPlayers)
        {
            int playerColor = ColorUtils.getPlayerColor(player);
            String assignedTeam = null;

            // Try to match player color to a team color
            for (Map.Entry<String, Integer> entry : teamColorMap.entrySet())
            {
                if (entry.getValue() == playerColor)
                {
                    assignedTeam = entry.getKey();
                    break;
                }
            }

            // If no direct match, assign to the team with fewer players
            if (assignedTeam == null)
            {
                int minCount = Integer.MAX_VALUE;
                for (String teamName : teamColorMap.keySet())
                {
                    int count = countPlayersInTeam(match, teamName);
                    if (count < minCount)
                    {
                        minCount = count;
                        assignedTeam = teamName;
                    }
                }
            }

            match.addPlayer(player, assignedTeam);

            // Force player ink color to match team color for accurate turf scanning
            int teamColor = stage.getTeamColor(assignedTeam);
            if (teamColor >= 0 && ColorUtils.getPlayerColor(player) != teamColor)
            {
                ColorUtils.setPlayerColor(player, teamColor);
            }
        }

        setSpawnPoints(source, stageName, stage, match);

        MatchHandler.addMatch(match);

        source.sendSuccess(() -> Component.translatable("commands.match.start.success", stageName,
            Component.literal(matchId.toString()).withStyle(net.minecraft.ChatFormatting.GREEN)), true);

        return 1;
    }

    private static int stopMatchCmd(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        String stageName = StringArgumentType.getString(context, "stage");

        Match match = MatchHandler.getMatchByStage(stageName);
        if (match == null)
            throw MATCH_NOT_FOUND.create(stageName);

        UUID matchId = match.id;
        MatchHandler.stopMatch(matchId);

        source.sendSuccess(() -> Component.translatable("commands.match.stop.success",
            Component.literal(stageName).withStyle(net.minecraft.ChatFormatting.GREEN)), true);

        return 1;
    }

    private static boolean isPlayerInStage(ServerPlayer player, Stage stage)
    {
        if (!player.level().dimension().location().equals(stage.dimID))
            return false;

        int minX = Math.min(stage.cornerA.getX(), stage.cornerB.getX());
        int minY = Math.min(stage.cornerA.getY(), stage.cornerB.getY());
        int minZ = Math.min(stage.cornerA.getZ(), stage.cornerB.getZ());
        int maxX = Math.max(stage.cornerA.getX(), stage.cornerB.getX());
        int maxY = Math.max(stage.cornerA.getY(), stage.cornerB.getY());
        int maxZ = Math.max(stage.cornerA.getZ(), stage.cornerB.getZ());

        return player.getX() >= minX && player.getX() <= maxX
            && player.getY() >= minY && player.getY() <= maxY
            && player.getZ() >= minZ && player.getZ() <= maxZ;
    }

    private static int countPlayersInTeam(Match match, String teamName)
    {
        int count = 0;
        for (String t : match.playerTeams.values())
        {
            if (t.equals(teamName)) count++;
        }
        return count;
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestStages(
        CommandContext<CommandSourceStack> context,
        com.mojang.brigadier.suggestion.SuggestionsBuilder builder)
    {
        CommandSourceStack source = context.getSource();
        HashMap<String, Stage> stages;

        if (source.getLevel().isClientSide())
            stages = ClientUtils.clientStages;
        else
            stages = SaveInfoCapability.get(source.getServer()).getStages();

        return SharedSuggestionProvider.suggest(stages.keySet(), builder);
    }

    private static void setSpawnPoints(CommandSourceStack source, String stageName, Stage stage, Match match)
    {
        Level stageLevel = source.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, stage.dimID));
        if (stageLevel == null) return;

        BlockPos minPos = new BlockPos(Math.min(stage.cornerA.getX(), stage.cornerB.getX()), Math.min(stage.cornerA.getY(), stage.cornerB.getY()), Math.min(stage.cornerA.getZ(), stage.cornerB.getZ()));
        BlockPos maxPos = new BlockPos(Math.max(stage.cornerA.getX(), stage.cornerB.getX()), Math.max(stage.cornerA.getY(), stage.cornerB.getY()), Math.max(stage.cornerA.getZ(), stage.cornerB.getZ()));

        Map<Integer, List<SpawnPadTileEntity>> spawnPads = new HashMap<>();
        for (int x = minPos.getX(); x <= maxPos.getX(); x++)
            for (int y = minPos.getY(); y <= maxPos.getY(); y++)
                for (int z = minPos.getZ(); z <= maxPos.getZ(); z++)
                {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (stageLevel.getBlockEntity(pos) instanceof SpawnPadTileEntity te)
                        spawnPads.computeIfAbsent(te.getColor(), k -> new ArrayList<>()).add(te);
                }

        if (spawnPads.isEmpty()) return;

        Map<String, Integer> teamUsage = new HashMap<>();
        for (UUID uuid : match.getPlayerUUIDs())
        {
            ServerPlayer player = match.getPlayer(uuid);
            if (player == null) continue;

            String team = match.getPlayerTeam(uuid);
            int teamColor = stage.getTeamColor(team);
            List<SpawnPadTileEntity> pads = spawnPads.get(teamColor);
            if (pads == null || pads.isEmpty()) continue;

            int idx = teamUsage.getOrDefault(team, 0) % pads.size();
            SpawnPadTileEntity pad = pads.get(idx);
            float yaw = stageLevel.getBlockState(pad.getBlockPos()).getValue(SpawnPadBlock.DIRECTION).toYRot();

            if (stageLevel == player.level())
                player.connection.teleport(pad.getBlockPos().getX() + 0.5, pad.getBlockPos().getY() + 0.5, pad.getBlockPos().getZ() + 0.5, yaw, 0);
            else
                player.teleportTo((ServerLevel) stageLevel, pad.getBlockPos().getX() + 0.5, pad.getBlockPos().getY() + 0.5, pad.getBlockPos().getZ() + 0.5, yaw, 0);

            player.setRespawnPosition(player.level().dimension(), pad.getBlockPos(), yaw, false, true);
            SuperJumpCommand.syncSpawnPosition(player);

            teamUsage.put(team, idx + 1);
        }
    }
}
