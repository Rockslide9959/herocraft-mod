package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.undead.UndeadHeadThrow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;

/**
 * v0.17.3 (user: "player cant move while aiming their head"): while Undead holds his head back to throw it the movement keys
 * do nothing (he can still turn to aim). Done on the input rather than with a speed modifier, which would have zoomed the
 * camera in (vanilla's FOV follows the walking speed). The server roots the jump and stops any slide as well.
 */
@Mixin(KeyboardInput.class)
public abstract class UndeadHeadThrowInputMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void projecthero$undeadHeadThrowRoot(boolean slow, float slowFactor, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && UndeadHeadThrow.aiming(mc.player)) {
			KeyboardInput self = (KeyboardInput) (Object) this;
			self.forwardImpulse = 0f;
			self.leftImpulse = 0f;
			self.up = false;
			self.down = false;
			self.left = false;
			self.right = false;
			self.jumping = false;
		}
	}
}
