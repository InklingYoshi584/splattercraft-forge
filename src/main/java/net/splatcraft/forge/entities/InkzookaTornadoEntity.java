package net.splatcraft.forge.entities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.splatcraft.forge.client.particles.InkSplashParticleData;
import net.splatcraft.forge.util.ColorUtils;
import net.splatcraft.forge.util.InkBlockUtils;
import net.splatcraft.forge.util.InkDamageUtils;
import net.splatcraft.forge.util.InkExplosion;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class InkzookaTornadoEntity extends Entity implements IColoredEntity
{
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(InkzookaTornadoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(InkzookaTornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(InkzookaTornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final float TORNADO_WIDTH = 0.5F;
    private static final float TORNADO_HEIGHT = 20.0F;
    private static final float IMPACT_DAMAGE = 45.0F;

    private final Set<UUID> hitTargets = new HashSet<>();
    private UUID ownerUUID;
    private ItemStack sourceWeapon = ItemStack.EMPTY;
    private InkBlockUtils.InkType inkType = InkBlockUtils.InkType.NORMAL;
    private int lifespan = 24;

    public InkzookaTornadoEntity(EntityType<? extends InkzookaTornadoEntity> type, Level level)
    {
        super(type, level);
    }

    public InkzookaTornadoEntity(Level level, LivingEntity owner, ItemStack sourceWeapon, InkBlockUtils.InkType inkType)
    {
        this(net.splatcraft.forge.registries.SplatcraftEntities.INKZOOKA_TORNADO.get(), level);
        this.ownerUUID = owner.getUUID();
        this.sourceWeapon = sourceWeapon.copy();
        this.inkType = inkType;
        this.setPos(owner.getX(), owner.getEyeY() - 10.0D, owner.getZ());
        this.setColor(ColorUtils.getInkColor(sourceWeapon));
    }

    @Override
    protected void defineSynchedData()
    {
        entityData.define(COLOR, ColorUtils.DEFAULT);
        entityData.define(WIDTH, TORNADO_WIDTH);
        entityData.define(HEIGHT, TORNADO_HEIGHT);
    }

    @Override
    public void tick()
    {
        super.tick();
        Vec3 motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);

        if (level().isClientSide)
        {
            spawnParticles();
        }
        else
        {
            inkPath();
            damageEntities();
        }

        if (lifespan-- <= 0)
            discard();
    }

    private void spawnParticles()
    {
        float[] rgb = ColorUtils.hexToRGB(getColor());
        for (int i = 0; i < 18; i++)
        {
            double progress = (double) i / 18.0D;
            double swirl = random.nextDouble() * Math.PI * 2.0D + tickCount * 0.45D + progress * Math.PI * 3.0D;
            double radius = TORNADO_WIDTH * (0.65D + random.nextDouble() * 0.8D);
            double x = getX() + Math.cos(swirl) * radius;
            double y = getY() + progress * TORNADO_HEIGHT + random.nextDouble() * 1.5D;
            double z = getZ() + Math.sin(swirl) * radius;
            double dx = -Math.sin(swirl) * 0.08D;
            double dz = Math.cos(swirl) * 0.08D;
            level().addParticle(new InkSplashParticleData(rgb[0], rgb[1], rgb[2], 1.1F + random.nextFloat() * 0.5F), x, y, z, dx, 0.1D + random.nextDouble() * 0.08D, dz);
        }

        for (int i = 0; i < 6; i++)
        {
            double x = getX() + (random.nextDouble() - 0.5D) * TORNADO_WIDTH * 2.4D;
            double y = getY() + random.nextDouble() * 2.0D;
            double z = getZ() + (random.nextDouble() - 0.5D) * TORNADO_WIDTH * 2.4D;
            level().addParticle(new InkSplashParticleData(rgb[0], rgb[1], rgb[2], 1.8F + random.nextFloat() * 0.5F), x, y, z, 0.0D, 0.16D, 0.0D);
        }
    }

    private void inkPath()
    {
        LivingEntity owner = getOwner();
        for (int y = 0; y < TORNADO_HEIGHT; y += 2)
            InkExplosion.createInkExplosion(level(), owner, blockPosition().above(y), 0.75F, 0.8F, 0.0F, false, getColor(), inkType, sourceWeapon);
    }

    private void damageEntities()
    {
        LivingEntity owner = getOwner();
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.2D), entity -> entity.isAlive() && entity != owner))
        {
            if (!hitTargets.add(target.getUUID()))
                continue;

            InkDamageUtils.doSplatDamage(level(), target, IMPACT_DAMAGE, getColor(), owner != null ? owner : this, sourceWeapon, true);
        }
    }

    public void shootFromRotation(Entity thrower, float pitch, float yaw, float pitchOffset, float velocity, float inaccuracy)
    {
        Vec3 look = thrower.getLookAngle().normalize().scale(velocity);
        setDeltaMovement(look);
        setYRot(thrower.getYRot());
        setXRot(thrower.getXRot());
    }

    public LivingEntity getOwner()
    {
        if (ownerUUID == null || !(level() instanceof net.minecraft.server.level.ServerLevel serverLevel))
            return null;

        Entity entity = serverLevel.getEntity(ownerUUID);
        return entity instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag)
    {
        if (tag.hasUUID("Owner"))
            ownerUUID = tag.getUUID("Owner");
        if (tag.contains("SourceWeapon"))
            sourceWeapon = ItemStack.of(tag.getCompound("SourceWeapon"));
        if (tag.contains("InkType"))
            inkType = InkBlockUtils.InkType.values.getOrDefault(new net.minecraft.resources.ResourceLocation(tag.getString("InkType")), InkBlockUtils.InkType.NORMAL);
        if (tag.contains("Lifespan"))
            lifespan = tag.getInt("Lifespan");
        if (tag.contains("Color"))
            setColor(ColorUtils.getColorFromNbt(tag));
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag)
    {
        if (ownerUUID != null)
            tag.putUUID("Owner", ownerUUID);
        tag.put("SourceWeapon", sourceWeapon.save(new CompoundTag()));
        tag.putString("InkType", inkType.getSerializedName());
        tag.putInt("Lifespan", lifespan);
        tag.putInt("Color", getColor());
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose)
    {
        return EntityDimensions.scalable(entityData.get(WIDTH), entityData.get(HEIGHT));
    }

    @Override
    public boolean isPickable()
    {
        return false;
    }

    @Override
    public int getColor()
    {
        return entityData.get(COLOR);
    }

    @Override
    public void setColor(int color)
    {
        entityData.set(COLOR, color);
    }
}
