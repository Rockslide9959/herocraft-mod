package com.projecthero.mod.client.miraculous;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.render.HandRing;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * v0.16.6: the worn Miraculous on the player model, for every viewer, whenever the power is held (transformed or not):
 * <ul>
 *   <li>Tikki: the Earrings, a stud on each ear -- black, red with black spots while transformed;</li>
 *   <li>Plagg: the Ring on the right hand's finger (the shared {@link HandRing}, also in first person) -- silver, black
 *       with a green paw gem while transformed;</li>
 *   <li>Trixx: the Necklace, a chain and an orange fox-tail pendant on the chest.</li>
 * </ul>
 * Registered after the kits' suit layers, and lifted further off the body while transformed so it sits on the suit.
 */
public class MiraculousJewelLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public static final HandRing.Palette RING_PLAIN = new HandRing.Palette("cat_ring", 0xB9BEC8, 0xF2F4F8, 0x6E737C, 0xD8DCE4, 0x9AA0AA);
	public static final HandRing.Palette RING_TRANSFORMED = new HandRing.Palette("cat_ring", 0x1C1C22, 0x50505C, 0x101014, 0xC8FF8A, 0x7EE23A);

	private static final int STUD_BLACK = 0x18161A;
	private static final int STUD_RED = 0xE0242C;
	private static final int STUD_SPOT = 0x0E0C0E;
	private static final int CHAIN = 0x3A3036;
	private static final int BAIL = 0xD9A521;
	private static final int FOX = 0xEE7C24;
	private static final int FOX_DARK = 0xC45E14;
	private static final int FOX_TIP = 0xF6F0E6;

	public MiraculousJewelLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		Kwami k = Miraculous.kwami(player);
		if (k == null || player.isInvisible()) {
			return;
		}
		boolean on = Miraculous.transformed(player);
		PlayerModel<AbstractClientPlayer> m = getParentModel();
		switch (k) {
			case TIKKI -> earrings(pose, buffers, light, m.head, on);
			case PLAGG -> ring(pose, buffers, player, m.rightArm, on);
			case TRIXX -> necklace(pose, buffers, light, m.body, on);
		}
	}

	/** First person (GreenLanternRingHandMixin): Cat Noir's ring on the hand you see. */
	public static void renderFirstPersonRing(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, ModelPart arm) {
		if (Miraculous.holds(player, Kwami.PLAGG) && !player.isInvisible()) {
			ring(pose, buffers, player, arm, Miraculous.transformed(player));
		}
	}

	private static void ring(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, ModelPart arm, boolean on) {
		pose.pushPose();
		arm.translateAndRotate(pose);
		HandRing.arm(player);
		HandRing.draw(pose, HandRing.buffer(buffers), on ? 0.6f : HandRing.SKIN_GAP, on ? RING_TRANSFORMED : RING_PLAIN);
		pose.popPose();
	}

	private static void earrings(PoseStack pose, MultiBufferSource buffers, int light, ModelPart head, boolean on) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(com.projecthero.mod.client.maxsteel.TurboDraw.WHITE));
		pose.pushPose();
		head.translateAndRotate(pose);
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		float out = on ? 4.85f : 4.65f;
		for (int side = -1; side <= 1; side += 2) {
			float x = side * out;
			box(vc, pose, x, -2.7f, 0.6f, 0.42f, 0.42f, 0.42f, on ? STUD_RED : STUD_BLACK, light);
			if (on) {
				float face = x + side * 0.43f;
				box(vc, pose, face, -2.85f, 0.45f, 0.03f, 0.12f, 0.12f, STUD_SPOT, light);
				box(vc, pose, face, -2.5f, 0.8f, 0.03f, 0.1f, 0.1f, STUD_SPOT, light);
			} else {
				box(vc, pose, x + side * 0.43f, -2.86f, 0.46f, 0.03f, 0.1f, 0.1f, 0x6A666E, light); // a glint
			}
		}
		pose.popPose();
	}

	private static void necklace(PoseStack pose, MultiBufferSource buffers, int light, ModelPart body, boolean on) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(com.projecthero.mod.client.maxsteel.TurboDraw.WHITE));
		pose.pushPose();
		body.translateAndRotate(pose);
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		float front = on ? -2.62f : -2.38f;
		// the chain: from each side of the collar down to the bail
		for (int side = -1; side <= 1; side += 2) {
			pose.pushPose();
			pose.translate(side * 1.25f, 1.55f, front);
			pose.mulPose(Axis.ZP.rotationDegrees(side * -48f));
			box(vc, pose, 0f, 0f, 0f, 0.12f, 1.75f, 0.12f, CHAIN, light);
			pose.popPose();
		}
		box(vc, pose, 0f, 2.95f, front - 0.05f, 0.25f, 0.2f, 0.16f, BAIL, light);
		// the fox tail: a plump orange brush tipped white, hanging a little askew
		pose.pushPose();
		pose.translate(0f, 3.15f, front - 0.1f);
		pose.mulPose(Axis.ZP.rotationDegrees(14f));
		pose.scale(1.35f, 1.35f, 1.35f);
		box(vc, pose, 0f, 0.75f, 0f, 0.42f, 0.7f, 0.2f, FOX, light);
		box(vc, pose, 0f, 0.75f, -0.02f, 0.3f, 0.5f, 0.2f, FOX_DARK, light);
		box(vc, pose, 0.05f, 1.75f, 0f, 0.36f, 0.36f, 0.2f, FOX_TIP, light);
		pose.popPose();
		pose.popPose();
	}

	/** A lit solid box centred at (x, y, z) with half extents, in the current pose. */
	static void box(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, int rgb, int light) {
		PoseStack.Pose p = pose.last();
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		float x0 = x - hx, x1 = x + hx, y0 = y - hy, y1 = y + hy, z0 = z - hz, z1 = z + hz;
		// model space: +y is DOWN the body, -z is the front
		quad(vc, p, r, g, b, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(vc, p, r, g, b, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
		quad(vc, p, r, g, b, light, 0, 0, -1, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0);
		quad(vc, p, r, g, b, light, 0, 0, 1, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1);
		quad(vc, p, r, g, b, light, -1, 0, 0, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1);
		quad(vc, p, r, g, b, light, 1, 0, 0, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose p, int r, int g, int b, int light, float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
		vc.addVertex(p, ax, ay, az).setColor(r, g, b, 255).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, bx, by, bz).setColor(r, g, b, 255).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, cx, cy, cz).setColor(r, g, b, 255).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, dx, dy, dz).setColor(r, g, b, 255).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}
}
