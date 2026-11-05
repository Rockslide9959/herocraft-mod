package com.projecthero.mod.client.omnitrix.alien;

import java.util.Map;
import java.util.WeakHashMap;

import com.projecthero.mod.omnitrix.alien.buzzshock.BuzzshockKit;
import com.projecthero.mod.omnitrix.alien.feedback.FeedbackKit;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/**
 * v0.17.2 round 3: the whole-body attitude of the two held Shift+X flights (Buzzshock's Spark Flight, Feedback's Surge Flight). The body
 * lies out along where the flier aims -- flat flying level, feet down climbing straight up, head first diving -- and banks into turns,
 * eased in and out so take-off and landing are one smooth motion ({@code StinkflyFlightPoseMixin} applies it). The kits' client ticks
 * feed it; every viewer does it from the synced flight flag.
 */
public final class ThrustFlightPose {
	private static final class State {
		float on, onO, lean, leanO, bank, bankO, lastYaw;
		boolean seen;
	}

	private static final Map<AbstractClientPlayer, State> STATES = new WeakHashMap<>();

	private ThrustFlightPose() {
	}

	/** One client tick for {@code p}. */
	public static void tick(AbstractClientPlayer p, boolean flying) {
		if (!flying && !STATES.containsKey(p)) {
			return;
		}
		State s = STATES.computeIfAbsent(p, k -> new State());
		s.onO = s.on;
		s.leanO = s.lean;
		s.bankO = s.bank;
		s.on = Mth.approach(s.on, flying ? 1f : 0f, flying ? 0.2f : 0.14f);
		float yaw = p.getYRot();
		float turn = s.seen ? Mth.wrapDegrees(yaw - s.lastYaw) : 0f;
		s.lastYaw = yaw;
		s.seen = true;
		s.lean += (Mth.clamp(90f + p.getXRot() * 0.95f, 8f, 172f) - s.lean) * 0.32f;
		s.bank += (Mth.clamp(-turn * 2.4f, -38f, 38f) - s.bank) * 0.22f;
		if (!flying && s.on <= 0f && s.onO <= 0f) {
			STATES.remove(p);
		}
	}

	private static float smooth(float t) {
		return t * t * (3f - 2f * t);
	}

	/** How far into the flying attitude {@code p} is, 0..1. */
	public static float amount(AbstractClientPlayer p, float partial) {
		State s = STATES.get(p);
		return s == null ? 0f : smooth(Mth.lerp(partial, s.onO, s.on));
	}

	/** The tilt for {@code StinkflyFlightPoseMixin}: {pitch forward, roll} in degrees and a bob (blocks), or null. */
	public static float[] bodyTilt(AbstractClientPlayer p, float partial) {
		State s = STATES.get(p);
		if (s == null || !(BuzzshockKit.isBuzzshock(p) || FeedbackKit.isFeedback(p))) {
			return null;
		}
		float k = smooth(Mth.lerp(partial, s.onO, s.on));
		if (k < 0.02f) {
			return null;
		}
		return new float[] { Mth.lerp(partial, s.leanO, s.lean) * k, Mth.lerp(partial, s.bankO, s.bank) * k, 0f };
	}

	/** The head stays on the aim against the body's pitch, so he looks where he flies. */
	public static void headOnAim(AbstractClientPlayer p, HumanoidModel<?> m, float partial) {
		State s = STATES.get(p);
		if (s == null) {
			return;
		}
		float lean = Mth.lerp(partial, s.leanO, s.lean) * smooth(Mth.lerp(partial, s.onO, s.on));
		m.head.xRot = Math.max(-1.35f, m.head.xRot - Math.min(lean, 100f) * Mth.DEG_TO_RAD * 0.85f);
		m.hat.copyFrom(m.head);
	}
}
