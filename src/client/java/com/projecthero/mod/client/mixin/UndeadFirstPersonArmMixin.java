package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.client.undead.UndeadRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/** v0.17.3: Undead's arm regrowing in first person, or missing its end (the vanilla arm is hidden while it is broken or cut). */
@Mixin(PlayerRenderer.class)
public abstract class UndeadFirstPersonArmMixin {
	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$undeadArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm, ModelPart sleeve,
			CallbackInfo ci) {
		UndeadState s = Undead.peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
		int part = arm == model.rightArm ? UndeadState.RIGHT_ARM : UndeadState.LEFT_ARM;
		float now = player.level().getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		if (!s.broken(part)) {
			com.projecthero.mod.client.undead.UndeadCuts.Cut cut = com.projecthero.mod.client.undead.UndeadCuts.of(s, part, now);
			if (cut != null) {
				pose.pushPose();
				arm.translateAndRotate(pose);
				UndeadRender.drawCut(pose, buffers, light, player.getSkin().texture(), UndeadRender.slim(player), part, cut);
				pose.popPose();
				com.projecthero.mod.client.undead.UndeadFirstPerson.afterArm(pose, buffers, light, player, part, arm, cut);
			}
			return;
		}
		float p = s.regrowth(part, now);
		if (p <= 0f) {
			return;
		}
		pose.pushPose();
		arm.translateAndRotate(pose);
		int end = UndeadState.extremity(part); // v0.17.3: the hand grows from the wrist as its own segment
		com.projecthero.mod.client.undead.UndeadRegrow.drawPart(pose, buffers, light, player.getSkin().texture(), UndeadRender.slim(player), part, Math.min(1f, p),
				end < 0 ? 0f : s.regrowth(end, now));
		pose.popPose();
	}
}
