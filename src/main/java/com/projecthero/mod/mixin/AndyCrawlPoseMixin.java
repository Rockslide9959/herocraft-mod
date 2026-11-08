package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.andy.Andy;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: Andy with both legs gone crawls -- held in the swim / crawl pose until a leg regrows. Both sides read the synced
 * state, so the server's box and every client's model agree.
 */
@Mixin(Player.class)
public abstract class AndyCrawlPoseMixin {
	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	private void projecthero$andyCrawl(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (Andy.bothLegsMissing(self) && !self.isPassenger() && !self.isSpectator() && !self.getAbilities().flying && !self.isFallFlying()) {
			if (self.getPose() != Pose.SWIMMING) {
				self.setPose(Pose.SWIMMING);
			}
			ci.cancel();
		}
	}
}
