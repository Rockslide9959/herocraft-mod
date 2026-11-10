package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.undead.UndeadLaunchFov;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

/** v0.17.3: Undead's X launches -- the Head Launch wind-up pulls the local player's view in, a launch kicks it out. */
@Mixin(AbstractClientPlayer.class)
public abstract class UndeadLaunchFovMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void projecthero$undeadLaunchFov(CallbackInfoReturnable<Float> cir) {
		if ((Object) this != Minecraft.getInstance().player) {
			return;
		}
		float scale = UndeadLaunchFov.scale((AbstractClientPlayer) (Object) this);
		if (scale != 1f) {
			cir.setReturnValue(cir.getReturnValue() * scale);
		}
	}
}
