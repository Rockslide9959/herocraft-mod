package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.beamclash.BeamClashClient;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;

/**
 * v0.17.3: a beam's server-sent impact sparks land where it would have hit, and in a clash aimed at you that is your face. While
 * you are in a clash, the ones right at your camera are dropped so you can see the fight.
 */
@Mixin(ClientPacketListener.class)
public abstract class BeamClashParticleMixin {
	@Inject(method = "handleParticleEvent", at = @At("HEAD"), cancellable = true)
	private void projecthero$beamClashClearView(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		if (BeamClashClient.blocksParticleAt(packet.getX(), packet.getY(), packet.getZ())) {
			ci.cancel();
		}
	}
}
