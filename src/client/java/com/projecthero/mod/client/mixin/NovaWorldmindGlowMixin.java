package com.projecthero.mod.client.mixin;

import com.projecthero.mod.client.nova.NovaWorldmindClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v0.15.13: Nova's Worldmind outlines. Hooked on {@code Minecraft#shouldEntityAppearGlowing}, which only this client's
 * renderer asks -- never the integrated server -- so the outlines are the Nova's alone (the v0.15.4 privacy rule; the
 * Kryptonian X-Ray works the same way).
 */
@Mixin(Minecraft.class)
public abstract class NovaWorldmindGlowMixin {
	@Shadow
	public LocalPlayer player;

	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$novaWorldmind(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		LocalPlayer viewer = this.player;
		if (viewer == null || entity == viewer || entity instanceof ArmorStand
				|| entity instanceof com.projecthero.mod.oathbreaker.entity.OathbreakerEntity) { // the Oathbreaker never glows
			return;
		}
		if (NovaWorldmindClient.outlines(viewer, entity)) {
			cir.setReturnValue(true);
		}
	}
}
