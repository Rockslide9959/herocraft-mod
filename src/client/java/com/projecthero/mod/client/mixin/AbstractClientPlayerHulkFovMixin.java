package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.hulk.Hulk;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.13.16: the unwilling Hulk change pins the player by multiplying movement speed to 0, and vanilla derives the FOV
 * from movement speed -- so the view zoomed in to about half its width for the whole kneeling change. Hold the FOV
 * neutral while it happens (vanilla eases back to the Hulk's wider sprinting FOV afterwards).
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerHulkFovMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("HEAD"), cancellable = true)
	private void projecthero$noHulkChangeZoom(CallbackInfoReturnable<Float> cir) {
		if (Hulk.changing((Player) (Object) this)) {
			cir.setReturnValue(1.0f);
		}
	}
}
