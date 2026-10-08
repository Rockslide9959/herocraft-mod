package com.projecthero.mod.client.flight;

import java.util.ArrayDeque;
import java.util.Iterator;

import com.projecthero.mod.client.greenlantern.HardLightRibbon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.15: a streamer of light behind a flier -- tapered camera-facing ribbons (a soft sheet, a fainter core), two thin
 * strands twisting round it and an additive glow, each point fading over {@link #LIFE} ticks. Built for Green Lantern's
 * sprint flight ({@code GreenLanternFlightFx}) and shared with Nova (user: "give the same trail to Nova please but just
 * give it his colours"); a {@link Palette} sets the colours, a strength scales every alpha.
 */
public final class LightTrail {
	/** How long a sampled point lives, ticks. */
	public static final int LIFE = 16;

	/** sheet = the soft ribbon, core = the narrow centre line, strand = the two twisting threads, glow = the additive halo. */
	public record Palette(int sheet, int core, int strand, int glow) {
	}

	public static final Palette GREEN_LANTERN = new Palette(0x35F075, 0xF0FFF4, 0xB8FFCC, 0x35F075);
	/** Nova: gold ribbons, a pale warm-gold core, his cyan in the strands. */
	public static final Palette NOVA = new Palette(0xF2C230, 0xFFEBB0, 0x8BF8FF, 0xF2C230);

	private LightTrail() {
	}

	/** Sampled points of one flier's path, oldest first. */
	public static final class History {
		private record Point(Vec3 pos, long tick) {
		}

		private final ArrayDeque<Point> points = new ArrayDeque<>();

		/** Adds {@code at} (unless it hasn't moved), drops points older than {@link #LIFE} and clears on a teleport. */
		public void sample(Vec3 at, long now) {
			Point last = points.peekLast();
			if (last != null && last.pos.distanceToSqr(at) > 64.0 * 64.0) {
				points.clear();
			}
			if (last == null || last.pos.distanceToSqr(at) > 0.0004) {
				points.addLast(new Point(at, now));
			}
			prune(now);
		}

		public void prune(long now) {
			while (!points.isEmpty() && now - points.peekFirst().tick > LIFE) {
				points.removeFirst();
			}
		}

		public boolean isEmpty() {
			return points.isEmpty();
		}

		public int size() {
			return points.size();
		}

		/** Draws this history (newest first, with {@code head} -- the live interpolated emitter -- in front if non-null). */
		public void draw(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 head, float partial, long now, float time,
				Palette palette, float strength) {
			// with a live head, the newest sample (this tick's end) lies AHEAD of the interpolated head -- skip it, or the
			// trail folds back on itself into a blunt end at the feet
			int skip = head != null && !points.isEmpty() ? 1 : 0;
			int n = points.size() - skip + (head != null ? 1 : 0);
			if (n < 2) {
				return;
			}
			Vec3[] pts = new Vec3[n];
			float[] age = new float[n];
			int i = 0;
			if (head != null) {
				pts[i] = head;
				age[i++] = 0f;
			}
			Iterator<Point> it = points.descendingIterator();
			if (skip == 1) {
				it.next();
			}
			while (it.hasNext()) {
				Point pt = it.next();
				pts[i] = pt.pos;
				age[i++] = Mth.clamp((now - pt.tick + partial) / LIFE, 0f, 1f);
			}
			LightTrail.draw(pose, buffers, cam, pts, age, time, palette, strength);
		}
	}

	/**
	 * Draws a trail through {@code pts} (newest first, world space) with per-point {@code age} 0 (fresh) .. 1 (gone).
	 * {@code pose} is the world-render stack (camera-relative); {@code strength} scales every alpha (1 = Green Lantern's
	 * sprint trail).
	 */
	public static void draw(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3[] pts, float[] age, float time, Palette c,
			float strength) {
		if (pts.length < 2) {
			return;
		}
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose last = pose.last();
		VertexConsumer vc = HardLightRibbon.translucent(buffers);
		ribbon(vc, last, cam, pts, age, 0.34f, c.sheet(), 0.2f * strength, 1.0f);
		ribbon(vc, last, cam, pts, age, 0.1f, c.core(), 0.28f * strength, 1.6f);
		strands(vc, last, cam, pts, age, time, c.strand(), strength);
		VertexConsumer add = HardLightRibbon.additive(buffers);
		ribbon(add, last, cam, pts, age, 0.6f, c.glow(), 0.14f * strength, 1.2f);
		pose.popPose();
	}

	/**
	 * A camera-facing ribbon through {@code pts}, {@code width} wide at its head, tapering and fading with age
	 * ({@code fadePow} shapes the fade).
	 */
	private static void ribbon(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, Vec3[] pts, float[] age, float width, int rgb,
			float alpha, float fadePow) {
		Vec3 prevL = null;
		Vec3 prevR = null;
		float prevA = 0f;
		for (int i = 0; i < pts.length; i++) {
			Vec3 dir = i + 1 < pts.length ? pts[i].subtract(pts[i + 1]) : pts[i - 1].subtract(pts[i]);
			if (dir.lengthSqr() < 1.0e-8) {
				dir = new Vec3(0, 1, 0);
			}
			Vec3 side = dir.cross(cam.subtract(pts[i]));
			side = side.lengthSqr() < 1.0e-8 ? new Vec3(1, 0, 0) : side.normalize();
			float life = 1f - age[i];
			// taper: a fine point at the very head (it leaves the body), widest just behind, thinning to the tail
			float head = Math.min(1f, (i + 0.35f) / 2.2f);
			float w = width * head * (float) Math.pow(life, 0.7);
			float a = alpha * (float) Math.pow(life, fadePow);
			Vec3 l = pts[i].add(side.scale(w));
			Vec3 r = pts[i].subtract(side.scale(w));
			if (prevL != null) {
				HardLightRibbon.quad(vc, pose, (float) prevL.x, (float) prevL.y, (float) prevL.z, (float) l.x, (float) l.y, (float) l.z,
						(float) r.x, (float) r.y, (float) r.z, (float) prevR.x, (float) prevR.y, (float) prevR.z, rgb, prevA, a, a, prevA);
			}
			prevL = l;
			prevR = r;
			prevA = a;
		}
	}

	/** Two thin strands of light twisting round the trail. */
	private static void strands(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, Vec3[] pts, float[] age, float time, int rgb,
			float strength) {
		for (int s = 0; s < 2; s++) {
			Vec3[] q = new Vec3[pts.length];
			for (int i = 0; i < pts.length; i++) {
				Vec3 dir = i + 1 < pts.length ? pts[i].subtract(pts[i + 1]) : pts[i - 1].subtract(pts[i]);
				dir = dir.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : dir.normalize();
				Vec3 a = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
				Vec3 u = dir.cross(a).normalize();
				Vec3 v = dir.cross(u).normalize();
				float phase = i * 0.55f - time * 0.5f + s * Mth.PI;
				float r = 0.26f * Math.min(1f, (i + 0.35f) / 2.2f) * (1f - age[i] * 0.5f);
				q[i] = pts[i].add(u.scale(Mth.cos(phase) * r)).add(v.scale(Mth.sin(phase) * r));
			}
			ribbon(vc, pose, cam, q, age, 0.035f, rgb, 0.38f * strength, 1.3f);
		}
	}
}
