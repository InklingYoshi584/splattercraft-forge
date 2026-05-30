package net.splatcraft.forge.client.data;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.core.BlockPos;
import net.splatcraft.forge.data.match.MatchPhase;
import net.splatcraft.forge.data.match.MatchType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class ClientMatchData
{
    public static UUID currentMatchId;
    public static MatchPhase phase = MatchPhase.COUNTDOWN;
    public static int remainingSeconds;
    public static int totalSeconds;
    public static MatchType matchType = MatchType.TURF;
    public static String[] teamNames = new String[0];
    public static int[] teamColors = new int[0];
    public static float[] teamPcts = new float[0];
    public static UUID[] playerUUIDs = new UUID[0];
    public static String[] playerTeams = new String[0];
    public static boolean[] playerAlive = new boolean[0];
    public static boolean[] playerSpecialReady = new boolean[0];

    public static long deathAnimStartTimeMs;
    public static long resultAnimStartMs;
    public static final Map<UUID, Long> playerDeathAnim = new HashMap<>();
    public static final Map<UUID, Boolean> playerPrevAlive = new HashMap<>();

    public static int[] zoneTimers = new int[0];
    public static int[] zonePenalties = new int[0];
    public static int controllingTeamIdx = -1;
    public static BlockPos[] zoneMins = new BlockPos[0];
    public static BlockPos[] zoneMaxs = new BlockPos[0];
    public static int[][] zoneTeamPcts = new int[0][];
    public static int[] zoneControllers = new int[0];
    public static boolean overtimeActive;
    public static int overtimeDrain;
    public static boolean zoneKnockout;

    public static void update(UUID matchId, MatchPhase p, int remaining, int total,
                              MatchType type, String[] tNames, int[] tColors, float[] tPcts,
                              UUID[] pUUIDs, String[] pTeams, boolean[] pAlive, boolean[] pSpecial)
    {
        if (currentMatchId != null && !currentMatchId.equals(matchId))
        {
            playerDeathAnim.clear();
            playerPrevAlive.clear();
        }

        currentMatchId = matchId;
        if (p == MatchPhase.FINISHED && phase != MatchPhase.FINISHED)
        {
            resultAnimStartMs = System.currentTimeMillis();
        }
        phase = p;
        remainingSeconds = remaining;
        totalSeconds = total;
        matchType = type;
        teamNames = tNames;
        teamColors = tColors;
        teamPcts = tPcts;

        for (int i = 0; i < playerUUIDs.length; i++)
        {
            UUID uuid = playerUUIDs[i];
            boolean prev = playerPrevAlive.getOrDefault(uuid, true);
            if (prev && !pAlive[i])
            {
                playerDeathAnim.put(uuid, System.currentTimeMillis());
            }
            if (pAlive[i])
            {
                playerDeathAnim.remove(uuid);
            }
            playerPrevAlive.put(uuid, pAlive[i]);
        }

        playerUUIDs = pUUIDs;
        playerTeams = pTeams;
        playerAlive = pAlive;
        playerSpecialReady = pSpecial;
    }

    public static boolean isFrozen()
    {
        return currentMatchId != null && (phase == MatchPhase.COUNTDOWN || phase == MatchPhase.FINISHED);
    }

    public static void updateZones(UUID matchId, String[] tNames, int[] tColors,
                                    int[] zTimers, int[] zPenalties, int ctrlIdx,
                                    BlockPos[] zMins, BlockPos[] zMaxs, int[][] zTeamPcts,
                                    int[] zControllers,
                                    boolean otActive, int otDrain)
    {
        if (currentMatchId != null && !currentMatchId.equals(matchId))
        {
            zoneTimers = new int[0];
            zonePenalties = new int[0];
            controllingTeamIdx = -1;
            zoneMins = new BlockPos[0];
            zoneMaxs = new BlockPos[0];
            zoneTeamPcts = new int[0][];
            zoneControllers = new int[0];
            overtimeActive = false;
            overtimeDrain = 0;
        }
        zoneTimers = zTimers;
        zonePenalties = zPenalties;
        controllingTeamIdx = ctrlIdx;
        zoneMins = zMins;
        zoneMaxs = zMaxs;
        zoneTeamPcts = zTeamPcts;
        zoneControllers = zControllers;
        overtimeActive = otActive;
        overtimeDrain = otDrain;
    }

    public static void clear()
    {
        currentMatchId = null;
        phase = MatchPhase.COUNTDOWN;
        remainingSeconds = 0;
        totalSeconds = 0;
        teamNames = new String[0];
        teamColors = new int[0];
        teamPcts = new float[0];
        playerUUIDs = new UUID[0];
        playerTeams = new String[0];
        playerAlive = new boolean[0];
        playerSpecialReady = new boolean[0];
        playerDeathAnim.clear();
        playerPrevAlive.clear();
        resultAnimStartMs = 0;
        zoneTimers = new int[0];
        zonePenalties = new int[0];
        controllingTeamIdx = -1;
        zoneMins = new BlockPos[0];
        zoneMaxs = new BlockPos[0];
        zoneTeamPcts = new int[0][];
        zoneControllers = new int[0];
        overtimeActive = false;
        overtimeDrain = 0;
        zoneKnockout = false;
    }

    public static int getPlayerIndex(UUID uuid)
    {
        for (int i = 0; i < playerUUIDs.length; i++)
        {
            if (playerUUIDs[i].equals(uuid))
                return i;
        }
        return -1;
    }

    public static int getTeamIndex(String teamName)
    {
        for (int i = 0; i < teamNames.length; i++)
        {
            if (teamNames[i].equals(teamName))
                return i;
        }
        return -1;
    }
}
