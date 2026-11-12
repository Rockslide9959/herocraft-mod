package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.doctorfate.DoctorFateClient;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;

/**
 * v0.17.3 Doctor Fate: while Nabu possesses this player his movement keys are Nabu's (the server's stream); while he is Taking hold
 * the movement keys sometimes go dead for a moment ({@link DoctorFateClient#input}).
 */
@Mixin(KeyboardInput.class)
public abstract class FateInputMixin {
	@Inject(method = "tick", at = @At("RETURN"))
	private void projecthero$nabuDrives(boolean slow, float slowFactor, CallbackInfo ci) {
		DoctorFateClient.input((Input) (Object) this);
	}
}
