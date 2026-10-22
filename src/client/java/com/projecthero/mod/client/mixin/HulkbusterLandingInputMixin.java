package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.ironman.IronManHulkbusterAnimatable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user): the Hulkbuster's landing holds you only until its impact frame (~0.2 s): no movement / jump input and
 * the touchdown's slide is killed, so the slam lands where you touched down. After that, moving simply cancels the rest
 * of the clip ({@link IronManHulkbusterAnimatable#landingHoldsInput}).
 */
@Mixin(KeyboardInput.class)
public abstract class HulkbusterLandingInputMixin {
	@Inject(method = "tick", at = @At("RETURN"))
	private void projecthero$holdForLanding(boolean slow, float slowFactor, CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !IronManHulkbusterAnimatable.landingHoldsInput(player)) {
			return;
		}
		Input in = (Input) (Object) this;
		in.forwardImpulse = 0f;
		in.leftImpulse = 0f;
		in.up = false;
		in.down = false;
		in.left = false;
		in.right = false;
		in.jumping = false;
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
		player.setSprinting(false);
	}
}
