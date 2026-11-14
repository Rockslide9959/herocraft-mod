package com.projecthero.mod.client.doctorfate;

import com.projecthero.mod.client.FlightPoseHelper;
import com.projecthero.mod.doctorfate.DoctorFate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.4 Doctor Fate's own limb poses, set on the vanilla humanoid model from {@code HumanoidModelMixin} after the shared flight
 * pose (the suit layer copies the model, so the suit follows). Every pose sets the limbs outright (weighted), so nothing of the
 * walk cycle or the shared flight limbs shows through.
 * <ul>
 *   <li><b>Flight</b> (user: "fix the flight animation ... make it look better"): a regal levitation. Hanging still: upright, arms
 *   held out low to the sides with the palms down, breathing slowly; legs together, one knee a touch bent, toes pointed, a slow
 *   sway. Flying along, the arms ease back; at full speed (the body laid along the flight) the arms sweep back along the sides and
 *   the legs lie straight together. Weighted by the shared flight feel, so it eases in at lift-off and out on landing.</li>
 *   <li><b>The Helmet of Fate</b> ({@link FateHelmetAnim}): both hands on the helmet wherever it is (chest, overhead, settling onto
 *   the head, lifted off), and the main hand raised to catch it while a summon flies in.</li>
 * </ul>
 * Negative {@code xRot} swings an arm forward / up; positive {@code zRot} moves the right arm out (the left arm mirrors).
 */
public final class FatePose {
	private FatePose() {
	}

	public static void apply(Player player, HumanoidModel<?> model, float ageInTicks) {
		if (player.level() == null || !DoctorFate.hasPower(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		flight(player, model, ageInTicks, partial);
		FateHelmetAnim a = FateHelmetAnim.of(player, partial);
		if (a.reach() > 0.001f) {
			reach(player, model, a.reach(), ageInTicks);
		}
		if (a.arms() > 0.001f) {
			holdHelmet(model, a);
		}
	}

	// ---------------------------------------------------------------- flight

	private static void flight(Player player, HumanoidModel<?> model, float age, float partial) {
		float w = Mth.clamp(FlightPoseHelper.limbWave(player, partial) * 1.25f, 0f, 1f);
		if (w <= 0.001f || (!DoctorFate.isFlying(player) && w < 0.02f)) {
			return;
		}
		float pace = FlightPoseHelper.pace(player, partial);
		float fast = smooth((Math.abs(FlightPoseHelper.lean(player, partial)) - 30f) / 50f); // the body laid along the flight
		float along = Math.max(smooth(pace * 1.6f), fast);
		float breathe = Mth.sin(age * 0.085f);
		float sway = Mth.sin(age * 0.055f + 0.8f);

		// arms: out low to the sides, palms down (hover) -> eased back (flying along) -> swept back along the sides (full speed)
		float armX = lerp3(along, fast, -0.28f + 0.05f * breathe, 0.25f, 0.42f);
		float armZ = lerp3(along, fast, 0.62f + 0.07f * breathe, 0.38f, 0.16f);
		float armY = lerp3(along, fast, -0.12f, 0f, 0f);
		boolean swinging = model.attackTime > 0f;
		boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
		if (!(swinging && rightMain)) {
			pose(model.rightArm, w, armX, armY, armZ);
		}
		if (!(swinging && !rightMain)) {
			pose(model.leftArm, w, armX - 0.04f * sway, -armY, -armZ);
		}

		// legs: together, the right knee a touch forward and toes pointed, a slow sway (hover) -> straight together (speed)
		float rLeg = lerp3(along, fast, -0.16f + 0.05f * sway, 0.08f, 0.04f);
		float lLeg = lerp3(along, fast, 0.10f - 0.04f * sway, 0.14f, 0.06f);
		float legZ = lerp3(along, fast, 0.03f, 0.02f, 0.01f);
		pose(model.rightLeg, w, rLeg, 0f, legZ);
		pose(model.leftLeg, w, lLeg, 0f, -legZ);
	}

	/** Hover -> flying along -> full speed. */
	private static float lerp3(float along, float fast, float hover, float cruise, float speed) {
		return Mth.lerp(fast, Mth.lerp(along, hover, cruise), speed);
	}

	// ---------------------------------------------------------------- the helmet

	/** The summon: the main hand raised high, open, to catch the helmet flying in. */
	private static void reach(Player player, HumanoidModel<?> model, float w, float age) {
		boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
		ModelPart main = rightMain ? model.rightArm : model.leftArm;
		float side = rightMain ? 1f : -1f;
		pose(main, w, -2.55f + 0.04f * Mth.sin(age * 0.6f), -0.25f * side, 0.18f * side);
	}

	/** Both arms aimed at the helmet's middle (Nova's suit-up rig), so the hands are on its sides wherever it is. */
	static void holdHelmet(HumanoidModel<?> model, FateHelmetAnim a) {
		float w = a.arms();
		float cy = model.head.y + a.hy() - 4f; // the helmet's middle (the head cube runs 8 px up from its pivot)
		float cz = model.head.z + a.hz();
		for (ModelPart arm : new ModelPart[] { model.rightArm, model.leftArm }) {
			float x = (float) Math.atan2(cz - arm.z, cy - arm.y);
			if (x > 0f) {
				x -= (float) (Math.PI * 2.0);
			}
			float in = arm == model.rightArm ? -0.12f : 0.12f; // palms pressed to its sides
			pose(arm, w, x, 0f, in);
		}
		if (a.held()) {
			model.head.xRot = Mth.lerp(w, model.head.xRot, -0.12f * (a.hy() < -4f ? 1f : 0f));
			model.head.yRot = Mth.lerp(w, model.head.yRot, 0f);
			model.hat.copyFrom(model.head);
		}
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
