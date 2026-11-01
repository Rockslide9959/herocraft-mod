package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.stand.TimeStop;

import net.minecraft.world.level.Level;

/** v0.17.2 Star Platinum: The World -- block entities (furnaces, hoppers, pistons) hold still too while time is stopped. */
@Mixin(Level.class)
public abstract class StandTimeStopLevelMixin {
	@Inject(method = "tickBlockEntities", at = @At("HEAD"), cancellable = true)
	private void projecthero$timeStopBlockEntities(CallbackInfo ci) {
		if (TimeStop.active() && TimeStop.freezesBlockEntities((Level) (Object) this)) {
			ci.cancel();
		}
	}
}
