package com.projecthero.mod.client.firearm;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import com.projecthero.mod.ProjectHeroMod;

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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only "bullet hole" decals. The server sends a {@link com.projecthero.mod.network.BulletHolePayload}
 * whenever a firearm shot strikes a solid block; this keeps a small bounded list of those points and
 * draws a fading textured quad on the hit face until it expires.
 *
 * <p>No entity is ever created (nothing to sync or leak): a hole is four vertices straight into the
 * world-render buffer. The list is capped at {@link #MAX} (oldest dropped first) and every hole
 * disappears after {@link #LIFETIME_MS}, fading out over its last {@link #FADE_MS}.
 */
public final class BulletHoleRenderer {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/misc/bullet_hole.png");
	private static final int MAX = 96;
	private static final long LIFETIME_MS = 60_000L;
	private static final long FADE_MS = 6_000L;
	private static final float HALF_SIZE = 0.16f;
	/** How far off the surface the decal floats, to beat z-fighting with the block face. */
	private static final double SURFACE_OFFSET = 0.015;

	private record Hole(double x, double y, double z, Direction face, long bornMs) {
	}

	private static final Deque<Hole> HOLES = new ArrayDeque<>();

	private BulletHoleRenderer() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(BulletHoleRenderer::render);
	}

	/** Called from the client packet receiver. */
	public static void add(double x, double y, double z, int face3d) {
		Direction dir = Direction.from3DDataValue(face3d);
		synchronized (HOLES) {
			HOLES.addLast(new Hole(x, y, z, dir, System.currentTimeMillis()));
			while (HOLES.size() > MAX) {
				HOLES.removeFirst();
			}
		}
	}

	public static void clear() {
		synchronized (HOLES) {
			HOLES.clear();
		}
	}

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		long now = System.currentTimeMillis();
		Vec3 cam = context.camera().getPosition();
		PoseStack poseStack = context.matrixStack();
		VertexConsumer buffer = context.consumers().getBuffer(RenderType.entityTranslucent(TEXTURE));

		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		var pose = poseStack.last();

		synchronized (HOLES) {
			Iterator<Hole> it = HOLES.iterator();
			while (it.hasNext()) {
				Hole h = it.next();
				long age = now - h.bornMs;
				if (age >= LIFETIME_MS) {
					it.remove();
					continue;
				}
				float alpha = age > LIFETIME_MS - FADE_MS
						? 1.0f - (age - (LIFETIME_MS - FADE_MS)) / (float) FADE_MS
						: 1.0f;
				drawHole(pose, buffer, mc, h, alpha);
			}
		}
		poseStack.popPose();
	}

	private static void drawHole(PoseStack.Pose pose, VertexConsumer buffer, Minecraft mc, Hole h, float alpha) {
		Vec3 n = Vec3.atLowerCornerOf(h.face.getNormal());
		Vec3 up = (h.face == Direction.UP || h.face == Direction.DOWN) ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
		Vec3 right = n.cross(up).normalize();
		up = right.cross(n).normalize();

		Vec3 c = new Vec3(h.x, h.y, h.z).add(n.scale(SURFACE_OFFSET));
		Vec3 r = right.scale(HALF_SIZE);
		Vec3 u = up.scale(HALF_SIZE);

		BlockPos lightPos = BlockPos.containing(c.add(n.scale(0.05)));
		int light = LevelRenderer.getLightColor(mc.level, lightPos);
		float nx = (float) n.x;
		float ny = (float) n.y;
		float nz = (float) n.z;

		vertex(pose, buffer, c.subtract(r).subtract(u), 0f, 1f, alpha, light, nx, ny, nz);
		vertex(pose, buffer, c.add(r).subtract(u), 1f, 1f, alpha, light, nx, ny, nz);
		vertex(pose, buffer, c.add(r).add(u), 1f, 0f, alpha, light, nx, ny, nz);
		vertex(pose, buffer, c.subtract(r).add(u), 0f, 0f, alpha, light, nx, ny, nz);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 p, float texU, float texV,
			float alpha, int light, float nx, float ny, float nz) {
		buffer.addVertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
				.setColor(1.0f, 1.0f, 1.0f, alpha)
				.setUv(texU, texV)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}
}
