package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.syndicate.KingpinCanePose;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.14.31: every client-side arm swing -- the local player's own click, and the swing the server broadcasts for other
 * players and mobs (the Kingpin's cane blows) -- is offered to {@link KingpinCanePose}, which starts the next cane strike
 * if the swinger holds the Kingpin's Cane. Client entities only; nothing is changed about the swing itself.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityCaneSwingMixin {
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
	private void projecthero$caneSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide()) {
			KingpinCanePose.onSwing(self, hand);
		}
	}
}
