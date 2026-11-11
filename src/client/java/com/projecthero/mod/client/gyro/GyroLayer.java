package com.projecthero.mod.client.gyro;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.gyro.model.SteelBallRender;
import com.projecthero.mod.client.mutation.MutationRender;
import com.projecthero.mod.gyro.Gyro;
import com.projecthero.mod.gyro.data.GyroState;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * v0.17.3 Gyro Zeppeli on every player who has the Spin, for every viewer: his hat (brim and crown, on the head) and the two Steel
 * Balls when they aren't thrown -- in his right hand while it's empty (spinning; golden while a Golden Spin charges), otherwise on
 * his belt at the hips. Every ball is drawn by the shared {@link SteelBallRender}.
 */
public class GyroLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	/** A ball's radius in the hand / on the belt. */
	public static final float HAND_RADIUS = 0.11f;

	private static ModelPart hat;

	public GyroLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	static ModelPart hat() {
		if (hat == null) {
			hat = MutationRender.bake(CubeListBuilder.create()
					.texOffs(0, 0).addBox(-4.5f, -11.2f, -4.5f, 9f, 3f, 9f)
					.texOffs(0, 12).addBox(-6f, -8.6f, -6f, 12f, 1f, 12f), 64, 32);
		}
		return hat;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !GyroSkin.shows(player)) {
			return;
		}
		PlayerModel<AbstractClientPlayer> model = getParentModel();
		// the hat
		pose.pushPose();
		model.head.translateAndRotate(pose);
		hat().render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(GyroSkin.HAT)), light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
		// the balls
		GyroState s = Gyro.peek(player);
		if (s == null) {
			return;
		}
		long now = player.level().getGameTime();
		boolean handFree = player.getMainHandItem().isEmpty();
		boolean golden = Gyro.charging(player) != GyroState.CHARGE_NONE;
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

	/** First person: the ball in the empty right hand (called from {@code GyroSkinMixin} at the end of {@code renderHand}). */
	public static void renderFirstPersonBall(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			float age) {
		if (!Gyro.ballReady(player, 0) || !player.getMainHandItem().isEmpty()) {
			return;
		}
		boolean golden = Gyro.charging(player) != GyroState.CHARGE_NONE;
		pose.pushPose();
		arm.translateAndRotate(pose);
		pose.translate(-1f / 16f, 11.0f / 16f, -1.4f / 16f);
		SteelBallRender.render(pose, buffers, light, HAND_RADIUS, (age * (golden ? 70f : 22f)) % 360f, golden);
		pose.popPose();
	}
}
