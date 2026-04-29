package net.splatcraft.forge.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.splatcraft.forge.handlers.SplatcraftCommonHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathMixin
{
    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
    private void splatcraft$holdServerPlayerDeathForRecap(CallbackInfo ci)
    {
        if (!((Object)this instanceof ServerPlayer serverPlayer) || !SplatcraftCommonHandler.shouldHoldServerPlayerDeath(serverPlayer))
            return;

        serverPlayer.deathTime = Math.min(serverPlayer.deathTime + 1, 19);
        ci.cancel();
    }
}
