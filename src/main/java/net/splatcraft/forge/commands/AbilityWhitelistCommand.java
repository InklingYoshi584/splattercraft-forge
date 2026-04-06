package net.splatcraft.forge.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.splatcraft.forge.util.AbilityAccessUtils;

public class AbilityWhitelistCommand
{
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("inkwhitelist")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> addPlayer(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> removePlayer(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("list")
                        .executes(context -> listState(context.getSource()))));
    }

    private static int addPlayer(CommandSourceStack source, ServerPlayer player)
    {
        if (AbilityAccessUtils.addAllowedPlayer(player.getUUID()))
        {
            source.sendSuccess(() -> Component.literal("Added " + player.getGameProfile().getName() + " to the ink ability whitelist."), true);
            return 1;
        }

        source.sendFailure(Component.literal(player.getGameProfile().getName() + " is already on the ink ability whitelist."));
        return 0;
    }

    private static int removePlayer(CommandSourceStack source, ServerPlayer player)
    {
        if (AbilityAccessUtils.removeAllowedPlayer(player.getUUID()))
        {
            source.sendSuccess(() -> Component.literal("Removed " + player.getGameProfile().getName() + " from the ink ability whitelist."), true);
            return 1;
        }

        source.sendFailure(Component.literal(player.getGameProfile().getName() + " is not on the ink ability whitelist."));
        return 0;
    }

    private static int listState(CommandSourceStack source)
    {
        if (net.splatcraft.forge.SplatcraftConfig.Server.abilityWhitelist.get().isEmpty())
        {
            source.sendSuccess(() -> Component.literal("Ink ability whitelist is empty. Nobody is currently allowed."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Ink ability whitelist is enabled via " + net.splatcraft.forge.SplatcraftConfig.Server.abilityWhitelist.get().size() + " UUID entr" + (net.splatcraft.forge.SplatcraftConfig.Server.abilityWhitelist.get().size() == 1 ? "y" : "ies") + "."), false);
        return 1;
    }
}
