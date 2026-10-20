package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1 comic rework: sculk sensors, shriekers and the Warden ignore a Mark 5 STEALTH wearer -- every game event the
 * wearer is the source of (steps, landing, flapping, hitting ground, item use...) is simply never emitted, so no
 * vibration ever reaches a listener.
 */
@Mixin(ServerLevel.class)
public abstract class IronManStealthVibrationMixin {
	@Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$stealthVibrations(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
		if (context.sourceEntity() instanceof Player player && com.projecthero.mod.ironman.IronManArmor.stealthed(player)) {
			ci.cancel();
		}
	}
}
