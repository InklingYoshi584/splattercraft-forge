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
import net.splatcraft.forge.entities.InkstrikeProfile;
import net.splatcraft.forge.registries.SplatcraftSounds;
import net.splatcraft.forge.network.SplatcraftPacketHandler;
import net.splatcraft.forge.network.s2c.UpdatePlayerInfoPacket;
import net.splatcraft.forge.util.ColorUtils;
import net.splatcraft.forge.util.InkBlockUtils;
import net.splatcraft.forge.util.PlayerCooldown;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InkstrikeSpecialItem extends SpecialWeaponItem
{
    public static final int POINTS_REQUIRED = 550;
    public static final int ACTIVE_TICKS = 600;
    private static final int THROW_COOLDOWN = 8;
    private static final float BEACON_PITCH_OFFSET = -4.0F;
    private static final float BEACON_THROW_SPEED = 0.8F;
    public static final int TACTICAL_RADIUS = 100;
    private static final int LAUNCH_LOCK_TICKS = 20;
    private static final String TAG_THROWS_REMAINING = "InkstrikeThrowsRemaining";
    private static final String TAG_ACTIVE_SEQUENCES = "InkstrikeActiveSequences";
    private static final String TAG_CENTER_X = "InkstrikeCenterX";
    private static final String TAG_CENTER_Z = "InkstrikeCenterZ";
    private static final String TAG_PENDING_X = "InkstrikePendingX";
    private static final String TAG_PENDING_Y = "InkstrikePendingY";
    private static final String TAG_PENDING_Z = "InkstrikePendingZ";
    private static final String TAG_PENDING_LAUNCH_TICKS = "InkstrikePendingLaunchTicks";

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
        setTargetCenter(player, player.getBlockX(), player.getBlockZ());
        clearPendingLaunch(player);

        player.displayClientMessage(Component.translatable(getStatusMessageKey()), false);
        return true;
    }

    @Override
    public void onSpecialActiveTick(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, int activeTicksRemaining)
    {
        if (!level.isClientSide)
            tickPendingLaunch(level, player, mainWeapon);

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
        clearPendingLaunch(player);
        clearTargetCenter(player);
    }

    @Override
    public boolean shouldInterruptActiveSpecial(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return getThrowsRemaining(player) <= 0 && getActiveSequenceCount(player) <= 0 && !hasPendingLaunch(player);
    }

    @Override
    public boolean replacesMainWeapon(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return getThrowsRemaining(player) > 0 || hasPendingLaunch(player);
    }

    @Override
    public ItemStack getMainWeaponReplacementRenderStack(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return new ItemStack(net.splatcraft.forge.registries.SplatcraftItems.inkstrikeBeacon.get());
    }

    @Override
    public void onMainWeaponUseTick(Level level, LivingEntity entity, ItemStack specialStack, ItemStack mainWeapon, int timeLeft)
    {
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

    protected InkstrikeProfile getProfile(ItemStack specialStack)
    {
        return InkstrikeProfile.SINGLE;
    }

    protected void launchBeacon(Level level, Player player, ItemStack mainWeapon)
    {
        if (player.getCooldowns().isOnCooldown(this))
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

    public static boolean tryQueueTarget(Player player, ItemStack mainWeapon, int worldX, int worldZ)
    {
        if (!(mainWeapon.getItem() instanceof WeaponBaseItem<?>))
            return false;

        ItemStack specialStack = WeaponBaseItem.getStoredSpecialWeapon(mainWeapon);
        if (!(specialStack.getItem() instanceof InkstrikeSpecialItem inkstrike) || specialStack.getItem() instanceof TripleInkstrikeSpecialItem)
            return false;

        if (getThrowsRemaining(player) <= 0 || hasPendingLaunch(player) || !hasTargetCenter(player))
            return false;

        int centerX = getTargetCenterX(player);
        int centerZ = getTargetCenterZ(player);
        double dx = worldX - centerX;
        double dz = worldZ - centerZ;
        double distanceSq = dx * dx + dz * dz;
        if (distanceSq > TACTICAL_RADIUS * TACTICAL_RADIUS)
        {
            double scale = TACTICAL_RADIUS / Math.sqrt(distanceSq);
            dx *= scale;
            dz *= scale;
        }

        int clampedX = Mth.floor(centerX + dx);
        int clampedZ = Mth.floor(centerZ + dz);
        net.minecraft.world.phys.Vec3 target = InkstrikeBeaconEntity.resolveTargetAt(player.level(), clampedX, clampedZ);
        CompoundTag data = getRuntimeData(player);
        data.putDouble(TAG_PENDING_X, target.x);
        data.putDouble(TAG_PENDING_Y, target.y);
        data.putDouble(TAG_PENDING_Z, target.z);
        data.putInt(TAG_PENDING_LAUNCH_TICKS, LAUNCH_LOCK_TICKS);
        setThrowsRemaining(player, 0);
        setActiveSequenceCount(player, getActiveSequenceCount(player) + 1);
        PlayerCooldown.setPlayerCooldown(player, new PlayerCooldown(mainWeapon.copy(), LAUNCH_LOCK_TICKS, player.getInventory().selected, InteractionHand.MAIN_HAND, false, false, true, player.onGround()));
        syncRuntime(player);
        return true;
    }

    private void tickPendingLaunch(Level level, Player player, ItemStack mainWeapon)
    {
        if (!hasPendingLaunch(player))
            return;

        CompoundTag data = getRuntimeData(player);
        int ticks = data.getInt(TAG_PENDING_LAUNCH_TICKS) - 1;
        if (ticks > 0)
        {
            data.putInt(TAG_PENDING_LAUNCH_TICKS, ticks);
            return;
        }

        ItemStack specialStack = WeaponBaseItem.getStoredSpecialWeapon(mainWeapon);
        if (!(specialStack.getItem() instanceof InkstrikeSpecialItem inkstrike))
        {
            clearPendingLaunch(player);
            return;
        }

        net.minecraft.world.phys.Vec3 target = new net.minecraft.world.phys.Vec3(data.getDouble(TAG_PENDING_X), data.getDouble(TAG_PENDING_Y), data.getDouble(TAG_PENDING_Z));
        clearPendingLaunch(player);
        InkstrikeProfile profile = inkstrike.getProfile(specialStack);
        InkstrikeEntity launchEffect = InkstrikeEntity.createLaunchEffect(level, player, mainWeapon.copy(), InkBlockUtils.getInkType(player), ColorUtils.getPlayerColor(player));
        level.addFreshEntity(launchEffect);
        level.addFreshEntity(new InkstrikeEntity(level, player, player.getUUID(), mainWeapon.copy(), InkBlockUtils.getInkType(player), ColorUtils.getPlayerColor(player), target, profile));
        syncRuntime(player);
    }

    public static boolean hasTargetCenter(Player player)
    {
        return getRuntimeData(player).contains(TAG_CENTER_X) && getRuntimeData(player).contains(TAG_CENTER_Z);
    }

    public static int getTargetCenterX(Player player)
    {
        return getRuntimeData(player).getInt(TAG_CENTER_X);
    }

    public static int getTargetCenterZ(Player player)
    {
        return getRuntimeData(player).getInt(TAG_CENTER_Z);
    }

    private static void setTargetCenter(Player player, int x, int z)
    {
        CompoundTag data = getRuntimeData(player);
        data.putInt(TAG_CENTER_X, x);
        data.putInt(TAG_CENTER_Z, z);
    }

	protected static void clearTargetCenter(Player player)
    {
        CompoundTag data = getRuntimeData(player);
        data.remove(TAG_CENTER_X);
        data.remove(TAG_CENTER_Z);
    }

    public static boolean hasPendingLaunch(Player player)
    {
        return getRuntimeData(player).contains(TAG_PENDING_LAUNCH_TICKS);
    }

    private static void clearPendingLaunch(Player player)
    {
        CompoundTag data = getRuntimeData(player);
        data.remove(TAG_PENDING_X);
        data.remove(TAG_PENDING_Y);
        data.remove(TAG_PENDING_Z);
        data.remove(TAG_PENDING_LAUNCH_TICKS);
    }

    private static void syncRuntime(Player player)
    {
        if (!player.level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
            SplatcraftPacketHandler.sendToTrackersAndSelf(new UpdatePlayerInfoPacket(serverPlayer), serverPlayer);
    }
}
