package com.projecthero.mod.client.mixin;

import com.projecthero.mod.client.ProjectHeroModClient;
import com.projecthero.mod.wolverine.Wolverine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v0.13.9 Wolverine melee, client side:
 * <ul>
 * <li>With the claws out and both hands empty, every left-click swing picks the left or right claw at random
 * (vanilla always swings the main hand). The swing packet carries the hand, so every viewer sees the same
 * one; the hit itself is vanilla's and deals the same damage either way.</li>
 * <li>While the right-click claw guard is up he can't swing at all -- the same rule vanilla applies to a
 * raised shield (see {@code WolverineBlock}).</li>
 * </ul>
 */
@Mixin(Minecraft.class)
public abstract class WolverineAttackMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void projecthero$wolverineGuardNoAttack(CallbackInfoReturnable<Boolean> cir) {
		if (ProjectHeroModClient.wolverineGuardSent) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void projecthero$wolverineGuardNoMining(boolean leftClick, CallbackInfo ci) {
		if (ProjectHeroModClient.wolverineGuardSent) {
			ci.cancel();
		}
	}

	@ModifyArg(method = "startAttack", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
	private InteractionHand projecthero$wolverineRandomHand(InteractionHand hand) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p != null && Wolverine.clawsOut(p) && p.getMainHandItem().isEmpty() && p.getOffhandItem().isEmpty()) {
			return p.getRandom().nextBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		}
		return hand;
	}
}
