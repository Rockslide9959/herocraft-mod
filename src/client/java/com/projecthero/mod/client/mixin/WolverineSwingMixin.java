package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.wolverine.WolverineClawCombo;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.1: tells {@link WolverineClawCombo} whenever a swing actually starts on this client -- the local player's own
 * left click, or any player's swing packet -- so every viewer steps through the same claw combo. Client-only mixin.
 */
@Mixin(LivingEntity.class)
public abstract class WolverineSwingMixin {
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("TAIL"))
	private void projecthero$wolverineCombo(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		// vanilla only restarts the swing (swingTime = -1) when the last one is far enough along
		if (self.level().isClientSide() && self.swinging && self.swingTime == -1) {
			WolverineClawCombo.onSwingStart(self, hand);
		}
	}
}
