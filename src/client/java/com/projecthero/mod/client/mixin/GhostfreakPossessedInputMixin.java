package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.omnitrix.alien.GhostfreakClient;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;

/**
 * v0.17.2 (user: "Ghostfreak can posses players for like 10 seconds"): a possessed player's movement keys are Ghostfreak's, not
 * theirs ({@link GhostfreakClient#possessedInput}). Their own client still runs the physics, so the walk is smooth.
 */
@Mixin(KeyboardInput.class)
public abstract class GhostfreakPossessedInputMixin {
	@Inject(method = "tick", at = @At("RETURN"))
	private void projecthero$ghostfreakDrives(boolean slow, float slowFactor, CallbackInfo ci) {
		GhostfreakClient.possessedInput((Input) (Object) this);
	}
}
