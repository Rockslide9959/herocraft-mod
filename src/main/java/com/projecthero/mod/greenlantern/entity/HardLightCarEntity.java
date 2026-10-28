package com.projecthero.mod.greenlantern.entity;

import com.projecthero.mod.greenlantern.GreenLanternConfig;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the <b>Hard-Light Car</b> -- a sleek open-top sports car of light (Carry Platform key, second form). Seats the
 * driver + 3. Shift+R (the GL R key with Shift while driving) sounds the horn and surges forward ({@link #boost}).
 *
 * <p>v0.17.1 round 2 (user: "make the jet and car easier to control"): W accelerates (0 to
 * {@link GreenLanternConfig#VEHICLE_CAR_TOP_BPS} b/s in about 2.8 s), S is a strong brake (50 to 0 in 0.7 s) that rolls
 * straight on into a quick reverse; A/D steer tight at a crawl and calm at speed ({@link #steerRate}), easing in and
 * snapping back to centre; holding W with A/D free, the car eases toward where the driver looks (mouse steering, the view
 * does not turn with it, so it settles on the look direction). Full grip -- it always goes where its nose points, and only
 * 40% steering in the air. Steps up {@link GreenLanternConfig#VEHICLE_CAR_STEP} blocks.
 */
public class HardLightCarEntity extends HardLightVehicleEntity {
	private static final double TOP = GreenLanternConfig.VEHICLE_CAR_TOP_BPS / 20.0;
	private static final double ACCEL = GreenLanternConfig.VEHICLE_CAR_ACCEL_BPS2 / 400.0;
	private static final double BRAKE = GreenLanternConfig.VEHICLE_CAR_BRAKE_BPS2 / 400.0;
	private static final double REVERSE = GreenLanternConfig.VEHICLE_CAR_REVERSE_BPS / 20.0;
	private static final double REVERSE_ACCEL = GreenLanternConfig.VEHICLE_CAR_REVERSE_ACCEL_BPS2 / 400.0;

	/** Smoothed A/D steering (-1..1), so a tap eases in and out instead of snapping the heading. */
	private float steer;
	/** Smoothed mouse-assist steering (-1..1). */
	private float assist;

	public HardLightCarEntity(EntityType<? extends HardLightCarEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return vehicleAttributes(GreenLanternConfig.VEHICLE_CAR_HP, GreenLanternConfig.VEHICLE_CAR_STEP);
	}

	@Override
	public int seats() {
		return GreenLanternConfig.VEHICLE_CAR_SEATS;
	}

	@Override
	protected Vec3 seat(int index) {
		// driver front-left, passenger front-right, two in the back (x+ is the car's left)
		return switch (index) {
			case 0 -> new Vec3(0.42, 0.45, 0.15);
			case 1 -> new Vec3(-0.42, 0.45, 0.15);
			case 2 -> new Vec3(0.42, 0.5, -0.85);
			default -> new Vec3(-0.42, 0.5, -0.85);
		};
	}

	@Override
	protected String controlsKey(boolean driver) {
		return driver ? "message.projecthero.green_lantern.vehicle.car_controls"
				: "message.projecthero.green_lantern.vehicle.passenger";
	}

	/**
	 * The signed speed along the nose (blocks/tick) after one tick of W ({@code forward} > 0), S (< 0) or neither -- pure, so
	 * the gametests can check the brake / reverse numbers. W while rolling backwards brakes too.
	 */
	public static double longitudinal(double speed, float forward, boolean grounded, boolean boosting) {
		if (forward > 0.01f) {
			double a = boosting ? ACCEL * 3.0 : ACCEL;
			speed = speed < 0 ? Math.min(0, speed + BRAKE) : speed + a;
		} else if (forward < -0.01f) {
			// a strong brake, then straight on into reverse in the same press
			speed = speed > 0 ? Math.max(0, speed - BRAKE) : speed - REVERSE_ACCEL;
		} else {
			speed *= grounded ? GreenLanternConfig.VEHICLE_CAR_COAST : 0.995; // rolls to a stop
			if (Math.abs(speed) < 0.01) {
				speed = 0;
			}
		}
		if (!boosting && speed > TOP) {
			return Math.max(TOP, speed - BRAKE * 0.3); // a boost's extra eases off rather than snapping back
		}
		return Mth.clamp(speed, -REVERSE, TOP * 1.2);
	}

	/**
	 * Degrees a tick at full lock for this speed (blocks/tick): nothing standing still, {@link
	 * GreenLanternConfig#VEHICLE_CAR_STEER_SLOW_DEG} from a crawl, easing (square-root curve) to {@link
	 * GreenLanternConfig#VEHICLE_CAR_STEER_FAST_DEG} at top speed.
	 */
	public static double steerRate(double speed) {
		double a = Math.abs(speed);
		double slow = GreenLanternConfig.VEHICLE_CAR_STEER_SLOW_DEG;
		double fast = GreenLanternConfig.VEHICLE_CAR_STEER_FAST_DEG;
		return Math.min(1.0, a / 0.15) * (slow - (slow - fast) * Math.sqrt(Math.min(1.0, a / TOP)));
	}

	/** Signed speed along the nose, blocks/second (gametests). */
	public double forwardBps() {
		Vec3 fwd = Vec3.directionFromRotation(0f, getYRot());
		return (vel.x * fwd.x + vel.z * fwd.z) * 20.0;
	}

	@Override
	protected void simulate(Controls in) {
		float yaw = getYRot();
		Vec3 fwd = Vec3.directionFromRotation(0f, yaw);
		double speed = vel.x * fwd.x + vel.z * fwd.z; // signed speed along the nose
		boolean grounded = onGround();
		float keyTurn = 0f;
		float assistTurn = 0f;
		if (in == null && followTarget != null) {
			// following its owner: steer at them, keep about VEHICLE_FOLLOW_DISTANCE away, at most VEHICLE_FOLLOW_CAR_BPS
			Vec3 to = followTarget.position().subtract(position());
			double d = Math.sqrt(to.x * to.x + to.z * to.z);
			double want = d > GreenLanternConfig.VEHICLE_FOLLOW_DISTANCE
					? Math.min(GreenLanternConfig.VEHICLE_FOLLOW_CAR_BPS / 20.0, (d - GreenLanternConfig.VEHICLE_FOLLOW_DISTANCE) * 0.2)
					: 0.0;
			float wantYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
			float diff = Mth.wrapDegrees(wantYaw - yaw);
			if (Math.abs(diff) > 60f) {
				want = Math.min(want, 0.25); // a tight, slow turn toward them first (5 b/s)
			}
			speed += Mth.clamp(want - speed, -BRAKE, ACCEL * 2.0);
			if (Math.abs(speed) < 0.01 && want == 0.0) {
				speed = 0;
			}
			double rate = steerRate(speed);
			assistTurn = (float) -Mth.clamp(diff, -rate, rate);
		} else {
			float strafe = in == null ? 0f : in.strafe();
			float forward = in == null ? 0f : in.forward();
			speed = longitudinal(speed, forward, grounded, boosting());
			// A/D: eased in (0.35 of the gap a tick), back to centre faster (0.5)
			steer += (strafe - steer) * (Math.abs(strafe) > Math.abs(steer) ? 0.35f : 0.5f);
			// mouse assist: W held, A/D free, rolling forward -- ease toward the view (unless looking well off the nose)
			float wantAssist = 0f;
			float diff = 0f;
			if (in != null && forward > 0.01f && Math.abs(strafe) < 0.01f && speed > 0.05) {
				diff = Mth.wrapDegrees(in.yaw() - yaw);
				if (Math.abs(diff) > 1.5f && Math.abs(diff) < GreenLanternConfig.VEHICLE_CAR_ASSIST_MAX_DEG) {
					wantAssist = -Mth.clamp(diff / 30f, -1f, 1f);
				}
			}
			assist += (wantAssist - assist) * 0.3f;
			double rate = steerRate(speed) * (grounded ? 1.0 : 0.4);
			keyTurn = (float) (steer * rate * Math.signum(speed));
			assistTurn = (float) (assist * rate);
			if (wantAssist != 0f && Math.abs(assistTurn) > Math.abs(diff)) {
				assistTurn = -diff; // never overshoots the look direction
			}
		}
		float turn = keyTurn + assistTurn;
		if (Math.abs(turn) > 1.0e-3f) {
			setYRot(yaw - turn);
			yawDelta = -keyTurn; // boat-style, the driver's view turns with A/D -- not with the mouse assist
			fwd = Vec3.directionFromRotation(0f, getYRot());
		}
		yBodyRot = yHeadRot = getYRot();
		double vy = vel.y;
		if (grounded && vy <= 0) {
			vy = -0.08; // keeps it pressed to the road over crests
		} else {
			vy = Math.max(-3.0, (vy - 0.08) * 0.98);
		}
		if (isInWater() || isInLava()) {
			vy = Math.min(0.08, vy + 0.12); // bobs up and floats
			speed *= 0.9;
		}
		// full grip: the whole speed goes along the nose, so it never slides sideways out of a turn
		vel = new Vec3(fwd.x * speed, vy, fwd.z * speed);
		moveByVel();
		if (horizontalCollision && grounded && Math.abs(speed) > 0.05) {
			// a wall it could not step: it stops there instead of grinding along / climbing it
			vel = new Vec3(vel.x * 0.5, vel.y, vel.z * 0.5);
		}
		float shown = Mth.clamp(in == null ? turn / 6f : steer + assist, -1f, 1f);
		visualTurn += (shown - visualTurn) * 0.5f;
	}

	@Override
	protected void clientTick() {
		// the wheels roll with the ground speed (radius 0.33)
		double dx = getX() - xo;
		double dz = getZ() - zo;
		Vec3 fwd = Vec3.directionFromRotation(0f, getYRot());
		double along = dx * fwd.x + dz * fwd.z;
		spin += (float) (along / 0.33);
		if (!isControlledByLocalInstance()) {
			// a remote car steers its front wheels from how fast it is turning
			float rate = Mth.wrapDegrees(getYRot() - yRotO);
			visualTurn += (Mth.clamp(-rate / 4f, -1f, 1f) - visualTurn) * 0.4f;
		}
		if (boosting() && tickCount % 2 == 0) {
			Vec3 back = local(0, 0.5, -2.3);
			level().addParticle(ParticleTypes.END_ROD, back.x, back.y, back.z, -fwd.x * 0.2, 0.02, -fwd.z * 0.2);
		}
	}
}
