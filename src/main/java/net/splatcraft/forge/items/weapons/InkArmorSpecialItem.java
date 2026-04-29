package net.splatcraft.forge.items.weapons;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.splatcraft.forge.data.capabilities.inkoverlay.InkOverlayCapability;
import net.splatcraft.forge.data.capabilities.inkoverlay.InkOverlayInfo;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfo;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfoCapability;
import net.splatcraft.forge.handlers.SpecialHandler;
import net.splatcraft.forge.network.SplatcraftPacketHandler;
import net.splatcraft.forge.network.s2c.UpdateInkOverlayPacket;
import net.splatcraft.forge.util.ColorUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class InkArmorSpecialItem extends SpecialWeaponItem
{
    public static final int POINTS_REQUIRED = 450;
    public static final int WINDUP_TICKS = 40;
    public static final int ACTIVE_TICKS = 200;
    public static final int BREAK_INVINCIBILITY_TICKS = 10;
    public static final float SHIELD_HEALTH = 12.0F;
    public static final float OVERFLOW_DAMAGE_MULTIPLIER = 0.6F;
    public static final float OVERFLOW_DAMAGE_CAP = 16.0F;

    private static final String TAG_BROKEN = "InkArmorBroken";

    public InkArmorSpecialItem()
    {
        super(POINTS_REQUIRED);
    }

    @Override
    public int getWindupTicks(ItemStack stack)
    {
        return WINDUP_TICKS;
    }

    @Override
    public int getActiveTicks(ItemStack stack)
    {
        return ACTIVE_TICKS;
    }

    @Override
    public boolean useSpecial(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        if (!PlayerInfoCapability.hasCapability(player))
            return false;

        PlayerInfoCapability.get(player).startSpecial(player.getInventory().selected, getWindupTicks(specialStack), getActiveTicks(specialStack));
        WeaponBaseItem.setSpecialPoints(mainWeapon, getPointsRequired(specialStack));
        player.displayClientMessage(Component.translatable("status.special.ink_armor"), false);
        return true;
    }

    @Override
    public void onSpecialWindupTick(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, int windupTicksRemaining)
    {
        float progress = 1.0F - (float) windupTicksRemaining / Math.max(getWindupTicks(specialStack), 1);
        updateInkOverlay(player, player.getMaxHealth() * progress);

        if (level instanceof ServerLevel serverLevel && player.tickCount % 4 == 0)
            ColorUtils.addInkSplashParticle(serverLevel, player, 0.9F);

        if (windupTicksRemaining <= 0)
            player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), SHIELD_HEALTH));
    }

    @Override
    public void onSpecialActiveTick(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, int activeTicksRemaining)
    {
        updateInkOverlay(player, !isBroken(player) && player.getAbsorptionAmount() > 0.0F ? player.getMaxHealth() : 0.0F);
        WeaponBaseItem.setSpecialPoints(mainWeapon, Math.max(0, activeTicksRemaining * getPointsRequired(specialStack) / Math.max(getActiveTicks(specialStack), 1)));

        if (level instanceof ServerLevel serverLevel && player.tickCount % 6 == 0)
            ColorUtils.addInkSplashParticle(serverLevel, player, 0.8F);
    }

    @Override
    public void onSpecialEnd(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, boolean interrupted)
    {
        player.setAbsorptionAmount(0);
        updateInkOverlay(player, 0);
        WeaponBaseItem.setSpecialPoints(mainWeapon, 0);
    }

    @Override
    public boolean shouldInterruptActiveSpecial(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return false;
    }

    @Override
    protected void appendSpecialTooltip(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.splatcraft.ink_armor.tooltip"));
    }

    public static boolean isActive(Player player)
    {
        return SpecialHandler.getActiveSpecialStack(player).getItem() instanceof InkArmorSpecialItem;
    }

    public static boolean isArmorDeployed(Player player)
    {
        return PlayerInfoCapability.hasCapability(player)
                && isActive(player)
                && !PlayerInfoCapability.get(player).isSpecialInWindup();
    }

    public static boolean isBroken(Player player)
    {
        return PlayerInfoCapability.hasCapability(player) && getRuntimeData(player).getBoolean(TAG_BROKEN);
    }

    public static void breakArmor(Player player)
    {
        if (!PlayerInfoCapability.hasCapability(player))
            return;

        CompoundTag data = getRuntimeData(player);
        data.putBoolean(TAG_BROKEN, true);
        PlayerInfoCapability.get(player).setInkArmorInvincibilityTicks(BREAK_INVINCIBILITY_TICKS);
        updateInkOverlay(player, 0.0F);
    }

    private static CompoundTag getRuntimeData(Player player)
    {
        PlayerInfo info = PlayerInfoCapability.get(player);
        return info.getSpecialData();
    }

    private static void updateInkOverlay(Player player, float amount)
    {
        if (!InkOverlayCapability.hasCapability(player))
            return;

        InkOverlayInfo info = InkOverlayCapability.get(player);
        info.setColor(ColorUtils.getPlayerColor(player));
        info.setAmount(amount);

        if (!player.level().isClientSide)
            SplatcraftPacketHandler.sendToTrackersAndSelf(new UpdateInkOverlayPacket(player, info), player);
    }
}
