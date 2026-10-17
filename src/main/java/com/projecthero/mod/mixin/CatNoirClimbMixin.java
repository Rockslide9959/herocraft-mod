package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.miraculous.catnoir.CatNoir;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Cat Noir's Rooftop Run -- against a wall he counts as on a ladder, so vanilla's own climbing runs on both
 * sides (walk into the wall to go up, Sneak to cling, no fall damage building, the server's flight check satisfied).
 * {@link CatNoir#climbing} reads only the synced state and the world, so every viewer agrees.
 */
@Mixin(LivingEntity.class)
public abstract class CatNoirClimbMixin {
	@Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
	private void projecthero$catNoirClimb(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player player && CatNoir.climbing(player)) {
			cir.setReturnValue(true);
		}
	}
}
