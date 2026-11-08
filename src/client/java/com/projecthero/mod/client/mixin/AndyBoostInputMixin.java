package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.andy.AndyFlight;

import net.minecraft.client.Minecraft;

/**
 * v0.17.3: while Andy flies, right click is the boost ({@code AndyClient} sends it): it must not also use the held item or
 * place a block.
 */
@Mixin(Minecraft.class)
public abstract class AndyBoostInputMixin {
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$andyBoostNoUse(CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		if (mc.player != null && AndyFlight.isFlying(mc.player)) {
			ci.cancel();
		}
	}
}
