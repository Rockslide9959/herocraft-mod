package com.projecthero.mod.client.saiyan.fx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.saiyan.moves.SaiyanMoves;
import com.projecthero.mod.saiyan.moves.network.SaiyanFxPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.17.3: the Saiyan moves' look, for every viewer, from {@link SaiyanFxPayload}s: the Ki Beam (a charging ball between the
 * cupped hands, then a white-hot core inside layered glow with spiralling streaks and a boiling end-point), the Spirit Orb
 * (a huge layered ball growing over the raised hands, energy streaming into it, a flight, an expanding blast with a ground
 * ring), punch impacts (a flash and radiating spokes), Instant Step afterimages (a fading ghost of the body and a streak),
 * Dragon Rush and Sky Combo trails, and landing shockwaves. Also keeps the move poses ({@link SaiyanMovePose}).
 * Drawn additively ({@code RenderType.lightning()}) with a solid core ({@code RenderType.debugQuads()}).
 */
public final class SaiyanFxClient {
	private SaiyanFxClient() {
	}

	/** entity id -> {anim, start game time}. */
	static final Map<Integer, long[]> ANIMS = new HashMap<>();

	private record BeamState(long start, float maxCharge, int rgb, long fired, float charge, int fireTicks) {
	}

	private record OrbCharge(long start, float maxCharge, int rgb) {
	}

	private record OrbFly(int owner, Vec3 pos, Vec3 vel, float size, int rgb, long start) {
	}

	private record Timed(Vec3 pos, Vec3 dir, float size, int rgb, long start, int kind) {
	}

	private record Ghost(int entity, Vec3 from, Vec3 to, float yaw, int rgb, long start) {
	}

	private static final class Trail {
		final int rgb;
		final long until;
		final ArrayDeque<Vec3> points = new ArrayDeque<>();

		Trail(int rgb, long until) {
			this.rgb = rgb;
			this.until = until;
		}
	}

