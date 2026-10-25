package com.projecthero.mod.client.flight;

import com.projecthero.mod.flight.PhysicsFlightModel;
import com.projecthero.mod.network.FlightAttitudePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * v0.16.2: the attitude half of <b>Physics Flight</b> (the Do a Barrel Roll-style mode picked on the {@code ,} Flight Mode
 * menu) for the local player; the velocity half is {@code DirectionalFlight} running {@link PhysicsFlightModel#step}.
 * While any mod flight is engaged in Physics mode:
 * <ul>
 *   <li>the mouse turns the flier about its <em>own</em> axes -- mouse Y pitches the nose, mouse X <b>rolls</b> (or yaws,
 *   per the menu), so a banked flier pulling back turns, and loops and full barrel rolls are possible;</li>
 *   <li><b>A / D</b> yaw the nose (or roll, if mouse X yaws), with a little ease-in;</li>
 *   <li>a roll carries <b>momentum</b> -- a flick keeps rolling for a moment and settles -- and with no roll input the
 *   flier <b>auto-levels</b> gently, banking into whatever turn it is making;</li>
 *   <li>the view rolls with the flier ({@link #cameraRoll}, applied by {@code PhysicsFlightCameraMixin}); the HUD
 *   stays level.</li>
 * </ul>
 * Runs once a frame ({@link #frame}, from the camera setup) so the rotation is as smooth as vanilla mouse look. The roll
 * is reported to the server a few times a second ({@link #clientTick}) so other players see the body bank.
 */
public final class PhysicsFlight {
	/** Vanilla's mouse-to-degrees factor in {@code Entity#turn}. */
	private static final double MOUSE_DEGREES = 0.15;
	/** Time constant of the roll momentum, seconds: a flick's roll is spread over roughly this long. */
	private static final float ROLL_TAU = 0.18f;
	/** A / D yaw rate at full press, degrees a second. */
	private static final float KEY_YAW_RATE = 75f;
	/** A / D roll rate at full press (mouse X yaws instead), degrees a second. */
	private static final float KEY_ROLL_RATE = 160f;
	/** Bank this many degrees per degree-a-second of turn while auto-levelling, up to {@link #MAX_AUTO_BANK}. */
	private static final float BANK_PER_TURN_RATE = 0.4f;
	private static final float MAX_AUTO_BANK = 35f;
	/** Auto-level strength: how quickly (per second) the roll eases toward level / the turn's bank. */
	private static final float LEVEL_RATE = 1.2f;
	/** Roll input within this long (seconds) holds off the auto-level. */
	private static final float LEVEL_DELAY = 0.35f;

	private static float roll;
	private static float rollVel;
	private static float keyYawRate;
	private static float keyRollRate;
	private static float turnRate;
	private static float lastYawChange;
	private static float sinceRollInput = 10f;
	private static double pendingPitch;
	private static double pendingYaw;
	private static double pendingRoll;
	private static long lastNanos;
	private static int playerIdentity;

	private static boolean sentPhysics;
	private static float sentRoll;
	private static int sendCooldown;

	private PhysicsFlight() {
	}

	private static void reset() {
		roll = 0f;
		rollVel = 0f;
		keyYawRate = 0f;
		keyRollRate = 0f;
		turnRate = 0f;
		lastYawChange = 0f;
		sinceRollInput = 10f;
		pendingPitch = 0.0;
		pendingYaw = 0.0;
		pendingRoll = 0.0;
		sentPhysics = false;
		sentRoll = 0f;
		sendCooldown = 0;
	}

	/** True while the local player is flying a mod flight in Physics mode. */
	public static boolean active(LocalPlayer player) {
		return player != null && FlightSettings.physics() && DirectionalFlight.engaged()
				&& player.getAbilities().flying && !player.isPassenger();
	}

	/** The local player's current roll, degrees (+ = right wing down). Eases back to 0 once the flight ends. */
	public static float roll() {
		return roll;
	}

	/** The roll the camera should show this frame, or 0 (setting off, front view, or not the local player's view). */
	public static float cameraRoll() {
		// v0.17.1: the Falcon's wingsuit banks the camera into its turns (its own flight, not a Physics Flight)
		if (FlightSettings.cameraRoll()) {
			float falcon = com.projecthero.mod.client.falcon.FalconFlightClient.cameraRoll();
			if (falcon != 0f) {
				return falcon;
			}
		}
		Minecraft mc = Minecraft.getInstance();
		if (roll == 0f || !FlightSettings.cameraRoll() || mc.player == null || mc.getCameraEntity() != mc.player
				|| mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
			return 0f;
		}
		return roll;
	}

	/**
	 * Mouse look for the local player ({@code Entity#turn}, raw mouse deltas). In Physics flight it is taken here instead
	 * of vanilla's yaw / pitch: returns true if consumed.
	 */
	public static boolean onMouseTurn(LocalPlayer player, double mouseX, double mouseY) {
		if (!active(player)) {
			return false;
		}
		addMouse(mouseX * MOUSE_DEGREES, mouseY * MOUSE_DEGREES);
		return true;
	}

	/** Mouse movement in degrees (vanilla-scaled): X rolls or yaws per the menu, Y pitches. */
	public static void addMouse(double xDegrees, double yDegrees) {
		pendingPitch += yDegrees;
		if (FlightSettings.mouseXRolls()) {
			double r = xDegrees * FlightSettings.rollSensitivity() * (FlightSettings.invertRoll() ? -1.0 : 1.0);
			pendingRoll += r;
			if (Math.abs(r) > 0.01) {
				sinceRollInput = 0f;
			}
		} else {
			pendingYaw += xDegrees;
		}
	}

	/** Once a frame, before the camera reads the player's rotation. */
	public static void frame() {
		long now = System.nanoTime();
		float dt = lastNanos == 0L ? 0f : Mth.clamp((now - lastNanos) / 1.0e9f, 0f, 0.1f);
		lastNanos = now;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		int identity = System.identityHashCode(player);
		if (identity != playerIdentity) {
			playerIdentity = identity;
			reset();
		}
		if (mc.isPaused() || dt <= 0f) {
			return;
		}
		if (!active(player)) {
			pendingPitch = pendingYaw = pendingRoll = 0.0;
			rollVel = keyYawRate = keyRollRate = turnRate = lastYawChange = 0f;
			// out of the flight: the view rolls back level quickly
			if (roll != 0f) {
				roll -= (float) PhysicsFlightModel.wrap(roll) * Math.min(1f, dt * 8f);
				if (Math.abs(roll) < 0.1f) {
					roll = 0f;
				}
			}
			return;
		}

		boolean keys = mc.screen == null;
		int side = (keys && mc.options.keyRight.isDown() ? 1 : 0) - (keys && mc.options.keyLeft.isDown() ? 1 : 0);
		float ease = Math.min(1f, dt * 6f);
		double yawDelta = pendingYaw;
		if (FlightSettings.mouseXRolls()) {
			keyYawRate += (side * KEY_YAW_RATE - keyYawRate) * ease;
			yawDelta += keyYawRate * dt;
		} else {
			float want = side * KEY_ROLL_RATE * FlightSettings.rollSensitivity() * (FlightSettings.invertRoll() ? -1f : 1f);
			keyRollRate += (want - keyRollRate) * ease;
			if (side != 0) {
				sinceRollInput = 0f;
			}
		}

		// roll momentum: a mouse flick becomes a roll speed that dies away, the same total roll spread over ~ROLL_TAU
		rollVel += (float) (pendingRoll / ROLL_TAU);
		double rollDelta = (rollVel + keyRollRate) * dt;
		rollVel *= (float) Math.exp(-dt / ROLL_TAU);
		if (Math.abs(rollVel) < 0.5f) {
			rollVel = 0f;
		}

		// the heading change actually flown last frame -- A / D, mouse yaw and a banked pull alike
		turnRate += (lastYawChange / dt - turnRate) * Math.min(1f, dt * 4f);
		sinceRollInput += dt;
		if (FlightSettings.autoLevel() && sinceRollInput > LEVEL_DELAY && Math.abs(rollVel) < 20f) {
			float bank = Mth.clamp(turnRate * BANK_PER_TURN_RATE, -MAX_AUTO_BANK, MAX_AUTO_BANK);
			rollDelta += PhysicsFlightModel.wrap(bank - roll) * (1.0 - Math.exp(-dt * LEVEL_RATE));
		}

		double pitchDelta = pendingPitch;
		pendingPitch = pendingYaw = pendingRoll = 0.0;
		lastYawChange = 0f;
		if (pitchDelta == 0.0 && yawDelta == 0.0 && Math.abs(rollDelta) < 1.0e-4) {
			return;
		}
		float before = player.getYRot();
		apply(player, pitchDelta, yawDelta, rollDelta);
		float change = player.getYRot() - before;
		// going over the top of a loop flips the yaw by 180 degrees: not a turn
		lastYawChange = Math.abs(change) < 45f ? change : 0f;
	}

	private static void apply(LocalPlayer player, double pitchDelta, double yawDelta, double rollDelta) {
		float oldYaw = player.getYRot();
		float oldPitch = player.getXRot();
		float[] next = PhysicsFlightModel.rotate(oldYaw, oldPitch, roll, pitchDelta, yawDelta, rollDelta);
		player.setYRot(next[0]);
		player.setXRot(Mth.clamp(next[1], -90f, 90f));
		roll = next[2];
		// like vanilla's turn: shift the previous-tick values too, so nothing interpolates the jump back
		player.yRotO += player.getYRot() - oldYaw;
		player.xRotO = Mth.clamp(player.xRotO + player.getXRot() - oldPitch, -90f, 90f);
	}

	/** Every client tick: tell the server this player's mode and roll when they change (for other viewers). */
	public static void clientTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.getConnection() == null || !ClientPlayNetworking.canSend(FlightAttitudePayload.TYPE)) {
			return;
		}
		if (sendCooldown > 0) {
			sendCooldown--;
		}
		boolean physics = FlightSettings.physics();
		float r = physics ? roll : 0f;
		boolean modeChanged = physics != sentPhysics;
		boolean leveled = r == 0f && sentRoll != 0f;
		boolean rolled = Math.abs(PhysicsFlightModel.wrap(r - sentRoll)) > 2.0 && sendCooldown == 0;
		if (modeChanged || leveled || rolled) {
			ClientPlayNetworking.send(new FlightAttitudePayload(physics, r));
			sentPhysics = physics;
			sentRoll = r;
			sendCooldown = 2;
		}
	}
}
