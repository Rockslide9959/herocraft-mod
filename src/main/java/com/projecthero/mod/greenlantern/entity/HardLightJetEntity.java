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
 * v0.17.1: the <b>Hard-Light Jet</b> -- a fighter of light (Carry Platform key, third form), pilot + 1 behind. It flies
 * where the pilot looks (the nose swings toward the view at {@link GreenLanternConfig#VEHICLE_JET_TURN_DEG} degrees a tick,
 * banking into the turn); W is throttle up to {@link GreenLanternConfig#VEHICLE_JET_TOP_BPS} b/s, S throttles back /
 * brakes, A/D add a little extra yaw. It needs airspeed to fly: below {@link GreenLanternConfig#VEHICLE_JET_STALL_BPS} b/s
 * it loses lift, the nose drops and it sinks gently (never a plummet). On the ground it taxis and lifts off once it is
 * past stall speed. R (hold) fires the twin wing cannons, Shift+R lights the afterburner -- both in
 * {@code GreenLanternVehicles}.
 */
public class HardLightJetEntity extends HardLightVehicleEntity {
	private static final double TOP = GreenLanternConfig.VEHICLE_JET_TOP_BPS / 20.0;
	private static final double ACCEL = GreenLanternConfig.VEHICLE_JET_ACCEL_BPS2 / 400.0;
	private static final double BRAKE = 30.0 / 400.0;
	private static final double STALL = GreenLanternConfig.VEHICLE_JET_STALL_BPS / 20.0;
	private static final double SINK = GreenLanternConfig.VEHICLE_JET_SINK_BPS / 20.0;

	/** Blocks/tick along the nose. */
	private double airspeed;

	public HardLightJetEntity(EntityType<? extends HardLightJetEntity> type, Level level) {
		super(type, level);
		setNoGravity(true); // its own lift / stall model (and a floating vehicle must not trip the server's anti-fly)
	}

	public static AttributeSupplier.Builder createAttributes() {
		return vehicleAttributes(GreenLanternConfig.VEHICLE_JET_HP, 0.6);
	}

	@Override
	public int seats() {
		return GreenLanternConfig.VEHICLE_JET_SEATS;
	}

	@Override
	protected Vec3 seat(int index) {
		return index == 0 ? new Vec3(0, 0.6, 0.85) : new Vec3(0, 0.72, -0.25);
	}

	@Override
	protected String controlsKey(boolean driver) {
		return driver ? "message.projecthero.green_lantern.vehicle.jet_controls"
				: "message.projecthero.green_lantern.vehicle.passenger";
	}

	/** Airspeed in blocks/second (gametests, the HUD line). */
	public double airspeedBps() {
		return airspeed * 20.0;
	}

	@Override
	protected void onNewController(Player pilot) {
		if (pilot != null && !onGround()) {
			airspeed = Math.max(airspeed, STALL * 1.15); // shaped in mid-air: it is already flying
		}
	}

	@Override
	protected void simulate(Player pilot, float strafe, float forward) {
		float yaw = getYRot();
		float pitch = getXRot();
		float turned = 0f;
		boolean grounded = onGround();
		if (pilot != null) {
			if (forward > 0.01f) {
				airspeed += boosting() ? ACCEL * 3.0 : ACCEL;
			} else if (forward < -0.01f) {
				airspeed = Math.max(0, airspeed - BRAKE);
			} else {
				airspeed *= grounded ? 0.97 : 0.999; // holds its speed in the air
			}
			float wantPitch = pilot.getXRot();
			if (grounded && airspeed < STALL) {
				wantPitch = 0f; // taxiing: level
			}
			float t = GreenLanternConfig.VEHICLE_JET_TURN_DEG;
			turned = Mth.clamp(Mth.wrapDegrees(pilot.getYRot() - yaw), -t, t) - strafe * 2.5f;
			yaw += turned;
			pitch = Mth.clamp(pitch + Mth.clamp(wantPitch - pitch, -t, t), -80f, 80f);
		} else {
			airspeed *= grounded ? 0.8 : 0.97;
			pitch += (0f - pitch) * 0.1f;
		}
		airspeed = Mth.clamp(airspeed, 0.0, TOP);
		Vec3 v = Vec3.directionFromRotation(pitch, yaw).scale(airspeed);
		if (airspeed < STALL) {
			// no lift: the nose drops and it sinks, more the slower it goes
			double lack = 1.0 - airspeed / STALL;
			if (!grounded) {
				v = new Vec3(v.x, Math.min(v.y, 0.0) * (1.0 - lack) - SINK * lack, v.z);
				pitch = Math.min(25f, pitch + (float) (1.5 * lack));
			} else {
				v = new Vec3(v.x, -0.05, v.z);
			}
		}
		setRot(yaw, pitch);
		yBodyRot = yHeadRot = yaw;
		vel = v;
		moveByVel();
		if (horizontalCollision) {
			airspeed *= 0.5; // a wall takes the speed off
		}
		visualTurn += (Mth.clamp(turned / GreenLanternConfig.VEHICLE_JET_TURN_DEG, -1f, 1f) - visualTurn) * 0.2f;
	}

	@Override
	protected void clientTick() {
		spin += (float) (0.2 + speedBps() / 60.0);
		if (!isControlledByLocalInstance()) {
			float rate = Mth.wrapDegrees(getYRot() - yRotO);
			visualTurn += (Mth.clamp(rate / GreenLanternConfig.VEHICLE_JET_TURN_DEG, -1f, 1f) - visualTurn) * 0.2f;
		}
		double bps = speedBps();
		if (bps > 30.0 && tickCount % 2 == 0) {
			for (int s = -1; s <= 1; s += 2) {
				Vec3 tip = local(s * 3.05, 0.45, -0.7);
				level().addParticle(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 0, 0, 0);
			}
		}
		if (boosting()) {
			Vec3 back = local(0, 0.55, -2.7);
			Vec3 d = Vec3.directionFromRotation(getXRot(), getYRot()).scale(-0.4);
			level().addParticle(ParticleTypes.END_ROD, back.x, back.y, back.z, d.x, d.y, d.z);
		}
	}
}
