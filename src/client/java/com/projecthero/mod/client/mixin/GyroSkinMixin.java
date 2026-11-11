package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.gyro.GyroLayer;
import com.projecthero.mod.client.gyro.GyroSkin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.3 Gyro Zeppeli: a player with the Spin is drawn in Gyro's skin ({@link GyroSkin}) -- the body through
 * {@code getTextureLocation}, the first-person arm by swapping the texture {@code renderHand} reads -- and holds a Steel Ball in his
 * empty right hand in first person ({@link GyroLayer#renderFirstPersonBall}).
 */
@Mixin(PlayerRenderer.class)
public abstract class GyroSkinMixin {
	@Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
			at = @At("RETURN"), cancellable = true)
	private void projecthero$gyroSkin(AbstractClientPlayer player, CallbackInfoReturnable<ResourceLocation> cir) {
		if (GyroSkin.shows(player)) {
			cir.setReturnValue(GyroSkin.texture(player));
		}
	}

	@ModifyVariable(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("STORE"), ordinal = 0)
	private ResourceLocation projecthero$gyroArmSkin(ResourceLocation skin, PoseStack pose, MultiBufferSource buffers, int light,
			AbstractClientPlayer player) {
		return GyroSkin.shows(player) ? GyroSkin.texture(player) : skin;
	}

	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$gyroHandBall(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			ModelPart sleeve, CallbackInfo ci) {
		if (!GyroSkin.shows(player) || player.isInvisible() || arm != ((PlayerRenderer) (Object) this).getModel().rightArm) {
			return;
		}
		float age = player.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		GyroLayer.renderFirstPersonBall(pose, buffers, light, player, arm, age);
	}
}
