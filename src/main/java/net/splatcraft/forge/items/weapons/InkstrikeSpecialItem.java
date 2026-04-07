package net.splatcraft.forge.items.weapons;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
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
    private static final String TAG_THROWS_REMAINING = "InkstrikeThrowsRemaining";
    private static final String TAG_ACTIVE_SEQUENCES = "InkstrikeActiveSequences";

    public InkstrikeSpecialItem()
    {
        this(POINTS_REQUIRED);
    }

    protected InkstrikeSpecialItem(int pointsRequired)
    {
        super(pointsRequired);
    }

    @Override
    public int getActiveTicks(ItemStack stack)
    {
        return ACTIVE_TICKS;
    }

    protected int getThrowCount(ItemStack stack)
    {
        return 1;
    }

    protected String getStatusMessageKey()
    {
        return "status.special.ink_strike";
    }

    protected String getTooltipKey()
    {
        return "item.splatcraft.ink_strike.tooltip";
    }

    @Override
    public boolean useSpecial(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        if (!PlayerInfoCapability.hasCapability(player))
            return false;

        PlayerInfoCapability.get(player).startSpecial(player.getInventory().selected, 0, getActiveTicks(specialStack));
        WeaponBaseItem.setSpecialPoints(mainWeapon, getPointsRequired(specialStack));
        WeaponBaseItem.setActiveSpecial(mainWeapon, true);
        setThrowsRemaining(player, getThrowCount(specialStack));
        setActiveSequenceCount(player, 0);

        if (!level.isClientSide)
        {
            InkstrikeEntity launchEffect = InkstrikeEntity.createLaunchEffect(level, player, mainWeapon.copy(), InkBlockUtils.getInkType(player), ColorUtils.getPlayerColor(player));
            level.addFreshEntity(launchEffect);
        }

        player.displayClientMessage(Component.translatable(getStatusMessageKey()), false);
        return true;
    }

    @Override
    public void onSpecialActiveTick(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, int activeTicksRemaining)
    {
        int throwsRemaining = getThrowsRemaining(player);
        if (throwsRemaining > 0)
        {
            int throwsTotal = Math.max(1, getThrowCount(specialStack));
            WeaponBaseItem.setSpecialPoints(mainWeapon, Math.max(1, Mth.ceil((float) getPointsRequired(specialStack) * throwsRemaining / throwsTotal)));
            return;
        }

        if (getActiveSequenceCount(player) > 0)
            WeaponBaseItem.setSpecialPoints(mainWeapon, Math.max(1, activeTicksRemaining * getPointsRequired(specialStack) / Math.max(getActiveTicks(specialStack), 1)));
        else WeaponBaseItem.setSpecialPoints(mainWeapon, 0);
    }

    @Override
    public void onSpecialEnd(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, boolean interrupted)
    {
        WeaponBaseItem.setSpecialPoints(mainWeapon, 0);
        WeaponBaseItem.setActiveSpecial(mainWeapon, false);
        player.getCooldowns().removeCooldown(this);
        setThrowsRemaining(player, 0);
        setActiveSequenceCount(player, 0);
    }

    @Override
    public boolean shouldInterruptActiveSpecial(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return getThrowsRemaining(player) <= 0 && getActiveSequenceCount(player) <= 0;
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
        if (!(entity instanceof Player player) || player.getCooldowns().isOnCooldown(this))
            return;

        int throwsRemaining = getThrowsRemaining(player);
        if (throwsRemaining <= 0)
            return;

        player.stopUsingItem();
        if (level.isClientSide)
            return;

        InkstrikeBeaconEntity beacon = new InkstrikeBeaconEntity(level, player, mainWeapon.copy(), InkBlockUtils.getInkType(player), ColorUtils.getPlayerColor(player));
        beacon.shootFromRotation(player, player.getXRot(), player.getYRot(), BEACON_PITCH_OFFSET, BEACON_THROW_SPEED, 0.0F);
        level.addFreshEntity(beacon);

        setThrowsRemaining(player, throwsRemaining - 1);
        setActiveSequenceCount(player, getActiveSequenceCount(player) + 1);
        player.getCooldowns().addCooldown(this, THROW_COOLDOWN);
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SplatcraftSounds.subThrow, SoundSource.PLAYERS, 0.8F, 0.95F + level.getRandom().nextFloat() * 0.1F);
    }

    @Override
    protected void appendSpecialTooltip(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag)
    {
        tooltip.add(Component.translatable(getTooltipKey()));
    }

    private static CompoundTag getRuntimeData(Player player)
    {
        return PlayerInfoCapability.get(player).getSpecialData();
    }

    public static int getThrowsRemaining(Player player)
    {
        return PlayerInfoCapability.hasCapability(player) ? getRuntimeData(player).getInt(TAG_THROWS_REMAINING) : 0;
    }

    public static void setThrowsRemaining(Player player, int throwsRemaining)
    {
        if (!PlayerInfoCapability.hasCapability(player))
            return;

        CompoundTag data = getRuntimeData(player);
        if (throwsRemaining > 0)
            data.putInt(TAG_THROWS_REMAINING, throwsRemaining);
        else data.remove(TAG_THROWS_REMAINING);
    }

    public static int getActiveSequenceCount(Player player)
    {
        return PlayerInfoCapability.hasCapability(player) ? getRuntimeData(player).getInt(TAG_ACTIVE_SEQUENCES) : 0;
    }

    public static void setActiveSequenceCount(Player player, int activeSequenceCount)
    {
        if (!PlayerInfoCapability.hasCapability(player))
            return;

        CompoundTag data = getRuntimeData(player);
        if (activeSequenceCount > 0)
            data.putInt(TAG_ACTIVE_SEQUENCES, activeSequenceCount);
        else data.remove(TAG_ACTIVE_SEQUENCES);
    }

    public static void finishSequence(Level level, UUID ownerUUID)
    {
        if (!(level instanceof ServerLevel serverLevel))
            return;

        Player player = serverLevel.getPlayerByUUID(ownerUUID);
        if (player != null)
            setActiveSequenceCount(player, Math.max(0, getActiveSequenceCount(player) - 1));
    }
}
