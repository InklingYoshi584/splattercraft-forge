package net.splatcraft.forge.network.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.splatcraft.forge.client.data.ClientMatchData;

import java.util.UUID;

public class MatchResultPacket extends PlayS2CPacket
{
    UUID matchId;
    String winnerTeam;
    float[] teamPcts;
    String[] teamNames;
    int[] teamColors;

    public MatchResultPacket() {}

    public MatchResultPacket(UUID matchId, String winnerTeam, float[] teamPcts, String[] teamNames, int[] teamColors)
    {
        this.matchId = matchId;
        this.winnerTeam = winnerTeam;
        this.teamPcts = teamPcts;
        this.teamNames = teamNames;
        this.teamColors = teamColors;
    }

    @Override
    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeUUID(matchId);
        buffer.writeUtf(winnerTeam != null ? winnerTeam : "");

        buffer.writeInt(teamNames.length);
        for (int i = 0; i < teamNames.length; i++)
        {
            buffer.writeUtf(teamNames[i]);
            buffer.writeInt(teamColors[i]);
            buffer.writeFloat(teamPcts[i]);
        }
    }

    public static MatchResultPacket decode(FriendlyByteBuf buffer)
    {
        UUID mid = buffer.readUUID();
        String winner = buffer.readUtf();
        if (winner.isEmpty()) winner = null;

        int count = buffer.readInt();
        String[] names = new String[count];
        int[] colors = new int[count];
        float[] pcts = new float[count];
        for (int i = 0; i < count; i++)
        {
            names[i] = buffer.readUtf();
            colors[i] = buffer.readInt();
            pcts[i] = buffer.readFloat();
        }

        return new MatchResultPacket(mid, winner, pcts, names, colors);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void execute()
    {
        if (ClientMatchData.currentMatchId != null && ClientMatchData.currentMatchId.equals(matchId))
        {
            if (ClientMatchData.phase != net.splatcraft.forge.data.match.MatchPhase.FINISHED)
                ClientMatchData.resultAnimStartMs = System.currentTimeMillis();
            ClientMatchData.phase = net.splatcraft.forge.data.match.MatchPhase.FINISHED;
            ClientMatchData.teamPcts = teamPcts;
            ClientMatchData.teamNames = teamNames;
            ClientMatchData.teamColors = teamColors;
        }
    }
}
