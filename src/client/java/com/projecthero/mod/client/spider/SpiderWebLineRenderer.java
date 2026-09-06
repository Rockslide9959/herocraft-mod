package com.projecthero.mod.client.spider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.spider.data.SpiderManState;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the web line, for every swinging player the client can see.
 *
 * <p>Purely a render: a handful of line segments straight into the world-render buffer, drawn from
 * the synced anchor. No entity of any kind is created, so there is nothing to accumulate, nothing to
 * leak and nothing left behind when the line is released -- the state flag flips and the line simply
 * stops being drawn on the next frame.
 *
 * <p>The line sags slightly toward its middle rather than being a taut straight segment, which reads
 * far more like webbing and costs one interpolation per segment.
 */
public final class SpiderWebLineRenderer {
	private static final int SEGMENTS = 10;
	private static final float R = 0.94f;
	private static final float G = 0.96f;
	private static final float B = 1.0f;

	private SpiderWebLineRenderer() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(SpiderWebLineRenderer::render);
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

		for (Player player : client.level.players()) {
			SpiderManState state = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
			if (state == null || !state.swinging) {
				continue;
			}
			drawLine(poseStack, consumers, camera, player, partial,
					new Vec3(state.anchorX, state.anchorY, state.anchorZ), state.swingHandRight);
		}
	}

	private static void drawLine(PoseStack poseStack, MultiBufferSource consumers, Camera camera,
			Player player, float partial, Vec3 anchor, boolean rightHand) {
		// v0.6.20: the line leaves the actual fist. The swing arm is pinned to a FIXED overhead pose in
		// HumanoidModelMixin (xRot -2.65, yRot 0, zRot -/+0.15 on the firing arm), so the fist's offset
		// from the body is deterministic -- resolve that fixed pose into world space rather than
		// guessing at a shoulder height and walking toward the anchor (which drifted above the hand
		// whenever the anchor was off to the side). Numbers are the fixed pose's hand tip (shoulder
		// pivot + a 12px arm rotated by that pose), divided by 16 into blocks:
		//   up  ~2.03   body-right (firing side)  ~0.41   body-forward  ~0.36
		double px = net.minecraft.util.Mth.lerp(partial, player.xo, player.getX());
		double py = net.minecraft.util.Mth.lerp(partial, player.yo, player.getY());
		double pz = net.minecraft.util.Mth.lerp(partial, player.zo, player.getZ());
		float bodyYaw = net.minecraft.util.Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		Vec3 bodyRight = Vec3.directionFromRotation(0.0f, bodyYaw + 90.0f);
		Vec3 bodyForward = Vec3.directionFromRotation(0.0f, bodyYaw);
		double side = rightHand ? 1.0 : -1.0;
		Vec3 eye = new Vec3(px, py, pz)
				.add(0.0, 2.03, 0.0)
				.add(bodyRight.scale(side * 0.41))
				.add(bodyForward.scale(0.36));

		Vec3 cam = camera.getPosition();
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		var pose = poseStack.last();
		VertexConsumer buffer = consumers.getBuffer(RenderType.lineStrip());

		double slack = Math.min(0.9, eye.distanceTo(anchor) * 0.035);
		for (int i = 0; i <= SEGMENTS; i++) {
			double t = (double) i / SEGMENTS;
			Vec3 p = eye.lerp(anchor, t);
			// a parabola that is zero at both ends and deepest in the middle
			p = p.subtract(0, slack * (4.0 * t * (1.0 - t)), 0);
			buffer.addVertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
					.setColor(R, G, B, 0.9f)
					.setNormal(pose, 0.0f, 1.0f, 0.0f);
		}
		poseStack.popPose();
	}
}
