package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.stand.TimeStopClient;

import net.minecraft.client.Minecraft;

/**
 * v0.17.2 Stands: a player frozen in stopped time gets no key presses (attack, use, inventory, hotbar). (v0.17.3: Precision
 * Sight's outlines went with the move.)
 */
@Mixin(Minecraft.class)
public abstract class StandMinecraftMixin {
	@Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
	private void projecthero$timeStopKeys(CallbackInfo ci) {
		if (TimeStopClient.localFrozen() || TimeStopClient.localLimited()) { // (v0.17.4: a speedster in it only moves)
			ci.cancel();
		}
	}
}
