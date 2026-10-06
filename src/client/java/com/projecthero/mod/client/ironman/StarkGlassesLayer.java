package com.projecthero.mod.client.ironman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.gear.StarkGear;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.15.1: the Stark Glasses on the wearer's face -- a thin gold aviator frame (top bar, bridge, rims, temple arms running
 * back over the ears) with red-tinted, see-through lenses and a small glint. Drawn on the head so it follows every look
 * and pose; hidden while an Iron Man helmet is on (the suit's faceplate covers the face) or the player is invisible.
 * Everyone sees it: the Stark Gear slot is synced to all clients.
 */
public class StarkGlassesLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/mutation/white.png");
	private static final float PX = 1f / 16f;
	private static final int GOLD = 0xD8A23A, GOLD_LIGHT = 0xF6D27A, GOLD_DARK = 0x8A6020;
	private static final int TINT = 0x9A1426, TINT_TOP = 0xD0404A, GLINT = 0xFFE6DC;
	/** Front of the frame / lenses, in pixels from the head pivot (the hat layer sits at -4.5). */
	private static final float FRONT = -4.78f;

	public StarkGlassesLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !StarkGear.hasGlasses(player) || StarkGear.ironManHelmetOn(player)
				|| !getParentModel().head.visible) {
			return;
		}
		pose.pushPose();
		getParentModel().head.translateAndRotate(pose);
		drawGlasses(pose, buffers, light);
		pose.popPose();
	}

	/** The glasses in head space (pixels; y down, -z out of the face). Shared so the item preview could reuse it. */
	public static void drawGlasses(PoseStack pose, MultiBufferSource buffers, int light) {
		VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
		float f0 = FRONT;
		float f1 = FRONT + 0.3f;
		// top bar across the brow, with a bright upper edge
		box(pose, solid, -4.75f, -4.95f, f0, 4.75f, -4.55f, f1, GOLD, 255, light);
		box(pose, solid, -4.6f, -5.0f, f0 - 0.02f, 4.6f, -4.88f, f1, GOLD_LIGHT, 255, light);
		// rims: outer and inner sides of each lens, and the aviator bottoms (stepped in toward the nose)
		for (int side = -1; side <= 1; side += 2) {
			float outer = side * 3.95f;
			float inner = side * 0.65f;
			box(pose, solid, Math.min(outer, outer - side * 0.3f), -4.6f, f0, Math.max(outer, outer - side * 0.3f), -3.0f, f1, GOLD, 255, light);
			box(pose, solid, Math.min(inner, inner + side * 0.25f), -4.6f, f0, Math.max(inner, inner + side * 0.25f), -3.1f, f1, GOLD, 255, light);
			float a = side * 3.65f;
			float b = side * 0.9f;
			box(pose, solid, Math.min(a, b), -2.75f, f0, Math.max(a, b), -2.5f, f1, GOLD_DARK, 255, light);
			// corner pieces where the bottom rim turns up
			box(pose, solid, Math.min(a, a + side * 0.3f), -3.1f, f0, Math.max(a, a + side * 0.3f), -2.6f, f1, GOLD_DARK, 255, light);
			box(pose, solid, Math.min(b, b - side * 0.25f), -3.2f, f0, Math.max(b, b - side * 0.25f), -2.6f, f1, GOLD_DARK, 255, light);
			// temple arm, from the hinge back over the ear
			float t0 = side * 4.55f;
			float t1 = side * 4.8f;
			box(pose, solid, Math.min(t0, t1), -4.9f, f0, Math.max(t0, t1), -4.6f, 1.6f, GOLD_DARK, 255, light);
			box(pose, solid, Math.min(t0, t1), -4.6f, 1.0f, Math.max(t0, t1), -3.6f, 1.6f, GOLD_DARK, 255, light);
		}
		// nose pads
		box(pose, solid, -0.55f, -3.4f, f0 + 0.1f, -0.3f, -3.0f, f1 + 0.2f, GOLD_LIGHT, 255, light);
		box(pose, solid, 0.3f, -3.4f, f0 + 0.1f, 0.55f, -3.0f, f1 + 0.2f, GOLD_LIGHT, 255, light);

		// lenses: translucent red tint, lighter at the top, a glint on each
		VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
		float lz0 = FRONT + 0.08f;
		float lz1 = FRONT + 0.2f;
		for (int side = -1; side <= 1; side += 2) {
			float o = side * 3.65f;
			float i = side * 0.9f;
			box(pose, glass, Math.min(o, i), -4.55f, lz0, Math.max(o, i), -3.85f, lz1, TINT_TOP, 210, light);
			box(pose, glass, Math.min(o, i), -3.85f, lz0, Math.max(o, i), -2.75f, lz1, TINT, 225, light);
			float g0 = side * 2.9f;
			float g1 = side * 2.4f;
			box(pose, glass, Math.min(g0, g1), -4.35f, lz0 - 0.03f, Math.max(g0, g1), -3.95f, lz1, GLINT, 200, 0xF000F0);
		}
	}

	/** An axis-aligned box from (x0,y0,z0) to (x1,y1,z1) in pixels, flat colour with alpha. */
	private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
			int rgb, int alpha, int light) {
		PoseStack.Pose p = pose.last();
		float ax = x0 * PX, ay = y0 * PX, az = z0 * PX, bx = x1 * PX, by = y1 * PX, bz = z1 * PX;
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		quad(vc, p, ax, ay, az, bx, ay, az, bx, by, az, ax, by, az, r, g, b, alpha, light, 0, 0, -1, 1.0f);
		quad(vc, p, ax, ay, bz, ax, by, bz, bx, by, bz, bx, ay, bz, r, g, b, alpha, light, 0, 0, 1, 0.6f);
		quad(vc, p, ax, ay, az, ax, by, az, ax, by, bz, ax, ay, bz, r, g, b, alpha, light, -1, 0, 0, 0.8f);
		quad(vc, p, bx, ay, az, bx, ay, bz, bx, by, bz, bx, by, az, r, g, b, alpha, light, 1, 0, 0, 0.8f);
		quad(vc, p, ax, ay, az, ax, ay, bz, bx, ay, bz, bx, ay, az, r, g, b, alpha, light, 0, -1, 0, 0.95f);
		quad(vc, p, ax, by, az, bx, by, az, bx, by, bz, ax, by, bz, r, g, b, alpha, light, 0, 1, 0, 0.7f);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose p, float x1, float y1, float z1, float x2, float y2, float z2,
			float x3, float y3, float z3, float x4, float y4, float z4, int r, int g, int b, int a, int light, float nx, float ny,
			float nz, float shade) {
		int sr = Math.round(r * shade), sg = Math.round(g * shade), sb = Math.round(b * shade);
		vc.addVertex(p, x1, y1, z1).setColor(sr, sg, sb, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, x2, y2, z2).setColor(sr, sg, sb, a).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, x3, y3, z3).setColor(sr, sg, sb, a).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, x4, y4, z4).setColor(sr, sg, sb, a).setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}
}
