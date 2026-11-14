package com.projecthero.mod.client.beastboy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.beastboy.BeastBoy;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * v0.17.3 Beast Boy: in human form he keeps his own skin with a green tint over it -- the player model drawn again, translucent,
 * with his skin multiplied by Sakutia green (no skin change, an overlay like the other powers' glows).
 */
public final class BeastTintLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	/** ARGB: green at a little over half strength. */
	private static final int TINT = 0x8C46D232;

	public BeastTintLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !BeastBoy.hasPower(player) || BeastBoy.morphed(player)) {
			return;
		}
		getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture())), light,
				OverlayTexture.NO_OVERLAY, TINT);
	}

	/**
	 * v0.17.4: the same green tint on the first-person arm (from {@code SuitFirstPersonArmMixin}): the arm and sleeve vanilla just drew,
	 * drawn again translucent and tinted, exactly as this layer does over the whole body in third person.
	 */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm, ModelPart sleeve) {
		if (player.isInvisible() || !BeastBoy.hasPower(player) || BeastBoy.morphed(player)) {
			return;
		}
		var vc = buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
		arm.render(pose, vc, light, OverlayTexture.NO_OVERLAY, TINT);
		sleeve.render(pose, vc, light, OverlayTexture.NO_OVERLAY, TINT);
	}
}
