package com.projecthero.mod.client.raven;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.raven.Raven;
import com.projecthero.mod.raven.RavenConfig;
import com.projecthero.mod.raven.data.RavenState;
import com.projecthero.mod.raven.network.RavenFxPayload;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.3 Raven: her effects on the client. The server's {@link RavenFxPayload}s become timed effects drawn after the entities: the
 * Shadow Grasp / Shadow Step claws, the Wrath's tentacles, the Wave's ring, the shadow streak, the healing thread and the pool she
 * sinks into. Drawn every frame from the synced state too: the Dark Shield dome and the Telekinesis thread to whatever she holds. Each
 * client tick: the dark wisps off a Raven's hands (thicker while she casts), and the black energy round a held creature.
 */
public final class RavenFx {
	private record Fx(int kind, int entity, Vec3 a, Vec3 b, int ticks, long start, long seed) {
	}

	private static final List<Fx> ACTIVE = new ArrayList<>();
	private static final int CLAW_DARK = 0xF00A0410;
	private static final int VIOLET_GLOW = 0x806A2BD0;
	private static final int RED_GLOW = 0xA0E01830;

	private RavenFx() {
	}

	static synchronized void reset() {
		ACTIVE.clear();
	}

	static synchronized void receive(RavenFxPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		ACTIVE.add(new Fx(p.kind(), p.entity(), p.a(), p.b(), p.ticks(), mc.level.getGameTime(), mc.level.random.nextLong()));
	}

	/** For the client harness: adds an effect as if the server sent it. */
	public static void inject(RavenFxPayload p) {
		receive(p);
	}

	// ---------------------------------------------------------------- tick: wisps

