package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.miraculous.catnoir.CataclysmFx;
import com.projecthero.mod.client.miraculous.catnoir.CatNoirFirstPerson;
import com.projecthero.mod.client.miraculous.catnoir.CatNoirRender;
import com.projecthero.mod.miraculous.catnoir.CatNoir;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.16.6: Cat Noir in first person -- the suit sleeve over the bare arm vanilla just rendered, then (main arm) the
 * baton in the fist and the CATACLYSM hand (v0.16.6: the dark matter on the fist and the compact crackling ball). See {@link CatNoirFirstPerson}.
 */
@Mixin(PlayerRenderer.class)
public abstract class CatNoirFirstPersonArmMixin {
	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$catNoirArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			ModelPart sleeve, CallbackInfo ci) {
		if (player != Minecraft.getInstance().player || player.isInvisible()) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float cover = CatNoirRender.cover(player, partial);
		if (cover <= 0f) {
			return;
		}
		PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
		boolean right = arm == model.rightArm;
		CatNoirRender.renderArm(pose, buffers, light, arm, right, cover, CatNoirRender.slim(player));
		float age = player.tickCount + partial;
		CatNoirFirstPerson.renderBaton(pose, buffers, light, player, arm, right, age, partial);
		if (CatNoir.charged(player) && CatNoirFirstPerson.isMainArm(player, right)) {
			CataclysmFx.renderHand(pose, buffers, player, arm, right, CatNoirRender.slim(player), age, partial, true);
		}
	}
}
