package com.projecthero.mod.gametest.mixin;

import java.util.function.BooleanSupplier;

import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Optional ({@code -Dprojecthero.gametest.fastTicks=true}, i.e. {@code -PfastTicks=true}): the test server does not sleep
 * out the rest of each 50 ms tick, it starts the next tick immediately. Tests count ticks, not wall-clock seconds, so a
 * suite that mostly waits (and every Time Slow test, which drops the tick rate to 1/s) finishes several times sooner.
 * <p>
 * v0.16.1 finding: for {@code runGameTest} this is a no-op. Vanilla's {@code GameTestServer} overrides
 * {@code waitUntilNextTick} with a bare {@code runAllTasks()} (checked with javap on the 1.21.1 jar), so the gametest
 * server never sleeps between ticks and the suite is already as fast as the CPU allows, with or without
 * {@code -PfastTicks}. The redirect only matters for a normal server. CI does not pass the flag, and that changes
 * nothing.
 */
@Mixin(MinecraftServer.class)
public abstract class UnthrottledTicksMixin {
	@Redirect(method = "waitUntilNextTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/MinecraftServer;managedBlock(Ljava/util/function/BooleanSupplier;)V"))
	private void projecthero$noSleep(MinecraftServer server, BooleanSupplier wait) {
		if (!Boolean.getBoolean("projecthero.gametest.fastTicks")) {
			server.managedBlock(wait);
		}
	}
}
