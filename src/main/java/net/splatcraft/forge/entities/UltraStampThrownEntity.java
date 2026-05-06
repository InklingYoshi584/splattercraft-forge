package net.splatcraft.forge.entities;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.splatcraft.forge.client.particles.InkExplosionParticleData;
import net.splatcraft.forge.registries.SplatcraftEntities;
import net.splatcraft.forge.registries.SplatcraftItems;
import net.splatcraft.forge.registries.SplatcraftSounds;
import net.splatcraft.forge.util.ColorUtils;
import net.splatcraft.forge.util.InkBlockUtils;
import net.splatcraft.forge.util.InkExplosion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UltraStampThrownEntity extends ThrowableItemProjectile
{
    private static final float EXPLOSION_SIZE = 1.5F;
    private static final float BLOCK_DAMAGE = 35.0F;
    private static final float SPLASH_DAMAGE = 12.0F;
    private static final float DIRECT_DAMAGE = 35.0F;
    private static final float SPIN_YAW_PER_TICK = 67.5F;
    private static final float SPIN_PITCH_PER_TICK = 43.0F;

    private UUID ownerUUID;
    private ItemStack sourceWeapon = ItemStack.EMPTY;
    private InkBlockUtils.InkType inkType = InkBlockUtils.InkType.NORMAL;
    private int color;
    private boolean impacted;

    public UltraStampThrownEntity(EntityType<? extends UltraStampThrownEntity> type, Level level)
    {
        super(type, level);
    }

    public UltraStampThrownEntity(Level level, LivingEntity owner, ItemStack sourceWeapon, InkBlockUtils.InkType inkType, int color)
    {
        this(SplatcraftEntities.ULTRA_STAMP_THROWN.get(), level);
        setOwner(owner);
        this.ownerUUID = owner.getUUID();
        this.sourceWeapon = sourceWeapon.copy();
        this.inkType = inkType;
        this.color = color;
        setItem(createDisplayStack());
    }

    @Override
    protected @NotNull Item getDefaultItem()
    {
        return SplatcraftItems.ultraStamp.get();
    }

    @Override
    public float getGravity()
    {
        return 0.035F;
    }

    @Override
    public void tick()
    {
        super.tick();

        if (impacted)
            return;

        yRotO = getYRot();
        xRotO = getXRot();
        setYRot(getYRot() + SPIN_YAW_PER_TICK);
        setXRot(getXRot() + SPIN_PITCH_PER_TICK);
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result)
    {
        super.onHitEntity(result);
        Entity entity = result.getEntity();
        Vec3 impactPos = entity instanceof LivingEntity livingEntity ? livingEntity.getBoundingBox().getCenter() : result.getLocation();
        impact(impactPos);
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result)
    {
        super.onHitBlock(result);
        impact(result.getLocation());
    }

    private void impact(Vec3 impactPos)
    {
        if (level().isClientSide || impacted)
            return;

        impacted = true;
        setPos(impactPos.x, impactPos.y, impactPos.z);

        LivingEntity owner = getOwnerEntity();
        InkExplosion.createInkExplosion(level(), owner, BlockPos.containing(impactPos), EXPLOSION_SIZE, BLOCK_DAMAGE, SPLASH_DAMAGE, DIRECT_DAMAGE, true, color, inkType, sourceWeapon);
        level().broadcastEntityEvent(this, (byte) 1);
        level().playSound(null, getX(), getY(), getZ(), SplatcraftSounds.subDetonate, SoundSource.PLAYERS, 0.9F, 0.95F + level().getRandom().nextFloat() * 0.1F);
        discard();
    }

    @Nullable
    private LivingEntity getOwnerEntity()
    {
        Entity owner = getOwner();
        if (owner instanceof LivingEntity livingEntity)
            return livingEntity;

        if (ownerUUID != null && level() instanceof ServerLevel serverLevel)
        {
            Entity resolvedOwner = serverLevel.getEntity(ownerUUID);
            if (resolvedOwner instanceof LivingEntity livingEntity)
                return livingEntity;
        }

        return null;
    }

    @Override
    public void handleEntityEvent(byte id)
    {
        super.handleEntityEvent(id);
        if (id == 1)
            level().addAlwaysVisibleParticle(new InkExplosionParticleData(color, EXPLOSION_SIZE * 2.5F), getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
    }

    @Override
    protected boolean canHitEntity(@NotNull Entity entity)
    {
        return super.canHitEntity(entity) && (!entity.equals(getOwner()) || tickCount > 4);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        if (ownerUUID != null)
            tag.putUUID("Owner", ownerUUID);
        tag.put("SourceWeapon", sourceWeapon.save(new CompoundTag()));
        tag.putString("InkType", inkType.getSerializedName());
        tag.putInt("Color", color);
        tag.putBoolean("Impacted", impacted);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner"))
            ownerUUID = tag.getUUID("Owner");
        if (tag.contains("SourceWeapon"))
            sourceWeapon = ItemStack.of(tag.getCompound("SourceWeapon"));
        if (tag.contains("InkType"))
            inkType = InkBlockUtils.InkType.values.getOrDefault(new ResourceLocation(tag.getString("InkType")), InkBlockUtils.InkType.NORMAL);
        if (tag.contains("Color"))
            color = tag.getInt("Color");
        impacted = tag.getBoolean("Impacted");
        if (tag.contains("Color"))
            setItem(createDisplayStack());
        else if (getItem().isEmpty())
            setItem(new ItemStack(SplatcraftItems.ultraStamp.get()));
    }

    private ItemStack createDisplayStack()
    {
        return ColorUtils.setInkColor(new ItemStack(SplatcraftItems.ultraStamp.get()), color);
    }
}
