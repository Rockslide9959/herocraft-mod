package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.speedforce.SpeedForceSpeedster;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;

/**
 * v0.17.7 (user: "make it so that you cant move when using the charging pose"): in the Speed Force Speedster's hold-C charge
 * stance the movement and jump keys do nothing and any slide is killed, every tick before the move (looking around still
 * works). Done on the input, not with a speed modifier, which would zoom the camera in (vanilla's FOV follows the speed). The
 * server holds the runner still as well ({@code SpeedForceAbilities.chargeTick}).
 */
@Mixin(KeyboardInput.class)
public abstract class SpeedForceChargeInputMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void projecthero$speedForceChargeRoot(boolean slow, float slowFactor, CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !SpeedForceSpeedster.chargeRooted(player)) {
			return;
		}
		KeyboardInput self = (KeyboardInput) (Object) this;
		self.forwardImpulse = 0f;
		self.leftImpulse = 0f;
		self.up = false;
		self.down = false;
		self.left = false;
		self.right = false;
		self.jumping = false;
		self.shiftKeyDown = false; // no crouch either: it would sink the set position into the floor
		SpeedForceSpeedster.holdStill(player);
	}
}
