package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.miraculous.hawkmoth.HawkMothFxClient;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.6: Hawk Moth's outlines -- every akumatized creature (purple, for everyone: the server sends the list to every
 * player) and his own Emotion Sense (mood colours, for him alone). Hooked on {@code Minecraft#shouldEntityAppearGlowing},
 * which only this client's renderer asks, so nothing is ever set on the entities (the v0.15.4 privacy rule).
 */
@Mixin(Minecraft.class)
public abstract class HawkMothGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$hawkMothOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		// (not your own body: a Champion player sees his own mask and aura, not an outline round himself)
		if (entity != Minecraft.getInstance().player && HawkMothFxClient.outline(entity) != -1) {
			cir.setReturnValue(true);
		}
	}
}
