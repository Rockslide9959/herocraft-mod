package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.greenlantern.mech.HardLightMechEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** v0.17.1: Sneak does not leave the Hard-Light Mech (N does), so vanilla's "Press Shift to Dismount" hint is dropped. */
@Mixin(Gui.class)
public abstract class GreenLanternMechGuiMixin {
	@Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void projecthero$noMechDismountHint(Component message, boolean animate, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && HardLightMechEntity.isPilot(mc.player)
				&& message.getContents() instanceof TranslatableContents tc && "mount.onboard".equals(tc.getKey())) {
			ci.cancel();
		}
	}

	/** Nor the vanilla mount hearts (18 rows of them for 360 integrity): the mech HUD shows its INTEGRITY bar instead. */
	@Inject(method = "renderVehicleHealth", at = @At("HEAD"), cancellable = true, require = 0)
	private void projecthero$noMechHearts(net.minecraft.client.gui.GuiGraphics graphics, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && HardLightMechEntity.isPilot(mc.player)) {
			ci.cancel();
		}
	}
}
