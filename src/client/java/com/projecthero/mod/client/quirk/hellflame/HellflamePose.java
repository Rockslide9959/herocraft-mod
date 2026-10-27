package com.projecthero.mod.client.quirk.hellflame;

import static com.projecthero.mod.client.quirk.QuirkKeyframes.f;
import static com.projecthero.mod.client.quirk.QuirkKeyframes.rest;

import com.projecthero.mod.client.HipBend;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.hellflame.Hellflame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 revamp: Endeavor's poses on the vanilla humanoid model, set at the end of {@code setupAnim}.
 *
 * <p><b>Keyframed moves</b> (v0.17.1: the shared 16-column {@link QuirkKeyframes} format, full leg rotation): each is a list
 * of frames in radians, smoothstep-interpolated, faded in over
 * 2 ticks and out over the last 3, driven by the synced {@code animId / animStart} so every viewer sees the same. Negative
 * arm X swings a limb forward / up; a positive right-arm Z lifts it out to his side; a negative right-arm Y swings it out to
 * his right. The torso bends from the hips ({@link HipBend}).
 *
 * <p><b>Held poses</b> from his running moves: the jets of Fire Jet Flight (arms angled down and back like rudders, legs
 * together, leaning into the flight; a hover keeps him upright, palms down), the Prominence charge (the right fist drawn
 * back at the hip, the left arm out sighting along the target, a braced stance, shaking as it fills), the beam (the right
 * arm locked along the look, the left hand gripping the forearm, legs braced wide) and the Jet Burn dash.
 */
