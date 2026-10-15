package com.projecthero.mod.client.bluebeetle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.bluebeetle.data.BlueBeetleState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.16.5: Blue Beetle's world effects for every viewer, from the synced state: the Mega Cannon's beam (a thick blue beam
 * with a white core, fading over 10 ticks) and the Bubble Prison (a shimmering blue sphere round the trapped creature).
 * Additive {@code lightning} quads, nothing spawned.
 */
public final class BlueBeetleEffectsRenderer {
	private BlueBeetleEffectsRenderer() {
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
			BlueBeetleState s = player.getAttachedOrElse(ModAttachments.BLUE_BEETLE_STATE, null);
			if (s == null || !s.hasPower || !s.suited) {
				continue;
			}
			boolean beam = s.fx.beamUntil > now - 1 && s.fx.beamEnd.size() == 3;
			Entity bubbled = s.fx.bubbleTarget != -1 && s.fx.bubbleUntil > now ? mc.level.getEntity(s.fx.bubbleTarget) : null;
			if (!beam && bubbled == null) {
				continue;
			}
			if (vc == null) {
				vc = context.consumers().getBuffer(RenderType.lightning());
			}
			pose.pushPose();
			pose.translate(-cam.x, -cam.y, -cam.z);
			Matrix4f m = pose.last().pose();
			if (beam) {
				float life = Mth.clamp((s.fx.beamUntil - now - partial) / 10f, 0f, 1f);
				Vec3 from = player.getEyePosition(partial).add(0, -0.35, 0)
						.add(Vec3.directionFromRotation(player.getViewXRot(partial), player.getViewYRot(partial)).scale(0.8));
				Vec3 to = new Vec3(s.fx.beamEnd.get(0), s.fx.beamEnd.get(1), s.fx.beamEnd.get(2));
				ribbon(vc, m, cam, from, to, 0.9f * life, 0.15f, 0.45f, 1.0f, 0.5f * life);
				ribbon(vc, m, cam, from, to, 0.35f * life, 0.85f, 0.95f, 1.0f, 0.9f * life);
			}
			if (bubbled != null) {
				Vec3 c = bubbled.getPosition(partial).add(0, bubbled.getBbHeight() * 0.5, 0);
				float r = Math.max(bubbled.getBbWidth(), bubbled.getBbHeight()) * 0.75f + 0.3f;
				float left = Mth.clamp((s.fx.bubbleUntil - now - partial) / 10f, 0f, 1f);
				float shimmer = 0.75f + 0.25f * Mth.sin((now + partial) * 0.4f);
				sphere(vc, m, c, r, 0.25f, 0.6f, 1.0f, 0.22f * shimmer * Math.max(0.3f, left));
			}
			pose.popPose();
		}
	}

	private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float width, float r, float g, float bl, float alpha) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0e-4 || alpha <= 0.01f) {
			return;
		}
		Vec3 side = dir.cross(cam.subtract(a.add(b).scale(0.5)));
		if (side.lengthSqr() < 1.0e-6) {
			return;
		}
		side = side.normalize().scale(width * 0.5);
		vertex(vc, m, a.subtract(side), r, g, bl, alpha);
		vertex(vc, m, a.add(side), r, g, bl, alpha);
		vertex(vc, m, b.add(side), r, g, bl, alpha);
		vertex(vc, m, b.subtract(side), r, g, bl, alpha);
	}

	/** A UV sphere of quads (12 x 8). */
	private static void sphere(VertexConsumer vc, Matrix4f m, Vec3 c, float r, float cr, float cg, float cb, float alpha) {
		int lon = 12, lat = 8;
		for (int i = 0; i < lat; i++) {
			double t0 = Math.PI * i / lat - Math.PI / 2, t1 = Math.PI * (i + 1) / lat - Math.PI / 2;
			for (int j = 0; j < lon; j++) {
				double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
				float edge = (i == 0 || i == lat - 1) ? 0.7f : 1f;
				vertex(vc, m, at(c, r, t0, p0), cr, cg, cb, alpha * edge);
				vertex(vc, m, at(c, r, t0, p1), cr, cg, cb, alpha * edge);
				vertex(vc, m, at(c, r, t1, p1), cr, cg, cb, alpha * edge);
				vertex(vc, m, at(c, r, t1, p0), cr, cg, cb, alpha * edge);
			}
		}
	}

	private static Vec3 at(Vec3 c, float r, double theta, double phi) {
		return c.add(Math.cos(theta) * Math.cos(phi) * r, Math.sin(theta) * r, Math.cos(theta) * Math.sin(phi) * r);
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
		vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
	}
}
