package com.projecthero.mod.client.stand;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.2 (user: "make star finger actually make a finger model stretch out and poke something from star platinums hand"): the
 * Star Finger mesh -- a real finger, not a beam. A smooth tube of rings along the stretch, drawn lit with smooth normals: fat at the
 * knuckle, thinning as it stretches, soft swellings and dark creases at its two joints, a rounded tip with a pale nail. It whips a
 * little as it shoots out and straightens as it locks. Index and middle fingers go out together, the middle one a little shorter.
 * Drawn in the frame of the two points it is given (camera-relative view space in {@link StandRender}).
 */
final class StandFinger {
	private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
	private static final int RINGS = 34;
	private static final int SIDES = 12;
	/** Star Platinum's skin, its joint creases and its nails. */
	private static final int SKIN = 0x9C8CF0;
	private static final int SKIN_DARK = 0x5A49B0;
	private static final int NAIL = 0xE9DDF7;

	private StandFinger() {
	}

	/**
	 * Both fingers from {@code base} toward {@code tip}. {@code extend} 0..1: how far out; {@code whip} 0..1: how much it still snakes.
	 * {@code side}: a unit vector across the hand (the fingers sit either side of the line); {@code up}: the bend of the snake.
	 */
	static void draw(MultiBufferSource buffers, Vector3f base, Vector3f tip, Vector3f side, float extend, float whip, int light, int alpha) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE)); // v0.17.3: solid, like the rest of him
		Vector3f s = new Vector3f(side).mul(0.075f);
		tube(vc, new Vector3f(base).add(s), new Vector3f(tip).add(s), extend, whip, 0.092f, light, alpha, 0f);
		Vector3f b2 = new Vector3f(base).sub(s);
		Vector3f t2 = new Vector3f(base).sub(s).lerp(new Vector3f(tip).sub(s), 0.93f);
		tube(vc, b2, t2, extend, whip, 0.086f, light, alpha, 1.9f);
	}

	private static void tube(VertexConsumer vc, Vector3f a, Vector3f b, float extend, float whip, float rootRadius, int light, int alpha, float phase) {
		Vector3f axis = new Vector3f(b).sub(a);
		float full = axis.length();
		if (full < 0.05f) {
			return;
		}
		axis.div(full);
		float len = Math.max(0.05f, full * extend);
		Vector3f u = new Vector3f(axis).cross(Math.abs(axis.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize();
		Vector3f v = new Vector3f(axis).cross(u).normalize();
		// the longer it stretches, the thinner it gets (it never quite turns into a wire)
		float radius = rootRadius * Mth.lerp(Mth.clamp(len / 9f, 0f, 1f), 1f, 0.62f);

		Vector3f[][] pos = new Vector3f[RINGS + 1][SIDES];
		Vector3f[][] nrm = new Vector3f[RINGS + 1][SIDES];
		int[] colour = new int[RINGS + 1];
		Vector3f[] centre = new Vector3f[RINGS + 1];
		float[] rad = new float[RINGS + 1];
		for (int i = 0; i <= RINGS; i++) {
			float k = i / (float) RINGS;
			// the rings bunch up at the tip, where the rounding needs them
			float s = 1f - (1f - k) * (1f - k) * 0.55f - (1f - k) * 0.45f;
			float d = s * len;
			float wave = Mth.sin(s * Mth.PI) * whip * Math.min(0.4f, len * 0.08f);
			centre[i] = new Vector3f(a).add(new Vector3f(axis).mul(d)).add(new Vector3f(u).mul(Mth.sin(s * 7f + phase) * wave))
					.add(new Vector3f(v).mul(Mth.cos(s * 5f + phase) * wave));
			rad[i] = radius * radiusAt(s, d, len);
			colour[i] = colourAt(s, d, len);
		}
		for (int i = 0; i <= RINGS; i++) {
			Vector3f along = new Vector3f(centre[Math.min(RINGS, i + 1)]).sub(centre[Math.max(0, i - 1)]);
			if (along.lengthSquared() < 1.0e-8f) {
				along.set(axis);
			}
			along.normalize();
			Vector3f ru = new Vector3f(along).cross(v).normalize();
			Vector3f rv = new Vector3f(along).cross(ru).normalize();
			// the surface leans with the radius: rounded normals at the swellings and the tip
			float slope = (rad[Math.max(0, i - 1)] - rad[Math.min(RINGS, i + 1)])
					/ Math.max(0.002f, centre[Math.max(0, i - 1)].distance(centre[Math.min(RINGS, i + 1)]));
			for (int j = 0; j < SIDES; j++) {
				float ang = j * Mth.TWO_PI / SIDES;
				Vector3f out = new Vector3f(ru).mul(Mth.cos(ang)).add(new Vector3f(rv).mul(Mth.sin(ang)));
				pos[i][j] = new Vector3f(centre[i]).add(new Vector3f(out).mul(rad[i]));
				nrm[i][j] = new Vector3f(out).add(new Vector3f(along).mul(slope)).normalize();
			}
		}
		for (int i = 0; i < RINGS; i++) {
			for (int j = 0; j < SIDES; j++) {
				int j2 = (j + 1) % SIDES;
				vert(vc, pos[i][j], nrm[i][j], colour[i], alpha, light);
				vert(vc, pos[i + 1][j], nrm[i + 1][j], colour[i + 1], alpha, light);
				vert(vc, pos[i + 1][j2], nrm[i + 1][j2], colour[i + 1], alpha, light);
				vert(vc, pos[i][j2], nrm[i][j2], colour[i], alpha, light);
			}
		}
	}

	/** The profile along the finger (s 0..1 of it, d blocks along): the taper, two joint swellings and the rounded tip. */
	private static float radiusAt(float s, float d, float len) {
		float r = Mth.lerp(s, 1f, 0.72f);
		r *= 1f + 0.13f * bump(s, 0.38f, 0.055f) + 0.1f * bump(s, 0.74f, 0.05f);
		float tipLen = Math.min(0.16f, len * 0.4f);
		float fromTip = len - d;
		if (fromTip < tipLen) {
			float q = 1f - fromTip / tipLen;
			r *= Mth.sqrt(Math.max(0.0005f, 1f - q * q));
		}
		return r;
	}

	private static int colourAt(float s, float d, float len) {
		float crease = Math.max(bump(s, 0.36f, 0.012f), Math.max(bump(s, 0.4f, 0.012f), Math.max(bump(s, 0.72f, 0.01f), bump(s, 0.76f, 0.01f))));
		int c = mix(SKIN, SKIN_DARK, crease * 0.75f);
		float fromTip = len - d;
		float nail = Mth.clamp((0.14f - fromTip) / 0.05f, 0f, 1f);
		return mix(c, NAIL, nail);
	}

	private static float bump(float s, float at, float width) {
		float x = (s - at) / width;
		return Mth.clamp(1f - x * x, 0f, 1f);
	}

	private static int mix(int a, int b, float k) {
		int r = (int) Mth.lerp(k, (a >> 16) & 255, (b >> 16) & 255);
		int g = (int) Mth.lerp(k, (a >> 8) & 255, (b >> 8) & 255);
		int bl = (int) Mth.lerp(k, a & 255, b & 255);
		return (r << 16) | (g << 8) | bl;
	}

	private static void vert(VertexConsumer vc, Vector3f p, Vector3f n, int rgb, int alpha, int light) {
		vc.addVertex(p.x, p.y, p.z, (alpha << 24) | rgb, 0.5f, 0.5f, OverlayTexture.NO_OVERLAY, light, n.x, n.y, n.z);
	}
}
