package com.projecthero.mod.client.quirk.zerogravity;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.quirk.fx.QuirkFxDraw;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Zero Gravity revamp, "Touch marks" (docs 3.7): everything Uravity floats wears her glowing pink fingertip prints --
 * five soft pads in an arc (the framework's {@code decal_print} texture, additive) -- for every viewer, from the synced marks
 * list ({@link ZeroGravityHud#marks}). A creature carries two, on its chest and its back, turning with it as it slowly
 * revolves; a block or an item one on the face toward the viewer. They pulse gently.
 */
public final class ZeroGravityPrints {
	private static final int PINK = 0xFF6FB4;

	private ZeroGravityPrints() {
	}

	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		int[][] marks = ZeroGravityHud.marks();
		if (marks.length == 0) {
			return;
		}
		Camera camera = context.camera();
		Vec3 cam = camera.getPosition();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		float t = mc.level.getGameTime() + partial;
		for (int[] ids : marks) {
			for (int id : ids) {
				Entity e = mc.level.getEntity(id);
				if (e == null || e.isInvisible() || e.distanceToSqr(cam) > 64 * 64) {
					continue;
				}
				float pulse = 0.75f + 0.25f * Mth.sin(t * 0.18f + id);
				Vec3 at = e.getPosition(partial);
				pose.pushPose();
				pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
				if (e instanceof LivingEntity le) {
					float yaw = Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot);
					float half = le.getBbWidth() * 0.5f + 0.03f;
					float size = Math.max(0.22f, Math.min(0.5f, le.getBbWidth() * 0.45f));
					pose.translate(0, le.getBbHeight() * 0.6f, 0);
					pose.mulPose(Axis.YP.rotationDegrees(-yaw));
					print(pose, buffers, 0, 0, half, size, pulse);
					pose.mulPose(Axis.YP.rotationDegrees(180f));
					print(pose, buffers, 0, -le.getBbHeight() * 0.08f, half, size * 0.85f, pulse * 0.9f);
				} else if (e instanceof Display.BlockDisplay || e instanceof ItemEntity) {
					boolean item = e instanceof ItemEntity;
					Vec3 c = item ? at.add(0, 0.25, 0) : at;
					Vec3 to = cam.subtract(c);
					if (to.lengthSqr() > 1.0e-4) {
						to = to.normalize();
					}
					float off = item ? 0.2f : 0.52f;
					pose.translate(to.x * off + (item ? 0 : 0), (item ? 0.25 : 0) + to.y * off, to.z * off);
					Quaternionf q = new Quaternionf(camera.rotation());
					pose.mulPose(q);
					pose.mulPose(Axis.XP.rotationDegrees(-90f));
					pose.mulPose(Axis.YP.rotationDegrees((id * 47) % 360));
					printFlat(pose, buffers, item ? 0.16f : 0.34f, pulse);
				}
				pose.popPose();
			}
		}
	}

	/** A print on a vertical surface {@code dz} out in front (local +Z), centred at (x, y). */
	private static void print(PoseStack pose, MultiBufferSource buffers, float x, float y, float dz, float h, float a) {
		VertexConsumer vc = QuirkFxDraw.glow(buffers, QuirkFxDraw.DECALS[3]);
		PoseStack.Pose last = pose.last();
		int[] c = { PINK, PINK, PINK, PINK };
		float[] al = { a, a, a, a };
		QuirkFxDraw.gquad(vc, last, new float[][] { { x - h, y + h, dz, 0, 0 }, { x - h, y - h, dz, 0, 1 }, { x + h, y - h, dz, 1, 1 },
				{ x + h, y + h, dz, 1, 0 } }, c, al);
	}

	/** A print lying in the local XZ plane. */
	private static void printFlat(PoseStack pose, MultiBufferSource buffers, float h, float a) {
		VertexConsumer vc = QuirkFxDraw.glow(buffers, QuirkFxDraw.DECALS[3]);
		PoseStack.Pose last = pose.last();
		int[] c = { PINK, PINK, PINK, PINK };
		float[] al = { a, a, a, a };
		QuirkFxDraw.gquad(vc, last, new float[][] { { -h, 0, -h, 0, 0 }, { -h, 0, h, 0, 1 }, { h, 0, h, 1, 1 }, { h, 0, -h, 1, 0 } }, c, al);
	}
}
