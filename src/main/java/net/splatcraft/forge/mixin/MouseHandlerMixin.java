package net.splatcraft.forge.mixin;

import net.minecraft.client.MouseHandler;
import net.splatcraft.forge.client.handlers.InkstrikeTacticalOverlayHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin
{
	@Shadow
	private double accumulatedDX;

	@Shadow
	private double accumulatedDY;

	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void splatcraft$redirectInkstrikeCursor(CallbackInfo ci)
	{
		double dx = accumulatedDX;
		double dy = accumulatedDY;
		InkstrikeTacticalOverlayHandler.consumeMouse(dx, dy);

		if (!InkstrikeTacticalOverlayHandler.isOverlayActive(net.minecraft.client.Minecraft.getInstance().player))
			return;

		accumulatedDX = 0.0D;
		accumulatedDY = 0.0D;
		ci.cancel();
	}
}
