package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Sneaking cannot dismount a Titan (the Titan Shift key is the way down), so vanilla's "Press Shift to Dismount" hint is dropped. */
@Mixin(Gui.class)
public abstract class GuiTitanMountMessageMixin {
	@Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void projecthero$noDismountHint(Component message, boolean animate, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && TitanFormEntity.isOwnerRider(mc.player)
				&& message.getContents() instanceof TranslatableContents tc && "mount.onboard".equals(tc.getKey())) {
			ci.cancel();
		}
	}
}
