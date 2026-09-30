package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.mutation.v0145.SuperSpeedClientV0145;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Super Speed Time Slow, client half (v0.14.5): the client skips the same 19 ticks in 20 as the server for entities
 * inside a Time Slow field, so client-simulated things (arrows, dropped items, falling blocks) don't race ahead and
 * snap back. A skipped entity's "previous" position / rotation / limb swing is pinned to the current one so the
 * frames in between don't flicker.
 */
@Mixin(ClientLevel.class)
public abstract class SuperSpeedClientLevelMixin {
	@Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
	private void projecthero$timeSlow(Entity entity, CallbackInfo ci) {
		if (SuperSpeedClientV0145.skipClientTick(entity)) {
			entity.setOldPosAndRot();
			if (entity instanceof LivingEntity living) {
				living.yBodyRotO = living.yBodyRot;
				living.yHeadRotO = living.yHeadRot;
				living.walkAnimation.setSpeed(0.0f);
				((SuperSpeedWalkAnimationAccessor) living.walkAnimation).projecthero$setSpeedOld(0.0f);
			}
			ci.cancel();
		}
	}
}
