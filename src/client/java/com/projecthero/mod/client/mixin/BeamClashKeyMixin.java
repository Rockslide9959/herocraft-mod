package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.beamclash.BeamClashClient;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * v0.17.5 (user: "while beam clashing the player get locked into it until 1 player wins or 1 player loses"): while the local player
 * is locked in a beam clash every key reads as up and its presses are thrown away -- moving, jumping, sneaking, the hotbar, the
 * inventory, dropping and every ability key -- except attack (the push), chat, commands, the player list, screenshots, fullscreen
 * and the camera keys. Letting go of the beam key this way does not end the clash: the server holds it.
 */
@Mixin(KeyMapping.class)
public abstract class BeamClashKeyMixin {
	@Shadow
	private int clickCount;

	@Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
	private void projecthero$beamClashHeldOff(CallbackInfoReturnable<Boolean> cir) {
		if (BeamClashClient.locked() && !projecthero$allowed()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
	private void projecthero$beamClashNoPress(CallbackInfoReturnable<Boolean> cir) {
		if (BeamClashClient.locked() && !projecthero$allowed()) {
			clickCount = 0;
			cir.setReturnValue(false);
		}
	}

	private boolean projecthero$allowed() {
		Options o = Minecraft.getInstance().options;
		Object self = this;
		return self == o.keyAttack || self == o.keyChat || self == o.keyCommand || self == o.keyPlayerList || self == o.keyScreenshot
				|| self == o.keyFullscreen || self == o.keyTogglePerspective || self == o.keySmoothCamera;
	}
}
