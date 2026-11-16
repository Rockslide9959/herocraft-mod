package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.powerarmour.PowerArmourFx;

import net.minecraft.world.entity.Entity;

/**
 * v0.17.5 Power Armour: the Visor (V) outlines living things within 32 blocks through walls -- the wearer's own view only, decided
 * client-side for client entities (the same privacy rule as {@code EntityGlowMixin}: never answered on the integrated server).
 */
@Mixin(Entity.class)
public abstract class PowerArmourVisorGlowMixin {
	@Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$powerArmourVisor(CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (com.projecthero.mod.ironman.IronManHighlight.mayDecide(self) && PowerArmourFx.visorOutlines(self)) {
			cir.setReturnValue(true);
		}
	}
}