public final class HellflamePose {
	private static final float[][] JET = {
			rest(0),
			f(3, 0.75f, 0.1f, 0.3f, -0.9f, 0.3f, -0.15f, 0.12f, 0.55f, 0.3f, 0, 0, -0.35f, 0, 0, 0),
			f(5, -1.65f, -0.1f, 0, 0.55f, 0.2f, -0.25f, 0.32f, -0.45f, 0.6f, 0, 0, -0.45f, 0, 0, 0.05f),
			f(10, -1.65f, -0.1f, 0, 0.55f, 0.2f, -0.25f, 0.32f, -0.45f, 0.6f, 0, 0, -0.45f, 0, 0, 0.05f),
			rest(14) };
	private static final float[][] FLIGHT = {
			rest(0),
			f(3, 0.45f, 0, 0.45f, 0.45f, 0, -0.45f, 0.35f, 0, -0.55f, 0, 0, -0.55f, 0, 0, 0.15f),
			f(6, 0.6f, 0, 0.6f, 0.6f, 0, -0.6f, -0.12f, 0, 0.12f, 0, 0, 0.12f, 0, 0, -0.25f),
			f(10, 0.4f, 0, 0.4f, 0.4f, 0, -0.4f, 0, 0, 0, 0, 0, 0, 0, 0, 0) };
	private static final float[][] SPIDER = {
			rest(0),
			f(3, -1.45f, -0.85f, 0.15f, -0.35f, 0, -0.3f, 0.05f, -0.35f, 0.15f, 0, 0, -0.15f, 0, 0, 0),
			f(6, -1.5f, 0.65f, 0, -0.35f, 0, -0.3f, 0.08f, 0.38f, 0.15f, 0, 0, -0.15f, 0, 0, 0),
			f(11, -1.45f, 0.7f, 0, -0.35f, 0, -0.3f, 0.08f, 0.4f, 0.15f, 0, 0, -0.15f, 0, 0, 0),
			rest(16) };
	private static final float[][] MESH = {
			rest(0),
			f(4, -1.55f, 0.18f, 0, -1.55f, -0.18f, 0, 0.1f, 0, 0.2f, 0, 0, -0.2f, 0, 0, 0.05f),
			f(8, -1.45f, -0.85f, 0.15f, -1.45f, 0.85f, -0.15f, 0.05f, 0, 0.2f, 0, 0, -0.2f, 0, 0, 0.1f),
			f(13, -1.45f, -0.85f, 0.15f, -1.45f, 0.85f, -0.15f, 0.05f, 0, 0.2f, 0, 0, -0.2f, 0, 0, 0.1f),
			rest(18) };
	private static final float[][] WALL = {
			rest(0),
			f(4, -1.3f, 0.95f, 0, -0.4f, 0, -0.3f, 0.12f, 0.45f, -0.2f, 0, 0, 0.2f, 0, 0, 0.1f),
			f(8, -1.4f, -1.0f, 0.35f, -0.5f, 0, -0.6f, 0.2f, -0.45f, 0.35f, 0, 0, -0.35f, 0, 0, 0.15f),
			f(13, -1.4f, -1.0f, 0.35f, -0.5f, 0, -0.6f, 0.2f, -0.45f, 0.35f, 0, 0, -0.35f, 0, 0, 0.15f),
			rest(18) };
	private static final float[][] CLOAK = {
			rest(0),
			f(5, -0.4f, 0.3f, 0.95f, -0.4f, -0.3f, -0.95f, 0.12f, 0, 0.1f, 0, 0, -0.1f, 0, 0, -0.2f),
			f(11, -0.45f, 0.3f, 1.05f, -0.45f, -0.3f, -1.05f, 0.15f, 0, 0.1f, 0, 0, -0.1f, 0, 0, -0.25f),
			rest(16) };
	private static final float[][] PILLAR = {
			rest(0),
			f(5, 0.35f, 0, 0.35f, -0.4f, 0, -0.4f, 0.4f, 0.2f, -0.3f, 0, 0, 0.3f, 0, 0, 0.25f),
			f(9, -2.9f, -0.1f, 0.1f, -0.3f, 0, -0.45f, -0.15f, -0.1f, 0.2f, 0, 0, -0.2f, 0, 0, -0.45f),
			f(14, -2.9f, -0.1f, 0.1f, -0.3f, 0, -0.45f, -0.15f, -0.1f, 0.2f, 0, 0, -0.2f, 0, 0, -0.45f),
			rest(18) };
	private static final float[][] VANISH = {
			f(0, 0.5f, 0, 0.45f, -1.0f, 0.45f, 0, 0.35f, 0.65f, 0.4f, 0, 0, -0.4f, 0, 0, 0.1f),
			f(3, 0.6f, 0, 0.5f, -1.05f, 0.45f, 0, 0.38f, 0.7f, 0.45f, 0, 0, -0.45f, 0, 0, 0.1f),
			f(5, -1.55f, -0.65f, 0.3f, 0.4f, 0.2f, -0.2f, 0.22f, -0.55f, 0.5f, 0, 0, -0.3f, 0, 0, 0),
			f(10, -1.55f, -0.65f, 0.3f, 0.4f, 0.2f, -0.2f, 0.22f, -0.55f, 0.5f, 0, 0, -0.3f, 0, 0, 0),
			rest(14) };
	private static final float[][] COOL = {
			rest(0),
			f(4, 0, 0, 1.45f, 0, 0, -1.45f, -0.15f, 0, 0.1f, 0, 0, -0.1f, 0, 0, -0.6f),
			f(16, -0.1f, 0, 1.4f, -0.1f, 0, -1.4f, -0.18f, 0, 0.1f, 0, 0, -0.1f, 0, 0, -0.65f),
			rest(22) };
	private static final float[][] PLUS = {
			rest(0),
			f(6, 0.35f, 0.2f, 0.5f, 0.35f, -0.2f, -0.5f, 0.4f, 0, -0.25f, 0, 0, 0.25f, 0, 0, 0.3f),
			f(10, -0.45f, 0, 1.95f, -0.45f, 0, -1.95f, -0.22f, 0, 0.25f, 0, 0, -0.25f, 0, 0, -0.6f),
			f(24, -0.5f, 0, 2.0f, -0.5f, 0, -2.0f, -0.25f, 0, 0.25f, 0, 0, -0.25f, 0, 0, -0.65f),
			rest(30) };
	/** Eight jabs, alternating fists, every 4 ticks (built in the static block). */
	private static final float[][] BARRAGE;

