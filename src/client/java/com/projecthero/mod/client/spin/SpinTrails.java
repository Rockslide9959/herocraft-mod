package com.projecthero.mod.client.spin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.spin.entity.SteelBallEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.17.3 round 3: the Spin's rendered VFX trails (no particles). Everything is drawn into ONE render type, {@link RenderType#lightning()}
 * (position + colour, additive), as camera-facing ribbons that fade to nothing at their edges, so they glow at night without blinding.
 * <ul>
 *   <li>{@link #tick}: each thrown ball's last {@link #MAX_POINTS} positions (per entity id, client only); a ball that is gone keeps its
 *   trail a few ticks while it shrinks away.</li>
 *   <li>{@link #renderBalls}: the ribbon -- smoothed through the points (Catmull-Rom), tapering and fading to the tail -- wound with
 *   {@link #STRANDS} thin helix strands spinning round the path with the ball, so it reads as spin, not a streak. Green-white for a
 *   plain ball, gold for a Golden Spin / Ball Breaker ball.</li>
 *   <li>{@link #spiralLine}: the Golden Spin's line, a bright gold spiral ribbon. {@link #arc}: the Spin Shield's orbit arcs.
 *   {@link #swirl}: the swirl round a spun creature. {@link #shockRing}: the Spin Quake's spinning shock ring.</li>
 * </ul>
 * Static state: {@link #TRAILS} (positions only, keyed by entity id), cleared by {@link #reset} on disconnect.
 */
public final class SpinTrails {
	/** Positions kept per ball (one a tick). */
	static final int MAX_POINTS = 14;
	/** Smoothing samples between two kept positions. */
	private static final int SUB = 3;
	static final int STRANDS = 3;

	private static final class Trail {
		final ArrayDeque<Vec3> points = new ArrayDeque<>();
		boolean golden;
		boolean alive = true;
		float phase;
	}

	private static final Map<Integer, Trail> TRAILS = new ConcurrentHashMap<>();

	private SpinTrails() {
	}

	static void reset() {
		TRAILS.clear();
	}

	/** Once a client tick: record where every thrown ball is; shrink the trails of balls that are gone. */
	static void tick(Minecraft mc) {
		Set<Integer> seen = new HashSet<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof SteelBallEntity ball && !ball.isRemoved()) {
				Trail t = TRAILS.computeIfAbsent(ball.getId(), k -> new Trail());
				t.golden = ball.golden();
				t.alive = true;
				t.points.addFirst(centre(ball, 1f));
				while (t.points.size() > MAX_POINTS) {
					t.points.removeLast();
				}
				seen.add(ball.getId());
			}
		}
		for (Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Trail> en = it.next();
			if (seen.contains(en.getKey())) {
				continue;
			}
			Trail t = en.getValue();
			t.alive = false;
			t.points.pollLast();
			t.points.pollLast();
			if (t.points.size() < 2) {
				it.remove();
			}
		}
	}

	private static Vec3 centre(SteelBallEntity ball, float partial) {
		return ball.getPosition(partial).add(0, ball.getBbHeight() * 0.5, 0);
	}

	// ---------------------------------------------------------------- the thrown balls

	static void renderBalls(Minecraft mc, MultiBufferSource buffers, Matrix4f m, Vec3 cam, float partial, float time) {
		if (TRAILS.isEmpty()) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		for (Map.Entry<Integer, Trail> en : TRAILS.entrySet()) {
			Trail t = en.getValue();
			List<Vec3> pts = new ArrayList<>(t.points.size() + 1);
			Entity e = mc.level.getEntity(en.getKey());
			if (t.alive && e instanceof SteelBallEntity ball) {
				pts.add(centre(ball, partial)); // the head sits on the ball as drawn this frame
				boolean first = true;
				for (Vec3 p : t.points) {
					if (first) {
						first = false; // (this tick's spot is ahead of the drawn ball)
						continue;
					}
					pts.add(p);
				}
			} else {
				pts.addAll(t.points);
			}
			if (pts.size() < 2) {
				continue;
			}
			List<Vec3> s = smooth(pts, cam);
			float[] core = t.golden ? new float[] { 1f, 0.93f, 0.62f } : new float[] { 0.86f, 1f, 0.9f };
			float[] edge = t.golden ? new float[] { 1f, 0.68f, 0.16f } : new float[] { 0.35f, 0.95f, 0.5f };
			float width = t.golden ? 0.2f : 0.15f;
			float alpha = t.alive ? 0.75f : 0.45f;
			ribbon(vc, m, s, width, alpha, edge, core, 1.6f);
			float spin = -time * 0.9f; // the strands turn with the ball
			for (int k = 0; k < STRANDS; k++) {
				helix(vc, m, s, spin + k * Mth.TWO_PI / STRANDS, t.golden ? 0.26f : 0.21f, 2.4f, 0.035f, alpha * 1.2f,
						t.golden ? new float[] { 1f, 0.85f, 0.4f } : new float[] { 0.75f, 1f, 0.82f });
			}
		}
	}

	// ---------------------------------------------------------------- the others

	/** The Golden Spin's line from {@code a} to {@code b} (world), fading with {@code fade} (1 .. 0). */
	static void spiralLine(MultiBufferSource buffers, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float time, float fade, boolean perfect) {
		double len = a.distanceTo(b);
		if (len < 0.1 || fade <= 0f) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		int n = (int) Mth.clamp(len * 2, 4, 90);
		List<Vec3> s = new ArrayList<>(n + 1);
		for (int i = 0; i <= n; i++) {
			s.add(a.lerp(b, i / (double) n).subtract(cam));
		}
		float[] core = { 1f, 0.96f, 0.75f }, edge = { 1f, 0.7f, 0.18f };
		// full width along the line, tapering only at the far end
		ribbon(vc, m, s, perfect ? 0.32f : 0.24f, 0.6f * fade, edge, core, 0.25f);
		for (int k = 0; k < STRANDS; k++) {
			helix(vc, m, s, time * 0.9f + k * Mth.TWO_PI / STRANDS, perfect ? 0.45f : 0.34f, 1.9f, 0.045f, 0.8f * fade,
					new float[] { 1f, 0.86f, 0.38f });
		}
	}

	/** A Spin Shield ball's arc: the circle of {@code radius} round {@code c} (world) behind the ball at {@code ang}. */
	static void arc(MultiBufferSource buffers, Matrix4f m, Vec3 cam, Vec3 c, float radius, float ang, float bobPhase, float time) {
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		List<Vec3> s = new ArrayList<>(16);
		for (int k = 0; k < 16; k++) {
			float a = ang - k * 0.09f;
			float t = time - k * 0.16f;
			float bob = Mth.sin(t * 0.3f + bobPhase) * 0.25f;
			s.add(new Vec3(c.x + Mth.cos(a) * radius, c.y + bob, c.z + Mth.sin(a) * radius).subtract(cam));
		}
		ribbon(vc, m, s, 0.13f, 0.5f, new float[] { 0.35f, 0.95f, 0.5f }, new float[] { 0.9f, 1f, 0.92f }, 1.4f);
		helix(vc, m, s, -time * 0.9f, 0.12f, 6f, 0.025f, 0.5f, new float[] { 1f, 0.86f, 0.4f });
	}

	/** Two gold strands spiralling round a spun creature at {@code c} (its feet, world), turning. */
	static void swirl(MultiBufferSource buffers, Matrix4f m, Vec3 cam, Vec3 c, float height, float radius, float time) {
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		int n = 28;
		for (int k = 0; k < 2; k++) {
			List<Vec3> s = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				float f = i / (float) (n - 1);
				float a = time * 0.42f + f * 1.6f * Mth.TWO_PI + k * Mth.PI;
				float r = radius * (0.85f + 0.15f * Mth.sin(f * Mth.PI));
				s.add(new Vec3(c.x + Mth.cos(a) * r, c.y + height * (0.08f + 0.86f * f), c.z + Mth.sin(a) * r).subtract(cam));
			}
			// both ends fade (sin), so the strand floats rather than starting and stopping hard
			ribbonProfile(vc, m, s, 0.07f, 0.65f, new float[] { 1f, 0.72f, 0.2f }, new float[] { 1f, 0.95f, 0.7f }, true);
		}
	}

	/** The Spin Quake's ring on the ground at {@code c} (world): {@code radius}, spinning arcs and a faint full band. */
	static void shockRing(MultiBufferSource buffers, Matrix4f m, Vec3 cam, Vec3 c, float radius, float thick, float time, float alpha) {
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		Vec3 o = c.subtract(cam);
		int arcs = 6, per = 10;
		float spin = time * 0.35f;
		for (int k = 0; k < arcs; k++) {
			float a0 = spin + k * Mth.TWO_PI / arcs;
			for (int i = 0; i < per; i++) {
				float f0 = i / (float) per, f1 = (i + 1) / (float) per;
				float b0 = a0 + f0 * 0.8f, b1 = a0 + f1 * 0.8f;
				float al0 = alpha * (1f - f0), al1 = alpha * (1f - f1); // each arc fades out behind its leading edge
				flatSeg(vc, m, o, b0, b1, radius - thick, radius, al0, al1, 1f, 0.8f, 0.3f);
			}
		}
		for (int i = 0; i < 40; i++) {
			float b0 = i * Mth.TWO_PI / 40, b1 = (i + 1) * Mth.TWO_PI / 40;
			flatSeg(vc, m, o, b0, b1, radius - thick * 0.4f, radius, alpha * 0.3f, alpha * 0.3f, 1f, 0.9f, 0.55f);
		}
	}

	// ---------------------------------------------------------------- the ribbon maths

	/** Catmull-Rom through {@code pts} (world), {@link #SUB} samples a span; returned camera-relative. */
	private static List<Vec3> smooth(List<Vec3> pts, Vec3 cam) {
		int n = pts.size();
		List<Vec3> out = new ArrayList<>(n * SUB);
		for (int i = 0; i < n - 1; i++) {
			Vec3 p0 = pts.get(Math.max(0, i - 1)), p1 = pts.get(i), p2 = pts.get(i + 1), p3 = pts.get(Math.min(n - 1, i + 2));
			for (int j = 0; j < SUB; j++) {
				double t = j / (double) SUB, t2 = t * t, t3 = t2 * t;
				double x = 0.5 * (2 * p1.x + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3);
				double y = 0.5 * (2 * p1.y + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3);
				double z = 0.5 * (2 * p1.z + (-p0.z + p2.z) * t + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3);
				out.add(new Vec3(x - cam.x, y - cam.y, z - cam.z));
			}
		}
		out.add(pts.get(n - 1).subtract(cam));
		return out;
	}

	private static Vec3 tangent(List<Vec3> s, int j) {
		Vec3 a = s.get(Math.max(0, j - 1)), b = s.get(Math.min(s.size() - 1, j + 1));
		Vec3 t = a.subtract(b);
		return t.lengthSqr() < 1.0e-8 ? new Vec3(0, 0, 1) : t.normalize();
	}

	/** Camera-facing ribbon through {@code s} (camera-relative, head first), tapering by {@code (1-f)^taper} and fading to the tail. */
	private static void ribbon(VertexConsumer vc, Matrix4f m, List<Vec3> s, float width, float alpha, float[] edge, float[] core, float taper) {
		int n = s.size();
		Vec3 prevL = null, prevC = null, prevR = null;
		float prevA = 0f;
		for (int j = 0; j < n; j++) {
			float f = j / (float) (n - 1);
			float w = width * (float) Math.pow(1f - f, taper) + 0.004f;
			float a = alpha * (1f - f) * (1f - f);
			Vec3 p = s.get(j);
			Vec3 side = tangent(s, j).cross(p).normalize().scale(w);
			Vec3 l = p.add(side), r = p.subtract(side);
			if (prevC != null) {
				band(vc, m, prevL, l, p, prevC, 0f, 0f, a, prevA, edge, core);
				band(vc, m, prevC, p, r, prevR, prevA, a, 0f, 0f, core, edge);
			}
			prevL = l;
			prevC = p;
			prevR = r;
			prevA = a;
		}
	}

	/** As {@link #ribbon}, with the width and fade following sin(pi f) (both ends soft) when {@code bothEnds}. */
	private static void ribbonProfile(VertexConsumer vc, Matrix4f m, List<Vec3> s, float width, float alpha, float[] edge, float[] core,
			boolean bothEnds) {
		int n = s.size();
		Vec3 prevL = null, prevC = null, prevR = null;
		float prevA = 0f;
		for (int j = 0; j < n; j++) {
			float f = j / (float) (n - 1);
			float k = bothEnds ? Mth.sin(f * Mth.PI) : 1f - f;
			float w = width * k + 0.004f;
			float a = alpha * k;
			Vec3 p = s.get(j);
			Vec3 side = tangent(s, j).cross(p).normalize().scale(w);
			Vec3 l = p.add(side), r = p.subtract(side);
			if (prevC != null) {
				band(vc, m, prevL, l, p, prevC, 0f, 0f, a, prevA, edge, core);
				band(vc, m, prevC, p, r, prevR, prevA, a, 0f, 0f, core, edge);
			}
			prevL = l;
			prevC = p;
			prevR = r;
			prevA = a;
		}
	}

	/** A thin strand wound round the path {@code s}: {@code turns} radians a block, starting at {@code phase}. */
	private static void helix(VertexConsumer vc, Matrix4f m, List<Vec3> s, float phase, float radius, float twist, float width, float alpha,
			float[] col) {
		int n = s.size();
		List<Vec3> h = new ArrayList<>(n);
		double arc = 0;
		Vec3 ref = new Vec3(0, 1, 0);
		for (int j = 0; j < n; j++) {
			if (j > 0) {
				arc += s.get(j).distanceTo(s.get(j - 1));
			}
			Vec3 t = tangent(s, j);
			Vec3 u = t.cross(Math.abs(t.y) > 0.9 ? new Vec3(1, 0, 0) : ref).normalize();
			Vec3 v = t.cross(u);
			float f = j / (float) (n - 1);
			double ang = phase + arc * twist;
			float r = radius * (1f - 0.45f * f);
			h.add(s.get(j).add(u.scale(Math.cos(ang) * r)).add(v.scale(Math.sin(ang) * r)));
		}
		ribbon(vc, m, h, width, alpha, col, col, 0.6f);
	}

	/** One quad of a ribbon half (both windings), the colour and alpha given per corner pair. */
	private static void band(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float aa, float ab, float ac, float ad,
			float[] colAB, float[] colCD) {
		v(vc, m, a, colAB, aa);
		v(vc, m, b, colAB, ab);
		v(vc, m, c, colCD, ac);
		v(vc, m, d, colCD, ad);
		v(vc, m, d, colCD, ad);
		v(vc, m, c, colCD, ac);
		v(vc, m, b, colAB, ab);
		v(vc, m, a, colAB, aa);
	}

	private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, float[] col, float a) {
		vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(col[0], col[1], col[2], Mth.clamp(a, 0f, 1f));
	}

	/** A flat ring segment on the ground (both windings). */
	private static void flatSeg(VertexConsumer vc, Matrix4f m, Vec3 o, float a0, float a1, float in, float out, float al0, float al1, float r,
			float g, float b) {
		float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
		float x = (float) o.x, y = (float) o.y, z = (float) o.z;
		float[][] q = { { x + c0 * in, z + s0 * in, al0 * 0.2f }, { x + c0 * out, z + s0 * out, al0 }, { x + c1 * out, z + s1 * out, al1 },
				{ x + c1 * in, z + s1 * in, al1 * 0.2f } };
		for (int k = 0; k < 4; k++) {
			vc.addVertex(m, q[k][0], y, q[k][1]).setColor(r, g, b, Mth.clamp(q[k][2], 0f, 1f));
		}
		for (int k = 3; k >= 0; k--) {
			vc.addVertex(m, q[k][0], y, q[k][1]).setColor(r, g, b, Mth.clamp(q[k][2], 0f, 1f));
		}
	}
}
