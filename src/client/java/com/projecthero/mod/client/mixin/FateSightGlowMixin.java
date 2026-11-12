package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.doctorfate.FateFx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** v0.17.3 Doctor Fate, Shift+V Sight of Nabu: the outlines through walls (only the casting client knows the list). */
@Mixin(Minecraft.class)
public abstract class FateSightGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$sightOfNabu(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (FateFx.sighted(entity)) {
			cir.setReturnValue(true);
		}
	}
}
