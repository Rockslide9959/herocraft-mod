package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
 * MOVEMENT_SPEED attribute the server sets from the momentum, and the step assist grows with it (6 blocks, up to 8); here:
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

	/** v0.17.1: last tick's run (horizontal unit direction and blocks a tick) for the turn assist / Auto-Slowdown. */
	@Unique
	private Vec3 projecthero$sfMoveDir;
	@Unique
	private double projecthero$sfMoveSpeed;

	/**
	 * v0.17.1 (user): the run itself, as it moves -- the <b>turn assist</b> (at speed a sharp turn bends round, the run's
	 * direction swinging toward where you steer at most {@link SpeedForceConfig#TURN_ASSIST_DEG_PER_TICK} a tick with its
	 * speed kept, so the server never reads a hard turn) and <b>Auto-Slowdown</b> (the Shift+N toggle: the run eases off
	 * before a drop deeper than Ground-Stick's reach, or lava / fire, ahead). Only the player's own walking move
	 * ({@code travel} passes the motion itself), never a piston's push.
	 */
	@ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
	private Vec3 projecthero$speedForceSteer(Vec3 pos) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (pos != self.getDeltaMovement() || !SpeedForceSpeedster.speedActive(self) || self.getAbilities().flying || self.isPassenger()
				|| self.isFallFlying() || self.isSpectator() || SpeedForceSpeedster.phasing(self)) {
			if (pos == self.getDeltaMovement()) {
				projecthero$sfMoveDir = null;
			}
			return pos;
		}
		float m = SpeedForceSpeedster.momentum(self);
		double sp = Math.sqrt(pos.x * pos.x + pos.z * pos.z);
		if (sp < 1.0e-4) {
			projecthero$sfMoveDir = null;
			return pos;
		}
		Vec3 dir = new Vec3(pos.x / sp, 0, pos.z / sp);
		Vec3 out = pos;
		// the turn assist
		Vec3 last = projecthero$sfMoveDir;
		if (last != null && m >= SpeedForceConfig.TURN_ASSIST_MOMENTUM && projecthero$sfMoveSpeed > 0.8 && !self.horizontalCollision) {
			double cos = Math.max(-1.0, Math.min(1.0, last.dot(dir)));
			double angle = Math.toDegrees(Math.acos(cos));
			if (angle > SpeedForceConfig.TURN_ASSIST_DEG_PER_TICK && angle < SpeedForceConfig.TURN_ASSIST_HARD_ANGLE) {
				double turn = Math.toRadians(SpeedForceConfig.TURN_ASSIST_DEG_PER_TICK) * Math.signum(last.x * dir.z - last.z * dir.x);
				double c = Math.cos(turn);
				double s = Math.sin(turn);
				dir = new Vec3(last.x * c - last.z * s, 0, last.x * s + last.z * c);
				sp = Math.max(sp, projecthero$sfMoveSpeed * SpeedForceConfig.TURN_ASSIST_KEEP);
				out = new Vec3(dir.x * sp, pos.y, dir.z * sp);
			}
		}
		// Auto-Slowdown
		com.projecthero.mod.speedforce.data.SpeedForceState st = SpeedForceSpeedster.peek(self);
		if (st != null && st.autoSlow && m >= SpeedForceConfig.AUTO_SLOW_MOMENTUM && self.onGround() && sp > SpeedForceConfig.AUTO_SLOW_MIN_BPT
				&& com.projecthero.mod.client.speedforce.SpeedForceBuildClient.floorY(self) == Integer.MIN_VALUE) {
			double look = Math.min(SpeedForceConfig.AUTO_SLOW_MAX_LOOK, sp * SpeedForceConfig.AUTO_SLOW_LOOKAHEAD_TICKS + 4.0);
			double danger = projecthero$sfDangerAhead(self, dir, look);
			if (danger >= 0.0) {
				double allowed = Math.max(SpeedForceConfig.AUTO_SLOW_MIN_BPT, (danger - SpeedForceConfig.AUTO_SLOW_MARGIN) / SpeedForceConfig.AUTO_SLOW_BRAKE);
				allowed = Math.max(allowed, Math.min(sp, projecthero$sfMoveSpeed) * SpeedForceConfig.AUTO_SLOW_STEP_KEEP);
				if (sp > allowed) {
					sp = allowed;
					out = new Vec3(dir.x * sp, out.y, dir.z * sp);
				}
			}
		}
		projecthero$sfMoveDir = dir;
		projecthero$sfMoveSpeed = sp;
		if (out != pos) {
			self.setDeltaMovement(out);
			return self.getDeltaMovement();
		}
		return pos;
	}

	/**
	 * How far ahead along {@code dir} the first danger is -- no ground within {@link SpeedForceConfig#AUTO_SLOW_DROP} blocks
	 * below the run (a cliff, a ravine), or lava / fire -- or -1 for none (or the world not loaded that far). The ground is
	 * followed sample by sample, so a hillside going down is not a cliff; water counts as ground (it is run across).
	 */
	@Unique
	private static double projecthero$sfDangerAhead(LocalPlayer self, Vec3 dir, double look) {
		net.minecraft.world.level.Level level = self.level();
		double refY = self.getY();
		for (double d = 1.0; d <= look; d += 1.0) {
			double x = self.getX() + dir.x * d;
			double z = self.getZ() + dir.z * d;
			if (projecthero$sfMissing(self, x, z)) {
				return -1.0;
			}
			int top = net.minecraft.util.Mth.floor(refY) + 3;
			int bottom = net.minecraft.util.Mth.floor(refY) - SpeedForceConfig.AUTO_SLOW_DROP;
			boolean ground = false;
			BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
			for (int y = top; y >= bottom; y--) {
				bp.set(net.minecraft.util.Mth.floor(x), y, net.minecraft.util.Mth.floor(z));
				net.minecraft.world.level.block.state.BlockState bs = level.getBlockState(bp);
				FluidState fl = bs.getFluidState();
				if (fl.is(FluidTags.LAVA) || bs.is(net.minecraft.tags.BlockTags.FIRE)) {
					return d;
				}
				if (fl.is(FluidTags.WATER)) {
					refY = y + 1.0;
					ground = true;
					break;
				}
				net.minecraft.world.phys.shapes.VoxelShape shape = bs.getCollisionShape(level, bp);
				if (!shape.isEmpty()) {
					refY = y + shape.max(net.minecraft.core.Direction.Axis.Y);
					ground = true;
					break;
				}
			}
			if (!ground) {
				return d;
			}
		}
		return -1.0;
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void projecthero$speedForceMovement(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		// v0.17.1 (user): the hold-C charge stance holds you in place
		if (SpeedForceSpeedster.chargeStart(self) != 0L) {
			Vec3 cv = self.getDeltaMovement();
			self.setDeltaMovement(0.0, Math.min(0.0, cv.y), 0.0);
		}
		if (!SpeedForceSpeedster.speedActive(self) || self.getAbilities().flying || self.isPassenger() || self.isFallFlying()
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
		// v0.17.1 (user) Speed Build: the bridge being laid holds the runner at its level until its blocks arrive
		int floor = com.projecthero.mod.client.speedforce.SpeedForceBuildClient.floorY(self);
		if (!onWater && floor != Integer.MIN_VALUE && self.getY() < floor && self.getY() > floor - 2.5) {
			self.setPos(self.getX(), floor, self.getZ());
			self.setDeltaMovement(v.x, Math.max(0.0, v.y), v.z);
			self.setOnGround(true);
			self.resetFallDistance();
			v = self.getDeltaMovement();
		}

		// ground hugging: ran off a dip without jumping -> back down onto the ground below
		boolean jumping = self.input != null && self.input.jumping;
		if (!onWater && projecthero$sfWasOnGround && !self.onGround() && !jumping && v.y <= 0.0 && horiz > 0.5 && !self.isInWater()) {
			// v0.17.1 (user): Ground-Stick (Shift+N) holds the runner to anything within its full 5 blocks
			double maxDrop = com.projecthero.mod.client.speed.SpeedsterGroundStick.enabled()
					? com.projecthero.mod.client.speed.SpeedsterGroundStick.MAX_DROP : 1.25 + 2.5 * m;
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
