package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.AlienKit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;

/**
 * v0.17.1 Omnitrix framework: an alien kit's movement hooks on whichever side moves the player --
 * {@link AlienKit#standsOnFluid} (water running: the collision side of {@code canStandOnFluid}, as striders walk on lava) and
 * {@link AlienKit#climbs} (wall running: "on a ladder" while pressed into a wall).
 */
@Mixin(LivingEntity.class)
public abstract class OmnitrixAlienLivingMixin {
	@Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
	private void projecthero$alienFluidRun(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player p && !fluid.isEmpty()) {
			AlienKit kit = AlienKit.activeKit(p);
			if (kit != null && kit.standsOnFluid(p, fluid)) {
				cir.setReturnValue(true);
			}
		}
	}

	/** v0.17.2 (Way Big): a giant drawn far bigger than his hitbox is culled by the body as drawn ({@link AlienKit#drawnBody}). */
	@Inject(method = "getBoundingBoxForCulling", at = @At("HEAD"), cancellable = true)
	private void projecthero$alienDrawnBody(CallbackInfoReturnable<net.minecraft.world.phys.AABB> cir) {
		if ((Object) this instanceof Player p) {
			AlienKit kit = AlienKit.activeKit(p);
			float[] d = kit == null ? null : kit.drawnBody(p);
			if (d != null) {
				double w = d[1] * 0.5;
				cir.setReturnValue(new net.minecraft.world.phys.AABB(p.getX() - w, p.getY(), p.getZ() - w, p.getX() + w, p.getY() + d[0], p.getZ() + w));
			}
		}
	}

	/** v0.17.2 (Way Big): a kit's step height past the attribute's cap of 10 ({@link AlienKit#stepHeight}). */
	@Inject(method = "maxUpStep", at = @At("HEAD"), cancellable = true)
	private void projecthero$alienStep(CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof Player p) {
			AlienKit kit = AlienKit.activeKit(p);
			float step = kit == null ? -1f : kit.stepHeight(p);
			if (step >= 0f) {
				cir.setReturnValue(step);
			}
		}
	}

	@Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
	private void projecthero$alienWallRun(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player p && !p.isSpectator()) {
			AlienKit kit = AlienKit.activeKit(p);
			if (kit != null && kit.climbs(p)) {
				cir.setReturnValue(true);
			}
		}
	}
}
