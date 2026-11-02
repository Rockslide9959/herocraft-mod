package com.projecthero.mod.client.omnitrix;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.2 (user: "this watch doesnt use dial icons it displays holograms of the aliens which can just be see through green models that
 * show up as the models"): the Recalibrated Omnitrix's dial. While it is primed, the selected alien stands over the face as a hologram --
 * its own body (the wide player model in its skin), tinted green, see-through and glowing, slowly turning -- on a cone of green light
 * rising from the face. Drawn by {@link OmnitrixWatch} in the arm's space (x out of the face), first and third person alike.
 */
public final class RecalHologram {
	/** A plain white 64x64 sheet: the hologram's solid "volume" pass is the model's shape in flat green. */
	private static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/omnitrix/holo_white.png");
	/** How tall the figure stands (blocks) and how high over the face its feet float. */
	private static final float HEIGHT = 0.34f, FLOAT = 0.04f;
	private static PlayerModel<LivingEntity> model;

	private RecalHologram() {
	}

	private static PlayerModel<LivingEntity> model() {
		if (model == null) {
			model = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
		}
		return model;
	}

	/**
	 * Draws {@code alienId}'s hologram standing on the face centred at ({@code faceX}, {@code cy}, 0) px, faded by {@code alpha} (0..1).
	 * {@code age} drives the turn, the bob and the shimmer.
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, String alienId, float faceX, float cy, float alpha, float age) {
		if (alpha <= 0.01f) {
			return;
		}
		AlienClientKit kit = AlienClients.of(alienId);
		ResourceLocation skin = kit == null || kit.customBody() ? null : kit.skin();
		int lit = LightTexture.FULL_BRIGHT;
		float shimmer = 0.85f + 0.15f * Mth.sin(age * 0.9f);
		float bob = 0.012f * Mth.sin(age * 0.12f);
		// ---- the cone of light from the face up round the figure
		cone(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE)), faceX / 16f, cy / 16f, alpha);
		if (skin == null) {
			return;
		}
		PlayerModel<LivingEntity> m = model();
		for (ModelPart part : new ModelPart[] { m.head, m.hat, m.body, m.jacket, m.rightArm, m.leftArm, m.rightSleeve, m.leftSleeve, m.rightLeg, m.leftLeg,
				m.rightPants, m.leftPants }) {
			part.resetPose();
			part.visible = true;
		}
		m.crouching = false;
		m.young = false;
		m.riding = false;
		m.attackTime = 0f;
		// a calm hologram pose: arms a little out
		m.rightArm.zRot = 0.12f;
		m.leftArm.zRot = -0.12f;
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftSleeve.copyFrom(m.leftArm);
		pose.pushPose();
		// stand on the face: the figure's up (+y) is the face's normal (+x)
		pose.translate(faceX / 16f + FLOAT + bob, cy / 16f, 0f);
		pose.mulPose(Axis.ZP.rotationDegrees(-90f));
		pose.mulPose(Axis.YP.rotationDegrees((age * 2.2f) % 360f));
		float s = HEIGHT / 1.875f;
		pose.scale(s, s, s);
		pose.scale(-1f, -1f, 1f);
		pose.translate(0f, -1.501f, 0f);
		int a1 = Math.round(70 * alpha * shimmer), a2 = Math.round(175 * alpha * shimmer);
		// the solid green volume, then the skin's detail in green over it
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE)), lit, OverlayTexture.NO_OVERLAY, (a1 << 24) | 0x6CFF7A);
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(skin)), lit, OverlayTexture.NO_OVERLAY, (a2 << 24) | 0xC8FFC0);
		pose.popPose();
	}

	/** A cone of light from the face (radius 1.1 px) widening to 3.4 px at the figure's head, fading out as it rises. */
	private static void cone(PoseStack pose, VertexConsumer vc, float x0, float cy, float alpha) {
		PoseStack.Pose p = pose.last();
		int seg = 20;
		float r0 = 1.1f / 16f, r1 = 3.4f / 16f, x1 = x0 + HEIGHT + 0.05f;
		int lit = LightTexture.FULL_BRIGHT;
		int aBot = Math.round(110 * alpha), aTop = 0;
		for (int i = 0; i < seg; i++) {
			double t0 = i * Math.PI * 2 / seg, t1 = (i + 1) * Math.PI * 2 / seg;
			float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
			v(vc, p, x0, cy + c0 * r0, s0 * r0, 0f, 0f, aBot, c0, s0, lit);
			v(vc, p, x0, cy + c1 * r0, s1 * r0, 1f, 0f, aBot, c1, s1, lit);
			v(vc, p, x1, cy + c1 * r1, s1 * r1, 1f, 1f, aTop, c1, s1, lit);
			v(vc, p, x1, cy + c0 * r1, s0 * r1, 0f, 1f, aTop, c0, s0, lit);
		}
	}

	private static void v(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float vv, int a, float ny, float nz, int light) {
		vc.addVertex(p, x, y, z).setColor(90, 255, 110, a).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, ny, nz);
	}
}
