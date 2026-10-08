package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.nova.NovaClient;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;

/**
 * v0.15.15: in first person, a Nova's own gold / cyan aura, Overload, suit-up and Force Field particles (and the white
 * flashes) that would spawn within about 1.5 blocks of his camera are dropped -- they are spawned round his body for
 * everyone else to see, and from inside they filled the whole view with big gold squares. See
 * {@link NovaClient#hideNearCamera}.
 */
@Mixin(ClientPacketListener.class)
public abstract class NovaFirstPersonParticleMixin {
	@Inject(method = "handleParticleEvent", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
			shift = At.Shift.AFTER), cancellable = true)
	private void projecthero$novaFirstPersonParticles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		if (NovaClient.hideNearCamera(packet.getParticle(), packet.getX(), packet.getY(), packet.getZ(), packet.getCount(),
				Math.max(packet.getXDist(), Math.max(packet.getYDist(), packet.getZDist())))) {
			ci.cancel();
		}
	}
}
