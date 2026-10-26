package com.projecthero.mod.client.greenlantern;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.greenlantern.GreenLanternConfig;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.1, explicit user request (a model for the dome instead of particles): the Protective Dome (Shift+Z) is drawn as
 * a hard-light globe instead of the old particle great-circles -- a smooth translucent green shell with a paler outer
 * skin, glowing latitude / longitude ribs like a construct lattice, a bright band sweeping up it, and a flicker as the
 * dome's HP runs low. It grows from nothing over {@link GreenLanternConfig#DOME_EXPAND_TICKS} (matching the server's
 * push-out radius) and follows its Lantern, centred where the server measures it (feet + 1). Purely a render keyed off
 * the synced barrier attachments.
 */
public final class GreenLanternDomeRenderer {
	private static final int LAT = 24;
	private static final int LON = 40;
	/** Player UUID -> client game time the dome was first seen up (drives the grow-in). */
	private static final Map<UUID, Long> SEEN = new HashMap<>();

	private GreenLanternDomeRenderer() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(GreenLanternDomeRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			SEEN.clear();
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		VertexConsumer vc = TurboDraw.buffer(context.consumers());
		Set<UUID> live = new HashSet<>();
		for (Player player : mc.level.players()) {
			float hp = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f);
			if (hp <= 0f || !player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, false)) {
				continue;
			}
			live.add(player.getUUID());
			long start = SEEN.computeIfAbsent(player.getUUID(), k -> now);
			float age = (now - start) + partial;
			float grow = Mth.clamp(age / GreenLanternConfig.DOME_EXPAND_TICKS, 0f, 1f);
			float radius = (float) GreenLanternConfig.DOME_RADIUS * (1f - (1f - grow) * (1f - grow)); // ease-out
			if (radius < 0.2f) {
				continue;
			}
			float health = Mth.clamp(hp / GreenLanternConfig.DOME_HP, 0f, 1f);
			// v0.17.1 durability shown in-world: the glow dims and slides from bright green toward a dark green as the
			// dome takes damage, cracks open under 50%, and under 20% the light stutters (uneven flicker + drop-outs)
			float flicker = 1f;
			if (health < GreenLanternConfig.DURABILITY_FLICKER_BELOW) {
				flicker = 0.5f + 0.5f * Mth.abs(Mth.sin(age * 1.7f) * Mth.sin(age * 0.61f + 1.3f));
				if (((int) (age * 0.5f)) % 7 == 0) {
					flicker *= 0.25f;
				}
			}
			float dim = 0.45f + 0.55f * health;
			Vec3 c = player.getPosition(partial).add(0, 1.0, 0);
			pose.pushPose();
			pose.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
			pose.mulPose(Axis.YP.rotationDegrees(age * 0.6f));
			PoseStack.Pose p = pose.last();
			shell(vc, p, radius, damaged(0x35F075, health), 0.15f * flicker * dim);
			shell(vc, p, radius * 1.015f, damaged(0xA8FFC0, health), 0.07f * flicker * dim);
			ribs(vc, p, radius * 1.005f, damaged(0x6CFF9A, health), 0.42f * flicker * dim, age);
			if (health < GreenLanternConfig.DURABILITY_CRACK_BELOW) {
				int cracks = 4 + Math.round((1f - health / GreenLanternConfig.DURABILITY_CRACK_BELOW) * 10f);
				for (int k = 0; k < cracks; k++) {
					crack(vc, p, radius * 1.008f, k, damaged(0xD8FFE4, health), 0.55f * flicker);
				}
			}
			pose.popPose();
		}
		SEEN.keySet().retainAll(live);
	}

	/** v0.17.1: a colour pulled from its bright self toward a deep, dark green as the dome's HP falls. */
	static int damaged(int rgb, float health) {
		float t = (1f - Mth.clamp(health, 0f, 1f)) * 0.8f;
		int dark = 0x0B4A1E;
		int r = Math.round(Mth.lerp(t, (rgb >> 16) & 0xFF, (dark >> 16) & 0xFF));
		int g = Math.round(Mth.lerp(t, (rgb >> 8) & 0xFF, (dark >> 8) & 0xFF));
		int b = Math.round(Mth.lerp(t, rgb & 0xFF, dark & 0xFF));
		return (r << 16) | (g << 8) | b;
	}

	/** v0.17.1: one jagged crack across the shell (a fixed pattern per index, so cracks stay put as the dome turns). */
	private static void crack(VertexConsumer vc, PoseStack.Pose p, float radius, int n, int rgb, float alpha) {
		double theta = -Math.PI / 2 + Math.PI * (0.15 + 0.7 * frac(n * 0.6180339 + 0.21));
		double phi = Math.PI * 2 * frac(n * 0.7548777 + 0.37);
		double w = 0.012;
		for (int s = 0; s < 8; s++) {
			double t1 = Mth.clamp(theta + (frac((n * 7 + s) * 0.41421356) - 0.5) * 0.22, -Math.PI / 2 + 0.05, Math.PI / 2 - 0.05);
			double p1 = phi + 0.07 + (frac((n * 5 + s) * 0.3247) - 0.5) * 0.18;
			// a thin quad from (theta, phi) to (t1, p1), widened sideways on the sphere
			double dt = t1 - theta;
			double dp = (p1 - phi) * Math.cos((theta + t1) * 0.5);
			double len = Math.max(1.0e-4, Math.sqrt(dt * dt + dp * dp));
			double ot = -dp / len * w;
			double op = dt / len * w / Math.max(0.15, Math.cos((theta + t1) * 0.5));
			quadPts(vc, p, rgb, alpha, radius, theta + ot, phi + op, t1 + ot, p1 + op, t1 - ot, p1 - op, theta - ot, phi - op);
			theta = t1;
			phi = p1;
		}
	}

	private static double frac(double v) {
		return v - Math.floor(v);
	}

	private static void quadPts(VertexConsumer vc, PoseStack.Pose p, int rgb, float alpha, float radius, double ta, double pa,
			double tb, double pb, double tc, double pc, double td, double pd) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int a = Mth.clamp((int) (alpha * 255f), 0, 255);
		float[] va = unit(ta, pa), vb = unit(tb, pb), vcc = unit(tc, pc), vd = unit(td, pd);
		vertex(vc, p, va, radius, 0, 0, r, g, b, a, va[0], va[1], va[2]);
		vertex(vc, p, vb, radius, 0, 1, r, g, b, a, va[0], va[1], va[2]);
		vertex(vc, p, vcc, radius, 1, 1, r, g, b, a, va[0], va[1], va[2]);
		vertex(vc, p, vd, radius, 1, 0, r, g, b, a, va[0], va[1], va[2]);
	}

	/** A smooth globe of quads (seen from inside too -- the render type draws both faces). */
	private static void shell(VertexConsumer vc, PoseStack.Pose p, float radius, int rgb, float alpha) {
		for (int i = 0; i < LAT; i++) {
			double t0 = Math.PI * i / LAT - Math.PI / 2;
			double t1 = Math.PI * (i + 1) / LAT - Math.PI / 2;
			for (int j = 0; j < LON; j++) {
				double p0 = 2 * Math.PI * j / LON;
				double p1 = 2 * Math.PI * (j + 1) / LON;
				quad(vc, p, rgb, alpha, radius, t0, p0, t1, p1);
			}
		}
	}

	/** Glowing construct lattice: latitude rings, meridians, and one bright band sweeping bottom to top. */
	private static void ribs(VertexConsumer vc, PoseStack.Pose p, float radius, int rgb, float alpha, float age) {
		double w = 0.035; // rib half-width in radians
		for (int ring = 1; ring < 8; ring++) {
			double t = Math.PI * ring / 8 - Math.PI / 2;
			band(vc, p, radius, rgb, alpha, t - w * 0.6, t + w * 0.6);
		}
		for (int m = 0; m < 12; m++) {
			double ph = 2 * Math.PI * m / 12;
			for (int i = 0; i < LAT; i++) {
				double t0 = Math.PI * i / LAT - Math.PI / 2;
				double t1 = Math.PI * (i + 1) / LAT - Math.PI / 2;
				double ww = w / Math.max(0.15, Math.cos((t0 + t1) * 0.5));
				quad(vc, p, rgb, alpha, radius, t0, ph - ww * 0.6, t1, ph + ww * 0.6);
			}
		}
		// the sweep: a soft bright band climbing the dome every 3 seconds
		double sweep = ((age % 60f) / 60f) * Math.PI - Math.PI / 2;
		band(vc, p, radius * 1.002f, 0xD8FFE4, alpha * 0.9f, sweep - 0.06, sweep + 0.06);
	}

	private static void band(VertexConsumer vc, PoseStack.Pose p, float radius, int rgb, float alpha, double t0, double t1) {
		for (int j = 0; j < LON; j++) {
			double p0 = 2 * Math.PI * j / LON;
			double p1 = 2 * Math.PI * (j + 1) / LON;
			quad(vc, p, rgb, alpha, radius, t0, p0, t1, p1);
		}
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose p, int rgb, float alpha, float radius, double t0, double p0,
			double t1, double p1) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int a = Mth.clamp((int) (alpha * 255f), 0, 255);
		float[] v00 = unit(t0, p0), v10 = unit(t1, p0), v11 = unit(t1, p1), v01 = unit(t0, p1);
		float nx = (v00[0] + v11[0]) * 0.5f, ny = (v00[1] + v11[1]) * 0.5f, nz = (v00[2] + v11[2]) * 0.5f;
		vertex(vc, p, v00, radius, 0, 0, r, g, b, a, nx, ny, nz);
		vertex(vc, p, v10, radius, 0, 1, r, g, b, a, nx, ny, nz);
		vertex(vc, p, v11, radius, 1, 1, r, g, b, a, nx, ny, nz);
		vertex(vc, p, v01, radius, 1, 0, r, g, b, a, nx, ny, nz);
	}

	private static float[] unit(double theta, double phi) {
		double c = Math.cos(theta);
		return new float[] { (float) (c * Math.cos(phi)), (float) Math.sin(theta), (float) (c * Math.sin(phi)) };
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose p, float[] v, float radius, float u, float uv, int r, int g,
			int b, int a, float nx, float ny, float nz) {
		vc.addVertex(p, v[0] * radius, v[1] * radius, v[2] * radius)
				.setColor(r, g, b, a)
				.setUv(u, uv)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT)
				.setNormal(p, nx, ny, nz);
	}
}
