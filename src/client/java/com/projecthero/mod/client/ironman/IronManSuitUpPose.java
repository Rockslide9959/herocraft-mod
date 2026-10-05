package com.projecthero.mod.client.ironman;

import com.projecthero.mod.ironman.IronManFaceplate;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitPoses;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.21: the body language of an Iron Man suit-up, read off the synced {@link IronManSuitFx} clocks so every viewer
 * sees it, laid over vanilla's own animation as a weighted blend.
 * <ul>
 *   <li><b>Suit-up / suit-down</b> (v0.14.28): the pose follows the piece that is building (or un-building) right now
 *       -- boots: looking down, a foot lifted then planted; leggings: wide braced stance, arms out; chestplate: arms
 *       spread, chest out, head up; helmet: hand to the face, head bowed, then the head snaps up. All of it is the pure
 *       function {@link IronManSuitPoses#sample} -- no random variants, no servo tremor -- and it is gone 0.25 s after
 *       the last piece finishes.</li>
 *   <li><b>Receive</b> (couriers / the Mark VII pod inbound): arms out, chin up, until the first piece lands.</li>
 *   <li><b>Case up / down</b> (Mark V): the right arm holds the case out in front while the left arm goes out.</li>
 *   <li><b>Faceplate beat</b>: when the visor closes the head dips and comes back up ({@value IronManSuitFx#FACEPLATE_TICKS}
 *       ticks); opening it tips the head back slightly instead.</li>
 * </ul>
 */
public final class IronManSuitUpPose {
	private static final int EASE_IN = 6;
	private static final int EASE_OUT = 5;

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

		IronManSuitPoses.Sample s = IronManSuitPoses.sample(fx, now, partial);
		if (s != null && s.weight() > 0.001f) {
			apply(model, s.weight(), s.key());
			touched = true;
		} else {
			float age = fx.poseAge(now, partial);
			if (age >= 0f) {
				float w = Math.min(smooth(age / EASE_IN), smooth((fx.poseTicks() - age) / EASE_OUT));
				switch (fx.poseKind()) {
					case IronManSuitFx.POSE_RECEIVE -> {
						// only until the first piece lands -- after that the per-piece pose (or nothing) takes over
						if (s == null && !anyPieceSince(fx, fx.poseStart()) && w > 0.001f) {
							arms(model, w, -0.25f, 1.05f, -0.25f, 1.05f);
							model.head.xRot = Mth.lerp(w, model.head.xRot, model.head.xRot - 0.18f);
							touched = true;
						}
					}
					case IronManSuitFx.POSE_MK5_UP, IronManSuitFx.POSE_MK5_DOWN -> {
						// v0.14.29: case out in both hands -> onto the chest -> arms out (and the reverse)
						float[] mk = com.projecthero.mod.ironman.suit.IronManMk5Suitcase.pose(
								fx.poseKind() == IronManSuitFx.POSE_MK5_UP, age);
						if (mk != null && mk[0] > 0.001f) {
							apply(model, mk[0], java.util.Arrays.copyOfRange(mk, 1, mk.length));
							touched = true;
						}
					}
					case IronManSuitFx.POSE_CASE_UP, IronManSuitFx.POSE_CASE_DOWN -> {
						if (w > 0.001f) {
							arms(model, w, -1.05f, 0.05f, -0.2f, 0.75f);
							touched = true;
						}
					}
					default -> {
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

	private static boolean anyPieceSince(IronManSuitFx fx, long since) {
		for (int bit = 0; bit < 4; bit++) {
			if (fx.assembling(bit) && fx.start(bit) >= since && fx.start(bit) > 0L) {
				return true;
			}
		}
		return false;
	}

	/** Blend every limb and the head toward {@code k} by {@code w}. */
	private static void apply(HumanoidModel<?> model, float w, float[] k) {
		model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, k[IronManSuitPoses.RAX]);
		model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, k[IronManSuitPoses.RAY]);
		model.rightArm.zRot = Mth.lerp(w, model.rightArm.zRot, k[IronManSuitPoses.RAZ]);
		model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, k[IronManSuitPoses.LAX]);
		model.leftArm.yRot = Mth.lerp(w, model.leftArm.yRot, k[IronManSuitPoses.LAY]);
		model.leftArm.zRot = Mth.lerp(w, model.leftArm.zRot, k[IronManSuitPoses.LAZ]);
		model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, k[IronManSuitPoses.RLX]);
		model.rightLeg.zRot = Mth.lerp(w, model.rightLeg.zRot, k[IronManSuitPoses.RLZ]);
		model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, k[IronManSuitPoses.LLX]);
		model.leftLeg.zRot = Mth.lerp(w, model.leftLeg.zRot, k[IronManSuitPoses.LLZ]);
		model.head.xRot = Mth.lerp(w, model.head.xRot, k[IronManSuitPoses.HX]);
		model.head.yRot = Mth.lerp(w * 0.8f, model.head.yRot, k[IronManSuitPoses.HY]);
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
