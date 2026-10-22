package com.projecthero.mod.client.quirk.zerogravity;

import com.projecthero.mod.client.HipBend;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.zerogravity.ZeroGravity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 revamp: Uravity's poses on the vanilla humanoid model, set at the end of {@code setupAnim}.
 *
 * <p><b>Keyframed moves</b> (the One For All format): frames {@code {tick, rightArmX, rightArmY, rightArmZ, leftArmX, leftArmY,
 * leftArmZ, bodyX, bodyY, rightLegX, leftLegX, headX}} in radians, smoothstep-interpolated, faded in over 2 ticks and out over
 * the last 3, driven by the synced {@code animId / animStart} (140..159). Negative arm X swings a limb forward / up; a positive
 * right-arm Z lifts it out to the side; a positive right-arm Y swings it in across the chest. The torso bends from the hips.
 * <ul>
 *   <li><b>Touch</b>: the right palm thrust out, fingers spread; <b>Release</b>: both hands before the chest, the five fingertips
 *       of each pressed together (her signature); <b>Release (rain)</b>: hands thrown up, fingertips together high, then swept
 *       down as the cloud falls;</li>
 *   <li><b>Meteor Shower</b>: palms to the ground, then the arms rising with the rubble; <b>Debris Shield</b>: the arms circle
 *       out; <b>Comet Toss</b>: wound up overhead, then a full-body throw; <b>Slam</b>: the touch, both arms heave it up, then
 *       haul it down;</li>
 *   <li><b>Gunhead</b>: guard, left jab, right palm, the grab and an over-the-shoulder throw; <b>Field</b>: both palms pushed
 *       out, then spread wide; <b>Catch</b>: both arms reaching up; <b>Float Self</b>: a tap of the fingertips to her chest,
 *       arms drifting out; <b>Leap</b>: crouch and spring; <b>sick</b>: bent over, a hand over her mouth.</li>
 * </ul>
 * <b>Held</b>: weightless while she floats (limbs drifting, a slow bob), arms up in the leap, kneeling and breathing while she
 * settles, and a hand held ready while she has things afloat.
 */
