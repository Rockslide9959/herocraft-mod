package com.projecthero.mod.client.shazam;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.shazam.Shazam;
import com.projecthero.mod.shazam.ShazamConfig;
import com.projecthero.mod.shazam.network.ShazamFxPayload;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Shazam effects pass (tester: "looks great, but needs a little work effect-wise"): his lightning as real branching bolt
 * geometry, for every viewer near ({@link ShazamFxPayload}). Bolts are midpoint-displaced with forks, drawn as a white-hot core in
 * a gold halo with {@code RenderType.lightning()} (additive), and re-shaped every two ticks so they flicker like the real thing:
 * <ul>
 *   <li><b>ARC</b>: Lightning Hand and its chain hops.</li>
 *   <li><b>SKY</b>: a bolt out of the sky with forks and a ground flash (Call the Lightning, Zeus Storm, the transform strike,
 *   Eternity Strike), and a sky flash for anyone near.</li>
 *   <li><b>BLUR</b>: Mercury Dash's red blur -- streaks and fading after-images with crackle along the path.</li>
 *   <li><b>CLAP</b>: Thunderclap's shockwave rings racing down the cone, sparking at the rim.</li>
 *   <li><b>BURST</b>: bolts racing out along the ground (Thunder Tackle, the Eternity slam).</li>
 *   <li><b>AURA</b>: Achilles Stand's golden aura (rising rings, a soft shell, arcs crawling over him).</li>
 *   <li><b>CRACK</b>: the landing's glowing lightning cracks across the ground.</li>
 * </ul>
 * Plus {@link #charged}: while the Lightning bar is at 60%+ (or Zeus Storm / Eternity Strike run) small arcs crackle round his
 * hands and his eyes glow (from {@link ShazamSuitLayer}). Cleared on disconnect.
 */
public final class ShazamBoltFx {
	private static final int CORE = 0xFFFFFF;
	private static final int GOLD = 0xFFD24A;
	private static final int PALE = 0xFFF4C8;
	private static final int RED = 0xFF2A18;

	private static final class Fx {
		final ShazamFxPayload p;
		final Vec3 a;
		final Vec3 b;
		final double start;

		Fx(ShazamFxPayload p, double start) {
			this.p = p;
			this.a = new Vec3(p.ax(), p.ay(), p.az());
			this.b = new Vec3(p.bx(), p.by(), p.bz());
			this.start = start;
		}
	}

	private record Seg(Vec3 a, Vec3 b, double w) {
	}

	private static final List<Fx> LIVE = new ArrayList<>();
	private static final Vec3 UP = new Vec3(0, 1, 0);

	private static VertexConsumer vc;
	private static Matrix4f mat;
	private static Vec3 origin = Vec3.ZERO;
	private static Vec3 eye = Vec3.ZERO;

	private ShazamBoltFx() {
	}

	public static void reset() {
		LIVE.clear();
	}

	public static void receive(ShazamFxPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (LIVE.size() > 120) {
			LIVE.remove(0);
		}
		LIVE.add(new Fx(p, mc.level.getGameTime()));
		if (p.kind() == ShazamFxPayload.SKY && mc.player != null && mc.player.distanceToSqr(p.bx(), p.by(), p.bz()) < 64 * 64) {
			mc.level.setSkyFlashTime(2);
		}
	}

	public static int live() {
		return LIVE.size();
	}

	// ---------------------------------------------------------------- drawing kit

	private static void v(Vec3 p, int rgb, float a) {
		vc.addVertex(mat, (float) (p.x - origin.x), (float) (p.y - origin.y), (float) (p.z - origin.z))
				.setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Math.round(255 * Mth.clamp(a, 0f, 1f)));
	}

	private static void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float al, float al2) {
		v(a, rgb, al);
		v(b, rgb, al);
		v(c, rgb, al2);
		v(d, rgb, al2);
		v(d, rgb, al2);
		v(c, rgb, al2);
		v(b, rgb, al);
		v(a, rgb, al);
	}

	private static void bar(Vec3 a, Vec3 b, Vec3 normal, double w, int rgb, float al) {
		if (al <= 0.004f) {
			return;
		}
		Vec3 side = b.subtract(a).cross(normal);
		if (side.lengthSqr() < 1.0e-10) {
			return;
		}
		side = side.normalize().scale(w);
		quad(a.add(side), b.add(side), b.subtract(side), a.subtract(side), rgb, al, al);
	}

	/** A glowing stroke facing the eye: soft gold halo, gold body, white-hot core. */
	private static void stroke(Vec3 a, Vec3 b, double w, int halo, float al) {
		Vec3 n = eye.subtract(a.add(b).scale(0.5));
		bar(a, b, n, w * 4.0, halo, al * 0.14f);
		bar(a, b, n, w * 1.6, halo, al * 0.55f);
		bar(a, b, n, w * 0.6, CORE, al);
	}

	/** A flat glowing stroke lying in the plane facing {@code normal} (ground cracks). */
	private static void flat(Vec3 a, Vec3 b, Vec3 normal, double w, int halo, float al, float heat) {
		bar(a, b, normal, w * 3.5, halo, al * 0.18f);
		bar(a, b, normal, w * 1.3, halo, al * 0.75f);
		bar(a, b, normal, w * 0.5, CORE, al * heat);
	}

	private static Vec3 perp(Vec3 d, RandomSource r) {
		Vec3 n = d.lengthSqr() < 1.0e-8 ? UP : d.normalize();
		Vec3 ref = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
		Vec3 u = n.cross(ref).normalize();
		Vec3 w = n.cross(u).normalize();
		double ang = r.nextDouble() * Math.PI * 2;
		return u.scale(Math.cos(ang)).add(w.scale(Math.sin(ang)));
	}

	/** Midpoint displacement with forks: the segments of one bolt a to b. */
	private static void bolt(List<Seg> out, Vec3 a, Vec3 b, double disp, int depth, double w, RandomSource r, int forks) {
		if (depth <= 0) {
			out.add(new Seg(a, b, w));
			return;
		}
		Vec3 d = b.subtract(a);
		Vec3 mid = a.add(d.scale(0.5)).add(perp(d, r).scale(disp * (0.4 + r.nextDouble() * 0.6)));
		bolt(out, a, mid, disp * 0.5, depth - 1, w, r, forks);
		bolt(out, mid, b, disp * 0.5, depth - 1, w * 0.92, r, forks);
		if (forks > 0 && r.nextFloat() < 0.38f) {
			Vec3 dir = mid.subtract(a);
			Vec3 end = mid.add(dir.scale(0.6 + r.nextDouble() * 0.6)).add(perp(dir, r).scale(dir.length() * 0.7));
			bolt(out, mid, end, disp * 0.5, depth - 1, w * 0.5, r, forks - 1);
		}
	}

	private static void drawBolt(Vec3 a, Vec3 b, double disp, int depth, double w, int seed, int forks, int halo, float al) {
		List<Seg> segs = new ArrayList<>();
		bolt(segs, a, b, disp, depth, w, RandomSource.create(seed), forks);
		for (Seg s : segs) {
			stroke(s.a, s.b, s.w, halo, al);
		}
	}

	/** Bright for two ticks, then a flicker that dies out. */
	private static float flicker(float t, int life, int seed) {
		if (t < 2f) {
			return 1f;
		}
		float fade = Mth.clamp(1f - (t - 2f) / Math.max(1f, life - 2f), 0f, 1f);
		int step = (int) t;
		boolean on = ((seed >>> (step % 16)) & 1) == 1 || step % 3 == 0;
		return fade * (on ? 1f : 0.35f);
	}

	private static float easeOut(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return 1f - (1f - x) * (1f - x) * (1f - x);
	}

	// ---------------------------------------------------------------- the world pass

	public static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (LIVE.isEmpty() || mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		double now = mc.level.getGameTime() + partial;
		PoseStack pose = ctx.matrixStack();
		vc = ctx.consumers().getBuffer(RenderType.lightning());
		mat = pose.last().pose();
		origin = ctx.camera().getPosition();
		eye = origin;
		for (Iterator<Fx> it = LIVE.iterator(); it.hasNext();) {
			Fx fx = it.next();
			double age = now - fx.start;
			if (age > fx.p.life() + 1 || age < -40) {
				it.remove();
				continue;
			}
			try {
				draw(mc, fx, (float) Math.max(0.0, age), partial);
			} catch (RuntimeException e) {
				it.remove();
			}
		}
	}

	private static void draw(Minecraft mc, Fx fx, float t, float partial) {
		int life = fx.p.life();
		int seed = fx.p.seed();
		int shape = seed + (int) (t / 2f) * 7919; // a new shape every two ticks
		float size = fx.p.size();
		switch (fx.p.kind()) {
			case ShazamFxPayload.ARC -> {
				double len = fx.a.distanceTo(fx.b);
				drawBolt(fx.a, fx.b, len * 0.22, 5, 0.05 * size, shape, 2, GOLD, flicker(t, life, seed));
			}
			case ShazamFxPayload.SKY -> sky(fx, t, life, size, seed, shape);
			case ShazamFxPayload.BLUR -> blur(fx, t, life, size, seed, shape);
			case ShazamFxPayload.CLAP -> clap(fx, t, life, size, shape);
			case ShazamFxPayload.BURST -> {
				RandomSource r = RandomSource.create(seed);
				int n = 6 + (int) (4 * size);
				float al = flicker(t, life, seed);
				float grow = easeOut(t / 2f);
				for (int i = 0; i < n; i++) {
					double ang = (i + r.nextDouble() * 0.6) * Math.PI * 2 / n;
					double len = (2.0 + 2.0 * size) * (0.6 + r.nextDouble() * 0.5) * grow;
					Vec3 from = fx.a.add(0, 0.15, 0);
					Vec3 to = from.add(Math.cos(ang) * len, r.nextDouble() * 0.6, Math.sin(ang) * len);
					drawBolt(from, to, len * 0.2, 4, 0.04, shape + i * 31, 1, GOLD, al);
				}
			}
			case ShazamFxPayload.AURA -> aura(mc, fx, t, life, shape, partial);
			case ShazamFxPayload.CRACK -> crack(fx, t, life, size, seed, shape);
			default -> {
			}
		}
	}

	private static void sky(Fx fx, float t, int life, float size, int seed, int shape) {
		RandomSource r = RandomSource.create(seed);
		Vec3 hit = fx.b;
		Vec3 top = hit.add(r.nextDouble() * 6 - 3, 34 + r.nextDouble() * 8, r.nextDouble() * 6 - 3);
		float al = flicker(t, life, seed);
		// the leader strikes down in the first tick
		float reach = Mth.clamp(t / 1.2f, 0f, 1f);
		Vec3 end = top.add(hit.subtract(top).scale(reach));
		drawBolt(top, end, 4.0 * size, 7, 0.13 * size, shape, 3, GOLD, al);
		if (size >= 1.5f) {
			drawBolt(top.add(1.5, 0, -1), end, 3.0 * size, 6, 0.05 * size, shape + 101, 2, PALE, al * 0.7f);
		}
		// the ground flash: a bright disc and a glowing ring
		if (reach >= 1f) {
			float g = Mth.clamp(1f - t / 6f, 0f, 1f);
			double rad = (1.0 + size) * (0.6 + 0.4 * easeOut(t / 3f));
			Vec3 c = hit.add(0, 0.05, 0);
			for (int i = 0; i < 20; i++) {
				double a0 = i * Math.PI * 2 / 20, a1 = (i + 1) * Math.PI * 2 / 20;
				Vec3 p0 = c.add(Math.cos(a0) * rad, 0, Math.sin(a0) * rad);
				Vec3 p1 = c.add(Math.cos(a1) * rad, 0, Math.sin(a1) * rad);
				quad(c, c, p1, p0, PALE, 0.7f * g, 0f);
			}
			// a few ground-hugging forks
			for (int i = 0; i < 3 + (int) size; i++) {
				double ang = r.nextDouble() * Math.PI * 2;
				Vec3 to = c.add(Math.cos(ang) * rad * 1.8, 0.05, Math.sin(ang) * rad * 1.8);
				drawBolt(c.add(0, 0.1, 0), to, 0.5, 3, 0.03, shape + i, 0, GOLD, al * 0.8f);
			}
		}
	}

	private static void blur(Fx fx, float t, int life, float size, int seed, int shape) {
		Vec3 a = fx.a, b = fx.b;
		Vec3 d = b.subtract(a);
		double len = d.length();
		if (len < 0.2) {
			return;
		}
		float fade = Mth.clamp(1f - t / life, 0f, 1f);
		int n = Math.max(4, (int) (len * 2));
		// three streaks at shin, waist and shoulder height; the tail end fades first
		for (double h : new double[] { 0.35, 0.95, 1.5 }) {
			for (int i = 0; i < n; i++) {
				float f0 = i / (float) n, f1 = (i + 1) / (float) n;
				float al = fade * Mth.clamp((f1 - (1f - fade) * 1.2f) * 2f, 0f, 1f);
				Vec3 p0 = a.add(d.scale(f0)).add(0, h, 0), p1 = a.add(d.scale(f1)).add(0, h, 0);
				Vec3 nrm = eye.subtract(p0.add(p1).scale(0.5));
				bar(p0, p1, nrm, 0.32, RED, al * 0.18f);
				bar(p0, p1, nrm, 0.1, RED, al * 0.7f);
				bar(p0, p1, nrm, 0.035, 0xFFD8C8, al * 0.8f);
			}
		}
		// after-images: red silhouettes left along the path
		for (int k = 1; k <= 3; k++) {
			float f = k / 4f;
			Vec3 c = a.add(d.scale(f));
			float al = fade * f * 0.35f;
			Vec3 toEye = eye.subtract(c);
			Vec3 side = new Vec3(toEye.z, 0, -toEye.x);
			side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
			quad(c.add(side.scale(0.32)), c.subtract(side.scale(0.32)), c.subtract(side.scale(0.32)).add(0, 1.85, 0),
					c.add(side.scale(0.32)).add(0, 1.85, 0), RED, al, al * 0.5f);
		}
		// crackle along the trail
		RandomSource r = RandomSource.create(shape);
		for (int i = 0; i < 4; i++) {
			Vec3 p0 = a.add(d.scale(r.nextDouble())).add(r.nextDouble() - 0.5, 0.3 + r.nextDouble() * 1.4, r.nextDouble() - 0.5);
			Vec3 p1 = p0.add(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5);
			drawBolt(p0, p1, 0.25, 3, 0.02, shape + i, 0, GOLD, fade);
		}
	}

	private static void clap(Fx fx, float t, int life, float size, int shape) {
		Vec3 n = fx.b.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : fx.b.normalize();
		Vec3 ref = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
		Vec3 u = n.cross(ref).normalize();
		Vec3 w = n.cross(u).normalize();
		RandomSource r = RandomSource.create(shape);
		for (int wave = 0; wave < 3; wave++) {
			float wt = t - wave * 1.5f;
			if (wt < 0f) {
				continue;
			}
			float f = easeOut(wt / 6f);
			float al = Mth.clamp(1f - wt / 8f, 0f, 1f) * (wave == 0 ? 1f : 0.6f);
			if (al <= 0f) {
				continue;
			}
			double dist = 0.8 + size * f;
			double rad = 0.4 + dist * 0.7;
			Vec3 c = fx.a.add(n.scale(dist));
			int segs = 32;
			Vec3 prev = null;
			for (int i = 0; i <= segs; i++) {
				double ang = i * Math.PI * 2 / segs;
				double jitter = i == segs ? 0 : (r.nextDouble() - 0.5) * 0.12 * rad;
				Vec3 p = c.add(u.scale(Math.cos(ang) * (rad + jitter))).add(w.scale(Math.sin(ang) * (rad + jitter)));
				if (prev != null) {
					bar(prev, p, n, 0.22 * (1 - f) + 0.06, 0xCFE4FF, al * 0.25f);
					bar(prev, p, n, 0.05, CORE, al * 0.9f);
				}
				prev = p;
			}
			// a faint disc of pressure inside the ring
			for (int i = 0; i < 16; i++) {
				double a0 = i * Math.PI * 2 / 16, a1 = (i + 1) * Math.PI * 2 / 16;
				Vec3 p0 = c.add(u.scale(Math.cos(a0) * rad)).add(w.scale(Math.sin(a0) * rad));
				Vec3 p1 = c.add(u.scale(Math.cos(a1) * rad)).add(w.scale(Math.sin(a1) * rad));
				quad(c, c, p1, p0, 0xDDEBFF, al * 0.12f, al * 0.02f);
			}
			if (wave == 0) {
				for (int i = 0; i < 6; i++) {
					double ang = r.nextDouble() * Math.PI * 2;
					Vec3 rim = c.add(u.scale(Math.cos(ang) * rad)).add(w.scale(Math.sin(ang) * rad));
					Vec3 out = rim.add(u.scale(Math.cos(ang) * 0.8)).add(w.scale(Math.sin(ang) * 0.8)).add(n.scale(0.4));
					drawBolt(rim, out, 0.3, 3, 0.025, shape + i, 0, GOLD, al);
				}
			}
		}
	}

	private static void aura(Minecraft mc, Fx fx, float t, int life, int shape, float partial) {
		Entity e = fx.p.entity() >= 0 && mc.level != null ? mc.level.getEntity(fx.p.entity()) : null;
		Vec3 feet = e != null ? e.getPosition(partial) : fx.a;
		double h = e != null ? e.getBbHeight() : 1.8;
		float al = Mth.clamp(t / 3f, 0f, 1f) * Mth.clamp((life - t) / 8f, 0f, 1f);
		double r = 0.85;
		// the soft shell: a cylinder of gold light fading toward its top and bottom
		int segs = 24;
		for (int i = 0; i < segs; i++) {
			double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
			Vec3 p0 = feet.add(Math.cos(a0) * r, 0, Math.sin(a0) * r);
			Vec3 p1 = feet.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
			Vec3 mid0 = p0.add(0, h * 0.5, 0), mid1 = p1.add(0, h * 0.5, 0);
			quad(mid0, mid1, p1.add(0, h + 0.3, 0), p0.add(0, h + 0.3, 0), GOLD, al * 0.14f, 0f);
			quad(mid0, mid1, p1, p0, GOLD, al * 0.14f, al * 0.3f);
		}
		// three rings climbing the body
		for (int k = 0; k < 3; k++) {
			double y = ((t * 0.08 + k / 3.0) % 1.0) * (h + 0.3);
			Vec3 c = feet.add(0, y, 0);
			Vec3 prev = c.add(r, 0, 0);
			for (int i = 1; i <= segs; i++) {
				double ang = i * Math.PI * 2 / segs;
				Vec3 p = c.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
				bar(prev, p, UP, 0.12, GOLD, al * 0.3f);
				bar(prev, p, UP, 0.035, CORE, al * 0.8f);
				prev = p;
			}
		}
		// arcs crawling over him
		RandomSource rr = RandomSource.create(shape);
		for (int i = 0; i < 3; i++) {
			double a0 = rr.nextDouble() * Math.PI * 2, a1 = a0 + rr.nextDouble() * 1.4 - 0.7;
			Vec3 p0 = feet.add(Math.cos(a0) * 0.55, rr.nextDouble() * h, Math.sin(a0) * 0.55);
			Vec3 p1 = feet.add(Math.cos(a1) * 0.55, rr.nextDouble() * h, Math.sin(a1) * 0.55);
			drawBolt(p0, p1, 0.2, 3, 0.022, shape + i, 0, GOLD, al);
		}
	}

	private static void crack(Fx fx, float t, int life, float size, int seed, int shape) {
		Vec3 c = fx.a;
		RandomSource r = RandomSource.create(seed);
		float reveal = easeOut(t / 3f);
		float al = Mth.clamp((life - t) / 14f, 0f, 1f);
		float heat = Mth.clamp(1f - t / 10f, 0.15f, 1f);
		int halo = t < 6f ? PALE : GOLD;
		int cracks = 7;
		Vec3 fwd = new Vec3(fx.b.x, 0, fx.b.z);
		double base = fwd.lengthSqr() < 1.0e-6 ? 0 : Math.atan2(fwd.z, fwd.x);
		for (int k = 0; k < cracks; k++) {
			double ang = base + (k + r.nextDouble() * 0.5) * Math.PI * 2 / cracks;
			double len = size * (0.6 + r.nextDouble() * 0.45);
			int steps = 7;
			Vec3 prev = c;
			double a = ang;
			for (int s = 1; s <= steps; s++) {
				if (s / (double) steps > reveal) {
					break;
				}
				a += (r.nextDouble() - 0.5) * 0.9;
				Vec3 p = prev.add(Math.cos(a) * len / steps, 0, Math.sin(a) * len / steps);
				double w = 0.07 * (1.0 - s / (double) steps * 0.7) * (0.8 + 0.2 * size / 3);
				flat(prev, p, UP, w, halo, al, heat);
				// a fork now and then
				if (r.nextFloat() < 0.3f) {
					double fa = a + (r.nextBoolean() ? 0.8 : -0.8);
					Vec3 q = p.add(Math.cos(fa) * len * 0.22, 0, Math.sin(fa) * len * 0.22);
					flat(p, q, UP, w * 0.6, halo, al, heat);
				}
				// sparks jumping off the crack while it's hot
				if (t < 10f && s % 3 == 0) {
					RandomSource sr = RandomSource.create(shape + k * 13 + s);
					drawBolt(p, p.add(sr.nextDouble() * 0.6 - 0.3, 0.4 + sr.nextDouble() * 0.6, sr.nextDouble() * 0.6 - 0.3), 0.15, 2, 0.02,
							shape + k + s, 0, GOLD, heat);
				}
				prev = p;
			}
		}
	}

	// ---------------------------------------------------------------- charged hands and eyes (from ShazamSuitLayer)

	/** He's charged: the Lightning bar at 60%+, or Zeus Storm / Eternity Strike running. */
	public static boolean charged(AbstractClientPlayer player) {
		return Shazam.lightning(player) >= ShazamConfig.LIGHTNING_MAX * 0.6f || Shazam.storming(player) || Shazam.eternityAge(player) >= 0;
	}

	/** Arcs crackling round both hands and glowing eyes, in the model's own space (called with the layer's pose). */
	public static void charged(PoseStack pose, MultiBufferSource buffers, PlayerModel<AbstractClientPlayer> model, AbstractClientPlayer player,
			float ageInTicks, boolean slim) {
		vc = buffers.getBuffer(RenderType.lightning());
		origin = Vec3.ZERO;
		int tick = (int) ageInTicks;
		float intensity = Shazam.storming(player) || Shazam.eternityAge(player) >= 0 ? 1f
				: Mth.clamp((Shazam.lightning(player) / ShazamConfig.LIGHTNING_MAX - 0.6f) / 0.4f, 0.35f, 1f);
		for (int side = 0; side < 2; side++) {
			pose.pushPose();
			(side == 0 ? model.rightArm : model.leftArm).translateAndRotate(pose);
			mat = pose.last().pose();
			double cx = (side == 0 ? -1.0 : 1.0) / 16.0 * (slim ? 0.5 : 1.0);
			Vec3 hand = new Vec3(cx, 10.5 / 16.0, 0);
			RandomSource r = RandomSource.create(player.getId() * 31L + tick * 7L + side);
			int arcs = 1 + (intensity > 0.7f ? 2 : 1);
			for (int i = 0; i < arcs; i++) {
				Vec3 p0 = hand.add((r.nextDouble() - 0.5) * 0.2, (r.nextDouble() - 0.3) * 0.25, (r.nextDouble() - 0.5) * 0.2);
				Vec3 p1 = p0.add((r.nextDouble() - 0.5) * 0.4, r.nextDouble() * 0.25, (r.nextDouble() - 0.5) * 0.4);
				List<Seg> segs = new ArrayList<>();
				bolt(segs, p0, p1, 0.08, 3, 0.012, r, 1);
				for (Seg s : segs) {
					bar(s.a, s.b, new Vec3(0, 0, 1), s.w * 3, GOLD, 0.3f * intensity);
					bar(s.a, s.b, new Vec3(1, 0, 0), s.w * 3, GOLD, 0.3f * intensity);
					bar(s.a, s.b, new Vec3(0, 0, 1), s.w, CORE, intensity);
					bar(s.a, s.b, new Vec3(1, 0, 0), s.w, CORE, intensity);
				}
			}
			pose.popPose();
		}
		// the eyes: two small white-gold glows on the face
		pose.pushPose();
		model.head.translateAndRotate(pose);
		mat = pose.last().pose();
		float pulse = (0.75f + 0.25f * Mth.sin(ageInTicks * 0.6f)) * intensity;
		double z = -4.06 / 16.0;
		for (int side = -1; side <= 1; side += 2) {
			double x0 = side * 1.0 / 16.0, x1 = side * 3.0 / 16.0;
			double y0 = -4.0 / 16.0, y1 = -3.0 / 16.0;
			quad(new Vec3(x0, y0, z), new Vec3(x1, y0, z), new Vec3(x1, y1, z), new Vec3(x0, y1, z), CORE, pulse, pulse);
			double g = 0.9 / 16.0;
			quad(new Vec3(x0 - g * side, y0 - g, z - 0.002), new Vec3(x1 + g * side, y0 - g, z - 0.002), new Vec3(x1 + g * side, y1 + g, z - 0.002),
					new Vec3(x0 - g * side, y1 + g, z - 0.002), GOLD, pulse * 0.35f, pulse * 0.35f);
		}
		pose.popPose();
	}
}
