package com.projecthero.mod.client.spider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.spider.data.SpiderManState;
import com.projecthero.mod.spider.data.SpiderMoveState;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1 Spider-Sense visuals in the world -- all purely client-side renders / particles, nothing on any entity:
 * <ul>
 * <li><b>Tingle lines</b>: the comic "spider-sense" squiggles round the head of every Spider-Man whose sense just fired
 * ({@link SpiderMoveState#tingleUntil}, synced, so other players see them in third person). Skipped for the local
 * player in first person.</li>
 * <li><b>Predicted paths</b>: a faint dotted red line along the simulated flight of every projectile (arrow, trident,
 * thrown item, fireball...) that will pass close to the local Spider-Man -- his own view only. (Firearms are hitscan,
 * so their warning is the aim outline + ring instead.)</li>
 * <li><b>Shimmer</b>: a faint glint on invisible hostiles near him (and on invisible players his server-side sense
 * already lists as a threat -- squadmates never are).</li>
 * </ul>
 */
public final class SpiderSenseFx {
	private static final double PATH_RANGE = 40.0;
	private static final double PATH_NEAR = 2.6;
	private static final int PATH_STEPS = 60;

	private SpiderSenseFx() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(SpiderSenseFx::render);
		ClientTickEvents.END_CLIENT_TICK.register(SpiderSenseFx::tick);
	}

	private static boolean isSpiderMan(Player p) {
		SpiderManState st = p.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		return st != null && st.hasPower;
	}

	// ---------------- render ----------------

	private static void render(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		Camera camera = context.camera();
		PoseStack poseStack = context.matrixStack();
		MultiBufferSource consumers = context.consumers();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long gameTime = client.level.getGameTime();
		double now = gameTime + partial;
		Vec3 cam = camera.getPosition();
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		VertexConsumer buffer = consumers.getBuffer(RenderType.lines());
		PoseStack.Pose pose = poseStack.last();

		// tingle lines round every tingling Spider-Man's head
		boolean firstPerson = client.options.getCameraType().isFirstPerson();
		for (Player p : client.level.players()) {
			SpiderMoveState ms = p.getAttachedOrElse(ModAttachments.SPIDER_MOVE_STATE, null);
			if (ms == null || ms.tingleUntil <= gameTime || p.isInvisible()) {
				continue;
			}
			if (p == client.getCameraEntity() && firstPerson) {
				continue;
			}
			double left = ms.tingleUntil - now;
			float alpha = (float) Math.min(1.0, left / 8.0) * 0.95f;
			if (alpha > 0.02f) {
				drawTingle(buffer, pose, camera, p, partial, now, alpha);
			}
		}

		// predicted projectile paths toward the local Spider-Man
		Player me = client.player;
		if (isSpiderMan(me) && !me.isSpectator()) {
			Vec3 centre = me.getPosition(partial).add(0, me.getBbHeight() * 0.5, 0);
			AABB box = me.getBoundingBox().inflate(PATH_RANGE);
			for (Projectile proj : client.level.getEntitiesOfClass(Projectile.class, box,
					e -> e.isAlive() && e.getOwner() != me && e.getDeltaMovement().lengthSqr() > 0.04)) {
				drawPath(buffer, pose, proj, partial, centre);
			}
		}
		poseStack.popPose();
	}

	/** Six jagged comic strokes fanned round the head, billboarded to the camera and flickering. */
	private static void drawTingle(VertexConsumer buffer, PoseStack.Pose pose, Camera camera, Player p, float partial,
			double now, float alpha) {
		Vec3 head = p.getPosition(partial).add(0, p.getEyeHeight() + 0.05, 0);
		// camera-facing basis
		Vec3 toCam = camera.getPosition().subtract(head);
		if (toCam.lengthSqr() < 1.0e-4) {
			return;
		}
		Vec3 fwd = toCam.normalize();
		Vec3 right = new Vec3(0, 1, 0).cross(fwd);
		if (right.lengthSqr() < 1.0e-4) {
			right = new Vec3(1, 0, 0);
		}
		right = right.normalize();
		Vec3 up = fwd.cross(right).normalize();
		// angles (degrees from straight up), mirrored on both sides of the head
		double[] angles = {-80, -52, -24, 24, 52, 80};
		for (int i = 0; i < angles.length; i++) {
			double flick = 0.85 + 0.15 * Math.sin(now * 1.7 + i * 2.1);
			double a = Math.toRadians(angles[i]);
			Vec3 dir = up.scale(Math.cos(a)).add(right.scale(Math.sin(a)));
			Vec3 side = up.scale(-Math.sin(a)).add(right.scale(Math.cos(a)));
			double r0 = 0.38;
			double r1 = 0.38 + 0.42 * flick;
			Vec3 prev = head.add(dir.scale(r0));
			int segs = 4;
			for (int s = 1; s <= segs; s++) {
				double t = s / (double) segs;
				double zig = (s == segs ? 0.0 : (s % 2 == 0 ? -1 : 1) * 0.07);
				Vec3 pt = head.add(dir.scale(r0 + (r1 - r0) * t)).add(side.scale(zig));
				// a second, slightly offset pass makes the thin vanilla line read as a bold ink stroke
				seg(buffer, pose, prev, pt, 0.98f, 0.30f, 0.22f, alpha);
				seg(buffer, pose, prev.add(side.scale(0.012)), pt.add(side.scale(0.012)), 1.0f, 0.92f, 0.85f, alpha * 0.7f);
				prev = pt;
			}
		}
	}

	/** Simulates the projectile forward and draws a dotted faint line while it will pass close to him. */
	private static void drawPath(VertexConsumer buffer, PoseStack.Pose pose, Projectile proj, float partial, Vec3 target) {
		double gravity = proj.isNoGravity() ? 0.0 : proj instanceof AbstractArrow ? 0.05 : proj instanceof ThrowableProjectile ? 0.03 : 0.0;
		double drag = proj instanceof AbstractArrow || proj instanceof ThrowableProjectile ? 0.99 : 1.0;
		Vec3 pos = proj.getPosition(partial);
		Vec3 vel = proj.getDeltaMovement();
		// first pass: does it come close at all?
		double best = Double.MAX_VALUE;
		int bestStep = -1;
		Vec3 p = pos;
		Vec3 v = vel;
		for (int i = 0; i < PATH_STEPS; i++) {
			p = p.add(v);
			v = v.scale(drag).subtract(0, gravity, 0);
			double d = p.distanceToSqr(target);
			if (d < best) {
				best = d;
				bestStep = i;
			}
		}
		if (best > PATH_NEAR * PATH_NEAR || bestStep < 0) {
			return;
		}
		// second pass: draw it, dotted, fading out a little past the closest approach
		p = pos;
		v = vel;
		int last = Math.min(PATH_STEPS, bestStep + 3);
		for (int i = 0; i <= last; i++) {
			Vec3 next = p.add(v);
			v = v.scale(drag).subtract(0, gravity, 0);
			if (i % 2 == 0) {
				float a = 0.6f * (i > bestStep ? 0.4f : 1.0f);
				seg(buffer, pose, p, p.lerp(next, 0.65), 1.0f, 0.25f, 0.2f, a);
			}
			p = next;
		}
	}

	private static void seg(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, float r, float g, float bl, float alpha) {
		Vec3 d = b.subtract(a);
		double len = Math.max(1.0e-6, d.length());
		float nx = (float) (d.x / len);
		float ny = (float) (d.y / len);
		float nz = (float) (d.z / len);
		buffer.addVertex(pose.pose(), (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, alpha).setNormal(pose, nx, ny, nz);
		buffer.addVertex(pose.pose(), (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, alpha).setNormal(pose, nx, ny, nz);
	}

	// ---------------- shimmer ----------------

	private static void tick(Minecraft client) {
		if (client.level == null || client.player == null || client.isPaused()) {
			return;
		}
		Player me = client.player;
		if (!isSpiderMan(me) || me.isSpectator() || (client.level.getGameTime() & 3L) != 0L) {
			return;
		}
		long now = client.level.getGameTime();
		var rand = client.level.random;
		for (LivingEntity e : client.level.getEntitiesOfClass(LivingEntity.class, me.getBoundingBox().inflate(16.0),
				x -> x != me && x.isAlive() && x.isInvisible())) {
			boolean threat = e instanceof net.minecraft.world.entity.monster.Enemy
					|| (e instanceof Player && SpiderSenseGlowClient.isThreat(e.getId(), now));
			if (!threat) {
				continue;
			}
			shimmer(client, e, rand);
		}
	}

	private static void shimmer(Minecraft client, Entity e, net.minecraft.util.RandomSource rand) {
		AABB b = e.getBoundingBox();
		for (int i = 0; i < 2; i++) {
			double x = b.minX + rand.nextDouble() * b.getXsize();
			double y = b.minY + rand.nextDouble() * b.getYsize();
			double z = b.minZ + rand.nextDouble() * b.getZsize();
			client.level.addParticle(ParticleTypes.WAX_OFF, x, y, z, 0.0, 0.01, 0.0);
		}
	}
}
