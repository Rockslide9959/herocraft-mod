package com.projecthero.mod.client.beastboy.model;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.Util;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy: everything a body animates from, read off the player once per frame (or set directly by a preview).
 */
public final class BeastPose {
	/** Walk cycle position (vanilla limb swing) and amount 0..1. */
	public float walkPos, walkAmt;
	/** Ticks, for breathing / tail sway / flapping. */
	public float age;
	/** Head turn relative to the body (radians, + = toward +X / left) and look pitch (radians, + = down). */
	public float headYaw, headPitch;
	public boolean inWater, airborne;
	/** Vertical speed (blocks/tick), for gliding vs. climbing. */
	public float vy;
	public String move;
	public float prog;
	/**
	 * Fix round: 0..1 how far into the sit pose (wolf: crouch to sit), eased over about a third of a second; and the tail's lag (radians,
	 * a damped swing against the body's turns). Set by {@link #of} from the per-player smoothing below, or directly by a preview.
	 */
	public float sit, tailLag;
	/**
	 * Hq round: horizontal speed (blocks a tick, from the position change; picks walk / trot / gallop), the vanilla attack swing 0..1
	 * (left click) and the entity id (for per-player smoothing; -1 in previews).
	 */
	public float speed, attack;
	public int entityId = -1;

	/** Per-player smoothing (entity id -> {sit, tail lag, last body yaw, last time s}); client render thread only. */
	private static final Map<Integer, float[]> SMOOTH = new HashMap<>();
	private static final long T0 = Util.getNanos();

	/** Drops the smoothing state (leaving a world). */
	public static void clearSmoothing() {
		SMOOTH.clear();
		MotionState.clear();
	}

	public static BeastPose of(AbstractClientPlayer player, float pt, String move, float prog) {
		BeastPose s = new BeastPose();
		s.entityId = player.getId();
		s.walkPos = player.walkAnimation.position(pt);
		s.walkAmt = Mth.clamp(player.walkAnimation.speed(pt), 0f, 1f);
		s.age = player.tickCount + pt;
		float body = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot);
		float head = Mth.rotLerp(pt, player.yHeadRotO, player.yHeadRot);
		// vanilla yaw grows clockwise seen from above (toward -X when facing +Z), so a positive yaw difference turns toward -X
		s.headYaw = -Mth.wrapDegrees(head - body) * Mth.DEG_TO_RAD;
		s.headPitch = player.getViewXRot(pt) * Mth.DEG_TO_RAD;
		s.inWater = player.isInWater() || player.isUnderWater();
		s.airborne = !player.onGround() && !s.inWater;
		s.vy = (float) player.getDeltaMovement().y;
		s.move = move;
		s.prog = Mth.clamp(prog, 0f, 1f);
		s.speed = (float) Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
		s.attack = player.getAttackAnim(pt);
		s.entityId = player.getId();
		smooth(s, player, body);
		return s;
	}

	/** Eases {@link #sit} toward crouching-and-still, and swings the tail lag against body turns, both frame-rate independent. */
	private static void smooth(BeastPose s, AbstractClientPlayer player, float bodyYaw) {
		if (SMOOTH.size() > 256) {
			SMOOTH.clear();
		}
		float now = (Util.getNanos() - T0) / 1.0e9f;
		float[] st = SMOOTH.computeIfAbsent(player.getId(), k -> new float[] { 0f, 0f, bodyYaw, now });
		float dt = Mth.clamp(now - st[3], 0f, 0.1f);
		st[3] = now;
		// sit while crouching on the ground; walking while crouched stands him back up
		float target = player.isShiftKeyDown() && player.onGround() ? Mth.clamp(1f - s.walkAmt * 2.5f, 0f, 1f) : 0f;
		st[0] += (target - st[0]) * (1f - (float) Math.exp(-dt * 8f));
		// tail lag: the body turns, the tail trails and settles (clamped, so a snap turn can't fling it)
		float turn = Mth.wrapDegrees(bodyYaw - st[2]) * Mth.DEG_TO_RAD;
		st[2] = bodyYaw;
		st[1] = Mth.clamp((st[1] + turn * 0.6f) * (float) Math.exp(-dt * 3.5f), -0.45f, 0.45f);
		s.sit = st[0];
		s.tailLag = st[1];
	}

	public boolean is(String m) {
		return move != null && move.equals(m);
	}

	public boolean isAny(String... ms) {
		if (move == null) {
			return false;
		}
		for (String m : ms) {
			if (move.equals(m)) {
				return true;
			}
		}
		return false;
	}

	/** 0..1 envelope of move {@code m}: rises over the first {@code in}, holds, falls over the last {@code out} (0 when not playing). */
	public float env(String m, float in, float out) {
		if (!is(m)) {
			return 0f;
		}
		return envelope(prog, in, out);
	}

	public static float envelope(float p, float in, float out) {
		float up = in <= 0f ? 1f : Mth.clamp(p / in, 0f, 1f);
		float down = out <= 0f ? 1f : Mth.clamp((1f - p) / out, 0f, 1f);
		return smooth(Math.min(up, down));
	}

	public static float smooth(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** Head yaw / pitch clamped to {@code maxYaw} / {@code maxPitch} radians. */
	public float yaw(float maxYaw) {
		return Mth.clamp(headYaw, -maxYaw, maxYaw);
	}

	public float pitch(float maxPitch) {
		return Mth.clamp(headPitch, -maxPitch, maxPitch);
	}

	/** Morph scale and flash for morph_in / morph_out: {scale, flash}. */
	public float[] morph() {
		if (is("morph_in")) {
			float p = prog;
			float s = p < 0.7f ? Mth.lerp(smooth(p / 0.7f), 0.3f, 1.08f) : Mth.lerp(smooth((p - 0.7f) / 0.3f), 1.08f, 1f);
			return new float[] { s, 1f - smooth(p) };
		}
		if (is("morph_out")) {
			float p = prog;
			return new float[] { Mth.lerp(smooth(p), 1f, 0.3f), smooth(p) };
		}
		return new float[] { 1f, 0f };
	}
}
