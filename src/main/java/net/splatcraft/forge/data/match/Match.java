package net.splatcraft.forge.data.match;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.splatcraft.forge.data.Stage;
import net.splatcraft.forge.items.weapons.WeaponBaseItem;

import java.util.*;

public class Match
{
    public final UUID id;
    public final String stageName;
    public final MatchType type;
    public MatchPhase phase = MatchPhase.COUNTDOWN;
    public final int totalTimeSeconds;
    public int remainingTimeTicks;
    public int countdownTicks = 60;
    public int finishedTicks;
    public final Map<UUID, String> playerTeams = new HashMap<>();
    public final Map<UUID, Boolean> playerAlive = new HashMap<>();
    public final Map<UUID, Boolean> playerSpecialReady = new HashMap<>();
    public final Map<String, Integer> teamScores = new HashMap<>();
    public int totalBlocks;
    public final Map<String, Float> teamPercentages = new HashMap<>();
    private final Map<UUID, ServerPlayer> playerCache = new HashMap<>();

    public Match(UUID id, String stageName, MatchType type, int totalTimeSeconds)
    {
        this.id = id;
        this.stageName = stageName;
        this.type = type;
        this.totalTimeSeconds = totalTimeSeconds;
        this.remainingTimeTicks = totalTimeSeconds * 20;
    }

    public Stage getStage(MinecraftServer server)
    {
        return net.splatcraft.forge.data.capabilities.saveinfo.SaveInfoCapability.get(server).getStages().get(stageName);
    }

    public BlockPos getCenterPos(Stage stage)
    {
        return new BlockPos(
            (stage.cornerA.getX() + stage.cornerB.getX()) / 2,
            Math.max(stage.cornerA.getY(), stage.cornerB.getY()) + 20,
            (stage.cornerA.getZ() + stage.cornerB.getZ()) / 2
        );
    }

    public void addPlayer(ServerPlayer player, String teamName)
    {
        UUID uuid = player.getUUID();
        playerTeams.put(uuid, teamName);
        playerAlive.put(uuid, true);
        playerSpecialReady.put(uuid, false);
        playerCache.put(uuid, player);
    }

    public void removePlayer(UUID uuid)
    {
        playerTeams.remove(uuid);
        playerAlive.remove(uuid);
        playerSpecialReady.remove(uuid);
        playerCache.remove(uuid);
    }

    public String getPlayerTeam(UUID uuid)
    {
        return playerTeams.get(uuid);
    }

    public ServerPlayer getPlayer(UUID uuid)
    {
        return playerCache.get(uuid);
    }

    public Collection<UUID> getPlayerUUIDs()
    {
        return playerTeams.keySet();
    }

    public Collection<String> getTeamNames()
    {
        return teamScores.keySet();
    }

    public void refreshPlayerCache(MinecraftServer server)
    {
        playerCache.clear();
        for (UUID uuid : playerTeams.keySet())
        {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null)
                playerCache.put(uuid, player);
        }
    }

    public void updatePlayerStates()
    {
        for (Map.Entry<UUID, ServerPlayer> entry : playerCache.entrySet())
        {
            ServerPlayer player = entry.getValue();
            if (player == null)
            {
                playerAlive.put(entry.getKey(), false);
                playerSpecialReady.put(entry.getKey(), false);
                continue;
            }

            playerAlive.put(entry.getKey(), player.isAlive() && !player.isDeadOrDying());

            ItemStack mainHand = player.getMainHandItem();
            boolean specialReady = mainHand.getItem() instanceof WeaponBaseItem<?>
                && WeaponBaseItem.canUseStoredSpecial(mainHand);
            playerSpecialReady.put(entry.getKey(), specialReady);
        }
    }

    public String getWinnerTeam()
    {
        String winner = null;
        float highest = -1;
        for (Map.Entry<String, Float> entry : teamPercentages.entrySet())
        {
            if (entry.getValue() > highest)
            {
                highest = entry.getValue();
                winner = entry.getKey();
            }
        }
        return winner;
    }
}
