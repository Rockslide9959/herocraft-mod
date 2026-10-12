package com.projecthero.mod.client.wolverine;

import com.projecthero.mod.wolverine.Wolverine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;

/**
 * Draws {@link WolverineClawsModel} on both hands of any player who is Wolverine with the claws out
 * (third person, and every other player's view). Reads only the synced state, so it is correct in
 * multiplayer. The first-person hand is drawn by {@code PlayerRendererClawsMixin}. v0.16.1: also feeds the
 * blade positions to {@link WolverineClawFx} for the slash trails.
 */
public class WolverineClawsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	static final int SILVER = 0xFFFFFFFF;
	static final int RAGE_TINT = 0xFFFFC4C4;

	public WolverineClawsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !WolverineClawsModel.visible(player, partialTick)) {
			return;
		}
		float ext = WolverineClawsModel.extension(player, partialTick);
		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		int color = Wolverine.raging(player) ? RAGE_TINT : SILVER;
		VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(WolverineClawsModel.TEXTURE));
		for (boolean right : new boolean[] {true, false}) {
			boolean animating = WolverineClawPose.trailing(player, partialTick,
					right ? net.minecraft.world.entity.HumanoidArm.RIGHT : net.minecraft.world.entity.HumanoidArm.LEFT);
			pose.pushPose();
			(right ? getParentModel().rightArm : getParentModel().leftArm).translateAndRotate(pose);
			WolverineClawsModel.render(pose, buffer, packedLight, OverlayTexture.NO_OVERLAY, right, slim, ext,
					WolverineClawsModel.WORLD_LENGTH, color);
			WolverineClawFx.recordWorld(player, right, animating, pose.last().pose(), slim, ext, WolverineClawsModel.WORLD_LENGTH);
			pose.popPose();
		}
	}
}
