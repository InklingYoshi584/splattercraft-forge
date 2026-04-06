package net.splatcraft.forge.items.weapons;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfoCapability;
import net.splatcraft.forge.entities.InkzookaTornadoEntity;
import net.splatcraft.forge.registries.SplatcraftSounds;
import net.splatcraft.forge.util.InkBlockUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class InkzookaSpecialItem extends SpecialWeaponItem
{
    public static final int POINTS_REQUIRED = 550;
    public static final int ACTIVE_TICKS = 140;
    private static final int SHOT_COOLDOWN = 12;

    public InkzookaSpecialItem()
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
        player.displayClientMessage(Component.translatable("status.special.inkzooka"), false);
        return true;
    }

    @Override
    public void onSpecialActiveTick(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, int activeTicksRemaining)
    {
        WeaponBaseItem.setSpecialPoints(mainWeapon, Math.max(0, activeTicksRemaining * getPointsRequired(specialStack) / Math.max(getActiveTicks(specialStack), 1)));
    }

    @Override
    public void onSpecialEnd(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, boolean interrupted)
    {
        WeaponBaseItem.setSpecialPoints(mainWeapon, 0);
        player.getCooldowns().removeCooldown(this);
    }

    @Override
    public boolean replacesMainWeapon(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return true;
    }

    @Override
    public void onMainWeaponUseTick(Level level, LivingEntity entity, ItemStack specialStack, ItemStack mainWeapon, int timeLeft)
    {
        if (!(entity instanceof Player player) || player.getCooldowns().isOnCooldown(this))
            return;

        player.getCooldowns().addCooldown(this, SHOT_COOLDOWN);
        if (!level.isClientSide)
        {
            InkzookaTornadoEntity tornado = new InkzookaTornadoEntity(level, player, mainWeapon, InkBlockUtils.getInkType(player));
            tornado.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.8F, 0.0F);
            level.addFreshEntity(tornado);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SplatcraftSounds.blasterShot, SoundSource.PLAYERS, 0.9F, 0.8F + level.getRandom().nextFloat() * 0.15F);
        }
    }

    @Override
    protected void appendSpecialTooltip(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.splatcraft.inkzooka.tooltip"));
    }
}
