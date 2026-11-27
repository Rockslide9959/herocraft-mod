package com.projecthero.mod.client.blackpanther;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.blackpanther.BlackPantherConfig;
import com.projecthero.mod.blackpanther.data.BlackPantherState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.17.3: the kinetic shockwave (Kinetic Pounce burst, Overload): a thick purple ring racing out to the blast's radius, fading as
 * it goes, with a thinner white-violet ring just inside it. Plain translucent quads, both windings (additive light washes out to
 * white over a bright sky).
 *
 * <p>v0.17.7 suited kit: Kinetic Overload also raises a big violet dome over the ring (latitude bands, brightest at the rim, swelling
 * to the full radius and fading); the Kinetic Dash leaves violet afterimages -- translucent silhouettes of him (head, body, arms,
 * legs) at the spots he passed, each fading out over half a second.
 */
public final class BlackPantherEffectsRenderer {
	private static final float LIFE = 11f;
	private static final float DOME_LIFE = 18f;
	private static final int SIDES = 48;
	private static final float GHOST_LIFE = 10f;

	/** Afterimage snapshot: where, which way the body faced, when. */
	private record Ghost(Vec3 pos, float yaw, float time) {
	}

	/** Client-only: the dash's afterimages per player (positions only; dropped as soon as they fade). */
	private static final Map<UUID, Deque<Ghost>> GHOSTS = new HashMap<>();

