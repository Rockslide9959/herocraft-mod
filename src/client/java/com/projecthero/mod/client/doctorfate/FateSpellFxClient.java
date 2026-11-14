package com.projecthero.mod.client.doctorfate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.doctorfate.network.FateSpellFxPayload;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Doctor Fate effects pass (tester: "needs more work on effects and moves"): every spell's glowing golden geometry, for
 * every viewer near ({@link FateSpellFxPayload} from the server). Additive light-lines built by {@link FateGlyphs}:
 * <ul>
 *   <li><b>Ankh Bolt</b>: a beam with a spinning ankh racing down it and rune rings left along its path.</li>
 *   <li><b>Golden Chains</b>: linked chains rising from four anchors onto the target, a binding rune circle under it.</li>
 *   <li><b>Banishment / Gate Drop</b>: an ankh gate opens flat on the ground (rune band, counter-turning rings, a lying ankh) under
 *   a pillar of light, then closes.</li>
 *   <li><b>Glyph Storm</b>: a rune circle in the sky rains hieroglyph blades that flash where they strike the ground circle.</li>
 *   <li><b>Ankh Step</b>: an upright ankh-ring portal opens and irises shut at both ends.</li>
 *   <li><b>Order Blast</b>: three rings of force race down a cone, glyphs on the first, an ankh at its mouth.</li>
 *   <li><b>Ward of Order</b>: a turning rune circle with a low golden rim wall.</li>
 *   <li><b>Avatar of Order</b>: its slam's huge rune circle and shock ring; a column of rings as it rises.</li>
 *   <li><b>Landing</b>: a golden ankh flares flat under him and a rune ring races out.</li>
 * </ul>
 * Drawn after the entities with {@code RenderType.lightning()} (additive). Cleared on disconnect.
 */
public final class FateSpellFxClient {
	private static final class Fx {
		final FateSpellFxPayload p;
		final Vec3 pos;
		final Vec3 dir;
		final double start;
		final int seed;

		Fx(FateSpellFxPayload p, double start, int seed) {
			this.p = p;
			this.pos = new Vec3(p.x(), p.y(), p.z());
			this.dir = new Vec3(p.dx(), p.dy(), p.dz());
			this.start = start;
			this.seed = seed;
		}
	}

	private static final List<Fx> LIVE = new ArrayList<>();
	private static final Vec3 UP = new Vec3(0, 1, 0);
	private static final Vec3 EAST = new Vec3(1, 0, 0);
	private static final Vec3 SOUTH = new Vec3(0, 0, 1);
	private static int seeds;

	private FateSpellFxClient() {
	}

	static void reset() {
		LIVE.clear();
	}

	static void receive(FateSpellFxPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (p.kind() == FateSpellFxPayload.SUMMON) {
			FateHelmetClient.receive(p); // v0.17.4: the helmet itself; its trail is drawn here
		}
		if (LIVE.size() > 160) {
			LIVE.remove(0);
		}
		LIVE.add(new Fx(p, mc.level.getGameTime(), seeds++ * 31));
	}

	/** Effects live right now (the harness counts them). */
	public static int live() {
		return LIVE.size();
	}

