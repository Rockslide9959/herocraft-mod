package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.eyeguy.EyeGuyKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * v0.17.4 Eye Guy's Far Sight (Hold V): while it is held the local player's view narrows like a spyglass (a quarter of the field of view),
 * easing in and out over a few frames.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class EyeGuyFarSightFovMixin {
	@Unique
	private static float projecthero$farSight;

	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void projecthero$eyeGuyFarSight(CallbackInfoReturnable<Float> cir) {
		if ((Object) this != Minecraft.getInstance().player) {
			return;
		}
		boolean on = EyeGuyKit.zooming((AbstractClientPlayer) (Object) this);
		projecthero$farSight += ((on ? 1f : 0f) - projecthero$farSight) * 0.35f;
		if (projecthero$farSight > 0.01f) {
			cir.setReturnValue(cir.getReturnValue() * (1f - 0.75f * projecthero$farSight));
		}
	}
}
