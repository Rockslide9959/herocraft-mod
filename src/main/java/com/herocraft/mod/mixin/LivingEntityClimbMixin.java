package com.herocraft.mod.mixin;

import com.herocraft.mod.spider.SpiderClimb;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reports an adhered Spider Adhesion / Spider-Man player as "on something climbable".
 *
 * <p>This is now a <em>flag</em>, not the movement implementation. The old version of this power used
 * the flag as its entire mechanism, which handed the player to vanilla's ladder physics and limited
 * them to what a ladder can do -- no ceiling case, no strafing along a wall, a constant slide
 * downward whenever you stopped pressing forward, and an attachment test so coarse
 * ({@code horizontalCollision}) that a one-tick gap at a block boundary dropped you. All of that now
 * lives in {@link SpiderClimb} and {@code SpiderClimbMovement}, which move the player themselves.
 *
 * <p>What is still worth having from the vanilla flag is everything <em>around</em> movement that
 * keys off it: the entity counts as climbing for animation and statistics purposes, and vanilla's own
 * fall-distance handling agrees with ours. It is deliberately not reported while on a ceiling --
 * there is no such thing as a vanilla ceiling ladder, and claiming one confuses the pose.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityClimbMixin {
	@Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
	private void herocraft$spiderClimb(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof Player player) || player.isSpectator()) {
			return;
		}
		if (SpiderClimb.attachedToWall(player)) {
			cir.setReturnValue(true);
		}
	}
}
