package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.speedforce.SpeedForceSenses;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.1 (user): the Speed Force Speedster's danger sense outlines. Hooked on {@code Minecraft#shouldEntityAppearGlowing},
 * which only this client's renderer asks (never the integrated server's glowing flag) -- the outline is the runner's alone.
 */
@Mixin(Minecraft.class)
public abstract class SpeedForceDangerGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$dangerSense(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (SpeedForceSenses.marked(entity)) {
			cir.setReturnValue(true);
		}
	}
}
