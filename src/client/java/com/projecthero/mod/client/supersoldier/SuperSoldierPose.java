package com.projecthero.mod.client.supersoldier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.blackpanther.BlackPantherPose;
import com.projecthero.mod.supersoldier.data.SuperSoldierState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.7 (user: "cool animations and stuff make it good please"): the Super Soldier's move animations, keyed off the synced
 * {@link SuperSoldierState#animId} / {@code animStart} so every viewer sees the same thing. Built on the Black Panther clip system
 * (anticipation, a snap through the strike, follow-through, recovery, faded over vanilla's pose): both arms, the torso twist (the
 * shoulders move with it), a forward lean, the legs, a crouch with vanilla's sneak offsets, the head; a whole-body spin (the Storm's
 * throw) and flip (the Storm's catch) from {@code SuperSoldierBodyMixin}; first-person hands from {@code ItemInHandRendererSuperSoldierMixin}.
 *
 * <ul>
 *   <li><b>Assemble</b> -- gathers the fist to the chest in a crouch, then punches it straight up, legs planted wide, chest out,
 *       head back (the shield arm when a shield is in the off hand); held, then eased down.</li>
 *   <li><b>Counter Stance</b> -- forearms crossed in front of the face, low, weight on the back foot; held for the window. The counter:
 *       a palm strike / shield bash straight out with the hips through it.</li>
 *   <li><b>Peak Human Barrage</b> -- the dash (right hand cocked back, leaning in, a sprinting stride), then right straight, left hook,
 *       right body shot, a knee, a left elbow, a right uppercut, each snapped in on its hit tick; the right hand drawn all the way
 *       back for the wind-up, then the haymaker lunge with the chest turned through.</li>
 *   <li><b>Shield Storm</b> -- the spring and the rise (arms up), the shield arm wound across the chest and flung out (the body spins
 *       a full turn through the throw), the hover (arms spread, watching); the catch overhead, a tucked front flip, the dive with the
 *       shield held overhead; the slam -- both arms driving down, one knee to the ground.</li>
 * </ul>
 * Clips are written for the lead arm as the right arm; a left-handed player, or a move led by the shield in the off hand
 * ({@link SuperSoldierState#animOff}), plays them mirrored.
 */
public final class SuperSoldierPose {
	private static final float N = Float.NaN;
	private static final int RX = 0, RY = 1, RZ = 2, LX = 3, LY = 4, LZ = 5, TWIST = 6, LEAN = 7, RLEG = 8, LLEG = 9, RLEGZ = 10, LLEGZ = 11,
			CROUCH = 12, HEAD = 13, CH = 14;
	private static final int SMOOTH = 0, SNAP = 1, LINEAR = 2;

	private SuperSoldierPose() {
	}

	// ================================================================== clip data

	private record Key(float t, int ease, float[] v) {
	}

	private record Clip(float duration, float in, float out, boolean hold, Key[] keys) {
	}

	private static Key k(float t, int ease, float rx, float ry, float rz, float lx, float ly, float lz, float twist, float lean, float rLeg,
			float lLeg, float crouch) {
		return new Key(t, ease, new float[] { rx, ry, rz, lx, ly, lz, twist, lean, rLeg, lLeg, N, N, crouch, N });
	}

	private static Key kx(float t, int ease, float rx, float ry, float rz, float lx, float ly, float lz, float twist, float lean, float rLeg,
			float lLeg, float rLegZ, float lLegZ, float crouch, float head) {
		return new Key(t, ease, new float[] { rx, ry, rz, lx, ly, lz, twist, lean, rLeg, lLeg, rLegZ, lLegZ, crouch, head });
	}

	private static Clip clip(float duration, float in, float out, Key... keys) {
		return new Clip(duration, in, out, false, keys);
	}

	/** A clip whose last pose is held while he is still in the air (the Storm's hover and dive). */
	private static Clip held(float duration, float in, float out, Key... keys) {
		return new Clip(duration, in, out, true, keys);
	}

	/** The boxing guard between the Barrage's blows. */
	private static Key guard(float t, float twist, float crouch) {
		return k(t, SMOOTH, -1.2f, -0.45f, 0.1f, -1.4f, 0.5f, -0.1f, twist, 0.18f, N, N, crouch);
	}

	private static final Clip ASSEMBLE = clip(28f, 1.5f, 6f,
			// gather: the fist drawn to the chest, crouched, the off hand low
			kx(3f, SMOOTH, -1.3f, -0.75f, 0f, -0.3f, 0f, -0.3f, 0.15f, 0.18f, 0.1f, -0.1f, 0.1f, -0.1f, 0.5f, 0.1f),
			// the call: the fist straight up, chest out, head back, feet planted wide
			kx(6f, SNAP, -2.95f, 0.05f, 0.22f, 0.15f, 0f, -0.35f, -0.05f, -0.12f, -0.05f, 0.05f, 0.24f, -0.24f, 0.22f, -0.38f),
			kx(20f, LINEAR, -3.0f, 0.05f, 0.2f, 0.12f, 0f, -0.32f, -0.05f, -0.1f, -0.05f, 0.05f, 0.24f, -0.24f, 0.25f, -0.32f));
	private static final Clip COUNTER = clip(24f, 1f, 4f,
			// forearms crossed in front of the face, low, weight on the back foot
			kx(2f, SNAP, -1.75f, -0.68f, 0.1f, -1.55f, 0.78f, -0.1f, 0.12f, 0.14f, 0.35f, -0.35f, N, N, 0.55f, 0.12f),
			kx(20f, LINEAR, -1.72f, -0.65f, 0.1f, -1.52f, 0.75f, -0.1f, 0.1f, 0.12f, 0.35f, -0.35f, N, N, 0.6f, 0.12f));
	private static final Clip COUNTER_HIT = clip(13f, 0.5f, 4f,
			// the answer: lead palm (or the shield) straight out, hips through, a lunge
			k(1.2f, SNAP, -1.72f, -0.1f, 0f, -0.55f, 0.4f, -0.25f, -0.6f, 0.35f, 0.5f, -0.45f, 0.3f),
			k(6f, SMOOTH, -1.5f, -0.2f, 0.05f, -0.7f, 0.4f, -0.2f, -0.45f, 0.25f, 0.4f, -0.35f, 0.25f));
	private static final Clip BARRAGE_DASH = clip(12f, 0.5f, 3f,
			// cocked right hand, leaning in, a sprinting stride
			k(1.5f, SNAP, 0.55f, 0f, 0.3f, -1.35f, 0.35f, 0f, 0.35f, 0.55f, 0.7f, -0.9f, 0.2f),
			k(8f, LINEAR, 0.6f, 0f, 0.3f, -1.4f, 0.35f, 0f, 0.38f, 0.55f, 0.7f, -0.9f, 0.2f));
	private static final Clip BARRAGE = clip(36f, 0.5f, 6f,
			guard(0f, 0f, 0.2f),
			// 1: right straight
			k(1f, SNAP, -1.62f, -0.15f, 0f, -1.25f, 0.55f, -0.1f, -0.5f, 0.22f, N, N, 0.2f),
			guard(2.6f, -0.1f, 0.2f),
			// 2: left hook, the arm swung flat across
			k(4f, SNAP, -1.2f, -0.5f, 0.1f, -1.55f, 0.82f, -0.2f, 0.62f, 0.2f, N, N, 0.25f),
			guard(5.6f, 0.1f, 0.25f),
			// 3: right to the body, dipping
			k(7f, SNAP, -1.15f, -0.3f, 0f, -1.4f, 0.5f, -0.1f, -0.55f, 0.42f, N, N, 0.65f),
			guard(8.6f, 0f, 0.3f),
			// 4: the clinch and the knee
			kx(10f, SNAP, -1.05f, -0.3f, 0.05f, -1.05f, 0.3f, -0.05f, 0f, 0.3f, -1.55f, 0.15f, N, N, 0f, 0.2f),
			guard(11.6f, 0f, 0.2f),
			// 5: the left elbow, flat across
			k(13f, SNAP, -1.2f, -0.4f, 0f, -1.65f, 1.25f, -0.1f, 0.75f, 0.2f, N, N, 0.3f),
			guard(14.6f, 0.1f, 0.25f),
			// 6: the right uppercut
			kx(16f, SNAP, -2.35f, -0.2f, 0.05f, -1.3f, 0.5f, -0.1f, -0.45f, -0.1f, N, N, N, N, 0.1f, -0.15f),
			// the wind-up: the right hand drawn right back, chest turned away, sinking
			k(20f, SMOOTH, 0.75f, 0.2f, 0.45f, -1.45f, 0.6f, -0.1f, 0.7f, -0.05f, 0.35f, -0.35f, 0.45f),
			k(23f, LINEAR, 0.88f, 0.2f, 0.5f, -1.5f, 0.6f, -0.1f, 0.8f, -0.05f, 0.38f, -0.38f, 0.5f),
			// the haymaker: everything through it
			kx(24.5f, SNAP, -1.72f, -0.3f, 0f, 0.45f, 0f, -0.3f, -0.82f, 0.4f, 0.55f, -0.6f, N, N, 0.25f, 0.05f),
			kx(30f, SMOOTH, -1.55f, -0.35f, 0.05f, 0.3f, 0f, -0.3f, -0.65f, 0.3f, 0.45f, -0.5f, N, N, 0.2f, 0f));
	private static final Clip STORM_THROW = held(100f, 0.5f, 4f,
			// spring
			k(1.5f, SNAP, 0.5f, 0f, 0.3f, 0.5f, 0f, -0.3f, 0f, 0.35f, -0.3f, 0.2f, 0.6f),
			// rise, arms up
			kx(3.5f, SMOOTH, -2.7f, 0f, 0.25f, -2.7f, 0f, -0.25f, 0f, -0.1f, -0.7f, -0.2f, N, N, 0f, -0.3f),
			// wind: the shield arm across the chest
			k(5.5f, SMOOTH, -1.45f, -1.25f, 0f, -0.6f, 0.3f, -0.4f, 0.75f, 0.05f, -0.5f, 0.1f, 0f),
			// release: flung out
			k(7.5f, SNAP, -1.55f, 0.8f, 0.7f, 0.2f, 0f, -0.6f, -0.65f, 0.15f, -0.4f, 0.2f, 0f),
			// the hover: arms spread, watching the shield work
			kx(13f, SMOOTH, -0.45f, 0f, 1.05f, -0.45f, 0f, -1.05f, 0f, 0.05f, -0.45f, 0.25f, 0.1f, -0.1f, 0f, 0.25f),
			kx(96f, LINEAR, -0.5f, 0f, 1.0f, -0.5f, 0f, -1.0f, 0f, 0.05f, -0.45f, 0.25f, 0.1f, -0.1f, 0f, 0.25f));
	private static final Clip STORM_CATCH = held(50f, 0.5f, 3f,
			// the catch, overhead
			kx(1.5f, SNAP, -2.8f, 0.1f, 0.35f, -0.6f, 0f, -0.6f, 0.1f, 0f, -0.3f, 0.2f, N, N, 0f, -0.4f),
			// tucked for the front flip
			k(4f, SMOOTH, -1.9f, 0f, 0.2f, -1.9f, 0f, -0.2f, 0f, 0.3f, -1.3f, -1.3f, 0.6f),
			k(9f, LINEAR, -1.9f, 0f, 0.2f, -1.9f, 0f, -0.2f, 0f, 0.3f, -1.3f, -1.3f, 0.6f),
			// the dive: the shield held overhead in both hands
			kx(11f, SNAP, -3.0f, 0f, 0.12f, -3.0f, 0f, -0.12f, 0f, -0.05f, 0.1f, -0.1f, N, N, 0f, -0.2f),
			kx(46f, LINEAR, -3.0f, 0f, 0.12f, -3.0f, 0f, -0.12f, 0f, -0.05f, 0.1f, -0.1f, N, N, 0f, -0.2f));
	private static final Clip STORM_SLAM = clip(22f, 0.3f, 6f,
			// both arms driving the shield down, one knee to the ground
			kx(1f, SNAP, -0.55f, -0.25f, 0f, -0.55f, 0.25f, 0f, 0f, 0.6f, -1.25f, 0.45f, N, N, 1f, 0.2f),
			kx(14f, LINEAR, -0.6f, -0.25f, 0f, -0.6f, 0.25f, 0f, 0f, 0.55f, -1.2f, 0.45f, N, N, 1f, 0.15f));

	private static Clip clipFor(int animId) {
		return switch (animId) {
			case SuperSoldierState.ANIM_ASSEMBLE -> ASSEMBLE;
			case SuperSoldierState.ANIM_COUNTER -> COUNTER;
			case SuperSoldierState.ANIM_COUNTER_HIT -> COUNTER_HIT;
			case SuperSoldierState.ANIM_BARRAGE_DASH -> BARRAGE_DASH;
			case SuperSoldierState.ANIM_BARRAGE -> BARRAGE;
			case SuperSoldierState.ANIM_STORM_THROW -> STORM_THROW;
			case SuperSoldierState.ANIM_STORM_CATCH -> STORM_CATCH;
			case SuperSoldierState.ANIM_STORM_SLAM -> STORM_SLAM;
			default -> null;
		};
	}

	// ================================================================== sampling

	private static float ease(int mode, float t) {
		return switch (mode) {
			case SNAP -> 1f - (1f - t) * (1f - t) * (1f - t);
			case LINEAR -> t;
			default -> t * t * (3f - 2f * t);
		};
	}

	private static float weight(Clip c, float time) {
		if (time < 0f || time >= c.duration) {
			return 0f;
		}
		float in = c.in <= 0f ? 1f : Mth.clamp(time / c.in, 0f, 1f);
		float last = c.keys[c.keys.length - 1].t;
		float out = time <= last ? 1f : 1f - Mth.clamp((time - last) / Math.max(0.01f, Math.min(c.out, c.duration - last)), 0f, 1f);
		return ease(SMOOTH, in) * ease(SMOOTH, out);
	}

	private static float[] sample(Clip c, float time, float[] base) {
		Key[] ks = c.keys;
		float[] out = new float[CH];
		int i = 0;
		while (i < ks.length && ks[i].t <= time) {
			i++;
		}
		Key a = i == 0 ? null : ks[i - 1];
		Key b = i < ks.length ? ks[i] : null;
		for (int ch = 0; ch < CH; ch++) {
			float va = a == null ? base[ch] : val(a.v[ch], base[ch]);
			if (b == null) {
				out[ch] = va;
				continue;
			}
			float vb = val(b.v[ch], base[ch]);
			float ta = a == null ? 0f : a.t;
			float t = b.t <= ta ? 1f : Mth.clamp((time - ta) / (b.t - ta), 0f, 1f);
			out[ch] = Mth.lerp(ease(b.ease, t), va, vb);
		}
		return out;
	}

	private static float val(float v, float base) {
		return Float.isNaN(v) ? base : v;
	}

	private static SuperSoldierState state(Player player) {
		SuperSoldierState s = player.getAttachedOrElse(ModAttachments.SUPER_SOLDIER_STATE, null);
		return s == null || !s.hasPower || player.level() == null ? null : s;
	}

	/** The clip age now; a held clip (the Storm) stops at its last pose while he is still airborne. */
	private static float age(Player player, SuperSoldierState s, Clip c, float partial) {
		float age = player.level().getGameTime() - s.animStart + partial;
		if (c.hold && !player.onGround() && !player.isInWater()) {
			float last = c.keys[c.keys.length - 1].t;
			age = Math.min(age, last);
		}
		return age;
	}

	/** True while one of his move clips is owning the body (the shield poses stand aside). */
	public static boolean active(Player player) {
		SuperSoldierState s = state(player);
		if (s == null) {
			return false;
		}
		Clip c = clipFor(s.animId);
		if (c == null) {
			return false;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		return weight(c, age(player, s, c, partial)) > 0.001f;
	}

	// ================================================================== third person

	public static void apply(Player player, HumanoidModel<?> model) {
		if (BlackPantherPose.inFirstPersonHand) {
			return; // the first-person hand: firstPerson() poses it
		}
		SuperSoldierState s = state(player);
		if (s == null) {
			return;
		}
		Clip c = clipFor(s.animId);
		if (c == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float age = age(player, s, c, partial);
		float w = weight(c, age);
		if (w <= 0.001f) {
			return;
		}
		// the lead arm: the main arm, or the off arm when the shield in the off hand leads
		boolean right = (player.getMainArm() == HumanoidArm.RIGHT) != s.animOff;
		float[] base = base(model, right);
		write(model, base, sample(c, age, base), w, right);
	}

	private static float[] base(HumanoidModel<?> m, boolean right) {
		float[] b = new float[CH];
		float mir = right ? 1f : -1f;
		var ra = right ? m.rightArm : m.leftArm;
		var la = right ? m.leftArm : m.rightArm;
		var rl = right ? m.rightLeg : m.leftLeg;
		var ll = right ? m.leftLeg : m.rightLeg;
		b[RX] = ra.xRot;
		b[RY] = ra.yRot * mir;
		b[RZ] = ra.zRot * mir;
		b[LX] = la.xRot;
		b[LY] = la.yRot * mir;
		b[LZ] = la.zRot * mir;
		b[TWIST] = m.body.yRot * mir;
		b[LEAN] = m.crouching ? 0f : m.body.xRot;
		b[RLEG] = rl.xRot;
		b[LLEG] = ll.xRot;
		b[RLEGZ] = rl.zRot * mir;
		b[LLEGZ] = ll.zRot * mir;
		b[CROUCH] = m.crouching ? 1f : 0f;
		b[HEAD] = m.head.xRot;
		return b;
	}

	private static void write(HumanoidModel<?> m, float[] base, float[] p, float w, boolean right) {
		float mir = right ? 1f : -1f;
		float[] v = new float[CH];
		for (int ch = 0; ch < CH; ch++) {
			v[ch] = Mth.lerp(w, base[ch], p[ch]);
		}
		var ra = right ? m.rightArm : m.leftArm;
		var la = right ? m.leftArm : m.rightArm;
		var rl = right ? m.rightLeg : m.leftLeg;
		var ll = right ? m.leftLeg : m.rightLeg;

		float crouch = Mth.clamp(v[CROUCH], 0f, 1f) - (m.crouching ? 1f : 0f);
		float lean = v[LEAN] + Mth.clamp(v[CROUCH], 0f, 1f) * 0.5f;
		m.body.xRot = lean;
		if (crouch != 0f) {
			m.body.y += 3.2f * crouch;
			m.head.y += 4.2f * crouch;
			m.rightArm.y += 3.2f * crouch;
			m.leftArm.y += 3.2f * crouch;
			m.rightLeg.z += 4.0f * crouch;
			m.leftLeg.z += 4.0f * crouch;
			m.rightLeg.y += 0.2f * crouch;
			m.leftLeg.y += 0.2f * crouch;
		}
		float sneakLean = Mth.clamp(v[CROUCH], 0f, 1f) * 0.5f;
		if (v[LEAN] != 0f) {
			float back = 12f * (Mth.sin(lean) - Mth.sin(sneakLean));
			float drop = 12f * (Mth.cos(sneakLean) - Mth.cos(lean));
			m.rightLeg.z += back;
			m.leftLeg.z += back;
			m.body.y += drop;
			m.head.y += drop;
			m.rightArm.y += drop;
			m.leftArm.y += drop;
		}
		float twist = v[TWIST] * mir;
		m.body.yRot = twist;
		m.rightArm.z = Mth.sin(twist) * 5.0f;
		m.rightArm.x = -Mth.cos(twist) * 5.0f;
		m.leftArm.z = -Mth.sin(twist) * 5.0f;
		m.leftArm.x = Mth.cos(twist) * 5.0f;
		if (v[LEAN] != 0f) {
			float dz = Mth.sin(v[LEAN]) * 2.0f;
			m.rightArm.z += dz;
			m.leftArm.z += dz;
		}
		ra.xRot = v[RX];
		ra.yRot = v[RY] * mir + twist;
		ra.zRot = v[RZ] * mir;
		la.xRot = v[LX];
		la.yRot = v[LY] * mir + twist;
		la.zRot = v[LZ] * mir;
		rl.xRot = v[RLEG];
		ll.xRot = v[LLEG];
		rl.zRot = v[RLEGZ] * mir;
		ll.zRot = v[LLEGZ] * mir;
		m.head.xRot = v[HEAD];
		// the hat / sleeves / trousers layers follow (PlayerModel copies them after setupAnim's tail for the base parts it owns)
		m.hat.copyFrom(m.head);
	}

	// ================================================================== whole-body spin and flip (SuperSoldierBodyMixin)

	/** The Storm's throw: a full turn about his own axis as the shield leaves. 0 when none plays. */
	public static float spinDegrees(Player player, float partial) {
		SuperSoldierState s = state(player);
		if (s == null || s.animId != SuperSoldierState.ANIM_STORM_THROW) {
			return 0f;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		boolean right = (player.getMainArm() == HumanoidArm.RIGHT) != s.animOff;
		return age < 12f ? (right ? -1f : 1f) * 360f * ease(SMOOTH, Mth.clamp((age - 4f) / 5f, 0f, 1f)) : 0f;
	}

	/** The Storm's catch: a tucked front flip about the hips before the dive. 0 when none plays. */
	public static float flipDegrees(Player player, float partial) {
		SuperSoldierState s = state(player);
		if (s == null || s.animId != SuperSoldierState.ANIM_STORM_CATCH) {
			return 0f;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		return age < 12f ? -360f * ease(SMOOTH, Mth.clamp((age - 2f) / 7.5f, 0f, 1f)) : 0f;
	}

	// ================================================================== first person

	private record FKey(float t, int ease, float[] lead, float[] off) {
	}

	private record FClip(float duration, float in, float out, boolean hold, FKey[] keys) {
	}

	private static FKey fk(float t, int ease, float[] lead, float[] off) {
		return new FKey(t, ease, lead, off);
	}

	private static float[] f(float dx, float dy, float dz, float rx, float ry, float rz) {
		return new float[] { dx, dy, dz, rx, ry, rz };
	}

	private static final float[] REST = f(0, 0, 0, 0, 0, 0);
	private static final float[] F_GUARD_LEAD = f(-0.1f, 0.08f, 0.05f, 12f, 0f, 0f);
	private static final float[] F_GUARD_OFF = f(-0.14f, 0.14f, -0.05f, 15f, 5f, 0f);

	private static FKey fguard(float t) {
		return fk(t, SMOOTH, F_GUARD_LEAD, F_GUARD_OFF);
	}

	private static final FClip F_ASSEMBLE = new FClip(28f, 1.5f, 6f, false, new FKey[] {
			fk(3f, SMOOTH, f(-0.22f, -0.02f, 0.05f, 10f, 0f, 0f), f(0.05f, -0.15f, 0.05f, -10f, 0f, 0f)),
			fk(6f, SNAP, f(-0.18f, 0.78f, -0.12f, 72f, 0f, -10f), f(0.05f, -0.2f, 0.05f, -12f, 0f, 0f)),
			fk(20f, LINEAR, f(-0.18f, 0.8f, -0.12f, 74f, 0f, -10f), f(0.05f, -0.2f, 0.05f, -12f, 0f, 0f)) });
	private static final FClip F_COUNTER = new FClip(24f, 1f, 4f, false, new FKey[] {
			fk(2f, SNAP, f(-0.36f, 0.26f, -0.05f, 30f, 0f, -40f), f(-0.36f, 0.32f, -0.1f, 30f, 0f, -40f)),
			fk(20f, LINEAR, f(-0.35f, 0.25f, -0.05f, 30f, 0f, -40f), f(-0.35f, 0.3f, -0.1f, 30f, 0f, -40f)) });
	private static final FClip F_COUNTER_HIT = new FClip(13f, 0.5f, 4f, false, new FKey[] {
			fk(1.2f, SNAP, f(-0.4f, 0.15f, -0.52f, 10f, 10f, 0f), f(0.05f, -0.12f, 0.05f, -10f, 0f, 0f)),
			fk(6f, SMOOTH, f(-0.32f, 0.12f, -0.4f, 8f, 8f, 0f), f(0.05f, -0.1f, 0.05f, -8f, 0f, 0f)) });
	private static final FClip F_BARRAGE_DASH = new FClip(12f, 0.5f, 3f, false, new FKey[] {
			fk(1.5f, SNAP, f(0.12f, -0.18f, 0.2f, -22f, 0f, 0f), f(-0.22f, 0.15f, -0.15f, 15f, 0f, 0f)),
			fk(8f, LINEAR, f(0.12f, -0.18f, 0.2f, -22f, 0f, 0f), f(-0.22f, 0.15f, -0.15f, 15f, 0f, 0f)) });
	private static final FClip F_BARRAGE = new FClip(36f, 0.5f, 6f, false, new FKey[] {
			fguard(0f),
			fk(1f, SNAP, f(-0.4f, 0.14f, -0.46f, 10f, 5f, 0f), F_GUARD_OFF),
			fguard(2.6f),
			fk(4f, SNAP, F_GUARD_LEAD, f(-0.48f, 0.16f, -0.38f, 12f, 18f, -25f)),
			fguard(5.6f),
			fk(7f, SNAP, f(-0.36f, -0.12f, -0.42f, -6f, 5f, 0f), F_GUARD_OFF),
			fguard(8.6f),
			fk(10f, SNAP, f(-0.22f, -0.26f, -0.22f, -30f, 0f, 0f), f(-0.22f, -0.26f, -0.22f, -30f, 0f, 0f)),
			fguard(11.6f),
			fk(13f, SNAP, F_GUARD_LEAD, f(-0.56f, 0.24f, -0.2f, 20f, -10f, -60f)),
			fguard(14.6f),
			fk(16f, SNAP, f(-0.3f, 0.36f, -0.36f, 55f, 0f, 0f), F_GUARD_OFF),
			fk(20f, SMOOTH, f(0.22f, -0.26f, 0.32f, -30f, 0f, 22f), F_GUARD_OFF),
			fk(23f, LINEAR, f(0.24f, -0.28f, 0.34f, -32f, 0f, 24f), F_GUARD_OFF),
			fk(24.5f, SNAP, f(-0.4f, 0.12f, -0.58f, 8f, 12f, 0f), f(0.1f, -0.2f, 0.1f, -15f, 0f, 0f)),
			fk(30f, SMOOTH, f(-0.3f, 0.1f, -0.45f, 6f, 10f, 0f), f(0.08f, -0.15f, 0.08f, -10f, 0f, 0f)) });
	private static final FClip F_STORM_THROW = new FClip(100f, 0.5f, 4f, true, new FKey[] {
			fk(3.5f, SMOOTH, f(-0.05f, 0.45f, 0.05f, 60f, 0f, 0f), f(-0.05f, 0.45f, 0.05f, 60f, 0f, 0f)),
			fk(5.5f, SMOOTH, f(-0.45f, 0.05f, 0.1f, 0f, 0f, 40f), f(0.05f, -0.1f, 0.05f, -10f, 0f, 0f)),
			fk(7.5f, SNAP, f(0.28f, 0.1f, -0.38f, 0f, 0f, -30f), f(0.05f, -0.1f, 0.05f, -10f, 0f, 0f)),
			fk(13f, SMOOTH, f(0.26f, -0.15f, 0.1f, -10f, 0f, 30f), f(0.26f, -0.15f, 0.1f, -10f, 0f, 30f)),
			fk(96f, LINEAR, f(0.26f, -0.15f, 0.1f, -10f, 0f, 30f), f(0.26f, -0.15f, 0.1f, -10f, 0f, 30f)) });
	private static final FClip F_STORM_CATCH = new FClip(50f, 0.5f, 3f, true, new FKey[] {
			fk(1.5f, SNAP, f(-0.1f, 0.56f, -0.1f, 70f, 0f, 0f), REST),
			fk(4f, SMOOTH, f(-0.15f, 0.3f, 0.05f, 40f, 0f, 0f), f(-0.15f, 0.3f, 0.05f, 40f, 0f, 0f)),
			fk(11f, SNAP, f(-0.16f, 0.6f, -0.05f, 80f, 0f, 0f), f(-0.16f, 0.6f, -0.05f, 80f, 0f, 0f)),
			fk(46f, LINEAR, f(-0.16f, 0.6f, -0.05f, 80f, 0f, 0f), f(-0.16f, 0.6f, -0.05f, 80f, 0f, 0f)) });
	private static final FClip F_STORM_SLAM = new FClip(22f, 0.3f, 6f, false, new FKey[] {
			fk(1f, SNAP, f(-0.16f, -0.4f, -0.36f, -45f, 0f, 0f), f(-0.16f, -0.4f, -0.36f, -45f, 0f, 0f)),
			fk(14f, LINEAR, f(-0.15f, -0.38f, -0.34f, -42f, 0f, 0f), f(-0.15f, -0.38f, -0.34f, -42f, 0f, 0f)) });

	private static FClip fclipFor(int animId) {
		return switch (animId) {
			case SuperSoldierState.ANIM_ASSEMBLE -> F_ASSEMBLE;
			case SuperSoldierState.ANIM_COUNTER -> F_COUNTER;
			case SuperSoldierState.ANIM_COUNTER_HIT -> F_COUNTER_HIT;
			case SuperSoldierState.ANIM_BARRAGE_DASH -> F_BARRAGE_DASH;
			case SuperSoldierState.ANIM_BARRAGE -> F_BARRAGE;
			case SuperSoldierState.ANIM_STORM_THROW -> F_STORM_THROW;
			case SuperSoldierState.ANIM_STORM_CATCH -> F_STORM_CATCH;
			case SuperSoldierState.ANIM_STORM_SLAM -> F_STORM_SLAM;
			default -> null;
		};
	}

	private static float fage(Player player, SuperSoldierState s, FClip c, float partial) {
		float age = player.level().getGameTime() - s.animStart + partial;
		if (c.hold && !player.onGround() && !player.isInWater()) {
			age = Math.min(age, c.keys[c.keys.length - 1].t);
		}
		return age;
	}

	private static float fweight(FClip c, float time) {
		return weight(new Clip(c.duration, c.in, c.out, false, new Key[] { new Key(c.keys[c.keys.length - 1].t, SMOOTH, new float[CH]) }),
				time);
	}

	private static float[] fsample(FClip c, float time, boolean lead) {
		float[] out = new float[6];
		FKey a = null, b = null;
		for (FKey key : c.keys) {
			if (key.t <= time) {
				a = key;
			} else {
				b = key;
				break;
			}
		}
		float[] va = a == null ? REST : arm(a, lead);
		if (b == null) {
			return va.clone();
		}
		float[] vb = arm(b, lead);
		float ta = a == null ? 0f : a.t;
		float t = ease(b.ease, Mth.clamp((time - ta) / Math.max(0.01f, b.t - ta), 0f, 1f));
		for (int j = 0; j < 6; j++) {
			out[j] = Mth.lerp(t, va[j], vb[j]);
		}
		return out;
	}

	private static float[] arm(FKey k, boolean lead) {
		float[] v = lead ? k.lead : k.off;
		return v == null ? REST : v;
	}

	/** How much a first-person move owns the empty hands right now (0 = vanilla's). */
	public static float firstPersonWeight(AbstractClientPlayer player, float partial) {
		SuperSoldierState s = state(player);
		if (s == null) {
			return 0f;
		}
		FClip c = fclipFor(s.animId);
		if (c == null) {
			return 0f;
		}
		return fweight(c, fage(player, s, c, partial));
	}

	/** Moves the first-person hand {@code arm} for the move playing now; true if anything was applied. */
	public static boolean firstPerson(AbstractClientPlayer player, HumanoidArm arm, float partial, PoseStack pose) {
		SuperSoldierState s = state(player);
		if (s == null) {
			return false;
		}
		FClip c = fclipFor(s.animId);
		if (c == null) {
			return false;
		}
		float age = fage(player, s, c, partial);
		float w = fweight(c, age);
		if (w <= 0.001f) {
			return false;
		}
		boolean lead = (arm == player.getMainArm()) != s.animOff;
		float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
		float[] d = fsample(c, age, lead);
		float[] v = new float[6];
		for (int j = 0; j < 6; j++) {
			v[j] = d[j] * w;
		}
		float px = side * 0.45f;
		pose.translate(side * v[0], v[1], v[2]);
		pose.translate(px, -0.45f, -0.7f);
		pose.mulPose(Axis.ZP.rotationDegrees(side * v[5]));
		pose.mulPose(Axis.YP.rotationDegrees(side * v[4]));
		pose.mulPose(Axis.XP.rotationDegrees(v[3]));
		pose.translate(-px, 0.45f, 0.7f);
		return true;
	}
}