	static {
		int jabs = 8;
		float[][] f = new float[2 + jabs * 2 + 1][];
		f[0] = f(0, -0.9f, 0, 0, -0.9f, 0, 0, 0.1f, 0, 0.25f, 0, 0, -0.25f, 0, 0, 0);
		int k = 1;
		f[k++] = f(1, -0.9f, 0.2f, 0, -0.9f, -0.2f, 0, 0.15f, 0, 0.3f, 0, 0, -0.3f, 0, 0, 0);
		for (int j = 0; j < jabs; j++) {
			boolean right = j % 2 == 0;
			float t = 1 + j * 4;
			// out: the jabbing arm straight along the look, the body turned into it
			f[k++] = f(t + 1.5f, right ? -1.62f : -0.75f, right ? -0.05f : 0.25f, 0, right ? -0.75f : -1.62f, right ? -0.25f : 0.05f, 0,
					0.18f, right ? -0.3f : 0.3f, 0.3f, 0, 0, -0.3f, 0, 0, 0);
			// back to the guard
			f[k++] = f(t + 3.5f, -0.95f, 0.2f, 0, -0.95f, -0.2f, 0, 0.15f, 0, 0.3f, 0, 0, -0.3f, 0, 0, 0);
		}
		f[k] = rest(1 + jabs * 4 + 3);
		BARRAGE = f;
	}

	private HellflamePose() {
	}

	static float[][] frames(int animId) {
		return switch (animId) {
			case Hellflame.ANIM_JET -> JET;
			case Hellflame.ANIM_FLIGHT -> FLIGHT;
			case Hellflame.ANIM_SPIDER -> SPIDER;
			case Hellflame.ANIM_MESH -> MESH;
			case Hellflame.ANIM_WALL -> WALL;
			case Hellflame.ANIM_CLOAK -> CLOAK;
			case Hellflame.ANIM_BARRAGE -> BARRAGE;
			case Hellflame.ANIM_PILLAR -> PILLAR;
			case Hellflame.ANIM_VANISH -> VANISH;
			case Hellflame.ANIM_COOL -> COOL;
			case Hellflame.ANIM_PLUS -> PLUS;
			default -> null;
		};
	}

	/** The pose's length in ticks (0: none). */
	public static int length(int animId) {
		if (animId == Hellflame.ANIM_OVERHEAT) {
			return 40;
		}
		float[][] f = frames(animId);
		return f == null ? 0 : (int) f[f.length - 1][0];
	}

