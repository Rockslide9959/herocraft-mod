package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.starfire.StarfireClient;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.3 Starfire, V Language Kiss on a player: for 60 s the two see each other outlined through walls. Hooked on
 * {@code Minecraft#shouldEntityAppearGlowing}, which only this client's renderer asks, so nobody else sees the outline.
 */
@Mixin(Minecraft.class)
public abstract class StarfireBondGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$starfireBond(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (StarfireClient.bondGlows(entity)) {
			cir.setReturnValue(true);
		}
	}
}
