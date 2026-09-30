package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;

import net.minecraft.server.level.ServerPlayer;

/**
 * Super Speed Shift+C Phase (v0.14.5). Speed Mode is a TOGGLE, and the router never forwards a TOGGLE's key-up, so
 * the hold-to-phase variant is routed here instead:
 * <ul>
 *   <li>{@code dispatchExperimental} HEAD -- Shift+C with Super Speed selected starts Phase instead of flipping
 *       Speed Mode;</li>
 *   <li>{@code handleInput} HEAD -- while phasing, every key is swallowed (no other ability can fire) and C's
 *       key-up ends the phase.</li>
 * </ul>
 */
@Mixin(value = AbilityRouter.class, remap = false)
public abstract class SuperSpeedAbilityRouterMixin {
	@Inject(method = "handleInput", at = @At("HEAD"), cancellable = true, remap = false)
	private static void projecthero$speedPhaseInput(ServerPlayer player, int slotNumber, boolean pressed, CallbackInfo ci) {
		if (SuperSpeedHandlers.interceptInput(player, slotNumber, pressed)) {
			ci.cancel();
		}
	}

	@Inject(method = "dispatchExperimental", at = @At("HEAD"), cancellable = true, remap = false)
	private static void projecthero$speedPhaseStart(ServerPlayer player, AbilitySlot slot, boolean pressed, CallbackInfo ci) {
		if (SuperSpeedHandlers.interceptDispatch(player, slot, pressed)) {
			ci.cancel();
		}
	}
}
