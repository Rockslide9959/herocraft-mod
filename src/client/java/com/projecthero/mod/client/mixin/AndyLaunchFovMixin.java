package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.andy.AndyLaunchFov;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

/** v0.17.3: Andy's X launches -- the Head Launch wind-up pulls the local player's view in, a launch kicks it out. */
@Mixin(AbstractClientPlayer.class)
public abstract class AndyLaunchFovMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void projecthero$andyLaunchFov(CallbackInfoReturnable<Float> cir) {
		if ((Object) this != Minecraft.getInstance().player) {
			return;
		}
		float scale = AndyLaunchFov.scale((AbstractClientPlayer) (Object) this);
		if (scale != 1f) {
			cir.setReturnValue(cir.getReturnValue() * scale);
		}
	}
}
