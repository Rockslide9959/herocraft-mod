package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.mutation.v0145.SpeedPhaseFx;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.8 Super Speed Phase: a phasing player is drawn at a fresh random offset every frame, so the whole body
 * vibrates ({@link SpeedPhaseFx#jitter}). The render offset is where vanilla already shifts a crouching player's model.
 */
@Mixin(PlayerRenderer.class)
public abstract class SpeedPhaseJitterMixin {
	@Inject(method = "getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;",
			at = @At("RETURN"), cancellable = true)
	private void projecthero$phaseJitter(AbstractClientPlayer player, float partialTick, CallbackInfoReturnable<Vec3> cir) {
		Vec3 j = SpeedPhaseFx.jitter(player);
		if (j != Vec3.ZERO) {
			cir.setReturnValue(cir.getReturnValue().add(j));
		}
	}
}
