package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.stand.StandClient;
import com.projecthero.mod.client.stand.TimeStopClient;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.2 Stands: Precision Sight's mob outlines (asked only by this client's renderer -- the Stand user's alone), and a
 * player frozen in stopped time gets no key presses (attack, use, inventory, hotbar).
 */
@Mixin(Minecraft.class)
public abstract class StandMinecraftMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$precisionSight(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (StandClient.sightOutlined(entity)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
	private void projecthero$timeStopKeys(CallbackInfo ci) {
		if (TimeStopClient.localFrozen()) {
			ci.cancel();
		}
	}
}
