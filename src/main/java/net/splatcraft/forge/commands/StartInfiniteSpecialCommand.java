package net.splatcraft.forge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfo;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfoCapability;
import net.splatcraft.forge.items.weapons.SpecialWeaponItem;
import net.splatcraft.forge.items.weapons.WeaponBaseItem;
import net.splatcraft.forge.network.SplatcraftPacketHandler;
import net.splatcraft.forge.network.s2c.UpdatePlayerInfoPacket;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

public class StartInfiniteSpecialCommand
{
    private static final DynamicCommandExceptionType ITEM_NOT_FOUND = new DynamicCommandExceptionType(o -> Component.translatable("commands.startinfinitespecial.item_not_found", o));
    private static final DynamicCommandExceptionType NOT_SPECIAL = new DynamicCommandExceptionType(o -> Component.translatable("commands.startinfinitespecial.not_special", o));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("startinfinitespecial").requires(cs -> cs.hasPermission(2))
            .then(Commands.argument("item", StringArgumentType.word())
                .suggests(StartInfiniteSpecialCommand::suggestSpecials)
                .executes(ctx -> {
                    ServerPlayer self = ctx.getSource().getPlayerOrException();
                    return execute(ctx.getSource(), StringArgumentType.getString(ctx, "item"), Collections.singleton(self));
                })
                .then(Commands.argument("target", EntityArgument.players())
                    .executes(ctx -> execute(ctx.getSource(), StringArgumentType.getString(ctx, "item"), EntityArgument.getPlayers(ctx, "target")))
                )
            )
        );
    }

    private static int execute(CommandSourceStack source, String itemId, Collection<ServerPlayer> targets) throws CommandSyntaxException
    {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null || rl.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE))
            rl = new ResourceLocation("splatcraft", itemId);

        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null) throw ITEM_NOT_FOUND.create(itemId);

        if (!(item instanceof SpecialWeaponItem specialWeapon))
            throw NOT_SPECIAL.create(itemId);

        ItemStack specialStack = new ItemStack(item);

        for (ServerPlayer player : targets)
        {
            ItemStack mainWeapon = findMainWeapon(player);
            if (mainWeapon.isEmpty())
            {
                source.sendFailure(Component.literal(player.getName().getString() + " has no main weapon"));
                return 0;
            }

            WeaponBaseItem.setStoredSpecialWeapon(mainWeapon, specialStack);
            WeaponBaseItem.setSpecialPoints(mainWeapon, Integer.MAX_VALUE / 2);
            WeaponBaseItem.setActiveSpecial(mainWeapon, true);

            specialWeapon.useSpecial(player.level(), player, specialStack, mainWeapon);

            PlayerInfo info = PlayerInfoCapability.get(player);
            info.setInfiniteSpecial(true);
            info.setSpecialTicksRemaining(Integer.MAX_VALUE);

            SplatcraftPacketHandler.sendToTrackersAndSelf(new UpdatePlayerInfoPacket(player), player);
        }

        source.sendSuccess(() -> Component.literal("Started infinite special for " + targets.size() + " player(s)"), true);
        return targets.size();
    }

    private static ItemStack findMainWeapon(ServerPlayer player)
    {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
        {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof WeaponBaseItem<?>)
                return stack;
        }
        return ItemStack.EMPTY;
    }

    private static CompletableFuture<Suggestions> suggestSpecials(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder)
    {
        for (ResourceLocation key : ForgeRegistries.ITEMS.getKeys())
        {
            Item item = ForgeRegistries.ITEMS.getValue(key);
            if (item instanceof SpecialWeaponItem)
                builder.suggest(key.getPath());
        }
        return builder.buildFuture();
    }
}
