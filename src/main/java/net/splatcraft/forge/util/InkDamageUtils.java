package net.splatcraft.forge.util;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.splatcraft.forge.commands.SuperJumpCommand;
import net.splatcraft.forge.data.capabilities.inkoverlay.InkOverlayCapability;
import net.splatcraft.forge.data.capabilities.inkoverlay.InkOverlayInfo;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfo;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfoCapability;
import net.splatcraft.forge.entities.IColoredEntity;
import net.splatcraft.forge.entities.SpawnShieldEntity;
import net.splatcraft.forge.entities.SquidBumperEntity;
import net.splatcraft.forge.network.SplatcraftPacketHandler;
import net.splatcraft.forge.network.s2c.UpdateInkOverlayPacket;
import net.splatcraft.forge.registries.SplatcraftDamageTypes;
import net.splatcraft.forge.registries.SplatcraftGameRules;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InkDamageUtils {
    public static boolean doSplatDamage(Level level, LivingEntity target, float damage, int color, Entity source, ItemStack sourceItem, boolean damageMobs) {
        return doDamage(level, target, damage, color, source, source, sourceItem, damageMobs, SplatcraftDamageTypes.INK_SPLAT, false);
    }

    public static boolean doRollDamage(Level level, LivingEntity target, float damage, int color, Entity source, ItemStack sourceItem, boolean damageMobs)
    {
        return doRollDamage(level, target, damage, color, source, source, sourceItem, damageMobs);
    }

    public static boolean doRollDamage(Level level, LivingEntity target, float damage, int color, Entity source, Entity directSource, ItemStack sourceItem, boolean damageMobs) {
        return doDamage(level, target, damage, color, directSource, source, sourceItem, damageMobs, SplatcraftDamageTypes.ROLL_CRUSH, true);
    }

    public static boolean canDamage(Entity target, Entity source)
    {
        return canDamage(target, ColorUtils.getEntityColor(source));
    }

    public static boolean canDamage(Entity target, int color)
    {
        boolean result = canDamageColor(target.level(), target.blockPosition(), ColorUtils.getEntityColor(target), color);

        if(result)
            for (SpawnShieldEntity shield : target.level().getEntitiesOfClass(SpawnShieldEntity.class, target.getBoundingBox()))
                if (ColorUtils.colorEquals(target.level(), target.blockPosition(), ColorUtils.getEntityColor(shield), ColorUtils.getEntityColor(target)))
                    return false;

        return result;
    }

    public static boolean canDamageColor(Level level, BlockPos pos, int targetColor, int sourceColor) {
        return SplatcraftGameRules.getLocalizedRule(level, pos, SplatcraftGameRules.INK_FRIENDLY_FIRE) || !ColorUtils.colorEquals(level, pos, targetColor, sourceColor);
    }

    public static boolean doDamage(Level level, LivingEntity target, float damage, int color, Entity source, Entity directSource, ItemStack sourceItem, boolean damageMobs, ResourceKey<DamageType> damageTypeKey, boolean applyHurtCooldown)
    {
        //Negate ink damage when super jumping
        if(PlayerInfoCapability.hasCapability(target))
        {
            PlayerInfo info = PlayerInfoCapability.get(target);
            if(info.hasPlayerCooldown() && info.getPlayerCooldown() instanceof SuperJumpCommand.SuperJump)
                return false;
        }

        InkDamageSource damageSource = new InkDamageSource(SplatcraftDamageTypes.get(level, damageTypeKey), directSource, source, sourceItem);
        if (damage <= 0 || (target.isInvulnerableTo(damageSource) && !(target instanceof SquidBumperEntity)))
            return false;

        if(InkOverlayCapability.hasCapability(target) && InkOverlayCapability.get(target).isInkproof())
            return false;

        float mobDmgPctg = SplatcraftGameRules.getIntRuleValue(level, SplatcraftGameRules.INK_MOB_DAMAGE_PERCENTAGE) * 0.01f;

        int targetColor = ColorUtils.getEntityColor(target);
        boolean doDamage = target instanceof Player || damageMobs || mobDmgPctg > 0;
        boolean canInk = canDamage(target, color);

        if (targetColor > -1) {
            doDamage = canInk;
        }

        if (target instanceof IColoredEntity) {
            target.invulnerableTime = (!applyHurtCooldown && !SplatcraftGameRules.getBooleanRuleValue(level, SplatcraftGameRules.INK_DAMAGE_COOLDOWN)) ? 1 : 20;
            doDamage = ((IColoredEntity) target).onEntityInked(damageSource, damage, color);
        } else if (target instanceof Sheep) {
            if (!((Sheep) target).isSheared()) {
                doDamage = false;
                canInk = false;
                targetColor = 1;

                InkOverlayInfo info = InkOverlayCapability.get(target);

                info.setWoolColor(color);
                if (!level.isClientSide()) {
                    SplatcraftPacketHandler.sendToTrackers(new UpdateInkOverlayPacket(target, info), target);
                }
            }
        }

        if (!(target instanceof SquidBumperEntity) && doDamage) {
            Vec3 deltaMovement = target.getDeltaMovement();
            doDamage = target.hurt(damageSource, damage * (target instanceof Player || target instanceof IColoredEntity || damageMobs ? 1 : mobDmgPctg));
            target.setDeltaMovement(deltaMovement); // trying to prevent knockback... (this game is so dumb)
            target.hurtMarked = false;
        }

        if ((targetColor <= -1 || canInk) && !target.isInWater() && !(target instanceof IColoredEntity && !((IColoredEntity) target).handleInkOverlay())) {
            if (InkOverlayCapability.hasCapability(target))
            {
                InkOverlayInfo info = InkOverlayCapability.get(target);
                if (info.getAmount() < target.getMaxHealth() * 1.5)
                    info.addAmount(damage * (target instanceof IColoredEntity || damageMobs ? 1 : Math.max(0.5f, mobDmgPctg)));
                info.setColor(color);
                if (!level.isClientSide()) {
                    SplatcraftPacketHandler.sendToTrackersAndSelf(new UpdateInkOverlayPacket(target, info), target);
                }

            }
        }

        if (!applyHurtCooldown && !SplatcraftGameRules.getBooleanRuleValue(level, SplatcraftGameRules.INK_DAMAGE_COOLDOWN))
            target.hurtTime = 1;

        return doDamage;
    }

    public static boolean isSplatted(LivingEntity target) {
        return target instanceof SquidBumperEntity ? ((SquidBumperEntity) target).getInkHealth() <= 0 : target.isDeadOrDying();
    }

    public static class InkDamageSource extends DamageSource
    {
        private final ItemStack weapon;

        public InkDamageSource(Holder<DamageType> pType, @Nullable Entity pDirectEntity, @Nullable Entity pCausingEntity, ItemStack weapon) {
            super(pType, pDirectEntity, pCausingEntity);
            this.weapon = weapon;
        }

        @Override
        public @NotNull Component getLocalizedDeathMessage(@NotNull LivingEntity entityLivingBaseIn)
        {
            String base = "death.attack." + this.type().msgId();

            if (getEntity() == null && getDirectEntity() == null)
            {
                return !weapon.isEmpty() ? Component.translatable(base + ".item", entityLivingBaseIn.getDisplayName(), weapon.getDisplayName()) : Component.translatable(base, entityLivingBaseIn.getDisplayName());
            }
            base += ".player";

            Component itextcomponent = this.getEntity() == null ? Objects.requireNonNull(this.getDirectEntity()).getDisplayName() : this.getEntity().getDisplayName();

            return !weapon.isEmpty() ? Component.translatable(base + ".item", entityLivingBaseIn.getDisplayName(), itextcomponent, weapon.getDisplayName()) : Component.translatable(base, entityLivingBaseIn.getDisplayName(), itextcomponent);
        }
    }
}