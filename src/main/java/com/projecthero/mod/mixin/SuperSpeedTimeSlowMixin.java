package com.projecthero.mod.mixin;

import java.util.function.BooleanSupplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow;

import net.minecraft.server.MinecraftServer;

/**
 * Super Speed Time Slow (v0.14.7, game-wide): while the world runs at 1 tick a second, the server thread spends the
 * gaps spinning on {@code pollTask}; that is where the caster gets their extra, full-speed ticks
 * ({@link SuperSpeedTimeSlow#pulse}) -- never in the middle of a server tick. Inert (one null check) whenever no Time
 * Slow is running.
 */
@Mixin(MinecraftServer.class)
public abstract class SuperSpeedTimeSlowMixin {
	@Inject(method = "pollTask", at = @At("HEAD"))
	private void projecthero$timeSlowPulse(CallbackInfoReturnable<Boolean> cir) {
		SuperSpeedTimeSlow.pulse((MinecraftServer) (Object) this);
	}

	@Inject(method = "tickServer", at = @At("HEAD"))
	private void projecthero$tickStart(BooleanSupplier haveTime, CallbackInfo ci) {
		SuperSpeedTimeSlow.markServerTick(true);
	}

	@Inject(method = "tickServer", at = @At("RETURN"))
	private void projecthero$tickEnd(BooleanSupplier haveTime, CallbackInfo ci) {
		SuperSpeedTimeSlow.markServerTick(false);
	}
}
