package com.projecthero.mod.client.omnitrix;

import java.util.Map;
import java.util.WeakHashMap;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 (user: "in first person when the player activates the omnitrix make the player bring their arm up and then use their
 * right hand to press the button on the omnitrix to pop up the dial ... the right hand should stay there then and also turn the
 * dial ... when the player deactivates the omnitrix make the player press the button again to deactivate it and then put their
 * arm back down"): the raised watch's whole two-hand sequence as one rig, evaluated client-side off the synced state so every
 * viewer agrees -- the wrist's raise, the core's pop ({@code lift}), the face morph, the icon, the right hand's path (in the
 * watch's own space, drawn in first person by {@link OmnitrixFirstPerson}) and its third-person button weight
 * ({@link OmnitrixPose}).
 *
 * <p>Ticks after R (the server's {@code selectStart}): the wrist rises (0-{@link #RAISE_T}), the right hand comes in from the
 * bottom right to the cuff's side button and presses it at {@link Omnitrix#PRESS_TICKS} -- the core pops on that press -- then
 * moves onto the dial's rim and stays, gripping it; the core is up at {@link Omnitrix#CORE_UP_TICKS} (the server's gate for the
 * scroll and the slap). R again: the hand goes back to the button and presses it, the core sinks and the face goes normal, the
 * hand pulls away down-right, then the wrist comes down. Every change of direction starts from wherever the rig is (a snapshot,
 * eased out), so pressing R mid-sequence reverses cleanly.
 */
public final class OmnitrixHands {
	/** The wrist's rise, ticks. */
	public static final float RAISE_T = 5f;
	/** The pop after the press, the face morph, the icon fade-in. */
	public static final float PRESS_T = Omnitrix.PRESS_TICKS, POP_T = 4f, MORPH_AT = PRESS_T + 2f, MORPH_T = 6f, ICON_AT = MORPH_AT + MORPH_T - 1f,
			ICON_T = 3f;
	/** The hand's beats on the way up: at the button (hovering), pressed, backed off, on the dial. */
	static final float UP_HOVER = PRESS_T - 1.5f, UP_AWAY = PRESS_T + 1.5f, UP_DIAL = Omnitrix.CORE_UP_TICKS + 0.5f;
	/** On the way down: at the button, pressed (the core sinks from here), off screen, the wrist down. */
	static final float DN_HOVER = 2.5f, DN_PRESS = 3.5f, DN_OFF = 8.5f, DN_RAISE_AT = 7f, DN_RAISE_T = 5f;

	/** Hand pose ids (see {@link #pose}). */
	static final int OFF = 0, HOVER = 1, PRESS = 2, AWAY = 3, DIAL = 4, SLAP_UP = 5, SLAP_DOWN = 6;

	/**
	 * The rig at one moment. {@code hand}: the right fingertips' point (px), the fingers' direction and the palm's normal, all in
	 * the left arm's (the watch's) space -- +x out of the face, +y down the arm, +z across (the button is on -z).
	 */
	public static final class Rig {
		public float raise, lift, morph, icon;
		/** How far the vanilla first-person main-hand arm / item is pushed out of view (0..1). */
		public float mainHide;
		/** Third person: how much the right hand is at the button (0..1, a little over 1 while pressing). */
		public float press3p;
		public float[] hand;

		Rig copy() {
			Rig r = new Rig();
			r.raise = raise;
			r.lift = lift;
			r.morph = morph;
			r.icon = icon;
			r.mainHide = mainHide;
			r.press3p = press3p;
			r.hand = hand == null ? null : hand.clone();
			return r;
		}
	}

	private static final class Mem {
		long selectStart = Long.MIN_VALUE;
		boolean selecting;
		Rig last = new Rig();
		Rig snap = new Rig();
		/** Whether the up-sequence had got past the button press when R lowered it. */
		boolean pressed;
	}

	private static final Map<Player, Mem> MEM = new WeakHashMap<>();
	private static final Rig NONE = new Rig();

	/** Ticks since the slap began ({@code 9999} when none is playing). */
	public static float slapClock(Player p, OmnitrixState s, float partial) {
		// only a slap during this raise (the grant plays ANIM_SLAP too, the watch slamming onto the wrist)
		return slapping(s) ? p.level().getGameTime() + partial - s.animStart : 9999f;
	}

	static boolean slapping(OmnitrixState s) {
		return s.animId == Omnitrix.ANIM_SLAP && s.selecting && s.animStart > s.selectStart;
	}

	private OmnitrixHands() {
	}

	static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	/** The pop: 0 -> a little over 1 -> 1. */
	static float popEase(float x) {
		if (x <= 0f) {
			return 0f;
		}
		x = Mth.clamp(x, 0f, 1f);
		float c1 = 1.6f, c3 = c1 + 1f;
		return 1f + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
	}

	/** The rig of {@code p} now (zero when the watch is down or the wearer is an alien). */
	public static Rig rig(Player p, OmnitrixState s, float partial, float L) {
		if (!s.hasPower || s.transformed()) {
			Mem m = MEM.get(p);
			if (m != null) {
				m.last = new Rig();
				m.snap = new Rig();
				m.selectStart = s.selectStart;
				m.selecting = s.selecting;
			}
			return NONE;
		}
		Mem m = MEM.computeIfAbsent(p, k -> new Mem());
		if (m.selectStart != s.selectStart || m.selecting != s.selecting) {
			if (m.selectStart != Long.MIN_VALUE) {
				m.snap = m.last.copy();
				m.pressed = m.selecting && !s.selecting && m.last.lift > 0.05f;
			}
			m.selectStart = s.selectStart;
			m.selecting = s.selecting;
		}
		if (!s.selecting && s.selectStart == 0) {
			return NONE;
		}
		float t = p.level().getGameTime() + partial - s.selectStart;
		Rig r = new Rig();
		Rig q = m.snap;
		float[] from = q.hand != null ? q.hand : pose(OFF, L, 0f, 0f);
		if (s.selecting) {
			r.raise = q.raise + (1f - q.raise) * smooth(t / RAISE_T);
			r.lift = Math.max(popEase((t - PRESS_T) / POP_T), q.lift * (1f - smooth(t / 3f)));
			float lp = OmnitrixWatch.LIFT * r.lift;
			r.morph = Math.max(smooth((t - MORPH_AT) / MORPH_T), q.morph * (1f - smooth(t / 3f)));
			r.icon = Math.max(Mth.clamp((t - ICON_AT) / ICON_T, 0f, 1f), q.icon * (1f - Mth.clamp(t / 1.5f, 0f, 1f)));
			r.mainHide = Math.max(q.mainHide, smooth(t / 2.5f));
			float twist = OmnitrixWatch.handTwist(p);
			float[] hand;
			if (t < UP_HOVER) {
				hand = lerp(from, pose(HOVER, L, lp, twist), smooth(t / UP_HOVER));
			} else if (t < PRESS_T) {
				hand = lerp(pose(HOVER, L, lp, twist), pose(PRESS, L, lp, twist), smooth((t - UP_HOVER) / (PRESS_T - UP_HOVER)));
			} else if (t < UP_AWAY) {
				hand = lerp(pose(PRESS, L, lp, twist), pose(AWAY, L, lp, twist), smooth((t - PRESS_T) / (UP_AWAY - PRESS_T)));
			} else {
				hand = lerp(pose(AWAY, L, lp, twist), pose(DIAL, L, lp, twist), smooth((t - UP_AWAY) / (UP_DIAL - UP_AWAY)));
			}
			// the slap: up off the dial, then the palm slams the core down
			if (slapping(s)) {
				float k = slapClock(p, s, partial);
				hand = k < SLAP_HIT - 0.8f ? lerp(hand, pose(SLAP_UP, L, lp, 0f), smooth(k / (SLAP_HIT - 0.8f)))
						: lerp(pose(SLAP_UP, L, lp, 0f), pose(SLAP_DOWN, L, slapLift(lp, k), 0f), smooth((k - (SLAP_HIT - 0.8f)) / 0.8f));
			}
			r.hand = hand;
			// third person: the hand to the button, a push on the press, and back onto the dial
			r.press3p = smooth((t - 2.5f) / (UP_HOVER - 2.5f)) * (1f - smooth((t - PRESS_T - 0.8f) / 2.7f))
					+ 0.6f * Mth.sin(Mth.clamp((t - UP_HOVER) / (UP_AWAY - UP_HOVER), 0f, 1f) * Mth.PI);
			r.press3p = Math.max(r.press3p, q.press3p * (1f - smooth(t / 3f)));
		} else if (m.pressed) {
			r.raise = q.raise * (1f - smooth((t - DN_RAISE_AT) / DN_RAISE_T));
			r.lift = q.lift * (1f - smooth((t - DN_PRESS) / 3f));
			float lp = OmnitrixWatch.LIFT * r.lift;
			r.morph = q.morph * (1f - smooth((t - DN_PRESS) / 3f));
			r.icon = q.icon * (1f - Mth.clamp((t - DN_PRESS) / 1.5f, 0f, 1f));
			r.mainHide = q.mainHide * (1f - smooth((t - DN_OFF + 0.5f) / 3.5f));
			if (t < DN_HOVER) {
				r.hand = lerp(from, pose(HOVER, L, lp, 0f), smooth(t / DN_HOVER));
			} else if (t < DN_PRESS) {
				r.hand = lerp(pose(HOVER, L, lp, 0f), pose(PRESS, L, lp, 0f), smooth((t - DN_HOVER) / (DN_PRESS - DN_HOVER)));
			} else {
				r.hand = lerp(pose(PRESS, L, lp, 0f), pose(OFF, L, lp, 0f), smooth((t - DN_PRESS) / (DN_OFF - DN_PRESS)));
			}
			float in = q.press3p + (1f - q.press3p) * smooth(t / DN_HOVER);
			r.press3p = in * (1f - smooth((t - DN_PRESS - 0.5f) / 2.5f))
					+ 0.6f * Mth.sin(Mth.clamp((t - DN_HOVER) / (DN_PRESS + 1f - DN_HOVER), 0f, 1f) * Mth.PI);
		} else {
			// lowered before the button was pressed: the hand just goes, the wrist comes down
			r.raise = q.raise * (1f - smooth((t - 1f) / 5f));
			r.lift = q.lift * (1f - smooth(t / 3f));
			float lp = OmnitrixWatch.LIFT * r.lift;
			r.morph = q.morph * (1f - smooth(t / 3f));
			r.icon = q.icon * (1f - Mth.clamp(t / 1.5f, 0f, 1f));
			r.mainHide = q.mainHide * (1f - smooth((t - 4f) / 3.5f));
			r.hand = lerp(from, pose(OFF, L, lp, 0f), smooth(t / 4f));
			r.press3p = q.press3p * (1f - smooth(t / 3f));
		}
		m.last = r;
		return r;
	}

	/** When the palm lands on the core after the click (ticks); the core is slammed in from here. */
	public static final float SLAP_HIT = 2f;

	/** The core's lift under the slap {@code k} ticks after the click. */
	public static float slapLift(float lift, float k) {
		return k < Omnitrix.SLAP_TICKS + 2 ? Mth.lerp(Mth.clamp((k - (SLAP_HIT - 0.6f)) / 0.6f, 0f, 1f), lift, -0.12f) : lift;
	}

	private static float[] lerp(float[] a, float[] b, float k) {
		float[] o = new float[9];
		for (int i = 0; i < 9; i++) {
			o[i] = a[i] + (b[i] - a[i]) * k;
		}
		return o;
	}

	/**
	 * A named hand pose for an arm of outer x {@code L} with the core lifted {@code lift}: {tip x,y,z, fingers x,y,z, palm x,y,z}
	 * in the watch's space (px). The dial grip turns with the ring by {@code twist} degrees (about the face's axis).
	 */
	static float[] pose(int id, float L, float lift, float twist) {
		float cy = OmnitrixModel.CY;
		float bx = (L - 1f) * 0.5f, bz = -2.95f; // the button's lens
		float bezelTop = OmnitrixModel.cuffTop(L) + 0.4f + Math.max(0f, lift);
		float top = OmnitrixModel.coreTop(L, lift);
		float[] o;
		switch (id) {
			case HOVER -> o = new float[] { bx + 0.9f, cy + 0.9f, bz - 1.5f, -0.3f, -0.35f, 1f, -1f, 0f, 0f };
			case PRESS -> o = new float[] { bx + 0.45f, cy + 0.45f, bz - 0.25f, -0.3f, -0.35f, 1f, -1f, 0f, 0f };
			case AWAY -> o = new float[] { bx + 2.6f, cy + 2.2f, bz - 2.2f, -0.45f, -0.5f, 0.85f, -1f, 0f, 0f };
			case DIAL -> {
				// the fingertips against the bezel's rim at its lower right, the fingers pointing in toward the centre, the palm level with
				// the face -- the hand stays off the face so the icon shows
				float a = (float) Math.toRadians(-38f + twist);
				float ry = (float) Math.cos(a), rz = (float) Math.sin(a);
				float rim = 2.3f;
				o = new float[] { bezelTop - 0.5f, cy + ry * rim, rz * rim, -0.12f, -ry, -rz, -1f, 0.25f * ry, 0.25f * rz };
			}
			case SLAP_UP -> o = new float[] { top + 4.2f, cy - 1.6f, 1.4f, -0.2f, -0.6f, 0.75f, -1f, 0f, 0f };
			case SLAP_DOWN -> o = new float[] { top + 2.05f, cy - 1.6f, 1.4f, 0f, -0.6f, 0.75f, -1f, 0f, 0f };
			default -> o = new float[] { bx + 9f, cy + 16f, bz - 16f, -0.3f, -0.6f, 0.8f, -1f, 0f, 0f }; // OFF: down-right, out of view
		}
		return o;
	}
}
