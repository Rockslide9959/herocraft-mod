package com.projecthero.mod.client.beastboy.model;

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

	public static BeastPose of(AbstractClientPlayer player, float pt, String move, float prog) {
		BeastPose s = new BeastPose();
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
		return s;
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
