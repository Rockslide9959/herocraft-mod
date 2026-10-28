package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.omnitrix.alien.AlienKit;

import net.minecraft.client.player.LocalPlayer;

/**
 * v0.17.1 Omnitrix framework ({@link AlienKit#phasing}): vanilla shoves a player who is inside blocks back out toward open
 * space every tick -- while an alien is intangible that is exactly where it means to be, so the shove is skipped (the Silver
 * Surfer's {@code SilverSurferPhaseClientMixin}).
 */
@Mixin(LocalPlayer.class)
public abstract class OmnitrixAlienPhaseClientMixin {
	@Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
	private void projecthero$alienPhaseNoShove(double x, double z, CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		AlienKit kit = AlienKit.activeKit(self);
		if (kit != null && kit.phasing(self)) {
			ci.cancel();
		}
	}
}
