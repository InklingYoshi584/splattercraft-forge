package net.splatcraft.forge.entities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import net.splatcraft.forge.registries.SplatcraftEntities;
import org.jetbrains.annotations.NotNull;

public class InkstrikeLandingIndicatorEntity extends Entity implements IColoredEntity
{
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(InkstrikeLandingIndicatorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFESPAN = SynchedEntityData.defineId(InkstrikeLandingIndicatorEntity.class, EntityDataSerializers.INT);

    public InkstrikeLandingIndicatorEntity(EntityType<? extends InkstrikeLandingIndicatorEntity> type, Level level)
    {
        super(type, level);
    }

    public InkstrikeLandingIndicatorEntity(Level level, double x, double y, double z, int color, int lifespan)
    {
        this(SplatcraftEntities.INKSTRIKE_LANDING_INDICATOR.get(), level);
        setPos(x, y, z);
        setColor(color);
        setLifespan(lifespan);
    }

    @Override
    protected void defineSynchedData()
    {
        entityData.define(COLOR, -1);
        entityData.define(LIFESPAN, 20);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (tickCount >= getLifespan())
            discard();
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag)
    {
        if (tag.contains("Color"))
            setColor(tag.getInt("Color"));
        if (tag.contains("Lifespan"))
            setLifespan(tag.getInt("Lifespan"));
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag)
    {
        tag.putInt("Color", getColor());
        tag.putInt("Lifespan", getLifespan());
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket()
    {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose)
    {
        return EntityDimensions.fixed(1.5F, 1.0F);
    }

    @Override
    public boolean isPickable()
    {
        return false;
    }

    public int getLifespan()
    {
        return entityData.get(LIFESPAN);
    }

    public void setLifespan(int lifespan)
    {
        entityData.set(LIFESPAN, Math.max(1, lifespan));
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
