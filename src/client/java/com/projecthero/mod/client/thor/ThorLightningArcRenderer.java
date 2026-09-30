package com.projecthero.mod.client.thor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/**
 * Draws every live {@link ThorLightningArcClient.Arc} -- Lightning Beam's continuous bolt and every hop of Chain
 * Lightning. Purely a render, same shape as {@code SpiderWebLineRenderer}: no entity is created, nothing to leak, the
 * segment simply stops being drawn once it fades out.
 *
 * <p>v0.14.4 look: each arc is a real glowing bolt instead of a bundle of 1-pixel lines -- a jagged main channel drawn
 * as camera-facing ribbons (storm-blue halo, electric-blue glow, white-hot core, see {@link ThorDraw#bolt}), a thinner
 * second channel twisting round it, a few short forks spitting off the bends, and a crackling ball of light where it
 * leaves the hammer and where it lands. The Beam is drawn heavier than a chain hop. The shape is re-seeded from the
 * game clock several times a second (held, then jumping -- never a smooth wave), and the brightness flickers with it.
 */
public final class ThorLightningArcRenderer {
	/** The Beam's reserved slot ({@code ThorPowers.LIGHTNING_ARC_SLOT_BEAM}). */
	private static final int BEAM_SLOT = 64;

	private ThorLightningArcRenderer() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(ThorLightningArcRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || context.consumers() == null) {
			return;
		}
		Camera camera = context.camera();
		MultiBufferSource consumers = context.consumers();
		PoseStack poseStack = context.matrixStack();
		if (poseStack == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);

		ThorLightningArcClient.prune(partial);
		if (ThorLightningArcClient.arcs().isEmpty()) {
			return;
		}

		double now = client.level.getGameTime() + partial;
		Vec3 cam = camera.getPosition();
		VertexConsumer vc = ThorDraw.buffer(consumers);
		for (ThorLightningArcClient.Arc arc : ThorLightningArcClient.arcs()) {
			float alpha = arc.alpha(now);
			if (alpha <= 0.0f) {
				continue;
			}
			Vec3 from = ThorLightningArcClient.fromPoint(arc, partial).subtract(cam);
			Vec3 to = ThorLightningArcClient.targetPoint(arc, partial).subtract(cam);
			// A stable per-segment offset so a multi-hop chain's strands don't all crackle in lockstep.
			double seed = arc.slot * 17.3 + arc.casterId * 3.1;
			drawArc(poseStack, vc, from, to, alpha, seed, now, arc.slot == BEAM_SLOT,
					!ThorLightningArcClient.startsAtOwnFirstPersonHand(arc));
		}
	}

	private static void drawArc(PoseStack poseStack, VertexConsumer vc, Vec3 start, Vec3 end, float alpha, double seed,
			double now, boolean beam, boolean startFlare) {
		double length = end.subtract(start).length();
		if (length < 1.0E-3) {
			return;
		}
		// a new jagged shape ~7 times a second, and a flicker in brightness that jumps with it
		double frame = Math.floor(now * (beam ? 0.45 : 0.35));
		double frameSeed = seed + frame * 31.7;
		float flicker = 0.78f + 0.22f * (float) ThorDraw.hash(frameSeed + 0.5);
		float a = alpha * flicker;
		float width = beam ? 1.35f : 1.0f;

		int nodes = Math.max(6, Math.min(40, (int) (length / 1.4)));
		double amplitude = Math.min(0.75, 0.15 + length * 0.02);
		PoseStack.Pose pose = poseStack.last();

		Vec3[] main = ThorDraw.jagged(start, end, nodes, amplitude, frameSeed);
		ThorDraw.bolt(vc, pose, main, width, a);

		// a thinner second channel twisting round the first
		Vec3[] second = ThorDraw.jagged(start, end, nodes, amplitude * 0.8, frameSeed + 101.0);
		ThorDraw.ribbon(vc, pose, second, 0.07f * width, ThorDraw.GLOW, 0.4f * a);
		ThorDraw.ribbon(vc, pose, second, 0.022f * width, ThorDraw.CORE, 0.7f * a);

		// short forks spitting off a few of the bends
		int forks = beam ? 3 : 2;
		for (int k = 0; k < forks; k++) {
			double h = ThorDraw.hash(frameSeed + 57.0 + k * 9.1);
			int at = 1 + (int) (h * (nodes - 2));
			Vec3 base = main[Math.max(1, Math.min(nodes - 1, at))];
			Vec3 dir = end.subtract(start).normalize();
			Vec3 jitter = new Vec3(ThorDraw.hash(frameSeed + k * 3.3) - 0.5, ThorDraw.hash(frameSeed + k * 5.9) - 0.5,
					ThorDraw.hash(frameSeed + k * 8.7) - 0.5).normalize();
			double forkLen = Math.min(2.6, 0.6 + length * 0.12) * (0.6 + 0.4 * ThorDraw.hash(frameSeed + k * 1.7));
			Vec3 tip = base.add(dir.scale(forkLen * 0.55)).add(jitter.scale(forkLen * 0.75));
			Vec3[] fork = ThorDraw.jagged(base, tip, 3, forkLen * 0.18, frameSeed + k * 43.0);
			ThorDraw.ribbon(vc, pose, fork, 0.06f * width, ThorDraw.GLOW, 0.4f * a);
			ThorDraw.ribbon(vc, pose, fork, 0.02f * width, ThorDraw.CORE, 0.8f * a);
		}

		// crackling balls of light at the hammer and at the point of impact
		float spin = (float) (now * 40.0 % 360.0);
		if (startFlare) {
			// (not in your own first-person view, where it would sit in the middle of the screen)
			ThorDraw.flare(vc, poseStack, start, (beam ? 0.2f : 0.15f) * flicker, spin, 0.85f * a);
		}
		ThorDraw.flare(vc, poseStack, end, (beam ? 0.34f : 0.26f) * flicker, -spin, 0.8f * a);
	}
}
