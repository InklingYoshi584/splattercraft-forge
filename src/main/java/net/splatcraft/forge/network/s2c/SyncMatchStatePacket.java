package net.splatcraft.forge.network.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.splatcraft.forge.data.match.Match;
import net.splatcraft.forge.data.match.MatchPhase;
import net.splatcraft.forge.data.match.MatchType;
import net.splatcraft.forge.client.data.ClientMatchData;

import java.util.*;

public class SyncMatchStatePacket extends PlayS2CPacket
{
    UUID matchId;
    int phase;
    int remainingSeconds;
    int totalSeconds;
    int typeOrdinal;
    String[] teamNames;
    int[] teamColors;
    float[] teamPcts;
    UUID[] playerUUIDs;
    String[] playerTeamNames;
    boolean[] playerAliveArr;
    boolean[] playerSpecialReadyArr;
    boolean clear;

    public SyncMatchStatePacket() {}

    public SyncMatchStatePacket(Match match, net.minecraft.server.MinecraftServer server)
    {
        this.matchId = match.id;
        this.phase = match.phase.ordinal();
        this.remainingSeconds = Math.max(0, (match.remainingTimeTicks + 19) / 20);
        this.totalSeconds = match.totalTimeSeconds;
        this.typeOrdinal = match.type.ordinal();
        this.clear = false;

        Collection<String> teams = match.getTeamNames();
        this.teamNames = teams.toArray(new String[0]);
        this.teamColors = new int[teams.size()];
        this.teamPcts = new float[teams.size()];

        int i = 0;
        net.splatcraft.forge.data.Stage stage = match.getStage(server);
        for (String team : teams)
        {
            if (match.type == MatchType.ZONES && match.phase == MatchPhase.FINISHED)
                this.teamPcts[i] = 100 - match.zoneTimers.getOrDefault(team, 100);
            else
                this.teamPcts[i] = match.teamPercentages.getOrDefault(team, 0.0F);
            this.teamColors[i] = stage != null ? stage.getTeamColor(team) : 0;
            i++;
        }

        Collection<UUID> players = match.getPlayerUUIDs();
        this.playerUUIDs = players.toArray(new UUID[0]);
        this.playerTeamNames = new String[players.size()];
        this.playerAliveArr = new boolean[players.size()];
        this.playerSpecialReadyArr = new boolean[players.size()];

        int j = 0;
        for (UUID uuid : players)
        {
            this.playerTeamNames[j] = match.getPlayerTeam(uuid);
            this.playerAliveArr[j] = match.playerAlive.getOrDefault(uuid, false);
            this.playerSpecialReadyArr[j] = match.playerSpecialReady.getOrDefault(uuid, false);
            j++;
        }
    }

    public static SyncMatchStatePacket createClearPacket()
    {
        SyncMatchStatePacket pkt = new SyncMatchStatePacket();
        pkt.clear = true;
        pkt.matchId = new UUID(0, 0);
        pkt.teamNames = new String[0];
        pkt.teamColors = new int[0];
        pkt.teamPcts = new float[0];
        pkt.playerUUIDs = new UUID[0];
        pkt.playerTeamNames = new String[0];
        pkt.playerAliveArr = new boolean[0];
        pkt.playerSpecialReadyArr = new boolean[0];
        return pkt;
    }

    @Override
    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeBoolean(clear);
        buffer.writeUUID(matchId);
        buffer.writeInt(phase);
        buffer.writeInt(remainingSeconds);
        buffer.writeInt(totalSeconds);
        buffer.writeInt(typeOrdinal);

        buffer.writeInt(teamNames.length);
        for (int i = 0; i < teamNames.length; i++)
        {
            buffer.writeUtf(teamNames[i]);
            buffer.writeInt(teamColors[i]);
            buffer.writeFloat(teamPcts[i]);
        }

        buffer.writeInt(playerUUIDs.length);
        for (int i = 0; i < playerUUIDs.length; i++)
        {
            buffer.writeUUID(playerUUIDs[i]);
            buffer.writeUtf(playerTeamNames[i]);
            buffer.writeBoolean(playerAliveArr[i]);
            buffer.writeBoolean(playerSpecialReadyArr[i]);
        }
    }

    public static SyncMatchStatePacket decode(FriendlyByteBuf buffer)
    {
        SyncMatchStatePacket pkt = new SyncMatchStatePacket();
        pkt.clear = buffer.readBoolean();
        pkt.matchId = buffer.readUUID();
        pkt.phase = buffer.readInt();
        pkt.remainingSeconds = buffer.readInt();
        pkt.totalSeconds = buffer.readInt();
        pkt.typeOrdinal = buffer.readInt();

        int teamCount = buffer.readInt();
        pkt.teamNames = new String[teamCount];
        pkt.teamColors = new int[teamCount];
        pkt.teamPcts = new float[teamCount];
        for (int i = 0; i < teamCount; i++)
        {
            pkt.teamNames[i] = buffer.readUtf();
            pkt.teamColors[i] = buffer.readInt();
            pkt.teamPcts[i] = buffer.readFloat();
        }

        int playerCount = buffer.readInt();
        pkt.playerUUIDs = new UUID[playerCount];
        pkt.playerTeamNames = new String[playerCount];
        pkt.playerAliveArr = new boolean[playerCount];
        pkt.playerSpecialReadyArr = new boolean[playerCount];
        for (int i = 0; i < playerCount; i++)
        {
            pkt.playerUUIDs[i] = buffer.readUUID();
            pkt.playerTeamNames[i] = buffer.readUtf();
            pkt.playerAliveArr[i] = buffer.readBoolean();
            pkt.playerSpecialReadyArr[i] = buffer.readBoolean();
        }

        return pkt;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void execute()
    {
        if (clear)
        {
            ClientMatchData.clear();
            return;
        }

        ClientMatchData.update(matchId, MatchPhase.values()[phase], remainingSeconds, totalSeconds,
            MatchType.values()[typeOrdinal], teamNames, teamColors, teamPcts,
            playerUUIDs, playerTeamNames, playerAliveArr, playerSpecialReadyArr);
    }
}
