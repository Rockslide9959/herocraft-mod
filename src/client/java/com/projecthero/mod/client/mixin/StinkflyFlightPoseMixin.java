package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.omnitrix.alien.StinkflyClient;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.2 (user: "make stinkfly's flying animations look better"): Stinkfly's whole body in flight -- tipped forward into his
 * speed, banked into his turns and bobbing on his wingbeats while he hovers ({@link StinkflyClient#bodyTilt}), pivoting about the
 * middle of the body like Moon Knight's glide.
 */
@Mixin(PlayerRenderer.class)
public abstract class StinkflyFlightPoseMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
			at = @At("TAIL"))
	private void projecthero$stinkflyFlight(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw,
			float partialTicks, float scale, CallbackInfo ci) {
		float[] tilt = StinkflyClient.bodyTilt(player, partialTicks);
		if (tilt == null) {
			// v0.17.2 (user: "give jetray better flight animations too please"): Jetray's flight attitude, the same pivot
			tilt = com.projecthero.mod.client.omnitrix.alien.jetray.JetrayBody.bodyTilt(player, partialTicks);
		}
		if (tilt == null) {
			// v0.17.2 (user: "make big chills flying animations look better please"): Big Chill's upright hover and small lean
			tilt = com.projecthero.mod.client.omnitrix.alien.bigchill.BigChillWings.bodyTilt(player, partialTicks);
		}
		if (tilt == null) {
			// v0.17.2 round 3: Buzzshock and Feedback, laid out along their held Shift+X flight
			tilt = com.projecthero.mod.client.omnitrix.alien.ThrustFlightPose.bodyTilt(player, partialTicks);
		}
		if (tilt == null) {
			// v0.17.2 round 4 (user: "upgrade his flight animations please"): Chromastone
			tilt = com.projecthero.mod.client.omnitrix.alien.ChromastoneFlightPose.bodyTilt(player, partialTicks);
		}
		if (tilt == null) {
			return;
		}
		float pivot = 0.9f * scale;
		poseStack.translate(0.0f, pivot + tilt[2], 0.0f);
		poseStack.mulPose(Axis.ZP.rotationDegrees(tilt[1]));
		poseStack.mulPose(Axis.XP.rotationDegrees(-tilt[0]));
		poseStack.translate(0.0f, -pivot, 0.0f);
	}
}
