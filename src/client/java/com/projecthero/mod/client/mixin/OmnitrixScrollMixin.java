package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.omnitrix.OmnitrixClient;

import net.minecraft.client.MouseHandler;

/** v0.17.1: while the Omnitrix is up the scroll wheel turns its dial instead of the hotbar. */
@Mixin(MouseHandler.class)
public abstract class OmnitrixScrollMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void projecthero$omnitrixDial(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (window == net.minecraft.client.Minecraft.getInstance().getWindow().getWindow() && OmnitrixClient.onScroll(yOffset)) {
			ci.cancel();
		}
	}

	/**
	 * v0.17.5: in the watch's function mode the middle mouse button moves to the next code slot (Shift: back) -- taken here, before
	 * the key mappings see it, so vanilla never picks a block with it.
	 */
	@Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
	private void projecthero$omnitrixFunctionMiddle(long window, int button, int action, int mods, CallbackInfo ci) {
		if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE && window == net.minecraft.client.Minecraft.getInstance().getWindow().getWindow()
				&& com.projecthero.mod.client.omnitrix.OmnitrixFunctionClient.blocksPick()) {
			if (action == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
				com.projecthero.mod.client.omnitrix.OmnitrixFunctionClient.onMiddleClick();
			}
			ci.cancel();
		}
	}
}