	private static final Map<Integer, BeamState> BEAMS = new HashMap<>();
	private static final Map<Integer, OrbCharge> ORB_CHARGES = new HashMap<>();
	private static final Map<Integer, OrbFly> ORBS = new HashMap<>();
	private static final List<Timed> BOOMS = new ArrayList<>();
	private static final List<Timed> IMPACTS = new ArrayList<>();
	private static final List<Timed> WAVES = new ArrayList<>();
	private static final List<Ghost> GHOSTS = new ArrayList<>();
	private static final Map<Integer, Trail> TRAILS = new HashMap<>();

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(SaiyanFxPayload.TYPE, (payload, context) -> context.client().execute(() -> receive(payload)));
		WorldRenderEvents.AFTER_ENTITIES.register(SaiyanFxClient::render);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> tick(mc));
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> reset());
	}

	static void reset() {
		ANIMS.clear();
		BEAMS.clear();
		ORB_CHARGES.clear();
		ORBS.clear();
		BOOMS.clear();
		IMPACTS.clear();
		WAVES.clear();
		GHOSTS.clear();
		TRAILS.clear();
	}

	private static long now() {
		ClientLevel l = Minecraft.getInstance().level;
		return l == null ? 0L : l.getGameTime();
	}

	// ---------------------------------------------------------------- the payloads

	static void receive(SaiyanFxPayload p) {
		long now = now();
		switch (p.kind()) {
			case SaiyanFxPayload.ANIM -> ANIMS.put(p.entity(), new long[] { p.extra(), now });
			case SaiyanFxPayload.BEAM_CHARGE -> BEAMS.put(p.entity(), new BeamState(now, p.f(), p.rgb(), -1L, 0f, 0));
			case SaiyanFxPayload.BEAM_FIRE -> {
				BeamState b = BEAMS.get(p.entity());
				BEAMS.put(p.entity(), new BeamState(b == null ? now : b.start(), b == null ? 30f : b.maxCharge(), p.rgb(), now, p.f(), p.extra()));
			}
			case SaiyanFxPayload.BEAM_END -> BEAMS.remove(p.entity());
			case SaiyanFxPayload.ORB_CHARGE -> ORB_CHARGES.put(p.entity(), new OrbCharge(now, p.f(), p.rgb()));
			case SaiyanFxPayload.ORB_CANCEL -> ORB_CHARGES.remove(p.entity());
			case SaiyanFxPayload.ORB_FLY -> {
				ORB_CHARGES.remove(p.entity());
				ORBS.put(p.extra(), new OrbFly(p.entity(), p.a(), p.b(), p.f(), p.rgb(), now));
			}
			case SaiyanFxPayload.ORB_BOOM -> {
				ORBS.remove(p.extra());
				BOOMS.add(new Timed(p.a(), Vec3.ZERO, p.f(), p.rgb(), now, 0));
				burst(p.a(), p.rgb(), 60, p.f() * 0.25);
			}
			case SaiyanFxPayload.IMPACT -> {
				IMPACTS.add(new Timed(p.a(), p.b(), p.f(), p.rgb(), now, 0));
				burst(p.a(), p.rgb(), (int) (6 + p.f() * 8), 0.25 + p.f() * 0.15);
			}
			case SaiyanFxPayload.AFTERIMAGE -> {
				GHOSTS.add(new Ghost(p.entity(), p.a(), p.b(), p.f(), p.rgb(), now));
				burst(p.a().add(0, 1, 0), p.rgb(), 14, 0.35);
			}
			case SaiyanFxPayload.RUSH -> TRAILS.put(p.entity(), new Trail(p.rgb(), now + Math.max(4, p.extra())));
			case SaiyanFxPayload.SHOCKWAVE -> {
				WAVES.add(new Timed(p.a(), p.b(), p.f(), p.rgb(), now, p.extra()));
				burst(p.a().add(0, 0.2, 0), p.rgb(), 24, 0.6);
			}
			default -> {
			}
		}
	}

	/** Ki sparks thrown out from a point, tinted. */
	private static void burst(Vec3 at, int rgb, int count, double speed) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		DustParticleOptions dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), 1.2f);
		for (int i = 0; i < count; i++) {
			double vx = (Math.random() - 0.5) * 2 * speed, vy = (Math.random() - 0.3) * 2 * speed, vz = (Math.random() - 0.5) * 2 * speed;
			level.addParticle(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : dust, at.x, at.y, at.z, vx, vy, vz);
		}
		if (count >= 10) {
			level.addParticle(ParticleTypes.FLASH, at.x, at.y, at.z, 0, 0, 0);
		}
	}

	// ---------------------------------------------------------------- the tick

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		long now = level.getGameTime();
		ANIMS.entrySet().removeIf(e -> now - e.getValue()[1] > 200);
		BOOMS.removeIf(t -> now - t.start() > 30);
		IMPACTS.removeIf(t -> now - t.start() > 8);
		WAVES.removeIf(t -> now - t.start() > 16);
		GHOSTS.removeIf(g -> now - g.start() > 14);
		BEAMS.entrySet().removeIf(e -> now - e.getValue().start() > 200);
		ORB_CHARGES.entrySet().removeIf(e -> now - e.getValue().start() > 200);
		ORBS.entrySet().removeIf(e -> now - e.getValue().start() > 120);
		for (Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Trail> e = it.next();
			Trail t = e.getValue();
			Entity ent = level.getEntity(e.getKey());
			if (now <= t.until && ent != null) {
				t.points.addLast(ent.position().add(0, ent.getBbHeight() * 0.55, 0));
			}
			if (t.points.size() > 10 || (now > t.until && !t.points.isEmpty())) {
				t.points.pollFirst();
			}
			if (now > t.until && t.points.isEmpty()) {
				it.remove();
			}
		}
		// energy streaming into a charging orb, sparks round a charging beam
		for (Map.Entry<Integer, OrbCharge> e : ORB_CHARGES.entrySet()) {
			Entity ent = level.getEntity(e.getKey());
			if (!(ent instanceof Player p)) {
				continue;
			}
			float charge = Math.min(1f, (now - e.getValue().start()) / e.getValue().maxCharge());
			Vec3 at = SaiyanMoves.orbHold(p, charge);
			for (int i = 0; i < 4; i++) {
				double a = Math.random() * Math.PI * 2, r = 6 + Math.random() * 6, h = Math.random() * 6 - 1;
				Vec3 from = at.add(Math.cos(a) * r, h, Math.sin(a) * r);
				Vec3 v = at.subtract(from).scale(0.06);
				level.addParticle(ParticleTypes.END_ROD, from.x, from.y, from.z, v.x, v.y, v.z);
			}
		}
		for (Map.Entry<Integer, BeamState> e : BEAMS.entrySet()) {
			Entity ent = level.getEntity(e.getKey());
			if (!(ent instanceof Player p) || e.getValue().fired() >= 0) {
				continue;
			}
			Vec3 at = SaiyanMoves.beamFrom(p);
			for (int i = 0; i < 2; i++) {
				Vec3 from = at.add((Math.random() - 0.5) * 1.6, (Math.random() - 0.5) * 1.6, (Math.random() - 0.5) * 1.6);
				Vec3 v = at.subtract(from).scale(0.18);
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, v.x, v.y, v.z);
			}
		}
	}

	// ---------------------------------------------------------------- drawing

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		float t = mc.level.getGameTime() + partial;
		Vec3 cam = ctx.camera().getPosition();
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
		VertexConsumer solid = buffers.getBuffer(RenderType.debugQuads());

		for (Map.Entry<Integer, BeamState> e : BEAMS.entrySet()) {
			Entity ent = mc.level.getEntity(e.getKey());
			if (ent instanceof Player p && !com.projecthero.mod.client.beamclash.BeamClashClient.hides(p)) {
				drawBeam(pose, cam, glow, solid, p, e.getValue(), partial, t);
			}
		}
		for (Map.Entry<Integer, OrbCharge> e : ORB_CHARGES.entrySet()) {
			Entity ent = mc.level.getEntity(e.getKey());
			if (ent instanceof Player p) {
				float charge = Mth.clamp((t - e.getValue().start()) / e.getValue().maxCharge(), 0f, 1f);
				Vec3 at = SaiyanMoves.orbHold(p, charge).add(p.getPosition(partial).subtract(p.position()));
				drawOrb(pose, cam, glow, solid, at, SaiyanMoves.orbSize(charge), e.getValue().rgb(), t);
			}
		}
		for (OrbFly o : ORBS.values()) {
			float age = t - o.start();
			Vec3 at = o.pos().add(o.vel().scale(age));
			drawOrb(pose, cam, glow, solid, at, o.size(), o.rgb(), t);
		}
		for (Timed b : BOOMS) {
			drawBoom(pose, cam, glow, solid, b, t);
		}
		for (Timed i : IMPACTS) {
			drawImpact(pose, cam, glow, i, t);
		}
		for (Timed w : WAVES) {
			drawWave(pose, cam, glow, w, t);
		}
		for (Ghost g : GHOSTS) {
			drawGhost(pose, cam, glow, g, t);
		}
		for (Trail tr : TRAILS.values()) {
			drawTrail(pose, cam, glow, tr);
		}
	}

	private static float r(int rgb) {
		return ((rgb >> 16) & 255) / 255f;
	}

	private static float g(int rgb) {
		return ((rgb >> 8) & 255) / 255f;
	}

	private static float b(int rgb) {
		return (rgb & 255) / 255f;
	}

	/** Where a client-side beam stops: the first block or creature on the look line. */
	private static Vec3[] beamSeg(Player p, float partial, double range) {
		Vec3 pos = p.getPosition(partial);
		Vec3 look = p.getViewVector(partial);
		Vec3 from = pos.add(0, p.getEyeHeight() - 0.45, 0).add(look.scale(0.7));
		Vec3 far = from.add(look.scale(range));
		BlockHitResult bhr = p.level().clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, from, end, new AABB(from, end).inflate(1.2),
				e -> e instanceof LivingEntity && e != p && e.isPickable(), from.distanceToSqr(end));
		if (ehr != null) {
			end = ehr.getLocation();
		}
		return new Vec3[] { from, end };
	}

	private static void drawBeam(PoseStack pose, Vec3 cam, VertexConsumer glow, VertexConsumer solid, Player p, BeamState s, float partial, float t) {
		int rgb = s.rgb();
		if (s.fired() < 0) {
			// charging: a ball of Ki gathering between the cupped hands, flickering and growing
			float charge = Mth.clamp((t - s.start()) / s.maxCharge(), 0f, 1f);
			Vec3 at = p.getPosition(partial).add(0, p.getEyeHeight() - 0.45, 0).add(p.getViewVector(partial).scale(0.7));
			float flick = 0.85f + 0.15f * Mth.sin(t * 2.3f);
			float rad = (0.12f + 0.32f * charge) * flick;
			sphere(pose, cam, solid, at, rad * 0.45f, 1f, 1f, 1f, 1f, 8);
			sphere(pose, cam, glow, at, rad, r(rgb), g(rgb), b(rgb), 0.65f, 10);
			sphere(pose, cam, glow, at, rad * 1.8f, r(rgb), g(rgb), b(rgb), 0.18f, 10);
			return;
		}
		Vec3[] seg = beamSeg(p, partial, 32.0);
		Vec3 d = seg[1].subtract(seg[0]);
		float len = (float) d.length();
		if (len < 0.05f) {
			return;
		}
		float age = t - s.fired();
		float grow = Mth.clamp(age / 3f, 0f, 1f);
		float fade = Mth.clamp((s.fireTicks() - age) / 5f, 0f, 1f);
		float width = (0.55f + 0.35f * s.charge()) * fade;
		float pulse = 1f + 0.08f * Mth.sin(t * 3.1f);
		float reach = len * grow;
		pose.pushPose();
		pose.translate(seg[0].x - cam.x, seg[0].y - cam.y, seg[0].z - cam.z);
		Matrix4f m = pose.last().pose();
		Vector3f dir = new Vector3f((float) d.x, (float) d.y, (float) d.z).normalize();
		Vector3f a = new Vector3f(dir).cross(Math.abs(dir.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
		Vector3f bb = new Vector3f(dir).cross(a).normalize();
		// white-hot core, then the coloured body, then a soft outer glow
		tube(solid, m, dir, a, bb, reach, 0.16f * width * pulse, 12, 0.95f, 0.98f, 1f, 1f);
		tube(glow, m, dir, a, bb, reach, 0.34f * width * pulse, 14, r(rgb), g(rgb), b(rgb), 0.7f);
		tube(glow, m, dir, a, bb, reach, 0.62f * width, 16, r(rgb), g(rgb), b(rgb), 0.22f);
		for (int k = 0; k < 3; k++) {
			helix(glow, m, dir, a, bb, reach, 0.42f * width, t * 0.9f + k * Mth.TWO_PI / 3f, 0.9f, 0.95f, 1f, 0.5f);
		}
		pose.popPose();
		// the boiling end-point and the start at the hands
		float boil = 0.8f + 0.2f * Mth.sin(t * 4.7f);
		Vec3 end = seg[0].add(d.scale(grow));
		sphere(pose, cam, solid, end, 0.45f * width * boil, 1f, 1f, 1f, 1f, 10);
		sphere(pose, cam, glow, end, 1.1f * width * boil, r(rgb), g(rgb), b(rgb), 0.45f, 12);
		sphere(pose, cam, glow, seg[0], 0.5f * width, r(rgb), g(rgb), b(rgb), 0.55f, 10);
		if (Math.random() < 0.6) {
			Minecraft.getInstance().level.addParticle(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, (Math.random() - 0.5) * 0.6,
					Math.random() * 0.4, (Math.random() - 0.5) * 0.6);
		}
	}

	private static void drawOrb(PoseStack pose, Vec3 cam, VertexConsumer glow, VertexConsumer solid, Vec3 at, float size, int rgb, float t) {
		float pulse = 1f + 0.05f * Mth.sin(t * 1.7f);
		// a bright core, the body of the ball, a churning shell and a wide halo
		sphere(pose, cam, solid, at, size * 0.55f * pulse, 0.92f, 0.97f, 1f, 1f, 14);
		sphere(pose, cam, glow, at, size * 0.85f * pulse, r(rgb), g(rgb), b(rgb), 0.55f, 16);
		sphere(pose, cam, glow, at, size * (1.0f + 0.04f * Mth.sin(t * 3.3f)), 0.8f, 0.92f, 1f, 0.28f, 16);
		sphere(pose, cam, glow, at, size * 1.45f, r(rgb), g(rgb), b(rgb), 0.1f, 16);
	}

	private static void drawBoom(PoseStack pose, Vec3 cam, VertexConsumer glow, VertexConsumer solid, Timed bm, float t) {
		float age = t - bm.start();
		float grow = Mth.clamp(age / 8f, 0f, 1f);
		float fade = Mth.clamp(1f - (age - 6f) / 24f, 0f, 1f);
		float rad = bm.size() * (0.2f + 0.8f * (1f - (1f - grow) * (1f - grow)));
		int rgb = bm.rgb();
		if (age < 6f) {
			sphere(pose, cam, solid, bm.pos(), rad * 0.5f, 1f, 1f, 1f, 1f, 14);
		}
		sphere(pose, cam, glow, bm.pos(), rad * 0.85f, 1f, 1f, 1f, 0.5f * fade, 16);
		sphere(pose, cam, glow, bm.pos(), rad, r(rgb), g(rgb), b(rgb), 0.45f * fade, 18);
		ring(pose, cam, glow, bm.pos().add(0, -rad * 0.4, 0), rad * 1.6f * grow, 0.6f, r(rgb), g(rgb), b(rgb), 0.5f * fade);
	}

	private static void drawImpact(PoseStack pose, Vec3 cam, VertexConsumer glow, Timed i, float t) {
		float age = t - i.start();
		float k = Mth.clamp(age / 8f, 0f, 1f);
		float s = i.size() * (0.4f + 0.9f * k);
		float a = (1f - k) * 0.8f;
		int rgb = i.rgb();
		sphere(pose, cam, glow, i.pos(), s * 0.35f, 1f, 1f, 1f, a, 8);
		sphere(pose, cam, glow, i.pos(), s * 0.6f, r(rgb), g(rgb), b(rgb), a * 0.6f, 10);
		// spokes: thin streaks flung out from the hit
		long seed = (long) (i.pos().x * 31 + i.pos().y * 17 + i.pos().z * 7);
		java.util.Random rnd = new java.util.Random(seed);
		pose.pushPose();
		pose.translate(i.pos().x - cam.x, i.pos().y - cam.y, i.pos().z - cam.z);
		Matrix4f m = pose.last().pose();
		for (int n = 0; n < 8; n++) {
			Vector3f dir = new Vector3f(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.4f, rnd.nextFloat() - 0.5f).normalize();
			Vector3f aa = new Vector3f(dir).cross(Math.abs(dir.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
			Vector3f bb = new Vector3f(dir).cross(aa).normalize();
			pose.pushPose();
			Matrix4f mm = new Matrix4f(m).translate(new Vector3f(dir).mul(s * 0.3f));
			tube(glow, mm, dir, aa, bb, s * (0.6f + rnd.nextFloat() * 0.6f), 0.03f * i.size(), 4, 1f, 1f, 1f, a);
			pose.popPose();
		}
		pose.popPose();
	}

	private static void drawWave(PoseStack pose, Vec3 cam, VertexConsumer glow, Timed w, float t) {
		float age = t - w.start();
		float k = Mth.clamp(age / 16f, 0f, 1f);
		float rad = w.size() * (0.2f + 0.8f * k);
		float a = (1f - k) * 0.7f;
		int rgb = w.rgb();
		if (w.kind() == 1) {
			// an upright ring facing the way the rush hit
			ringFacing(pose, cam, glow, w.pos(), w.dir(), rad, 0.35f, r(rgb), g(rgb), b(rgb), a);
		} else {
			ring(pose, cam, glow, w.pos().add(0, 0.1, 0), rad, 0.45f, r(rgb), g(rgb), b(rgb), a);
			ring(pose, cam, glow, w.pos().add(0, 0.1, 0), rad * 0.7f, 0.25f, 1f, 1f, 1f, a * 0.6f);
		}
	}

	private static void drawGhost(PoseStack pose, Vec3 cam, VertexConsumer glow, Ghost gh, float t) {
		float age = t - gh.start();
		float a = Mth.clamp(1f - age / 14f, 0f, 1f) * 0.5f;
		int rgb = gh.rgb();
		// a glowing silhouette left where he stood, and a streak to where he went
		pose.pushPose();
		pose.translate(gh.from().x - cam.x, gh.from().y - cam.y, gh.from().z - cam.z);
		pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-gh.yaw()));
		float grow = 1f + age * 0.03f;
		Matrix4f m = pose.last().pose();
		box(glow, m, -0.25f * grow, 1.5f, -0.25f * grow, 0.25f * grow, 2.0f, 0.25f * grow, r(rgb), g(rgb), b(rgb), a);
		box(glow, m, -0.25f * grow, 0.75f, -0.13f * grow, 0.25f * grow, 1.5f, 0.13f * grow, r(rgb), g(rgb), b(rgb), a);
		box(glow, m, -0.5f * grow, 0.8f, -0.12f * grow, -0.26f * grow, 1.5f, 0.12f * grow, r(rgb), g(rgb), b(rgb), a);
		box(glow, m, 0.26f * grow, 0.8f, -0.12f * grow, 0.5f * grow, 1.5f, 0.12f * grow, r(rgb), g(rgb), b(rgb), a);
		box(glow, m, -0.25f * grow, 0f, -0.12f * grow, -0.01f, 0.75f, 0.12f * grow, r(rgb), g(rgb), b(rgb), a);
		box(glow, m, 0.01f, 0f, -0.12f * grow, 0.25f * grow, 0.75f, 0.12f * grow, r(rgb), g(rgb), b(rgb), a);
		pose.popPose();
		if (age < 6f) {
			Vec3 a0 = gh.from().add(0, 1, 0), a1 = gh.to().add(0, 1, 0);
			Vec3 d = a1.subtract(a0);
			float len = (float) d.length();
			if (len > 0.1f) {
				pose.pushPose();
				pose.translate(a0.x - cam.x, a0.y - cam.y, a0.z - cam.z);
				Vector3f dir = new Vector3f((float) d.x, (float) d.y, (float) d.z).normalize();
				Vector3f aa = new Vector3f(dir).cross(Math.abs(dir.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
				Vector3f bb = new Vector3f(dir).cross(aa).normalize();
				tube(glow, pose.last().pose(), dir, aa, bb, len, 0.06f, 6, 1f, 1f, 1f, (1f - age / 6f) * 0.7f);
				pose.popPose();
			}
		}
	}

	private static void drawTrail(PoseStack pose, Vec3 cam, VertexConsumer glow, Trail tr) {
		Vec3 prev = null;
		int i = 0, n = tr.points.size();
		for (Vec3 pt : tr.points) {
			if (prev != null) {
				float a = (i / (float) n) * 0.6f;
				Vec3 d = pt.subtract(prev);
				float len = (float) d.length();
				if (len > 0.05f) {
					pose.pushPose();
					pose.translate(prev.x - cam.x, prev.y - cam.y, prev.z - cam.z);
					Vector3f dir = new Vector3f((float) d.x, (float) d.y, (float) d.z).normalize();
					Vector3f aa = new Vector3f(dir).cross(Math.abs(dir.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
					Vector3f bb = new Vector3f(dir).cross(aa).normalize();
					tube(glow, pose.last().pose(), dir, aa, bb, len, 0.12f + 0.3f * i / n, 8, r(tr.rgb), g(tr.rgb), b(tr.rgb), a);
					pose.popPose();
				}
			}
			prev = pt;
			i++;
		}
	}

	// ---------------------------------------------------------------- geometry

	private static void sphere(PoseStack pose, Vec3 cam, VertexConsumer vc, Vec3 at, float rad, float r, float g, float b, float a, int seg) {
		if (rad <= 0.001f || a <= 0.001f) {
			return;
		}
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		Matrix4f m = pose.last().pose();
		int rings = Math.max(4, seg / 2);
		for (int i = 0; i < rings; i++) {
			float t0 = Mth.PI * i / rings - Mth.HALF_PI, t1 = Mth.PI * (i + 1) / rings - Mth.HALF_PI;
			for (int j = 0; j < seg; j++) {
				float p0 = Mth.TWO_PI * j / seg, p1 = Mth.TWO_PI * (j + 1) / seg;
				Vector3f v0 = sp(t0, p0, rad), v1 = sp(t0, p1, rad), v2 = sp(t1, p1, rad), v3 = sp(t1, p0, rad);
				quad(vc, m, v0, v1, v2, v3, r, g, b, a);
			}
		}
		pose.popPose();
	}

	private static Vector3f sp(float lat, float lon, float rad) {
		return new Vector3f(Mth.cos(lat) * Mth.cos(lon) * rad, Mth.sin(lat) * rad, Mth.cos(lat) * Mth.sin(lon) * rad);
	}

	private static void ring(PoseStack pose, Vec3 cam, VertexConsumer vc, Vec3 at, float rad, float width, float r, float g, float b, float a) {
		ringFacing(pose, cam, vc, at, new Vec3(0, 1, 0), rad, width, r, g, b, a);
	}

	/** A flat ring round {@code at}, its plane facing {@code normal}. */
	private static void ringFacing(PoseStack pose, Vec3 cam, VertexConsumer vc, Vec3 at, Vec3 normal, float rad, float width, float r, float g,
			float b, float a) {
		if (rad <= 0.01f || a <= 0.001f) {
			return;
		}
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		Matrix4f m = pose.last().pose();
		Vector3f n = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z).normalize();
		Vector3f u = new Vector3f(n).cross(Math.abs(n.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
		Vector3f v = new Vector3f(n).cross(u).normalize();
		int seg = 40;
		float in = Math.max(0f, rad - width);
		for (int i = 0; i < seg; i++) {
			float t0 = Mth.TWO_PI * i / seg, t1 = Mth.TWO_PI * (i + 1) / seg;
			Vector3f o0 = new Vector3f(u).mul(Mth.cos(t0)).add(new Vector3f(v).mul(Mth.sin(t0)));
			Vector3f o1 = new Vector3f(u).mul(Mth.cos(t1)).add(new Vector3f(v).mul(Mth.sin(t1)));
			quad(vc, m, new Vector3f(o0).mul(in), new Vector3f(o1).mul(in), new Vector3f(o1).mul(rad), new Vector3f(o0).mul(rad), r, g, b, a);
		}
		pose.popPose();
	}

	private static void tube(VertexConsumer vc, Matrix4f m, Vector3f dir, Vector3f a, Vector3f b, float len, float r, int sides, float cr, float cg,
			float cb, float ca) {
		for (int i = 0; i < sides; i++) {
			float t0 = i * Mth.TWO_PI / sides, t1 = (i + 1) * Mth.TWO_PI / sides;
			Vector3f p0 = new Vector3f(a).mul(Mth.cos(t0) * r).add(new Vector3f(b).mul(Mth.sin(t0) * r));
			Vector3f p1 = new Vector3f(a).mul(Mth.cos(t1) * r).add(new Vector3f(b).mul(Mth.sin(t1) * r));
			Vector3f e = new Vector3f(dir).mul(len);
			quad(vc, m, p0, p1, new Vector3f(p1).add(e), new Vector3f(p0).add(e), cr, cg, cb, ca);
		}
	}

	private static void helix(VertexConsumer vc, Matrix4f m, Vector3f dir, Vector3f a, Vector3f b, float len, float r, float phase, float cr, float cg,
			float cb, float ca) {
		int steps = Math.max(4, (int) (len * 5));
		float w = 0.06f;
		for (int i = 0; i < steps; i++) {
			float s0 = i / (float) steps * len, s1 = (i + 1) / (float) steps * len;
			float t0 = phase + s0 * 1.3f, t1 = phase + s1 * 1.3f;
			Vector3f p0 = new Vector3f(a).mul(Mth.cos(t0) * r).add(new Vector3f(b).mul(Mth.sin(t0) * r)).add(new Vector3f(dir).mul(s0));
			Vector3f p1 = new Vector3f(a).mul(Mth.cos(t1) * r).add(new Vector3f(b).mul(Mth.sin(t1) * r)).add(new Vector3f(dir).mul(s1));
			Vector3f o0 = new Vector3f(a).mul(Mth.cos(t0)).add(new Vector3f(b).mul(Mth.sin(t0))).mul(w);
			Vector3f o1 = new Vector3f(a).mul(Mth.cos(t1)).add(new Vector3f(b).mul(Mth.sin(t1))).mul(w);
			quad(vc, m, new Vector3f(p0).sub(o0), new Vector3f(p1).sub(o1), new Vector3f(p1).add(o1), new Vector3f(p0).add(o0), cr, cg, cb, ca);
		}
	}

	private static void box(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b,
			float a) {
		Vector3f[] c = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			c[i] = new Vector3f((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1);
		}
		int[][] faces = { { 0, 4, 6, 2 }, { 1, 3, 7, 5 }, { 0, 1, 5, 4 }, { 2, 6, 7, 3 }, { 0, 2, 3, 1 }, { 4, 5, 7, 6 } };
		for (int[] f : faces) {
			quad(vc, m, c[f[0]], c[f[1]], c[f[2]], c[f[3]], r, g, b, a);
		}
	}

	private static void quad(VertexConsumer vc, Matrix4f m, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float r, float g, float b, float a) {
		vc.addVertex(m, p0.x, p0.y, p0.z).setColor(r, g, b, a);
		vc.addVertex(m, p1.x, p1.y, p1.z).setColor(r, g, b, a);
		vc.addVertex(m, p2.x, p2.y, p2.z).setColor(r, g, b, a);
		vc.addVertex(m, p3.x, p3.y, p3.z).setColor(r, g, b, a);
		vc.addVertex(m, p3.x, p3.y, p3.z).setColor(r, g, b, a);
		vc.addVertex(m, p2.x, p2.y, p2.z).setColor(r, g, b, a);
		vc.addVertex(m, p1.x, p1.y, p1.z).setColor(r, g, b, a);
		vc.addVertex(m, p0.x, p0.y, p0.z).setColor(r, g, b, a);
	}

	// ---------------------------------------------------------------- the pose's view

	/** The pose running on this player (0: none) and its age in ticks. */
	static float[] anim(Player p, float partial) {
		long[] a = ANIMS.get(p.getId());
		if (a == null) {
			return null;
		}
		return new float[] { a[0], p.level().getGameTime() - a[1] + partial };
	}

	static boolean beamFiring(Player p) {
		BeamState s = BEAMS.get(p.getId());
		return s != null && s.fired() >= 0;
	}

	static boolean beamCharging(Player p) {
		BeamState s = BEAMS.get(p.getId());
		return s != null && s.fired() < 0;
	}

	static boolean orbCharging(Player p) {
		return ORB_CHARGES.containsKey(p.getId());
	}
}
