package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.miraculous.viperion.Viperion;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Viperion's Serpent Dash slithers LOW -- while it runs the player is held in the swim / crawl pose (a
 * 0.6-block box, under swings and arrows). Both sides read the synced state, so the server's box and every client's
 * model agree.
 */
@Mixin(Player.class)
public abstract class ViperionDashPoseMixin {
	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	private void projecthero$viperionSlither(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (Viperion.slithering(self) && !self.isPassenger() && !self.isSpectator()) {
			if (self.getPose() != Pose.SWIMMING) {
				self.setPose(Pose.SWIMMING);
			}
			ci.cancel();
		}
	}
}
