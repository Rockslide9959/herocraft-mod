package com.projecthero.mod.client.bluebeetle;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.bluebeetle.BlueBeetle;
import com.projecthero.mod.bluebeetle.data.BlueBeetleState;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 (user): Khaji-Da's takeover from the player's chair. While Khaji-Da Unleashed runs the server drives the body
 * ({@code KhajiDaTakeover}); here, at the start of every client tick, the movement / jump / sprint / sneak keys are let
 * go and any clicks thrown away (so nothing the player does moves or swings him), the camera goes behind him in third
 * person (the player's own view comes back afterwards), and the body turns at the synced target ({@code fx.takeTarget})
 * the same way the server aims it, so the two never fight over his rotation. The Hulk rampage's technique.
 */
public final class BlueBeetleTakeoverClient {
	private static CameraType savedCamera;

	private BlueBeetleTakeoverClient() {
	}

	public static void startTick(Minecraft mc) {
		Player player = mc.player;
		boolean on = player != null && mc.level != null && BlueBeetle.unleashed(player);
		if (!on) {
			if (savedCamera != null) {
				mc.options.setCameraType(savedCamera);
				savedCamera = null;
			}
			return;
		}
		if (savedCamera == null) {
			savedCamera = mc.options.getCameraType();
		}
		if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
		mc.options.keyUp.setDown(false);
		mc.options.keyDown.setDown(false);
		mc.options.keyLeft.setDown(false);
		mc.options.keyRight.setDown(false);
		mc.options.keyJump.setDown(false);
		mc.options.keySprint.setDown(false);
		mc.options.keyShift.setDown(false);
		mc.options.keyAttack.setDown(false);
		mc.options.keyUse.setDown(false);
		while (mc.options.keyAttack.consumeClick()) {
			// thrown away: Khaji-Da swings, not the player
		}
		while (mc.options.keyUse.consumeClick()) {
			// likewise
		}
		face(player);
	}

	/** Turns the body at the synced target's middle (its movement led like the server leads it). */
	private static void face(Player player) {
		BlueBeetleState s = player.getAttachedOrElse(ModAttachments.BLUE_BEETLE_STATE, null);
		if (s == null || s.fx.takeTarget < 0 || !(player.level().getEntity(s.fx.takeTarget) instanceof LivingEntity t) || !t.isAlive()) {
			return;
		}
		Vec3 v = t.getDeltaMovement();
		Vec3 at = t.position().add(v.x * 2.0, t.getBbHeight() * 0.6, v.z * 2.0);
		Vec3 d = at.subtract(player.getEyePosition());
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		if (flat < 1.0e-3) {
			return;
		}
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0f;
		float pitch = Mth.clamp((float) (-Mth.atan2(d.y, flat) * Mth.RAD_TO_DEG), -85.0f, 85.0f);
		// eased, so the camera behind him swings round rather than snapping
		player.setYRot(player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * 0.5f);
		player.setYHeadRot(player.getYRot());
		player.setXRot(Mth.lerp(0.5f, player.getXRot(), pitch));
	}
}
