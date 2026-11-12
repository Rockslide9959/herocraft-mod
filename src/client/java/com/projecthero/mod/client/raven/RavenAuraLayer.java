package com.projecthero.mod.client.raven;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.raven.Raven;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Raven: her look, as overlays only (the power never changes the skin).
 * <ul>
 *   <li><b>White glowing eyes</b> while she casts (a move a moment ago, a hold, the shield, meditating).</li>
 *   <li><b>Four red eyes</b> at Rage (Composure under 10) and through Trigon's Wrath.</li>
 *   <li><b>Trigon's Wrath</b>: a shadow shell over the whole (3x scaled) body -- a giant cloaked shadow with the red eyes burning.</li>
 *   <li><b>The black hood</b> of Trigon's Wrath (the cape is {@link RavenCloakLayer}). The craftable Raven's Cloak costume that
 *   also drew a hood was removed on 2026-11-12.</li>
 * </ul>
 */
public class RavenAuraLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation SHADOW = ProjectHeroMod.id("textures/entity/raven/shadow.png");
	private static final float PX = 1f / 16f;

	public RavenAuraLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float pt, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || Raven.inShadow(player)) {
			return;
		}
		boolean raven = Raven.hasPower(player);
		boolean wrath = raven && Raven.inWrath(player);
		PlayerModel<AbstractClientPlayer> m = getParentModel();
		if (wrath) {
			// the shadow shell: the whole body again, a little larger, near-black
			pose.pushPose();
			pose.scale(1.07f, 1.04f, 1.07f);
			float flicker = 0.88f + 0.06f * Mth.sin(ageInTicks * 0.3f);
			int a = Math.round(255 * flicker);
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(SHADOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
					(a << 24) | 0xFFFFFF);
			pose.popPose();
		}
		boolean hooded = wrath && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
		if (hooded) {
			pose.pushPose();
			m.head.translateAndRotate(pose);
			hood(pose, buffers, 0xFF0E0810, light);
			pose.popPose();
		}
		if (!raven) {
			return;
		}
		boolean rage = Raven.rage(player);
		boolean casting = Raven.casting(player);
		if (!rage && !casting) {
			return;
		}
		pose.pushPose();
		m.head.translateAndRotate(pose);
		if (wrath) {
			pose.scale(1.07f, 1.04f, 1.07f);
		}
		VertexConsumer core = buffers.getBuffer(RenderType.entityTranslucentEmissive(RavenDraw.WHITE));
		float z = -4.05f * PX;
		if (rage) {
			int red = 0xFFFF1A1A;
			// two pairs, one above the other
			eye(core, pose, -3.0f, -1.6f, -4.1f, -3.2f, z, red);
			eye(core, pose, 1.6f, 3.0f, -4.1f, -3.2f, z, red);
			eye(core, pose, -2.6f, -1.2f, -5.6f, -4.8f, z, red);
			eye(core, pose, 1.2f, 2.6f, -5.6f, -4.8f, z, red);
			VertexConsumer halo = buffers.getBuffer(RenderType.lightning());
			for (float[] e : new float[][] { { -3.0f, -1.6f, -4.1f, -3.2f }, { 1.6f, 3.0f, -4.1f, -3.2f }, { -2.6f, -1.2f, -5.6f, -4.8f },
					{ 1.2f, 2.6f, -5.6f, -4.8f } }) {
				haloQuad(halo, pose, e[0] - 0.6f, e[1] + 0.6f, e[2] - 0.6f, e[3] + 0.6f, z - 0.02f * PX, 0x90FF2020);
			}
		} else {
			int white = 0xFFF4F0FF;
			eye(core, pose, -3.0f, -1.0f, -4.0f, -3.0f, z, white);
			eye(core, pose, 1.0f, 3.0f, -4.0f, -3.0f, z, white);
			VertexConsumer halo = buffers.getBuffer(RenderType.lightning());
			haloQuad(halo, pose, -3.6f, -0.4f, -4.6f, -2.4f, z - 0.02f * PX, 0x70D8C8FF);
			haloQuad(halo, pose, 0.4f, 3.6f, -4.6f, -2.4f, z - 0.02f * PX, 0x70D8C8FF);
		}
		pose.popPose();
	}

	/** One eye: a flat full-bright quad on the face, in head pixels (x0..x1, y0..y1). */
	private static void eye(VertexConsumer vc, PoseStack pose, float x0, float x1, float y0, float y1, float z, int argb) {
		PoseStack.Pose last = pose.last();
		int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		float[][] pts = { { x0, y0 }, { x0, y1 }, { x1, y1 }, { x1, y0 } };
		for (float[] p : pts) {
			vc.addVertex(last, p[0] * PX, p[1] * PX, z).setColor(r, g, b, a).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(LightTexture.FULL_BRIGHT).setNormal(last, 0f, 0f, -1f);
		}
	}

	private static void haloQuad(VertexConsumer vc, PoseStack pose, float x0, float x1, float y0, float y1, float z, int argb) {
		PoseStack.Pose last = pose.last();
		int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		float[][] pts = { { x0, y0 }, { x0, y1 }, { x1, y1 }, { x1, y0 } };
		for (float[] p : pts) {
			vc.addVertex(last, p[0] * PX, p[1] * PX, z).setColor(r, g, b, a);
		}
	}

	/**
	 * The hood, in head space (pixels): a shell round the head, open at the face, with a peak at the back. Front band over the brow and
	 * the two sides framing the face. Only Trigon's Wrath draws it now.
	 */
	private static void hood(PoseStack pose, MultiBufferSource buffers, int argb, int light) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(RavenDraw.WHITE));
		PoseStack.Pose last = pose.last();
		float o = 4.7f, top = -9.0f, bottom = 0.6f;
		int inner = RavenDraw.lerpColour(argb, 0xFF000000, 0.55f);
		// top, back, the two sides
		box(vc, last, light, argb, new Vec3(-o, top, -o), new Vec3(o, top, -o), new Vec3(o, top, o), new Vec3(-o, top, o)); // top
		box(vc, last, light, argb, new Vec3(-o, top, o), new Vec3(o, top, o), new Vec3(o, bottom, o + 0.6), new Vec3(-o, bottom, o + 0.6)); // back
		box(vc, last, light, argb, new Vec3(-o, top, -o), new Vec3(-o, top, o), new Vec3(-o - 0.3, bottom, o + 0.6), new Vec3(-o - 0.5, bottom, -o + 0.4)); // side
		box(vc, last, light, argb, new Vec3(o, top, -o), new Vec3(o, top, o), new Vec3(o + 0.3, bottom, o + 0.6), new Vec3(o + 0.5, bottom, -o + 0.4)); // side
		// the peak at the back of the head
		box(vc, last, light, argb, new Vec3(-2.2, top, o), new Vec3(2.2, top, o), new Vec3(0, top + 1.2, o + 1.6), new Vec3(0, top + 1.2, o + 1.6));
		// the front: a brow band and the two cheeks, so the face shows through the opening
		box(vc, last, light, argb, new Vec3(-o, top, -o), new Vec3(o, top, -o), new Vec3(o, -7.2, -o - 0.1), new Vec3(-o, -7.2, -o - 0.1));
		box(vc, last, light, argb, new Vec3(-o, -7.2, -o - 0.1), new Vec3(-3.3, -7.2, -o - 0.1), new Vec3(-3.6, bottom, -o + 0.2),
				new Vec3(-o - 0.5, bottom, -o + 0.4));
		box(vc, last, light, argb, new Vec3(3.3, -7.2, -o - 0.1), new Vec3(o, -7.2, -o - 0.1), new Vec3(o + 0.5, bottom, -o + 0.4),
				new Vec3(3.6, bottom, -o + 0.2));
		// the shadowed inside of the opening
		box(vc, last, light, inner, new Vec3(-3.3, -7.2, -o + 0.05), new Vec3(3.3, -7.2, -o + 0.05), new Vec3(3.3, -6.6, -4.2), new Vec3(-3.3, -6.6, -4.2));
	}

	private static void box(VertexConsumer vc, PoseStack.Pose pose, int light, int argb, Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
		int al = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bl = argb & 0xFF;
		Vec3 n = b.subtract(a).cross(d.subtract(a));
		n = n.lengthSqr() < 1.0e-9 ? new Vec3(0, -1, 0) : n.normalize();
		for (Vec3 p : new Vec3[] { a, b, c, d }) {
			vc.addVertex(pose, (float) p.x * PX, (float) p.y * PX, (float) p.z * PX).setColor(r, g, b(bl), al).setUv(0.5f, 0.5f)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
		}
	}

	private static int b(int v) {
		return v;
	}
}
