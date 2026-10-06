package com.projecthero.mod.client.ironman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.gear.StarkGear;
import com.projecthero.mod.ironman.item.IronManArmorItem;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * v0.15.4: the Colantotte Bracelets on the wearer's wrists -- a thin red band round each forearm just above the hand, with
 * darker rims, a light top edge and a small silver sensor stud on the outside. Drawn in each arm's own space so it follows
 * every swing and pose, for every viewer (the Stark Gear slot is synced to all); hidden under an Iron Man chestplate (the
 * gauntlet covers the wrist) or while invisible. {@link #renderFirstPerson} draws the same band on the first-person hand.
 */
public class ColantotteBraceletsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/mutation/white.png");
	private static final float PX = 1f / 16f;
	private static final int RED = 0xC41E2E, RED_DARK = 0x6E0C16, RED_LIGHT = 0xF0505C, STUD = 0xD6DAE2;
	/** The band, in arm pixels (y down from the shoulder pivot; the hand ends at y 10). */
	private static final float BAND_TOP = 6.7f, BAND_BOTTOM = 8.3f;
	/** How far the band stands proud of the arm (the sleeve layer sits at 0.25). */
	private static final float PROUD = 0.42f;

	public ColantotteBraceletsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	/** Are the bracelets drawn on this player right now? */
	public static boolean shows(Player player) {
		return !player.isInvisible() && StarkGear.hasBracelets(player)
				&& !(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof IronManArmorItem);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (!shows(player)) {
			return;
		}
		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		PlayerModel<AbstractClientPlayer> model = getParentModel();
		if (model.rightArm.visible) {
			drawOnArm(pose, buffers, light, model.rightArm, true, slim);
		}
		if (model.leftArm.visible) {
			drawOnArm(pose, buffers, light, model.leftArm, false, slim);
		}
	}

	/** First person: the band on the hand that was just drawn ({@code arm} still holds vanilla's animated pose). */
	public static void renderFirstPerson(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			ModelPart arm, boolean right) {
		if (!shows(player)) {
			return;
		}
		drawOnArm(pose, buffers, light, arm, right, player.getSkin().model() == PlayerSkin.Model.SLIM);
	}

	private static void drawOnArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right,
			boolean slim) {
		pose.pushPose();
		arm.translateAndRotate(pose);
		// the arm box: right arm x -3..1 (slim -2..1), left arm x -1..3 (slim -1..2); z -2..2
		float w = slim ? 3f : 4f;
		float x0 = right ? 1f - w : -1f;
		float x1 = x0 + w;
		drawBand(pose, buffers, light, x0, x1, right);
		pose.popPose();
	}

	/** The band round an arm box spanning {@code x0..x1}, z -2..2. */
	private static void drawBand(PoseStack pose, MultiBufferSource buffers, int light, float x0, float x1, boolean right) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
		float p = PROUD;
		// the band itself, then thin darker rims at its top and bottom edges and a light line along the top
		box(pose, vc, x0 - p, BAND_TOP, -2f - p, x1 + p, BAND_BOTTOM, 2f + p, RED, light);
		box(pose, vc, x0 - p - 0.05f, BAND_TOP - 0.05f, -2f - p - 0.05f, x1 + p + 0.05f, BAND_TOP + 0.28f, 2f + p + 0.05f, RED_DARK, light);
		box(pose, vc, x0 - p - 0.05f, BAND_BOTTOM - 0.28f, -2f - p - 0.05f, x1 + p + 0.05f, BAND_BOTTOM + 0.05f, 2f + p + 0.05f, RED_DARK, light);
		box(pose, vc, x0 - p - 0.02f, BAND_TOP + 0.3f, -2f - p - 0.02f, x1 + p + 0.02f, BAND_TOP + 0.48f, 2f + p + 0.02f, RED_LIGHT, light);
		// the sensor stud on the outside of the wrist
		float out = right ? x0 - p : x1 + p;
		float s0 = right ? out - 0.25f : out;
		box(pose, vc, s0, BAND_TOP + 0.45f, -0.55f, s0 + 0.25f, BAND_BOTTOM - 0.45f, 0.55f, STUD, light);
	}

	/** An axis-aligned box from (x0,y0,z0) to (x1,y1,z1) in arm pixels, flat colour, shaded per face. */
	private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
			int rgb, int light) {
		PoseStack.Pose p = pose.last();
		float ax = x0 * PX, ay = y0 * PX, az = z0 * PX, bx = x1 * PX, by = y1 * PX, bz = z1 * PX;
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		quad(vc, p, ax, ay, az, bx, ay, az, bx, by, az, ax, by, az, r, g, b, light, 0, 0, -1, 0.9f);
		quad(vc, p, ax, ay, bz, ax, by, bz, bx, by, bz, bx, ay, bz, r, g, b, light, 0, 0, 1, 0.75f);
		quad(vc, p, ax, ay, az, ax, by, az, ax, by, bz, ax, ay, bz, r, g, b, light, -1, 0, 0, 0.8f);
		quad(vc, p, bx, ay, az, bx, ay, bz, bx, by, bz, bx, by, az, r, g, b, light, 1, 0, 0, 0.8f);
		quad(vc, p, ax, ay, az, ax, ay, bz, bx, ay, bz, bx, ay, az, r, g, b, light, 0, -1, 0, 1.0f);
		quad(vc, p, ax, by, az, bx, by, az, bx, by, bz, ax, by, bz, r, g, b, light, 0, 1, 0, 0.65f);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose p, float x1, float y1, float z1, float x2, float y2, float z2,
			float x3, float y3, float z3, float x4, float y4, float z4, int r, int g, int b, int light, float nx, float ny,
			float nz, float shade) {
		int sr = Math.round(r * shade), sg = Math.round(g * shade), sb = Math.round(b * shade);
		vc.addVertex(p, x1, y1, z1).setColor(sr, sg, sb, 255).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, x2, y2, z2).setColor(sr, sg, sb, 255).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, x3, y3, z3).setColor(sr, sg, sb, 255).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, x4, y4, z4).setColor(sr, sg, sb, 255).setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}
}
