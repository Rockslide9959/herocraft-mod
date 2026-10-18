package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.quirk.erasure.ErasureHud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.7: Eraser Head's goggles outline the powered players and special mobs around him. Hooked on
 * {@code Minecraft#shouldEntityAppearGlowing}, which only this client's renderer asks -- the outlines are his alone.
 */
@Mixin(Minecraft.class)
public abstract class ErasureGogglesGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$erasureGogglesGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (ErasureHud.marked(entity)) {
			cir.setReturnValue(true);
		}
	}
}
