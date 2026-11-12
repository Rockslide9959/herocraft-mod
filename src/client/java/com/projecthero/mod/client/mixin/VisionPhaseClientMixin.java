package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.vision.Vision;

import net.minecraft.client.player.LocalPlayer;

/**
 * v0.17.3 Vision's Intangibility: vanilla shoves a player who is inside blocks back toward open space every tick -- while he is
 * wall-phasing that is exactly where he means to be, so the shove is skipped (as {@code OmnitrixAlienPhaseClientMixin} for Ghostfreak).
 */
@Mixin(LocalPlayer.class)
public abstract class VisionPhaseClientMixin {
	@Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
	private void projecthero$visionPhaseNoShove(double x, double z, CallbackInfo ci) {
		if (Vision.wallPhasing((LocalPlayer) (Object) this)) {
			ci.cancel();
		}
	}
}
