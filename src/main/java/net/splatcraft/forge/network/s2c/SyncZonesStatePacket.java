package net.splatcraft.forge.network.s2c;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.splatcraft.forge.client.data.ClientMatchData;

import java.util.UUID;

public class SyncZonesStatePacket extends PlayS2CPacket
{
    UUID matchId;
    String[] teamNames;
    int[] teamColors;
    int[] zoneTimers;
    int[] zonePenalties;
    int controllingTeamIdx;
    int zoneCount;
    BlockPos[] zoneMins;
    BlockPos[] zoneMaxs;
    int[][] zoneTeamPcts;
    int[] zoneControllers;
    boolean overtimeActive;
    int overtimeDrain;

    public SyncZonesStatePacket() {}

    public SyncZonesStatePacket(UUID matchId, String[] teamNames, int[] teamColors,
                                int[] zoneTimers, int[] zonePenalties,
                                int controllingTeamIdx,
                                BlockPos[] zoneMins, BlockPos[] zoneMaxs,
                                int[][] zoneTeamPcts, int[] zoneControllers,
                                boolean overtimeActive, int overtimeDrain)
    {
        this.matchId = matchId;
        this.teamNames = teamNames;
        this.teamColors = teamColors;
        this.zoneTimers = zoneTimers;
        this.zonePenalties = zonePenalties;
        this.controllingTeamIdx = controllingTeamIdx;
        this.zoneCount = zoneMins.length;
        this.zoneMins = zoneMins;
        this.zoneMaxs = zoneMaxs;
        this.zoneTeamPcts = zoneTeamPcts;
        this.zoneControllers = zoneControllers;
        this.overtimeActive = overtimeActive;
        this.overtimeDrain = overtimeDrain;
    }

    @Override
    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeUUID(matchId);

        buffer.writeInt(teamNames.length);
        for (int i = 0; i < teamNames.length; i++)
        {
            buffer.writeUtf(teamNames[i]);
            buffer.writeInt(teamColors[i]);
            buffer.writeInt(zoneTimers[i]);
            buffer.writeInt(zonePenalties[i]);
        }

        buffer.writeInt(controllingTeamIdx);

        buffer.writeInt(zoneCount);
        for (int i = 0; i < zoneCount; i++)
        {
            buffer.writeBlockPos(zoneMins[i]);
            buffer.writeBlockPos(zoneMaxs[i]);

            buffer.writeInt(zoneTeamPcts[i].length);
            for (int j = 0; j < zoneTeamPcts[i].length; j++)
                buffer.writeInt(zoneTeamPcts[i][j]);
            buffer.writeInt(zoneControllers[i]);
        }

        buffer.writeBoolean(overtimeActive);
        buffer.writeInt(overtimeDrain);
    }

    public static SyncZonesStatePacket decode(FriendlyByteBuf buffer)
    {
        SyncZonesStatePacket pkt = new SyncZonesStatePacket();
        pkt.matchId = buffer.readUUID();

        int teamCount = buffer.readInt();
        pkt.teamNames = new String[teamCount];
        pkt.teamColors = new int[teamCount];
        pkt.zoneTimers = new int[teamCount];
        pkt.zonePenalties = new int[teamCount];
        for (int i = 0; i < teamCount; i++)
        {
            pkt.teamNames[i] = buffer.readUtf();
            pkt.teamColors[i] = buffer.readInt();
            pkt.zoneTimers[i] = buffer.readInt();
            pkt.zonePenalties[i] = buffer.readInt();
        }

        pkt.controllingTeamIdx = buffer.readInt();

        pkt.zoneCount = buffer.readInt();
        pkt.zoneMins = new BlockPos[pkt.zoneCount];
        pkt.zoneMaxs = new BlockPos[pkt.zoneCount];
        pkt.zoneTeamPcts = new int[pkt.zoneCount][];
        pkt.zoneControllers = new int[pkt.zoneCount];
        for (int i = 0; i < pkt.zoneCount; i++)
        {
            pkt.zoneMins[i] = buffer.readBlockPos();
            pkt.zoneMaxs[i] = buffer.readBlockPos();

            int pctLen = buffer.readInt();
            pkt.zoneTeamPcts[i] = new int[pctLen];
            for (int j = 0; j < pctLen; j++)
                pkt.zoneTeamPcts[i][j] = buffer.readInt();
            pkt.zoneControllers[i] = buffer.readInt();
        }

        pkt.overtimeActive = buffer.readBoolean();
        pkt.overtimeDrain = buffer.readInt();

        return pkt;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void execute()
    {
        ClientMatchData.updateZones(matchId, teamNames, teamColors,
            zoneTimers, zonePenalties, controllingTeamIdx,
            zoneMins, zoneMaxs, zoneTeamPcts, zoneControllers,
            overtimeActive, overtimeDrain);
    }
}
