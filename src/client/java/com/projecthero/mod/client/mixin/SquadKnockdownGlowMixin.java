package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.squad.SquadKnockdownClient;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.5 squad knockdown: a knocked squadmate is outlined -- on this client's screen only (hooked on
 * {@code Minecraft#shouldEntityAppearGlowing}, which only the local renderer asks, so nothing is broadcast; the v0.15.4
 * privacy rule).
 */
@Mixin(Minecraft.class)
public abstract class SquadKnockdownGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$knockedSquadmateGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (SquadKnockdownClient.outlined(entity)) {
			cir.setReturnValue(true);
		}
	}
}
