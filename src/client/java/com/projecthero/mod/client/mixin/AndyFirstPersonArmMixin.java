package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.client.andy.AndyRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/** v0.17.3: Andy's arm regrowing in first person (the vanilla arm is hidden while it is broken). */
@Mixin(PlayerRenderer.class)
public abstract class AndyFirstPersonArmMixin {
	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$andyArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm, ModelPart sleeve,
			CallbackInfo ci) {
		AndyState s = Andy.peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
		int part = arm == model.rightArm ? AndyState.RIGHT_ARM : AndyState.LEFT_ARM;
		if (!s.broken(part)) {
			return;
		}
		float p = s.regrowth(part, player.level().getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
		if (p <= 0f) {
			return;
		}
		pose.pushPose();
		arm.translateAndRotate(pose);
		AndyRender.regrow(pose, buffers, light, player.getSkin().texture(), AndyRender.slim(player), part, Math.min(1f, p));
		pose.popPose();
	}
}
