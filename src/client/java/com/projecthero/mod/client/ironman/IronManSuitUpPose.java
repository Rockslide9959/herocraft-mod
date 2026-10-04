package com.projecthero.mod.client.ironman;

import com.projecthero.mod.ironman.IronManFaceplate;
import com.projecthero.mod.ironman.suit.IronManSuitFx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.21: the body language of an Iron Man suit-up, read off the synced {@link IronManSuitFx} clocks so every viewer
 * sees it. Laid over vanilla's own animation (a weighted blend toward the target angles, eased in over
 * {@value #EASE_IN} ticks and out over {@value #EASE_OUT}):
 * <ul>
 *   <li><b>Suit-up</b> (inventory / platform): arms out to the sides and slightly raised, palms forward, while the pieces
 *       lock on; the chest lifts a touch.</li>
 *   <li><b>Receive</b> (couriers / the Mark VII pod inbound): arms wider and braced, chin up, waiting for the pieces.</li>
 *   <li><b>Case up / down</b> (Mark V): the right arm holds the case out in front while the left arm goes out; the suit
 *       climbs out of (or folds back into) the case.</li>
 *   <li><b>Suit-down</b>: arms ease out as the plates break away, then drop.</li>
 *   <li><b>Faceplate beat</b>: when the visor closes (end of a suit-up, or H) the head dips and comes back up --
 *       {@value IronManSuitFx#FACEPLATE_TICKS} ticks; opening it tips the head back slightly instead.</li>
 * </ul>
 */
public final class IronManSuitUpPose {
	private static final int EASE_IN = 6;
	private static final int EASE_OUT = 10;

	private IronManSuitUpPose() {
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (player.level() == null) {
			return;
		}
		IronManSuitFx fx = IronManSuitFx.of(player);
		if (fx == IronManSuitFx.EMPTY) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		long now = player.level().getGameTime();
		boolean touched = false;

		float age = fx.poseAge(now, partial);
		if (age >= 0f) {
			float w = Math.min(smooth(age / EASE_IN), smooth((fx.poseTicks() - age) / EASE_OUT));
			if (w > 0.001f) {
				touched = true;
				switch (fx.poseKind()) {
					case IronManSuitFx.POSE_RECEIVE -> {
						arms(model, w, -0.25f, 1.05f, -0.25f, 1.05f);
						model.head.xRot = Mth.lerp(w, model.head.xRot, model.head.xRot - 0.18f);
					}
					case IronManSuitFx.POSE_CASE_UP, IronManSuitFx.POSE_CASE_DOWN -> {
						// right arm holds the case out in front, left arm out to the side
						arms(model, w, -1.05f, 0.05f, -0.2f, 0.75f);
					}
					case IronManSuitFx.POSE_SUIT_DOWN -> {
						float mid = (float) Math.sin(Math.PI * Mth.clamp(age / Math.max(1, fx.poseTicks()), 0f, 1f));
						arms(model, w * mid, -0.15f, 0.55f, -0.15f, 0.55f);
					}
					default -> { // POSE_SUIT_UP
						arms(model, w, -0.35f, 0.62f, -0.35f, 0.62f);
						model.body.xRot = Mth.lerp(w, model.body.xRot, model.body.xRot - 0.05f);
					}
				}
			}
		}

		float fa = fx.faceplateAge(now, partial);
		if (fa >= 0f) {
			float beat = (float) Math.sin(Math.PI * fa / IronManSuitFx.FACEPLATE_TICKS);
			model.head.xRot += IronManFaceplate.isOpen(player) ? -0.16f * beat : 0.3f * beat;
			touched = true;
		}
		if (touched) {
			model.hat.copyFrom(model.head);
		}
	}

	/** Blend both arms toward (pitch, outward roll); the left arm's roll is mirrored. */
	private static void arms(HumanoidModel<?> model, float w, float rPitch, float rRoll, float lPitch, float lRoll) {
		model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, rPitch);
		model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, 0f);
		model.rightArm.zRot = Mth.lerp(w, model.rightArm.zRot, rRoll);
		model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, lPitch);
		model.leftArm.yRot = Mth.lerp(w, model.leftArm.yRot, 0f);
		model.leftArm.zRot = Mth.lerp(w, model.leftArm.zRot, -lRoll);
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}
}
