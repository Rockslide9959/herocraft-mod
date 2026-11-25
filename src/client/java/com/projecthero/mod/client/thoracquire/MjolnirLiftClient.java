package com.projecthero.mod.client.thoracquire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.client.thor.ThorDraw;
import com.projecthero.mod.thoracquire.MjolnirLift;
import com.projecthero.mod.thoracquire.ThorAcquireAttachments;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: the Mjolnir lift cutscene on the client, from the synced {@link ThorAcquireAttachments#MJOLNIR_LIFT} clock:
 * <ul>
 * <li>{@link #raise}: how far the arm is raised (the pose mixin {@code ThorAcquireLiftPoseMixin} lifts the hammer arm
 * straight up and the head back): up over 0-18, held, lowered over the last 10 ticks;</li>
 * <li>before the strike: arcs crawling over the hammer head; at {@link MjolnirLift#STRIKE}: a thick lightning column from the
 * sky onto the player that keeps pouring until the bind; at {@link MjolnirLift#BIND}: an expanding ring and a sphere of light;</li>
 * <li>a white screen flash for the lifter at the strike and at the bind.</li>
 * </ul>
 */
public final class MjolnirLiftClient {
	private MjolnirLiftClient() {
	}

	/** Ticks into the lift for {@code p} (with partial), or -1. */
	public static float elapsed(Player p, float partial) {
		long start = p.getAttachedOrElse(ThorAcquireAttachments.MJOLNIR_LIFT, 0L);
		if (start == 0L) {
			return -1f;
		}
		float t = p.level().getGameTime() - start + partial;
		return t < 0 ? 0 : t;
	}

	/** 0..1, the arm raise weight at {@code t}. */
	public static float raise(float t) {
		if (t < 0) {
			return 0f;
		}
		float up = Math.min(1f, t / 18f);
		up = up * up * (3 - 2 * up);
		float down = t > MjolnirLift.DURATION - 10 ? Math.max(0f, (MjolnirLift.DURATION - t) / 10f) : 1f;
		return up * down;
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
			float t = elapsed(p, partial);
			if (t < 0 || t > MjolnirLift.DURATION) {
				continue;
			}
			VertexConsumer vc = ThorDraw.buffer(buffers);
			Vec3 feet = p.getPosition(partial).subtract(cam);
			Vec3 head = feet.add(0, 2.85, 0); // hammer head held high
			double seed = Math.floor((p.level().getGameTime() + partial) / 2.0) * 3.3 + p.getId();
			if (t < MjolnirLift.STRIKE) {
				float k = t / MjolnirLift.STRIKE;
				int arcs = 1 + (int) (k * 4);
				for (int i = 0; i < arcs; i++) {
					double a = ThorDraw.hash(seed + i) * Math.PI * 2;
					Vec3 tip = head.add(Math.cos(a) * (0.6 + k), 0.3 + ThorDraw.hash(seed + i + 4) * k * 2.5, Math.sin(a) * (0.6 + k));
					ThorDraw.bolt(vc, pose.last(), ThorDraw.jagged(head, tip, 5, 0.15, seed + i), 0.4f + k * 0.4f, 0.9f);
				}
				// a faint gathering glow round the head
				pose.pushPose();
				pose.translate(head.x, head.y, head.z);
				TurboDraw.box(vc, pose.last(), 0.15f + k * 0.2f, 0.15f + k * 0.2f, 0.15f + k * 0.2f, ThorDraw.GLOW, 0.25f + k * 0.3f);
				pose.popPose();
			} else if (t < MjolnirLift.BIND) {
				// the column: a thick bolt and a second twisting one, sky to hammer, and a cylinder of light round the player
				Vec3 sky = head.add(0, 60, 0);
				ThorDraw.bolt(vc, pose.last(), ThorDraw.jagged(sky, head, 22, 1.4, seed), 3.2f, 1f);
				ThorDraw.bolt(vc, pose.last(), ThorDraw.jagged(sky.add(1.5, 0, -1), head, 18, 2.0, seed + 5), 1.4f, 0.7f);
				float fade = 1f - (t - MjolnirLift.STRIKE) / (MjolnirLift.BIND - MjolnirLift.STRIKE) * 0.4f;
				ThorDraw.wall(vc, pose.last(), feet, 0.9f, 3.4f, ThorDraw.GLOW, 0.35f * fade, 32);
				pose.pushPose();
				pose.translate(feet.x, feet.y + 1.2, feet.z);
				TurboDraw.box(vc, pose.last(), 0.6f, 1.5f, 0.6f, 0xFFFFFF, 0.18f * fade);
				pose.popPose();
			} else {
				float k = Math.min(1f, (t - MjolnirLift.BIND) / 14f);
				float a = 1f - k;
				ThorDraw.ring(vc, pose.last(), feet.add(0, 0.05, 0), 0.5f + k * 9f, 1.5f + k * 10f, ThorDraw.GLOW, 0.8f * a, 48);
				pose.pushPose();
				pose.translate(feet.x, feet.y + 1.2, feet.z);
				float s = 0.6f + k * 3.5f;
				TurboDraw.box(vc, pose.last(), s, s, s, 0xFFFFFF, 0.35f * a);
				pose.popPose();
			}
		}
	}

	static void hud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		float t = elapsed(mc.player, delta.getGameTimeDeltaPartialTick(false));
		if (t < 0) {
			return;
		}
		float a = 0f;
		if (t >= MjolnirLift.STRIKE && t < MjolnirLift.STRIKE + 8) {
			a = 0.85f * (1f - (t - MjolnirLift.STRIKE) / 8f);
		} else if (t >= MjolnirLift.BIND && t < MjolnirLift.BIND + 12) {
			a = 0.95f * (1f - (t - MjolnirLift.BIND) / 12f);
		}
		if (a > 0.01f) {
			int alpha = (int) (a * 255) << 24;
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), alpha | 0xE8F2FF);
		}
	}
}
