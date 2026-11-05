package com.projecthero.mod.client.miraculous.queenbee;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.render.OverlayStrips;
import com.projecthero.mod.miraculous.queenbee.QueenBee;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.2: Queen Bee on every player who is (or is turning into) her -- the suit (the wearer's jacket / sleeves / trousers
 * going texel by texel during the change; her hat layer, her hair, stays) and the spinning top: hanging at her right hip,
 * in her right hand during a move, spun overhead as a rotor (Top Copter), spinning face-out before her (Spinning Shield).
 * Thrown or hovering for the volley it is drawn in the world ({@link QueenBeeWorldFx}).
 */
public class QueenBeeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public QueenBeeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		float cover = QueenBeeRender.cover(player, partialTick);
		if (cover <= 0f) {
			return;
		}
		boolean slim = QueenBeeRender.slim(player);
		QueenBeeRender.renderSuit(pose, buffers, light, getParentModel(), slim, cover);
		if (cover < 0.999f) {
			OverlayStrips.render(pose, buffers, light, player, getParentModel(), (u, v) -> QueenBeeRender.order(u, v) > cover, false);
		}
		// (in the long transformation the scene draws the top until it reaches her hip)
		if (cover > 0.6f && QueenBee.is(player) && !QueenBee.topAway(player) && QueenBeeSequenceClient.topOnHip(player, partialTick)) {
			renderTop(pose, buffers, light, player, getParentModel(), ageInTicks, partialTick);
		}
	}

	/** Third person: the top at her hip, in her hand, overhead or spinning before her. */
	public static void renderTop(PoseStack pose, MultiBufferSource buffers, int light, Player player, HumanoidModel<?> m, float ageInTicks, float partial) {
		float t = ageInTicks + partial;
		float venom = QueenBee.venomArmed(player) ? 0.75f + 0.25f * (float) Math.sin(t * 0.5f) : 0f;
		pose.pushPose();
		if (QueenBee.shielding(player)) {
			// spinning face-out before her chest: a whirling striped disc
			m.body.translateAndRotate(pose);
			pose.translate(0f, 5.5f / 16f, -7f / 16f);
			pose.mulPose(Axis.XP.rotationDegrees(90f));
			pose.scale(1.7f, 1.7f, 1.7f);
			QueenBeeRender.renderTop(pose, buffers, light, t * 1.4f, venom);
		} else if (QueenBee.copter(player)) {
			// spun overhead like a rotor, point down onto her fist
			m.rightArm.translateAndRotate(pose);
			pose.translate(-1f / 16f, 13.5f / 16f, 0f);
			pose.mulPose(Axis.ZP.rotationDegrees(180f));
			pose.scale(1.4f, 1.0f, 1.4f);
			QueenBeeRender.renderTop(pose, buffers, light, t * 1.6f, venom);
		} else if (QueenBeePose.inHand(player, partial)) {
			// in her fist, the point out past her knuckles
			m.rightArm.translateAndRotate(pose);
			pose.translate(-1f / 16f, 12.5f / 16f, 0f);
			pose.mulPose(Axis.ZP.rotationDegrees(180f));
			pose.scale(0.75f, 0.75f, 0.75f);
			QueenBeeRender.renderTop(pose, buffers, light, t * 0.6f, venom);
		} else {
			// hanging from her belt at the right hip
			m.body.translateAndRotate(pose);
			pose.translate(-4.7f / 16f, 11.0f / 16f, 0.3f / 16f);
			pose.mulPose(Axis.ZP.rotationDegrees(180f - 12f));
			pose.scale(0.6f, 0.6f, 0.6f);
			QueenBeeRender.renderTop(pose, buffers, light, 0f, venom);
		}
		pose.popPose();
	}
}
