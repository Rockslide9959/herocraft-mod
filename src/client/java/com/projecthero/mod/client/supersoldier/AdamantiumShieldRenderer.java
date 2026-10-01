package com.projecthero.mod.client.supersoldier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.14.9: draws the Adamantium Shield -- a round disc (front: red / white / red rings, blue centre, white star; back:
 * brushed steel) with a red rim and a leather grip -- wherever the item is drawn: in either hand, raised to block, in the
 * GUI, on the ground, in a frame and as the thrown shield. Its item model is {@code builtin/entity} with the vanilla
 * shield's own display transforms (plus the {@code blocking} override, see {@link SuperSoldierClient}), and this draws in
 * the same space the vanilla shield model does (flipped by {@code scale(1, -1, -1)}, plate 1 px thick at z -2..-1, grip
 * behind it), so it is held and raised exactly like a vanilla shield. Front and back are square quads cut round by the
 * texture's alpha; the rim is a 32-sided band.
 */
public final class AdamantiumShieldRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/adamantium_shield.png");
	/** Disc radius in model pixels (the vanilla plate is 12 x 22). */
	private static final float RADIUS = 9.0f;
	private static final float FRONT_Z = -2.0f;
	private static final float BACK_Z = -1.0f;
	private static final int SIDES = 32;
	private static final float TEX = 64.0f;

	@Override
	public void render(ItemStack stack, ItemDisplayContext mode, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		pose.pushPose();
		pose.scale(1.0f, -1.0f, -1.0f);
		VertexConsumer vc = ItemRenderer.getFoilBufferDirect(buffers, RenderType.entityCutoutNoCull(TEXTURE), true, stack.hasFoil());
		PoseStack.Pose p = pose.last();
		float r = RADIUS / 16.0f;
		float zf = FRONT_Z / 16.0f;
		float zb = BACK_Z / 16.0f;
		// front face (away from the hand) and back face, each the 32x32 disc art
		quad(vc, p, -r, -r, zf, r, -r, zf, r, r, zf, -r, r, zf, 0, 0, 32, 32, 0, 0, -1, light, overlay);
		quad(vc, p, r, -r, zb, -r, -r, zb, -r, r, zb, r, r, zb, 32, 0, 64, 32, 0, 0, 1, light, overlay);
		// rim
		for (int i = 0; i < SIDES; i++) {
			double a0 = i * Math.PI * 2.0 / SIDES;
			double a1 = (i + 1) * Math.PI * 2.0 / SIDES;
			float x0 = (float) Math.cos(a0) * r;
			float y0 = (float) Math.sin(a0) * r;
			float x1 = (float) Math.cos(a1) * r;
			float y1 = (float) Math.sin(a1) * r;
			float nx = (float) Math.cos((a0 + a1) * 0.5);
			float ny = (float) Math.sin((a0 + a1) * 0.5);
			float u0 = i * 32.0f / SIDES;
			float u1 = (i + 1) * 32.0f / SIDES;
			quad(vc, p, x0, y0, zf, x1, y1, zf, x1, y1, zb, x0, y0, zb, u0, 32, u1, 36, nx, ny, 0, light, overlay);
		}
		// the grip: the vanilla shield's handle box (-1, -3, -1) .. (1, 3, 5)
		box(vc, p, -1, -3, -1, 1, 3, 5, light, overlay);
		pose.popPose();
	}

	private static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int light,
			int overlay) {
		float a = x0 / 16f, b = y0 / 16f, c = z0 / 16f, d = x1 / 16f, e = y1 / 16f, f = z1 / 16f;
		float u0 = 32, v0 = 32, u1 = 40, v1 = 40;
		quad(vc, p, a, b, c, d, b, c, d, e, c, a, e, c, u0, v0, u1, v1, 0, 0, -1, light, overlay);
		quad(vc, p, a, b, f, d, b, f, d, e, f, a, e, f, u0, v0, u1, v1, 0, 0, 1, light, overlay);
		quad(vc, p, a, b, c, a, e, c, a, e, f, a, b, f, u0, v0, u1, v1, -1, 0, 0, light, overlay);
		quad(vc, p, d, b, c, d, e, c, d, e, f, d, b, f, u0, v0, u1, v1, 1, 0, 0, light, overlay);
		quad(vc, p, a, b, c, d, b, c, d, b, f, a, b, f, u0, v0, u1, v1, 0, -1, 0, light, overlay);
		quad(vc, p, a, e, c, d, e, c, d, e, f, a, e, f, u0, v0, u1, v1, 0, 1, 0, light, overlay);
	}

	/** One quad: corners 1-4 get UVs (u0,v0) (u1,v0) (u1,v1) (u0,v1) in texture pixels. */
	private static void quad(VertexConsumer vc, PoseStack.Pose p, float x1, float y1, float z1, float x2, float y2, float z2, float x3,
			float y3, float z3, float x4, float y4, float z4, float u0, float v0, float u1, float v1, float nx, float ny, float nz,
			int light, int overlay) {
		vertex(vc, p, x1, y1, z1, u0 / TEX, v0 / TEX, nx, ny, nz, light, overlay);
		vertex(vc, p, x2, y2, z2, u1 / TEX, v0 / TEX, nx, ny, nz, light, overlay);
		vertex(vc, p, x3, y3, z3, u1 / TEX, v1 / TEX, nx, ny, nz, light, overlay);
		vertex(vc, p, x4, y4, z4, u0 / TEX, v1 / TEX, nx, ny, nz, light, overlay);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, float nx, float ny,
			float nz, int light, int overlay) {
		vc.addVertex(p, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
	}
}
