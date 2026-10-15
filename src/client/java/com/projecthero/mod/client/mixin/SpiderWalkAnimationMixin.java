package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.spider.SpiderSwingPose;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.4 (user: "while Spider-Man is swinging he starts running in the air"): vanilla turns every living entity's
 * horizontal speed into its walk cycle ({@code walkAnimation}: limb swing / limb swing amount), in the air too -- and a
 * swing moves him at 1-3 blocks a tick, a full sprint stride. Every pose that blends over vanilla (the swing easing in
 * and out, the release, a trick ending) let that stride through, so his legs and arms ran in mid-air. While he is on a
 * line or flying on after one ({@link SpiderSwingPose#holdsWalkCycle}) the walk cycle winds down to rest instead (the
 * same 0.4 easing vanilla uses, so it fades rather than snaps) and picks up again once he lands. Runs on every client
 * for every Spider-Man drawn (local and remote players both tick their walk animation here), never on the server.
 */
@Mixin(LivingEntity.class)
public abstract class SpiderWalkAnimationMixin {
	@Inject(method = "updateWalkAnimation", at = @At("HEAD"), cancellable = true)
	private void projecthero$spiderNoAirStride(float distance, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof Player player && player.level().isClientSide() && SpiderSwingPose.holdsWalkCycle(player)) {
			self.walkAnimation.update(0.0f, 0.4f);
			ci.cancel();
		}
	}
}