	/** Pose values at {@code tick} (fractional) of {@code f}, or null past the end ({@link QuirkKeyframes}, 16 columns). */
	static float[] sample(float[][] f, float tick) {
		return QuirkKeyframes.sample(f, tick);
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!Hellflame.is(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float ageTicks = player.tickCount + partial;
		ModelPart r = model.rightArm, l = model.leftArm;
		QuirkState s = Quirks.peek(player);
		boolean keyframed = false;
		float animAge = 0f;
		int animId = s == null ? 0 : s.animId;
		if (s != null && animId >= 20 && animId <= 39 && player.level() != null) {
			animAge = player.level().getGameTime() - s.animStart + partial;
			keyframed = animAge >= 0f && animAge < length(animId);
		}

		// ---- held poses
		if (Hellflame.flying(player) && !player.onGround()) {
			float speed = (float) Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
			float lean = Mth.clamp(speed * 1.4f, 0f, 1.0f);
			float sw = Mth.sin(ageTicks * 0.3f) * 0.05f;
			HipBend.pitch(model, 0.8f, 0.12f + 0.5f * lean);
			// hovering: palms angled down at his sides; flying: swept back like rudders
			pose(r, 1f, 0.25f + 0.5f * lean + sw, 0f, 0.45f - 0.1f * lean);
			pose(l, 1f, 0.25f + 0.5f * lean - sw, 0f, -0.45f + 0.1f * lean);
			model.rightLeg.xRot = 0.1f + 0.35f * lean + sw;
			model.leftLeg.xRot = 0.05f + 0.35f * lean - sw;
			model.rightLeg.yRot = 0f;
			model.leftLeg.yRot = 0f;
			model.rightLeg.zRot = 0.05f * (1f - lean);
			model.leftLeg.zRot = -0.05f * (1f - lean);
			model.head.xRot = Mth.lerp(lean * 0.6f, model.head.xRot, -0.6f);
		}
		if (Hellflame.charging(player)) {
			float c = Hellflame.charge(player, partial);
			float shake = c > 0.6f ? Mth.sin(ageTicks * 2.6f) * 0.05f * c : 0f;
			HipBend.pitch(model, 0.8f, 0.18f);
			model.body.yRot = Mth.lerp(0.8f, model.body.yRot, 0.55f * c + 0.15f);
			// the right fist drawn back at the hip, the left arm out sighting along the target
			pose(r, 1f, 0.75f + 0.25f * c + shake, 0.25f, 0.35f);
			pose(l, 1f, -1.45f + model.head.xRot, model.head.yRot + 0.25f, 0f);
			model.rightLeg.xRot = 0.45f;
			model.leftLeg.xRot = -0.45f;
			model.head.xRot = Mth.lerp(0.4f, model.head.xRot, model.head.xRot + 0.05f);
		}
		if (Hellflame.beaming(player)) {
			float kick = Mth.sin(ageTicks * 1.7f) * 0.025f;
			HipBend.pitch(model, 0.8f, 0.14f);
			pose(r, 1f, -1.5708f + model.head.xRot + kick, model.head.yRot, 0f);
			pose(l, 1f, -1.3f + model.head.xRot - kick, model.head.yRot + 0.7f, 0f);
			model.body.yRot = Mth.lerp(0.6f, model.body.yRot, -0.2f);
			model.rightLeg.xRot = 0.5f;
			model.leftLeg.xRot = -0.5f;
			model.rightLeg.zRot = 0.08f;
			model.leftLeg.zRot = -0.08f;
		}
		if (Hellflame.dashing(player) && !keyframed) {
			pose(r, 1f, -1.6f + model.head.xRot, -0.1f, 0f);
			pose(l, 1f, 0.6f, 0f, -0.25f);
			HipBend.pitch(model, 0.8f, 0.3f);
			model.body.yRot = -0.25f;
			model.rightLeg.xRot = 0.6f;
			model.leftLeg.xRot = -0.4f;
		}

		if (!keyframed) {
			model.hat.copyFrom(model.head);
			return;
		}
		int len = length(animId);
		float w = Math.max(0f, Math.min(1f, Math.min(animAge / 2f, (len - animAge) / 3f)));
		if (animId == Hellflame.ANIM_VANISH) {
			w = Math.max(0f, Math.min(1f, (len - animAge) / 3f)); // he appears already coiled: no fade in
		}
		if (animId == Hellflame.ANIM_OVERHEAT) {
			overheat(player, model, w, animAge);
			model.hat.copyFrom(model.head);
			return;
		}
		float[] p = sample(frames(animId), animAge);
		if (p != null) {
			// the aimed moves follow the look up and down
			float aim = switch (animId) {
				case Hellflame.ANIM_JET, Hellflame.ANIM_SPIDER, Hellflame.ANIM_MESH, Hellflame.ANIM_BARRAGE, Hellflame.ANIM_VANISH -> model.head.xRot * 0.6f;
				default -> 0f;
			};
			r.xRot = Mth.lerp(w, r.xRot, p[1] + (p[1] < -1.0f ? aim : 0f));
			r.yRot = Mth.lerp(w, r.yRot, p[2]);
			r.zRot = Mth.lerp(w, r.zRot, p[3]);
			l.xRot = Mth.lerp(w, l.xRot, p[4] + (p[4] < -1.0f ? aim : 0f));
			l.yRot = Mth.lerp(w, l.yRot, p[5]);
			l.zRot = Mth.lerp(w, l.zRot, p[6]);
			HipBend.pitch(model, w, p[7]);
			model.body.yRot = Mth.lerp(w, model.body.yRot, p[8]);
			model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, p[QuirkKeyframes.R_LEG_X]);
			model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, p[QuirkKeyframes.L_LEG_X]);
			QuirkKeyframes.legTwist(model, p, w);
			model.head.xRot = Mth.lerp(w, model.head.xRot, model.head.xRot + p[QuirkKeyframes.HEAD_X]);
		}
		model.hat.copyFrom(model.head);
	}

	/** Spent: bent over, hands on the knees, breathing hard. */
	private static void overheat(Player player, HumanoidModel<?> model, float w, float age) {
		float breath = Mth.sin(age * 0.5f) * 0.04f;
		model.body.xRot = Mth.lerp(w, model.body.xRot, 0.55f + breath);
		pose(model.rightArm, w, -0.85f, 0f, 0.1f);
		pose(model.leftArm, w, -0.85f, 0f, -0.1f);
		model.head.xRot = Mth.lerp(w, model.head.xRot, -0.2f);
		if (!player.isCrouching()) {
			// the vanilla crouch's offsets, so the bent body stays joined up
			model.rightLeg.z += 4.0f * w;
			model.leftLeg.z += 4.0f * w;
			model.head.y += 4.2f * w;
			model.body.y += 3.2f * w;
			model.rightArm.y += 3.2f * w;
			model.leftArm.y += 3.2f * w;
		}
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
