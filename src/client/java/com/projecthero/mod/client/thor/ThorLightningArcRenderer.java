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

	/** v0.13.6: fewer, larger nodes than before -- vanilla's own {@code LightningBolt} zigzags in a
	 * handful of sharp jumps rather than a smooth wave, and this is the main thing that reads as
	 * "blocky" rather than "crackly wire." */
	private static final int BLOCKY_NODES = 6;
	/** How many near-parallel copies of the core strand are drawn (each nudged a hair sideways) to fake
	 * real width -- {@code RenderType.lines()} is a 1px GL line with no width control, so "thicker" has
	 * to come from bundling several of them into a tight bundle instead. */
	private static final int CORE_THICKNESS_PLIES = 5;
	private static final double CORE_THICKNESS_RADIUS = 0.05;

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

		int nodes = Math.max(BLOCKY_NODES, (int) (length / 4.0));
		double amplitude = Math.min(0.6, 0.12 + length * 0.014);

		// Core bolt: a handful of sharp, blocky zigzag segments (one consistent path per frame, not a
		// smooth travelling wave), drawn as a tight bundle of parallel plies so it reads as thick.
		Vec3[] corePath = blockyPath(start, end, perpA, perpB, nodes, amplitude, timeSeed, 0);
		for (int ply = 0; ply < CORE_THICKNESS_PLIES; ply++) {
			double plyAngle = (Math.PI * 2.0 * ply) / CORE_THICKNESS_PLIES;
			Vec3 plyOffset = perpA.scale(Math.cos(plyAngle) * CORE_THICKNESS_RADIUS)
					.add(perpB.scale(Math.sin(plyAngle) * CORE_THICKNESS_RADIUS));
			drawPolyline(buffer, pose, corePath, plyOffset, alpha, CORE_R, CORE_G, CORE_B);
		}

		// Fainter secondary crackle strands, thin and offset further out, for texture around the core.
		for (int strand = 1; strand < STRAND_COUNT; strand++) {
			Vec3[] strandPath = blockyPath(start, end, perpA, perpB, nodes, amplitude * 0.7, timeSeed, strand);
			drawPolyline(buffer, pose, strandPath, Vec3.ZERO, alpha * 0.4f, CORE_R, CORE_G, CORE_B);
		}

		poseStack.popPose();
	}

	/** Builds a jagged node path from {@code start} to {@code end}: a fixed random offset per node
	 * (re-seeded on {@code timeSeed} so it still crackles over time) rather than a continuous sine wave,
	 * so consecutive nodes connect with sharp angles instead of a smooth curve. */
	private static Vec3[] blockyPath(Vec3 start, Vec3 end, Vec3 perpA, Vec3 perpB, int nodes,
			double amplitude, double timeSeed, int strandIndex) {
		Vec3[] path = new Vec3[nodes + 1];
		path[0] = start;
		path[nodes] = end;
		for (int i = 1; i < nodes; i++) {
			double t = (double) i / nodes;
			// Discrete per-node seed (floor'd, not continuous) so the shape holds a jagged pose for a
			// short stretch of time and then jumps to a new one, instead of smoothly animating.
			double nodeSeed = Math.floor(timeSeed * 6.0) + i * 13.7 + strandIndex * 5.3;
			double hash = fract(Math.sin(nodeSeed) * 43758.5453);
			double hash2 = fract(Math.sin(nodeSeed * 1.37 + 7.1) * 12543.657);
			double edgeTaper = Math.sin(Math.PI * t); // zero at both ends, so it always meets its endpoints
			double a = (hash * 2.0 - 1.0) * amplitude * edgeTaper;
			double b = (hash2 * 2.0 - 1.0) * amplitude * edgeTaper;
			path[i] = start.lerp(end, t).add(perpA.scale(a)).add(perpB.scale(b));
		}
		return path;
	}

	private static double fract(double v) {
		return v - Math.floor(v);
	}

	private static void drawPolyline(VertexConsumer buffer, PoseStack.Pose pose, Vec3[] path, Vec3 offset,
			float alpha, float r, float g, float b) {
		for (int i = 1; i < path.length; i++) {
			Vec3 prev = path[i - 1].add(offset);
			Vec3 p = path[i].add(offset);
			Vec3 d = p.subtract(prev);
			double len = Math.max(1.0e-6, d.length());
			float nx = (float) (d.x / len);
			float ny = (float) (d.y / len);
			float nz = (float) (d.z / len);
			buffer.addVertex(pose.pose(), (float) prev.x, (float) prev.y, (float) prev.z)
					.setColor(r, g, b, alpha).setNormal(pose, nx, ny, nz);
			buffer.addVertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
					.setColor(r, g, b, alpha).setNormal(pose, nx, ny, nz);
		}
	}
}
