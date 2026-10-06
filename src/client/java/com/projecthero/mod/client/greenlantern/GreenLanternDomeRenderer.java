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
			// low HP: the light stutters
			float flicker = health > 0.3f ? 1f : 0.55f + 0.45f * Mth.abs(Mth.sin(age * (1.6f - health)));
			Vec3 c = player.getPosition(partial).add(0, 1.0, 0);
			pose.pushPose();
			pose.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
			pose.mulPose(Axis.YP.rotationDegrees(age * 0.6f));
			PoseStack.Pose p = pose.last();
			shell(vc, p, radius, 0x35F075, 0.15f * flicker);
			shell(vc, p, radius * 1.015f, 0xA8FFC0, 0.07f * flicker);
			ribs(vc, p, radius * 1.005f, 0x6CFF9A, 0.42f * flicker, age);
			pose.popPose();
		}
		SEEN.keySet().retainAll(live);
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
