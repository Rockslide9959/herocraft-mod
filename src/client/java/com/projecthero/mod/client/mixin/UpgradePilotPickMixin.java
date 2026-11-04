package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.2 fix (user: "whenever i left click to hit the iron golem takes damage"): Upgrade piloting a golem stands inside it, so
 * his crosshair always found the golem first. On his own client the golem he pilots cannot be picked: the crosshair (and so
 * the attack and use keys) reaches whatever is in front of it. Client only -- on the server the golem still takes his hits.
 */
@Mixin(LivingEntity.class)
public abstract class UpgradePilotPickMixin {
	@Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
	private void projecthero$pilotSeesThroughGolem(CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (self.level().isClientSide() && UpgradeKit.pilotPair(Minecraft.getInstance().player, self)) {
			cir.setReturnValue(false);
		}
	}
}