	static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		double now = mc.level.getGameTime() + partial;
		PoseStack pose = ctx.matrixStack();
		java.util.List<com.projecthero.mod.doctorfate.helmet.FateHelmetEntity> placed = mc.level.getEntitiesOfClass(
				com.projecthero.mod.doctorfate.helmet.FateHelmetEntity.class, new net.minecraft.world.phys.AABB(ctx.camera().getBlockPosition()).inflate(48.0));
		if (LIVE.isEmpty() && placed.isEmpty()) {
			return;
		}
		pose.pushPose();
		FateGlyphs.begin(ctx.consumers().getBuffer(RenderType.lightning()), pose.last().pose(), ctx.camera().getPosition());
		for (com.projecthero.mod.doctorfate.helmet.FateHelmetEntity h : placed) {
			placedHelmet(h, partial);
		}
		for (Iterator<Fx> it = LIVE.iterator(); it.hasNext();) {
			Fx fx = it.next();
			double age = now - fx.start;
			if (age > fx.p.life() + 1 || age < -40) {
				it.remove();
				continue;
			}
			float t = (float) Math.max(0.0, age);
			try {
				draw(mc, fx, t, partial);
			} catch (RuntimeException e) {
				it.remove(); // an effect must never break the frame
			}
		}
		pose.popPose();
	}

	/** In over {@code in} ticks, out over the last {@code out}. */
	private static float env(float t, int life, float in, float out) {
		return Mth.clamp(t / in, 0f, 1f) * Mth.clamp((life - t) / out, 0f, 1f);
	}

	private static float easeOut(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return 1f - (1f - x) * (1f - x) * (1f - x);
	}

	private static void draw(Minecraft mc, Fx fx, float t, float partial) {
		int life = fx.p.life();
		int col = fx.p.colour();
		float size = fx.p.size();
		switch (fx.p.kind()) {
			case FateSpellFxPayload.BOLT -> bolt(fx, t, size, col);
			case FateSpellFxPayload.ANKH -> {
				float a = env(t, life, 1.5f, Math.min(6f, life * 0.6f));
				float s = size * (0.55f + 0.45f * easeOut(t / 3f)) * (1f + 0.25f * t / life);
				Vec3[] b = FateGlyphs.basis(fx.dir);
				FateGlyphs.ankh(fx.pos, b[0], b[1], s, s * 0.045, col, a);
				FateGlyphs.ring(fx.pos.add(b[1].scale(s * 0.05)), b[0], b[1], s * 0.62, s * 0.02, 24, col, a * 0.6f, true);
			}
			case FateSpellFxPayload.GATE -> gate(fx, t, life, size, col);
			case FateSpellFxPayload.PORTAL -> portal(fx, t, life, size, col);
			case FateSpellFxPayload.CHAINS -> chains(mc, fx, t, life, size, col, partial);
			case FateSpellFxPayload.WARD -> ward(fx, t, life, size, col);
			case FateSpellFxPayload.STORM -> storm(fx, t, life, size, col);
			case FateSpellFxPayload.CONE -> cone(fx, t, life, size, col);
			case FateSpellFxPayload.SLAM -> slam(fx, t, life, size, col);
			case FateSpellFxPayload.LANDING -> landing(fx, t, life, size, col);
			case FateSpellFxPayload.RISE -> rise(fx, t, life, size, col);
			case FateSpellFxPayload.SUMMON -> summon(mc, fx, t, life, col, partial);
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- Ankh Bolt

	private static void bolt(Fx fx, float t, float size, int col) {
		Vec3 a = fx.pos, b = fx.dir;
		Vec3 d = b.subtract(a);
		double len = d.length();
		if (len < 1.0e-3) {
			return;
		}
		Vec3 n = d.scale(1.0 / len);
		float travel = easeOut(t / 4f);
		Vec3 head = a.add(d.scale(travel));
		float fade = t < 3f ? 1f : Mth.clamp(1f - (t - 3f) / (fx.p.life() - 3f), 0f, 1f);
		// the beam: a thin bright line behind the head, thinning as it fades
		FateGlyphs.glowBeam(a, head, 0.07 * size * (0.4 + 0.6 * fade), col, fade);
		// rune rings left along the path
		Vec3[] bs = FateGlyphs.basis(n);
		for (double s = 1.5; s < len * travel; s += 2.2) {
			float ra = fade * 0.7f;
			FateGlyphs.ring(a.add(n.scale(s)), bs[0], bs[1], 0.22 * size * (1 + (1 - fade) * 0.8), 0.018, 12, col, ra, false);
		}
		// the spinning ankh at the head (faces down the beam), until it hits
		if (t < 5f) {
			double spin = t * 1.4;
			Vec3 u = bs[0].scale(Math.cos(spin)).add(bs[1].scale(Math.sin(spin)));
			Vec3 v = n.cross(u);
			float s = 1.0f * size;
			FateGlyphs.ankh(head, u, v, s, s * 0.05, col, Mth.clamp(1.25f - t / 5f, 0f, 1f));
		}
	}

	// ---------------------------------------------------------------- ankh gates

	private static void gate(Fx fx, float t, int life, float size, int col) {
		float open = easeOut(t / 4f) * Mth.clamp((life - t) / 6f, 0f, 1f);
		if (open <= 0.01f) {
			return;
		}
		double r = size * open;
		float a = Mth.clamp(open * 1.2f, 0f, 1f);
		Vec3 c = fx.pos.add(0, 0.04, 0);
		double spin = t * 0.08;
		Vec3 u = EAST.scale(Math.cos(spin)).add(SOUTH.scale(Math.sin(spin)));
		Vec3 v = SOUTH.scale(Math.cos(spin)).subtract(EAST.scale(Math.sin(spin)));
		FateGlyphs.disc(c, EAST, SOUTH, r, r, 28, col, 0.16f * a);
		FateGlyphs.disc(c.add(0, 0.01, 0), EAST, SOUTH, r * 0.45, r * 0.45, 20, 0xFFF6D8, 0.12f * a);
		FateGlyphs.ring(c, u, v, r, 0.05 + 0.02 * size, 40, col, a, true);
		FateGlyphs.ring(c, u, v, r * 0.78, 0.03, 36, col, a * 0.8f, true);
		FateGlyphs.band(c, u, v, r * 0.89, Math.max(8, (int) (size * 7)), r * 0.14, 0.012 + 0.006 * size, spin, col, a * 0.9f, fx.seed);
		Vec3 u2 = EAST.scale(Math.cos(-spin * 2)).add(SOUTH.scale(Math.sin(-spin * 2)));
		Vec3 v2 = SOUTH.scale(Math.cos(-spin * 2)).subtract(EAST.scale(Math.sin(-spin * 2)));
		FateGlyphs.ring(c, u2, v2, r * 0.45, 0.025, 24, col, a * 0.9f, true);
		FateGlyphs.ankh(c.add(0, 0.01, 0), u2, v2, r * 0.72, r * 0.03, col, a);
		// the pillar of light
		Vec3 cam = FateGlyphs.camera();
		Vec3 side = cam.subtract(c).cross(UP);
		if (side.lengthSqr() > 1.0e-6) {
			side = side.normalize().scale(r * 0.85);
			double h = 2.5 + size * 2.5;
			Vec3 top = c.add(0, h * open, 0);
			FateGlyphs.gradient(c.subtract(side), c.add(side), top.add(side), top.subtract(side), col, 0.35f * a, 0f);
			FateGlyphs.gradient(c.subtract(side.scale(0.35)), c.add(side.scale(0.35)), top.add(side.scale(0.35)), top.subtract(side.scale(0.35)),
					0xFFF6D8, 0.3f * a, 0f);
		}
	}

	private static void portal(Fx fx, float t, int life, float size, int col) {
		float open = easeOut(t / 3f) * Mth.clamp((life - t) / 5f, 0f, 1f);
		if (open <= 0.01f) {
			return;
		}
		Vec3 flat = new Vec3(fx.dir.x, 0, fx.dir.z);
		Vec3[] b = FateGlyphs.basis(flat.lengthSqr() < 1.0e-6 ? SOUTH : flat);
		Vec3 u = b[0], v = UP;
		double rx = 0.62 * size * open, ry = 1.05 * size * open;
		float a = Mth.clamp(open * 1.3f, 0f, 1f);
		FateGlyphs.disc(fx.pos, u, v, rx, ry, 24, col, 0.2f * a);
		FateGlyphs.ellipse(fx.pos, u, v, rx, ry, 0.045, 32, col, a);
		FateGlyphs.ellipse(fx.pos, u, v, rx * 0.8, ry * 0.86, 0.02, 28, col, a * 0.7f);
		// the swirl: three spiral arms turning inward
		Vec3 n = u.cross(v);
		for (int arm = 0; arm < 3; arm++) {
			Vec3 prev = null;
			for (int k = 0; k <= 10; k++) {
				double f = k / 10.0;
				double ang = arm * Math.PI * 2 / 3 + f * 2.4 + t * 0.5;
				Vec3 p = FateGlyphs.at(fx.pos, u, v, Math.cos(ang) * rx * 0.75 * (1 - f), Math.sin(ang) * ry * 0.75 * (1 - f));
				if (prev != null) {
					FateGlyphs.glow(prev, p, n, 0.02, col, a * (float) (1 - f * 0.6));
				}
				prev = p;
			}
		}
		FateGlyphs.ankh(fx.pos, u, v, ry * 0.7, 0.03, col, a * 0.9f);
	}

	// ---------------------------------------------------------------- v0.17.4 the Helmet of Fate

	/** The summoned helmet's golden trail along its arc, and a small turning ankh ring round it ({@link FateHelmetClient} draws it). */
	private static void summon(Minecraft mc, Fx fx, float t, int life, int col, float partial) {
		Entity e = mc.level.getEntity(fx.p.entity());
		if (e == null) {
			return;
		}
		Vec3 to = FateHelmetClient.catchPoint(e, partial);
		double f = Mth.clamp(t / life, 0.0, 1.0);
		float fade = Mth.clamp((life + 1 - t) / 3f, 0f, 1f);
		Vec3 prev = null;
		int n = 18;
		for (int k = 0; k <= n; k++) {
			double g = f - 0.35 * (1.0 - k / (double) n);
			if (g < 0) {
				continue;
			}
			Vec3 p = FateHelmetClient.pathPoint(fx.pos, to, g);
			if (prev != null) {
				float a = (float) (k / (double) n) * fade;
				FateGlyphs.glowBeam(prev, p, 0.05 + 0.06 * k / n, col, a * 0.8f);
				FateGlyphs.beam(prev, p, 0.02, com.projecthero.mod.doctorfate.DoctorFateFx.WHITE_GOLD, a);
			}
			prev = p;
		}
		if (f < 1.0) {
			Vec3 at = FateHelmetClient.pathPoint(fx.pos, to, f);
			Vec3 cam = FateGlyphs.camera();
			Vec3[] b = FateGlyphs.basis(cam.subtract(at));
			double spin = t * 0.35;
			Vec3 u = b[0].scale(Math.cos(spin)).add(b[1].scale(Math.sin(spin)));
			Vec3 v = b[1].scale(Math.cos(spin)).subtract(b[0].scale(Math.sin(spin)));
			FateGlyphs.ring(at, u, v, 0.55, 0.025, 24, col, 0.8f * fade, true);
			FateGlyphs.band(at, u, v, 0.72, 8, 0.12, 0.012, spin, col, 0.7f * fade, fx.seed);
		} else {
			// arrival: an ankh flares in his hands
			float a = Mth.clamp(1f - (t - life) / 1f, 0f, 1f);
			Vec3[] b = FateGlyphs.basis(e.getLookAngle());
			FateGlyphs.ankh(to, b[0], b[1], 0.9, 0.04, col, a);
		}
	}

	/** A helmet set down in the world: a flat golden ankh and a slow rune ring glowing on the ground under it. */
	private static void placedHelmet(com.projecthero.mod.doctorfate.helmet.FateHelmetEntity h, float partial) {
		if (h.falling()) {
			return;
		}
		Vec3 c = h.getPosition(partial).add(0, 0.03, 0);
		float t = h.tickCount + partial;
		float pulse = 0.55f + 0.25f * Mth.sin(t * 0.12f);
		double r = Math.toRadians(h.getYRot() + t * 0.6);
		Vec3 fwd = new Vec3(-Math.sin(r), 0, Math.cos(r));
		Vec3 side = new Vec3(Math.cos(r), 0, Math.sin(r));
		FateGlyphs.ankh(c, side, fwd, 0.55, 0.025, com.projecthero.mod.doctorfate.DoctorFate.GOLD, 0.5f * pulse);
		FateGlyphs.ring(c, side, fwd, 0.62, 0.018, 28, com.projecthero.mod.doctorfate.DoctorFate.GOLD, 0.45f * pulse, true);
		FateGlyphs.band(c, side, fwd, 0.78, 10, 0.1, 0.01, t * 0.02, com.projecthero.mod.doctorfate.DoctorFate.GOLD, 0.35f * pulse, h.getId());
	}

	// ---------------------------------------------------------------- Golden Chains

	private static void chains(Minecraft mc, Fx fx, float t, int life, float size, int col, float partial) {
		Entity e = fx.p.entity() >= 0 && mc.level != null ? mc.level.getEntity(fx.p.entity()) : null;
		Vec3 feet = e != null ? e.getPosition(partial) : fx.pos;
		double h = e != null ? e.getBbHeight() : 1.8;
		double w = e != null ? e.getBbWidth() : 0.6;
		Vec3 core = feet.add(0, h * 0.55, 0);
		float a = env(t, life, 2f, 6f);
		float reach = easeOut(t / 4f);
		double r = w * 0.5 + size;
		// the binding circle under it
		double spin = t * 0.1;
		Vec3 u = EAST.scale(Math.cos(spin)).add(SOUTH.scale(Math.sin(spin)));
		Vec3 v = SOUTH.scale(Math.cos(spin)).subtract(EAST.scale(Math.sin(spin)));
		Vec3 g = feet.add(0, 0.05, 0);
		FateGlyphs.ring(g, u, v, r, 0.04, 32, col, a, true);
		FateGlyphs.ring(g, u, v, r * 0.72, 0.025, 28, col, a * 0.8f, true);
		FateGlyphs.band(g, u, v, r * 0.86, 8, r * 0.18, 0.012, spin, col, a * 0.8f, fx.seed);
		FateGlyphs.disc(g, EAST, SOUTH, r, r, 24, col, 0.1f * a);
		// four chains from the circle's edge onto the body, link by link
		for (int k = 0; k < 4; k++) {
			double ang = k * Math.PI / 2 + Math.PI / 4 + spin * 0.5;
			Vec3 anchor = g.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
			Vec3 target = core.add(Math.cos(ang) * w * 0.3, (k % 2 == 0 ? 0.25 : -0.15) * h, Math.sin(ang) * w * 0.3);
			Vec3 d = target.subtract(anchor).scale(reach);
			double len = d.length();
			if (len < 0.05) {
				continue;
			}
			Vec3 n = d.scale(1.0 / len);
			Vec3[] bs = FateGlyphs.basis(n);
			double link = 0.3;
			int links = (int) (len / link);
			for (int i = 0; i < links; i++) {
				Vec3 c = anchor.add(n.scale((i + 0.5) * link));
				Vec3 across = i % 2 == 0 ? bs[0] : bs[1];
				FateGlyphs.ellipse(c, n, across, link * 0.7, link * 0.32, 0.026, 10, col, a);
			}
		}
	}

	// ---------------------------------------------------------------- Ward of Order

	private static void ward(Fx fx, float t, int life, float size, int col) {
		float a = env(t, life, 5f, 10f);
		float grow = easeOut(t / 6f);
		double r = size * grow;
		Vec3 c = fx.pos.add(0, 0.05, 0);
		double spin = t * 0.025;
		Vec3 u = EAST.scale(Math.cos(spin)).add(SOUTH.scale(Math.sin(spin)));
		Vec3 v = SOUTH.scale(Math.cos(spin)).subtract(EAST.scale(Math.sin(spin)));
		float pulse = 0.8f + 0.2f * Mth.sin(t * 0.25f);
		FateGlyphs.ring(c, u, v, r, 0.07, 64, col, a * pulse, true);
		FateGlyphs.ring(c, u, v, r * 0.88, 0.03, 60, col, a * 0.8f, true);
		FateGlyphs.band(c, u, v, r * 0.94, 18, 0.5, 0.018, spin, col, a * 0.85f, fx.seed);
		Vec3 u2 = EAST.scale(Math.cos(-spin * 1.6)).add(SOUTH.scale(Math.sin(-spin * 1.6)));
		Vec3 v2 = SOUTH.scale(Math.cos(-spin * 1.6)).subtract(EAST.scale(Math.sin(-spin * 1.6)));
		FateGlyphs.ring(c, u2, v2, r * 0.42, 0.04, 36, col, a * 0.9f, true);
		// a square of Order inscribed in the circle, and the ankh at its heart
		for (int k = 0; k < 4; k++) {
			double a0 = -spin * 1.6 + k * Math.PI / 2, a1 = a0 + Math.PI / 2;
			Vec3 p0 = c.add(Math.cos(a0) * r * 0.86, 0, Math.sin(a0) * r * 0.86);
			Vec3 p1 = c.add(Math.cos(a1) * r * 0.86, 0, Math.sin(a1) * r * 0.86);
			FateGlyphs.glow(p0, p1, UP, 0.025, col, a * 0.7f);
		}
		FateGlyphs.ankh(c.add(0, 0.01, 0), u2, v2, r * 0.6, 0.035, col, a);
		FateGlyphs.disc(c, EAST, SOUTH, r, r, 40, col, 0.06f * a);
		FateGlyphs.wall(c, r, 1.3, 48, col, 0.22f * a * pulse);
	}

	// ---------------------------------------------------------------- Glyph Storm

	private static void storm(Fx fx, float t, int life, float size, int col) {
		float a = env(t, life, 4f, 8f);
		double r = size * easeOut(t / 5f);
		Vec3 g = fx.pos.add(0, 0.06, 0);
		Vec3 sky = fx.pos.add(0, 7.0, 0);
		double spin = t * 0.06;
		Vec3 u = EAST.scale(Math.cos(spin)).add(SOUTH.scale(Math.sin(spin)));
		Vec3 v = SOUTH.scale(Math.cos(spin)).subtract(EAST.scale(Math.sin(spin)));
		FateGlyphs.ring(sky, u, v, r, 0.08, 56, col, a, true);
		FateGlyphs.ring(sky, u, v, r * 0.85, 0.04, 52, col, a * 0.7f, true);
		FateGlyphs.band(sky, u, v, r * 0.93, 16, 0.6, 0.02, -spin, col, a * 0.9f, fx.seed);
		FateGlyphs.disc(sky, EAST, SOUTH, r, r, 40, col, 0.08f * a);
		FateGlyphs.ring(g, v, u, r, 0.05, 56, col, a * 0.8f, true);
		FateGlyphs.disc(g, EAST, SOUTH, r, r, 40, col, 0.05f * a);
		// the blades: four a tick, each falls from the sky circle in 5 ticks and flashes on the ground
		RandomSource rnd = RandomSource.create(fx.seed);
		int total = (int) ((life - 6) * 4);
		Vec3 cam = FateGlyphs.camera();
		for (int i = 0; i < total; i++) {
			double ang = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * size;
			int glyph = rnd.nextInt(FateGlyphs.COUNT);
			float born = i / 4f;
			float age = t - born;
			if (age < 0f || age > 9f) {
				continue;
			}
			Vec3 ground = g.add(Math.cos(ang) * rr, 0, Math.sin(ang) * rr);
			if (age < 5f) {
				float f = age / 5f;
				Vec3 at = ground.add(0, 7.0 * (1 - f * f) + 0.4, 0);
				Vec3 toCam = cam.subtract(at);
				Vec3 side = new Vec3(toCam.z, 0, -toCam.x);
				side = side.lengthSqr() < 1.0e-6 ? EAST : side.normalize();
				FateGlyphs.glyph(glyph, at, side, UP, 0.85, 0.03, col, 1f);
				FateGlyphs.glowBeam(at.add(0, 0.3, 0), at.add(0, 0.3 + 1.6 * f, 0), 0.03, col, 0.5f);
			} else {
				float f = (age - 5f) / 4f;
				FateGlyphs.ring(ground, EAST, SOUTH, 0.2 + 0.7 * f, 0.03, 12, col, (1f - f), true);
				if (age < 6.5f) {
					FateGlyphs.glyph(glyph, ground.add(0, 0.02, 0), EAST, SOUTH, 0.6, 0.025, 0xFFF6D8, 1f - f);
				}
			}
		}
	}

	// ---------------------------------------------------------------- Order Blast

	private static void cone(Fx fx, float t, int life, float size, int col) {
		Vec3 n = fx.dir.lengthSqr() < 1.0e-6 ? SOUTH : fx.dir.normalize();
		Vec3[] b = FateGlyphs.basis(n);
		double range = size;
		for (int wave = 0; wave < 3; wave++) {
			float wt = t - wave * 2f;
			if (wt < 0f) {
				continue;
			}
			float f = easeOut(wt / 6f);
			float a = Mth.clamp(1f - wt / 9f, 0f, 1f) * (wave == 0 ? 1f : 0.7f);
			if (a <= 0f) {
				continue;
			}
			double d = 0.8 + range * f;
			double r = 0.3 + d * 0.62;
			Vec3 c = fx.pos.add(n.scale(d));
			FateGlyphs.ring(c, b[0], b[1], r, 0.06 + 0.04 * (1 - f), 36, col, a, true);
			FateGlyphs.disc(c, b[0], b[1], r, r, 28, col, 0.12f * a);
			if (wave == 0) {
				FateGlyphs.band(c, b[0], b[1], r * 0.8, 8, r * 0.22, 0.02, t * 0.2, col, a, fx.seed);
			}
		}
		// the cone's edges flash and the ankh stands at its mouth
		float edge = Mth.clamp(1f - t / 6f, 0f, 1f);
		if (edge > 0f) {
			Vec3 far = fx.pos.add(n.scale(range * 0.9));
			for (int k = 0; k < 8; k++) {
				double ang = k * Math.PI / 4 + 0.2;
				Vec3 rim = FateGlyphs.at(far, b[0], b[1], Math.cos(ang) * range * 0.55, Math.sin(ang) * range * 0.55);
				FateGlyphs.glowBeam(fx.pos.add(n.scale(0.6)), rim, 0.025, col, edge * 0.6f);
			}
		}
		float ak = Mth.clamp(1f - t / life, 0f, 1f);
		FateGlyphs.ankh(fx.pos.add(n.scale(0.9)), b[0], b[1], 0.9, 0.05, col, ak);
	}

	// ---------------------------------------------------------------- the Avatar of Order

	private static void slam(Fx fx, float t, int life, float size, int col) {
		float a = env(t, life, 1f, 10f);
		double r = size * easeOut(t / 3f);
		Vec3 c = fx.pos.add(0, 0.08, 0);
		double spin = t * 0.05;
		Vec3 u = EAST.scale(Math.cos(spin)).add(SOUTH.scale(Math.sin(spin)));
		Vec3 v = SOUTH.scale(Math.cos(spin)).subtract(EAST.scale(Math.sin(spin)));
		FateGlyphs.ring(c, u, v, r, 0.12, 64, col, a, true);
		FateGlyphs.ring(c, u, v, r * 0.86, 0.06, 60, col, a * 0.8f, true);
		FateGlyphs.band(c, u, v, r * 0.93, 20, 0.8, 0.03, spin, col, a, fx.seed);
		FateGlyphs.ankh(c.add(0, 0.01, 0), fx.dir.lengthSqr() > 0 ? new Vec3(fx.dir.z, 0, -fx.dir.x).normalize() : EAST,
				fx.dir.lengthSqr() > 0 ? new Vec3(fx.dir.x, 0, fx.dir.z).normalize() : SOUTH, r * 0.85, 0.08, col, a);
		FateGlyphs.disc(c, EAST, SOUTH, r, r, 40, col, 0.12f * a);
		float shock = Mth.clamp(t / 8f, 0f, 1f);
		if (shock < 1f) {
			FateGlyphs.ring(c, EAST, SOUTH, size * (0.4 + 1.1 * easeOut(shock)), 0.18 * (1 - shock) + 0.03, 64, 0xFFF6D8, 1f - shock, true);
		}
		FateGlyphs.wall(c, r * 0.95, 3.0 * (1 - shock) + 0.5, 48, col, 0.3f * a);
	}

	private static void rise(Fx fx, float t, int life, float size, int col) {
		float a = env(t, life, 2f, 10f);
		for (int k = 0; k < 8; k++) {
			float h = (t * 2.2f + k * 5f) % 40f;
			FateGlyphs.ring(fx.pos.add(0, h, 0), EAST, SOUTH, size * (1f - h / 60f), 0.08, 40, col, a * (1f - h / 40f), true);
		}
	}

	// ---------------------------------------------------------------- the landing

	private static void landing(Fx fx, float t, int life, float size, int col) {
		float a = env(t, life, 1f, 14f);
		Vec3 c = fx.pos;
		Vec3 fwd = new Vec3(fx.dir.x, 0, fx.dir.z);
		fwd = fwd.lengthSqr() < 1.0e-6 ? SOUTH : fwd.normalize();
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		double s = size * (0.6 + 0.4 * easeOut(t / 4f));
		// the ankh flat on the ground, its loop ahead of him
		FateGlyphs.ankh(c.add(fwd.scale(s * 0.1)), right, fwd, s, 0.05 + 0.02 * size, col, a);
		FateGlyphs.disc(c, EAST, SOUTH, s * 0.4, s * 0.4, 24, 0xFFF6D8, 0.2f * a);
		// the rune ring racing out
		float f = easeOut(t / 12f);
		double r = 0.6 + size * 1.7 * f;
		float ra = Mth.clamp(1f - t / 16f, 0f, 1f);
		FateGlyphs.ring(c, EAST, SOUTH, r, 0.06, 48, col, ra, true);
		FateGlyphs.band(c, EAST, SOUTH, r * 0.9, 12, 0.45 + 0.15 * size, 0.018, t * 0.03, col, ra, fx.seed);
		FateGlyphs.ring(c, EAST, SOUTH, r * 0.55, 0.03, 36, col, ra * 0.7f, true);
		FateGlyphs.wall(c, r * 0.92, 0.9 * (1 - f) + 0.1, 40, col, 0.28f * ra);
	}
}
