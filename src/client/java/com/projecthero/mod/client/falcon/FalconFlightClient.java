package com.projecthero.mod.client.falcon;

import com.projecthero.mod.falcon.Falcon;
import com.projecthero.mod.falcon.FalconConfig;
import com.projecthero.mod.falcon.network.FalconActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Falcon's wingsuit, client half. The player moves client-side, so the flight is simulated here on top of the
 * vanilla glide (diving builds speed, pulling up trades it for height, too slow and it stalls):
 * <ul>
 *   <li><b>Hold Sprint</b>: the jet boost -- pulls toward {@link FalconConfig#BOOST_SPEED} along the look; the server burns
 *   Thrust while it hears the boost is on (and turns it off when Thrust runs out).</li>
 *   <li><b>Space</b>: a wing flap -- a climb burst (costs Thrust; the server re-checks).</li>
 *   <li><b>Ground skim</b>: low and fast, the air cushion adds speed.</li>
 *   <li><b>Banking</b>: the camera rolls into turns ({@link #cameraRoll}, read by {@code PhysicsFlight.cameraRoll}).</li>
 * </ul>
 * It also re-starts the local glide whenever the server says the wings are out but the client dropped it (a sync race).
 */
public final class FalconFlightClient {
	private static boolean jumpWasDown;
	private static boolean sentBoost;
	private static int flapCooldown;
	private static long flapAt = -100L;
	private static float roll;
	private static float rollO;
	private static float lastYaw = Float.NaN;
	private static double speed;
	private static boolean stalling;
	private static boolean skimming;

	private FalconFlightClient() {
	}

	public static void reset() {
		jumpWasDown = false;
		sentBoost = false;
		flapCooldown = 0;
		flapAt = -100L;
		roll = 0f;
		rollO = 0f;
		lastYaw = Float.NaN;
		speed = 0.0;
		stalling = false;
		skimming = false;
	}

	/** Double-tap jump as the Falcon, wings folded: take off (from the ground too). */
	public static boolean wantsTakeOff(LocalPlayer player) {
		return Falcon.hasPower(player) && !Falcon.flying(player) && !player.isInWater() && !player.isPassenger() && !player.getAbilities().flying;
	}

	public static void takeOff() {
		ClientPlayNetworking.send(new FalconActionPayload(FalconActionPayload.Action.TAKE_OFF));
	}

	/** The speed shown on the HUD, blocks a second. */
	public static double speedBps() {
		return speed * 20.0;
	}

	public static boolean stalling() {
		return stalling;
	}

	public static boolean skimming() {
		return skimming;
	}

	/** -1..1: the local player's wing flap (a quick sweep after Space), 0 for anyone else. */
	public static float flap(Player player, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (player != mc.player || mc.level == null) {
			return Falcon.boosting(player) ? -0.3f : 0f;
		}
		float age = mc.level.getGameTime() - flapAt + partial;
		if (age < 0f || age > 8f) {
			return Falcon.boosting(player) ? -0.3f : 0f;
		}
		return Mth.sin(age / 8f * (float) Math.PI * 2f);
	}

	/** The camera's bank this frame, degrees (+ = right wing down), or 0. */
	public static float cameraRoll() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.getCameraEntity() != mc.player || mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
			return 0f;
		}
		float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
		return Mth.lerp(partial, rollO, roll);
	}

	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			return;
		}
		if (flapCooldown > 0) {
			flapCooldown--;
		}
		rollO = roll;
		if (!Falcon.hasPower(p)) {
			if (sentBoost) {
				sentBoost = false;
			}
			roll *= 0.8f;
			lastYaw = Float.NaN;
			stalling = false;
			skimming = false;
			speed = p.getDeltaMovement().length();
			return;
		}
		boolean flying = Falcon.flying(p);
		// the server says the wings are out: keep the local glide going (it can drop it before the state arrives)
		if (flying && !p.isFallFlying() && !p.onGround() && !p.isInWater() && p.getDeltaMovement().y < 0.2) {
			p.startFallFlying();
			p.connection.send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
		}
		boolean gliding = flying && p.isFallFlying();
		boolean jumpDown = mc.options.keyJump.isDown();
		boolean jumpPressed = jumpDown && !jumpWasDown;
		jumpWasDown = jumpDown;
		Vec3 v = p.getDeltaMovement();
		Vec3 look = p.getLookAngle();

		// Sprint: the jet boost
		boolean wantBoost = gliding && mc.options.keySprint.isDown() && Falcon.thrust(p) > 0.5f && mc.screen == null;
		if (wantBoost != sentBoost) {
			sentBoost = wantBoost;
			ClientPlayNetworking.send(new FalconActionPayload(wantBoost ? FalconActionPayload.Action.BOOST_ON : FalconActionPayload.Action.BOOST_OFF));
		}
		if (wantBoost) {
			Vec3 target = look.scale(FalconConfig.BOOST_SPEED);
			v = v.add(look.scale(FalconConfig.BOOST_PUSH)).add(target.subtract(v).scale(FalconConfig.BOOST_PULL));
		}
		// Space: a wing flap
		if (gliding && jumpPressed && flapCooldown <= 0 && Falcon.thrust(p) >= FalconConfig.FLAP_COST && mc.screen == null) {
			flapCooldown = FalconConfig.FLAP_COOLDOWN;
			flapAt = mc.level.getGameTime();
			v = new Vec3(v.x, Math.max(v.y, 0.0) + FalconConfig.FLAP_LIFT, v.z).add(look.scale(FalconConfig.FLAP_FORWARD));
			ClientPlayNetworking.send(new FalconActionPayload(FalconActionPayload.Action.FLAP));
		}
		// the ground skim
		skimming = false;
		if (gliding && v.horizontalDistance() > FalconConfig.SKIM_MIN_SPEED) {
			Vec3 from = p.position();
			BlockHitResult hit = mc.level.clip(new ClipContext(from, from.add(0, -FalconConfig.SKIM_HEIGHT, 0), ClipContext.Block.COLLIDER,
					ClipContext.Fluid.ANY, p));
			if (hit.getType() != HitResult.Type.MISS) {
				skimming = true;
				Vec3 flat = new Vec3(v.x, 0, v.z).normalize();
				if (v.horizontalDistance() < FalconConfig.SKIM_CAP) {
					v = v.add(flat.scale(FalconConfig.SKIM_ACCEL));
				}
				if (mc.level.getGameTime() % 2 == 0) {
					Vec3 g = hit.getLocation();
					mc.level.addParticle(ParticleTypes.CLOUD, g.x, g.y + 0.1, g.z, -v.x * 0.2, 0.05, -v.z * 0.2);
				}
			}
		}
		if (gliding) {
			p.setDeltaMovement(v);
		}
		speed = v.length();
		stalling = gliding && v.length() < FalconConfig.STALL_SPEED && p.getXRot() < 10f;

		// the bank: the camera rolls into the turn, eased
		float yaw = p.getYRot();
		float target = 0f;
		if (gliding && !Float.isNaN(lastYaw)) {
			float turn = Mth.wrapDegrees(yaw - lastYaw);
			target = Mth.clamp(turn * 2.4f, -38f, 38f);
		}
		lastYaw = yaw;
		roll += (target - roll) * (gliding ? 0.18f : 0.25f);
		if (Math.abs(roll) < 0.01f) {
			roll = 0f;
		}
	}
}
