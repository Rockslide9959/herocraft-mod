package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.omnitrix.alien.alienx.AlienXHall;

import net.minecraft.client.renderer.GameRenderer;

/** v0.17.2 round 4: the world on Alien X's screen shows no block outline: nobody is looking at a block from there. */
@Mixin(GameRenderer.class)
public abstract class AlienXHallOutlineMixin {
	@Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
	private void projecthero$noOutlineInHall(CallbackInfoReturnable<Boolean> cir) {
		if (AlienXHall.showing()) {
			cir.setReturnValue(false);
		}
	}
}
