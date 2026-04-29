package net.splatcraft.forge.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.splatcraft.forge.client.handlers.DeathRecapClientHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin
{
    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
    private void splatcraft$holdDeathForRecap(CallbackInfo ci)
    {
        if (!DeathRecapClientHandler.shouldHoldLocalPlayerDeath())
            return;

        LivingEntity self = (LivingEntity)(Object)this;
        self.deathTime = Math.min(self.deathTime + 1, 19);
        ci.cancel();
    }
}
