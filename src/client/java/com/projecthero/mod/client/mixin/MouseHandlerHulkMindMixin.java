package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.projecthero.mod.client.hulk.HulkMindScreen;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;

/**
 * v0.16.4 (user): inside the Hulk's mind (a death rampage, {@link HulkMindScreen}) the mouse turns the mind walker, not the
 * player -- the server is steering the rampaging Hulk, so his own rotation must not move. Sensitivity, smoothing and invert
 * still apply (only the final {@code turn} call is redirected). The scroll wheel does nothing in there (no hotbar).
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerHulkMindMixin {
	@WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void projecthero$hulkMindLook(LocalPlayer player, double dx, double dy, Operation<Void> original) {
		if (HulkMindScreen.showing()) {
			HulkMindScreen.turn(dx, dy);
			return;
		}
		original.call(player, dx, dy);
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void projecthero$hulkMindNoScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (HulkMindScreen.showing() && net.minecraft.client.Minecraft.getInstance().screen == null) {
			ci.cancel();
		}
	}

}
