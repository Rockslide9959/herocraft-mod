package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.speedforce.SpeedForceConfig;
import com.projecthero.mod.speedforce.SpeedForceSpeedster;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: what makes 250 blocks a second actually run smoothly on the ground for the Speed Force Speedster (local
 * player only; the server accepts the positions -- see {@code SpeedForceMoveCheckMixin}). The speed itself is the
 * MOVEMENT_SPEED attribute the server sets from the momentum, and the step assist grows with it (up to 5 blocks); here:
 * <ul>
 *   <li><b>ground hugging</b>: running off a dip or down a slope at 12 blocks a tick would launch you 30 blocks through
 *       the air -- a runner who was on the ground and didn't jump is set back down on the ground below (up to a couple of
 *       blocks, more with the momentum) instead;</li>
 *   <li><b>jumps keep their speed</b> (the air drag would eat it in half a second);</li>
 *   <li><b>never outrunning the world</b>: if the chunks ahead aren't loaded yet the run eases down (never more than 40%
 *       a tick, so the server never reads it as a wall) until they are, instead of running off into the void;</li>
 *   <li>water running and wall running (look steeply up into a wall), like the plain Speedster's.</li>
 * </ul>
 */
@Mixin(LocalPlayer.class)
public abstract class SpeedForceLocalPlayerMixin {
	@Unique
	private boolean projecthero$sfWasOnGround;
	@Unique
	private boolean projecthero$sfWallRunning;
	@Unique
	private double projecthero$sfGroundSpeed;

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void projecthero$speedForceMovement(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (!SpeedForceSpeedster.hasPower(self) || self.getAbilities().flying || self.isPassenger() || self.isFallFlying()
				|| self.isSpectator() || SpeedForceSpeedster.phasing(self)) {
			projecthero$sfWasOnGround = self.onGround();
			projecthero$sfWallRunning = false;
			projecthero$sfGroundSpeed = 0.0;
			return;
		}
		float m = SpeedForceSpeedster.momentum(self);

		// wall run: into a wall while looking steeply up -- straight up it, with a hop onto the top
		boolean canWallRun = m >= SpeedForceConfig.WALL_RUN_MOMENTUM && self.getXRot() <= -60.0f && self.zza > 0.0f
				&& !self.isShiftKeyDown() && !self.isInWater();
		if (canWallRun && self.horizontalCollision) {
			Vec3 cur = self.getDeltaMovement();
			self.setDeltaMovement(cur.x, 0.7 + 0.9 * m, cur.z);
			self.resetFallDistance();
			projecthero$sfWallRunning = true;
			projecthero$sfWasOnGround = false;
			return;
		}
		if (projecthero$sfWallRunning) {
			projecthero$sfWallRunning = false;
			if (canWallRun && !self.onGround()) {
				float yaw = self.getYRot() * ((float) Math.PI / 180.0f);
				self.setDeltaMovement(-Math.sin(yaw) * 0.5, 0.5, Math.cos(yaw) * 0.5);
				self.resetFallDistance();
				return;
			}
		}

		Vec3 v = self.getDeltaMovement();
		double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
		boolean steering = self.zza != 0.0f || self.xxa != 0.0f;

		// water running (the same surface finding as the plain Speedster's)
		double surfaceY = Double.NaN;
		if (!self.isShiftKeyDown() && self.isSprinting() && (horiz > 0.08 || steering)) {
			BlockPos feet = self.blockPosition();
			FluidState fluid = self.level().getFluidState(feet);
			if (!fluid.is(FluidTags.WATER) && self.level().getFluidState(feet.below()).is(FluidTags.WATER)) {
				feet = feet.below();
				fluid = self.level().getFluidState(feet);
			}
			if (fluid.is(FluidTags.WATER) && self.level().getFluidState(feet.above()).isEmpty()) {
				double y = feet.getY() + fluid.getHeight(self.level(), feet);
				if (self.getY() >= y - 1.2 && self.getY() <= y + 0.5) {
					surfaceY = y;
				}
			}
		}
		boolean onWater = !Double.isNaN(surfaceY);
		if (onWater) {
			if (self.getY() < surfaceY) {
				self.setPos(self.getX(), surfaceY, self.getZ());
			}
			self.setDeltaMovement(v.x, Math.max(0.0, v.y), v.z);
			self.setOnGround(true);
			self.resetFallDistance();
			v = self.getDeltaMovement();
		}

		// ground hugging: ran off a dip without jumping -> back down onto the ground below
		boolean jumping = self.input != null && self.input.jumping;
		if (!onWater && projecthero$sfWasOnGround && !self.onGround() && !jumping && v.y <= 0.0 && horiz > 0.5 && !self.isInWater()) {
			double maxDrop = 1.25 + 2.5 * m;
			AABB box = self.getBoundingBox();
			double hit = -1.0;
			for (double d = 0.25; d <= maxDrop + 1.0e-6; d += 0.25) {
				if (!self.level().noCollision(self, box.move(0, -d, 0))) {
					hit = d;
					break;
				}
			}
			if (hit > 0.0) {
				double lo = hit - 0.25;
				double hi = hit;
				for (int i = 0; i < 5; i++) {
					double mid = (lo + hi) * 0.5;
					if (self.level().noCollision(self, box.move(0, -mid, 0))) {
						lo = mid;
					} else {
						hi = mid;
					}
				}
				self.setPos(self.getX(), self.getY() - lo, self.getZ());
				self.setDeltaMovement(v.x, 0.0, v.z);
				self.setOnGround(true);
				self.resetFallDistance();
				v = self.getDeltaMovement();
			}
		}

		// jumps keep their speed: ease back up toward the speed at take-off
		if (self.onGround() || onWater) {
			projecthero$sfGroundSpeed = Math.min(SpeedForceConfig.TOP_SPEED_BPS / 20.0, horiz);
		} else if (projecthero$sfGroundSpeed > 0.05 && horiz > 1.0e-4 && horiz < projecthero$sfGroundSpeed && !self.horizontalCollision) {
			double target = horiz + (projecthero$sfGroundSpeed - horiz) * 0.5;
			double f = target / horiz;
			self.setDeltaMovement(v.x * f, v.y, v.z * f);
			v = self.getDeltaMovement();
			horiz = Math.sqrt(v.x * v.x + v.z * v.z);
		}

		// never outrun the world: ease off while the chunks ahead are still loading
		if (horiz > 0.8) {
			double perTick = horiz / 0.546; // the next tick's displacement on the ground (an upper bound in the air)
			double ahead = perTick * 10.0 + 24.0;
			Vec3 dir = new Vec3(v.x / horiz, 0, v.z / horiz);
			double missing = -1.0;
			for (double d = 8.0; d <= ahead; d += 8.0) {
				double x = self.getX() + dir.x * d;
				double z = self.getZ() + dir.z * d;
				if (projecthero$sfMissing(self, x, z)) {
					missing = d;
					break;
				}
			}
			if (missing > 0.0) {
				double allowed = Math.max(0.5, (missing - 16.0) / 10.0);
				if (perTick > allowed) {
					double f = Math.max(0.6, allowed / perTick);
					self.setDeltaMovement(v.x * f, v.y, v.z * f);
				}
			}
		}
		// the ground under your feet itself not here yet (the world fell behind): hold still until it is
		if (projecthero$sfMissing(self, self.getX(), self.getZ())) {
			Vec3 hv = self.getDeltaMovement();
			self.setDeltaMovement(0.0, Math.max(0.0, hv.y), 0.0);
			self.resetFallDistance();
		}
		projecthero$sfWasOnGround = self.onGround();
	}

	/** A chunk the client doesn't have yet (not loaded, or only the empty placeholder). */
	@Unique
	private static boolean projecthero$sfMissing(LocalPlayer self, double x, double z) {
		int cx = SectionPos.blockToSectionCoord(x);
		int cz = SectionPos.blockToSectionCoord(z);
		return !self.level().hasChunk(cx, cz)
				|| self.level().getChunk(cx, cz) instanceof net.minecraft.world.level.chunk.EmptyLevelChunk;
	}
}
