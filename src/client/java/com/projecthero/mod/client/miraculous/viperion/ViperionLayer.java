package com.projecthero.mod.client.miraculous.viperion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.render.OverlayStrips;
import com.projecthero.mod.miraculous.viperion.Viperion;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Viperion on every player who is (or is turning into) him -- the suit (the wearer's second layer going texel
 * by texel during the change) and the Lyre: strapped across his back, in his right hand during a move, spinning before
 * him for the String Shield. See {@link ViperionRender}.
 */
public class ViperionLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public ViperionLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		float cover = ViperionRender.cover(player, partialTick);
		if (cover <= 0f) {
			return;
		}
		boolean slim = ViperionRender.slim(player);
		ViperionRender.renderSuit(pose, buffers, light, getParentModel(), slim, cover);
		if (cover < 0.999f) {
			// the wearer's jacket / sleeves / trousers go texel by texel with the light (the hat -- their hair -- stays)
			OverlayStrips.render(pose, buffers, light, player, getParentModel(), (u, v) -> ViperionRender.order(u, v) > cover, false);
		}
		if (cover > 0.6f && Viperion.is(player) && ViperionSequenceClient.lyreShown(player, partialTick)) {
			renderLyre(pose, buffers, light, player, getParentModel(), ageInTicks, partialTick);
		}
	}

	/** Third person: the Lyre on his back, in his right hand, or spinning before his chest. */
	public static void renderLyre(PoseStack pose, MultiBufferSource buffers, int light, Player player, HumanoidModel<?> m, float ageInTicks, float partial) {
		pose.pushPose();
		if (Viperion.shielding(player)) {
			// spinning before him, face out: a disc of strings
			m.body.translateAndRotate(pose);
			pose.translate(0f, 5.5f / 16f, -7f / 16f);
			pose.mulPose(Axis.ZP.rotationDegrees((ageInTicks + partial) * 38f));
			pose.translate(0f, -2f / 16f, 0f);
			pose.scale(1.25f, 1.25f, 1.25f);
			ViperionRender.renderLyre(pose, buffers, light, 0.6f);
		} else if (ViperionPose.inHand(player, partial)) {
			m.rightArm.translateAndRotate(pose);
			pose.translate(-1f / 16f, 10.5f / 16f, 0f);
			// out of the fist forward-and-up with the arm (the instrument along -Z of the hand, its face to the side)
			pose.mulPose(Axis.XP.rotationDegrees(-110f));
			pose.mulPose(Axis.YP.rotationDegrees(90f));
			pose.scale(0.8f, 0.8f, 0.8f);
			ViperionRender.renderLyre(pose, buffers, light, strum(player, partial));
		} else {
			// strapped across his back, the neck up over the left shoulder
			m.body.translateAndRotate(pose);
			pose.translate(0f, 6.5f / 16f, 2.75f / 16f);
			pose.mulPose(Axis.ZP.rotationDegrees(180f - 32f));
			pose.scale(0.8f, 0.8f, 0.8f);
			ViperionRender.renderLyre(pose, buffers, light, 0f);
		}
		pose.popPose();
	}

	/** The strings shimmer while a sound move plays. */
	static float strum(Player player, float partial) {
		float[] p = ViperionPose.playing(player, partial);
		if (p == null) {
			return 0f;
		}
		int id = (int) p[0];
		if (id == Viperion.ANIM_CHORD || id == Viperion.ANIM_RESONANCE || id == Viperion.ANIM_CHECKPOINT || id == Viperion.ANIM_REWIND) {
			return Math.max(0f, 1f - p[1] / ViperionPose.length(id)) + p[1] * 0.01f;
		}
		return 0f;
	}
}
