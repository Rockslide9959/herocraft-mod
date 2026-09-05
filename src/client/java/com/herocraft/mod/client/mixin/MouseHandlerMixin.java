package com.herocraft.mod.client.mixin;

import com.herocraft.mod.client.firearm.FirearmClient;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.OptionInstance;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scope handling for the sniper (and any future scoped firearm):
 * <ul>
 *   <li>the scroll wheel cycles the zoom level while aiming a scoped weapon (instead of the hotbar);</li>
 *   <li>mouse look is slowed while scoped, proportional to the zoom, so a 10x scope is controllable
 *       (spec section 7 -- "slightly reduce mouse sensitivity if practical").</li>
 * </ul>
 * Both are inert unless a scoped firearm is actually raised.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void herocraft$scopeZoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (FirearmClient.canScrollZoom() && (yOffset > 0.0 || yOffset < 0.0)) {
			FirearmClient.cycleZoom();
			ci.cancel();
		}
	}

	@Redirect(method = "turnPlayer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
	private Object herocraft$scopeSensitivity(OptionInstance<?> instance) {
		Object value = instance.get();
		float factor = FirearmClient.scopedSensitivityFactor();
		if (factor < 1.0f && value instanceof Double d) {
			return (Double) (d * factor);
		}
		return value;
	}
}
