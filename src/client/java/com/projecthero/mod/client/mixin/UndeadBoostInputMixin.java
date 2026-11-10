package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.undead.UndeadFlight;

import net.minecraft.client.Minecraft;

/**
 * v0.17.3: while Undead flies, right click is the boost ({@code UndeadClient} sends it): it must not also use the held item or
 * place a block.
 */
@Mixin(Minecraft.class)
public abstract class UndeadBoostInputMixin {
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$undeadBoostNoUse(CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		if (mc.player != null && UndeadFlight.isFlying(mc.player)) {
			ci.cancel();
		}
	}
}
