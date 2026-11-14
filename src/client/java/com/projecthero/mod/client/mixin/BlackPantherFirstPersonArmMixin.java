package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.blackpanther.BlackPantherPose;
import com.projecthero.mod.client.blackpanther.BlackPantherRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.3: Black Panther's suited arm in first person. v0.17.4: the vibranium claws come out of the fingertips of the hand the
 * player sees, and the third-person pose is kept off the first-person hand (vanilla calls {@code setupAnim} for it).
 */
@Mixin(PlayerRenderer.class)
public abstract class BlackPantherFirstPersonArmMixin {
	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("HEAD"))
	private void projecthero$blackPantherHandStart(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			ModelPart sleeve, CallbackInfo ci) {
		BlackPantherPose.inFirstPersonHand = true;
	}

	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$blackPantherArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			ModelPart sleeve, CallbackInfo ci) {
		BlackPantherPose.inFirstPersonHand = false;
		if (player != Minecraft.getInstance().player || player.isInvisible()) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float progress = BlackPantherRender.progress(player, partial);
		if (progress <= 0f) {
			return;
		}
		PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
		boolean right = arm == model.rightArm;
		BlackPantherRender.renderArm(pose, buffers, light, player, arm, right, progress);
		if (progress >= 1f && BlackPantherRender.clawsShown(player)) {
			// (the suit's arm is always the wide 4 px arm -- BlueBeetleRender.model -- slim wearers included, so the claws sit on it)
			BlackPantherRender.renderClaws(pose, buffers, light, player, arm, right, true);
		}
	}
}
