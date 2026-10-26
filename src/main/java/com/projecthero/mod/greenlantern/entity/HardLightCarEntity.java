package com.projecthero.mod.greenlantern.entity;

import com.projecthero.mod.greenlantern.GreenLanternConfig;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the <b>Hard-Light Car</b> -- a sleek open-top sports car of light (Carry Platform key, second form). Seats the
 * driver + 3. W accelerates (0 to {@link GreenLanternConfig#VEHICLE_CAR_TOP_BPS} b/s in about 3.5 s), S brakes and then
 * reverses, A/D steer (sharper at low speed, gentler at speed, smoothed so it never snaps), and it steps up over 1-block
 * bumps. Shift+R (the GL R key with Shift while driving) sounds the horn and surges forward ({@link #boost}).
 */
public class HardLightCarEntity extends HardLightVehicleEntity {
	private static final double TOP = GreenLanternConfig.VEHICLE_CAR_TOP_BPS / 20.0;
	private static final double ACCEL = GreenLanternConfig.VEHICLE_CAR_ACCEL_BPS2 / 400.0;
	private static final double BRAKE = GreenLanternConfig.VEHICLE_CAR_BRAKE_BPS2 / 400.0;
	private static final double REVERSE = GreenLanternConfig.VEHICLE_CAR_REVERSE_BPS / 20.0;

	/** Smoothed steering input (-1..1), so a tap of A/D eases in and out instead of snapping the heading. */
	private float steer;

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

	@Override
	protected void simulate(Player pilot, float strafe, float forward) {
		float yaw = getYRot();
		Vec3 fwd = Vec3.directionFromRotation(0f, yaw);
		double speed = vel.x * fwd.x + vel.z * fwd.z; // signed speed along the nose
		if (pilot == null) {
			forward = 0f;
			strafe = 0f;
		}
		if (forward > 0.01f) {
			double a = boosting() ? ACCEL * 3.0 : ACCEL;
			speed = speed < 0 ? Math.min(0, speed + BRAKE) : speed + a;
		} else if (forward < -0.01f) {
			speed = speed > 0 ? Math.max(0, speed - BRAKE) : speed - ACCEL * 0.8;
		} else {
			speed *= onGround() ? 0.975 : 0.995; // rolls to a stop
			if (Math.abs(speed) < 0.004) {
				speed = 0;
			}
		}
		speed = Mth.clamp(speed, -REVERSE, TOP);
		// steering: eased input, strongest around 8-15 b/s, a gentle arc at full speed, nothing standing still
		steer += (strafe - steer) * 0.3f;
		double a = Math.abs(speed);
		double rate = Math.min(1.0, a / 0.35) * Mth.lerp(Math.min(1.0, a / TOP), 5.0, 1.8);
		float turn = (float) (steer * rate * Math.signum(speed));
		if (Math.abs(turn) > 1.0e-3f) {
			setYRot(yaw - turn);
			yawDelta = -turn;
			fwd = Vec3.directionFromRotation(0f, getYRot());
		}
		yBodyRot = yHeadRot = getYRot();
		double vy = vel.y;
		if (onGround() && vy <= 0) {
			vy = -0.08; // keeps it pressed to the road over crests
		} else {
			vy = Math.max(-3.0, (vy - 0.08) * 0.98);
		}
		if (isInWater() || isInLava()) {
			vy = Math.min(0.08, vy + 0.12); // bobs up and floats
			speed *= 0.9;
		}
		vel = new Vec3(fwd.x * speed, vy, fwd.z * speed);
		moveByVel();
		visualTurn = steer;
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
