package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.hero.power.p08.PyrokinesisHandlers;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Pyrokinesis passives, on whichever side moves the player (the client for its own player):
 * <ul>
 * <li>lava doesn't slow a pyrokinetic -- on the lava floor {@code travel} skips its lava branch (the second
 * {@code canStandOnFluid} check), so they walk at normal speed and can jump; v0.16.5 (user: "cant swim in lava ... like
 * trapped"): off the floor the vanilla lava swim runs (so Space rises), sped up -- faster strokes and a stronger rise;</li>
 * <li>Flame Body walks ON lava: lava counts as a fluid they can stand on (the collision side of
 * {@code canStandOnFluid}), unless they hold Shift to sink in.</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class PyroLavaMixin {
	@Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
	private void projecthero$pyroLavaWalk(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player p && fluid.is(FluidTags.LAVA) && !p.isShiftKeyDown()
				&& PyrokinesisHandlers.flameBodyActive(p)) {
			cir.setReturnValue(true);
		}
	}

	@Redirect(method = "travel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;canStandOnFluid(Lnet/minecraft/world/level/material/FluidState;)Z",
			ordinal = 1))
	private boolean projecthero$pyroLavaNoSlow(LivingEntity self, FluidState fluid, Vec3 travel) {
		if (self instanceof Player p && fluid.is(FluidTags.LAVA) && PyrokinesisHandlers.owns(p) && p.onGround()) {
			return true;
		}
		return self.canStandOnFluid(fluid);
	}

	@org.spongepowered.asm.mixin.Shadow
	protected boolean jumping;

	/** Swimming in lava (off the floor): quicker strokes, and Space really lifts you -- up and out over the rim. */
	@Inject(method = "travel", at = @At("TAIL"))
	private void projecthero$pyroLavaSwim(Vec3 travel, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof Player p) || !p.isInLava() || p.getAbilities().flying || !PyrokinesisHandlers.owns(p)) {
			return;
		}
		if (p.onGround()) {
			// on the lava floor: a real jump (vanilla only gives a liquid nudge here, which never lifts you off the floor)
			if (jumping) {
				Vec3 g = p.getDeltaMovement();
				p.setDeltaMovement(g.x, Math.max(g.y, 0.42), g.z);
			}
			return;
		}
		Vec3 v = p.getDeltaMovement();
		double h = Math.sqrt(v.x * v.x + v.z * v.z);
		boolean stroking = travel.x * travel.x + travel.z * travel.z > 1.0e-4; // only while you swim, never a drift
		double scale = stroking && h > 1.0e-4 ? Math.min(2.2, 0.28 / h) : 1.0;
		double vy = v.y;
		if (jumping) {
			vy = Math.max(vy, p.horizontalCollision ? 0.34 : 0.16); // a wall at the rim: climb out
		} else if (!p.isShiftKeyDown()) {
			vy = Math.max(vy, -0.06); // drift down slowly, like water
		}
		p.setDeltaMovement(v.x * (scale > 1.0 ? scale : 1.0), vy, v.z * (scale > 1.0 ? scale : 1.0));
	}
}
