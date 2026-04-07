package net.splatcraft.forge.entities;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.splatcraft.forge.registries.SplatcraftEntities;
import net.splatcraft.forge.registries.SplatcraftItems;
import net.splatcraft.forge.util.InkBlockUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InkstrikeBeaconEntity extends ThrowableItemProjectile
{
    private UUID ownerUUID;
    private ItemStack sourceWeapon = ItemStack.EMPTY;
    private InkBlockUtils.InkType inkType = InkBlockUtils.InkType.NORMAL;
    private int color;

    public InkstrikeBeaconEntity(EntityType<? extends InkstrikeBeaconEntity> type, Level level)
    {
        super(type, level);
    }

    public InkstrikeBeaconEntity(Level level, LivingEntity owner, ItemStack sourceWeapon, InkBlockUtils.InkType inkType, int color)
    {
        super(SplatcraftEntities.INKSTRIKE_BEACON.get(), owner, level);
        this.ownerUUID = owner.getUUID();
        this.sourceWeapon = sourceWeapon.copy();
        this.inkType = inkType;
        this.color = color;
        setItem(new ItemStack(SplatcraftItems.inkstrikeBeacon.get()));
    }

    @Override
    protected @NotNull Item getDefaultItem()
    {
        return SplatcraftItems.inkstrikeBeacon.get();
    }

    @Override
    public float getGravity()
    {
        return 0.03F;
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result)
    {
        super.onHitEntity(result);
        spawnInkstrike(result.getLocation());
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result)
    {
        super.onHitBlock(result);
        spawnInkstrike(result.getLocation());
    }

    private void spawnInkstrike(Vec3 hitLocation)
    {
        if (level().isClientSide || isRemoved())
            return;

        LivingEntity owner = getOwnerEntity();
        Vec3 target = resolveTarget(hitLocation);
        InkstrikeEntity inkstrike = new InkstrikeEntity(level(), owner, ownerUUID, sourceWeapon, inkType, color, target);
        level().addFreshEntity(inkstrike);

        discard();
    }

    private Vec3 resolveTarget(Vec3 hitLocation)
    {
        BlockPos pos = BlockPos.containing(hitLocation.x, hitLocation.y, hitLocation.z);

        while (pos.getY() > level().getMinBuildHeight())
        {
            BlockState state = level().getBlockState(pos);
            if (!state.getCollisionShape(level(), pos).isEmpty())
                return new Vec3(hitLocation.x, pos.getY() + 1.05D, hitLocation.z);

            pos = pos.below();
        }

        return new Vec3(hitLocation.x, level().getMinBuildHeight() + 1.0D, hitLocation.z);
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
    public void addAdditionalSaveData(@NotNull CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        if (ownerUUID != null)
            tag.putUUID("Owner", ownerUUID);
        tag.put("SourceWeapon", sourceWeapon.save(new CompoundTag()));
        tag.putString("InkType", inkType.getSerializedName());
        tag.putInt("Color", color);
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
            inkType = InkBlockUtils.InkType.values.getOrDefault(new net.minecraft.resources.ResourceLocation(tag.getString("InkType")), InkBlockUtils.InkType.NORMAL);
        if (tag.contains("Color"))
            color = tag.getInt("Color");
        if (getItem().isEmpty())
            setItem(new ItemStack(SplatcraftItems.inkstrikeBeacon.get()));
    }

    @Override
    protected void onHit(@NotNull HitResult result)
    {
        if (!isRemoved())
            super.onHit(result);
    }
}
