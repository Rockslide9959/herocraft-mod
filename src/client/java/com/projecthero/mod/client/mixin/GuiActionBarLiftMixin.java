package com.projecthero.mod.client.mixin;

import com.projecthero.mod.client.gui.IronManHud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v0.16.7: the action-bar message ("on cooldown", "LOW POWER", "BROWNOUT"...) sits exactly where the Iron Man key strip
 * is drawn, so while the strip is on screen the message is lifted to just above it ({@link IronManHud#actionBarLift}).
 */
@Mixin(Gui.class)
public abstract class GuiActionBarLiftMixin {
	private boolean projecthero$lifted;

	@Inject(method = "renderOverlayMessage", at = @At("HEAD"))
	private void projecthero$liftActionBar(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		int lift = IronManHud.actionBarLift(g.guiHeight());
		projecthero$lifted = lift > 0;
		if (projecthero$lifted) {
			g.pose().pushPose();
			g.pose().translate(0f, -lift, 0f);
		}
	}

	@Inject(method = "renderOverlayMessage", at = @At("RETURN"))
	private void projecthero$dropActionBar(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		if (projecthero$lifted) {
			g.pose().popPose();
			projecthero$lifted = false;
		}
	}
}
