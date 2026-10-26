package com.projecthero.mod.client.falcon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.projecthero.mod.falcon.Falcon;
import com.projecthero.mod.falcon.network.FalconActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 (second pass): the Falcon's jet flight, client half. The movement itself is the shared directional flight
 * ({@code client.flight.DirectionalFlight} with {@code FalconFlight.tune}); this class only adds the feel:
 * <ul>
 *   <li><b>the bank</b>: every Falcon in flight rolls into his turns -- the camera for the local player
 *   ({@link #cameraRoll}, read by {@code PhysicsFlight.cameraRoll}) and the body for everyone
 *   ({@link #bodyRoll}, read by {@code FlightPoseHelper.roll}), worked out on each client from the flier's own yaw;</li>
 *   <li>the speed readout on the HUD;</li>
 *   <li>the double-tap jump gate (take off / drop out).</li>
 * </ul>
 */
public final class FalconFlightClient {
	/** entity id -> {roll, rollO, lastYaw (NaN = none), last x, last y, last z, speed b/t}. */
	private static final Map<Integer, double[]> BANK = new HashMap<>();

	private FalconFlightClient() {
	}

	public static void reset() {
		BANK.clear();
	}

	/** Double-tap jump as the Falcon: take off (on the ground or in the air) or drop out of the flight. */
	public static boolean wantsTakeOff(LocalPlayer player) {
		if (!Falcon.hasPower(player) || player.isInWater() || player.isPassenger()) {
			return false;
		}
		// creative flight is vanilla's own double-tap
		return Falcon.flying(player) || !player.getAbilities().instabuild;
	}

	public static void takeOff() {
		ClientPlayNetworking.send(new FalconActionPayload(FalconActionPayload.Action.TOGGLE_FLIGHT));
	}

	/** The local player's speed, blocks a second (the HUD). */
	public static double speedBps() {
		Minecraft mc = Minecraft.getInstance();
		double[] b = mc.player == null ? null : BANK.get(mc.player.getId());
		return b == null ? 0.0 : b[6] * 20.0;
	}

	/** The camera's bank this frame, degrees (+ = right wing down), or 0. */
	public static float cameraRoll() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.getCameraEntity() != mc.player || mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
			return 0f;
		}
		return bodyRoll(mc.player, mc.getTimer().getGameTimeDeltaPartialTick(false));
	}

	/** This Falcon's bank this frame, degrees (+ = right side down), 0 when not flying. */
	public static float bodyRoll(Player player, float partial) {
		double[] b = BANK.get(player.getId());
		if (b == null) {
			return 0f;
		}
		return (float) Mth.lerp(partial, b[1], b[0]);
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			BANK.clear();
			return;
		}
		Set<Integer> seen = new HashSet<>();
		for (Player p : mc.level.players()) {
			if (!Falcon.hasPower(p)) {
				continue;
			}
			seen.add(p.getId());
			double[] b = BANK.computeIfAbsent(p.getId(), k -> new double[] { 0, 0, Double.NaN, p.getX(), p.getY(), p.getZ(), 0 });
			boolean flying = Falcon.flying(p) && !p.onGround();
			double dx = p.getX() - b[3], dy = p.getY() - b[4], dz = p.getZ() - b[5];
			b[3] = p.getX();
			b[4] = p.getY();
			b[5] = p.getZ();
			double sp = Math.sqrt(dx * dx + dy * dy + dz * dz);
			b[6] = sp > 12.0 ? b[6] : b[6] + (sp - b[6]) * 0.5;
			b[1] = b[0];
			float yaw = p.getYRot();
			double target = 0.0;
			if (flying && !Double.isNaN(b[2])) {
				double turn = Mth.wrapDegrees(yaw - (float) b[2]);
				// harder banks the faster he flies: a hover turn barely tips the wings
				double speedFactor = Mth.clamp(sp / 1.0, 0.25, 1.0);
				target = Mth.clamp(turn * 2.6 * speedFactor, -40.0, 40.0);
			}
			b[2] = yaw;
			b[0] += (target - b[0]) * (flying ? 0.2 : 0.3);
			if (Math.abs(b[0]) < 0.01) {
				b[0] = 0.0;
			}
		}
		BANK.keySet().retainAll(seen);
	}
}
