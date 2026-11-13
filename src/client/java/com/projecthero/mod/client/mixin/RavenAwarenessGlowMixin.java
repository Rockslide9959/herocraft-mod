package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.raven.RavenAwareness;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.3 Raven, passive Mystical Awareness: every 8 s, hostiles within 24 blocks glow through walls for 2 s. Hooked on
 * {@code Minecraft#shouldEntityAppearGlowing}, which only this client's renderer asks, so the outlines are hers alone.
 */
@Mixin(Minecraft.class)
public abstract class RavenAwarenessGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$mysticalAwareness(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		Minecraft mc = (Minecraft) (Object) this;
		if (mc.player != null && RavenAwareness.glows(mc.player, entity)) {
			cir.setReturnValue(true);
		}
	}
}
