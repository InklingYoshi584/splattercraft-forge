package net.splatcraft.forge.entities;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.splatcraft.forge.client.particles.InkSplashParticleData;
import net.splatcraft.forge.registries.SplatcraftEntities;
import net.splatcraft.forge.registries.SplatcraftItems;
import net.splatcraft.forge.util.ColorUtils;
import net.splatcraft.forge.util.InkBlockUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InkstrikeEntity extends ThrowableItemProjectile
{
    private static final double FALL_HEIGHT = 32.0D;
    private static final double LAUNCH_SPEED = 1.45D;
    private static final int LAUNCH_LIFESPAN = 20;

    private UUID ownerUUID;
    private ItemStack sourceWeapon = ItemStack.EMPTY;
    private InkBlockUtils.InkType inkType = InkBlockUtils.InkType.NORMAL;
    private int color;
    private Vec3 targetPos = Vec3.ZERO;
    private boolean cosmeticLaunch;
    private boolean impacted;
    private int travelTicks = InkstrikeProfile.TRIPLE.travelTicks();
    private float tornadoDiameter = InkstrikeProfile.TRIPLE.tornadoDiameter();
    private int tornadoDurationTicks = InkstrikeProfile.TRIPLE.tornadoDurationTicks();
    private float tornadoDamagePerTick = InkstrikeProfile.TRIPLE.damagePerTick();
    private boolean landingIndicatorSpawned;
    private boolean spawnLandingIndicator = true;

    public InkstrikeEntity(EntityType<? extends InkstrikeEntity> type, Level level)
    {
        super(type, level);
    }

    public InkstrikeEntity(Level level, @Nullable LivingEntity owner, @Nullable UUID ownerUUID, ItemStack sourceWeapon, InkBlockUtils.InkType inkType, int color, Vec3 targetPos)
    {
        this(level, owner, ownerUUID, sourceWeapon, inkType, color, targetPos, InkstrikeProfile.TRIPLE);
    }

    public InkstrikeEntity(Level level, @Nullable LivingEntity owner, @Nullable UUID ownerUUID, ItemStack sourceWeapon, InkBlockUtils.InkType inkType, int color, Vec3 targetPos, InkstrikeProfile profile)
    {
        this(SplatcraftEntities.INKSTRIKE.get(), level);
        if (owner != null)
            setOwner(owner);
        this.ownerUUID = owner != null ? owner.getUUID() : ownerUUID;
        this.sourceWeapon = sourceWeapon.copy();
        this.inkType = inkType;
        this.color = color;
        this.targetPos = targetPos;
        this.travelTicks = Math.max(1, profile.travelTicks());
        this.tornadoDiameter = profile.tornadoDiameter();
        this.tornadoDurationTicks = profile.tornadoDurationTicks();
        this.tornadoDamagePerTick = profile.damagePerTick();
        setItem(new ItemStack(SplatcraftItems.inkStrike.get()));
        setPos(targetPos.x, targetPos.y + FALL_HEIGHT, targetPos.z);
    }

    public static InkstrikeEntity createLaunchEffect(Level level, LivingEntity owner, ItemStack sourceWeapon, InkBlockUtils.InkType inkType, int color)
    {
        return createLaunchEffect(level, owner, sourceWeapon, inkType, color, Vec3.ZERO, Vec3.ZERO);
    }

    public static InkstrikeEntity createLaunchEffect(Level level, LivingEntity owner, ItemStack sourceWeapon, InkBlockUtils.InkType inkType, int color, Vec3 positionOffset, Vec3 velocityOffset)
    {
        InkstrikeEntity entity = new InkstrikeEntity(SplatcraftEntities.INKSTRIKE.get(), level);
        entity.ownerUUID = owner.getUUID();
        entity.sourceWeapon = sourceWeapon.copy();
        entity.inkType = inkType;
        entity.color = color;
        entity.cosmeticLaunch = true;
        entity.spawnLandingIndicator = false;
        entity.setOwner(owner);
        entity.setItem(new ItemStack(SplatcraftItems.inkStrike.get()));
        entity.setPos(owner.getX() + positionOffset.x, owner.getEyeY() - 0.15D + positionOffset.y, owner.getZ() + positionOffset.z);
        entity.setDeltaMovement(velocityOffset.x, LAUNCH_SPEED + velocityOffset.y, velocityOffset.z);
        return entity;
    }

    @Override
    protected @NotNull Item getDefaultItem()
    {
        return SplatcraftItems.inkStrike.get();
    }

    @Override
    public float getGravity()
    {
        return 0.0F;
    }

    @Override
    public boolean isNoGravity()
    {
        return true;
    }

    @Override
    public void tick()
    {
        super.tick();

        if (level().isClientSide)
            spawnTrailParticles();

        if (cosmeticLaunch)
        {
            if (tickCount >= LAUNCH_LIFESPAN)
                discard();
            return;
        }

        double progress = Math.min(1.0D, tickCount / (double)Math.max(travelTicks, 1));
        setPos(targetPos.x, targetPos.y + FALL_HEIGHT * (1.0D - progress), targetPos.z);

        if (!level().isClientSide && spawnLandingIndicator && !landingIndicatorSpawned)
        {
            landingIndicatorSpawned = true;
            level().addFreshEntity(new InkstrikeLandingIndicatorEntity(level(), targetPos.x, targetPos.y, targetPos.z, color, travelTicks));
        }

        if (!level().isClientSide && !impacted && tickCount >= travelTicks)
        {
            setPos(targetPos.x, targetPos.y, targetPos.z);
            impact();
        }
    }

    private void spawnTrailParticles()
    {
        float[] rgb = ColorUtils.hexToRGB(color);
        int particleCount = cosmeticLaunch ? 8 : 12;

        for (int i = 0; i < particleCount; i++)
        {
            double x = getX() + (random.nextDouble() - 0.5D) * 0.45D;
            double y = getY() + random.nextDouble() * 0.8D;
            double z = getZ() + (random.nextDouble() - 0.5D) * 0.45D;
            double dx = (random.nextDouble() - 0.5D) * 0.06D;
            double dy = cosmeticLaunch ? 0.08D + random.nextDouble() * 0.05D : 0.01D + random.nextDouble() * 0.04D;
            double dz = (random.nextDouble() - 0.5D) * 0.06D;
            level().addParticle(new InkSplashParticleData(rgb[0], rgb[1], rgb[2], 1.0F + random.nextFloat() * 0.5F), x, y, z, dx, dy, dz);
        }
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result)
    {
        super.onHitEntity(result);
        if (!cosmeticLaunch)
        {
            setPos(result.getLocation().x, result.getLocation().y, result.getLocation().z);
            impact();
        }
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result)
    {
        super.onHitBlock(result);
        if (!cosmeticLaunch)
        {
            setPos(result.getLocation().x, result.getLocation().y, result.getLocation().z);
            impact();
        }
    }

    private void impact()
    {
        if (level().isClientSide || impacted)
            return;

        impacted = true;
        LivingEntity owner = getOwnerEntity();
        InkstrikeTornadoEntity tornado = new InkstrikeTornadoEntity(level(), owner, ownerUUID, sourceWeapon, inkType, color, position(), tornadoDiameter, tornadoDurationTicks, tornadoDamagePerTick);
        level().addFreshEntity(tornado);

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
    public void addAdditionalSaveData(@NotNull CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        if (ownerUUID != null)
            tag.putUUID("Owner", ownerUUID);
        tag.put("SourceWeapon", sourceWeapon.save(new CompoundTag()));
        tag.putString("InkType", inkType.getSerializedName());
        tag.putInt("Color", color);
        tag.putDouble("TargetX", targetPos.x);
        tag.putDouble("TargetY", targetPos.y);
        tag.putDouble("TargetZ", targetPos.z);
        tag.putBoolean("CosmeticLaunch", cosmeticLaunch);
        tag.putBoolean("Impacted", impacted);
        tag.putInt("TravelTicks", travelTicks);
        tag.putFloat("TornadoDiameter", tornadoDiameter);
        tag.putInt("TornadoDurationTicks", tornadoDurationTicks);
        tag.putFloat("TornadoDamagePerTick", tornadoDamagePerTick);
        tag.putBoolean("SpawnLandingIndicator", spawnLandingIndicator);
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
        targetPos = new Vec3(tag.getDouble("TargetX"), tag.getDouble("TargetY"), tag.getDouble("TargetZ"));
        cosmeticLaunch = tag.getBoolean("CosmeticLaunch");
        impacted = tag.getBoolean("Impacted");
        if (tag.contains("TravelTicks"))
            travelTicks = Math.max(1, tag.getInt("TravelTicks"));
        if (tag.contains("TornadoDiameter"))
            tornadoDiameter = tag.getFloat("TornadoDiameter");
        if (tag.contains("TornadoDurationTicks"))
            tornadoDurationTicks = tag.getInt("TornadoDurationTicks");
        if (tag.contains("TornadoDamagePerTick"))
            tornadoDamagePerTick = tag.getFloat("TornadoDamagePerTick");
        spawnLandingIndicator = !tag.contains("SpawnLandingIndicator") || tag.getBoolean("SpawnLandingIndicator");
        setItem(new ItemStack(SplatcraftItems.inkStrike.get()));
    }
}
