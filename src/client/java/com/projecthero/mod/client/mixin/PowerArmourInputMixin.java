package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.powerarmour.PowerArmourClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;

/**
 * v0.17.5 Power Armour: while the suit's enter / exit sequence runs (and while the Core Overload winds up) the player's own
 * movement keys do nothing; {@link PowerArmourClient#drive} then walks them along the sequence's path (as velocity, so the
 * walk animation plays).
 */
@Mixin(KeyboardInput.class)
public abstract class PowerArmourInputMixin {
	@Inject(method = "tick", at = @At("RETURN"))
	private void projecthero$powerArmourDrive(boolean slow, float slowFactor, CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			PowerArmourClient.input(player, (Input) (Object) this);
		}
	}
}