	static void tick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			return;
		}
		long now = mc.level.getGameTime();
		synchronized (RavenFx.class) {
			ACTIVE.removeIf(f -> now - f.start > f.ticks + 20 || now < f.start - 5);
		}
		RandomSource rnd = mc.level.random;
		// v0.17.3 tester round ("model is a little strange"): the wisps were a dark cloud of 1.0-size dust plus smoke round each
		// hand, every third tick even idle. Now: small dust, no smoke, a faint mote now and then at rest, a thin stream while casting.
		DustParticleOptions voidDust = new DustParticleOptions(new Vector3f(0.16f, 0.07f, 0.24f), 0.55f);
		DustParticleOptions violet = new DustParticleOptions(new Vector3f(0.48f, 0.22f, 0.78f), 0.5f);
		for (Player pl : mc.level.players()) {
			RavenState s = Raven.peek(pl);
			if (s == null || !s.hasPower || pl.isInvisible() || pl.distanceToSqr(mc.cameraEntity == null ? pl : mc.cameraEntity) > 64 * 64) {
				continue;
			}
			boolean casting = Raven.casting(pl);
			boolean firstPerson = pl == mc.player && mc.options.getCameraType().isFirstPerson();
			if (!casting && rnd.nextInt(10) != 0) {
				continue;
			}
			if (casting && rnd.nextInt(2) != 0) {
				continue;
			}
			if (firstPerson && (!casting || rnd.nextInt(3) != 0)) {
				continue;
			}
			float yaw = Mth.lerp(1f, pl.yBodyRotO, pl.yBodyRot) * Mth.DEG_TO_RAD;
			Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
			Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
			double sc = pl.getScale();
			double handY = (s.meditating ? 0.55 : casting ? 1.05 : 0.75) * sc;
			for (int side = -1; side <= 1; side += 2) {
				Vec3 h = pl.position().add(right.scale(0.38 * side * sc)).add(fwd.scale((casting ? 0.25 : 0.05) * sc)).add(0, handY, 0);
				if (firstPerson) {
					// v0.17.3 tester round: beside the first-person hands, sideways from where she LOOKS (it used the body's yaw, so
					// with the head turned both wisps drifted into one spot ahead of her); higher while the shield's arms are up
					Vec3 look = pl.getViewVector(1f);
					float hy = pl.getYRot() * Mth.DEG_TO_RAD;
					Vec3 side2 = new Vec3(-Mth.cos(hy), 0, -Mth.sin(hy));
					h = s.shield ? pl.getEyePosition().add(look.scale(0.8)).add(side2.scale(0.38 * side)).add(0, -0.3, 0)
							: pl.getEyePosition().add(look.scale(0.9)).add(side2.scale(0.55 * side)).add(0, -0.7, 0);
				}
				if (casting && rnd.nextInt(2) == 0) {
					mc.level.addParticle(violet, h.x, h.y, h.z, 0, 0.03, 0);
				} else {
					mc.level.addParticle(voidDust, h.x + (rnd.nextDouble() - 0.5) * 0.08, h.y + (rnd.nextDouble() - 0.5) * 0.08,
							h.z + (rnd.nextDouble() - 0.5) * 0.08, 0, 0.02, 0);
				}
			}
			// the black energy round what she holds
			if (s.tkTarget >= 0) {
				Entity held = mc.level.getEntity(s.tkTarget);
				if (held != null) {
					for (int i = 0; i < 2; i++) {
						mc.level.addParticle(voidDust, held.getX() + (rnd.nextDouble() - 0.5) * held.getBbWidth() * 1.4,
								held.getY() + rnd.nextDouble() * held.getBbHeight(), held.getZ() + (rnd.nextDouble() - 0.5) * held.getBbWidth() * 1.4,
								0, 0.01, 0);
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- render

	static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		Vec3 cam = ctx.camera().getPosition();
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		float time = now + partial;
		PoseStack.Pose last = pose.last();

		// the Dark Shield domes and the Telekinesis threads
		for (Player pl : mc.level.players()) {
			RavenState s = Raven.peek(pl);
			if (s == null || !s.hasPower) {
				continue;
			}
			if (s.shield) {
				double r = RavenConfig.SHIELD_RADIUS * pl.getScale();
				Vec3 c = pl.getPosition(partial).add(0, 1.0 * pl.getScale(), 0).subtract(cam);
				float pulse = 0.85f + 0.15f * Mth.sin(time * 0.25f);
				float hp = Mth.clamp(s.shieldHp / RavenConfig.SHIELD_ABSORB, 0f, 1f);
				// v0.17.3 tester round ("Raven bubble doesn't show the player model"): no depth writes and no culling
				// (RavenDomeRenderType), so the player and mobs inside always draw, whatever order the batches go in
				RavenDraw.sphere(buffers.getBuffer(RavenDomeRenderType.VEIL), last, c, r, 10, 20,
						RavenDraw.fade(0x5A12061E, pulse * (0.5f + 0.5f * hp)), true);
				// the violet seams: latitude bands that crawl up the dome
				for (int band = 0; band < 4; band++) {
					double t = ((time * 0.02 + band / 4.0) % 1.0) * Math.PI - Math.PI / 2;
					double rr = r * Math.cos(t);
					Vec3 cc = c.add(0, r * Math.sin(t), 0);
					RavenDraw.ring(buffers.getBuffer(RavenDomeRenderType.SEAM), last, cc, rr * 0.99, rr * 1.01 + 0.03, 32,
							RavenDraw.fade(VIOLET_GLOW, 0.6f * pulse), true);
				}
			}
			if (s.tkTarget >= 0) {
				Entity held = mc.level.getEntity(s.tkTarget);
				if (held != null) {
					Vec3 from = pl.getPosition(partial).add(0, 1.1 * pl.getScale(), 0).add(pl.getViewVector(partial).scale(0.5));
					Vec3 to = held.getPosition(partial).add(0, held.getBbHeight() * 0.5, 0);
					List<Vec3> pts = new ArrayList<>();
					for (int i = 0; i <= 12; i++) {
						double k = i / 12.0;
						Vec3 p = from.lerp(to, k);
						double wob = Math.sin(k * Math.PI) * 0.25;
						p = p.add(Math.sin(time * 0.4 + k * 9) * wob, Math.cos(time * 0.33 + k * 7) * wob, Math.sin(time * 0.29 + k * 8) * wob);
						pts.add(p.subtract(cam));
					}
					RavenDraw.ribbon(RavenDraw.glow(buffers), last, pts, 0.12, 0.05, 0x906A2BD0, 0x506A2BD0, true);
					RavenDraw.ribbon(RavenDraw.shadow(buffers), last, pts, 0.06, 0.03, 0xC0100418, 0x80100418, false);
				}
			}
		}

		List<Fx> list;
		synchronized (RavenFx.class) {
			list = new ArrayList<>(ACTIVE);
		}
		for (Fx f : list) {
			float age = time - f.start;
			if (age < 0) {
				continue;
			}
			switch (f.kind) {
				case RavenFxPayload.CLAWS -> claws(mc, buffers, last, cam, f, age);
				case RavenFxPayload.TENTACLE -> tentacle(mc, buffers, last, cam, f, age, partial, time);
				case RavenFxPayload.WAVE -> wave(buffers, last, cam, f, age);
				case RavenFxPayload.STREAK -> streak(buffers, last, cam, f, age, time);
				case RavenFxPayload.HEAL -> heal(mc, buffers, last, cam, f, age, partial, time);
				case RavenFxPayload.SINK -> sink(buffers, last, cam, f, age);
				default -> {
				}
			}
		}
	}

	/** Black claws curling up out of the ground round a creature, holding, then sinking back. */
	private static void claws(Minecraft mc, MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age) {
		boolean burst = f.b.z > 0.5; // Shadow Step: they burst up fast and higher
		float grow = Mth.clamp(age / (burst ? 2.5f : 4f), 0f, 1f);
		float sink = Mth.clamp((age - f.ticks) / 6f, 0f, 1f);
		float k = grow * (1f - sink);
		if (k <= 0.01f) {
			return;
		}
		Vec3 c = f.a;
		double w = Math.max(0.5, f.b.x);
		double h = Math.max(1.0, f.b.y) * (burst ? 1.25 : 0.95);
		RandomSource rnd = RandomSource.create(f.seed);
		int n = burst ? 6 : 7;
		for (int i = 0; i < n; i++) {
			double ang = Math.PI * 2 * i / n + rnd.nextDouble() * 0.4;
			double rad = w * 0.5 + 0.35 + rnd.nextDouble() * 0.15;
			double hh = h * (0.75 + rnd.nextDouble() * 0.5) * k;
			Vec3 root = c.add(Math.cos(ang) * rad, 0.0, Math.sin(ang) * rad);
			Vec3 inward = new Vec3(-Math.cos(ang), 0, -Math.sin(ang));
			List<Vec3> pts = new ArrayList<>();
			for (int s = 0; s <= 5; s++) {
				double t = s / 5.0;
				// up, then hooking in over the creature
				Vec3 p = root.add(0, hh * t, 0).add(inward.scale(rad * 0.85 * t * t));
				pts.add(p.subtract(cam));
			}
			RavenDraw.claw(RavenDraw.shadow(buffers), pose, pts, 0.16 + rnd.nextDouble() * 0.05, CLAW_DARK);
			RavenDraw.ribbon(RavenDraw.glow(buffers), pose, pts, 0.22, 0.02, RavenDraw.fade(VIOLET_GLOW, 0.5f * k), RavenDraw.fade(VIOLET_GLOW, 0.1f), true);
		}
		RavenDraw.ring(RavenDraw.shadow(buffers), pose, c.add(0, 0.02, 0).subtract(cam), 0.0, w * 0.5 + 0.7, 20, RavenDraw.fade(0xC00A0410, k), false);
	}

	/** A dark tentacle reaching from the giant to a creature: out, wrapped, then pulled away. */
	private static void tentacle(Minecraft mc, MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age,
			float partial, float time) {
		Entity t = mc.level.getEntity(f.entity);
		if (t == null) {
			return;
		}
		float reach = Mth.clamp(age / 5f, 0f, 1f);
		float fade = Mth.clamp(1f - (age - f.ticks) / 6f, 0f, 1f);
		if (fade <= 0f) {
			return;
		}
		Vec3 from = f.a;
		Vec3 to = t.getPosition(partial).add(0, t.getBbHeight() * 0.5, 0);
		Vec3 mid = from.lerp(to, 0.5).add(0, from.distanceTo(to) * 0.35, 0);
		List<Vec3> pts = new ArrayList<>();
		int n = 16;
		RandomSource rnd = RandomSource.create(f.seed);
		double phase = rnd.nextDouble() * 6;
		for (int i = 0; i <= n; i++) {
			double k = i / (double) n * reach;
			// a quadratic curve from her to it, writhing
			Vec3 a = from.lerp(mid, k), b = mid.lerp(to, k);
			Vec3 p = a.lerp(b, k);
			double wob = Math.sin(k * Math.PI) * 0.6;
			p = p.add(Math.sin(time * 0.35 + k * 10 + phase) * wob, Math.cos(time * 0.3 + k * 8 + phase) * wob * 0.5, Math.cos(time * 0.32 + k * 9) * wob);
			pts.add(p.subtract(cam));
		}
		RavenDraw.ribbon(RavenDraw.shadow(buffers), pose, pts, 0.7, 0.12, RavenDraw.fade(0xF0080208, fade), RavenDraw.fade(0xE0100410, fade), false);
		RavenDraw.ribbon(RavenDraw.glow(buffers), pose, pts, 0.95, 0.2, RavenDraw.fade(RED_GLOW, 0.45f * fade), RavenDraw.fade(RED_GLOW, 0.15f * fade), true);
		if (reach >= 1f) {
			// coils round it
			Vec3 tc = to.subtract(cam);
			double w = t.getBbWidth() * 0.6 + 0.15;
			for (int ring = 0; ring < 3; ring++) {
				RavenDraw.ring(RavenDraw.shadow(buffers), pose, tc.add(0, (ring - 1) * t.getBbHeight() * 0.25, 0), w, w + 0.14, 16, RavenDraw.fade(0xE0080208, fade), false);
			}
		}
	}

	private static void wave(MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age) {
		float k = Mth.clamp(age / 10f, 0f, 1f);
		float fade = Mth.clamp(1f - (age - 6f) / Math.max(1f, f.ticks - 6f), 0f, 1f);
		if (fade <= 0f) {
			return;
		}
		double r = f.b.x * (0.15 + 0.85 * (1 - (1 - k) * (1 - k)));
		Vec3 c = f.a.subtract(cam);
		RavenDraw.ring(RavenDraw.shadow(buffers), pose, c, Math.max(0, r - 0.9), r, 48, RavenDraw.fade(0xB00A0410, fade), false);
		RavenDraw.ring(RavenDraw.glow(buffers), pose, c.add(0, 0.03, 0), Math.max(0, r - 0.35), r + 0.05, 48, RavenDraw.fade(VIOLET_GLOW, 0.8f * fade), true);
	}

	private static void streak(MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age, float time) {
		float fade = Mth.clamp(1f - age / Math.max(1f, f.ticks), 0f, 1f);
		if (fade <= 0f) {
			return;
		}
		List<Vec3> pts = new ArrayList<>();
		for (int i = 0; i <= 10; i++) {
			double k = i / 10.0;
			pts.add(f.a.lerp(f.b, k).add(0, Math.sin(k * Math.PI * 3 + time * 0.3) * 0.08, 0).subtract(cam));
		}
		RavenDraw.ribbon(RavenDraw.shadow(buffers), pose, pts, 0.2, 1.1, RavenDraw.fade(0x400A0410, fade), RavenDraw.fade(0xE00A0410, fade), false);
		RavenDraw.ribbon(RavenDraw.glow(buffers), pose, pts, 0.3, 1.3, RavenDraw.fade(VIOLET_GLOW, 0.1f * fade), RavenDraw.fade(VIOLET_GLOW, 0.5f * fade), true);
	}

	private static void heal(Minecraft mc, MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age, float partial, float time) {
		float fade = Mth.clamp(1f - age / Math.max(1f, f.ticks), 0f, 1f);
		Entity t = mc.level.getEntity(f.entity);
		Vec3 to = t != null ? t.getPosition(partial).add(0, t.getBbHeight() * 0.6, 0) : f.b;
		List<Vec3> pts = new ArrayList<>();
		for (int i = 0; i <= 14; i++) {
			double k = i / 14.0;
			pts.add(f.a.lerp(to, k).add(0, Math.sin(k * Math.PI) * 0.4 + Math.sin(k * 12 - time * 0.6) * 0.05, 0).subtract(cam));
		}
		RavenDraw.ribbon(RavenDraw.glow(buffers), pose, pts, 0.12, 0.12, RavenDraw.fade(0xC0E8E0FF, fade), RavenDraw.fade(0xC0FFFFFF, fade), true);
	}

	private static void sink(MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age) {
		float grow = Mth.clamp(age / 4f, 0f, 1f);
		float fade = Mth.clamp(1f - (age - f.ticks) / 6f, 0f, 1f);
		if (fade <= 0f) {
			return;
		}
		Vec3 c = f.a.add(0, 0.02, 0).subtract(cam);
		RavenDraw.ring(RavenDraw.shadow(buffers), pose, c, 0.0, 0.95 * grow, 24, RavenDraw.fade(0xF0050208, fade), false);
		RavenDraw.ring(RavenDraw.glow(buffers), pose, c.add(0, 0.01, 0), 0.85 * grow, 1.0 * grow, 24, RavenDraw.fade(VIOLET_GLOW, 0.7f * fade), true);
	}

	/** For the harness: how many effects are running. */
	public static synchronized int active() {
		Iterator<Fx> it = ACTIVE.iterator();
		int n = 0;
		while (it.hasNext()) {
			it.next();
			n++;
		}
		return n;
	}
}
