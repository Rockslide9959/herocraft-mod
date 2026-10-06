package com.projecthero.mod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.item.IronManArmorItem;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * The Arc Reactor in the chest of any player with the Tony Stark power who is <b>not</b> wearing an Iron Man chestplate
 * (the suit has its own). Purely cosmetic; the power is the synced {@link TonyStark} attachment, so other players see it.
 *
 * <p>v0.14.26: a real 3D model instead of the flat item icon, after the comics' chest RT -- a round unit set into the
 * sternum: a bevelled steel housing ring standing a little proud of the chest, a dark recessed bowl, ten copper coil
 * segments round it with cyan light leaking between them, a bright layered core (white-hot centre, cyan ring), and a soft
 * glow that pulses slowly. The glowing parts are full-bright; the metal takes the world's light.
 */
public class ArcReactorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/mutation/white.png");
	private static final float PX = 1f / 16f;
	private static final int STEEL = 0x8E959E, STEEL_DARK = 0x4A4F57, STEEL_LIGHT = 0xC8CED6, BOWL = 0x1A1F26;
	private static final int COPPER = 0xB8692E, COPPER_DARK = 0x7A4219;
	private static final int CYAN = 0x6FE6FF, CORE = 0xE8FFFF;

	public ArcReactorLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ItemRenderer itemRenderer) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (!TonyStark.hasPower(player) || player.isInvisible()) {
			return;
		}
		if (player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof IronManArmorItem) {
			return;
		}
		pose.pushPose();
		getParentModel().body.translateAndRotate(pose);
		// body pivot is at the neck (model y points down); the sternum, on the chest's front face (z = -2 px), and the
		// shirt's outer layer sits a quarter-pixel proud of that
		// v0.15.1, explicit user request: higher up -- the housing's top edge sits just under the chest's 2nd pixel row
		// (centre 3.25 px below the neck, outer radius ~1.25 px after the scale below)
		pose.translate(0.0, 3.25 * PX, -2.25 * PX);
		// v0.14.28, explicit user request: about 2.5 px across on the chest (the housing is 5.3 px wide unscaled)
		float s = 2.5f / 5.3f;
		pose.scale(s, s, s);
		float pulse = 0.82f + 0.18f * Mth.sin(ageInTicks * 0.12f);
		drawReactor(pose, buffers, packedLight, ageInTicks, pulse);
		pose.popPose();
	}

	/** The reactor, centred on the origin, facing -Z (out of the chest). Shared so other renderers can reuse it. */
	public static void drawReactor(PoseStack pose, MultiBufferSource buffers, int light, float age, float pulse) {
		VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
		// ---- the housing: a ring of 20 steel blocks, bevelled (a lighter inner lip, a darker outer edge)
		ring(pose, solid, 20, 1.75f, 2.55f, 0f, -0.7f, STEEL, light);
		ring(pose, solid, 20, 1.75f, 2.0f, -0.7f, -0.85f, STEEL_LIGHT, light);
		ring(pose, solid, 20, 2.35f, 2.65f, 0.05f, -0.45f, STEEL_DARK, light);
		// four bolts on the rim
		for (int i = 0; i < 4; i++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(45f + i * 90f));
			box(pose, solid, -0.22f, 2.05f, -0.95f, 0.22f, 2.45f, -0.7f, STEEL_LIGHT, light);
			pose.popPose();
		}
		// ---- the bowl behind everything
		disc(pose, solid, 1.8f, -0.05f, 0.05f, BOWL, light);
		// ---- ten copper coil segments round the core, each wrapped (two tones)
		for (int i = 0; i < 10; i++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(i * 36f));
			box(pose, solid, -0.32f, 0.95f, -0.55f, 0.32f, 1.7f, -0.1f, COPPER, light);
			box(pose, solid, -0.34f, 1.15f, -0.6f, 0.34f, 1.3f, -0.1f, COPPER_DARK, light);
			box(pose, solid, -0.34f, 1.45f, -0.6f, 0.34f, 1.6f, -0.1f, COPPER_DARK, light);
			pose.popPose();
		}

		int full = 0xF000F0;
		// ---- light leaking between the coils (full-bright, behind the copper)
		for (int i = 0; i < 10; i++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(18f + i * 36f));
			box(pose, solid, -0.08f, 0.95f, -0.35f, 0.08f, 1.65f, -0.05f, lerp(CYAN, CORE, 0.2f * pulse), full);
			pose.popPose();
		}
		// ---- the core: a cyan ring round a white-hot centre, stepped forward
		ring(pose, solid, 16, 0.55f, 0.95f, -0.2f, -0.6f, lerp(0x2FB8E0, CYAN, pulse), full);
		disc(pose, solid, 0.58f, -0.25f, -0.75f, lerp(CYAN, CORE, pulse), full);
		disc(pose, solid, 0.3f, -0.7f, -0.82f, 0xFFFFFF, full);

		// ---- the glow: additive soft discs in front, breathing with the pulse
		VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
		float a = 0.22f * pulse;
		glowDisc(pose, glow, 1.4f, -0.9f, CYAN, a);
		glowDisc(pose, glow, 2.4f, -0.88f, CYAN, a * 0.45f);
		glowDisc(pose, glow, 0.8f, -0.92f, CORE, a * 1.6f);
	}

	// ---------------------------------------------------------------- geometry (all in pixels)

	/** A ring of {@code n} boxes between radii {@code r0..r1}, from depth {@code z0} to {@code z1}. */
	private static void ring(PoseStack pose, VertexConsumer vc, int n, float r0, float r1, float z0, float z1, int rgb, int light) {
		float half = (float) (Math.PI * r1 / n) * 1.08f;
		for (int i = 0; i < n; i++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(i * 360f / n));
			box(pose, vc, -half, r0, Math.min(z0, z1), half, r1, Math.max(z0, z1), rgb, light);
			pose.popPose();
		}
	}

	/** A round disc of radius {@code r}: three squares turned 0 / 30 / 60 degrees. */
	private static void disc(PoseStack pose, VertexConsumer vc, float r, float z0, float z1, int rgb, int light) {
		float s = r * 0.93f;
		for (int k = 0; k < 3; k++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(k * 30f));
			box(pose, vc, -s, -s, Math.min(z0, z1), s, s, Math.max(z0, z1), rgb, light);
			pose.popPose();
		}
	}

	/** A flat translucent glow disc at depth {@code z}. */
	private static void glowDisc(PoseStack pose, VertexConsumer vc, float r, float z, int rgb, float alpha) {
		for (int k = 0; k < 3; k++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(k * 30f));
			float s = r * PX;
			PoseStack.Pose p = pose.last();
			int cr = (rgb >> 16) & 0xFF, cg = (rgb >> 8) & 0xFF, cb = rgb & 0xFF, ca = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255);
			float zz = z * PX;
			v(vc, p, -s, -s, zz, cr, cg, cb, ca, 0f, 0f, 0xF000F0);
			v(vc, p, s, -s, zz, cr, cg, cb, ca, 1f, 0f, 0xF000F0);
			v(vc, p, s, s, zz, cr, cg, cb, ca, 1f, 1f, 0xF000F0);
			v(vc, p, -s, s, zz, cr, cg, cb, ca, 0f, 1f, 0xF000F0);
			pose.popPose();
		}
	}

	/** An axis-aligned box from (x0,y0,z0) to (x1,y1,z1) in pixels, flat colour. */
	private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int light) {
		PoseStack.Pose p = pose.last();
		float ax = x0 * PX, ay = y0 * PX, az = z0 * PX, bx = x1 * PX, by = y1 * PX, bz = z1 * PX;
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		// front (-z) is brightest, the sides a touch darker for shape
		quad(vc, p, ax, ay, az, bx, ay, az, bx, by, az, ax, by, az, r, g, b, light, 0, 0, -1, 1.0f);
		quad(vc, p, ax, ay, bz, ax, by, bz, bx, by, bz, bx, ay, bz, r, g, b, light, 0, 0, 1, 0.6f);
		quad(vc, p, ax, ay, az, ax, by, az, ax, by, bz, ax, ay, bz, r, g, b, light, -1, 0, 0, 0.8f);
		quad(vc, p, bx, ay, az, bx, ay, bz, bx, by, bz, bx, by, az, r, g, b, light, 1, 0, 0, 0.8f);
		quad(vc, p, ax, ay, az, ax, ay, bz, bx, ay, bz, bx, ay, az, r, g, b, light, 0, -1, 0, 0.9f);
		quad(vc, p, ax, by, az, bx, by, az, bx, by, bz, ax, by, bz, r, g, b, light, 0, 1, 0, 0.75f);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose p, float x1, float y1, float z1, float x2, float y2, float z2, float x3,
			float y3, float z3, float x4, float y4, float z4, int r, int g, int b, int light, float nx, float ny, float nz, float shade) {
		int sr = Math.round(r * shade), sg = Math.round(g * shade), sb = Math.round(b * shade);
		vc.addVertex(p, x1, y1, z1).setColor(sr, sg, sb, 255).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, x2, y2, z2).setColor(sr, sg, sb, 255).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, x3, y3, z3).setColor(sr, sg, sb, 255).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, x4, y4, z4).setColor(sr, sg, sb, 255).setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}

	private static void v(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, int r, int g, int b, int a, float u, float vv, int light) {
		vc.addVertex(p, x, y, z).setColor(r, g, b, a).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0f, 0f, -1f);
	}

	private static int lerp(int c0, int c1, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = Math.round(((c0 >> 16) & 0xFF) * (1 - t) + ((c1 >> 16) & 0xFF) * t);
		int g = Math.round(((c0 >> 8) & 0xFF) * (1 - t) + ((c1 >> 8) & 0xFF) * t);
		int b = Math.round((c0 & 0xFF) * (1 - t) + (c1 & 0xFF) * t);
		return (r << 16) | (g << 8) | b;
	}
}
