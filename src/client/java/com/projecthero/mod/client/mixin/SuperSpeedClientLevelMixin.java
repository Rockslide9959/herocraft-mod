package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.mutation.v0145.SuperSpeedClientV0145;
import com.projecthero.mod.client.mutation.v0145.TimeSlowClient;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.phys.Vec3;

/**
 * Super Speed Time Slow, the caster's own client (v0.14.7, game-wide): every other client runs at the synced 1 tick
 * a second, but the caster's keeps 20, so here the world is held back to the same pace -- each entity other than the
 * caster ticks once every 20 client ticks. On the ticks in between it creeps on smoothly instead of freezing:
 * client-simulated things (arrows, items, falling blocks, TNT) glide 1/20th of their velocity, and creatures and
 * players drift 1/60th of the way to their next synced position with their limb swing ticking on at 1/20th, so
 * nothing stutters. The level clock (sky, day / night, moon) advances once every 20 ticks too.
 */
@Mixin(ClientLevel.class)
public abstract class SuperSpeedClientLevelMixin {
	@Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
	private void projecthero$timeSlow(Entity entity, CallbackInfo ci) {
		if (SuperSpeedClientV0145.glideClientTick(entity)) {
			ci.cancel();
			return;
		}
		if (SuperSpeedClientV0145.skipClientTick(entity)) {
			entity.setOldPosAndRot();
			Vec3 target = new Vec3(entity.lerpTargetX(), entity.lerpTargetY(), entity.lerpTargetZ());
			if (target.distanceToSqr(entity.position()) < 256.0) {
				entity.setPos(entity.position().add(target.subtract(entity.position()).scale(1.0 / 60.0)));
			}
			if (entity instanceof LivingEntity living) {
				living.yBodyRotO = living.yBodyRot;
				living.yHeadRotO = living.yHeadRot;
				WalkAnimationState walk = living.walkAnimation;
				SuperSpeedWalkAnimationAccessor acc = (SuperSpeedWalkAnimationAccessor) walk;
				acc.projecthero$setSpeedOld(walk.speed());
				acc.projecthero$setPosition(acc.projecthero$position() + walk.speed() / 20.0f);
			}
			ci.cancel();
		}
	}

	@Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
	private void projecthero$timeSlowClock(CallbackInfo ci) {
		if (TimeSlowClient.skipWorldTick()) {
			ci.cancel();
		}
	}
}
