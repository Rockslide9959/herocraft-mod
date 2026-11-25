package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.cutscene.CutsceneHands;

import net.minecraft.client.renderer.GameRenderer;

/**
 * v0.17.7: no first-person hands or held items while a cutscene plays ({@link CutsceneHands#hide()}). A HEAD cancel of the whole
 * hand pass, so the first-person hand/item mixins inside it (view bob, combo, Hulkbuster, Titan, Symbiote...) simply don't run.
 */
@Mixin(GameRenderer.class)
public abstract class CutsceneHandsMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void projecthero$hideHandsInCutscenes(CallbackInfo ci) {
		if (CutsceneHands.hide()) {
			ci.cancel();
		}
	}
}
