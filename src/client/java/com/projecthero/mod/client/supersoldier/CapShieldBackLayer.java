package com.projecthero.mod.client.supersoldier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import com.projecthero.mod.supersoldier.CapShieldSlot;
import com.projecthero.mod.supersoldier.CapShieldStow;
import com.projecthero.mod.supersoldier.item.CapBattleSuitItem;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.5: a Captain America shield carried but not held, slung flat on the back between the shoulder blades, star up and
 * face out ({@link CapShieldStow} decides which, on the server). It follows the torso (crouch, twists) and stands off a little
 * further over a chestplate.
 *
 * <p>v0.17.5 Battle Suit: the shield in the suit top's <b>shield slot</b> ({@link CapShieldSlot}, read from the worn top -- synced with
 * the equipment, so every viewer sees it) hangs here the same way; while it is raised to block (and while it lowers again) it is
 * strapped to the off-hand forearm instead ({@link CapShieldPose#renderStrapped}, the held-shield look).
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
		if (p.isInvisible() || p.isSpectator()) {
			return;
		}
		ItemStack slotted = CapShieldSlot.slotted(p);
		if (!slotted.isEmpty() && CapShieldSlot.hasSlot(p) && CapShieldPose.block(p, partial) > 0.001f) {
			// raised (or still lowering): strapped to the off-hand forearm, like a shield held in that hand
			HumanoidArm arm = p.getMainArm().getOpposite();
			pose.pushPose();
			getParentModel().translateToHand(arm, pose);
			CapShieldPose.renderStrapped(p, slotted, arm, arm == HumanoidArm.RIGHT ? getParentModel().rightArm : getParentModel().leftArm, pose,
					buffers, light);
			pose.popPose();
			return;
		}
		ItemStack s = slotted.isEmpty() ? p.getAttached(CapShieldStow.STOWED) : slotted;
		if (s == null || s.isEmpty()) {
			return;
		}
		// the Battle Suit is a skin-tight costume: no chestplate bulk to stand off
		ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
		boolean armoured = !chest.isEmpty() && !(chest.getItem() instanceof CapBattleSuitItem);
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
