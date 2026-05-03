package net.splatcraft.forge.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ServerLevel.class)
public class ServerLevelParticleMixin
{
    @ModifyArg(method = "sendParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket;<init>(Lnet/minecraft/core/particles/ParticleOptions;ZDDDFFFFI)V"), index = 1, require = 1)
    private boolean splatcraft$forceParticleDistance(boolean original)
    {
        return true;
    }
}
