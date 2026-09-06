package com.projecthero.mod.client.mixin;

import com.projecthero.mod.firearm.item.FirearmItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the local player holds a firearm, the left mouse button fires the gun instead of swinging /
 * breaking a block / attacking an entity. The fire request itself is sent from
 * {@link com.projecthero.mod.client.firearm.FirearmClient}; this mixin only suppresses the vanilla
 * attack so the two do not both happen. Gated strictly on {@code held instanceof FirearmItem}, so it
 * changes nothing for any other item or power.
 */
@Mixin(Minecraft.class)
public abstract class FirearmAttackMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void projecthero$firearmStartAttack(CallbackInfoReturnable<Boolean> cir) {
		if (holdingFirearm()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void projecthero$firearmContinueAttack(boolean leftClick, CallbackInfo ci) {
		if (holdingFirearm()) {
			ci.cancel();
		}
	}

	private static boolean holdingFirearm() {
		LocalPlayer p = Minecraft.getInstance().player;
		return p != null && p.getMainHandItem().getItem() instanceof FirearmItem;
	}
}
