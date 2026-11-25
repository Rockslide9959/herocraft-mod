package com.projecthero.mod.client.shazam.journey;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Shazam acquisition: <b>the illusion of the subway stairway</b>, drawn only for the player it appears to. In its own
 * frame (origin at the top step's near edge on the ground, +z down the stairs): eight stone steps going down between white-tiled
 * walls to a landing and a dark doorway (seen through a depth "hole" in the ground, {@link JourneyRenderTypes#HOLE}, so it
 * reads as a real stairwell that isn't there; deeper parts darker), green iron railings round the opening with two globe lamps at the entrance, a shimmering gold arch over the
 * top step and an old green sign on it -- "SUBWAY" and "Eternity Line". The whole thing is pale and ghostly: it fades in, wavers,
 * flickers and now and then nearly vanishes.
 */
final class IllusionRenderer {
	private static final ResourceLocation STEPS = ResourceLocation.withDefaultNamespace("textures/block/stone_bricks.png");
	private static final ResourceLocation TILES = ResourceLocation.withDefaultNamespace("textures/block/quartz_bricks.png");
	private static final ResourceLocation FLOOR = ResourceLocation.withDefaultNamespace("textures/block/polished_andesite.png");
	private static final ResourceLocation DARK = ResourceLocation.withDefaultNamespace("textures/block/black_concrete.png");
	private static final ResourceLocation IRON = ResourceLocation.withDefaultNamespace("textures/block/iron_block.png");
	private static final ResourceLocation GLOW = ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
	private static final ResourceLocation PLATE = ResourceLocation.withDefaultNamespace("textures/block/green_concrete.png");

	static final int STEP_COUNT = 8;
	static final float STEP = 0.5f;
	static final float HALF_W = 1.5f;
	static final float DEPTH = STEP_COUNT * STEP;
	static final float LANDING = STEP_COUNT * STEP + 1.6f;

	private IllusionRenderer() {
	}

	/**
	 * Draws the illusion at {@code at} descending toward {@code yaw}, {@code alpha} 0..1 overall, {@code t} its age in ticks
	 * (for the shimmer); {@code solid} 0..1 steadies it (the descent).
	 */
	static void draw(PoseStack ps, MultiBufferSource.BufferSource buffers, Vec3 cam, Vec3 at, float yaw, float alpha, float t, float solid) {
		if (alpha <= 0.01f) {
			return;
		}
		// the flicker: a waver, and now and then it nearly goes out
		float waver = 0.78f + 0.14f * Mth.sin(t * 0.37f) + 0.08f * Mth.sin(t * 1.73f + 0.6f);
		double drop = Math.abs(Math.sin(Math.floor(t / 2.0) * 12.9898) * 43758.5453) % 1.0;
		if (drop < 0.07 && solid < 0.5f) {
			waver *= 0.3f;
		}
		float a = Mth.clamp(alpha * Mth.lerp(solid, waver, 1.0f), 0f, 1f);
		ps.pushPose();
		ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		ps.mulPose(Axis.YP.rotationDegrees(-yaw));
		int light = LightTexture.FULL_BRIGHT;

		// ---- the stairwell: first push the depth back to the far plane inside the opening (JourneyRenderTypes.HOLE), so what
		// follows shows only through it, like a real hole; then the walls, the landing and doorway, and the steps
		PoseStack.Pose pose = ps.last();
		org.joml.Matrix4f mat = pose.pose();
		VertexConsumer hole = buffers.getBuffer(JourneyRenderTypes.HOLE);
		hole.addVertex(mat, -HALF_W, 0.003f, 0f).setColor(0, 0, 0, 255);
		hole.addVertex(mat, HALF_W, 0.003f, 0f).setColor(0, 0, 0, 255);
		hole.addVertex(mat, HALF_W, 0.003f, LANDING).setColor(0, 0, 0, 255);
		hole.addVertex(mat, -HALF_W, 0.003f, LANDING).setColor(0, 0, 0, 255);
		buffers.endBatch(JourneyRenderTypes.HOLE);
		RenderType wallsType = RenderType.entityTranslucent(TILES);
		VertexConsumer walls = buffers.getBuffer(wallsType);
		for (int side = -1; side <= 1; side += 2) {
			float x = side * HALF_W;
			for (int i = 0; i < 6; i++) {
				float z0 = i * (LANDING / 6f), z1 = (i + 1) * (LANDING / 6f);
				float bottom = -Math.min(DEPTH, z1) - 0.2f;
				int c = tint(0.75f - 0.07f * i, a * 0.85f);
				Quads.wallX(walls, pose, z0, bottom, z1, 0f, x, side < 0, 0, 0, (z1 - z0), -bottom, c, light);
			}
		}
		buffers.endBatch(wallsType);
		RenderType darkType = RenderType.entityTranslucent(DARK);
		VertexConsumer dark = buffers.getBuffer(darkType);
		Quads.wallZ(dark, pose, -HALF_W, -DEPTH - 0.2f, HALF_W, 0f, LANDING, false, 0, 0, 1, 1, tint(0.08f, a * 0.95f), light);
		buffers.endBatch(darkType);
		RenderType floorType = RenderType.entityTranslucent(FLOOR);
		VertexConsumer floor = buffers.getBuffer(floorType);
		Quads.flatY(floor, pose, -HALF_W, DEPTH, HALF_W, LANDING, -DEPTH, true, 0, 0, 3, 1.6f, tint(0.35f, a * 0.85f), light);
		buffers.endBatch(floorType);
		RenderType stepsType = RenderType.entityTranslucent(STEPS);
		VertexConsumer steps = buffers.getBuffer(stepsType);
		for (int i = STEP_COUNT - 1; i >= 0; i--) {
			float z0 = i * STEP, z1 = (i + 1) * STEP;
			float top = -(i + 1) * STEP;
			float shade = 1.0f - 0.08f * i;
			Quads.flatY(steps, pose, -HALF_W, z0, HALF_W, z1, top, true, 0, 0, 3, 0.5f, tint(shade, a * 0.9f), light);
			Quads.wallZ(steps, pose, -HALF_W, top, HALF_W, top + STEP, z0, false, 0, 0, 3, 0.5f, tint(shade * 0.7f, a * 0.9f), light);
		}
		buffers.endBatch(stepsType);

		// ---- above ground (depth tested): the railings and lamp posts
		VertexConsumer iron = buffers.getBuffer(JourneyRenderTypes.ghost(IRON));
		int green = argb(a * 0.85f, 0.32f, 0.62f, 0.42f);
		float rx = HALF_W + 0.1f;
		for (int side = -1; side <= 1; side += 2) {
			float x = side * rx;
			for (float z = 0.6f; z <= LANDING + 0.01f; z += 1.0f) {
				Quads.box(iron, pose, x - 0.05f, 0f, z - 0.05f, x + 0.05f, 1.05f, z + 0.05f, 0, 0, 0.1f, 1, green, light);
			}
			Quads.box(iron, pose, x - 0.06f, 1.0f, 0.5f, x + 0.06f, 1.1f, LANDING + 0.1f, 0, 0, 1, 0.1f, green, light);
			Quads.box(iron, pose, x - 0.04f, 0.5f, 0.5f, x + 0.04f, 0.56f, LANDING + 0.1f, 0, 0, 1, 0.1f, green, light);
			// the lamp post at the entrance and its globe
			Quads.box(iron, pose, x - 0.07f, 0f, -0.07f, x + 0.07f, 2.3f, 0.07f, 0, 0, 0.14f, 2.3f, green, light);
		}
		Quads.box(iron, pose, -rx, 1.0f, LANDING + 0.05f, rx, 1.1f, LANDING + 0.15f, 0, 0, 3, 0.1f, green, light);
		for (float x = -rx + 0.75f; x < rx; x += 0.75f) {
			Quads.box(iron, pose, x - 0.05f, 0f, LANDING + 0.05f, x + 0.05f, 1.05f, LANDING + 0.15f, 0, 0, 0.1f, 1, green, light);
		}
		buffers.endBatch(JourneyRenderTypes.ghost(IRON));

		// ---- the globes, the shimmering arch, the sign
		VertexConsumer glow = buffers.getBuffer(JourneyRenderTypes.ghost(GLOW));
		float pulse = 0.8f + 0.2f * Mth.sin(t * 0.21f);
		for (int side = -1; side <= 1; side += 2) {
			float x = side * rx;
			float s = 0.17f;
			Quads.box(glow, pose, x - s, 2.3f, -s, x + s, 2.3f + 2 * s, s, 0, 0, 1, 1, argb(a * 0.9f * pulse, 0.85f, 1.0f, 0.8f), light);
		}
		int segs = 18;
		float archR = 1.85f, archH = 3.5f;
		for (int i = 0; i < segs; i++) {
			double a0 = Math.PI * i / segs, a1 = Math.PI * (i + 1) / segs;
			float x0 = (float) (-Math.cos(a0) * archR), y0 = (float) (Math.sin(a0) * (archH - 1.2f)) + 1.2f;
			float x1 = (float) (-Math.cos(a1) * archR), y1 = (float) (Math.sin(a1) * (archH - 1.2f)) + 1.2f;
			float wob = 0.03f * Mth.sin(t * 0.5f + i);
			float w = 0.11f;
			float sh = 0.75f + 0.25f * Mth.sin(t * 0.3f - i * 0.7f); // a shimmer running round the arch
			int c = argb(a * 0.75f * sh, 1.0f, 0.88f, 0.45f);
			Quads.quad(glow, pose, x0 - w, y0, -0.15f + wob, x1 - w, y1, -0.15f + wob, x1 + w, y1 + w, -0.15f + wob, x0 + w, y0 + w, -0.15f + wob, 0, 0, 1, 1, c,
					light, 0, 0, -1);
		}
		// the uprights under the arch
		for (int side = -1; side <= 1; side += 2) {
			float x = side * archR;
			Quads.box(glow, pose, x - 0.08f, 0f, -0.23f, x + 0.08f, 1.25f, -0.07f, 0, 0, 1, 1, argb(a * 0.6f * pulse, 1.0f, 0.88f, 0.45f), light);
		}
		buffers.endBatch(JourneyRenderTypes.ghost(GLOW));
		VertexConsumer plate = buffers.getBuffer(JourneyRenderTypes.ghost(PLATE));
		Quads.box(plate, pose, -1.3f, 2.55f, -0.24f, 1.3f, 3.2f, -0.1f, 0, 0, 2.6f, 0.65f, argb(a * 0.9f, 0.35f, 0.55f, 0.4f), light);
		buffers.endBatch(JourneyRenderTypes.ghost(PLATE));

		// the sign's words, facing whoever approaches
		Font font = Minecraft.getInstance().font;
		ps.pushPose();
		ps.translate(0, 2.88, -0.26);
		ps.mulPose(Axis.YP.rotationDegrees(180f));
		float scale = 0.022f;
		ps.scale(scale, -scale, scale);
		int textAlpha = Mth.clamp((int) (a * 255), 8, 255);
		Component title = Component.translatable("sign.projecthero.journey.illusion.title").withStyle(ChatFormatting.BOLD);
		font.drawInBatch(title, -font.width(title) / 2f, -9, (textAlpha << 24) | 0xF4F0DC, false, ps.last().pose(), buffers, Font.DisplayMode.NORMAL, 0,
				light);
		ps.scale(0.6f, 0.6f, 0.6f);
		Component sub = Component.translatable("sign.projecthero.journey.illusion.line");
		font.drawInBatch(sub, -font.width(sub) / 2f, 4, (textAlpha << 24) | 0xE8C870, false, ps.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
		ps.popPose();
		buffers.endBatch();
		ps.popPose();
	}

	/** A pale, cold, ghostly tint at brightness {@code b} and alpha {@code a}. */
	private static int tint(float b, float a) {
		return argb(a, 0.82f * b + 0.1f, 0.92f * b + 0.08f, b);
	}

	private static int argb(float a, float r, float g, float b) {
		return (Mth.clamp((int) (a * 255), 0, 255) << 24) | (Mth.clamp((int) (r * 255), 0, 255) << 16) | (Mth.clamp((int) (g * 255), 0, 255) << 8)
				| Mth.clamp((int) (b * 255), 0, 255);
	}
}
