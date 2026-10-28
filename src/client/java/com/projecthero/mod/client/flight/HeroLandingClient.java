package com.projecthero.mod.client.flight;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.client.quirk.QuirkFirstPersonPose;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.flight.landing.HeroLandingPayload;
import com.projecthero.mod.flight.landing.HeroLandingStyle;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: the client side of {@code flight.landing.HeroLanding} -- which players are holding a superhero landing (from
 * {@link HeroLandingPayload}), how far into it they are, and the local player's first-person juice: the camera dips toward
 * the ground and nods ({@code mixin.HeroLandingCameraMixin}) and, bare-handed, the planted fist pushes into view
 * ({@link QuirkFirstPersonPose}). The body pose is {@link HeroLandingPose}. Per-entity static state: cleared on disconnect
 * and pruned every second.
 */
public final class HeroLandingClient {
	/** Ticks to slam into the pose. */
	public static final float IN = 2.5f;
	/** Ticks to rise back to standing after the hold ends (or is cut short). */
	public static final float OUT = 5f;

	/** One lander, in client game time. */
	public static final class State {
		public final HeroLandingStyle style;
		public final long start;
		public final float strength;
		/** When the server ended the hold (client game time), or -1 while it holds. */
		long endAt = -1L;

		State(HeroLandingStyle style, long start, float strength) {
			this.style = style;
			this.start = start;
			this.strength = strength;
		}

		/** Ticks since touchdown (fractional). */
		public float age(float partial) {
			Minecraft mc = Minecraft.getInstance();
			return mc.level == null ? 0f : mc.level.getGameTime() - start + partial;
		}

		/** The hold's end in ticks after touchdown (the server's, or the style's own length as a fallback). */
		public float endAge() {
			return endAt >= 0L ? endAt - start : style.holdTicks + 2f;
		}

		/** The pose weight 0..1: slammed in over {@link #IN}, held, eased out over {@link #OUT} after the end. */
		public float weight(float partial) {
			float t = age(partial);
			if (t < 0f) {
				return 0f;
			}
			float in = smooth(t / IN);
			float out = 1f - smooth((t - endAge()) / OUT);
			return Math.min(in, out);
		}

		public boolean finished(float partial) {
			return age(partial) > endAge() + OUT + 1f;
		}
	}

	private static final Map<Integer, State> STATES = new HashMap<>();

	private HeroLandingClient() {
	}

	static float smooth(float f) {
		f = Mth.clamp(f, 0f, 1f);
		return f * f * (3f - 2f * f);
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(HeroLandingPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> accept(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> STATES.clear());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				STATES.clear();
				return;
			}
			if (client.level.getGameTime() % 20 == 0) {
				STATES.entrySet().removeIf(e -> e.getValue().finished(0f) || client.level.getEntity(e.getKey()) == null);
			}
		});
	}

	static void accept(HeroLandingPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		long now = mc.level.getGameTime();
		if (p.style() == 0) {
			State s = STATES.get(p.entityId());
			if (s != null && s.endAt < 0L) {
				s.endAt = Math.max(now, s.start + 2L);
			}
			return;
		}
		HeroLandingStyle style = HeroLandingStyle.byId(p.style());
		if (style != HeroLandingStyle.NONE) {
			STATES.put(p.entityId(), new State(style, now, p.strength()));
		}
	}

	/** The landing {@code e} is in (holding or rising out of), or null. */
	public static @Nullable State get(Entity e) {
		State s = STATES.get(e.getId());
		if (s == null) {
			return null;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		return s.finished(partial) ? null : s;
	}

	/** Test / harness hook: start a landing on the client directly. */
	public static void play(Entity e, HeroLandingStyle style, float strength) {
		accept(new HeroLandingPayload(e.getId(), style.id, strength));
	}

	/**
	 * 0..1: how much of the flight pose (the body lean, the raised arm) is left -- a landing straightens the dive out within its
	 * first tick, and keeps it out until it ends (the flight is over by then).
	 */
	public static float flightPoseFactor(Player p, float partial) {
		State s = get(p);
		if (s == null) {
			return 1f;
		}
		return 1f - smooth(s.age(partial) / 0.6f);
	}

	// ---------------------------------------------------------------- the Silver Surfer's board

	/**
	 * While the Surfer lands, the board stays under his feet; it slides back from under him as he steps off. Returns the
	 * board's backward offset in blocks, or -1 for no board.
	 */
	public static float boardOffset(Player p) {
		State s = get(p);
		if (s == null || s.style != HeroLandingStyle.SILVER_SURFER) {
			return -1f;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float t = s.age(partial);
		float stepFrom = s.endAge() - 3f;
		if (t < stepFrom) {
			return 0f;
		}
		float f = smooth((t - stepFrom) / (OUT + 2f));
		return f >= 0.999f ? -1f : f * 0.9f;
	}

	// ---------------------------------------------------------------- first person

	/** The camera's drop (blocks, positive = down) for the local player this frame. */
	public static float cameraDip(Player p, float partial) {
		State s = get(p);
		if (s == null) {
			return 0f;
		}
		float t = s.age(partial);
		float w = s.weight(partial);
		float base = 0.22f + 0.16f * s.strength;
		// a bounce through the impact: down past the pose and back up a little
		float bounce = t < 5f ? Mth.sin(Mth.clamp(t / 5f, 0f, 1f) * Mth.PI) * 0.12f * (0.5f + s.strength) : 0f;
		return base * w + bounce * Math.min(1f, w * 2f);
	}

	/** The camera's nod (degrees, positive = looking down) this frame. */
	public static float cameraNod(Player p, float partial) {
		State s = get(p);
		if (s == null) {
			return 0f;
		}
		float t = s.age(partial);
		float impact = t < 6f ? Mth.sin(Mth.clamp(t / 6f, 0f, 1f) * Mth.PI) * (4f + 4f * s.strength) : 0f;
		return impact + 2.5f * s.weight(partial);
	}

	/** The fist punched into the ground, pushing into the bottom of the view (13-column first-person clip). */
	private static final float[][] FP_FIST = {
			QuirkFirstPersonPose.f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkFirstPersonPose.f(2.5f, -42, 24, 12, 0.1f, -0.06f, -0.22f, 0, 0, 0, 0, 0, 0),
			QuirkFirstPersonPose.f(5, -36, 22, 10, 0.08f, -0.04f, -0.18f, 0, 0, 0, 0, 0, 0),
			QuirkFirstPersonPose.f(40, -36, 22, 10, 0.08f, -0.04f, -0.18f, 0, 0, 0, 0, 0, 0) };

	/** Bare-handed and landing in a fist style: the first-person arm sample, or null. */
	public static @Nullable QuirkFirstPersonPose.Sample firstPerson(Player p, float partial) {
		State s = get(p);
		if (s == null || (s.style.pose != HeroLandingStyle.Pose.FIST && s.style.pose != HeroLandingStyle.Pose.HAMMER)) {
			return null;
		}
		float t = Math.min(s.age(partial), 39f);
		float[] v = QuirkKeyframes.sampleN(FP_FIST, t, QuirkFirstPersonPose.COLS);
		if (v == null) {
			return null;
		}
		return new QuirkFirstPersonPose.Sample(v, s.weight(partial), false);
	}
}
