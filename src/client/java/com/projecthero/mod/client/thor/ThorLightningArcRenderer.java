package com.projecthero.mod.client.thor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

/**
 * Draws every live {@link ThorLightningArcClient.Arc} as a crackling, jagged multi-strand line instead
 * of a straight line of particles (v0.13.4) -- Lightning Beam's continuous bolt and every hop of Chain
 * Lightning both go through here. Purely a render, same shape as {@code SpiderWebLineRenderer}: no
 * entity is created, nothing to leak, the segment simply stops being drawn once it fades out.
 *
 * <p>The jaggedness is a helical wobble around the straight start-to-end line -- two perpendicular axes
 * mixed with different phases, so it reads as jagged from any camera angle, tapering to zero at both
 * ends so it always connects exactly to its real endpoints. It is re-randomized every frame from the
 * game clock (not from the network payload), which is what makes it flicker/crackle continuously for as
 * long as the segment is held, rather than holding one static zigzag shape.
 */
public final class ThorLightningArcRenderer {
	private static final float CORE_R = 0.85f;
	private static final float CORE_G = 0.90f;
	private static final float CORE_B = 1.0f;
	/** Secondary crackle strands, fainter than the core bolt. */
	private static final int STRAND_COUNT = 3;

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

		double timeSeed = (client.level.getGameTime() + partial) / 20.0;
		double now = client.level.getGameTime() + partial;

		for (ThorLightningArcClient.Arc arc : ThorLightningArcClient.arcs()) {
			float alpha = arc.alpha(now);
			if (alpha <= 0.0f) {
				continue;
			}
			Vec3 from = ThorLightningArcClient.fromPoint(arc, partial);
			Vec3 to = ThorLightningArcClient.targetPoint(arc, partial);
			// A stable per-segment offset so a multi-hop chain's strands don't all crackle in lockstep.
			double seed = timeSeed + arc.slot * 1.7 + arc.casterId * 0.31;
			drawArc(poseStack, consumers, camera, from, to, alpha, seed);
		}
	}

	private static void drawArc(PoseStack poseStack, MultiBufferSource consumers, Camera camera,
			Vec3 start, Vec3 end, float alpha, double timeSeed) {
		Vec3 delta = end.subtract(start);
		double length = delta.length();
		if (length < 1.0E-4) {
			return;
		}

		Vec3 cam = camera.getPosition();
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		var pose = poseStack.last();
		VertexConsumer buffer = consumers.getBuffer(RenderType.lines());

		Vec3 dir = delta.scale(1.0 / length);
		Vec3 ref = Math.abs(dir.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
		Vec3 perpA = dir.cross(ref).normalize();
		Vec3 perpB = dir.cross(perpA).normalize();

		int segments = Math.max(6, (int) (length * 2.5));
		double amplitude = Math.min(0.5, 0.08 + length * 0.012);

		for (int strand = 0; strand < STRAND_COUNT; strand++) {
			double phase = timeSeed * 9.0 + strand * 2.4;
			double freq = 3.0 + strand * 0.9;
			double strandAmplitude = strand == 0 ? amplitude : amplitude * 0.6;
			float strandAlpha = alpha * (strand == 0 ? 1.0f : 0.4f);

			Vec3 prev = null;
			for (int i = 0; i <= segments; i++) {
				double t = (double) i / segments;
				double wobble = strandAmplitude * Math.sin(Math.PI * t);
				double angle = phase + t * freq * Math.PI * 2.0;
				Vec3 offset = perpA.scale(Math.cos(angle) * wobble).add(perpB.scale(Math.sin(angle) * wobble));
				Vec3 p = start.lerp(end, t).add(offset);
				if (prev != null) {
					Vec3 d = p.subtract(prev);
					double len = Math.max(1.0e-6, d.length());
					float nx = (float) (d.x / len);
					float ny = (float) (d.y / len);
					float nz = (float) (d.z / len);
					buffer.addVertex(pose.pose(), (float) prev.x, (float) prev.y, (float) prev.z)
							.setColor(CORE_R, CORE_G, CORE_B, strandAlpha).setNormal(pose, nx, ny, nz);
					buffer.addVertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
							.setColor(CORE_R, CORE_G, CORE_B, strandAlpha).setNormal(pose, nx, ny, nz);
				}
				prev = p;
			}
		}

		poseStack.popPose();
	}
}
