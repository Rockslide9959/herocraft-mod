package com.projecthero.mod.client.mixin;

import com.projecthero.mod.client.flight.DirectionalFlight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v0.14.16: tells {@link DirectionalFlight} when the server has set the local player's velocity (a dash, Dive Bomb, a
 * launch, a knockback), so the flight goes with it instead of easing it away as if it had never happened. TAIL is only
 * reached on the client thread -- the first, network-thread call bails out through {@code ensureRunningOnSameThread}.
 */
@Mixin(ClientPacketListener.class)
public abstract class DirectionalFlightMotionMixin {
	@Inject(method = "handleSetEntityMotion", at = @At("TAIL"))
	private void projecthero$flightMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
		var player = Minecraft.getInstance().player;
		if (player != null && packet.getId() == player.getId()) {
			DirectionalFlight.onServerMotion();
		}
	}
}
