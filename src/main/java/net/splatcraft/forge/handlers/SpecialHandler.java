package net.splatcraft.forge.handlers;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfo;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfoCapability;
import net.splatcraft.forge.items.weapons.InkArmorSpecialItem;
import net.splatcraft.forge.items.weapons.SpecialWeaponItem;
import net.splatcraft.forge.items.weapons.WeaponBaseItem;
import net.splatcraft.forge.network.SplatcraftPacketHandler;
import net.splatcraft.forge.network.s2c.UpdatePlayerInfoPacket;
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
        if (!PlayerInfoCapability.hasCapability(player))
            return;

        PlayerInfo info = PlayerInfoCapability.get(player);
        if (info.hasInkArmorInvincibility())
            info.tickInkArmorInvincibility();

        if (!AbilityAccessUtils.canUseInkAbilities(player))
        {
            ItemStack activeSpecial = getActiveSpecialStack(player);
            if (activeSpecial.getItem() instanceof SpecialWeaponItem specialWeapon)
            {
                int sourceSlot = info.getSpecialSourceSlot();
                if (sourceSlot >= 0 && sourceSlot < player.getInventory().getContainerSize())
                    endSpecial(player, player.getInventory().getItem(sourceSlot), specialWeapon, activeSpecial, true);
            }
            return;
        }

        if (!info.hasActiveSpecial())
            return;

        int sourceSlot = info.getSpecialSourceSlot();
        if (sourceSlot < 0 || sourceSlot >= player.getInventory().getContainerSize())
        {
            ItemStack heldStack = player.getMainHandItem();
            if (heldStack.getItem() instanceof WeaponBaseItem<?>)
                WeaponBaseItem.setActiveSpecial(heldStack, false);
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
            WeaponBaseItem.setActiveSpecial(weaponStack, false);
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
            if (!info.isInfiniteSpecial())
                endSpecial(player, weaponStack, specialWeapon, specialStack, true);
            return;
        }

        if (!info.isInfiniteSpecial())
            info.tickActiveSpecial();
        specialWeapon.onSpecialActiveTick(player.level(), player, specialStack, weaponStack, info.getSpecialTicksRemaining());

        if (info.getSpecialTicksRemaining() <= 0 && !info.isInfiniteSpecial())
            endSpecial(player, weaponStack, specialWeapon, specialStack, false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event)
    {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide || !PlayerInfoCapability.hasCapability(player))
            return;

        PlayerInfo info = PlayerInfoCapability.get(player);
        if (info.hasInkArmorInvincibility() && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(LivingDamageEvent event)
    {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide || !InkArmorSpecialItem.hasInkArmor(player) || InkArmorSpecialItem.isBroken(player))
            return;

        if (player.getAbsorptionAmount() > 0.0F)
            return;

        InkArmorSpecialItem.breakArmor(player);
        if (event.getAmount() > 0.0F)
            event.setAmount(Math.min(event.getAmount() * InkArmorSpecialItem.OVERFLOW_DAMAGE_MULTIPLIER, InkArmorSpecialItem.OVERFLOW_DAMAGE_CAP));
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
        if (!player.level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
            SplatcraftPacketHandler.sendToTrackersAndSelf(new UpdatePlayerInfoPacket(serverPlayer), serverPlayer);
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