	private BlackPantherEffectsRenderer() {
	}

	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			GHOSTS.clear();
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		float time = now + partial;
		PoseStack pose = context.matrixStack();
		Vec3 cam = context.camera().getPosition();
		VertexConsumer vc = context.consumers().getBuffer(RenderType.debugQuads());
		for (Player player : mc.level.players()) {
			BlackPantherState s = player.getAttachedOrElse(ModAttachments.BLACK_PANTHER_STATE, null);
			if (s == null) {
				continue;
			}
			trackDash(player, s, time);
			if (s.pulseStart == 0L || s.pulsePos.size() != 3) {
				continue;
			}
			float age = now - s.pulseStart + partial;
			boolean overload = s.animId == BlackPantherState.ANIM_OVERLOAD && s.pulseStart >= s.animStart;
			if (age < 0f || age > (overload ? DOME_LIFE : LIFE)) {
				continue;
			}
			pose.pushPose();
			pose.translate(s.pulsePos.get(0) - cam.x, s.pulsePos.get(1) - cam.y, s.pulsePos.get(2) - cam.z);
			Matrix4f m = pose.last().pose();
			if (age <= LIFE) {
				float t = age / LIFE;
				float r = s.pulseRadius * (1f - (1f - t) * (1f - t));
				float fade = 1f - t;
				ring(vc, m, Math.max(0.1f, r - 1.1f), r, 0.5f, 0.12f, 0.95f, 0.8f * fade);
				ring(vc, m, Math.max(0.05f, r - 0.22f), r, 0.82f, 0.62f, 1.0f, 0.9f * fade);
			}
			if (overload) {
				float t = age / DOME_LIFE;
				float r = s.pulseRadius * (1f - (1f - t) * (1f - t) * (1f - t));
				dome(vc, m, r, 0.62f * (1f - t * t));
			}
			pose.popPose();
		}
		renderGhosts(vc, pose, cam, time);
	}

	// ---------------------------------------------------------------- the dash's afterimages

	private static void trackDash(Player player, BlackPantherState s, float time) {
		float age = player.level().getGameTime() - s.animStart;
		boolean dashing = s.animId == BlackPantherState.ANIM_DASH && age >= 0f && age <= BlackPantherConfig.DASH_TICKS + 1f;
		if (!dashing) {
			return;
		}
		Deque<Ghost> q = GHOSTS.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
		Vec3 at = player.position();
		Ghost last = q.peekLast();
		// a new silhouette every 0.7 blocks of travel (the server moves him 2 blocks a tick)
		if (last == null || last.pos.distanceToSqr(at) > 0.49) {
			if (last != null) {
				Vec3 from = last.pos;
				int n = (int) Math.min(4, Math.floor(from.distanceTo(at) / 0.7));
				for (int i = 1; i < n; i++) {
					q.addLast(new Ghost(from.lerp(at, i / (double) n), player.yBodyRot, time));
				}
			}
			q.addLast(new Ghost(at, player.yBodyRot, time));
			while (q.size() > 24) {
				q.pollFirst();
			}
		}
	}

	private static void renderGhosts(VertexConsumer vc, PoseStack pose, Vec3 cam, float time) {
		for (Iterator<Map.Entry<UUID, Deque<Ghost>>> it = GHOSTS.entrySet().iterator(); it.hasNext();) {
			Deque<Ghost> q = it.next().getValue();
			q.removeIf(g -> time - g.time > GHOST_LIFE || time < g.time - 40f);
			if (q.isEmpty()) {
				it.remove();
				continue;
			}
			for (Ghost g : q) {
				float fade = 1f - (time - g.time) / GHOST_LIFE;
				pose.pushPose();
				pose.translate(g.pos.x - cam.x, g.pos.y - cam.y, g.pos.z - cam.z);
				pose.mulPose(Axis.YP.rotationDegrees(180f - g.yaw)); // the player renderer's own body turn
				Matrix4f m = pose.last().pose();
				float a = 0.38f * fade;
				// a player-sized silhouette (px / 16): legs, body, arms swept back, head
				box(vc, m, -4, 0, -2, 0, 12, 2, a, 0.55f);
				box(vc, m, 0, 0, -2, 4, 12, 2, a, 0.55f);
				box(vc, m, -4, 12, -2, 4, 24, 2, a, 0.7f);
				box(vc, m, -8, 13, -1, -4, 24, 4, a * 0.85f, 0.6f);
				box(vc, m, 4, 13, -1, 8, 24, 4, a * 0.85f, 0.6f);
				box(vc, m, -4, 24, -4, 4, 32, 4, a, 0.8f);
				pose.popPose();
			}
		}
	}

	/** A translucent violet box in px (x right, y up, z forward); {@code light} tints from deep violet to pale lilac. */
	private static void box(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float a, float light) {
		float s = 1f / 16f;
		x0 *= s; y0 *= s; z0 *= s; x1 *= s; y1 *= s; z1 *= s;
		float r = Mth.lerp(light, 0.45f, 0.85f), g = Mth.lerp(light, 0.12f, 0.65f), b = 1.0f;
		quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, a);
		quad(vc, m, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, r, g, b, a);
		quad(vc, m, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, r, g, b, a);
		quad(vc, m, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
		quad(vc, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, a);
		quad(vc, m, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, r, g, b, a);
	}

	/** One quad, both windings. */
	private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
			float dx, float dy, float dz, float r, float g, float b, float a) {
		vc.addVertex(m, ax, ay, az).setColor(r, g, b, a);
		vc.addVertex(m, bx, by, bz).setColor(r, g, b, a);
		vc.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
		vc.addVertex(m, dx, dy, dz).setColor(r, g, b, a);
		vc.addVertex(m, dx, dy, dz).setColor(r, g, b, a);
		vc.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
		vc.addVertex(m, bx, by, bz).setColor(r, g, b, a);
		vc.addVertex(m, ax, ay, az).setColor(r, g, b, a);
	}

	// ---------------------------------------------------------------- the Overload dome

	/** A hemisphere of {@code radius}: thin at the crown, brightest round the rim where it meets the ground. */
	private static void dome(VertexConsumer vc, Matrix4f m, float radius, float alpha) {
		if (radius < 0.2f || alpha < 0.01f) {
			return;
		}
		int bands = 10;
		for (int j = 0; j < bands; j++) {
			float p0 = j * Mth.HALF_PI / bands;
			float p1 = (j + 1) * Mth.HALF_PI / bands;
			float r0 = Mth.cos(p0) * radius, y0 = Mth.sin(p0) * radius;
			float r1 = Mth.cos(p1) * radius, y1 = Mth.sin(p1) * radius;
			float a0 = alpha * (0.15f + 0.85f * (1f - j / (float) bands) * (1f - j / (float) bands));
			float a1 = alpha * (0.15f + 0.85f * (1f - (j + 1) / (float) bands) * (1f - (j + 1) / (float) bands));
			for (int i = 0; i < SIDES; i++) {
				float t0 = i * Mth.TWO_PI / SIDES;
				float t1 = (i + 1) * Mth.TWO_PI / SIDES;
				float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
				vc.addVertex(m, c0 * r0, y0, s0 * r0).setColor(0.6f, 0.25f, 1.0f, a0);
				vc.addVertex(m, c1 * r0, y0, s1 * r0).setColor(0.6f, 0.25f, 1.0f, a0);
				vc.addVertex(m, c1 * r1, y1, s1 * r1).setColor(0.75f, 0.5f, 1.0f, a1);
				vc.addVertex(m, c0 * r1, y1, s0 * r1).setColor(0.75f, 0.5f, 1.0f, a1);
				vc.addVertex(m, c0 * r1, y1, s0 * r1).setColor(0.75f, 0.5f, 1.0f, a1);
				vc.addVertex(m, c1 * r1, y1, s1 * r1).setColor(0.75f, 0.5f, 1.0f, a1);
				vc.addVertex(m, c1 * r0, y0, s1 * r0).setColor(0.6f, 0.25f, 1.0f, a0);
				vc.addVertex(m, c0 * r0, y0, s0 * r0).setColor(0.6f, 0.25f, 1.0f, a0);
			}
		}
	}

	private static void ring(VertexConsumer vc, Matrix4f m, float inner, float outer, float r, float g, float b, float a) {
		for (int i = 0; i < SIDES; i++) {
			float a0 = i * Mth.TWO_PI / SIDES;
			float a1 = (i + 1) * Mth.TWO_PI / SIDES;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			vc.addVertex(m, c0 * inner, 0f, s0 * inner).setColor(r, g, b, a * 0.2f);
			vc.addVertex(m, c0 * outer, 0f, s0 * outer).setColor(r, g, b, a);
			vc.addVertex(m, c1 * outer, 0f, s1 * outer).setColor(r, g, b, a);
			vc.addVertex(m, c1 * inner, 0f, s1 * inner).setColor(r, g, b, a * 0.2f);
			vc.addVertex(m, c1 * inner, 0f, s1 * inner).setColor(r, g, b, a * 0.2f);
			vc.addVertex(m, c1 * outer, 0f, s1 * outer).setColor(r, g, b, a);
			vc.addVertex(m, c0 * outer, 0f, s0 * outer).setColor(r, g, b, a);
			vc.addVertex(m, c0 * inner, 0f, s0 * inner).setColor(r, g, b, a * 0.2f);
		}
	}
}
