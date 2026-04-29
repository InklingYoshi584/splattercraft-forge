package net.splatcraft.forge.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.splatcraft.forge.client.handlers.DeathRecapClientHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin implements DeathRecapClientHandler.CameraAccessor
{
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "setup", at = @At("TAIL"))
    private void splatcraft$applyDeathRecapCamera(BlockGetter level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci)
    {
        DeathRecapClientHandler.applyCameraOverride((Camera)(Object)this, entity, partialTick);
    }

    @Override
    public void splatcraft$setPosition(Vec3 position)
    {
        setPosition(position);
    }

    @Override
    public void splatcraft$setRotation(float yaw, float pitch)
    {
        setRotation(yaw, pitch);
    }
}
