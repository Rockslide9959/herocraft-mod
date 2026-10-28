package com.projecthero.mod.client.quirk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.fpbody.FirstPersonBody;
import com.projecthero.mod.quirk.PlusUltra;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 Quirk framework: <b>first-person arm poses</b> and the <b>full-body first-person</b> view for big moves.
 *
 * <p><b>Arms</b> ({@code mixin.QuirkFirstPersonPoseMixin} on {@code ItemInHandRenderer}): today's first-person arm mixins
 * only drew sleeves; these MOVE the arms. A clip is a list of 13-column frames
 * <pre>
 *   { tick,  mainPitch, mainYaw, mainRoll, mainX, mainY, mainZ,  offPitch, offYaw, offRoll, offX, offY, offZ }
 * </pre>
 * -- degrees about the arm's first-person shoulder (pitch negative = the fist rises up into view; yaw positive = toward the
 * screen centre; roll positive = the knuckles turn in) and offsets in blocks (x toward the centre, y up, z negative = away,
 * into the screen). The main arm is the one in your main hand's side (mirrored for left-handed players). A
 * {@link Clip#twoHanded} clip also draws the OFF arm (vanilla never draws an empty off hand). Frames are smoothstepped and
 * faded like {@link QuirkKeyframes}. Register a clip for one of the kit's synced anim ids:
 * <pre>
 *   QuirkFirstPersonPose.register(Quirk.EXPLOSION, Explosion.ANIM_AP_SHOT, QuirkFirstPersonPose.clip(
 *       QuirkFirstPersonPose.f(0, 0, 0, 0, 0, 0, 0,  0, 0, 0, 0, 0, 0),
 *       QuirkFirstPersonPose.f(3, -25, 18, 0, -0.1f, 0.12f, -0.15f,  -20, 30, 0, -0.2f, 0.1f, -0.1f),
 *       QuirkFirstPersonPose.f(9, 0, 0, 0, 0, 0, 0,  0, 0, 0, 0, 0, 0)).twoHanded());
 * </pre>
 * Both hands empty only: a held item keeps vanilla's hand.
 *
 * <p><b>Full body</b>: {@link #fullBody(Quirk, int)} marks anim ids (Howitzer spin, kicks, ultimates) for which the
 * local player sees their whole body in first person ({@link FirstPersonBody}) -- their own legs and arms doing the move,
 * the third-person keyframes everyone else sees. The Plus Ultra hold and the first {@link #ULTIMATE_FP_TICKS} after an
 * ultimate always use it.
 */
public final class QuirkFirstPersonPose {
	public static final int COLS = 13;
	/** After an ultimate fires, the full-body first-person view stays this long (kits with longer ultimates add predicates). */
	public static final int ULTIMATE_FP_TICKS = 30;

	/** A first-person clip. */
	public static final class Clip {
		final float[][] frames;
		boolean twoHanded;

		Clip(float[][] frames) {
			this.frames = frames;
		}

		/** Also draw (and pose) the off arm. */
		public Clip twoHanded() {
			this.twoHanded = true;
			return this;
		}

		public float length() {
			return frames[frames.length - 1][0];
		}
	}

	private static final Map<Long, Clip> CLIPS = new HashMap<>();
	private static final Set<Long> FULL_BODY = new HashSet<>();
	/** The framework's own: the Plus Ultra hold (both fists clenched in, trembling). */
	private static final Clip HOLD = clip(
			f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			f(4, -32, 22, 18, -0.08f, 0.14f, 0.05f, -32, 22, 18, -0.08f, 0.14f, 0.05f),
			f(20, -38, 26, 22, -0.1f, 0.16f, 0.04f, -38, 26, 22, -0.1f, 0.16f, 0.04f)).twoHanded();

	private QuirkFirstPersonPose() {
	}

	/** One frame. */
	public static float[] f(float tick, float mp, float my, float mr, float mx, float mY, float mz, float op, float oy, float or, float ox, float oY,
			float oz) {
		return new float[] { tick, mp, my, mr, mx, mY, mz, op, oy, or, ox, oY, oz };
	}

	public static Clip clip(float[]... frames) {
		return new Clip(frames);
	}

	private static long key(Quirk q, int animId) {
		return ((long) q.ordinal() << 32) | (animId & 0xFFFFFFFFL);
	}

	public static void register(Quirk q, int animId, Clip clip) {
		CLIPS.put(key(q, animId), clip);
	}

	/** In first person, show the whole body while this anim plays. */
	public static void fullBody(Quirk q, int animId) {
		FULL_BODY.add(key(q, animId));
	}

	/** From {@code QuirkClient.initialize}: the full-body view hook. */
	static void initialize() {
		FirstPersonBody.register(QuirkFirstPersonPose::showsFullBody);
	}

	private static boolean showsFullBody(AbstractClientPlayer p) {
		if (p != Minecraft.getInstance().player) {
			return false;
		}
		Quirk q = Quirks.quirk(p);
		if (q == null || q.standalone) {
			return false;
		}
		if (PlusUltra.clientHold(p) >= 0f || PlusUltra.sinceFired(p) < ULTIMATE_FP_TICKS) {
			return true;
		}
		QuirkState s = Quirks.peek(p);
		if (s != null && s.animId != 0 && FULL_BODY.contains(key(q, s.animId))) {
			float age = p.level().getGameTime() - s.animStart;
			Clip c = CLIPS.get(key(q, s.animId));
			int len = c != null ? (int) c.length() : 30;
			return age >= 0 && age < Math.max(len, 20);
		}
		QuirkClientKit kit = QuirkClient.kitFor(q);
		return kit != null && kit.fullBodyFirstPerson(p);
	}

	/** A sampled pose: the 13 columns (index 0 = the tick) and its fade weight. */
	public record Sample(float[] v, float weight, boolean twoHanded) {
	}

	/** What the local player's first-person arms should do this frame, or null (vanilla). */
	public static Sample current(Player p, float partial) {
		// v0.17.1: a superhero landing's planted fist (any power, not only Quirks)
		Sample landing = com.projecthero.mod.client.flight.HeroLandingClient.firstPerson(p, partial);
		if (landing != null) {
			return landing;
		}
		Quirk q = Quirks.quirk(p);
		if (q == null || q.standalone) {
			return null;
		}
		float hold = PlusUltra.clientHold(p);
		if (hold >= 0f) {
			float tick = Math.min(HOLD.length(), hold * 20f + 1f);
			float[] v = QuirkKeyframes.sampleN(HOLD.frames, tick, COLS);
			if (v != null) {
				float shake = 1.5f + 3f * hold;
				float n = Mth.sin((p.tickCount + partial) * 2.9f) * shake;
				v[1] += n;
				v[7] -= n;
				return new Sample(v, Math.min(1f, tick / 3f), true);
			}
		}
		QuirkState s = Quirks.peek(p);
		if (s == null || s.animId == 0) {
			return null;
		}
		Clip c = CLIPS.get(key(q, s.animId));
		if (c == null) {
			return null;
		}
		float age = p.level().getGameTime() - s.animStart + partial;
		float[] v = QuirkKeyframes.sampleN(c.frames, age, COLS);
		if (v == null) {
			return null;
		}
		return new Sample(v, QuirkKeyframes.weight(age, c.length()), c.twoHanded);
	}

	/**
	 * Applies one arm's part of a sample to the hand pose stack ({@code main}: columns 1-6, else 7-12) for the arm on
	 * {@code side} (+1 right, -1 left), about that arm's first-person shoulder.
	 */
	public static void apply(PoseStack pose, Sample s, boolean main, float side) {
		int o = main ? 1 : 7;
		float w = s.weight();
		float pitch = s.v()[o] * w, yaw = s.v()[o + 1] * w, roll = s.v()[o + 2] * w;
		float dx = s.v()[o + 3] * w, dy = s.v()[o + 4] * w, dz = s.v()[o + 5] * w;
		// the arm's first-person shoulder (where vanilla's renderPlayerArm starts), so the fist swings about it
		float px = side * 0.64f, py = -0.6f, pz = -0.72f;
		pose.translate(-side * dx, dy, dz);
		pose.translate(px, py, pz);
		pose.mulPose(Axis.YP.rotationDegrees(-side * yaw));
		pose.mulPose(Axis.XP.rotationDegrees(-pitch));
		pose.mulPose(Axis.ZP.rotationDegrees(side * roll));
		pose.translate(-px, -py, -pz);
	}
}
