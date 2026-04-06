package net.splatcraft.forge.util;

import net.minecraft.world.entity.player.Player;
import net.splatcraft.forge.SplatcraftConfig;

import java.util.List;
import java.util.UUID;

public class AbilityAccessUtils
{
    public static boolean canUseInkAbilities(Player player)
    {
        if (player == null)
            return false;

        List<? extends String> whitelist = SplatcraftConfig.Server.abilityWhitelist.get();
        if (whitelist.isEmpty())
            return false;

        String playerUuid = player.getUUID().toString();
        for (String entry : whitelist)
        {
            if (playerUuid.equalsIgnoreCase(entry))
                return true;
        }

        return false;
    }

    public static boolean addAllowedPlayer(UUID uuid)
    {
        List<String> updated = new java.util.ArrayList<>(SplatcraftConfig.Server.abilityWhitelist.get());
        String entry = uuid.toString();
        if (updated.stream().anyMatch(value -> value.equalsIgnoreCase(entry)))
            return false;

        updated.add(entry);
        SplatcraftConfig.Server.abilityWhitelist.set(updated);
        SplatcraftConfig.saveServerConfig();
        return true;
    }

    public static boolean removeAllowedPlayer(UUID uuid)
    {
        String entry = uuid.toString();
        List<String> updated = new java.util.ArrayList<>(SplatcraftConfig.Server.abilityWhitelist.get());
        boolean removed = updated.removeIf(value -> value.equalsIgnoreCase(entry));
        if (!removed)
            return false;

        SplatcraftConfig.Server.abilityWhitelist.set(updated);
        SplatcraftConfig.saveServerConfig();
        return true;
    }

    public static boolean isWhitelistEnabled()
    {
        return true;
    }
}
