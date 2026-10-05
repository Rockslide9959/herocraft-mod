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
 *   <li><b>Suit-up</b> (v0.14.27): one of {@link IronManSuitFx#POSE_VARIANTS} keyframed poses, picked at random by the
 *       server per suit-up ({@link IronManSuitFx#poseVariant()}). Each variant has a start and an end key for every piece
 *       (boots, leggings, chestplate, helmet): the body moves from one to the other while that piece builds, cross-fades
 *       into the next piece's keys when it starts, and the piece that has just finished gives an accent beat (boots: a
 *       stomp, leggings: a knee dip, chestplate: a chest-out flex, helmet: a head snap). A faint servo tremor rides on
 *       top while a piece is building.</li>
 *   <li><b>Receive</b> (couriers / the Mark VII pod inbound): arms wider and braced, chin up, waiting for the pieces;
 *       once a piece lands and builds the suit-up variant takes over.</li>
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
	/** Cross-fade from the last piece's end key into the next piece's keys. */
	private static final float CROSS_FADE = 12f;
	/** Length of the "piece locked on" accent beat. */
	private static final float ACCENT = 10f;

	/**
	 * One key: right arm x/y/z, left arm x/y/z, right leg x/z, left leg x/z, head x/y. Arm / leg z is the real model
	 * angle (right +z and left -z both swing the limb out to the side); x negative swings a limb forward / up.
	 */
	record Key(float rax, float ray, float raz, float lax, float lay, float laz, float rlx, float rlz, float llx, float llz,
			float hx, float hy) {
		Key lerp(Key o, float t) {
			return new Key(Mth.lerp(t, rax, o.rax), Mth.lerp(t, ray, o.ray), Mth.lerp(t, raz, o.raz),
					Mth.lerp(t, lax, o.lax), Mth.lerp(t, lay, o.lay), Mth.lerp(t, laz, o.laz),
					Mth.lerp(t, rlx, o.rlx), Mth.lerp(t, rlz, o.rlz), Mth.lerp(t, llx, o.llx), Mth.lerp(t, llz, o.llz),
					Mth.lerp(t, hx, o.hx), Mth.lerp(t, hy, o.hy));
		}
	}

	private static Key k(float rax, float ray, float raz, float lax, float lay, float laz, float rlx, float rlz, float llx,
			float llz, float hx, float hy) {
		return new Key(rax, ray, raz, lax, lay, laz, rlx, rlz, llx, llz, hx, hy);
	}

	/**
	 * [variant][piece bit 0 HEAD .. 3 FEET][0 start key, 1 end key]. The pieces build boots -> leggings -> chestplate ->
	 * helmet, so read each variant bottom-up.
	 */
	static final Key[][][] KEYS = {
			// 0 "Iron Stance": watches the boots form, arms drift out, a full T as the chest builds, chin up for the helmet
			{
					{ k(-0.10f, 0f, 0.55f, -0.10f, 0f, -0.55f, 0f, 0.14f, 0f, -0.14f, -0.30f, 0f),
							k(0.05f, 0f, 0.30f, 0.05f, 0f, -0.30f, 0f, 0.12f, 0f, -0.12f, 0.05f, 0f) },
					{ k(-0.05f, 0f, 0.90f, -0.05f, 0f, -0.90f, 0f, 0.14f, 0f, -0.14f, 0.20f, 0f),
							k(0f, 0f, 1.45f, 0f, 0f, -1.45f, 0f, 0.16f, 0f, -0.16f, -0.05f, 0f) },
					{ k(0f, 0f, 0.35f, 0f, 0f, -0.35f, 0f, 0.14f, 0f, -0.14f, 0.45f, 0f),
							k(-0.10f, 0f, 0.65f, -0.10f, 0f, -0.65f, 0f, 0.16f, 0f, -0.16f, 0.30f, 0f) },
					{ k(0f, 0f, 0.20f, 0f, 0f, -0.20f, 0f, 0.06f, 0f, -0.06f, 0.65f, 0f),
							k(0f, 0f, 0.30f, 0f, 0f, -0.30f, 0f, 0.14f, 0f, -0.14f, 0.55f, 0f) } },
			// 1 "Reach for the Sky": arms straight up while the legs form, opening to a wide V for the chest, down for the helmet
			{
					{ k(-0.20f, 0f, 0.35f, -0.20f, 0f, -0.35f, 0f, 0.08f, 0f, -0.08f, -0.40f, 0f),
							k(0f, 0f, 0.12f, 0f, 0f, -0.12f, 0f, 0.08f, 0f, -0.08f, 0f, 0f) },
					{ k(-2.60f, 0f, 0.55f, -2.60f, 0f, -0.55f, 0f, 0.08f, 0f, -0.08f, -0.25f, 0f),
							k(-2.20f, 0f, 1.00f, -2.20f, 0f, -1.00f, 0f, 0.10f, 0f, -0.10f, -0.35f, 0f) },
					{ k(-2.60f, 0f, 0.45f, -2.60f, 0f, -0.45f, 0f, 0.06f, 0f, -0.06f, 0.35f, 0f),
							k(-2.50f, 0f, 0.55f, -2.50f, 0f, -0.55f, 0f, 0.08f, 0f, -0.08f, 0.15f, 0f) },
					{ k(-2.45f, 0f, 0.50f, -2.45f, 0f, -0.50f, 0f, 0.04f, 0f, -0.04f, 0.60f, 0f),
							k(-2.60f, 0f, 0.45f, -2.60f, 0f, -0.45f, 0f, 0.06f, 0f, -0.06f, 0.45f, 0f) } },
			// 2 "Inspect the Gauntlet": Tony turns his right hand over and studies it as the suit grows up his body
			{
					{ k(-1.85f, -0.15f, 0.05f, 0f, 0f, -0.30f, 0f, 0.06f, 0f, -0.06f, -0.05f, -0.40f),
							k(-0.45f, 0f, 0.15f, -0.45f, 0f, -0.15f, 0f, 0.06f, 0f, -0.06f, 0.10f, 0f) },
					{ k(-1.45f, -0.15f, 0.05f, 0.05f, 0f, -0.55f, 0f, 0.06f, 0f, -0.06f, 0.15f, -0.35f),
							k(-1.80f, -0.20f, 0.10f, 0.05f, 0f, -0.75f, 0f, 0.06f, 0f, -0.06f, -0.10f, -0.45f) },
					{ k(-1.10f, -0.25f, 0f, 0f, 0f, -0.20f, 0f, 0.06f, 0f, -0.06f, 0.45f, -0.20f),
							k(-1.35f, -0.30f, 0f, 0f, 0f, -0.25f, 0f, 0.06f, 0f, -0.06f, 0.30f, -0.30f) },
					{ k(-0.20f, 0f, 0.10f, 0f, 0f, -0.10f, 0f, 0.06f, 0f, -0.06f, 0.60f, 0.15f),
							k(-0.95f, -0.20f, 0f, 0f, 0f, -0.15f, 0f, 0.06f, 0f, -0.06f, 0.50f, -0.10f) } },
			// 3 "Power Brace": fists together low and a wide braced stance, arms flung back as the chest locks, fists crossed for the helmet
			{
					{ k(-1.30f, 0.55f, 0f, -1.30f, -0.55f, 0f, 0f, 0.20f, 0f, -0.20f, 0.25f, 0f),
							k(-1.40f, 0.65f, 0f, -1.40f, -0.65f, 0f, 0f, 0.18f, 0f, -0.18f, -0.15f, 0f) },
					{ k(-0.50f, 0.20f, 0.30f, -0.50f, -0.20f, -0.30f, 0f, 0.22f, 0f, -0.22f, 0.30f, 0f),
							k(0.55f, 0f, 0.75f, 0.55f, 0f, -0.75f, 0f, 0.24f, 0f, -0.24f, -0.25f, 0f) },
					{ k(-0.70f, 0.40f, 0f, -0.70f, -0.40f, 0f, -0.15f, 0.26f, 0.15f, -0.26f, 0.50f, 0f),
							k(-0.60f, 0.45f, 0f, -0.60f, -0.45f, 0f, -0.10f, 0.24f, 0.10f, -0.24f, 0.40f, 0f) },
					{ k(-0.55f, 0.35f, 0f, -0.55f, -0.35f, 0f, -0.20f, 0.22f, 0.20f, -0.22f, 0.65f, 0f),
							k(-0.65f, 0.40f, 0f, -0.65f, -0.40f, 0f, -0.15f, 0.26f, 0.15f, -0.26f, 0.55f, 0f) } },
	};

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
						Key build = buildKey(fx, now, partial, false);
						if (build != null) {
							apply(model, w, build);
						} else {
							arms(model, w, -0.25f, 1.05f, -0.25f, 1.05f);
							model.head.xRot = Mth.lerp(w, model.head.xRot, model.head.xRot - 0.18f);
						}
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
						Key build = buildKey(fx, now, partial, true);
						if (build != null) {
							apply(model, w, build);
						} else {
							arms(model, w, -0.35f, 0.62f, -0.35f, 0.62f);
						}
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

	/**
	 * v0.14.27: this frame's suit-up key for {@code fx}'s variant, driven by the piece building right now and the piece
	 * that finished before it -- or null (no piece has a build clock and {@code idleToo} is false).
	 */
	static Key buildKey(IronManSuitFx fx, long now, float partial, boolean idleToo) {
		int variant = Math.floorMod(fx.poseVariant(), IronManSuitFx.POSE_VARIANTS);
		Key[][] keys = KEYS[variant];
		int window = fx.lockTicks();
		// the piece building now (latest start inside its window) and the last finished one before it
		int cur = -1;
		int prev = -1;
		for (int bit = 0; bit < 4; bit++) {
			if (!fx.assembling(bit) || fx.start(bit) <= 0L) {
				continue;
			}
			float a = now - fx.start(bit) + partial;
			if (a >= 0f && a < window) {
				if (cur < 0 || fx.start(bit) > fx.start(cur)) {
					cur = bit;
				}
			}
		}
		for (int bit = 0; bit < 4; bit++) {
			if (bit == cur || !fx.assembling(bit) || fx.start(bit) <= 0L || fx.start(bit) < fx.poseStart()) {
				continue;
			}
			float a = now - fx.start(bit) + partial;
			if (a >= window && (cur < 0 || fx.start(bit) <= fx.start(cur)) && (prev < 0 || fx.start(bit) > fx.start(prev))) {
				prev = bit;
			}
		}
		if (cur < 0 && prev < 0) {
			return idleToo ? keys[3][0] : null; // waiting for the first piece: the boots' opening key
		}
		Key target;
		float tremor = 0f;
		if (cur >= 0) {
			float a = now - fx.start(cur) + partial;
			float p = Mth.clamp(a / window, 0f, 1f);
			target = keys[cur][0].lerp(keys[cur][1], smooth(p));
			if (prev >= 0) {
				target = keys[prev][1].lerp(target, smooth(a / CROSS_FADE));
			} else if (a < CROSS_FADE) {
				target = keys[3][0].lerp(target, smooth(a / CROSS_FADE));
			}
			tremor = 0.025f * (float) Math.sin(a * 1.9f) * (1f - p * 0.5f);
		} else {
			target = keys[prev][1]; // all done: hold the last piece's end key into the faceplate beat
		}
		// the accent beat of the piece that has just locked on
		if (prev >= 0) {
			float since = now - (fx.start(prev) + window) + partial;
			if (since >= 0f && since < ACCENT) {
				target = accent(target, prev, (float) Math.sin(Math.PI * since / ACCENT));
			}
		}
		if (tremor != 0f) {
			target = new Key(target.rax + tremor, target.ray, target.raz + tremor * 0.6f, target.lax - tremor,
					target.lay, target.laz - tremor * 0.6f, target.rlx, target.rlz, target.llx, target.llz, target.hx,
					target.hy);
		}
		return target;
	}

	/** The "locked on" beat of piece {@code bit} at strength {@code s} (0..1..0). */
	private static Key accent(Key t, int bit, float s) {
		return switch (bit) {
			// boots: a stomp -- the right leg kicks up and slams down, arms jolt
			case 3 -> new Key(t.rax + 0.10f * s, t.ray, t.raz + 0.08f * s, t.lax + 0.10f * s, t.lay, t.laz - 0.08f * s,
					t.rlx - 0.55f * s, t.rlz, t.llx, t.llz, t.hx + 0.10f * s, t.hy);
			// leggings: both knees brace out, head dips
			case 2 -> new Key(t.rax, t.ray, t.raz, t.lax, t.lay, t.laz, t.rlx - 0.12f * s, t.rlz + 0.12f * s,
					t.llx - 0.12f * s, t.llz - 0.12f * s, t.hx + 0.18f * s, t.hy);
			// chestplate: a chest-out flex, arms thrown back and out
			case 1 -> new Key(t.rax + 0.35f * s, t.ray, t.raz + 0.25f * s, t.lax + 0.35f * s, t.lay, t.laz - 0.25f * s,
					t.rlx, t.rlz, t.llx, t.llz, t.hx - 0.20f * s, t.hy);
			// helmet: the head snaps level and the fists clench in
			default -> new Key(t.rax - 0.15f * s, t.ray, t.raz - 0.10f * s, t.lax - 0.15f * s, t.lay, t.laz + 0.10f * s,
					t.rlx, t.rlz, t.llx, t.llz, t.hx - 0.30f * s, t.hy * (1f - s));
		};
	}

	/** Blend every limb and the head toward {@code k} by {@code w}. */
	private static void apply(HumanoidModel<?> model, float w, Key k) {
		model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, k.rax);
		model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, k.ray);
		model.rightArm.zRot = Mth.lerp(w, model.rightArm.zRot, k.raz);
		model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, k.lax);
		model.leftArm.yRot = Mth.lerp(w, model.leftArm.yRot, k.lay);
		model.leftArm.zRot = Mth.lerp(w, model.leftArm.zRot, k.laz);
		model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, k.rlx);
		model.rightLeg.zRot = Mth.lerp(w, model.rightLeg.zRot, k.rlz);
		model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, k.llx);
		model.leftLeg.zRot = Mth.lerp(w, model.leftLeg.zRot, k.llz);
		model.head.xRot = Mth.lerp(w, model.head.xRot, k.hx);
		model.head.yRot = Mth.lerp(w * 0.8f, model.head.yRot, k.hy);
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
