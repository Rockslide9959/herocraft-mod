package com.projecthero.mod.client.worthy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.blackpanther.BlackPantherPose;
import com.projecthero.mod.supersoldier.SuperSoldier;
import com.projecthero.mod.worthy.WorthyState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.7 Thor x Super Soldier: the lent-weapon moves' animations, keyed off the synced {@link WorthyState#anim} / {@code animStart}
 * so every viewer sees the same thing. The {@code SuperSoldierPose} clip technique (anticipation, a SNAP into the strike, follow-through,
 * recovery faded back to vanilla; the twist carries the shoulders, the lean bends from the hips, a crouch uses the sneak offsets),
 * with Thor's weapon language: the hammer whirled overhead (take-off, Launch, Whirl Guard -- a procedural spin on top of the clip),
 * raised to the sky to call lightning, driven into the ground, hurled and caught.
 * <p>
 * Clips are written for the WEAPON arm as the right arm ("lead") and the shield / free arm as the left; a left-handed player, or a
 * weapon held in the off hand ({@link WorthyState#animOff}), plays them mirrored. Third person from {@code WorthyPoseMixin}
 * (after Thor's and the Super Soldier's own poses), first person from {@code ItemInHandRendererWorthyMixin}.
 */
public final class WorthyPose {
	private static final float N = Float.NaN;
	private static final int RX = 0, RY = 1, RZ = 2, LX = 3, LY = 4, LZ = 5, TWIST = 6, LEAN = 7, RLEG = 8, LLEG = 9, CROUCH = 10, HEAD = 11,
			CH = 12;
	private static final int SMOOTH = 0, SNAP = 1, LINEAR = 2;

	private WorthyPose() {
	}

	private record Key(float t, int ease, float[] v) {
	}

	private record Clip(float duration, float in, float out, boolean hold, Key[] keys) {
	}

	private static Key k(float t, int ease, float rx, float ry, float rz, float lx, float ly, float lz, float twist, float lean, float rLeg,
			float lLeg, float crouch) {
		return new Key(t, ease, new float[] { rx, ry, rz, lx, ly, lz, twist, lean, rLeg, lLeg, crouch, N });
	}

	private static Key kh(float t, int ease, float rx, float ry, float rz, float lx, float ly, float lz, float twist, float lean, float rLeg,
			float lLeg, float crouch, float head) {
		return new Key(t, ease, new float[] { rx, ry, rz, lx, ly, lz, twist, lean, rLeg, lLeg, crouch, head });
	}

	private static Clip clip(float duration, float in, float out, Key... keys) {
		return new Clip(duration, in, out, false, keys);
	}

	private static Clip held(float duration, float in, float out, Key... keys) {
		return new Clip(duration, in, out, true, keys);
	}

	// ------------------------------------------------------------------ Mjolnir / both
	private static final Clip HAMMER_COMBO = clip(20f, 0.5f, 5f,
			// 1: forehand swing, the hips through it
			k(1.5f, SNAP, -1.5f, 0.6f, 0f, -0.6f, 0.3f, -0.2f, -0.6f, 0.2f, 0.3f, -0.3f, 0.2f),
			// drawn back over the other shoulder
			k(4f, SMOOTH, -2.2f, -0.5f, 0.3f, -0.5f, 0f, -0.2f, 0.5f, 0.05f, 0.3f, -0.3f, 0.2f),
			// 2: backhand
			k(6.2f, SNAP, -1.4f, -0.75f, 0.1f, -0.7f, 0.4f, -0.2f, 0.55f, 0.25f, 0.3f, -0.3f, 0.25f),
			// up overhead for the finisher
			kh(9f, SMOOTH, -3.0f, 0f, 0.2f, -1.2f, 0f, -0.3f, 0f, -0.15f, -0.2f, 0.2f, 0.1f, -0.25f),
			// 3: the overhead smash
			k(11f, SNAP, -0.9f, 0f, 0.1f, -0.6f, 0f, -0.2f, -0.1f, 0.45f, 0.5f, -0.4f, 0.5f),
			k(15f, LINEAR, -0.95f, 0f, 0.1f, -0.6f, 0f, -0.2f, -0.1f, 0.42f, 0.5f, -0.4f, 0.5f));
	private static final Clip UPPERCUT = clip(16f, 0.5f, 4f,
			k(1f, SNAP, 0.4f, 0f, 0.2f, -0.6f, 0.3f, -0.2f, 0.4f, 0.3f, 0.4f, -0.4f, 0.5f),
			kh(3f, SNAP, -2.8f, -0.2f, 0.2f, -0.4f, 0f, -0.3f, -0.4f, -0.15f, -0.3f, 0.2f, 0f, -0.3f),
			kh(10f, LINEAR, -2.75f, -0.2f, 0.2f, -0.4f, 0f, -0.3f, -0.35f, -0.12f, -0.3f, 0.2f, 0f, -0.25f));
	private static final Clip RAISE = clip(18f, 1f, 4f,
			// the weapon straight up to the sky, the free hand low and open, head back
			kh(2.5f, SMOOTH, -3.05f, 0f, 0.15f, -0.3f, 0f, -0.45f, 0f, -0.12f, -0.05f, 0.05f, 0.1f, -0.45f),
			kh(14f, LINEAR, -3.05f, 0f, 0.15f, -0.3f, 0f, -0.45f, 0f, -0.1f, -0.05f, 0.05f, 0.1f, -0.4f));
	private static final Clip THUNDERCLAP = clip(20f, 0.5f, 5f,
			kh(3f, SMOOTH, -3.0f, 0f, 0.1f, -2.4f, 0f, -0.2f, 0f, -0.15f, -0.2f, 0.2f, 0f, -0.3f),
			k(5f, SNAP, -0.5f, 0f, 0.1f, -0.8f, 0f, -0.2f, 0f, 0.7f, -1.0f, 0.4f, 1.0f),
			k(14f, LINEAR, -0.55f, 0f, 0.1f, -0.8f, 0f, -0.2f, 0f, 0.65f, -1.0f, 0.4f, 1.0f));
	private static final Clip SLAM_RISE = held(60f, 0.5f, 3f,
			k(2f, SMOOTH, -3.0f, 0f, 0.2f, -2.6f, 0f, -0.3f, 0f, -0.1f, -0.6f, -0.2f, 0f),
			k(56f, LINEAR, -3.0f, 0f, 0.2f, -2.6f, 0f, -0.3f, 0f, -0.1f, -0.6f, -0.2f, 0f));
	private static final Clip SLAM_HIT = clip(18f, 0.3f, 5f,
			kh(1f, SNAP, -0.6f, -0.1f, 0f, -0.7f, 0.2f, 0f, 0f, 0.6f, -1.25f, 0.45f, 1f, 0.2f),
			kh(12f, LINEAR, -0.62f, -0.1f, 0f, -0.7f, 0.2f, 0f, 0f, 0.55f, -1.2f, 0.45f, 1f, 0.15f));
	private static final Clip WORTHY_STORM = clip(50f, 1f, 6f,
			kh(4f, SMOOTH, -3.05f, 0f, 0.1f, -0.4f, 0f, -0.6f, 0f, -0.15f, 0f, 0f, 0.05f, -0.5f),
			kh(34f, LINEAR, -3.08f, 0f, 0.1f, -0.45f, 0f, -0.65f, 0f, -0.18f, 0f, 0f, 0.05f, -0.55f),
			// the burst: the hammer brought down into the ground
			k(37f, SNAP, -0.6f, 0f, 0.1f, -0.6f, 0f, -0.3f, 0f, 0.6f, -1.2f, 0.4f, 1.0f),
			k(44f, LINEAR, -0.62f, 0f, 0.1f, -0.6f, 0f, -0.3f, 0f, 0.55f, -1.2f, 0.4f, 1.0f));
	private static final Clip DASH = clip(12f, 0.5f, 3f,
			k(1.5f, SNAP, -1.6f, 0f, 0f, 0.6f, 0f, -0.3f, 0f, 0.5f, 0.6f, -0.8f, 0.1f),
			k(8f, LINEAR, -1.6f, 0f, 0f, 0.6f, 0f, -0.3f, 0f, 0.5f, 0.6f, -0.8f, 0.1f));
	/** The whirl overhead (take-off, Launch): the spin itself is added procedurally ({@link #whirl}). */
	private static final Clip WHIRL_UP = clip(16f, 1f, 4f,
			kh(1.5f, SMOOTH, -2.9f, 0f, 0.2f, -0.3f, 0f, -0.45f, 0f, -0.1f, 0f, 0f, 0.25f, -0.3f),
			kh(11f, LINEAR, -2.95f, 0f, 0.2f, -0.3f, 0f, -0.5f, 0f, -0.1f, -0.3f, 0.1f, 0f, -0.3f));
	/** Whirl Guard / Axe Guard: the weapon spun in front (spin procedural). */
	private static final Clip WHIRL_GUARD = clip(32f, 1f, 4f,
			k(1.5f, SNAP, -1.5f, -0.35f, 0f, -0.7f, 0.5f, -0.1f, 0.2f, 0.15f, 0.3f, -0.3f, 0.3f),
			k(28f, LINEAR, -1.5f, -0.35f, 0f, -0.7f, 0.5f, -0.1f, 0.2f, 0.15f, 0.3f, -0.3f, 0.3f));
	private static final Clip RALLY = clip(26f, 1.5f, 5f,
			kh(3f, SMOOTH, -1.3f, -0.7f, 0f, -0.3f, 0f, -0.3f, 0.15f, 0.18f, 0.1f, -0.1f, 0.5f, 0.1f),
			kh(6f, SNAP, -3.0f, 0.05f, 0.2f, 0.15f, 0f, -0.35f, -0.05f, -0.12f, -0.05f, 0.05f, 0.2f, -0.38f),
			kh(20f, LINEAR, -3.0f, 0.05f, 0.2f, 0.12f, 0f, -0.32f, -0.05f, -0.1f, -0.05f, 0.05f, 0.22f, -0.32f));
	private static final Clip THROW = clip(14f, 0.5f, 4f,
			k(1f, SMOOTH, -2.8f, 0.3f, 0.5f, -0.8f, 0.3f, -0.2f, 0.6f, -0.1f, 0.3f, -0.3f, 0.1f),
			k(3f, SNAP, -1.5f, -0.2f, 0f, 0.3f, 0f, -0.3f, -0.6f, 0.3f, 0.5f, -0.4f, 0.2f),
			k(8f, SMOOTH, -1.3f, -0.2f, 0f, 0.2f, 0f, -0.3f, -0.4f, 0.2f, 0.4f, -0.3f, 0.15f));
	// ------------------------------------------------------------------ Stormbreaker
	private static final Clip AXE_CLEAVES = clip(20f, 0.5f, 5f,
			k(1.5f, SNAP, -1.45f, 0.75f, 0f, -0.6f, 0.3f, -0.2f, -0.75f, 0.25f, 0.4f, -0.4f, 0.3f),
			k(5f, SMOOTH, -1.8f, -0.8f, 0.3f, -0.5f, 0f, -0.2f, 0.7f, 0.1f, 0.3f, -0.3f, 0.3f),
			k(9f, SNAP, -1.35f, 0.9f, 0.1f, -0.7f, 0.4f, -0.2f, -0.9f, 0.35f, 0.5f, -0.5f, 0.4f),
			k(15f, LINEAR, -1.35f, 0.85f, 0.1f, -0.7f, 0.4f, -0.2f, -0.85f, 0.32f, 0.5f, -0.5f, 0.4f));
	private static final Clip OVERHEAD_CHOP = clip(18f, 0.5f, 5f,
			// two-handed overhead, then the chop into the ground
			kh(1.5f, SMOOTH, -3.0f, -0.3f, 0.1f, -3.0f, 0.3f, -0.1f, 0f, -0.15f, -0.2f, 0.2f, 0f, -0.3f),
			k(3.5f, SNAP, -0.65f, -0.3f, 0.05f, -0.65f, 0.3f, -0.05f, 0f, 0.65f, -1.0f, 0.4f, 0.9f),
			k(12f, LINEAR, -0.68f, -0.3f, 0.05f, -0.68f, 0.3f, -0.05f, 0f, 0.6f, -1.0f, 0.4f, 0.9f));
	private static final Clip EXEC_CHOP = held(40f, 0.5f, 3f,
			kh(2f, SMOOTH, -3.0f, -0.3f, 0.1f, -3.0f, 0.3f, -0.1f, 0f, -0.2f, -0.8f, -0.3f, 0f, -0.25f),
			kh(36f, LINEAR, -3.0f, -0.3f, 0.1f, -3.0f, 0.3f, -0.1f, 0f, -0.2f, -0.8f, -0.3f, 0f, -0.25f));
	private static final Clip VERDICT = clip(42f, 1f, 5f,
			k(3f, SMOOTH, -2.9f, 0.3f, 0.4f, -0.6f, 0f, -0.4f, 0.5f, -0.1f, 0.2f, -0.2f, 0.1f),
			k(6.5f, SNAP, -1.4f, -0.2f, 0f, 0.3f, 0f, -0.3f, -0.6f, 0.3f, 0.5f, -0.4f, 0.2f),
			// pointing at the storm he called down
			kh(14f, SMOOTH, -1.65f, 0f, 0.1f, -0.2f, 0f, -0.3f, -0.2f, 0.1f, 0.3f, -0.3f, 0.1f, 0f),
			kh(32f, LINEAR, -1.65f, 0f, 0.1f, -0.2f, 0f, -0.3f, -0.2f, 0.1f, 0.3f, -0.3f, 0.1f, 0f),
			// the catch
			kh(35f, SNAP, -2.6f, 0.1f, 0.3f, -0.4f, 0f, -0.4f, 0.1f, 0f, 0.1f, -0.1f, 0.1f, -0.3f));
	// ------------------------------------------------------------------ shield + weapon (left arm = the shield)
	private static final Clip SHIELD_STRIKE = clip(18f, 0.5f, 5f,
			// the bash: shield shoved out, weapon cocked low behind
			k(1.2f, SNAP, -0.5f, 0f, 0.35f, -1.6f, 0.3f, -0.1f, 0.5f, 0.3f, 0.4f, -0.4f, 0.3f),
			k(4f, SMOOTH, -2.2f, 0.4f, 0.4f, -1.4f, 0.4f, -0.1f, 0.3f, 0.15f, 0.3f, -0.3f, 0.3f),
			// the weapon comes through
			k(6.8f, SNAP, -1.5f, -0.35f, 0f, -1.1f, 0.45f, -0.1f, -0.6f, 0.3f, 0.5f, -0.5f, 0.35f),
			k(13f, LINEAR, -1.45f, -0.35f, 0f, -1.1f, 0.45f, -0.1f, -0.55f, 0.28f, 0.5f, -0.5f, 0.35f));
	private static final Clip BASH_LAUNCH = clip(18f, 0.5f, 5f,
			kh(1.5f, SNAP, -0.3f, 0f, 0.3f, -2.6f, 0.2f, -0.2f, 0.4f, -0.1f, -0.3f, 0.2f, 0f, -0.3f),
			kh(7f, SMOOTH, -3.0f, 0f, 0.2f, -2.0f, 0.2f, -0.2f, 0.1f, -0.2f, -0.2f, 0.2f, 0f, -0.4f),
			k(9.5f, SNAP, -0.7f, 0f, 0.1f, -1.0f, 0.3f, -0.1f, -0.4f, 0.5f, 0.4f, -0.4f, 0.6f),
			k(14f, LINEAR, -0.72f, 0f, 0.1f, -1.0f, 0.3f, -0.1f, -0.4f, 0.45f, 0.4f, -0.4f, 0.6f));
	private static final Clip SHIELD_FLING = clip(14f, 0.5f, 4f,
			// the shield arm across the chest, then flung out (the weapon held back out of the way)
			k(1f, SNAP, -0.4f, 0f, 0.3f, -1.45f, 1.25f, 0f, -0.7f, 0.05f, -0.2f, 0.2f, 0f),
			k(3f, SNAP, -0.4f, 0f, 0.3f, -1.55f, -0.8f, -0.7f, 0.65f, 0.15f, -0.2f, 0.2f, 0f),
			k(9f, SMOOTH, -0.4f, 0f, 0.3f, -1.2f, -0.6f, -0.6f, 0.4f, 0.1f, -0.1f, 0.1f, 0f));
	private static final Clip TWIN_THROW = clip(14f, 0.5f, 4f,
			k(1f, SMOOTH, -2.8f, 0.3f, 0.5f, -1.45f, 1.25f, 0f, 0f, -0.05f, 0.3f, -0.3f, 0.1f),
			k(3f, SNAP, -1.5f, -0.2f, 0f, -1.55f, -0.8f, -0.7f, 0f, 0.3f, 0.5f, -0.4f, 0.2f),
			k(8f, SMOOTH, -1.3f, -0.2f, 0f, -1.2f, -0.6f, -0.6f, 0f, 0.2f, 0.4f, -0.3f, 0.15f));
	private static final Clip CLAP = clip(30f, 1f, 6f,
			// the shield up in front, the weapon raised high and back
			kh(4f, SMOOTH, -3.0f, 0.3f, 0.5f, -1.6f, 0.45f, -0.1f, 0.35f, -0.1f, 0.3f, -0.3f, 0.3f, -0.2f),
			kh(11f, LINEAR, -3.1f, 0.35f, 0.55f, -1.62f, 0.45f, -0.1f, 0.4f, -0.12f, 0.3f, -0.3f, 0.35f, -0.2f),
			// the weapon struck down onto the shield
			kh(12.5f, SNAP, -1.55f, -0.55f, 0f, -1.6f, 0.45f, -0.1f, 0.1f, 0.25f, 0.5f, -0.5f, 0.5f, 0.05f),
			kh(22f, LINEAR, -1.5f, -0.55f, 0f, -1.58f, 0.45f, -0.1f, 0.1f, 0.22f, 0.5f, -0.5f, 0.5f, 0.05f));
	private static final Clip LIGHTNING_GUARD = clip(52f, 1f, 4f,
			k(1.5f, SNAP, -2.4f, 0.2f, 0.4f, -1.55f, 0.75f, -0.1f, 0.15f, 0.1f, 0.35f, -0.35f, 0.45f),
			k(48f, LINEAR, -2.4f, 0.2f, 0.4f, -1.55f, 0.75f, -0.1f, 0.15f, 0.1f, 0.35f, -0.35f, 0.45f));

	private static Clip clipFor(int anim) {
		return switch (anim) {
			case WorthyState.ANIM_HAMMER_COMBO -> HAMMER_COMBO;
			case WorthyState.ANIM_UPPERCUT -> UPPERCUT;
			case WorthyState.ANIM_CALL_LIGHTNING -> RAISE;
			case WorthyState.ANIM_THUNDERCLAP -> THUNDERCLAP;
			case WorthyState.ANIM_SLAM_RISE -> SLAM_RISE;
			case WorthyState.ANIM_SLAM_HIT -> SLAM_HIT;
			case WorthyState.ANIM_WORTHY_STORM -> WORTHY_STORM;
			case WorthyState.ANIM_DASH -> DASH;
			case WorthyState.ANIM_LAUNCH, WorthyState.ANIM_TAKEOFF -> WHIRL_UP;
			case WorthyState.ANIM_GUARD -> WHIRL_GUARD;
			case WorthyState.ANIM_RALLY -> RALLY;
			case WorthyState.ANIM_THROW -> THROW;
			case WorthyState.ANIM_AXE_CLEAVES -> AXE_CLEAVES;
			case WorthyState.ANIM_LIGHTNING_CLEAVE -> OVERHEAD_CHOP;
			case WorthyState.ANIM_EXEC_CHOP -> EXEC_CHOP;
			case WorthyState.ANIM_VERDICT -> VERDICT;
			case WorthyState.ANIM_SHIELD_STRIKE -> SHIELD_STRIKE;
			case WorthyState.ANIM_BASH_LAUNCH -> BASH_LAUNCH;
			case WorthyState.ANIM_SHIELD_THROW -> SHIELD_FLING;
			case WorthyState.ANIM_TWIN_THROW -> TWIN_THROW;
			case WorthyState.ANIM_CLAP -> CLAP;
			case WorthyState.ANIM_LIGHTNING_GUARD -> LIGHTNING_GUARD;
			default -> null;
		};
	}

	// ================================================================== sampling (as SuperSoldierPose)

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

	private static WorthyState state(Player player) {
		if (player.level() == null || !SuperSoldier.hasPower(player)) {
			return null;
		}
		WorthyState s = WorthyState.of(player);
		return s.anim == WorthyState.ANIM_NONE ? null : s;
	}

	private static float age(Player player, WorthyState s, Clip c, float partial) {
		float age = player.level().getGameTime() - s.animStart + partial;
		if (c.hold && !player.onGround() && !player.isInWater()) {
			age = Math.min(age, c.keys[c.keys.length - 1].t);
		}
		return age;
	}

	/** True while one of the lent-weapon clips owns the body. */
	public static boolean active(Player player) {
		WorthyState s = state(player);
		Clip c = s == null ? null : clipFor(s.anim);
		if (c == null) {
			return false;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		return weight(c, age(player, s, c, partial)) > 0.001f;
	}

	// ================================================================== third person

	public static void apply(Player player, HumanoidModel<?> model) {
		if (BlackPantherPose.inFirstPersonHand) {
			return;
		}
		WorthyState s = state(player);
		Clip c = s == null ? null : clipFor(s.anim);
		if (c == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float age = age(player, s, c, partial);
		float w = weight(c, age);
		if (w <= 0.001f) {
			return;
		}
		boolean right = (player.getMainArm() == HumanoidArm.RIGHT) != s.animOff;
		float[] base = base(model, right);
		float[] pose = sample(c, age, base);
		whirl(s.anim, age, pose);
		write(model, base, pose, w, right);
	}

	/** The procedural spin on top of the whirl clips: the weapon arm circling overhead, or the weapon spun in front (the guard). */
	private static void whirl(int anim, float age, float[] pose) {
		if (anim == WorthyState.ANIM_TAKEOFF || anim == WorthyState.ANIM_LAUNCH) {
			float a = age * 2.4f;
			pose[RY] += Mth.sin(a) * 0.55f;
			pose[RZ] += Mth.cos(a) * 0.3f;
		} else if (anim == WorthyState.ANIM_GUARD) {
			float a = age * 2.8f;
			pose[RZ] += Mth.sin(a) * 0.45f;
			pose[RX] += Mth.cos(a) * 0.3f;
		}
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
		m.head.xRot = v[HEAD];
		m.hat.copyFrom(m.head);
	}

	// ================================================================== first person

	/** First-person keys: {dx, dy, dz, rotX, rotY, rotZ} for the weapon hand ("lead") and the other hand. */
	private record FKey(float t, int ease, float[] lead, float[] off) {
	}

	private record FClip(float duration, float in, float out, boolean hold, FKey[] keys) {
	}

	private static float[] f(float dx, float dy, float dz, float rx, float ry, float rz) {
		return new float[] { dx, dy, dz, rx, ry, rz };
	}

	private static final float[] REST = f(0, 0, 0, 0, 0, 0);

	private static FKey fk(float t, int ease, float[] lead, float[] off) {
		return new FKey(t, ease, lead, off);
	}

	private static final FClip F_SWINGS = new FClip(20f, 0.5f, 5f, false, new FKey[] {
			fk(1.5f, SNAP, f(-0.35f, 0.05f, -0.2f, -20f, 40f, 30f), REST),
			fk(4f, SMOOTH, f(0.15f, 0.25f, 0.05f, 40f, -30f, -20f), REST),
			fk(6.2f, SNAP, f(-0.25f, 0.05f, -0.25f, -15f, -40f, -30f), REST),
			fk(9f, SMOOTH, f(0f, 0.55f, 0.1f, 70f, 0f, 0f), REST),
			fk(11f, SNAP, f(-0.1f, -0.25f, -0.35f, -60f, 0f, 0f), REST),
			fk(15f, LINEAR, f(-0.1f, -0.25f, -0.33f, -58f, 0f, 0f), REST) });
	private static final FClip F_RAISE = new FClip(18f, 1f, 4f, false, new FKey[] {
			fk(2.5f, SMOOTH, f(-0.1f, 0.6f, -0.1f, 75f, 0f, 0f), f(0.05f, -0.15f, 0.05f, -10f, 0f, 0f)),
			fk(14f, LINEAR, f(-0.1f, 0.62f, -0.1f, 76f, 0f, 0f), f(0.05f, -0.15f, 0.05f, -10f, 0f, 0f)) });
	private static final FClip F_SMASH = new FClip(18f, 0.5f, 5f, false, new FKey[] {
			fk(2f, SMOOTH, f(-0.05f, 0.55f, 0.1f, 70f, 0f, 0f), f(-0.05f, 0.4f, 0.1f, 50f, 0f, 0f)),
			fk(4f, SNAP, f(-0.1f, -0.3f, -0.35f, -65f, 0f, 0f), f(-0.1f, -0.2f, -0.2f, -40f, 0f, 0f)),
			fk(12f, LINEAR, f(-0.1f, -0.3f, -0.33f, -62f, 0f, 0f), f(-0.1f, -0.2f, -0.2f, -38f, 0f, 0f)) });
	private static final FClip F_THROW = new FClip(14f, 0.5f, 4f, false, new FKey[] {
			fk(1f, SMOOTH, f(0.15f, 0.35f, 0.25f, 50f, -20f, -20f), REST),
			fk(3f, SNAP, f(-0.15f, 0.05f, -0.5f, -10f, 0f, 0f), REST),
			fk(8f, SMOOTH, f(-0.1f, 0f, -0.35f, -5f, 0f, 0f), REST) });
	private static final FClip F_CLEAVES = new FClip(20f, 0.5f, 5f, false, new FKey[] {
			fk(1.5f, SNAP, f(-0.45f, 0f, -0.2f, -10f, 60f, 40f), REST),
			fk(5f, SMOOTH, f(0.2f, 0.2f, 0.05f, 30f, -40f, -30f), REST),
			fk(9f, SNAP, f(-0.5f, 0f, -0.25f, -10f, 70f, 45f), REST),
			fk(15f, LINEAR, f(-0.48f, 0f, -0.24f, -10f, 68f, 44f), REST) });
	private static final FClip F_GUARD = new FClip(32f, 1f, 4f, false, new FKey[] {
			fk(1.5f, SNAP, f(-0.25f, 0.15f, -0.25f, 10f, 0f, 0f), f(-0.3f, 0.25f, -0.1f, 25f, 0f, -30f)),
			fk(28f, LINEAR, f(-0.25f, 0.15f, -0.25f, 10f, 0f, 0f), f(-0.3f, 0.25f, -0.1f, 25f, 0f, -30f)) });
	private static final FClip F_SHIELD_STRIKE = new FClip(18f, 0.5f, 5f, false, new FKey[] {
			fk(1.2f, SNAP, f(0.1f, -0.15f, 0.1f, -10f, 0f, 0f), f(-0.35f, 0.2f, -0.45f, 15f, 0f, -20f)),
			fk(4f, SMOOTH, f(0.1f, 0.35f, 0.1f, 50f, -20f, -15f), f(-0.3f, 0.2f, -0.25f, 15f, 0f, -20f)),
			fk(6.8f, SNAP, f(-0.3f, 0.02f, -0.35f, -20f, -35f, -25f), f(-0.15f, 0.1f, -0.1f, 10f, 0f, -10f)),
			fk(13f, LINEAR, f(-0.28f, 0.02f, -0.33f, -18f, -33f, -24f), f(-0.15f, 0.1f, -0.1f, 10f, 0f, -10f)) });
	private static final FClip F_CLAP = new FClip(30f, 1f, 6f, false, new FKey[] {
			fk(4f, SMOOTH, f(0.05f, 0.6f, 0.15f, 75f, -10f, -10f), f(-0.3f, 0.25f, -0.15f, 20f, 0f, -30f)),
			fk(11f, LINEAR, f(0.05f, 0.62f, 0.15f, 78f, -10f, -10f), f(-0.3f, 0.25f, -0.15f, 20f, 0f, -30f)),
			fk(12.5f, SNAP, f(-0.3f, 0.1f, -0.4f, -15f, -30f, -20f), f(-0.3f, 0.25f, -0.2f, 20f, 0f, -30f)),
			fk(22f, LINEAR, f(-0.3f, 0.1f, -0.38f, -14f, -30f, -20f), f(-0.3f, 0.25f, -0.2f, 20f, 0f, -30f)) });

	private static FClip fclipFor(int anim) {
		return switch (anim) {
			case WorthyState.ANIM_HAMMER_COMBO, WorthyState.ANIM_UPPERCUT -> F_SWINGS;
			case WorthyState.ANIM_CALL_LIGHTNING, WorthyState.ANIM_RALLY, WorthyState.ANIM_TAKEOFF, WorthyState.ANIM_LAUNCH,
					WorthyState.ANIM_SLAM_RISE, WorthyState.ANIM_EXEC_CHOP -> F_RAISE;
			case WorthyState.ANIM_THUNDERCLAP, WorthyState.ANIM_SLAM_HIT, WorthyState.ANIM_WORTHY_STORM, WorthyState.ANIM_LIGHTNING_CLEAVE,
					WorthyState.ANIM_BASH_LAUNCH -> F_SMASH;
			case WorthyState.ANIM_THROW, WorthyState.ANIM_VERDICT, WorthyState.ANIM_TWIN_THROW, WorthyState.ANIM_DASH -> F_THROW;
			case WorthyState.ANIM_AXE_CLEAVES -> F_CLEAVES;
			case WorthyState.ANIM_GUARD, WorthyState.ANIM_LIGHTNING_GUARD -> F_GUARD;
			case WorthyState.ANIM_SHIELD_STRIKE -> F_SHIELD_STRIKE;
			case WorthyState.ANIM_CLAP -> F_CLAP;
			default -> null;
		};
	}

	private static float fage(Player player, WorthyState s, FClip c, float partial) {
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
		float[] va = a == null ? REST : (lead ? a.lead : a.off);
		if (b == null) {
			return va.clone();
		}
		float[] vb = lead ? b.lead : b.off;
		float ta = a == null ? 0f : a.t;
		float t = ease(b.ease, Mth.clamp((time - ta) / Math.max(0.01f, b.t - ta), 0f, 1f));
		for (int j = 0; j < 6; j++) {
			out[j] = Mth.lerp(t, va[j], vb[j]);
		}
		return out;
	}

	/** How much a first-person move owns the hands right now (0 = vanilla's). */
	public static float firstPersonWeight(Player player, float partial) {
		WorthyState s = state(player);
		FClip c = s == null ? null : fclipFor(s.anim);
		return c == null ? 0f : fweight(c, fage(player, s, c, partial));
	}

	/** Moves the first-person item in hand {@code arm} for the move playing now (just before vanilla draws it). */
	public static boolean firstPerson(Player player, HumanoidArm arm, float partial, PoseStack pose) {
		WorthyState s = state(player);
		FClip c = s == null ? null : fclipFor(s.anim);
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
		if (lead && (s.anim == WorthyState.ANIM_TAKEOFF || s.anim == WorthyState.ANIM_LAUNCH || s.anim == WorthyState.ANIM_GUARD)) {
			v[5] += (float) (age * 60.0 % 360.0) * w; // the whirl
		}
		pose.translate(side * v[0], v[1], v[2]);
		pose.mulPose(Axis.ZP.rotationDegrees(side * v[5]));
		pose.mulPose(Axis.YP.rotationDegrees(side * v[4]));
		pose.mulPose(Axis.XP.rotationDegrees(v[3]));
		return true;
	}
}
