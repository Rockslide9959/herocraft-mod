package com.projecthero.mod.greenlantern.entity;

import com.projecthero.mod.greenlantern.GreenLanternConfig;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the <b>Hard-Light Jet</b> -- a fighter of light (Carry Platform key, third form), pilot + 1 behind. R (hold)
 * fires the twin wing cannons, Shift+R lights the afterburner -- both in {@code GreenLanternVehicles}.
 *
 * <p>v0.17.1 round 2 (user: "make the jet and car easier to control"):
 * <ul>
 *   <li><b>Mouse</b>: the nose eases toward where the pilot looks ({@link GreenLanternConfig#VEHICLE_JET_TURN_EASE} of the
 *   gap a tick, at most {@link GreenLanternConfig#VEHICLE_JET_TURN_DEG} degrees) -- quick on a big turn, no wobble when it
 *   gets there. It banks into the turn and rolls itself level when the turn is done. A/D are a rudder that turns the
 *   pilot's view with it (so it never fights the mouse).</li>
 *   <li><b>Throttle</b>: W pushes the lever up, S pulls it down, and it stays where you leave it ({@link #throttle}); the
 *   airspeed follows the lever.</li>
 *   <li><b>No stall</b>: below {@link GreenLanternConfig#VEHICLE_JET_STALL_BPS} b/s the wings lose lift and the VTOL jets
 *   take over -- it hovers, nose levelling, holding its height. Space rises (and eases the throttle off, into a hover),
 *   Sneak descends, at {@link GreenLanternConfig#VEHICLE_JET_CLIMB_BPS} b/s at any speed.</li>
 *   <li><b>Take-off / landing</b>: Space lifts straight off the ground; close to the ground a descent flares down to
 *   {@link GreenLanternConfig#VEHICLE_JET_LANDING_BPS} b/s and it settles on softly. Holding Sneak lands it, and once down
 *   the usual half-second hold gets you out (in the air the hold is {@link GreenLanternConfig#VEHICLE_JET_AIR_EXIT_HOLD_TICKS}
 *   ticks, a bail-out cushioned by Slow Falling).</li>
 * </ul>
 */
public class HardLightJetEntity extends HardLightVehicleEntity {
	private static final double TOP = GreenLanternConfig.VEHICLE_JET_TOP_BPS / 20.0;
	private static final double ACCEL = GreenLanternConfig.VEHICLE_JET_ACCEL_BPS2 / 400.0;
	private static final double BRAKE = GreenLanternConfig.VEHICLE_JET_BRAKE_BPS2 / 400.0;
	private static final double STALL = GreenLanternConfig.VEHICLE_JET_STALL_BPS / 20.0;
	private static final double CLIMB = GreenLanternConfig.VEHICLE_JET_CLIMB_BPS / 20.0;
	private static final double LANDING = GreenLanternConfig.VEHICLE_JET_LANDING_BPS / 20.0;
	private static final double PARKED_DESCENT = GreenLanternConfig.VEHICLE_JET_PARKED_DESCENT_BPS / 20.0;

	/** Blocks/tick along the nose. */
	private double airspeed;
	/** The throttle lever, 0..1 (W up, S down, holds in between). */
	private float throttle;

	public HardLightJetEntity(EntityType<? extends HardLightJetEntity> type, Level level) {
		super(type, level);
		setNoGravity(true); // its own lift / hover model (and a floating vehicle must not trip the server's anti-fly)
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

	@Override
	protected int airExitHoldTicks() {
		return GreenLanternConfig.VEHICLE_JET_AIR_EXIT_HOLD_TICKS;
	}

	/** Airspeed in blocks/second (gametests, the HUD line). */
	public double airspeedBps() {
		return airspeed * 20.0;
	}

	/** The throttle lever, 0..1 (the HUD). */
	public float throttle() {
		return throttle;
	}

	/** Below lift speed: hovering on its VTOL jets (the HUD's HOVER tag). */
	public boolean hovering() {
		return airspeed < STALL && !onGround();
	}

	/** Gametests: set the airspeed / throttle directly. */
	public void setAirspeedForTest(double bps, float lever) {
		airspeed = bps / 20.0;
		throttle = lever;
	}

	@Override
	protected void onNewController(net.minecraft.world.entity.player.Player pilot) {
		if (pilot != null && !onGround()) {
			// taken over in mid-air: the lever starts where the jet already is
			throttle = (float) Mth.clamp(airspeed / TOP, 0.0, 1.0);
		}
	}

	/** How far down (blocks, up to {@code max}) the first solid thing under it is. */
	private double groundGap(double max) {
		for (double d = 0.25; d <= max; d += 0.5) {
			if (!level().noCollision(this, getBoundingBox().move(0, -d, 0))) {
				return d;
			}
		}
		return Double.MAX_VALUE;
	}

	@Override
	protected void simulate(Controls in) {
		float yaw = getYRot();
		float pitch = getXRot();
		float turned = 0f;
		// landed: on the ground, or resting a hair above it (a hover at zero height must not flicker between the two)
		boolean grounded = onGround() || !level().noCollision(this, getBoundingBox().move(0, -0.1, 0));
		double vy;
		Vec3 v;
		if (in == null && followTarget != null) {
			// following: hover a little above and behind its owner, facing the way they face
			Vec3 back = Vec3.directionFromRotation(0f, followTarget.getYRot()).scale(-GreenLanternConfig.VEHICLE_FOLLOW_DISTANCE);
			Vec3 spot = followTarget.position().add(back.x, 2.5, back.z);
			Vec3 to = spot.subtract(position());
			Vec3 move = to.scale(0.15);
			double cap = GreenLanternConfig.VEHICLE_FOLLOW_JET_BPS / 20.0;
			if (move.length() > cap) {
				move = move.normalize().scale(cap);
			}
			vel = vel.add(move.subtract(vel).scale(0.35));
			float wantYaw = to.horizontalDistance() > 6.0 ? (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f
					: followTarget.getYRot();
			turned = Mth.clamp(Mth.wrapDegrees(wantYaw - yaw) * 0.15f, -4f, 4f);
			setRot(yaw + turned, pitch + (0f - pitch) * 0.2f);
			yBodyRot = yHeadRot = getYRot();
			airspeed = vel.horizontalDistance();
			throttle = 0f;
			moveByVel();
			visualTurn += (Mth.clamp(turned / 4f, -1f, 1f) - visualTurn) * 0.2f;
			return;
		}
		if (in != null) {
			// the throttle lever: W up, S down, it holds in between; Space eases it off into a hover
			if (in.forward() > 0.01f) {
				throttle += GreenLanternConfig.VEHICLE_JET_THROTTLE_UP_PER_SEC / 20f;
			} else if (in.forward() < -0.01f) {
				throttle -= GreenLanternConfig.VEHICLE_JET_THROTTLE_DOWN_PER_SEC / 20f;
			}
			if (in.up() && !grounded) {
				throttle -= GreenLanternConfig.VEHICLE_JET_THROTTLE_DOWN_PER_SEC / 40f;
			}
			throttle = Mth.clamp(throttle, 0f, 1f);
			double target = boosting() ? TOP * 1.25 : throttle * TOP;
			double accel = boosting() ? ACCEL * 3.0 : ACCEL;
			airspeed += Mth.clamp(target - airspeed, -BRAKE, accel);
			if (grounded && throttle == 0f) {
				airspeed *= 0.85; // wheel brakes on the ground
			}
			double lift = Mth.clamp(airspeed / STALL, 0.0, 1.0);
			// the mouse: the nose eases toward the view (hovering it stays level-ish; taxiing it stays level)
			float wantPitch = grounded && airspeed < STALL ? 0f : (float) (in.pitch() * lift);
			float t = GreenLanternConfig.VEHICLE_JET_TURN_DEG;
			float ease = GreenLanternConfig.VEHICLE_JET_TURN_EASE;
			turned = Mth.clamp(Mth.wrapDegrees(in.yaw() - yaw) * ease, -t, t);
			// A/D: a rudder that turns the view with it
			float rudder = -in.strafe() * GreenLanternConfig.VEHICLE_JET_RUDDER_DEG;
			yaw += turned + rudder;
			yawDelta = rudder;
			pitch = Mth.clamp(pitch + Mth.clamp((wantPitch - pitch) * ease, -t, t), -80f, 80f);
			airspeed = Mth.clamp(airspeed, 0.0, TOP * 1.25);
			v = Vec3.directionFromRotation(pitch, yaw).scale(airspeed);
			// wings carry it at speed; under lift speed the VTOL jets hold its height (no stall, no sink)
			vy = v.y * lift + (in.up() ? CLIMB : in.down() ? -CLIMB : 0.0);
			turned += rudder;
		} else {
			// parked: it lets itself down to the ground and stops
			airspeed *= grounded ? 0.7 : 0.94;
			if (airspeed < 0.01) {
				airspeed = 0;
			}
			pitch += (0f - pitch) * 0.1f;
			v = Vec3.directionFromRotation(0f, yaw).scale(airspeed);
			vy = -PARKED_DESCENT;
		}
		if (vy < 0) {
			// soft landing: flared down close to the ground
			double gap = groundGap(3.0);
			if (gap <= 3.0) {
				vy = Math.max(vy, -Math.max(LANDING, gap * 0.12));
			}
		}
		if (grounded && vy <= 0) {
			vy = -0.04; // sitting on its skids
		}
		if (grounded && pitch > 0f && airspeed < STALL) {
			pitch += (0f - pitch) * 0.3f; // set down level
		}
		// vertical motion eased, so climbing / hovering / descending never jolt
		double vyNow = vel.y + (vy - vel.y) * 0.35;
		setRot(yaw, pitch);
		yBodyRot = yHeadRot = yaw;
		vel = new Vec3(v.x, vyNow, v.z);
		moveByVel();
		if (horizontalCollision) {
			airspeed *= 0.5; // a wall takes the speed off
		}
		// banks into the turn, rolls itself level once the nose is where you look
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
		if (hovering() && tickCount % 3 == 0) {
			// the VTOL jets: a faint downwash under the wings
			for (int s = -1; s <= 1; s += 2) {
				Vec3 under = local(s * 1.6, 0.1, -0.2);
				level().addParticle(ParticleTypes.END_ROD, under.x, under.y, under.z, 0, -0.12, 0);
			}
		}
		if (boosting()) {
			Vec3 back = local(0, 0.55, -2.7);
			Vec3 d = Vec3.directionFromRotation(getXRot(), getYRot()).scale(-0.4);
			level().addParticle(ParticleTypes.END_ROD, back.x, back.y, back.z, d.x, d.y, d.z);
		}
	}
}
