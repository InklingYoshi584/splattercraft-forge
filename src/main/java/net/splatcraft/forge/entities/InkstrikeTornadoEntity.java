package net.splatcraft.forge.entities;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.splatcraft.forge.client.particles.InkSplashParticleData;
import net.splatcraft.forge.registries.SplatcraftDamageTypes;
import net.splatcraft.forge.registries.SplatcraftEntities;
import net.splatcraft.forge.util.ColorUtils;
import net.splatcraft.forge.util.InkBlockUtils;
import net.splatcraft.forge.util.InkDamageUtils;
import net.splatcraft.forge.util.InkExplosion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InkstrikeTornadoEntity extends Entity implements IColoredEntity
{
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(InkstrikeTornadoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(InkstrikeTornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(InkstrikeTornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final float START_WIDTH = 1.0F;
    private static final float END_WIDTH = 15.0F;
    private static final float TORNADO_HEIGHT = 20.0F;
    private static final int EXPANSION_TICKS = 40;
    private static final float DAMAGE_PER_TICK = 4.0F;
    private static final float BLOCK_INK_STRENGTH = 0.8F;

    private UUID ownerUUID;
    private ItemStack sourceWeapon = ItemStack.EMPTY;
    private InkBlockUtils.InkType inkType = InkBlockUtils.InkType.NORMAL;

    public InkstrikeTornadoEntity(EntityType<? extends InkstrikeTornadoEntity> type, Level level)
    {
        super(type, level);
    }

    public InkstrikeTornadoEntity(Level level, @Nullable LivingEntity owner, @Nullable UUID ownerUUID, ItemStack sourceWeapon, InkBlockUtils.InkType inkType, int color, Vec3 pos)
    {
        this(SplatcraftEntities.INKSTRIKE_TORNADO.get(), level);
        this.ownerUUID = owner != null ? owner.getUUID() : ownerUUID;
        this.sourceWeapon = sourceWeapon.copy();
        this.inkType = inkType;
        setColor(color);
        setPos(pos.x, pos.y, pos.z);
        setTornadoWidth(START_WIDTH);
    }

    @Override
    protected void defineSynchedData()
    {
        entityData.define(COLOR, ColorUtils.DEFAULT);
        entityData.define(WIDTH, START_WIDTH);
        entityData.define(HEIGHT, TORNADO_HEIGHT);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> dataAccessor)
    {
        if (dataAccessor.equals(WIDTH) || dataAccessor.equals(HEIGHT))
            refreshDimensions();

        super.onSyncedDataUpdated(dataAccessor);
    }

    @Override
    public void tick()
    {
        super.tick();

        float progress = EXPANSION_TICKS <= 1 ? 1.0F : Math.min(1.0F, Math.max(0.0F, (tickCount - 1) / (float) (EXPANSION_TICKS - 1)));
        float width = Mth.lerp(progress, START_WIDTH, END_WIDTH);
        setTornadoWidth(width);

        if (level().isClientSide)
        {
            spawnParticles();
        }
        else
        {
            inkArea(width * 0.5F);
            damageEntities();
        }

        if (tickCount >= EXPANSION_TICKS)
            discard();
    }

    private void spawnParticles()
    {
        float[] rgb = ColorUtils.hexToRGB(getColor());
        float radius = getTornadoWidth() * 0.5F;
        int swirlParticles = Math.max(42, Mth.ceil(radius * 10.0F));

        for (int i = 0; i < swirlParticles; i++)
        {
            double progress = (double) i / (double) swirlParticles;
            double swirl = random.nextDouble() * Math.PI * 2.0D + tickCount * 0.45D + progress * Math.PI * 3.0D;
            double currentRadius = radius * (0.45D + random.nextDouble() * 0.65D);
            double x = getX() + Math.cos(swirl) * currentRadius;
            double y = getY() + progress * TORNADO_HEIGHT + random.nextDouble() * 1.5D;
            double z = getZ() + Math.sin(swirl) * currentRadius;
            double dx = -Math.sin(swirl) * 0.08D;
            double dz = Math.cos(swirl) * 0.08D;
            level().addParticle(new InkSplashParticleData(rgb[0], rgb[1], rgb[2], 1.0F + random.nextFloat() * 0.7F), x, y, z, dx, 0.08D + random.nextDouble() * 0.12D, dz);
        }

        int coreParticles = Math.max(20, Mth.ceil(radius * 4.0F));
        for (int i = 0; i < coreParticles; i++)
        {
            double x = getX() + (random.nextDouble() - 0.5D) * radius * 1.8D;
            double y = getY() + random.nextDouble() * 2.5D;
            double z = getZ() + (random.nextDouble() - 0.5D) * radius * 1.8D;
            level().addParticle(new InkSplashParticleData(rgb[0], rgb[1], rgb[2], 1.7F + random.nextFloat() * 0.8F), x, y, z, 0.0D, 0.15D + random.nextDouble() * 0.05D, 0.0D);
        }

        int outerRingParticles = Math.max(30, Mth.ceil(radius * 8.0F));
        for (int i = 0; i < outerRingParticles; i++)
        {
            double swirl = random.nextDouble() * Math.PI * 2.0D + tickCount * 0.22D;
            double currentRadius = radius * (0.85D + random.nextDouble() * 0.3D);
            double x = getX() + Math.cos(swirl) * currentRadius;
            double y = getY() + random.nextDouble() * 1.5D;
            double z = getZ() + Math.sin(swirl) * currentRadius;
            double dx = -Math.sin(swirl) * (0.1D + random.nextDouble() * 0.05D);
            double dz = Math.cos(swirl) * (0.1D + random.nextDouble() * 0.05D);
            level().addParticle(new InkSplashParticleData(rgb[0], rgb[1], rgb[2], 1.2F + random.nextFloat() * 0.7F), x, y, z, dx, 0.16D + random.nextDouble() * 0.06D, dz);
        }
    }

    private void inkArea(float radius)
    {
        LivingEntity owner = getOwner();
        InkExplosion.createInkExplosion(level(), owner != null ? owner : this, blockPosition(), radius, BLOCK_INK_STRENGTH, 0.0F, false, getColor(), inkType, sourceWeapon);
    }

    private void damageEntities()
    {
        LivingEntity owner = getOwner();
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.35D), entity -> entity.isAlive() && entity != owner))
        {
            InkDamageUtils.doDamage(level(), target, DAMAGE_PER_TICK, getColor(), owner != null ? owner : this, this, sourceWeapon, true, SplatcraftDamageTypes.INK_STRIKE_TORNADO, false);
        }
    }

    @Nullable
    public LivingEntity getOwner()
    {
        if (ownerUUID == null || !(level() instanceof ServerLevel serverLevel))
            return null;

        Entity entity = serverLevel.getEntity(ownerUUID);
        return entity instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    public float getTornadoWidth()
    {
        return entityData.get(WIDTH);
    }

    public void setTornadoWidth(float width)
    {
        entityData.set(WIDTH, width);
        reapplyPosition();
        refreshDimensions();
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag)
    {
        if (tag.hasUUID("Owner"))
            ownerUUID = tag.getUUID("Owner");
        if (tag.contains("SourceWeapon"))
            sourceWeapon = ItemStack.of(tag.getCompound("SourceWeapon"));
        if (tag.contains("InkType"))
            inkType = InkBlockUtils.InkType.values.getOrDefault(new ResourceLocation(tag.getString("InkType")), InkBlockUtils.InkType.NORMAL);
        if (tag.contains("Color"))
            setColor(ColorUtils.getColorFromNbt(tag));
        if (tag.contains("Width"))
            setTornadoWidth(tag.getFloat("Width"));
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag)
    {
        if (ownerUUID != null)
            tag.putUUID("Owner", ownerUUID);
        tag.put("SourceWeapon", sourceWeapon.save(new CompoundTag()));
        tag.putString("InkType", inkType.getSerializedName());
        tag.putInt("Color", getColor());
        tag.putFloat("Width", getTornadoWidth());
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
