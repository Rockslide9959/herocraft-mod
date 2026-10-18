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
 *   <li>Trixx: the Necklace, a chain and an orange fox-tail pendant on the chest;</li>
 *   <li>Nooroo: the Brooch, a purple-and-silver butterfly at the collar -- darker purple while transformed;</li>
 *   <li>Wayzz (v0.16.6): the Turtle Bracelet on the left wrist, dark green beads round a turtle-shell bead -- bright
 *       green while transformed;</li>
 *   <li>Kaalki (v0.16.6): the Horseshoe Glasses on the face -- silver-grey frames; v0.16.7: hidden while the Pegasus suit
 *       covers the face, whose own goggles (painted on the suit skin) take their place.</li>
 * </ul>
 * Registered after the kits' suit layers, and lifted further off the body while transformed so it sits on the suit.
 */
public class MiraculousJewelLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public static final HandRing.Palette RING_PLAIN = new HandRing.Palette("cat_ring", 0xB9BEC8, 0xF2F4F8, 0x6E737C, 0xD8DCE4, 0x9AA0AA);
	/** Transformed: a black band and a dark round face carrying Plagg's glowing green paw print ({@link HandRing.Emblem#PAW}). */
	public static final HandRing.Palette RING_TRANSFORMED = new HandRing.Palette("cat_ring", 0x1C1C22, 0x50505C, 0x101014, 0x5CFF2A, 0x8CFF4A);

	/** The transformed ring's glowing paw print (full-bright, like the other rings' gems). */
	private static final int PAW = 0x8CFF4A;
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
			case NOOROO -> brooch(pose, buffers, light, m.body, on);
			case WAYZZ -> bracelet(pose, buffers, player, light, m.leftArm, on);
			case KAALKI -> {
				// v0.16.7 (user): transformed, Pegasus wears the suit's goggles (painted on the suit skin) -- the glasses go once
				// the reveal has passed the face and come back as it leaves it
				if (com.projecthero.mod.client.miraculous.pegasus.PegasusRender.cover(player, partialTick) < GOGGLES_COVER) {
					glasses(pose, buffers, light, m.head, on);
				}
			}
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
		float gap = on ? 0.6f : HandRing.SKIN_GAP;
		var vc = HandRing.buffer(buffers);
		// v0.16.6: the reworked ring shape -- silver with a plain domed face; transformed, black with the glowing paw emblem
		HandRing.draw(pose, vc, gap, on ? RING_TRANSFORMED : RING_PLAIN, on ? HandRing.Emblem.PAW : HandRing.Emblem.GEM);
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

	private static final int BROOCH_WING = 0xA066DC;
	private static final int BROOCH_WING_DARK = 0x4A1E78;
	private static final int BROOCH_RIM = 0xD8DCE4;
	private static final int BROOCH_BODY = 0xC8CCD4;

	private static void brooch(PoseStack pose, MultiBufferSource buffers, int light, ModelPart body, boolean on) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(com.projecthero.mod.client.maxsteel.TurboDraw.WHITE));
		pose.pushPose();
		body.translateAndRotate(pose);
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		float front = on ? -2.62f : -2.38f;
		int wing = on ? BROOCH_WING_DARK : BROOCH_WING;
		int inner = on ? 0x2A0C48 : 0xC9A4F0;
		pose.translate(0f, 2.6f, front);
		// the silver body, then two wings a side (upper broad, lower small), each with a silver rim and a pale centre
		box(vc, pose, 0f, 0.15f, -0.1f, 0.18f, 0.85f, 0.14f, BROOCH_BODY, light);
		for (int side = -1; side <= 1; side += 2) {
			pose.pushPose();
			pose.translate(side * 0.75f, -0.25f, 0f);
			pose.mulPose(Axis.ZP.rotationDegrees(side * 18f));
			box(vc, pose, 0f, 0f, 0.02f, 0.66f, 0.55f, 0.1f, BROOCH_RIM, light);
			box(vc, pose, 0f, 0f, -0.04f, 0.56f, 0.45f, 0.1f, wing, light);
			box(vc, pose, side * 0.1f, -0.05f, -0.1f, 0.2f, 0.16f, 0.06f, inner, light);
			pose.popPose();
			pose.pushPose();
			pose.translate(side * 0.55f, 0.65f, 0f);
			pose.mulPose(Axis.ZP.rotationDegrees(side * -24f));
			box(vc, pose, 0f, 0f, 0.02f, 0.42f, 0.36f, 0.1f, BROOCH_RIM, light);
			box(vc, pose, 0f, 0f, -0.04f, 0.33f, 0.28f, 0.1f, wing, light);
			pose.popPose();
		}
		pose.popPose();
	}

	// v0.16.6: Wayzz's Turtle Bracelet: cord, bead, bead glint, shell, plate, seam, rim -- dark green (bright transformed)
	private static final int[] BRACELET_PLAIN = { 0x1C3A24, 0x2E6B3C, 0x4A9A5A, 0x3A7E3E, 0x24562A, 0x8CCB6E, 0x5E7A3A };
	private static final int[] BRACELET_ON = { 0x1E6A2E, 0x46C25A, 0x8AF09A, 0x3FAE5A, 0x2A7A3A, 0xB8F08C, 0x8EB04E };

	/**
	 * v0.16.6: the Turtle Bracelet round the LEFT wrist -- a dark green cord hugging the arm (wide or slim), three jade
	 * beads on each face, and on the outer face the turtle-shell bead: an olive rim, the domed shell with a darker centre
	 * plate and light seams. Bright green while transformed, a little further off the arm to sit on the suit.
	 */
	private static void bracelet(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, int light, ModelPart arm, boolean on) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(com.projecthero.mod.client.maxsteel.TurboDraw.WHITE));
		int[] c = on ? BRACELET_ON : BRACELET_PLAIN;
		boolean slim = player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
		pose.pushPose();
		arm.translateAndRotate(pose);
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		float gap = on ? 0.55f : 0.12f;
		// the left arm's box: x -1..3 (wide) or -1..2 (slim), z -2..2; the outer face is +x
		float cx = slim ? 0.5f : 1.0f;
		float hx = (slim ? 1.5f : 2.0f) + gap, hz = 2.0f + gap;
		float y = 7.6f;
		// the cord: four slabs round the wrist
		box(vc, pose, cx, y, -hz, hx + 0.12f, 0.26f, 0.12f, c[0], light);
		box(vc, pose, cx, y, hz, hx + 0.12f, 0.26f, 0.12f, c[0], light);
		box(vc, pose, cx - hx, y, 0f, 0.12f, 0.26f, hz + 0.12f, c[0], light);
		box(vc, pose, cx + hx, y, 0f, 0.12f, 0.26f, hz + 0.12f, c[0], light);
		// the jade beads (each with a glint on its outer face)
		for (int i = -1; i <= 1; i++) {
			float bx = cx + i * (hx - 0.5f) * 0.72f, bz = i * (hz - 0.5f) * 0.72f;
			for (int s = -1; s <= 1; s += 2) {
				box(vc, pose, bx, y, s * (hz + 0.2f), 0.3f, 0.32f, 0.2f, c[1], light);
				box(vc, pose, bx - 0.08f, y - 0.1f, s * (hz + 0.41f), 0.08f, 0.08f, 0.02f, c[2], light);
			}
			box(vc, pose, cx - hx - 0.2f, y, bz, 0.2f, 0.32f, 0.3f, c[1], light);
			box(vc, pose, cx - hx - 0.41f, y - 0.1f, bz - 0.08f, 0.02f, 0.08f, 0.08f, c[2], light);
			if (i != 0) {
				box(vc, pose, cx + hx + 0.2f, y, bz, 0.2f, 0.32f, 0.3f, c[1], light);
				box(vc, pose, cx + hx + 0.41f, y - 0.1f, bz - 0.08f, 0.02f, 0.08f, 0.08f, c[2], light);
			}
		}
		// the turtle-shell bead on the outer face of the wrist
		float ox = cx + hx;
		box(vc, pose, ox + 0.2f, y, 0f, 0.18f, 0.56f, 0.66f, c[6], light);  // the rim
		box(vc, pose, ox + 0.5f, y, 0f, 0.16f, 0.5f, 0.58f, c[3], light);   // the dome
		box(vc, pose, ox + 0.72f, y, 0f, 0.08f, 0.32f, 0.38f, c[3], light); // its crown
		box(vc, pose, ox + 0.81f, y, 0f, 0.02f, 0.16f, 0.18f, c[4], light); // the centre plate
		box(vc, pose, ox + 0.67f, y, 0f, 0.02f, 0.46f, 0.03f, c[5], light); // seams
		box(vc, pose, ox + 0.67f, y, 0f, 0.02f, 0.03f, 0.54f, c[5], light);
		pose.popPose();
	}

	/**
	 * v0.16.7: how much of the Pegasus suit must show before the Horseshoe Glasses give way to the suit's goggles -- the
	 * reveal starts at the face, so by 0.15 the goggles' texels are on.
	 */
	public static final float GOGGLES_COVER = 0.15f;

	// v0.16.6: Kaalki's Horseshoe Glasses: frame, lit, dark, nail holes -- silver-grey (gold while the suit is still forming)
	private static final int[] GLASSES_PLAIN = { 0xA8ACB4, 0xD4D8E0, 0x6A6E78, 0x34363E };
	private static final int[] GLASSES_ON = { 0xE6B84A, 0xFFE08A, 0xA87A20, 0x5A3A0C };

	/**
	 * v0.16.6: the Horseshoe Glasses on the face -- each eye framed by a horseshoe (two sides with nail holes, a rounded
	 * toe at the bottom, the heels turned in at the top), an arched bridge, and the temples back along the sides of the
	 * head. Silver-grey; gold while transformed, lifted a little further off the face to sit on the suit.
	 */
	private static void glasses(PoseStack pose, MultiBufferSource buffers, int light, ModelPart head, boolean on) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(com.projecthero.mod.client.maxsteel.TurboDraw.WHITE));
		int[] c = on ? GLASSES_ON : GLASSES_PLAIN;
		pose.pushPose();
		head.translateAndRotate(pose);
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		// head space: y -8 (top) .. 0 (chin), the face is z = -4, the eyes are the row y = -4 .. -3
		float z = on ? -4.78f : -4.62f;
		float side = on ? 4.66f : 4.54f;
		for (int s = -1; s <= 1; s += 2) {
			float cx = s * 2.0f;
			for (int e = -1; e <= 1; e += 2) {
				float x = cx + e * 1.45f;
				box(vc, pose, x, -3.75f, z, 0.17f, 0.85f, 0.13f, c[0], light);                // the sides
				box(vc, pose, x - e * 0.04f, -4.1f, z - 0.14f, 0.06f, 0.06f, 0.02f, c[3], light); // nail holes
				box(vc, pose, x - e * 0.04f, -3.45f, z - 0.14f, 0.06f, 0.06f, 0.02f, c[3], light);
				box(vc, pose, cx + e * 1.2f, -4.52f, z, 0.32f, 0.15f, 0.14f, c[1], light);    // the heels, turned in
				// the rounded toe's corners
				pose.pushPose();
				pose.translate(cx + e * 1.18f, -2.72f, z);
				pose.mulPose(Axis.ZP.rotationDegrees(e * -45f));
				box(vc, pose, 0f, 0f, 0f, 0.42f, 0.16f, 0.13f, c[0], light);
				pose.popPose();
			}
			box(vc, pose, cx, -2.42f, z, 0.82f, 0.16f, 0.13f, c[2], light);                  // the toe
			// the temple: round the corner of the face, then back along the side of the head
			box(vc, pose, s * (3.45f + side) / 2f, -4.4f, z, (side - 3.45f) / 2f + 0.12f, 0.12f, 0.12f, c[0], light);
			box(vc, pose, s * side, -4.4f, (z + 0.6f) / 2f, 0.12f, 0.12f, (0.6f - z) / 2f, c[2], light);
		}
		box(vc, pose, 0f, -4.42f, z, 0.62f, 0.12f, 0.12f, c[0], light);                        // the arched bridge
		box(vc, pose, 0f, -4.6f, z, 0.32f, 0.08f, 0.12f, c[1], light);
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
