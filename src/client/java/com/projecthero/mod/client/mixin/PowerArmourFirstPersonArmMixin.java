package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.powerarmour.SuitLooks;
import com.projecthero.mod.client.powerarmour.SuitRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.5 Power Armour: the suit's arm (plates, trim, lights, mounted weapon) on the first-person hand. Round 2: the hand itself
 * is hidden while suited ({@code PowerArmourHideBodyMixin}), so it is drawn here: inside the suit's arm while the arm is open, as
 * it is while walking into or out of the frame.
 */
@Mixin(PlayerRenderer.class)
public abstract class PowerArmourFirstPersonArmMixin {
	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$powerArmourArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			ModelPart sleeve, CallbackInfo ci) {
		if (player != Minecraft.getInstance().player || player.isInvisible()) {
			return;
		}
		com.projecthero.mod.powerarmour.PowerArmourState s = com.projecthero.mod.powerarmour.PowerArmour.state(player);
		if (!SuitLooks.hidesBody(s)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
		boolean right = arm == model.rightArm;
		SuitRenderer.Look look = SuitLooks.wearer(player, player.level().getGameTime() + partial);
		if (look == null) {
			// walking into / out of the frame: the hand as it is
			boolean a = arm.visible, b = sleeve.visible;
			arm.visible = true;
			sleeve.visible = player.isModelPartShown(right ? net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE
					: net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE);
			arm.render(pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.entitySolid(player.getSkin().texture())), light,
					net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
			sleeve.render(pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(player.getSkin().texture())), light,
					net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
			arm.visible = a;
			sleeve.visible = b;
			return;
		}
		look.firstPerson = true;
		if (look.open(right ? com.projecthero.mod.client.powerarmour.SuitPanel.RARM_FRONT : com.projecthero.mod.client.powerarmour.SuitPanel.LARM_FRONT) > 0f
				|| look.open(right ? com.projecthero.mod.client.powerarmour.SuitPanel.RARM_BACK : com.projecthero.mod.client.powerarmour.SuitPanel.LARM_BACK) > 0f) {
			SuitRenderer.renderInnerArm(pose, buffers, light, arm, sleeve, right, player);
		}
		SuitRenderer.renderArm(pose, buffers, light, arm, right, look);
	}
}
