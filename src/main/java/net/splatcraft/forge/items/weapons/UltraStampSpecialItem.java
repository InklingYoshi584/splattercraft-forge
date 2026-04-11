package net.splatcraft.forge.items.weapons;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfo;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfoCapability;
import net.splatcraft.forge.entities.SquidBumperEntity;
import net.splatcraft.forge.entities.UltraStampThrownEntity;
import net.splatcraft.forge.handlers.SpecialHandler;
import net.splatcraft.forge.registries.SplatcraftSounds;
import net.splatcraft.forge.util.ColorUtils;
import net.splatcraft.forge.util.InkBlockUtils;
import net.splatcraft.forge.util.InkDamageUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UltraStampSpecialItem extends SpecialWeaponItem
{
    public static final int POINTS_REQUIRED = 500;
    public static final int ACTIVE_TICKS = 140;
    public static final int SWING_TICKS = 6;
    public static final int END_LAG_TICKS = 10;
    public static final int RUSH_INTERVAL_TICKS = 5;
    public static final int THROW_STARTUP_TICKS = 10;

    public static final int PHASE_IDLE = 0;
    public static final int PHASE_SWING = 1;
    public static final int PHASE_END_LAG = 2;
    public static final int PHASE_RUSH = 3;
    public static final int PHASE_THROW_STARTUP = 4;

    private static final float INNER_DAMAGE = 20.0F;
    private static final float OUTER_DAMAGE = 8.0F;
    private static final float THROW_SPEED = 1.75F;
    private static final float RUSH_PUSH = 1.65F;
    private static final float SWING_CENTER_DISTANCE = 3.0F;
    private static final float INNER_HALF_WIDTH = 1.5F;
    private static final float INNER_HALF_HEIGHT = 1.5F;
    private static final float OUTER_HALF_WIDTH = 3.5F;
    private static final float OUTER_HALF_HEIGHT = 2.5F;
    private static final float INNER_HALF_DEPTH = 1.0F;
    private static final float OUTER_HALF_DEPTH = 2.0F;

    private static final String TAG_PHASE = "UltraStampPhase";
    private static final String TAG_PHASE_TICKS = "UltraStampPhaseTicks";
    private static final String TAG_PHASE_MAX_TICKS = "UltraStampPhaseMaxTicks";
    private static final String TAG_CAN_ENTER_RUSH = "UltraStampCanEnterRush";
    private static final String TAG_RENDER_PHASE = "UltraStampRenderPhase";
    private static final String TAG_RENDER_PHASE_TICKS = "UltraStampRenderPhaseTicks";
    private static final String TAG_RENDER_PHASE_MAX_TICKS = "UltraStampRenderPhaseMaxTicks";

    public UltraStampSpecialItem()
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
        WeaponBaseItem.setSpecialPoints(mainWeapon, getPointsRequired(specialStack));
        WeaponBaseItem.setActiveSpecial(mainWeapon, true);
        setCanEnterRush(player, false);
        setPhase(player, mainWeapon, PHASE_IDLE, 0, 0);
        player.displayClientMessage(Component.translatable("status.special.ultra_stamp"), false);
        return true;
    }

    @Override
    public void onSpecialActiveTick(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, int activeTicksRemaining)
    {
        if (!PlayerInfoCapability.hasCapability(player))
            return;

        if (getPhase(player) == PHASE_THROW_STARTUP && activeTicksRemaining <= 0 && getPhaseTicks(player) > 0)
            PlayerInfoCapability.get(player).setSpecialTicksRemaining(1);

        updateDisplayedPoints(player, specialStack, mainWeapon, activeTicksRemaining);

        if (level.isClientSide)
            return;

        boolean holdingUse = player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND && ItemStack.isSameItemSameTags(player.getUseItem(), mainWeapon);
        int phase = getPhase(player);

        switch (phase)
        {
            case PHASE_IDLE ->
            {
                if (holdingUse)
                    startSwing(player, mainWeapon);
            }
            case PHASE_SWING ->
            {
                int phaseTicks = shrinkPhaseTicks(player, mainWeapon);
                if (phaseTicks <= 0)
                {
                    doStampSwing(level, player, specialStack, mainWeapon, false);
                    setCanEnterRush(player, true);
                    setPhase(player, mainWeapon, PHASE_END_LAG, END_LAG_TICKS, END_LAG_TICKS);
                }
            }
            case PHASE_END_LAG ->
            {
                int phaseTicks = shrinkPhaseTicks(player, mainWeapon);
                if (phaseTicks <= 0)
                {
                    if (getCanEnterRush(player) && holdingUse)
                        startRush(level, player, specialStack, mainWeapon);
                    else setPhase(player, mainWeapon, PHASE_IDLE, 0, 0);
                    setCanEnterRush(player, false);
                }
            }
            case PHASE_RUSH ->
            {
                if (!holdingUse)
                {
                    setCanEnterRush(player, false);
                    setPhase(player, mainWeapon, PHASE_END_LAG, END_LAG_TICKS, END_LAG_TICKS);
                    return;
                }

                int phaseTicks = shrinkPhaseTicks(player, mainWeapon);
                if (phaseTicks <= 0)
                {
                    doStampSwing(level, player, specialStack, mainWeapon, true);
                    setPhase(player, mainWeapon, PHASE_RUSH, RUSH_INTERVAL_TICKS, RUSH_INTERVAL_TICKS);
                }
            }
            case PHASE_THROW_STARTUP ->
            {
                int phaseTicks = shrinkPhaseTicks(player, mainWeapon);
                if (phaseTicks <= 0)
                {
                    throwStamp(level, player, mainWeapon);
                    PlayerInfoCapability.get(player).setSpecialTicksRemaining(0);
                }
            }
        }
    }

    @Override
    public void onSpecialEnd(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, boolean interrupted)
    {
        WeaponBaseItem.setSpecialPoints(mainWeapon, 0);
        WeaponBaseItem.setActiveSpecial(mainWeapon, false);
        player.stopUsingItem();
        setCanEnterRush(player, false);
        clearRenderData(mainWeapon);
    }

    @Override
    public boolean replacesMainWeapon(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return true;
    }

    @Override
    protected void appendSpecialTooltip(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.splatcraft.ultra_stamp.tooltip"));
    }

    public static boolean isActive(Player player)
    {
        ItemStack mainWeapon = player.getMainHandItem();
        if (WeaponBaseItem.hasActiveSpecial(mainWeapon) && WeaponBaseItem.getStoredSpecialWeapon(mainWeapon).getItem() instanceof UltraStampSpecialItem)
            return true;

        return SpecialHandler.getActiveSpecialStack(player).getItem() instanceof UltraStampSpecialItem;
    }

    public static boolean tryStartThrow(Player player)
    {
        if (!PlayerInfoCapability.hasCapability(player))
            return false;

        PlayerInfo info = PlayerInfoCapability.get(player);
        int sourceSlot = info.getSpecialSourceSlot();
        if (sourceSlot < 0 || sourceSlot >= player.getInventory().getContainerSize())
            return false;

        ItemStack mainWeapon = player.getInventory().getItem(sourceSlot);
        if (!(WeaponBaseItem.getStoredSpecialWeapon(mainWeapon).getItem() instanceof UltraStampSpecialItem))
            return false;

        if (getPhase(player) == PHASE_THROW_STARTUP)
            return false;

        player.stopUsingItem();
        setCanEnterRush(player, false);
        setPhase(player, mainWeapon, PHASE_THROW_STARTUP, THROW_STARTUP_TICKS, THROW_STARTUP_TICKS);
        return true;
    }

    public static int getRenderPhase(ItemStack stack)
    {
        return stack.hasTag() ? stack.getTag().getInt(TAG_RENDER_PHASE) : PHASE_IDLE;
    }

    public static int getRenderPhaseTicks(ItemStack stack)
    {
        return stack.hasTag() ? stack.getTag().getInt(TAG_RENDER_PHASE_TICKS) : 0;
    }

    public static int getRenderPhaseMaxTicks(ItemStack stack)
    {
        return stack.hasTag() ? stack.getTag().getInt(TAG_RENDER_PHASE_MAX_TICKS) : 0;
    }

    public static boolean isRushPhase(ItemStack stack)
    {
        return getRenderPhase(stack) == PHASE_RUSH;
    }

    private static void updateDisplayedPoints(Player player, ItemStack specialStack, ItemStack mainWeapon, int activeTicksRemaining)
    {
        int points;
        if (getPhase(player) == PHASE_THROW_STARTUP && getPhaseTicks(player) > 0)
            points = Math.max(1, Mth.ceil((float) Math.max(activeTicksRemaining, 1) * POINTS_REQUIRED / Math.max(ACTIVE_TICKS, 1)));
        else points = activeTicksRemaining > 0 ? Math.max(1, Mth.ceil((float) activeTicksRemaining * getPoints(specialStack) / Math.max(ACTIVE_TICKS, 1))) : 0;
        WeaponBaseItem.setSpecialPoints(mainWeapon, points);
    }

    private static int getPoints(ItemStack specialStack)
    {
        return specialStack.getItem() instanceof SpecialWeaponItem specialWeapon ? specialWeapon.getPointsRequired(specialStack) : POINTS_REQUIRED;
    }

    private static void startSwing(Player player, ItemStack mainWeapon)
    {
        setCanEnterRush(player, false);
        setPhase(player, mainWeapon, PHASE_SWING, SWING_TICKS, SWING_TICKS);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static void startRush(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        doStampSwing(level, player, specialStack, mainWeapon, true);
        setPhase(player, mainWeapon, PHASE_RUSH, RUSH_INTERVAL_TICKS, RUSH_INTERVAL_TICKS);
    }

    private static void doStampSwing(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, boolean rush)
    {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SplatcraftSounds.rollerFling, SoundSource.PLAYERS, 0.9F, 0.85F + level.getRandom().nextFloat() * 0.15F);

        if (level.isClientSide)
            return;

        Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot()).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 center = player.position().add(0.0D, 1.0D, 0.0D).add(forward.scale(SWING_CENTER_DISTANCE));

        if (rush)
        {
            if (player.onGround())
                propelForward(player);
            inkRushArea(level, player, center, forward, right);
        }

        AABB outerBox = new AABB(center.x - 3.0D, center.y - 3.0D, center.z - 3.0D, center.x + 3.0D, center.y + 3.0D, center.z + 3.0D);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, outerBox, EntitySelector.NO_SPECTATORS.and(entity ->
        {
            if (!(entity instanceof LivingEntity livingEntity) || livingEntity == player)
                return false;
            if (InkDamageUtils.isSplatted(livingEntity))
                return false;
            return InkDamageUtils.canDamage(entity, player) || entity instanceof SquidBumperEntity;
        })))
        {
            Vec3 relative = target.getBoundingBox().getCenter().subtract(center);
            double depth = relative.dot(forward);
            double width = relative.dot(right);
            double height = relative.y;

            float damage = getSwingDamage(depth, width, height);
            if (damage <= 0.0F)
                continue;

            InkDamageUtils.doSplatDamage(level, target, damage, ColorUtils.getPlayerColor(player), player, specialStack, true);
        }
    }

    private static float getSwingDamage(double depth, double width, double height)
    {
        if (Math.abs(depth) <= INNER_HALF_DEPTH && Math.abs(width) <= INNER_HALF_WIDTH && Math.abs(height) <= INNER_HALF_HEIGHT)
            return INNER_DAMAGE;

        if (Math.abs(depth) <= OUTER_HALF_DEPTH && Math.abs(width) <= OUTER_HALF_WIDTH && Math.abs(height) <= OUTER_HALF_HEIGHT)
            return OUTER_DAMAGE;

        return 0.0F;
    }

    private static void inkRushArea(Level level, Player player, Vec3 center, Vec3 forward, Vec3 right)
    {
        Set<BlockPos> paintedPositions = new HashSet<>();
        int startY = Mth.floor(player.getY() + 2.0D);
        int minY = Mth.floor(player.getY() - 3.0D);
        int widthExtent = Math.max(0, Mth.ceil(OUTER_HALF_WIDTH) - 1);
        int depthExtent = Math.max(0, Mth.ceil(OUTER_HALF_DEPTH) - 1);

        for (int depth = -depthExtent; depth <= depthExtent; depth++)
        {
            for (int width = -widthExtent; width <= widthExtent; width++)
            {
                Vec3 sample = center.add(right.scale(width)).add(forward.scale(depth));
                BlockPos paintPos = findRushPaintPos(level, sample, startY, minY);
                if (paintPos != null && paintedPositions.add(paintPos))
                    InkBlockUtils.playerInkBlock(player, level, paintPos, ColorUtils.getPlayerColor(player), INNER_DAMAGE, InkBlockUtils.getInkType(player));
            }
        }
    }

    @Nullable
    private static BlockPos findRushPaintPos(Level level, Vec3 sample, int startY, int minY)
    {
        int x = Mth.floor(sample.x);
        int z = Mth.floor(sample.z);

        for (int y = startY; y >= minY; y--)
        {
            BlockPos pos = new BlockPos(x, y, z);
            if (!InkBlockUtils.canInkPassthrough(level, pos))
                return pos;
        }

        return null;
    }

    private static void propelForward(Player player)
    {
        Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot()).normalize().scale(RUSH_PUSH);
        Vec3 movement = player.getDeltaMovement();
        player.setDeltaMovement(movement.x + forward.x, movement.y, movement.z + forward.z);
        player.hurtMarked = true;
    }

    private static void throwStamp(Level level, Player player, ItemStack mainWeapon)
    {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 spawnPos = player.position().add(0.0D, 1.1D, 0.0D).add(look.scale(1.85D));
        UltraStampThrownEntity thrownEntity = new UltraStampThrownEntity(level, player, mainWeapon.copy(), InkBlockUtils.getInkType(player), ColorUtils.getPlayerColor(player));
        thrownEntity.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        thrownEntity.shoot(look.x, look.y * 0.65D + 0.2D, look.z, THROW_SPEED, 0.0F);
        level.addFreshEntity(thrownEntity);

        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SplatcraftSounds.subThrow, SoundSource.PLAYERS, 0.9F, 0.95F + level.getRandom().nextFloat() * 0.1F);
    }

    private static CompoundTag getRuntimeData(Player player)
    {
        return PlayerInfoCapability.get(player).getSpecialData();
    }

    private static int getPhase(Player player)
    {
        return PlayerInfoCapability.hasCapability(player) ? getRuntimeData(player).getInt(TAG_PHASE) : PHASE_IDLE;
    }

    private static int getPhaseTicks(Player player)
    {
        return PlayerInfoCapability.hasCapability(player) ? getRuntimeData(player).getInt(TAG_PHASE_TICKS) : 0;
    }

    private static boolean getCanEnterRush(Player player)
    {
        return PlayerInfoCapability.hasCapability(player) && getRuntimeData(player).getBoolean(TAG_CAN_ENTER_RUSH);
    }

    private static void setCanEnterRush(Player player, boolean canEnterRush)
    {
        if (!PlayerInfoCapability.hasCapability(player))
            return;

        CompoundTag data = getRuntimeData(player);
        if (canEnterRush)
            data.putBoolean(TAG_CAN_ENTER_RUSH, true);
        else data.remove(TAG_CAN_ENTER_RUSH);
    }

    private static int shrinkPhaseTicks(Player player, ItemStack mainWeapon)
    {
        if (!PlayerInfoCapability.hasCapability(player))
            return 0;

        CompoundTag data = getRuntimeData(player);
        int phase = data.getInt(TAG_PHASE);
        int maxTicks = data.getInt(TAG_PHASE_MAX_TICKS);
        int phaseTicks = Math.max(0, data.getInt(TAG_PHASE_TICKS) - 1);
        setPhase(player, mainWeapon, phase, phaseTicks, maxTicks);
        return phaseTicks;
    }

    private static void setPhase(Player player, ItemStack mainWeapon, int phase, int phaseTicks, int maxPhaseTicks)
    {
        if (!PlayerInfoCapability.hasCapability(player))
            return;

        CompoundTag data = getRuntimeData(player);
        if (phase == PHASE_IDLE && phaseTicks <= 0 && maxPhaseTicks <= 0)
        {
            data.remove(TAG_PHASE);
            data.remove(TAG_PHASE_TICKS);
            data.remove(TAG_PHASE_MAX_TICKS);
        }
        else
        {
            data.putInt(TAG_PHASE, phase);
            data.putInt(TAG_PHASE_TICKS, Math.max(phaseTicks, 0));
            data.putInt(TAG_PHASE_MAX_TICKS, Math.max(maxPhaseTicks, 0));
        }

        CompoundTag tag = mainWeapon.getOrCreateTag();
        if (phase == PHASE_IDLE && phaseTicks <= 0 && maxPhaseTicks <= 0)
            clearRenderData(mainWeapon);
        else
        {
            tag.putInt(TAG_RENDER_PHASE, phase);
            tag.putInt(TAG_RENDER_PHASE_TICKS, Math.max(phaseTicks, 0));
            tag.putInt(TAG_RENDER_PHASE_MAX_TICKS, Math.max(maxPhaseTicks, 0));
        }
    }

    private static void clearRenderData(ItemStack mainWeapon)
    {
        if (!mainWeapon.hasTag())
            return;

        CompoundTag tag = mainWeapon.getTag();
        tag.remove(TAG_RENDER_PHASE);
        tag.remove(TAG_RENDER_PHASE_TICKS);
        tag.remove(TAG_RENDER_PHASE_MAX_TICKS);
    }
}
