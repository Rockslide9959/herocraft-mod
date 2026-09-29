package com.projecthero.mod.client.moonknight;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.projecthero.mod.network.MoonKnightScholarSightPayload;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Moon Knight Phase 5, Steven Grant's "Scholar's Sight": outlines the chests, barrels, ores and spawners the server
 * found ({@link MoonKnightScholarSightPayload}) through walls, for this player only, gently pulsing and fading out at
 * the end. The outlines use their own line render type with the depth test switched off, drawn in
 * {@code AFTER_ENTITIES} through the frame's buffer source (so the camera transform is the one every other world line
 * uses).
 */
public final class MoonKnightScholarSightRenderer {
	/** Lines, drawn on top of everything. */
	private static final RenderType XRAY_LINES = new RenderType("projecthero_moon_knight_xray_lines",
			DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 4096, false, false,
			() -> {
				RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
				RenderSystem.lineWidth(2.5f);
				RenderSystem.enableBlend();
				RenderSystem.defaultBlendFunc();
				RenderSystem.disableDepthTest();
				RenderSystem.depthMask(false);
				RenderSystem.disableCull();
			},
			() -> {
				RenderSystem.enableCull();
				RenderSystem.depthMask(true);
				RenderSystem.enableDepthTest();
				RenderSystem.disableBlend();
				RenderSystem.lineWidth(1.0f);
			}) {
	};

	private static long[] positions = new long[0];
	private static int[] colours = new int[0];
	private static long startTick;
	private static long endTick;
	private static ClientLevel level;

	private MoonKnightScholarSightRenderer() {
	}

	public static void receive(MoonKnightScholarSightPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		positions = payload.positions();
		colours = payload.colours();
		level = mc.level;
		startTick = mc.level.getGameTime();
		endTick = startTick + Math.max(1, payload.ticks());
	}

	public static void clear() {
		positions = new long[0];
		colours = new int[0];
		level = null;
	}

	public static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (positions.length == 0 || mc.level == null) {
			return;
		}
		if (mc.level != level || mc.level.getGameTime() >= endTick) {
			clear();
			return;
		}
		MultiBufferSource consumers = context.consumers();
		PoseStack pose = context.matrixStack();
		if (consumers == null || pose == null) {
			return;
		}
		Camera camera = context.camera();
		Vec3 cam = camera.getPosition();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		double now = mc.level.getGameTime() + partial;
		// fade in over 0.25 s, pulse, fade out over the last second
		float in = (float) Math.min(1.0, (now - startTick) / 5.0);
		float out = (float) Math.min(1.0, (endTick - now) / 20.0);
		float pulse = 0.75f + 0.25f * (float) Math.sin(now * 0.25);
		float alpha = Math.max(0.0f, Math.min(in, out)) * pulse;
		if (alpha <= 0.01f) {
			return;
		}
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		VertexConsumer lines = consumers.getBuffer(XRAY_LINES);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int i = 0; i < positions.length; i++) {
			m.set(positions[i]);
			int rgb = i < colours.length ? colours[i] : 0xFFFFFF;
			float r = ((rgb >> 16) & 0xFF) / 255.0f;
			float g = ((rgb >> 8) & 0xFF) / 255.0f;
			float b = (rgb & 0xFF) / 255.0f;
			LevelRenderer.renderLineBox(pose, lines, m.getX() + 0.02, m.getY() + 0.02, m.getZ() + 0.02,
					m.getX() + 0.98, m.getY() + 0.98, m.getZ() + 0.98, r, g, b, alpha);
		}
		pose.popPose();
		if (consumers instanceof MultiBufferSource.BufferSource source) {
			source.endBatch(XRAY_LINES);
		}
	}
}
