package com.projecthero.mod.client.quirk.zerogravity;

import static com.projecthero.mod.client.quirk.QuirkKeyframes.f;
import static com.projecthero.mod.client.quirk.QuirkKeyframes.rest;

import com.projecthero.mod.client.HipBend;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
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
 * <p><b>Keyframed moves</b> (v0.17.1: the shared 16-column {@link QuirkKeyframes} format, full leg rotation) in radians,
 * smoothstep-interpolated, faded in over 2 ticks and out over
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
			rest(0),
			f(1, -0.6f, 0.3f, 0, -0.3f, 0, -0.3f, 0.05f, 0.25f, 0.1f, 0, 0, -0.1f, 0, 0, 0),
			f(3, -1.6f, -0.1f, 0, -0.35f, 0, -0.35f, 0.12f, -0.25f, 0.3f, 0, 0, -0.2f, 0, 0, 0),
			f(6, -1.6f, -0.1f, 0, -0.35f, 0, -0.35f, 0.12f, -0.25f, 0.3f, 0, 0, -0.2f, 0, 0, 0),
			rest(10) };
	private static final float[][] RELEASE = {
			rest(0),
			f(3, -0.8f, -0.2f, 0.2f, -0.8f, 0.2f, -0.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			f(6, -1.25f, -0.62f, 0, -1.25f, 0.62f, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0.15f),
			f(13, -1.25f, -0.62f, 0, -1.25f, 0.62f, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0.15f),
			rest(18) };
	private static final float[][] RAIN = {
			rest(0),
			f(4, -2.6f, 0, 0.3f, -2.6f, 0, -0.3f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.4f),
			f(7, -2.75f, -0.4f, 0, -2.75f, 0.4f, 0, -0.12f, 0, 0, 0, 0, 0, 0, 0, -0.45f),
			f(11, -1.0f, 0, 0.6f, -1.0f, 0, -0.6f, 0.25f, 0, 0.2f, 0, 0, -0.2f, 0, 0, 0.1f),
			f(15, -1.0f, 0, 0.6f, -1.0f, 0, -0.6f, 0.25f, 0, 0.2f, 0, 0, -0.2f, 0, 0, 0.1f),
			rest(20) };
	private static final float[][] SELF = {
			rest(0),
			f(4, -1.0f, -0.7f, 0, -1.0f, 0.7f, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0.2f),
			f(8, -0.4f, 0, 0.8f, -0.4f, 0, -0.8f, 0, 0, -0.3f, 0, 0, 0.15f, 0, 0, -0.1f),
			f(12, -0.35f, 0, 0.75f, -0.35f, 0, -0.75f, 0, 0, -0.3f, 0, 0, 0.15f, 0, 0, 0) };
	private static final float[][] LEAP = {
			rest(0),
			f(3, 0.6f, 0, 0.2f, 0.6f, 0, -0.2f, 0.35f, 0, -0.6f, 0, 0, -0.6f, 0, 0, 0.2f),
			f(6, -2.6f, 0, 0.3f, -2.6f, 0, -0.3f, -0.1f, 0, 0.2f, 0, 0, -0.2f, 0, 0, -0.3f),
			f(14, -2.4f, 0, 0.35f, -2.4f, 0, -0.35f, -0.05f, 0, -0.6f, 0, 0, 0.3f, 0, 0, -0.2f) };
	private static final float[][] SHOWER = {
			rest(0),
			f(6, -0.9f, 0, 0.15f, -0.9f, 0, -0.15f, 0.45f, 0, -0.3f, 0, 0, 0.3f, 0, 0, 0.3f),
			f(14, -2.0f, 0, 0.55f, -2.0f, 0, -0.55f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.35f),
			f(24, -2.3f, 0, 0.5f, -2.3f, 0, -0.5f, -0.12f, 0, 0, 0, 0, 0, 0, 0, -0.45f),
			rest(30) };
	private static final float[][] SHIELD = {
			rest(0),
			f(4, -1.3f, 0.4f, 0.6f, -1.3f, -0.4f, -0.6f, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(8, -0.9f, -0.3f, 0.95f, -0.9f, 0.3f, -0.95f, 0.05f, 0, 0, 0, 0, 0, 0, 0, -0.1f),
			f(12, -1.5f, 0, 0.5f, -1.5f, 0, -0.5f, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0),
			rest(18) };
	private static final float[][] COMET = {
			rest(0),
			f(4, -2.6f, 0.2f, 0.4f, -0.9f, 0, -0.3f, -0.12f, 0.45f, -0.2f, 0, 0, 0.3f, 0, 0, -0.1f),
			f(7, -1.2f, -0.3f, 0, 0.3f, 0, -0.3f, 0.25f, -0.45f, 0.5f, 0, 0, -0.4f, 0, 0, 0.05f),
			f(10, -1.2f, -0.3f, 0, 0.3f, 0, -0.3f, 0.25f, -0.45f, 0.5f, 0, 0, -0.4f, 0, 0, 0.05f),
			rest(14) };
	private static final float[][] SLAM = {
			rest(0),
			f(3, -1.55f, -0.1f, 0, -0.4f, 0, -0.3f, 0.1f, -0.2f, 0.2f, 0, 0, -0.2f, 0, 0, 0),
			f(7, -2.9f, 0, 0.12f, -2.9f, 0, -0.12f, -0.12f, 0, 0, 0, 0, 0, 0, 0, -0.5f),
			f(12, -0.25f, 0, 0.2f, -0.25f, 0, -0.2f, 0.42f, 0, 0.3f, 0, 0, -0.3f, 0, 0, 0.2f),
			f(15, -0.25f, 0, 0.2f, -0.25f, 0, -0.2f, 0.42f, 0, 0.3f, 0, 0, -0.3f, 0, 0, 0.2f),
			rest(18) };
	// v0.17.1: Gunhead Martial Arts as a proper three-hit set -- the boxer's guard, a right palm strike (hit at 0), the left
	// fingertips' touch (6), the grab and the arm-lock throw over her shoulder (12); the legs step through it
	private static final float[][] GUNHEAD = {
			rest(0),
			f(1, -1.45f, -0.55f, 0, -1.25f, 0.6f, 0, 0.12f, -0.25f, -0.3f, 0, 0.05f, 0.35f, 0, -0.05f, 0.05f),
			f(3, -1.62f, -0.08f, 0, -1.2f, 0.65f, 0, 0.22f, 0.4f, -0.55f, 0.2f, 0.05f, 0.4f, 0, -0.05f, 0.05f),
			f(5, -1.35f, -0.5f, 0, -1.25f, 0.6f, 0, 0.12f, 0.1f, -0.3f, 0, 0.05f, 0.35f, 0, -0.05f, 0.05f),
			f(8, -1.2f, -0.6f, 0, -1.68f, 0.12f, 0, 0.22f, -0.45f, 0.35f, 0, 0.05f, -0.5f, -0.2f, -0.05f, 0.05f),
			f(11, -1.55f, -0.3f, 0, -1.5f, 0.35f, 0, 0.18f, -0.1f, -0.2f, 0, 0.05f, 0.2f, 0, -0.05f, 0.05f),
			f(13, -2.5f, 0.25f, 0.2f, -2.2f, 0.5f, 0, -0.1f, 0.6f, -0.35f, 0, 0.1f, 0.35f, 0, -0.1f, -0.15f),
			f(16, -0.5f, 0.35f, 0, -0.35f, 0.6f, 0, 0.55f, 1.05f, 0.5f, 0, 0.15f, -0.55f, 0, -0.1f, 0.25f),
			f(19, -0.5f, 0.35f, 0, -0.35f, 0.6f, 0, 0.5f, 1.0f, 0.45f, 0, 0.15f, -0.5f, 0, -0.1f, 0.2f),
			rest(24) };
	// v0.17.1 Shift+R Debris Barrage: arms flung wide, then both palms driven forward along the aim
	private static final float[][] BARRAGE = {
			rest(0),
			f(3, -2.0f, 0.3f, 1.05f, -2.0f, -0.3f, -1.05f, -0.12f, 0, -0.2f, 0, 0.1f, 0.25f, 0, -0.1f, -0.2f),
			f(6, -1.62f, -0.28f, 0, -1.62f, 0.28f, 0, 0.22f, 0, -0.55f, 0, 0.05f, 0.45f, 0, -0.05f, 0.05f),
			f(11, -1.6f, -0.26f, 0, -1.6f, 0.26f, 0, 0.2f, 0, -0.5f, 0, 0.05f, 0.4f, 0, -0.05f, 0.05f),
			rest(15) };
	// v0.17.1 Shift+X Weightless Push: the arms swept back like a swimmer's kick-off, legs trailing (the body dives: bodyTurn)
	private static final float[][] PUSH = {
			rest(0),
			f(2, 0.75f, 0, 0.45f, 0.75f, 0, -0.45f, 0, 0, 0.45f, 0, 0.12f, 0.6f, 0, -0.12f, -0.6f),
			f(9, 0.6f, 0, 0.5f, 0.6f, 0, -0.5f, 0, 0, 0.35f, 0, 0.15f, 0.5f, 0, -0.15f, -0.6f),
			rest(13) };
	// v0.17.1 Shift+C Gravity Bomb: a deep squat, both palms to the ground, then the arms heave the slab up overhead
	private static final float[][] BOMB = {
			rest(0),
			fl(5, -0.75f, -0.2f, 0.1f, -0.75f, 0.2f, -0.1f, 0.6f, 0, -1.1f, 0, 0.15f, -1.1f, 0, -0.15f, 0.3f),
			f(12, -2.9f, 0, 0.3f, -2.9f, 0, -0.3f, -0.15f, 0, 0, 0, 0.12f, 0, 0, -0.12f, -0.45f),
			f(26, -2.85f, 0, 0.32f, -2.85f, 0, -0.32f, -0.15f, 0, 0, 0, 0.12f, 0, 0, -0.12f, -0.45f),
			rest(30) };
	// v0.17.1 the slab's drop: arms brought down hard and forward, fingertips together
	private static final float[][] BOMB_DROP = {
			f(0, -2.85f, 0, 0.32f, -2.85f, 0, -0.32f, -0.15f, 0, 0, 0, 0.12f, 0, 0, -0.12f, -0.45f),
			f(4, -1.1f, -0.55f, 0, -1.1f, 0.55f, 0, 0.4f, 0, -0.45f, 0, 0.1f, 0.4f, 0, -0.1f, 0.15f),
			f(12, -1.15f, -0.55f, 0, -1.15f, 0.55f, 0, 0.35f, 0, -0.4f, 0, 0.1f, 0.35f, 0, -0.1f, 0.1f),
			rest(16) };
	// v0.17.1 Full Release: fingertips spread wide, arms up and out, head back as everything rises (held through the float)
	private static final float[][] FULL_RELEASE = {
			rest(0),
			f(5, -1.7f, 0, 1.25f, -1.7f, 0, -1.25f, -0.1f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.3f),
			f(20, -2.3f, 0, 1.05f, -2.3f, 0, -1.05f, -0.18f, 0, 0, 0, 0.22f, 0, 0, -0.22f, -0.45f),
			f(80, -2.35f, 0, 1.0f, -2.35f, 0, -1.0f, -0.2f, 0, 0, 0, 0.22f, 0, 0, -0.22f, -0.5f),
			rest(84) };
	// v0.17.1 "Release!": the hands swing in and the fingertips of both meet before her chest (the clap)
	private static final float[][] CLAP = {
			f(0, -2.35f, 0, 1.0f, -2.35f, 0, -1.0f, -0.2f, 0, 0, 0, 0.22f, 0, 0, -0.22f, -0.5f),
			f(3, -1.35f, -0.66f, 0, -1.35f, 0.66f, 0, 0.12f, 0, -0.1f, 0, 0.15f, 0.1f, 0, -0.15f, 0.1f),
			f(18, -1.3f, -0.64f, 0, -1.3f, 0.64f, 0, 0.1f, 0, -0.1f, 0, 0.15f, 0.1f, 0, -0.15f, 0.1f),
			rest(24) };
	private static final float[][] FIELD = {
			rest(0),
			f(4, -1.5f, -0.1f, 0, -1.5f, 0.1f, 0, 0.12f, 0, 0.3f, 0, 0, -0.3f, 0, 0, 0),
			f(8, -1.4f, -0.9f, 0.15f, -1.4f, 0.9f, -0.15f, 0.05f, 0, 0.3f, 0, 0, -0.3f, 0, 0, -0.1f),
			f(15, -1.4f, -0.9f, 0.15f, -1.4f, 0.9f, -0.15f, 0.05f, 0, 0.3f, 0, 0, -0.3f, 0, 0, -0.1f),
			rest(20) };
	private static final float[][] CATCH = {
			rest(0),
			f(4, -2.2f, -0.3f, 0.3f, -2.2f, 0.3f, -0.3f, -0.1f, 0, 0.2f, 0, 0, -0.2f, 0, 0, -0.4f),
			f(12, -2.1f, -0.3f, 0.35f, -2.1f, 0.3f, -0.35f, -0.1f, 0, 0.2f, 0, 0, -0.2f, 0, 0, -0.35f),
			rest(18) };
	private static final float[][] SICK = {
			rest(0),
			f(6, -1.95f, 0.95f, 0, -0.8f, 0.6f, 0, 0.5f, 0, 0, 0, 0, 0, 0, 0, 0.35f),
			f(44, -1.95f, 0.95f, 0, -0.8f, 0.6f, 0, 0.55f, 0, 0, 0, 0, 0, 0, 0, 0.4f),
			rest(50) };

	private ZeroGravityPose() {
	}

	/** A frame with the crouch folded into the legs / body (the 16-column table has no crouch column). */
	private static float[] fl(float tick, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float rly,
			float rlz, float llx, float lly, float llz, float hx) {
		return f(tick, rax, ray, raz, lax, lay, laz, bx, by, rlx, rly, rlz, llx, lly, llz, hx);
	}

	/**
	 * v0.17.1: degrees to turn her whole body {@code [yaw, pitch, roll]} ({@code ZeroGravityBodyTurnMixin}), or null: the
	 * Weightless Push dives her flat along the dash; floating (Float Self) she drifts in a slow weightless tilt.
	 */
	public static float[] bodyTurn(Player p, float partial) {
		if (!ZeroGravity.is(p) || p.level() == null) {
			return null;
		}
		QuirkState s = Quirks.peek(p);
		if (s == null) {
			return null;
		}
		long now = p.level().getGameTime();
		if (s.animId == ZeroGravity.ANIM_PUSH) {
			float age = now - s.animStart + partial;
			if (age >= 0f && age < 13f) {
				float in = Mth.clamp(age / 2f, 0f, 1f), out = Mth.clamp((13f - age) / 4f, 0f, 1f);
				float k = Math.min(in, out);
				k = k * k * (3f - 2f * k);
				return new float[] { 0f, 72f * k, 0f };
			}
		}
		if (ZeroGravity.selfFloating(p) && !p.onGround()) {
			float t = p.tickCount + partial;
			return new float[] { 0f, 10f * Mth.sin(t * 0.045f) + 6f, 0f };
		}
		return null;
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
			case ZeroGravity.ANIM_BARRAGE -> BARRAGE;
			case ZeroGravity.ANIM_PUSH -> PUSH;
			case ZeroGravity.ANIM_BOMB -> BOMB;
			case ZeroGravity.ANIM_BOMB_DROP -> BOMB_DROP;
			case ZeroGravity.ANIM_FULL_RELEASE -> FULL_RELEASE;
			case ZeroGravity.ANIM_CLAP -> CLAP;
			default -> null;
		};
	}

	public static int length(int animId) {
		float[][] f = frames(animId);
		return f == null ? 0 : (int) f[f.length - 1][0];
	}

	static float[] sample(float[][] f, float tick) {
		return QuirkKeyframes.sample(f, tick);
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
		// v0.17.1 Catch (hold V): both arms up, reaching, as long as it is held
		if (animId == ZeroGravity.ANIM_CATCH && animAge >= 12f && s != null) {
			float sway = Mth.sin(ageTicks * 0.12f) * 0.08f;
			pose(r, 1f, -2.15f + sway, -0.3f, 0.35f);
			pose(l, 1f, -2.15f - sway, 0.3f, -0.35f);
			model.head.xRot = Mth.lerp(0.7f, model.head.xRot, -0.35f);
			model.hat.copyFrom(model.head);
			return;
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
		if (animId == ZeroGravity.ANIM_BOMB_DROP || animId == ZeroGravity.ANIM_CLAP) {
			w = Math.max(0f, Math.min(1f, (len - animAge) / 4f)); // they start from the pose before (no fade in)
		}
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
			if (animId == ZeroGravity.ANIM_FULL_RELEASE) {
				// fingertips trembling with the strain
				float tr = Mth.sin(ageTicks * 2.3f) * 0.03f * Mth.clamp(animAge / 20f, 0f, 1f);
				p[1] += tr;
				p[4] -= tr;
			}
			if (animId == ZeroGravity.ANIM_BOMB && animAge > 3f && animAge < 9f) {
				com.projecthero.mod.client.quirk.QuirkKeyframes.crouch(model, w * Mth.clamp(1f - Math.abs(animAge - 5.5f) / 3.5f, 0f, 1f));
			}
			r.xRot = Mth.lerp(w, r.xRot, p[1] + (p[1] < -1.0f && p[1] > -2.0f ? aim : 0f));
			r.yRot = Mth.lerp(w, r.yRot, p[2]);
			r.zRot = Mth.lerp(w, r.zRot, p[3]);
			l.xRot = Mth.lerp(w, l.xRot, p[4] + (p[4] < -1.0f && p[4] > -2.0f ? aim : 0f));
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

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
