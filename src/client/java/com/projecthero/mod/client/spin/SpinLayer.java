package com.projecthero.mod.client.spin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.spin.model.SteelBallRender;
import com.projecthero.mod.spin.Spin;
import com.projecthero.mod.spin.data.SpinState;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * v0.17.3 on every player who has the Spin, for every viewer: the two Steel
 * Balls when they aren't thrown -- in his right hand while it's empty (spinning; golden while a Golden Spin charges), otherwise on
 * his belt at the hips. Every ball is drawn by the shared {@link SteelBallRender}.
 */
public class SpinLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	/** A ball's radius in the hand / on the belt. */
	public static final float HAND_RADIUS = 0.11f;

	public SpinLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !Spin.hasPower(player)) {
			return;
		}
		PlayerModel<AbstractClientPlayer> model = getParentModel();
		// the balls
		SpinState s = Spin.peek(player);
		if (s == null) {
			return;
		}
		long now = player.level().getGameTime();
		boolean handFree = player.getMainHandItem().isEmpty();
		boolean golden = Spin.charging(player) != SpinState.CHARGE_NONE;
		for (int i = 0; i < 2; i++) {
			if (!s.ballReady(i, now)) {
				continue;
			}
			pose.pushPose();
			if (i == 0 && handFree) {
				model.rightArm.translateAndRotate(pose);
				pose.translate(-1f / 16f, 11.6f / 16f, -1.2f / 16f);
				float spin = (ageInTicks * (golden ? 70f : 22f)) % 360f;
				SteelBallRender.render(pose, buffers, light, HAND_RADIUS, spin, golden);
			} else {
				model.body.translateAndRotate(pose);
				pose.translate((i == 0 ? -3.4f : 3.4f) / 16f, 10.6f / 16f, -2.6f / 16f);
				SteelBallRender.render(pose, buffers, light, HAND_RADIUS, 0f, false);
			}
			pose.popPose();
		}
	}

	/** First person: the ball in the empty right hand (called from {@code SpinSkinMixin} at the end of {@code renderHand}). */
	public static void renderFirstPersonBall(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			float age) {
		if (!Spin.ballReady(player, 0) || !player.getMainHandItem().isEmpty()) {
			return;
		}
		boolean golden = Spin.charging(player) != SpinState.CHARGE_NONE;
		pose.pushPose();
		arm.translateAndRotate(pose);
		pose.translate(-1f / 16f, 11.0f / 16f, -1.4f / 16f);
		SteelBallRender.render(pose, buffers, light, HAND_RADIUS, (age * (golden ? 70f : 22f)) % 360f, golden);
		pose.popPose();
	}
}
