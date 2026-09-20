package com.projecthero.mod.client.render;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.item.GreenLanternItems;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.11.10, explicit user request: a tiny, always-on Power Ring on the hand of any bonded Green
 * Lantern -- "literally 1 pixel that is always active on the players skin and doesn't affect their
 * skin or conflict in anyway". Mirrors {@link ArcReactorLayer}'s approach exactly (a static item render
 * anchored to a limb bone, purely cosmetic, driven by the synced {@link GreenLantern#hasPower} state so
 * it shows correctly for other players too) rather than editing the skin texture itself, which is
 * exactly what keeps it from ever touching or conflicting with the player's own skin.
 *
 * <p>Sits on whichever arm is the player's main hand, scaled down to a bare fleck of colour rather than
 * a normal item-sized render -- it is meant to read as a worn ring, not a held item.
 */
public class PowerRingLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private final ItemRenderer itemRenderer;
	private final ItemStack ring = new ItemStack(GreenLanternItems.POWER_RING);

	public PowerRingLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
			ItemRenderer itemRenderer) {
		super(parent);
		this.itemRenderer = itemRenderer;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (!GreenLantern.hasPower(player) || player.isInvisible()) {
			return;
		}
		boolean rightHand = player.getMainArm() == HumanoidArm.RIGHT;
		pose.pushPose();
		(rightHand ? getParentModel().rightArm : getParentModel().leftArm).translateAndRotate(pose);
		// Arm-bone space: Y runs down the forearm toward the hand/wrist -- 0.62 sits right at the wrist,
		// just short of the hand itself, and a fraction outward on X centres it on the finger rather than
		// dead-centre of the wrist bone.
		double sideOffset = rightHand ? 0.02 : -0.02;
		pose.translate(sideOffset, 0.62, 0.0);
		pose.mulPose(Axis.XP.rotationDegrees(90f));
		// "literally 1 pixel": a fleck, not a normal item render -- ArcReactorLayer's own chest emblem
		// uses 0.28f for comparison.
		pose.scale(0.045f, 0.045f, 0.045f);
		itemRenderer.renderStatic(ring, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
				pose, buffers, player.level(), player.getId());
		pose.popPose();
	}
}
