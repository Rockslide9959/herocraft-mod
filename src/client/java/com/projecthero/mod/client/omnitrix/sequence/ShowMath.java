package com.projecthero.mod.client.omnitrix.sequence;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** v0.17.3: the show sequences' shared easing, envelopes, hashes and colour maths (moved out of the v0.17.2 Four Arms code). */
public final class ShowMath {
	private ShowMath() {
	}

	/** Smoothstep of {@code x} clamped to 0..1. */
	public static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	/** 0 -> 1 with a springy overshoot over {@code len} ticks (a bulge popping up). */
	public static float pop(float t, float len) {
		if (t <= 0f) {
			return 0f;
		}
		float x = Mth.clamp(t / len, 0f, 1f);
		float c1 = 2.4f, c3 = c1 + 1f;
		return 1f + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
	}

	/** Up from {@code a} over {@code in}, held to {@code b}, down over {@code out}: a flash's envelope (0..1). */
	public static float env(float t, float a, float in, float b, float out) {
		if (t < a || t > b + out) {
			return 0f;
		}
		return t < a + in ? smooth((t - a) / in) : t <= b ? 1f : 1f - smooth((t - b) / out);
	}

	/** A deterministic 0..1 from a seed and an index. */
	public static float hash(long seed, int i) {
		long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 40) / (float) (1L << 24);
	}

	/** Blends a part's rotation toward {@code x y z} by {@code w}. */
	public static void rot(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}

	/** The horizontal unit vector a yaw (degrees) faces. */
	public static Vec3 forward(float yawDeg) {
		float rad = yawDeg * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(rad), 0, Mth.cos(rad));
	}

	/** {@code rgb} darkened by {@code k}, opaque (for additive glows). */
	public static int scaleRgb(int rgb, float k) {
		k = Mth.clamp(k, 0f, 1f);
		return 0xFF000000 | (Math.round(((rgb >> 16) & 255) * k) << 16) | (Math.round(((rgb >> 8) & 255) * k) << 8) | Math.round((rgb & 255) * k);
	}

	public static int lerpRgb(float t, int a, int b) {
		t = Mth.clamp(t, 0f, 1f);
		int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
		int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
		int bb = (int) Mth.lerp(t, a & 255, b & 255);
		return (r << 16) | (g << 8) | bb;
	}

	/** A white glow colour of brightness {@code k} (additive layers). */
	public static int grey(float k) {
		int v = Math.round(255 * Mth.clamp(k, 0f, 1f));
		return 0xFF000000 | (v << 16) | (v << 8) | v;
	}
}
