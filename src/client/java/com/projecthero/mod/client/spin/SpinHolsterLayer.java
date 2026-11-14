package com.projecthero.mod.client.spin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.miraculous.queenbee.QueenBeeRender;
import com.projecthero.mod.client.spin.model.SteelBallRender;
import com.projecthero.mod.spin.GyroSuitItem;
import com.projecthero.mod.spin.Spin;
import com.projecthero.mod.spin.SpinBalls;
import com.projecthero.mod.spin.SpinConfig;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * v0.17.4 (user: "When the suit is [worn] if the player has steel balls in their inventory they show up on the suit's holsters on their
 * hip"): Gyro's holster trousers ({@link GyroSuitItem#holstersWorn}) carry two leather cups on the front of the belt, one over each hip,
 * and each of his Steel Balls that is in his inventory but not in a hand sits in one -- the first in the right cup, the second in the
 * left ({@link SpinBalls#holsteredCount}; other viewers read the synced belt bits). Drawn on the posed body, so it turns and sneaks
 * with him.
 */
public class SpinHolsterLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final int LEATHER = 0x6A4528, LEATHER_D = 0x45301C, BRASS = 0xC9A23C;
	/** The cups' centres on the body bone (px): over each hip, on the front of the belt. */
	private static final float CUP_X = 2.8f, CUP_Y = 11.1f, CUP_Z = -3.05f;
	/** The ball's radius in blocks (1.5 px: the held ball's size). */
	private static final float BALL_R = 0.094f;

	public SpinHolsterLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !GyroSuitItem.holstersWorn(player)) {
			return;
		}
		int balls = Spin.hasPower(player) ? Math.min(SpinConfig.BALLS, SpinBalls.holsteredCount(player)) : 0;
		pose.pushPose();
		getParentModel().body.translateAndRotate(pose);
		for (int side = 0; side < 2; side++) {
			float sx = side == 0 ? -1f : 1f; // the model's -x is his right
			pose.pushPose();
			pose.translate(sx * CUP_X / 16f, CUP_Y / 16f, CUP_Z / 16f);
			cup(pose, buffers, light);
			if (side < balls) {
				pose.pushPose();
				pose.translate(0f, -0.35f / 16f, -0.1f / 16f); // sitting in the cup, its top half showing
				SteelBallRender.render(pose, buffers, light, BALL_R, 0f, false);
				pose.popPose();
			}
			pose.popPose();
		}
		pose.popPose();
	}

	/** One holster: a leather cup open at the top (a base and four walls, lower in front), a strap up to the belt and a brass stud. */
	private static void cup(PoseStack pose, MultiBufferSource buffers, int light) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(com.projecthero.mod.client.maxsteel.TurboDraw.WHITE));
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		// (in px about the cup's centre; y down)
		QueenBeeRender.box(vc, pose, 0f, 1.35f, 0f, 1.55f, 0.2f, 1.25f, LEATHER_D, light); // base
		QueenBeeRender.box(vc, pose, 0f, 0.75f, -1.2f, 1.55f, 0.6f, 0.12f, LEATHER, light); // front wall (low)
		QueenBeeRender.box(vc, pose, 0f, 0.35f, 1.15f, 1.55f, 1.0f, 0.12f, LEATHER, light); // back wall, against the belt
		QueenBeeRender.box(vc, pose, -1.5f, 0.6f, 0f, 0.12f, 0.75f, 1.25f, LEATHER, light); // sides
		QueenBeeRender.box(vc, pose, 1.5f, 0.6f, 0f, 0.12f, 0.75f, 1.25f, LEATHER, light);
		QueenBeeRender.box(vc, pose, 0f, -0.95f, 1.0f, 0.5f, 0.55f, 0.18f, LEATHER_D, light); // the strap up to the belt
		QueenBeeRender.box(vc, pose, 0f, 0.75f, -1.36f, 0.32f, 0.3f, 0.06f, BRASS, light); // stud on the front
		pose.popPose();
	}
}
