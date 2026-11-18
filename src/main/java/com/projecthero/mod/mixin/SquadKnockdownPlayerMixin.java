package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.squad.SquadKnockdown;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.5 squad knockdown: a knocked player crawls -- held in vanilla's swim / crawl pose (the 0.6-block box used to crawl
 * under trapdoors, and vanilla's own crawl speed on the owner's client) -- and no mob's targeting sees them
 * ({@code canBeSeenAsEnemy}: every vanilla target goal and sensor asks it, and a mob already chasing them drops them at its next
 * check). Both sides read {@link SquadKnockdown#isKnocked}: the server's table there, the synced attachment on a client, so the
 * server's box and the owner's movement agree; other clients get the pose from the server's entity data.
 */
@Mixin(Player.class)
public abstract class SquadKnockdownPlayerMixin {
	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	private void projecthero$knockedCrawl(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (SquadKnockdown.isKnocked(self) && !self.isPassenger() && !self.isSpectator()) {
			if (self.getPose() != Pose.SWIMMING) {
				self.setPose(Pose.SWIMMING);
			}
			ci.cancel();
		}
	}

	@Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
	private void projecthero$knockedUnseen(CallbackInfoReturnable<Boolean> cir) {
		if (SquadKnockdown.isKnocked((Player) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
