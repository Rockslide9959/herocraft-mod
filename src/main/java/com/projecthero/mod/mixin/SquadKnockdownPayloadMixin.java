package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.squad.SquadKnockdown;

import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * v0.17.5 squad knockdown: while a player is knocked down, every Project Hero ability / power-key packet they send is dropped
 * before Fabric hands it to its receiver -- one gate for all ~80 power payloads instead of a check in each. Only this mod's
 * packets are touched (see {@link SquadKnockdown#blocksPayload}); vanilla and other mods are untouched. Priority 500 puts this
 * ahead of Fabric's own receiver hook (priority 999) on the same method. Runs on the network thread, which is why the knocked
 * table it reads is concurrent.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, priority = 500)
public abstract class SquadKnockdownPayloadMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
	private void projecthero$dropKnockedPowerPackets(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
		if (packet.payload() != null && SquadKnockdown.blocksPayload(this.player, packet.payload().type().id())) {
			ci.cancel();
		}
	}
}
