package com.projecthero.mod.client.spider;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.spider.SpiderWrap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the web cocoon round anything Spider-Man's Web Shot has wrapped ({@link SpiderWrap}): a textured shell of
 * webbing (the web-wall texture) a little bigger than the body, bound by three thicker bands of strand -- real geometry
 * in the world buffer, not particles. Drawn for every cocooned living entity within 96 blocks; nothing is created or
 * kept, so it simply stops being drawn when the cocoon is cut.
 */
public final class SpiderWrapRenderer {
	private static final ResourceLocation SHELL = ProjectHeroMod.id("textures/entity/spider_web_wall.png");
	private static final ResourceLocation BAND = ProjectHeroMod.id("textures/entity/spider_web_strand.png");

	private SpiderWrapRenderer() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(SpiderWrapRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		MultiBufferSource buffers = context.consumers();
		PoseStack pose = context.matrixStack();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity) || !SpiderWrap.isWrapped(e) || e.distanceToSqr(cam) > 96.0 * 96.0) {
				continue;
			}
			if (e == mc.player && mc.options.getCameraType().isFirstPerson()) {
				continue; // never wrap the camera itself
			}
			Vec3 p = new Vec3(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()),
					Mth.lerp(partial, e.zo, e.getZ())).subtract(cam);
			int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ()));
			double hw = e.getBbWidth() * 0.5 + 0.12;
			double h = e.getBbHeight() + 0.12;
			pose.pushPose();
			PoseStack.Pose last = pose.last();
			// the shell
			box(last, buffers.getBuffer(RenderType.entityTranslucent(SHELL)), p.x - hw, p.y - 0.04, p.z - hw,
					p.x + hw, p.y + h, p.z + hw, light, 0.92f);
			// three bands binding it, a touch proud of the shell
			VertexConsumer band = buffers.getBuffer(RenderType.entityTranslucent(BAND));
			double bw = hw + 0.03;
			for (double f : new double[] {0.22, 0.52, 0.8}) {
				double y = p.y + h * f;
				box(last, band, p.x - bw, y - 0.07, p.z - bw, p.x + bw, y + 0.07, p.z + bw, light, 1.0f);
			}
			pose.popPose();
		}
	}

	/** An axis-aligned box (camera-relative), each face one tile of the texture, both sides visible (no-cull type). */
	private static void box(PoseStack.Pose pose, VertexConsumer vc, double x0, double y0, double z0, double x1, double y1,
			double z1, int light, float alpha) {
		// -Z / +Z
		quad(pose, vc, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, light, alpha);
		quad(pose, vc, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1, light, alpha);
		// -X / +X
		quad(pose, vc, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, light, alpha);
		quad(pose, vc, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, light, alpha);
		// top / bottom
		quad(pose, vc, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, light, alpha);
		quad(pose, vc, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, light, alpha);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer vc, double ax, double ay, double az, double bx, double by,
			double bz, double cx, double cy, double cz, double dx, double dy, double dz, float nx, float ny, float nz,
			int light, float alpha) {
		vertex(pose, vc, ax, ay, az, 0f, 1f, nx, ny, nz, light, alpha);
		vertex(pose, vc, bx, by, bz, 1f, 1f, nx, ny, nz, light, alpha);
		vertex(pose, vc, cx, cy, cz, 1f, 0f, nx, ny, nz, light, alpha);
		vertex(pose, vc, dx, dy, dz, 0f, 0f, nx, ny, nz, light, alpha);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer vc, double x, double y, double z, float u, float v,
			float nx, float ny, float nz, int light, float alpha) {
		vc.addVertex(pose.pose(), (float) x, (float) y, (float) z)
				.setColor(0.95f, 0.96f, 1.0f, alpha)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}
}
