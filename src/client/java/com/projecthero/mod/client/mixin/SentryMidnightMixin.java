package com.projecthero.mod.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v0.17.5 Sentry: near the Void's takeover the day/night clock shows midnight -- on this client only. The client's copy of the day
 * time (what the sky, the sun / moon and the sky darkening read) is reported as this day's midnight while
 * {@code VoidTakeoverClient.midnight()} says so; the server's time, and so every other player and the world itself, never changes.
 */
@Mixin(ClientLevel.ClientLevelData.class)
public class SentryMidnightMixin {
	@Inject(method = "getDayTime", at = @At("RETURN"), cancellable = true)
	private void projecthero$voidMidnight(CallbackInfoReturnable<Long> cir) {
		if (com.projecthero.mod.client.sentry.VoidTakeoverClient.midnight()) {
			long t = cir.getReturnValue();
			cir.setReturnValue(Math.floorDiv(t, 24000L) * 24000L + 18000L);
		}
	}
}
