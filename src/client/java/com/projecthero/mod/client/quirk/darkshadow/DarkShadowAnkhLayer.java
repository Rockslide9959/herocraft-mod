package com.projecthero.mod.client.quirk.darkshadow;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.quirk.darkshadow.DarkShadow;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.7: Black Ankh -- Dark Shadow wrapped round its user as armour: a purple-black feathered shell a little bigger
 * than the body (body, arms, legs; the head stays Tokoyami's), shoulder spines and long claws on both hands. Drawn for
 * every viewer while the toggle is on. Texture {@code textures/entity/quirk/dark_shadow/ankh.png} (player layout).
 */
public class DarkShadowAnkhLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/quirk/dark_shadow/ankh.png");
	private static PlayerModel<AbstractClientPlayer> wide;
	private static PlayerModel<AbstractClientPlayer> slim;

	public DarkShadowAnkhLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	private static PlayerModel<AbstractClientPlayer> model(boolean isSlim) {
		if (isSlim ? slim == null : wide == null) {
			ModelPart root = LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.42f), isSlim), 64, 64).bakeRoot();
			PlayerModel<AbstractClientPlayer> m = new PlayerModel<>(root, isSlim);
			if (isSlim) {
				slim = m;
			} else {
				wide = m;
			}
		}
		return isSlim ? slim : wide;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !DarkShadow.is(player) || !DarkShadow.ankh(player)) {
			return;
		}
		boolean isSlim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		PlayerModel<AbstractClientPlayer> m = model(isSlim);
		getParentModel().copyPropertiesTo(m);
		m.setAllVisible(true);
		m.head.visible = false;
		m.hat.visible = false;
		m.jacket.visible = false;
		m.leftSleeve.visible = false;
		m.rightSleeve.visible = false;
		m.leftPants.visible = false;
		m.rightPants.visible = false;
		int overlay = LivingEntityRenderer.getOverlayCoords(player, 0f);
		VertexConsumer shell = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
		m.renderToBuffer(pose, shell, light, overlay, 0xFFFFFFFF);

		// spines on the shoulders, claws on the hands
		VertexConsumer bits = buffers.getBuffer(RenderType.entityCutoutNoCull(DarkShadowModel.TEXTURE));
		for (int side = -1; side <= 1; side += 2) {
			ModelPart arm = side < 0 ? m.rightArm : m.leftArm;
			pose.pushPose();
			arm.translateAndRotate(pose);
			// two spines up and out from the shoulder
			for (int k = 0; k < 2; k++) {
				pose.pushPose();
				pose.translate(side * (1.0f + k * 0.8f) / 16f, -2.4f / 16f, (k - 0.5f) * 1.5f / 16f);
				pose.mulPose(Axis.XP.rotationDegrees(90f));
				pose.mulPose(Axis.YP.rotationDegrees(side * (35f + k * 20f)));
				DarkShadowModel.spike(pose, bits, light, 1.1f - k * 0.25f, 0xFFFFFFFF);
				pose.popPose();
			}
			// the claws: down past the fingers
			pose.translate(side * (isSlim ? 0.5f : 1f) / 16f, 10.5f / 16f, 0f);
			pose.mulPose(Axis.XP.rotationDegrees(-90f));
			DarkShadowModel.claw(pose, bits, light, 0.15f, 0.9f, 0xFFFFFFFF);
			pose.popPose();
		}
		pose.pushPose();
		m.body.translateAndRotate(pose);
		// a ridge of spines down the back
		for (int k = 0; k < 3; k++) {
			pose.pushPose();
			pose.translate(0f, (1.5f + k * 3.2f) / 16f, 2.4f / 16f);
			pose.mulPose(Axis.XP.rotationDegrees(30f + k * 12f));
			DarkShadowModel.spike(pose, bits, light, 0.9f - k * 0.15f, 0xFFFFFFFF);
			pose.popPose();
		}
		pose.popPose();
	}
}
