package net.splatcraft.forge.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.splatcraft.forge.client.handlers.SplatcraftKeyHandler;
import net.splatcraft.forge.items.weapons.WeaponBaseItem;
import net.splatcraft.forge.network.SplatcraftPacketHandler;
import net.splatcraft.forge.network.c2s.UseStoredSpecialPacket;
import net.splatcraft.forge.network.c2s.UseStoredSubWeaponPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public class WeaponHotkeyMixin
{

	@Mixin(MultiPlayerGameMode.class)
	public static abstract class PlayerControllerMix
	{
		@Inject(method = "releaseUsingItem", at = @At("HEAD"), cancellable = true)
		private void releaseUsingItem(Player player, CallbackInfo callbackInfo)
		{
			if (SplatcraftKeyHandler.isSubWeaponHotkeyDown() && player.getUsedItemHand() == InteractionHand.OFF_HAND)
				callbackInfo.cancel();
		}
	}

	@Mixin(Minecraft.class)
	public static abstract class MinecraftInstance
	{
		@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
		private void startUseItem(CallbackInfo ci)
		{
			if (SplatcraftKeyHandler.isSubWeaponHotkeyDown())
			{
				SplatcraftKeyHandler.startUsingItemInHand(InteractionHand.OFF_HAND);
				ci.cancel();
			}
		}
	}

	@Mixin(Minecraft.class)
	public static abstract class MinecraftAttackMixin
	{
		@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
		private void startAttack(CallbackInfoReturnable<Boolean> cir)
		{
			Minecraft minecraft = (Minecraft) (Object) this;
			if (minecraft.player == null || minecraft.screen != null)
				return;

			ItemStack stack = minecraft.player.getMainHandItem();
			if (stack.getItem() instanceof WeaponBaseItem<?> && !WeaponBaseItem.getStoredSubWeapon(stack).isEmpty())
			{
				SplatcraftPacketHandler.sendToServer(new UseStoredSubWeaponPacket());
				cir.setReturnValue(false);
			}
		}
	}

	@Mixin(Minecraft.class)
	public static abstract class MinecraftPickMixin
	{
		@Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
		private void pickBlock(CallbackInfo ci)
		{
			Minecraft minecraft = (Minecraft) (Object) this;
			if (minecraft.player == null || minecraft.screen != null || !SplatcraftKeyHandler.isSpecialWeaponHotkeyDown())
				return;

			ItemStack stack = minecraft.player.getMainHandItem();
			if (stack.getItem() instanceof WeaponBaseItem<?> && WeaponBaseItem.canUseStoredSpecial(stack))
			{
				SplatcraftPacketHandler.sendToServer(new UseStoredSpecialPacket());
				ci.cancel();
			}
		}
	}
}
