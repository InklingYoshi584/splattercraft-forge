package net.splatcraft.forge.items.weapons;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfoCapability;
import net.splatcraft.forge.entities.InkstrikeBeaconEntity;
import net.splatcraft.forge.entities.InkstrikeEntity;
import net.splatcraft.forge.registries.SplatcraftSounds;
import net.splatcraft.forge.util.ColorUtils;
import net.splatcraft.forge.util.InkBlockUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InkstrikeSpecialItem extends SpecialWeaponItem
{
    public static final int POINTS_REQUIRED = 550;
    public static final int ACTIVE_TICKS = 600;
    private static final int THROW_COOLDOWN = 8;
    private static final float BEACON_PITCH_OFFSET = -4.0F;
    private static final float BEACON_THROW_SPEED = 2.25F;
    private static final String TAG_FIRED = "InkstrikeFired";
    private static final String TAG_TRACKED_ENTITY = "InkstrikeTrackedEntity";

    public InkstrikeSpecialItem()
    {
        super(POINTS_REQUIRED);
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

        PlayerInfoCapability.get(player).startSpecial(player.getInventory().selected, 0, getActiveTicks(specialStack));
        WeaponBaseItem.setSpecialPoints(mainWeapon, getPointsRequired(specialStack));
        WeaponBaseItem.setActiveSpecial(mainWeapon, true);
        setFired(player, false);
        trackSequenceEntity(player, null);

        if (!level.isClientSide)
        {
            InkstrikeEntity launchEffect = InkstrikeEntity.createLaunchEffect(level, player, mainWeapon.copy(), InkBlockUtils.getInkType(player), ColorUtils.getPlayerColor(player));
            level.addFreshEntity(launchEffect);
        }

        player.displayClientMessage(Component.translatable("status.special.ink_strike"), false);
        return true;
    }

    @Override
    public void onSpecialActiveTick(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, int activeTicksRemaining)
    {
        if (!hasFired(player))
        {
            WeaponBaseItem.setSpecialPoints(mainWeapon, getPointsRequired(specialStack));
            return;
        }

        WeaponBaseItem.setSpecialPoints(mainWeapon, Math.max(0, activeTicksRemaining * getPointsRequired(specialStack) / Math.max(getActiveTicks(specialStack), 1)));
    }

    @Override
    public void onSpecialEnd(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, boolean interrupted)
    {
        if (interrupted)
        {
            Entity trackedEntity = getTrackedSequenceEntity(level, player);
            if (trackedEntity != null)
                trackedEntity.discard();
        }

        WeaponBaseItem.setSpecialPoints(mainWeapon, 0);
        WeaponBaseItem.setActiveSpecial(mainWeapon, false);
        player.getCooldowns().removeCooldown(this);
        trackSequenceEntity(player, null);
        setFired(player, false);
    }

    @Override
    public boolean shouldInterruptActiveSpecial(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return hasFired(player) && getTrackedSequenceEntity(level, player) == null;
    }

    @Override
    public boolean replacesMainWeapon(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return true;
    }

    @Override
    public ItemStack getMainWeaponReplacementRenderStack(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return new ItemStack(net.splatcraft.forge.registries.SplatcraftItems.inkstrikeBeacon.get());
    }

    @Override
    public void onMainWeaponUseTick(Level level, LivingEntity entity, ItemStack specialStack, ItemStack mainWeapon, int timeLeft)
    {
        if (!(entity instanceof Player player) || player.getCooldowns().isOnCooldown(this) || hasFired(player))
            return;

        player.stopUsingItem();
        if (level.isClientSide)
            return;

        InkstrikeBeaconEntity beacon = new InkstrikeBeaconEntity(level, player, mainWeapon.copy(), InkBlockUtils.getInkType(player), ColorUtils.getPlayerColor(player));
        beacon.shootFromRotation(player, player.getXRot(), player.getYRot(), BEACON_PITCH_OFFSET, BEACON_THROW_SPEED, 0.0F);
        level.addFreshEntity(beacon);

        setFired(player, true);
        trackSequenceEntity(player, beacon);
        player.getCooldowns().addCooldown(this, THROW_COOLDOWN);
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SplatcraftSounds.subThrow, SoundSource.PLAYERS, 0.8F, 0.95F + level.getRandom().nextFloat() * 0.1F);
    }

    @Override
    protected void appendSpecialTooltip(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.splatcraft.ink_strike.tooltip"));
    }

    private static CompoundTag getRuntimeData(Player player)
    {
        return PlayerInfoCapability.get(player).getSpecialData();
    }

    public static boolean hasFired(Player player)
    {
        return PlayerInfoCapability.hasCapability(player) && getRuntimeData(player).getBoolean(TAG_FIRED);
    }

    public static void setFired(Player player, boolean fired)
    {
        if (!PlayerInfoCapability.hasCapability(player))
            return;

        CompoundTag data = getRuntimeData(player);
        if (fired)
            data.putBoolean(TAG_FIRED, true);
        else
            data.remove(TAG_FIRED);
    }

    public static void trackSequenceEntity(Player player, @Nullable Entity trackedEntity)
    {
        if (!PlayerInfoCapability.hasCapability(player))
            return;

        CompoundTag data = getRuntimeData(player);
        if (trackedEntity != null)
            data.putUUID(TAG_TRACKED_ENTITY, trackedEntity.getUUID());
        else
            data.remove(TAG_TRACKED_ENTITY);
    }

    public static void trackSequenceEntity(Level level, UUID ownerUUID, @Nullable Entity trackedEntity)
    {
        if (!(level instanceof ServerLevel serverLevel))
            return;

        Player player = serverLevel.getPlayerByUUID(ownerUUID);
        if (player != null)
            trackSequenceEntity(player, trackedEntity);
    }

    @Nullable
    public static Entity getTrackedSequenceEntity(Level level, Player player)
    {
        if (!(level instanceof ServerLevel serverLevel) || !PlayerInfoCapability.hasCapability(player))
            return null;

        CompoundTag data = getRuntimeData(player);
        if (!data.hasUUID(TAG_TRACKED_ENTITY))
            return null;

        Entity trackedEntity = serverLevel.getEntity(data.getUUID(TAG_TRACKED_ENTITY));
        return trackedEntity != null && trackedEntity.isAlive() && !trackedEntity.isRemoved() ? trackedEntity : null;
    }
}
