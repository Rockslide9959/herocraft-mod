package com.projecthero.mod.client.mixin;

import com.projecthero.mod.client.spider.SpiderClimbMovement;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands an adhered player's movement to {@link SpiderClimbMovement} instead of vanilla physics.
 *
 * <p>Targets {@code LivingEntity} because {@code LocalPlayer} does not override {@code travel}, but
 * this mixin lives in the <b>client</b> config and is guarded to the local player, so it is only ever
 * the machine that owns the keyboard replacing its own movement -- exactly the split the rest of the
 * mod's client-simulated movement uses. Every other entity, and the server, are untouched.
 */
@Mixin(LivingEntity.class)
public abstract class SpiderTravelMixin {
	@Shadow
	public abstract void calculateEntityAnimation(boolean isFlying);

	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void projecthero$spiderClimbTravel(Vec3 travelVector, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player && SpiderClimbMovement.travel(player)) {
			// Vanilla's travel ends by advancing the limb-swing animation. Skipping it would leave a
			// crawling player frozen in an idle pose instead of visibly moving along the surface.
			calculateEntityAnimation(false);
			ci.cancel();
		}
	}
}
