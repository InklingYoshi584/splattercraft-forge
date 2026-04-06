package net.splatcraft.forge.handlers;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfo;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfoCapability;
import net.splatcraft.forge.items.weapons.SpecialWeaponItem;
import net.splatcraft.forge.items.weapons.WeaponBaseItem;
import net.splatcraft.forge.util.AbilityAccessUtils;

@Mod.EventBusSubscriber(modid = Splatcraft.MODID)
public class SpecialHandler
{
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START || event.side.isClient())
            return;

        Player player = event.player;
        if (!AbilityAccessUtils.canUseInkAbilities(player))
        {
            ItemStack activeSpecial = getActiveSpecialStack(player);
            if (activeSpecial.getItem() instanceof SpecialWeaponItem specialWeapon)
            {
                int sourceSlot = PlayerInfoCapability.get(player).getSpecialSourceSlot();
                if (sourceSlot >= 0 && sourceSlot < player.getInventory().getContainerSize())
                    endSpecial(player, player.getInventory().getItem(sourceSlot), specialWeapon, activeSpecial, true);
            }
            return;
        }

        if (!PlayerInfoCapability.hasCapability(player))
            return;

        PlayerInfo info = PlayerInfoCapability.get(player);
        if (!info.hasActiveSpecial())
            return;

        int sourceSlot = info.getSpecialSourceSlot();
        if (sourceSlot < 0 || sourceSlot >= player.getInventory().getContainerSize())
        {
            info.clearSpecial();
            return;
        }

        ItemStack weaponStack = player.getInventory().getItem(sourceSlot);
        if (!(weaponStack.getItem() instanceof WeaponBaseItem<?>))
        {
            info.clearSpecial();
            return;
        }

        ItemStack specialStack = WeaponBaseItem.getStoredSpecialWeapon(weaponStack);
        if (!(specialStack.getItem() instanceof SpecialWeaponItem specialWeapon))
        {
            info.clearSpecial();
            WeaponBaseItem.setSpecialPoints(weaponStack, 0);
            return;
        }

        if (info.isSpecialInWindup())
        {
            info.tickSpecialWindup();
            specialWeapon.onSpecialWindupTick(player.level(), player, specialStack, weaponStack, info.getSpecialWindupTicksRemaining());
            return;
        }

        if (specialWeapon.shouldInterruptActiveSpecial(player.level(), player, specialStack, weaponStack))
        {
            endSpecial(player, weaponStack, specialWeapon, specialStack, true);
            return;
        }

        info.tickActiveSpecial();
        specialWeapon.onSpecialActiveTick(player.level(), player, specialStack, weaponStack, info.getSpecialTicksRemaining());

        if (info.getSpecialTicksRemaining() <= 0)
            endSpecial(player, weaponStack, specialWeapon, specialStack, false);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof Player player) || !PlayerInfoCapability.hasCapability(player))
            return;

        PlayerInfo info = PlayerInfoCapability.get(player);
        if (!info.hasActiveSpecial())
            return;

        int sourceSlot = info.getSpecialSourceSlot();
        if (sourceSlot >= 0 && sourceSlot < player.getInventory().getContainerSize())
        {
            ItemStack weaponStack = player.getInventory().getItem(sourceSlot);
            ItemStack specialStack = WeaponBaseItem.getStoredSpecialWeapon(weaponStack);
            if (specialStack.getItem() instanceof SpecialWeaponItem specialWeapon)
                endSpecial(player, weaponStack, specialWeapon, specialStack, true);
        }

        info.clearSpecial();
    }

    private static void endSpecial(Player player, ItemStack weaponStack, SpecialWeaponItem specialWeapon, ItemStack specialStack, boolean interrupted)
    {
        specialWeapon.onSpecialEnd(player.level(), player, specialStack, weaponStack, interrupted);
        PlayerInfoCapability.get(player).clearSpecial();
    }

    public static ItemStack getActiveSpecialStack(Player player)
    {
        if (!PlayerInfoCapability.hasCapability(player))
            return ItemStack.EMPTY;

        PlayerInfo info = PlayerInfoCapability.get(player);
        if (!info.hasActiveSpecial())
            return ItemStack.EMPTY;

        int sourceSlot = info.getSpecialSourceSlot();
        if (sourceSlot < 0 || sourceSlot >= player.getInventory().getContainerSize())
            return ItemStack.EMPTY;

        ItemStack weaponStack = player.getInventory().getItem(sourceSlot);
        if (!(weaponStack.getItem() instanceof WeaponBaseItem<?>))
            return ItemStack.EMPTY;

        return WeaponBaseItem.getStoredSpecialWeapon(weaponStack);
    }

    public static boolean hasInfiniteInkSpecial(Player player)
    {
        ItemStack specialStack = getActiveSpecialStack(player);
        if (!(specialStack.getItem() instanceof SpecialWeaponItem specialWeapon))
            return false;

        ItemStack weaponStack = player.getInventory().getItem(PlayerInfoCapability.get(player).getSpecialSourceSlot());
        return specialWeapon.grantsInfiniteInk(player.level(), player, specialStack, weaponStack);
    }
}
