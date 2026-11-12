package com.projecthero.mod.client.spin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.spin.model.SteelBallRender;
import com.projecthero.mod.spin.Spin;
import com.projecthero.mod.spin.SpinConfig;
import com.projecthero.mod.spin.SpinMarks;
import com.projecthero.mod.spin.SpinSounds;
import com.projecthero.mod.spin.data.SpinMark;
import com.projecthero.mod.spin.data.SpinState;
import com.projecthero.mod.spin.entity.SteelBallEntity;
import com.projecthero.mod.spin.network.SpinFxPayload;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.17.3 the Spin's effects on the client.
 * <ul>
 *   <li>From {@link SpinFxPayload}: Scan's ball spinning on the ground and its gold outlines (the scanner's alone,
 *   {@link #scanned}), the Golden Spin's line (a gold beam wound with a spiral; brighter on a Perfect Spin), Ball Breaker -- the
 *   pale armoured figure that grows out of the ball, rushes the target and strikes -- the Spin Quake's ring, and the HUD's
 *   Perfect / miss flash ({@link #perfectFlash}).</li>
 *   <li>From the synced state: both balls orbiting a Spin Shield, and the golden rings twisting round every spun creature.</li>
 *   <li>Per tick: the thrown balls' spiralling trail, a spun player's view drifting (his own client only), the loop sounds.</li>
 * </ul>
 * Every ball drawn here goes through the shared {@link SteelBallRender}.
 */
public final class SpinFx {
	public static final ResourceLocation BREAKER_TEX = ProjectHeroMod.id("textures/entity/spin/power/ball_breaker.png");
	public static final int GOLD = 0xFFC83A;

	private record ScanBall(Vec3 pos, long start, int ticks) {
	}

	private record Line(Vec3 a, Vec3 b, long start, int life, boolean perfect) {
	}

	private record Breaker(Vec3 from, Vec3 to, int target, long start, int delay) {
	}

	private record Quake(Vec3 c, long start, int life) {
	}

	private static final Map<Integer, Long> SCANNED = new ConcurrentHashMap<>();
	private static final List<ScanBall> SCAN_BALLS = new ArrayList<>();
	private static final List<Line> LINES = new ArrayList<>();
	private static final List<Breaker> BREAKERS = new ArrayList<>();
	private static final List<Quake> QUAKES = new ArrayList<>();
	private static final Set<Integer> BALL_SOUNDS = new HashSet<>();
	private static final Map<UUID, SpinLoopSound> CHARGE_SOUNDS = new HashMap<>();

	/** The HUD's flash: game time of the last release and whether it was Perfect. */
	public static long perfectFlashAt = -1000L;
	public static boolean perfectFlash;

	private static PlayerModel<AbstractClientPlayer> figure;

	private SpinFx() {
	}

	public static void reset() {
		SCANNED.clear();
		SCAN_BALLS.clear();
		LINES.clear();
		BREAKERS.clear();
		QUAKES.clear();
		BALL_SOUNDS.clear();
		CHARGE_SOUNDS.clear();
		perfectFlashAt = -1000L;
	}

	static void receive(SpinFxPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		long now = mc.level.getGameTime();
		switch (p.kind()) {
			case SpinFxPayload.SCAN_BALL -> SCAN_BALLS.add(new ScanBall(p.a(), now, p.ticks()));
			case SpinFxPayload.SCAN_GLOW -> {
				for (int id : p.ids()) {
					SCANNED.put(id, now + p.ticks());
				}
			}
			case SpinFxPayload.GOLDEN_LINE -> LINES.add(new Line(p.a(), p.b(), now, p.ticks(), p.flag()));
			case SpinFxPayload.BREAKER -> BREAKERS.add(new Breaker(p.a(), p.b(), p.entity(), now, Math.max(1, p.ticks())));
			case SpinFxPayload.QUAKE -> QUAKES.add(new Quake(p.a(), now, Math.max(1, p.ticks())));
			case SpinFxPayload.PERFECT -> {
				perfectFlashAt = now;
				perfectFlash = p.flag();
			}
			default -> {
			}
		}
	}

	/** Lit up by this player's Scan right now (the outline mixins ask). */
	public static boolean scanned(Entity e) {
		Long until = SCANNED.get(e.getId());
		if (until == null) {
			return false;
		}
		if (e.level().getGameTime() > until || !e.isAlive()) {
			SCANNED.remove(e.getId());
			return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- per tick

	static void tick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			return;
		}
		long now = mc.level.getGameTime();
		// the thrown balls' spiralling trail and their whirr
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof SteelBallEntity ball) {
				trail(mc, ball);
				if (BALL_SOUNDS.add(ball.getId())) {
					int id = ball.getId();
					mc.getSoundManager().play(new SpinLoopSound(SpinSounds.BALL_SPIN_LOOP, ball, 0.35f, () -> {
						boolean alive = !ball.isRemoved();
						if (!alive) {
							BALL_SOUNDS.remove(id);
						}
						return alive;
					}));
				}
			}
		}
		// the Golden Rectangle's charge loop, on whoever is charging
		for (Player pl : mc.level.players()) {
			boolean charging = Spin.charging(pl) != SpinState.CHARGE_NONE;
			SpinLoopSound s = CHARGE_SOUNDS.get(pl.getUUID());
			if (charging && (s == null || s.isStopped())) {
				SpinLoopSound fresh = new SpinLoopSound(SpinSounds.GOLDEN_CHARGE_LOOP, pl, 0.4f,
						() -> Spin.charging(pl) != SpinState.CHARGE_NONE);
				CHARGE_SOUNDS.put(pl.getUUID(), fresh);
				mc.getSoundManager().play(fresh);
			}
		}
		// a spun player's view drifts (gently: he can still fight, just not aim true)
		LocalPlayer me = mc.player;
		if (me != null && SpinMarks.playerSpun(me)) {
			float t = (me.tickCount) * 0.21f;
			float dyaw = 1.4f + Mth.sin(t) * 1.6f;
			float dpitch = Mth.sin(t * 1.7f) * 0.7f;
			me.turn(dyaw / 0.15f, dpitch / 0.15f);
		}
		SCAN_BALLS.removeIf(b -> now > b.start + b.ticks + 2);
		LINES.removeIf(l -> now > l.start + l.life);
		BREAKERS.removeIf(b -> now > b.start + b.delay + 22);
		QUAKES.removeIf(q -> now > q.start + q.life);
	}

	private static void trail(Minecraft mc, SteelBallEntity ball) {
		Vec3 v = ball.getDeltaMovement();
		if (v.lengthSqr() < 1.0e-4) {
			return;
		}
		Vec3 d = v.normalize();
		Vec3 up = Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 u = d.cross(up).normalize();
		Vec3 w = d.cross(u).normalize();
		Vec3 c = ball.position().add(0, ball.getBbHeight() * 0.5, 0);
		float a = ball.tickCount * 1.1f;
		boolean golden = ball.golden();
		DustParticleOptions dust = new DustParticleOptions(golden ? new Vector3f(1f, 0.82f, 0.25f) : new Vector3f(1f, 0.86f, 0.42f), golden ? 0.9f : 0.6f);
		for (int k = 0; k < 2; k++) {
			for (int step = 0; step < 2; step++) {
				float ang = a + k * Mth.PI + step * 0.55f;
				Vec3 back = c.subtract(v.scale(step * 0.5));
				Vec3 p = back.add(u.scale(Mth.cos(ang) * 0.22)).add(w.scale(Mth.sin(ang) * 0.22));
				mc.level.addParticle(dust, p.x, p.y, p.z, 0, 0, 0);
			}
		}
	}

	// ---------------------------------------------------------------- the world

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

		// Spin Shield: both balls orbit their thrower
		for (Player pl : mc.level.players()) {
			SpinState s = Spin.peek(pl);
			if (s == null || !s.hasPower || s.shieldUntil <= now || pl.isInvisible()) {
				continue;
			}
			Vec3 c = pl.getPosition(partial).add(0, 1.05, 0);
			for (int i = 0; i < 2; i++) {
				float ang = time * 0.55f + i * Mth.PI;
				float bob = Mth.sin(time * 0.3f + i) * 0.25f;
				double x = c.x + Mth.cos(ang) * 1.35, y = c.y + bob, z = c.z + Mth.sin(ang) * 1.35;
				pose.pushPose();
				pose.translate(x - cam.x, y - cam.y, z - cam.z);
				SteelBallRender.render(pose, buffers, LightTexture.FULL_BRIGHT, 0.14f, (time * 60f) % 360f, false);
				pose.popPose();
			}
			pose.pushPose();
			pose.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
			ring(buffers.getBuffer(RenderType.debugQuads()), pose.last().pose(), 1.28f, 1.42f, 1f, 0.82f, 0.3f, 0.25f);
			pose.popPose();
		}

		// Scan: the ball spinning on the ground, a pulse ring every half second
		for (ScanBall b : SCAN_BALLS) {
			float age = time - b.start;
			if (age < 0 || age > b.ticks) {
				continue;
			}
			pose.pushPose();
			pose.translate(b.pos.x - cam.x, b.pos.y + 0.16 - cam.y, b.pos.z - cam.z);
			SteelBallRender.render(pose, buffers, LightTexture.FULL_BRIGHT, 0.16f, (age * 70f) % 360f, false);
			float pulse = (age % 10f) / 10f;
			ring(buffers.getBuffer(RenderType.debugQuads()), pose.last().pose(), 0.2f + pulse * 3.8f, 0.35f + pulse * 4f, 1f, 0.84f, 0.3f,
					0.5f * (1f - pulse));
			pose.popPose();
		}

		// Golden Spin: the gold line, wound with a spiral
		for (Line l : LINES) {
			float age = time - l.start;
			if (age < 0 || age > l.life) {
				continue;
			}
			float fade = 1f - age / l.life;
			goldenLine(buffers.getBuffer(RenderType.debugQuads()), pose, cam, l, time, fade);
		}

		// Spin Quake: a gold ring racing out
		for (Quake q : QUAKES) {
			float age = time - q.start;
			if (age < 0 || age > q.life) {
				continue;
			}
			float t = age / q.life;
			float r = (float) SpinConfig.QUAKE_RADIUS * (1f - (1f - t) * (1f - t));
			pose.pushPose();
			pose.translate(q.c.x - cam.x, q.c.y + 0.08 - cam.y, q.c.z - cam.z);
			ring(buffers.getBuffer(RenderType.debugQuads()), pose.last().pose(), Math.max(0.1f, r - 0.9f), r, 1f, 0.78f, 0.25f, 0.8f * (1f - t));
			pose.popPose();
		}

		// Ball Breaker
		for (Breaker b : BREAKERS) {
			renderBreaker(mc, pose, buffers, cam, b, time);
		}

		// the rings twisting round every spun creature
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity le) || le.isInvisible()) {
				continue;
			}
			SpinMark m = le.getAttachedOrElse(ModAttachments.SPIN_MARK, null);
			if (m == null || !(m.spun(now) || m.locked(now))) {
				continue;
			}
			if (le == mc.player && mc.options.getCameraType().isFirstPerson()) {
				continue;
			}
			Vec3 c = le.getPosition(partial);
			float h = le.getBbHeight();
			float r = le.getBbWidth() * 0.72f + 0.12f;
			for (int k = 0; k < 3; k++) {
				pose.pushPose();
				pose.translate(c.x - cam.x, c.y + h * (0.22f + 0.3f * k) - cam.y, c.z - cam.z);
				pose.mulPose(Axis.YP.rotationDegrees(time * 24f + k * 50f));
				pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(time * 0.35f + k * 2.1f) * 22f));
				ring(buffers.getBuffer(RenderType.debugQuads()), pose.last().pose(), r, r + 0.06f, 1f, 0.8f, 0.28f, 0.75f);
				pose.popPose();
			}
		}
	}

	private static void goldenLine(VertexConsumer vc, PoseStack pose, Vec3 cam, Line l, float time, float fade) {
		Vec3 a = l.a.subtract(cam), b = l.b.subtract(cam);
		Vec3 dir = b.subtract(a);
		double len = dir.length();
		if (len < 1.0e-3) {
			return;
		}
		Vec3 d = dir.scale(1.0 / len);
		Vec3 mid = a.add(b).scale(0.5);
		Vec3 side = d.cross(mid.normalize()).normalize();
		Matrix4f m = pose.last().pose();
		float w = l.perfect ? 0.22f : 0.14f;
		float alpha = (l.perfect ? 0.95f : 0.7f) * fade;
		quad(vc, m, a.add(side.scale(w)), b.add(side.scale(w)), b.subtract(side.scale(w)), a.subtract(side.scale(w)), 1f, 0.85f, 0.3f, alpha);
		quad(vc, m, a.add(side.scale(w * 0.35)), b.add(side.scale(w * 0.35)), b.subtract(side.scale(w * 0.35)), a.subtract(side.scale(w * 0.35)),
				1f, 0.98f, 0.8f, alpha);
		// the spiral wound round the line
		Vec3 up = Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 u = d.cross(up).normalize();
		Vec3 v = d.cross(u).normalize();
		int steps = (int) Math.min(240, len * 6);
		float radius = l.perfect ? 0.42f : 0.3f;
		for (int i = 0; i < steps; i++) {
			double s = i / (double) steps * len;
			float ang = (float) (s * 2.4 - time * 0.9);
			Vec3 p = a.add(d.scale(s)).add(u.scale(Mth.cos(ang) * radius)).add(v.scale(Mth.sin(ang) * radius));
			float q = 0.045f;
			quad(vc, m, p.add(side.scale(q)).add(d.scale(q)), p.add(side.scale(q)).subtract(d.scale(q)), p.subtract(side.scale(q)).subtract(d.scale(q)),
					p.subtract(side.scale(q)).add(d.scale(q)), 1f, 0.9f, 0.45f, alpha);
		}
	}

	/** The figure: grows out of the ball (4 ticks), rushes the target, strikes, then fades. */
	private static void renderBreaker(Minecraft mc, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Breaker b, float time) {
		float age = time - b.start;
		if (age < 0) {
			return;
		}
		if (figure == null) {
			figure = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
		}
		Entity target = mc.level.getEntity(b.target);
		Vec3 to = target != null ? target.position() : b.to.subtract(0, 0.9, 0);
		Vec3 from = b.from.subtract(0, 1.1, 0);
		Vec3 flat = to.subtract(from).multiply(1, 0, 1);
		Vec3 stop = flat.lengthSqr() > 1.6 ? to.subtract(flat.normalize().scale(1.1)) : from;
		float rush = Mth.clamp(age / b.delay, 0f, 1f);
		float ease = 1f - (1f - rush) * (1f - rush);
		Vec3 pos = from.lerp(stop, ease);
		float yaw = (float) (Mth.atan2(-flat.x, flat.z) * Mth.RAD_TO_DEG);
		float grow = Mth.clamp(age / 4f, 0.15f, 1f);
		float strike = Mth.clamp((age - b.delay) / 5f, 0f, 1f);
		float fade = age < b.delay + 6 ? 1f : Mth.clamp(1f - (age - b.delay - 6) / 16f, 0f, 1f);
		if (fade <= 0f) {
			return;
		}
		// two afterimages trailing the rush, then the figure itself
		for (int ghost = 2; ghost >= 0; ghost--) {
			float back = ghost * 0.12f;
			if (ghost > 0 && rush >= 1f) {
				continue;
			}
			float ge = 1f - (1f - Math.max(0f, rush - back)) * (1f - Math.max(0f, rush - back));
			Vec3 gp = ghost == 0 ? pos : from.lerp(stop, ge);
			float alpha = fade * (ghost == 0 ? 0.78f : 0.3f / ghost);
			pose.pushPose();
			pose.translate(gp.x - cam.x, gp.y - cam.y, gp.z - cam.z);
			pose.mulPose(Axis.YP.rotationDegrees(-yaw));
			float sc = 1.3f * grow;
			pose.scale(-sc, -sc, sc);
			pose.translate(0f, -1.501f, 0f);
			poseFigure(age, strike);
			int a = Mth.clamp((int) (alpha * 255), 0, 255);
			figure.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(BREAKER_TEX)), LightTexture.FULL_BRIGHT,
					OverlayTexture.NO_OVERLAY, (a << 24) | 0xFFFFFF);
			pose.popPose();
		}
	}

	private static void poseFigure(float age, float strike) {
		PlayerModel<AbstractClientPlayer> f = figure;
		f.head.setRotation(0, 0, 0);
		f.body.setRotation(0, 0, 0);
		f.rightArm.setRotation(0, 0, 0);
		f.leftArm.setRotation(0, 0, 0);
		f.rightLeg.setRotation(0, 0, 0);
		f.leftLeg.setRotation(0, 0, 0);
		// the rush: leaning in, arms back; the strike: the right fist driven forward, the left pulled back
		f.body.xRot = 0.35f;
		f.head.xRot = -0.2f;
		f.rightLeg.xRot = -0.5f;
		f.leftLeg.xRot = 0.6f;
		f.rightArm.xRot = Mth.lerp(strike, 0.9f, -1.65f);
		f.rightArm.zRot = Mth.lerp(strike, 0.2f, 0.05f);
		f.leftArm.xRot = Mth.lerp(strike, 0.9f, 0.7f);
		f.leftArm.zRot = -0.25f;
		f.hat.copyFrom(f.head);
		f.jacket.copyFrom(f.body);
		f.rightSleeve.copyFrom(f.rightArm);
		f.leftSleeve.copyFrom(f.leftArm);
		f.rightPants.copyFrom(f.rightLeg);
		f.leftPants.copyFrom(f.leftLeg);
	}

	// ---------------------------------------------------------------- primitives (both windings: seen from either side)

	static void quad(VertexConsumer vc, Matrix4f m, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, float r, float g, float b, float a) {
		vert(vc, m, p1, r, g, b, a);
		vert(vc, m, p2, r, g, b, a);
		vert(vc, m, p3, r, g, b, a);
		vert(vc, m, p4, r, g, b, a);
		vert(vc, m, p4, r, g, b, a);
		vert(vc, m, p3, r, g, b, a);
		vert(vc, m, p2, r, g, b, a);
		vert(vc, m, p1, r, g, b, a);
	}

	private static void vert(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
		vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
	}

	static void ring(VertexConsumer vc, Matrix4f m, float inner, float outer, float r, float g, float b, float a) {
		int sides = 40;
		for (int i = 0; i < sides; i++) {
			float a0 = i * Mth.TWO_PI / sides, a1 = (i + 1) * Mth.TWO_PI / sides;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			vc.addVertex(m, c0 * inner, 0f, s0 * inner).setColor(r, g, b, a * 0.3f);
			vc.addVertex(m, c0 * outer, 0f, s0 * outer).setColor(r, g, b, a);
			vc.addVertex(m, c1 * outer, 0f, s1 * outer).setColor(r, g, b, a);
			vc.addVertex(m, c1 * inner, 0f, s1 * inner).setColor(r, g, b, a * 0.3f);
			vc.addVertex(m, c1 * inner, 0f, s1 * inner).setColor(r, g, b, a * 0.3f);
			vc.addVertex(m, c1 * outer, 0f, s1 * outer).setColor(r, g, b, a);
			vc.addVertex(m, c0 * outer, 0f, s0 * outer).setColor(r, g, b, a);
			vc.addVertex(m, c0 * inner, 0f, s0 * inner).setColor(r, g, b, a * 0.3f);
		}
	}
}
