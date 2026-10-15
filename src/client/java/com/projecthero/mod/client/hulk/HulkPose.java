package com.projecthero.mod.client.hulk;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.render.JointedLimbs;
import com.projecthero.mod.hulk.GammaOverload;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.13.15: Banner's side of the change (the Hulk model plays the matching {@code animation.hulk.transform*} clip). v0.16.4:
 * no cross-fade any more -- Banner is drawn alone, greening and swelling, until {@link HulkMorph} swaps him for the Hulk.
 * Applied to the vanilla humanoid model after its own animation, from {@code HumanoidModelMixin}.
 *
 * <p>The vanilla body pivots at the neck; to lean from the hips like the Hulk rig does, the neck is moved forward and
 * down around a fixed hip point, and the head and shoulders follow it. Model space: +Y is down, -Z is forward, leg X
 * positive swings the foot back, arm X negative raises the arm forward.
 *
 * <p>v0.16.4: the same pose for the Gamma origin -- soaking up a reactor's flash ({@link #applyAbsorb}) and the body left
 * behind while he is in his mind ({@link #applyHeadache}).
 *
 * <p>v0.16.4 (user): "break the player's body parts in half at the joints" -- six changes now ({@code
 * HulkState.Combat.transformStyle}), each a keyframed Banner sequence ({@link #STYLES}) with real elbows and knees: the
 * lower arm / leg is bent through {@link JointedLimbs} (the Spider-Man technique -- every limb box of the player's model,
 * skin and overlay, drawn in two halves mitred at the joint). Every pose STANDS (user rule: no kneeling, no legs folded
 * back). And the Hulk's death: while the Hulk lies on his back ({@code animation.hulk.death_fall}) and fades off him, Banner
 * lies there too ({@link #applyDeathFall}).
 */
public final class HulkPose {
	/**
	 * Pose numbers: forward lean (rad), head bow (rad). v0.16.4 (user): "the player should be standing up not having there legs
	 * lay backwards" -- every one of these poses (the unwilling change, the Gamma absorb, the mind body) now STANDS: no hip
	 * drop, legs straight in a braced, slightly wide stance; the hunch from the hips, the hands and the shiver stay.
	 */
	private static final float LEAN = 0.38f;
	/** Feet a little apart (rad of leg roll each side) -- braced against it. */
	private static final float STANCE = 0.12f;
	private static final float HEAD_BOW = 0.6f;
	/** Arms raised to clutch the sides of the head (in the body's frame) and angled in. */
	private static final float ARM_UP = -2.55f;
	private static final float ARM_IN = 0.45f;
	/** Ticks to ease into the pose. */
	private static final float DROP_TICKS = 6.0f;
	/** v0.16.4: how long after {@code ANIM_DEATH_FALL} starts Banner may still be lying there (the slow change back + 2 s). */
	public static final long DEATH_FALL_HOLD_TICKS = 30L * 20L;
	/** v0.16.4: ticks the Hulk takes to hit the ground in {@code death_fall} (1.0 s) -- Banner's lie eases in over the same. */
	private static final float DEATH_FALL_TICKS = 20.0f;

	// ------------------------------------------------------------------ v0.16.4: the six Banner sequences
	// One frame: {tick, lean, headX, rArmX, rArmIn, rArmOut, lArmX, lArmIn, lArmOut, rElbow, lElbow, rLegX, lLegX, stance,
	// rKnee, lKnee, shake}. Arm X is in the body's frame (the lean is added); arm "in" turns the arm across the chest, "out"
	// lifts it to the side (both mirrored per side); elbow / knee are flexion (rad); leg X negative = forward.
	private static final int TICK = 0, LEAN_ = 1, HEAD = 2, RAX = 3, RAIN = 4, RAOUT = 5, LAX = 6, LAIN = 7, LAOUT = 8, REL = 9, LEL = 10,
			RLX = 11, LLX = 12, STANCE_ = 13, RKN = 14, LKN = 15, SHAKE = 16, N = 17;

	private static float[] f(float... v) {
		return v;
	}

	private static final float[] NEUTRAL = f(0, 0, 0, 0, 0, 0.05f, 0, 0, 0.05f, 0.15f, 0.15f, 0, 0, 0.04f, 0.04f, 0.04f, 0);

	private static float[] at(float tick, float[] base, float... overrides) {
		float[] r = base.clone();
		r[TICK] = tick;
		for (int i = 0; i + 1 < overrides.length; i += 2) {
			r[(int) overrides[i]] = overrides[i + 1];
		}
		return r;
	}

	/** Quick (H, 30 ticks): a braced hunch, fists clench and the arms flex out, then up into the roar as he fades. */
	private static final float[][] QUICK;
	/** Forced (104 ticks): hunched, both hands clutching the back of his head, elbows forward, shaking harder and harder. */
	private static final float[][] FORCED;
	/** Rip: the chest heaves, fists pulled together in a crab flex, then everything bursts outward. */
	private static final float[][] RIP;
	/** Pound: bent over at the hips (standing, knees soft), both fists hammer the ground -- on the Hulk clip's beat. */
	private static final float[][] POUND;
	/** Arch: doubled over clutching his gut, then the back arches and he screams at the sky. */
	private static final float[][] ARCH;
	/** Stagger: dazed, a hand to his head, stumbles forward right then left and catches himself, standing wide. */
	private static final float[][] STAGGER;

	static {
		float[] hunch = at(0, NEUTRAL, LEAN_, 0.5f, HEAD, 0.4f, RAX, -0.45f, RAIN, 0.3f, RAOUT, 0.15f, LAX, -0.45f, LAIN, 0.3f, LAOUT, 0.15f,
				REL, 1.4f, LEL, 1.4f, RLX, -0.12f, LLX, -0.12f, STANCE_, 0.12f, RKN, 0.25f, LKN, 0.25f, SHAKE, 0.03f);
		float[] flex = at(0, hunch, LEAN_, 0.35f, HEAD, 0.15f, RAX, -0.45f, RAIN, 0.0f, RAOUT, 0.8f, LAX, -0.45f, LAIN, 0.0f, LAOUT, 0.8f,
				REL, 1.65f, LEL, 1.65f, SHAKE, 0.05f);
		float[] roar = at(0, flex, LEAN_, -0.2f, HEAD, -0.6f, RAX, -0.6f, RAOUT, 1.25f, LAX, -0.6f, LAOUT, 1.25f, REL, 0.7f, LEL, 0.7f,
				RLX, 0, LLX, 0, STANCE_, 0.16f, RKN, 0.1f, LKN, 0.1f, SHAKE, 0.02f);
		QUICK = new float[][] { at(0, NEUTRAL), at(5, hunch), at(14, flex), at(20, roar), at(30, roar) };

		// the existing forced look (hunch, head bowed, hands on his head), now with the elbows folded so the hands reach it
		float[] clutch = at(0, NEUTRAL, LEAN_, LEAN, HEAD, HEAD_BOW, RAX, -2.05f, RAOUT, 0.55f, LAX, -2.05f, LAOUT, 0.55f, REL, 1.75f, LEL, 1.75f,
				STANCE_, STANCE, RKN, 0.18f, LKN, 0.18f, RLX, -0.08f, LLX, -0.08f, SHAKE, 0.03f);
		FORCED = new float[][] { at(0, NEUTRAL), at(DROP_TICKS, clutch), at(20, clutch), at(80, clutch, LEAN_, 0.46f, SHAKE, 0.09f),
				at(104, clutch, LEAN_, 0.46f, SHAKE, 0.09f) };

		float[] ripHunch = at(0, NEUTRAL, LEAN_, 0.3f, HEAD, 0.3f, RAX, -0.5f, RAIN, 0.35f, RAOUT, 0.3f, LAX, -0.5f, LAIN, 0.35f, LAOUT, 0.3f,
				REL, 1.4f, LEL, 1.4f, STANCE_, 0.1f, RKN, 0.15f, LKN, 0.15f, RLX, -0.07f, LLX, -0.07f, SHAKE, 0.02f);
		float[] crab = at(0, ripHunch, LEAN_, 0.42f, HEAD, 0.1f, RAX, -0.65f, RAIN, 0.6f, RAOUT, 0.5f, LAX, -0.65f, LAIN, 0.6f, LAOUT, 0.5f,
				REL, 1.85f, LEL, 1.85f, RKN, 0.22f, LKN, 0.22f, RLX, -0.1f, LLX, -0.1f, SHAKE, 0.05f);
		float[] burst = at(0, crab, LEAN_, -0.25f, HEAD, -0.5f, RAX, 0.15f, RAIN, 0, RAOUT, 1.35f, LAX, 0.15f, LAIN, 0, LAOUT, 1.35f,
				REL, 0.5f, LEL, 0.5f, STANCE_, 0.16f, RKN, 0.1f, LKN, 0.1f, RLX, 0, LLX, 0, SHAKE, 0.07f);
		RIP = new float[][] { at(0, NEUTRAL), at(8, ripHunch), at(20, crab), at(60, crab, SHAKE, 0.08f), at(67, crab, LEAN_, 0.5f, REL, 2.0f, LEL, 2.0f),
				at(74, burst), at(104, burst, SHAKE, 0.04f) };

		// bent over from the hips, braced; the fists go up (overhead in the bent body's frame) and down to the ground,
		// landing on the Hulk clip's pounds (1.05, 1.75, 2.45, 3.15, 3.75 s)
		float[] bent = at(0, NEUTRAL, LEAN_, 0.95f, HEAD, -0.55f, RAX, -0.6f, RAOUT, 0.25f, LAX, -0.6f, LAOUT, 0.25f, REL, 1.0f, LEL, 1.0f,
				STANCE_, 0.15f, RKN, 0.3f, LKN, 0.3f, RLX, -0.15f, LLX, -0.15f, SHAKE, 0.02f);
		float[] up = at(0, bent, RAX, -2.9f, RAOUT, 0.15f, LAX, -2.9f, LAOUT, 0.15f, REL, 0.6f, LEL, 0.6f, LEAN_, 0.85f);
		float[] down = at(0, bent, RAX, -0.15f, RAOUT, 0.12f, LAX, -0.15f, LAOUT, 0.12f, REL, 0.15f, LEL, 0.15f, LEAN_, 1.05f, SHAKE, 0.05f);
		int[] hits = { 21, 35, 49, 63, 75 };
		float[][] pound = new float[3 + hits.length * 3][];
		int i = 0;
		pound[i++] = at(0, NEUTRAL);
		pound[i++] = at(10, bent);
		for (int h : hits) {
			pound[i++] = at(h - 6, up);
			pound[i++] = at(h, down);
			pound[i++] = at(h + 2, down, LEAN_, 1.0f);
		}
		pound[i] = at(104, down);
		POUND = pound;

		float[] gut = at(0, NEUTRAL, LEAN_, 0.45f, HEAD, 0.5f, RAX, -0.9f, RAIN, 0.7f, RAOUT, 0.2f, LAX, -0.9f, LAIN, 0.7f, LAOUT, 0.2f,
				REL, 1.6f, LEL, 1.6f, STANCE_, 0.1f, RKN, 0.2f, LKN, 0.2f, RLX, -0.1f, LLX, -0.1f, SHAKE, 0.04f);
		float[] arch = at(0, gut, LEAN_, -0.45f, HEAD, -1.1f, RAX, 0.45f, RAIN, 0, RAOUT, 0.65f, LAX, 0.45f, LAIN, 0, LAOUT, 0.65f,
				REL, 0.5f, LEL, 0.5f, STANCE_, 0.15f, RKN, 0.25f, LKN, 0.25f, RLX, 0.05f, LLX, 0.05f, SHAKE, 0.05f);
		ARCH = new float[][] { at(0, NEUTRAL), at(9, gut), at(28, gut, SHAKE, 0.07f), at(40, gut, LEAN_, 0.1f, HEAD, -0.2f),
				at(48, arch), at(80, arch, SHAKE, 0.1f), at(104, arch, SHAKE, 0.1f) };

		float[] daze = at(0, NEUTRAL, LEAN_, 0.2f, HEAD, 0.2f, RAX, -2.4f, RAOUT, 0.6f, REL, 1.7f, LAX, -0.2f, LAOUT, 0.25f, LEL, 0.4f,
				SHAKE, 0.03f);
		float[] stepR = at(0, NEUTRAL, LEAN_, 0.55f, HEAD, -0.1f, RAX, -1.0f, RAOUT, 0.5f, LAX, -1.1f, LAOUT, 0.5f, REL, 0.6f, LEL, 0.55f,
				RLX, -0.55f, RKN, 0.6f, LLX, 0.3f, LKN, 0.1f, STANCE_, 0.08f, SHAKE, 0.02f);
		float[] stepL = at(0, stepR, RLX, 0.3f, RKN, 0.1f, LLX, -0.58f, LKN, 0.62f, LEAN_, 0.6f);
		float[] caught = at(0, NEUTRAL, LEAN_, 0.35f, HEAD, -0.2f, RAX, -0.4f, RAOUT, 0.8f, LAX, -0.4f, LAOUT, 0.8f, REL, 1.6f, LEL, 1.6f,
				STANCE_, 0.22f, RKN, 0.3f, LKN, 0.3f, RLX, -0.15f, LLX, -0.15f, SHAKE, 0.04f);
		STAGGER = new float[][] { at(0, NEUTRAL), at(7, daze), at(16, daze, HEAD, 0.3f, LEAN_, 0.25f), at(25, stepR), at(31, stepR, LEAN_, 0.48f),
				at(37, NEUTRAL, LEAN_, 0.4f, RAX, -0.8f, LAX, -0.9f, RAOUT, 0.45f, LAOUT, 0.45f, REL, 0.7f, LEL, 0.7f), at(45, stepL),
				at(51, stepL, LEAN_, 0.5f), at(59, stepR, LEAN_, 0.6f, RLX, -0.45f), at(70, caught), at(104, caught, SHAKE, 0.06f) };
	}

	/** v0.16.4: the six Banner sequences, indexed by {@code HulkState.TRANSFORM_*}. */
	private static final float[][][] STYLES = { QUICK, FORCED, RIP, POUND, ARCH, STAGGER };

	private HulkPose() {
	}

	/**
	 * v0.16.4: which of the six changes this state plays ({@code HulkState.TRANSFORM_*}). A pre-0.16.4 unwilling change
	 * (style never set) is the forced one.
	 */
	public static int style(HulkState s) {
		int st = s.combat.transformStyle;
		if (st < HulkState.TRANSFORM_QUICK || st > HulkState.TRANSFORM_STAGGER) {
			st = HulkState.TRANSFORM_QUICK;
		}
		if (st == HulkState.TRANSFORM_QUICK && s.combat.unwilling) {
			st = HulkState.TRANSFORM_FORCED;
		}
		return st;
	}

	/** v0.16.4: is a change into the Hulk under way right now (any of the six; {@link Hulk#changeTicks} is its length)? */
	public static boolean changingIn(HulkState s, long now) {
		return s.hasPower && s.hulk && now - s.formChangedAt >= 0L && now - s.formChangedAt < Hulk.changeTicks(s);
	}

	/** v0.16.4: the Hulk's death -- lying on his back ({@code ANIM_DEATH_FALL}) through the slow change back. */
	public static boolean deathFall(HulkState s, long now) {
		return s.hasPower && s.animId == HulkState.ANIM_DEATH_FALL && now - s.animStart >= 0L && now - s.animStart < DEATH_FALL_HOLD_TICKS;
	}

	/**
	 * v0.16.4: {@link #deathFall(HulkState, long)} for this very body -- a respawned / relogged player is a new entity
	 * (its tick count starts again), so a fall begun before it existed is not his.
	 */
	public static boolean deathFall(Player player, HulkState s) {
		long since = player.level().getGameTime() - s.animStart;
		return deathFall(s, player.level().getGameTime()) && player.tickCount + 2 >= since;
	}

	/** 0..1: how far into the change pose Banner is right now (0 unless a change into the Hulk is under way). */
	public static float kneel(Player player, float partialTick) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s == null || !changingIn(s, player.level().getGameTime())) {
			return 0.0f;
		}
		float t = (player.level().getGameTime() - s.formChangedAt) + partialTick;
		float f = Math.max(0.0f, Math.min(1.0f, t / DROP_TICKS));
		return f * f * (3.0f - 2.0f * f);
	}

	public static void apply(Player player, HumanoidModel<?> m) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s != null && deathFall(player, s)) {
			applyDeathFall(player, s, m, partial);
			return;
		}
		float k = kneel(player, partial);
		if (k <= 0.0f || s == null) {
			// v0.16.1: body.z / head.z / hat.z are reset for every player by HumanoidModelMixin#projecthero$resetTorsoZ,
			// first, before any pose (resetting them here clobbered the hip-bent poses applied before this one)
			applyAbsorb(player, m, partial);
			return;
		}
		float t = (player.level().getGameTime() - s.formChangedAt) + partial;
		float[] fr = sample(STYLES[style(s)], t);
		float age = player.tickCount + partial;
		float shake = fr[SHAKE] * k;
		float jx = Mth.sin(age * 2.7f) * shake;
		float jz = Mth.cos(age * 3.3f) * shake;
		// the rip's chest heave: slow deep breaths under the flex
		if (style(s) == HulkState.TRANSFORM_RIP && t < 66.0f) {
			fr[LEAN_] += Mth.sin(age * 0.45f) * 0.07f;
		}
		poseFrame(m, k, fr, jx, jz);
	}

	/** The frame at {@code t} ticks: smoothstep between the two keys around it. */
	private static float[] sample(float[][] keys, float t) {
		if (t <= keys[0][TICK]) {
			return keys[0].clone();
		}
		for (int i = 1; i < keys.length; i++) {
			float[] b = keys[i];
			if (t <= b[TICK]) {
				float[] a = keys[i - 1];
				float f = (t - a[TICK]) / Math.max(0.001f, b[TICK] - a[TICK]);
				f = f * f * (3.0f - 2.0f * f);
				float[] r = new float[N];
				for (int j = 0; j < N; j++) {
					r[j] = a[j] + (b[j] - a[j]) * f;
				}
				return r;
			}
		}
		return keys[keys.length - 1].clone();
	}

	/**
	 * v0.16.4: one frame of a change sequence -- {@link #pose} for the hunch, head and shoulders, then each arm its own
	 * angles, both legs, and the elbows / knees bent through {@link JointedLimbs}.
	 */
	private static void poseFrame(HumanoidModel<?> m, float k, float[] fr, float jx, float jz) {
		// what the vanilla animation had (walk swing...), eased out of over the first ticks
		float rax = m.rightArm.xRot, lax = m.leftArm.xRot, ray = m.rightArm.yRot, lay = m.leftArm.yRot, raz = m.rightArm.zRot, laz = m.leftArm.zRot;
		float rlx = m.rightLeg.xRot, llx = m.leftLeg.xRot, rlz = m.rightLeg.zRot, llz = m.leftLeg.zRot;
		float lean = fr[LEAN_] * k + jx * 0.5f;
		pose(m, k, lean, fr[HEAD], 0.0f, 0.0f, jx, jz);
		m.rightArm.xRot = Mth.lerp(k, rax, fr[RAX] + lean) + jz;
		m.leftArm.xRot = Mth.lerp(k, lax, fr[LAX] + lean) - jz;
		m.rightArm.yRot = Mth.lerp(k, ray, -fr[RAIN]);
		m.leftArm.yRot = Mth.lerp(k, lay, fr[LAIN]);
		m.rightArm.zRot = Mth.lerp(k, raz, fr[RAOUT]);
		m.leftArm.zRot = Mth.lerp(k, laz, -fr[LAOUT]);
		m.rightLeg.xRot = Mth.lerp(k, rlx, fr[RLX]);
		m.leftLeg.xRot = Mth.lerp(k, llx, fr[LLX]);
		m.rightLeg.zRot = Mth.lerp(k, rlz, fr[STANCE_]);
		m.leftLeg.zRot = Mth.lerp(k, llz, -fr[STANCE_]);
		joints(fr[REL] * k, fr[LEL] * k, fr[RKN] * k, fr[LKN] * k);
	}

	/** Elbow flexion bends the forearm forward (-X), knee flexion the shin back (+X); ~0 leaves the limb straight. */
	private static void joints(float rElbow, float lElbow, float rKnee, float lKnee) {
		JointedLimbs.setJoint(JointedLimbs.RIGHT_ARM, Math.abs(rElbow) > 0.002f ? new Quaternionf().rotationX(-rElbow) : null);
		JointedLimbs.setJoint(JointedLimbs.LEFT_ARM, Math.abs(lElbow) > 0.002f ? new Quaternionf().rotationX(-lElbow) : null);
		JointedLimbs.setJoint(JointedLimbs.RIGHT_LEG, Math.abs(rKnee) > 0.002f ? new Quaternionf().rotationX(rKnee) : null);
		JointedLimbs.setJoint(JointedLimbs.LEFT_LEG, Math.abs(lKnee) > 0.002f ? new Quaternionf().rotationX(lKnee) : null);
	}

	/**
	 * v0.16.4 (user): "he drops flat on his back in Hulk form, slowly detransforms into the regular player" -- Banner, fading
	 * in under the Hulk, lies on his back where the Hulk fell: arms flung out, knees a little up, face to the sky. Every part
	 * of the model is turned together about a point just above the ground (a rigid rotation of the whole body by -90
	 * degrees about X), easing in with the Hulk's fall.
	 */
	private static void applyDeathFall(Player player, HulkState s, HumanoidModel<?> m, float partial) {
		float since = (player.level().getGameTime() - s.animStart) + partial;
		float f = Math.max(0.0f, Math.min(1.0f, since / DEATH_FALL_TICKS));
		f = f * f * (3.0f - 2.0f * f);
		float age = player.tickCount + partial;
		float breath = Mth.sin(age * 0.12f) * 0.03f;
		// the lying pose, standing frame (then the whole thing is laid down)
		m.body.xRot = 0.0f;
		m.body.yRot = 0.0f;
		m.body.zRot = 0.0f;
		m.body.y = 0.0f;
		m.body.z = 0.0f;
		m.head.xRot = -0.15f + breath;
		m.head.yRot = 0.35f;
		m.head.zRot = 0.0f;
		m.head.y = 0.0f;
		m.head.z = 0.0f;
		m.rightArm.y = 2.0f;
		m.leftArm.y = 2.0f;
		m.rightArm.z = 0.0f;
		m.leftArm.z = 0.0f;
		m.rightArm.xRot = -0.2f;
		m.rightArm.yRot = 0.0f;
		m.rightArm.zRot = 1.15f + breath;
		m.leftArm.xRot = -0.35f;
		m.leftArm.yRot = 0.0f;
		m.leftArm.zRot = -1.3f - breath;
		m.rightLeg.y = 12.0f;
		m.leftLeg.y = 12.0f;
		m.rightLeg.z = 0.0f;
		m.leftLeg.z = 0.0f;
		m.rightLeg.xRot = -0.2f;
		m.rightLeg.yRot = 0.0f;
		m.rightLeg.zRot = 0.14f;
		m.leftLeg.xRot = -0.05f;
		m.leftLeg.yRot = 0.0f;
		m.leftLeg.zRot = -0.1f;
		joints(0.35f, 0.25f, 0.4f, 0.12f);
		// lay it all down: -90 degrees about X round (0, 22, 0) -- the back on the ground (y 24), the head behind his feet
		float ang = -Mth.HALF_PI * f;
		Quaternionf lay = new Quaternionf().rotationX(ang);
		Vector3f centre = new Vector3f(0.0f, 22.0f, 0.0f);
		for (ModelPart part : new ModelPart[] { m.body, m.head, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg }) {
			Vector3f p = new Vector3f(part.x, part.y, part.z).sub(centre);
			lay.transform(p).add(centre);
			part.x = p.x;
			part.y = p.y;
			part.z = p.z;
			Quaternionf q = new Quaternionf(lay).mul(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot));
			Vector3f e = q.getEulerAnglesZYX(new Vector3f());
			part.xRot = e.x;
			part.yRot = e.y;
			part.zRot = e.z;
		}
		m.hat.copyFrom(m.head);
	}

	/**
	 * v0.16.4 (user): "during that flash the player needs to fall to their knees and absorb the gamma radiation into their
	 * body" -- {@code GammaOverload.KNEEL_AT} ticks into a reactor's flash he braces where he stands (v0.16.4 user: standing,
	 * not kneeling), arms held a little out from his sides, his head tipping back and his body arching as the radiation
	 * builds, a tremble growing with it. Driven by the synced {@code GAMMA_ABSORB} state, so every viewer sees it.
	 */
	private static void applyAbsorb(Player player, HumanoidModel<?> m, float partial) {
		com.projecthero.mod.hulk.data.GammaAbsorb a = player.getAttachedOrElse(ModAttachments.GAMMA_ABSORB, null);
		if (a == null) {
			return;
		}
		float t = (player.level().getGameTime() - a.startedAt()) + partial;
		float k = Math.max(0.0f, Math.min(1.0f, (t - GammaOverload.KNEEL_AT) / DROP_TICKS));
		if (k <= 0.0f) {
			return;
		}
		k = k * k * (3.0f - 2.0f * k);
		float build = Math.max(0.0f, Math.min(1.0f, (t - GammaOverload.ABSORB_START) / (GammaOverload.FLASH_TICKS - GammaOverload.ABSORB_START)));
		float age = player.tickCount + partial;
		float shake = (0.012f + 0.05f * build) * k;
		float jx = Mth.sin(age * 2.9f) * shake;
		float jz = Mth.cos(age * 3.7f) * shake;
		// standing braced at first, then arching back as it fills him
		float lean = (0.12f - 0.32f * build) * k + jx * 0.5f;
		float head = 0.25f - 1.0f * build;
		pose(m, k, lean, head, -0.35f - 0.25f * build, 0.5f + 0.2f * build, jx, jz);
		// v0.16.4: fists clenched, elbows and knees a little bent against it
		joints((0.5f + 0.4f * build) * k, (0.5f + 0.4f * build) * k, 0.15f * k, 0.15f * k);
	}

	/**
	 * v0.16.4: the mind body's pose ({@code HulkMindBodyRenderer}) -- standing, hunched, both hands clutching his head,
	 * looking down at the ground, shaking. {@code age} drives the shiver.
	 */
	public static void applyHeadache(HumanoidModel<?> m, float age) {
		float shake = 0.035f;
		float jx = Mth.sin(age * 2.7f) * shake;
		float jz = Mth.cos(age * 3.3f) * shake;
		pose(m, 1.0f, LEAN + 0.08f + jx * 0.5f, HEAD_BOW + 0.25f, ARM_UP, ARM_IN, jx, jz);
	}

	/**
	 * The pose itself, {@code k} 0..1 of the way into it: standing braced (v0.16.4), the body leans
	 * {@code lean} from the hips (the neck swings forward and down round a fixed hip point, head and shoulders with it), the
	 * head bows to {@code headX}, the arms go to {@code armX} (on top of the lean) and out by {@code armZ}; {@code jx}/{@code jz}
	 * is the shiver.
	 */
	private static void pose(HumanoidModel<?> m, float k, float lean, float headX, float armX, float armZ, float jx, float jz) {
		float hipY = 12.0f;
		// the body leans from the hips: the neck swings forward and down
		float neckY = hipY - 12.0f * Mth.cos(lean);
		float neckZ = -12.0f * Mth.sin(lean);
		m.body.xRot = lean;
		m.body.yRot = Mth.lerp(k, m.body.yRot, 0.0f);
		m.body.y = neckY;
		m.body.z = neckZ;

		m.head.y = neckY;
		m.head.z = neckZ;
		m.head.xRot = Mth.lerp(k, m.head.xRot, headX) + jz;
		m.head.yRot = Mth.lerp(k, m.head.yRot, 0.0f);
		m.head.zRot = jx;
		m.hat.copyFrom(m.head);

		// shoulders sit 2 px below the neck, in the leaning body's frame
		float shoulderY = neckY + 2.0f * Mth.cos(lean);
		float shoulderZ = neckZ + 2.0f * Mth.sin(lean);
		m.rightArm.y = Mth.lerp(k, m.rightArm.y, shoulderY);
		m.leftArm.y = Mth.lerp(k, m.leftArm.y, shoulderY);
		m.rightArm.z = Mth.lerp(k, m.rightArm.z, shoulderZ);
		m.leftArm.z = Mth.lerp(k, m.leftArm.z, shoulderZ);
		m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, armX + lean) + jz;
		m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, armX + lean) - jz;
		m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, 0.0f);
		m.leftArm.yRot = Mth.lerp(k, m.leftArm.yRot, 0.0f);
		m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, armZ);
		m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, -armZ);

		// v0.16.4 (user): standing, legs straight and braced a little apart (was on his knees, legs folded back)
		m.rightLeg.y = hipY;
		m.leftLeg.y = hipY;
		m.rightLeg.z = 0.0f;
		m.leftLeg.z = 0.0f;
		m.rightLeg.xRot = Mth.lerp(k, m.rightLeg.xRot, 0.0f);
		m.leftLeg.xRot = Mth.lerp(k, m.leftLeg.xRot, 0.0f);
		m.rightLeg.yRot = Mth.lerp(k, m.rightLeg.yRot, 0.0f);
		m.leftLeg.yRot = Mth.lerp(k, m.leftLeg.yRot, 0.0f);
		m.rightLeg.zRot = Mth.lerp(k, m.rightLeg.zRot, STANCE);
		m.leftLeg.zRot = Mth.lerp(k, m.leftLeg.zRot, -STANCE);
	}
}
