package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.symbiote.monster.SymbioteMonster;

import net.minecraft.client.player.AbstractClientPlayer;

/**
 * v0.17.5 Symbiote Monster form: the host is rooted (movement speed -100%) while the Shift+Z charge, the cocoon and the melt run, and
 * vanilla narrows the field of view with the speed attribute -- it zoomed the camera right in on the transformation. While rooted the
 * view stays as it is.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class SymbioteMonsterFovMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void projecthero$noRootedZoom(CallbackInfoReturnable<Float> cir) {
		if (SymbioteMonster.rooted((AbstractClientPlayer) (Object) this)) {
			cir.setReturnValue(1.0f);
		}
	}
}
