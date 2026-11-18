package com.projecthero.mod.client.supersoldier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import com.projecthero.mod.supersoldier.CapShieldStow;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.5: a Captain America shield carried but not held, slung flat on the back between the shoulder blades, star up and
 * face out ({@link CapShieldStow} decides which, on the server). It follows the torso (crouch, twists) and stands off a little
 * further over a chestplate.
 */
public class CapShieldBackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	/** Back of the torso (px from the body pivot) to the straps; over a chestplate it sits out further. */
	private static final float BARE = 2.15f, ARMOURED = 3.25f;

	public CapShieldBackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limbSwing, float limbAmount, float partial,
			float age, float headYaw, float headPitch) {
		ItemStack s = p.getAttached(CapShieldStow.STOWED);
		if (s == null || s.isEmpty() || p.isInvisible() || p.isSpectator()) {
			return;
		}
		boolean armoured = !p.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
		pose.pushPose();
		getParentModel().body.translateAndRotate(pose);
		pose.translate(0f, 5.8f / 16f, (armoured ? ARMOURED : BARE) / 16f);
		// shield space -> body: front (+Z) out of the back, star (+Y) up (the body's -Y)
		pose.mulPose(Axis.ZP.rotationDegrees(180f));
		pose.scale(CapShieldPose.BODY_SCALE, CapShieldPose.BODY_SCALE, CapShieldPose.BODY_SCALE);
		pose.translate(0f, 0f, -CapShieldModel.CONTACT_Z / 16f);
		CapShieldModel.render(pose, buffers, CapShieldModel.texture(s), s.hasFoil(), light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}
}
