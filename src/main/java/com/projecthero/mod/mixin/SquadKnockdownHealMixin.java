package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.squad.SquadKnockdown;

import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.5 squad knockdown: a knocked player does not heal (natural regen, Regeneration, a squadmate's heal power) -- only a
 * revive stands them up. Refusing the heal itself, rather than resetting health afterwards, also keeps the heart bar from
 * blinking every time hunger tries to regenerate.
 */
@Mixin(LivingEntity.class)
public abstract class SquadKnockdownHealMixin {
	@Inject(method = "heal", at = @At("HEAD"), cancellable = true)
	private void projecthero$knockedNoHeal(float amount, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.level().isClientSide() && SquadKnockdown.isKnocked(self)) {
			ci.cancel();
		}
	}
}
