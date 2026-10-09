package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.FlightPoseHelper;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * The body half of the flight pose (the limbs live in {@code HumanoidModelMixin}): tilts the whole
 * player forward by {@link FlightPoseHelper#lean}, from upright while hovering through a slight
 * walking lean to fully horizontal while sprinting -- THOR_DESIGN.md's Flight power, with the
 * superman pose at the top end.
 *
 * <p>The head is deliberately <em>not</em> carried along by that tilt; {@code HumanoidModelMixin}
 * takes the same angle back out of the head part so it keeps looking where the player is looking
 * instead of being driven face-down into the ground at full sprint.
 *
 * <p>Everything keys off the smoothed pose values, which are driven by the synced flight flag and
 * the entity's own position/sprint state -- so remote viewers see the same animation the flying
 * player does.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
	/**
	 * Ceiling crawling: turn the model over.
	 *
	 * <p>Done to the <em>model</em> and not the camera, deliberately. Rolling a first-person view 180
	 * degrees is genuinely nauseating and makes the player's own controls unreadable, and the design
	 * is explicit that movement reliability and a legible camera come first. Rotating here instead
	 * costs nothing in either: the crawl still works exactly the same, the player still sees the world
	 * the right way up, and because it keys off the synced climb state every <em>other</em> player
	 * sees the upside-down pose too, which is the half that actually reads as Spider-Man.
	 */
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("TAIL"))
	private void projecthero$ceilingCrawlPose(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks,
			float rotationYaw, float partialTicks, float scale, CallbackInfo ci) {
		if (!com.projecthero.mod.spider.SpiderClimb.onCeiling(player)) {
			return;
		}
		// Flip about the model's local Z (its facing axis at this point in setupRotations) and lift it
		// back onto the ceiling, since rotating about the feet would put the model below the surface.
		poseStack.translate(0.0f, player.getBbHeight() - 0.1f, 0.0f);
		poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
	}

	/** Wolverine Claw Dash: pitch the whole body forward into the lunge (limbs are posed in HumanoidModelMixin). */
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("TAIL"))
	private void projecthero$clawDashLean(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks,
			float rotationYaw, float partialTicks, float scale, CallbackInfo ci) {
		if (com.projecthero.mod.client.wolverine.WolverineDashPose.amount(player) <= 0.0f) {
			return;
		}
		poseStack.translate(0.0f, 0.3f, 0.0f);
		poseStack.mulPose(Axis.XP.rotationDegrees(-com.projecthero.mod.client.wolverine.WolverineDashPose.LEAN_DEGREES));
	}

	/** v0.15.16: the Punisher's Tactical Roll -- a full tumble about the body's middle (forward / back / to either side). */
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("TAIL"))
	private void projecthero$punisherRoll(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks,
			float rotationYaw, float partialTicks, float scale, CallbackInfo ci) {
		float[] r = com.projecthero.mod.client.punisher.PunisherGunPose.rollAngle(player, partialTicks);
		if (r == null) {
			return;
		}
		poseStack.translate(0.0f, 0.75f - r[2], 0.0f);
		poseStack.mulPose(r[0] == 0f ? Axis.XP.rotationDegrees(r[1]) : Axis.ZP.rotationDegrees(r[1]));
		poseStack.translate(0.0f, -0.75f, 0.0f);
	}

	/** v0.14.11: the speed-force whirl of a Flash Ring suit-up (two turns) / suit-down (one turn back). */
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("TAIL"))
	private void projecthero$flashWhirl(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks,
			float rotationYaw, float partialTicks, float scale, CallbackInfo ci) {
		float spin = com.projecthero.mod.client.flash.FlashSuitReveal.spin(player, partialTicks);
		if (spin != 0.0f) {
			poseStack.mulPose(Axis.YP.rotationDegrees(-spin));
		}
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("TAIL"))
	private void projecthero$leanWhileFlying(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks,
			float rotationYaw, float partialTicks, float scale, CallbackInfo ci) {
		// v0.15.9 Repulsor Boots: a slow hover bob, and a bank into a strafe -- before the lean, so the banked body
		// then leans forward along its own axis. Local +X is the flier's right here, and a positive Z rotation tips the
		// top toward -X, hence the minus. (IronManFlightFxClient mirrors this order for the boot jets.)
		float bob = com.projecthero.mod.client.ironman.IronManFlightPose.bootsBob(player, partialTicks);
		if (bob != 0.0f) {
			poseStack.translate(0.0f, bob, 0.0f);
		}
		float roll = com.projecthero.mod.client.ironman.IronManFlightPose.bootsRoll(player, partialTicks);
		if (Math.abs(roll) >= 0.05f) {
			poseStack.mulPose(Axis.ZP.rotationDegrees(-roll));
		}
		float lean = FlightPoseHelper.lean(player, partialTicks);
		if (Math.abs(lean) < 0.05f) {
			return;
		}

		// Negative: by this point setupRotations has already yawed the model so its local -Z
		// axis points along the player's facing direction; a *positive* X rotation tips the
		// head toward +Z (backward, away from facing) -- negative tips it toward -Z (forward),
		// which is the dive-forward pose we actually want.
		poseStack.mulPose(Axis.XP.rotationDegrees(-lean));
	}
}