public final class ZeroGravityPose {
	private static final float[][] TOUCH = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 1, -0.6f, 0.3f, 0, -0.3f, 0, -0.3f, 0.05f, 0.25f, 0.1f, -0.1f, 0 },
			{ 3, -1.6f, -0.1f, 0, -0.35f, 0, -0.35f, 0.12f, -0.25f, 0.3f, -0.2f, 0 },
			{ 6, -1.6f, -0.1f, 0, -0.35f, 0, -0.35f, 0.12f, -0.25f, 0.3f, -0.2f, 0 },
			{ 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] RELEASE = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 3, -0.8f, -0.2f, 0.2f, -0.8f, 0.2f, -0.2f, 0, 0, 0, 0, 0 },
			{ 6, -1.25f, -0.62f, 0, -1.25f, 0.62f, 0, 0.05f, 0, 0, 0, 0.15f },
			{ 13, -1.25f, -0.62f, 0, -1.25f, 0.62f, 0, 0.05f, 0, 0, 0, 0.15f },
			{ 18, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] RAIN = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 4, -2.6f, 0, 0.3f, -2.6f, 0, -0.3f, -0.1f, 0, 0, 0, -0.4f },
			{ 7, -2.75f, -0.4f, 0, -2.75f, 0.4f, 0, -0.12f, 0, 0, 0, -0.45f },
			{ 11, -1.0f, 0, 0.6f, -1.0f, 0, -0.6f, 0.25f, 0, 0.2f, -0.2f, 0.1f },
			{ 15, -1.0f, 0, 0.6f, -1.0f, 0, -0.6f, 0.25f, 0, 0.2f, -0.2f, 0.1f },
			{ 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] SELF = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 4, -1.0f, -0.7f, 0, -1.0f, 0.7f, 0, 0.05f, 0, 0, 0, 0.2f },
			{ 8, -0.4f, 0, 0.8f, -0.4f, 0, -0.8f, 0, 0, -0.3f, 0.15f, -0.1f },
			{ 12, -0.35f, 0, 0.75f, -0.35f, 0, -0.75f, 0, 0, -0.3f, 0.15f, 0 } };
	private static final float[][] LEAP = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 3, 0.6f, 0, 0.2f, 0.6f, 0, -0.2f, 0.35f, 0, -0.6f, -0.6f, 0.2f },
			{ 6, -2.6f, 0, 0.3f, -2.6f, 0, -0.3f, -0.1f, 0, 0.2f, -0.2f, -0.3f },
			{ 14, -2.4f, 0, 0.35f, -2.4f, 0, -0.35f, -0.05f, 0, -0.6f, 0.3f, -0.2f } };
	private static final float[][] SHOWER = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 6, -0.9f, 0, 0.15f, -0.9f, 0, -0.15f, 0.45f, 0, -0.3f, 0.3f, 0.3f },
			{ 14, -2.0f, 0, 0.55f, -2.0f, 0, -0.55f, -0.1f, 0, 0, 0, -0.35f },
			{ 24, -2.3f, 0, 0.5f, -2.3f, 0, -0.5f, -0.12f, 0, 0, 0, -0.45f },
			{ 30, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] SHIELD = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 4, -1.3f, 0.4f, 0.6f, -1.3f, -0.4f, -0.6f, 0.05f, 0, 0, 0, 0 },
			{ 8, -0.9f, -0.3f, 0.95f, -0.9f, 0.3f, -0.95f, 0.05f, 0, 0, 0, -0.1f },
			{ 12, -1.5f, 0, 0.5f, -1.5f, 0, -0.5f, 0.05f, 0, 0, 0, 0 },
			{ 18, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] COMET = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 4, -2.6f, 0.2f, 0.4f, -0.9f, 0, -0.3f, -0.12f, 0.45f, -0.2f, 0.3f, -0.1f },
			{ 7, -1.2f, -0.3f, 0, 0.3f, 0, -0.3f, 0.25f, -0.45f, 0.5f, -0.4f, 0.05f },
			{ 10, -1.2f, -0.3f, 0, 0.3f, 0, -0.3f, 0.25f, -0.45f, 0.5f, -0.4f, 0.05f },
			{ 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] SLAM = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 3, -1.55f, -0.1f, 0, -0.4f, 0, -0.3f, 0.1f, -0.2f, 0.2f, -0.2f, 0 },
			{ 7, -2.9f, 0, 0.12f, -2.9f, 0, -0.12f, -0.12f, 0, 0, 0, -0.5f },
			{ 12, -0.25f, 0, 0.2f, -0.25f, 0, -0.2f, 0.42f, 0, 0.3f, -0.3f, 0.2f },
			{ 15, -0.25f, 0, 0.2f, -0.25f, 0, -0.2f, 0.42f, 0, 0.3f, -0.3f, 0.2f },
			{ 18, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] GUNHEAD = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 2, -1.2f, -0.3f, 0, -1.2f, 0.3f, 0, 0.1f, 0, 0.3f, -0.3f, 0 },
			{ 4, -1.0f, -0.3f, 0, -1.62f, 0.05f, 0, 0.15f, 0.3f, 0.3f, -0.3f, 0 },
			{ 7, -1.2f, -0.3f, 0, -1.2f, 0.3f, 0, 0.1f, 0, 0.3f, -0.3f, 0 },
			{ 9, -1.62f, -0.05f, 0, -1.0f, 0.3f, 0, 0.15f, -0.35f, 0.3f, -0.3f, 0 },
			{ 12, -1.5f, -0.2f, 0, -1.5f, 0.2f, 0, 0.2f, 0, 0.35f, -0.35f, 0.05f },
			{ 15, -2.6f, 0.3f, 0, -2.4f, 0.5f, 0, 0.35f, 0.9f, 0.4f, -0.4f, 0.2f },
			{ 18, -2.6f, 0.3f, 0, -2.4f, 0.5f, 0, 0.35f, 0.9f, 0.4f, -0.4f, 0.2f },
			{ 22, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FIELD = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 4, -1.5f, -0.1f, 0, -1.5f, 0.1f, 0, 0.12f, 0, 0.3f, -0.3f, 0 },
			{ 8, -1.4f, -0.9f, 0.15f, -1.4f, 0.9f, -0.15f, 0.05f, 0, 0.3f, -0.3f, -0.1f },
			{ 15, -1.4f, -0.9f, 0.15f, -1.4f, 0.9f, -0.15f, 0.05f, 0, 0.3f, -0.3f, -0.1f },
			{ 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] CATCH = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 4, -2.2f, -0.3f, 0.3f, -2.2f, 0.3f, -0.3f, -0.1f, 0, 0.2f, -0.2f, -0.4f },
			{ 12, -2.1f, -0.3f, 0.35f, -2.1f, 0.3f, -0.35f, -0.1f, 0, 0.2f, -0.2f, -0.35f },
			{ 18, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] SICK = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 6, -1.95f, 0.95f, 0, -0.8f, 0.6f, 0, 0.5f, 0, 0, 0, 0.35f },
			{ 44, -1.95f, 0.95f, 0, -0.8f, 0.6f, 0, 0.55f, 0, 0, 0, 0.4f },
			{ 50, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };

	private ZeroGravityPose() {
	}

	static float[][] frames(int animId) {
		return switch (animId) {
			case ZeroGravity.ANIM_TOUCH -> TOUCH;
			case ZeroGravity.ANIM_RELEASE -> RELEASE;
			case ZeroGravity.ANIM_RAIN -> RAIN;
			case ZeroGravity.ANIM_SELF -> SELF;
			case ZeroGravity.ANIM_LEAP -> LEAP;
			case ZeroGravity.ANIM_SHOWER -> SHOWER;
			case ZeroGravity.ANIM_SHIELD -> SHIELD;
			case ZeroGravity.ANIM_COMET -> COMET;
			case ZeroGravity.ANIM_SLAM -> SLAM;
			case ZeroGravity.ANIM_GUNHEAD -> GUNHEAD;
			case ZeroGravity.ANIM_FIELD -> FIELD;
			case ZeroGravity.ANIM_CATCH -> CATCH;
			case ZeroGravity.ANIM_SICK -> SICK;
			default -> null;
		};
	}

	public static int length(int animId) {
		float[][] f = frames(animId);
		return f == null ? 0 : (int) f[f.length - 1][0];
	}

	static float[] sample(float[][] f, float tick) {
		if (tick < 0f || tick > f[f.length - 1][0]) {
			return null;
		}
		for (int i = 1; i < f.length; i++) {
			if (tick <= f[i][0]) {
				float[] a = f[i - 1];
				float[] b = f[i];
				float t = (tick - a[0]) / Math.max(1e-4f, b[0] - a[0]);
				t = t * t * (3f - 2f * t);
				float[] out = new float[12];
				for (int k = 1; k < 12; k++) {
					out[k] = Mth.lerp(t, a[k], b[k]);
				}
				return out;
			}
		}
		return null;
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!ZeroGravity.is(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float ageTicks = player.tickCount + partial;
		ModelPart r = model.rightArm, l = model.leftArm;
		boolean airborne = !player.onGround();
		QuirkState s = Quirks.peek(player);
		int animId = s == null ? 0 : s.animId;
		float animAge = 0f;
		boolean keyframed = false;
		if (s != null && animId >= 140 && animId <= 159 && player.level() != null) {
			animAge = player.level().getGameTime() - s.animStart + partial;
			keyframed = animAge >= 0f && animAge < length(animId);
		}

		// ---- held poses
		if (ZeroGravity.selfFloating(player) && airborne) {
			// weightless: the limbs drift, a slow bob
			float b = Mth.sin(ageTicks * 0.09f);
			float c = Mth.cos(ageTicks * 0.07f);
			pose(r, 0.85f, -0.35f + 0.15f * b, 0f, 0.75f + 0.12f * c);
			pose(l, 0.85f, -0.35f - 0.15f * b, 0f, -0.75f - 0.12f * c);
			model.rightLeg.xRot = Mth.lerp(0.8f, model.rightLeg.xRot, -0.35f + 0.12f * c);
			model.leftLeg.xRot = Mth.lerp(0.8f, model.leftLeg.xRot, 0.15f - 0.12f * c);
			model.rightLeg.zRot = Mth.lerp(0.8f, model.rightLeg.zRot, 0.12f);
			model.leftLeg.zRot = Mth.lerp(0.8f, model.leftLeg.zRot, -0.12f);
			HipBend.pitch(model, 0.6f, 0.05f * b);
		} else if (ZeroGravity.leaping(player) && airborne && !keyframed) {
			pose(r, 0.8f, -2.4f, 0f, 0.35f);
			pose(l, 0.8f, -2.4f, 0f, -0.35f);
			model.rightLeg.xRot = Mth.lerp(0.7f, model.rightLeg.xRot, -0.6f);
			model.leftLeg.xRot = Mth.lerp(0.7f, model.leftLeg.xRot, 0.3f);
		} else if (ZeroGravity.settling(player) && !keyframed) {
			// down on one knee, hands on her knees, breathing slowly
			float breath = Mth.sin(ageTicks * 0.15f) * 0.04f;
			HipBend.pitch(model, 1f, 0.3f + breath);
			pose(r, 1f, -0.7f, -0.1f, 0.05f);
			pose(l, 1f, -0.9f, 0.1f, -0.05f);
			model.rightLeg.xRot = 1.25f;
			model.leftLeg.xRot = -1.15f;
			model.rightLeg.y += 2.5f;
			model.leftLeg.y += 1.0f;
			model.leftLeg.z -= 1.5f;
			model.rightLeg.z += 2.0f;
			model.body.y += 3.0f;
			model.head.y += 3.0f;
			r.y += 3.0f;
			l.y += 3.0f;
			model.head.xRot = Mth.lerp(0.6f, model.head.xRot, 0.25f);
		} else if (ZeroGravity.holding(player) && !keyframed && model.attackTime <= 0f) {
			// things afloat: her right hand held ready, fingertips up, to Release
			float k = 0.5f;
			pose(r, k, -0.75f, -0.35f, 0.1f);
		}

		if (!keyframed) {
			model.hat.copyFrom(model.head);
			return;
		}
		int len = length(animId);
		float w = Math.max(0f, Math.min(1f, Math.min(animAge / 2f, (len - animAge) / 3f)));
		if (animId == ZeroGravity.ANIM_SELF || animId == ZeroGravity.ANIM_LEAP) {
			w = Math.max(0f, Math.min(1f, animAge / 2f)); // they hand over to the held pose with no fade out
		}
		float[] p = sample(frames(animId), animAge);
		if (p != null) {
			float aim = switch (animId) {
				case ZeroGravity.ANIM_TOUCH, ZeroGravity.ANIM_COMET, ZeroGravity.ANIM_GUNHEAD, ZeroGravity.ANIM_FIELD -> model.head.xRot * 0.6f;
				default -> 0f;
			};
			if (animId == ZeroGravity.ANIM_SICK) {
				p[7] += Mth.sin(animAge * 0.5f) * 0.04f;
			}
			r.xRot = Mth.lerp(w, r.xRot, p[1] + (p[1] < -1.0f && p[1] > -2.0f ? aim : 0f));
			r.yRot = Mth.lerp(w, r.yRot, p[2]);
			r.zRot = Mth.lerp(w, r.zRot, p[3]);
			l.xRot = Mth.lerp(w, l.xRot, p[4] + (p[4] < -1.0f && p[4] > -2.0f ? aim : 0f));
			l.yRot = Mth.lerp(w, l.yRot, p[5]);
			l.zRot = Mth.lerp(w, l.zRot, p[6]);
			HipBend.pitch(model, w, p[7]);
			model.body.yRot = Mth.lerp(w, model.body.yRot, p[8]);
			model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, p[9]);
			model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, p[10]);
			model.head.xRot = Mth.lerp(w, model.head.xRot, model.head.xRot + p[11]);
		}
		model.hat.copyFrom(model.head);
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
