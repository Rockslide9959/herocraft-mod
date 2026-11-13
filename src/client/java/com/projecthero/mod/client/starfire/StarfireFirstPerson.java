package com.projecthero.mod.client.starfire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.starfire.Starfire;
import com.projecthero.mod.starfire.data.StarfireState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;

/** v0.17.3 tester round: the first-person arm's hand glow ({@code mixin.StarfireFirstPersonArmMixin}), same shells as the body. */
public final class StarfireFirstPerson {
	private StarfireFirstPerson() {
	}

	public static void renderArm(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, ModelPart arm, boolean right) {
		if (player.isInvisible()) {
			return;
		}
		StarfireState s = Starfire.peek(player);
		if (s == null || !s.hasPower || !StarfireGlowLayer.handsGlow(player, s)) {
			return;
		}
		float t = player.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		pose.pushPose();
		arm.translateAndRotate(pose);
		StarfireGlowLayer.renderHand(pose, buffers, player, right, t);
		pose.popPose();
	}
}
