package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.beastboy.BeastBoyForms;

import net.minecraft.client.player.LocalPlayer;

/**
 * v0.17.5 (user: "In fish mode make it so that beast boy can see better underwater"): as a sea animal Beast Boy has full water
 * vision at once, the way Conduit Power gives it -- vanilla otherwise ramps it up over 30 s after going under, and it is what
 * brightens the water fog colour so dark water reads (Night Vision does not brighten fog underwater). The fog distance itself is
 * pushed out in {@code FogRendererMixin#projecthero$raidFog}. No status icon: nothing is added to the player.
 */
@Mixin(LocalPlayer.class)
public abstract class BeastBoyWaterVisionMixin {
	@Inject(method = "getWaterVision", at = @At("HEAD"), cancellable = true)
	private void projecthero$beastWaterVision(CallbackInfoReturnable<Float> cir) {
		if (BeastBoyForms.waterBody((LocalPlayer) (Object) this)) {
			cir.setReturnValue(1.0f);
		}
	}
}
