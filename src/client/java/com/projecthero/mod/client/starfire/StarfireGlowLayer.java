package com.projecthero.mod.client.starfire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.mutation.MutationRender;
import com.projecthero.mod.starfire.Starfire;
import com.projecthero.mod.starfire.StarfireAbilityManager;
import com.projecthero.mod.starfire.StarfireConfig;
import com.projecthero.mod.starfire.data.StarfireState;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Starfire's glow overlays, on her own body, every viewer. The power never changes the skin: these are emissive extras.
 * <ul>
 *   <li><b>Green eyes</b>, always: two glowing pixels on the face. At full Fury they blaze -- bigger, white-hot, flickering.</li>
 *   <li><b>Glowing hands</b> while a starbolt is ready (Starbolt to pay for one, R off cooldown): a green glow round each fist.</li>
 *   <li><b>Flame hair</b> in flight: her hair flicks back into flame-like strands, red at the root to orange-gold at the tips,
 *   streaming behind the head and waving.</li>
 * </ul>
 */
public class StarfireGlowLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static ModelPart eyes;
	private static ModelPart blaze;
	private static ModelPart fist;
	private static ModelPart strand;

	public StarfireGlowLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	private static void bake() {
		if (eyes != null) {
			return;
		}
		eyes = MutationRender.bake(CubeListBuilder.create().texOffs(0, 0)
				.addBox(-3.0f, -4.0f, -4.26f, 2.0f, 1.0f, 0.2f)
				.addBox(1.0f, -4.0f, -4.26f, 2.0f, 1.0f, 0.2f), 4, 4);
		blaze = MutationRender.bake(CubeListBuilder.create().texOffs(0, 0)
				.addBox(-3.5f, -4.5f, -4.4f, 3.0f, 2.0f, 0.2f)
				.addBox(0.5f, -4.5f, -4.4f, 3.0f, 2.0f, 0.2f), 4, 4);
		fist = MutationRender.bake(CubeListBuilder.create().texOffs(0, 0)
				.addBox(-2.0f, 7.5f, -2.0f, 4.0f, 4.0f, 4.0f, new CubeDeformation(0.35f)), 4, 4);
		// one strand: a thin flat flame, root at the origin, streaming along +z (behind the head)
		strand = MutationRender.bake(CubeListBuilder.create().texOffs(0, 0)
				.addBox(-0.6f, -0.4f, 0f, 1.2f, 0.8f, 6.0f), 4, 4);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		StarfireState s = Starfire.peek(player);
		if (s == null || !s.hasPower || player.isInvisible()) {
			return;
		}
		bake();
		PlayerModel<AbstractClientPlayer> model = getParentModel();
		VertexConsumer vc = buffers.getBuffer(RenderType.eyes(MutationRender.WHITE));
		float t = ageInTicks + partialTick;
		boolean fury = s.mood >= StarfireConfig.MOOD_MAX - 0.5f;
		// the eyes
		pose.pushPose();
		model.head.translateAndRotate(pose);
		eyes.render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, fury ? 0xFFE8FFD8 : 0xFF5CFF5A);
		if (fury) {
			int a = 150 + (int) (80 * (0.5f + 0.5f * Mth.sin(t * 0.9f)));
			blaze.render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, (a << 24) | 0x3CFF3A);
		}
		pose.popPose();
		// the hands
		if (s.starbolt >= Starfire.boltCost(player, StarfireConfig.BOLT_COST) && Starfire.cooldownRemaining(player, StarfireAbilityManager.STARBOLT) <= 0
				|| s.chargeMove == StarfireState.CHARGE_BURST || s.beams) {
			int a = 90 + (int) (60 * (0.5f + 0.5f * Mth.sin(t * 0.35f)));
			int argb = (a << 24) | 0x5CFF5A;
			for (ModelPart arm : new ModelPart[] { model.rightArm, model.leftArm }) {
				if (!arm.visible) {
					continue;
				}
				pose.pushPose();
				arm.translateAndRotate(pose);
				fist.render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb);
				pose.popPose();
			}
		}
		// the flame hair in flight
		if (s.flying) {
			pose.pushPose();
			model.head.translateAndRotate(pose);
			for (int i = 0; i < 9; i++) {
				float fx = -3.2f + (i % 5) * 1.6f;
				float fy = -7.6f + (i / 5) * 2.6f + (i % 2) * 0.6f;
				float wave = Mth.sin(t * 0.55f + i * 1.3f);
				float len = 0.8f + 0.35f * (0.5f + 0.5f * Mth.sin(t * 0.8f + i * 2.1f));
				pose.pushPose();
				pose.translate(fx / 16f, fy / 16f, 4.0f / 16f);
				pose.mulPose(Axis.XP.rotationDegrees(-18f + wave * 10f + (i / 5) * 12f));
				pose.mulPose(Axis.YP.rotationDegrees((fx) * 4f + wave * 6f));
				pose.scale(1f, 1f, len);
				// root: deep red-orange; a second, shorter, brighter tongue inside it
				strand.render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xC8FF4A1A);
				pose.scale(0.6f, 0.6f, 1.25f);
				strand.render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xC0FFB43A);
				pose.popPose();
			}
			pose.popPose();
		}
	}
}
