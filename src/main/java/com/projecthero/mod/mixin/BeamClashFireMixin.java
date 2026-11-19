package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.beamclash.BeamClash;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * v0.17.5 (user: "sentry gets set on fire"): two shooters locked in a beam clash can't be set alight, by each other's beams or
 * anything else; only the clash's outcome hurts. Burning down (a lower count) still goes through.
 */
@Mixin(Entity.class)
public abstract class BeamClashFireMixin {
	@Shadow
	public abstract int getRemainingFireTicks();

	@Shadow
	public abstract Level level();

	@Inject(method = "setRemainingFireTicks", at = @At("HEAD"), cancellable = true)
	private void projecthero$noFireInBeamClash(int ticks, CallbackInfo ci) {
		if (ticks > 0 && ticks > getRemainingFireTicks() && (Object) this instanceof LivingEntity le && !level().isClientSide
				&& BeamClash.locked(le)) {
			ci.cancel();
		}
	}
}
