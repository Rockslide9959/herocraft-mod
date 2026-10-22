package com.projecthero.mod.client.quirk.explosion;

import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.flight.DirectionalFlightModel.Tune;
import com.projecthero.mod.quirk.explosion.Explosion;
import com.projecthero.mod.quirk.explosion.ExplosionConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Explosion Propulsion (X, held) for the local player -- the mod's shared directional-flight model
 * ({@link DirectionalFlightModel}, the one Iron Man / Nova / Kryptonian fly) with Bakugo's own numbers, driven from the
 * client like every flight in the mod while the synced Propulsion flag is on:
 * <ul>
 *   <li><b>W / S</b> blast along / against the full 3D look (look up to climb), <b>A / D</b> slide sideways,
 *       <b>Space / Sneak</b> rise / sink;</li>
 *   <li>Sprint is the bigger blasts (faster);</li>
 *   <li>no input eases into a slowly sinking hover -- the explosions can't hold him perfectly still.</li>
 * </ul>
 * It does not take over vanilla's travel step (no {@code abilities.flying}): it sets the velocity at the start of the
 * tick from its own record of last tick's velocity, and vanilla moves him by it. A wall or the floor kills that
 * component of the record.
 */
public final class ExplosionPropulsionClient {
	private static Vec3 velocity;

	private ExplosionPropulsionClient() {
	}

	public static void reset() {
		velocity = null;
	}

	private static boolean engaged(LocalPlayer p) {
		return Explosion.propelling(p) && !p.isPassenger() && !p.isSpectator() && !p.getAbilities().flying && !p.isFallFlying();
	}

	public static Tune tune(boolean sprint) {
		return new Tune(sprint ? ExplosionConfig.PROPEL_SPRINT_SPEED : ExplosionConfig.PROPEL_SPEED, ExplosionConfig.PROPEL_VERTICAL,
				ExplosionConfig.PROPEL_STRAFE, ExplosionConfig.PROPEL_ACCELERATION, ExplosionConfig.PROPEL_REVERSE, ExplosionConfig.PROPEL_IDLE,
				0.0, 0.0, false, DirectionalFlightModel.PUSH_CARRY_TICKS, true);
	}

	/** START of the client tick: this tick's velocity. */
	public static void startTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.isPaused() || !engaged(p)) {
			velocity = null;
			return;
		}
		Tune tune = tune(p.isSprinting());
		if (velocity == null) {
			velocity = p.getDeltaMovement();
		}
		float forward = p.input.forwardImpulse;
		float strafe = p.input.leftImpulse;
		int vertical = DirectionalFlightModel.verticalInput(p.input.jumping, p.input.shiftKeyDown, false);
		boolean steering = DirectionalFlightModel.steering(forward, strafe, vertical, tune);
		Vec3 wanted = steering
				? DirectionalFlightModel.wantedVelocity(p.getLookAngle(), p.getYRot(), forward, strafe, vertical, tune)
				: new Vec3(0.0, -ExplosionConfig.PROPEL_IDLE_SINK, 0.0);
		// blasting along the floor lifts him off it
		if (steering && p.onGround() && wanted.y < 0.25) {
			wanted = new Vec3(wanted.x, 0.25, wanted.z);
		}
		Vec3 next = DirectionalFlightModel.step(velocity, wanted, steering, tune, false);
		velocity = next;
		p.setDeltaMovement(next);
		p.resetFallDistance();
	}

	/** END of the client tick: what actually happened (walls and floors stop the record). */
	public static void endTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (velocity == null || p == null) {
			return;
		}
		if (p.horizontalCollision) {
			velocity = velocity.multiply(0.3, 1.0, 0.3);
		}
		if (p.verticalCollision) {
			velocity = new Vec3(velocity.x, 0.0, velocity.z);
		}
	}
}
