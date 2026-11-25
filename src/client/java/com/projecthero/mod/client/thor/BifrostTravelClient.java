package com.projecthero.mod.client.thor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.stormbreaker.BifrostTravel;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: the Bifrost Travel cinematic on the client ({@link BifrostTravel}); the column itself is {@link BifrostColumnDraw}.
 * <ul>
 * <li>{@link #raise}: the caster's raise weight from the synced {@link BifrostTravel#CAST} clock -- Stormbreaker up over the
 * first 10 ticks, held aloft through the rise and the descent, lowered over the last {@link BifrostTravel#POSE_LOWER} ticks
 * (pose: {@code BifrostRaisePoseMixin}, the axe turned to point at the sky: {@code BifrostRaiseGripMixin}, first person:
 * {@code BifrostRaiseFirstPersonMixin});</li>
 * <li>lightning crackling over the raised axe head until the strike, then the odd arc while it is held up;</li>
 * <li>for a rider ({@link BifrostTravel#RIDE} clock): a soft flash when the beam strikes, a white-out at the transport, and
 * a shimmer of rainbow at the screen edges while they are in the beam.</li>
 * </ul>
 */
public final class BifrostTravelClient {
	private BifrostTravelClient() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(BifrostTravelClient::render);
		HudRenderCallback.EVENT.register(BifrostTravelClient::hud);
	}

	/** Ticks since {@code p} raised Stormbreaker for the Bifrost (with partial), or -1. */
	public static float castTime(Player p, float partial) {
		long start = p.getAttachedOrElse(BifrostTravel.CAST, 0L);
		if (start == 0L) {
			return -1f;
		}
		float t = p.level().getGameTime() - start + partial;
		return t < 0 ? 0 : t;
	}

	/** 0..1, how far Stormbreaker is raised to the sky. */
	public static float raise(Player p, float partial) {
		float t = castTime(p, partial);
		if (t < 0) {
			return 0f;
		}
		float up = Mth.clamp(t / 10f, 0f, 1f);
		up = up * up * (3 - 2 * up);
		float down = Mth.clamp((BifrostTravel.LAND - t) / BifrostTravel.POSE_LOWER, 0f, 1f);
		return up * down;
	}

	/** Roughly where the raised axe head is (world space) for the arcs. */
	private static Vec3 axeHead(Player p, float partial) {
		float yaw = Mth.lerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		float side = p.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(0.36 * side);
		return p.getPosition(partial).add(right).add(0, 2.85, 0);
	}

	static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = ctx.camera().getPosition();
		MultiBufferSource buffers = ctx.consumers();
		PoseStack pose = ctx.matrixStack();
		for (Player p : mc.level.players()) {
			float t = castTime(p, partial);
			if (t < 0 || t > BifrostTravel.LAND) {
				continue;
			}
			if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
				continue; // the arcs would sit in front of the camera; first person shows its own raised axe
			}
			VertexConsumer vc = ThorDraw.buffer(buffers);
			Vec3 head = axeHead(p, partial).subtract(cam);
			double seed = Math.floor((p.level().getGameTime() + partial) / 2.0) * 3.3 + p.getId();
			float k = Mth.clamp(t / BifrostTravel.STRIKE, 0f, 1f);
			int arcs = t < BifrostTravel.STRIKE ? 1 + (int) (k * 4) : (ThorDraw.hash(seed) > 0.6 ? 1 : 0);
			for (int i = 0; i < arcs; i++) {
				double a = ThorDraw.hash(seed + i) * Math.PI * 2;
				Vec3 tip = head.add(Math.cos(a) * (0.5 + k * 0.8), 0.2 + ThorDraw.hash(seed + i + 4) * (0.4 + k * 1.6), Math.sin(a) * (0.5 + k * 0.8));
				ThorDraw.bolt(vc, pose.last(), ThorDraw.jagged(head, tip, 5, 0.14, seed + i), 0.35f + k * 0.35f, 0.9f);
			}
			if (t < BifrostTravel.STRIKE + 4) {
				pose.pushPose();
				pose.translate(head.x, head.y, head.z);
				float s = 0.12f + k * 0.22f;
				TurboDraw.box(vc, pose.last(), s, s, s, ThorDraw.GLOW, 0.2f + k * 0.35f);
				pose.popPose();
			}
			if (t >= BifrostTravel.STRIKE - 1 && t < BifrostTravel.STRIKE + 3) {
				// the instant the bridge answers: a bolt from the axe up into the sky
				ThorDraw.bolt(vc, pose.last(), ThorDraw.jagged(head, head.add(0, 40, 0), 16, 0.9, seed), 1.6f, 0.9f);
			}
		}
	}

	static void hud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) {
			return;
		}
		long start = mc.player.getAttachedOrElse(BifrostTravel.RIDE, 0L);
		if (start == 0L) {
			return;
		}
		float t = mc.player.level().getGameTime() - start + delta.getGameTimeDeltaPartialTick(false);
		int w = g.guiWidth();
		int h = g.guiHeight();
		// a shimmer of rainbow at the edges while in the beam
		if (t > BifrostTravel.STRIKE && t < BifrostTravel.LAND) {
			float in = Mth.clamp((t - BifrostTravel.STRIKE) / 8f, 0f, 1f) * Mth.clamp((BifrostTravel.LAND - t) / 8f, 0f, 1f);
			int band = Math.max(18, h / 7);
			int top = withAlpha(BifrostColumnDraw.rgb(t * 0.015f), 0.30f * in);
			int bottom = withAlpha(BifrostColumnDraw.rgb(t * 0.015f + 0.5f), 0.30f * in);
			int left = withAlpha(BifrostColumnDraw.rgb(t * 0.015f + 0.25f), 0.24f * in);
			int right = withAlpha(BifrostColumnDraw.rgb(t * 0.015f + 0.75f), 0.24f * in);
			g.fillGradient(0, 0, w, band, top, top & 0x00FFFFFF);
			g.fillGradient(0, h - band, w, h, bottom & 0x00FFFFFF, bottom);
			horizontalGradient(g, 0, 0, band, h, left, left & 0x00FFFFFF);
			horizontalGradient(g, w - band, 0, w, h, right & 0x00FFFFFF, right);
		}
		float a = 0f;
		if (t >= BifrostTravel.STRIKE && t < BifrostTravel.STRIKE + 8) {
			a = 0.45f * (1f - (t - BifrostTravel.STRIKE) / 8f);
		} else if (t >= BifrostTravel.TRANSPORT - 4 && t < BifrostTravel.TRANSPORT) {
			a = 0.95f * (t - (BifrostTravel.TRANSPORT - 4)) / 4f; // white-out at the top...
		} else if (t >= BifrostTravel.TRANSPORT && t < BifrostTravel.TRANSPORT + 12) {
			a = 0.95f * (1f - (t - BifrostTravel.TRANSPORT) / 12f); // ...clearing on the far side
		}
		if (a > 0.01f) {
			g.fill(0, 0, w, h, ((int) (a * 255) << 24) | 0xF4F8FF);
		}
	}

	private static int withAlpha(int rgb, float a) {
		return (Mth.clamp((int) (a * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
	}

	/** fillGradient is vertical only: a left-to-right gradient as thin columns. */
	private static void horizontalGradient(GuiGraphics g, int x0, int y0, int x1, int y1, int from, int to) {
		int n = Math.max(1, x1 - x0);
		for (int i = 0; i < n; i += 2) {
			float k = i / (float) n;
			int a = (int) Mth.lerp(k, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF);
			int c = (a << 24) | (from & 0xFFFFFF);
			g.fill(x0 + i, y0, Math.min(x1, x0 + i + 2), y1, c);
		}
	}
}
