package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * Super Speed Shift+C Phase (v0.14.5): a phasing speedster stays standing. Inside a wall vanilla would drop them to
 * the crawl pose, and holding sneak would crouch (and crawl-speed) them -- Shift only picks the variant on the press.
 */
@Mixin(Player.class)
public abstract class SuperSpeedPhasePoseMixin {
	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	private void projecthero$speedPhaseStands(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (SuperSpeedHandlers.phasing(self)) {
			if (self.getPose() != Pose.STANDING) {
				self.setPose(Pose.STANDING);
			}
			ci.cancel();
		}
	}
}
