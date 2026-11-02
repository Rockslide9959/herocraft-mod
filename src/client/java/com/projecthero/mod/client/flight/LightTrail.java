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

	/**
	 * v0.17.2 (XLR8 revamp, user: "add xlr8s speed trail to his speed mode please, the blue and black lines"): a banded line --
	 * a {@code band} colour with a {@code core} down its middle and thin {@code edge} lines on both sides. {@code glow} &gt; 0 adds
	 * an additive halo over the core only (additive black is invisible, so the dark edges always go through the normal
	 * translucent blend).
	 */
	public record Banded(int band, int core, int edge, float glow) {
	}

	/** XLR8's speed lines: his blue band, a pale-blue core, near-black edges. */
	public static final Banded XLR8 = new Banded(0x3A7BD5, 0x9FD8FF, 0x0A0E14, 0f);
	/** XLR8 in Overdrive: the core runs white-hot (with a little glow on it). */
	public static final Banded XLR8_OVERDRIVE = new Banded(0x3A7BD5, 0xFFFFFF, 0x0A0E14, 0.35f);

	private LightTrail() {
	}

	/**
	 * v0.17.2 (XLR8 revamp): draws a banded line through {@code pts} (newest first, world space; per-point {@code age} 0..1),
	 * {@code width} half-wide at its head, tapering and fading with age. The core, the two band strips and the two edge lines
	 * are side by side (never stacked), so the translucent sort cannot muddy them; the dark edges are never additive.
	 */
	public static void drawBanded(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3[] pts, float[] age, Banded s, float width,
			float strength) {
		if (pts.length < 2) {
			return;
		}
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose last = pose.last();
		VertexConsumer vc = HardLightRibbon.translucent(buffers);
		band(vc, last, cam, pts, age, width, -0.22f, 0.22f, s.core(), 0.9f * strength, 1.3f);
		band(vc, last, cam, pts, age, width, 0.22f, 0.74f, s.band(), 0.82f * strength, 1.0f);
		band(vc, last, cam, pts, age, width, -0.74f, -0.22f, s.band(), 0.82f * strength, 1.0f);
		band(vc, last, cam, pts, age, width, 0.74f, 1.0f, s.edge(), 0.9f * strength, 0.9f);
		band(vc, last, cam, pts, age, width, -1.0f, -0.74f, s.edge(), 0.9f * strength, 0.9f);
		if (s.glow() > 0f) {
			band(HardLightRibbon.additive(buffers), last, cam, pts, age, width, -0.3f, 0.3f, s.core(), s.glow() * strength, 1.4f);
		}
		pose.popPose();
	}

	/**
	 * A camera-facing strip through {@code pts} between {@code inner} and {@code outer} (fractions of the tapered half-width
	 * {@code width}, negative = the other side), fading with age.
	 */
	private static void band(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, Vec3[] pts, float[] age, float width, float inner, float outer,
			int rgb, float alpha, float fadePow) {
		Vec3 prevA = null;
		Vec3 prevB = null;
		float prevAl = 0f;
		for (int i = 0; i < pts.length; i++) {
			Vec3 dir = i + 1 < pts.length ? pts[i].subtract(pts[i + 1]) : pts[i - 1].subtract(pts[i]);
			if (dir.lengthSqr() < 1.0e-8) {
				dir = new Vec3(0, 1, 0);
			}
			Vec3 side = dir.cross(cam.subtract(pts[i]));
			side = side.lengthSqr() < 1.0e-8 ? new Vec3(1, 0, 0) : side.normalize();
			float life = 1f - age[i];
			float head = Math.min(1f, (i + 0.5f) / 1.6f);
			float w = width * head * (float) Math.pow(life, 0.5);
			float al = alpha * (float) Math.pow(life, fadePow);
			Vec3 a = pts[i].add(side.scale(w * inner));
			Vec3 b = pts[i].add(side.scale(w * outer));
			if (prevA != null) {
				HardLightRibbon.quad(vc, pose, (float) prevA.x, (float) prevA.y, (float) prevA.z, (float) a.x, (float) a.y, (float) a.z,
						(float) b.x, (float) b.y, (float) b.z, (float) prevB.x, (float) prevB.y, (float) prevB.z, rgb, prevAl, al, al, prevAl);
			}
			prevA = a;
			prevB = b;
			prevAl = al;
		}
	}

	/** Sampled points of one flier's path, oldest first. */
	public static final class History {
		private record Point(Vec3 pos, long tick) {
		}

		private final ArrayDeque<Point> points = new ArrayDeque<>();
		/** v0.17.2 (XLR8 revamp): how long this history's points live (XLR8's Overdrive lines last longer). */
		private int life = LIFE;

		public History() {
		}

		/** v0.17.2 (XLR8 revamp): a history whose points live {@code life} ticks. */
		public History(int life) {
			this.life = life;
		}

		/** v0.17.2 (XLR8 revamp): changes how long the points live from now on. */
		public void life(int ticks) {
			this.life = Math.max(2, ticks);
		}

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
			while (!points.isEmpty() && now - points.peekFirst().tick > life) {
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
			Vec3[] pts = points(head);
			if (pts == null) {
				return;
			}
			LightTrail.draw(pose, buffers, cam, pts, ages(head, partial, now), time, palette, strength);
		}

		/**
		 * v0.17.2 (XLR8 revamp): draws this history as a {@link #drawBanded banded} line -- a coloured band with thin dark
		 * edges (newest first, with {@code head} in front if non-null).
		 */
		public void drawBanded(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 head, float partial, long now, Banded style,
				float width, float strength) {
			Vec3[] pts = points(head);
			if (pts == null) {
				return;
			}
			LightTrail.drawBanded(pose, buffers, cam, pts, ages(head, partial, now), style, width, strength);
		}

		/** v0.17.2 (XLR8's sheet trail): the points newest first (the live head in front), or null if there are fewer than two. */
		public Vec3[] pointsNewestFirst(Vec3 head) {
			return points(head);
		}

		/** v0.17.2 (XLR8's sheet trail): each point's age 0..1, matching {@link #pointsNewestFirst}. */
		public float[] agesNewestFirst(Vec3 head, float partial, long now) {
			return ages(head, partial, now);
		}

		/** The points newest first (the live head in front), or null if there are fewer than two. */
		private Vec3[] points(Vec3 head) {
			// with a live head, the newest sample (this tick's end) lies AHEAD of the interpolated head -- skip it, or the
			// trail folds back on itself into a blunt end at the feet
			int skip = head != null && !points.isEmpty() ? 1 : 0;
			int n = points.size() - skip + (head != null ? 1 : 0);
			if (n < 2) {
				return null;
			}
			Vec3[] pts = new Vec3[n];
			int i = 0;
			if (head != null) {
				pts[i++] = head;
			}
			Iterator<Point> it = points.descendingIterator();
			if (skip == 1) {
				it.next();
			}
			while (it.hasNext()) {
				pts[i++] = it.next().pos;
			}
			return pts;
		}

		private float[] ages(Vec3 head, float partial, long now) {
			int skip = head != null && !points.isEmpty() ? 1 : 0;
			int n = points.size() - skip + (head != null ? 1 : 0);
			float[] age = new float[Math.max(0, n)];
			int i = 0;
			if (head != null) {
				age[i++] = 0f;
			}
			Iterator<Point> it = points.descendingIterator();
			if (skip == 1) {
				it.next();
			}
			while (it.hasNext()) {
				age[i++] = Mth.clamp((now - it.next().tick + partial) / life, 0f, 1f);
			}
			return age;
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
