package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.stormbreaker.BifrostTravel;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.7: the Bifrost Travel cinematic holds its riders in the air for about 4.5 s (the rise, then the descent at the
 * destination) -- past vanilla's 4 s floating limit, so on a server with allow-flight off they would be kicked for flying.
 * While {@link BifrostTravel#inBeam} the limit is lifted; it applies again the moment the trip ends.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class BifrostFlightKickMixin {
	@Inject(method = "getMaximumFlyingTicks", at = @At("HEAD"), cancellable = true)
	private void projecthero$bifrostNoKick(Entity entity, CallbackInfoReturnable<Integer> cir) {
		if (entity instanceof ServerPlayer p && BifrostTravel.inBeam(p)) {
			cir.setReturnValue(Integer.MAX_VALUE / 2);
		}
	}
}
