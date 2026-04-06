package net.splatcraft.forge.registries;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.commands.ClearInkCommand;
import net.splatcraft.forge.commands.InkColorCommand;
import net.splatcraft.forge.commands.ReplaceColorCommand;
import net.splatcraft.forge.commands.ScanTurfCommand;
import net.splatcraft.forge.commands.StageCommand;
import net.splatcraft.forge.commands.SuperJumpCommand;
import net.splatcraft.forge.commands.arguments.InkColorArgument;

import static net.splatcraft.forge.Splatcraft.MODID;

@Mod.EventBusSubscriber(modid = Splatcraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SplatcraftCommands
{
    protected static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARGUMENT_REGISTRY = DeferredRegister.create(ForgeRegistries.COMMAND_ARGUMENT_TYPES, MODID);

    public static final RegistryObject<ArgumentTypeInfo<?, ?>> INK_COLOR_ARGUMENT = ARGUMENT_REGISTRY.register("ink_color", () -> ArgumentTypeInfos.registerByClass(InkColorArgument.class, SingletonArgumentInfo.contextFree(InkColorArgument::inkColor)));

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event)
    {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        InkColorCommand.register(dispatcher);
        ScanTurfCommand.register(dispatcher);
        ClearInkCommand.register(dispatcher);
        ReplaceColorCommand.register(dispatcher);
        StageCommand.register(dispatcher);

        SuperJumpCommand.register(dispatcher);
    }

    public static void registerArguments()
    {

        //ArgumentTypes.register(Splatcraft.MODID + ":color_criterion", ColorCriterionArgument.class, new EmptyArgumentSerializer<>(ColorCriterionArgument::colorCriterion));
    }
}
