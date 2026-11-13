package com.projecthero.mod.client.raven;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.beamclash.BeamClashClient;
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
 * v0.17.3 Raven: her effects on the client (revamp). The server's {@link RavenFxPayload}s become timed effects drawn after the
 * entities: claws (Shadow Step, Shadow Binding), tentacles (the Wrath's red, Telekinetic Crush's violet grip), slam rings, the shadow
 * streak, the healing thread, the pool she sinks into, bursts, the Dark Energy Wave's charge and rolling front, Demonic Ascension's
 * eruption, the Void Vortex, the Binding circle, Soul Detonation's marks, Soul Devour's beam, Dimensional Rift portals, the Astral
 * Sanctuary dome, Soul Restoration's channel and the Veil swallowing her. Drawn every frame from the synced state: the Soul Shield
 * bubble and the Telekinesis thread. Each client tick: the dark wisps off a Raven's hands, the black energy round a held creature and
 * the souls flowing down a drain beam. Kept to her dark-purple / black look; no glow on her face.
 */
public final class RavenFx {
	private record Fx(int kind, int entity, Vec3 a, Vec3 b, int ticks, long start, long seed) {
	}

	private static final List<Fx> ACTIVE = new ArrayList<>();
	private static final int CLAW_DARK = 0xF00A0410;
	private static final int VIOLET_GLOW = 0x806A2BD0;
	private static final int LILAC_GLOW = 0x90B48CF0;
	private static final int RED_GLOW = 0xA0E01830;
	private static final int SHADOW = 0xC00A0410;

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
		if (p.kind() == RavenFxPayload.BEAM_END) {
			ACTIVE.removeIf(f -> f.kind == RavenFxPayload.BEAM && f.entity == p.entity());
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
		List<Fx> beams = new ArrayList<>();
		synchronized (RavenFx.class) {
			ACTIVE.removeIf(f -> now - f.start > f.ticks + 20 || now < f.start - 5);
			for (Fx f : ACTIVE) {
				if (f.kind == RavenFxPayload.BEAM) {
					beams.add(f);
				}
			}
		}
		RandomSource rnd = mc.level.random;
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
					Vec3 look = pl.getViewVector(1f);
					float hy = pl.getYRot() * Mth.DEG_TO_RAD;
					Vec3 side2 = new Vec3(-Mth.cos(hy), 0, -Mth.sin(hy));
					h = pl.getEyePosition().add(look.scale(0.9)).add(side2.scale(0.55 * side)).add(0, -0.7, 0);
				}
				if (casting && rnd.nextInt(2) == 0) {
					mc.level.addParticle(violet, h.x, h.y, h.z, 0, 0.03, 0);
				} else {
					mc.level.addParticle(voidDust, h.x + (rnd.nextDouble() - 0.5) * 0.08, h.y + (rnd.nextDouble() - 0.5) * 0.08,
							h.z + (rnd.nextDouble() - 0.5) * 0.08, 0, 0.02, 0);
				}
			}
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
		// souls drawn down Soul Devour's beam
		for (Fx f : beams) {
			Entity caster = mc.level.getEntity(f.entity);
			Entity t = mc.level.getEntity((int) f.a.x);
			if (caster == null || t == null || BeamClashClient.hides(caster)) {
				continue;
			}
			Vec3 from = t.position().add(0, t.getBbHeight() * 0.55, 0);
			Vec3 to = hand(caster, 1f);
			Vec3 d = to.subtract(from).scale(0.09);
			Vec3 at = from.lerp(to, rnd.nextDouble() * 0.3);
			mc.level.addParticle(ParticleTypes.SOUL, at.x, at.y, at.z, d.x, d.y, d.z);
		}
	}

	/** Where her casting hand is (a little ahead of her chest, along her look). */
	private static Vec3 hand(Entity e, float partial) {
		Vec3 look = e.getViewVector(partial);
		return e.getPosition(partial).add(0, e.getBbHeight() * 0.68, 0).add(look.scale(0.6));
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

		// the Soul Shield bubbles and the Telekinesis threads
		for (Player pl : mc.level.players()) {
			RavenState s = Raven.peek(pl);
			if (s == null || !s.hasPower) {
				continue;
			}
			if (Raven.guarding(pl) && !pl.isInvisible()) {
				guard(buffers, last, pl, s, cam, partial, time, now);
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
				case RavenFxPayload.CLAWS -> claws(buffers, last, cam, f, age);
				case RavenFxPayload.TENTACLE -> tentacle(mc, buffers, last, cam, f, age, partial, time);
				case RavenFxPayload.WAVE -> slamRing(buffers, last, cam, f, age);
				case RavenFxPayload.STREAK -> streak(buffers, last, cam, f, age, time);
				case RavenFxPayload.HEAL -> heal(mc, buffers, last, cam, f, age, partial, time);
				case RavenFxPayload.SINK -> sink(buffers, last, cam, f, age);
				case RavenFxPayload.GUARD_UP -> guardUp(mc, buffers, last, cam, f, age, partial);
				case RavenFxPayload.BURST -> burst(buffers, last, cam, f, age, time);
				case RavenFxPayload.CHARGE -> charge(mc, buffers, last, cam, f, age, partial, time);
				case RavenFxPayload.WAVE_FRONT -> waveFront(buffers, last, cam, f, age, time);
				case RavenFxPayload.ASCEND -> ascend(buffers, last, cam, f, age, time);
				case RavenFxPayload.VORTEX -> vortex(buffers, last, cam, f, age, time);
				case RavenFxPayload.BIND -> bind(buffers, last, cam, f, age, time);
				case RavenFxPayload.MARK -> mark(mc, buffers, last, cam, f, age, partial, time);
				case RavenFxPayload.BEAM -> beam(mc, buffers, last, cam, f, age, partial, time);
				case RavenFxPayload.RIFT -> rift(buffers, last, cam, f, age, time);
				case RavenFxPayload.SANCTUARY -> sanctuary(buffers, last, cam, f, age, time);
				case RavenFxPayload.CHANNEL -> channel(mc, buffers, last, cam, f, age, partial, time);
				case RavenFxPayload.VEIL -> veil(mc, buffers, last, cam, f, age, partial);
				default -> {
				}
			}
		}
	}

	// ---------------------------------------------------------------- the Soul Shield (from the synced state)

	/** A close violet bubble round her, hexagon seams crawling over it, dimmer as it's worn down. */
	private static void guard(MultiBufferSource buffers, PoseStack.Pose pose, Player pl, RavenState s, Vec3 cam, float partial, float time, long now) {
		double sc = pl.getScale();
		double r = RavenConfig.GUARD_RADIUS * sc;
		Vec3 c = pl.getPosition(partial).add(0, pl.getBbHeight() * 0.5, 0).subtract(cam);
		float full = (Raven.ascended(pl) ? RavenConfig.GUARD_ABSORB_ASCENDED : RavenConfig.GUARD_ABSORB) * Raven.shieldMult(pl);
		float hp = Mth.clamp(s.guardHp / Math.max(1f, full), 0f, 1f);
		long left = s.guardUntil - now;
		float flicker = left < 20 && (now / 2) % 2 == 0 ? 0.55f : 1f;
		float pulse = (0.85f + 0.15f * Mth.sin(time * 0.3f)) * flicker;
		RavenDraw.sphere(buffers.getBuffer(RavenDomeRenderType.VEIL), pose, c, r, 10, 18, RavenDraw.fade(0x40402080, pulse * (0.45f + 0.55f * hp)), true);
		// two latitude seams sliding round, plus a bright rim facing the camera (the barrier's edge)
		for (int band = 0; band < 3; band++) {
			double t = ((time * 0.03 + band / 3.0) % 1.0) * Math.PI - Math.PI / 2;
			double rr = r * Math.cos(t);
			RavenDraw.ring(buffers.getBuffer(RavenDomeRenderType.SEAM), pose, c.add(0, r * Math.sin(t), 0), rr * 0.98, rr * 1.02 + 0.02, 28,
					RavenDraw.fade(LILAC_GLOW, 0.5f * pulse * (0.4f + 0.6f * hp)), true);
		}
		RavenDraw.sphere(buffers.getBuffer(RavenDomeRenderType.SEAM), pose, c, r * 1.02, 8, 16, RavenDraw.fade(0x182A1050, pulse), true);
	}

	// ---------------------------------------------------------------- the effects

	/** Black claws curling up out of the ground round a creature, holding, then sinking back. */
	private static void claws(MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age) {
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
			clawAt(buffers, pose, cam, c, ang, rad, hh, 0.16 + rnd.nextDouble() * 0.05, k);
		}
		RavenDraw.ring(RavenDraw.shadow(buffers), pose, c.add(0, 0.02, 0).subtract(cam), 0.0, w * 0.5 + 0.7, 20, RavenDraw.fade(0xC00A0410, k), false);
	}

	/** One claw rising at angle {@code ang}, {@code rad} out from {@code c}, hooking in over the middle. */
	private static void clawAt(MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Vec3 c, double ang, double rad, double hh, double thick, float k) {
		Vec3 root = c.add(Math.cos(ang) * rad, 0.0, Math.sin(ang) * rad);
		Vec3 inward = new Vec3(-Math.cos(ang), 0, -Math.sin(ang));
		List<Vec3> pts = new ArrayList<>();
		for (int s = 0; s <= 5; s++) {
			double t = s / 5.0;
			pts.add(root.add(0, hh * t, 0).add(inward.scale(Math.min(rad, 1.2) * 0.85 * t * t)).subtract(cam));
		}
		RavenDraw.claw(RavenDraw.shadow(buffers), pose, pts, thick, CLAW_DARK);
		RavenDraw.ribbon(RavenDraw.glow(buffers), pose, pts, 0.22, 0.02, RavenDraw.fade(VIOLET_GLOW, 0.5f * k), RavenDraw.fade(VIOLET_GLOW, 0.1f), true);
	}

	/** A dark tentacle reaching to a creature: out, wrapped, then pulled away. Red for the Wrath, violet for Telekinetic Crush. */
	private static void tentacle(Minecraft mc, MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age,
			float partial, float time) {
		Entity t = mc.level.getEntity(f.entity);
		if (t == null) {
			return;
		}
		boolean grip = f.b.x > 0.5;
		float reach = Mth.clamp(age / (grip ? 4f : 5f), 0f, 1f);
		float fade = Mth.clamp(1f - (age - f.ticks) / 6f, 0f, 1f);
		if (fade <= 0f) {
			return;
		}
		Vec3 from = f.a;
		Vec3 to = t.getPosition(partial).add(0, t.getBbHeight() * 0.5, 0);
		Vec3 mid = from.lerp(to, 0.5).add(0, from.distanceTo(to) * (grip ? 0.2 : 0.35), 0);
		List<Vec3> pts = new ArrayList<>();
		int n = 16;
		RandomSource rnd = RandomSource.create(f.seed);
		double phase = rnd.nextDouble() * 6;
		for (int i = 0; i <= n; i++) {
			double k = i / (double) n * reach;
			Vec3 a = from.lerp(mid, k), b = mid.lerp(to, k);
			Vec3 p = a.lerp(b, k);
			double wob = Math.sin(k * Math.PI) * (grip ? 0.3 : 0.6);
			p = p.add(Math.sin(time * 0.35 + k * 10 + phase) * wob, Math.cos(time * 0.3 + k * 8 + phase) * wob * 0.5, Math.cos(time * 0.32 + k * 9) * wob);
			pts.add(p.subtract(cam));
		}
		double w0 = grip ? 0.22 : 0.7, w1 = grip ? 0.08 : 0.12;
		int glow = grip ? VIOLET_GLOW : RED_GLOW;
		RavenDraw.ribbon(RavenDraw.shadow(buffers), pose, pts, w0, w1, RavenDraw.fade(0xF0080208, fade), RavenDraw.fade(0xE0100410, fade), false);
		RavenDraw.ribbon(RavenDraw.glow(buffers), pose, pts, w0 * 1.4, w1 * 1.6, RavenDraw.fade(glow, 0.45f * fade), RavenDraw.fade(glow, 0.15f * fade), true);
		if (reach >= 1f) {
			Vec3 tc = to.subtract(cam);
			double w = t.getBbWidth() * 0.6 + 0.15;
			for (int ring = 0; ring < 3; ring++) {
				double yy = (ring - 1) * t.getBbHeight() * 0.25 + Math.sin(time * 0.3 + ring) * 0.04;
				RavenDraw.ring(RavenDraw.shadow(buffers), pose, tc.add(0, yy, 0), w, w + (grip ? 0.08 : 0.14), 16, RavenDraw.fade(0xE0080208, fade), false);
				if (grip) {
					RavenDraw.ring(RavenDraw.glow(buffers), pose, tc.add(0, yy + 0.01, 0), w + 0.06, w + 0.12, 16, RavenDraw.fade(VIOLET_GLOW, 0.6f * fade), true);
				}
			}
		}
	}

	/** A dark ring spreading on the ground (a slam). */
	private static void slamRing(MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age) {
		float k = Mth.clamp(age / 8f, 0f, 1f);
		float fade = Mth.clamp(1f - (age - 4f) / Math.max(1f, f.ticks - 4f), 0f, 1f);
		if (fade <= 0f) {
			return;
		}
		double r = f.b.x * (0.15 + 0.85 * (1 - (1 - k) * (1 - k)));
		Vec3 c = f.a.subtract(cam);
		RavenDraw.ring(RavenDraw.shadow(buffers), pose, c, Math.max(0, r - 0.6), r, 32, RavenDraw.fade(0xB00A0410, fade), false);
		RavenDraw.ring(RavenDraw.glow(buffers), pose, c.add(0, 0.03, 0), Math.max(0, r - 0.25), r + 0.05, 32, RavenDraw.fade(VIOLET_GLOW, 0.8f * fade), true);
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
		// (the thread follows its target; Telekinetic Pull sends her own id with a lever as the end, so only a target near b counts)
		Vec3 to = t != null && t.position().distanceToSqr(f.b) < 9
				? t.getPosition(partial).add(0, t.getBbHeight() * 0.6, 0) : f.b;
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

	/** The Soul Shield snapping up: a bright shell shrinking onto her and a ring flashing out at her feet. */
	private static void guardUp(Minecraft mc, MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age, float partial) {
		Entity e = mc.level.getEntity(f.entity);
		Vec3 c = e != null ? e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0) : f.a;
		float k = Mth.clamp(age / f.ticks, 0f, 1f);
		float fade = 1f - k;
		double r = RavenConfig.GUARD_RADIUS * (1.8 - 0.8 * Math.sqrt(k));
		RavenDraw.sphere(buffers.getBuffer(RavenDomeRenderType.SEAM), pose, c.subtract(cam), r, 8, 16, RavenDraw.fade(0x50A070F0, fade), true);
		Vec3 feet = (e != null ? e.getPosition(partial) : f.a.subtract(0, 1, 0)).add(0, 0.04, 0).subtract(cam);
		double rr = 0.5 + 2.2 * k;
		RavenDraw.ring(RavenDraw.glow(buffers), pose, feet, rr - 0.18, rr, 32, RavenDraw.fade(LILAC_GLOW, fade), true);
	}

	/** A ball of dark energy bursting: a shell flashing out and fading, with spikes thrown off it. */
	private static void burst(MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age, float time) {
		float k = Mth.clamp(age / f.ticks, 0f, 1f);
		if (k >= 1f) {
			return;
		}
		boolean wild = f.b.y > 0.5;
		float fade = 1f - k;
		double r = f.b.x * (0.25 + 0.75 * (1 - (1 - k) * (1 - k)));
		Vec3 c = f.a.subtract(cam);
		RavenDraw.sphere(buffers.getBuffer(RavenDomeRenderType.VEIL), pose, c, r * 0.8, 8, 14, RavenDraw.fade(0xB00A0410, fade * fade), true);
		RavenDraw.sphere(buffers.getBuffer(RavenDomeRenderType.SEAM), pose, c, r, 8, 14, RavenDraw.fade(wild ? 0x60C04070 : 0x608040D0, fade), true);
		RandomSource rnd = RandomSource.create(f.seed);
		int spikes = 7 + (int) Math.min(8, f.b.x * 2);
		for (int i = 0; i < spikes; i++) {
			Vec3 dir = new Vec3(rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 1.4 - 0.4, rnd.nextDouble() * 2 - 1).normalize();
			Vec3 a = c.add(dir.scale(r * 0.4));
			Vec3 b = c.add(dir.scale(r * (1.1 + 0.5 * rnd.nextDouble())));
			RavenDraw.spike(RavenDraw.shadow(buffers), pose, a, b, 0.07 * fade + 0.01, RavenDraw.fade(CLAW_DARK, fade));
		}
		RavenDraw.ring(RavenDraw.glow(buffers), pose, c, r * 0.95, r * 1.1, 28, RavenDraw.fade(wild ? RED_GLOW : LILAC_GLOW, fade), true);
	}

	/** Dark energy gathering at her hands (b.y 0), or a ring tightening round her (b.y 1: Soul Detonation). */
	private static void charge(Minecraft mc, MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age, float partial, float time) {
		Entity e = mc.level.getEntity(f.entity);
		if (e == null || age > f.ticks + 2) {
			return;
		}
		float k = Mth.clamp(age / f.ticks, 0f, 1f);
		if (f.b.y > 0.5) {
			Vec3 feet = e.getPosition(partial).add(0, 0.05, 0).subtract(cam);
			double r = f.b.x * (1 - k) + 0.6;
			RavenDraw.ring(RavenDraw.shadow(buffers), pose, feet, r - 0.5, r, 40, RavenDraw.fade(SHADOW, 0.8f), false);
			RavenDraw.ring(RavenDraw.glow(buffers), pose, feet.add(0, 0.02, 0), r - 0.15, r + 0.05, 40, RavenDraw.fade(LILAC_GLOW, 0.5f + 0.5f * k), true);
			// the sigil's spokes turning
			for (int i = 0; i < 6; i++) {
				double ang = time * 0.12 + i * Math.PI / 3;
				Vec3 a = feet.add(Math.cos(ang) * (r - 0.4), 0.02, Math.sin(ang) * (r - 0.4));
				Vec3 b = feet.add(Math.cos(ang + 0.4) * 0.5, 0.02, Math.sin(ang + 0.4) * 0.5);
				List<Vec3> pts = List.of(a, a.lerp(b, 0.5), b);
				RavenDraw.ribbon(RavenDraw.glow(buffers), pose, pts, 0.08, 0.02, RavenDraw.fade(VIOLET_GLOW, 0.7f), RavenDraw.fade(VIOLET_GLOW, 0.2f), true);
			}
			return;
		}
		Vec3 h = hand(e, partial).subtract(cam);
		double core = 0.12 + 0.35 * k;
		RavenDraw.sphere(RavenDraw.shadow(buffers), pose, h, core, 6, 10, RavenDraw.fade(0xE00A0410, 1f), false);
		RavenDraw.sphere(buffers.getBuffer(RavenDomeRenderType.SEAM), pose, h, core * 1.35, 6, 10, RavenDraw.fade(0x507040D0, 0.6f + 0.4f * k), true);
		// streaks of energy drawn into the core
		RandomSource rnd = RandomSource.create(f.seed + (long) age);
		for (int i = 0; i < 6; i++) {
			Vec3 dir = new Vec3(rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1).normalize();
			Vec3 a = h.add(dir.scale(1.3 - 0.6 * k));
			Vec3 b = h.add(dir.scale(core * 1.2));
			RavenDraw.ribbon(RavenDraw.glow(buffers), pose, List.of(a, a.lerp(b, 0.5), b), 0.02, 0.06, RavenDraw.fade(VIOLET_GLOW, 0.2f), RavenDraw.fade(LILAC_GLOW, 0.8f), true);
		}
	}

	/** The Dark Energy Wave: a curved wall of shadow rolling out, crested with violet, leaving a scorched trail. */
	private static void waveFront(MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age, float time) {
		double front = Math.min(RavenConfig.WAVE_RANGE, (age + 1) * RavenConfig.WAVE_SPEED);
		float fade = Mth.clamp(1f - (age - f.ticks) / 6f, 0f, 1f);
		if (fade <= 0f) {
			return;
		}
		Vec3 dir = f.b;
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		double half = 1.5 + (RavenConfig.WAVE_HALF_WIDTH - 1.5) * front / RavenConfig.WAVE_RANGE;
		int n = 14;
		for (int layer = 0; layer < 3; layer++) {
			double back = layer * 0.7;
			double d = Math.max(0.3, front - back);
			double hw = 1.5 + (RavenConfig.WAVE_HALF_WIDTH - 1.5) * d / RavenConfig.WAVE_RANGE;
			Vec3[] bottom = new Vec3[n + 1], top = new Vec3[n + 1];
			for (int i = 0; i <= n; i++) {
				double u = i / (double) n * 2 - 1; // -1 .. 1 across
				double bow = (1 - u * u) * 0.9; // the middle leads
				double hh = (1.9 - Math.abs(u) * 0.9) * (1 - layer * 0.25) * (0.9 + 0.1 * Math.sin(time * 0.6 + u * 5));
				Vec3 base = f.a.add(dir.scale(d + bow)).add(side.scale(u * hw)).add(0, 0.05, 0);
				bottom[i] = base.subtract(cam);
				top[i] = base.add(dir.scale(-0.3)).add(0, hh, 0).subtract(cam);
			}
			float a = fade * (1f - layer * 0.3f);
			for (int i = 0; i < n; i++) {
				RavenDraw.quad(RavenDraw.shadow(buffers), pose, bottom[i], bottom[i + 1], top[i + 1], top[i], RavenDraw.fade(layer == 0 ? 0xD00A0410 : 0x900A0410, a));
			}
			if (layer == 0) {
				List<Vec3> crest = new ArrayList<>();
				for (Vec3 t : top) {
					crest.add(t);
				}
				RavenDraw.ribbon(RavenDraw.glow(buffers), pose, crest, 0.35, 0.35, RavenDraw.fade(LILAC_GLOW, a), RavenDraw.fade(LILAC_GLOW, a), true);
			}
		}
		// the trail behind it
		Vec3 o = f.a.add(0, 0.03, 0);
		List<Vec3> trail = new ArrayList<>();
		for (int i = 0; i <= 8; i++) {
			trail.add(o.add(dir.scale(front * i / 8.0)).subtract(cam));
		}
		RavenDraw.ribbon(RavenDraw.shadow(buffers), pose, trail, 2.6, half * 2, RavenDraw.fade(0x500A0410, fade), RavenDraw.fade(0x800A0410, fade), false);
	}

	/** Demonic Ascension's eruption: a violet shockwave and pillars of dark light rising round her. */
	private static void ascend(MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age, float time) {
		float k = Mth.clamp(age / f.ticks, 0f, 1f);
		if (k >= 1f) {
			return;
		}
		float fade = 1f - k;
		Vec3 c = f.a.add(0, 0.05, 0).subtract(cam);
		double r = 0.5 + 4.5 * (1 - (1 - k) * (1 - k));
		RavenDraw.ring(RavenDraw.shadow(buffers), pose, c, Math.max(0, r - 0.9), r, 40, RavenDraw.fade(SHADOW, fade), false);
		RavenDraw.ring(RavenDraw.glow(buffers), pose, c.add(0, 0.02, 0), Math.max(0, r - 0.3), r + 0.08, 40, RavenDraw.fade(LILAC_GLOW, fade), true);
		for (int i = 0; i < 8; i++) {
			double ang = i * Math.PI / 4 + time * 0.05;
			double pr = 0.9 + 0.4 * k;
			Vec3 base = c.add(Math.cos(ang) * pr, 0, Math.sin(ang) * pr);
			double h = (2.5 + (i % 2) * 0.8) * Math.min(1, k * 3);
			List<Vec3> pts = List.of(base, base.add(0, h * 0.5, 0), base.add(0, h, 0));
			RavenDraw.ribbon(RavenDraw.glow(buffers), pose, pts, 0.28, 0.02, RavenDraw.fade(VIOLET_GLOW, 0.9f * fade), RavenDraw.fade(VIOLET_GLOW, 0.1f), true);
		}
	}

	/** Void Vortex: a spiral of shadow round a black eye, a funnel of rings over it, violet sparks along its arms. */
	private static void vortex(MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age, float time) {
		float grow = Mth.clamp(age / 8f, 0f, 1f);
		float shut = Mth.clamp((f.ticks - age) / 4f, 0f, 1f);
		float k = grow * shut;
		if (k <= 0.01f) {
			return;
		}
		Vec3 c = f.a.subtract(cam);
		double R = f.b.x * 0.55 * k;
		double spin = time * 0.35;
		// the disc: spiral arms
		for (int arm = 0; arm < 4; arm++) {
			List<Vec3> pts = new ArrayList<>();
			for (int i = 0; i <= 18; i++) {
				double t = i / 18.0;
				double ang = spin + arm * Math.PI / 2 + t * 4.2;
				double rr = R * (1 - t) + 0.25;
				pts.add(c.add(Math.cos(ang) * rr, -0.9 + t * 0.6, Math.sin(ang) * rr));
			}
			RavenDraw.ribbon(RavenDraw.shadow(buffers), pose, pts, 0.9 * k, 0.15, RavenDraw.fade(0x800A0410, k), RavenDraw.fade(0xF00A0410, k), false);
			RavenDraw.ribbon(RavenDraw.glow(buffers), pose, pts, 0.25 * k, 0.05, RavenDraw.fade(VIOLET_GLOW, 0.3f * k), RavenDraw.fade(LILAC_GLOW, 0.9f * k), true);
		}
		// the funnel
		for (int i = 0; i < 5; i++) {
			double y = -0.9 + i * 0.55 + Math.sin(time * 0.2 + i) * 0.05;
			double rr = (0.35 + i * 0.32) * k;
			RavenDraw.ring(RavenDraw.shadow(buffers), pose, c.add(0, y, 0), rr, rr + 0.12, 20, RavenDraw.fade(0xB00A0410, k), false);
			RavenDraw.ring(RavenDraw.glow(buffers), pose, c.add(0, y + 0.01, 0), rr + 0.1, rr + 0.16, 20, RavenDraw.fade(VIOLET_GLOW, 0.5f * k), true);
		}
		RavenDraw.sphere(RavenDraw.shadow(buffers), pose, c.add(0, -0.4, 0), 0.45 * k, 6, 10, 0xFF050208, false);
	}

	/** Shadow Binding: a pool of shadow with a glowing rim, and claws curling up round the edge. */
	private static void bind(MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age, float time) {
		float grow = Mth.clamp(age / 5f, 0f, 1f);
		float sink = Mth.clamp((age - f.ticks) / 8f, 0f, 1f);
		float k = grow * (1f - sink);
		if (k <= 0.01f) {
			return;
		}
		double r = f.b.x * (0.3 + 0.7 * grow);
		Vec3 c = f.a.subtract(cam);
		RavenDraw.ring(RavenDraw.shadow(buffers), pose, c, 0, r, 36, RavenDraw.fade(0xA0050208, k), false);
		RavenDraw.ring(RavenDraw.glow(buffers), pose, c.add(0, 0.02, 0), r - 0.2, r + 0.05, 36, RavenDraw.fade(LILAC_GLOW, 0.8f * k * (0.8f + 0.2f * Mth.sin(time * 0.4f))), true);
		RavenDraw.ring(RavenDraw.glow(buffers), pose, c.add(0, 0.02, 0), r * 0.55 - 0.06, r * 0.55, 30, RavenDraw.fade(VIOLET_GLOW, 0.5f * k), true);
		RandomSource rnd = RandomSource.create(f.seed);
		int n = 10;
		for (int i = 0; i < n; i++) {
			double ang = Math.PI * 2 * i / n + rnd.nextDouble() * 0.3;
			double hh = (1.0 + rnd.nextDouble() * 0.9) * k;
			clawAt(buffers, pose, cam, f.a, ang, r * 0.92, hh, 0.13, k);
		}
	}

	/** Soul Detonation's mark: a turning violet sigil over the creature, pulsing faster toward the blast. */
	private static void mark(Minecraft mc, MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age, float partial, float time) {
		Entity e = mc.level.getEntity(f.entity);
		if (e == null || age > f.ticks) {
			return;
		}
		float k = Mth.clamp(age / f.ticks, 0f, 1f);
		Vec3 c = e.getPosition(partial).add(0, e.getBbHeight() + 0.45, 0).subtract(cam);
		float beat = 0.6f + 0.4f * Mth.sin(time * (0.4f + k * 1.6f));
		double r = 0.45 + 0.1 * beat;
		// a vertical sigil facing the camera: rings in the plane of the camera
		Vec3 toCam = c.scale(-1).normalize();
		Vec3 right = toCam.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 up = right.cross(toCam).normalize();
		List<Vec3> ring = new ArrayList<>();
		for (int i = 0; i <= 24; i++) {
			double ang = i * Math.PI * 2 / 24;
			ring.add(c.add(right.scale(Math.cos(ang) * r)).add(up.scale(Math.sin(ang) * r)));
		}
		RavenDraw.ribbon(RavenDraw.shadow(buffers), pose, ring, 0.12, 0.12, 0xE00A0410, 0xE00A0410, false);
		RavenDraw.ribbon(RavenDraw.glow(buffers), pose, ring, 0.07, 0.07, RavenDraw.fade(LILAC_GLOW, beat), RavenDraw.fade(LILAC_GLOW, beat), true);
		for (int i = 0; i < 3; i++) {
			double ang = time * 0.15 + i * Math.PI * 2 / 3;
			Vec3 a = c.add(right.scale(Math.cos(ang) * r)).add(up.scale(Math.sin(ang) * r));
			Vec3 b = c.add(right.scale(Math.cos(ang + 2.1) * r)).add(up.scale(Math.sin(ang + 2.1) * r));
			RavenDraw.ribbon(RavenDraw.glow(buffers), pose, List.of(a, a.lerp(b, 0.5), b), 0.04, 0.04, RavenDraw.fade(VIOLET_GLOW, beat), RavenDraw.fade(VIOLET_GLOW, beat), true);
		}
	}

	/** Soul Devour's drain: a twisting black beam from the creature to her hand, a violet strand coiling round it. */
	private static void beam(Minecraft mc, MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age, float partial, float time) {
		Entity caster = mc.level.getEntity(f.entity);
		Entity t = mc.level.getEntity((int) f.a.x);
		if (caster == null || t == null || age > f.ticks || BeamClashClient.hides(caster)) {
			return;
		}
		boolean me = caster == mc.player && mc.options.getCameraType().isFirstPerson();
		Vec3 from = me ? caster.getEyePosition(partial).add(caster.getViewVector(partial).scale(0.8)).add(0, -0.45, 0) : hand(caster, partial);
		Vec3 to = t.getPosition(partial).add(0, t.getBbHeight() * 0.55, 0);
		float open = Mth.clamp(age / 3f, 0f, 1f);
		Vec3 axis = to.subtract(from);
		double len = axis.length();
		Vec3 dir = axis.normalize();
		Vec3 ref = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
		Vec3 s1 = dir.cross(ref).normalize(), s2 = dir.cross(s1).normalize();
		int n = Math.max(8, (int) (len * 3));
		List<Vec3> core = new ArrayList<>(), strand = new ArrayList<>(), strand2 = new ArrayList<>();
		for (int i = 0; i <= n; i++) {
			double k = i / (double) n * open;
			Vec3 p = from.add(axis.scale(k));
			double w = Math.sin(k * Math.PI) * 0.12;
			core.add(p.add(s1.scale(Math.sin(time * 0.5 + k * 9) * w)).subtract(cam));
			double ang = -time * 0.6 + k * len * 2.2;
			strand.add(p.add(s1.scale(Math.cos(ang) * 0.22)).add(s2.scale(Math.sin(ang) * 0.22)).subtract(cam));
			strand2.add(p.add(s1.scale(Math.cos(ang + Math.PI) * 0.22)).add(s2.scale(Math.sin(ang + Math.PI) * 0.22)).subtract(cam));
		}
		RavenDraw.ribbon(RavenDraw.shadow(buffers), pose, core, 0.16, 0.3, 0xE0080208, 0xF0080208, false);
		RavenDraw.ribbon(RavenDraw.glow(buffers), pose, core, 0.32, 0.5, RavenDraw.fade(VIOLET_GLOW, 0.4f), RavenDraw.fade(VIOLET_GLOW, 0.6f), true);
		RavenDraw.ribbon(RavenDraw.glow(buffers), pose, strand, 0.05, 0.07, RavenDraw.fade(LILAC_GLOW, 0.9f), RavenDraw.fade(LILAC_GLOW, 0.6f), true);
		RavenDraw.ribbon(RavenDraw.glow(buffers), pose, strand2, 0.04, 0.06, RavenDraw.fade(VIOLET_GLOW, 0.8f), RavenDraw.fade(VIOLET_GLOW, 0.5f), true);
		// the grip round the victim
		Vec3 tc = to.subtract(cam);
		double w = t.getBbWidth() * 0.7 + 0.1;
		RavenDraw.ring(RavenDraw.glow(buffers), pose, tc, w, w + 0.08, 20, RavenDraw.fade(LILAC_GLOW, 0.6f + 0.3f * Mth.sin(time * 0.5f)), true);
	}

	/** A Dimensional Rift: a tall oval tear, black inside with turning violet rings, opening and closing. */
	private static void rift(MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age, float time) {
		float open = Mth.clamp(age / 4f, 0f, 1f);
		float shut = Mth.clamp((f.ticks - age) / 5f, 0f, 1f);
		float k = open * shut;
		if (k <= 0.01f) {
			return;
		}
		Vec3 c = f.a.subtract(cam);
		Vec3 face = f.b;
		Vec3 side = new Vec3(-face.z, 0, face.x).normalize();
		Vec3 up = new Vec3(0, 1, 0);
		double hw = 0.75 * k, hh = 1.25;
		int n = 28;
		Vec3[] rim = new Vec3[n];
		for (int i = 0; i < n; i++) {
			double ang = i * Math.PI * 2 / n;
			rim[i] = c.add(side.scale(Math.cos(ang) * hw)).add(up.scale(Math.sin(ang) * hh * (0.6 + 0.4 * k)));
		}
		for (int i = 0; i < n; i++) {
			RavenDraw.quad(RavenDraw.shadow(buffers), pose, c, c, rim[i], rim[(i + 1) % n], 0xF8050208);
		}
		for (int ring = 1; ring <= 3; ring++) {
			double s = ring / 3.6;
			List<Vec3> pts = new ArrayList<>();
			for (int i = 0; i <= 24; i++) {
				double ang = i * Math.PI * 2 / 24 + time * 0.1 * (ring % 2 == 0 ? 1 : -1);
				pts.add(c.add(side.scale(Math.cos(ang) * hw * s)).add(up.scale(Math.sin(ang) * hh * s * (0.6 + 0.4 * k))).add(face.scale(0.01 * ring)));
			}
			RavenDraw.ribbon(RavenDraw.glow(buffers), pose, pts, 0.05, 0.05, RavenDraw.fade(VIOLET_GLOW, 0.6f * k), RavenDraw.fade(VIOLET_GLOW, 0.6f * k), true);
		}
		List<Vec3> edge = new ArrayList<>(List.of(rim));
		edge.add(rim[0]);
		RavenDraw.ribbon(RavenDraw.glow(buffers), pose, edge, 0.22, 0.22, RavenDraw.fade(LILAC_GLOW, k), RavenDraw.fade(LILAC_GLOW, k), true);
	}

	/** Astral Sanctuary: a dome of see-through shadow with violet seams crawling up it and a glowing ring on the ground. */
	private static void sanctuary(MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age, float time) {
		float grow = Mth.clamp(age / 8f, 0f, 1f);
		float shut = Mth.clamp((f.ticks - age) / 10f, 0f, 1f);
		float k = grow * shut;
		if (k <= 0.01f) {
			return;
		}
		double r = f.b.x * (1 - (1 - grow) * (1 - grow));
		Vec3 c = f.a.subtract(cam);
		float pulse = 0.85f + 0.15f * Mth.sin(time * 0.2f);
		hemisphere(buffers.getBuffer(RavenDomeRenderType.VEIL), pose, c, r, 8, 28, RavenDraw.fade(0x4A12061E, pulse * k), true);
		for (int band = 0; band < 4; band++) {
			double t = ((time * 0.015 + band / 4.0) % 1.0) * Math.PI / 2;
			double rr = r * Math.cos(t);
			RavenDraw.ring(buffers.getBuffer(RavenDomeRenderType.SEAM), pose, c.add(0, r * Math.sin(t), 0), rr * 0.99, rr * 1.01 + 0.03, 40,
					RavenDraw.fade(VIOLET_GLOW, 0.65f * pulse * k), true);
		}
		// meridians
		for (int m = 0; m < 8; m++) {
			double ang = m * Math.PI / 4 + time * 0.01;
			List<Vec3> pts = new ArrayList<>();
			for (int i = 0; i <= 10; i++) {
				double t = i / 10.0 * Math.PI / 2;
				pts.add(c.add(Math.cos(ang) * r * Math.cos(t), r * Math.sin(t), Math.sin(ang) * r * Math.cos(t)));
			}
			RavenDraw.ribbon(buffers.getBuffer(RavenDomeRenderType.SEAM), pose, pts, 0.06, 0.02, RavenDraw.fade(VIOLET_GLOW, 0.4f * k), RavenDraw.fade(VIOLET_GLOW, 0.1f * k), true);
		}
		RavenDraw.ring(RavenDraw.glow(buffers), pose, c.add(0, 0.04, 0), r - 0.25, r + 0.05, 48, RavenDraw.fade(LILAC_GLOW, 0.8f * k), true);
	}

	/** The upper half of {@link RavenDraw#sphere}. */
	private static void hemisphere(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, double r, int lat, int lon, int argb, boolean glow) {
		for (int i = 0; i < lat; i++) {
			double t0 = Math.PI / 2 * i / lat, t1 = Math.PI / 2 * (i + 1) / lat;
			for (int j = 0; j < lon; j++) {
				double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
				Vec3 a = c.add(pt(r, t0, p0)), b = c.add(pt(r, t0, p1)), cc = c.add(pt(r, t1, p1)), d = c.add(pt(r, t1, p0));
				if (glow) {
					RavenDraw.glowQuad(vc, pose, a, b, cc, d, argb);
				} else {
					RavenDraw.quad(vc, pose, a, b, cc, d, argb);
				}
			}
		}
	}

	private static Vec3 pt(double r, double theta, double phi) {
		return new Vec3(r * Math.cos(theta) * Math.cos(phi), r * Math.sin(theta), r * Math.cos(theta) * Math.sin(phi));
	}

	/** Soul Restoration's channel: pale rings of light rising round her, a soft column inside them. */
	private static void channel(Minecraft mc, MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age, float partial, float time) {
		Entity e = mc.level.getEntity(f.entity);
		if (e == null || age > f.ticks + 4) {
			return;
		}
		float fade = Mth.clamp((f.ticks + 4 - age) / 6f, 0f, 1f) * Mth.clamp(age / 4f, 0f, 1f);
		Vec3 feet = e.getPosition(partial).subtract(cam);
		for (int i = 0; i < 3; i++) {
			double t = ((time * 0.03 + i / 3.0) % 1.0);
			double y = t * 2.2;
			double r = 0.9 - t * 0.35;
			RavenDraw.ring(RavenDraw.glow(buffers), pose, feet.add(0, y, 0), r - 0.06, r, 28, RavenDraw.fade(0xA0E8E0FF, fade * (1 - (float) t)), true);
		}
		RavenDraw.ring(RavenDraw.glow(buffers), pose, feet.add(0, 0.04, 0), 0.75, 1.0, 28, RavenDraw.fade(0x70C8B8FF, fade), true);
	}

	/** Shadow Veil: a shell of shadow closing over her and swallowing her. */
	private static void veil(Minecraft mc, MultiBufferSource buffers, PoseStack.Pose pose, Vec3 cam, Fx f, float age, float partial) {
		Entity e = mc.level.getEntity(f.entity);
		Vec3 c = e != null ? e.getPosition(partial).add(0, 1.0, 0) : f.a.add(0, 1.0, 0);
		float k = Mth.clamp(age / f.ticks, 0f, 1f);
		if (k >= 1f) {
			return;
		}
		double r = 1.6 * (1 - k) + 0.1;
		RavenDraw.sphere(buffers.getBuffer(RavenDomeRenderType.VEIL), pose, c.subtract(cam), r, 8, 14, RavenDraw.fade(0xD0050208, 1f - k * 0.5f), true);
		RavenDraw.ring(RavenDraw.glow(buffers), pose, c.subtract(cam).add(0, -0.95, 0), r * 0.9, r, 24, RavenDraw.fade(VIOLET_GLOW, 1f - k), true);
	}

	/** An effect of {@code kind} sent for {@code entityId} is still running (the poses ask: charging, drain beam, channel). */
	public static synchronized boolean running(int kind, int entityId, long now) {
		for (Fx f : ACTIVE) {
			if (f.kind == kind && f.entity == entityId && now - f.start <= f.ticks && (kind != RavenFxPayload.CHARGE || f.b.y < 0.5)) {
				return true;
			}
		}
		return false;
	}

	/** For the harness: how many effects are running. */
	public static synchronized int active() {
		return ACTIVE.size();
	}
}
