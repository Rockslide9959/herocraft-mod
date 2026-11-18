package com.projecthero.mod.client.powerarmour;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.powerarmour.PowerArmour;
import com.projecthero.mod.powerarmour.PowerArmourConfig;
import com.projecthero.mod.powerarmour.PowerArmourState;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * v0.17.5 Power Armour: the suit on its wearer, riding the posed player model, for every viewer. Round 2: the wearer's own model
 * is hidden ({@code PowerArmourHideBodyMixin}); the suit is drawn at {@link PowerArmourConfig#SUIT_SCALE} of a player whatever
 * the wearer's current scale (sealed, their {@code SCALE} attribute already is that), and while the suit is open round them their
 * own body is drawn inside it at their real size ({@link SuitRenderer#renderInner}).
 */
public class PowerArmourLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public PowerArmourLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		PowerArmourState s = PowerArmour.state(player);
		if (s == null) {
			return;
		}
		float time = player.level().getGameTime() + partialTick;
		float scale = Math.max(0.05f, player.getScale());
		int overlay = LivingEntityRenderer.getOverlayCoords(player, 0f);
		float lift = SuitLooks.innerLift(s, time);
		if (lift >= 0f) {
			pose.pushPose();
			SuitRenderer.scaleAboutFeet(pose, 1f / scale);
			SuitRenderer.renderInner(pose, buffers, light, overlay, getParentModel(), player, lift);
			pose.popPose();
		}
		SuitRenderer.Look look = SuitLooks.wearer(player, time);
		if (look == null) {
			return;
		}
		pose.pushPose();
		SuitRenderer.scaleAboutFeet(pose, PowerArmourConfig.SUIT_SCALE / scale);
		SuitRenderer.render(pose, buffers, light, overlay, SuitLooks.parts(getParentModel()), look);
		pose.popPose();
	}
}
