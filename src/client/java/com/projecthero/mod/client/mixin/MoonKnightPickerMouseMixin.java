package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MouseHandler;

/**
 * Moon Knight Phase 5 -- the radial alter picker (hold C) is used without freeing the mouse: while it is open the
 * camera stays still and the mouse's movement steers the picker's cursor instead, and the scroll wheel steps through
 * the three alters instead of the hotbar. Inert whenever the picker is closed.
 */
@Mixin(MouseHandler.class)
public abstract class MoonKnightPickerMouseMixin {
	@Shadow
	private double accumulatedDX;
	@Shadow
	private double accumulatedDY;

	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void projecthero$steerAlterPicker(double movementTime, CallbackInfo ci) {
		if (com.projecthero.mod.client.moonknight.MoonKnightAlterPicker.isOpen()) {
			com.projecthero.mod.client.moonknight.MoonKnightAlterPicker.onMouseMoved(accumulatedDX, accumulatedDY);
			accumulatedDX = 0.0;
			accumulatedDY = 0.0;
			ci.cancel();
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void projecthero$scrollAlterPicker(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (com.projecthero.mod.client.moonknight.MoonKnightAlterPicker.isOpen() && yOffset != 0.0) {
			com.projecthero.mod.client.moonknight.MoonKnightAlterPicker.onScroll(yOffset);
			ci.cancel();
		}
	}
}
