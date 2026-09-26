package com.projecthero.mod.client.wolverine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.wolverine.Wolverine;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.12.43: after a Wolverine survives a lethal fall his LEGS show the raw flesh model for 20 seconds, then the flesh fades away
 * (his own skin phasing back in over it) across another 20 seconds. Drawn as a layer over the normal body with every part but the
 * legs hidden; the whole-body flesh look of the Death Surge is left to {@code PlayerRendererFleshMixin}.
 */
public class WolverineLegFleshLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation FLESH = ProjectHeroMod.id("textures/entity/wolverine_flesh.png");

	public WolverineLegFleshLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (!Wolverine.hasPower(player) || player.isInvisible() || WolverineFlesh.active(player, partialTick)) {
			return; // (the whole body is already flesh while the Death Surge runs)
		}
		float alpha = WolverineFlesh.legFleshAlpha(player, partialTick);
		if (alpha <= 0.01f) {
			return;
		}
		PlayerModel<AbstractClientPlayer> model = getParentModel();
		ModelPart[] others = { model.head, model.hat, model.body, model.jacket, model.leftArm, model.rightArm, model.leftSleeve,
				model.rightSleeve };
		boolean[] was = new boolean[others.length];
		for (int i = 0; i < others.length; i++) {
			was[i] = others[i].visible;
			others[i].visible = false;
		}
		VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(FLESH));
		int argb = (Math.round(alpha * 255.0f) << 24) | 0xFFFFFF;
		model.renderToBuffer(pose, buffer, packedLight, OverlayTexture.NO_OVERLAY, argb);
		for (int i = 0; i < others.length; i++) {
			others[i].visible = was[i];
		}
	}
}
