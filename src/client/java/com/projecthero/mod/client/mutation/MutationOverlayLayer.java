package com.projecthero.mod.client.mutation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.hero.visual.MutationVisualState;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** v0.13.22: draws every registered {@link MutationOverlays} overlay whose flag is on for the rendered player. */
public final class MutationOverlayLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public MutationOverlayLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		MutationVisualState state = MutationVisuals.state(player);
		if (state.flags().isEmpty() || player.isInvisible()) {
			return;
		}
		MutationOverlays.Context ctx = new MutationOverlays.Context(pose, buffers, light, player, getParentModel(),
				partialTick, ageInTicks, state);
		for (String flag : state.flags()) {
			for (MutationOverlays.Overlay overlay : MutationOverlays.get(flag)) {
				pose.pushPose();
				try {
					overlay.render(ctx);
				} catch (RuntimeException e) {
					// a broken overlay must never take the player render down with it
				}
				pose.popPose();
			}
		}
	}
}
