package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.omnitrix.alien.RipjawsClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.2 Ripjaws revamp: the outline colour of his Blood Scent (Feeding Frenzy: red on anything under half health) and Blood
 * in the Water (in water: a faint pink on the wounded) -- {@link RipjawsClient#scentColour}. The viewer's own render only, and
 * only ever answered for a client-side entity (the {@code IronManHighlight.mayDecide} privacy rule).
 */
@Mixin(Entity.class)
public abstract class RipjawsScentColorMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$ripjawsScentColor(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer || !com.projecthero.mod.ironman.IronManHighlight.mayDecide(self)) {
			return;
		}
		LocalPlayer viewer = Minecraft.getInstance().player;
		if (viewer == null || !RipjawsClient.shown(viewer)) {
			return;
		}
		int c = RipjawsClient.scentColour(viewer, self);
		if (c >= 0) {
			cir.setReturnValue(c);
		}
	}
}
