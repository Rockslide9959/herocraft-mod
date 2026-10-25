package com.projecthero.mod.client.silversurfer;

import java.util.Deque;
import java.util.Iterator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.silversurfer.SilverSurfer;
import com.projecthero.mod.silversurfer.SilverSurferConfig;
import com.projecthero.mod.silversurfer.data.SilverSurferState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.17.1: the Silver Surfer's world effects for every viewer, from the synced state: the Power Cosmic beam (a white core
 * in a pale-blue glow), the Cosmic Barrier (a shimmering silver hexagon in front of him that dims as its 40 points go) and
 * the silver streak behind the board (brighter and longer at Light-Speed). Additive {@code lightning} quads.
 */
public final class SilverSurferEffects {
	private SilverSurferEffects() {
	}

	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		Camera camera = context.camera();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		PoseStack pose = context.matrixStack();
		Vec3 cam = camera.getPosition();
		VertexConsumer vc = null;
		for (Player player : mc.level.players()) {
			SilverSurferState s = SilverSurfer.peek(player);
			if (s == null || !s.hasPower) {
				continue;
			}
			boolean beam = s.beamStart != 0L && now - s.beamStart >= SilverSurferConfig.BEAM_HOLD_TICKS && s.beamEnd.size() == 3;
			boolean barrier = s.barrierUntil > now && s.barrierHp > 0f;
			Deque<Vec3> trail = SilverSurferClientState.trail(player);
			if (!beam && !barrier && (trail == null || trail.size() < 2)) {
				continue;
			}
			if (vc == null) {
				vc = context.consumers().getBuffer(RenderType.lightning());
			}
			pose.pushPose();
			pose.translate(-cam.x, -cam.y, -cam.z);
			Matrix4f m = pose.last().pose();
			boolean fp = player == mc.player && mc.options.getCameraType().isFirstPerson();
			if (beam) {
				float flicker = 0.85f + 0.15f * Mth.sin((now + partial) * 1.7f);
				Vec3 from = hand(player, partial, fp, camera);
				Vec3 to = new Vec3(s.beamEnd.get(0), s.beamEnd.get(1), s.beamEnd.get(2));
				ribbon(vc, m, cam, from, to, 0.42f * flicker, 0.45f, 0.65f, 1.0f, 0.45f);
				ribbon(vc, m, cam, from, to, 0.16f * flicker, 0.95f, 0.98f, 1.0f, 0.95f);
			}
			if (barrier) {
				float hp = s.barrierHp / SilverSurferConfig.BARRIER_HP;
				float pulse = 0.75f + 0.25f * Mth.sin((now + partial) * 0.6f);
				Vec3 look = Vec3.directionFromRotation(player.getViewXRot(partial), player.getViewYRot(partial));
				Vec3 c = player.getEyePosition(partial).add(look.scale(1.1)).add(0, -0.4, 0);
				float spin = (now + partial) * 0.02f;
				polygon(vc, m, c, look, 1.25f, 6, spin, 0.8f, 0.88f, 1.0f, 0.35f * pulse * (0.4f + 0.6f * hp));
				polygon(vc, m, c, look, 1.35f, 6, -(now + partial) * 0.03f, 0.6f, 0.75f, 1.0f, 0.18f * pulse);
				rim(vc, m, cam, c, look, 1.3f, 6, spin, 0.95f, 0.97f, 1.0f, 0.75f * pulse * (0.4f + 0.6f * hp));
			}
			if (trail != null && trail.size() >= 2 && !(fp && trail.size() < 3)) {
				streak(vc, m, cam, player, trail, partial, SilverSurfer.lightSpeed(player));
			}
			pose.popPose();
		}
	}

	/** The silver streak: two fading ribbons through his recent positions (at the board and at chest height). */
	private static void streak(VertexConsumer vc, Matrix4f m, Vec3 cam, Player player, Deque<Vec3> trail, float partial, boolean light) {
		Vec3 head = player.getPosition(partial);
		Vec3 prev = head;
		int i = 0;
		int n = trail.size();
		Iterator<Vec3> it = trail.iterator();
		while (it.hasNext()) {
			Vec3 p = it.next();
			if (i == 0 && p.distanceToSqr(head) < 1.0e-4) {
				i++;
				continue;
			}
			float k = 1f - (float) i / n;
			float w = (light ? 0.5f : 0.3f) * k;
			float a = (light ? 0.7f : 0.35f) * k;
			ribbon(vc, m, cam, prev.add(0, -0.15, 0), p.add(0, -0.15, 0), w, 0.85f, 0.92f, 1.0f, a);
			if (light && i > 1) {
				ribbon(vc, m, cam, prev.add(0, 1.0, 0), p.add(0, 1.0, 0), w * 0.8f, 0.65f, 0.8f, 1.0f, a * 0.5f);
				ribbon(vc, m, cam, prev.add(0, 1.0, 0), p.add(0, 1.0, 0), w * 0.3f, 1.0f, 1.0f, 1.0f, a);
			}
			prev = p;
			i++;
		}
	}

	private static Vec3 hand(Player player, float partial, boolean fp, Camera cam) {
		if (fp) {
			Vector3f look = cam.getLookVector();
			Vector3f up = cam.getUpVector();
			Vector3f left = cam.getLeftVector();
			return cam.getPosition().add(look.x * 0.8, look.y * 0.8, look.z * 0.8).add(-left.x * 0.38, -left.y * 0.38, -left.z * 0.38)
					.add(-up.x * 0.3, -up.y * 0.3, -up.z * 0.3);
		}
		Vec3 pos = player.getPosition(partial);
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		Vec3 right = Vec3.directionFromRotation(0.0f, bodyYaw + 90.0f);
		Vec3 look = Vec3.directionFromRotation(player.getViewXRot(partial), player.getViewYRot(partial));
		return pos.add(0.0, player.getBbHeight() * 0.76, 0.0).add(right.scale(0.36)).add(look.scale(0.75));
	}

	/** A camera-facing strip from {@code a} to {@code b}. */
	static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float width, float r, float g, float bl, float alpha) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0e-4 || alpha <= 0.01f) {
			return;
		}
		Vec3 mid = a.add(b).scale(0.5);
		Vec3 side = dir.cross(cam.subtract(mid));
		if (side.lengthSqr() < 1.0e-6) {
			return;
		}
		side = side.normalize().scale(width * 0.5);
		vertex(vc, m, a.subtract(side), r, g, bl, alpha);
		vertex(vc, m, a.add(side), r, g, bl, alpha);
		vertex(vc, m, b.add(side), r, g, bl, alpha);
		vertex(vc, m, b.subtract(side), r, g, bl, alpha);
	}

	/** A flat regular polygon centred on {@code c}, facing {@code normal}, turned by {@code spin} radians. */
	private static void polygon(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 normal, float radius, int sides, float spin, float r, float g, float b,
			float alpha) {
		Vec3 n = normal.normalize();
		Vec3 u = n.cross(new Vec3(0, 1, 0));
		if (u.lengthSqr() < 1.0e-4) {
			u = new Vec3(1, 0, 0);
		}
		u = u.normalize();
		Vec3 v = u.cross(n).normalize();
		for (int i = 0; i < sides; i++) {
			double a0 = Math.PI * 2 * i / sides + spin;
			double a1 = Math.PI * 2 * (i + 1) / sides + spin;
			Vec3 p0 = c.add(u.scale(Math.cos(a0) * radius)).add(v.scale(Math.sin(a0) * radius));
			Vec3 p1 = c.add(u.scale(Math.cos(a1) * radius)).add(v.scale(Math.sin(a1) * radius));
			vertex(vc, m, c, r, g, b, alpha * 0.5f);
			vertex(vc, m, p0, r, g, b, alpha);
			vertex(vc, m, p1, r, g, b, alpha);
			vertex(vc, m, c, r, g, b, alpha * 0.5f);
			// and the other winding, so it shows from either side
			vertex(vc, m, c, r, g, b, alpha * 0.5f);
			vertex(vc, m, p1, r, g, b, alpha);
			vertex(vc, m, p0, r, g, b, alpha);
			vertex(vc, m, c, r, g, b, alpha * 0.5f);
		}
	}

	/** The bright edge of a polygon: a camera-facing ribbon along each side. */
	private static void rim(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 c, Vec3 normal, float radius, int sides, float spin, float r, float g,
			float b, float alpha) {
		Vec3 n = normal.normalize();
		Vec3 u = n.cross(new Vec3(0, 1, 0));
		if (u.lengthSqr() < 1.0e-4) {
			u = new Vec3(1, 0, 0);
		}
		u = u.normalize();
		Vec3 v = u.cross(n).normalize();
		for (int i = 0; i < sides; i++) {
			double a0 = Math.PI * 2 * i / sides + spin;
			double a1 = Math.PI * 2 * (i + 1) / sides + spin;
			Vec3 p0 = c.add(u.scale(Math.cos(a0) * radius)).add(v.scale(Math.sin(a0) * radius));
			Vec3 p1 = c.add(u.scale(Math.cos(a1) * radius)).add(v.scale(Math.sin(a1) * radius));
			ribbon(vc, m, cam, p0, p1, 0.07f, r, g, b, alpha);
		}
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
		vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
	}
}
