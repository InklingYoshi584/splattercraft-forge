package net.splatcraft.forge.items.weapons;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.splatcraft.forge.commands.SuperJumpCommand;
import net.splatcraft.forge.data.capabilities.inkoverlay.InkOverlayCapability;
import net.splatcraft.forge.data.capabilities.inkoverlay.InkOverlayInfo;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfo;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfoCapability;
import net.splatcraft.forge.network.SplatcraftPacketHandler;
import net.splatcraft.forge.network.s2c.UpdateInkOverlayPacket;
import net.splatcraft.forge.network.s2c.UpdatePlayerInfoPacket;
import net.splatcraft.forge.registries.SplatcraftSounds;
import net.splatcraft.forge.util.ColorUtils;
import net.splatcraft.forge.util.InkBlockUtils;
import net.splatcraft.forge.util.InkDamageUtils;
import net.splatcraft.forge.util.InkExplosion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ZipcasterSpecialItem extends SpecialWeaponItem
{
    public static final int POINTS_REQUIRED = 500;
    public static final int ACTIVE_TICKS = 200;
    public static final int ZIP_INK_COST = 6;
    public static final int MIN_ZIP_ACTION_INK = 10;
    public static final int ARMOR_HEALTH = 16;
    public static final int ZIP_RANGE = 80;
    public static final float REEL_MAX_SPEED = 3.2F;
    public static final float REEL_ACCELERATION = 1.0F;
    public static final float REEL_CONTACT_DAMAGE = 16.0F;
    public static final float LANDING_SIZE = 2.8F;
    public static final float LANDING_BLOCK_DAMAGE = 30.0F;
    public static final float LANDING_MAX_DAMAGE = 10.0F;
    public static final float LANDING_MIN_DAMAGE = 6.0F;
    public static final float ANCHOR_SIZE = 2.4F;
    public static final float ANCHOR_BLOCK_DAMAGE = 25.0F;

    public static final int PHASE_IDLE = 0;
    public static final int PHASE_REEL = 1;
    public static final int PHASE_LATCH = 2;
    public static final int PHASE_END_LAG = 3;

    private static final int END_LAG_TICKS = 10;
    private static final int MIN_STARTUP_LAG_TICKS = 2;
    private static final int MAX_STARTUP_LAG_TICKS = 6;

    public static final int LATCH_ACTION_DETACH = 0;
    public static final int LATCH_ACTION_JUMP = 1;

    private static final float WALL_JUMP_BACK_SPEED = 0.55F;
    private static final float WALL_JUMP_UP_SPEED = 0.6F;
    private static final double LATCH_DISTANCE = 0.55D;

    private static final String TAG_ACTIVE = "ZipcasterActive";
    private static final String TAG_PHASE = "ZipcasterPhase";
    private static final String TAG_REEL_SPEED = "ZipcasterReelSpeed";
    private static final String TAG_RECALL = "ZipcasterRecall";
    private static final String TAG_ANCHOR = "ZipcasterAnchor";
    private static final String TAG_TARGET = "ZipcasterTarget";
    private static final String TAG_LATCH_FACE = "ZipcasterLatchFace";
    private static final String TAG_LATCH_POS = "ZipcasterLatchPos";
    private static final String TAG_STARTUP_TICKS = "ZipcasterStartupTicks";
    private static final String TAG_END_LAG_TICKS = "ZipcasterEndLagTicks";

    public ZipcasterSpecialItem()
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

        PlayerInfo info = PlayerInfoCapability.get(player);
        info.startSpecial(player.getInventory().selected, 0, getActiveTicks(specialStack));
        WeaponBaseItem.setSpecialPoints(mainWeapon, getActiveTicks(specialStack));
        WeaponBaseItem.setActiveSpecial(mainWeapon, true);

        CompoundTag data = info.getSpecialData();
        data.putBoolean(TAG_ACTIVE, true);
        data.putInt(TAG_PHASE, PHASE_IDLE);
        setVec3(data, TAG_RECALL, resolveRecallPoint(player));
        data.remove(TAG_ANCHOR);
        data.remove(TAG_TARGET);
        data.putFloat(TAG_REEL_SPEED, 0.0F);
        data.putInt(TAG_STARTUP_TICKS, 0);
        data.putInt(TAG_END_LAG_TICKS, 0);
        player.setNoGravity(false);

        player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), ARMOR_HEALTH));
        updateInkOverlay(player, player.getMaxHealth());
        syncRuntime(player);
        player.displayClientMessage(Component.translatable("status.special.zipcaster"), false);
        return true;
    }

    @Override
    public void onSpecialActiveTick(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, int activeTicksRemaining)
    {
        WeaponBaseItem.setSpecialPoints(mainWeapon, getInk(player));

        if (level.isClientSide || !PlayerInfoCapability.hasCapability(player))
            return;

        if (getPhase(player) == PHASE_REEL)
        {
            player.stopUsingItem();
            player.getCooldowns().addCooldown(mainWeapon.getItem(), 2);
            handleReelTick(player, mainWeapon, specialStack);
        }
        else if (getPhase(player) == PHASE_LATCH)
        {
            handleLatchTick(player);
        }
        else if (getPhase(player) == PHASE_END_LAG)
        {
            handleEndLagTick(player);
        }

        if (level instanceof ServerLevel serverLevel && player.tickCount % 6 == 0)
            ColorUtils.addInkSplashParticle(serverLevel, player, 0.8F);

        if (isEndingSoon(player))
        {
            player.stopUsingItem();
            player.getCooldowns().addCooldown(mainWeapon.getItem(), 2);
        }

        if (getInk(player) <= 0 && shouldDelayRecall(player))
            PlayerInfoCapability.get(player).setSpecialTicksRemaining(1);

        updateInkOverlay(player, player.getMaxHealth());
        syncRuntime(player);
    }

    @Override
    public void onSpecialEnd(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, boolean interrupted)
    {
        player.stopUsingItem();
        player.stopFallFlying();
        player.getAbilities().flying = false;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        WeaponBaseItem.setActiveSpecial(mainWeapon, false);
        WeaponBaseItem.setSpecialPoints(mainWeapon, 0);
        player.setAbsorptionAmount(0.0F);
        player.setNoGravity(false);
        player.noPhysics = false;
        updateInkOverlay(player, 0.0F);

        Vec3 recallPos = getRecallPos(player);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && player.isAlive() && recallPos != null)
            SuperJumpCommand.startForcedSuperJump(serverPlayer, recallPos);
    }

    @Override
    public boolean shouldInterruptActiveSpecial(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return getInk(player) <= 0 && !shouldDelayRecall(player);
    }

    @Override
    protected void appendSpecialTooltip(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.splatcraft.zipcaster.tooltip"));
    }

    public static boolean tryUseZip(Player player, ItemStack mainWeapon)
    {
        if (!canUseZipAction(player))
            return false;

        Vec3 eyePos = player.getEyePosition();
        Vec3 endPos = eyePos.add(player.getLookAngle().scale(ZIP_RANGE));
        BlockHitResult hit = player.level().clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK)
            return false;

        Vec3 anchorPos = hit.getLocation();
        Vec3 targetPos = anchorPos.add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.75D));
        if (player.level().clip(new ClipContext(targetPos, targetPos.add(0.0D, 1.6D, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.BLOCK)
            targetPos = anchorPos;

        releaseLatch(player);

        consumeInk(player, mainWeapon, ZIP_INK_COST);

        CompoundTag data = getRuntimeData(player);
        data.putInt(TAG_PHASE, PHASE_REEL);
        data.putFloat(TAG_REEL_SPEED, 0.0F);
        data.putInt(TAG_STARTUP_TICKS, getStartupLagTicks(player, anchorPos));
        data.putInt(TAG_END_LAG_TICKS, 0);
        setVec3(data, TAG_ANCHOR, anchorPos);
        setVec3(data, TAG_TARGET, targetPos);
        data.putString(TAG_LATCH_FACE, hit.getDirection().getSerializedName());

        player.setNoGravity(true);
        player.noPhysics = true;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), ARMOR_HEALTH));
        updateInkOverlay(player, player.getMaxHealth());

        InkExplosion.createInkExplosion(player.level(), player, BlockPos.containing(anchorPos), ANCHOR_SIZE, ANCHOR_BLOCK_DAMAGE, 0.0F, false, ColorUtils.getPlayerColor(player), InkBlockUtils.getInkType(player), mainWeapon);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SplatcraftSounds.subThrow, SoundSource.PLAYERS, 0.9F, 1.2F);
        syncRuntime(player);
        return true;
    }

    public static boolean isActive(Player player)
    {
        return PlayerInfoCapability.hasCapability(player) && getRuntimeData(player).getBoolean(TAG_ACTIVE);
    }

    public static boolean usesSpecialInk(LivingEntity entity)
    {
        return entity instanceof Player player && isActive(player);
    }

    public static boolean isBusy(Player player)
    {
        int phase = getPhase(player);
        return phase == PHASE_REEL || phase == PHASE_END_LAG;
    }

    public static boolean isActionLocked(Player player)
    {
        return isBusy(player) || isEndingSoon(player);
    }

    public static boolean canUseZipAction(Player player)
    {
        int phase = getPhase(player);
        return isActive(player) && !isEndingSoon(player) && (phase == PHASE_IDLE || phase == PHASE_LATCH) && getInk(player) >= MIN_ZIP_ACTION_INK;
    }

    public static boolean isLatched(Player player)
    {
        return getPhase(player) == PHASE_LATCH;
    }

    public static boolean isEndingSoon(Player player)
    {
        return isActive(player) && getInk(player) <= 2;
    }

    private static boolean shouldDelayRecall(Player player)
    {
        int phase = getPhase(player);
        return phase == PHASE_REEL || phase == PHASE_END_LAG;
    }

    public static int getDisplayRequired(Player player)
    {
        return isActive(player) ? ACTIVE_TICKS : 0;
    }

    public static int getDisplayCurrent(Player player, ItemStack mainWeapon)
    {
        return isActive(player) ? getInk(player) : -1;
    }

    public static int getReducedMainInkCost(float amount)
    {
        return Math.max(1, Mth.ceil(amount * 0.5F));
    }

    public static boolean hasEnoughSpecialInk(LivingEntity entity, float amount)
    {
        return entity instanceof Player player && getInk(player) >= getReducedMainInkCost(amount);
    }

    public static boolean consumeMainWeaponInk(LivingEntity entity, ItemStack mainWeapon, float amount)
    {
        return entity instanceof Player player && consumeInk(player, mainWeapon, getReducedMainInkCost(amount));
    }

    public static boolean refundMainWeaponInk(LivingEntity entity, ItemStack mainWeapon, float amount)
    {
        return entity instanceof Player player && refundInk(player, mainWeapon, getReducedMainInkCost(amount));
    }

    public static int getPhase(Player player)
    {
        return PlayerInfoCapability.hasCapability(player) ? getRuntimeData(player).getInt(TAG_PHASE) : PHASE_IDLE;
    }

    @Nullable
    public static Direction getLatchFace(Player player)
    {
        if (!PlayerInfoCapability.hasCapability(player))
            return null;

        String name = getRuntimeData(player).getString(TAG_LATCH_FACE);
        return name.isEmpty() ? null : Direction.byName(name);
    }

    @Nullable
    public static Vec3 getRecallPos(Player player)
    {
        return PlayerInfoCapability.hasCapability(player) ? getVec3(getRuntimeData(player), TAG_RECALL) : null;
    }

    @Nullable
    public static Vec3 getAnchorPos(Player player)
    {
        return PlayerInfoCapability.hasCapability(player) ? getVec3(getRuntimeData(player), TAG_ANCHOR) : null;
    }

    @Nullable
    public static Vec3 getLatchPos(Player player)
    {
        return PlayerInfoCapability.hasCapability(player) ? getVec3(getRuntimeData(player), TAG_LATCH_POS) : null;
    }

    public static boolean handleLatchAction(Player player, int action)
    {
        if (!isLatched(player))
            return false;

        if (action == LATCH_ACTION_JUMP)
            wallJump(player);
        else clearLatchedState(player);

        syncRuntime(player);
        return true;
    }

    private static void handleReelTick(Player player, ItemStack mainWeapon, ItemStack specialStack)
    {
        Vec3 targetPos = getVec3(getRuntimeData(player), TAG_TARGET);
        Vec3 anchorPos = getAnchorPos(player);
        if (targetPos == null || anchorPos == null)
        {
            clearZipState(player);
            syncRuntime(player);
            return;
        }

        CompoundTag data = getRuntimeData(player);
        int startupTicks = data.getInt(TAG_STARTUP_TICKS);
        if (startupTicks > 0)
        {
            data.putInt(TAG_STARTUP_TICKS, startupTicks - 1);
            player.setNoGravity(true);
            player.noPhysics = true;
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            player.fallDistance = 0.0F;
            return;
        }

        Vec3 center = player.position().add(0.0D, player.getBbHeight() * 0.45D, 0.0D);
        Vec3 offset = targetPos.subtract(center);
        double distance = offset.length();
        if (distance <= 0.9D)
        {
            finishZip(player, mainWeapon, specialStack, anchorPos);
            return;
        }

        float speed = Math.min(REEL_MAX_SPEED, data.getFloat(TAG_REEL_SPEED) + REEL_ACCELERATION);
        data.putFloat(TAG_REEL_SPEED, speed);

        Vec3 velocity = offset.normalize().scale(Math.min(speed, distance));
        player.setNoGravity(true);
        player.noPhysics = true;
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;

        AABB hitBox = player.getBoundingBox().inflate(0.45D).move(velocity);
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, hitBox, EntitySelector.NO_SPECTATORS.and(entity -> entity != player && entity instanceof LivingEntity livingEntity && !InkDamageUtils.isSplatted(livingEntity) && InkDamageUtils.canDamage(entity, player))))
            InkDamageUtils.doRollDamage(player.level(), target, REEL_CONTACT_DAMAGE, ColorUtils.getPlayerColor(player), player, mainWeapon, true);
    }

    private static void finishZip(Player player, ItemStack mainWeapon, ItemStack specialStack, Vec3 anchorPos)
    {
        Direction face = getLatchFace(player);
        CompoundTag data = getRuntimeData(player);
        player.noPhysics = false;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        InkExplosion.createInkExplosion(player.level(), player, BlockPos.containing(anchorPos), LANDING_SIZE, LANDING_BLOCK_DAMAGE, LANDING_MIN_DAMAGE, LANDING_MAX_DAMAGE, true, ColorUtils.getPlayerColor(player), InkBlockUtils.getInkType(player), mainWeapon);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SplatcraftSounds.subDetonate, SoundSource.PLAYERS, 0.9F, 1.0F);

        data.putInt(TAG_PHASE, PHASE_END_LAG);
        data.putInt(TAG_END_LAG_TICKS, END_LAG_TICKS);
        if (face != null && face.getAxis().isHorizontal())
        {
            setVec3(data, TAG_LATCH_POS, anchorPos);
            player.setNoGravity(true);
        }
        else
        {
            clearLatchState(player);
            player.setNoGravity(false);
        }

        syncRuntime(player);
    }

    private static void handleEndLagTick(Player player)
    {
        CompoundTag data = getRuntimeData(player);
        int ticks = Math.max(0, data.getInt(TAG_END_LAG_TICKS) - 1);
        data.putInt(TAG_END_LAG_TICKS, ticks);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;

        if (ticks > 0)
            return;

        Direction face = getLatchFace(player);
        Vec3 latchPos = getLatchPos(player);
        if (face != null && face.getAxis().isHorizontal() && latchPos != null)
            enterLatch(player, latchPos, face);
        else clearZipState(player);
    }

    private static void handleLatchTick(Player player)
    {
        Vec3 latchPos = getLatchPos(player);
        Direction latchFace = getLatchFace(player);
        if (latchPos == null || latchFace == null || !latchFace.getAxis().isHorizontal())
        {
            clearLatchedState(player);
            syncRuntime(player);
            return;
        }

        BlockPos wallPos = BlockPos.containing(latchPos.subtract(Vec3.atLowerCornerOf(latchFace.getNormal()).scale(0.1D)));
        if (player.level().getBlockState(wallPos).getCollisionShape(player.level(), wallPos).isEmpty())
        {
            clearLatchedState(player);
            syncRuntime(player);
            return;
        }

        Vec3 snapped = latchPos.subtract(Vec3.atLowerCornerOf(latchFace.getNormal()).scale(LATCH_DISTANCE));
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(true);
        player.noPhysics = false;
        player.setPos(snapped.x, snapped.y, snapped.z);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
    }

    private static void enterLatch(Player player, Vec3 anchorPos, Direction face)
    {
        CompoundTag data = getRuntimeData(player);
        data.putInt(TAG_PHASE, PHASE_LATCH);
        data.putFloat(TAG_REEL_SPEED, 0.0F);
        data.putString(TAG_LATCH_FACE, face.getSerializedName());
        setVec3(data, TAG_LATCH_POS, anchorPos);
        data.putInt(TAG_STARTUP_TICKS, 0);
        data.putInt(TAG_END_LAG_TICKS, 0);
        data.remove(TAG_TARGET);
        player.setNoGravity(true);
    }

    private static void wallJump(Player player)
    {
        Direction face = getLatchFace(player);
        clearLatchedState(player);
        if (face == null)
            return;

        Vec3 launch = Vec3.atLowerCornerOf(face.getNormal()).scale(-WALL_JUMP_BACK_SPEED).add(0.0D, WALL_JUMP_UP_SPEED, 0.0D);
        player.noPhysics = false;
        player.setDeltaMovement(launch);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
    }

    private static void clearLatchedState(Player player)
    {
        clearZipState(player);
    }

    private static void clearLatchState(Player player)
    {
        CompoundTag data = getRuntimeData(player);
        data.remove(TAG_LATCH_POS);
        data.remove(TAG_LATCH_FACE);
    }

    private static void releaseLatch(Player player)
    {
        clearLatchState(player);
        player.setNoGravity(false);
        player.noPhysics = false;
    }

    private static void clearZipState(Player player)
    {
        CompoundTag data = getRuntimeData(player);
        data.putInt(TAG_PHASE, PHASE_IDLE);
        data.putFloat(TAG_REEL_SPEED, 0.0F);
        data.putInt(TAG_STARTUP_TICKS, 0);
        data.putInt(TAG_END_LAG_TICKS, 0);
        data.remove(TAG_ANCHOR);
        data.remove(TAG_TARGET);
        releaseLatch(player);
    }

    private static int getStartupLagTicks(Player player, Vec3 anchorPos)
    {
        Vec3 armStart = player.position().add(0.0D, player.getBbHeight() * 0.55D, 0.0D);
        float distanceFactor = Mth.clamp((float) (armStart.distanceTo(anchorPos) / ZIP_RANGE), 0.0F, 1.0F);
        return Mth.clamp(Math.round(Mth.lerp(distanceFactor, (float) MIN_STARTUP_LAG_TICKS, (float) MAX_STARTUP_LAG_TICKS)), MIN_STARTUP_LAG_TICKS, MAX_STARTUP_LAG_TICKS);
    }

    private static Vec3 resolveRecallPoint(Player player)
    {
        return SuperJumpCommand.resolveSafeLanding(player, BlockPos.containing(player.position())).orElse(player.position());
    }

    private static boolean consumeInk(Player player, ItemStack mainWeapon, int amount)
    {
        PlayerInfo info = PlayerInfoCapability.get(player);
        int ink = Math.max(0, info.getSpecialTicksRemaining() - amount);
        if (ink <= 0 && info.hasActiveSpecial())
            ink = 1;
        info.setSpecialTicksRemaining(ink);
        WeaponBaseItem.setSpecialPoints(mainWeapon, ink);
        syncRuntime(player);
        return ink >= 0;
    }

    private static boolean refundInk(Player player, ItemStack mainWeapon, int amount)
    {
        PlayerInfo info = PlayerInfoCapability.get(player);
        int ink = Math.min(ACTIVE_TICKS, info.getSpecialTicksRemaining() + amount);
        info.setSpecialTicksRemaining(ink);
        WeaponBaseItem.setSpecialPoints(mainWeapon, ink);
        syncRuntime(player);
        return true;
    }

    private static int getInk(Player player)
    {
        return PlayerInfoCapability.hasCapability(player) ? PlayerInfoCapability.get(player).getSpecialTicksRemaining() : 0;
    }

    private static CompoundTag getRuntimeData(Player player)
    {
        return PlayerInfoCapability.get(player).getSpecialData();
    }

    @Nullable
    private static Vec3 getVec3(CompoundTag data, String key)
    {
        if (!data.contains(key, Tag.TAG_COMPOUND))
            return null;

        CompoundTag vecTag = data.getCompound(key);
        return new Vec3(vecTag.getDouble("X"), vecTag.getDouble("Y"), vecTag.getDouble("Z"));
    }

    private static void setVec3(CompoundTag data, String key, Vec3 value)
    {
        CompoundTag vecTag = new CompoundTag();
        vecTag.putDouble("X", value.x);
        vecTag.putDouble("Y", value.y);
        vecTag.putDouble("Z", value.z);
        data.put(key, vecTag);
    }

    private static void syncRuntime(Player player)
    {
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer)
            SplatcraftPacketHandler.sendToTrackersAndSelf(new UpdatePlayerInfoPacket(serverPlayer), serverPlayer);
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
